#!/usr/bin/env bash
# Renders the Google Play icon and feature graphic into docs/play/.
#
# The artwork is the owner's picture, tools/app-icon-source.png, used as given:
# nothing here draws it. The icon is what a launcher shows — the picture filling
# the square, its own rounded corners cut away with the icon's blues behind —
# at the 512x512 Play asks for; Play rounds the corners itself. The source is
# 398x392, so the icon is that picture enlarged by about a third, and a little
# softer than a native 512 would be.
#
# The feature graphic sets the same picture beside the app's name on those
# blues. Both are pages rendered by headless Chrome, so the type is the
# browser's and the result is reproducible.
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME=${CHROME:-"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
[ -x "$CHROME" ] || { echo "Chrome not found; set CHROME=/path/to/chrome" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp tools/app-icon-source.png "$TMP/source.png"
mkdir -p docs/play

# The launcher's background gradient, from ic_launcher_background.xml.
BLUES='#80CAF2 0%, #3E6CC0 50%, #364CB1 100%'

# The picture, its own rounded corners cut away. Its corner radius is about
# 18% of its width; Play's mask is rounder, so the cut never shows.
PICTURE='<div class="picture"><img src="source.png" alt=""></div>'
PICTURE_CSS='.picture { overflow: hidden; border-radius: 18%; }
             .picture img { display: block; width: 100%; height: 100%; object-fit: fill; }'

cat > "$TMP/icon.html" <<HTML
<!doctype html><meta charset="utf-8">
<style>
  html, body { margin: 0; width: 512px; height: 512px; overflow: hidden; }
  body { background: linear-gradient(180deg, $BLUES); }
  $PICTURE_CSS
  .picture { width: 512px; height: 512px; }
</style>
$PICTURE
HTML

cat > "$TMP/feature.html" <<HTML
<!doctype html><meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=block" rel="stylesheet">
<style>
  html, body { margin: 0; width: 1024px; height: 500px; overflow: hidden; }
  body {
    background: linear-gradient(160deg, $BLUES);
    font-family: Roboto, system-ui, sans-serif; color: #fff;
    display: flex; align-items: center; gap: 56px; padding: 0 80px; box-sizing: border-box;
  }
  $PICTURE_CSS
  .picture { flex: none; width: 300px; height: 300px;
             box-shadow: 0 18px 40px rgba(16, 30, 90, 0.45); }
  h1 { font-size: 76px; font-weight: 700; margin: 0 0 18px; letter-spacing: -1px; }
  p  { font-size: 31px; font-weight: 400; line-height: 1.3; margin: 0; opacity: 0.95; }
  .ways { margin-top: 30px; display: flex; gap: 12px; }
  .ways span { font-size: 22px; font-weight: 500; padding: 8px 18px;
               border-radius: 999px; background: rgba(255, 255, 255, 0.18); }
</style>
$PICTURE
<div>
  <h1>How Much?</h1>
  <p>Photograph a price.<br>Read it in your own currency.</p>
  <div class="ways"><span>Camera</span><span>Gallery</span><span>Calculator</span></div>
</div>
HTML

shoot() { # page width height out
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --default-background-color=00000000 --virtual-time-budget=5000 \
    --window-size="$2,$3" --screenshot="$4" "file://$TMP/$1" > /dev/null 2>&1
}

shoot icon.html 512 512 "$TMP/icon.png"
shoot feature.html 1024 500 "$TMP/feature.png"

# Play asks for a 32-bit PNG, and Chrome writes an opaque page without an
# alpha channel; Core Graphics re-encodes it with one, every pixel opaque.
cat > "$TMP/rgba.swift" <<'SWIFT'
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
let args = CommandLine.arguments
let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil)!
let image = CGImageSourceCreateImageAtIndex(source, 0, nil)!
let context = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8,
                        bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
let out = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL,
                                          UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(out, context.makeImage()!, nil)
guard CGImageDestinationFinalize(out) else { exit(1) }
SWIFT
swift "$TMP/rgba.swift" "$TMP/icon.png" docs/play/icon-512.png
# Play wants the feature graphic without transparency: JPEG has none.
sips -s format jpeg -s formatOptions 95 "$TMP/feature.png" --out docs/play/feature-graphic.jpg > /dev/null

sips -g pixelWidth -g pixelHeight -g hasAlpha docs/play/icon-512.png docs/play/feature-graphic.jpg
