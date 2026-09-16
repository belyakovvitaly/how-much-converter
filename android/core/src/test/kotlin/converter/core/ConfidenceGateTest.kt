package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Why there is no confidence gate.
 *
 * Refusing a low-confidence reading is the obvious way to keep a wrong price
 * off the screen, and on this corpus it does not work: the recognizers are
 * confident when they are wrong and hesitant when they are right. These tests
 * exist so that the next person to reach for a threshold sees the measurement
 * instead of rediscovering it.
 *
 * If a future corpus shows a gate paying for itself, these numbers will fail
 * and should be re-measured rather than deleted.
 */
class ConfidenceGateTest {

    private val bench = BenchFixtures()

    @Test
    fun `the one invented price is reported confidently`() {
        val angled = bench.engine("tesseract.json").images
            .first { it.image == "10-camera-angle" }
        val line = angled.lines.single()

        // The shot says "799 руб."; Tesseract reads 199 and is sure about it.
        assertEquals("199 руб.", line.text)
        assertTrue(
            line.confidence >= 0.85,
            "the wrong reading came back at ${line.confidence}; no usable gate sits above that",
        )
    }

    @Test
    fun `gating Vision above its floor costs real prices and removes nothing wrong`() {
        val vision = bench.engine("vision-accurate.json")

        val ungated = bench.score(vision, minConfidence = 0.0)
        val gated = bench.score(vision, minConfidence = 0.6)

        assertEquals(0, ungated.wrong)
        assertEquals(0, gated.wrong, "there was nothing wrong for the gate to remove")
        assertTrue(
            gated.hits < ungated.hits,
            "the gate was supposed to be costly: ${ungated.hits} -> ${gated.hits}",
        )
        assertEquals(25, ungated.hits)
        assertEquals(20, gated.hits)
    }

    @Test
    fun `no threshold removes Tesseract's wrong price while it still reads the right ones`() {
        val tesseract = bench.engine("tesseract.json")

        // Walk up the range: the wrong answer outlives every threshold that
        // leaves the correct readings intact.
        val baseline = bench.score(tesseract, minConfidence = 0.0)
        for (threshold in listOf(0.5, 0.6, 0.7, 0.8)) {
            val score = bench.score(tesseract, minConfidence = threshold)
            assertEquals(
                1, score.wrong,
                "a gate at $threshold was expected to keep the wrong price, got $score",
            )
        }

        val harsh = bench.score(tesseract, minConfidence = 0.9)
        assertEquals(0, harsh.wrong, "0.9 finally removes it")
        assertTrue(
            harsh.hits < baseline.hits,
            "and takes correct prices with it: ${baseline.hits} -> ${harsh.hits}",
        )
    }
}
