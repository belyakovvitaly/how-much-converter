// Content script: finds prices in the page and appends a converted value.
//
// Best-effort, v1: it recognizes common currency symbols and ISO codes placed
// directly before or after a number. Ambiguous "$" is treated according to the
// "dollarAssumption" setting (default USD).
//
// Two passes are needed. Most pages keep a whole price in one text node, but
// plenty of stores split it across sibling elements — <span>Gs </span><span>
// 23.000</span> — where no single text node holds a complete price.

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

  // Currency alternation: every known symbol (longest first, so "R$" wins over
  // "$") plus any three-letter ISO code.
  const CUR = `(?:${Object.keys(SYMBOL_TO_CODE)
    .sort((a, b) => b.length - a.length)
    .map((sym) => sym.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("|")}|[A-Z]{3})`;

  // <currency><number> or <number><currency>, anywhere in a run of text.
  const RE = new RegExp(
    `(${CUR})\\s?(${NUMBER})|(${NUMBER})\\s?(${CUR})`,
    "gu"
  );

  // The same, but matching a whole string — used on elements whose entire text
  // is one price.
  const FULL_RE = new RegExp(
    `^(?:(${CUR})\\s?(${NUMBER})|(${NUMBER})\\s?(${CUR}))$`,
    "u"
  );

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

  function conversionNode(converted) {
    const conv = document.createElement("span");
    conv.className = CONV_CLASS;
    conv.textContent = ` (≈ ${formatConverted(converted, settings.targetCurrency)})`;
    return conv;
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

      wrap.appendChild(conversionNode(converted));

      pieces.push(wrap);
      cursor = match.index + full.length;
    }

    if (!pieces.length) return;
    pieces.push(document.createTextNode(text.slice(cursor)));

    const frag = document.createDocumentFragment();
    pieces.forEach((p) => frag.appendChild(p));
    node.parentNode.replaceChild(frag, node);
  }

  const SKIP_SELECTOR = [...SKIP_TAGS].join(",");

  function shouldSkip(el) {
    if (!el) return true;
    if (el.isContentEditable) return true;
    // `closest` rather than a tag test: a price inside <pre><span> is still
    // inside a <pre>, and our own markup must never be re-scanned.
    return Boolean(el.closest(`${SKIP_SELECTOR},.${WRAP_CLASS},.${CONV_CLASS}`));
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

  // Second pass: elements whose entire text is a single price, assembled from
  // more than one child. Only shallow elements qualify — one to three children,
  // none of which has children of its own — which is what the split-price
  // markup in the wild looks like and keeps this from reading textContent off
  // large subtrees.
  function annotateSplit(root) {
    // Reverse document order puts descendants before their ancestors, so the
    // innermost element around a price claims it and the outer ones then see
    // the annotation already inside and leave it alone.
    const all = root.querySelectorAll("*");
    for (let i = all.length - 1; i >= 0; i--) {
      const el = all[i];
      const kids = el.children;
      if (kids.length < 1 || kids.length > 3) continue;
      let shallow = true;
      for (const kid of kids) {
        if (kid.children.length) { shallow = false; break; }
      }
      if (!shallow || shouldSkip(el)) continue;
      if (el.querySelector(`.${CONV_CLASS}`)) continue;

      const text = el.textContent;
      if (text.length > 40) continue;
      const match = FULL_RE.exec(text.replace(/\s+/gu, " ").trim());
      if (!match) continue;

      const amount = parseAmount(match[2] || match[3]);
      const converted =
        amount == null ? null : convert(amount, resolveCode(match[1] || match[4]));
      if (converted == null) continue;

      el.appendChild(conversionNode(converted));
    }
  }

  // Undo every annotation so a settings change can be re-applied cleanly.
  function unwrapAll() {
    document.querySelectorAll(`.${CONV_CLASS}`).forEach((el) => el.remove());
    document.querySelectorAll(`.${WRAP_CLASS}`).forEach((wrap) => {
      wrap.replaceWith(document.createTextNode(wrap.textContent));
    });
  }

  // Runs both passes, then drops the mutation records our own edits produced so
  // the observer does not treat them as a page change and loop forever.
  function apply(root = document.body) {
    if (!rates || !settings.enabled) return;
    walk(root);
    annotateSplit(root);
    if (observer) observer.takeRecords();
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
    apply();
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

    startObserver();
    apply();
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
