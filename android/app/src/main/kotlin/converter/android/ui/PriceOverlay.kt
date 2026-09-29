package converter.android.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import converter.core.labelAreas

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
    /** Null when there is nothing to convert into; then nothing is drawn. */
    target: String?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxSize()) {
        drawConversions(measurer, prices, imageWidth, imageHeight, rates, target)
    }
}

/**
 * The picture with its labels drawn into it, at the picture's own size — what
 * the screen shows, as a file.
 *
 * The same drawing as [PriceOverlay], onto a bitmap instead of a view, so a
 * saved picture cannot come out labelled differently from the one on screen.
 * The scale is one pixel to a pixel, and so is the type's.
 */
fun renderConversions(
    context: Context,
    image: Bitmap,
    prices: List<LocatedPrice>,
    rates: RateTable?,
    target: String?,
): Bitmap {
    val out = image.copy(Bitmap.Config.ARGB_8888, true)
    val density = Density(1f, 1f)
    val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(context),
        defaultDensity = density,
        defaultLayoutDirection = LayoutDirection.Ltr,
    )
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(out.asImageBitmap()),
        size = Size(out.width.toFloat(), out.height.toFloat()),
    ) {
        drawConversions(measurer, prices, out.width, out.height, rates, target)
    }
    return out
}

/** Every label, mapped from the picture's pixels onto whatever is drawn on. */
private fun DrawScope.drawConversions(
    measurer: TextMeasurer,
    prices: List<LocatedPrice>,
    imageWidth: Int,
    imageHeight: Int,
    rates: RateTable?,
    target: String?,
) {
    if (imageWidth <= 0 || imageHeight <= 0 || rates == null || target == null) return
    val viewport = Viewport(imageWidth, imageHeight, size.width, size.height)

    val labels = prices.mapNotNull { located ->
        // Already in the target currency: a label would repeat the tag.
        if (located.price.code == target) return@mapNotNull null
        val converted = rates.convert(located.price.amount, located.price.code, target)
            ?: return@mapNotNull null
        located.box to formatConverted(converted, target)
    }
    // Laid out together, so neighbours on a receipt do not cover each other;
    // in the picture's pixels, since the mapping onto the view only scales.
    val areas = labelAreas(labels.map { it.first })
    for ((area, label) in areas.zip(labels)) {
        drawLabel(measurer = measurer, area = viewport.map(area), text = label.second)
    }
}

/**
 * One label, filling the [area] laid out for it — its price and a margin, or
 * less where a neighbour needed the room; see [labelAreas].
 *
 * The type is sized to the area, then shrunk if it would overflow, so a long
 * conversion over a short price stays inside its own background instead of
 * running across its neighbour.
 */
private fun DrawScope.drawLabel(measurer: TextMeasurer, area: Box, text: String) {
    val left = area.x0.toFloat()
    val top = area.y0.toFloat()
    val width = (area.x1 - area.x0).toFloat()
    val height = (area.y1 - area.y0).toFloat()
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
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * CORNER),
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
// As shares of the whole label's height. A label with no neighbours is its box
// and a margin of LABEL_MARGIN each side, so these draw it as it always was:
// type at 0.72 of the box's height, padding 0.12, corners 0.2.
private const val PADDING = 0.1f
private const val FONT_HEIGHT = 0.58f
private const val CORNER = 0.16f
