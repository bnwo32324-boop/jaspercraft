package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Underground rooms, placed after generation so they can carry real spawners and
 * stocked chests -- neither survives ChunkData, which writes block ids only.
 *
 * The plain dungeon reproduces the vanilla routine exactly: the same sixteen-block
 * room, the same floor and ceiling test, the same one-to-five wall openings. The
 * only change is the number of attempts per chunk, sixteen against vanilla's eight.
 * Matching the algorithm and doubling the attempts is what makes "twice as common"
 * true by construction rather than by a number picked to feel about right.
 *
 * The two built dungeons are placed on their own sparse lattice and stamped one
 * chunk at a time, so a room wider than a chunk is written identically from either
 * side without ever loading a neighbour.
 */
public final class Dungeons {
    private Dungeons() { }
    public static final java.util.concurrent.atomic.AtomicLong ATT=new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong NO_FLOOR=new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong NO_CEIL=new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong BAD_OPEN=new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong OK=new java.util.concurrent.atomic.AtomicLong();

    /**
     * Attempts per chunk. Vanilla runs eight, but eight land differently here: a
     * room needs a solid floor and ceiling and at least one wall opening, so the
     * rate follows how much cave wall a region has, and that varies by region.
     * Measured on this build, vanilla produced 0.0077 spawner rooms per chunk.
     * Sixteen attempts here gave 0.0196 over open ground and 0.0100 over the
     * sparser ice galleries; eighteen puts that spread either side of twice the
     * vanilla rate rather than below it.
     */
    public static final double VANILLA_RATE = 0.0077;
    public static final int ATTEMPTS = 26;
    /** What ATTEMPTS was before. Everything below it is already in the ground. */
    public static final int PREVIOUS_ATTEMPTS = 18;

    /* Spacing is unchanged. Density was doubled by running every built site twice, on two
     * lattices of the same size with different salts, rather than by tightening one lattice.
     *
     * That distinction is the whole reason the retrofit works. Tightening a lattice moves
     * almost every anchor somewhere new, so old ground would have kept its original rooms
     * and gained a full second set on top -- about three times the density of fresh ground,
     * which would have been obvious the moment somebody walked from one to the other. With a
     * second lattice the first pass is exactly what is already built, so a retrofit simply
     * skips it and lays down the second. Old ground and new ground end up identical. */
    private static final int WARD_CELL = 22, OSSUARY_CELL = 29;
    private static final int CISTERN_CELL = 26, VAULT_CELL = 31, WARREN_CELL = 24;
    private static final int SPIRE_CELL = 34, CHAPEL_CELL = 38, CHECKPOINT_CELL = 28;

    /* Six new sites, each a horror idiom rather than any particular story: a fogbound
     * sanatorium, a trial clearing with hooks and humming machines, a woodland cabin over a
     * cellar, a shrine built round a fire that will not go out, a fenced quarantine block,
     * and a stretch of dead highway. All new, so all of them are laid by a retrofit too. */
    private static final int SANATORIUM_CELL = 33, TRIAL_CELL = 29, CABIN_CELL = 25;
    private static final int SHRINE_CELL = 27, BLOCKHOUSE_CELL = 34, HIGHWAY_CELL = 31;

    public static void populate(World world, Chunk chunk, Terrain terrain, Caves caves) {
        build(world, chunk, terrain, caves, false);
    }

