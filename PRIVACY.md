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

## Reported pages

If you use **It didn't work here**, a note about that page is added to a list in
`chrome.storage.local` — the same local storage as the settings. The note holds
the address, the page title, the currency the extension decided the page was
priced in, how many prices it converted, your settings at the time, the version
of the extension, and up to five short pieces of text from the page that look
like prices it did not convert.

It is not sent anywhere. Nobody but you can read it, it is visible only in the
extension's own popup, and **Clear** deletes it.

Two buttons take it out of the browser, and only when you press them:

- **Copy** puts the list on your clipboard, for you to paste wherever you like.
- **Report on GitHub** opens the issue form with the list already filled in.
  Nothing is sent by opening it — the text sits in a form on your screen, and
  it reaches GitHub only if you review it and submit it yourself. Read it
  first: it contains the addresses of the pages you reported, and anything in
  a page address is in it too.

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
