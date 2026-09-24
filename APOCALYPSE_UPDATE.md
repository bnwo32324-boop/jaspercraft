# The Last Broadcast — deployed

Verified 2026-09-07T01:40:28Z at https://jaspr.chat/jaspercraft/.

## Included

- Hard survival apocalypse with six undead variants, finite-range pursuit, gradual block breaching, limited climbing, and warned TNT breaches.
- Blood Moons, Hollow Revenants, and summoning Grave Wardens; supernatural does not mean glowing/full-bright mobs.
- Six ruined-structure families with variants, supported foundations, underground interiors, supplies, and rare relic caches. Newly explored terrain only.
- Last Light rifle, Requiem shotgun, Gravebreaker railgun. Expensive relic/Nether Star recipes, marked ammunition, reloads, persistent magazines, and custom 3D item models delivered automatically.
- In-game field guide; replacements use a book and rotten flesh.

No world reset, account changes, or inventory migration. The saved player game mode is not forced on login. Default survival and always-on operation remain configured. Existing players must reload the page to receive the new models.

## Verification

- Exact deployed plugin SHA256: `05EC4A02DE8C2E9DBEEBDE0D30E4F110AACEA77F3F73605776CCD7BD16494142`.
- Real isolated Paper 1.12.2 test: 64,142 assertions, 24/24 completed cache-bearing ruins spanning every family, protected/cancelled TNT and digging checks, actual region-file save/reload of loot and magazine NBT, clean shutdown. See `candidate/apocalypse-smoke-51506d3b-980a-40df-9811-61bd23bdf315/result.json`.
- 768 layout scenarios plus terrain, support, access, rotation, journal interruption/corruption, and rail-state regressions passed.
- 17 asset/browser regression tests and 40 SSO contract checks passed. The precompiled client JavaScript remains unchanged.
- 3,124 item damage/model selections and 12 held-view orientation matrix cases passed. All texture references resolve against the real EPK. Visual holding/aiming has not been manually tested in a live client or on physical iOS/Android hardware during this update.
- Only five item-model entries changed/added; 5,791 other archive entries retained byte-identical content. EPK grew by 13,240 bytes.
- Public landing page, loader chain, and full EPK fetched successfully; public EPK SHA256 matched `6B08BA9298DB1636FE39ADD9B9D1ECE4F1F7D63D76802D278BBD2F9D7C95E7E7`.
- Live logs contain `APOCALYPSE_READY`; gateway reports running, SSO ready, always-on true, idle shutdown zero. An existing EaglerXServer/SkinsRestorer `ServerLoadEvent` compatibility warning was also present in five pre-update logs; this release did not change those plugins.

The actual gameplay test did not simulate player input/crafting/reloading events end-to-end. It verified the live recipe registry, production ingredient checks, serialization, collision helpers, block events, generation, and shutdown. Runtime limits are explicit; no zero-overhead claim is made. Generic land claims are not detected by the new-terrain ruin generator, but existing structures/player edits and nearby players are avoided; siege block changes honor cancellable Bukkit events.

Implementation, tuning, and build instructions: `server/custom-plugins/JasprApocalypse/README.md`.

Subsequent client-only release: [native dismemberment](GORE_UPDATE.md). That release updates the client renderer; the plugin and asset hashes recorded above remain unchanged.

## Daylight rules update — 2026-09-16 (live)

- **Equal spawns day and night, above ground and underground.** The ambient siege spawner no longer has a night gate; torch and artificial light still suppress spawns. Invasions no longer burn off at sunrise (schedule still starts on invasion nights).
- **No daylight burning, entirely.** Sunlight combust is cancelled for every non-player mob; lava, fire blocks and flame weapons still burn.
- **Day weakness (all hostile mobs, incl. customs):** half max health and half melee damage while the sun is up (`time < 12500 || > 23500`), fully restored at night; TNT-carrier blasts halved too. Subtle gray chat note on each day/night flip.
- **Day sluggishness:** mobs standing in direct sunlight at/above the surface move 30% slower; shade, cover, night and underground restore full speed.
- **Mining twice as fast:** siege breach passes halved (metal 12, stone 7, wood 4, other 3; protected blocks still excluded) and invader miners dig on half cooldown.

Deployed `JasprApocalypse.jar C7DEBABA28ECC0E3411E732432552183CA9BA2C5D6B0B65597C76AC44A9B3414` and `JasprInvasions.jar 8C68C9252992066D50EA4E321A18B9B8D386997FA0F4926CF811AA063965079B` with a graceful restart; previous jars kept in `.runtime/plugin-backup-20260916-172134-daylight/`. Verified: `equipment-smoke` PASS (2576 assertions, incl. a new daylight-cycle-and-mining phase), both plugins enabled with `APOCALYPSE_READY`, live metrics flowing, no new errors. Known pre-existing, unchanged: `stats-smoke`/`apocalypse-smoke`/`creative-catalogue`/`siege-awareness-paper` drift failures documented in the session, and the `EaglerXServer/SkinsRestorer` + AuthMe GeoIP boot warnings.

### Sun-slow follow-up fix — 2026-09-16 (live)

The first daylight build slowed the speed attribute, but siege hunters steer on fixed
traversal velocities (transfers, wall climbs, centering nudges, roof jumps) that never read
it — so the most visible above-ground mobs ignored the slow. Fixed: all fixed horizontal
chase velocities scale ×0.7 while the sun-slowed marker is present (vertical lifts untouched
so climbs and pillar jumps still land); ground chase already read the attribute. Verticals,
halts and gravity are unchanged. `APOCALYPSE_METRICS` now reports `sunSlowed=N` so the effect
is observable live during the day.

