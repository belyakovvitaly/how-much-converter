package converter.core

import kotlin.test.Test
import kotlin.test.assertContains

class ProblemReportTest {

    private val report = ProblemReport(
        appVersion = "0.6.0",
        device = "Android 16 (API 36), samsung SM-S931B",
        writtenAt = "2026-09-29 16:10",
        origin = "camera",
        readWidth = 4000,
        readHeight = 3000,
        source = "ARS",
        detectedSource = "ARS",
        target = "USD",
        detectedTarget = null,
        receipt = false,
        rates = RateTable("USD", mapOf("USD" to 1.0, "ARS" to 1000.0), fetchedAt = 42),
        ratesFetchedAt = "2026-09-29 11:40",
        lines = listOf(
            OcrLine("$ 3.648,75", 0.91, Box(212.0, 340.4, 388.0, 391.6)),
            OcrLine("PECHUGAS ", 0.5, null),
        ),
        prices = listOf(LocatedPrice(Price(3648.75, "ARS"), Box(212.0, 340.0, 388.0, 392.0))),
        note = "  the second tag was not read ",
    )

    private val text = problemReportText(report)

    @Test
    fun `says which build, on what, and what the reader said`() {
        assertContains(text, "How Much? 0.6.0 · Android 16 (API 36), samsung SM-S931B")
        assertContains(text, "What was wrong: the second tag was not read\n")
    }

    @Test
    fun `says what the currencies were and where each came from`() {
        assertContains(text, "prices in: ARS (detected ARS)")
        assertContains(text, "convert into: USD (detected nothing)")
        assertContains(text, "fetched 2026-09-29 11:40, 1 ARS = 0.001 USD")
    }

    @Test
    fun `gives every line as read, quoted, with its box`() {
        assertContains(text, "\"$ 3.648,75\"  0.91  at 212,340–388,392")
        assertContains(text, "\"PECHUGAS \"  0.5  at ?")
    }

    @Test
    fun `gives every price with what it became`() {
        assertContains(text, "3648.75 ARS -> 3.65 USD")
    }

    @Test
    fun `an empty note is said to be empty`() {
        assertContains(problemReportText(report.copy(note = " ")), "What was wrong: (not said)")
    }
}
