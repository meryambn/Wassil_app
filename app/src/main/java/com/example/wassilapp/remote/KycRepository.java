package com.example.wassilapp.remote;

import com.example.wassilapp.BuildConfig;
import com.example.wassilapp.remote.dto.KycDocumentDto;
import com.example.wassilapp.remote.dto.NewKycDocumentRequest;
import com.example.wassilapp.remote.dto.ReviewKycRequest;
import com.example.wassilapp.remote.dto.SignedUrlResponse;

import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Identity documents: upload, list, and (for admins) review.
 *
 * <p>Two steps make a submission, in this order: the file goes to the private "kyc"
 * bucket first, and only once that succeeds is a row written pointing at it. The
 * reverse order would leave rows referencing files that do not exist, which an admin
 * would see as a document that cannot be opened and could neither approve nor
 * meaningfully reject.
 */
public class KycRepository {

    /** Required of every courier: national ID, driver's license, and vehicle registration (carte grise). */
    public static final String[] REQUIRED_TYPES = {"cin", "permis", "carte_grise"};

    public interface SubmitCallback {
        void onSubmitted(KycDocumentDto document);

        void onError(String message);
    }

    public interface ListCallback {
        void onSuccess(List<KycDocumentDto> documents);

        void onError(String message);
    }

    public interface SignedUrlCallback {
        void onUrl(String absoluteUrl);

        void onError(String message);
    }

    public interface ReviewCallback {
        void onReviewed(KycDocumentDto document);

        void onError(String message);
    }

    private static final String SELECT_WITH_OWNER =
            "*,owner:profiles!user_id(id,full_name,phone,vehicle_type,rating)";

    private final KycApi api = SupabaseClient.restClient().create(KycApi.class);

    /**
     * Uploads a document and records it.
     *
     * @param bytes    the image, already compressed by the caller
     * @param mimeType e.g. "image/jpeg"
     */
    public void submit(String myUid, String docType, byte[] bytes, String mimeType,
                        String ocrText, SubmitCallback callback) {
        if (myUid == null || bytes == null || bytes.length == 0) {
            callback.onError("Document invalide");
            return;
        }

        // The uid prefix is not decoration: the storage policy reads it as the owner.
        // The timestamp keeps every submission distinct, so a re-upload after a
        // rejection never overwrites the file that was actually reviewed.
        final String path = String.format(Locale.US, "%s/%s-%d.jpg",
                myUid, docType, System.currentTimeMillis());

        RequestBody body = RequestBody.create(bytes, MediaType.parse(mimeType));
        api.uploadFile(path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    callback.onError(errorMessage(response));
                    return;
                }
                recordDocument(myUid, docType, path, ocrText, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private void recordDocument(String myUid, String docType, String path,
                                 String ocrText, SubmitCallback callback) {
        api.submit(new NewKycDocumentRequest(myUid, docType, path, ocrText))
                .enqueue(new Callback<List<KycDocumentDto>>() {
                    @Override
                    public void onResponse(Call<List<KycDocumentDto>> call,
                                           Response<List<KycDocumentDto>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            callback.onSubmitted(response.body().get(0));
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<KycDocumentDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void getMine(String myUid, ListCallback callback) {
        api.getMine("eq." + myUid, "created_at.desc").enqueue(listHandler(callback));
    }

    /** Documents awaiting review. Empty for anyone who is not an admin. */
    public void getPendingQueue(ListCallback callback) {
        api.getQueue("eq.pending", "created_at.asc", SELECT_WITH_OWNER)
                .enqueue(listHandler(callback));
    }

    /**
     * A temporary link to view one document.
     *
     * <p>Five minutes: long enough to look at, short enough that a link copied out of
     * a log or screenshot stops working almost immediately.
     */
    public void signedUrl(String objectPath, SignedUrlCallback callback) {
        api.signUrl(objectPath, new KycApi.ExpiryRequest(300))
                .enqueue(new Callback<SignedUrlResponse>() {
                    @Override
                    public void onResponse(Call<SignedUrlResponse> call,
                                           Response<SignedUrlResponse> response) {
                        SignedUrlResponse b = response.body();
                        if (response.isSuccessful() && b != null && b.signedUrl != null) {
                            // The API returns a path relative to /storage/v1.
                            callback.onUrl(BuildConfig.SUPABASE_URL.replaceAll("/$", "")
                                    + "/storage/v1" + b.signedUrl);
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<SignedUrlResponse> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void review(String docId, boolean approve, ReviewCallback callback) {
        api.review(new ReviewKycRequest(docId, approve)).enqueue(new Callback<KycDocumentDto>() {
            @Override
            public void onResponse(Call<KycDocumentDto> call, Response<KycDocumentDto> response) {
                if (response.isSuccessful()) {
                    callback.onReviewed(response.body());
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<KycDocumentDto> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private Callback<List<KycDocumentDto>> listHandler(ListCallback callback) {
        return new Callback<List<KycDocumentDto>>() {
            @Override
            public void onResponse(Call<List<KycDocumentDto>> call,
                                   Response<List<KycDocumentDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<KycDocumentDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        };
    }

    private String errorMessage(Response<?> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "Request failed";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
