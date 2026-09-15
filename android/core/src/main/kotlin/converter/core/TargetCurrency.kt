// Guessing what the reader wants prices in.
//
// The extension asks the same question of a web page and answers it from the
// page's domain and language. A phone can do better: it knows which country's
// network it is on, which is a fact about where its owner is standing rather
// than about what they once configured.
package converter.core

/** The currency spent in [country], by two-letter code, or null if unknown. */
fun currencyForCountry(country: String?): String? {
    val code = country?.trim()?.lowercase() ?: return null
    return COUNTRY_TO_CURRENCY[code]
}

/**
 * The currency a speaker of [language] most likely thinks in.
 *
 * A weaker signal than the country and used only after it — a Russian speaker
 * may well be standing in Georgia — but better than defaulting to dollars for
 * someone who has never held one. Accepts a bare code or a full tag: "ru",
 * "ru-RU", "ru_RU".
 */
fun currencyForLanguage(language: String?): String? {
    val tag = language?.trim()?.lowercase() ?: return null
    val base = tag.takeWhile { it != '-' && it != '_' }
    return LANG_TO_CURRENCY[base]
}

/**
 * What to convert into, from the strongest signal available.
 *
 * Country first, because it says where the prices being photographed are not.
 * Then the region of the locale, then its language, and only then [fallback] —
 * which is a guess, and a poor one for most of the world.
 */
fun chooseTargetCurrency(
    country: String? = null,
    localeCountry: String? = null,
    language: String? = null,
    fallback: String = "USD",
): String =
    currencyForCountry(country)
        ?: currencyForCountry(localeCountry)
        ?: currencyForLanguage(language)
        ?: fallback
