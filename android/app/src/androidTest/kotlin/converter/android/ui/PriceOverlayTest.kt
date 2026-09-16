package converter.android.ui

import androidx.compose.foundation.layout.Box as LayoutBox
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import converter.core.Box
import converter.core.LocatedPrice
import converter.core.Price
import converter.core.RateTable
import converter.core.Viewport
import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Does the label land on the price?
 *
 * The overlay's arithmetic is the one part of this app whose mistakes are
 * invisible to every other test: a label fifty pixels off still draws, still
 * says the right number, and points at the wrong thing. So this renders it over
 * a frame of known size and looks at the pixels.
 */
class PriceOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private val rates = RateTable(
        base = "USD",
        rates = mapOf("USD" to 1.0, "RUB" to 92.0, "EUR" to 0.92),
        fetchedAt = 0,
    )

    // A price in the upper left quarter of a 1000x2000 frame.
    private val imageWidth = 1000
    private val imageHeight = 2000
    private val priceBox = Box(100.0, 200.0, 400.0, 300.0)

    /**
     * How dark a pixel is. The capture comes back opaque — the test activity
     * paints its own light background behind the overlay — so alpha says
     * nothing and the label has to be told apart by its colour.
     */
    private fun ComposeColor.darkness(): Float = 1f - (red * 0.299f + green * 0.587f + blue * 0.114f)

    private fun render() {
        compose.setContent {
            LayoutBox(Modifier.fillMaxSize()) {
                PriceOverlay(
                    prices = listOf(LocatedPrice(Price(1299.0, "RUB"), priceBox)),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rates = rates,
                    target = "EUR",
                )
            }
        }
    }

    @Test
    fun drawsTheLabelWhereThePriceIs() {
        render()
        val pixels = compose.onRoot().captureToImage().toPixelMap()

        val viewport = Viewport(
            imageWidth, imageHeight,
            pixels.width.toFloat(), pixels.height.toFloat(),
        )
        val mapped = viewport.map(priceBox)

        // Sampled across the box rather than at its centre: the centre is where
        // the white type sits, and a single pixel there says the label is
        // missing when it is in fact perfectly placed.
        var dark = 0
        var light = 0
        for (y in mapped.y0.toInt()..mapped.y1.toInt() step 4) {
            for (x in mapped.x0.toInt()..mapped.x1.toInt() step 4) {
                if (pixels[x, y].darkness() > 0.5f) dark++ else light++
            }
        }

        assertTrue(
            "the price at $mapped is mostly not covered: $dark dark, $light light",
            dark > light,
        )
        assertTrue(
            "the label has no text on it: every sampled pixel was dark",
            light > 0,
        )
    }

    @Test
    fun leavesTheRestOfTheFrameAlone() {
        render()
        val pixels = compose.onRoot().captureToImage().toPixelMap()

        // Diagonally opposite the price, well outside its box.
        val farX = (pixels.width * 0.9f).toInt()
        val farY = (pixels.height * 0.9f).toInt()

        val away = pixels[farX, farY]
        assertTrue(
            "the overlay painted where there is no price, at ($farX, $farY): $away",
            away.darkness() < 0.2f,
        )
    }

    @Test
    fun drawsNothingWithoutARate() {
        // A label repeating what the tag already says would be clutter; one
        // guessing at a rate would be worse.
        compose.setContent {
            LayoutBox(Modifier.fillMaxSize()) {
                PriceOverlay(
                    prices = listOf(LocatedPrice(Price(1299.0, "XYZ"), priceBox)),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rates = rates,
                    target = "EUR",
                )
            }
        }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val viewport = Viewport(
            imageWidth, imageHeight,
            pixels.width.toFloat(), pixels.height.toFloat(),
        )
        val mapped = viewport.map(priceBox)
        val centre = pixels[
            ((mapped.x0 + mapped.x1) / 2).toInt(),
            ((mapped.y0 + mapped.y1) / 2).toInt(),
        ]
        assertTrue(
            "a label was drawn for a currency with no rate: $centre",
            centre.darkness() < 0.2f,
        )
    }
}
