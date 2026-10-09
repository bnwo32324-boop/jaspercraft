/* client/animationapi/{Animator,Transform,JointModelRenderer,IAnimatedEntity}.java and
 * client/model/ScalableModelRenderer.java, on the engine's ModelBase / ModelRenderer objects.
 * ModelRenderer and ModelBase get readable aliases (rotateAngleX, addBox, render, ...), so the translated models keep
 * the Java statements as written. The overrides of JointModelRenderer (setTextureOffset, setTextureSize, addChild,
 * addBox delegate to the inner renderer) are honoured by those aliases; ScalableModelRenderer.render is honoured by
 * the E7Q hook (ModelRenderer.render renders children with direct calls, so an override on the object would be
 * skipped for nested parts). */
(function (M) {
  "use strict";

  // ---------------------------------------------------------------- engine model classes and aliases
  M.MODEL_CLASSES = { DQ: DQ, M2: M2, OB: OB, DR9: DR9, CJR: CJR };
  function bool01(v) { return v ? 1 : 0; }
  function flagAlias(min) {
    var g = function () { return this[min]; }; g.$jmAlias = true;
    return { get: g, set: function (v) { this[min] = bool01(v); }, enumerable: false, configurable: true };
  }
  M.MODEL_FIELDS = {
    M2: { textureWidth: "bdO", textureHeight: "bby", textureOffsetX: "bH9", textureOffsetY: "bH$", rotationPointX: "cD",
      rotationPointY: "bs", rotationPointZ: "bA", rotateAngleX: "A", rotateAngleY: "bb", rotateAngleZ: "bX",
      cubeList: "a6Y", childModels: "OS", offsetX: "bot", offsetY: "bcS", offsetZ: "bcR", boxName: "dI3" },
    DQ: { swingProgress: "v5", boxList: "cJ9", textureWidth: "vI", textureHeight: "vd" },
    // ModelCreeper (G8M) and ModelBiped (AB4) public parts used by CreeperMinionModel / MutantSkeletonArmorModel
    DR9: { head: "cfP", creeperArmor: "dWX", body: "cSR", leg1: "cgs", leg2: "cgr", leg3: "cgu", leg4: "cgt" },
    OB: { bipedHead: "lA", bipedHeadwear: "Ea", bipedBody: "k_", bipedRightArm: "gM", bipedLeftArm: "f3", bipedRightLeg: "mD",
      bipedLeftLeg: "nc", leftArmPose: "a2N", rightArmPose: "a6t" }
  };
  // ModelRenderer(model) / ModelRenderer(model, u, v) / JointModelRenderer / ScalableModelRenderer
  M.MR = function (model, u, v) { return arguments.length >= 3 ? BX(model, u, v) : H7(model); };
  M.JMR = function (model, x, y) {                            // JointModelRenderer(ModelBase, int, int)
    var joint = H7(model);                                   // super(model): registered in model.boxList first
    var inner = BX(model, x, y);                             // this.model = new ModelRenderer(model, x, y)
    HV(joint, inner);                                        // super.addChild(this.model)
    joint.$jmJoint = inner;
    return joint;
  };
  M.SMR = function (model, u, v) { var r = BX(model, u, v); r.$jmS = 1.0; return r; };
  function target(r) { return r.$jmJoint !== undefined ? r.$jmJoint : r; }
  M.MODEL_METHODS = {
    M2: {
      // addBox(x, y, z, w, h, d) returns this; addBox(..., float scaleFactor) is void; addBox(..., boolean mirrored)
      addBox: function (r, x, y, z, w, h, d, extra) {
        var t = target(r);
        if (extra === undefined) { CH(t, x, y, z, w, h, d); return t; }
        if (typeof extra === "boolean") { Y(t.a6Y, Hrq(t, t.bH9, t.bH$, x, y, z, w, h, d, 0.0, extra ? 1 : 0)); return t; }
        B$(t, x, y, z, w, h, d, extra);
        return undefined;
      },
      setRotationPoint: BQ,
      addChild: function (r, child) { HV(target(r), child); },
      setTextureOffset: function (r, u, v) { if (r.$jmJoint !== undefined) { DW(r.$jmJoint, u, v); return r; } return DW(r, u, v); },
      setTextureSize: function (r, w, h) { if (r.$jmJoint !== undefined) { FR(r.$jmJoint, w, h); return r; } return FR(r, w, h); },
      render: E7Q, renderWithRotation: Eu3, postRender: FFZ,
      getModel: function (r) { return r.$jmJoint; },                 // JointModelRenderer.getModel
      setScale: function (r, s) { r.$jmS = s; }                       // ScalableModelRenderer.setScale
    },
    DQ: {
      render: "ha", setRotationAngles: "i3", setLivingAnimations: "Lo",
      setModelAttributes: function (m, other) { if (typeof m.bpZ === "function") m.bpZ(other); else AAt(m, other); }
    }
  };
  // ModelBase virtuals a translated model may override (ModelBiped.postRenderArm is not overridden by any mod model)
  M.registerVirtuals("model", { render: "ha", setRotationAngles: "i3", setLivingAnimations: "Lo" });
  M.installModelAliases = function () {
    var p = M2.prototype;
    ["mirror", "showModel", "isHidden"].forEach(function (k, i) { if (!Object.getOwnPropertyDescriptor(p, k)) Object.defineProperty(p, k, flagAlias(["i$", "eT", "cIT"][i])); });
    var pq = DQ.prototype;
    if (!Object.getOwnPropertyDescriptor(pq, "isChild")) Object.defineProperty(pq, "isChild", flagAlias("ww"));
    if (!Object.getOwnPropertyDescriptor(pq, "isRiding")) Object.defineProperty(pq, "isRiding", flagAlias("b$r"));
    M.installAliases(M.MODEL_FIELDS, M.MODEL_METHODS, M.MODEL_CLASSES);
  };
  // E7Q hook: ScalableModelRenderer.render(scale) = push, scale(s), super.render(scale), pop
  M.scaledRender = function (r, scale) {
    if (!r.cIT && r.eT) {
      var s = r.$jmS;
      Eu0(); FWM(s, s, s);
      r.$jmS = undefined;
      try { E7Q(r, scale); } finally { r.$jmS = s; ECi(); }
    }
  };

  // ---------------------------------------------------------------- ModelBase subclasses
  // spec: { name, extend (DQ by default), init(model, args) = engine constructor of the parent, fields(self), ctor = Java body,
  //   methods, nonVirtual = readable names that are Java overloads (not overrides) of a ModelBase virtual }
  M.defineModel = function (spec) {
    var P = spec.extend || DQ;
    var C = M.defineClass({ name: spec.name, extend: P, fields: spec.fields, methods: spec.methods, group: "model", nonVirtual: spec.nonVirtual });
    C.create = function () {
      var m = new C();
      if (spec.init) spec.init(m, arguments); else Gs(m);
      if (spec.ctor) spec.ctor.apply(m, arguments);
      return m;
    };
    return C;
  };

  // ---------------------------------------------------------------- Transform.java
  function Transform() { this.rotationX = 0.0; this.rotationY = 0.0; this.rotationZ = 0.0; this.offsetX = 0.0; this.offsetY = 0.0; this.offsetZ = 0.0; }
  Transform.prototype.getRotationX = function () { return this.rotationX; };
  Transform.prototype.getRotationY = function () { return this.rotationY; };
  Transform.prototype.getRotationZ = function () { return this.rotationZ; };
  Transform.prototype.getOffsetX = function () { return this.offsetX; };
  Transform.prototype.getOffsetY = function () { return this.offsetY; };
  Transform.prototype.getOffsetZ = function () { return this.offsetZ; };
  Transform.prototype.addRotation = function (x, y, z) { this.rotationX += x; this.rotationY += y; this.rotationZ += z; };
  Transform.prototype.addOffset = function (x, y, z) { this.offsetX += x; this.offsetY += y; this.offsetZ += z; };
  M.Transform = Transform;

  // ---------------------------------------------------------------- Animator.java (HashMap<ModelRenderer, Transform> -> Map)
  function Animator(model) {
    this.tempTick = 0; this.prevTempTick = 0; this.correctAnim = false; this.mainModel = model; this.animEntity = null;
    this.transformMap = new Map(); this.prevTransformMap = new Map(); this.partialTick = 0.0;
  }
  Animator.prototype.getEntity = function () { return this.animEntity; };
  Animator.prototype.update = function (entity, partialTick) {
    this.prevTempTick = 0; this.tempTick = 0; this.correctAnim = false; this.animEntity = entity;
    this.transformMap.clear(); this.prevTransformMap.clear(); this.partialTick = partialTick;
    var boxes = M.listToArray(this.mainModel.boxList);
    for (var i = 0; i < boxes.length; i++) { var box = boxes[i]; box.rotateAngleX = 0.0; box.rotateAngleY = 0.0; box.rotateAngleZ = 0.0; }
  };
  Animator.prototype.setAnimation = function (animID) {
    this.prevTempTick = 0; this.tempTick = 0;
    this.correctAnim = this.animEntity.getAnimationID() === animID;
    return this.correctAnim;
  };
  Animator.prototype.startPhase = function (duration) { if (this.correctAnim) { this.prevTempTick = this.tempTick; this.tempTick += duration; } };
  Animator.prototype.setStationaryPhase = function (duration) { this.startPhase(duration); this.endPhase_(true); };
  Animator.prototype.resetPhase = function (duration) { this.startPhase(duration); this.endPhase(); };
  Animator.prototype.rotate = function (box, x, y, z) { if (this.correctAnim) this.getTransform(box).addRotation(x, y, z); };
  Animator.prototype.move = function (box, x, y, z) { if (this.correctAnim) this.getTransform(box).addOffset(x, y, z); };
  Animator.prototype.getTransform = function (box) {
    var t = this.transformMap.get(box);
    if (t === undefined) { t = new Transform(); this.transformMap.set(box, t); }
    return t;
  };
  Animator.prototype.endPhase = function () { this.endPhase_(false); };
  Animator.prototype.endPhase_ = function (stationary) {
    if (this.correctAnim) {
      var animTick = this.animEntity.getAnimationTick();
      if (animTick >= this.prevTempTick && animTick < this.tempTick) {
        if (stationary) {
          this.prevTransformMap.forEach(function (transform, model) {
            model.rotateAngleX += transform.getRotationX(); model.rotateAngleY += transform.getRotationY(); model.rotateAngleZ += transform.getRotationZ();
            model.rotationPointX += transform.getOffsetX(); model.rotationPointY += transform.getOffsetY(); model.rotationPointZ += transform.getOffsetZ();
          });
        } else {
          var tick = ((animTick - this.prevTempTick) + this.partialTick) / (this.tempTick - this.prevTempTick);
          var inc = M.MH.sin(tick * M.PI / 2.0), dec = 1.0 - inc;
          this.prevTransformMap.forEach(function (transform, model) {
            model.rotateAngleX += dec * transform.getRotationX(); model.rotateAngleY += dec * transform.getRotationY(); model.rotateAngleZ += dec * transform.getRotationZ();
            model.rotationPointX += dec * transform.getOffsetX(); model.rotationPointY += dec * transform.getOffsetY(); model.rotationPointZ += dec * transform.getOffsetZ();
          });
          this.transformMap.forEach(function (transform, model) {
            model.rotateAngleX += inc * transform.getRotationX(); model.rotateAngleY += inc * transform.getRotationY(); model.rotateAngleZ += inc * transform.getRotationZ();
            model.rotationPointX += inc * transform.getOffsetX(); model.rotationPointY += inc * transform.getOffsetY(); model.rotationPointZ += inc * transform.getOffsetZ();
          });
        }
      }
      if (!stationary) {
        this.prevTransformMap.clear();
        var prev = this.prevTransformMap;
        this.transformMap.forEach(function (v, k) { prev.set(k, v); });
        this.transformMap.clear();
      }
    }
  };
  M.Animator = Animator;
  M.PI = Math.fround(Math.PI);                                // (float) Math.PI
})(JasprMutants);
