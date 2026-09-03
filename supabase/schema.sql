-- WASSIL platform schema
-- Run this once in the Supabase SQL editor (or `supabase db push`) on a fresh project.
-- Auth (signup/login, password hashing, sessions) is handled by Supabase's built-in
-- auth.users table — these tables only hold app-specific data and reference it.

create extension if not exists "pgcrypto";

-- ============================================================
-- profiles — one row per auth.users row, app-specific fields
-- ============================================================
create type user_role as enum ('sender', 'delivery', 'admin');

create table profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null,
    phone text unique not null,
    role user_role not null default 'sender',
    balance numeric(10, 2) not null default 0,
    vehicle_type text, -- 'Moto', 'Voiture', 'Camionnette', 'Camion' (delivery role only)
    rating numeric(2, 1) not null default 5.0,
    -- Number of reviews behind that average. Without it the default 5.0 of a
    -- brand new courier is indistinguishable from a 5.0 earned over 40 jobs.
    rating_count int not null default 0,
    wilaya text,
    commune text,
    is_active boolean not null default true,
    profile_image_url text,
    kyc_status text not null default 'unverified', -- 'unverified', 'pending', 'verified', 'rejected'
    created_at timestamptz not null default now()
);

-- ============================================================
-- orders
-- ============================================================
-- NOTE: these labels intentionally match the status strings hardcoded in the
-- Android client (OrderAdapter, HistoryActivity, EarningsActivity,
-- OrderTrackingActivity, DatabaseHelper.getTotalRevenue). Do not rename one
-- side without the other, or cloud orders stop matching every client check.
-- The six lifecycle states of cahier des charges p.3, plus Annulée. Declared in
-- lifecycle order so status.asc sorts chronologically.
create type order_status as enum (
    'pending',          -- Recherche d'un livreur
    'prise_en_charge',  -- Livreur accepté
    'vers_depart',      -- En route vers le point de départ
    'colis_recupere',   -- Colis récupéré  (picked_up_at is stamped here)
    'en_route',         -- En cours de livraison
    'livre',            -- Livré           (delivered_at + courier credit)
    'annule'            -- Annulée
);

create table orders (
    id text primary key default ('WSL-' || extract(year from now()) || '-' || upper(substr(gen_random_uuid()::text, 1, 5))),
    sender_id uuid not null references profiles(id),
    delivery_id uuid references profiles(id),
    status order_status not null default 'pending',
    asking_price numeric(10, 2) not null,
    negotiated_price numeric(10, 2),
    pickup_address text not null,
    drop_address text not null,
    pickup_wilaya text not null,
    drop_wilaya text not null,
    pickup_lat double precision,
    pickup_lng double precision,
    drop_lat double precision,
    drop_lng double precision,
    distance_km numeric(6, 2),
    estimated_minutes int,
    package_type text not null,
    weight_kg numeric(6, 2),
    is_urgent boolean not null default false,
    special_instructions text,
    created_at timestamptz not null default now(),
    picked_up_at timestamptz,
    delivered_at timestamptz,
    -- Set once, by credit_courier_on_delivery, when the courier fee is paid.
    -- Its presence is what makes the payout idempotent; see the trigger below.
    courier_paid_at timestamptz
);

create index orders_status_wilaya_idx on orders (status, pickup_wilaya);
create index orders_sender_idx on orders (sender_id);
create index orders_delivery_idx on orders (delivery_id);

-- ============================================================
-- offers — couriers proposing a price on a pending order
-- (this is the multi-offer marketplace flow: sender picks one)
-- ============================================================
create type offer_status as enum ('pending', 'accepted', 'rejected', 'withdrawn');

create table offers (
    id uuid primary key default gen_random_uuid(),
    order_id text not null references orders(id) on delete cascade,
    courier_id uuid not null references profiles(id),
    offered_price numeric(10, 2) not null,
    message text,
    status offer_status not null default 'pending',
    created_at timestamptz not null default now(),
    unique (order_id, courier_id)
);

-- ============================================================
-- messages — chat between sender and courier on an order
-- ============================================================
create table messages (
    id uuid primary key default gen_random_uuid(),
    order_id text not null references orders(id) on delete cascade,
    sender_id uuid not null references profiles(id),
    body text,
    image_url text,
    created_at timestamptz not null default now()
);

create index messages_order_idx on messages (order_id, created_at);

-- ============================================================
-- courier_locations — latest GPS ping per active order
-- ============================================================
create table courier_locations (
    order_id text primary key references orders(id) on delete cascade,
    courier_id uuid not null references profiles(id),
    lat double precision not null,
    lng double precision not null,
    updated_at timestamptz not null default now()
);

-- ============================================================
-- payments
-- ============================================================
create type payment_method as enum ('cash', 'wallet', 'cib', 'edahabia');
create type payment_status as enum ('pending', 'completed', 'failed', 'refunded');

create table payments (
    id uuid primary key default gen_random_uuid(),
    order_id text not null references orders(id),
    payer_id uuid not null references profiles(id),
    payee_id uuid not null references profiles(id),
    amount numeric(10, 2) not null,
    currency text not null default 'DZD',
    method payment_method not null,
    status payment_status not null default 'pending',
    transaction_id text,
    notes text,
    created_at timestamptz not null default now(),
    completed_at timestamptz
);

-- ============================================================
-- ratings — one per order, sender rates courier after delivery
-- ============================================================
-- profiles.rating is recomputed from these rows by the recompute_rating trigger
-- (SECURITY DEFINER, because rating is deliberately outside the client-writable
-- column grant, in the same protected class as balance and role). rating_count is
-- kept alongside it so "5.0 with no reviews" can be told apart from "5.0 earned".
-- There is no UPDATE or DELETE policy: a review, once left, is final.
create table ratings (
    id uuid primary key default gen_random_uuid(),
    order_id text not null references orders(id),
    rater_id uuid not null references profiles(id),
    ratee_id uuid not null references profiles(id),
    stars int not null check (stars between 1 and 5),
    comment text,
    created_at timestamptz not null default now(),
    unique (order_id, rater_id)
);

