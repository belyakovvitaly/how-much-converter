# Android app

Two modules. `:core` holds the price rules and is done enough to be under test;
`:app` is the camera shell around them, and is still missing the one thing that
makes it useful — a recognizer.

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

## What `:app` does today

Camera preview, frames analysed one at a time on a background thread with only
the newest kept, each one passed through [`OcrEngine`](app/src/main/kotlin/converter/android/ocr/OcrEngine.kt)
and then through `:core`, with whatever prices come out listed under the
viewfinder. The UI names the running engine and warns when that engine cannot
read Cyrillic, because a Latin-only recognizer corrupts those prices instead of
missing them.

Frames are not chased at video rate and should not be: recognition runs about
830 ms a frame on an emulator.

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

Until there is a setting for it, the target currency is the one belonging to the
device's locale, falling back to USD.

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
