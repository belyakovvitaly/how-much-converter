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
  // Ambiguous, and so only resolved when the page says which country it is.
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
};

// Country (a ccTLD, or the region subtag of a lang attribute) to the currency
// its shops price in. Only currencies this extension can convert are listed:
// a country whose currency it does not know is better left undetected.
const COUNTRY_TO_CURRENCY = {
  ae: "AED", ar: "ARS", au: "AUD", bo: "BOB", br: "BRL", ca: "CAD",
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
// filter sitting next to a "126 produkter" count becomes "kr126". innerText is
// what the page actually shows — with the line breaks the layout puts in — so
// a match that survives there is one a reader would see as one price too.
//
// Costly enough (it forces layout) to be worth calling only on a text that has
// already matched, and only while nothing is being written to the page.
function matchIsVisible(el, matched) {
  const shown = el.innerText;
  // An element outside the layout has no innerText of its own to disagree with.
  if (!shown) return true;
  return shown.replace(/\s+/gu, " ").trim().includes(matched);
}

function detectPageCurrency(doc, loc, isKnownCode) {
  return (
    currencyFromMarkup(doc, isKnownCode) ||
    currencyFromHostname(loc.hostname) ||
    currencyFromLang(doc.documentElement.lang)
  );
}

// Number token: 1 234 567,89 / 1,234,567.89 / 1'234'567.89 / 1234.5 / 1234
// The apostrophes are Switzerland's thousands separator, in both the typographic
// and the typewriter spelling; a shop picks one or the other.
const NUMBER = String.raw`\d{1,3}(?:[.,   '’]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?`;

// A token written in a script that spaces its words has to be fenced off by
// letters, or "руб" matches inside "рубанок" and "USD" inside "USDT". A token
// in a script that does not space its words — 円, 원, บาท — must not be fenced,
// because the character right after it is usually another letter and the fence
// would reject every real price.
const SPACED_SCRIPT = /[\p{Script=Latin}\p{Script=Cyrillic}\p{Script=Greek}]/u;

// <currency><number> or <number><currency>, anywhere in a run of text.
function buildPriceRegExp() {
  const escape = (sym) => sym.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const symbols = Object.keys(SYMBOL_TO_CODE).sort((a, b) => b.length - a.length);
  const fenced = symbols.filter((sym) => SPACED_SCRIPT.test(sym)).map(escape);
  const bare = symbols.filter((sym) => !SPACED_SCRIPT.test(sym)).map(escape);

  // Longest first within each group, and the fenced group first overall, so
  // "R$" wins over "$" and "US$" over "$".
  const cur =
    `(?:(?<![\\p{L}])(?:${fenced.join("|")}|[A-Z]{3})(?![\\p{L}])` +
    `|(?:${bare.join("|")}))`;

  return new RegExp(`(${cur})\\s?(${NUMBER})|(${NUMBER})\\s?(${cur})`, "gu");
}

// Which currency a matched token means. `context` carries what only the page
// knows: its own currency, the user's "$" setting, and a test for codes we
// hold a rate for. Returns null when the token cannot be pinned down — the
// caller then leaves the price alone.
function resolveSymbol(token, context) {
  if (!token) return null;
  const code = SYMBOL_TO_CODE[token];
  if (!code) return context.isKnownCode(token);

  const options = AMBIGUOUS_SYMBOLS[token];
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
  let s = String(raw).replace(/[  \s'’]/g, "");
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
    currencyFromMarkup,
    detectPageCurrency,
    matchIsVisible,
  };
}
