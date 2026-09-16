package converter.android.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.Price
import converter.core.PriceContext
import converter.core.locatePrices
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A street sign in Buenos Aires: six printed tags, and a price chalked by hand
 * below them, white on black, its "$" standing apart from the digits.
 *
 * The detector finds nothing of that "$" but a speck, so the line was read as
 * a bare "5600×Kg" and rightly left alone. A second look with room to the left
 * finds the symbol — and at other sizes of the same photo, the scrap is read
 * as a digit or two, which the second look has to see past. The photo is as a
 * messenger delivered it, 960 pixels wide.
 */
@RunWith(AndroidJUnit4::class)
class SignPhotoTest {

    @Test
    fun readsTheChalkedPriceAsWellAsThePrintedOnes() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val source = context.assets.open("regression/sign-chalk.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        val expected = listOf(16800.0, 30400.0, 28000.0, 29200.0, 21300.0, 7600.0, 5600.0)
            .map { Price(it, "ARS") }.sortedBy { it.amount }

        // At more than one size: the first fix for this read the symbol at the
        // size the test had and not at the size a phone's own photo is read
        // at, where the scrap came back as a digit and blocked the second look.
        for (scale in listOf(1.0, 1.5625, 2.0, 2.5)) {
            val bitmap = Bitmap.createScaledBitmap(
                source, (source.width * scale).toInt(), (source.height * scale).toInt(), true,
            )
            engine.reset()
            val lines = engine.recognize(bitmap, thorough = true)
            // At 1.0 the scaled bitmap is the source itself.
            if (bitmap !== source) bitmap.recycle()
            val prices = locatePrices(lines, PriceContext(pageCurrency = "ARS")).map { it.price }
            Log.i(TAG, "x$scale: ${lines.map { it.text }} -> $prices")

            if (scale == 1.0) {
                assertEquals("lines: ${lines.map { it.text }}", expected, prices.sortedBy { it.amount })
            } else {
                // An enlarged copy of a messenger's JPEG is not a sharp photo,
                // and at 1.5625 the recognizer drops a digit from the printed
                // "$16.800" whatever this does — a failure of its own, recorded
                // in the README. What this checks at other sizes is the chalk.
                assertTrue(
                    "at x$scale the chalked price was missed: ${lines.map { it.text }}",
                    Price(5600.0, "ARS") in prices,
                )
            }
        }
        source.recycle()
    }

    companion object {
        private const val TAG = "HowMuchSign"
        private lateinit var engine: PaddleOnnxEngine

        @BeforeClass
        @JvmStatic
        fun loadModels() {
            engine = PaddleOnnxEngine.create(InstrumentationRegistry.getInstrumentation().targetContext)
        }

        @AfterClass
        @JvmStatic
        fun releaseModels() {
            engine.close()
        }
    }
}
