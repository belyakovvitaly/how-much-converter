#!/usr/bin/env bash
# Regenerates docs/screenshot-*.png at the 1280x800 the Chrome Web Store wants.
#
# Headless Chrome cannot load an unpacked extension and drive its popup, so the
# harness runs the real src/currency.js and src/content.js against test/demo.html
# with chrome.* stubbed and a fixed rate table — the same code paths the packed
# extension takes, minus the parts headless will not do. The popup shot renders
# the real src/popup.html in an iframe over the page.
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME=${CHROME:-"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
[ -x "$CHROME" ] || { echo "Chrome not found; set CHROME=/path/to/chrome" >&2; exit 1; }

PORT=8788
TMP=.screenshot-tmp
trap 'rm -rf "$TMP"; [ -n "${SERVER:-}" ] && kill "$SERVER" 2>/dev/null || true' EXIT
rm -rf "$TMP"; mkdir -p "$TMP" docs

STUB='<script>
  window.chrome = {
    storage: { local: { get: async () => ({ enabled: true, targetCurrency: "USD",
               dollarAssumption: "UYU", ratesCache: { fetchedAt: Date.now() - 1000 } }), set() {} },
               onChanged: { addListener() {} } },
    runtime: { sendMessage: (m, cb) => cb({ ok: true, cache: { base: "USD", fetchedAt: Date.now(),
               rates: { USD: 1, UYU: 40.1, PYG: 7300, EUR: 0.921, BRL: 5.42, CHF: 0.879 } } }) },
  };
</script>'

python3 - "$TMP" "$STUB" <<'PY'
import sys, pathlib
tmp, stub = pathlib.Path(sys.argv[1]), sys.argv[2]
demo = pathlib.Path("test/demo.html").read_text(encoding="utf-8")
inline = demo.replace("  </body>", f'''    {stub}
    <script src="/src/currency.js"></script>
    <script src="/src/content.js"></script>
  </body>''')
(tmp / "inline.html").write_text(inline, encoding="utf-8")
(tmp / "popup.html").write_text(inline.replace("  </body>", '''    <style>
      #shot-popup { position: fixed; top: 14px; right: 22px; width: 330px; height: 205px;
        border: 0; border-radius: 12px; background: #fff; z-index: 9999;
        box-shadow: 0 12px 34px rgba(12,22,38,.28), 0 2px 6px rgba(12,22,38,.12); }
    </style>
    <iframe id="shot-popup" src="/.screenshot-tmp/popup-inner.html"></iframe>
  </body>'''), encoding="utf-8")
popup = pathlib.Path("src/popup.html").read_text(encoding="utf-8")
(tmp / "popup-inner.html").write_text(
    popup.replace('href="popup.css"', 'href="/src/popup.css"')
         .replace('<script src="currency.js">', stub + '\n    <script src="/src/currency.js">')
         .replace('<script src="popup.js">', '<script src="/src/popup.js">'), encoding="utf-8")
PY

python3 -m http.server "$PORT" --bind 127.0.0.1 >/dev/null 2>&1 &
SERVER=$!
until curl -sf "http://127.0.0.1:$PORT/manifest.json" >/dev/null; do sleep 0.2; done

for name in inline popup; do
  raw="$TMP/$name.png"
  # Headless Chrome does not always exit after --screenshot, so wait on the file.
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --no-first-run \
    --blink-settings=preferredColorScheme=1 --force-device-scale-factor=2 \
    --window-size=1280,800 --virtual-time-budget=5000 \
    --user-data-dir="$TMP/profile-$name" --screenshot="$raw" \
    "http://127.0.0.1:$PORT/$TMP/$name.html" >/dev/null 2>&1 &
  chrome_pid=$!
  for _ in $(seq 60); do [ -s "$raw" ] && break; sleep 0.5; done
  kill "$chrome_pid" 2>/dev/null || true
  [ -s "$raw" ] || { echo "capture failed: $name" >&2; exit 1; }
  # Shot at 2x for sharpness, then down to the store's exact 1280x800.
  sips -z 800 1280 -s format png "$raw" --out "docs/screenshot-$name.png" >/dev/null
  echo "docs/screenshot-$name.png"
done
