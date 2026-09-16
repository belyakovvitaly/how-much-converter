package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Amounts on a receipt, where the currency is printed nowhere near them.
 *
 * The lines are those of a real receipt from an Argentine supermarket, as a
 * recognizer would return them one box at a time.
 */
class ReceiptTest {

    private fun amounts(text: String) = findBareAmounts(text).map { it.amount }

    @Test
    fun `line totals, discounts and the total are amounts`() {
        assertEquals(listOf(32648.0), amounts("32648,00"))
        assertEquals(listOf(-9794.40), amounts("-9794,40"))
        assertEquals(listOf(-22371.60), amounts("-22371,60"))
        assertEquals(listOf(52200.40), amounts("TOTAL 52200,40"))
        assertEquals(listOf(-5612.40), amounts("18708,00 -5612,40").drop(1))
    }

    @Test
    fun `grouped amounts are read whichever way they are grouped`() {
        assertEquals(listOf(1234.56), amounts("1.234,56"))
        assertEquals(listOf(1234.56), amounts("1,234.56"))
        assertEquals(listOf(1234567.89), amounts("1 234 567,89"))
    }

    @Test
    fun `the price per kilo is an amount, the weight before it is not`() {
        assertEquals(listOf(14000.0), amounts("2,332 x 14000,00"))
        assertEquals(listOf(3118.0), amounts("6,000 x 3118,00"))
        // A weight read to only two places still multiplies something.
        assertEquals(listOf(4000.0), amounts("5,80 x 4000,00"))
    }

    @Test
    fun `the numbers that are not money are left alone`() {
        val header = listOf(
            "10/09/2026 13:24:07",
            "NRO.T.:2168-04199243",
            "NRO.CAJA:0004 NRO.TERM:3970",
            "CUIT:30-54808315-6 INGRESOS BRUTOS:901-923274-2",
            "FECHA INICIO ACTIVIDAD COMERCIAL:30/11/2004",
            "0000017613 00000000000000",
            "0.654 x 02517613006544",
            "0000490643 07790742333605",
            "SUC168 COTO CICSA",
            "30 OFF PROMO VISA DE [M]",
            "xx1928 01",
            "IVA 21,00%",
            "1.234.56",
            "A12,50",
        )
        for (line in header) assertEquals(emptyList(), amounts(line), line)
    }

    @Test
    fun `change not given is not labelled`() {
        assertEquals(emptyList(), amounts("0,00"))
    }

    @Test
    fun `bare amounts are read only when the reader asks`() {
        val line = OcrLine("32648,00", box = Box(0.0, 0.0, 100.0, 20.0))
        assertEquals(emptyList(), locatePrices(listOf(line)).map { it.price })
        assertEquals(
            listOf(Price(32648.0, "ARS")),
            locatePrices(listOf(line), PriceContext(bareAmounts = "ARS")).map { it.price },
        )
    }

    @Test
    fun `a currency written on the receipt still wins`() {
        val line = OcrLine("TOTAL 12,50 EUR 3,00", box = Box(0.0, 0.0, 300.0, 20.0))
        assertEquals(
            listOf(Price(12.5, "EUR"), Price(3.0, "ARS")),
            locatePrices(listOf(line), PriceContext(bareAmounts = "ARS")).map { it.price },
        )
    }

    @Test
    fun `a discount keeps its sign when converted`() {
        assertEquals("-63.10 USD", formatConverted(-63.1, "USD"))
        assertEquals("-0.50 USD", formatConverted(-0.5, "USD"))
        assertEquals("-150 USD", formatConverted(-150.2, "USD"))
        assertEquals("0.00 USD", formatConverted(-0.001, "USD"))
    }
}
