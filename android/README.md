# Android app

Two modules. `:core` holds the price rules — what counts as a currency token,
how a number is spelled, which frames agree — and is the part iOS will share.
`:app` is the camera and the recognizer around them.

It converts prices two ways: typed into a calculator, or read from a picture —
a photo taken in the app, or one chosen from the gallery. It can also read a
live camera, but that is switched off for now (see below); the parts built
around movement — frame voting, box tracking — are kept, and have never met a
real shop.

```sh
./gradlew :core:test        # the price rules, against the benchmark's numbers
./gradlew :app:assembleDebug
```

The build needs an Android SDK with API 37 and build-tools 36. Gradle finds it
through `ANDROID_HOME` or a `local.properties` holding `sdk.dir=...`; that file
is per-machine and stays out of the repository.

To run it on an emulator, an image and a device are enough — there is no API 37
system image yet, and none is needed, since `minSdk` is 26:

```sh
sdkmanager "system-images;android-36;google_apis;arm64-v8a"
avdmanager create avd -n how-much -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7
emulator -avd how-much -camera-back virtualscene    # a scene with things to point at
./gradlew :app:installDebug
```

The OCR models are committed — 12.7 MB of ONNX in `app/src/main/assets`, which
is a fifth of one build of the APK and buys a clone that builds with nothing
else installed, and a CI that can make a release without a Python toolchain and
a model download. To change them:

```sh
./tools/export-ocr-models.py     # rewrites det.onnx, rec.onnx, charset.txt
```

To put it on a phone, the debug APK is enough: it is signed with the debug key,
so it sideloads without a keystore. It is `io.github.belyakovvitaly.howmuch.debug`,
named "How Much? dev", so it installs beside a released copy rather than
clashing with it — the two are signed with different keys, and Android refuses
one over the other.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

It comes to about 61 MB, most of it ONNX Runtime's native library and 12.7 MB
of models. The build keeps only `arm64-v8a`: four copies of that library made a
166 MB APK, and every phone this can run on is 64-bit ARM, as is the emulator on
an Apple Silicon Mac. An Intel-host emulator would need `x86_64` adding back in
`app/build.gradle.kts`.

## Signing, and what Google Play knows it by

The app is `io.github.belyakovvitaly.howmuch`. That is permanent once it is on
Google Play; `converter.android`, what it was until 0.6.0, was never going to be
free there. The code's own package — the namespace — is still
`converter.android`, which nothing outside the build sees.

A release is signed with the project's upload key: the APK on GitHub, and the
App Bundle that goes to Play, where Play App Signing re-signs it for the store.
The key is not in the repository. CI reads it from four secrets —
`HOW_MUCH_KEYSTORE_BASE64`, `HOW_MUCH_KEYSTORE_PASSWORD`, `HOW_MUCH_KEY_ALIAS`,
`HOW_MUCH_KEY_PASSWORD` — and refuses to make a release without them. On a
machine, `signingProperties=` in `local.properties` names a file holding
`storeFile`, `storePassword`, `keyAlias` and `keyPassword`; without it,
`assembleRelease` builds unsigned and the debug build is unaffected.

```sh
./gradlew :app:assembleRelease :app:bundleRelease
```

Lose the key and Play can be asked to accept a new upload key, but the APK on
GitHub cannot be updated in place: a phone would have to uninstall first.

## What `:app` does today

The app opens on a home screen: the two currencies as `ARS → USD`, each side
opening a picker, and the ways to get an amount into them — **Calculator**, and
under "From a picture", **Camera** and **Gallery**. All three share the same
two currencies, so choosing one anywhere changes it everywhere.

**Camera** is a viewfinder, a shutter and a gallery button, and nothing is read
until the shutter is pressed. The photograph is then read thoroughly, the same
as a gallery picture (see below), and shown with the conversions on it; back
returns to the viewfinder for another.

**Live reading is off, not gone.** With `LIVE_RECOGNITION` in `MainActivity`
set to true, the camera also reads its viewfinder frame by frame through
[`OcrEngine`](app/src/main/kotlin/converter/android/ocr/OcrEngine.kt) and
`:core`, and draws the conversions over the live picture, with one line under
it saying what came of the last look. Off, the analyser is not bound at all, so
no frame is read and no battery spent on it. The sections below on what a frame
costs, box tracking and frame voting describe that mode.

