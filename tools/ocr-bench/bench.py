#!/usr/bin/env python3
"""Scores OCR engines by the only thing that matters for this project: would
the extension's own price rules have turned the recognized text into the right
conversion?

A price counts as converted only when the amount AND the currency both come
out right. An amount paired with the wrong currency is a wrong answer, not a
partial one — for a converter, a confidently wrong price is worse than no
price at all, so wrong answers are reported in their own column and never
netted against hits.

    ./run_vision.swift ... && ./bench.py out/vision-accurate.json
    ./bench.py out/*.json

Each engine writes out/<name>.json in the shape the runners produce:

    {"engine": ..., "config": ..., "images": [
        {"image": "01-price-tag", "ms": 620, "lines": [
            {"text": "1299", "confidence": 1.0, "box": [x0, y0, x1, y1]}]}]}
"""
import argparse
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
CURRENCY_JS = HERE.parent.parent / "extension" / "src" / "currency.js"


# --- the extension's own rules ----------------------------------------------
def load_symbol_table():
    """Reads SYMBOL_TO_CODE and NUMBER out of extension/src/currency.js.

    Parsed rather than copied so the benchmark cannot quietly drift from the
    table the extension actually ships.
    """
    src = CURRENCY_JS.read_text(encoding="utf-8")

    block = re.search(r"const SYMBOL_TO_CODE = \{(.*?)\n\};", src, re.S)
    if not block:
        sys.exit(f"cannot find SYMBOL_TO_CODE in {CURRENCY_JS}")
    symbols = dict(re.findall(r'"((?:[^"\\]|\\.)*)":\s*"([A-Z]{3})"', block.group(1)))
    if not symbols:
        sys.exit("SYMBOL_TO_CODE parsed empty")

    number = re.search(r"const NUMBER = String\.raw`(.*?)`;", src, re.S)
    if not number:
        sys.exit(f"cannot find NUMBER in {CURRENCY_JS}")

    return symbols, number.group(1)


SYMBOL_TO_CODE, NUMBER = load_symbol_table()
# ISO codes are written out by some shops and read back by every engine.
for _code in set(SYMBOL_TO_CODE.values()):
    SYMBOL_TO_CODE.setdefault(_code, _code)


def parse_amount(raw):
    """Hand port of parseAmount() in extension/src/currency.js.

    Kept in sync by PARSE_CHECKS below rather than by hope — run this file and
    a drifted port fails loudly before any score is printed.
    """
    s = re.sub(r"[  \s'’]", "", str(raw))
    has_comma, has_dot = "," in s, "." in s

    if has_comma and has_dot:
        if s.rfind(",") > s.rfind("."):
            s = s.replace(".", "").replace(",", ".")
        else:
            s = s.replace(",", "")
    elif has_comma:
        parts = s.split(",")
        s = (parts[0] + "." + parts[1]
             if len(parts) == 2 and len(parts[1]) <= 2 else "".join(parts))
    elif has_dot:
        parts = s.split(".")
        if len(parts) > 2:
            s = "".join(parts)
        elif len(parts) == 2 and len(parts[1]) == 3:
            s = "".join(parts)

    try:
        return float(s)
    except ValueError:
        return None


PARSE_CHECKS = [
    ("1 234,56", 1234.56), ("1,234.56", 1234.56), ("1.234.567", 1234567.0),
    ("12,34", 12.34), ("1,234", 1234.0), ("1'234.50", 1234.50),
    ("1’234’567", 1234567.0), ("1234", 1234.0), ("89,00", 89.0),
]

# What a recognizer emits instead of the real glyph. These are OCR artifacts,
# not ways anyone writes money, so they live here and never in currency.js:
# feeding "P" -> RUB back to the extension would convert English prose.
HOMOGLYPHS = {
    "P": "RUB", "Р": "RUB", "p": "RUB",          # ₽ read as Latin P / Cyrillic Р
    "py6": "RUB", "py6.": "RUB", "pу6": "RUB", "pу6.": "RUB",
    "pyб": "RUB", "pyб.": "RUB", "pуб": "RUB", "pуб.": "RUB",
    "rpH": "UAH", "rph": "UAH", "рпн": "UAH",    # грн
    "T": "KZT", "Tг": "KZT", "Tr": "KZT", "tr": "KZT",  # ₸ / тг
    "Є": "EUR",                                   # € read as Cyrillic Є
}


def price_finder(symbols):
    """A price is a number with a currency token directly before or after it —
    the same adjacency rule content.js applies to text nodes."""
    alternation = "|".join(
        re.escape(s) for s in sorted(symbols, key=len, reverse=True))
    return re.compile(
        rf"(?P<pre>{alternation})\s?(?P<n1>{NUMBER})"
        rf"|(?P<n2>{NUMBER})\s?(?P<post>{alternation})")


def find_prices(text, symbols, pattern):
    out = []
    for m in pattern.finditer(text):
        sym = m.group("pre") or m.group("post")
        amount = parse_amount(m.group("n1") or m.group("n2"))
        code = symbols.get(sym)
        if amount is not None and code:
            out.append((round(amount, 2), code))
    return out


