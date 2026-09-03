package com.example.wassilapp.geo;

import com.example.wassilapp.geo.dto.DirectionsResponse;
import com.example.wassilapp.geo.dto.GeocodeResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Distance, duration and place names from Mapbox. Callbacks land on the main thread.
 *
 * <p>Every method degrades rather than fails. A delivery app that cannot quote a
 * price because a third-party API is slow is worse than one that quotes a clearly
 * labelled estimate, so the caller is always told which of the two it received.
 */
public class GeoRepository {

    /** The road distance between two points, and how it was obtained. */
    public static class RouteResult {
        public final double distanceKm;
        public final int minutes;
        /** False when this came from the haversine fallback rather than Mapbox. */
        public final boolean fromRoadNetwork;

        RouteResult(double distanceKm, int minutes, boolean fromRoadNetwork) {
            this.distanceKm = distanceKm;
            this.minutes = minutes;
            this.fromRoadNetwork = fromRoadNetwork;
        }
    }

    public interface RouteCallback {
        void onResult(RouteResult result);
    }

    public interface PlaceCallback {
        /**
         * @param address full label, or null if nothing was resolved
         * @param wilaya  administrative region, lowercased, or null
         */
        void onResolved(String address, String wilaya);
    }

    private final MapboxGeoApi api = MapboxClient.api();

