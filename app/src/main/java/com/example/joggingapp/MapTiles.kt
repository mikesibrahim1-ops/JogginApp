package com.example.joggingapp

/**
 * Central map-tile configuration.
 *
 * To switch providers (or revert), change ONLY the `provider` value below.
 * Everything else in the app reads from this object, so no other file needs editing.
 *
 *  - OSM_HOT: OpenStreetMap "Humanitarian" style, hosted by OSM-France (default).
 *             Key-free, lighter/cleaner than the dense Standard render. OSM-France's
 *             policy permits free, non-profit, moderate-traffic mobile apps that send
 *             an identifying User-Agent (done below) and show attribution (done on map).
 *  - OSM:     Standard OpenStreetMap public servers (denser labels/POIs). Key-free.
 *             Compliance requires THREE things (all done): (1) the canonical host
 *             https://tile.openstreetmap.org/{z}/{x}/{y}.png with NO a./b./c. subdomains,
 *             (2) a distinct product-name User-Agent (NOT a com.example.* placeholder),
 *             (3) visible "© OpenStreetMap contributors" attribution on the map.
 *             A small personal, interactive fitness app is well within policy.
 *  - OSM_DE:  OpenStreetMap Germany tile servers. Key-free, same OSM look/data,
 *             generally lenient for app use. First fallback if `tile.openstreetmap.org`
 *             ever blocks us again.
 *  - CARTO:   Carto Voyager. NOTE: Carto's public CDN now returns "API key required"
 *             for app/distributed use — kept only for reference, not usable key-free.
 */
object MapTiles {

    enum class Provider { OSM_HOT, OSM, OSM_DE, CARTO }

    // ── Switch here to revert / change provider ───────────────────────────────
    // OSM_HOT = lighter, less-cluttered "Humanitarian" style (hosted by OSM-France).
    //           Cleaner than the busy Standard OSM render. Default.
    // OSM     = standard OpenStreetMap (denser labels/POIs). Fallback.
    val provider: Provider = Provider.OSM
    // ──────────────────────────────────────────────────────────────────────────

    // Identifying User-Agent. OSM's policy (operations.osmfoundation.org/policies/tiles)
    // REQUIRES a clear, unique string that LEADS WITH A DISTINCT PRODUCT NAME + version,
    // e.g. "MyTownMaps/1.4 (+https://example.org)". It must NOT look like a library
    // default, and must NOT lead with a "com.example.*" placeholder-style id (the word
    // "example" reads as the reserved placeholder OSM rejects with a 403).
    // This is the actual fix for the "Access blocked / 403 / osm.wiki/Blocked" tiles.
    const val USER_AGENT = "JogginApp/1.0 (Android jogging tracker; +https://github.com/joggingApp/joggingapp.github.io)"

    /** osmdroid tile-source name (used as the cache namespace). */
    val sourceName: String get() = when (provider) {
        Provider.OSM_HOT -> "OpenStreetMapHOT"
        Provider.OSM     -> "OpenStreetMap"
        Provider.OSM_DE  -> "OpenStreetMapDE"
        Provider.CARTO   -> "CartoVoyager"
    }

    /** Base URLs (with trailing slash) — round-robined subdomains where available. */
    val baseUrls: Array<String> get() = when (provider) {
        // HOT "Humanitarian" style, hosted by OSM-France. Lighter/cleaner than Standard.
        Provider.OSM_HOT -> arrayOf(
            "https://a.tile.openstreetmap.fr/hot/",
            "https://b.tile.openstreetmap.fr/hot/",
            "https://c.tile.openstreetmap.fr/hot/"
        )
        // Policy requires EXACTLY this host (no a./b./c. subdomains — those are being
        // withdrawn and now return 403). Single canonical host only.
        Provider.OSM -> arrayOf(
            "https://tile.openstreetmap.org/"
        )
        Provider.OSM_DE -> arrayOf(
            "https://a.tile.openstreetmap.de/",
            "https://b.tile.openstreetmap.de/",
            "https://c.tile.openstreetmap.de/"
        )
        Provider.CARTO -> arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    }

    /** File extension the provider serves tiles as. */
    val imageExt: String get() = when (provider) {
        Provider.OSM_HOT -> ".png"
        Provider.OSM     -> ".png"
        Provider.OSM_DE  -> ".png"
        Provider.CARTO   -> ".png"
    }

    /** Subdomain letters for the raw-download (share bitmap) path. */
    val subdomains: CharArray get() = when (provider) {
        Provider.OSM_HOT -> charArrayOf('a', 'b', 'c')
        Provider.OSM     -> charArrayOf()          // canonical host has no subdomains
        Provider.OSM_DE  -> charArrayOf('a', 'b', 'c')
        Provider.CARTO   -> charArrayOf('a', 'b', 'c', 'd')
    }

    /**
     * Build a raw tile URL (used by the share-image bitmap stitcher, which downloads
     * tiles directly rather than through osmdroid).
     */
    fun rawTileUrl(zoom: Int, tileX: Int, tileY: Int): String {
        // OSM uses the canonical host with no subdomain; others round-robin subdomains.
        val sub = if (subdomains.isEmpty()) "" else subdomains[(tileX + tileY) % subdomains.size]
        return when (provider) {
            Provider.OSM_HOT -> "https://$sub.tile.openstreetmap.fr/hot/$zoom/$tileX/$tileY.png"
            Provider.OSM     -> "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
            Provider.OSM_DE  -> "https://$sub.tile.openstreetmap.de/$zoom/$tileX/$tileY.png"
            Provider.CARTO   -> "https://$sub.basemaps.cartocdn.com/rastertiles/voyager/$zoom/$tileX/$tileY.png"
        }
    }

    /** Shared osmdroid tile source used by every map in the app. */
    fun tileSource() = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
        sourceName, 0, 20, 256, imageExt, baseUrls
    ) {
        override fun getTileURLString(t: Long) = baseUrl +
            org.osmdroid.util.MapTileIndex.getZoom(t) + "/" +
            org.osmdroid.util.MapTileIndex.getX(t) + "/" +
            org.osmdroid.util.MapTileIndex.getY(t) + mImageFilenameEnding
    }
}
