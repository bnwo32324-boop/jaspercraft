# Progression update: more stats, the bounty board, turret upgrades, 32 trinkets (2026-10-02)

Owner, 2026-10-02: "More stats and ranks ... Add many more at your discretion", "Bounty board ... This should also be
accessible through a command. Reminders should be posted in the chat on occasions", "Turret upgrade tree ... it should
also change the look of the turret itself. The model should change with each upgrade. It should also change what sound
effects it makes. Also, make the baseline initial turret, unupgraded, nerfed a little bit, and make it a little bit more
expensive to craft", "Add twice the amount of trinkets and baubles (make sure some of them are exclusive to certain
dimensions)".

## Stats (JasprRPG 1.2.0) - press K or /stats
- **45 stats** (was 23), in five tabs along the bottom of the sheet: Combat, Defense, Survival, Gathering, Movement.
- **15 ranks** (was 10; Treasure Finder 5, was 3). Ranks 11-15 are *mastery ranks*: each is worth half an ordinary rank and
  costs a flat 50 levels (ranks 1-10 keep the GokiStats prices: 6, 8, 11 ... 48). Dying still resets every stat.
- New stats:
  - Combat: **Precision** (critical-hit chance, up to 25%, +50% damage), **Ferocity** (criticals hit harder),
    **Bloodthirst** (heal from melee damage), **Axemanship**, **Gunslinger** (firearms), **Slayer** (bosses and elites).
  - Defense: **Evasion** (dodge a blow or shot), **Fire Ward**, **Blast Ward**, **Antidote** (poison, wither, magic),
    **Steadfast** (knockback resistance), **Second Wind** (a burst of regeneration when a blow leaves you below 30%).
  - Survival: **Recovery** (natural healing), **Endurance** (slower hunger), **Luck** (fishing and chest loot),
    **Scholar** (more experience from everything), **Scavenger** (extra monster drops), **Engineering** (your sentries hit
    harder).
  - Gathering: **Angler** (double catches), **Green Thumb** (extra produce), **Lumberjack** (extra logs).
  - Movement: **Fleet Foot** (speed).
- The older stats keep working through the mastery ranks (Mining reaches Haste VII, High Leaper Jump Boost V, ...).
  Blocks you placed yourself never pay gathering bonuses. Logs: `RPG_STAT_UP`, `RPG_METRICS`.

## The bounty board (JasprBounties 1.0.0, new plugin) - /bounty (/bounties, /contracts)
- Every player has **three dailies** (easy, medium, hard) and **two weeklies**, rolled for them each day (midnight) and
  week (Monday). 51 kinds of bounty: slay monsters, mine ores, chop, harvest, fish, smelt, cook, breed, tame, enchant,
  travel; realm bounties (the Nether, Drownhollow, Atlas, the Backrooms) appear once you have been there, and the
  weeklies include a Nether Lord, the Ghast Queen, a Drownhollow Warden, an Atlas lord, a Backrooms keeper, the Wither and invaders.
- **Rewards:** experience (150 / 350 / 700 / 2000 points: it pays for stats) plus materials; hard bounties have a 10% and
  weeklies a 50% chance of a trinket. Claiming all three dailies in a day grows a **streak** (+100 XP per day of it, up to
  7; a trinket every seventh day). Finished but unclaimed bounties are paid automatically at the reset
  (or when you next join).
