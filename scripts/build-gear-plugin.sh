#!/usr/bin/env bash
# Linux/cloud twin of build-gear-plugin.ps1: builds candidate/gear/JasprGear.jar (Java 8 bytecode)
# and candidate/gear/gear-catalog.json (GearExport). Never installs anything under server/.
#   bash scripts/build-gear-plugin.sh            (javac/jar/java from PATH; JDK 17+)
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PLUGIN=$ROOT/server/custom-plugins/JasprGear
OUT=$ROOT/candidate/gear
CP=$ROOT/server/cache/patched_1.12.2.jar
[ -f "$CP" ] || CP=$ROOT/candidate/structure-audit/testserver-template/cache/patched_1.12.2.jar
[ -f "$CP" ] || { echo "patched_1.12.2.jar not found" >&2; exit 2; }
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
javac --release 8 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$WORK" $(find "$PLUGIN/src" -name '*.java')
cp "$PLUGIN/resources/plugin.yml" "$PLUGIN/resources/config.yml" "$WORK/"
rm -f "$OUT/JasprGear.jar"
jar --create --file "$OUT/JasprGear.jar" -C "$WORK" .
java -cp "$CP:$OUT/JasprGear.jar" chat.jaspr.gear.GearExport "$OUT/gear-catalog.json"
echo "Built candidate only (not installed): $OUT/JasprGear.jar"
sha256sum "$OUT/JasprGear.jar"
