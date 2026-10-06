# Mutant Creatures Legacy -> JasperCraft: port brief (read first)

## What the owner asked (2026-10-05, verbatim)
- "I also want you to use the mutant creatures mod. It should be ported to the game one-to-one. Don't recreate it or
  substitute it. It should be the actual source code, just adapted and made compatible with Jaspercraft."
- "Also, to be clear again, I don't want you to be inspired by the code or just to use it as a simple reference. I
  literally want the mod in the game fully. I don't want there to be any difference between the mod in-game and if I was
  running it through Forge."
- "once you're done porting the MutantCreatures, I want them spawning naturally in the overworld as well."
- Integration into the Dungeon Dimension is done separately (JasprDungeon calls `chat.jaspr.mutants.MutantsApi.spawn(String
  kind, Location at)` by reflection and expects a `LivingEntity` back; kinds: mutant_zombie, mutant_skeleton, mutant_creeper,
  mutant_enderman, creeper_minion, spider_pig, mutant_snow_golem).

## Source and licence
- Source: `C:\Users\AM\Downloads\MutantCreaturesLegacy-main (1)\MutantCreaturesLegacy-main` (Forge 1.12.2, MCP names,
  88 Java files, ~14.5k lines; assets under src/main/resources/assets/mutantbeasts). Release jar for reference:
  `C:\Users\AM\Downloads\MutantCreaturesLegacy-1.12.2-1.0.4 (1).jar`.
- Code and original assets: AGPL-3.0 (LICENSE). Keep the copyright headers, ship LICENSE with the port, keep the ported
  source in the repository (players must be able to get it: AGPL section 13). The NEW Mutant Skeleton sounds
  (assets/.../sounds/entity/mutant_skeleton/*.ogg outside legacy/) are All Rights Reserved (LICENSE_ASSETS): never ship
  them; the mod's own config switches `mutantSkeletonLegacy{Ambient,Death,Hurt,Step}Sound` select the original (AGPL)
  legacy sounds: set them true.

## The platform (why this is a port, not a jar drop)
- Server: Paper 1.12.2 (Spigot NMS names, net.minecraft.server.v1_12_R1). No Forge. Toolchain for compiling MCP-named
  source against it and reobfuscating: `C:\Users\AM\Documents\JasperCraft-Mutants\toolchain\README.md`.
- Client: the browser client is EaglercraftX 1.12.2 compiled to JavaScript by TeaVM (`site/classes.js`), patched by many
  JS "stages". No Forge, no Java class loading. The mod's client code (models, renderers, animation API, particles, layers,
  the tracker GUI, item renderers) is translated line by line into a JS stage, the way Mo' Bends was
  (`scripts/build-mobends-client.cjs`, `client-mods/mobends/`). Reference for the client internals:
  `C:\Users\AM\Documents\JasperCraft-Mutants\docs\CLIENT_INTERNALS.md`.
- Assets go into the client archive (`site/assets.epk`) through a merge script like `scripts/build-trinket-pack.cjs`:
  textures, models, sounds.json entries and .ogg files (legacy skeleton sounds only), lang names.
- New items cannot be new client Item ids: each mod item is a vanilla carrier item with NBT identity and a damage-band or
  NBT-skin model override (see `scripts/trinket-art`, `scripts/build-nbt-skin-client.cjs`, the realm armoury's worn-armour
  hook in `scripts/build-armory-client.cjs`); the item's server behaviour is the mod's own code.

## Server plugin JasprMutants (AGPL-3.0)
- Keep the mod's package (`chumbanotz.mutantbeasts...`) and classes; compile the real source with MCP names against
  `paper-mcp.jar` plus a small `net.minecraftforge...` shim package that implements exactly the Forge API the mod uses on
  the server (ForgeEventFactory, ForgeHooks, MinecraftForge.EVENT_BUS posting the few events the mod subscribes to from
  Bukkit events, IEntityAdditionalSpawnData, IThrowableEntity, EnumHelper for the armour material, ObfuscationReflectionHelper
  with the reobf names, config as the mod's own MBConfig defaults loaded from a YAML file, OreDictionary lookups). Change mod
  code only where the platform forces it, and mark each change with `// JasperCraft port:` and why.
- Entities: register every mod entity (mutant_zombie, mutant_skeleton, mutant_creeper, mutant_enderman, mutant_snow_golem,
  spider_pig, creeper_minion, endersoul_clone, body_part, chemical_x, creeper_minion_egg, endersoul_fragment, mutant_arrow,
  skull_spirit, throwable_block) with the server so they save, load, track (the mod's tracker ranges/frequencies) and spawn
  packets reach clients in a form the client stage turns into the mod's entities (agree the exact protocol with the client
  port in `MUTANTS_PROTOCOL.md` (repo root): network type ids, metadata keys the vanilla client must not apply, IEntityAdditionalSpawnData
  bytes, the mod's four packets (CreeperMinionTracker server-bound, HeldBlock, SpawnParticle, Teleport client-bound) as
  plugin channel `mutantbeasts:main` with the mod's own message ids and byte layouts).
- Items, recipes (assets/.../recipes), brewing (SpecialBrewingRecipe: Chemical X), loot tables (assets/.../loot_tables),
  advancements (assets/.../advancements) as close to the mod as the platform allows; spawn eggs; the creeper minion tracker.
- Natural spawning in the overworld as the mod configures it (MBConfig global spawn rate and per-mutant probabilities,
  biome rules via the shim's BiomeDictionary over vanilla biomes), and never inside the dungeon's run worlds (the dungeon
  places its own) unless asked.
- `chat.jaspr.mutants.MutantsApi.spawn(String kind, Location at)` for the dungeon.
- Logs: MUTANTS_READY with counts; privacy-safe failure lines.

## Client stage JASPR_MUTANTS
- Translate the mod's client package faithfully: animationapi (Animator, JointModelRenderer, Transform, IAnimatedEntity),
  models (all of client/model), renderers and layers (client/renderer/entity, MBEntityLayerOnShoulder, LayerCreeperCharge),
  particles (EndersoulParticle, SkullSpiritParticle), the item stack renderer (Endersoul Hand), the tracker screen, the
  client event handler and proxy behaviour (e.g. the mutant enderman's view effects), using the engine's own ModelRenderer,
  GlStateManager and texture binding where they exist.
- Also carry the dungeon's Big Mobs render scale (`jaspr:scale` table: "entityId:hundredths,..." -> draw that mob scaled and
  give its client hitbox the same size), since both touch the same render hooks.
- Fenced, removable, rebuilt from the LIVE classes.js, parse-checked, with diagnostics like Mo' Bends'.
