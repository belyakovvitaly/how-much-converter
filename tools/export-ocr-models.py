#!/usr/bin/env python3
"""Exports the PaddleOCR models the Android app runs, as ONNX, into its assets.

The app uses ONNX Runtime rather than Paddle Lite: ONNX Runtime publishes an
official Android artifact to Maven Central, and Paddle Lite does not — what is
there under that name is third-party repackaging.

    pip install paddlepaddle paddleocr paddle2onnx
    ./tools/export-ocr-models.py

Writes det.onnx, rec.onnx and charset.txt to android/app/src/main/assets. Those
are build outputs, not sources, so they are not committed; run this once after
cloning, or whenever the models change.

The Paddle models themselves come from PaddleOCR's own cache in
~/.paddlex/official_models, which fills the first time PaddleOCR runs. The
benchmark does that: tools/ocr-bench/run_paddle.py.
"""
import argparse
import json
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
CACHE = pathlib.Path.home() / ".paddlex" / "official_models"

# The mobile detector, and the East Slavic recognizer — the Cyrillic one. The
# benchmark in tools/ocr-bench is what picked this pair.
DET = "PP-OCRv5_mobile_det"
REC = "eslav_PP-OCRv5_mobile_rec"


def paddle2onnx_command() -> str:
    """paddle2onnx ships a console script and no __main__, so `-m` will not do.
    Looked for beside the running interpreter first, so a virtualenv's copy wins
    over anything on PATH."""
    beside = pathlib.Path(sys.executable).parent / "paddle2onnx"
    if beside.exists():
        return str(beside)
    found = shutil.which("paddle2onnx")
    if not found:
        sys.exit("paddle2onnx is not installed: pip install paddle2onnx")
    return found


def export(model: str, name: str, opset: int) -> pathlib.Path:
    source = CACHE / model
    if not (source / "inference.json").exists():
        sys.exit(
            f"{model} is not in {CACHE}.\n"
            f"PaddleOCR downloads it on first use — run tools/ocr-bench/run_paddle.py once."
        )

    target = ASSETS / f"{name}.onnx"
    result = subprocess.run(
        [
            paddle2onnx_command(),
            "--model_dir", str(source),
            "--model_filename", "inference.json",
            "--params_filename", "inference.pdiparams",
            "--save_file", str(target),
            "--opset_version", str(opset),
        ],
        capture_output=True, text=True,
    )
    if result.returncode != 0 or not target.exists():
        sys.exit(f"paddle2onnx failed for {model}:\n{result.stderr.strip()}")
    return target


def export_charset() -> pathlib.Path:
    """The recognizer's labels, in the order its output indexes them.

    CTC blank first, then the model's dictionary, then a space. Getting this
    order wrong does not fail loudly — it silently shifts every character.
    """
    config = json.loads((CACHE / REC / "config.json").read_text(encoding="utf-8"))
    dictionary = config["PostProcess"]["character_dict"]
    labels = ["<blank>"] + list(dictionary) + [" "]

    target = ASSETS / "charset.txt"
    # One label per line, so a trailing space survives the round trip.
    target.write_text("\n".join(labels), encoding="utf-8")
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--opset", type=int, default=14)
    args = parser.parse_args()

    ASSETS.mkdir(parents=True, exist_ok=True)
    written = [
        export(DET, "det", args.opset),
        export(REC, "rec", args.opset),
        export_charset(),
    ]
    for path in written:
        size = path.stat().st_size
        print(f"{path.relative_to(ROOT)}  {size / 1_000_000:.1f} MB"
              if size > 100_000 else
              f"{path.relative_to(ROOT)}  {size} bytes")


if __name__ == "__main__":
    main()