-- ============================================================
-- kyc_documents — courier identity verification
-- ============================================================
create type kyc_doc_type as enum ('cin', 'permis', 'carte_grise');
create type kyc_doc_status as enum ('pending', 'approved', 'rejected');

create table kyc_documents (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references profiles(id),
    doc_type kyc_doc_type not null,
    file_url text not null,
    status kyc_doc_status not null default 'pending',
    reviewed_by uuid references profiles(id),
    created_at timestamptz not null default now()
);

-- ============================================================
-- withdrawals — courier cash-out requests (spec p.6 "Retraits")
-- ============================================================
-- Lifecycle:
--   pending --approve--> approved --transfer--> paid       (money gone)
--      │                     └----cancel-----> cancelled   (refund)
--      └---reject---------------------------> rejected     (refund)
--
-- 'paid' is separate from 'approved' because approving is a decision while paying
-- is a settlement done by a human through a bank/CCP transfer this system does not
-- control. Without it, "approved but not yet actually paid" is unanswerable.
--
-- 'cancelled' is separate from 'rejected': rejected = the request was refused;
-- cancelled = it was approved but the payout was abandoned. The courier needs to
-- be told different things in each case. A merely failed transfer attempt is
-- neither — it changes nothing, and mark_withdrawal_paid can simply be retried.
--
-- Order matters: status.asc lists actionable rows (pending, approved) first.
create type withdrawal_status as enum ('pending', 'approved', 'paid', 'cancelled', 'rejected');

create table withdrawals (
    id               uuid primary key default gen_random_uuid(),
    courier_id       uuid not null references profiles(id),
    amount           numeric(10, 2) not null check (amount > 0),
    status           withdrawal_status not null default 'pending',
    requested_at     timestamptz not null default now(),
    resolved_at      timestamptz,
    resolved_by      uuid references profiles(id),
    -- Real-world transfer id (bank reference / CCP / BaridiMob receipt): the link
    -- between this record and the actual movement of money.
    payout_reference text,
    note             text
);

create index withdrawals_courier_idx on withdrawals (courier_id, requested_at desc);

-- ============================================================
-- Row Level Security
-- ============================================================
create or replace function is_admin() returns boolean as $$
    select exists (
        select 1 from profiles where id = auth.uid() and role = 'admin'
    );
$$ language sql security definer stable;

alter table profiles enable row level security;
alter table orders enable row level security;
alter table offers enable row level security;
alter table messages enable row level security;
alter table courier_locations enable row level security;
alter table payments enable row level security;
alter table ratings enable row level security;
alter table kyc_documents enable row level security;

-- profiles: everyone can read active courier profiles (needed to browse offers);
-- only the owner can write their own row; admins can do anything.
create policy "profiles_select" on profiles for select
    using (id = auth.uid() or role = 'delivery' or is_admin());
create policy "profiles_update_own" on profiles for update
    using (id = auth.uid() or is_admin());
-- Only 'sender' and 'delivery' may be self-registered. Without the role check any
-- authenticated user could insert their own profile as an admin, which unlocks
-- is_admin() — approving their own withdrawals and reading every order/profile.
-- Admins are created deliberately via SQL/service_role only.
create policy "profiles_insert_own" on profiles for insert
    with check (
        id = auth.uid()
        and role in ('sender', 'delivery')
    );

-- orders: sender and assigned courier can see/manage their own; any active courier
-- can see pending orders in their wilaya to make an offer; admins see all.
create policy "orders_select" on orders for select
    using (
        sender_id = auth.uid()
        or delivery_id = auth.uid()
        or status = 'pending'
        or is_admin()
    );
create policy "orders_insert_own" on orders for insert
    with check (sender_id = auth.uid());
create policy "orders_update" on orders for update
    using (sender_id = auth.uid() or delivery_id = auth.uid() or is_admin());

-- offers: courier manages their own offers; sender sees offers on their own orders.
create policy "offers_select" on offers for select
    using (
        courier_id = auth.uid()
        or is_admin()
        or exists (select 1 from orders o where o.id = order_id and o.sender_id = auth.uid())
    );
create policy "offers_insert_own" on offers for insert
    with check (courier_id = auth.uid());
create policy "offers_update" on offers for update
    using (
        courier_id = auth.uid()
        or exists (select 1 from orders o where o.id = order_id and o.sender_id = auth.uid())
    );

-- messages: only the order's sender/courier can read or post.
create policy "messages_select" on messages for select
    using (
        is_admin()
        or exists (
            select 1 from orders o where o.id = order_id
            and (o.sender_id = auth.uid() or o.delivery_id = auth.uid())
        )
    );
create policy "messages_insert" on messages for insert
    with check (
        sender_id = auth.uid()
        and exists (
            select 1 from orders o where o.id = order_id
            and (o.sender_id = auth.uid() or o.delivery_id = auth.uid())
        )
    );

-- A conversation is append-only: there is deliberately no UPDATE or DELETE policy,
-- so nobody can rewrite or erase what was said. Verified with a real user JWT — a
-- PATCH and a DELETE both returned 2xx (PostgREST reports success even when RLS
-- matched zero rows) yet left every row untouched.
--
-- The grants are revoked as a second, independent layer: Postgres checks privileges
-- before RLS, so the day someone adds an "edit your own message" policy it cannot
-- reach further than intended. FK cascade from orders still works — cascading
-- deletes are executed by the system, not under the calling role's grants.
revoke update, delete, truncate, references, trigger on table messages from anon;
revoke update, delete, truncate, references, trigger on table messages from authenticated;

-- courier_locations: courier writes their own ping; sender of that order + admin can read.
create policy "locations_select" on courier_locations for select
    using (
        courier_id = auth.uid()
        or is_admin()
        or exists (select 1 from orders o where o.id = order_id and o.sender_id = auth.uid())
    );
create policy "locations_upsert" on courier_locations for insert
    with check (courier_id = auth.uid());
create policy "locations_update" on courier_locations for update
    using (courier_id = auth.uid());

-- payments: payer/payee can read; writes go through a service-role edge function only.
create policy "payments_select" on payments for select
    using (payer_id = auth.uid() or payee_id = auth.uid() or is_admin());

