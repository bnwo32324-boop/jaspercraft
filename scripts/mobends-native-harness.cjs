'use strict';
// Loads a TeaVM-compiled browser client bundle (site/classes.js) in node:vm -- no browser, no WebGL, no network -- and
// hands out its minified top-level bindings, so engine code (models, boxes, quads, ...) can be unit-tested on the real
// engine objects:
//
//   const {loadClient} = require('./mobends-native-harness.cjs');
//   const {fn} = loadClient('site/classes.js');
//   const model = new fn.OB(); fn.AB4(model, 0, 0, 64, 32);     // a real ModelBiped
//
// How it works
// * The bundle is a UMD wrapper around one module function. Just before that function closes, a block is appended that
//   returns a live accessor (getter + setter) for every top-level binding the name scan finds, plus a direct-eval hook
//   for anything else. Accessors stay live: static fields and TeaVM's self-replacing class-initialiser trampolines
//   always read their current value, and assigning fn.X rebinds X inside the bundle (handy for stubbing a call).
// * Evaluating the bundle only declares classes and functions and runs the load-time code of patched-in blocks. TeaVM's
//   entry point (exported as `main`) is never called, so the game, its threads and its eager static initialisers never
//   start. Lazy class initialisers still run on first use, exactly as in the browser.
// * The sandbox is given no Node APIs (no require, process, module, exports). node:vm is not a security boundary;
//   the bundle is trusted code. Evaluation first runs in the 'minimal' profile; if it throws there, it is retried once
//   in a fresh context with the 'browser' profile (inert window, document, DOM classes, navigator, storage, media and
//   network stubs). No stub reaches the network. Timers, animation frames, idle callbacks and microtasks queued
//   through the stubs are only recorded -- they never fire and no real Node timer is created -- so a loaded client can
//   neither keep the process alive nor run code behind a test's back.
// * Nothing is cached or written to disk.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {performance: hostPerformance} = require('node:perf_hooks');

const EXPORT_KEY = '__nativeHarnessExports';
const MAX_LOGS = 1000;
const RESERVED = new Set((
  'arguments await break case catch class const continue debugger default delete do else enum eval export extends ' +
  'false finally for function if implements import in instanceof interface let new null package private protected ' +
  'public return static super switch this throw true try typeof var void while with yield').split(' '));
