# Mo' Bends in the main game -- 2026-10-01

The owner asked to finish the isolated Mo' Bends port, with the dismemberment system working on it, "working in the
actual game", and "a setting in video settings to turn it off".

## What it is
Mo' Bends 1.2.2 (goblinbob/Iwo Plaza, MIT) animates players and mobs with bending limbs: elbows and knees, a torso that
twists and leans, smooth transitions between poses. This is a JavaScript translation of the original mod's animation
code (same classes, methods and constants as the Java source), built into the deployed browser client as one more
reversible client stage. The isolated sandbox could not ship: it was compiled against a different client build and
would have dropped the other client stages (gore, BetterCombat, gear, tanks, video settings, ...).

Animated, as in the original release: **players, zombies (and husks), skeletons, zombie pigmen, spiders (and cave
spiders), squids and wolves**. Players: stand, walk, sprint, sneak, jump and sprint-jump, fall, swim (surface and
underwater), climb ladders and vines (with the ledge pull-up), ride living mounts, sit in boats/minecarts, fly
(creative), elytra, sleep, hold a torch, eat, draw a bow, block with a shield, punch, swing tools, the five-move sword
combo (with the spin attack) and the guard stances, with sword trails. The cape waves in 16 hinged slabs; armor, held
items, skulls/pumpkins, elytra and worn gear follow the bent limbs. Zombies lean or stumble (two styles per zombie),
skeletons strafe and aim, pigmen hunch, spiders place every leg with inverse kinematics, crawl up walls and curl up when
they die, squid tentacles ripple, wolves run on the original keyframe animations (idle, walk, sit down, sit, stand up,
breathing, tongue). Flying arrows leave a short fading trail.

Left out on purpose: the original's online pieces (supporter accessories, downloadable animation packs, the in-game
animation editor and its settings window), because they need Mo' Bends' web services. Zombie villagers stay vanilla,
exactly as in the original release (their head model is not a zombie model).

## Video Settings switch
Options -> Video Settings -> **"Mo' Bends animations: ON/OFF"** (last JasperCraft row, beside "Optional particles").
It is personal and saved in the browser (`jaspr.mobends.v1`); presets, auto-detect and "Reset Video Defaults" never
change it. OFF puts every vanilla model back at once (nothing is reloaded); ON bends them again.

## Works with the other systems
- **Dismemberment (gore)**: limbs are cut on the bent model. A cut upper arm or thigh takes the forearm/shin with it;
  forearms and shins can also be lost on their own; a lost arm or forearm drops its held item. Health bands, healing,
  pieces and blood are unchanged.
- **BetterCombat**: third-person attacks are always Mo' Bends' own (since 2026-10-02 BetterCombat has no third-person
  layer; see MELEE_UPDATE.md). BetterCombat keeps its gameplay and its first-person weapon motion.
- **Gear / worn trinkets, armor stands, other client stages**: unchanged. Worn gear attaches to the bent limbs.

## Safety and diagnostics
- Client only: no plugin, packet, world or account change. Each player's browser animates what it sees.
- Any error inside Mo' Bends turns it off for that session and restores the vanilla models; the game keeps running.
  It reports one bounded `jaspercraft.mobends.error` event (stage + sanitized message, no names or positions) through
  the existing diagnostics route, and `jaspercraft.mobends.state` when the switch changes.
- `window.JasprMoBendsDiagnostics.status()` shows the switch, counts of bent renderers, tracked entities, trails and
  per-layer counters. Bounded: at most 128 arrow trails, sword-trail parts expire after 20 ticks.

## Build and tests
```
node scripts/mobends-assets.cjs              # (only if the original resources change) wolf animations + texture
node scripts/build-mobends-client.cjs        # candidate/mobends-client/classes.js from the LIVE site/classes.js
node --test tests/mobends-core.test.cjs tests/mobends-math-parity.test.cjs tests/mobends-native-harness.test.cjs tests/mobends-native.test.cjs
node scripts/mobends-preview.cjs             # loopback Paper + browser fixture (commands: mobends lineup|walk|sit|hurt|pace|sneak|sprint|elytra|arrow|tp)
```
The builder checks every hook anchor in its function, that every engine name it uses exists, that the result parses
and that removing the stage gives back the input byte for byte. Mo' Bends is the outermost client stage: several of
its hooks sit right next to gore's, the video stage's and Dynamic Surroundings' hooks. To rebuild one of those, first
take Mo' Bends out (`node scripts/build-mobends-client.cjs --unpatch` writes
`candidate/mobends-client/classes.without-mobends.js`), rebuild that stage on it, then run this builder on the result.
Running this builder on a client that already has the stage replaces it.

Mo' Bends' client tick runs at the start of `Minecraft.runTick` (20 per second, skipped while paused), exactly where
Forge fires `ClientTickEvent`; its render update runs once per world pass. In the headless loopback probe the game
client times out after one to two minutes with or without this stage (the unmodified live client does the same), so
keep headless runs short. The tests compare the JS math with the original Java
(compiled from the mod's sources), run every controller through its states, and load the real client in Node to check
that the bent player covers the vanilla player limb for limb in the rest pose, with the original UVs.
Sources: `client-mods/mobends/` (core, TeaVM adapter, generated assets), notices in `THIRD_PARTY_NOTICES.txt` there.
