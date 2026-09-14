# iOS app

Not written yet. This file records the decisions the first commit should start
from, so the measurements behind them do not have to be repeated.

## OCR engine: Vision

Apple's own `VNRecognizeTextRequest`, which needs no dependency, no network and
no key, and lists both `ru-RU` and `uk-UA` among its supported languages. On the
bench corpus it reads 25 of 26 prices with no wrong answers, including all four
camera cases — defocus, an angled shot, low light, a serif menu.

**Use `.accurate`, not `.fast`.** The fast level is Latin-first and unusable
here: it transliterates Cyrillic into Latin lookalikes (`руб.` → `py6.`,
`грн` → `rpH`) and corrupts digits along the way (`180 ₽` → `18oP`). It scores 1
of 26 raw, and 18 even with homoglyph mapping, against 25 for `.accurate`.
Latency is not the reason to reach for it: `.accurate` ran 85–380 ms per image
on a desktop CPU, which is enough for a few frames a second.

The single miss is `₸` — read as "г", since Kazakh is not in Vision's language
list. Tenge needs its own handling, or accepting that it is unsupported.

## Worth carrying over from the Android side

Vision needs neither the homoglyph table nor box merging to reach its score, but
both belong in the shared layer anyway: PaddleOCR cannot reach the same number
without them, and Vision is not harmed by them. The safety rule applies equally
— a prefix symbol can be read as a digit and fuse into the number, so treat a
prefix glyph touching a number as suspect and gate on confidence. Vision's own
confidence is a usable signal: on the bench it dropped to 0.5 on exactly the
lines carrying `₽`, flagging its own shaky readings.

## Shared with Android

The engine is the platform layer; everything above it is not. Currency tables,
number grammar, homoglyph mapping, box merging, rate fetching and frame voting
are the same on both platforms. Kotlin Multiplatform is the obvious seam;
[`../android`](../android) is the other side of it, and
[`../tools/ocr-bench`](../tools/ocr-bench) is where both engines are measured.