const MODULE_HEAD = /function\s*\(\s*\$rt_globals\s*,\s*\$rt_exports\s*\)\s*\{/;

/** Candidate top-level names. Generous on purpose (TeaVM wraps lines anywhere, e.g. "var T\n=..."); names that do not
 * resolve inside the module function are dropped after evaluation. */
function scanNames(source) {
  const found = new Set();
  const patterns = [
    /\bfunction\s*\*?\s*([A-Za-z_$][\w$]*)\s*\(/g,          // function declarations
    /\b(?:var|let|const|class)\s+([A-Za-z_$][\w$]*)/g,      // first binding of a declaration
    /[,;{}]\s*([A-Za-z_$][\w$]*)\s*=(?![=>])/g,             // later bindings of "var a=1,b=2" lists
  ];
  for (const re of patterns) for (const m of source.matchAll(re)) found.add(m[1]);
  return [...found].filter((n) => !RESERVED.has(n) && !n.startsWith('$harness$'));
}

/** Offset of the "}));" that closes the UMD module function (only whitespace and comments may follow it), or -1. */
function moduleEnd(source) {
  let end = source.length;
  for (let guard = 0; guard < 64; guard++) {
    while (end > 0 && /\s/.test(source[end - 1])) end--;
    const lineStart = source.lastIndexOf('\n', end - 1) + 1;
    if (/^\s*\/\//.test(source.slice(lineStart, end))) { end = lineStart; continue; }
    if (end >= 2 && source.startsWith('*/', end - 2)) {
      const open = source.lastIndexOf('/*', end - 3);
      if (open >= 0) { end = open; continue; }
    }
    break;
  }
  return end >= 4 && source.startsWith('}));', end - 4) ? end - 4 : -1;
}

function harnessBlock(names) {
  const snapshot = names.map((n) => `typeof ${n}!=="undefined"?${n}:void 0`).join(',\n');
  const live = names.map((n) => `get ${n}(){return ${n}},set ${n}($harness$v){${n}=$harness$v}`).join(',\n');
  return `\n;$rt_exports[${JSON.stringify(EXPORT_KEY)}]={evaluate:function($harness$src){return eval($harness$src);},` +
    `snapshot:function(){return[\n${snapshot}];},\nlive:{\n${live}}};\n`;
}

function describe(value) {
  try {
    const text = value && typeof value === 'object' && typeof value.stack === 'string' ? value.stack : String(value);
    return text.length > 2000 ? text.slice(0, 2000) + '...' : text;
  } catch (e) {
    return '[unprintable]';
  }
}

function makeConsole(logs, forward) {
  const write = (level, args) => {
    if (logs.length >= MAX_LOGS) logs.shift();
    logs.push({level, text: args.map(describe).join(' ')});
    if (forward && typeof forward[level] === 'function') forward[level](...args);
  };
  const out = {};
  for (const level of ['log', 'info', 'warn', 'error', 'debug', 'trace']) out[level] = (...args) => write(level, args);
  out.dir = out.table = (...args) => write('log', args);
  out.assert = (ok, ...args) => { if (!ok) write('error', ['Assertion failed', ...args]); };
  for (const name of ['group', 'groupCollapsed', 'groupEnd', 'time', 'timeEnd', 'timeLog', 'count', 'countReset',
    'clear', 'profile', 'profileEnd']) out[name] = () => {};
  return out;
}

/** Inert scheduling: callbacks are recorded with an id and never run; no Node timer is ever created. */
function makeScheduler() {
  let nextId = 1;
  let disposed = false;
  const timers = new Map();
  const frames = new Map();
  const idle = new Map();
  const microtasks = [];
  const record = (map, entry) => { const id = nextId++; if (!disposed) map.set(id, entry); return id; };
  const forget = (map) => (id) => { map.delete(id); };
  return {
    base: {
      setTimeout: (callback, delay) => record(timers, {kind: 'timeout', callback, delay: Number(delay) || 0}),
      setInterval: (callback, delay) => record(timers, {kind: 'interval', callback, delay: Number(delay) || 0}),
      clearTimeout: forget(timers),
      clearInterval: forget(timers),
      requestAnimationFrame: (callback) => record(frames, {callback}),
      cancelAnimationFrame: forget(frames),
      queueMicrotask: (callback) => { if (!disposed) microtasks.push(callback); },
    },
    browser: {
      requestIdleCallback: (callback) => record(idle, {callback}),
      cancelIdleCallback: forget(idle),
    },
    pending: () => ({timers: timers.size, frames: frames.size, idle: idle.size, microtasks: microtasks.length}),
    dispose() {
      disposed = true;
      timers.clear(); frames.clear(); idle.clear(); microtasks.length = 0;
    },
  };
}

function makePerformance() {
  const start = hostPerformance.now();
  return {
    timeOrigin: hostPerformance.timeOrigin + start,
    now: () => hostPerformance.now() - start,
    mark() {}, measure() {}, clearMarks() {}, clearMeasures() {},
    getEntries: () => [], getEntriesByName: () => [], getEntriesByType: () => [],
  };
}

class MemoryStorage {
  #items = new Map();
  get length() { return this.#items.size; }
  key(index) { const key = [...this.#items.keys()][index]; return key === undefined ? null : key; }
  getItem(key) { key = String(key); return this.#items.has(key) ? this.#items.get(key) : null; }
  setItem(key, value) { this.#items.set(String(key), String(value)); }
  removeItem(key) { this.#items.delete(String(key)); }
  clear() { this.#items.clear(); }
}

const never = () => new Promise(() => {});   // a request that never settles: no network, no rejection, no handle
const noop = () => {};

function inertElement(tagName, documentRef) {
  const tag = String(tagName || 'div').toUpperCase();
  return {
    tagName: tag, nodeName: tag, nodeType: 1, id: '', className: '', innerHTML: '', innerText: '', textContent: '',
    value: '', src: '', href: '', width: 0, height: 0, clientWidth: 0, clientHeight: 0, offsetWidth: 0, offsetHeight: 0,
    scrollWidth: 0, scrollHeight: 0, scrollTop: 0, scrollLeft: 0, complete: false, naturalWidth: 0, naturalHeight: 0,
    style: {setProperty: noop, removeProperty: () => '', getPropertyValue: () => ''},
    dataset: {},
    classList: {add: noop, remove: noop, toggle: () => false, contains: () => false, replace: () => false},
    attributes: [], children: [], childNodes: [], parentNode: null, parentElement: null, firstChild: null,
    lastChild: null, ownerDocument: documentRef || null,
    setAttribute: noop, removeAttribute: noop, getAttribute: () => null, hasAttribute: () => false,
    appendChild: (child) => child, removeChild: (child) => child, insertBefore: (child) => child,
    replaceChild: (added, removed) => removed, append: noop, prepend: noop, remove: noop, contains: () => false,
    cloneNode: () => inertElement(tag, documentRef),
    addEventListener: noop, removeEventListener: noop, dispatchEvent: () => true,
    getBoundingClientRect: () => ({x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0}),
    getClientRects: () => [],
    querySelector: () => null, querySelectorAll: () => [], getElementsByTagName: () => [],
    getElementsByClassName: () => [],
    focus: noop, blur: noop, click: noop, scrollIntoView: noop, scrollTo: noop,
    getContext: () => null,                               // no WebGL, no 2D canvas
    toDataURL: () => 'data:,', toBlob: noop,
    requestPointerLock: noop, requestFullscreen: never,
    play: never, pause: noop, load: noop, canPlayType: () => '',
  };
}

class InertEvent {
  constructor(type, init) {
    Object.assign(this, init || {});
    this.type = String(type);
    this.defaultPrevented = false;
  }
  preventDefault() { this.defaultPrevented = true; }
  stopPropagation() {}
  stopImmediatePropagation() {}
}

class InertCustomEvent extends InertEvent {
  constructor(type, init) {
    super(type, init);
    this.detail = init && init.detail !== undefined ? init.detail : null;
  }
}

class InertObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() { return []; }
}

class InertWebSocket {
  constructor(url) {
    this.url = String(url);
    this.protocol = '';
    this.extensions = '';
    this.readyState = 3;                                 // CLOSED: it never connects
    this.bufferedAmount = 0;
    this.binaryType = 'blob';
    this.onopen = this.onmessage = this.onerror = this.onclose = null;
  }
  send() {}
  close() {}
  addEventListener() {}
  removeEventListener() {}
  dispatchEvent() { return true; }
}
Object.assign(InertWebSocket, {CONNECTING: 0, OPEN: 1, CLOSING: 2, CLOSED: 3});

class InertXMLHttpRequest {
  constructor() {
    this.readyState = 0;
    this.status = 0;
    this.statusText = '';
    this.response = null;
    this.responseText = '';
    this.responseType = '';
    this.timeout = 0;
    this.withCredentials = false;
    this.upload = {addEventListener: noop, removeEventListener: noop};
    this.onload = this.onerror = this.onreadystatechange = this.onprogress = null;
  }
  open() {}
  send() {}                                              // never completes
  abort() {}
  setRequestHeader() {}
  getResponseHeader() { return null; }
  getAllResponseHeaders() { return ''; }
  overrideMimeType() {}
  addEventListener() {}
  removeEventListener() {}
}

class InertWorker {
  constructor() { this.onmessage = this.onerror = null; }
  postMessage() {}
  terminate() {}
  addEventListener() {}
  removeEventListener() {}
}

/** Empty DOM and event classes, for load-time feature checks and prototype patches (instanceof, X.prototype.y=...). */
function domClasses() {
  const named = (name, Base) => ({[name]: class extends Base {}})[name];
  class EventTarget {
    addEventListener() {}
    removeEventListener() {}
    dispatchEvent() { return true; }
  }
  const out = {EventTarget};
  out.Node = named('Node', EventTarget);
  out.Element = named('Element', out.Node);
  out.HTMLElement = named('HTMLElement', out.Element);
  for (const name of ['HTMLCanvasElement', 'HTMLImageElement', 'HTMLMediaElement', 'HTMLVideoElement',
    'HTMLAudioElement', 'HTMLInputElement', 'HTMLDivElement']) out[name] = named(name, out.HTMLElement);
  for (const name of ['KeyboardEvent', 'MouseEvent', 'WheelEvent', 'TouchEvent', 'PointerEvent', 'FocusEvent',
    'InputEvent', 'GamepadEvent']) out[name] = named(name, InertEvent);
  return out;
}

/** Stubs present in every profile: console, inert scheduling, a clock and the text codecs. */
function baseStubs(env) {
  return {
    console: env.console,
    ...env.scheduler.base,
    performance: makePerformance(),
    TextEncoder, TextDecoder, atob, btoa,
  };
}

/** Extra stubs for bundles whose load-time code insists on a page. All inert, none touches the network. */
function browserStubs(env) {
  const location = {
    href: 'about:blank', protocol: 'about:', host: '', hostname: '', port: '', pathname: 'blank', search: '',
    hash: '', origin: 'null', assign: noop, replace: noop, reload: noop, toString: () => 'about:blank',
  };
  const document = {
    nodeType: 9, readyState: 'complete', visibilityState: 'hidden', hidden: true, title: '', cookie: '',
    referrer: '', URL: 'about:blank', baseURI: 'about:blank', currentScript: null, activeElement: null,
    pointerLockElement: null, fullscreenElement: null, location,
    get defaultView() { return env.global; },
    createElement: (tag) => inertElement(tag, document),
    createElementNS: (ns, tag) => inertElement(tag, document),
    createTextNode: (text) => ({nodeType: 3, textContent: String(text)}),
    createDocumentFragment: () => inertElement('#document-fragment', document),
    createEvent: (type) => new InertEvent(type),
    getElementById: () => null, getElementsByTagName: () => [], getElementsByClassName: () => [],
    getElementsByName: () => [], querySelector: () => null, querySelectorAll: () => [],
    addEventListener: noop, removeEventListener: noop, dispatchEvent: () => true,
    hasFocus: () => false, exitPointerLock: noop, exitFullscreen: never,
  };
  document.documentElement = inertElement('html', document);
  document.head = inertElement('head', document);
  document.body = inertElement('body', document);
  const media = (query) => ({
    matches: false, media: String(query), onchange: null, addListener: noop, removeListener: noop,
    addEventListener: noop, removeEventListener: noop, dispatchEvent: () => true,
  });
  function Image(width, height) {
    const el = inertElement('img', document);
    el.width = width | 0;
    el.height = height | 0;
    return el;
  }
  function Audio(src) {
    const el = inertElement('audio', document);
    el.src = src === undefined ? '' : String(src);
    return el;
  }
  const self = {get() { return env.global; }, enumerable: true, configurable: true};
  const stubs = {
    document, location,
    navigator: {
      userAgent: 'Mozilla/5.0 (node:vm) native-harness', appName: 'Netscape', appVersion: '5.0', platform: '',
      vendor: '', language: 'en-US', languages: ['en-US'], hardwareConcurrency: 1, maxTouchPoints: 0,
      onLine: false, cookieEnabled: false, webdriver: true, doNotTrack: '1',
      getGamepads: () => [], vibrate: () => false, sendBeacon: () => false, javaEnabled: () => false,
    },
    history: {length: 1, state: null, pushState: noop, replaceState: noop, back: noop, forward: noop, go: noop},
    screen: {
      width: 1280, height: 720, availWidth: 1280, availHeight: 720, colorDepth: 24, pixelDepth: 24,
      orientation: {type: 'landscape-primary', angle: 0, addEventListener: noop, removeEventListener: noop},
    },
    localStorage: new MemoryStorage(),
    sessionStorage: new MemoryStorage(),
    matchMedia: media,
    devicePixelRatio: 1, innerWidth: 1280, innerHeight: 720, outerWidth: 1280, outerHeight: 720,
    scrollX: 0, scrollY: 0, pageXOffset: 0, pageYOffset: 0, isSecureContext: false, origin: 'null', name: '',
    closed: false, opener: null, frameElement: null,
    addEventListener: noop, removeEventListener: noop, dispatchEvent: () => true, postMessage: noop,
    getComputedStyle: () => ({getPropertyValue: () => ''}), getSelection: () => null,
    scrollTo: noop, scroll: noop, focus: noop, blur: noop, open: () => null, close: noop, print: noop,
    alert: noop, confirm: () => false, prompt: () => null,
    Image, Audio,
    WebSocket: InertWebSocket, XMLHttpRequest: InertXMLHttpRequest, fetch: never, Worker: InertWorker,
    Event: InertEvent, CustomEvent: InertCustomEvent, ...domClasses(),
    MutationObserver: class MutationObserver extends InertObserver {},
    ResizeObserver: class ResizeObserver extends InertObserver {},
    IntersectionObserver: class IntersectionObserver extends InertObserver {},
    PerformanceObserver: class PerformanceObserver extends InertObserver {},
    URL, URLSearchParams,
    crypto: {
      getRandomValues(array) {
        const bits = array.BYTES_PER_ELEMENT * 8;
        for (let i = 0; i < array.length; i++) {
          array[i] = bits > 32 ? BigInt(Math.floor(Math.random() * 2 ** 32)) : Math.floor(Math.random() * 2 ** bits);
        }
        return array;
      },
    },
    ...env.scheduler.browser,
  };
  for (const name of ['window', 'self', 'top', 'parent', 'frames']) Object.defineProperty(stubs, name, self);
  return stubs;
}

/** Merges stub sets by descriptor, so lazy getters (window -> the context's global) are not read early. */
function mergeStubs(...parts) {
  const out = {};
  for (const part of parts) Object.defineProperties(out, Object.getOwnPropertyDescriptors(part));
  return out;
}

/** Installs stubs as accessors so the harness can report which globals the bundle actually read. */
function installStubs(sandbox, stubs, touched) {
  for (const name of Object.keys(stubs)) {
    const desc = Object.getOwnPropertyDescriptor(stubs, name);
    let replaced = false;
    let replacement;
    Object.defineProperty(sandbox, name, {
      configurable: true,
      enumerable: false,
      get() {
        touched.add(name);
        if (replaced) return replacement;
        return desc.get ? desc.get.call(stubs) : desc.value;
      },
      set(value) { replaced = true; replacement = value; },
    });
  }
}

function evaluateIn(profile, script, names, options) {
  const logs = [];
  const touched = new Set();
  const scheduler = makeScheduler();
  const env = {global: undefined, scheduler, console: makeConsole(logs, options.console)};
  const stubs = profile === 'browser' ? mergeStubs(baseStubs(env), browserStubs(env)) : baseStubs(env);
  const sandbox = {};
  installStubs(sandbox, stubs, touched);
  const context = vm.createContext(sandbox, {name: 'teavm-client (' + profile + ')'});
  env.global = vm.runInContext('globalThis', context);
  touched.clear();
  const started = hostPerformance.now();
  try {
    script.runInContext(context, {timeout: options.timeoutMs, breakOnSigint: false});
  } catch (error) {
    scheduler.dispose();
    throw error;
  }
  const evalMs = hostPerformance.now() - started;
  const touchedDuringLoad = [...touched].sort();
  const exported = sandbox[EXPORT_KEY];
  delete sandbox[EXPORT_KEY];
  if (!exported || typeof exported.snapshot !== 'function') {
    scheduler.dispose();
    throw new Error('the bundle finished without reaching the harness block (did the module function return early?)');
  }
  // Snapshot with the sandbox's globals (stubs, and what the bundle put on window) hidden, so a scanned name that only
  // resolves to a global is not mistaken for a module binding. The snapshot only evaluates typeof and reads. Globals
  // are deleted through the context (an assignment inside the vm also lands on the context's own global object, which
  // a host-side delete would leave behind) and restored on the sandbox, where every lookup finds them again.
  const hidden = [];
  for (const key of Object.getOwnPropertyNames(sandbox)) {
    const desc = Object.getOwnPropertyDescriptor(sandbox, key);
    if (desc.configurable) hidden.push([key, desc]);
  }
  let values;
  try {
    vm.runInContext('(function(keys){for(var i=0;i<keys.length;i++)delete globalThis[keys[i]];})', context)(
      hidden.map(([key]) => key));
    values = exported.snapshot();
  } finally {
    for (const [key, desc] of hidden) Object.defineProperty(sandbox, key, desc);
    touched.clear();
    for (const name of touchedDuringLoad) touched.add(name);
  }
  const descriptors = Object.getOwnPropertyDescriptors(exported.live);
  const fn = Object.create(null);
  const kept = [];
  const classes = new Map();
  for (let i = 0; i < names.length; i++) {
    const value = values[i];
    if (value === undefined) continue;                  // not a module-scope binding (or still undefined after load)
    const name = names[i];
    const {get, set} = descriptors[name];
    Object.defineProperty(fn, name, {get, set, enumerable: true, configurable: true});
    kept.push(name);
    if (typeof value === 'function' && value.$meta && typeof value.$meta.name === 'string' &&
      !classes.has(value.$meta.name)) classes.set(value.$meta.name, name);
  }
  let reverse = null;
  return {
    fn,
    context: sandbox,
    names: kept,
    classes,
    profile,
    evalMs,
    logs,
    stubs: {touchedDuringLoad, touched: () => [...touched].sort()},
    evaluate: (code) => exported.evaluate(String(code)),
    nameOf(value) {
      if (reverse === null) {
        reverse = new Map();
        for (const name of kept) {
          const v = fn[name];
          if ((typeof v === 'function' || (v !== null && typeof v === 'object')) && !reverse.has(v)) reverse.set(v, name);
        }
      }
      return reverse.has(value) ? reverse.get(value) : null;
    },
    pending: scheduler.pending,
    dispose: scheduler.dispose,
  };
}

/**
 * Evaluates TeaVM bundle source text. options:
 *   filename   name used in stack traces (default 'classes.js')
 *   profile    'auto' (default: 'minimal', then 'browser' if that throws), 'minimal' or 'browser'
 *   console    a console-like object that also receives the bundle's console output (it is always captured in logs)
 *   timeoutMs  limit for evaluating the bundle (default 60000)
 */
function loadClientSource(source, options = {}) {
  const started = hostPerformance.now();
  const filename = options.filename || 'classes.js';
  const profile = options.profile || 'auto';
  if (!['auto', 'minimal', 'browser'].includes(profile)) throw new Error('unknown profile: ' + profile);
  if (typeof source !== 'string') throw new TypeError('bundle source must be a string');
  if (!MODULE_HEAD.test(source.slice(0, 4096))) {
    throw new Error(filename + ' is not a TeaVM UMD bundle (no function($rt_globals,$rt_exports){ wrapper)');
  }
  const end = moduleEnd(source);
  if (end < 0) throw new Error(filename + ' is not a TeaVM UMD bundle (it does not end with "}));")');
  const names = scanNames(source);
  const script = new vm.Script(source.slice(0, end) + harnessBlock(names) + source.slice(end), {filename});
  const settings = {console: options.console, timeoutMs: options.timeoutMs || 60000};
  const attempts = profile === 'auto' ? ['minimal', 'browser'] : [profile];
  let firstError = null;
  for (const attempt of attempts) {
    try {
      const client = evaluateIn(attempt, script, names, settings);
      client.file = filename;
      client.fallbackError = firstError ? describe(firstError) : null;
      client.loadMs = hostPerformance.now() - started;
      return client;
    } catch (error) {
      if (error && error.code === 'ERR_SCRIPT_EXECUTION_TIMEOUT') throw error;
      if (firstError === null) firstError = error;
      if (attempt === attempts[attempts.length - 1]) {
        const detail = firstError === error ? describe(error) : 'minimal: ' + describe(firstError) + '\nbrowser: ' + describe(error);
        throw new Error('could not evaluate ' + filename + ' in node:vm (' + attempts.join(', then ') + ' profile)\n' + detail,
          {cause: error});
      }
    }
  }
  throw new Error('unreachable');
}

/** Reads and evaluates a classes.js file (read-only; nothing is written). See loadClientSource for options. */
function loadClient(classesPath, options = {}) {
  const started = hostPerformance.now();
  const file = path.resolve(String(classesPath));
  const client = loadClientSource(fs.readFileSync(file, 'utf8'), {filename: file, ...options});
  client.file = file;
  client.loadMs = hostPerformance.now() - started;    // including the read
  return client;
}

module.exports = {loadClient, loadClientSource};
