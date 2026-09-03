package com.example.wassilapp.geo;

import com.example.wassilapp.geo.dto.DirectionsResponse;
import com.example.wassilapp.geo.dto.GeocodeResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Mapbox REST endpoints. Called over plain HTTP rather than the Maps SDK. */
public interface MapboxGeoApi {

    /**
     * Road distance and driving time between two points.
     *
     * <p>The coordinate pair is already formatted as "lng,lat;lng,lat" and must not
     * be escaped again, hence encoded = true — Retrofit would otherwise turn the
     * separators into %2C and %3B and Mapbox would reject the path.
     *
     * <p>Note the order: Mapbox takes longitude first. Swapping them is the classic
     * mistake here, and it fails silently by returning a route somewhere else in the
     * world rather than an error.
     */
    @GET("directions/v5/mapbox/driving/{coordinates}")
    Call<DirectionsResponse> directions(@Path(value = "coordinates", encoded = true) String coordinates,
                                        @Query("access_token") String token,
                                        @Query("overview") String overview,
                                        @Query("geometries") String geometries);

    /** Coordinates to a place name. Longitude first here too. */
    @GET("geocoding/v5/mapbox.places/{lngLat}.json")
    Call<GeocodeResponse> reverseGeocode(@Path(value = "lngLat", encoded = true) String lngLat,
                                         @Query("access_token") String token,
                                         @Query("language") String language,
                                         @Query("types") String types);

    /** Forward geocoding: search query to place matches. */
    @GET("geocoding/v5/mapbox.places/{query}.json")
    Call<GeocodeResponse> forwardGeocode(@Path(value = "query", encoded = false) String query,
                                        @Query("access_token") String token,
                                        @Query("country") String country,
                                        @Query("language") String language,
                                        @Query("proximity") String proximity);
}
