// Which currency the prices are in, and which one the reader thinks in.
//
// These are two different questions and the app needs both, which is the whole
// point of it: standing in another country, the prices in front of you are in
// a currency you do not think in, and you want them in the one you do. Guessing
// the same answer for both — as an earlier version of this file did, by
// detecting the *target* from where the phone was — converts the local currency
// into itself, which is exactly useless in the only situation that matters.
//
// The signals differ too. Where the phone is says what the prices are; where it
// is from says what the reader is used to.
package converter.core

/** The currency spent in [country], by two-letter code, or null if unknown. */
fun currencyForCountry(country: String?): String? {
    val code = country?.trim()?.lowercase() ?: return null
    return COUNTRY_TO_CURRENCY[code]
}

/**
 * The currency a speaker of [language] most likely thinks in.
 *
 * A weaker signal than a country and used only after one — a Russian speaker
 * may well be living in Georgia — but better than defaulting to dollars for
 * someone who has never held one. Accepts a bare code or a full tag: "ru",
 * "ru-RU", "ru_RU".
 */
fun currencyForLanguage(language: String?): String? {
    val tag = language?.trim()?.lowercase() ?: return null
    val base = tag.takeWhile { it != '-' && it != '_' }
    return LANG_TO_CURRENCY[base]
}

/**
 * What the prices being photographed are most likely in: the currency of the
 * country the phone is standing in.
 *
 * Returns null when that is not known, and deliberately does not fall back to
 * anything. This value decides what an ambiguous symbol means — `$` is a peso
 * in half of Latin America, `kr` is three different krone — so a wrong guess
 * does not merely fail to help, it produces a confident conversion at the wrong
 * rate. No answer leaves those symbols unresolved, which is the safe outcome.
 */
fun localCurrency(networkCountry: String?): String? = currencyForCountry(networkCountry)

/**
 * What to convert into: the reader's own currency.
 *
 * The SIM's country says where they are from even while they are abroad, which
 * is why it comes before the locale. This always answers, because something has
 * to be converted into; [fallback] is a poor guess for most of the world and is
 * only the last resort.
 */
fun homeCurrency(
    simCountry: String? = null,
    localeCountry: String? = null,
    language: String? = null,
    fallback: String = "USD",
): String =
    currencyForCountry(simCountry)
        ?: currencyForCountry(localeCountry)
        ?: currencyForLanguage(language)
        ?: fallback
