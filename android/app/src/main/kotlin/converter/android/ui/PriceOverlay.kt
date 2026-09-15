package converter.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import converter.core.Box
import converter.core.LocatedPrice
import converter.core.RateTable
import converter.core.Viewport
import converter.core.convert
import converter.core.formatConverted

/**
 * Draws each converted price over the price it was read from.
 *
 * The point is not decoration: a list under the viewfinder makes the reader
 * match prices to labels themselves, which is most of the work when there are
 * several on a shelf. Put the answer where the question is and there is nothing
 * to match up.
 *
 * Nothing is drawn for a price with no rate. A label that said only what was
 * already printed on the tag would be clutter, and one that guessed would be
 * worse.
 */
@Composable
fun PriceOverlay(
    prices: List<LocatedPrice>,
    imageWidth: Int,
    imageHeight: Int,
    rates: RateTable?,
    target: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0 || rates == null) return@Canvas
        val viewport = Viewport(imageWidth, imageHeight, size.width, size.height)

        for (located in prices) {
            val converted = rates.convert(located.price.amount, located.price.code, target)
                ?: continue
            drawLabel(
                measurer = measurer,
                box = viewport.map(located.box),
                text = formatConverted(converted, target),
            )
        }
    }
}

/**
 * One label, covering the price it replaces.
 *
 * The type is sized to the box, then shrunk if it would overflow, so a long
 * conversion over a short price stays inside its own background instead of
 * running across its neighbour.
 */
private fun DrawScope.drawLabel(measurer: TextMeasurer, box: Box, text: String) {
    val left = box.x0.toFloat()
    val top = box.y0.toFloat()
    val width = (box.x1 - box.x0).toFloat()
    val height = (box.y1 - box.y0).toFloat()
    if (width <= 1f || height <= 1f) return

    val padding = height * PADDING
    var fontSize = height * FONT_HEIGHT
    var measured = measure(measurer, text, fontSize)

    val room = width - padding * 2
    if (measured.first > room && measured.first > 0f) {
        fontSize *= room / measured.first
        measured = measure(measurer, text, fontSize)
    }

    drawRoundRect(
        color = LABEL_BACKGROUND,
        topLeft = Offset(left - padding, top - padding),
        size = Size(width + padding * 2, height + padding * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * 0.2f),
    )

    drawText(
        textMeasurer = measurer,
        text = text,
        topLeft = Offset(
            x = left + (width - measured.first) / 2f,
            y = top + (height - measured.second) / 2f,
        ),
        style = TextStyle(
            color = Color.White,
            fontSize = TextUnit(fontSize / density, TextUnitType.Sp),
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

/** Width and height the text would take, in pixels, at [fontSizePx]. */
private fun DrawScope.measure(
    measurer: TextMeasurer,
    text: String,
    fontSizePx: Float,
): Pair<Float, Float> {
    val result = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = TextUnit(fontSizePx / density, TextUnitType.Sp),
            fontWeight = FontWeight.SemiBold,
        ),
    )
    return result.size.width.toFloat() to result.size.height.toFloat()
}

/** Dark enough to read white type on, sheer enough to see the tag underneath. */
private val LABEL_BACKGROUND = Color(0xE6101418)
private const val PADDING = 0.12f
private const val FONT_HEIGHT = 0.72f
