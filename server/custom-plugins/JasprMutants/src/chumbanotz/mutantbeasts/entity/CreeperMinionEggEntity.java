package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import chumbanotz.mutantbeasts.util.MutatedExplosion;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class CreeperMinionEggEntity
extends Entity
implements IEntityOwnable {
    private static final DataParameter<Boolean> CHARGED = EntityDataManager.createKey(CreeperMinionEggEntity.class, (DataSerializer)DataSerializers.BOOLEAN);
    private int health = 8;
    private int age = (60 + this.rand.nextInt(40)) * 1200;
    private int recentlyHit;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private UUID ownerUUID;

    public CreeperMinionEggEntity(World world) {
        super(world);
        this.preventEntitySpawning = true;
        this.setSize(0.5625f, 0.75f);
    }

    public CreeperMinionEggEntity(MutantCreeperEntity spawner, EntityPlayer owner) {
        this(spawner.world);
        this.ownerUUID = owner.getUniqueID();
        this.setPosition(spawner.posX, spawner.posY, spawner.posZ);
        if (spawner.getPowered()) {
            this.setCharged(true);
        }
    }

    protected void entityInit() {
        this.dataManager.register(CHARGED, false);
    }

    public boolean isCharged() {
        return (Boolean)this.dataManager.get(CHARGED);
    }

    private void setCharged(boolean charged) {
        this.dataManager.set(CHARGED, charged);
    }

    public EntityPlayer getOwner() {
        return this.ownerUUID == null ? null : this.world.getPlayerEntityByUUID(this.ownerUUID);
    }

    public UUID getOwnerId() {
        return this.ownerUUID;
    }

    public double getYOffset() {
        if (this.getRidingEntity() instanceof EntityPlayer) {
            return (double)this.height - (this.getRidingEntity().isSneaking() ? 0.3 : 0.2);
        }
        return 0.0;
    }

    public double getMountedYOffset() {
        return this.height;
    }

    protected boolean canTriggerWalking() {
        return false;
    }

    public AxisAlignedBB getCollisionBox(Entity entity) {
        return entity.canBePushed() ? entity.getEntityBoundingBox() : null;
    }

    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    public boolean canBePushed() {
        return !this.isDead;
    }

    public boolean canRiderInteract() {
        return true;
    }

    public boolean hitByEntity(Entity entityIn) {
        return entityIn == this.getRidingEntity();
    }

    // JasperCraft port: client-only (Entity.setPositionAndRotationDirect does not exist in the server jar, so the super
    // call is left out); the server never calls this method, the client stage ports it.
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.motionX = this.velocityX;
        this.motionY = this.velocityY;
        this.motionZ = this.velocityZ;
    }

    // JasperCraft port: client-only (Entity.setVelocity does not exist in the server jar, super call left out).
    public void setVelocity(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
    }

    private void hatch() {
        EntityPlayer player;
        CreeperMinionEntity minion = new CreeperMinionEntity(this.world);
        if (this.ownerUUID != null && (player = this.getOwner()) != null && !ForgeEventFactory.onAnimalTame(minion, player)) {
            minion.setTamedBy(player);
            minion.getAISit().setSitting(true);
            player.setLastAttackedEntity(null);
        }
        if (this.isCharged()) {
            minion.setPowered(true);
        }
        minion.setPosition(this.posX, this.posY, this.posZ);
        this.world.spawnEntity(minion);
        this.playSound(MBSoundEvents.ENTITY_CREEPER_MINION_EGG_HATCH, 0.7f, 0.9f + this.rand.nextFloat() * 0.1f);
        this.setDead();
    }

    public void onStruckByLightning(EntityLightningBolt lightningBolt) {
        super.onStruckByLightning(lightningBolt);
        this.setCharged(true);
    }

    public void onUpdate() {
        super.onUpdate();
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (!this.hasNoGravity()) {
            this.motionY -= (double)0.04f;
        }
        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        this.motionX *= (double)0.98f;
        this.motionY *= (double)0.98f;
        this.motionZ *= (double)0.98f;
        if (this.onGround) {
            this.motionX *= (double)0.7f;
            this.motionZ *= (double)0.7f;
        }
        if (this.isRiding() && (this.isEntityInsideOpaqueBlock() || this.getRidingEntity() instanceof EntityPlayer && (((EntityPlayer)this.getRidingEntity()).isElytraFlying() || ((EntityPlayer)this.getRidingEntity()).isPlayerSleeping()))) {
            this.playMountSound(false);
            this.dismountRidingEntity();
        }
        if (!this.world.isRemote) {
            if (this.health < 8 && this.ticksExisted - this.recentlyHit > 80 && this.ticksExisted % 20 == 0) {
                ++this.health;
            }
            if (--this.age <= 0) {
                this.hatch();
            }
        }
    }

    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (this.isRiding() && player == this.getRidingEntity()) {
            this.playMountSound(false);
            this.getEntityToRide(player).dismountRidingEntity();
            return true;
        }
        if (!player.isElytraFlying()) {
            this.startRiding(this.getEntityToRide(player), true);
            this.playMountSound(true);
            return true;
        }
        return false;
    }

    private Entity getEntityToRide(Entity entity) {
        List passengers = entity.getPassengers();
        return !passengers.isEmpty() ? this.getEntityToRide((Entity)passengers.get(0)) : entity;
    }

    private void playMountSound(boolean mount) {
        this.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.7f, (mount ? 0.6f : 0.3f) + this.rand.nextFloat() * 0.1f);
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isEntityInvulnerable(source) || source.getTrueSource() == this.getRidingEntity()) {
            return false;
        }
        if (!this.world.isRemote && !this.isDead && this.ticksExisted > 0) {
            this.markVelocityChanged();
            if (source.isExplosion()) {
                this.age -= (int)(amount * 80.0f);
                EntityUtil.sendParticlePacket(this, EnumParticleTypes.HEART, (int)(amount / 2.0f));
                return false;
            }
            this.recentlyHit = this.ticksExisted;
            this.motionY = 0.2f;
            this.health -= (int)amount;
            if (this.health <= 0) {
                MutatedExplosion.create(this, this.isCharged() ? 2.0f : 0.0f, false, true);
                if (this.world.getGameRules().getBoolean("doEntityDrops")) {
                    if (this.isCharged() || this.rand.nextInt(3) == 0) {
                        this.dropItem(MBItems.CREEPER_SHARD, 1);
                    } else {
                        for (int j = 5 + this.rand.nextInt(6); j > 0; --j) {
                            this.dropItem(Items.GUNPOWDER, 1);
                        }
                    }
                }
                this.setDead();
            }
            return true;
        }
        return false;
    }

    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("Health", this.health);
        compound.setInteger("Age", this.age);
        if (this.isCharged()) {
            compound.setBoolean("Charged", true);
        }
        if (this.ownerUUID != null) {
            compound.setUniqueId("OwnerUUID", this.ownerUUID);
        }
    }

    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.health = compound.getInteger("Health");
        if (compound.hasKey("Age")) {
            this.age = compound.getInteger("Age");
        }
        this.setCharged(compound.getBoolean("Charged"));
        if (compound.hasUniqueId("OwnerUUID")) {
            this.ownerUUID = compound.getUniqueId("OwnerUUID");
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
