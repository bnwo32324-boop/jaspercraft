#!/usr/bin/env bash
# Rebuilds JasprImportedWorldgen 1.3.0 from the 1.2.0 jar: recompiles every class under
# server/custom-plugins/JasprImportedWorldgen/patch (1.2.1: GearLoot; 1.3.0: grid 3 = CellPlanner, CellLedger,
# Admission, ClaimGuard, ImportedWorldgenPlugin) against Paper + the base jar + JasprHorrorBiomes 3.28.0, replaces
# exactly those classes (and their inner classes) in a copy of the base jar and bumps plugin.yml. Nothing else changes.
#   bash scripts/patch-imported-worldgen.sh [BASE_1.2.0_JAR] [OUT_JAR] [HB_3.28_JAR]
# Defaults: base = the 1.2.0 jar from git history; out = candidate/imported/JasprImportedWorldgen.jar;
# HB = candidate/horror-biomes/JasprHorrorBiomes.jar (build it with scripts/build-horror-biomes.sh).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
BASE=${1:-}
if [ -z "$BASE" ]; then BASE=$WORK/base.jar; git -C "$ROOT" show fbc9c8e:server/plugins/JasprImportedWorldgen.jar > "$BASE"; fi
OUT=${2:-$ROOT/candidate/imported/JasprImportedWorldgen.jar}
HB=${3:-$ROOT/candidate/horror-biomes/JasprHorrorBiomes.jar}
VERSION=1.3.0
CP=$ROOT/server/cache/patched_1.12.2.jar
[ -f "$CP" ] || CP=$ROOT/candidate/structure-audit/testserver-template/cache/patched_1.12.2.jar
PATCH=$ROOT/server/custom-plugins/JasprImportedWorldgen/patch
unzip -p "$BASE" plugin.yml | grep -q '^version: 1.2.0$' || { echo "base jar is not JasprImportedWorldgen 1.2.0" >&2; exit 2; }
unzip -p "$HB" plugin.yml | grep -q '^version: 3\.2[89]\.' || { echo "HB jar is not JasprHorrorBiomes 3.28+: $HB" >&2; exit 2; }
mkdir -p "$WORK/classes" "$(dirname "$OUT")"
javac --release 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$CP:$BASE:$HB" -d "$WORK/classes" \
  $(find "$PATCH" -name '*.java' | sort)
unzip -p "$BASE" plugin.yml | sed "s/^version: 1.2.0\$/version: $VERSION/" > "$WORK/classes/plugin.yml"
cp "$BASE" "$OUT.tmp"
# Drop the base jar's copies of every patched top-level class and its inner classes (an anonymous class the new
# source no longer has must not linger), then add the recompiled ones.
DROP=()
for src in $(cd "$PATCH" && find . -name '*.java' | sort); do
  cls=${src#./}; cls=${cls%.java}
  while read -r entry; do DROP+=("$entry"); done < <(unzip -Z1 "$BASE" | grep -E "^${cls}(\\\$[^/]*)?\\.class\$" || true)
done
[ ${#DROP[@]} -eq 0 ] || zip -q -d "$OUT.tmp" "${DROP[@]}"
(cd "$WORK/classes" && jar uf "$OUT.tmp" plugin.yml $(find chat -name '*.class' | sort))
mv -f "$OUT.tmp" "$OUT"
echo "Built candidate only (not installed): $OUT version=$VERSION"
sha256sum "$OUT"
