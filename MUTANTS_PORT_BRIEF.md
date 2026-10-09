# Mutant Creatures Legacy -> JasperCraft: port brief (read first)

## What the owner asked (2026-10-05, verbatim)
- "I also want you to use the mutant creatures mod. It should be ported to the game one-to-one. Don't recreate it or
  substitute it. It should be the actual source code, just adapted and made compatible with Jaspercraft."
- "Also, to be clear again, I don't want you to be inspired by the code or just to use it as a simple reference. I
  literally want the mod in the game fully. I don't want there to be any difference between the mod in-game and if I was
  running it through Forge."
- "once you're done porting the MutantCreatures, I want them spawning naturally in the overworld as well."
- Integration into the Dungeon Dimension is done separately (JasprDungeon calls `chat.jaspr.mutants.MutantsApi.spawn(String
  kind, Location at)` by reflection and expects a Bukkit `LivingEntity` back, or null; kinds: mutant_zombie,
  mutant_skeleton, mutant_creeper, mutant_enderman, creeper_minion, spider_pig, mutant_snow_golem).

## Source and licence
- Source: `C:\Users\AM\Downloads\MutantCreaturesLegacy-main (1)\MutantCreaturesLegacy-main` (Forge 1.12.2, MCP names,
  88 Java files, ~14.5k lines; assets under src/main/resources/assets/mutantbeasts; default config MutantBeasts.cfg).
  Release jar for reference: `C:\Users\AM\Downloads\MutantCreaturesLegacy-1.12.2-1.0.4 (1).jar`.
- Code and original assets: AGPL-3.0 (LICENSE). Keep the copyright headers, ship LICENSE with the port, keep the ported
  source in this repository (players must be able to get it: AGPL section 13; the lead adds the in-game/site source offer).
- The NEW Mutant Skeleton sounds (`sounds/entity/mutant_skeleton/*.ogg` outside `legacy/`) are All Rights Reserved
  (LICENSE_ASSETS): never copy, ship or commit them. The mod's own switches `mutantSkeletonLegacy{Ambient,Death,Hurt,Step}Sound`
  are set true; bite/bow_draw/bow_shoot/jump/punch use vanilla stand-ins (`MUTANTS_PROTOCOL.md` 1.4).

## The platform (why this is a port, not a jar drop)
- Server: Paper 1.12.2 (Spigot NMS names, `net.minecraft.server.v1_12_R1`). No Forge. Toolchain for compiling MCP-named
  source against Paper and reobfuscating to Spigot names: `C:\Users\AM\Documents\JasperCraft-Mutants\toolchain\README.md`.
- Client: the browser client is EaglercraftX 1.12.2 compiled to JavaScript by TeaVM (`site/classes.js`), patched by many
  fenced JS "stages". No Forge, no Java class loading. The mod's client-side code (entity client paths, models,
  renderers, animation API, particles, layers, the tracker GUI, item renderers, armour model) is translated faithfully into
  a JS stage, the way Mo' Bends was (`scripts/build-mobends-client.cjs`, `client-mods/mobends/`). Engine reference:
  `MUTANTS_CLIENT_INTERNALS.md` (repo root).
- Wire contract: `MUTANTS_PROTOCOL.md` (repo root). Like Forge, the same objects exist on both sides with fixed ids:
  15 entities (210-224), 15 items (4000-4014), 43 sound events (1000-1042), 2 particles (100, 101); entity spawns use the
  `jaspr:mutants` SPAWN message; the mod's four packets use channel `mutantbeasts` byte-for-byte.
- Assets go into the client archive (`site/assets.epk`) through a merge script like `scripts/build-trinket-pack.cjs`, all
  under the `minecraft` domain (the client loads no other domain): textures, item models, sounds.json entries
  `jaspr.mutants.*` with .ogg files (AGPL ones only), lang entries (the mod's en_us names/keys).

## Server plugin JasprMutants (AGPL-3.0) - `server/custom-plugins/JasprMutants/`
- Keep the mod's package (`chumbanotz.mutantbeasts...`) and classes; compile the real source with MCP names against
  `paper-mcp.jar` plus a `net.minecraftforge...` shim that implements exactly the Forge API the mod uses (ForgeEventFactory,
  ForgeHooks, MinecraftForge.EVENT_BUS fed from Bukkit/NMS hook points for the events the mod subscribes to,
  IEntityAdditionalSpawnData, IThrowableEntity, IShearable, EnumHelper (armour material, particle types),
  ObfuscationReflectionHelper with the reobf names, Config/ConfigManager over the mod's MBConfig defaults, BrewingRecipeRegistry,
  BiomeDictionary/ForgeRegistries.BIOMES views, OreDictionary lookups, the IGuiHandler/proxy split). Methods Forge adds to
  vanilla classes (overridden or called by the mod) must keep working: call the mod's overrides from the equivalent hook.
  Change mod code only where the platform forces it, mark each change `// JasperCraft port: <why>`, list all in
  `server/custom-plugins/JasprMutants/PORT_NOTES.md`.
- Registries: register the mod's entity classes (Paper EntityTypes, ids/keys of the protocol table), items (Item registry,
  ids 4000-4014), sound events (1000-1042, the MBSoundEvents FIELD instances), particle enum constants (100, 101), loot
  tables (the mod's JSON under its keys), recipes (the mod's JSON recipes as NMS IRecipe; never unlock them in the client
  recipe book), the brewing recipes, advancements (the mod's JSON), spawn eggs (vanilla spawn_egg + EntityTag id).
- Entities: save/load (chunk NBT round trip), Bukkit wrappers for every class (non-vanilla classes otherwise hit
  CraftEntity's "Unknown entity" assertion), tracking with the mod's tracker parameters through a custom tracker entry
  that sends the protocol's SPAWN message instead of any vanilla spawn packet (Paper's EntityTrackerEntry throws for
  unknown non-living classes), plus the per-player filter of section 5 of the protocol.
- Natural spawning in the main overworld (`world`) exactly as the mod configures it (copySpawnsForMutant weights, MBConfig
  global spawn rate and rules), and not in other worlds (realms, dungeon runs) unless the dungeon asks through MutantsApi.
- `chat.jaspr.mutants.MutantsApi.spawn(String kind, Location at)`.
- Logs: `MUTANTS_READY ...` (add it to the deployer's READY list at integration), privacy-safe failure lines.

## Client stage JASPR_MUTANTS - `client-mods/mutants/`, `scripts/build-mutants-client.cjs`, `scripts/build-mutants-pack.cjs`
- Twin classes for the 15 entities (client paths of the mod's entity classes: data keys, ticking, animation state,
  handleStatusUpdate, readSpawnData, interactions such as the tracker GUI and spider-pig riding) and the 15 items (client
  paths: use actions, armour model and texture, the Endersoul Hand item renderer, tooltips), registered under the protocol
  ids, plus the SPAWN handler, the `mutantbeasts` channel handlers, sounds, particles (100/101), spawn-egg colours, the
  creative tab.
- Renderers and models translated from the mod (animationapi Animator/JointModelRenderer/Transform/IAnimatedEntity,
  ScalableModelRenderer, every model, renderer and layer), using the engine's ModelRenderer, GlStateManager and texture
  binding (resumable first bind).
- Also the dungeon's Big Mobs render scale (`jaspr:scale`, protocol section 6).
- Fenced, removable, rebuilt from the LIVE classes.js, parse-checked, `unpatch(build(x)) === x`, with diagnostics like
  Mo' Bends'. Installed as the outermost stage.
