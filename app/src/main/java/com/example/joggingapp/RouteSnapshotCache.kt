package com.example.joggingapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.MapTileIndex
import java.io.File

/**
 * Off-thread tile-based map snapshot cache for Activity History cards.
 *
 * All I/O runs on Dispatchers.IO. Never creates a MapView or posts to the main thread.
 * Reads tiles exclusively from osmdroid's on-disk SQLite cache — no network calls.
 * Returns null if tiles are absent (RouteThumbnail line-only fallback kicks in).
 *
 * Memory cache: LruCache keyed by "${routeId}_${w}x${h}" (8 entries ≈ 5 MB max).
 * Disk cache:  context.cacheDir/route_snapshots/${routeId}_${w}x${h}.jpg (quality 80).
 */
object RouteSnapshotCache {

    private const val LRU_MAX_ENTRIES = 8
    private const val DISK_CACHE_DIR = "route_snapshots"
    private const val JPEG_QUALITY = 80
    private const val DEFAULT_ZOOM = 14
    private const val FALLBACK_ZOOM = 13
    private const val TILE_SIZE_PX = 256

    // Background fill for the stitched tile canvas (light grey, visually distinct
    // from surfaceVariant so it signals "real map" even before tiles appear).
    private const val BG_COLOR = 0xFFF5F5F5.toInt()

    // Route polyline paint — matches RouteThumbnail stroke colour.
    private const val ROUTE_GREEN = 0xFF66BB6A.toInt()
    private const val ROUTE_STROKE = 8f
    private const val START_DOT_RADIUS = 10f

    // In-memory LRU — access synchronised internally by LruCache.
    private val memCache = LruCache<String, Bitmap>(LRU_MAX_ENTRIES)

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns a [Bitmap] snapshot for [route] at [widthPx] × [heightPx], or null
     * if tiles are not cached (caller should show RouteThumbnail as fallback).
     *
     * Call from a coroutine scope — always runs on [Dispatchers.IO].
     * NEVER call from the main thread.
     */
    suspend fun getOrGenerate(
        context: Context,
        route: SavedRoute,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${route.id}_${widthPx}x${heightPx}"

        // 1. Memory cache hit
        memCache[cacheKey]?.let { return@withContext it }

        // 2. Disk cache hit
        readFromDiskCache(context, cacheKey)?.also {
            memCache.put(cacheKey, it)
            return@withContext it
        }

        // 3. Generate from SQLite tile cache
        val bitmap = generateSnapshot(context, route, widthPx, heightPx)
            ?: return@withContext null

        writeToDiskCache(context, cacheKey, bitmap)
        memCache.put(cacheKey, bitmap)
        bitmap
    }

    // ── Disk cache ─────────────────────────────────────────────────────────────

    private fun diskCacheFile(context: Context, key: String): File =
        File(File(context.cacheDir, DISK_CACHE_DIR).also { it.mkdirs() }, "$key.jpg")

