# Expedition equipment contract

All methods are public static on `chat.jaspr.apocalypse.ApocalypseItems`; call on the Bukkit server thread.

```java
ItemStack weapon = ApocalypseItems.gear("whisper");
ItemStack tiered = ApocalypseItems.expedition("bulwark_chestplate", 5);
Map<String, String> all = ApocalypseItems.catalogue();
Map<String, String> guns = ApocalypseItems.catalogue("gun");
```

`gear(id)` is the stable reflection entry point for ExpeditionLoot. It creates ONE fresh item at provenance tier 1. `expedition(id,tier)` clamps tier to 1–5 and records it in NBT; tier never multiplies stats. Unknown/null IDs throw `IllegalArgumentException`. Every gun starts empty with a fresh serial. Do not stack guns, melee weapons, or armor; their runtime identity requires amount 1. Use the existing `ammo(int)`, `scrap(int)`, and `relic(int)` for bulk supplies. Materials and consumables can stack normally. Separate tier tags intentionally make different provenance tiers different stacks.

Both catalogue methods return immutable insertion-ordered ID-to-display-name maps, never ItemStacks. Supported categories: `gun`, `melee`, `armor`, `material`, `consumable`, `supply`, and `all`. Unknown categories return an empty map. The full catalogue has 85 entries: 35 guns, 24 melee weapons, 16 armor pieces, 4 materials, 3 consumables, 3 legacy supplies. This expansion appends exactly 24 guns and 16 melee weapons. Loot eligibility/weights belong to the loot caller; this factory does not grant items to players automatically.

## Guns

Damage is before native armor, shielding, invulnerability, PvP, and damage-event protection. Range is blocks. Magazine capacity counts shots; cartridge cost is per loaded shot. Native reload, main-hand-only firing, serial checks, cancellation and collision-shape raycasts remain shared with the original Arsenal.

| ID / name | Damage | Range | Magazine / cartridge cost | Shot delay / reload | Behavior |
| --- | --- | --- | --- | --- | --- |
| `rifle` / Last Light | 32 | 64 | 18 / 1 | 300 / 2400 ms | Original rifle unchanged |
| `shotgun` / Requiem | 9 × 10 | 28 | 6 / 2 | 1100 / 3000 ms | Original shotgun unchanged |
| `railgun` / Gravebreaker | 100 | 96 | 3 / 8 | 2000 / 4200 ms | Original 3-body penetration, 80% retained per body |
| `whisper` / Whisper | 28 | 72 | 24 / 2 | 240 / 2600 ms | Suppressed sound; zombie attraction radius 8 instead of 40 |
| `tempest` / Tempest | 38 per shot | 68 | 24 / 2 | 1600 / 3200 ms | Three tracked shots, 11 ticks apart; re-aims each shot |
| `bastion` / Bastion | 54 | 52 | 12 / 4 | 650 / 4600 ms | 2-body autocannon, 75% retained; moving damage ×0.7 |
| `longwatch` / Longwatch | 58 | 112 | 8 / 3 | 950 / 2800 ms | Beyond 32 blocks, damage ×1.35 |
| `sunlance` / Sunlance | 3 × 30 | 48 | 6 / 6 | 1250 / 3800 ms | Narrow plasma fan, 2-body penetration, 65% retained |
| `adjudicator` / Adjudicator | 46 | 88 | 10 / 3 | 580 / 3100 ms | Stationary damage ×1.3 |
| `cyclops` / Cyclops | 12 × 13 | 22 | 4 / 5 | 1450 / 4000 ms | Wide, heavy close-range scatter volley |
| `frostbite` / Frostbite | 64 | 82 | 5 / 5 | 1350 / 3500 ms | 4-body precision flechette, 70% retained per body |

### Appended firearms

Every firearm has a distinct 3x3 crafting recipe made only from vanilla ingredients. Sidearms begin with iron-block receivers, ingot barrels, redstone triggers and gunpowder actions; carbines, shotguns and rifles add heavier frames, optics, pistons, repeaters and feed mechanisms. Energy and heavy weapons add redstone blocks, gold, diamonds and other rare vanilla components. No relic, salvage, Weapon Core, Power Cell or other tagged custom item is accepted. Ordinary cartridges are shared across the arsenal. Capacity, accuracy, range, fire cadence, reload time, cartridge cost and specialist conditions still trade off against one another; provenance tier never multiplies damage.

