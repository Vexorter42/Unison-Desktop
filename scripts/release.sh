#!/usr/bin/env bash
# Выпуск новой версии одной командой:
#   ./scripts/release.sh 0.1.2 "Что нового в этой версии"
# Поднимает версию, собирает .msi и .exe, коммитит, ставит тег и публикует GitHub Release —
# установленные копии Unison увидят его как обновление.
set -euo pipefail

VERSION="${1:?Укажите версию, например: ./scripts/release.sh 0.1.2 \"Что нового\"}"
NOTES="${2:-}"
cd "$(dirname "$0")/.."

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
  echo "Тег v$VERSION уже есть." >&2
  exit 1
fi

GRADLE=build.gradle.kts
sed -i -E "s/val appVersion = \"[^\"]+\"/val appVersion = \"$VERSION\"/" "$GRADLE"
echo "→ версия $VERSION"

./gradlew packageMsi packageExe --console=plain -q
mkdir -p dist
MSI="dist/Unison-Desktop-$VERSION.msi"
EXE="dist/Unison-Desktop-$VERSION.exe"
cp build/compose/binaries/main/msi/Unison-"$VERSION".msi "$MSI"
cp build/compose/binaries/main/exe/Unison-"$VERSION".exe "$EXE"
echo "→ собраны $MSI и $EXE"

git add "$GRADLE" CHANGELOG.md
git commit -m "Release v$VERSION"
git tag "v$VERSION"
git push
git push origin "v$VERSION"

gh release create "v$VERSION" "$MSI" "$EXE" --title "Unison для Windows $VERSION" --notes "${NOTES:-Подробности — в CHANGELOG.md}"
echo "✓ Релиз v$VERSION опубликован"
