#!/usr/bin/env bash
# Builds JasprAtlas into build/JasprAtlas.jar (Java 8 bytecode against the patched Paper 1.12.2 jar).
set -e
cd "$(dirname "$0")"
GAME="$(cd ../../.. && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin}"
[ -x "$JDK/javac" ] || [ -x "$JDK/javac.exe" ] || JDK="$(dirname "$(command -v javac)")"
W() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) W() { cygpath -m "$1"; };; esac
PAPER="${PAPER_JAR:-$GAME/server/cache/patched_1.12.2.jar}"
[ -f "$PAPER" ] || PAPER="/c/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/server/cache/patched_1.12.2.jar"
rm -rf build/classes && mkdir -p build/classes
"$JDK/javac" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -proc:none -cp "$(W "$PAPER")" -d build/classes $(find src -name '*.java')
"$JDK/jar" --create --file build/JasprAtlas.jar -C build/classes . -C resources .
echo "built build/JasprAtlas.jar"
