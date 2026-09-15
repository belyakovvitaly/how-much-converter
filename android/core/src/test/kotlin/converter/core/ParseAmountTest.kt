package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The cases that pin parseAmount to its JavaScript original. These are the same
 * ones PARSE_CHECKS holds in tools/ocr-bench/bench.py — three ports of one
 * function, kept honest by one list.
 */
class ParseAmountTest {

    @Test
    fun `reads the separators shops actually use`() {
        assertEquals(1234.56, parseAmount("1 234,56"))
        assertEquals(1234.56, parseAmount("1,234.56"))
        assertEquals(1234567.0, parseAmount("1.234.567"))
        assertEquals(12.34, parseAmount("12,34"))
        assertEquals(1234.0, parseAmount("1,234"))
        assertEquals(1234.0, parseAmount("1234"))
        assertEquals(89.0, parseAmount("89,00"))
        assertEquals(12.5, parseAmount("12,50"))
    }

    @Test
    fun `reads Switzerland's apostrophe as a thousands separator`() {
        assertEquals(1234.50, parseAmount("1'234.50"))
        assertEquals(1234567.0, parseAmount("1’234’567"))
    }

    @Test
    fun `a dash standing in for the minor unit carries no value`() {
        // German and Austrian shops write "1.449,–" for a round amount.
        assertEquals(1449.0, parseAmount("1.449,–"))
        assertEquals(1449.0, parseAmount("1.449,-"))
    }

    @Test
    fun `refuses what is not a number`() {
        assertNull(parseAmount(""))
        assertNull(parseAmount("руб"))
    }
}
