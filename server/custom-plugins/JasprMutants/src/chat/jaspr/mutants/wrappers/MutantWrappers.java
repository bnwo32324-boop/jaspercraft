package chat.jaspr.mutants.wrappers;

import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import chumbanotz.mutantbeasts.entity.EndersoulCloneEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSnowGolemEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity;
import chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity;
import chumbanotz.mutantbeasts.entity.projectile.ThrowableBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftCreeper;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftGolem;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftMonster;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftTameableAnimal;
import org.bukkit.entity.EntityType;

/**
 * Bukkit wrappers for the mod's entities. CraftBukkit's CraftEntity.getEntity factory throws for classes it does not
 * know (BodyPart, the projectiles, the tameables, the snow golem), so every mod class returns one of these from
 * getBukkitEntity(). Each implements the natural Bukkit interface of its vanilla base: Monster for the mutants and
 * endersoul clones (turrets, RPG and daylight logic treat them as mobs), Creeper for the mutant creeper, Golem for
 * the snow golem, Tameable animals for the spider pig and creeper minion, Projectile for Chemical X and thrown blocks.
 * Bukkit's EntityType has no constants for mod entities: getType() is UNKNOWN, as CraftBukkit reports for any
 * living entity it has no type for; MutantsApi.kindOf(entity) names the mod kind.
 */
public final class MutantWrappers {
    private MutantWrappers() {
    }

    public static CraftEntity create(Entity entity) {
        CraftServer server = (CraftServer) Bukkit.getServer();
        if (entity instanceof MutantCreeperEntity) return new Creeper(server, (MutantCreeperEntity) entity);
        if (entity instanceof MutantZombieEntity || entity instanceof MutantSkeletonEntity || entity instanceof MutantEndermanEntity
                || entity instanceof EndersoulCloneEntity) {
            return new Monster(server, (net.minecraft.entity.monster.EntityMob) entity);
        }
        if (entity instanceof MutantSnowGolemEntity) return new Golem(server, (MutantSnowGolemEntity) entity);
        if (entity instanceof SpiderPigEntity || entity instanceof CreeperMinionEntity) {
            return new Tameable(server, (net.minecraft.entity.passive.EntityTameable) entity);
        }
        if (entity instanceof ChemicalXEntity || entity instanceof ThrowableBlockEntity) return new Projectile(server, entity);
        return new Plain(server, entity);
    }

    static String describe(Entity entity) {
        ResourceLocation key = EntityList.getKey(entity);
        return "CraftMutant{" + (key == null ? entity.getClass().getSimpleName() : key.toString()) + "}";
    }

    /** BodyPart, CreeperMinionEgg, EndersoulFragment, MutantArrow, SkullSpirit. */
    public static final class Plain extends CraftEntity {
        Plain(CraftServer server, Entity entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }

    /** ChemicalX, ThrowableBlock (EntityThrowable: CraftBukkit casts its wrapper to Projectile for ProjectileHitEvent). */
    public static final class Projectile extends CraftProjectile {
        Projectile(CraftServer server, Entity entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }

    /** MutantZombie, MutantSkeleton, MutantEnderman, EndersoulClone. */
    public static final class Monster extends CraftMonster {
        Monster(CraftServer server, net.minecraft.entity.monster.EntityMob entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }

    /** MutantCreeper: a Bukkit Creeper (setPowered/isPowered reach the mod's own powered state). */
    public static final class Creeper extends CraftCreeper {
        Creeper(CraftServer server, MutantCreeperEntity entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }

    /** MutantSnowGolem. */
    public static final class Golem extends CraftGolem {
        Golem(CraftServer server, MutantSnowGolemEntity entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }

    /** SpiderPig, CreeperMinion. */
    public static final class Tameable extends CraftTameableAnimal {
        Tameable(CraftServer server, net.minecraft.entity.passive.EntityTameable entity) {
            super(server, entity);
        }

        @Override
        public EntityType getType() {
            return EntityType.UNKNOWN;
        }

        @Override
        public String toString() {
            return describe(this.getHandle());
        }
    }
}
