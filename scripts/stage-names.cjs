'use strict';
// Free identifiers of a stage's module text (the engine names it relies on): comments, strings, template-free code and
// regular-expression literals are stripped first, then declared names, property names, object keys and labels are
// dropped. Used by the melee module and JASPR_COMBATFX builders to check every engine name exists in the client.
const JS_GLOBALS = new Set(('Object Array Math String Number Boolean JSON Error TypeError RangeError SyntaxError Map Set WeakMap WeakSet ' +
  'Proxy Reflect Date BigInt DataView RegExp Uint8Array Int8Array Uint16Array Int16Array Int32Array Uint32Array Float32Array Float64Array ' +
  'TextEncoder TextDecoder isFinite isNaN parseInt parseFloat undefined NaN Infinity console arguments this true false null typeof ' +
  'instanceof new return var function if else for while do break continue switch case default throw try catch finally in of delete void ' +
  'let const class extends super yield await async with debugger import export static get set').split(' '));
const REGEX_BEFORE = new Set(['(', ',', '=', ':', '[', '!', '&', '|', '?', '{', '}', ';', '+', '-', '*', '%', '<', '>', '~', '^', 'return', 'typeof', 'case', 'do', 'else', 'in', 'of', 'new', 'delete', 'void', 'throw']);
// replace comments, strings and regex literals with neutral tokens
function strip(src) {
  let out = '', i = 0, prev = '';
  const n = src.length;
  const isIdChar = c => /[\w$]/.test(c);
  while (i < n) {
    const c = src[i], d = src[i + 1];
    if (c === '/' && d === '*') { const e = src.indexOf('*/', i + 2); i = e < 0 ? n : e + 2; out += ' '; continue; }
    if (c === '/' && d === '/') { const e = src.indexOf('\n', i + 2); i = e < 0 ? n : e; out += ' '; continue; }
    if (c === '"' || c === "'" || c === '`') {
      let j = i + 1; while (j < n && src[j] !== c) { if (src[j] === '\\') j++; j++; }
      i = j + 1; out += '""'; prev = '"'; continue;
    }
    if (c === '/' && (prev === '' || REGEX_BEFORE.has(prev))) {
      let j = i + 1, cls = false;
      while (j < n) { const ch = src[j]; if (ch === '\\') { j += 2; continue; } if (ch === '[') cls = true; else if (ch === ']') cls = false; else if (ch === '/' && !cls) break; else if (ch === '\n') break; j++; }
      j++; while (j < n && /[a-z]/.test(src[j])) j++;
      i = j; out += ' RE '; prev = 'RE'; continue;
    }
    if (isIdChar(c)) {
      let j = i; while (j < n && isIdChar(src[j])) j++;
      const word = src.slice(i, j); out += word; prev = /^\d/.test(word) ? '0' : REGEX_BEFORE.has(word) ? word : 'id'; i = j; continue;
    }
    if (!/\s/.test(c)) prev = c === ')' || c === ']' ? ')' : c;
    out += c; i++;
  }
  return out;
}
function engineNames(src) {
  const code = strip(src);
  const toks = [...code.matchAll(/[A-Za-z_$][\w$]*|\d[\w.]*|\S/g)].map(m => m[0]);
  const declaredHere = new Set(['RE']), used = new Set();
  const isId = t => /^[A-Za-z_$][\w$]*$/.test(t);
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i];
    if (t === 'var' || t === 'let' || t === 'const') {
      let depth = 0, expectName = true;
      for (let j = i + 1; j < toks.length; j++) {
        const u = toks[j];
        if (u === '(' || u === '[' || u === '{') depth++;
        else if (u === ')' || u === ']' || u === '}') { if (depth === 0) break; depth--; }
        else if (depth === 0 && (u === ';' || u === 'in' || u === 'of')) break;
        else if (depth === 0 && u === ',') { expectName = true; continue; }
        if (expectName && isId(u)) { declaredHere.add(u); expectName = false; }
        else if (u !== '=') expectName = false;
      }
    } else if (t === 'function') {
      let j = i + 1;
      if (isId(toks[j])) { declaredHere.add(toks[j]); j++; }
      if (toks[j] === '(') for (j++; j < toks.length && toks[j] !== ')'; j++) if (isId(toks[j])) declaredHere.add(toks[j]);
    } else if (t === 'catch' && toks[i + 1] === '(' && isId(toks[i + 2])) declaredHere.add(toks[i + 2]);
  }
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i];
    if (!isId(t) || JS_GLOBALS.has(t)) continue;
    if (toks[i - 1] === '.') continue;
    if ((toks[i - 1] === '{' || toks[i - 1] === ',') && toks[i + 1] === ':') continue;
    if (toks[i - 1] === 'break' || toks[i - 1] === 'continue') continue;
    if ((toks[i - 1] === ';' || toks[i - 1] === '}' || toks[i - 1] === '{') && toks[i + 1] === ':') continue;
    // getter / setter names in object literals: get name() / set name(v)
    if ((toks[i - 1] === 'get' || toks[i - 1] === 'set') && toks[i + 1] === '(') continue;
    used.add(t);
  }
  return [...used].filter(n => !declaredHere.has(n)).sort();
}
module.exports = { engineNames, strip };
