# JasprMutants wire protocol (server plugin <-> client stage), version 1

Authoritative contract between the Paper plugin `JasprMutants` and the browser client stage `JASPR_MUTANTS`. Both sides
implement exactly this; a change needs both sides and this file in the same change. Owner's rule behind every choice:
"I don't want there to be any difference between the mod in-game and if I was running it through Forge."

## 0. Principle: the same objects on both sides, like Forge
Forge runs the mod's real classes on server AND client and syncs registry ids (entities, items, sounds, particles) from
server to client at login. We do the same with FIXED id tables (below) instead of a handshake:
- The server registers the mod's real entity, item, sound-event and particle objects in Paper's registries under these ids.
- The client stage registers client-side twins (JS classes translated from the mod's client paths: entities, items,
  particles, sounds) under the same ids, so vanilla packets (metadata, status bytes, equipment, inventories, sounds,
  particles, destroy, movement) just work, and the mod's own client logic (animations, particles, the tracker GUI,
  spider-pig riding, held blocks, teleports) runs as it does under Forge.
- Only the entity SPAWN uses a custom message (Forge does the same: FML's EntitySpawnMessage), because it must carry the
  mod's `IEntityAdditionalSpawnData` and `IThrowableEntity` thrower atomically and because Paper's tracker cannot spawn
  non-vanilla classes.

## 1. Id tables

### 1.1 Entities (network type id = 210 + the mod's registration index; registry key `mutantbeasts:<name>`)
| id | name | Java base (server and client) | living | tracker range, update freq, velocity | egg colours |
| --- | --- | --- | --- | --- | --- |
| 210 | body_part | Entity | no | 64, 10, true | - |
| 211 | chemical_x | EntityThrowable | no | 160, 10, true | - |
| 212 | endersoul_clone | EntityMob | yes | 80, 3, true | 15027455 / 15027455 |
| 213 | creeper_minion | EntityShoulderRiding (EntityTameable) | yes | 80, 3, true | 894731 / 0xB7B7B7 |
| 214 | creeper_minion_egg | Entity | no | 160, 20, true | - |
| 215 | endersoul_fragment | Entity | no | 64, 10, true | - |
| 216 | mutant_arrow | Entity | no | 80, 3, true | - |
| 217 | mutant_creeper | EntityCreeper | yes | 80, 3, true | 5349438 / 11013646 |
| 218 | mutant_enderman | EntityMob | yes | 80, 3, true | 0x161616 / 8860812 |
| 219 | mutant_skeleton | EntityMob | yes | 80, 3, true | 0xC1C1C1 / 6310217 |
| 220 | mutant_snow_golem | EntityGolem | yes | 80, 3, true | 0xE5FFFF / 16753434 |
| 221 | mutant_zombie | EntityMob | yes | 80, 3, true | 7969893 / 44975 |
| 222 | skull_spirit | Entity | no | 160, 20, false | - |
| 223 | spider_pig | EntityTameable (IJumpingMount) | yes | 80, 3, true | 3419431 / 15771042 |
| 224 | throwable_block | EntityThrowable | no | 64, 100, true | - |

Translation keys stay the mod's (`entity.mutantbeasts.<name>.name` via EntityEntry name `mutantbeasts.<name>`). Spawn
eggs are vanilla `spawn_egg` with `EntityTag.id = "mutantbeasts:<name>"` (the client registers egg colours for these keys).

### 1.2 Entity data keys (ids must match on both sides; parents: Entity 0-5, EntityLivingBase 6-10, EntityLiving 11,
EntityAgeable 12, EntityTameable 13-14, EntityCreeper 12-14). The Eaglercraft client hard-codes data key ids and some
vanilla ones are wrong (memory: AreaEffectCloud, boats, horses, llamas, End crystal): the client stage MUST verify the
hard-coded ids of every vanilla parent it extends (EntityAgeable/EntityTameable/EntityCreeper) equal the server's.
| entity | own keys |
| --- | --- |
| mutant_zombie | 12 LIVES VarInt, 13 THROW_ATTACK_STATE Byte |
| mutant_skeleton, endersoul_clone | none |
| mutant_creeper | (12 STATE VarInt, 13 POWERED Boolean, 14 IGNITED Boolean from EntityCreeper) 15 STATUS Byte |
| mutant_enderman | 12 ACTIVE_ARM Byte, 13 CLONE Boolean |
| mutant_snow_golem | 12 OWNER_UNIQUE_ID Optional<UUID>, 13 DATA_FLAGS Byte |
| spider_pig | (12 BABY, 13 TAMED Byte, 14 OWNER Optional<UUID>) 15 CLIMBING Boolean |
| creeper_minion | (12 BABY, 13 TAMED, 14 OWNER) 15 CREEPER_MINION_FLAGS Byte, 16 EXPLODE_STATE VarInt, 17 EXPLOSION_RADIUS Float |
| skull_spirit | 6 ATTACHED Boolean |
| throwable_block | 6 HELD Boolean |
| creeper_minion_egg | 6 CHARGED Boolean |
| endersoul_fragment | 6 TAMED Boolean |
| mutant_arrow | 6 TARGET_X Float, 7 TARGET_Y Float, 8 TARGET_Z Float, 9 SPEED Float, 10 CLONES VarInt |
| body_part, chemical_x | none |
(Ids follow the order of `EntityDataManager.createKey` calls in each class; the server port asserts these at startup and
logs `MUTANTS_DATAKEYS_MISMATCH` if Paper assigns anything else.)

