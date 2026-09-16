#!/usr/bin/env python3
"""Where the time goes on a shelf, and what raising the detector's input buys.

Reports detection and recognition separately, because the fix for one is not
the fix for the other: detection decides what is *found*, recognition decides
what is *read*, and on a shelf full of fine print most of the reading is of
text that could never be a price.
"""
import os, sys, time
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipeline as P
from pipeline import Pipeline, load_png, det_preprocess, connected_boxes, unclip, rec_preprocess, ctc_decode

sys.path.insert(0, "/Users/vbelyakov/GitHub/how-much-converter/tools/ocr-bench")
import bench

HERE = os.path.dirname(os.path.abspath(__file__))


def run(pipe, rgb, long_side, min_height=0):
    P.DET_LONG_SIDE = long_side
    t0 = time.perf_counter()
    tensor, (sx, sy) = det_preprocess(rgb)
    probs = pipe.det.run(None, {pipe.det.get_inputs()[0].name: tensor})[0][0, 0]
    boxes = connected_boxes(probs > P.DET_THRESH, probs)
    det_ms = (time.perf_counter() - t0) * 1000

    t1 = time.perf_counter()
    lines, skipped = [], 0
    for box in boxes:
        grown = unclip(box, probs.shape[1], probs.shape[0])
        if grown is None:
            continue
        x0, y0, x1, y1, _ = grown
        ox0, oy0 = max(0, int(x0 * sx)), max(0, int(y0 * sy))
        ox1, oy1 = min(rgb.shape[1], int(np.ceil(x1 * sx))), min(rgb.shape[0], int(np.ceil(y1 * sy)))
        if ox1 - ox0 < 3 or oy1 - oy0 < 3:
            continue
        if (oy1 - oy0) < min_height:
            skipped += 1
            continue
        crop = rgb[oy0:oy1, ox0:ox1]
        t = rec_preprocess(crop)
        if t is None:
            continue
        logits = pipe.rec.run(None, {pipe.rec.get_inputs()[0].name: t})[0][0]
        text, conf = ctc_decode(logits, pipe.charset)
        if text.strip():
            lines.append({"text": text, "confidence": conf,
                          "box": [float(ox0), float(oy0), float(ox1), float(oy1)]})
    rec_ms = (time.perf_counter() - t1) * 1000
    return lines, det_ms, rec_ms, len(boxes), skipped


PRICE_RE = bench.price_finder(bench.SYMBOL_TO_CODE)


def prices(lines):
    """Reuses the benchmark's own merge and price rules, so the count here means
    the same thing it means there."""
    got = []
    for text in bench_merge(lines):
        got += bench.find_prices(text, bench.SYMBOL_TO_CODE, PRICE_RE)
    return got


def bench_merge(lines):
    return bench.merge_boxes(lines)


def main():
    pipe = Pipeline()
    print(f"{'case':<7} {'det px':>7} {'minH':>5} {'det ms':>7} {'rec ms':>7} "
          f"{'boxes':>6} {'skip':>5} {'read':>5}  prices")
    print("-" * 92)
    for case in ("close", "shelf", "far"):
        rgb = load_png(os.path.join(HERE, "shelf", f"{case}.png"))
        for long_side in (960, 1280, 1600):
            for min_h in (0, 14):
                lines, det_ms, rec_ms, nboxes, skipped = run(pipe, rgb, long_side, min_h)
                found = sorted({p[0] for p in prices(lines)})
                print(f"{case:<7} {long_side:>7} {min_h:>5} {det_ms:>7.0f} {rec_ms:>7.0f} "
                      f"{nboxes:>6} {skipped:>5} {len(lines):>5}  {len(found)} {found[:4]}")
        print()


if __name__ == "__main__":
    main()
