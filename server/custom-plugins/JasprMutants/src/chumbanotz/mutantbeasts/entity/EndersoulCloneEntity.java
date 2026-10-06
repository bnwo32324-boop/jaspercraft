package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.entity.ai.MBEntityAIAttackMelee;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.pathfinding.MBGroundPathNavigator;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import java.lang.invoke.LambdaMetafactory;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

public class EndersoulCloneEntity
extends EntityMob {
    private MutantEndermanEntity cloner;

    public EndersoulCloneEntity(World worldIn) {
        super(worldIn);
        this.stepHeight = 1.0f;
        this.experienceValue = this.rand.nextInt(2);
        this.setPathPriority(PathNodeType.DAMAGE_FIRE, -1.0f);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0f);
        this.setSize(0.6f, 2.9f);
    }

    public EndersoulCloneEntity(MutantEndermanEntity cloner, double x, double y, double z) {
        this(cloner.world);
        this.cloner = cloner;
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(cloner.getMaxHealth());
        this.setHealth(cloner.getHealth());
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(cloner.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue());
        if (!EntityUtil.teleportTo(this, x, y, z)) {
            this.copyLocationAndAnglesFrom(cloner);
        }
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new MBEntityAIAttackMelee(this, 1.2));
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1.0);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1.0);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3);
    }

    public float getEyeHeight() {
        return 2.55f;
    }

    protected PathNavigate createNavigator(World worldIn) {
        return new MBGroundPathNavigator(this, worldIn);
    }

    public int getMaxFallHeight() {
        return 3;
    }

    public void setAttackTarget(@Nullable EntityLivingBase entitylivingbaseIn) {
        super.setAttackTarget(entitylivingbaseIn);
        this.setFlag(2, entitylivingbaseIn != null);
    }

    public boolean isAggressive() {
        return this.getFlag(2);
    }

    // JasperCraft port: client-only (Entity.handleStatusUpdate is not in the server jar, so the super call is left out);
    // the server never calls it, the client stage ports it.
    public void handleStatusUpdate(byte id) {
        if (id == 0) {
            EntityUtil.spawnEndersoulParticles(this, 256, 1.8f);
        }
    }

    public void onLivingUpdate() {
        this.isJumping = false;
        super.onLivingUpdate();
        if (this.cloner != null && (!this.cloner.isEntityAlive() || this.cloner.isAIDisabled() || this.cloner.world != this.world)) {
            this.setDead();
        }
    }

    protected void updateAITasks() {
        EntityLivingBase entity = this.getAttackTarget();
        if (this.rand.nextInt(10) == 0 && entity != null && (this.isInWater() || this.fallDistance > 3.0f || this.isRidingSameEntity(entity) || this.getDistanceSq(entity) > 1024.0 || !this.hasPath())) {
            this.teleportToEntity(entity);
        }
    }

    public boolean attackEntityAsMob(Entity entityIn) {
        boolean flag = super.attackEntityAsMob(entityIn);
        if (!this.world.isRemote && this.rand.nextInt(3) != 0) {
            this.teleportToEntity(entityIn);
        }
        if (flag) {
            this.heal(2.0f);
        }
        this.swingArm(EnumHand.MAIN_HAND);
        return flag;
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean flag;
        if (this.isEntityInvulnerable(source) || source.getTrueSource() instanceof EntityDragon || source.getTrueSource() instanceof MutantEndermanEntity) {
            return false;
        }
        boolean bl = flag = !this.world.isRemote && !this.isDead && this.ticksExisted > 0;
        if (flag) {
            if (source.getTrueSource() instanceof EntityPlayer) {
                this.attackingPlayer = (EntityPlayer)source.getTrueSource();
                this.recentlyHit = 100;
            }
            if (this.world.getGameRules().getBoolean("doMobLoot")) {
                EntityUtil.dropExperience(this, this.recentlyHit, this::getExperiencePoints, this.attackingPlayer);
                // JasperCraft port: drops outside a death are immediate on Forge; CraftBukkit would hold them back (forceDrops)
                boolean immediateDrops = chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(this, true);
                this.dropLoot(this.recentlyHit > 0, ForgeHooks.getLootingLevel(this, source.getTrueSource(), source), source);
                chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(this, immediateDrops);
            }
            this.setDead();
        }
        return flag;
    }

    private boolean teleportToEntity(Entity entity) {
        double z;
        double y;
        double x = entity.posX + (double)((this.rand.nextFloat() - 0.5f) * 24.0f);
        boolean flag = EntityUtil.teleportTo(this, x, y = entity.posY + (double)this.rand.nextInt(5) + 4.0, z = entity.posZ + (double)((this.rand.nextFloat() - 0.5f) * 24.0f));
        if (flag) {
            this.dismountRidingEntity();
            if (!this.isSilent()) {
                this.world.playSound(null, this.prevPosX, this.prevPosY, this.prevPosZ, MBSoundEvents.ENTITY_ENDERSOUL_CLONE_TELEPORT, this.getSoundCategory(), 1.0f, 1.0f);
                this.playSound(MBSoundEvents.ENTITY_ENDERSOUL_CLONE_TELEPORT, 1.0f, 1.0f);
            }
        }
        return flag;
    }

    protected void collideWithNearbyEntities() {
    }

    public boolean canBeHitWithPotion() {
        return false;
    }

    public boolean isNoDespawnRequired() {
        return super.isNoDespawnRequired() || this.cloner != null;
    }

    public void setDead() {
        super.setDead();
        this.world.setEntityState(this, (byte)0);
        this.playSound(this.getDeathSound(), this.getSoundVolume(), this.getSoundPitch());
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return null;
    }

    protected SoundEvent getDeathSound() {
        return MBSoundEvents.ENTITY_ENDERSOUL_CLONE_DEATH;
    }

    public boolean isEntityEqual(Entity entityIn) {
        return super.isEntityEqual(entityIn) || entityIn == this.cloner;
    }

    public Team getTeam() {
        return this.cloner != null ? this.cloner.getTeam() : super.getTeam();
    }

    public boolean isOnSameTeam(Entity entityIn) {
        return this.cloner != null && (this.cloner == entityIn || this.cloner.isOnSameTeam(entityIn)) || super.isOnSameTeam(entityIn);
    }

    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return this.cloner == null && super.writeToNBTOptional(compound);
    }

    protected ResourceLocation getLootTable() {
        return EntityUtil.getLootTable(this);
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
