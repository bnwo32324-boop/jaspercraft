# Gate guides: beating Atlas, Drownhollow and the Nether -- 2026-09-29

The owner asked for "a straightforward, easy way to beat all three dimensions individually, sequentially, or whatever
the player chooses", "extremely obvious" how, with a compass per dimension pointing where to go. Follow-ups: a guide at
each portal hands the compass out, as many as a player wants; he also hands out a checklist that ticks itself off, and
a third item, a self-updating map with markers. All three items can be had again and again, for every realm.

## What a player sees
- **A guide at every gate.** Coming through a portal into a realm, a guide stands beside the gate (the *Atlas Guide*,
  a librarian; the *Drownhollow Guide*, a priest; the *Nether Guide*, a blacksmith). He cannot be hurt and does not
  trade. The first time in a realm the player is handed the three items; every arrival they are told their goal and
  next task. Right-click the guide for any item they lack; sneak and right-click for a whole new set.
- **The compass** points to the next task. Held, a line above the hotbar reads e.g. `1/5 Win a Warden's Seal | 118 blocks
  ahead-right, 20 down` (in Drownhollow it also shows the Dread when it is high). Right-click: the task, where it is and
  how to do it in chat, and a trail of light towards it. The needle turns in Atlas and Drownhollow (the client spins it
  in the Nether, so there the line and the trail do the work).
- **The checklist** (a written book) shows the goal, every task ticked or not, the next task with instructions, and tips.
  It rewrites itself when a task is done and again when it is opened.
- **The map** shows the realm (land, sea, regions), its great places, the player (white arrow) and the next task (red
  marker, or a red arrow on the edge when it is off the map). It redraws itself as the player moves (Atlas: the whole
  realm on one map, recoloured as provinces are freed).
- **Task done** titles and chat lines as each step completes; **REALM CONQUERED**, a broadcast and a gold Conqueror's
  Crown for a realm; **THREE REALMS**, a broadcast and a Crown of the Three Realms (chainmail, Protection II,
  Unbreaking III) for all three. `/goals` (also `/quests`, `/checklist`) lists every realm's tasks in any world.

## The three roads
- **Atlas** (11 tasks, the Codex's road): meet Archon Kleio; the Oath from Lysandra, break Kallias; the Hymn from Iaso,
  break Melaina (her three Fonts); the Counterpoint from Perdix, break Daidaros (three Governors, in order); the
  Charter from Hesper, break Keleos (three Edict Stones); the Light of Theano from Kleio; end the Pyrarch (hold the Light
  at the Heart's root). Bosses and mechanisms are shared: whatever anyone broke is ticked for everyone. A player who
  comes after Atlas is free faces the **Echo of the Pyrarch**: Kleio lends them the Light, and the Echo rises in the
  Cinder Throne only for players who have not conquered Atlas (same fight; `ATLAS_ECHO_FALLEN`). Rekindlers from before
  the guides are recorded as conquerors (and get their crown) on their next visit.
- **Drownhollow** (5 tasks): win a Warden's Seal, a second different one, a third; set them into the Great Door; slay the
  Dreamer's Herald. The Door is shared, so Seals already set count for everyone; the compass leads to the nearest Warden
  whose Seal still counts (preferring Wardens that are awake). Herald slayers from before the guides are recorded as
  conquerors. The Pilgrim's Primer and the Drowned Star Compass are no longer handed out (the owner wanted three items);
  `/ruins primer` still gives the Primer.
- **The Nether** (4 tasks): reach the Spore Cathedral; take a Potion of Sorrow from the Font of Sorrow in its crypt (walk
  up to it, or click it with either button: the browser client does not send an empty-handed right-click on a plain
  block); pour it into the Urn of Sorrow on the crown; slay the Ghast Queen (everyone who hurt her or stood within 64
  blocks conquers the Nether).

## Fixed along the way
- **Client freeze near spore clouds (and any lingering-potion cloud).** The browser client registered the six
  AreaEffectCloud data keys at ids 8..13; a 1.12.2 server sends them at 6..11. A cloud's radius slot then held the
  server's "waiting" Boolean, `getRadius` read undefined, the particle loop's bound pi*r*r was NaN and `e >= NaN` never
  ends: the tab froze (a Spore Creeper explosion in the Cathedral crypt froze the test client every time; the server
  then kicked it for "flying" or timed it out). `scripts/build-cloud-client.cjs` renumbers the keys in place (same byte
  length, source map exact); `site/client.html` asks for `classes.js?v=20260929-cloud1`. Players must reload the page to
  get it; JasprNether no longer spawns a cloud entity at all (the spore cloud is coloured particles), so old tabs are
  safe from it too. Found by pausing the frozen page in the debugger (`["stack", ...]` in `scripts/tank-cdp-probe.cjs`).
  Other entity keys with wrong ids in the client (boats, horses, llamas, End crystals) only look wrong; left for a
  separate task.
- The Great Door answered each Seal twice ("The Great Door is sealed...") because the client repeats a refused click
  with the off hand; the Font of Sorrow did the same. Both now answer once.
- An open Great Door survives a server restart (it stayed physically open but forgot it was open).
- The compass and checklist no longer stop chests, doors and levers from working when held.

## Where things are
- `GuideKit.java` (shared, identical in `chat.jaspr.atlas`, `chat.jaspr.ruins`, `chat.jaspr.nether`; a test keeps the
  copies equal): items, guides, tasks heartbeat, compass line and trail, checklist, map renderer, victories, `/goals`.
- Each realm's road: `AtlasQuest.java`, `RuinsQuest.java`, `NetherQuest.java` (tasks, map colours, markers, labels).
- Owner tools: `/atlas guide|quest`, `/ruins guide|quest|primer|as <player> <sub>`, `/jnether guide|quest [player]`.
- Tests: `node --test tests/guide-kit.test.cjs tests/cloud-client.test.cjs`. In-game runs (2026-09-29, loopback fixture
  with all three realms): portal/gate arrival with the guide and items, compass line and trail, map, checklist, each
  task ticking off, victory in each realm, the Echo, the Three Realms.
- Logs (no player names): `GUIDE_ITEMS`, `GUIDE_PLACED`, `GUIDE_MAP`, `GUIDE_TASK_DONE`, `GUIDE_VICTORY`,
  `GUIDE_PAST_VICTORY`, `GUIDE_THREE_REALMS`, `GUIDE_TASKS_FAILED`, `GUIDE_TICK_FAILED`; `NETHER_FONT_GIVEN how=`,
  `ATLAS_ECHO_FALLEN`, `ATLAS_BOSS_RISEN ... echo=true`. Player tags: `jr_kit_<realm>`, `jr_beat_<realm>`,
  `jr_beat_<realm>_on_<date>`, `jr_beat_all`, `jr_guide_<realm>` (on guides).
