package com.example.wassilapp.remote.dto;

/** Body of the review_kyc_document RPC. */
public class ReviewKycRequest {
    public String p_doc_id;
    public boolean p_approve;

    public ReviewKycRequest(String docId, boolean approve) {
        this.p_doc_id = docId;
        this.p_approve = approve;
    }
}
