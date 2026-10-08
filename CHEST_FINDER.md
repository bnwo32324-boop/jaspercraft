# Chest Finder, chest search and Sort (live since 2026-10-03; Find on inventory items 2026-10-08)

Owner, 2026-10-03: "With the easier crafting UI, you can search any item. I think you should be able to right-click, and
then it comes up with a UI element that says "Find". If you click "Find", it should show particle effects on the chest that
has that particular item. Chests themselves should also have search boxes UI where you can search any item." Then:
"There also should be a sorting button for chests that auto-organizes everything."

Owner, 2026-10-08: "There is a find feature in the easier crafting menu where you could right-click and find items. I want
this also to be a feature with the inventory, where you can right-click any item in your inventory and click Find. The UI
should look the same, and it should have the same functionality of identifying the items in nearby chests." And: "It should
be Shift + right-click, since right-click already has a function in the inventory to halve item stack."

## What players get
- **Find (inventory items).** Hold **Shift** and right-click any item in the inventory screen or at a crafting table (the
  hotbar, the pockets of the wide inventory, the armour slots, the off hand, the crafting grid; not the crafting output).
  The same small menu opens, titled with the item's name: **Find in chests** and **Cancel**. Find does exactly what Find
  in the panel does: the screen closes, every nearby chest holding that item gives off green sparks for 15 seconds, and chat
  says how many and where the nearest is. A plain right click is untouched (it still picks up half a stack), and so are
  Shift + left click (quick move) and Shift + right click on the crafting output. While the menu is open the game's item
  tooltip is hidden; Escape, Cancel, or a click elsewhere closes it. On a phone: turn **Right: ON** and **Shift: ON**, then
  tap the item. Chest, furnace and other windows have no Find menu.
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
- **Sort button.** Beside the search box. One click sorts the whole container:
  - Partial stacks of the same item are merged.
  - Items are ordered the way the creative screen groups them: building blocks, decorations, redstone, transportation,
    miscellaneous (in 1.12 that includes ingots, diamonds and other materials), food, tools, combat, brewing.
  - Within a group, items go by kind. Vanilla comes before a JasperCraft item made on the same base, the freshest tool
    first, then by name.
  - Empty slots go to the end. The chest stays open, and it works on double chests too.

## Rules (server, JasprFinder 1.1.0)
- **Find on a slot.** The client sends `find slot <window id> <slot> <title>` on `jaspr:find` (the window id is the open
  window's `gui.h2.iu`, 0 for the inventory screen). The server reads that slot of the window the player has open right
  now (`activeContainer`), builds the request from its own copy of the stack (`FindRequest.of`), and searches exactly as for
  the panel. The client never names the item, so it cannot ask about something it does not hold.
  - **Same match as the panel.** An unbreakable item (a JasperCraft model item) matches that exact item and damage; a
    vanilla tool or armour piece matches at any wear, never a JasperCraft item on the same base; anything else matches its
    damage value. The title is what the player's client calls the item (colour codes and control characters removed, at most
    48 characters), else the item's own name, else the material.
  - **Refused, with a reason.** A window id that is not the open window (`stale`), a slot past the window's last (`range`) or
    an empty slot (`empty`) find nothing; `stale` and `empty` say "Find: that item is no longer there." on the action bar.
    Window ids and slots are bounded to 0-255. The 2-second limit per player is shared with the panel's Find.
- **Sort.** The plugin channel `jaspr:sort` carries `sort <window id>`. The server sorts the container the player has
  open, and only when that window is still the one open, so a late click never sorts the next screen.
  - **What can be sorted.** A chest, trapped chest, double chest, chest minecart, shulker box, or the player's own ender
    chest. Never a plugin's chest menu (bounty board, gear menus): those get "Only chests and shulker boxes can be
    sorted." on the action bar.
  - **Safety.** Stacks are only merged, never split; a stack above its normal size stays whole. The result is checked to
    hold exactly the same items before it is written, and otherwise the chest is left alone (`FINDER_SORT_FAILED`).
  - **Rate limit.** At most two sorts a second per player.
  - **Logs.** `FINDER_SORT player= container= slots= stacks=<before>-><after> ms=` and `FINDER_SORT_REFUSED`.
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
- **Protocol.** The plugin channel `jaspr:find` carries `find <item id> <damage> <exact 0|1> <title>` (the panel) or
  `find slot <window id> <slot> <title>` (an inventory item), as a PacketBuffer string of at most 200 characters. It is not
  a command, so TestServerControl's command gate is not involved. A 1.0.0 server ignores the second form.
- **Logs.** `FINDER_READY ... slotFind=true`, `FINDER_FIND player=<uuid> item=<material:damage[:exact]> via=<panel|slot>
  found= scanned= ms=`, `FINDER_FIND_REFUSED player=<uuid> via=slot reason=<stale|empty|range> window= slot=`,
  `FINDER_METRICS` on disable (with `slotRequests`, `slotStale`, `slotEmpty`), and `FINDER_LOAD_FAILED` /
  `FINDER_SAVE_FAILED`. No addresses, no chat text.

