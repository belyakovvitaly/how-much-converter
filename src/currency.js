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
  "R$": "BRL",
  "€": "EUR",
  "£": "GBP",
  "¥": "JPY",
  "₩": "KRW",
  "₽": "RUB",
  "₴": "UAH",
  "₺": "TRY",
  "₹": "INR",
  "₪": "ILS",
  "฿": "THB",
  "zł": "PLN",
  "Kč": "CZK",
  "$": "USD", // ambiguous; overridable via the "dollarAssumption" setting
};

// Currencies offered in the popup dropdown.
const CURRENCIES = [
  "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SGD", "HKD",
  "CNY", "INR", "KRW", "RUB", "UAH", "TRY", "BRL", "MXN", "ZAR", "PLN",
  "CZK", "SEK", "NOK", "DKK", "ILS", "AED", "SAR", "THB", "IDR", "PHP",
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
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: code,
      maximumFractionDigits: amount >= 100 ? 0 : 2,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${code}`;
  }
}

// Exposed for content.js (classic script scope) and popup.js (module-ish use).
if (typeof self !== "undefined") {
  self.HMC = { SYMBOL_TO_CODE, CURRENCIES, NUMBER, parseAmount, formatConverted };
}
