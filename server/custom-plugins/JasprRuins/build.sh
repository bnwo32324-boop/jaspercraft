#!/usr/bin/env bash
# Builds JasprRuins into build/JasprRuins.jar (Java 8 bytecode against the patched Paper 1.12.2 jar, JasprHorrorBiomes
# for Terrain and the JasprLostCities build with CityApi.registerWorld). HB_JAR / LC_JAR override those jars.
set -e
cd "$(dirname "$0")"
GAME="$(cd ../../.. && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin}"
[ -x "$JDK/javac" ] || [ -x "$JDK/javac.exe" ] || JDK="$(dirname "$(command -v javac)")"
SEP=':'; W() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';'; W() { cygpath -m "$1"; };; esac
HB="${HB_JAR:-$GAME/server/plugins/JasprHorrorBiomes.jar}"
LC="${LC_JAR:-$GAME/server/custom-plugins/JasprLostCities/build/JasprLostCities.jar}"
rm -rf build/classes && mkdir -p build/classes
"$JDK/javac" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -proc:none -cp "$(W "$GAME/server/cache/patched_1.12.2.jar")$SEP$(W "$HB")$SEP$(W "$LC")" -d build/classes $(find src -name '*.java')
"$JDK/jar" --create --file build/JasprRuins.jar -C build/classes . -C resources .
echo "built build/JasprRuins.jar"
