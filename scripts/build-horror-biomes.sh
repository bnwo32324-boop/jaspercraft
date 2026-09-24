#!/usr/bin/env bash
# Linux/cloud twin of build-horror-biomes.ps1: candidate/horror-biomes/JasprHorrorBiomes.jar (Java 8 bytecode).
# Never installs anything under server/.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC=$ROOT/server/custom-plugins/JasprHorrorBiomes
OUT=$ROOT/candidate/horror-biomes
CP=$ROOT/server/cache/patched_1.12.2.jar
[ -f "$CP" ] || CP=$ROOT/candidate/structure-audit/testserver-template/cache/patched_1.12.2.jar
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
javac --release 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$CP" -d "$WORK" $(find "$SRC/src" -name '*.java')
cp -r "$SRC/resources/." "$WORK/"
rm -f "$OUT/JasprHorrorBiomes.jar"
jar --create --file "$OUT/JasprHorrorBiomes.jar" -C "$WORK" .
echo "Built candidate only (not installed): $OUT/JasprHorrorBiomes.jar version=$(grep '^version:' "$SRC/resources/plugin.yml" | cut -d' ' -f2)"
sha256sum "$OUT/JasprHorrorBiomes.jar"
