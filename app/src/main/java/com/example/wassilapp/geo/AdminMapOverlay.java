package com.example.wassilapp.geo;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.MapboxStyleManager;
import com.mapbox.maps.extension.style.layers.LayerUtils;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.sources.SourceUtils;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages live markers and camera fitting for the Admin Dashboard overview map.
 * Declarative design using Mapbox style sources and circle layers.
 * Safely handles zero markers without crashing.
 */
public final class AdminMapOverlay {

    public static class MapMarker {
        public final double lat;
        public final double lng;
        public final int color;
        public final String title;

        public MapMarker(double lat, double lng, int color, String title) {
            this.lat = lat;
            this.lng = lng;
            this.color = color;
            this.title = title;
        }
    }

    private static final String SRC_COURIER = "admin-courier-src-";
    private static final String LYR_COURIER = "admin-courier-lyr-";
    private static final String SRC_PICKUP = "admin-pickup-src-";
    private static final String LYR_PICKUP = "admin-pickup-lyr-";
    private static final String SRC_DROP = "admin-drop-src-";
    private static final String LYR_DROP = "admin-drop-lyr-";

    public static final int COLOR_COURIER_MOTO = 0xFFFF9800;   // Orange
    public static final int COLOR_COURIER_CAR = 0xFF4CAF50;    // Green
    public static final int COLOR_COURIER_TRUCK = 0xFF9C27B0;  // Purple
    public static final int COLOR_ORDER_PICKUP = 0xFF1E88E5;   // Blue
    public static final int COLOR_ORDER_DROP = 0xFFE53935;     // Red
    private static final int COLOR_HALO = 0xFFFFFFFF;

    private static int lastCourierCount = 0;
    private static int lastPickupCount = 0;
    private static int lastDropCount = 0;

    private AdminMapOverlay() {}

    /**
     * Clears all previously rendered admin markers from the style.
     */
    public static void clear(MapboxStyleManager style) {
        if (style == null) return;

        for (int i = 0; i < lastCourierCount; i++) {
            String lyr = LYR_COURIER + i;
            String src = SRC_COURIER + i;
            if (style.styleLayerExists(lyr)) style.removeStyleLayer(lyr);
            if (style.styleSourceExists(src)) style.removeStyleSource(src);
        }
        lastCourierCount = 0;

        for (int i = 0; i < lastPickupCount; i++) {
            String lyr = LYR_PICKUP + i;
            String src = SRC_PICKUP + i;
            if (style.styleLayerExists(lyr)) style.removeStyleLayer(lyr);
            if (style.styleSourceExists(src)) style.removeStyleSource(src);
        }
        lastPickupCount = 0;

        for (int i = 0; i < lastDropCount; i++) {
            String lyr = LYR_DROP + i;
            String src = SRC_DROP + i;
            if (style.styleLayerExists(lyr)) style.removeStyleLayer(lyr);
            if (style.styleSourceExists(src)) style.removeStyleSource(src);
        }
        lastDropCount = 0;
    }

    /**
     * Renders active couriers and order pins onto the Mapbox style.
     */
    public static void renderMarkers(MapboxStyleManager style,
                                     List<MapMarker> couriers,
                                     List<MapMarker> pickups,
                                     List<MapMarker> drops) {
        if (style == null) return;
        clear(style);

        if (couriers != null) {
            for (int i = 0; i < couriers.size(); i++) {
                MapMarker m = couriers.get(i);
                addCircle(style, SRC_COURIER + i, LYR_COURIER + i, m.lng, m.lat, m.color, 9.0);
            }
            lastCourierCount = couriers.size();
        }

        if (pickups != null) {
            for (int i = 0; i < pickups.size(); i++) {
                MapMarker m = pickups.get(i);
                addCircle(style, SRC_PICKUP + i, LYR_PICKUP + i, m.lng, m.lat, m.color, 7.5);
            }
            lastPickupCount = pickups.size();
        }

        if (drops != null) {
            for (int i = 0; i < drops.size(); i++) {
                MapMarker m = drops.get(i);
                addCircle(style, SRC_DROP + i, LYR_DROP + i, m.lng, m.lat, m.color, 7.5);
            }
            lastDropCount = drops.size();
        }
    }

    private static void addCircle(MapboxStyleManager style, String srcId, String lyrId,
                                  double lng, double lat, int color, double radius) {
        SourceUtils.addSource(style, new GeoJsonSource.Builder(srcId)
                .feature(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                .build());

        LayerUtils.addLayer(style, new CircleLayer(lyrId, srcId)
                .circleColor(color)
                .circleRadius(radius)
                .circleStrokeColor(COLOR_HALO)
                .circleStrokeWidth(2.5));
    }

    /**
     * Dynamically frames all points on the map.
     * If there are no points or only 1 point, safely centers without crashing.
     */
    public static void framePoints(MapboxMap map, List<MapMarker> allPoints,
                                   double viewWidthDp, double viewHeightDp) {
        if (map == null) return;

        List<MapMarker> validPoints = new ArrayList<>();
        if (allPoints != null) {
            for (MapMarker p : allPoints) {
                // Ignore unset 0,0 points (Gulf of Guinea)
                if (p != null && (Math.abs(p.lat) > 0.01 || Math.abs(p.lng) > 0.01)) {
                    validPoints.add(p);
                }
            }
        }

        if (validPoints.isEmpty()) {
            // Default center: Algeria (Algiers overview)
            map.setCamera(new CameraOptions.Builder()
                    .center(Point.fromLngLat(3.0588, 36.7538))
                    .zoom(6.5)
                    .build());
            return;
        }

        if (validPoints.size() == 1) {
            MapMarker p = validPoints.get(0);
            map.setCamera(new CameraOptions.Builder()
                    .center(Point.fromLngLat(p.lng, p.lat))
                    .zoom(12.0)
                    .build());
            return;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;

        for (MapMarker p : validPoints) {
            if (p.lat < minLat) minLat = p.lat;
            if (p.lat > maxLat) maxLat = p.lat;
            if (p.lng < minLng) minLng = p.lng;
            if (p.lng > maxLng) maxLng = p.lng;
        }

        double centreLat = (minLat + maxLat) / 2.0;
        double centreLng = (minLng + maxLng) / 2.0;

        double latSpan = Math.max(Math.abs(maxLat - minLat), 0.005);
        double lngSpan = Math.max(Math.abs(maxLng - minLng), 0.005);

        double w = viewWidthDp > 0 ? viewWidthDp : 360;
        double h = viewHeightDp > 0 ? viewHeightDp : 240;

        double tileSize = 512.0;
        double zoomForWidth = Math.log(360.0 * w / (tileSize * lngSpan)) / Math.log(2);
        double zoomForHeight = Math.log(360.0 * h * Math.cos(Math.toRadians(centreLat))
                / (tileSize * latSpan)) / Math.log(2);

        double zoom = Math.min(zoomForWidth, zoomForHeight) - 0.4;
        zoom = Math.max(4.0, Math.min(15.0, zoom));

        map.setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(centreLng, centreLat))
                .zoom(zoom)
                .build());
    }
}
