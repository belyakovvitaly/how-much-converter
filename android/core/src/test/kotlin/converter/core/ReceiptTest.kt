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
        // A faint dotted minus, as the recognizer reads it off thermal paper.
        assertEquals(listOf(-6964.80), amounts("~6964,80"))
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

    private fun pesos(text: String) = findBareAmounts(text, "CLP").map { it.amount }

    /** The lines of a Chilean receipt, from IKEA's restaurant in Santiago. */
    @Test
    fun `whole pesos grouped by thousands are amounts in a currency without cents`() {
        assertEquals(listOf(8990.0), pesos("Pastelera con carne pc 8.990 D"))
        assertEquals(listOf(1190.0), pesos("1.190 D"))
        assertEquals(listOf(24040.0), pesos("Total $ 24.040"))
        assertEquals(listOf(-24040.0), pesos("-24.040"))
        assertEquals(listOf(20201.0, 3839.0), pesos("Monto Neto 20.201 IVA 19% 3.839"))
        assertEquals(listOf(1234567.0), pesos("1.234.567"))
        assertEquals(listOf(3500.0), findBareAmounts("3,500", "JPY").map { it.amount })
    }

    @Test
    fun `the numbers on a Chilean receipt that are not money are left alone`() {
        val lines = listOf(
            // Under a thousand there is no separator to tell a price by.
            "LASKANDE bebida IKEA pc 990 D",
            "Número de Artículos: 7",
            "N° de Item: 598000289",
            "Recibo 0000000242000239443",
            "Fecha: 05-10-26 17:27",
            "Trans: 268626",
            "T. Crédito 6790",
            "IVA 19%",
            "R.U.T.: 76.123.456-7",
            "RUT 12.345.678-K",
            "0.654",
            "1.250 kg",
            "2.000 x 1.990",
            "5.10.2026",
            // A Chilean weight, with the decimal comma, is not grouped pesos.
            "1,234",
        )
        for (line in lines) {
            val expected = if (line == "2.000 x 1.990") listOf(1990.0) else emptyList()
            assertEquals(expected, pesos(line), line)
        }
    }

    @Test
    fun `whole amounts are read only for a currency without cents`() {
        assertEquals(emptyList(), amounts("24.040"))
        assertEquals(emptyList(), findBareAmounts("24.040", "ARS"))
        assertEquals(listOf(24040.0), pesos("24.040"))
        // Cents still read the old way, whatever the currency.
        assertEquals(listOf(1234.56), pesos("1.234,56"))
    }

    @Test
    fun `a receipt in pesos is read through the receipt switch`() {
        val line = OcrLine("Daim Tarta de almend pc 1.990 D", box = Box(0.0, 0.0, 300.0, 20.0))
        assertEquals(
            listOf(Price(1990.0, "CLP")),
            locatePrices(listOf(line), PriceContext(bareAmounts = "CLP")).map { it.price },
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
