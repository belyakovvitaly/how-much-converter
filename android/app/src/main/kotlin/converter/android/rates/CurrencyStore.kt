package converter.android.rates

import android.content.Context
import android.telephony.TelephonyManager
import converter.core.homeCurrency
import converter.core.localCurrency
import java.util.Locale

/**
 * The two currencies the app needs, and where each comes from.
 *
 * **Local** is what the prices in front of the camera are in. It is detected
 * from the mobile network's country — where the phone is standing — and it is
 * allowed to be unknown, because its job is to say what an ambiguous symbol
 * means and a wrong answer there converts confidently at the wrong rate.
 *
 * **Home** is what to convert into. It comes from the SIM's country first,
 * which travels with the reader and still says where they are from while they
 * are abroad, then the locale.
 *
 * Neither reading needs a permission and neither leaves the device, which keeps
 * the promise PRIVACY.md makes for the extension.
 */
class CurrencyStore(private val context: Context) {

    private val preferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** An explicit choice of what the prices are in, or null to detect it. */
    var local: String?
        get() = preferences.getString(KEY_LOCAL, null)
        set(value) = put(KEY_LOCAL, value)

    /** An explicit choice of what to convert into, or null to detect it. */
    var home: String?
        get() = preferences.getString(KEY_HOME, null)
        set(value) = put(KEY_HOME, value)

    fun resolvedLocal(): String? = local ?: detectLocal()

    fun resolvedHome(): String = home ?: detectHome()

    /** Where the phone is, or null when that is not known. */
    fun detectLocal(): String? = localCurrency(telephony()?.networkCountryIso.orBlankToNull())

    /** Where the phone — and so its owner — is from. */
    fun detectHome(): String {
        val locale = Locale.getDefault()
        return homeCurrency(
            simCountry = telephony()?.simCountryIso.orBlankToNull(),
            localeCountry = locale.country,
            language = locale.language,
        )
    }

    private fun telephony(): TelephonyManager? = runCatching {
        context.getSystemService(TelephonyManager::class.java)
    }.getOrNull()

    private fun String?.orBlankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun put(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    private companion object {
        const val KEY_LOCAL = "localCurrency"
        const val KEY_HOME = "homeCurrency"
    }
}
