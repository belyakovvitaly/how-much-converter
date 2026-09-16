package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A second look to the left of a bare number, for a symbol the detector missed. */
class PrefixLookTest {

    @Test
    fun `a line that starts with a bare number is worth a look`() {
        assertEquals("5600", leadingBareNumber("5600×Kg"))
        assertEquals("3.648,75", leadingBareNumber("3.648,75"))
        assertEquals("32648,00", leadingBareNumber(" 32648,00"))
    }

    @Test
    fun `a line that already says its currency, or is not a number, is not`() {
        assertNull(leadingBareNumber("5600 ₽"))
        assertNull(leadingBareNumber("450 грн"))
        assertNull(leadingBareNumber("$5600"))
        assertNull(leadingBareNumber("Pollo Entero"))
    }

    @Test
    fun `the chalked sign gets its dollar back`() {
        // What the phone read, and what a crop with room to the left read.
        assertEquals("$5600×Kg", withPrefixFrom("5600×Kg", "$5600Kg"))
        assertEquals("$5600×Kg", withPrefixFrom("5600×Kg", "$ 5600 Kg"))
        assertEquals("€12,50", withPrefixFrom("12,50", "€12,50"))
    }

    @Test
    fun `the symbol is written touching the number, so nothing before claims it`() {
        // The scrap the symbol was detected as, read as a digit, beside the
        // line with the symbol restored: one price, not two.
        val prices = locatePrices(
            listOf(
                OcrLine("8", box = Box(270.0, 700.0, 297.0, 735.0)),
                OcrLine(withPrefixFrom("5600×Kg", "$5600Kg")!!, box = Box(250.0, 685.0, 503.0, 785.0)),
            ),
            PriceContext(pageCurrency = "ARS"),
        ).map { it.price }
        assertEquals(listOf(Price(5600.0, "ARS")), prices)
    }

    @Test
    fun `a scrap a glyph wide is taken for a glyph, a word is not`() {
        assertEquals(true, looksLikeLoneGlyph(OcrLine("8", box = Box(0.0, 0.0, 23.0, 32.0))))
        assertEquals(true, looksLikeLoneGlyph(OcrLine("69", box = Box(0.0, 0.0, 46.0, 60.0))))
        assertEquals(false, looksLikeLoneGlyph(OcrLine("30", box = Box(0.0, 0.0, 120.0, 32.0))))
        assertEquals(false, looksLikeLoneGlyph(OcrLine("OFF", box = Box(0.0, 0.0, 30.0, 32.0))))
    }

    @Test
    fun `the digits are never the second look's`() {
        // The symbol read as a digit and fused: the number no longer matches.
        assertNull(withPrefixFrom("1 200,50", "21 200,50"))
        assertNull(withPrefixFrom("5600×Kg", "$15600Kg"))
        // A different number altogether.
        assertNull(withPrefixFrom("5600", "$5800"))
    }

    @Test
    fun `only a symbol is taken, not a stray letter or word`() {
        assertNull(withPrefixFrom("5600", "S5600"))
        assertNull(withPrefixFrom("5600", "x 5600"))
        assertNull(withPrefixFrom("5600", "5600"))
        assertNull(withPrefixFrom("5600", "Total 5600"))
    }

    @Test
    fun `the region grows toward where its text starts`() {
        val level = RotatedBox(100.0, 50.0, 80.0, 20.0, 0.0).extendedBack(20.0)
        assertEquals(Box(40.0, 40.0, 140.0, 60.0), level.bounds)

        // Tilted, it grows back along the text, not straight left.
        val tilted = RotatedBox(100.0, 50.0, 80.0, 20.0, Math.toRadians(30.0)).extendedBack(20.0)
        assertEquals(100.0 - kotlin.math.cos(Math.toRadians(30.0)) * 10, tilted.centerX, 1e-9)
        assertEquals(50.0 - kotlin.math.sin(Math.toRadians(30.0)) * 10, tilted.centerY, 1e-9)
        assertEquals(100.0, tilted.width)
    }
}