-- ratings: participants of the order can read; rater can insert their own rating.
create policy "ratings_select" on ratings for select
    using (rater_id = auth.uid() or ratee_id = auth.uid() or is_admin());
create policy "ratings_insert" on ratings for insert
    with check (
        rater_id = auth.uid()
        and exists (
            select 1 from orders o where o.id = order_id
            and (o.sender_id = auth.uid() or o.delivery_id = auth.uid())
            and o.status = 'livre'
        )
    );

-- withdrawals: readable by the courier and admins. Deliberately no insert/update
-- policy — rows are created only by request_withdrawal() and resolved only by
-- resolve_withdrawal(), so a client cannot fabricate or self-approve a payout.
alter table withdrawals enable row level security;
create policy "withdrawals_select" on withdrawals for select
    using (courier_id = auth.uid() or is_admin());

-- Second, independent layer. Postgres checks GRANTS before RLS, so removing the
-- write privileges means a bug in one policy cannot by itself expose the payout
-- table. The SECURITY DEFINER functions (request_withdrawal, resolve_withdrawal,
-- mark_withdrawal_paid) run as the table owner and are unaffected.
revoke all on table withdrawals from anon;
revoke insert, update, delete, truncate, references, trigger
    on table withdrawals from authenticated;

-- Money (balance), trust (rating), verification (kyc_status) and authorisation
-- (role) must never be writable by a client: without this a courier could PATCH
-- their own balance, or set role='admin'. Those columns change only through
-- SECURITY DEFINER functions and triggers.
revoke update on table profiles from authenticated, anon;
grant update (full_name, phone, wilaya, commune, vehicle_type, profile_image_url, is_active)
    on table profiles to authenticated;

-- kyc_documents: owner can read/insert their own; only admins can review (update).
create policy "kyc_select" on kyc_documents for select
    using (user_id = auth.uid() or is_admin());
create policy "kyc_insert_own" on kyc_documents for insert
    with check (user_id = auth.uid());
create policy "kyc_review" on kyc_documents for update
    using (is_admin());

-- ============================================================
-- orders: payout and lifecycle integrity
-- ============================================================
-- The client changes exactly one column on an order: OrdersApi.updateStatus sends
-- {status}. Everything else -- prices, delivery_id, the timestamps, the paid marker
-- -- is set by the server. Before this was locked down, a party to an order could
-- PATCH negotiated_price (measured: 200 -> 999999) and be paid that amount.
-- accept_offer is SECURITY DEFINER and runs as the table owner, so assignment still
-- works despite the client having no grant on delivery_id.
revoke update on table orders from anon;
revoke update on table orders from authenticated;
grant update (status) on table orders to authenticated;

-- Pays the courier once per order, ever.
--
-- The guard is "has this order paid out" (courier_paid_at is null), not "did the
-- status just become livre". The earlier version tested the transition, so replaying
-- livre -> en_route -> livre paid the same fee on every cycle -- a courier could mint
-- balance and withdraw it. A recorded payment cannot be replayed the way a
-- transition can.
--
-- BEFORE, not AFTER: an AFTER trigger cannot write NEW, and setting the marker with
-- a second UPDATE would re-enter this trigger.
create or replace function credit_courier_on_delivery()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_wallet_payment boolean;
begin
    -- The column belongs to this trigger, not the caller: even if the grant above
    -- were ever widened, a client could not clear the marker to unlock a payout.
    new.courier_paid_at := old.courier_paid_at;

    if new.status = 'livre'
       and new.delivery_id is not null
       and new.courier_paid_at is null then

        -- Check if the platform collected funds via in-app wallet payment
        select exists (
            select 1 from payments
            where order_id = new.id and method = 'wallet' and status = 'completed'
        ) into v_wallet_payment;

        -- If paid by wallet, credit the courier balance on the platform.
        -- If paid in physical cash, the courier collects cash at the door directly,
        -- so crediting platform balance would double-pay the courier.
        if v_wallet_payment then
            update profiles
               set balance = balance + coalesce(new.negotiated_price, new.asking_price, 0)
             where id = new.delivery_id;
        end if;

        new.courier_paid_at := now();
    end if;

    return new;
end;
$$;

-- The lifecycle only moves forward. Reversible status is what made the payout loop
-- possible, and it also produced incoherent rows: going back through colis_recupere
-- re-stamps picked_up_at, leaving delivered_at earlier than picked_up_at.
-- Skipping ahead stays legal -- it is not a security boundary, since a courier can
-- tap through the steps anyway.
create or replace function enforce_status_transition()
returns trigger
language plpgsql
set search_path to 'public', 'pg_temp'
as $$
declare
    old_rank   int;
    new_rank   int;
    is_sender  boolean;
    is_courier boolean;
begin
    if new.status = old.status then
        return new;
    end if;

    -- No end user in the request (migrations, service_role, admin tooling). anon
    -- never gets here: orders_update rejects it first.
    if auth.uid() is null or is_admin() then
        return new;
    end if;

    if old.status in ('livre', 'annule') then
        raise exception 'Le statut % est définitif pour la commande %', old.status, new.id
            using errcode = 'check_violation';
    end if;

    is_sender  := (new.sender_id = auth.uid());
    is_courier := (new.delivery_id = auth.uid());

    -- Cancelling is the client's call, and only the client's: the app offers no
    -- cancel action to couriers, and "the courier dropped it" needs to return the
    -- order to the pool rather than kill it, which is a feature that does not exist.
    if new.status = 'annule' then
        if not is_sender then
            raise exception 'Annulation réservée au client'
                using errcode = 'check_violation';
        end if;
        return new;
    end if;

    -- Accepting an offer. accept_offer is SECURITY DEFINER, but auth.uid() reads the
    -- request JWT rather than the database role, so inside it this is still the
    -- sender -- which is correct: choosing a courier IS the sender's action. Without
    -- this branch, locking advancement to the courier would break assignment.
    if is_sender and old.status = 'pending' and new.status = 'prise_en_charge' then
        return new;
    end if;

    -- Everything else on the lifecycle is the courier reporting their own progress.
    -- Before this, a sender could mark their own order 'livre' and trigger the payout
    -- without a delivery having happened.
    if not is_courier then
        raise exception 'Seul le livreur assigné peut faire avancer la livraison'
            using errcode = 'check_violation';
    end if;

    old_rank := case old.status
        when 'pending' then 0 when 'prise_en_charge' then 1 when 'vers_depart' then 2
        when 'colis_recupere' then 3 when 'en_route' then 4 when 'livre' then 5 end;
    new_rank := case new.status
        when 'pending' then 0 when 'prise_en_charge' then 1 when 'vers_depart' then 2
        when 'colis_recupere' then 3 when 'en_route' then 4 when 'livre' then 5 end;

    -- Skipping ahead stays allowed: not a security boundary (the courier can tap
    -- through the steps anyway) and blocking it would break offline catch-up.
    if new_rank <= old_rank then
        raise exception 'Transition de statut invalide : % vers %', old.status, new.status
            using errcode = 'check_violation';
    end if;

    return new;
