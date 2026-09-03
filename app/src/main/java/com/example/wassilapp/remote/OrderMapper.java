package com.example.wassilapp.remote;

import com.example.wassilapp.models.Order;
import com.example.wassilapp.remote.dto.OrderDto;

/**
 * Converts a Supabase order row into the local {@link Order} model the UI already
 * knows how to display.
 *
 * <p>Identity is passed in rather than read from the DTO: the cloud identifies
 * users by UUID while the local model and every existing screen still use the
 * SQLite integer id, so the caller resolves that mapping first.
 */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static Order toOrder(OrderDto dto, int senderLocalId, String senderName,
                                 int deliveryLocalId, String deliveryName) {
        return new Order(
                dto.id,
                senderLocalId,
                senderName,
                deliveryLocalId,
                deliveryName,
                dto.status,
                dto.asking_price,
                dto.negotiated_price != null ? dto.negotiated_price : dto.asking_price,
                dto.pickup_address,
                dto.drop_address,
                dto.pickup_wilaya,
                dto.drop_wilaya,
                dto.distance_km != null ? dto.distance_km : 0,
                dto.estimated_minutes != null ? dto.estimated_minutes : 0,
                dto.created_at,
                dto.picked_up_at,
                dto.delivered_at,
                // The order's two fixed endpoints. These are NOT the courier's live
                // position -- that is tracked separately in courier_locations, and
                // conflating the two is why these were previously hardcoded to zero,
                // which silently dropped the pickup and drop coordinates on every sync.
                // Local field names say sender/delivery; they mean pickup/drop.
                dto.pickup_lat != null ? dto.pickup_lat : 0.0,
                dto.pickup_lng != null ? dto.pickup_lng : 0.0,
                dto.drop_lat != null ? dto.drop_lat : 0.0,
                dto.drop_lng != null ? dto.drop_lng : 0.0,
                dto.package_type,
                dto.weight_kg != null ? dto.weight_kg : 0,
                dto.special_instructions);
    }
}