| ID / name | Model band | Damage | Range | Magazine / cost | Shot / reload (ms) | Specialty / silhouette |
| --- | --- | --- | --- | --- | --- | --- |
| `sepulcher` / Sepulcher Service Pistol | 1450 | 26 | 46 | 12 / 1 | 380 / 1800 | Short steel slide, exposed sights |
| `vesper` / Vesper Silenced Pistol | 1440 | 29 | 54 | 9 / 2 | 480 / 2200 | Long suppressor; noise radius 8 |
| `ossuary` / Ossuary Hand Cannon | 1430 | 57 | 58 | 6 / 3 | 920 / 3300 | Gold cylinder and cocked hammer |
| `turnstile` / Turnstile Snub Revolver | 1420 | 42 | 36 | 5 / 2 | 620 / 2400 | Stub barrel, salvage cylinder |
| `cinder` / Cinder Machine Pistol | 1410 | 24 | 40 | 20 / 2 | 260 / 2500 | Extended grip magazine, wire stock |
| `tunnelrat` / Tunnel Rat Patrol Carbine | 1400 | 35 | 58 | 16 / 2 | 430 / 2700 | Narrow patrol receiver, skeletal stock |
| `blackbox` / Black Box Burst Carbine | 1390 | 33 per shot | 62 | 21 / 2 | 1650 / 3100 | Tracked three-shot burst, bullpup carry handle |
| `quarantine` / Quarantine Battle Carbine | 1380 | 44 | 70 | 14 / 3 | 600 / 2900 | Stationary ×1.3; broad receiver, solid stock |
| `signal` / Signal Lost Marksman | 1370 | 53 | 104 | 7 / 3 | 1050 / 3200 | Beyond 32 blocks ×1.35; scope, skeletal stock |
| `gallows` / Gallows Bolt Rifle | 1360 | 72 | 108 | 5 / 4 | 1500 / 3400 | Long wood stock, bolt handle, scope |
| `watchtower` / Watchtower Anti-Materiel | 1350 | 86 | 112 | 3 / 6 | 2100 / 4400 | Braced, 2 bodies; wide scope and bipod |
| `whiteout` / Whiteout Covert Rifle | 1340 | 61 | 98 | 6 / 4 | 1350 / 3300 | Noise radius 8; scoped suppressor and pale shroud |
| `bellringer` / Bellringer Double Barrel | 1330 | 10 × 12 | 26 | 2 / 3 | 1350 / 2600 | Paired barrels and break-action hinge |
| `lockjaw` / Lockjaw Breaching Pump | 1320 | 8 × 11 | 32 | 5 / 3 | 1150 / 3100 | Pump handguard; narrower spread |
| `choir` / Choir Three-Barrel Verdict | 1310 | 12 × 12 | 24 | 3 / 5 | 1700 / 3600 | Triangular three-barrel cluster |
| `ashfall` / Ashfall Drum Shotgun | 1300 | 8 × 10 | 30 | 8 / 4 | 1000 / 4100 | Wide drum magazine and skeletal shoulder stock |
| `nullpoint` / Null Point Needle Rail | 1290 | 68 | 92 | 4 / 5 | 1650 / 3600 | 3 bodies, 80% retained; slim twin rails |
| `cenotaph` / Cenotaph Siege Rail | 1280 | 96 | 106 | 2 / 8 | 2450 / 4700 | 3 bodies, 80% retained; heavy accelerator frame |
| `witchlight` / Witchlight Arc Projector | 1270 | 3 × 27 | 44 | 6 / 5 | 1400 / 3400 | 2 bodies, 65% retained; green chamber and fork |
| `stormcoil` / Stormcoil Induction Lance | 1260 | 74 | 76 | 4 / 6 | 1850 / 4100 | 4 bodies, 70% retained; exposed coil bands |
| `hexbreaker` / Hexbreaker Reliquary Beam | 1250 | 2 × 39 | 64 | 5 / 5 | 1550 / 3800 | 2 bodies, 65% retained; raised relic cross and paired rails |
| `pallbearer` / Pallbearer Belt Cannon | 1240 | 49 | 60 | 20 / 4 | 700 / 5000 | Braced, 2 bodies; belt feed, box and bipod |
| `ironpsalm` / Iron Psalm Rotary Gun | 1230 | 34 | 48 | 28 / 3 | 380 / 5200 | Braced; rotary barrel cluster and ammunition box |
| `deadfrequency` / Dead Frequency Pulse Rifle | 1220 | 45 per shot | 74 | 15 / 4 | 1900 / 4200 | Tracked three-shot burst, short-stock accelerator |

