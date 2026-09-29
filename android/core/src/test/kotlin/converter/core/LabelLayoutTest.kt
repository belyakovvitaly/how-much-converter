package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelLayoutTest {

    private fun overlap(a: Box, b: Box) =
        minOf(a.x1, b.x1) > maxOf(a.x0, b.x0) && minOf(a.y1, b.y1) > maxOf(a.y0, b.y0)

    private fun assertNoOverlaps(areas: List<Box>) {
        for (i in areas.indices) for (j in i + 1 until areas.size) {
            assertTrue(!overlap(areas[i], areas[j]), "labels $i and $j overlap: ${areas[i]} ${areas[j]}")
        }
    }

    private fun covers(area: Box, box: Box) =
        area.x0 <= box.x0 && area.y0 <= box.y0 && area.x1 >= box.x1 && area.y1 >= box.y1

    @Test
    fun `a label on its own keeps its whole margin`() {
        val box = Box(100.0, 200.0, 300.0, 250.0)
        assertEquals(listOf(Box(94.0, 194.0, 306.0, 256.0)), labelAreas(listOf(box)))
    }

    @Test
    fun `labels far apart are left alone`() {
        val boxes = listOf(Box(0.0, 0.0, 100.0, 40.0), Box(0.0, 200.0, 100.0, 240.0))
        assertEquals(boxes.map { it.labelArea() }, labelAreas(boxes))
    }

    @Test
    fun `neighbours a line apart give up their margins, not their prices`() {
        // Two amounts 4 px apart: their margins overlap, the amounts do not.
        val upper = Box(600.0, 100.0, 800.0, 140.0)
        val lower = Box(600.0, 144.0, 800.0, 184.0)
        val areas = labelAreas(listOf(upper, lower))
        assertNoOverlaps(areas)
        assertTrue(covers(areas[0], upper), "the upper price is still covered")
        assertTrue(covers(areas[1], lower), "the lower price is still covered")
    }

    @Test
    fun `boxes that overlap are divided halfway between their lines`() {
        // A detector's boxes for slightly turned text: taller than the text,
        // and overlapping the next line's.
        val upper = Box(600.0, 100.0, 800.0, 160.0)
        val lower = Box(610.0, 130.0, 790.0, 190.0)
        val areas = labelAreas(listOf(upper, lower))
        assertNoOverlaps(areas)
        // Each keeps its own centre line: the label is still on its price.
        assertTrue(areas[0].y0 < 130.0 && areas[0].y1 > 130.0)
        assertTrue(areas[1].y0 < 160.0 && areas[1].y1 > 160.0)
    }

    @Test
    fun `two prices on one line are divided side by side`() {
        // "2,50 x 14000,00" read as two amounts whose margins touch.
        val left = Box(100.0, 100.0, 200.0, 140.0)
        val right = Box(205.0, 101.0, 400.0, 141.0)
        val areas = labelAreas(listOf(left, right))
        assertNoOverlaps(areas)
        assertTrue(covers(areas[0], left) && covers(areas[1], right))
        // Divided across, not above and below: each keeps its full height.
        assertEquals(left.labelArea().height, areas[0].height, 1e-9)
    }

    @Test
    fun `a stack of close lines comes apart, whatever order it arrives in`() {
        // A receipt's subtotal, discounts and total, one under another.
        val stack = listOf(
            Box(640.0, 1400.0, 850.0, 1450.0),
            Box(640.0, 1440.0, 850.0, 1490.0),
            Box(640.0, 1480.0, 850.0, 1530.0),
        )
        assertNoOverlaps(labelAreas(stack))
        assertNoOverlaps(labelAreas(stack.reversed()))
    }

    @Test
    fun `a label never swaps places with its neighbour`() {
        val stack = listOf(
            Box(640.0, 1440.0, 850.0, 1490.0),
            Box(640.0, 1400.0, 850.0, 1450.0),
        )
        val areas = labelAreas(stack)
        // The second box is the upper one, and its label stays above.
        assertTrue(areas[1].y1 <= areas[0].y0)
    }
}
