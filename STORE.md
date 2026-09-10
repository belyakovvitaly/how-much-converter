# Chrome Web Store submission notes

Everything the dashboard asks for, written out so a submission (or a
resubmission after a rejection) is copy-paste rather than improvisation.

Build the upload with `./tools/build.sh` — it writes `dist/how-much-<version>.zip`
with `manifest.json` at the archive root, which is what the store expects.
Bump `"version"` in `manifest.json` before every upload; the store refuses a
version it has already seen.

## Listing

**Name** — How Much? — Currency Price Converter

**Short description** (132 char limit)

> Shows the prices on any web page in the currency you actually think in, inline, right next to the original.

**Category** — Shopping

**Detailed description**

> Prices on foreign sites are just numbers until you convert them. How Much?
> does it for you, in place: every price it recognizes gets the equivalent in
> your currency appended right after it, so you can read a shop the way a local
> does.
>
> • Works on any site — supermarkets, marketplaces, listings, news.
> • Understands currency symbols and ISO codes on either side of the number
>   ($5, 5 USD, €5, 5 €, Gs 23.000, ₽500).
> • Understands local number formats: 1,234.56 and 1.234,56 are both read
>   correctly.
> • Handles prices that a site splits across page elements, which is how most
>   store templates are actually built.
> • A bare "$" is ambiguous — much of Latin America prints it and means a peso.
>   Tell the extension what "$" should mean on the sites you use.
> • Rates come from a public exchange-rate service and are cached for six
>   hours; refresh them yourself any time from the popup.
>
> No account, no analytics, no tracking. Your settings stay on your machine and
> nothing about the pages you visit ever leaves your browser.

## Privacy tab

**Single purpose**

> Display the prices already shown on a web page in a currency the user chooses.

**Justification — `storage`**

> Stores the user's three settings (target currency, what a bare "$" means, and
> whether conversion is on) and caches the exchange-rate table so the extension
> does not refetch rates on every page load.

**Justification — host permission `https://open.er-api.com/*`**

> The extension's only network request. It fetches a public USD-based
> exchange-rate table used to derive every currency pair as a cross rate. The
> request contains no user data, no page data, and no identifier.

**Justification — content script on `<all_urls>`**

> Prices appear on arbitrary shopping, listing, and news sites, and the user
> cannot enumerate in advance which sites they will want converted — the value
> of the extension is that it works wherever a price shows up. The content
> script only reads page text to locate prices and appends a converted amount
> next to them. It transmits nothing; page content never leaves the browser.

**Data usage** — certify that no user data is collected, for every category.

**Privacy policy URL** — the raw or Pages URL of [PRIVACY.md](PRIVACY.md), e.g.
`https://github.com/belyakovvitaly/how-much-converter/blob/main/PRIVACY.md`

## Assets

- **Screenshots** — ready, at the required 1280×800:
  [`docs/screenshot-inline.png`](docs/screenshot-inline.png) (conversions on a
  page) and [`docs/screenshot-popup.png`](docs/screenshot-popup.png) (the
  popup). Regenerate with `./tools/screenshots.sh`.
- **Icon** — the 128×128 the listing needs is in `icons/`.
- **Small promo tile** — 440×280 PNG, still missing. Optional, and only needed
  to be eligible for store promotion.
