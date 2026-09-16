// Shared currency data and parsing helpers.
// Loaded before content.js (see manifest) and used by popup.js via a copy of
// the CURRENCIES list.

// Symbols that map unambiguously (or ambiguously but with a sensible default)
// to an ISO 4217 code. Longer keys are matched before shorter ones so that
// "R$" wins over "$" and "CA$" wins over "$".
const SYMBOL_TO_CODE = {
  "US$": "USD",
  "CA$": "CAD",
  "C$": "CAD",
  "A$": "AUD",
  "AU$": "AUD",
  "HK$": "HKD",
  "NZ$": "NZD",
  "S$": "SGD",
  "NT$": "TWD",
  "R$": "BRL",
  "MX$": "MXN",
  "AR$": "ARS",
  "CL$": "CLP",
  "COL$": "COP",
  "U$S": "USD",
  "$U": "UYU",
  "RD$": "DOP",
  "B/.": "PAB",
  "S/": "PEN",
  "₲": "PYG",
  "Gs.": "PYG",
  "Gs": "PYG",
  "€": "EUR",
  "£": "GBP",
  "¥": "JPY",
  "₩": "KRW",
  "₽": "RUB",
  // Written-out names, for shops that spell the currency instead of signing it.
  "рублей": "RUB",
  "рубля": "RUB",
  "руб.": "RUB",
  "руб": "RUB",
  "гривен": "UAH",
  "грн": "UAH",
  "₴": "UAH",
  "₸": "KZT",
  "тг": "KZT",
  "₾": "GEL",
  "₺": "TRY",
  "₹": "INR",
  "₪": "ILS",
  "₱": "PHP",
  "₡": "CRC",
  "₦": "NGN",
  "฿": "THB",
  "zł": "PLN",
  "Kč": "CZK",
  // Codes a shop writes instead of a sign. Turkey says TL far more often than
  // ₺, Hungary Ft, Indonesia Rp, Malaysia RM.
  "TL": "TRY",
  "Ft": "HUF",
  "Rp": "IDR",
  "RM": "MYR",
  "дин.": "RSD",
  "дин": "RSD",
  // Scripts that do not space their words. These must stay out of the fenced
  // group below or they would never match: the character after them is usually
  // another letter.
  "₫": "VND",
  "đ": "VND",
  "円": "JPY",
  "원": "KRW",
  "บาท": "THB",
  // Bolivia and Venezuela both abbreviate to "Bs" — boliviano and bolívar.
  "Bs.": "BOB",
  "Bs": "BOB",
  "֏": "AMD",
  // Armenia writes the dram out: "դր." at home, "dr" on an English page.
  "դր.": "AMD",
  "դր": "AMD",
  "dr": "AMD",
  // Sweden's way of saying "kronor, and no öre": 429:- is 429 kr. There is no
  // currency in the text at all, so only the page's own country can say which
  // krona it is.
  ":-": "SEK",
  // Ambiguous, and so only resolved when the page says which country it is.
  "R": "ZAR",
  "元": "CNY",
  "kr": "SEK",
  "lei": "RON",
  "Rs.": "INR",
  "Rs": "INR",
  "$": "USD", // ambiguous; overridable via the "dollarAssumption" setting
};

