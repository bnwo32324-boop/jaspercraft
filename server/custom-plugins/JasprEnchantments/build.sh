#!/usr/bin/env bash
# Builds build/JasprEnchantments.jar (Java 8 bytecode against Paper 1.12.2).
# SmeSlotType extends an enum, which javac forbids, so it is compiled first against stub/ (a
# non-enum stand-in that is never packaged); the rest compiles against the real server jar.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
GAME="$(cd "$HERE/../../.." && pwd)"
CP="$GAME/server/cache/patched_1.12.2.jar"
JAVAC="${JAVAC:-javac}"
JAR="${JARTOOL:-jar}"
OUT="$HERE/build/classes"
STUB="$HERE/build/stub"
SEP=":"; W() { printf '%s' "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";"; W() { cygpath -w "$1"; };; esac
rm -rf "$HERE/build"
mkdir -p "$OUT" "$STUB"
"$JAVAC" --release 8 -encoding UTF-8 -nowarn -cp "$CP" -d "$STUB" "$HERE/stub/net/minecraft/server/v1_12_R1/EnchantmentSlotType.java"
"$JAVAC" --release 8 -encoding UTF-8 -nowarn -cp "$(W "$STUB")$SEP$(W "$CP")" -d "$OUT" "$HERE/slottype/chat/jaspr/enchant/nms/SmeSlotType.java"
"$JAVAC" --release 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$(W "$OUT")$SEP$(W "$CP")" -d "$OUT" $(find "$HERE/src" -name '*.java')
"$JAR" --create --file "$HERE/build/JasprEnchantments.jar" -C "$OUT" . -C "$HERE/resources" .
rm -rf "$OUT" "$STUB"
echo "built $HERE/build/JasprEnchantments.jar"
