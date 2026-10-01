package com.example.joggingapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

/**
 * Creates a small green directional arrow bitmap for the run start marker.
 * The arrow points in the direction of initial travel (bearing from first to second point).
 */
fun makeStartArrowDrawable(context: Context, bearingDeg: Float): BitmapDrawable {
    val size = dpToPx(context, 28)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2

    // Outer circle
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, circlePaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(cx, cy, r, borderPaint)

    // Arrow triangle pointing up, then rotated to bearing
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        val aw = r * 0.45f
        val ah = r * 0.65f
        moveTo(cx, cy - ah)          // tip
        lineTo(cx - aw, cy + ah * 0.3f)
        lineTo(cx + aw, cy + ah * 0.3f)
        close()
    }
    canvas.save()
    canvas.rotate(bearingDeg, cx, cy)
    canvas.drawPath(path, arrowPaint)
    canvas.restore()

    return BitmapDrawable(context.resources, bmp)
}

/**
 * Creates a checkered flag bitmap for the run end marker.
 */
fun makeFinishFlagDrawable(context: Context): BitmapDrawable {
    val size = dpToPx(context, 28)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2

    // Red outer circle
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, circlePaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(cx, cy, r, borderPaint)

    // Checkered pattern inside (2x2 grid of squares)
    val squareSize = r * 0.38f
    val startX = cx - squareSize
    val startY = cy - squareSize
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121"); style = Paint.Style.FILL }

    // Clip to circle
    val clipPath = Path().apply { addCircle(cx, cy, r - 3f, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(clipPath)
    for (row in 0..3) {
        for (col in 0..3) {
            val x = startX - squareSize + col * squareSize
            val y = startY - squareSize + row * squareSize
            val paint = if ((row + col) % 2 == 0) whitePaint else blackPaint
            canvas.drawRect(x, y, x + squareSize, y + squareSize, paint)
        }
    }
    canvas.restore()

    return BitmapDrawable(context.resources, bmp)
}

/**
 * Creates a buddy avatar marker — an amber/orange circle with a white border and the
 * buddy's initials (or a person glyph if no name). Deliberately a different colour and
 * shape treatment from the user's blue location dot so the two are never confused
 * (Req 5.2). Optionally overlays a supplied profile [photo] cropped to the circle.
 */
fun makeBuddyAvatarDrawable(context: Context, displayName: String, photo: Bitmap? = null): BitmapDrawable {
    val size = dpToPx(context, 40)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2

    // Amber fill (distinct from the user's blue dot)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FB8C00")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, fillPaint)

    if (photo != null) {
        // Crop the photo into the circle (leave a ring of the amber fill as a border)
        val clip = Path().apply { addCircle(cx, cy, r - 3f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val src = Rect(0, 0, photo.width, photo.height)
        val dst = RectF(cx - (r - 3f), cy - (r - 3f), cx + (r - 3f), cy + (r - 3f))
        canvas.drawBitmap(photo, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        // Initials (up to 2 letters) in white
        val initials = displayName.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .take(2).joinToString("")
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.38f
            isFakeBoldText = true
        }
        if (initials.isNotEmpty()) {
            val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(initials, cx, cy - yOffset, textPaint)
        } else {
            // Fallback: simple person glyph (head + shoulders)
            val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawCircle(cx, cy - r * 0.22f, r * 0.28f, glyph)
            val body = RectF(cx - r * 0.42f, cy + r * 0.05f, cx + r * 0.42f, cy + r * 0.7f)
            canvas.drawRoundRect(body, r * 0.42f, r * 0.42f, glyph)
        }
    }

    // White border ring on top
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(cx, cy, r, borderPaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun dpToPx(context: Context, dp: Int): Int =
    (dp * context.resources.displayMetrics.density + 0.5f).toInt()

/**
 * Calculates bearing in degrees from point a to point b.
 */
fun bearingDeg(a: Point, b: Point): Float {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
}