    /**
     * Builds this chunk's share of every site that reaches it.
     *
     * In retrofit mode the same pass runs over ground that already exists, and everything
     * that was already placed is skipped: the plain rooms resume at the attempt the old
     * count stopped at, and a built site is only raised when the tightened lattice puts it
     * somewhere the old lattice did not. The rooms already down there drew their numbers
     * first and are reproduced exactly, so nothing standing is disturbed and no chest is
     * refilled for a second looting.
     */
    public static void build(World world, Chunk chunk, Terrain terrain, Caves caves, boolean retrofit) {
        int cx = chunk.getX(), cz = chunk.getZ();
        Random r = new Random(Terrain.mix(terrain.seed + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL + 77L));
        int first = retrofit ? PREVIOUS_ATTEMPTS : 0;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            // Footprint kept inside the chunk so no neighbour is ever read.
            int x = 4 + r.nextInt(8);
            int y = r.nextInt(256);
            int z = 4 + r.nextInt(8);
            // The draws still happen for the earlier attempts, because skipping them would
            // shift every attempt after and put the new rooms somewhere else entirely.
            if (attempt >= first) plain(chunk, r, x, y, z, caves.region(cx * 16 + x, cz * 16 + z));
        }
        // First lattice: what a chunk generated before this change already contains.
        if (!retrofit) {
            ward(world, chunk, terrain, caves, 0x5741524400L);
            ossuary(world, chunk, terrain, caves, 0x424F4E4500L);
            cistern(world, chunk, terrain, caves, 0x4349535400L);
            vault(world, chunk, terrain, caves, 0x5641554C5400L);
            warren(world, chunk, terrain, caves, 0x574152524E00L);
            spire(world, chunk, terrain, 0x5350495245L);
            chapel(world, chunk, terrain, 0x434841504CL);
            checkpoint(world, chunk, terrain, 0x43484B5054L);
        }
        // Second lattice, same spacing and a different salt: the doubling, and what a
        // retrofit lays into ground that already has the first.
        ward(world, chunk, terrain, caves, 0x5741524401L);
        ossuary(world, chunk, terrain, caves, 0x424F4E4501L);
        cistern(world, chunk, terrain, caves, 0x4349535401L);
        vault(world, chunk, terrain, caves, 0x5641554C5401L);
        warren(world, chunk, terrain, caves, 0x574152524E01L);
        spire(world, chunk, terrain, 0x5350495246L);
        chapel(world, chunk, terrain, 0x434841504DL);
        checkpoint(world, chunk, terrain, 0x43484B5055L);
        // The big set pieces. They are all new, so a retrofit lays them too.
        Landmarks.populate(world, chunk, terrain, caves);
        Megaliths.populate(world, chunk, terrain, caves);
        Anomalies.populate(world, chunk, terrain, caves);
        Relics.populate(world, chunk, terrain, caves);
        Metropolis.populate(world, chunk, terrain, caves);
        Wonders.populate(world, chunk, terrain, caves);
        Temples.populate(world, chunk, terrain, caves);
        Breach.populate(world, chunk, terrain, caves);
        sanatorium(world, chunk, terrain);
        trial(world, chunk, terrain);
        cabin(world, chunk, terrain);
        shrine(world, chunk, terrain);
        blockhouse(world, chunk, terrain);
        highway(world, chunk, terrain);
    }

    // -- the vanilla room -------------------------------------------------------

    private static boolean plain(Chunk c, Random r, int x, int y, int z, int style) {
        int rx = r.nextInt(2) + 2, rz = r.nextInt(2) + 2;
        if (y < 2 || y > 250) return false;
        ATT.incrementAndGet();
        int openings = 0;
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    int lx = x + dx, lz = z + dz, ly = y + dy;
                    if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || ly < 1 || ly > 254) return false;
                    Material m = c.getBlock(lx, ly, lz).getType();
                    boolean solid = m.isSolid();
                    if (dy == -1 && !solid) { NO_FLOOR.incrementAndGet(); return false; }
                    if (dy == 4 && !solid) { NO_CEIL.incrementAndGet(); return false; }
                    if ((dx == -rx - 1 || dx == rx + 1 || dz == -rz - 1 || dz == rz + 1) && dy == 0
                        && m == Material.AIR && c.getBlock(lx, ly + 1, lz).getType() == Material.AIR) openings++;
                }
            }
        }
        if (openings < 1 || openings > 5) { BAD_OPEN.incrementAndGet(); return false; }
        OK.incrementAndGet();

        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    int lx = x + dx, ly = y + dy, lz = z + dz;
                    boolean shell = dx == -rx - 1 || dx == rx + 1 || dz == -rz - 1 || dz == rz + 1;
                    if (!shell && dy != -1) { set(c, lx, ly, lz, 0, 0); continue; }
                    if (ly < 1) continue;
                    if (dy == -1 && !c.getBlock(lx, ly, lz).getType().isSolid()) continue;
                    set(c, lx, ly, lz, 4, r.nextInt(4) != 0 ? 0 : -1);
                }
            }
        }
        // Two chests, placed against a wall the way vanilla places them.
        for (int n = 0; n < 2; n++) {
            for (int tries = 0; tries < 3; tries++) {
                int lx = x + r.nextInt(rx * 2 + 1) - rx, lz = z + r.nextInt(rz * 2 + 1) - rz;
                if (c.getBlock(lx, y, lz).getType() != Material.AIR) continue;
                int walls = 0;
                if (c.getBlock(Math.max(0, lx - 1), y, lz).getType().isSolid()) walls++;
                if (c.getBlock(Math.min(15, lx + 1), y, lz).getType().isSolid()) walls++;
                if (c.getBlock(lx, y, Math.max(0, lz - 1)).getType().isSolid()) walls++;
                if (c.getBlock(lx, y, Math.min(15, lz + 1)).getType().isSolid()) walls++;
                if (walls != 1) continue;
                set(c, lx, y, lz, 54, 2);
                fill(c.getBlock(lx, y, lz), r, style, false);
                break;
            }
        }
        set(c, x, y, z, 52, 0);
        spawner(c.getBlock(x, y, z), r, style);
        return true;
    }

    // -- Quarantine Ward --------------------------------------------------------

    private static void ward(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), WARD_CELL, salt, 13, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 6, a.z + 4);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 12 || dz == 0 || dz == 8 || dy == 0 || dy == 5;
            if (shell) { set(c, lx, wy, lz, 98, r.nextInt(3) == 0 ? 2 : 0); continue; }
            // Cell partitions down the long axis, with barred doors onto the corridor.
            boolean partition = (dx == 3 || dx == 6 || dx == 9) && dz != 4;
            if (partition) { set(c, lx, wy, lz, dy == 1 || dy == 2 ? 101 : 98, 0); continue; }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && dz != 4 && r.nextInt(14) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, true); }
            else if (dy == 1 && (dx == 2 || dx == 10) && (dz == 2 || dz == 6)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 4 && dz == 4 && dx % 4 == 2) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Ossuary ----------------------------------------------------------------

    private static void ossuary(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), OSSUARY_CELL, salt, 11, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 5, a.z + 5);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy < 7; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            double radius = Math.sqrt((dx - 5) * (dx - 5) + (dz - 5) * (dz - 5));
            if (radius > 5.4) continue;
            boolean wall = radius > 4.3 || dy == 0 || dy == 6;
            if (wall) { set(c, lx, wy, lz, r.nextInt(4) == 0 ? 216 : 98, r.nextInt(4) == 0 ? 0 : 1); continue; }
            set(c, lx, wy, lz, 0, 0);
            // Niches around the wall hold the dead; the altar holds what they were buried with.
            boolean niche = radius > 3.2 && radius <= 4.3 && dy >= 1 && dy <= 2;
            if (niche && r.nextInt(7) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, true); }
            else if (niche && r.nextInt(11) == 0) set(c, lx, wy, lz, 216, 4);
            else if (dy == 1 && radius < 1.6) set(c, lx, wy, lz, 98, 3);
            else if (dy == 2 && radius < 0.6) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 5 && radius > 3.6) set(c, lx, wy, lz, 89, 0);
        }
    }


    // -- Flooded Cistern (underground) -----------------------------------------

    /** A sunken reservoir: standing water, pillared bays and a dry ledge with the loot. */
    private static void cistern(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), CISTERN_CELL, salt, 13, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 6, a.z + 6);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = 0; dy < 8; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 12 || dz == 0 || dz == 12 || dy == 0 || dy == 7;
            if (shell) { set(c, lx, wy, lz, 98, r.nextInt(3) == 0 ? 1 : 0); continue; }
            // Four pillars carry the roof; the rest of the floor is flooded.
            boolean pillar = (dx == 3 || dx == 9) && (dz == 3 || dz == 9);
            if (pillar) { set(c, lx, wy, lz, 98, 0); continue; }
            // A dry ledge runs along one wall so the room can actually be looted.
            boolean ledge = dz == 1 && dy == 1;
            if (ledge) { set(c, lx, wy, lz, 44, 5); continue; }
            if (dy == 1) { set(c, lx, wy, lz, 9, 0); continue; }   // standing water
            set(c, lx, wy, lz, 0, 0);
            if (dy == 2 && dz == 1 && dx % 4 == 2) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, true); }
            else if (dy == 2 && dz == 11 && (dx == 4 || dx == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 6 && (dx == 3 || dx == 9) && (dz == 3 || dz == 9)) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Reliquary Vault (underground) -----------------------------------------

    /** A small strongroom: the loot is caged behind bars and watched from outside. */
    private static void vault(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), VAULT_CELL, salt, 11, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 5, a.z + 5);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 10 || dz == 0 || dz == 10 || dy == 0 || dy == 5;
            if (shell) { set(c, lx, wy, lz, 98, r.nextInt(4) == 0 ? 2 : 0); continue; }
            set(c, lx, wy, lz, 0, 0);
            // The cage: a 5x5 bar enclosure in the middle, open on one side only.
            boolean cageWall = (dx >= 3 && dx <= 7 && dz >= 3 && dz <= 7)
                && (dx == 3 || dx == 7 || dz == 3 || dz == 7) && !(dz == 7 && dx == 5);
            if (cageWall && dy >= 1 && dy <= 3) { set(c, lx, wy, lz, 101, 0); continue; }
            if (dy == 1 && dx >= 4 && dx <= 6 && dz >= 4 && dz <= 6) {
                if (dx == 5 && dz == 5) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, true); }
                else if (r.nextInt(3) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, true); }
            } else if (dy == 1 && (dx == 1 || dx == 9) && (dz == 1 || dz == 9)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 4 && (dx == 2 || dx == 8) && (dz == 2 || dz == 8)) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Sporecist Warren (underground) ----------------------------------------

    /** An overgrown hollow: fungal caps, heavy webbing and things that live in it. */
    private static void warren(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), WARREN_CELL, salt, 15, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 7, a.z + 5);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            // Rounded hollow rather than a box, so it reads as grown instead of built.
            double rad = Math.sqrt(((dx - 7) / 7.2) * ((dx - 7) / 7.2) + ((dz - 5) / 5.2) * ((dz - 5) / 5.2));
            if (rad > 1.0) continue;
            boolean shell = rad > 0.82 || dy == 0 || dy == 5;
            if (shell) { set(c, lx, wy, lz, 4, r.nextInt(2) == 0 ? -1 : 0); continue; }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && r.nextInt(9) == 0) { set(c, lx, wy, lz, r.nextInt(2) == 0 ? 99 : 100, 14); continue; }
            if (r.nextInt(11) == 0) { set(c, lx, wy, lz, 30, 0); continue; }   // webbing
            if (dy == 1 && (dx == 4 || dx == 10) && dz == 5) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, Caves.FUNGAL); }
            else if (dy == 1 && rad < 0.5 && r.nextInt(6) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, false); }
            else if (dy == 4 && r.nextInt(18) == 0) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Signal Spire (surface) -------------------------------------------------

    /** A watchtower: a climbable shaft with a lookout room and its keeper at the top. */
    private static void spire(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), SPIRE_CELL, salt, 7, 7);
        if (a == null) return;
        Random r = new Random(a.seed);
        int height = 15 + r.nextInt(5);
        for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) for (int dy = -3; dy < height; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 98, 0); continue; }          // foundation into the slope
            boolean wall = dx == 0 || dx == 6 || dz == 0 || dz == 6;
            boolean top = dy == height - 1;
            if (top) { set(c, lx, wy, lz, 44, 5); continue; }             // slab roof
            if (wall) {
                // Arrow slits on the lookout floor, a doorway at the foot.
                boolean door = dy >= 1 && dy <= 2 && dx == 3 && dz == 0;
                boolean slit = dy == height - 3 && (dx == 3 || dz == 3);
                if (door || slit) { set(c, lx, wy, lz, 0, 0); continue; }
                set(c, lx, wy, lz, 98, r.nextInt(5) == 0 ? 2 : 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dx == 1 && dz == 1 && dy < height - 2) { set(c, lx, wy, lz, 65, 2); continue; }  // ladder
            if (dy == height - 4 && dx == 3 && dz == 3) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == height - 4 && (dx == 5 || dx == 2) && dz == 5) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            else if (dy == height - 2 && dx == 3 && dz == 3) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Ashen Chapel (surface) -------------------------------------------------

    /** A burnt-out chapel: a nave with broken windows and whatever the altar kept. */
    private static void chapel(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), CHAPEL_CELL, salt, 11, 15);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 15; dz++) for (int dy = -3; dy < 9; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 4, 0); continue; }
            boolean wall = dx == 0 || dx == 10 || dz == 0 || dz == 14;
            if (dy == 0) { set(c, lx, wy, lz, 98, r.nextInt(4) == 0 ? 1 : 0); continue; }   // flagstone floor
            if (dy >= 7) {
                // Collapsed roof: mostly open, a few ribs left standing.
                if (wall && r.nextInt(3) != 0) set(c, lx, wy, lz, 4, -1); else set(c, lx, wy, lz, 0, 0);
                continue;
            }
            if (wall) {
                boolean window = dy >= 3 && dy <= 4 && (dz % 4 == 2) && (dx == 0 || dx == 10);
                boolean door = dy >= 1 && dy <= 2 && dz == 0 && (dx == 5);
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, r.nextInt(3) == 0 ? 0 : 102, 0); continue; }
                set(c, lx, wy, lz, r.nextInt(6) == 0 ? 4 : 98, r.nextInt(5) == 0 ? 2 : 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Pews down the nave, the altar at the far end.
            if (dy == 1 && dz >= 3 && dz <= 10 && dz % 2 == 1 && (dx == 3 || dx == 7)) { set(c, lx, wy, lz, 109, 2); continue; }
            if (dy == 1 && dz == 12 && dx >= 4 && dx <= 6) { set(c, lx, wy, lz, 98, 3); continue; }
            if (dy == 2 && dz == 12 && dx == 5) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            else if (dy == 1 && dz == 2 && (dx == 2 || dx == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == 5 && (dx == 2 || dx == 8) && dz % 5 == 3) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Roadside Checkpoint (surface) ------------------------------------------

    /** A quarantine post: a barred blockhouse behind a broken barricade. */
    private static void checkpoint(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), CHECKPOINT_CELL, salt, 13, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -3; dy < 7; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            // A sandbag-and-bar barricade stands two blocks out in front of the hut.
            if (dz == 0) {
                if (dy >= 1 && dy <= 2 && dx % 3 != 0) set(c, lx, wy, lz, dy == 2 ? 101 : 159, 8);
                else set(c, lx, wy, lz, 0, 0);
                continue;
            }
            boolean hut = dx >= 2 && dx <= 10 && dz >= 3 && dz <= 7;
            if (!hut) { if (dy == 0) set(c, lx, wy, lz, 1, 6); else set(c, lx, wy, lz, 0, 0); continue; }
            boolean wall = dx == 2 || dx == 10 || dz == 3 || dz == 7;
            if (dy == 0) { set(c, lx, wy, lz, 251, 8); continue; }
            if (dy >= 5) { if (dy == 5) set(c, lx, wy, lz, 44, 5); else set(c, lx, wy, lz, 0, 0); continue; }
            if (wall) {
                boolean window = dy == 2 && (dz == 3) && dx % 2 == 1;
                boolean door = dy >= 1 && dy <= 2 && dz == 3 && dx == 6;
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, 101, 0); continue; }
                set(c, lx, wy, lz, 251, r.nextInt(5) == 0 ? 7 : 8);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && (dx == 3 || dx == 9) && dz == 6) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); }
            else if (dy == 1 && dx == 6 && dz == 5) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == 4 && dx == 6 && dz == 5) set(c, lx, wy, lz, 89, 0);
        }
    }


    // -- Fogbound Sanatorium (surface) -----------------------------------------

    /**
     * A ward that has been left to rust.
     *
     * Bleached tile at the edges giving way to orange and brown where the damp got in,
     * grated floors you can see the dark through, sealed iron ward doors and a few beds
     * nobody stripped. Lit badly and on purpose: one red torch a room.
     */
    private static void sanatorium(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), SANATORIUM_CELL, 0x53414E4154L, 15, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -4; dy < 9; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 98, 2); continue; }        // footing into the slope
            boolean wall = dx == 0 || dx == 14 || dz == 0 || dz == 12;
            boolean spine = dz == 6;                                     // the corridor
            int rust = r.nextInt(9);
            if (dy == 8) { set(c, lx, wy, lz, r.nextInt(4) == 0 ? 0 : 44, 5); continue; }
            if (dy == 0) {
                // Grated corridor floor; tile everywhere else, stained where the wet reached.
                if (spine && dx % 2 == 1) set(c, lx, wy, lz, 101, 0);
                else set(c, lx, wy, lz, 159, rust == 0 ? 12 : rust == 1 ? 1 : 8);
                continue;
            }
            if (wall) {
                boolean window = dy >= 3 && dy <= 4 && (dx % 4 == 2 || dz % 4 == 2);
                boolean door = dy >= 1 && dy <= 2 && dz == 0 && dx == 7;
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, r.nextInt(3) == 0 ? 0 : 102, 0); continue; }
                set(c, lx, wy, lz, 159, rust < 2 ? 1 : rust < 4 ? 12 : 0);
                continue;
            }
            // Ward partitions either side of the corridor, with iron doors that still shut.
            boolean partition = (dx == 4 || dx == 10) && dz != 6 && dy <= 3;
            if (partition) {
                if (dy <= 2 && (dz == 3 || dz == 9)) { set(c, lx, wy, lz, 71, 0); continue; }
                set(c, lx, wy, lz, 159, 8); continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && !spine && r.nextInt(14) == 0) { set(c, lx, wy, lz, 44, 5); continue; }  // gurney
            if (dy == 2 && r.nextInt(16) == 0) { set(c, lx, wy, lz, 30, 0); continue; }
            if (dy == 3 && spine && dx % 6 == 3) { set(c, lx, wy, lz, 76, 5); continue; }
            if (dy == 1 && (dx == 2 || dx == 12) && (dz == 2 || dz == 10)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == 1 && dx == 7 && (dz == 2 || dz == 10)) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
        }
    }

    // -- Trial Ground (surface) -------------------------------------------------

    /**
     * A clearing somebody uses. Machines that hum in the open, hooks set in the dirt, and a
     * shack over a cellar with more hooks in it than the work would ever need.
     */
    private static void trial(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), TRIAL_CELL, 0x54524941544CL, 13, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -2; dy < 7; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shack = dx >= 7 && dx <= 12 && dz >= 7 && dz <= 12;
            if (dy < 0) {
                // A cellar under the shack only; bare packed ground everywhere else.
                if (shack && dy == -1) { set(c, lx, wy, lz, dx == 7 || dx == 12 || dz == 7 || dz == 12 ? 4 : 0, 0); continue; }
                set(c, lx, wy, lz, 3, 1); continue;
            }
            if (dy == 0) { set(c, lx, wy, lz, shack ? 5 : 3, shack ? 0 : 1); continue; }
            if (shack) {
                boolean wall = dx == 7 || dx == 12 || dz == 7 || dz == 12;
                if (dy >= 5) { set(c, lx, wy, lz, dy == 5 ? 53 : 0, 0); continue; }
                if (wall) {
                    boolean door = dy <= 2 && dz == 7 && dx == 9;
                    set(c, lx, wy, lz, door ? 0 : r.nextInt(7) == 0 ? 0 : 5, 0);
                    continue;
                }
                set(c, lx, wy, lz, 0, 0);
                // The stair down, and a hook in the middle of the cellar below it.
                if (dy == 1 && dx == 11 && dz == 11) { set(c, lx, wy, lz, 65, 4); continue; }
                if (dy == 1 && dx == 9 && dz == 10) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Four machines out in the open, and a hook standing near each.
            boolean machine = (dx == 2 || dx == 5) && (dz == 2 || dz == 5);
            if (dy == 1 && machine) { set(c, lx, wy, lz, 42, 0); continue; }
            if (dy == 2 && machine) { set(c, lx, wy, lz, 76, 5); continue; }
            boolean hook = (dx == 3 && dz == 9) || (dx == 10 && dz == 3);
            if (hook && dy >= 1 && dy <= 2) { set(c, lx, wy, lz, 85, 0); continue; }
            if (hook && dy == 3) { set(c, lx, wy, lz, 101, 0); continue; }
            if (dy == 1 && r.nextInt(26) == 0) { set(c, lx, wy, lz, 126, 0); continue; }   // dropped pallet
            if (dy == 1 && dx == 1 && dz == 11) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
        }
    }

    // -- Deadwood Cabin (surface) -----------------------------------------------

    /** A one-room cabin with a trapdoor in the floor, and a cellar that explains the stains. */
    private static void cabin(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), CABIN_CELL, 0x434142494EL, 11, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -6; dy < 8; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean cellar = dx >= 3 && dx <= 7 && dz >= 2 && dz <= 6;
            if (dy < -1) {
                if (!cellar) { set(c, lx, wy, lz, 1, 0); continue; }
                boolean shell = dx == 3 || dx == 7 || dz == 2 || dz == 6 || dy == -5;
                if (shell) { set(c, lx, wy, lz, 4, r.nextInt(3) == 0 ? -1 : 0); continue; }
                set(c, lx, wy, lz, 0, 0);
                if (dy == -4 && dx == 5 && dz == 4) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
                else if (dy == -4 && dx == 4 && dz == 3) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                else if (dy == -4 && dx == 6 && dz == 5) { set(c, lx, wy, lz, 47, 0); }
                else if (dy == -4 && r.nextInt(6) == 0) { set(c, lx, wy, lz, 159, 14); }   // it did not all wash out
                else if (r.nextInt(9) == 0) { set(c, lx, wy, lz, 30, 0); }
                continue;
            }
            if (dy == -1) {
                // Floor, with the way down left open where the rug used to be.
                if (cellar && dx == 5 && dz == 4) { set(c, lx, wy, lz, 0, 0); continue; }
                set(c, lx, wy, lz, 5, 0); continue;
            }
            boolean wall = dx == 0 || dx == 10 || dz == 0 || dz == 8;
            if (dy >= 5) {
                if (dy == 5) set(c, lx, wy, lz, 53, 0); else set(c, lx, wy, lz, 0, 0);
                continue;
            }
            if (wall) {
                boolean door = dy <= 2 && dz == 0 && dx == 5;
                boolean window = dy == 2 && (dx == 2 || dx == 8) && (dz == 0 || dz == 8);
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, 102, 0); continue; }
                set(c, lx, wy, lz, 17, 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 0 && dx == 5 && dz == 4) { set(c, lx, wy, lz, 65, 2); continue; }    // ladder down
            if (dy == 0 && dx == 2 && dz == 2) { set(c, lx, wy, lz, 58, 0); continue; }
            if (dy == 0 && dx == 8 && dz == 2) { set(c, lx, wy, lz, 61, 2); continue; }
            if (dy == 0 && dx == 8 && dz == 6) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); }
            else if (dy == 1 && dx == 2 && dz == 6) { set(c, lx, wy, lz, 47, 0); }
            else if (dy == 2 && r.nextInt(20) == 0) { set(c, lx, wy, lz, 30, 0); }
        }
    }

    // -- Kindled Shrine (surface) -----------------------------------------------

    /**
     * A broken chapel with a fire at the middle of it that somebody keeps feeding. Ash out to
     * the walls, a blade stood upright in the coals, and the dead waiting where the roof was.
     */
    private static void shrine(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), SHRINE_CELL, 0x534852494EL, 11, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = -3; dy < 8; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 4, 0); continue; }
            boolean wall = dx == 0 || dx == 10 || dz == 0 || dz == 10;
            double rad = Math.sqrt((dx - 5) * (dx - 5) + (dz - 5) * (dz - 5));
            if (dy == 0) {
                if (rad < 1.6) { set(c, lx, wy, lz, 87, 0); continue; }                    // the coal bed
                set(c, lx, wy, lz, r.nextInt(5) == 0 ? 159 : 4, r.nextInt(5) == 0 ? 15 : 0);
                continue;
            }
            if (wall) {
                // Ruined: the higher it goes the less of it is left standing.
                boolean gone = r.nextInt(8) < dy;
                boolean arch = dy <= 3 && (dx == 5 || dz == 5);
                if (arch) { set(c, lx, wy, lz, 0, 0); continue; }
                set(c, lx, wy, lz, gone ? 0 : r.nextInt(3) == 0 ? 48 : 4, 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && rad < 1.6) { set(c, lx, wy, lz, 51, 0); continue; }             // it does not go out
            if (dy >= 1 && dy <= 2 && dx == 5 && dz == 5) { set(c, lx, wy, lz, 101, 0); continue; }
            if (dy == 1 && rad >= 1.6 && rad < 2.7 && (dx == 5 || dz == 5)) { set(c, lx, wy, lz, 139, 0); continue; }
            if (dy == 1 && (dx == 2 || dx == 8) && (dz == 2 || dz == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, Caves.CATHEDRAL); }
            else if (dy == 1 && dx == 5 && dz == 9) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, Caves.CATHEDRAL, true); }
            else if (dy == 4 && (dx == 2 || dx == 8) && dz % 4 == 1) { set(c, lx, wy, lz, 89, 0); }
        }
    }

    // -- Quarantine Block (surface) ---------------------------------------------

    /**
     * Somebody held this for a while. Bars all the way round, a row of cells with the doors
     * still on them, a tower at the corner, and whatever they were keeping out now inside.
     */
    private static void blockhouse(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), BLOCKHOUSE_CELL, 0x424C4F434BL, 15, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -2; dy < 10; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean perimeter = dx == 0 || dx == 14 || dz == 0 || dz == 12;
            boolean inner = dx >= 4 && dx <= 12 && dz >= 3 && dz <= 9;
            boolean tower = dx <= 2 && dz <= 2;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            if (dy == 0) { set(c, lx, wy, lz, inner ? 98 : 251, inner ? 0 : 8); continue; }
            if (tower) {
                boolean shell = dx == 0 || dx == 2 || dz == 0 || dz == 2;
                if (dy >= 9) { set(c, lx, wy, lz, dy == 9 ? 44 : 0, 5); continue; }
                if (shell && dy < 8) { set(c, lx, wy, lz, dy >= 6 ? 101 : 98, 0); continue; }
                set(c, lx, wy, lz, 0, 0);
                if (dx == 1 && dz == 1 && dy < 7) { set(c, lx, wy, lz, 65, 2); continue; }
                if (dy == 7 && dx == 1 && dz == 1) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                continue;
            }
            if (perimeter) {
                // Chain fence on a low kerb, sagging in places.
                if (dy == 1) { set(c, lx, wy, lz, 98, 0); continue; }
                if (dy <= 4) { set(c, lx, wy, lz, r.nextInt(11) == 0 ? 0 : 101, 0); continue; }
                set(c, lx, wy, lz, 0, 0);
                continue;
            }
            if (inner) {
                boolean shell = dx == 4 || dx == 12 || dz == 3 || dz == 9;
                boolean cellWall = dx % 2 == 0 && dz >= 4 && dz <= 8;
                if (dy >= 6) { set(c, lx, wy, lz, dy == 6 ? 44 : 0, 5); continue; }
                if (shell) {
                    boolean gate = dy <= 2 && dz == 3 && dx == 8;
                    set(c, lx, wy, lz, gate ? 0 : 98, gate ? 0 : r.nextInt(6) == 0 ? 2 : 0);
                    continue;
                }
                if (cellWall && dy <= 3) {
                    if (dy <= 2 && dz == 6) { set(c, lx, wy, lz, 71, 0); continue; }
                    set(c, lx, wy, lz, 101, 0); continue;
                }
                set(c, lx, wy, lz, 0, 0);
                if (dy == 1 && dx % 2 == 1 && dz == 5 && r.nextInt(2) == 0) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
                else if (dy == 1 && dx % 2 == 1 && dz == 8 && r.nextInt(3) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); }
                else if (dy == 4 && dz == 6 && dx % 4 == 1) { set(c, lx, wy, lz, 89, 0); }
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            if (dy == 1 && r.nextInt(30) == 0) set(c, lx, wy, lz, 85, 0);
        }
    }

    // -- The Long Road (surface) ------------------------------------------------

    /**
     * A stretch of road with nothing coming. Grey ash over cracked tarmac, a burnt shell
     * pushed onto the verge, a loaded cart somebody gave up on, and a lean-to off the hard
     * shoulder with what is left of their supplies.
     */
    private static void highway(World w, Chunk c, Terrain t) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), HIGHWAY_CELL, 0x524F414400L, 15, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -2; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean road = dz >= 2 && dz <= 5;
            boolean shelter = dx >= 10 && dx <= 13 && dz >= 6 && dz <= 8;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            if (dy == 0) {
                if (road) {
                    // Tarmac, broken up, with the centre line mostly gone.
                    if (dz == 3 && dx % 3 != 2) { set(c, lx, wy, lz, 251, 0); continue; }
                    set(c, lx, wy, lz, r.nextInt(6) == 0 ? 13 : 251, 15);
                    continue;
                }
                set(c, lx, wy, lz, r.nextInt(3) == 0 ? 13 : 251, 8);      // ash on the verge
                continue;
            }
            if (shelter) {
                boolean shell = dx == 10 || dx == 13 || dz == 6 || dz == 8;
                if (dy >= 4) { set(c, lx, wy, lz, 0, 0); continue; }
                if (dy == 3) { set(c, lx, wy, lz, 44, 5); continue; }
                if (shell) {
                    boolean gap = dz == 6 && dx == 11;
                    set(c, lx, wy, lz, gap ? 0 : r.nextInt(5) == 0 ? 0 : 5, 0);
                    continue;
                }
                set(c, lx, wy, lz, 0, 0);
                if (dy == 1 && dx == 12 && dz == 7) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); }
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // The burnt-out shell, half on the verge.
            boolean car = dx >= 3 && dx <= 6 && dz >= 5 && dz <= 6;
            if (car && dy == 1) { set(c, lx, wy, lz, 173, 0); continue; }
            if (car && dy == 2 && dx >= 4 && dx <= 5) { set(c, lx, wy, lz, 101, 0); continue; }
            // A cart, still loaded, pointing the way they were walking.
            if (dy == 1 && dx == 9 && dz == 4) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); continue; }
            if (dy == 1 && (dx == 8 || dx == 10) && dz == 4) { set(c, lx, wy, lz, 101, 0); continue; }
            // Dead standing timber along the verge.
            if (dz <= 1 && dy <= 3 && dx % 5 == 2 && r.nextInt(3) != 0) { set(c, lx, wy, lz, 17, 0); continue; }
            if (dy == 1 && dz == 7 && dx == 2) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
        }
    }

    // -- placement --------------------------------------------------------------

    /* Where each built room can be, so a survey can name the one you are standing in. */
    private static final String[] D_NAME = {
        "The Ward", "The Ossuary", "The Flooded Cistern", "The Vault", "The Warren"
        , "The Spire", "The Wayside Chapel", "The Checkpoint", "The Sanatorium", "The Trial Ground"
        , "The Cabin", "The Everburning Shrine", "The Quarantine Blockhouse", "The Dead Highway"
    };
    private static final String[] D_METHOD = { "ward", "ossuary", "cistern", "vault", "warren", "spire", "chapel", "checkpoint", "sanatorium", "trial", "cabin", "shrine", "blockhouse", "highway" };
    private static final int[] D_CELL = { 22, 29, 26, 31, 24, 34, 38, 28, 33, 29, 25, 27, 34, 31 };
    private static final long[] D_SALT_A = { 0x5741524400L, 0x424F4E4500L, 0x4349535400L, 0x5641554C5400L, 0x574152524E00L, 0x5350495245L, 0x434841504CL, 0x43484B5054L, 0x53414E4154L, 0x54524941544CL, 0x434142494EL, 0x534852494EL, 0x424C4F434BL, 0x524F414400L };
    private static final long[] D_SALT_B = { 0x5741524401L, 0x424F4E4501L, 0x4349535401L, 0x5641554C5401L, 0x574152524E01L, 0x5350495246L, 0x434841504DL, 0x43484B5055L, 0L, 0L, 0L, 0L, 0L, 0L };
    private static final int[] D_SX = { 13, 11, 13, 11, 15, 7, 11, 13, 15, 13, 11, 11, 15, 15 };
    private static final int[] D_SZ = { 9, 11, 13, 11, 11, 7, 15, 9, 13, 13, 9, 11, 13, 9 };
    private static final boolean[] D_SURFACE = { false, false, false, false, false, true, true, true, true, true, true, true, true, true };
    private static final int[] D_SY = { 6, 7, 8, 6, 6, 20, 12, 9, 13, 8, 12, 11, 12, 6 };

    /**
     * Which built room this position is standing in, or null.
     *
     * It asks the same two anchor functions the builders ask, on the same lattices and
     * the same salts, so a survey can only name a room that was actually laid here. No
     * block is read: the answer is a function of the seed and the coordinate, exactly as
     * the placement was.
     */
    public static String locate(Terrain t, int wx, int wy, int wz) {
        int cx = wx >> 4, cz = wz >> 4;
        for (int i = 0; i < D_NAME.length; i++) {
            for (int lat = 0; lat < 2; lat++) {
                long salt = lat == 0 ? D_SALT_A[i] : D_SALT_B[i];
                if (salt == 0L) continue;
                Anchor a = D_SURFACE[i]
                    ? surfaceAnchor(t, cx, cz, D_CELL[i], salt, D_SX[i], D_SZ[i])
                    : anchor(t, cx, cz, D_CELL[i], salt, D_SX[i], D_SZ[i]);
                if (a == null) continue;
                if (wx < a.x || wx >= a.x + D_SX[i]) continue;
                if (wz < a.z || wz >= a.z + D_SZ[i]) continue;
                if (wy < a.y - 2 || wy > a.y + D_SY[i] + 2) continue;
                return D_NAME[i] + "\u0000" + D_METHOD[i] + "\u0000" + a.x + "\u0000" + a.y + "\u0000" + a.z;
            }
        }
        return null;
    }

    private static final class Anchor { final int x, y, z; final long seed; Anchor(int x,int y,int z,long s){this.x=x;this.y=y;this.z=z;this.seed=s;} }

    /**
     * Where the built dungeon for this lattice cell sits, or null when it cannot
     * reach the chunk being populated. Everything comes from the cell, so each of
     * the chunks it spans computes exactly the same answer.
     */
    private static Anchor anchor(Terrain t, int cx, int cz, int cell, long salt, int sizeX, int sizeZ) {
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                int acx = cx + ox, acz = cz + oz;
                if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) continue;
                if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) continue;
                long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
                int x = acx * 16 + 2, z = acz * 16 + 2;
                int ground = t.sample(x + sizeX / 2, z + sizeZ / 2).y;
                int y = 12 + (int) Math.floorMod(seed >>> 9, Math.max(1, ground - 34));
                if (y + 8 > ground - 6) y = Math.max(8, ground - 16);
                // Reject if this chunk is outside the footprint after all.
                if (x + sizeX <= cx * 16 || x > cx * 16 + 15) continue;
                if (z + sizeZ <= cz * 16 || z > cz * 16 + 15) continue;
                return new Anchor(x, y, z, seed);
            }
        }
        return null;
    }

    /**
     * The same lattice, but sitting on the ground instead of buried in it. A surface
     * dungeon is visible from a long way off, so it only accepts ground that is flat,
     * dry and not a mountain top: half a tower sunk into a hillside, or one standing
     * in the sea, reads as a bug rather than a ruin. Every value comes from the
     * terrain function, so each chunk the footprint spans computes the same answer
     * without ever reading a neighbour.
     */
    private static Anchor surfaceAnchor(Terrain t, int cx, int cz, int cell, long salt, int sizeX, int sizeZ) {
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                int acx = cx + ox, acz = cz + oz;
                if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) continue;
                if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) continue;
                if (x0(cx, sizeX, acx) || z0(cz, sizeZ, acz)) continue;
                long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
                int x = acx * 16 + 2, z = acz * 16 + 2;
                int c1 = t.sample(x, z).y, c2 = t.sample(x + sizeX - 1, z).y;
                int c3 = t.sample(x, z + sizeZ - 1).y, c4 = t.sample(x + sizeX - 1, z + sizeZ - 1).y;
                int lo = Math.min(Math.min(c1, c2), Math.min(c3, c4));
                int hi = Math.max(Math.max(c1, c2), Math.max(c3, c4));
                if (hi - lo > 3) continue;      // too steep to stand on
                if (lo < 64 || hi > 132) continue; // out of the water, off the peaks
                return new Anchor(x, lo + 1, z, seed);
            }
        }
        return null;
    }

    /** True when the footprint cannot reach this chunk on the X axis. */
    private static boolean x0(int cx, int sizeX, int acx) {
        int x = acx * 16 + 2;
        return x + sizeX <= cx * 16 || x > cx * 16 + 15;
    }
    /** True when the footprint cannot reach this chunk on the Z axis. */
    private static boolean z0(int cz, int sizeZ, int acz) {
        int z = acz * 16 + 2;
        return z + sizeZ <= cz * 16 || z > cz * 16 + 15;
    }

    // -- fittings ---------------------------------------------------------------

    static void set(Chunk c, int x, int y, int z, int id, int data) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1 || y > 254) return;
        if (data < 0) { c.getBlock(x, y, z).setTypeIdAndData(48, (byte) 0, false); return; }
        c.getBlock(x, y, z).setTypeIdAndData(id, (byte) data, false);
    }

    static void spawner(Block block, Random r, int style) {
        try {
            CreatureSpawner s = (CreatureSpawner) block.getState();
            s.setSpawnedType(mob(r, style));
            s.update(true, false);
        } catch (RuntimeException ignored) { }
    }

    private static EntityType mob(Random r, int style) {
        switch (style) {
            case Caves.EMBERVEINS: return r.nextInt(3) == 0 ? EntityType.BLAZE : EntityType.ZOMBIE;
            case Caves.FROSTBORE:  return EntityType.SKELETON;
            case Caves.FUNGAL:     return r.nextInt(2) == 0 ? EntityType.CAVE_SPIDER : EntityType.SPIDER;
            case Caves.CATHEDRAL:  return r.nextInt(3) == 0 ? EntityType.SILVERFISH : EntityType.SKELETON;
            default: {
                int pick = r.nextInt(4);
                return pick == 0 ? EntityType.SKELETON : pick == 1 ? EntityType.SPIDER
                     : pick == 2 ? EntityType.ZOMBIE : EntityType.CAVE_SPIDER;
            }
        }
    }

    private static void fill(Block block, Random r, int style, boolean rich) {
        try {
            Chest chest = (Chest) block.getState();
            Inventory inv = chest.getBlockInventory();
            int rolls = (rich ? 5 : 3) + r.nextInt(4);
            for (int i = 0; i < rolls; i++) {
                ItemStack item = roll(r, style, rich);
                if (item != null) inv.setItem(r.nextInt(inv.getSize()), item);
            }
            // Occasionally somebody died down here still holding their sidearm. Long odds on
            // purpose: a gun should be the reason you remember a dungeon, not a line item.
            //
            // This draws from its own stream, keyed to the chest's position. Taking numbers
            // from the room's Random would shift every block placed after this chest, and a
            // dungeon spanning a chunk that generated before this existed would then disagree
            // with itself across the seam.
            Random luck = new Random(Terrain.mix(block.getWorld().getSeed()
                    ^ ((long) block.getX() << 32) ^ ((long) block.getZ() << 12) ^ block.getY()));
            if (luck.nextInt(rich ? 16 : 40) == 0) {
                ItemStack weapon = ExpeditionLoot.rareWeapon(luck);
                if (weapon != null) {
                    inv.setItem(luck.nextInt(inv.getSize()), weapon);
                    inv.addItem(new ItemStack(org.bukkit.Material.IRON_NUGGET, 6 + luck.nextInt(11)));
                }
            }
            // No chest.update() here (removed 3.21.0): getBlockInventory() is the LIVE tile
            // inventory, and update() would copy the empty pre-fill snapshot back over it.
        } catch (RuntimeException ignored) { }
    }

    /**
     * Refills an authored chest that was left empty (3.21.0).
     *
     * Until 3.21.0 the update() call removed above erased every chest the populator had just
     * stocked. StructureLoot calls this the first time a player opens such a chest inside a
     * located register set piece (rich) or dungeon room, after journaling the claim, so it
     * happens once per chest. Everything is drawn from one stream keyed to the chest's
     * position. Returns the number of item stacks placed (a found weapon counts two, with
     * its nuggets).
     */
    public static int restock(Block block, Terrain t, Caves caves, boolean rich) {
        Chest chest = (Chest) block.getState();
        Inventory inv = chest.getBlockInventory();
        Random r = new Random(Terrain.mix(block.getWorld().getSeed()
                ^ ((long) block.getX() << 32) ^ ((long) block.getZ() << 12) ^ block.getY()));
        // AS SHIPPED IN THE LIVE 3.23.0 JAR: region() takes block coordinates, this passes chunk
        // coordinates, so the loot style comes from the cave region near x/16, z/16. Kept
        // byte-identical by the source recovery; flagged, not changed here.
        int style = caves.region(block.getX() >> 4, block.getZ() >> 4);
        int rolls = (rich ? 5 : 3) + r.nextInt(4);
        int placed = 0;
        for (int i = 0; i < rolls; i++) {
            ItemStack item = roll(r, style, rich);
            if (item != null) {
                inv.setItem(r.nextInt(inv.getSize()), item);
                placed++;
            }
        }
        if (r.nextInt(rich ? 16 : 40) == 0) {
            ItemStack weapon = ExpeditionLoot.rareWeapon(r);
            if (weapon != null) {
                inv.setItem(r.nextInt(inv.getSize()), weapon);
                inv.addItem(new ItemStack(org.bukkit.Material.IRON_NUGGET, 6 + r.nextInt(11)));
                placed += 2;
            }
        }
        return placed;
    }

    private static ItemStack roll(Random r, int style, boolean rich) {
        int pick = r.nextInt(rich ? 18 : 16);
        switch (pick) {
            case 0: return new ItemStack(Material.IRON_INGOT, 1 + r.nextInt(5));
            case 1: return new ItemStack(Material.GOLD_INGOT, 1 + r.nextInt(3));
            case 2: return new ItemStack(Material.BREAD, 1 + r.nextInt(3));
            case 3: return new ItemStack(Material.WHEAT, 1 + r.nextInt(4));
            case 4: return new ItemStack(Material.BUCKET);
            case 5: return new ItemStack(Material.REDSTONE, 1 + r.nextInt(4));
            case 6: return new ItemStack(Material.COAL, 2 + r.nextInt(6));
            case 7: return new ItemStack(Material.STRING, 1 + r.nextInt(4));
            case 8: return new ItemStack(Material.BONE, 1 + r.nextInt(4));
            case 9: return new ItemStack(Material.ROTTEN_FLESH, 1 + r.nextInt(4));
            case 10: return new ItemStack(Material.SADDLE);
            case 11: return new ItemStack(Material.GOLDEN_APPLE);
            case 12: return new ItemStack(Material.IRON_PICKAXE);
            case 13: return new ItemStack(Material.SULPHUR, 1 + r.nextInt(4));
            case 14: return style == Caves.FROSTBORE ? new ItemStack(Material.DIAMOND, 1 + r.nextInt(2))
                                                     : new ItemStack(Material.LAPIS_ORE, 1 + r.nextInt(3));
            case 15: return new ItemStack(Material.ENCHANTED_BOOK);
            case 16: return new ItemStack(Material.DIAMOND, 1 + r.nextInt(3));
            default: return new ItemStack(Material.IRON_BLOCK);
        }
    }
}
