package chat.jaspr.biomes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;

/**
 * Spawners that work in daylight (3.22.0).
 *
 * A vanilla spawner only places a hostile mob where the light level is 7 or less, and most
 * structure rooms are lit -- torches, lamps, sky light that reached a room carved after the
 * first light pass. The owner asked that every spawner spawn "during daytime and nighttime".
 *
 * Every 40 ticks, for each authenticated player, the spawners within 16 blocks are checked.
 * One that has fired on its own within the last 200 ticks is left alone (it works). One that
 * has been silent that long is driven by hand the way vanilla would: its own mob type, a
 * random spot in the vanilla spawn box (+-4 x/z, +-1 y), air at feet and head over a solid
 * block, no more than 6 of that mob already nearby, up to 12 attempts, one mob per drive.
 * Nothing is written to the spawner's own data; spawners that work are never touched.
 */
public final class SpawnerDrive implements Listener {
    /** A spawner that fired within this many ticks is working and is not driven. */
    private static final long QUIET_TICKS = 200L;
    /** Player distance at which a spawner is active (vanilla's requiredPlayerRange). */
    private static final int RANGE = 16;
    /** Vanilla spawnRange: offsets are +-4 horizontally. */
    private static final int SPAWN_RANGE = 4;
    /** Vanilla maxNearbyEntities. */
    private static final int MAX_NEARBY = 6;
    private static final int ATTEMPTS = 12;

    private final HorrorPlugin plugin;
    /** Packed spawner position -> world full time it last produced a mob (or was first seen). */
    private final Map<Long, Long> lastFired = new HashMap<>();
    private final Random random = new Random();
    private long driven, covered;
    private int task = -1;

    SpawnerDrive(HorrorPlugin plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 40L, 40L).getTaskId();
    }

    public void stop() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        task = -1;
        lastFired.clear();
        HandlerList.unregisterAll(this);
    }

    public String status() { return "spawnersDriven=" + driven + " spawnersCovered=" + covered; }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    /** A spawner that works on its own is recorded, and is then left alone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fired(SpawnerSpawnEvent e) {
        if (e.getSpawner() == null) return;
        Block block = e.getSpawner().getBlock();
        lastFired.put(key(block.getX(), block.getY(), block.getZ()), block.getWorld().getFullTime());
    }

    private void sweep() {
        Set<Long> seen = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!HorrorPlugin.authenticated(player)) continue;
            World world = player.getWorld();
            int cx = player.getLocation().getBlockX() >> 4, cz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    // Never load a chunk for this.
                    if (!world.isChunkLoaded(cx + dx, cz + dz)) continue;
                    for (BlockState state : world.getChunkAt(cx + dx, cz + dz).getTileEntities()) {
                        if (!(state instanceof CreatureSpawner)) continue;
                        Block block = state.getBlock();
                        long id = key(block.getX(), block.getY(), block.getZ());
                        if (!seen.add(id)) continue;
                        if (player.getLocation().distanceSquared(block.getLocation().add(.5, .5, .5)) > RANGE * RANGE) continue;
                        drive(block, false);
                    }
                }
            }
        }
    }

    /**
     * Spawns at most one mob for this spawner. With force=false a spawner that fired recently,
     * or that is seen for the first time, is only recorded. Returns the number of mobs spawned.
     */
    public int drive(Block block, boolean force) {
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return 0;
        long now = block.getWorld().getFullTime();
        long id = key(block.getX(), block.getY(), block.getZ());
        Long last = lastFired.get(id);
        if (!force && last != null && now - last < QUIET_TICKS) { covered++; return 0; }
        if (!force && last == null) { lastFired.put(id, now); return 0; }
        EntityType type = ((CreatureSpawner) state).getSpawnedType();
        if (type == null || !type.isAlive() || !type.isSpawnable()) return 0;
        int nearby = 0;
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(.5, .5, .5), 5, 4, 5)) {
            if (entity.getType() == type) nearby++;
        }
        if (nearby >= MAX_NEARBY) { lastFired.put(id, now); return 0; }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = block.getX() + random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE;
            int y = block.getY() + random.nextInt(3) - 1;
            int z = block.getZ() + random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE;
            if (y < 1 || y > 250) continue;
            Block feet = block.getWorld().getBlockAt(x, y, z);
            if (!open(feet) || !open(feet.getRelative(0, 1, 0))) continue;
            if (!feet.getRelative(0, -1, 0).getType().isSolid()) continue;
            Entity spawned;
            try {
                spawned = block.getWorld().spawnEntity(feet.getLocation().add(.5, 0, .5), type);
            } catch (RuntimeException e) {
                return 0;
            }
            if (spawned == null || !(spawned instanceof LivingEntity)) return 0;
            lastFired.put(id, now);
            driven++;
            return 1;
        }
        lastFired.put(id, now);
        return 0;
    }

    private static boolean open(Block block) {
        Material type = block.getType();
        return type == Material.AIR || type == Material.LONG_GRASS || type == Material.SNOW;
    }
}
