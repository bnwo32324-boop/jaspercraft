# Trinket icons, silent effects and turret placement (2026-10-05)

Three owner requests, one change set.

## 1. Sentry turrets can be mounted anywhere with two blocks of headroom

> "A sentry turret should be able to be placed down anywhere as long as it doesn't clip through the ceiling, so about two
> blocks ... Get rid of the logic that says they need space horizontally."

`SentryTurret.roomFor` (JasprApocalypse 3.7.2) now refuses a mount for three reasons only: the target block is not free
(air, liquid, grass, a dead bush or a single snow layer are free), a solid block sits within two blocks above it (the head
needs `PLACE_HEADROOM = 2`), or a player's body is really inside the block. The old check refused the mount whenever a player
stood within about 1.3 blocks of it, which made every spot beside you unusable; `SentryPlacement.overlaps` is the exact
body-against-block test (0.6 x 1.8 box) and is unit tested (`tests/sentry-placement.test.cjs`).

## 2. Item effects are silent: nothing lists them in the inventory

The Pearl of the Drowned keeps Water Breathing on its bearer, refreshed every second as a 70-tick effect. The HUD already hid
it (the effect is ambient with no particles) but the inventory screens list every effect, so a "Water Breathing 0:03" box sat
over the Easier Crafting search bar for good.

* **Client** (`scripts/build-silent-effects-client.cjs`, marker `/*JF*/`): `InventoryEffectRenderer` skips an effect that is
  ambient **and** particle-less, and only moves the window aside for effects it will list. Potions are not ambient and beacons
  show particles, so only effects that servers mark this way disappear. The effects still work; only the box is gone.
  Tests: `tests/silent-effects-client.test.cjs` (anchors, reversal, the rewritten function run against mocks) and the real
  browser check `tests/silent-effects-browser.cjs` (box region unchanged by a silent effect, drawn for a potion, window not
  moved, no exceptions).
* **Servers**: every effect an item, trinket, bauble, armament or stat keeps or grants on its bearer is
  `new PotionEffect(type, ticks, amplifier, true, false)`. The audit found the convention already used by Drownhollow relics,
  Nether trinkets, Survivor gear, the Backrooms' "carried" effects and the RPG stat effects; this change adds the dungeon
  baubles and Reliquary warding, RPG armament "adrenaline", the Nether Ghastly Pendant and a Nether weapon proc, and the
  Backrooms' use-effects (canteen, coolant vial, cold coffee, dead man's watch).
* A visible potion effect still shows its box over the search bar while it lasts (vanilla behaviour, not item-driven).

## 3. Every trinket, bauble and seal has its own texture

111 original 16x16 icons (`scripts/trinket-art/`, a small deterministic pixel-art engine: shapes, rim shading, outline):
72 Dungeon baubles + the Reliquary pouch, 8 Drownhollow relics + 5 Seals, 10 Nether trinkets, 15 Backrooms trinkets.
Later the same day the big structures update (`BIG_STRUCTURES_UPDATE.md`) added two carried relics of the colossi, the
Heart of the Sporefather and the Oracle's Prism (Nether bands 31-32): 113 icons, archive cache key `20261005-colossi1`.

* **Carrier technique** (as the Survivor gear's stone hoe, the sentry's iron pickaxe and the armoury's diamond tools): the
  item is an unbreakable stone tool whose damage value (its *band*) picks the model. `stone_sword` carries the Dungeon
  (bands 1-73), `stone_shovel` the rest (Drownhollow 1-15, Nether 21-32, Backrooms 41-55). Stone tools cannot be smelted or
  burned. A custom empty attribute list replaces the tool's attack modifiers, and durability, attributes and the unbreakable
  line are hidden, so a trinket is never a weapon or a tool; right-clicking a block with one makes no path.
* **Single source of truth**: `scripts/trinket-art/catalog.cjs` (art, carrier, band). Each plugin has the same generated
  `Skin.java` (from `scripts/trinket-art/Skin.java.template`, `node scripts/trinket-art/gen-skin.cjs`) and its band numbers;
  `tests/trinket-art.test.cjs` keeps them identical and fails if a trinket definition is left on a vanilla stand-in.
* **Items made earlier are upgraded in place** (same item, same data, new look) on join, when a container is opened and after
  a pickup; logged as `DUNGEON_RELIC_ICONS`, `RUINS_RELIC_ICONS`, `NETHER_TRINKET_ICONS`, `BACKROOMS_TRINKET_ICONS`
  (counts only). The old stand-in items are still recognised until upgraded. Dungeon pouches keep their uuid and sockets.
* **Exception**: the Faceless Mask and the Crown of the Drowned Star are worn in the helmet slot, so they stay the wither
  skull and the diamond helmet they are.
* **Client files**: `scripts/build-trinket-pack.cjs` merges the pack into the live `assets.epk` (only `stone_sword.json` and
  `stone_shovel.json` change besides the new entries; selector proof over every damage state). The Creative catalogue's
  Dungeon entries are re-skinned by `scripts/update-dungeon-creative-icons.cjs` (a rebased copy of the dungeon sandbox's
  fenced catalogue builder, `scripts/dungeon-creative-client.cjs`). `scripts/assemble-trinket-client.cjs` builds everything
  from the live client files; cache key `20261005-trinkets1`.
* **Real-server check**: `tests/trinket-skins-runtime.cjs` loads the four plugins' item classes on a real Paper server (never
  enabling the plugins) through `tests/java/chat/jaspr/trinketprobe/TrinketProbe.java`: every trinket is skinned, recognised
  and upgraded, the pouch keeps its identity, the worn two stay vanilla (2,564 checks).

## Rebuild order and ownership

Live EPK -> `build-trinket-pack.cjs` (nobody else owns `stone_sword.json` / `stone_shovel.json`; the armoury pack must still be
built last for the diamond models). Live classes.js -> silent-effects stage -> Dungeon creative entries (data only; it is the
outermost stage: unpatch it, `node scripts/build-silent-effects-client.cjs --unpatch`, before rebuilding another stage).
Plugin jars: `node scripts/patch-plugin-jars.cjs --game <live checkout> --out <folder>` replaces only the classes that differ.

New trinket: draw it in the plugin's art file, give it a band in `catalog.cjs`, add `Skin` use and the band in the plugin,
run the tests.
