# Mutant lairs (JasprMutantLairs 1.0.0), 2026-10-14

Owner: "add 3 boss structures for every mutant creature mob each in the overworld."

There are twelve boss lairs, three for each of Mutant Creatures' four hostile mutants. The mod's snow golem, spider pig
and creeper minion are things a player makes or tames, so they have no boss lairs.

| Mutant | Lair | Boss | What it is |
| --- | --- | --- | --- |
| Zombie | The Plague Pit | The Pit Warden | A terraced quarantine pit full of the dead, with a failed fence and two field-hospital tents. The vault is a crypt in the pit wall. |
| Zombie | The Rotting Abattoir | The Butcher of the Fold | A slaughterhouse with a fallen-in roof, meat hooks, pens and a cold store. The vault is the grain silo. |
| Zombie | The Gravewarden Mausoleum | The Gravewarden | A walled cemetery and a pillared mausoleum, with a crypt hall below. The vault is the ossuary behind the crypt. |
| Skeleton | The Ossuary Spire | The Bone Regent | A bone tower in a ring of rib spikes, with a winding stair. The vault is the reliquary on its crown. |
| Skeleton | The Colossus Ribcage | The Ribbound Colossus | A giant fossil lying in an abandoned dig, with a camp and scaffolds. The vault is the skull. |
| Skeleton | The Bone Barbican | The Marrow Marshal | An archers' fort with curtain walls, four towers, a gatehouse and a range. The vault is the keep's upper hall. |
| Creeper | The Blast Crater | The Crater Mother | A scorched crater with a creeper nest and its eggs, and wreckage. The vault is a half-buried bunker. |
| Creeper | The Overgrown Greenhouse | The Verdant Detonator | A glass conservatory gone wild, with a dry fountain. The vault is the potting shed. |
| Creeper | The Powder Mill | The Old Fuse | A gunpowder works with a mill house, water wheel, chimney, storehouse and rails. The vault is the earth-covered magazine. |
| Enderman | The Void Obelisk | The Hollow Seer | Black obelisks around a sunken end-stone arena, with floating shards. The vault is under the altar. |
| Enderman | The Shattered Observatory | The Stargazer | A cracked dome with a great telescope and a book gallery. The vault is the sealed archive below. |
| Enderman | The Ender Grove | The Grove Shepherd | Warped trees around a sunken amphitheatre. The vault is the hollow of the greatest trunk. |

## Playing them

- **Discovery.** Walking up to a lair shows its name ("Lair of the Mutant Zombie"). `/lairs` lists the lairs you have
  found and gives the nearest one's direction and state.
- **Waking the boss.** Stepping into the arena wakes the boss. It is the mod's real mutant, with its own AI, attacks and
  boss bar, named after the lair and with twice its normal health. Its garrison of themed mobs wakes with it. JasprGear
  drops a trinket on its death and the bounty board counts it (the `jaspr_boss` tag).
- **Leashing.** The boss is held to its arena. If no fighting player is within 72 blocks for a minute, it goes back to
  sleep.
- **The vault.** While the boss lives, the vault cannot be opened, broken or blown up. The first kill opens it for good.
  It holds diamonds, emeralds, gold, strong enchanted books, an enchanted weapon or armour of the mutant's kind,
  experience, sometimes a totem or an enchanted golden apple, and the lair's own written account. Supply chests outside
  the vault carry the lair's theme.
- **Respawns.** After a kill the boss rests 30 minutes, then the lair can be fought again. The mod's own drops and a
  trinket come with every kill; the vault's loot comes only once.

## Where they go (safety first)

- **Grid.** There is one lair per 512 × 512 cell of the main overworld. Every 2048 × 1536 area holds all twelve, and the
  same lair never stands in a neighbouring cell.
- **Land checks.** A lair is only built into land that already exists and that nobody has touched:
  - every chunk under it, plus a 4-block skirt, is generated and populated;
  - no chunk is lived in (inhabited time at most 1200 ticks, the same rule as HorrorBiomes' retrofit);
  - no chunk is guarded by another pack;
  - no JasprMuseMaps or JasprImportedWorldgen plan reaches it;
  - no Lost City is reserved there;
  - the land is flat and dry enough;
  - it is more than 400 blocks from spawn.
- **Block-by-block scan.** The land is read block by block before anything is built. The lair is refused if there is
  anything made by anyone (structure blocks, containers, spawners, signs, frames, armour stands, carts, villagers) from
  the lair's deepest dig to the sky.
- **Out of sight.** Lairs are only built while no player is within 160 blocks. The view distance is 96, so nobody sees
  one appear.
- **Guards.** Built chunks are guarded the way MuseMaps guards its own, so HorrorBiomes' repair and retrofit passes leave
  them alone.
- **Load.** The work is spread out: one candidate check, two scanned chunks or two built chunks every five ticks, with at
  most six saved-chunk reads per step.

## Operators

- `/lairs status` shows counters and refusal reasons.
- `/lairs list` lists every lair.
- `/lairs check [key]` compares a built lair with its drawing, block by block.
- `/lairs build <design> [rotation]` builds one at your feet, without the land checks (for testing).
- The log lines are `LAIRS_READY`, `LAIR_DECIDED`, `LAIR_BUILT`, `LAIR_CELL_EMPTY`, `LAIR_BOSS_WAKE`,
  `LAIR_BOSS_DEFEATED`, `LAIR_VAULT_OPENED` and `LAIR_BOSS_SLEEPS`.
- The ledger is `plugins/JasprMutantLairs/lairs-<world uid>.yml`.

## Build and tests

```
bash scripts/build-mutant-lairs.sh            # jar + drawing check of all twelve (LAIRS_PREVIEW=<dir> keeps the pictures)
node --test tests/mutant-lairs-preview.test.cjs
node tests/mutant-lairs-server.cjs <jar> <scratch dir>   # one Paper test server, port 25687, one core
```

The server test runs with the real JasprMutants:

- It builds all twelve, in every rotation, and checks each block for block.
- It checks loot, spawners, signs and seals.
- It wakes each of the four mutants as itself with twice its health, and checks the garrison and the discovery title.
- It checks the vault: sealed and refusing to open, then opened by the kill, then openable.
- It checks the boss rests after a kill.
- It lets the Director find and build a lair on its own.
- It restarts the server and checks that everything persisted.