Braced guns deal ×0.7 damage while moving and retain 75% per body. Black Box and Dead Frequency use Tempest's existing bounded scheduler: at most two follow-up tasks, 11 ticks apart, with slot/serial/world/round/authentication revalidation. They charge only emitted shots. Every ray still stops at native solid collision geometry and unloaded terrain. Occult/rail/arc labels are weapon identities, not permission to bypass damage events or destroy blocks. No explosion, block edit, immunity reset, glow or light-emission behavior was added.

Stationary means velocity magnitude below 0.08 blocks/tick. Tempest charges only shots actually emitted, validates the serial, slot, world, current rounds and authentication before each scheduled shot, and stops on item/inventory changes or travel. Its 11-tick spacing preserves native immunity behavior. Native immunity can still suppress damage when any gun fires rapidly at the same victim; it is never forcibly reset. Penetration crosses bodies, never solid geometry. Supplied damage is aggregated once per victim for simultaneous pellet volleys. Rays stop at unloaded chunk neighborhoods, with a maximum 448 quarter-block steps for Longwatch.

Gun model damage IDs on unbreakable diamond hoes: original 1560/1550/1540; Whisper 1530, Tempest 1520, Bastion 1510, Longwatch 1500, Sunlance 1490, Adjudicator 1480, Cyclops 1470, Frostbite 1460. Eight new silhouettes include a suppressor, bullpup carry handle, drum-fed braced cannon, long scope/bolt rifle, exposed plasma rails, thumbhole marksman stock, wide pump scattergun, and forked flechette accelerator.

## Melee

Normal diamond-sword hits are scaled from their native 7-damage basis, retaining native attack charge, critical hits, enchantment contributions and damage protection. The listed damage assumes a plain full-strength hit before armor. A per-player recovery gate also prevents switching weapons to bypass recovery. Native sword reach and invulnerability remain intact; there are no extra damage calls, forced health deductions, potion replacements, or manufactured drops.

| ID / name | Damage / recovery | Specialty |
| --- | --- | --- |
| `trench_blade` / Trench Blade | 10 / 350 ms | Sneaking ×1.4 |
| `breacher_axe` / Breacher Axe | 18 / 1100 ms | Targets with 12+ armor ×1.4 |
| `mono_katana` / Monofilament Katana | 15 / 550 ms | Consistent fast cuts |
| `shock_baton` / Shock Baton | 12 / 650 ms | Zombies/skeletons ×1.35 |
| `gravity_maul` / Gravity Maul | 26 / 1800 ms | Stationary ×1.25 |
| `reaper_scythe` / Reaper Scythe | 19 / 1250 ms | Targets below 40% health ×1.3 |
| `thermal_machete` / Thermal Machete | 16 / 800 ms | Already-burning targets ×1.35 |
| `sentinel_spear` / Sentinel Spear | 17 / 1000 ms | Hits at distance ≥2.4 blocks ×1.3 |

These use distinct cuboid silhouettes selected at unbreakable diamond-sword damage 1530 down to 1460 in table order.

### Appended melee weapons

These append diamond-sword model bands 1450 through 1300. Perks use the existing cancellable melee event, native attack charge and native reach, with the same per-player recovery gate. No specialty adds a second hit, health deduction, splash attack or fire placement.

