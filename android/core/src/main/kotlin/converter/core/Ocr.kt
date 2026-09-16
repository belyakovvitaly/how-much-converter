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

/** A run of text that reads as one phrase, and where each piece of it sits. */
data class MergedText(
    val text: String,
    /** The whole run, for a caller that does not care which piece is which. */
    val box: Box,
    /** Each source line's span within [text], so a match can be placed. */
    val parts: List<Part>,
) {
    data class Part(val range: IntRange, val box: Box)

    /**
     * Where the given span sits: the pieces it touches, each cut down to the
     * characters of it the span covers.
     *
     * A recognizer often returns a price with its neighbours in one box —
     * "2,332 x 14000,00" on a receipt — and a label over the whole box would
     * hide the quantity along with the price. A box says nothing about where
     * each character is, so the cut assumes they are evenly spaced. That is
     * exact for a receipt's fixed-width type and close enough for a tag's.
     */
    fun boxFor(range: IntRange): Box {
        val touched = parts.filter { it.range.first <= range.last && range.first <= it.range.last }
        if (touched.isEmpty()) return box
        val cut = touched.map { part ->
            val length = part.range.last - part.range.first + 1
            val from = maxOf(range.first, part.range.first) - part.range.first
            val to = minOf(range.last, part.range.last) - part.range.first + 1
            val width = part.box.x1 - part.box.x0
            part.box.copy(
                x0 = part.box.x0 + width * from / length,
                x1 = part.box.x0 + width * to / length,
            )
        }
        return Box(
            x0 = cut.minOf { it.x0 },
            y0 = cut.minOf { it.y0 },
            x1 = cut.maxOf { it.x1 },
            y1 = cut.maxOf { it.y1 },
        )
    }
}

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
fun mergeBoxes(lines: List<OcrLine>): List<MergedText> {
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

    val out = mutableListOf<MergedText>()

    fun flush(group: List<OcrLine>) {
        if (group.isEmpty()) return
        val builder = StringBuilder()
        val parts = mutableListOf<MergedText.Part>()
        for (line in group) {
            if (builder.isNotEmpty()) builder.append(' ')
            val start = builder.length
            builder.append(line.text)
            parts += MergedText.Part(start until builder.length, line.box!!)
        }
        out += MergedText(
            text = builder.toString(),
            box = Box(
                x0 = group.minOf { it.box!!.x0 },
                y0 = group.minOf { it.box!!.y0 },
                x1 = group.maxOf { it.box!!.x1 },
                y1 = group.maxOf { it.box!!.y1 },
            ),
            parts = parts,
        )
    }

    for (row in rows) {
        row.items.sortBy { it.box!!.x0 }
        var group = mutableListOf<OcrLine>()
        var groupX1 = Double.NaN
        var groupHeight = 0.0
        for (line in row.items) {
            val box = line.box!!
            if (group.isNotEmpty() && box.x0 - groupX1 > 1.2 * maxOf(box.height, groupHeight)) {
                flush(group)
                group = mutableListOf()
                groupX1 = Double.NaN
                groupHeight = 0.0
            }
            group += line
            groupX1 = if (groupX1.isNaN()) box.x1 else maxOf(groupX1, box.x1)
            groupHeight = maxOf(groupHeight, box.height)
        }
        flush(group)
    }
    return out
}

/** A price and the part of the image it was read from. */
data class LocatedPrice(val price: Price, val box: Box)

/**
 * Reads the prices out of one engine's output, keeping where each one sits.
 *
 * Position is what lets the conversion be drawn over the price itself rather
 * than listed somewhere else on the screen.
 */
fun locatePrices(
    lines: List<OcrLine>,
    context: PriceContext = PriceContext(),
    merge: Boolean = true,
    homoglyphs: Boolean = true,
    minConfidence: Double = 0.0,
): List<LocatedPrice> {
    // Filtered before merging: a dropped fragment must not join a group either.
    val kept = if (minConfidence > 0.0) lines.filter { it.confidence >= minConfidence }
               else lines

    val runs: List<MergedText> = if (merge) {
        mergeBoxes(kept)
    } else {
        kept.mapNotNull { line ->
            val box = line.box ?: return@mapNotNull null
            MergedText(line.text, box, listOf(MergedText.Part(line.text.indices, box)))
        }
    }

    val resolved = if (homoglyphs) {
        context.copy(isKnownCode = { token -> HOMOGLYPHS[token] ?: context.isKnownCode(token) })
    } else {
        context
    }
    val regex = if (homoglyphs) OCR_PRICE_REGEX else PRICE_REGEX

    return runs.flatMap { run ->
        val found = findPricesWithRanges(run.text, resolved, regex)
        // A number that already has a currency keeps it; only the rest are
        // taken to be in the receipt's.
        val bare = context.bareAmounts?.let { code ->
            findBareAmounts(run.text)
                .filter { amount -> found.none { it.range.overlaps(amount.range) } }
                .map { FoundPrice(Price(it.amount, code), it.range) }
        }.orEmpty()
        (found + bare).sortedBy { it.range.first }.map {
            LocatedPrice(it.price, run.boxFor(it.range))
        }
    }
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
): List<Price> =
    locatePrices(lines, context, merge, homoglyphs, minConfidence).map { it.price }

private fun IntRange.overlaps(other: IntRange) =
    first <= other.last && other.first <= last

/**
 * The price pattern widened by the homoglyph tokens. They have to be in the
 * alternation as well as in the lookup: the pattern is what decides whether a
 * token sits next to the number at all.
 */
val OCR_PRICE_REGEX: Regex by lazy { buildPriceRegex(HOMOGLYPHS.keys) }
