# How Much? — Currency Price Converter

A Chrome extension (Manifest V3) that scans the prices on any web page and shows
them in the currency you care about, inline, next to the original:

> The jacket costs **$129.00 (≈ 119 EUR)**.

No build step, no framework, no API key.

## Install (unpacked, for development)

1. Open `chrome://extensions`.
2. Turn on **Developer mode** (top right).
3. Click **Load unpacked** and select this folder.
4. Pin the extension and open its popup to pick your target currency.

## How it works

| Part | File | Responsibility |
| --- | --- | --- |
| Rates | `src/background.js` | Fetches a USD-based rate table from [open.er-api.com](https://open.er-api.com) and caches it in `chrome.storage.local` for 6 hours. All pairs are derived as cross rates. |
| Detection | `src/currency.js` | Symbol/ISO-code tables and a locale-aware number parser (`1 234,56` vs `1,234.56`). |
| Page changes | `src/content.js` | Walks text nodes, appends the converted value in a `.hmc-wrap` span, and re-scans on DOM mutations. |
| Settings | `src/popup.*` | Target currency, what `$` should mean, on/off, and a manual rate refresh. |

## Known limitations (v1)

- Only currencies placed **directly** before or after the number are recognized
  (`$5`, `5 USD`), not prose like "five dollars" or "USD five".
- A bare `$` is assumed to be USD unless you change **Treat "$" as** in the popup.
- Amounts split across HTML elements (e.g. `<span>$</span><span>5</span>`) are missed.

## Roadmap ideas

- Per-site currency overrides.
- Hover tooltip with the rate and fetch time instead of inline text.
- Offline fallback bundle of rates.

## License

MIT — see [LICENSE](LICENSE).
