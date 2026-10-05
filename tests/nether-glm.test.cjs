'use strict';
// The owner's GLM Nether structures in the Nether (owner, 2026-09-29: "Implement everything from GLM Nether structures
// into the Nether's natural structure generation ... make these structures dangerous and filled with loot. They should
// only contain Nether mobs ... a lot of these Nether mobs to be custom ... bosses that you have to conquer ... custom
// loot that has a Nether theme ... Have them integrated into the story, mission, and quest progression system").
// Compiles the plugin with the offline check (tests/java/chat/jaspr/nether/GlmPreview.java): planning over a
// 12,000-block square (no overlaps, no mega collisions, every build and every Lord appears, neighbouring lord cells
// differ, every floor fits) and every build drawn chunk by chunk on synthetic Nether terrain (deterministic, no
// forbidden block, stocked, cheap). Then the wiring: creatures, Lords, loot, relics, quest, safety rules.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const nether = path.join(root, 'server/custom-plugins/JasprNether');
const src = path.join(nether, 'src/chat/jaspr/nether');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');
const LORDS = ['deathwing', 'ignareth', 'pit_lord', 'ashen_wither', 'cursed_king', 'dread_sorcerer', 'voidborn', 'bone_colossus', 'crimson_tyrant', 'blood_count'];
const builds = () => fs.readFileSync(path.join(nether, 'resources/glm/builds.tsv'), 'utf8').split('\n').filter(l => l && !l.startsWith('#')).map(l => l.split('\t'));

test('GLM builds: planned, drawn per chunk, deterministic, no valuables, stocked, cheap', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-nether-glm-'));
  const classes = path.join(out, 'classes');
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/nether/MegaPreview.java'), path.join(root, 'tests/java/chat/jaspr/nether/GlmPreview.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=2', '-Xmx1600m', '-Djava.awt.headless=true', '-cp', classes + path.delimiter + cp,
    'chat.jaspr.nether.GlmPreview', '-', path.join(nether, 'resources')], {encoding: 'utf8', timeout: 900000, cwd: out});
  fs.rmSync(out, {recursive: true, force: true});
  const failed = run.stdout.split(/\r?\n/).filter(l => / FAIL$/.test(l.trim())).join(' | ');
  assert.equal(run.status, 0, (failed || run.stderr + run.stdout.slice(-4000)).slice(0, 6000));
  assert.match(run.stdout, /GLM_OK/);
  assert.match(run.stdout, /glm plan .*distinctBuilds=97\/97 catalog=98 disabledPlaced=0 missing=0 .*overlaps=0 megaHits=0 lordRepeat=0 badFloor=0 PASS/);
  for (const [key] of builds()) assert.match(run.stdout, new RegExp('glm ' + key + ' .*deterministic=true forbidden=\\{\\} .*PASS'), key);
  for (const lord of LORDS) assert.match(run.stdout, new RegExp('points=\\{[^}]*lord:' + lord + '=1'), lord + ' has its arena');
});

