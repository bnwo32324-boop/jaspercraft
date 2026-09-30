# light-lab — is this world lit when it is first sent?

A loopback-only test (never touches the live server) for the question in `LIGHT_UPDATE.md`: does a world send its new
chunks with their block light, or without? A throwaway Paper (the live server jar, the live `spigot.yml`/`paper.yml`/
`bukkit.yml`, the live seed) loads the plugins from `--jars`; a mineflayer bot visits never-seen terrain in each world,
goes somewhere else, and comes back.

For every visit it counts the light-emitting blocks (glowstone, lava, lanterns, torches, ...; minecraft-data
`emitLight >= 7`) whose own block-light value is below their emission: **dark emitters**.

| column | meaning |
| --- | --- |
| `first` | the first full copy of each chunk as it arrived on the wire — what a player sees on a first visit |
| `held` | what the bot holds once the terrain has gone quiet (partial re-sends applied) |
| `server` | the server's own light arrays at that moment (LightProbe `census`) |

A chunk sent before its light was worked out has dark emitters in `first` and none on a revisit. The packets are parsed
here (`parseChunkPacket`), not by prismarine-chunk, which does not read the light nibbles in their wire order.

## Run it

```bash
bash scripts/light-lab/build-probe.sh            # once: candidate/light-lab/LightProbe.jar (fixture-only helper, never deployed)
node scripts/light-lab/light-lab.cjs --tag after --worlds nether,atlas,ruins --sites 4
node scripts/light-lab/light-lab.cjs --jars <copy of the live jars> --tag before --worlds nether,atlas,ruins --sites 4
```

- `--jars` defaults to `server/plugins` (what is about to ship). `--worlds` takes `overworld, nether, end, atlas, ruins, backrooms`.
- `--ticktest nether,atlas`: idle tick time with the flag off and on, alternating (the flag's own cost).
- `--reload atlas,ruins`: leave, wait for the plugin to unload the world, come back: is the flag set again on the new world?
- `--randomtest atlas`: do the game's random light checks near a player light a lamp the server never lit?
- `--preflag off` forces the flag off through the probe (the Backrooms run with it off is the positive control: a fresh visit
  arrives about 97% dark, a revisit about 12%).
- Needs mineflayer: `JASPR_NODE_MODULES=<a node_modules that has it>` (falls back to the perf sandbox's copy). The live server
  folder it copies the jar and settings from is `JASPR_LIVE_SERVER` (default: the live checkout's `server/`).
- Results: `candidate/light-lab/runs/<tag>-<time>/results.json` and `console.log` (per-visit numbers, flags, log lines).
  Lean: one Paper (2 GB, 3 cores, below-normal priority), one headless bot, a few minutes per world. It stops what it started.

## Reading it

- `first dark` high and `revisit dark` low: a sending problem; the per-world flag fixes it (`spigotConfig.randomLightUpdates`).
- `revisit dark` high too: the server never lit those emitters (a generator that draws chunks from ChunkData hands the server
  blocks with no light; the game's first light pass then reaches mostly sources under a roof that let light through: in Atlas 85%
  of the lamps in the open and 71% of the solid ones, such as sea lanterns, stay dark). That is not a sending problem; fixing it
  changes server-side light (mob spawning, Drownhollow's Dread), so it is an owner decision.
- `cols` after settling: 81 at view distance 4 with the flag off, about 49-56 with it on in a sky-lit world and 72 in the Nether
  (the outermost ring waits until it is populated and lit, as in the game itself).