- **Board:** `/bounty` opens it (or any sign with `[Bounties]` on its first line: anyone can make one). `/bounty list`,
  `/bounty claim`, `/bounty reroll <1-3>` (swap one daily a day), `/bounty top` (this week's hunters),
  `/bounty remind on|off`.
- **Reminders in chat:** a minute after joining, about every 30-35 minutes of play, when a bounty is half done (action
  bar) and finished, and an hour before the dailies reset. Weekly completions are announced to everyone. Lines are
  clickable (open the board).
- Credit: your own kills, kills by your sentries, and everyone within 64 blocks for a boss. Placed blocks and silk-touch
  mining never count. Owner tools: `/bounty admin <player> info|reset|complete <1-5>|realm <name>`.
- Logs: `BOUNTIES_READY`, `BOUNTY_ROLL`, `BOUNTY_DONE`, `BOUNTY_CLAIM`, `BOUNTY_REROLL`, `BOUNTY_STREAK`, `BOUNTY_REALM`,
  `BOUNTIES_METRICS`. Data: `plugins/JasprBounties/players/<uuid>.yml`, `board.yml`.

## Sentry turret upgrades (JasprApocalypse 3.7.0)
- **Tree:** Sentry (Mk I) -> Reinforced (Mk II) -> **Gatling**, **Cannon** or **Tesla** (Mk III) -> Storm Gatling,
  Siege Howitzer or Arc Tower (Mk IV). Right-click a sentry, then the anvil; the owner pays from their inventory
  (Reinforced: 4 iron blocks, 16 redstone, 4 gold; Mk III: 3 diamonds, metal or redstone blocks and parts: hoppers, TNT
  and obsidian, or ender pearls; Mk IV: 6 diamonds, blocks and Nether materials: blaze rods, magma cream, or quartz and
  glowstone).
- **Each tier has its own model** (8 cuboid models, iron pickaxe damage 100-107: `apocalypse-pack/sentry-upgrades.cjs`)
  and **its own sounds** when firing and when locking on (dispenser clicks, rotary snares, cannon blasts and
  explosions, lightning cracks and chimes), plus its own particles.
- **Abilities:** Gatling fires every quarter second and ignores a target's recovery frames; Cannon shells splash 60% onto
  everything within 3 blocks (Howitzer: 4 blocks and knockback); Tesla bolts chain to 2 more foes (Arc Tower: 4, and
  slow them). Mk IV and the cannons reach further.
- **Plain sentry nerfed:** 13 / 10 / 7 damage (slow / normal / fast), was 16 / 12 / 8. **Dearer:** the cheap recipe
  (4 iron ingots around an iron block) is gone; the one recipe is the blueprint: seven iron ingots, an iron block and
  redstone.
- A collected sentry keeps its upgrade. Shots are credited to the owner (bounties), and grow with the owner's
  **Engineering** stat and **Engineer's Toolbelt** trinket. turrets.yml is no longer rewritten on every shot.
- Logs: `SENTRY_UPGRADE`, `SENTRY_PLACE/PICKUP/BREAK/BLAST ... tier=`, `/apocalypse status` counts upgrades, splash and
  chain hits.

## Trinkets (JasprGear 4.0.0): 16 -> 32
- **Eight new craftable ones** (same end-game recipe rule: a Nether Star and 2+ diamond blocks), also in loot:
  Fletcher's Quiver (belt: arrows return, fly faster), Sharpshooter's Monocle (head: +30% ranged damage beyond 16 m),
  Trench Coat (body: +2 hearts, -20% from shots), Lucky Coin (charm: +2 luck, extra drops), Medic's Armband (stronger
  healing, heals hurt allies nearby), Engineer's Toolbelt (belt: your sentries +20% damage and +4 range), Hunter's
  Trophy Necklace (neck: +12% damage per recent kill, three stacks), Vigil Ring (ring: chimes at creepers and invaders,
  +1 armor).
- **Eight found only in one realm** (no recipe, never in other loot): dropped by that realm's creatures (0.4%), elites (3%)
  and bosses (35%):
  - the Nether: Magma Heart Pendant (lava -75%, burning ends, melee sets foes ablaze), Soulfire Ring (+30% vs Nether
    creatures, -20% from them);
  - Drownhollow: Choir's Tidepearl (breathe and swim underwater, horrors -20%), Eye of the Deep (+30% vs horrors and
    Wardens, no Dread nausea);
  - Atlas: Dominion Signet (+30% vs the Dominion and Atlas lords, +2 armor), Titan's Girdle (+3 hearts, +50% knockback
    resistance, melee staggers);
  - the Backrooms: Flask of Almond Water (heals below half health, Backrooms entities -20%), Shard of the Exit Sign
    (+10% speed; in the Backrooms, [H] noclips through up to 3 blocks of wall).
- New 16x16 art for all 16 (`scripts/build-gear-pack.cjs`), Creative catalogue and recipe-book entries.
- Logs: `GEAR_REALM_DROP`, `GEAR_EXTRAS_METRICS`; `GEAR_READY ... realmItems=8`.

## Tests (offline, no game client or test server)
`node --test` on: `tests/rpg-stats.test.cjs`, `tests/bounties.test.cjs`, `tests/sentry-upgrades.test.cjs`,
`tests/sentry-model.test.cjs`, `tests/apocalypse-assets.test.cjs`, `tests/gear-trinkets.test.cjs`,
`tests/recipe-book-engine.test.cjs` (each compiles its plugin and runs an offline check where it can).
