# Dual wielding fixes (combat rework, workstream A) -- 2026-10-11

The owner: dual wielding "broke on a programmatic level. There are some bugs, and it's finicky. Make sure it's in working
order before you apply the animations."

Plugin `JasprBetterCombat` **1.5.6-port.5** (2026-10-14: **1.5.6-port.6**, fix S8) (`server/plugins/JasprBetterCombat.jar`) and Better Combat client head
**bettercombat9** (`client-mods/bettercombat/bettercombat-head.js`, built into the LIVE `classes.js` by
`scripts/build-bettercombat-client.cjs`, which replaces only the head region). Better Combat Rebirth 1.5.6's combat
rules and the settings panel are kept; where the port had drifted from 1.5.6, it is back on 1.5.6.

## What changes for players
- An off-hand click on a mob or player attacks, also when the ground or a wall is under the crosshair behind the target.
- Right after a hotbar switch or F, both hands attack with what is in them now (no dead click, no vanilla fallback).
- Right click with a sword in the off hand never steals what the main hand can do: eating, blocking, bows, pearls,
  rods, buckets, block placing, doors, chests, villager trades, horses, armor stands, your own pets, name tags, leads,
  and the plugins' item abilities (staffs, wands, trinkets, the codex ...) all work as without Better Combat. When the
  main hand has nothing to do, the off hand attacks.
- One off-hand attack per click, also on phones: holding Use (or the right button) no longer spams weak attacks.
- A server lag spike no longer switches dual wielding off mid-fight.
- Recharge bars react at the click and follow the item in hand; a refunded miss gives the main hand's energy back.
- Shield blocks count as blocks, punching with an empty hand no longer throws an error, and an off-hand hit too soon on
  the same target sounds "no damage" (as in 1.5.6) instead of a silent swing.

## Bugs found and fixed

Each line: symptom; cause; fix; the test that proves it (before = the head live today, `bettercombat-live-head.js`).

### Server (plugin)
| # | Symptom | Cause | Fix | Test |
| --- | --- | --- | --- | --- |
| S1 | `NullPointerException ... because "this.item" is null` at `strike(BetterCombatPlugin.java:274)` on every bare-hand hit (10 times in the live logs, Oct 3-9); the rest of the hit (damage stat, damage particles, exhaustion, the state sync) was skipped | `ItemStack.hitEntity` called on `ItemStack.EMPTY` (no Item); 1.5.6 checks `isEmpty` first | wear the weapon only when there is one | server S5, S26 |
| S2 | A hit on a raised shield played hit sounds, knocked back, wore the weapon and counted damage | CraftBukkit's `damageEntity` returns true for a fully blocked hit (vanilla: false) | the shield block is read from the strike's own damage event (BLOCKING modifier) and treated as "no damage", verdict `blocked` | server S17, S18 |
| S3 | An off-hand hit inside the target's 10-tick off-hand timer was a silent dead swing | the port returned before anything; 1.5.6 spends the off-hand energy and plays "no damage" | the early return is gone: 1.5.6's rule | server S4 |
| S4 | Refused attacks were dropped silently (same-tick repeat, wrong item, villager target, disabled ...): the client could not tell | no answer existed | every attack message with a sequence number gets a verdict: `hit` / `blocked` / `miss` / `denied` + reason + "energy spent" | server S2-S24 |
| S5 | (with the new right click, see C5) an off-hand attack on top of a plugin's main-hand item ability, or on a player a plugin was handling (reviving, boarding a tank) | -- | refused (`denied used` / `denied interact`) in the same tick as a main-hand air click a plugin claimed (`useItemInHand=DENY`), or a cancelled right click on that player | server S13-S16 |
| S6 | After a plugin reload, connected clients kept sending attacks into nothing | no notice on disable | `onDisable` sends `enabled:false` | server S25 |
| S7 | A settings-panel edit did not reach the clients' item / entity lists | -- | the static `cfg` is re-sent to connected clients on every change / reload | server S19 |
| S8 | (2026-10-14, **1.5.6-port.6**) An axe and a sword in the two hands sometimes both showed as swords (first person, hotbar, F5) until the next main-hand blow | an off-hand blow swaps the hands on the server for a moment (so enchantments and plugins see the attacking weapon as the main hand, as in 1.5.6); JasprRPG rewrites the "main hand" in that moment (armament XP on every blow and kill), CraftBukkit sends that slot to the client at once, and the swap back is invisible to the inventory's change tracking | after every off-hand blow both hand slots are sent as they really are and the container's last-sent copies follow; covers every plugin that writes a hand (or calls updateInventory) during the blow | server S27, S28 (fixture `rewrite` = a plugin that rewrites the main hand on every blow) |

