# Survivor Gear (trinket slots) - Phases 1 and 2

Seven Baubles-style trinket slots in the real survival inventory, fifteen apocalyptic trinkets
with their own 16x16 pixel art, real mechanics, recipes, rare mob drops and a structure-loot API.
Design parity target: xzeroair *Trinkets and Baubles* 0.33.4 (behaviour reference only; no code
was copied from it or from Baubles). New plugin `JasprGear`; JasprApocalypse and
JasprHorrorBiomes are untouched. No world reset.

Phase status: **Phase 1 (slots, UI, 15 trinkets, textures, mechanics, loot API) is done.
Phase 2 (status effects, Adrenaline + HUD bar, ability costs, consumables) is done** - see
"Phase 2" below. Phase 3 (mutations / races) is not started - see "Remaining" at the end.

## Playing

- Press **E**: a gear column with seven slots sits to the right of the inventory (to the left on
  narrow screens): **Neck, Ring, Ring, Belt, Head, Body, Charm**. Empty slots show a grey
  silhouette; hovering shows the item tooltip (or the slot's description).
- Put gear on by clicking a slot with the item on the cursor, **shift-clicking** gear in your
  inventory (it goes to a free matching slot; any-slot gear prefers the Charm slot), or
  **right-clicking** the gear while holding it. Click worn gear to pick it back up; shift-click it
  to send it straight to your inventory. The wrong type is refused with a message.
- **/gear** (aliases **/trinkets**, **/kit**) opens the same seven slots as a server menu for
  mobile, Creative and older cached clients, plus the Field Journal **XP bank** button.
  `/gear list`, `/gear recipes [id]`, `/gear bank deposit|withdraw [levels|all]`,
  `/gear scan <any|coal|iron|gold|redstone|lapis|diamond|emerald|quartz>`, `/gear arc|dodge|magnet`.
- Keys (Options > Controls, remappable, saved with the account): **G** Arc Shot, **H** Dodge /
  Blink (sneak+H: remote ender chest), **J** Magnet (sneak+J: repel pulse). K (stats),
  M (waypoints), L (dynamic lights) and C (zoom) are unchanged.
- Creative: all 15 trinkets are in the Miscellaneous tab and in Search ("gear", "trinket", names).
  In Creative, equip with right-click or /gear (the Creative screen has no gear column).
- Death: worn gear drops with your other items (JasprGraves collects it). With keepInventory it
  stays on. Gear persists across reconnects and restarts.

## The fifteen trinkets

Rank = power 1 (weak utility) .. 5 (strongest). Name colour: rank 1-2 green, 3 aqua, 4 purple, 5 gold.

| Trinket (id) | Slot | Rank | Mod counterpart | Mechanics |
| --- | --- | --- | --- | --- |
| Capacitor Belt (`capacitor_belt`) | Belt | 4 | Arcing Orb | +10% speed, -25% lightning damage. **G Arc Shot**: zaps the hostile in your sights (16 m) for 4, chaining to 2 more within 5 m for 2 each (6 s). **H Dodge**: dash along your view (sneak: backwards), 1.5 s fall grace (4 s). 12% of melee hits discharge: +2 damage and a spark jumps to a nearby hostile. |
| Riot Vest (`riot_vest`) | Body | 4 | Damage Shield | Ballistic plates absorb up to 6 damage from melee/projectiles, recharging 1 point per 3 s after 5 s without a hit (action-bar pips). 30% of arrows glance off (deflected). -30% explosion damage. |
| Thermal Goggles (`thermal_goggles`) | Head | 3 | Dragon's Eye | Immune to fire/burning/magma, -50% lava. Sneak still for 1 s: thermal ore scan reports the nearest ore within 8 blocks with distance and direction (3 s; `/gear scan` filter). No night vision. |
| Phase Headset (`phase_headset`) | Head | 5 | Ender Queen's Crown | **H Blink** up to 8 blocks along your view (6 s; stops at walls); **sneak+H** opens your ender chest anywhere (10 s). 10% chance to phase through a hit; arrows may blink you aside (25%, 10 s). Endermen ignore your gaze unless you hit them. Shorts out while wet (water/rain). |
| Field Journal (`field_journal`) | Any | 1 | Experience Device | +20% experience from orbs. XP bank: store up to 1395 points (30 levels) inside the journal item (shown in its lore) via `/gear bank` or the /gear menu button; it travels with the journal. |
| Razor Claws (`razor_claws`) | Any | 3 | Faelis Claw | +1 melee damage; 20% chance to cause Bleeding (1 damage/s for 4 s). Wall climbing: jump at a wall you face to climb it; sneak to cling. |
| Tritium Ring (`tritium_ring`) | Ring | 1 | Ring of Enchanted Eyes | Motion tracker: every 4 s reports how many hostiles are within 12 m and the nearest one's distance/direction; warns when a hostile targets you. Immune to Blindness. No night vision or glow. |
| Sprinter's Brace (`sprint_brace`) | Any | 2 | Stone of Greater Inertia | +15% speed, +30% knockback resistance, -50% fall damage. Momentum: sprint-jumps launch farther and a little higher. |
| Gyro Stabilizer (`gyro_stabilizer`) | Any | 3 | Stone of Inertia Null | +80% knockback resistance, no wall-impact (elytra) damage, -80% fall damage. Gyro brake: a long fall is arrested just above the ground (4 s). |
| Toxin Injector (`toxin_injector`) | Any | 3 | Poison Stone | Immune to poison, including JasprBlight water poison. 20% chance to poison on hit, +2 damage against poisoned targets; melee attackers are poisoned back (25%). |
| Scrap Magnet (`scrap_magnet`) | Any | 2 | Polarized Stone | **J** toggles the magnet: pulls dropped items and XP orbs within 7 m (items you just dropped keep their pickup delay); while on, 30% of arrows veer off. **Sneak+J** repel pulse pushes hostiles and projectiles away (8 s). |
| Rebreather (`rebreather`) | Neck | 1 | Stone of the Sea | Breathe underwater; immune to the blight water poison while in water; swim boost toward where you look; Haste I underwater. |
| Worn Teddy Bear (`teddy_bear`) | Any | 4 | Teddy Bear | Rest anywhere: sneak still for 3 s to heal 1 HP every 2 s (interrupted by damage). Waking after sleep: Well Rested (Regeneration I 30 s, Absorption I 2 min). Last Stand: a fatal hit leaves you at 3 hearts instead (20 min, persisted across relogs). |
| Grav Harness (`grav_harness`) | Any | 5 | Stone of Negative Gravity | Weightless: hover while airborne for up to 12 s, swing to rise, sneak+swing to sink, then a slow descent; no fall damage. |
| Necrotic Ring (`necrotic_ring`) | Ring | 3 | Wither Ring | Immune to Wither. 15% chance to wither on hit; hitting a withered target heals you 1 HP (1 s); melee attackers are withered back (20%). |

Balance notes: effects of duplicates never stack; potions are only minor helpers (Haste underwater,
Well Rested, the harness hover) and are ambient, particle-free and short so removal ends them
within a second; attribute bonuses use fixed per-item modifier UUIDs and are removed exactly.
Hostile targeting honours world PvP (players are only zapped when PvP is on).

## Recipes (vanilla ingredients only; tagged/custom items are refused as ingredients)

Rows top to bottom, `.` = empty.

| Trinket | Shape | Key |
| --- | --- | --- |
| Capacitor Belt | `LRL / IBI / LRL` | L leather, R redstone, I iron ingot, B redstone block |
| Riot Vest | `I.I / LOL / ILI` | I iron ingot, L leather, O obsidian |
| Thermal Goggles | `SLS / GMG` | S string, L leather, G glass pane, M magma cream |
| Phase Headset | `IRI / E.E` | I iron ingot, R redstone, E ender pearl |
| Field Journal | `.F. / IBL` | F feather, I ink sac, B book, L lapis lazuli |
| Razor Claws | `FFF / III` | F flint, I iron ingot |
| Tritium Ring | `NGN / N.N / NNN` | N iron nugget, G glowstone dust |
| Sprinter's Brace | `SLS / LRL / SLS` | S string, L leather, R rabbit's foot |
| Gyro Stabilizer | `GIG / ICI / GIG` | G gold nugget, I iron ingot, C compass |
| Toxin Injector | `N / B / E` | N iron nugget, B glass bottle, E fermented spider eye |
| Scrap Magnet | `R.R / I.I / III` | R redstone, I iron ingot |
| Rebreather | `.S. / LPL / .I.` | S string, L leather, P pufferfish, I iron ingot |
| Worn Teddy Bear | `.W. / WSW / W.W` | W brown wool, S string |
| Grav Harness | `LHL / FIF / L.L` | L leather, H shulker shell, F feather, I iron ingot |
| Necrotic Ring | `NCN / N.N / NBN` | N iron nugget, C coal, B bone |

## Drops and structure loot

- Hostile mobs killed by a player drop one random trinket with 0.35% chance (zombies 0.5%),
  weighted by `6 - rank`; spawner mobs never drop gear. Configurable in
  `plugins/JasprGear/config.yml` (`drops.hostile-chance`, `drops.zombie-chance`, capped at 5%).
- Structure loot API — wired in since 2026-09-24 (JasprHorrorBiomes 3.26.0: catalogue tiers I-V, set-piece
  depth tiers, troves, dungeon rooms; JasprImportedWorldgen 1.2.0: about 2 + tier chests per design):
  class **`chat.jaspr.gear.GearApi`** (load with JasprGear's class loader, e.g.
  `Class.forName("chat.jaspr.gear.GearApi", true, Bukkit.getPluginManager().getPlugin("JasprGear").getClass().getClassLoader())`):
  - `public static ItemStack rollLoot(java.util.Random random, int tier)` - tier 0 trivial rooms,
    1..5 structure difficulty (set pieces pass depth tier + 1). Uses only the passed Random
    (`nextDouble` then `nextInt`), so seeded rolls are deterministic. Chance per chest by tier
    0..5: 3%, 5%, 8%, 12%, 18%, 25%. Rank 1-2 anywhere, rank 3 needs tier 3+, rank 4 tier 4+,
    rank 5 only tier 5 (lowest weight). Returns null most of the time. Main thread, no player needed.
  - `public static ItemStack create(String gearId)`, `public static boolean isGear(ItemStack)`,
    `public static String gearId(ItemStack)`, `public static int rank(String gearId)`,
    `public static java.util.List<String> ids()`.

## Phase 2: Adrenaline, status effects, supplies (JasprGear 2.0.0, 2026-09-24)

Reference for behaviour: the Trinkets mod's potions and mana items (design only, no code copied).
Everything is server-side and real: no vanilla potion stands in for a status, and nothing glows
or gives night vision.

### Adrenaline (the mod's mana)

- Max **100**, +10 per Adrenaline Crystal (up to +100 = 200). New survivors start full.
- Refills **2/s**; doubled while Invigorated, halved while food is at 3 drumsticks or less.
  Adrenaline rush: real hits taken add 1 per damage point (max 5 per hit; bleeding does not count).
- Ability costs, paid only when the ability actually fires (after its cooldown check; Creative is
  free): **G Arc Shot 25, H Dodge 15, H Blink 30, sneak+H ender chest 10, sneak+J repel 20,
  J magnet on 5** (off is free). Too little: an action-bar line says how much is needed.
- Persisted per player in `plugins/JasprGear/players/<uuid>.vitals` (`jaspr-vitals 1`: adrenaline,
  crystals, remaining status time). It is a separate file so a Phase 1 jar (rollback) never sees
  unknown keys in `.gear`; unknown keys are ignored, an unreadable file is renamed
  `*.vitals.corrupt-<time>` and the player starts with defaults. Written on quit, shutdown,
  crystal use and at most once a minute while it changes.
- `/gear vitals` (aliases `effects`, `adrenaline`) prints adrenaline, regen, crystals, statuses and costs.
- Fixed on the way: shutdown no longer overwrites a `.gear` file that could not be read (Phase 1 saved
  every profile on disable, ignoring the "never overwrite" flag).

### Status effects

| Status (wire id) | What it does | Sources | Ends early |
| --- | --- | --- | --- |
| Bleeding (`bleed`) | 1 damage per second (players and mobs) | Razor Claws (20%, 4 s, as in Phase 1); hostile melee on a player (4%, 5 s, `status.mob-bleed-chance`) | Field Bandage, Full Restore, Regeneration (potion, golden apple, beacon), death |
| Ice Resistance (`ice`) | Slowness is stripped (JasprRPG Frost, strays, potions); stray arrows deal half | Full Restore (90 s); mushroom/rabbit/beetroot stew (60 s) | - |
| Invigorated (`vigor`) | +10% move speed (fixed-UUID attribute modifier, removed exactly), adrenaline regen x2 | Stim Reagent (45 s); waking with the Worn Teddy Bear (2 min) | - |
| Lightning Resistance (`volt`) | Lightning damage -80%, fire from it put out, shocks cannot paralyse | Full Restore (90 s) | - |
| Paralysis (`para`) | Cannot move (falling and looking still work), attack, shoot, prime (creepers) or use G/H/J; at most 3 s, then 3 s immunity | Lightning strikes (2 s); Arc Shot main bolt (30%: mobs 1.5 s, players 0.75 s) | Full Restore |

Mobs only take Bleeding and Paralysis (at most 256 tracked; paralysed mobs are held at the spot).
Player statuses pause while offline and are cleared on death. `gear effect <player>
<bleed|ice|vigor|volt|para|clear> [seconds]` (op/console) applies one for testing.

### Supplies (consumables)

Right-click to use (doors and chests still open; never works as a hoe; Creative keeps the item).
Same unbreakable stone-hoe carrier and damage-band textures as the trinkets (models 16-20), but
identity is a separate `JasprGearUse:{id,doses}` compound, so a supply is never gear and gear is
never a supply. Refused as crafting ingredients like gear.

| Supply (id) | Theme | Effect | Doses | Found |
| --- | --- | --- | --- | --- |
| Adrenaline Candy (`adrenaline_candy`) | blister of caffeine chews | +20 adrenaline (refused when full) | 3 | anywhere; recipe `SRS / .P.` (sugar, redstone, paper) |
| Field Bandage (`field_bandage`) | gauze pad | stops Bleeding, heals 1 heart | 2 | anywhere; recipe `PSP` (paper, string) |
| Stim Reagent (`stim_reagent`) | auto-injector | +50 adrenaline, Invigorated 45 s | 1 | loot tier 2+ |
| Full Restore (`full_restore`) | energy drink | full adrenaline, +3 hearts, cures Bleeding and Paralysis, Ice + Lightning Resistance 90 s | 1 | loot tier 3+ |
| Adrenaline Crystal (`adrenaline_crystal`) | crystal ampoule | +10 max adrenaline for good (10 at most) | 1 | loot tier 4+ (rarest) |

Divergence from the mod, on purpose: its Mana Reagent removes a crystal and poisons you and its
Restore resets your race; here the Stim Reagent and Full Restore are field medicine.

Loot: `GearApi.rollLoot` keeps the trinket band exactly (every seed that rolled a trinket still
rolls the same one - proven over 240,000 seeds against the Phase 1 algorithm). A roll that misses it
may land in a new supply band just above: 6, 8, 10, 12, 14, 16% per chest for tiers 0-5, rarer
supplies weighing more in harder tiers. Callers draw gear last, so the one extra `nextInt` on a
supply hit moves nothing that is placed. Also `GearApi.isConsumable`, `consumableIds`, and
`create(id)` accepts supply ids. Mob drops: 1% per hostile killed by a player (`drops.supply-chance`,
capped at 5%): candy, bandage or a stim. `/gear give <player> supplies|<id>`.

### HUD and wire

- A Phase 2 client says `hello 2`; the server then sends `{"v":1,"t":"hud","on":..,"a":72,"m":110,
  "fx":[["bleed",4]]}` whenever a shown number changes (at most about once a second, plus
  immediately on spend/use). Slot packets stay protocol 1, so Phase 1 clients (`hello 1`) never get
  HUD packets and Phase 1 servers simply never send them.
- The browser draws a 64 px bar with `ADR 72/110` above it and up to five status tags stacked above
  that, 31 px right of the hotbar - clear of the hotbar, a right-side offhand slot, the hotbar attack
  indicator, hearts/food and chat - and hidden while any screen (chat, inventory, menus) is open or
  when the screen is too narrow. Hook: `GuiIngame.renderGameOverlay` (Ewc state 190, after the
  potion icons) -> `JasprGearHud`, inside the same fenced `JASPR_GEAR_V1` block; the builder's
  reversal/parse proofs cover it. A HUD fault disables only the HUD, never the panel.
- Non-HUD clients get action-bar notices when a status starts or ends, and `/gear vitals`.
- Versions: `classes.js?v=20260924-gear2`, `jaspr-client.js?build=20260924-gear2`,
  `assets.epk?build=20260924-gear2`.

### Phase 2 verification (2026-09-24, cloud, Linux)

- Build: `bash scripts/build-gear-plugin.sh` (javac --release 8), `node scripts/build-gear-pack.cjs`
  (5874 unrelated EPK entries byte-identical, 26 bands, 264 selector states), `node
  scripts/build-gear-client.cjs` (reversal byte-for-byte, parses; re-running it on the patched
  `site/classes.js` reproduces it exactly; CR count unchanged).
- Paper 1.12.2 test server from `candidate/structure-audit/testserver-template`: loads with no errors,
  **GEAR_SELFTEST PASS, 612 checks** (Phase 1 checks plus supplies, dose handling, loot parity and
  supply gating/rates, adrenaline math, status table, offline parking, vitals file round trip /
  corrupt file / unknown keys, HUD packet). Also loads cleanly next to JasprHorrorBiomes 3.26.0.
- `tests/gear-phase2-bot.cjs` (mineflayer client vs the test server): **28/28** - hello 2 + HUD,
  equip, dodge cost, paralysis locks movement and abilities, bleeding damages, bandage cures and
  loses a dose, candy, stim + speed modifier, crystal (max 110), Full Restore, lightning resisted vs
  paralysing, hello-1 client gets no HUD; after a server restart the crystal and adrenaline persist (2/2).
- `tests/gear-hud-browser.cjs` (headless Chromium, real patched client in the client-side Testing
  Grounds world, loopback stubs only): HUD drawn every frame with no errors, low-bar colour, hidden
  while chat is open.
- Pre-existing, unrelated test failures (same on the base branch): `creative-catalogue.test.cjs`
  (stats hook anchor), `release-preservation.test.cjs` #3-4, `ping-overlay.test.cjs` #1.
- Not done in the cloud: the live server and the real EaglerXServer path (a local session should
  deploy the jar, `classes.js`, `assets.epk`, `client.html`, `jaspr-client.js` and play-test).

## How it works

- **Items**: an unbreakable, flag-hidden **stone hoe** whose damage value selects the texture
  (trinkets 1-15, slot icons 40-45) through `stone_hoe.json` damage-band overrides - the same
  technique as the apocalypse arsenal. Identity is only the NBT compound `JasprGear:{id}`;
  names/lore are display. Gear never works as a hoe, is unstackable and is refused as a crafting
  ingredient.
- **Server** (`server/custom-plugins/JasprGear`): authoritative slots per player in
  `plugins/JasprGear/players/<uuid>.gear` (Base64 SNBT per slot, temp file + fsync + atomic
  rename on an ordered writer thread; an unreadable file is renamed `*.corrupt-<time>`, never
  deleted, and never overwritten). Effects are recomputed on every change, join, respawn, world
  and game-mode change and every second.
- **Browser panel** (`client-mods/gear-teavm.js`, built into `site/classes.js` by
  `scripts/build-gear-client.cjs`): after joining, the client sends `hello 1` on the plugin
  channel **`jaspr:gear`**; the server answers with the seven slots (SNBT) and from then on the
  panel is drawn. A click sends `click <slot> <button> <shift>`; Paper applies it against its own
  cursor and slots and answers with the state plus the vanilla cursor packet. Keys send
  `key arc|dodge|magnet`. The server only talks to clients that said hello (20 messages/s cap),
  so older cached clients never receive anything and keep using /gear.
- **Assets** (`scripts/build-gear-pack.cjs`): 21 original 16x16 textures (15 trinkets,
  6 slot silhouettes), 22 models and the stone-hoe selector merged into `site/assets.epk`; every
  other EPK entry stays byte-identical, and all 132 stone-hoe damage states are proven to select
  the right model.
- **Command access**: `TestServerControl`'s public allowlist gained exactly `gear`, `trinkets`,
  `kit` and their `jasprgear:` forms (one class changed; SSO bridge and op rules untouched).

## Diagnostics

Console/log events: `GEAR_READY`, `GEAR_CLIENT_HELLO`, `GEAR_EQUIP`/`GEAR_UNEQUIP` (uuid, slot,
item), `GEAR_DEATH_DROP`, `GEAR_DEATH_RESTORED`, `GEAR_MOB_DROP`, `GEAR_XP_BANK`,
`GEAR_LAST_STAND`, `GEAR_SAVE_FAILED`, `GEAR_LOAD_FAILED`, `GEAR_NET_RATE_LIMIT`,
`GEAR_SELFTEST PASS|FAIL`, `GEAR_STOPPED`; Phase 2: `GEAR_CONSUME`, `GEAR_CRYSTAL`,
`GEAR_VITALS_LOAD_FAILED`, `GEAR_EFFECT` (admin), and `/gear status` adds adrenaline spent, statuses
applied/refused/cured, bleed ticks, locked moves/hits, supplies used, HUD packets sent. `/gear status` (op/console) prints counters (equips,
drops, arcs, blinks, absorbed hits, climbs, brakes, save failures). Console-only: `gear peek
<player|uuid>`, `gear open <player>`, `gear selftest`, `gear give <player|*> <id|all>` (also op).
Browser: `window.JasprGearDiagnostics.status()` (counters only, no identities). No passwords,
tokens or IPs are logged.

## Files

- Server: `server/custom-plugins/JasprGear/` (src, resources), build `scripts/build-gear-plugin.ps1`
  (Linux/cloud: `scripts/build-gear-plugin.sh`)
  (also writes `candidate/gear/gear-catalog.json` with the exact canonical SNBT via `GearExport`).
- Assets: `scripts/build-gear-pack.cjs` -> `candidate/gear/assets.epk`, `candidate/gear/pack/`.
- Client: `client-mods/gear-teavm.js`, `scripts/build-gear-client.cjs` (fenced
  `JASPR_GEAR_V1` and `JASPR_GEAR_CAT` blocks; strips and regenerates itself on a patched client;
  proves byte-for-byte reversal; parses the result).
- Tests: `scripts/gear-preview.cjs` (loopback Paper on 25597 + static page),
  `scripts/gear-cdp-probe.cjs` (disposable headless Chrome driver), `tests/gear-phase2-bot.cjs`
  (mineflayer end-to-end), `tests/gear-hud-browser.cjs` (HUD render in headless Chromium).

## Adding a trinket

1. Add an entry to `GearItem` (unique id, slot type, unused model 16-39, rank, lore, recipe).
2. Implement its mechanic in `GearAbilities` (and an attribute modifier in its constructor if any).
3. Draw 16x16 art in `ART` in `scripts/build-gear-pack.cjs`.
4. `scripts/build-gear-plugin.ps1`, `node scripts/build-gear-pack.cjs`,
   `node scripts/build-gear-client.cjs`; run the fixture self-test; deploy jar, `assets.epk`,
   `classes.js` and bump the `?v=`/`?build=` versions.

## Verification (2026-09-23)

- In-plugin self-test on real Paper classes: **GEAR_SELFTEST PASS, 468 checks, 0 failures**
  (identity/NBT round trips, slot typing, recipes, store round trip with a corrupt file, effect
  totals, menu, wire format, loot determinism, tier/rank gating and rates over 160,000 rolls,
  XP bank math).
- Headless-browser fixture against loopback Paper: panel drawn with icons and all textures;
  equip by cursor, shift-click equip/unequip, type refusal, right-click equip, tooltips, G/H/J
  keys reaching the server, /gear menu shift-equip, Creative search listing, death drop, and slots
  surviving a real server restart (`GEAR_PEEK ... source=disk`).
- Asset merge: 5874 unrelated EPK entries byte-identical, 264 selector states checked.
- Client build: reversal restores the unpatched `classes.js` byte for byte; the result parses.

## Remaining (not in Phase 1)

- Phase 3: the nine races as mutation serums/race baubles (abilities; size changes only if the
  client and server can agree safely).
- Gear column inside the Creative inventory screen, gear recipes in the EasierCrafting panel,
  visible worn-trinket models on players.
- Structure loot: the worldgen owners still need to call `GearApi.rollLoot` from their loot code.