The icons are drawn rather than imported: Material's extended set is several
megabytes for the sake of a circle, a picture frame, a camera and a calculator.

The version sits in the top corner of the home screen in small grey type, so a
report from a shop can say which build it was about.

A picture that arrives before the models have loaded waits for them, rather
than being reported as having no price in it.

## The calculator

Two rows, one per currency, and a keypad. Either row can be typed into: tap the
other one and it becomes the one being typed, starting from the amount it
showed, so a digit replaces that amount and an operator carries on from it. The
keys do arithmetic — `+ − × ÷`, times and divide first — for three of
something or a few prices added up, and the result shows under the sum as it
is typed.

Under the rows, what one unit is worth, whichever way round gives a number above
one (`1 USD = 1,529 ARS`, not `1 ARS = 0.00065 USD`), and how old the rates are:
offline, yesterday's table is still used, and the reader should know it is
yesterday's. A division by zero, or a result too large to be an amount anyone
meant, is no amount rather than infinity.

The arithmetic is [`Calculation`](core/src/main/kotlin/converter/core/Calculator.kt)
in `:core`, with the point always a point; the screen shows the locale's own
separators, and the key is labelled with the locale's decimal mark.

The picker shows a flag beside each currency. They are emoji — two regional
indicator letters, drawn by the phone — so there are no images to bundle and
nothing to keep in step with a design. Which flag stands for which currency is
generated alongside the rest from `COUNTRY_TO_CURRENCY`, with the handful that
several countries share named explicitly: the euro shows the union's own flag
rather than whichever member sorts first. It is a display choice and nothing
reads it back.

The launcher icon is a picture: a camera, and € → $ under it. It is a bitmap in
`mipmap-*`, one per density, filling the middle 72dp of the adaptive icon's 108
— the part a launcher shows. Its own rounded corners are cut to transparency
and a gradient in the same blues sits behind, so a launcher whose mask is
squarer than those corners shows blue there rather than the light grey the
picture came with. There is no monochrome layer, because a picture drawn in one
colour is a solid square.

## What a frame costs, and where

Measured on an emulator, over a shelf of six tags surrounded by the fine print
a real tag carries:

| stage | shelf, first frame | shelf, settled |
| --- | --- | --- |
| detect | 160 ms | 115 ms |
| find boxes | 3 ms | 3 ms |
| read text | 103 ms | 1 ms |
| **total** | **266 ms** | **114 ms** |

Reading is what grows with a busy scene — about 13 ms a box, and a shelf is
mostly text that could never be a price. So boxes are read tallest first, since
a price is the large type on a tag, and only twelve afresh per frame. Nothing is
lost to the cap: a box left unread this frame is read in the next, and one read
now is free in every frame after, because the tracker carries it. On the shelf
all six prices arrive in the first frame regardless, because the ordering puts
them at the front. Text shorter than about a hundredth of the frame is skipped
outright — it reads as noise at any budget.

That took the shelf from 535 ms a frame to 266, and to 114 once the scene
settles. What is left is mostly detection, which is roughly constant.

Two things about a camera frame that a fixture never shows, and that the first
build on a real phone got wrong:

- **It arrives sideways.** A phone's back camera is mounted landscape, so held
  upright it delivers the scene rotated ninety degrees, and the recognizer has
  no model for rotated text. Without turning the frame upright the app reads
  nothing at all — which is what happened, and which no test here could catch,
  since every test feeds a bitmap that is already the right way up.
  `readsNothingFromASidewaysFrame` now pins it.
- **The default analysis resolution is 640x480**, which leaves a price tag
  across a room a few pixels tall; the detector scales its input to a long side
  of 960 in any case, so anything less is capacity thrown away.

## The prefix glyph

