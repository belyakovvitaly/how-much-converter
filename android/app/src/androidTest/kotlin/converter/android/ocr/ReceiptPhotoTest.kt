package converter.android.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.PriceContext
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
