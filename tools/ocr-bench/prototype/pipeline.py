#!/usr/bin/env python3
"""The phone's OCR pipeline, on a desktop, where trying something costs seconds.

Every part of this has a counterpart in android/app and android/core, and the
point is to try a change here first. Two of the app's fixes were found this way
and would have been expensive to find in Kotlin: feeding the recognizer its
nominal 320-pixel width squashes a long line until it stops being readable, and
a currency glyph at the very edge of a crop is read as a digit and fuses into
the number.

It reads the same models the app ships, so it needs only onnxruntime and numpy —
no PaddleOCR, no model download:

    pip install onnxruntime numpy
    ./pipeline.py                 # writes out/onnx-prototype.json
    ../bench.py ../out/onnx-prototype.json

SIDE_MARGIN mirrors the app's crop margin and can be swept from the environment
to see what a different one would cost.
"""
import json
import os
import pathlib
import sys
import time

import numpy as np
import onnxruntime as ort

HERE = pathlib.Path(__file__).resolve().parent
BENCH = HERE.parent
# The models the app ships, so this and the phone read with the same weights.
ASSETS = BENCH.parent.parent / "android" / "app" / "src" / "main" / "assets"

DET_LONG_SIDE = 960
DET_THRESH = 0.3
DET_BOX_THRESH = 0.6
DET_UNCLIP_RATIO = 1.5
# The app's own crop margin, in units of text height. Overridable, to measure
# what another would cost — more than this starts losing readings.
SIDE_MARGIN = float(os.environ.get("SIDE_MARGIN", "0.3"))
DET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
DET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

REC_HEIGHT, REC_WIDTH = 48, 320


def load_charset():
    """The recognizer's labels, in the order its output indexes them.

    The same charset.txt the app reads, so a label cannot mean one thing here
    and another on the phone.
    """
    text = (ASSETS / "charset.txt").read_text(encoding="utf-8")
    return text.split("\n")


# --- detection --------------------------------------------------------------
def det_preprocess(rgb):
    """Scales the long side to 960, rounds to a multiple of 32, normalizes."""
    h, w = rgb.shape[:2]
    scale = min(DET_LONG_SIDE / max(h, w), 1.0)
    nh = max(32, int(round(h * scale / 32)) * 32)
    nw = max(32, int(round(w * scale / 32)) * 32)

    resized = resize_bilinear(rgb, nh, nw).astype(np.float32) / 255.0
    normalized = (resized - DET_MEAN) / DET_STD
    chw = np.transpose(normalized, (2, 0, 1))[None]
    return chw.astype(np.float32), (w / nw, h / nh)


def resize_bilinear(img, out_h, out_w):
    """Plain bilinear resize. Written out because the Kotlin side has to do the
    same arithmetic on a Bitmap, and any difference here would hide there."""
    h, w = img.shape[:2]
    ys = (np.arange(out_h, dtype=np.float32) + 0.5) * (h / out_h) - 0.5
    xs = (np.arange(out_w, dtype=np.float32) + 0.5) * (w / out_w) - 0.5
    ys = np.clip(ys, 0, h - 1)
    xs = np.clip(xs, 0, w - 1)

    y0 = np.floor(ys).astype(np.int32)
    x0 = np.floor(xs).astype(np.int32)
    y1 = np.minimum(y0 + 1, h - 1)
    x1 = np.minimum(x0 + 1, w - 1)
    wy = (ys - y0)[:, None, None]
    wx = (xs - x0)[None, :, None]

    top = img[y0][:, x0] * (1 - wx) + img[y0][:, x1] * wx
    bottom = img[y1][:, x0] * (1 - wx) + img[y1][:, x1] * wx
    return top * (1 - wy) + bottom * wy


def connected_boxes(mask, probs):
    """Flood-fills the thresholded map and returns one axis-aligned box per
    component, with the mean probability inside it as the score.

    An explicit stack rather than recursion: the same loop has to run on a
    phone, where a deep recursion over a 960x960 map is a crash.
    """
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    boxes = []

    for start_y in range(h):
        for start_x in range(w):
            if not mask[start_y, start_x] or seen[start_y, start_x]:
                continue
            stack = [(start_y, start_x)]
            seen[start_y, start_x] = True
            min_y = max_y = start_y
            min_x = max_x = start_x
            total = 0.0
            count = 0

            while stack:
                y, x = stack.pop()
                total += probs[y, x]
                count += 1
                if y < min_y: min_y = y
                if y > max_y: max_y = y
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        stack.append((ny, nx))

            if count < 4:
                continue
            score = total / count
            if score < DET_BOX_THRESH:
                continue
            boxes.append((min_x, min_y, max_x + 1, max_y + 1, score))
    return boxes


def unclip(box, w_limit, h_limit):
    """PaddleOCR grows each detection before cropping; for a rectangle the
    offset it uses works out to area * ratio / perimeter."""
    x0, y0, x1, y1, score = box
    bw, bh = x1 - x0, y1 - y0
    if bw <= 0 or bh <= 0:
        return None
    distance = bw * bh * DET_UNCLIP_RATIO / (2.0 * (bw + bh))
    return (
        max(0.0, x0 - distance), max(0.0, y0 - distance),
        min(float(w_limit), x1 + distance), min(float(h_limit), y1 + distance),
        score,
    )


