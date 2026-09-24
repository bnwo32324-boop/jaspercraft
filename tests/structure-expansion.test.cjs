'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const cp = require('node:child_process');

const root = path.resolve(__dirname, '..');
const plugin = path.join(root, 'server/custom-plugins/JasprHorrorBiomes');
const catalogue = fs.readFileSync(path.join(plugin, 'resources/structures/catalog-v1.tsv'), 'utf8');
const lines = catalogue.split(/\r?\n/).filter(line => line && !line.startsWith('#'));
const rows = lines.map(line => line.split('|'));
const newRows = rows.slice(104);
const newRooms = 'RVLEIJHYOQXZabcegijklmtvwp';

function cells(graph) {
  const points = [];
  graph.split('/').forEach((row, z) => [...row].forEach((type, x) => {
    if (type !== '.') points.push([x, z]);
  }));
  return points;
}

// Ignore palette, room letters, translation, reflection and rotation when measuring layout variety.
function silhouette(graph) {
  const points = cells(graph);
  return Array.from({length: 8}, (_, transform) => {
    const rotated = points.map(([x, z]) => {
      if (transform >= 4) x = -x;
      for (let r = 0; r < transform % 4; r++) [x, z] = [-z, x];
      return [x, z];
    });
    const minX = Math.min(...rotated.map(p => p[0]));
    const minZ = Math.min(...rotated.map(p => p[1]));
    return rotated.map(([x, z]) => `${x - minX},${z - minZ}`).sort().join(';');
  }).sort()[0];
}

test('original 104 rows and selection order remain exactly frozen', () => {
  const digest = crypto.createHash('sha256').update(lines.slice(0, 104).join('\n') + '\n').digest('hex');
  assert.equal(digest, 'a37a35bbd474b455b7ae88410f28113be8c80bcf0c64a11b42f1f25cd8ef9733');
  assert.ok(newRows.length >= 126);
  assert.equal(new Set(rows.map(row => row[0])).size, rows.length);
});

test('new commissions cover all 62 biomes, minor ruins, major sites and distinct room programs', () => {
  const motifs = new Set();
  for (const row of newRows) {
    const [id, name, family, tier, mapping, exclusive, mode, graph] = row;
    assert.equal(row.length, 8, id);
    assert.match(id, /^[a-z0-9_]+$/);
    assert.ok(name.length > 12 && family.length > 2, id);
    assert.match(tier, /^[1-5]$/, id);
    assert.match(exclusive, /^(true|false)$/, id);
    assert.match(mode, /^(surface|buried|underwater)$/, id);
    assert.match(graph, /^[.hMsSKTDUBFWCGAP=oq23468RVLEIJHYOQXZabcegijklmtvwp/]+$/, id);
    assert.ok(graph.split('/').length <= 20 && graph.split('/').every(line => line.length <= 20), id);
    const biomes = mapping.split(',').map(Number);
    assert.equal(new Set(biomes).size, biomes.length, id);
    assert.ok(biomes.every(biome => Number.isInteger(biome) && biome >= 0 && biome < 62), id);
    assert.equal(exclusive === 'true', biomes.length === 1, id);
    const used = new Set([...graph].filter(type => newRooms.includes(type)));
    for (const type of used) motifs.add(type);
    assert.ok(used.size >= 2, `Room program needs multiple functions: ${id}`);
    if (exclusive === 'true') {
      assert.ok(Number(tier) >= 3 && cells(graph).length >= 12, `Exclusive expedition: ${id}`);
      assert.ok(used.size >= 3, `Exclusive room variety: ${id}`);
    }
    const occupied = new Set(cells(graph).map(p => p.join(',')));
    const seen = new Set(), pending = [occupied.values().next().value];
    while (pending.length) {
      const point = pending.pop();
      if (seen.has(point)) continue;
      seen.add(point);
      const [x, z] = point.split(',').map(Number);
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const next = `${x + dx},${z + dz}`;
        if (occupied.has(next) && !seen.has(next)) pending.push(next);
      }
    }
    assert.equal(seen.size, occupied.size, `Disconnected graph: ${id}`);
  }
  for (let biome = 0; biome < 62; biome++) {
    const choices = newRows.filter(row => row[4].split(',').map(Number).includes(biome));
    assert.ok(choices.some(row => row[5] === 'true'), `New exclusive for biome ${biome}`);
    assert.ok(choices.some(row => row[5] === 'false'), `New shared for biome ${biome}`);
  }
  assert.equal(motifs.size, 26);
  assert.ok(newRows.filter(row => Number(row[3]) <= 2 && cells(row[7]).length <= 12).length >= 30);
  assert.ok(newRows.filter(row => cells(row[7]).length >= 35).length >= 15);
  assert.ok(new Set(newRows.map(row => row[2])).size >= 18);
  const shapes = new Set(newRows.map(row => silhouette(row[7])));
  assert.ok(shapes.size >= 105, `Only ${shapes.size} physical silhouettes`);
  console.log(`Expansion catalogue: ${newRows.length} additions, ${shapes.size} silhouettes, ${motifs.size} motifs`);
});

