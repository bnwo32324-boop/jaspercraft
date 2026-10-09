/* client/renderer/entity/** and ClientProxy.preInit's registrations: every renderer and layer of Mutant Creatures,
 * translated statement by statement onto the engine's Render (FV) / RenderLiving (D$) classes. Renderers are real TeaVM
 * subclasses; their overrides sit under the engine's virtual names (RenderLiving.doRender = RC, shouldRender = dWg,
 * renderModel = enx, preRenderCallback = tp, applyRotations = a95, getDeathMaxRotation = cZf, getColorMultiplier = ebL,
 * renderName = egj, Render.doRender = jV, getEntityTexture = eI). Layers are objects with the LayerRenderer methods
 * (shouldCombineTextures = pq, doRenderLayer = no), as the engine's own layer list expects.
 *
 * Engine-forced differences, each the closest thing this client has:
 *  - GlStateManager.enableNormalize and enableOutlineMode do not exist in this client (no-ops dropped; outlines are the
 *    glowing effect's pass, which the client does not run for these renderers);
 *  - EntityRenderer.setupFogColor(boolean) is func_191514_d here (DEc), exactly what vanilla's charged-creeper layer calls;
 *  - ClientRegistry.registerEntityShader (spectating a creeper minion, endersoul clone or mutant enderman) needs the
 *    post-processing shader pipeline, which this client does not have; nothing is registered;
 *  - textures live under minecraft:textures/entity/jaspr_mutants/ (the client loads no other resource domain).
 * Every texture is loaded before the first frame that draws a mutant (JasprMutantsPreload, resumable), so no draw path
 * of this file can suspend. */
