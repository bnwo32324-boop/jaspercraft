# Native dismemberment adapter

This is our own implementation for the deployed JasperCraft 1.12.2 JavaScript client, not the original Forge mod jars. It is automatically delivered with the game. No client installation, server entities, physics plugin, account migration, or world reset is required.

## Behavior

- Living non-player mobs progressively lose peripheral model parts at 72%, 42%, and 18% health. The head/torso are generally reserved for death. Parts with children detach together; armor models follow missing anatomy, and a missing hand does not leave a floating held weapon.
- Death scatters the remaining textured body parts. The native mob model is suppressed during the death animation so there is no duplicate intact corpse. One-part models are sliced into quarters rather than disappearing entirely on their first injury.
- Blood/debris bursts and short-lived surface splashes use Minecraft-style geometry. Skeletons, slimes, constructs and supernatural mobs have appropriate bone/ichor/debris colors.
- The native mob's textures and animated transforms are reused. Native lightmaps, depth testing, fog and nearby loaded-block light apply; this does not turn on glowing/full-bright rendering.
- Gravity, bounce and surface collision are local visual simulation. Deaths, loot, damage, block destruction, hitboxes, AI and inventory remain server-authoritative and unchanged. Missing legs/arms are cosmetic; this implementation does not disable attacks or change pathfinding.
- Healing restores the appropriate anatomy. This is a rendering of synchronized health, not permanent server-side amputation state. Players and armor stands are excluded.

## Limits and cleanup

Desktop caps: 96 body pieces, 120 blood fragments, 72 splashes, 256 tracked mobs, 64-block range. Coarse-pointer/mobile caps: 48/48/32. Physics processes at most 96 bodies per step (48 mobile), at 20 Hz. No background frame loop or network telemetry is added. The renderer uses its existing frame loop.

Body pieces expire in 7–9 seconds; droplets in roughly 2 seconds; splashes in 11 seconds. Dead/unused state is pruned and all effects release the old world on disconnect/dimension change. A cosmetic exception disables the extension and falls back to ordinary rendering. Normal native display-list fiber resumes are preserved. There is bounded additional rendering work; this is **not** a zero-performance-cost claim.

## Build and verification

```powershell
node scripts/build-gore-client.cjs
node --test tests/gore-runtime.test.cjs
node scripts/gore-preview.cjs
```

The build writes **only** `candidate/gore-client/`. It checks the exact pre-extension client SHA256, verifies every hook anchor/count, parses the candidate, and verifies byte-for-byte reversal. It also accepts its own currently patched build, removes this extension, and regenerates it. If the underlying TeaVM client changes, re-audit the adapter; do not relax the hash assertion.

`gore-preview.cjs` starts a fresh, loopback-only Paper server and browser fixture. Start it in an interactive terminal (`tty=true`) and use the printed URL. `gorepreview cycle` exercises all 49 spawnable living mob types through two injury stages and death. `gorepreview <TYPE>`, `gorepreview hit <damage>`, `gorepreview night`, and `gorepreview day` support visual checks. `stop` shuts down the fixture gracefully; a 45-minute safety timeout also stops it. The fixture plugin refuses to enable without its private test JVM flag. It is never installed in production.

`window.JasprGoreDiagnostics.status()` exposes bounded counters, mob-type coverage, limits, and a sanitized last error for troubleshooting. It does not expose account details, entity IDs, credentials, settings, or native control APIs. There is no diagnostic overlay in the shipped game; the overlay exists only in the isolated preview.

Deployment is a client-only asset update: validate the candidate, publish `classes.js`, and bump the existing loader query versions. Keep `assets.epk`, the settings namespace, authentication, and all server plugins unchanged. Existing game tabs need a page reload to receive the new client. Native Java/third-party clients do not receive a browser-renderer extension.

The Tab list keeps the native connection bars and adds a color-coded `N ms`
label beside them. It reads the same `NetworkPlayerInfo` response-time value
(`bzW`) as the native bar renderer on every Tab-list render, so it is not a
second timer or a stale server-side name rewrite. `-- ms` is shown until the
client has a valid value. The label is cosmetic and fails closed if the
renderer ever changes.

## Source map / native adapter notes

`gore-teavm.js` documents the adapter boundary. `Gxv` brackets entity rendering; `DbP` brackets world entity rendering; `E7Q` and `Eu3` draw model parts; `CM$` owns layers; `Cqg` attaches held items; `Gsc` changes worlds. The callback wrapper preserves TeaVM suspension/resumption. All inserted names are prefixed and the engine itself is closure-scoped.

`scripts/inspect-client.cjs` and `scripts/client-source-map.cjs` are read-only offline inspection tools. They never execute the game's `main()` or mutate the live bundle. The original source map remains approximate around patched functions (as it already was for earlier startup fixes); extension source is kept readable in this directory.
