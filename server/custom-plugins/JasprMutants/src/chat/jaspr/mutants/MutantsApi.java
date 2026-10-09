package chat.jaspr.mutants;

import chat.jaspr.mutants.registry.EntityRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Public API of JasprMutants for other plugins (JasprDungeon calls it by reflection, so it needs no compile-time
 * dependency). Kinds are the mod's registry paths: mutant_zombie, mutant_skeleton, mutant_creeper, mutant_enderman,
 * mutant_snow_golem, spider_pig, creeper_minion, endersoul_clone.
 */
public final class MutantsApi {
    private MutantsApi() {
    }

    /**
     * Spawns the mod's living entity of this kind as a spawn egg or Bukkit's World.spawnEntity would (random yaw,
     * onInitialSpawn, Bukkit CreatureSpawnEvent with reason CUSTOM). Main thread only.
     *
     * @return the Bukkit entity, or null for an unknown kind, a cancelled spawn or a call off the main thread
     */
    public static LivingEntity spawn(String kind, Location at) {
        if (kind == null || at == null || at.getWorld() == null || !Bukkit.isPrimaryThread()) return null;
        JasprMutants plugin = JasprMutants.instance();
        if (plugin == null || !plugin.isReady()) return null;
        EntityEntry entry = EntityRegistry.entryOf(kind);
        if (entry == null || !EntityLiving.class.isAssignableFrom(entry.getEntityClass())) return null;
        WorldServer world = ((CraftWorld) at.getWorld()).getHandle();
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation("mutantbeasts", kind), world);
        if (!(entity instanceof EntityLiving)) return null;
        EntityLiving living = (EntityLiving) entity;
        living.setLocationAndAngles(at.getX(), at.getY(), at.getZ(), MathHelper.wrapDegrees(world.rand.nextFloat() * 360.0F), 0.0F);
        living.rotationYawHead = living.rotationYaw;
        living.renderYawOffset = living.rotationYaw;
        living.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(living)), null);
        if (!world.addEntity(living, CreatureSpawnEvent.SpawnReason.CUSTOM) || living.isDead) {
            plugin.spawnRefused(kind);
            return null;
        }
        plugin.spawned(kind);
        return (LivingEntity) living.getBukkitEntity();
    }

    /** The mod kind of an entity ("mutant_zombie" ...), or null for anything that is not one of the mod's entities. */
    public static String kindOf(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof CraftEntity)) return null;
        EntityEntry entry = EntityRegistry.entryOf(((CraftEntity) entity).getHandle().getClass());
        return entry == null ? null : entry.getRegistryName().getPath();
    }

    public static boolean isMutant(org.bukkit.entity.Entity entity) {
        return kindOf(entity) != null;
    }

    public static boolean available() {
        JasprMutants plugin = JasprMutants.instance();
        return plugin != null && plugin.isReady();
    }
}
