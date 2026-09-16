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
        assertEquals("$ 5600×Kg", withPrefixFrom("5600×Kg", "$5600Kg"))
        assertEquals("$ 5600×Kg", withPrefixFrom("5600×Kg", "$ 5600 Kg"))
        assertEquals("€ 12,50", withPrefixFrom("12,50", "€12,50"))
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
