/* Client twins of the smaller Mutant Creatures entities: BodyPartEntity, ChemicalXEntity, CreeperMinionEntity,
 * CreeperMinionEggEntity, EndersoulCloneEntity, EndersoulFragmentEntity, MutantArrowEntity, SkullSpiritEntity,
 * ThrowableBlockEntity (chumbanotz/mutantbeasts/entity/**). Same rules as mutants-entities.js. */
(function (M) {
  "use strict";
  var f = M.f, MH = M.MH, R = M.R, W = M.W, DATA = M.DATA;

  // EndersoulFragmentEntity.IS_VALID_TARGET and isProtected (used by the mutant enderman's client death/scream pulls)
  M.EndersoulFragmentIsProtected = function (entity) {
    if (!(entity instanceof Co)) return false;
    var hand = M.ITEMS_MB ? M.ITEMS_MB.ENDERSOUL_HAND : null;
    return hand !== null && (M.itemOf(entity.getHeldItemMainhand()) === hand || M.itemOf(entity.getHeldItemOffhand()) === hand);
  };
  M.mobDamage = function (entity, kind) {                     // DamageSource.causeMobDamage(entity)[.setDamageBypassesArmor().setMagicDamage()]
    var src = RN(entity);
    if (kind === "screamMagicBypass") src = BdM(Q_(src));
    return src;
  };

  M.defineSmallEntities = function (T, X) {
    var CFG = M.CFG, PT = X.PT, SND = X.SND, DSRC = X.DSRC, bool = X.bool, isRemote = X.isRemote, trueSource = X.trueSource;
    var EntityUtil = M.EntityUtil;
    var Entity = Eg, EntityMob = H0, EntityShoulderRiding = BfX, EntityThrowable = Vh;
    var sEntity = X.sEntity, sMob = X.sMob, sShoulder = X.sShoulder, sThrowable = X.sThrowable;

    M.IS_VALID_TARGET = M.predicate(function (entity) {
      var c = entity.constructor;
      return X.canAITarget(entity) && c !== GC && c !== Jo && c !== Iz && c !== T.EndersoulCloneEntity && c !== T.EndersoulFragmentEntity && c !== T.MutantEndermanEntity && c !== Nx && c !== Nd;
    });

    // ============================================================ BodyPartEntity.java
    // Server-only: the constructor with an owner, burning damage to entities, despawn, item drop on interaction.
    var BodyPartEntity = T.BodyPartEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.BodyPartEntity", extend: Entity,
      fields: function (s) { s.part = 0; s.yawPositive = false; s.pitchPositive = false; s.velocityX = 0.0; s.velocityY = 0.0; s.velocityZ = 0.0; s.despawnTimer = 0; },
      methods: {
        entityInit: function () {},
        getPart: function () { return this.part; },
        canTriggerWalking: function () { return 0; },
        canBeCollidedWith: function () { return bool(!this.isDead); },
        setPositionAndRotationDirect: function (x, y, z) {
          this.setPosition(x, y, z);
          this.motionX = this.velocityX; this.motionY = this.velocityY; this.motionZ = this.velocityZ;
        },
        setVelocity: function (x, y, z) {
          this.velocityX = x; this.velocityY = y; this.velocityZ = z;
          this.motionX = this.velocityX; this.motionY = this.velocityY; this.motionZ = this.velocityZ;
        },
        onUpdate: function () {
          sEntity.onUpdate.call(this);
          this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
          if (!this.hasNoGravity()) this.motionY -= 0.045;
          this.moveEntity(M.MOVER_SELF(), this.motionX, this.motionY, this.motionZ);
          this.motionX *= 0.96; this.motionY *= 0.96; this.motionZ *= 0.96;
          if (this.onGround) { this.motionX *= 0.7; this.motionY *= 0.7; this.motionZ *= 0.7; }
          if (!this.onGround && !this.isInWeb) {
            this.rotationYaw += 10.0 * (this.yawPositive ? 1 : -1);
            this.rotationPitch += 15.0 * (this.pitchPositive ? 1 : -1);
            var self = this;
            X.forEachEntity(W.getEntitiesInAABBexcluding(this.world, this, this.getEntityBoundingBox(), M.predicate(function (e) { return self.canHarm(e); })), function (entity) {
              entity.attackEntityFrom(Bgf(self, self), 4.0 + R.nextInt(self.rand, 4));    // client: no effect
            });
            if (this.despawnTimer > 0) --this.despawnTimer;
          } else ++this.despawnTimer;
        },
        processInitialInteract: function (player, hand) {
          if (CFG.ENTITIES.mutantSkeletonBoneDrops) { player.swingArm(hand); this.setDead(); }
          return 1;
        },
        canHarm: function (entity) { return !!entity.canBeCollidedWith() && !(entity instanceof T.MutantSkeletonEntity); },
        getItemByPart: function () {
          var I = M.ITEMS_MB;
          if (this.part === 0) return I.MUTANT_SKELETON_PELVIS;
          if (this.part >= 1 && this.part < 19) return I.MUTANT_SKELETON_RIB;
          if (this.part === 19) return I.MUTANT_SKELETON_SKULL;
          if (this.part >= 21 && this.part < 29) return I.MUTANT_SKELETON_LIMB;
          if (this.part === 29 || this.part === 30) return I.MUTANT_SKELETON_SHOULDER_PAD;
          return M.vanillaItem("air");
        },
        getName: function () { return this.hasCustomName() ? this.getCustomNameTag() : M.I18n.translateToLocal(M.ustr(M.itemTranslationKey(this.getItemByPart())) + ".name"); },
        readSpawnData: function (buf) { this.part = buf.readByte(); }
      }
    });
    BodyPartEntity.create = function (world) {
      var e = new BodyPartEntity(); BnV(e, world);
      e.prevRotationYaw = e.rotationYaw = R.nextFloat(e.rand) * 360.0;
      e.prevRotationPitch = e.rotationPitch = R.nextFloat(e.rand) * 360.0;
      e.yawPositive = R.nextBoolean(e.rand); e.pitchPositive = R.nextBoolean(e.rand);
      e.setSize(0.7, 0.7);
      return e;
    };

    // ============================================================ ChemicalXEntity.java
    // Server-only: onImpact's target search, skull spirit spawn and sound (client returns at !world.isRemote).
    var ChemicalXEntity = T.ChemicalXEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity", extend: EntityThrowable,
      methods: {
        getGravityVelocity: function () { return 0.05; },
        handleStatusUpdate: function (id) {
          if (id === 3) {
            var x, y, z, i;
            for (i = R.nextInt(this.rand, 5); i < 50; ++i) {
              x = (R.nextFloat(this.rand) - 0.5) * 1.2; y = R.nextFloat(this.rand) * 0.2; z = (R.nextFloat(this.rand) - 0.5) * 1.2;
              W.spawnParticle(this.world, M.MBParticles.SKULL_SPIRIT, this.posX, this.posY, this.posZ, x, y, z, M.intArray(0));
            }
            for (i = 5 + R.nextInt(this.rand, 3); i >= 0; --i) {
              x = (R.nextFloat(this.rand) - R.nextFloat(this.rand)) * 0.3; y = 0.1 + R.nextFloat(this.rand) * 0.1; z = (R.nextFloat(this.rand) - R.nextFloat(this.rand)) * 0.3;
              W.spawnParticle(this.world, PT.ITEM_CRACK, this.posX, this.posY, this.posZ, x, y, z, M.intArrayOf([C_q(M.ITEMS_MB.CHEMICAL_X)]));
            }
          }
        },
        onImpact: function (result) {
          // block hits without a collision box are ignored; the rest happens on the server
        }
      }
    });
    ChemicalXEntity.create = function (world) { var e = new ChemicalXEntity(); FTZ(e, world); return e; };

    // ============================================================ CreeperMinionEntity.java
    // Server-only: AI, explosion, taming messages, sitting toggle, NBT, loot, shoulder landing goal.
    var CREEPER_MINION_FLAGS = DATA.createKey(15, X.BYTE), EXPLODE_STATE = DATA.createKey(16, X.VARINT), EXPLOSION_RADIUS = DATA.createKey(17, X.FLOAT);
    var CreeperMinionEntity = T.CreeperMinionEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.CreeperMinionEntity", extend: EntityShoulderRiding,
      fields: function (s) { s.lastActiveTime = 0; s.timeSinceIgnited = 0; s.fuseTime_ = 0; },
      methods: {
        applyEntityAttributes: function () {
          sShoulder.applyEntityAttributes.call(this);
          M.setBase(this, "MAX_HEALTH", 4.0);
          M.setBase(this, "MOVEMENT_SPEED", 0.25);
        },
        entityInit: function () {
          sShoulder.entityInit.call(this);
          DATA.register(this, CREEPER_MINION_FLAGS, M.boxByte(0));
          DATA.register(this, EXPLODE_STATE, M.boxInt(-1));
          DATA.register(this, EXPLOSION_RADIUS, M.boxFloat(20.0));
        },
        getOwner: function () {
          var uuid = this.getOwnerId();
          if (uuid === null) return null;
          var entity = W.getPlayerEntityByUUID(this.world, uuid);
          return entity instanceof Co ? entity : null;
        },
        getExplodeState: function () { return M.unboxInt(DATA.get(this, EXPLODE_STATE)); },
        setExplodeState: function (state) { DATA.set(this, EXPLODE_STATE, M.boxInt(state)); },
        flags_: function () { return M.unboxByte(DATA.get(this, CREEPER_MINION_FLAGS)); },
        setFlagBit_: function (bit, on) { var b0 = this.flags_(); DATA.set(this, CREEPER_MINION_FLAGS, M.boxByte(on ? M.toByte(b0 | bit) : M.toByte(b0 & ~bit))); },
        getPowered: function () { return (this.flags_() & 1) !== 0; },
        setPowered: function (p) { this.setFlagBit_(1, p); },
        hasIgnited: function () { return (this.flags_() & 4) !== 0; },
        canExplodeContinuously: function () { return (this.flags_() & 8) !== 0; },
        setCanExplodeContinuously: function (c) { this.setFlagBit_(8, c); },
        canDestroyBlocks: function () { return (this.flags_() & 0x10) !== 0; },
        setDestroyBlocks: function (d) { this.setFlagBit_(0x10, d); },
        canRideOnShoulder: function () { return (this.flags_() & 0x20) !== 0; },
        getExplosionRadius: function () { return M.unboxFloat(DATA.get(this, EXPLOSION_RADIUS)) / 10.0; },
        setExplosionRadius: function (radius) { DATA.set(this, EXPLOSION_RADIUS, M.boxFloat(radius * 10.0)); },
        isChild: function () { return 0; },
        getAttackTarget: function () {
          if (!this.isTamed()) {
            var owner = this.getOwner();
            return owner instanceof T.MutantCreeperEntity ? owner.getAttackTarget() : EdM(this);
          }
          return EdM(this);
        },
        onUpdate: function () {
          if (this.isEntityAlive()) {
            var i;
            this.lastActiveTime = this.timeSinceIgnited;
            if (this.hasIgnited()) this.setExplodeState(1);
            if ((i = this.getExplodeState()) > 0 && this.timeSinceIgnited === 0) this.playSound(SND.ENTITY_CREEPER_MINION_PRIMED, 1.0, this.getSoundPitch());
            this.timeSinceIgnited += i;
            if (this.timeSinceIgnited < 0) this.timeSinceIgnited = 0;
            if (this.timeSinceIgnited >= this.fuseTime_) {
              this.timeSinceIgnited = 0;
              this.setExplodeState(-this.fuseTime_);
            }
            var target = this.getAttackTarget();
            if (this.motionX * this.motionY * this.motionZ > 0.800000011920929 && target !== null && M.aabb.intersects(M.aabb.grow(M.aabb.expand(this.getEntityBoundingBox(), this.motionX, this.motionY, this.motionZ), 0.5), target.getEntityBoundingBox())) {
              this.timeSinceIgnited = this.fuseTime_;
            }
          }
          sShoulder.onUpdate.call(this);
        },
        getCreeperFlashIntensity: function (partialTicks) { return (this.lastActiveTime + (this.timeSinceIgnited - this.lastActiveTime) * partialTicks) / (this.fuseTime_ - 2); },
        processInteract: function (player, hand) {
          var itemstack = player.getHeldItem(hand), item = M.itemOf(itemstack), I = M.ITEMS;
          if (M.stack.interactWithEntity(itemstack, player, this, hand)) return 1;
          if (this.isTamed()) {
            if (item === M.ITEMS_MB.CREEPER_MINION_TRACKER) {
              M.openGui(0, this);                             // player.openGui(MutantBeasts.INSTANCE, 0, world, id, 0, 0): client side
              return 1;
            }
            if (!this.isOwner(player)) return 0;
            if (item === I.GUNPOWDER) {
              if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0);
                EntityUtil.spawnParticleAtEntity(this, PT.HEART, 1, M.intArray(0));
                M.stack.shrink(itemstack, 1);
                return 1;
              }
              if (!(this.getMaxHealth() < 20.0)) return 0;
              EntityUtil.spawnParticleAtEntity(this, PT.HEART, 1, M.intArray(0));
              M.setBase(this, "MAX_HEALTH", this.getMaxHealth() + 1.0);
              M.stack.shrink(itemstack, 1);
              return 1;
            }
            if (item === I.TNT) {
              if (this.canExplodeContinuously()) {
                var explosionRadius = this.getExplosionRadius();
                if (!(explosionRadius < 4.0)) return 0;
                this.forcedAgeTimer += 5;
                this.setExplosionRadius(explosionRadius + 0.11);
                M.stack.shrink(itemstack, 1);
                return 1;
              }
              this.forcedAgeTimer += 15;
              this.setCanExplodeContinuously(true);
              M.stack.shrink(itemstack, 1);
              return 1;
            }
            return 1;                                         // world.isRemote: the sitting toggle is the server's
          }
          if (item === I.FLINT_AND_STEEL && !this.hasIgnited()) {
            W.playSound(this.world, player, this.posX, this.posY, this.posZ, SND.vanilla("item.flintandsteel.use"), this.getSoundCategory(), 1.0, R.nextFloat(this.rand) * 0.4 + 0.8);
            player.swingArm(hand);
            return 1;                                         // world.isRemote
          }
          if (!player.isCreative() || this.getOwner() !== null || item !== M.ITEMS_MB.CREEPER_MINION_TRACKER) return 0;
          return 1;                                           // world.isRemote
        },
        attackEntityFrom: function (source, amount) {
          if (X.isExplosion(source)) {
            if (this.isTamed()) return 0;
            if (amount >= 2.0) amount = 2.0;
          }
          if (this.aiSit !== null) M.aiSitSetSitting(this.aiSit, false);   // EntityAISit.setSitting(false); aiSit is server-only (null here)
          return sShoulder.attackEntityFrom.call(this, source, amount);
        },
        isImmuneToExplosions: function () { return bool(this.isTamed()); },
        canBeLeashedTo: function () { return bool(!this.getLeashed() && this.isTamed()); },
        canDespawn: function () { return bool(!this.isTamed()); },
        getTeam: function () { var owner = this.getOwner(); return owner !== null ? owner.getTeam() : sShoulder.getTeam.call(this); },
        isOnSameTeam: function (entityIn) {
          var owner = this.getOwner();
          return bool(owner !== null && (entityIn === owner || owner.isOnSameTeam(entityIn)) || sShoulder.isOnSameTeam.call(this, entityIn));
        },
        isBreedingItem: function () { return 0; },
        createChild: function () { return null; },
        getSoundPitch: function () { return (R.nextFloat(this.rand) - R.nextFloat(this.rand)) * 0.2 + 1.5; },
        getAmbientSound: function () { return SND.ENTITY_CREEPER_MINION_AMBIENT; },
        getHurtSound: function () { return SND.ENTITY_CREEPER_MINION_HURT; },
        getDeathSound: function () { return SND.ENTITY_CREEPER_MINION_DEATH; },
        getSoundCategory: function () { return this.isTamed() ? M.ENUM.SoundCategory.NEUTRAL : M.ENUM.SoundCategory.HOSTILE; }
      }
    });
    CreeperMinionEntity.create = function (world) {
      var e = new CreeperMinionEntity(); BHZ(e, world);       // EntityShoulderRiding(World) -> EntityTameable(World)
      e.fuseTime_ = 26;
      e.setDestroyBlocks(true);
      e.setSize(0.3, 0.84);
      return e;
    };

    // ============================================================ CreeperMinionEggEntity.java
    // Server-only: health, hatching age, explosion damage, owner, hatch, drops, NBT.
    var CHARGED = DATA.createKey(6, X.BOOLEAN);
    var CreeperMinionEggEntity = T.CreeperMinionEggEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.CreeperMinionEggEntity", extend: Entity, ifaces: [M.IFACE.IEntityOwnable],
      fields: function (s) { s.health = 0; s.age = 0; s.recentlyHit = 0; s.velocityX = 0.0; s.velocityY = 0.0; s.velocityZ = 0.0; s.ownerUUID = null; },
      methods: {
        entityInit: function () { DATA.register(this, CHARGED, M.boxBool(false)); },
        isCharged: function () { return M.unboxBool(DATA.get(this, CHARGED)); },
        getYOffset: function () {
          var riding = this.getRidingEntity();
          if (riding instanceof Cb) return this.height - (riding.isSneaking() ? 0.3 : 0.2);
          return 0.0;
        },
        getMountedYOffset: function () { return this.height; },
        canTriggerWalking: function () { return 0; },
        getCollisionBox: function (entity) { return entity.canBePushed() ? entity.getEntityBoundingBox() : null; },
        canBeCollidedWith: function () { return bool(!this.isDead); },
        canBePushed: function () { return bool(!this.isDead); },
        canRiderInteract: function () { return true; },
        hitByEntity: function (entityIn) { return bool(entityIn === this.getRidingEntity()); },
        setPositionAndRotationDirect: function (x, y, z, yaw, pitch, inc, teleport) {
          sEntity.setPositionAndRotationDirect.call(this, x, y, z, yaw, pitch, inc, teleport);
          this.motionX = this.velocityX; this.motionY = this.velocityY; this.motionZ = this.velocityZ;
        },
        setVelocity: function (x, y, z) {
          sEntity.setVelocity.call(this, x, y, z);
          this.velocityX = x; this.velocityY = y; this.velocityZ = z;
        },
        onUpdate: function () {
          sEntity.onUpdate.call(this);
          this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
          if (!this.hasNoGravity()) this.motionY -= 0.03999999910593033;
          this.moveEntity(M.MOVER_SELF(), this.motionX, this.motionY, this.motionZ);
          this.motionX *= 0.9800000190734863; this.motionY *= 0.9800000190734863; this.motionZ *= 0.9800000190734863;
          if (this.onGround) { this.motionX *= 0.699999988079071; this.motionZ *= 0.699999988079071; }
          var riding = this.getRidingEntity();
          if (this.isRiding() && (this.isEntityInsideOpaqueBlock() || riding instanceof Cb && (riding.isElytraFlying() || riding.isPlayerSleeping()))) {
            this.playMountSound(false);
            this.dismountRidingEntity();
          }
        },
        processInitialInteract: function (player, hand) {
          if (this.isRiding() && player === this.getRidingEntity()) {
            this.playMountSound(false);
            this.getEntityToRide(player).dismountRidingEntity();
            return 1;
          }
          if (!player.isElytraFlying()) {
            this.startRiding2(this.getEntityToRide(player), 1);
            this.playMountSound(true);
            return 1;
          }
          return 0;
        },
        getEntityToRide: function (entity) {
          var passengers = entity.getPassengers();
          return M.listSize(passengers) !== 0 ? this.getEntityToRide(M.listGet(passengers, 0)) : entity;
        },
        playMountSound: function (mount) { this.playSound(SND.vanilla("entity.item.pickup"), 0.7, (mount ? 0.6 : 0.3) + R.nextFloat(this.rand) * 0.1); },
        attackEntityFrom: function (source) {
          if (this.isEntityInvulnerable(source) || trueSource(source) === this.getRidingEntity()) return 0;
          return 0;                                           // the rest is server-side (!world.isRemote)
        },
        getOwner: function () { return this.ownerUUID === null ? null : W.getPlayerEntityByUUID(this.world, this.ownerUUID); },
        getOwnerId: function () { return this.ownerUUID; }
      }
    });
    CreeperMinionEggEntity.create = function (world) {
      var e = new CreeperMinionEggEntity(); BnV(e, world);
      e.health = 8; e.age = (60 + R.nextInt(e.rand, 40)) * 1200;
      e.preventEntitySpawning = 1;
      e.setSize(0.5625, 0.75);
      return e;
    };

    // ============================================================ EndersoulCloneEntity.java
    // Server-only: cloner link, AI, teleporting, damage handling, loot, team via cloner.
    var EndersoulCloneEntity = T.EndersoulCloneEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.EndersoulCloneEntity", extend: EntityMob,
      fields: function (s) { s.cloner = null; },
      methods: {
        applyEntityAttributes: function () {
          sMob.applyEntityAttributes.call(this);
          M.setBase(this, "MAX_HEALTH", 1.0);
          M.setBase(this, "ATTACK_DAMAGE", 1.0);
          M.setBase(this, "MOVEMENT_SPEED", 0.3);
        },
        getEyeHeight: function () { return f(2.55); },
        getMaxFallHeight: function () { return 3; },
        isAggressive: function () { return this.getFlag(2); },
        handleStatusUpdate: function (id) {
          sMob.handleStatusUpdate.call(this, id);
          if (id === 0) EntityUtil.spawnEndersoulParticles(this, 256, 1.8);
        },
        onLivingUpdate: function () {
          this.isJumping = 0;
          sMob.onLivingUpdate.call(this);
          if (this.cloner !== null && (!this.cloner.isEntityAlive() || this.cloner.isAIDisabled() || this.cloner.world !== this.world)) this.setDead();
        },
        attackEntityFrom: function (source) {
          if (this.isEntityInvulnerable(source) || trueSource(source) instanceof Nx || trueSource(source) instanceof T.MutantEndermanEntity) return 0;
          return 0;                                           // !world.isRemote && ... : client never damages
        },
        collideWithNearbyEntities: function () {},
        canBeHitWithPotion: function () { return 0; },
        setDead: function () {
          sMob.setDead.call(this);
          W.setEntityState(this.world, this, 0);              // empty on the client
          this.playSound(this.getDeathSound(), this.getSoundVolume(), this.getSoundPitch());
        },
        getHurtSound: function () { return null; },
        getDeathSound: function () { return SND.ENTITY_ENDERSOUL_CLONE_DEATH; },
        isEntityEqual: function (entityIn) { return bool(sMob.isEntityEqual.call(this, entityIn) || entityIn === this.cloner); },
        getTeam: function () { return this.cloner !== null ? this.cloner.getTeam() : sMob.getTeam.call(this); },
        isOnSameTeam: function (entityIn) { return bool(this.cloner !== null && (this.cloner === entityIn || this.cloner.isOnSameTeam(entityIn)) || sMob.isOnSameTeam.call(this, entityIn)); }
      }
    });
    EndersoulCloneEntity.create = function (world) {
      var e = new EndersoulCloneEntity(); F4$(e, world);
      e.stepHeight = 1.0; e.experienceValue = R.nextInt(e.rand, 2);
      DZ(); CUc(e, Lgf, -1.0); CUc(e, Lge, -1.0);             // PathNodeType.DAMAGE_FIRE, DANGER_FIRE
      e.setSize(0.6, 2.9);
      return e;
    };

    // ============================================================ EndersoulFragmentEntity.java
    // Server-only: explosion, owner following velocity, NBT. The owner/tamed interaction runs on both sides.
    var TAMED = DATA.createKey(6, X.BOOLEAN);
    var EndersoulFragmentEntity = T.EndersoulFragmentEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.EndersoulFragmentEntity", extend: Entity,
      fields: function (s) { s.explodeTick = 0; s.stickRotations = null; s.owner = null; },
      methods: {
        entityInit: function () { DATA.register(this, TAMED, M.boxBool(false)); },
        getOwner: function () { return this.owner; },
        isTamed: function () { return M.unboxBool(DATA.get(this, TAMED)); },
        canTriggerWalking: function () { return 0; },
        canBeCollidedWith: function () { return bool(this.isEntityAlive()); },
        canBePushed: function () { return bool(this.isEntityAlive()); },
        handleStatusUpdate: function (id) { if (id === 3) EntityUtil.spawnEndersoulParticles(this, 64, 0.8); },
        onUpdate: function () {
          sEntity.onUpdate.call(this);
          this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
          if (this.owner === null && this.motionY > -0.05000000074505806 && !this.hasNoGravity()) this.motionY = Math.max(-0.05000000074505806, this.motionY - 0.10000000149011612);
          if (!(this.owner === null || this.owner.isEntityAlive() && M.isAddedToWorld(this.owner) && this.world === this.owner.world)) this.owner = null;
          this.moveEntity(M.MOVER_SELF(), this.motionX, this.motionY, this.motionZ);
          this.motionX *= 0.9; this.motionY *= 0.9; this.motionZ *= 0.9;
        },
        processInitialInteract: function (player) {
          if (this.isTamed()) {
            if (this.owner === null && !player.isSneaking()) { this.owner = player; this.playSound(SND.vanilla("entity.experience_orb.pickup"), 1.0, 1.0); return 1; }
            if (this.owner === player && player.isSneaking()) { this.owner = null; this.playSound(SND.vanilla("entity.experience_orb.pickup"), 1.0, 1.5); return 1; }
            return 0;
          }
          this.owner = player;
          this.playSound(SND.vanilla("entity.player.levelup"), 1.0, 1.5);
          return 1;
        },
        attackEntityFrom: function (source) { if (this.isEntityInvulnerable(source)) return 0; return 1; },
        getSoundCategory: function () { return this.isTamed() ? M.ENUM.SoundCategory.NEUTRAL : M.ENUM.SoundCategory.HOSTILE; }
      }
    });
    EndersoulFragmentEntity.create = function (world) {
      var e = new EndersoulFragmentEntity(); BnV(e, world);
      e.explodeTick = 20 + R.nextInt(e.rand, 20);
      e.stickRotations = [];
      e.preventEntitySpawning = 1;
      for (var i = 0; i < 8; ++i) { e.stickRotations.push([0.0, 0.0, 0.0]); for (var j = 0; j < 3; ++j) e.stickRotations[i][j] = R.nextFloat(e.rand) * 2.0 * Math.PI; }
      e.setSize(0.75, 0.75);
      return e;
    };

    // ============================================================ MutantArrowEntity.java
    // Server-only: hitEntities / handleEntities (damage, potion effects).
    var TARGET_X = DATA.createKey(6, X.FLOAT), TARGET_Y = DATA.createKey(7, X.FLOAT), TARGET_Z = DATA.createKey(8, X.FLOAT),
      SPEED = DATA.createKey(9, X.FLOAT), CLONES = DATA.createKey(10, X.VARINT);
    var MutantArrowEntity = T.MutantArrowEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.projectile.MutantArrowEntity", extend: Entity,
      fields: function (s) { s.damage = 0.0; s.pointedEntities = null; s.potionEffect = null; s.shooter = null; },
      methods: {
        entityInit: function () {
          DATA.register(this, TARGET_X, M.boxFloat(0.0)); DATA.register(this, TARGET_Y, M.boxFloat(0.0)); DATA.register(this, TARGET_Z, M.boxFloat(0.0));
          DATA.register(this, SPEED, M.boxFloat(2.0)); DATA.register(this, CLONES, M.boxInt(10));
        },
        getTargetX: function () { return M.unboxFloat(DATA.get(this, TARGET_X)); },
        getTargetY: function () { return M.unboxFloat(DATA.get(this, TARGET_Y)); },
        getTargetZ: function () { return M.unboxFloat(DATA.get(this, TARGET_Z)); },
        getSpeed: function () { return M.unboxFloat(DATA.get(this, SPEED)); },
        getClones: function () { return M.unboxInt(DATA.get(this, CLONES)); },
        onUpdate: function () {
          sEntity.onUpdate.call(this);
          var x = this.getTargetX() - this.posX, y = this.getTargetY() - this.posY, z = this.getTargetZ() - this.posZ;
          var d = Math.sqrt(x * x + z * z);
          this.rotationYaw = 180.0 + Math.atan2(x, z) * 180.0 / Math.PI;
          if (this.rotationYaw > 360.0) this.rotationYaw -= 360.0;
          this.rotationPitch = Math.atan2(y, d) * 180.0 / Math.PI;
          if (this.ticksExisted > 10) this.setDead();
        },
        isInRangeToRender3d: function () { return 1; },
        writeToNBTOptional: function () { return 0; }
      }
    });
    MutantArrowEntity.create = function (world) {
      var e = new MutantArrowEntity(); BnV(e, world);
      e.damage = CFG.ENTITIES.mutantSkeletonArrowDamage; e.pointedEntities = []; e.noClip = 1;
      return e;
    };

    // ============================================================ SkullSpiritEntity.java
    // Server-only: attaching, the mutation itself, NBT.
    var ATTACHED = DATA.createKey(6, X.BOOLEAN);
    var SkullSpiritEntity = T.SkullSpiritEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.SkullSpiritEntity", extend: Entity,
      fields: function (s) { s.startTick = 0; s.attachedTick = 0; s.targetId = 0; s.targetUUID = null; },
      methods: {
        entityInit: function () { DATA.register(this, ATTACHED, M.boxBool(false)); },
        isAttached: function () { return M.unboxBool(DATA.get(this, ATTACHED)); },
        doesEntityNotTriggerPressurePlate: function () { return 1; },
        getTarget: function () { return W.getEntityByID(this.world, this.targetId); },
        onUpdate: function () {
          var target = this.getTarget(), i, posX, posY, posZ, x, y, z;
          if (target !== null && target.isEntityAlive()) {
            if (this.isAttached()) {
              this.setPosition(target.posX, target.posY, target.posZ);
              if (R.nextInt(this.rand, 8) === 0) target.attackEntityFrom(DSRC.MAGIC, 0.0);
              for (i = 0; i < 3; ++i) {
                posX = target.posX + R.nextFloat(this.rand) * target.width * 2.0 - target.width;
                posY = target.posY + 0.5 + R.nextFloat(this.rand) * target.height;
                posZ = target.posZ + R.nextFloat(this.rand) * target.width * 2.0 - target.width;
                x = R.nextGaussian(this.rand) * 0.02; y = R.nextGaussian(this.rand) * 0.02; z = R.nextGaussian(this.rand) * 0.02;
                W.spawnParticle(this.world, M.MBParticles.SKULL_SPIRIT, posX, posY, posZ, x, y, z, M.intArray(0));
              }
            } else {
              this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
              this.motionX = 0.0; this.motionY = 0.0; this.motionZ = 0.0;
              if (this.startTick-- >= 0) this.motionY += 0.30000001192092896 * this.startTick / 15.0;
              x = target.posX - this.posX; y = target.posY - this.posY; z = target.posZ - this.posZ;
              var d = Math.sqrt(x * x + y * y + z * z);
              this.motionX += x / d * 0.20000000298023224; this.motionY += y / d * 0.20000000298023224; this.motionZ += z / d * 0.20000000298023224;
              this.moveEntity(M.MOVER_SELF(), this.motionX, this.motionY, this.motionZ);
              for (i = 0; i < 16; ++i) {
                var xx = (R.nextFloat(this.rand) - 0.5) * 1.2, yy = (R.nextFloat(this.rand) - 0.5) * 1.2, zz = (R.nextFloat(this.rand) - 0.5) * 1.2;
                W.spawnParticle(this.world, M.MBParticles.SKULL_SPIRIT, this.posX + xx, this.posY + yy, this.posZ + zz, 0.0, 0.0, 0.0, M.intArray(0));
              }
            }
          } else this.setDead();
        },
        readSpawnData: function (buf) { this.targetId = buf.readInt(); }
      }
    });
    SkullSpiritEntity.create = function (world) {
      var e = new SkullSpiritEntity(); BnV(e, world);
      e.startTick = 15; e.attachedTick = 80 + R.nextInt(e.rand, 40);
      e.noClip = 1; e.setSize(0.1, 0.1);
      return e;
    };

    // ============================================================ ThrowableBlockEntity.java
    // Server-only: the thrower constructors, onImpact effects (block placement, damage, drops), NBT, throwBlock.
    var HELD = DATA.createKey(6, X.BOOLEAN);
    var ThrowableBlockEntity = T.ThrowableBlockEntity = M.defineClass({
      name: "chumbanotz.mutantbeasts.entity.projectile.ThrowableBlockEntity", extend: EntityThrowable,
      fields: function (s) { s.blockState = null; s.ownerUUID = null; },
      methods: {
        entityInit: function () { DATA.register(this, HELD, M.boxBool(false)); },
        getBlockState: function () { return this.blockState; },
        getThrower: function () { return this.thrower; },
        setThrower: function (entity) { if (entity instanceof Co) { this.thrower = entity; this.ownerUUID = entity.getUniqueID(); } },
        isHeld: function () { return M.unboxBool(DATA.get(this, HELD)); },
        setHeld: function (held) { DATA.set(this, HELD, M.boxBool(held)); },
        getGravityVelocity: function () {
          if (this.thrower instanceof T.MutantSnowGolemEntity) return 0.06;
          if (this.thrower instanceof Cb) return 0.04;
          return 0.01;
        },
        canTriggerWalking: function () { return 0; },
        canBeCollidedWith: function () { return bool(this.isHeld() && !this.isDead); },
        canBePushed: function () { return bool(this.isHeld() && !this.isDead); },
        canBeAttackedWithItem: function () { return 0; },
        applyEntityCollision: function (entityIn) { if (entityIn !== this.thrower) sThrowable.applyEntityCollision.call(this, entityIn); },
        handleStatusUpdate: function (id) {
          if (id === 3) {
            for (var i = 0; i < 60; ++i) {
              var x = this.posX + R.nextFloat(this.rand) * this.width * 2.0 - this.width;
              var y = this.posY + 0.5 + R.nextFloat(this.rand) * this.height;
              var z = this.posZ + R.nextFloat(this.rand) * this.width * 2.0 - this.width;
              var motx = (R.nextFloat(this.rand) - R.nextFloat(this.rand)) * 3.0, moty = 0.5 + R.nextFloat(this.rand) * 2.0, motz = (R.nextFloat(this.rand) - R.nextFloat(this.rand)) * 3.0;
              W.spawnParticle(this.world, PT.BLOCK_CRACK, x, y, z, motx, moty, motz, M.intArrayOf([GvO(this.blockState)]));
            }
          }
        },
        onUpdate: function () {
          if (this.isHeld()) {
            this.lastTickPosX = this.posX; this.lastTickPosY = this.posY; this.lastTickPosZ = this.posZ;
            this.onEntityUpdate();
            if (!(this.thrower !== null && this.thrower.isEntityAlive() && X.notSpectating(this.thrower) && M.EndersoulFragmentIsProtected(this.thrower))) {
              this.setHeld(false);
            } else {
              var vec = this.thrower.getLookVec();
              var x = this.thrower.posX + vec.bh * 1.6 - this.posX;
              var y = this.thrower.posY + this.thrower.getEyeHeight() + vec.bq * 1.6 - this.posY;
              var z = this.thrower.posZ + vec.bi * 1.6 - this.posZ;
              var offset = 0.6000000238418579;
              this.motionX = x * offset; this.motionY = y * offset; this.motionZ = z * offset;
              this.moveEntity(M.MOVER_SELF(), this.motionX, this.motionY, this.motionZ);
            }
          } else {
            this.ignoreEntity = this.thrower;
            sThrowable.onUpdate.call(this);
          }
        },
        processInitialInteract: function (player, hand) {
          var itemStack = player.getHeldItem(hand);
          if (player.isSneaking() || M.itemOf(itemStack) !== M.ITEMS_MB.ENDERSOUL_HAND) return 0;
          if (this.isHeld() && this.thrower === player) {
            player.swingArm(hand);
            M.stack.damageItem(itemStack, 1, player);
            return 1;
          }
          return 0;
        },
        onImpact: function (result) {
          if (this.thrower instanceof T.MutantSnowGolemEntity) {
            // entityHit damage / area damage: attackEntityFrom returns false on the client
          }
        },
        readSpawnData: function (buf) { this.blockState = DVV(buf.readInt()); }
      }
    });
    ThrowableBlockEntity.prototype.$jmThrowable = true;       // IThrowableEntity (Forge interface): SPAWN sets the thrower
    ThrowableBlockEntity.create = function (world) {
      var e = new ThrowableBlockEntity(); FTZ(e, world);
      e.blockState = M.defaultState(M.block("grass"));
      e.setSize(1.0, 1.0);
      return e;
    };
  };
})(JasprMutants);