// Every currency the extension knows about, with a display name for the
// popup's dropdowns.
const CURRENCY_NAMES = {
  AED: "UAE dirham",
  AMD: "Armenian dram",
  ARS: "Argentine peso",
  AUD: "Australian dollar",
  BGN: "Bulgarian lev",
  BOB: "Bolivian boliviano",
  BRL: "Brazilian real",
  CAD: "Canadian dollar",
  CHF: "Swiss franc",
  CLP: "Chilean peso",
  CNY: "Chinese yuan",
  COP: "Colombian peso",
  CRC: "Costa Rican colón",
  CUP: "Cuban peso",
  CZK: "Czech koruna",
  DKK: "Danish krone",
  DOP: "Dominican peso",
  EGP: "Egyptian pound",
  EUR: "Euro",
  GBP: "Pound sterling",
  GEL: "Georgian lari",
  GTQ: "Guatemalan quetzal",
  HKD: "Hong Kong dollar",
  HUF: "Hungarian forint",
  IDR: "Indonesian rupiah",
  ILS: "Israeli new shekel",
  INR: "Indian rupee",
  JPY: "Japanese yen",
  KRW: "South Korean won",
  KZT: "Kazakhstani tenge",
  MXN: "Mexican peso",
  MYR: "Malaysian ringgit",
  NGN: "Nigerian naira",
  NOK: "Norwegian krone",
  NZD: "New Zealand dollar",
  PAB: "Panamanian balboa",
  PEN: "Peruvian sol",
  PHP: "Philippine peso",
  PLN: "Polish złoty",
  PYG: "Paraguayan guaraní",
  RON: "Romanian leu",
  RSD: "Serbian dinar",
  RUB: "Russian ruble",
  SAR: "Saudi riyal",
  SEK: "Swedish krona",
  SGD: "Singapore dollar",
  THB: "Thai baht",
  TRY: "Turkish lira",
  TWD: "New Taiwan dollar",
  UAH: "Ukrainian hryvnia",
  USD: "US dollar",
  UYU: "Uruguayan peso",
  VES: "Venezuelan bolívar",
  VND: "Vietnamese dong",
  ZAR: "South African rand",
};

// Currencies offered in the popup's "Convert to" dropdown.
const CURRENCIES = Object.keys(CURRENCY_NAMES).sort();

// Currencies that write themselves with a bare "$", offered in the popup's
// "Treat $ as" dropdown. Grouped because the pesos are the usual surprise:
// half of Latin America prints "$" and means something other than USD.
const DOLLAR_CURRENCIES = [
  {
    label: "Pesos",
    codes: ["ARS", "CLP", "COP", "CUP", "DOP", "MXN", "PHP", "UYU"],
  },
  {
    label: "Dollars",
    codes: ["USD", "AUD", "CAD", "HKD", "NZD", "SGD", "TWD"],
  },
];

// Symbols that more than one currency writes the same way. The page's own
// currency decides between them (see detectPageCurrency in content.js); with
// no signal the price is left alone, because a plausible-looking wrong amount
// is worse than no annotation at all.
//
// "$" keeps its own setting as a manual override, since it is the one symbol
// a traveller can reasonably be expected to answer for.
const AMBIGUOUS_SYMBOLS = {
  "$": DOLLAR_CURRENCIES.flatMap((group) => group.codes),
  "\u00a5": ["JPY", "CNY"],
  // Scandinavia's krone, three countries deep, at rates close enough that a
  // wrong one passes for right.
  kr: ["SEK", "NOK", "DKK"],
  // One candidate each, but still worth a signal: "lei" is Moldovan as well as
  // Romanian, and "Rs" is Pakistani, Sri Lankan and Nepali as well as Indian.
  // Those currencies are not converted here, so the only safe reading of the
  // token on their pages is none at all.
  lei: ["RON"],
  Rs: ["INR"],
  "Rs.": ["INR"],
  R: ["ZAR"],
  元: ["CNY", "TWD"],
  ":-": ["SEK", "NOK", "DKK"],
  // "5 dr" is a dose as often as it is a price.
  dr: ["AMD"],
  "Bs.": ["BOB", "VES"],
  Bs: ["BOB", "VES"],
};

// ISO codes that are also the name of something common enough to outnumber the
// currency in ordinary text. Like the ambiguous symbols, these are read as
// money only where the page is priced in them: "PHP 8.2" is a version number
// everywhere except the Philippines, and "AMD 5600" a processor everywhere
// except Armenia.
const AMBIGUOUS_CODES = {
  AMD: ["AMD"],
  PHP: ["PHP"],
};

