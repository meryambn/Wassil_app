package com.example.wassilapp.remote.dto;

/**
 * Insert payload for public.ratings.
 *
 * <p>Nothing here is trusted. The ratings_insert policy independently re-checks that
 * rater_id equals auth.uid(), that the order belongs to the rater, and that its
 * status is already 'livre' — so a client cannot review a delivery it was not part
 * of, review on someone else's behalf, or review a job that has not happened yet.
 * The unique (order_id, rater_id) constraint stops a second review of the same order.
 */
public class NewRatingRequest {
    public String order_id;
    public String rater_id;
    public String ratee_id;
    public int stars;
    public String comment;

    public NewRatingRequest(String orderId, String raterId, String rateeId,
                             int stars, String comment) {
        this.order_id = orderId;
        this.rater_id = raterId;
        this.ratee_id = rateeId;
        this.stars = stars;
        // Gson omits nulls, so an empty comment lets the column default to NULL
        // instead of storing an empty string that later reads as "left a comment".
        this.comment = (comment == null || comment.trim().isEmpty()) ? null : comment.trim();
    }
}
