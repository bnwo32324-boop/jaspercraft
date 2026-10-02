'use strict';
// Writes headless-probe steps that freeze first-person melee clips at chosen phases in the loopback fixture
// (scripts/mobends-preview.cjs exposes the motion as window.JasprMeleeFixture there, and only there).
// Usage: node scripts/melee-probe-steps.cjs <steps.json> <slot 1-9> <family> <clip@phase> ...
const fs = require('node:fs');
const [out, slot, family, ...shots] = process.argv.slice(2);
const ids = { sword: 276, axe: 258, tool: 257, heavy: 258, spear: 267, hook: 292, dagger: 267 };
const durations = { sword: 8.5, axe: 11, heavy: 13, spear: 8, hook: 11, dagger: 6, tool: 9 };
const steps = [['wait', 16000], ['eval', 'JSON.stringify({bc:JasprBetterCombatStatus().active,fx:typeof JasprMeleeFixture})'],
  ['eval', "fetch('/cmd?c=mobends%20walk').then(r=>r.text())"], ['wait', 500],
  ['eval', "fetch('/cmd?c=mobends%20tp%200.5%2064%203.8%200%206').then(r=>r.text())"], ['wait', 2000]];
if (slot && slot !== '1') steps.push(['key', 'Digit' + slot, 48 + Number(slot), slot], ['wait', 1200]);
for (const spec of shots) {
  const [clip, p] = spec.split('@');
  const stamp = { id: ids[family], tick: 0, sequence: 1, type: family, key: 'probe', bound: true, side: 'right', clip, duration: durations[family], start: null };
  const js = '(function(){var M=JasprMeleeFixture,st=' + JSON.stringify(stamp) + ';M.current=function(main){return main?st:null;};' +
    'M.restyle=function(s){return s;};M.phase=function(){return ' + Number(p) + ';};return "' + clip + ' ' + p + '";})()';
  steps.push(['eval', js], ['wait', 450], ['shot', clip + '-' + p]);
}
steps.push(['eval', 'JSON.stringify(JasprBetterCombatStatus().visual)']);
fs.writeFileSync(out, JSON.stringify(steps));
console.log(steps.length, 'steps');
