/* One-time profile preparation. Rendering and the compiled client remain untouched. */
(function (root) {
  "use strict";
  var namespace = "_eaglercraft_1122_tailscale_ui2";
  var key = namespace + ".p";
  var ownerKey = "jaspr.jaspercraft.settings.owner.v1";
  var settingKeyPattern = new RegExp("^" + namespace.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\.[A-Za-z0-9_.-]{1,64}$");
  var limit = 32 * 1024 * 1024;

  function assertName(name) {
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new Error("Invalid character name from Jaspr.");
  }
  function settingKeys(storage) {
    var keys = [];
    for (var i = 0; i < Number(storage.length || 0); i++) {
      var candidate = storage.key(i);
      if (typeof candidate === "string" && settingKeyPattern.test(candidate)) keys.push(candidate);
    }
    return keys;
  }
  function settings(storage) {
    var values = {};
    settingKeys(storage).forEach(function (settingKey) {
      var value = storage.getItem(settingKey);
      if (typeof value === "string") values[settingKey] = value;
    });
    return values;
  }
  function initialSettings(name, storage) {
    assertName(name);
    var previousOwner = storage.getItem(ownerKey);
    // A browser cache owned by another account must never seed a new account.
    // A missing marker is the one-time migration path for pre-sync players.
    return previousOwner && previousOwner !== name ? {} : settings(storage);
  }
  function applySettings(name, storage, values) {
    assertName(name);
    if (!values || typeof values !== "object" || Array.isArray(values)) throw new Error("Jaspr returned invalid game settings.");
    var validated = {};
    Object.keys(values).forEach(function (settingKey) {
      if (!settingKeyPattern.test(settingKey) || typeof values[settingKey] !== "string")
        throw new Error("Jaspr returned invalid game settings.");
      validated[settingKey] = values[settingKey];
    });
    settingKeys(storage).forEach(function (settingKey) { storage.removeItem(settingKey); });
    Object.keys(validated).forEach(function (settingKey) { storage.setItem(settingKey, validated[settingKey]); });
    storage.setItem(ownerKey, name);
  }

  function concat(parts) {
    var out = new Uint8Array(parts.reduce(function (sum, part) { return sum + part.length; }, 0));
    var offset = 0;
    parts.forEach(function (part) { out.set(part, offset); offset += part.length; });
    return out;
  }
  function text(value) {
    var bytes = new TextEncoder().encode(value);
    return concat([new Uint8Array([bytes.length >>> 8, bytes.length & 255]), bytes]);
  }
  function intTag(name, value) {
    var bytes = new Uint8Array(4); new DataView(bytes.buffer).setInt32(0, value);
    return concat([new Uint8Array([3]), text(name), bytes]);
  }
  function newProfile() {
    return concat([new Uint8Array([10, 0, 0]), intTag("presetSkin", 0), intTag("customSkin", -1),
      intTag("presetCape", 0), intTag("customCape", -1), new Uint8Array([0])]);
  }

  // Walk NBT boundaries, splice only the top-level username payload, retain all other bytes.
  function withUsername(input, name) {
    assertName(name);
    var bytes = new Uint8Array(input), view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    if (bytes.length > limit) throw new Error("Profile is too large.");
    var pos = 0, found = null;
    function take(n) {
      if (!Number.isSafeInteger(n) || n < 0 || pos + n > bytes.length) throw new Error("Saved profile is incomplete.");
      var start = pos; pos += n; return start;
    }
    function u8() { return bytes[take(1)]; }
    function i32() { return view.getInt32(take(4)); }
    function string() {
      var n = view.getUint16(take(2)); return new TextDecoder().decode(bytes.subarray(take(n), pos));
    }
    function payload(type, depth) {
      if (depth > 64) throw new Error("Saved profile is too deeply nested.");
      var n, child;
      switch (type) {
        case 1: take(1); break;
        case 2: take(2); break;
        case 3: case 5: take(4); break;
        case 4: case 6: take(8); break;
        case 7: take(i32()); break;
        case 8: string(); break;
        case 9:
          child = u8(); n = i32();
          if (n < 0 || n > 1000000 || (child === 0 && n !== 0)) throw new Error("Invalid profile list.");
          for (var i = 0; i < n; i++) payload(child, depth + 1);
          break;
        case 10:
          while ((child = u8()) !== 0) {
            var field = string(), start = pos;
            payload(child, depth + 1);
            if (depth === 0 && field === "username") {
              if (found || child !== 8) throw new Error("Invalid profile username field.");
              found = [start, pos];
            }
          }
          break;
        case 11: n = i32(); if (n < 0) throw new Error("Invalid profile array."); take(n * 4); break;
        case 12: n = i32(); if (n < 0) throw new Error("Invalid profile array."); take(n * 8); break;
        default: throw new Error("Unsupported profile field.");
      }
    }
    if (u8() !== 10) throw new Error("Saved profile is not a compound.");
    string(); payload(10, 0);
    if (pos !== bytes.length) throw new Error("Unexpected profile data.");
    if (found) return concat([bytes.subarray(0, found[0]), text(name), bytes.subarray(found[1])]);
    return concat([bytes.subarray(0, bytes.length - 1), new Uint8Array([8]), text("username"), text(name), new Uint8Array([0])]);
  }

  async function transform(bytes, compress) {
    var stream = new Blob([bytes]).stream().pipeThrough(compress ? new CompressionStream("gzip") : new DecompressionStream("gzip"));
    var reader = stream.getReader(), parts = [], size = 0;
    while (true) {
      var part = await reader.read(); if (part.done) break;
      size += part.value.length;
      if (size > limit) { await reader.cancel(); throw new Error("Profile is too large."); }
      parts.push(part.value);
    }
    return concat(parts);
  }
  async function prepare(name, storage) {
    if (typeof CompressionStream === "undefined" || typeof DecompressionStream === "undefined")
      throw new Error("Please use a current browser to keep your saved character settings.");
    var previous = storage.getItem(key), bytes = newProfile();
    if (previous) {
      if (previous.length > limit) throw new Error("Profile is too large.");
      bytes = await transform(Uint8Array.from(atob(previous), function (c) { return c.charCodeAt(0); }), false);
    }
    var updated = await transform(withUsername(bytes, name), true), chunks = [];
    for (var offset = 0; offset < updated.length; offset += 8192)
      chunks.push(String.fromCharCode.apply(null, updated.subarray(offset, offset + 8192)));
    // Atomic storage replacement only after successful decode/validation/encode. No clearing of any keys.
    storage.setItem(key, btoa(chunks.join("")));
  }
  var api = {
    namespace: namespace,
    key: key,
    ownerKey: ownerKey,
    settings: settings,
    initialSettings: initialSettings,
    applySettings: applySettings,
    withUsername: withUsername,
    newProfile: newProfile,
    prepare: prepare
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.JasprProfile = api;
})(typeof window !== "undefined" ? window : globalThis);
