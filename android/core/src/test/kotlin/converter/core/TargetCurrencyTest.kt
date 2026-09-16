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

    // --- the two questions, which are not the same question ------------------
    @Test
    fun `abroad, the prices are local and the reader is not`() {
        // The situation the app exists for: a Russian visiting Georgia. The
        // prices in front of them are in lari; what they want is roubles.
        // Detecting one answer for both would convert lari into lari.
        assertEquals("GEL", localCurrency(networkCountry = "ge"))
        assertEquals("RUB", homeCurrency(simCountry = "ru", localeCountry = "ru", language = "ru"))
    }

    @Test
    fun `an unknown place leaves the local currency unknown rather than guessed`() {
        // This value decides what an ambiguous symbol means, and a wrong guess
        // converts confidently at the wrong rate. No answer is the safe one.
        assertNull(localCurrency(null))
        assertNull(localCurrency("zz"))
    }

    @Test
    fun `home comes from the SIM first, because it travels with the reader`() {
        // Phone bought in Russia, locale since switched to English: still RUB.
        assertEquals("RUB", homeCurrency(simCountry = "ru", localeCountry = "us", language = "en"))
    }

    @Test
    fun `without a SIM the locale is used, then the language`() {
        assertEquals("PLN", homeCurrency(localeCountry = "pl", language = "ru"))
        assertEquals("RUB", homeCurrency(language = "ru-RU"))
    }

    @Test
    fun `an unknown country falls through rather than blocking the rest`() {
        assertEquals("RUB", homeCurrency(simCountry = "zz", language = "ru"))
    }

    @Test
    fun `home always answers, because something has to be converted into`() {
        assertEquals("USD", homeCurrency())
        assertEquals("EUR", homeCurrency(fallback = "EUR"))
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

class LocalCurrencyResolvesAmbiguityTest {

    private fun read(text: String, local: String?) =
        findPrices(text, PriceContext(pageCurrency = local))

    @Test
    fun `a bare dollar sign is refused when the country is unknown`() {
        // Half of Latin America prints "$" and means a peso. Reading it as USD
        // would be a confident conversion at a rate ten to a thousand times off.
        assertEquals(emptyList(), read("$1,299", null))
    }

    @Test
    fun `standing in Argentina, a dollar sign is a peso`() {
        assertEquals(listOf(Price(1299.0, "ARS")), read("$1,299", localCurrency("ar")))
    }

    @Test
    fun `standing in the United States, the same sign is a dollar`() {
        assertEquals(listOf(Price(1299.0, "USD")), read("$1,299", localCurrency("us")))
    }

    @Test
    fun `a krone is three currencies, and the country picks one`() {
        assertEquals(listOf(Price(429.0, "SEK")), read("429 kr", localCurrency("se")))
        assertEquals(listOf(Price(429.0, "NOK")), read("429 kr", localCurrency("no")))
        assertEquals(emptyList(), read("429 kr", null))
    }

    @Test
    fun `an unambiguous symbol needs no country at all`() {
        // Most prices say plainly what they are; the country is only for the
        // ones that do not.
        assertEquals(listOf(Price(1299.0, "RUB")), read("1 299 ₽", null))
        assertEquals(listOf(Price(450.0, "UAH")), read("450 грн", null))
    }

    @Test
    fun `a country whose currency does not share the symbol does not claim it`() {
        // Standing in Poland does not make "$" a zloty.
        assertEquals(emptyList(), read("$1,299", localCurrency("pl")))
    }
}

class FlagTest {

    @Test
    fun `a currency shows its country's flag`() {
        assertEquals("🇷🇺", flagFor("RUB"))   // ru
        assertEquals("🇺🇦", flagFor("UAH"))   // ua
        assertEquals("🇦🇷", flagFor("ARS"))   // ar
    }

    @Test
    fun `a currency several countries share shows the one that issues it`() {
        // Not whichever member sorts first: the euro is the union's.
        assertEquals("🇪🇺", flagFor("EUR"))   // eu
        assertEquals("🇺🇸", flagFor("USD"))   // us, not Ecuador
        assertEquals("🇨🇭", flagFor("CHF"))   // ch, not Liechtenstein
    }

    @Test
    fun `every currency the picker can show has a flag`() {
        // The generator refuses to write the table without one, so this is the
        // guard on the Kotlin side of that.
        for (code in CURRENCY_CODES) {
            assertEquals(true, flagFor(code) != null, "$code has no flag")
        }
    }

    @Test
    fun `a currency nobody has heard of has no flag rather than a broken one`() {
        assertNull(flagFor("XYZ"))
        assertNull(flagFor(""))
    }

    @Test
    fun `a flag is one glyph pair, not a pair of letters`() {
        val flag = flagFor("RUB")!!
        // Two surrogate pairs: four chars, two code points, both regional
        // indicators. A phone that cannot draw it shows two letters, not junk.
        assertEquals(4, flag.length)
        assertEquals(2, flag.codePointCount(0, flag.length))
    }
}
