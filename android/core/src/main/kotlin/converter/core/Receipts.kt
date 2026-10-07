// Reading the amounts on a shop receipt, which mostly carry no currency at all.
//
// On a price tag or a web page a number without a currency is left alone: it
// could be anything, and a wrong price is worse than none. A receipt is the
// exception the reader asks for by hand — every total and line on it is in the
// shop's currency, and the currency is printed nowhere near them. So the reader
// names it, and what is left to decide is which numbers are amounts.
//
// A receipt is dense with numbers that are not: dates, times, weights, article
// codes, tax IDs, till numbers. What sets an amount apart is that it is written
// to the cent, so that is the rule — exactly two decimals, standing alone.
//
// Except where there are no cents to write. A Chilean receipt says `8.990` and
// `24.040`: pesos, grouped by thousands. For a currency with no minor unit a
// whole number counts too, but only one long enough to need its thousands
// separator — `990` is as likely a count or a till number as a price, and is
// left alone.
package converter.core

/** An amount found without a currency, and where in the text it was written. */
data class BareAmount(val amount: Double, val range: IntRange)

/**
 * `1234,56`, `1.234,56`, `1,234.56`, `1 234,56`, with an optional minus for a
 * discount line.
 *
 * - Exactly two decimals, and nothing numeric either side: that is what keeps
 *   out `0.654` (a weight), `10/09/2026`, `13:24:07` and `30-54808315-6`.
 * - A grouping separator must differ from the decimal one, or `1.234.56` would
 *   be read as whatever the parser made of it.
 * - A number followed by a multiplication sign is a quantity, not a price:
 *   `2,50 x 14000,00` is two and a half kilos at fourteen thousand.
 * - A number followed by `%` is a rate.
 * - A minus may arrive as `~`: a faint dotted dash on thermal paper is read
 *   that way, and losing it would turn a discount into a purchase.
 */
private val BARE_AMOUNT = Regex(
    "(?<![\\p{L}\\d.,:/-])" +
    "([-–−~]\\s?)?" +
    "(\\d{1,3}([.,\\u00a0\\u202f ])\\d{3}(?:\\3\\d{3})*(?!\\3)[.,]\\d{2}|\\d+[.,]\\d{2})" +
    "(?![\\d.,:/%]|\\p{L}|\\s?[xX×*](?:\\s|$))"
)

/** Where a currency with no minor unit groups its thousands with a comma. */
private val COMMA_GROUPED = setOf("JPY", "KRW")

/**
 * `8.990`, `24.040`, `-24.040`: a whole amount in a currency without cents,
 * grouped the way that currency is written — a point in Chile, a comma in Japan.
 *
 * - At least one separator. Without one, `990` cannot be told from the `7` of
 *   an article count or a till number.
 * - No leading zero, so `0.654` stays a weight.
 * - Not followed by a dash: `76.123.456-7` is a Chilean tax ID.
 * - Not followed by a unit: `1.250 kg` is a weight wherever the comma is the
 *   decimal mark and some till prints a point anyway.
 */
private fun wholeAmount(separator: Char) = Regex(
    "(?<![\\p{L}\\d.,:/-])" +
    "([-–−~]\\s?)?" +
    "([1-9]\\d{0,2}(?:${Regex.escape(separator.toString())}\\d{3})+)" +
    "(?![\\d.,:/%\\-–−]|\\p{L}|\\s?[xX×*](?:\\s|$)|\\s?(?i:k?g|gr|lts?|l|ml|cc|un|uds?)\\b)"
)

private val WHOLE_POINT = wholeAmount('.')
private val WHOLE_COMMA = wholeAmount(',')

/**
 * Every amount in a run of text that is written like money but names no
 * currency. Zero is skipped: `0,00` is change not given, and a label over it
 * would say nothing.
 *
 * [currency] is what the amounts are taken to be in. For one with no minor
 * unit, whole amounts grouped by thousands are read as well.
 */
fun findBareAmounts(text: String, currency: String? = null): List<BareAmount> {
    val whole = when (currency) {
        !in ZERO_DECIMAL -> null
        in COMMA_GROUPED -> WHOLE_COMMA
        else -> WHOLE_POINT
    }
    val matches = BARE_AMOUNT.findAll(text) + (whole?.findAll(text).orEmpty())
    return matches.mapNotNull { match ->
        val magnitude = parseAmount(match.groupValues[2]) ?: return@mapNotNull null
        if (magnitude == 0.0) return@mapNotNull null
        val amount = if (match.groupValues[1].isNotEmpty()) -magnitude else magnitude
        BareAmount(amount, match.range)
    }.sortedBy { it.range.first }.toList()
}
