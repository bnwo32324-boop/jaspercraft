#!/usr/bin/env bash
# Builds server/plugins/JasprMutants.jar (Mutant Creatures Legacy server port, AGPL-3.0).
# The source is MCP-named (the mod's own names); the mcpremap toolchain compiles it against an MCP-named copy of Paper,
# reobfuscates it to Paper's Spigot names and verifies every reference against the real Paper jar (exit 1 = problems).
#   bash scripts/build-mutants-plugin.sh            (Windows Git Bash; toolchain: C:/Users/AM/Documents/JasperCraft-Mutants/toolchain)
#   MCPREMAP_TOOLCHAIN=/path/to/toolchain bash scripts/build-mutants-plugin.sh
# Java steps run at low priority under the PC's shared heavy-work lock (the toolchain's env.sh).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TC="${MCPREMAP_TOOLCHAIN:-C:/Users/AM/Documents/JasperCraft-Mutants/toolchain}"
PLUGIN="$ROOT/server/custom-plugins/JasprMutants"
OUT="$ROOT/candidate/mutants-build"
[ -f "$TC/scripts/compile.sh" ] || { echo "mcpremap toolchain not found at $TC" >&2; exit 2; }
rm -rf "$OUT"
mkdir -p "$OUT/resources"
# Resources: plugin.yml, the mod's data files, plus the licence and notice (AGPL-3.0 section 4/5: ship the licence).
cp -r "$PLUGIN/resources/." "$OUT/resources/"
cp "$PLUGIN/LICENSE" "$OUT/resources/LICENSE"
cp "$PLUGIN/NOTICE" "$OUT/resources/NOTICE"
bash "$TC/scripts/compile.sh" "$PLUGIN/src" "$OUT/resources" "$OUT/JasprMutants-mcp.jar"
bash "$TC/scripts/reobf.sh" "$OUT/JasprMutants-mcp.jar" "$OUT/JasprMutants-spigot.jar"
bash "$TC/scripts/verify.sh" "$OUT/JasprMutants-spigot.jar" "$OUT/JasprMutants-mcp.jar"
cp -f "$OUT/JasprMutants-spigot.jar" "$ROOT/server/plugins/JasprMutants.jar"
echo "Built server/plugins/JasprMutants.jar (verify report: candidate/mutants-build/JasprMutants-spigot.verify.txt)"
sha256sum "$ROOT/server/plugins/JasprMutants.jar"