test('real ChunkData: all designs, marker access, waterproofing, motif geometry and append-only validation', {timeout: 300000}, () => {
  const output = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-expansion-jvm-'));
  const sourceDir = path.join(plugin, 'src/chat/jaspr/biomes');
  const sources = fs.readdirSync(sourceDir).filter(name => name.endsWith('.java')).map(name => path.join(sourceDir, name));
  const jar = path.join(root, 'server/cache/patched_1.12.2.jar');
  const spec = path.join(__dirname, 'java/chat/jaspr/biomes/StructureExpansionTest.java');
  const compile = cp.spawnSync('javac', ['--release', '8', '-encoding', 'UTF-8', '-cp', jar, '-d', output, ...sources, spec], {
    encoding: 'utf8', windowsHide: true, timeout: 45000
  });
  assert.equal(compile.status, 0, String(compile.error || '') + compile.stdout + compile.stderr);
  const java = (resources, args = []) => cp.spawnSync('java', ['-Xmx768m', '-cp', [output, jar, resources].join(path.delimiter),
    'chat.jaspr.biomes.StructureExpansionTest', ...args], {encoding: 'utf8', windowsHide: true, timeout: 250000, maxBuffer: 4 * 1024 * 1024});
  const run = java(path.join(plugin, 'resources'));
  process.stdout.write(run.stdout || '');
  assert.equal(run.status, 0, String(run.error || '') + (run.stderr || ''));
  assert.match(run.stdout, /STRUCTURE_EXPANSION_PASS designs=\d+ new=\d+ motifs=26 modeVariants=78/);

  // Corrupt only generated fixture resources; never touch the catalogue or any live server file.
  const fixture = path.join(output, 'invalid-resources');
  fs.mkdirSync(path.join(fixture, 'structures'), {recursive: true});
  const rejected = (changed, expected) => {
    fs.writeFileSync(path.join(fixture, 'structures/catalog-v1.tsv'), changed.join('\n') + '\n');
    const result = java(fixture, ['--load-only']);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, expected);
  };
  const disconnected = [...lines];
  const split = [...rows[104]]; split[7] = 'LL./.../..L'; disconnected[104] = split.join('|');
  rejected(disconnected, /Disconnected room graph/);
  const reordered = [...lines]; [reordered[0], reordered[1]] = [reordered[1], reordered[0]];
  rejected(reordered, /Original 104 structure rows changed or reordered/);
  const unknown = [...lines]; split[7] = '@LL'; unknown[104] = split.join('|');
  rejected(unknown, /Unknown room @/);
  console.log('Expansion loader rejected disconnected graphs, unknown motifs and legacy reordering');
});
