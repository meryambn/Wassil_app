package com.example.wassilapp.geo;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.MapboxStyleManager;
import com.mapbox.maps.extension.style.layers.LayerUtils;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;
import com.mapbox.maps.extension.style.sources.SourceUtils;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a delivery's route and its two endpoints onto a Mapbox style.
 *
 * <p>Kept out of the Activity because the Activity is already long, and because the
 * ordering rules here are easy to get wrong in a way that fails silently: a layer
 * added before its source exists is simply never rendered, with no exception.
 *
 * <p>Everything is drawn as style layers rather than annotations. Layers are
 * declarative — the same ids are removed and re-added on every draw, so repeated
 * calls cannot accumulate duplicates the way stray marker objects can.
 */
public final class RouteOverlay {

    private static final String SRC_ROUTE = "wassil-route-src";
    private static final String LYR_ROUTE = "wassil-route-lyr";
    private static final String SRC_PICKUP = "wassil-pickup-src";
    private static final String LYR_PICKUP = "wassil-pickup-lyr";
    private static final String SRC_DROP = "wassil-drop-src";
    private static final String LYR_DROP = "wassil-drop-lyr";

    private static final int COLOUR_ROUTE = 0xFF1E88E5;
    private static final int COLOUR_PICKUP = 0xFF43A047;
    private static final int COLOUR_DROP = 0xFFE53935;
    private static final int COLOUR_HALO = 0xFFFFFFFF;

    private RouteOverlay() {
    }

    /**
     * @param shape the driving route, or null. When null a straight line between the
     *              endpoints is drawn instead: it is not the real path, but it still
     *              shows which way the parcel is going, which is better than an empty
     *              map when Directions is unreachable.
     */
    public static void draw(MapboxStyleManager style,
                            double pickupLat, double pickupLng,
                            double dropLat, double dropLng,
                            List<GeoRepository.Point> shape) {

        clear(style);

        List<Point> line = new ArrayList<>();
        if (shape != null && shape.size() >= 2) {
            for (GeoRepository.Point p : shape) {
                line.add(Point.fromLngLat(p.lng, p.lat));
            }
        } else {
            line.add(Point.fromLngLat(pickupLng, pickupLat));
            line.add(Point.fromLngLat(dropLng, dropLat));
        }

        // Source first, then the layer that reads it. The reverse order produces a
        // layer bound to a source that does not exist yet, which renders nothing and
        // reports no error.
        SourceUtils.addSource(style, new GeoJsonSource.Builder(SRC_ROUTE)
                .geometry(LineString.fromLngLats(line))
                .build());
        LayerUtils.addLayer(style, new LineLayer(LYR_ROUTE, SRC_ROUTE)
                .lineColor(COLOUR_ROUTE)
                .lineWidth(5.0)
                .lineOpacity(0.85)
                .lineCap(LineCap.ROUND)
                .lineJoin(LineJoin.ROUND));

        addPoint(style, SRC_PICKUP, LYR_PICKUP, pickupLng, pickupLat, COLOUR_PICKUP);
        addPoint(style, SRC_DROP, LYR_DROP, dropLng, dropLat, COLOUR_DROP);
    }

    private static void addPoint(MapboxStyleManager style, String sourceId, String layerId,
                                 double lng, double lat, int colour) {
        SourceUtils.addSource(style, new GeoJsonSource.Builder(sourceId)
                .feature(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                .build());
        // A circle rather than an icon: a symbol layer would need a bitmap registered
        // in the style first, and a coloured dot reads just as clearly at this size.
        LayerUtils.addLayer(style, new CircleLayer(layerId, sourceId)
                .circleColor(colour)
                .circleRadius(8.0)
                .circleStrokeColor(COLOUR_HALO)
                .circleStrokeWidth(2.5));
    }

    /** Layers before sources: a source still in use by a layer cannot be removed. */
    public static void clear(MapboxStyleManager style) {
        for (String id : new String[]{LYR_ROUTE, LYR_PICKUP, LYR_DROP}) {
            if (style.styleLayerExists(id)) {
                style.removeStyleLayer(id);
            }
        }
        for (String id : new String[]{SRC_ROUTE, SRC_PICKUP, SRC_DROP}) {
            if (style.styleSourceExists(id)) {
                style.removeStyleSource(id);
            }
        }
    }

    /** Mapbox renders 512px tiles, which is what sets the world size at a given zoom. */
    private static final double TILE_SIZE_PX = 512.0;

    /**
     * Frames both endpoints inside a map view of the given pixel size.
     *
     * <p>The zoom has to be computed rather than fixed, because two addresses on the
     * same street and two on opposite sides of the wilaya must both fit the same
     * small map. It also has to be computed <i>per axis</i>: a first version compared
     * the two spans and derived one zoom from the larger, which ignored the fact that
     * this view is much wider than it is tall. On a route that was mostly east-west
     * that produced a zoom where the endpoints sat outside the visible width — the
     * line ran off the edge of the map with neither pin in view.
     *
     * <p><b>The dimensions must be density-independent, not raw pixels.</b> Mapbox
     * defines zoom against logical screen points, so passing physical pixels on a
     * 2.6x-density phone tells it the viewport is 2.6x wider than it is, and it
     * happily zooms in until only a third of the route fits. That failure is easy to
     * miss because the map still looks like a perfectly good map.
     *
     * @param viewWidthDp  width of the map view in dp, 0 if not yet measured
     * @param viewHeightDp height of the map view in dp, 0 if not yet measured
     */
    public static void frame(MapboxMap map,
                             double lat1, double lng1, double lat2, double lng2,
                             double viewWidthDp, double viewHeightDp) {

        double centreLat = (lat1 + lat2) / 2;
        double centreLng = (lng1 + lng2) / 2;

        // Floors stop the divisions blowing up when the two points nearly coincide.
        double latSpan = Math.max(Math.abs(lat1 - lat2), 0.0005);
        double lngSpan = Math.max(Math.abs(lng1 - lng2), 0.0005);

        // Fall back to a plausible viewport if the view has not been laid out yet.
        double w = viewWidthDp > 0 ? viewWidthDp : 360;
        double h = viewHeightDp > 0 ? viewHeightDp : 240;

        // How far each axis can be zoomed before its span stops fitting. In Mercator a
        // degree of latitude occupies more pixels than a degree of longitude by
        // 1/cos(latitude), which is why the vertical calculation carries that factor.
        double zoomForWidth = log2(360.0 * w / (TILE_SIZE_PX * lngSpan));
        double zoomForHeight = log2(360.0 * h * Math.cos(Math.toRadians(centreLat))
                / (TILE_SIZE_PX * latSpan));

        // The tighter axis wins: fitting one while overflowing the other is the bug.
        // The subtraction is breathing room so the pins are not against the edge.
        double zoom = Math.min(zoomForWidth, zoomForHeight) - 0.45;
        zoom = Math.max(3.0, Math.min(16.0, zoom));

        map.setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(centreLng, centreLat))
                .zoom(zoom)
                .build());
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }
}
