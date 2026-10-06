package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.entity.ai.EntityAIAvoidDamage;
import chumbanotz.mutantbeasts.entity.ai.EntityAIHurtByNearestTarget;
import chumbanotz.mutantbeasts.entity.ai.MBEntityAIAttackMelee;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.pathfinding.MBGroundPathNavigator;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import chumbanotz.mutantbeasts.util.MutatedExplosion;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowOwner;
import net.minecraft.entity.ai.EntityAILandOnOwnersShoulder;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIOwnerHurtByTarget;
import net.minecraft.entity.ai.EntityAIOwnerHurtTarget;
import net.minecraft.entity.ai.EntityAISit;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITargetNonTamed;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityShoulderRiding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.scoreboard.Team;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class CreeperMinionEntity
extends EntityShoulderRiding {
    private static final DataParameter<Byte> CREEPER_MINION_FLAGS = EntityDataManager.createKey(CreeperMinionEntity.class, (DataSerializer)DataSerializers.BYTE);
    private static final DataParameter<Integer> EXPLODE_STATE = EntityDataManager.createKey(CreeperMinionEntity.class, (DataSerializer)DataSerializers.VARINT);
    private static final DataParameter<Float> EXPLOSION_RADIUS = EntityDataManager.createKey(CreeperMinionEntity.class, (DataSerializer)DataSerializers.FLOAT);
    private int lastActiveTime;
    private int timeSinceIgnited;
    private int fuseTime = 26;

    public CreeperMinionEntity(World worldIn) {
        super(worldIn);
        this.setDestroyBlocks(true);
        this.setSize(0.3f, 0.84f);
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.aiSit = new EntityAISit(this);
        this.tasks.addTask(1, this.aiSit);
        this.tasks.addTask(2, new AISwell());
        this.tasks.addTask(3, new EntityAIAvoidEntity<EntityOcelot>((EntityCreature)this, EntityOcelot.class, 6.0f, 1.0, 1.2){

            public boolean shouldExecute() {
                return !CreeperMinionEntity.this.isTamed() && super.shouldExecute();
            }
        });
        this.tasks.addTask(4, new MBEntityAIAttackMelee(this, 1.2));
        this.tasks.addTask(5, new EntityAIAvoidDamage(this, 1.2));
        this.tasks.addTask(6, new FollowOwnerGoal());
        this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 1.0));
        this.tasks.addTask(8, new EntityAILandOnOwnersShoulder(this){

            public boolean shouldExecute() {
                return MBConfig.ENTITIES.creeperMinionOnShoulder && CreeperMinionEntity.this.isTamed() && CreeperMinionEntity.this.getOwner() instanceof EntityPlayer && super.shouldExecute();
            }
        });
        this.tasks.addTask(9, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0f));
        this.tasks.addTask(9, new EntityAILookIdle(this));
        this.targetTasks.addTask(0, new EntityAIOwnerHurtByTarget(this));
        this.targetTasks.addTask(1, new EntityAIOwnerHurtTarget(this));
        this.targetTasks.addTask(2, new EntityAIHurtByNearestTarget(this));
        this.targetTasks.addTask(3, new EntityAITargetNonTamed(this, EntityPlayer.class, true, null));
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(4.0);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25);
    }

    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CREEPER_MINION_FLAGS, (byte)0);
        this.dataManager.register(EXPLODE_STATE, -1);
        this.dataManager.register(EXPLOSION_RADIUS, Float.valueOf(20.0f));
    }

    @Nullable
    public EntityLivingBase getOwner() {
        UUID uuid = this.getOwnerId();
        if (uuid == null) {
            return null;
        }
        Entity entity = this.world.getPlayerEntityByUUID(uuid);
        if (entity == null && this.world instanceof WorldServer) {
            entity = ((WorldServer)((Object)this.world)).getEntityFromUuid(uuid);
        }
        return entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
    }

    public int getExplodeState() {
        return (Integer)this.dataManager.get(EXPLODE_STATE);
    }

    public void setExplodeState(int state) {
        this.dataManager.set(EXPLODE_STATE, state);
    }

    public boolean getPowered() {
        return ((Byte)this.dataManager.get(CREEPER_MINION_FLAGS) & 1) != 0;
    }

    public void setPowered(boolean powered) {
        byte b0 = (Byte)this.dataManager.get(CREEPER_MINION_FLAGS);
        this.dataManager.set(CREEPER_MINION_FLAGS, powered ? (byte)(b0 | 1) : (byte)(b0 & 0xFFFFFFFE));
    }

    public boolean hasIgnited() {
        return ((Byte)this.dataManager.get(CREEPER_MINION_FLAGS) & 4) != 0;
    }

    public void ignite() {
        byte b0 = (Byte)this.dataManager.get(CREEPER_MINION_FLAGS);
        this.dataManager.set(CREEPER_MINION_FLAGS, (byte)(b0 | 4));
    }

    public boolean canExplodeContinuously() {
        return ((Byte)this.dataManager.get(CREEPER_MINION_FLAGS) & 8) != 0;
    }

    public void setCanExplodeContinuously(boolean continuously) {
        byte b0 = (Byte)this.dataManager.get(CREEPER_MINION_FLAGS);
        this.dataManager.set(CREEPER_MINION_FLAGS, continuously ? (byte)(b0 | 8) : (byte)(b0 & 0xFFFFFFF7));
    }

    public boolean canDestroyBlocks() {
        return ((Byte)this.dataManager.get(CREEPER_MINION_FLAGS) & 0x10) != 0;
    }

    public void setDestroyBlocks(boolean destroy) {
        byte b0 = (Byte)this.dataManager.get(CREEPER_MINION_FLAGS);
        this.dataManager.set(CREEPER_MINION_FLAGS, destroy ? (byte)(b0 | 0x10) : (byte)(b0 & 0xFFFFFFEF));
    }

    public boolean canRideOnShoulder() {
        return ((Byte)this.dataManager.get(CREEPER_MINION_FLAGS) & 0x20) != 0;
    }

    public void setCanRideOnShoulder(boolean canRide) {
        byte b0 = (Byte)this.dataManager.get(CREEPER_MINION_FLAGS);
        this.dataManager.set(CREEPER_MINION_FLAGS, canRide ? (byte)(b0 | 0x20) : (byte)(b0 & 0xFFFFFFDF));
    }

    public float getExplosionRadius() {
        return ((Float)this.dataManager.get(EXPLOSION_RADIUS)).floatValue() / 10.0f;
    }

    public void setExplosionRadius(float radius) {
        this.dataManager.set(EXPLOSION_RADIUS, Float.valueOf(radius * 10.0f));
    }

    protected PathNavigate createNavigator(World worldIn) {
        return new MBGroundPathNavigator(this, worldIn);
    }

    public boolean isChild() {
        return false;
    }

    public boolean canSitOnShoulder() {
        return super.canSitOnShoulder() && this.canRideOnShoulder() && this.getAttackTarget() == null && this.getExplodeState() < 0;
    }

    public void onStruckByLightning(EntityLightningBolt lightningBolt) {
        super.onStruckByLightning(lightningBolt);
        this.setPowered(true);
    }

    public EntityLivingBase getAttackTarget() {
        if (!this.isTamed()) {
            EntityLivingBase owner = this.getOwner();
            return owner instanceof MutantCreeperEntity ? ((MutantCreeperEntity)owner).getAttackTarget() : super.getAttackTarget();
        }
        return super.getAttackTarget();
    }

    public void onUpdate() {
        if (!this.world.isRemote && !this.isTamed() && this.world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            this.setDead();
        }
        if (this.isEntityAlive()) {
            int i;
            this.lastActiveTime = this.timeSinceIgnited;
            if (this.hasIgnited()) {
                this.setExplodeState(1);
            }
            if ((i = this.getExplodeState()) > 0 && this.timeSinceIgnited == 0) {
                this.playSound(MBSoundEvents.ENTITY_CREEPER_MINION_PRIMED, 1.0f, this.getSoundPitch());
            }
            this.timeSinceIgnited += i;
            if (this.timeSinceIgnited < 0) {
                this.timeSinceIgnited = 0;
            }
            if (this.timeSinceIgnited >= this.fuseTime) {
                this.timeSinceIgnited = 0;
                if (!this.world.isRemote) {
                    MutatedExplosion.create(this, this.getExplosionRadius() + (this.getPowered() ? 2.0f : 0.0f), false, this.canDestroyBlocks());
                    if (!this.canExplodeContinuously()) {
                        if (this.world.getGameRules().getBoolean("showDeathMessages") && this.getOwner() instanceof EntityPlayerMP) {
                            this.getOwner().sendMessage(new TextComponentTranslation("death.attack.explosion", new Object[]{this.getName()}));
                        }
                        this.dead = true;
                        this.setDead();
                        EntityUtil.spawnLingeringCloud(this);
                    }
                }
                this.setExplodeState(-this.fuseTime);
            }
            if (this.motionX * this.motionY * this.motionZ > (double)0.8f && this.getAttackTarget() != null && this.getEntityBoundingBox().expand(this.motionX, this.motionY, this.motionZ).grow(0.5).intersects(this.getAttackTarget().getEntityBoundingBox())) {
                this.timeSinceIgnited = this.fuseTime;
            }
        }
        super.onUpdate();
    }

    public float getCreeperFlashIntensity(float partialTicks) {
        return ((float)this.lastActiveTime + (float)(this.timeSinceIgnited - this.lastActiveTime) * partialTicks) / (float)(this.fuseTime - 2);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (itemstack.interactWithEntity(player, this, hand)) {
            return true;
        }
        if (this.isTamed()) {
            if (itemstack.getItem() == MBItems.CREEPER_MINION_TRACKER) {
                // JasperCraft port: EntityPlayer.openGui is FML's; on the server it only asks the mod's IGuiHandler (null here)
                chat.jaspr.mutants.compat.ForgeAccess.openGui(player, MutantBeasts.INSTANCE, 0, this.world, this.getEntityId(), 0, 0);
                return true;
            }
            if (!this.isOwner(player)) return false;
            if (itemstack.getItem() == Items.GUNPOWDER) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0f);
                    EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.HEART, 1, new int[0]);
                    itemstack.shrink(1);
                    return true;
                }
                if (!(this.getMaxHealth() < 20.0f)) return false;
                EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.HEART, 1, new int[0]);
                this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(this.getMaxHealth() + 1.0f);
                itemstack.shrink(1);
                return true;
            }
            if (itemstack.getItem() == Item.getItemFromBlock((Block)Blocks.TNT)) {
                if (this.canExplodeContinuously()) {
                    float explosionRadius = this.getExplosionRadius();
                    if (!(explosionRadius < 4.0f)) return false;
                    this.forcedAgeTimer += 5;
                    this.setExplosionRadius(explosionRadius + 0.11f);
                    itemstack.shrink(1);
                    return true;
                }
                this.forcedAgeTimer += 15;
                this.setCanExplodeContinuously(true);
                itemstack.shrink(1);
                return true;
            }
            if (this.world.isRemote) return true;
            this.aiSit.setSitting(!this.isSitting());
            this.navigator.clearPath();
            this.setAttackTarget(null);
            return true;
        }
        if (itemstack.getItem() == Items.FLINT_AND_STEEL && !this.hasIgnited()) {
            this.world.playSound(player, this.posX, this.posY, this.posZ, SoundEvents.ITEM_FLINTANDSTEEL_USE, this.getSoundCategory(), 1.0f, this.rand.nextFloat() * 0.4f + 0.8f);
            player.swingArm(hand);
            player.addStat(StatList.getObjectUseStats((Item)itemstack.getItem()));
            if (this.world.isRemote) return true;
            this.ignite();
            itemstack.damageItem(1, player);
            return true;
        }
        if (!player.isCreative() || this.getOwner() != null || itemstack.getItem() != MBItems.CREEPER_MINION_TRACKER) return false;
        if (this.world.isRemote) return true;
        this.setTamed(true);
        this.setOwnerId(player.getUniqueID());
        player.setLastAttackedEntity(null);
        player.sendMessage(new TextComponentTranslation(MBItems.CREEPER_MINION_TRACKER.getTranslationKey() + ".tame_success", new Object[]{this.getName(), player.getName()}));
        return true;
    }

    public boolean shouldAttackEntity(EntityLivingBase target, EntityLivingBase owner) {
        return EntityUtil.shouldAttackEntity(target, owner, true);
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.isExplosion()) {
            if (this.isTamed()) {
                return false;
            }
            if (amount >= 2.0f) {
                amount = 2.0f;
            }
        }
        if (this.aiSit != null) {
            this.aiSit.setSitting(false);
        }
        return super.attackEntityFrom(source, amount);
    }

    public boolean isImmuneToExplosions() {
        return this.isTamed();
    }

    public boolean canBeLeashedTo(EntityPlayer player) {
        return !this.getLeashed() && this.isTamed();
    }

    public boolean canAttackClass(Class<? extends EntityLivingBase> cls) {
        return super.canAttackClass(cls) && cls != MutantCreeperEntity.class;
    }

    protected boolean canDespawn() {
        return !this.isTamed();
    }

    @Nullable
    public Team getTeam() {
        EntityLivingBase owner = this.getOwner();
        return owner != null ? owner.getTeam() : super.getTeam();
    }

    public boolean isOnSameTeam(Entity entityIn) {
        EntityLivingBase owner = this.getOwner();
        return owner != null && (entityIn == owner || owner.isOnSameTeam(entityIn)) || super.isOnSameTeam(entityIn);
    }

    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    public EntityAgeable createChild(EntityAgeable ageable) {
        return null;
    }

    public void playLivingSound() {
        if (this.getAttackTarget() == null && this.getExplodeState() <= 0) {
            super.playLivingSound();
        }
    }

    protected float getSoundPitch() {
        return (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2f + 1.5f;
    }

    protected SoundEvent getAmbientSound() {
        return MBSoundEvents.ENTITY_CREEPER_MINION_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return MBSoundEvents.ENTITY_CREEPER_MINION_HURT;
    }

    protected SoundEvent getDeathSound() {
        return MBSoundEvents.ENTITY_CREEPER_MINION_DEATH;
    }

    public SoundCategory getSoundCategory() {
        return this.isTamed() ? SoundCategory.NEUTRAL : SoundCategory.HOSTILE;
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("Tamed", this.isTamed());
        compound.setBoolean("ExplodesContinuously", this.canExplodeContinuously());
        compound.setBoolean("DestroysBlocks", this.canDestroyBlocks());
        compound.setBoolean("CanRideOnShoulder", this.canRideOnShoulder());
        compound.setBoolean("Ignited", this.hasIgnited());
        compound.setFloat("ExplosionRadius", this.getExplosionRadius());
        if (this.getPowered()) {
            compound.setBoolean("Powered", true);
        }
        for (String unusedNBT : new String[]{"Age", "ForcedAge", "InLove", "LoveCause"}) {
            compound.removeTag(unusedNBT);
        }
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setTamed(compound.getBoolean("Tamed"));
        this.setCanExplodeContinuously(compound.getBoolean("ExplodesContinuously"));
        this.setDestroyBlocks(compound.getBoolean("DestroysBlocks"));
        this.setCanRideOnShoulder(compound.getBoolean("CanRideOnShoulder"));
        this.setPowered(compound.getBoolean("Powered"));
        if (compound.hasKey("ExplosionRadius", 99)) {
            this.setExplosionRadius(compound.getFloat("ExplosionRadius"));
        }
        if (compound.getBoolean("Ignited")) {
            this.ignite();
        }
    }

    protected ResourceLocation getLootTable() {
        return EntityUtil.getLootTable(this);
    }

    class FollowOwnerGoal
    extends EntityAIFollowOwner {
        public FollowOwnerGoal() {
            super(CreeperMinionEntity.this, 1.2, 10.0f, 5.0f);
        }

        public boolean shouldExecute() {
            return CreeperMinionEntity.this.getAttackTarget() == null && super.shouldExecute();
        }

        public void updateTask() {
            if (!CreeperMinionEntity.this.isTamed()) {
                if (CreeperMinionEntity.this.getOwner() != null) {
                    CreeperMinionEntity.this.navigator.tryMoveToEntityLiving(CreeperMinionEntity.this.getOwner(), 1.2);
                }
            } else {
                super.updateTask();
            }
        }
    }

    class AISwell
    extends EntityAIBase {
        public AISwell() {
            this.setMutexBits(1);
        }

        public boolean shouldExecute() {
            EntityLivingBase attackTarget = CreeperMinionEntity.this.getAttackTarget();
            return !CreeperMinionEntity.this.isSitting() && (CreeperMinionEntity.this.getExplodeState() > 0 || attackTarget != null && CreeperMinionEntity.this.getDistanceSq(attackTarget) < 9.0 && CreeperMinionEntity.this.getEntitySenses().canSee(attackTarget));
        }

        public void startExecuting() {
            CreeperMinionEntity.this.navigator.clearPath();
        }

        public void updateTask() {
            CreeperMinionEntity.this.setExplodeState(CreeperMinionEntity.this.getAttackTarget() == null || CreeperMinionEntity.this.getDistanceSq(CreeperMinionEntity.this.getAttackTarget()) > 36.0 || !CreeperMinionEntity.this.getEntitySenses().canSee(CreeperMinionEntity.this.getAttackTarget()) ? -1 : 1);
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
