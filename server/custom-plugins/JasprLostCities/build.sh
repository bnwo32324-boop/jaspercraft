#!/usr/bin/env bash
# Builds JasprLostCities into build/JasprLostCities.jar (Java 8 bytecode against the patched Paper 1.12.2 jar and
# JasprHorrorBiomes, whose Terrain/ChunkLight it uses). HB_JAR overrides the HorrorBiomes jar (default: the live one).
set -e
cd "$(dirname "$0")"
GAME="$(cd ../../.. && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin}"
[ -x "$JDK/javac" ] || [ -x "$JDK/javac.exe" ] || JDK="$(dirname "$(command -v javac)")"
SEP=':'; W() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';'; W() { cygpath -m "$1"; };; esac
HB="${HB_JAR:-$GAME/server/plugins/JasprHorrorBiomes.jar}"
rm -rf build/classes && mkdir -p build/classes
"$JDK/javac" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$(W "$GAME/server/cache/patched_1.12.2.jar")$SEP$(W "$HB")" -d build/classes $(find src -name '*.java')
"$JDK/jar" --create --file build/JasprLostCities.jar -C build/classes . -C resources .
echo "built build/JasprLostCities.jar"