Deployed `JasprApocalypse.jar BF4C448A0CE134105C474BFFA65B8AB88E50D07444C6280502758D1CAF9CE852` (previous kept in `.runtime/plugin-backup-20260916-174500-sunslow/`). Verified: `equipment-smoke` PASS (2577 assertions), clean enable with `APOCALYPSE_READY`, metrics flowing with the new field, no new errors.

### Guaranteed rotten flesh — 2026-09-17 (live)

Every custom zombie now drops rotten flesh on every death, with all other loot untouched:
`SiegeDirector` tops hunter drops to at least one flesh regardless of killer or vanilla luck,
and a detonating TNT Carrier leaves one at its crater (detonation bypasses the death event);
`InvasionListener` adds one flesh to zombie-type invaders after the no-farm clear (gear stays
cleared, XP cap stays); `StructureEncounters` adds one flesh to zombie-type vault guardians in
the final MONITOR clear (vault loot stays locked). Vanilla mobs and non-zombie customs unchanged.

Deployed `JasprApocalypse.jar 62AD2BF8AFF50F678C8DFCD0CE94B7C0910A49E35A88B4BB15A743ED1AFE1A8F`, `JasprInvasions.jar EE7043C134764A541DC2468E168DF394D80F049D7ABC55EA7D0B1BDBD0440A21` and `JasprHorrorBiomes.jar BC400C9F47F3CD84A41966A2EBB115336EFA8C9870264506B1A860FC60112C5A` in one graceful restart (previous jars in `.runtime/plugin-backup-20260917-flesh/`). Verified: `equipment-smoke` PASS (2577 assertions, incl. a `guaranteed-flesh-drops` phase covering walker and Breacher), clean enable of all three plugins, metrics flowing, no new errors. The movement-slow system (JasprDaylight + traversal factor) was deliberately not touched.

### Halved hunter reach — 2026-09-17 (live)

All 7 siege hunters (Shambler, Runner, Wall Crawler, Breacher, TNT Carrier, Revenant, Warden)
now land melee hits only within ~0.89 blocks feet-to-feet (threshold `0.8` squared, down from
~1.79 / `3.2`) — half their previous reach and well under the vanilla 1.43. Cooldown (20 ticks),
line-of-sight gate, damage values, TNT blast radius and guardian/invasion/vanilla reach untouched.

Deployed `JasprApocalypse.jar 5B527B284C309FBFF69425AF28B4E583D40EDBB7CD94FA7EC5EB4A70554C398E` (previous in `.runtime/plugin-backup-20260917-reach/`). Verified: `equipment-smoke` PASS (2577 assertions), clean enable with `APOCALYPSE_READY`, no new errors.

### Halved hunter damage — 2026-09-17 (live)

All 7 siege hunters deal half melee damage: Shambler/Runner/Crawler 4→2, Breacher 8→4,
Revenant 6→3, Warden 10→5; TNT Carrier blasts halved at the base formula too
(`max(1,(16-d*3)*0.5)`). The day-weakness modifier stacks on top as before, so daytime hits land at roughly a quarter of the original values. Revenant/Warden slow effects, warden summons, blast radius and all other mobs untouched.

Deployed `JasprApocalypse.jar ECA44428E88486CF80366CF08E2E2D2159A71D7118491E3FB75D731DDB96150D` (previous in `.runtime/plugin-backup-20260917-damage/`). Verified: `equipment-smoke` PASS (2579 assertions, incl. per-kind halved-damage checks), clean enable with `APOCALYPSE_READY`, no new errors.

### Synchronized day/night damage — 2026-09-17 (live)

Days hit too softly, so the daytime damage weakness is retired: all 7 hunters now deal their
full halved-base damage around the clock (Shambler/Runner/Crawler 2, Breacher 4, Revenant 3,
Warden 5, TNT blast at base formula day and night). The daytime *health* weakness stays, and
any legacy damage-weakness marker on living mobs is stripped within seconds of this build
loading. Revenant/Warden slow effects and all other mobs untouched.

Deployed `JasprApocalypse.jar 64FB81503748FE30E0DA69A87B1B401E987DE651FA42384D7812061EB91FE420` (previous in `.runtime/plugin-backup-20260917-syncdmg/`). Verified: `equipment-smoke` PASS (2579 assertions), clean enable with `APOCALYPSE_READY`, no new errors.

### Configurable difficulty, set to peaceful — 2026-09-17 (live)

The plugin used to force HARD on every boot. `configureWorld` now honors a top-level
`difficulty:` key (`peaceful/easy/normal/hard`, defaults to HARD on garbage, live config set to
`peaceful`). Peaceful despawns hostiles, stops hunger drain and regens health; custom siege
spawners keep firing but their mobs cannot survive it. Flip it back to `hard` + restart anytime.

Deployed `JasprApocalypse.jar 7C583CB26F3773AD929F70DEB9DA746654468A4725EAC659B2082E484B9607EC` (previous in `.runtime/plugin-backup-20260917-peaceful/`). Verified: `equipment-smoke` PASS (2579 assertions), clean enable with `APOCALYPSE_READY`, `level.dat` Difficulty reads 0 after autosave, no new errors.
