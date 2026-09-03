package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.MessageDto;
import com.example.wassilapp.remote.dto.NewMessageRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Thin wrapper over {@link MessagesApi}. Callbacks land on the main thread.
 *
 * <p>There is deliberately no local SQLite cache behind this. Every other entity in
 * the app is cached because it must survive offline; a conversation is only useful
 * while both parties are connected, and caching it would add a merge problem
 * (local-only messages that were never accepted by the server) for no real gain.
 *
 * <p>Access control lives entirely in the database. The messages_select policy
 * restricts rows to the order's sender or courier, so even if this class asked for
 * another order's conversation the server would return an empty list.
 */
public class MessageRepository {

    public interface MessageListCallback {
        void onSuccess(List<MessageDto> messages);

        void onError(String message);
    }

    public interface SendCallback {
        void onSent(MessageDto message);

        void onError(String message);
    }

    private final MessagesApi api = SupabaseClient.restClient().create(MessagesApi.class);

    /**
     * Loads the conversation for one order.
     *
     * @param afterCreatedAt when non-null, only messages strictly newer than this
     *                       timestamp are returned. Pass the created_at of the last
     *                       message already on screen — a value that came from the
     *                       server itself, never from the device clock, so clock
     *                       skew between the two phones cannot skip or repeat rows.
     */
    public void getMessages(String orderId, String afterCreatedAt, MessageListCallback callback) {
        String after = afterCreatedAt != null ? "gt." + afterCreatedAt : null;
        api.getForOrder("eq." + orderId, after, "created_at.asc")
                .enqueue(new Callback<List<MessageDto>>() {
                    @Override
                    public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void send(String orderId, String myUid, String body, SendCallback callback) {
        api.send(new NewMessageRequest(orderId, myUid, body))
                .enqueue(new Callback<List<MessageDto>>() {
                    @Override
                    public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                        // A successful insert that returns no row means the row was
                        // written but RLS hid it from the response — treat that as a
                        // failure rather than pretending the message was delivered.
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSent(response.body().get(0));
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    private String errorMessage(Response<?> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "Request failed";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
