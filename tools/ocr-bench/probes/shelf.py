#!/usr/bin/env python3
"""Renders a shelf: several price tags, each small in the frame, the way they
look when a whole shelf is in view rather than one tag.

That is the case the app struggled with on a real phone — several tags at once,
and sometimes none read at all — so it needs a fixture before it can have a fix.
"""
import pathlib, subprocess, time

HERE = pathlib.Path(__file__).resolve().parent
REGRESSION = (HERE.parent.parent.parent / "android" / "app" / "src"
              / "androidTest" / "assets" / "regression")
OUT = REGRESSION
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

TAG = """
<div class="tag">
  <div class="banner">COMUNIDAD</div>
  <div class="row"><span class="pct">%(pct)s%%</span><span class="price">$ %(price)s</span></div>
  <div class="fine">CAFE INSTANTANEO ORIGINAL NESCAFE FRA 100 GRM</div>
  <div class="fine">NO ACUMULA CON OTROS DESCUENTOS</div>
</div>
"""

PAGE = """<!doctype html><meta charset="utf-8">
<style>
  * { margin:0; box-sizing:border-box; }
  body { width:1600px; height:1200px; background:#6b4a2f;
         font-family:-apple-system,"Helvetica Neue",Arial,sans-serif;
         display:flex; flex-direction:column; justify-content:space-around; padding:28px; }
  .shelf { display:flex; justify-content:space-around; align-items:flex-end;
           border-bottom:14px solid #2c4a8a; padding-bottom:6px; }
  .tag { width:%(tagw)spx; background:#fff; border:1px solid #bbb; }
  .banner { background:#f08a24; color:#fff; font-size:%(banner)spx; font-weight:800;
            text-align:center; padding:2px 0; letter-spacing:.5px; }
  .row { display:flex; align-items:baseline; justify-content:space-between; padding:3px 5px; }
  .pct { font-size:%(pct)spx; font-weight:700; }
  .price { font-size:%(price)spx; font-weight:700; }
  .fine { font-size:%(fine)spx; color:#333; padding:0 5px 2px; line-height:1.15; }
</style>
%(shelves)s
"""

def build(scale, prices):
    tagw = int(300 * scale)
    css = dict(tagw=tagw, banner=int(15 * scale), pct=int(22 * scale),
               price=int(30 * scale), fine=int(8 * scale))
    shelves = ""
    for row in range(0, len(prices), 3):
        tags = "".join(TAG % dict(pct=15, price=p) for p in prices[row:row + 3])
        shelves += f'<div class="shelf">{tags}</div>'
    return PAGE % dict(shelves=shelves, **css)

CASES = {
    # One tag filling much of the frame: the easy case that already worked.
    "close":  (1.6, ["4.462,50", "3.648,75", "3.398,61"]),
    # A shelf at arm's length: what the phone was actually pointed at.
    "shelf":  (0.9, ["4.462,50", "3.648,75", "3.398,61", "2.199,00", "5.870,25", "999,90"]),
    # Further back still.
    "far":    (0.55, ["4.462,50", "3.648,75", "3.398,61", "2.199,00", "5.870,25", "999,90"]),
}

def main():
    OUT.mkdir(exist_ok=True)
    pages = HERE / ".html"; pages.mkdir(exist_ok=True)
    procs = []
    for name, (scale, prices) in CASES.items():
        (pages / f"{name}.html").write_text(build(scale, prices), encoding="utf-8")
        procs.append(subprocess.Popen([
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            "--force-device-scale-factor=1", "--window-size=1600,1200",
            "--virtual-time-budget=2000", f"--user-data-dir={HERE}/.chrome-{name}",
            f"--screenshot={OUT}/{name}.png", (pages / f"{name}.html").as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    deadline = time.time() + 90
    while time.time() < deadline and len(list(OUT.glob("*.png"))) < len(CASES):
        time.sleep(0.5)
    for p in procs: p.kill()
    for name in CASES:
        print(name, (OUT / f"{name}.png").exists())

if __name__ == "__main__":
    main()