end;
$$;

-- Timestamps describe where the order IS, not the path it took. The old version only
-- ever wrote a stamp, and only on the transition that first produced it: a correction
-- backwards left the row still claiming a pickup that had been undone (this is how
-- delivered_at ended up EARLIER than picked_up_at), and an order that jumped straight
-- to 'livre' was delivered without ever having been picked up.
create or replace function stamp_order_timestamps()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    r int;
begin
    -- Both columns are owned by this trigger. Whatever the caller sent is discarded
    -- before anything is derived, so a stamp cannot be forged or cleared from
    -- outside -- true for every caller, not just those missing the grant.
    new.picked_up_at := old.picked_up_at;
    new.delivered_at := old.delivered_at;

    -- 'annule' is an exit, not a stage: whatever already happened stays recorded.
    if new.status = 'annule' then
        return new;
    end if;

    r := case new.status
        when 'pending' then 0 when 'prise_en_charge' then 1 when 'vers_depart' then 2
        when 'colis_recupere' then 3 when 'en_route' then 4 when 'livre' then 5 end;

    if r >= 3 then
        if new.picked_up_at is null then
            new.picked_up_at := now();
        end if;
    else
        new.picked_up_at := null;
    end if;

    if r = 5 then
        if new.delivered_at is null then
            new.delivered_at := now();
        end if;
        -- A jump straight to 'livre' skips the pickup stamp entirely; an order
        -- cannot have been delivered before it was collected.
        if new.picked_up_at is null or new.picked_up_at > new.delivered_at then
            new.picked_up_at := new.delivered_at;
        end if;
    else
        new.delivered_at := null;
    end if;

    return new;
end;
$$;

-- BEFORE triggers fire in alphabetical order, so the name matters: an illegal
-- transition is rejected before the credit logic is considered.
create trigger orders_check_status_transition
before update on orders
for each row execute function enforce_status_transition();

create trigger orders_credit_courier
before update on orders
for each row execute function credit_courier_on_delivery();

create trigger orders_stamp_timestamps
before update on orders
for each row execute function stamp_order_timestamps();

-- ============================================================
-- device_tokens — where to send a push for a given user
-- ============================================================
-- The FCM registration token is the primary key rather than a surrogate id, because
-- a token identifies one app install globally. That matters when a phone changes
-- hands: FCM reissues the same token to whoever signs in next, and the row has to
-- move with it, or the previous account keeps being addressed by a device it no
-- longer has.
create table device_tokens (
    token      text primary key,
    user_id    uuid not null references profiles(id) on delete cascade,
    platform   text not null default 'android',
    updated_at timestamptz not null default now()
);

create index device_tokens_user_idx on device_tokens (user_id);

alter table device_tokens enable row level security;

-- Read and retire your own; registration goes through the function below.
create policy "device_tokens_select" on device_tokens for select
    using (user_id = auth.uid());
create policy "device_tokens_delete" on device_tokens for delete
    using (user_id = auth.uid());

revoke all on table device_tokens from anon;
revoke all on table device_tokens from authenticated;
grant select, delete on table device_tokens to authenticated;

-- Registration is privileged, so it is a function rather than an upsert.
--
-- An UPDATE policy's USING clause is evaluated against the row as it currently
-- stands, so a token still owned by the previous user fails `user_id = auth.uid()`
-- for the new one -- measured: the upsert returned 403 and the handover silently did
-- not happen. Relaxing USING to `true` would fix that by letting any authenticated
-- user claim any token they can name, turning a leaked token into a way to redirect
-- someone else's notifications. Running as the table owner avoids both.
create or replace function register_device_token(
    p_token text,
    p_platform text default 'android'
)
returns void
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentification requise';
    end if;
    if p_token is null or length(p_token) < 20 then
        raise exception 'Jeton invalide';
    end if;

    insert into device_tokens (token, user_id, platform, updated_at)
    values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
    on conflict (token) do update
        set user_id    = auth.uid(),
            platform   = excluded.platform,
            updated_at = now();
end;
$$;

grant execute on function register_device_token(text, text) to authenticated;

-- Sending happens in the send-push Edge Function, not here: FCM v1 authenticates
-- with a Google service-account private key, which must never ship inside an APK.
-- That function reads device_tokens with service_role -- reading another user's
-- tokens is precisely what the policies above forbid everyone else.

-- ============================================================
-- payments: recording that money changed hands
-- ============================================================
-- One payment per order. Without this a retried tap, a stale screen or a replayed
-- request pays the same delivery twice -- the same class of bug as the courier
-- double-credit.
alter table payments add constraint payments_order_unique unique (order_id);

-- Clients read their own payments and write none directly: both ways of paying go
-- through a function, because both decide an amount and one moves a balance.
revoke all on table payments from anon;
revoke all on table payments from authenticated;
grant select on table payments to authenticated;

-- Spending the in-app balance. Replaces a client-side debit that compared and
-- subtracted against local SQLite and so never reached profiles.balance at all --
-- and had it worked, an amount chosen by the client would have been spendable
-- against a balance checked by the client.
create or replace function pay_with_wallet(p_order_id text)
returns payments
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_order   orders;
    v_amount  numeric;
    v_balance numeric;
    v_row     payments;
