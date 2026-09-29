'use strict';
// Client performance patches (JASPR_PERF_V1, scripts/perf-client-patches.cjs): the shipped classes.js carries them, the
// patch script reproduces it exactly, and the geometry the patches rely on is right -- occlusion rays never hide a clear
// line of sight (exact segment/box reference), the camera position recovered from the modelview is exact, and the
// particle frustum never culls a particle whose centre is on screen.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const patches = require(path.join(root, 'scripts', 'perf-client-patches.cjs'));
const script = fs.readFileSync(path.join(root, 'scripts', 'perf-client-patches.cjs'), 'utf8');
const grab = (name) => {
  const i = script.indexOf('function ' + name + '(');
  assert.ok(i >= 0, name);
  let depth = 0, j = script.indexOf('{', i);
  for (; j < script.length; j++) { if (script[j] === '{') depth++; else if (script[j] === '}' && --depth === 0) break; }
  return script.slice(i, j + 1);
};
const FIELDS = ['h_','h$','ia','g4','h7','h9','h8','g3','h5','hy','h6','gy','lB','lD','lC','jU'];
const toObj = (m) => Object.fromEntries(FIELDS.map((f, i) => [f, m[i]]));
const mul = (a, b) => { const r = new Array(16).fill(0); for (let c = 0; c < 4; c++) for (let rr = 0; rr < 4; rr++) for (let k = 0; k < 4; k++) r[c * 4 + rr] += a[k * 4 + rr] * b[c * 4 + k]; return r; };
const rotY = (a) => { const c = Math.cos(a), s = Math.sin(a); return [c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]; };
const rotX = (a) => { const c = Math.cos(a), s = Math.sin(a); return [1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]; };
const trans = (x, y, z) => [1,0,0,0, 0,1,0,0, 0,0,1,0, x,y,z,1];
const perspective = (fovY, aspect, near, far) => { const f = 1 / Math.tan(fovY / 2), m = new Array(16).fill(0); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; };
let seed = 20260929;
const rand = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };

test('site/classes.js carries every JASPR_PERF_V1 patch and parses', () => {
  const src = fs.readFileSync(path.join(root, 'site', 'classes.js'), 'utf8');
  assert.ok(src.includes(patches.MARKER));
  for (const name of Object.keys(patches.PATCHES)) assert.ok(src.includes(`JASPR_PERF_V1_BEGIN ${name}`), name);
  assert.doesNotThrow(() => new Function(src));
  assert.match(fs.readFileSync(path.join(root, 'site', 'client.html'), 'utf8'), /classes\.js\?v=20260929-perf1/);
});

test('patch script is idempotent on the shipped client', () => {
  const src = fs.readFileSync(path.join(root, 'site', 'classes.js'), 'utf8');
  assert.equal(patches.apply(src).changed, false);
  const git = spawnSync('git', ['-C', root, 'show', 'HEAD~0:site/classes.js'], { encoding: 'utf8', maxBuffer: 64 << 20 });
  if (git.status === 0 && !git.stdout.includes(patches.MARKER)) assert.equal(patches.apply(git.stdout).source, src);
});

test('occlusion rays never hide a clear line of sight', () => {
  let solid = new Set();
  const api = new Function('solidFn', 'stats', `var JasprPerfStats=stats;var JasprPerfEyeX=0,JasprPerfEyeY=0,JasprPerfEyeZ=0;function JasprPerfSolid(x,y,z){return solidFn(x,y,z);}
${grab('JasprPerfRay')}\n${grab('JasprPerfBoxVisible')}
return {ray:JasprPerfRay,box:function(ex,ey,ez,a,b,c,d,e,f){JasprPerfEyeX=ex;JasprPerfEyeY=ey;JasprPerfEyeZ=ez;return JasprPerfBoxVisible(a,b,c,d,e,f);}};`)((x, y, z) => solid.has(`${x},${y},${z}`), { occRays: 0 });
  const wall = (x, y0, y1, z0, z1) => { for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) solid.add(`${x},${y},${z}`); };
  solid = new Set(); wall(10, 50, 80, -20, 20);
  assert.equal(api.box(0.5, 65.6, 0.5, 20, 64, 0, 20.6, 65.8, 0.6), false, 'behind a full wall');
  solid.delete('10,65,0');
  assert.equal(api.box(0.5, 65.6, 0.5, 20, 64, 0, 20.6, 65.8, 0.6), true, 'through a one-block window');
  solid = new Set(['20,64,0']);
  assert.equal(api.ray(0.5, 65.6, 0.5, 20.5, 64.5, 0.5), true, 'the target block itself never hides it');
  const blocked = (e, g) => { // exact reference: does the segment pass through the interior of a solid voxel?
    const T = g.map(Math.floor).join(',');
    for (const key of solid) {
      if (key === T) continue;
      const b = key.split(',').map(Number); let t0 = 0, t1 = 1, ok = true;
      for (let i = 0; i < 3 && ok; i++) {
        const d = g[i] - e[i], lo = b[i] + 1e-9, hi = b[i] + 1 - 1e-9;
        if (Math.abs(d) < 1e-12) { if (e[i] <= lo || e[i] >= hi) ok = false; continue; }
        let a = (lo - e[i]) / d, c = (hi - e[i]) / d; if (a > c) [a, c] = [c, a];
        t0 = Math.max(t0, a); t1 = Math.min(t1, c); if (t0 >= t1) ok = false;
      }
      if (ok) return true;
    }
    return false;
  };
  let falseHide = 0, falseShow = 0;
  for (let t = 0; t < 20000; t++) {
    solid = new Set();
    for (let k = 0; k < 40; k++) solid.add(`${Math.floor(rand() * 20 - 10)},${Math.floor(rand() * 6 + 62)},${Math.floor(rand() * 20 - 10)}`);
    const e = [rand() * 20 - 10, 62 + rand() * 6, rand() * 20 - 10], g = [rand() * 20 - 10, 62 + rand() * 6, rand() * 20 - 10];
    if (solid.has(e.map(Math.floor).join(','))) continue;
    const clear = api.ray(e[0], e[1], e[2], g[0], g[1], g[2]), exact = blocked(e, g);
    if (!clear && !exact) falseHide++;
    if (clear && exact) falseShow++;
  }
  assert.equal(falseHide, 0);
  assert.equal(falseShow, 0);
});