// Two tokens are too common in ordinary text to be matched on their own, and
// what saves them is not the same thing, so each gets its own pattern here in
// place of the plain escaped literal. These are regex source, not literals:
// nothing escapes them.
const TOKEN_PATTERNS = {
  // 元 opens a great many ordinary Chinese words — 元旦, 元月, 元素 — and closes
  // as many others — 单元, 纪元. A price neither continues into another Han
  // character nor follows one, so refuse on either side. Fencing both ends
  // matters: without the lookbehind the 元 of 单元 pairs with the number after
  // it and "单元 3 元素" reads as 3 yuan. The cost is "5999元起" ("from 5999"),
  // which is the price of not reading 2026元旦 as 2026 yuan.
  元: "(?<![\\p{Script=Han}])元(?![\\p{Script=Han}])",
  // R is a lone capital letter sitting where a tyre size ("205/55 R16"), a
  // model number or a year could be. Requiring the number to be shaped like a
  // price — grouped thousands, two decimals, or three digits and up — leaves
  // R16 and R5 alone. It also gives up prices under R100, which is the side to
  // err on: a missed conversion is an inconvenience, a wrong one is a lie.
  R: "R(?=\\s?(?:\\d{1,3}[.,\u00a0\u202f ]\\d{3}|\\d+[.,]\\d{2}|\\d{3,}))",
  // ":-" has to touch the number — "429:-" is a price, "5 :-)" is a smiley with
  // a 5 in front of it — and nothing may follow that would make it something
  // else: a digit (a time, a range) or the rest of a smiley.
  ":-": "(?<=\\d):-(?![\\d)\\p{L}])",
};

// Country (a ccTLD, or the region subtag of a lang attribute) to the currency
// its shops price in. Only currencies this extension can convert are listed:
// a country whose currency it does not know is better left undetected.
const COUNTRY_TO_CURRENCY = {
  ae: "AED", am: "AMD", ar: "ARS", au: "AUD", bo: "BOB", br: "BRL",
  ca: "CAD",
  ch: "CHF", cl: "CLP", cn: "CNY", co: "COP", cr: "CRC", cu: "CUP",
  cz: "CZK", dk: "DKK", do: "DOP", ec: "USD", eg: "EGP", gb: "GBP",
  ge: "GEL", gt: "GTQ", hk: "HKD", hu: "HUF", id: "IDR", il: "ILS",
  in: "INR", jp: "JPY", kr: "KRW", kz: "KZT", li: "CHF", mx: "MXN",
  my: "MYR", ng: "NGN", no: "NOK", nz: "NZD", pa: "PAB", pe: "PEN",
  ph: "PHP", pl: "PLN", py: "PYG", ro: "RON", rs: "RSD", ru: "RUB",
  sa: "SAR", se: "SEK", sg: "SGD", th: "THB", tr: "TRY", tw: "TWD",
  ua: "UAH", uk: "GBP", us: "USD", uy: "UYU", ve: "VES", vn: "VND",
  za: "ZAR",
  // The euro area. Bulgaria joined on 1 January 2026, so a .bg shop prices in
  // euro now; BGN stays in the currency list for pages written before that.
  ad: "EUR", at: "EUR", be: "EUR", bg: "EUR", cy: "EUR", de: "EUR",
  ee: "EUR", es: "EUR", fi: "EUR", fr: "EUR", gr: "EUR", hr: "EUR",
  ie: "EUR", it: "EUR", lt: "EUR", lu: "EUR", lv: "EUR", mc: "EUR",
  mt: "EUR", nl: "EUR", pt: "EUR", si: "EUR", sk: "EUR", sm: "EUR",
};

// Languages spoken in exactly one currency area. Deliberately short: German,
// French, Italian and Portuguese each straddle two currencies, and Spanish and
// English a dozen, so they say nothing on their own and are left out.
const LANG_TO_CURRENCY = {
  cs: "CZK", da: "DKK", el: "EUR", et: "EUR", fi: "EUR", he: "ILS",
  hi: "INR", hr: "EUR", hu: "HUF", id: "IDR", ja: "JPY", ka: "GEL",
  kk: "KZT", ko: "KRW", lt: "EUR", lv: "EUR", ms: "MYR", nb: "NOK",
  nl: "EUR", nn: "NOK", no: "NOK", pl: "PLN", ro: "RON", ru: "RUB",
  sk: "EUR", sl: "EUR", sr: "RSD", sv: "SEK", th: "THB", tr: "TRY",
  uk: "UAH", vi: "VND",
};

// Which country's flag stands beside a currency in the popup's lists. Display
// only: it says what to draw next to a code, never what a page is priced in.
// Most follow from COUNTRY_TO_CURRENCY; these are the ones several countries
// share, where the flag should be the issuer's rather than whichever member
// sorts first. The Android picker uses the same rule (tools/gen-currency-kt.py).
const FLAG_OVERRIDES = {
  EUR: "eu", // the union's own flag, rather than picking a member
  USD: "us", // also spent in Ecuador
  GBP: "gb",
  CHF: "ch", // also Liechtenstein
  BGN: "bg", // Bulgaria prices in euro now; the lev is kept for old pages
};