begin
    select * into v_order from orders where id = p_order_id;
    if not found then raise exception 'Commande introuvable'; end if;
    if v_order.sender_id <> auth.uid() then
        raise exception 'Seul le client peut payer cette commande';
    end if;
    if v_order.delivery_id is null then raise exception 'Aucun livreur assigné'; end if;

    -- Before the balance check, deliberately. With it after, paying twice reported
    -- "Solde insuffisant" (the first payment having spent the money) and told the
    -- user to top up for a delivery they had already paid for.
    if exists (select 1 from payments where order_id = p_order_id) then
        raise exception 'Cette commande a déjà été payée';
    end if;

    -- Server-side. The client asking what it owes is the same mistake as letting it
    -- set negotiated_price.
    v_amount := coalesce(v_order.negotiated_price, v_order.asking_price, 0);
    if v_amount <= 0 then raise exception 'Montant invalide'; end if;

    -- FOR UPDATE holds the row for the transaction, so two taps landing together
    -- cannot both read the same balance and both pass the check.
    select balance into v_balance from profiles where id = auth.uid() for update;
    if v_balance < v_amount then raise exception 'Solde insuffisant'; end if;

    update profiles set balance = balance - v_amount where id = auth.uid();

    insert into payments (order_id, payer_id, payee_id, amount, method, status, completed_at)
    values (p_order_id, v_order.sender_id, v_order.delivery_id, v_amount,
            'wallet', 'completed', now())
    returning * into v_row;
    return v_row;
exception
    when unique_violation then raise exception 'Cette commande a déjà été payée';
end;
$$;

-- Cash is not a transfer the platform performs; it is a physical event the courier
-- attests to. So this records a fact and moves no balance.
--
-- OPEN QUESTION (business model, not code): credit_courier_on_delivery already adds
-- the fee to the courier's balance on delivery, which models "the platform collects
-- and pays the courier". If the customer instead hands cash straight to the courier,
-- the courier holds the money AND gains the balance. Whether a cash payment should
-- offset that credit is a decision about how WASSIL settles with its couriers, and
-- is deliberately not assumed here.
create or replace function record_cash_payment(p_order_id text)
returns payments
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_order  orders;
    v_amount numeric;
    v_row    payments;
begin
    select * into v_order from orders where id = p_order_id;
    if not found then raise exception 'Commande introuvable'; end if;
    if v_order.delivery_id is distinct from auth.uid() then
        raise exception 'Seul le livreur assigné peut confirmer un paiement en espèces';
    end if;
    -- Paiement à la livraison: nothing to confirm before the parcel arrives.
    if v_order.status <> 'livre' then
        raise exception 'La commande doit être livrée avant de confirmer le paiement';
    end if;

    v_amount := coalesce(v_order.negotiated_price, v_order.asking_price, 0);

    insert into payments (order_id, payer_id, payee_id, amount, method, status, completed_at)
    values (p_order_id, v_order.sender_id, v_order.delivery_id, v_amount,
            'cash', 'completed', now())
    returning * into v_row;
    return v_row;
exception
    when unique_violation then raise exception 'Cette commande a déjà été payée';
end;
$$;

grant execute on function pay_with_wallet(text) to authenticated;
grant execute on function record_cash_payment(text) to authenticated;

-- ============================================================
-- kyc_documents: on-device OCR fields
-- ============================================================
-- Text read off the document by ML Kit on the courier's own phone.
--
-- IMPORTANT: a triage aid for the reviewing admin, never a control. The recognition
-- runs on the applicant's device, so its output is client-supplied and a determined
-- applicant could send whatever text they liked. What makes that acceptable is that
-- it decides nothing: the admin still opens the actual image, and approval still
-- happens only through review_kyc_document. Treating this as verification would be
-- security theatre -- doing it trustworthily needs a server-side vision API.
alter table kyc_documents add column ocr_text text;
alter table kyc_documents add column ocr_name_matches boolean;

-- The verdict is computed here rather than accepted from the client. The raw text can
-- be forged either way, but the rule stays consistent and cannot be flipped by simply
-- posting {"ocr_name_matches": true} -- measured: a row claiming true on unrelated
-- text was stored as false.


create or replace function compute_ocr_name_match()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_name     text;
    v_haystack text;
    v_token    text;
    v_checked  int := 0;
begin
    -- Whatever the client sent is discarded before anything is derived.
    new.ocr_name_matches := null;

    if new.ocr_text is null or length(new.ocr_text) < 3 then
        return new;
    end if;

    select full_name into v_name from profiles where id = new.user_id;
    if v_name is null then
        return new;
    end if;

    -- Fold both sides to bare uppercase letters. ID cards transliterate names without
    -- accents and in inconsistent order, so comparing whole strings would almost
    -- always fail; token containment is the workable test.
    v_haystack := upper(translate(new.ocr_text,
        'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇç',
        'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'));
    v_haystack := regexp_replace(v_haystack, '[^A-Z0-9]+', ' ', 'g');

    v_name := upper(translate(v_name,
        'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇç',
        'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'));
    v_name := regexp_replace(v_name, '[^A-Z ]+', ' ', 'g');

    new.ocr_name_matches := true;
    foreach v_token in array regexp_split_to_array(trim(v_name), '\s+') loop
        -- Short fragments ("EL", "BEN") match almost any text, so they are skipped
        -- rather than allowed to manufacture a false confirmation.
        if length(v_token) >= 3 then
            v_checked := v_checked + 1;
            if position(v_token in v_haystack) = 0 then
                new.ocr_name_matches := false;
            end if;
        end if;
    end loop;

    -- No usable token means no opinion, which is different from "does not match".
    if v_checked = 0 then
        new.ocr_name_matches := null;
    end if;

    return new;
end;
$$;

create trigger kyc_ocr_match
before insert or update on kyc_documents
for each row execute function compute_ocr_name_match();

-- ============================================================
-- accept_offer — atomic courier assignment & offer resolution
-- ============================================================
create or replace function accept_offer(p_offer_id uuid)
returns orders
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_offer offers;
    v_order orders;
