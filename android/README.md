# Android app

Two modules. `:core` holds the price rules — what counts as a currency token,
how a number is spelled, which frames agree — and is the part iOS will share.
`:app` is the camera and the recognizer around them.

It reads prices off a live camera and shows them converted. What it has not yet
had is a day in a real shop: everything below was measured on fixed images and
an emulator, and the parts built around movement — frame voting, box tracking —
have never met any.

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

Before the app will build, the OCR models have to be exported into its assets —
they are build outputs, not sources, so they are not committed:

```sh
./tools/export-ocr-models.py     # writes det.onnx, rec.onnx, charset.txt
```

To put it on a phone, the debug APK is enough: it is signed with the debug key,
so it sideloads without a keystore.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

It comes to about 61 MB, most of it ONNX Runtime's native library and 12.7 MB
of models. The build keeps only `arm64-v8a`: four copies of that library made a
166 MB APK, and every phone this can run on is 64-bit ARM, as is the emulator on
an Apple Silicon Mac. An Intel-host emulator would need `x86_64` adding back in
`app/build.gradle.kts`.

## What `:app` does today

Camera preview, frames analysed one at a time on a background thread with only
the newest kept, each one passed through [`OcrEngine`](app/src/main/kotlin/converter/android/ocr/OcrEngine.kt)
and then through `:core`, with whatever prices come out listed under the
viewfinder. The UI names the running engine and warns when that engine cannot
read Cyrillic, because a Latin-only recognizer corrupts those prices instead of
missing them.

Frames are not chased at video rate and should not be: recognition runs about
830 ms a frame on an emulator.

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

One USD-based table from [open.er-api.com](https://open.er-api.com), cached for
six hours, every pair derived from it as a cross rate — the same arrangement
`extension/src/background.js` uses, and the same free service.

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

Two simplifications, both measured rather than assumed:

- **Axis-aligned boxes, not rotated ones.** PaddleOCR fits minimum-area
  rectangles, which needs OpenCV. The price rules only ever ask which boxes
  share a line, so the rotation is never read.
- **Flood fill, not contour tracing**, for the same reason.

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