### Client (head)
| # | Symptom | Cause | Fix | Test |
| --- | --- | --- | --- | --- |
| C1 | Off-hand right click at a target with the ground or a wall under the crosshair did nothing (the main hand did hit it) | the off hand checked "crosshair on a block" BEFORE the combat ray and handed the click to block use | the combat ray first; block use only when it finds nothing | client "BUG: an off-hand click ...", regression B1 |
| C2 | For a round trip after a hotbar switch or F: off-hand clicks dead, main-hand clicks vanilla (3-block reach) | the client compared the held items with the server's last-seen `mainItem` / `offItem` | local check with the server's off-hand weapon id list + the local special-item guard; the held slot is synced to the server before the attack (as vanilla's `attackEntity`), the server validates in order | client "BUG: right after a hotbar switch ...", regression B4 |
| C3 | Dual wielding switched off whenever the server was silent for 3 s (lag), and never came back after a plugin reload | `active()` required a packet within 3 s; the hello was only sent 3 times per connection | 15 s window, a new hello after 5 s of silence, plus the disable notice (S6) | client "BUG: a long server silence ...", regression B5 |
| C4 | Right click on a villager / horse / armor stand / own pet with a sword in the off hand: a dead off-hand swing, no trade / ride / interaction | the client claimed the click; the server refuses those targets | the server sends the blacklist as network entity type ids; with the own-pet check the click stays vanilla (as 1.5.6's `shouldAttack`) | client "BUG: villagers ...", regression B2, native test |
| C5 | Food / bow / shield / potion in the main hand blocked off-hand attacks even when unusable (full hunger, no arrows); every other right-click use (pearls, rods, buckets, leads, name tags, breeding, abilities) was stolen by the off-hand attack | a fixed "main item has a use duration" flag; the click was otherwise taken before vanilla | right click = vanilla's main-hand step (the engine's own `rightClickMouse` code, per hand), then the off-hand attack, then vanilla's off-hand step | use-equivalence test (612 scenarios vs the live `F$w`), client tests, regression B6 |
| C6 | Holding right click / the phone's Use button: an off-hand attack every 4 ticks (weak hits, energy never full; a phone tap held over 200 ms gave a second weak hit) | `rightClickMouse` repeats while the use key is held | only a press attacks (1.5.6's Forge `MouseEvent` fired once per press; the main hand works the same); repeats stay vanilla | client "BUG: holding the use button ...", regression B3 |
| C7 | Spectator mode: left click on an entity did not spectate it | the click was consumed and the server ignores spectators | spectators keep vanilla clicks | regression B7 |
| C8 | Two clicks of one hand in one tick: two messages, the server dropped the second silently | -- | one attack per hand and tick on the client too (the second is fed `denied`) | regression B8, server S9 |
| C9 | The main-hand bar showed empty after a predicted hit the server did not land (or full after an unforeseen hit) | the client reset its cooldown on its own prediction only | the verdict carries "energy spent": the client gives the energy back or spends it | client "BUG: the main-hand energy ..." |
| C10 | The off-hand bar appeared a round trip late and followed the server's last-seen item | bar from server packets only | predicted at the click from the weapon's period; the off-hand item is checked every tick | client "the off-hand recharge bar ...", "the off-hand bar follows ..." |

