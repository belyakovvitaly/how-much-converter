package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomTest {

    private val w = 1000.0
    private val h = 2000.0

    /** Where a point of the unmagnified view ends up on screen. */
    private fun Zoom.screen(x: Double, y: Double) =
        Pair(w / 2 + scale * (x - w / 2) + offsetX, h / 2 + scale * (y - h / 2) + offsetY)

    @Test
    fun `what is under the fingers stays under them`() {
        val zoomed = Zoom().transformed(2.0, 300.0, 700.0, 0.0, 0.0, w, h)
        val (x, y) = zoomed.screen(300.0, 700.0)
        assertEquals(300.0, x, 1e-9)
        assertEquals(700.0, y, 1e-9)

        // And again from an already magnified, shifted view.
        val start = Zoom(2.0, 100.0, -200.0)
        val focus = start.screen(400.0, 900.0)
        val again = start.transformed(1.5, focus.first, focus.second, 0.0, 0.0, w, h)
        val (x2, y2) = again.screen(400.0, 900.0)
        assertEquals(focus.first, x2, 1e-9)
        assertEquals(focus.second, y2, 1e-9)
    }

    @Test
    fun `it neither shrinks below the whole picture nor grows without end`() {
        assertEquals(1.0, Zoom().transformed(0.2, 500.0, 1000.0, 0.0, 0.0, w, h).scale)
        assertEquals(Zoom.MAX_SCALE, Zoom().transformed(100.0, 500.0, 1000.0, 0.0, 0.0, w, h).scale)
    }

    @Test
    fun `the picture cannot be dragged past its edge`() {
        val dragged = Zoom(2.0).transformed(1.0, 0.0, 0.0, 5000.0, -5000.0, w, h)
        assertEquals(500.0, dragged.offsetX)
        assertEquals(-1000.0, dragged.offsetY)
        // At the whole picture there is nowhere to drag to.
        assertEquals(Zoom(), Zoom().transformed(1.0, 0.0, 0.0, 300.0, 300.0, w, h))
    }

    @Test
    fun `double tap goes in at the tap and back out to the whole picture`() {
        val tapped = Zoom().toggled(200.0, 400.0, w, h)
        assertEquals(Zoom.DOUBLE_TAP_SCALE, tapped.scale)
        val (x, y) = tapped.screen(200.0, 400.0)
        assertEquals(200.0, x, 1e-9)
        assertEquals(400.0, y, 1e-9)
        assertEquals(Zoom(), tapped.toggled(0.0, 0.0, w, h))
    }
}