// The flag as an emoji: two regional indicator letters, which the system draws
// as a flag. Nothing to bundle. "" when a currency has no country to show.
function flagFor(code) {
  const country =
    FLAG_OVERRIDES[code] ??
    Object.keys(COUNTRY_TO_CURRENCY)
      .filter((cc) => COUNTRY_TO_CURRENCY[cc] === code)
      .sort()[0];
  if (!country || !/^[a-z]{2}$/.test(country)) return "";
  const base = 0x1f1e6 - "a".charCodeAt(0);
  return String.fromCodePoint(base + country.charCodeAt(0), base + country.charCodeAt(1));
}

// The last label of a hostname: "falabella.com.pe" -> PEN, "takealot.com" -> null.
function currencyFromHostname(hostname) {
  const tld = String(hostname || "").toLowerCase().split(".").pop();
  return COUNTRY_TO_CURRENCY[tld] || null;
}

// A lang attribute, region first: "en-ZA" is South African however English it
// is, and "zh-Hant-TW" is Taiwan. A bare "en" or "es" resolves to nothing.
function currencyFromLang(lang) {
  const parts = String(lang || "").toLowerCase().split("-");
  for (let i = parts.length - 1; i > 0; i--) {
    if (parts[i].length === 2 && COUNTRY_TO_CURRENCY[parts[i]]) {
      return COUNTRY_TO_CURRENCY[parts[i]];
    }
  }
  return LANG_TO_CURRENCY[parts[0]] || null;
}

// --- What currency is a page priced in? ------------------------------------
//
// Only consulted for symbols that are ambiguous on their own. Order matters:
// the page's own machine-readable statement beats a guess from its address,
// which beats a guess from its language. `isKnownCode` is the caller's test
// for "a currency we hold a rate for" — the markup is full of three-letter
// strings that are not currencies.

const MAX_LD_SCRIPTS = 20;
const MAX_LD_NODES = 2000;

// Breadth-first, so a shallow page-level offer is seen before anything buried
// in a nested catalogue, and bounded so a huge blob cannot stall the page.
function findPriceCurrency(root, isKnownCode) {
  const queue = [root];
  let seen = 0;
  while (queue.length && seen < MAX_LD_NODES) {
    const node = queue.shift();
    seen++;
    if (!node || typeof node !== "object") continue;
    if (!Array.isArray(node)) {
      const code = isKnownCode(node.priceCurrency);
      if (code) return code;
    }
    for (const value of Object.values(node)) {
      if (value && typeof value === "object") queue.push(value);
    }
  }
  return null;
}

// The three shapes shops state their currency in. Authoritative when present:
// the site is telling us outright rather than us inferring anything.
function currencyFromMarkup(doc, isKnownCode) {
  for (const el of doc.querySelectorAll(
    '[itemprop~="priceCurrency"],[property~="priceCurrency"]'
  )) {
    const code = isKnownCode(el.getAttribute("content") || el.textContent);
    if (code) return code;
  }

  const meta = doc.querySelector(
    'meta[property="product:price:currency"],meta[property="og:price:currency"]'
  );
  if (meta) {
    const code = isKnownCode(meta.getAttribute("content"));
    if (code) return code;
  }

  const scripts = [
    ...doc.querySelectorAll('script[type="application/ld+json"]'),
  ].slice(0, MAX_LD_SCRIPTS);
  for (const script of scripts) {
    let data;
    try {
      data = JSON.parse(script.textContent);
    } catch {
      continue; // hand-built JSON-LD is often malformed; it is only a hint
    }
    // A page may carry several offers. The first currency wins: it is the
    // page's main one often enough, and surveying them all would not say more.
    const code = findPriceCurrency(data, isKnownCode);
    if (code) return code;
  }
  return null;
}

