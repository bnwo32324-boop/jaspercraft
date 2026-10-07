# The JasperCraft look of the inventory (2026-10-07)

Owner, 2026-10-07, about the Field Journal: "aesthetically, it looks really ugly ... make the whole inventory fit the theme of Jasper
Craft. I just want a thematic, superficial change. Don't change any of the logic."

**Colours only.** No data, hook, layout rule, click or server code changed (checked mechanically: the three client modules at the
previous commit and now are identical once colour literals, colour names and comments are neutralised; the only other differences are
three new colour names and which name three draw calls use).

## The palette (from the game's own art)

`scripts/jasper-theme.cjs` holds it; everything below is generated from or tested against it.

| Part | Colour | Where it comes from |
| --- | --- | --- |
| Window body | `#12454F` teal | the cat favicon's background |
| Frame highlight (top/left, 2 px) | `#F0B552` amber | torchlight on the banner |
| Frame shade (bottom/right, 2 px) | `#7A4E1C` bronze | the same, in shadow |
| Frame outline | `#03171B` | the banner's night side |
| Slot: dark edge / inside / light edge | `#06222A` / `#0B2F37` / `#3C8791` | sockets cut into the teal |
| Title text ("Crafting", creative tab names) | `#F4E6BC` cream | |
| Player recess | night sky with stars, a dusk glow and a mountain ridge, dithered | the banner's sky |

## What changed on screen

* **Survival inventory** (`container/inventory.png`): recoloured pixel for pixel, same geometry and slots; the crafting arrow is amber.
* **Widened part** (`client-mods/wide-inventory-teavm.js`): its frame and extra slots use the same palette, so the 90 px extension
  continues the window without a seam.
* **Field Journal panel** (`client-mods/journal-teavm.js`): a night-teal screen set in like a slot, cream text, amber selected-tab
  underline, bronze separators, a brass "Open Stats" button, a lime experience bar; red / gold / green / aqua keep their meanings in
  warmer tones. Every text colour is tested for contrast against what it is drawn on.
* **Gear column** (`client-mods/gear-teavm.js`): same frame and slots.
* **Creative inventory** (`tab_inventory.png`, `tab_items.png`, `tab_item_search.png`, `tabs.png`): recoloured the same way (the "destroy
  item" slot is a muted red); the tab sprites get the amber/bronze edges.
* **Title text**: the two window titles (`E3x` "Crafting", `Gzj` creative tab name) were dark grey for the light body; they are cream now.

**Not changed:** chests, crafting table, furnace and the other windows (they keep their grey look and dark titles), the Easier
Crafting search box and recipe panel (dark grey, drawn by `recipe-book-teavm.js`), tooltips, the HUD. Say the word and the same
palette can go on those too.

## How it is built

| What | Where |
| --- | --- |
| Palette and the texture recolouring (colour classes by position: frame vs slot highlight, recess, arrow) | `scripts/jasper-theme.cjs` |
| EPK: replaces the five textures, every other entry byte-identical, idempotent | `scripts/build-theme-pack.cjs` |
| Client text: two title colours (marker `JTHEME`) and the gear column's colour block | `scripts/build-theme-client.cjs` |
| Everything from the LIVE files (wide + journal stages rebuilt, theme stage, EPK, cache keys, manifest) | `scripts/assemble-theme-client.cjs --game <live checkout> --out <folder> --key <key>` |
| Tests | `tests/theme-client.test.cjs` (palette agreement between textures and modules, contrast, every shape and every pixel outside the windows kept, 46 slot cells, stage reversible and stacking with the other stages); `tests/wide-inventory-client.test.cjs` and `tests/journal-client.test.cjs` pin the new colours; `tests/journal-browser.cjs` checks the real client's pixels |

The gear module is built from a catalogue file the gear builder keeps outside the repository, so its colours are swapped inside the
live client as one exact text block (reversible; a gear stage rebuilt from the changed module already carries the new block).

Cache keys: client `20261007-theme1`. Preview without a browser (real module code, the game's own font sheet): `node scripts/theme-preview.cjs out.png jasper <tab 0..2>`.
