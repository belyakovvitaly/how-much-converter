// Content script: finds prices in the page and shows each one converted —
// beside it, in its place, or on hover, as the popup says.
//
// Best-effort, v1: it recognizes common currency symbols and ISO codes placed
// directly before or after a number. Ambiguous "$" is treated according to the
// "dollarAssumption" setting (default USD).
//
// Two passes are needed. Most pages keep a whole price in one text node, but
// plenty of stores split it across sibling elements — <span>Gs </span><span>
// 23.000</span> — where no single text node holds a complete price.

(function () {
  const {
    buildPriceRegExp,
    resolveSymbol,
    parseAmount,
    formatConverted,
    detectPageCurrency,
    siteOf,
    collapsePriceText,
    matchIsOnOneLine,
  } = self.HMC;

  const WRAP_CLASS = "hmc-wrap";
  const CONV_CLASS = "hmc-conv";
  // The shop's own text, lifted into an element of ours so it can be hidden.
  const ORIG_CLASS = "hmc-orig";
  // On every converted price, whatever the display: what it converts to. This
  // is how a price is known to be taken, counted, and shown on hover.
  const HOST_ATTR = "data-hmc";
  const MARKED = `[${HOST_ATTR}]`;
  // "On hover": a price that shows its conversion when pointed at.
  const TIP_ATTR = "data-hmc-tip";
  // "Instead of the price": the shop's own price, hidden by content.css.
  const HIDDEN_ATTR = "data-hmc-hidden";
  const DISPLAYS = new Set(["beside", "replace", "hover"]);
  const SKIP_TAGS = new Set([
    "SCRIPT", "STYLE", "NOSCRIPT", "TEXTAREA", "CODE", "PRE", "KBD",
  ]);

  let settings = {
    enabled: true,
    targetCurrency: "USD",
    dollarAssumption: "auto",
    // Where the conversion goes: "beside" the price, "replace" it, or shown on
    // "hover" only.
    display: "beside",
    // Sites, as siteOf names them, the reader asked to be left alone.
    excludedSites: [],
  };
  let rates = null; // { base, rates: { USD: 1, ... } }
  let observer = null;
  let pageCurrency = null; // what "$" or "¥" means *here*; null = unknown

  const RE = buildPriceRegExp();

  // This page's site, once: it is what the exclusion list holds.
  const SITE = siteOf(location.hostname);

  // Switched on, and not on a site the reader switched it off for.
  function active() {
    const excluded = Array.isArray(settings.excludedSites) && settings.excludedSites.includes(SITE);
    return settings.enabled && !excluded;
  }

  // An ISO code, but only one we hold a rate for: "USD" is a currency here,
  // "EUR" is, "SKU" is not.
  function knownCode(value) {
    const up = String(value || "").trim().toUpperCase();
    return /^[A-Z]{3}$/.test(up) && rates.rates[up] ? up : null;
  }

  function resolveCode(token) {
    return resolveSymbol(token, {
      pageCurrency,
      dollarAssumption: settings.dollarAssumption,
      isKnownCode: knownCode,
    });
  }

  function convert(amount, fromCode) {
    const to = settings.targetCurrency;
    if (!fromCode || fromCode === to) return null;
    const from = rates.rates[fromCode];
    const dest = rates.rates[to];
    if (!from || !dest) return null;
    return (amount / from) * dest;
  }

  // The conversion is a note on a price, not a second price. Appended inside a
  // shop's price element it inherits that element's display size, and on
  // mercadolibre.com.ar — 32px digits in a flex row that does not wrap — the
  // note then had no room and broke in the middle: "(≈" on one line, "14,99 $)"
  // on the next. So it is never broken inside, and never set larger than the
  // page's ordinary text; a price in running text is that size anyway and looks
  // the same as before.
  const NOTE_MIN_PX = 12;
  const NOTE_MAX_PX = 16;
  // The last size tried before a note that still does not fit is let wrap.
  const NOTE_TIGHT_PX = 11;

  function noteSize() {
    const body = parseFloat(getComputedStyle(document.body).fontSize);
    if (!Number.isFinite(body)) return NOTE_MAX_PX;
    return Math.min(NOTE_MAX_PX, Math.max(NOTE_MIN_PX, body));
  }

  function display() {
    return DISPLAYS.has(settings.display) ? settings.display : "beside";
  }

  function label(converted) {
    return `≈ ${formatConverted(converted, settings.targetCurrency)}`;
  }

  function conversionNode(text, size) {
    const conv = document.createElement("span");
    conv.className = CONV_CLASS;
    conv.style.fontSize = `min(1em, ${size}px)`;
    // The space stays outside the unbreakable part, so running text can still
    // wrap between the price and its note.
    const note = document.createElement("span");
    note.style.whiteSpace = "nowrap";
    note.textContent = `(${text})`;
    conv.append(" ", note);
    return conv;
  }

  // In place of the price, the conversion is the price: the shop's size, no
  // brackets, and the original a hover away in its title.
  function replacementNode(text, original) {
    const conv = document.createElement("span");
    conv.className = CONV_CLASS;
    conv.title = original;
    const inner = document.createElement("span");
    inner.style.whiteSpace = "nowrap";
    inner.textContent = text;
    conv.append(inner);
    return conv;
  }

  // Hides what an element shows without taking it out of the page: a shop's
  // script may still hold its nodes and update them. Elements are hidden by
  // attribute rather than inline style, which would overwrite the shop's own;
  // bare text cannot be styled, so it is wrapped.
  function hideContents(host) {
    for (const child of [...host.childNodes]) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        child.setAttribute(HIDDEN_ATTR, "");
      } else if (child.nodeType === Node.TEXT_NODE && child.nodeValue.trim()) {
        const orig = document.createElement("span");
        orig.className = ORIG_CLASS;
        orig.setAttribute(HIDDEN_ATTR, "");
        child.replaceWith(orig);
        orig.appendChild(child);
      }
    }
  }

  // Marks `host` — our wrapper around a price, or the shop's element holding a
  // split one — as converted, and shows the conversion the way the settings
  // ask. A split host may carry a unit after the price ("340 руб/шт"); in place
  // of the price that goes too, since it is not ours to separate.
  function annotate(host, converted, original, size, notes) {
    const text = label(converted);
    host.setAttribute(HOST_ATTR, text);
    const mode = display();
    if (mode === "hover") {
      host.setAttribute(TIP_ATTR, "");
      return;
    }
    let conv;
    if (mode === "replace") {
      hideContents(host);
      conv = replacementNode(text, original);
    } else {
      conv = conversionNode(text, size);
    }
    host.appendChild(conv);
    notes.push(conv);
  }

  // How far up to look for the box a note has to fit in. The price element
  // itself is no use: in a flex row it grows to hold whatever is put in it. The
  // card around it does not, and that is two or three levels up.
  const FIT_ANCESTORS = 4;

  function overflows(conv) {
    const r = conv.getBoundingClientRect();
    // Not drawn: an off-screen screen-reader copy, say. Nothing to fit.
    if (r.width <= 1 || r.height <= 1) return false;
    let el = conv.parentElement;
    for (let i = 0; el && el !== document.body && i < FIT_ANCESTORS; i++) {
      if (r.right > el.getBoundingClientRect().right + 0.5) return true;
      el = el.parentElement;
    }
    return false;
  }

  // Where a note still does not fit — the narrowest cards — it is made smaller
  // once more, and only then allowed to wrap, as it always used to. Every step
  // measures all the notes first and writes afterwards, so the page is laid out
  // once per step rather than once per price.
  function fitNotes(notes) {
    // In place of the price there is no neighbour to space from, and the size
    // is the price's own: all that is left is to let it wrap if it must.
    if (display() === "replace") {
      for (const conv of notes.filter(overflows)) conv.lastChild.style.whiteSpace = "normal";
      return;
    }
    // Inside a flex or grid row the leading space collapses away and the note
    // is drawn touching the digits: "22.619(≈". A margin does what the space
    // cannot there.
    const spaced = notes.filter((conv) => {
      const display = getComputedStyle(conv.parentElement).display;
      return /flex|grid/.test(display);
    });
    for (const conv of spaced) conv.style.marginInlineStart = "0.25em";

    let tight = notes.filter(overflows);
    for (const conv of tight) conv.style.fontSize = `min(1em, ${NOTE_TIGHT_PX}px)`;
    tight = tight.filter(overflows);
    for (const conv of tight) conv.lastChild.style.whiteSpace = "normal";
  }

  // Replaces the matched slice of a text node with a wrapper element that keeps
  // the original text and adds the converted value.
  function annotateNode(node, size, notes) {
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
      annotate(wrap, converted, full, size, notes);

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
    return Boolean(el.closest(`${SKIP_SELECTOR},.${WRAP_CLASS},.${CONV_CLASS},${MARKED}`));
  }

  function walk(root, size, notes) {
    if (!rates || !active()) return;

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
    for (const t of targets) annotateNode(t, size, notes);
  }

  // A split price lives in a small element: a handful of nodes holding one
  // number, one currency, and maybe a unit. These bounds keep the pass off
  // large subtrees, whose textContent would be expensive to read.
  const SPLIT_MAX_CHILDREN = 3;
  const SPLIT_MAX_DESCENDANTS = 6;
  const SPLIT_MAX_TEXT = 40;
  // Before whitespace is collapsed; only there to bound the work above.
  const SPLIT_MAX_RAW_TEXT = 400;

  // Second pass: small elements holding exactly one price that no single text
  // node contains on its own — <span>Gs</span><span>23.000</span>, or a number
  // followed by a currency in a nested span. The price need not be the whole
  // text: shops append units to it ("340 руб/шт").
  function annotateSplit(root, size, notes) {
    // Reverse document order puts descendants before their ancestors, so the
    // innermost element around a price claims it and the outer ones then find
    // it taken and leave it alone.
    const all = root.querySelectorAll("*");
    const claimed = new WeakSet();
    const pending = [];

    for (let i = all.length - 1; i >= 0; i--) {
      const el = all[i];
      if (el.childElementCount < 1 || el.childElementCount > SPLIT_MAX_CHILDREN) continue;
      if (el.getElementsByTagName("*").length > SPLIT_MAX_DESCENDANTS) continue;
      if (shouldSkip(el)) continue;
      // Taken by a descendant in this pass, or annotated by the first one.
      if (claimed.has(el) || el.querySelector(MARKED)) continue;

      // Cheap bound first: markup indentation inflates textContent, so the real
      // cap has to be measured after collapsing whitespace, not before.
      if (el.textContent.length > SPLIT_MAX_RAW_TEXT) continue;
      const { text, map } = collapsePriceText(el);
      if (text.length > SPLIT_MAX_TEXT) continue;

      // Exactly one price, or we cannot say which the appended value refers to.
      const matches = [...text.matchAll(RE)];
      if (matches.length !== 1) continue;

      const [match] = matches;
      // ...and drawn as one unbroken run, rather than two neighbouring
      // elements that textContent ran together.
      if (!matchIsOnOneLine(map, match.index, match.index + match[0].length)) {
        continue;
      }

      const amount = parseAmount(match[2] || match[3]);
      const converted =
        amount == null ? null : convert(amount, resolveCode(match[1] || match[4]));
      if (converted == null) continue;

      pending.push([el, converted, match[0]]);
      for (let p = el; p; p = p.parentElement) claimed.add(p);
    }

    // Nothing above writes to the page. Everything that does happens here, so
    // that reading innerText — which forces layout — never lands between two
    // edits and makes the browser re-lay-out the page for each one.
    for (const [el, converted, original] of pending) {
      annotate(el, converted, original, size, notes);
    }
  }

  // A few price-shaped strings the page still shows unconverted, to go with a
  // report. This is the part a report cannot be written without: knowing that
  // a page failed says nothing, knowing it says "Bs21,50" says everything.
  const MISSED_SAMPLES = 5;

  function missedPrices() {
    const out = [];
    const seen = new Set();
    const all = document.body.querySelectorAll("*");

    for (let i = 0; i < all.length && out.length < MISSED_SAMPLES; i++) {
      const el = all[i];
      if (el.getElementsByTagName("*").length > SPLIT_MAX_DESCENDANTS) continue;
      if (shouldSkip(el)) continue;
      // Already handled, here or in a child.
      if (el.matches(MARKED) || el.querySelector(MARKED)) continue;

      const text = el.textContent.replace(/\s+/gu, " ").trim();
      if (text.length < 2 || text.length > SPLIT_MAX_TEXT) continue;
      if (!/\d/.test(text)) continue;
      // A bare number, a percentage or a date is not a price waiting to be
      // recognized; something that is neither digit nor punctuation is.
      if (!/[^\d\s.,:%/()\-+]/u.test(text)) continue;
      if (seen.has(text)) continue;

      seen.add(text);
      out.push(text);
    }
    return out;
  }

  // Undo every annotation so a settings change can be re-applied cleanly.
  function unwrapAll() {
    hideTip();
    document.querySelectorAll(`.${CONV_CLASS}`).forEach((el) => el.remove());
    document.querySelectorAll(`.${WRAP_CLASS}`).forEach((wrap) => {
      wrap.replaceWith(document.createTextNode(wrap.textContent));
    });
    // The shop's own text node goes back, not a copy of it.
    document.querySelectorAll(`.${ORIG_CLASS}`).forEach((orig) => {
      orig.replaceWith(...orig.childNodes);
    });
    for (const attr of [HOST_ATTR, TIP_ATTR, HIDDEN_ATTR]) {
      document.querySelectorAll(`[${attr}]`).forEach((el) => el.removeAttribute(attr));
    }
  }

  // --- "On hover" -----------------------------------------------------------
  //
  // One tooltip for the whole page, made on first use and kept outside <body>,
  // where the observer is not looking. It is our own element name, so a shop's
  // stylesheet has no rule that reaches it; content.css draws it.
  let tip = null;

  function showTip(host) {
    if (!tip) {
      tip = document.createElement("hmc-tip");
      tip.setAttribute("role", "tooltip");
      document.documentElement.appendChild(tip);
    }
    tip.textContent = host.getAttribute(HOST_ATTR);
    tip.hidden = false;
    // Above the price, or below it where there is no room; never off screen.
    const gap = 6;
    const r = host.getBoundingClientRect();
    const t = tip.getBoundingClientRect();
    let top = r.top - t.height - gap;
    if (top < gap) top = r.bottom + gap;
    const left = Math.max(
      gap,
      Math.min(r.left + (r.width - t.width) / 2, innerWidth - t.width - gap)
    );
    tip.style.transform = `translate(${Math.round(left)}px, ${Math.round(top)}px)`;
  }

  function hideTip() {
    if (tip) tip.hidden = true;
  }

  document.addEventListener("mouseover", (e) => {
    const host = e.target instanceof Element ? e.target.closest(`[${TIP_ATTR}]`) : null;
    if (host) showTip(host);
    else hideTip();
  });
  // Out of the window altogether.
  document.addEventListener("mouseout", (e) => {
    if (!e.relatedTarget) hideTip();
  });
  // It is placed once, against where the price was; a scroll leaves it behind.
  addEventListener("scroll", hideTip, { capture: true, passive: true });

  // Runs both passes, then drops the mutation records our own edits produced so
  // the observer does not treat them as a page change and loop forever.
  function apply(root = document.body) {
    if (!rates || !active()) return;
    const size = noteSize();
    const notes = [];
    walk(root, size, notes);
    annotateSplit(root, size, notes);
    fitNotes(notes);
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
    // A shop that renders its offer client-side has no JSON-LD to read on the
    // first pass, so keep looking until something answers.
    if (!pageCurrency) {
      pageCurrency = detectPageCurrency(document, location, knownCode);
    }
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
      "display",
      "excludedSites",
    ]);
    settings = { ...settings, ...stored };
    if (settings.enabled === undefined) settings.enabled = true;

    rates = await getRates();
    if (!rates) return;

    pageCurrency = detectPageCurrency(document, location, knownCode);

    startObserver();
    apply();
  }

  // The popup asks the page about itself when you report it. Answering from the
  // content script means the popup needs no permission to read tab URLs — this
  // script is already running here.
  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg && msg.type === "pageInfo") {
      sendResponse({
        url: location.href,
        title: document.title,
        converted: document.querySelectorAll(MARKED).length,
        currency: pageCurrency,
        missed: missedPrices(),
      });
    }
  });

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== "local") return;
    let touched = false;
    const keys = ["enabled", "targetCurrency", "dollarAssumption", "display", "excludedSites"];
    for (const key of keys) {
      if (changes[key]) {
        settings[key] = changes[key].newValue;
        touched = true;
      }
    }
    if (!touched) return;
    if (active()) rescan();
    else unwrapAll();
  });

  init();
})();
