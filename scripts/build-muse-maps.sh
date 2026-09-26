#!/usr/bin/env bash
# Builds JasprMuseMaps.jar (code) and JasprMuseMapsPack.jar (Muse+GLM_Maps block data) into server/plugins/.
#   bash scripts/build-muse-maps.sh            (pack data must exist: python -B scripts/muse-glm/build_muse_pack.py)
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
P=$ROOT/server/custom-plugins/JasprMuseMaps
OUT=${1:-$ROOT/server/plugins}
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
SEP=':'; NATIVE_ROOT=$ROOT
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';'; NATIVE_ROOT=$(cygpath -m "$ROOT");; esac
CP="$NATIVE_ROOT/server/cache/patched_1.12.2.jar${SEP}$NATIVE_ROOT/server/plugins/JasprHorrorBiomes.jar"
mkdir -p "$WORK/code" "$WORK/pack"
javac -nowarn --release 8 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$WORK/code" $(find "$P/src" -name '*.java')
cp "$P/resources/"* "$WORK/code/"
jar --create --file "$OUT/JasprMuseMaps.jar" -C "$WORK/code" .
javac -nowarn --release 8 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$WORK/pack" $(find "$P/pack-src" -name '*.java')
[ -f "$P/pack/maps/catalog.json" ] || { echo "pack data missing: run scripts/muse-glm/build_muse_pack.py" >&2; exit 2; }
cp -r "$P/pack/"* "$WORK/pack/"
jar --create --file "$OUT/JasprMuseMapsPack.jar" -C "$WORK/pack" .
ls -la "$OUT/JasprMuseMaps.jar" "$OUT/JasprMuseMapsPack.jar"
