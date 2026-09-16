package converter.android.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.Price
import converter.core.PriceContext
import converter.core.RateTable
import converter.core.convert
import converter.core.formatConverted
import converter.core.findPrices
import converter.core.readPrices
import org.json.JSONArray
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real engine, on a real device, over the benchmark's own corpus.
 *
 * `:core` tests prove the price rules against recorded engine output; this
 * proves the engine that produces it. Same twelve images, same twenty-six
 * prices, same rule that a wrong price counts against you separately from a
 * missed one.
 *
 * The numbers to beat were set in tools/ocr-bench: PaddleOCR reads 25 of the 26
 * and invents none. Anything less here means the Android port lost something
 * the desktop pipeline had.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOnnxEngineTest {

    private data class Case(val id: String, val expected: List<Price>)

    private fun cases(): List<Case> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val text = context.assets.open("bench/truth.json").use {
            it.bufferedReader().readText()
        }
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            val spellings = entry.getJSONArray("truth")
            val expected = (0 until spellings.length()).mapNotNull { i ->
                findPrices(spellings.getString(i)).firstOrNull()
            }
            Case(entry.getString("id"), expected)
        }
    }

    @Test
    fun readsTheBenchmarkCorpusAsWellAsTheDesktopPipeline() {
        val context = InstrumentationRegistry.getInstrumentation().context
        var hits = 0
        var misses = 0
        var wrong = 0
        val report = StringBuilder()

        for (case in cases()) {
            val bitmap = context.assets.open("bench/${case.id}.png").use {
                BitmapFactory.decodeStream(it)
            }
            requireNotNull(bitmap) { "could not decode ${case.id}.png" }

            // Consecutive fixtures are unrelated images, not a continuation of
            // one another: carrying a reading from one to the next would be
            // reading text off the wrong picture.
            engine.reset()

            val started = System.currentTimeMillis()
            val lines = engine.recognize(bitmap)
            val elapsed = System.currentTimeMillis() - started
            bitmap.recycle()

            val found = readPrices(lines).toMutableList()
            val missed = mutableListOf<Price>()
            for (price in case.expected) {
                if (found.remove(price)) hits++ else { misses++; missed += price }
            }
            wrong += found.size

            report.append("${case.id}  [${elapsed} ms]")
            if (missed.isNotEmpty()) report.append("  missed $missed")
            if (found.isNotEmpty()) report.append("  WRONG $found")
            report.append('\n')
        }

        // Log rather than println: the instrumentation runner drops stdout,
        // and this detail is the whole point of running on a device.
        report.lines().filter { it.isNotBlank() }.forEach { Log.i(TAG, it) }

        assertEquals("the engine invented a price:\n$report", 0, wrong)
        assertTrue(
            "read $hits of 26; the desktop pipeline reads 25\n$report",
            hits >= 25,
        )
        assertEquals("unexpected miss count\n$report", 1, misses)
    }

    @Test
    fun convertsAPriceItReadOffAPhoto() {
        // The whole chain in one place: pixels to a recognized line, a line to
        // an amount and a currency, and that pair to what a user would read.
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("bench/01-price-tag.png").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val prices = readPrices(engine.recognize(bitmap))
        bitmap.recycle()

        val price = prices.single()
        assertEquals("RUB", price.code)
        assertEquals(1299.0, price.amount, 0.001)

        // A fixed table, so the test says nothing about the network: 92 roubles
        // and 0.92 euro to the dollar makes 1299 roubles 12.99 euro.
        val rates = RateTable(
            base = "USD",
            rates = mapOf("USD" to 1.0, "RUB" to 92.0, "EUR" to 0.92),
            fetchedAt = 0,
        )
        val converted = rates.convert(price.amount, price.code, "EUR")
        assertEquals("12.99 EUR", formatConverted(converted!!, "EUR"))
    }

    @Test
    fun skipsTheRecognizerForBoxesThatHaveNotMoved() {
        // A still scene is the common case, and re-reading every box in it is
        // the bulk of the work. The second pass over an identical frame should
        // reuse every box rather than run the recognizer again.
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("bench/02-shop-list.png").use {
            BitmapFactory.decodeStream(it)
        }

        engine.reset()
        val firstStart = System.currentTimeMillis()
        val first = engine.recognize(bitmap)
        val firstMillis = System.currentTimeMillis() - firstStart

        val secondStart = System.currentTimeMillis()
        val second = engine.recognize(bitmap)
        val secondMillis = System.currentTimeMillis() - secondStart
        bitmap.recycle()

        Log.i(TAG, "same frame twice: $firstMillis ms then $secondMillis ms")

        assertTrue("nothing was read at all", first.isNotEmpty())
        assertTrue("the first pass should read, not reuse", first.none { it.reused })
        assertTrue("the second pass should reuse: $second", second.all { it.reused })
        assertEquals(
            "reuse changed what was read",
            first.map { it.text },
            second.map { it.text },
        )
        assertTrue(
            "reuse saved nothing: $firstMillis ms then $secondMillis ms",
            secondMillis < firstMillis,
        )
    }

    @Test
    fun doesNotSwallowACurrencyGlyphIntoTheNumber() {
        // The one failure that produces a confidently wrong price rather than
        // no price. "₴1 200,50 грн" has a prefix glyph the recognizer can read
        // as a digit; when it does, the amount comes back seventeen times too
        // large with its currency still attached, so nothing downstream can
        // refuse it. Crops carry side margin to prevent exactly this.
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("regression/prefix-glyph.png").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val lines = engine.recognize(bitmap)
        val prices = readPrices(lines)
        bitmap.recycle()

        Log.i(TAG, "prefix glyph fixture read as: ${lines.map { it.text }} -> $prices")

        val inflated = prices.filter { it.amount > 20_000 }
        assertTrue(
            "the glyph was read as a digit and fused into the amount: $inflated " +
                "(lines: ${lines.map { it.text }})",
            inflated.isEmpty(),
        )
        assertTrue(
            "expected 1200.50 UAH among $prices",
            prices.any { it.code == "UAH" && kotlin.math.abs(it.amount - 1200.5) < 0.01 },
        )
    }

    @Test
    fun readsASidewaysFrameNowThatBoxesFollowTheText() {
        // This test used to assert the opposite, and was right to: before boxes
        // were fitted to the direction the text runs, a frame rotated ninety
        // degrees read as nothing, and the app shipped once doing exactly that.
        // Fitting the angle fixed the general case and this one with it.
        //
        // The camera still turns its frames upright, for a different reason:
        // the overlay draws labels in the frame's coordinates, and in a
        // sideways frame they would be drawn sideways too.
        val context = InstrumentationRegistry.getInstrumentation().context
        val upright = context.assets.open("bench/01-price-tag.png").use {
            BitmapFactory.decodeStream(it)
        }
        val sideways = Bitmap.createBitmap(
            upright, 0, 0, upright.width, upright.height,
            Matrix().apply { postRotate(90f) }, true,
        )

        engine.reset()
        val fromUpright = readPrices(engine.recognize(upright))
        engine.reset()
        val fromSideways = readPrices(engine.recognize(sideways))
        upright.recycle()
        sideways.recycle()

        Log.i(TAG, "upright: $fromUpright   sideways: $fromSideways")

        assertEquals(listOf(Price(1299.0, "RUB")), fromUpright)
        assertEquals("a sideways frame should read the same", fromUpright, fromSideways)
    }

    @Test
    fun readsAWholeShelfWithinABudget() {
        // A whole shelf is the case that was slow on a phone: several tags at
        // once, each surrounded by fine print that costs a recognizer run
        // apiece and could never be a price. Reading is capped per frame and
        // spends itself on the tallest text first, so the question is not only
        // whether a frame is quick but whether the prices still all arrive —
        // over a few frames, as the camera would give them.
        val context = InstrumentationRegistry.getInstrumentation().context
        for (case in listOf("close", "shelf", "far")) {
            val bitmap = context.assets.open("regression/$case.png").use {
                BitmapFactory.decodeStream(it)
            }
            engine.reset()

            val seen = mutableSetOf<Price>()
            for (pass in 1..4) {
                val started = System.currentTimeMillis()
                val lines = engine.recognize(bitmap)
                val total = System.currentTimeMillis() - started
                // Argentine tags: "$" there is a peso, and without a country
                // the rule refuses the symbol outright — correctly, which is
                // why the app feeds it one.
                val prices = readPrices(lines, PriceContext(pageCurrency = "ARS"))
                seen += prices
                val t = engine.timings
                Log.i(
                    TAG,
                    "$case frame $pass: ${total} ms = detect ${t.detectMillis} " +
                        "+ boxes ${t.postProcessMillis} + read ${t.recogniseMillis}; " +
                        "${t.boxes} boxes, ${t.recognised} read, ${t.reused} reused, " +
                        "${t.skipped} skipped, ${prices.size} prices, ${seen.size} seen so far",
                )
            }
            bitmap.recycle()

            assertTrue("$case found no prices at all", seen.isNotEmpty())
        }
    }

    @Test
    fun readsATagThatIsNotSquareToTheCamera() {
        // A shelf is usually seen from the side. Before the crop was turned
        // upright this did not merely fail — it produced a plausible wrong
        // number, which is the one outcome this project refuses: at eight
        // degrees "$ 3.648,75" read as 1648, and obliquely as 38648.
        val context = InstrumentationRegistry.getInstrumentation().context
        val wanted = Price(3648.75, "ARS")

        for (case in listOf("flat", "tilt-8", "tilt-15", "tilt-25")) {
            val bitmap = context.assets.open("regression/$case.png").use {
                BitmapFactory.decodeStream(it)
            }
            engine.reset()
            val lines = engine.recognize(bitmap)
            val prices = readPrices(lines, PriceContext(pageCurrency = "ARS"))
            bitmap.recycle()

            Log.i(TAG, "$case: ${lines.map { it.text }} -> $prices")

            val wrong = prices.filter { it != wanted }
            assertTrue("$case invented $wrong from ${lines.map { it.text }}", wrong.isEmpty())
            assertTrue("$case read no price at all: ${lines.map { it.text }}", wanted in prices)
        }
    }

    @Test
    fun severePerspectiveIsStillMisread() {
        // The limit of fitting one angle to a region: a tag seen at thirty
        // degrees is not merely rotated, it is foreshortened, and no single
        // rotation undoes that. Rotating still helps — the same fixture used to
        // read "$ 3.648,75" as 38648 and now reads 3.64 — but it is still wrong,
        // and this records that rather than pretending otherwise.
        //
        // Undoing it properly needs the region's quadrilateral and a
        // perspective warp, which connected components do not give.
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("regression/oblique.png").use {
            BitmapFactory.decodeStream(it)
        }
        engine.reset()
        val prices = readPrices(engine.recognize(bitmap), PriceContext(pageCurrency = "ARS"))
        bitmap.recycle()

        Log.i(TAG, "oblique (known limit): $prices")
        assertTrue(
            "oblique now reads correctly — the limit has moved, update this test",
            Price(3648.75, "ARS") !in prices,
        )
    }

    companion object {
        private const val TAG = "HowMuchBench"
        private lateinit var engine: PaddleOnnxEngine

        @BeforeClass
        @JvmStatic
        fun loadModels() {
            // The models live in the app's own assets, not the test's.
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
