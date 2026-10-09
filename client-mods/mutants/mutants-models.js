/* Every model of client/model/*.java, translated statement by statement on the engine's ModelBase/ModelRenderer.
 * Same boxes, rotation points, texture offsets, mirror flags and animation curves as the Java; JointModelRenderer
 * and ScalableModelRenderer per mutants-animation.js. Float literals keep their decimal value (the engine evaluates
 * Java float arithmetic in doubles too). One model object per kind, shared by all entities of that kind. */
(function (M) {
  "use strict";
  var MH = M.MH, MR = M.MR, JMR = M.JMR, SMR = M.SMR, T = M.T, PI;
  M.MODELS = {};
  M.defineModels = function () {
    var MODELS = M.MODELS;
    var sCreeperModel = M.superOf(DR9, "model"), sBipedModel = M.superOf(OB, "model");
    PI = M.PI;
  // ScalableModelRenderer.java: M.SMR + M.scaledRender in mutants-animation.js

    // ============================================================ CrossbowModel.java
    var CrossbowModel = MODELS.CrossbowModel = function (model) {   // CrossbowModel(ModelBase model)
          this.armwear = MR(model, 0, 64);
          this.armwear.addBox(-2.0, -3.0, -2.0, 4, 6, 4, 0.3);
          this.middle = MR(model, 16, 64);
          this.middle.addBox(-2.0, -2.0, -3.0, 4, 4, 6);
          this.middle.setRotationPoint(-3.5, 0.0, 0.0);
          this.armwear.addChild(this.middle);
          this.middle1 = MR(model, 36, 64);
          this.middle1.addBox(-1.5, -1.5, -3.0, 3, 3, 6);
          this.middle1.setRotationPoint(0.0, 0.6, -4.0);
          this.middle.addChild(this.middle1);
          this.middle2 = MR(model, 36, 64);
          this.middle2.addBox(-1.5, -1.5, -3.0, 3, 3, 6);
          this.middle2.setRotationPoint(0.0, 0.6, 4.0);
          this.middle.addChild(this.middle2);
          this.side1 = MR(model, 0, 74);
          this.side1.addBox(-1.0, -1.0, -8.0, 2, 2, 8);
          this.side1.setRotationPoint(0.0, 0.0, -2.0);
          this.middle1.addChild(this.side1);
          this.side2 = MR(model, 0, 74);
          this.side2.addBox(-1.0, -1.0, 0.0, 2, 2, 8);
          this.side2.setRotationPoint(0.0, 0.0, 2.0);
          this.middle2.addChild(this.side2);
          this.side3 = MR(model, 20, 74);
          this.side3.addBox(-0.5, -0.5, -8.0, 1, 1, 8);
          this.side3.setRotationPoint(0.0, 0.0, -5.0);
          this.side1.addChild(this.side3);
          this.side4 = MR(model, 20, 74);
          this.side4.addBox(-0.5, -0.5, 0.0, 1, 1, 8);
          this.side4.setRotationPoint(0.0, 0.0, 5.0);
          this.side2.addChild(this.side4);
          this.rope1 = MR(model, 0, 84);
          this.rope1.addBox(-0.5, -0.5, 0.0, 1, 1, 15, -0.4);
          this.rope1.setRotationPoint(0.0, 0.0, -6.0);
          this.side3.addChild(this.rope1);
          this.rope2 = MR(model, 0, 84);
          this.rope2.addBox(-0.5, -0.5, -15.0, 1, 1, 15, -0.4);
          this.rope2.setRotationPoint(0.0, 0.0, 6.0);
          this.side4.addChild(this.rope2);
    };
    CrossbowModel.prototype = {
        setAngles: function (PI) {
          this.middle1.rotateAngleX = PI / 8.0;
          this.middle2.rotateAngleX = -PI / 8.0;
          this.side1.rotateAngleX = -PI / 5.0;
          this.side2.rotateAngleX = PI / 5.0;
          this.side3.rotateAngleX = -PI / 4.0;
          this.side4.rotateAngleX = PI / 4.0;
        },
        rotateRope: function () {
          this.rope1.rotateAngleX = -(this.middle1.rotateAngleX + this.side1.rotateAngleX + this.side3.rotateAngleX);
          this.rope2.rotateAngleX = -(this.middle2.rotateAngleX + this.side2.rotateAngleX + this.side4.rotateAngleX);
        }
    };

    // ============================================================ MutantZombieModel.java
    MODELS.MutantZombieModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantZombieModel",
      fields: function (s) { s.partialTick = 0.0; },
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 128;
          this.pelvis = MR(this);
          this.pelvis.setRotationPoint(0.0, 10.0, 6.0);
          this.waist = MR(this, 0, 44);
          this.waist.addBox(-7.0, -16.0, -6.0, 14, 16, 12);
          this.pelvis.addChild(this.waist);
          this.chest = MR(this, 0, 16);
          this.chest.addBox(-12.0, -12.0, -8.0, 24, 12, 16);
          this.chest.setRotationPoint(0.0, -12.0, 0.0);
          this.waist.addChild(this.chest);
          this.head = MR(this, 0, 0);
          this.head.addBox(-4.0, -8.0, -4.0, 8, 8, 8);
          this.head.setRotationPoint(0.0, -11.0, -4.0);
          this.chest.addChild(this.head);
          this.arm1 = MR(this, 104, 0);
          this.arm1.addBox(-3.0, 0.0, -3.0, 6, 16, 6);
          this.arm1.setRotationPoint(-11.0, -8.0, 2.0);
          this.chest.addChild(this.arm1);
          this.arm2 = MR(this, 104, 0);
          this.arm2.mirror = 1;
          this.arm2.addBox(-3.0, 0.0, -3.0, 6, 16, 6);
          this.arm2.setRotationPoint(11.0, -8.0, 2.0);
          this.chest.addChild(this.arm2);
          this.forearm1 = MR(this, 104, 22);
          this.forearm1.addBox(-3.0, 0.0, -3.0, 6, 16, 6, 0.1);
          this.forearm1.setRotationPoint(0.0, 14.0, 0.0);
          this.arm1.addChild(this.forearm1);
          this.forearm2 = MR(this, 104, 22);
          this.forearm2.mirror = 1;
          this.forearm2.addBox(-3.0, 0.0, -3.0, 6, 16, 6, 0.1);
          this.forearm2.setRotationPoint(0.0, 14.0, 0.0);
          this.arm2.addChild(this.forearm2);
          this.leg1 = MR(this, 80, 0);
          this.leg1.addBox(-3.0, 0.0, -3.0, 6, 11, 6);
          this.leg1.setRotationPoint(-5.0, -2.0, 0.0);
          this.pelvis.addChild(this.leg1);
          this.leg2 = MR(this, 80, 0);
          this.leg2.mirror = 1;
          this.leg2.addBox(-3.0, 0.0, -3.0, 6, 11, 6);
          this.leg2.setRotationPoint(5.0, -2.0, 0.0);
          this.pelvis.addChild(this.leg2);
          this.foreleg1 = MR(this, 80, 17);
          this.foreleg1.addBox(-3.0, 0.0, -3.0, 6, 8, 6, 0.1);
          this.foreleg1.setRotationPoint(0.0, 9.5, 0.0);
          this.leg1.addChild(this.foreleg1);
          this.foreleg2 = MR(this, 80, 17);
          this.foreleg2.mirror = 1;
          this.foreleg2.addBox(-3.0, 0.0, -3.0, 6, 8, 6, 0.1);
          this.foreleg2.setRotationPoint(0.0, 9.5, 0.0);
          this.leg2.addChild(this.foreleg2);
      },
      methods: {
        render: function (entity, f, f1, f2, f3, f4, f5) {
          this.setAngles();
          this.animate(entity, f, f1, f2, f3, f4, f5);
          this.pelvis.render(f5);
        },
        setAngles: function () {
          this.pelvis.rotationPointY = 10.0;
          this.waist.rotateAngleX = 0.19634955;
          this.chest.rotateAngleX = 0.5235988;
          this.chest.rotateAngleY = 0.0;
          this.head.rotateAngleX = -0.71994835;
          this.head.rotateAngleY = 0.0;
          this.head.rotateAngleZ = 0.0;
          this.arm1.rotateAngleX = -0.32724923;
          this.arm1.rotateAngleY = 0.0;
          this.arm1.rotateAngleZ = 0.3926991;
          this.arm2.rotateAngleX = -0.32724923;
          this.arm2.rotateAngleY = 0.0;
          this.arm2.rotateAngleZ = -0.3926991;
          this.forearm1.rotateAngleX = -1.0471976;
          this.forearm2.rotateAngleX = -1.0471976;
          this.leg1.rotateAngleX = -0.7853982;
          this.leg1.rotateAngleY = 0.0;
          this.leg1.rotateAngleZ = 0.0;
          this.leg2.rotateAngleX = -0.7853982;
          this.leg2.rotateAngleY = 0.0;
          this.leg2.rotateAngleZ = 0.0;
          this.foreleg1.rotateAngleX = 0.7853982;
          this.foreleg2.rotateAngleX = 0.7853982;
        },
        animate: function (zombie, f, f1, f2, f3, f4, f5) {
          var walkAnim1= (MH.sin(((f - 0.7) * 0.4)) + 0.7) * f1;
          var walkAnim2= -(MH.sin(((f + 0.7) * 0.4)) - 0.7) * f1;
          var walkAnim= MH.sin((f * 0.4)) * f1;
          var breatheAnim= MH.sin((f2 * 0.1));
          var faceYaw= f3 * PI / 180.0;
          var facePitch= f4 * PI / 180.0;
          if (zombie.deathTime <= 0) {
              var scale;
              if (zombie.getAttackID() === 1) {
                  this.animateMelee(zombie.getAttackTick());
              }
              if (zombie.getAttackID() === 3) {
                  this.animateRoar(zombie.getAttackTick());
                  scale = 1.0 - MH.clampF((zombie.getAttackTick() / 6.0), 0.0, 1.0);
                  walkAnim1 *= scale;
                  walkAnim2 *= scale;
                  walkAnim *= scale;
                  facePitch *= scale;
              }
              if (zombie.getAttackID() === 2) {
                  this.animateThrow(zombie);
                  scale = 1.0 - MH.clampF((zombie.getAttackTick() / 3.0), 0.0, 1.0);
                  walkAnim1 *= scale;
                  walkAnim2 *= scale;
                  walkAnim *= scale;
                  facePitch *= scale;
              }
          } else {
              this.animateDeath(zombie);
              var scale= 1.0 - MH.clampF((zombie.deathTime / 6.0), 0.0, 1.0);
              walkAnim1 *= scale;
              walkAnim2 *= scale;
              walkAnim *= scale;
              breatheAnim *= scale;
              faceYaw *= scale;
              facePitch *= scale;
          }
          this.chest.rotateAngleX += breatheAnim * 0.02;
          this.arm1.rotateAngleZ -= breatheAnim * 0.05;
          this.arm2.rotateAngleZ += breatheAnim * 0.05;
          this.head.rotateAngleX += facePitch * 0.6;
          this.head.rotateAngleY += faceYaw * 0.8;
          this.head.rotateAngleZ -= faceYaw * 0.2;
          this.chest.rotateAngleX += facePitch * 0.4;
          this.chest.rotateAngleY += faceYaw * 0.2;
          this.pelvis.rotationPointY += MH.sin((f * 0.8)) * f1 * 0.5;
          this.chest.rotateAngleY -= walkAnim * 0.1;
          this.arm1.rotateAngleX -= walkAnim * 0.6;
          this.arm2.rotateAngleX += walkAnim * 0.6;
          this.leg1.rotateAngleX += walkAnim1 * 0.9;
          this.leg2.rotateAngleX += walkAnim2 * 0.9;
        },
        animateMelee: function (fullTick) {
          this.arm1.rotateAngleZ = 0.0;
          this.arm2.rotateAngleZ = 0.0;
          if (fullTick < 8) {
              var tick= (fullTick + this.partialTick) / 8.0;
              var f= -MH.sin((tick * PI / 2.0));
              var f1= MH.cos((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.2;
              this.chest.rotateAngleX += f * 0.2;
              this.arm1.rotateAngleX += f * 2.3;
              this.arm1.rotateAngleZ += f1 * PI / 8.0;
              this.arm2.rotateAngleX += f * 2.3;
              this.arm2.rotateAngleZ -= f1 * PI / 8.0;
              this.forearm1.rotateAngleX += f * 0.8;
              this.forearm2.rotateAngleX += f * 0.8;
          } else if (fullTick < 12) {
              var tick= ((fullTick - 8) + this.partialTick) / 4.0;
              var f= -MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.9 + 0.7;
              this.chest.rotateAngleX += f * 0.9 + 0.7;
              this.arm1.rotateAngleX += f * 0.2 - 2.1;
              this.arm1.rotateAngleZ += f1 * 0.3;
              this.arm2.rotateAngleX += f * 0.2 - 2.1;
              this.arm2.rotateAngleZ -= f1 * 0.3;
              this.forearm1.rotateAngleX += f * 1.0 + 0.2;
              this.forearm2.rotateAngleX += f * 1.0 + 0.2;
          } else if (fullTick < 16) {
              this.waist.rotateAngleX += 0.7;
              this.chest.rotateAngleX += 0.7;
              this.arm1.rotateAngleX -= 2.1;
              this.arm1.rotateAngleZ += 0.3;
              this.arm2.rotateAngleX -= 2.1;
              this.arm2.rotateAngleZ -= 0.3;
              this.forearm1.rotateAngleX += 0.2;
              this.forearm2.rotateAngleX += 0.2;
          } else if (fullTick < 24) {
              var tick= ((fullTick - 16) + this.partialTick) / 8.0;
              var f= MH.cos((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.7;
              this.chest.rotateAngleX += f * 0.7;
              this.arm1.rotateAngleX -= f * 2.1;
              this.arm1.rotateAngleZ += f * -0.09269908 + 0.3926991;
              this.arm2.rotateAngleX -= f * 2.1;
              this.arm2.rotateAngleZ -= f * -0.09269908 + 0.3926991;
              this.forearm1.rotateAngleX += f * 0.2;
              this.forearm2.rotateAngleX += f * 0.2;
          } else {
              this.arm1.rotateAngleZ += 0.3926991;
              this.arm2.rotateAngleZ += -0.3926991;
          }
        },
        animateRoar: function (fullTick) {
          var f1;
          var f;
          var tick;
          if (fullTick < 10) {
              tick = (fullTick + this.partialTick) / 10.0;
              f = MH.sin((tick * PI / 2.0));
              f1 = MH.sin((tick * PI * PI / 8.0));
              this.waist.rotateAngleX += f * 0.2;
              this.chest.rotateAngleX += f * 0.4;
              this.chest.rotateAngleY += f1 * 0.06;
              this.head.rotateAngleX += f * 0.8;
              this.arm1.rotateAngleX -= f * 1.2;
              this.arm1.rotateAngleZ += f * 0.6;
              this.arm2.rotateAngleX -= f * 1.2;
              this.arm2.rotateAngleZ -= f * 0.6;
              this.forearm1.rotateAngleX -= f * 0.8;
              this.forearm2.rotateAngleX -= f * 0.8;
          } else if (fullTick < 15) {
              tick = ((fullTick - 10) + this.partialTick) / 5.0;
              f = MH.cos((tick * PI / 2.0));
              f1 = MH.sin((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.39634955 - 0.19634955;
              this.chest.rotateAngleX += f * 0.6 - 0.2;
              this.head.rotateAngleX += f * 1.0 - 0.2;
              this.arm1.rotateAngleX -= f * 2.2 - 1.0;
              this.arm1.rotateAngleY += f1 * 0.4;
              this.arm1.rotateAngleZ += 0.6;
              this.arm2.rotateAngleX -= f * 2.2 - 1.0;
              this.arm2.rotateAngleY -= f1 * 0.4;
              this.arm2.rotateAngleZ -= 0.6;
              this.forearm1.rotateAngleX -= f * 1.0 - 0.2;
              this.forearm2.rotateAngleX -= f * 1.0 - 0.2;
              this.leg1.rotateAngleY += f1 * 0.3;
              this.leg2.rotateAngleY -= f1 * 0.3;
          } else if (fullTick < 75) {
              this.waist.rotateAngleX -= 0.19634955;
              this.chest.rotateAngleX -= 0.2;
              this.head.rotateAngleX -= 0.2;
              this.addRotation(this.arm1, 1.0, 0.4, 0.6);
              this.addRotation(this.arm2, 1.0, -0.4, -0.6);
              this.forearm1.rotateAngleX += 0.2;
              this.forearm2.rotateAngleX += 0.2;
              this.leg1.rotateAngleY += 0.3;
              this.leg2.rotateAngleY -= 0.3;
          } else if (fullTick < 90) {
              tick = ((fullTick - 75) + this.partialTick) / 15.0;
              f = MH.cos((tick * PI / 2.0));
              this.waist.rotateAngleX -= f * 0.69634956 - 0.5;
              this.chest.rotateAngleX -= f * 0.7 - 0.5;
              this.head.rotateAngleX -= f * 0.6 - 0.4;
              this.addRotation(this.arm1, f * 2.6 - 1.6, f * 0.4, f * 0.99269915 - 0.3926991);
              this.addRotation(this.arm2, f * 2.6 - 1.6, -f * 0.4, -f * 0.99269915 + 0.3926991);
              this.forearm1.rotateAngleX += f * -0.6 + 0.8;
              this.forearm2.rotateAngleX += f * -0.6 + 0.8;
              this.leg1.rotateAngleY += f * 0.3;
              this.leg2.rotateAngleY -= f * 0.3;
          } else if (fullTick < 110) {
              this.waist.rotateAngleX += 0.5;
              this.chest.rotateAngleX += 0.5;
              this.head.rotateAngleX += 0.4;
              this.addRotation(this.arm1, -1.6, 0.0, -0.3926991);
              this.addRotation(this.arm2, -1.6, 0.0, 0.3926991);
              this.forearm1.rotateAngleX += 0.8;
              this.forearm2.rotateAngleX += 0.8;
          } else {
              tick = ((fullTick - 110) + this.partialTick) / 10.0;
              f = MH.cos((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.5;
              this.chest.rotateAngleX += f * 0.5;
              this.head.rotateAngleX += f * 0.4;
              this.addRotation(this.arm1, f * -1.6, 0.0, f * (-PI) / 8.0);
              this.addRotation(this.arm2, f * -1.6, 0.0, f * PI / 8.0);
              this.forearm1.rotateAngleX += f * 0.8;
              this.forearm2.rotateAngleX += f * 0.8;
          }
          if (fullTick >= 10 && fullTick < 75) {
              tick = ((fullTick - 10) + this.partialTick) / 65.0;
              f = MH.sin((tick * PI * 8.0));
              f1 = MH.sin((tick * PI * 8.0 + 0.7853982));
              this.head.rotateAngleY += f * 0.5 - f1 * 0.2;
              this.head.rotateAngleZ -= f * 0.5;
              this.chest.rotateAngleY += f1 * 0.06;
          }
        },
        animateThrow: function (zombie) {
          if (zombie.getAttackTick() < 3) {
              var tick= (zombie.getAttackTick() + this.partialTick) / 3.0;
              var f= MH.sin((tick * PI / 2.0));
              this.chest.rotateAngleX -= f * 0.4;
              this.arm1.rotateAngleX -= f * 1.8;
              this.arm1.rotateAngleZ -= f * PI / 8.0;
              this.arm2.rotateAngleX -= f * 1.8;
              this.arm2.rotateAngleZ += f * PI / 8.0;
          } else if (zombie.getAttackTick() < 5) {
              this.chest.rotateAngleX -= 0.4;
              this.arm1.rotateAngleX -= 1.0;
              this.arm1.rotateAngleZ = 0.0;
              this.arm2.rotateAngleX -= 1.0;
              this.arm2.rotateAngleZ = 0.0;
          } else if (zombie.getAttackTick() < 8) {
              var tick= ((zombie.getAttackTick() - 5) + this.partialTick) / 3.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.waist.rotateAngleX += f1 * 0.2;
              this.chest.rotateAngleX -= f * 0.6 - 0.2;
              this.arm1.rotateAngleX -= f * 2.2 - 0.4;
              this.arm1.rotateAngleZ -= f * PI / 8.0;
              this.arm2.rotateAngleX -= f * 2.2 - 0.4;
              this.arm2.rotateAngleZ += f * PI / 8.0;
              this.forearm1.rotateAngleX -= f1 * 0.4;
              this.forearm2.rotateAngleX -= f1 * 0.4;
          } else if (zombie.getAttackTick() < 10) {
              this.waist.rotateAngleX += 0.2;
              this.chest.rotateAngleX += 0.2;
              this.arm1.rotateAngleX += 0.4;
              this.arm2.rotateAngleX += 0.4;
              this.forearm1.rotateAngleX -= 0.4;
              this.forearm2.rotateAngleX -= 0.4;
          } else if (zombie.getAttackTick() < 15) {
              var tick= ((zombie.getAttackTick() - 10) + this.partialTick) / 5.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.39634955 - 0.19634955;
              this.chest.rotateAngleX += f * 0.8 - 0.6;
              this.arm1.rotateAngleX += f * 3.0 - 2.6;
              this.arm2.rotateAngleX += f * 3.0 - 2.6;
              this.forearm1.rotateAngleX -= f * 0.4;
              this.forearm2.rotateAngleX -= f * 0.4;
              this.leg1.rotateAngleX += f1 * 0.6;
              this.leg2.rotateAngleX += f1 * 0.6;
          } else if (zombie.throwHitTick === -1) {
              this.waist.rotateAngleX -= 0.19634955;
              this.chest.rotateAngleX -= 0.6;
              this.arm1.rotateAngleX -= 2.6;
              this.arm2.rotateAngleX -= 2.6;
              this.leg1.rotateAngleX += 0.6;
              this.leg2.rotateAngleX += 0.6;
          } else if (zombie.throwHitTick < 5) {
              var tick= (zombie.throwHitTick + this.partialTick) / 3.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.waist.rotateAngleX -= f * 0.39634955 - 0.2;
              this.chest.rotateAngleX -= f * 0.8 - 0.2;
              this.addRotation(this.arm1, -(f * 2.2 + 0.4), -f1 * PI / 8.0, f1 * 0.4);
              this.addRotation(this.arm2, -(f * 2.2 + 0.4), f1 * PI / 8.0, -f1 * 0.4);
              this.forearm1.rotateAngleX += f1 * 0.2;
              this.forearm2.rotateAngleX += f1 * 0.2;
              this.leg1.rotateAngleX += f * 0.8 - 0.2;
              this.leg2.rotateAngleX += f * 0.8 - 0.2;
          } else if (zombie.throwFinishTick === -1) {
              this.waist.rotateAngleX += 0.2;
              this.chest.rotateAngleX += 0.2;
              this.addRotation(this.arm1, -0.4, -0.3926991, 0.4);
              this.addRotation(this.arm2, -0.4, 0.3926991, -0.4);
              this.forearm1.rotateAngleX += 0.2;
              this.forearm2.rotateAngleX += 0.2;
              this.leg1.rotateAngleX -= 0.2;
              this.leg2.rotateAngleX -= 0.2;
          } else if (zombie.throwFinishTick < 10) {
              var tick= (zombie.throwFinishTick + this.partialTick) / 10.0;
              var f= MH.cos((tick * PI / 2.0));
              this.waist.rotateAngleX += f * 0.2;
              this.chest.rotateAngleX += f * 0.2;
              this.addRotation(this.arm1, -f * 0.4, -f * PI / 8.0, f * 0.4);
              this.addRotation(this.arm1, -f * 0.4, f * PI / 8.0, -f * 0.4);
              this.forearm1.rotateAngleX += f * 0.2;
              this.forearm2.rotateAngleX += f * 0.2;
              this.leg1.rotateAngleX -= f * 0.2;
              this.leg2.rotateAngleX -= f * 0.2;
          }
        },
        animateDeath: function (zombie) {
          if (zombie.deathTime <= 20) {
              var tick= (zombie.deathTime + this.partialTick - 1.0) / 20.0;
              var f= MH.sin((tick * PI / 2.0));
              this.pelvis.rotationPointY += f * 28.0;
              this.head.rotateAngleX -= f * PI / 10.0;
              this.head.rotateAngleY += f * PI / 5.0;
              this.chest.rotateAngleX -= f * PI / 12.0;
              this.waist.rotateAngleX -= f * PI / 10.0;
              this.arm1.rotateAngleX -= f * PI / 2.0;
              this.arm1.rotateAngleY += f * PI / 2.8;
              this.arm2.rotateAngleX -= f * PI / 2.0;
              this.arm2.rotateAngleY -= f * PI / 2.8;
              this.leg1.rotateAngleX += f * PI / 6.0;
              this.leg1.rotateAngleZ += f * PI / 12.0;
              this.leg2.rotateAngleX += f * PI / 6.0;
              this.leg2.rotateAngleZ -= f * PI / 12.0;
          } else if (zombie.deathTime <= 100) {
              this.pelvis.rotationPointY += 28.0;
              this.head.rotateAngleX -= 0.31415927;
              this.head.rotateAngleY += 0.62831855;
              this.chest.rotateAngleX -= 0.2617994;
              this.waist.rotateAngleX -= 0.31415927;
              this.arm1.rotateAngleX = (this.arm1.rotateAngleX - 1.57079635);
              this.arm1.rotateAngleY = (this.arm1.rotateAngleY + 1.12199739);
              this.arm2.rotateAngleX = (this.arm2.rotateAngleX - 1.57079635);
              this.arm2.rotateAngleY = (this.arm2.rotateAngleY - 1.12199739);
              this.leg1.rotateAngleX += 0.5235988;
              this.leg1.rotateAngleZ += 0.2617994;
              this.leg2.rotateAngleX += 0.5235988;
              this.leg2.rotateAngleZ -= 0.2617994;
          } else {
              var tick= ((40 - (140 - zombie.deathTime)) + this.partialTick) / 40.0;
              var f= MH.cos((tick * PI / 2.0));
              this.pelvis.rotationPointY += f * 28.0;
              this.head.rotateAngleX -= f * PI / 10.0;
              this.head.rotateAngleY += f * PI / 5.0;
              this.chest.rotateAngleX -= f * PI / 12.0;
              this.waist.rotateAngleX -= f * PI / 10.0;
              this.arm1.rotateAngleX -= f * PI / 2.0;
              this.arm1.rotateAngleY += f * PI / 2.8;
              this.arm2.rotateAngleX -= f * PI / 2.0;
              this.arm2.rotateAngleY -= f * PI / 2.8;
              this.leg1.rotateAngleX += f * PI / 6.0;
              this.leg1.rotateAngleZ += f * PI / 12.0;
              this.leg2.rotateAngleX += f * PI / 6.0;
              this.leg2.rotateAngleZ -= f * PI / 12.0;
          }
        },
        addRotation: function (model, x, y, z) {
          model.rotateAngleX += x;
          model.rotateAngleY += y;
          model.rotateAngleZ += z;
        },
        getPartialTick: function () {
          return this.partialTick;
        },
        setLivingAnimations: function (entityIn, limbSwing, limbSwingAmount, partialTick) {
          this.partialTick = partialTick;
        },
      }
    });

    // ============================================================ MutantSkeletonModel.java
    MODELS.MutantSkeletonModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantSkeletonModel",
      fields: function (s) { s.partialTick = 0.0; },
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 128;
          this.skeleBase = MR(this);
          this.skeleBase.setRotationPoint(0.0, 3.0, 0.0);
          this.pelvis = MR(this, 0, 16);
          this.pelvis.addBox(-4.0, -6.0, -3.0, 8, 6, 6);
          this.skeleBase.addChild(this.pelvis);
          this.waist = MR(this, 32, 0);
          this.waist.addBox(-2.5, -8.0, -2.0, 5, 8, 4);
          this.waist.setRotationPoint(0.0, -5.0, 0.0);
          this.pelvis.addChild(this.waist);
          this.spine = new Array(3);
          this.spine[0] = new Spine(this, false);
          this.spine[0].middle.setRotationPoint(0.0, -7.0, 0.0);
          this.waist.addChild(this.spine[0].middle);
          for (var i= 1; i < this.spine.length; ++i) {
              this.spine[i] = new Spine(this, false);
              this.spine[i].middle.setRotationPoint(0.0, -5.0, 0.0);
              this.spine[i - 1].middle.addChild(this.spine[i].middle);
          }
          this.neck = MR(this, 64, 0);
          this.neck.addBox(-1.5, -4.0, -1.5, 3, 4, 3);
          this.neck.setRotationPoint(0.0, -4.0, 0.0);
          this.spine[2].middle.addChild(this.neck);
          this.head = JMR(this, 0, 0);
          this.head.addBox(-4.0, -8.0, -4.0, 8, 8, 8, 0.4);
          this.head.setRotationPoint(0.0, -4.0, -1.0);
          this.neck.addChild(this.head);
          this.jaw = MR(this, 72, 0);
          this.jaw.addBox(-4.0, -3.0, -8.0, 8, 3, 8, 0.7);
          this.jaw.setRotationPoint(0.0, -0.2, 3.5);
          this.head.addChild(this.jaw);
          this.shoulder1 = MR(this, 28, 16);
          this.shoulder1.addBox(-4.0, -3.0, -3.0, 8, 3, 6);
          this.shoulder1.setRotationPoint(-7.0, -3.0, -1.0);
          this.spine[2].middle.addChild(this.shoulder1);
          this.shoulder2 = MR(this, 28, 16);
          this.shoulder2.mirror = 1;
          this.shoulder2.addBox(-4.0, -3.0, -3.0, 8, 3, 6);
          this.shoulder2.setRotationPoint(7.0, -3.0, -1.0);
          this.spine[2].middle.addChild(this.shoulder2);
          this.arm1 = JMR(this, 0, 28);
          this.arm1.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.arm1.setRotationPoint(-1.0, -1.0, 0.0);
          this.shoulder1.addChild(this.arm1);
          this.arm2 = JMR(this, 0, 28);
          this.arm2.mirror = 1;
          this.arm2.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.arm2.setRotationPoint(1.0, -1.0, 0.0);
          this.shoulder2.addChild(this.arm2);
          this.forearm1 = JMR(this, 16, 28);
          this.forearm1.addBox(-2.0, 0.0, -2.0, 4, 14, 4, -0.01);
          this.forearm1.setRotationPoint(0.0, 11.0, 0.0);
          this.arm1.addChild(this.forearm1);
          this.forearm2 = JMR(this, 16, 28);
          this.forearm2.mirror = 1;
          this.forearm2.addBox(-2.0, 0.0, -2.0, 4, 14, 4, -0.01);
          this.forearm2.setRotationPoint(0.0, 11.0, 0.0);
          this.arm2.addChild(this.forearm2);
          this.leg1 = JMR(this, 0, 28);
          this.leg1.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.leg1.setRotationPoint(-2.5, -2.5, 0.0);
          this.pelvis.addChild(this.leg1);
          this.leg2 = JMR(this, 0, 28);
          this.leg2.mirror = 1;
          this.leg2.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.leg2.setRotationPoint(2.5, -2.5, 0.0);
          this.pelvis.addChild(this.leg2);
          this.foreleg1 = JMR(this, 32, 28);
          this.foreleg1.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.foreleg1.setRotationPoint(0.0, 12.0, 0.0);
          this.leg1.addChild(this.foreleg1);
          this.foreleg2 = JMR(this, 32, 28);
          this.foreleg2.mirror = 1;
          this.foreleg2.addBox(-2.0, 0.0, -2.0, 4, 12, 4);
          this.foreleg2.setRotationPoint(0.0, 12.0, 0.0);
          this.leg2.addChild(this.foreleg2);
          this.bow = new CrossbowModel(this);
          this.bow.armwear.setRotationPoint(0.0, 8.0, 0.0);
          this.forearm1.addChild(this.bow.armwear);
          this.animator = new M.Animator(this);
      },
      methods: {
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.animator.update(entityIn, this.partialTick);
          this.setAngles();
          this.animate(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
          this.skeleBase.render(scale);
        },
        setAngles: function () {
          this.skeleBase.rotationPointY = 3.0;
          this.pelvis.rotateAngleX = -0.31415927;
          this.waist.rotateAngleX = 0.22439948;
          for (var i= 0; i < this.spine.length; ++i) {
              this.spine[i].setAngles(PI, i === 1);
          }
          this.neck.rotateAngleX = -0.1308997;
          this.head.rotateAngleX = -0.1308997;
          this.jaw.rotateAngleX = 0.09817477;
          this.shoulder1.rotateAngleX = -0.7853982;
          this.shoulder2.rotateAngleX = -0.7853982;
          this.arm1.getModel().rotateAngleX = 0.5235988;
          this.arm1.getModel().rotateAngleZ = 0.31415927;
          this.arm2.getModel().rotateAngleX = 0.5235988;
          this.arm2.getModel().rotateAngleZ = -0.31415927;
          this.forearm1.getModel().rotateAngleX = -0.5235988;
          this.forearm2.getModel().rotateAngleX = -0.5235988;
          this.leg1.rotateAngleX = -0.2617994 - this.pelvis.rotateAngleX;
          this.leg1.rotateAngleZ = 0.19634955;
          this.leg2.rotateAngleX = -0.2617994 - this.pelvis.rotateAngleX;
          this.leg2.rotateAngleZ = -0.19634955;
          this.foreleg1.rotateAngleZ = -0.1308997;
          this.foreleg1.getModel().rotateAngleX = 0.31415927;
          this.foreleg2.rotateAngleZ = 0.1308997;
          this.foreleg2.getModel().rotateAngleX = 0.31415927;
          this.bow.setAngles(PI);
          this.bow.rotateRope();
        },
        animate: function (skele, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, f5) {
          var scale;
          var walkAnim1= MH.sin((limbSwing * 0.5));
          var walkAnim2= MH.sin((limbSwing * 0.5 - 1.1));
          var breatheAnim= MH.sin((ageInTicks * 0.1));
          var faceYaw= netHeadYaw * PI / 180.0;
          var facePitch= headPitch * PI / 180.0;
          if (skele.getAnimationID() === 1) {
              this.animateMelee(skele.getAnimationTick());
              this.bow.rotateRope();
              scale = 1.0 - MH.clampF((skele.getAnimationTick() / 4.0), 0.0, 1.0);
              walkAnim1 *= scale;
              walkAnim2 *= scale;
          } else if (skele.getAnimationID() === 2) {
              this.animateShoot(skele.getAnimationTick(), facePitch, faceYaw);
              scale = 1.0 - MH.clampF((skele.getAnimationTick() / 4.0), 0.0, 1.0);
              walkAnim1 *= scale;
              walkAnim2 *= scale;
              facePitch *= scale;
              faceYaw *= scale;
          } else if (skele.getAnimationID() === 3) {
              this.animateMultiShoot(skele.getAnimationTick(), facePitch, faceYaw);
              scale = 1.0 - MH.clampF((skele.getAnimationTick() / 4.0), 0.0, 1.0);
              walkAnim1 *= scale;
              walkAnim2 *= scale;
              facePitch *= scale;
              faceYaw *= scale;
          } else if (this.animator.setAnimation(4)) {
              this.animateConstrict();
              this.bow.rotateRope();
              scale = 1.0 - MH.clampF((skele.getAnimationTick() / 6.0), 0.0, 1.0);
              facePitch *= scale;
              faceYaw *= scale;
          } else {
              this.bow.rotateRope();
          }
          this.skeleBase.rotationPointY -= (-0.5 + Math.abs(walkAnim1)) * limbSwingAmount;
          this.spine[0].middle.rotateAngleY -= walkAnim1 * 0.06 * limbSwingAmount;
          this.arm1.rotateAngleX -= walkAnim1 * 0.9 * limbSwingAmount;
          this.arm2.rotateAngleX += walkAnim1 * 0.9 * limbSwingAmount;
          this.leg1.rotateAngleX += (0.2 + walkAnim1) * 1.0 * limbSwingAmount;
          this.leg2.rotateAngleX -= (-0.2 + walkAnim1) * 1.0 * limbSwingAmount;
          this.foreleg1.getModel().rotateAngleX += (0.6 + walkAnim2) * 0.6 * limbSwingAmount;
          this.foreleg2.getModel().rotateAngleX -= (-0.6 + walkAnim2) * 0.6 * limbSwingAmount;
          for (var i= 0; i < this.spine.length; ++i) {
              this.spine[i].animate(breatheAnim);
          }
          this.head.rotateAngleX -= breatheAnim * 0.02;
          this.jaw.rotateAngleX += breatheAnim * 0.04 + 0.04;
          this.arm1.rotateAngleZ += breatheAnim * 0.025;
          this.arm2.rotateAngleZ -= breatheAnim * 0.025;
          this.head.getModel().rotateAngleX += facePitch;
          this.head.getModel().rotateAngleY += faceYaw;
        },
        animateMelee: function (fullTick) {
          if (fullTick < 3) {
              var tick= (fullTick + this.partialTick) / 3.0;
              var f= MH.sin((tick * PI / 2.0));
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += f * PI / 16.0;
              }
              this.arm1.rotateAngleY += f * PI / 10.0;
              this.arm1.rotateAngleZ += f * PI / 4.0;
              this.arm2.rotateAngleZ += f * (-PI) / 16.0;
          } else if (fullTick < 5) {
              var tick= ((fullTick - 3) + this.partialTick) / 2.0;
              var f= MH.cos((tick * PI / 2.0));
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += f * 0.5890486 - 0.3926991;
              }
              this.arm1.rotateAngleY += f * 2.7307692 - 2.41661;
              this.arm1.rotateAngleZ += f * 1.1780972 - 0.3926991;
              this.arm2.rotateAngleZ += -0.19634955;
          } else if (fullTick < 8) {
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -0.3926991;
              }
              this.arm1.rotateAngleY += -2.41661;
              this.arm1.rotateAngleZ += -0.3926991;
              this.arm2.rotateAngleZ += -0.19634955;
          } else if (fullTick < 14) {
              var tick= ((fullTick - 8) + this.partialTick) / 6.0;
              var f= MH.cos((tick * PI / 2.0));
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += f * (-PI) / 8.0;
              }
              this.arm1.rotateAngleY += f * (-PI) / 1.3;
              this.arm1.rotateAngleZ += f * (-PI) / 8.0;
              this.arm2.rotateAngleZ += f * (-PI) / 16.0;
          }
        },
        animateShoot: function (fullTick, facePitch, faceYaw) {
          if (fullTick < 5) {
              var tick= (fullTick + this.partialTick) / 5.0;
              var f= MH.sin((tick * PI / 2.0));
              this.arm1.getModel().rotateAngleX += -f * PI / 4.0;
              this.arm1.rotateAngleY += -f * PI / 2.0;
              this.arm1.rotateAngleZ += f * PI / 16.0;
              this.forearm1.rotateAngleX += f * PI / 7.0;
              this.arm2.getModel().rotateAngleX += -f * PI / 4.0;
              this.arm2.rotateAngleY += f * PI / 2.0;
              this.arm2.rotateAngleZ += -f * PI / 16.0;
              this.arm2.getModel().rotateAngleZ += -f * PI / 8.0;
              this.forearm2.rotateAngleX += -f * PI / 6.0;
              this.bow.rotateRope();
          } else if (fullTick < 12) {
              var tick= ((fullTick - 5) + this.partialTick) / 7.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              var f1s= MH.sin((tick * PI / 2.0 * 0.4));
              this.head.getModel().rotateAngleY += f1 * PI / 4.0;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -f1 * PI / 12.0;
                  this.spine[i].middle.rotateAngleX += f1 * facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += f1 * faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += f * 0.2617994 - 1.0471976;
              this.arm1.rotateAngleY += f * -0.9424778 - 0.62831855;
              this.arm1.rotateAngleZ += f * -0.850848 + 1.0471976;
              this.forearm1.rotateAngleX += 0.44879895;
              this.arm2.getModel().rotateAngleX += f * 1.8325956 - 2.6179938;
              this.arm2.rotateAngleY += f * 0.9424778 + 0.62831855;
              this.arm2.rotateAngleZ += f * 0.850848 - 1.0471976;
              this.arm2.getModel().rotateAngleZ += -f * PI / 8.0;
              this.forearm2.rotateAngleX += f * 0.10471976 - 0.62831855;
              this.bow.middle1.rotateAngleX += -f1s * PI / 16.0;
              this.bow.side1.rotateAngleX += -f1s * PI / 24.0;
              this.bow.middle2.rotateAngleX += f1s * PI / 16.0;
              this.bow.side2.rotateAngleX += f1s * PI / 24.0;
              this.bow.rotateRope();
              this.bow.rope1.rotateAngleX += f1s * PI / 6.0;
              this.bow.rope2.rotateAngleX += -f1s * PI / 6.0;
          } else if (fullTick < 26) {
              this.head.getModel().rotateAngleY += 0.7853982;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -0.2617994;
                  this.spine[i].middle.rotateAngleX += facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += -1.0471976;
              this.arm1.rotateAngleY += -0.62831855;
              this.arm1.rotateAngleZ += 1.0;
              this.forearm1.rotateAngleX += 0.44879895;
              this.arm2.getModel().rotateAngleX += -2.6179938;
              this.arm2.rotateAngleY += 0.62831855;
              this.arm2.rotateAngleZ += -1.0471976;
              this.forearm2.rotateAngleX += -0.62831855;
              var tick= MH.clampF(((fullTick - 25) + this.partialTick), 0.0, 1.0);
              var f= MH.cos((tick * PI / 2.0));
              this.bow.middle1.rotateAngleX += -f * PI / 16.0;
              this.bow.side1.rotateAngleX += -f * PI / 24.0;
              this.bow.middle2.rotateAngleX += f * PI / 16.0;
              this.bow.side2.rotateAngleX += f * PI / 24.0;
              this.bow.rotateRope();
              this.bow.rope1.rotateAngleX += f * PI / 6.0;
              this.bow.rope2.rotateAngleX += -f * PI / 6.0;
          } else if (fullTick < 30) {
              var tick= ((fullTick - 26) + this.partialTick) / 4.0;
              var f= MH.cos((tick * PI / 2.0));
              this.head.getModel().rotateAngleY += f * PI / 4.0;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -f * PI / 12.0;
                  this.spine[i].middle.rotateAngleX += f * facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += f * faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += -f * PI / 3.0;
              this.arm1.rotateAngleY += -f * PI / 5.0;
              this.arm1.rotateAngleZ += f * PI / 3.0;
              this.forearm1.rotateAngleX += f * PI / 7.0;
              this.arm2.getModel().rotateAngleX += -f * PI / 1.2;
              this.arm2.rotateAngleY += f * PI / 5.0;
              this.arm2.rotateAngleZ += -f * PI / 3.0;
              this.forearm2.rotateAngleX += -f * PI / 5.0;
              this.bow.rotateRope();
          }
        },
        animateMultiShoot: function (fullTick, facePitch, faceYaw) {
          if (fullTick < 10) {
              var tick= (fullTick + this.partialTick) / 10.0;
              var f= MH.sin((tick * PI / 2.0));
              this.skeleBase.rotationPointY += f * 3.5;
              this.spine[0].middle.rotateAngleX += f * PI / 6.0;
              this.head.rotateAngleX += -f * PI / 4.0;
              this.arm1.rotateAngleX += f * PI / 6.0;
              this.arm1.rotateAngleZ += f * PI / 16.0;
              this.arm2.rotateAngleX += f * PI / 6.0;
              this.arm2.rotateAngleZ += -f * PI / 16.0;
              this.leg1.rotateAngleX += -f * PI / 8.0;
              this.leg2.rotateAngleX += -f * PI / 8.0;
              this.foreleg1.getModel().rotateAngleX += f * PI / 4.0;
              this.foreleg2.getModel().rotateAngleX += f * PI / 4.0;
              this.bow.rotateRope();
          } else if (fullTick < 12) {
              var tick= ((fullTick - 10) + this.partialTick) / 2.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.skeleBase.rotationPointY += f * 3.5;
              this.spine[0].middle.rotateAngleX += f * PI / 6.0;
              this.head.rotateAngleX += -f * PI / 4.0;
              this.arm1.rotateAngleX += f * PI / 6.0;
              this.arm1.rotateAngleZ += f * PI / 16.0;
              this.arm2.rotateAngleX += f * PI / 6.0;
              this.arm2.rotateAngleZ += -f * PI / 16.0;
              this.leg1.rotateAngleX += -f * PI / 8.0;
              this.leg2.rotateAngleX += -f * PI / 8.0;
              this.foreleg1.getModel().rotateAngleX += f * PI / 4.0;
              this.foreleg2.getModel().rotateAngleX += f * PI / 4.0;
              this.arm1.rotateAngleZ += -f1 * PI / 14.0;
              this.arm2.rotateAngleZ += f1 * PI / 14.0;
              this.leg1.rotateAngleZ += -f1 * PI / 24.0;
              this.leg2.rotateAngleZ += f1 * PI / 24.0;
              this.foreleg1.rotateAngleZ += f1 * PI / 64.0;
              this.foreleg2.rotateAngleZ += -f1 * PI / 64.0;
              this.bow.rotateRope();
          } else if (fullTick < 14) {
              this.arm1.rotateAngleZ += -0.22439948;
              this.arm2.rotateAngleZ += 0.22439948;
              this.leg1.rotateAngleZ += -0.1308997;
              this.leg2.rotateAngleZ += 0.1308997;
              this.foreleg1.rotateAngleZ += 0.049087387;
              this.foreleg2.rotateAngleZ += -0.049087387;
              this.bow.rotateRope();
          } else if (fullTick < 17) {
              var tick= ((fullTick - 14) + this.partialTick) / 3.0;
              var f= MH.sin((tick * PI / 2.0));
              var f1= MH.cos((tick * PI / 2.0));
              this.arm1.rotateAngleZ += -f1 * PI / 14.0;
              this.arm2.rotateAngleZ += f1 * PI / 14.0;
              this.leg1.rotateAngleZ += -f1 * PI / 24.0;
              this.leg2.rotateAngleZ += f1 * PI / 24.0;
              this.foreleg1.rotateAngleZ += f1 * PI / 64.0;
              this.foreleg2.rotateAngleZ += -f1 * PI / 64.0;
              this.arm1.getModel().rotateAngleX += -f * PI / 4.0;
              this.arm1.rotateAngleY += -f * PI / 2.0;
              this.arm1.rotateAngleZ += f * PI / 16.0;
              this.forearm1.rotateAngleX += f * PI / 7.0;
              this.arm2.getModel().rotateAngleX += -f * PI / 4.0;
              this.arm2.rotateAngleY += f * PI / 2.0;
              this.arm2.rotateAngleZ += -f * PI / 16.0;
              this.arm2.getModel().rotateAngleZ += -f * PI / 8.0;
              this.forearm2.rotateAngleX += -f * PI / 6.0;
              this.bow.rotateRope();
          } else if (fullTick < 20) {
              var tick= ((fullTick - 17) + this.partialTick) / 3.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              var f1s= MH.sin((tick * PI / 2.0 * 0.4));
              this.head.getModel().rotateAngleY += f1 * PI / 4.0;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -f1 * PI / 12.0;
                  this.spine[i].middle.rotateAngleX += f1 * facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += f1 * faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += f * 0.2617994 - 1.0471976;
              this.arm1.rotateAngleY += f * -0.9424778 - 0.62831855;
              this.arm1.rotateAngleZ += f * -0.850848 + 1.0471976;
              this.forearm1.rotateAngleX += 0.44879895;
              this.arm2.getModel().rotateAngleX += f * 1.8325956 - 2.6179938;
              this.arm2.rotateAngleY += f * 0.9424778 + 0.62831855;
              this.arm2.rotateAngleZ += f * 0.850848 - 1.0471976;
              this.arm2.getModel().rotateAngleZ += -f * PI / 8.0;
              this.forearm2.rotateAngleX += f * 0.10471976 - 0.62831855;
              this.bow.middle1.rotateAngleX += -f1s * PI / 16.0;
              this.bow.side1.rotateAngleX += -f1s * PI / 24.0;
              this.bow.middle2.rotateAngleX += f1s * PI / 16.0;
              this.bow.side2.rotateAngleX += f1s * PI / 24.0;
              this.bow.rotateRope();
              this.bow.rope1.rotateAngleX += f1s * PI / 6.0;
              this.bow.rope2.rotateAngleX += -f1s * PI / 6.0;
          } else if (fullTick < 24) {
              this.head.getModel().rotateAngleY += 0.7853982;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -0.2617994;
                  this.spine[i].middle.rotateAngleX += facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += -1.0471976;
              this.arm1.rotateAngleY += -0.62831855;
              this.arm1.rotateAngleZ += 1.0;
              this.forearm1.rotateAngleX += 0.44879895;
              this.arm2.getModel().rotateAngleX += -2.6179938;
              this.arm2.rotateAngleY += 0.62831855;
              this.arm2.rotateAngleZ += -1.0471976;
              this.forearm2.rotateAngleX += -0.62831855;
              var tick= MH.clampF(((fullTick - 25) + this.partialTick), 0.0, 1.0);
              var f= MH.cos((tick * PI / 2.0));
              this.bow.middle1.rotateAngleX += -f * PI / 16.0;
              this.bow.side1.rotateAngleX += -f * PI / 24.0;
              this.bow.middle2.rotateAngleX += f * PI / 16.0;
              this.bow.side2.rotateAngleX += f * PI / 24.0;
              this.bow.rotateRope();
              this.bow.rope1.rotateAngleX += f * PI / 6.0;
              this.bow.rope2.rotateAngleX += -f * PI / 6.0;
          } else if (fullTick < 28) {
              var tick= ((fullTick - 24) + this.partialTick) / 4.0;
              var f= MH.cos((tick * PI / 2.0));
              this.head.getModel().rotateAngleY += f * PI / 4.0;
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].middle.rotateAngleY += -f * PI / 12.0;
                  this.spine[i].middle.rotateAngleX += f * facePitch / 3.0;
                  this.spine[i].middle.rotateAngleY += f * faceYaw / 3.0;
              }
              this.arm1.getModel().rotateAngleX += -f * PI / 3.0;
              this.arm1.rotateAngleY += -f * PI / 5.0;
              this.arm1.rotateAngleZ += f * PI / 3.0;
              this.forearm1.rotateAngleX += f * PI / 7.0;
              this.arm2.getModel().rotateAngleX += -f * PI / 1.2;
              this.arm2.rotateAngleY += f * PI / 5.0;
              this.arm2.rotateAngleZ += -f * PI / 3.0;
              this.forearm2.rotateAngleX += -f * PI / 5.0;
              this.bow.rotateRope();
          }
        },
        animateConstrict: function () {
          block6: {
              var f;
              var tick;
              var animTick;
              block7: {
                  block5: {
                      this.animator.startPhase(5);
                      this.animator.rotate(this.waist, 0.1308997, 0.0, 0.0);
                      for (animTick = 0; animTick < this.spine.length; ++animTick) {
                          tick = animTick === 0 ? 0.3926991 : (animTick === 2 ? -0.3926991 : 0.0);
                          f = animTick === 1 ? 0.3926991 : 0.31415927;
                          this.animator.rotate(this.spine[animTick].side1[0], tick, f, 0.0);
                          this.animator.rotate(this.spine[animTick].side1[1], 0.0, 0.15707964, 0.0);
                          this.animator.rotate(this.spine[animTick].side1[2], 0.0, 0.2617994, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[0], tick, -f, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[1], 0.0, -0.15707964, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[2], 0.0, -0.2617994, 0.0);
                      }
                      this.animator.rotate(this.arm1, 0.0, 0.0, 0.8975979);
                      this.animator.rotate(this.arm2, 0.0, 0.0, -0.8975979);
                      this.animator.move(this.skeleBase, 0.0, 1.0, 0.0);
                      this.animator.rotate(this.leg1, -0.44879895, 0.0, 0.0);
                      this.animator.rotate(this.leg2, -0.44879895, 0.0, 0.0);
                      this.animator.rotate(this.foreleg1.getModel(), 0.5235988, 0.0, 0.0);
                      this.animator.rotate(this.foreleg2.getModel(), 0.5235988, 0.0, 0.0);
                      this.animator.endPhase();
                      this.animator.setStationaryPhase(2);
                      this.animator.startPhase(1);
                      this.animator.rotate(this.neck, 0.19634955, 0.0, 0.0);
                      this.animator.rotate(this.head, 0.15707964, 0.0, 0.0);
                      this.animator.rotate(this.waist, 0.31415927, 0.0, 0.0);
                      this.animator.rotate(this.spine[0].middle, 0.2617994, 0.0, 0.0);
                      for (animTick = 0; animTick < this.spine.length; ++animTick) {
                          tick = animTick === 0 ? 0.1308997 : (animTick === 2 ? -0.1308997 : 0.0);
                          f = animTick === 1 ? -0.17453294 : -0.22439948;
                          this.animator.rotate(this.spine[animTick].side1[0], tick - 0.08, f, 0.0);
                          this.animator.rotate(this.spine[animTick].side1[1], 0.0, 0.15707964, 0.0);
                          this.animator.rotate(this.spine[animTick].side1[2], 0.0, 0.2617994, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[0], tick + 0.08, -f, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[1], 0.0, -0.15707964, 0.0);
                          this.animator.rotate(this.spine[animTick].side2[2], 0.0, -0.2617994, 0.0);
                      }
                      this.animator.move(this.skeleBase, 0.0, 1.0, 0.0);
                      this.animator.rotate(this.leg1, -0.44879895, 0.0, 0.0);
                      this.animator.rotate(this.leg2, -0.44879895, 0.0, 0.0);
                      this.animator.rotate(this.foreleg1.getModel(), 0.5235988, 0.0, 0.0);
                      this.animator.rotate(this.foreleg2.getModel(), 0.5235988, 0.0, 0.0);
                      this.animator.endPhase();
                      this.animator.setStationaryPhase(4);
                      this.animator.resetPhase(8);
                      animTick = this.animator.getEntity().getAnimationTick();
                      if (animTick >= 5) break block5;
                      tick = (animTick + this.partialTick) / 5.0;
                      f = MH.sin((tick * PI / 2.0));
                      for (var i= 0; i < this.spine.length; ++i) {
                          this.spine[i].side1[0].setScale(1.0 + f * 0.6);
                          this.spine[i].side2[0].setScale(1.0 + f * 0.6);
                      }
                      break block6;
                  }
                  if (animTick >= 12) break block7;
                  for (var i= 0; i < this.spine.length; ++i) {
                      this.spine[i].side1[0].setScale(1.6);
                      this.spine[i].side2[0].setScale(1.6);
                  }
                  break block6;
              }
              if (animTick >= 20) break block6;
              tick = ((animTick - 12) + this.partialTick) / 8.0;
              f = MH.cos((tick * PI / 2.0));
              for (var i= 0; i < this.spine.length; ++i) {
                  this.spine[i].side1[0].setScale(1.0 + f * 0.6);
                  this.spine[i].side2[0].setScale(1.0 + f * 0.6);
              }
          }
        },
        setLivingAnimations: function (entitylivingbaseIn, limbSwing, limbSwingAmount, partialTickTime) {
          this.partialTick = partialTickTime;
        },
      }
    });
    // ------------------------------------------------------------ MutantSkeletonModel.java: static class Spine
    // Spine(ModelBase model) { this(model, false); } -> callers pass skeletonPart explicitly
    function Spine(model, skeletonPart) {
      this.middle = MR(model, 50, 0);
      this.middle.addBox(-2.5, -4.0, -2.0, 5, 4, 4, 0.5);
      this.side1 = new Array(3);
      this.side2 = new Array(3);
      this.side1[0] = SMR(model, 32, 12);
      this.side1[0].addBox(skeletonPart ? 0.0 : -6.0, -2.0, -2.0, 6, 2, 2, 0.25);
      if (!skeletonPart) {
        this.side1[0].setRotationPoint(-3.0, -1.0, 1.75);
      }
      this.middle.addChild(this.side1[0]);
      this.side2[0] = SMR(model, 32, 12);
      this.side2[0].mirror = 1;
      this.side2[0].addBox(skeletonPart ? -6.0 : 0.0, -2.0, -2.0, 6, 2, 2, 0.25);
      if (!skeletonPart) {
        this.side2[0].setRotationPoint(3.0, -1.0, 1.75);
      }
      this.middle.addChild(this.side2[0]);
      this.side1[1] = SMR(model, 32, 12);
      this.side1[1].mirror = 1;
      this.side1[1].addBox(-6.0, -2.0, -2.0, 6, 2, 2, 0.2);
      this.side1[1].setRotationPoint(skeletonPart ? -0.5 : -6.5, 0.0, 0.0);
      this.side1[0].addChild(this.side1[1]);
      this.side2[1] = SMR(model, 32, 12);
      this.side2[1].addBox(0.0, -2.0, -2.0, 6, 2, 2, 0.2);
      this.side2[1].setRotationPoint(skeletonPart ? 0.5 : 6.5, 0.0, 0.0);
      this.side2[0].addChild(this.side2[1]);
      this.side1[2] = SMR(model, 32, 12);
      this.side1[2].addBox(-6.0, -2.0, -2.0, 6, 2, 2, 0.15);
      this.side1[2].setRotationPoint(-6.4, 0.0, 0.0);
      this.side1[1].addChild(this.side1[2]);
      this.side2[2] = SMR(model, 32, 12);
      this.side2[2].mirror = 1;
      this.side2[2].addBox(0.0, -2.0, -2.0, 6, 2, 2, 0.15);
      this.side2[2].setRotationPoint(6.4, 0.0, 0.0);
      this.side2[1].addChild(this.side2[2]);
    }
    // private void resetAngles(ModelRenderer ... boxes)
    Spine.prototype.resetAngles = function (boxes) {
      if (!Array.isArray(boxes)) boxes = [boxes];
      for (var i = 0; i < boxes.length; ++i) {
        var box = boxes[i];
        box.rotateAngleX = 0.0;
        box.rotateAngleY = 0.0;
        box.rotateAngleZ = 0.0;
      }
    };
    Spine.prototype.setAngles = function (PI, middleSpine) {
      this.resetAngles(this.middle);
      this.resetAngles(this.side1);
      this.resetAngles(this.side2);
      this.middle.rotateAngleX = PI / 18.0;
      this.side1[0].rotateAngleY = -PI / 4.5;
      this.side2[0].rotateAngleY = PI / 4.5;
      this.side1[1].rotateAngleY = -PI / 3.0;
      this.side2[1].rotateAngleY = PI / 3.0;
      this.side1[2].rotateAngleY = -PI / 3.5;
      this.side2[2].rotateAngleY = PI / 3.5;
      if (middleSpine) {
        for (var i = 0; i < this.side1.length; ++i) {
          this.side1[i].rotateAngleY *= 0.98;
          this.side2[i].rotateAngleY *= 0.98;
        }
      }
      this.side1[0].setScale(1.0);
      this.side2[0].setScale(1.0);
    };
    Spine.prototype.animate = function (breatheAnim) {
      this.side1[1].rotateAngleY += breatheAnim * 0.02;
      this.side2[1].rotateAngleY -= breatheAnim * 0.02;
    };

    // ============================================================ MutantSkeletonPartModel.java
    MODELS.MutantSkeletonPartModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantSkeletonPartModel",
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 128;
          this.pelvis = MR(this, 0, 16);
          this.pelvis.addBox(-4.0, -3.0, -3.0, 8, 6, 6);
          this.spine = new Array(3);
          for (var i= 0; i < this.spine.length; ++i) {
              this.spine[i] = new Spine(this, true);
              M.listRemove(this.boxList, this.spine[i].middle);   // boxList.remove(Object)
          }
          this.head = JMR(this, 0, 0);
          this.head.addBox(-4.0, -4.0, -4.0, 8, 8, 8, 0.4);
          this.jaw = MR(this, 72, 0);
          this.jaw.addBox(-4.0, -3.0, -8.0, 8, 3, 8, 0.7);
          this.jaw.setRotationPoint(0.0, 3.8, 3.7);
          this.head.addChild(this.jaw);
          this.arm1 = JMR(this, 0, 28);
          this.arm1.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.arm2 = JMR(this, 0, 28);
          this.arm2.mirror = 1;
          this.arm2.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.forearm1 = JMR(this, 16, 28);
          this.forearm1.addBox(-2.0, -7.0, -2.0, 4, 14, 4, -0.01);
          this.forearm2 = JMR(this, 16, 28);
          this.forearm2.mirror = 1;
          this.forearm2.addBox(-2.0, -7.0, -2.0, 4, 14, 4, -0.01);
          this.leg1 = JMR(this, 0, 28);
          this.leg1.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.leg2 = JMR(this, 0, 28);
          this.leg2.mirror = 1;
          this.leg2.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.foreleg1 = JMR(this, 32, 28);
          this.foreleg1.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.foreleg2 = JMR(this, 32, 28);
          this.foreleg2.mirror = 1;
          this.foreleg2.addBox(-2.0, -6.0, -2.0, 4, 12, 4);
          this.shoulder1 = MR(this, 28, 16);
          this.shoulder1.addBox(-4.0, -1.5, -3.0, 8, 3, 6);
          this.shoulder2 = MR(this, 28, 16);
          this.shoulder2.mirror = 1;
          this.shoulder2.addBox(-4.0, -1.5, -3.0, 8, 3, 6);
      },
      methods: {
        setAngles: function () {
          this.jaw.rotateAngleX = 0.09817477;
          for (var i= 0; i < this.spine.length; ++i) {
              this.spine[i].setAngles(PI, i === 1);
          }
        },
        getSkeletonPart: function (index) {
          return M.listGet(this.boxList, index);
        },
      }
    });

    // ============================================================ MutantCreeperModel.java
    MODELS.MutantCreeperModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantCreeperModel",
      nonVirtual: ["setRotationAngles"],                    // private setRotationAngles(float x6): not ModelBase's
      ctor: function (scale) {                              // MutantCreeperModel() { this(0.0f); } / (float scale)
          if (scale === undefined) scale = 0.0;
          this.textureWidth = 128;
          this.textureHeight = 64;
          this.pelvis = MR(this, 0, 0);
          this.pelvis.addBox(-5.0, -14.0, -4.0, 10, 14, 8, scale);
          this.pelvis.setRotationPoint(0.0, 14.0, -3.0);
          this.body = MR(this, 36, 0);
          this.body.addBox(-4.5, -14.0, -3.5, 9, 16, 7, scale);
          this.body.setRotationPoint(0.0, -12.0, 0.0);
          this.pelvis.addChild(this.body);
          this.neck = MR(this, 68, 0);
          this.neck.addBox(-4.0, -14.0, -3.0, 8, 14, 6, scale);
          this.neck.setRotationPoint(0.0, -11.0, 1.0);
          this.body.addChild(this.neck);
          this.head = MR(this, 0, 22);
          this.head.addBox(-5.0, -12.0, -5.0, 10, 12, 10, scale);
          this.head.setRotationPoint(0.0, -12.0, 1.0);
          this.neck.addChild(this.head);
          this.frleg = MR(this, 40, 24);
          this.frleg.addBox(-3.0, -4.0, -14.0, 6, 4, 14, scale);
          this.frleg.setRotationPoint(3.0, 0.0, 0.0);
          this.pelvis.addChild(this.frleg);
          this.flleg = MR(this, 40, 24);
          this.flleg.mirror = 1;
          this.flleg.addBox(-3.0, -4.0, -14.0, 6, 4, 14, scale);
          this.flleg.setRotationPoint(-3.0, 0.0, 0.0);
          this.pelvis.addChild(this.flleg);
          this.frforeleg = MR(this, 96, 0);
          this.frforeleg.addBox(-3.5, 0.0, -4.0, 7, 20, 8, scale);
          this.frforeleg.setRotationPoint(0.0, -4.0, -14.0);
          this.frleg.addChild(this.frforeleg);
          this.flforeleg = MR(this, 96, 0);
          this.flforeleg.mirror = 1;
          this.flforeleg.addBox(-3.5, 0.0, -4.0, 7, 20, 8, scale);
          this.flforeleg.setRotationPoint(0.0, -4.0, -14.0);
          this.flleg.addChild(this.flforeleg);
          this.brleg = MR(this, 0, 44);
          this.brleg.addBox(-2.0, -4.0, 0.0, 4, 4, 14, scale);
          this.brleg.setRotationPoint(2.0, -2.0, 4.0);
          this.pelvis.addChild(this.brleg);
          this.blleg = MR(this, 0, 44);
          this.blleg.mirror = 1;
          this.blleg.addBox(-2.0, -4.0, 0.0, 4, 4, 14, scale);
          this.blleg.setRotationPoint(-2.0, -2.0, 4.0);
          this.pelvis.addChild(this.blleg);
          this.brforeleg = MR(this, 80, 28);
          this.brforeleg.addBox(-3.0, 0.0, -3.0, 6, 18, 6, scale);
          this.brforeleg.setRotationPoint(0.0, -4.0, 14.0);
          this.brleg.addChild(this.brforeleg);
          this.blforeleg = MR(this, 80, 28);
          this.blforeleg.mirror = 1;
          this.blforeleg.addBox(-3.0, 0.0, -3.0, 6, 18, 6, scale);
          this.blforeleg.setRotationPoint(0.0, -4.0, 14.0);
          this.blleg.addChild(this.blforeleg);
      },
      methods: {
        render: function (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setAngles();
          this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
          this.pelvis.render(scale);
        },
        setAngles: function () {
          this.pelvis.rotationPointY = 14.0;
          this.pelvis.rotateAngleX = -0.7853982;
          this.body.rotateAngleX = 0.9424778;
          this.body.rotateAngleY = 0.0;
          this.neck.rotateAngleX = 1.0471976;
          this.head.rotateAngleX = 0.5235988;
          this.frleg.rotateAngleX = 0.31415927;
          this.frleg.rotateAngleY = -0.7853982;
          this.frleg.rotateAngleZ = 0.0;
          this.flleg.rotateAngleX = 0.31415927;
          this.flleg.rotateAngleY = 0.7853982;
          this.flleg.rotateAngleZ = 0.0;
          this.frforeleg.rotateAngleX = -0.20943952;
          this.frforeleg.rotateAngleY = 0.3926991;
          this.flforeleg.rotateAngleX = -0.20943952;
          this.flforeleg.rotateAngleY = -0.3926991;
          this.brleg.rotateAngleX = 0.9;
          this.brleg.rotateAngleY = 0.62831855;
          this.brleg.rotateAngleZ = 0.0;
          this.blleg.rotateAngleX = 0.9;
          this.blleg.rotateAngleY = -0.62831855;
          this.blleg.rotateAngleZ = 0.0;
          this.brforeleg.rotateAngleX = 0.48332196;
          this.blforeleg.rotateAngleX = 0.48332196;
        },
        setRotationAngles: function (f, f1, f2, f3, f4, f5) {
          var breatheAnim= MH.sin((f2 * 0.1));
          var walkAnim1= (MH.sin((f * PI / 4.0)) + 0.4) * f1;
          var walkAnim2= (MH.sin((f * PI / 4.0 + PI)) + 0.4) * f1;
          if (walkAnim1 < 0.0) {
              walkAnim1 = 0.0;
          }
          if (walkAnim2 < 0.0) {
              walkAnim2 = 0.0;
          }
          var walkAnim3= MH.sin((f * PI / 8.0)) * f1;
          var walkAnim4= (MH.sin((f * PI / 4.0 + 1.5707964)) + 0.4) * f1;
          var walkAnim5= (MH.sin((f * PI / 4.0 + 4.712389)) + 0.4) * f1;
          if (walkAnim4 < 0.0) {
              walkAnim4 = 0.0;
          }
          if (walkAnim5 < 0.0) {
              walkAnim5 = 0.0;
          }
          var walkAnim6= MH.sin((f * PI / 8.0 + 1.5707964)) * f1;
          var faceYaw= f3 / 57.295776;
          var facePitch= f4 / 57.295776;
          var f6= faceYaw / 3.0;
          var f7= facePitch / 3.0;
          this.pelvis.rotationPointY += MH.sin((f * PI / 4.0)) * f1 * 0.5;
          this.body.rotateAngleX += breatheAnim * 0.02;
          this.body.rotateAngleX += f7;
          this.body.rotateAngleY += f6;
          this.neck.rotateAngleX += breatheAnim * 0.02;
          this.neck.rotateAngleX += f7;
          this.neck.rotateAngleY = f6;
          this.head.rotateAngleX += breatheAnim * 0.02;
          this.head.rotateAngleX += f7;
          this.head.rotateAngleY = f6;
          this.frleg.rotateAngleX -= walkAnim1 * 0.3;
          this.frleg.rotateAngleY += walkAnim3 * 0.2;
          this.frleg.rotateAngleZ += walkAnim3 * 0.2;
          this.flleg.rotateAngleX -= walkAnim2 * 0.3;
          this.flleg.rotateAngleY -= walkAnim3 * 0.2;
          this.flleg.rotateAngleZ -= walkAnim3 * 0.2;
          this.brleg.rotateAngleX += walkAnim5 * 0.3;
          this.brleg.rotateAngleY -= walkAnim6 * 0.2;
          this.brleg.rotateAngleZ -= walkAnim6 * 0.2;
          this.blleg.rotateAngleX += walkAnim4 * 0.3;
          this.blleg.rotateAngleY += walkAnim6 * 0.2;
          this.blleg.rotateAngleZ += walkAnim6 * 0.2;
          if (this.swingProgress > -9990.0) {
              var swingAnim= MH.sin((this.swingProgress * PI));
              this.body.rotateAngleX += swingAnim * PI / 3.0;
              this.neck.rotateAngleX -= swingAnim * PI / 4.0;
          }
        },
      }
    });

    // ============================================================ MutantEndermanModel.java
    MODELS.MutantEndermanModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantEndermanModel",
      fields: function (s) { s.partialTick = 0.0; },
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 64;
          this.pelvis = MR(this);
          this.pelvis.setRotationPoint(0.0, -15.5, 8.0);
          this.abdomen = MR(this, 32, 0);
          this.abdomen.addBox(-4.0, -10.0, -2.0, 8, 10, 4);
          this.pelvis.addChild(this.abdomen);
          this.chest = MR(this, 50, 8);
          this.chest.addBox(-5.0, -16.0, -3.0, 10, 16, 6);
          this.chest.setRotationPoint(0.0, -8.0, 0.0);
          this.abdomen.addChild(this.chest);
          this.neck = MR(this, 32, 14);
          this.neck.addBox(-1.5, -4.0, -1.5, 3, 4, 3);
          this.neck.setRotationPoint(0.0, -15.0, 0.0);
          this.chest.addChild(this.neck);
          this.head = MR(this);
          this.head.setTextureOffset(0, 0).addBox(-4.0, -4.0, -8.0, 8, 6, 8, 0.5);
          this.head.setTextureOffset(0, 14).addBox(-4.0, 3.0, -8.0, 8, 2, 8, 0.5);
          this.head.setRotationPoint(0.0, -5.0, 3.0);
          this.neck.addChild(this.head);
          this.mouth = MR(this, 0, 24);
          this.mouth.addBox(-4.0, 3.0, -8.0, 8, 2, 8);
          this.head.addChild(this.mouth);
          this.rightArm = new Arm(this, this.chest, true);
          this.leftArm = new Arm(this, this.chest, false);
          this.lowerRightArm = new Arm(this, this.chest, true);
          this.lowerRightArm.arm.rotationPointY += 6.0;
          this.lowerLeftArm = new Arm(this, this.chest, false);
          this.lowerLeftArm.arm.rotationPointY += 6.0;
          this.legjoint1 = MR(this);
          this.legjoint1.setRotationPoint(-1.5, 0.0, 0.75);
          this.abdomen.addChild(this.legjoint1);
          this.legjoint2 = MR(this);
          this.legjoint2.setRotationPoint(1.5, 0.0, 0.75);
          this.abdomen.addChild(this.legjoint2);
          this.leg1 = MR(this, 0, 34);
          this.leg1.addBox(-1.5, 0.0, -1.5, 3, 24, 3, 0.5);
          this.leg1.setRotationPoint(0.0, -2.0, 0.0);
          this.legjoint1.addChild(this.leg1);
          this.leg2 = MR(this, 0, 34);
          this.leg2.mirror = 1;
          this.leg2.addBox(-1.5, 0.0, -1.5, 3, 24, 3, 0.5);
          this.leg2.setRotationPoint(0.0, -2.0, 0.0);
          this.legjoint2.addChild(this.leg2);
          this.foreleg1 = MR(this, 12, 34);
          this.foreleg1.addBox(-1.5, 0.0, -1.5, 3, 24, 3, 0.5);
          this.foreleg1.setRotationPoint(0.0, 23.0, 0.0);
          this.leg1.addChild(this.foreleg1);
          this.foreleg2 = MR(this, 12, 34);
          this.foreleg2.mirror = 1;
          this.foreleg2.addBox(-1.5, 0.0, -1.5, 3, 24, 3, 0.5);
          this.foreleg2.setRotationPoint(0.0, 23.0, 0.0);
          this.leg2.addChild(this.foreleg2);
      },
      methods: {
        render: function (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setAngles();
          var mutantEnderman= entity;
          this.animate(mutantEnderman, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
          this.lowerRightArm.arm.setScale(mutantEnderman.getArmScale(this.partialTick));
          this.lowerLeftArm.arm.setScale(mutantEnderman.getArmScale(this.partialTick));
          this.pelvis.render(scale);
        },
        setAngles: function () {
          this.pelvis.rotationPointY = -15.5;
          this.abdomen.rotateAngleX = 0.31415927;
          this.chest.rotateAngleX = 0.3926991;
          this.chest.rotateAngleY = 0.0;
          this.chest.rotateAngleZ = 0.0;
          this.neck.rotateAngleX = 0.19634955;
          this.neck.rotateAngleZ = 0.0;
          this.head.rotateAngleX = -0.7853982;
          this.head.rotateAngleY = 0.0;
          this.head.rotateAngleZ = 0.0;
          this.mouth.rotateAngleX = 0.0;
          this.rightArm.setAngles();
          this.leftArm.setAngles();
          this.lowerRightArm.setAngles();
          this.lowerRightArm.arm.rotateAngleX += 0.1;
          this.lowerRightArm.arm.rotateAngleZ -= 0.2;
          this.lowerLeftArm.setAngles();
          this.lowerLeftArm.arm.rotateAngleX += 0.1;
          this.lowerLeftArm.arm.rotateAngleZ += 0.2;
          this.legjoint1.rotateAngleX = 0.0;
          this.legjoint2.rotateAngleX = 0.0;
          this.leg1.rotateAngleX = -0.8975979;
          this.leg1.rotateAngleY = 0.0;
          this.leg1.rotateAngleZ = 0.2617994;
          this.leg2.rotateAngleX = -0.8975979;
          this.leg2.rotateAngleY = 0.0;
          this.leg2.rotateAngleZ = -0.2617994;
          this.foreleg1.rotateAngleX = 0.7853982;
          this.foreleg1.rotateAngleZ = -0.1308997;
          this.foreleg2.rotateAngleX = 0.7853982;
          this.foreleg2.rotateAngleZ = 0.1308997;
        },
        animate: function (enderman, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, f5) {
          var scale;
          var arm;
          var walkSpeed= 0.3;
          var walkAnim1= (MH.sin(((limbSwing - 0.8) * walkSpeed)) + 0.8) * limbSwingAmount;
          var walkAnim2= -(MH.sin(((limbSwing + 0.8) * walkSpeed)) - 0.8) * limbSwingAmount;
          var walkAnim3= (MH.sin(((limbSwing + 0.8) * walkSpeed)) - 0.8) * limbSwingAmount;
          var walkAnim4= -(MH.sin(((limbSwing - 0.8) * walkSpeed)) + 0.8) * limbSwingAmount;
          var walkAnim= new Array(5).fill(0.0);
          walkAnim.fill(MH.sin((limbSwing * walkSpeed)) * limbSwingAmount);
          var breatheAnim= MH.sin((ageInTicks * 0.15));
          var faceYaw= netHeadYaw * PI / 180.0;
          var facePitch= headPitch * PI / 180.0;
          for (arm = 1; arm < enderman.heldBlock.length; ++arm) {
              if (enderman.heldBlock[arm] === 0) continue;
              this.animateHoldBlock(enderman.heldBlockTick[arm], arm, enderman.hasTarget > 0);
              var n= arm;
              walkAnim[n] = walkAnim[n] * 0.4;
          }
          if (enderman.getAttackID() === 1) {
              arm = enderman.getActiveArm();
              this.animateMelee(enderman.getAttackTick(), arm);
              walkAnim[arm] = 0.0;
          }
          if (enderman.getAttackID() === 2) {
              arm = enderman.getActiveArm();
              this.animateThrowBlock(enderman.getAttackTick(), arm);
          }
          if (enderman.getAttackID() === 5) {
              this.animateScream(enderman.getAttackTick());
              scale = 1.0 - MH.clampF((enderman.getAttackTick() / 6.0), 0.0, 1.0);
              faceYaw *= scale;
              facePitch *= scale;
              walkAnim1 *= scale;
              walkAnim2 *= scale;
              walkAnim3 *= scale;
              walkAnim4 *= scale;
              walkAnim.fill(0.0);
          }
          if (enderman.getAttackID() === 7) {
              this.animateTeleSmash(enderman.getAttackTick());
          }
          if (enderman.getAttackID() === 8) {
              this.animateDeath(enderman.deathTime);
              scale = 1.0 - MH.clampF((enderman.deathTime / 6.0), 0.0, 1.0);
              faceYaw *= scale;
              facePitch *= scale;
              walkAnim1 *= scale;
              walkAnim2 *= scale;
              walkAnim3 *= scale;
              walkAnim4 *= scale;
              walkAnim.fill(0.0);
          }
          this.head.rotateAngleX += facePitch * 0.5;
          this.head.rotateAngleY += faceYaw * 0.7;
          this.head.rotateAngleZ -= faceYaw * 0.7;
          this.neck.rotateAngleX += facePitch * 0.3;
          this.chest.rotateAngleX += facePitch * 0.2;
          this.mouth.rotateAngleX += breatheAnim * 0.02 + 0.02;
          this.neck.rotateAngleX -= breatheAnim * 0.02;
          this.rightArm.arm.rotateAngleZ += breatheAnim * 0.004;
          this.leftArm.arm.rotateAngleZ -= breatheAnim * 0.004;
          for (var finger of this.rightArm.finger) {
              finger.rotateAngleZ += breatheAnim * 0.05;
          }
          this.rightArm.thumb.rotateAngleZ -= breatheAnim * 0.05;
          for (var finger of this.leftArm.finger) {
              finger.rotateAngleZ -= breatheAnim * 0.05;
          }
          this.leftArm.thumb.rotateAngleZ += breatheAnim * 0.05;
          this.lowerRightArm.arm.rotateAngleZ += breatheAnim * 0.002;
          this.lowerLeftArm.arm.rotateAngleZ -= breatheAnim * 0.002;
          for (var finger of this.lowerRightArm.finger) {
              finger.rotateAngleZ += breatheAnim * 0.02;
          }
          this.lowerRightArm.thumb.rotateAngleZ -= breatheAnim * 0.02;
          for (var finger of this.lowerLeftArm.finger) {
              finger.rotateAngleZ -= breatheAnim * 0.02;
          }
          this.lowerLeftArm.thumb.rotateAngleZ += breatheAnim * 0.02;
          this.pelvis.rotationPointY -= Math.abs(walkAnim[0]);
          this.chest.rotateAngleY -= walkAnim[0] * 0.06;
          this.rightArm.arm.rotateAngleX -= walkAnim[1] * 0.6;
          this.leftArm.arm.rotateAngleX += walkAnim[2] * 0.6;
          this.rightArm.forearm.rotateAngleX -= walkAnim[1] * 0.2;
          this.leftArm.forearm.rotateAngleX += walkAnim[2] * 0.2;
          this.lowerRightArm.arm.rotateAngleX -= walkAnim[3] * 0.3;
          this.lowerLeftArm.arm.rotateAngleX += walkAnim[4] * 0.3;
          this.lowerRightArm.forearm.rotateAngleX -= walkAnim[3] * 0.1;
          this.lowerLeftArm.forearm.rotateAngleX += walkAnim[4] * 0.1;
          this.legjoint1.rotateAngleX += walkAnim1 * 0.6;
          this.legjoint2.rotateAngleX += walkAnim2 * 0.6;
          this.foreleg1.rotateAngleX += walkAnim3 * 0.3;
          this.foreleg2.rotateAngleX += walkAnim4 * 0.3;
        },
        animateHoldBlock: function (fullTick, armID, hasTarget) {
          var tick= (fullTick + this.partialTick) / 10.0;
          if (!hasTarget) {
              tick = fullTick === 0 ? 0.0 : (fullTick - this.partialTick) / 10.0;
          }
          var f= MH.sin((tick * PI / 2.0));
          if (armID === 1) {
              this.rightArm.arm.rotateAngleZ += f * 0.8;
              this.rightArm.forearm.rotateAngleZ += f * 0.6;
              this.rightArm.hand.rotateAngleY += f * 0.8;
              this.rightArm.finger[0].rotateAngleX += -f * 0.2;
              this.rightArm.finger[2].rotateAngleX += f * 0.2;
              for (var i= 0; i < this.rightArm.finger.length; ++i) {
                  this.rightArm.finger[i].rotateAngleZ += f * 0.6;
              }
              this.rightArm.thumb.rotateAngleZ += -f * 0.4;
          } else if (armID === 2) {
              this.leftArm.arm.rotateAngleZ += -f * 0.8;
              this.leftArm.forearm.rotateAngleZ += -f * 0.6;
              this.leftArm.hand.rotateAngleY += -f * 0.8;
              this.leftArm.finger[0].rotateAngleX += -f * 0.2;
              this.leftArm.finger[2].rotateAngleX += f * 0.2;
              for (var i= 0; i < this.leftArm.finger.length; ++i) {
                  this.leftArm.finger[i].rotateAngleZ += -f * 0.6;
              }
              this.leftArm.thumb.rotateAngleZ += f * 0.4;
          } else if (armID === 3) {
              this.lowerRightArm.arm.rotateAngleZ += f * 0.5;
              this.lowerRightArm.forearm.rotateAngleZ += f * 0.4;
              this.lowerRightArm.hand.rotateAngleY += f * 0.4;
              this.lowerRightArm.finger[0].rotateAngleX += -f * 0.2;
              this.lowerRightArm.finger[2].rotateAngleX += f * 0.2;
              for (var i= 0; i < this.lowerRightArm.finger.length; ++i) {
                  this.lowerRightArm.finger[i].rotateAngleZ += f * 0.6;
              }
              this.lowerRightArm.thumb.rotateAngleZ += -f * 0.4;
          } else if (armID === 4) {
              this.lowerLeftArm.arm.rotateAngleZ += -f * 0.5;
              this.lowerLeftArm.forearm.rotateAngleZ += -f * 0.4;
              this.lowerLeftArm.hand.rotateAngleY += -f * 0.4;
              this.lowerLeftArm.finger[0].rotateAngleX += -f * 0.2;
              this.lowerLeftArm.finger[2].rotateAngleX += f * 0.2;
              for (var i= 0; i < this.lowerLeftArm.finger.length; ++i) {
                  this.lowerLeftArm.finger[i].rotateAngleZ += -f * 0.6;
              }
              this.lowerLeftArm.thumb.rotateAngleZ += f * 0.4;
          }
        },
        animateMelee: function (fullTick, armID) {
          var right= (armID & 1) === 1 ? 1 : -1;
          var arm= this.getArmFromID(armID);
          if (fullTick < 2) {
              var tick= (fullTick + this.partialTick) / 2.0;
              var f= MH.sin((tick * PI / 2.0));
              arm.arm.rotateAngleX += f * 0.2;
              arm.finger[0].rotateAngleZ += f * 0.3 * right;
              arm.finger[1].rotateAngleZ += f * 0.3 * right;
              arm.finger[2].rotateAngleZ += f * 0.3 * right;
              arm.foreFinger[0].rotateAngleZ += -f * 0.5 * right;
              arm.foreFinger[1].rotateAngleZ += -f * 0.5 * right;
              arm.foreFinger[2].rotateAngleZ += -f * 0.5 * right;
          } else if (fullTick < 5) {
              var tick= ((fullTick - 2) + this.partialTick) / 3.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.chest.rotateAngleY += -f1 * 0.1 * right;
              arm.arm.rotateAngleX += f * 1.1 - 1.1;
              arm.forearm.rotateAngleX += -f * 0.4;
              arm.finger[0].rotateAngleZ += 0.3 * right;
              arm.finger[1].rotateAngleZ += 0.3 * right;
              arm.finger[2].rotateAngleZ += 0.3 * right;
              arm.foreFinger[0].rotateAngleZ += -0.5 * right;
              arm.foreFinger[1].rotateAngleZ += -0.5 * right;
              arm.foreFinger[2].rotateAngleZ += -0.5 * right;
          } else if (fullTick < 6) {
              this.chest.rotateAngleY += -0.1 * right;
              arm.arm.rotateAngleX += -1.1;
              arm.forearm.rotateAngleX += -0.4;
              arm.finger[0].rotateAngleZ += 0.3 * right;
              arm.finger[1].rotateAngleZ += 0.3 * right;
              arm.finger[2].rotateAngleZ += 0.3 * right;
              arm.foreFinger[0].rotateAngleZ += -0.5 * right;
              arm.foreFinger[1].rotateAngleZ += -0.5 * right;
              arm.foreFinger[2].rotateAngleZ += -0.5 * right;
          } else if (fullTick < 10) {
              var tick= ((fullTick - 6) + this.partialTick) / 4.0;
              var f= MH.cos((tick * PI / 2.0));
              this.chest.rotateAngleY += -f * 0.1 * right;
              arm.arm.rotateAngleX += -f * 1.1;
              arm.forearm.rotateAngleX += -f * 0.4;
              arm.finger[0].rotateAngleZ += f * 0.3 * right;
              arm.finger[1].rotateAngleZ += f * 0.3 * right;
              arm.finger[2].rotateAngleZ += f * 0.3 * right;
              arm.foreFinger[0].rotateAngleZ += -f * 0.5 * right;
              arm.foreFinger[1].rotateAngleZ += -f * 0.5 * right;
              arm.foreFinger[2].rotateAngleZ += -f * 0.5 * right;
          }
        },
        animateThrowBlock: function (fullTick, armID) {
          if (armID === 1) {
              if (fullTick < 4) {
                  var tick= (fullTick + this.partialTick) / 4.0;
                  var f= MH.cos((tick * PI / 2.0));
                  var f1= MH.sin((tick * PI / 2.0));
                  this.rightArm.arm.rotateAngleX += -f1 * 1.5;
                  this.rightArm.arm.rotateAngleZ += f * 0.8;
                  this.rightArm.forearm.rotateAngleZ += f * 0.6;
                  this.rightArm.hand.rotateAngleY += f * 0.8;
                  this.rightArm.finger[0].rotateAngleX += -f * 0.2;
                  this.rightArm.finger[2].rotateAngleX += f * 0.2;
                  for (var i= 0; i < this.rightArm.finger.length; ++i) {
                      this.rightArm.finger[i].rotateAngleZ += f * 0.6;
                  }
                  this.rightArm.thumb.rotateAngleZ += -f * 0.4;
              } else if (fullTick < 7) {
                  this.rightArm.arm.rotateAngleX += -1.5;
              } else if (fullTick < 14) {
                  var tick= ((fullTick - 7) + this.partialTick) / 7.0;
                  var f= MH.cos((tick * PI / 2.0));
                  this.rightArm.arm.rotateAngleX += -f * 1.5;
              }
          } else if (armID === 2) {
              if (fullTick < 4) {
                  var tick= (fullTick + this.partialTick) / 4.0;
                  var f= MH.cos((tick * PI / 2.0));
                  var f1= MH.sin((tick * PI / 2.0));
                  this.leftArm.arm.rotateAngleX += -f1 * 1.5;
                  this.leftArm.arm.rotateAngleZ += -f * 0.8;
                  this.leftArm.forearm.rotateAngleZ += -f * 0.6;
                  this.leftArm.hand.rotateAngleY += -f * 0.8;
                  this.leftArm.finger[0].rotateAngleX += -f * 0.2;
                  this.leftArm.finger[2].rotateAngleX += f * 0.2;
                  for (var i= 0; i < this.leftArm.finger.length; ++i) {
                      this.leftArm.finger[i].rotateAngleZ += -f * 0.6;
                  }
                  this.leftArm.thumb.rotateAngleZ += f * 0.4;
              } else if (fullTick < 7) {
                  this.leftArm.arm.rotateAngleX += -1.5;
              } else if (fullTick < 14) {
                  var tick= ((fullTick - 7) + this.partialTick) / 7.0;
                  var f= MH.cos((tick * PI / 2.0));
                  this.leftArm.arm.rotateAngleX += -f * 1.5;
              }
          } else if (armID === 3) {
              if (fullTick < 4) {
                  var tick= (fullTick + this.partialTick) / 4.0;
                  var f= MH.cos((tick * PI / 2.0));
                  var f1= MH.sin((tick * PI / 2.0));
                  this.lowerRightArm.arm.rotateAngleX += -f1 * 1.5;
                  this.lowerRightArm.arm.rotateAngleZ += f * 0.5;
                  this.lowerRightArm.forearm.rotateAngleZ += f * 0.4;
                  this.lowerRightArm.hand.rotateAngleY += f * 0.4;
                  this.lowerRightArm.finger[0].rotateAngleX += -f * 0.2;
                  this.lowerRightArm.finger[2].rotateAngleX += f * 0.2;
                  for (var i= 0; i < this.lowerRightArm.finger.length; ++i) {
                      this.lowerRightArm.finger[i].rotateAngleZ += f * 0.6;
                  }
                  this.lowerRightArm.thumb.rotateAngleZ += -f * 0.4;
              } else if (fullTick < 7) {
                  this.lowerRightArm.arm.rotateAngleX += -1.5;
              } else if (fullTick < 14) {
                  var tick= ((fullTick - 7) + this.partialTick) / 7.0;
                  var f= MH.cos((tick * PI / 2.0));
                  this.lowerRightArm.arm.rotateAngleX += -f * 1.5;
              }
          } else if (armID === 4) {
              if (fullTick < 4) {
                  var tick= (fullTick + this.partialTick) / 4.0;
                  var f= MH.cos((tick * PI / 2.0));
                  var f1= MH.sin((tick * PI / 2.0));
                  this.lowerLeftArm.arm.rotateAngleX += -f1 * 1.5;
                  this.lowerLeftArm.arm.rotateAngleZ += -f * 0.5;
                  this.lowerLeftArm.forearm.rotateAngleZ += -f * 0.4;
                  this.lowerLeftArm.hand.rotateAngleY += -f * 0.4;
                  this.lowerLeftArm.finger[0].rotateAngleX += -f * 0.2;
                  this.lowerLeftArm.finger[2].rotateAngleX += f * 0.2;
                  for (var i= 0; i < this.lowerLeftArm.finger.length; ++i) {
                      this.lowerLeftArm.finger[i].rotateAngleZ += -f * 0.6;
                  }
                  this.lowerLeftArm.thumb.rotateAngleZ += f * 0.4;
              } else if (fullTick < 7) {
                  this.lowerLeftArm.arm.rotateAngleX += -1.5;
              } else if (fullTick < 14) {
                  var tick= ((fullTick - 7) + this.partialTick) / 7.0;
                  var f= MH.cos((tick * PI / 2.0));
                  this.lowerLeftArm.arm.rotateAngleX += -f * 1.5;
              }
          }
        },
        animateScream: function (fullTick) {
          if (fullTick < 35) {
              var i;
              var tick= (fullTick + this.partialTick) / 35.0;
              var f= MH.sin((tick * PI / 2.0));
              this.abdomen.rotateAngleX += f * 0.3;
              this.chest.rotateAngleX += f * 0.4;
              this.neck.rotateAngleX += f * 0.2;
              this.head.rotateAngleX += f * 0.3;
              this.rightArm.arm.rotateAngleX += -f * 0.6;
              this.rightArm.arm.rotateAngleY += f * 0.4;
              this.rightArm.forearm.rotateAngleX += -f * 0.8;
              this.rightArm.hand.rotateAngleZ += -f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.rightArm.finger[i].rotateAngleZ += f * 0.3;
                  this.rightArm.foreFinger[i].rotateAngleZ += -f * 0.5;
              }
              this.leftArm.arm.rotateAngleX += -f * 0.6;
              this.leftArm.arm.rotateAngleY += -f * 0.4;
              this.leftArm.forearm.rotateAngleX += -f * 0.8;
              this.leftArm.hand.rotateAngleZ += f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.leftArm.finger[i].rotateAngleZ += -f * 0.3;
                  this.leftArm.foreFinger[i].rotateAngleZ += f * 0.5;
              }
              this.lowerRightArm.arm.rotateAngleX += -f * 0.4;
              this.lowerRightArm.arm.rotateAngleY += f * 0.2;
              this.lowerRightArm.forearm.rotateAngleX += -f * 0.8;
              this.lowerRightArm.hand.rotateAngleZ += -f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerRightArm.finger[i].rotateAngleZ += f * 0.3;
                  this.lowerRightArm.foreFinger[i].rotateAngleZ += -f * 0.5;
              }
              this.lowerLeftArm.arm.rotateAngleX += -f * 0.4;
              this.lowerLeftArm.arm.rotateAngleY += -f * 0.2;
              this.lowerLeftArm.forearm.rotateAngleX += -f * 0.8;
              this.lowerLeftArm.hand.rotateAngleZ += f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerLeftArm.finger[i].rotateAngleZ += -f * 0.3;
                  this.lowerLeftArm.foreFinger[i].rotateAngleZ += f * 0.5;
              }
          } else if (fullTick < 40) {
              var i;
              this.abdomen.rotateAngleX += 0.3;
              this.chest.rotateAngleX += 0.4;
              this.neck.rotateAngleX += 0.2;
              this.head.rotateAngleX += 0.3;
              this.rightArm.arm.rotateAngleX += -0.6;
              this.rightArm.arm.rotateAngleY += 0.4;
              this.rightArm.forearm.rotateAngleX += -0.8;
              this.rightArm.hand.rotateAngleZ += -0.4;
              for (i = 0; i < 3; ++i) {
                  this.rightArm.finger[i].rotateAngleZ += 0.3;
                  this.rightArm.foreFinger[i].rotateAngleZ += -0.5;
              }
              this.leftArm.arm.rotateAngleX += -0.6;
              this.leftArm.arm.rotateAngleY += -0.4;
              this.leftArm.forearm.rotateAngleX += -0.8;
              this.leftArm.hand.rotateAngleZ += 0.4;
              for (i = 0; i < 3; ++i) {
                  this.leftArm.finger[i].rotateAngleZ += -0.3;
                  this.leftArm.foreFinger[i].rotateAngleZ += 0.5;
              }
              this.lowerRightArm.arm.rotateAngleX += -0.4;
              this.lowerRightArm.arm.rotateAngleY += 0.2;
              this.lowerRightArm.forearm.rotateAngleX += -0.8;
              this.lowerRightArm.hand.rotateAngleZ += -0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerRightArm.finger[i].rotateAngleZ += 0.3;
                  this.lowerRightArm.foreFinger[i].rotateAngleZ += -0.5;
              }
              this.lowerLeftArm.arm.rotateAngleX += -0.4;
              this.lowerLeftArm.arm.rotateAngleY += -0.2;
              this.lowerLeftArm.forearm.rotateAngleX += -0.8;
              this.lowerLeftArm.hand.rotateAngleZ += 0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerLeftArm.finger[i].rotateAngleZ += -0.3;
                  this.lowerLeftArm.foreFinger[i].rotateAngleZ += 0.5;
              }
          } else if (fullTick < 44) {
              var i;
              var tick= ((fullTick - 40) + this.partialTick) / 4.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.abdomen.rotateAngleX += -f * 0.1 + 0.4;
              this.chest.rotateAngleX += f * 0.1 + 0.3;
              this.chest.rotateAngleZ += f1 * 0.5;
              this.neck.rotateAngleX += f * 0.2;
              this.neck.rotateAngleZ += f1 * 0.2;
              this.head.rotateAngleX += f * 1.2 - 0.8;
              this.head.rotateAngleZ += f1 * 0.4;
              this.mouth.rotateAngleX += f1 * 0.6;
              this.rightArm.arm.rotateAngleX += -f * 0.6;
              this.rightArm.arm.rotateAngleY += 0.4;
              this.rightArm.forearm.rotateAngleX += -f * 0.8;
              this.rightArm.hand.rotateAngleZ += -f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.rightArm.finger[i].rotateAngleZ += f * 0.3;
                  this.rightArm.foreFinger[i].rotateAngleZ += -f * 0.5;
              }
              this.leftArm.arm.rotateAngleX += -f * 0.6;
              this.leftArm.arm.rotateAngleY += -0.4;
              this.leftArm.forearm.rotateAngleX += -f * 0.8;
              this.leftArm.hand.rotateAngleZ += f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.leftArm.finger[i].rotateAngleZ += -f * 0.3;
                  this.leftArm.foreFinger[i].rotateAngleZ += f * 0.5;
              }
              this.lowerRightArm.arm.rotateAngleX += -f * 0.4;
              this.lowerRightArm.arm.rotateAngleY += -f * 0.1 + 0.3;
              this.lowerRightArm.forearm.rotateAngleX += -f * 0.8;
              this.lowerRightArm.hand.rotateAngleZ += -f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerRightArm.finger[i].rotateAngleZ += f * 0.3;
                  this.lowerRightArm.foreFinger[i].rotateAngleZ += -f * 0.5;
              }
              this.lowerLeftArm.arm.rotateAngleX += -f * 0.4;
              this.lowerLeftArm.arm.rotateAngleY += f * 0.1 - 0.3;
              this.lowerLeftArm.forearm.rotateAngleX += -f * 0.8;
              this.lowerLeftArm.hand.rotateAngleZ += f * 0.4;
              for (i = 0; i < 3; ++i) {
                  this.lowerLeftArm.finger[i].rotateAngleZ += -f * 0.3;
                  this.lowerLeftArm.foreFinger[i].rotateAngleZ += f * 0.5;
              }
              this.leg1.rotateAngleZ += f1 * 0.1;
              this.leg2.rotateAngleZ += -f1 * 0.1;
          } else if (fullTick < 155) {
              var tick= ((fullTick - 44) + this.partialTick) / 111.0;
              var f= MH.cos((tick * PI / 2.0));
              this.abdomen.rotateAngleX += 0.4;
              this.chest.rotateAngleX += 0.3;
              this.chest.rotateAngleZ += f * 1.0 - 0.5;
              this.neck.rotateAngleZ += f * 0.4 - 0.2;
              this.head.rotateAngleX += -0.8;
              this.head.rotateAngleZ += f * 0.8 - 0.4;
              this.mouth.rotateAngleX += 0.6;
              this.rightArm.arm.rotateAngleY += 0.4;
              this.leftArm.arm.rotateAngleY += -0.4;
              this.lowerRightArm.arm.rotateAngleY += 0.3;
              this.lowerLeftArm.arm.rotateAngleY += -0.3;
              this.leg1.rotateAngleZ += 0.1;
              this.leg2.rotateAngleZ += -0.1;
          } else if (fullTick < 160) {
              var tick= ((fullTick - 155) + this.partialTick) / 5.0;
              var f= MH.cos((tick * PI / 2.0));
              this.abdomen.rotateAngleX += f * 0.4;
              this.chest.rotateAngleX += f * 0.3;
              this.chest.rotateAngleZ += -f * 0.5;
              this.neck.rotateAngleZ += -f * 0.2;
              this.head.rotateAngleX += -f * 0.8;
              this.head.rotateAngleZ += -f * 0.4;
              this.mouth.rotateAngleX += f * 0.6;
              this.rightArm.arm.rotateAngleY += f * 0.4;
              this.leftArm.arm.rotateAngleY += -f * 0.4;
              this.lowerRightArm.arm.rotateAngleY += f * 0.3;
              this.lowerLeftArm.arm.rotateAngleY += -f * 0.3;
              this.leg1.rotateAngleZ += f * 0.1;
              this.leg2.rotateAngleZ += -f * 0.1;
          }
        },
        animateTeleSmash: function (fullTick) {
          if (fullTick < 18) {
              var tick= (fullTick + this.partialTick) / 18.0;
              var f= MH.sin((tick * PI / 2.0));
              this.chest.rotateAngleX += -f * 0.3;
              this.rightArm.arm.rotateAngleY += f * 0.2;
              this.rightArm.arm.rotateAngleZ += f * 0.8;
              this.rightArm.hand.rotateAngleY += f * 1.7;
              this.leftArm.arm.rotateAngleY += -f * 0.2;
              this.leftArm.arm.rotateAngleZ += -f * 0.8;
              this.leftArm.hand.rotateAngleY += -f * 1.7;
              this.lowerRightArm.arm.rotateAngleY += f * 0.2;
              this.lowerRightArm.arm.rotateAngleZ += f * 0.6;
              this.lowerRightArm.hand.rotateAngleY += f * 1.7;
              this.lowerLeftArm.arm.rotateAngleY += -f * 0.2;
              this.lowerLeftArm.arm.rotateAngleZ += -f * 0.6;
              this.lowerLeftArm.hand.rotateAngleY += -f * 1.7;
          } else if (fullTick < 20) {
              var tick= ((fullTick - 18) + this.partialTick) / 2.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.chest.rotateAngleX += -f * 0.3;
              this.rightArm.arm.rotateAngleX += -f1 * 0.8;
              this.rightArm.arm.rotateAngleY += 0.2;
              this.rightArm.arm.rotateAngleZ += 0.8;
              this.rightArm.hand.rotateAngleY += 1.7;
              this.leftArm.arm.rotateAngleX += -f1 * 0.8;
              this.leftArm.arm.rotateAngleY += -0.2;
              this.leftArm.arm.rotateAngleZ += -0.8;
              this.leftArm.hand.rotateAngleY += -1.7;
              this.lowerRightArm.arm.rotateAngleX += -f1 * 0.9;
              this.lowerRightArm.arm.rotateAngleY += 0.2;
              this.lowerRightArm.arm.rotateAngleZ += 0.6;
              this.lowerRightArm.hand.rotateAngleY += 1.7;
              this.lowerLeftArm.arm.rotateAngleX += -f1 * 0.9;
              this.lowerLeftArm.arm.rotateAngleY += -0.2;
              this.lowerLeftArm.arm.rotateAngleZ += -0.6;
              this.lowerLeftArm.hand.rotateAngleY += -1.7;
          } else if (fullTick < 24) {
              this.rightArm.arm.rotateAngleX += -0.8;
              this.rightArm.arm.rotateAngleY += 0.2;
              this.rightArm.arm.rotateAngleZ += 0.8;
              this.rightArm.hand.rotateAngleY += 1.7;
              this.leftArm.arm.rotateAngleX += -0.8;
              this.leftArm.arm.rotateAngleY += -0.2;
              this.leftArm.arm.rotateAngleZ += -0.8;
              this.leftArm.hand.rotateAngleY += -1.7;
              this.lowerRightArm.arm.rotateAngleX += -0.9;
              this.lowerRightArm.arm.rotateAngleY += 0.2;
              this.lowerRightArm.arm.rotateAngleZ += 0.6;
              this.lowerRightArm.hand.rotateAngleY += 1.7;
              this.lowerLeftArm.arm.rotateAngleX += -0.9;
              this.lowerLeftArm.arm.rotateAngleY += -0.2;
              this.lowerLeftArm.arm.rotateAngleZ += -0.6;
              this.lowerLeftArm.hand.rotateAngleY += -1.7;
          } else if (fullTick < 30) {
              var tick= ((fullTick - 24) + this.partialTick) / 6.0;
              var f= MH.cos((tick * PI / 2.0));
              this.rightArm.arm.rotateAngleX += -f * 0.8;
              this.rightArm.arm.rotateAngleY += f * 0.2;
              this.rightArm.arm.rotateAngleZ += f * 0.8;
              this.rightArm.hand.rotateAngleY += f * 1.7;
              this.leftArm.arm.rotateAngleX += -f * 0.8;
              this.leftArm.arm.rotateAngleY += -f * 0.2;
              this.leftArm.arm.rotateAngleZ += -f * 0.8;
              this.leftArm.hand.rotateAngleY += -f * 1.7;
              this.lowerRightArm.arm.rotateAngleX += -f * 0.9;
              this.lowerRightArm.arm.rotateAngleY += f * 0.2;
              this.lowerRightArm.arm.rotateAngleZ += f * 0.6;
              this.lowerRightArm.hand.rotateAngleY += f * 1.7;
              this.lowerLeftArm.arm.rotateAngleX += -f * 0.9;
              this.lowerLeftArm.arm.rotateAngleY += -f * 0.2;
              this.lowerLeftArm.arm.rotateAngleZ += -f * 0.6;
              this.lowerLeftArm.hand.rotateAngleY += -f * 1.7;
          }
        },
        animateDeath: function (deathTick) {
          if (deathTick < 80) {
              var tick= (deathTick + this.partialTick) / 80.0;
              var f= MH.sin((tick * PI / 2.0));
              this.head.rotateAngleX += f * 0.4;
              this.neck.rotateAngleX += f * 0.3;
              this.pelvis.rotationPointY += -f * 12.0;
              this.rightArm.arm.rotateAngleX += -f * 0.4;
              this.rightArm.arm.rotateAngleY += f * 0.4;
              this.rightArm.arm.rotateAngleZ += f * 0.6;
              this.rightArm.forearm.rotateAngleX += -f * 1.2;
              this.leftArm.arm.rotateAngleX += -f * 0.4;
              this.leftArm.arm.rotateAngleY += -f * 0.2;
              this.leftArm.arm.rotateAngleZ += -f * 0.6;
              this.leftArm.forearm.rotateAngleX += -f * 1.2;
              this.lowerRightArm.arm.rotateAngleX += -f * 0.4;
              this.lowerRightArm.arm.rotateAngleY += f * 0.4;
              this.lowerRightArm.arm.rotateAngleZ += f * 0.6;
              this.lowerRightArm.forearm.rotateAngleX += -f * 1.2;
              this.lowerLeftArm.arm.rotateAngleX += -f * 0.4;
              this.lowerLeftArm.arm.rotateAngleY += -f * 0.2;
              this.lowerLeftArm.arm.rotateAngleZ += -f * 0.6;
              this.lowerLeftArm.forearm.rotateAngleX += -f * 1.2;
              this.leg1.rotateAngleX += -f * 0.9;
              this.leg1.rotateAngleY += f * 0.3;
              this.leg2.rotateAngleX += -f * 0.9;
              this.leg2.rotateAngleY += -f * 0.3;
              this.foreleg1.rotateAngleX += f * 1.6;
              this.foreleg2.rotateAngleX += f * 1.6;
          } else if (deathTick < 84) {
              var tick= ((deathTick - 80) + this.partialTick) / 4.0;
              var f= MH.cos((tick * PI / 2.0));
              var f1= MH.sin((tick * PI / 2.0));
              this.head.rotateAngleX += f * 0.4;
              this.mouth.rotateAngleX += f1 * 0.6;
              this.neck.rotateAngleX += f * 0.4 - 0.1;
              this.chest.rotateAngleX += -f1 * 0.8;
              this.abdomen.rotateAngleX += -f1 * 0.2;
              this.pelvis.rotationPointY += -12.0;
              this.rightArm.arm.rotateAngleX += -f * 0.4;
              this.rightArm.arm.rotateAngleY += -f * 1.4 + 1.8;
              this.rightArm.arm.rotateAngleZ += f * 0.6;
              this.rightArm.forearm.rotateAngleX += -f * 1.2;
              this.leftArm.arm.rotateAngleX += -f * 0.4;
              this.leftArm.arm.rotateAngleY += f * 1.6 - 1.8;
              this.leftArm.arm.rotateAngleZ += -f * 0.6;
              this.leftArm.forearm.rotateAngleX += -f * 1.2;
              this.lowerRightArm.arm.rotateAngleX += -f * 0.5 + 0.1;
              this.lowerRightArm.arm.rotateAngleY += -f * 1.1 + 1.5;
              this.lowerRightArm.arm.rotateAngleZ += f * 0.6;
              this.lowerRightArm.forearm.rotateAngleX += -f * 1.2;
              this.lowerLeftArm.arm.rotateAngleX += -f * 0.5 + 0.1;
              this.lowerLeftArm.arm.rotateAngleY += f * 1.1 - 1.5;
              this.lowerLeftArm.arm.rotateAngleZ += -f * 0.6;
              this.lowerLeftArm.forearm.rotateAngleX += -f * 1.2;
              this.leg1.rotateAngleX += -f * 1.7 + 0.8;
              this.leg1.rotateAngleY += f * 0.3;
              this.leg1.rotateAngleZ += f1 * 0.2;
              this.leg2.rotateAngleX += -f * 1.7 + 0.8;
              this.leg2.rotateAngleY += -f * 0.3;
              this.leg2.rotateAngleZ += -f1 * 0.2;
              this.foreleg1.rotateAngleX += f * 1.6;
              this.foreleg2.rotateAngleX += f * 1.6;
          } else {
              this.mouth.rotateAngleX += 0.6;
              this.neck.rotateAngleX += -0.1;
              this.chest.rotateAngleX += -0.8;
              this.abdomen.rotateAngleX += -0.2;
              this.pelvis.rotationPointY += -12.0;
              this.rightArm.arm.rotateAngleY += 1.8;
              this.leftArm.arm.rotateAngleY += -1.8;
              this.lowerRightArm.arm.rotateAngleX += 0.1;
              this.lowerRightArm.arm.rotateAngleY += 1.5;
              this.lowerLeftArm.arm.rotateAngleX += 0.1;
              this.lowerLeftArm.arm.rotateAngleY += -1.5;
              this.leg1.rotateAngleX += 0.8;
              this.leg1.rotateAngleZ += 0.2;
              this.leg2.rotateAngleX += 0.8;
              this.leg2.rotateAngleZ += -0.2;
          }
        },
        getArmFromID: function (armID) {
          return armID === 1 ? this.rightArm : (armID === 2 ? this.leftArm : (armID === 3 ? this.lowerRightArm : this.lowerLeftArm));
        },
        postRenderArm: function (scale, armID) {
          this.pelvis.postRender(scale);
          this.abdomen.postRender(scale);
          this.chest.postRender(scale);
          this.getArmFromID(armID).postRender(scale);
        },
        setLivingAnimations: function (entitylivingbaseIn, limbSwing, limbSwingAmount, partialTickTime) {
          this.partialTick = partialTickTime;
        },
        resetAngles: endermanResetAngles,   // static
      }
    });
    // ------------------------------------------------------------ MutantEndermanModel.java: static class Arm
    function endermanResetAngles(model) {                      // MutantEndermanModel.resetAngles (static)
      model.rotateAngleX = 0.0;
      model.rotateAngleY = 0.0;
      model.rotateAngleZ = 0.0;
    }
    function Arm(model, connect, right) {
      var i;
      this.right = right;
      this.finger = new Array(3);
      this.foreFinger = new Array(3);
      this.arm = SMR(model, 92, 0);
      this.arm.mirror = !this.right;
      this.arm.addBox(-1.5, 0.0, -1.5, 3, 22, 3, 0.1);
      this.arm.setRotationPoint(this.right ? -4.0 : 4.0, -14.0, 0.0);
      connect.addChild(this.arm);
      this.forearm = MR(model, 104, 0);
      this.forearm.mirror = !this.right;
      this.forearm.addBox(-1.5, 0.0, -1.5, 3, 18, 3);
      this.forearm.setRotationPoint(0.0, 21.0, 1.0);
      this.arm.addChild(this.forearm);
      this.hand = MR(model);
      this.hand.setRotationPoint(0.0, 17.5, 0.0);
      this.forearm.addChild(this.hand);
      var fingerScale = 0.6;
      for (i = 0; i < this.finger.length; ++i) {
        this.finger[i] = MR(model, 76, 0);
        this.finger[i].mirror = !this.right;
        this.finger[i].addBox(-0.5, 0.0, -0.5, 1, i === 1 ? 6 : 5, 1, fingerScale);
      }
      this.finger[0].setRotationPoint(this.right ? -0.5 : 0.5, 0.0, -1.0);
      this.finger[1].setRotationPoint(this.right ? -0.5 : 0.5, 0.0, 0.0);
      this.finger[2].setRotationPoint(this.right ? -0.5 : 0.5, 0.0, 1.0);
      for (i = 0; i < this.foreFinger.length; ++i) {
        this.foreFinger[i] = MR(model, 76, 0);
        this.foreFinger[i].mirror = !this.right;
        this.foreFinger[i].addBox(-0.5, 0.0, -0.5, 1, i === 1 ? 6 : 5, 1, fingerScale - 0.01);
        this.foreFinger[i].setRotationPoint(0.0, 0.5 + (i === 1 ? 6 : 5), 0.0);
      }
      for (i = 0; i < this.finger.length; ++i) {
        this.hand.addChild(this.finger[i]);
        this.finger[i].addChild(this.foreFinger[i]);
      }
      this.thumb = MR(model, 76, 0);
      this.thumb.mirror = this.right;
      this.thumb.addBox(-0.5, 0.0, -0.5, 1, 5, 1, fingerScale);
      this.thumb.setRotationPoint(this.right ? 0.5 : -0.5, 0.0, -0.5);
      this.hand.addChild(this.thumb);
    }
    Arm.prototype.setAngles = function () {
      endermanResetAngles(this.arm);
      endermanResetAngles(this.forearm);
      endermanResetAngles(this.hand);
      for (var i = 0; i < this.finger.length; ++i) {
        endermanResetAngles(this.finger[i]);
        endermanResetAngles(this.foreFinger[i]);
      }
      endermanResetAngles(this.thumb);
      if (this.right) {
        this.arm.rotateAngleX = -0.5235988;
        this.arm.rotateAngleZ = 0.5235988;
        this.forearm.rotateAngleX = -0.62831855;
        this.hand.rotateAngleY = -0.3926991;
        this.finger[0].rotateAngleX = -0.2617994;
        this.finger[1].rotateAngleZ = 0.17453294;
        this.finger[2].rotateAngleX = 0.2617994;
        this.foreFinger[0].rotateAngleZ = -0.2617994;
        this.foreFinger[1].rotateAngleZ = -0.3926991;
        this.foreFinger[2].rotateAngleZ = -0.2617994;
        this.thumb.rotateAngleX = -0.62831855;
        this.thumb.rotateAngleZ = -0.3926991;
      } else {
        this.arm.rotateAngleX = -0.5235988;
        this.arm.rotateAngleZ = -0.5235988;
        this.forearm.rotateAngleX = -0.62831855;
        this.hand.rotateAngleY = 0.3926991;
        this.finger[0].rotateAngleX = -0.2617994;
        this.finger[1].rotateAngleZ = -0.17453294;
        this.finger[2].rotateAngleX = 0.2617994;
        this.foreFinger[0].rotateAngleZ = 0.2617994;
        this.foreFinger[1].rotateAngleZ = 0.3926991;
        this.foreFinger[2].rotateAngleZ = 0.2617994;
        this.thumb.rotateAngleX = -0.62831855;
        this.thumb.rotateAngleZ = 0.3926991;
      }
    };
    Arm.prototype.postRender = function (scale) {
      this.arm.postRender(scale);
      this.forearm.postRender(scale);
      this.hand.postRender(scale);
    };

    // ============================================================ MutantSnowGolemModel.java
    MODELS.MutantSnowGolemModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantSnowGolemModel",
      fields: function (s) { s.partialTick = 0.0; },
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 64;
          this.pelvis = MR(this);
          this.pelvis.setRotationPoint(0.0, 13.5, 5.0);
          this.abdomen = MR(this, 0, 32);
          this.abdomen.addBox(-5.0, -8.0, -4.0, 10, 8, 8);
          this.pelvis.addChild(this.abdomen);
          this.chest = MR(this, 24, 36);
          this.chest.addBox(-8.0, -12.0, -6.0, 16, 12, 12);
          this.chest.setRotationPoint(0.0, -6.0, 0.0);
          this.head = JMR(this, 0, 0);
          this.head.setTextureSize(64, 32);
          this.head.addBox(-4.0, -8.0, -4.0, 8, 8, 8, 0.5);
          this.head.setRotationPoint(0.0, -12.0, -2.0);
          this.chest.addChild(this.head);
          this.headCore = MR(this, 64, 0);
          this.headCore.addBox(-4.0, -8.0, -4.0, 8, 8, 8);
          this.headCore.setTextureOffset(80, 46).addBox(-4.0, -8.0, -4.0, 8, 8, 8, -0.5);
          this.headCore.setRotationPoint(0.0, 0.0, 0.0);
          this.head.addChild(this.headCore);
          this.abdomen.addChild(this.chest);
          this.arm1 = JMR(this, 68, 16);
          this.arm1.addBox(-2.5, 0.0, -2.5, 5, 10, 5);
          this.arm1.setRotationPoint(-9.0, -11.0, 0.0);
          this.chest.addChild(this.arm1);
          this.forearm1 = JMR(this, 96, 0);
          this.forearm1.addBox(-3.0, 0.0, -3.0, 6, 12, 6);
          this.forearm1.setRotationPoint(0.0, 10.0, 0.0);
          this.arm1.addChild(this.forearm1);
          this.arm2 = JMR(this, 68, 16);
          this.arm2.mirror = 1;
          this.arm2.addBox(-2.5, 0.0, -2.5, 5, 10, 5);
          this.arm2.setRotationPoint(9.0, -11.0, 0.0);
          this.chest.addChild(this.arm2);
          this.forearm2 = JMR(this, 96, 0);
          this.forearm2.mirror = 1;
          this.forearm2.addBox(-3.0, 0.0, -3.0, 6, 12, 6);
          this.forearm2.setRotationPoint(0.0, 10.0, 0.0);
          this.arm2.addChild(this.forearm2);
          this.leg1 = JMR(this, 88, 18);
          this.leg1.addBox(-3.0, 0.0, -3.0, 6, 8, 6);
          this.leg1.setRotationPoint(-4.0, -1.0, -3.0);
          this.pelvis.addChild(this.leg1);
          this.foreleg1 = JMR(this, 88, 32);
          this.foreleg1.addBox(-3.0, 0.0, -3.0, 6, 8, 6);
          this.foreleg1.setRotationPoint(-1.0, 6.0, -0.0);
          this.leg1.addChild(this.foreleg1);
          this.leg2 = JMR(this, 88, 18);
          this.leg2.mirror = 1;
          this.leg2.addBox(-3.0, 0.0, -3.0, 6, 8, 6);
          this.leg2.setRotationPoint(4.0, -1.0, -3.0);
          this.pelvis.addChild(this.leg2);
          this.foreleg2 = JMR(this, 88, 32);
          this.foreleg2.mirror = 1;
          this.foreleg2.addBox(-3.0, 0.0, -3.0, 6, 8, 6);
          this.foreleg2.setRotationPoint(1.0, 6.0, -0.0);
          this.leg2.addChild(this.foreleg2);
      },
      methods: {
        render: function (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setAngles();
          this.animate(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
          this.pelvis.render(scale);
        },
        setAngles: function () {
          this.pelvis.rotationPointY = 13.5;
          this.abdomen.rotateAngleX = 0.1308997;
          this.chest.rotateAngleX = 0.1308997;
          this.chest.rotateAngleY = 0.0;
          this.head.rotateAngleX = -0.2617994;
          this.head.getModel().rotateAngleX = 0.0;
          this.head.getModel().rotateAngleY = 0.0;
          this.arm1.rotateAngleX = -0.31415927;
          this.arm1.rotateAngleZ = 0.0;
          this.arm1.getModel().rotateAngleX = 0.0;
          this.arm1.getModel().rotateAngleY = 0.5235988;
          this.arm1.getModel().rotateAngleZ = 0.5235988;
          this.forearm1.rotateAngleY = -0.5235988;
          this.forearm1.rotateAngleZ = -0.2617994;
          this.forearm1.getModel().rotateAngleX = -0.5235988;
          this.arm2.rotateAngleX = -0.31415927;
          this.arm2.rotateAngleZ = 0.0;
          this.arm2.getModel().rotateAngleX = 0.0;
          this.arm2.getModel().rotateAngleY = -0.5235988;
          this.arm2.getModel().rotateAngleZ = -0.5235988;
          this.forearm2.rotateAngleY = 0.5235988;
          this.forearm2.rotateAngleZ = 0.2617994;
          this.forearm2.getModel().rotateAngleX = -0.5235988;
          this.leg1.rotateAngleX = -0.62831855;
          this.leg1.getModel().rotateAngleZ = 0.5235988;
          this.foreleg1.rotateAngleZ = -0.5235988;
          this.foreleg1.getModel().rotateAngleX = 0.69813174;
          this.leg2.rotateAngleX = -0.62831855;
          this.leg2.getModel().rotateAngleZ = -0.5235988;
          this.foreleg2.rotateAngleZ = 0.5235988;
          this.foreleg2.getModel().rotateAngleX = 0.69813174;
        },
        animate: function (golem, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          var temp= 0.5;
          var walkAnim= MH.sin((limbSwing * 0.45)) * limbSwingAmount;
          var walkAnim1= (MH.cos(((limbSwing - temp) * 0.45)) + temp) * limbSwingAmount;
          var walkAnim2= (MH.cos(((limbSwing - temp + PI * 2) * 0.45)) + temp) * limbSwingAmount;
          var breatheAnim= MH.sin((ageInTicks * 0.11));
          var faceYaw= netHeadYaw * PI / 180.0;
          var facePitch= headPitch * PI / 180.0;
          if (golem.isThrowing()) {
              this.animateThrow(golem.getThrowingTick());
              var scale1= 1.0 - MH.clampF((golem.getThrowingTick() / 4.0), 0.0, 1.0);
              walkAnim *= scale1;
          }
          this.head.getModel().rotateAngleX -= breatheAnim * 0.01;
          this.chest.rotateAngleX -= breatheAnim * 0.01;
          this.arm1.rotateAngleZ += breatheAnim * 0.03;
          this.arm2.rotateAngleZ -= breatheAnim * 0.03;
          this.head.getModel().rotateAngleX += facePitch;
          this.head.getModel().rotateAngleY += faceYaw;
          this.pelvis.rotationPointY += Math.abs(walkAnim) * 1.5;
          this.abdomen.rotateAngleX += limbSwingAmount * 0.2;
          this.chest.rotateAngleY -= walkAnim * 0.1;
          this.head.rotateAngleX -= limbSwingAmount * 0.2;
          this.arm1.rotateAngleX -= walkAnim * 0.6;
          this.arm2.rotateAngleX += walkAnim * 0.6;
          this.forearm1.getModel().rotateAngleX -= walkAnim * 0.2;
          this.forearm2.getModel().rotateAngleX += walkAnim * 0.2;
          this.leg1.rotateAngleX += walkAnim1 * 1.1;
          this.leg2.rotateAngleX += walkAnim2 * 1.1;
          this.foreleg1.getModel().rotateAngleX += walkAnim * 0.2;
          this.foreleg2.getModel().rotateAngleX -= walkAnim * 0.2;
        },
        animateThrow: function (fullTick) {
          if (fullTick < 7) {
              var tick= (fullTick + this.partialTick) / 7.0;
              var f= MH.sin((tick * PI / 2.0));
              this.abdomen.rotateAngleX += -f * 0.2;
              this.chest.rotateAngleX += -f * 0.4;
              this.arm1.rotateAngleX += -f * 1.6;
              this.arm1.rotateAngleZ += f * 0.8;
              this.arm2.rotateAngleX += -f * 1.6;
              this.arm2.rotateAngleZ += -f * 0.8;
          } else if (fullTick < 10) {
              var tick= ((fullTick - 7) + this.partialTick) / 3.0;
              var f= MH.cos((tick * PI / 2.0));
              this.abdomen.rotateAngleX += -f * 0.4 + 0.2;
              this.chest.rotateAngleX += -f * 0.6 + 0.2;
              this.arm1.rotateAngleX += -f * 0.8 - 0.8;
              this.arm1.rotateAngleZ += 0.8;
              this.arm2.rotateAngleX += -f * 0.8 - 0.8;
              this.arm2.rotateAngleZ += -0.8;
          } else if (fullTick < 14) {
              this.abdomen.rotateAngleX += 0.2;
              this.chest.rotateAngleX += 0.2;
              this.arm1.rotateAngleX += -0.8;
              this.arm1.rotateAngleZ += 0.8;
              this.arm2.rotateAngleX += -0.8;
              this.arm2.rotateAngleZ += -0.8;
          } else if (fullTick < 20) {
              var tick= ((fullTick - 14) + this.partialTick) / 6.0;
              var f= MH.cos((tick * PI / 2.0));
              this.abdomen.rotateAngleX += f * 0.2;
              this.chest.rotateAngleX += f * 0.2;
              this.arm1.rotateAngleX += -f * 0.8;
              this.arm1.rotateAngleZ += f * 0.8;
              this.arm2.rotateAngleX += -f * 0.8;
              this.arm2.rotateAngleZ += -f * 0.8;
          }
        },
        postRenderArm: function (scale) {
          this.pelvis.postRender(scale);
          this.abdomen.postRender(scale);
          this.chest.postRender(scale);
          this.arm1.postRender(scale);
          this.arm1.getModel().postRender(scale);
          this.forearm1.postRender(scale);
          this.forearm1.getModel().postRender(scale);
        },
        setLivingAnimations: function (entitylivingbaseIn, limbSwing, limbSwingAmount, partialTickTime) {
          this.partialTick = partialTickTime;
        },
      }
    });

    // ============================================================ SpiderPigModel.java
    function spiderPigResetAngles() {                       // public static void resetAngles(ModelRenderer ... boxes)
      for (var i = 0; i < arguments.length; ++i) {
        var box = arguments[i];
        box.rotateAngleX = 0.0;
        box.rotateAngleY = 0.0;
        box.rotateAngleZ = 0.0;
      }
    }
    MODELS.SpiderPigModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.SpiderPigModel",
      ctor: function () {
          this.textureWidth = 128;
          this.textureHeight = 64;
          this.base = MR(this);
          this.base.setRotationPoint(0.0, 14.5, -2.0);
          this.body2 = MR(this, 32, 0);
          this.body2.addBox(-3.0, -3.0, 0.0, 6, 6, 10);
          this.body2.setTextureOffset(44, 16).addBox(-5.0, -5.0, -4.0, 10, 8, 12, -0.6);
          this.base.addChild(this.body2);
          this.body1 = JMR(this, 64, 0);
          this.body1.addBox(-3.5, -3.5, -9.0, 7, 7, 9);
          this.body1.setRotationPoint(0.0, -1.0, 1.5);
          this.body2.addChild(this.body1);
          this.butt = MR(this, 0, 16);
          this.butt.addBox(-5.0, -4.5, 0.0, 10, 9, 12);
          this.butt.setRotationPoint(0.0, 0.0, 7.0);
          this.body2.addChild(this.butt);
          this.head = JMR(this, 0, 0);
          this.head.addBox(-4.0, -4.0, -8.0, 8, 8, 8);
          this.head.setRotationPoint(0.0, 0.0, -8.0);
          this.body1.addChild(this.head);
          this.snout = MR(this, 24, 0);
          this.snout.addBox(-2.0, 0.0, -9.0, 4, 3, 1);
          this.head.addChild(this.snout);
          this.frontLeg1 = JMR(this, 0, 37);
          this.frontLeg1.addBox(-1.0, 0.0, -1.0, 2, 12, 2);
          this.frontLeg1.setRotationPoint(-3.5, 0.0, -5.0);
          this.body1.addChild(this.frontLeg1);
          this.frontLegF1 = JMR(this, 8, 37);
          this.frontLegF1.addBox(-1.0, 0.0, -1.0, 2, 16, 2);
          this.frontLegF1.setRotationPoint(-0.0, 12.0, -0.1);
          this.frontLeg1.addChild(this.frontLegF1);
          this.frontLeg2 = JMR(this, 0, 37);
          this.frontLeg2.mirror = 1;
          this.frontLeg2.addBox(-1.0, 0.0, -1.0, 2, 12, 2);
          this.frontLeg2.setRotationPoint(3.5, 0.0, -5.0);
          this.body1.addChild(this.frontLeg2);
          this.frontLegF2 = JMR(this, 8, 37);
          this.frontLegF2.mirror = 1;
          this.frontLegF2.addBox(-1.0, 0.0, -1.0, 2, 16, 2);
          this.frontLegF2.setRotationPoint(0.0, 12.0, 0.1);
          this.frontLeg2.addChild(this.frontLegF2);
          this.middleLeg1 = JMR(this, 0, 37);
          this.middleLeg1.addBox(-1.0, 0.0, -1.0, 2, 12, 2);
          this.middleLeg1.setRotationPoint(-3.5, 0.0, -3.0);
          this.body1.addChild(this.middleLeg1);
          this.middleLegF1 = JMR(this, 8, 37);
          this.middleLegF1.addBox(-1.0, 0.0, -1.0, 2, 16, 2);
          this.middleLegF1.setRotationPoint(0.0, 12.0, -0.1);
          this.middleLeg1.addChild(this.middleLegF1);
          this.middleLeg2 = JMR(this, 0, 37);
          this.middleLeg2.mirror = 1;
          this.middleLeg2.addBox(-1.0, 0.0, -1.0, 2, 12, 2);
          this.middleLeg2.setRotationPoint(3.5, 0.0, -3.0);
          this.body1.addChild(this.middleLeg2);
          this.middleLegF2 = JMR(this, 8, 37);
          this.middleLegF2.mirror = 1;
          this.middleLegF2.addBox(-1.0, 0.0, -1.0, 2, 16, 2);
          this.middleLegF2.setRotationPoint(0.0, 12.0, 0.1);
          this.middleLeg2.addChild(this.middleLegF2);
          this.backLeg1 = JMR(this, 16, 37);
          this.backLeg1.addBox(-2.0, 0.0, -2.0, 4, 4, 4);
          this.backLeg1.setRotationPoint(-2.5, 2.0, 7.0);
          this.body2.addChild(this.backLeg1);
          this.backLegF1 = JMR(this, 16, 45);
          this.backLegF1.addBox(-2.0, 0.0, -2.0, 4, 4, 4, 0.2);
          this.backLegF1.setRotationPoint(0.0, 3.0, 0.0);
          this.backLeg1.addChild(this.backLegF1);
          this.backLeg2 = JMR(this, 32, 37);
          this.backLeg2.mirror = 1;
          this.backLeg2.addBox(-2.0, 0.0, -2.0, 4, 4, 4);
          this.backLeg2.setRotationPoint(2.5, 2.0, 7.0);
          this.body2.addChild(this.backLeg2);
          this.backLegF2 = JMR(this, 16, 45);
          this.backLegF2.mirror = 1;
          this.backLegF2.addBox(-2.0, 0.0, -2.0, 4, 4, 4, 0.2);
          this.backLegF2.setRotationPoint(0.0, 3.0, 0.0);
          this.backLeg2.addChild(this.backLegF2);
      },
      methods: {
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setAngles();
          this.animate(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
          this.base.render(scale);
        },
        setAngles: function () {
          spiderPigResetAngles(this.head, this.head.getModel(), this.body1, this.body2, this.butt);
          spiderPigResetAngles(this.frontLeg1, this.frontLeg1.getModel(), this.frontLegF1, this.frontLegF1.getModel(), this.frontLeg2, this.frontLeg2.getModel(), this.frontLegF2, this.frontLegF2.getModel());
          spiderPigResetAngles(this.middleLeg1, this.middleLeg1.getModel(), this.middleLegF1, this.middleLegF1.getModel(), this.middleLeg2, this.middleLeg2.getModel(), this.middleLegF2, this.middleLegF2.getModel());
          spiderPigResetAngles(this.backLeg1, this.backLeg1.getModel(), this.backLegF1, this.backLegF1.getModel(), this.backLeg2, this.backLeg2.getModel(), this.backLegF2, this.backLegF2.getModel());
          this.body1.rotateAngleX += 0.3926991;
          this.body2.rotateAngleX += -0.05235988;
          this.butt.rotateAngleX += 0.5711987;
          this.head.rotateAngleX += -0.3926991;
          this.frontLeg1.rotateAngleX += -(this.body1.rotateAngleX + this.body2.rotateAngleX);
          this.frontLeg1.rotateAngleY += -1.0471976;
          this.frontLeg1.getModel().rotateAngleZ += 2.0943952;
          this.frontLegF1.rotateAngleZ += -1.6534699;
          this.frontLeg2.rotateAngleX += -(this.body1.rotateAngleX + this.body2.rotateAngleX);
          this.frontLeg2.rotateAngleY += 1.0;
          this.frontLeg2.getModel().rotateAngleZ += -2.0943952;
          this.frontLegF2.rotateAngleZ += 1.6534699;
          this.middleLeg1.rotateAngleX += -(this.body1.rotateAngleX + this.body2.rotateAngleX);
          this.middleLeg1.rotateAngleY += -0.31415927;
          this.middleLeg1.getModel().rotateAngleZ += 2.0399954;
          this.middleLegF1.rotateAngleZ += -1.6534699;
          this.middleLeg2.rotateAngleX += -(this.body1.rotateAngleX + this.body2.rotateAngleX);
          this.middleLeg2.rotateAngleY += 0.31415927;
          this.middleLeg2.getModel().rotateAngleZ += -2.0399954;
          this.middleLegF2.rotateAngleZ += 1.6534699;
          this.backLeg1.rotateAngleX += -0.3926991;
          this.backLeg1.getModel().rotateAngleZ += 0.3926991;
          this.backLegF1.rotateAngleZ += -0.3926991;
          this.backLegF1.getModel().rotateAngleX += 0.5711987;
          this.backLeg2.rotateAngleX += -0.3926991;
          this.backLeg2.getModel().rotateAngleZ += -0.3926991;
          this.backLegF2.rotateAngleZ += 0.3926991;
          this.backLegF2.getModel().rotateAngleX += 0.5711987;
        },
        animate: function (entity, f, f1, f2, f3, f4, f5) {
          var moveAnim= MH.sin((f * 0.9)) * f1;
          var moveAnim1= MH.sin((f * 0.9 + 0.3)) * f1;
          var moveAnim1d= MH.sin((f * 0.9 + 0.3 + 0.5)) * f1;
          var moveAnim2= MH.sin((f * 0.9 + 0.9)) * f1;
          var moveAnim2d= MH.sin((f * 0.9 + 0.9 + 0.5)) * f1;
          var moveAnim3= MH.sin((f * 0.9 - 0.3)) * f1;
          var moveAnim3d= MH.sin((f * 0.9 - 0.3 + 0.5)) * f1;
          var moveAnim4= MH.sin((f * 0.9 - 0.9)) * f1;
          var moveAnim4d= MH.sin((f * 0.9 - 0.9 + 0.5)) * f1;
          var breatheAnim= MH.sin((f2 * 0.2));
          var faceYaw= f3 * PI / 180.0;
          var facePitch= f4 * PI / 180.0;
          this.head.rotateAngleX += breatheAnim * 0.02;
          this.body1.rotateAngleX += breatheAnim * 0.005;
          this.butt.rotateAngleX += -breatheAnim * 0.015;
          this.head.getModel().rotateAngleX += facePitch;
          this.head.getModel().rotateAngleY += faceYaw;
          this.frontLeg1.getModel().rotateAngleZ += -moveAnim1 * PI / 6.0;
          this.frontLeg1.getModel().rotateAngleX += -0.3926991 * f1;
          this.frontLegF1.rotateAngleZ += moveAnim1d * PI / 6.0 + 0.2617994 * f1;
          this.frontLeg2.getModel().rotateAngleZ += moveAnim2 * PI / 6.0;
          this.frontLeg2.getModel().rotateAngleX += -0.3926991 * f1;
          this.frontLegF2.rotateAngleZ += -(moveAnim2d * PI / 6.0 + 0.2617994 * f1);
          this.middleLeg1.getModel().rotateAngleZ += -moveAnim3 * PI / 6.0;
          this.middleLeg1.getModel().rotateAngleX += -0.8975979 * f1;
          this.middleLegF1.rotateAngleZ += moveAnim3d * PI / 6.0 + 0.3926991 * f1;
          this.middleLeg2.getModel().rotateAngleZ += moveAnim4 * PI / 6.0;
          this.middleLeg2.getModel().rotateAngleX += -0.8975979 * f1;
          this.middleLegF2.rotateAngleZ += -(moveAnim4d * PI / 6.0 + 0.3926991 * f1);
          this.backLeg1.rotateAngleX += -moveAnim4 * PI / 5.0 + 0.2617994 * f1;
          this.backLeg2.rotateAngleX += -moveAnim1 * PI / 5.0 + 0.2617994 * f1;
          this.body2.rotateAngleX += -moveAnim * PI / 20.0;
          this.head.rotateAngleX += moveAnim * PI / 20.0;
        },
        resetAngles: spiderPigResetAngles,   // static
      }
    });

    // ============================================================ CreeperMinionModel.java
    MODELS.CreeperMinionModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.CreeperMinionModel", extend: DR9,
      // CreeperMinionModel() { this(0.0f); }  /  CreeperMinionModel(float scale) { super(scale); }
      init: function (m, args) { G8M(m, args.length > 0 ? args[0] : 0.0); },
      methods: {
        // ModelCreeper.render as written in Java. Restated here only because TeaVM compiled the engine's ModelCreeper.render
        // with a direct call to ModelCreeper.setRotationAngles, which would skip the override below.
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entityIn);
          this.head.render(scale);
          this.body.render(scale);
          this.leg1.render(scale);
          this.leg2.render(scale);
          this.leg3.render(scale);
          this.leg4.render(scale);
        },
        setRotationAngles: function (limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn) {
          sCreeperModel.setRotationAngles.call(this, limbSwing *= 3.0, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
          this.head.rotationPointY = 6.0;
          this.body.rotationPointY = 6.0;
          this.leg1.setRotationPoint(-2.0, 18.0, 4.0);
          this.leg2.setRotationPoint(2.0, 18.0, 4.0);
          this.leg3.setRotationPoint(-2.0, 18.0, -4.0);
          this.leg4.setRotationPoint(2.0, 18.0, -4.0);
          if (entityIn === null || entityIn instanceof T.CreeperMinionEntity && entityIn.isSitting()) {
              this.head.rotationPointY += 6.0;
              this.body.rotationPointY += 6.0;
              this.leg1.rotationPointY += 4.0;
              this.leg1.rotationPointZ -= 2.0;
              this.leg2.rotationPointY += 4.0;
              this.leg2.rotationPointZ -= 2.0;
              this.leg3.rotationPointY += 4.0;
              this.leg3.rotationPointZ += 2.0;
              this.leg4.rotationPointY += 4.0;
              this.leg4.rotationPointZ += 2.0;
              this.leg1.rotateAngleX = 1.5707964;
              this.leg2.rotateAngleX = 1.5707964;
              this.leg3.rotateAngleX = -1.5707964;
              this.leg4.rotateAngleX = -1.5707964;
          }
        },
      }
    });

    // ============================================================ CreeperMinionEggModel.java
    MODELS.CreeperMinionEggModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.CreeperMinionEggModel",
      ctor: function (scale) {                              // CreeperMinionEggModel() { this(0.0f); } / (float scale)
          if (scale === undefined) scale = 0.0;
          this.egg = MR(this, 0, 0);                         // field initializer
          this.egg.setRotationPoint(0.0, 22.0, 0.0);
          this.egg.addBox(-2.0, 1.0, -2.0, 4, 1, 4, scale);
          this.egg.addBox(-3.0, -3.0, -3.0, 6, 4, 6, scale);
          this.egg.addBox(-1.0, -6.0, -1.0, 2, 1, 2, scale);
          this.egg.addBox(-2.0, -5.0, -2.0, 4, 2, 4, scale);
      },
      methods: {
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.egg.render(scale);
        },
      }
    });

    // ============================================================ EndersoulFragmentModel.java
    MODELS.EndersoulFragmentModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.EndersoulFragmentModel",
      ctor: function () {
          this.base = MR(this);                              // field initializer
          this.sticks = new Array(8);                        // field initializer
          this.base.addBox(-2.0, -2.0, -2.0, 4, 4, 4);
          this.base.setRotationPoint(0.0, 22.0, 0.0);
          for (var i= 0; i < this.sticks.length; ++i) {
              this.sticks[i] = MR(this);
              if (i < this.sticks.length / 2) {
                  this.sticks[i].addBox(-0.5, -4.0, -0.5, 1, 8, 1);
              } else {
                  this.sticks[i].addBox(-0.5, -6.0, -0.5, 1, 10, 1, 0.15);
              }
              this.base.addChild(this.sticks[i]);
          }
      },
      methods: {
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          if (entityIn instanceof T.EndersoulFragmentEntity) {
              var entity= entityIn;
              for (var i= 0; i < this.sticks.length; ++i) {
                  this.sticks[i].rotateAngleX = entity.stickRotations[i][0];
                  this.sticks[i].rotateAngleY = entity.stickRotations[i][1];
                  this.sticks[i].rotateAngleZ = entity.stickRotations[i][2];
              }
          }
          this.base.render(0.0625);
        },
      }
    });

    // ============================================================ EndersoulHandModel.java
    MODELS.EndersoulHandModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.EndersoulHandModel",
      ctor: function () {
          var i;
          this.textureWidth = 32;
          this.textureHeight = 32;
          this.finger = new Array(3);
          this.foreFinger = new Array(3);
          this.hand = MR(this);
          this.hand.setRotationPoint(0.0, 17.5, 0.0);
          var fingerScale= 0.6;
          for (i = 0; i < this.finger.length; ++i) {
              this.finger[i] = MR(this, i * 4, 0);
              this.finger[i].addBox(-0.5, 0.0, -0.5, 1, i === 1 ? 6 : 5, 1, fingerScale);
          }
          this.finger[0].setRotationPoint(-0.5, 0.0, -1.0);
          this.finger[1].setRotationPoint(-0.5, 0.0, 0.0);
          this.finger[2].setRotationPoint(-0.5, 0.0, 1.0);
          for (i = 0; i < this.foreFinger.length; ++i) {
              this.foreFinger[i] = MR(this, 1 + i * 5, 0);
              this.foreFinger[i].addBox(-0.5, 0.0, -0.5, 1, i === 1 ? 6 : 5, 1, fingerScale - 0.01);
              this.foreFinger[i].setRotationPoint(0.0, 0.5 + (i === 1 ? 6 : 5), 0.0);
          }
          for (i = 0; i < this.finger.length; ++i) {
              this.hand.addChild(this.finger[i]);
              this.finger[i].addChild(this.foreFinger[i]);
          }
          this.thumb = MR(this, 14, 0);
          this.thumb.addBox(-0.5, 0.0, -0.5, 1, 5, 1, fingerScale);
          this.thumb.setRotationPoint(0.5, 0.0, -0.5);
          this.hand.addChild(this.thumb);
      },
      methods: {
        resetAngles: function (model) {
          model.rotateAngleX = 0.0;
          model.rotateAngleY = 0.0;
          model.rotateAngleZ = 0.0;
        },
        setAngles: function () {
          this.resetAngles(this.hand);
          for (var i= 0; i < this.finger.length; ++i) {
              this.resetAngles(this.finger[i]);
              this.resetAngles(this.foreFinger[i]);
          }
          this.resetAngles(this.thumb);
          this.hand.rotateAngleY = -0.3926991;
          this.finger[0].rotateAngleX = -0.2617994;
          this.finger[1].rotateAngleZ = 0.17453294;
          this.finger[2].rotateAngleX = 0.2617994;
          this.foreFinger[0].rotateAngleZ = -0.2617994;
          this.foreFinger[1].rotateAngleZ = -0.3926991;
          this.foreFinger[2].rotateAngleZ = -0.2617994;
          this.thumb.rotateAngleX = -0.62831855;
          this.thumb.rotateAngleZ = -0.3926991;
        },
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.setAngles();
          this.hand.render(0.0625);
        },
      }
    });
    // EndersoulHandModel.Baked / .Unbaked / loader: Forge ICustomModelLoader + IBakedModel plumbing that routes the
    // endersoul hand to MBTileEntityItemStackRenderer and swaps endersoul_hand_gui / endersoul_hand_model by transform
    // type. The engine has no Forge model loader; mutants-items.js reproduces the same selection (see NOTES).

    // ============================================================ MutantArrowModel.java
    MODELS.MutantArrowModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantArrowModel",
      ctor: function () {
          this.stick = MR(this, 0, 0);                       // field initializer
          this.stick.addBox(-0.5, -0.5, -13.0, 1, 1, 26);
          this.stick.setRotationPoint(0.0, 24.0, 0.0);
          this.point1 = MR(this, 0, 0);
          this.point1.addBox(-3.0, -0.5, 0.0, 3, 1, 1, 0.25);
          this.point1.setRotationPoint(0.0, 0.0, -12.0);
          this.stick.addChild(this.point1);
          this.point2 = MR(this, 0, 0);
          this.point2.addBox(0.0, -0.5, 0.0, 3, 1, 1, 0.251);
          this.point2.setRotationPoint(0.0, 0.0, -12.0);
          this.stick.addChild(this.point2);
          this.point3 = MR(this, 0, 2);
          this.point3.addBox(-0.5, -3.0, 0.0, 1, 3, 1, 0.25);
          this.point3.setRotationPoint(0.0, 0.0, -13.0);
          this.stick.addChild(this.point3);
          this.point4 = MR(this, 0, 2);
          this.point4.addBox(-0.5, 0.0, 0.0, 1, 3, 1, 0.251);
          this.point4.setRotationPoint(0.0, 0.0, -13.0);
          this.stick.addChild(this.point4);
          this.point1.rotateAngleY = 0.7853982;
          this.point2.rotateAngleY = -0.7853982;
          this.point3.rotateAngleX = -0.7853982;
          this.point4.rotateAngleX = 0.7853982;
      },
      methods: {
        render: function (entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale) {
          this.stick.render(scale);
        },
      }
    });

    // ============================================================ MutantSkeletonArmorModel.java
    MODELS.MutantSkeletonArmorModel = M.defineModel({
      name: "chumbanotz.mutantbeasts.client.model.MutantSkeletonArmorModel", extend: OB,
      init: function (m) { GSe(m, 0.5); },                  // super(0.5f) = ModelBiped(float modelSize)
      ctor: function () {
          M.listClear(this.bipedHead.cubeList);
          M.listClear(this.bipedHeadwear.cubeList);
          M.listClear(this.bipedBody.cubeList);
          M.listClear(this.bipedRightArm.cubeList);
          M.listClear(this.bipedLeftArm.cubeList);
          M.listClear(this.bipedRightLeg.cubeList);
          M.listClear(this.bipedLeftLeg.cubeList);
          var head= MR(this, 0, 0);
          head.addBox(-4.0, -8.0, -4.0, 8, 8, 8, 0.4);
          var jaw= MR(this, 32, 0);
          jaw.addBox(-4.0, -3.0, -8.0, 8, 3, 8, 0.7);
          jaw.setRotationPoint(0.0, -0.2, 3.5);
          jaw.rotateAngleX = 0.09817477;
          head.addChild(jaw);
          this.bipedHeadwear.addChild(head);
      },
      methods: {
        setRotationAngles: function (limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn) {
          if (entityIn instanceof HC) {                       // EntityArmorStand
              var entityarmorstand= entityIn;
              this.bipedHead.rotateAngleX = PI / 180 * entityarmorstand.bpf.FE;
              this.bipedHead.rotateAngleY = PI / 180 * entityarmorstand.bpf.FF;
              this.bipedHead.rotateAngleZ = PI / 180 * entityarmorstand.bpf.FG;
              this.bipedHead.setRotationPoint(0.0, 1.0, 0.0);
              this.bipedBody.rotateAngleX = PI / 180 * entityarmorstand.a4x.FE;
              this.bipedBody.rotateAngleY = PI / 180 * entityarmorstand.a4x.FF;
              this.bipedBody.rotateAngleZ = PI / 180 * entityarmorstand.a4x.FG;
              this.bipedLeftArm.rotateAngleX = PI / 180 * entityarmorstand.bkg.FE;
              this.bipedLeftArm.rotateAngleY = PI / 180 * entityarmorstand.bkg.FF;
              this.bipedLeftArm.rotateAngleZ = PI / 180 * entityarmorstand.bkg.FG;
              this.bipedRightArm.rotateAngleX = PI / 180 * entityarmorstand.bm0.FE;
              this.bipedRightArm.rotateAngleY = PI / 180 * entityarmorstand.bm0.FF;
              this.bipedRightArm.rotateAngleZ = PI / 180 * entityarmorstand.bm0.FG;
              this.bipedLeftLeg.rotateAngleX = PI / 180 * entityarmorstand.biE.FE;
              this.bipedLeftLeg.rotateAngleY = PI / 180 * entityarmorstand.biE.FF;
              this.bipedLeftLeg.rotateAngleZ = PI / 180 * entityarmorstand.biE.FG;
              this.bipedLeftLeg.setRotationPoint(1.9, 11.0, 0.0);
              this.bipedRightLeg.rotateAngleX = PI / 180 * entityarmorstand.biX.FE;
              this.bipedRightLeg.rotateAngleY = PI / 180 * entityarmorstand.biX.FF;
              this.bipedRightLeg.rotateAngleZ = PI / 180 * entityarmorstand.biX.FG;
              this.bipedRightLeg.setRotationPoint(-1.9, 11.0, 0.0);
              AGZ(this.bipedHead, this.bipedHeadwear);         // ModelBase.copyModelAngles (static)
          } else {
              sBipedModel.setRotationAngles.call(this, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
          }
        },
      }
    });

  };
})(JasprMutants);
