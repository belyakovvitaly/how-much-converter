package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The detector post-process, on maps small enough to reason about by hand.
 *
 * End-to-end accuracy is settled elsewhere — the ONNX pipeline built on these
 * functions scored 25 of the benchmark's 26 prices. What these tests protect is
 * the arithmetic underneath: that regions are separated, weak ones dropped, and
 * boxes grown the way PaddleOCR grows them.
 */
class DetectionTest {

    /** Builds a probability map from rows of characters; '#' is 1.0, '.' is 0. */
    private fun map(vararg rows: String): Triple<FloatArray, Int, Int> {
        val width = rows.first().length
        val probs = FloatArray(width * rows.size)
        rows.forEachIndexed { y, row ->
            require(row.length == width) { "ragged map" }
            row.forEachIndexed { x, ch ->
                probs[y * width + x] = when (ch) {
                    '#' -> 1.0f
                    '+' -> 0.5f   // above the 0.3 threshold, below the 0.6 box score
                    else -> 0.0f
                }
            }
        }
        return Triple(probs, width, rows.size)
    }

    @Test
    fun `separate regions become separate boxes`() {
        val (probs, w, h) = map(
            "##...##",
            "##...##",
            ".......",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f))
        assertEquals(2, found.size)

        val ordered = found.sortedBy { it.box.x0 }
        assertEquals(0.0, ordered[0].box.x0)
        assertEquals(2.0, ordered[0].box.x1)
        assertEquals(5.0, ordered[1].box.x0)
        assertEquals(7.0, ordered[1].box.x1)
    }

    @Test
    fun `a diagonal touch does not join two regions`() {
        // Four-way connectivity, like PaddleOCR's: pixels meeting at a corner
        // belong to different regions, or two adjacent words merge into one.
        val (probs, w, h) = map(
            "##..",
            "##..",
            "..##",
            "..##",
        )
        assertEquals(2, detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).size)
    }

    @Test
    fun `a region the detector is unsure of is dropped`() {
        val (probs, w, h) = map(
            "++++",
            "++++",
        )
        // Every pixel clears the 0.3 threshold, so the region forms; its mean
        // score of 0.5 does not clear the 0.6 box threshold, so it is discarded.
        assertTrue(detectBoxes(probs, w, h).isEmpty())
    }

    @Test
    fun `a speck too small to be text is dropped`() {
        val (probs, w, h) = map(
            "#...",
            "....",
        )
        assertTrue(detectBoxes(probs, w, h, DetectionSettings(minPixels = 4)).isEmpty())
    }

    @Test
    fun `unclip grows a box by area over perimeter`() {
        // 10 wide, 10 tall: 100 * 1.5 / (2 * 20) = 3.75 on every side.
        val grown = unclip(Box(20.0, 20.0, 30.0, 30.0), ratio = 1.5f, 100, 100)!!
        assertEquals(16.25, grown.x0)
        assertEquals(16.25, grown.y0)
        assertEquals(33.75, grown.x1)
        assertEquals(33.75, grown.y1)
    }

    @Test
    fun `unclip stays inside the image`() {
        val grown = unclip(Box(0.0, 0.0, 10.0, 10.0), ratio = 1.5f, 12, 12)!!
        assertEquals(0.0, grown.x0)
        assertEquals(0.0, grown.y0)
        assertEquals(12.0, grown.x1)
        assertEquals(12.0, grown.y1)
    }

    @Test
    fun `an empty map yields nothing rather than failing`() {
        val (probs, w, h) = map("....", "....")
        assertTrue(detectBoxes(probs, w, h).isEmpty())
    }

    @Test
    fun `a region filling the whole map does not overrun the stack`() {
        // The flood fill carries its own stack precisely for this case; on a
        // real frame one region can run to hundreds of thousands of pixels.
        val width = 200
        val height = 200
        val probs = FloatArray(width * height) { 1.0f }
        val found = detectBoxes(probs, width, height, DetectionSettings(unclipRatio = 0f))
        assertEquals(1, found.size)
        assertEquals(Box(0.0, 0.0, width.toDouble(), height.toDouble()), found.single().box)
    }

    // --- how a region sits --------------------------------------------------
    private fun degrees(detection: Detection) = Math.toDegrees(detection.rotated.angle)

    @Test
    fun `a horizontal line of text is read as upright`() {
        val (probs, w, h) = map(
            "........",
            "########",
            "........",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertEquals(0.0, degrees(found), 1.0)
    }

    @Test
    fun `a line running downhill is read as tilted, and by how much`() {
        // A staircase at roughly 45 degrees.
        val (probs, w, h) = map(
            "##......",
            "###.....",
            ".###....",
            "..###...",
            "...###..",
            "....###.",
            ".....###",
            "......##",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertEquals(45.0, degrees(found), 8.0)
    }

    @Test
    fun `a line running uphill tilts the other way`() {
        val (probs, w, h) = map(
            "......##",
            ".....###",
            "....###.",
            "...###..",
            "..###...",
            ".###....",
            "###.....",
            "##......",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertEquals(-45.0, degrees(found), 5.0)
    }

    @Test
    fun `a line all but level is read as level`() {
        // Sixty wide, stepping down one row halfway: under two degrees. Turning
        // the crop by that much gains nothing and resamples away faint glyphs —
        // a dotted minus on a receipt, which made a discount a purchase.
        val band = { from: Int -> CharArray(60) { if (it >= 30 == (from == 1)) '#' else '.' }.concatToString() }
        val (probs, w, h) = map(
            ".".repeat(60),
            band(0),
            "#".repeat(60),
            band(1),
            ".".repeat(60),
        )
        val measured = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f, minTiltDegrees = 0.0)).single()
        assertTrue(kotlin.math.abs(degrees(measured)) in 0.5..3.0, "fitted ${degrees(measured)}")

        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertEquals(0.0, degrees(found), 0.0)
    }

    @Test
    fun `a blob with no direction is left upright rather than guessed at`() {
        // A single character or a speck has no reading direction, and a
        // confidently wrong angle would turn a readable crop into a diagonal one.
        val (probs, w, h) = map(
            "####",
            "####",
            "####",
            "####",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertEquals(0.0, degrees(found), 0.001)
    }

    @Test
    fun `the upright bounds contain the whole tilted region`() {
        val (probs, w, h) = map(
            "##......",
            "###.....",
            ".###....",
            "..##....",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertTrue(found.box.x0 <= 0.5, "left edge cut off: ${found.box}")
        assertTrue(found.box.y0 <= 0.5, "top edge cut off: ${found.box}")
        assertTrue(found.box.x1 >= 3.5, "right edge cut off: ${found.box}")
        assertTrue(found.box.y1 >= 3.5, "bottom edge cut off: ${found.box}")
    }

    @Test
    fun `a tilted line is narrower than its upright bounds`() {
        // The point of the whole exercise: the rotated rectangle hugs the text,
        // while the upright one has to hold the diagonal and is mostly
        // background.
        val (probs, w, h) = map(
            "##......",
            "###.....",
            ".###....",
            "..###...",
            "...###..",
            "....###.",
            ".....###",
            "......##",
        )
        val found = detectBoxes(probs, w, h, DetectionSettings(unclipRatio = 0f)).single()
        assertTrue(
            found.rotated.height < (found.box.y1 - found.box.y0) * 0.75,
            "the rotated box is no tighter than the upright one: ${found.rotated}",
        )
    }
}
