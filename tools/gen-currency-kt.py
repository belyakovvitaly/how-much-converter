#!/usr/bin/env python3
"""Generates the Kotlin currency tables from extension/src/currency.js.

The phone apps and the extension have to agree on what a currency token is;
they cannot if the tables are copied by hand. So the JavaScript stays the one
source and this writes the Kotlin, the same way tools/ocr-bench/bench.py reads
it for the benchmark.

    ./tools/gen-currency-kt.py            # rewrite the generated file
    ./tools/gen-currency-kt.py --check    # fail if it is out of date (CI)

Regex sources are translated as they are copied: JavaScript spells a script
class \\p{Script=Han}, Java spells it \\p{IsHan}.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "extension" / "src" / "currency.js"
TARGET = (ROOT / "android" / "core" / "src" / "main" / "kotlin"
          / "converter" / "core" / "CurrencyTables.kt")

SRC = SOURCE.read_text(encoding="utf-8")


def block(name, kind="object"):
    """The body of a top-level `const NAME = { ... };` (or `[ ... ];`)."""
    opener, closer = ("\\{", "\\};") if kind == "object" else ("\\[", "\\];")
    m = re.search(f"const {name} = {opener}(.*?)\n{closer}", SRC, re.S)
    if not m:
        sys.exit(f"cannot find {name} in {SOURCE}")
    return m.group(1)


JS_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f",
              "v": "\v", "0": "\0", "\\": "\\", '"': '"', "'": "'",
              "`": "`", "/": "/"}


def js_string(raw):
    """Decodes a JavaScript string literal body to its runtime value.

    Written out rather than leaning on unicode_escape, which decodes bytes and
    so mangles every non-ASCII character it meets — and these tables are full
    of them (元, ₽, Kč).
    """
    out, i = [], 0
    while i < len(raw):
        if raw[i] != "\\":
            out.append(raw[i])
            i += 1
            continue
        nxt = raw[i + 1]
        if nxt == "u" and raw[i + 2] == "{":
            end = raw.index("}", i + 3)
            out.append(chr(int(raw[i + 3:end], 16)))
            i = end + 1
        elif nxt == "u":
            out.append(chr(int(raw[i + 2:i + 6], 16)))
            i += 6
        elif nxt == "x":
            out.append(chr(int(raw[i + 2:i + 4], 16)))
            i += 4
        else:
            out.append(JS_ESCAPES.get(nxt, nxt))
            i += 2
    return "".join(out)


ENTRY = re.compile(
    r'^\s*(?:"((?:[^"\\]|\\.)*)"|([^\s:,][^\s:]*))\s*:\s*"([A-Za-z]{3})"\s*,',
    re.M)


def symbol_to_code():
    """SYMBOL_TO_CODE, insertion order preserved — the build sorts by length,
    but keeping the file's order makes the generated Kotlin diffable."""
    out = {}
    for quoted, bare, code in ENTRY.findall(block("SYMBOL_TO_CODE")):
        out[js_string(quoted) if quoted else bare] = code
    if not out:
        sys.exit("SYMBOL_TO_CODE parsed empty")
    return out


def currency_names():
    """CURRENCY_NAMES, for a picker that has to show more than a code."""
    body = block("CURRENCY_NAMES")
    names = dict(re.findall(r'^\s*([A-Z]{3}):\s*"((?:[^"\\]|\\.)*)"', body, re.M))
    if not names:
        sys.exit("CURRENCY_NAMES parsed empty")
    return {code: js_string(name) for code, name in names.items()}


def code_by_region(name):
    """A two-letter key to a currency: COUNTRY_TO_CURRENCY or LANG_TO_CURRENCY."""
    pairs = re.findall(r'\b([a-z]{2}):\s*"([A-Z]{3})"', block(name))
    if not pairs:
        sys.exit(f"{name} parsed empty")
    return dict(pairs)


# Which country's flag stands for a currency in the picker. Most follow from
# COUNTRY_TO_CURRENCY; these are the ones several countries share, or none does.
FLAG_OVERRIDES = {
    "EUR": "eu",   # the union's own flag, rather than picking a member
    "USD": "us",   # also spent in Ecuador
    "GBP": "gb",
    "CHF": "ch",   # also Liechtenstein
    "BGN": "bg",   # Bulgaria prices in euro now; the lev is kept for old pages
}


