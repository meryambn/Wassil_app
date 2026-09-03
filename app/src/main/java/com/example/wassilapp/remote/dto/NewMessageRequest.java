package com.example.wassilapp.remote.dto;

/**
 * Insert payload for public.messages.
 *
 * <p>id and created_at are deliberately absent so the database fills them
 * (gen_random_uuid() / now()). Sending a client-generated created_at would put the
 * phone's clock — which can be wrong by minutes — into the ordering key that both
 * the message list and the polling cursor depend on.
 *
 * <p>sender_id is sent explicitly because the messages_insert policy checks
 * {@code sender_id = auth.uid()}: a mismatch is rejected by the database, so a
 * client cannot post a message under someone else's name.
 */
public class NewMessageRequest {
    public String order_id;
    public String sender_id;
    public String body;

    public NewMessageRequest(String orderId, String senderId, String body) {
        this.order_id = orderId;
        this.sender_id = senderId;
        this.body = body;
    }
}