(function (M) {
  "use strict";
  var MH = M.MH, R = M.R;

  // ---------------------------------------------------------------- textures (MutantBeasts.getEntityTexture)
  var TEX = Object.create(null), ALL_TEX = [];
  function entityTexture(name) {
    var r = TEX[name];
    if (r === undefined) { r = M.rl("textures/entity/jaspr_mutants/" + name + ".png"); TEX[name] = r; ALL_TEX.push(r); }
    return r;
  }
  M.entityTexture = entityTexture;
  var LIGHTNING_TEXTURE = null;                               // new ResourceLocation("textures/entity/creeper/creeper_armor.png")
  M.preloadTextures = function () {
    if (LIGHTNING_TEXTURE === null) { LIGHTNING_TEXTURE = M.rl("textures/entity/creeper/creeper_armor.png"); ALL_TEX.push(LIGHTNING_TEXTURE); }
    ["mutant_zombie", "mutant_skeleton", "mutant_creeper", "spider_pig/spider_pig", "spider_pig/saddle", "mutant_enderman/mutant_enderman",
      "mutant_enderman/eyes", "mutant_enderman/death", "endersoul", "mutant_snow_golem/mutant_snow_golem", "mutant_snow_golem/pumpkin",
      "mutant_snow_golem/glow", "creeper_minion", "creeper_minion_egg", "endersoul_fragment", "mutant_arrow", "endersoul_hand"].forEach(entityTexture);
    if (M.CFG.ENTITIES.creeperMinionOnShoulder) {
      Hj2();                                                  // RenderParrot's class initialiser: PARROT_TEXTURES
      for (var i = 0; i < LrQ.data.length; i++) if (ALL_TEX.indexOf(LrQ.data[i]) < 0) ALL_TEX.push(LrQ.data[i]);
    }
    return ALL_TEX;
  };
  M.texturesReady = function () { for (var i = 0; i < ALL_TEX.length; i++) if (ALL_TEX[i].a3s !== 1) return false; return ALL_TEX.length > 0; };

  // ---------------------------------------------------------------- engine helpers (all synchronous once textures are loaded)
  function mc() { return HEH; }
  function bindTexture(rl) { D17(HEH.bE, rl); }              // Minecraft.getTextureManager().bindTexture
  function fogColor(black) { DEc(HEH.fU, black ? 1 : 0); }    // EntityRenderer.setupFogColor (func_191514_d)
  function lightmap(u, v) { G0W(33985, u, v); }               // OpenGlHelper.setLightmapTextureCoords(lightmapTexUnit, u, v)
  function brightness(e) { return Ei5(e); }                   // Entity.getBrightnessForRender
  function blocksTexture() { Lp(); return HEN; }              // TextureMap.LOCATION_BLOCKS_TEXTURE
  function renderBlockBrightness(state, b) { Ceq(HEH.EG, state, b); }
  function isInvisible(e) { return !!DfJ(e); }
  function depthMask(flag) { EFX(flag ? 1 : 0); }
  function setLightmap(e) { var i = brightness(e); lightmap(i % 65536, (i / 65536) | 0); }   // RenderLiving.setLightmap
  M.R_HELPERS = { bindTexture: bindTexture, fogColor: fogColor, lightmap: lightmap, depthMask: depthMask, setLightmap: setLightmap };

  // ---------------------------------------------------------------- LayerCreeperCharge.render (static)
  function chargeRender(entityIn, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, mainModel, chargedModel) {
    depthMask(entityIn === null || !isInvisible(entityIn));
    bindTexture(LIGHTNING_TEXTURE);
    DSz(5890);
    Cds();
    DPm(ageInTicks * 0.01, ageInTicks * 0.01, 0.0);
    DSz(5888);
    CyM();
    CFh(0.5, 0.5, 0.5, 1.0);
    DFk();
    Fb_(1, 1);                                                // SourceFactor.ONE, DestFactor.ONE
    chargedModel.setModelAttributes(mainModel);
    fogColor(true);
    chargedModel.render(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    fogColor(false);
    DSz(5890);
    Cds();
    DSz(5888);
    D75();
    CTP();
    depthMask(true);
  }
  M.chargeRender = chargeRender;
  function creeperChargeLayer(renderer, model) {               // LayerCreeperCharge(RenderLiving, ModelBase)
    return {
      pq: function () { return 0; },
      no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
        if (e instanceof M.T.MutantCreeperEntity && e.getPowered() || e instanceof M.T.CreeperMinionEntity && e.getPowered()) {
          chargeRender(e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, renderer.iK, model);
        }
      }
    };
  }

  // ---------------------------------------------------------------- EndersoulCloneRenderer.render (static)
  function endersoulRender(entityIn, texture, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, model, alpha) {
    depthMask(entityIn === null || !isInvisible(entityIn));
    DFk();
    bindTexture(texture);
    DSz(5890);
    Cds();
    var f = ageInTicks * 0.008;
    DPm(f, f, 0.0);
    DSz(5888);
    CyM();
    Fb_(770, 771);                                            // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
    lightmap(61680 % 65536, (61680 / 65536) | 0);
    CFh(0.9, 0.3, 1.0, alpha);
    D75();
    fogColor(true);
    model.render(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    fogColor(false);
    var i = brightness(entityIn);
    lightmap(i % 65536, (i / 65536) | 0);
    CFh(1.0, 1.0, 1.0, 1.0);
    DSz(5890);
    Cds();
    DSz(5888);
    CTP();
    depthMask(true);
  }
  M.endersoulRender = endersoulRender;

  // ---------------------------------------------------------------- class definition helpers
  // init(r, rm) runs the engine constructor of the parent (Render B68 / RenderLivingBase B7Q, which RenderLiving's
  // constructor calls as its only statement), then the Java constructor body (ctor).
  // what the engine gets from an override while rendering is off (or after it threw): shouldRender false, a loaded texture
  var FALLBACK = { dWg: function () { return 0; }, eI: function () { return blocksTexture(); }, cZf: function () { return 90.0; },
    ebL: function () { return 0; } };
  function counted(fn) { return function () { M.stats.renders++; return fn.apply(this, arguments); }; }   // calls into the renderers
  function guardVirtuals(virtuals) {
    var out = {};
    for (var v in virtuals) out[v] = M.guardVirtual("render", v, counted(virtuals[v]), FALLBACK[v]);
    return out;
  }
  // LayerRenderer objects: doRenderLayer guarded like the renderers
  function addLayer(renderer, layer) {
    layer.no = M.guardVirtual("render", "no", layer.no);
    C9g(renderer, layer);
  }
  function defineRender(spec) {
    var C = M.defineClass({ name: spec.name, extend: spec.extend, fields: spec.fields, virtuals: guardVirtuals(spec.virtuals || {}) });
    if (spec.methods) for (var k in spec.methods) Object.defineProperty(C.prototype, k, { value: spec.methods[k], writable: true, configurable: true });
    C.create = function (rm) { var r = new C(); spec.init(r, rm); if (spec.ctor) spec.ctor.call(r, rm); return r; };
    return C;
  }
  function living(model, shadow) { return function (r, rm) { B7Q(r, rm, model(), shadow); }; }
  function plain(r, rm) { B68(r, rm); }
  M.RENDERERS = {};

  M.defineRenderers = function () {
    var MODELS = M.MODELS, T = M.T, RL = M.RENDERERS;

    // ============================================================ MutantZombieRenderer.java
    var ZOMBIE_TEXTURE = entityTexture("mutant_zombie");
    RL.MutantZombieRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantZombieRenderer", extend: D$,
      init: living(function () { return MODELS.MutantZombieModel.create(); }, 1.0),
      virtuals: {
        enx: function (living, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor) {   // renderModel
          if (living.vanishTime > 0) {
            CyM();
            Fb_(770, 771);
            CFh(1.0, 1.0, 1.0, 1.0 - (living.vanishTime + this.iK.getPartialTick()) / 100.0 * 0.6);
          }
          CoL(this, living, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
          if (living.vanishTime > 0) {
            CTP();
            CFh(1.0, 1.0, 1.0, 1.0);
          }
        },
        tp: function () { FWM(1.3, 1.3, 1.3); },                // preRenderCallback
        a95: function (entityLiving, ageInTicks, rotationYaw, partialTicks) {                                  // applyRotations
          if (entityLiving.deathTime > 0) {
            Gc9(180.0 - rotationYaw, 0.0, 1.0, 0.0);
            var pitch = Math.min(20, entityLiving.deathTime), reviving = false;
            if (entityLiving.deathTime > 100) { pitch = 140 - entityLiving.deathTime; reviving = true; }
            if (pitch > 0) {
              var f = (pitch + partialTicks - 1.0) / 20.0 * 1.6;
              if (reviving) f = (pitch - partialTicks) / 40.0 * 1.6;
              if ((f = MH.sqrt(f)) > 1.0) f = 1.0;
              Gc9(f * this.cZf(entityLiving), -1.0, 0.0, 0.0);
            }
          } else ECq(this, entityLiving, ageInTicks, rotationYaw, partialTicks);
        },
        cZf: function () { return 80.0; },                      // getDeathMaxRotation
        eI: function () { return ZOMBIE_TEXTURE; }              // getEntityTexture
      }
    });

    // ============================================================ MutantSkeletonRenderer.java
    var SKELETON_TEXTURE = entityTexture("mutant_skeleton");
    RL.MutantSkeletonRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantSkeletonRenderer", extend: D$,
      init: living(function () { return MODELS.MutantSkeletonModel.create(); }, 0.7),
      virtuals: { cZf: function () { return 0.0; }, eI: function () { return SKELETON_TEXTURE; } }
    });

    // ============================================================ MutantCreeperRenderer.java
    var CREEPER_TEXTURE = entityTexture("mutant_creeper");
    RL.MutantCreeperRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantCreeperRenderer", extend: D$,
      init: living(function () { return MODELS.MutantCreeperModel.create(); }, 1.5),
      ctor: function () { addLayer(this, creeperChargeLayer(this, MODELS.MutantCreeperModel.create(2.0))); },
      virtuals: {
        tp: function (e) {
          var scale = 1.2;
          if (e.deathTime > 0) { var f1 = e.deathTime / 100.0; scale -= f1 * 0.4; }
          FWM(scale, scale, scale);
        },
        ebL: function (e, lightBrightness, partialTickTime) {   // getColorMultiplier
          if (e.isJumpAttacking() && e.deathTime === 0) {
            var f = e.getCreeperFlashIntensity(partialTickTime);
            if ((M.f2i(f * 10.0)) % 2 === 0) return 0;
            var i = M.f2i(f * 0.2 * 255.0);
            i = MH.clampI(i, 0, 255);
            return i << 24 | 0x30FFFFFF;
          }
          var a = M.f2i(e.getCreeperFlashIntensity(partialTickTime)) * -1;
          var r = 255, g = 255, b = 255;
          if (e.getPowered()) { r = 160; g = 180; }
          return a << 24 | r << 16 | g << 8 | b;
        },
        cZf: function () { return 0.0; },
        eI: function () { return CREEPER_TEXTURE; }
      }
    });

    // ============================================================ SpiderPigRenderer.java
    var PIG_TEXTURE = entityTexture("spider_pig/spider_pig"), SADDLE_TEXTURE = entityTexture("spider_pig/saddle");
    RL.SpiderPigRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.SpiderPigRenderer", extend: D$,
      init: living(function () { return MODELS.SpiderPigModel.create(); }, 0.8),
      ctor: function () {
        var self = this;
        addLayer(this, {                                             // SaddleLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            if (e.isSaddled()) {
              FTd(self, SADDLE_TEXTURE);
              self.iK.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            }
          }
        });
      },
      virtuals: {
        cZf: function () { return 180.0; },
        tp: function (e) {
          var scale = 1.2;
          if (e.bV5()) { scale *= 0.5; this.Cb *= 0.5; } else this.Cb = 0.8;     // isChild; shadowSize
          FWM(scale, scale, scale);
        },
        eI: function () { return PIG_TEXTURE; }
      }
    });

    // ============================================================ EndersoulCloneRenderer.java
    var ENDERSOUL_TEXTURE = entityTexture("endersoul");
    RL.EndersoulCloneRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.EndersoulCloneRenderer", extend: D$,
      init: living(function () { var m = new CJR(); GOC(m, 0.0); return m; }, 0.5),          // new ModelEnderman(0.0f)
      ctor: function () { this.b$m = 0.5; },                    // shadowOpaque
      virtuals: {
        RC: function (entity, x, y, z, entityYaw, partialTicks) {                               // doRender
          this.iK.dlm = entity.isAggressive() ? 1 : 0;   // ModelEnderman.isAttacking
          if (entity.isAggressive()) {
            x += R.nextGaussian(entity.getRNG()) * 0.02;
            z += R.nextGaussian(entity.getRNG()) * 0.02;
          }
          CIl(this, entity, x, y, z, entityYaw, partialTicks);
        },
        enx: function (e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor) {
          endersoulRender(e, ENDERSOUL_TEXTURE, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, this.iK, 1.0);
        },
        cZf: function () { return 0.0; },
        eI: function () { return ENDERSOUL_TEXTURE; }
      }
    });
    M.ENDERSOUL_TEXTURE = ENDERSOUL_TEXTURE;

    // ============================================================ MutantEndermanRenderer.java
    var ENDERMAN_TEXTURE = entityTexture("mutant_enderman/mutant_enderman"), EYES_TEXTURE = entityTexture("mutant_enderman/eyes"),
      DEATH_TEXTURE = entityTexture("mutant_enderman/death");
    RL.MutantEndermanRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantEndermanRenderer", extend: D$,
      init: living(function () { return MODELS.MutantEndermanModel.create(); }, 0.8),
      fields: function (s) { s.endermanModel = null; s.cloneModel = null; s.teleportAttack = false; },
      ctor: function () {
        var self = this;
        this.endermanModel = this.iK;
        this.cloneModel = new CJR(); GOC(this.cloneModel, 0.0);
        addLayer(this, {                                             // EyesLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            if (!e.isClone()) {
              DFk();
              depthMask(!isInvisible(e));
              FTd(self, EYES_TEXTURE);
              lightmap(61680.0, 0.0);
              CFh(1.0, 1.0, 1.0, 1.0);
              self.iK.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
              setLightmap(e);
              depthMask(true);
              D75();
            }
          }
        });
        addLayer(this, {                                             // EndersoulLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            var teleport = e.getAttackID() === 4 && e.getAttackTick() < 10, scream = e.getAttackID() === 5, clone = e.isClone();
            if (teleport || scream || clone) {
              var glowScale = 2.0, alpha = 1.0;
              if (teleport) {
                glowScale = 1.2 + (e.getAttackTick() + partialTicks) / 10.0;
                if (self.teleportAttack) {
                  glowScale = 2.2 - (e.getAttackTick() + partialTicks) / 10.0;
                  if (e.getAttackTick() < 2) alpha = (e.getAttackTick() + partialTicks) / 2.0;
                } else if (e.getAttackTick() >= 8) alpha -= (e.getAttackTick() - 8 + partialTicks) / 2.0;
              }
              if (scream) {
                if (e.getAttackTick() < 40) {
                  glowScale = 1.2 + (e.getAttackTick() + partialTicks) / 40.0;
                  alpha = (e.getAttackTick() + partialTicks) / 40.0;
                } else if (e.getAttackTick() < 160) glowScale = 2.2;
                else {
                  glowScale = 2.2 - (e.getAttackTick() + partialTicks) / 10.0;
                  alpha = 1.0 - (e.getAttackTick() + partialTicks) / 40.0;
                }
              }
              if (!clone) { Eu0(); FWM(glowScale, glowScale * 0.8, glowScale); }
              endersoulRender(e, ENDERSOUL_TEXTURE, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, self.iK, alpha);
              if (!clone) ECi();
            }
          }
        });
        addLayer(this, {                                             // HeldBlocksLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            if (!(self.iK instanceof MODELS.MutantEndermanModel)) return;
            CF0();
            for (var i = 1; i < e.heldBlock.length; ++i) {
              if (e.heldBlock[i] === 0) continue;
              Eu0();
              self.iK.postRenderArm(0.0625, i);
              DPm(0.0, 1.2, 0.0);
              var tick = e.ticksExisted + i * 2.0 * M.PI + partialTicks;
              Gc9(tick * 10.0, 1.0, 0.0, 0.0);
              Gc9(tick * 8.0, 0.0, 1.0, 0.0);
              Gc9(tick * 6.0, 0.0, 0.0, 1.0);
              var f = 0.75;
              FWM(-f, -f, f);
              var var4 = brightness(e);
              lightmap(var4 % 65536, (var4 / 65536) | 0);
              CFh(1.0, 1.0, 1.0, 1.0);
              FTd(self, blocksTexture());
              DPm(-0.5, -0.5, 0.5);
              renderBlockBrightness(DVV(e.heldBlock[i]), 1.0);
              ECi();
            }
            ET8();
          }
        });
      },
      virtuals: {
        dWg: function (e, camera, camX, camY, camZ) {           // shouldRender
          if (Fy3(this, e, camera, camX, camY, camZ)) return 1;
          if (e.getAttackID() === 4) {
            var pos = e.getTeleportPosition(), width = e.width / 2.0, bb = new DN();
            ED(bb, pos.m + 0.5 - width, pos.i, pos.l + 0.5 - width, pos.m + 0.5 + width, pos.i + e.height, pos.l + 0.5 + width);
            return Ye(camera, bb) ? 1 : 0;
          }
          return 0;
        },
        enx: function (e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor) {
          if (e.deathTime > 80) {
            var blendFactor = (e.deathTime - 80) / 200.0;
            CcT(515);
            D6M();
            DQU(516, blendFactor);
            FTd(this, DEATH_TEXTURE);
            this.iK.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
            DQU(516, 0.1);
            CcT(514);
          }
          CoL(this, e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
          CcT(515);
        },
        RC: function (entity, x, y, z, entityYaw, partialTicks) {
          if (entity.isClone()) { this.Cb = 0.5; this.b$m = 0.5; this.iK = this.cloneModel; }
          else { this.Cb = 0.8; this.b$m = 1.0; this.iK = this.endermanModel; }
          this.teleportAttack = false;
          this.cloneModel.dlm = entity.isAggressive() ? 1 : 0;   // ModelEnderman.isAttacking
          var forcedLook = entity.getAttackID() === 3, scream = entity.getAttackID() === 5, clone = entity.isClone() && entity.isAggressive(),
            telesmash = entity.getAttackID() === 7 && entity.getAttackTick() < 18, death = entity.getAttackID() === 8;
          if (forcedLook || scream || clone || telesmash || death) {
            var shake = 0.03;
            if (entity.getAttackTick() >= 40 && !clone && !death) shake *= 0.5;
            if (clone) shake = 0.02;
            if (death) shake = entity.getAttackTick() < 80 ? 0.019999999552965164 : 0.05000000074505806;
            x += R.nextGaussian(entity.getRNG()) * shake;
            z += R.nextGaussian(entity.getRNG()) * shake;
          }
          CIl(this, entity, x, y, z, entityYaw, partialTicks);
          if (entity.getAttackID() === 4) {
            this.teleportAttack = true;
            var rm = this.ja, tp = entity.getTeleportPosition();
            var renderPosX = tp.m + 0.5 - rm.bP0, renderPosY = tp.i - rm.bP1, renderPosZ = tp.l + 0.5 - rm.bPZ;
            CIl(this, entity, renderPosX, renderPosY, renderPosZ, entityYaw, partialTicks);
            EnF(this, entity, renderPosX, renderPosY, renderPosZ, entityYaw, partialTicks);
          }
        },
        cZf: function () { return 0.0; },
        eI: function (e) { return e.isClone() ? null : ENDERMAN_TEXTURE; }
      }
    });

    // ============================================================ MutantSnowGolemRenderer.java
    var GOLEM_TEXTURE = entityTexture("mutant_snow_golem/mutant_snow_golem"), PUMPKIN_TEXTURE = entityTexture("mutant_snow_golem/pumpkin"),
      GLOW_TEXTURE = entityTexture("mutant_snow_golem/glow");
    var ICE_STATE = null;
    RL.MutantSnowGolemRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantSnowGolemRenderer", extend: D$,
      init: living(function () { return MODELS.MutantSnowGolemModel.create(); }, 0.7),
      ctor: function () {
        var self = this;
        addLayer(this, {                                             // PumpkinLayer
          pq: function () { return 1; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            if (e.isPumpkinEquipped() && !isInvisible(e)) {
              FTd(self, PUMPKIN_TEXTURE);
              self.iK.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            }
          }
        });
        addLayer(this, {                                             // GlowLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
            if (e.isPumpkinEquipped()) {
              FTd(self, GLOW_TEXTURE);
              DFk();
              depthMask(!isInvisible(e));
              lightmap(61680.0, 0.0);
              var f1 = MH.cos(ageInTicks * 0.1), f2 = MH.cos(ageInTicks * 0.15);
              CFh(1.0, 0.8 + 0.05 * f2, 0.15 + 0.2 * f1, 1.0);
              fogColor(true);
              self.iK.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
              fogColor(false);
              CFh(1.0, 1.0, 1.0, 1.0);
              setLightmap(e);
              depthMask(true);
              D75();
            }
          }
        });
        addLayer(this, {                                             // HeldBlockLayer
          pq: function () { return 0; },
          no: function (e, limbSwing, limbSwingAmount, partialTicks) {
            if (e.isEntityAlive() && e.isThrowing() && e.getThrowingTick() < 7) {
              if (ICE_STATE === null) ICE_STATE = M.defaultState(M.block("ice"));
              CF0();
              Eu0();
              DPm(0.4, 0.0, 0.0);
              self.iK.postRenderArm(0.0625);
              DPm(0.0, 0.9, 0.0);
              FWM(-0.8, -0.8, 0.8);
              var i = brightness(e);
              lightmap(i % 65536, (i / 65536) | 0);
              CFh(1.0, 1.0, 1.0, 1.0);
              FTd(self, blocksTexture());
              DPm(-0.5, -0.5, 0.5);
              renderBlockBrightness(ICE_STATE, 1.0);
              ECi();
              ET8();
            }
          }
        });
      },
      virtuals: {
        egj: function (entity, x, y, z) {                       // renderName
          Co$(this, entity, x, y, z);
          var owner = entity.getOwner();
          if (owner !== null) {
            // textComponent.getStyle().setItalic(true): the owner's name and every unstyled part of it in italics
            var text = "\u00a7o" + M.ustr(Cqa(owner.iG())).split("\u00a7r").join("\u00a7r\u00a7o");
            if (this.cTS(entity)) y += this.ja.dZE.c6 * 1.15 * 0.025;     // FONT_HEIGHT * 1.15 * 0.025
            DU8(this, entity, x, y, z, M.jstr(text), 64.0);                 // Render.renderEntityName(..., NAME_TAG_RANGE)
          }
        },
        eI: function () { return GOLEM_TEXTURE; }
      }
    });

    // ============================================================ CreeperMinionRenderer.java
    var MINION_TEXTURE = entityTexture("creeper_minion");
    M.CREEPER_MINION_TEXTURE = MINION_TEXTURE;
    RL.CreeperMinionRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.CreeperMinionRenderer", extend: D$,
      init: living(function () { return MODELS.CreeperMinionModel.create(); }, 0.25),
      ctor: function () { addLayer(this, creeperChargeLayer(this, MODELS.CreeperMinionModel.create(2.0))); },
      virtuals: {
        tp: function (e, partialTickTime) {
          var f = e.getCreeperFlashIntensity(partialTickTime);
          var f1 = 1.0 + MH.sin(f * 100.0) * f * 0.01;
          f = MH.clampF(f, 0.0, 1.0);
          f *= f; f *= f;
          var f2 = (1.0 + f * 0.4) * f1 * 0.5, f3 = (1.0 + f * 0.1) / f1 * 0.5;
          FWM(f2, f3, f2);
        },
        ebL: function (e, lightBrightness, partialTickTime) {
          var f = e.getCreeperFlashIntensity(partialTickTime);
          if ((M.f2i(f * 10.0)) % 2 === 0) return 0;
          var i = M.f2i(f * 0.2 * 255.0);
          i = MH.clampI(i, 0, 255);
          return i << 24 | 0x30FFFFFF;
        },
        eI: function () { return MINION_TEXTURE; }
      }
    });

    // ============================================================ CreeperMinionEggRenderer.java
    var EGG_TEXTURE = entityTexture("creeper_minion_egg");
    RL.CreeperMinionEggRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.CreeperMinionEggRenderer", extend: FV,
      init: plain,
      fields: function (s) { s.eggModel = null; s.chargedModel = null; },
      ctor: function () { this.eggModel = MODELS.CreeperMinionEggModel.create(); this.chargedModel = MODELS.CreeperMinionEggModel.create(1.0); this.Cb = 0.4; },
      virtuals: {
        jV: function (entity, x, y, z, entityYaw, partialTicks) {
          DqP(this, entity, x, y, z, entityYaw, partialTicks);
          Eu0();
          DPm(x, y, z);
          CF0();
          FWM(-1.0, -1.0, 1.0);
          FWM(1.5, 1.5, 1.5);
          DPm(0.0, -1.501, 0.0);
          Ew0(this, entity);
          this.eggModel.render(entity, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0625);
          if (entity.isCharged()) chargeRender(entity, 0.0, 0.0, partialTicks, entity.ticksExisted + partialTicks, 0.0, 0.0, 0.0625, this.eggModel, this.chargedModel);
          ET8();
          ECi();
        },
        eI: function () { return EGG_TEXTURE; }
      }
    });

    // ============================================================ BodyPartRenderer.java
    RL.BodyPartRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.BodyPartRenderer", extend: FV,
      init: plain,
      fields: function (s) { s.model = null; },
      ctor: function () {
        this.model = MODELS.MutantSkeletonPartModel.create();
        var list = this.model.boxList;
        for (var i = M.listSize(list) - 1; i >= 0; --i) {
          var renderer = M.listGet(list, i);
          if (M.listSize(renderer.cubeList) !== 0) continue;
          M.listRemoveAt(list, i);
        }
      },
      virtuals: {
        jV: function (entity, x, y, z, entityYaw, partialTicks) {
          DqP(this, entity, x, y, z, entityYaw, partialTicks);
          Eu0();
          DPm(x, y, z);
          var yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
          var pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
          Gc9(yaw, 0.2, 0.9, -0.1);
          Gc9(pitch, 0.9, 0.1, 0.2);
          CF0();
          FWM(1.2, -1.2, -1.2);
          Ew0(this, entity);
          this.model.setAngles();
          this.model.getSkeletonPart(entity.getPart()).render(0.0625);
          ET8();
          ECi();
        },
        eI: function () { return SKELETON_TEXTURE; }
      }
    });

    // ============================================================ EndersoulFragmentRenderer.java
    var FRAGMENT_TEXTURE = entityTexture("endersoul_fragment");
    RL.EndersoulFragmentRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.EndersoulFragmentRenderer", extend: FV,
      init: plain,
      fields: function (s) { s.model = null; },
      ctor: function () { this.model = MODELS.EndersoulFragmentModel.create(); this.Cb = 0.3; this.b$m = 0.5; },
      virtuals: {
        jV: function (entity, x, y, z, entityYaw, partialTicks) {
          DqP(this, entity, x, y, z, entityYaw, partialTicks);
          Eu0();
          DPm(x, y - 1.9, z);
          FWM(1.6, 1.6, 1.6);
          endersoulRender(entity, FRAGMENT_TEXTURE, 0.0, 0.0, entity.ticksExisted + partialTicks, 0.0, 0.0, 0.0625, this.model, 1.0);
          ECi();
        },
        eI: function () { return FRAGMENT_TEXTURE; }
      }
    });

    // ============================================================ MutantArrowRenderer.java
    var ARROW_TEXTURE = entityTexture("mutant_arrow");
    RL.MutantArrowRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.MutantArrowRenderer", extend: FV,
      init: plain,
      fields: function (s) { s.arrowModel = null; },
      ctor: function () { this.arrowModel = MODELS.MutantArrowModel.create(); },
      virtuals: {
        b2r: function () { return 1; },                         // shouldRender
        jV: function (entity, x, y, z, entityYaw, partialTicks) {
          DqP(this, entity, x, y, z, entityYaw, partialTicks);
          Eu0();
          CyM();
          Fb_(770, 771);
          DPm(x, y, z);
          Ew0(this, entity);
          var ageInTicks = entity.ticksExisted + partialTicks;
          for (var i = 0; i < entity.getClones(); ++i) {
            Eu0();
            var scale = entity.getSpeed() - i * 0.08;
            var x1 = (entity.getTargetX() - entity.posX) * ageInTicks * scale;
            var y1 = (entity.getTargetY() - entity.posY) * ageInTicks * scale;
            var z1 = (entity.getTargetZ() - entity.posZ) * ageInTicks * scale;
            DPm(x1, y1, z1);
            Gc9(entity.rotationYaw, 0.0, 1.0, 0.0);
            Gc9(entity.rotationPitch, 1.0, 0.0, 0.0);
            FWM(1.2, 1.2, 1.2);
            CFh(1.0, 1.0, 1.0, 1.0 - i * 0.08);
            this.arrowModel.render(entity, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0625);
            ECi();
          }
          CTP();
          CFh(1.0, 1.0, 1.0, 1.0);
          ECi();
        },
        eI: function () { return ARROW_TEXTURE; }
      }
    });

    // ============================================================ ThrowableBlockRenderer.java
    RL.ThrowableBlockRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.renderer.entity.ThrowableBlockRenderer", extend: FV,
      init: plain,
      ctor: function () { this.Cb = 0.6; },
      virtuals: {
        jV: function (entity, x, y, z, entityYaw, partialTicks) {
          if (entity.getThrower() instanceof T.MutantSnowGolemEntity) {
            Eu0();
            DPm(x, y + 0.5, z);
            Gc9(entity.rotationYaw, 0.0, 1.0, 0.0);
            Gc9(45.0, 0.0, 1.0, 0.0);
            Gc9((entity.ticksExisted + partialTicks) * 20.0, 1.0, 0.0, 0.0);
            Gc9((entity.ticksExisted + partialTicks) * 12.0, 0.0, 0.0, -1.0);
            Ew0(this, entity);
            DPm(-0.5, -0.5, 0.5);
            renderBlockBrightness(entity.getBlockState(), 1.0);
            ECi();
          } else {
            CF0();
            Eu0();
            DPm(x, y + 0.5, z);
            Gc9(45.0, 0.0, 1.0, 0.0);
            Gc9((entity.ticksExisted + partialTicks) * 20.0, 1.0, 0.0, 0.0);
            Gc9((entity.ticksExisted + partialTicks) * 12.0, 0.0, 0.0, -1.0);
            var scale = 0.75;
            FWM(-scale, -scale, scale);
            Ew0(this, entity);
            var var4 = brightness(entity);
            lightmap(var4 % 65536, (var4 / 65536) | 0);
            CyM();
            Fb_(770, 771);
            DPm(-0.5, -0.5, 0.5);
            renderBlockBrightness(entity.getBlockState(), 1.0);
            CTP();
            CFh(1.0, 1.0, 1.0, 1.0);
            ECi();
            ET8();
          }
          DqP(this, entity, x, y, z, entityYaw, partialTicks);
        },
        eI: function () { return blocksTexture(); }
      }
    });

    // ============================================================ SkullSpiritEntity: Render with no texture (ClientProxy)
    RL.SkullSpiritRenderer = defineRender({
      name: "chumbanotz.mutantbeasts.client.ClientProxy$SkullSpiritRender", extend: FV,
      init: plain,
      virtuals: { eI: function () { return null; } }
    });
  };

  // ---------------------------------------------------------------- MBEntityLayerOnShoulder.java
  // Replaces vanilla's LayerEntityOnShoulder (BJV) in every player renderer, as ClientProxy.init does: vanilla's layer
  // binds a null renderer's texture for anything that is not a parrot. Parrots are drawn exactly as vanilla does.
  var shoulderLayer = null;
  function isOnShoulder(tag, id) {
    var s = M.ustr(F54(tag, M.JS("id")));
    if (s.indexOf(":") < 0) s = "minecraft:" + s;
    return s.toLowerCase() === id;
  }
  function makeShoulderLayer(rm) {
    var parrotRender = new AZV(); BC0(parrotRender, rm);       // RenderParrot (its class initialiser fills PARROT_TEXTURES)
    var parrotModel = HbK();                                   // new ModelParrot()
    var creeperMinionModel = M.MODELS.CreeperMinionModel.create(), chargedModel = M.MODELS.CreeperMinionModel.create(2.0);
    function renderOnShoulder(player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, leftShoulder) {
      var compoundnbt = leftShoulder ? Ec6(player) : Dcd(player);
      if (compoundnbt === null || D16(compoundnbt)) return;
      if (isOnShoulder(compoundnbt, "minecraft:parrot")) {
        Eu0();
        DPm(leftShoulder ? 0.4 : -0.4, player.q1() ? -1.3 : -1.5, 0.0);
        bindTexture(LrQ.data[DcG(compoundnbt, M.JS("Variant"))]);
        Egx(parrotModel, player, limbSwing, limbSwingAmount, partialTicks);
        Dus(parrotModel, limbSwing, limbSwingAmount, 0.0, netHeadYaw, headPitch, scale, player);
        F$F(parrotModel, player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        ECi();
      } else if (isOnShoulder(compoundnbt, "mutantbeasts:creeper_minion")) {
        Eu0();
        DPm(leftShoulder ? 0.42 : -0.42, player.q1() ? -0.55 : -0.75, 0.0);
        bindTexture(M.CREEPER_MINION_TEXTURE);
        FWM(0.5, 0.5, 0.5);
        creeperMinionModel.render(null, 0.0, 0.0, ageInTicks, netHeadYaw, headPitch, scale);
        if (DcG(compoundnbt, M.JS("Powered")) !== 0) chargeRender(null, 0.0, 0.0, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, creeperMinionModel, chargedModel);
        ECi();
      }
    }
    return {
      $jmShoulder: true,
      pq: function () { return 0; },
      no: function (player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale) {
        CF0();
        CFh(1.0, 1.0, 1.0, 1.0);
        renderOnShoulder(player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, true);
        renderOnShoulder(player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale, false);
        ET8();
      }
    };
  }
  M.installShoulderLayers = function (rm) {
    if (!M.CFG.ENTITIES.creeperMinionOnShoulder) return 0;
    if (shoulderLayer === null) { shoulderLayer = makeShoulderLayer(rm); shoulderLayer.no = M.guardVirtual("render", "no", shoulderLayer.no); }
    var replaced = 0, seen = [];
    ["default", "slim", "zombie", "eagler"].forEach(function (k) {
      var r = Cno(rm.bfw, M.JS(k));
      if (r === null || seen.indexOf(r) >= 0 || !r.cHT) return;
      seen.push(r);
      var layers = M.listToArray(r.cHT);
      for (var i = layers.length - 1; i >= 0; i--) if (layers[i] instanceof BJV || layers[i].$jmShoulder) M.listRemoveAt(r.cHT, i);
      C9g(r, shoulderLayer);                                   // guarded: while rendering is off nothing rides on shoulders
      replaced++;
    });
    return replaced;
  };

  // ---------------------------------------------------------------- ClientProxy.preInit: RenderingRegistry
  M.registerRenderers = function (rm) {
    var T = M.T, RL = M.RENDERERS;
    var pairs = [
      [T.BodyPartEntity, RL.BodyPartRenderer], [T.CreeperMinionEntity, RL.CreeperMinionRenderer], [T.CreeperMinionEggEntity, RL.CreeperMinionEggRenderer],
      [T.EndersoulCloneEntity, RL.EndersoulCloneRenderer], [T.EndersoulFragmentEntity, RL.EndersoulFragmentRenderer], [T.MutantArrowEntity, RL.MutantArrowRenderer],
      [T.MutantCreeperEntity, RL.MutantCreeperRenderer], [T.MutantEndermanEntity, RL.MutantEndermanRenderer], [T.MutantSkeletonEntity, RL.MutantSkeletonRenderer],
      [T.MutantSnowGolemEntity, RL.MutantSnowGolemRenderer], [T.MutantZombieEntity, RL.MutantZombieRenderer], [T.SkullSpiritEntity, RL.SkullSpiritRenderer],
      [T.SpiderPigEntity, RL.SpiderPigRenderer], [T.ThrowableBlockEntity, RL.ThrowableBlockRenderer]
    ];
    for (var i = 0; i < pairs.length; i++) EDK(rm.dg, E(pairs[i][0]), pairs[i][1].create(rm));
    // ChemicalXEntity: new RenderSnowball<>(manager, MBItems.CHEMICAL_X, Minecraft.getMinecraft().getRenderItem())
    var snowball = new XW(); C5L(snowball, rm, M.ITEMS_MB.CHEMICAL_X, HEH.u4);
    EDK(rm.dg, E(T.ChemicalXEntity), snowball);
    return pairs.length + 1;
  };
})(JasprMutants);
