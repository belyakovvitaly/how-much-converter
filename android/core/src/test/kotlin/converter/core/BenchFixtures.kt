package converter.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads and scores the engine output recorded by tools/ocr-bench.
 *
 * The JSON under src/test/resources/bench is a copy of what the runners in
 * tools/ocr-bench/out produce, committed so these tests need neither the
 * engines nor the images to run.
 */
class BenchFixtures {

    @Serializable
    data class JsonLine(
        val text: String,
        val confidence: Double = 1.0,
        val box: List<Double>? = null,
    ) {
        fun toOcrLine() = OcrLine(
            text = text,
            confidence = confidence,
            box = box?.let { Box(it[0], it[1], it[2], it[3]) },
        )
    }

    @Serializable
    data class JsonImage(
        val image: String,
        val ms: Double = 0.0,
        val lines: List<JsonLine>,
    )

    @Serializable
    data class JsonEngine(
        val engine: String,
        val config: String = "",
        val images: List<JsonImage>,
    )

    @Serializable
    data class JsonTruth(val id: String, val note: String = "", val truth: List<String>)

    /**
     * How an engine did. A wrong answer is counted apart from a miss and never
     * netted against a hit: on a phone as on a page, the wrong price is worse
     * than no price.
     */
    data class Score(val hits: Int, val misses: Int, val wrong: Int) {
        override fun toString() = "$hits ok / $misses missed / $wrong wrong"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/bench/$name")
            ?.bufferedReader()?.readText()
            ?: error("missing test resource /bench/$name")

    val truth: List<JsonTruth> by lazy { json.decodeFromString(resource("truth.json")) }

    fun engine(file: String): JsonEngine = json.decodeFromString(resource(file))

    /** The prices a case is supposed to yield, read with the plain rules. */
    fun expected(case: JsonTruth): List<Price> =
        case.truth.mapNotNull { findPrices(it).firstOrNull() }

    val totalPrices: Int by lazy { truth.sumOf { expected(it).size } }

    fun score(
        engine: JsonEngine,
        merge: Boolean = true,
        homoglyphs: Boolean = true,
        minConfidence: Double = 0.0,
    ): Score {
        val byId = engine.images.associateBy { it.image }
        var hits = 0
        var misses = 0
        var wrong = 0

        for (case in truth) {
            val want = expected(case)
            val lines = byId[case.id]?.lines?.map { it.toOcrLine() } ?: emptyList()
            val got = readPrices(
                lines,
                merge = merge,
                homoglyphs = homoglyphs,
                minConfidence = minConfidence,
            ).toMutableList()

            for (price in want) {
                if (got.remove(price)) hits++ else misses++
            }
            wrong += got.size
        }
        return Score(hits, misses, wrong)
    }
}
