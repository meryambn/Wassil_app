package com.example.wassilapp.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Response of POST /storage/v1/object/sign/{bucket}/{path}. */
public class SignedUrlResponse {
    /** Relative, e.g. "/object/sign/kyc/<uid>/cin-1.jpg?token=…". */
    @SerializedName("signedURL")
    public String signedUrl;
}
