#!/usr/bin/env python3
"""Asks what the recognizer actually does to a currency glyph standing in front
of the digits.

The corpus has one instance of the failure — "₴1 200,50" coming back as
"21 200,50" — which is enough to know the shape of the danger and not enough to
build a rule on. This renders prefix-symbol prices across the symbols that are
written that way, and reports what comes back, so the rule is written against
evidence rather than against one anecdote.
"""
import json
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
REGRESSION = (HERE.parent.parent.parent / "android" / "app" / "src"
              / "androidTest" / "assets" / "regression")
OUT = HERE / ".out"          # the sweep itself is scratch
KEEPS = {"uah-both": "prefix-glyph"}   # the one case a test reads
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

PAGE = """<!doctype html><meta charset="utf-8">
<style>
  * { margin: 0; box-sizing: border-box; }
  html, body { width: 600px; height: 200px; }
  body { display: grid; place-items: center; background: %(bg)s;
         font-family: -apple-system, "Helvetica Neue", Arial, sans-serif; }
  .stage { %(stage)s }
  .p { font-size: %(size)spx; font-weight: 700; }
</style>
<div class="stage"><div class="p">%(text)s</div></div>
"""

# Every symbol this converter knows that is written before the number.
CASES = [
    ("uah-clean", "₴1 200,50", 54, "#fff", ""),
    ("usd-clean", "$1,299.00", 54, "#fff", ""),
    ("eur-clean", "€1 299", 54, "#fff", ""),
    ("gbp-clean", "£129.99", 54, "#fff", ""),
    ("jpy-clean", "¥1299", 54, "#fff", ""),
    ("rub-prefix", "₽1 299", 54, "#fff", ""),
    ("inr-clean", "₹1,299", 54, "#fff", ""),
    ("krw-clean", "₩12,990", 54, "#fff", ""),
    # The same, small and in camera conditions — the regime the failure was
    # first seen in.
    ("uah-small", "₴1 200,50", 24, "#fff", ""),
    ("usd-small", "$1,299.00", 24, "#fff", ""),
    ("uah-camera", "₴1 200,50", 48, "linear-gradient(160deg,#efe7dc,#d9cfc2)",
     "transform: rotate(-3deg); filter: blur(0.8px) contrast(0.88);"),
    ("usd-camera", "$1,299.00", 48, "linear-gradient(160deg,#efe7dc,#d9cfc2)",
     "transform: rotate(-3deg); filter: blur(0.8px) contrast(0.88);"),
    ("eur-camera", "€1 299", 48, "linear-gradient(200deg,#e8eaee,#c9cdd4)",
     "transform: perspective(700px) rotateY(20deg); filter: blur(0.4px);"),
    # The decisive case: a prefix glyph AND a suffix token on one line. If the
    # glyph becomes a digit while the token survives, the pipeline has a
    # currency to pair the inflated number with — and shows a wrong price
    # instead of no price.
    ("uah-both", "₴1 200,50 грн", 40, "#fff", ""),
    ("inr-both", "₹1,299 INR", 40, "#fff", ""),
    ("uah-both-camera", "₴1 200,50 грн", 40, "linear-gradient(160deg,#efe7dc,#d9cfc2)",
     "transform: rotate(-3deg); filter: blur(0.7px);"),
]


def render():
    OUT.mkdir(exist_ok=True)
    pages = HERE / ".html"
    pages.mkdir(exist_ok=True)
    procs = []
    for name, text, size, bg, stage in CASES:
        (pages / f"{name}.html").write_text(
            PAGE % dict(text=text, size=size, bg=bg, stage=stage), encoding="utf-8")
        procs.append(subprocess.Popen([
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--force-device-scale-factor=2",
            "--window-size=600,200", "--virtual-time-budget=2000",
            f"--user-data-dir={HERE}/.chrome-{name}",
            f"--screenshot={OUT}/{name}.png",
            (pages / f"{name}.html").as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))

    deadline = time.time() + 90
    while time.time() < deadline and len(list(OUT.glob("*.png"))) < len(CASES):
        time.sleep(0.5)
    for p in procs:
        p.kill()


def main():
    render()
    sys.path.insert(0, str(HERE))
    from pipeline import Pipeline, load_png

    pipeline = Pipeline()
    rows = []
    for name, text, *_ in CASES:
        path = OUT / f"{name}.png"
        if not path.exists():
            print(f"{name}: not rendered", file=sys.stderr)
            continue
        lines, _ = pipeline.run(load_png(path))
        got = " | ".join(l["text"] for l in lines)
        kept = text[0] in got
        rows.append(dict(case=name, wanted=text, got=got, glyph_kept=kept))
        print(f"{name:<14} wanted {text!r:<16} got {got!r}"
              f"{'' if kept else '   <- glyph lost'}")

    # The decisive case becomes the committed fixture; the rest of the sweep is
    # evidence for a decision already taken and lives only as long as the run.
    for case, fixture in KEEPS.items():
        source = OUT / f"{case}.png"
        if source.exists():
            REGRESSION.mkdir(parents=True, exist_ok=True)
            (REGRESSION / f"{fixture}.png").write_bytes(source.read_bytes())
            print(f"kept {case} as {fixture}.png", file=sys.stderr)

    (OUT / "results.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2))
    lost = [r for r in rows if not r["glyph_kept"]]
    print(f"\n{len(lost)} of {len(rows)} lost the prefix glyph")


if __name__ == "__main__":
    main()
