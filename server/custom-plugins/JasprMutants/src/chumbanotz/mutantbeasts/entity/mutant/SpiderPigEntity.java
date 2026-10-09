package chumbanotz.mutantbeasts.entity.mutant;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import chumbanotz.mutantbeasts.entity.ai.EntityAIAvoidDamage;
import chumbanotz.mutantbeasts.entity.ai.EntityAIHurtByNearestTarget;
import chumbanotz.mutantbeasts.entity.ai.MBEntityAIAttackMelee;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.IJumpingMount;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowOwner;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAIOwnerHurtByTarget;
import net.minecraft.entity.ai.EntityAIOwnerHurtTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITargetNonTamed;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateClimber;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class SpiderPigEntity extends EntityTameable implements IJumpingMount {
    private static final DataParameter<Boolean> CLIMBING = EntityDataManager.createKey(SpiderPigEntity.class, (DataSerializer) DataSerializers.BOOLEAN);
    private static final Set<Item> TEMPTATION_ITEMS = ImmutableSet.of(Items.CARROT, Items.POTATO, Items.BEETROOT, Items.PORKCHOP, Items.SPIDER_EYE);
    private int leapCooldown;
    private int leapTick;
    private boolean isLeaping;
    private float chargePower;
    private int chargingTick;
    private int chargeExhaustion;
    private boolean chargeExhausted;
    private final List<WebPos> webList = new ArrayList<WebPos>(12);

    public SpiderPigEntity(World worldIn) {
        super(worldIn);
        this.setSize(1.4f, 0.9f);
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new MBEntityAIAttackMelee(this, 1.1).setMaxAttackTick(15));
        this.tasks.addTask(2, new LeapAttackGoal());
        this.tasks.addTask(3, new EntityAIAvoidDamage(this, 1.1));
        this.tasks.addTask(4, new EntityAIFollowOwner(this, 1.0, 10.0f, 5.0f));
        this.tasks.addTask(5, new EntityAIMate(this, 1.0));
        this.tasks.addTask(6, new EntityAITempt(this, 1.1, false, TEMPTATION_ITEMS));
        this.tasks.addTask(7, new EntityAIFollowParent(this, 1.1));
        this.tasks.addTask(8, new EntityAIWanderAvoidWater(this, 1.0));
        this.tasks.addTask(9, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0f));
        this.tasks.addTask(9, new EntityAILookIdle(this));
        this.targetTasks.addTask(0, new EntityAIOwnerHurtByTarget(this));
        this.targetTasks.addTask(1, new EntityAIOwnerHurtTarget(this));
        this.targetTasks.addTask(2, new EntityAIHurtByNearestTarget(this, true));
        this.targetTasks.addTask(3, new EntityAITargetNonTamed<>(this, EntityLiving.class, true, SpiderPigEntity::isPigOrSpider));
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(MBConfig.ENTITIES.spiderPigArmor);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(MBConfig.ENTITIES.spiderPigAttackDamage);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(MBConfig.ENTITIES.spiderPigKnockbackResistance);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(MBConfig.ENTITIES.spiderPigMaxHealth);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(MBConfig.ENTITIES.spiderPigMovementSpeed);
        this.getEntityAttribute(SWIM_SPEED).setBaseValue(MBConfig.ENTITIES.spiderPigSwimSpeed);
    }

    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CLIMBING, false);
    }

    public boolean isBesideClimbableBlock() {
        return this.dataManager.get(CLIMBING);
    }

    private void setBesideClimbableBlock(boolean climbing) {
        this.dataManager.set(CLIMBING, climbing);
    }

    public boolean isSaddled() {
        return (this.dataManager.get(TAMED) & 2) != 0;
    }

    private void setSaddled(boolean saddled) {
        byte b0 = this.dataManager.get(TAMED);
        this.dataManager.set(TAMED, saddled ? (byte) (b0 | 2) : (byte) (b0 & 0xFFFFFFFD));
    }

    public EnumCreatureAttribute getCreatureAttribute() {
        return EnumCreatureAttribute.ARTHROPOD;
    }

    public float getEyeHeight() {
        return this.height * 0.75f;
    }

    protected PathNavigate createNavigator(World worldIn) {
        return new PathNavigateClimber(this, worldIn);
    }

    public boolean isPotionApplicable(PotionEffect potioneffectIn) {
        return potioneffectIn.getPotion() != MobEffects.POISON && super.isPotionApplicable(potioneffectIn);
    }

    public void setAttackTarget(EntityLivingBase entitylivingbaseIn) {
        if (!this.isChild()) {
            super.setAttackTarget(entitylivingbaseIn);
        }
    }

    public boolean isBreedingItem(ItemStack stack) {
        return TEMPTATION_ITEMS.contains(stack.getItem());
    }

    public void fall(float distance, float damageMultiplier) {
    }

    public void onUpdate() {
        super.onUpdate();
        this.setBesideClimbableBlock(this.collidedHorizontally);
        if (this.chargeExhaustion >= 120) {
            this.chargeExhausted = true;
        }
        if (this.chargeExhaustion <= 0) {
            this.chargeExhausted = false;
        }
        this.chargeExhaustion = Math.max(0, this.chargeExhaustion - 1);
        if (!this.world.isRemote) {
            this.leapCooldown = Math.max(0, this.leapCooldown - 1);
            if (this.leapTick > 10 && this.onGround) {
                this.isLeaping = false;
            }
            this.updateWebList(false);
            this.updateChargeState();
            if (this.isTamed() && this.ticksExisted % 600 == 0) {
                this.heal(1.0f);
            }
        }
    }

    private void updateWebList(boolean onlyCheckSize) {
        WebPos first;
        if (!onlyCheckSize) {
            for (int i = 0; i < this.webList.size(); ++i) {
                WebPos coord = this.webList.get(i);
                if (this.world.getBlockState(coord).getBlock() != Blocks.WEB) {
                    this.webList.remove(i);
                    --i;
                    continue;
                }
                --coord.timeLeft;
            }
            if (!this.webList.isEmpty() && (first = this.webList.get(0)).timeLeft < 0) {
                this.webList.remove(0);
                if (ForgeEventFactory.getMobGriefingEvent(this.world, this)) {
                    this.removeWeb(first);
                }
            }
        }
        while (this.webList.size() > 12) {
            first = this.webList.remove(0);
            this.removeWeb(first);
        }
    }

    private void removeWeb(BlockPos pos) {
        if (this.world.getBlockState(pos).getBlock() == Blocks.WEB) {
            this.world.destroyBlock(pos, false);
        }
    }

    private void updateChargeState() {
        if (this.chargingTick > 0) {
            for (Entity entity : this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox())) {
                if (entity == this || !entity.canBeCollidedWith() || this.isOnSameTeam(entity)) continue;
                this.attackEntityAsMob(entity);
            }
        }
        this.chargingTick = Math.max(0, this.chargingTick - 1);
    }

    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (this.isTamed() && this.isOwner(player)) {
            if (itemstack.getItem() instanceof ItemFood && this.isBreedingItem(itemstack) && this.getHealth() < this.getMaxHealth()) {
                this.heal(((ItemFood) itemstack.getItem()).getHealAmount(itemstack));
                this.consumeItemFromStack(player, itemstack);
                return true;
            }
            if (itemstack.getItem() == Items.SADDLE) {
                if (!(player.isSneaking() || this.isSaddled() || this.isChild())) {
                    this.setSaddled(true);
                    this.world.playSound(player, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_PIG_SADDLE, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    this.consumeItemFromStack(player, itemstack);
                    return true;
                }
            } else if (this.isSaddled() && !this.isBeingRidden()) {
                if (!player.isSneaking()) {
                    if (!this.world.isRemote) {
                        player.startRiding(this);
                        this.navigator.clearPath();
                    }
                    return true;
                }
                this.setSaddled(false);
                this.world.playSound(player, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_PIG_SADDLE, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                if (!this.world.isRemote) {
                    // JasperCraft port: Forge drops the saddle at once; CraftBukkit holds a living entity's drops back for its death (forceDrops)
                    boolean immediateDrops = chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(this, true);
                    this.dropItem(Items.SADDLE, 1);
                    chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(this, immediateDrops);
                }
                return true;
            }
        }
        return super.processInteract(player, hand);
    }

    public boolean shouldAttackEntity(EntityLivingBase target, EntityLivingBase owner) {
        return EntityUtil.shouldAttackEntity(target, owner, false);
    }

    public boolean attackEntityAsMob(Entity entityIn) {
        boolean flag;
        this.isLeaping = false;
        if (this.rand.nextInt(2) == 0 && ForgeEventFactory.getMobGriefingEvent(this.world, this)) {
            double dx = entityIn.posX - entityIn.prevPosX;
            double dz = entityIn.posZ - entityIn.prevPosZ;
            BlockPos pos = new BlockPos((int) (entityIn.posX + dx * 0.5), MathHelper.floor(this.getEntityBoundingBox().minY), (int) (entityIn.posZ + dz * 0.5));
            Material material = this.world.getBlockState(pos).getMaterial();
            if (!material.isSolid() && !material.isLiquid() && material != Material.WEB) {
                this.world.setBlockState(pos, Blocks.WEB.getDefaultState());
                this.webList.add(new WebPos(pos, this.chargingTick > 0 ? 600 : 1200));
                this.updateWebList(true);
                this.motionY = Math.max(0.25, this.motionY);
                this.fallDistance = 0.0f;
            }
        }
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (!(entityIn instanceof EntitySpider) && !(entityIn instanceof SpiderPigEntity) && this.world.isMaterialInBB(entityIn.getEntityBoundingBox(), Material.WEB)) {
            damage += 4.0f;
        }
        if (flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage)) {
            this.applyEnchantments(this, entityIn);
        }
        return flag;
    }

    public boolean canJump() {
        return this.isSaddled() && !this.chargeExhausted && this.onGround && !this.collidedHorizontally;
    }

    public void setJumpPower(int jumpPowerIn) {
        this.chargeExhaustion += 50 * jumpPowerIn / 100;
        this.chargePower = (float) jumpPowerIn / 100.0f;
    }

    public void handleStartJump(int jumpPowerIn) {
        this.chargingTick = 8 * jumpPowerIn / 100;
    }

    public void handleStopJump() {
    }

    protected boolean isMovementBlocked() {
        return super.isMovementBlocked() || this.isBeingRidden() && this.isSaddled();
    }

    @Nullable
    public Entity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
    }

    public boolean canBeSteered() {
        return this.getControllingPassenger() instanceof EntityLivingBase;
    }

    public void travel(float strafe, float vertical, float forward) {
        if (this.isBeingRidden() && this.canBeSteered()) {
            EntityLivingBase livingentity = (EntityLivingBase) this.getControllingPassenger();
            this.stepHeight = 1.0f;
            this.rotationYaw = this.rotationYawHead = livingentity.rotationYaw;
            this.prevRotationYaw = this.rotationYawHead;
            this.prevRotationPitch = this.rotationPitch = livingentity.rotationPitch * 0.4f;
            this.setRotation(this.rotationYaw, this.rotationPitch);
            while (this.renderYawOffset > this.rotationYawHead + 180.0f) {
                this.renderYawOffset -= 360.0f;
            }
            while (this.renderYawOffset < this.rotationYawHead - 180.0f) {
                this.renderYawOffset += 360.0f;
            }
            if (!this.chargeExhausted && this.chargePower > 0.0f && (this.onGround || this.collidedHorizontally)) {
                float pitch = this.rotationPitch;
                this.rotationPitch = 0.0f;
                this.rotationPitch = pitch;
                Vec3d lookVec = this.getLookVec();
                double power = (double) 1.6f * (double) this.chargePower;
                this.motionX = lookVec.x * power;
                this.motionY = 0.3f;
                this.motionZ = lookVec.z * power;
                this.chargePower = 0.0f;
            } else {
                this.chargePower = 0.0f;
            }
            this.jumpMovementFactor = this.getAIMoveSpeed() * 0.1f;
            if (this.canPassengerSteer()) {
                strafe = livingentity.moveStrafing * 0.8f;
                forward = livingentity.moveForward * 0.6f;
                this.setAIMoveSpeed((float) this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
                super.travel(strafe, vertical, forward);
            } else if (livingentity instanceof EntityPlayer) {
                this.motionX = 0.0;
                this.motionY = 0.0;
                this.motionZ = 0.0;
            } else {
                this.prevLimbSwingAmount = this.limbSwingAmount;
                double d1 = this.posX - this.prevPosX;
                double d0 = this.posZ - this.prevPosZ;
                float f2 = MathHelper.sqrt(d1 * d1 + d0 * d0) * 4.0f;
                if (f2 > 1.0f) {
                    f2 = 1.0f;
                }
                this.limbSwingAmount += (f2 - this.limbSwingAmount) * 0.4f;
                this.limbSwing += this.limbSwingAmount;
            }
        } else {
            this.stepHeight = 0.6f;
            this.jumpMovementFactor = 0.02f;
            super.travel(strafe, vertical, forward);
        }
    }

    public void onKillEntity(EntityLivingBase entityLivingIn) {
        if (!this.world.isRemote) {
            if (entityLivingIn instanceof CreeperMinionEntity && !this.isTamed()) {
                CreeperMinionEntity minion = (CreeperMinionEntity) entityLivingIn;
                EntityLivingBase owner = minion.getOwner();
                if (owner instanceof EntityPlayer && !ForgeEventFactory.onAnimalTame(this, (EntityPlayer) owner)) {
                    this.playTameEffect(true);
                    this.world.setEntityState(this, (byte) 7);
                    this.setTamedBy((EntityPlayer) owner);
                    minion.setDead();
                } else {
                    this.playTameEffect(false);
                    this.world.setEntityState(this, (byte) 6);
                }
            }
            if (SpiderPigEntity.isPigOrSpider(entityLivingIn)) {
                EntityUtil.convertMobWithNBT(entityLivingIn, new SpiderPigEntity(this.world), false);
            }
        }
    }

    protected boolean canDespawn() {
        return !this.isTamed();
    }

    public boolean isOnLadder() {
        return this.isBesideClimbableBlock();
    }

    public void setInWeb() {
    }

    public EntityAgeable createChild(EntityAgeable ageable) {
        if (this.rand.nextInt(20) == 0) {
            return new EntityPig(this.world);
        }
        SpiderPigEntity spiderPig = new SpiderPigEntity(this.world);
        UUID uuid = this.getOwnerId();
        if (uuid != null) {
            spiderPig.setOwnerId(uuid);
            spiderPig.setTamed(true);
        }
        return spiderPig;
    }

    protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
        super.dropEquipment(wasRecentlyHit, lootingModifier);
        if (this.isSaddled()) {
            this.dropItem(Items.SADDLE, 1);
            this.setSaddled(false);
        }
    }

    // JasperCraft port: Entity.onRemovedFromWorld is a Forge hook (no @Override, no super call: Forge's only clears its
    // isAddedToWorld flag); chat.jaspr.mutants.hooks.EntityLifecycle calls it from Paper's EntityRemoveFromWorldEvent,
    // which fires at the same point of World.onEntityRemoved.
    public void onRemovedFromWorld() {
        if (!this.world.isRemote && !this.webList.isEmpty() && ForgeEventFactory.getMobGriefingEvent(this.world, this)) {
            for (WebPos webPos : this.webList) {
                this.removeWeb(webPos);
            }
        }
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("Saddled", this.isSaddled());
        if (!this.webList.isEmpty()) {
            NBTTagList nbtTagList = new NBTTagList();
            for (WebPos coord : this.webList) {
                NBTTagCompound compound1 = NBTUtil.createPosTag(coord);
                compound1.setInteger("TimeLeft", coord.timeLeft);
                nbtTagList.appendTag(compound1);
            }
            compound.setTag("Webs", nbtTagList);
        }
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setSaddled(compound.getBoolean("Saddled"));
        NBTTagList listnbt = compound.getTagList("Webs", 10);
        for (int i = 0; i < listnbt.tagCount(); ++i) {
            NBTTagCompound compound1 = listnbt.getCompoundTagAt(i);
            this.webList.add(i, new WebPos(NBTUtil.getPosFromTag(compound1), compound1.getInteger("TimeLeft")));
        }
    }

    protected SoundEvent getAmbientSound() {
        return MBSoundEvents.ENTITY_SPIDER_PIG_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return MBSoundEvents.ENTITY_SPIDER_PIG_HURT;
    }

    protected SoundEvent getDeathSound() {
        return MBSoundEvents.ENTITY_SPIDER_PIG_DEATH;
    }

    protected void playStepSound(BlockPos pos, Block blockIn) {
        this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.15f, 1.0f);
    }

    public static boolean isPigOrSpider(EntityLivingBase livingEntity) {
        return livingEntity.getClass() == EntityPig.class || livingEntity.getClass() == EntitySpider.class;
    }

    protected ResourceLocation getLootTable() {
        return EntityUtil.getLootTable(this);
    }

    static class WebPos
            extends BlockPos {
        private int timeLeft;

        public WebPos(BlockPos pos, int timeLeft) {
            super(pos);
            this.timeLeft = timeLeft;
        }
    }

    class LeapAttackGoal
            extends EntityAIBase {
        LeapAttackGoal() {
        }

        public boolean shouldExecute() {
            EntityLivingBase target = SpiderPigEntity.this.getAttackTarget();
            return target != null && SpiderPigEntity.this.leapCooldown <= 0 && (SpiderPigEntity.this.onGround || SpiderPigEntity.this.collidedHorizontally || SpiderPigEntity.this.isInWater() || SpiderPigEntity.this.isInLava()) && (SpiderPigEntity.this.getDistanceSq(target) < 64.0 && SpiderPigEntity.this.rand.nextInt(8) == 0 || SpiderPigEntity.this.getDistanceSq(target) < 6.25);
        }

        public void startExecuting() {
            SpiderPigEntity.this.isLeaping = true;
            SpiderPigEntity.this.leapCooldown = 15;
            EntityLivingBase target = SpiderPigEntity.this.getAttackTarget();
            double x = target.posX - SpiderPigEntity.this.posX;
            double y = target.posY - SpiderPigEntity.this.posY;
            double z = target.posZ - SpiderPigEntity.this.posZ;
            double d = MathHelper.sqrt(x * x + y * y + z * z);
            double scale = 2.0f + 0.2f * SpiderPigEntity.this.rand.nextFloat() * SpiderPigEntity.this.rand.nextFloat();
            SpiderPigEntity.this.motionX = x / d * scale;
            SpiderPigEntity.this.motionY = y / d * scale * 0.5 + 0.3;
            SpiderPigEntity.this.motionZ = z / d * scale;
        }

        public boolean shouldContinueExecuting() {
            return SpiderPigEntity.this.isLeaping && SpiderPigEntity.this.leapTick < 40;
        }

        public void updateTask() {
            ++SpiderPigEntity.this.leapTick;
        }

        public void resetTask() {
            SpiderPigEntity.this.leapTick = 0;
        }
    }

    // JasperCraft port: Bukkit wrapper; CraftBukkit's entity factory has no case for this class (chat.jaspr.mutants.wrappers).
    @Override
    public org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity getBukkitEntity() {
        if (this.bukkitEntity == null) {
            this.bukkitEntity = chat.jaspr.mutants.wrappers.MutantWrappers.create(this);
        }
        return this.bukkitEntity;
    }
}
