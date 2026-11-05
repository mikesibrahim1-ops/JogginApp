package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRunTest {

    private fun points(n: Int) = (1..n).map { Point(it.toDouble(), it.toDouble(), it * 1000L, 1f) }

    @Test fun `no pauses keeps the points as they are`() =
        assertEquals(points(3), ActiveRun.pathWithBreaks(points(3), emptyList()))

    @Test fun `no points gives an empty path`() =
        assertTrue(ActiveRun.pathWithBreaks(emptyList(), listOf(0)).isEmpty())

    @Test fun `a pause puts a break between the points either side`() {
        val path = ActiveRun.pathWithBreaks(points(4), listOf(2))
        assertEquals(5, path.size)
        assertTrue(path[2].lat.isNaN())
        assertEquals(2.0, path[1].lat, 0.0)
        assertEquals(3.0, path[3].lat, 0.0)
    }

    @Test fun `a pause still in progress leaves a break at the end`() {
        val path = ActiveRun.pathWithBreaks(points(2), listOf(2))
        assertEquals(3, path.size)
        assertTrue(path.last().lat.isNaN())
    }

    @Test fun `a pause before any point adds no break`() =
        assertEquals(points(2), ActiveRun.pathWithBreaks(points(2), listOf(0)))

    @Test fun `repeat pauses with no points between add one break`() =
        assertEquals(4, ActiveRun.pathWithBreaks(points(3), listOf(1, 1)).size)

    @Test fun `a break does not change the distance`() {
        val plain = points(4)
        val broken = ActiveRun.pathWithBreaks(plain, listOf(2))
        val expected = calcDistanceKm(plain.subList(0, 2)) + calcDistanceKm(plain.subList(2, 4))
        assertEquals(expected, calcDistanceKm(broken), 0.0001)
    }
}
