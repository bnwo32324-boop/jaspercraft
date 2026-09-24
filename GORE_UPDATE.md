# Native dismemberment — deployed

Published 2026-09-07T22:38:22Z; public delivery and server health verified at 2026-09-07T22:39:23Z.

Play at https://jaspr.chat/jaspercraft/. Existing game tabs need a full page reload to receive this update.

## Included

- Custom native browser-client implementation: no Forge installation or extra download prompt.
- Progressive, health-based amputation and wound caps for living non-player mobs; small single-body models chip into pieces instead of vanishing alive.
- Final deaths scatter the remaining textured body parts, with gravity, rotation, bounce, terrain collision, blood/debris bursts and short-lived surface splashes.
- Native local lighting, fog and depth apply. No full-bright/glowing override. Skeletons, slimes, constructs and supernatural mobs use matching bone/ichor/debris colors.
- Bounded effects and cleanup, including lower coarse-pointer/mobile limits. No diagnostic overlay in production.

Amputation is cosmetic and follows synchronized health; healing can restore anatomy. Server AI, hitboxes, attacks and loot are unchanged. Players and armor stands are excluded. Third-party/native Java clients do not receive this browser-renderer extension.

## Verification

- Public `classes.js` fetched in full and SHA256 matched `80E120E18EEB33E1F57BD21239D97D6C1DE4CD746BB40E521C36736078F285FC` (14,376,327 bytes).
- Landing page, SSO loader and game loader all deliver cache version `20260907-gore1`.
- All 31 Node tests passed: 14 gore runtime/build tests, 14 browser/auth/settings/startup regressions, and 3 apocalypse asset tests. The existing site verification script passed.
- 40 Java SSO ticket/replay/UUID/privilege/player/outage contract checks passed. The rebuilt authentication candidate was **not** deployed.
- Two full live-renderer sweeps on an isolated Paper 1.12.2 fixture exercised all 49 spawnable living mob types through injury and death, including both bosses, animals and flying mobs. Each sweep rendered all 49 deaths and 530 body pieces, with zero renderer errors; expired effects and tracked states returned to zero.
- The second complete sweep used client SHA256 `3154477951b0f16fea5c9df18d089fb4d22947a3b86282fabfe020adb0606281` and recorded 145 injury observations and 118 amputation events. It completed at 2026-09-07T22:34:25Z.
- Two narrowly scoped fixes followed that sweep: original geometry-scale preservation for wolf head/tail rendering, and prevention of duplicate severed limbs during regeneration within the same health band. All 31 tests above ran on the final deployed build. The final build was also loaded in the real browser fixture: wolf injury/death produced correctly sized pieces; nighttime zombie deaths produced dark, normally lit debris and floor splashes, with zero renderer errors.
- Physical iOS/Android hardware was not tested. Mobile work/memory limits are covered by automated tests; no zero-overhead claim is made.

Fixture: `candidate/gore-preview-1b474c3e-3597-426f-9bf3-729c7308b138/`. It used its own loopback-only world and synthetic test identity, never a Jaspr account. The fixture was stopped after verification; no test plugin was installed in production.

## Preserved

- No production server restart, world reset, account migration or inventory modification.
- Asset EPK remains `6B08BA9298DB1636FE39ADD9B9D1ECE4F1F7D63D76802D278BBD2F9D7C95E7E7`.
- Apocalypse plugin remains `05EC4A02DE8C2E9DBEEBDE0D30E4F110AACEA77F3F73605776CCD7BD16494142`.
- Authentication plugin remains `E164C9643F58F5670F134B2B0988603A198E451380210A0202EAB3956A42EB49`.
- Settings namespace, account persistence, Survival defaults, per-player game-mode persistence, weapon models/orientation, mobile launch fixes and direct entry are unchanged.
- Post-publication gateway: online; server: running; SSO ready; always-on true; idle shutdown zero; no last failure.

Implementation, limits, diagnostics, build and reproduction: [client-mods/README.md](client-mods/README.md).

Subsequent release: [62-biome overhaul and progress-preserving terrain reset](BIOMES_UPDATE.md). That release retains the gore implementation while adding native biome names/climate and changing the complete client hash.
