package com.example.wassilapp.remote.dto;

/** Mirrors a row of public.ratings — one review of one delivery. */
public class RatingDto {
    public String id;
    public String order_id;
    /** Who wrote the review. */
    public String rater_id;
    /** Who is being reviewed. */
    public String ratee_id;
    public int stars;
    public String comment;
    public String created_at;
}
