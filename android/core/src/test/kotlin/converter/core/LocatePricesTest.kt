package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where each price sits, so a conversion can be drawn over it rather than
 * listed elsewhere on the screen.
 */
class LocatePricesTest {

    private fun line(text: String, x0: Double, x1: Double, y0: Double = 0.0, y1: Double = 40.0) =
        OcrLine(text, box = Box(x0, y0, x1, y1))

    @Test
    fun `a price gets the box of the line it was read from`() {
        val found = locatePrices(listOf(line("1 299 ₽", 100.0, 300.0)))
        assertEquals(listOf(LocatedPrice(Price(1299.0, "RUB"), Box(100.0, 0.0, 300.0, 40.0))), found)
    }

    @Test
    fun `a price split across boxes gets both of them`() {
        // The detector routinely splits a large price; the label has to cover
        // the number and its symbol, not half of it.
        val found = locatePrices(
            listOf(line("1299", 100.0, 260.0), line("P", 262.0, 300.0))
        )
        val located = found.single()
        assertEquals(Price(1299.0, "RUB"), located.price)
        assertEquals(Box(100.0, 0.0, 300.0, 40.0), located.box)
    }

    @Test
    fun `two prices on one line get their own boxes, not a shared one`() {
        // "Цена: 99 USD / 9 900 ₽" is one phrase but two answers, and stacking
        // both labels on the same rectangle would hide one of them.
        val found = locatePrices(
            listOf(line("99 USD", 100.0, 220.0), line("/", 226.0, 240.0), line("9 900 ₽", 246.0, 400.0))
        )
        assertEquals(2, found.size)

        val usd = found.first { it.price.code == "USD" }
        val rub = found.first { it.price.code == "RUB" }
        assertEquals(Box(100.0, 0.0, 220.0, 40.0), usd.box)
        assertEquals(Box(246.0, 0.0, 400.0, 40.0), rub.box)
        assertTrue(usd.box.x1 <= rub.box.x0, "the two labels should not overlap")
    }

    @Test
    fun `prices on different rows keep their own vertical positions`() {
        val found = locatePrices(
            listOf(
                line("180 ₽", 600.0, 760.0, y0 = 10.0, y1 = 50.0),
                line("290 ₽", 600.0, 760.0, y0 = 90.0, y1 = 130.0),
            )
        )
        assertEquals(2, found.size)
        assertEquals(10.0, found.first { it.price.amount == 180.0 }.box.y0)
        assertEquals(90.0, found.first { it.price.amount == 290.0 }.box.y0)
    }

    @Test
    fun `readPrices still answers with just the prices`() {
        // The older, position-free reading is what the benchmark scores.
        val lines = listOf(line("1 299 ₽", 100.0, 300.0))
        assertEquals(locatePrices(lines).map { it.price }, readPrices(lines))
    }

    @Test
    fun `a line with no box is skipped rather than placed at the origin`() {
        // A label drawn in the corner because its position was unknown would be
        // worse than no label.
        val found = locatePrices(listOf(OcrLine("1 299 ₽", box = null)))
        assertTrue(found.isEmpty())
    }
}
