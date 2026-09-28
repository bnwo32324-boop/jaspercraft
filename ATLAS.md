# Atlas, the Divided Realm (JasprAtlas)

**TL;DR:** a new dimension reached through a quartz gate whose surface is half blue and half black.
- **The Asterian Concord:** a luminous, Greek-inspired, hyper-advanced civilization in the west.
- **The Cinder Dominion:** an ash-black tyranny in the east, ruled by four Ash-Crowned witch-kings under the Pyrarch.
- **The adventure:** learn from the Concord how each tyrant can be broken, cross the frontier, free the enslaved, break the
  four crowns, and bring the Hearthstar's light to the Cinder Heart. Liberation visibly heals the land.
- **Getting there:** build a nether-portal-shaped frame of quartz blocks (at least 4 wide and 5 tall) in the
  overworld and kindle it with lapis lazuli and coal (use them on the frame, or throw them in). Walk back into any
  quartz gate to go home.
- **Owner tools:** `/atlas status`, `/atlas tp [place]`, `/atlas spot <kind>`, `/atlas where`, `/atlas key <id>`;
  players use `/atlas codex`.
- **Status:** see the table at the end. Built in the isolated worktree `Documents\JasperCraft-Atlas` (branch
  `claude/atlas-dimension`). The owner approved the live deploy (2026-09-27) once it is tested.

## 1. The history of Atlas (what the player uncovers)
- **Atlas** is "the Bearer", the land that bears the sky. Its sky, the Vault, rests on the **Rim** at the edge of the world
  (the world border, 2048 x 2048 blocks).
- **The Falling Light, year 1 of the Light (YL):** a singing star, the **Asterion**, fell on the central plain.
  - Its song is the root of **Harmonics**, the Concord's science of resonance. Harmonics gave them **lumen** (living
    light), **Talos automata**, heliodromes (light-gates between cities), stone that shapes itself, and medicine.
  - The founders swore the **Homonoia**, the Founding Charter: "No law shall bind the tongue of a free citizen", and "No
    single hand shall hold the Star".
- **The Sundering, YL 1101:** **Phosphoros the Lampbearer**, the greatest harmonist and Keeper of the Star, argued that
  "harmony is obedience to the right note".
  - Refused by the Synedrion, he tried to conduct the Star alone in its temple, the **Heliotheion**.
  - The Star split. Its student-keeper **Theano** carried the blue half, the **Hearthstar**, west to Astreion.
  - The black half, the **Cinder Heart**, burned without light and ate song. Where it lay the plain blackened and ash
    began to fall; the Heliotheion became **Anthrakion**.
  - Phosphoros, bound to it, became **the Pyrarch**.
- **The Year of Four Crowns, YL 1112:** the Pyrarch broke four shards from the Cinder Heart into crowns and offered each to
  a great Asterian who wanted to finish a good work at any cost. They became the **Ash-Crowned**:
  - **Kallias Strategos**, the general who wanted to end war forever. Now **the Marshal of the Teeth**.
  - **Melaina Iatra**, the healer who could not bear death. Now **the Stiller** of the Petrified Weald.
  - **Daidaros Tekton**, the engineer who wanted to end all labour. Now **the Forgemaster**.
  - **Keleos Nomothetes**, the lawgiver who wanted a world without crime. Now **the Silent Magistrate**.
