// Content script: finds prices in the page and appends a converted value.
//
// Best-effort, v1: it recognizes common currency symbols and ISO codes placed
// directly before or after a number. Ambiguous "$" is treated according to the
// "dollarAssumption" setting (default USD).

(function () {
  const { SYMBOL_TO_CODE, NUMBER, parseAmount, formatConverted } = self.HMC;

  const WRAP_CLASS = "hmc-wrap";
  const CONV_CLASS = "hmc-conv";
  const SKIP_TAGS = new Set([
    "SCRIPT", "STYLE", "NOSCRIPT", "TEXTAREA", "CODE", "PRE", "KBD",
  ]);

  let settings = {
    enabled: true,
    targetCurrency: "USD",
    dollarAssumption: "USD",
  };
  let rates = null; // { base, rates: { USD: 1, ... } }
  let observer = null;

  // Build the match regex from the known symbols and codes.
  function buildRegex() {
    const symbols = Object.keys(SYMBOL_TO_CODE)
      .sort((a, b) => b.length - a.length) // longest first
      .map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
      .join("|");
    const codeAlt = "[A-Z]{3}";
    const cur = `(?:${symbols}|${codeAlt})`;
    // <currency><number>  or  <number><currency>
    return new RegExp(
      `(${cur})\\s?(${NUMBER})|(${NUMBER})\\s?(${cur})`,
      "gu"
    );
  }

  const RE = buildRegex();

  function resolveCode(token) {
    if (!token) return null;
    if (SYMBOL_TO_CODE[token]) {
      const code = SYMBOL_TO_CODE[token];
      return code === "USD" && token === "$" ? settings.dollarAssumption : code;
    }
    const up = token.toUpperCase();
    return /^[A-Z]{3}$/.test(up) && rates.rates[up] ? up : null;
  }

  function convert(amount, fromCode) {
    const to = settings.targetCurrency;
    if (!fromCode || fromCode === to) return null;
    const from = rates.rates[fromCode];
    const dest = rates.rates[to];
    if (!from || !dest) return null;
    return (amount / from) * dest;
  }

  // Replaces the matched slice of a text node with a wrapper element that keeps
  // the original text and adds the converted value.
  function annotateNode(node) {
    const text = node.nodeValue;
    RE.lastIndex = 0;
    let match;
    const pieces = [];
    let cursor = 0;

    while ((match = RE.exec(text)) !== null) {
      const [full] = match;
      const curToken = match[1] || match[4];
      const numToken = match[2] || match[3];
      const amount = parseAmount(numToken);
      const fromCode = resolveCode(curToken);
      const converted = amount != null ? convert(amount, fromCode) : null;
      if (converted == null) continue;

      pieces.push(document.createTextNode(text.slice(cursor, match.index)));

      const wrap = document.createElement("span");
      wrap.className = WRAP_CLASS;
      wrap.appendChild(document.createTextNode(full));

      const conv = document.createElement("span");
      conv.className = CONV_CLASS;
      conv.textContent = ` (≈ ${formatConverted(converted, settings.targetCurrency)})`;
      wrap.appendChild(conv);

      pieces.push(wrap);
      cursor = match.index + full.length;
    }

    if (!pieces.length) return;
    pieces.push(document.createTextNode(text.slice(cursor)));

    const frag = document.createDocumentFragment();
    pieces.forEach((p) => frag.appendChild(p));
    node.parentNode.replaceChild(frag, node);
  }

  function shouldSkip(el) {
    if (!el) return true;
    if (SKIP_TAGS.has(el.tagName)) return true;
    if (el.isContentEditable) return true;
    if (el.closest(`.${WRAP_CLASS}`)) return true;
    return false;
  }

  function walk(root) {
    if (!rates || !settings.enabled) return;

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.nodeValue || !node.nodeValue.trim()) {
          return NodeFilter.FILTER_REJECT;
        }
        return shouldSkip(node.parentElement)
          ? NodeFilter.FILTER_REJECT
          : NodeFilter.FILTER_ACCEPT;
      },
    });

    const targets = [];
    let n;
    while ((n = walker.nextNode())) targets.push(n);
    targets.forEach(annotateNode);
  }

  // Undo every annotation so a settings change can be re-applied cleanly.
  function unwrapAll() {
    document.querySelectorAll(`.${WRAP_CLASS}`).forEach((wrap) => {
      const conv = wrap.querySelector(`.${CONV_CLASS}`);
      if (conv) conv.remove();
      wrap.replaceWith(document.createTextNode(wrap.textContent));
    });
  }

  const debounce = (fn, ms) => {
    let t;
    return (...a) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...a), ms);
    };
  };

  const rescan = debounce(() => {
    unwrapAll();
    walk(document.body);
  }, 400);

  function startObserver() {
    if (observer) return;
    observer = new MutationObserver((mutations) => {
      for (const m of mutations) {
        if (m.addedNodes.length) {
          rescan();
          return;
        }
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  function getRates(force = false) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage({ type: "getRates", force }, (resp) => {
        if (resp && resp.ok) resolve(resp.cache);
        else resolve(null);
      });
    });
  }

  async function init() {
    const stored = await chrome.storage.local.get([
      "enabled",
      "targetCurrency",
      "dollarAssumption",
    ]);
    settings = { ...settings, ...stored };
    if (settings.enabled === undefined) settings.enabled = true;

    rates = await getRates();
    if (!rates) return;

    walk(document.body);
    startObserver();
  }

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== "local") return;
    let touched = false;
    for (const key of ["enabled", "targetCurrency", "dollarAssumption"]) {
      if (changes[key]) {
        settings[key] = changes[key].newValue;
        touched = true;
      }
    }
    if (!touched) return;
    if (settings.enabled) rescan();
    else unwrapAll();
  });

  init();
})();
