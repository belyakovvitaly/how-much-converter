# How Much? — Currency Price Converter

A Chrome extension (Manifest V3) that scans the prices on any web page and shows
them in the currency you care about, inline, next to the original:

> The jacket costs **$129.00 (≈ 119 EUR)**.

No build step, no framework, no API key.

## Install

Not in the Chrome Web Store yet — see [STORE.md](STORE.md) for what a submission
needs. Until then, or for development:

1. Open `chrome://extensions`.
2. Turn on **Developer mode** (top right).
3. Click **Load unpacked** and select this folder.
4. Pin the extension and open its popup to pick your target currency.

Chrome blocks installing a `.crx` from anywhere but the store, so an unpacked
load is the only way to run it off-store.

## Package

```sh
./tools/build.sh
```

Writes `dist/how-much-<version>.zip`, laid out the way the store wants it.

## How it works

| Part | File | Responsibility |
| --- | --- | --- |
| Rates | `src/background.js` | Fetches a USD-based rate table from [open.er-api.com](https://open.er-api.com) and caches it in `chrome.storage.local` for 6 hours. All pairs are derived as cross rates. |
| Detection | `src/currency.js` | Symbol/ISO-code tables and a locale-aware number parser (`1 234,56` vs `1,234.56`). |
| Page changes | `src/content.js` | Two passes — text nodes that hold a whole price, then shallow elements that spread one across children — appending the converted value in a `.hmc-conv` span, and re-scanning on DOM mutations. |
| Settings | `src/popup.*` | Target currency, what `$` should mean, on/off, and a manual rate refresh. |

## Known limitations (v1)

- Only currencies placed **directly** before or after the number are recognized
  (`$5`, `5 USD`), not prose like "five dollars" or "USD five".
- A bare `$` is assumed to be USD unless you change **Treat "$" as** in the popup.
- A price split across elements is only found when the element holding it
  contains nothing else (`<span><span>$</span><span>5</span></span>`), which is
  the common store-template shape but not the only one.

## Roadmap ideas

- Per-site currency overrides.
- Hover tooltip with the rate and fetch time instead of inline text.
- Offline fallback bundle of rates.

## Privacy

Nothing is collected and nothing about the pages you visit leaves the browser —
see [PRIVACY.md](PRIVACY.md).

## License

MIT — see [LICENSE](LICENSE).
