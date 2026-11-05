package com.example.joggingapp

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * A buddy's route drawn as a dotted line, shifted sideways in screen pixels so that where
 * it runs along my own route the two sit side by side instead of one hiding the other
 * (V1.71). The shift is a few pixels at every zoom, so away from my route it stays on the
 * buddy's real path to within a few metres.
 *
 * Used by the live map, the post-run summary and the history snapshots.
 */
class BuddyRouteOverlay(
    private val points: List<GeoPoint>,
    /** Stroke width of my own route on the same map, so the dots clear it. */
    myRouteStrokePx: Float = 10f
) : Overlay() {

    private val dotPx = 8f
    private val offsetPx = myRouteStrokePx / 2f + dotPx / 2f + 2f
    private val dots = DashPathEffect(floatArrayOf(0.1f, 16f), 0f)

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = dotPx + 4f; color = android.graphics.Color.WHITE; pathEffect = dots
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = dotPx; color = BUDDY_ROUTE_ARGB; pathEffect = dots
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || points.size < 2) return
        val proj = osmv.projection
        // Project, dropping points under 2px apart: they only make the side direction jitter.
        val xs = ArrayList<Float>(points.size); val ys = ArrayList<Float>(points.size)
        val tmp = android.graphics.Point()
        for (g in points) {
            proj.toPixels(g, tmp)
            val x = tmp.x.toFloat(); val y = tmp.y.toFloat()
            if (xs.isEmpty() || hypot(x - xs.last(), y - ys.last()) >= 2f) { xs.add(x); ys.add(y) }
        }
        if (xs.size < 2) return
        val path = Path()
        for (i in xs.indices) {
            // Direction through this point (neighbour to neighbour), then step to its left.
            val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(xs.lastIndex)
            val dx = xs[b] - xs[a]; val dy = ys[b] - ys[a]
            val len = hypot(dx, dy)
            val nx = if (len > 0f) -dy / len * offsetPx else 0f
            val ny = if (len > 0f) dx / len * offsetPx else 0f
            if (i == 0) path.moveTo(xs[i] + nx, ys[i] + ny) else path.lineTo(xs[i] + nx, ys[i] + ny)
        }
        c.drawPath(path, halo)
        c.drawPath(path, dot)
    }

    companion object {
        /** The buddy's colour everywhere it's drawn: the dotted route and its legend. */
        const val BUDDY_ROUTE_ARGB: Int = 0xFF2196F3.toInt()
    }
}
