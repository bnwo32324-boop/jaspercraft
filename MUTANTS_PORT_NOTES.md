# Mutant Creatures port: how it fits together

Mutant Creatures Legacy (chumbanotz, AGPL-3.0) ported 1:1 to JasperCraft. Wire contract: `MUTANTS_PROTOCOL.md`. Engine
reference for the client: `MUTANTS_CLIENT_INTERNALS.md`. Brief and licence notes: `MUTANTS_PORT_BRIEF.md`.

## Pieces

| Piece | Where | Built by | Tests |
| --- | --- | --- | --- |
| Server plugin JasprMutants (the mod's own classes on a Forge shim) | `server/custom-plugins/JasprMutants`, `server/plugins/JasprMutants.jar` | `scripts/build-mutants-plugin.sh` (toolchain: `C:\Users\AM\Documents\JasperCraft-Mutants\toolchain`) | `tests/mutants-runtime.cjs` (real Paper, 36 checks) |
| Client stage JASPR_MUTANTS (outermost stage of `site/classes.js`) | `client-mods/mutants/*.js` | `scripts/build-mutants-client.cjs` | `tests/mutants-client.test.cjs` (offline, 18 tests) |
| Client assets (textures, item models, sounds, sound events, names) | `client-mods/mutants/assets/mutantbeasts` | `scripts/build-mutants-pack.cjs` | checked by the builder (every reference resolves; idempotent) |
| Live client files (classes.js, assets.epk, cache keys) | `candidate/mutants-deploy` | `scripts/assemble-mutants-client.cjs` | `tests/mutants-browser.cjs` (real client + server) |
| Dungeon use | `server/custom-plugins/JasprDungeon/.../MutantBridge.java` | (JasprDungeon 7) | dungeon tests |

## Client install order

1. `Bootstrap.register` (hook `Fga`): sound events 1000-1042, particle types 100/101, the 15 entity twins (ids 210-224),
   the 15 items (ids 4000-4014, translation keys `item.mutantbeasts.*`). This is before the model bakery reads item variants.
2. First `Minecraft.runTick` (resumable state 3105 in `CHq`, already at the main menu): renderers, particles and the tracker
   screen are defined, every texture they draw is bound once (the image decode may suspend there and nowhere else), then
   renderers, the shoulder layer, item models and particle factories are registered. Until then a mutant is not drawn.
3. Per tick: HELLO (`jaspr:mutants` op 0) once per connection, a pending tracker screen, at most four queued messages.

Hooks: `Fga` registry, `Cyr` payloads (`jaspr:mutants`, `mutantbeasts`; `jaspr:scale` is left to JASPR_BIGMOBS), `CHq` tick,
`EQy`/`F4A` spider pig jumping, `E7Q` scalable model parts, `Fbv` item model variants, `AH1` item perspective, `F5U` TEISR
(endersoul hand), `D$Y` armour model (mutant skeleton skull).

## Failure isolation

Every engine entry point is guarded; an exception reports one `jaspercraft.mutants.error` event, switches off only its part
and the engine gets a safe answer, never the exception:

- entities (tick, status, interaction, data watcher, movement, impact): the twin is removed from this client's world;
- render (renderers, layers, shoulder layer): nothing drawn for mutants;
- particles: the particle expires / is not spawned;
- items: the vanilla Item/ItemArmor method answers;
- gui, send, spawn, modchannel, payload: as named.

`JasprMutantsDiagnostics.status()` in the page shows the counters, the parts switched off, and the first failure's stack
(`firstStack`, local only, never sent).

## Assets

Under the minecraft domain (the client resolves only that one for these paths): `textures/entity|gui|particle/jaspr_mutants/`,
`textures/items/jaspr_mutants/`, `textures/models/armor/jaspr_mutants_mutant_skeleton_layer_{1,2}.png`,
`models/item/jaspr_mutants/`, `sounds/jaspr/mutants/`, events `jaspr.mutants.<mod event>` in `sounds.json`, names appended to
`lang/en_us.lang` between `JASPR_MUTANTS` fences.

The newer Mutant Skeleton sounds (outside `legacy/`) are All Rights Reserved: never copied, shipped or committed. Their events
play the legacy files (ambient, death, hurt, step; the server uses the legacy events) or a close vanilla event (bite, bow
draw, bow shot, jump, punch). The build stops if such a file appears in the source folder.

## Rebuilding

The Mutants stage is the outermost one in `classes.js`. To rebuild any other stage: `node scripts/build-mutants-client.cjs
--unpatch <classes.js>`, rebuild the other stage on that, then build Mutants again. The asset pack only adds files and
replaces its own fenced lang block and `jaspr.mutants.*` sound events, so it can be re-run on any later archive.

## Licence

AGPL-3.0 (`client-mods/mutants/assets/LICENSE`, `ASSETS_LICENSE.txt`; the plugin carries the same). Players can get the
source: it is this repository (AGPL section 13 offer on the client's About/credits page, owned by the lead).
