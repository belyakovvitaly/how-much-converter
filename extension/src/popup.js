// Popup UI: reads and writes settings in chrome.storage.local and shows how
// old the cached rates are.
//
// Wrapped in an IIFE because currency.js is a plain (non-module) script sharing
// this page's global scope — a top-level `const CURRENCIES` here would collide
// with the one it declares and stop this whole file from parsing.
(function () {
  const { CURRENCY_NAMES, CURRENCIES, DOLLAR_CURRENCIES, flagFor, siteOf } = self.HMC;

  const els = {
    enabled: document.getElementById("enabled"),
    target: document.getElementById("targetCurrency"),
    dollar: document.getElementById("dollarAssumption"),
    display: document.getElementById("display"),
    status: document.getElementById("ratesStatus"),
    refresh: document.getElementById("refresh"),
    report: document.getElementById("report"),
    reportStatus: document.getElementById("reportStatus"),
    reportList: document.getElementById("reportList"),
    reportCount: document.getElementById("reportCount"),
    reportItems: document.getElementById("reportItems"),
    copyReports: document.getElementById("copyReports"),
    issueReports: document.getElementById("issueReports"),
    clearReports: document.getElementById("clearReports"),
    version: document.getElementById("version"),
    site: document.getElementById("site"),
    siteState: document.getElementById("siteState"),
    siteName: document.getElementById("siteName"),
    siteToggle: document.getElementById("siteToggle"),
    excludedList: document.getElementById("excludedList"),
    excludedCount: document.getElementById("excludedCount"),
    excludedItems: document.getElementById("excludedItems"),
  };

  const VERSION = chrome.runtime.getManifest().version;
  els.version.textContent = `v${VERSION}`;

  // Windows ships no flag glyphs: a flag emoji there comes out as its two
  // letters, and a row would read "US USD — US dollar". A flag is drawn in
  // colour and the fallback letters are not, so draw one and look.
  const FLAGS_DRAWN = (() => {
    try {
      const canvas = document.createElement("canvas");
      canvas.width = canvas.height = 24;
      const ctx = canvas.getContext("2d", { willReadFrequently: true });
      ctx.font = "20px sans-serif";
      ctx.textBaseline = "top";
      ctx.fillText(flagFor("EUR"), 0, 0);
      const { data } = ctx.getImageData(0, 0, 24, 24);
      for (let i = 0; i < data.length; i += 4) {
        const [r, g, b, a] = [data[i], data[i + 1], data[i + 2], data[i + 3]];
        if (a > 0 && (Math.abs(r - g) > 40 || Math.abs(g - b) > 40)) return true;
      }
      return false;
    } catch {
      return false;
    }
  })();

  function option(code) {
    const opt = document.createElement("option");
    opt.value = code;
    const flag = FLAGS_DRAWN ? flagFor(code) : "";
    opt.textContent = `${flag ? `${flag} ` : ""}${code} — ${CURRENCY_NAMES[code] || code}`;
    return opt;
  }

  for (const code of CURRENCIES) {
    els.target.appendChild(option(code));
  }

  const auto = document.createElement("option");
  auto.value = "auto";
  auto.textContent = "Detect from the page";
  els.dollar.appendChild(auto);

  for (const { label, codes } of DOLLAR_CURRENCIES) {
    const group = document.createElement("optgroup");
    group.label = label;
    for (const code of codes) group.appendChild(option(code));
    els.dollar.appendChild(group);
  }

  const DEFAULTS = {
    enabled: true,
    targetCurrency: "USD",
    // "auto" reads the currency off the page; picking a code here overrides it.
    dollarAssumption: "auto",
    // Beside the price, in its place, or on hover.
    display: "beside",
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
    els.display.value = s.display;
    showRatesStatus();
  }

  function save() {
    chrome.storage.local.set({
      enabled: els.enabled.checked,
      targetCurrency: els.target.value,
      dollarAssumption: els.dollar.value,
      display: els.display.value,
    });
  }

  els.enabled.addEventListener("change", save);
  els.target.addEventListener("change", save);
  els.dollar.addEventListener("change", save);
  els.display.addEventListener("change", save);

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
        meta.textContent =
          `${when(r.at)} · ${r.converted} converted · ${r.currency || "no currency"} — `;
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
    const { targetCurrency, dollarAssumption, display } = {
      ...DEFAULTS,
      ...(await chrome.storage.local.get(["targetCurrency", "dollarAssumption", "display"])),
    };
    const entry = {
      url: info.url,
      title: info.title,
      at: Date.now(),
      converted: info.converted,
      // What the page looked like to the extension, which is what makes a
      // report worth reading: the currency it settled on, the prices it left
      // alone, and the settings in force at the time.
      currency: info.currency || null,
      missed: info.missed || [],
      target: targetCurrency,
      dollar: dollarAssumption,
      display,
      version: VERSION,
    };
    const seen = reports.findIndex((r) => r.url === entry.url);
    if (seen >= 0) reports.splice(seen, 1);
    reports.unshift(entry);

    await chrome.storage.local.set({ reports: reports.slice(0, MAX_REPORTS) });
    els.reportStatus.textContent = seen >= 0 ? "Already on the list" : "Saved";
    renderReports();
  });

  // One report, written out the way it would be read in an issue.
  function asText(r) {
    const lines = [
      `${new Date(r.at).toISOString().slice(0, 10)}  ${r.url}`,
      `  extension ${r.version || "?"}  ·  page currency: ${r.currency || "not detected"}` +
        `  ·  converted: ${r.converted}` +
        `  ·  target: ${r.target || "?"}  ·  "$" as: ${r.dollar || "?"}` +
        // Reports saved before there was a choice were all "beside".
        `  ·  shown: ${r.display || "beside"}`,
    ];
    if (r.missed && r.missed.length) {
      lines.push(`  not converted: ${r.missed.map((m) => JSON.stringify(m)).join("  ")}`);
    }
    return lines.join("\n");
  }

  async function reportText() {
    const { reports = [] } = await chrome.storage.local.get("reports");
    return reports.map(asText).join("\n\n");
  }

  els.copyReports.addEventListener("click", async () => {
    await navigator.clipboard.writeText(await reportText());
    els.reportStatus.textContent = "Copied";
  });

  // Opens the issue form with the report already in it. Nothing leaves the
  // browser until the form is submitted, which is GitHub's button, not ours.
  const ISSUES_URL = "https://github.com/belyakovvitaly/how-much-converter/issues/new";

  els.issueReports.addEventListener("click", async () => {
    const body = `Prices that were not converted:\n\n\`\`\`\n${await reportText()}\n\`\`\`\n`;
    const url =
      `${ISSUES_URL}?title=${encodeURIComponent("Prices not converted")}` +
      `&body=${encodeURIComponent(body)}`;
    // GitHub drops a URL over about 8k; the clipboard is the way out for a
    // long list.
    if (url.length > 8000) {
      await navigator.clipboard.writeText(await reportText());
      els.reportStatus.textContent = "Too long — copied instead";
      return;
    }
    chrome.tabs.create({ url });
  });

  // --- Sites left alone ------------------------------------------------------
  //
  // A list of sites, as siteOf names them, in chrome.storage.local beside the
  // other settings — not storage.sync, which would hand Google a list of the
  // shops one reads. The content script on each page checks it and listens for
  // changes, so a site is let go of, or taken up again, without a reload.

  let currentSite = null;

  async function excludedSites() {
    const { excludedSites = [] } = await chrome.storage.local.get("excludedSites");
    return excludedSites;
  }

  async function setExcluded(site, excluded) {
    const sites = (await excludedSites()).filter((s) => s !== site);
    if (excluded) sites.push(site);
    sites.sort();
    await chrome.storage.local.set({ excludedSites: sites });
    renderSites();
  }

  async function renderSites() {
    const sites = await excludedSites();

    // Only where the content script answered: not on chrome:// pages, the
    // store, or a file, where there is nothing to switch off.
    els.site.hidden = !currentSite;
    if (currentSite) {
      const off = sites.includes(currentSite);
      els.siteState.textContent = off ? "Left alone:" : "Converting on";
      els.siteName.textContent = currentSite;
      els.siteName.parentElement.title = currentSite;
      els.siteToggle.textContent = off ? "Convert here again" : "Exclude this site";
    }

    els.excludedList.hidden = sites.length === 0;
    els.excludedCount.textContent =
      sites.length === 1 ? "1 excluded site" : `${sites.length} excluded sites`;
    els.excludedItems.replaceChildren(
      ...sites.map((site) => {
        const li = document.createElement("li");
        const name = document.createElement("span");
        name.textContent = site;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "Remove";
        remove.addEventListener("click", () => setExcluded(site, false));
        li.append(name, remove);
        return li;
      })
    );
  }

  els.siteToggle.addEventListener("click", async () => {
    if (!currentSite) return;
    setExcluded(currentSite, !(await excludedSites()).includes(currentSite));
  });

  async function findSite() {
    const info = await pageInfo();
    try {
      currentSite = (info && siteOf(new URL(info.url).hostname)) || null;
    } catch {
      currentSite = null;
    }
    renderSites();
  }

  els.clearReports.addEventListener("click", async () => {
    await chrome.storage.local.remove("reports");
    els.reportStatus.textContent = "Cleared";
    renderReports();
  });

  load();
  renderReports();
  renderSites();
  findSite();
})();
