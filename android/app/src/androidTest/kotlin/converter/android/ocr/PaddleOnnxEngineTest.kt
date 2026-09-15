package converter.android.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import converter.core.Price
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
