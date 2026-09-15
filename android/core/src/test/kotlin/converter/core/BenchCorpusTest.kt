package converter.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 *
 * A price counts only when the amount and the currency are both right, and a
 * wrong answer is counted apart from a miss: on a phone as on a page, showing
 * the wrong price is worse than showing none.
 */
class BenchCorpusTest {

    @Serializable
    data class JsonLine(
        val text: String,
        val confidence: Double = 1.0,
        val box: List<Double>? = null,
    )

    @Serializable
    data class JsonImage(val image: String, val ms: Double = 0.0, val lines: List<JsonLine>)

    @Serializable
    data class JsonEngine(
        val engine: String,
        val config: String = "",
        val images: List<JsonImage>,
    )

    @Serializable
    data class JsonTruth(val id: String, val note: String = "", val truth: List<String>)

    data class Score(val hits: Int, val misses: Int, val wrong: Int) {
        override fun toString() = "$hits ok / $misses missed / $wrong wrong"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/bench/$name")
            ?.bufferedReader()?.readText()
            ?: error("missing test resource /bench/$name")

    private val truth: List<JsonTruth> by lazy {
        json.decodeFromString(resource("truth.json"))
    }

    private fun engine(file: String): JsonEngine = json.decodeFromString(resource(file))

    private fun JsonLine.toOcrLine() = OcrLine(
        text = text,
        confidence = confidence,
        box = box?.let { Box(it[0], it[1], it[2], it[3]) },
    )

    /** The prices a case is supposed to yield, read with the plain rules. */
    private fun expected(case: JsonTruth): List<Price> =
        case.truth.mapNotNull { findPrices(it).firstOrNull() }

    private fun score(engine: JsonEngine, merge: Boolean, homoglyphs: Boolean): Score {
        val byId = engine.images.associateBy { it.image }
        var hits = 0
        var misses = 0
        var wrong = 0

        for (case in truth) {
            val want = expected(case)
            val lines = byId[case.id]?.lines?.map { it.toOcrLine() } ?: emptyList()
            val got = readPrices(lines, merge = merge, homoglyphs = homoglyphs).toMutableList()

            for (price in want) {
                if (got.remove(price)) hits++ else misses++
            }
            wrong += got.size
        }
        return Score(hits, misses, wrong)
    }

    private val totalPrices: Int by lazy { truth.sumOf { expected(it).size } }

    @Test
    fun `the corpus reads as 26 prices`() {
        assertEquals(26, totalPrices, "truth.json no longer yields the measured 26 prices")
    }

    @Test
    fun `Vision accurate converts every price but the tenge, and invents none`() {
        val score = score(engine("vision-accurate.json"), merge = true, homoglyphs = true)
        assertEquals(Score(25, 1, 0), score)
    }

    @Test
    fun `PaddleOCR needs both mitigations to match Vision`() {
        val paddle = engine("paddle-mobile.json")
        val raw = score(paddle, merge = false, homoglyphs = false)
        val glyphsOnly = score(paddle, merge = false, homoglyphs = true)
        val mergeOnly = score(paddle, merge = true, homoglyphs = false)
        val both = score(paddle, merge = true, homoglyphs = true)

        println(
            """
            PaddleOCR mobile
              raw           $raw
              + homoglyphs  $glyphsOnly
              + box merge   $mergeOnly
              + both        $both
            """.trimIndent()
        )

        assertEquals(Score(25, 1, 0), both, "both mitigations together should reach 25/26")
        assertTrue(glyphsOnly.hits < both.hits, "homoglyphs alone should not be enough")
        assertTrue(mergeOnly.hits < both.hits, "box merging alone should not be enough")
    }

    @Test
    fun `a Latin-first recognizer cannot be rescued by homoglyphs alone`() {
        // Vision's fast level stands in for ML Kit, which has no Cyrillic model.
        val fast = engine("vision-fast.json")
        val best = score(fast, merge = true, homoglyphs = true)
        assertTrue(
            best.hits <= 20,
            "a Latin-first engine scored $best; the benchmark measured about 18/26"
        )
    }

    @Test
    fun `no engine invents a price once both mitigations are on`() {
        // The one thing the phone app must never do. Tesseract is the exception
        // the benchmark found — it reads "799 руб." as "199 руб." on the angled
        // shot — and it is here to keep that visible rather than forgotten.
        for (file in listOf("vision-accurate.json", "paddle-mobile.json", "vision-fast.json")) {
            val score = score(engine(file), merge = true, homoglyphs = true)
            assertEquals(0, score.wrong, "$file invented a price: $score")
        }
        val tesseract = score(engine("tesseract.json"), merge = true, homoglyphs = true)
        assertEquals(1, tesseract.wrong, "Tesseract's known wrong answer changed: $tesseract")
    }
}
