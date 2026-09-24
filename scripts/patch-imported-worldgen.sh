#!/usr/bin/env bash
# Rebuilds JasprImportedWorldgen 1.2.1 from the 1.2.0 jar: recompiles the patched GearLoot
# (server/custom-plugins/JasprImportedWorldgen/patch) and bumps plugin.yml. Nothing else changes.
#   bash scripts/patch-imported-worldgen.sh [BASE_1.2.0_JAR] [OUT_JAR]
# Default base: the 1.2.0 jar from git history; default out: candidate/imported/JasprImportedWorldgen.jar.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
BASE=${1:-}
if [ -z "$BASE" ]; then BASE=$WORK/base.jar; git -C "$ROOT" show fbc9c8e:server/plugins/JasprImportedWorldgen.jar > "$BASE"; fi
OUT=${2:-$ROOT/candidate/imported/JasprImportedWorldgen.jar}
CP=$ROOT/server/cache/patched_1.12.2.jar
[ -f "$CP" ] || CP=$ROOT/candidate/structure-audit/testserver-template/cache/patched_1.12.2.jar
unzip -p "$BASE" plugin.yml | grep -q '^version: 1.2.0$' || { echo "base jar is not JasprImportedWorldgen 1.2.0" >&2; exit 2; }
mkdir -p "$WORK/classes" "$(dirname "$OUT")"
javac --release 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$CP:$BASE:$ROOT/server/plugins/JasprHorrorBiomes.jar" -d "$WORK/classes" \
  "$ROOT/server/custom-plugins/JasprImportedWorldgen/patch/chat/jaspr/imported/GearLoot.java"
unzip -p "$BASE" plugin.yml | sed 's/^version: 1.2.0$/version: 1.2.1/' > "$WORK/classes/plugin.yml"
cp "$BASE" "$OUT.tmp"
(cd "$WORK/classes" && jar uf "$OUT.tmp" plugin.yml chat/jaspr/imported/GearLoot.class)
mv -f "$OUT.tmp" "$OUT"
echo "Built candidate only (not installed): $OUT version=1.2.1"
sha256sum "$OUT"
