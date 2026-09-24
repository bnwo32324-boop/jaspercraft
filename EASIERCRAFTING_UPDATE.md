# EasierCrafting panel: every recipe, vanilla and JasperCraft (2026-09-24)

The recipe panel left of the inventory / crafting table (port of Giselbaer's EasierCrafting 1.12) now
works like the original mod: it lists **every recipe you can craft right now** from the 36 inventory
slots that fits the open grid (2x2 inventory or 3x3 table), grouped by creative tab and sorted by name.

- **Click** a recipe: loads the grid and crafts one. **Shift-click**: as many as the inventory allows.
  **Right-click**: only loads the grid. Search (2+ letters) covers all recipes; ones you cannot make yet
  are dimmed and say what is missing ("missing ingredients" / "needs a crafting table"). Hover shows the
  ingredients under the window, cycling through alternatives (coal/charcoal, any planks, any wool...).
- **Source of truth**: `client-mods/recipe-table.json`, an export of every crafting recipe the real
  server registers - 433 vanilla, 91 JasprApocalypse (89 blueprints, Sentry Turret, Field Guide), 23
  JasprGear (trinkets, supplies, backpacks) - by a build-time plugin (`scripts/java/recipe-export`,
  never deployed) on a test server with every live plugin. Ten special recipes with no fixed ingredients
  (dyeing, fireworks, map/book cloning, banners, shields, repair, tipped arrows) are skipped, as in the
  original.
- **What was wrong before**: vanilla recipes were not listed at all; the custom table was hand-synced
  and had drifted (Sentry Turret shown with the wrong recipe; Field Guide missing); 3x3 recipes were
  offered in the 2x2 grid and filled the wrong cells; an ingredient split over several stacks made the
  click do nothing; no shift-crafting; blueprint crafts silently failed because the server refuses
  shift-crafting them and the panel shift-clicked.
- **How a craft is sent**: fill clicks first (whole stacks, halves, single items, each cell from one
  exact item so mixed planks never swap on the cursor, cursor empty at the end); the result is taken
  only once the server shows it in the output slot (the client cannot predict it, and an early click
  would make the server ignore the rest). Blueprints: one plain click into a free slot.
- Stacks with NBT (named/custom items) are never spent as ingredients, as in the original.

## When recipes change (a plugin adds or changes one)
```
bash scripts/export-recipes.sh <test-server-dir>     # test server with every live Jaspr jar
node scripts/sync-recipe-table.cjs                   # -> JasprRecipeTable in client-mods/recipe-book-teavm.js
node scripts/build-recipe-book-client.cjs --upgrade  # swaps the module inside site/classes.js
GEAR_CLIENT_SOURCE=candidate/recipe-book-client/classes.js node scripts/build-gear-client.cjs
cp candidate/gear-client/classes.js site/classes.js  # and bump classes.js?v= in site/client.html
```
`node scripts/sync-recipe-table.cjs --check` fails when the module is out of date.

## Tests (2026-09-24, cloud)
- `node --test tests/recipe-book-engine.test.cjs` 8/8: table completeness, 2x2 vs 3x3, coal/charcoal,
  split stacks, tagged stacks refused, every click script replayed on a vanilla slot model.
- `tests/recipe-book-bot.cjs` 21/21 on a server with every live plugin, sending the panel's own clicks:
  shift-crafts 7 logs into 28 planks, charcoal torches, mixed-plank chest, split-stack pickaxe, Leather
  Satchel, Sentry Turret and Military Salvage (x4) with one plain click.
- `tests/recipe-book-browser.cjs` 12/12 in the real client (headless Chromium, singleplayer): panel on
  the 2x2 inventory lists planks/torches/lever and hides 3x3-only recipes; hover; shift-click turns 7
  logs into 28 planks; the list updates. `tests/gear-hud-browser.cjs` still passes.
- Version: `classes.js?v=20260924-gear6`. Server plugins unchanged by this.
