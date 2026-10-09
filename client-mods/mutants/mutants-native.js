/* JasperCraft Mutant Creatures: native adapter for the TeaVM-compiled EaglercraftX 1.12.2 client.
 *
 * The rest of the stage is a line-by-line translation of the client side of Mutant Creatures Legacy (Mutant Beasts
 * 1.12.2, chumbanotz and contributors, GNU AGPL-3.0, see client-mods/mutants/LICENSE and NOTICE). This file isolates
 * every minified engine name the translation uses, so the translated units read like the Java they come from:
 *  - engine fields and methods get readable aliases on the engine prototypes (names of 5+ characters or with a
 *    trailing "_"; TeaVM's minified names are at most 4 characters, and the installer refuses any collision),
 *  - classes are real TeaVM subclasses ($rt_metadata), so instanceof, $rt_isInstance and virtual dispatch of the
 *    engine reach the translated overrides exactly as the JVM reaches the mod's overrides under Forge,
 *  - super calls go to the Java-correct implementation (TeaVM drops vtable entries of intermediate classes whose
 *    method every vanilla subclass overrides; those are listed in SUPER_IMPL).
 * Loading this file touches no engine state; everything is installed by JasprMutants.install() from the registry hook
 * (end of Bootstrap.register), after TeaVM's eager static initialisers have run.
 */
