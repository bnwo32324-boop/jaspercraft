# Gun animations (combat rework workstream C, 2026-10-11)

> "Also give gun animations when they shoot, such as recoil, etc."
> "Try to make the combat for melee weapons and gunplay extremely satisfying when it comes to the animations."

A new outermost client stage, `JASPR_GUNFX` (`client-mods/gunfx/`, builder `scripts/build-gunfx-client.cjs`), animates every
gun a player can hold in first person: a punchy recoil kick that springs back, recoil that builds up in sustained fire and
settles, the gun's own action cycling (slide, hammer, pump, bolt, energy vent, rotary shiver), reloads, dry fire, Portal Gun
shots, dungeon caster casts, an additive muzzle flash for a frame or two, brass and shells flying out, a smoke wisp, and a
camera impulse through workstream B's `JasprCameraFX.kick` when that API is present.

**Visual only.** Nothing here changes damage, fire rate, ammunition, reloads or packets. Nothing is predicted: every
animation follows something the server already did.

## What starts an animation

| Event | How the client knows (exact, from the server's own data) |
| --- | --- |
| Shot | The held gun's NBT `rounds` drops for the same serial (`JasprApocalypse.serial`) or uuid (`JasprPenitentArmory.uuid`) in the same hotbar slot. One drop = one shot (Apocalypse sends one update per round, bursts included; a merged update of n rounds plays n kicks 2 ticks apart). Dungeon guns pay a whole burst up front (rounds drop by `burst`), so the later rounds of a Belfry Repeater / Labyrinth Double kick 11 ticks apart, exactly the server's `Pending` schedule, and are dropped when the player switches away (as the server drops them). |
| Reload start | Dungeon: `reloadAt` set on the same gun. Apocalypse guns keep no reload flag, so the stage notes the server's own reload sound: `block.iron_trapdoor.open` at pitch 0.8 where this player stands (`Arsenal.reload`; vanilla trapdoors play at 0.9-1.0). |
| Reload end | `rounds` rises on the same gun. A reload whose start was never seen still gets the closing flourish. |
| Dry fire | A use click was processed this tick (`rightClickDelayTimer` was just set to 4, no screen open) while the gun holds fewer rounds than a trigger costs and no reload runs; the Portal Gun's "No surface in range." too. A twitch, never a shot. |
| Portal Gun shot | Its own action-bar line ("Blue portal set." / "Orange portal set."): the flash and the dust take that portal's colour. |
| Cast (dungeon casters) | `readyAt` moves on for the same uuid (the server reserves the caster's recovery on every cast). |

What never animates: a stack the server rewrites without spending a round (lore, armament data), a different gun (other
serial or slot), creative-mode clicks (the server does not fire), anything in the off hand that the server did not change.

The server's replacement stack of the same gun is shown as the same item: the vanilla re-equip dip that used to play on
every shot (the gun sank off-screen and came back) is gone; switching to another gun still re-equips normally.

## Gun inventory

**Apocalypse arsenal** (`JasprApocalypse` `Arsenal.java`): unbreakable diamond hoe (item 293) with a model band per gun;
NBT `JasprApocalypse {id, arsenalMark, serial, rounds}`; fires from the main hand on right click; the reload time shrinks 15%
per Speed Loader rank (`JasprArmament.Abilities.speed_loader`, read by the stage for the reload timing).

| Gun | id | Band | Mag | Shot / reload ms | Server pattern | Animation class |
| --- | --- | --- | --- | --- | --- | --- |
| Last Light | `rifle` | 1560 | 18 | 300 / 2400 | standard | **rifle** |
| Requiem | `shotgun` | 1550 | 6 | 1100 / 3000 | standard | **pump** |
| Gravebreaker | `railgun` | 1540 | 3 | 2000 / 4200 | standard | **rail** (cyan) |
| Whisper | `whisper` | 1530 | 24 | 240 / 2600 | standard | **smg** (suppressed) |
| Tempest | `tempest` | 1520 | 24 | 1600 / 3200 | 3-shot burst | **burst** |
| Bastion | `bastion` | 1510 | 12 | 650 / 4600 | standard | **heavy** |
| Longwatch | `longwatch` | 1500 | 8 | 950 / 2800 | standard | **bolt** |
| Sunlance | `sunlance` | 1490 | 6 | 1250 / 3800 | standard | **plasma** (pink) |
| Adjudicator | `adjudicator` | 1480 | 10 | 580 / 3100 | standard | **dmr** |
| Cyclops | `cyclops` | 1470 | 4 | 1450 / 4000 | standard | **pump** |
| Frostbite | `frostbite` | 1460 | 5 | 1350 / 3500 | standard | **rail** (ice) |
| Sepulcher Service Pistol | `sepulcher` | 1450 | 12 | 380 / 1800 | standard | **pistol** |
| Vesper Silenced Pistol | `vesper` | 1440 | 9 | 480 / 2200 | suppressed | **pistol** (suppressed) |
| Ossuary Hand Cannon | `ossuary` | 1430 | 6 | 920 / 3300 | standard | **magnum** |
| Turnstile Snub Revolver | `turnstile` | 1420 | 5 | 620 / 2400 | standard | **revolver** |
| Cinder Machine Pistol | `cinder` | 1410 | 20 | 260 / 2500 | standard | **mpistol** |
| Tunnel Rat Patrol Carbine | `tunnelrat` | 1400 | 16 | 430 / 2700 | standard | **carbine** |
| Black Box Burst Carbine | `blackbox` | 1390 | 21 | 1650 / 3100 | burst | **burst** |
| Quarantine Battle Carbine | `quarantine` | 1380 | 14 | 600 / 2900 | stationary | **rifle** |
| Signal Lost Marksman | `signal` | 1370 | 7 | 1050 / 3200 | distant | **bolt** |
| Gallows Bolt Rifle | `gallows` | 1360 | 5 | 1500 / 3400 | standard | **bolt** |
| Watchtower Anti-Materiel | `watchtower` | 1350 | 3 | 2100 / 4400 | braced | **antimat** |
| Whiteout Covert Rifle | `whiteout` | 1340 | 6 | 1350 / 3300 | suppressed | **bolt** (suppressed) |
| Bellringer Double Barrel | `bellringer` | 1330 | 2 | 1350 / 2600 | standard | **breakgun** |
| Lockjaw Breaching Pump | `lockjaw` | 1320 | 5 | 1150 / 3100 | standard | **pump** |
| Choir Three-Barrel Verdict | `choir` | 1310 | 3 | 1700 / 3600 | standard | **breakgun** |
| Ashfall Drum Shotgun | `ashfall` | 1300 | 8 | 1000 / 4100 | standard | **drum** |
| Null Point Needle Rail | `nullpoint` | 1290 | 4 | 1650 / 3600 | rail | **rail** (cyan) |
| Cenotaph Siege Rail | `cenotaph` | 1280 | 2 | 2450 / 4700 | rail | **rail** (violet) |
| Witchlight Arc Projector | `witchlight` | 1270 | 6 | 1400 / 3400 | plasma | **plasma** (green) |
| Stormcoil Induction Lance | `stormcoil` | 1260 | 4 | 1850 / 4100 | flechette | **rail** (blue) |
| Hexbreaker Reliquary Beam | `hexbreaker` | 1250 | 5 | 1550 / 3800 | plasma | **plasma** (magenta) |
| Pallbearer Belt Cannon | `pallbearer` | 1240 | 20 | 700 / 5000 | braced | **heavy** |
| Iron Psalm Rotary Gun | `ironpsalm` | 1230 | 28 | 380 / 5200 | braced | **rotary** |
| Dead Frequency Pulse Rifle | `deadfrequency` | 1220 | 15 | 1900 / 4200 | burst | **pulse** (teal) |
| Rapture Service Pistol | `rapture` | 1210 | 12 | 450 / 1900 | standard | **pistol** |
| G18 Machine Pistol | `g18` | 1200 | 18 | 1600 / 2100 | burst | **mpistol** |
| .44 Magnum | `magnum44` | 1190 | 6 | 900 / 3000 | standard | **magnum** |
| Wingman Elite | `wingman` | 1180 | 6 | 700 / 2400 | distant | **revolver** |
| Mozambique | `mozambique` | 1170 | 3 | 800 / 2600 | standard | **breakgun** (small) |

Holding right click repeats the click every 4 ticks, so the fast guns (Whisper 240 ms, Cinder 260 ms, Last Light 300 ms,
Iron Psalm and Sepulcher 380 ms) fire several shots a second: that is the sustained-fire case.

**Portal Gun** (`PortalGun.java`, `portal_gun`, diamond hoe band 1160, no rounds): class **portal**; shots alternate blue and
orange, read from its action-bar line.

**Dungeon armoury** (`JasprDungeon` `Arsenal.java` / `ArmoryCatalog.java`): flat vanilla items (iron / gold / diamond horse
armour 417-419, blaze rod 369, prismarine shard 409, ghast tear 370), NBT `JasprPenitentArmory {version, kind:"weapon", id,
uuid, rounds, loading, readyAt, reloadAt}`. Their first-person "muzzle" is the far end of the sprite; casters cast from the tip.

| Type | Carrier | Burst | Reload ms | Class |
| --- | --- | --- | --- | --- |
| BAPTIST_NEEDLER | iron horse armour | 1 | 1400 | carbine |
| BELFRY_REPEATER | iron | 3 (11 ticks apart) | 1800 | burst |
| CARRION_SCATTERGUN | iron | 1 | 1800 | pump |
| OPALINE_RAIL | diamond | 1 | 2400 | rail (opal) |
| FOUNDRY_CANNON | gold | 1 | 2200 | magnum |
| MENAGERIE_DARTER | iron | 1 | 1600 | pistol (quiet) |
| SCRIPTORIUM_SIPHON | iron | 1 | 1700 | pistol |
| VESPER_DRILL | gold | 1 | 1900 | rifle |
| PAUPERS_RICOCHET | iron | 1 | 1800 | revolver |
| ASTRAL_ORRERY | diamond | 1 | 2100 | plasma (gold) |
| LABYRINTH_DOUBLE | gold | 2 (11 ticks apart) | 1800 | burst |
| LAST_ABSOLUTION | diamond | 1 | 2400 | bolt |
| CARRION_CENSER, SUNLESS_BRAND | blaze rod | cast | - | magic (witch sparks / flame) |
| OPAL_PRISM | prismarine shard | cast | - | magic (magic crits) |
| PALE_LANTERN | ghast tear | cast | - | magic (spell) |

Not guns: the Backrooms "Staple Gun" is a vanilla bow (vanilla draw animation), the realm armouries are armour and melee.
There is no launcher-type gun in the arsenal; the heaviest kicks are the anti-materiel rifle, the rails and the belt guns.

## Classes (profiles)

All motion is in the first-person hand frame (after vanilla's `transformSideFirstPerson`, before the item's display
transform): x right, y up, z toward the eye, blocks and degrees. Geometry per gun (muzzle centre, bore axis, grip, butt)
is measured from the pack models by `scripts/gunfx-data.cjs` into `client-mods/gunfx/gunfx-data.js`.

| Class | Kick back / climb / roll | Attack, spring | Sustained fire | Cycle | Reload | Flash | Particles | Camera pitch |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| pistol | 0.07 / 11 / 3.5 | 0.55 t, fast | light | slide jolt | pistol (raised, canted, slide snap) | small | brass | 0.7 |
| mpistol | 0.045 / 6 / 2.6 | 0.5 t, fast | strong | slide jolt | pistol | small | brass | 0.45 |
| revolver | 0.085 / 16 / 5 | 0.6 t | - | hammer cock | cylinder (swing out, spin, flick) | medium | - (casings stay in) | 1.3 |
| magnum | 0.11 / 21 / 7 | 0.65 t | - | hammer cock | cylinder | large | smoke | 2.0 |
| smg | 0.042 / 3.5 / 1.6 | 0.45 t, quick | strong | - | mag | medium | brass | 0.35 |
| carbine | 0.065 / 5.5 / 2 | 0.5 t | medium | - | mag | medium | brass | 0.6 |
| rifle | 0.08 / 6.5 / 2.2 | 0.5 t | medium | - | mag (in, canted, seat, charging handle) | medium | brass | 0.7 |
| dmr | 0.095 / 8.5 / 2.4 | 0.55 t | light | - | mag | medium | brass, smoke | 1.0 |
| burst | 0.065 / 5 / 2 | 0.5 t | strong | - | mag | medium | brass | 0.6 |
| bolt | 0.13 / 11 / 2.6 | 0.6 t, slow | - | bolt: turn, draw, drive, turn (casing on the draw) | bolt | large | brass, smoke | 1.6 |
| antimat | 0.2 / 14 / 3.5 | 0.7 t, slow | - | bolt | bolt | very large | brass, smoke x2 | 2.4 |
| pump | 0.15 / 13 / 4 | 0.6 t | - | pump: gun rocks back and forward (shell on the pull) | shells (canted, shell-by-shell, pump) | wide | red shell, smoke x2 | 2.0 |
| breakgun | 0.16 / 14 / 4 | 0.6 t | - | - | break (tipped open, snapped shut) | wide | smoke x2 | 2.2 |
| drum | 0.13 / 10 / 3.5 | 0.55 t | light | - | drum | wide | red shell, smoke | 1.6 |
| heavy | 0.08 / 4 / 2 | 0.6 t, heavy | very strong | thump | belt (tilted up, slam, rack) | large | brass, smoke | 0.6 |
| rotary | 0.04 / 1.8 / 1.2 | 0.4 t | strong | rotary shiver | belt | medium | brass | 0.28 |
| rail | 0.16 / 7 / 1.5 | 1.3 t shove | - | vent wobble | cell (canted, hum, click, power-up) | thin long spike, tinted | sparks | 1.8 |
| plasma | 0.11 / 7 / 3 | 1.0 t | - | vent wobble | cell | tinted | sparks | 1.2 |
| pulse | 0.07 / 5 / 2 | 0.7 t | strong | short vent | cell | tinted | sparks | 0.7 |
| portal | 0.06 / 6 / 7 | 0.7 t, springy | - | vent wobble | - | blue / orange | coloured dust | 0.5 |
| magic | thrust 0.2 away / 16 / 7 | 1.6 t | - | - | - | - | the caster's own sparks | 0.4 |

Kick curve per shot: an ease-out rise to the peak in `a` ticks, then a damped spring (frequency `w`, damping `z` below 1)
through one small overshoot (at most 35%) and settled in well under a second. Shots add up; sustained fire adds a slower
drift (fades over `T` ticks, soft-limited at `max`) so a held trigger pushes the gun back and up and lets it settle when
released. Rolls and yaws are random per shot (deterministic from the shot's sequence number). Every channel is soft-limited
(`LIMIT`: 0.3-0.35 blocks, 50 degrees climb, 70 roll, 30 yaw). The kick turns about the grip, or between the grip and the butt
for shouldered guns (`shoulder`); reloads always turn about the grip.

Reloads: a reload brings the gun in toward the middle and cants it (the vanilla gun already sits in the lower right corner;
dropping it would take it out of sight), works it (bob, shell by shell, cylinder spin, energy hum), seats the magazine and
racks it, timed to the gun's own reload time so the gun is up as the rounds come back; rounds coming back earlier start the
finish at once; a shot ends a reload animation by easing it out over 3 ticks.

Muzzle flash: additive (no glow, no outline, nothing on mobs), drawn right after the gun with no depth test or write, unlit
and untextured: a hot hexagonal core, a star of tapered spikes across the bore and two crossed flares along it, at most 17
quads, one draw call, full in the first frame drawn after the shot and gone after `life` ticks (1.2-2.0 ticks: a frame or two
on a phone, three or four at 60 fps). Suppressed guns barely flash.

Timing: client ticks plus partial ticks, never wall time. An event starts at the first frame drawn after the tick that saw it
(the kick starts from rest and the flash is full in that frame, whatever the frame rate); an event first drawn more than 1.5
ticks late (third person, a screen open) is not replayed from its start.

## Engine hooks

All three anchors are original engine text (checked against the unpatched bundle), each found exactly once in its function
and outside every fenced `JASPR_*` region (BetterCombat head, melee module and every other stage untouched):

| Function | Where | Hook |
| --- | --- | --- |
| `C45` EntityRenderer.updateRenderer (inlined ItemRenderer.updateEquippedItem) | right after both held stacks are read, before the re-equip checks | `JasprGunFXTick(b,f,g,j)`: resumable state 7491, once per client tick (reads a hand's NBT only when it holds a new stack object, the seven carrier items only) |
| `Ch0` ItemRenderer.renderItemSide | around RenderItem.renderItemModel | `JasprGunFXPre(c,d,e)` (first person only: the recoil transform) and `JasprGunFXPost()` (the flash) |
| `Chn` NetHandlerPlayClient.handleSoundEffect | before the sound plays | `JasprGunFXSound(...)`: notes the Apocalypse reload sound |

The module block goes right before the TeaVM footer, so it is the outermost stage. Combat assembly order (lead):
LIVE classes.js -> A's BetterCombat head -> B's melee module -> B's JASPR_COMBATFX -> **this stage last**.

```
node scripts/build-gunfx-client.cjs [input classes.js]           # default input: the LIVE site/classes.js (read only)
  -> candidate/gunfx/classes.js + candidate/gunfx/manifest.json
node scripts/build-gunfx-client.cjs --unpatch [input classes.js] -> candidate/gunfx/classes.without-gunfx.js
```

The builder refuses an anchor another stage replaced (it never guesses), checks that all 63 engine names the module uses are
declared by the client, that the module is ASCII with no regular-expression literals, that the result parses, that
`unpatch(build(x)) === x` byte for byte and that a rebuild is stable. The lead bumps the `classes.js?v=` key in
`site/client.html` when deploying (this workstream never touches `site/`).

## Knobs

| What | Where |
| --- | --- |
| Per-class feel (kick, springs, drift, cycle, flash, particles, camera) | `CLASSES` in `client-mods/gunfx/gunfx-core.js` |
| Reload choreographies | `RELOADS` (same file) |
| Which class a gun uses, its reload time, suppressed / energy colour / size | `APOC` and `DUNGEON` (same file; `tests/gunfx-core.test.cjs` pins ids, reload times, bursts and kinds to the server source) |
| Flash colours | `FLASH` (energy guns tint the rim with their own colour) |
| Gun geometry (muzzle, bore, grip, butt) | regenerate with `node scripts/gunfx-data.cjs` after a model change (`--check` verifies) |
| Camera impulse direction | `CAMERA_PITCH_SIGN` in `gunfx-native.js` (1: the view kicks up with a positive `pitch`) |
| Personal opt-out | localStorage `jaspr.gunfx.v1` = `{"enabled":false}` |
| Particle setting | All: flash, casings, smoke, sparks, full portal dust and cast sparks. Decreased: flash, casings and a little dust / spell (no smoke or sparks; vanilla also drops a third of all particles). Minimal, or the JasperCraft "particle effects" video option off: recoil only, no flash, no particles. |

## Camera impulse (workstream B's API)

On each shot / portal shot / cast, in first person only: `JasprCameraFX.kick({pitch, yaw, roll, back, ms})` with the class's
values (pitch 0.28-2.4 degrees, yaw and roll a fraction of a degree with a random sign, back 0-0.05 blocks, 95-320 ms).
Feature-detected (`typeof JasprCameraFX` or `$rt_globals.JasprCameraFX`); without it nothing camera-wise happens (counted as
`kickMissing`). Assumed sign: positive `pitch` = the view kicks up with the muzzle; if B's API uses Minecraft's pitch
convention (positive looks down), flip `CAMERA_PITCH_SIGN`.

## Diagnostics

`jaspercraft.gunfx.state` (`armed` on the first gun held, `first-shot` on the first animated shot) and `jaspercraft.gunfx.error`
(part, error text up to 180 characters, counters) through the existing same-origin route, only on `/jaspercraft/` pages, at most
12 per page; no positions, names, serials or chat. `JasprGunFXDiagnostics.status()` returns the counters (ticks, reads, shots,
reloads, dry, casts, portal, kicks, kickMissing, particles, aligned re-equips, poses, flashes, sounds), both hands' state,
disabled parts and the last error. Any exception switches off only its own part (`read`, `tick`, `pose`, `flash`,
`particles`, `camera`, `sound`, `overlay`) for the page; the game never sees it.

## Tests and previews

Run one at a time (PC safety: `lockrun.sh`, `--test-concurrency=1`):

| Test | What it checks |
| --- | --- |
| `tests/gunfx-core.test.cjs` | gun tables vs the server source; generated geometry vs the models; detection from synthetic NBT sequences (shots, replacements, other serials, slot changes, off hand, random sequences never produce a shot without a drop); sustained fire bounded and settling; first-frame start; kick curve; Apocalypse and dungeon bursts; reloads (sound, reloadAt, late flourish, eased cut); dry fire; Portal Gun lines; casts; settings; cycles and reload styles; flash geometry bounds |
| `tests/gunfx-native.test.cjs` | the engine glue on mocks with a simulated TeaVM fiber that suspends at engine calls (same results, no repeated call), renderer alignment, camera API present / absent / third person, casings through the particle setting, GL state restored exactly after the flash, the reload-sound filter, action-bar shots, dry clicks, failure isolation, bounded diagnostics, opt-out, builder on the LIVE client (round trip, stable, outermost, refuses a replaced anchor) |
| `tests/gunfx-harness.test.cjs` | the built candidate loaded in node:vm (game not started): the reader on real engine NBT objects, two ticks on real stacks (one shot, no re-equip dip, one casing, no part disabled) |

Previews (software renderer, real pack models and game textures; `node scripts/gunfx-preview.cjs candidate/gunfx-preview
[shots auto reload portal sprites trace paths]`): `shot-<class>-<gun>.png` (one shot per class, 0 to 24 ticks),
`auto-*.png` (sustained fire), `reload-*.png` (every reload style), `portal.png`, `sprite-*.png` (dungeon guns and casters),
`trace-*.png` (pose channels over time: climb red, back yellow x100, roll blue, yaw green, flash white), `path-*.png` (the
muzzle on screen over time).

## Open items

- Workstream B's `JasprCameraFX.kick`: confirm the field names and the pitch sign (see above) when B's stage lands.
- Not yet seen in a real browser: the lead's single lean verification after assembly should fire one gun of each kind
  (e.g. Sepulcher, Last Light held down, Lockjaw, Gallows, a rail) and reload once; `JasprGunFXDiagnostics.status()` shows
  the counters.
