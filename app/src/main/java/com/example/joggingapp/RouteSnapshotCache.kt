package com.example.joggingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache for the static route-map snapshots shown on Activity History cards.
 *
 * Two tiers, sized for the WHOLE list (the reverted 8-entry LRU thrashed on 40+ routes
 * and caused the scroll jank):
 *  - In-memory [LruCache] of 64 entries — big enough to hold every card's bitmap so a
 *    scrolled-back card hits memory with ZERO main-thread decode or generation.
 *  - Disk JPEGs under cacheDir/route_snapshots, keyed by route.id + size + version, so
 *    snapshots survive restarts and are generated exactly once, ever.
 *
 * [getOrGenerate] runs on [Dispatchers.IO]; disk decode happens off the main thread and
 * the decoded bitmap is promoted to memory. [peek] is a synchronous memory-only hit the
 * Composable uses to show an already-decoded bitmap instantly. The Composable NEVER
 * decodes or generates on the main thread.
 */
object RouteSnapshotCache {

    private const val LRU_MAX_ENTRIES = 64
    private const val DISK_CACHE_DIR = "route_snapshots"
    private const val JPEG_QUALITY = 85
    private const val VERSION = "v1"

    private val memCache = LruCache<String, Bitmap>(LRU_MAX_ENTRIES)

    /** Stable cache key: route id + target pixel size + schema version. */
    fun keyFor(routeId: String, widthPx: Int, heightPx: Int): String =
        "${routeId}_${widthPx}x${heightPx}_$VERSION"

    /** Synchronous memory-only lookup. Safe on the main thread (no I/O, no decode). */
    fun peek(key: String): Bitmap? = memCache.get(key)

    /**
     * Return a snapshot bitmap for [route] at [widthPx] x [heightPx], generating and
     * caching it (memory + disk) on first use. Runs on [Dispatchers.IO]; the one-time
     * MapView render inside [RouteMapSnapshot.generate] hops to the main thread itself.
     *
     * Returns null when a real map image can't be produced (<2 points, or offline +
     * uncached) so the caller keeps the RouteThumbnail line fallback.
     */
    suspend fun getOrGenerate(
        context: Context,
        route: SavedRoute,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val key = keyFor(route.id, widthPx, heightPx)

        // (a) memory
        memCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            // Re-check memory after the dispatcher hop (another caller may have filled it).
            memCache.get(key)?.let { return@withContext it }

            // (b) disk
            val file = diskFile(context, key)
            if (file.exists()) {
                val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (decoded != null) {
                    memCache.put(key, decoded)
                    return@withContext decoded
                }
                // Corrupt/partial file — drop it and regenerate.
                file.delete()
            }

            // (c) generate (allowed to fetch tiles from the network, one time)
            val generated = RouteMapSnapshot.generate(context, route, widthPx, heightPx)
                ?: return@withContext null

            writeDisk(file, generated)
            memCache.put(key, generated)
            generated
        }
    }

    private fun diskFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.jpg")
    }

    private fun writeDisk(file: File, bitmap: Bitmap) {
        runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }
    }
}
