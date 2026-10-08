# The JasperCraft look of the inventory and every container window (2026-10-07)

Owner, 2026-10-07, about the Field Journal: "aesthetically, it looks really ugly ... make the whole inventory fit the theme of Jasper
Craft. I just want a thematic, superficial change. Don't change any of the logic."
Then, with a screenshot of a crafting table whose widened part was teal next to a grey window: "The UI is fucked up in furnaces,
crafting tables, etc., and chests, etc." The first version recoloured the survival and creative inventories only, while the widened
part draws in the same colours in every window that has it; this version recolours every container window.

**Colours only.** No data, hook, layout rule, click or server code changed (checked mechanically: the client modules at the first theme
commit and now are identical once colour literals, colour names and comments are neutralised; the only other differences are three
new colour names and which name three draw calls use).

## The palette (from the game's own art)

`scripts/jasper-theme.cjs` holds it; everything below is generated from or tested against it.

| Part | Colour | Where it comes from |
| --- | --- | --- |
| Window body | `#12454F` teal | the cat favicon's background |
| Frame highlight (top/left, 2 px) | `#F0B552` amber | torchlight on the banner |
| Frame shade (bottom/right, 2 px) | `#7A4E1C` bronze | the same, in shadow |
| Frame outline | `#03171B` | the banner's night side |
| Slot: dark edge / inside / light edge | `#06222A` / `#0B2F37` / `#3C8791` | sockets cut into the teal |
| Title text ("Crafting", "Inventory", the chest's name ...) | `#F4E6BC` cream | |
| Recess behind the player and the horse | night sky with stars, a dusk glow and a mountain ridge, dithered | the banner's sky |

## What changed on screen

* **Every container window**, recoloured pixel for pixel (same geometry, same slots, same sprites): survival inventory, creative
  inventory (three windows and the tab sprites), chest and large chest (so the stat sheet and every plugin menu), shulker box,
  dispenser / dropper, hopper, crafting table, furnace, brewing stand, enchanting table, anvil, beacon, horse, villager trading.
  Unlit arrows, flames and pipes are amber or bronze, lit progress sprites are cream (a furnace's arrow, a brewing stand's bubbles),
  the beacon's dark panel is night-teal with a bronze border, its chosen button is brass. The enchanting table's and the anvil's
  parchment fields keep their own tan (they are paper), as does the villager's trade preview bubble.
* **Window titles** (the dark grey 4210752 literal in the thirteen title-drawing functions) are cream. The advancements screen is not a
  container window and keeps its look.
* **Widened part** (`client-mods/wide-inventory-teavm.js`): frame and extra slots in the same palette, so the 90 px extension
  continues every window without a seam.
* **Field Journal panel** (`client-mods/journal-teavm.js`): a night-teal screen set in like a slot, cream text, amber selected-tab
  underline, bronze separators, a brass "Open Stats" button, a lime experience bar; red / gold / green / aqua keep their meanings in
  warmer tones. Every text colour is tested for contrast against what it is drawn on.
* **Gear column** (`client-mods/gear-teavm.js`): same frame and slots.
* **Easier Crafting search box, Chest Finder box and Sort button** (`client-mods/recipe-book-teavm.js`): near-black and grey became
  night-teal with a teal border (cream when focused) and a brass button.
* The recipe-book toggle buttons (the green book) sit on the teal body now.

**Not changed:** tooltips, the HUD, the advancements screen, the Easier Crafting recipe popups (tooltip-style purple), the main menu.

## How it is built

| What | Where |
| --- | --- |
| Palette and the texture recolouring (colour classes by position: frame vs slot highlight, slot boxes found by their dark edge, recesses, glyphs, sprite rectangles) | `scripts/jasper-theme.cjs` |
| EPK: replaces the seventeen textures, every other entry byte-identical, idempotent (a texture that has the theme only gets its still-vanilla sprites recoloured) | `scripts/build-theme-pack.cjs` |
| Client text: the thirteen title literals (marker `JTHEME`) and exact text blocks inside the gear and recipe-book modules | `scripts/build-theme-client.cjs` |
| Everything from the LIVE files (wide + journal stages rebuilt, theme stage, EPK, cache keys, manifest) | `scripts/assemble-theme-client.cjs --game <live checkout> --out <folder> --key <key>` |
| Reviewing without a browser | `scripts/theme-contact-sheet.cjs <folder>` (every texture, vanilla beside themed) and `scripts/theme-preview.cjs out.png jasper <tab>` (the widened inventory with the real modules and the game's font) |
| Tests | `tests/theme-client.test.cjs` (palette agreement between textures and modules, contrast, every shape and every pixel outside the windows kept, 46 slot cells, container drawings, the stage reversible and stacking with the other stages, only the advancements title left dark); `tests/wide-inventory-client.test.cjs`, `tests/journal-client.test.cjs` and `tests/chest-finder.test.cjs` pin the new colours; `tests/journal-browser.cjs` checks the real client's pixels and the stat sheet window |

The gear and recipe-book modules are swapped as exact text blocks inside the live client instead of rebuilding their stages (the gear
builder needs a catalogue file kept outside the repository; the recipe-book stage sits under the wide-inventory stage): strip() puts the
old blocks back, and a stage rebuilt from the changed module already carries the new ones.

Cache keys: `20261007-theme1` (survival and creative inventories, journal, gear column, widened part), `20261007-theme2` (every container
window, titles, search boxes).
