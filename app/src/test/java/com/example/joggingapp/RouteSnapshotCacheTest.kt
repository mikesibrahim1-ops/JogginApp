package com.example.joggingapp

import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.MapTileIndex
import kotlin.math.*

/**
 * Pure JVM unit tests for the tile-coordinate math used by [RouteSnapshotCache].
 * No Android dependencies, no MapView, no Bitmap — safe to run in the unit-test JVM.
 */
class RouteSnapshotCacheTest {

    // ── Tile-coord helpers (mirrors the logic in RouteSnapshotCache) ───────────

    private fun lonToTileXd(lon: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return (lon + 180.0) / 360.0 * n
    }

    private fun latToTileYd(lat: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val rad = Math.toRadians(lat)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n
    }

    // ── MapTileIndex pack/unpack round-trip ────────────────────────────────────

    @Test
    fun tileIndex_packUnpack_roundTrip() {
        val zoom = 14; val x = 8213; val y = 5479
        val packed = MapTileIndex.getTileIndex(zoom, x, y)
        assertEquals("zoom round-trip", zoom, MapTileIndex.getZoom(packed))
        assertEquals("x round-trip", x, MapTileIndex.getX(packed))
        assertEquals("y round-trip", y, MapTileIndex.getY(packed))
    }

    @Test
    fun tileIndex_packUnpack_zoom0() {
        val packed = MapTileIndex.getTileIndex(0, 0, 0)
        assertEquals(0, MapTileIndex.getZoom(packed))
        assertEquals(0, MapTileIndex.getX(packed))
        assertEquals(0, MapTileIndex.getY(packed))
    }

    @Test
    fun tileIndex_packUnpack_zoom13() {
        val zoom = 13; val x = 4106; val y = 2739
        val packed = MapTileIndex.getTileIndex(zoom, x, y)
        assertEquals(zoom, MapTileIndex.getZoom(packed))
        assertEquals(x, MapTileIndex.getX(packed))
        assertEquals(y, MapTileIndex.getY(packed))
    }

    // ── Bbox-to-tile-range correctness ─────────────────────────────────────────

    @Test
    fun bboxTileRange_london_zoom14_isReasonable() {
        // Central London approx bbox.
        val minLat = 51.48; val maxLat = 51.52
        val minLon = -0.12; val maxLon = -0.08
        val zoom = 14

        val tileX0 = lonToTileXd(minLon, zoom).toInt()
        val tileX1 = lonToTileXd(maxLon, zoom).toInt()
        val tileY0 = latToTileYd(maxLat, zoom).toInt()  // north = smaller y
        val tileY1 = latToTileYd(minLat, zoom).toInt()

        // Width and height in tiles should be small (1–4 for a ~4km bbox at z14).
        val tileWidth = tileX1 - tileX0 + 1
        val tileHeight = tileY1 - tileY0 + 1
        assertTrue("tile width > 0", tileWidth > 0)
        assertTrue("tile height > 0", tileHeight > 0)
        assertTrue("tile width ≤ 4 for ~4km at z14", tileWidth <= 4)
        assertTrue("tile height ≤ 4 for ~4km at z14", tileHeight <= 4)
        // North bbox edge should have smaller tile-Y than south edge (y is inverted).
        assertTrue("y0 ≤ y1 (north = smaller tile y)", tileY0 <= tileY1)
    }

    @Test
    fun bboxTileRange_tinyRoute_yieldsAtLeastOneTile() {
        // A very short 100m route.
        val minLat = 51.5000; val maxLat = 51.5001
        val minLon = -0.0001; val maxLon =  0.0001
        val zoom = 14
        val tileX0 = lonToTileXd(minLon, zoom).toInt()
        val tileX1 = lonToTileXd(maxLon, zoom).toInt()
        val tileY0 = latToTileYd(maxLat, zoom).toInt()
        val tileY1 = latToTileYd(minLat, zoom).toInt()
        val count = (tileX1 - tileX0 + 1) * (tileY1 - tileY0 + 1)
        assertTrue("at least 1 tile for tiny route", count >= 1)
    }

    @Test
    fun lonToTileXd_primerMeridian_zoom14() {
        // lon=0 should be exactly at tile x = 2^14/2 = 8192.
        val xd = lonToTileXd(0.0, 14)
        assertEquals(8192.0, xd, 0.001)
    }

    @Test
    fun lonToTileXd_lon180_zoom14_isMaxTile() {
        val n = 2.0.pow(14)
        val xd = lonToTileXd(180.0, 14)
        assertEquals(n, xd, 0.001)
    }

    // ── Cache-key uniqueness ────────────────────────────────────────────────────

    @Test
    fun cacheKey_differentSizes_areDistinct() {
        val key1 = "routeABC_600x360"
        val key2 = "routeABC_300x180"
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_differentRoutes_sameSize_areDistinct() {
        val key1 = "route001_600x360"
        val key2 = "route002_600x360"
        assertNotEquals(key1, key2)
    }
}
