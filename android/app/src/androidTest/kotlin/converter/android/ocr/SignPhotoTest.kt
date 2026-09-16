package converter.android.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.Price
import converter.core.PriceContext
import converter.core.locatePrices
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A street sign in Buenos Aires: six printed tags, and a price chalked by hand
 * below them, white on black, its "$" standing apart from the digits.
 *
 * The detector finds nothing of that "$" but a speck, so the line was read as
 * a bare "5600×Kg" and rightly left alone. A second look with room to the left
 * finds the symbol. The photo is as a messenger delivered it, 960 pixels wide.
 */
@RunWith(AndroidJUnit4::class)
class SignPhotoTest {

    @Test
    fun readsTheChalkedPriceAsWellAsThePrintedOnes() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("regression/sign-chalk.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val lines = engine.recognize(bitmap, thorough = true)
        bitmap.recycle()
        val prices = locatePrices(lines, PriceContext(pageCurrency = "ARS")).map { it.price }
        Log.i(TAG, "lines: ${lines.map { it.text }} -> $prices")

        assertEquals(
            "lines: ${lines.map { it.text }}",
            listOf(16800.0, 30400.0, 28000.0, 29200.0, 21300.0, 7600.0, 5600.0)
                .map { Price(it, "ARS") }.sortedBy { it.amount },
            prices.sortedBy { it.amount },
        )
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
