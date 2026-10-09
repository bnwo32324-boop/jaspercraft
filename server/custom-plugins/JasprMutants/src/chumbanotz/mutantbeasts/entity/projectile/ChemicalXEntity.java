package chumbanotz.mutantbeasts.entity.projectile;

import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import chumbanotz.mutantbeasts.entity.EndersoulCloneEntity;
import chumbanotz.mutantbeasts.entity.SkullSpiritEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSnowGolemEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.MBParticles;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class ChemicalXEntity extends EntityThrowable {
    private static final Map<Class<? extends EntityLiving>, Class<? extends EntityLiving>> MUTATIONS = (Map<Class<? extends EntityLiving>, Class<? extends EntityLiving>>)
            (new ImmutableMap.Builder())
                    .put(EntityCreeper.class, MutantCreeperEntity.class)
                    .put(EntityEnderman.class, MutantEndermanEntity.class)
                    .put(EntityPig.class, SpiderPigEntity.class)
                    .put(EntitySkeleton.class, MutantSkeletonEntity.class)
                    .put(EntitySnowman.class, MutantSnowGolemEntity.class)
                    .put(EntityZombie.class, MutantZombieEntity.class)
                    .build();

    public static final Predicate<Entity> IS_APPLICABLE;

    static {
        IS_APPLICABLE = (target -> {
            Class<?> entityClass = target.getClass();
            return (target.isNonBoss() && !MUTATIONS.containsValue(entityClass) && entityClass != CreeperMinionEntity.class && entityClass != EndersoulCloneEntity.class);
        });
    }

    public ChemicalXEntity(World worldIn) {
        super(worldIn);
    }

    public ChemicalXEntity(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public ChemicalXEntity(World worldIn, EntityLivingBase throwerIn) {
        super(worldIn, throwerIn);
    }

    protected float getGravityVelocity() {
        return 0.05f;
    }

    public void handleStatusUpdate(byte id) {
        if (id == 3) {
            float z;
            float y;
            float x;
            int i;
            for (i = this.rand.nextInt(5); i < 50; ++i) {
                x = (this.rand.nextFloat() - 0.5f) * 1.2f;
                y = this.rand.nextFloat() * 0.2f;
                z = (this.rand.nextFloat() - 0.5f) * 1.2f;
                this.world.spawnParticle(MBParticles.SKULL_SPIRIT, this.posX, this.posY, this.posZ, x, y, z, new int[0]);
            }
            for (i = 5 + this.rand.nextInt(3); i >= 0; --i) {
                x = (this.rand.nextFloat() - this.rand.nextFloat()) * 0.3f;
                y = 0.1f + this.rand.nextFloat() * 0.1f;
                z = (this.rand.nextFloat() - this.rand.nextFloat()) * 0.3f;
                this.world.spawnParticle(EnumParticleTypes.ITEM_CRACK, this.posX, this.posY, this.posZ, x, y, z, new int[]{Item.getIdFromItem((Item)MBItems.CHEMICAL_X)});
            }
        }
    }

    protected void onImpact(RayTraceResult result) {
        if (result.typeOfHit == RayTraceResult.Type.BLOCK && this.world.getBlockState(result.getBlockPos()).getCollisionBoundingBox(this.world, result.getBlockPos()) == Block.NULL_AABB) {
            return;
        }
        if (!this.world.isRemote) {
            EntityLiving target = null;
            if (result.typeOfHit == RayTraceResult.Type.ENTITY && result.entityHit instanceof EntityLiving && IS_APPLICABLE.test(result.entityHit)) {
                target = (EntityLiving)result.entityHit;
            } else {
                List list = this.world.getEntitiesWithinAABB(EntityLiving.class, this.getEntityBoundingBox().grow(12.0, 8.0, 12.0), IS_APPLICABLE);
                if (!list.isEmpty()) {
                    list.sort(new EntityAINearestAttackableTarget.Sorter(this));
                    EntityLiving nearestTarget = (EntityLiving)list.get(0);
                    if (this.getDistanceSq(nearestTarget) < 144.0) {
                        target = nearestTarget;
                    }
                }
            }
            if (target != null) {
                SkullSpiritEntity spirit = new SkullSpiritEntity(this.world, target);
                spirit.setPosition(this.posX, this.posY, this.posZ);
                this.world.spawnEntity(spirit);
            }
            this.playSound(SoundEvents.ENTITY_SPLASH_POTION_BREAK, 1.0f, (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2f + 1.0f);
            this.world.setEntityState(this, (byte)3);
            this.setDead();
        }
    }

    @Nullable
    public static Class<? extends EntityLiving> getMutantOf(EntityLiving target) {
        Class<?> entityClass = target.getClass();
        if (!MUTATIONS.containsKey(entityClass)) {
            return null;
        }
        if (!(entityClass != EntityPig.class || target.isPotionActive(MobEffects.UNLUCK) && target.getActivePotionEffect(MobEffects.UNLUCK).getAmplifier() == 13)) {
            return null;
        }
        if (target instanceof EntityZombie && target.isChild()) {
            return null;
        }
        return MUTATIONS.get(entityClass);
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
