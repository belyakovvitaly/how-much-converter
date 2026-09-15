# Chrome Web Store submission notes

Everything the dashboard asks for, written out so a submission (or a
resubmission after a rejection) is copy-paste rather than improvisation.

Build the upload with `./tools/build.sh` — it writes `dist/how-much-<version>.zip`
with `manifest.json` at the archive root, which is what the store expects.
Bump `"version"` in `extension/manifest.json` before every upload; the store refuses a
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
>   ($5, 5 USD, €5, 5 €, Gs 23.000, ₽500), and the codes shops write instead of
>   a symbol (57.249 TL, 399 990 Ft, Rp 9.499.000, 129.98 rsd).
> • Understands local number formats: 1,234.56, 1.234,56 and 1'234.56 are all
>   read correctly — as are the shapes shops invent, like a superscript minor
>   unit (189⁹⁹ RSD), a dash where the cents go (1.449,–) and Sweden's 429:-,
>   which names no currency at all.
> • Handles prices that a site splits across page elements, which is how most
>   store templates are actually built.
> • Several countries share a symbol — much of Latin America prints "$" and
>   means a peso, "¥" is both yen and yuan, "kr" belongs to three Nordic
>   countries. The extension works out which one a page is priced in from the
>   page itself, and leaves a price alone rather than guess when it cannot tell.
>   You can still say what "$" should mean, if you would rather decide.
> • Recognizes currencies that are spelled out rather than signed (340 руб).
> • Rates come from a public exchange-rate service and are cached for six
>   hours; refresh them yourself any time from the popup.
> • Found a shop it misses? "It didn't work here" keeps a note in a list only
>   you can see, stored in your own browser — copy it out or open a GitHub
>   issue with it when you want to.
>
> No account, no analytics, no tracking. Your settings stay on your machine and
> nothing about the pages you visit ever leaves your browser.

## Privacy tab

**Single purpose** — one field, 1,000 characters

> How Much? has a single purpose: to show the prices already displayed on a web
> page in a currency the user chooses.
>
> The extension reads the text of the page the user is viewing, recognizes
> amounts written with a currency symbol or an ISO code, converts them using a
> public exchange-rate table, and appends the converted amount next to the
> original price. Nothing on the page is replaced, hidden or rewritten; the
> original price stays exactly as the site wrote it.
>
> There is no second feature. It does not block, inject, redirect or modify page
> content beyond appending that converted amount, and it collects nothing about
> the user or the pages they visit.

**Justification — `storage`**

> chrome.storage.local is used for four local things, none of which leave the
> browser:
>
> 1. The user's three settings: the target currency, what a bare "$" should
>    mean, and whether conversion is switched on.
> 2. A cache of the exchange-rate table, so the extension does not refetch rates
>    on every page load. It is refreshed at most every six hours.
> 3. The list of pages the user marked with "It didn't work here", so they can
>    review, copy or clear it in the popup. It holds the page address, what the
>    extension detected, and a few price-shaped strings it failed to convert.
> 4. Nothing else.
>
> All of it stays in the user's own browser profile. None of it is transmitted,
> and the extension has no server or account to transmit it to.

**Justification — host permissions**

The console offers a single host-permission field, so it has to cover both the
rate endpoint and the `<all_urls>` content script. Answering only for the
endpoint reads as an omission about the broader one.

> The extension needs two kinds of host access, for two clearly separate
> reasons.
>
> https://open.er-api.com/* is the extension's only network request. It fetches
> a public, USD-based exchange-rate table, which is what makes conversion
> possible; every currency pair is derived from it as a cross rate. The request
> carries no user data, no page data and no identifier, and the response is
> cached locally for six hours.
>
> The content script runs on <all_urls> because prices appear on arbitrary
> shopping, marketplace, listing and news sites, and the user cannot enumerate
> in advance which sites they will want converted. The value of the extension is
> that a price is converted wherever it shows up, including on a site the user
> has never visited before. The script only reads page text to locate prices and
> appends a converted amount next to them. It sends nothing anywhere: page
> content never leaves the browser.

**Data usage** — certify that no user data is collected, for every category.

**Privacy policy URL** — the raw or Pages URL of [PRIVACY.md](PRIVACY.md), e.g.
`https://github.com/belyakovvitaly/how-much-converter/blob/main/PRIVACY.md`

## Assets

- **Screenshots** — ready, at the required 1280×800:
  [`docs/screenshot-inline.png`](docs/screenshot-inline.png) (conversions on a
  page) and [`docs/screenshot-popup.png`](docs/screenshot-popup.png) (the
  popup). Regenerate with `./tools/screenshots.sh`.
- **Icon** — the 128×128 the listing needs is in `extension/icons/`.
- **Small promo tile** — 440×280 PNG, still missing. Optional, and only needed
  to be eligible for store promotion.
