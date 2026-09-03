package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.MessageDto;
import com.example.wassilapp.remote.dto.NewMessageRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for public.messages. */
public interface MessagesApi {

    /**
     * return=representation makes PostgREST echo the inserted row back, so the
     * client learns the server-assigned id and created_at instead of guessing them.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    Call<List<MessageDto>> send(@Body NewMessageRequest body);

    /**
     * Messages on one order, oldest first.
     *
     * <p>{@code after} is an optional created_at cursor of the form "gt.&lt;ts&gt;".
     * Retrofit omits a @Query whose value is null, so this one method serves both the
     * first full load (after == null) and every incremental poll after it.
     */
    @GET("rest/v1/messages")
    Call<List<MessageDto>> getForOrder(@Query("order_id") String orderIdFilter,
                                       @Query("created_at") String after,
                                       @Query("order") String order);
}
