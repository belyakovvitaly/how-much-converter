// The currency calculator's arithmetic: what its keys build, and what that
// comes to. Kept off the screen so the rules can be checked without one.
package converter.core

import kotlin.math.abs
import kotlin.math.round

const val KEY_PLUS = '+'
const val KEY_MINUS = '−'
const val KEY_TIMES = '×'
const val KEY_DIVIDE = '÷'
const val KEY_POINT = '.'
const val KEY_EQUALS = '='
const val KEY_ERASE = '⌫'
const val KEY_CLEAR = 'C'

private val OPERATORS = setOf(KEY_PLUS, KEY_MINUS, KEY_TIMES, KEY_DIVIDE)

/** More digits than any price has; beyond it a key is ignored. */
private const val MAX_DIGITS = 12

/** Beyond this a result is not an amount anyone meant, and doubles lose cents. */
private const val MAX_RESULT = 1e15

/**
 * What has been typed, as the keys spelled it: digits, [KEY_POINT] and the four
 * operators. The decimal separator is always a point here; the screen shows
 * the locale's.
 *
 * [replaceOnDigit] is set when the text is a result rather than something
 * typed — after equals, or carried over from the other side — so a digit
 * starts a new number, as on any calculator, while an operator carries on from
 * the result.
 */
data class Calculation(val text: String = "", val replaceOnDigit: Boolean = false) {

    /** The amount, or null while there is none — nothing typed, or ÷ 0. */
    val value: Double? get() = evaluate(text)

    /** Whether there is arithmetic to show the result of, not just a number. */
    val hasOperator: Boolean get() = text.any { it in OPERATORS }

    fun press(key: Char): Calculation = when {
        key == KEY_CLEAR -> Calculation()
        key == KEY_ERASE -> Calculation(if (replaceOnDigit) "" else text.dropLast(1))
        key == KEY_EQUALS -> value?.let { Calculation(plainNumber(it), replaceOnDigit = true) } ?: this
        key in OPERATORS -> operator(key)
        key == KEY_POINT || key.isDigit() ->
            (if (replaceOnDigit) Calculation() else this).digit(key)
        else -> this
    }

    private fun operator(key: Char): Calculation {
        val last = text.lastOrNull() ?: return this
        // A second operator in a row replaces the first: the reader changed
        // their mind, not asked for "5 × − 3".
        val base = if (last in OPERATORS) text.dropLast(1) else text
        if (base.isEmpty() || base == "-") return Calculation(base)
        return Calculation(base + key)
    }

    private fun digit(key: Char): Calculation {
        val number = text.takeLastWhile { it.isDigit() || it == KEY_POINT }
        if (key == KEY_POINT) {
            if (KEY_POINT in number) return this
            return Calculation(text + (if (number.isEmpty()) "0." else "."))
        }
        if (number.count { it.isDigit() } >= MAX_DIGITS) return this
        // No leading zeros: "0" then "5" is 5, not 05.
        if (number == "0") return Calculation(text.dropLast(1) + key)
        return Calculation(text + key)
    }
}

/**
 * What [expression] comes to, × and ÷ before + and −.
 *
 * A trailing operator is ignored, so the answer is there while the next number
 * is still being typed. Null for nothing at all, and for a division by zero —
 * a converter that shows infinity dollars has said nothing useful.
 */
fun evaluate(expression: String): Double? {
    val numbers = mutableListOf<Double>()
    val operators = mutableListOf<Char>()
    var i = 0
    val text = expression.trimEnd { it in OPERATORS }
    if (text.isEmpty()) return null
    while (i < text.length) {
        val start = i
        // A result carried over can be negative; its sign is ASCII.
        if (text[i] == '-') i++
        while (i < text.length && (text[i].isDigit() || text[i] == KEY_POINT)) i++
        numbers += text.substring(start, i).toDoubleOrNull() ?: return null
        if (i < text.length) {
            if (text[i] !in OPERATORS) return null
            operators += text[i++]
        }
    }

    // First × and ÷, folding each into the number before it...
    val terms = mutableListOf(numbers[0])
    val signs = mutableListOf<Char>()
    for ((k, op) in operators.withIndex()) {
        val next = numbers[k + 1]
        when (op) {
            KEY_TIMES -> terms[terms.lastIndex] *= next
            KEY_DIVIDE -> {
                if (next == 0.0) return null
                terms[terms.lastIndex] /= next
            }
            else -> {
                terms += next
                signs += op
            }
        }
    }
    // ...then + and −, left to right.
    var total = terms[0]
    for ((k, op) in signs.withIndex()) {
        total = if (op == KEY_PLUS) total + terms[k + 1] else total - terms[k + 1]
    }
    return total.takeIf { it.isFinite() && abs(it) < MAX_RESULT }
}

/**
 * An amount as the keys would have typed it: a point for the decimals, no
 * grouping, no trailing zeros, at most [digits] decimals.
 */
fun plainNumber(amount: Double, digits: Int = 2): String {
    var scale = 1.0
    repeat(digits) { scale *= 10 }
    val rounded = round(amount * scale) / scale
    val whole = rounded.toLong()
    val sign = if (rounded < 0 && whole == 0L) "-" else ""
    val fraction = round(abs(rounded - whole) * scale).toLong()
    if (fraction == 0L) return "${if (rounded == 0.0) 0 else whole}"
    return "$sign$whole." + fraction.toString().padStart(digits, '0').trimEnd('0')
}

/**
 * [expression] for the screen: each number grouped in threes by [grouping],
 * with [decimal] for the point, and room around the operators.
 */
fun displayExpression(expression: String, grouping: Char, decimal: Char): String = buildString {
    var i = 0
    while (i < expression.length) {
        val c = expression[i]
        if (c in OPERATORS) {
            append(' ').append(c).append(' ')
            i++
            continue
        }
        val start = i
        if (c == '-') i++
        while (i < expression.length && (expression[i].isDigit() || expression[i] == KEY_POINT)) i++
        val number = expression.substring(start, i)
        if (number.isEmpty()) { append(c); i++; continue }
        val sign = if (number.startsWith('-')) "-" else ""
        val unsigned = number.removePrefix("-")
        val whole = unsigned.substringBefore(KEY_POINT)
        append(sign)
        for ((k, d) in whole.withIndex()) {
            if (k > 0 && (whole.length - k) % 3 == 0) append(grouping)
            append(d)
        }
        if (KEY_POINT in unsigned) append(decimal).append(unsigned.substringAfter(KEY_POINT))
    }
}
