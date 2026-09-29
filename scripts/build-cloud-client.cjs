'use strict';

// Exact, candidate-only fix for the composed browser client (site/classes.js): the TeaVM build registers the six
// EntityAreaEffectCloud data keys (radius, colour, waiting, particle, particle params) at ids 8..13, but a 1.12.2
// server sends them at 6..11 (Entity owns 0..5). Every cloud (lingering potions, dragon breath, a creeper with potion
// effects, the Nether's spore clouds) therefore stored the server's "waiting" Boolean as its radius; getRadius (GEn)
// read undefined, the particle loop's bound pi*r*r became NaN and "e >= NaN" never ends: the tab froze for good.
// This stage renumbers the keys to 6..11 in the key initialiser (Dy6). The replacement keeps every byte offset (the
// two-digit ids that become one digit are padded with a space), so the source map stays exact. Every anchor must match
// exactly once and the accessors must read the keys they are named for, or the build stops. Idempotent.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.CLOUD_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');

const BROKEN = 'function Dy6(){var b,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();b=$T.l();}_:while(true){switch($p){case 0:b=8;$p=1;case 1:Fc();if(B()){break _;}'
  + 'LfI=C0(b,Kun);LfJ=C0(9,Ks7);LfK=C0(10,Ks9);LfL=C0(11,Ks7);LfM=C0(12,Ks7);LfN=C0(13,Ks7);return;';
const FIXED = 'function Dy6(){var b,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();b=$T.l();}_:while(true){switch($p){case 0:b=6;$p=1;case 1:Fc();if(B()){break _;}'
  + 'LfI=C0(b,Kun);LfJ=C0(7,Ks7);LfK=C0( 8,Ks9);LfL=C0( 9,Ks7);LfM=C0(10,Ks7);LfN=C0(11,Ks7);return;';

/** The accessors that prove Dy6 initialises the cloud's keys: radius is a Float (.fB), waiting a Boolean (.br). */
const ACCESSORS = [
  ['GEn', 'c=LfI;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;return c.fB;', 'getRadius reads the radius key as a Float'],
  ['F8t', 'c=LfK;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;return c.br;', 'shouldIgnoreRadius reads the waiting key as a Boolean'],
  ['C0x', 'c=LfJ;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;return c.bn;', 'getColor reads the colour key as an Integer'],
  ['F1P', 'c=LfL;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;d=c.bn;', 'getParticle reads the particle key as an Integer'],
];

function functionBody(text, name) {
  const start = text.indexOf('function ' + name + '(');
  if (start < 0 || text.indexOf('function ' + name + '(', start + 1) >= 0) throw new Error(name + ' must be defined once');
  const end = text.indexOf('function ', start + 10);
  return text.slice(start, end < 0 ? text.length : end);
}

/** The cloud's key ids as the client registers them, in declaration order (radius first). */
function cloudKeyIds(text) {
  const body = functionBody(text, 'Dy6');
  const m = body.match(/case 0:b=(\d+);\$p=1;case 1:Fc\(\);if\(B\(\)\)\{break _;\}LfI=C0\(b,Kun\);LfJ=C0\( ?(\d+),Ks7\);LfK=C0\( ?(\d+),Ks9\);LfL=C0\( ?(\d+),Ks7\);LfM=C0\( ?(\d+),Ks7\);LfN=C0\( ?(\d+),Ks7\);return;/);
  if (!m) throw new Error('the cloud key initialiser (Dy6) changed: re-audit before patching');
  return m.slice(1).map(Number);
}

function build(input) {
  for (const [name, anchor, label] of ACCESSORS) if (!functionBody(input, name).includes(anchor)) throw new Error(label + ' (' + name + ') changed: re-audit');
  const ids = cloudKeyIds(input);
  if (ids.join() === '6,7,8,9,10,11') {
    if (input.split(FIXED).length !== 2) throw new Error('fixed initialiser must occur exactly once');
    return input;
  }
  if (ids.join() !== '8,9,10,11,12,13' || input.split(BROKEN).length !== 2) throw new Error('unexpected cloud key ids ' + ids.join() + ': re-audit');
  const output = input.replace(BROKEN, () => FIXED);
  if (output.length !== input.length) throw new Error('the fix must keep every byte offset');
  if (cloudKeyIds(output).join() !== '6,7,8,9,10,11') throw new Error('fix did not apply');
  new vm.Script(output, {filename: 'candidate/cloud-client/classes.js'});
  return output;
}

/** Undoes the fix (for the byte-for-byte reversal check). */
function revert(output) {
  if (output.split(FIXED).length !== 2) throw new Error('fixed initialiser must occur exactly once');
  return output.replace(FIXED, () => BROKEN);
}

if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const input = inputBytes.toString('latin1');
  const output = build(input);
  const outputBytes = Buffer.from(output, 'latin1');
  if (output !== input && revert(output) !== input) throw new Error('reversal is not byte-exact');
  const dir = path.join(root, 'candidate', 'cloud-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes),
    bytes: outputBytes.length, cloudKeyIdsBefore: cloudKeyIds(input), cloudKeyIdsAfter: cloudKeyIds(output), changed: output !== input};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build, revert, cloudKeyIds, BROKEN, FIXED};
