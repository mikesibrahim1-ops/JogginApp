package com.example.joggingapp

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Marks osmdroid overlays that belong to the buddy layer so they can be swapped out. */
private const val BUDDY_TAG = "buddy_overlay"

@Composable
fun JogMap(
    path: List<Point>,
    routeColor: Color = Color(0xFF4CAF50),
    hasGpsSignal: Boolean = false,
    buddyView: BuddyView? = null,
    buddyTrail: List<BuddyTrailPoint> = emptyList(),
    buddyUpdatedLabel: (Long) -> String = { "" },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var followLocation by remember { mutableStateOf(true) }
    var gotFirstFix by remember { mutableStateOf(false) }

    // Build the MapView + a FrameLayout wrapper that holds the focus button
    val container = remember {
        Configuration.getInstance().apply {
            userAgentValue = MapTiles.USER_AGENT
            osmdroidBasePath = context.getExternalFilesDir(null)
            osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
        }

        // Tile provider is chosen centrally in MapTiles.kt (OSM by default, OSM.de fallback).
        val mapView = MapView(context).apply {
            setTileSource(MapTiles.tileSource())
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            isClickable = true
        }

        // Wrap map + button in a FrameLayout so the button is a real child view
        val frame = FrameLayout(context)
        frame.addView(mapView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Focus button — native ImageButton in the bottom-right corner
        val btnSize = (44 * context.resources.displayMetrics.density).toInt()
        val margin = (12 * context.resources.displayMetrics.density).toInt()
        val btn = ImageButton(context).apply {
            // Circle background
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#9E9E9E")) // grey until GPS signal arrives
            }
            background = circle
            // Use a target/crosshair drawable from Android resources
            setImageResource(android.R.drawable.ic_menu_mylocation)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(8, 8, 8, 8)
            elevation = 8f
            tag = "focusBtn"
            contentDescription = "Re-centre map on my location"
        }
        val lp = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = margin
            rightMargin = margin
        }
        frame.addView(btn, lp)

        // OSM tile usage policy REQUIRES visible "© OpenStreetMap contributors"
        // attribution on the map (bottom corner, not hidden). Bottom-left here since
        // the focus button occupies bottom-right.
        val attribution = android.widget.TextView(context).apply {
            text = "© OpenStreetMap contributors"
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setBackgroundColor(android.graphics.Color.parseColor("#B0FFFFFF")) // translucent white
            textSize = 10f
            val padH = (6 * context.resources.displayMetrics.density).toInt()
            val padV = (2 * context.resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
        }
        val attrLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START }
        frame.addView(attribution, attrLp)

        // Return both as a pair so we can reference them in effects
        Pair(frame, mapView)
    }

    val frame = container.first
    val mapView = container.second

    val locationOverlay = remember {
        // Create a custom person icon — vivid blue dot with white border
        val personSize = (36 * context.resources.displayMetrics.density).toInt()
        val personBmp = android.graphics.Bitmap.createBitmap(personSize, personSize, android.graphics.Bitmap.Config.ARGB_8888)
        val c2 = android.graphics.Canvas(personBmp)
        val outerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1565C0"); style = android.graphics.Paint.Style.FILL
        }
        val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#42A5F5"); style = android.graphics.Paint.Style.FILL
        }
        val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL
        }
        val cx = personSize / 2f; val cy = personSize / 2f
        c2.drawCircle(cx, cy, personSize / 2f - 2f, outerPaint)
        c2.drawCircle(cx, cy, personSize / 2f - 6f, innerPaint)
        c2.drawCircle(cx, cy, personSize * 0.18f, whitePaint)

        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            // Use our custom person bitmap for BOTH states (stationary + moving)
            setPersonIcon(personBmp)
            setDirectionIcon(personBmp)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionAnchor(0.5f, 0.5f)
            enableMyLocation()
            enableFollowLocation()  // auto-pan to user location when GPS locks
            // Callback when first fix arrives — ensures map jumps to real location
            runOnFirstFix {
                gotFirstFix = true
                val loc = myLocation
                if (loc != null) {
                    mapView.post {
                        mapView.controller.animateTo(loc)
                        mapView.controller.setZoom(16.0)
                    }
                }
            }
        }
    }
    // Wire up overlays once
    LaunchedEffect(Unit) {
        mapView.overlays.clear()
        mapView.overlays.add(locationOverlay)

        // Detect user panning — disable follow mode
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                followLocation = false
                updateFocusButton(frame, followLocation, path.isNotEmpty())
                return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean = false
        })

        // Wire focus button click
        val btn = frame.findViewWithTag<android.widget.ImageButton>("focusBtn")
        btn?.setOnClickListener {
            followLocation = true
            updateFocusButton(frame, true, path.isNotEmpty())
            if (path.isNotEmpty()) {
                val last = path.last()
                mapView.post { mapView.controller.animateTo(GeoPoint(last.lat, last.lon)) }
            } else {
                // No GPS path yet — try to centre on device location from overlay
                locationOverlay.myLocation?.let { loc ->
                    mapView.post { mapView.controller.animateTo(loc) }
                }
            }
        }
    }

    // Update route whenever path or colour changes — split at NaN segment breaks
    LaunchedEffect(path.size, routeColor, hasGpsSignal) {
        val argb = android.graphics.Color.argb(
            (routeColor.alpha * 255).toInt(),
            (routeColor.red * 255).toInt(),
            (routeColor.green * 255).toInt(),
            (routeColor.blue * 255).toInt()
        )

        // Remove old route overlays (keep locationOverlay AND the buddy trail)
        mapView.overlays.removeAll { it is Polyline && it.relatedObject != BUDDY_TAG }

        if (path.size >= 2) {
            // Split path into segments at NaN sentinel points
            val segments = mutableListOf<MutableList<Point>>()
            var current = mutableListOf<Point>()
            for (pt in path) {
                if (pt.lat.isNaN()) {
                    if (current.size >= 2) segments.add(current)
                    current = mutableListOf()
                } else {
                    current.add(pt)
                }
            }
            if (current.size >= 2) segments.add(current)

            // Draw each segment as its own Polyline
            segments.forEach { seg ->
                val line = Polyline(mapView).apply {
                    outlinePaint.color = argb
                    outlinePaint.strokeWidth = 10f
                    setPoints(seg.map { GeoPoint(it.lat, it.lon) })
                }
                mapView.overlays.add(0, line)
            }

            if (followLocation && path.isNotEmpty()) {
                val last = path.last { !it.lat.isNaN() }
                mapView.post { mapView.controller.animateTo(GeoPoint(last.lat, last.lon)) }
            }
        }
        // Update button colour based on GPS availability
        updateFocusButton(frame, followLocation, hasGpsSignal)
        mapView.post { mapView.invalidate() }
    }

    // ── Buddy overlay: avatar marker at the buddy's latest fix + sparse trail ─────
    // Rendered only when a buddy is live (mutual-on + fix < 15 min); otherwise the
    // overlays are removed. The trail is drawn in a distinct amber, dashed style so it
    // never looks like the user's own route (Req 5.2, 5b).
    val buddyLat = buddyView?.location?.lat
    val buddyLon = buddyView?.location?.lon
    LaunchedEffect(buddyLat, buddyLon, buddyTrail.size, buddyView?.user?.appId) {
        // Clear any previous buddy overlays (tagged via relatedObject).
        mapView.overlays.removeAll { it is Marker && it.relatedObject == BUDDY_TAG }
        mapView.overlays.removeAll { it is Polyline && it.relatedObject == BUDDY_TAG }

        val live = buddyView?.isLiveNow() == true
        val loc = buddyView?.location
        if (live && loc != null) {
            // Trail first (drawn under the marker)
            if (buddyTrail.size >= 2) {
                val trail = Polyline(mapView).apply {
                    relatedObject = BUDDY_TAG
                    outlinePaint.color = android.graphics.Color.parseColor("#FB8C00")
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    setPoints(buddyTrail.sortedBy { it.at }.map { GeoPoint(it.lat, it.lon) })
                }
                mapView.overlays.add(0, trail)
            }

            // Buddy avatar marker
            val photo = buddyView.user.photoRef?.let { p ->
                try { android.graphics.BitmapFactory.decodeFile(p) } catch (_: Exception) { null }
            }
            val marker = Marker(mapView).apply {
                relatedObject = BUDDY_TAG
                position = GeoPoint(loc.lat, loc.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = makeBuddyAvatarDrawable(context, buddyView.user.displayName, photo)
                title = buddyView.user.displayName.ifBlank { "Buddy" }
                snippet = buddyUpdatedLabel(loc.at)
            }
            mapView.overlays.add(marker)
        }
        mapView.post { mapView.invalidate() }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            locationOverlay.disableMyLocation()
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { frame },
        update = { _ ->
            // gotFirstFix triggers recomposition when osmdroid gets its first GPS lock
            val gpsAvailable = gotFirstFix || hasGpsSignal
            updateFocusButton(frame, followLocation, gpsAvailable)
        },
        modifier = modifier
    )
}

/** Updates the focus button colour to reflect current follow state */
private fun updateFocusButton(frame: FrameLayout, following: Boolean, hasGps: Boolean = true) {
    val btn = frame.findViewWithTag<android.widget.ImageButton>("focusBtn") ?: return
    val color = when {
        !hasGps -> android.graphics.Color.parseColor("#9E9E9E")   // grey — no GPS signal
        following -> android.graphics.Color.parseColor("#43A047") // green — following, GPS active
        else -> android.graphics.Color.parseColor("#FFFFFF")       // white — GPS active but not following
    }
    val circle = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
    btn.post { btn.background = circle }
}
