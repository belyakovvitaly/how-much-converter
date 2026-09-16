#!/usr/bin/env python3
"""Derive the extension's PNG icons from the supplied artwork.

The icon is a picture the owner supplied, tools/icon-source.jpeg, and it is used
as given: nothing here draws. The script only prepares it for a toolbar —

  * crops to the blue frame, since the file has a white margin around it and is
    a few pixels wider than it is tall;
  * pads that to a square, transparently;
  * makes the white outside the frame's rounded corners transparent, so a dark
    toolbar does not show white corners behind them (the white inside the frame
    is part of the design and stays);
  * scales down to the sizes Chrome asks for. The 128 px store icon keeps the
    16 px transparent border Chrome's guidelines ask for; the toolbar sizes use
    the whole square, because at 16 px every pixel of the design is needed.

To change the icon, replace icon-source.jpeg and run:

    pip install Pillow
    python3 tools/make_icons.py
"""

import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools" / "icon-source.jpeg"
OUT_DIR = ROOT / "extension" / "icons"

# (size, transparent border on each side)
SIZES = [(16, 0), (32, 0), (48, 0), (128, 16)]


def is_frame_blue(pixel):
    r, g, b = pixel[:3]
    return b > 170 and r < 110 and 90 < g < 170


def frame_box(image):
    """The bounding box of the blue frame."""
    rgb = image.convert("RGB")
    px = rgb.load()
    w, h = rgb.size
    xs = [x for x in range(w) if any(is_frame_blue(px[x, y]) for y in range(0, h, 3))]
    ys = [y for y in range(h) if any(is_frame_blue(px[x, y]) for x in range(0, w, 3))]
    if not xs or not ys:
        raise SystemExit(f"no blue frame found in {SOURCE.name}")
    return xs[0], ys[0], xs[-1] + 1, ys[-1] + 1


def prepared():
    art = Image.open(SOURCE).convert("RGBA")
    art = art.crop(frame_box(art))

    # The frame is a closed shape, so a fill from the corners reaches only the
    # white outside its rounded corners and never the white inside it.
    w, h = art.size
    for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        if art.getpixel(corner)[3] and min(art.getpixel(corner)[:3]) > 200:
            ImageDraw.floodfill(art, corner, (255, 255, 255, 0), thresh=60)

    side = max(w, h)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(art, ((side - w) // 2, (side - h) // 2), art)
    return square


def main():
    art = prepared()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for size, border in SIZES:
        inner = size - 2 * border
        icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        scaled = art.resize((inner, inner), Image.LANCZOS)
        icon.paste(scaled, (border, border), scaled)
        path = OUT_DIR / f"icon{size}.png"
        icon.save(path, optimize=True)
        print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
