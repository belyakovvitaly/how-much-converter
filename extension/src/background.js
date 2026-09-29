// Service worker: fetches and caches exchange rates.
//
// Rates come from https://open.er-api.com (free, no API key, CORS-enabled).
// A single USD-based table is cached; all conversions are derived from it as
// cross rates, so one request covers every currency pair.

const RATES_URL = "https://open.er-api.com/v6/latest/USD";
const CACHE_KEY = "ratesCache";
// Never ask again sooner than this — what the privacy policy promises — and,
// when the service gives no schedule, not later either.
const MIN_AGE_MS = 6 * 60 * 60 * 1000;
// With a schedule, the longest a table is trusted in case the schedule slips.
const LONGEST_WAIT_MS = 36 * 60 * 60 * 1000;

// The service publishes once a day and says when it will next; before then,
// another request fetches the same table. The same rule as isFresh in
// android/core/src/main/kotlin/converter/core/Rates.kt.
function isFresh(cache, now) {
  const age = now - cache.fetchedAt;
  if (age < 0) return false;
  if (age < MIN_AGE_MS) return true;
  return Boolean(cache.nextUpdateAt) && now < cache.nextUpdateAt && age < LONGEST_WAIT_MS;
}

// Seconds since the epoch in the response; zero or absent means it did not say.
function millis(seconds) {
  return Number.isFinite(seconds) && seconds > 0 ? seconds * 1000 : null;
}

async function readCache() {
  const { [CACHE_KEY]: cache } = await chrome.storage.local.get(CACHE_KEY);
  return cache || null;
}

async function fetchRates() {
  const res = await fetch(RATES_URL, { cache: "no-store" });
  if (!res.ok) throw new Error(`Rate API returned ${res.status}`);

  const data = await res.json();
  if (data.result !== "success" || !data.rates) {
    throw new Error("Rate API returned an unexpected payload");
  }

  const cache = {
    base: data.base_code || "USD",
    rates: data.rates, // { USD: 1, EUR: 0.92, ... }
    fetchedAt: Date.now(),
    // When the service published these, and when it will publish the next.
    updatedAt: millis(data.time_last_update_unix),
    nextUpdateAt: millis(data.time_next_update_unix),
  };
  await chrome.storage.local.set({ [CACHE_KEY]: cache });
  return cache;
}

// Returns a fresh-enough cache, fetching only when stale or missing.
// `force` bypasses the freshness check (used by the popup's Refresh button).
async function getRates(force = false) {
  const cache = await readCache();
  const fresh = cache && isFresh(cache, Date.now());

  if (fresh && !force) return cache;

  try {
    return await fetchRates();
  } catch (err) {
    // If the network call fails but we have something cached, keep using it.
    if (cache) return { ...cache, stale: true, error: String(err) };
    throw err;
  }
}

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg && msg.type === "getRates") {
    getRates(Boolean(msg.force))
      .then((cache) => sendResponse({ ok: true, cache }))
      .catch((err) => sendResponse({ ok: false, error: String(err) }));
    return true; // keep the message channel open for the async response
  }
});
