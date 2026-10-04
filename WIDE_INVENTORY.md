# Wide inventory (1.5x)

Owner, 2026-10-03: "The player's inventory, by default, should be 1.5x as big. This goes for all players, past,
present, and future. This change should affect the hotbar as well."

## What players get

- Every inventory row is 14 slots instead of 9 (1.5 x 9 = 13.5, rounded up):
  - the **hotbar has 14 slots**;
  - the three main rows have 14 each;
  - that makes **56 slots instead of 36**.
- Existing players keep every item where it was. The 20 new slots start empty.
- **The 5 extra hotbar slots** are drawn on the HUD and can be selected:
  - by mouse wheel;
  - by the phone touch hotbar, which now has 14 buttons (5 extra once the server agrees);
  - by clicking in the inventory.
  Number keys 1-9 still pick slots 1-9.
- **The new slots in windows** sit in a *pocket* beside every window that shows your inventory (inventory, chests,
  furnaces, crafting tables, Creative and so on). Each row of the pocket is level with the row it extends, and the
  window and pocket are centred together.
- **Pickups** fill the 14-slot hotbar first, then each row left to right.
- **Shift-click** moves between the rows and the whole 14-slot hotbar.
- **Plugins:** Survivor Gear, the EasierCrafting panel, chest search, Apocalypse guns and Dungeon guns/relic pouch all use
  the extra slots.

## How it works

- **Server.** The installed Paper jar is re-patched by `scripts/build-wide-inventory-server.cjs`. The ASM patcher is in
  `scripts/java/wide-inventory/patcher` and the rules are in `.../src/.../JasprWide.java`.
  - `PlayerInventory.items` holds 56 stacks. Items 0-35 keep their vanilla meaning.
  - Items 36-40 are hotbar slots 10-14. Items 41-55 extend rows 1-3.
  - Armour sits at flat index 56-59 and the off hand at 60, so Bukkit's `getHelmet()`/`getArmorContents()`/
    `getStorageContents()` stay correct.
- **Handshake.** The JasperCraft client says `wide1` on plugin channel `jaspr:inv` when it joins, and the server answers.
  Only then does that connection's inventory window get the 20 extra slots (66 window slots, chest 83, ...).
  - Any other client (an old cached page, Eaglercraft 1.5/1.8 through EaglerXRewind) keeps vanilla 36-slot windows.
  - Its pickups also stay in the 36 slots it can see.
- **Client.** The client stage `scripts/build-wide-inventory-client.cjs` (marker `JASPR_WIDEINV`) uses the module
  `client-mods/wide-inventory-teavm.js`.
  - It hooks the TeaVM InventoryPlayer, ContainerPlayer, GuiContainer, GuiIngame hotbar, Creative and the handshake.
  - It is fenced and reversible (`--unpatch`).
  - It is the outermost stage around the gear/recipe modules: unpatch it before rebuilding those.
- **Rollback without a jar swap:** create `server/jaspr-wide.disabled`. The server then ignores new hellos, so every
  connection is vanilla on the wire, and items in the extra slots stay saved.

## Logs

- Server: `JASPR_WIDE_ENABLED player=<name> again=<bool> window=66 extraStacks=<n> held=<i>`, plus
  `JASPR_WIDE_DISABLED` / `JASPR_WIDE_UNKNOWN_HELLO` / `JASPR_WIDE_BAD_HELLO`.
- Client: `jaspercraft.wideinv.state` / `jaspercraft.wideinv.error` events go to `/api/diagnostics/events`, at most 8
  per page. `JasprWideBridge.slots()` reports 9 or 14.

## Tests

- `tests/wide-inventory-client.test.cjs`: offline. Module logic, the builder on the live client, and the touch hotbar.
- `tests/wide-inventory-bot.cjs`: 41 checks on one low-priority Paper test server with two bots (vanilla and wide).
  Covers the wire, fill order, held slot, Bukkit API slot numbers, click events, shift-click, Creative, a chest window
  and save/rejoin.
- `tests/wide-inventory-browser.cjs [--mobile]`: the real TeaVM client in one headless Chrome against the loopback
  fixture. 19 checks on desktop and 22 on mobile: HUD, pocket clicks, shift-click, chest, Creative and the touch hotbar.
