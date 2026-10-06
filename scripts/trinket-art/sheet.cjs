'use strict';
/* Contact sheet for reviewing icons: node scripts/trinket-art/sheet.cjs <set> [out.png] [scale]
 * sets: dungeon | dungeon7 | ruins | nether | backrooms | all. Prints each icon's painted size and flags anything that would touch the edge. */
const fs = require('node:fs'), path = require('node:path');
const {png, SIZE} = require('./engine.cjs');

function pixelsOf(icon) { return icon.finish(); }
/** Draws icons on a grid; each cell is the icon at `scale` over a checkerboard. */
function sheet(entries, scale = 4, cols = 12) {
  const pad = 2, cell = SIZE * scale + pad * 2, rows = Math.ceil(entries.length / cols);
  const W = cols * cell, H = rows * cell, out = new Array(W * H).fill(null);
  const bg = (x, y) => ((Math.floor(x / (scale * 2)) + Math.floor(y / (scale * 2))) % 2 ? [88, 88, 96] : [104, 104, 112]);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) out[y * W + x] = bg(x, y);
  entries.forEach(([name, icon], n) => {
    const px = pixelsOf(icon), cx = (n % cols) * cell + pad, cy = Math.floor(n / cols) * cell + pad;
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
      const c = px[y * SIZE + x]; if (!c) continue;
      for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) out[(cy + y * scale + dy) * W + cx + x * scale + dx] = c;
    }
  });
  return png(out, W, H);
}

function load(set) {
  const entries = [];
  if (set === 'dungeon' || set === 'all') {
    const d = require('./dungeon.cjs');
    for (const name of Object.keys(d.ICONS)) entries.push(['dungeon:' + name, d.draw(name)]);
    entries.push(['dungeon:POUCH', d.draw('POUCH')]);
  }
  if (set === 'dungeon7' || set === 'all') {
    const d7 = require('./dungeon7.cjs');
    for (const name of Object.keys(d7.ICONS)) entries.push(['dungeon7:' + name, d7.draw(name)]);
  }
  for (const other of ['ruins', 'nether', 'backrooms']) {
    if (set === other || set === 'all') {
      const f = path.join(__dirname, other + '.cjs');
      if (fs.existsSync(f)) { const m = require(f); for (const name of Object.keys(m.ICONS)) entries.push([other + ':' + name, m.draw(name)]); }
    }
  }
  return entries;
}

if (require.main === module) {
  const set = process.argv[2] || 'dungeon', out = process.argv[3] || path.join(__dirname, '..', '..', 'candidate', 'trinket-art', set + '-sheet.png');
  const scale = +(process.argv[4] || 4), entries = load(set);
  let bad = 0;
  entries.forEach(([name, icon]) => {
    const b = icon.box(), w = b.x1 - b.x0 + 1, h = b.y1 - b.y0 + 1;
    if (w > 14 || h > 14) { console.log('TOO BIG ' + name + ' ' + w + 'x' + h); bad++; }
  });
  fs.mkdirSync(path.dirname(out), {recursive: true});
  fs.writeFileSync(out, sheet(entries, scale));
  console.log(entries.length + ' icons -> ' + out + (bad ? ' (' + bad + ' too big)' : ''));
  entries.forEach(([name], n) => process.stdout.write((n % 12 === 0 ? '\n' : ' ') + String(n).padStart(2) + ':' + name.split(':')[1].slice(0, 9).padEnd(9)));
  console.log();
}
module.exports = {sheet, load};
