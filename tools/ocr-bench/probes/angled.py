#!/usr/bin/env python3
"""Price tags at an angle — a shelf seen from the side, which is how a shelf is
usually seen.

Reported from a real shop as the case that reads nothing at all. The detector
finds the text; the crop handed to the recognizer has it running diagonally,
which is the price of axis-aligned boxes.
"""
import pathlib, subprocess, time

HERE = pathlib.Path(__file__).resolve().parent
REGRESSION = (HERE.parent.parent.parent / "android" / "app" / "src"
              / "androidTest" / "assets" / "regression")
OUT = REGRESSION
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

PAGE = """<!doctype html><meta charset="utf-8">
<style>
  * { margin:0; box-sizing:border-box; }
  body { width:1200px; height:900px; background:#7a5636; display:grid; place-items:center;
         font-family:-apple-system,"Helvetica Neue",Arial,sans-serif; }
  .tag { width:420px; background:#fff; border:1px solid #999; transform:%(transform)s; }
  .banner { background:#e8342a; color:#fff; font-size:22px; font-weight:800;
            text-align:center; padding:3px 0; }
  .row { display:flex; align-items:baseline; justify-content:space-between; padding:6px 10px; }
  .pct { font-size:30px; font-weight:700; }
  .price { font-size:44px; font-weight:700; }
  .fine { font-size:11px; color:#333; padding:0 10px 5px; }
</style>
<div class="tag">
  <div class="banner">PRECIOS IMPOSIBLES</div>
  <div class="row"><span class="pct">25%%</span><span class="price">$ 3.648,75</span></div>
  <div class="fine">YERBA MATE PLAYADITO 1 KG</div>
</div>
"""

CASES = {
    "flat":    "none",
    "tilt-8":  "rotate(-8deg)",
    "tilt-15": "rotate(-15deg)",
    "tilt-25": "rotate(-25deg)",
    # A shelf seen from the side: perspective plus tilt, as in the photo.
    "oblique": "perspective(900px) rotateY(32deg) rotate(-10deg)",
}


def main():
    OUT.mkdir(exist_ok=True)
    pages = HERE / ".html"; pages.mkdir(exist_ok=True)
    procs = []
    for name, transform in CASES.items():
        (pages / f"{name}.html").write_text(PAGE % dict(transform=transform), encoding="utf-8")
        procs.append(subprocess.Popen([
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            "--force-device-scale-factor=1", "--window-size=1200,900",
            "--virtual-time-budget=2000", f"--user-data-dir={HERE}/.chrome-{name}",
            f"--screenshot={OUT}/{name}.png", (pages / f"{name}.html").as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    deadline = time.time() + 90
    while time.time() < deadline and len(list(OUT.glob("*.png"))) < len(CASES):
        time.sleep(0.5)
    for p in procs: p.kill()
    print(" ".join(sorted(p.stem for p in OUT.glob("*.png"))))


if __name__ == "__main__":
    main()
