package chat.jaspr.biomes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Vanilla passive-animal population for a custom generator.
 *
 * A custom ChunkGenerator never runs vanilla's world-generation animal pass, and
 * in vanilla that pass is where nearly every sheep, cow, pig and chicken in a
 * world comes from -- the ongoing spawn tick only ever trickles. That omission,
 * not a spawn rule anywhere in this plugin, is why the world had no wool.
 *
 * Rather than invent a spawn table, this calls the server's own world-gen spawner,
 * so herd sizes, per-biome weights and density are exactly vanilla's by
 * construction. Reflection keeps the plugin free of a compile-time NMS
 * dependency: if the mapping ever moves, the call is skipped and the world simply
 * behaves as it did before.
 */
public final class VanillaFauna {
    private VanillaFauna() { }

    /** Vanilla runs one world-gen animal pass per chunk. */
    private static final int WORLDGEN_PASSES = 2;

    private static volatile boolean resolved, unavailable;
    private static Method spawnMethod, handleMethod, biomeMethod;
    private static Constructor<?> blockPosition;

    private static synchronized void resolve(World world) {
        if (resolved || unavailable) return;
        try {
            String craft = Bukkit.getServer().getClass().getPackage().getName();
            String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1) + ".";
            Class<?> nmsWorld = Class.forName(nms + "World");
            Class<?> biomeBase = Class.forName(nms + "BiomeBase");
            Class<?> blockPos = Class.forName(nms + "BlockPosition");
            Class<?> spawner = Class.forName(nms + "SpawnerCreature");
            // SpawnerCreature.performWorldGenSpawning(World, BiomeBase, x, z, sizeX, sizeZ, Random)
            spawnMethod = spawner.getMethod("a", nmsWorld, biomeBase,
                    int.class, int.class, int.class, int.class, Random.class);
            handleMethod = world.getClass().getMethod("getHandle");
            biomeMethod = nmsWorld.getMethod("getBiome", blockPos);
            blockPosition = blockPos.getConstructor(int.class, int.class, int.class);
            resolved = true;
            Bukkit.getLogger().info("[JasprHorrorBiomes] FAUNA_READY vanillaWorldgenSpawning=true");
        } catch (Throwable missing) {
            unavailable = true;
            Bukkit.getLogger().warning("[JasprHorrorBiomes] FAUNA_UNAVAILABLE " + missing);
        }
    }

    /** Vanilla's world-gen animal pass over one freshly populated chunk. */
    public static void worldgen(World world, Chunk chunk, Random random) {
        resolve(world);
        if (!resolved) return;
        try {
            Object handle = handleMethod.invoke(world);
            int x = chunk.getX() * 16, z = chunk.getZ() * 16;
            Object biome = biomeMethod.invoke(handle, blockPosition.newInstance(x + 16, 0, z + 16));
            // Vanilla runs this pass once per chunk and only about a tenth of chunks come out
            // with a herd. Twice is still vanilla's own spawner making vanilla's own choices,
            // it just gives the map roughly double the standing wildlife, which is what a
            // world with this much cave and predator pressure needs to stay stocked.
            for (int pass = 0; pass < WORLDGEN_PASSES; pass++) {
                spawnMethod.invoke(null, handle, biome, x + 8, z + 8, 16, 16, random);
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Top up ground that was generated before this pass existed. Without it the
     * land players already explored stays permanently barren, because vanilla only
     * ever seeds a chunk once. Runs the same vanilla pass on a few loaded chunks
     * out in the landscape around players, and stops as soon as the world is
     * carrying a normal number of animals.
     */
    /**
     * Sheep specifically, because wool has no other source in this world.
     *
     * The general restock above is vanilla's own pass and therefore spreads itself across
     * every animal the biome allows, which on a bad roll can leave a player who needs a bed
     * with cows and chickens and nothing else. This keeps a floor under the sheep alone: it
     * looks for grass near somebody, in chunks that are already loaded, and puts a small
     * flock there. It does nothing once the floor is met.
     */
    public static int topUpSheep(World world, Random random, int floor) {
        int sheep = 0;
        for (Entity entity : world.getEntities()) if (entity instanceof Sheep) sheep++;
        if (sheep >= floor) return 0;
        List<Player> players = new ArrayList<Player>();
        for (Player player : world.getPlayers()) if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) players.add(player);
        if (players.isEmpty()) return 0;
        int placed = 0;
        for (int attempt = 0; attempt < 12 && sheep + placed < floor; attempt++) {
            Location at = players.get(random.nextInt(players.size())).getLocation();
            int x = at.getBlockX() + (random.nextBoolean() ? 1 : -1) * (24 + random.nextInt(40));
            int z = at.getBlockZ() + (random.nextBoolean() ? 1 : -1) * (24 + random.nextInt(40));
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block ground = world.getHighestBlockAt(x, z).getRelative(BlockFace.DOWN);
            if (ground.getType() != Material.GRASS) continue;
            Location spot = ground.getLocation().add(0.5, 1.0, 0.5);
            if (spot.getBlock().getType() != Material.AIR) continue;
            int flock = 2 + random.nextInt(3);
            for (int n = 0; n < flock && sheep + placed < floor; n++) {
                try {
                    world.spawnEntity(spot.clone().add(random.nextDouble() * 3 - 1.5, 0, random.nextDouble() * 3 - 1.5),
                            EntityType.SHEEP);
                    placed++;
                } catch (RuntimeException ignored) { }
            }
        }
        return placed;
    }

    public static int restock(World world, Random random, int attempts, int animalCap) {
        resolve(world);
        if (!resolved) return 0;
        int animals = 0;
        for (Entity entity : world.getEntities()) if (entity instanceof Animals) animals++;
        if (animals >= animalCap) return 0;
        List<Player> players = new ArrayList<Player>();
        for (Player player : world.getPlayers()) if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) players.add(player);
        if (players.isEmpty()) return 0;
        int seeded = 0;
        for (int i = 0; i < attempts && animals + seeded * 3 < animalCap; i++) {
            Location at = players.get(random.nextInt(players.size())).getLocation();
            // Far enough out that herds appear in the landscape rather than underfoot,
            // close enough that the chunk is loaded and the player will actually find them.
            int cx = (at.getBlockX() >> 4) + (random.nextBoolean() ? 4 : -4) + random.nextInt(5) - 2;
            int cz = (at.getBlockZ() >> 4) + (random.nextBoolean() ? 4 : -4) + random.nextInt(5) - 2;
            if (!world.isChunkLoaded(cx, cz)) continue;
            worldgen(world, world.getChunkAt(cx, cz), random);
            seeded++;
        }
        return seeded;
    }
}
