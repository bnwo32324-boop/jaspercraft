/* Shader-pack browser glue. Modes: 0 OFF (default, native path untouched),
 * 1 MakeUp UltraFast, 2 Chocapic13 Toaster, 3 Miniature, 4 Sildur's Vibrant
 * Lite. Selection persists in
 * localStorage under jaspr.shader.pack and is exposed in Video Settings.
 *
 * Post pipeline (packs 1-3): after the world pass renders natively (untouched
 * target, MSAA and present logic intact), the drawn pixels are copied to a
 * texture and graded back over the same buffer with the pack's final color
 * science (exposure, Unreal/soft tonemap, saturation, contrast, tint,
 * vignette, 8-bit dither, miniature focus band). GUI/HUD draw after, exactly
 * like OptiFine final passes. Anaglyph multi-pass falls back to direct.
 * Nothing is ever rebound persistently, so a failure can only skip grading,
 * never black the screen (fail closed).
 * Lightmap core (GyZ wrap): after vanilla builds the 16x16 texture, texels
 * are raised toward per-pack torch curves (never darkened). Fog color (GmS
 * wrap) is multiplied per pack. Both are idempotent across fiber resumes.
 */
// Set only while the native GuiVideoSettings instance is displaying the
// shader-pack picker. Keeping this as a client flag lets the picker reuse the
// normal Minecraft screen, list scrolling, button rendering, and Done flow.
var JasprShadersPickerMode = 0;
// Fast-path flag shared by the native wrappers. OFF must not even enter the
// shader object on a render, lightmap, or fog call.
var JasprShadersEnabled = false;
var JasprShaders = (function () {
  "use strict";
  var MODE = 0, FAILED = false, FAILS = 0, LAST_ERROR = "", SKIP_LOGGED = "";
  var REV = 15;
  var program = null, vbo = null, copyTex = null, copyFbo = null, copyW = 0, copyH = 0;
  var locCache = null, lastButton = null;
  var vaoMode = 0, vaoFake = null;

  function logInfo(msg) {
    try { if (typeof console !== "undefined" && console.log) console.log("[JasprShaders] " + msg); }
    catch (e) { /* headless */ }
  }

  function logWarn(msg) {
    try { if (typeof console !== "undefined" && console.warn) console.warn("[JasprShaders] " + msg); }
    catch (e) { /* headless */ }
  }

  try {
    var store = ($rt_globals && $rt_globals.localStorage) ? $rt_globals.localStorage : null;
    if (store) {
      var raw = store.getItem("jaspr.shader.pack");
      if (raw === "1" || raw === "2" || raw === "3" || raw === "4") MODE = parseInt(raw, 10);
    }
  } catch (e) { MODE = 0; }
  JasprShadersEnabled = MODE !== SHADER_PACK_OFF;

  function persist() {
    try {
      if ($rt_globals && $rt_globals.localStorage)
        $rt_globals.localStorage.setItem("jaspr.shader.pack", String(MODE));
    } catch (e) { /* in-memory mode still applies */ }
  }

  // Retrievable without devtools chops: mirrors the button/console state so a
  // future diagnostic read (or a pasted Application-tab value) shows it.
  function persistStatus() {
    try {
      if ($rt_globals && $rt_globals.localStorage)
        $rt_globals.localStorage.setItem("jaspr.shader.status", JSON.stringify({
          mode: MODE, failed: FAILED, fails: FAILS, lastError: LAST_ERROR,
          target: copyTex ? (copyW + "x" + copyH) : null
        }));
    } catch (e) { /* diagnostics only */ }
  }

  function gl() {
    if (typeof HEf !== "undefined" && HEf) return HEf;
    return null;
  }

  // Size of the buffer the world is currently drawing into: the canvas, or
  // the engine's own present target when one is bound.
  function targetSize(g) {
    try {
      var bound = null;
      try { bound = g.getParameter(g.FRAMEBUFFER_BINDING); } catch (e) { bound = null; }
      if (!bound) {
        if (typeof HHY !== "undefined" && HHY && HHY.width > 0 && HHY.height > 0)
          return { w: HHY.width | 0, h: HHY.height | 0 };
        return null;
      }
      if (typeof HHJ !== "undefined" && HHJ > 0 && typeof HHK !== "undefined" && HHK > 0)
        return { w: HHJ | 0, h: HHK | 0 };
      if (typeof HHY !== "undefined" && HHY && HHY.width > 0 && HHY.height > 0)
        return { w: HHY.width | 0, h: HHY.height | 0 };
      return null;
    } catch (e) { return null; }
  }

  // Keep the GLSL source out of startup work. The client still contains the
  // implementation, but no shader source is assembled until a pack is used.
  var VERT_SRC = null, FRAG_SRC = null;
  function shaderSources() {
    if (VERT_SRC && FRAG_SRC) return;
    VERT_SRC = [
      "attribute vec2 p;varying vec2 uv;",
      "void main(){uv=p*0.5+0.5;gl_Position=vec4(p,0.0,1.0);}"
    ].join("\n");
    FRAG_SRC = [
      "precision mediump float;",
      "uniform sampler2D uTex;",
      "uniform vec2 uTexel;",
      "uniform float uExpo;",
      "uniform float uSat;",
      "uniform float uCon;",
      "uniform float uGamma;",
      "uniform float uVig;",
      "uniform float uTilt;",
      "uniform float uFocusY;",
      "uniform float uFocusH;",
      "uniform float uBlur;",
      "uniform float uDither;",
      "uniform float uTonemap;",
      "uniform vec3 uTint;",
      "varying vec2 uv;",
      "float unreal(float x){return x/(0.98135426889*x+0.154*0.98135426889);}",
      "float soft(float x){float a=x*(2.2*x+0.15);return a/(x*(2.2*x+0.9)+0.15);}",
      "float sildur(float x){float A=0.28;float B=0.29;float C=0.10;float D=0.2;float E=0.025;float F=0.35;return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;}",
      "float aces(float x){float a=x*(2.51*x+0.03);float b=x*(2.43*x+0.59)+0.14;return clamp(a/b,0.0,1.0);}",
      "float bslc(float x){float c=max(x,0.0);c=c*(1.0+c*0.25)/(1.0+c);return pow(max(c,0.0),1.0/1.3);}",
      "vec3 grade(vec3 c){",
      " c*=uExpo;",
      " if(uTonemap>5.5){c=vec3(bslc(c.r),bslc(c.g),bslc(c.b));}",
      " else if(uTonemap>4.5){c=vec3(aces(c.r),aces(c.g),aces(c.b));}",
      " else if(uTonemap>3.5){float w=sildur(15.2);float invg=1.0/max(0.1,uGamma);c=pow(max(vec3(sildur(c.r*4.7),sildur(c.g*4.7),sildur(c.b*4.7)),vec3(0.0))/w,vec3(invg));}",
      " else if(uTonemap>1.5){c=vec3(soft(c.r),soft(c.g),soft(c.b));}",
      " else if(uTonemap>0.5){c=vec3(unreal(c.r),unreal(c.g),unreal(c.b));}",
      " float l=dot(c,vec3(0.2126,0.7152,0.0722));",
      " c=mix(vec3(l),c,uSat);c=(c-0.5)*uCon+0.5;c*=uTint;return c;}",
      "void main(){",
      " vec3 c;",
      " if(uTilt>0.5){",
      "  float m=clamp((abs(uv.y-uFocusY)-uFocusH*0.5)/max(0.05,uFocusH),0.0,1.0);",
      "  vec2 px=uTexel*uBlur*m;",
      "  vec3 acc=texture2D(uTex,uv).rgb*4.0;",
      "  acc+=texture2D(uTex,uv+vec2(px.x,0.0)).rgb*2.0;",
      "  acc+=texture2D(uTex,uv-vec2(px.x,0.0)).rgb*2.0;",
      "  acc+=texture2D(uTex,uv+vec2(0.0,px.y)).rgb*2.0;",
      "  acc+=texture2D(uTex,uv-vec2(0.0,px.y)).rgb*2.0;",
      "  c=grade(acc/12.0);",
      " }else{c=grade(texture2D(uTex,uv).rgb);}",
      " vec2 d=uv-0.5;c*=1.0-uVig*dot(d,d)*2.0;",
      " if(uDither>0.5){",
      "  float n=fract(52.9829189*fract(0.06711056*gl_FragCoord.x+0.00583715*gl_FragCoord.y));",
      "  c+=n*exp2(-8.0);}",
      " gl_FragColor=vec4(c,1.0);}"
    ].join("\n");
  }

  function compileProgram(g) {
    shaderSources();
    function shader(type, src) {
      var s = g.createShader(type);
      g.shaderSource(s, src);
      g.compileShader(s);
      if (!g.getShaderParameter(s, g.COMPILE_STATUS)) {
        try { logWarn("shader compile failed: " + g.getShaderInfoLog(s)); } catch (e) { /* ignore */ }
        return null;
      }
      return s;
    }
    var vs = shader(g.VERTEX_SHADER, VERT_SRC);
    var fs = shader(g.FRAGMENT_SHADER, FRAG_SRC);
    if (!vs || !fs) {
      try { if (vs && g.deleteShader) g.deleteShader(vs); } catch (e0) { /* ignore */ }
      try { if (fs && g.deleteShader) g.deleteShader(fs); } catch (e1) { /* ignore */ }
      return null;
    }
    var p = g.createProgram();
    g.attachShader(p, vs);
    g.attachShader(p, fs);
    g.bindAttribLocation(p, 0, "p");
    g.linkProgram(p);
    if (!g.getProgramParameter(p, g.LINK_STATUS)) {
      try { logWarn("program link failed: " + g.getProgramInfoLog(p)); } catch (e) { /* ignore */ }
      try { if (g.deleteProgram) g.deleteProgram(p); } catch (e2) { /* ignore */ }
      try { if (g.deleteShader) g.deleteShader(vs); } catch (e3) { /* ignore */ }
      try { if (g.deleteShader) g.deleteShader(fs); } catch (e4) { /* ignore */ }
      return null;
    }
    try { if (g.deleteShader) g.deleteShader(vs); } catch (e5) { /* ignore */ }
    try { if (g.deleteShader) g.deleteShader(fs); } catch (e6) { /* ignore */ }
    return p;
  }

  // Vertex-array containment: grade draws configure attrib state inside our
  // own VAO (bound through Fwv so the VAO cache stays coherent), leaving
  // engine VAOs and default-state layouts the engine assumes untouched.
  function ensureVAO(g) {
    if (vaoMode < 0) return false;
    if (vaoFake) return true;
    try {
      var raw = null;
      if (g.createVertexArray) {
        raw = g.createVertexArray();
        if (!raw) { vaoMode = -1; return false; }
        vaoMode = 2;
      } else {
        var ext = null;
        try { ext = g.getExtension("OES_vertex_array_object"); } catch (e) { ext = null; }
        if (!ext || !ext.createVertexArrayOES) { vaoMode = -1; return false; }
        raw = ext.createVertexArrayOES();
        if (!raw) { vaoMode = -1; return false; }
        vaoMode = 1;
      }
      vaoFake = { cE1: raw };
      return true;
    } catch (e) { vaoMode = -1; vaoFake = null; return false; }
  }

  function deleteVAO() {
    try {
      var g = gl();
      if (g && vaoFake && vaoFake.cE1) {
        if (vaoMode === 2) { try { g.deleteVertexArray(vaoFake.cE1); } catch (e) { /* ignore */ } }
        else if (vaoMode === 1) {
          try {
            var ext = g.getExtension("OES_vertex_array_object");
            if (ext) ext.deleteVertexArrayOES(vaoFake.cE1);
          } catch (e2) { /* ignore */ }
        }
      }
    } catch (e) { /* already gone */ }
    vaoFake = null;
  }

  function ensureTarget(g, size) {
    if (copyTex && copyFbo && copyW === size.w && copyH === size.h) return true;
    destroyTarget();
    var savedRead = null, savedDraw = null, haveBindings = false;
    try {
      try { savedRead = g.getParameter(g.READ_FRAMEBUFFER_BINDING); } catch (e0) {
        try { savedRead = g.getParameter(g.FRAMEBUFFER_BINDING); } catch (e1) { savedRead = null; }
      }
      try { savedDraw = g.getParameter(g.DRAW_FRAMEBUFFER_BINDING); } catch (e2) {
        try { savedDraw = g.getParameter(g.FRAMEBUFFER_BINDING); } catch (e3) { savedDraw = null; }
      }
      haveBindings = true;
    } catch (e) { haveBindings = false; }
    var s0 = null;
    try { s0 = saveState(g); } catch (e0) { s0 = null; }
    try {
      if (!program) {
        program = compileProgram(g);
        if (!program) { FAILED = true; logWarn("grade program unavailable; packs disabled"); return false; }
        locCache = {};
        var names = ["uTex", "uTexel", "uExpo", "uSat", "uCon", "uGamma", "uVig", "uTilt",
          "uFocusY", "uFocusH", "uBlur", "uDither", "uTonemap", "uTint"];
        for (var i = 0; i < names.length; i++) locCache[names[i]] = g.getUniformLocation(program, names[i]);
        vbo = g.createBuffer();
        g.bindBuffer(g.ARRAY_BUFFER, vbo);
        g.bufferData(g.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), g.STATIC_DRAW);
        g.bindBuffer(g.ARRAY_BUFFER, null);
        logInfo("grade program ready");
      }
      copyTex = g.createTexture();
      g.bindTexture(g.TEXTURE_2D, copyTex);
      g.texParameteri(g.TEXTURE_2D, g.TEXTURE_MIN_FILTER, g.LINEAR);
      g.texParameteri(g.TEXTURE_2D, g.TEXTURE_MAG_FILTER, g.LINEAR);
      g.texParameteri(g.TEXTURE_2D, g.TEXTURE_WRAP_S, g.CLAMP_TO_EDGE);
      g.texParameteri(g.TEXTURE_2D, g.TEXTURE_WRAP_T, g.CLAMP_TO_EDGE);
      g.texImage2D(g.TEXTURE_2D, 0, g.RGBA, size.w, size.h, 0, g.RGBA, g.UNSIGNED_BYTE, null);
      g.bindTexture(g.TEXTURE_2D, null);
      copyFbo = g.createFramebuffer();
      g.bindFramebuffer(g.FRAMEBUFFER, copyFbo);
      g.framebufferTexture2D(g.FRAMEBUFFER, g.COLOR_ATTACHMENT0, g.TEXTURE_2D, copyTex, 0);
      var fbOk = true;
      try { fbOk = g.checkFramebufferStatus(g.FRAMEBUFFER) === g.FRAMEBUFFER_COMPLETE; }
      catch (e) { fbOk = true; }
      copyW = size.w; copyH = size.h;
      logInfo("grade target " + size.w + "x" + size.h + (fbOk ? "" : " (incomplete, blit checks apply)"));
      if (s0) { try { restoreState(g, s0); } catch (e5) { /* stay usable */ } }
      try { FNV(63); } catch (e4) { /* caches stay, bindings restored below */ }
      return true;
    } catch (e) {
      if (s0) { try { restoreState(g, s0); } catch (e6) { /* stay usable */ } }
      destroyTarget(); FAILED = true;
      logWarn("grade target setup failed: " + describeError(e));
      return false;
    } finally {
      if (haveBindings) {
        try {
          g.bindFramebuffer(g.READ_FRAMEBUFFER, savedRead);
          g.bindFramebuffer(g.DRAW_FRAMEBUFFER, savedDraw);
        } catch (e0) {
          try { g.bindFramebuffer(g.FRAMEBUFFER, savedDraw); } catch (e1) { /* stay usable */ }
        }
      }
    }
  }

  function destroyTarget() {
    try {
      if (copyFbo) {
        var g = gl();
        if (g) {
          try { g.deleteFramebuffer(copyFbo); } catch (e) { /* ignore */ }
          if (copyTex) { try { g.deleteTexture(copyTex); } catch (e2) { /* ignore */ } }
        }
      } else if (copyTex) {
        var g2 = gl();
        if (g2) { try { g2.deleteTexture(copyTex); } catch (e3) { /* ignore */ } }
      }
    } catch (e) { /* already gone */ }
    copyTex = null; copyFbo = null; copyW = 0; copyH = 0;
  }

  // OFF is a real unload: discard all pack-side GPU objects and the lazily
  // assembled source strings. Re-enabling a pack recompiles on first use.
  function releaseResources() {
    var g = gl();
    try { destroyTarget(); } catch (e) { /* already gone */ }
    try { if (g && vbo && g.deleteBuffer) g.deleteBuffer(vbo); } catch (e2) { /* ignore */ }
    try { if (g && program && g.deleteProgram) g.deleteProgram(program); } catch (e3) { /* ignore */ }
    try { deleteVAO(); } catch (e4) { /* ignore */ }
    program = null; vbo = null; locCache = null; vaoMode = 0;
    VERT_SRC = null; FRAG_SRC = null;
    FAILED = false; FAILS = 0; LAST_ERROR = ""; SKIP_LOGGED = "";
  }

  function describeError(e) {
    try {
      if (e && typeof e === "object") {
        if (typeof e.glCopy !== "undefined") return "copy failed, glError=" + e.glCopy;
        if (typeof e.glBlit !== "undefined") return "resolve failed, glError=" + e.glBlit;
        if (e.message) return String(e.message).slice(0, 160);
        return String(e).slice(0, 160);
      }
      return String(e).slice(0, 160);
    } catch (ignored) { return "unknown"; }
  }

  function noteFail(e, size) {
    FAILS++;
    LAST_ERROR = describeError(e);
    var detail = "grade failed (" + FAILS + "): " + LAST_ERROR;
    if (size) detail += " target=" + size.w + "x" + size.h;
    logWarn(detail);
    if (FAILS >= 5 && !FAILED) {
      FAILED = true;
      logWarn("packs disabled after 5 consecutive failures: " + LAST_ERROR);
      persistStatus();
      try {
        if (lastButton) lastButton.dd = JasprShadersLabelUnavailable();
      } catch (ignored) { /* label stays */ }
    }
    return false;
  }

  function JasprShadersLabelUnavailable() {
    try {
      var text = "Shaders: " + shaderPackSelectorLabel(MODE) + " (unavailable)";
      if (typeof $rt_str !== "undefined") return $rt_str(text);
      return text;
    } catch (e) { return null; }
  }

  function texBinding(g, unit) {
    // Read one unit's binding without disturbing the active unit on exit.
    var cur = null;
    try { cur = g.getParameter(g.ACTIVE_TEXTURE); } catch (e) { cur = null; }
    try { g.activeTexture(unit); } catch (e) { return null; }
    var h = null;
    try { h = g.getParameter(g.TEXTURE_BINDING_2D); } catch (e) { h = null; }
    try { if (cur) g.activeTexture(cur); } catch (e) { /* stay usable */ }
    return h;
  }

  function saveState(g) {
    function param(p) { try { return g.getParameter(p); } catch (e) { return null; } }
    var t1 = 33985;
    try { if (g.TEXTURE1) t1 = g.TEXTURE1; } catch (e) { /* default unit 1 */ }
    var attribs = [];
    for (var i = 0; i < 8; i++) {
      var rec = { enabled: false, size: null, type: null, norm: null, stride: null };
      try {
        rec.enabled = !!g.getVertexAttrib(i, g.VERTEX_ATTRIB_ARRAY_ENABLED);
        try { rec.size = g.getVertexAttrib(i, g.VERTEX_ATTRIB_ARRAY_SIZE); } catch (e0) { rec.size = null; }
        try { rec.type = g.getVertexAttrib(i, g.VERTEX_ATTRIB_ARRAY_TYPE); } catch (e1) { rec.type = null; }
        try { rec.norm = g.getVertexAttrib(i, g.VERTEX_ATTRIB_ARRAY_NORMALIZED); } catch (e2) { rec.norm = null; }
        try { rec.stride = g.getVertexAttrib(i, g.VERTEX_ATTRIB_ARRAY_STRIDE); } catch (e3) { rec.stride = null; }
      } catch (e) { /* ignore */ }
      attribs.push(rec);
    }
    return {
      program: param(g.CURRENT_PROGRAM),
      buffer: param(g.ARRAY_BUFFER_BINDING),
      texture: param(g.TEXTURE_BINDING_2D),
      textures: { u0: texBinding(g, g.TEXTURE0), u1: texBinding(g, t1) },
      active: param(g.ACTIVE_TEXTURE),
      viewport: param(g.VIEWPORT),
      blend: param(g.BLEND),
      depth: param(g.DEPTH_TEST),
      cull: param(g.CULL_FACE),
      scissor: param(g.SCISSOR_TEST),
      srcRgb: param(g.BLEND_SRC_RGB),
      dstRgb: param(g.BLEND_DST_RGB),
      attribs: attribs
    };
  }

  function restoreState(g, s) {
    try {
      try { CGj({ Yv: s.program }); } catch (e) { g.useProgram(s.program); }
      g.bindBuffer(g.ARRAY_BUFFER, s.buffer);
      var t1 = 33985;
      try { if (g.TEXTURE1) t1 = g.TEXTURE1; } catch (e2) { /* default unit 1 */ }
      try {
        var tu = (s.textures || {});
        g.activeTexture(g.TEXTURE0);
        g.bindTexture(g.TEXTURE_2D, tu.u0 === undefined ? s.texture : tu.u0);
        g.activeTexture(t1);
        g.bindTexture(g.TEXTURE_2D, tu.u1 === undefined ? null : tu.u1);
      } catch (e3) {
        g.activeTexture(g.TEXTURE0);
        g.bindTexture(g.TEXTURE_2D, s.texture);
      }
      if (s.active && s.active !== g.TEXTURE0 && s.active !== t1) g.activeTexture(s.active);
      else if (s.active) g.activeTexture(s.active);
      if (s.viewport) g.viewport(s.viewport[0], s.viewport[1], s.viewport[2], s.viewport[3]);
      if (s.blend) g.enable(g.BLEND); else g.disable(g.BLEND);
      if (s.depth) g.enable(g.DEPTH_TEST); else g.disable(g.DEPTH_TEST);
      if (s.cull) g.enable(g.CULL_FACE); else g.disable(g.CULL_FACE);
      if (s.scissor) g.enable(g.SCISSOR_TEST); else g.disable(g.SCISSOR_TEST);
      try {
        if (s.srcRgb !== null && s.dstRgb !== null) g.blendFunc(s.srcRgb, s.dstRgb);
      } catch (e) { /* fixed-function blend state stays */ }
      for (var i = 0; i < 8; i++) {
        try {
          if (s.attribs[i] && s.attribs[i].enabled) g.enableVertexAttribArray(i);
          else g.disableVertexAttribArray(i);
        } catch (e) { /* ignore */ }
      }
    } catch (e) { /* best-effort restore */ }
  }

  // No-VAO contexts only: rewrite the assumed attrib layout disturbed above.
  // Vertex offsets are not queryable anywhere, so position-first layouts
  // (offset 0, the vanilla convention) restore exactly; anything else keeps
  // whatever the draw left, same as before this call.
  function reissueLayout(g, s) {
    for (var i = 0; i < 8; i++) {
      var r = s.attribs[i];
      if (!r || r.size === null || r.type === null) continue;
      try {
        if (r.enabled) g.enableVertexAttribArray(i); else g.disableVertexAttribArray(i);
        g.vertexAttribPointer(i, r.size, r.type, !!r.norm, r.stride || 0, 0);
      } catch (e) { /* best effort */ }
    }
  }

  return {
    mode: function () { return MODE; },
    failed: function () { return FAILED; },
    choose: function (mode) {
      var previous = MODE;
      mode = mode | 0;
      if (mode < 0 || mode >= SHADER_PACK_COUNT) mode = SHADER_PACK_OFF;
      MODE = mode;
      if (MODE === SHADER_PACK_OFF) releaseResources();
      else if (previous === SHADER_PACK_OFF) {
        FAILED = false; FAILS = 0; LAST_ERROR = ""; SKIP_LOGGED = "";
      }
      JasprShadersEnabled = MODE !== SHADER_PACK_OFF && !FAILED;
      persist();
      persistStatus();
      logInfo("selected=" + shaderPackLabel(MODE) + " (" + MODE + ") [sh" + REV + "]");
      try { if (lastButton) lastButton.dd = this.label(); } catch (e) { /* label stays */ }
      return MODE;
    },
    bind: function (button) {
      try { lastButton = button; button.dd = this.label(); } catch (e) { /* label stays */ }
      return button;
    },
    cycle: function () {
      var previous = MODE;
      MODE = shaderPackCycle(MODE);
      if (MODE === SHADER_PACK_OFF) releaseResources();
      else if (previous === SHADER_PACK_OFF) {
        FAILED = false; FAILS = 0; LAST_ERROR = ""; SKIP_LOGGED = "";
      }
      JasprShadersEnabled = MODE !== SHADER_PACK_OFF && !FAILED;
      persist();
      persistStatus();
      logInfo("mode=" + shaderPackLabel(MODE) + " (" + MODE + ") [sh" + REV + "]");
      return MODE;
    },
    label: function (suffix) {
      // This text is rendered inside the native 150px option tile. The full
      // pack name and revision remain available through status/log output.
      var text = "Shaders: " + shaderPackSelectorLabel(MODE) + (suffix || "");
      if (typeof $rt_str !== "undefined") return $rt_str(text);
      return text;
    },
    click: function (button) {
      // Kept for compatibility with an older saved client. The live GUI no
      // longer cycles here; its native row opens the pack picker instead.
      this.bind(button);
      return MODE;
    },
    status: function () {
      var vp = null, unit = null, vao = -9;
      try {
        var g = gl();
        if (g) {
          var v = g.getParameter(g.VIEWPORT);
          if (v) vp = v[0] + "," + v[1] + "," + v[2] + "," + v[3];
          unit = g.getParameter(g.ACTIVE_TEXTURE);
        }
      } catch (e) { /* ignore */ }
      try { vao = vaoMode; } catch (e2) { vao = -9; }
      return { rev: REV, mode: MODE, enabled: JasprShadersEnabled, failed: FAILED, fails: FAILS, lastError: LAST_ERROR, skip: SKIP_LOGGED,
        vaoMode: vaoMode,
        viewport: vp, activeUnit: unit, vaoMode: vao,
        path: (typeof HEf !== "undefined" && HEf && typeof HEf.blitFramebuffer === "function") ? "blit" : "copy",
        target: copyTex ? (copyW + "x" + copyH) : null,
        loaded: !!(program || vbo || copyTex || copyFbo || vaoFake),
        sourcesBuilt: !!(VERT_SRC && FRAG_SRC) };
    },
    reset: function () { releaseResources(); JasprShadersEnabled = MODE !== SHADER_PACK_OFF; },
    // True when the world pass may be graded (single pass only).
    shouldReroute: function (pass) {
      if (!JasprShadersEnabled || FAILED || MODE === 0 || pass !== 2) return false;
      return true;
    },
    // Resolve the just-rendered world into the grade texture (a blit, so
    // multisampled sources resolve instead of erroring), then draw it back
    // over the same buffer with the pack program. The render target, MSAA
    // resolve and present logic are never touched. All engine GL caches are
    // invalidated afterwards (FNV bitmask, the same discipline its own blits
    // use), so later batches re-establish bindings instead of trusting state
    // this pass changed.
    grade: function () {
      var g = gl();
      if (!JasprShadersEnabled || !g || FAILED || MODE === SHADER_PACK_OFF) return false;
      var size = targetSize(g);
      if (!size || size.w < 2 || size.h < 2 || size.w > 4096 || size.h > 4096) {
        if (!SKIP_LOGGED) { SKIP_LOGGED = "size"; logWarn("grade skipping: no target size"); }
        return false;
      }
      if (!ensureTarget(g, size) || !program || !locCache || !copyTex || !copyFbo) {
        if (!SKIP_LOGGED) { SKIP_LOGGED = "target"; logWarn("grade skipping: target unavailable"); }
        return false;
      }
      var useBlit = false;
      try { useBlit = typeof g.blitFramebuffer === "function"; } catch (e) { useBlit = false; }
      var st = saveState(g);
      var savedRead = null, savedDraw = null;
      try {
        try { while (g.getError() !== g.NO_ERROR) { /* drain */ } } catch (e) { /* ignore */ }
        var vaoOk = false, savedVAO = null, haveVAO = false;
        try {
          savedVAO = (typeof HDF !== "undefined") ? HDF : null;
          haveVAO = true;
        } catch (e) { savedVAO = null; haveVAO = false; }
        try {
          if (ensureVAO(g) && vaoFake) { Fwv(vaoFake); vaoOk = true; }
        } catch (e) { vaoOk = false; }
        var canBlit = false;
        try { canBlit = typeof g.blitFramebuffer === "function"; } catch (e) { canBlit = false; }
        var acquired = false, lastErr = g.NO_ERROR;
        g.activeTexture(g.TEXTURE0);
        if (canBlit) {
          try { savedRead = g.getParameter(g.READ_FRAMEBUFFER_BINDING); } catch (e0) {
            try { savedRead = g.getParameter(g.FRAMEBUFFER_BINDING); } catch (e1) { savedRead = null; }
          }
          try { savedDraw = g.getParameter(g.DRAW_FRAMEBUFFER_BINDING); } catch (e2) {
            try { savedDraw = g.getParameter(g.FRAMEBUFFER_BINDING); } catch (e3) { savedDraw = null; }
          }
          try {
            g.bindFramebuffer(g.READ_FRAMEBUFFER, savedRead);
            g.bindFramebuffer(g.DRAW_FRAMEBUFFER, copyFbo);
            g.blitFramebuffer(0, 0, size.w, size.h, 0, 0, size.w, size.h,
              g.COLOR_BUFFER_BIT, g.NEAREST);
            try { lastErr = g.getError(); } catch (e) { lastErr = g.NO_ERROR; }
          } catch (e4) { lastErr = -1; }
          try {
            g.bindFramebuffer(g.READ_FRAMEBUFFER, savedRead);
            g.bindFramebuffer(g.DRAW_FRAMEBUFFER, savedDraw);
          } catch (e5) {
            try { g.bindFramebuffer(g.FRAMEBUFFER, savedDraw); } catch (e6) { /* stay usable */ }
          }
          acquired = (lastErr === g.NO_ERROR);
          if (!acquired) logWarn("resolve blit failed, trying copy: glError=" + lastErr);
        }
        if (!acquired) {
          try {
            g.bindTexture(g.TEXTURE_2D, copyTex);
            g.copyTexImage2D(g.TEXTURE_2D, 0, g.RGBA, 0, 0, size.w, size.h, 0);
            try { lastErr = g.getError(); } catch (e) { lastErr = g.NO_ERROR; }
            acquired = (lastErr === g.NO_ERROR);
          } catch (e7) { lastErr = -2; acquired = false; }
          if (!acquired) throw { glCopy: lastErr };
        }
        // The blit path never binds: unit 0 must carry the copy for the draw
        // unconditionally, or the sampler reads whatever the engine left.
        g.activeTexture(g.TEXTURE0);
        g.bindTexture(g.TEXTURE_2D, copyTex);
        var grade = shaderPackGrade(MODE);
        g.disable(g.BLEND);
        g.disable(g.DEPTH_TEST);
        g.disable(g.CULL_FACE);
        g.disable(g.SCISSOR_TEST);
        try { CGj({ Yv: program }); } catch (e) { g.useProgram(program); }
        g.uniform1i(locCache.uTex, 0);
        g.uniform2f(locCache.uTexel, 1 / size.w, 1 / size.h);
        g.uniform1f(locCache.uExpo, grade.exposure);
        g.uniform1f(locCache.uSat, grade.saturation);
        g.uniform1f(locCache.uCon, grade.contrast);
        g.uniform1f(locCache.uGamma, grade.gamma || 1.0);
        g.uniform1f(locCache.uVig, grade.vignette);
        g.uniform1f(locCache.uTilt, grade.tilt);
        g.uniform1f(locCache.uFocusY, grade.focusY);
        g.uniform1f(locCache.uFocusH, grade.focusH);
        g.uniform1f(locCache.uBlur, grade.blur);
        g.uniform1f(locCache.uDither, grade.dither);
        g.uniform1f(locCache.uTonemap, grade.tonemap);
        g.uniform3f(locCache.uTint, grade.tint[0], grade.tint[1], grade.tint[2]);
        g.bindBuffer(g.ARRAY_BUFFER, vbo);
        g.enableVertexAttribArray(0);
        g.vertexAttribPointer(0, 2, g.FLOAT, false, 0, 0);
        g.drawArrays(g.TRIANGLES, 0, 3);
        g.disableVertexAttribArray(0);
        if (haveVAO) { try { Fwv(savedVAO); } catch (e) { /* binding caches reset below */ } }
        restoreState(g, st);
        if (vaoMode < 0) { try { reissueLayout(g, st); } catch (e2) { /* best effort */ } }
        try { FNV(63); } catch (e) { /* caches stay, restore covered bindings */ }
        if (FAILS > 0) { FAILS = 0; LAST_ERROR = ""; }
        if (SKIP_LOGGED) SKIP_LOGGED = "";
        return true;
      } catch (e) {
        try {
          if (haveVAO) {
            try { Fwv(savedVAO); } catch (ignored3) { /* caches reset below */ }
          }
        } catch (ignored4) { /* stay usable */ }
        try { restoreState(g, st); } catch (ignored) { /* stay usable */ }
        if (vaoMode < 0) { try { reissueLayout(g, st); } catch (ignored5) { /* stay usable */ } }
        try { FNV(63); } catch (ignored2) { /* stay usable */ }
        return noteFail(e, size);
      }
    },
    // Lightmap core: raise vanilla texels toward the pack curve (never darken).
    gradeLightmap: function (renderer) {
      if (!JasprShadersEnabled || FAILED || MODE === 0) return false;
      try {
        var arr = renderer && renderer.cKO && renderer.cKO.data;
        if (!arr || arr.length < 256) return false;
        for (var f = 0; f < 256; f++) {
          var v = arr[f] | 0;
          var vr = ((v >> 16) & 255) / 255, vg = ((v >> 8) & 255) / 255, vb = (v & 255) / 255;
          var pack = shaderLightTexel(MODE, f % 16, (f / 16) | 0);
          var r = Math.max(vr, pack[0]), g = Math.max(vg, pack[1]), b = Math.max(vb, pack[2]);
          arr[f] = (0xFF000000 | ((r * 255 | 0) << 16) | ((g * 255 | 0) << 8) | (b * 255 | 0)) | 0;
        }
        if (renderer.bKW && typeof Egf !== "undefined") Egf(renderer.bKW);
        return true;
      } catch (e) { return false; }
    },
    fog: function () {
      if (!JasprShadersEnabled || FAILED || MODE === 0) return null;
      return shaderPackFog(MODE);
    }
  };
}());

