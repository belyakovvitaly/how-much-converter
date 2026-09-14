# Android app

Not written yet. This file records the decisions the first commit should start
from, so the measurements behind them do not have to be repeated.

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
glyph touching a number as suspect, gate on recognizer confidence, and for live
camera require the same reading across several frames before drawing it.

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
