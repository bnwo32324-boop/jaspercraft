#!/usr/bin/env bash
# Builds the structure-audit capture harness plugin -> SA/harness/build/JasprCaptureHarness.jar
#   bash build.sh [JHB_JAR]     JHB_JAR: a JasprHorrorBiomes jar that contains chat.jaspr.biomes.CaptureHook
#                               (default SA/jars/base323.jar; build it with build-tree.sh)
# The harness targets Java 17 (the test server's JVM; it uses StackWalker). The "stub" ApocalypseItems
# stand-in is compiled for Java 8 and stored as a .bin resource so the plugin class loader never defines it.
set -euo pipefail
GAME=/c/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale
SA=$GAME/candidate/structure-audit
H=$SA/harness
JDK17="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin"
PAPER="$GAME/server/cache/patched_1.12.2.jar"
JHB=${1:-$SA/jars/base323.jar}
case "$JHB" in [A-Za-z]:*) JHB="/$(echo "${JHB:0:1}" | tr 'A-Z' 'a-z')${JHB:2}"; JHB=${JHB//\//};; esac
[ "$(unzip -l "$JHB" | grep -c "chat/jaspr/biomes/CaptureHook.class")" -ge 1 ] || { echo "$JHB has no CaptureHook" >&2; exit 2; }
W=$(mktemp -d "$H/.build.XXXXXX"); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/classes/jaspr/audit/harness/stub" "$W/stub" "$H/build"
# Windows javac wants a ;-separated classpath of Windows paths
wp() { cygpath -w "$1"; }
"$JDK17/javac.exe" --release 17 -encoding UTF-8 -nowarn -cp "$(wp "$PAPER");$(wp "$JHB")" -d "$(wp "$W/classes")" $(find "$H/src" -name '*.java' | while read f; do wp "$f"; done)
"$JDK17/javac.exe" --release 8 -encoding UTF-8 -nowarn -cp "$(wp "$PAPER")" -d "$(wp "$W/stub")" "$(wp "$H/stub-src/chat/jaspr/apocalypse/ApocalypseItems.java")"
cp "$W/stub/chat/jaspr/apocalypse/ApocalypseItems.class" "$W/classes/jaspr/audit/harness/stub/ApocalypseItems.bin"
cp "$H/resources/plugin.yml" "$W/classes/"
rm -f "$H/build/JasprCaptureHarness.jar.tmp"
"$JDK17/jar.exe" cf "$(wp "$H/build/JasprCaptureHarness.jar.tmp")" -C "$(wp "$W/classes")" .
mv -f "$H/build/JasprCaptureHarness.jar.tmp" "$H/build/JasprCaptureHarness.jar"
echo "built $H/build/JasprCaptureHarness.jar md5=$(md5sum "$H/build/JasprCaptureHarness.jar" | cut -d' ' -f1) against $(basename "$JHB")"
