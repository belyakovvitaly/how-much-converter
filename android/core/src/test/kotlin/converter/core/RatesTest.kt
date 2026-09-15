package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RatesTest {

    private val table = RateTable(
        base = "USD",
        rates = mapOf("USD" to 1.0, "EUR" to 0.92, "RUB" to 92.0, "JPY" to 150.0),
        fetchedAt = 1_000_000,
    )

    // --- converting ---------------------------------------------------------
    @Test
    fun `converts through the base currency`() {
        // 9200 RUB is 100 USD is 92 EUR.
        assertEquals(92.0, table.convert(9200.0, "RUB", "EUR")!!, 1e-9)
    }

    @Test
    fun `converting a currency to itself changes nothing`() {
        assertEquals(1299.0, table.convert(1299.0, "RUB", "RUB"))
    }

    @Test
    fun `an unknown currency yields nothing rather than a guess`() {
        assertNull(table.convert(10.0, "RUB", "XYZ"))
        assertNull(table.convert(10.0, "XYZ", "RUB"))
    }

    @Test
    fun `an unusable rate yields nothing`() {
        val broken = table.copy(rates = table.rates + ("BAD" to 0.0))
        assertNull(broken.convert(10.0, "BAD", "USD"))
    }

    // --- freshness ----------------------------------------------------------
    @Test
    fun `a table is fresh for six hours and not a moment longer`() {
        assertTrue(table.isFresh(table.fetchedAt))
        assertTrue(table.isFresh(table.fetchedAt + RATES_MAX_AGE_MILLIS - 1))
        assertTrue(!table.isFresh(table.fetchedAt + RATES_MAX_AGE_MILLIS))
    }

    @Test
    fun `a table fetched in the future is not treated as fresh`() {
        // A clock that jumped backwards should send us to the network, not
        // leave a table that never expires.
        assertTrue(!table.isFresh(table.fetchedAt - 1))
    }

    // --- parsing ------------------------------------------------------------
    private val goodResponse = """
        {"result":"success","base_code":"USD",
         "rates":{"USD":1,"EUR":0.92,"RUB":92.0}}
    """.trimIndent()

    @Test
    fun `reads the rate service's response`() {
        val parsed = parseRatesResponse(goodResponse, fetchedAt = 42)!!
        assertEquals("USD", parsed.base)
        assertEquals(42, parsed.fetchedAt)
        assertEquals(0.92, parsed.rates["EUR"])
    }

    @Test
    fun `refuses a payload that says it failed`() {
        assertNull(parseRatesResponse("""{"result":"error","rates":{"EUR":0.9}}""", 0))
    }

    @Test
    fun `refuses a payload with no rates, and anything that is not JSON`() {
        assertNull(parseRatesResponse("""{"result":"success"}""", 0))
        assertNull(parseRatesResponse("<html>down for maintenance</html>", 0))
        assertNull(parseRatesResponse("", 0))
    }

    @Test
    fun `skips individual rates that make no sense`() {
        val parsed = parseRatesResponse(
            """{"result":"success","rates":{"EUR":0.92,"AAA":0,"BBB":"nonsense"}}""", 0
        )!!
        assertEquals(setOf("EUR"), parsed.rates.keys)
    }

    // --- the policy ---------------------------------------------------------
    @Test
    fun `a fresh cache is used without going to the network`() {
        var asked = false
        val outcome = resolveRates(table, now = table.fetchedAt + 1) {
            asked = true
            goodResponse
        }
        assertIs<RatesOutcome.Fresh>(outcome)
        assertTrue(!asked, "the network should not have been touched")
    }

    @Test
    fun `a forced refresh goes to the network even when the cache is fresh`() {
        var asked = false
        resolveRates(table, now = table.fetchedAt + 1, force = true) {
            asked = true
            goodResponse
        }
        assertTrue(asked)
    }

    @Test
    fun `a stale cache is refreshed`() {
        val outcome = resolveRates(table, now = table.fetchedAt + RATES_MAX_AGE_MILLIS) {
            goodResponse
        }
        val fresh = assertIs<RatesOutcome.Fresh>(outcome)
        assertEquals(table.fetchedAt + RATES_MAX_AGE_MILLIS, fresh.table.fetchedAt)
    }

    @Test
    fun `a failed fetch falls back to the old table rather than to nothing`() {
        // Yesterday's rate is a good answer; no answer is not.
        val outcome = resolveRates(table, now = table.fetchedAt + RATES_MAX_AGE_MILLIS) {
            throw IllegalStateException("no network")
        }
        val stale = assertIs<RatesOutcome.Stale>(outcome)
        assertEquals(table, stale.table)
        assertEquals("no network", stale.reason)
    }

    @Test
    fun `a response that parses to nothing counts as a failure, not as rates`() {
        val outcome = resolveRates(table, now = table.fetchedAt + RATES_MAX_AGE_MILLIS) {
            """{"result":"error"}"""
        }
        assertIs<RatesOutcome.Stale>(outcome)
    }

    @Test
    fun `with no cache and no network there is nothing to offer`() {
        val outcome = resolveRates(cached = null, now = 0) { throw Exception("offline") }
        val unavailable = assertIs<RatesOutcome.Unavailable>(outcome)
        assertEquals("offline", unavailable.reason)
    }

    // --- formatting ---------------------------------------------------------
    @Test
    fun `small amounts keep their cents and large ones do not`() {
        assertEquals("12.34 EUR", formatConverted(12.344, "EUR"))
        assertEquals("1299 RUB", formatConverted(1299.4, "RUB"))
    }

    @Test
    fun `a currency without a minor unit never shows one`() {
        assertEquals("15 JPY", formatConverted(15.4, "JPY"))
        assertEquals("7300 PYG", formatConverted(7300.0, "PYG"))
    }

    @Test
    fun `cents are padded, not truncated`() {
        assertEquals("12.05 EUR", formatConverted(12.05, "EUR"))
        assertEquals("12.00 EUR", formatConverted(12.0, "EUR"))
    }
}
