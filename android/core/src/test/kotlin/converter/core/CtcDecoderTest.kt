package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CtcDecoderTest {

    // Index 0 is the CTC blank, as in the model's own label order.
    private val charset = listOf("<blank>", "a", "b", "c")

    /** Builds logits from one winning label per time step. */
    private fun logits(vararg best: Int): FloatArray {
        val classes = charset.size
        val out = FloatArray(best.size * classes) { 0.1f }
        best.forEachIndexed { step, label -> out[step * classes + label] = 0.9f }
        return out
    }

    @Test
    fun `repeats collapse and blanks disappear`() {
        val result = ctcDecode(logits(1, 1, 0, 1, 2, 2, 3), 7, charset.size, charset)
        // a a _ a b b c  ->  "a" "a" "b" "c"
        assertEquals("aabc", result.text)
    }

    @Test
    fun `a blank between two of the same letter keeps both`() {
        assertEquals("aa", ctcDecode(logits(1, 0, 1), 3, charset.size, charset).text)
    }

    @Test
    fun `without the blank the same two collapse into one`() {
        assertEquals("a", ctcDecode(logits(1, 1, 1), 3, charset.size, charset).text)
    }

    @Test
    fun `confidence averages the labels kept, not the blanks`() {
        // Two steps pick 'a' at 0.9, one is a blank. Averaging the blank in
        // would drag the result toward the padding rather than the text.
        val result = ctcDecode(logits(1, 0, 2), 3, charset.size, charset)
        assertEquals("ab", result.text)
        assertTrue(result.confidence > 0.85f, "confidence was ${result.confidence}")
    }

    @Test
    fun `all blanks reads as nothing, with no confidence`() {
        val result = ctcDecode(logits(0, 0, 0), 3, charset.size, charset)
        assertEquals("", result.text)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `a label past the end of the charset is ignored rather than crashing`() {
        // Guards the seam between the model and the dictionary: if they ever
        // disagree about the label count, the app should read less, not die.
        val classes = charset.size + 2
        val out = FloatArray(2 * classes) { 0.1f }
        out[0 * classes + 1] = 0.9f                  // 'a'
        out[1 * classes + classes - 1] = 0.9f        // past the charset
        assertEquals("a", ctcDecode(out, 2, classes, charset).text)
    }
}
