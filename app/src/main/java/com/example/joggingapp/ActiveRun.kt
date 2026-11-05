package com.example.joggingapp

/**
 * Lets a reopened screen reconnect to a run the tracking service is still recording. The
 * screen's own state dies with the activity while the service keeps going, so what only the
 * screen knows (activity type, steps) is kept here, for the life of the process.
 */
object ActiveRun {

    /** What the tracking service has recorded so far. [path] is in the screen's format. */
    data class Snapshot(val path: List<Point>, val elapsedSeconds: Int, val paused: Boolean)

    @Volatile var activityMode = 0
    @Volatile var usedModes: List<Int> = emptyList()
    @Volatile var stepBaseline = -1f
    @Volatile var stepCount = 0

    fun resetSteps() {
        stepBaseline = -1f
        stepCount = 0
    }

    /**
     * Rebuilds the screen's path from the service's points: the screen marks each pause with a
     * NaN point so no line or distance joins the two sides. [breaks] holds the number of
     * points recorded when each pause began.
     */
    fun pathWithBreaks(points: List<Point>, breaks: List<Int>): List<Point> {
        val breakAt = breaks.filter { it in 1..points.size }.toSet()
        val path = ArrayList<Point>(points.size + breakAt.size)
        for (i in 0..points.size) {
            if (i in breakAt) path.add(Point(Double.NaN, Double.NaN, points[i - 1].timeMs, 0f))
            if (i < points.size) path.add(points[i])
        }
        return path
    }
}
