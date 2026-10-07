package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Amounts under a thousand on a receipt in a currency without cents, which
 * are told from counts and codes by where they stand rather than how they read.
 */
class AmountColumnTest {

    private fun line(text: String, x0: Int, y0: Int, x1: Int, y1: Int) =
        OcrLine(text, box = Box(x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble()))

    /**
     * Every line the engine returned for the Chilean receipt in
     * ReceiptPhotoTest, with its box, rounded to the pixel.
     */
    private val chilean = listOf(
        line("6", 701, 22, 885, 70),
        line("18:31", 46, 25, 185, 68),
        line("RosarTU", 275, 126, 378, 163),
        line("ress.", 208, 148, 268, 174),
        line("CONDES", 319, 158, 417, 199),
        line("NA DE SANTIAGCI", 409, 167, 651, 242),
        line("Region: MEIKU", 146, 217, 342, 280),
        line("Recibo 000000242000239443", 108, 261, 545, 358),
        line("IDN: LEOR FoOD)", 103, 302, 412, 382),
        line("Trans:", 474, 303, 585, 356),
        line("268626", 647, 312, 758, 366),
        line("Fecha: 05-10-26 17:27", 99, 326, 462, 414),
        line("Descripción", 100, 406, 294, 471),
        line("Monto", 618, 413, 717, 467),
        line("N° de Item: 598000289", 89, 475, 462, 540),
        line("Pastelera con carne pc", 85, 515, 496, 570),
        line("8.990", 621, 516, 728, 565),
        line("D", 716, 518, 772, 555),
        line("N° de Item: 598000015", 81, 551, 461, 606),
        line("5.490", 626, 583, 733, 629),
        line("D", 745, 587, 775, 622),
        line("Albondigas suecas de pc", 79, 591, 493, 636),
        line("N°de ItEm: 5980C0112", 71, 621, 465, 677),
        line("1.190", 634, 651, 741, 701),
        line("D", 746, 654, 783, 694),
        line("Papas fritas_ Add On pc", 72, 660, 478, 711),
        line("Nde Item: 598000066", 65, 691, 467, 751),
        line("OD", 726, 722, 791, 776),
        line("1.490", 636, 726, 745, 776),
        line("Cafe Ponderado Rest pc", 66, 734, 482, 789),
        line("N°de Item: 598000084", 62, 766, 470, 831),
        line("990", 667, 800, 744, 857),
        line("D", 746, 806, 785, 846),
        line("LASKANDE bebida IKEA pc", 62, 811, 498, 866),
        line("N° de Item: 598000018", 60, 846, 468, 911),
        line("1.990", 639, 884, 737, 929),
        line("D", 751, 885, 782, 919),
        line("Daim Tarta de almend pc", 62, 887, 505, 952),
        line("Nde Item: 59800018", 57, 922, 475, 997),
        line("3.900", 634, 950, 736, 999),
        line("D", 750, 956, 779, 985),
        line("Sandwich de camarone pc", 60, 958, 509, 1038),
        line("24.040", 612, 1014, 721, 1052),
        line("-24.040", 604, 1036, 720, 1078),
        line("Total", 75, 1048, 177, 1092),
        line("\$", 204, 1048, 233, 1077),
        line("rédito", 172, 1070, 273, 1103),
        line("Numero_de Articulos:", 91, 1115, 436, 1170),
        line("Detalle Totales e Impuestos", 103, 1140, 540, 1207),
        line("20.201", 575, 1169, 675, 1216),
        line("3.839", 583, 1193, 676, 1242),
        line("Monto Neto", 110, 1205, 288, 1266),
        line("IVA 19%", 111, 1237, 246, 1296),
        line("24.040", 571, 1244, 674, 1299),
        line("Total", 115, 1301, 213, 1362),
        line("Send message", 46, 1825, 276, 1875),
    )

    @Test
    fun `the drink at 990 is read in the column of the amounts around it`() {
        val amounts = locatePrices(chilean, PriceContext(bareAmounts = "CLP")).map { it.price.amount }
        assertEquals(
            listOf(
                8990.0, 5490.0, 1190.0, 1490.0, 990.0, 1990.0, 3900.0,
                24040.0, -24040.0, 20201.0, 3839.0, 24040.0,
            ).sorted(),
            amounts.sorted(),
        )
    }

    @Test
    fun `a short number is read only for a currency without cents`() {
        val amounts = locatePrices(chilean, PriceContext(bareAmounts = "ARS")).map { it.price.amount }
        assertEquals(emptyList(), amounts)
    }

    private val above = Box(600.0, 700.0, 740.0, 750.0)
    private val below = Box(600.0, 900.0, 740.0, 950.0)

    @Test
    fun `a number right-aligned between two amounts is in their column`() {
        assertTrue(inAmountColumn(Box(670.0, 800.0, 740.0, 850.0), listOf(above, below)))
        // The column leans, as a receipt in a hand does.
        val leaning = Box(580.0, 900.0, 700.0, 950.0)
        assertTrue(inAmountColumn(Box(650.0, 800.0, 720.0, 850.0), listOf(above, leaning)))
    }

    @Test
    fun `a number out of the column is not`() {
        // Off by a digit's width.
        assertFalse(inAmountColumn(Box(645.0, 800.0, 715.0, 850.0), listOf(above, below)))
        // Above the first amount, as a transaction number in the header is.
        assertFalse(inAmountColumn(Box(670.0, 600.0, 740.0, 650.0), listOf(above, below)))
        // Below the last.
        assertFalse(inAmountColumn(Box(670.0, 1000.0, 740.0, 1050.0), listOf(above, below)))
        // In the description's column, a line's number or an address.
        assertFalse(inAmountColumn(Box(300.0, 800.0, 370.0, 850.0), listOf(above, below)))
    }

    @Test
    fun `what is not a short amount is not read, in the column or out of it`() {
        for (text in listOf("7", "12", "6790", "268626", "099", "19%", "250 g", "120 x", "17:27")) {
            assertEquals(emptyList(), findShortWholeAmounts(text, "CLP"), text)
        }
        assertEquals(listOf(990.0), findShortWholeAmounts("990 D", "CLP").map { it.amount })
        assertEquals(listOf(-500.0), findShortWholeAmounts("-500", "CLP").map { it.amount })
        assertEquals(emptyList(), findShortWholeAmounts("990", "USD"))
    }

    @Test
    fun `a short number cannot vouch for the next`() {
        // One amount and two short numbers under it: the first has no amount
        // below it, so neither is in a column.
        val lines = listOf(
            line("1.490", 600, 700, 740, 750),
            line("990", 670, 800, 740, 850),
            line("500", 670, 900, 740, 950),
        )
        val amounts = locatePrices(lines, PriceContext(bareAmounts = "CLP")).map { it.price.amount }
        assertEquals(listOf(1490.0), amounts)
    }
}
