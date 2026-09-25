'use strict';
// Z-fighting guard for cuboid item models (2026-09-25, owner report: the Tempest flickered while walking).
//
// Two elements whose faces point the same way and lie in the same plane with overlapping area render at
// identical depth, so the GPU alternates between them frame to frame. For every such pair the element with
// the smaller face (the detail: rail, band, grip wrap, bolt) is pushed out along that face's normal by EPS
// model units (1/16 px = 1/256 block ... EPS 0.05 is ~1/320 of a block), until no pair is left. Rotated
// elements are left alone. Deterministic: same input, same output.
const EPS = 0.05;
const AXIS = { west: [0, 'from', -1], east: [0, 'to', 1], down: [1, 'from', -1], up: [1, 'to', 1], north: [2, 'from', -1], south: [2, 'to', 1] };
const round = v => Math.round(v * 10000) / 10000;

function faces(elements) {
  const out = [];
  elements.forEach((e, i) => {
    if (e.rotation && e.rotation.angle) return;
    for (const [dir, [axis, end]] of Object.entries(AXIS)) {
      if (!e.faces || !e.faces[dir]) continue;
      const [a, b] = [0, 1, 2].filter(k => k !== axis);
      out.push({ i, dir, v: e[end][axis], a: [e.from[a], e.to[a]], b: [e.from[b], e.to[b]],
        area: (e.to[a] - e.from[a]) * (e.to[b] - e.from[b]) });
    }
  });
  return out;
}

/** Same-direction coplanar overlapping face pairs as [smallerElementIndex, direction]. */
function conflicts(elements) {
  const f = faces(elements), found = [];
  for (let p = 0; p < f.length; p++) for (let q = p + 1; q < f.length; q++) {
    const A = f[p], B = f[q];
    if (A.i === B.i || A.dir !== B.dir || Math.abs(A.v - B.v) > 1e-6) continue;
    const oa = Math.min(A.a[1], B.a[1]) - Math.max(A.a[0], B.a[0]);
    const ob = Math.min(A.b[1], B.b[1]) - Math.max(A.b[0], B.b[0]);
    if (oa <= 1e-6 || ob <= 1e-6) continue;
    const small = A.area < B.area - 1e-9 ? A : B.area < A.area - 1e-9 ? B : (A.i > B.i ? A : B);
    found.push([small.i, small.dir]);
  }
  return found;
}

/** Returns new elements with every z-fighting pair separated; the input is not modified. */
function separate(elements) {
  const out = elements.map(e => JSON.parse(JSON.stringify(e)));
  for (let pass = 0; pass < 64; pass++) {
    const list = conflicts(out);
    if (!list.length) return out;
    const done = new Set();
    for (const [i, dir] of list) {
      const key = i + dir;
      if (done.has(key)) continue;
      done.add(key);
      const [axis, end, sign] = AXIS[dir];
      out[i][end][axis] = round(out[i][end][axis] + sign * EPS);
    }
  }
  throw new Error('z-fighting did not converge');
}

module.exports = { EPS, conflicts, separate };