### 1.3 Items (numeric item id 4000 + the mod's registration index; registry key `mutantbeasts:<name>`)
| id | name | Java class | stack | max damage (MBConfig) |
| --- | --- | --- | --- | --- |
| 4000 | chemical_x | ChemicalXItem | 1 | - |
| 4001 | creeper_minion_tracker | Item | 1 | - |
| 4002 | creeper_shard | CreeperShardItem | 1 | 32 (creeperShardCharges) |
| 4003 | endersoul_hand | EndersoulHandItem | 1 | 240 |
| 4004 | hulk_hammer | HulkHammerItem | 1 | 64 |
| 4005 | mutant_skeleton_arms | Item | 64 | - |
| 4006 | mutant_skeleton_limb | Item | 64 | - |
| 4007 | mutant_skeleton_shoulder_pad | Item | 64 | - |
| 4008 | mutant_skeleton_rib | Item | 64 | - |
| 4009 | mutant_skeleton_rib_cage | Item | 64 | - |
| 4010 | mutant_skeleton_pelvis | Item | 64 | - |
| 4011 | mutant_skeleton_skull | MutantSkeletonArmorItem(HEAD) | 1 | armour material mutant_skeleton (durability 15, protection 2/5/6/2, enchantability 9, equip sound entity.skeleton.step, toughness MBConfig) |
| 4012 | mutant_skeleton_chestplate | MutantSkeletonArmorItem(CHEST) | 1 | " |
| 4013 | mutant_skeleton_leggings | MutantSkeletonArmorItem(LEGS) | 1 | " |
| 4014 | mutant_skeleton_boots | MutantSkeletonArmorItem(FEET) | 1 | " |
Translation keys `item.mutantbeasts.<name>.name`; creative tab `mutantbeasts` (icon Chemical X, then all spawn eggs of
the mod's entities, as `MutantBeasts.CREATIVE_TAB.displayAllRelevantItems`). Item NBT is whatever the mod writes.
No new blocks.

### 1.4 Sound events (registry id 1000 + index in `MBSoundEvents` FIELD declaration order; 43 events)
1000 entity.creeper_minion.ambient, 1001 .death, 1002 .hurt, 1003 .primed, 1004 entity.creeper_minion_egg.hatch,
1005 entity.endersoul_clone.death, 1006 entity.endersoul_clone.teleport, 1007 entity.endersoul_fragment.explode,
1008 entity.mutant_creeper.ambient, 1009 .charge, 1010 .death, 1011 .hurt,
1012 entity.mutant_enderman.ambient, 1013 .death, 1014 .hurt, 1015 .morph, 1016 .scream, 1017 .stare, 1018 .teleport,
1019 entity.mutant_skeleton.ambient, 1020 .ambient.legacy, 1021 .bite, 1022 .bow_draw, 1023 .bow_shoot, 1024 .death,
1025 .death.legacy, 1026 .hurt, 1027 .hurt.legacy, 1028 .jump, 1029 .punch, 1030 .step, 1031 .step.legacy,
1032 entity.mutant_snow_golem.death, 1033 .hurt,
1034 entity.mutant_zombie.ambient, 1035 .attack, 1036 .death, 1037 .grunt, 1038 .hurt, 1039 .roar,
1040 entity.spider_pig.ambient, 1041 .death, 1042 .hurt.
- Server: register the `MBSoundEvents` FIELD instances themselves (the mod plays those instances; its
  `registerSoundEvents` creates different instances and omits the legacy/bite/bow/jump/punch ones, which only works in
  singleplayer where packets are not serialised). Name `mutantbeasts:<event>`.
- Client: SoundEvent with the same id and the name `minecraft:jaspr.mutants.<event>`; `assets/minecraft/sounds.json`
  gets `jaspr.mutants.<event>` entries translated from the mod's sounds.json (files under
  `minecraft:jaspr/mutants/<path>`, vanilla "type":"event" references kept, attenuation/weights/volume/pitch kept).
