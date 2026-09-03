package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewProfileRequest;
import com.example.wassilapp.remote.dto.Profile;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Thin wrapper over {@link ProfilesApi} for Activities. Callbacks land on the main thread. */
public class ProfileRepository {

    public interface ProfileCallback {
        void onSuccess(Profile profile);

        void onError(String message);
    }

    public interface ProfileLookupCallback {
        void onFound(Profile profile);

        void onNotFound();

        void onError(String message);
    }

    private final ProfilesApi api = SupabaseClient.profilesApi();

    public void getById(String userId, ProfileLookupCallback callback) {
        api.getById("eq." + userId, "*").enqueue(new Callback<List<Profile>>() {
            @Override
            public void onResponse(Call<List<Profile>> call, Response<List<Profile>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(errorMessage(response));
                } else if (response.body().isEmpty()) {
                    callback.onNotFound();
                } else {
                    callback.onFound(response.body().get(0));
                }
            }

            @Override
            public void onFailure(Call<List<Profile>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void create(NewProfileRequest request, ProfileCallback callback) {
        api.create(request).enqueue(new Callback<List<Profile>>() {
            @Override
            public void onResponse(Call<List<Profile>> call, Response<List<Profile>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<Profile>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public interface CouriersCallback {
        void onSuccess(List<Profile> couriers);

        void onError(String message);
    }

    public void getCouriers(CouriersCallback callback) {
        api.getCouriers("eq.delivery", "created_at.desc", "*").enqueue(new Callback<List<Profile>>() {
            @Override
            public void onResponse(Call<List<Profile>> call, Response<List<Profile>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<Profile>> call, Throwable t) {
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