| ID / name | Band | Damage / recovery (ms) | Specialty / silhouette |
| --- | --- | --- | --- |
| `gravespike` / Gravespike Trench Dirk | 1450 | 12 / 450 | Sneaking ×1.4; long thin dirk |
| `railpick` / Railworker's Armor Pick | 1440 | 17 / 1050 | Armor ≥12 ×1.4; side spike and pick head |
| `wardcleaver` / Ward Cleaver | 1430 | 15 / 850 | Zombies/skeletons ×1.35; broad weighted plate |
| `pilgrim_lance` / Ash Pilgrim's Lance | 1420 | 20 / 1250 | Distance ≥2.4 blocks ×1.3; long shaft and pennant |
| `cautery_sabre` / Cautery Sabre | 1410 | 18 / 950 | Burning targets ×1.35; swept blue edge |
| `suture_sickle` / Suture Harvest Sickle | 1400 | 14 / 700 | Target below 40% health ×1.3; inward hooked fang |
| `tollhammer` / Last Toll Bell Hammer | 1390 | 28 / 2000 | Stationary ×1.25; flared bell-shaped head |
| `rebar_sword` / Quarantine Rebar Greatsword | 1380 | 22 / 1350 | Consistent; wide blade and welded teeth |
| `vesper_dagger` / Vesper Ritual Dagger | 1370 | 11 / 400 | Sneaking ×1.4; short double-guard ritual blade |
| `hollow_halberd` / Hollow Watch Halberd | 1360 | 23 / 1500 | Armor ≥12 ×1.4; long axe, spear and rear spike |
| `ossuary_flail` / Ossuary Chain Flail | 1350 | 21 / 1400 | Zombies/skeletons ×1.35; linked chain and bone head |
| `ember_falchion` / Ember Procession Falchion | 1340 | 19 / 1150 | Burning targets ×1.35; broad red cutting edge |
| `mourning_glaive` / Mourning Station Glaive | 1330 | 18 / 1100 | Distance ≥2.4 blocks ×1.3; long swept pole blade |
| `altar_mallet` / Silent Altar Mallet | 1320 | 24 / 1700 | Stationary ×1.25; squared green head and white binding |
| `execution_sword` / Final Verdict Execution Sword | 1310 | 25 / 1800 | Target below 40% health ×1.3; squared execution tip |
| `wire_whip` / Razorwire Scourge | 1300 | 13 / 600 | Consistent fast cuts; three barbed strands, vanilla reach |

## Exoskeletons

Each set requires all FOUR verified pieces in their correct equipped slots. Every piece is diamond armor with an exact model band and persistent server-issued mark/serial. Carrying pieces, partial sets, mixed sets, or forged display names grant no perks.

| Set | Exact piece IDs | Full-set perks and tradeoff |
| --- | --- | --- |
| Bulwark | `bulwark_helmet`, `bulwark_chestplate`, `bulwark_leggings`, `bulwark_boots` | +4 armor, +0.35 knockback resistance, −0.015 movement speed |
| Ranger | `ranger_helmet`, `ranger_chestplate`, `ranger_leggings`, `ranger_boots` | +0.025 movement speed, +0.4 attack speed, −2 armor |
| Spectre | `spectre_helmet`, `spectre_chestplate`, `spectre_leggings`, `spectre_boots` | +0.015 movement speed, +3 attack damage, −3 armor |
| Hazmat | `hazmat_helmet`, `hazmat_chestplate`, `hazmat_leggings`, `hazmat_boots` | +2 armor, +0.15 knockback resistance, −0.3 attack speed; fire/lava damage ×0.65, magic/poison ×0.75 |

Exoskeleton crafting is intentionally a late-game undertaking. Every complete set costs **8 diamond blocks, 8 iron blocks, 6 gold blocks, 4 Nether Stars, and 4 set-specific vanilla cores**. Bulwark uses obsidian, Ranger uses emerald blocks, Spectre uses Eyes of Ender, and Hazmat uses slime blocks. No custom item is accepted as a substitute. Exact piece grids are available in the crafting-table recipe browser.