    private fun readFromDiskCache(context: Context, key: String): Bitmap? {
        val file = diskCacheFile(context, key)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDiskCache(context: Context, key: String, bitmap: Bitmap) {
        try {
            diskCacheFile(context, key).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (_: Exception) {
            // Non-fatal — in-memory cache still works.
        }
    }

    // ── Snapshot generation ────────────────────────────────────────────────────

    private fun generateSnapshot(
        context: Context,
        route: SavedRoute,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        // Filter NaN pause sentinels.
        val real = route.points.filter { !it.lat.isNaN() && !it.lon.isNaN() }
        if (real.size < 2) return null

        val minLat = real.minOf { it.lat }
        val maxLat = real.maxOf { it.lat }
        val minLon = real.minOf { it.lon }
        val maxLon = real.maxOf { it.lon }

        // Try zoom 14, fall back to 13 if no tiles present in the SQLite cache.
        val dbFile = File(context.cacheDir, "osmdroid/${MapTiles.sourceName}.sqlite")
        if (!dbFile.exists()) return null

        for (zoom in listOf(DEFAULT_ZOOM, FALLBACK_ZOOM)) {
            val result = tryGenerateAtZoom(
                dbFile, real, minLat, maxLat, minLon, maxLon,
                zoom, widthPx, heightPx
            )
            if (result != null) return result
        }
        return null
    }

    /**
     * Attempts to stitch tiles at the given zoom. Returns null if no tiles were found.
     */
    private fun tryGenerateAtZoom(
        dbFile: File,
        real: List<Point>,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double,
        zoom: Int,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        val n = Math.pow(2.0, zoom.toDouble())

        // Convert bbox corners to tile floating-point coords.
        fun lonToTileXd(lon: Double) = (lon + 180.0) / 360.0 * n
        fun latToTileYd(lat: Double): Double {
            val rad = Math.toRadians(lat)
            return (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * n
        }

        // Centre of bbox in tile coords.
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0
        val centerTileXd = lonToTileXd(centerLon)
        val centerTileYd = latToTileYd(centerLat)

        // Tile range covering the bbox with 1-tile margin.
        val bboxTileX0 = lonToTileXd(minLon).toInt() - 1
        val bboxTileX1 = lonToTileXd(maxLon).toInt() + 1
        val bboxTileY0 = latToTileYd(maxLat).toInt() - 1  // y is inverted (north = smaller y)
        val bboxTileY1 = latToTileYd(minLat).toInt() + 1

        val maxTile = n.toInt() - 1
        val tileX0 = bboxTileX0.coerceAtLeast(0)
        val tileX1 = bboxTileX1.coerceAtMost(maxTile)
        val tileY0 = bboxTileY0.coerceAtLeast(0)
        val tileY1 = bboxTileY1.coerceAtMost(maxTile)

        // Load tiles from the SQLite DB.
        var tilesFound = 0
        val tileData = mutableMapOf<Pair<Int, Int>, Bitmap>()
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                for (ty in tileY0..tileY1) {
                    for (tx in tileX0..tileX1) {
                        val tile = loadTileFromDb(db, MapTiles.sourceName, zoom, tx, ty)
                        if (tile != null) {
                            tileData[Pair(tx, ty)] = tile
                            tilesFound++
                        }
                    }
                }
            }
        } catch (_: Exception) {
            tileData.values.forEach { it.recycle() }
            return null
        }

        if (tilesFound == 0) {
            tileData.values.forEach { it.recycle() }
            return null
        }

        // Build output bitmap.
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(BG_COLOR)

        // Composite tiles.
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for ((pos, tile) in tileData) {
            val (tx, ty) = pos
            val pixX = ((tx - centerTileXd) * TILE_SIZE_PX + widthPx / 2.0).toFloat()
            val pixY = ((ty - centerTileYd) * TILE_SIZE_PX + heightPx / 2.0).toFloat()
            canvas.drawBitmap(tile, pixX, pixY, tilePaint)
            tile.recycle()
        }

        // Helper: map lat/lon → canvas pixel using same tile-coord transform.
        fun mapX(lon: Double) =
            ((lonToTileXd(lon) - centerTileXd) * TILE_SIZE_PX + widthPx / 2.0).toFloat()
        fun mapY(lat: Double) =
            ((latToTileYd(lat) - centerTileYd) * TILE_SIZE_PX + heightPx / 2.0).toFloat()

        // Draw route polyline.
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ROUTE_GREEN
            strokeWidth = ROUTE_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(mapX(real.first().lon), mapY(real.first().lat))
            for (i in 1 until real.size) {
                lineTo(mapX(real[i].lon), mapY(real[i].lat))
            }
        }
        canvas.drawPath(path, routePaint)

        // Start dot (green) and end dot (primary blue).
        val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ROUTE_GREEN
        }
        val endDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF1565C0.toInt()  // primary blue
        }
        canvas.drawCircle(mapX(real.first().lon), mapY(real.first().lat), START_DOT_RADIUS, startDotPaint)
        canvas.drawCircle(mapX(real.last().lon), mapY(real.last().lat), START_DOT_RADIUS, endDotPaint)

        return output
    }

    // ── SQLite tile reader ─────────────────────────────────────────────────────

    /**
     * Reads a single tile BLOB from osmdroid's SQLite cache.
     * Schema: table `tiles`, columns `provider TEXT`, `key INTEGER`, `tile BLOB`.
     * The packed key is [MapTileIndex.getTileIndex].
     */
    private fun loadTileFromDb(
        db: SQLiteDatabase,
        provider: String,
        zoom: Int,
        tileX: Int,
        tileY: Int
    ): Bitmap? {
        val key = MapTileIndex.getTileIndex(zoom, tileX, tileY)
        return try {
            db.rawQuery(
                "SELECT tile FROM tiles WHERE provider=? AND key=?",
                arrayOf(provider, key.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                val blob = cursor.getBlob(0) ?: return null
                BitmapFactory.decodeByteArray(blob, 0, blob.size)
            }
        } catch (_: Exception) {
            null
        }
    }
}
