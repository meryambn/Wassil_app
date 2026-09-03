package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewRatingRequest;
import com.example.wassilapp.remote.dto.RatingDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Thin wrapper over {@link RatingsApi}. Callbacks land on the main thread.
 *
 * <p>Note what this class does NOT do: it never writes profiles.rating. That column
 * is not client-writable at all — the column-level UPDATE grant on profiles covers
 * only self-service fields, deliberately leaving rating in the same protected class
 * as balance and role. The average is recomputed by the recompute_rating trigger
 * when a row lands in ratings, so the score always agrees with the reviews behind it
 * and no client can nudge its own number.
 */
public class RatingRepository {

    public interface SubmitCallback {
        void onSubmitted(RatingDto rating);

        void onError(String message);
    }

    public interface ExistingCallback {
        /** @param existing the review this user already left, or null if none. */
        void onResult(RatingDto existing);

        void onError(String message);
    }

    private final RatingsApi api = SupabaseClient.restClient().create(RatingsApi.class);

    public void submit(String orderId, String raterUid, String rateeUid, int stars,
                        String comment, SubmitCallback callback) {
        api.create(new NewRatingRequest(orderId, raterUid, rateeUid, stars, comment))
                .enqueue(new Callback<List<RatingDto>>() {
                    @Override
                    public void onResponse(Call<List<RatingDto>> call, Response<List<RatingDto>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSubmitted(response.body().get(0));
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<RatingDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void findMine(String orderId, String raterUid, ExistingCallback callback) {
        api.getMineForOrder("eq." + orderId, "eq." + raterUid)
                .enqueue(new Callback<List<RatingDto>>() {
                    @Override
                    public void onResponse(Call<List<RatingDto>> call, Response<List<RatingDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onResult(response.body().isEmpty() ? null : response.body().get(0));
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<RatingDto>> call, Throwable t) {
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