Normal movement base is 0.1, so 0.025 is 25% of that base, not a replacement of a player's current speed. Perks use deterministic, separately owned UUID attribute modifiers. No potion is added, removed or overwritten, and no attribute base value is changed. Cleanup removes only those exact owned UUIDs. Full sets are checked each tick and before damage; inventory events, death, quit, world travel, mode changes and disable remove owned modifiers. Join/start also clean stale owned modifiers after abnormal shutdown. No max-health, regeneration, flight, ammo generation or item-drop perks are used.

Native Minecraft 1.12 item-model overrides support unique armor inventory/held/dropped models; worn armor still uses the vanilla diamond armor silhouette/texture. This pack does not claim per-item worn armor rendering, which would require a different client mechanism. Model damage for Bulwark/Ranger/Spectre/Hazmat is 10/20/30/40 on each corresponding diamond armor item.

## Materials and supplies

| ID / name | Base | Function |
| --- | --- | --- |
| `alloy_plate` / Tempered Alloy Plate | Iron ingot | With Power Cell: 48 cartridges |
| `weapon_core` / Ancient Weapon Core | Quartz | With Military Salvage: 4 alloy plates |
| `power_cell` / Sealed Power Cell | Prismarine shard | Ammunition and coolant crafting |
| `ballistic_fiber` / Ballistic Fiber | String | With ordinary golden apple: Trauma Kit |
| `trauma_kit` / Trauma Kit | Magma cream | Right-click: up to 8 health, 20-second shared supply cooldown |
| `field_ration` / Expedition Field Ration | Clay ball | Right-click: up to 6 food and 3 saturation, 10-second shared cooldown |
| `coolant_injector` / Coolant Injector | Slimeball | Right-click: extinguish fire, 15-second shared cooldown |
| `sanitized_flesh` / Sanitized Flesh | Cooked beef | Furnace-smelt rotten flesh; right-click: up to 8 food and saturation, never sickens |

Power Cell + ordinary slimeball makes a Coolant Injector. All four new recipes are shapeless, one ingredient per occupied slot; one ordinary click with empty cursor. Preview and pickup verify tagged input identity and material. No shift, number-key, drop, or creative craft. Canceled healing/food events consume nothing, unnecessary supplies are retained, and inventory/authentication are rechecked after callbacks. Supplies never remove existing potions. Weapon Core is quartz, never a Nether Star; original gun recipes also reject tagged substitutes in their vanilla ingredient slots. Sanitized Flesh is furnace-only (no crafting grid), carries no shared cooldown, and can never inflict Hunger — rotten flesh keeps its vanilla risk.

All equipment actions require an authenticated, living survival/adventure player in the configured apocalypse overworld, Nether, End, or the exact world `jaspr_backrooms`. Equipment eligibility uses `Arsenal.equipmentWorld`; `ApocalypsePlugin.enabledWorld` remains scoped to existing world-generation/siege behavior. The liminal-world owner does not need to broaden that global predicate for equipment.

## Sentry turret

| ID / name | Base | Function |
| --- | --- | --- |
| `sentry_turret` / Sentry Turret | Iron pickaxe, model 100 | Placeable automated defense; hostile mobs by default |

Crafting is shaped and available in both Survival and Creative, using one ordinary click with empty cursor: 4 plain iron ingots around 1 plain iron block (`" I " / "IBI" / " I "`). Preview and pickup verify exact plain-vanilla ingredients; tagged substitutes are rejected, and shift/number-key/drop clicks are denied like firearms. The issued item is a marked, stackable dispenser (model 0, no serial) listed in the Creative catalogue `block` category (Building Blocks tab, searchable as turret/sentry/defense).

