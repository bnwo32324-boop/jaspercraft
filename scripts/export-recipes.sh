#!/usr/bin/env bash
# Regenerates client-mods/recipe-table.json (the EasierCrafting panel's recipe list) from a disposable
# Paper test server that has every live Jaspr plugin (e.g. a copy of
# candidate/structure-audit/testserver-template plus server/plugins/Jaspr*.jar). Never run on the live server.
#   bash scripts/export-recipes.sh <test-server-dir>
# then: node scripts/sync-recipe-table.cjs && node scripts/build-recipe-book-client.cjs --upgrade
#       && GEAR_CLIENT_SOURCE=candidate/recipe-book-client/classes.js node scripts/build-gear-client.cjs
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR=$(cd "$1" && pwd)
[ -f "$DIR/paper-1.12.2.jar" ] || { echo "no paper-1.12.2.jar in $DIR" >&2; exit 2; }
CP=$ROOT/server/cache/patched_1.12.2.jar
[ -f "$CP" ] || CP=$ROOT/candidate/structure-audit/testserver-template/cache/patched_1.12.2.jar
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
SRC=$ROOT/scripts/java/recipe-export
javac --release 8 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$WORK/classes" $(find "$SRC/src" -name '*.java')
cp "$SRC/plugin.yml" "$WORK/classes/"
jar --create --file "$DIR/plugins/JasprRecipeExport.jar" -C "$WORK/classes" .
rm -f "$DIR/plugins/JasprRecipeExport/recipes.json"
: > "$WORK/cmds"
( cd "$DIR" && tail -n +1 -f "$WORK/cmds" | java -Xmx1G -Dcom.mojang.eula.agree=true -jar paper-1.12.2.jar --nojline > "$WORK/out" 2>&1 ) &
for i in $(seq 1 180); do grep -q 'RECIPE_EXPORT' "$WORK/out" 2>/dev/null && break; sleep 1; done
echo stop >> "$WORK/cmds"
grep -E 'RECIPE_EXPORT' "$WORK/out" || { tail -20 "$WORK/out"; exit 1; }
for i in $(seq 1 60); do pgrep -f "tail -n \+1 -f $WORK/cmds" >/dev/null || break; sleep 1; done
pkill -f "tail -n \+1 -f $WORK/cmds" 2>/dev/null || true
cp "$DIR/plugins/JasprRecipeExport/recipes.json" "$ROOT/client-mods/recipe-table.json"
rm -f "$DIR/plugins/JasprRecipeExport.jar"
echo "Wrote client-mods/recipe-table.json"
