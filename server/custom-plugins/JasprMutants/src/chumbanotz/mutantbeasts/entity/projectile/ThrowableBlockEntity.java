package chumbanotz.mutantbeasts.entity.projectile;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.EndersoulFragmentEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSnowGolemEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.EntityUtil;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.common.registry.IThrowableEntity;

public class ThrowableBlockEntity extends EntityThrowable
implements IEntityAdditionalSpawnData,
IThrowableEntity {
    private static final DataParameter<Boolean> HELD = EntityDataManager.createKey(ThrowableBlockEntity.class, (DataSerializer)DataSerializers.BOOLEAN);
    private IBlockState blockState = Blocks.GRASS.getDefaultState();
    private UUID ownerUUID;

    public ThrowableBlockEntity(World worldIn) {
        super(worldIn);
        this.setSize(1.0f, 1.0f);
    }

    public ThrowableBlockEntity(World worldIn, MutantSnowGolemEntity mutantSnowGolem) {
        super(worldIn, mutantSnowGolem.posX, mutantSnowGolem.posY + 1.954, mutantSnowGolem.posZ);
        this.rotationYaw = mutantSnowGolem.rotationYaw;
        this.setThrower(mutantSnowGolem);
        this.blockState = Blocks.ICE.getDefaultState();
    }

    public ThrowableBlockEntity(World world, MutantEndermanEntity enderman, int armID) {
        super(world, enderman.posX, enderman.posY + 4.7, enderman.posZ);
        this.setThrower(enderman);
        this.blockState = Block.getStateById(enderman.heldBlock[armID]);
        boolean outer = armID <= 2;
        boolean right = (armID & 1) == 1;
        EntityLivingBase living = enderman.getAttackTarget();
        Vec3d forward = EntityUtil.getDirVector(this.rotationYaw, outer ? 2.7f : 1.4f);
        Vec3d strafe = EntityUtil.getDirVector(this.rotationYaw + (right ? 90.0f : -90.0f), outer ? 2.2f : 2.0f);
        this.posX += forward.x + strafe.x;
        this.posY += (outer ? 2.8f : 1.1f) - 4.8f;
        this.posZ += forward.z + strafe.z;
        if (living != null) {
            this.shoot(living.posX - this.posX, living.posY + (double)living.getEyeHeight() - this.posY, living.posZ - this.posZ, 1.4f, 1.0f);
        } else {
            this.throwBlock();
        }
    }

    public ThrowableBlockEntity(World world, EntityPlayer player, IBlockState blockState, BlockPos pos) {
        super(world, player);
        this.setThrower(player);
        this.blockState = blockState;
        this.setPosition((double)pos.getX() + 0.5, pos.getY(), (double)pos.getZ() + 0.5);
        this.setHeld(true);
    }

    protected void entityInit() {
        this.dataManager.register(HELD, false);
    }

    public IBlockState getBlockState() {
        return this.blockState;
    }

    @Nullable
    public EntityLivingBase getThrower() {
        Entity entity;
        if (this.thrower == null && this.ownerUUID != null && this.world instanceof WorldServer && (entity = ((WorldServer) this.world).getEntityFromUuid(this.ownerUUID)) instanceof EntityLivingBase) {
            this.setThrower(entity);
        }
        return this.thrower;
    }

    public void setThrower(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            this.thrower = (EntityLivingBase)entity;
            this.ownerUUID = entity.getUniqueID();
        }
    }

    public boolean isHeld() {
        return this.dataManager.get(HELD);
    }

    private void setHeld(boolean held) {
        this.dataManager.set(HELD, held);
    }

    protected float getGravityVelocity() {
        if (this.thrower instanceof MutantSnowGolemEntity) {
            return 0.06f;
        }
        if (this.thrower instanceof EntityPlayer) {
            return 0.04f;
        }
        return 0.01f;
    }

    protected boolean canTriggerWalking() {
        return false;
    }

    public boolean canBeCollidedWith() {
        return this.isHeld() && !this.isDead;
    }

    public boolean canBePushed() {
        return this.isHeld() && !this.isDead;
    }

    public boolean canBeAttackedWithItem() {
        return false;
    }

    public void applyEntityCollision(Entity entityIn) {
        if (entityIn != this.thrower) {
            super.applyEntityCollision(entityIn);
        }
    }

    public void handleStatusUpdate(byte id) {
        if (id == 3) {
            for (int i = 0; i < 60; ++i) {
                double x = this.posX + (double)(this.rand.nextFloat() * this.width * 2.0f) - (double)this.width;
                double y = this.posY + 0.5 + (double)(this.rand.nextFloat() * this.height);
                double z = this.posZ + (double)(this.rand.nextFloat() * this.width * 2.0f) - (double)this.width;
                double motx = (this.rand.nextFloat() - this.rand.nextFloat()) * 3.0f;
                double moty = 0.5f + this.rand.nextFloat() * 2.0f;
                double motz = (this.rand.nextFloat() - this.rand.nextFloat()) * 3.0f;
                this.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y, z, motx, moty, motz, Block.getStateId(this.blockState));
            }
        }
    }

    public void onUpdate() {
        if (this.isHeld()) {
            this.lastTickPosX = this.posX;
            this.lastTickPosY = this.posY;
            this.lastTickPosZ = this.posZ;
            if (!this.world.isRemote) {
                this.setFlag(6, this.isGlowing());
            }
            this.onEntityUpdate();
            if (!(this.thrower != null && this.thrower.isEntityAlive() && EntitySelectors.NOT_SPECTATING.apply(this.thrower) && EndersoulFragmentEntity.isProtected(this.thrower))) {
                this.setHeld(false);
            } else {
                Vec3d vec = this.thrower.getLookVec();
                double x = this.thrower.posX + vec.x * 1.6 - this.posX;
                double y = this.thrower.posY + (double)this.thrower.getEyeHeight() + vec.y * 1.6 - this.posY;
                double z = this.thrower.posZ + vec.z * 1.6 - this.posZ;
                float offset = 0.6f;
                this.motionX = x * (double)offset;
                this.motionY = y * (double)offset;
                this.motionZ = z * (double)offset;
                this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
            }
        } else {
            this.ignoreEntity = this.thrower;
            super.onUpdate();
        }
    }

    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (player.isSneaking() || itemStack.getItem() != MBItems.ENDERSOUL_HAND) {
            return false;
        }
        if (this.isHeld() && this.thrower == player) {
            if (!this.world.isRemote) {
                this.setHeld(false);
                this.throwBlock();
            }
            player.swingArm(hand);
            itemStack.damageItem(1, player);
            return true;
        }
        return false;
    }

    private void throwBlock() {
        this.rotationYaw = this.thrower.rotationYaw;
        this.rotationPitch = this.thrower.rotationPitch;
        float f = 0.4f;
        this.motionX = -MathHelper.sin(this.rotationYaw / 180.0f * (float)Math.PI) * MathHelper.cos(this.rotationPitch / 180.0f * (float)Math.PI) * f;
        this.motionY = -MathHelper.sin(this.rotationPitch / 180.0f * (float)Math.PI) * f;
        this.motionZ = MathHelper.cos(this.rotationYaw / 180.0f * (float)Math.PI) * MathHelper.cos(this.rotationPitch / 180.0f * (float)Math.PI) * f;
        this.shoot(this.motionX, this.motionY, this.motionZ, 1.4f, 1.0f);
    }

    protected void onImpact(RayTraceResult result) {
        if (result.typeOfHit == RayTraceResult.Type.BLOCK && this.world.getBlockState(result.getBlockPos()).getCollisionBoundingBox(this.world, result.getBlockPos()) == Block.NULL_AABB) {
            return;
        }
        if (this.thrower instanceof MutantSnowGolemEntity) {
            if (result.typeOfHit == RayTraceResult.Type.ENTITY && (this.thrower.isOnSameTeam(result.entityHit) || result.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, this.thrower), 4.0f) && result.entityHit instanceof EntityEnderman)) {
                return;
            }
            for (Entity entity : this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().grow(2.5, 2.0, 2.5))) {
                if (!entity.canBeCollidedWith() || this.thrower.isOnSameTeam(entity) || !(this.getDistanceSq(entity) <= 6.25)) continue;
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(this, this.thrower), 4.0f + (float) MBConfig.ENTITIES.mutantSnowGolemIceChunkDamage);
            }
            if (!this.world.isRemote) {
                this.playSound(chat.jaspr.mutants.compat.ForgeAccess.getSoundType(this.blockState.getBlock(), this.blockState, this.world, this.getPosition(), this).getBreakSound() /* JasperCraft port: Forge's Block.getSoundType(state, world, pos, entity) */, 0.8f, (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2f + 0.8f);
                this.world.setEntityState(this, (byte)3);
                this.setDead();
            }
        } else if (!this.world.isRemote) {
            Block block;
            boolean canOwnerGrief;
            BlockPos pos = new BlockPos(this);
            boolean bl = canOwnerGrief = !(this.thrower instanceof EntityLiving) || ForgeEventFactory.getMobGriefingEvent(this.world, this.thrower);
            if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
                if (canOwnerGrief && this.canPlaceBlock(result.getBlockPos(), result.sideHit)) {
                    SoundType soundType = chat.jaspr.mutants.compat.ForgeAccess.getSoundType(this.blockState.getBlock(), this.blockState, this.world, pos, this.thrower); // JasperCraft port: Forge's Block.getSoundType(state, world, pos, entity)
                    this.playSound(soundType.getPlaceSound(), (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
                } else {
                    this.world.playEvent(2001, pos, Block.getStateId(this.blockState));
                    if (this.world.getGameRules().getBoolean("doEntityDrops") && canOwnerGrief) {
                        block = this.blockState.getBlock();
                        this.entityDropItem(new ItemStack(block, 1, block.damageDropped(this.blockState)), 0.0f);
                    }
                }
            } else if (result.typeOfHit == RayTraceResult.Type.ENTITY) {
                if (result.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, this.thrower), 4.0f) && result.entityHit instanceof EntityEnderman) {
                    return;
                }
                this.world.playEvent(2001, pos, Block.getStateId(this.blockState));
                if (this.world.getGameRules().getBoolean("doEntityDrops") && canOwnerGrief) {
                    block = this.blockState.getBlock();
                    this.entityDropItem(new ItemStack(block, 1, block.damageDropped(this.blockState)), 0.0f);
                }
            }
            for (Entity entity : this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().grow(2.0))) {
                if (!entity.canBeCollidedWith() || entity.isEntityEqual(this.thrower) || !(this.getDistanceSq(entity) <= 4.0)) continue;
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(this, this.thrower), (float) MBConfig.ENTITIES.mutantEndermanBlockDamage);
            }
            this.setDead();
        }
    }

    private boolean canPlaceBlock(BlockPos pos, EnumFacing facing) {
        if (this.thrower instanceof EntityPlayer && !((EntityPlayer)this.thrower).isAllowEdit()) {
            return false;
        }
        if (!this.blockState.getBlock().isReplaceable(this.world, pos)) {
            pos = pos.offset(facing);
        }
        return this.world.mayPlace(this.blockState.getBlock(), pos, false, facing, this.thrower) && this.world.setBlockState(pos, this.blockState, 11);
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
        compound.setBoolean("Held", this.isHeld());
        compound.setTag("BlockState", NBTUtil.writeBlockState(new NBTTagCompound(), this.blockState));
        if (this.ownerUUID != null) {
            compound.setUniqueId("OwnerUUID", this.ownerUUID);
        }
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
        this.setHeld(compound.getBoolean("Held"));
        if (compound.hasKey("BlockState", 10)) {
            this.blockState = NBTUtil.readBlockState(compound.getCompoundTag("BlockState"));
        }
        if (compound.hasUniqueId("OwnerUUID")) {
            this.ownerUUID = compound.getUniqueId("OwnerUUID");
        }
    }

    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(Block.getStateId(this.blockState));
    }

    public void readSpawnData(ByteBuf additionalData) {
        this.blockState = Block.getStateById(additionalData.readInt());
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
