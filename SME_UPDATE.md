# So Many Enchantments (JasprEnchantments 1.0.0) -- 2026-09-26

Port of the Forge mod "Rin's So Many Enchantments?" 1.0.9 (source + jar supplied by the owner).

- **Server** (`server/custom-plugins/JasprEnchantments`, `bash build.sh`): all 130 enchantments registered as real NMS
  enchantments (ids 72-201, keys `somanyenchantments:<name>`, table in `resources/enchantments.tsv`) with a Bukkit
  wrapper each, SME's exact levels, enchantability, rarity, treasure/curse flags, 46 incompatibility groups, exact
  table/anvil item sets (custom slot types), table/librarian/loot blacklists, and every effect (combat pipeline in
  Forge priority order, arrows, crits, looting, fishing, dig assist, temporary ice/magma, attribute modifiers with SME's
  UUIDs, Upgraded Potentials). SME's enchanting-table upgrade mechanic is a GUI opened by sneak-right-clicking a table
  (prismarine tokens 1/8, 10% tier failure, curse equivalents, 30 bookshelves). `/jsme` = op test helpers.
  Deviation (owner rule): Sol's Blessing never applies Glowing. Self-test (`-Djaspr.sme.selftest=true`): 39/39 with every
  live plugin installed.
- **Client** (`scripts/build-sme-client.cjs`, `scripts/build-sme-assets.cjs`): the TeaVM client registers the same 130
  enchantments at the same ids after vanilla's (tooltips, enchanting-table hints, anvil, Creative books), SME's lang
  (names coloured as SME colours them, levels to CCLVI) and sounds; client-side effects: Advanced Efficiency /
  Inefficient dig speed, Light Weight / Heavy Weight jump, Swift Swimming, Strafe draw speed. Fail-safe: a registration
  error skips that enchantment; `window.JasprSmeStatus` reports registered/failed.
- **Loot**: vanilla loot tables (enchant_randomly etc.) pick SME enchantments through the registry; JasprHorrorBiomes
  structure books (about half) and Muse+GLM_Maps chests carry SME enchantments; curses, Pandora's Curse and Supreme
  Protection are never placed as loot.
