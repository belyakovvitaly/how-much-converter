package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxTrackingTest {

    private fun box(x0: Double, y0: Double, x1: Double, y1: Double) = Box(x0, y0, x1, y1)

    // --- overlap ------------------------------------------------------------
    @Test
    fun `a box overlaps itself completely`() {
        val b = box(10.0, 10.0, 20.0, 20.0)
        assertEquals(1.0, overlap(b, b), 1e-9)
    }

    @Test
    fun `boxes that do not meet do not overlap`() {
        assertEquals(0.0, overlap(box(0.0, 0.0, 10.0, 10.0), box(20.0, 20.0, 30.0, 30.0)))
    }

    @Test
    fun `boxes that only touch along an edge do not overlap`() {
        assertEquals(0.0, overlap(box(0.0, 0.0, 10.0, 10.0), box(10.0, 0.0, 20.0, 10.0)))
    }

    @Test
    fun `half of each box in common is a third of their union`() {
        // Two 10x10 boxes offset by 5 horizontally: intersection 50, union 150.
        val score = overlap(box(0.0, 0.0, 10.0, 10.0), box(5.0, 0.0, 15.0, 10.0))
        assertEquals(1.0 / 3.0, score, 1e-9)
    }

    @Test
    fun `overlap is a ratio, so one threshold works at any text size`() {
        val small = overlap(box(0.0, 0.0, 10.0, 10.0), box(1.0, 0.0, 11.0, 10.0))
        val large = overlap(box(0.0, 0.0, 100.0, 100.0), box(10.0, 0.0, 110.0, 100.0))
        assertEquals(small, large, 1e-9)
    }

    @Test
    fun `an empty box overlaps nothing`() {
        assertEquals(0.0, overlap(box(5.0, 5.0, 5.0, 5.0), box(0.0, 0.0, 10.0, 10.0)))
    }

    // --- reuse --------------------------------------------------------------
    private val tracked = TrackedLine(box(100.0, 100.0, 200.0, 140.0), "1 299 ₽", 0.9)
    private val state = TrackerState(listOf(tracked))

    @Test
    fun `a box that barely moved reuses the earlier reading`() {
        val moved = box(102.0, 101.0, 202.0, 141.0)
        assertEquals(tracked, state.reuseFor(moved))
    }

    @Test
    fun `a box that moved off the old one is read again`() {
        val elsewhere = box(400.0, 400.0, 500.0, 440.0)
        assertNull(state.reuseFor(elsewhere))
    }

    @Test
    fun `a box that changed size is read again`() {
        // Same corner, twice the size: the text under it is not the same text.
        val grown = box(100.0, 100.0, 300.0, 180.0)
        assertNull(state.reuseFor(grown))
    }

    @Test
    fun `the closest match wins, so neighbouring prices do not swap texts`() {
        val upper = TrackedLine(box(0.0, 0.0, 100.0, 40.0), "upper", 0.9)
        val lower = TrackedLine(box(0.0, 42.0, 100.0, 82.0), "lower", 0.9)
        val both = TrackerState(listOf(upper, lower))

        // Sits almost exactly on the lower one.
        val query = box(0.0, 43.0, 100.0, 83.0)
        assertEquals("lower", both.reuseFor(query, TrackerSettings(minOverlap = 0.5))?.text)
    }

    @Test
    fun `a reading cannot be carried forever`() {
        // Re-reading periodically is what stops a stale price sitting on screen
        // after the thing it was read from has changed.
        val settings = TrackerSettings(maxReuses = 3)
        val stale = TrackerState(listOf(tracked.copy(reuses = 3)))
        assertNull(stale.reuseFor(tracked.box, settings))

        val nearlyStale = TrackerState(listOf(tracked.copy(reuses = 2)))
        assertTrue(nearlyStale.reuseFor(tracked.box, settings) != null)
    }

    @Test
    fun `an empty tracker reuses nothing`() {
        assertNull(TrackerState().reuseFor(tracked.box))
    }
}