function JasprShadersPickerTitle() {
  var text = "Shader Packs";
  if (typeof $rt_str !== "undefined") return $rt_str(text);
  return text;
}

function JasprShadersPickerLabel(mode) {
  mode = mode | 0;
  // Never fall back to OFF for an out-of-range visual row. That can make a
  // stale native row look like a duplicate pack after a list migration.
  if (mode < 0 || mode >= SHADER_PACK_COUNT) return "";
  var text = shaderPackLabel(mode);
  if (JasprShaders.mode() === mode) text = "* " + text;
  if (typeof $rt_str !== "undefined") return $rt_str(text);
  return text;
}

// Native GuiOptionButton factory used by the picker rows. This is a fiber
// because LIw may initialize translated button text through TeaVM.
function JasprShadersPickerButton(a, b, c) {
  var d, e, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  // The native row builder can suspend and resume between the two columns.
  // Recompute the label from the stable button ID at the last possible point
  // so a resumed row can never inherit another pack's label.
  e = (a | 0) - 910;
  if (e >= 0 && e < SHADER_PACK_COUNT) c = JasprShadersPickerLabel(e);
  _:while (true) { switch ($p) {
    case 0:
      $p = 1;
    case 1:
      $z = LIw(a, b, 0, c); if (B()) break _;
      d = $z;
      if (d && e >= 0 && e < SHADER_PACK_COUNT) d.dd = c;
      return d;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

// Builds every picker row itself rather than borrowing the native two-column
// row loop. That loop pairs a left and a right option per row and carries the
// pair through a fiber that can suspend between the two columns; reusing it for
// a list it was not written for is what put the same pack on screen twice and
// dropped another. One pack per row, the id is the index, and the label is read
// from the id -- so a row cannot show a pack it is not.
function JasprShadersBuildPicker(a, b, c) {
  var d, e, f, g, h, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); h = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      d = (c / 2 | 0) - 155 | 0;
      e = 0;
      $p = 1;
    case 1:
      if (e >= SHADER_PACK_COUNT) { a.bGZ = b; return; }
      $p = 2;
    case 2:
      $z = LIw(910 + (e | 0), d, 0, JasprShadersPickerLabel(e)); if (B()) break _;
      f = $z;
      $p = 3;
    case 3:
      $z = E32(); if (B()) break _;
      g = $z;
      h = new Bv1;
      h.Wy = g;
      h.bEa = f;
      h.bdH = null;
      Y(b.csQ, h);
      e = e + 1 | 0;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, g, h, $p);
}

// Append the main selector to GuiOptionsRowList after the native options.
// It therefore scrolls with Video Settings and never competes with Done.
function JasprShadersAppendVideoRow(a, b, c) {
  var d, e, f, g, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      d = (c / 2 | 0) - 155 | 0;
      $p = 1;
    case 1:
      $z = LIw(901, d, 0, JasprShaders.label()); if (B()) break _;
      e = $z;
      f = b.csQ;
      g = new Bv1;
      $p = 2;
    case 2:
      $z = E32(); if (B()) break _;
      g.Wy = $z;
      g.bEa = JasprShaders.bind(e);
      g.bdH = null;
      Y(f, g);
      a.bGZ = b;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, g, $p);
}

