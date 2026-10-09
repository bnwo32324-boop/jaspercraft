package chumbanotz.mutantbeasts.entity.projectile;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MutantArrowEntity extends Entity {
    private static final DataParameter<Float> TARGET_X = EntityDataManager.createKey(MutantArrowEntity.class, (DataSerializer) DataSerializers.FLOAT);
    private static final DataParameter<Float> TARGET_Y = EntityDataManager.createKey(MutantArrowEntity.class, (DataSerializer) DataSerializers.FLOAT);
    private static final DataParameter<Float> TARGET_Z = EntityDataManager.createKey(MutantArrowEntity.class, (DataSerializer) DataSerializers.FLOAT);
    private static final DataParameter<Float> SPEED = EntityDataManager.createKey(MutantArrowEntity.class, (DataSerializer) DataSerializers.FLOAT);
    private static final DataParameter<Integer> CLONES = EntityDataManager.createKey(MutantArrowEntity.class, (DataSerializer) DataSerializers.VARINT);
    private float damage;
    private final List<Entity> pointedEntities;
    private PotionEffect potionEffect;
    private EntityLivingBase shooter;

    public MutantArrowEntity(World world) {
        super(world);
        this.damage = (float) MBConfig.ENTITIES.mutantSkeletonArrowDamage;
        this.pointedEntities = new ArrayList<Entity>();
        this.noClip = true;
    }

    public MutantArrowEntity(World world, EntityLivingBase shooter, EntityLivingBase target) {
        this(world);
        this.shooter = shooter;
        if (!world.isRemote) {
            this.setTargetX(target.posX);
            this.setTargetY(target.posY);
            this.setTargetZ(target.posZ);
        }
        double yPos = shooter.posY + (double) shooter.getEyeHeight();
        if (shooter instanceof MutantSkeletonEntity) {
            yPos = shooter.posY + 1.28;
        }
        this.setPosition(shooter.posX, yPos, shooter.posZ);
        double x = this.getTargetX() - this.posX;
        double y = this.getTargetY() - this.posY;
        double z = this.getTargetZ() - this.posZ;
        double d = Math.sqrt(x * x + z * z);
        this.rotationYaw = 180.0f + (float) Math.toDegrees(Math.atan2(x, z));
        this.rotationPitch = (float) Math.toDegrees(Math.atan2(y, d));
    }

    protected void entityInit() {
        this.dataManager.register(TARGET_X, Float.valueOf(0.0f));
        this.dataManager.register(TARGET_Y, Float.valueOf(0.0f));
        this.dataManager.register(TARGET_Z, Float.valueOf(0.0f));
        this.dataManager.register(SPEED, Float.valueOf(2.0f));
        this.dataManager.register(CLONES, 10);
    }

    public double getTargetX() {
        return this.dataManager.get(TARGET_X).floatValue();
    }

    public void setTargetX(double x) {
        this.dataManager.set(TARGET_X, Float.valueOf((float) x));
    }

    public double getTargetY() {
        return this.dataManager.get(TARGET_Y).floatValue();
    }

    public void setTargetY(double y) {
        this.dataManager.set(TARGET_Y, Float.valueOf((float) y));
    }

    public double getTargetZ() {
        return this.dataManager.get(TARGET_Z).floatValue();
    }

    public void setTargetZ(double z) {
        this.dataManager.set(TARGET_Z, Float.valueOf((float) z));
    }

    public float getSpeed() {
        return this.dataManager.get(SPEED).floatValue();
    }

    public void setSpeed(float speed) {
        this.dataManager.set(SPEED, Float.valueOf(speed));
    }

    public int getClones() {
        return this.dataManager.get(CLONES);
    }

    public void setClones(int clones) {
        this.dataManager.set(CLONES, clones);
    }

    public void randomize(float scale) {
        this.setTargetX(this.getTargetX() + (double) ((this.rand.nextFloat() - 0.5f) * scale * 2.0f));
        this.setTargetY(this.getTargetY() + (double) ((this.rand.nextFloat() - 0.5f) * scale * 2.0f));
        this.setTargetZ(this.getTargetZ() + (double) ((this.rand.nextFloat() - 0.5f) * scale * 2.0f));
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setPotionEffect(PotionEffect effect) {
        this.potionEffect = effect;
    }

    public void onUpdate() {
        super.onUpdate();
        double x = this.getTargetX() - this.posX;
        double y = this.getTargetY() - this.posY;
        double z = this.getTargetZ() - this.posZ;
        double d = Math.sqrt(x * x + z * z);
        this.rotationYaw = 180.0f + (float) Math.toDegrees(Math.atan2(x, z));
        if (this.rotationYaw > 360.0f) {
            this.rotationYaw -= 360.0f;
        }
        this.rotationPitch = (float) Math.toDegrees(Math.atan2(y, d));
        if (!this.world.isRemote) {
            if (this.ticksExisted == 2) {
                this.hitEntities(0);
            }
            if (this.ticksExisted == 3) {
                this.hitEntities(32);
            }
            if (this.ticksExisted == 4) {
                this.handleEntities();
            }
        }
        if (this.ticksExisted > 10) {
            this.setDead();
        }
    }

    private void hitEntities(int offset) {
        double targetX = this.getTargetX();
        double targetY = this.getTargetY();
        double targetZ = this.getTargetZ();
        double d3 = this.posX - targetX;
        double d4 = this.posY - targetY;
        double d5 = this.posZ - targetZ;
        double dist = MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
        double dx = (targetX - this.posX) / dist;
        double dy = (targetY - this.posY) / dist;
        double dz = (targetZ - this.posZ) / dist;
        for (int i = offset; i < offset + 64; ++i) {
            double x = this.posX + dx * (double) i * 0.5;
            double y = this.posY + dy * (double) i * 0.5;
            double z = this.posZ + dz * (double) i * 0.5;
            AxisAlignedBB box = new AxisAlignedBB(x, y, z, x, y, z).grow(0.3);
            this.pointedEntities.addAll(this.world.getEntitiesWithinAABBExcludingEntity(this.shooter, box));
        }
    }

    private void handleEntities() {
        this.pointedEntities.remove(this);
        DamageSource damageSource = new EntityDamageSourceIndirect("arrow", this, this.shooter) {

            public Vec3d getDamageLocation() {
                return null;
            }
        }.setProjectile();
        for (Entity entity : this.pointedEntities) {
            if ((!(entity instanceof MultiPartEntityPart) || !entity.attackEntityFrom(DamageSource.GENERIC.setExplosion(), this.damage)) && (!entity.canBeCollidedWith() || !entity.attackEntityFrom(damageSource, this.damage)))
                continue;
            this.applyEnchantments(this.shooter, entity);
            this.world.playSound(null, entity.posX, entity.posY, entity.posZ, SoundEvents.ENTITY_ARROW_HIT, this.getSoundCategory(), 1.0f, 1.2f / (this.rand.nextFloat() * 0.2f + 0.9f));
            if (this.potionEffect == null || !(entity instanceof EntityLivingBase)) continue;
            ((EntityLivingBase) entity).addPotionEffect(this.potionEffect);
            if (!MBConfig.ENTITIES.mutantSkeletonArrowPhasing) this.setDead();
        }
        this.pointedEntities.clear();
    }

    public boolean isInRangeToRender3d(double x, double y, double z) {
        return true;
    }

    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }

    protected void writeEntityToNBT(NBTTagCompound compound) {
    }

    protected void readEntityFromNBT(NBTTagCompound compound) {
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
