#!/usr/bin/env bash
# Builds JasprMutantLairs.jar into server/plugins/ (or the given folder), then checks every lair drawing (Preview).
#   bash scripts/build-mutant-lairs.sh [out dir]
# The previews (isometric PNGs of all twelve lairs) land in $WORK/preview unless LAIRS_PREVIEW=<dir> keeps them.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
P=$ROOT/server/custom-plugins/JasprMutantLairs
OUT=${1:-$ROOT/server/plugins}
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
SEP=':'; NATIVE_ROOT=$ROOT
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';'; NATIVE_ROOT=$(cygpath -m "$ROOT");; esac
# The server jar is not in git: LAIRS_SERVER_JAR, else the checkout's own cache, else the live one on the PC.
SERVER_JAR=${LAIRS_SERVER_JAR:-$NATIVE_ROOT/server/cache/patched_1.12.2.jar}
[ -f "$SERVER_JAR" ] || SERVER_JAR=C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/server/cache/patched_1.12.2.jar
CP="$SERVER_JAR${SEP}$NATIVE_ROOT/server/plugins/JasprHorrorBiomes.jar"
mkdir -p "$WORK/code"
javac -J-XX:ActiveProcessorCount=1 -nowarn --release 8 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$WORK/code" $(find "$P/src" -name '*.java')
jar -J-XX:ActiveProcessorCount=1 --create --file "$OUT/JasprMutantLairs.jar" -C "$WORK/code" . -C "$P/resources" .
java -XX:ActiveProcessorCount=1 -Xmx512m -cp "$OUT/JasprMutantLairs.jar" chat.jaspr.lairs.Preview "${LAIRS_PREVIEW:-$WORK/preview}"
ls -la "$OUT/JasprMutantLairs.jar"