- **The Withdrawal, YL 1127:** the Synedrion abandoned the eastern cities (Pellene, Aigai) to hold the **Pharos Line**
  (the Concord's records soften this). Thousands were left behind and became the **Bound**.
- **The Long Watch, YL 1127-1401 (now):**
  - Harmonics fails in the ash (the Cinder eats song), and Asterians sicken beyond the Line.
  - Strangers from other worlds are **not of the Song**; the ash does not know them. That is why the player matters.
- **The uncomfortable truths the player can find:**
  - The Withdrawal.
  - Lampsa's guild traded with the Dominion for ash-iron.
  - The Lyceum built the harmonic weapons the Dominion now uses.
  - Phosphoros believed, to the end, that he was saving Atlas. His journals in Anthrakion show it.

## 2. Geography (world coordinates; the border is at +-1024)
- **West: the Asterian Concord** (x < the frontier - 50): lush plains, cultivated end to end.
  - **Astreion**, the White City (-600, 0): the capital. Its Synedrion, Stoa of Shields, Great Library, Hearth of Theano
    and House of Return.
  - **The Gate of Strangers** (the arrival quartz gate) stands just outside Astreion's west wall (-712, 0).
  - **Lampsa** (-420, -380): engineers, lumen foundries, the Mechaneion.
  - **Hieranthe** (-420, 380): temples, the Asklepieion (the houses of healing).
  - **Mnemeia** (-840, -420): the Archive of Memory, the Founding Charter.
  - **Demes:** villages, villas, farms, orchards, shrines and tombs fill every cell.
  - **Heliodromes** (quartz rings) link the cities once discovered.
- **The Pharos Line** (x about -40): the Lampwall.
  - A white wall with **Pharoi** (light towers) every 128 blocks. Some have gone dark.
  - **The Last Watch** fort (-60, 0) stands where the Royal Road crosses.
- **The Wound** (the frontier band, about 100 blocks): a scorched no-man's-land.
  - Craters, dead automata, ruined outposts, bones.
  - The land turns from grass to ash across it.
- **East: the Cinder Dominion.** Its provinces are set around Anthrakion at T = (620, 0):
  - **The Ashen Marches** (outside the Teeth): ash plains with war camps, slave pens, watchtowers and marching roads.
    Kallias holds **the Pylon of Teeth** (the black gate), at (260, 0) in **the Teeth**, a ring wall of radius 360 around
    the interior.
  - **The Petrified Weald** (north, inside the Teeth): stone trees and the stilling-houses. Melaina's **Stilled Garden**
    is at (620, -230).
  - **The Scorched Forges** (south): lava channels, foundries and slave mines. Daidaros's **Great Engine** is at
    (620, 230).
  - **The Fallen Cities** (east): conquered Pellene and Aigai. Keleos's **Hall of Edicts** is in Pellene (850, 0).
  - **The Plateau of Cinders** (r < 110): four **Wards** around **Anthrakion**, the spiral tower crowned by the Cinder
    Heart.
- **The Royal Road** runs along z = 0 from the Gate of Strangers through Astreion and the Last Watch to the Pylon and
  Anthrakion. This is the journey into enemy land.

## 3. Peoples
- **The Concord:**
  - **Citizens** (villagers): named, with trades by calling. Most are posed at their work (scribes, priests, merchants,
    smiths); some walk.
  - **Talos automata** (iron golems) guard cities and the Line.
  - **Key figures** (invulnerable, respawning, always reachable):
    - Philon of the Threshold
    - Archon Kleio Theanid
    - Strategos Lysandra Kallid (Kallias's descendant)
    - Priestess Iaso
    - Mechanic Perdix
    - Archivist Hesper
    - Librarian Eudora
    - Lochagos Menon of the Last Watch
    - Anaxis the Quiet (the dissenting philosopher)
    - Neaira, keeper of the Hearth
- **The Dominion** (roles):
  - **Rule:** the Ash-Crowned.
  - **Lieutenants:** the Crownless, black riders.
  - **Worship and administration:** the Sworn (Asterian collaborators) — Cinder Priests (evokers), Taskmasters
    (vindicators), clerks.
  - **Soldiery:** the Ashborn (orcs) — grunts, bowmen, Blackshields and war-chiefs.
  - **Tunnelers, lookouts and informers:** Gnawlings (goblins).
  - **Gate guards and haulers:** Slag Trolls (hostile iron golems).
  - **Shock troops:** the Emberkin (blazes) at the forges, and one pit demon.
  - **The enslaved:** the Bound (villagers), both Asterians and the Kelani plains-folk.
  - Some Dominion folk talk: **Uzgar** the Ashborn deserter, **Vesk** the goblin informer (who sometimes lies), and
    **Clerk Thersites** (bribeable, keeper of the Withdrawal ledger).

## 4. How to win (knowledge from the Concord is required and always recoverable)
Every key item and every lesson can be requested again from the teacher, or from a copy desk at the institution.
Nothing is lost if an item is.

| Ash-Crowned | Stronghold | His or her power | Concord knowledge | How it breaks them |
|---|---|---|---|---|
| Kallias | Pylon of Teeth | tireless legions; he regenerates | **Oath of Kallias** (Lysandra, Stoa of Shields) | holding the Oath near him makes him mortal |
| Melaina | Stilled Garden | nothing dies near her Stilling Fonts | **Hymn of Passage** (Iaso, Asklepieion) | sing it at the three fonts to silence them |
| Daidaros | Great Engine | the Engine shields and powers him | **The Counterpoint** (Perdix, Mechaneion) | break the three Governors in the right order |
| Keleos | Hall of Edicts | his Edict Stones compel and shield | **Founding Charter** (Hesper, Archive) | unspeak the three Edict Stones with the Charter |

- **Kallias holds the only gate** through the Teeth. A **Veil** of choking ash turns back anyone who crosses the Teeth
  before he falls.
- **Each fallen Ash-Crowned** darkens one Ward at Anthrakion and liberates that province.
- **With all four Wards dark:**
  - The Synedrion lends the **Light of Theano** (the Hearthstar's light).
  - Anthrakion's gate opens.
  - At the summit the Pyrarch fights in phases; the Light must be carried into the Cinder Heart to end it.
- **Victory:**
  - The ash stops and the sky clears over all Atlas.
  - The Chained Choir and every remaining captive go free.
  - Anthrakion's crown breaks.
  - A monument at the Gate of Strangers names the Rekindlers.

## 5. Liberation and the world afterwards
- **Province state:** occupied, then liberated; the whole realm is won when all five are (the Plateau falls last).
  - Liberated land heals as its chunks load. Every block still exactly as generated is regenerated in its liberated form:
    ash becomes soil and grass, fires go out, pens open.
  - Player-built or changed blocks are never touched.
  - Dominion spawns stop, and liberated camps become resettled villages.
  - Heliodromes wake in liberated forts.
- **Shared progression:** state is world-wide and saved.
  - Boss defeats, liberation, freed camps and rescued captives cannot be undone or repeated.
  - Rewards are claimed once per player.
  - Re-entering, reconnecting, restarting and unloading all keep everything.

## 6. Client (browser)
- **Initial client** (`site/classes.js`, fenced stages):
  - **Portal colours:** quartz frames split blue and black at the portal's middle.
  - **Realm loader:** reads a hidden server objective ("JRM v1 atlas ..."), loads `site/realms/atlas.js` lazily on first
    entry, and exposes a small engine API (particles, player position, sky and fog overrides).
- **Atlas module** (`site/realms/atlas.js`, loaded only in Atlas):
  - Sky and fog palettes per province and liberation state.
  - Continuous ash fall (falling-dust particles) over occupied Dominion land.
  - Embers at the forges and lumen motes in the Concord.

## 7. Status (checkpoints)
Labels: **offline-tested** (checked by `AtlasPreview` without a server), **server-tested** (checked on an isolated
Paper test server), **browser-tested** (checked there with real headless browser clients, two at once),
**planned** (not done).

| Area | State |
|---|---|
| Terrain, zones, biomes; cities, countryside, frontier, Dominion provinces and strongholds | offline-tested (deterministic, 0.5 ms per chunk, every cell filled on both sides); browser-tested (walked) |
| Story spots (10 figures, 12 bosses, 9 mechanisms, 12 captives, 16 berths, 10 heliodromes, 4 wards, 2 talkers) | offline-tested |
| Infill (2026-09-28): every bare 8x8 or 4x4 patch gets a scene or detail fitting its land; ground with nothing within 3 blocks went from 25-52% to 0.3-11% per region | offline-tested (measured), browser-tested (walked all regions) |
| Redraw on design change (epoch 2): old chunks moved into `jaspr_atlas/retired-epoch1-*`, world, progress and gates kept, a player logging in inside new buildings is lifted out | server-tested |
| Books (45 lore books, 5 key texts, 12 testimonies) fit their pages | offline-tested |
| World lifecycle: lazy load (0.1 s), unload 60 s after the last player, login inside Atlas, border | browser-tested |
| Persistence across unload and a full server restart (state, liberation, per-chunk masks, gates) | browser-tested |
| Quartz gate: kindle with lapis and coal (any face), travel both ways, the Great Gate, dead players ignored | browser-tested |
| Client: gates half blue, half black; realm module loaded lazily (25-45 ms); sky, fog, ash, embers, motes | browser-tested (ash only checked by diagnostics: the probe runs with particles off) |
| Dialogue (book UI with tappable answers, chat fallback), key items given and re-issued | browser-tested (Philon, Kleio, Lysandra, a captive, a rescued captive) |
| The Veil (turns back, ejects before the Pylon) | browser-tested |
| Kallias (immune until the Oath is held near him), rewards once per participant | browser-tested (two players) |
| Fonts + Hymn, Governors in order (wrong order resets), Edict Stones + Charter, the other three Ash-Crowned | server-tested (owner commands stand in for aiming) |
| The Light of Theano; the Pyrarch's phases; the Heart broken by the Light; victory | server-tested and browser-tested (screens) |
| Liberation: healing keeps player-changed blocks and full containers; land turns green, sky clears | browser-tested (464 chunks healed in the run) |
| Captives (rescue blocked by guards, House of Return berths, freed with their province) and a labour camp | server-tested |
| Trading (merchant screen), libraries (read and copy), heliodromes (find, list, travel), the Codex | browser-tested |
| Protection of cities, walls and heliodromes | implemented; not exercised in a test run |
| Deploy | live since the 23:32 restart on 2026-09-27 (ATLAS_READY, health checks passed); the Atlas world is created on first entry |
