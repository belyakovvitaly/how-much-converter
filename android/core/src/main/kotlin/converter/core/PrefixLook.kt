// Looking again, to the left of a number, for a currency the detector missed.
//
// A symbol written apart from the digits — a chalked "$ 5600 x Kg" — is thin
// and sparse, and the detector can find nothing of it but a speck. The number
// is read, the symbol never is, and a bare number is rightly left alone. Read
// again with room to its left, the same line comes back as "$5600Kg": the
// recognizer sees the symbol perfectly well once it is in the crop.
//
// The second look is allowed to add a symbol and nothing else. The digits are
// always the first reading's — a symbol misread as a digit and fused into the
// number is the one failure this project exists to refuse — and the symbol is
// taken only when it stands directly before that same number.
package converter.core

private val LEADING_NUMBER = Regex("^(?:$NUMBER)")

/** A currency symbol written with at least one mark that is not a letter. */
private val PREFIX_SYMBOLS: Set<String> =
    SYMBOL_TO_CODE.keys.filterTo(mutableSetOf()) { token -> token.any { !it.isLetter() } }

/**
 * The number a line of text starts with, when nothing says what currency it is
 * in and a look to its left might; null otherwise.
 */
fun leadingBareNumber(text: String): String? {
    val trimmed = text.trimStart()
    val number = LEADING_NUMBER.find(trimmed)?.value ?: return null
    // "5600 ₽" already carries its currency, after the number.
    val attached = OCR_PRICE_REGEX.find(trimmed)
    if (attached != null && attached.range.first == 0) return null
    return number
}

/**
 * The first reading with the symbol the second one found in front of it, or
 * null if the second reading does not show a known symbol directly before the
 * very same number.
 */
fun withPrefixFrom(first: String, second: String): String? {
    val number = leadingBareNumber(first) ?: return null
    val match = Regex("^(\\S{1,4}?)\\s?(\\d.*)$").find(second.trim()) ?: return null
    val symbol = match.groupValues[1]
    if (symbol !in PREFIX_SYMBOLS) return null
    val again = LEADING_NUMBER.find(match.groupValues[2])?.value ?: return null
    if (again.filterNot(Char::isWhitespace) != number.filterNot(Char::isWhitespace)) return null
    // Written touching the number, so the symbol belongs to it and to nothing
    // before it: "8 $ 5600" would also read as a price of eight.
    return symbol + first.trimStart()
}

/**
 * Whether a line is one glyph's worth of reading in a box about as wide as it
 * is tall — which, just before a bare number, is likelier the symbol the
 * recognizer could not make out than a word of its own. A chalked "$" alone
 * came back as "8", "69" and "89" at different sizes of the same photograph.
 */
fun looksLikeLoneGlyph(line: OcrLine): Boolean {
    val box = line.box ?: return false
    return line.text.trim().length <= 2 && box.x1 - box.x0 <= box.height * 1.5
}

/** The same region, lengthened by [by] at the end its text starts from. */
fun RotatedBox.extendedBack(by: Double): RotatedBox {
    val c = kotlin.math.cos(angle)
    val s = kotlin.math.sin(angle)
    return copy(
        centerX = centerX - c * by / 2,
        centerY = centerY - s * by / 2,
        width = width + by,
    )
}

/** Whether two boxes share any area. */
fun Box.intersects(other: Box): Boolean =
    x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1