var JasprMutants = (function () {
  "use strict";
  var M = {};
  M.version = "mutants-client-1";
  M.PROTOCOL_VERSION = 1;

  // ================================================================ TeaVM runtime
  function jstr(s) { return s === null || s === undefined ? null : $rt_str(String(s)); }
  function ustr(s) { return s === null || s === undefined ? null : $rt_ustr(s); }
  M.jstr = jstr; M.ustr = ustr;
  var jstrCache = Object.create(null);
  // cached Java string for a constant (never mutated by the engine)
  function JS(s) { var v = jstrCache[s]; if (v === undefined) { v = $rt_str(s); jstrCache[s] = v; } return v; }
  M.JS = JS;
  M.isInstance = function (o, cls) { return o !== null && o !== undefined && $rt_isInstance(o, cls); };
  M.classOf = function (cls) { return E(cls); };              // java.lang.Class object of a JS class
  // Java primitive conversions (TeaVM keeps floats as doubles, like the engine itself)
  M.f = Math.fround;                                          // a Java float literal as TeaVM emits it
  M.toInt = function (x) { return x | 0; };
  M.toByte = function (x) { return (x << 24) >> 24; };
  M.toShort = function (x) { return (x << 16) >> 16; };
  M.idiv = function (a, b) { return (a / b) | 0; };
  M.f2i = function (x) { return x !== x ? 0 : x >= 2147483647 ? 2147483647 : x <= -2147483648 ? -2147483648 : (x | 0); };
  // boxing as the engine does it
  M.boxByte = function (b) { return Es(b); };                  // new Byte (UJ, .eg)
  M.boxInt = function (i) { return U(i); };                    // Integer.valueOf (.bn)
  M.boxFloat = function (f) { return D4(f); };                 // Float.valueOf (.fB)
  M.boxBool = function (b) { return Bu(b ? 1 : 0); };          // Boolean.valueOf (.br)
  M.unboxByte = function (o) { return o.eg; };
  M.unboxInt = function (o) { return o.bn; };
  M.unboxFloat = function (o) { return o.fB; };
  M.unboxBool = function (o) { return !!o.br; };
  M.intArray = function (n) { return Bh(n); };
  M.intArrayOf = function (arr) { return CN(arr); };
  M.long = function (n) { return N(n); };                     // Long.fromInt
  M.longToNumber = function (l) { return DU(l); };
  M.arrayList = function () { return Bq(); };
  // java.util.List through its interface methods (size = bl, get = c4): works for every List implementation
  M.listSize = function (l) { return l.bl(); };
  M.listGet = function (l, i) { return l.c4(i); };
  M.listAdd = function (l, x) { Y(l, x); };
  M.listRemoveAt = function (l, i) { return HB(l, i); };
  M.listRemove = function (l, o) { return !!l.F0(o); };      // List.remove(Object)
  M.listClear = function (l) { l.oC(); };                   // List.clear() (AbstractList: removeRange(0, size()))
  M.listToArray = function (l) { if (!l) return []; if (l.qN && typeof l.g === "number") return Array.prototype.slice.call(l.qN.data, 0, l.g); var n = l.bl(), a = []; for (var i = 0; i < n; i++) a.push(l.c4(i)); return a; };

  // ================================================================ readable aliases on engine prototypes
  // FIELDS[jsClass] = { readableName: minifiedField }
  // Java field declaration order checked against the TeaVM constructor of each class (scripts: see MUTANTS_CLIENT_NOTES).
  var FIELDS = {
    Eg: { // net.minecraft.entity.Entity
      entityId: "cu", preventEntitySpawning: "a8k", riddenByEntities: "a1k", rideCooldown: "bng", ridingEntity: "fS",
      world: "a", prevPosX: "dn", prevPosY: "d9", prevPosZ: "dv", posX: "b", posY: "f", posZ: "c",
      motionX: "s", motionY: "p", motionZ: "t", rotationYaw: "C", rotationPitch: "bd", prevRotationYaw: "cy",
      prevRotationPitch: "c2", boundingBox_: "bc", onGround: "bQ", collidedHorizontally: "vj", collidedVertically: "a5G",
      collided: "efC", velocityChanged: "S6", isInWeb: "a31", isDead: "ed", width: "bI", height: "bZ",
      prevDistanceWalkedModified: "UA", distanceWalkedModified: "Iy", fallDistance: "ku", lastTickPosX: "fj",
      lastTickPosY: "e2", lastTickPosZ: "fk", stepHeight: "r5", noClip: "tl", rand: "h", ticksExisted: "cv",
      fire_: "os", inWater: "e1", hurtResistantTime: "hx", firstUpdate: "bbm", isImmuneToFire: "ql", dataManager: "y",
      addedToChunk: "tO", serverPosX: "cHl", serverPosY: "cHi", serverPosZ: "cHj", ignoreFrustumCheck: "che",
      isAirBorne: "pU", dimension: "iE", entityUniqueID: "fY"
    },
    Co: { // net.minecraft.entity.EntityLivingBase
      isSwingInProgress: "G1", swingingHand: "crX", swingProgressInt: "a2f", hurtTime: "o2", maxHurtTime: "bkB",
      attackedAtYaw: "FI", livingDeathTime_: "uS", prevSwingProgress: "dY8", swingProgress: "bYC",
      prevLimbSwingAmount: "qi", limbSwingAmount: "hp", limbSwing: "CE", renderYawOffset: "cZ",
      prevRenderYawOffset: "s1", rotationYawHead: "gN", prevRotationYawHead: "zM", jumpMovementFactor: "tG",
      attackingPlayer: "Jp", recentlyHit: "PK", dead: "bF0", isJumping: "O8", moveStrafing: "nR", moveForward: "ZD",
      moveVertical: "jS", randomYawVelocity: "qO", newPosRotationIncrements: "a5W", activeItemStack: "l0",
      activeItemStackUseCount: "wS", ticksElytraFlying: "bwP"
    },
    Gj: { // net.minecraft.entity.EntityLiving
      livingSoundTime: "bAT", experienceValue: "De", navigator: "cw", attackTarget_: "c3"
    },
    AMR: { growingAge_: "dTA", forcedAge: "bYA", forcedAgeTimer: "bkQ", ageWidth: "b_8", ageHeight: "dyr" },
    KH: { inLove: "QQ" },
    S5: { aiSit: "sm" },
    Kw: { creeperLastActiveTime: "dN1", creeperTimeSinceIgnited: "A5", fuseTime: "baG", explosionRadius: "ctV" },
    Vh: { thrower: "Fq", throwerName: "a4Y", ignoreEntity: "cgP", inGround: "b7c" }
  };
  // METHODS[jsClass] = { readableName: "vname" (virtual) | function (non-virtual implementation, receiver first) }
  var METHODS = {
    Eg: {
      onUpdate: "dt", onEntityUpdate: "cXJ", entityInit: "et", handleStatusUpdate: "q2", notifyDataManagerChange: "BF",
      getName: "b1", setDead: "W7", setPosition: "RW", setLocationAndAngles: "eaF", setPositionAndRotationDirect: "bbp",
      setVelocity: "bAZ", canBeCollidedWith: "DI", canBePushed: "blF", canTriggerWalking: "Bq", getCollisionBox: "dbE",
      hitByEntity: "eh2", getYOffset: "bof", getMountedYOffset: "brb", processInitialInteract: "a71",
      attackEntityFrom: "kb", isInRangeToRender3d: "d7q", isInRangeToRenderDist: "NN", getSoundCategory: "a6$",
      doesEntityNotTriggerPressurePlate: "d6S", applyEntityCollision: "bRW", canBeAttackedWithItem: "btu",
      isEntityEqual: "ehH", getTeam: "a_Z", isOnSameTeam: "bb_", getEyeHeight: "hz", fall: "AT",
      getBrightnessForRender: "be9", getBrightness: "btE", playSound: "zS", isEntityAlive: "ekW", isSneaking: "q1",
      isInWater: "eg$", isEntityInsideOpaqueBlock: "dmw", getControllingPassenger: "crJ", canPassengerSteer: "cKw",
      updatePassenger: "b8u", dismountRidingEntity: "b7r", startRiding2: "cCk", getParts: "dTG", canBeRidden: "dzR",
      isPushedByWater: "dRH", setInWeb: "cX$", onStruckByLightning: "bIu", isImmuneToExplosions: "ejT",
      onKillCommand: "ctS", isNonBoss: "dg_", updateFallState: "bGu", isEntityInvulnerable: "ctI",
      applyPlayerInteraction: "d8z", getPositionVector: "LE", getEntityWorld: "nx", getPosition: "sF",
      moveEntity: "a3S", addVelocity: "ee1", getLook: "bWS", setCustomNameTag: "cXn", setPositionAndUpdate: "bqY",
      getCollisionBoundingBox: "cNu", getHorizontalFacing: "elM", getRenderBoundingBox: "c50", isBurning: "bHM",
      // non-virtual
      getEntityBoundingBox: CK1, setSize: function (e, w, h) { FET(e, Math.fround(w), Math.fround(h)); }, setRotation: Egc, setPositionAndRotation: FnF, setEntityBoundingBox: D7x, isRiding: E9Z,
      isBeingRidden: FD0, getRidingEntity: CqZ, getPassengers: Gr5, startRiding: DHE, hasCustomName: F4L,
      getCustomNameTag: EMG, getAlwaysRenderNameTag: CWv, setAlwaysRenderNameTag: EDX, isSilent: C31, getFlag: EuW,
      setFlag: D9n, isInvisible: DfJ, isGlowing: F_O, isWet: CP$, isInLava: GtN, hasNoGravity: FuH,
      getLookVec: Dyw, getPositionEyes: DWa, getDistanceSq3: DXH, getDistanceSqToBlock: DkD, getDistance3: Fr$,
      setFire: FE7, extinguish: FJ8, getUniqueID: DNI, getEntityId: DCP, setEntityId: EpF, setUniqueId: F63,
      getDataManager: Gzc, isPassenger: EcG, isRidingSameEntity: DIO, removePassengers: FX4,
      isOffsetPositionInLiquid: FP7, moveToBlockPosAndAngles: Exi, isInsideOfMaterial: DBf,
      getDistanceSqToEntity: B_J, getDistanceToEntity: GE2
    },
    Co: {
      onLivingUpdate: "nP", onDeathUpdate: "d$5", applyEntityAttributes: "hg", getCreatureAttribute: "Xn",
      updateDistance: "dnP", travel: "bmY", isMovementBlocked: "cGC", isOnLadder: "cxy", canBeHitWithPotion: "ef1",
      isPotionApplicable: "eiJ", getHurtSound: "gj", getDeathSound: "gm", getSoundVolume: "Za", getSoundPitch: "b_C",
      handleJumpLava: "ekI", onDeath: "P6", heal: "b6$", collideWithNearbyEntities: "dAx", isChild: "bV5",
      getAIMoveSpeed: "eiD", swingArm: "bWO", setRevengeTarget: "dyu", isPlayerSleeping: "edh", isServerWorld: "cWG",
      setSprinting: "djy", getRotationYawHead: "d9n", setRotationYawHead: "dj7", setRenderYawOffset: "cnz",
      resetActiveHand: "dqN",
      // non-virtual
      setHealth: DVZ, getHealth: ENU, getMaxHealth: Crp, canEntityBeSeen: CK7, getAttributeMap: EXX,
      getEntityAttribute: EAj, setAIMoveSpeed: E4o, getHeldItem: CjH, getHeldItemMainhand: EZ5,
      getHeldItemOffhand: EjD, isPotionActive: CcO, getActivePotionEffect: DxM, getActivePotionEffects: F9x,
      isElytraFlying: EKz, isActiveItemStackBlocking: Ctq, getSwingProgress: C3W, getTotalArmorValue: Ez7,
      dismountEntity: GdW, getRNG: Cr8, knockBack: Cm7, handleJumpWater: DGp, getActiveItemStack: F6Q
    },
    Gj: {
      initEntityAI: "lf", processInteract: "yS", canBeSteered: "cKU", canBeLeashedTo: "b1I", getMaxFallHeight: "ebg", setAttackTarget: "IL",
      playLivingSound: "ejn", getAmbientSound: "hD", getTalkInterval: "b2V", canDespawn: "bqF",
      getMaxSpawnedInChunk: "cuG", getLootTable: "f1", updateAITasks: "A2", canAttackClass: "efD",
      // non-virtual
      getAttackTarget: EdM, getLeashed: CpF, isAIDisabled: CD0, setNoAI: EJo, getNavigator: CU3, isNoDespawnRequired: DSK,
      enablePersistence: C7V, getEntitySenses: Cj4, getLookHelper: Dfi
    },
    AMR: {
      setScaleForAge: "en5",
      // non-virtual (EntityAgeable.setSize is its own final method, as in Java)
      setSize: function (e, w, h) { EOX(e, Math.fround(w), Math.fround(h)); }, setScale: CPc, getGrowingAge: FcM, setGrowingAge: FbH, ageUp: ENT
    },
    KH: { isBreedingItem: "a$Y", consumeItemFromStack: De_, setInLove: End, isInLove: S7 },
    S5: {
      setTamed: "eaq",
      isTamed: Cm2, isSitting: F9J, setSitting: DLX, getOwnerId: EjC, setOwnerId: ENE, getOwner: FOL, isOwner: F78,
      playTameEffect: EOK
    },
    Kw: { getCreeperFlashIntensity: C_j, hasIgnited: CmQ, getCreeperState: D77, setCreeperState: FuW, getPowered: CPF, ignite: EmM },
    Vh: { getGravityVelocity: "cDw", setThrowableHeading: "dr9", onImpact: "Xk", getThrower: CVu },   // onImpact: abstract in EntityThrowable (no vtable entry)
    Cb: { isCreative: "a58", isSpectator: "mH", getLeftShoulderEntity: Ec6, getRightShoulderEntity: Dcd, isAllowEdit: Gpw, getCooldownTracker: Dk6 }
  };
  // Super implementations whose TeaVM prototype resolution differs from Java (the method has no vtable entry in the
  // declaring class because every vanilla subclass overrides it). SUPER_IMPL[jsClass][vname] = implementation.
  var SUPER_IMPL = {
    H0: { hg: DKW },            // EntityMob.applyEntityAttributes (TeaVM would resolve EntityLiving's)
    S5: { et: FIi },            // EntityTameable.entityInit (TeaVM would resolve EntityAgeable's)
    BfX: { et: FIi }
  };
  M.FIELDS = FIELDS; M.METHODS = METHODS; M.SUPER_IMPL = SUPER_IMPL;
  var CLASSES = { Eg: Eg, Co: Co, Gj: Gj, N3: N3, H0: H0, Kw: Kw, AHO: AHO, AMR: AMR, KH: KH, S5: S5, BfX: BfX, Vh: Vh, Cb: Cb, Vf: Vf,
    TR: TR };                                                  // TR EntitySnowball: implements the abstract onImpact (Xk)
  M.engineClass = function (name) { return CLASSES[name]; };
  // TeaVM's minified member names in this client are at most 3 characters (virtual names) / 3-4 (fields): readable
  // aliases have at least 4 characters and may not shadow anything the engine already has on the prototype chain.
  function ownedAlias(proto, name) {
    for (var p = proto; p; p = Object.getPrototypeOf(p)) {
      var d = Object.getOwnPropertyDescriptor(p, name);
      if (d) return !!(d.get && d.get.$jmAlias);
    }
    return null;
  }
  function defineAlias(proto, name, desc, owner) {
    if (name.length < 4) throw new Error("alias too short " + name);
    var own = Object.getOwnPropertyDescriptor(proto, name);
    if (own) { if (own.get && own.get.$jmAlias) return; throw new Error("alias collision " + owner + "." + name); }
    var inherited = ownedAlias(proto, name);
    if (inherited === false) throw new Error("alias would shadow an engine member " + owner + "." + name);
    Object.defineProperty(proto, name, desc);
  }
  function fieldAlias(min) {
    var g = function () { return this[min]; }; g.$jmAlias = true;
    var s = function (v) { this[min] = v; };
    return { get: g, set: s, enumerable: false, configurable: true };
  }
  function methodAlias(target, readable) {
    var f;
    if (typeof target === "string") {
      f = function () { var m = this[target]; return m.apply(this, arguments); };
    } else {
      f = function () {
        switch (arguments.length) {
          case 0: return target(this);
          case 1: return target(this, arguments[0]);
          case 2: return target(this, arguments[0], arguments[1]);
          case 3: return target(this, arguments[0], arguments[1], arguments[2]);
          default: var a = [this]; for (var i = 0; i < arguments.length; i++) a.push(arguments[i]); return target.apply(null, a);
        }
      };
    }
    f.$jmAlias = true; f.$jmTarget = target;
    var g = function () { return f; }; g.$jmAlias = true;
    return { get: g, enumerable: false, configurable: true };
  }
  var aliasesInstalled = false;
  M.installAliases = function (fields, methods, classes) {
    var name, k, proto;
    for (name in fields) { proto = classes[name].prototype; for (k in fields[name]) defineAlias(proto, k, fieldAlias(fields[name][k]), name); }
    for (name in methods) {
      proto = classes[name].prototype;
      for (k in methods[name]) {
        var t = methods[name][k];
        if (t === null) continue;
        if (typeof t === "string" && typeof proto[t] !== "function") {
          // abstract in this class (e.g. Entity.entityInit): the name must exist on some engine subclass
          var seen = false;
          for (var cn in classes) if (classes[cn].prototype instanceof classes[name] && typeof classes[cn].prototype[t] === "function") seen = true;
          if (!seen) throw new Error("missing virtual " + name + "." + k + " -> " + t);
        }
        if (typeof t !== "string" && typeof t !== "function") throw new Error("missing native " + name + "." + k);
        defineAlias(proto, k, methodAlias(t, k), name);
      }
    }
  };
  // virtual name of a readable method (searching the tables), for overrides
  // group: the class hierarchy the readable name belongs to ("entity" by default; "model", "render", "layer",
  // "particle", "item", "gui"): TeaVM gives one virtual name per Java method descriptor, the groups keep readable
  // names of different hierarchies apart.
  function vnameOf(readable, group) {
    if (group && group !== "entity") return EXTRA_V[group] && EXTRA_V[group][readable] ? EXTRA_V[group][readable] : null;
    for (var c in METHODS) { var t = METHODS[c][readable]; if (typeof t === "string") return t; }
    return null;
  }
  var EXTRA_V = {};                                           // other hierarchies register their virtual names here
  M.registerVirtuals = function (group, map) { EXTRA_V[group] = map; };
  M.vname = vnameOf;

  // ================================================================ class definition
  // spec: { name, extend, ifaces, fields(self) (zero defaults, like TeaVM's JS constructor), methods: {readable: fn},
  //         virtuals: {vname: fn} }
  M.defineClass = function (spec) {
    var P = spec.extend, fieldsFn = spec.fields;
    var C = function () { P.call(this); if (fieldsFn) fieldsFn(this); };
    $rt_metadata([C, spec.name, -1, P, spec.ifaces || [], 0, 3, 0, 0, 0]);
    C.$jm = { name: spec.name, parent: P };
    if (spec.methods) M.override(C, spec.methods, spec.group, spec.nonVirtual);
    if (spec.virtuals) for (var v in spec.virtuals) C.prototype[v] = spec.virtuals[v];
    return C;
  };
  // Engine entry points of the entity twins (the world tick, status bytes, interaction, data watcher, movement). An
  // exception there must not reach the engine (a client crash): it is reported once (part "entities") and the entity
  // is removed from this client's world; the server keeps it. readSpawnData is not guarded: the SPAWN reader catches.
  var ENTRY_GUARDS = { onUpdate: 1, onLivingUpdate: 1, handleStatusUpdate: 1, processInteract: 1, notifyDataManagerChange: 1,
    travel: 1, onDeathUpdate: 1, updatePassenger: 1, onImpact: 1 };
  function guardEntry(k, fn) {
    return function () {
      try { return fn.apply(this, arguments); }
      catch (e) {
        M.fail("entities", e);
        try { if (this.world && this.world.r) this.W7(); } catch (_) {}   // setDead on a client world
        return k === "processInteract" ? 0 : undefined;
      }
    };
  }
  // installs overrides under the virtual name (engine dispatch, guarded for entry points) and the readable name
  // (translated code)
  M.override = function (C, methods, group, nonVirtual) {
    for (var k in methods) {
      var fn = methods[k], v = nonVirtual && nonVirtual.indexOf(k) >= 0 ? null : vnameOf(k, group);
      Object.defineProperty(C.prototype, k, { value: fn, writable: true, enumerable: false, configurable: true });
      if (v) C.prototype[v] = ENTRY_GUARDS[k] ? guardEntry(k, fn) : fn;
    }
  };
  // Renderer, layer, particle and item overrides: when their part is off, or throws, the engine gets the fallback
  // (nothing drawn, vanilla item behaviour) instead of an exception.
  M.guardVirtual = function (part, name, fn, fallback) {
    return function () {
      if (!M.enabled(part)) return fallback ? fallback.apply(this, arguments) : undefined;
      try { return fn.apply(this, arguments); }
      catch (e) {
        if (part === "render") stats.renderErrors++;
        M.fail(part, e);
        return fallback ? fallback.apply(this, arguments) : undefined;
      }
    };
  };
  // super.<readable>(...) of Java: the nearest Java declaration in the parent chain
  M.superOf = function (P, group) {
    var cache = Object.create(null);
    return new Proxy({}, { get: function (t, readable) {
      if (cache[readable]) return cache[readable];
      var v = vnameOf(readable, group), fn = null, c, cn;
      if (v) {
        for (c = P; c && !fn; c = c.$meta && c.$meta.superclass) {
          for (cn in CLASSES) if (CLASSES[cn] === c && SUPER_IMPL[cn] && SUPER_IMPL[cn][v]) { var impl = SUPER_IMPL[cn][v]; fn = function () { var a = [this]; for (var i = 0; i < arguments.length; i++) a.push(arguments[i]); return impl.apply(null, a); }; }
          if (fn) break;
          if (Object.prototype.hasOwnProperty.call(c.prototype, v)) { var pf = c.prototype[v]; fn = function () { return pf.apply(this, arguments); }; }
        }
        if (!fn) { var pv = P.prototype[v]; if (typeof pv === "function") fn = function () { return pv.apply(this, arguments); }; }
      } else {
        var d = P.prototype[readable];
        if (typeof d === "function") fn = d;
      }
      if (!fn) throw new Error("no super method " + readable);
      cache[readable] = fn;
      return fn;
    } });
  };

  // ================================================================ engine statics and helpers
  M.mc = function () { return HEH; };                         // Minecraft.getMinecraft()
  M.world = function () { return HEH ? HEH.X : null; };       // Minecraft.world (WorldClient)
  M.player = function () { return HEH ? HEH.v : null; };      // Minecraft.player (EntityPlayerSP)
  // java.util.Random (EaglercraftRandom)
  M.R = {
    nextInt: function (r, n) { return H(r, n); }, nextFloat: function (r) { return X(r); },
    nextDouble: function (r) { return BN(r); }, nextGaussian: function (r) { return Cv(r); },
    nextBoolean: function (r) { return !!DD(r); }
  };
  // net.minecraft.util.math.MathHelper (the engine's own lookup-table sin/cos)
  M.MH = {
    sin: function (x) { return D2_(x); }, cos: function (x) { return CiK(x); }, sqrt: function (x) { return CuM(x); },
    floor: function (x) { return Gmc(x); }, clampF: function (x, a, b) { return x < a ? a : x > b ? b : x; },
    clampI: function (x, a, b) { return x < a ? a : x > b ? b : x; }, wrapDegrees: function (x) { return D3g(x); },
    atan2: function (y, x) { return CJf(y, x); }
  };
  // World helpers (net.minecraft.world.World / WorldClient)
  M.W = {
    isRemote: function (w) { return !!w.r; },
    rand: function (w) { return w.R; },
    getEntityByID: function (w, id) { return w.baK(id); },
    getPlayerEntityByUUID: function (w, uuid) { return DQW(w, uuid); },
    isDaytime: function (w) { return !!OC(w); },
    getEntitiesInAABBexcluding: function (w, e, bb, pred) { return EcF(w, e, bb, pred); },
    getEntitiesWithinAABBExcludingEntity: function (w, e, bb) { return EzD(w, e, bb); },
    // World.spawnParticle(EnumParticleTypes, x, y, z, xs, ys, zs, int...) = spawnParticle(id, ignoreRange, ...)
    spawnParticle: function (w, type, x, y, z, xs, ys, zs, params) { Efj(w, type.jq, type.bVO, x, y, z, xs, ys, zs, params || Bh(0)); },
    spawnParticleIgnore: function (w, type, ignore, x, y, z, xs, ys, zs, params) { Efj(w, type.jq, type.bVO || ignore ? 1 : 0, x, y, z, xs, ys, zs, params || Bh(0)); },
    // World.playSound(player, x, y, z, ...) (WorldClient: plays only when player is the local player)
    playSound: function (w, player, x, y, z, ev, cat, vol, pitch) { w.elK(player, x, y, z, ev, cat, vol, pitch); },
    // World.setEntityState is empty on the client (WorldServer broadcasts); kept for line-by-line translation
    setEntityState: function () {},
    getBlockState: function (w, pos) { return w.cO(pos); },
    isAirBlock: function (w, pos) { return !!w.dXL(pos); },
    addEntityToWorld: function (w, id, e) { E05(w, id, e); }
  };
  M.Enum = {};                                                // filled at install (enum constants)

  // ================================================================ data parameters (EntityDataManager)
  M.DATA = {
    createKey: function (id, serializer) { return C0(id, serializer); },
    register: function (e, key, value) { FkW(e.y, key, value); },
    get: function (e, key) { return E24(e.y, key); },
    set: function (e, key, value) { B$8(e.y, key, value); },
    // serializers (DataSerializers clinit Fc())
    BYTE: function () { Fc(); return Ks6; }, VARINT: function () { Fc(); return Ks7; }, STRING: function () { Fc(); return Ks8; },
    BOOLEAN: function () { Fc(); return Ks9; }, FLOAT: function () { Fc(); return Kun; }, OPTIONAL_UNIQUE_ID: function () { Fc(); return Ky4; },
    absent: function () { return KyO; }                       // Optional.absent(), as EntityTameable.entityInit uses it
  };
  // parent data keys of the vanilla classes the mod extends (verified against the server's 1.12.2 ids at install)
  M.PARENT_KEYS = {
    Entity: [[0, "Ksn"], [1, "Kso"], [2, "Ksp"], [3, "Ksq"], [4, "Ksr"], [5, "Kss"]],
    EntityLivingBase: [[6, "Ktb"], [7, "Ktc"], [8, "Ktd"], [9, "Kte"], [10, "Ktf"]],
    EntityLiving: [[11, "Kxb"]], EntityAgeable: [[12, "Kx$"]], EntityTameable: [[13, "KCY"], [14, "KCZ"]],
    EntityCreeper: [[12, "Kyz"], [13, "KyA"], [14, "KyB"]]
  };
  M.parentKeyIds = function () {
    NN(); Ub(); ABH(); A9j(); ACw(); Zp();
    return { Entity: [Ksn, Kso, Ksp, Ksq, Ksr, Kss].map(keyId), EntityLivingBase: [Ktb, Ktc, Ktd, Kte, Ktf].map(keyId),
      EntityLiving: [Kxb].map(keyId), EntityAgeable: [Kx$].map(keyId), EntityTameable: [KCY, KCZ].map(keyId),
      EntityCreeper: [Kyz, KyA, KyB].map(keyId) };
  };
  function keyId(k) { return k.b0H; }                         // DataParameter.id
  M.keyId = keyId;

  // ================================================================ diagnostics and failure isolation
  var stats = { installs: 0, spawns: 0, spawnUnknown: 0, spawnErrors: 0, messages: 0, statusBytes: 0, renders: 0,
    renderErrors: 0, binds: 0, particles: 0, sounds: 0, screens: 0, teisr: 0, armor: 0, errors: 0, hello: 0, disabled: 0 };
  var disabled = Object.create(null);                          // part -> reason
  var diag = { sent: 0, page: "mutants-" + Date.now().toString(36), lastError: "" };
  M.stats = stats;
  function send(event, details) {
    try {
      var loc = $rt_globals.location;
      if (diag.sent >= 12 || !loc || String(loc.pathname).indexOf("/jaspercraft/") !== 0 || typeof $rt_globals.fetch !== "function") return;
      diag.sent++;
      $rt_globals.fetch("/api/diagnostics/events", { method: "POST", credentials: "same-origin", cache: "no-store",
        headers: { "Content-Type": "application/json", "X-Jaspergers-Client": "web-v1" },
        body: JSON.stringify({ events: [{ event: event, pageSessionId: diag.page, at: new Date().toISOString(), details: details }] }) }).catch(function () {});
    } catch (_) {}
  }
  function statsCopy() { var o = {}; for (var k in stats) o[k] = stats[k]; return o; }
  function shortError(e) { return String(e && e.message || e).replace(/[\r\n]+/g, " ").slice(0, 180); }
  M.state = function (what, extra) {
    var d = { state: String(what).slice(0, 40), version: M.version, stats: statsCopy() };
    if (extra) for (var k in extra) d[k] = extra[k];
    send("jaspercraft.mutants.state", d);
  };
  // A failing part is switched off for the page (the rest of the game and the other parts keep running).
  M.fail = function (part, error) {
    stats.errors++;
    diag.lastError = shortError(error);
    // local only (never sent): the first lines of the stack, for whoever reads JasprMutantsDiagnostics.status()
    try { if (!diag.lastStack) diag.lastStack = (part + ": " + String(error && error.stack || error)).split("\n").slice(0, 10).join(" | ").slice(0, 1200); } catch (_) {}
    if (!disabled[part]) { disabled[part] = diag.lastError; stats.disabled++; }
    try { if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft Mutants] " + part + ": " + diag.lastError); } catch (_) {}
    send("jaspercraft.mutants.error", { part: String(part).slice(0, 40), error: diag.lastError, stats: statsCopy() });
  };
  M.enabled = function (part) { return !disabled[part] && !disabled.all; };
  M.disabledParts = function () { var o = {}; for (var k in disabled) o[k] = disabled[k]; return o; };
  M.guard = function (part, fn, fallback) {
    return function () {
      if (!M.enabled(part)) return fallback === undefined ? undefined : (typeof fallback === "function" ? fallback.apply(this, arguments) : fallback);
      try { return fn.apply(this, arguments); }
      catch (e) { M.fail(part, e); return fallback === undefined ? undefined : (typeof fallback === "function" ? fallback.apply(this, arguments) : fallback); }
    };
  };
  M.diagnostics = function () {
    return { version: M.version, protocol: M.PROTOCOL_VERSION, installed: !!M.installed, ready: !!M.lateDone,
      textures: M.texturesReady ? M.texturesReady() : false, stats: statsCopy(),
      disabled: M.disabledParts(), lastError: diag.lastError, firstStack: diag.lastStack || "", eventsSent: diag.sent };
  };
  try { $rt_globals.JasprMutantsDiagnostics = Object.freeze({ status: function () { return M.diagnostics(); } }); } catch (_) {}

  M._aliasesInstalled = function () { return aliasesInstalled; };
  M._markAliases = function () { aliasesInstalled = true; };
  M.CLASSES = CLASSES;
  return M;
})();
