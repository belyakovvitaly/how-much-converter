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

/**
 * Every amount in a run of text that is written like money but names no
 * currency. Zero is skipped: `0,00` is change not given, and a label over it
 * would say nothing.
 */
fun findBareAmounts(text: String): List<BareAmount> =
    BARE_AMOUNT.findAll(text).mapNotNull { match ->
        val magnitude = parseAmount(match.groupValues[2]) ?: return@mapNotNull null
        if (magnitude == 0.0) return@mapNotNull null
        val amount = if (match.groupValues[1].isNotEmpty()) -magnitude else magnitude
        BareAmount(amount, match.range)
    }.toList()
