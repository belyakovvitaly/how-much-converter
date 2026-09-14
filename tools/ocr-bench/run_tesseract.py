#!/usr/bin/env python3
"""Runs Tesseract over the fixture set and writes out/tesseract.json.

    brew install tesseract tesseract-lang
    ./run_tesseract.py            # -l rus+eng

Reads TSV rather than plain text so every line carries a bounding box, the
same as the other runners: box merging has to be available to every engine or
the comparison is not a fair one. Words are grouped into lines by Tesseract's
own block/paragraph/line numbering, and the box is their union.
"""
import argparse
import collections
import csv
import glob
import io
import json
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent


def recognize(path, lang, psm):
    start = time.perf_counter()
    proc = subprocess.run(
        ["tesseract", path, "-", "-l", lang, "--psm", str(psm), "tsv"],
        capture_output=True, text=True)
    ms = (time.perf_counter() - start) * 1000
    if proc.returncode != 0:
        sys.exit(f"tesseract failed on {path}: {proc.stderr.strip()}")

    grouped = collections.OrderedDict()
    reader = csv.DictReader(io.StringIO(proc.stdout), delimiter="\t",
                            quoting=csv.QUOTE_NONE)
    for row in reader:
        text = (row.get("text") or "").strip()
        if not text:
            continue
        try:
            conf = float(row["conf"])
            left, top = int(row["left"]), int(row["top"])
            width, height = int(row["width"]), int(row["height"])
        except (KeyError, ValueError):
            continue
        if conf < 0:
            continue
        key = (row["block_num"], row["par_num"], row["line_num"])
        entry = grouped.setdefault(key, {"words": [], "confs": [],
                                         "box": [left, top, left + width, top + height]})
        entry["words"].append(text)
        entry["confs"].append(conf)
        box = entry["box"]
        box[0], box[1] = min(box[0], left), min(box[1], top)
        box[2], box[3] = max(box[2], left + width), max(box[3], top + height)

    lines = [{"text": " ".join(g["words"]),
              "confidence": sum(g["confs"]) / len(g["confs"]) / 100,
              "box": [float(v) for v in g["box"]]}
             for g in grouped.values()]
    return lines, ms


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", default="rus+eng")
    ap.add_argument("--psm", type=int, default=6)
    args = ap.parse_args()

    images = []
    for path in sorted(glob.glob(str(HERE / "img" / "*.png"))):
        stem = pathlib.Path(path).stem
        lines, ms = recognize(path, args.lang, args.psm)
        images.append({"image": stem, "ms": ms, "lines": lines})
        print(f"{stem}  [{ms:.0f} ms]", file=sys.stderr)
        for line in lines:
            print(f"    {line['confidence']:.2f}  {line['text']!r}", file=sys.stderr)

    out_dir = HERE / "out"
    out_dir.mkdir(exist_ok=True)
    dest = out_dir / "tesseract.json"
    dest.write_text(json.dumps(
        {"engine": "Tesseract", "config": f"{args.lang}, psm {args.psm}",
         "images": images}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    avg = sum(i["ms"] for i in images) / len(images)
    print(f"\navg {avg:.0f} ms -> {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
