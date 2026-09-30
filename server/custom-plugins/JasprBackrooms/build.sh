#!/usr/bin/env bash
# Builds JasprBackrooms into build/JasprBackrooms.jar (Java 8 bytecode against the patched Paper 1.12.2 jar).
set -e
cd "$(dirname "$0")"
GAME="$(cd ../../.. && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin}"
[ -x "$JDK/javac" ] || [ -x "$JDK/javac.exe" ] || JDK="$(dirname "$(command -v javac)")"
W() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) W() { cygpath -m "$1"; };; esac
rm -rf build/classes && mkdir -p build/classes
"$JDK/javac" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -proc:none -cp "$(W "$GAME/server/cache/patched_1.12.2.jar")" -d build/classes $(find src -name '*.java')
"$JDK/jar" --create --file build/JasprBackrooms.jar.tmp -C build/classes . -C resources .
mv -f build/JasprBackrooms.jar.tmp build/JasprBackrooms.jar
echo "built build/JasprBackrooms.jar"
