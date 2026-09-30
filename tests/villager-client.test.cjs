'use strict';
// Villager right-click crash (scripts/build-villager-client.cjs): the shipped classes.js carries the fix, the script is
// idempotent and exactly reversible, and -- running the real client code in a VM after its own bootstrap -- no
// profession leaves a villager's trade list null any more (a nitwit did, which crashed processInteract), no lookup
// throws, and farmer .. butcher get exactly the trades they got before.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const {build, revert, isFixed, MARKER} = require(path.join(root, 'scripts', 'build-villager-client.cjs'));
const {CLIENT_BUILD} = require('./client-build.cjs');
const live = () => fs.readFileSync(path.join(root, 'site', 'classes.js'), 'latin1');

/** Loads a client into a VM, runs TeaVM's static initialisers (from main) and Bootstrap.register, then builds the
 * villager trade table (EntityVillager.bootstrap) the way register2 does. Returns an evaluator inside the module. */
function boot(text) {
  const main = text.slice(text.indexOf('function GNc('), text.indexOf('function GNc(') + 6000);
  const m = main.match(/case 0:((?:[\w$]+\(\);)+)b=b\.data;((?:[\w$]+\(\);)+)\$p\s*=\s*1;case 1:a:\{FsB\(\);if\(B\(\)\)\{break _;\}((?:[\w$]+\(\);)+)c\s*=\s*b\.length/);
  assert.ok(m, 'main static initialiser list found');
  const tail = text.lastIndexOf('}));');
  const src = text.slice(0, tail) + ';$rt_exports.__jasprEval=function(s){return eval(s);};\n' + text.slice(tail);
  const ctx = {exports: {}, console: {log() {}, info() {}, warn() {}, error() {}, debug() {}}, setTimeout, clearTimeout, setInterval, clearInterval};
  ctx.global = ctx;
  vm.createContext(ctx);
  vm.runInContext(src, ctx, {filename: 'classes.js'});
  const E = ctx.exports.__jasprEval;
  E(m[1] + m[2] + 'FsB();' + m[3]);
  E('Fga();EvX();');
  assert.equal(E('KFH.clU.data.length'), 5, 'the client trade table lists farmer .. butcher only');
  // Record every exception a catch block converts, so the swallowed ones inside populateBuyingList are visible.
  E('globalThis.__jasprSeen=[];F=(function(wrap){return function(e){__jasprSeen.push(String(e&&e.message||e));return wrap(e);};})(F);');
  return E;
}

/** populateBuyingList on a villager of each profession, many random seeds: career, trade count, null list, exceptions. */
function populate(E) {
  return E(`(function(){
    var real=FRt,out=[];
    FRt=function(a){return a.__profession;};
    try{
      for(var p=0;p<6;p++)for(var seed=1;seed<=40;seed++){
        var r=new Ff();A2S(r,N(seed*7919));
        var v={v4:null,Yt:0,bk1:0,h:r,__profession:p};
        __jasprSeen.length=0;
        DF_(v);
        var row={p:p,seed:seed,nullList:v.v4===null,career:v.Yt,level:v.bk1,thrown:__jasprSeen.slice()};
        if(v.v4!==null){row.empty=E3i(v.v4)===1;row.trades=v.v4.g;}
        out.push(row);
      }
    }finally{FRt=real;}
    return JSON.stringify(out);
  })()`);
}

test('site/classes.js carries the villager fix; the stage is idempotent and exactly reversible', () => {
  const text = live();
  assert.ok(isFixed(text) && text.split(MARKER).length === 2);
  assert.equal(build(text), text, 'rebuilding the installed client changes nothing');
  const before = revert(text);
  assert.ok(!isFixed(before));
  assert.equal(build(before), text, 'building from the old client gives the installed one');
  new vm.Script(text);
  assert.ok(fs.readFileSync(path.join(root, 'site', 'client.html'), 'utf8').includes('classes.js?v=' + CLIENT_BUILD), 'browsers fetch the fixed client');
});

test('stage refuses a client whose villager code changed', () => {
  const old = revert(live());
  assert.throws(() => build(old.replace('function Hdv(){var a=new Bit();GPm(a);return a;}', () => 'function Hdv(){var a=new Bit();return a;}')), /re-audit/);
  assert.throws(() => build(old.replace('case 3:a:{try{$z=ACy(b,c);if(B()){break _;}b=$z;b=b;if(a.Yt){', () => 'case 3:a:{try{$z=ACy(b,c);if(B()){break _;}b=$z;if(a.Yt){')), /re-audit/);
});

// Known and harmless: the So Many Enchantments client stage builds its enchantments without getMinEnchantability
// (si), so a smith's client-side enchanted-item trade throws inside populateBuyingList -- after the list exists and
// holds the smith's first trade. The client list only decides whether the click counts as handled; the server trades.
const KNOWN = /\bsi is not a function/;

test('real client code: every profession gets a trade list, nitwits no longer fail, farmer .. butcher unchanged', { timeout: 120000 }, () => {
  const text = live();
  const broken = JSON.parse(populate(boot(revert(text))));
  const E = boot(text);
  const fixed = JSON.parse(populate(E));
  const tableCareers = JSON.parse(E('JSON.stringify(KFH.clU.data.map(function(p){return p.bl();}))'));

  // The old client reproduces the crash condition: a nitwit's list stays null after populateBuyingList.
  const oldNitwits = broken.filter(r => r.p === 5);
  assert.ok(oldNitwits.every(r => r.nullList && r.thrown.length === 1), 'old client: nitwit list stays null (the crash)');

  for (const r of fixed) {
    assert.equal(r.nullList, false, `profession ${r.p} seed ${r.seed}: list is never null`);
    for (const t of r.thrown) assert.match(t, KNOWN, `profession ${r.p} seed ${r.seed}: only the known enchantment gap`);
    if (r.thrown.length) assert.ok(r.p === 3 && !r.empty, 'the enchantment gap only hits smiths, whose list is already filled');
  }
  for (const r of fixed.filter(r => r.p === 5)) assert.ok(r.empty && r.career === 0 && !r.thrown.length, 'nitwit: empty list, like vanilla');
  const careers = {};
  for (const r of fixed.filter(r => r.p < 5)) {
    assert.ok(!r.empty && r.trades > 0 && r.level === 1, `profession ${r.p}: level-1 trades`);
    (careers[r.p] = careers[r.p] || new Set()).add(r.career);
  }
  assert.deepEqual(Object.values(careers).map(s => s.size), tableCareers, 'every career of every profession was exercised');
  assert.deepEqual(fixed.filter(r => r.p < 5), broken.filter(r => r.p < 5), 'farmer .. butcher get exactly the old trades');
});
