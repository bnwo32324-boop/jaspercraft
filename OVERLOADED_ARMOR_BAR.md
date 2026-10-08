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

## "It is not working" (owner, 2026-10-05)

The report came with the gear in a screenshot: an Emerald Helmet, a diamond chestplate (named, Thorns III), diamond leggings and
diamond boots, and a bar of ten full white icons. That gear is **exactly 20 armour** (3 + 8 + 6 + 3), which is one full white row
by the mod's own rule ("exactly full stays white"); the bar wraps only from 21. What does and does not count:

- **Armour points only.** Enchantments (Thorns, Protection ...) do not add armour points (they act on damage separately), and
  neither do toughness or knockback resistance, which are what make the realm armoury pieces "stronger than diamond"
  (`ArmoryPiece`: at the time helmet 3, chestplate 9, leggings 7, boots 3 = 22 for a whole set against diamond's 3/8/6/3 = 20,
  toughness 3 each against diamond's 2). **Changed the same day (owner: "Emerald should be better than Diamond. That includes
  the helmet"):** every piece is now one point above diamond, 4/9/7/4 = 24 a set, and old pieces were upgraded in place
  (`REALM_ARMORY.md`); the reported gear (an Emerald Helmet with diamond chestplate, leggings and boots) is now 21 armour: a
  half orange icon over a full white row.
- At the time a single Emerald Helmet was worth what a diamond helmet is, which is why that gear came to exactly 20. The orange
  now appears with any one realm piece over diamond's own 20 (each is +1), or a Survivor Gear ring that carries armour while
  equipped (Vigil Ring +1, Dominion Signet +2, `GearAbilities`): a whole realm set (24) shows two orange icons over a white row;
  the 1.12 cap of 30 shows five orange over five white.

The port was only ever tested against stand-ins, so it was checked in the real client: `tests/armor-bar-browser.cjs` runs the
live `classes.js` and `assets.epk` in headless Chrome against the loopback fixture (`scripts/tank-preview.cjs`, a lean Paper
server): the player wears diamond pieces with explicit `generic.armor` modifiers so the server computes 0, 7, 20, 22, 23 and 30
armour (and 22 with Regeneration, the other entry into the armour loop), and the ten icons of the armour row are classified by
colour from screenshots. All 12 checks pass: no row at 0, three white and a half and empty outlines at 7, ten white at 20, one
orange at 22, orange plus an orange/white half icon at 23, five orange at 30, no page or server exceptions.

    TANK_PREVIEW_ROOT=<checkout with candidate/tanks and candidate/tank-client> ARMOR_BAR_CLASSES=<live classes.js> ARMOR_BAR_ASSETS=<live assets.epk> node tests/armor-bar-browser.cjs [out-dir]

## Armaments count (2026-10-07)

Owner, with a screenshot of two identical Emerald Boots, one an Ancient armament (Level 1, "+40% protection") and one plain: "Both of
them have the same armor stats in the overloaded armor bar. This needs to be corrected because the one with 40% more protection needs
to see and update the armor bar in accordance with that."

The bar used to draw the client's armour attribute only, and an armament's protection is not an attribute: it is a rarity bonus that
`ArmamentListener.onHurt` applies to the damage of every worn, enhanced piece (`damage x (1 - min(0.55, bonus x 0.55))`), and that the
lore states as "+N% protection" (Uncommon 5, Rare 11, Ultra Rare 18, Legendary 28, Ancient 40). Nothing told the client.

**Now:** the bar draws the armour attribute **plus the armour points the worn armaments add**. Every worn, enhanced piece of armour
counts **its own armour once more by its rarity's bonus**, and the sum is rounded to whole points once:

| Worn | Armour of the piece | Bonus | Points added |
| --- | --- | --- | --- |
| Ancient Emerald Boots | 4 | 40% | 1.6 |
| Legendary Emerald Chestplate | 9 | 28% | 2.52 |
| Rare Emerald Leggings | 7 | 11% | 0.77 |
| Uncommon Emerald Helmet | 4 | 5% | 0.2 |
| **all four** | | | 5.09, shown as **5** |

So 22 armour with the plain boots is a white row and one orange icon, and the same boots as an Ancient armament make it 24: two
orange icons. A piece with no armament, or a rarity without a bonus (Default, Basic), adds nothing; a piece's armour is read the way
the game reads it (the item's own modifiers for that slot, or the vanilla ones when it has none), so a modifier that belongs to
another slot adds nothing, exactly as it gives no real armour.

**Display only.** How much damage a piece turns aside is unchanged (and is not the same arithmetic: Ancient turns aside 22% of a
blow, whatever the armour bar says). Only the bar moved.

How it travels: `RpgApi.armamentArmor(Player)` (JasprRPG 1.3.3, read-only) -> the Field Journal (JasprJournal 1.0.2) sends it in its
once-a-second change check as `ab` (left out when 0, bounded to 200) -> the Journal's client module keeps it (`armorBonus()`, 0 once the
data is a minute old or there is none) -> `JasprArmorBar.frame()` adds it to the attribute it draws. An older server sends no `ab` and
the bar is what it was; a client without the Journal stage ignores the bar's extra. A change shows within about a second of putting a
piece on or taking it off.

Tests: `armor-bar-client.test.cjs` (the module with and without a Journal, garbage, the cache), `journal-client.test.cjs` (parse,
bounds, freshness), `journal-server.test.cjs` (the door is the same test the damage handler applies, read-only, wire format),
`JournalRulesTest` (payload), the real-Paper `journal-smoke.cjs` (`armament-armour-for-the-armour-bar`: the real RPG jar, Ancient
boots 1.6 -> 2, four rarities 5.09 -> 5, vanilla armour, another slot's modifier, a sword, the 200 cap, nothing written) and, in the
real client against the real server, `journal-browser.cjs`: leather pieces with 14 + 4 = 18 armour draw nine white icons and an empty
outline, the same boots as an Ancient armament draw ten, taking the armament off draws nine again.
