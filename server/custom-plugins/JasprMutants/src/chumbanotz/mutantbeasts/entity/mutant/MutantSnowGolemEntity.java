package chumbanotz.mutantbeasts.entity.mutant;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.ai.EntityAIAvoidDamage;
import chumbanotz.mutantbeasts.entity.ai.EntityAIFleeRain;
import chumbanotz.mutantbeasts.entity.ai.EntityAIHurtByNearestTarget;
import chumbanotz.mutantbeasts.entity.projectile.ThrowableBlockEntity;
import chumbanotz.mutantbeasts.pathfinding.MBGroundPathNavigator;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import com.google.common.base.Optional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackRanged;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.event.ForgeEventFactory;

public class MutantSnowGolemEntity extends EntityGolem implements IRangedAttackMob, IEntityOwnable, IShearable {
    private static final DataParameter<Optional<UUID>> OWNER_UNIQUE_ID = EntityDataManager.createKey(MutantSnowGolemEntity.class, (DataSerializer) DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final DataParameter<Byte> DATA_FLAGS = EntityDataManager.createKey(MutantSnowGolemEntity.class, (DataSerializer) DataSerializers.BYTE);
    private boolean isThrowing;
    private int throwingTick;

    public MutantSnowGolemEntity(World worldIn) {
        super(worldIn);
        this.setPathPriority(PathNodeType.WATER, -1.0f);
        this.setSize(1.1f, 2.2f);
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new SwimJumpGoal());
        this.tasks.addTask(1, new EntityAIFleeRain(this, 1.1f));
        this.tasks.addTask(2, new ThrowIceGoal());
        this.tasks.addTask(3, new EntityAIAttackRanged(this, 1.1f, 30, 12.0f));
        this.tasks.addTask(4, new EntityAIMoveTowardsRestriction(this, 1.1f));
        this.tasks.addTask(5, new EntityAIAvoidDamage(this, 1.1f));
        this.tasks.addTask(6, new EntityAIWanderAvoidWater(this, 1.0, 1.0000001E-5f));
        this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0f));
        this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityLiving.class, 6.0f));
        this.tasks.addTask(9, new EntityAILookIdle(this));
        this.targetTasks.addTask(0, new EntityAIHurtByNearestTarget(this));
        this.targetTasks.addTask(1, new EntityAINearestAttackableTarget(this, EntityLiving.class, 0, true, false, entity ->
                (IMob.MOB_SELECTOR.apply((Entity) entity) && (!(entity instanceof EntityCreeper) || ((EntityCreeper) entity).getAttackTarget() == this))));
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(MBConfig.ENTITIES.mutantSnowGolemArmor);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(MBConfig.ENTITIES.mutantSnowGolemKnockbackResistance);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(MBConfig.ENTITIES.mutantSnowGolemMaxHealth);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(MBConfig.ENTITIES.mutantSnowGolemMovementSpeed);
        this.getEntityAttribute(SWIM_SPEED).setBaseValue(MBConfig.ENTITIES.mutantSnowGolemSwimSpeed);
    }

    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(OWNER_UNIQUE_ID, Optional.absent());
        this.dataManager.register(DATA_FLAGS, (byte) 1);
    }

    @Nullable
    public EntityPlayer getOwner() {
        UUID uuid = this.getOwnerId();
        return uuid == null ? null : this.world.getPlayerEntityByUUID(uuid);
    }

    @Nullable
    public UUID getOwnerId() {
        return (UUID) ((Optional) this.dataManager.get(OWNER_UNIQUE_ID)).orNull();
    }

    public void setOwnerId(@Nullable UUID uuid) {
        this.dataManager.set(OWNER_UNIQUE_ID, Optional.fromNullable(uuid));
    }

    public boolean isPumpkinEquipped() {
        return ((Byte) this.dataManager.get(DATA_FLAGS) & 1) != 0;
    }

    public void setPumpkinEquipped(boolean pumpkinEquipped) {
        byte b0 = (Byte) this.dataManager.get(DATA_FLAGS);
        this.dataManager.set(DATA_FLAGS, pumpkinEquipped ? (byte) (b0 | 1) : (byte) (b0 & 0xFFFFFFFE));
    }

    public boolean getSwimJump() {
        return ((Byte) this.dataManager.get(DATA_FLAGS) & 4) != 0;
    }

    public void setSwimJump(boolean swimJump) {
        byte b0 = (Byte) this.dataManager.get(DATA_FLAGS);
        this.dataManager.set(DATA_FLAGS, swimJump ? (byte) (b0 | 4) : (byte) (b0 & 0xFFFFFFFB));
    }

    protected PathNavigate createNavigator(World worldIn) {
        return new MBGroundPathNavigator(this, worldIn).setAvoidRain(true);
    }

    public float getEyeHeight() {
        return 2.0f;
    }

    public boolean canAttackClass(Class<? extends EntityLivingBase> cls) {
        return super.canAttackClass(cls) && IMob.class.isAssignableFrom(cls);
    }

    public int getMaxSpawnedInChunk() {
        return 1;
    }

    public void onUpdate() {
        super.onUpdate();
        if (this.world.isRemote && this.getSwimJump()) {
            EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.SNOWBALL, 6, new int[0]);
            EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.WATER_SPLASH, 6, new int[0]);
        }
        if (this.isThrowing && this.throwingTick++ >= 20) {
            this.isThrowing = false;
            this.throwingTick = 0;
        }
        if (this.ticksExisted % 20 == 0 && this.isWet() && MBConfig.ENTITIES.mutantSnowGolemWaterWeakness) {
            this.attackEntityFrom(DamageSource.DROWN, 1.0f);
        }
        if (this.world.provider.isNether() && MBConfig.ENTITIES.mutantSnowGolemNetherWeakness) {
            if (this.rand.nextFloat() > Math.min(80.0f, this.getHealth()) * 0.01f) {
                this.world.spawnParticle(EnumParticleTypes.WATER_DROP, this.posX + (double) (this.rand.nextFloat() * this.width * 1.5f) - (double) this.width, this.posY - 0.15 + (double) (this.rand.nextFloat() * this.height), this.posZ + (double) (this.rand.nextFloat() * this.width * 1.5f) - (double) this.width, 0.0, 0.0, 0.0, new int[0]);
            }
            if (this.ticksExisted % 60 == 0) {
                this.attackEntityFrom(DamageSource.ON_FIRE, 1.0f);
            }
        } else if (!this.world.isRemote && this.onGround && ForgeEventFactory.getMobGriefingEvent(this.world, this)) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos posDown = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos posUp = new BlockPos.MutableBlockPos();
            for (int i = -2; i <= 2; ++i) {
                for (int j = -2; j <= 2; ++j) {
                    boolean placeIce;
                    if (Math.abs(i) == 2 && Math.abs(j) == 2) continue;
                    pos.setPos(MathHelper.floor((double) this.posX) + i, MathHelper.floor((double) this.posY), MathHelper.floor((double) this.posZ) + j);
                    posDown.setPos(pos).setY(pos.getY() - 1);
                    posUp.setPos(pos).setY(pos.getY() + 1);
                    boolean placeSnow = this.world.getBiome(pos).getTemperature(pos) < 0.95f && this.world.isAirBlock(pos) && Blocks.SNOW_LAYER.canPlaceBlockAt(this.world, pos);
                    boolean bl = placeIce = this.world.getBlockState(posDown).getBlock() == Blocks.WATER || this.world.getBlockState(posDown).getBlock() == Blocks.FLOWING_WATER;
                    if (this.world.getBlockState(pos).getBlock() == Blocks.FLOWING_WATER) {
                        this.world.setBlockState(pos, Blocks.ICE.getDefaultState());
                    }
                    if (this.world.getBlockState(posUp).getBlock() == Blocks.FLOWING_WATER) {
                        this.world.setBlockState(posUp, Blocks.ICE.getDefaultState());
                    }
                    if (placeSnow && ((Math.abs(i) == 2 || Math.abs(j) == 2) && this.rand.nextInt(20) != 0 || (Math.abs(i) == 1 || Math.abs(j) == 1) && this.rand.nextInt(10) != 0) || placeIce && ((Math.abs(i) == 2 || Math.abs(j) == 2) && this.rand.nextInt(14) != 0 || (Math.abs(i) == 1 || Math.abs(j) == 1) && this.rand.nextInt(6) != 0))
                        continue;
                    if (placeSnow) {
                        this.world.setBlockState(pos, Blocks.SNOW_LAYER.getDefaultState());
                    }
                    if (!placeIce) continue;
                    this.world.setBlockState(posDown, Blocks.ICE.getDefaultState());
                }
            }
        }
        if (!this.world.isRemote && this.ticksExisted % 40 == 0 && this.isEntityAlive() && this.getHealth() < this.getMaxHealth() && this.isSnowingAt(new BlockPos(this))) {
            this.heal(1.0f);
        }
    }

    private boolean isSnowingAt(BlockPos position) {
        if (!this.world.isRaining()) {
            return false;
        }
        if (!this.world.canSeeSky(position)) {
            return false;
        }
        if (this.world.getPrecipitationHeight(position).getY() > position.getY()) {
            return false;
        }
        Biome biome = this.world.getBiome(position);
        return biome.getEnableSnow() && biome.getTemperature(position) < 0.15f;
    }

    protected void updateAITasks() {
        if (this.getLeashed()) {
            return;
        }
        EntityPlayer owner = this.getOwner();
        if (owner != null && owner.isEntityAlive()) {
            this.setHomePosAndDistance(new BlockPos(owner), this.getAttackTarget() == null ? 8 : 16);
        } else if (this.hasHome()) {
            this.detachHome();
        }
    }

    public boolean isThrowing() {
        return this.isThrowing;
    }

    public int getThrowingTick() {
        return this.throwingTick;
    }

    private void startThrowing() {
        this.isThrowing = true;
        this.throwingTick = 0;
        if (!this.world.isRemote) {
            this.world.setEntityState(this, (byte) 0);
        }
    }

    // JasperCraft port: client-only (Entity.handleStatusUpdate is not in the server jar, so the super call is left out);
    // the server never calls it, the client stage ports it.
    public void handleStatusUpdate(byte id) {
        if (id == 0) {
            this.startThrowing();
        } else {
            if (id == 2 || id == 33 || id == 36 || id == 37) {
                EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.SNOWBALL, 30, new int[0]);
            }
        }
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.getImmediateSource() instanceof EntitySnowball) {
            if (this.getHealth() < this.getMaxHealth()) {
                if (!this.world.isRemote) {
                    this.heal(1.0f);
                }
                EntityUtil.spawnParticleAtEntity(this, EnumParticleTypes.HEART, 1, new int[0]);
            }
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    public void attackEntityWithRangedAttack(EntityLivingBase target, float distanceFactor) {
        if (!this.isThrowing && distanceFactor < 1.0f) {
            this.isThrowing = true;
        }
    }

    public void setSwingingArms(boolean swingingArms) {
    }

    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (itemStack.interactWithEntity(player, this, hand)) {
            return true;
        }
        if ((this.getOwnerId() == null || player == this.getOwner()) && itemStack.getItem() != Items.SNOWBALL) {
            if (!this.world.isRemote) {
                this.setOwnerId(this.getOwnerId() == null ? player.getUniqueID() : null);
            }
            return true;
        }
        return false;
    }

    public boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos) {
        return this.isPumpkinEquipped();
    }

    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        this.setPumpkinEquipped(false);
        return Collections.singletonList(new ItemStack(Blocks.LIT_PUMPKIN));
    }

    public void onDeath(DamageSource cause) {
        if (!this.world.isRemote && this.world.getGameRules().getBoolean("showDeathMessages") && this.getOwner() instanceof EntityPlayerMP) {
            this.getOwner().sendMessage(this.getCombatTracker().getDeathMessage());
        }
        super.onDeath(cause);
    }

    public boolean isOnSameTeam(Entity entityIn) {
        if (entityIn == this || entityIn == this.getOwner() || super.isOnSameTeam(entityIn)) {
            return true;
        }
        if (!(entityIn instanceof IMob)) {
            return this.getAttackTarget() != entityIn && (!(entityIn instanceof EntityLiving) || ((EntityLiving) entityIn).getAttackTarget() != this) && this.getTeam() == null && entityIn.getTeam() == null;
        }
        return false;
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("Pumpkin", this.isPumpkinEquipped());
        if (this.getOwnerId() != null) {
            compound.setUniqueId("OwnerUUID", this.getOwnerId());
        }
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("Pumpkin")) {
            this.setPumpkinEquipped(compound.getBoolean("Pumpkin"));
        }
        if (compound.hasUniqueId("OwnerUUID")) {
            this.setOwnerId(compound.getUniqueId("OwnerUUID"));
        }
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return MBSoundEvents.ENTITY_MUTANT_SNOW_GOLEM_HURT;
    }

    protected SoundEvent getDeathSound() {
        return MBSoundEvents.ENTITY_MUTANT_SNOW_GOLEM_DEATH;
    }

    protected void playStepSound(BlockPos pos, Block blockIn) {
        this.playSound(SoundEvents.BLOCK_SNOW_STEP, 0.15f, 1.0f);
    }

    protected ResourceLocation getLootTable() {
        return EntityUtil.getLootTable(this);
    }

    class ThrowIceGoal
            extends EntityAIBase {
        private EntityLivingBase attackTarget;

        ThrowIceGoal() {
        }

        public boolean shouldExecute() {
            this.attackTarget = MutantSnowGolemEntity.this.getAttackTarget();
            return this.attackTarget != null && MutantSnowGolemEntity.this.isThrowing;
        }

        public void startExecuting() {
            MutantSnowGolemEntity.this.startThrowing();
        }

        public boolean shouldContinueExecuting() {
            return MutantSnowGolemEntity.this.isThrowing && MutantSnowGolemEntity.this.throwingTick < 20;
        }

        public void updateTask() {
            MutantSnowGolemEntity.this.getNavigator().clearPath();
            MutantSnowGolemEntity.this.renderYawOffset = MutantSnowGolemEntity.this.rotationYaw;
            if (MutantSnowGolemEntity.this.throwingTick == 7) {
                ThrowableBlockEntity block = new ThrowableBlockEntity(MutantSnowGolemEntity.this.world, MutantSnowGolemEntity.this);
                block.posY += 1.0;
                double x = this.attackTarget.posX - block.posX;
                double y = this.attackTarget.posY - block.posY;
                double z = this.attackTarget.posZ - block.posZ;
                double xz = Math.sqrt(x * x + z * z);
                block.shoot(x, y + xz * (double) 0.4f, z, 0.9f, 1.0f);
                MutantSnowGolemEntity.this.world.spawnEntity(block);
            }
        }
    }

    class SwimJumpGoal
            extends EntityAIBase {
        private int jumpTick = 20;
        private boolean waterReplaced;
        private BlockPos.MutableBlockPos prevPos;

        public SwimJumpGoal() {
            this.setMutexBits(4);
            ((MBGroundPathNavigator) MutantSnowGolemEntity.this.navigator).setCanSwim(true);
        }

        public boolean shouldExecute() {
            return MutantSnowGolemEntity.this.isInWater();
        }

        public void startExecuting() {
            this.prevPos = new BlockPos.MutableBlockPos(MathHelper.floor((double) MutantSnowGolemEntity.this.posX), MathHelper.floor((double) MutantSnowGolemEntity.this.getEntityBoundingBox().minY) - 1, MathHelper.floor((double) MutantSnowGolemEntity.this.posZ));
            MutantSnowGolemEntity.this.motionX = (MutantSnowGolemEntity.this.rand.nextFloat() - MutantSnowGolemEntity.this.rand.nextFloat()) * 0.9f;
            MutantSnowGolemEntity.this.motionY = 1.5;
            MutantSnowGolemEntity.this.motionZ = (MutantSnowGolemEntity.this.rand.nextFloat() - MutantSnowGolemEntity.this.rand.nextFloat()) * 0.9f;
            MutantSnowGolemEntity.this.attackEntityFrom(DamageSource.DROWN, 16.0f);
            MutantSnowGolemEntity.this.setSwimJump(true);
        }

        public boolean shouldContinueExecuting() {
            return this.jumpTick > 0;
        }

        public void updateTask() {
            --this.jumpTick;
            if (!this.waterReplaced && !MutantSnowGolemEntity.this.isInWater() && this.jumpTick < 17 && ForgeEventFactory.getMobGriefingEvent(MutantSnowGolemEntity.this.world, MutantSnowGolemEntity.this)) {
                this.prevPos.setY(this.getWaterSurfaceHeight(MutantSnowGolemEntity.this.world, this.prevPos));
                if ((double) this.prevPos.getY() > MutantSnowGolemEntity.this.posY) {
                    return;
                }
                for (int x = -2; x <= 2; ++x) {
                    for (int y = -1; y <= 1; ++y) {
                        for (int z = -2; z <= 2; ++z) {
                            if (y != 0 && (Math.abs(x) == 2 || Math.abs(z) == 2)) continue;
                            BlockPos blockPos = this.prevPos.add(x, y, z);
                            Block block = MutantSnowGolemEntity.this.world.getBlockState(blockPos).getBlock();
                            if (!MutantSnowGolemEntity.this.world.isAirBlock(blockPos) && block != Blocks.WATER && block != Blocks.FLOWING_WATER || (y != 0 ? (Math.abs(x) == 1 || Math.abs(z) == 1) && MutantSnowGolemEntity.this.rand.nextInt(4) == 0 : (Math.abs(x) == 2 || Math.abs(z) == 2) && MutantSnowGolemEntity.this.rand.nextInt(3) == 0))
                                continue;
                            MutantSnowGolemEntity.this.world.setBlockState(blockPos, Blocks.ICE.getDefaultState());
                        }
                    }
                }
                BlockPos posUp2 = this.prevPos.up(2);
                if (MutantSnowGolemEntity.this.world.isAirBlock(posUp2)) {
                    MutantSnowGolemEntity.this.world.setBlockState(posUp2, Blocks.ICE.getDefaultState());
                }
                this.waterReplaced = true;
            }
        }

        public void resetTask() {
            this.jumpTick = 20;
            this.waterReplaced = false;
            MutantSnowGolemEntity.this.setSwimJump(false);
        }

        private int getWaterSurfaceHeight(World world, BlockPos coord) {
            Block block;
            int y = coord.getY();
            while ((block = world.getBlockState(new BlockPos(coord.getX(), y, coord.getZ())).getBlock()) == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                ++y;
            }
            return y;
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