# --- reuniting a split price ------------------------------------------------
def merge_boxes(lines):
    """Clusters boxes into visual rows, then walks each row left to right and
    breaks it wherever the horizontal gap is too wide to be one phrase.

    This is the OCR analogue of the second pass in content.js: there a price is
    split across sibling elements, here across detection boxes, and either way
    the number and its currency have to be reunited before the adjacency rule
    can see them.

    Rows must be built before any left-to-right ordering. Sorting by y first
    lets a right-hand fragment open the group and strand the digits to its left
    — that is what turned "1 299" into "299".
    """
    items = [l for l in lines if l.get("box") and l["text"].strip()]
    rows = []
    for it in sorted(items, key=lambda l: l["box"][1]):
        x0, y0, x1, y1 = it["box"]
        height = y1 - y0
        for row in rows:
            ry0, ry1 = row["y"]
            if min(y1, ry1) - max(y0, ry0) > 0.5 * min(height, ry1 - ry0):
                row["items"].append(it)
                row["y"] = (min(ry0, y0), max(ry1, y1))
                break
        else:
            rows.append({"items": [it], "y": (y0, y1)})

    out = []
    for row in rows:
        row["items"].sort(key=lambda l: l["box"][0])
        group, group_x1, group_h = [], None, None
        for it in row["items"]:
            x0, _, x1, y1 = it["box"]
            height = y1 - it["box"][1]
            if group and x0 - group_x1 > 1.2 * max(height, group_h):
                out.append(" ".join(group))
                group, group_x1, group_h = [], None, None
            group.append(it["text"])
            group_x1 = x1 if group_x1 is None else max(group_x1, x1)
            group_h = height if group_h is None else max(group_h, height)
        if group:
            out.append(" ".join(group))
    return out


# --- scoring ----------------------------------------------------------------
def score(images, truth, merge, aliases):
    symbols = dict(SYMBOL_TO_CODE)
    if aliases:
        symbols.update(HOMOGLYPHS)
    pattern = price_finder(symbols)

    by_id = {im["image"]: im for im in images}
    hits = misses = wrong = 0
    rows = []
    for case in truth:
        want = []
        for spelled in case["truth"]:
            found = find_prices(spelled, SYMBOL_TO_CODE, price_finder(SYMBOL_TO_CODE))
            if found:
                want.append(found[0])

        image = by_id.get(case["id"], {"lines": []})
        texts = (merge_boxes(image["lines"]) if merge
                 else [l["text"] for l in image["lines"]])
        got = []
        for text in texts:
            got.extend(find_prices(text, symbols, pattern))

        leftover = list(got)
        hit, miss = [], []
        for w in want:
            if w in leftover:
                leftover.remove(w)
                hit.append(w)
            else:
                miss.append(w)
        hits += len(hit)
        misses += len(miss)
        wrong += len(leftover)
        rows.append(dict(id=case["id"], want=want, hit=hit, miss=miss, wrong=leftover))

    return dict(hits=hits, misses=misses, wrong=wrong, rows=rows)


def fmt(prices):
    return ", ".join(f"{a:g} {c}" for a, c in prices)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("results", nargs="+", help="out/<engine>.json files to score")
    ap.add_argument("--detail", action="store_true",
                    help="list every miss and wrong answer, not just totals")
    args = ap.parse_args()

    for raw, expected in PARSE_CHECKS:
        got = parse_amount(raw)
        if got != expected:
            sys.exit(f"parse_amount drifted from extension/src/currency.js: "
                     f"{raw!r} -> {got!r}, expected {expected!r}")

    truth = json.loads((HERE / "truth.json").read_text(encoding="utf-8"))
    total = sum(len(c["truth"]) for c in truth)
    print(f"{total} prices across {len(truth)} images "
          f"({len(SYMBOL_TO_CODE)} currency tokens from extension/src/currency.js)\n")

    header = f"{'engine':<34} {'variant':<22} {'ok':>7} {'missed':>7} {'WRONG':>6}"
    print(header)
    print("-" * len(header))

    for path in args.results:
        data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        name = f"{data.get('engine', path)} ({data.get('config', '')})".strip()
        has_boxes = any(l.get("box") for im in data["images"] for l in im["lines"])
        variants = [("raw", False, False), ("+ homoglyphs", False, True)]
        if has_boxes:
            variants += [("+ box merge", True, False),
                         ("+ merge + homoglyphs", True, True)]

        times = [im["ms"] for im in data["images"] if im.get("ms")]
        for label, merge, aliases in variants:
            r = score(data["images"], truth, merge, aliases)
            print(f"{name[:33]:<34} {label:<22} "
                  f"{r['hits']:>3}/{total:<3} {r['misses']:>7} {r['wrong']:>6}")
            name = ""
        if times:
            print(f"{'':<34} {'latency':<22} "
                  f"{min(times):.0f}-{max(times):.0f} ms, avg {sum(times)/len(times):.0f} ms")

        if args.detail:
            best = score(data["images"], truth, has_boxes, True)
            for row in best["rows"]:
                if row["miss"] or row["wrong"]:
                    bits = []
                    if row["miss"]:
                        bits.append("missed " + fmt(row["miss"]))
                    if row["wrong"]:
                        bits.append("WRONG " + fmt(row["wrong"]))
                    print(f"{'':<34} {row['id']:<22} {'; '.join(bits)}")
        print()


if __name__ == "__main__":
    main()