// Fiber: render the world pass natively, then grade a copy back over it.
function JasprShadersPass(a, f, b, c) {
  var $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); c = $T.l(); b = $T.l(); f = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (!JasprShadersEnabled || !JasprShaders.shouldReroute(f)) { Ctb(a, f, b, c); if (B()) break _; return; }
      $p = 1;
    case 1:
      Ctb(a, f, b, c); if (B()) break _;
      $p = 2;
    case 2:
      JasprShaders.grade();
      return;
    default: FT();
  } }
  Ds().s(a, f, b, c, $p);
}

function JasprShadersLightmap(a) {
  if (!JasprShadersEnabled) return;
  JasprShaders.gradeLightmap(a);
}

function GyZ(a, b) {
  GyZ_orig(a, b);
  JasprShadersLightmap(a);
}

function GmS(a, b) {
  GmS_orig(a, b);
  if (!JasprShadersEnabled) return;
  var m = JasprShaders.fog();
  if (m) { a.eH *= m[0]; a.eF *= m[1]; a.eJ *= m[2]; }
}

if (typeof window !== "undefined" && window) {
  try { window.JasprShadersDiagnostics = Object.freeze({ status: function () { return JasprShaders.status(); } }); }
  catch (e) { /* diagnostics stay local-only */ }
}
