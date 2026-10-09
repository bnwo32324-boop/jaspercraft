/* Client twins of the Mutant Creatures entity classes (chumbanotz/mutantbeasts/entity/**). Each class is a real TeaVM
 * subclass of the vanilla class the mod extends, registered under the protocol's network type id (MUTANTS_PROTOCOL.md
 * 1.1) and data key ids (1.2). Translated: every method that can run on a client (world.isRemote == true): constructors,
 * entityInit, client ticks, status bytes, spawn data, interactions, riding, death animations. Server-only code (AI
 * goals, NBT, loot, explosions, spawning rules, boss bars) never runs on a client and is not translated; each class
 * lists those methods in its header so the omission is auditable. Java float arithmetic stays in doubles exactly as
 * the TeaVM engine does it. */
(function (M) {
  "use strict";
  var f = M.f, MH = M.MH, R = M.R, W = M.W, DATA = M.DATA, CFG;
  var T = M.T = {};                                           // translated classes by Java simple name
  M.ENTITY_TYPES = [];                                        // protocol table 1.1, filled below

  // ---------------------------------------------------------------- shared helpers
  function DS() { D3(); return { GENERIC: Kt4, OUT_OF_WORLD: KsZ, LAVA: Ksv, DROWN: Ktp, IN_WALL: Ktn, FALL: KtZ, MAGIC: KEC, ON_FIRE: Ksu }; }
  function trueSource(src) { return src.eQ(); }               // DamageSource.getTrueSource
  function immediateSource(src) { return src.LV(); }          // DamageSource.getImmediateSource
  function isExplosion(src) { return !!G8e(src); }
  function isUnblockable(src) { return !!GWn(src); }
  function particle(id) { CC(); return D2a(id); }             // EnumParticleTypes.getParticleFromId
  M.particle = particle;
  function P() { // EnumParticleTypes constants used by the mod
    return { EXPLOSION_NORMAL: particle(0), WATER_SPLASH: particle(5), PORTAL: particle(24), FLAME: particle(26),
      SNOWBALL: particle(31), HEART: particle(34), BARRIER: particle(35), ITEM_CRACK: particle(36), BLOCK_CRACK: particle(37),
      WATER_DROP: particle(39) };
  }
  M.P = P;
  function bool(x) { return x ? 1 : 0; }
  function isRemote(e) { return !!e.world.r; }

  // EntityUtil.java (client-reachable helpers)
  var EntityUtil = M.EntityUtil = {
    spawnParticleAtEntity: function (entity, particleType, amount, parameters) {
      if (isRemote(entity)) {
        var rng = entity.getRNG();
        for (var i = 0; i < amount; ++i) {
          var posX = entity.posX + R.nextFloat(rng) * entity.width * 2.0 - entity.width;
          var posY = entity.posY + 0.5 + R.nextFloat(rng) * entity.height;
          var posZ = entity.posZ + R.nextFloat(rng) * entity.width * 2.0 - entity.width;
          var x = R.nextGaussian(rng) * 0.02, y = R.nextGaussian(rng) * 0.02, z = R.nextGaussian(rng) * 0.02;
          W.spawnParticle(entity.world, particleType, posX, posY, posZ, x, y, z, parameters);
        }
      }
    },
    spawnEndersoulParticles: function (entity, amount, speed) {
      var wr = entity.world.R;
      for (var i = 0; i < amount; ++i) {
        var f = (R.nextFloat(wr) - 0.5) * speed, f1 = (R.nextFloat(wr) - 0.5) * speed, f2 = (R.nextFloat(wr) - 0.5) * speed;
        var tempX = entity.posX + (R.nextFloat(wr) - 0.5) * entity.width;
        var tempY = entity.posY + (R.nextFloat(wr) - 0.5) * entity.height + 0.5;
        var tempZ = entity.posZ + (R.nextFloat(wr) - 0.5) * entity.width;
        W.spawnParticle(entity.world, M.MBParticles.ENDERSOUL, tempX, tempY, tempZ, f, f1, f2, null);
      }
    },
    getDirVector: function (rotation, scale) {
      var rad = rotation * (Math.PI / 180);
      return [-MH.sin(rad) * scale, 0.0, MH.cos(rad) * scale];
    }
  };
  // Forge's Entity.isAddedToWorld(): true between World.onEntityAdded and onEntityRemoved. The client equivalent is
  // "alive and still in the client world's entity list" (removal from the world marks the entity dead).
  function isAddedToWorld(entity) { return !entity.isDead && entity.world !== null && entity.addedToChunk_ !== false; }
  M.isAddedToWorld = isAddedToWorld;

  // Guava Predicate implemented as a TeaVM object (interface Dp, method "cs" = apply)
  var predicateClass = null;
  function predicate(fn) {
    if (!predicateClass) {
      predicateClass = function () { D.call(this); this.$fn = null; };
      $rt_metadata([predicateClass, "chumbanotz.mutantbeasts.JasprPredicate", -1, D, [Dp], 0, 3, 0, 0, 0]);
      predicateClass.prototype.cs = function (e) { return this.$fn(e) ? 1 : 0; };
    }
    var p = new predicateClass(); p.$fn = fn; return p;
  }
  M.predicate = predicate;
  function forEachEntity(list, fn) { var a = M.listToArray(list); for (var i = 0; i < a.length; i++) fn(a[i]); }

  // EntitySelectors.CAN_AI_TARGET / NOT_SPECTATING (engine predicates, eagerly initialised by the client's main)
  function canAITarget(e) { return !!KRu.cs(e); }
  function notSpectating(e) { return !!Kw_.cs(e); }

  // Entity class lookup for the instanceof/getClass checks of the mod
  function classIs(e, Cls) { return e !== null && e !== undefined && e.constructor === Cls; }

  // spawn data reader (IEntityAdditionalSpawnData.readSpawnData(ByteBuf)): big-endian, bounded
  M.ByteReader = function (bytes) { this.v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength); this.i = 0; this.n = bytes.byteLength; };
  M.ByteReader.prototype = {
    need: function (k) { if (this.i + k > this.n) throw new Error("spawn data too short"); var i = this.i; this.i += k; return i; },
    readInt: function () { return this.v.getInt32(this.need(4)); },
    readByte: function () { return this.v.getInt8(this.need(1)); },
    readUnsignedByte: function () { return this.v.getUint8(this.need(1)); },
    readBoolean: function () { return this.v.getUint8(this.need(1)) !== 0; },
    readShort: function () { return this.v.getInt16(this.need(2)); },
    readFloat: function () { return this.v.getFloat32(this.need(4)); },
    readDouble: function () { return this.v.getFloat64(this.need(8)); },
    readLong: function () { return this.v.getBigInt64(this.need(8)); },
    remaining: function () { return this.n - this.i; }
  };
  // BlockPos.fromLong (X 26 bits at 38, Y 12 bits at 26, Z 26 bits at 0)
  M.blockPosFromLong = function (v) {
    var x = Number(BigInt.asIntN(26, v >> 38n)), y = Number(BigInt.asIntN(12, v >> 26n)), z = Number(BigInt.asIntN(26, v));
    return Dy(x, y, z);
  };
  M.BLOCKPOS_ORIGIN = function () { return Dy(0, 0, 0); };

  // ---------------------------------------------------------------- vanilla parents
  var Entity = Eg, EntityMob = H0, EntityCreeper = Kw, EntityGolem = AHO, EntityTameable = S5,
    EntityShoulderRiding = BfX, EntityThrowable = Vh;
  var sEntity = M.superOf(Entity), sMob = M.superOf(EntityMob), sCreeper = M.superOf(EntityCreeper),
    sGolem = M.superOf(EntityGolem), sTameable = M.superOf(EntityTameable), sShoulder = M.superOf(EntityShoulderRiding),
    sThrowable = M.superOf(EntityThrowable);

  M.defineEntities = function () {
    CFG = M.CFG;
    var DSRC = DS(), PT = P();
    var BYTE = DATA.BYTE(), VARINT = DATA.VARINT(), BOOLEAN = DATA.BOOLEAN(), FLOAT = DATA.FLOAT(), OPT_UUID = DATA.OPTIONAL_UNIQUE_ID();
    var SND = M.SND;                                          // MBSoundEvents + vanilla SoundEvents (mutants-registry.js)
    var AP8_IJumpingMount = AP8;

    // ============================================================ MutantZombieEntity.java
    // Server-only, not translated: initEntityAI, applyEntityAttributes' AI attributes are kept (client attribute map),
    // getCanSpawnHere, createNavigator, attackEntityAsMob, updateAITasks, boss info, onKillEntity, NBT, loot, goals.
    var LIVES = DATA.createKey(12, VARINT), THROW_ATTACK_STATE = DATA.createKey(13, BYTE);
    var MutantZombieEntity = T.MutantZombieEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity", extend: EntityMob,
      fields: function (s) { s.attackID = 0; s.attackTick = 0; s.throwHitTick = 0; s.throwFinishTick = 0; s.vanishTime = 0; s.deathTime = 0; },
      methods: {
        applyEntityAttributes: function () {
          sMob.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.mutantZombieArmor);
          M.setBase(this, "ATTACK_DAMAGE", CFG.ENTITIES.mutantZombieAttackDamage);
          M.setBase(this, "FOLLOW_RANGE", CFG.ENTITIES.mutantZombieFollowRange);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.mutantZombieKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.mutantZombieMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.mutantZombieMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.mutantZombieSwimSpeed);
        },
        entityInit: function () {
          sMob.entityInit.call(this);
          DATA.register(this, LIVES, M.boxInt(CFG.ENTITIES.mutantZombieLives));
          DATA.register(this, THROW_ATTACK_STATE, M.boxByte(0));
        },
        getCreatureAttribute: function () { return M.ENUM.CreatureAttribute.UNDEAD; },
        updateDistance: function (f, f1) { return this.deathTime > 0 ? f1 : sMob.updateDistance.call(this, f, f1); },
        getEyeHeight: function () { return f(2.8); },
        canBePushed: function () { return bool(!this.isOnLadder()); },
        fall: function () {},
        applyPlayerInteraction: function (player, vec, hand) {
          var itemStack = player.getHeldItem(hand);
          if (!(M.itemOf(itemStack) !== M.ITEMS.FLINT_AND_STEEL || this.isEntityAlive() || this.isBurning() || this.isWet())) {
            this.setFire(8);
            player.swingArm(hand);
            M.stack.damageItem(itemStack, 1, player);
            W.playSound(this.world, player, this.posX, this.posY, this.posZ, SND.vanilla("item.flintandsteel.use"), this.getSoundCategory(), 1.0, R.nextFloat(this.rand) * 0.4 + 0.8);
            return M.ENUM.ActionResult.SUCCESS;
          }
          return M.ENUM.ActionResult.PASS;
        },
        attackEntityFrom: function (source, amount) {
          if (this.isEntityInvulnerable(source)) return 0;
          if (this.attackID === 3 && source !== DSRC.OUT_OF_WORLD) {
            if (this.attackTick < 10) return 0;
            if (!isUnblockable(source)) amount *= 0.15;
          }
          var entity;
          return bool(((entity = trueSource(source)) === null || this.attackID !== 2 || entity !== this.getAttackTarget()) && sMob.attackEntityFrom.call(this, source, amount));
        },
        handleStatusUpdate: function (id) {
          if (id <= 0) { this.attackID = Math.abs(id); this.attackTick = 0; }
          else sMob.handleStatusUpdate.call(this, id);
        },
        onUpdate: function () {
          sMob.onUpdate.call(this);
          this.fixRotation();
          this.updateAnimation();
          // updateMeleeGrounds: the seismic wave list is only filled by the server's MeleeGoal
          if (!W.isDaytime(this.world) && this.ticksExisted % 100 === 0 && this.isEntityAlive() && this.getHealth() < this.getMaxHealth()) this.heal(2.0);
          // resurrections: only filled by the server's RoarGoal
          if (this.getHealth() > 0.0) { this.deathTime = 0; this.vanishTime = 0; }
        },
        fixRotation: function () {
          var yaw;
          for (yaw = this.rotationYawHead - this.renderYawOffset; yaw < -180.0; yaw += 360.0) {}
          while (yaw >= 180.0) yaw -= 360.0;
          var offset = 0.1;
          if (this.attackID === 1) offset = 0.2;
          this.renderYawOffset += yaw * offset;
        },
        updateAnimation: function () {
          if (this.attackID !== 0) ++this.attackTick;
          if (isRemote(this)) {
            if (this.attackID === 2) {
              if (this.getThrowAttackHit()) { if (this.throwHitTick === -1) this.throwHitTick = 0; ++this.throwHitTick; }
              if (this.getThrowAttackFinish()) { if (this.throwFinishTick === -1) this.throwFinishTick = 0; ++this.throwFinishTick; }
            } else { this.throwHitTick = -1; this.throwFinishTick = -1; }
          }
        },
        getLives: function () { return M.unboxInt(DATA.get(this, LIVES)); },
        setLives: function (lives) { DATA.set(this, LIVES, M.boxInt(lives)); },
        getThrowAttackHit: function () { return (M.unboxByte(DATA.get(this, THROW_ATTACK_STATE)) & 1) !== 0; },
        getThrowAttackFinish: function () { return (M.unboxByte(DATA.get(this, THROW_ATTACK_STATE)) & 2) !== 0; },
        getAttackID: function () { return this.attackID; },
        getAttackTick: function () { return this.attackTick; },
        getRenderBoundingBox: function () { return De(this.getEntityBoundingBox(), 1.0, 1.0, 1.0); },
        canBeRidden: function (entityIn) { return bool(sMob.canBeRidden.call(this, entityIn) && entityIn instanceof Co); },
        isPushedByWater: function () { return 0; },
        onDeath: function () { /* server only: deathCause, status 3, recentlyHit */ },
        onDeathUpdate: function () {
          if (this.deathTime <= 25 || !this.isBurning() || this.deathTime >= 100) ++this.deathTime;
          if (this.isBurning()) ++this.vanishTime; else if (this.vanishTime > 0) --this.vanishTime;
          if (this.getLives() <= 0) this.livingDeathTime_ = this.deathTime;       // ((EntityMob) this).deathTime = deathTime
          if (this.deathTime >= 140) {
            this.deathTime = 0; this.vanishTime = 0;
            this.setLives(this.getLives() - 1);
            this.setHealth(Math.round(this.getMaxHealth() / 3.75));
            return;
          }
          if (this.vanishTime >= 100 || this.getLives() <= 0 && this.deathTime > 25) {
            EntityUtil.spawnParticleAtEntity(this, this.isBurning() ? PT.FLAME : PT.EXPLOSION_NORMAL, 30);
            this.setDead();
          }
        },
        handleJumpWater: function () { this.motionY += f(0.04); },
        handleJumpLava: function () { this.handleJumpWater(); },
        getHurtSound: function () { return SND.ENTITY_MUTANT_ZOMBIE_HURT; },
        getDeathSound: function () { return SND.ENTITY_MUTANT_ZOMBIE_DEATH; },
        getAmbientSound: function () { return SND.ENTITY_MUTANT_ZOMBIE_AMBIENT; },
        isNonBoss: function () { return 0; },                   // MBConfig mutantZombieBossClassification = true
        readSpawnData: function (buf) {
          this.attackID = buf.readInt(); this.attackTick = buf.readInt(); this.deathTime = buf.readInt();
          this.vanishTime = buf.readInt(); this.throwHitTick = buf.readInt(); this.throwFinishTick = buf.readInt();
        }
      }
    });
    MutantZombieEntity.init = function (e) {                 // field initializers + constructor body
      e.throwHitTick = -1; e.throwFinishTick = -1;
      e.stepHeight = 1.0; e.experienceValue = 30; e.setSize(1.8, 3.2);
    };
    MutantZombieEntity.create = function (world) { var e = new MutantZombieEntity(); F4$(e, world); MutantZombieEntity.init(e); return e; };

    // ============================================================ MutantSkeletonEntity.java
    // Server-only: AI goals, attackEntityAsMob, updateAITasks, boss info, the body-part burst of onDeath, loot, NBT.
    var MutantSkeletonEntity = T.MutantSkeletonEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity", extend: EntityMob,
      fields: function (s) { s.attackID = 0; s.attackTick = 0; },
      methods: {
        applyEntityAttributes: function () {
          sMob.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.mutantSkeletonArmor);
          M.setBase(this, "ATTACK_DAMAGE", CFG.ENTITIES.mutantSkeletonAttackDamage);
          M.setBase(this, "FOLLOW_RANGE", CFG.ENTITIES.mutantSkeletonFollowRange);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.mutantSkeletonKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.mutantSkeletonMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.mutantSkeletonMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.mutantSkeletonSwimSpeed);
        },
        getCreatureAttribute: function () { return M.ENUM.CreatureAttribute.UNDEAD; },
        getEyeHeight: function () { return f(3.25); },
        fall: function () {},
        handleStatusUpdate: function (id) {
          if (id <= 0) { this.attackID = Math.abs(id); this.attackTick = 0; }
          else sMob.handleStatusUpdate.call(this, id);
        },
        onLivingUpdate: function () {
          sMob.onLivingUpdate.call(this);
          if (this.attackID !== 0) ++this.attackTick;
          if (!W.isDaytime(this.world) && this.ticksExisted % 100 === 0 && this.getHealth() < this.getMaxHealth()) this.heal(2.0);
        },
        attackEntityFrom: function (source, amount) {
          return bool(!(trueSource(source) instanceof MutantSkeletonEntity) && sMob.attackEntityFrom.call(this, source, amount));
        },
        canBeRidden: function (entityIn) { return bool(sMob.canBeRidden.call(this, entityIn) && entityIn instanceof Co); },
        isPushedByWater: function () { return 0; },
        getAnimationID: function () { return this.attackID; },
        setAnimationID: function (id) { this.attackID = id; },
        getAnimationTick: function () { return this.attackTick; },
        setAnimationTick: function (tick) { this.attackTick = tick; },
        onDeath: function (cause) {
          sMob.onDeath.call(this, cause);
          // !world.isRemote: legacy death sound, area damage and the 18 BodyPartEntity pieces (server spawns them)
          this.livingDeathTime_ = 19;
        },
        handleJumpWater: function () { this.motionY += f(0.04); },
        handleJumpLava: function () { this.handleJumpWater(); },
        isNonBoss: function () { return 0; },
        getAmbientSound: function () { return CFG.ENTITIES.mutantSkeletonLegacyAmbientSound ? SND.ENTITY_MUTANT_SKELETON_AMBIENT_LEGACY : SND.ENTITY_MUTANT_SKELETON_AMBIENT; },
        getHurtSound: function () { return CFG.ENTITIES.mutantSkeletonLegacyHurtSound ? SND.ENTITY_MUTANT_SKELETON_HURT_LEGACY : SND.ENTITY_MUTANT_SKELETON_HURT; },
        getDeathSound: function () { return CFG.ENTITIES.mutantSkeletonLegacyDeathSound ? SND.ENTITY_MUTANT_SKELETON_DEATH_LEGACY : SND.ENTITY_MUTANT_SKELETON_DEATH; },
        // IAnimatedEntity default readSpawnData
        readSpawnData: function (buf) { this.setAnimationID(buf.readInt()); this.setAnimationTick(buf.readInt()); }
      }
    });
    MutantSkeletonEntity.init = function (e) { e.stepHeight = 1.0; e.experienceValue = 30; e.setSize(1.2, 3.6); };
    MutantSkeletonEntity.create = function (world) { var e = new MutantSkeletonEntity(); F4$(e, world); MutantSkeletonEntity.init(e); return e; };

    // ============================================================ MutantCreeperEntity.java
    // Server-only: AI goals, updateFallState explosion, attackEntityAsMob, updateAITasks, boss info, the explosion,
    // lingering cloud, loot, egg drop and death sound of onDeath/onDeathUpdate, getExplosionResistance, NBT.
    var STATUS = DATA.createKey(15, BYTE);
    var MutantCreeperEntity = T.MutantCreeperEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity", extend: EntityCreeper,
      fields: function (s) { s.chargeTime = 0; s.chargeHits = 0; s.lastFlashTick = 0; s.flashTick = 0; s.summonLightning = false; s.deathTime = 0; },
      methods: {
        applyEntityAttributes: function () {
          sCreeper.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.mutantCreeperArmor);
          M.setBase(this, "ATTACK_DAMAGE", CFG.ENTITIES.mutantCreeperAttackDamage);
          M.setBase(this, "FOLLOW_RANGE", CFG.ENTITIES.mutantCreeperFollowRange);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.mutantCreeperKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.mutantCreeperMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.mutantCreeperMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.mutantCreeperSwimSpeed);
        },
        entityInit: function () { sCreeper.entityInit.call(this); DATA.register(this, STATUS, M.boxByte(0)); },
        getPowered: function () { return (M.unboxByte(DATA.get(this, STATUS)) & 1) !== 0; },
        isJumpAttacking: function () { return (M.unboxByte(DATA.get(this, STATUS)) & 2) !== 0; },
        isCharging: function () { return (M.unboxByte(DATA.get(this, STATUS)) & 4) !== 0; },
        getEyeHeight: function () { return f(2.6); },
        fall: function () {},
        attackEntityFrom: function (source, amount) {
          if (this.isEntityInvulnerable(source)) return 0;
          if (isExplosion(source)) {
            var healAmount = amount / 2.0;
            if (this.isEntityAlive() && this.getHealth() < this.getMaxHealth() && !(trueSource(source) instanceof MutantCreeperEntity)) {
              this.heal(healAmount);
              // EntityUtil.sendParticlePacket: server only
            }
            return 0;
          }
          var flag = !(trueSource(source) instanceof EntityCreeper) && sCreeper.attackEntityFrom.call(this, source, amount);
          if (this.isCharging() && flag && amount > 0.0) --this.chargeHits;
          return bool(flag);
        },
        processInteract: function () { return 0; },
        handleStatusUpdate: function (id) {
          if (id === 6) EntityUtil.spawnParticleAtEntity(this, PT.HEART, 15);
          else sCreeper.handleStatusUpdate.call(this, id);
        },
        onUpdate: function () {
          sCreeper.onUpdate.call(this);
          this.lastFlashTick = this.flashTick;
          if (this.isJumpAttacking()) {
            if (this.flashTick === 0) this.playSound(SND.vanilla("entity.creeper.primed"), 2.0, this.getSoundPitch() * 0.5);
            ++this.flashTick;
          } else if (this.flashTick > 0) this.flashTick = 0;
        },
        canBeRidden: function (entityIn) { return bool(sCreeper.canBeRidden.call(this, entityIn) && entityIn instanceof Co); },
        isPushedByWater: function () { return 0; },
        getCreeperFlashIntensity: function (partialTick) {
          if (this.deathTime > 0) return this.deathTime / 100.0 * 255.0;
          if (this.isCharging()) return (this.ticksExisted % 20 < 10 ? 0.6 : 0.0) * 255.0;
          return (this.lastFlashTick + (this.flashTick - this.lastFlashTick) * partialTick) / 28.0;
        },
        onDeath: function () { /* server only */ },
        onDeathUpdate: function () {
          ++this.deathTime;
          var explosionPower = this.getPowered() ? CFG.ENTITIES.mutantCreeperDeathStrengthCharged : CFG.ENTITIES.mutantCreeperDeathStrength;
          var radius = explosionPower * 1.5, self = this;
          forEachEntity(W.getEntitiesInAABBexcluding(this.world, this, De(this.getEntityBoundingBox(), radius, radius, radius), KRu), function (entity) {
            var x = self.posX - entity.posX, y = self.posY - entity.posY, z = self.posZ - entity.posZ;
            var d = Math.sqrt(x * x + y * y + z * z);
            var f2 = self.deathTime / 100.0;
            entity.motionX += x / d * f2 * 0.09;
            entity.motionY += y / d * f2 * 0.09;
            entity.motionZ += z / d * f2 * 0.09;
          });
          this.posX += R.nextFloat(this.rand) * 0.2 - 0.1;
          this.posZ += R.nextFloat(this.rand) * 0.2 - 0.1;
          if (this.deathTime >= 100) this.setDead();
        },
        hasIgnited: function () { return false; },
        getCreeperState: function () { return -1; },
        handleJumpWater: function () { this.motionY += f(0.04); },
        handleJumpLava: function () { this.handleJumpWater(); },
        isNonBoss: function () { return 0; },
        getAmbientSound: function () { return SND.ENTITY_MUTANT_CREEPER_AMBIENT; },
        getHurtSound: function () { return SND.ENTITY_MUTANT_CREEPER_HURT; },
        getDeathSound: function () { return SND.ENTITY_MUTANT_CREEPER_HURT; },
        readSpawnData: function (buf) { this.flashTick = buf.readInt(); this.deathTime = buf.readInt(); }
      }
    });
    // EntityCreeper(World) is inlined into its factory in this client: Kw clinit, EntityMob(World), fuseTime = 30,
    // explosionRadius = 3, setSize(0.6, 1.7); then MutantCreeperEntity's constructor body.
    MutantCreeperEntity.create = function (world) {
      var e = new MutantCreeperEntity(); Zp(); F4$(e, world); e.fuseTime = 30; e.explosionRadius = 3; e.setSize(0.6, 1.7);
      e.chargeHits = 3 + R.nextInt(e.rand, 3);
      e.stepHeight = 1.0; e.experienceValue = 30; e.setSize(1.98, 2.8);
      return e;
    };

    // ============================================================ MutantEndermanEntity.java
    // Server-only: AI goals, teleport logic (teleportTo, teleportByChance), block placement/throwing, updateAITasks,
    // attackEntityAsMob, the drops and fragments of the death, boss info, NBT, loot.
    var ACTIVE_ARM = DATA.createKey(12, BYTE), CLONE = DATA.createKey(13, BOOLEAN);
    var MutantEndermanEntity = T.MutantEndermanEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity", extend: EntityMob,
      fields: function (s) {
        s.attackID = 0; s.attackTick = 0; s.prevArmScale = 0; s.armScale = 0; s.hasTarget = 0; s.teleportPosition = null;
        s.screamDelayTick = 0; s.heldBlock = null; s.heldBlockTick = null; s.capturedEntities = null; s.deathTime = 0;
      },
      methods: {
        applyEntityAttributes: function () {
          sMob.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.mutantEndermanArmor);
          M.setBase(this, "ATTACK_DAMAGE", CFG.ENTITIES.mutantEndermanAttackDamage);
          M.setBase(this, "FOLLOW_RANGE", CFG.ENTITIES.mutantEndermanFollowRange);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.mutantEndermanKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.mutantEndermanMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.mutantEndermanMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.mutantEndermanSwimSpeed);
        },
        entityInit: function () {
          sMob.entityInit.call(this);
          DATA.register(this, ACTIVE_ARM, M.boxByte(0));
          DATA.register(this, CLONE, M.boxBool(false));
        },
        getTeleportPosition: function () { return this.teleportPosition; },
        setTeleportPosition: function (pos) {
          this.teleportPosition = pos;
          this.attackID = 4;
          if (isRemote(this)) this.spawnTeleportParticles();
        },
        getActiveArm: function () { return M.unboxByte(DATA.get(this, ACTIVE_ARM)); },
        isClone: function () { return M.unboxBool(DATA.get(this, CLONE)); },
        getAttackID: function () { return this.attackID; },
        getAttackTick: function () { return this.attackTick; },
        setAttackID: function (attackID) { this.attackID = attackID; this.attackTick = 0; },
        getEyeHeight: function () { return this.isClone() ? f(2.55) : f(3.9); },
        getMaxFallHeight: function () { return this.isClone() ? 3 : sMob.getMaxFallHeight.call(this); },
        canBeCollidedWith: function () { return bool(sMob.canBeCollidedWith.call(this) && this.attackID !== 4); },
        notifyDataManagerChange: function (key) {
          sMob.notifyDataManagerChange.call(this, key);
          if (M.keyId(CLONE) === M.keyId(key)) {
            if (this.isClone()) this.setSize(0.6, 2.9); else this.setSize(1.2, 4.2);
          }
        },
        isAggressive: function () { return this.getFlag(2); },
        getArmScale: function (partialTicks) { return (this.prevArmScale + (this.armScale - this.prevArmScale) * partialTicks) / 10.0; },
        updateTargetTick: function () {
          this.prevArmScale = this.armScale;
          if (this.isAggressive()) this.hasTarget = 20;
          var emptyHanded = true;
          for (var i = 1; i < this.heldBlock.length; ++i) {
            if (this.heldBlock[i] > 0) emptyHanded = false;
            if (this.hasTarget > 0) {
              if (this.heldBlock[i] <= 0) continue;
              this.heldBlockTick[i] = Math.min(10, this.heldBlockTick[i] + 1);
              continue;
            }
            this.heldBlockTick[i] = Math.max(0, this.heldBlockTick[i] - 1);
          }
          if (this.hasTarget > 0) this.armScale = Math.min(10, this.armScale + 1);
          else if (emptyHanded) this.armScale = Math.max(0, this.armScale - 1);
          // else if (!world.isRemote): block placement / throw trigger (server)
          this.hasTarget = Math.max(0, this.hasTarget - 1);
        },
        updateScreamEntities: function () {
          this.screamDelayTick = Math.max(0, this.screamDelayTick - 1);
          if (this.attackID === 5 && this.attackTick >= 40 && this.attackTick <= 160) {
            if (this.attackTick === 160) this.capturedEntities = null;
            else if (this.capturedEntities === null) this.capturedEntities = M.listToArray(W.getEntitiesInAABBexcluding(this.world, this, De(this.getEntityBoundingBox(), 20.0, 12.0, 20.0), M.IS_VALID_TARGET));
            for (var i = 0; this.capturedEntities !== null && i < this.capturedEntities.length; ++i) {
              var entity = this.capturedEntities[i];
              if (this.getDistanceSqToEntity(entity) > 400.0 || !notSpectating(entity) || !isAddedToWorld(entity)) { this.capturedEntities.splice(i, 1); --i; continue; }
              if (this.attackTick === 40) {
                entity.attackEntityFrom(M.mobDamage(this, "screamMagicBypass"), 4.0);
                // potion effects on EntityLiving victims: server authoritative (the client copy of a mob shows none)
              }
              entity.rotationPitch += (R.nextFloat(this.rand) - 0.3) * 6.0;
            }
          }
        },
        handleStatusUpdate: function (id) {
          if (id <= 0) {
            this.setAttackID(Math.abs(id));
            if (this.attackID === 6) this.spawnTeleportParticles();
          } else sMob.handleStatusUpdate.call(this, id);
        },
        onLivingUpdate: function () {
          this.isJumping = 0;
          sMob.onLivingUpdate.call(this);
          if (this.attackID !== 0) ++this.attackTick;
          if (isRemote(this) && this.attackID === 5 && this.attackTick === 40) this.spawnTeleportParticles();
          if (this.attackID === 8) this.deathTime = this.attackTick;
          this.updateTargetTick();
          this.updateScreamEntities();
          if (isRemote(this) && !this.isClone()) {
            var h = this.attackID !== 8 ? this.height : this.height + 1.0;
            var w = this.attackID !== 8 ? this.width : this.width * 1.5;
            for (var i = 0; i < 3; ++i) {
              var x = this.posX + (R.nextDouble(this.rand) - 0.5) * w;
              var y = this.posY + R.nextDouble(this.rand) * h - 0.25;
              var z = this.posZ + (R.nextDouble(this.rand) - 0.5) * w;
              W.spawnParticle(this.world, PT.PORTAL, x, y, z, (R.nextDouble(this.rand) - 0.5) * 2.0, -R.nextDouble(this.rand), (R.nextDouble(this.rand) - 0.5) * 2.0, null);
            }
          }
        },
        collideWithNearbyEntities: function () { if (!this.isClone()) sMob.collideWithNearbyEntities.call(this); },
        attackEntityFrom: function (source, amount) {
          if (this.isEntityInvulnerable(source)) return 0;
          var ts = trueSource(source);
          if (ts instanceof Nx || ts instanceof MutantEndermanEntity) return 0;  // EntityDragon (Nx)
          if ((this.attackID === 4 || this.attackID === 5) && source !== DSRC.OUT_OF_WORLD) return 0;
          var damaged = sMob.attackEntityFrom.call(this, source, amount);
          if (damaged && (this.attackID === 3 || this.attackID === 6)) { this.attackID = 0; return damaged; }
          // !world.isRemote: teleport dodges
          return damaged;
        },
        isPotionApplicable: function (e) { return bool(!this.isClone() && sMob.isPotionApplicable.call(this, e)); },
        spawnTeleportParticles: function () {
          var temp = this.attackID === 4 ? 512 : 256;
          for (var i = 0; i < temp; ++i) {
            var f = (R.nextFloat(this.rand) - 0.5) * 1.8, f1 = (R.nextFloat(this.rand) - 0.5) * 1.8, f2 = (R.nextFloat(this.rand) - 0.5) * 1.8;
            var useCurrentPos = this.attackID !== 4 || i < (temp / 2 | 0);
            var tp = this.getTeleportPosition();
            var tempX = (useCurrentPos ? this.posX : tp.m) + (R.nextDouble(this.rand) - 0.5) * this.width;
            var tempY = (useCurrentPos ? this.posY : tp.i) + (R.nextDouble(this.rand) - 0.5) * this.height + 1.5;
            var tempZ = (useCurrentPos ? this.posZ : tp.l) + (R.nextDouble(this.rand) - 0.5) * this.width;
            W.spawnParticle(this.world, M.MBParticles.ENDERSOUL, tempX, tempY, tempZ, f, f1, f2, null);
          }
        },
        getRenderBoundingBox: function () { return De(this.getEntityBoundingBox(), 3.5, 3.5, 3.5); },
        canBeRidden: function (entityIn) { return bool(sMob.canBeRidden.call(this, entityIn) && entityIn instanceof Co); },
        isPushedByWater: function () { return 0; },
        onDeath: function (cause) { sMob.onDeath.call(this, cause); this.capturedEntities = null; },
        onDeathUpdate: function () {
          this.motionX = 0.0; this.motionY = Math.min(this.motionY, 0.0); this.motionZ = 0.0;
          if (this.deathTime === 80) this.playSound(SND.ENTITY_MUTANT_ENDERMAN_DEATH, 5.0, this.getSoundPitch());
          if (this.deathTime >= 60) {
            if (this.deathTime < 80 && this.capturedEntities === null) this.capturedEntities = M.listToArray(W.getEntitiesInAABBexcluding(this.world, this, De(this.getEntityBoundingBox(), 10.0, 8.0, 10.0), M.IS_VALID_TARGET));
            // !world.isRemote: endersoul fragments
          }
          if (this.deathTime >= 80 && this.deathTime < 260 && this.capturedEntities !== null) {
            for (var i = 0; i < this.capturedEntities.length; ++i) {
              var entity = this.capturedEntities[i];
              if (M.EndersoulFragmentIsProtected(entity) || !notSpectating(entity) || !isAddedToWorld(entity)) { this.capturedEntities.splice(i, 1); --i; continue; }
              if (entity.fallDistance > 4.5) entity.fallDistance = 4.5;
              if (!(this.getDistanceSqToEntity(entity) > 64.0)) continue;
              var x = this.posX - entity.posX, z = this.posZ - entity.posZ, d = Math.sqrt(x * x + z * z);
              entity.motionX = 0.800000011920929 * x / d;
              entity.motionZ = 0.800000011920929 * z / d;
              if (!(this.posY + 4.0 > entity.posY)) continue;
              entity.motionY = Math.max(entity.motionY, 0.4000000059604645);
            }
          }
          if (this.deathTime >= 280) this.setDead();
        },
        getName: function () { return this.isClone() ? M.I18n.translateToLocal("entity.mutantbeasts.endersoul_clone.name") : sMob.getName.call(this); },
        playLivingSound: function () { if (!this.isClone()) sMob.playLivingSound.call(this); },
        isNonBoss: function () { return 0; },
        getAmbientSound: function () { return SND.ENTITY_MUTANT_ENDERMAN_AMBIENT; },
        getHurtSound: function () { return SND.ENTITY_MUTANT_ENDERMAN_HURT; },
        getDeathSound: function () { return SND.ENTITY_MUTANT_ENDERMAN_HURT; },
        readSpawnData: function (buf) {
          this.attackID = buf.readInt(); this.attackTick = buf.readInt(); this.deathTime = buf.readInt();
          this.armScale = buf.readInt(); this.hasTarget = buf.readInt();
          this.teleportPosition = M.blockPosFromLong(buf.readLong());
        },
        sendHoldBlock: function (blockIndex, blockId) { this.heldBlock[blockIndex] = blockId; this.heldBlockTick[blockIndex] = 0; }
      }
    });
    MutantEndermanEntity.init = function (e) {
      e.teleportPosition = M.BLOCKPOS_ORIGIN(); e.heldBlock = [0, 0, 0, 0, 0]; e.heldBlockTick = [0, 0, 0, 0, 0];
      e.experienceValue = 40; e.stepHeight = 1.5; e.setSize(1.2, 4.2);
    };
    MutantEndermanEntity.create = function (world) { var e = new MutantEndermanEntity(); F4$(e, world); MutantEndermanEntity.init(e); return e; };

    // ============================================================ MutantSnowGolemEntity.java
    // Server-only: AI goals, ranged attack, snow/ice placement, heal in snow, owner home, shearing (IShearable),
    // death message, NBT, loot.
    var OWNER_UNIQUE_ID = DATA.createKey(12, OPT_UUID), DATA_FLAGS = DATA.createKey(13, BYTE);
    var MutantSnowGolemEntity = T.MutantSnowGolemEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.MutantSnowGolemEntity", extend: EntityGolem, ifaces: [M.IFACE.IRangedAttackMob, M.IFACE.IEntityOwnable],
      fields: function (s) { s.isThrowing_ = false; s.throwingTick = 0; },
      methods: {
        applyEntityAttributes: function () {
          sGolem.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.mutantSnowGolemArmor);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.mutantSnowGolemKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.mutantSnowGolemMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.mutantSnowGolemMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.mutantSnowGolemSwimSpeed);
        },
        entityInit: function () {
          sGolem.entityInit.call(this);
          DATA.register(this, OWNER_UNIQUE_ID, DATA.absent());
          DATA.register(this, DATA_FLAGS, M.boxByte(1));
        },
        getOwner: function () { var uuid = this.getOwnerId(); return uuid === null ? null : W.getPlayerEntityByUUID(this.world, uuid); },
        getOwnerId: function () { return M.optionalOrNull(DATA.get(this, OWNER_UNIQUE_ID)); },
        isPumpkinEquipped: function () { return (M.unboxByte(DATA.get(this, DATA_FLAGS)) & 1) !== 0; },
        getSwimJump: function () { return (M.unboxByte(DATA.get(this, DATA_FLAGS)) & 4) !== 0; },
        getEyeHeight: function () { return f(2.0); },
        onUpdate: function () {
          sGolem.onUpdate.call(this);
          if (isRemote(this) && this.getSwimJump()) {
            EntityUtil.spawnParticleAtEntity(this, PT.SNOWBALL, 6, M.intArray(0));
            EntityUtil.spawnParticleAtEntity(this, PT.WATER_SPLASH, 6, M.intArray(0));
          }
          if (this.isThrowing_ && this.throwingTick++ >= 20) { this.isThrowing_ = false; this.throwingTick = 0; }
          if (this.ticksExisted % 20 === 0 && this.isWet() && CFG.ENTITIES.mutantSnowGolemWaterWeakness) this.attackEntityFrom(DSRC.DROWN, 1.0);
          if (!!this.world.b4.bc8 && CFG.ENTITIES.mutantSnowGolemNetherWeakness) {    // world.provider.isNether()
            if (R.nextFloat(this.rand) > Math.min(80.0, this.getHealth()) * 0.01) {
              W.spawnParticle(this.world, PT.WATER_DROP, this.posX + R.nextFloat(this.rand) * this.width * 1.5 - this.width, this.posY - 0.15 + R.nextFloat(this.rand) * this.height, this.posZ + R.nextFloat(this.rand) * this.width * 1.5 - this.width, 0.0, 0.0, 0.0, M.intArray(0));
            }
            if (this.ticksExisted % 60 === 0) this.attackEntityFrom(DSRC.ON_FIRE, 1.0);
          }
        },
        isThrowing: function () { return this.isThrowing_; },
        getThrowingTick: function () { return this.throwingTick; },
        startThrowing: function () { this.isThrowing_ = true; this.throwingTick = 0; },
        handleStatusUpdate: function (id) {
          if (id === 0) this.startThrowing();
          else {
            sGolem.handleStatusUpdate.call(this, id);
            if (id === 2 || id === 33 || id === 36 || id === 37) EntityUtil.spawnParticleAtEntity(this, PT.SNOWBALL, 30, M.intArray(0));
          }
        },
        attackEntityFrom: function (source, amount) {
          if (immediateSource(source) instanceof TR) {                 // EntitySnowball
            if (this.getHealth() < this.getMaxHealth()) EntityUtil.spawnParticleAtEntity(this, PT.HEART, 1, M.intArray(0));
            return 0;
          }
          return sGolem.attackEntityFrom.call(this, source, amount);
        },
        processInteract: function (player, hand) {
          var itemStack = player.getHeldItem(hand);
          if (M.stack.interactWithEntity(itemStack, player, this, hand)) return 1;
          if ((this.getOwnerId() === null || player === this.getOwner()) && M.itemOf(itemStack) !== M.ITEMS.SNOWBALL) return 1;
          return 0;
        },
        isOnSameTeam: function (entityIn) {
          if (entityIn === this || entityIn === this.getOwner() || sGolem.isOnSameTeam.call(this, entityIn)) return 1;
          if (!M.isInstance(entityIn, M.IFACE.IMob)) {
            return bool(this.getAttackTarget() !== entityIn && (!(entityIn instanceof Gj) || entityIn.getAttackTarget() !== this) && this.getTeam() === null && entityIn.getTeam() === null);
          }
          return 0;
        },
        getHurtSound: function () { return SND.ENTITY_MUTANT_SNOW_GOLEM_HURT; },
        getDeathSound: function () { return SND.ENTITY_MUTANT_SNOW_GOLEM_DEATH; }
      }
    });
    // EntityGolem(World) in this client: EntityCreature init then nothing (B0Q is the snowman's factory helper)
    MutantSnowGolemEntity.create = function (world) {
      var e = new MutantSnowGolemEntity(); M.initEntityCreature(e, world);
      M.setPathPriority(e, "WATER", -1.0);
      e.setSize(1.1, 2.2);
      return e;
    };

    // ============================================================ SpiderPigEntity.java
    // Server-only: AI, web list, charge attack, leap, taming via kills, breeding child, NBT, loot, saddle drop.
    var CLIMBING = DATA.createKey(15, BOOLEAN);
    var SpiderPigEntity = T.SpiderPigEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity", extend: EntityTameable, ifaces: [AP8_IJumpingMount],
      fields: function (s) { s.leapCooldown = 0; s.leapTick = 0; s.isLeaping = false; s.chargePower = 0.0; s.chargingTick = 0; s.chargeExhaustion = 0; s.chargeExhausted = false; },
      methods: {
        applyEntityAttributes: function () {
          sTameable.applyEntityAttributes.call(this);
          M.setBase(this, "ARMOR", CFG.ENTITIES.spiderPigArmor);
          M.registerAttribute(this, "ATTACK_DAMAGE", CFG.ENTITIES.spiderPigAttackDamage);
          M.setBase(this, "KNOCKBACK_RESISTANCE", CFG.ENTITIES.spiderPigKnockbackResistance);
          M.setBase(this, "MAX_HEALTH", CFG.ENTITIES.spiderPigMaxHealth);
          M.setBase(this, "MOVEMENT_SPEED", CFG.ENTITIES.spiderPigMovementSpeed);
          M.setBase(this, "SWIM_SPEED", CFG.ENTITIES.spiderPigSwimSpeed);
        },
        entityInit: function () { sTameable.entityInit.call(this); DATA.register(this, CLIMBING, M.boxBool(false)); },
        isBesideClimbableBlock: function () { return M.unboxBool(DATA.get(this, CLIMBING)); },
        setBesideClimbableBlock: function (climbing) { DATA.set(this, CLIMBING, M.boxBool(climbing)); },
        isSaddled: function () { return (M.unboxByte(DATA.get(this, M.TAMED_KEY())) & 2) !== 0; },
        setSaddled: function (saddled) {
          var b0 = M.unboxByte(DATA.get(this, M.TAMED_KEY()));
          DATA.set(this, M.TAMED_KEY(), M.boxByte(saddled ? M.toByte(b0 | 2) : M.toByte(b0 & 0xFFFFFFFD)));
        },
        getCreatureAttribute: function () { return M.ENUM.CreatureAttribute.ARTHROPOD; },
        getEyeHeight: function () { return this.height * f(0.75); },
        isPotionApplicable: function (e) { return bool(M.potionOf(e) !== M.POTIONS.POISON && sTameable.isPotionApplicable.call(this, e)); },
        isBreedingItem: function (stack) { return bool(M.SPIDER_PIG_TEMPTATION_ITEMS().indexOf(M.itemOf(stack)) >= 0); },
        fall: function () {},
        onUpdate: function () {
          sTameable.onUpdate.call(this);
          this.setBesideClimbableBlock(!!this.collidedHorizontally);
          if (this.chargeExhaustion >= 120) this.chargeExhausted = true;
          if (this.chargeExhaustion <= 0) this.chargeExhausted = false;
          this.chargeExhaustion = Math.max(0, this.chargeExhaustion - 1);
          // !world.isRemote: leap cooldown, webs, charge state, tamed regen
        },
        processInteract: function (player, hand) {
          var itemstack = player.getHeldItem(hand);
          if (this.isTamed() && this.isOwner(player)) {
            var item = M.itemOf(itemstack);
            if (item instanceof HM && this.isBreedingItem(itemstack) && this.getHealth() < this.getMaxHealth()) {   // ItemFood
              this.heal(M.foodHealAmount(item, itemstack));
              this.consumeItemFromStack(player, itemstack);
              return 1;
            }
            if (item === M.ITEMS.SADDLE) {
              if (!(player.isSneaking() || this.isSaddled() || this.isChild())) {
                this.setSaddled(true);
                W.playSound(this.world, player, this.posX, this.posY, this.posZ, SND.vanilla("entity.pig.saddle"), M.ENUM.SoundCategory.NEUTRAL, 0.5, 1.0);
                this.consumeItemFromStack(player, itemstack);
                return 1;
              }
            } else if (this.isSaddled() && !this.isBeingRidden()) {
              if (!player.isSneaking()) return 1;            // the server mounts the player
              this.setSaddled(false);
              W.playSound(this.world, player, this.posX, this.posY, this.posZ, SND.vanilla("entity.pig.saddle"), M.ENUM.SoundCategory.NEUTRAL, 0.5, 1.0);
              return 1;
            }
          }
          return sTameable.processInteract.call(this, player, hand);
        },
        canJump: function () { return this.isSaddled() && !this.chargeExhausted && !!this.onGround && !this.collidedHorizontally; },
        setJumpPower: function (jumpPowerIn) {
          this.chargeExhaustion += M.idiv(50 * jumpPowerIn, 100);
          this.chargePower = jumpPowerIn / 100.0;
        },
        handleStartJump: function (jumpPowerIn) { this.chargingTick = M.idiv(8 * jumpPowerIn, 100); },
        handleStopJump: function () {},
        isMovementBlocked: function () { return bool(sTameable.isMovementBlocked.call(this) || this.isBeingRidden() && this.isSaddled()); },
        getControllingPassenger: function () { var p = this.getPassengers(); return M.listSize(p) === 0 ? null : M.listGet(p, 0); },
        canBeSteered: function () { return bool(this.getControllingPassenger() instanceof Co); },
        travel: function (strafe, vertical, forward) {
          if (this.isBeingRidden() && this.canBeSteered()) {
            var livingentity = this.getControllingPassenger();
            this.stepHeight = 1.0;
            this.rotationYaw = this.rotationYawHead = livingentity.rotationYaw;
            this.prevRotationYaw = this.rotationYawHead;
            this.prevRotationPitch = this.rotationPitch = livingentity.rotationPitch * 0.4;
            this.setRotation(this.rotationYaw, this.rotationPitch);
            while (this.renderYawOffset > this.rotationYawHead + 180.0) this.renderYawOffset -= 360.0;
            while (this.renderYawOffset < this.rotationYawHead - 180.0) this.renderYawOffset += 360.0;
            if (!this.chargeExhausted && this.chargePower > 0.0 && (this.onGround || this.collidedHorizontally)) {
              var pitch = this.rotationPitch;
              this.rotationPitch = 0.0;
              this.rotationPitch = pitch;
              var lookVec = this.getLookVec();
              var power = 1.600000023841858 * this.chargePower;
              this.motionX = lookVec.bh * power;
              this.motionY = 0.30000001192092896;
              this.motionZ = lookVec.bi * power;
              this.chargePower = 0.0;
            } else this.chargePower = 0.0;
            this.jumpMovementFactor = this.getAIMoveSpeed() * 0.1;
            if (this.canPassengerSteer()) {
              strafe = livingentity.moveStrafing * 0.8;
              forward = livingentity.moveForward * 0.6;
              this.setAIMoveSpeed(M.attributeValue(this, "MOVEMENT_SPEED"));
              sTameable.travel.call(this, strafe, vertical, forward);
            } else if (livingentity instanceof Cb) {
              this.motionX = 0.0; this.motionY = 0.0; this.motionZ = 0.0;
            } else {
              this.prevLimbSwingAmount = this.limbSwingAmount;
              var d1 = this.posX - this.prevPosX, d0 = this.posZ - this.prevPosZ;
              var f2 = MH.sqrt(d1 * d1 + d0 * d0) * 4.0;
              if (f2 > 1.0) f2 = 1.0;
              this.limbSwingAmount += (f2 - this.limbSwingAmount) * 0.4;
              this.limbSwing += this.limbSwingAmount;
            }
          } else {
            this.stepHeight = 0.6;
            this.jumpMovementFactor = 0.02;
            sTameable.travel.call(this, strafe, vertical, forward);
          }
        },
        canDespawn: function () { return bool(!this.isTamed()); },
        isOnLadder: function () { return bool(this.isBesideClimbableBlock()); },
        setInWeb: function () {},
        createChild: function () { return null; },          // breeding is server-side
        getAmbientSound: function () { return SND.ENTITY_SPIDER_PIG_AMBIENT; },
        getHurtSound: function () { return SND.ENTITY_SPIDER_PIG_HURT; },
        getDeathSound: function () { return SND.ENTITY_SPIDER_PIG_DEATH; }
      }
    });
    SpiderPigEntity.prototype.$jmJumpingMount = true;
    SpiderPigEntity.create = function (world) { var e = new SpiderPigEntity(); BHZ(e, world); e.setSize(1.4, 0.9); return e; };

    M.defineSmallEntities(T, { DSRC: DSRC, PT: PT, SND: SND, BYTE: BYTE, VARINT: VARINT, BOOLEAN: BOOLEAN, FLOAT: FLOAT, OPT_UUID: OPT_UUID,
      isRemote: isRemote, bool: bool, trueSource: trueSource, isExplosion: isExplosion, forEachEntity: forEachEntity,
      canAITarget: canAITarget, notSpectating: notSpectating, classIs: classIs, sEntity: sEntity, sMob: sMob, sShoulder: sShoulder, sThrowable: sThrowable });

    // ---------------- protocol table 1.1 (network type id = 210 + registration index)
    var defs = [
      ["body_part", T.BodyPartEntity, 0, 0, false], ["chemical_x", T.ChemicalXEntity, 0, 0, false],
      ["endersoul_clone", T.EndersoulCloneEntity, 15027455, 15027455, true], ["creeper_minion", T.CreeperMinionEntity, 894731, 0xB7B7B7, true],
      ["creeper_minion_egg", T.CreeperMinionEggEntity, 0, 0, false], ["endersoul_fragment", T.EndersoulFragmentEntity, 0, 0, false],
      ["mutant_arrow", T.MutantArrowEntity, 0, 0, false], ["mutant_creeper", MutantCreeperEntity, 5349438, 11013646, true],
      ["mutant_enderman", MutantEndermanEntity, 0x161616, 8860812, true], ["mutant_skeleton", MutantSkeletonEntity, 0xC1C1C1, 6310217, true],
      ["mutant_snow_golem", MutantSnowGolemEntity, 0xE5FFFF, 16753434, true], ["mutant_zombie", MutantZombieEntity, 7969893, 44975, true],
      ["skull_spirit", T.SkullSpiritEntity, 0, 0, false], ["spider_pig", SpiderPigEntity, 3419431, 15771042, true],
      ["throwable_block", T.ThrowableBlockEntity, 0, 0, false]
    ];
    M.ENTITY_TYPES.length = 0;
    for (var i = 0; i < defs.length; i++) M.ENTITY_TYPES.push({ id: 210 + i, name: defs[i][0], cls: defs[i][1], egg: defs[i][4] ? [defs[i][2], defs[i][3]] : null });
    return T;
  };
})(JasprMutants);