## Client (TeaVM)
- **The menu, the Find sender and the chest search** live in the EasierCrafting module
  `client-mods/recipe-book-teavm.js`. Install them with `node scripts/build-recipe-book-client.cjs --upgrade --source
  <live classes.js>`, which replaces only the module region.
  - The menu is drawn over the list's items with the depth test off, the same way vanilla draws its slot highlight
    (`C70` / `DVf`).
  - Find sends the request the way the gear module sends its own (`AKy`, `Iu`, `FuF`, `BgN`, `wd`). It then closes the
    screen the way Escape does (`Cpd`).
  - The chest search's clicks and keys come through the module's existing GuiContainer hooks.
  - **Find on an item.** The same click hook (`Gmh` -> `JasprRecipeBookClick`) looks first for Shift + right click (Shift is
    read like the original's shift-click: the game's own `Jz(42)` / `Jz(54)`, which also sees the phone's Shift toggle, with
    the page's key events as a fallback). The slot under the pointer is worked out from the slots' own positions
    (`Lr`, `Fg`, a 16 px square a pixel wider on every side), because a touch moves the pointer and clicks at once. Every slot
    of the window counts except the crafting output. The click state machine reads the slot (`eew`), asks the game for the
    item's name (`EJv`, the title) and opens the panel's own menu (`RB.openSlotMenu`: Find in chests, Cancel, kept on screen).
    An empty slot, a plain right click and Shift + left click are left to the screen. While the menu is open the foreground
    hook clears the screen's hovered slot (`a_b`), so no item tooltip is drawn over (or behind) the menu and the number and
    drop keys do nothing to the slot underneath.
  - Find on an item sends `find slot <gui.h2.iu> <slot> <title>` through the same sender and closes the screen like the panel's
    Find.
  - The Sort button sends `sort <gui.h2.iu>` (the window id, as handleMouseClick uses it) through the same sender, at
    most once every 0.6 s, and leaves the screen open. The server's slot updates redraw the chest.
- **Chest screen hooks.** `node scripts/build-chest-search-client.cjs --source candidate/recipe-book-client/classes.js`
  adds one guarded first state to GuiChest (`DUK`) and GuiShulkerBox (`D2Q`) foreground draws, marked
  `/*JASPR_CHESTSEARCH_V1*/`. The hooks are exact and reversible byte for byte.
- **Diagnostics.**
  - `JasprRecipeBookDiagnostics.status()` now reports `menu`, `menus`, `slotMenus`, `finds`, `slotFinds` and `sorts`.
  - `JasprChestSearchDiagnostics.status()` reports `open`, `search`, `found`, `scans`, `names`, `keys`, `clears` and `sorts`.

## Tests
- `tests/chest-finder.test.cjs` (set `CHEST_SEARCH_SOURCE` to a client with the module) covers:
  - the menu: entries, Fill = old right click, Cancel, Escape, staying on screen;
  - the Find text for every recipe;
  - the Click state machine sending exactly one `jaspr:find` packet and closing the screen;
  - Find on an inventory item: Shift + right click opens the menu (armour, off hand, wide-inventory slots included) titled
    with the item's name, drawn in the panel menu's colours with the tooltip hidden; a plain right click, Shift + left
    click, an empty slot, the crafting output, a panel that is not ready and an off-slot click stay the screen's; Find sends
    `find slot <window> <slot> <name>` once and closes the screen; Cancel, Escape and a click elsewhere close the menu; kept
    on a small screen;
  - the chest search: dims, frames, count, cached names, keys, right-click clear, layout above long titles;
  - the Sort button: layout, hover, one `jaspr:sort` packet with the window id, screen stays open, rate limit, no sort on
    a right click or without a window;
  - the screen hooks;
  - the plugin wiring;
  - `tests/java/chat/jaspr/finder/FinderCheck.java`: parsing, matching incl. shulker boxes, decode, directions, and
    every request the panel can send, run against the real item registry. It also checks the sorter: the creative tab of
    each group, a mixed chest's exact sorted order, merging, an over-full stack left whole, a full chest of mixed wool, and
    named items never merged. For Find on a slot: the request built from a real stack (worn tool, JasperCraft model item,
    wool colour, named item, empty), every shape of `find slot` message the client can send (and malformed ones), and the
    lookup in a real NMS container: the right slot, an empty slot, the wrong window, no window, past the last slot.
- `tests/find-slot-browser.cjs` (needs Chrome, the wide-inventory Paper jar and the candidate client from
  `scripts/assemble-find-slot-client.cjs`) runs the real client against a real Paper with JasprFinder 1.1.0 and checks, with
  real mouse and key events, everything above end to end: the plain right click still halves a stack, Shift + right click opens
  the menu in the panel menu's colours without moving anything, Find finds the chest (`FINDER_FIND ... via=slot found=1`),
  a miss finds none, an armour slot works, an empty slot opens nothing, Cancel / Escape keep the inventory open, the phone's
  synthetic Shift key opens the menu, an item removed before Find arrives is refused (`FINDER_FIND_REFUSED reason=empty`),
  and `FINDER_METRICS` counts it.
- The existing `tests/recipe-book-engine.test.cjs`, `tests/gun-recipe-blueprints.test.cjs` and
  `tests/armor-bar-client.test.cjs` still pass.

## Deploying
- 2026-10-03: the plugin and the client went live together (a new plugin, so a restart).
- 2026-10-08 (Find on inventory items, JasprFinder 1.1.0):
  1. Plugin first: `node scripts/patch-plugin-jars.cjs --game <live checkout> --out candidate/jars-find JasprFinder` rebuilds the
     live jar with only the changed classes (`FinderPlugin`, `FinderPlugin$SlotLookup`, `FindRequest`) and `plugin.yml`. A
     plugin install restarts the server once, when it is empty. A 1.0.0 server ignores `find slot ...`, so the order matters
     only for the player's experience: with the old plugin the menu would open but Find would find nothing.
  2. Client: `node scripts/assemble-find-slot-client.cjs --game <live checkout> --out candidate/find-slot-deploy --key <key>`
     upgrades the module region of the live `site/classes.js` (the stages are checked to rebuild the result unchanged) and
     bumps the `classes.js?v=` key in `site/client.html`. No assets change.