// textContent runs adjacent elements together, so a token ending one element
// and a number starting the next read as a single price: IKEA's "4 000+ kr"
// filter sitting next to a "126 produkter" count becomes "kr126".
//
// What separates that from a genuine split price — <span>Gs</span><span>23.000
// </span> — is not the tree, which has the same shape either way. It is that
// the halves of a real price are drawn touching, and these two are drawn at
// opposite ends of a filter row. So measure the seam.
//
// Two earlier attempts are worth recording, because both look right and are
// not. innerText reports a line break between block-level children, and a flex
// row is full of those — MercadoLibre builds a price out of two display:block
// spans drawn side by side, and innerText calls them two lines. Measuring the
// element as a whole fails the other way: a Range over a whole line of inline
// content returns one rectangle covering all of it, gap and all.
//
// So the measurement has to be of the match itself, which means being able to
// point at it in the DOM — hence the map below.

// Shops set the minor unit as a superscript and let the markup carry the
// decimal point: maxi.rs writes <div>189</div><sup>99</sup><div>RSD</div> for
// 189,99 RSD, and reading that as text gives "18999RSD" — a hundred times the
// real price. Where the digits are raised, the separator is put back.
function isRaisedMinorUnit(node) {
  const parent = node.parentElement;
  if (!parent) return false;
  if (!/^\d{1,2}$/.test(node.nodeValue.trim())) return false;
  if (parent.tagName === "SUP") return true;
  const view = parent.ownerDocument.defaultView;
  return view ? view.getComputedStyle(parent).verticalAlign === "super" : false;
}

// Collapses whitespace the way the scan does, while keeping, for every
// character of the result, the node and offset it came from.
function collapsePriceText(el) {
  const map = [];
  let text = "";
  let pendingSpace = false;

  const walker = el.ownerDocument.createTreeWalker(el, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const value = node.nodeValue;

    // Only right after the whole part of a number, so a footnote marker or an
    // exponent elsewhere in the text is left alone.
    if (/\d$/.test(text) && !pendingSpace && isRaisedMinorUnit(node)) {
      text += ",";
      map.push({ node, offset: value.search(/\S/) });
    }
    for (let i = 0; i < value.length; i++) {
      if (/\s/u.test(value[i])) {
        // Leading whitespace is dropped, and a run of it becomes one space —
        // but only once something follows it, which is what trims the tail.
        pendingSpace = text.length > 0;
        continue;
      }
      if (pendingSpace) {
        text += " ";
        map.push({ node, offset: i });
        pendingSpace = false;
      }
      text += value[i];
      map.push({ node, offset: i });
    }
  }
  return { text, map };
}

// Is the matched run drawn as one unbroken piece of a line? Its pieces must
// share a line and touch: a space between them is fine, the width of a filter
// row is not.
function matchIsOnOneLine(map, start, end) {
  // Group the matched characters by the text node they came from and measure
  // each run separately. A Range spanning two nodes is no good here: it reports
  // one rectangle for the whole line, the gap inside it included, which is the
  // very thing being looked for.
  const runs = [];
  for (let i = start; i < end; i++) {
    const at = map[i];
    if (!at) continue;
    const last = runs[runs.length - 1];
    if (last && last.node === at.node) last.to = at.offset + 1;
    else runs.push({ node: at.node, from: at.offset, to: at.offset + 1 });
  }
  // All in one text node: nothing was run together, and the first pass has it.
  if (runs.length < 2) return true;

  const rects = runs.map((run) => {
    const range = run.node.ownerDocument.createRange();
    range.setStart(run.node, run.from);
    range.setEnd(run.node, run.to);
    return range.getBoundingClientRect();
  });
  // A box no bigger than a pixel is not text anyone can read. That covers an
  // element that is not laid out at all (0×0) and, at 1×1, the standard
  // screen-reader-only recipe — width and height of 1px with overflow hidden —
  // which is exactly what IKEA's filter rows use: a hidden "28 produkter"
  // beside the visible count, and it is that hidden copy the "kr" of the label
  // runs into. A price with a piece nobody can see is not a price.
  if (rects.some((r) => r.width <= 1 || r.height <= 1)) return false;

  const tallest = Math.max(...rects.map((r) => r.height));
  const centres = rects.map((r) => r.top + r.height / 2);
  // Centres rather than tops: a currency symbol is often set in a different
  // size from the digits beside it and sits a pixel or two off on the very
  // same line. Two lines are a whole line-height apart.
  if (Math.max(...centres) - Math.min(...centres) > tallest * 0.75) return false;

  const ordered = [...rects].sort((a, b) => a.left - b.left);
  let widest = 0;
  for (let i = 1; i < ordered.length; i++) {
    widest = Math.max(widest, ordered[i].left - ordered[i - 1].right);
  }
  // Measured against the pages this was built from, as a fraction of the line
  // height: a price whose halves touch scores 0.00, one separated by a space
  // 0.51, and stock.com.py's two non-breaking spaces 1.02 — while IKEA's filter
  // label and its count, the case this exists to reject, score 6.94. Anywhere
  // in between separates them; 1.5 leaves room on the side of the real prices.
  return widest <= tallest * 1.5;
}

