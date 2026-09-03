package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.KycDocumentDto;
import com.example.wassilapp.remote.dto.NewKycDocumentRequest;
import com.example.wassilapp.remote.dto.ReviewKycRequest;
import com.example.wassilapp.remote.dto.SignedUrlResponse;

import java.util.List;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** PostgREST + Storage endpoints for identity verification. */
public interface KycApi {

    /**
     * Uploads the file itself.
     *
     * <p>The path must begin with the uploader's own uid: the kyc_upload_own storage
     * policy compares that first segment to auth.uid(), which is what confines a
     * courier to their own folder. encoded = true because the path already contains
     * the slashes that separate those segments.
     *
     * <p>x-upsert is false so a second submission cannot silently overwrite the file
     * an admin already reviewed.
     */
    @Headers("x-upsert: false")
    @POST("storage/v1/object/kyc/{path}")
    Call<Void> uploadFile(@Path(value = "path", encoded = true) String path,
                          @Body RequestBody file);

    /**
     * Mints a short-lived link so an admin can view a document.
     *
     * <p>The bucket is private, so there is no permanent URL to hand around; every
     * view is a fresh, expiring grant.
     */
    @POST("storage/v1/object/sign/kyc/{path}")
    Call<SignedUrlResponse> signUrl(@Path(value = "path", encoded = true) String path,
                                    @Body ExpiryRequest body);

    class ExpiryRequest {
        public int expiresIn;

        public ExpiryRequest(int seconds) {
            this.expiresIn = seconds;
        }
    }

    @Headers("Prefer: return=representation")
    @POST("rest/v1/kyc_documents")
    Call<List<KycDocumentDto>> submit(@Body NewKycDocumentRequest body);

    /** My own documents. RLS restricts this to the caller regardless of the filter. */
    @GET("rest/v1/kyc_documents")
    Call<List<KycDocumentDto>> getMine(@Query("user_id") String userFilter,
                                       @Query("order") String order);

    /**
     * The review queue. Returns rows only for an admin — kyc_select narrows everyone
     * else to their own documents, so this is safe to call from any screen.
     */
    @GET("rest/v1/kyc_documents")
    Call<List<KycDocumentDto>> getQueue(@Query("status") String statusFilter,
                                        @Query("order") String order,
                                        @Query("select") String select);

    /** Approval is a function, not a PATCH: UPDATE is granted to nobody. */
    @POST("rest/v1/rpc/review_kyc_document")
    Call<KycDocumentDto> review(@Body ReviewKycRequest body);
}
