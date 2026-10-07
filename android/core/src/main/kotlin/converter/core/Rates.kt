// Exchange rates: one USD-based table, every pair derived from it.
//
// A port of extension/src/background.js. The network and the storage are the
// caller's business — everything here is a function of its arguments, so the
// policy that matters (when to refetch, what to do when the fetch fails) is
// testable without either.
package converter.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.round

/**
 * Rates against [base], and when they were fetched (epoch milliseconds).
 *
 * [updatedAt] is when the service published them, and [nextUpdateAt] when it
 * says it will publish the next ones — it does so once a day. Both are null
 * for a table cached before they were read, or a response without them.
 */
data class RateTable(
    val base: String,
    val rates: Map<String, Double>,
    val fetchedAt: Long,
    val updatedAt: Long? = null,
    val nextUpdateAt: Long? = null,
)

/**
 * Never ask again sooner than this: six hours, as the extension uses, and what
 * the privacy policy promises. Without a schedule from the service, a table is
 * also stale after it.
 */
const val RATES_MAX_AGE_MILLIS: Long = 6 * 60 * 60 * 1000

/**
 * With a schedule, the longest a table is trusted to still be the latest, in
 * case the schedule slips: a day and a half.
 */
const val RATES_LONGEST_WAIT_MILLIS: Long = 36 * 60 * 60 * 1000

/** https://open.er-api.com — free, no key, one request covers every pair. */
const val RATES_URL: String = "https://open.er-api.com/v6/latest/USD"

/**
 * Whether asking again could bring anything new.
 *
 * The service publishes once a day and says when it will next do so; before
 * then, another request would fetch the same table. So a table is fresh for
 * [maxAge] regardless, and after that for as long as the next publication is
 * still ahead — but never past [RATES_LONGEST_WAIT_MILLIS], and never when the
 * clock says it was fetched in the future.
 */
fun RateTable.isFresh(now: Long, maxAge: Long = RATES_MAX_AGE_MILLIS): Boolean {
    val age = now - fetchedAt
    if (age < 0) return false
    if (age < maxAge) return true
    val next = nextUpdateAt ?: return false
    return now < next && age < RATES_LONGEST_WAIT_MILLIS
}

fun RateTable.knows(code: String): Boolean = rates.containsKey(code)

/**
 * Converts through the base currency.
 *
 * Returns null rather than guessing when either side is unknown or the rate is
 * unusable: a converter that invents a number is worse than one that stays
 * quiet.
 */
fun RateTable.convert(amount: Double, from: String, to: String): Double? {
    if (from == to) return amount
    val fromRate = rates[from] ?: return null
    val toRate = rates[to] ?: return null
    if (fromRate <= 0.0 || toRate <= 0.0) return null
    if (!fromRate.isFinite() || !toRate.isFinite()) return null

    val converted = amount / fromRate * toRate
    return if (converted.isFinite()) converted else null
}

/**
 * Reads the rate API's response.
 *
 * Returns null on anything unexpected — a payload that says it failed, one with
 * no rates, one that is not JSON at all. The caller then keeps whatever it had.
 */
fun parseRatesResponse(json: String, fetchedAt: Long): RateTable? {
    val root: JsonObject = runCatching {
        Json.parseToJsonElement(json).jsonObject
    }.getOrNull() ?: return null

    if (root["result"]?.jsonPrimitive?.content != "success") return null
    val rates = root["rates"]?.jsonObject ?: return null

    val parsed = buildMap<String, Double> {
        for ((code, value) in rates) {
            val rate = value.jsonPrimitive.content.toDoubleOrNull() ?: continue
            if (rate > 0.0 && rate.isFinite()) put(code, rate)
        }
    }
    if (parsed.isEmpty()) return null

    val base = root["base_code"]?.jsonPrimitive?.content ?: "USD"
    // Seconds since the epoch; zero or absent means the service did not say.
    fun time(key: String): Long? =
        root[key]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)
    return RateTable(
        base = base,
        rates = parsed,
        fetchedAt = fetchedAt,
        updatedAt = time("time_last_update_unix"),
        nextUpdateAt = time("time_next_update_unix"),
    )
}

/** What [resolveRates] could offer. */
sealed interface RatesOutcome {
    /** Fetched, or cached and still within its six hours. */
    data class Fresh(val table: RateTable) : RatesOutcome

    /** The fetch failed, but there is an older table to go on with. */
    data class Stale(val table: RateTable, val reason: String) : RatesOutcome

    /** Nothing cached and nothing fetched. */
    data class Unavailable(val reason: String) : RatesOutcome
}

/**
 * Decides what to convert with.
 *
 * [fetch] is the only impure part and is injected, so the policy can be tested
 * against a network that succeeds, fails, or lies. Failing over to an old table
 * rather than to nothing is deliberate: yesterday's rate is a good answer, and
 * no answer is not.
 */
fun resolveRates(
    cached: RateTable?,
    now: Long,
    force: Boolean = false,
    maxAge: Long = RATES_MAX_AGE_MILLIS,
    fetch: () -> String,
): RatesOutcome {
    if (cached != null && cached.isFresh(now, maxAge) && !force) {
        return RatesOutcome.Fresh(cached)
    }

    val response = runCatching(fetch)
    val parsed = response.getOrNull()?.let { parseRatesResponse(it, now) }
    if (parsed != null) return RatesOutcome.Fresh(parsed)

    val reason = response.exceptionOrNull()?.message
        ?: response.getOrNull()?.let { "the rate service returned something unusable" }
        ?: "the rate service could not be reached"

    return if (cached != null) RatesOutcome.Stale(cached, reason)
    else RatesOutcome.Unavailable(reason)
}

/**
 * Currencies with no minor unit, so a converted amount never shows cents that
 * do not exist. Only those this converter knows are listed.
 */
internal val ZERO_DECIMAL = setOf("CLP", "IDR", "JPY", "KRW", "PYG", "VND")

/**
 * How many decimals a converted amount is shown with: cents below 100, none
 * above, and never more precision than the currency itself has.
 */
fun convertedDigits(amount: Double, code: String): Int = when {
    code in ZERO_DECIMAL -> 0
    abs(amount) >= 100 -> 0
    else -> 2
}

/**
 * Formats a converted amount the way the extension does: cents below 100, none
 * above, and never more precision than the currency itself has.
 *
 * Grouping separators are left to the platform — that is a locale question, and
 * this layer has no locale.
 */
fun formatConverted(amount: Double, code: String): String {
    val digits = convertedDigits(amount, code)
    // The sign is written separately: a discount of half a unit has no whole
    // part to carry it.
    val size = abs(amount)
    val rounded = if (digits == 0) {
        round(size).toLong().toString()
    } else {
        val cents = round(size * 100).toLong()
        "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
    val sign = if (amount < 0 && rounded.any { it in '1'..'9' }) "-" else ""
    return "$sign$rounded $code"
}
