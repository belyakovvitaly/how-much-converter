package converter.android.rates

import android.content.Context
import android.telephony.TelephonyManager
import converter.core.chooseTargetCurrency
import java.util.Locale

/**
 * What to convert into: whatever the reader chose, or a guess from where the
 * phone is.
 *
 * The guess comes from the mobile network's country, then the SIM's, then the
 * locale. That is deliberately not the location permission: the network country
 * answers the only question being asked — which country's prices am I looking
 * at — without asking for GPS, without a geocoder lookup over the network, and
 * without anything about the reader leaving the device, which is the promise
 * PRIVACY.md makes for the extension and this keeps for the app.
 *
 * A phone with no SIM, an emulator included, simply falls through to the locale.
 */
class TargetCurrencyStore(private val context: Context) {

    private val preferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The reader's explicit choice, or null when it is left automatic. */
    var chosen: String?
        get() = preferences.getString(KEY_TARGET, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_TARGET) else putString(KEY_TARGET, value)
            }.apply()
        }

    /** The currency to use now. */
    fun resolve(): String = chosen ?: detect()

    /** Where the phone thinks it is, expressed as money. */
    fun detect(): String {
        val telephony = runCatching {
            context.getSystemService(TelephonyManager::class.java)
        }.getOrNull()

        // networkCountryIso is where the phone is; simCountryIso is where it is
        // from. Neither needs a permission.
        val country = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }

        val locale = Locale.getDefault()
        return chooseTargetCurrency(
            country = country,
            localeCountry = locale.country,
            language = locale.language,
        )
    }

    private companion object {
        const val KEY_TARGET = "targetCurrency"
    }
}
