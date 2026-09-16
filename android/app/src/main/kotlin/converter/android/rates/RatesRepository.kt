package converter.android.rates

import android.content.Context
import android.util.Log
import converter.core.RATES_URL
import converter.core.RateTable
import converter.core.RatesOutcome
import converter.core.parseRatesResponse
import converter.core.resolveRates
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the rate table and keeps the last one.
 *
 * The policy — when to refetch, and what to do when the fetch fails — is in
 * `:core`, where it is tested without a network. What is here is the network
 * and the storage.
 *
 * The response is cached as the raw JSON it arrived as, rather than as a
 * serialized map: it is already the smallest faithful form, and storing it
 * means there is only one parser to agree with.
 */
class RatesRepository(context: Context) {

    private val preferences =
        context.getSharedPreferences("rates", Context.MODE_PRIVATE)

    fun cached(): RateTable? {
        val json = preferences.getString(KEY_JSON, null) ?: return null
        val fetchedAt = preferences.getLong(KEY_FETCHED_AT, 0)
        return parseRatesResponse(json, fetchedAt)
    }

    /** Blocking: call it off the main thread. */
    fun load(force: Boolean = false): RatesOutcome {
        var response: String? = null
        val outcome = resolveRates(
            cached = cached(),
            now = System.currentTimeMillis(),
            force = force,
        ) {
            fetch().also { response = it }
        }

        val body = response
        if (outcome is RatesOutcome.Fresh && body != null) {
            preferences.edit()
                .putString(KEY_JSON, body)
                .putLong(KEY_FETCHED_AT, outcome.table.fetchedAt)
                .apply()
        }
        if (outcome is RatesOutcome.Stale) {
            Log.w(TAG, "using rates from ${outcome.table.fetchedAt}: ${outcome.reason}")
        }
        return outcome
    }

    private fun fetch(): String {
        val connection = (URL(RATES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("the rate service answered $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "HowMuchRates"
        const val KEY_JSON = "response"
        const val KEY_FETCHED_AT = "fetchedAt"
        const val TIMEOUT_MILLIS = 10_000
    }
}
