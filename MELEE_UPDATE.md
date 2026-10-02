# First-person melee rework -- 2026-10-02

The owner asked for two things:

- Third-person melee attacks should be Mo' Bends only, without the other agent's BetterCombat third-person rig.
- The first-person melee animation should be reworked to look "realistic and natural, like how you would actually use a sword".

## Third person: Mo' Bends only
The BetterCombat client stage no longer touches the player model:
- The melee rig's third-person layer is gone: the torso twist, the shoulder poses and the planted stance.
- The game's own `ModelBiped.setRotationAngles` and swing-packet handler are back, byte for byte as before the rig.
- Mo' Bends no longer hands the arms to BetterCombat during a strike. It always plays its own attack animations: the sword combo, punches and tool swings.

With Mo' Bends switched off in Video Settings, third person shows vanilla's arm swing.

## First person: how the new swing moves
The weapon moves as if an arm swung it:
- **Wind-up:** the weapon is cocked back, for example raised to the right shoulder for a forehand.
- **Strike:** the blade cuts across the view in one plane, so the edge leads. It is fastest through the target and reaches the crosshair about 0.1 s after the click on a sword.
- **Follow-through:** the blade carries past the target and slows to a stop low on the far side.
- **Recovery:** the weapon is lifted back to guard through a low position on the right, gently and never as fast as the strike.

Each phase meets the next with no pop or kink in speed. Every clip starts and ends exactly on vanilla's resting pose.

### Combos
- Moves are chosen from where the weapon is. Clicking again while a forehand's blade is still low on the left gives a backhand that cuts back across.
- From guard, the sword alternates a falling diagonal forehand, a cut straight down and a level slash.
- A click during a wind-up or strike never cuts the stroke short. The next move waits for that stroke to finish and starts where it ended, so fast clicking still looks like a real combo.

### Weapon families
Each family is detected from the real item, including JasprApocalypse custom weapons:

| Family | Moves |
|---|---|
| Swords | 3 moves from guard + backhand |
| Axes | chop, cleave, backhand |
| Mauls, hammers, greatswords | slow overhead smash, wide sweep, backhand |
| Spears, lances, rapiers | aimed drive and jab at the crosshair |
| Scythes, sickles, halberds | reaping sweeps |
| Daggers | quick slash, stab, backhand |
| Pickaxes, shovels, hoes (as weapons) | hack, swipe, backhand |

Bare fists use vanilla's punch.

## Unchanged
- Damage, reach, cooldowns, packets, mining, guns, shields, bows, food and maps.
- The personal option Options -> Better Combat -> "JasperCraft melee animations" still switches the first-person motion off.
- No camera shake, no extra rendering.

## Build, preview, tests
```
node scripts/build-melee-client.cjs                                     # live classes.js -> candidate/melee-client (Mo' Bends taken off first)
node scripts/build-mobends-client.cjs candidate/melee-client/classes.js # Mo' Bends back on top -> candidate/mobends-client
node scripts/melee-preview.cjs [outDir] [clip ...]                      # offline frames + blade-path plots (no game, no browser)
node scripts/melee-preview-chain.cjs out.png sword 0,2,4                # a combo as the game plays it (click ticks)
node --test tests/melee-motion.test.cjs tests/melee-native.test.cjs
```
The preview reproduces the game's first-person item transform. It was checked against a real client frame of the resting sword and against the previous rig's own game recordings. The motion tests cover:
- the exact rest pose at both ends
- continuity, including at combo clicks
- the strike being the fastest phase
- mirroring for the off hand
- combo choice and queueing
- item families
- the sword's contact time

The native test loads the built client offline and checks that the real first-person hook draws exactly the motion's pose, and vanilla when the option is off.

Sources: `client-mods/melee/melee-motion.js` (the motion), `client-mods/melee/melee-native.js` (the TeaVM bridge). The BetterCombat 7 rig's sources under `server/custom-plugins/JasprBetterCombat/melee-rig` are no longer what is deployed.