A currency symbol written in front of the digits sits at the very edge of the
detector's box, where the recognizer reads it worst — and reading it wrong costs
far more than missing it, because a glyph misread as a digit fuses into the
number. `₴1 200,50 грн` came back as `21 200,50 грн`: seventeen times too large,
with its currency still attached, so nothing downstream could refuse it. That is
the one failure this project has been guarding against all along, and the only
one measured that produces a confidently wrong price rather than none.

It cannot be caught by a rule about content. `21 200,50 грн` is a perfectly
ordinary price, and refusing every amount that starts with a 2 would throw away
far more than it saved: the evidence that a glyph was ever there is destroyed by
the recognizer.

So the fix is geometric. Crops carry a third of the text's height of margin on
each side, and that is enough to change the failure: the glyph comes back as a
letter, or not at all, and the number is intact. Probed across the symbols
written this way — `₴ ₹ ¥ ₩ $ € £`, clean, small and under camera conditions —
`$ € £` are never lost, while `₴ ₹ ¥ ₩` are, and only `₴` and `₹` were ever read
as digits. The margin costs nothing on the benchmark corpus, and more of it
starts losing readings. `doesNotSwallowACurrencyGlyphIntoTheNumber` pins the
case that used to fail.

## The symbol the detector missed

A symbol written apart from its digits — a chalked `$ 5600 x Kg` on a street
sign — is thin and sparse, and the detector finds it only as a scrap: one the
recognizer cannot read at all, or reads as a digit or two (`8`, `69`, `89` at
different sizes of the same photo). The number is read, the symbol is not, and
a bare number is rightly left alone.

So a line that starts with a bare number gets a second look, with a line's
height of room before it, once everything else in the frame has been read. Text
read there stands in the way — reading across a neighbour could only confuse
the two — unless it is a lone glyph, one or two characters in a box about as
wide as it is tall, which just before a bare number is likelier the symbol
itself. If the look finds a symbol, that scrap's reading is dropped.

The symbol reads in some crops and not others, so a few are tried: square to
the frame first, then turned to the line's angle, at full height and trimmed
toward the digits' band. A hand-written line's angle is fitted to all of it —
the "x Kg" slopes down and made this one ten degrees — and turning by it leans
the symbol out of shape; the box is as tall as the "Kg" hanging below.

Whatever is tried, only a symbol can come of it.
[`withPrefixFrom`](core/src/main/kotlin/converter/core/PrefixLook.kt) takes a
known currency symbol — one with a mark that is not a letter — and only when it
stands directly before the very number the first reading had; the digits are
always the first reading's, so a symbol misread as a digit changes the number
and is refused. The symbol is written touching the number, `$5600×Kg`: with a
space, a scrap read as `8` beside it would make `8 $ 5600` a price of eight.

A live frame takes at most four second looks of three crops each; the shelf's
first frame went from 266 ms to about 290.

The first version of this passed its test and failed on a phone. The test had
the photo at the size a messenger delivered it, where the scrap happened to read
as nothing; at the size a phone's own photo is read at, it read as a digit and
blocked the look. `SignPhotoTest` now reads the sign at several sizes. At the
original size it holds all seven prices; enlarged, only the chalked one —
because at 1.5625 times the recognizer reads the printed `$16.800` as
`$16.00`, a wrong price that has nothing to do with the chalk, and was there
before. An enlarged messenger JPEG is not a sharp photo, so how often that
happens with a real one is not known yet.

## Box tracking

Detection costs one model run for a whole frame; recognition costs one run per
box. On a still scene those boxes barely move, so re-reading each of them every
frame is most of the work and almost none of the information.
[`TrackerState.reuseFor`](core/src/main/kotlin/converter/core/BoxTracking.kt)
carries a reading forward when a box overlaps its predecessor by three quarters,
and gives up after three frames so a stale price cannot sit on screen after the
thing it was read from changed. Measured on the emulator, reading the same
eight-box frame twice went from 901 ms to 440 ms.

**The trap this creates, and the answer to it.** A reading carried forward looks
exactly like a reading several frames agree on, so left alone, tracking would
manufacture the very agreement the voting exists to demand — one wrong
recognition could confirm itself by being copied. So a carried line is marked
`reused`, and the voting holds such a price's score instead of raising it: the
price stays on screen, but only an independent recognition counts as evidence.

