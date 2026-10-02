/* JasperCraft Mo' Bends: native adapter for the TeaVM 1.12.2 client (see scripts/build-mobends-client.cjs).
 * Mo' Bends 1.2.2 is Copyright (c) 2017 Iwo Plaza and contributors, MIT License (THIRD_PARTY_NOTICES.txt).
 * Minified client names are isolated in the first section. The rest follows Mo' Bends' own classes:
 * ModelPart / ModelPartExtended / ModelPartPostOffset / PartContainer, BoxFactory / MutatedBox / BoxMutator,
 * the mutators, the armor wrapper, LayerCustomHeldItem / Cape / Elytra, LayerWolfMisc and the sword/arrow trails.
 * Hooks are entered at completed renderer fiber boundaries; nothing here suspends.
 */
var JasprMoBendsBridge = (function () {
  "use strict";
  var STORAGE_KEY = "jaspr.mobends.v1", SCALE = 0.0625, PI = Math.PI;

  // ===================== native names (TeaVM 1.12.2 u1 client) =====================
  // Entities: posX/Y/Z b f c, prevPos dn d9 dv, motion s p t, yaw C/cy, pitch bd/c2, body yaw cZ/s1, head yaw gN/zM,
  // limb swing CE/hp/qi, swing bYC/G1, ticksExisted cv, world a, riding fS, inWater e1, bounding box bc, id cu,
  // height bZ, active stack l0 (use count wS), elytra ticks bwP, capabilities bC (isFlying mw), dead ed.
  function guardClinit() { G9(); Hc(); Wq(); By(); Dt(); AKp(); U6(); HJ(); C5(); }
  var clinitDone = false;
  function clinits() { if (!clinitDone) { guardClinit(); clinitDone = true; } }
  var E = {
    id: function (e) { return e.cu; },
    posX: function (e) { return e.b; }, posY: function (e) { return e.f; }, posZ: function (e) { return e.c; },
    prevPosX: function (e) { return e.dn; }, prevPosY: function (e) { return e.d9; }, prevPosZ: function (e) { return e.dv; },
    motionX: function (e) { return e.s; }, motionY: function (e) { return e.p; }, motionZ: function (e) { return e.t; },
    rotationYaw: function (e) { return e.C; }, prevRotationYaw: function (e) { return e.cy; },
    rotationPitch: function (e) { return e.bd; }, prevRotationPitch: function (e) { return e.c2; },
    renderYawOffset: function (e) { return e.cZ; }, prevRenderYawOffset: function (e) { return e.s1; },
    rotationYawHead: function (e) { return e.gN; }, prevRotationYawHead: function (e) { return e.zM; },
    limbSwing: function (e) { return e.CE; }, limbSwingAmount: function (e) { return e.hp; }, prevLimbSwingAmount: function (e) { return e.qi; },
    getSwingProgress: function (e, pt) { return C3W(e, pt); },
    swingProgressField: function (e) { return e.bYC; },
    isSwingInProgress: function (e) { return !!e.G1; },
    ticksExisted: function (e) { return e.cv; },
    height: function (e) { return e.bZ; },
    hasWorld: function (e) { return e.a != null; },
    isChild: function (e) { return !!e.bV5(); },
    isRiding: function (e) { return e.fS !== null; },
    ridingEntity: function (e) { return e.fS; },
    isLiving: function (x) { return x instanceof Co; },
    isSneaking: function (e) { return !!e.q1(); },
    isSprinting: function (e) { return !!CBf(e); },
    isInWater: function (e) { return !!e.e1; },
    isOnLadder: function (e) { return !!e.cxy(); },
    lookVec: function (e) { var v = Dyw(e); return [v.bh, v.bq, v.bi]; },
    primaryHandRight: function (e) { G9(); return e.bbx() === Kua; },
    mainStack: function (e) { return EZ5(e); }, offStack: function (e) { return EjD(e); }, activeStack: function (e) { return e.l0; },
    mainItem: function (e) { return C52(EZ5(e)); }, offItem: function (e) { return C52(EjD(e)); },
    stackUseAction: function (stack) { Wq(); var a = Fr4(stack); return a === Kul ? "block" : a === Lbp ? "bow" : a === Kui ? "eat" : a === Kuh ? "drink" : "none"; },
    itemInUseCount: function (e) { return e.wS; },
    itemInUseMaxCount: function (e) { return EG7(e) ? CCb(e.l0) - e.wS : 0; },
    activeHandMain: function (e) { Hc(); return e.dBS() === HFd; },
    isEntityAlive: function (e) { return !!e.ekW(); },
    isPlayerSleeping: function (e) { return !!e.edh(); },
    ticksElytraFlying: function (e) { return e.bwP; },
    capabilitiesFlying: function (e) { return !!(e.bC && e.bC.mw); },
    health: function (e) { return ENU(e); },
    smallArms: function () { return !!(currentRenderer && currentRenderer.d9q); },
    chasing: function (p) { return [p.bp1, p.bp0, p.bp2, p.Ts, p.Tu, p.Tt]; },
    cameraYaw: function (p) { return [p.bui, p.wj]; },
    distanceWalked: function (p) { return [p.UA, p.Iy]; },
    wolfSitting: function (e) { return !!F9J(e); },
    wolfInterestedAngle: function (e, pt) { return Math.fround(Math.fround(e.cUv + (e.blR - e.cUv) * pt) * Math.fround(0.15) * Math.fround(PI)); },
    wolfShakeAngle: function (e, pt, o) { return CF$(e, pt, o); },
    wolfTailRotation: function (e) { return EUr(null, e, 0); },
    squidRotation: function (e) { return e.a2l; }, prevSquidRotation: function (e) { return e.em8; },
    besideClimbable: function (e) { return !!Dn1(e); }
  };
  function stateAt(e, x, y, z) { return e.a.cO(Dy(x | 0, y | 0, z | 0)); }
  var W = {
    isStairs: function (e, x, y, z) { return D0(stateAt(e, x, y, z)) instanceof GY; },
    isClimbable: function (e, x, y, z) { var b = D0(stateAt(e, x, y, z)); return b instanceof AFo || b instanceof Ro; },
    isStaticLiquid: function (e, x, y, z) { return D0(stateAt(e, x, y, z)) instanceof BkV; },
    isAir: function (e, x, y, z) { return D0(stateAt(e, x, y, z)) instanceof Blj; },
    climbableFacing: function (e, x, y, z) {
      var s = stateAt(e, x, y, z), b = D0(s);
      if (b instanceof AFo) { U6(); var f = CAi(s, KtW); return f === KsS || f === HFo ? "north" : f === KsW ? "south" : f === KsT ? "west" : f === KsU ? "east" : "north"; }
      if (b instanceof Ro) { HJ(); if (CAi(s, KXV).br) return "west"; if (CAi(s, KXW).br) return "east"; if (CAi(s, KXY).br) return "south"; if (CAi(s, KXX).br) return "north"; }
      return "north";
    },
    collidesBelow: function (e, d) { return DNo(e.a, e, FF(e.bc, 0, -d, 0)).g > 0; }
  };
  var I = {
    isAir: function (item) { By(); return item === KIO || item instanceof BlY; },
    isSword: function (item) { return item instanceof OE; },
    isFood: function (item) { return item instanceof HM; },
    isBow: function (item) { return item instanceof AM9; },
    isTorch: function (item) { return item instanceof Ht && item.oD !== null && item.oD.constructor === Xh; },
    stackEmpty: function (stack) { return !!CCH(stack); },
    stackItem: function (stack) { return C52(stack); }
  };
  var scratchMatrix = null;
  var GL = {
    translate: function (x, y, z) { DPm(x, y, z); },
    rotate: function (a, x, y, z) { Gc9(a, x, y, z); },
    scale: function (x, y, z) { FWM(x, y, z); },
    push: function () { Eu0(); },
    pop: function () { ECi(); },
    color: function (r, g, b, a) { CFh(r, g, b, a); },
    // GlHelper.rotate: multMatrix(quatToGlMatrix(q)) through the engine's own matrix multiply (keeps its access serials)
    multQuat: function (x, y, z, w) {
      if (!scratchMatrix) scratchMatrix = new (HKM.data[0].constructor)();
      var m = scratchMatrix, f = x * x, f1 = x * y, f2 = x * z, f3 = x * w, f4 = y * y, f5 = y * z, f6 = y * w, f7 = z * z, f8 = z * w;
      m.h_ = 1 - 2 * (f4 + f7); m.h$ = 2 * (f1 + f8); m.ia = 2 * (f2 - f6); m.g4 = 0;
      m.h7 = 2 * (f1 - f8); m.h9 = 1 - 2 * (f + f7); m.h8 = 2 * (f5 + f3); m.g3 = 0;
      m.h5 = 2 * (f2 + f6); m.hy = 2 * (f5 - f3); m.h6 = 1 - 2 * (f + f4); m.gy = 0;
      m.lB = 0; m.lD = 0; m.lC = 0; m.jU = 1;
      EQk(m);
    }
  };
  function glState() { return { blend: HHV, cull: HHT, lighting: Kri, tex: Krr.data[0], active: HEo, src: Krc & 65535, dst: Krd & 65535, color: [HKI, HKJ, HKK, HKL] }; }
  function restoreGl(s) {
    GnI(33984); if (s.tex) CQ6(); else DCQ();
    if (s.lighting) D75(); else DFk();
    if (s.cull) Ggy(); else F1Q();
    Fb_(s.src, s.dst); if (s.blend) CyM(); else CTP();
    CFh(s.color[0], s.color[1], s.color[2], s.color[3]); GnI(33984 + s.active);
  }
  // A sword trail is GL_QUADS of colored vertices (the original's immediate-mode glBegin/glVertex path).
  function drawTrail(verts) {
    var s = glState();
    try {
      GnI(33984); DCQ(); DFk(); F1Q(); CyM(); Fb_(770, 771); CcT(515);
      var t = GdM(), buf = t.dy; C5(); Ep0(buf, 7, HLn);
      for (var i = 0; i < verts.length; i += 7) { CUb(buf, verts[i], verts[i + 1], verts[i + 2]); Eip(buf, verts[i + 3], verts[i + 4], verts[i + 5], verts[i + 6]); E74(buf); }
      FE$(t);
    } finally { restoreGl(s); }
  }
  // ===================== end of native names in the accessor section =====================

  var currentRenderer = null, lastRenderManager = null;
  var N = {
    gl: GL, entity: E, world: W, item: I, drawTrail: drawTrail,
    viewEntity: function () { return lastRenderManager ? lastRenderManager.a18 : null; },
    worldEntityById: function (id) { var p = HEH && HEH.v, w = p && p.a; return w ? w.baK(id) : null; },
    loadBendsAnimation: function (key) {
      var b64 = typeof JasprMoBendsAssets !== "undefined" && JasprMoBendsAssets.animations[key];
      if (!b64) return null;
      var bin = $rt_globals.atob(b64), bytes = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return core.loadBinaryAnimation(bytes);
    },
    report: function (event, error) { report(event, error); }
  };
  var core = createJasprMoBendsCore(N), DUH = core.DataUpdateHandler, DB = core.EntityDatabase;
  var Vec3f = core.Vec3f, SmoothOrientation = core.SmoothOrientation, Quaternion = core.Quaternion, jint = core.jint;

  // ===================== settings, failure handling, diagnostics =====================
  var enabled = true, failed = false, lastError = "";
  var stats = { mutations: 0, demutations: 0, entities: 0, armorWrappers: 0, trails: 0, errors: 0, frames: 0, ticks: 0, heldItems: 0, capes: 0, elytras: 0, firstPerson: 0, wolfLayers: 0 };
  try { var saved = JSON.parse($rt_globals.localStorage.getItem(STORAGE_KEY)); if (saved && saved.enabled === false) enabled = false; } catch (_) {}
  function persist() { try { $rt_globals.localStorage.setItem(STORAGE_KEY, JSON.stringify({ enabled: enabled })); } catch (_) {} }
  var diagnostics = { sent: 0, page: "mobends-" + Date.now().toString(36) };
  function send(event, details) {
    // Bounded, same-origin, through the existing diagnostics route; no names, positions, chat or credentials.
    try {
      var loc = $rt_globals.location;
      if (diagnostics.sent >= 12 || !loc || String(loc.pathname).indexOf("/jaspercraft/") !== 0 || typeof $rt_globals.fetch !== "function") return;
      diagnostics.sent++;
      $rt_globals.fetch("/api/diagnostics/events", { method: "POST", credentials: "same-origin", cache: "no-store", headers: { "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1" },
        body: JSON.stringify({ events: [{ event: event, pageSessionId: diagnostics.page, at: new Date().toISOString(), details: details }] }) }).catch(function () {});
    } catch (_) {}
  }
  function report(event, error) {
    stats.errors++;
    lastError = String(error && error.message || error).replace(/[\r\n]+/g, " ").slice(0, 180);
    if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft Mo' Bends] " + event + ": " + lastError);
    send("jaspercraft.mobends.error", { stage: String(event).slice(0, 40), error: lastError, stats: statsCopy() });
  }
  function statsCopy() { var o = {}; for (var k in stats) o[k] = stats[k]; return o; }
  // Any exception disables Mo' Bends for the session and puts every vanilla model back, so rendering continues.
  function fail(stage, error) {
    if (failed) return;
    failed = true; report(stage, error);
    try { restoreAll(); } catch (e) { report("restore", e); }
  }
  function matrixDepth() { return KrH === 5888 ? HKD : -1; }
  function unwindMatrix(depth) { if (depth >= 0 && KrH === 5888 && HKD > depth) HKD = depth; }

  // ===================== Mo' Bends model parts on native ModelRenderer objects =====================
  function list(l) { return l && l.qN ? Array.prototype.slice.call(l.qN.data, 0, l.g) : []; }
  // ModelPart(ModelBase, register, texU, texV) of kind part | extended | postOffset | container
  function ModelPart(model, register, texU, texV, kind) {
    var r = new M2();
    if (register) DGf(r, model, null);
    else { r.bdO = 64; r.bby = 32; r.eT = 1; r.a6Y = Bq(); r.ddy = model; r.dI3 = null; FR(r, model.vI, model.vd); }
    DW(r, texU, texV);
    r.$mb = this; this.native = r; this.kind = kind || "part";
    this.position = new Vec3f(); this.scale = new Vec3f(1, 1, 1); this.offset = new Vec3f(); this.rotation = new SmoothOrientation();
    this.offsetScale = 1; this.globalOffset = new Vec3f(); this.mutatedBoxes = []; this.parent = null;
    this.extension = null; this.extensionChild = null; this.postOffset = new Vec3f();
    this.inner = null; this.innerOffset = new Vec3f();
  }
  var P = ModelPart.prototype;
  P.isModelPart = true;
  P.getPosition = function () { return this.position; };
  P.getScale = function () { return this.scale; };
  P.getOffset = function () { return this.offset; };
  P.getRotation = function () { return this.rotation; };
  P.getOffsetScale = function () { return this.offsetScale; };
  P.getGlobalOffset = function () { return this.globalOffset; };
  P.getParent = function () { return this.parent; };
  P.isShowing = function () { return !!this.native.eT && !this.native.cIT; };
  P.setVisible = function (v) { this.native.eT = v ? 1 : 0; };
  P.update = function (tpf) { this.rotation.update(tpf); };
  P.setPosition = function (x, y, z) { this.position.set(x, y, z); return this; };
  P.setParent = function (p) { this.parent = p; return this; };
  P.setMirror = function (m) { this.native.i$ = m ? 1 : 0; return this; };
  P.setPostOffset = function (x, y, z) { this.postOffset.set(x, y, z); return this; };
  P.setInnerOffset = function (x, y, z) { this.innerOffset.set(x, y, z); return this; };
  // Pivot hint for the dismemberment system only (Mo' Bends ignores rotation points when it draws).
  P.pivot = function (x, y, z) { var r = this.native; r.cD = x; r.bs = y; r.bA = z; return this; };
  // ModelPartExtended.setExtension. The extension is also listed as a child so the dismemberment system detaches
  // a forearm or shin together with its upper limb; renderPart skips it there because it is drawn via renderJustPart.
  P.setExtension = function (part) {
    if (this.extensionChild) { var l = this.native.OS; if (l) ECM(l, this.extensionChild); }
    this.extension = part; this.extensionChild = part ? part.native : null;
    if (part) HV(this.native, part.native);
    return this;
  };
  P.addChild = function (part) { HV(this.native, part.native); return this; };
  P.syncUp = function (o) {
    if (o == null) return;
    this.position.copy(o.getPosition()); this.offset.copy(o.getOffset()); this.rotation.copy(o.getRotation());
    this.scale.copy(o.getScale()); this.offsetScale = o.getOffsetScale();
    if (this.kind !== "container") this.globalOffset.copy(o.getGlobalOffset());
  };
  P.applyPreTransform = function (scale) { var g = this.globalOffset; if (g.x !== 0 || g.y !== 0 || g.z !== 0) GL.translate(g.x * scale, g.y * scale, g.z * scale); };
  P.applyLocalTransform = function (scale) { core.applyLocal(this, scale); };
  P.applyCharacterTransform = function (scale) {
    if (this.kind === "container") { if (this.parent != null) this.parent.applyCharacterTransform(scale); this.applyLocalTransform(scale); }
    else core.applyCharacter(this, scale);
  };
  P.applyPostTransform = function (scale) {
    if (this.kind === "extended") { if (this.extension != null) this.extension.propagateTransform(scale); }
    else if (this.kind === "postOffset") GL.translate(this.postOffset.x * scale, this.postOffset.y * scale, this.postOffset.z * scale);
  };
  P.propagateTransform = function (scale) {
    this.applyLocalTransform(scale); this.applyPostTransform(scale);
    if (this.kind === "extended") this.applyPostTransform(scale);   // ModelPartExtended calls it once more, as the original does
  };
  P.postRender = function (scale) { this.applyCharacterTransform(scale); this.applyPostTransform(scale); };
  P.compile = function (scale) {
    for (var i = 0; i < this.mutatedBoxes.length; i++) syncVisibleQuads(this.mutatedBoxes[i]);
    F$t(this.native, scale);
  };
  function drawList(r, scale) { if (typeof JasprGoreDraw === "function") JasprGoreDraw(r, scale, r.bWg); else Dle(r.bWg); }
  function renderChildren(r, scale, skip) {
    var l = r.OS;
    if (l === null || l === undefined) return;
    for (var i = 0; i < l.g; i++) { var c = l.qN.data[i]; if (c !== skip) E7Q(c, scale); }
  }
  P.renderPart = function (scale) {
    if (!this.isShowing()) return;
    if (this.kind === "container") { this.renderContainer(scale, false); return; }
    var r = this.native;
    if (!r.clh) this.compile(scale);
    GL.push();
    this.applyCharacterTransform(scale);
    drawList(r, scale);
    if (this.kind === "extended" && this.extension != null) this.extension.renderJustPart(scale);
    renderChildren(r, scale, this.extensionChild);
    GL.pop();
  };
  P.renderJustPart = function (scale) {
    if (!this.isShowing()) return;
    if (this.kind === "container") { this.renderContainer(scale, true); return; }
    var r = this.native;
    if (!r.clh) this.compile(scale);
    GL.push();
    this.applyLocalTransform(scale);
    drawList(r, scale);
    if (this.kind === "extended" && this.extension != null) this.extension.renderJustPart(scale);
    renderChildren(r, scale, this.extensionChild);
    GL.pop();
  };
  // PartContainer.renderPart / renderJustPart: draws a vanilla armor part with zeroed angles inside Mo' Bends' transform
  P.renderContainer = function (scale, justPart) {
    GL.push();
    if (justPart) this.applyLocalTransform(scale); else this.applyCharacterTransform(scale);
    var io = this.innerOffset;
    if (io.x !== 0 || io.y !== 0 || io.z !== 0) GL.translate(io.x * scale, io.y * scale, io.z * scale);
    var m = this.inner, x = m.cD, y = m.bs, z = m.bA, ox = m.bot, oy = m.bcS, oz = m.bcR;
    m.A = m.bb = m.bX = 0; m.cD = m.bs = m.bA = 0; m.bot = m.bcS = m.bcR = 0; m.eT = 1; m.cIT = 0;
    E7Q(m, scale);
    m.cD = x; m.bs = y; m.bA = z; m.bot = ox; m.bcS = oy; m.bcR = oz;
    renderChildren(this.native, scale, null);
    GL.pop();
  };
  P.addBox = function (x, y, z, w, h, l, delta) {
    var r = this.native, box = F2d(r, r.bH9, r.bH$, x, y, z, w, h, l, delta || 0);
    Y(r.a6Y, box); r.clh = 0; return this;
  };
  P.addMutatedBox = function (box) { this.mutatedBoxes.push(box); Y(this.native.a6Y, box); this.native.clh = 0; return this; };
  P.addVanillaBox = function (box) { Y(this.native.a6Y, box); this.native.clh = 0; return this; };
  P.developBox = function (x, y, z, dx, dy, dz, sf) { return new BoxFactory(x, y, z, dx, dy, dz, sf).setTarget(this); };
  function PartContainer(model, inner) {
    ModelPart.call(this, model, true, 0, 0, "container");
    this.inner = inner; this.native.i$ = inner.i$;
  }
  PartContainer.prototype = P;

  // ---- BoxFactory / TextureFace / MutatedBox / BoxMutator
  var LEFT = 0, RIGHT = 1, TOP = 2, BOTTOM = 3, FRONT = 4, BACK = 5;
  function TextureFace(u, v, us, vs) { this.uPos = u; this.vPos = v; this.uSize = us; this.vSize = vs; this.faceRotation = "IDENTITY"; }
  function BoxFactory(x, y, z, dx, dy, dz, delta) {
    delta = delta || 0;
    this.min = new Vec3f(x - delta, y - delta, z - delta); this.max = new Vec3f(x + dx + delta, y + dy + delta, z + dz + delta);
    this.faces = [null, null, null, null, null, null]; this.uvWidth = dx; this.uvHeight = dy; this.uvLength = dz;
    this.mirrored = false; this.faceVisibilityFlag = 63; this.textureU = 0; this.textureV = 0; this.textureUVSet = false; this.target = null;
  }
  // BoxFactory(ModelRenderer, ModelBox): faces read back from a vanilla box's texture coordinates
  BoxFactory.fromBox = function (renderer, source) {
    var f = new BoxFactory(0, 0, 0, 0, 0, 0, 0);
    f.min.set(source.dsk, source.dsh, source.dse); f.max.set(source.dsl, source.dsi, source.dsf);
    f.mirrored = !!renderer.i$;
    var quads = source.a4t;
    if (quads == null) return f;
    var tw = renderer.bdO, th = renderer.bby;
    f.textureUVSet = true;
    for (var i = 0; i < 6; i++) {
      var v = quads.data[i].a4R.data, start = f.mirrored ? v[2] : v[1], end = f.mirrored ? v[0] : v[3];
      f.faces[i] = new TextureFace(jint(Math.fround(start.cDP * tw)), jint(Math.fround(start.cDQ * th)), jint(Math.fround((end.cDP - start.cDP) * tw)), jint(Math.fround((end.cDQ - start.cDQ) * th)));
    }
    return f;
  };
  BoxFactory.fromFaces = function (x0, y0, z0, x1, y1, z1, faces) {
    var f = new BoxFactory(0, 0, 0, 0, 0, 0, 0);
    f.min.set(x0, y0, z0); f.max.set(x1, y1, z1);
    for (var i = 0; i < faces.length; i++) f.faces[i] = new TextureFace(faces[i].uPos, faces[i].vPos, faces[i].uSize, faces[i].vSize);
    f.textureUVSet = true; return f;
  };
  var BF = BoxFactory.prototype;
  BF.setTarget = function (target) {
    this.target = target;
    if (!this.textureUVSet) { this.textureU = target.native.bH9; this.textureV = target.native.bH$; this.generateTextureFaces(); }
    return this;
  };
  BF.inflate = function (dx, dy, dz) { this.min.add(-dx, -dy, -dz); this.max.add(dx, dy, dz); return this; };
  BF.setWidth = function (w) { this.max.x = this.min.x + w; return this; };
  BF.setHeight = function (h) { this.max.y = this.min.y + h; return this; };
  BF.setLength = function (l) { this.max.z = this.min.z + l; return this; };
  BF.resize = function (dx, dy, dz) { this.max.set(this.min.x + dx, this.min.y + dy, this.min.z + dz); return this; };
  BF.hideFace = function (side) { this.faceVisibilityFlag &= ~(1 << side); return this; };
  BF.showFace = function (side) { this.faceVisibilityFlag |= 1 << side; return this; };
  BF.offsetTextureQuad = function (side, x, y) {
    if (!this.textureUVSet) { this.textureUVSet = true; this.generateTextureFaces(); }
    var f = this.faces[side]; f.uPos = jint(f.uPos + x); f.vPos = jint(f.vPos + y); return this;
  };
  BF.rotateTextureQuad = function (side, rotation) {
    if (!this.textureUVSet) { this.textureUVSet = true; this.generateTextureFaces(); }
    this.faces[side].faceRotation = rotation; return this;
  };
  BF.offset = function (x, y, z) { this.min.add(x, y, z); this.max.add(x, y, z); return this; };
  BF.create = function () { var box = mutatedBox(this.target.native, this.min, this.max, this.faces, this.faceVisibilityFlag); if (this.target) this.target.addMutatedBox(box); return box; };
  BF.createFor = function (part) { return mutatedBox(part.native, this.min, this.max, this.faces, this.faceVisibilityFlag); };
  BF.generateTextureFaces = function () {
    var u = this.textureU, v = this.textureV, w = this.uvWidth, h = this.uvHeight, l = this.uvLength;
    this.faces[0] = new TextureFace(u + l + w, v + l, l, h);
    this.faces[1] = new TextureFace(u, v + l, l, h);
    this.faces[2] = new TextureFace(u + l, v, w, l);
    this.faces[3] = new TextureFace(u + l + w, v + l, w, -l);
    this.faces[4] = new TextureFace(u + l, v + l, w, h);
    this.faces[5] = new TextureFace(u + l + w + l, v + l, w, h);
  };
  // ModelUtils.createQuad / applyFaceRotation
  function createQuad(positions, face, tw, th) {
    var us = face.uSize, vs = face.vSize;
    if (face.faceRotation === "CLOCKWISE" || face.faceRotation === "COUNTER_CLOCKWISE") { us = face.vSize; vs = face.uSize; }
    var quad = A5Y(T(Xb, positions), face.uPos, face.vPos, face.uPos + us, face.vPos + vs, tw, th);
    if (face.faceRotation !== "IDENTITY") {
      var vtx = quad.a4R.data, u = [vtx[0].cDP, vtx[1].cDP, vtx[2].cDP, vtx[3].cDP], vv = [vtx[0].cDQ, vtx[1].cDQ, vtx[2].cDQ, vtx[3].cDQ];
      var off = face.faceRotation === "CLOCKWISE" ? 3 : face.faceRotation === "COUNTER_CLOCKWISE" ? 1 : 2;
      for (var i = 0; i < 4; i++) { vtx[i].cDP = u[(i + off) % 4]; vtx[i].cDQ = vv[(i + off) % 4]; }
    }
    return quad;
  }
  function flipFace(quad) { var d = quad.a4R.data, r = []; for (var i = d.length - 1; i >= 0; i--) r.push(d[i]); quad.a4R = T(Xb, r); }
  // MutatedBox(renderer, min, max, faces, faceVisibilityFlag): a real ModelBox whose quad list holds the visible faces
  function mutatedBox(renderer, min, max, faces, flag) {
    var box = new DZG();
    Ehl(box, renderer, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    var x0 = min.x, y0 = min.y, z0 = min.z, x1 = max.x, y1 = max.y, z1 = max.z, t;
    if (renderer.i$) { t = x1; x1 = x0; x0 = t; }
    var v7 = AMs(x0, y0, z0, 0, 0), v = AMs(x1, y0, z0, 0, 8), v1 = AMs(x1, y1, z0, 8, 8), v2 = AMs(x0, y1, z0, 8, 0);
    var v3 = AMs(x0, y0, z1, 0, 0), v4 = AMs(x1, y0, z1, 0, 8), v5 = AMs(x1, y1, z1, 8, 8), v6 = AMs(x0, y1, z1, 8, 0);
    var vp = box.dSQ.data; vp[0] = v7; vp[1] = v; vp[2] = v1; vp[3] = v2; vp[4] = v3; vp[5] = v4; vp[6] = v5; vp[7] = v6;
    var tw = renderer.bdO, th = renderer.bby;
    var quads = [createQuad([v4, v, v1, v5], faces[0], tw, th), createQuad([v7, v3, v6, v2], faces[1], tw, th), createQuad([v4, v3, v7, v], faces[2], tw, th),
      createQuad([v1, v2, v6, v5], faces[3], tw, th), createQuad([v, v7, v2, v1], faces[4], tw, th), createQuad([v3, v4, v5, v6], faces[5], tw, th)];
    if (renderer.i$) for (var i = 0; i < 6; i++) flipFace(quads[i]);
    box.$mbQuads = quads; box.$mbVisibility = flag;
    syncVisibleQuads(box);
    return box;
  }
  function syncVisibleQuads(box) {
    if (!box.$mbQuads) return;
    var out = [];
    for (var i = 0; i < 6; i++) if ((box.$mbVisibility >> i) & 1) out.push(box.$mbQuads[i]);
    box.a4t = T(Bgq, out);
  }
  function BoxMutator(model, renderer, factory, u, v) { this.targetModel = model; this.targetRenderer = renderer; this.factory = factory; this.textureOffsetX = u; this.textureOffsetY = v; }
  BoxMutator.createFrom = function (model, renderer, box) {
    var quads = box.a4t;
    if (quads == null) return null;
    var q = quads.data, tw = renderer.bdO, th = renderer.bby;
    var texU = jint(Math.fround(q[RIGHT].a4R.data[1].cDP * tw)), texV = jint(Math.fround(q[TOP].a4R.data[1].cDQ * th));
    if (renderer.i$) texV = jint(Math.fround(q[BOTTOM].a4R.data[1].cDQ * th));
    var x = q[1].a4R.data[0].Kj.bh, inflation = Math.min(Math.abs(Math.fround(box.dsk - x)), Math.abs(Math.fround(box.dsl - x)));
    var target = BoxFactory.fromBox(renderer, box);
    target.inflate(inflation, inflation, inflation);
    return new BoxMutator(model, renderer, target, texU, texV);
  };
  BoxMutator.prototype.sliceFromBottom = function (sliceY) {
    var f = this.factory, height = f.max.y - f.min.y;
    if (sliceY > f.min.y && sliceY < f.max.y) {
      var newHeight = sliceY - f.min.y, faces = [null, null, null, null, null, null], sides = [BACK, FRONT, LEFT, RIGHT];
      for (var i = 0; i < sides.length; i++) {
        var face = f.faces[sides[i]], cut = jint(Math.fround(face.vSize * (newHeight / height)));
        faces[sides[i]] = new TextureFace(face.uPos, face.vPos + cut, face.uSize, face.vSize - cut);
        face.vSize = cut;
      }
      faces[TOP] = new TextureFace(f.faces[TOP].uPos, f.faces[TOP].vPos, f.faces[TOP].uSize, f.faces[TOP].vSize);
      faces[BOTTOM] = new TextureFace(f.faces[BOTTOM].uPos, f.faces[BOTTOM].vPos, f.faces[BOTTOM].uSize, f.faces[BOTTOM].vSize);
      var sliced = BoxFactory.fromFaces(f.min.x, sliceY, f.min.z, f.max.x, f.max.y, f.max.z, faces);
      sliced.hideFace(TOP); f.max.y = sliceY; f.hideFace(BOTTOM);
      return sliced;
    }
    return null;
  };

  // ===================== mutators =====================
  function cloneModel(model) {
    var c = Object.create(Object.getPrototypeOf(model));
    for (var k in model) if (Object.prototype.hasOwnProperty.call(model, k) && k.charAt(0) !== "$") c[k] = model[k];
    c.cJ9 = Bq();
    return c;
  }
  var mutatorByModel = new WeakMap();
  function Mutator(bender) { this.bender = bender; this.original = null; this.model = null; this.renderer = null; this.headYaw = 0; this.headPitch = 0; this.limbSwing = 0; this.limbSwingAmount = 0; this.swingProgress = 0; this.swaps = []; }
  var MU = Mutator.prototype;
  MU.shouldModelBeSkipped = function () { return true; };
  MU.fetchFields = function () {};
  MU.mutate = function (renderer) {
    var model = renderer.iK;
    if (model == null || this.shouldModelBeSkipped(model)) return false;
    this.renderer = renderer; this.original = model; this.fetchFields(renderer, model);
    var clone = cloneModel(model);
    this.model = clone;
    this.createParts(clone, 0);
    renderer.iK = clone; mutatorByModel.set(clone, this);
    this.swapLayers(renderer);
    stats.mutations++;
    return true;
  };
  MU.demutate = function () {
    if (!this.renderer) return;
    if (this.renderer.iK === this.model) this.renderer.iK = this.original;
    for (var i = this.swaps.length - 1; i >= 0; i--) this.swaps[i]();
    this.swaps.length = 0; stats.demutations++;
  };
  MU.swapLayers = function (renderer) {
    // LayerCustomHead is rebuilt around the Mo' Bends head (BipedMutator.swapLayer); the armor and held-item
    // layers keep their objects and are redirected by the getModelFromSlot / renderHeldItem hooks.
    if (!this.head) return;
    var layers = list(renderer.cHT), self = this;
    layers.forEach(function (layer) {
      if (layer instanceof A7l) { var old = layer.cH_; layer.cH_ = self.head.native; self.swaps.push(function () { layer.cH_ = old; }); }
    });
  };
  MU.updateModel = function (entity, renderer, pt) {
    var shouldSit = E.isRiding(entity);
    var f = core.GUtil.interpolateRotation(E.prevRenderYawOffset(entity), E.renderYawOffset(entity), pt);
    var f1 = core.GUtil.interpolateRotation(E.prevRotationYawHead(entity), E.rotationYawHead(entity), pt);
    var yaw = f1 - f, ridden = E.ridingEntity(entity);
    if (shouldSit && ridden instanceof Co) {
      f = core.GUtil.interpolateRotation(E.prevRenderYawOffset(ridden), E.renderYawOffset(ridden), pt);
      yaw = f1 - f;
      var f3 = core.MathHelper.wrapDegrees(yaw);
      if (f3 < -85) f3 = -85;
      if (f3 >= 85) f3 = 85;
      f = f1 - f3;
      if (f3 * f3 > 2500) f += f3 * 0.2;
      yaw = f1 - f;
    }
    var pitch = E.prevRotationPitch(entity) + (E.rotationPitch(entity) - E.prevRotationPitch(entity)) * pt, f5 = 0, f6 = 0;
    if (!E.isRiding(entity)) {
      f5 = E.prevLimbSwingAmount(entity) + (E.limbSwingAmount(entity) - E.prevLimbSwingAmount(entity)) * pt;
      f6 = E.limbSwing(entity) - E.limbSwingAmount(entity) * (1 - pt);
      if (E.isChild(entity)) f6 *= 3;
      if (f5 > 1) f5 = 1;
      yaw = f1 - f;
    }
    this.headYaw = yaw; this.headPitch = pitch; this.limbSwing = f6; this.limbSwingAmount = f5;
    this.swingProgress = E.getSwingProgress(entity, pt);
  };
  MU.performAnimations = function (data) {
    data.headYaw.set(core.MathHelper.wrapDegrees(this.headYaw)); data.headPitch.set(core.MathHelper.wrapDegrees(this.headPitch));
    data.limbSwing.set(this.limbSwing); data.limbSwingAmount.set(this.limbSwingAmount); data.swingProgress.set(this.swingProgress);
    data.getController().perform(data);
  };
  MU.getOrMakeData = function (entity) { var b = this.bender; return DB.getOrMake(function (e) { return new b.data(e); }, entity); };
  MU.getData = function (entity) { return DB.get(entity); };
  function subMutator(Parent, proto) { var C = function (bender) { Parent.call(this, bender); }; C.prototype = Object.create(Parent.prototype); C.prototype.constructor = C; for (var k in proto) C.prototype[k] = proto[k]; return C; }

  var BipedMutator = subMutator(Mutator, {
    createParts: function (original, sf) {
      var body = this.body = new ModelPart(original, true, 16, 16, "postOffset").setPostOffset(0, -12, 0).setPosition(0, 12, 0);
      body.addBox(-4, -12, -2, 8, 12, 4, sf); original.k_ = body.native;
      var head = this.head = new ModelPart(original, true, 0, 0).setParent(body).setPosition(0, -12, 0);
      head.addBox(-4, -8, -4, 8, 8, 8, sf); original.lA = head.native;
      var armWidth = 4, armY = -10;
      var leftArm = this.leftArm = new ModelPart(original, true, 40, 16, "extended").setParent(body).setPosition(5, armY, 0).setMirror(true);
      leftArm.developBox(-1, -2, -2, armWidth, 6, 4, sf).inflate(0.01, 0, 0.01).hideFace(BOTTOM).create(); original.f3 = leftArm.native;
      var rightArm = this.rightArm = new ModelPart(original, true, 40, 16, "extended").setParent(body).setPosition(-5, armY, 0);
      rightArm.developBox(-armWidth + 1, -2, -2, armWidth, 6, 4, sf).inflate(0.01, 0, 0.1).hideFace(BOTTOM).create(); original.gM = rightArm.native;
      var leftForeArm = this.leftForeArm = new ModelPart(original, true, 40, 16 + 6, "postOffset").setPostOffset(0, -4, -2).setParent(leftArm).setPosition(0, 4, 2).setMirror(true);
      leftForeArm.developBox(-1, 0, -4, armWidth, 6, 4, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      leftArm.setExtension(leftForeArm);
      var rightForeArm = this.rightForeArm = new ModelPart(original, true, 40, 16 + 6, "postOffset").setPostOffset(0, -4, -2).setParent(rightArm).setPosition(0, 4, 2);
      rightForeArm.developBox(-armWidth + 1, 0, -4, armWidth, 6, 4, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      rightArm.setExtension(rightForeArm);
      var rightLeg = this.rightLeg = new ModelPart(original, true, 0, 16, "extended").setPosition(0, 12, 0);
      rightLeg.addBox(-3.9, 0, -2, 4, 6, 4, sf); original.mD = rightLeg.native;
      var leftLeg = this.leftLeg = new ModelPart(original, true, 0, 16, "extended").setPosition(0, 12, 0).setMirror(true);
      leftLeg.addBox(-0.1, 0, -2, 4, 6, 4, sf); original.nc = leftLeg.native;
      var leftForeLeg = this.leftForeLeg = new ModelPart(original, true, 0, 16 + 6).setParent(leftLeg).setPosition(0, 6, -2).setMirror(true);
      leftForeLeg.developBox(-0.1, 0, 0, 4, 6, 4, sf).inflate(0.01, 0, 0.01).offsetTextureQuad(BOTTOM, 0, -6).create();
      leftLeg.setExtension(leftForeLeg);
      var rightForeLeg = this.rightForeLeg = new ModelPart(original, true, 0, 16 + 6).setParent(rightLeg).setPosition(0, 6, -2);
      rightForeLeg.developBox(-3.9, 0, 0, 4, 6, 4, sf).inflate(0.01, 0, 0.01).offsetTextureQuad(BOTTOM, 0, -6).create();
      rightLeg.setExtension(rightForeLeg);
      var headwear = this.headwear = new ModelPart(original, true, 32, 0).setParent(head);
      headwear.addBox(-4, -8, -4, 8, 8, 8, sf + 0.5); original.Ea = headwear.native;
      this.bipedPivots();
      return true;
    },
    bipedPivots: function () {
      this.body.pivot(0, 0, 0); this.head.pivot(0, 0, 0); this.headwear.pivot(0, 0, 0);
      this.rightArm.pivot(-5, 2, 0); this.leftArm.pivot(5, 2, 0); this.rightForeArm.pivot(-5, 6, 2); this.leftForeArm.pivot(5, 6, 2);
      this.rightLeg.pivot(-1.9, 12, 0); this.leftLeg.pivot(1.9, 12, 0); this.rightForeLeg.pivot(-1.9, 18, -2); this.leftForeLeg.pivot(1.9, 18, -2);
    },
    syncUpWithData: function (data) {
      this.head.syncUp(data.head); this.body.syncUp(data.body); this.leftArm.syncUp(data.leftArm); this.rightArm.syncUp(data.rightArm);
      this.leftLeg.syncUp(data.leftLeg); this.rightLeg.syncUp(data.rightLeg); this.leftForeArm.syncUp(data.leftForeArm); this.rightForeArm.syncUp(data.rightForeArm);
      this.leftForeLeg.syncUp(data.leftForeLeg); this.rightForeLeg.syncUp(data.rightForeLeg);
    }
  });
  var ZombieMutator = subMutator(BipedMutator, { shouldModelBeSkipped: function (model) { return !(model instanceof C4z); } });
  var PigZombieMutator = subMutator(BipedMutator, { shouldModelBeSkipped: function (model) { return !(model instanceof C4z); } });
  var SkeletonMutator = subMutator(BipedMutator, {
    shouldModelBeSkipped: function (model) { return !(model instanceof Gky); },
    fetchFields: function (renderer, model) { this.boneLimbs = jint(Bm(model.gM.a6Y, 0).dsk) === -1; },
    createParts: function (original, sf) {
      BipedMutator.prototype.createParts.call(this, original, sf);
      if (this.boneLimbs) {
        var body = this.body;
        var rightArm = this.rightArm = new ModelPart(original, true, 40, 16, "extended").setParent(body).setPosition(-5, 2, 0);
        rightArm.developBox(-1, -2, -1, 2, 6, 2, sf).inflate(0.01, 0, 0.01).hideFace(BOTTOM).create(); original.gM = rightArm.native;
        var leftArm = this.leftArm = new ModelPart(original, true, 40, 16, "extended").setParent(body).setPosition(5, 2, 0);
        leftArm.developBox(-1, -2, -1, 2, 6, 2, sf).inflate(0.01, 0, 0.01).hideFace(BOTTOM).create(); original.f3 = leftArm.native;
        var rightForeArm = this.rightForeArm = new ModelPart(original, true, 40, 16 + 6, "postOffset").setPostOffset(0, -4, -1).setPosition(0, 4, 1).setParent(rightArm);
        rightForeArm.developBox(-1, 0, -2, 2, 6, 2, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
        rightArm.setExtension(rightForeArm);
        var leftForeArm = this.leftForeArm = new ModelPart(original, true, 40, 16 + 6, "postOffset").setPostOffset(0, -4, -1).setPosition(0, 4, 1).setParent(leftArm);
        leftForeArm.developBox(-1, 0, -2, 2, 6, 2, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
        leftArm.setExtension(leftForeArm);
        var rightLeg = this.rightLeg = new ModelPart(original, true, 0, 16, "extended").setPosition(-2, 12, 0);
        rightLeg.addBox(-1, 0, -1, 2, 6, 2, sf); original.mD = rightLeg.native;
        var leftLeg = this.leftLeg = new ModelPart(original, true, 0, 16, "extended").setPosition(2, 12, 0).setMirror(true);
        leftLeg.addBox(-1, 0, -1, 2, 6, 2, sf); original.nc = leftLeg.native;
        var leftForeLeg = this.leftForeLeg = new ModelPart(original, true, 0, 16 + 6).setParent(leftLeg).setPosition(0, 6, -2).setMirror(true);
        leftForeLeg.developBox(-1, 0, 0, 2, 6, 2, sf).inflate(0.01, 0, 0.01).offsetTextureQuad(BOTTOM, 0, -6).create();
        leftLeg.setExtension(leftForeLeg);
        var rightForeLeg = this.rightForeLeg = new ModelPart(original, true, 0, 16 + 6).setParent(rightLeg).setPosition(0, 6, -2);
        rightForeLeg.developBox(-1, 0, 0, 2, 6, 2, sf).inflate(0.01, 0, 0.01).offsetTextureQuad(BOTTOM, 0, -6).create();
        rightLeg.setExtension(rightForeLeg);
        pruneUnreachable(original, this);
        this.bipedPivots();
      }
      return true;
    }
  });
  // The replaced first-pass limbs stay registered in the original Mo' Bends (they were never drawn). Dropping them
  // from the clone's box list keeps arrows stuck in a mob on parts that actually move, and keeps the dismemberment
  // system from choosing a part nobody sees.
  function pruneUnreachable(model, mut) {
    var keep = new Set(), l = model.cJ9;
    ["body", "head", "headwear", "leftArm", "rightArm", "leftForeArm", "rightForeArm", "leftLeg", "rightLeg", "leftForeLeg", "rightForeLeg",
      "bodywear", "leftArmwear", "rightArmwear", "leftForeArmwear", "rightForeArmwear", "leftLegwear", "rightLegwear", "leftForeLegwear", "rightForeLegwear"]
      .forEach(function (k) { if (mut[k]) keep.add(mut[k].native); });
    for (var i = l.g - 1; i >= 0; i--) if (!keep.has(l.qN.data[i])) HB(l, i);
  }
  var PlayerMutator = subMutator(BipedMutator, {
    shouldModelBeSkipped: function (model) { return !(model instanceof BVS); },
    fetchFields: function (renderer) { this.smallArms = !!renderer.d9q; },
    createParts: function (original, sf) {
      BipedMutator.prototype.createParts.call(this, original, sf);
      var armWidth = this.smallArms ? 3 : 4, armY = this.smallArms ? -9.5 : -10, body = this.body;
      var leftArm = this.leftArm = new ModelPart(original, true, 32, 48, "extended");
      leftArm.setParent(body).setPosition(5, armY, 0).developBox(-1, -2, -2, armWidth, 6, 4, sf).inflate(0.01, 0, 0.01).hideFace(BOTTOM).create();
      original.f3 = leftArm.native;
      var rightArm = this.rightArm = new ModelPart(original, true, 40, 16, "extended");
      rightArm.setParent(body).setPosition(-5, armY, 0).developBox(-armWidth + 1, -2, -2, armWidth, 6, 4, sf).inflate(0.01, 0, 0.01).hideFace(BOTTOM).create();
      original.gM = rightArm.native;
      var leftForeArm = this.leftForeArm = new ModelPart(original, true, 32, 48 + 6, "postOffset").setPostOffset(0, -4, -2);
      leftForeArm.setPosition(0, 4, 2).setParent(leftArm).developBox(-1, 0, -4, armWidth, 6, 4, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      leftArm.setExtension(leftForeArm);
      var rightForeArm = this.rightForeArm = new ModelPart(original, true, 40, 16 + 6, "postOffset").setPostOffset(0, -4, -2);
      rightForeArm.setPosition(0, 4, 2).setParent(rightArm).developBox(-armWidth + 1, 0, -4, armWidth, 6, 4, sf).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      rightArm.setExtension(rightForeArm);
      var leftLeg = this.leftLeg = new ModelPart(original, true, 16, 48, "extended").setPosition(0, 12, 0);
      leftLeg.addBox(-0.1, 0, -2, 4, 6, 4, sf); original.nc = leftLeg.native;
      leftLeg.setExtension(this.leftForeLeg);
      var bodywear = this.bodywear = new ModelPart(original, true, 16, 32);
      bodywear.setParent(body); bodywear.addBox(-4, -12, -2, 8, 12, 4, sf + 0.25); original.bq3 = bodywear.native;
      var wearHeight = (6 + 2 * sf + 0.5) - 0.25;
      var leftArmwear = this.leftArmwear = new ModelPart(original, true, 48, 48);
      leftArmwear.setParent(leftArm).developBox(-1, -2, -2, armWidth, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.0025, 0, 0.0025).hideFace(BOTTOM).create();
      original.a0W = leftArmwear.native;
      var rightArmwear = this.rightArmwear = new ModelPart(original, true, 40, 32);
      rightArmwear.setParent(rightArm).developBox(-armWidth + 1, -2, -2, armWidth, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.0025, 0, 0.0025).hideFace(BOTTOM).create();
      original.Wo = rightArmwear.native;
      var leftForeArmwear = this.leftForeArmwear = new ModelPart(original, true, 48, 48 + 6);
      leftForeArmwear.developBox(-1, 0, -4, armWidth, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.005, 0, 0.005).offset(0, 0.25, 0).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      leftForeArm.addChild(leftForeArmwear);
      var rightForeArmwear = this.rightForeArmwear = new ModelPart(original, true, 40, 32 + 6);
      rightForeArmwear.developBox(-armWidth + 1, 0, -4, armWidth, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.005, 0, 0.005).offset(0, 0.25, 0).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      rightForeArm.addChild(rightForeArmwear);
      var leftLegwear = this.leftLegwear = new ModelPart(original, true, 0, 48);
      leftLegwear.setParent(leftLeg).developBox(-0.1, 0, -2, 4, 6, 4, sf + 0.25).setHeight(wearHeight).hideFace(BOTTOM).create();
      original.a4$ = leftLegwear.native;
      var rightLegwear = this.rightLegwear = new ModelPart(original, true, 0, 32);
      rightLegwear.setParent(this.rightLeg).developBox(-3.9, 0, -2, 4, 6, 4, sf + 0.25).setHeight(wearHeight).hideFace(BOTTOM).create();
      original.bqQ = rightLegwear.native;
      var leftForeLegwear = this.leftForeLegwear = new ModelPart(original, true, 0, 48 + 6);
      leftForeLegwear.developBox(-0.1, 0, 0, 4, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.005, 0, 0.005).offset(0, 0.25, 0).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      this.leftForeLeg.addChild(leftForeLegwear);
      var rightForeLegwear = this.rightForeLegwear = new ModelPart(original, true, 0, 32 + 6);
      rightForeLegwear.developBox(-3.9, 0, 0, 4, 6, 4, sf + 0.25).setHeight(wearHeight).inflate(0.005, 0, 0.005).offset(0, 0.25, 0).hideFace(TOP).offsetTextureQuad(BOTTOM, 0, -6).create();
      this.rightForeLeg.addChild(rightForeLegwear);
      pruneUnreachable(original, this);
      this.bipedPivots();
      return true;
    },
    performAnimations: function (data) {
      this.leftForeArmwear.setVisible(this.leftArmwear.isShowing()); this.rightForeArmwear.setVisible(this.rightArmwear.isShowing());
      this.leftForeLegwear.setVisible(this.leftLegwear.isShowing()); this.rightForeLegwear.setVisible(this.rightLegwear.isShowing());
      MU.performAnimations.call(this, data);
    },
    poseForFirstPersonView: function () { this.body.rotation.identity(); this.rightArm.rotation.identity(); this.rightForeArm.rotation.identity(); this.leftArm.rotation.identity(); this.leftForeArm.rotation.identity(); }
  });
  var SpiderMutator = subMutator(Mutator, {
    shouldModelBeSkipped: function (model) { return !(model instanceof F$X); },
    createParts: function (original) {
      var legLength = 12, foreLegLength = 12;
      this.spiderHead = new ModelPart(original, true, 32, 4); this.spiderHead.setPosition(0, 15, -3); this.spiderHead.addBox(-4, -4, -8, 8, 8, 8, 0); original.coY = this.spiderHead.native;
      this.spiderNeck = new ModelPart(original, true, 0, 0); this.spiderNeck.setPosition(0, 15, 0); this.spiderNeck.addBox(-3, -3, -3, 6, 6, 6, 0); original.cSt = this.spiderNeck.native;
      this.spiderBody = new ModelPart(original, true, 0, 12); this.spiderBody.setPosition(0, 15, 9); this.spiderBody.addBox(-5, -4, -6, 10, 8, 12, 0); original.cx0 = this.spiderBody.native;
      this.spiderHead.pivot(0, 15, -3); this.spiderNeck.pivot(0, 15, 0); this.spiderBody.pivot(0, 15, 9);
      this.upper = []; this.lower = [];
      var fields = ["bLf", "bLg", "bLd", "bLe", "bLb", "bLc", "bLh", "bLi"];
      for (var i = 0; i < 8; i++) {
        var odd = i % 2 === 1, z = 2 - (i / 2 | 0);
        var up = this.upper[i] = new ModelPart(original, true, odd ? 18 : 26, 0);
        up.setPosition(odd ? 4 : -4, 15, z); up.developBox(odd ? -1 : (-legLength + 1), -1, -1, 8, 2, 2, 0).setWidth(legLength).create();
        var low = this.lower[i] = new ModelPart(original, true, odd ? 26 : 18, 0);
        low.setPosition(odd ? foreLegLength : -foreLegLength, 0, 0);
        low.developBox(odd ? 0 : -foreLegLength, 0, -1, 8, 2, 2, 0).offset(0, 0, 0.005).resize(foreLegLength, 1.99, 1.99).create();
        up.addChild(low); up.pivot(odd ? 4 : -4, 15, z); low.pivot(odd ? 16 : -16, 15, z);
        original[fields[i]] = up.native;
      }
      return true;
    },
    syncUpWithData: function (data) {
      this.spiderHead.syncUp(data.spiderHead); this.spiderNeck.syncUp(data.spiderNeck); this.spiderBody.syncUp(data.spiderBody);
      for (var i = 0; i < 8; i++) { this.upper[i].syncUp(data.limbs[i].upperPart); this.lower[i].syncUp(data.limbs[i].lowerPart); }
    }
  });
  var SquidMutator = subMutator(Mutator, {
    shouldModelBeSkipped: function (model) { return !(model instanceof BIY); },
    createParts: function (original) {
      var S = core.TENTACLE_SECTIONS, H = core.SECTION_HEIGHT;
      this.squidBody = new ModelPart(original, true, 0, 0); this.squidBody.setPosition(0, 8, 0); this.squidBody.addBox(-6, -8, -6, 12, 16, 12); this.squidBody.pivot(0, 8, 0);
      original.cEB = this.squidBody.native;
      var roots = G(M2, 8); original.bn9 = roots;
      this.tentacles = [];
      for (var i = 0; i < 8; i++) {
        var row = [], d0 = i * PI * 2 / 8, x = Math.fround(Math.cos(d0)) * 4, z = Math.fround(Math.sin(d0)) * 4;
        row[0] = new ModelPart(original, true, 48, 0); roots.data[i] = row[0].native;
        row[0].setPosition(x, 16, z); row[0].addBox(-1, 0, 0, 2, H, 2); row[0].rotation.rotateY(i * -360 / 8 + 90); row[0].pivot(x, 16, z);
        for (var j = 1; j < S; j++) {
          row[j] = new ModelPart(original, true, 48, 0); row[j].setPosition(0, H, 0); row[j].addBox(-1, 0, -2, 2, H, 2);
          row[j - 1].addChild(row[j]);
        }
        this.tentacles.push(row);
      }
      return true;
    },
    syncUpWithData: function (data) {
      this.squidBody.syncUp(data.squidBody);
      for (var i = 0; i < 8; i++) for (var j = 0; j < core.TENTACLE_SECTIONS; j++) this.tentacles[i][j].syncUp(data.squidTentacles[i][j]);
    }
  });
  var WolfMutator = subMutator(Mutator, {
    shouldModelBeSkipped: function (model) { return !(model instanceof C2y); },
    createParts: function (original, sf) {
      var body = this.wolfBody = new ModelPart(original, true, 18, 14).setPosition(0, 13, 8);
      body.developBox(-3, -3, -8, 6, 6, 9, sf).offsetTextureQuad(TOP, 9, 6).rotateTextureQuad(TOP, "HALF_TURN").offsetTextureQuad(BACK, -12, -9)
        .rotateTextureQuad(BOTTOM, "HALF_TURN").offsetTextureQuad(BOTTOM, -8, 6).rotateTextureQuad(LEFT, "CLOCKWISE").offsetTextureQuad(LEFT, -3, -3)
        .rotateTextureQuad(RIGHT, "COUNTER_CLOCKWISE").offsetTextureQuad(RIGHT, 0, -3).create();
      original.Y8 = body.native;
      var head = this.wolfHeadMain = new ModelPart(original, true, 0, 0).setParent(body).setPosition(0, 0, -7);
      head.addBox(-3, -3, -4, 6, 6, 4, sf); original.XW = head.native;
      var mane = this.wolfMane = new ModelPart(original, true, 21, 0).setParent(body).setPosition(0, 0, -7);
      mane.developBox(-4, -3.5, -2, 8, 7, 6, sf).offsetTextureQuad(TOP, 1, 7).rotateTextureQuad(TOP, "HALF_TURN").offsetTextureQuad(BACK, -5, -6)
        .offsetTextureQuad(BOTTOM, 8, 7).rotateTextureQuad(BOTTOM, "HALF_TURN").rotateTextureQuad(LEFT, "CLOCKWISE").offsetTextureQuad(LEFT, -14, 1)
        .rotateTextureQuad(RIGHT, "COUNTER_CLOCKWISE").offsetTextureQuad(RIGHT, 15, 1).offsetTextureQuad(FRONT, 1, -6).create();
      original.a79 = mane.native;
      var legPos = [[-2.5, 16, 7], [0.5, 16, 7], [-2.5, 0, -4], [0.5, 0, -4]], legFields = ["bel", "bek", "bqM", "bqL"], self = this;
      this.legs = []; this.foreLegs = [];
      legPos.forEach(function (p, i) {
        var leg = new ModelPart(original, true, 0, 18, "extended").setParent(body).setPosition(p[0], p[1], p[2]);
        leg.addBox(-1, 0, -1, 2, 4, 2, sf); original[legFields[i]] = leg.native; self.legs.push(leg);
      });
      var tail = this.wolfTail = new ModelPart(original, true, 9, 18).setParent(body).setPosition(-1, 0, 8);
      tail.addBox(-1, 0, -2, 2, 8, 2, sf); original.Y9 = tail.native;
      var nose = this.nose = new ModelPart(original, true, 0, 10).setPosition(0, 1, -4);
      nose.developBox(-1.5, -1, -4, 3, 2, 4, 0).hideFace(BOTTOM).create(); head.addChild(nose);
      var mouth = this.mouth = new ModelPart(original, true, 0, 12).setPosition(0, 2, -4);
      mouth.developBox(-1.5, 0, -4, 3, 1, 4, 0).hideFace(TOP).create(); head.addChild(mouth);
      var leftEar = this.leftEar = new ModelPart(original, true, 16, 14).setPosition(0, 1, -4); leftEar.addBox(-1, -2, -1, 2, 2, 1, 0); head.addChild(leftEar);
      var rightEar = this.rightEar = new ModelPart(original, true, 16, 14).setPosition(0, 1, -4); rightEar.addBox(-1, -2, -1, 2, 2, 1, 0); head.addChild(rightEar);
      [[-4, -1, -1, 0, 0], [-4, -1, -1, 0, 0], [-4, 1, -1, 0, -2], [-4, 1, -1, 0, -2]].forEach(function (p, i) {
        var fore = new ModelPart(original, true, 0, 18).setParent(self.legs[i]).setPosition(0, p[0], p[1]);
        fore.addBox(-1, p[3], p[4], 2, 4, 2, sf); self.legs[i].setExtension(fore); self.foreLegs.push(fore);
      });
      body.pivot(0, 13, 8); head.pivot(-1, 13.5, -7); mane.pivot(-1, 14, 2); tail.pivot(-1, 12, 8);
      legPos.forEach(function (p, i) { self.legs[i].pivot(p[0], 16, p[2] === 7 ? 7 : -4); self.foreLegs[i].pivot(p[0], 20, p[2] === 7 ? 7 : -4); });
      return true;
    },
    swapLayers: function (renderer) {
      var layers = renderer.cHT, layer = wolfMiscLayer(this);
      Y(layers, layer);
      this.swaps.push(function () { ECM(layers, layer); });
    },
    syncUpWithData: function (data) {
      this.wolfHeadMain.syncUp(data.head); this.wolfBody.syncUp(data.body);
      for (var i = 0; i < 4; i++) { this.legs[i].syncUp(data["leg" + (i + 1)]); this.foreLegs[i].syncUp(data["foreLeg" + (i + 1)]); }
      this.wolfTail.syncUp(data.tail); this.wolfMane.syncUp(data.mane); this.nose.syncUp(data.nose); this.mouth.syncUp(data.mouth);
      this.leftEar.syncUp(data.leftEar); this.rightEar.syncUp(data.rightEar);
    }
  });

  // ===================== benders (DefaultAddon) =====================
  function Bender(key, cls, data, mutator, renderer) { this.key = key; this.entityClass = cls; this.data = data; this.Mutator = mutator; this.renderer = renderer; this.animate = true; this.mutators = new Map(); this.skipped = new WeakSet(); }
  var benders = null, benderCache = new WeakMap();
  function registry() {
    if (benders) return benders;
    var biped = new core.MutatedRenderer("biped"), mob = new core.MutatedRenderer("mob");
    benders = [
      new Bender("mobends-player", Vf, core.PlayerData, PlayerMutator, new core.MutatedRenderer("player")),
      new Bender("mobends-zombie", Iw, core.ZombieData, ZombieMutator, biped),
      new Bender("mobends-skeleton", OF, core.SkeletonData, SkeletonMutator, biped),
      new Bender("mobends-zombie_pigman", PP, core.PigZombieData, PigZombieMutator, biped),
      new Bender("mobends-spider", SN, core.SpiderData, SpiderMutator, mob),
      new Bender("mobends-squid", ZL, core.SquidData, SquidMutator, mob),
      new Bender("mobends-wolf", KF, core.WolfData, WolfMutator, mob)
    ];
    return benders;
  }
  function getForEntity(entity) {
    if (benderCache.has(entity)) return benderCache.get(entity);
    var all = registry(), found = null, i;
    for (i = 0; i < all.length && !found; i++) if (entity.constructor === all[i].entityClass) found = all[i];
    for (i = 0; i < all.length && !found; i++) if (entity instanceof all[i].entityClass) found = all[i];
    benderCache.set(entity, found);
    return found;
  }
  core.BenderRegistry.clearCache = function (entity) { if (entity) benderCache.delete(entity); };
  function restoreAll() {
    (benders || []).forEach(function (b) { b.mutators.forEach(function (m) { m.demutate(); }); b.mutators.clear(); });
    armorWrappers.forEach(function (w) { w.deapply(); });
    DB.refresh(); benderCache = new WeakMap();
  }

  // ===================== the armor wrapper (ArmorWrapper / HumanoidPartWrapper / HumanoidLimbWrapper) =====================
  var armorWrappers = new Map();
  function HumanoidPartWrapper(vanillaModel, vanillaPart, field, select) {
    this.field = field; this.select = select; this.vanillaPart = vanillaPart; this.container = new PartContainer(vanillaModel, vanillaPart);
  }
  HumanoidPartWrapper.prototype.syncUp = function (data) { this.container.syncUp(this.select(data)); };
  HumanoidPartWrapper.prototype.apply = function (model) { model[this.field] = this.container.native; this.container.native.cIT = this.vanillaPart.cIT; this.container.native.eT = this.vanillaPart.eT; };
  HumanoidPartWrapper.prototype.deapply = function (model) { model[this.field] = this.vanillaPart; this.vanillaPart.cIT = this.container.native.cIT; this.vanillaPart.eT = this.container.native.eT; };
  HumanoidPartWrapper.prototype.setParent = function (p) { this.container.setParent(p); return this; };
  HumanoidPartWrapper.prototype.offsetInner = function (x, y, z) { this.container.setInnerOffset(x, y, z); return this; };
  function HumanoidLimbWrapper(vanillaModel, vanillaPart, field, select, selectLower, cutPlane, inflation) {
    this.vanillaPart = vanillaPart; this.field = field; this.select = select; this.selectLower = selectLower; this.inflation = inflation;
    this.upperPart = new ModelPart(vanillaModel, false, 0, 0); this.upperPartAnchor = new ModelPart(vanillaModel, false, 0, 0);
    this.lowerPart = new ModelPart(vanillaModel, false, 0, 0); this.lowerPartAnchor = new ModelPart(vanillaModel, false, 0, 0);
    this.upperPart.addChild(this.upperPartAnchor); this.upperPart.addChild(this.lowerPart); this.lowerPart.addChild(this.lowerPartAnchor);
    var m = vanillaPart.i$; this.upperPart.native.i$ = this.upperPartAnchor.native.i$ = this.lowerPart.native.i$ = this.lowerPartAnchor.native.i$ = m;
    this.sliceAppendage(vanillaModel, vanillaPart, cutPlane);
  }
  var HLW = HumanoidLimbWrapper.prototype;
  HLW.sliceAppendage = function (vanillaModel, vanillaPart, cutPlane) {
    var self = this;
    list(vanillaPart.a6Y).forEach(function (box) {
      var mutator = BoxMutator.createFrom(vanillaModel, vanillaPart, box);
      if (mutator == null) return;
      if (mutator.factory.min.y < cutPlane) {
        var lowerFactory = mutator.sliceFromBottom(cutPlane);
        self.upperPartAnchor.addMutatedBox(mutator.factory.inflate(self.inflation, 0, self.inflation).createFor(self.upperPart));
        if (lowerFactory != null) {
          var lowerInflation = self.inflation + 0.001;
          self.lowerPartAnchor.addMutatedBox(lowerFactory.inflate(lowerInflation, 0, lowerInflation).createFor(self.upperPart));
        }
      } else self.lowerPartAnchor.addVanillaBox(box);
    });
    list(vanillaPart.OS).forEach(function (child) {
      if (child == null) return;
      HV(child.bs < cutPlane ? self.upperPartAnchor.native : self.lowerPartAnchor.native, child);
    });
  };
  HLW.syncUp = function (data) { this.upperPart.syncUp(this.select(data)); if (this.lowerPart != null) this.lowerPart.syncUp(this.selectLower(data)); };
  HLW.apply = function (model) { model[this.field] = this.upperPart.native; this.upperPart.native.cIT = this.vanillaPart.cIT; this.upperPart.native.eT = this.vanillaPart.eT; };
  HLW.deapply = function (model) { model[this.field] = this.vanillaPart; this.vanillaPart.cIT = this.upperPart.native.cIT; this.vanillaPart.eT = this.upperPart.native.eT; };
  HLW.setParent = function (p) { this.upperPart.setParent(p); return this; };
  HLW.offsetInner = function (x, y, z) { this.upperPartAnchor.setPosition(x, y, z); return this; };
  HLW.offsetLower = function (x, y, z) { this.lowerPartAnchor.setPosition(x, y, z); return this; };
  function ArmorWrapper(original) {
    this.original = original; this.applied = false; this.bodyTransform = new core.ModelPartTransform();
    var bt = this.bodyTransform;
    this.partWrappers = [
      new HumanoidPartWrapper(original, original.k_, "k_", function (d) { return d.body; }).offsetInner(0, -12, 0),
      new HumanoidPartWrapper(original, original.lA, "lA", function (d) { return d.head; }).setParent(bt),
      new HumanoidPartWrapper(original, original.Ea, "Ea", function (d) { return d.head; }).setParent(bt),
      new HumanoidLimbWrapper(original, original.f3, "f3", function (d) { return d.leftArm; }, function (d) { return d.leftForeArm; }, 4, 0.001).offsetLower(0, -4, -2).setParent(bt),
      new HumanoidLimbWrapper(original, original.gM, "gM", function (d) { return d.rightArm; }, function (d) { return d.rightForeArm; }, 4, 0.001).offsetLower(0, -4, -2).setParent(bt),
      new HumanoidLimbWrapper(original, original.nc, "nc", function (d) { return d.leftLeg; }, function (d) { return d.leftForeLeg; }, 6, 0).offsetLower(1.9, -6, 2).offsetInner(1.9, 0, 0),
      new HumanoidLimbWrapper(original, original.mD, "mD", function (d) { return d.rightLeg; }, function (d) { return d.rightForeLeg; }, 6, 0).offsetLower(-1.9, -6, 2).offsetInner(-1.9, 0, 0)
    ];
  }
  ArmorWrapper.prototype.prepare = function (data) {
    this.bodyTransform.syncUp(data.body);
    for (var i = 0; i < this.partWrappers.length; i++) this.partWrappers[i].syncUp(data);
    if (!this.applied) { for (var j = 0; j < this.partWrappers.length; j++) this.partWrappers[j].apply(this.original); this.applied = true; }
  };
  ArmorWrapper.prototype.deapply = function () {
    if (!this.applied) return;
    for (var j = 0; j < this.partWrappers.length; j++) this.partWrappers[j].deapply(this.original);
    this.applied = false;
  };

  // ===================== the render stack =====================
  var stack = [];
  function pre(entity, renderer, pt, renderManager) {
    stack.push(null);
    if (failed || !(entity instanceof Co) || !renderer || renderer.iK === undefined) return;
    if (renderManager) lastRenderManager = renderManager;
    clinits();
    var bender = getForEntity(entity);
    if (!bender) return;
    var depth = matrixDepth();
    GL.push();
    var top = stack.length - 1;
    stack[top] = { entity: entity, renderer: renderer, data: null, depth: depth };
    if (enabled && bender.animate) {
      if (bender.skipped.has(renderer)) return;
      var mutator = bender.mutators.get(renderer);
      if (!mutator) {
        mutator = new bender.Mutator(bender);
        if (!mutator.mutate(renderer)) { bender.skipped.add(renderer); return; }
        bender.mutators.set(renderer, mutator);
      }
      currentRenderer = renderer;
      mutator.updateModel(entity, renderer, pt);
      var data = mutator.getOrMakeData(entity);
      mutator.lastData = data; mutator.lastEntity = entity;
      mutator.performAnimations(data, renderer, pt);
      mutator.syncUpWithData(data);
      bender.renderer.beforeRender(data, entity, pt);
      stack[top].data = data;
      stats.entities++;
    } else {
      var m = bender.mutators.get(renderer);
      if (m) { m.demutate(); bender.mutators.delete(renderer); }
    }
  }
  function post() {
    var frame = stack.pop();
    if (frame) { GL.pop(); unwindMatrix(frame.depth); }
  }
  function current() { for (var i = stack.length - 1; i >= 0; i--) if (stack[i]) return stack[i]; return null; }
  function activeMutator(renderer) {
    if (failed || !enabled || !renderer) return null;
    var model = renderer.iK, m = model && mutatorByModel.get(model);
    return m && m.renderer === renderer ? m : null;
  }

  // ===================== layers =====================
  // LayerCustomHeldItem.renderHeldItem (Forge's order: the sneak offset before translateToHand)
  function heldItem(layer, entity, stack, transform, side) {
    var renderer = layer.bsE, m = activeMutator(renderer);
    if (!m) return false;
    var data = DB.get(entity);
    if (!data || !data.isBiped) return false;
    if (CCH(stack)) return true;
    G9();
    GL.push();
    try {
      if (entity.q1()) GL.translate(0, 0.2, 0);
      renderer.dRJ().cD2(SCALE, side);
      var rot = side === Kua ? data.renderRightItemRotation : data.renderLeftItemRotation;
      GL.translate(0, 8 * SCALE, 0); core.GlHelper.rotate(rot.getSmooth()); GL.translate(0, -8 * SCALE, 0);
      GL.rotate(-90, 1, 0, 0); GL.rotate(180, 0, 1, 0);
      var left = side === Kvp;
      GL.translate((left ? -1 : 1) / 16, 0.125, -0.625);
      Ch0(E32().a66, entity, stack, transform, left ? 1 : 0);
    } finally { GL.pop(); }
    stats.heldItems++;
    return true;
  }
  // BendsCapeRenderer: 16 hinged slabs, each a real display list built by the engine's ModelRenderer compiler
  var capeSlabs = null, capeAngles = new Float32Array(16);
  function capeSlabList() {
    if (capeSlabs) return capeSlabs;
    capeSlabs = [];
    var holder = new OB(); Gs(holder);
    for (var i = 0; i < 16; i++) {
      var texV = i, slab = new M2(); slab.bdO = 64; slab.bby = 32; slab.eT = 1; slab.a6Y = Bq(); slab.ddy = holder;
      var box = new DZG(); Ehl(box, slab, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      var x1 = -5, y1 = 0, z1 = 0, x2 = 5, y2 = 1, z2 = 1, tu = 0;
      var v7 = AMs(x1, y1, z1, 0, 0), v = AMs(x2, y1, z1, 0, 8), v1 = AMs(x2, y2, z1, 8, 8), v2 = AMs(x1, y2, z1, 8, 0);
      var v3 = AMs(x1, y1, z2, 0, 0), v4 = AMs(x2, y1, z2, 0, 8), v5 = AMs(x2, y2, z2, 8, 8), v6 = AMs(x1, y2, z2, 8, 0);
      var D = 1, Wd = 10, L = 1;
      box.a4t = T(Bgq, [
        A5Y(T(Xb, [v4, v, v1, v5]), tu + D + Wd, texV + D, tu + D + Wd + D, texV + D + L, 64, 32),
        A5Y(T(Xb, [v7, v3, v6, v2]), tu, texV + D, tu + D, texV + D + L, 64, 32),
        A5Y(T(Xb, [v4, v3, v7, v]), tu + D, texV, tu + D + Wd, texV + D, 64, 32),
        A5Y(T(Xb, [v1, v2, v6, v5]), tu + D + Wd, texV + D, tu + D + Wd + Wd, texV, 64, 32),
        A5Y(T(Xb, [v, v7, v2, v1]), tu + D, texV + D, tu + D + Wd, texV + D + L, 64, 32),
        A5Y(T(Xb, [v3, v4, v5, v6]), tu + D + Wd + D, texV + D, tu + D + Wd + D + Wd, texV + D + L, 64, 32)]);
      Y(slab.a6Y, box);
      capeSlabs.push(slab);
    }
    return capeSlabs;
  }
  function renderCape(scale) {
    var slabs = capeSlabList(), n = 0;
    for (var i = 0; i < slabs.length; i++) {
      var slab = slabs[i], angle = capeAngles[i], hinge = angle < 0 ? 1 : 0;
      if (!slab.clh) F$t(slab, scale);
      GL.push(); n++;
      GL.translate(0, (i === 0 ? 0 : 1) * scale, hinge * scale);
      GL.rotate(angle, 1, 0, 0);
      GL.translate(0, 0, -hinge * scale);
      Dle(slab.bWg);
    }
    while (n-- > 0) GL.pop();
  }
  function cape(layer, player, ls, lsa, pt, age, yaw, pitch, scale) {
    var renderer = layer.cXQ, m = activeMutator(renderer);
    if (!m) return false;
    var data = DB.get(player);
    if (!data || !data.isPlayer) return false;
    AKp(); Dt(); By();
    if (Gyc(player) && !DfJ(player) && Clb(player, LqA) && DQF(player) !== null) {
      if (C52(player.yI(Kuf)) !== Ktv) {
        CFh(1, 1, 1, 1); FTd(renderer, DQF(player));
        GL.push();
        try {
          if (player.q1()) GL.translate(0, 4 * scale, 0);
          data.body.applyLocalTransform(SCALE);
          GL.translate(0, -12 * scale, 2.2 * scale);
          data.cape.applyLocalTransform(SCALE);
          GL.rotate(180, 0, 1, 0);
          core.capeSlabAngles(data, capeAngles);
          renderCape(SCALE); stats.capes++;
        } finally { GL.pop(); }
      }
    }
    return true;
  }
  // LayerCustomElytra: the vanilla elytra model drawn on Mo' Bends' animated body
  function elytra(layer, player, ls, lsa, pt, age, yaw, pitch, scale) {
    var renderer = layer.bcL, m = activeMutator(renderer);
    if (!m) return false;
    var data = DB.get(player);
    if (!data || !data.isPlayer) return false;
    Dt(); By(); AKp();
    var stack = player.yI(Kuf);
    if (C52(stack) !== Ktv) return true;
    CFh(1, 1, 1, 1); CyM(); Fb_(1, 0);
    if (player instanceof Vf && FmP(player) && Dv8(player) !== null) FTd(renderer, Dv8(player));
    else if (player instanceof Vf && Gyc(player) && DQF(player) !== null && Clb(player, LqA)) FTd(renderer, DQF(player));
    else { Fxy(); FTd(renderer, LvD); }
    GL.push();
    try {
      data.body.applyCharacterTransform(SCALE);
      GL.translate(0, -12 * scale, 0);
      var model = layer.ckF;
      DqH(model, ls, lsa, age, yaw, pitch, scale, player);
      Djv(model, player, ls, lsa, age, yaw, pitch, scale);
      if (EmX(stack)) Fzs(renderer, player, model, ls, lsa, pt, age, yaw, pitch, scale);
      CTP(); stats.elytras++;
    } finally { GL.pop(); }
    return true;
  }
  // LayerWolfMisc: mouth interior and tongue, textured from Mo' Bends' wolf_misc.png (8x8)
  var wolfTexture = null, wolfMeshes = null;
  function wolfMiscTexture() {
    if (wolfTexture) return wolfTexture;
    var px = typeof JasprMoBendsAssets !== "undefined" ? JasprMoBendsAssets.wolfMisc : null;
    var t = new YW(); Fl9(t, 8, 8);
    for (var i = 0; i < 64; i++) t.a5e.data[i] = px ? px[i] | 0 : -1;
    Egf(t); wolfTexture = t; return t;
  }
  function plane(minX, y, minZ, width, length, facingUp, tex) {
    // MeshBuilder.texturedXZPlane as one quad (the original's two triangles share this diagonal)
    var us = 1 / 8, maxX = minX + width, maxZ = minZ + length, ny = facingUp ? -1 : 1;
    var u0 = tex[0] * us, u1 = tex[2] * us, v0 = tex[1] * us, v1 = tex[3] * us;
    return [[minX, y, maxZ, u0, v0], [minX, y, minZ, u0, v1], [maxX, y, minZ, u1, v1], [maxX, y, maxZ, u1, v0], ny];
  }
  function compileMesh(q) {
    var list = F6T(); Clc(list, 4864);
    var t = GdM(), buf = t.dy; C5(); Ep0(buf, 7, Lq0);
    for (var i = 0; i < 4; i++) { CUb(buf, q[i][0], q[i][1], q[i][2]); EpJ(buf, q[i][3], q[i][4]); Gu1(buf, 0, q[4], 0); E74(buf); }
    FE$(t); ELo();
    return list;
  }
  function wolfMeshList() {
    if (wolfMeshes) return wolfMeshes;
    wolfMeshes = {
      mouthBottom: compileMesh(plane(-1.5, 0, -4, 3, 4, true, [0, 0, 3, 4])),
      mouthTop: compileMesh(plane(-1.5, 1, -4, 3, 4, false, [0, 0, 3, 4])),
      mouthInside: compileMesh(plane(-1.5, -4.1, -3, 3, 1, true, [0, 0, 3, 4])),
      tongue: compileMesh(plane(-1.5, 0, -4, 3, 6, true, [3, 0, 6, 6]))
    };
    return wolfMeshes;
  }
  function wolfMiscLayer() {
    return {
      pq: function () { return 0; },
      no: function (wolf, ls, lsa, pt, age, yaw, pitch, scale) {
        if (failed || !enabled) return;
        var data = DB.get(wolf);
        if (!data || !(data instanceof core.WolfData)) return;
        try {
          var tex = wolfMiscTexture(), meshes = wolfMeshList();
          GnI(33984); FUe(FST(tex));
          GL.push();
          if (E.isChild(wolf)) { GL.translate(0, 10 * scale, 0); data.body.applyLocalTransform(scale * 0.5); }
          else data.body.applyLocalTransform(scale);
          data.head.applyLocalTransform(scale);
          Ggy();
          GL.push(); GL.rotate(90, 1, 0, 0); GL.scale(scale, scale, scale); Dle(meshes.mouthInside); GL.pop();
          GL.push(); data.nose.applyLocalTransform(scale); GL.scale(scale, scale, scale); Dle(meshes.mouthTop); GL.pop();
          GL.push(); data.mouth.applyLocalTransform(scale); GL.scale(scale, scale, scale); Dle(meshes.mouthBottom); GL.pop();
          GL.push(); data.tongue.applyLocalTransform(scale); GL.scale(scale, scale, scale); Dle(meshes.tongue); GL.pop();
          GL.pop(); stats.wolfLayers++;
        } catch (e) { fail("wolf_layer", e); }
      }
    };
  }
  // ArrowTrail / ArrowTrailManager: a fading ribbon behind flying arrows
  var arrowTrails = new Map();
  function arrowForward(a) { return pitchYaw(a.bd, a.C); }
  function pitchYaw(pitch, yaw) {
    var MH = core.MathHelper, f = MH.cos(-yaw * 0.017453292 - PI), f1 = MH.sin(-yaw * 0.017453292 - PI);
    var f2 = -MH.cos(-pitch * 0.017453292), f3 = MH.sin(-pitch * 0.017453292);
    return [f1 * f2, f3, f * f2];
  }
  function TrailNode(arrow) { this.x = 0; this.y = 0; this.z = 0; this.up = new Vec3f(); this.right = new Vec3f(); this.moveToArrow(arrow); }
  TrailNode.prototype.moveTo = function (n) { this.x = n.x; this.y = n.y; this.z = n.z; this.up.copy(n.up); this.right.copy(n.right); };
  TrailNode.prototype.moveToArrow = function (a) {
    this.x = a.b; this.y = a.f; this.z = a.c;
    var fw = arrowForward(a), up = pitchYaw(a.bd + 90, a.C);
    this.up.set(Math.fround(-up[0]), Math.fround(-up[1]), Math.fround(up[2]));
    var ax = Math.fround(-fw[0]), ay = Math.fround(-fw[1]), az = Math.fround(fw[2]), u = this.up;
    this.right.set(ay * u.z - az * u.y, az * u.x - ax * u.z, ax * u.y - ay * u.x);
  };
  function ArrowTrail(arrow) { this.arrow = arrow; this.spawnCooldown = 1; this.nodes = []; this.resetNodes(); }
  ArrowTrail.prototype.resetNodes = function () { for (var i = 0; i < 10; i++) this.nodes[i] = new TrailNode(this.arrow); };
  ArrowTrail.prototype.render = function (pt) {
    if (this.spawnCooldown > 40) { this.spawnCooldown = 0; this.resetNodes(); }
    while (this.spawnCooldown >= 1) { for (var i = 9; i > 0; i--) this.nodes[i].moveTo(this.nodes[i - 1]); this.nodes[0].moveToArrow(this.arrow); this.spawnCooldown -= 1; }
    var view = N.viewEntity();
    if (view == null) return;
    var vx = view.dn + (view.b - view.dn) * pt, vy = view.d9 + (view.f - view.d9) * pt, vz = view.dv + (view.c - view.dv) * pt;
    var verts = [];
    for (var j = 1; j < 10; j++) {
      var n0 = this.nodes[j - 1], n1 = this.nodes[j], p0 = [n0.x - vx, n0.y - vy, n0.z - vz], p1 = [n1.x - vx, n1.y - vy, n1.z - vz];
      var s0 = (10 - j) / 10 * 0.1, s1 = j === 1 ? 0 : (10 - j - 1) / 10 * 0.1;
      [[n0.right, n1.right], [n0.up, n1.up]].forEach(function (axes) {
        var a0 = axes[0], a1 = axes[1];
        verts.push(p0[0] - a0.x * s0, p0[1] - a0.y * s0, p0[2] - a0.z * s0, 1, 1, 1, 0.5);
        verts.push(p0[0] + a0.x * s0, p0[1] + a0.y * s0, p0[2] + a0.z * s0, 1, 1, 1, 0.5);
        verts.push(p1[0] + a1.x * s1, p1[1] + a1.y * s1, p1[2] + a1.z * s1, 1, 1, 1, 0.5);
        verts.push(p1[0] - a1.x * s1, p1[1] - a1.y * s1, p1[2] - a1.z * s1, 1, 1, 1, 0.5);
      });
    }
    GL.push();
    try { drawTrail(verts); } finally { GL.pop(); }
  };
  function arrow(entity, x, y, z, pt) {
    if (failed || !enabled || !core.ModConfig.showArrowTrails || !(entity instanceof Kx)) return;
    try {
      var trail = arrowTrails.get(entity);
      if (!trail) { if (arrowTrails.size >= 128) return; trail = new ArrowTrail(entity); arrowTrails.set(entity, trail); stats.trails++; }
      trail.render(pt);
    } catch (e) { fail("arrow_trail", e); }
  }

  // ===================== frame / tick =====================
  var lastWorld = null;
  function frame(partialTicks, renderManager) {
    if (renderManager) lastRenderManager = renderManager;
    if (failed || !enabled) return;
    var player = HEH && HEH.v;
    if (!player || !player.a) return;
    stats.frames++;
    var paused = !!HEH.cp;   // Minecraft.isGamePaused: the original freezes partial ticks and animation while paused
    if (!paused) DUH.partialTicks = partialTicks;
    var newTicks = player.cv + partialTicks;
    if (DUH.ticks > newTicks) DB.onTicksRestart();
    if (paused) { DUH.ticksPerFrame = 0; return; }
    DUH.ticksPerFrame = Math.min(Math.max(0, newTicks - DUH.ticks), 1);
    DUH.ticks = newTicks;
    if (lastWorld !== player.a) { lastWorld = player.a; arrowTrails.clear(); }
    DB.updateRender(partialTicks);
    arrowTrails.forEach(function (t, a) { t.spawnCooldown += DUH.ticksPerFrame; if (a.ed || !player.a) arrowTrails.delete(a); });
  }
  // ClientTickEvent: once per Minecraft.runTick (20 per second), never while the game is paused
  function tick() {
    if (failed || !enabled) return;
    var player = HEH && HEH.v;
    if (!player || !player.a || HEH.cp) return;
    stats.ticks++;
    DB.updateClient();
  }

  // ===================== guarded entry points =====================
  function guard(stage, fn, fallback) {
    return function (a, b, c, d, e, f, g, h, i) {
      if (failed) return fallback;
      var depth = matrixDepth();
      try { return fn(a, b, c, d, e, f, g, h, i); }
      catch (err) { unwindMatrix(depth); fail(stage, err); return fallback; }
    };
  }
  function setEnabled(on) {
    on = !!on;
    if (on === enabled) return;
    enabled = on; persist();
    if (!on) { try { restoreAll(); } catch (e) { report("disable", e); } }
    send("jaspercraft.mobends.state", { enabled: enabled, failed: failed, stats: statsCopy() });
  }
  // The Video Settings row: "Mo' Bends: ON/OFF" (id 973, after the optional-particles option)
  if (typeof JasprVideoLabel === "function" && typeof JasprVideoAction === "function") {
    var videoLabel = JasprVideoLabel, videoAction = JasprVideoAction;
    JasprVideoLabel = function (id) { return id === 973 ? $rt_str("Mo' Bends animations: " + (failed ? "OFF (error)" : enabled ? "ON" : "OFF")) : videoLabel(id); };
    JasprVideoAction = function (id) { if (id === 973) { if (!failed) setEnabled(!enabled); JasprVideoRefresh = true; return; } videoAction(id); };
  }
  $rt_globals.JasprMoBendsDiagnostics = Object.freeze({
    status: function () {
      var counts = {};
      (benders || []).forEach(function (b) { counts[b.key] = b.mutators.size; });
      return { enabled: enabled, failed: failed, lastError: lastError, tracked: DB.entryMap.size, mutators: counts, arrowTrails: arrowTrails.size, stats: statsCopy() };
    },
    setEnabled: function (on) { setEnabled(on); return enabled; }
  });

  return {
    pre: guard("pre", pre), post: guard("post", post),
    render: guard("render", function (part, scale) { part.$mb.renderPart(scale); }),
    postRender: guard("post_render", function (part, scale) { part.$mb.postRender(scale); }),
    firstPerson: guard("first_person", function (renderer) { var m = activeMutator(renderer); if (m && m.poseForFirstPersonView) { m.poseForFirstPersonView(); stats.firstPerson++; } }),
    armorModel: guard("armor", function (layer, model) {
      var w = armorWrappers.get(model), m = activeMutator(layer.bOZ), frame = current();
      var data = m && frame && frame.data;
      if (data && data.isBiped && model instanceof OB) {
        if (!w) { w = new ArmorWrapper(model); armorWrappers.set(model, w); stats.armorWrappers++; }
        w.prepare(data);
      } else if (w) w.deapply();
      return model;
    }, null),
    heldItem: guard("held_item", heldItem, false),
    cape: guard("cape", cape, false),
    elytra: guard("elytra", elytra, false),
    arrow: arrow,
    frame: guard("frame", frame), tick: guard("tick", tick),
    enabled: function () { return enabled && !failed; }, setEnabled: setEnabled,
    // Internals for the offline tests (tests/mobends-native.test.cjs); nothing in the game calls these.
    _test: { core: core, ModelPart: ModelPart, BoxFactory: BoxFactory, cloneModel: cloneModel, registry: registry, ArmorWrapper: ArmorWrapper,
      PlayerMutator: PlayerMutator, ZombieMutator: ZombieMutator, SkeletonMutator: SkeletonMutator, PigZombieMutator: PigZombieMutator,
      SpiderMutator: SpiderMutator, SquidMutator: SquidMutator, WolfMutator: WolfMutator, stats: stats }
  };
}());

// getModelFromSlot hook target (armor follows Mo' Bends' limbs); falls back to the vanilla model on any error
function JasprMoBendsArmor(layer, model) { var w = JasprMoBendsBridge.armorModel(layer, model); return w === null ? model : w; }
