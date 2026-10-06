'use strict';
// Offline harness for the JASPR_MUTANTS stage: builds the candidate from the LIVE classes.js (read only), loads it in
// node:vm (scripts/mobends-native-harness.cjs; no browser, no WebGL, no network), runs TeaVM's eager static
// initialisers (what main() does before the game starts) and Bootstrap.register, which ends in the stage's registry
// hook. GL is stood in for by a matrix stack, like tests/mobends-native.test.cjs.
const os = require('node:os');
try { os.setPriority(os.constants.priority.PRIORITY_LOW); } catch (e) {}
const fs = require('node:fs');
const path = require('node:path');
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');
const builder = require('../scripts/build-mutants-client.cjs');

const LIVE = process.env.MUTANTS_CLASSES_JS || builder.LIVE;
const available = fs.existsSync(LIVE);
let cached = null;

function eagerInitialisers(source) {
  const gi = source.indexOf('function GNc(');
  const body = source.slice(gi, source.indexOf('$p=1;case 1:', gi));
  return body.slice(body.indexOf('case 0:') + 7).split(';').map(x => x.trim()).filter(x => /^[A-Za-z_$][\w$]*\(\)$/.test(x));
}

function load(options = {}) {
  if (cached && !options.fresh) return cached;
  const input = fs.readFileSync(LIVE, 'latin1');
  const built = builder.build(input);
  const client = loadClientSource(built.output, { filename: 'mutants-candidate.js' });
  const ev = client.evaluate;
  ev('Error.stackTraceLimit=60');
  for (const init of eagerInitialisers(built.output)) ev(init);
  ev('Fga()');                                                // Bootstrap.register -> JasprMutantsBridge.registry()
  const result = { client, fn: client.fn, ev, input, built, M: client.fn.JasprMutants };
  if (!options.fresh) cached = result;
  return result;
}

// A client world stand-in: what entity constructors and the stage's client code read.
function fakeWorld(ev) {
  return ev(`(function(){
    var rnd = new Ff(); EZg(rnd);                              // EaglercraftRandom
    var w = { r: 1, R: rnd, b4: { lE: function () { return { v0: 0 }; }, bc8: 0 }, gw: Bq(), $added: [] };
    w.baK = function (id) { var a = w.$added; for (var i = 0; i < a.length; i++) if (a[i].cu === id) return a[i]; return null; };
    return w;
  })()`);
}

module.exports = { load, available, LIVE, builder, fakeWorld, eagerInitialisers };