    /**
     * Road distance and driving time between two points.
     *
     * <p>Never calls back with an error: if Mapbox is unreachable or returns no
     * route, the haversine estimate is delivered instead with fromRoadNetwork
     * false, and it is the caller's job to say so in the UI.
     */
    public void route(double fromLat, double fromLng, double toLat, double toLng,
                      RouteCallback callback) {

        // Mapbox takes longitude before latitude. Locale.US matters: a French or
        // Arabic default locale formats decimals with a comma, which would turn
        // "3.06,36.75" into "3,06,36,75" and produce a nonsense request.
        String coords = String.format(Locale.US, "%f,%f;%f,%f", fromLng, fromLat, toLng, toLat);

        api.directions(coords, MapboxClient.token(), "false", "geojson")
                .enqueue(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call,
                                           Response<DirectionsResponse> response) {
                        DirectionsResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.hasRoute()) {
                            DirectionsResponse.Route r = body.routes.get(0);
                            callback.onResult(new RouteResult(
                                    r.distance / 1000.0,
                                    (int) Math.ceil(r.duration / 60.0),
                                    true));
                        } else {
                            // A 200 with no route is a real case: two points with no
                            // driveable connection between them.
                            callback.onResult(fallback(fromLat, fromLng, toLat, toLng));
                        }
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                        callback.onResult(fallback(fromLat, fromLng, toLat, toLng));
                    }
                });
    }

    public interface ShapeCallback {
        /**
         * @param points the driving route as [lng, lat] pairs in Mapbox order, or
         *               null when no route could be fetched. Callers draw a straight
         *               line between the endpoints instead rather than showing
         *               nothing — an approximate line is still orientation.
         */
        void onShape(List<Point> points);
    }

    /** One vertex of a route. Kept separate from GeoPoint, which carries an address. */
    public static class Point {
        public final double lat;
        public final double lng;

        Point(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    /**
     * The drawable shape of the route between two points.
     *
     * <p>Separate from {@link #route} because the two callers want different things:
     * the price estimate needs a number and asks for overview=false, while the
     * tracking map needs the polyline. Requesting the geometry in both places would
     * download a few kilobytes of coordinates every time someone taps "estimer".
     */
    public void routeShape(double fromLat, double fromLng, double toLat, double toLng,
                           ShapeCallback callback) {

        String coords = String.format(Locale.US, "%f,%f;%f,%f", fromLng, fromLat, toLng, toLat);

        api.directions(coords, MapboxClient.token(), "full", "geojson")
                .enqueue(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call,
                                           Response<DirectionsResponse> response) {
                        DirectionsResponse body = response.body();
                        if (!response.isSuccessful() || body == null || !body.hasRoute()) {
                            callback.onShape(null);
                            return;
                        }
                        DirectionsResponse.Geometry g = body.routes.get(0).geometry;
                        if (g == null || g.coordinates == null || g.coordinates.isEmpty()) {
                            callback.onShape(null);
                            return;
                        }

                        List<Point> points = new ArrayList<>(g.coordinates.size());
                        for (List<Double> pair : g.coordinates) {
                            // GeoJSON order is [longitude, latitude].
                            if (pair != null && pair.size() >= 2) {
                                points.add(new Point(pair.get(1), pair.get(0)));
                            }
                        }
                        callback.onShape(points.isEmpty() ? null : points);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                        callback.onShape(null);
                    }
                });
    }

    private RouteResult fallback(double fromLat, double fromLng, double toLat, double toLng) {
        double km = DistanceCalculator.estimatedRoadKm(fromLat, fromLng, toLat, toLng);
        return new RouteResult(km, DistanceCalculator.estimatedMinutes(km), false);
    }

    /**
     * Turns coordinates into a place name and a wilaya.
     *
     * <p>The wilaya is lowercased before being returned. The orders table already
     * stores it lowercase, and the courier's pending-orders query filters on it with
     * an exact match — so returning Mapbox's "Alger" verbatim would hide every such
     * order from couriers whose profile says "alger".
     */
    public void describe(double lat, double lng, PlaceCallback callback) {
        String lngLat = String.format(Locale.US, "%f,%f", lng, lat);

        api.reverseGeocode(lngLat, MapboxClient.token(), "fr", "address,place,locality,region")
                .enqueue(new Callback<GeocodeResponse>() {
                    @Override
                    public void onResponse(Call<GeocodeResponse> call,
                                           Response<GeocodeResponse> response) {
                        GeocodeResponse body = response.body();
                        GeocodeResponse.Feature f = body != null ? body.first() : null;
                        if (f == null) {
                            callback.onResolved(null, null);
                            return;
                        }
                        callback.onResolved(f.place_name, extractWilaya(f));
                    }

                    @Override
                    public void onFailure(Call<GeocodeResponse> call, Throwable t) {
                        // The pin is still perfectly usable without a name.
                        callback.onResolved(null, null);
                    }
                });
    }

    public static class SearchResult {
        public final String placeName;
        public final double lat;
        public final double lng;
        public final String wilaya;

        public SearchResult(String placeName, double lat, double lng, String wilaya) {
            this.placeName = placeName;
            this.lat = lat;
            this.lng = lng;
            this.wilaya = wilaya;
        }
    }

    public interface SearchCallback {
        void onResults(List<SearchResult> results);
    }

    /**
     * Searches places by name/query using Mapbox Geocoding v5 in Algeria.
     */
    public void searchAddress(String query, double proxLat, double proxLng, SearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onResults(new ArrayList<>());
            return;
        }

        String proximity = String.format(Locale.US, "%f,%f", proxLng, proxLat);

        api.forwardGeocode(query.trim(), MapboxClient.token(), "dz", "fr", proximity)
                .enqueue(new Callback<GeocodeResponse>() {
                    @Override
                    public void onResponse(Call<GeocodeResponse> call, Response<GeocodeResponse> response) {
                        List<SearchResult> results = new ArrayList<>();
                        GeocodeResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.features != null) {
                            for (GeocodeResponse.Feature f : body.features) {
                                if (f.center != null && f.center.size() >= 2) {
                                    // center is [lng, lat]
                                    double lng = f.center.get(0);
                                    double lat = f.center.get(1);
                                    String wilaya = extractWilaya(f);
                                    results.add(new SearchResult(f.place_name != null ? f.place_name : f.text, lat, lng, wilaya));
                                }
                            }
                        }
                        callback.onResults(results);
                    }

                    @Override
                    public void onFailure(Call<GeocodeResponse> call, Throwable t) {
                        callback.onResults(new ArrayList<>());
                    }
                });
    }

    /**
     * Mapbox returns the administrative hierarchy in the feature's context array,
     * each entry tagged by type. The "region" level is what Algeria calls a wilaya.
     * Fallback to "place" if region is not present.
     */
    private String extractWilaya(GeocodeResponse.Feature f) {
        if (f.context == null) {
            return null;
        }
        for (GeocodeResponse.Context c : f.context) {
            if (c.id != null && c.id.startsWith("region") && c.text != null) {
                return c.text.trim().toLowerCase(Locale.ROOT);
            }
        }
        for (GeocodeResponse.Context c : f.context) {
            if (c.id != null && c.id.startsWith("place") && c.text != null) {
                return c.text.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