Placing the item records a turret (owner, default settings) at a vanilla dispenser body topped by a tracking head: a small invisible marker armor stand wearing a six-times-effective turret model. The 1.12.2 display transform is capped at 4x, so the remaining 1.5x is baked into the cuboids; a calibrated asset base offset plus 1.1-block marker anchor puts the model's base plate directly on the dispenser top while keeping the marker in open-air lighting. The model's local negative-Z barrel is rendered with the target-forward head transform; the head yaw is calculated from the same body muzzle origin used by the shot, so the barrel faces its locked target instead of being inverted. It pitches its head with elevation and sweeps slowly while idle. Breaking, blasting, or sneak + right-clicking collects the item back as a fresh marked single with no duplication. Right-clicking the body opens a settings inventory: enabled, target hostiles/passives/players, range 12/24/36, fire rate slow/normal/fast (16/12/8 damage at ~1.1/0.6/0.35s), priority nearest/weakest/strongest, plus owner/kill readout. Placement works in Creative (catalogue-issued) and survival (crafted). Turrets persist in `plugins/JasprApocalypse/turrets.yml`, never load chunks, cap at 6 per player and 60 total by default (`sentry` config section), need no ammunition, and their shots use cancellable damage events, block-step line of sight, world PvP gating for player targets, and short-range siege noise like gunfire. The issued item is a marked, unbreakable, serialized single (model 100, like firearms and melee).

## Candidate validation and delivery

```powershell
./scripts/build-apocalypse.ps1
node --test tests/apocalypse-assets.test.cjs
node tests/equipment-smoke.cjs --paper --java-home="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
node tests/apocalypse-smoke.cjs --paper --java-home="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
node scripts/build-apocalypse-pack.cjs --output candidate/apocalypse/jaspr-apocalypse.zip
node scripts/merge-apocalypse-assets.cjs
```

The equipment runtime probe covers catalogue/serialization/forgery, original and new recipe validation, anvil and repair input NBT preservation, AuthMe, creative/mixed/partial-set rejection, preservation of other modifiers and potions, supplies and cancellation, all firearm triggers, slab/stair/fence/glass rays, unloaded boundaries, timed reload, burst completion/cancellation, and lifecycle restart. It uses real Paper/NBT/recipes/collision/scheduling and scripted Player/Attribute interfaces. It is not a network-client combat or animation test. The original smoke suite also exercises actual world/chest persistence and now checks the shared siege edit budget, including cancelled attempts.

Asset tests validate 90 model files, 9,886 durability/unbreakable cases, 140 gun held-orientation cases and 96 melee cases, distinguish every weapon's geometry, resolve against actual client textures, and verify EPK merge idempotence and unchanged unrelated entries. The legacy contract freezes all 45 non-selector models byte-for-byte and all original 11 gun/8 melee specifications. Only the diamond-hoe, diamond-sword and iron-pickaxe selectors are extended; every old band and vanilla fallback remains valid. The sentry selector is covered by its own durability-emulation test over all 252 iron-pickaxe states. New gun models have 13–24 cuboids, melee models 6–17. Each gun points down local −Z, has sights +Y and grips −Y; both hands have explicit transforms. Melee tips remain +Y in model space and point upright/forward in hand. Intentional 180-degree muzzle and upside-down mutations are rejected in all gun hand contexts. Manual in-client sight alignment and device-specific clipping still require the browser fixture.

The cuboid source is `apocalypse-pack/arsenal-expansion.cjs`; checked-in JSON must match it exactly. It contains no I/O, random geometry, tick loop or texture downloads. The actual in-memory merge adds 19,769 bytes (19.3 KiB) to the pre-expansion compressed browser assets while preserving 5,786 unrelated resources. No EPK was written to the site by this slice.

Run `node tests/arsenal-expansion-runtime.cjs` for the isolated expansion gate. It compiles only the three owned Java files into a UUID fixture copy of the existing candidate jar, then uses the real Paper equipment probe. The probe covers all 85 catalogue identities, serialization, forged/stacked/wrong-band/invalid-magazine rejection, all 35 triggers and hit aggregation/wall blocking, body attenuation, all reload cancellation paths, partial heavy reload cost, old/new three-shot bursts, full-set perks, supplies, AuthMe and lifecycle cleanup. Hit recipients and Player interfaces are scripted; Paper collision, NBT, recipes and scheduler are real. Neither the shared candidate jar nor production files are changed.

Candidate outputs only: `candidate/apocalypse/JasprApocalypse.jar`, `candidate/apocalypse/assets.epk`, `candidate/apocalypse/jaspr-apocalypse.zip`, and `candidate/apocalypse/asset-merge-report.json`. Test fixtures/results stay under `candidate/apocalypse-smoke-*`. No production plugin/config/site/world files are changed or deployed by this slice.
