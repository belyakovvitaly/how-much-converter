package converter.android.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.Box
import converter.core.PriceContext
import converter.core.labelArea
import converter.core.labelAreas
import converter.core.locatePrices
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A photograph of a real supermarket receipt, read in receipt mode.
 *
 * Every other fixture is rendered by Chrome. This one is thermal paper, curled,
 * held in a hand under a lamp, at the size the app reads a gallery picture at.
 * The card's last digits are blanked out; nothing here depends on them.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptPhotoTest {

    @Test
    fun readsTheAmountsAndNothingElse() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("regression/receipt-photo.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val lines = engine.recognize(bitmap, thorough = true)
        bitmap.recycle()
        val amounts = locatePrices(lines, PriceContext(bareAmounts = "ARS"))
            .map { it.price.amount }

        Log.i(TAG, "lines: ${lines.map { it.text }}")
        Log.i(TAG, "amounts: $amounts")

        // Everything printed on it as money, discounts negative. The total is
        // printed twice.
        val printed = listOf(
            14000.0, 32648.0, -9794.40,
            4000.0, 23216.0, -6964.80,
            3118.0, 18708.0, -5612.40,
            74572.0, -22371.60, 52200.40, 52200.40,
        )

        // A number not on that list is an invented price, and a discount read
        // without its minus is one too.
        val invented = amounts.filter { a -> printed.none { kotlin.math.abs(it - a) < 0.005 } }
        assertTrue("read amounts that are not on the receipt: $invented\n$lines", invented.isEmpty())

        assertTrue(
            "read ${amounts.size} of the ${printed.size} amounts: $amounts",
            amounts.size >= printed.size - 1,
        )
    }

    /**
     * The receipt's lines sit a line apart, and near the subtotal the amounts'
     * boxes overlap outright: drawn as they came, the labels piled up and hid
     * each other. Laid out, no two overlap, and each is still on its price.
     */
    @Test
    fun labelsOnTheReceiptDoNotCoverEachOther() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("regression/receipt-photo.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val lines = engine.recognize(bitmap, thorough = true)
        bitmap.recycle()
        val boxes = locatePrices(lines, PriceContext(bareAmounts = "ARS")).map { it.box }
        Log.i(TAG, "boxes: $boxes")

        fun overlaps(areas: List<Box>) = areas.indices.flatMap { i ->
            (i + 1 until areas.size).filter { j ->
                val a = areas[i]
                val b = areas[j]
                minOf(a.x1, b.x1) > maxOf(a.x0, b.x0) && minOf(a.y1, b.y1) > maxOf(a.y0, b.y0)
            }.map { j -> i to j }
        }

        // The case this is about: without the layout, labels do overlap.
        val unplaced = overlaps(boxes.map { it.labelArea() })
        Log.i(TAG, "overlapping before layout: $unplaced")
        assertTrue("the fixture no longer has neighbouring labels to separate", unplaced.isNotEmpty())

        val areas = labelAreas(boxes)
        assertTrue("labels still overlap: ${overlaps(areas)}", overlaps(areas).isEmpty())
        for ((box, area) in boxes.zip(areas)) {
            val x = (box.x0 + box.x1) / 2
            val y = (box.y0 + box.y1) / 2
            assertTrue(
                "a label moved off its price: $box -> $area",
                x in area.x0..area.x1 && y in area.y0..area.y1,
            )
        }
    }

    companion object {
        private const val TAG = "HowMuchReceipt"
        private lateinit var engine: PaddleOnnxEngine

        @BeforeClass
        @JvmStatic
        fun loadModels() {
            engine = PaddleOnnxEngine.create(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        }

        @AfterClass
        @JvmStatic
        fun releaseModels() {
            engine.close()
        }
    }
}
