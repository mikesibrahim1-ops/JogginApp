package com.example.joggingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * One-time renderer that produces a STATIC map bitmap for a route using a REAL
 * off-screen osmdroid [MapView] — the same render path as the detail-view RouteMap
 * (tile source, polyline, start/finish markers, zoomToBoundingBox). This is what makes
 * real tiles appear (manual SQLite tile stitching — the reverted attempt — came up blank).
 *
 * Correctness/perf contract:
 *  - MapView is created and driven on the MAIN thread (osmdroid requirement).
 *  - Exactly ONE off-screen MapView exists at any moment: a [Mutex] serializes generation,
 *    and the MapView is detached (`onDetach`) as soon as capture finishes.
 *  - Tile loading is async; we poll [org.osmdroid.views.overlay.TilesOverlay] tile-state
 *    counters until the visible tiles are drawn (or a timeout), then capture via
 *    `view.draw(Canvas(bitmap))`. Network fetch is allowed here (off the scroll path).
 *  - Returns null when there are <2 real points, or when NO tile ever loaded (caller then
 *    falls back to the Canvas RouteThumbnail line so the card still shows something).
 *
 * This object owns NO Compose code and holds no long-lived MapView.
 */
object RouteMapSnapshot {

    // Only one generation at a time -> only one off-screen MapView ever exists.
    private val genMutex = Mutex()

    private const val TILE_LOAD_TIMEOUT_MS = 6_000L
    private const val POLL_INTERVAL_MS = 100L
    private const val ROUTE_GREEN = "#66BB6A"
    private const val ROUTE_STROKE = 10f

    /**
     * Render [route] to a [widthPx] x [heightPx] bitmap. Suspends; hops to the main
     * thread internally. Returns null if generation can't produce a real map image.
     */
    suspend fun generate(
        context: Context,
        route: SavedRoute,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val points = route.points.filter { !it.lat.isNaN() && !it.lon.isNaN() }
        if (points.size < 2) return null

        return genMutex.withLock {
            withContext(Dispatchers.Main) {
                // One-time osmdroid config (mirrors MainActivity.configureOsmdroid).
                org.osmdroid.config.Configuration.getInstance().apply {
                    userAgentValue = MapTiles.USER_AGENT
                    osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
                }

                val mapView = MapView(context).apply {
                    setTileSource(MapTiles.tileSource())
                    setMultiTouchControls(false)
                    isClickable = false
                    isFocusable = false
                    // Keep built-in zoom buttons off the captured image.
                    @Suppress("DEPRECATION")
                    setBuiltInZoomControls(false)
                }

                try {
                    val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
                    val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }

                    val line = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.parseColor(ROUTE_GREEN)
                        outlinePaint.strokeWidth = ROUTE_STROKE
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        setPoints(points.map { GeoPoint(it.lat, it.lon) })
                    }
                    val bearing = bearingDeg(points[0], points[1])
                    val startM = Marker(mapView).apply {
                        position = GeoPoint(points.first().lat, points.first().lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = makeStartArrowDrawable(context, bearing); title = "Start"
                    }
                    val endM = Marker(mapView).apply {
                        position = GeoPoint(points.last().lat, points.last().lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = makeFinishFlagDrawable(context); title = "Finish"
                    }
                    mapView.overlays.addAll(listOf(line, startM, endM))

                    // Measure + layout the off-screen view to the exact target size so
                    // tile loading and drawing use the real pixel dimensions.
                    mapView.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                    )
                    mapView.layout(0, 0, widthPx, heightPx)

                    // Frame the whole route (same padding/bbox as the detail view).
                    mapView.zoomToBoundingBox(
                        BoundingBox(maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001),
                        false, 48
                    )
                    mapView.onResume()

                    // The capture bitmap is also the DRAW TARGET during the wait. osmdroid
                    // requests tiles and advances its TileStates counters only inside
                    // MapView.draw (TilesOverlay.drawTiles). An off-screen, window-less
                    // MapView gets no system draw pass, and invalidate() is a no-op with no
                    // ViewRootImpl — so we must drive the draw ourselves every poll pass.
                    // Each draw requests the visible tiles and lets the async downloads land
                    // into subsequent passes; the final draw is the captured image.
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    val tilesLoaded = awaitTiles(mapView, canvas)

                    // Even on timeout, keep the last draw if at least one tile is present;
                    // otherwise return null so the caller keeps the line fallback.
                    if (!tilesLoaded && !anyTilePresent(mapView)) {
                        return@withContext null
                    }
                    bitmap
                } finally {
                    // Tear the MapView down so NO MapView is retained between generations.
                    mapView.overlays.clear()
                    mapView.onPause()
                    mapView.onDetach()
                }
            }
        }
    }

    /**
     * Drive tile loading until all visible tiles are drawn (upToDate + scaled >= total,
     * with total > 0) or until the timeout elapses. CRITICAL: each poll DRAWS the MapView
     * into [canvas] — this is the only thing that makes osmdroid request tiles and advance
     * its TileStates counters (TilesOverlay.drawTiles runs only from MapView.draw). On a
     * detached off-screen view invalidate() schedules nothing, so drawing manually each
     * pass is what lets the async tile downloads land. Returns true if fully loaded; the
     * last draw left in [canvas]'s bitmap is the capture. This is what osmdroid's own
     * MapSnapshot does.
     */
    private suspend fun awaitTiles(mapView: MapView, canvas: Canvas): Boolean {
        val deadline = System.currentTimeMillis() + TILE_LOAD_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            mapView.draw(canvas)
            val states = mapView.overlayManager.tilesOverlay.tileStates
            val total = states.total
            if (total > 0 && (states.upToDate + states.scaled) >= total) {
                // One more draw so the captured bitmap reflects the final tile set.
                mapView.draw(canvas)
                return true
            }
            delay(POLL_INTERVAL_MS)
        }
        // Final draw so a timeout-with-tiles capture reflects the latest tiles.
        mapView.draw(canvas)
        return false
    }

    /** True if at least one tile has been drawn (up-to-date or scaled). */
    private fun anyTilePresent(mapView: MapView): Boolean {
        val states = mapView.overlayManager.tilesOverlay.tileStates
        return (states.upToDate + states.scaled) > 0
    }
}