begin
    select * into v_offer from offers where id = p_offer_id;
    if not found then
        raise exception 'Offre introuvable';
    end if;

    if v_offer.status <> 'pending' then
        raise exception 'Cette offre ne peut plus être acceptée';
    end if;

    select * into v_order from orders where id = v_offer.order_id for update;
    if not found then
        raise exception 'Commande introuvable';
    end if;

    if v_order.sender_id <> auth.uid() then
        raise exception 'Seul le client peut accepter une offre';
    end if;

    if v_order.status <> 'pending' then
        raise exception 'La commande a déjà été attribuée ou traitée';
    end if;

    -- Update the order assignment and price
    update orders
       set delivery_id = v_offer.courier_id,
           negotiated_price = v_offer.offered_price,
           status = 'prise_en_charge'
     where id = v_order.id
    returning * into v_order;

    -- Mark accepted offer
    update offers
       set status = 'accepted'
     where id = p_offer_id;

    -- Reject all competing pending offers for the same order
    update offers
       set status = 'rejected'
     where order_id = v_order.id
       and id <> p_offer_id
       and status = 'pending';

    return v_order;
end;
$$;

grant execute on function accept_offer(uuid) to authenticated;

-- ============================================================
-- review_kyc_document — admin review & courier kyc_status sync
-- ============================================================
create or replace function review_kyc_document(
    p_doc_id uuid,
    p_approve boolean
)
returns kyc_documents
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_doc       kyc_documents;
    v_user_id   uuid;
    v_cin_stat  kyc_doc_status;
    v_prm_stat  kyc_doc_status;
    v_new_stat  text;
begin
    if not is_admin() then
        raise exception 'Action réservée aux administrateurs';
    end if;

    select * into v_doc from kyc_documents where id = p_doc_id for update;
    if not found then
        raise exception 'Document KYC introuvable';
    end if;

    update kyc_documents
       set status = case when p_approve then 'approved'::kyc_doc_status else 'rejected'::kyc_doc_status end,
           reviewed_by = auth.uid()
     where id = p_doc_id
    returning * into v_doc;

    v_user_id := v_doc.user_id;

    select status into v_cin_stat
      from kyc_documents
     where user_id = v_user_id and doc_type = 'cin'
     order by created_at desc
     limit 1;

    select status into v_prm_stat
      from kyc_documents
     where user_id = v_user_id and doc_type = 'permis'
     order by created_at desc
     limit 1;

    select status into v_cg_stat
      from kyc_documents
     where user_id = v_user_id and doc_type = 'carte_grise'
     order by created_at desc
     limit 1;

    if v_cin_stat = 'approved' and v_prm_stat = 'approved' and v_cg_stat = 'approved' then
        v_new_stat := 'verified';
    elsif v_cin_stat = 'rejected' or v_prm_stat = 'rejected' or v_cg_stat = 'rejected' then
        v_new_stat := 'rejected';
    elsif v_cin_stat = 'pending' or v_prm_stat = 'pending' or v_cg_stat = 'pending' then
        v_new_stat := 'pending';
    else
        v_new_stat := 'unverified';
    end if;

    update profiles
       set kyc_status = v_new_stat
     where id = v_user_id;

    return v_doc;
end;
$$;

grant execute on function review_kyc_document(uuid, boolean) to authenticated;

-- ============================================================
-- withdrawals — courier payout requests & admin resolution
-- ============================================================
create or replace function request_withdrawal(p_amount numeric)
returns withdrawals
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_balance numeric;
    v_row     withdrawals;
begin
    if auth.uid() is null then
        raise exception 'Authentification requise';
    end if;

    if p_amount is null or p_amount <= 0 then
        raise exception 'Montant de retrait invalide';
    end if;

    select balance into v_balance
      from profiles
     where id = auth.uid()
     for update;

    if not found then
        raise exception 'Profil introuvable';
    end if;

    if v_balance < p_amount then
        raise exception 'Solde insuffisant pour ce montant de retrait';
    end if;

    -- Debit balance immediately upon request to reserve the funds
    update profiles
       set balance = balance - p_amount
     where id = auth.uid();

    insert into withdrawals (courier_id, amount, status, requested_at)
    values (auth.uid(), p_amount, 'pending', now())
    returning * into v_row;

    return v_row;
end;
$$;

create or replace function resolve_withdrawal(
    p_withdrawal_id uuid,
    p_approve boolean,
    p_note text default null
)
returns withdrawals
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_row withdrawals;
begin
    if not is_admin() then
        raise exception 'Action réservée aux administrateurs';
    end if;

    select * into v_row from withdrawals where id = p_withdrawal_id for update;
    if not found then
        raise exception 'Demande de retrait introuvable';
    end if;

    if v_row.status <> 'pending' then
        raise exception 'Cette demande a déjà été traitée';
    end if;

    if p_approve then
        update withdrawals
           set status = 'approved',
               resolved_at = now(),
               resolved_by = auth.uid(),
               note = coalesce(p_note, note)
         where id = p_withdrawal_id
        returning * into v_row;
    else
        -- If rejected, refund the reserved balance back to courier
        update profiles
           set balance = balance + v_row.amount
         where id = v_row.courier_id;

        update withdrawals
           set status = 'rejected',
               resolved_at = now(),
               resolved_by = auth.uid(),
               note = coalesce(p_note, note)
         where id = p_withdrawal_id
        returning * into v_row;
    end if;

    return v_row;
end;
$$;

create or replace function mark_withdrawal_paid(
    p_withdrawal_id uuid,
    p_payout_reference text
)
returns withdrawals
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_row withdrawals;
begin
    if not is_admin() then
        raise exception 'Action réservée aux administrateurs';
    end if;

    if p_payout_reference is null or trim(p_payout_reference) = '' then
        raise exception 'La référence de virement (CCP/Banque) est obligatoire';
    end if;

    select * into v_row from withdrawals where id = p_withdrawal_id for update;
    if not found then
        raise exception 'Demande de retrait introuvable';
    end if;

    if v_row.status <> 'approved' then
        raise exception 'Le retrait doit être approuvé avant d''être marqué comme payé';
    end if;

    update withdrawals
       set status = 'paid',
           payout_reference = trim(p_payout_reference)
     where id = p_withdrawal_id
    returning * into v_row;

    return v_row;
