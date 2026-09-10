#!/usr/bin/env bash
# Packs the extension into a Chrome Web Store-ready zip.
#
# The store wants an archive whose root contains manifest.json — no wrapper
# directory, and nothing that isn't shipped (docs, tests, this script).
set -euo pipefail
cd "$(dirname "$0")/.."

version=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' manifest.json | head -1)
out="dist/how-much-$version.zip"

mkdir -p dist
rm -f "$out"
zip -r -q -X "$out" manifest.json src icons -x '*.DS_Store'

echo "$out"
unzip -l "$out"