test('GLM builds: the index, the converter and the owner folder rules', () => {
  const rows = builds();
  assert.equal(rows.length, 98, '98 unique builds (N206 and N224 duplicate N018 and N086)');
  const tiers = {};
  for (const r of rows) tiers[r[2]] = (tiers[r[2]] || 0) + 1;
  assert.deepEqual(Object.keys(tiers).sort(), ['common', 'great', 'lord']);
  const lords = rows.filter(r => r[2] === 'lord').map(r => r[5]).sort();
  assert.deepEqual(lords, [...LORDS].sort(), 'each Lord holds exactly one stronghold');
  for (const r of rows) {
    assert.ok(fs.existsSync(path.join(nether, 'resources/glm', r[0] + '.glb')), r[0] + '.glb');
    assert.ok(Number(r[7]) <= 122, r[0] + ' fits the Nether (height ' + r[7] + ')');
  }
  const conv = fs.readFileSync(path.join(nether, 'tools/convert_glm.py'), 'utf8');
  // valuables become look-alikes; the converter refuses to write a build that still holds one
  for (const id of [41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 120, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154, 27, 28, 147, 148, 71, 167])
    assert.match(conv, new RegExp('FORBIDDEN = \\{[^}]*\\b' + id + '\\b'), 'forbidden ' + id);
  assert.match(conv, /raise SystemExit\('%s: forbidden blocks survived/);
  assert.doesNotMatch(conv, /open\([^)]*SRC[^)]*'w'/, 'the owner folder is only read');
  assert.match(conv, /'N206', '', 'common', 'arcane', skip='duplicate of N018'/);
  // skull rule: generation never hands out wither skulls
  assert.match(conv, /e\['skull'\] = \{1: 0, 3: 2, 5: 0\}/);
});

test('GLM wiring: creatures, spawners, Lords, loot, relics, quest', () => {
  const mobs = java('Mobs'), fiends = java('Fiends'), lords = java('Lords'), sites = java('GlmSites'), items = java('Items'), loot = java('Loot');
  const known = new Set([...items.matchAll(/new Def\("([a-z_]+)"/g)].map(m => m[1]));
  for (const set of ['hellforged', 'soulweave', 'wither_bone', 'orange_salamander_hide', 'black_salamander_hide'])
    for (const slot of ['helmet', 'chestplate', 'leggings', 'boots']) known.add(set + '_' + slot);
  for (const h of ['withered', 'blazed', 'frosted']) for (const t of ['sword', 'pickaxe', 'shovel', 'axe', 'hoe', 'hammer']) known.add(h + '_amedian_' + t);
  for (const lord of LORDS) known.add('sigil_' + lord);
  // the thirteen creatures
  const kinds = [...fiends.matchAll(/new Mobs\.Spec\("([a-z_]+)"/g)].map(m => m[1]);
  assert.equal(kinds.length, 13);
  assert.match(mobs, /Fiends\.specs\(Mobs::spec\);/);
  // every roster and native kind exists (a Fiend, a NetherEx mob or a vanilla Nether mob)
  const vanilla = new Set(['wither_skeleton', 'magma_cube', 'blaze', 'zombie_pigman']);
  const netherex = new Set([...mobs.matchAll(/spec\(new Spec\("([a-z_]+)"/g)].map(m => m[1]));
  for (const m of sites.matchAll(/new String\[\]\{([^}]*)\}/g))
    for (const k of m[1].match(/"([a-z_]+)"/g).map(s => s.slice(1, -1))) assert.ok(kinds.includes(k) || vanilla.has(k) || netherex.has(k), 'roster kind ' + k);
  // drops and loot name real items
  for (const m of (fiends + loot + lords).matchAll(/Items\.create\("([a-z_]+)"|nx\("([a-z_]+)"|add\(drops, "([a-z_]+)"/g)) {
    const id = m[1] || m[2] || m[3];
    assert.ok(known.has(id), 'unknown item ' + id);
  }
  for (const list of ['GEAR', 'WEAPONS', 'TRINKETS']) {
    const m = loot.match(new RegExp('static final String\\[\\] ' + list + ' = \\{([^}]*)\\}'));
    for (const id of m[1].match(/"([a-z_]+)"/g).map(s => s.slice(1, -1))) assert.ok(known.has(id), list + ' ' + id);
  }
  // the Lords: ten, each with a relic and a sigil, credited by tag, resting between risings
  for (const lord of LORDS) {
    assert.match(lords, new RegExp('def\\(new Def\\("' + lord + '"'), lord);
    const relic = lords.match(new RegExp('def\\(new Def\\("' + lord + '"[^;]*"([a-z_]+)"\\)\\);'))[1];
    assert.ok(known.has(relic), lord + ' relic ' + relic);
  }
  assert.match(lords, /p\.addScoreboardTag\("jn_lord_" \+ d\.id\)/);
  assert.match(lords, /Items\.create\("sigil_" \+ d\.id, 1\)/);
  assert.match(lords, /REST_TICKS = 20 \* 60 \* 15/);
  // no Lord ever damages blocks; the dragons never use the End's paths (the plugin flies them in the hover phase)
  assert.match(lords, /if \(e\.getEntity\(\) instanceof org\.bukkit\.entity\.EnderDragon\) \{ e\.setCancelled\(true\); return; \}/);
  assert.match(lords, /e\.blockList\(\)\.clear\(\);/);
  assert.match(lords, /public void onChangeBlock\(EntityChangeBlockEvent e\)/);
  assert.match(lords, /DragonControllerPhase\.k\) m\.setControllerPhase\(net\.minecraft\.server\.v1_12_R1\.DragonControllerPhase\.k\)/);
  // no area effect clouds (older browser clients froze on them), no night vision, full-bright or glowing
  for (const name of ['Lords', 'Fiends', 'Relics', 'GlmLife', 'GlmSites']) {
    const code = java(name);
    assert.doesNotMatch(code, /AreaEffectCloud|DragonFireball/, name + ' spawns no clouds');
    assert.doesNotMatch(code, /NIGHT_VISION|GLOWING/, name + ' gives no night vision or glowing');
  }
  // spawners are the plugin's: inert blocks woken near players, bounded
  const life = java('GlmLife'), ops = java('StructureOps');
  assert.match(ops, /if \(inert\) cs\.setRequiredPlayerRange\(0\);/);
  assert.match(life, /static final int NEAR = 16, CAP = 4;/);
  assert.match(life, /if \(b\.getType\(\) != Material\.MOB_SPAWNER\) continue;/, 'a broken spawner stops');
  // fire never destroys a stronghold
  assert.match(life, /public void onBurn\(BlockBurnEvent e\)/);
  assert.match(life, /IgniteCause\.SPREAD \|\| c == BlockIgniteEvent\.IgniteCause\.LAVA/);
  // the quest: three Lords before the Urn of Sorrow answers
  const quest = java('NetherQuest'), boss = java('Boss');
  assert.match(quest, /for \(int i = 0; i < Lords\.NEEDED; i\+\+\)/);
  assert.match(lords, /NEEDED = 3/);
  assert.match(boss, /lords < Lords\.NEEDED && \(plugin\.guide == null \|\| !plugin\.guide\.beaten\(e\.getPlayer\(\)\)\)/);
  assert.match(boss, /NETHER_URN_REFUSED/);
  // chests of the builds are placed with their tile entities (after the flush), loot never followed by update()
  assert.match(ops, /Loot\.fill\(\(\(Chest\) s\)\.getBlockInventory\(\), p\.table, r\); glmLoot\+\+; \} \/\/ live inventory; never update\(\) afterwards/);
  // the regenerated Nether
  assert.match(java('NetherPlugin'), /static final int REGEN_EPOCH = 7;/);
});
