#!/usr/bin/env python3
"""Runs PaddleOCR over the fixture set and writes out/paddle-<config>.json.

The default is the configuration that would actually ship on a phone: the
mobile detector plus the East Slavic (Cyrillic) mobile recognizer, with the
document orientation and unwarping preprocessors off — they cost time and do
nothing for a photo of a price tag.

    pip install paddlepaddle paddleocr
    ./run_paddle.py              # mobile detector + Cyrillic mobile recognizer
    ./run_paddle.py --server     # what PaddleOCR(lang="ru") picks on its own

Naming only the detector is a trap: PaddleOCR then drops the language-derived
recognizer and falls back to a Latin one, which silently turns the Cyrillic
test into a Latin one. Both models are therefore always named explicitly.
"""
import argparse
import glob
import json
import pathlib
import sys
import time
import warnings

warnings.filterwarnings("ignore")

HERE = pathlib.Path(__file__).resolve().parent

MOBILE = dict(text_detection_model_name="PP-OCRv5_mobile_det",
              text_recognition_model_name="eslav_PP-OCRv5_mobile_rec")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", action="store_true",
                    help="let PaddleOCR pick its own models (server detector)")
    ap.add_argument("--lang", default="ru")
    args = ap.parse_args()

    from paddleocr import PaddleOCR

    kwargs = dict(lang=args.lang,
                  use_doc_orientation_classify=False,
                  use_doc_unwarping=False,
                  use_textline_orientation=False)
    if not args.server:
        kwargs.update(MOBILE)
    ocr = PaddleOCR(**kwargs)

    images = []
    for path in sorted(glob.glob(str(HERE / "img" / "*.png"))):
        stem = pathlib.Path(path).stem
        start = time.perf_counter()
        raw = ocr.predict(path)
        ms = (time.perf_counter() - start) * 1000

        lines = []
        for page in raw or []:
            page = page if isinstance(page, dict) else getattr(page, "json", page)
            if not isinstance(page, dict):
                continue
            page = page.get("res", page)
            texts = page.get("rec_texts", [])
            scores = page.get("rec_scores", [1.0] * len(texts))
            polys = page.get("rec_polys", page.get("dt_polys", [None] * len(texts)))
            for text, confidence, poly in zip(texts, scores, polys):
                box = None
                if poly is not None:
                    xs = [float(p[0]) for p in poly]
                    ys = [float(p[1]) for p in poly]
                    box = [min(xs), min(ys), max(xs), max(ys)]
                lines.append({"text": text, "confidence": float(confidence), "box": box})

        images.append({"image": stem, "ms": ms, "lines": lines})
        print(f"{stem}  [{ms:.0f} ms]", file=sys.stderr)
        for line in lines:
            print(f"    {line['confidence']:.2f}  {line['text']!r}", file=sys.stderr)

    config = "server det + ru rec" if args.server else "mobile det + eslav rec"
    out_dir = HERE / "out"
    out_dir.mkdir(exist_ok=True)
    dest = out_dir / ("paddle-server.json" if args.server else "paddle-mobile.json")
    dest.write_text(json.dumps(
        {"engine": "PaddleOCR", "config": config, "images": images},
        ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    avg = sum(i["ms"] for i in images) / len(images)
    print(f"\navg {avg:.0f} ms -> {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
