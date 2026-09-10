# Chrome Web Store submission notes

Everything the dashboard asks for, written out so a submission (or a
resubmission after a rejection) is copy-paste rather than improvisation.

Build the upload with `./tools/build.sh` — it writes `dist/how-much-<version>.zip`
with `manifest.json` at the archive root, which is what the store expects.
Bump `"version"` in `manifest.json` before every upload; the store refuses a
version it has already seen.

## Walkthrough

Steps only the account holder can take are marked **(you)** — they involve
signing in, paying, or making a legal declaration. Everything else is already
prepared in this file.

**Before the dashboard**

1. **(you)** Turn on 2-Step Verification on the Google account you will publish
   from. The store refuses to publish without it. Note that the developer email
   cannot be changed later, so pick the account you intend to keep.
2. Have the upload ready: `./tools/build.sh`, or download the zip from the
   [latest release](https://github.com/belyakovvitaly/how-much-converter/releases/latest).

**Registration** — <https://chrome.google.com/webstore/devconsole/>

3. **(you)** Sign in, accept the developer agreement, and pay the one-time $5
   registration fee.
4. **(you)** Declare Trader or Non-Trader. A Trader's legal name, address and
   phone are published at the bottom of the listing.
5. **(you)** Verify the contact email the dashboard asks for.

**The item**

6. **Add new item** → **Choose file** → the zip → **Upload**.
7. *Store listing* tab: name, short description, detailed description and
   category from the sections below; upload both PNGs from `docs/`.
8. *Privacy* tab: single purpose, the three permission justifications, and the
   data-usage certification — all below. Privacy policy URL:
   <https://github.com/belyakovvitaly/how-much-converter/blob/main/PRIVACY.md>
9. *Distribution* tab: countries, and visibility. **Unlisted** installs in one
   click from a link but is not searchable, and can be switched to Public later
   without another review — the safer first publish.
10. **(you)** Submit for review. Expect several days; the `<all_urls>` content
    script draws a closer look than most permissions.

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
> • Recognizes currencies that are spelled out rather than signed (340 руб).
> • Rates come from a public exchange-rate service and are cached for six
>   hours; refresh them yourself any time from the popup.
> • Found a shop it misses? "Report this page" keeps the address in a list only
>   you can see, stored in your own browser.
>
> No account, no analytics, no tracking. Your settings stay on your machine and
> nothing about the pages you visit ever leaves your browser.

## Privacy tab

**Single purpose**

> Display the prices already shown on a web page in a currency the user chooses.

**Justification — `storage`**

> Stores the user's three settings (target currency, what a bare "$" means, and
> whether conversion is on), caches the exchange-rate table so the extension does
> not refetch rates on every page load, and holds the list of pages the user
> marked as not converting correctly. All of it is local; none is transmitted.

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
