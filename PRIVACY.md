# Privacy Policy — How Much? Currency Price Converter

Last updated: 9 September 2026

## Short version

The extension collects nothing, sends nothing about you anywhere, and has no
accounts, analytics, or trackers.

## What is stored, and where

Three settings — your target currency, what a bare `$` should mean, and whether
conversion is on — plus a cached table of exchange rates. All of it lives in
`chrome.storage.local`, on your own machine. It is never uploaded, and it is
deleted when you remove the extension.

## What is read from web pages

To find prices, the extension reads the text of the pages you visit and adds a
converted amount next to any price it recognizes. This happens entirely inside
your browser. Page content is never transmitted, stored, or logged.

## Network requests

The extension makes exactly one kind of request: it fetches a public,
USD-based exchange-rate table from `https://open.er-api.com`, at most once every
six hours, or when you press **Refresh**.

That request carries no identifier, no API key, no page address, and nothing
about what you were looking at — it is the same request for every user. As with
any web request, the operator of that service can see the IP address it came
from; their terms are at [open.er-api.com](https://open.er-api.com).

## What is not done

No analytics. No advertising. No tracking. No selling or sharing of data. No
remote code: everything the extension runs ships inside the package.

## Contact

Questions or reports: open an issue at
<https://github.com/belyakovvitaly/how-much-converter/issues>.