def currency_to_country():
    """A flag for every currency the picker can show.

    Display only: this says which flag to draw beside a code, never what a page
    or a photograph is priced in.
    """
    by_currency = {}
    for country, code in code_by_region("COUNTRY_TO_CURRENCY").items():
        by_currency.setdefault(code, []).append(country)

    chosen = {code: sorted(countries)[0] for code, countries in by_currency.items()}
    chosen.update(FLAG_OVERRIDES)

    missing = [code for code in currency_codes() if code not in chosen]
    if missing:
        sys.exit(
            f"no flag for {', '.join(missing)} — add them to FLAG_OVERRIDES in "
            f"{pathlib.Path(__file__).name}"
        )
    return {code: chosen[code] for code in currency_codes()}


def currency_codes():
    body = block("CURRENCY_NAMES")
    codes = re.findall(r"^\s*([A-Z]{3}):", body, re.M)
    if not codes:
        sys.exit("CURRENCY_NAMES parsed empty")
    return codes


def codes_needing_capitals():
    m = re.search(r"const CODES_NEEDING_CAPITALS = new Set\(\[(.*?)\]\)", SRC, re.S)
    if not m:
        sys.exit("cannot find CODES_NEEDING_CAPITALS")
    return re.findall(r'"([A-Z]{3})"', m.group(1))


def token_patterns():
    """TOKEN_PATTERNS holds regex source, not literals, so only the script
    class spelling is translated."""
    out = {}
    body = block("TOKEN_PATTERNS")
    for quoted, bare, value in re.findall(
            r'^\s*(?:"((?:[^"\\]|\\.)*)"|([^\s:,][^\s:]*))\s*:\s*"((?:[^"\\]|\\.)*)"\s*,',
            body, re.M):
        key = js_string(quoted) if quoted else bare
        # Decode first: these are regex source held in a string literal, so the
        # backslashes arrive doubled and must come down to one before the
        # pattern is translated and re-escaped for Kotlin.
        out[key] = js_to_java_regex(js_string(value))
    return out


def js_to_java_regex(source):
    """\\p{Script=Han} -> \\p{IsHan}: Java's own spelling of a script class.

    Takes a decoded pattern — one backslash per escape — so it works the same
    on a pattern that came from a string literal as on one that came from a
    regex literal.
    """
    return re.sub(r"\\p\{Script=(\w+)\}", r"\\p{Is\1}", source)


def raw_templates():
    """Every `const X = String.raw` ... ``, with ${X} references resolved."""
    raws = dict(re.findall(r"const (\w+) = String\.raw`(.*?)`;", SRC, re.S))

    def resolve(name, seen=()):
        if name in seen:
            sys.exit(f"circular String.raw reference at {name}")
        out = raws[name]
        for ref in set(re.findall(r"\$\{(\w+)\}", out)):
            if ref not in raws:
                sys.exit(f"{name} references ${{{ref}}}, not a String.raw const")
            out = out.replace("${" + ref + "}", resolve(ref, seen + (name,)))
        return out

    return {name: resolve(name) for name in raws}


def ambiguous_symbols():
    """AMBIGUOUS_SYMBOLS, with the "$" entry's DOLLAR_CURRENCIES spread out."""
    dollars = re.findall(r'codes: \[(.*?)\]', block("DOLLAR_CURRENCIES", "array"), re.S)
    dollar_codes = [c for group in dollars for c in re.findall(r'"([A-Z]{3})"', group)]

    out = {}
    for quoted, bare, value in re.findall(
            r'^\s*(?:"((?:[^"\\]|\\.)*)"|([^\s:,][^\s:]*))\s*:\s*(\[[^\]]*\]|DOLLAR_CURRENCIES[^,]*),',
            block("AMBIGUOUS_SYMBOLS"), re.M | re.S):
        key = js_string(quoted) if quoted else bare
        out[key] = dollar_codes if "DOLLAR_CURRENCIES" in value \
            else re.findall(r'"([A-Z]{3})"', value)
    if "$" not in out:
        sys.exit("AMBIGUOUS_SYMBOLS lost its \"$\" entry")
    return out


def ambiguous_codes():
    out = {}
    for code, value in re.findall(r'^\s*([A-Z]{3}):\s*(\[[^\]]*\])',
                                  block("AMBIGUOUS_CODES"), re.M):
        out[code] = re.findall(r'"([A-Z]{3})"', value)
    return out


