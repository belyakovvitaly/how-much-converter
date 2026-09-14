// Popup UI: reads and writes settings in chrome.storage.local and shows how
// old the cached rates are.
//
// Wrapped in an IIFE because currency.js is a plain (non-module) script sharing
// this page's global scope — a top-level `const CURRENCIES` here would collide
// with the one it declares and stop this whole file from parsing.
(function () {
  const { CURRENCY_NAMES, CURRENCIES, DOLLAR_CURRENCIES } = self.HMC;

  const els = {
    enabled: document.getElementById("enabled"),
    target: document.getElementById("targetCurrency"),
    dollar: document.getElementById("dollarAssumption"),
    status: document.getElementById("ratesStatus"),
    refresh: document.getElementById("refresh"),
    report: document.getElementById("report"),
    reportStatus: document.getElementById("reportStatus"),
    reportList: document.getElementById("reportList"),
    reportCount: document.getElementById("reportCount"),
    reportItems: document.getElementById("reportItems"),
    copyReports: document.getElementById("copyReports"),
    clearReports: document.getElementById("clearReports"),
  };

  function option(code) {
    const opt = document.createElement("option");
    opt.value = code;
    opt.textContent = `${code} — ${CURRENCY_NAMES[code] || code}`;
    return opt;
  }

  for (const code of CURRENCIES) {
    els.target.appendChild(option(code));
  }

  for (const { label, codes } of DOLLAR_CURRENCIES) {
    const group = document.createElement("optgroup");
    group.label = label;
    for (const code of codes) group.appendChild(option(code));
    els.dollar.appendChild(group);
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

  // --- Reporting pages where conversion did not work -----------------------
  //
  // Reports are kept in chrome.storage.local and go nowhere else: this browser
  // profile is the only place they exist. "Copy all" is how they get out.

  const MAX_REPORTS = 200;

  // Ask the content script rather than reading tab.url, which would need the
  // "tabs" permission. The script is already running on the page being reported.
  async function pageInfo() {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab) return null;
    try {
      return (await chrome.tabs.sendMessage(tab.id, { type: "pageInfo" })) || null;
    } catch {
      return null; // no content script here (chrome:// pages, the store, PDFs)
    }
  }

  function when(ts) {
    return new Date(ts).toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }

  async function renderReports() {
    const { reports = [] } = await chrome.storage.local.get("reports");
    els.reportList.hidden = reports.length === 0;
    if (!reports.length) return;

    els.reportCount.textContent =
      reports.length === 1 ? "1 reported page" : `${reports.length} reported pages`;
    els.reportItems.replaceChildren(
      ...reports.map((r) => {
        const li = document.createElement("li");
        const meta = document.createElement("span");
        meta.className = "when";
        meta.textContent = `${when(r.at)} · ${r.converted} converted — `;
        li.append(meta, document.createTextNode(r.url));
        return li;
      })
    );
  }

  els.report.addEventListener("click", async () => {
    const info = await pageInfo();
    if (!info) {
      els.reportStatus.textContent = "Can't read this page";
      return;
    }

    const { reports = [] } = await chrome.storage.local.get("reports");
    const entry = {
      url: info.url,
      title: info.title,
      at: Date.now(),
      converted: info.converted,
    };
    const seen = reports.findIndex((r) => r.url === entry.url);
    if (seen >= 0) reports.splice(seen, 1);
    reports.unshift(entry);

    await chrome.storage.local.set({ reports: reports.slice(0, MAX_REPORTS) });
    els.reportStatus.textContent = seen >= 0 ? "Already on the list" : "Saved";
    renderReports();
  });

  els.copyReports.addEventListener("click", async () => {
    const { reports = [] } = await chrome.storage.local.get("reports");
    const text = reports
      .map((r) => `${new Date(r.at).toISOString().slice(0, 10)}\t${r.converted}\t${r.url}`)
      .join("\n");
    await navigator.clipboard.writeText(text);
    els.reportStatus.textContent = "Copied";
  });

  els.clearReports.addEventListener("click", async () => {
    await chrome.storage.local.remove("reports");
    els.reportStatus.textContent = "Cleared";
    renderReports();
  });

  load();
  renderReports();
})();
