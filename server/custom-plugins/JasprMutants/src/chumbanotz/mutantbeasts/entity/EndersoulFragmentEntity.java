package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.entity.EndersoulCloneEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

public class EndersoulFragmentEntity
extends Entity {
    public static final Predicate<Entity> IS_VALID_TARGET = entity -> {
        Class<?> entityClass = entity.getClass();
        return EntitySelectors.CAN_AI_TARGET.apply(entity) && entityClass != EntityItem.class && entityClass != EntityXPOrb.class && entityClass != EntityEnderCrystal.class && entityClass != EndersoulCloneEntity.class && entityClass != EndersoulFragmentEntity.class && entityClass != MutantEndermanEntity.class && entityClass != EntityDragon.class && entityClass != EntityEnderman.class;
    };
    private static final DataParameter<Boolean> TAMED = EntityDataManager.createKey(EndersoulFragmentEntity.class, (DataSerializer)DataSerializers.BOOLEAN);
    private int explodeTick;
    public final float[][] stickRotations;
    private EntityPlayer owner;

    public EndersoulFragmentEntity(World world) {
        super(world);
        this.explodeTick = 20 + this.rand.nextInt(20);
        this.stickRotations = new float[8][3];
        this.preventEntitySpawning = true;
        for (int i = 0; i < this.stickRotations.length; ++i) {
            for (int j = 0; j < this.stickRotations[i].length; ++j) {
                this.stickRotations[i][j] = this.rand.nextFloat() * 2.0f * (float)Math.PI;
            }
        }
        this.setSize(0.75f, 0.75f);
    }

    protected void entityInit() {
        this.dataManager.register(TAMED, false);
    }

    public EntityPlayer getOwner() {
        return this.owner;
    }

    public boolean isTamed() {
        return (Boolean)this.dataManager.get(TAMED);
    }

    public void setTamed(boolean tamed) {
        this.dataManager.set(TAMED, tamed);
    }

    protected boolean canTriggerWalking() {
        return false;
    }

    public boolean canBeCollidedWith() {
        return this.isEntityAlive();
    }

    public boolean canBePushed() {
        return this.isEntityAlive();
    }

    public void handleStatusUpdate(byte id) {
        if (id == 3) {
            EntityUtil.spawnEndersoulParticles(this, 64, 0.8f);
        }
    }

    public void onUpdate() {
        super.onUpdate();
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.owner == null && this.motionY > (double)-0.05f && !this.hasNoGravity()) {
            this.motionY = Math.max((double)-0.05f, this.motionY - (double)0.1f);
        }
        // JasperCraft port: Entity.isAddedToWorld() is Forge's (ForgeAccess.isAddedToWorld)
        if (!(this.owner == null || this.owner.isEntityAlive() && chat.jaspr.mutants.compat.ForgeAccess.isAddedToWorld(this.owner) && this.world == this.owner.world)) {
            this.owner = null;
        }
        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.9;
        this.motionY *= 0.9;
        this.motionZ *= 0.9;
        if (!this.world.isRemote) {
            if (!this.isTamed() && --this.explodeTick == 0) {
                this.explode();
            }
            if (this.owner != null && !this.owner.isSpectator() && this.getDistanceSq(this.owner) > 9.0) {
                float scale = 0.05f;
                this.addVelocity((this.owner.posX - this.posX) * (double)scale, (this.owner.posY + (double)(this.owner.height / 3.0f) - this.posY) * (double)scale, (this.owner.posZ - this.posZ) * (double)scale);
            }
        }
    }

    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (this.isTamed()) {
            if (this.owner == null && !player.isSneaking()) {
                this.owner = player;
                this.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                return true;
            }
            if (this.owner == player && player.isSneaking()) {
                this.owner = null;
                this.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                return true;
            }
            return false;
        }
        if (!this.world.isRemote) {
            this.setTamed(true);
        }
        this.owner = player;
        this.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        return true;
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isEntityInvulnerable(source)) {
            return false;
        }
        if (!this.world.isRemote && !this.isDead && this.ticksExisted > 0) {
            this.explode();
        }
        return true;
    }

    private void explode() {
        this.playSound(MBSoundEvents.ENTITY_ENDERSOUL_FRAGMENT_EXPLODE, 1.0f, (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2f + 1.0f);
        this.world.setEntityState(this, (byte)3);
        for (Entity entity : this.world.getEntitiesInAABBexcluding(this, this.getEntityBoundingBox().grow(5.0), IS_VALID_TARGET)) {
            if (!(this.getDistanceSq(entity) <= 25.0)) continue;
            boolean protectedEntity = EndersoulFragmentEntity.isProtected(entity);
            boolean causeDamage = true;
            if (protectedEntity) {
                boolean bl = causeDamage = this.rand.nextInt(3) == 0;
            }
            if (causeDamage) {
                entity.attackEntityFrom(DamageSource.MAGIC, 1.0f);
            }
            if (protectedEntity) continue;
            double x = entity.posX - this.posX;
            double z = entity.posZ - this.posZ;
            double d = Math.sqrt(x * x + z * z);
            entity.motionX = (double)0.8f * x / d;
            entity.motionY = this.rand.nextFloat() * 0.6f - 0.1f;
            entity.motionZ = (double)0.8f * z / d;
            EntityUtil.sendPlayerVelocityPacket(entity);
        }
        this.setDead();
    }

    public static boolean isProtected(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase entityLivingBase = (EntityLivingBase)entity;
        return entityLivingBase.getHeldItemMainhand().getItem() == MBItems.ENDERSOUL_HAND || entityLivingBase.getHeldItemOffhand().getItem() == MBItems.ENDERSOUL_HAND;
    }

    public SoundCategory getSoundCategory() {
        return this.isTamed() ? SoundCategory.NEUTRAL : SoundCategory.HOSTILE;
    }

    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setBoolean("Tamed", this.isTamed());
        compound.setInteger("ExplodeTick", this.explodeTick);
    }

    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.setTamed(compound.getBoolean("Collected") || compound.getBoolean("Tamed"));
        if (compound.hasKey("ExplodeTick")) {
            this.explodeTick = compound.getInteger("ExplodeTick");
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
