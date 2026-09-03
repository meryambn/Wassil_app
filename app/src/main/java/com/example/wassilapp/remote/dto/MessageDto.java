package com.example.wassilapp.remote.dto;

/**
 * Mirrors a row of public.messages — one chat message on one order.
 *
 * <p><b>Careful with {@link #sender_id}.</b> In the orders table that column means
 * "the customer who created the order". Here it means "whoever typed this message",
 * which is just as often the courier. The two are unrelated despite the identical
 * name. Never decide which side of the conversation a message belongs to by
 * comparing it against order.sender_id — compare it against the signed-in user's
 * uid instead ("is this mine?"), which is the one test that works for both roles.
 */
public class MessageDto {
    public String id;
    public String order_id;
    public String sender_id;
    public String body;
    /** Reserved for photo messages; always null until a storage bucket exists. */
    public String image_url;
    public String created_at;
}
