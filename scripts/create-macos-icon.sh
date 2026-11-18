#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

iconset="$(mktemp -d)/Unison.iconset"
mkdir -p "$iconset"
for size in 16 32 128 256 512; do
  sips -s format png -z "$size" "$size" docs/logo.png --out "$iconset/icon_${size}x${size}.png" >/dev/null
  doubled=$((size * 2))
  sips -s format png -z "$doubled" "$doubled" docs/logo.png --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o icons/unison.icns
echo "Created icons/unison.icns"
