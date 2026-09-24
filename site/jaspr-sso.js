(function () {
  "use strict";
  var account = null, mode = "login", generation = 0;
  var diagnosticBudget = 28;
  var pageSessionId = "jaspercraft-host-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 10);
  var providers = { google: false, discord: false };
  var oauthPendingKey = "jaspercraft.oauth.pending.v1";
  var authDraftKey = "jaspercraft.auth.draft.v1";
  var oauthReturn = { provider: "", error: "", message: "" };
  var byId = function (id) { return document.getElementById(id); };
  var status = byId("status"), form = byId("auth-form");

  function diagnostic(event, details) {
    if (diagnosticBudget <= 0 || typeof window.fetch !== "function") return;
    diagnosticBudget--;
    try {
      window.fetch("/api/diagnostics/events", {
        method: "POST", credentials: "same-origin", cache: "no-store", keepalive: true,
        headers: { "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1" },
        body: JSON.stringify({ events: [{ event: event, pageSessionId: pageSessionId, at: new Date().toISOString(), details: details || {} }] })
      }).catch(function () {});
    } catch (_) {}
  }

  function providerLabel(provider) { return provider === "discord" ? "Discord" : "Google"; }
  function readStoredJson(key) {
    try {
      var value = localStorage.getItem(key);
      return value ? JSON.parse(value) : null;
    } catch (_) { return null; }
  }
  function writeStoredJson(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) {}
  }
  function removeStored(key) {
    try { localStorage.removeItem(key); } catch (_) {}
  }
  function readPendingOAuth() {
    var pending = readStoredJson(oauthPendingKey);
    if (!pending || !["google", "discord"].includes(pending.provider)
        || !Number.isFinite(Number(pending.startedAt)) || Date.now() - Number(pending.startedAt) > 35 * 60 * 1000) {
      removeStored(oauthPendingKey);
      return null;
    }
    return pending;
  }
  function setPendingOAuth(provider) {
    writeStoredJson(oauthPendingKey, { provider: provider, startedAt: Date.now(), returnTo: "/jaspercraft/" });
  }
  function clearPendingOAuth() { removeStored(oauthPendingKey); }
  function oauthErrorMessage(code, provider) {
    var label = provider ? providerLabel(provider) + " " : "";
    var messages = {
      signin_cancelled: label + "sign-in was cancelled. You can try again or choose another option.",
      signin_expired: label + "sign-in expired or returned in a different browser. Try the same button again here.",
      signin_rate_limited: "Too many sign-in attempts. Wait a few minutes, then try again.",
      signin_invalid: label + "returned an invalid sign-in response. Try again.",
      oauth_provider_unavailable: label + "is temporarily unavailable. Try again shortly.",
      oauth_provider_rejected: label + "rejected the sign-in. Try again or choose another option.",
      oauth_provider_response: label + "returned an invalid response. Try again.",
      oauth_profile_invalid: "That " + label + "profile cannot be used here.",
      account_banned: "This account is banned from Jaspr.chat.",
      device_blocked: "This device is temporarily blocked because it was linked to a banned identity.",
      identity_limit: "This device or connection has created too many identities.",
      session_limit: "Too many sessions are active on this device or connection."
    };
    return messages[code] || "External sign-in could not be completed. Try again or choose another option.";
  }
  function readOAuthReturn() {
    var current = new URL(location.href);
    var provider = current.searchParams.get("auth") || "";
    var error = current.searchParams.get("auth_error") || "";
    var pending = readPendingOAuth();
    if (!["google", "discord"].includes(provider)) provider = "";
    if ((provider || error) && window.history && typeof window.history.replaceState === "function") {
      current.searchParams.delete("auth");
      current.searchParams.delete("auth_error");
      window.history.replaceState(null, "", current.pathname + current.search + current.hash);
    }
    var pendingProvider = pending && pending.provider || "";
    if (provider || error) clearPendingOAuth();
    var result = { provider: provider || pendingProvider, error: error, message: "" };
    if (error) result.message = oauthErrorMessage(error, pendingProvider);
    if (provider) diagnostic("jaspercraft.host.oauth_returned", { provider: provider, success: true });
    else if (error) diagnostic("jaspercraft.host.oauth_returned", { provider: pendingProvider || null, success: false, code: error.slice(0, 80) });
    return result;
  }
  function saveAuthDraft() {
    writeStoredJson(authDraftKey, {
      mode: mode,
      username: byId("username").value || "",
      displayName: byId("display-name").value || ""
    });
  }
  function restoreAuthDraft() {
    var draft = readStoredJson(authDraftKey);
    if (!draft) return;
    byId("username").value = String(draft.username || "").slice(0, 64);
    byId("display-name").value = String(draft.displayName || "").slice(0, 32);
    authMode(draft.mode === "register" ? "register" : "login", true);
  }
  function clearAuthDraft() { removeStored(authDraftKey); }
  function ensureLobbyArt() {
    if (typeof window.matchMedia === "function" && window.matchMedia("(max-width: 760px)").matches) return;
    var banner = byId("lobby-banner");
    if (banner && banner.dataset.loaded !== "true") {
      banner.dataset.loaded = "true";
      banner.src = banner.dataset.src || "jaspercraft-banner.png?build=20260914-dlights10";
    }
  }

  function focusFrame(frame, reason) {
    if (!frame || !frame.isConnected) return;
    try { frame.focus({ preventScroll: true }); } catch (_) {}
    try { frame.contentWindow.focus(); } catch (_) {}
    if (reason) diagnostic("jaspercraft.host.frame_focus", { reason: reason });
  }

  function message(text, error) {
    status.textContent = text || "";
    status.dataset.error = error ? "true" : "false";
  }
  function busy(value) {
    ["auth-submit", "login-tab", "register-tab", "guest", "google-signin", "discord-signin", "alias-submit", "retry", "retry-game"].forEach(function (id) {
      byId(id).disabled = value;
    });
  }
  function authMode(value, restoring) {
    mode = value;
    var registering = value === "register";
    byId("display-label").hidden = byId("confirm-label").hidden = !registering;
    byId("confirm").required = registering;
    byId("password").autocomplete = registering ? "new-password" : "current-password";
    byId("login-tab").setAttribute("aria-selected", String(!registering));
    byId("register-tab").setAttribute("aria-selected", String(registering));
    byId("auth-submit").textContent = registering ? "Create persistent account" : "Sign in";
    byId("local-account-help").textContent = registering
      ? "Create a Jaspr.chat account with a username and password. No email or verification link is required."
      : "Use your Jaspr.chat username and password.";
    if (!restoring) saveAuthDraft();
    message("");
  }
  async function request(path, data, method) {
    var controller = new AbortController(), timer = setTimeout(function () { controller.abort(); }, 15000);
    try {
      var response = await fetch(path, {
        method: method || (data === undefined ? "GET" : "POST"), credentials: "same-origin", cache: "no-store", redirect: "error",
        headers: data === undefined ? { Accept: "application/json" } : {
          Accept: "application/json", "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1"
        },
        body: data === undefined ? undefined : JSON.stringify(data), signal: controller.signal
      });
      var payload;
      try { payload = await response.json(); } catch (_) { throw new Error("JasperCraft sign-in is unavailable. Please try again shortly."); }
      if (!response.ok) {
        var detail = payload && payload.error && payload.error.message;
        var error = new Error(response.status === 401 ? "Choose how you want to enter JasperCraft."
          : response.status === 429 ? "Too many attempts. Please wait a moment and try again."
          : detail || "We couldn't complete that request. Please try again shortly.");
        error.status = response.status;
        error.code = payload && payload.error && payload.error.code;
        throw error;
      }
      return payload;
    } finally { clearTimeout(timer); }
  }
  function removeGame() {
    generation++;
    var frame = byId("game-host").querySelector("iframe");
    if (frame) {
      diagnostic("jaspercraft.host.frame_removed", {});
      frame.remove();
    }
  }
  function showAuth() {
    removeGame();
    ensureLobbyArt();
    account = null;
    byId("alias-panel").hidden = true;
    byId("auth-panel").hidden = false;
    byId("lobby").hidden = false;
    byId("game-host").hidden = true;
    byId("game-status").hidden = true;
    document.body.classList.remove("playing", "checking");
    document.body.classList.add("authenticating");
    var pending = readPendingOAuth();
    if (oauthReturn.error) message(oauthReturn.message, true);
    else if (pending) message(providerLabel(pending.provider) + " sign-in was not finished. Continue with " + providerLabel(pending.provider) + " again, or choose another option below.", true);
    else message("Choose one Jaspr.chat identity. Google and Discord work for new and existing users.");
  }
  function showAlias() {
    removeGame();
    byId("auth-panel").hidden = true;
    byId("alias-panel").hidden = false;
    byId("lobby").hidden = false;
    byId("game-host").hidden = true;
    byId("game-status").hidden = true;
    document.body.classList.remove("playing", "checking");
    document.body.classList.add("authenticating");
    message("");
  }
  function updateProviders() {
    ["google", "discord"].forEach(function (provider) {
      var ready = providers[provider] === true;
      byId(provider + "-signin").classList.toggle("setup-needed", !ready);
      byId(provider + "-note").textContent = ready ? "Creates or signs in" : "Temporarily unavailable";
    });
  }
  async function loadOptions() {
    var started = Date.now();
    var info = await request("/api/jaspercraft/auth");
    providers = info.authProviders || providers;
    updateProviders();
    diagnostic("jaspercraft.host.auth_options_loaded", {
      google: providers.google === true,
      discord: providers.discord === true,
      durationMs: Date.now() - started
    });
  }
  async function loadAccount() {
    var info = await request("/api/jaspercraft/me");
    if (info.authenticated !== true || !info.user || !["account", "guest"].includes(info.user.kind)
        || typeof info.user.username !== "string" || !/^[A-Za-z0-9_]{3,16}$/.test(info.gameName)) {
      throw new Error("Jaspr returned an invalid character. Please try again.");
    }
    account = info;
    if (oauthReturn.provider) clearPendingOAuth();
    var chat = new URL(info.chatUrl || "/", location.origin);
    byId("back-chat").href = chat.origin === location.origin && !chat.username && !chat.password ? chat.href : location.origin + "/";
    return info;
  }
  async function launch(info) {
    removeGame();
    var attempt = generation;
    account = info || await loadAccount();
    if (account.profileSetupRequired) { showAlias(); return; }
    var name = account.gameName;
    var synced = await request("/api/jaspercraft/settings/sync", {
      gameName: name,
      initialValues: JasprProfile.initialSettings(name, localStorage)
    });
    JasprProfile.applySettings(name, localStorage, synced.values);
    diagnostic("jaspercraft.host.settings_restored", {
      initialized: Boolean(synced.initialized),
      revision: Number(synced.revision) || 0,
      count: Object.keys(synced.values || {}).length
    });
    await JasprProfile.prepare(name, localStorage);
    var profileValue = localStorage.getItem(JasprProfile.key);
    if (typeof profileValue === "string") {
      var profileChange = {};
      profileChange[JasprProfile.key] = profileValue;
      await request("/api/jaspercraft/settings", { gameName: name, changes: profileChange });
    }
    if (attempt !== generation) return;
    var connection = await request("/api/jaspercraft/connect", {});
    if (attempt !== generation) return;
    if (connection.gameName !== name) throw new Error("Your account changed. Reopening JasperCraft will reconnect the correct character.");
    diagnostic("jaspercraft.host.launch_authorized", { gameNameLength: name.length });
    var frame = document.createElement("iframe");
    var frameLoadStarted = Date.now();
    frame.title = "JasperCraft game";
    frame.tabIndex = 0;
    frame.allow = "fullscreen; autoplay; microphone";
    frame.setAttribute("allowfullscreen", "");
    var loaded = new Promise(function (resolve, reject) {
      var timeout = setTimeout(function () { reject(new Error("The game took too long to load. Please try again.")); }, 60000);
      frame.addEventListener("load", function () {
        clearTimeout(timeout);
        diagnostic("jaspercraft.host.frame_loaded", { durationMs: Date.now() - frameLoadStarted });
        focusFrame(frame, "loaded");
        resolve();
      }, { once: true });
      frame.addEventListener("error", function () { clearTimeout(timeout); reject(new Error("The game could not load.")); }, { once: true });
    });
  frame.src = "client.html?build=20260914-dlights10";
    byId("game-host").textContent = "";
    byId("game-host").appendChild(frame);
    byId("game-host").hidden = false;
    byId("lobby").hidden = true;
    document.body.classList.remove("authenticating", "checking");
    document.body.classList.add("playing");
    byId("game-status-text").textContent = "Loading your character…";
    byId("game-status").dataset.error = "false";
    byId("retry-game").hidden = true;
    byId("game-status").hidden = false;
    focusFrame(frame, "mounted");
    await loaded;
    if (attempt !== generation) return;
    frame.contentWindow.JasperCraftClient.start({ gameName: name, serverAddress: connection.serverAddress, join: true });
    focusFrame(frame, "client-started");
    setTimeout(function () { if (attempt === generation) focusFrame(frame, "client-settled"); }, 500);
    byId("game-status").hidden = true;
  }
  async function enterWorld() {
    var optionsReady = loadOptions().catch(function () { updateProviders(); });
    try {
      var info = await loadAccount();
      if (info.profileSetupRequired) showAlias();
      else await launch(info);
    } catch (error) {
      if (error.status === 401) { await optionsReady; showAuth(); return; }
      throw error;
    }
  }
  function showFailure(error) {
    diagnostic("jaspercraft.host.launch_failed", {
      name: String(error && error.name || "Error").slice(0, 60),
      code: String(error && error.code || "").slice(0, 80),
      status: Number(error && error.status) || 0
    });
    removeGame();
    var text = error.name === "AbortError" ? "Sign-in timed out. Please try again." : error.message || "Unable to start JasperCraft.";
    if (account) {
      byId("game-host").hidden = false;
      byId("game-status-text").textContent = text;
      byId("game-status").dataset.error = "true";
      byId("game-status").hidden = false;
      byId("retry-game").hidden = false;
    } else {
      showAuth();
      message(text, true);
      byId("retry").hidden = false;
    }
  }
  async function run(action) {
    busy(true);
    try { await action(); }
    catch (error) { showFailure(error); }
    finally { busy(false); }
  }
  function startExternalSignIn(provider) {
    if (!providers[provider]) {
      message((provider === "google" ? "Google" : "Discord") + " sign-in needs one-time owner setup.", true);
      return;
    }
    setPendingOAuth(provider);
    saveAuthDraft();
    diagnostic("jaspercraft.host.oauth_start_requested", { provider: provider, returnTo: "jaspercraft" });
    busy(true);
    location.assign("/api/auth/oauth/" + provider + "/start?return_to=" + encodeURIComponent("/jaspercraft/"));
  }

  oauthReturn = readOAuthReturn();
  restoreAuthDraft();
  ["username", "display-name"].forEach(function (id) {
    byId(id).addEventListener("input", saveAuthDraft);
  });
  byId("login-tab").addEventListener("click", function () { authMode("login"); });
  byId("register-tab").addEventListener("click", function () { authMode("register"); });
  byId("google-signin").addEventListener("click", function () { startExternalSignIn("google"); });
  byId("discord-signin").addEventListener("click", function () { startExternalSignIn("discord"); });
  byId("guest").addEventListener("click", function () {
    run(async function () { await request("/api/auth/guest", {}); clearPendingOAuth(); clearAuthDraft(); await launch(await loadAccount()); });
  });
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    run(async function () {
      var credentials = { username: byId("username").value.trim(), password: byId("password").value };
      var registering = mode === "register";
      if (registering && credentials.password !== byId("confirm").value) throw new Error("The passwords do not match.");
      try {
        var payload = Object.assign({}, credentials);
        if (registering && byId("display-name").value.trim()) payload.displayName = byId("display-name").value.trim();
        await request(registering ? "/api/auth/register" : "/api/auth/login", payload);
        clearPendingOAuth();
        clearAuthDraft();
        await launch(await loadAccount());
      } finally { byId("password").value = byId("confirm").value = ""; }
    });
  });
  byId("alias-panel").addEventListener("submit", function (event) {
    event.preventDefault();
    run(async function () {
      await request("/api/users/me/google-alias", { username: byId("alias-username").value.trim() }, "PATCH");
      clearPendingOAuth();
      clearAuthDraft();
      await launch(await loadAccount());
    });
  });
  byId("retry").addEventListener("click", function () { byId("retry").hidden = true; run(enterWorld); });
  byId("retry-game").addEventListener("click", function () { run(enterWorld); });
  byId("back-chat").addEventListener("click", function (event) {
    if (event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) removeGame();
  });
  window.addEventListener("pagehide", removeGame);
  window.addEventListener("pageshow", function (event) { if (event.persisted) run(enterWorld); });
  window.addEventListener("focus", function () { focusFrame(byId("game-host").querySelector("iframe"), "host-window-focus"); });
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden) focusFrame(byId("game-host").querySelector("iframe"), "host-visible");
  });

  if (location.protocol === "file:" || (location.hostname !== "jaspr.chat" && location.hostname !== "127.0.0.1" && location.hostname !== "localhost")) {
    showAuth();
    message("Open JasperCraft on Jaspr.chat so your game and chat identity stay connected.", true);
  } else { run(enterWorld); }
})();
