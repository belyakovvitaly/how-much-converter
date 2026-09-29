package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculatorTest {

    private fun typed(keys: String): Calculation =
        keys.fold(Calculation()) { calc, key -> calc.press(key) }

    // --- typing -------------------------------------------------------------
    @Test
    fun `digits and a point make a number`() {
        assertEquals("1500.5", typed("1500.5").text)
        assertEquals(1500.5, typed("1500.5").value)
    }

    @Test
    fun `a point first is a zero and a point`() {
        assertEquals("0.5", typed(".5").text)
    }

    @Test
    fun `a number has one point and no leading zeros`() {
        assertEquals("1.25", typed("1.2.5").text)
        assertEquals("5", typed("005").text)
        assertEquals("0.05", typed("0.05").text)
    }

    @Test
    fun `a number stops growing at twelve digits`() {
        assertEquals("123456789012", typed("1234567890123").text)
    }

    @Test
    fun `an operator needs a number before it, and a second one replaces the first`() {
        assertEquals("", typed("$KEY_PLUS").text)
        assertEquals("5$KEY_TIMES", typed("5$KEY_PLUS$KEY_TIMES").text)
    }

    @Test
    fun `erase takes back one key, clear takes back all of them`() {
        assertEquals("15", typed("150$KEY_ERASE").text)
        assertEquals("", typed("150$KEY_CLEAR").text)
    }

    // --- arithmetic ---------------------------------------------------------
    @Test
    fun `times and divide come before plus and minus`() {
        assertEquals(1700.0, typed("200${KEY_PLUS}500${KEY_TIMES}3").value)
        assertEquals(50.0, typed("100${KEY_MINUS}100${KEY_DIVIDE}2").value)
    }

    @Test
    fun `a trailing operator is ignored, so the answer shows while typing`() {
        assertEquals(1500.0, typed("1500$KEY_TIMES").value)
    }

    @Test
    fun `nothing typed and a division by zero are no amount at all`() {
        assertNull(Calculation().value)
        assertNull(typed("5${KEY_DIVIDE}0").value)
    }

    @Test
    fun `an absurd result is no amount`() {
        assertNull(typed("999999999999${KEY_TIMES}999999999999").value)
    }

    @Test
    fun `an operator is arithmetic to show, a lone number is not`() {
        assertFalse(typed("1500").hasOperator)
        assertTrue(typed("1500${KEY_PLUS}2").hasOperator)
    }

    // --- equals and carried results -----------------------------------------
    @Test
    fun `equals replaces the arithmetic with its result`() {
        val done = typed("1500${KEY_TIMES}3$KEY_EQUALS")
        assertEquals("4500", done.text)
        assertTrue(done.replaceOnDigit)
    }

    @Test
    fun `after a result a digit starts afresh, an operator carries on`() {
        val done = typed("2${KEY_PLUS}2$KEY_EQUALS")
        assertEquals("7", done.press('7').text)
        assertEquals("4$KEY_TIMES", done.press(KEY_TIMES).text)
        assertEquals("", done.press(KEY_ERASE).text)
    }

    @Test
    fun `a negative result can be carried on from`() {
        val done = typed("5${KEY_MINUS}8$KEY_EQUALS")
        assertEquals("-3", done.text)
        assertEquals(-6.0, done.press(KEY_TIMES).press('2').value)
    }

    @Test
    fun `a plain number has no trailing zeros and no more decimals than asked`() {
        assertEquals("1500", plainNumber(1500.0))
        assertEquals("3.6", plainNumber(3.6))
        assertEquals("3.58", plainNumber(3.5849))
        assertEquals("1395", plainNumber(1395.2, digits = 0))
        assertEquals("-0.5", plainNumber(-0.5))
        assertEquals("0", plainNumber(-0.001))
    }

    // --- display --------------------------------------------------------------
    @Test
    fun `the screen groups thousands and uses the locale's separators`() {
        assertEquals("1.500.000,5", displayExpression("1500000.5", '.', ','))
        assertEquals("12 000 $KEY_TIMES 3", displayExpression("12000${KEY_TIMES}3", ' ', '.'))
        assertEquals("0,", displayExpression("0.", '.', ','))
        assertEquals("-3 $KEY_PLUS 1", displayExpression("-3${KEY_PLUS}1", ' ', '.'))
    }
}
