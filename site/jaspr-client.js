(function () {
  "use strict";
  var started = false;
  var diagnosticBudget = 24;
  var pageSessionId = "jaspercraft-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 10);
  var storageNamespace = "_eaglercraft_1122_tailscale_ui2";
  var settingKeyPattern = /^_eaglercraft_1122_tailscale_ui2\.[A-Za-z0-9_.-]{1,64}$/;
  var pendingSettings = Object.create(null);
  var settingsTimer = null;
  var settingsSending = false;
  var settingsRetryMs = 500;
  var activeGameName = "";
  var observedSettings = Object.create(null);
  var settingsStorageObserved = false;

  function fullSettingKey(value) {
    var candidate = String(value || "");
    if (settingKeyPattern.test(candidate)) return candidate;
    if (/^[A-Za-z0-9_.-]{1,64}$/.test(candidate)) candidate = storageNamespace + "." + candidate;
    return settingKeyPattern.test(candidate) ? candidate : null;
  }

  function loadSetting(value) {
    var key = fullSettingKey(value);
    if (!key || !window.localStorage) return null;
    try { return window.localStorage.getItem(key); } catch (_) { return null; }
  }

  function currentSettings() {
    var result = Object.create(null);
    if (!window.localStorage) return result;
    try {
      for (var i = 0; i < window.localStorage.length; i++) {
        var key = fullSettingKey(window.localStorage.key(i));
        if (!key) continue;
        var value = window.localStorage.getItem(key);
        if (value !== null) result[key] = String(value);
      }
    } catch (_) {}
    return result;
  }

  function scanSettings() {
    var latest = currentSettings();
    Object.keys(latest).forEach(function (key) {
      if (!Object.prototype.hasOwnProperty.call(observedSettings, key) || observedSettings[key] !== latest[key]) {
        queueSetting(key, latest[key]);
      }
    });
    Object.keys(observedSettings).forEach(function (key) {
      if (!Object.prototype.hasOwnProperty.call(latest, key)) queueSetting(key, null);
    });
    observedSettings = latest;
  }

  function sameLocalStorage(value) {
    if (value === window.localStorage) return true;
    try { return value === window.localStorage; } catch (_) { return false; }
  }

  function storageMethodOwner(storage, name) {
    var target = storage;
    while (target && !Object.prototype.hasOwnProperty.call(target, name)) target = Object.getPrototypeOf(target);
    return target;
  }

  function installSettingsStorageObserver() {
    if (settingsStorageObserved || !window.localStorage) return;
    observedSettings = currentSettings();
    var storage = window.localStorage;
    var methods = {
      setItem: function (original, receiver, args) {
        var result = original.apply(receiver, args);
        if (sameLocalStorage(receiver)) {
          var key = fullSettingKey(args[0]);
          if (key) queueSetting(key, String(args[1]));
        }
        return result;
      },
      removeItem: function (original, receiver, args) {
        var result = original.apply(receiver, args);
        if (sameLocalStorage(receiver)) {
          var key = fullSettingKey(args[0]);
          if (key) queueSetting(key, null);
        }
        return result;
      },
      clear: function (original, receiver, args) {
        var result = original.apply(receiver, args);
        if (sameLocalStorage(receiver)) scanSettings();
        return result;
      }
    };
    try {
      Object.keys(methods).forEach(function (name) {
        var owner = storageMethodOwner(storage, name);
        if (!owner || typeof owner[name] !== "function") return;
        var original = owner[name];
        if (original.__jasprSettingsObserver) return;
        var wrapped = function () { return methods[name](original, this, arguments); };
        wrapped.__jasprSettingsObserver = true;
        Object.defineProperty(owner, name, {
          configurable: true,
          enumerable: Object.prototype.propertyIsEnumerable.call(owner, name),
          writable: true,
          value: wrapped
        });
      });
      settingsStorageObserved = true;
      diagnostic("jaspercraft.settings.storage_observer", { installed: true, count: Object.keys(observedSettings).length });
    } catch (error) {
      diagnostic("jaspercraft.settings.storage_observer", {
        installed: false,
        name: String(error && error.name || "Error").slice(0, 60)
      });
    }
  }

  function scheduleSettings(delay) {
    if (settingsTimer !== null || !activeGameName || typeof window.fetch !== "function") return;
    settingsTimer = setTimeout(function () {
      settingsTimer = null;
      flushSettings(false);
    }, delay);
  }

  function queueSetting(value, settingValue) {
    var key = fullSettingKey(value);
    if (!key) return;
    var normalized = settingValue === null || settingValue === undefined ? null : String(settingValue);
    pendingSettings[key] = normalized;
    if (normalized === null) delete observedSettings[key];
    else observedSettings[key] = normalized;
    scheduleSettings(350);
  }

  function flushSettings(keepalive) {
    if (settingsSending || !activeGameName || typeof window.fetch !== "function") return;
    if (settingsTimer !== null) {
      clearTimeout(settingsTimer);
      settingsTimer = null;
    }
    var keys = Object.keys(pendingSettings);
    if (!keys.length) return;
    var changes = {};
    keys.forEach(function (key) {
      changes[key] = pendingSettings[key];
      delete pendingSettings[key];
    });
    settingsSending = true;
    var operation;
    try {
      operation = window.fetch("/api/jaspercraft/settings", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        keepalive: Boolean(keepalive),
        headers: { "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1" },
        body: JSON.stringify({ gameName: activeGameName, changes: changes })
      });
    } catch (error) {
      operation = Promise.reject(error);
    }
    Promise.resolve(operation).then(function (response) {
      if (!response.ok) throw new Error("Settings update failed with status " + response.status + ".");
      return response.json();
    }).then(function (result) {
      settingsRetryMs = 500;
      diagnostic("jaspercraft.settings.saved", {
        count: keys.length,
        revision: Number(result && result.revision) || 0
      });
    }).catch(function (error) {
      keys.forEach(function (key) {
        if (!Object.prototype.hasOwnProperty.call(pendingSettings, key)) pendingSettings[key] = changes[key];
      });
      settingsRetryMs = Math.min(settingsRetryMs * 2, 8000);
      diagnostic("jaspercraft.settings.save_failed", {
        count: keys.length,
        name: String(error && error.name || "Error").slice(0, 60)
      });
    }).finally(function () {
      settingsSending = false;
      if (Object.keys(pendingSettings).length) scheduleSettings(settingsRetryMs);
    });
  }

  function diagnostic(event, details) {
    if (diagnosticBudget <= 0 || typeof window.fetch !== "function") return;
    diagnosticBudget--;
    try {
      window.fetch("/api/diagnostics/events", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        keepalive: true,
        headers: { "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1" },
        body: JSON.stringify({ events: [{ event: event, pageSessionId: pageSessionId, at: new Date().toISOString(), details: details || {} }] })
      }).catch(function () {});
    } catch (_) {}
  }

  function focusGameSurface(reason) {
    if (typeof document === "undefined") return;
    try { window.focus(); } catch (_) {}
    var target = document.querySelector("canvas") || document.body;
    if (target) {
      try {
        if (!target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1");
        target.focus({ preventScroll: true });
      } catch (_) {}
    }
    if (reason) diagnostic("jaspercraft.input.focus_requested", {
      reason: reason,
      documentFocused: typeof document.hasFocus === "function" ? document.hasFocus() : null,
      canvasPresent: Boolean(document.querySelector("canvas"))
    });
  }

  function isInteractiveTarget(event) {
    var target = event && event.target;
    if (!target || typeof target.closest !== "function") return false;
    return Boolean(target.closest("button, a, input, select, textarea, [role='button'], [contenteditable='true']"));
  }

  function installFocusRecovery() {
    if (typeof document === "undefined") return;
    var movementLogged = false, pointerLogs = 0;
    ["pointerdown", "mousedown", "touchstart"].forEach(function (name) {
      window.addEventListener(name, function (event) {
        if (!isInteractiveTarget(event)) focusGameSurface(name);
      }, { capture: true, passive: true });
    });
    window.addEventListener("focus", function () { focusGameSurface("window-focus"); });
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) focusGameSurface("visible");
    });
    window.addEventListener("keydown", function (event) {
      if (!movementLogged && /^(?:Key[WASD]|Space)$/.test(String(event.code || ""))) {
        movementLogged = true;
        diagnostic("jaspercraft.input.movement_key", {
          documentFocused: typeof document.hasFocus === "function" ? document.hasFocus() : null,
          pointerLocked: Boolean(document.pointerLockElement)
        });
      }
    }, { capture: true });
    document.addEventListener("pointerlockchange", function () {
      if (pointerLogs++ < 4) diagnostic("jaspercraft.input.pointer_lock", {
        active: Boolean(document.pointerLockElement),
        documentFocused: typeof document.hasFocus === "function" ? document.hasFocus() : null
      });
      if (document.pointerLockElement) focusGameSurface("");
    });
    focusGameSurface("client-start");
  }

  function installNonBlockingAudio() {
    var NativeAudioContext = window.AudioContext || window.webkitAudioContext;
    if (!NativeAudioContext || NativeAudioContext.__jasprNonBlocking) return;
    var contexts = [];
    function resumeAll() {
      contexts = contexts.filter(function (context) {
        if (!context || context.state === "closed") return false;
        if (context.state === "suspended") {
          try { context.resume().catch(function () {}); } catch (_) {}
        }
        return true;
      });
    }
    var WrappedAudioContext = new Proxy(NativeAudioContext, {
      construct: function (target, args) {
        var context = Reflect.construct(target, args, target);
        contexts.push(context);
        try { context.resume().catch(function () {}); } catch (_) {}
        return context;
      }
    });
    WrappedAudioContext.__jasprNonBlocking = true;
    if (window.AudioContext) window.AudioContext = WrappedAudioContext;
    if (window.webkitAudioContext) window.webkitAudioContext = WrappedAudioContext;
    ["pointerdown", "mousedown", "touchstart", "keydown"].forEach(function (name) {
      window.addEventListener(name, resumeAll, { capture: true, passive: name !== "keydown" });
    });
    // If browser policy suspends sound, the first ordinary game input resumes it.
  }

  function installSocketDiagnostics(stableAddress) {
    var NativeWebSocket = window.WebSocket;
    if (!NativeWebSocket || NativeWebSocket.__jasprObserved) return;
    var socketSequence = 0;
    var ObservedWebSocket = new Proxy(NativeWebSocket, {
      construct: function (target, args) {
        var socket = Reflect.construct(target, args);
        var requested;
        try { requested = new URL(String(args[0]), location.href); } catch (_) { return socket; }
        if (requested.href !== stableAddress || typeof socket.addEventListener !== "function") return socket;
        var socketId = ++socketSequence, openedAt = Date.now(), opened = false;
        diagnostic("jaspercraft.socket.constructed", { socketId: socketId });
        socket.addEventListener("open", function () {
          opened = true;
          diagnostic("jaspercraft.socket.opened", { socketId: socketId, durationMs: Date.now() - openedAt });
        }, { once: true });
        socket.addEventListener("error", function () {
          diagnostic("jaspercraft.socket.error", { socketId: socketId, opened: opened });
        }, { once: true });
        socket.addEventListener("close", function (event) {
          diagnostic("jaspercraft.socket.closed", {
            socketId: socketId,
            opened: opened,
            code: Number(event.code) || 0,
            clean: Boolean(event.wasClean),
            durationMs: Date.now() - openedAt
          });
        }, { once: true });
        return socket;
      }
    });
    ObservedWebSocket.__jasprObserved = true;
    window.WebSocket = ObservedWebSocket;
  }

  // Video settings and auto-detection are owned by the native video adapter.
  window.JasperCraftClient = {
    start: function (session) {
      if (started || parent === window || !/^[A-Za-z0-9_]{3,16}$/.test(session.gameName))
        throw new Error("Open JasperCraft through its account page.");
      var socket = new URL(session.serverAddress, location.href);
      var secure = location.protocol === "https:";
      if (socket.protocol !== (secure ? "wss:" : "ws:") || socket.host !== location.host
          || socket.pathname !== "/jaspercraft/socket" || socket.search || socket.username || socket.password || socket.hash)
        throw new Error("Unexpected game server address.");
      var stableAddress = socket.href;
      activeGameName = session.gameName;
      // The only reader is the single-player testing grounds entry, which is the
      // operator account's own local world. Nothing is sent anywhere.
      window.JasprAccountName = session.gameName;
      installSettingsStorageObserver();
      installNonBlockingAudio();
      installFocusRecovery();
      installSocketDiagnostics(stableAddress);
      var relayId = Math.floor(Math.random() * 3);
      window.eaglercraftXOpts = {
        demoMode: false,
        container: "game_frame",
        assetsURI: "assets.epk?build=20260926-sme1",
        localesURI: "lang/",
        worldsDB: "eaglercraft_1122_tailscale_worlds",
        resourcePacksDB: "eaglercraft_1122_tailscale_resourcepacks",
        localStorageNamespace: storageNamespace,
        localStorageLoaded: loadSetting,
        localStorageSaved: queueSetting,
        logInvalidCerts: false,
        crashOnUncaughtExceptions: true,
        servers: [{ addr: stableAddress, name: "JasperCraft on Jaspr.chat", hideAddr: true }],
        relays: [
          { addr: "wss://relay.deev.is/", comment: "lax1dude relay #1", primary: relayId === 0 },
          { addr: "wss://relay.lax1dude.net/", comment: "lax1dude relay #2", primary: relayId === 1 },
          { addr: "wss://relay.shhnowisnottheti.me/", comment: "ayunami relay #1", primary: relayId === 2 }
        ]
      };
      if (session.join) window.eaglercraftXOpts.joinServer = stableAddress;
      started = true;
      diagnostic("jaspercraft.engine.starting", { autoJoin: Boolean(session.join), gameNameLength: session.gameName.length });
      main();
      setTimeout(function () { focusGameSurface("engine-started"); }, 0);
      setTimeout(function () { focusGameSurface("engine-settled"); }, 500);
    }
  };
  if (typeof window.addEventListener === "function") {
    window.addEventListener("online", function () { scanSettings(); flushSettings(false); });
    window.addEventListener("pagehide", function () { scanSettings(); flushSettings(true); });
  }
  if (typeof document !== "undefined") {
    document.addEventListener("visibilitychange", function () {
      if (document.hidden) { scanSettings(); flushSettings(true); }
    });
  }
})();