Kept on purpose: left click still attacks blacklisted entities (1.5.6), mining keeps vanilla, the swing fields are set
directly for hits (1.5.6's `EventHandlersClient`), guns / gadgets / portal devices / gear keep all their clicks.

## Combat feed (for the animation workstreams)
Global in the head (`classes.js` module scope), append-only:
```
JasprCombatFeed = { seq: <last event number>, events: <last <= 64 events, oldest first>, since(seq) -> events with e.seq > seq }
event = { seq, at: Date.now(), hand: 'M'|'O', kind, item: <item id of that hand, -1 empty>, target: <entity id or -1>,
          crit, sweep, fatal, pair: { main: <item id, -1 empty>, off: <item id, -1 empty> },
          ref: <seq of the 'swing' a verdict answers, else -1>, tick: <player tick count> }   // ref, tick: additions
kind  'swing'   the client sent an attack for that hand (target = the entity under the combat ray, or -1)
      'hit' | 'blocked' | 'miss'   the server's verdict on that swing (crit / sweep / fatal from the server; a 'miss'
                with a target = it touched the target but did no damage, e.g. the off-hand timer)
      'denied'  a click consumed without an attack: on the client (recharging with "require full energy", a second
                click of one hand in one tick; ref -1) or refused by the server after a 'swing' (ref = that swing)
```
`JasprBetterCombat.markSwing(off[, id])`, `motion(main)`, `settings()`, `active()`, `blocks()`, `empty()`,
`JasprCombatSpecial` keep their meaning. New: `JasprBetterCombat.offReady()` (the off hand holds a weapon now),
`remaining()` (off-hand recharge in ticks, predicted at the click). Diagnostics:
`window.JasprBetterCombatDiagnostics.status()` (counters, last 8 events), `jaspercraft.bettercombat.state/error`
events (at most 8 per page, counters and state names only).

## Protocol on `jaspr:combat` (backward compatible both ways)
- client -> server: `hello 1`, `config open`, `attack M|O` (old) and `attack M|O <seq>` (protocol 2; seq 1..2^31-1).
- server -> client: the state JSON gains `"proto":2`; on each hello and settings change
  `{"v":1,"cfg":{"proto":2,"offIds":[...],"noAttack":[...entity type ids],"players":false}}`; per `attack ... <seq>`
  `{"v":1,"res":{"s":seq,"h":"M|O","k":"hit|blocked|miss|denied","t":entityId|-1,"i":itemId,"c":0|1,"w":0|1,"f":0|1,"e":0|1,"r":"reason"}}`.
- Old clients ignore `cfg` / `res` (no `range`) and never send sequence numbers; the new client sends the old message
  to an old server and keeps the old item gating there. Deploy order does not matter.

## Build and tests
```
javac -J-XX:ActiveProcessorCount=1 --release 8 -encoding UTF-8 -cp server/cache/patched_1.12.2.jar -d <out> $(find server/custom-plugins/JasprBetterCombat/src -name '*.java')
jar --create --file server/plugins/JasprBetterCombat.jar -C <out> . -C server/custom-plugins/JasprBetterCombat/resources .   # + THIRD_PARTY_NOTICES.txt
node scripts/build-bettercombat-client.cjs [classes.js] [--check]   # LIVE -> candidate/bettercombat/classes.js + manifest.json
node --test --test-concurrency=1 tests/bettercombat-client.test.cjs      # one file at a time
node --test --test-concurrency=1 tests/bettercombat-regression.test.cjs
node --test --test-concurrency=1 tests/bettercombat-use.test.cjs
node --test --test-concurrency=1 tests/bettercombat-build.test.cjs
node --test --test-concurrency=1 tests/bettercombat-native.test.cjs
node tests/bettercombat-server.cjs <JasprBetterCombat.jar> <scratch dir>   # one Paper test server, port 25686, one core
```
The builder audits every engine name and mapping it relies on (swing fields, cooldown field, held-item sync, spectator
game type, entity classes, the `rightClickMouse` text its per-hand copy reproduces), proves that only the head region
changed, parses the result and is idempotent; a changed mapping stops it.

## Not covered / for the other workstreams
- Third person: Mo' Bends plays every swing with the primary arm and the main item's attack (Mo' Bends 1.2.2), so an
  off-hand swing reads as a main-hand swing in third person. Not changed here (Mo' Bends is outside workstream A).
- Workstream B: `tests/melee-native.test.cjs` expects `build:'bettercombat8'`; after this head it is `bettercombat9`.
  B's confirm() should treat `denied` with a `ref` like `miss` (no impact). `JasprEpicPose` uses the server's `off` flag;
  `JasprBetterCombat.offReady()` follows the item in hand.
- Plugin NPCs that are not players and not on the blacklist (Atlas talkers: invulnerable zombies) still get a visible
  off-hand swing on right click (no damage, their talk still opens). The Dungeon Abyssal Sceptre's ability does not
  claim the click (it listens at MONITOR), so an off-hand attack can follow it.
- Not verified in a real browser here (offline engine-equivalence, native-class and protocol-bot tests instead); the
  lead's lean browser check should try a left/right/alternating attack and a villager right click.
