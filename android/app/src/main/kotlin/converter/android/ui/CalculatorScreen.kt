package converter.android.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import converter.core.Calculation
import converter.core.KEY_CLEAR
import converter.core.KEY_DIVIDE
import converter.core.KEY_EQUALS
import converter.core.KEY_ERASE
import converter.core.KEY_MINUS
import converter.core.KEY_PLUS
import converter.core.KEY_POINT
import converter.core.KEY_TIMES
import converter.core.RateTable
import converter.core.convert
import converter.core.convertedDigits
import converter.core.displayExpression
import converter.core.flagFor
import converter.core.plainNumber
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

/**
 * Converts an amount typed by hand, in the same two currencies the photographs
 * are read in.
 *
 * Either side can be typed into: tap the other one and it becomes the one being
 * typed, starting from the amount it showed. The keys do arithmetic too, for
 * three of something or two prices added up.
 */
@Composable
fun CalculatorScreen(
    rates: RateTable?,
    source: String?,
    target: String?,
    onChangeSource: () -> Unit,
    onChangeTarget: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    var text by rememberSaveable { mutableStateOf("") }
    var replaceOnDigit by rememberSaveable { mutableStateOf(false) }
    // Which side is being typed: the prices' currency, or the reader's own.
    var typingTarget by rememberSaveable { mutableStateOf(false) }
    val calculation = Calculation(text, replaceOnDigit)

    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val typedCode = if (typingTarget) target else source
    val otherCode = if (typingTarget) source else target
    val typed = calculation.value
    val other = if (typed != null && rates != null && typedCode != null && otherCode != null) {
        rates.convert(typed, typedCode, otherCode)
    } else null

    fun press(key: Char) {
        val next = calculation.press(key)
        text = next.text
        replaceOnDigit = next.replaceOnDigit
    }

    // The other side becomes the one typed, starting from what it showed.
    fun switchSides() {
        val code = otherCode
        text = if (other != null && code != null) plainNumber(other, convertedDigits(other, code)) else ""
        replaceOnDigit = true
        typingTarget = !typingTarget
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onClose)
            Text(
                text = "Calculator",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        val typedText = displayExpression(calculation.text, symbols.groupingSeparator, symbols.decimalSeparator)
            .ifEmpty { "0" }
        val otherText = when {
            other != null && otherCode != null -> formatAmount(other, otherCode)
            typed == null -> "0"
            else -> "—"
        }
        val result = if (calculation.hasOperator && typed != null && typedCode != null) {
            "= " + formatAmount(typed, typedCode)
        } else null

        AmountRow(
            code = source,
            amount = if (typingTarget) otherText else typedText,
            result = if (typingTarget) null else result,
            active = !typingTarget,
            onPickCurrency = onChangeSource,
            onClick = { if (typingTarget) switchSides() },
        )
        AmountRow(
            code = target,
            amount = if (typingTarget) typedText else otherText,
            result = if (typingTarget) result else null,
            active = typingTarget,
            onPickCurrency = onChangeTarget,
            onClick = { if (!typingTarget) switchSides() },
        )

        RateLine(rates, source, target)

        Keypad(decimal = symbols.decimalSeparator, onKey = ::press)
    }
}

/** One currency and its amount; the one being typed stands out. */
@Composable
private fun AmountRow(
    code: String?,
    amount: String,
    result: String?,
    active: Boolean,
    onPickCurrency: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0xFF1E2A3A) else Color(0xFF141414))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The code opens the picker, the rest of the row picks the side.
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickCurrency)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            code?.let(::flagFor)?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = code ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            Text(
                text = amount,
                color = if (active) Color.White else Color(0xFFBDBDBD),
                // A long number steps down a size rather than running off.
                style = if (amount.length > 12) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            if (result != null) {
                Text(
                    text = result,
                    color = Color(0xFF9E9E9E),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * What one unit is worth, and how old the rates are — or why there is nothing
 * to convert with.
 */
@Composable
private fun RateLine(rates: RateTable?, source: String?, target: String?) {
    val text = when {
        source == null -> "Choose what the prices are in"
        target == null -> "Choose what to convert into"
        rates == null -> "No rates yet"
        else -> {
            // Whichever way round gives a number above one: "1 USD = 1 395 ARS"
            // says more than "1 ARS = 0.00072 USD".
            val oneTarget = rates.convert(1.0, target, source)
            val oneSource = rates.convert(1.0, source, target)
            val rate = when {
                oneTarget != null && oneTarget >= 1 -> "1 $target = ${formatAmount(oneTarget, source)} $source"
                oneSource != null -> "1 $source = ${formatAmount(oneSource, target)} $target"
                else -> null
            }
            val age = DateUtils.getRelativeTimeSpanString(
                rates.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            )
            if (rate == null) "No rate for this pair" else "$rate · updated $age"
        }
    }
    Text(
        text = text,
        color = if (rates == null || source == null || target == null) Color(0xFFFFB74D) else Color(0xFF9E9E9E),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun Keypad(decimal: Char, onKey: (Char) -> Unit) {
    // Each row as keys and how many columns each spans; four columns in all.
    val rows = listOf(
        listOf(KEY_CLEAR to 2, KEY_ERASE to 1, KEY_DIVIDE to 1),
        listOf('7' to 1, '8' to 1, '9' to 1, KEY_TIMES to 1),
        listOf('4' to 1, '5' to 1, '6' to 1, KEY_MINUS to 1),
        listOf('1' to 1, '2' to 1, '3' to 1, KEY_PLUS to 1),
        listOf('0' to 2, KEY_POINT to 1, KEY_EQUALS to 1),
    )
    val gap = 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        // Widths worked out rather than weighted: a key two columns wide
        // spans the gap between them too, which weights alone cannot say.
        val column = (maxWidth - gap * 3) / 4
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for ((key, span) in row) {
                        Key(key, decimal, onKey, width = column * span + gap * (span - 1))
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(key: Char, decimal: Char, onKey: (Char) -> Unit, width: Dp) {
    val label = when (key) {
        KEY_POINT -> decimal.toString()
        else -> key.toString()
    }
    val background = when (key) {
        KEY_EQUALS -> Color(0xFF3D6FB6)
        KEY_PLUS, KEY_MINUS, KEY_TIMES, KEY_DIVIDE -> Color(0xFF2B3440)
        KEY_CLEAR, KEY_ERASE -> Color(0xFF333333)
        else -> Color(0xFF1F1F1F)
    }
    Box(
        Modifier
            .width(width)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable { onKey(key) }
            .semantics {
                contentDescription = when (key) {
                    KEY_ERASE -> "Erase"
                    KEY_CLEAR -> "Clear"
                    else -> label
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

/** An amount in the locale's own grouping, to the precision the currency has. */
private fun formatAmount(amount: Double, code: String): String {
    val digits = convertedDigits(amount, code)
    return NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(amount)
}
