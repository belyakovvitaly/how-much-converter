#!/usr/bin/env python3
"""Regenerates the fixture corpus in img/ and the ground truth in truth.json.

The PNGs are committed, so this only needs running when a case is added or
changed: an OCR benchmark is only comparable over time if the pixels stay put,
and Chrome's font rendering does not.

Each case is a standalone page screenshotted at a fixed size. The "camera"
cases apply CSS blur / rotation / perspective / contrast so the fixtures look
like phone frames rather than clean renders — those are the ones that separate
the engines.

    ./gen.py            # rewrite html/, re-render img/, rewrite truth.json
    CHROME=... ./gen.py # point at a different Chrome
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
HTML = HERE / "html"
IMG = HERE / "img"

CHROME = os.environ.get(
    "CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

BASE = """<!doctype html><meta charset="utf-8">
<style>
  * { margin: 0; box-sizing: border-box; }
  html, body { width: 600px; height: 320px; }
  body {
    display: grid; place-items: center;
    font-family: -apple-system, "Helvetica Neue", Arial, sans-serif;
    background: %(bg)s;
  }
  .stage { %(stage)s }
  %(css)s
</style>
<div class="stage">%(body)s</div>
"""

CASES = [
    dict(
        id="01-price-tag",
        note="Shop price tag, large type",
        bg="#fff",
        stage="",
        css=".p { font-size: 64px; font-weight: 700; letter-spacing: -1px; }",
        body='<div class="p">1 299 ₽</div>',
        truth=["1 299 ₽"],
    ),
    dict(
        id="02-shop-list",
        note="Product list at UI type sizes",
        bg="#fff",
        stage="width: 460px;",
        css=""".row { display: flex; justify-content: space-between; padding: 9px 0;
                      border-bottom: 1px solid #e6e6e6; font-size: 15px; color: #222; }
               .row b { font-weight: 600; }""",
        body="""<div class="row"><span>Наушники беспроводные</span><b>12 490 руб.</b></div>
                <div class="row"><span>Чехол силиконовый</span><b>899 руб.</b></div>
                <div class="row"><span>Кабель USB-C</span><b>1 250 ₽</b></div>
                <div class="row"><span>Зарядное устройство</span><b>2 199,90 ₽</b></div>""",
        truth=["12 490 руб.", "899 руб.", "1 250 ₽", "2 199,90 ₽"],
    ),
    dict(
        id="03-marketplace",
        note="Marketplace: struck-through old price, large new one",
        bg="#fff",
        stage="text-align: center;",
        css=""".old { font-size: 22px; color: #9a9a9a; text-decoration: line-through; }
               .new { font-size: 56px; font-weight: 800; color: #c2185b; margin-top: 6px; }
               .mo  { font-size: 17px; color: #444; margin-top: 10px; }""",
        body="""<div class="old">2 490 ₽</div><div class="new">1 299 ₽</div>
                <div class="mo">или 217 ₽ в месяц</div>""",
        truth=["2 490 ₽", "1 299 ₽", "217 ₽"],
    ),
    dict(
        id="04-uah",
        note="Hryvnia, as a symbol and spelled out",
        bg="#fff",
        stage="text-align: center;",
        css=".a { font-size: 48px; font-weight: 700; } .b { font-size: 30px; margin-top: 14px; }",
        body='<div class="a">450 грн</div><div class="b">₴1 200,50</div>',
        truth=["450 грн", "₴1 200,50"],
    ),
    dict(
        id="05-kzt",
        note="Tenge, as a symbol and spelled out",
        bg="#fff",
        stage="text-align: center;",
        css=".a { font-size: 48px; font-weight: 700; } .b { font-size: 30px; margin-top: 14px; }",
        body='<div class="a">15 900 ₸</div><div class="b">4 500 тг</div>',
        truth=["15 900 ₸", "4 500 тг"],
    ),
    dict(
        id="06-prose",
        note="Currency spelled out inside a sentence",
        bg="#fff",
        stage="width: 470px;",
        css=".t { font-size: 21px; line-height: 1.5; color: #1a1a1a; }",
        body="""<div class="t">Аренда квартиры — 2 500 рублей в сутки,
                залог 10 000 рублей. Уборка 1 200 руб.</div>""",
        truth=["2 500 рублей", "10 000 рублей", "1 200 руб."],
    ),
    dict(
        id="07-mixed",
        note="Cyrillic and Latin on one line",
        bg="#fff",
        stage="text-align: center;",
        css=".t { font-size: 34px; font-weight: 600; } .s { font-size: 20px; color: #555; margin-top: 12px; }",
        body='<div class="t">Цена: 99 USD / 9 900 ₽</div><div class="s">Доставка 15 € или 1 500 руб.</div>',
        truth=["99 USD", "9 900 ₽", "15 €", "1 500 руб."],
    ),
    dict(
        id="08-receipt",
        note="Till receipt, monospaced",
        bg="#fff",
        stage="width: 360px;",
        css=""".r { font-family: "SF Mono", Menlo, monospace; font-size: 16px; line-height: 1.7; }
               .r div { display: flex; justify-content: space-between; }""",
        body="""<div class="r">
                  <div><span>Хлеб</span><span>89,00</span></div>
                  <div><span>Молоко 3.2%</span><span>112,50</span></div>
                  <div><span>Кофе</span><span>1 033,06</span></div>
                  <div><b>ИТОГО</b><b>1 234,56 руб.</b></div>
                </div>""",
        truth=["1 234,56 руб."],
    ),
    # --- camera-like conditions ---------------------------------------------
    dict(
        id="09-camera-blur",
        note="Camera: slight defocus, tilt, warm light",
        bg="linear-gradient(160deg,#efe7dc,#d9cfc2)",
        stage="transform: rotate(-3.5deg); filter: blur(0.8px) contrast(0.88) brightness(1.04);",
        css=""".card { background: #fdfcfa; padding: 26px 44px; border-radius: 6px;
                       box-shadow: 0 10px 26px rgba(0,0,0,.18); text-align: center; }
               .p { font-size: 58px; font-weight: 700; }
               .n { font-size: 18px; color: #666; margin-top: 4px; }""",
        body='<div class="card"><div class="n">Цена</div><div class="p">3 490 ₽</div></div>',
        truth=["3 490 ₽"],
    ),
    dict(
        id="10-camera-angle",
        note="Camera: shot at an angle, with shadow",
        bg="linear-gradient(200deg,#e8eaee,#c9cdd4)",
        stage="transform: perspective(700px) rotateY(24deg) rotateX(7deg); filter: blur(0.4px);",
        css=""".card { background: #fff; padding: 24px 40px; border-radius: 4px;
                       box-shadow: 0 16px 30px rgba(0,0,0,.25); }
               .p { font-size: 50px; font-weight: 700; }
               .q { font-size: 22px; color: #555; margin-top: 8px; }""",
        body='<div class="card"><div class="p">799 руб.</div><div class="q">за 1 кг</div></div>',
        truth=["799 руб."],
    ),
    dict(
        id="11-camera-lowlight",
        note="Camera: low light and low contrast",
        bg="#3a3a3e",
        stage="filter: contrast(0.62) brightness(0.85) blur(0.5px); transform: rotate(1.8deg);",
        css=""".card { background: #6e6e73; color: #e8e8ea; padding: 22px 36px; border-radius: 8px; }
               .p { font-size: 44px; font-weight: 700; }""",
        body='<div class="card"><div class="p">1 899 ₽ / шт</div></div>',
        truth=["1 899 ₽"],
    ),
    dict(
        id="12-camera-menu",
        note="Camera: cafe menu, serif face, small type",
        bg="linear-gradient(150deg,#f3ede1,#e3d8c4)",
        stage="transform: rotate(-1.5deg); filter: blur(0.6px);",
        css=""".m { font-family: Georgia, "Times New Roman", serif; font-size: 20px;
                    line-height: 2; width: 400px; }
               .m div { display: flex; justify-content: space-between;
                        border-bottom: 1px dotted #a99; }""",
        body="""<div class="m">
                  <div><span>Эспрессо</span><span>180 ₽</span></div>
                  <div><span>Капучино</span><span>290 ₽</span></div>
                  <div><span>Чизкейк</span><span>450 руб.</span></div>
                </div>""",
        truth=["180 ₽", "290 ₽", "450 руб."],
    ),
]


def write_pages():
    HTML.mkdir(exist_ok=True)
    for c in CASES:
        (HTML / f"{c['id']}.html").write_text(
            BASE % dict(bg=c["bg"], stage=c["stage"], css=c["css"], body=c["body"]),
            encoding="utf-8")


def render():
    """Screenshots every page. Headless Chrome does not reliably exit after
    --screenshot (same quirk tools/screenshots.sh works around), so the shots
    are launched together and the processes killed once the files appear."""
    if not pathlib.Path(CHROME).exists():
        sys.exit(f"Chrome not found at {CHROME}; set CHROME=/path/to/chrome")
    IMG.mkdir(exist_ok=True)
    for old in IMG.glob("*.png"):
        old.unlink()

    procs, profiles = [], []
    for c in CASES:
        profile = HERE / f".chrome-{c['id']}"
        profiles.append(profile)
        procs.append(subprocess.Popen([
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--force-device-scale-factor=2",
            "--window-size=600,320", "--virtual-time-budget=2000",
            f"--user-data-dir={profile}",
            f"--screenshot={IMG / (c['id'] + '.png')}",
            (HTML / f"{c['id']}.html").as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))

    deadline = time.time() + 90
    while time.time() < deadline:
        if len(list(IMG.glob("*.png"))) == len(CASES):
            break
        time.sleep(0.5)
    for p in procs:
        p.kill()
    for profile in profiles:
        shutil.rmtree(profile, ignore_errors=True)

    missing = [c["id"] for c in CASES if not (IMG / f"{c['id']}.png").exists()]
    if missing:
        sys.exit(f"capture failed: {', '.join(missing)}")


def main():
    write_pages()
    render()
    (HERE / "truth.json").write_text(
        json.dumps([{k: c[k] for k in ("id", "note", "truth")} for c in CASES],
                   ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    prices = sum(len(c["truth"]) for c in CASES)
    print(f"{len(CASES)} images, {prices} prices -> {IMG}")


if __name__ == "__main__":
    main()
