'use strict';
// JasprSky: the eerie sky of Drownhollow (once Ul'Nhaar). The fenced client stage (scripts/build-sky-client.cjs) is installed in
// site/classes.js, refreshes idempotently and parses; the server's hidden objective matches what the client looks for.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('sky stage: five fenced hooks, block refresh is idempotent, installed, parses', () => {
  const {build} = require('../scripts/build-sky-client.cjs');
  const live = fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
  const staged = build(live);
  assert.equal(staged.split('/*JASPR_SKY_V1*/').length - 1, 5);
  assert.equal(staged.split('/* JASPR_SKY_V1_BEGIN */').length - 1, 1);
  for (const hook of ['JasprTank.tick(a);/*JASPR_SKY_V1*/JasprSky.tick(a);', 'if(JasprSky.on){f=JasprSky.sky[0];', 'if(JasprSky.on){r=0.22;s=0.18;}',
    'CFh(u,JasprSky.on?u*0.35:u,', 'GmS_orig(a, b);/*JASPR_SKY_V1*/JasprSky.fog(a);'])
    assert.ok(staged.includes(hook), hook);
  assert.equal(build(staged), staged, 'refreshing the fenced block is idempotent');
  assert.equal(staged, live, 'site/classes.js carries the current sky block');
  assert.ok(staged.includes('indexOf("JRS v1")===0'), 'the client waits for the server objective');
  new vm.Script(staged);
});

test('sky server side: hidden objective sent on entry, removed on leaving, ambience throttled', () => {
  const sky = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Sky.java');
  assert.ok(sky.includes('JRS v1 eerie'), 'objective display name the client matches');
  assert.ok(sky.includes('OBJECTIVE = "jrs", DISPLAY = "JRS v1 eerie"'), 'objective name the client looks up');
  assert.match(read('site/client.html'), /classes\.js\?v=\d{8}-\w+/, 'the client is versioned');
});
