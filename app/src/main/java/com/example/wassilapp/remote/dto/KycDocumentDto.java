package com.example.wassilapp.remote.dto;

/** Mirrors a row of public.kyc_documents. */
public class KycDocumentDto {
    public String id;
    public String user_id;
    /** cin | permis | carte_grise */
    public String doc_type;
    /**
     * Object path inside the private "kyc" bucket, NOT a fetchable URL.
     *
     * <p>Storing a path rather than a link is deliberate: a URL invites code to just
     * open it, whereas a path forces every read through a signed-URL request that
     * re-checks who is asking. These are national ID cards; nothing about them should
     * be reachable by anyone holding a string.
     */
    public String file_url;
    /** pending | approved | rejected */
    public String status;
    public String reviewed_by;
    public String created_at;
    /** Text recognised on-device; an aid to the reviewer, not proof. */
    public String ocr_text;
    /** Trigger-computed: does the profile name appear in ocr_text? */
    public Boolean ocr_name_matches;

    /** Embedded owner, present only on the admin queue query. */
    public OrderDto.ProfileRef owner;
}
