package com.example.joggingapp

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit coverage for the cache key + disk round-trip. These stay on the IO path only —
 * they never create a real MapView or touch the network (that is device-verified).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RouteSnapshotCacheTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    private fun route(id: String, pts: List<Point>) =
        SavedRoute(id, pts, 1.0f, 0.5f, 2.0f, 10f, 1000L)

    @Test
    fun keyFor_isStableForRouteIdAndSize() {
        val a = RouteSnapshotCache.keyFor("route-1", 300, 180)
        val b = RouteSnapshotCache.keyFor("route-1", 300, 180)
        assertEquals(a, b)
        assertTrue(a.startsWith("route-1_"))
        assertTrue(a.contains("300x180"))
        // Different size -> different key.
        assertNotEquals(a, RouteSnapshotCache.keyFor("route-1", 301, 180))
        // Different route -> different key.
        assertNotEquals(a, RouteSnapshotCache.keyFor("route-2", 300, 180))
    }

    @Test
    fun getOrGenerate_returnsDiskCachedBitmap_withoutRegenerating() = runBlocking {
        val w = 120; val h = 80
        val r = route("disk-hit", listOf(Point(1.0, 2.0, 0L, 1f), Point(1.1, 2.1, 1000L, 1f)))
        val key = RouteSnapshotCache.keyFor(r.id, w, h)

        // Seed the disk cache with a known bitmap so no MapView work is needed.
        val dir = File(ctx.cacheDir, "route_snapshots").apply { mkdirs() }
        val file = File(dir, "$key.jpg")
        val seed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        seed.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { seed.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        assertTrue(file.exists())

        val result = RouteSnapshotCache.getOrGenerate(ctx, r, w, h)
        assertNotNull(result)
        assertEquals(w, result!!.width)
        assertEquals(h, result.height)
    }

    @Test
    fun getOrGenerate_returnsNull_forRouteWithFewerThanTwoPoints() = runBlocking {
        val r = route("too-few", listOf(Point(1.0, 2.0, 0L, 1f)))
        val result = RouteSnapshotCache.getOrGenerate(ctx, r, 120, 80)
        assertNull(result)
    }

    @Test
    fun getOrGenerate_returnsNull_forNonPositiveSize() = runBlocking {
        val r = route("zero-size", listOf(Point(1.0, 2.0, 0L, 1f), Point(1.1, 2.1, 1000L, 1f)))
        assertNull(RouteSnapshotCache.getOrGenerate(ctx, r, 0, 80))
        assertNull(RouteSnapshotCache.getOrGenerate(ctx, r, 120, 0))
    }
}
