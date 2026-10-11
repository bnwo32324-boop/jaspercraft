'use strict';
// The combat rework's client, assembled in the lead's order (COMBAT_REWORK.md), one builder at a time:
//   LIVE classes.js -> A's Better Combat head (bettercombat9) -> B's melee module -> Mo' Bends module swap (off-hand attacks
//   in third person) -> B's JASPR_COMBATFX -> C's JASPR_GUNFX (outermost)
// Usage: node scripts/assemble-combat-client.cjs [LIVE classes.js]
//   -> candidate/combat-deploy/classes.js + candidate/combat-deploy/manifest.json (every step's input and output sha256)
// Reads the LIVE client only; never writes site/, server/ or .runtime.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const root = path.join(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const input = path.resolve(process.argv[2] || LIVE);
const STEPS = [
  ['bettercombat', 'scripts/build-bettercombat-client.cjs', [input], 'candidate/bettercombat/classes.js'],
  ['melee', 'scripts/build-melee-client.cjs', ['candidate/bettercombat/classes.js'], 'candidate/melee-client/classes.js'],
  ['mobends-module', 'scripts/build-mobends-client.cjs', ['--module', 'candidate/melee-client/classes.js'], 'candidate/mobends-client/classes.js'],
  ['combatfx', 'scripts/build-combatfx-client.cjs', ['candidate/mobends-client/classes.js'], 'candidate/combatfx/classes.js'],
  ['gunfx', 'scripts/build-gunfx-client.cjs', ['candidate/combatfx/classes.js'], 'candidate/gunfx/classes.js']
];
const manifest = { input, inputSHA256: sha(input), steps: [], builtAt: null };
for (const [name, script, args, output] of STEPS) {
  const from = path.resolve(root, args[args.length - 1]);
  const r = spawnSync(process.execPath, [path.join(root, script), ...args], { cwd: root, encoding: 'utf8', windowsHide: true, maxBuffer: 64 * 1024 * 1024 });
  if (r.status !== 0) throw new Error(name + ' failed (' + r.status + '):\n' + (r.stderr || '') + (r.stdout || '').slice(-2000));
  const out = path.join(root, output);
  manifest.steps.push({ name, script, inputSHA256: sha(from), output, sha256: sha(out) });
  console.log(name + ' ' + sha(out).slice(0, 16));
}
const dir = path.join(root, 'candidate', 'combat-deploy');
fs.mkdirSync(dir, { recursive: true });
fs.copyFileSync(path.join(root, STEPS[STEPS.length - 1][3]), path.join(dir, 'classes.js'));
manifest.sha256 = sha(path.join(dir, 'classes.js'));
manifest.bytes = fs.statSync(path.join(dir, 'classes.js')).size;
manifest.builtAt = new Date().toISOString();
fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log('Candidate only: ' + path.join(dir, 'classes.js') + ' sha256 ' + manifest.sha256);
