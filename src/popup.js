// Popup UI: reads and writes settings in chrome.storage.local and shows how
// old the cached rates are.

const { CURRENCIES } = self.HMC;

const els = {
  enabled: document.getElementById("enabled"),
  target: document.getElementById("targetCurrency"),
  dollar: document.getElementById("dollarAssumption"),
  status: document.getElementById("ratesStatus"),
  refresh: document.getElementById("refresh"),
};

// Populate the target-currency dropdown.
for (const code of CURRENCIES) {
  const opt = document.createElement("option");
  opt.value = code;
  opt.textContent = code;
  els.target.appendChild(opt);
}

const DEFAULTS = {
  enabled: true,
  targetCurrency: "USD",
  dollarAssumption: "USD",
};

function ago(ts) {
  const mins = Math.round((Date.now() - ts) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.round(mins / 60);
  return `${hrs} h ago`;
}

async function showRatesStatus() {
  const { ratesCache } = await chrome.storage.local.get("ratesCache");
  if (!ratesCache) {
    els.status.textContent = "Rates: not loaded yet";
    return;
  }
  const flag = ratesCache.stale ? " (stale)" : "";
  els.status.textContent = `Rates updated ${ago(ratesCache.fetchedAt)}${flag}`;
}

async function load() {
  const stored = await chrome.storage.local.get(Object.keys(DEFAULTS));
  const s = { ...DEFAULTS, ...stored };
  els.enabled.checked = s.enabled;
  els.target.value = s.targetCurrency;
  els.dollar.value = s.dollarAssumption;
  showRatesStatus();
}

function save() {
  chrome.storage.local.set({
    enabled: els.enabled.checked,
    targetCurrency: els.target.value,
    dollarAssumption: els.dollar.value,
  });
}

els.enabled.addEventListener("change", save);
els.target.addEventListener("change", save);
els.dollar.addEventListener("change", save);

els.refresh.addEventListener("click", () => {
  els.status.textContent = "Rates: refreshing…";
  chrome.runtime.sendMessage({ type: "getRates", force: true }, () => {
    showRatesStatus();
  });
});

load();
