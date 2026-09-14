// Service worker: fetches and caches exchange rates.
//
// Rates come from https://open.er-api.com (free, no API key, CORS-enabled).
// A single USD-based table is cached; all conversions are derived from it as
// cross rates, so one request covers every currency pair.

const RATES_URL = "https://open.er-api.com/v6/latest/USD";
const CACHE_KEY = "ratesCache";
const MAX_AGE_MS = 6 * 60 * 60 * 1000; // 6 hours

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
  };
  await chrome.storage.local.set({ [CACHE_KEY]: cache });
  return cache;
}

// Returns a fresh-enough cache, fetching only when stale or missing.
// `force` bypasses the freshness check (used by the popup's Refresh button).
async function getRates(force = false) {
  const cache = await readCache();
  const fresh = cache && Date.now() - cache.fetchedAt < MAX_AGE_MS;

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