- Licence: the non-legacy Mutant Skeleton files are All Rights Reserved and are NEVER shipped. MBConfig
  `mutantSkeletonLegacy{Ambient,Death,Hurt,Step}Sound = true` (server) selects the AGPL legacy files for those four.
  1019/1024/1026/1030 also point at the legacy files on the client. 1021 bite, 1022 bow_draw, 1023 bow_shoot, 1028 jump,
  1029 punch have no free version: the client maps them to vanilla stand-in events (e.g. bow_shoot -> entity.skeleton.shoot,
  punch -> entity.player.attack.strong, jump -> entity.skeleton.step at low pitch, bite -> entity.skeleton.hurt at low
  pitch, bow_draw -> item.armor.equip_chain) - the one documented, licence-forced difference.

### 1.5 Particles (EnumParticleTypes, ids from MBConfig defaults)
100 `mutantbeasts:endersoul` (ignoreRange true, 0 args), 101 `mutantbeasts:skull_spirit` (ignoreRange true, 0 args).
Both sides add these enum constants to EnumParticleTypes' id and name maps (as `MBParticles.register` does); the client
registers the EndersoulParticle / SkullSpiritParticle factories for them.

## 2. Channel `jaspr:mutants` (JasperCraft framing; binary PacketBuffer; first byte = op)

### op 0 HELLO (client -> server, once per connection, from handleJoinGame like the wide stage)
`byte 0, VarInt protocolVersion (=1), String clientBuild (<=64 chars)`. The server marks the player as stage-capable,
logs `MUTANTS_CLIENT_HELLO version=1` (no names beyond the player name the server already logs).

### op 1 SPAWN (server -> client; replaces the vanilla spawn packet for EVERY mod entity, living or not)
```
byte    1
VarInt  entityId
long    uuidMost, long uuidLeast
VarInt  typeId                    (table 1.1)
double  x, y, z
byte    yaw, pitch, headYaw       (angle * 256 / 360, as vanilla spawn packets)
short   motionX, motionY, motionZ (motion * 8000, clamped to +-3.9, as vanilla)
VarInt  throwerId + 1             (IThrowableEntity thrower; 0 = none)
...     data manager entries      (vanilla EntityDataManager.writeEntries: all entries, 0xFF terminated)
VarInt  spawnDataLength, then that many bytes of IEntityAdditionalSpawnData.writeSpawnData (0 if not implemented)
```
Client: create the type's class (`new X(world)`), set entityId, uuid, serverPos (x*4096 longs) and position/rotation,
head yaw (`rotationYawHead`, `renderYawOffset` for living), motion, thrower, apply the entries
(`setEntryValues`), call `readSpawnData`, then `addEntityToWorld(entityId, entity)` - mirroring vanilla
handleSpawnMob/handleSpawnObject and FML's spawn handler. Unknown typeId: log a diagnostic once, skip.
Later packets for the entity are vanilla (metadata, movement, head look, velocity, equipment, attributes, effects,
passengers, status, destroy).

