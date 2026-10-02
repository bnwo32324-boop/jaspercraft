/* JasperCraft Mo' Bends: the animation core of Mo' Bends 1.2.2 (Forge 1.12.2) translated to JavaScript.
 * Mo' Bends is Copyright (c) 2017 Iwo Plaza and contributors, MIT License (see THIRD_PARTY_NOTICES.txt).
 * Class, method and constant names follow the original Java source so the two can be read side by side.
 * Everything engine-specific (entities, world, GL) goes through the native adapter N (mobends-teavm.js).
 * This file has no TeaVM names; it runs unchanged in Node for the parity tests.
 */
function createJasprMoBendsCore(N) {
  "use strict";
  var PI = Math.PI, TWO_PI = PI * 2, RAD_TO_DEG = 180 / PI;
  function extend(Child, Parent) { Child.prototype = Object.create(Parent.prototype); Child.prototype.constructor = Child; return Child; }

  // ---- net.minecraft.util.math.MathHelper (the lookup-table sin/cos the original animations use)
  var SIN_TABLE = new Float32Array(65536);
  for (var t = 0; t < 65536; t++) SIN_TABLE[t] = Math.sin(t * PI * 2 / 65536);
  // Java (int) of a float: truncation toward zero, saturating at the int range, NaN -> 0
  function jint(v) { return v >= 2147483647 ? 2147483647 : v <= -2147483648 ? -2147483648 : (v < 0 ? Math.ceil(v) : Math.floor(v)) | 0; }
  var MathHelper = {
    // (int)(value * 10430.378F) with Java's float rounding at each step (10430.378F is 10430.3779296875)
    sin: function (v) { return SIN_TABLE[jint(Math.fround(Math.fround(v) * 10430.3779296875)) & 65535]; },
    cos: function (v) { return SIN_TABLE[jint(Math.fround(Math.fround(Math.fround(v) * 10430.3779296875) + 16384)) & 65535]; },
    sqrt: function (v) { return Math.sqrt(v); },
    abs: function (v) { return Math.abs(v); },
    atan2: function (y, x) { return Math.atan2(y, x); },
    wrapDegrees: function (v) { v = v % 360; if (v >= 180) v -= 360; if (v < -180) v += 360; return v; },
    clamp: function (v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }
  };

  // ---- goblinbob.mobends.core.util.GUtil / Tween
  var GUtil = {
    PI: PI, TWO_PI: TWO_PI, RAD_TO_DEG: RAD_TO_DEG,
    clamp: function (v, lo, hi) { return Math.min(Math.max(v, lo), hi); },
    angleFromCoordinates: function (x, z) { return Math.atan2(x, z) / PI * 180; },
    wrapRadians: function (a) { a = a % PI; if (a >= PI) a -= PI * 2; else if (a < -PI) a += PI * 2; return a; },
    getRadianDifference: function (a, b) { a = GUtil.wrapRadians(a); b = GUtil.wrapRadians(b); var d = Math.abs(a - b); return d > PI ? PI * 2 - d : d; },
    lerp: function (a, b, s) { return a + (b - a) * s; },
    interpolateRotation: function (a, b, pt) { var f; for (f = b - a; f < -180; f += 360); while (f >= 180) f -= 360; return a + pt * f; },
    translate: function (points, x, y, z) { for (var i = 0; i < points.length; i++) points[i].add(x, y, z); return points; },
    rotate: function (points, q) { for (var i = 0; i < points.length; i++) QuaternionUtils.multiply(points[i], q, points[i]); }
  };
  var Tween = {
    easeIn: function (a, p) { return Math.pow(a, p); },
    easeOut: function (a, p) { return 1 - Math.pow(1 - a, p); },
    easeInOut: function (a, p) { if (a < 0.5) { a *= 2; a = Math.pow(a, p); a /= 2; } else { a = 1 - a; a *= 2; a = Math.pow(a, p); a /= 2; a = 1 - a; } return a; }
  };

  // ---- goblinbob.mobends.core.math
  function Vec3f(x, y, z) { this.x = x || 0; this.y = y || 0; this.z = z || 0; }
  Vec3f.prototype.set = function (x, y, z) { this.x = x; this.y = y; this.z = z; };
  Vec3f.prototype.copy = function (o) { this.x = o.x; this.y = o.y; this.z = o.z; };
  Vec3f.prototype.add = function (x, y, z) { this.x += x; this.y += y; this.z += z; };
  Vec3f.prototype.addVec = function (o) { this.x += o.x; this.y += o.y; this.z += o.z; };
  Vec3f.prototype.scale = function (a) { this.x *= a; this.y *= a; this.z *= a; };
  Vec3f.prototype.lengthSq = function () { return this.x * this.x + this.y * this.y + this.z * this.z; };
  Vec3f.prototype.getX = function () { return this.x; };
  Vec3f.prototype.getY = function () { return this.y; };
  Vec3f.prototype.getZ = function () { return this.z; };

  function Quaternion(x, y, z, w) { this.x = x || 0; this.y = y || 0; this.z = z || 0; this.w = w === undefined ? 1 : w; }
  Quaternion.prototype.lengthSquared = function () { return this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w; };
  Quaternion.prototype.length = function () { return Math.sqrt(this.lengthSquared()); };
  Quaternion.prototype.set = function (x, y, z, w) { this.x = x; this.y = y; this.z = z; this.w = w; };
  Quaternion.prototype.copy = function (q) { this.x = q.x; this.y = q.y; this.z = q.z; this.w = q.w; };
  Quaternion.prototype.setIdentity = function () { this.set(0, 0, 0, 1); };
  Quaternion.prototype.normalise = function () {
    var length = this.length();
    if (length !== 0) { var inv = 1 / length; this.x *= inv; this.y *= inv; this.z *= inv; this.w *= inv; }
  };
  Quaternion.prototype.negate = function () { this.x = -this.x; this.y = -this.y; this.z = -this.z; };
  Quaternion.prototype.setFromAxisAngle = function (x, y, z, angle) {
    var n = Math.sqrt(x * x + y * y + z * z), s = Math.sin(0.5 * angle) / n;
    this.x = x * s; this.y = y * s; this.z = z * s; this.w = Math.cos(0.5 * angle);
  };
  Quaternion.prototype.rotate = function (x, y, z, angle) {
    var n = Math.sqrt(x * x + y * y + z * z), s = Math.sin(0.5 * angle) / n;
    Quaternion.mul4(x * s, y * s, z * s, Math.cos(0.5 * angle), this.x, this.y, this.z, this.w, this);
  };
  Quaternion.mul = function (l, r, dest) {
    dest.set(l.x * r.w + l.w * r.x + l.y * r.z - l.z * r.y,
      l.y * r.w + l.w * r.y + l.z * r.x - l.x * r.z,
      l.z * r.w + l.w * r.z + l.x * r.y - l.y * r.x,
      l.w * r.w - l.x * r.x - l.y * r.y - l.z * r.z);
    return dest;
  };
  Quaternion.mul4 = function (x1, y1, z1, w1, x2, y2, z2, w2, dest) {
    dest.set(x1 * w2 + w1 * x2 + y1 * z2 - z1 * y2, y1 * w2 + w1 * y2 + z1 * x2 - x1 * z2, z1 * w2 + w1 * z2 + x1 * y2 - y1 * x2, w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2);
    return dest;
  };

  var QuaternionUtils = {
    // The original's formula, kept exactly (it negates only the x component of the axis).
    multiply: function (vector, quat, dest) {
      var ux = -quat.x, uy = quat.y, uz = quat.z, s = -quat.w;
      var x = vector.x, y = vector.y, z = vector.z;
      var dotUU = ux * ux + uy * uy + uz * uz, dotUV = ux * x + uy * y + uz * z;
      var cx = uy * z - uz * y, cy = uz * x - ux * z, cz = ux * y - uy * x;
      dest.set(ux * 2 * dotUV, uy * 2 * dotUV, uz * 2 * dotUV);
      dest.add(x * (s * s - dotUU), y * (s * s - dotUU), z * (s * s - dotUU));
      dest.add(cx * 2 * s, cy * 2 * s, cz * 2 * s);
    },
    // Column-major OpenGL matrix of a quaternion (QuaternionUtils.quatToGlMatrix).
    quatToGlMatrix: function (q, out) {
      var f = q.x * q.x, f1 = q.x * q.y, f2 = q.x * q.z, f3 = q.x * q.w, f4 = q.y * q.y, f5 = q.y * q.z, f6 = q.y * q.w, f7 = q.z * q.z, f8 = q.z * q.w;
      out[0] = 1 - 2 * (f4 + f7); out[1] = 2 * (f1 + f8); out[2] = 2 * (f2 - f6); out[3] = 0;
      out[4] = 2 * (f1 - f8); out[5] = 1 - 2 * (f + f7); out[6] = 2 * (f5 + f3); out[7] = 0;
      out[8] = 2 * (f2 + f6); out[9] = 2 * (f5 - f3); out[10] = 1 - 2 * (f + f4); out[11] = 0;
      out[12] = 0; out[13] = 0; out[14] = 0; out[15] = 1;
      return out;
    }
  };
  var GlHelper = { rotate: function (q) { N.gl.multQuat(q.x, q.y, q.z, q.w); } };

  var scratchQ = new Quaternion();
  function SmoothOrientation() {
    this.start = new Quaternion(); this.end = new Quaternion(); this.smooth = new Quaternion();
    this.progress = 1; this.smoothness = 1;
  }
  var SO = SmoothOrientation.prototype;
  SO.getEnd = function () { return this.end; };
  SO.getSmooth = function () { return this.smooth; };
  SO.copy = function (o) { this.start.copy(o.start); this.end.copy(o.end); this.smooth.copy(o.smooth); this.progress = o.progress; };
  SO.setSmoothness = function (s) { this.smoothness = s; return this; };
  SO.set = function (x, y, z, w) { this.start.set(x, y, z, w); this.start.normalise(); this.end.copy(this.start); this.smooth.copy(this.start); this.progress = 0; return this; };
  SO.add = function (x, y, z, w) { this.start.x += x; this.start.y += y; this.start.z += z; this.start.w += w; this.end.copy(this.start); this.smooth.copy(this.start); this.progress = 0; return this; };
  SO.orient = function (angle, x, y, z) { this.start.copy(this.smooth); this.end.setFromAxisAngle(x, y, z, angle / 180 * PI); this.progress = 0; this.updateSmooth(); return this; };
  SO.orientX = function (a) { return this.orient(a, 1, 0, 0); };
  SO.orientY = function (a) { return this.orient(a, 0, 1, 0); };
  SO.orientZ = function (a) { return this.orient(a, 0, 0, 1); };
  SO.orientInstant = function (a, x, y, z) { this.end.setFromAxisAngle(x, y, z, a / 180 * PI); this.start.copy(this.end); this.smooth.copy(this.end); return this; };
  SO.orientInstantX = function (a) { return this.orientInstant(a, 1, 0, 0); };
  SO.orientInstantY = function (a) { return this.orientInstant(a, 0, 1, 0); };
  SO.orientInstantZ = function (a) { return this.orientInstant(a, 0, 0, 1); };
  SO.rotate = function (angle, x, y, z) { this.end.rotate(x, y, z, angle / 180 * PI); this.updateSmooth(); return this; };
  SO.rotateX = function (a) { return this.rotate(a, 1, 0, 0); };
  SO.rotateY = function (a) { return this.rotate(a, 0, 1, 0); };
  SO.rotateZ = function (a) { return this.rotate(a, 0, 0, 1); };
  SO.rotateInstant = function (angle, x, y, z) {
    scratchQ.setFromAxisAngle(x, y, z, angle / 180 * PI); Quaternion.mul(scratchQ, this.end, this.end);
    this.start.copy(this.end); this.smooth.copy(this.end); return this;
  };
  SO.rotateInstantX = function (a) { return this.rotateInstant(a, 1, 0, 0); };
  SO.rotateInstantY = function (a) { return this.rotateInstant(a, 0, 1, 0); };
  SO.rotateInstantZ = function (a) { return this.rotateInstant(a, 0, 0, 1); };
  SO.localRotate = function (angle, x, y, z) { scratchQ.setFromAxisAngle(x, y, z, angle / 180 * PI); Quaternion.mul(this.end, scratchQ, this.end); this.updateSmooth(); return this; };
  SO.localRotateX = function (a) { return this.localRotate(a, 1, 0, 0); };
  SO.localRotateY = function (a) { return this.localRotate(a, 0, 1, 0); };
  SO.localRotateZ = function (a) { return this.localRotate(a, 0, 0, 1); };
  SO.orientZero = function () { this.start.copy(this.smooth); this.end.setIdentity(); this.progress = 0; this.updateSmooth(); return this; };
  SO.identity = function () { this.start.setIdentity(); this.end.setIdentity(); this.smooth.setIdentity(); this.progress = 1; return this; };
  SO.finish = function () { this.smooth.copy(this.end); this.start.copy(this.end); this.progress = 1; this.updateSmooth(); return this; };
  SO.update = function (tpf) { this.progress += tpf * this.smoothness; this.progress = Math.min(this.progress, 1); this.updateSmooth(); };
  SO.updateSmooth = function () {
    var s = this.start, e = this.end, p = this.progress;
    this.smooth.set(s.x + (e.x - s.x) * p, s.y + (e.y - s.y) * p, s.z + (e.z - s.z) * p, s.w + (e.w - s.w) * p);
    this.smooth.normalise();
  };

  function SmoothVector3f(src) {
    this.start = new Vec3f(); this.end = new Vec3f(); this.smoothness = new Vec3f(1, 1, 1); this.completion = new Vec3f();
    if (src) this.copy(src);
  }
  var SV = SmoothVector3f.prototype;
  SV.slideTo = function (x, y, z, smoothness) {
    if (this.end.x !== x || this.end.y !== y || this.end.z !== z) {
      this.start.set(this.getX(), this.getY(), this.getZ()); this.end.set(x, y, z);
      this.completion.set(0, 0, 0); this.smoothness.set(smoothness, smoothness, smoothness);
    }
  };
  SV.slideToZero = function (smoothness) { this.slideTo(0, 0, 0, smoothness === undefined ? 1 : smoothness); };
  SV.slideX = function (v, s) { if (this.end.x !== v) { this.start.x = this.getX(); this.end.x = v; this.completion.x = 0; } this.smoothness.x = s === undefined ? 0.6 : s; };
  SV.slideY = function (v, s) { if (this.end.y !== v) { this.start.y = this.getY(); this.end.y = v; this.completion.y = 0; } this.smoothness.y = s === undefined ? 0.6 : s; };
  SV.slideZ = function (v, s) { if (this.end.z !== v) { this.start.z = this.getZ(); this.end.z = v; this.completion.z = 0; } this.smoothness.z = s === undefined ? 0.6 : s; };
  SV.add = function (x, y, z) { this.start.set(this.getX(), this.getY(), this.getZ()); this.completion.set(0, 0, 0); this.end.x += x; this.end.y += y; this.end.z += z; };
  SV.setX = function (v) { this.start.x = v; this.end.x = v; this.completion.x = 1; };
  SV.setY = function (v) { this.start.y = v; this.end.y = v; this.completion.y = 1; };
  SV.setZ = function (v) { this.start.z = v; this.end.z = v; this.completion.z = 1; };
  SV.set = function (x, y, z) { this.start.set(x, y, z); this.end.copy(this.start); this.completion.set(1, 1, 1); };
  SV.copy = function (o) { this.completion.copy(o.completion); this.smoothness.copy(o.smoothness); this.end.copy(o.end); this.start.copy(o.start); };
  SV.limitDistanceTo = function (other, maxDistance) {
    var dx = this.end.x - other.end.x, dy = this.end.y - other.end.y, dz = this.end.z - other.end.z, sq = dx * dx + dy * dy + dz * dz;
    if (sq > maxDistance * maxDistance) { var l = Math.sqrt(sq); this.end.set(other.end.x + dx / l * maxDistance, other.end.y + dy / l * maxDistance, other.end.z + dz / l * maxDistance); }
  };
  SV.getX = function () { return this.start.x + (this.end.x - this.start.x) * this.completion.x; };
  SV.getY = function () { return this.start.y + (this.end.y - this.start.y) * this.completion.y; };
  SV.getZ = function () { return this.start.z + (this.end.z - this.start.z) * this.completion.z; };
  SV.update = function (tpf) {
    var c = this.completion, s = this.smoothness;
    c.x = Math.min(c.x + tpf * s.x, 1); c.y = Math.min(c.y + tpf * s.y, 1); c.z = Math.min(c.z + tpf * s.z, 1);
  };
  SV.finish = function () { this.set(this.end.x, this.end.y, this.end.z); };

  // ---- goblinbob.mobends.core.client.model.ModelPartTransform (IModelPart without geometry)
  function ModelPartTransform(parent) {
    this.position = new Vec3f(); this.scale = new Vec3f(1, 1, 1); this.offset = new Vec3f();
    this.rotation = new SmoothOrientation(); this.offsetScale = 1; this.globalOffset = new Vec3f();
    this.parent = parent || null;
  }
  var MPT = ModelPartTransform.prototype;
  MPT.isModelPart = true;
  MPT.update = function (tpf) { this.rotation.update(tpf); };
  MPT.getPosition = function () { return this.position; };
  MPT.getScale = function () { return this.scale; };
  MPT.getOffset = function () { return this.offset; };
  MPT.getRotation = function () { return this.rotation; };
  MPT.getOffsetScale = function () { return this.offsetScale; };
  MPT.getGlobalOffset = function () { return this.globalOffset; };
  MPT.getParent = function () { return this.parent; };
  MPT.isShowing = function () { return true; };
  MPT.setVisible = function () {};
  MPT.renderPart = function () {};
  MPT.renderJustPart = function () {};
  // ModelPartTransform.syncUp does not copy the global offset (ModelPart.syncUp does).
  MPT.syncUp = function (part) {
    if (part == null) return;
    this.position.copy(part.getPosition()); this.rotation.copy(part.getRotation()); this.offset.copy(part.getOffset());
    this.scale.copy(part.getScale()); this.offsetScale = part.getOffsetScale();
  };
  MPT.applyPreTransform = function (scale) { var g = this.globalOffset; if (g.x !== 0 || g.y !== 0 || g.z !== 0) N.gl.translate(g.x * scale, g.y * scale, g.z * scale); };
  MPT.applyLocalTransform = function (scale) { applyLocal(this, scale); };
  MPT.applyCharacterTransform = function (scale) { applyCharacter(this, scale); };
  MPT.propagateTransform = function (scale) { this.applyLocalTransform(scale); };
  MPT.applyPostTransform = function () {};
  // IModelPart.applyCharacterTransform (default method) and the shared local transform
  function applyCharacter(part, scale) {
    part.applyPreTransform(scale);
    var parent = part.getParent();
    if (parent != null) parent.applyCharacterTransform(scale * part.getOffsetScale());
    part.applyLocalTransform(scale);
  }
  function applyLocal(part, scale) {
    var p = part.position, o = part.offset, k = scale * part.offsetScale, s = part.scale;
    if (p.x !== 0 || p.y !== 0 || p.z !== 0) N.gl.translate(p.x * k, p.y * k, p.z * k);
    if (o.x !== 0 || o.y !== 0 || o.z !== 0) N.gl.translate(o.x * k, o.y * k, o.z * k);
    GlHelper.rotate(part.rotation.getSmooth());
    if (s.x !== 0 || s.y !== 0 || s.z !== 0) N.gl.scale(s.x, s.y, s.z);
  }

  // ---- goblinbob.mobends.core.data.OverridableProperty
  function OverridableProperty(value) { this.value = value; this.overrideValue = null; }
  OverridableProperty.prototype.override = function (v) { this.overrideValue = v; };
  OverridableProperty.prototype.unsetOverride = function () { this.overrideValue = null; };
  OverridableProperty.prototype.get = function () { return this.overrideValue != null ? this.overrideValue : this.value; };
  OverridableProperty.prototype.set = function (v) { this.value = v; };

  // ---- goblinbob.mobends.core.client.event.DataUpdateHandler
  var DataUpdateHandler = { partialTicks: 0, ticks: 0, ticksPerFrame: 0, getTicks: function () { return DataUpdateHandler.ticks; } };

  // ---- goblinbob.mobends.core.data.EntityData / LivingEntityData
  function EntityData(entity) {
    this.entity = entity; this.entityID = 0; this.positionX = this.positionY = this.positionZ = 0;
    this.nameToPartMap = new Map(); this.onGround = true; this.onGroundOverride = null; this.stillnessOverride = null;
    if (entity != null) { var E = N.entity; this.entityID = E.id(entity); this.positionX = E.posX(entity); this.positionY = E.posY(entity); this.positionZ = E.posZ(entity); }
    this.motionX = this.prevMotionX = 0; this.motionY = this.prevMotionY = 1; this.motionZ = this.prevMotionZ = 0;
    this.initModelPose();
  }
  var ED = EntityData.prototype;
  ED.initModelPose = function () {
    this.globalOffset = new SmoothVector3f(); this.localOffset = new SmoothVector3f();
    this.renderRotation = new SmoothOrientation(); this.centerRotation = new SmoothOrientation();
    this.nameToPartMap.set("renderRotation", this.renderRotation); this.nameToPartMap.set("centerRotation", this.centerRotation);
  };
  ED.updateParts = function (tpf) { this.globalOffset.update(tpf); this.localOffset.update(tpf); this.renderRotation.update(tpf); this.centerRotation.update(tpf); };
  ED.calcOnGround = function () {
    if (this.onGroundOverride != null) return this.onGroundOverride;
    var e = this.entity, E = N.entity, x = Math.floor(E.posX(e)), y = Math.floor(E.posY(e)), z = Math.floor(E.posZ(e));
    if (this.motionY <= 0 && (N.world.isStairs(e, x, y, z) || N.world.isStairs(e, x, y - 1, z))) return true;
    return N.world.collidesBelow(e, 0.125);
  };
  ED.getPositionX = function () { return this.positionX; };
  ED.getPositionY = function () { return this.positionY; };
  ED.getPositionZ = function () { return this.positionZ; };
  ED.getMotionX = function () { return this.motionX; };
  ED.getMotionY = function () { return this.motionY; };
  ED.getMotionZ = function () { return this.motionZ; };
  ED.getPrevMotionX = function () { return this.prevMotionX; };
  ED.getPrevMotionY = function () { return this.prevMotionY; };
  ED.getPrevMotionZ = function () { return this.prevMotionZ; };
  ED.getInterpolatedMotionX = function () { return this.prevMotionX + (this.motionX - this.prevMotionX) * DataUpdateHandler.partialTicks; };
  ED.getInterpolatedMotionY = function () { return this.prevMotionY + (this.motionY - this.prevMotionY) * DataUpdateHandler.partialTicks; };
  ED.getInterpolatedMotionZ = function () { return this.prevMotionZ + (this.motionZ - this.prevMotionZ) * DataUpdateHandler.partialTicks; };
  ED.isOnGround = function () { return this.onGround; };
  ED.isStillHorizontally = function () {
    var sq = this.motionX * this.motionX + this.motionZ * this.motionZ;
    return this.stillnessOverride != null ? this.stillnessOverride : sq < 0.0025;
  };
  ED.update = function () { if (this.entity == null) return; this.updateParts(DataUpdateHandler.ticksPerFrame); };
  ED.getEntity = function () { return this.entity; };
  ED.getLookAngle = function () { var v = N.entity.lookVec(this.entity); return GUtil.angleFromCoordinates(v[0], v[2]); };
  ED.getMovementAngle = function () { if (this.isStillHorizontally()) return 0; return GUtil.angleFromCoordinates(this.motionX, this.motionZ) - this.getLookAngle(); };
  ED.getForwardMomentum = function () {
    if (this.isStillHorizontally()) return 0;
    var v = N.entity.lookVec(this.entity), l = Math.sqrt(v[0] * v[0] + v[2] * v[2]);
    if (l < 1e-4) return 0;
    return v[0] / l * this.motionX + v[2] / l * this.motionZ;
  };
  ED.getSidewaysMomentum = function () {
    if (this.isStillHorizontally()) return 0;
    // Vec3d.rotateYaw(-PI/2) of the look vector, flattened and normalised
    var v = N.entity.lookVec(this.entity), a = -PI / 2, c = Math.cos(a), s = Math.sin(a);
    var rx = v[0] * c + v[2] * s, rz = v[2] * c - v[0] * s, l = Math.sqrt(rx * rx + rz * rz);
    if (l < 1e-4) return 0;
    return rx / l * this.motionX + rz / l * this.motionZ;
  };
  ED.isStrafing = function () { var a = this.getMovementAngle(); return (a >= 30 && a <= 150) || (a >= -150 && a <= -30); };
  ED.isUnderwater = function () {
    var e = this.entity, E = N.entity;
    if (!E.isInWater(e)) return false;
    return N.world.isStaticLiquid(e, Math.floor(E.posX(e)), Math.floor(E.posY(e) + 2), Math.floor(E.posZ(e)));
  };
  ED.getPrevMotionMagnitude = function () { return Math.sqrt(this.prevMotionX * this.prevMotionX + this.prevMotionY * this.prevMotionY + this.prevMotionZ * this.prevMotionZ); };
  ED.getMotionMagnitude = function () { return Math.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ); };
  ED.getInterpolatedMotionMagnitude = function () { var p = this.getPrevMotionMagnitude(); return p + (this.getMotionMagnitude() - p) * DataUpdateHandler.partialTicks; };
  ED.getXZMotionMagnitude = function () { return Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ); };
  ED.getPrevXZMotionMagnitude = function () { return Math.sqrt(this.prevMotionX * this.prevMotionX + this.prevMotionZ * this.prevMotionZ); };
  ED.getInterpolatedXZMotionMagnitude = function () { var p = this.getPrevXZMotionMagnitude(); return p + (this.getXZMotionMagnitude() - p) * DataUpdateHandler.partialTicks; };
  ED.updateClient = function () {
    var e = this.entity, E = N.entity;
    this.prevMotionX = this.motionX; this.prevMotionY = this.motionY; this.prevMotionZ = this.motionZ;
    var x = E.posX(e), y = E.posY(e), z = E.posZ(e);
    this.motionX = x - this.positionX; this.motionY = y - this.positionY; this.motionZ = z - this.positionZ;
    this.positionX = x; this.positionY = y; this.positionZ = z;
  };
  ED.getPartForName = function (name) { return this.nameToPartMap.get(name); };
  ED.onTicksRestart = function () {};

  function LivingEntityData(entity) {
    EntityData.call(this, entity);
    this.climbingCycle = 0; this.alreadyAttacked = false; this.climbing = false;
    this.limbSwing = new OverridableProperty(0); this.limbSwingAmount = new OverridableProperty(0); this.swingProgress = new OverridableProperty(0);
    this.headYaw = new OverridableProperty(0); this.headPitch = new OverridableProperty(0);
    this.ticksInAir = 100; this.ticksAfterTouchdown = 100; this.ticksAfterAttack = 100; this.ticksFalling = 100;
  }
  extend(LivingEntityData, EntityData);
  var LED = LivingEntityData.prototype;
  LED.setClimbing = function (f) { this.climbing = f; };
  LED.getClimbingCycle = function () { return this.climbingCycle; };
  LED.getTicksInAir = function () { return this.ticksInAir; };
  LED.getTicksAfterTouchdown = function () { return this.ticksAfterTouchdown; };
  LED.getTicksAfterAttack = function () { return this.ticksAfterAttack; };
  LED.getTicksFalling = function () { return this.ticksFalling; };
  LED.isClimbing = function () { return this.climbing; };
  LED.updateClient = function () {
    ED.updateClient.call(this);
    var calc = this.calcOnGround();
    if (calc && !this.onGround) { this.onTouchdown(); this.onGround = true; }
    if ((!calc && this.onGround) || (this.prevMotionY <= 0 && this.motionY - this.prevMotionY > 0.4 && this.ticksInAir > 2)) { this.onLiftoff(); this.onGround = false; }
    if (this.calcClimbing()) { this.climbingCycle += this.motionY * 2.6; this.climbing = true; } else this.climbing = false;
    if (N.entity.isSwingInProgress(this.entity)) {
      if (!this.alreadyAttacked || this.ticksAfterAttack > 5) { this.onAttack(); this.alreadyAttacked = true; }
    } else this.alreadyAttacked = false;
  };
  LED.update = function (pt) {
    ED.update.call(this, pt);
    var tpf = DataUpdateHandler.ticksPerFrame;
    if (this.isOnGround()) this.ticksAfterTouchdown += tpf;
    else { this.ticksInAir += tpf; if (this.motionY < 0) this.ticksFalling += tpf; else this.ticksFalling = 0; }
    this.ticksAfterAttack += tpf;
  };
  LED.onTouchdown = function () { this.ticksAfterTouchdown = 0; this.ticksFalling = 0; };
  LED.onLiftoff = function () { this.ticksInAir = 0; };
  LED.onAttack = function () { this.ticksAfterAttack = 0; };
  var FACING_ANGLE = { south: 0, west: 90, north: 180, east: 270 };
  LED.getClimbingRotation = function () { return FACING_ANGLE[this.getLadderFacing()] + 180; };
  LED.getLadderFacing = function () {
    var e = this.entity, E = N.entity, x = Math.floor(E.posX(e)), y = Math.floor(E.posY(e)), z = Math.floor(E.posZ(e)), W = N.world;
    var facing = W.climbableFacing(e, x, y, z);
    if (facing === "north") facing = W.climbableFacing(e, x, y - 1, z);
    if (facing === "north") facing = W.climbableFacing(e, x, y - 2, z);
    return facing;
  };
  LED.calcClimbing = function () {
    var e = this.entity;
    if (e == null || !N.entity.hasWorld(e)) return false;
    var E = N.entity, W = N.world, x = Math.floor(E.posX(e)), y = Math.floor(E.posY(e)), z = Math.floor(E.posZ(e));
    return E.isOnLadder(e) && !this.isOnGround() && (W.isClimbable(e, x, y, z) || W.isClimbable(e, x, y - 1, z) || W.isClimbable(e, x, y - 2, z));
  };
  LED.getLedgeHeight = function () {
    var e = this.entity, E = N.entity, W = N.world, posY = E.posY(e);
    var clientY = Math.fround(posY + (posY - E.prevPosY(e)) * DataUpdateHandler.partialTicks);
    var x = Math.floor(E.posX(e)), y = Math.floor(posY), z = Math.floor(E.posZ(e)), frac = clientY - jint(clientY);
    if (!W.isClimbable(e, x, y + 2, z)) {
      if (!W.isClimbable(e, x, y + 1, z)) return !W.isClimbable(e, x, y, z) ? frac + 2 : frac + 1;
      return frac;
    }
    return -2;
  };
  LED.isDrawingBow = function () {
    var e = this.entity, E = N.entity;
    return E.itemInUseCount(e) > 0 && (E.stackUseAction(E.mainStack(e)) === "bow" || E.stackUseAction(E.offStack(e)) === "bow");
  };

  // ---- goblinbob.mobends.core.data.EntityDatabase
  var EntityDatabase = {
    entryMap: new Map(),
    get: function (entity) { return this.entryMap.get(N.entity.id(entity)); },
    getOrMake: function (factory, entity) {
      var id = N.entity.id(entity), data = this.entryMap.get(id);
      if (data == null) { data = factory(entity); this.entryMap.set(id, data); }
      return data;
    },
    updateClient: function () {
      var self = this;
      this.entryMap.forEach(function (data, id) {
        var entity = N.worldEntityById(id);
        if (entity == null || data.getEntity() !== entity) { BenderRegistry.clearCache(data.getEntity()); self.entryMap.delete(id); }
        else data.updateClient();
      });
    },
    updateRender: function (pt) { this.entryMap.forEach(function (data) { data.update(pt); }); },
    refresh: function () { this.entryMap.clear(); },
    onTicksRestart: function () { this.entryMap.forEach(function (data) { data.onTicksRestart(); }); }
  };

  // ---- goblinbob.mobends.core.animation
  function AnimationBit() { this.layer = null; }
  AnimationBit.prototype.setupForPlay = function (layer, data) { this.layer = layer; this.onPlay(data); };
  AnimationBit.prototype.getActions = function () { return null; };
  AnimationBit.prototype.onPlay = function () {};
  AnimationBit.prototype.perform = function () {};
  function HardAnimationLayer() { this.performedBit = null; this.previousBit = null; }
  var HAL = HardAnimationLayer.prototype;
  HAL.playBit = function (bit, data) { this.previousBit = this.performedBit; this.performedBit = bit; bit.setupForPlay(this, data); };
  HAL.playOrContinueBit = function (bit, data) { if (!this.isPlaying(bit)) this.playBit(bit, data); };
  HAL.perform = function (data) { if (this.performedBit != null) this.performedBit.perform(data); };
  HAL.isPlaying = function (bit) { return bit === undefined ? this.performedBit != null : bit === this.performedBit; };
  HAL.clearAnimation = function () { this.performedBit = null; };
  HAL.getPerformedBit = function () { return this.performedBit; };
  function bit(proto, ctor) { var C = ctor || function () { AnimationBit.call(this); }; extend(C, AnimationBit); for (var k in proto) C.prototype[k] = proto[k]; return C; }
  function ArmatureMask(mode) { this.mode = mode; this.includedParts = []; this.excludedParts = []; }
  ArmatureMask.prototype.include = function (b) { this.includedParts.push(b); };
  ArmatureMask.prototype.exclude = function (b) { this.excludedParts.push(b); };
  ArmatureMask.prototype.doesAllow = function (b) {
    if (this.mode === "INCLUDE_ONLY") return this.includedParts.indexOf(b) >= 0;
    if (this.mode === "EXCLUDE_ONLY") return this.excludedParts.indexOf(b) < 0;
    return true;
  };

  // shared helpers for the biped bits
  function hands(data) {
    var right = N.entity.primaryHandRight(data.getEntity());
    return { right: right, dir: right ? 1 : -1, mainArm: right ? data.rightArm : data.leftArm, offArm: right ? data.leftArm : data.rightArm,
      mainForeArm: right ? data.rightForeArm : data.leftForeArm, offForeArm: right ? data.leftForeArm : data.rightForeArm,
      mainItemRotation: right ? data.renderRightItemRotation : data.renderLeftItemRotation };
  }
  function holdsSword(e) { return N.item.isSword(N.entity.mainItem(e)); }

  // ---- goblinbob.mobends.standard.animation.bit.biped
  var StandAnimationBit = bit({
    kneelDuration: 0.15,
    onPlay: function (data) {
      var touchdown = Math.min(data.getTicksAfterTouchdown() * this.kneelDuration, 1);
      if (touchdown < 0.5) {
        data.body.rotation.orientInstant(20, 1, 0, 0);
        data.rightLeg.rotation.orient(-20, 1, 0, 0); data.leftLeg.rotation.orient(-45, 1, 0, 0);
        data.rightForeLeg.rotation.orient(60, 1, 0, 0); data.leftForeLeg.rotation.orient(60, 1, 0, 0);
      }
    },
    perform: function (data) {
      data.localOffset.slideToZero(0.3); data.globalOffset.slideToZero(0.3);
      data.renderRotation.setSmoothness(0.3).orientZero(); data.centerRotation.setSmoothness(0.3).orientZero();
      data.renderRightItemRotation.setSmoothness(0.3).orientZero(); data.renderLeftItemRotation.setSmoothness(0.3).orientZero();
      data.rightLeg.rotation.orient(0, 1, 0, 0); data.rightLeg.rotation.rotate(2, 0, 0, 1); data.rightLeg.rotation.rotate(5, 0, 1, 0);
      data.leftLeg.rotation.orient(0, 1, 0, 0); data.leftLeg.rotation.rotate(-2, 0, 0, 1); data.leftLeg.rotation.rotate(-5, 0, 1, 0);
      data.rightForeLeg.rotation.orient(4, 1, 0, 0); data.leftForeLeg.rotation.orient(4, 1, 0, 0);
      data.rightForeArm.rotation.orient(-4, 1, 0, 0); data.leftForeArm.rotation.orient(-4, 1, 0, 0);
      data.head.rotation.orientX(data.headPitch.get()).rotateY(data.headYaw.get());
      var phase = DataUpdateHandler.getTicks() / 10;
      data.body.rotation.setSmoothness(1).orientX(((MathHelper.cos(phase) - 1) / 2) * -3);
      data.rightArm.rotation.setSmoothness(0.4).orientX(0).rotateZ(MathHelper.cos(phase + PI / 2) * -2.5 + 2.5);
      data.leftArm.rotation.setSmoothness(0.4).orientX(0).rotateZ(MathHelper.cos(phase + PI / 2) * 2.5 - 2.5);
      var touchdown = Math.min(data.getTicksAfterTouchdown() * this.kneelDuration, 1);
      if (touchdown < 1) {
        data.body.rotation.setSmoothness(1); data.body.rotation.orient(20 * (1 - touchdown), 1, 0, 0);
        data.globalOffset.setY(-Math.sin(touchdown * PI) * 2);
      }
    }
  });
  var WalkAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3); data.globalOffset.slideToZero(0.3);
      data.centerRotation.setSmoothness(0.3).orientZero(); data.renderRotation.setSmoothness(0.3).orientZero();
      data.renderRightItemRotation.setSmoothness(0.3).orientZero(); data.renderLeftItemRotation.setSmoothness(0.3).orientZero();
      var limbSwing = data.limbSwing.get() * 0.6662, armSwingAmount = data.limbSwingAmount.get() * 0.5 / PI * 180;
      data.rightArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing + PI) * armSwingAmount).rotateZ(5);
      data.leftArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing) * armSwingAmount).rotateZ(-5);
      var legSwingAmount = 0.7 * data.limbSwingAmount.get() / PI * 180;
      data.rightLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing) * legSwingAmount).rotateZ(2);
      data.leftLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing + PI) * legSwingAmount).rotateZ(-2);
      var v = (limbSwing / PI) % 2;
      data.leftForeLeg.rotation.setSmoothness(0.5).orientX(v > 1 ? 45 : 0);
      data.rightForeLeg.rotation.setSmoothness(0.5).orientX(v > 1 ? 0 : 45);
      data.leftForeArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing + PI / 2) * -10 - 10);
      data.rightForeArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing) * -10 - 10);
      var bodyRotationY = MathHelper.cos(limbSwing) * -20, bodyRotationX = MathHelper.cos(limbSwing * 2) * 5 + 3;
      var var10 = data.headYaw.get() * 0.1; var10 = Math.max(-10, Math.min(var10, 10));
      data.body.rotation.setSmoothness(0.5).orientY(bodyRotationY).rotateX(bodyRotationX).rotateZ(-var10);
      data.head.rotation.setSmoothness(0.5).orientX(data.headPitch.get() - bodyRotationX).rotateY(data.headYaw.get() - bodyRotationY);
      data.globalOffset.slideY(MathHelper.cos(limbSwing * 2) * 0.6);
      var touchdown = Math.min(data.getTicksAfterTouchdown() * 0.15, 1);
      if (touchdown < 1) {
        data.body.rotation.setSmoothness(1); data.body.rotation.orient(20 * (1 - touchdown), 1, 0, 0);
        data.globalOffset.setY(-Math.sin(touchdown * PI) * 2);
      }
    }
  });
  var SprintAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3); data.globalOffset.slideToZero(0.1);
      data.centerRotation.setSmoothness(0.3).orientZero(); data.renderRotation.setSmoothness(0.3).orientZero();
      data.renderRightItemRotation.setSmoothness(0.3).orientZero(); data.renderLeftItemRotation.setSmoothness(0.3).orientZero();
      var headPitch = data.headPitch.get(), headYaw = data.headYaw.get();
      var limbSwing = data.limbSwing.get() * 0.6662 * 0.8, armSwingAmount = data.limbSwingAmount.get() / PI * 180 * 1.1;
      data.rightArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing + PI) * armSwingAmount).rotateZ(5);
      data.leftArm.rotation.setSmoothness(0.8).orientX(MathHelper.cos(limbSwing) * armSwingAmount).rotateZ(-5);
      var legSwingAmount = 1.26 * data.limbSwingAmount.get() / PI * 180;
      data.rightLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing) * legSwingAmount).rotateZ(2);
      data.leftLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing + PI) * legSwingAmount).rotateZ(-2);
      var foreLegSwingAmount = 0.7 * data.limbSwingAmount.get() / PI * 180, v = (limbSwing / PI) % 2;
      data.leftForeLeg.rotation.setSmoothness(0.7).orientX(40 + MathHelper.cos(limbSwing + 1.8) * foreLegSwingAmount);
      data.rightForeLeg.rotation.setSmoothness(0.7).orientX(40 + MathHelper.cos(limbSwing + PI + 1.8) * foreLegSwingAmount);
      data.leftForeArm.rotation.setSmoothness(0.3).orientX(v > 1 ? -10 : -45);
      data.rightForeArm.rotation.setSmoothness(0.3).orientX(v > 1 ? -45 : -10);
      var bodyRotationY = MathHelper.cos(limbSwing) * -40, bodyRotationX = MathHelper.cos(limbSwing * 2) * 10 + 10;
      var var10 = headYaw * 0.3; var10 = Math.max(-10, Math.min(var10, 10));
      data.body.rotation.setSmoothness(0.8).orientY(bodyRotationY).rotateX(bodyRotationX).rotateZ(-var10);
      data.head.rotation.setSmoothness(0.5).orientX(headPitch - bodyRotationX).rotateY(headYaw - bodyRotationY);
      data.globalOffset.slideY(MathHelper.cos(limbSwing * 2 + 0.6) * 1.5, 0.9);
    }
  });
  var JumpAnimationBit = bit({
    onPlay: function (data) {
      data.renderRotation.identity(); data.centerRotation.identity();
      data.body.rotation.orientInstantX(20);
      data.rightLeg.rotation.orientInstantX(0); data.leftLeg.rotation.orientInstantX(0);
      data.rightForeLeg.rotation.orientInstantX(0); data.leftForeLeg.rotation.orientInstantX(0);
      data.rightArm.rotation.orientInstantZ(2); data.leftArm.rotation.orientInstantZ(-2);
      data.rightForeArm.rotation.orientInstantX(-20); data.leftForeArm.rotation.orientInstantX(-20);
    },
    perform: function (data) {
      if (data.prevMotionY < 0 && data.motionY > 0) this.onPlay(data);
      data.globalOffset.slideToZero(0.3);
      data.renderRotation.setSmoothness(0.3).orientZero(); data.centerRotation.setSmoothness(0.7).orientZero();
      data.renderRightItemRotation.setSmoothness(0.3).orientZero(); data.renderLeftItemRotation.setSmoothness(0.3).orientZero();
      var bodyRotationX = Math.max(1 - data.ticksInAir * 0.1, 0);
      data.body.rotation.setSmoothness(0.2).orientX(bodyRotationX);
      data.rightArm.rotation.setSmoothness(0.05).orientZ(45); data.leftArm.rotation.setSmoothness(0.05).orientZ(-45);
      data.rightForeArm.rotation.setSmoothness(0.3).orientX(0); data.leftForeArm.rotation.setSmoothness(0.3).orientX(0);
      data.head.rotation.orientX(data.headPitch.get() - bodyRotationX).rotateY(data.headYaw.get());
      if (!data.isStillHorizontally()) {
        var limbSwing = data.limbSwing.get() * 0.6662, limbSwingAmount = 0.7 * data.limbSwingAmount.get() / PI * 180;
        data.rightLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing) * limbSwingAmount);
        data.leftLeg.rotation.setSmoothness(1).orientX(-5 + MathHelper.cos(limbSwing + PI) * limbSwingAmount);
        var lv = (limbSwing / PI) % 2;
        data.leftForeLeg.rotation.setSmoothness(0.3).orientX(lv > 1 ? 45 : 0);
        data.rightForeLeg.rotation.setSmoothness(0.3).orientX(lv > 1 ? 0 : 45);
        data.leftForeArm.rotation.setSmoothness(0.3).orientX((MathHelper.cos(limbSwing + PI / 2) / 2 + 0.5) * -20);
        data.rightForeArm.rotation.setSmoothness(0.3).orientX((MathHelper.cos(limbSwing) / 2 + 0.5) * -20);
      } else {
        data.rightLeg.rotation.setSmoothness(0.1).orientZ(10); data.rightLeg.rotation.setSmoothness(0.3).rotateX(-45);
        data.leftLeg.rotation.setSmoothness(0.1).orientZ(-10); data.leftLeg.rotation.setSmoothness(0.3).rotateX(-17);
        data.rightForeLeg.rotation.setSmoothness(0.3).orientX(70); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(17);
      }
    }
  });
  var SneakAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3); data.globalOffset.slideY(-1.3);
      var limbSwing = data.limbSwing.get() * 0.6662, limbSwingAmount = data.limbSwingAmount.get() * 1.4 * 1.1 / PI * 180, v = (limbSwing / PI) % 2;
      data.rightLeg.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing) * limbSwingAmount - 5).rotateZ(10);
      data.leftLeg.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing + PI) * limbSwingAmount - 5).rotateZ(-10);
      data.rightArm.rotation.setSmoothness(0.8).orientX(20 * MathHelper.cos(limbSwing + PI) - 20).rotateZ(10);
      data.leftArm.rotation.setSmoothness(0.8).orientX(20 * MathHelper.cos(limbSwing) - 20).rotateZ(-10);
      data.leftForeLeg.rotation.setSmoothness(0.3).orientX(v > 1 ? 45 : 10);
      data.rightForeLeg.rotation.setSmoothness(0.3).orientX(v > 1 ? 10 : 45);
      var var2 = 25 + MathHelper.cos(limbSwing * 2) * 5;
      data.body.rotation.localRotateX(var2); data.head.rotation.rotateX(-var2);
    }
  });
  var FallingAnimationBit = bit({
    perform: function (data) {
      data.centerRotation.setSmoothness(0.3).orientZero();
      data.head.rotation.orientX(data.headPitch.get()).rotateY(data.headYaw.get());
      data.body.rotation.orientY(0).setSmoothness(0.5);
      var ticks = DataUpdateHandler.getTicks() * 0.5, delay = 1, armSpan = 20, legSpan = 10;
      var transition = MathHelper.clamp((data.getTicksFalling() - 10) / 80, 0, 1), s = transition * 0.9;
      data.leftArm.rotation.setSmoothness(s).orientZ(-90 + MathHelper.sin(ticks) * armSpan).rotateY(MathHelper.cos(ticks) * armSpan);
      data.rightArm.rotation.setSmoothness(s).orientZ(90 + MathHelper.sin(ticks + delay) * armSpan).rotateY(MathHelper.cos(ticks + delay) * armSpan);
      data.leftForeArm.rotation.setSmoothness(s).orientX(-15); data.rightForeArm.rotation.setSmoothness(s).orientX(-15);
      data.leftLeg.rotation.setSmoothness(s).orientX(MathHelper.sin(ticks) * legSpan).rotateZ(-20 + MathHelper.cos(ticks) * legSpan);
      data.rightLeg.rotation.setSmoothness(s).orientX(MathHelper.sin(ticks + delay) * legSpan).rotateZ(20 + MathHelper.cos(ticks + delay) * legSpan);
      data.leftForeLeg.rotation.setSmoothness(s).orientX(20); data.rightForeLeg.rotation.setSmoothness(s).orientX(20);
      data.renderRotation.setSmoothness(s).orientX(20); data.head.rotation.setSmoothness(s).rotateX(-20);
    }
  });
  FallingAnimationBit.TICKS_BEFORE_FALLING = 10;
  var LadderClimbAnimationBit = bit({
    perform: function (data) {
      var living = data.getEntity();
      data.centerRotation.setSmoothness(0.3).orientZero();
      var off = PI, p = data.getClimbingCycle();
      var armSwingRight = Math.sin(p) * 0.5 + 0.5, armSwingLeft = Math.sin(p + PI) * 0.5 + 0.5;
      var armSwingRight2 = Math.sin(p - 0.3) * 0.5 + 0.5, armSwingLeft2 = Math.sin(p + PI - 0.3) * 0.5 + 0.5;
      var armSwingDouble = Math.sin(p * 2) * 0.5 + 0.5, armSwingDouble2 = Math.sin(p * 2 - 1.8) * 0.5 + 0.5;
      var legSwingRight = Math.sin(p + off) * 0.5 + 0.5, legSwingLeft = Math.sin(p + off + PI) * 0.5 + 0.5;
      var legSwingRight2 = Math.sin(p + off + 0.3) * 0.5 + 0.5, legSwingLeft2 = Math.sin(p + off + PI + 0.3) * 0.5 + 0.5;
      var armOrientX = -45, climbingRotation = data.getClimbingRotation();
      var renderRotationY = MathHelper.wrapDegrees(N.entity.rotationYaw(living) - data.headYaw.get() - climbingRotation);
      data.renderRotation.setSmoothness(0.6).orientY(renderRotationY);
      data.localOffset.slideZ(armSwingDouble2, 0.6);
      data.body.rotation.setSmoothness(0.5).orientX(armSwingDouble * 10);
      data.rightArm.rotation.setSmoothness(0.5).orientX(-90 + armOrientX + armSwingRight * 70);
      data.leftArm.rotation.setSmoothness(0.5).orientX(-90 + armOrientX + armSwingLeft * 70);
      data.rightForeArm.rotation.setSmoothness(0.5).orientX(armSwingRight2 * -80);
      data.leftForeArm.rotation.setSmoothness(0.5).orientX(armSwingLeft2 * -80);
      data.rightLeg.rotation.setSmoothness(0.5).orientX(-45 - legSwingRight * 50);
      data.leftLeg.rotation.setSmoothness(0.5).orientX(-45 - legSwingLeft * 50);
      data.rightForeLeg.rotation.setSmoothness(0.5).orientX(20 + legSwingRight2 * 90);
      data.leftForeLeg.rotation.setSmoothness(0.5).orientX(20 + legSwingLeft2 * 90);
      data.head.rotation.orientX(data.headPitch.get()).rotateY(GUtil.clamp(MathHelper.wrapDegrees(data.headYaw.get() + renderRotationY), -90, 90));
      var ledge = data.getLedgeHeight();
      if (ledge >= 0.6) {
        var armRotX = ledge - 0.6;
        data.body.rotation.setSmoothness(0.5).orientX(armRotX * 50);
        data.rightArm.rotation.setSmoothness(0.5).orientX(-100 + armRotX * 40);
        data.leftArm.rotation.setSmoothness(0.5).orientX(-100 + armRotX * 40);
        data.rightForeArm.rotation.setSmoothness(0.5).orientX(-10);
        data.leftForeArm.rotation.setSmoothness(0.5).orientX(-10);
      }
    }
  });
  var RidingAnimationBit = bit({
    perform: function (data) {
      var living = data.getEntity(), E = N.entity;
      data.localOffset.slideToZero(0.3);
      data.renderRotation.orientZero(); data.centerRotation.setSmoothness(0.3).orientZero();
      data.renderLeftItemRotation.orientZero(); data.renderRightItemRotation.orientZero();
      data.head.rotation.orientX(data.headPitch.get()).rotateY(data.headYaw.get());
      data.body.rotation.orientY(0).setSmoothness(0.5);
      data.leftLeg.rotation.orientX(-90).rotateZ(-10).rotateY(-25);
      data.rightLeg.rotation.orientX(-90).rotateZ(10).rotateY(25);
      data.leftForeLeg.rotation.orientX(60); data.rightForeLeg.rotation.orientX(60);
      data.leftArm.rotation.orientX(0).rotateZ(-10); data.leftForeArm.rotation.orientX(-10);
      data.rightArm.rotation.orientX(0).rotateZ(10); data.rightForeArm.rotation.orientX(-10);
      var ridden = E.ridingEntity(living);
      if (ridden != null && E.isLiving(ridden)) {
        var relativeHeadYaw = MathHelper.wrapDegrees(E.rotationYaw(living) - E.renderYawOffset(ridden));
        var relativeYaw = MathHelper.wrapDegrees(E.rotationYaw(living) - data.headYaw.get() - E.renderYawOffset(ridden));
        data.body.rotation.orientZ(MathHelper.clamp(-relativeHeadYaw * 0.25, -20, 20));
        data.leftLeg.rotation.rotateX(-MathHelper.sin(relativeYaw / 180 * PI * 1.5) * 45);
        data.rightLeg.rotation.rotateX(MathHelper.sin(relativeYaw / 180 * PI * 1.5) * 45);
      }
      if (!data.isStillHorizontally()) {
        data.body.rotation.orientX(25);
        data.leftArm.rotation.orientX(-45).rotateZ(10); data.leftForeArm.rotation.orientX(-10);
        data.rightArm.rotation.orientX(-45).rotateZ(-10); data.rightForeArm.rotation.orientX(-10);
        var mx = E.motionX(living), mz = E.motionZ(living), motionMagnitude = Math.sqrt(mx * mx + mz * mz) * 100;
        if (motionMagnitude > 1) {
          var ticks = DataUpdateHandler.getTicks() * 0.5, bodyRotation = 45 + MathHelper.cos(ticks) * 10;
          data.body.rotation.orientX(bodyRotation); data.head.rotation.rotateX(-bodyRotation);
          data.leftArm.rotation.rotateX(-bodyRotation); data.rightArm.rotation.rotateX(-bodyRotation);
          data.globalOffset.slideY(MathHelper.sin(ticks) * 0.3);
        } else data.head.rotation.rotateX(-25);
      }
    }
  });
  var SittingAnimationBit = bit({
    perform: function (data) {
      data.centerRotation.setSmoothness(0.3).orientZero();
      data.head.rotation.orientX(data.headPitch.get()).rotateY(data.headYaw.get());
      data.body.rotation.orientY(0).setSmoothness(0.5);
      data.leftLeg.rotation.orientX(-90).rotateZ(-10).rotateY(-15);
      data.rightLeg.rotation.orientX(-90).rotateZ(10).rotateY(15);
      data.leftForeLeg.rotation.orientX(10); data.rightForeLeg.rotation.orientX(10);
      data.leftArm.rotation.orientX(0).rotateZ(-10); data.leftForeArm.rotation.orientX(-10);
      data.rightArm.rotation.orientX(0).rotateZ(10); data.rightForeArm.rotation.orientX(-10);
      data.renderRotation.orientZero(); data.renderLeftItemRotation.orientZero(); data.renderRightItemRotation.orientZero();
    }
  });
  var SwimmingAnimationBit = bit({
    onPlay: function () { this.transformTransition = 0; this.transitionSpeed = 0.1; },
    perform: function (data) {
      var ticks = DataUpdateHandler.getTicks();
      var armSway = (MathHelper.cos(ticks * 0.1625) + 1) / 2, armSway2 = (-MathHelper.sin(ticks * 0.1625) + 1) / 2;
      var legFlap = MathHelper.cos(ticks * 0.4625), foreArmSway = ((ticks * 0.1625) % TWO_PI) / TWO_PI;
      var foreArmStretch = Math.max(armSway * 2 - 1, 0);
      var t = Tween.easeInOut(this.transformTransition, 3);
      if (data.isStillHorizontally() || data.isDrawingBow() || data.getTicksAfterAttack() < 10 || !data.isUnderwater()) {
        if (this.transformTransition > 0) { this.transformTransition -= DataUpdateHandler.ticksPerFrame * this.transitionSpeed; this.transformTransition = Math.max(0, this.transformTransition); }
        armSway = (MathHelper.cos(ticks * 0.0825) + 1) / 2; armSway2 = (-MathHelper.sin(ticks * 0.0825) + 1) / 2; legFlap = MathHelper.cos(ticks * 0.2625);
        data.leftArm.rotation.setSmoothness(0.3).orientX(armSway2 * 30 - 15).rotateZ(-armSway * 30);
        data.rightArm.rotation.setSmoothness(0.3).orientX(armSway2 * 30 - 15).rotateZ(armSway * 30);
        data.leftForeArm.rotation.setSmoothness(0.3).orientX(armSway2 * -40); data.rightForeArm.rotation.setSmoothness(0.3).orientX(armSway2 * -40);
        data.leftLeg.rotation.setSmoothness(0.3).orientX(legFlap * 40); data.rightLeg.rotation.setSmoothness(0.3).orientX(-legFlap * 40);
        data.leftForeLeg.rotation.setSmoothness(0.4).orientX(5); data.rightForeLeg.rotation.setSmoothness(0.4).orientX(5);
        data.body.rotation.orientX(armSway * 10);
      } else {
        if (this.transformTransition < 1) { this.transformTransition += DataUpdateHandler.ticksPerFrame * this.transitionSpeed; this.transformTransition = Math.min(this.transformTransition, 1); }
        data.leftArm.rotation.setSmoothness(0.3).orientX(armSway * -120).rotateY(-90 * t).rotateX(armSway * 20);
        data.rightArm.rotation.setSmoothness(0.3).orientX(armSway * -120).rotateY(90 * t).rotateX(armSway * 20);
        var fore = (foreArmSway < 0.55 || foreArmSway > 0.9) ? foreArmStretch * -60 : -60;
        data.leftForeArm.rotation.setSmoothness(0.3).orientX(fore); data.rightForeArm.rotation.setSmoothness(0.3).orientX(fore);
        data.leftLeg.rotation.setSmoothness(0.3).orientX(legFlap * 40); data.rightLeg.rotation.setSmoothness(0.3).orientX(-legFlap * 40);
        data.leftForeLeg.rotation.setSmoothness(0.4).orientX(5); data.rightForeLeg.rotation.setSmoothness(0.4).orientX(5);
        data.body.rotation.setSmoothness(0.5).orientX(armSway * -20);
        data.renderRightItemRotation.setSmoothness(0.3).orientX(armSway * 50);
      }
      data.head.rotation.setSmoothness(1).orientX(data.headPitch.get()).rotateY(data.headYaw.get()).rotateX(-80 * t);
      data.renderRotation.setSmoothness(0.7).orientX(t * 80);
      data.globalOffset.slideZ(-20 * t, 0.7); data.globalOffset.slideY(14 * t, 0.7);
      data.localOffset.slideToZero(0.3);
    }
  }, function () { AnimationBit.call(this); this.transformTransition = 0; this.transitionSpeed = 0.1; });
  var TorchHoldingAnimationBit = bit({
    perform: function (data) {
      var living = data.getEntity(), E = N.entity, right = E.primaryHandRight(living), torchHand = null;
      if (N.item.isTorch(E.mainItem(living))) torchHand = right ? "right" : "left";
      else if (N.item.isTorch(E.offItem(living))) torchHand = right ? "left" : "right";
      if (torchHand == null) return;
      var mainArm = torchHand === "right" ? data.rightArm : data.leftArm, mainForeArm = torchHand === "right" ? data.rightForeArm : data.leftForeArm;
      mainArm.getRotation().orientX(-90 + data.headPitch.get() * 0.5).rotateY(data.headYaw.get() * 0.7);
      mainForeArm.getRotation().orientX(-5);
    }
  });
  var EatingAnimationBit = bit({
    onPlay: function () { this.bringUpAnimation = 0; },
    perform: function (data) {
      var ticks = DataUpdateHandler.getTicks(), right = this.actionHand === "right", dir = right ? 1 : -1;
      var mainArm = right ? data.rightArm : data.leftArm, mainForeArm = right ? data.rightForeArm : data.leftForeArm;
      if (this.bringUpAnimation < 1) { this.bringUpAnimation += DataUpdateHandler.ticksPerFrame * 0.15; this.bringUpAnimation = Math.min(this.bringUpAnimation, 1); }
      else { var wiggle = MathHelper.cos(ticks * 1); data.head.rotation.orientX(wiggle * 5).rotateY(15 * dir); }
      mainArm.rotation.orientX(this.bringUpAnimation * -80).rotateZ(45 * this.bringUpAnimation * dir);
      mainForeArm.rotation.orientX(this.bringUpAnimation * -45);
    }
  }, function (hand) { AnimationBit.call(this); this.actionHand = hand; this.bringUpAnimation = 0; });
  var ShieldAnimationBit = bit({
    onPlay: function () { this.bringUpAnimation = 0; },
    perform: function (data) {
      var right = this.actionHand === "right", dir = right ? 1 : -1;
      var mainArm = right ? data.rightArm : data.leftArm, mainForeArm = right ? data.rightForeArm : data.leftForeArm;
      if (this.bringUpAnimation < 1) { this.bringUpAnimation += DataUpdateHandler.ticksPerFrame * 0.7; this.bringUpAnimation = Math.min(this.bringUpAnimation, 1); }
      mainArm.rotation.orientX(this.bringUpAnimation * 0).rotateY(-45 * this.bringUpAnimation * dir);
      mainForeArm.rotation.orientX(this.bringUpAnimation * -45);
    }
  }, function (hand) { AnimationBit.call(this); this.actionHand = hand; this.bringUpAnimation = 0; });
  var FistGuardAnimationBit = bit({
    perform: function (data) {
      var dir = N.entity.primaryHandRight(data.getEntity()) ? 1 : -1;
      if (!data.isStillHorizontally()) return;
      data.globalOffset.slideY(-2);
      data.renderRotation.setSmoothness(0.3).orientY(-20 * dir);
      data.rightArm.rotation.setSmoothness(0.3).orientX(-90).rotateZ(20); data.rightForeArm.rotation.setSmoothness(0.3).orientX(-80);
      data.leftArm.rotation.setSmoothness(0.3).orientX(-90).rotateZ(-20); data.leftForeArm.rotation.setSmoothness(0.3).orientX(-80);
      data.body.rotation.rotateX(10);
      data.rightLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(10);
      data.leftLeg.rotation.setSmoothness(0.3).orientX(-30).rotateY(-25).rotateZ(-10);
      data.rightForeLeg.rotation.setSmoothness(0.3).orientX(30); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(30);
      data.head.rotation.rotateX(-10); data.head.rotation.rotateY(-20 * dir);
    }
  });
  var AttackStanceAnimationBit = bit({
    kneelDuration: 0.15,
    onPlay: function () { this.legSpreadAnimation = 0; },
    perform: function (data) {
      var h = hands(data), dir = h.dir;
      var breath0 = Math.sin(DataUpdateHandler.getTicks() / 5), breath1 = Math.cos(DataUpdateHandler.getTicks() / 5.7);
      data.renderRotation.setSmoothness(0.3).orientY(-30 * dir);
      var bodyRotationX = 20 + breath0 * 2;
      data.body.rotation.setSmoothness(0.3).orientX(bodyRotationX);
      data.head.rotation.rotateY(-30 * dir); data.head.rotation.rotateX(-bodyRotationX);
      data.rightLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(10).rotateY(25);
      data.leftLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(-10).rotateY(-25);
      data.rightForeLeg.rotation.setSmoothness(0.3).orientX(30); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(30);
      h.mainArm.getRotation().setSmoothness(0.3).orientZ(60 * dir + breath0 * 5).rotateY(breath1 * 5);
      h.offArm.getRotation().setSmoothness(0.3).orientZ(-60 * dir + breath1 * 5);
      h.mainForeArm.getRotation().setSmoothness(0.3).orientX(-20); h.offForeArm.getRotation().setSmoothness(0.3).orientX(-60);
      h.mainItemRotation.setSmoothness(0.3).orientX(65);
      data.globalOffset.slideY(-2);
      var touchdown = Math.min(data.getTicksAfterTouchdown() * this.kneelDuration, 1);
      if (touchdown < 1) {
        data.body.rotation.setSmoothness(1); data.body.rotation.orientX(5 * (1 - touchdown) + 15);
        data.globalOffset.setY(-MathHelper.sin(touchdown * PI) * 2 - 2);
      }
    }
  }, function () { AnimationBit.call(this); this.legSpreadAnimation = 0; });
  var AttackStanceSprintAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3);
      var h = hands(data), dir = h.dir;
      if (holdsSword(data.getEntity())) data.swordTrail.add(data, 0, 0, -10);
      data.body.rotation.rotateY(20 * dir); data.head.rotation.rotateY(-20 * dir);
      h.mainArm.getRotation().orientZ(60 * dir); h.mainArm.getRotation().rotateY(60 * dir);
      h.offArm.getRotation().rotateZ(-30 * dir);
      if (h.right) data.renderRightItemRotation.setSmoothness(0.3).orientX(45); else data.renderLeftItemRotation.setSmoothness(0.3).orientX(45);
    }
  });
  function slashCommon(data, h, bodyX, bodyY, smoothHead) {
    data.body.rotation.setSmoothness(0.9).orientX(bodyX).orientY(bodyY);
    var head = data.head.rotation; if (smoothHead) head.setSmoothness(0.9);
    head.orientX(MathHelper.wrapDegrees(data.headPitch.get()) - bodyX).rotateY(MathHelper.wrapDegrees(data.headYaw.get()) - bodyY);
  }
  var AttackSlashUpAnimationBit = bit({
    onPlay: function (data) { data.swordTrail.reset(); },
    perform: function (data) {
      data.localOffset.slideToZero(0.3);
      var living = data.getEntity(), h = hands(data), dir = h.dir;
      if (data.getTicksAfterAttack() < 4 && holdsSword(living)) data.swordTrail.add(data);
      var armSwing = Math.min(data.getTicksAfterAttack() / 10 * 3, 1);
      var bx = 20 - armSwing * 20, by = -70 * armSwing * dir;
      slashCommon(data, h, bx, by, true);
      h.mainArm.getRotation().setSmoothness(0.9).orientZ(110 * armSwing * dir).rotateY((60 - armSwing * 180) * dir);
      h.offArm.getRotation().setSmoothness(0.3).orientZ(-20 * dir);
      h.mainForeArm.getRotation().setSmoothness(0.3).orientX(-20); h.offForeArm.getRotation().setSmoothness(0.3).orientX(-60);
      if (data.isStillHorizontally() && !N.entity.isRiding(living)) {
        data.rightLeg.rotation.orientZ(5).rotateY(15).rotateX(-20); data.leftLeg.rotation.orientZ(-5).rotateY(-15).rotateX(-20);
        data.rightForeLeg.rotation.orientX(25); data.renderRotation.setSmoothness(0.3).orientY(0 * dir); data.globalOffset.slideY(-1);
      }
      h.mainItemRotation.setSmoothness(0.9).orientInstantX(180);
    }
  });
  function slashDownOutward(outward) {
    return bit({
      onPlay: function (data) { data.swordTrail.reset(); this.ticksPlayed = 0; },
      perform: function (data) {
        data.localOffset.slideToZero(0.3);
        var living = data.getEntity(), h = hands(data), dir = h.dir;
        if (data.getTicksAfterAttack() < 4 && holdsSword(living)) data.swordTrail.add(data);
        var attackState = this.ticksPlayed / 10, armSwing = GUtil.clamp(attackState * 3, 0, 1);
        var bx = 20 - attackState * 20, by = (30 + 10 * attackState) * dir;
        slashCommon(data, h, bx, by, true);
        h.mainArm.getRotation().setSmoothness(0.3).orientZ((outward ? 70 + armSwing * 40 : 60) * dir).rotateInstantY((-20 + armSwing * 70) * dir);
        h.offArm.getRotation().setSmoothness(0.3).orientZ(-80 * dir);
        h.mainForeArm.getRotation().setSmoothness(0.3).orientX(-20); h.offForeArm.getRotation().setSmoothness(0.3).orientX(-60);
        if (data.isStillHorizontally() && !N.entity.isRiding(living)) {
          data.rightLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(10).rotateY(25);
          data.leftLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(-10).rotateY(-25);
          data.rightForeLeg.rotation.setSmoothness(0.3).orientX(30); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(30);
          data.head.rotation.rotateY(-30 * dir); data.globalOffset.slideY(-2); data.renderRotation.setSmoothness(0.3).orientY(-30 * dir);
        }
        h.mainItemRotation.orientInstantX(90);
        this.ticksPlayed += DataUpdateHandler.ticksPerFrame;
      }
    }, function () { AnimationBit.call(this); this.ticksPlayed = 0; });
  }
  var AttackSlashDownAnimationBit = slashDownOutward(false), AttackSlashOutwardAnimationBit = slashDownOutward(true);
  var AttackSlashInwardAnimationBit = bit({
    onPlay: function (data) { data.swordTrail.reset(); },
    perform: function (data) {
      data.localOffset.slideToZero(0.3);
      var living = data.getEntity(), h = hands(data), dir = h.dir;
      if (data.getTicksAfterAttack() < 4 && holdsSword(living)) data.swordTrail.add(data);
      var armSwing = Math.min(data.getTicksAfterAttack() / 10 * 3, 1);
      var bx = 20 - armSwing * 20, by = -70 * armSwing * dir;
      slashCommon(data, h, bx, by, true);
      h.mainArm.getRotation().setSmoothness(0.9).orientZ(90 * dir).rotateY((60 - armSwing * 180) * dir);
      h.offArm.getRotation().setSmoothness(0.3).orientZ(-20 * dir);
      h.mainForeArm.getRotation().setSmoothness(0.3).orientX(-10); h.offForeArm.getRotation().setSmoothness(0.3).orientX(-60);
      if (data.isStillHorizontally() && !N.entity.isRiding(living)) {
        data.rightLeg.rotation.orientZ(5).rotateY(15).rotateX(-20); data.leftLeg.rotation.orientZ(-5).rotateY(-15).rotateX(-20);
        data.rightForeLeg.rotation.orientX(25); data.renderRotation.setSmoothness(0.3).orientY(0 * dir); data.globalOffset.slideY(-1);
      }
      h.mainItemRotation.setSmoothness(0.9).orientInstantX(50);
    }
  });
  var AttackWhirlSlashAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3);
      var living = data.getEntity(), h = hands(data), dir = h.dir;
      if (data.getTicksAfterAttack() < 0.5) data.swordTrail.reset();
      if (N.entity.mainItem(living) != null) data.swordTrail.add(data);
      var attackState = data.getTicksAfterAttack() / 10, armSwing = Math.min(attackState * 2, 1), var5 = GUtil.clamp(attackState * 1.6, 0, 1);
      var bx = 20 - attackState * 20, by = 20 * attackState * dir;
      data.body.rotation.setSmoothness(0.9).orientX(bx).orientY(by);
      data.head.rotation.orientX(MathHelper.wrapDegrees(data.headPitch.get()) - bx).rotateY(MathHelper.wrapDegrees(data.headYaw.get()) - by - 30 * dir);
      h.offArm.getRotation().setSmoothness(0.3).orientZ(20 * dir);
      h.offArm.getRotation().setSmoothness(0.3).orientZ(-80 * dir);
      h.mainArm.getRotation().setSmoothness(0.3).orientZ(-(-10 - var5 * 120) * dir).rotateInstantY((-20 + armSwing * 70) * dir);
      h.mainForeArm.getRotation().setSmoothness(0.3).orientX(-20); h.offForeArm.getRotation().setSmoothness(0.3).orientX(-60);
      if (data.isStillHorizontally()) {
        data.rightLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(10).rotateY(25);
        data.leftLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(-10).rotateY(-25);
        data.rightForeLeg.rotation.setSmoothness(0.3).orientX(30); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(30);
      }
      data.globalOffset.slideY(-2);
      h.mainItemRotation.setSmoothness(0.9).orientX(90 * attackState);
      data.renderRotation.orientInstantY(MathHelper.wrapDegrees(-(30 + 360 * var5) * dir));
    }
  });
  var PunchAnimationBit = bit({
    perform: function (data) {
      data.rightArm.rotation.setSmoothness(0.3).orientX(-90).rotateZ(20);
      data.leftArm.rotation.setSmoothness(0.3).orientZ(-20).rotateX(-90);
      data.rightForeArm.rotation.setSmoothness(0.3).orientX(-80); data.leftForeArm.rotation.setSmoothness(0.3).orientX(-80);
      var renderRotationY = 0;
      if (data.isStillHorizontally()) {
        renderRotationY = -20; data.globalOffset.slideY(-2);
        data.rightLeg.rotation.setSmoothness(0.3).orientX(-30).rotateZ(10);
        data.leftLeg.rotation.setSmoothness(0.3).orientX(-30).rotateY(-25).rotateZ(-10);
        data.rightForeLeg.rotation.setSmoothness(0.3).orientX(30); data.leftForeLeg.rotation.setSmoothness(0.3).orientX(30);
      }
      if (this.fistPunchArm === "right") {
        data.rightArm.rotation.setSmoothness(0.9).orientY(-90).rotateX(-90 + data.headPitch.get()).rotateY(10);
        data.rightForeArm.rotation.setSmoothness(0.9).orientX(0);
        data.body.rotation.setSmoothness(0.6).orientY(-20 + renderRotationY); data.head.rotation.rotateY(20);
      } else {
        data.leftArm.rotation.setSmoothness(0.9).orientY(100).rotateX(-90 + data.headPitch.get()).rotateY(-16);
        data.leftForeArm.rotation.setSmoothness(0.9).orientX(0);
        data.body.rotation.setSmoothness(0.6).orientY(20 + renderRotationY); data.head.rotation.rotateY(-20);
      }
      data.renderRotation.orientY(renderRotationY);
    }
  }, function (arm) { AnimationBit.call(this); this.fistPunchArm = arm; });

  // ---- biped item actions (BipedActionController, BowAction, PunchingAction, SwordAction, ToolAction)
  var BowAction = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3);
      var living = data.getEntity(), headPitch = data.headPitch.get(), headYaw = data.headYaw.get(), right = this.actionHand === "right", dir = right ? 1 : -1;
      var mainArm = right ? data.rightArm : data.leftArm, offArm = right ? data.leftArm : data.rightArm;
      var mainForeArm = right ? data.rightForeArm : data.leftForeArm, offForeArm = right ? data.leftForeArm : data.rightForeArm;
      var aimed = living != null ? Math.min(N.entity.itemInUseMaxCount(living), 15) : 0;
      var bodyTwistY = (((aimed - 10) / 5) * -25) * dir, var2 = aimed / 10, var5 = Math.max(headPitch - 90, -160);
      var bodyRotationY = -bodyTwistY + headYaw;
      if (data.isClimbing()) {
        var renderRotationY = MathHelper.wrapDegrees(N.entity.rotationYaw(living) - headYaw - data.getClimbingRotation());
        bodyRotationY = MathHelper.wrapDegrees(headYaw + renderRotationY);
        data.head.rotation.setSmoothness(0.5).orientX(headPitch);
      } else data.head.rotation.setSmoothness(0.5).orientX(headPitch).rotateY(headYaw - bodyRotationY);
      data.body.rotation.setSmoothness(0.8).orientY(bodyRotationY);
      mainArm.rotation.setSmoothness(0.8).orientX(headPitch - 90).rotateY(bodyTwistY);
      offArm.rotation.setSmoothness(1).orientY(80 * dir).rotateZ((-MathHelper.cos(headPitch / 180 * PI) * 40 + 40) * dir).rotateX(var5);
      mainForeArm.rotation.setSmoothness(1).orientX(0);
      offForeArm.rotation.orientX(var2 * -30);
    }
  }, function (hand) { AnimationBit.call(this); this.actionHand = hand; });
  var PunchingAction = bit({
    perform: function (data) {
      var ticksAfterAttack = data.getTicksAfterAttack();
      if (ticksAfterAttack < this.lastTicksAfterAttack) this.punchingFist = this.punchingFist === "left" ? "right" : "left";
      this.lastTicksAfterAttack = ticksAfterAttack;
      if (ticksAfterAttack < 10) this.layerBase.playOrContinueBit(this.punchingFist === "left" ? this.bitPunchLeft : this.bitPunchRight, data);
      else if (ticksAfterAttack < 60) this.layerBase.playOrContinueBit(this.bitFistGuard, data);
      else this.layerBase.clearAnimation();
      this.layerBase.perform(data);
    }
  }, function () {
    AnimationBit.call(this); this.layerBase = new HardAnimationLayer(); this.bitFistGuard = new FistGuardAnimationBit();
    this.bitPunchLeft = new PunchAnimationBit("left"); this.bitPunchRight = new PunchAnimationBit("right"); this.punchingFist = "left"; this.lastTicksAfterAttack = 0;
  });
  // SwordAction keeps one shared list of move bits, as the original's static list does.
  var swordMoves = null;
  var SwordAction = bit({
    nextMove: function (data) {
      var b = swordMoves[this.moveId];
      if (b != null) this.layerBase.playBit(b, data); else this.layerBase.clearAnimation();
      this.moveId = (this.moveId + 1) % swordMoves.length;
    },
    perform: function (data) {
      var ticksAfterAttack = data.getTicksAfterAttack();
      if (ticksAfterAttack < this.lastTicksAfterAttack) this.nextMove(data);
      this.lastTicksAfterAttack = ticksAfterAttack;
      var entity = data.getEntity();
      if (ticksAfterAttack > 20) this.moveId = 0;
      if (ticksAfterAttack < 10) {
      } else if (ticksAfterAttack < 60 && data.isOnGround()) {
        if (N.entity.isSprinting(entity)) this.layerBase.playOrContinueBit(this.bitAttackStanceSprint, data);
        else if (data.isStillHorizontally()) this.layerBase.playOrContinueBit(this.bitAttackStance, data);
        else this.layerBase.clearAnimation();
      } else this.layerBase.clearAnimation();
      this.layerBase.perform(data);
    }
  }, function () {
    AnimationBit.call(this);
    if (!swordMoves) swordMoves = [new AttackSlashUpAnimationBit(), new AttackSlashDownAnimationBit(), new AttackSlashInwardAnimationBit(), new AttackSlashOutwardAnimationBit(), new AttackWhirlSlashAnimationBit()];
    this.layerBase = new HardAnimationLayer(); this.bitAttackStance = new AttackStanceAnimationBit(); this.bitAttackStanceSprint = new AttackStanceSprintAnimationBit();
    this.lastTicksAfterAttack = 0; this.moveId = 0;
  });
  var ToolAction = bit({
    perform: function (data) {
      var entity = data.getEntity();
      if (!N.entity.isSwingInProgress(entity)) return;
      var headPitch = data.headPitch.get(), headYaw = data.headYaw.get(), right = this.actionHand === "right", side = right ? 1 : -1;
      var mainArm = right ? data.rightArm : data.leftArm;
      data.localOffset.slideToZero(0.3); data.centerRotation.setSmoothness(0.3).orientZero();
      var swing = data.swingProgress.get(), sq = MathHelper.sqrt(swing) * (PI * 2);
      var bodyYaw = MathHelper.sin(sq) * 30 * side;
      data.body.rotation.setSmoothness(0.8).orientY(bodyYaw);
      var bodyPitch = 0;
      if (N.entity.isSneaking(entity)) { data.body.rotation.rotateX(20); bodyPitch = 20; }
      data.head.rotation.setSmoothness(0.8).orientX(headPitch - bodyPitch).rotateY(headYaw - bodyYaw);
      mainArm.rotation.orientInstantX(MathHelper.sin(sq) * 50 - 30);
      mainArm.rotation.localRotateZ(MathHelper.cos(sq) * -20 + 10).finish();
    }
  }, function (hand) { AnimationBit.call(this); this.actionHand = hand; });
  var USE_ACTIONS = { food: EatingAnimationBit, bow: BowAction, shield: ShieldAnimationBit };
  var ATTACK_ACTIONS = { tool: ToolAction, fists: PunchingAction, sword: SwordAction };
  function BipedActionController() { this.layerAction = new HardAnimationLayer(); this.currentUseActionType = null; this.currentAttackActionType = null; this.actionBit = null; }
  // ModelBiped.ArmPose of a held stack: "empty" | "item" | "block" | "bow"
  BipedActionController.getAction = function (entity, stack) {
    if (!N.item.stackEmpty(stack)) {
      if (N.entity.itemInUseCount(entity) > 0) { var a = N.entity.stackUseAction(stack); if (a === "block") return "block"; if (a === "bow") return "bow"; }
      return "item";
    }
    return "empty";
  };
  BipedActionController.getItemUseAction = function (item, poseMain, poseOff) {
    if (N.item.isAir(item)) return null;
    if (N.item.isFood(item)) return "food";
    if (N.item.isBow(item) || poseMain === "bow" || poseOff === "bow") return "bow";
    if (poseMain === "block" || poseOff === "block") return "shield";
    return "food";
  };
  BipedActionController.getItemAttackAction = function (item) { if (N.item.isSword(item)) return "sword"; if (N.item.isAir(item)) return "fists"; return "tool"; };
  BipedActionController.prototype.perform = function (data, primaryRight, mainStack, offStack, activeItem) {
    var entity = data.getEntity(), E = N.entity;
    var poseMain = BipedActionController.getAction(entity, mainStack), poseOff = BipedActionController.getAction(entity, offStack);
    var activeHand = E.activeHandMain(entity) ? (primaryRight ? "right" : "left") : (primaryRight ? "left" : "right");
    var useType = BipedActionController.getItemUseAction(activeItem, poseMain, poseOff);
    if (useType !== this.currentUseActionType) {
      this.currentUseActionType = useType;
      if (useType != null) { this.actionBit = new USE_ACTIONS[useType](activeHand); this.layerAction.playOrContinueBit(this.actionBit, data); }
      else { this.layerAction.clearAnimation(); this.currentAttackActionType = null; }
    }
    var attackType = BipedActionController.getItemAttackAction(N.item.stackItem(mainStack));
    if (this.currentAttackActionType !== attackType) {
      this.currentAttackActionType = attackType;
      this.actionBit = new ATTACK_ACTIONS[attackType](primaryRight ? "right" : "left");
      this.layerAction.playOrContinueBit(this.actionBit, data);
    }
    this.layerAction.perform(data);
  };
  BipedActionController.prototype.clearAction = function () { this.layerAction.clearAnimation(); };
  function performItemActions(controller, data) {
    var e = data.getEntity(), E = N.entity;
    controller.perform(data, E.primaryHandRight(e), E.mainStack(e), E.offStack(e), N.item.stackItem(E.activeStack(e)));
  }

  // ---- goblinbob.mobends.standard.animation.bit.player
  var PlayerWalkAnimationBit = bit({
    perform: function (data) {
      WalkAnimationBit.prototype.perform.call(this, data);
      if (data.getTicksAfterAttack() < 10) data.head.rotation.setSmoothness(0.5).orientX(data.headPitch.get()).rotateY(data.headYaw.get());
    }
  });
  var PlayerSprintAnimationBit = bit({
    perform: function (data) {
      SprintAnimationBit.prototype.perform.call(this, data);
      if (data.getTicksAfterAttack() < 10) data.head.rotation.setSmoothness(0.5).orientX(data.headPitch.get()).rotateY(data.headYaw.get());
    }
  });
  var SprintJumpAnimationBit = bit({
    onPlay: function () { this.relax = 0; },
    perform: function (data) {
      if (data.getPrevMotionY() < 0 && data.getMotionY() > 0) this.onPlay(data);
      var sw = data.getSprintJumpLeg(), mtp = sw ? 1 : -1;
      var mainArm = sw ? data.rightArm : data.leftArm, offArm = sw ? data.leftArm : data.rightArm;
      var mainLeg = sw ? data.rightLeg : data.leftLeg, offLeg = sw ? data.leftLeg : data.rightLeg;
      var mainForeLeg = sw ? data.rightForeLeg : data.leftForeLeg, offForeLeg = sw ? data.leftForeLeg : data.rightForeLeg;
      var bodyRotationY = 20 * mtp, bodyLean = MathHelper.clamp(data.getMotionY(), -0.2, 0.2) * -100 + 20;
      if (this.relax < 1) { this.relax += DataUpdateHandler.ticksPerFrame * 0.1; this.relax = Math.min(this.relax, 1); }
      var relaxAngle = MathHelper.sqrt(MathHelper.sqrt(this.relax));
      data.centerRotation.setSmoothness(0.3).orientZero(); data.globalOffset.slideToZero(0.5);
      data.body.rotation.setSmoothness(0.3).orientX(bodyLean).rotateY(bodyRotationY);
      data.rightLeg.rotation.setSmoothness(0.8).orientZ(5); data.leftLeg.rotation.setSmoothness(0.8).orientZ(-5);
      data.rightArm.rotation.setSmoothness(0.3).orientZ(10); data.leftArm.rotation.setSmoothness(0.3).orientZ(-10);
      mainLeg.getRotation().rotateX(-45); offLeg.getRotation().rotateX(45);
      mainArm.getRotation().rotateX(50); offArm.getRotation().rotateX(-50);
      mainForeLeg.getRotation().orientX(80 - relaxAngle * 80); offForeLeg.getRotation().orientX(relaxAngle * 70);
      data.head.rotation.orientX(data.headPitch.get() - 20); data.head.rotation.rotateY(data.headYaw.get() - bodyRotationY);
    }
  }, function () { AnimationBit.call(this); this.relax = 0; });
  var FlyingAnimationBit = bit({
    perform: function (data) {
      var player = data.getEntity(), magnitude = data.getInterpolatedMotionMagnitude(), ticks = DataUpdateHandler.getTicks();
      var forwardMomentum = MathHelper.clamp(data.getForwardMomentum(), -1, 1), sideMomentum = MathHelper.clamp(data.getSidewaysMomentum(), -1, 1);
      var xzMomentum = data.getInterpolatedXZMotionMagnitude(), headPitch = data.headPitch.get(), headYaw = data.headYaw.get(), headYawAbs = Math.abs(headYaw);
      var yMomentumAngle = MathHelper.atan2(xzMomentum, data.getMotionY()) * 180 / PI;
      if (N.entity.isSprinting(player) && !data.isDrawingBow() && data.getTicksAfterAttack() >= 10) {
        var speedFactor = MathHelper.clamp(magnitude, 0, 0.2) / 0.2;
        data.centerRotation.setSmoothness(1).orientX(yMomentumAngle * speedFactor).rotateZ(headYaw);
        var bodyRotationX = MathHelper.clamp(headPitch * 0.8, -60, 0);
        data.head.rotation.setSmoothness(1).orientY(headYaw).rotateX(headPitch - bodyRotationX - yMomentumAngle * speedFactor);
        data.body.rotation.setSmoothness(0.7).orientX(bodyRotationX);
        data.leftArm.rotation.setSmoothness(0.7).orientX(-bodyRotationX).rotateZ(-60 + 55 * speedFactor - headYawAbs * 0.5);
        data.rightArm.rotation.setSmoothness(0.7).orientX(-bodyRotationX).rotateZ(60 - 55 * speedFactor + headYawAbs * 0.5);
        data.leftForeArm.rotation.setSmoothness(0.7).orientZero(); data.rightForeArm.rotation.setSmoothness(0.7).orientZero();
        data.leftLeg.rotation.setSmoothness(0.7).orientZ(-5); data.rightLeg.rotation.setSmoothness(0.7).orientZ(5);
        data.leftForeLeg.rotation.setSmoothness(0.7).orientX(0); data.rightForeLeg.rotation.setSmoothness(0.7).orientX(0);
      } else if (magnitude < 0.1) {
        var armSway = (MathHelper.cos(ticks * 0.0825) + 1) / 2, armSway2 = (-MathHelper.sin(ticks * 0.0825) + 1) / 2;
        var legFlap = MathHelper.cos(ticks * 0.125), legFlap2 = MathHelper.sin(ticks * 0.125);
        data.leftArm.rotation.setSmoothness(0.3).orientX(armSway2 * 30 - 15).rotateZ(-armSway * 30);
        data.rightArm.rotation.setSmoothness(0.3).orientX(armSway2 * 30 - 15).rotateZ(armSway * 30);
        data.leftForeArm.rotation.setSmoothness(0.3).orientX(armSway2 * -40); data.rightForeArm.rotation.setSmoothness(0.3).orientX(armSway2 * -40);
        data.leftLeg.rotation.setSmoothness(0.3).orientZ(-5 + legFlap * 3).rotateX(-25 + legFlap2 * 5);
        data.rightLeg.rotation.setSmoothness(0.3).orientZ(5 - legFlap * 3).rotateX(-6 + legFlap2 * 5);
        data.leftForeLeg.rotation.setSmoothness(0.4).orientX(20 - legFlap2 * 15); data.rightForeLeg.rotation.setSmoothness(0.4).orientX(5);
        data.body.rotation.orientX(armSway * 10); data.centerRotation.orientZero();
        data.head.rotation.setSmoothness(1).orientX(headPitch).rotateY(headYaw);
      } else {
        data.centerRotation.orientZero(); data.centerRotation.rotateX(forwardMomentum * 50);
        data.body.rotation.orientZero();
        data.leftArm.rotation.orientX(forwardMomentum * 90).localRotateZ(sideMomentum * -80 - 20);
        data.rightArm.rotation.orientX(forwardMomentum * 90).localRotateZ(sideMomentum * -80 + 20);
        data.leftForeArm.rotation.orientZero(); data.rightForeArm.rotation.orientZero();
        data.leftLeg.rotation.orientX(-45).localRotateZ(sideMomentum * -40 - 5);
        data.rightLeg.rotation.orientX(-6).localRotateZ(sideMomentum * -40 + 5);
        data.leftForeLeg.rotation.orientX(30); data.rightForeLeg.rotation.orientX(10);
        data.head.rotation.setSmoothness(1).orientX(headPitch).rotateX(-forwardMomentum * 50);
        if (!data.isDrawingBow()) data.centerRotation.localRotateY(-headYaw);
      }
      data.renderRotation.setSmoothness(0.7).orientX(0); data.globalOffset.slideToZero(0.7);
    }
  });
  var ElytraAnimationBit = bit({
    perform: function (data) {
      var magnitude = data.getInterpolatedMotionMagnitude(), headYaw = data.headYaw.get(), headYawAbs = Math.abs(headYaw);
      var speedFactor = MathHelper.clamp(magnitude, 0, 0.2) / 0.2;
      data.head.rotation.setSmoothness(1).orientY(headYaw).rotateX(-90);
      data.body.rotation.setSmoothness(0.7).orientX(0);
      data.leftArm.rotation.setSmoothness(0.7).orientX(0).rotateZ(-60 + 55 * speedFactor - headYawAbs * 0.5);
      data.rightArm.rotation.setSmoothness(0.7).orientX(0).rotateZ(60 - 55 * speedFactor + headYawAbs * 0.5);
      data.leftForeArm.rotation.setSmoothness(0.7).orientZero(); data.rightForeArm.rotation.setSmoothness(0.7).orientZero();
      data.leftLeg.rotation.setSmoothness(0.7).orientZ(-5); data.rightLeg.rotation.setSmoothness(0.7).orientZ(5);
      data.leftForeLeg.rotation.setSmoothness(0.7).orientX(0); data.rightForeLeg.rotation.setSmoothness(0.7).orientX(0);
      data.centerRotation.setSmoothness(1).orientZero(); data.renderRotation.setSmoothness(0.7).orientX(0); data.globalOffset.slideToZero(0.7);
    }
  });
  var SleepingAnimationBit = bit({
    perform: function (data) {
      data.localOffset.slideToZero(0.3); data.globalOffset.slideToZero(0.3);
      data.renderRotation.setSmoothness(0.3).orientZero(); data.centerRotation.setSmoothness(0.3).orientZero();
      data.renderRightItemRotation.setSmoothness(0.3).orientZero(); data.renderLeftItemRotation.setSmoothness(0.3).orientZero();
      data.rightLeg.rotation.orient(0, 1, 0, 0); data.rightLeg.rotation.rotate(2, 0, 0, 1); data.rightLeg.rotation.rotate(5, 0, 1, 0);
      data.leftLeg.rotation.orient(0, 1, 0, 0); data.leftLeg.rotation.rotate(-2, 0, 0, 1); data.leftLeg.rotation.rotate(-5, 0, 1, 0);
      data.rightForeLeg.rotation.orient(4, 1, 0, 0); data.leftForeLeg.rotation.orient(4, 1, 0, 0);
      data.rightForeArm.rotation.orient(-4, 1, 0, 0); data.leftForeArm.rotation.orient(-4, 1, 0, 0);
      var phase = DataUpdateHandler.getTicks() / 10;
      data.head.rotation.setSmoothness(1).orientX(((MathHelper.cos(phase) - 1) / 2) * -3);
      data.rightArm.rotation.setSmoothness(0.4).orientX(0).rotateZ(2.5);
      data.leftArm.rotation.setSmoothness(0.4).orientX(0).rotateZ(-2.5);
    }
  });
  var CapeAnimationBit = bit({
    perform: function (data) {
      var p = data.getEntity(), E = N.entity, pt = DataUpdateHandler.partialTicks, c = E.chasing(p);
      data.cape.rotation.orientX(0);
      var d0 = c[0] + (c[3] - c[0]) * pt - (E.prevPosX(p) + (E.posX(p) - E.prevPosX(p)) * pt);
      var d1 = c[1] + (c[4] - c[1]) * pt - (E.prevPosY(p) + (E.posY(p) - E.prevPosY(p)) * pt);
      var d2 = c[2] + (c[5] - c[2]) * pt - (E.prevPosZ(p) + (E.posZ(p) - E.prevPosZ(p)) * pt);
      var f = E.prevRenderYawOffset(p) + (E.renderYawOffset(p) - E.prevRenderYawOffset(p)) * pt;
      var d3 = Math.sin(f * 0.017453292), d4 = -Math.cos(f * 0.017453292);
      var f1 = MathHelper.clamp(d1 * 10, -6, 32);
      var f2 = Math.fround(d0 * d3 + d2 * d4) * 100, f3 = Math.fround(d0 * d4 - d2 * d3) * 100;
      if (f2 < 0) f2 = 0;
      var cam = E.cameraYaw(p), f4 = cam[0] + (cam[1] - cam[0]) * pt, walk = E.distanceWalked(p);
      f1 = f1 + Math.sin((walk[0] + (walk[1] - walk[0]) * pt) * 6) * 32 * f4;
      if (E.isSneaking(p)) f1 += 25;
      if (data.isFlying() && E.isSprinting(p)) { data.cape.rotation.setSmoothness(0.5).orientX(0); data.setCapeWaveSpeed(4); }
      else {
        data.cape.rotation.setSmoothness(0.5).orientX(6 + f2 / 2 + f1);
        data.cape.rotation.rotateZ(f3 / 2); data.cape.rotation.rotateY(-f3 / 2);
        data.setCapeWaveSpeed(1);
      }
    }
  });

  // ---- mob bits: pig zombie, skeleton, zombie, spider, wolf
  var PigZombieStandAnimationBit = bit({ perform: function (data) { StandAnimationBit.prototype.perform.call(this, data); pigZombieLean(data); } });
  var PigZombieWalkAnimationBit = bit({
    perform: function (data) {
      WalkAnimationBit.prototype.perform.call(this, data); pigZombieLean(data);
      data.globalOffset.slideY(Math.abs(MathHelper.sin(data.limbSwing.get() * 0.6662)) * -1.4 - 3);
    }
  });
  function pigZombieLean(data) {
    data.globalOffset.slideY(-3);
    data.body.rotation.localRotateX(20).rotateZ(-10); data.head.rotation.rotateX(-20);
    data.rightArm.rotation.rotateX(-20).rotateZ(10); data.leftArm.rotation.rotateX(-20).rotateZ(10);
    data.rightLeg.rotation.rotateZ(10); data.leftLeg.rotation.rotateZ(-10);
    data.rightLeg.rotation.rotateX(-30); data.leftLeg.rotation.rotateX(-10).rotateY(-10);
    data.rightForeLeg.rotation.rotateX(25); data.leftForeLeg.rotation.rotateX(25);
  }
  var SkeletonWalkAnimationBit = bit({
    perform: function (data) {
      WalkAnimationBit.prototype.perform.call(this, data);
      if (data.isStrafing()) {
        var limbSwing = data.limbSwing.get() * 0.6662, legSwingAmount = 0.7 * data.limbSwingAmount.get() / PI * 180;
        data.rightLeg.rotation.setSmoothness(1).orientZ(-5 + MathHelper.cos(limbSwing) * legSwingAmount);
        data.leftLeg.rotation.setSmoothness(1).orientZ(-5 + MathHelper.cos(limbSwing + PI) * legSwingAmount);
      }
    }
  });
  var ZombieLeanAnimationBit = bit({
    perform: function (data) {
      data.globalOffset.slideY(-3);
      data.body.rotation.localRotateX(30); data.head.rotation.rotateX(-30);
      data.rightArm.rotation.rotateX(-30); data.leftArm.rotation.rotateX(-30);
      data.rightLeg.rotation.rotateZ(10); data.leftLeg.rotation.rotateZ(-10);
      data.rightLeg.rotation.rotateX(-20); data.leftLeg.rotation.rotateX(-20);
      data.rightForeLeg.rotation.rotateX(25); data.leftForeLeg.rotation.rotateX(25);
      if (!data.isStillHorizontally() && data.getCurrentWalkingState() === 1) { data.rightArm.rotation.orientX(-90 - 30); data.leftArm.rotation.orientX(-90 - 30); }
    }
  });
  var ZombieStumblingAnimationBit = bit({
    perform: function (data) {
      var limbSwing = data.limbSwing.get() * 0.6662;
      limbSwing += Math.cos(limbSwing * 2) * 0.3;
      var swingAmount = 45 * data.limbSwingAmount.get();
      data.rightLeg.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing) * swingAmount);
      data.leftLeg.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing + PI) * swingAmount);
      data.rightArm.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing + PI) * swingAmount);
      data.leftArm.rotation.setSmoothness(1).orientX(MathHelper.cos(limbSwing) * swingAmount);
      data.body.rotation.setSmoothness(0.5).orientY(MathHelper.cos(limbSwing + PI) * swingAmount);
      var heavy = Math.min((limbSwing % PI) / PI, 1), heavyInv = 1 - Math.min((limbSwing % PI) / PI, 1);
      data.body.rotation.rotateX(heavyInv * 40); data.body.rotation.rotateZ(MathHelper.cos(limbSwing) * 10);
      data.head.rotation.rotateX(-heavyInv * 40); data.head.rotation.rotateZ(-40 + heavy * 20);
    }
  });
  function putLimbOnGround(upper, lower, odd, stretchDistance, groundLevel, smoothness) {
    var finish = smoothness === undefined;
    if (finish) smoothness = 1;
    var seg = 12, maxStretch = seg * 2;
    var c = groundLevel === 0 ? stretchDistance : Math.sqrt(stretchDistance * stretchDistance + groundLevel * groundLevel);
    if (c > maxStretch) c = maxStretch;
    var alpha = c > maxStretch ? 0 : Math.acos((c / 2) / seg), beta = Math.atan2(stretchDistance, -groundLevel);
    var lowerAngle = Math.max(-2.3, -2 * alpha), upperAngle = Math.min(1, alpha + beta - PI / 2);
    upper.setSmoothness(smoothness).localRotateZ((upperAngle / PI * 180) * (odd ? -1 : 1));
    lower.setSmoothness(smoothness).orientZ((lowerAngle / PI * 180) * (odd ? -1 : 1));
    if (finish) { upper.finish(); lower.finish(); }
  }
  function SpiderAnimationBitBase() { AnimationBit.call(this); this.startTransition = 0; }
  extend(SpiderAnimationBitBase, AnimationBit);
  SpiderAnimationBitBase.prototype.animateMovingLimb = function (data, groundLevel, limbSwing, index, minDist, maxDist, minRot, maxRot) {
    var odd = index % 2 === 1, offset = (((index + 1) / 2 | 0) % 2) === 0 ? PI : 0;
    var sideRotation = minRot + (MathHelper.sin(limbSwing + offset) * 0.5 + 0.5) * (maxRot - minRot);
    var dist = minDist + (MathHelper.sin(limbSwing + offset) * 0.5 + 0.5) * (maxDist - minDist);
    groundLevel += -7 + Math.max(0, MathHelper.cos(limbSwing + offset)) * 4;
    var limb = data.limbs[index];
    limb.upperPart.rotation.setSmoothness(1).orientY(odd ? sideRotation : -sideRotation);
    if (this.startTransition >= 1) putLimbOnGround(limb.upperPart.rotation, limb.lowerPart.rotation, odd, dist, groundLevel);
    else putLimbOnGround(limb.upperPart.rotation, limb.lowerPart.rotation, odd, dist, groundLevel, this.startTransition);
    limb.setAngleAndDistance(odd ? sideRotation / 180 * PI : PI - sideRotation / 180 * PI, dist * 0.0625);
  };
  function spiderSub(proto) { var C = function () { SpiderAnimationBitBase.call(this); }; extend(C, SpiderAnimationBitBase); for (var k in proto) C.prototype[k] = proto[k]; return C; }
  var LEG_PATTERN = [[0, 0, 20, 10, -80, -50], [0.3, 1, 20, 10, -80, -50], [0.3, 2, 15, 15, -30, 10], [0, 3, 15, 15, -30, 10],
    [0.4, 4, 7, 15, 20, 50], [0.7, 5, 7, 15, 20, 50], [0.7, 6, 10, 20, 60, 80], [0.4, 7, 10, 20, 60, 80]];
  function animateLegs(bitObj, data, groundLevel, limbSwing) {
    for (var i = 0; i < 8; i++) { var p = LEG_PATTERN[i]; bitObj.animateMovingLimb(data, groundLevel, limbSwing + p[0], p[1], p[2], p[3], p[4], p[5]); }
  }
  var SpiderCrawlAnimationBit = spiderSub({
    perform: function (data) {
      var pt = DataUpdateHandler.partialTicks, spider = data.getEntity(), headYaw = data.headYaw.get(), headPitch = data.headPitch.get();
      var limbSwing = data.getInterpolatedCrawlProgress() * 5, groundLevel = MathHelper.sin(limbSwing * 0.6) * 1.2;
      if (this.startTransition < 1) this.startTransition += DataUpdateHandler.ticksPerFrame * 0.1;
      data.spiderHead.rotation.orientInstantX(headPitch); data.spiderHead.rotation.rotateY(headYaw).finish();
      animateLegs(this, data, groundLevel, limbSwing);
      var E = N.entity, yaw = E.prevRotationYaw(spider) + (E.rotationYaw(spider) - E.prevRotationYaw(spider)) * pt;
      var renderRotationY = MathHelper.wrapDegrees(yaw - data.getCrawlingRotation());
      data.renderRotation.orientX(-90); data.renderRotation.setSmoothness(0.6).rotateY(renderRotationY);
      data.localOffset.slideTo(0, -10, 0, 0.5); data.centerRotation.orientZero();
    }
  });
  var SpiderMoveAnimationBit = spiderSub({
    perform: function (data) {
      var ticks = DataUpdateHandler.getTicks(), headYaw = data.headYaw.get(), headPitch = data.headPitch.get();
      var limbSwing = data.limbSwing.get() * 0.6662, groundLevel = MathHelper.sin(ticks * 0.6) * 1.2;
      var touchdown = Math.min(data.getTicksAfterTouchdown() / 10, 1);
      if (this.startTransition < 1) this.startTransition += DataUpdateHandler.ticksPerFrame * 0.1;
      if (touchdown < 1) groundLevel += Math.sin((touchdown * 1.2 - 0.2) * PI * 2) * 3 * (1 - touchdown);
      data.spiderHead.rotation.orientInstantX(headPitch); data.spiderHead.rotation.rotateY(headYaw).finish();
      var bodyX = MathHelper.sin(ticks * 0.2) * 0.4, bodyZ = MathHelper.cos(ticks * 0.2) * 0.4;
      animateLegs(this, data, groundLevel, limbSwing);
      data.localOffset.slideToZero(); data.globalOffset.set(bodyX, -groundLevel, -bodyZ);
      data.renderRotation.orientZero(); data.centerRotation.orientZero();
    }
  });
  var SpiderIdleAnimationBit = bit({
    perform: function (data) {
      var ticks = DataUpdateHandler.getTicks(), pt = DataUpdateHandler.partialTicks, spider = data.getEntity();
      var headYaw = data.headYaw.get(), headPitch = data.headPitch.get(), groundLevel = Math.sin(ticks * 0.1) * 0.5;
      var touchdown = Math.min(data.getTicksAfterTouchdown() / 10, 1);
      if (touchdown < 1) groundLevel += Math.sin((touchdown * 1 - 0) * PI * 2) * 4 * (1 - touchdown);
      data.spiderHead.rotation.orientInstantX(headPitch); data.spiderHead.rotation.rotateY(headYaw).finish();
      var bodyX = Math.sin(ticks * 0.2) * 0.4, bodyZ = Math.cos(ticks * 0.2) * 0.4;
      for (var i = 0; i < data.limbs.length; i++) {
        var limb = data.limbs[i], ik = limb.solveIK(bodyX, bodyZ, pt);
        var deviation = GUtil.getRadianDifference(limb.getNeutralYaw(), ik.xzAngle + PI / 2);
        if (deviation > 0.9 || ik.xzDistance * 0.0625 > 1.2) limb.adjustToNeutralPosition();
        limb.applyIK(ik, groundLevel, 4, pt);
      }
      if (N.entity.ticksExisted(spider) % 100 < 10) { data.limbs[6].adjustToLocalPosition(0, 1.5, 0.2); data.limbs[7].adjustToLocalPosition(0, 1.5, 0.2); }
      data.localOffset.slideToZero(); data.globalOffset.set(Math.fround(bodyX), Math.fround(-groundLevel), Math.fround(-bodyZ));
      data.centerRotation.orientZero(); data.renderRotation.orientZero();
    }
  });
  var SpiderJumpAnimationBit = bit({
    perform: function (data) {
      for (var i = 0; i < data.limbs.length; i++) {
        var odd = i % 2 === 1, limb = data.limbs[i], naturalYaw = -(i / (data.limbs.length - 1) * 2 - 1);
        naturalYaw = odd ? (-naturalYaw * 1.3) : (naturalYaw * 1.3);
        limb.upperPart.rotation.orientY(naturalYaw / PI * 180);
      }
      var motionY = MathHelper.clamp(-data.getInterpolatedMotionY() * 5, -1, 1), legAngle = -20 + motionY * 25, foreLegAngle = -70 - motionY * 40;
      for (var j = 0; j < 8; j++) {
        var sign = j % 2 === 0 ? 1 : -1;
        data.limbs[j].upperPart.rotation.setSmoothness(1).localRotateZ(legAngle * sign);
      }
      for (var k = 0; k < 8; k++) {
        var s2 = k % 2 === 0 ? 1 : -1;
        data.limbs[k].lowerPart.rotation.setSmoothness(1).orientZ(foreLegAngle * s2);
      }
      data.localOffset.slideToZero(); data.globalOffset.set(0, 0, 0); data.centerRotation.orientZero(); data.renderRotation.orientZero();
    }
  });
  var SpiderDeathAnimationBit = bit({
    onPlay: function () { this.wiggleSpeedMultiplier = 1; this.wigglePhase = 0; },
    perform: function (data) {
      data.globalOffset.slideY(10, 0.3);
      data.spiderHead.rotation.orientInstantX(data.headPitch.get()); data.spiderHead.rotation.rotateY(data.headYaw.get());
      var L = data.limbs, zUp = [-45, 45, -33.3, 33.3, -33.3, 33.3, -45, 45], yUp = [45, -45, 22.5, -22.5, -22.5, 22.5, -45, 45], i;
      for (i = 0; i < 8; i++) L[i].upperPart.rotation.orientInstantZ(zUp[i]);
      for (i = 0; i < 8; i++) L[i].upperPart.rotation.rotateY(yUp[i]);
      for (i = 0; i < 8; i++) L[i].lowerPart.rotation.orientInstantZ(i % 2 === 0 ? -89 : 89);
      var limbSwing = data.limbSwing.get() * 0.6662, amount = data.limbSwingAmount.get() / PI * 180;
      var f3 = -(MathHelper.cos(limbSwing * 2) * 0.4) * amount, f4 = -(MathHelper.cos(limbSwing * 2 + PI) * 0.4) * amount;
      var f5 = -(MathHelper.cos(limbSwing * 2 + PI / 2) * 0.4) * amount, f6 = -(MathHelper.cos(limbSwing * 2 + PI * 3 / 2) * 0.4) * amount;
      var f7 = Math.abs(MathHelper.sin(limbSwing) * 0.4) * amount, f8 = Math.abs(MathHelper.sin(limbSwing + PI) * 0.4) * amount;
      var f9 = Math.abs(MathHelper.sin(limbSwing + PI / 2) * 0.4) * amount, f10 = Math.abs(MathHelper.sin(limbSwing + PI * 3 / 2) * 0.4) * amount;
      var fy = [f3, -f3, f4, -f4, f5, -f5, f6, -f6];
      for (i = 0; i < 8; i++) L[i].upperPart.rotation.rotateY(fy[i]);
      if (this.wiggleSpeedMultiplier > 0) { this.wiggleSpeedMultiplier -= DataUpdateHandler.ticksPerFrame * 0.1; this.wiggleSpeedMultiplier = Math.max(0, this.wiggleSpeedMultiplier); }
      this.wigglePhase += (0.3 + this.wiggleSpeedMultiplier * 2) * DataUpdateHandler.ticksPerFrame;
      var w = 10 + this.wiggleSpeedMultiplier * 10, p = this.wigglePhase;
      var w1 = MathHelper.cos(p) * w, w2 = MathHelper.cos(p + PI / 4) * w, w3 = MathHelper.cos(p + PI / 2) * w, w4 = MathHelper.cos(p + PI / 4 * 3) * w;
      var fz = [f7 + w1, -f7 + w2, f8 + w3, -f8 + w4, f9 + w1, -f9 + w2, f10 + w3, -f10 + w4];
      for (i = 0; i < 8; i++) L[i].upperPart.rotation.rotateZ(fz[i]);
    }
  }, function () { AnimationBit.call(this); this.wiggleSpeedMultiplier = 1; this.wigglePhase = 0; });

  // ---- goblinbob.mobends.standard.client.renderer.entity.SwordTrail
  function TrailPart(primaryRight, color, vx, vy, vz) {
    this.primaryRight = primaryRight; this.baseColor = color;
    this.body = new ModelPartTransform(); this.arm = new ModelPartTransform(); this.foreArm = new ModelPartTransform();
    this.renderRotation = new Quaternion(); this.renderOffset = new Vec3f(); this.itemRotation = new Quaternion(); this.position = new Vec3f();
    this.velocityX = vx; this.velocityY = vy; this.velocityZ = vz; this.ticksExisted = 0;
  }
  TrailPart.prototype.update = function (tpf) {
    this.ticksExisted += tpf;
    this.position.x += this.velocityX * tpf; this.position.y += this.velocityY * tpf; this.position.z += this.velocityZ * tpf;
  };
  TrailPart.prototype.points = function () {
    var alpha = 1 - Math.min(this.ticksExisted / 5, 1);
    var points = [new Vec3f(0, 0, -8 + 8 * alpha), new Vec3f(0, 0, -8 - 8 * alpha)];
    GUtil.translate(points, 0, 0, 16); GUtil.rotate(points, this.itemRotation);
    GUtil.translate(points, this.primaryRight ? -1 : 1, -6, 0); GUtil.rotate(points, this.foreArm.rotation.getSmooth());
    GUtil.translate(points, 0, -6 + 2, 0); GUtil.rotate(points, this.arm.rotation.getSmooth());
    GUtil.translate(points, this.arm.position.x, 10, 0); GUtil.rotate(points, this.body.rotation.getSmooth());
    GUtil.translate(points, 0, 12, 0); GUtil.rotate(points, this.renderRotation);
    GUtil.translate(points, this.renderOffset.x, this.renderOffset.y, this.renderOffset.z);
    for (var i = 0; i < 2; i++) points[i].addVec(this.position);
    return { p: points, alpha: alpha };
  };
  var WHITE = [1, 1, 1];
  function SwordTrail() { this.trailPartList = []; }
  SwordTrail.prototype.reset = function () { this.trailPartList.length = 0; };
  SwordTrail.prototype.add = function (data, vx, vy, vz) {
    var right = N.entity.primaryHandRight(data.getEntity()), part = new TrailPart(right, WHITE, vx || 0, vy || 0, vz || 0);
    part.body.syncUp(data.body);
    if (right) { part.arm.syncUp(data.rightArm); part.foreArm.syncUp(data.rightForeArm); part.itemRotation.copy(data.renderRightItemRotation.getSmooth()); }
    else { part.arm.syncUp(data.leftArm); part.foreArm.syncUp(data.leftForeArm); part.itemRotation.copy(data.renderLeftItemRotation.getSmooth()); }
    part.renderOffset.set(data.globalOffset.getX(), data.globalOffset.getY(), data.globalOffset.getZ());
    part.renderRotation.copy(data.renderRotation.getSmooth()); part.renderRotation.negate();
    // Bounded: a part lives 20 ticks; this keeps a stalled frame clock from growing the list.
    if (this.trailPartList.length >= 256) this.trailPartList.shift();
    this.trailPartList.push(part);
  };
  SwordTrail.prototype.update = function (tpf) {
    var list = this.trailPartList;
    for (var i = 0; i < list.length; i++) list[i].update(tpf);
    for (var j = list.length - 1; j >= 0; j--) if (list[j].ticksExisted > 20) list.splice(j, 1);
  };
  // The original draws GL_QUADS in immediate mode; the adapter receives the same vertices with colors.
  SwordTrail.prototype.render = function () {
    var list = this.trailPartList;
    if (!list.length) return;
    var verts = [];
    function v(p, c, a) { verts.push(p.x, p.y, p.z, c[0], c[1], c[2], a); }
    for (var i = 0; i < list.length; i++) {
      var first = i === 0, last = i === list.length - 1, d = list[i].points(), c = list[i].baseColor, p = d.p;
      if (!first) { v(p[1], c, d.alpha); v(p[0], c, d.alpha); }
      v(p[0], c, d.alpha); v(p[1], c, d.alpha);
      if (last) { v(p[1], c, d.alpha); v(p[0], c, d.alpha); }
    }
    N.drawTrail(verts);
  };

  // ---- goblinbob.mobends.standard.data
  function BipedEntityData(entity) { LivingEntityData.call(this, entity); }
  extend(BipedEntityData, LivingEntityData);
  var BED = BipedEntityData.prototype;
  BED.isBiped = true;
  BED.initModelPose = function () {
    ED.initModelPose.call(this);
    this.body = new ModelPartTransform(); this.head = new ModelPartTransform(this.body);
    this.rightArm = new ModelPartTransform(this.body); this.leftArm = new ModelPartTransform(this.body);
    this.rightLeg = new ModelPartTransform(); this.leftLeg = new ModelPartTransform();
    this.rightForeArm = new ModelPartTransform(this.rightArm); this.leftForeArm = new ModelPartTransform(this.leftArm);
    this.rightForeLeg = new ModelPartTransform(this.rightLeg); this.leftForeLeg = new ModelPartTransform(this.leftLeg);
    this.renderRightItemRotation = new SmoothOrientation(); this.renderLeftItemRotation = new SmoothOrientation();
    this.swordTrail = new SwordTrail();
    var m = this.nameToPartMap, self = this;
    ["body", "head", "leftArm", "rightArm", "leftLeg", "rightLeg", "leftForeArm", "rightForeArm", "leftForeLeg", "rightForeLeg", "renderRightItemRotation", "renderLeftItemRotation"]
      .forEach(function (k) { m.set(k, self[k]); });
    this.body.position.set(0, 12, 0); this.head.position.set(0, -12, 0);
    this.rightArm.position.set(-5, -10, 0); this.leftArm.position.set(5, -10, 0);
    this.rightLeg.position.set(0, 12, 0); this.leftLeg.position.set(0, 12, 0);
    this.rightForeArm.position.set(0, 4, 2); this.leftForeArm.position.set(0, 4, 2);
    this.leftForeLeg.position.set(0, 6, -2); this.rightForeLeg.position.set(0, 6, -2);
  };
  BED.updateParts = function (tpf) {
    ED.updateParts.call(this, tpf);
    this.head.update(tpf); this.body.update(tpf); this.rightArm.update(tpf); this.leftArm.update(tpf);
    this.rightLeg.update(tpf); this.leftLeg.update(tpf); this.rightForeArm.update(tpf); this.leftForeArm.update(tpf);
    this.rightForeLeg.update(tpf); this.leftForeLeg.update(tpf);
    this.globalOffset.update(tpf); this.renderRotation.update(tpf);
    this.renderRightItemRotation.update(tpf); this.renderLeftItemRotation.update(tpf);
    this.swordTrail.update(tpf);
  };

  function PlayerData(entity) {
    BipedEntityData.call(this, entity);
    this.sprintJumpLeg = false; this.sprintJumpLegSwitched = false; this.fistPunchArm = false; this.currentAttack = 0;
    this.capeWavePhase = 0; this.capeWaveSpeed = 0; this.flyingStateOverride = null; this.controller = new PlayerController();
  }
  extend(PlayerData, BipedEntityData);
  var PD = PlayerData.prototype;
  PD.isPlayer = true;
  PD.getController = function () { return this.controller; };
  PD.setCapeWaveSpeed = function (v) { this.capeWaveSpeed = v; };
  PD.getCapeWavePhase = function () { return this.capeWavePhase; };
  PD.initModelPose = function () {
    BED.initModelPose.call(this);
    this.cape = new ModelPartTransform(this.body); this.nameToPartMap.set("cape", this.cape); this.cape.position.set(0, 0, 0);
    if (N.entity.smallArms(this.entity)) { this.rightArm.position.set(-5, -9.5, 0); this.leftArm.position.set(5, -9.5, 0); }
  };
  PD.updateParts = function (tpf) { BED.updateParts.call(this, tpf); this.cape.update(tpf); };
  PD.update = function (pt) {
    LED.update.call(this, pt);
    if (this.getTicksAfterAttack() > 20) this.currentAttack = 0;
    if (this.motionY < 0) this.sprintJumpLegSwitched = false;
    if (!this.sprintJumpLegSwitched && this.motionY > 0) { this.sprintJumpLeg = !this.sprintJumpLeg; this.sprintJumpLegSwitched = true; }
    this.capeWavePhase += this.capeWaveSpeed * DataUpdateHandler.ticksPerFrame;
    if (this.capeWavePhase > 380) this.capeWavePhase -= 380;
  };
  PD.onLiftoff = function () { LED.onLiftoff.call(this); if (!this.sprintJumpLegSwitched) { this.sprintJumpLeg = !this.sprintJumpLeg; this.sprintJumpLegSwitched = true; } };
  PD.onAttack = function () {
    if (N.item.isAir(N.entity.mainItem(this.entity))) { this.fistPunchArm = !this.fistPunchArm; this.ticksAfterAttack = 0; return; }
    if (this.ticksAfterAttack <= 6) return;
    switch (this.currentAttack) {
      case 1: this.currentAttack = 2; break;
      case 2: this.currentAttack = 3; break;
      case 3: this.currentAttack = 4; break;
      case 4: this.currentAttack = (!ModConfig.performSpinAttack || N.entity.isRiding(this.entity)) ? 1 : 5; break;
      default: this.currentAttack = 1;
    }
    this.ticksAfterAttack = 0;
  };
  PD.getCurrentAttack = function () { return this.currentAttack; };
  PD.getFistPunchArm = function () { return this.fistPunchArm; };
  PD.getSprintJumpLeg = function () { return this.sprintJumpLeg; };
  PD.isFlying = function () { return this.flyingStateOverride != null ? this.flyingStateOverride : N.entity.capabilitiesFlying(this.entity); };

  function ZombieDataBase(entity) {
    BipedEntityData.call(this, entity);
    this.animationSet = jint(Math.fround(Math.fround(N.entity.id(entity)) * Math.fround(3.61352))) % 2;
    this.currentWalkingState = 0; this.ticksBeforeStateChange = 0;
  }
  extend(ZombieDataBase, BipedEntityData);
  ZombieDataBase.prototype.getAnimationSet = function () { return this.animationSet; };
  ZombieDataBase.prototype.getCurrentWalkingState = function () { return this.currentWalkingState; };
  ZombieDataBase.prototype.update = function (pt) {
    LED.update.call(this, pt);
    this.ticksBeforeStateChange -= DataUpdateHandler.ticksPerFrame;
    if (this.ticksBeforeStateChange <= 0) { this.currentWalkingState = Math.floor(Math.random() * 2); this.ticksBeforeStateChange = 80 + Math.floor(Math.random() * 20); }
  };
  function ZombieData(entity) { ZombieDataBase.call(this, entity); this.controller = new ZombieController(); }
  extend(ZombieData, ZombieDataBase);
  ZombieData.prototype.getController = function () { return this.controller; };
  function PigZombieData(entity) { BipedEntityData.call(this, entity); this.controller = new PigZombieController(); }
  extend(PigZombieData, BipedEntityData);
  PigZombieData.prototype.getController = function () { return this.controller; };
  function SkeletonData(entity) { BipedEntityData.call(this, entity); this.controller = new SkeletonController(); }
  extend(SkeletonData, BipedEntityData);
  SkeletonData.prototype.getController = function () { return this.controller; };
  SkeletonData.prototype.initModelPose = function () {
    BED.initModelPose.call(this);
    this.rightArm.position.set(-5, -10, 0); this.leftArm.position.set(5, -10, 0);
    this.rightLeg.position.set(-2, 12, 0); this.leftLeg.position.set(2, 12, 0);
    this.rightForeArm.position.set(0, 4, 1); this.leftForeArm.position.set(0, 4, 1);
    this.leftForeLeg.position.set(0, 6, -1); this.rightForeLeg.position.set(0, 6, -1);
  };

  function SpiderLimb(data, index) {
    this.data = data; this.upperPart = new ModelPartTransform(); this.lowerPart = new ModelPartTransform();
    this.index = index; this.odd = index % 2 === 1;
    var neutralYaw = index / (8 - 1) * 2 - 1;
    this.neutralYaw = this.odd ? (neutralYaw * 1.3) : (PI - neutralYaw * 1.3);
    this.worldX = this.worldZ = this.prevWorldX = this.prevWorldZ = 0;
    this.adjustTargetX = 0; this.adjustTargetZ = 0; this.adjustingProgress = 1; this.adjustingSpeed = 0.2;
    var z = 2 - (index / 2 | 0);
    this.upperPart.position.set(this.odd ? 4 : -4, 15, z); this.lowerPart.position.set(this.odd ? 11 : -11, -1, 0);
    this.resetPosition();
  }
  var SL = SpiderLimb.prototype;
  function bodyYawRad(data) { return Math.fround(N.entity.renderYawOffset(data.entity) / 180 * PI); }
  SL.resetPosition = function () {
    var by = bodyYawRad(this.data);
    this.worldX = Math.cos(this.neutralYaw + by) + this.data.getPositionX(); this.worldZ = Math.sin(this.neutralYaw + by) + this.data.getPositionZ();
    this.prevWorldX = this.worldX; this.prevWorldZ = this.worldZ;
  };
  SL.updateClient = function () {
    this.prevWorldX = this.worldX; this.prevWorldZ = this.worldZ;
    if (this.adjustingProgress < 1) {
      this.adjustingProgress += this.adjustingSpeed;
      if (this.adjustingProgress >= 1) { this.worldX = this.adjustTargetX; this.worldZ = this.adjustTargetZ; this.adjustingProgress = 1; }
      else { this.worldX += (this.adjustTargetX - this.worldX) * 0.2; this.worldZ += (this.adjustTargetZ - this.worldZ) * 0.2; }
    }
  };
  SL.setAngleAndDistance = function (angle, distance) {
    this.setLocalPosition(MathHelper.cos(angle) * distance + this.upperPart.position.x * 0.0625, MathHelper.sin(angle) * distance - this.upperPart.position.z * 0.0625);
  };
  SL.adjustToNeutralPosition = function () {
    if (this.adjustingProgress !== 1) return;
    this.adjustingSpeed = 0.2; this.adjustingProgress = 0;
    var by = bodyYawRad(this.data);
    this.adjustTargetX = Math.cos(this.neutralYaw + by) * 1.2 + this.data.getPositionX(); this.adjustTargetZ = Math.sin(this.neutralYaw + by) * 1.2 + this.data.getPositionZ();
  };
  SL.adjustToLocalPosition = function (x, z, speed) {
    if (this.adjustingProgress !== 1) return;
    this.adjustingSpeed = speed; this.adjustingProgress = 0;
    var by = bodyYawRad(this.data);
    this.adjustTargetX = x * Math.cos(by) - z * Math.sin(by) + this.data.getPositionX(); this.adjustTargetZ = x * Math.sin(by) + z * Math.cos(by) + this.data.getPositionZ();
  };
  SL.setLocalPosition = function (x, z) {
    this.adjustingProgress = 1;
    var by = bodyYawRad(this.data);
    this.worldX = this.adjustTargetX = x * Math.cos(by) - z * Math.sin(by) + this.data.getPositionX();
    this.worldZ = this.adjustTargetZ = x * Math.sin(by) + z * Math.cos(by) + this.data.getPositionZ();
  };
  SL.solveIK = function (bodyX, bodyZ, pt) {
    var e = this.data.entity, E = N.entity;
    var ryo = (E.prevRenderYawOffset(e) + (E.renderYawOffset(e) - E.prevRenderYawOffset(e)) * pt) / 180 * PI;
    var sx = E.prevPosX(e) + (E.posX(e) - E.prevPosX(e)) * pt, sz = E.prevPosZ(e) + (E.posZ(e) - E.prevPosZ(e)) * pt;
    var wx = this.prevWorldX + (this.worldX - this.prevWorldX) * pt, wz = this.prevWorldZ + (this.worldZ - this.prevWorldZ) * pt;
    var x = (wx - sx) / 0.0625, z = -(wz - sz) / 0.0625;
    var localX = x * Math.cos(ryo) - z * Math.sin(ryo) - bodyX, localZ = x * Math.sin(ryo) + z * Math.cos(ryo) - bodyZ;
    var dx = this.upperPart.position.x - localX, dz = this.upperPart.position.z - localZ;
    return { worldX: wx, worldZ: wz, localX: localX, localZ: localZ, deltaX: dx, deltaZ: dz, xzDistance: Math.sqrt(dx * dx + dz * dz), xzAngle: Math.atan2(dx, dz) };
  };
  SL.applyIK = function (r, groundLevel, liftHeight) {
    var xzAngle = this.odd ? (PI / 2 + r.xzAngle) : (-PI / 2 + r.xzAngle);
    this.upperPart.rotation.orientY(xzAngle / PI * 180); this.lowerPart.rotation.orientZero();
    putLimbOnGround(this.upperPart.rotation, this.lowerPart.rotation, this.odd, r.xzDistance, groundLevel - 7 + Math.sin(this.adjustingProgress * PI) * liftHeight);
  };
  SL.getNeutralYaw = function () { return this.neutralYaw; };

  function SpiderData(entity) {
    LivingEntityData.call(this, entity);
    this.controller = new SpiderController(); this.prevCrawlProgress = 0; this.crawlProgress = 0; this.wallFacing = null;
  }
  extend(SpiderData, LivingEntityData);
  var SD = SpiderData.prototype;
  SD.getController = function () { return this.controller; };
  SD.getCrawlProgress = function () { return this.crawlProgress; };
  SD.getInterpolatedCrawlProgress = function () { return GUtil.lerp(this.prevCrawlProgress, this.crawlProgress, DataUpdateHandler.partialTicks); };
  SD.initModelPose = function () {
    ED.initModelPose.call(this);
    this.spiderBody = new ModelPartTransform(); this.spiderNeck = new ModelPartTransform(); this.spiderHead = new ModelPartTransform();
    this.limbs = new Array(8);
    for (var i = 0; i < 8; i++) { this.limbs[i] = new SpiderLimb(this, i); this.nameToPartMap.set("leg" + (i + 1), this.limbs[i].upperPart); this.nameToPartMap.set("foreLeg" + (i + 1), this.limbs[i].lowerPart); }
    this.nameToPartMap.set("body", this.spiderBody); this.nameToPartMap.set("neck", this.spiderNeck); this.nameToPartMap.set("head", this.spiderHead);
    this.spiderHead.position.set(0, 15, -3); this.spiderNeck.position.set(0, 15, 0); this.spiderBody.position.set(0, 15, 9);
  };
  SD.updateParts = function (tpf) {
    ED.updateParts.call(this, tpf);
    this.spiderBody.update(tpf); this.spiderNeck.update(tpf); this.spiderHead.update(tpf);
    for (var i = 0; i < 8; i++) { this.limbs[i].upperPart.update(tpf); this.limbs[i].lowerPart.update(tpf); }
  };
  SD.updateClient = function () {
    LED.updateClient.call(this);
    for (var i = 0; i < 8; i++) this.limbs[i].updateClient();
    this.prevCrawlProgress = this.crawlProgress;
    this.crawlProgress += MathHelper.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
    this.wallFacing = this.calcWallFacing();
  };
  SD.calcWallFacing = function () {
    var e = this.entity, E = N.entity, W = N.world;
    if (!E.isOnLadder(e)) return null;
    var x = Math.floor(E.posX(e)), y = Math.floor(E.posY(e)), z = Math.floor(E.posZ(e));
    if (!W.isAir(e, x, y, z - 1)) return "north";
    if (!W.isAir(e, x, y, z + 1)) return "south";
    if (!W.isAir(e, x - 1, y, z)) return "west";
    if (!W.isAir(e, x + 1, y, z)) return "east";
    return null;
  };
  SD.getCrawlingRotation = function () { return this.wallFacing == null ? 0 : FACING_ANGLE[this.wallFacing]; };

  var TENTACLE_SECTIONS = 9, SECTION_HEIGHT = (18 / TENTACLE_SECTIONS) | 0;
  function SquidData(entity) { LivingEntityData.call(this, entity); this.controller = new SquidController(); }
  extend(SquidData, LivingEntityData);
  SquidData.prototype.getController = function () { return this.controller; };
  SquidData.prototype.initModelPose = function () {
    ED.initModelPose.call(this);
    this.squidBody = new ModelPartTransform(); this.squidBody.rotation.finish(); this.squidBody.position.set(0, 8, 0);
    this.nameToPartMap.set("body", this.squidBody);
    this.squidTentacles = [];
    for (var i = 0; i < 8; i++) {
      var d0 = i * PI * 2 / 8, row = [];
      row[0] = new ModelPartTransform(); row[0].position.set(Math.fround(Math.cos(d0)) * 4, 16, Math.fround(Math.sin(d0)) * 4);
      for (var j = 1; j < TENTACLE_SECTIONS; j++) {
        row[j] = new ModelPartTransform(); row[j].rotation.finish(); row[j].position.set(0, SECTION_HEIGHT, 0);
        this.nameToPartMap.set("tentacle_" + i + "_" + j, row[j]);
      }
      row[1].position.set(0, SECTION_HEIGHT, 2);
      this.squidTentacles.push(row);
    }
  };
  SquidData.prototype.updateParts = function (tpf) {
    ED.updateParts.call(this, tpf); this.squidBody.update(tpf);
    for (var i = 0; i < 8; i++) for (var j = 0; j < TENTACLE_SECTIONS; j++) this.squidTentacles[i][j].update(tpf);
  };

  var WOLF_PARTS = ["head", "body", "leg1", "leg2", "leg3", "leg4", "tail", "mane", "nose", "mouth", "tongue", "leftEar", "rightEar", "foreLeg1", "foreLeg2", "foreLeg3", "foreLeg4"];
  function WolfData(entity) { LivingEntityData.call(this, entity); this.controller = new WolfController(); }
  extend(WolfData, LivingEntityData);
  WolfData.prototype.getController = function () { return this.controller; };
  WolfData.prototype.initModelPose = function () {
    ED.initModelPose.call(this);
    var self = this;
    WOLF_PARTS.forEach(function (k) { self[k] = new ModelPartTransform(); self.nameToPartMap.set(k, self[k]); });
    this.head.position.set(0, -0.5, -13); this.body.position.set(0, 14, 8); this.mane.position.set(0, -0.5, -12);
    this.nose.position.set(0, 1, -3); this.mouth.position.set(0, 2, -3); this.tongue.position.set(0, 2, -3);
    this.leftEar.position.set(-2, -3, -1); this.rightEar.position.set(2, -3, -1);
    this.leg1.position.set(-2, 3, -1); this.leg2.position.set(2, 3, -1); this.leg3.position.set(-2, 3, -12); this.leg4.position.set(2, 3, -12);
    this.tail.position.set(0, -3, 0);
    this.foreLeg1.position.set(0, 4, -1); this.foreLeg2.position.set(0, 4, -1); this.foreLeg3.position.set(0, 4, 1); this.foreLeg4.position.set(0, 4, 1);
  };
  WolfData.prototype.updateParts = function (tpf) { ED.updateParts.call(this, tpf); for (var i = 0; i < WOLF_PARTS.length; i++) this[WOLF_PARTS[i]].update(tpf); };
  WolfData.prototype.isSitting = function () { return N.entity.wolfSitting(this.entity); };

  // ---- controllers
  function PlayerController() {
    this.layerBase = new HardAnimationLayer(); this.layerTorch = new HardAnimationLayer(); this.layerSneak = new HardAnimationLayer(); this.layerCape = new HardAnimationLayer();
    this.bitStand = new StandAnimationBit(); this.bitJump = new JumpAnimationBit(); this.bitSneak = new SneakAnimationBit(); this.bitLadderClimb = new LadderClimbAnimationBit();
    this.bitSwimming = new SwimmingAnimationBit(); this.bitRiding = new RidingAnimationBit(); this.bitSitting = new SittingAnimationBit(); this.bitFalling = new FallingAnimationBit();
    this.bitWalk = new PlayerWalkAnimationBit(); this.bitSprint = new PlayerSprintAnimationBit(); this.bitSprintJump = new SprintJumpAnimationBit();
    this.bitTorchHolding = new TorchHoldingAnimationBit(); this.bitFlying = new FlyingAnimationBit(); this.bitElytra = new ElytraAnimationBit();
    this.bitCape = new CapeAnimationBit(); this.bitSleeping = new SleepingAnimationBit(); this.actionController = new BipedActionController();
    this.upperBodyOnlyMask = new ArmatureMask("EXCLUDE_ONLY");
    ["root", "head", "leftLeg", "leftForeLeg", "rightLeg", "rightForeLeg"].forEach(function (b) { this.upperBodyOnlyMask.exclude(b); }, this);
  }
  PlayerController.prototype.performActionAnimations = function (data, player) {
    if (N.entity.isEntityAlive(player) && N.entity.isPlayerSleeping(player)) { this.actionController.clearAction(); return; }
    performItemActions(this.actionController, data);
  };
  PlayerController.prototype.perform = function (data) {
    var player = data.getEntity(), E = N.entity;
    this.layerCape.playOrContinueBit(this.bitCape, data);
    if (E.isEntityAlive(player) && E.isPlayerSleeping(player)) { this.layerBase.playOrContinueBit(this.bitSleeping, data); this.layerSneak.clearAnimation(); }
    else if (E.isRiding(player)) {
      var ridden = E.ridingEntity(player);
      this.layerBase.playOrContinueBit(ridden != null && E.isLiving(ridden) ? this.bitRiding : this.bitSitting, data);
      this.layerSneak.clearAnimation();
    } else {
      if (E.ticksElytraFlying(player) > 4) { this.layerBase.playOrContinueBit(this.bitElytra, data); this.layerSneak.clearAnimation(); this.layerTorch.clearAnimation(); }
      else if (data.isClimbing()) { this.layerBase.playOrContinueBit(this.bitLadderClimb, data); this.layerSneak.clearAnimation(); this.layerTorch.clearAnimation(); }
      else if (E.isInWater(player)) { this.layerBase.playOrContinueBit(this.bitSwimming, data); this.layerSneak.clearAnimation(); this.layerTorch.clearAnimation(); }
      else if (!data.isOnGround() || data.getTicksAfterTouchdown() < 1) {
        if (data.isFlying()) this.layerBase.playOrContinueBit(this.bitFlying, data);
        else if (data.getTicksFalling() > FallingAnimationBit.TICKS_BEFORE_FALLING) this.layerBase.playOrContinueBit(this.bitFalling, data);
        else this.layerBase.playOrContinueBit(E.isSprinting(player) ? this.bitSprintJump : this.bitJump, data);
        this.layerSneak.clearAnimation(); this.layerTorch.clearAnimation();
      } else {
        if (data.isStillHorizontally()) { this.layerBase.playOrContinueBit(this.bitStand, data); this.layerTorch.playOrContinueBit(this.bitTorchHolding, data); }
        else if (E.isSprinting(player)) { this.layerBase.playOrContinueBit(this.bitSprint, data); this.layerTorch.clearAnimation(); }
        else { this.layerBase.playOrContinueBit(this.bitWalk, data); this.layerTorch.playOrContinueBit(this.bitTorchHolding, data); }
        if (E.isSneaking(player)) this.layerSneak.playOrContinueBit(this.bitSneak, data); else this.layerSneak.clearAnimation();
      }
    }
    data.renderLeftItemRotation.orientZero(); data.renderRightItemRotation.orientZero();
    this.layerBase.perform(data); this.layerSneak.perform(data); this.layerTorch.perform(data);
    this.performActionAnimations(data, player);
    this.layerCape.perform(data);
  };
  function ZombieController() {
    this.layerBase = new HardAnimationLayer(); this.layerSet = new HardAnimationLayer();
    this.bitStand = new StandAnimationBit(); this.bitWalk = new WalkAnimationBit(); this.bitJump = new JumpAnimationBit();
    this.bitAnimationSet = [new ZombieLeanAnimationBit(), new ZombieStumblingAnimationBit()];
  }
  ZombieController.prototype.perform = function (data) {
    if (!data.isOnGround() || data.getTicksAfterTouchdown() < 1) this.layerBase.playOrContinueBit(this.bitJump, data);
    else this.layerBase.playOrContinueBit(data.isStillHorizontally() ? this.bitStand : this.bitWalk, data);
    this.layerSet.playOrContinueBit(this.bitAnimationSet[data.getAnimationSet()], data);
    this.layerBase.perform(data); this.layerSet.perform(data);
  };
  function SkeletonController() {
    this.layerBase = new HardAnimationLayer(); this.bitStand = new StandAnimationBit(); this.bitWalk = new SkeletonWalkAnimationBit(); this.bitJump = new JumpAnimationBit();
    this.actionController = new BipedActionController();
  }
  SkeletonController.prototype.perform = function (data) {
    if (!data.isOnGround() || data.getTicksAfterTouchdown() < 1) this.layerBase.playOrContinueBit(this.bitJump, data);
    else this.layerBase.playOrContinueBit(data.isStillHorizontally() ? this.bitStand : this.bitWalk, data);
    this.layerBase.perform(data);
    performItemActions(this.actionController, data);
  };
  function PigZombieController() {
    this.layerBase = new HardAnimationLayer(); this.layerAction = new HardAnimationLayer();
    this.bitStand = new PigZombieStandAnimationBit(); this.bitWalk = new PigZombieWalkAnimationBit(); this.bitJump = new JumpAnimationBit();
    this.bitAttack = new AttackSlashInwardAnimationBit();
  }
  PigZombieController.prototype.perform = function (data) {
    var pigZombie = data.getEntity();
    if (!data.isOnGround() || data.getTicksAfterTouchdown() < 1) this.layerBase.playOrContinueBit(this.bitJump, data);
    else this.layerBase.playOrContinueBit(data.isStillHorizontally() ? this.bitStand : this.bitWalk, data);
    if (N.entity.swingProgressField(pigZombie) > 0) this.layerAction.playOrContinueBit(this.bitAttack, data); else this.layerAction.clearAnimation();
    this.layerBase.perform(data); this.layerAction.perform(data);
  };
  function SpiderController() {
    this.layerBase = new HardAnimationLayer(); this.bitIdle = new SpiderIdleAnimationBit(); this.bitMove = new SpiderMoveAnimationBit();
    this.bitJump = new SpiderJumpAnimationBit(); this.bitDeath = new SpiderDeathAnimationBit(); this.bitClimb = new SpiderCrawlAnimationBit();
    this.resetAfterJumped = false;
  }
  SpiderController.prototype.perform = function (data) {
    var spider = data.getEntity();
    if (N.entity.health(spider) <= 0) this.layerBase.playOrContinueBit(this.bitDeath, data);
    else if (N.entity.besideClimbable(spider)) this.layerBase.playOrContinueBit(this.bitClimb, data);
    else if (!data.isOnGround() || data.getTicksAfterTouchdown() < 1) { this.layerBase.playOrContinueBit(this.bitJump, data); if (this.resetAfterJumped) this.resetAfterJumped = false; }
    else {
      if (!this.resetAfterJumped) { for (var i = 0; i < data.limbs.length; i++) data.limbs[i].resetPosition(); this.resetAfterJumped = true; }
      this.layerBase.playOrContinueBit(data.isStillHorizontally() ? this.bitIdle : this.bitMove, data);
    }
    this.layerBase.perform(data);
  };
  function SquidController() {}
  SquidController.prototype.perform = function (data) {
    var squid = data.getEntity(), E = N.entity, prev = E.prevSquidRotation(squid), cur = E.squidRotation(squid);
    var squidRotation = prev + (cur - prev) * DataUpdateHandler.partialTicks + 1.1;
    var f = Math.max(0, squidRotation / PI), base = 0;
    if (prev < PI) base = MathHelper.sin(f * f * PI) * 60;
    for (var i = 0; i < 8; i++) {
      var d0 = i * -360 / 8 + 90;
      data.squidTentacles[i][0].rotation.setSmoothness(0.1).orientX(base).rotateY(d0);
      var f2 = Math.max(0, squidRotation / (PI * 2));
      for (var j = 1; j < TENTACLE_SECTIONS; j++) {
        var tentacleAngle = 0;
        if (cur < PI) tentacleAngle = MathHelper.sin(f2 * PI * 2 + j * 0.1) * 10;
        data.squidTentacles[i][j].rotation.setSmoothness(0.1).orientX(-tentacleAngle);
      }
    }
  };

  // ---- Kumo (the wolf's keyframe state machine): KumoAnimatorState, KeyframeLayerState, nodes and conditions
  var animationCache = new Map();
  function loadAnimation(key) {
    if (animationCache.has(key)) return animationCache.get(key);
    var anim = N.loadBendsAnimation ? N.loadBendsAnimation(key) : null;
    animationCache.set(key, anim); return anim;
  }
  function duration(animation) { var d = 0; if (animation) animation.bones.forEach(function (b) { if (b.keyframes.length > d) d = b.keyframes.length; }); return d; }
  function StandardKeyframeNode(t) {
    this.animation = t.animationKey != null ? loadAnimation(t.animationKey) : null;
    this.startFrame = t.startFrame || 0; this.playbackSpeed = t.playbackSpeed === undefined ? 1 : t.playbackSpeed; this.looping = !!t.looping;
    this.animationDuration = duration(this.animation); this.connections = []; this.progress = this.startFrame;
  }
  StandardKeyframeNode.prototype.start = function (ctx) { this.progress = this.startFrame; this.connections.forEach(function (c) { if (c.triggerCondition.onNodeStarted) c.triggerCondition.onNodeStarted(ctx); }); };
  StandardKeyframeNode.prototype.update = function (ctx, dt) {
    if (this.animation == null) return;
    if (this.looping) { this.progress += this.playbackSpeed * dt; while (this.progress >= this.animationDuration - 1 && this.animationDuration > 1) this.progress -= this.animationDuration - 1; }
    else if (this.progress < this.animationDuration - 2) this.progress = Math.min(this.progress + this.playbackSpeed * dt, this.animationDuration - 2);
  };
  StandardKeyframeNode.prototype.isAnimationFinished = function () { return this.animation == null || !this.looping && this.progress >= this.animationDuration - 2; };
  function MovementKeyframeNode(t) {
    this.animation = t.animationKey != null ? loadAnimation(t.animationKey) : null;
    this.startFrame = t.startFrame || 0; this.playbackSpeed = t.playbackSpeed === undefined ? 1 : t.playbackSpeed;
    this.animationDuration = duration(this.animation); this.connections = []; this.progress = this.startFrame;
  }
  MovementKeyframeNode.prototype.start = StandardKeyframeNode.prototype.start;
  MovementKeyframeNode.prototype.isAnimationFinished = function () { return false; };
  MovementKeyframeNode.prototype.update = function (ctx) {
    if (this.animation == null) return;
    var data = ctx.entityData;
    this.progress = this.playbackSpeed * (data.limbSwing.get() * 0.6662);
    if (this.animationDuration > 1) this.progress %= this.animationDuration - 1;
  };
  var NODE_TYPES = { "core:standard": StandardKeyframeNode, "core:movement": MovementKeyframeNode };
  function createCondition(t) {
    if (!t || !t.type) throw new Error("No type was specified for trigger condition.");
    switch (t.type) {
      case "core:not": var inner = createCondition(t.condition); return { isConditionMet: function (ctx) { return !inner.isConditionMet(ctx); }, onNodeStarted: function (ctx) { if (inner.onNodeStarted) inner.onNodeStarted(ctx); } };
      case "core:and": case "core:or":
        var list = (t.conditions || []).map(createCondition), and = t.type === "core:and";
        return { isConditionMet: function (ctx) { for (var i = 0; i < list.length; i++) { var m = list[i].isConditionMet(ctx); if (and && !m) return false; if (!and && m) return true; } return and; } };
      case "core:state":
        return { isConditionMet: function (ctx) {
          var d = ctx.entityData;
          switch (t.state) {
            case "ON_GROUND": return d.isOnGround();
            case "AIRBORNE": return !d.isOnGround();
            case "SPRINTING": return N.entity.isSprinting(d.getEntity());
            case "STANDING_STILL": return d.isStillHorizontally();
            case "MOVING_HORIZONTALLY": return !d.isStillHorizontally();
          }
          return false;
        } };
      case "core:ticks_passed":
        var ticksToPass = Number(t.ticksToPass) | 0;
        return { ticksOnStart: 0, onNodeStarted: function () { this.ticksOnStart = DataUpdateHandler.getTicks(); },
          isConditionMet: function () { return DataUpdateHandler.getTicks() > this.ticksOnStart + ticksToPass; } };
      case "core:animation_finished":
        return { isConditionMet: function (ctx) { return ctx.currentNode != null ? ctx.currentNode.isAnimationFinished() : false; } };
      case "mobends:wolf_state":
        if (!t.state) throw new Error("No 'state' property given for trigger condition.");
        return { isConditionMet: function (ctx) { return t.state === "SITTING" ? ctx.entityData.isSitting() : false; } };
    }
    throw new Error("A non-existent trigger condition type was specified: " + t.type);
  }
  var EASING = { LINEAR: function (t) { return t; }, EASE_IN: function (t) { return Tween.easeIn(t, 2); }, EASE_OUT: function (t) { return Tween.easeOut(t, 2); }, EASE_IN_OUT: function (t) { return Tween.easeInOut(t, 2); } };
  function KeyframeLayerState(t) {
    var self = this;
    this.mask = null;
    if (t.mask) { this.mask = new ArmatureMask(t.mask.mode); (t.mask.includedParts || []).forEach(function (p) { self.mask.include(p); }); (t.mask.excludedParts || []).forEach(function (p) { self.mask.exclude(p); }); }
    this.nodeStates = t.nodes.map(function (n) { var C = NODE_TYPES[n.type]; if (!C) throw new Error("A non-existent KeyframeNode type was specified: " + n.type); return new C(n); });
    t.nodes.forEach(function (n, i) {
      (n.connections || []).forEach(function (c) {
        var target = self.nodeStates[c.targetNodeIndex];
        if (!target) throw new Error("A connection to node at index: " + c.targetNodeIndex + " was specified, which doesn't exist.");
        self.nodeStates[i].connections.push({ targetNode: target, triggerCondition: createCondition(c.triggerCondition), transitionDuration: c.transitionDuration || 0, transitionEasing: c.transitionEasing || "EASE_IN_OUT" });
      });
    });
    this.currentNode = this.nodeStates[t.entryNode || 0];
    if (!this.currentNode) throw new Error("Entry node index is out of bounds.");
    this.previousNode = null; this.transitionProgress = 0; this.transitionDuration = 0; this.transitionEasing = "EASE_IN_OUT";
  }
  var KLS = KeyframeLayerState.prototype;
  KLS.start = function (ctx) { this.currentNode.start(ctx); };
  KLS.shouldPartBeAffected = function (name) { return this.mask == null || this.mask.doesAllow(name); };
  KLS.update = function (ctx, dt) {
    var data = ctx.entityData, node = this.currentNode;
    if (node != null) {
      var animation = node.animation;
      if (animation != null) {
        this.applyRestPose(data, animation);
        if (this.previousNode != null) {
          var t = (EASING[this.transitionEasing] || EASING.EASE_IN_OUT)(this.transitionProgress / this.transitionDuration);
          this.applyKeyframeAnimation(data, this.previousNode.animation, this.previousNode.progress, 1 - t);
          this.applyKeyframeAnimation(data, animation, node.progress, t);
          this.transitionProgress += dt;
          if (this.transitionProgress >= this.transitionDuration) this.previousNode = null;
        } else this.applyKeyframeAnimation(data, animation, node.progress, 1);
      }
    }
    ctx.currentNode = this.currentNode;
    for (var i = 0; i < this.nodeStates.length; i++) this.nodeStates[i].update(ctx, dt);
    var cons = this.currentNode.connections;
    for (var j = 0; j < cons.length; j++) {
      var c = cons[j];
      if (c.triggerCondition.isConditionMet(ctx)) {
        this.transitionDuration = c.transitionDuration; this.transitionEasing = c.transitionEasing;
        if (this.transitionDuration === 0) this.previousNode = null; else { this.previousNode = this.currentNode; this.transitionProgress = 0; }
        this.currentNode = c.targetNode; this.currentNode.start(ctx);
        break;
      }
    }
  };
  KLS.applyRestPose = function (data, animation) {
    var self = this, hasRoot = this.shouldPartBeAffected("root") && animation.bones.has("root");
    if (hasRoot) data.globalOffset.set(0, 0, 0);
    if (hasRoot || (this.shouldPartBeAffected("centerRotation") && animation.bones.has("centerRotation"))) data.centerRotation.set(0, 0, 0, 0);
    animation.bones.forEach(function (bone, key) {
      if (!self.shouldPartBeAffected(key)) return;
      var part = data.getPartForName(key);
      if (part && part.isModelPart) { part.getRotation().set(0, 0, 0, 0); part.getOffset().set(0, 0, 0); }
    });
  };
  function tweenVectorAdditiveSmooth(target, a, b, tween, amount) {
    target.add((a[0] + (b[0] - a[0]) * tween) * amount, (a[1] + (b[1] - a[1]) * tween) * amount, (a[2] + (b[2] - a[2]) * tween) * amount); target.finish();
  }
  function tweenOrientationAdditive(target, a, b, tween, amount) {
    target.add((a[0] + (b[0] - a[0]) * tween) * amount, (a[1] + (b[1] - a[1]) * tween) * amount, (a[2] + (b[2] - a[2]) * tween) * amount, (a[3] + (b[3] - a[3]) * tween) * amount);
  }
  KLS.applyKeyframeAnimation = function (data, animation, keyframeIndex, amount) {
    if (animation == null) return;
    var self = this, frameA = jint(keyframeIndex), frameB = frameA + 1, tween = keyframeIndex - frameA, k, n;
    if (this.shouldPartBeAffected("root") && animation.bones.has("root")) {
      var root = animation.bones.get("root"); k = root.keyframes[frameA]; n = root.keyframes[frameB];
      if (k && n) tweenVectorAdditiveSmooth(data.globalOffset, k.position, n.position, tween, amount);
    }
    if (this.shouldPartBeAffected("centerRotation") && animation.bones.has("centerRotation")) {
      var cr = animation.bones.get("centerRotation"); k = cr.keyframes[frameA]; n = cr.keyframes[frameB];
      if (k && n) { tweenOrientationAdditive(data.centerRotation, k.rotation, n.rotation, tween, amount); tweenVectorAdditiveSmooth(data.globalOffset, k.position, n.position, tween, amount); }
    }
    animation.bones.forEach(function (bone, key) {
      if (!self.shouldPartBeAffected(key)) return;
      var part = data.getPartForName(key);
      if (part == null) return;
      var a = bone.keyframes[frameA], b = bone.keyframes[frameB];
      if (a && b && part.isModelPart) {
        tweenOrientationAdditive(part.getRotation(), a.rotation, b.rotation, tween, amount);
        var o = part.getOffset(), am = -amount;
        o.add((a.position[0] + (b.position[0] - a.position[0]) * tween) * am, (a.position[1] + (b.position[1] - a.position[1]) * tween) * am, (a.position[2] + (b.position[2] - a.position[2]) * tween) * am);
      }
    });
  };
  function KumoAnimatorState(template) {
    if (!template.layers) throw new Error("No layers were specified");
    this.layerStates = template.layers.map(function (l) { if (l.type !== "KEYFRAME") return null; return new KeyframeLayerState(l); }).filter(Boolean);
    this.context = { entityData: null, layerState: null, currentNode: null }; this.started = false;
  }
  KumoAnimatorState.prototype.update = function (data, dt) {
    var ctx = this.context; ctx.entityData = data;
    for (var i = 0; i < this.layerStates.length; i++) { var l = this.layerStates[i]; ctx.layerState = l; if (!this.started) l.start(ctx); l.update(ctx, dt); }
    this.started = true;
  };
  // goblinbob.mobends.core.animation.keyframe.BinaryAnimationLoader (big-endian, like DataInputStream)
  function loadBinaryAnimation(bytes) {
    var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), at = 0, header = "";
    for (var h = 0; h < 9; h++) header += String.fromCharCode(view.getUint8(at++));
    if (header !== "BENDSANIM") throw new Error("File doesn't start with the header.");
    at += 4; // version
    var keyframes = view.getInt32(at); at += 4;
    var boneCount = view.getInt32(at); at += 4;
    var bones = new Map();
    for (var i = 0; i < boneCount; i++) {
      var name = [], c;
      while ((c = view.getUint8(at++)) !== 0) name.push(c);
      var boneName = decodeURIComponent(escape(String.fromCharCode.apply(null, name))), frames = [];
      for (var j = 0; j < keyframes; j++) {
        var flags = view.getInt8(at++), f = {};
        if (flags & 1) { f.position = [view.getFloat32(at), view.getFloat32(at + 4), view.getFloat32(at + 8)]; at += 12; } else f.position = [0, 0, 0];
        if (flags & 2) { f.rotation = [view.getFloat32(at), view.getFloat32(at + 4), view.getFloat32(at + 8), view.getFloat32(at + 12)]; at += 16; } else f.rotation = [0, 0, 0, 1];
        if (flags & 4) { f.scale = [view.getFloat32(at), view.getFloat32(at + 4), view.getFloat32(at + 8)]; at += 12; } else f.scale = [1, 1, 1];
        frames.push(f);
      }
      bones.set(boneName, { keyframes: frames });
    }
    return { bones: bones };
  }
  var WOLF_ANIMATOR = { layers: [
    { type: "KEYFRAME", entryNode: 0, nodes: [
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_idle.bendsanim", playbackSpeed: 1, looping: true, connections: [
        { targetNodeIndex: 1, transitionDuration: 4, triggerCondition: { type: "core:state", state: "MOVING_HORIZONTALLY" } },
        { targetNodeIndex: 2, transitionDuration: 3, triggerCondition: { type: "mobends:wolf_state", state: "SITTING" } }] },
      { type: "core:movement", animationKey: "mobends:bends/animations/wolf_walking.bendsanim", playbackSpeed: 4, connections: [
        { targetNodeIndex: 0, transitionDuration: 4, triggerCondition: { type: "core:state", state: "STANDING_STILL" } },
        { targetNodeIndex: 2, transitionDuration: 1, triggerCondition: { type: "mobends:wolf_state", state: "SITTING" } }] },
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_sitting_down.bendsanim", playbackSpeed: 2, startFrame: 6, connections: [
        { targetNodeIndex: 3, transitionDuration: 1, triggerCondition: { type: "core:animation_finished" } }] },
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_sitting.bendsanim", playbackSpeed: 1, startFrame: 0, looping: true, connections: [
        { targetNodeIndex: 4, transitionDuration: 1, triggerCondition: { type: "core:not", condition: { type: "mobends:wolf_state", state: "SITTING" } } }] },
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_standing_up.bendsanim", playbackSpeed: 2, startFrame: 4, connections: [
        { targetNodeIndex: 0, transitionDuration: 1, triggerCondition: { type: "core:animation_finished" } }] }] },
    { type: "KEYFRAME", entryNode: 0, additive: true, mask: { mode: "INCLUDE_ONLY", includedParts: ["tongue", "mouth"] }, nodes: [
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_idle.bendsanim", playbackSpeed: 1, looping: true, connections: [
        { targetNodeIndex: 1, transitionDuration: 4, triggerCondition: { type: "core:ticks_passed", ticksToPass: "80" } }] },
      { type: "core:standard", animationKey: "mobends:bends/animations/wolf_breathing.bendsanim", playbackSpeed: 1.2, looping: true, connections: [
        { targetNodeIndex: 0, transitionDuration: 4, triggerCondition: { type: "core:ticks_passed", ticksToPass: "50" } }] }] }
  ] };
  function WolfController() {
    try { this.kumoAnimatorState = new KumoAnimatorState(WOLF_ANIMATOR); }
    catch (e) { this.kumoAnimatorState = null; N.report && N.report("wolf_animator", e); }
  }
  WolfController.prototype.perform = function (data) {
    var wolf = data.getEntity(), E = N.entity, pt = DataUpdateHandler.partialTicks, ticks = E.ticksExisted(wolf) + pt;
    if (this.kumoAnimatorState) this.kumoAnimatorState.update(data, DataUpdateHandler.ticksPerFrame);
    if (E.isChild(wolf)) { data.head.offsetScale = 0.5; data.head.globalOffset.set(0, 5, -2); }
    else { data.head.offsetScale = 1; data.head.globalOffset.set(0, 0, 0); }
    data.head.position.set(0, -0.5, -13);
    data.head.rotation.localRotateY(data.headYaw.get()).finish();
    data.head.rotation.localRotateX(data.headPitch.get()).finish();
    data.head.rotation.localRotateZ((E.wolfInterestedAngle(wolf, pt) + E.wolfShakeAngle(wolf, pt, 0)) * RAD_TO_DEG).finish();
    data.mane.rotation.localRotateZ(E.wolfShakeAngle(wolf, pt, -0.08) * RAD_TO_DEG).finish();
    data.tail.rotation.localRotateZ(E.wolfShakeAngle(wolf, pt, -0.2) * RAD_TO_DEG).finish();
    data.tail.rotation.localRotateZ(E.wolfInterestedAngle(wolf, pt) * MathHelper.sin(ticks) * 20).finish();
    data.tail.rotation.localRotateX(E.wolfTailRotation(wolf) * RAD_TO_DEG - 90).finish();
    data.head.offset.set(0, 0, 0);
  };

  // ---- goblinbob.mobends.standard.main.ModConfig (defaults of the original release)
  var ModConfig = { showArrowTrails: true, showSwordTrail: true, performSpinAttack: true };

  // ---- goblinbob.mobends.core.client.MutatedRenderer and the standard renderers
  function MutatedRenderer(kind) { this.kind = kind; this.scale = 0.0625; }
  MutatedRenderer.interpolateRotation = function (prev, cur, pt) { var f; for (f = cur - prev; f < -180; f += 360); while (f >= 180) f -= 360; return prev + pt * f; };
  MutatedRenderer.prototype.beforeRender = function (data, entity, pt) {
    var E = N.entity, gl = N.gl, scale = this.scale;
    var ex = E.prevPosX(entity) + (E.posX(entity) - E.prevPosX(entity)) * pt;
    var ey = E.prevPosY(entity) + (E.posY(entity) - E.prevPosY(entity)) * pt;
    var ez = E.prevPosZ(entity) + (E.posZ(entity) - E.prevPosZ(entity)) * pt;
    var view = N.viewEntity(), vx = ex, vy = ey, vz = ez;
    if (view != null) {
      vx = E.prevPosX(view) + (E.posX(view) - E.prevPosX(view)) * pt;
      vy = E.prevPosY(view) + (E.posY(view) - E.prevPosY(view)) * pt;
      vz = E.prevPosZ(view) + (E.posZ(view) - E.prevPosZ(view)) * pt;
    }
    var yaw = MutatedRenderer.interpolateRotation(E.prevRenderYawOffset(entity), E.renderYawOffset(entity), pt);
    gl.translate(ex - vx, ey - vy, ez - vz);
    gl.rotate(-yaw, 0, 1, 0);
    this.renderLocalAccessories(entity, data, pt);
    var globalScale = E.isChild(entity) ? 0.5 : 1, h = E.height(entity);
    gl.translate(data.globalOffset.getX() * scale * globalScale, data.globalOffset.getY() * scale * globalScale, data.globalOffset.getZ() * scale * globalScale);
    gl.translate(0, h / 2, 0); GlHelper.rotate(data.centerRotation.getSmooth()); gl.translate(0, -h / 2, 0);
    GlHelper.rotate(data.renderRotation.getSmooth());
    gl.translate(data.localOffset.getX() * scale * globalScale, data.localOffset.getY() * scale * globalScale, data.localOffset.getZ() * scale * globalScale);
    this.transformLocally(entity, data, pt);
    gl.rotate(yaw, 0, 1, 0);
    gl.translate(vx - ex, vy - ey, vz - ez);
  };
  MutatedRenderer.prototype.renderLocalAccessories = function (entity, data) {
    if (this.kind !== "biped" && this.kind !== "player") return;
    if (data.isBiped && ModConfig.showSwordTrail && data.swordTrail.trailPartList.length) {
      N.gl.push(); N.gl.scale(0.0625, 0.0625, 0.0625); data.swordTrail.render(); N.gl.color(1, 1, 1, 1); N.gl.pop();
    }
  };
  MutatedRenderer.prototype.transformLocally = function (entity) {
    if (this.kind === "biped") { if (N.entity.isSneaking(entity)) N.gl.translate(0, 5 * this.scale, 0); }
    else if (this.kind === "player") { if (N.entity.isSneaking(entity)) N.gl.translate(0, (N.entity.capabilitiesFlying(entity) ? 4 : 5) * this.scale, 0); }
  };

  // ---- the BendsCapeRenderer wave (16 slabs); geometry and drawing live in the model module
  var SLAB_AMOUNT = 16;
  function capeSlabAngles(data, out) {
    var phase = data.getCapeWavePhase();
    for (var i = 0; i < SLAB_AMOUNT; i++) {
      var waveOffset = i / SLAB_AMOUNT, magnitude = 80 / SLAB_AMOUNT * (0.7 + ((i / SLAB_AMOUNT) | 0));
      out[i] = Math.fround(Math.cos(phase * 0.2 + waveOffset * 7.2) * magnitude);
    }
    out[0] += -10;
    return out;
  }

  // ---- benders (the entity classes Mo' Bends animates) and the mutator bookkeeping
  var BenderRegistry = {
    cache: new WeakMap(),
    clearCache: function (entity) { if (entity) this.cache.delete(entity); },
    clearAll: function () { this.cache = new WeakMap(); }
  };

  return {
    PI: PI, MathHelper: MathHelper, GUtil: GUtil, Tween: Tween, Vec3f: Vec3f, Quaternion: Quaternion, QuaternionUtils: QuaternionUtils, GlHelper: GlHelper,
    SmoothOrientation: SmoothOrientation, SmoothVector3f: SmoothVector3f, ModelPartTransform: ModelPartTransform, applyCharacter: applyCharacter, applyLocal: applyLocal,
    DataUpdateHandler: DataUpdateHandler, EntityDatabase: EntityDatabase, BenderRegistry: BenderRegistry, ModConfig: ModConfig,
    EntityData: EntityData, LivingEntityData: LivingEntityData, BipedEntityData: BipedEntityData, PlayerData: PlayerData,
    ZombieData: ZombieData, PigZombieData: PigZombieData, SkeletonData: SkeletonData, SpiderData: SpiderData, SquidData: SquidData, WolfData: WolfData,
    TENTACLE_SECTIONS: TENTACLE_SECTIONS, SECTION_HEIGHT: SECTION_HEIGHT, SLAB_AMOUNT: SLAB_AMOUNT, capeSlabAngles: capeSlabAngles,
    MutatedRenderer: MutatedRenderer, HardAnimationLayer: HardAnimationLayer, loadBinaryAnimation: loadBinaryAnimation, KumoAnimatorState: KumoAnimatorState,
    WOLF_ANIMATOR: WOLF_ANIMATOR, SwordTrail: SwordTrail, putLimbOnGround: putLimbOnGround, jint: jint
  };
}
if (typeof module !== "undefined" && module.exports) module.exports = { createJasprMoBendsCore: createJasprMoBendsCore };
