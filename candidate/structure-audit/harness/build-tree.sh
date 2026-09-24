#!/usr/bin/env bash
# Builds the canonical JasprHorrorBiomes source tree (or another tree) into a plugin jar for the
# capture harness. Never writes anything under server\ (the tree is only read).
#
#   bash build-tree.sh [OUT_JAR] [TREE]
#     OUT_JAR  default SA/jars/base323.jar
#     TREE     default GAME/server/custom-plugins/JasprHorrorBiomes  (must contain src/ and resources/)
#
# Standard project command: JDK 17 javac --release 8 -encoding UTF-8 -cp server/cache/patched_1.12.2.jar
set -euo pipefail
GAME=/c/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale
SA=$GAME/candidate/structure-audit
JDK17="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin"
CP="$GAME/server/cache/patched_1.12.2.jar"
OUT=${1:-$SA/jars/base323.jar}
TREE=${2:-$GAME/server/custom-plugins/JasprHorrorBiomes}
case "$OUT" in [A-Za-z]:*) OUT="/$(echo "${OUT:0:1}" | tr 'A-Z' 'a-z')${OUT:2}"; OUT=${OUT//\\//};; esac
case "$TREE" in [A-Za-z]:*) TREE="/$(echo "${TREE:0:1}" | tr 'A-Z' 'a-z')${TREE:2}"; TREE=${TREE//\\//};; esac
[ -d "$TREE/src" ] && [ -d "$TREE/resources" ] || { echo "not a plugin tree: $TREE" >&2; exit 2; }
grep -q "class CaptureHook" "$TREE/src/chat/jaspr/biomes/CaptureHook.java" 2>/dev/null \
  || echo "WARNING: $TREE has no CaptureHook; captured dumps will have an empty mask" >&2
WORK=$(mktemp -d "$SA/harness/.tree-build.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$(dirname "$OUT")"
(cd "$TREE" && "$JDK17/javac.exe" --release 8 -encoding UTF-8 -nowarn -cp "$CP" -d "$WORK/classes" $(find src -name '*.java'))
rm -f "$OUT.tmp"
"$JDK17/jar.exe" cf "$OUT.tmp" -C "$WORK/classes" . -C "$TREE/resources" .
mv -f "$OUT.tmp" "$OUT"
echo "built $OUT  classes=$(find "$WORK/classes" -name '*.class' | wc -l)  version=$(grep '^version:' "$TREE/resources/plugin.yml" | cut -d' ' -f2)  md5=$(md5sum "$OUT" | cut -d' ' -f1)"