function detectPageCurrency(doc, loc, isKnownCode) {
  const fromMarkup = currencyFromMarkup(doc, isKnownCode);
  const fromHost = currencyFromHostname(loc.hostname);
  const fromLang = currencyFromLang(doc.documentElement.lang);

  // The markup is normally the best of the three, being a statement rather
  // than an inference — but it can simply be wrong. maxi.rs declares EUR in
  // its JSON-LD while pricing every shelf in dinars, and taking its word for
  // it would misread an unlabelled price by a factor of a hundred.
  //
  // So when the two strong signals disagree, the language breaks the tie: two
  // of three carries it. With nothing to break it, the page does not get a
  // currency at all, and the ambiguous symbols stay unconverted — which is the
  // trade this whole feature is built on.
  if (fromMarkup && fromHost && fromMarkup !== fromHost) {
    if (fromLang === fromHost) return fromHost;
    if (fromLang === fromMarkup) return fromMarkup;
    return null;
  }

  return fromMarkup || fromHost || fromLang;
}

// Number token: 1 234 567,89 / 1,234,567.89 / 1'234'567.89 / 1234.5 / 1234
// The apostrophes are Switzerland's thousands separator, in both the typographic
// and the typewriter spelling; a shop picks one or the other.
// A dash can stand in for the minor unit — German and Austrian shops write
// "1.449,–" for a round amount — in either the typographic or the hyphen form.
const MINOR = String.raw`[.,](?:\d{1,2}|[–-])`;
const NUMBER = String.raw`\d{1,3}(?:[.,   '’]\d{3})+(?:${MINOR})?|\d+(?:${MINOR})?`;

// Codes that are also everyday English words, and so are only read as money
// when written the way money is: BOB, COP, CUP, GEL, PEN, RUB, TRY.
const CODES_NEEDING_CAPITALS = new Set([
  "BOB", "COP", "CUP", "GEL", "PEN", "RUB", "TRY",
]);

// A token written in a script that spaces its words has to be fenced off by
// letters, or "руб" matches inside "рубанок" and "USD" inside "USDT". A token
// in a script that does not space its words — 円, 원, บาท — must not be fenced,
// because the character right after it is usually another letter and the fence
// would reject every real price.
const SPACED_SCRIPT =
  /[\p{Script=Latin}\p{Script=Cyrillic}\p{Script=Greek}\p{Script=Armenian}\p{Script=Hebrew}]/u;

// <currency><number> or <number><currency>, anywhere in a run of text.
function buildPriceRegExp() {
  const pattern = (sym) =>
    TOKEN_PATTERNS[sym] ?? sym.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const symbols = Object.keys(SYMBOL_TO_CODE).sort((a, b) => b.length - a.length);
  const fenced = symbols.filter((sym) => SPACED_SCRIPT.test(sym)).map(pattern);
  const bare = symbols.filter((sym) => !SPACED_SCRIPT.test(sym)).map(pattern);

  // Spelling the ISO codes out rather than matching any three capitals: a
  // stray "UVP" or "SKU" would otherwise match and consume the number after
  // it, and the real price alongside — "UVP 1449,– €" — would be gone by the
  // time the scan resumed.
  //
  // Most are taken in any case, since shops write "129.98 rsd/Kg" as readily
  // as "RSD". The exceptions are the ones that are also ordinary words: in
  // lower case "5 pen set" is stationery, not Peruvian soles, and "rub 2
  // drops" is an instruction, not roubles. Those hold out for capitals.
  const codes = Object.keys(CURRENCY_NAMES)
    .map((code) =>
      CODES_NEEDING_CAPITALS.has(code)
        ? code
        : [...code].map((ch) => `[${ch}${ch.toLowerCase()}]`).join("")
    )
    .join("|");

  // Longest first within each group, and the fenced group first overall, so
  // "R$" wins over "$" and "US$" over "$".
  const cur =
    `(?:(?<![\\p{L}])(?:${fenced.join("|")}|${codes})(?![\\p{L}])` +
    `|(?:${bare.join("|")}))`;

  return new RegExp(`(${cur})\\s?(${NUMBER})|(${NUMBER})\\s?(${cur})`, "gu");
}

