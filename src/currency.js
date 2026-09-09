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
  "U$S": "UYU",
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
  "₴": "UAH",
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

// Number token: 1 234 567,89 / 1,234,567.89 / 1234.5 / 1234
const NUMBER = String.raw`\d{1,3}(?:[.,   ]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?`;

// Turns a localized number string into a Number, guessing the decimal
// separator from context. Returns null when it cannot be parsed.
function parseAmount(raw) {
  let s = String(raw).replace(/[  \s]/g, "");
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
    NUMBER,
    parseAmount,
    formatConverted,
  };
}
