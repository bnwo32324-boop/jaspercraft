#!/usr/bin/env bash
# Builds the fixture-only LightProbe plugin (never deployed) into candidate/light-lab/LightProbe.jar
# (Java 8 bytecode against the patched Paper 1.12.2 jar).
set -e
cd "$(dirname "$0")"
GAME="$(cd ../.. && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin}"
[ -x "$JDK/javac" ] || [ -x "$JDK/javac.exe" ] || JDK="$(dirname "$(command -v javac)")"
W() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) W() { cygpath -m "$1"; };; esac
PAPER="${PAPER_JAR:-$GAME/server/cache/patched_1.12.2.jar}"
OUT="$GAME/candidate/light-lab"
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
"$JDK/javac" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -proc:none -cp "$(W "$PAPER")" -d "$OUT/classes" $(find probe/src -name '*.java')
"$JDK/jar" --create --file "$OUT/LightProbe.jar" -C "$OUT/classes" . -C probe/resources .
echo "built $OUT/LightProbe.jar"
