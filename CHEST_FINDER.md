# Chest Finder and chest search (2026-10-03, sandbox: not live yet)

Owner, 2026-10-03: "With the easier crafting UI, you can search any item. I think you should be able to right-click, and
then it comes up with a UI element that says "Find". If you click "Find", it should show particle effects on the chest that
has that particular item. Chests themselves should also have search boxes UI where you can search any item."

## What players get
- **Find (crafting panel).** Right-click any item in the EasierCrafting panel (the craftable list or the search results,
  in the inventory and at a crafting table) and a small menu opens: **Find in chests**, **Fill grid** (the panel's old
  right click, shown when the recipe can be made now), **Cancel**. Find closes the screen. Every nearby chest holding the
  item then gives off green sparks, around the chest and in a short column above it, for 15 seconds. Only the player who
  asked sees them. Chat says how many chests hold it and how far away and in which direction the nearest is
  ("12 m north-east, below"). On a phone, the Right: ON touch button makes a tap a right click.
- **Chest search box.** Chests, trapped chests, ender chests and shulker boxes have a search box in the title row. If a
  long chest name would run into it, the box moves above the window. Click it and type: every chest slot whose item
  name does not contain each typed word is dimmed, matches get a gold frame, and the box shows how many matched.
  A right click on the box clears it. Escape or Enter leaves the box, keeping the search.

## Rules (server, JasprFinder 1.0.0)
- **Which chests.** Only containers the player has opened themselves are searched (`search: opened`), plus their own
  ender chest: if the item is in it, every ender chest nearby lights up. Find can't show what someone else keeps in a
  chest the player never saw, and it can't be used to sniff out unopened dungeon loot chests. Chests count once they are
  opened after the update. `search: all` in `plugins/JasprFinder/config.yml` searches every chest, shulker box, hopper,
  dispenser and dropper in range.
- **What matches.** What the panel shows:
  - A JasperCraft item (unbreakable, model by damage: realm armoury, Survivor Gear, gadgets) matches only that exact item.
  - A vanilla tool or armour piece matches at any wear, but never a JasperCraft item made on the same base.
  - Anything else matches its exact damage value (wool colour, plank kind ...).
  - Shulker boxes inside chests are searched one level deep.
- **Bounds.**
  - One request per player every 2 s.
  - Radius 48 (config `radius`, 8 to 96), loaded chunks only.
  - At most 600 containers looked at and 16 highlighted per request.
  - One highlight per player at a time.
  - Each player remembers at most 4096 containers, most recent first, in `plugins/JasprFinder/players/<uuid>.txt`.
    Broken chests are forgotten when they are next searched.
- **Protocol.** The plugin channel `jaspr:find` carries `find <item id> <damage> <exact 0|1> <title>`
  (PacketBuffer string, at most 200 characters). It is not a command, so TestServerControl's command gate is not
  involved.
- **Logs.** `FINDER_READY`, `FINDER_FIND player=<uuid> item=<material:damage[:exact]> found= scanned= ms=`,
  `FINDER_METRICS` on disable, and `FINDER_LOAD_FAILED` / `FINDER_SAVE_FAILED`. No addresses, no chat text.

## Client (TeaVM)
- **The menu, the Find sender and the chest search** live in the EasierCrafting module
  `client-mods/recipe-book-teavm.js`. Install them with `node scripts/build-recipe-book-client.cjs --upgrade --source
  <live classes.js>`, which replaces only the module region.
  - The menu is drawn over the list's items with the depth test off, the same way vanilla draws its slot highlight
    (`C70` / `DVf`).
  - Find sends the request the way the gear module sends its own (`AKy`, `Iu`, `FuF`, `BgN`, `wd`). It then closes the
    screen the way Escape does (`Cpd`).
  - The chest search's clicks and keys come through the module's existing GuiContainer hooks.
- **Chest screen hooks.** `node scripts/build-chest-search-client.cjs --source candidate/recipe-book-client/classes.js`
  adds one guarded first state to GuiChest (`DUK`) and GuiShulkerBox (`D2Q`) foreground draws, marked
  `/*JASPR_CHESTSEARCH_V1*/`. The hooks are exact and reversible byte for byte.
- **Diagnostics.**
  - `JasprRecipeBookDiagnostics.status()` now reports `menu`, `menus` and `finds`.
  - `JasprChestSearchDiagnostics.status()` reports `open`, `search`, `found`, `scans`, `names`, `keys` and `clears`.

## Tests
- `tests/chest-finder.test.cjs` (set `CHEST_SEARCH_SOURCE` to a client with the module) covers:
  - the menu: entries, Fill = old right click, Cancel, Escape, staying on screen;
  - the Find text for every recipe;
  - the Click state machine sending exactly one `jaspr:find` packet and closing the screen;
  - the chest search: dims, frames, count, cached names, keys, right-click clear, layout above long titles;
  - the screen hooks;
  - the plugin wiring;
  - `tests/java/chat/jaspr/finder/FinderCheck.java`: parsing, matching incl. shulker boxes, decode, directions, and
    every request the panel can send, run against the real item registry.
- The existing `tests/recipe-book-engine.test.cjs`, `tests/gun-recipe-blueprints.test.cjs` and
  `tests/armor-bar-client.test.cjs` still pass.

## Deploying (later: another agent was working on live JasperCraft, so this was built in a sandbox)
1. Plugin: `server/plugins/JasprFinder.jar`. It is a new plugin, so a restart is needed (when the server is empty).
2. Client: rebuild on the live `site/classes.js` at deploy time, module upgrade first, then the hooks. Bump the
   `classes.js?v=` key in `site/client.html`.
