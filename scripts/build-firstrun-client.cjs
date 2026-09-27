'use strict';

// Exact, candidate-only first-join stage for the composed browser client (site/classes.js).
// Minecraft.startGame (FEH) normally opens GuiConnecting(parent = Edit Profile) when joinServer is set, so a
// first-time browser auto-joins in the middle of its heavy first load (asset unpacking, caches) and often
// times out; "Back to server list" then lands on Edit Profile ("create a character").
//  - First run ($rt_globals.JasprFirstRun, set by jaspr-client.js for accounts with no saved JasperCraft data):
//    open Edit Profile first, with GuiConnecting as its parent, so Done joins the server.
//  - Everyone else: auto-join as before, but the failed attempt's parent is a second GuiConnecting, so the
//    single "Back to server list" press retries the join once before falling back to Edit Profile.
// GuiConnecting's constructor only stores its target (Eaq); the socket opens when the screen is shown.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.FIRSTRUN_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MARK = '/*JASPR_FIRSTRUN_V1*/';

const BLOCK = [
  '/* JASPR_FIRSTRUN_V1_BEGIN */',
  '/* First-time players (no saved JasperCraft data) see Edit Profile before joining; Done opens the connecting',
  ' * screen, after the engine has finished its first load. The flag comes from jaspr-client.js. */',
  'var JasprFirstRun={active:function(){return $rt_globals.JasprFirstRun===true;}};',
  '/* JASPR_FIRSTRUN_V1_END */',
].join('\r\n');

function body(text, name) {
  const start = text.indexOf('function ' + name + '(');
  if (start < 0 || text.indexOf('function ' + name + '(', start + 1) >= 0) throw new Error(name + ' must be defined once');
  const end = text.indexOf('\nfunction', start + 10);
  return [start, end < 0 ? text.length : end];
}
function within(text, name, pairs) {
  const [start, end] = body(text, name);
  let fn = text.slice(start, end);
  for (const [from, to, label] of pairs) {
    if (fn.split(from).length !== 2) throw new Error(label + ' anchor must occur exactly once in ' + name);
    fn = fn.replace(from, () => to);
  }
  return text.slice(0, start) + fn + text.slice(end);
}

function build(input) {
  if (input.includes('JASPR_FIRSTRUN_V1')) throw new Error('Client already contains the first-run stage');
  if (!input.includes('/* JASPR_TANK_V1_END */')) throw new Error('Expected the tank stage before this one');
  let output = within(input, 'FEH', [
    ['b=new AHi;c=new Zk;f=new Hj;$p=88;continue _;',
      'b=new AHi;c=new Zk;f=new Hj;' + MARK + 'g=new AHi;$p=88;continue _;', 'join-branch screens'],
    ['case 88:B1d(f);if(B()){break _;}$p=89;',
      'case 88:B1d(f);if(B()){break _;}' + MARK + 'if(JasprFirstRun.active()){h=a.cGx;d=a.dSs;$p=195;continue _;}$p=89;', 'first-run branch'],
    ['case 90:BmS(b,c,a,f,d);if(B()){break _;}$p=91;',
      'case 90:' + MARK + 'BmS(g,c,a,f,d);if(B()){break _;}$p=198;case 198:BmS(b,g,a,f,d);if(B()){break _;}$p=91;', 'retry parent'],
    ['case 94:FsZ(b);if(B()){break _;}return;default:FT();',
      'case 94:FsZ(b);if(B()){break _;}return;' + MARK + 'case 195:BmS(b,f,a,h,d);if(B()){break _;}$p=196;'
      + 'case 196:BOH(c,b);if(B()){break _;}$p=197;case 197:GGw(a,c);if(B()){break _;}b=a.bE;c=a.cr_;$p=92;continue _;default:FT();',
      'first-run screens'],
  ]);
  if (output.split('/* JASPR_TANK_V1_END */').length !== 2) throw new Error('append anchor must occur exactly once');
  output = output.replace('/* JASPR_TANK_V1_END */', '/* JASPR_TANK_V1_END */\r\n\r\n' + BLOCK);
  new vm.Script(output, {filename: 'candidate/firstrun-client/classes.js'});
  return output;
}

if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const output = build(inputBytes.toString('latin1'));
  const outputBytes = Buffer.from(output, 'latin1');
  const dir = path.join(root, 'candidate', 'firstrun-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes),
    hooks: output.split(MARK).length - 1};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build};
