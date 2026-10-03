# Overloaded Armor Bar (client, 2026-10-03)

Owner, 2026-10-03: "I want this mod, overloaded armor bar, to be implemented in the game. You can do a direct port, or you
can recreate it yourself" (Overloaded Armor Bar 1.0.4g for 1.12.2, MIT, LocusWay and Tfarcenim).

- What it does: armour above 20 no longer vanishes off the end of the HUD bar. The bar wraps, and every further 20
  points is drawn over the previous row in the next colour: white, orange, gold, cyan, green, purple (the last repeats).
  A half icon shows the new colour on its left half and the previous row's colour on its right half, as in the mod.
  In 1.12 armour is capped at 30, so in practice the second (orange) row is what appears: a full realm armour set (22)
  shows one orange icon over a full white row.
- Ported, not loaded: this client is a TeaVM build, so the mod's renderer (ArmorBar.calculateArmorIcons and
  OverlayEventHandler.renderArmorBar) is ported to the client stage `scripts/build-armor-bar-client.cjs`. It hooks
  GuiIngame.renderPlayerStats: both entries into vanilla's armour loop jump to one new state that draws the ported bar.
- Kept from vanilla instead of the mod's defaults: the empty armour outlines below 20 (the mod's "Show empty armor icons"
  is off by default), and the bar's height (vanilla's, which allows for extra rows of hearts; the mod squeezes health to
  one row for health-bar mods this client does not have). The mod's Lava Waders charm overlay is for another mod and was
  left out.
- Licence: client-mods/armor-bar/THIRD_PARTY_NOTICES.txt; the shipped code names it too.
- Test: `tests/armor-bar-client.test.cjs` (stage exact, reversible, stable; every icon for 0, 7, 20, 22, 23, 30, 40,
  41 ... 300 armour against the mod's rules; colours from the mod's own config).
- Client only: no plugin or restart. Cache key `classes.js?v=20261003-armorbar1`.