end;
$$;

create or replace function cancel_withdrawal(
    p_withdrawal_id uuid,
    p_reason text default null
)
returns withdrawals
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_row withdrawals;
begin
    if not is_admin() then
        raise exception 'Action réservée aux administrateurs';
    end if;

    select * into v_row from withdrawals where id = p_withdrawal_id for update;
    if not found then
        raise exception 'Demande de retrait introuvable';
    end if;

    if v_row.status <> 'approved' then
        raise exception 'Seul un retrait approuvé peut être annulé';
    end if;

    -- Refund the reserved money back to the courier
    update profiles
       set balance = balance + v_row.amount
     where id = v_row.courier_id;

    update withdrawals
       set status = 'cancelled',
           note = coalesce(p_reason, note)
     where id = p_withdrawal_id
    returning * into v_row;

    return v_row;
end;
$$;

grant execute on function request_withdrawal(numeric) to authenticated;
grant execute on function resolve_withdrawal(uuid, boolean, text) to authenticated;
grant execute on function mark_withdrawal_paid(uuid, text) to authenticated;
grant execute on function cancel_withdrawal(uuid, text) to authenticated;

-- ============================================================
-- ratings — automated recomputation trigger
-- ============================================================
create or replace function recompute_profile_rating()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_avg numeric(2, 1);
    v_cnt int;
begin
    select round(avg(stars)::numeric, 1), count(*)
      into v_avg, v_cnt
      from ratings
     where ratee_id = new.ratee_id;

    update profiles
       set rating = coalesce(v_avg, 5.0),
           rating_count = coalesce(v_cnt, 0)
     where id = new.ratee_id;

    return new;
end;
$$;

create trigger ratings_recompute
after insert or update on ratings
for each row execute function recompute_profile_rating();

-- ============================================================
-- Supabase Storage bucket and policies for KYC documents
-- ============================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('kyc', 'kyc', false, 5242880, array['image/jpeg', 'image/png'])
on conflict (id) do nothing;

create policy "kyc_upload_own" on storage.objects for insert
    with check (
        bucket_id = 'kyc'
        and auth.uid()::text = (storage.foldername(name))[1]
    );

create policy "kyc_read_admin_or_owner" on storage.objects for select
    using (
        bucket_id = 'kyc'
        and (
            auth.uid()::text = (storage.foldername(name))[1]
            or is_admin()
        )
    );

-- ============================================================
-- AI Intelligent Delivery Estimator & Continuous Learning Table
-- Calibrated with Algerian carrier benchmark dataset
-- ============================================================
create table if not exists delivery_estimates (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references profiles(id),
    distance_km numeric(6, 2) not null,
    actual_weight_kg numeric(6, 2) not null,
    length_cm numeric(6, 2),
    width_cm numeric(6, 2),
    height_cm numeric(6, 2),
    volumetric_weight_kg numeric(6, 2),
    billable_weight_kg numeric(6, 2) not null,
    package_type text not null,
    pickup_wilaya text,
    drop_wilaya text,
    is_urgent boolean not null default false,
    recommended_vehicle text not null,
    estimated_price numeric(10, 2) not null,
    estimated_minutes int not null,
    created_at timestamptz not null default now()
);

alter table delivery_estimates enable row level security;

create policy "estimates_insert" on delivery_estimates for insert
    with check (auth.uid() is null or user_id = auth.uid());

create policy "estimates_select" on delivery_estimates for select
    using (auth.uid() is null or user_id = auth.uid() or is_admin());

