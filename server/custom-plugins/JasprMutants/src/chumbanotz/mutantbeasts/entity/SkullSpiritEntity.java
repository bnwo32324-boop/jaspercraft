package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBParticles;
import chumbanotz.mutantbeasts.util.MutatedExplosion;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

public class SkullSpiritEntity
extends Entity
implements IEntityAdditionalSpawnData {
    private static final DataParameter<Boolean> ATTACHED = EntityDataManager.createKey(SkullSpiritEntity.class, (DataSerializer)DataSerializers.BOOLEAN);
    private int startTick = 15;
    private int attachedTick = 80 + this.rand.nextInt(40);
    private int targetId;
    private UUID targetUUID;

    public SkullSpiritEntity(World world) {
        super(world);
        this.noClip = true;
        this.setSize(0.1f, 0.1f);
    }

    public SkullSpiritEntity(World world, EntityLiving target) {
        this(world);
        this.targetId = target.getEntityId();
        this.targetUUID = target.getUniqueID();
    }

    protected void entityInit() {
        this.dataManager.register(ATTACHED, false);
    }

    public boolean isAttached() {
        return (Boolean)this.dataManager.get(ATTACHED);
    }

    private void setAttached(boolean attached) {
        this.dataManager.set(ATTACHED, attached);
    }

    public boolean doesEntityNotTriggerPressurePlate() {
        return true;
    }

    public Entity getTarget() {
        if (this.targetUUID != null && this.world instanceof WorldServer) {
            return ((WorldServer)((Object)this.world)).getEntityFromUuid(this.targetUUID);
        }
        return this.world.getEntityByID(this.targetId);
    }

    public void onUpdate() {
        Entity target = this.getTarget();
        if (target != null && target.isEntityAlive()) {
            if (this.isAttached()) {
                if (!this.world.isRemote) {
                    target.motionX = (this.rand.nextFloat() - this.rand.nextFloat()) * 0.1f;
                    target.motionZ = (this.rand.nextFloat() - this.rand.nextFloat()) * 0.1f;
                    if (--this.attachedTick <= 0 && target instanceof EntityLiving) {
                        EntityLiving mob = (EntityLiving)target;
                        Class<? extends EntityLiving> mutantClass = ChemicalXEntity.getMutantOf(mob);
                        if (mutantClass != null && this.rand.nextFloat() < 0.75f) {
                            MutatedExplosion.create(this, 2.0f, false, false);
                            EntityLiving mutant = EntityUtil.convertMobWithNBT(mob, (EntityLiving)EntityList.newEntity(mutantClass, (World)this.world), true);
                            mutant.enablePersistence();
                            AxisAlignedBB bb = mutant.getEntityBoundingBox();
                            for (BlockPos pos : BlockPos.getAllInBox((int)MathHelper.floor((double)bb.minX), (int)MathHelper.floor((double)bb.minY), (int)MathHelper.floor((double)bb.minZ), (int)MathHelper.floor((double)bb.maxX), (int)MathHelper.floor((double)bb.maxY), (int)MathHelper.floor((double)bb.maxZ))) {
                                if (!(this.world.getBlockState(pos).getBlockHardness(this.world, pos) >= 0.0f)) continue;
                                this.world.destroyBlock(pos, true);
                            }
                            for (EntityPlayerMP entityplayermp : this.world.getEntitiesWithinAABB(EntityPlayerMP.class, bb.grow(5.0))) {
                                CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayermp, mutant);
                            }
                        } else {
                            this.setAttached(false);
                            MutatedExplosion.create(this, 2.0f, false, false);
                        }
                        this.setDead();
                    }
                }
                this.setPosition(target.posX, target.posY, target.posZ);
                if (this.rand.nextInt(8) == 0) {
                    target.attackEntityFrom(DamageSource.MAGIC, 0.0f);
                }
                for (int i = 0; i < 3; ++i) {
                    double posX = target.posX + (double)(this.rand.nextFloat() * target.width * 2.0f) - (double)target.width;
                    double posY = target.posY + 0.5 + (double)(this.rand.nextFloat() * target.height);
                    double posZ = target.posZ + (double)(this.rand.nextFloat() * target.width * 2.0f) - (double)target.width;
                    double x = this.rand.nextGaussian() * 0.02;
                    double y = this.rand.nextGaussian() * 0.02;
                    double z = this.rand.nextGaussian() * 0.02;
                    this.world.spawnParticle(MBParticles.SKULL_SPIRIT, posX, posY, posZ, x, y, z, new int[0]);
                }
            } else {
                this.prevPosX = this.posX;
                this.prevPosY = this.posY;
                this.prevPosZ = this.posZ;
                this.motionX = 0.0;
                this.motionY = 0.0;
                this.motionZ = 0.0;
                if (this.startTick-- >= 0) {
                    this.motionY += (double)(0.3f * (float)this.startTick / 15.0f);
                }
                double x = target.posX - this.posX;
                double y = target.posY - this.posY;
                double z = target.posZ - this.posZ;
                double d = Math.sqrt(x * x + y * y + z * z);
                this.motionX += x / d * (double)0.2f;
                this.motionY += y / d * (double)0.2f;
                this.motionZ += z / d * (double)0.2f;
                this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                if (!this.world.isRemote && this.getDistanceSq(target) < 1.0) {
                    this.setAttached(true);
                }
                for (int i = 0; i < 16; ++i) {
                    float xx = (this.rand.nextFloat() - 0.5f) * 1.2f;
                    float yy = (this.rand.nextFloat() - 0.5f) * 1.2f;
                    float zz = (this.rand.nextFloat() - 0.5f) * 1.2f;
                    this.world.spawnParticle(MBParticles.SKULL_SPIRIT, this.posX + (double)xx, this.posY + (double)yy, this.posZ + (double)zz, 0.0, 0.0, 0.0, new int[0]);
                }
            }
        } else {
            this.setDead();
        }
    }

    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setBoolean("Attached", this.isAttached());
        compound.setInteger("AttachedTick", this.attachedTick);
        if (this.targetUUID != null) {
            compound.setUniqueId("Target", this.targetUUID);
        }
    }

    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.setAttached(compound.getBoolean("Attached"));
        this.attachedTick = compound.getInteger("AttachedTick");
        if (compound.hasUniqueId("Target")) {
            this.targetUUID = compound.getUniqueId("Target");
        }
    }

    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(this.targetId);
    }

    public void readSpawnData(ByteBuf additionalData) {
        this.targetId = additionalData.readInt();
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
