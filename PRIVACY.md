# Privacy Policy — How Much? Currency Price Converter

Last updated: 29 September 2026

How Much? comes in two forms, both made by Vitaly Belyakov: a browser
extension, and an Android app (`io.github.belyakovvitaly.howmuch`). This policy
covers both; each has its own part below.

## Short version

Neither has accounts, analytics, advertising or trackers, and neither sells or
shares anything about you.

- **The extension** collects nothing and sends nothing about you anywhere.
- **The Android app** reads your pictures on the phone itself and sends
  nothing either — except a problem report, and only when you send it yourself
  from your mail app.

# The browser extension

## What is stored, and where

Your settings — the target currency, where the conversion is shown, what a bare
`$` should mean, and whether conversion is on — the sites you excluded, by name
only (`mercadolibre.com.ar`, not a page address), and a cached table of exchange
rates. All of it lives in `chrome.storage.local`, on your own machine. It is
never uploaded, and it is deleted when you remove the extension.

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

To find prices, the extension reads the text of the pages you visit and shows a
converted amount with any price it recognizes — beside it, in its place, or on
hover, as you choose. This happens entirely inside your browser. Page content is
never transmitted, stored, or logged.

## Network requests

The extension makes exactly one kind of request: it fetches a public,
USD-based exchange-rate table from `https://open.er-api.com`, at most once every
six hours — usually once a day, when the service publishes new rates — or when
you press **Refresh**.

That request carries no identifier, no API key, no page address, and nothing
about what you were looking at — it is the same request for every user. As with
any web request, the operator of that service can see the IP address it came
from; their terms are at [open.er-api.com](https://open.er-api.com).

## What is not done

No analytics. No advertising. No tracking. No selling or sharing of data. No
remote code: everything the extension runs ships inside the package.

# The Android app

## Pictures, and the camera

The app converts prices in pictures: a photo you take in it, one you choose
from your gallery, or one another app shares with it. Reading a picture —
finding the text, and the prices in it — happens entirely on the phone, with
models that ship inside the app. No picture is uploaded to read it.

- **The camera** is used only while the camera screen is open, and only to take
  the photo you take. Nothing is read from the viewfinder.
- **The gallery** is opened through Android's own photo picker. The app sees
  only the picture you choose, and has no permission to see any other.
- A picture is held in memory while it is on screen, and is not kept after.

## What is stored, and where

On the phone, in the app's own storage: the two currencies you chose, and a
cached table of exchange rates. Nothing else is kept, and all of it is deleted
when you uninstall the app.

**Save** writes the picture you are looking at, with its conversions drawn on
it, to your gallery, in Pictures/How Much — only when you press it. From then
on it is your picture, like any other in the gallery.

## Which currencies, and how the app knows

To guess which currency the prices around you are in, the app asks the phone
which country its mobile network is in; to guess yours, which country the SIM
card is from, and the phone's language. That is the whole of it: the app does
not ask for your location, uses no GPS, and none of this leaves the phone. You
can set either currency by hand instead.

## Network requests

The app makes one kind of request by itself, the same one the extension makes:
it fetches the public, USD-based exchange-rate table from
`https://open.er-api.com`, at most once every six hours, and usually once a
day, when the service publishes new rates. It carries no
identifier and nothing about you or your pictures — it is the same request for
every user. The operator of that service can see the IP address it came from;
their terms are at [open.er-api.com](https://open.er-api.com).

## Problem reports

When a picture is not converted as it should be, **Report** can send it to the
developer. Nothing is sent unless you go all the way through:

1. You press **Report**, and may say what was wrong.
2. You press **Send**. The app prepares a report and opens your mail app with a
   message to the developer, the report attached.
3. You send that message from your mail app — or do not, and nothing leaves.

The report holds:

- the picture — the file itself if it came from your gallery or another app,
  with the location your phone recorded in it removed; otherwise the picture as
  the app read it;
- the picture with the conversions drawn on it, as you saw it;
- the text the app read from the picture, where it found it, and which prices
  it found;
- the two currencies, and which ones the phone suggested; whether receipt mode
  was on; the exchange rate used and when it was fetched;
- the app's version, the Android version, and the make and model of the phone;
- what you wrote, if anything.

It does not hold your name, your contacts, your location or any identifier
beyond what your own mail app adds to any message you send — your email
address, for one. Look at the picture before you send it: a receipt can show
the last digits of a card, or a name.

Reports are read only to find out why a picture was misread, and to fix it. A
picture may be kept as a test case, so that the fix stays fixed; one is added
to the project's public repository only with anything personal blanked out,
and only after asking you. Ask for a report to be deleted by replying to it, or
through the issues page below, and it will be.

## What is not done

No analytics, no crash reporting in the background, no advertising, no
tracking, no account. The app does not ask for your location, your contacts or
access to your photo library.

## Children

Neither the extension nor the app is directed at children, and neither knowingly
collects anything from anyone.

# Contact

Questions, or a request about a report you sent: open an issue at
<https://github.com/belyakovvitaly/how-much-converter/issues>, or reply to the
report itself.
