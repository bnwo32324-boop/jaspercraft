package chumbanotz.mutantbeasts.entity;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.EntityUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

public class BodyPartEntity
        extends Entity
        implements IEntityAdditionalSpawnData {
    private int part;
    private final boolean yawPositive;
    private final boolean pitchPositive;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int despawnTimer;

    public BodyPartEntity(World world) {
        super(world);
        this.prevRotationYaw = this.rotationYaw = this.rand.nextFloat() * 360.0f;
        this.prevRotationPitch = this.rotationPitch = this.rand.nextFloat() * 360.0f;
        this.yawPositive = this.rand.nextBoolean();
        this.pitchPositive = this.rand.nextBoolean();
        this.setSize(0.7f, 0.7f);
    }

    public BodyPartEntity(World world, EntityLiving owner, int bodyPart) {
        this(world);
        this.setPosition(owner.posX, owner.posY + (double) (3.2f * (0.25f + this.rand.nextFloat() * 0.5f)), owner.posZ);
        this.part = bodyPart;
        if (owner.isBurning()) {
            this.setFire(EntityUtil.getFire(owner) / 20);
        }
    }

    protected void entityInit() {
    }

    public int getPart() {
        return this.part;
    }

    public ItemStack getPickedResult(RayTraceResult target) {
        return new ItemStack(this.getItemByPart());
    }

    protected boolean canTriggerWalking() {
        return false;
    }

    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.setPosition(x, y, z);
        this.motionX = this.velocityX;
        this.motionY = this.velocityY;
        this.motionZ = this.velocityZ;
    }

    public void setVelocity(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
        this.motionX = this.velocityX;
        this.motionY = this.velocityY;
        this.motionZ = this.velocityZ;
    }

    public void onUpdate() {
        super.onUpdate();
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (!this.hasNoGravity()) {
            this.motionY -= 0.045;
        }
        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.96;
        this.motionY *= 0.96;
        this.motionZ *= 0.96;
        if (this.onGround) {
            this.motionX *= 0.7;
            this.motionY *= 0.7;
            this.motionZ *= 0.7;
        }
        if (!this.onGround && !this.isInWeb) {
            this.rotationYaw += 10.0f * (float) (this.yawPositive ? 1 : -1);
            this.rotationPitch += 15.0f * (float) (this.pitchPositive ? 1 : -1);
            for (Entity entity : this.world.getEntitiesInAABBexcluding(this, this.getEntityBoundingBox(), this::canHarm)) {
                if (this.isBurning()) {
                    entity.setFire(EntityUtil.getFire(this) / 20);
                }
                entity.attackEntityFrom(DamageSource.causeThrownDamage((Entity) this, (Entity) this), 4.0f + (float) this.rand.nextInt(4));
            }
            if (this.despawnTimer > 0) {
                --this.despawnTimer;
            }
        } else {
            ++this.despawnTimer;
        }
        if (!this.world.isRemote && this.despawnTimer >= (MBConfig.ENTITIES.mutantSkeletonBoneDrops ? 6000 : 100)) {
            this.setDead();
        }
    }

    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (MBConfig.ENTITIES.mutantSkeletonBoneDrops) {
            if (!this.world.isRemote && this.world.getGameRules().getBoolean("doEntityDrops")) {
                this.dropItem(this.getItemByPart(), 1).setNoPickupDelay();
            }
            player.swingArm(hand);
            this.setDead();
        }

        return true;
    }

    private boolean canHarm(Entity entity) {
        return entity.canBeCollidedWith() && !(entity instanceof MutantSkeletonEntity);
    }

    public Item getItemByPart() {
        if (this.part == 0) {
            return MBItems.MUTANT_SKELETON_PELVIS;
        }
        if (this.part >= 1 && this.part < 19) {
            return MBItems.MUTANT_SKELETON_RIB;
        }
        if (this.part == 19) {
            return MBItems.MUTANT_SKELETON_SKULL;
        }
        if (this.part >= 21 && this.part < 29) {
            return MBItems.MUTANT_SKELETON_LIMB;
        }
        if (this.part == 29 || this.part == 30) {
            return MBItems.MUTANT_SKELETON_SHOULDER_PAD;
        }
        return Items.AIR;
    }

    public String getName() {
        return this.hasCustomName() ? this.getCustomNameTag() : I18n.translateToLocal((String) (this.getItemByPart().getTranslationKey() + ".name"));
    }

    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setByte("Part", (byte) this.part);
        compound.setShort("DespawnTimer", (short) this.despawnTimer);
    }

    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.part = compound.getByte("Part");
        this.despawnTimer = compound.getShort("DespawnTimer");
    }

    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeByte(this.part);
    }

    public void readSpawnData(ByteBuf additionalData) {
        this.part = additionalData.readByte();
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
