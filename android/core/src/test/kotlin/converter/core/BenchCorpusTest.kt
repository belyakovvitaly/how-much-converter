package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays the OCR benchmark against this core.
 *
 * tools/ocr-bench measured four engines through the extension's price rules and
 * a Python port of them. This runs the same recorded engine output through the
 * Kotlin port, so the phone core is held to numbers that were established by
 * measurement rather than to whatever it happens to do.
 */
class BenchCorpusTest {

    private val bench = BenchFixtures()

    @Test
    fun `the corpus reads as 26 prices`() {
        assertEquals(26, bench.totalPrices, "truth.json no longer yields the measured 26 prices")
    }

    @Test
    fun `Vision accurate converts every price but the tenge, and invents none`() {
        assertEquals(
            BenchFixtures.Score(25, 1, 0),
            bench.score(bench.engine("vision-accurate.json")),
        )
    }

    @Test
    fun `PaddleOCR needs both mitigations to match Vision`() {
        val paddle = bench.engine("paddle-mobile.json")
        val raw = bench.score(paddle, merge = false, homoglyphs = false)
        val glyphsOnly = bench.score(paddle, merge = false, homoglyphs = true)
        val mergeOnly = bench.score(paddle, merge = true, homoglyphs = false)
        val both = bench.score(paddle, merge = true, homoglyphs = true)

        println(
            """
            PaddleOCR mobile
              raw           $raw
              + homoglyphs  $glyphsOnly
              + box merge   $mergeOnly
              + both        $both
            """.trimIndent()
        )

        assertEquals(BenchFixtures.Score(25, 1, 0), both, "both together should reach 25/26")
        assertTrue(glyphsOnly.hits < both.hits, "homoglyphs alone should not be enough")
        assertTrue(mergeOnly.hits < both.hits, "box merging alone should not be enough")

        // Homoglyphs without merging is the one combination that invents a
        // price: the fragments reunite as a number the mapping then completes.
        assertEquals(1, glyphsOnly.wrong, "homoglyphs alone should still invent one price")
    }

    @Test
    fun `a Latin-first recognizer cannot be rescued by homoglyphs alone`() {
        // Vision's fast level stands in for ML Kit, which has no Cyrillic model.
        val best = bench.score(bench.engine("vision-fast.json"))
        assertTrue(
            best.hits <= 20,
            "a Latin-first engine scored $best; the benchmark measured about 18/26",
        )
    }

    @Test
    fun `no engine invents a price once both mitigations are on`() {
        // The one thing the phone app must never do. Tesseract is the exception
        // the benchmark found — it reads "799 руб." as "199 руб." on the angled
        // shot — and it is here to keep that visible rather than forgotten.
        for (file in listOf("vision-accurate.json", "paddle-mobile.json", "vision-fast.json")) {
            val score = bench.score(bench.engine(file))
            assertEquals(0, score.wrong, "$file invented a price: $score")
        }
        val tesseract = bench.score(bench.engine("tesseract.json"))
        assertEquals(1, tesseract.wrong, "Tesseract's known wrong answer changed: $tesseract")
    }
}
