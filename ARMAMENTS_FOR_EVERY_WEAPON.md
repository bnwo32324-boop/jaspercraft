# Every weapon is an armament (2026-10-08, JasprRPG 1.3.4)

Owner, with a screenshot of a Vermilion diamond sword from the Dungeon Dimension: "I'm not getting points to upgrade this weapon in the
advanced armaments system for some reason, yet it works on other weapons, particularly vanilla weapons. Fix this globally." Then: "I
think 100% of weapons should have armament capabilities."

## Why that sword never levelled

An item earns armament experience (and with it the tokens the armament sheet spends) only once it *is* an armament: it carries a
`JasprArmament` record with a level and a rarity. Until now an item got that record in only two ways:

- a **35% roll** when it was crafted, picked up from the ground, taken from an output slot or pulled from the Creative menu
  (`armaments.enhance-chance: 0.35`; the 65% that failed stayed ordinary for good);
- realm armoury pieces, always, also when a chest holding them was opened.

Loot taken out of a chest (the Dungeon Dimension's included), an item handed over by a plugin or a command, or anything that missed
its 35% never became an armament, so it never levelled. Vanilla weapons seemed to work because many of them are crafted and some of
those won the roll.

## Now

**Every eligible item is an armament**: every sword, axe, bow, piece of armour, gun and realm tool (`armaments.tools: realm`, as
before). Only the **rarity** is still rolled, with the same weights as before (half Basic, which carries no bonus of its own; realm
pieces never below Uncommon). An item becomes an armament whichever way it turns up:

| When | What |
| --- | --- |
| crafted, picked up, taken from an output slot, pulled from Creative | as before, but always |
| a chest, a dungeon's loot or any other container is opened | everything eligible in it (before: realm pieces only) |
| it lands a blow (weapons, guns) or takes one (armour) | on the spot; that blow already counts |
| it is held in the main hand or worn | within a second (only while no container window is open over it) |
| its armament sheet is opened | at once, so the sheet shows level 1 and the experience to the next |

So the owner's sword becomes an armament the moment it is held, and levels from its first blow. Items that are armaments already are
never re-rolled. Only `armaments.armor: false` (off by default) keeps armour ordinary; the sheet then says so.

**The item's own lines stay.** An armament's block used to replace the lore of any item that was not a gun or a realm piece, which
would have erased "Recovered from the Vermilion Court." and "The Dungeon Dimension | Threat 4". Now an item's own lines stay on top and
the armament block goes under the `- Armament -` header beneath them, through every level-up and ability. A plain weapon enhanced
before keeps exactly the lore it had (its block starts at the rarity line, with no header); if another plugin later rewrites an item's
lore without the block, its lines are kept and the block comes back under the header.

`armaments.enhance-chance` and `armaments.enhance-chance-creative` are no longer read (an old config that has them is harmless). The
count of items made armaments is in `RPG_METRICS ... armamentsMade=N` when the plugin stops.

## Tests

- `tests/realm-armory-armaments.test.cjs` runs `ArmamentToolCheck` against the real CraftBukkit item code: nine kinds of weapon and
  armour are always armaments, rarity is still rolled (Basic most often, Ancient seen), dirt and a plain pickaxe stay as they are, an
  armament keeps its level and tokens, the Vermilion sword's own lines stay above one header through four level-ups and an ability, a
  plain weapon enhanced before keeps its shape, a lore rewritten by another plugin keeps its lines, `armaments.armor: false` keeps only
  armour ordinary; plus a source contract that every acquisition path and every use makes armaments and no roll chance is left.
- The real-Paper `journal-smoke.cjs` loads the new RPG jar (1.3.4) with the Journal, Apocalypse, Disasters, Invasions and Gear jars:
  478 assertions pass.
