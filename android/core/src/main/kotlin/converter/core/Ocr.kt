// Turning what an OCR engine returns into text the price rules can read.
//
// Two things stand between a recognizer and a price, and the benchmark in
// tools/ocr-bench measures both: a detector splits one price across boxes, and
// a recognizer drifts between alphabets on currency glyphs. Neither is specific
// to Android — iOS needs the same two — so both live here rather than beside
// the engine.
package converter.core

/** A box in image pixels, origin top-left. */
data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    val height: Double get() = y1 - y0
}

/**
 * One line as an engine reported it.
 *
 * [reused] marks a line carried over from an earlier frame instead of read in
 * this one — see BoxTracking.kt. It matters downstream: a carried reading is
 * not independent evidence, and the voting must not treat it as such.
 */
data class OcrLine(
    val text: String,
    val confidence: Double = 1.0,
    val box: Box? = null,
    val reused: Boolean = false,
)

/**
 * What a recognizer emits instead of the real glyph.
 *
 * These are OCR artifacts, not ways anyone writes money, which is why they are
 * kept apart from SYMBOL_TO_CODE: teaching the converter that "P" means RUB
 * would turn every English sentence into a price. The reference copy is
 * HOMOGLYPHS in tools/ocr-bench/bench.py, and the benchmark parity test fails
 * if the two drift apart.
 */
val HOMOGLYPHS: Map<String, String> = mapOf(
    // ₽ read as Latin P or Cyrillic Р
    "P" to "RUB", "Р" to "RUB", "p" to "RUB",
    // руб. with its letters swapped for lookalikes
    "py6" to "RUB", "py6." to "RUB", "pу6" to "RUB", "pу6." to "RUB",
    "pyб" to "RUB", "pyб." to "RUB", "pуб" to "RUB", "pуб." to "RUB",
    // грн
    "rpH" to "UAH", "rph" to "UAH", "рпн" to "UAH",
    // ₸ and тг
    "T" to "KZT", "Tг" to "KZT", "Tr" to "KZT", "tr" to "KZT",
    // € read as Cyrillic Є
    "Є" to "EUR",
)

/**
 * Clusters boxes into visual rows, then walks each row left to right and breaks
 * it wherever the horizontal gap is too wide to be one phrase.
 *
 * This is the OCR counterpart of the second pass in extension/src/content.js:
 * there a price is split across sibling elements, here across detection boxes,
 * and either way the number and its currency have to be reunited before
 * adjacency can be seen at all.
 *
 * Rows are built before anything is ordered left to right. Sorting by y first
 * lets a right-hand fragment open a group and strand the digits to its left,
 * which is what once turned "1 299" into "299" — a wrong price, which is worse
 * than no price.
 */
fun mergeBoxes(lines: List<OcrLine>): List<String> {
    data class Row(var y0: Double, var y1: Double, val items: MutableList<OcrLine>)

    val placed = lines.filter { it.box != null && it.text.isNotBlank() }
        .sortedBy { it.box!!.y0 }

    val rows = mutableListOf<Row>()
    for (line in placed) {
        val box = line.box!!
        val row = rows.firstOrNull { row ->
            val overlap = minOf(box.y1, row.y1) - maxOf(box.y0, row.y0)
            overlap > 0.5 * minOf(box.height, row.y1 - row.y0)
        }
        if (row == null) {
            rows += Row(box.y0, box.y1, mutableListOf(line))
        } else {
            row.items += line
            row.y0 = minOf(row.y0, box.y0)
            row.y1 = maxOf(row.y1, box.y1)
        }
    }

    val out = mutableListOf<String>()
    for (row in rows) {
        row.items.sortBy { it.box!!.x0 }
        var group = mutableListOf<String>()
        var groupX1 = Double.NaN
        var groupHeight = 0.0
        for (line in row.items) {
            val box = line.box!!
            if (group.isNotEmpty() && box.x0 - groupX1 > 1.2 * maxOf(box.height, groupHeight)) {
                out += group.joinToString(" ")
                group = mutableListOf()
                groupX1 = Double.NaN
                groupHeight = 0.0
            }
            group += line.text
            groupX1 = if (groupX1.isNaN()) box.x1 else maxOf(groupX1, box.x1)
            groupHeight = maxOf(groupHeight, box.height)
        }
        if (group.isNotEmpty()) out += group.joinToString(" ")
    }
    return out
}

/**
 * Reads the prices out of one engine's output for one image.
 *
 * [homoglyphs] is on by default because no recognizer this project measured
 * gets the currency glyphs right on its own, and [merge] because a large price
 * is routinely split in two.
 *
 * [minConfidence] defaults to off, and the benchmark is why. A confidence gate
 * looks like the obvious guard against a wrong price and is not one: on the
 * corpus it never removes a wrong reading before it starts removing right ones.
 * Tesseract's one invented price — "799 руб." read as "199 руб." — is reported
 * at 0.90 confidence, while the seven Vision lines that sit at 0.50 are all
 * correct. Gating Vision above 0.5 costs five real prices and removes nothing.
 * ConfidenceGateTest pins that, so the idea is not quietly reintroduced.
 */
fun readPrices(
    lines: List<OcrLine>,
    context: PriceContext = PriceContext(),
    merge: Boolean = true,
    homoglyphs: Boolean = true,
    minConfidence: Double = 0.0,
): List<Price> {
    // Filtered before merging: a dropped fragment must not join a group either.
    val kept = if (minConfidence > 0.0) lines.filter { it.confidence >= minConfidence }
               else lines
    val texts = if (merge) mergeBoxes(kept) else kept.map { it.text }
    val resolved = if (homoglyphs) {
        PriceContext(
            pageCurrency = context.pageCurrency,
            dollarAssumption = context.dollarAssumption,
            isKnownCode = { token -> HOMOGLYPHS[token] ?: context.isKnownCode(token) },
        )
    } else {
        context
    }
    val regex = if (homoglyphs) OCR_PRICE_REGEX else PRICE_REGEX
    return texts.flatMap { findPrices(it, resolved, regex) }
}

/**
 * The price pattern widened by the homoglyph tokens. They have to be in the
 * alternation as well as in the lookup: the pattern is what decides whether a
 * token sits next to the number at all.
 */
val OCR_PRICE_REGEX: Regex by lazy { buildPriceRegex(HOMOGLYPHS.keys) }