create or replace function estimate_delivery_quote(
    p_distance_km numeric,
    p_weight_kg numeric,
    p_length_cm numeric default null,
    p_width_cm numeric default null,
    p_height_cm numeric default null,
    p_package_type text default 'Colis',
    p_pickup_wilaya text default 'alger',
    p_drop_wilaya text default 'alger',
    p_is_urgent boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_vol_weight numeric := 0;
    v_billable_weight numeric;
    v_base_fare numeric := 300;
    v_km_rate numeric := 35;
    v_kg_rate numeric := 20;
    v_type_mult numeric := 1.0;
    v_urgency_mult numeric := 1.0;
    v_calculated_price numeric;
    v_recommended_vehicle text := 'Moto';
    v_vehicle_reason text := 'Idéal pour petits colis et plis (< 10kg)';
    v_estimated_minutes int;
    v_volume_m3 numeric := 0;
    v_is_inter_wilaya boolean := false;
    v_norm_drop text;
    v_result jsonb;
begin
    -- 1. Calculate volumetric weight & volume: (L * W * H) / 5000
    if p_length_cm is not null and p_width_cm is not null and p_height_cm is not null
       and p_length_cm > 0 and p_width_cm > 0 and p_height_cm > 0 then
        v_volume_m3 := round(((p_length_cm * p_width_cm * p_height_cm) / 1000000.0)::numeric, 4);
        v_vol_weight := round(((p_length_cm * p_width_cm * p_height_cm) / 5000.0)::numeric, 2);
    end if;

    v_billable_weight := greatest(coalesce(p_weight_kg, 1.0), v_vol_weight);

    -- 2. Zone Analysis (Urban vs Regional / Inter-Wilaya)
    v_norm_drop := lower(trim(coalesce(p_drop_wilaya, 'alger')));
    if lower(trim(coalesce(p_pickup_wilaya, 'alger'))) <> v_norm_drop then
        v_is_inter_wilaya := true;
        v_km_rate := 25;
        -- Base fare calibrated against Algerian market benchmark dataset by zone
        if v_norm_drop in ('bechar', 'adrar', 'tamanrasset', 'illizi', 'tindouf', 'djanet', 'in salah', 'in guezzam', 'timimoun', 'beni abbes', 'bordj badji mokhtar') then
            v_base_fare := 1250;
        elsif v_norm_drop in ('biskra', 'ghardaia', 'ouargla', 'el oued', 'touggourt', 'el mghair', 'el menia', 'ouled djellal') then
            v_base_fare := 850;
        elsif v_norm_drop in ('batna', 'djelfa', 'tiaret', 'khenchela', 'souk ahras', 'bordj bou arreridj', 'msila', 'laghouat', 'oum el bouaghi', 'tebessa', 'saida', 'el bayadh', 'naama') then
            v_base_fare := 550;
        else
            v_base_fare := 450;
        end if;
    else
        v_base_fare := 300;
        v_km_rate := 35;
    end if;

    -- 3. Vehicle Recommendation Classifier
    if v_billable_weight > 50 or v_volume_m3 > 0.35 then
        v_recommended_vehicle := 'Camion';
        v_vehicle_reason := 'Colis lourd ou volumineux (> 50kg ou > 0.35m³)';
    elsif v_billable_weight > 10 or v_volume_m3 > 0.04 or p_package_type in ('Fragile', 'Alimentaire') then
        v_recommended_vehicle := 'Voiture';
        v_vehicle_reason := 'Volume moyen ou marchandise fragile (10-50kg)';
    else
        v_recommended_vehicle := 'Moto';
        v_vehicle_reason := 'Idéal pour petits colis et plis (< 10kg)';
    end if;

    -- 4. Package Type Multiplier
    case p_package_type
        when 'Électronique' then v_type_mult := 1.20;
        when 'Electronique' then v_type_mult := 1.20;
        when 'Fragile'      then v_type_mult := 1.25;
        when 'Document'     then v_type_mult := 0.85;
        when 'Documents'    then v_type_mult := 0.85;
        when 'Alimentaire'  then v_type_mult := 1.10;
        else                     v_type_mult := 1.00;
    end case;

    if p_is_urgent then
        v_urgency_mult := 1.35;
    end if;

    -- 5. Calculate Final Price
    v_calculated_price := (v_base_fare + (greatest(p_distance_km, 1.0) * v_km_rate) + (greatest(v_billable_weight - 2.0, 0.0) * v_kg_rate)) * v_type_mult * v_urgency_mult;
    v_calculated_price := round(v_calculated_price / 10.0) * 10.0;
    v_calculated_price := greatest(v_calculated_price, 200.0);

    -- 6. Estimate Delivery Time
    v_estimated_minutes := case v_recommended_vehicle
        when 'Moto'   then ceil((p_distance_km / 25.0) * 60.0) + 10
        when 'Voiture' then ceil((p_distance_km / 20.0) * 60.0) + 15
        else               ceil((p_distance_km / 15.0) * 60.0) + 25
    end;

    if p_is_urgent then
        v_estimated_minutes := greatest(ceil(v_estimated_minutes * 0.75)::int, 15);
    end if;

    -- 7. Log Telemetry
    insert into delivery_estimates (
        user_id, distance_km, actual_weight_kg, length_cm, width_cm, height_cm,
        volumetric_weight_kg, billable_weight_kg, package_type, pickup_wilaya, drop_wilaya,
        is_urgent, recommended_vehicle, estimated_price, estimated_minutes
    ) values (
        auth.uid(), p_distance_km, coalesce(p_weight_kg, 1.0), p_length_cm, p_width_cm, p_height_cm,
        v_vol_weight, v_billable_weight, p_package_type, p_pickup_wilaya, p_drop_wilaya,
        p_is_urgent, v_recommended_vehicle, v_calculated_price, v_estimated_minutes
    );

    v_result := jsonb_build_object(
        'estimated_price', v_calculated_price,
        'min_price', greatest(v_calculated_price - 50, 200),
        'max_price', v_calculated_price + 100,
        'estimated_minutes', v_estimated_minutes,
        'recommended_vehicle', v_recommended_vehicle,
        'vehicle_reason', v_vehicle_reason,
        'billable_weight_kg', v_billable_weight,
        'volumetric_weight_kg', v_vol_weight,
        'volume_m3', v_volume_m3,
        'is_inter_wilaya', v_is_inter_wilaya
    );

    return v_result;
end;
$$;

grant execute on function estimate_delivery_quote(numeric, numeric, numeric, numeric, numeric, text, text, text, boolean) to authenticated, anon;

-- ============================================================
-- platform_settings — global configurable system parameters
-- ============================================================
create table if not exists platform_settings (
    key text primary key,
    value text not null,
    updated_at timestamptz not null default now()
);

alter table platform_settings enable row level security;

create policy "settings_select" on platform_settings for select using (true);
create policy "settings_update_admin" on platform_settings for update
using (is_admin()) with check (is_admin());
create policy "settings_insert_admin" on platform_settings for insert
with check (is_admin());

insert into platform_settings (key, value)
values ('commission_percentage', '15')
on conflict (key) do nothing;

create or replace function set_platform_commission(p_percentage numeric)
returns numeric
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
begin
    if not is_admin() then
        raise exception 'Action réservée aux administrateurs';
    end if;

    if p_percentage < 0 or p_percentage > 100 then
        raise exception 'Le pourcentage de commission doit être entre 0 et 100';
    end if;

    insert into platform_settings (key, value, updated_at)
    values ('commission_percentage', p_percentage::text, now())
    on conflict (key) do update
       set value = excluded.value,
           updated_at = excluded.updated_at;

    return p_percentage;
end;
$$;

create or replace function credit_courier_on_delivery()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_gross numeric;
    v_commission_rate numeric := 15.0;
    v_commission numeric;
    v_net_courier numeric;
begin
    new.courier_paid_at := old.courier_paid_at;

    if new.status = 'livre'
       and new.delivery_id is not null
       and new.courier_paid_at is null then

        select coalesce(value::numeric, 15.0) into v_commission_rate
          from platform_settings
         where key = 'commission_percentage';
        if v_commission_rate is null then
            v_commission_rate := 15.0;
        end if;

        v_gross := coalesce(new.negotiated_price, new.asking_price, 0);
        v_commission := round((v_gross * v_commission_rate / 100.0), 2);
        v_net_courier := v_gross - v_commission;

        update profiles
           set balance = balance + v_net_courier
         where id = new.delivery_id;

        new.courier_paid_at := now();
    end if;

    return new;
end;
$$;