test('occlusion camera position is recovered exactly from the modelview', () => {
  const api = new Function(`var HEH={X:{},G:{lv:0}},HKM,HKD=0,LoE=0,LoF=0,LoG=0,JasprPerfOccOk=false,JasprPerfOccFrameNo=0,JasprPerfOccBudget=0,JasprPerfOccBroken=false,JasprPerfEyeX=0,JasprPerfEyeY=0,JasprPerfEyeZ=0,JasprPerfOccWorld=null,JasprPerfCX=0,JasprPerfCZ=0,JasprPerfCh=null,JasprPerfOccMat=new Float32Array(16);
var JasprPerfFields=${JSON.stringify(FIELDS)};function JasprPerfOn(){return true;}
${grab('JasprPerfOccFrame')}
return function(mv,le,lf,lg,third){HEH.G.lv=third?1:0;HKM={data:[mv]};LoE=le;LoF=lf;LoG=lg;JasprPerfOccFrame();return [JasprPerfOccOk,JasprPerfEyeX,JasprPerfEyeY,JasprPerfEyeZ];};`)();
  for (let t = 0; t < 500; t++) {
    const feet = [rand() * 2000 - 1000, 60 + rand() * 40, rand() * 2000 - 1000], eye = [(rand() - 0.5) * 0.2, 1.54 + rand() * 0.1, (rand() - 0.5) * 0.2];
    const view = mul(mul(rotX((rand() - 0.5) * 3), rotY(rand() * 6.3)), trans(-eye[0], -eye[1], -eye[2]));
    const [ok, x, y, z] = api(toObj(view), feet[0], feet[1], feet[2], false);
    assert.ok(ok);
    assert.ok(Math.abs(x - feet[0] - eye[0]) < 1e-3 && Math.abs(y - feet[1] - eye[1]) < 1e-3 && Math.abs(z - feet[2] - eye[2]) < 1e-3);
  }
  assert.equal(api(toObj(trans(0, -1.62, 0)), 0, 64, 0, true)[0], false, 'third person: no occlusion culling');
});

test('particle frustum never culls a particle whose centre is on screen', () => {
  const api = new Function(`var HKM,HKD=0,HKQ,HKF=0,LoV=0,LoW=0,LoX=0,JasprPerfTick=0,JasprPerfPlanesOk=false,JasprPerfPlanes=new Float32Array(20),JasprPerfMat=new Float32Array(16),JasprPerfProj=new Float32Array(16),JasprPerfClip=new Float32Array(16),JasprPerfStats={particlesSeen:0,particlesCulled:0};
var JasprPerfFields=${JSON.stringify(FIELDS)};var $rt_globals=globalThis;function JasprPerfOn(){return true;}
${grab('JasprPerfParticleFrame')}\n${grab('JasprPerfParticleVisible')}
return {set:function(mv,pr){HKM={data:[mv]};HKQ={data:[pr]};JasprPerfParticleFrame();return JasprPerfPlanesOk;},vis:function(x,y,z){return JasprPerfParticleVisible({kX:x,d1:x,iR:y,db:y,kW:z,d0:z,er:1},0);}};`)();
  let wrong = 0;
  for (let t = 0; t < 200; t++) {
    const view = mul(rotX((rand() - 0.5) * Math.PI * 0.95), rotY(rand() * 2 * Math.PI)), proj = perspective((50 + rand() * 60) * Math.PI / 180, 1 + rand(), 0.05, 256);
    assert.ok(api.set(toObj(view), toObj(proj)));
    const clip = mul(proj, view);
    for (let k = 0; k < 200; k++) {
      const p = [(rand() - 0.5) * 60, (rand() - 0.5) * 60, (rand() - 0.5) * 60];
      const c = [0, 1, 2, 3].map((r) => clip[r] * p[0] + clip[4 + r] * p[1] + clip[8 + r] * p[2] + clip[12 + r]);
      if (c[3] > 0.05 && Math.abs(c[0]) <= c[3] && Math.abs(c[1]) <= c[3] && !api.vis(p[0], p[1], p[2])) wrong++;
    }
  }
  assert.equal(wrong, 0);
  api.set(toObj(rotY(0)), toObj(perspective(70 * Math.PI / 180, 16 / 9, 0.05, 256)));
  assert.equal(api.vis(0, 0, 10), false, 'behind the camera is culled');
  assert.equal(api.vis(0, 0, -10), true, 'straight ahead is drawn');
});