For the same reason an engine that carries readings needs `reset()` before an
image that is not a continuation of the last one — a fresh photo, or the next
of a batch. The corpus test calls it between fixtures; without it, a reading
could be carried onto text from a different picture.

## Frame voting

One frame is not evidence. A recognizer reading a live camera disagrees with
itself between frames — a price flickers out as a hand moves, comes back a digit
different — so showing the newest frame's answer would both blink and put every
momentary misreading on screen.

[`VoteState.observe`](core/src/main/kotlin/converter/core/FrameVoting.kt) scores
each reading: a point for every frame it appears in, a point off for every frame
it does not. It has to reach three before it is shown, and then survives until
its score runs out, which is what stops one blurred frame blanking the display.
The score is capped, so a price stared at for a minute does not linger for a
minute after the camera moves on.

What it does not do is adjudicate. Two readings that are both persistent are
both shown — better that the reader sees the disagreement than that one is
picked confidently and wrongly. It thins out noise; it does not know which of
two steady answers is true.

## Rates

One USD-based table from [open.er-api.com](https://open.er-api.com), every pair
derived from it as a cross rate — the same arrangement
`extension/src/background.js` uses, and the same free service.

**Once a day, not every six hours.** The service publishes a new table daily
and says when the next is due (`time_next_update_unix`); asking before then
fetches the same table again. So a table is kept for six hours whatever
happens — what the privacy policy promises — and after that until the next
publication, but never past a day and a half, in case the schedule slips.
Without a schedule, six hours is the rule, as before. [`isFresh`](core/src/main/kotlin/converter/core/Rates.kt)
holds it, and the extension's `background.js` has the same rule.

**Which day, not how long ago.** The calculator says "rates of Sep 29": the day
the service published them is what says how current a rate is; when this phone
downloaded them says nothing. A table cached before that was read shows the
download time instead.

**The credit is required.** The service's free access asks for a link,
"Rates By Exchange Rate API", wherever its rates are used, and may cut off
access without it. It sits under the rate in the calculator and beside the
count on a picture's screen, and in the extension's popup. Its terms allow
commercial use and caching on the device; they do not allow passing the table
on, and they recommend against using the rates for actual transactions — a
guide to what something costs, not a quote.

The policy lives in `:core` as [`resolveRates`](core/src/main/kotlin/converter/core/Rates.kt),
which takes the fetch as an argument rather than performing it. That is what
makes the interesting part testable without a network: a fresh cache is used
without a request, a forced refresh ignores freshness, and a failed fetch falls
back to the old table rather than to nothing — yesterday's rate is a good
answer, and no answer is not. A response that parses to nothing counts as a
failure, not as rates.

`RatesRepository` in `:app` supplies the network and the storage, and nothing
else. It keeps the response as the raw JSON it arrived as, so there is only one
parser to agree with.

## A photograph, not only a camera

**Photo** takes a still; **Gallery** opens one already taken. Either is read and
shown with its conversions drawn on it, the same overlay as the live view.

A still is read *thoroughly*: the per-frame reading budget is lifted, because
there is no next frame to defer the rest to and no reason to hurry. That makes
it the better tool for a crowded shelf, where the live view reads the largest
text first and catches up over several frames.

**Gallery** opens the system's photo picker inside the app, and one tap on a
picture opens it. The picker as its own screen — which is what Android 16 shows
once Google Photos is part of it — treats even a single choice as a selection
to confirm and asks for Done; embedded, it reports a choice the moment it is
made. It is still the system's picker, drawn by the system, so the app sees
only the picture it is given and asks for no permission to see it. Embedding
needs Android 14 with SDK extension 15; without it, the button opens the
standalone picker as before, and so does a session that fails to start. The
library, `androidx.photopicker`, is still an alpha.

**Pinch or double tap to zoom.** The picture and its labels are magnified as
one, so a label stays on its price at any zoom and nothing is read again; the
picture never shrinks below the whole of it or slides past its edge. The
arithmetic is [`Zoom`](core/src/main/kotlin/converter/core/Zoom.kt), tested
without a screen.

**Share** works from the other side: the app is listed when any app shares a
picture, and the picture opens as if it had been picked. It can arrive before
the models have loaded — a share starts the app cold — so a still waits for
the recognizer rather than reading with the placeholder and reporting no price
in a picture full of them. The same wait covers a photo taken in the first
second. Back from the picture goes to the camera, and back again to the app
that shared it.

Two things a still needs that a frame does not:

- **EXIF orientation.** A camera writes the picture in the sensor's frame and
  records how the phone was held in a tag; nothing in `BitmapFactory` applies
  it. Skipped, every portrait photograph arrives sideways and reads as
  nothing — the same failure the live camera had, arriving by another route.
- **A lock on the engine.** The still is read from its own thread while the
  camera keeps running, and two readings sharing one tracker would carry text
  from one picture onto another.

## Keeping a picture, and reporting one

Once a picture is read, two buttons sit in its top corner.

**Save** puts it in the gallery, in Pictures/How Much, with the labels drawn
into it. It is the same drawing as the screen's, onto a bitmap at the
picture's own size rather than onto a view, so the saved copy cannot come out
labelled differently from what was on screen. From Android 10 the gallery takes
it without any permission; before that the app asks for storage first.

**Report** is for a picture that did not come out right. It asks what was
wrong, then writes one zip and opens a mail app with it — a message addressed
to the developer, with a subject, a line of summary and the zip attached, sent
only when the reader sends it there. With one mail app on the phone it opens
directly; with several, a chooser lists only those, since a chat app would
ignore the address. With none, or in a build without an address, the share
sheet opens instead and the reader picks where it goes.

The address is not in the repository, which is public: the build takes it from
the `HOW_MUCH_REPORT_EMAIL` environment variable — a secret of the same name in
CI — or from `reportEmail=` in `local.properties`. The app never shows it; the
mail app's own "To" does, since that is where the message is sent from, and
anyone who unpacks the APK can find it.

In the zip:

- `report.txt` — the build and the phone; the currencies, each with what was
  detected; the receipt switch; the rate used and when it was fetched; every
  price found, with what it became and its box; and every line the recognizer
  returned, quoted, with its confidence and box.
- `original.jpg` (or `.png`, `.webp`) — the picture's own file when it came
  from the gallery or a share, its pixels untouched. The app reads a smaller,
  turned copy, and a fix has to hold at the original's size too: one verified
  only at a messenger's size has already failed on the phone. Its GPS tags are
  removed first — a phone writes where a photo was taken into it, and
  `ExportsTest` holds the copy to having none while keeping the image data and
  the orientation byte for byte. A format whose EXIF cannot be rewritten, HEIC
  among them, is left out rather than sent with a location.
- `as-read.jpg` otherwise — for a photo taken in the app, which has no file of
  its own, or an original left out — the picture exactly as the recognizer saw
  it, at JPEG quality 100, and with no EXIF at all.
- `as-shown.jpg` — the picture with its labels, as the reader saw it.

A zip rather than a picture because a messenger compresses a picture it is
handed as one, and the failure may not survive the compression; a file travels
as it is. Not a GitHub issue, which the extension uses: an issue link carries
text and no picture, and the repository is public, while a receipt can show a
card's last digits and a name. The dialog says that before anything is sent.

## A receipt, where the currency is printed nowhere

A shop's receipt is mostly amounts with no currency beside them, so the photo
screen has a **Receipt** switch, with the two currencies next to it. With it on,
every number written to the cent is taken to be in the source currency; with no
source currency known, turning it on opens the picker, since there is nothing
to read the amounts in until one is chosen.

It is a switch and not a guess. Anywhere but a receipt a bare number is as
likely a weight or a code as a price, and reading those as money is the wrong
price this project refuses. [`findBareAmounts`](core/src/main/kotlin/converter/core/Receipts.kt)
is what decides which numbers are amounts, and a receipt is dense with numbers
that are not — dates, times, weights, article codes, tax IDs:

- **Exactly two decimals, standing alone.** `32648,00` and `-9794,40` are
  amounts; `0.654`, `10/09/2026`, `13:24:07` and `30-54808315-6` are not.
- **A number before a multiplication sign is a quantity.** In
  `2,50 x 14000,00` only the price per kilo is converted.
- **A discount keeps its minus**, and the conversion shows it.
- **`0,00` is skipped**: change not given says nothing worth a label.
- **Without cents, a thousands separator.** A peso in Chile has no minor unit,
  and its receipt says `8.990` and `24.040`. For such a currency — the ones
  `convertedDigits` already shows without cents — a whole number counts too,
  grouped the way that currency writes it: a point, or a comma for the yen and
  the won. Not a number followed by a dash (`76.123.456-7`, a Chilean tax ID)
  or by a unit (`1.250 kg`).
- **Under a thousand, by where it stands.** `990` is written exactly like a
  till number, so its text cannot decide. It is read when it stands in the
  column of amounts: three digits, right-aligned with the nearest amount above
  it and the nearest below, to within a third of its height. The edge is
  interpolated between those two, since a receipt in a hand leans. A number
  above the first amount or below the last has no column to stand in, and only
  amounts with a separator make the column, so one short number cannot vouch
  for the next. What this would still take for money is a count of a hundred
  or more printed in the amounts' column. `AmountColumnTest` holds it to every
  line, with its box, of the Chilean receipt below.
- **A currency printed on the receipt still wins** over the switch.

The recognizer's lines are kept with the photograph, so changing a currency or
the switch re-reads the prices from them without reading the picture again.

`ReceiptTest` holds these rules to the lines of a real supermarket receipt, and
`ReceiptPhotoTest` holds the engine to a photograph of it — thermal paper,
curled, in a hand, at the size a gallery picture is read at, with the card's
last digits blanked. All thirteen amounts are read, discounts with their minus,
and nothing else on it is taken for money.

It also holds the engine to a Chilean one, which arrived as a screenshot of
someone's story — 923 pixels wide, tilted, as good as that receipt will get —
with the account's name and picture and the card's digits blanked. All twelve
amounts are read, the drink's `990` among them, and nothing else.

The minus was the hard part. It is a faint dotted dash, and the recognizer
reads it as `-`, as `~`, or not at all, and not at all is a discount shown as a
purchase. `~` is taken as a minus. Losing it altogether turned out to be the
rotation's doing: the receipt was three degrees off square, turning the crop by
that much resampled the dash away, and a tilt that small is now read as none.
Wider side margins did not bring it back, and a margin above and below did but
cost the tag at twenty-five degrees its price.

## The conversion goes on the price

The converted amount is drawn over the price it was read from, not listed under
the viewfinder. With several prices on a shelf, a list makes the reader match
labels to tags themselves, which is most of the work; put the answer where the
question is and there is nothing to match up.

Three pieces make that possible, and each is in `:core` where it can be checked
without a camera:

- `locatePrices` keeps the box a price was read from. Merging remembers which
  span of the joined text came from which line, so two prices on one line get
  their own boxes rather than a shared one, and a price split across boxes gets
  both.
- A label covers the price and not the rest of its line. A recognizer often
  returns a price with its neighbours in one box — `2,332 x 14000,00` on a
  receipt — so the box is cut down to the characters the price occupies,
  assuming they are evenly spaced: exact for a receipt's fixed-width type,
  close enough for a tag's.
- `Viewport` maps image pixels onto the view showing them. The preview is set to
  fit rather than fill: filling crops the frame, and a price the camera read in
  the cropped part would be converted and then drawn off-screen.
- The analyser remembers where each price was last seen, so a label stays put
  through the frames the voting keeps a price alive after it stops being read.
- Neighbouring labels do not cover each other. A label is its price's box and
  a margin, and on a receipt the lines sit so close — and a detector's box for
  slightly turned text is so much taller than the text — that the margins, and
  near the subtotal the boxes themselves, overlapped: three labels piled into
  one unreadable block. [`labelAreas`](core/src/main/kotlin/converter/core/LabelLayout.kt)
  lays them out before anything is drawn. Two that overlap are divided along
  whatever separates them: margins first, so each still covers its whole
  price; only where the boxes overlap too does the border fall halfway between
  their lines. The type is sized to what is left, so a crowded label is drawn
  smaller, on its own price. `ReceiptPhotoTest` holds the real receipt to it:
  three overlapping pairs before, none after, every label still on its price.

`PriceOverlayTest` renders the overlay over a frame of known size and checks the
pixels, because a label fifty pixels off still draws, still says the right
number, and still points at the wrong thing — a mistake no other test here can
see. Nothing is drawn for a price with no rate: a label repeating the tag would
be clutter, and one guessing would be worse.

## Two currencies, not one

The app exists for one situation: standing in another country, where the prices
in front of you are in a currency you do not think in. So there are two
questions, and they are not the same question.

**What the prices are in** is detected from the mobile network's country —
where the phone is standing. It is allowed to be unknown, and says so, because
its job is to decide what an ambiguous symbol means: `$` is a peso in half of
Latin America, `kr` is three different krone. A wrong answer there does not
merely fail to help, it converts confidently at the wrong rate, so no answer is
the safe one and those symbols simply go unread.

**What to convert into** comes from the SIM's country first, which travels with
the reader and still says where they are from while they are abroad, then the
locale, then its language.

Either can be set by hand: the two underlined currencies under the viewfinder
open a picker each.

**Never one currency twice.** Converting a currency into itself answers
nothing, so each picker leaves out the other side's currency, and "Automatic"
cannot be picked where detection would land on it. When the two still come out
the same — at home, both detections agree — a choice outranks a detection and
the side without one is left unknown: the target shows `?`, nothing is
converted, and the panel asks what to convert into. A price already in the
target currency gets no label, since it would only repeat the tag.
`resolveCurrencies` holds the rule.

An earlier version of this got the direction backwards — it detected the
*target* from where the phone was, which in Georgia would have converted lari
into lari, useless in exactly the situation the app is for. The tests now name
the case: `abroad, the prices are local and the reader is not`.

**Not the location permission.** The network country answers the question
without GPS, without a geocoder lookup over the network, and without anything
about the reader leaving the device — the promise PRIVACY.md makes for the
extension.


## The engine

[`PaddleOnnxEngine`](app/src/main/kotlin/converter/android/ocr/PaddleOnnxEngine.kt)
runs PP-OCRv5's mobile detector and the East Slavic recognizer through **ONNX
Runtime** — not Paddle Lite, which has no official Android artifact on Maven
Central; what is published there under that name is third-party repackaging.

Only what needs Android stays in `:app`: bitmaps and tensors. The parts that can
be tested without a device are in `:core` — `detectBoxes` for the detector's
probability map and `ctcDecode` for the recognizer's output.

**Flood fill, not contour tracing**, so there is no OpenCV — and each region is
fitted with a rotated rectangle rather than an upright one, because a shelf is
usually seen from the side.

That rotation started as a simplification in the other direction, and the
benchmark supported it: on a corpus of near-upright text, upright boxes cost
nothing. A real shop said otherwise, and not by failing quietly. At eight
degrees `$ 3.648,75` read as **1648**, and obliquely as **38648** — a plausible
wrong price, the one outcome this project refuses. The direction now comes from
the region's second moments and the extents from projecting its pixels onto it,
so the crop handed to the recognizer is upright. A region not clearly longer
than it is wide keeps its angle at zero: a single character has no reading
direction, and a confident wrong angle is worse than none.

Tilts of eight, fifteen and twenty-five degrees now read correctly and invent
nothing. A tilt under three degrees is read as none: turning a crop resamples
it, which costs faint glyphs and gains nothing on text that close to level. Thirty degrees of *perspective* still misreads — foreshortening is not
rotation, and no single angle undoes it. `severePerspectiveIsStillMisread`
records that rather than papering over it.

`PaddleOnnxEngineTest` runs the engine on a device over the benchmark's own
twelve images and holds it to the desktop pipeline's result: 25 of the 26
prices, nothing invented. Its one miss is the hryvnia case, where `₴1 200,50`
is read as `21 200,50` — the prefix glyph swallowed into the number, which is
the failure the safety rule above exists for.

```sh
./gradlew :app:connectedDebugAndroidTest
```

`core` is deliberately off the Android SDK: these rules are what Android and
iOS share, and keeping them on plain Kotlin/JVM means they can be tested with a
JDK alone. `BenchCorpusTest` replays the engine output recorded in
`tools/ocr-bench` through this port and holds it to what was measured there —
Vision at 25 of 26 prices with nothing invented, PaddleOCR reaching the same
only with both mitigations below, and neither mitigation enough on its own.

`CurrencyTables.kt` is generated from the extension's own `currency.js` by
[`tools/gen-currency-kt.py`](../tools/gen-currency-kt.py) — the two cannot
agree about what a currency token is if the tables are copied by hand. Change
the JavaScript and regenerate; `--check` fails when the file is stale.

The rest of this file records the decisions the app should start from, so the
measurements behind them do not have to be repeated.

## OCR engine: PaddleOCR

`PP-OCRv5_mobile_det` for detection plus `eslav_PP-OCRv5_mobile_rec` — the East
Slavic recognizer — for recognition. Together about 12.5 MB of model, small
enough to ship in the APK or fetch on first run.

**ML Kit is not an option.** It has five script models — Latin, Chinese,
Devanagari, Japanese, Korean — and no Cyrillic one. A Latin-only recognizer does
not merely miss Cyrillic prices, it corrupts them: `180 ₽` comes back as `18oP`
and `3 490 ₽` as `34902`. Scored on the bench corpus it reaches 18 of 26 against
PaddleOCR's 25. Tesseract is worse still and was the only engine to return a
confidently wrong price.

Name both models explicitly when constructing the pipeline. Naming only the
detector makes PaddleOCR drop the language-derived recognizer and fall back to a
Latin one, which turns a Cyrillic build into a Latin one without any error.

## Two things the engine does not do on its own

PaddleOCR only reaches 25/26 with both of these. Either one alone leaves it at
11, and homoglyph mapping without box merging produces a wrong price.

- **Homoglyph mapping.** The recognizer drifts between alphabets on currency
  glyphs: `₽` arrives as Latin `P` or Cyrillic `Р`, `руб.` as `pу6.`, `€` as
  Ukrainian `Є`. The substitutions are stable and enumerable — see `HOMOGLYPHS`
  in [`../tools/ocr-bench/bench.py`](../tools/ocr-bench/bench.py). They are OCR
  artifacts and belong to the OCR layer only; they must never reach the shared
  currency table, where `P → RUB` would convert English prose.
- **Box merging.** The detector splits a large price across boxes — on the
  bench's price tag, `1299` and `P` land in separate boxes that touch. Cluster
  boxes into rows first, then order left to right within a row; sorting by y
  before clustering lets a right-hand fragment strand the digits to its left,
  which turns `1 299` into `299`.

## Safety rule

Never show a conversion that might be wrong — a missed price is a feature that
did not fire, a wrong one is a lie about what something costs. The one shape
that yields a plausible wrong amount is a prefix symbol read as a digit and
fused into the number: `₴1 200,50` comes back as `21 200,50`. Treat a prefix
glyph touching a number as suspect, and for live camera require the same
reading across several frames before drawing it.

**Not by gating on confidence.** That was the plan until it was measured, and
the corpus says the recognizers are confident when they are wrong and hesitant
when they are right: Tesseract reports its one invented price at 0.90, while
the seven Vision lines sitting at 0.50 are all correct. No threshold removes a
wrong reading before it starts removing right ones — gating Vision above 0.5
costs five real prices and removes nothing. `ConfidenceGateTest` holds that
measurement so the idea is not quietly reintroduced.

## Performance

On the bench (desktop CPU, through Python) the mobile configuration averages
794 ms per image against 5 s for the server detector. Native inference on a
phone will be faster, but not 30 fps: run detection on a downscaled frame, track
boxes between frames, and re-recognize only a box that changed.

## Shared with iOS

The engine is the platform layer; everything above it is not. Currency tables,
number grammar, homoglyph mapping, box merging, rate fetching and frame voting
are the same on both platforms — the bench shows merging and homoglyphs alone
are worth 14 of the 26 prices, so that logic is the product, not glue. Kotlin
Multiplatform is the obvious seam; [`../ios`](../ios) is the other side of it.