// Which currency a matched token means. `context` carries what only the page
// knows: its own currency, the user's "$" setting, and a test for codes we
// hold a rate for. Returns null when the token cannot be pinned down — the
// caller then leaves the price alone.
function resolveSymbol(token, context) {
  if (!token) return null;
  const fromSymbol = SYMBOL_TO_CODE[token];
  const code = fromSymbol ?? context.isKnownCode(token);
  if (!code) return null;

  const options = fromSymbol
    ? AMBIGUOUS_SYMBOLS[token]
    : AMBIGUOUS_CODES[code];
  if (!options) return code;

  // The "$" setting is a manual override; on "auto" it defers to the page like
  // every other ambiguous symbol.
  if (token === "$" && context.dollarAssumption !== "auto") {
    return context.dollarAssumption;
  }
  return options.includes(context.pageCurrency) ? context.pageCurrency : null;
}

// Turns a localized number string into a Number, guessing the decimal
// separator from context. Returns null when it cannot be parsed.
function parseAmount(raw) {
  // The dash standing in for the minor unit carries no value: "1.449,–" is 1449.
  let s = String(raw).replace(/[  \s'’]/g, "").replace(/[.,][–-]$/, "");
  const hasComma = s.includes(",");
  const hasDot = s.includes(".");

  if (hasComma && hasDot) {
    // Whichever separator comes last is the decimal one.
    if (s.lastIndexOf(",") > s.lastIndexOf(".")) {
      s = s.replace(/\./g, "").replace(",", ".");
    } else {
      s = s.replace(/,/g, "");
    }
  } else if (hasComma) {
    const parts = s.split(",");
    // "12,34" -> decimal; "1,234" or "1,234,567" -> thousands
    s = parts.length === 2 && parts[1].length <= 2
      ? parts[0] + "." + parts[1]
      : parts.join("");
  } else if (hasDot) {
    const parts = s.split(".");
    if (parts.length > 2) {
      s = parts.join(""); // "1.234.567" -> thousands
    } else if (parts[1] && parts[1].length === 3) {
      s = parts.join(""); // "1.234" -> treat trailing triple as thousands
    }
    // otherwise keep as a plain decimal
  }

  const n = Number.parseFloat(s);
  return Number.isFinite(n) ? n : null;
}

// Formats a converted amount for display, e.g. "1,234.50 EUR".
function formatConverted(amount, code) {
  // Drop the cents on larger amounts, but never ask for more precision than
  // the currency itself has: Intl throws when the maximum it is given falls
  // below the currency's own minimum (2 for USD, 0 for JPY/PYG).
  const wanted = amount >= 100 ? 0 : 2;
  try {
    const plain = new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: code,
    });
    const digits = Math.min(wanted, plain.resolvedOptions().maximumFractionDigits);
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: code,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${code}`;
  }
}

// Exposed for content.js (classic script scope) and popup.js (module-ish use).
if (typeof self !== "undefined") {
  self.HMC = {
    SYMBOL_TO_CODE,
    CURRENCY_NAMES,
    CURRENCIES,
    DOLLAR_CURRENCIES,
    AMBIGUOUS_SYMBOLS,
    NUMBER,
    buildPriceRegExp,
    resolveSymbol,
    parseAmount,
    formatConverted,
    currencyFromHostname,
    currencyFromLang,
    flagFor,
    currencyFromMarkup,
    detectPageCurrency,
    collapsePriceText,
    matchIsOnOneLine,
  };
}