### op 2 DATA_KEYS (reserved), ops >= 3 reserved.

## 3. Channel `mutantbeasts` (the mod's own SimpleNetworkWrapper, byte-identical to Forge)
Payload = discriminator byte + the message's `toBytes` (Netty ByteBuf, big-endian), exactly as FML's SimpleIndexedCodec:
| disc | message | direction | bytes |
| --- | --- | --- | --- |
| 0 | CreeperMinionTrackerPacket | client -> server | int entityId, byte optionsId (0 destroyBlocks, 1 alwaysRenderNameTag, 2 canRideOnShoulder), boolean value |
| 1 | HeldBlockPacket | server -> client | int entityId, int blockId (Block.getStateId), byte blockIndex |
| 2 | SpawnParticlePacket | server -> client | int particleId, double x, y, z, double offsetX, offsetY, offsetZ, int amount |
| 3 | TeleportPacket | server -> client | int entityId, long blockPos (BlockPos.toLong) |
Handlers run the mod's own handler code. The server registers the incoming channel with Bukkit's Messenger and sends with
NMS `PacketPlayOutCustomPayload` directly (no REGISTER handshake needed). The server validates incoming sizes and entity
ids (bounded reads; ignore unknown/foreign entities) and logs rejects as `MUTANTS_PACKET_REJECTED reason=...`.

## 4. Status bytes and other vanilla packets
Unchanged from the mod: `world.setEntityState(entity, b)` (MutantZombie/MutantSkeleton/MutantEnderman send `-attackID`,
3 death/explosion effects, 6/7 heart/smoke, 0 clone vanish, 30 shield break via EntityUtil ...). The client's twin class
implements `handleStatusUpdate` exactly like the mod; vanilla dispatch calls it.

## 5. Clients without the stage (stale tabs after a deploy)
They ignore `jaspr:mutants`/`mutantbeasts`, so mod entities stay invisible to them; unknown item ids read as empty
stacks. Two things would CRASH them: sound effect packets with ids 1000-1042 (null SoundEvent) and particle packets
with ids 100/101 (they degrade to barrier particles, harmless but ugly). The server therefore drops outgoing sound
packets with mod sound ids, and mod particle packets, for players that have not sent HELLO (Netty outbound filter per
player; counter in the status line). Everything else is sent as is.

## 6. Big Mobs (dungeon) scale table
Channel `jaspr:scale` (JasprDungeon `Bodies.java`) belongs to the JASPR_BIGMOBS client stage, which is live; the Mutants
stage does not consume it (its payload hook returns the message to the client untouched). A scaled mutant is drawn and
sized by Big Mobs like any other mob.

## 7. Diagnostics (both sides; privacy rules of AGENTS.md)
Server: `MUTANTS_READY entities=15 items=15 sounds=43 particles=2 recipes=.. brewing=.. lootTables=.. spawns=..`,
`MUTANTS_CLIENT_HELLO`, `MUTANTS_SPAWN_FAILED type=.. reason=..`, `MUTANTS_PACKET_REJECTED`, `MUTANTS_SOUND_FILTERED count=..`.
Client: events `jaspercraft.mutants.state` / `jaspercraft.mutants.error` (<= 12 per page; counters, type ids, short
error text) and `JasprMutantsDiagnostics.status()`.
