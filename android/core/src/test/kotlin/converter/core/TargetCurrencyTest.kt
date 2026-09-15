package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetCurrencyTest {

    @Test
    fun `a country code gives the currency spent there`() {
        assertEquals("RUB", currencyForCountry("ru"))
        assertEquals("UAH", currencyForCountry("UA"))
        assertEquals("KZT", currencyForCountry(" kz "))
    }

    @Test
    fun `the euro area is one answer, not twenty`() {
        for (country in listOf("de", "fr", "it", "es", "pt", "ie", "bg")) {
            assertEquals("EUR", currencyForCountry(country), "for $country")
        }
    }

    @Test
    fun `a country we do not know is not guessed at`() {
        assertNull(currencyForCountry("zz"))
        assertNull(currencyForCountry(""))
        assertNull(currencyForCountry(null))
    }

    @Test
    fun `a language tag works bare or full`() {
        assertEquals("RUB", currencyForLanguage("ru"))
        assertEquals("RUB", currencyForLanguage("ru-RU"))
        assertEquals("RUB", currencyForLanguage("ru_RU"))
        assertEquals("UAH", currencyForLanguage("uk-UA"))
    }

    @Test
    fun `English has no currency of its own, and is not given one`() {
        // Half the world speaks it and they do not share a currency; the
        // country is the signal, not the language.
        assertNull(currencyForLanguage("en"))
        assertNull(currencyForLanguage("en-GB"))
    }

    @Test
    fun `the country wins over the locale and the language`() {
        // Someone Russian-speaking, phone set to Russia, standing in Georgia:
        // the prices in front of them are in lari.
        assertEquals(
            "GEL",
            chooseTargetCurrency(country = "ge", localeCountry = "ru", language = "ru"),
        )
    }

    @Test
    fun `without a country the locale is used, then the language`() {
        assertEquals("PLN", chooseTargetCurrency(localeCountry = "pl", language = "ru"))
        assertEquals("RUB", chooseTargetCurrency(language = "ru-RU"))
    }

    @Test
    fun `an unknown country falls through rather than blocking the rest`() {
        assertEquals("RUB", chooseTargetCurrency(country = "zz", language = "ru"))
    }

    @Test
    fun `with no signal at all the fallback is used and named`() {
        assertEquals("USD", chooseTargetCurrency())
        assertEquals("EUR", chooseTargetCurrency(fallback = "EUR"))
    }

    @Test
    fun `every currency a country maps to is one the converter knows`() {
        // A guess pointing at a currency with no rate and no name would show
        // the reader an empty conversion.
        val known = CURRENCY_CODES.toSet()
        for ((country, code) in COUNTRY_TO_CURRENCY) {
            assertEquals(true, code in known, "$country maps to $code, which is not in CURRENCY_CODES")
        }
        for ((language, code) in LANG_TO_CURRENCY) {
            assertEquals(true, code in known, "$language maps to $code, which is not in CURRENCY_CODES")
        }
    }

    @Test
    fun `every currency has a name for the picker to show`() {
        for (code in CURRENCY_CODES) {
            assertEquals(true, CURRENCY_NAMES.containsKey(code), "$code has no name")
        }
    }
}
