// Finding a price in a run of text, and reading what it is worth.
//
// A port of buildPriceRegExp, resolveSymbol and parseAmount from
// extension/src/currency.js. The tables the three of them stand on are
// generated (CurrencyTables.kt), but this logic is written out, so it is held
// to the JavaScript by tests rather than by construction.
package converter.core

/** An amount and the currency it is in. */
data class Price(val amount: Double, val code: String)

/**
 * What only the surroundings know. On a web page that is the page's own
 * currency and the user's "$" setting; on a phone it will be the locale, where
 * the photo was taken, or an explicit choice — the same question, asked of a
 * different context.
 */
data class PriceContext(
    val pageCurrency: String? = null,
    val dollarAssumption: String = "auto",
    val isKnownCode: (String) -> String? = ::defaultKnownCode,
)

private val CURRENCY_CODE_SET = CURRENCY_CODES.toSet()

/** Reads a written-out ISO code, in any case, if it is one we know. */
fun defaultKnownCode(token: String): String? =
    token.uppercase().takeIf { it in CURRENCY_CODE_SET }

private val SPACED = Regex(SPACED_SCRIPT)

/** The characters a regex gives meaning to, escaped one at a time — the same
 *  set currency.js escapes, so the two build the same alternation. */
private val NEEDS_ESCAPE = Regex("""[.*+?^${'$'}{}()|\[\]\\]""")

private fun escapeLiteral(token: String) =
    NEEDS_ESCAPE.replace(token) { "\\" + it.value }

/**
 * `<currency><number>` or `<number><currency>`, anywhere in a run of text.
 *
 * Tokens written in a script that spaces its words are fenced by letters, or
 * "руб" matches inside "рубанок"; tokens in a script that does not are left
 * unfenced, because the character after them is usually another letter.
 */
fun buildPriceRegex(extraTokens: Collection<String> = emptyList()): Regex {
    fun pattern(token: String) = TOKEN_PATTERNS[token] ?: escapeLiteral(token)

    val symbols = (SYMBOL_TO_CODE.keys + extraTokens).sortedByDescending { it.length }
    val fenced = symbols.filter { SPACED.containsMatchIn(it) }.map(::pattern)
    val bare = symbols.filterNot { SPACED.containsMatchIn(it) }.map(::pattern)

    // Spelled out rather than matching any three capitals: a stray "UVP" or
    // "SKU" would otherwise match and swallow the number after it. Most codes
    // are taken in any case; the ones that are also ordinary words hold out
    // for capitals.
    val codes = CURRENCY_CODES.joinToString("|") { code ->
        if (code in CODES_NEEDING_CAPITALS) code
        else code.map { ch -> "[$ch${ch.lowercaseChar()}]" }.joinToString("")
    }

    val currency =
        "(?:(?<!\\p{L})(?:${fenced.joinToString("|")}|$codes)(?!\\p{L})" +
        "|(?:${bare.joinToString("|")}))"

    return Regex("($currency)\\s?($NUMBER)|($NUMBER)\\s?($currency)")
}

/** Built once: the alternation runs to thousands of characters. */
val PRICE_REGEX: Regex by lazy { buildPriceRegex() }

/**
 * Which currency a matched token means, or null when it cannot be pinned down
 * — in which case the caller leaves the price alone rather than guessing.
 */
fun resolveSymbol(token: String?, context: PriceContext): String? {
    if (token.isNullOrEmpty()) return null

    val fromSymbol = SYMBOL_TO_CODE[token]
    val code = fromSymbol ?: context.isKnownCode(token) ?: return null

    val options = (if (fromSymbol != null) AMBIGUOUS_SYMBOLS[token]
                   else AMBIGUOUS_CODES[code]) ?: return code

    // The "$" setting is a manual override; on "auto" it defers to context
    // like every other ambiguous symbol.
    if (token == "$" && context.dollarAssumption != "auto") {
        return context.dollarAssumption
    }
    return if (context.pageCurrency in options) context.pageCurrency else null
}

/**
 * Turns a localized number into a Double, guessing the decimal separator from
 * context. Returns null when it cannot be read.
 */
fun parseAmount(raw: String): Double? {
    // The dash standing in for the minor unit carries no value: "1.449,–" is 1449.
    var s = raw.replace(Regex("[\\u00a0\\u202f\\s'’]"), "")
    s = s.replace(Regex("[.,][–-]$"), "")

    val hasComma = s.contains(',')
    val hasDot = s.contains('.')

    if (hasComma && hasDot) {
        // Whichever separator comes last is the decimal one.
        s = if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
            s.replace(".", "").replaceFirst(",", ".")
        } else {
            s.replace(",", "")
        }
    } else if (hasComma) {
        val parts = s.split(",")
        // "12,34" -> decimal; "1,234" or "1,234,567" -> thousands
        s = if (parts.size == 2 && parts[1].length <= 2) parts[0] + "." + parts[1]
            else parts.joinToString("")
    } else if (hasDot) {
        val parts = s.split(".")
        if (parts.size > 2) {
            s = parts.joinToString("")           // "1.234.567" -> thousands
        } else if (parts.size == 2 && parts[1].length == 3) {
            s = parts.joinToString("")           // "1.234" -> trailing triple
        }
        // otherwise keep as a plain decimal
    }

    return s.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Every price in a run of text. */
fun findPrices(
    text: String,
    context: PriceContext = PriceContext(),
    regex: Regex = PRICE_REGEX,
): List<Price> = regex.findAll(text).mapNotNull { match ->
    val groups = match.groupValues
    val token = groups[1].ifEmpty { groups[4] }
    val number = groups[2].ifEmpty { groups[3] }
    val code = resolveSymbol(token, context) ?: return@mapNotNull null
    val amount = parseAmount(number) ?: return@mapNotNull null
    Price(amount, code)
}.toList()
