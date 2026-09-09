#!/usr/bin/env python3
"""Generate the extension's PNG icons with no third-party dependencies.

Draws a green rounded square with a white ring (a simple "coin" mark) at
16, 48 and 128 px. Re-run after changing the look:

    python3 tools/make_icons.py
"""

import os
import struct
import zlib

OUT_DIR = os.path.join(os.path.dirname(__file__), os.pardir, "icons")

BG = (45, 125, 70)       # green
RING = (255, 255, 255)   # white
TRANSPARENT = (0, 0, 0, 0)


def rounded_alpha(x, y, size, radius):
    """Alpha (0..255) for the rounded-square mask at pixel (x, y)."""
    cx = min(x, size - 1 - x)
    cy = min(y, size - 1 - y)
    if cx >= radius or cy >= radius:
        return 255
    dx = radius - cx
    dy = radius - cy
    dist = (dx * dx + dy * dy) ** 0.5
    return max(0, min(255, int((radius - dist + 0.5) * 255)))


def build_rgba(size):
    radius = max(2, size // 5)
    cx = cy = (size - 1) / 2
    outer = size * 0.42
    inner = size * 0.26
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            a = rounded_alpha(x, y, size, radius)
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if inner <= d <= outer:
                r, g, b = RING
            else:
                r, g, b = BG
            row += bytes((r, g, b, a))
        rows.append(bytes(row))
    return rows


def write_png(path, size):
    rows = build_rgba(size)
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        sig
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as fh:
        fh.write(png)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for size in (16, 48, 128):
        path = os.path.join(OUT_DIR, f"icon{size}.png")
        write_png(path, size)
        print(f"wrote {os.path.relpath(path)}")


if __name__ == "__main__":
    main()
