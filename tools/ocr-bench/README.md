# OCR bench

Measures whether an OCR engine is good enough to build the phone version of
this converter on — the one that reads prices off a photo or a live camera
instead of off the DOM.

The question it answers is not "did the engine read the text" but **would the
extension's own price rules have produced the right conversion**. So the
recognized text is run through the symbol table, number grammar and
`parseAmount` of [`extension/src/currency.js`](../../extension/src/currency.js),
and a price counts only when the amount *and* the currency both come out
right.

Wrong answers get their own column and are never netted against hits. For a
currency converter a confidently wrong price is worse than no price: a missed
price is a feature that did not fire, a wrong one is a lie about what something
costs.

## The corpus

12 images, 26 prices, in `img/` — committed, because a benchmark is only
comparable over time if the pixels stay put and Chrome's font rendering does
not. Eight are clean renders (price tag, product list, marketplace, receipt,
prose, mixed scripts, hryvnia, tenge); four simulate a phone camera — defocus
and tilt, a shot taken at an angle, low light, a serif menu.

Currencies are deliberately Cyrillic-heavy (`₽ ₴ ₸ руб. грн тг рублей`), since
that is where the engines diverge; Latin ones are read correctly by everything.

## Running it

```sh
# Apple Vision — the engine an iOS build would use
swiftc -O run_vision.swift -o .build/run_vision
./.build/run_vision            # accurate
./.build/run_vision --fast     # the realtime-oriented level

# PaddleOCR — the Android candidate
pip install paddlepaddle paddleocr
./run_paddle.py

# Tesseract
brew install tesseract tesseract-lang
./run_tesseract.py

./bench.py out/*.json --detail
```

Each runner writes `out/<engine>.json` in one shape — per image, the recognized
lines with confidence and a top-left-origin pixel box. `bench.py` scores every
file through four variants, so the contribution of each mitigation is visible
rather than assumed.

`gen.py` rewrites the corpus and `truth.json`; it needs Chrome and only wants
running when a case changes.

## What it found

| Engine | Best | Missed | Wrong | Latency* |
| --- | --- | --- | --- | --- |
| Apple Vision, accurate | **25 / 26** | 1 | **0** | 85–380 ms |
| PaddleOCR mobile + merge + homoglyphs | **25 / 26** | 1 | **0** | 441–1439 ms |
| Apple Vision, fast + homoglyphs | 18 / 26 | 8 | 0 | 9–75 ms |
| Tesseract rus+eng + homoglyphs | 18 / 26 | 8 | **1** | 78–148 ms |

<sub>* desktop CPU, not a phone; Paddle's is through Python</sub>

Vision works out of the box and supports `ru-RU` and `uk-UA` officially. Its
one miss is `₸`, read as "г" — Kazakh is not in its language list.

PaddleOCR reaches the same score, but only with **both** of the mitigations
below. Neither alone gets past 23, and one of them alone produces a wrong
answer:

| PaddleOCR mobile | ok | missed | wrong |
| --- | --- | --- | --- |
| raw | 11 / 26 | 15 | 0 |
| + homoglyphs | 23 / 26 | 3 | 1 |
| + box merge | 11 / 26 | 15 | 0 |
| + both | 25 / 26 | 1 | 0 |

**Homoglyphs.** A Cyrillic-capable recognizer reads words flawlessly but drifts
between alphabets on currency glyphs: `₽` comes back as Latin `P` or Cyrillic
`Р`, `руб.` as `pу6.`, `€` as Ukrainian `Є`. The substitutions are stable and
enumerable, so `HOMOGLYPHS` in `bench.py` maps them back. They live there and
must never be merged into `currency.js` — teaching the extension that `P` means
RUB would convert English prose.

**Box merge.** The detector splits a large price across boxes: in the price tag
`'1299'` lands at x389–726 and `'P'` at x724–810, touching, on one line. This is
the same problem as the second pass in
[`extension/src/content.js`](../../extension/src/content.js), where a price is
split across sibling elements — only the measure of adjacency changes, from DOM
structure to geometry. Rows have to be clustered before anything is ordered
left to right; sorting by y first lets a right-hand fragment open the group and
strand the digits to its left, which turned `1 299` into `299`.

**The one dangerous failure class.** A prefix symbol can be read as a digit and
fuse with the number: `₴1 200,50` comes back as `21 200,50`. Today that is
harmless — the currency fails to match, so nothing is shown — but it is the one
shape that can yield a plausible wrong amount. Whatever ships should treat a
prefix glyph touching a number as suspect.

Tesseract is the weakest and the only engine that produced a confidently wrong
price: on the angled shot it read `799 руб.` as `199 руб.`

ML Kit is absent because it has no Cyrillic model at all — five scripts, none
of them Cyrillic. Vision's `--fast` level stands in for it: it is a Latin-first
recognizer, and it fails the same way, transliterating Cyrillic into Latin
lookalikes (`руб.` → `py6.`, `грн` → `rpH`) while corrupting digits (`180 ₽` →
`18oP`, `3 490 ₽` → `34902`).

## Keeping it honest

`bench.py` parses the symbol table and number grammar straight out of
`extension/src/currency.js` instead of copying them, so the two cannot drift.
`parseAmount` is a hand port and is pinned by `PARSE_CHECKS`, which runs before
any score is printed — a drifted port fails loudly rather than quietly scoring
against rules the extension no longer uses.