# --- emitting ---------------------------------------------------------------
def kt_string(value):
    """A Kotlin string literal. Everything goes in escaped, so a backslash in a
    regex source survives the trip without a raw-string delimiter clash."""
    out = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + out.replace("\n", "\\n") + '"'


def kt_map(entries, indent="    "):
    return "\n".join(f"{indent}{kt_string(k)} to {kt_string(v)}," for k, v in entries.items())


def kt_list_map(entries, indent="    "):
    return "\n".join(
        f"{indent}{kt_string(k)} to listOf({', '.join(kt_string(v) for v in vs)}),"
        for k, vs in entries.items())


def render():
    raws = raw_templates()
    for required in ("NUMBER",):
        if required not in raws:
            sys.exit(f"cannot find {required} in {SOURCE}")

    spaced = re.search(r"const SPACED_SCRIPT =\s*/(.*?)/u;", SRC, re.S)
    if not spaced:
        sys.exit("cannot find SPACED_SCRIPT")

    return f'''// Generated by tools/gen-currency-kt.py from extension/src/currency.js.
// Do not edit: change the JavaScript and regenerate, or the phone apps and the
// extension will disagree about what counts as a price.
package converter.core

/** Currency tokens to ISO 4217 codes. */
val SYMBOL_TO_CODE: Map<String, String> = linkedMapOf(
{kt_map(symbol_to_code())}
)

/** Every currency the converter knows a name for. */
val CURRENCY_CODES: List<String> = listOf(
    {", ".join(kt_string(c) for c in currency_codes())}
)

/** What each currency is called, for a picker that shows more than a code. */
val CURRENCY_NAMES: Map<String, String> = mapOf(
{kt_map(currency_names())}
)

/**
 * Where a currency is spent, by country code. Used to guess what to convert
 * into from where the phone is, and by the extension to read what a page is
 * priced in.
 */
val COUNTRY_TO_CURRENCY: Map<String, String> = mapOf(
{kt_map(code_by_region("COUNTRY_TO_CURRENCY"))}
)

/** The same guess from a language tag, when the country is not known. */
val LANG_TO_CURRENCY: Map<String, String> = mapOf(
{kt_map(code_by_region("LANG_TO_CURRENCY"))}
)

/** Which country's flag stands for a currency. Display only. */
val CURRENCY_TO_COUNTRY: Map<String, String> = mapOf(
{kt_map(currency_to_country())}
)

/** Codes that are also everyday English words, so only read in capitals. */
val CODES_NEEDING_CAPITALS: Set<String> = setOf(
    {", ".join(kt_string(c) for c in codes_needing_capitals())}
)

/** Tokens too common in ordinary text to match bare: regex source, not literals. */
val TOKEN_PATTERNS: Map<String, String> = mapOf(
{kt_map(token_patterns())}
)

/** Symbols several currencies share; pinned down by context or not at all. */
val AMBIGUOUS_SYMBOLS: Map<String, List<String>> = mapOf(
{kt_list_map(ambiguous_symbols())}
)

/** ISO codes that are also ordinary words or version numbers. */
val AMBIGUOUS_CODES: Map<String, List<String>> = mapOf(
{kt_list_map(ambiguous_codes())}
)

/** The number grammar, with ${{MINOR}} already resolved. */
const val NUMBER: String = {kt_string(raws["NUMBER"])}

/** Scripts that space their words, and so need their tokens fenced by letters. */
const val SPACED_SCRIPT: String = {kt_string(js_to_java_regex(spaced.group(1)))}
'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="exit non-zero if the generated file is out of date")
    args = ap.parse_args()

    generated = render()
    if args.check:
        current = TARGET.read_text(encoding="utf-8") if TARGET.exists() else ""
        if current != generated:
            sys.exit(f"{TARGET.relative_to(ROOT)} is out of date; "
                     f"run tools/gen-currency-kt.py")
        print(f"{TARGET.relative_to(ROOT)} is up to date")
        return

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(generated, encoding="utf-8")
    print(f"{TARGET.relative_to(ROOT)}: {len(symbol_to_code())} symbols, "
          f"{len(currency_codes())} currencies, {len(token_patterns())} token patterns, "
          f"{len(code_by_region('COUNTRY_TO_CURRENCY'))} countries, "
          f"{len(currency_to_country())} flags")


if __name__ == "__main__":
    main()