# --- recognition ------------------------------------------------------------
def rec_preprocess(crop, max_width=1600):
    """Keeps the aspect ratio and feeds the natural width.

    The recognizer's width is a dynamic axis, so there is nothing to pad to.
    Forcing the documented 320 instead squashes a long line horizontally until
    it stops being readable — "Аренда квартиры — 2 500 рублей в сутки," came
    back as "Аренда ква2500 уов с" before this was fixed.
    """
    h, w = crop.shape[:2]
    if h == 0 or w == 0:
        return None
    target_w = int(round(REC_HEIGHT * w / h))
    target_w = max(16, min(max_width, target_w))
    target_w = ((target_w + 7) // 8) * 8

    resized = resize_bilinear(crop, REC_HEIGHT, target_w).astype(np.float32)
    normalized = (resized / 255.0 - 0.5) / 0.5
    return np.transpose(normalized, (2, 0, 1))[None]


def ctc_decode(logits, charset):
    """Greedy CTC: argmax per step, drop repeats, drop blanks."""
    best = np.argmax(logits, axis=-1)
    scores = np.max(logits, axis=-1)

    text = []
    kept = []
    previous = -1
    for index, label in enumerate(best):
        if label != previous and label != 0:
            if label < len(charset):
                text.append(charset[label])
                kept.append(scores[index])
        previous = label
    confidence = float(np.mean(kept)) if kept else 0.0
    return "".join(text), confidence


# --- the whole thing --------------------------------------------------------
class Pipeline:
    def __init__(self):
        options = ort.SessionOptions()
        options.intra_op_num_threads = 4
        self.det = ort.InferenceSession(str(ASSETS / "det.onnx"), options)
        self.rec = ort.InferenceSession(str(ASSETS / "rec.onnx"), options)
        self.charset = load_charset()

    def run(self, rgb):
        started = time.perf_counter()

        tensor, (scale_x, scale_y) = det_preprocess(rgb)
        probs = self.det.run(None, {self.det.get_inputs()[0].name: tensor})[0][0, 0]

        mask = probs > DET_THRESH
        boxes = connected_boxes(mask, probs)

        lines = []
        for box in boxes:
            grown = unclip(box, probs.shape[1], probs.shape[0])
            if grown is None:
                continue
            x0, y0, x1, y1, score = grown
            # Back to the original image's pixels.
            pad = SIDE_MARGIN * (y1 - y0) * scale_y
            ox0, ox1 = int(x0 * scale_x - pad), int(np.ceil(x1 * scale_x + pad))
            oy0, oy1 = int(y0 * scale_y), int(np.ceil(y1 * scale_y))
            ox0, oy0 = max(0, ox0), max(0, oy0)
            ox1 = min(rgb.shape[1], ox1)
            oy1 = min(rgb.shape[0], oy1)
            if ox1 - ox0 < 3 or oy1 - oy0 < 3:
                continue

            crop = rgb[oy0:oy1, ox0:ox1]
            tensor = rec_preprocess(crop)
            if tensor is None:
                continue
            logits = self.rec.run(None, {self.rec.get_inputs()[0].name: tensor})[0][0]
            text, confidence = ctc_decode(logits, self.charset)
            if not text.strip():
                continue
            lines.append({
                "text": text,
                "confidence": confidence,
                "box": [float(ox0), float(oy0), float(ox1), float(oy1)],
            })

        return lines, (time.perf_counter() - started) * 1000


def load_png(path):
    """Reads a PNG without an image library: ONNX Runtime brings none, and the
    fixtures are all PNG."""
    import struct
    import zlib

    data = pathlib.Path(path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos = 8
    width = height = depth = color = None
    idat = bytearray()
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", body[:10])
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
        pos += 12 + length

    assert depth == 8, f"unsupported bit depth {depth}"
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color]
    raw = zlib.decompress(bytes(idat))
    stride = width * channels

    out = np.zeros((height, stride), dtype=np.uint8)
    previous = np.zeros(stride, dtype=np.uint8)
    at = 0
    for row in range(height):
        filter_type = raw[at]
        at += 1
        line = np.frombuffer(raw[at:at + stride], dtype=np.uint8).astype(np.int32)
        at += stride
        current = np.zeros(stride, dtype=np.int32)
        for i in range(stride):
            a = current[i - channels] if i >= channels else 0
            b = int(previous[i])
            c = int(previous[i - channels]) if i >= channels else 0
            value = line[i]
            if filter_type == 1:
                value += a
            elif filter_type == 2:
                value += b
            elif filter_type == 3:
                value += (a + b) // 2
            elif filter_type == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                value += a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
            current[i] = value & 0xFF
        out[row] = current.astype(np.uint8)
        previous = out[row]

    image = out.reshape(height, width, channels)
    return image[:, :, :3] if channels >= 3 else np.repeat(image, 3, axis=2)


def main():
    images = sorted((BENCH / "img").glob("*.png"))
    if not images:
        sys.exit("no fixture images found")

    pipeline = Pipeline()
    out = {"engine": "PaddleOCR via ONNX", "config": "mobile det + eslav rec, axis-aligned boxes",
           "images": []}
    for path in images:
        rgb = load_png(path)
        lines, ms = pipeline.run(rgb)
        out["images"].append({"image": path.stem, "ms": ms, "lines": lines})
        print(f"{path.stem}  [{ms:.0f} ms]", file=sys.stderr)
        for line in lines:
            print(f"    {line['confidence']:.2f}  {line['text']!r}", file=sys.stderr)

    target = BENCH / "out" / "onnx-prototype.json"
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(out, ensure_ascii=False, indent=2))
    print(f"\nwrote {target}", file=sys.stderr)


if __name__ == "__main__":
    main()
