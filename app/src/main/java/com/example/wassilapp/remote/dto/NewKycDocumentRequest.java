package com.example.wassilapp.remote.dto;

/**
 * Insert payload for public.kyc_documents.
 *
 * <p>status is deliberately absent so the column default ('pending') applies. Letting
 * the client name a status would let a courier submit a document already marked
 * approved — the kyc_insert_own policy checks who you are, not what you claim.
 */
public class NewKycDocumentRequest {
    public String user_id;
    public String doc_type;
    public String file_url;
    /**
     * Raw text read off the document on-device.
     *
     * <p>Sent for the reviewing admin's benefit, not as evidence. The match
     * verdict is computed by a trigger and any value posted for it is
     * discarded, because OCR performed on the applicant's own phone is
     * exactly as trustworthy as the applicant.
     */
    public String ocr_text;

    public NewKycDocumentRequest(String userId, String docType, String filePath,
                                  String ocrText) {
        this.user_id = userId;
        this.doc_type = docType;
        this.file_url = filePath;
        // Truncated: a full page of recognised text is of no use to a reviewer and
        // would bloat every row.
        this.ocr_text = (ocrText == null || ocrText.trim().isEmpty()) ? null
                : ocrText.trim().substring(0, Math.min(ocrText.trim().length(), 1000));
    }
}
