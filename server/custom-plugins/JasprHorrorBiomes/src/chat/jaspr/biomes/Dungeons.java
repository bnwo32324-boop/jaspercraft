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
    public static final java.util.concurrent.atomic.AtomicLong TAKEN=new java.util.concurrent.atomic.AtomicLong();
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
    public static final int ATTEMPTS = 39;
    /** 3.28.0 (second 1.5x, StructureRates): attempts from ATTEMPTS up draw after all of those, only in chunks new in
     * 3.28.0, so the first 39 rooms of every chunk are drawn exactly as before. */
    public static final int ATTEMPTS_V2 = 59;
    /** What ATTEMPTS was until 3.25.0 (1.5x, StructureRates): attempts from here on draw after all of those, so
     * the first twenty-six rooms of every chunk are drawn exactly as before, and none is laid by a retrofit
     * into a chunk that predates 3.25.0. */
    public static final int ATTEMPTS_324 = 26;
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
        // Every builder is still called straight from here (the capture harness names a write after the
        // frame just inside build()); the writes are recorded for settle() at the end (audit 2026-09-23).
        Written written = WRITTEN.get();
        written.begin(chunk);
        int cx = chunk.getX(), cz = chunk.getZ();
        Random r = new Random(Terrain.mix(terrain.seed + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL + 77L));
        int first = retrofit ? PREVIOUS_ATTEMPTS : 0;
        boolean fresh = StructureRates.fresh(terrain.seed, cx, cz);
        boolean fresh2 = StructureRates.fresh2(terrain.seed, cx, cz);
        for (int attempt = 0; attempt < ATTEMPTS_V2; attempt++) {
            // Footprint kept inside the chunk so no neighbour is ever read.
            int x = 4 + r.nextInt(8);
            int y = r.nextInt(256);
            int z = 4 + r.nextInt(8);
            // The draws still happen for the earlier attempts, because skipping them would
            // shift every attempt after and put the new rooms somewhere else entirely.
            if (attempt >= first && (attempt < ATTEMPTS_324 || fresh) && (attempt < ATTEMPTS || fresh2))
                plain(chunk, terrain, r, x, y, z, caves.region(cx * 16 + x, cz * 16 + z));
        }
        // First lattice: what a chunk generated before this change already contains.
        // (entrance/clearing: structure audit 2026-09-23 -- a ladder way into each buried room, and the
        // ground round each surface one cut back before it is built; see below.)
        if (!retrofit) {
            ward(world, chunk, terrain, caves, 0x5741524400L);
            ossuary(world, chunk, terrain, caves, 0x424F4E4500L);
            cistern(world, chunk, terrain, caves, 0x4349535400L);
            vault(world, chunk, terrain, caves, 0x5641554C5400L);
            warren(world, chunk, terrain, caves, 0x574152524E00L);
            for (int i = 0; i < 5; i++) entrance(chunk, terrain, i, D_SALT_A[i]);
            clearing(chunk, terrain, 5, D_SALT_A[5]);
            spire(world, chunk, terrain, 0x5350495245L);
            clearing(chunk, terrain, 6, D_SALT_A[6]);
            chapel(world, chunk, terrain, 0x434841504CL);
            clearing(chunk, terrain, 7, D_SALT_A[7]);
            checkpoint(world, chunk, terrain, 0x43484B5054L);
        }
        // Second lattice, same spacing and a different salt: the doubling, and what a
        // retrofit lays into ground that already has the first.
        ward(world, chunk, terrain, caves, 0x5741524401L);
        ossuary(world, chunk, terrain, caves, 0x424F4E4501L);
        cistern(world, chunk, terrain, caves, 0x4349535401L);
        vault(world, chunk, terrain, caves, 0x5641554C5401L);
        warren(world, chunk, terrain, caves, 0x574152524E01L);
        for (int i = 0; i < 5; i++) entrance(chunk, terrain, i, D_SALT_B[i]);
        clearing(chunk, terrain, 5, D_SALT_B[5]);
        spire(world, chunk, terrain, 0x5350495246L);
        clearing(chunk, terrain, 6, D_SALT_B[6]);
        chapel(world, chunk, terrain, 0x434841504DL);
        clearing(chunk, terrain, 7, D_SALT_B[7]);
        checkpoint(world, chunk, terrain, 0x43484B5055L);
        // Third lattice (3.25.0, StructureRates): the eight doubled rooms once more at full density, so each is
        // laid 1.5 times as often. These yield to everything older (see taken()), and never go into a chunk
        // that predates 3.25.0, so a retrofit over old ground lays none of them.
        ward(world, chunk, terrain, caves, D_SALT_C[0]);
        ossuary(world, chunk, terrain, caves, D_SALT_C[1]);
        cistern(world, chunk, terrain, caves, D_SALT_C[2]);
        vault(world, chunk, terrain, caves, D_SALT_C[3]);
        warren(world, chunk, terrain, caves, D_SALT_C[4]);
        for (int i = 0; i < 5; i++) entrance(chunk, terrain, i, D_SALT_C[i]);
        clearing(chunk, terrain, 5, D_SALT_C[5]);
        spire(world, chunk, terrain, D_SALT_C[5]);
        clearing(chunk, terrain, 6, D_SALT_C[6]);
        chapel(world, chunk, terrain, D_SALT_C[6]);
        clearing(chunk, terrain, 7, D_SALT_C[7]);
        checkpoint(world, chunk, terrain, D_SALT_C[7]);
        // The big set pieces. They are all new, so a retrofit lays them too.
        Landmarks.populate(world, chunk, terrain, caves);
        Megaliths.populate(world, chunk, terrain, caves);
        Anomalies.populate(world, chunk, terrain, caves);
        Relics.populate(world, chunk, terrain, caves);
        Metropolis.populate(world, chunk, terrain, caves);
        Wonders.populate(world, chunk, terrain, caves);
        Temples.populate(world, chunk, terrain, caves);
        Breach.populate(world, chunk, terrain, caves);
        clearing(chunk, terrain, 8, D_SALT_A[8]);
        sanatorium(world, chunk, terrain, SANATORIUM_CELL, 0x53414E4154L);
        clearing(chunk, terrain, 9, D_SALT_A[9]);
        trial(world, chunk, terrain, TRIAL_CELL, 0x54524941544CL);
        clearing(chunk, terrain, 10, D_SALT_A[10]);
        cabin(world, chunk, terrain, CABIN_CELL, 0x434142494EL);
        clearing(chunk, terrain, 11, D_SALT_A[11]);
        shrine(world, chunk, terrain, SHRINE_CELL, 0x534852494EL);
        clearing(chunk, terrain, 12, D_SALT_A[12]);
        blockhouse(world, chunk, terrain, BLOCKHOUSE_CELL, 0x424C4F434BL);
        clearing(chunk, terrain, 13, D_SALT_A[13]);
        highway(world, chunk, terrain, HIGHWAY_CELL, 0x524F414400L);
        // Second lattice for the six single rooms (3.25.0): sqrt 2 wider, half as many again; yields as above.
        clearing(chunk, terrain, 8, D_SALT_C[8]);
        sanatorium(world, chunk, terrain, D_CELL_C[8], D_SALT_C[8]);
        clearing(chunk, terrain, 9, D_SALT_C[9]);
        trial(world, chunk, terrain, D_CELL_C[9], D_SALT_C[9]);
        clearing(chunk, terrain, 10, D_SALT_C[10]);
        cabin(world, chunk, terrain, D_CELL_C[10], D_SALT_C[10]);
        clearing(chunk, terrain, 11, D_SALT_C[11]);
        shrine(world, chunk, terrain, D_CELL_C[11], D_SALT_C[11]);
        clearing(chunk, terrain, 12, D_SALT_C[12]);
        blockhouse(world, chunk, terrain, D_CELL_C[12], D_SALT_C[12]);
        clearing(chunk, terrain, 13, D_SALT_C[13]);
        highway(world, chunk, terrain, D_CELL_C[13], D_SALT_C[13]);
        // Fourth lattice (3.28.0, the second 1.5x): every room once more, on ground new in 3.28.0 only, yielding to
        // everything older and every tier-2 set piece, catalogue site and sanctuary (see taken()).
        ward(world, chunk, terrain, caves, D_SALT_D[0]);
        ossuary(world, chunk, terrain, caves, D_SALT_D[1]);
        cistern(world, chunk, terrain, caves, D_SALT_D[2]);
        vault(world, chunk, terrain, caves, D_SALT_D[3]);
        warren(world, chunk, terrain, caves, D_SALT_D[4]);
        for (int i = 0; i < 5; i++) entrance(chunk, terrain, i, D_SALT_D[i]);
        clearing(chunk, terrain, 5, D_SALT_D[5]);
        spire(world, chunk, terrain, D_SALT_D[5]);
        clearing(chunk, terrain, 6, D_SALT_D[6]);
        chapel(world, chunk, terrain, D_SALT_D[6]);
        clearing(chunk, terrain, 7, D_SALT_D[7]);
        checkpoint(world, chunk, terrain, D_SALT_D[7]);
        clearing(chunk, terrain, 8, D_SALT_D[8]);
        sanatorium(world, chunk, terrain, D_CELL_D[8], D_SALT_D[8]);
        clearing(chunk, terrain, 9, D_SALT_D[9]);
        trial(world, chunk, terrain, D_CELL_D[9], D_SALT_D[9]);
        clearing(chunk, terrain, 10, D_SALT_D[10]);
        cabin(world, chunk, terrain, D_CELL_D[10], D_SALT_D[10]);
        clearing(chunk, terrain, 11, D_SALT_D[11]);
        shrine(world, chunk, terrain, D_CELL_D[11], D_SALT_D[11]);
        clearing(chunk, terrain, 12, D_SALT_D[12]);
        blockhouse(world, chunk, terrain, D_CELL_D[12], D_SALT_D[12]);
        clearing(chunk, terrain, 13, D_SALT_D[13]);
        highway(world, chunk, terrain, D_CELL_D[13], D_SALT_D[13]);
        settle(chunk, written.bits);             // see settle(): the faults every builder left behind
        written.chunk = null;
    }

    // -- the vanilla room -------------------------------------------------------

    private static boolean plain(Chunk c, Terrain t, Random r, int x, int y, int z, int style) {
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
        // Last in the order of precedence (3.23 structure audit): the room would cut into a catalogue
        // site stamped before it, or be half overwritten by a set piece or built room laid after it.
        // Asked only once the blocks have passed, so the lookup costs nothing on the attempts that fail.
        int wx = c.getX() * 16 + x - rx - 1, wz = c.getZ() * 16 + z - rz - 1;
        // 3.28.0: every layer, tier 2 included. A tier-2 site never reaches a chunk that existed before 3.28.0, so
        // in such a chunk (a retrofit) these answer exactly as the 3.25-3.27 tests did.
        if (Megaliths.occupiedAll3(t, wx, wz, rx * 2 + 3, rz * 2 + 3, y + 4) || catalogued(t, wx, wz, rx * 2 + 3, rz * 2 + 3, y + 4, 2)
                || roomed(t, wx, wz, rx * 2 + 3, rz * 2 + 3, y - 1, y + 4)) {
            TAKEN.incrementAndGet();
            return false;
        }
        OK.incrementAndGet();

        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    int lx = x + dx, ly = y + dy, lz = z + dz;
                    boolean shell = dx == -rx - 1 || dx == rx + 1 || dz == -rz - 1 || dz == rz + 1;
                    if (!shell && dy != -1) { set(c, lx, ly, lz, 0, 0); continue; }
                    if (ly < 1) continue;
                    if (dy == -1 && !c.getBlock(lx, ly, lz).getType().isSolid()) continue;
                    int roll = r.nextInt(4);                     // drawn exactly as before, one per cell
                    // The vanilla wall rule (structure audit 2026-09-23): the cave openings that qualified
                    // the room stay open -- a wall cell over air goes, and air in the wall is left alone.
                    // Walling every shell cell sealed the room in rock with no way in.
                    if (dy >= 0 && !c.getBlock(lx, ly - 1, lz).getType().isSolid()) { set(c, lx, ly, lz, 0, 0); continue; }
                    if (dy >= 0 && !c.getBlock(lx, ly, lz).getType().isSolid()) continue;
                    set(c, lx, ly, lz, 4, roll != 0 ? 0 : -1);
                }
            }
        }
        // Two chests, placed against a wall the way vanilla places them.
        for (int n = 0; n < 2; n++) {
            for (int tries = 0; tries < 3; tries++) {
                int lx = x + r.nextInt(rx * 2 + 1) - rx, lz = z + r.nextInt(rz * 2 + 1) - rz;
                if (c.getBlock(lx, y, lz).getType() != Material.AIR) continue;
                int walls = 0, facing = 2;
                // The chest turns its front away from the one wall it stands against, as vanilla's
                // correctFacing does (structure audit 2026-09-23: it always faced north, often into that wall).
                if (c.getBlock(Math.max(0, lx - 1), y, lz).getType().isSolid()) { walls++; facing = 5; }
                if (c.getBlock(Math.min(15, lx + 1), y, lz).getType().isSolid()) { walls++; facing = 4; }
                if (c.getBlock(lx, y, Math.max(0, lz - 1)).getType().isSolid()) { walls++; facing = 3; }
                if (c.getBlock(lx, y, Math.min(15, lz + 1)).getType().isSolid()) { walls++; facing = 2; }
                if (walls != 1) continue;
                set(c, lx, y, lz, 54, facing);
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
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(0, salt), salt, 13, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 6, a.z + 4);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 12 || dz == 0 || dz == 8 || dy == 0 || dy == 5;
            if (shell) { set(c, lx, wy, lz, 98, r.nextInt(3) == 0 ? 2 : 0); continue; }
            // Eight 2x2 cells off the aisle (dz 4), four each side (structure audit 2026-09-23). The old
            // partitions put the bars on the dividers and left every bay open to the aisle, so nothing
            // was a cell: the dividers are solid brick now, and each cell has a barred front with an iron
            // door in it (the entrance shaft comes down at the west end of the aisle).
            boolean divider = (dx == 3 || dx == 6 || dx == 9) && dz != 4;
            if (divider) { set(c, lx, wy, lz, 98, (dx * 5 + dz * 3 + dy) % 7 == 0 ? 2 : 0); continue; }
            if (dz == 3 || dz == 5) {
                boolean door = dx == 2 || dx == 4 || dx == 8 || dx == 10;
                if (!door) { set(c, lx, wy, lz, dy <= 3 ? 101 : 98, 0); continue; }
                // The corner cells, where the inmates still are, stay shut (a plate each side opens them);
                // the middle cells stand open.
                boolean shut = dx == 2 || dx == 10;
                int facing = dz == 3 ? 3 : 1;
                if (dy == 1) set(c, lx, wy, lz, 71, shut ? facing : facing | 4);
                else if (dy == 2) set(c, lx, wy, lz, 71, 8);
                else set(c, lx, wy, lz, 98, 0);                                  // lintel
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            int cell = dz < 4 ? dz : 8 - dz;                                     // 1 = back row, 2 = by the bars
            boolean north = dz < 4;
            if (dy == 1 && (dx == 1 || dx == 11) && cell == 1) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 1 && (dx == 2 || dx == 10) && (cell == 2 || dz == 4)) set(c, lx, wy, lz, 70, 0);   // the plates
            else if (dy == 1 && (dx == 4 || dx == 5 || dx == 7 || dx == 8) && cell == 1) {
                // A cot along the back wall, its head to the middle divider.
                boolean head = dx == 5 || dx == 7;
                set(c, lx, wy, lz, 26, (dx < 6 ? 3 : 1) | (head ? 8 : 0));
            } else if (dy == 1 && (dx == 5 || dx == 7) && cell == 2) {
                // By the bars: a locker in the north cells, a wash basin in the south ones.
                if (north) { set(c, lx, wy, lz, 54, dx == 5 ? 4 : 5); fill(c.getBlock(lx, wy, lz), r, style, true); }
                else set(c, lx, wy, lz, 118, 0);
            } else if (dy == 1 && dx == 11 && dz == 4) {
                // The warden's locker at the dead end of the aisle, and his notice over it.
                set(c, lx, wy, lz, 54, 4); fill(c.getBlock(lx, wy, lz), r, style, true);
            } else if (dy == 3 && dx == 11 && dz == 4) {
                set(c, lx, wy, lz, 68, 4); sign(c.getBlock(lx, wy, lz), "", "QUARANTINE", "KEEP DOORS", "SHUT");
            } else if (dy == 4 && cell == 1 && (dx == 2 || dx == 5 || dx == 7 || dx == 10)) set(c, lx, wy, lz, 30, 0);
            else if (dy == 4 && dz == 4 && dx == 6) set(c, lx, wy, lz, 89, 0);
            // Dim red lamps on the dividers at the two ends, so the corner cells stay dark enough to wake.
            else if (dy == 3 && dz == 4 && (dx == 3 || dx == 9)) set(c, lx, wy, lz, 76, dx == 3 ? 3 : 4);
        }
        piers(c, a, new int[][]{{0, 0}, {6, 0}, {12, 0}, {0, 8}, {6, 8}, {12, 8}}, 98, 0);
    }

    /** Writes the text of a sign just placed (a sign's state may be updated; only a chest's may not). */
    private static void sign(Block block, String... lines) {
        try {
            org.bukkit.block.Sign s = (org.bukkit.block.Sign) block.getState();
            for (int i = 0; i < lines.length && i < 4; i++) s.setLine(i, lines[i]);
            s.update(true, false);
        } catch (RuntimeException ignored) { }
    }

    // -- Ossuary ----------------------------------------------------------------

    private static void ossuary(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(1, salt), salt, 11, 11);
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
            if (wall) {
                int stone = r.nextInt(4) == 0 ? 216 : 98, moss = r.nextInt(4) == 0 ? 0 : 1;   // same draws
                set(c, lx, wy, lz, stone, stone == 216 ? 0 : moss);         // bone block has no data 1
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Niches around the wall hold the dead; the altar holds what they were buried with.
            // Structure audit 2026-09-23: everything in the ring stands on the floor now (chests and bones
            // rolled at dy2 hung a block over it, one on a chest's lid); the dead lie at the seven compass
            // points the entrance (north, dx5 dz1) leaves free, a skull on each; the ring of 24 lights is
            // four, so the altar keeper is dark enough to wake. The niche rolls are drawn as before.
            boolean niche = radius > 3.2 && radius <= 4.3 && dy >= 1 && dy <= 2;
            boolean entry = dx == 5 && dz == 1;
            boolean point = niche && !entry && (dx == 5 || dz == 5 || Math.abs(dx - 5) == Math.abs(dz - 5));
            boolean chestRoll = niche && r.nextInt(7) == 0, boneRoll = niche && !chestRoll && r.nextInt(11) == 0;
            int under = c.getBlock(lx, wy - 1, lz).getTypeId();
            if (point) {
                if (dy == 1) set(c, lx, wy, lz, 216, Math.abs(dx - 5) > Math.abs(dz - 5) ? 8 : Math.abs(dx - 5) < Math.abs(dz - 5) ? 4 : 0);
                else set(c, lx, wy, lz, 144, 1);
            }
            else if (chestRoll && dy == 1 && !entry) {
                int face = Math.abs(dz - 5) >= Math.abs(dx - 5) ? (dz < 5 ? 3 : 2) : (dx < 5 ? 5 : 4);   // toward the altar
                set(c, lx, wy, lz, 54, face); fill(c.getBlock(lx, wy, lz), r, style, true);
            }
            else if (boneRoll && !entry && (dy == 1 || under == 216)) set(c, lx, wy, lz, 216, 4);
            else if (dy == 1 && radius < 1.6) set(c, lx, wy, lz, 98, 3);
            else if (dy == 1 && dz == 5 && (dx == 3 || dx == 7)) { set(c, lx, wy, lz, 54, dx == 3 ? 4 : 5); fill(c.getBlock(lx, wy, lz), r, style, true); }
            else if (dy == 2 && radius < 0.6) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
            else if (dy == 5 && radius > 3.6 && Math.abs(dx - 5) == Math.abs(dz - 5)) set(c, lx, wy, lz, 89, 0);
        }
        piers(c, a, new int[][]{{2, 2}, {8, 2}, {2, 8}, {8, 8}, {5, 10}, {0, 5}, {10, 5}}, 98, 1);
    }


    // -- Flooded Cistern (underground) -----------------------------------------

    /** A sunken reservoir: standing water, pillared bays and a dry ledge with the loot. */
    private static void cistern(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(2, salt), salt, 13, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 6, a.z + 6);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = 0; dy < 8; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 12 || dz == 0 || dz == 12 || dy == 0 || dy == 7;
            if (shell) {
                int moss = r.nextInt(3) == 0 ? 1 : 0;
                // Inflow grates high in the east and west walls, over the water (structure audit 2026-09-23).
                boolean grate = (dx == 0 || dx == 12) && dz == 6 && dy >= 4 && dy <= 5;
                set(c, lx, wy, lz, grate ? 101 : 98, grate ? 0 : moss);
                continue;
            }
            // Four pillars carry the roof; the rest of the floor is flooded. Each ends in a lamp under the
            // vault: the lamp test came after this one and was never reached, so the room was pitch dark.
            boolean pillar = (dx == 3 || dx == 9) && (dz == 3 || dz == 9);
            if (pillar) { set(c, lx, wy, lz, dy == 6 ? 89 : 98, 0); continue; }
            // A dry ledge runs along one wall so the room can actually be looted: full blocks two wide now,
            // standing clear of the water, so the chests sit on it and there is a walk past them (the
            // bottom-slab ledge was under the water line and one wide, and the chests faced the wall).
            boolean ledge = dz <= 2 && dy == 1;
            if (ledge) { set(c, lx, wy, lz, 98, 0); continue; }
            // The two keepers stand on a walk along the far wall, not on the water (they floated on it).
            if (dy == 1 && dz == 11) { set(c, lx, wy, lz, 98, (dx * 3) % 4 == 1 ? 1 : 0); continue; }
            if (dy == 1) { set(c, lx, wy, lz, 9, 0); continue; }   // standing water
            set(c, lx, wy, lz, 0, 0);
            if (dy == 2 && dz == 1 && dx % 4 == 2) { set(c, lx, wy, lz, 54, 3); fill(c.getBlock(lx, wy, lz), r, style, true); }
            else if (dy == 2 && dz == 11 && (dx == 4 || dx == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
        }
        piers(c, a, new int[][]{{0, 0}, {12, 0}, {0, 12}, {12, 12}, {3, 3}, {9, 3}, {3, 9}, {9, 9}}, 98, 0);
    }

    // -- Reliquary Vault (underground) -----------------------------------------

    /** A small strongroom: the loot is caged behind bars and watched from outside. */
    private static void vault(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(3, salt), salt, 11, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 5, a.z + 5);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shell = dx == 0 || dx == 10 || dz == 0 || dz == 10 || dy == 0 || dy == 5;
            if (shell) {
                int crack = r.nextInt(4) == 0 ? 2 : 0;
                // One lamp set in the vault over the cage lights the reliquary, not the guard walk: the four
                // corner lamps sat over the corner guards and kept them from ever waking (audit 2026-09-23).
                if (dy == 5 && dx == 5 && dz == 5) set(c, lx, wy, lz, 89, 0);
                else set(c, lx, wy, lz, 98, crack);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // The cage: a 5x5 bar enclosure in the middle, open on one side only.
            boolean cageWall = (dx >= 3 && dx <= 7 && dz >= 3 && dz <= 7)
                && (dx == 3 || dx == 7 || dz == 3 || dz == 7) && !(dz == 7 && dx == 5);
            if (cageWall && dy >= 1 && dy <= 3) { set(c, lx, wy, lz, 101, 0); continue; }
            if (dy == 1 && dx >= 4 && dx <= 6 && dz >= 4 && dz <= 6) {
                // The centre chest faces the gate; the others stand only on the four diagonal cells, facing
                // the lane from the gate, so no two touch (they merged into double and triple chests) and
                // the lane stays clear. Same draws; two in three diagonals keep about the old haul.
                if (dx == 5 && dz == 5) { set(c, lx, wy, lz, 54, 3); fill(c.getBlock(lx, wy, lz), r, style, true); }
                else if (r.nextInt(3) != 0 && dx != 5 && dz != 5) { set(c, lx, wy, lz, 54, dx == 4 ? 5 : 4); fill(c.getBlock(lx, wy, lz), r, style, true); }
            } else if (dy == 1 && (dx == 1 || dx == 9) && (dz == 1 || dz == 9)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, style); }
        }
        piers(c, a, new int[][]{{0, 0}, {10, 0}, {0, 10}, {10, 10}, {5, 0}, {0, 5}, {10, 5}}, 98, 2);
    }

    /**
     * Stone piers under a buried room where a cave has opened beneath its floor (structure audit 2026-09-23;
     * the rooms stood on nothing over caverns): at the given columns, down through air or liquid to the cave
     * floor -- at most eight blocks, and only where one is reached.
     */
    private static void piers(Chunk c, Anchor a, int[][] spots, int id, int data) {
        for (int[] p : spots) {
            int lx = a.x + p[0] - c.getX() * 16, lz = a.z + p[1] - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) continue;
            int y = a.y - 1;
            while (y > 1 && y > a.y - 10 && (c.getBlock(lx, y, lz).getTypeId() == 0 || c.getBlock(lx, y, lz).isLiquid())) y--;
            if (y == a.y - 1 || y < a.y - 9 || !c.getBlock(lx, y, lz).getType().isSolid()) continue;
            for (int yy = y + 1; yy < a.y; yy++) set(c, lx, yy, lz, id, data);
        }
    }

    // -- Sporecist Warren (underground) ----------------------------------------

    /** An overgrown hollow: fungal caps, heavy webbing and things that live in it. */
    private static void warren(World w, Chunk c, Terrain t, Caves caves, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(4, salt), salt, 15, 11);
        if (a == null) return;
        Random r = new Random(a.seed);
        int style = caves.region(a.x + 7, a.z + 5);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            // Rounded hollow rather than a box, so it reads as grown instead of built.
            double rad = Math.sqrt(((dx - 7) / 7.2) * ((dx - 7) / 7.2) + ((dz - 5) / 5.2) * ((dz - 5) / 5.2));
            // 1.001, not 1.0: the four cells at the ends of the short axis (rad 1.0009) sit against open
            // floor, and skipping them left four holes in the shell (structure audit 2026-09-23).
            if (rad > 1.001) continue;
            boolean shell = rad > 0.82 || dy == 0 || dy == 5;
            if (shell) { set(c, lx, wy, lz, 4, r.nextInt(2) == 0 ? -1 : 0); continue; }
            set(c, lx, wy, lz, 0, 0);
            // The cell in front of the ladder recess (entrance(), west end) is kept clear. Same draws.
            boolean entry = dx == 2 && dz == 5 && dy <= 2;
            if (dy == 1 && r.nextInt(9) == 0) { if (!entry) set(c, lx, wy, lz, r.nextInt(2) == 0 ? 99 : 100, 14); else r.nextInt(2); continue; }
            if (r.nextInt(11) == 0) { if (!entry) set(c, lx, wy, lz, 30, 0); continue; }   // webbing
            if (dy == 1 && (dx == 4 || dx == 10) && dz == 5) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, Caves.FUNGAL); }
            else if (dy == 1 && rad < 0.5 && r.nextInt(6) == 0) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, style, false); }
            else if (dy == 4 && r.nextInt(18) == 0) set(c, lx, wy, lz, 89, 0);
        }
    }

    // -- Signal Spire (surface) -------------------------------------------------

    /** A watchtower: a climbable shaft with a lookout room and its keeper at the top. */
    private static void spire(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cellFor(5, salt), salt, 7, 7);
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
            // Structure audit 2026-09-23: the lookout had no floor -- keeper and chests hung twelve blocks up
            // the shaft and the ladder ran out in the air beside them. The lookout floor is laid one under
            // them with a hatch for the ladder, and the ground floor is laid level with the doorway (the
            // shaft floor was a block under the threshold).
            boolean hatch = dx == 1 && dz == 1;
            if (dy == 0 || (dy == height - 5 && !hatch)) { set(c, lx, wy, lz, 98, 0); continue; }
            set(c, lx, wy, lz, 0, 0);
            // The ladder hangs on the north wall (65:2 hung it on the shaft air), up through the hatch.
            if (hatch && dy <= height - 4) { set(c, lx, wy, lz, 65, 3); continue; }
            if (dy == height - 4 && dx == 3 && dz == 3) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == height - 4 && (dx == 5 || dx == 2) && dz == 5) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            // A torch over the door below; one red lamp by a slit up top, so the keeper still wakes at night
            // (the glowstone two blocks over it never let it).
            else if (dy == 3 && dx == 3 && dz == 1) set(c, lx, wy, lz, 50, 3);
            else if (dy == height - 3 && dx == 5 && dz == 1) set(c, lx, wy, lz, 76, 2);
        }
        // The signal itself: a fire that does not go out, on a netherrack hearth in the middle of the roof.
        int lx = a.x + 3 - c.getX() * 16, lz = a.z + 3 - c.getZ() * 16;
        if (lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15 && a.y + height <= 250) {
            set(c, lx, a.y + height - 1, lz, 87, 0);
            set(c, lx, a.y + height, lz, 51, 0);
        }
    }

    // -- Ashen Chapel (surface) -------------------------------------------------

    /** A burnt-out chapel: a nave with broken windows and whatever the altar kept. */
    private static void chapel(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cellFor(6, salt), salt, 11, 15);
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
                // A rib stands on the wall or on the rib below it; one over a gap hung in the air and was
                // pruned as a floater (structure audit 2026-09-22). Same draws as before.
                // The four springers the surviving roof ribs rise from always stand (audit 2026-09-23).
                boolean springer = dy == 7 && (dx == 0 || dx == 10) && (dz == 4 || dz == 10);
                if (wall && (r.nextInt(3) != 0 || springer) && c.getBlock(lx, wy - 1, lz).getTypeId() != 0) set(c, lx, wy, lz, 4, -1);
                else set(c, lx, wy, lz, 0, 0);
                continue;
            }
            if (wall) {
                boolean window = dy >= 3 && dy <= 4 && (dz % 4 == 2) && (dx == 0 || dx == 10);
                boolean door = dy >= 1 && dy <= 2 && dz == 0 && (dx == 5);
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, r.nextInt(3) == 0 ? 0 : 102, 0); continue; }
                int stone = r.nextInt(6) == 0 ? 4 : 98, cracked = r.nextInt(5) == 0 ? 2 : 0;   // same draws
                set(c, lx, wy, lz, stone, stone == 98 ? cracked : 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Pews down the nave, the altar at the far end. Structure audit 2026-09-23: three seats a pew,
            // facing the altar (109:2 turned their backs to it), one in a few burnt down to a slab or gone.
            int wear = (dx * 7 + dz * 11) % 13;
            if (dy == 1 && dz >= 3 && dz <= 10 && dz % 2 == 1 && dx >= 2 && dx <= 8 && dx != 5) {
                if (wear == 3) set(c, lx, wy, lz, 44, 5);
                else if (wear != 8) set(c, lx, wy, lz, 109, 3);
                continue;
            }
            if (dy == 1 && dz == 12 && dx >= 4 && dx <= 6) { set(c, lx, wy, lz, 98, 3); continue; }
            if (dy == 2 && dz == 12 && dx == 5) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            else if (dy == 1 && dz == 2 && (dx == 2 || dx == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            else if (dy == 2 && dz == 12 && (dx == 4 || dx == 6)) set(c, lx, wy, lz, 50, 5);            // altar candles
            // The nave lights are sconces on the side walls (they hung in the air over the pews); none by the
            // door, where the two keepers wait.
            else if (dy == 4 && (dx == 1 || dx == 9) && (dz == 8 || dz == 13)) set(c, lx, wy, lz, 50, dx == 1 ? 1 : 2);
            // What came down with the roof, heaped in the corners and along the walls, clear of the aisle.
            else if (dy == 1 && ((dx == 1 && (dz == 6 || dz == 13)) || (dx == 9 && (dz == 8 || dz == 13)))) set(c, lx, wy, lz, dz == 13 ? 4 : 98, dz == 13 ? -1 : 2);
            else if (dy == 2 && dx == 1 && dz == 13) set(c, lx, wy, lz, 44, 3);
            else if (dy == 1 && (dx == 1 || dx == 9) && (dz == 7 || dz == 12)) set(c, lx, wy, lz, 44, dx == 1 ? 3 : 5);
            // Soot on the flagstones.
            else if (dy == 1 && wear == 5 && dz >= 2) set(c, lx, wy, lz, 171, (dx + dz) % 2 == 0 ? 7 : 15);
        }
        // The roof, legible as a roof that fell in: both gables still stand to a peak (a cross on the one over
        // the door, a window in each), one rib still spans the nave and the other broke off halfway. Every
        // piece rests on the wall or the piece before it.
        for (int dz = 0; dz <= 14; dz += 14) {
            for (int dy = 7; dy <= 10; dy++) for (int dx = dy - 6; dx <= 16 - dy; dx++) {
                boolean pane = dy == 8 && dx == 5;
                put(c, a, dx, dy, dz, pane ? 102 : 98, pane ? 0 : (dx * 3 + dy + dz) % 5 == 0 ? 2 : 0);
            }
        }
        for (int dy = 11; dy <= 13; dy++) put(c, a, 5, dy, 0, 139, 0);
        put(c, a, 4, 12, 0, 139, 0);
        put(c, a, 6, 12, 0, 139, 0);
        for (int dz = 4; dz <= 10; dz += 6) {
            boolean whole = dz == 10;
            put(c, a, 1, 7, dz, 109, 5);
            put(c, a, 1, 8, dz, 98, 0);
            put(c, a, 2, 8, dz, 109, 5);
            put(c, a, 9, 7, dz, 109, 4);
            put(c, a, 9, 8, dz, 98, 0);
            if (whole) {
                for (int dx = 3; dx <= 7; dx++) put(c, a, dx, 8, dz, 44, 13);
                put(c, a, 8, 8, dz, 109, 4);
            } else {
                put(c, a, 3, 8, dz, 44, 13);             // snapped off over the nave
            }
        }
    }

    /** One block of a room at (dx, dy, dz) from its anchor, when that cell is in this chunk. */
    private static void put(Chunk c, Anchor a, int dx, int dy, int dz, int id, int data) {
        int lx = a.x + dx - c.getX() * 16, lz = a.z + dz - c.getZ() * 16, wy = a.y + dy;
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) return;
        set(c, lx, wy, lz, id, data);
    }

    // -- Roadside Checkpoint (surface) ------------------------------------------

    /** A quarantine post: a barred blockhouse behind a broken barricade. */
    private static void checkpoint(World w, Chunk c, Terrain t, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cellFor(7, salt), salt, 13, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -3; dy < 7; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            // A sandbag-and-bar barricade stands two blocks out in front of the hut.
            // Structure audit 2026-09-23: the barricade stands on the apron (its ground course was cut to air,
            // so it hung over a gutter); the gap in front of the door is the controlled way through, a gate
            // left open, and two sandbags lie where they were knocked off beside the other gaps.
            if (dz == 0) {
                if (dy == 0) set(c, lx, wy, lz, 1, 6);
                else if (dy >= 1 && dy <= 2 && dx % 3 != 0) set(c, lx, wy, lz, dy == 2 ? 101 : 159, dy == 2 ? 0 : 8);
                else if (dy == 1 && dx == 6) set(c, lx, wy, lz, 107, 4);
                else set(c, lx, wy, lz, 0, 0);
                continue;
            }
            boolean hut = dx >= 2 && dx <= 10 && dz >= 3 && dz <= 7;
            if (!hut) {
                if (dy == 0) set(c, lx, wy, lz, 1, 6);
                else if (dy == 1 && dz == 1 && (dx == 4 || dx == 8)) set(c, lx, wy, lz, 159, 8);
                // The post's notices, either side of the door.
                else if (dy == 3 && dz == 2 && (dx == 5 || dx == 7)) {
                    set(c, lx, wy, lz, 68, 2);
                    if (dx == 5) sign(c.getBlock(lx, wy, lz), "", "CHECKPOINT", "HALT", "");
                    else sign(c.getBlock(lx, wy, lz), "QUARANTINE", "ZONE", "NO ENTRY", "BEYOND");
                }
                else set(c, lx, wy, lz, 0, 0);
                continue;
            }
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
            // The guard post fitted out (audit 2026-09-23; it was two chests and a spawner in a bare box):
            // counters under the barred windows with the gate lever, a cot and a wash basin along the back,
            // and one red emergency lamp instead of the glowstone over the spawner that kept it asleep.
            else if (dy == 1 && dz == 4 && (dx == 3 || dx == 4 || dx == 8 || dx == 9)) set(c, lx, wy, lz, 44, 13);
            else if (dy == 2 && dz == 4 && dx == 8) set(c, lx, wy, lz, 69, 5);
            else if (dy == 1 && dz == 6 && (dx == 4 || dx == 5)) set(c, lx, wy, lz, 26, dx == 4 ? 9 : 1);
            else if (dy == 1 && dz == 6 && dx == 8) set(c, lx, wy, lz, 118, 0);
            else if (dy == 3 && dz == 6 && dx == 6) set(c, lx, wy, lz, 76, 4);
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
    private static void sanatorium(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 15, 13);
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
            // The holes in the roof fall only on cells with even dx and dz, so no slab is left ringed by holes
            // and hanging (structure audit 2026-09-23); same draw, half as many holes.
            if (dy == 8) { boolean gap = r.nextInt(4) <= 1 && dx % 2 == 0 && dz % 2 == 0; set(c, lx, wy, lz, gap ? 0 : 44, gap ? 0 : 5); continue; }
            if (dy == 0) {
                // Grated corridor floor; tile everywhere else, stained where the wet reached.
                if (spine && dx % 2 == 1) set(c, lx, wy, lz, 101, 0);
                else set(c, lx, wy, lz, 159, rust == 0 ? 12 : rust == 1 ? 1 : 8);
                continue;
            }
            if (wall) {
                // Windows at every fourth block of each wall, the corners solid: "dx % 4 == 2" also matched
                // the whole east wall (dx 14), which was one long window band (structure audit 2026-09-23).
                boolean corner = (dx == 0 || dx == 14) && (dz == 0 || dz == 12);
                boolean window = dy >= 3 && dy <= 4 && !corner
                    && (((dz == 0 || dz == 12) && dx % 4 == 2) || ((dx == 0 || dx == 14) && dz % 4 == 2));
                boolean door = dy >= 1 && dy <= 2 && dz == 0 && dx == 7;
                if (door) { set(c, lx, wy, lz, 0, 0); continue; }
                if (window) { set(c, lx, wy, lz, r.nextInt(3) == 0 ? 0 : 102, 0); continue; }
                set(c, lx, wy, lz, 159, rust < 2 ? 1 : rust < 4 ? 12 : 0);
                continue;
            }
            /* Structure audit 2026-09-23: a corridor with wards off it. The spine (dz 6) is walled on both
             * sides (dz 5 and 7) the height of the partitions; the four corner wards open off it through iron
             * ward doors with a button either side (the old doors sat in the partitions, joined bay to bay,
             * and had no way to open them), and the front door leads through admissions (north middle) onto
             * the corridor, with supply and treatment across it (south middle). */
            boolean partition = (dx == 4 || dx == 10) && dz != 6 && dy <= 3;
            boolean wardFront = (dz == 5 || dz == 7) && dy <= 3;
            if (partition || wardFront) {
                boolean wardDoor = wardFront && (dx == 2 || dx == 12) && dy <= 2;
                boolean opening = wardFront && dx == 7 && dy <= 2;
                if (wardDoor) set(c, lx, wy, lz, 71, dy == 2 ? 8 : dz == 5 ? 3 : 1);   // upper half over lower
                else if (opening) set(c, lx, wy, lz, 0, 0);
                else set(c, lx, wy, lz, 159, rust == 0 ? 12 : rust == 1 ? 1 : 8);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            boolean westWard = dx <= 3, eastWard = dx >= 11, north = dz < 6;
            int back = north ? 1 : 11;                                        // the far wall of each room
            // A button either side of each ward door, on the ward-front wall beside it (it powers that wall
            // block, and the block the door): corridor side at dx 1/13 for the north doors and dx 3/11 for
            // the south ones, ward side the other way round.
            if (dy == 2) {
                int button = 0;
                if ((spine && (dx == 1 || dx == 13)) || (dz == 8 && (dx == 1 || dx == 13))) button = 3;
                else if ((spine && (dx == 3 || dx == 11)) || (dz == 4 && (dx == 3 || dx == 11))) button = 4;
                if (button != 0) { set(c, lx, wy, lz, 77, button); continue; }
            }
            // The corridor lights hang on the ward-front wall either side of the spine; placed standing on
            // the air of the corridor they popped off (structure audit 2026-09-22).
            if (dy == 3 && spine && dx % 6 == 4) { set(c, lx, wy, lz, 76, 3); continue; }
            if (spine) continue;
            if (dy == 7 && r.nextInt(16) == 0) { set(c, lx, wy, lz, 30, 0); continue; }   // webs under the roof
            if (dy == 1 && (dx == 2 || dx == 12) && (dz == 2 || dz == 10)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
            // Two beds in each corner ward, heads to the far wall, a lane between them to the keeper.
            else if (dy == 1 && (westWard || eastWard) && (dx == 1 || dx == 3 || dx == 11 || dx == 13)
                     && (north ? dz == 3 || dz == 4 : dz == 8 || dz == 9)) {
                boolean head = dz == 3 || dz == 9;
                set(c, lx, wy, lz, 26, (north ? 2 : 0) | (head ? 8 : 0));
            }
            // One red lamp a room, on the outer wall.
            else if (dy == 3 && westWard && dx == 1 && (dz == 3 || dz == 9)) set(c, lx, wy, lz, 76, 1);
            else if (dy == 3 && eastWard && dx == 13 && (dz == 3 || dz == 9)) set(c, lx, wy, lz, 76, 2);
            else if (dy == 3 && dx == 8 && dz == back) set(c, lx, wy, lz, 76, north ? 3 : 4);
            // Admissions: a counter across the room with the lane through it, the records locker behind it.
            else if (dy == 1 && north && dz == 3 && dx >= 5 && dx <= 9 && dx != 7) set(c, lx, wy, lz, 44, 8);
            else if (dy == 1 && north && dx == 5 && dz == 1) { set(c, lx, wy, lz, 54, 3); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            // Supply and treatment: the medicine chest, a wash basin, a gurney with its drip stand.
            else if (dy == 1 && !north && dx == 7 && dz == 10) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
            else if (dy == 1 && !north && dx == 5 && dz == 11) set(c, lx, wy, lz, 118, 0);
            else if (dy == 1 && !north && (dx == 8 || dx == 9) && dz == 9) set(c, lx, wy, lz, 44, 8);
            else if (dy <= 2 && !north && dx == 9 && dz == 11) set(c, lx, wy, lz, 101, 0);
        }
    }

    // -- Trial Ground (surface) -------------------------------------------------

    /**
     * A clearing somebody uses. Machines that hum in the open, hooks set in the dirt, and a
     * shack over a cellar with more hooks in it than the work would ever need.
     */
    private static void trial(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 13, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -4; dy < 7; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean shack = dx >= 7 && dx <= 12 && dz >= 7 && dz <= 12;
            if (dy < 0) {
                /* The cellar under the shack (structure audit 2026-09-23): it was one course deep and sealed,
                 * under a solid floor with a ladder stub standing on it. Now three blocks of headroom in a
                 * cobble box (written solid even where a cave runs under the clearing), a ladder up the east
                 * wall to an open hatch, a hook post and hooks from the joists, the basin, and what is kept
                 * down here: the spawner that stood out in the yard with nothing to explain it. */
                if (shack) {
                    boolean ring = dx == 7 || dx == 12 || dz == 7 || dz == 12;
                    if (ring || dy == -4) { set(c, lx, wy, lz, 4, 0); continue; }
                    set(c, lx, wy, lz, 0, 0);
                    if (dx == 11 && dz == 11) set(c, lx, wy, lz, 65, 4);
                    else if (dx == 9 && dz == 9) set(c, lx, wy, lz, 85, 0);
                    else if (dy == -1 && ((dx == 10 && dz == 8) || (dx == 8 && dz == 10))) set(c, lx, wy, lz, 101, 0);
                    else if (dy == -3 && dx == 8 && dz == 11) set(c, lx, wy, lz, 118, 0);
                    else if (dy == -3 && dx == 8 && dz == 8) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
                    continue;
                }
                if (dy >= -2) set(c, lx, wy, lz, 3, 1);     // bare packed ground everywhere else
                continue;
            }
            if (dy == 0) {
                if (shack && dx == 11 && dz == 11) { set(c, lx, wy, lz, 65, 4); continue; }   // the ladder through the hatch
                set(c, lx, wy, lz, shack ? 5 : 3, shack ? 0 : 1);
                continue;
            }
            if (shack) {
                boolean wall = dx == 7 || dx == 12 || dz == 7 || dz == 12;
                if (dy >= 5) { set(c, lx, wy, lz, dy == 5 ? 53 : 0, 0); continue; }
                if (wall) {
                    // Missing boards on the faces only: never a corner post, the top course or the board the
                    // lamp hangs on. Same draws.
                    boolean door = dy <= 2 && dz == 7 && dx == 9;
                    boolean keep = ((dx == 7 || dx == 12) && (dz == 7 || dz == 12)) || dy == 4 || (dx == 7 && dz == 10 && dy == 3);
                    set(c, lx, wy, lz, door ? 0 : r.nextInt(7) == 0 && !keep ? 0 : 5, 0);
                    continue;
                }
                set(c, lx, wy, lz, 0, 0);
                // The work shed: a bench, hooks from the roof, one lamp by the door; the chest and the hatch.
                if (dy == 1 && dx == 9 && dz == 10) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                else if (dy == 1 && dx == 11 && dz == 11) set(c, lx, wy, lz, 96, 6);       // its lid, standing open
                else if (dy == 1 && dx == 8 && dz == 8) set(c, lx, wy, lz, 58, 0);
                else if (dy == 4 && dx == 10 && (dz == 9 || dz == 10)) set(c, lx, wy, lz, 101, 0);
                else if (dy == 3 && dx == 8 && dz == 10) set(c, lx, wy, lz, 50, 1);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Four machines out in the open, and a hook standing near each.
            boolean machine = (dx == 2 || dx == 5) && (dz == 2 || dz == 5);
            if (dy == 1 && machine) { set(c, lx, wy, lz, 43, 8); continue; }      // 43:8, was iron block
            if (dy == 2 && machine) { set(c, lx, wy, lz, 76, 5); continue; }
            // A hook is a post with an arm and something hanging off it (a bar on top read as an aerial).
            boolean hook = (dx == 3 && dz == 9) || (dx == 10 && dz == 3);
            if (hook && dy >= 1 && dy <= 3) { set(c, lx, wy, lz, 85, 0); continue; }
            boolean arm = (dx == 4 && dz == 9) || (dx == 10 && dz == 4);
            if (arm && dy == 3) { set(c, lx, wy, lz, 85, 0); continue; }
            if (arm && dy == 2) { set(c, lx, wy, lz, 101, 0); continue; }
            if (dy == 1 && r.nextInt(26) == 0) { set(c, lx, wy, lz, 126, 0); continue; }   // dropped pallet
        }
    }

    // -- Deadwood Cabin (surface) -----------------------------------------------

    /** A one-room cabin with a trapdoor in the floor, and a cellar that explains the stains. */
    private static void cabin(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 11, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -6; dy < 8; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean cellar = dx >= 3 && dx <= 7 && dz >= 2 && dz <= 6;
            /* Structure audit 2026-09-23: the way down was a one-block ladder standing loose on the floor over
             * an open drop onto the spawner, with no way back up. The hatch is now against the cellar's south
             * wall, a ladder on that wall up through the floor with the lid standing open over it (it carries
             * the climb on); the cellar floor is two courses (a sealed void lay under it); the stains are in
             * the floor instead of red cubes standing in the room. */
            if (dy < -1) {
                if (!cellar) { set(c, lx, wy, lz, 1, 0); continue; }
                boolean shell = dx == 3 || dx == 7 || dz == 2 || dz == 6 || dy <= -5;
                if (shell) { set(c, lx, wy, lz, 4, r.nextInt(3) == 0 ? -1 : 0); continue; }
                set(c, lx, wy, lz, 0, 0);
                if (dx == 5 && dz == 5) { set(c, lx, wy, lz, 65, 2); continue; }               // ladder, south wall
                if (dy == -4 && dx == 5 && dz == 4) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
                else if (dy == -4 && dx == 4 && dz == 3) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                else if (dy == -4 && dx == 6 && dz == 5) { set(c, lx, wy, lz, 47, 0); }
                else if (dy == -4 && r.nextInt(6) == 0) { set(c, lx, wy - 1, lz, 159, 14); }   // it did not all wash out
                else if (r.nextInt(9) == 0) { set(c, lx, wy, lz, 30, 0); }
                continue;
            }
            if (dy == -1) {
                // Floor, with the way down where the rug used to be: the ladder comes up through it.
                if (cellar && dx == 5 && dz == 5) { set(c, lx, wy, lz, 65, 2); continue; }
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
                // A plank door standing open under a log lintel (it was a bare three-high slot).
                if (door) { set(c, lx, wy, lz, dy == 2 ? 17 : 64, dy == 0 ? 5 : dy == 1 ? 8 : 0); continue; }
                if (window) { set(c, lx, wy, lz, 102, 0); continue; }
                set(c, lx, wy, lz, 17, 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // Somebody lived here: a kitchen corner by the door, a table, the bed, a bookcase on the west
            // wall (one shelf hung at head height in the middle of the room), a lamp, and the rug pushed off
            // the hatch with the stain it was covering.
            if (dy == 0 && dx == 5 && dz == 5) { set(c, lx, wy, lz, 96, 4); continue; }        // the hatch lid, open
            if (dy == 0 && dz == 1 && dx >= 2 && dx <= 4) { set(c, lx, wy, lz, dx == 2 ? 61 : dx == 3 ? 58 : 118, dx == 2 ? 3 : 0); continue; }
            if (dy == 0 && dx == 8 && dz == 6) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); continue; }
            if (dy == 0 && dz == 7 && (dx == 8 || dx == 9)) { set(c, lx, wy, lz, 26, dx == 9 ? 11 : 3); continue; }
            if (dy <= 1 && dx == 1 && (dz == 5 || dz == 6)) { set(c, lx, wy, lz, 47, 0); continue; }
            if (dy == 0 && dx == 3 && dz == 4) { set(c, lx, wy, lz, 85, 0); continue; }
            if (dy == 1 && dx == 3 && dz == 4) { set(c, lx, wy, lz, 72, 0); continue; }
            if (dy == 0 && dx == 2 && dz == 4) { set(c, lx, wy, lz, 53, 1); continue; }
            if (dy == 2 && dx == 4 && dz == 1) { set(c, lx, wy, lz, 50, 3); continue; }
            if (dy == 0 && dz == 6 && dx >= 4 && dx <= 6) { set(c, lx, wy, lz, 171, 12); continue; }
            if (dy == 0 && ((dx == 4 && dz == 4) || (dx == 6 && dz == 5))) { set(c, lx, wy, lz, 171, 14); continue; }
            if (dy == 4 && r.nextInt(20) == 0) { set(c, lx, wy, lz, 30, 0); }                  // webs under the roof
        }
        // A plank step at floor level outside the door: the ground cut back round the cabin still stands a
        // course over the cabin floor, and the door (the old slot was three high) needs its sill clear.
        for (int dx = 4; dx <= 6; dx++) {
            put(c, a, dx, -1, -1, 5, 0);
            for (int dy = 0; dy <= 2; dy++) put(c, a, dx, dy, -1, 0, 0);
        }
    }

    // -- Kindled Shrine (surface) -----------------------------------------------

    /**
     * A broken chapel with a fire at the middle of it that somebody keeps feeding. Ash out to
     * the walls, a blade stood upright in the coals, and the dead waiting where the roof was.
     */
    private static void shrine(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 11, 11);
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
                int ash = r.nextInt(5) == 0 ? 159 : 4, black = r.nextInt(5) == 0 ? 15 : 0;   // same draws
                // Grey and black ash on the cobble (the roll gave white terracotta; audit 2026-09-23).
                set(c, lx, wy, lz, ash, ash == 159 ? black == 15 ? 15 : 8 : 0);
                continue;
            }
            if (wall) {
                /* Ruined: the higher it goes the less of it is left standing. Structure audit 2026-09-23: the
                 * decay was rolled block by block, which riddled the base with holes and left stones hanging
                 * over them; now each column of the wall keeps a height of its own (the corners stand higher,
                 * as the buttresses would), a doorway keeps its lintel only while both jambs still stand to
                 * carry it, and what fell lies at the foot of the wall inside. Same draws as before. */
                boolean gone = r.nextInt(8) < dy;
                boolean arch = dy <= 3 && (dx == 5 || dz == 5);
                if (arch) { set(c, lx, wy, lz, 0, 0); continue; }
                int stone = gone ? 0 : r.nextInt(3) == 0 ? 48 : 4;            // same draws as before
                int stands = shrineWall(a, dx, dz);
                if (dx == 5 || dz == 5) {
                    stands = Math.min(stands, dx == 5 ? Math.min(shrineWall(a, 4, dz), shrineWall(a, 6, dz))
                                                      : Math.min(shrineWall(a, dx, 4), shrineWall(a, dx, 6)));
                }
                set(c, lx, wy, lz, dy <= stands ? (stone != 0 ? stone : 4) : 0, 0);
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // The blade stands in the middle of the coals, on the netherrack (it hung over the fire), hilt up.
            if (dx == 5 && dz == 5 && dy <= 3) { set(c, lx, wy, lz, dy == 3 ? 139 : 101, 0); continue; }
            if (dy == 1 && rad < 1.6) { set(c, lx, wy, lz, 51, 0); continue; }             // it does not go out
            if (dy == 1 && rad >= 1.6 && rad < 2.7 && (dx == 5 || dz == 5)) { set(c, lx, wy, lz, 139, 0); continue; }
            if (dy == 1 && (dx == 2 || dx == 8) && (dz == 2 || dz == 8)) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, Caves.CATHEDRAL); }
            // The chest beside the south doorway, not in it.
            else if (dy == 1 && dx == 4 && dz == 9) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, Caves.CATHEDRAL, true); }
            // Two broken benches facing the fire. (The six lamps that hung in the air are gone: the fire
            // lights the shrine.)
            else if (dy == 1 && dz == 7 && (dx == 3 || dx == 4 || dx == 6 || dx == 7)) set(c, lx, wy, lz, 67, 2);
            else if (dy == 1 && (dx == 1 || dx == 9 || dz == 1 || dz == 9)) {
                // Fallen masonry at the inside foot of a stretch of wall that has come down below the lintels.
                int ox = dx == 1 ? 0 : dx == 9 ? 10 : dx, oz = ox == dx ? (dz == 1 ? 0 : 10) : dz;
                if (ox != 5 && oz != 5 && !((ox == 0 || ox == 10) && (oz == 0 || oz == 10)) && shrineWall(a, ox, oz) < 4)
                    set(c, lx, wy, lz, (dx + dz) % 2 == 0 ? 48 : 44, (dx + dz) % 2 == 0 ? 0 : 3);
            }
        }
    }

    /**
     * How high a column of the shrine's ruined wall still stands (1..7): drawn per column, the corners two
     * higher, and never more than one course over the higher of its neighbours along the wall, so the wall
     * steps down where it fell instead of standing in spikes.
     */
    private static int shrineWall(Anchor a, int dx, int dz) {
        int n = 0;
        if (dz == 0 || dz == 10) n = Math.max(n, Math.max(shrineRaw(a, dx - 1, dz), shrineRaw(a, dx + 1, dz)));
        if (dx == 0 || dx == 10) n = Math.max(n, Math.max(shrineRaw(a, dx, dz - 1), shrineRaw(a, dx, dz + 1)));
        return Math.min(shrineRaw(a, dx, dz), n + 1);
    }

    private static int shrineRaw(Anchor a, int dx, int dz) {
        long h = Terrain.mix(a.seed + dx * 31L + dz * 1009L + 0x57414CL);
        int stands = 7 - (int) ((h & 3) + ((h >>> 2) & 3));
        if ((dx == 0 || dx == 10) && (dz == 0 || dz == 10)) stands += 2;
        return Math.min(7, stands);
    }

    // -- Quarantine Block (surface) ---------------------------------------------

    /**
     * Somebody held this for a while. Bars all the way round, a row of cells with the doors
     * still on them, a tower at the corner, and whatever they were keeping out now inside.
     */
    private static void blockhouse(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 15, 13);
        if (a == null) return;
        Random r = new Random(a.seed);
        /* Structure audit 2026-09-23. The compound had no gate, the blockhouse door opened straight onto a
         * row of bars, the cell doors could never open, and the tower had no door and a lid over a gap. Now:
         * a forced gate in the north fence in line with the door; a corridor inside the door with the cells
         * off it, each behind a barred front with an iron door; a lookout deck on the tower. Which cells hold
         * a keeper or a chest is decided once for the whole room from its own stream, so every chunk the
         * room spans agrees (the cells are drawn as before: a keeper one in two, a chest one in three). A
         * cell with a chest stands open; one that still holds its keeper is shut; an empty one was opened. */
        Random cells = new Random(a.seed ^ 0x43454C4CL);
        boolean[] keeper = new boolean[4], stock = new boolean[4];
        for (int k = 0; k < 4; k++) { keeper[k] = cells.nextInt(2) == 0; stock[k] = cells.nextInt(3) == 0; }
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -2; dy < 10; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean perimeter = dx == 0 || dx == 14 || dz == 0 || dz == 12;
            boolean inner = dx >= 4 && dx <= 12 && dz >= 3 && dz <= 9;
            boolean tower = dx <= 2 && dz <= 2;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            if (dy == 0) {
                if (dx == 2 && dz == 7) { set(c, lx, wy, lz, 167, 0); continue; }           // the decon drain
                set(c, lx, wy, lz, inner ? 98 : 251, inner ? 0 : 8);
                continue;
            }
            if (tower) {
                // A door onto the yard, the ladder on the west wall up through the deck, a lookout on top.
                boolean shell = dx == 0 || dx == 2 || dz == 0 || dz == 2;
                if (dy == 8) { set(c, lx, wy, lz, shell ? 98 : 65, shell ? 0 : 5); continue; }
                if (dy == 9) {
                    // A bar railing along the two outer sides, open to the yard on the inner two, the hatch lid
                    // standing open, and the watch's chest in the corner.
                    if (dx == 2 && dz == 2) { set(c, lx, wy, lz, 54, 4); fill(c.getBlock(lx, wy, lz), r, -1, true); }
                    else if (!shell) set(c, lx, wy, lz, 96, 7);
                    else set(c, lx, wy, lz, dx == 0 || dz == 0 ? 101 : 0, 0);
                    continue;
                }
                if (shell) {
                    boolean door = dx == 2 && dz == 1 && dy <= 2;
                    boolean slit = dy >= 6 && !(dx == 0 && dz == 1);           // the ladder's wall stays brick
                    set(c, lx, wy, lz, door ? 0 : slit ? 101 : 98, 0);
                    continue;
                }
                set(c, lx, wy, lz, 65, 5);
                continue;
            }
            if (perimeter) {
                // Chain fence on a low kerb, sagging in places, and the gate in it forced and left open.
                if (dz == 0 && (dx == 7 || dx == 8)) {
                    if (dy == 1) set(c, lx, wy, lz, 71, 5);
                    else if (dy == 2) set(c, lx, wy, lz, 71, dx == 7 ? 8 : 9);
                    else set(c, lx, wy, lz, 0, 0);
                    continue;
                }
                if (dz == 0 && (dx == 6 || dx == 9)) { set(c, lx, wy, lz, dy <= 4 ? 98 : dy == 5 ? 44 : 0, dy == 5 ? 5 : 0); continue; }
                if (dy == 1) { set(c, lx, wy, lz, 98, 0); continue; }
                if (dy <= 4) { set(c, lx, wy, lz, r.nextInt(11) == 0 ? 0 : 101, 0); continue; }
                set(c, lx, wy, lz, 0, 0);
                continue;
            }
            if (inner) {
                boolean shell = dx == 4 || dx == 12 || dz == 3 || dz == 9;
                if (dy >= 6) { set(c, lx, wy, lz, dy == 6 ? 44 : 0, dy == 6 ? 5 : 0); continue; }
                if (shell) {
                    boolean gate = dy <= 2 && dz == 3 && dx == 8;
                    set(c, lx, wy, lz, gate ? 0 : 98, gate ? 0 : r.nextInt(6) == 0 ? 2 : 0);
                    continue;
                }
                int k = (dx - 5) / 2;
                boolean cellCol = dx % 2 == 1;
                if (dz == 5 && dy <= 3) {                                   // the barred fronts
                    if (cellCol && dy <= 2) {
                        boolean open = stock[k] || !keeper[k];
                        set(c, lx, wy, lz, 71, dy == 2 ? 8 : open ? 5 : 1);   // upper half on top
                    } else set(c, lx, wy, lz, 101, 0);
                    continue;
                }
                if (!cellCol && dz >= 6 && dy <= 3) { set(c, lx, wy, lz, 101, 0); continue; }   // between cells
                set(c, lx, wy, lz, 0, 0);
                if (dy == 1 && cellCol && dz == 7 && keeper[k]) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); }
                else if (dy == 1 && cellCol && dz == 8 && stock[k]) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); }
                else if (dy == 5 && dz == 4 && dx == 8) set(c, lx, wy, lz, 89, 0);          // one lamp, in the corridor
                continue;
            }
            set(c, lx, wy, lz, 0, 0);
            // The yard: a triage bay under an awning along the west fence (cots, the basin by the drain), and
            // the guard's desk by the gate, with the notice on the blockhouse wall.
            if (dx >= 1 && dx <= 3 && dz >= 4 && dz <= 10) {
                if (dy == 3) set(c, lx, wy, lz, 35, 0);
                else if (dx == 3 && (dz == 4 || dz == 10) && dy <= 2) set(c, lx, wy, lz, 85, 0);
                else if (dy == 1 && dx == 1 && (dz == 5 || dz == 6 || dz == 8 || dz == 9)) set(c, lx, wy, lz, 26, (dz == 5 || dz == 8) ? 10 : 2);
                else if (dy == 1 && dx == 3 && dz == 7) set(c, lx, wy, lz, 118, 0);
                continue;
            }
            if (dy == 1 && dz == 1 && (dx == 10 || dx == 11)) set(c, lx, wy, lz, 44, 13);
            else if (dy == 2 && dz == 1 && dx == 10) set(c, lx, wy, lz, 69, 5);
            else if (dy == 2 && dz == 2 && dx == 7) {
                set(c, lx, wy, lz, 68, 2);
                sign(c.getBlock(lx, wy, lz), "QUARANTINE", "BLOCK", "AUTHORISED", "PERSONNEL ONLY");
            }
        }
    }

    // -- The Long Road (surface) ------------------------------------------------

    /**
     * A stretch of road with nothing coming. Grey ash over cracked tarmac, a burnt shell
     * pushed onto the verge, a loaded cart somebody gave up on, and a lean-to off the hard
     * shoulder with what is left of their supplies.
     */
    private static void highway(World w, Chunk c, Terrain t, int cell, long salt) {
        Anchor a = surfaceAnchor(t, c.getX(), c.getZ(), cell, salt, 15, 9);
        if (a == null) return;
        Random r = new Random(a.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 9; dz++) for (int dy = -2; dy < 6; dy++) {
            int wx = a.x + dx, wy = a.y + dy, wz = a.z + dz;
            int lx = wx - c.getX() * 16, lz = wz - c.getZ() * 16;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || wy < 1 || wy > 250) continue;
            boolean road = dz >= 2 && dz <= 5;
            boolean shelter = dx >= 10 && dx <= 13 && dz >= 6 && dz <= 8;
            if (dy < 0) { set(c, lx, wy, lz, 1, 0); continue; }
            // Structure audit 2026-09-23: the road frays into the ground at both ends (it stopped in a
            // straight cut), the burnt shell is charred terracotta (it matched the tarmac), the cart is a
            // handcart, the dead trees are whole trunks, and the verge spawner lives in a storm drain.
            int wear = (dx * 7 + dz * 13 + (int) (a.seed & 7)) & 7;
            int fray = dx == 0 || dx == 14 ? 2 : dx == 1 || dx == 13 ? 4 : 0;
            if (dy == 0) {
                if (road) {
                    // Tarmac, broken up, with the centre line mostly gone.
                    boolean frayed = fray > 0 && wear % fray == 0;
                    if (dz == 3 && dx % 3 != 2) { set(c, lx, wy, lz, frayed ? 13 : 251, 0); continue; }
                    boolean grit = r.nextInt(6) == 0;
                    if (frayed) set(c, lx, wy, lz, wear < 4 ? 3 : 13, wear < 4 ? 1 : 0);
                    else set(c, lx, wy, lz, grit ? 13 : 251, grit ? 0 : 15);
                    continue;
                }
                boolean grit = r.nextInt(3) == 0;
                if (dx == 2 && dz == 7) { set(c, lx, wy, lz, 52, 0); spawner(c.getBlock(lx, wy, lz), r, -1); continue; }
                boolean drain = (dz == 7 && (dx == 1 || dx == 3)) || (dx == 2 && (dz == 6 || dz == 8));
                set(c, lx, wy, lz, grit || drain ? 13 : 251, grit || drain ? 0 : 8);         // ash on the verge
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
            if (dy == 1 && dx == 2 && dz == 7) { set(c, lx, wy, lz, 167, 0); continue; }     // the drain grate
            // The burnt-out shell, half on the verge.
            boolean car = dx >= 3 && dx <= 6 && dz >= 5 && dz <= 6;
            if (car && dy == 1) { set(c, lx, wy, lz, 159, 15); continue; }     // black terracotta, was coal block
            if (car && dy == 2 && dx >= 4 && dx <= 5) { set(c, lx, wy, lz, 101, 0); continue; }
            // A handcart, still loaded, pointing the way they were walking: wheels either side, the shaft
            // ahead, a tail board behind.
            if (dy == 1 && dx == 9 && dz == 4) { set(c, lx, wy, lz, 54, 2); fill(c.getBlock(lx, wy, lz), r, -1, false); continue; }
            if (dy == 1 && dx == 9 && (dz == 3 || dz == 5)) { set(c, lx, wy, lz, 96, dz == 3 ? 4 : 5); continue; }
            if (dy == 1 && dx == 10 && dz == 4) { set(c, lx, wy, lz, 85, 0); continue; }
            if (dy == 1 && dx == 8 && dz == 4) { set(c, lx, wy, lz, 126, 0); continue; }
            // Dead standing timber along the verge: one whole trunk a tree, three to five high with a bare
            // limb at the top, and sometimes the stump of a second. Same draws as before.
            boolean treeRoll = dz <= 1 && dy <= 3 && dx % 5 == 2 && r.nextInt(3) != 0;
            if (dz <= 1 && dx % 5 == 2) {
                int top = 3 + (wear % 3);
                if (dz == 0 && dy <= top) {
                    set(c, lx, wy, lz, 17, 0);
                    if (dy == top) set(c, lx - 1, wy, lz, 17, 4);
                    continue;
                }
                if (dz == 1 && dy == 1 && treeRoll) { set(c, lx, wy, lz, 17, 0); continue; }
            }
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
    /** 3.25.0 (StructureRates): a third lattice for the doubled rooms 0-7, a second for the single rooms 8-13 --
     * half as many rooms again, the spacing trimmed by the share each loses to everything older (rates probe,
     * SA/work/r-rates). Separate arrays: D_CELL and D_SALT_A/B keep their shape (the importer reads them). */
    private static final int[] D_CELL_C = { 22, 29, 26, 30, 23, 33, 36, 27, 44, 41, 34, 37, 44, 42 };
    private static final long[] D_SALT_C = { 0x5741524402L, 0x424F4E4502L, 0x4349535402L, 0x5641554C5402L, 0x574152524E02L, 0x5350495247L, 0x434841504EL, 0x43484B5056L, 0x53414E4156L, 0x54524941544EL, 0x4341424950L, 0x5348524950L, 0x424C4F434DL, 0x524F414402L };
    /** 3.28.0 (the second 1.5x, StructureRates): lattice D for all fourteen rooms, half the 3.27 count again after
     * everything older has its ground (rates probe, tests/java/chat/jaspr/biomes/StructureRatesProbe.java). */
    private static final int[] D_CELL_D = { 18, 23, 21, 24, 18, 26, 28, 21, 34, 31, 27, 30, 35, 33 };
    private static final long[] D_SALT_D = { 0x5741524403L, 0x424F4E4503L, 0x4349535403L, 0x5641554C5403L, 0x574152524E03L, 0x5350495248L, 0x434841504FL, 0x43484B5057L, 0x53414E4157L, 0x54524941544FL, 0x4341424951L, 0x5348524951L, 0x424C4F434EL, 0x524F414403L };
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
     *
     * Recognition skips the admission test (3.23 structure audit): a room laid before its
     * ground was ruled out -- it is still standing in every chunk generated earlier -- is
     * still named, and its chests are still stocked on first open.
     */
    public static String locate(Terrain t, int wx, int wy, int wz) {
        int cx = wx >> 4, cz = wz >> 4;
        // 3.28.0 lattice D, named only where a room was really admitted, exactly as lattice C below: it keeps two
        // blocks clear of every admitted room of the older lattices, so no position named before changes name.
        for (int i = 0; i < D_NAME.length; i++) {
            Anchor a = D_SURFACE[i]
                ? surfaceAnchor(t, cx, cz, D_CELL_D[i], D_SALT_D[i], D_SX[i], D_SZ[i], true)
                : anchor(t, cx, cz, D_CELL_D[i], D_SALT_D[i], D_SX[i], D_SZ[i], true);
            if (a == null) continue;
            if (wx < a.x || wx >= a.x + D_SX[i]) continue;
            if (wz < a.z || wz >= a.z + D_SZ[i]) continue;
            if (wy < a.y - 2 || wy > a.y + D_SY[i] + 2) continue;
            return D_NAME[i] + "\u0000" + D_METHOD[i] + "\u0000" + a.x + "\u0000" + a.y + "\u0000" + a.z;
        }
        // 3.25.0 lattice: named only where a room was really admitted. Such a room keeps two blocks clear of
        // every admitted older room at any height, so every position named before is named as before.
        for (int i = 0; i < D_NAME.length; i++) {
            Anchor a = D_SURFACE[i]
                ? surfaceAnchor(t, cx, cz, D_CELL_C[i], D_SALT_C[i], D_SX[i], D_SZ[i], true)
                : anchor(t, cx, cz, D_CELL_C[i], D_SALT_C[i], D_SX[i], D_SZ[i], true);
            if (a == null) continue;
            if (wx < a.x || wx >= a.x + D_SX[i]) continue;
            if (wz < a.z || wz >= a.z + D_SZ[i]) continue;
            if (wy < a.y - 2 || wy > a.y + D_SY[i] + 2) continue;
            return D_NAME[i] + "\u0000" + D_METHOD[i] + "\u0000" + a.x + "\u0000" + a.y + "\u0000" + a.z;
        }
        for (int i = 0; i < D_NAME.length; i++) {
            for (int lat = 0; lat < 2; lat++) {
                long salt = lat == 0 ? D_SALT_A[i] : D_SALT_B[i];
                if (salt == 0L) continue;
                Anchor a = D_SURFACE[i]
                    ? surfaceAnchor(t, cx, cz, D_CELL[i], salt, D_SX[i], D_SZ[i], false)
                    : anchor(t, cx, cz, D_CELL[i], salt, D_SX[i], D_SZ[i], false);
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
        return anchor(t, cx, cz, cell, salt, sizeX, sizeZ, true);
    }

    /** admit=false is recognition (locate): the anchor as the lattice gives it, before the admission test. */
    private static Anchor anchor(Terrain t, int cx, int cz, int cell, long salt, int sizeX, int sizeZ, boolean admit) {
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
                if (admit && taken(t, x, z, sizeX, sizeZ, y + tall(salt), salt)) continue;
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
        return surfaceAnchor(t, cx, cz, cell, salt, sizeX, sizeZ, true);
    }

    /** admit=false is recognition (locate), as for anchor(). */
    private static Anchor surfaceAnchor(Terrain t, int cx, int cz, int cell, long salt, int sizeX, int sizeZ, boolean admit) {
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
                if (admit && taken(t, x, z, sizeX, sizeZ, lo + 1 + tall(salt), salt)) continue;
                return new Anchor(x, lo + 1, z, seed);
            }
        }
        return null;
    }

    /*
     * Who gets the ground when two kinds of structure want it (3.23 structure audit).
     *
     * Three placement systems used to run blind to one another, and whichever wrote last won:
     * the expedition catalogue is stamped by the chunk generator, then the populator lays the
     * vanilla rooms, the two older room lattices, the register set pieces and last the six newer
     * rooms. Each one took chunks out of whatever it landed on -- a ward through a Hive, a field
     * of pods keeping half a checkpoint, a Hallowed Reach through somebody's catalogue abbey.
     *
     * The order of precedence is now: register set pieces (ranked among themselves by
     * Megaliths.claimed, and never moved by any of this), then catalogue sites (not admitted
     * where a set piece stands), then the lattice rooms here (not admitted where either stands),
     * then the vanilla rooms (not built where any of those stands). It is decided at admission
     * only. locate(), Megaliths.located() and StructurePlanner.identify() still recognise
     * anything the old rules put down, so ground generated before this keeps its names and its
     * loot. Every test below is a pure function of the seed and the room's own lattice cell,
     * so each chunk a room spans reaches the same answer.
     */

    /** True when a register set piece or an admitted catalogue site owns this box (+2 cordon) up to top. */
    private static boolean taken(Terrain t, int x, int z, int sizeX, int sizeZ, int top) {
        return Megaliths.occupied(t, x, z, sizeX, sizeZ, top) || catalogued(t, x, z, sizeX, sizeZ, top, 0);
    }

    /**
     * taken() for a room on the given lattice. The older lattices (A, B) ask exactly what they always asked.
     * A 3.25.0 room (lattice C, StructureRates) also yields to the new set pieces and catalogue sites, to every
     * admitted older room within two blocks at any height (they were placed without knowing about it), to the
     * sanctuary halos, and to chunks that predate 3.25.0.
     */
    private static boolean taken(Terrain t, int x, int z, int sizeX, int sizeZ, int top, long salt) {
        if (latticeD(salt) >= 0) return takenD(t, x, z, sizeX, sizeZ, top, latticeD(salt));
        if (!fresh(salt)) return taken(t, x, z, sizeX, sizeZ, top);
        return !StructureRates.permits(t.seed, x, z, sizeX, sizeZ)
            || StructureRates.nearSanctuary(t, x, z, sizeX, sizeZ)
            || Megaliths.occupiedAll(t, x, z, sizeX, sizeZ, top)
            || catalogued(t, x, z, sizeX, sizeZ, top, 1)   // tiers 0-1: never a 3.28.0 site (StructureRates)
            || oldRoomNear(t, x, z, sizeX, sizeZ)
            || newRoomNear(t, x, z, sizeX, sizeZ, freshIndex(salt));
    }

    /**
     * taken() for a lattice-D room (3.28.0, the second 1.5x): only on ground new in 3.28.0 (the v2 boundary), clear of
     * every sanctuary halo (tier 2 included), every set piece of every layer, every catalogue site of every tier,
     * every admitted lattice A/B/C room within two blocks at any height, and every lower-indexed lattice-D room.
     */
    private static boolean takenD(Terrain t, int x, int z, int sizeX, int sizeZ, int top, int index) {
        return !StructureRates.permits2(t.seed, x, z, sizeX, sizeZ)
            || StructureRates.nearSanctuaryAll(t, x, z, sizeX, sizeZ)
            || Megaliths.occupiedAll3(t, x, z, sizeX, sizeZ, top)
            || catalogued(t, x, z, sizeX, sizeZ, top, 2)
            || roomNearABC(t, x, z, sizeX, sizeZ)
            || roomNearD(t, x, z, sizeX, sizeZ, index);
    }

    /** The lattice-D index of this salt, or -1. */
    private static int latticeD(long salt) {
        for (int i = 0; i < D_SALT_D.length; i++) if (D_SALT_D[i] == salt) return i;
        return -1;
    }

    /** True when an admitted room of lattice A, B or C stands within two blocks of this box, at any height:
     * what every 3.28.0 (tier-2) site asks. */
    static boolean roomNearABC(Terrain t, int x, int z, int sizeX, int sizeZ) {
        return oldRoomNear(t, x, z, sizeX, sizeZ) || newRoomNear(t, x, z, sizeX, sizeZ, D_NAME.length);
    }

    /** newRoomNear() among the lattice-D rooms: a lower index outranks. */
    private static boolean roomNearD(Terrain t, int x, int z, int sizeX, int sizeZ, int below) {
        for (int i = 0; i < below; i++) {
            int cell = D_CELL_D[i];
            long salt = D_SALT_D[i];
            int mx = (int) Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, (long) cell);
            int mz = (int) Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, (long) cell);
            int a0 = Math.floorDiv(x - D_SX[i] - 4, 16), a1 = Math.floorDiv(x + sizeX + 1, 16);
            int b0 = Math.floorDiv(z - D_SZ[i] - 4, 16), b1 = Math.floorDiv(z + sizeZ + 1, 16);
            for (int acx = a0 + Math.floorMod(mx - a0, cell); acx <= a1; acx += cell) {
                for (int acz = b0 + Math.floorMod(mz - b0, cell); acz <= b1; acz += cell) {
                    int ax = acx * 16 + 2, az = acz * 16 + 2;
                    if (ax + D_SX[i] + 2 <= x || x + sizeX + 2 <= ax) continue;
                    if (az + D_SZ[i] + 2 <= z || z + sizeZ + 2 <= az) continue;
                    Anchor a = D_SURFACE[i]
                        ? surfaceAnchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i])
                        : anchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i]);
                    if (a != null && a.x == ax && a.z == az) return true;
                }
            }
        }
        return false;
    }

    private static int freshIndex(long salt) {
        for (int i = 0; i < D_SALT_C.length; i++) if (D_SALT_C[i] == salt) return i;
        return 0;
    }

    /**
     * True when a 3.25.0 room of a lower index is admitted within two blocks of this box, at any height: the new
     * rooms never overlap one another (the index order decides, and a room only ever asks lower ones).
     */
    private static boolean newRoomNear(Terrain t, int x, int z, int sizeX, int sizeZ, int below) {
        for (int i = 0; i < below; i++) {
            int cell = D_CELL_C[i];
            long salt = D_SALT_C[i];
            int mx = (int) Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, (long) cell);
            int mz = (int) Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, (long) cell);
            int a0 = Math.floorDiv(x - D_SX[i] - 4, 16), a1 = Math.floorDiv(x + sizeX + 1, 16);
            int b0 = Math.floorDiv(z - D_SZ[i] - 4, 16), b1 = Math.floorDiv(z + sizeZ + 1, 16);
            for (int acx = a0 + Math.floorMod(mx - a0, cell); acx <= a1; acx += cell) {
                for (int acz = b0 + Math.floorMod(mz - b0, cell); acz <= b1; acz += cell) {
                    int ax = acx * 16 + 2, az = acz * 16 + 2;
                    if (ax + D_SX[i] + 2 <= x || x + sizeX + 2 <= ax) continue;
                    if (az + D_SZ[i] + 2 <= z || z + sizeZ + 2 <= az) continue;
                    Anchor a = D_SURFACE[i]
                        ? surfaceAnchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i])
                        : anchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i]);
                    if (a != null && a.x == ax && a.z == az) return true;
                }
            }
        }
        return false;
    }

    /** True for a 3.25.0 lattice (D_SALT_C). */
    private static boolean fresh(long salt) {
        for (long s : D_SALT_C) if (s == salt) return true;
        return false;
    }

    /** The lattice spacing that goes with this salt: the 3.25.0 lattice of rooms 8-13 is wider. */
    private static int cellFor(int i, long salt) { return salt == D_SALT_D[i] ? D_CELL_D[i] : salt == D_SALT_C[i] ? D_CELL_C[i] : D_CELL[i]; }

    /**
     * True when an older (lattice A or B) room admitted for generation stands within two blocks of this box,
     * at any height. Everything added in 3.25.0 (set pieces, sanctuaries, catalogue sites, rooms) asks this,
     * because the older rooms were admitted without knowing about it and are still built where they were.
     */
    static boolean oldRoomNear(Terrain t, int x, int z, int sizeX, int sizeZ) {
        for (int i = 0; i < D_NAME.length; i++) {
            for (int lat = 0; lat < 2; lat++) {
                long salt = lat == 0 ? D_SALT_A[i] : D_SALT_B[i];
                if (salt == 0L) continue;
                int cell = D_CELL[i];
                int mx = (int) Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, (long) cell);
                int mz = (int) Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, (long) cell);
                int a0 = Math.floorDiv(x - D_SX[i] - 4, 16), a1 = Math.floorDiv(x + sizeX + 1, 16);
                int b0 = Math.floorDiv(z - D_SZ[i] - 4, 16), b1 = Math.floorDiv(z + sizeZ + 1, 16);
                for (int acx = a0 + Math.floorMod(mx - a0, cell); acx <= a1; acx += cell) {
                    for (int acz = b0 + Math.floorMod(mz - b0, cell); acz <= b1; acz += cell) {
                        int ax = acx * 16 + 2, az = acz * 16 + 2;
                        if (ax + D_SX[i] + 2 <= x || x + sizeX + 2 <= ax) continue;
                        if (az + D_SZ[i] + 2 <= z || z + sizeZ + 2 <= az) continue;
                        Anchor a = D_SURFACE[i]
                            ? surfaceAnchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i])
                            : anchor(t, acx, acz, cell, salt, D_SX[i], D_SZ[i]);
                        if (a != null && a.x == ax && a.z == az) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when a catalogue site admitted for generation (StructurePlanner.sites) builds within two
     * blocks of this box -- its rooms with their margin, or its approach as far as it runs
     * (Site.envelope) -- unless the box lies wholly beneath the site: sixteen blocks under its
     * lowest room floor clears the piers it stands on, so a buried room far below a surface site
     * is not a collision.
     */
    private static boolean catalogued(Terrain t, int x, int z, int sizeX, int sizeZ, int top, int maxTier) {
        for (int cx = (x - 2) >> 4; cx <= (x + sizeX + 1) >> 4; cx++) {
            for (int cz = (z - 2) >> 4; cz <= (z + sizeZ + 1) >> 4; cz++) {
                // Older rooms ask only the tier-0 (3.24) sites, and never plan a tier-1 cell to do it; the 3.25.0 rooms
                // (lattice C) only tier 0-1, never planning a 3.28.0 (tier-2) cell; lattice D and the vanilla rooms all.
                for (StructurePlanner.Site s : maxTier >= 2 ? StructurePlanner.sites(t.seed, cx, cz)
                        : maxTier == 1 ? StructurePlanner.sitesTier01(t.seed, cx, cz) : StructurePlanner.sitesTier0(t.seed, cx, cz)) {
                    int floor = s.y;
                    for (StructurePlanner.Room room : s.rooms) floor = Math.min(floor, room.floor);
                    if (top < floor - 16) continue;
                    for (int[] e : s.envelope()) {
                        if (e[0] + e[2] + 2 <= x || x + sizeX + 2 <= e[0]) continue;
                        if (e[1] + e[3] + 2 <= z || z + sizeZ + 2 <= e[1]) continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when an admitted lattice room overlaps this box (+2 cordon) between bottom and top. */
    private static boolean roomed(Terrain t, int x, int z, int sizeX, int sizeZ, int bottom, int top) {
        for (int cx = (x - 2) >> 4; cx <= (x + sizeX + 1) >> 4; cx++) {
            for (int cz = (z - 2) >> 4; cz <= (z + sizeZ + 1) >> 4; cz++) {
                for (int i = 0; i < D_NAME.length; i++) {
                    for (int lat = 0; lat < 4; lat++) {
                        long salt = lat == 0 ? D_SALT_A[i] : lat == 1 ? D_SALT_B[i] : lat == 2 ? D_SALT_C[i] : D_SALT_D[i];
                        if (salt == 0L) continue;
                        Anchor a = D_SURFACE[i]
                            ? surfaceAnchor(t, cx, cz, cellFor(i, salt), salt, D_SX[i], D_SZ[i])
                            : anchor(t, cx, cz, cellFor(i, salt), salt, D_SX[i], D_SZ[i]);
                        if (a == null) continue;
                        if (a.x + D_SX[i] + 2 <= x || x + sizeX + 2 <= a.x) continue;
                        if (a.z + D_SZ[i] + 2 <= z || z + sizeZ + 2 <= a.z) continue;
                        if (top < a.y - 6 || bottom > a.y + D_SY[i]) continue;   // footings go six down at most
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** How far the room on this lattice stands above its anchor (D_SY), found by its salt. */
    private static int tall(long salt) {
        for (int i = 0; i < D_SY.length; i++) if (D_SALT_A[i] == salt || D_SALT_B[i] == salt || D_SALT_C[i] == salt || D_SALT_D[i] == salt) return D_SY[i];
        return 20;
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

    // -- ways in and ground (structure audit 2026-09-23) --------------------------

    /*
     * The way into each buried room. None of the five was ever given one: the rooms are closed
     * boxes sunk in rock, so the dry ledge "so the room can actually be looted", the cage "open on
     * one side" and the ward's aisle all led nowhere. Each now has the same thing, a ladder recess
     * cut into its own wall and carried up a lined shaft to daylight (Megaliths.shaft, as the set
     * pieces' risers): the ward at the west end of its aisle, the ossuary on the north axis of the
     * ring, the cistern at the west end of the ledge, the vault on the cage axis facing the gate, the
     * warren at the west end of its long axis. The recess column and everything round it lie in one
     * chunk for every room, so nothing is read from a neighbour. A shaft that would come up through
     * a set piece or a catalogue site standing over the room is not dug.
     *
     * Row: room-relative column dx, dz; the side it hangs on (Megaliths.SIDE); lining id, data.
     */
    private static final int[][] ENTRY = {
        {0, 4, 2, 98, 0},     // The Ward: stone brick
        {5, 0, 0, 98, 1},     // The Ossuary: mossy stone brick
        {0, 1, 2, 98, 0},     // The Flooded Cistern
        {5, 10, 1, 98, 0},    // The Vault
        {1, 5, 2, 4, 0},      // The Warren: cobble, a dug burrow
    };

    private static void entrance(Chunk c, Terrain t, int i, long salt) {
        Anchor a = anchor(t, c.getX(), c.getZ(), cellFor(i, salt), salt, D_SX[i], D_SZ[i]);
        if (a == null) return;
        int wx = a.x + ENTRY[i][0], wz = a.z + ENTRY[i][1];
        if (taken(t, wx - 1, wz - 1, 3, 3, 255, salt)) return;
        Written w = WRITTEN.get();
        if (CaptureHook.sink != null) w.as = "Dungeons." + D_METHOD[i];      // test harness only: the room's own work
        Megaliths.shaft(c, t, wx, wz, a.y + 1, a.y + D_SY[i] - 1, ENTRY[i][2], ENTRY[i][3], ENTRY[i][4]);
        w.as = null;
    }

    /*
     * The ground round a surface room, cut back before it is built. surfaceAnchor() puts the floor
     * on the lowest of four corner samples, so on a slope the uphill terrain stood in the room and
     * buried the doorways on that side; the builders only write their own box, so a tree standing in
     * it lost its trunk and left its crown hanging over the roof. Now everything standing over the
     * footprint from the floor up is cleared, the three-block margin round it is cut to steps (level
     * with the floor, then one up, then two up) so every door on every side opens onto ground and the
     * three blocks of relief surfaceAnchor() allows can be walked back up, and crowns left orphaned
     * beyond that go. Standing water outside the footprint is left alone. The first two rings are a
     * cordon no other structure is admitted into (taken()); the third is only cut where it is bare
     * ground or plants no builder has written. Each chunk writes only its own part.
     */
    private static void clearing(Chunk c, Terrain t, int i, long salt) {
        // The cut runs up to six blocks past the footprint, so a chunk the room itself never reaches can
        // still hold part of its margin: the room is looked up from the neighbouring chunks as well
        // (surfaceAnchor reads only the terrain function, so every chunk gets the same answer).
        long done = Long.MIN_VALUE;
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            Anchor a = surfaceAnchor(t, c.getX() + ox, c.getZ() + oz, cellFor(i, salt), salt, D_SX[i], D_SZ[i]);
            if (a == null || (((long) a.x << 32) ^ (a.z & 0xffffffffL)) == done) continue;
            done = ((long) a.x << 32) ^ (a.z & 0xffffffffL);      // one lattice point per salt reaches here
            clearing(c, a, i);
        }
    }

    private static void clearing(Chunk c, Anchor a, int i) {
        Written w = WRITTEN.get();
        if (CaptureHook.sink != null) w.as = "Dungeons." + D_METHOD[i];      // test harness only: the room's own work
        int bx = c.getX() * 16, bz = c.getZ() * 16, top = Math.min(250, a.y + 30);
        for (int pass = 0; pass < 2; pass++)
        for (int wx = Math.max(a.x - 6, bx); wx <= Math.min(a.x + D_SX[i] + 5, bx + 15); wx++) {
            for (int wz = Math.max(a.z - 6, bz); wz <= Math.min(a.z + D_SZ[i] + 5, bz + 15); wz++) {
                int ring = Math.max(Math.max(a.x - wx, wx - (a.x + D_SX[i] - 1)), Math.max(a.z - wz, wz - (a.z + D_SZ[i] - 1)));
                if ((ring <= 3) != (pass == 0)) continue;
                int lx = wx - bx, lz = wz - bz;
                boolean grass = false, did = false;
                for (int y = top; y >= a.y + 1 + Math.max(0, ring - 1); y--) {
                    Block b = c.getBlock(lx, y, lz);
                    int id = b.getTypeId();
                    if (id == 0) continue;
                    if (ring > 3) {                            // beyond the cut: only orphaned crowns
                        if ((id == 18 || id == 161) && !Megaliths.rooted(c, wx, y, wz)) set(c, lx, y, lz, 0, 0);
                        continue;
                    }
                    if (ring > 0 && b.isLiquid()) continue;
                    if (ring == 3 && (w.bits.get((y << 8) | (lz << 4) | lx) || !(bare(id) || Megaliths.plant(id)))) continue;
                    if (id == 2) grass = true;
                    set(c, lx, y, lz, 0, 0);
                    did = true;
                }
                int floor = a.y + Math.max(0, ring - 1);
                if (did && grass && c.getBlock(lx, floor, lz).getTypeId() == 3) set(c, lx, floor, lz, 2, 0);
            }
        }
        w.as = null;
    }

    /** Bare ground a clearing may cut outside its cordon: stone, soils, sands, clay, mesa terracotta, snow and ice. */
    private static boolean bare(int id) {
        return id == 1 || id == 2 || id == 3 || id == 12 || id == 13 || id == 24 || id == 78 || id == 79 || id == 80
            || id == 82 || id == 110 || id == 159 || id == 172 || id == 174 || id == 179;
    }

    /*
     * Settling (structure audit 2026-09-23). Every block the builders write in a chunk -- the rooms
     * here, the set pieces, the landmarks -- is recorded, and once they are all down the recorded
     * cells are checked for the faults the audit found over and over, whoever wrote them:
     * - a ladder with nothing behind it is turned to the side of its cell that has a wall; a run
     *   that stops in the air over a floor it could reach down a wall is carried down to it; a run
     *   that stops under one course of built ceiling with a room or a roof over it gets a hatch;
     * - a spawner or a chest standing on nothing is set down on the floor under it (a hole in the
     *   floor under it is patched instead), and one standing on water or lava gets a pier of the
     *   pool's own bed under it; spawner mob and chest contents move with it;
     * - a cobweb touching nothing is removed, a vine is turned to the wall beside it or removed;
     * - loose rubble and snapped logs (at most four blocks) hanging in the air fall to the ground.
     * Only this chunk is read: anything at its edge that might rest on a neighbour is left alone.
     */
    private static final class Written {
        Chunk chunk;
        final java.util.BitSet bits = new java.util.BitSet(1 << 16);
        /* Test harness only (null in production): which builder wrote each cell settle() may change, and
         * the builder the next writes are credited to, so the capture harness books a fix to the
         * structure it belongs to rather than to settle() or to build(). */
        String[] owner;
        String as;

        void begin(Chunk c) {
            chunk = c;
            bits.clear();
            as = null;
            if (CaptureHook.sink == null) owner = null;
            else if (owner == null) owner = new String[1 << 16];
            else java.util.Arrays.fill(owner, null);
        }
    }

    private static final ThreadLocal<Written> WRITTEN = new ThreadLocal<Written>() {
        @Override protected Written initialValue() { return new Written(); }
    };

    private static int at(Chunk c, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) return -1;       // a neighbour chunk: unknown
        if (y < 1 || y > 254) return y < 1 ? 7 : 0;
        return c.getBlock(x, y, z).getTypeId();
    }

    private static boolean solid(Chunk c, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1) return true;
        return y <= 254 && c.getBlock(x, y, z).getType().isSolid();
    }

    /** Something a ladder hangs on (Megaliths.backs), known to be there: a neighbour chunk is not read. */
    private static boolean backed(Chunk c, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) return false;
        return y >= 1 && y <= 254 && Megaliths.backs(c.getBlock(x, y, z));
    }

    /** Something a chest or a spawner can stand on. */
    private static boolean floor(int id) {
        return id > 0 && id != 52 && id != 54 && id != 146 && id != 65 && !stair(id)
            && (Material.getMaterial(id) != null && Material.getMaterial(id).isSolid() || id == 171 || id == 78);
    }

    private static boolean stair(int id) {
        return id == 53 || id == 67 || id == 108 || id == 109 || id == 114 || id == 128 || id == 134 || id == 135
            || id == 136 || id == 156 || id == 163 || id == 164 || id == 180 || id == 203;
    }

    private static boolean liquid(int id) { return id >= 8 && id <= 11; }

    /** Lights and tile entities: never cut into a hatch or used as filler. */
    private static boolean fixture(int id) {
        return id == 89 || id == 169 || id == 124 || id == 91 || id == 138 || id == 52 || id == 54 || id == 146
            || id == 61 || id == 62 || id == 23 || id == 158 || id == 154 || id == 117 || id == 116 || id == 130 || id == 84;
    }

    private static final int[] LADDER_SIDE = {-1, -1, 1, 0, 3, 2};   // ladder data -> Megaliths.SIDE index
    private static final int[][] SIDE4 = {{0, -1, 3}, {0, 1, 2}, {-1, 0, 5}, {1, 0, 4}};

    private static void settle(Chunk c, java.util.BitSet bits) {
        int[] cells = bits.stream().toArray();
        for (int idx : cells) {                                        // ladders, one run at a time from its foot
            int x = idx & 15, z = (idx >> 4) & 15, y = idx >> 8;
            if (at(c, x, y, z) != 65 || at(c, x, y - 1, z) == 65) continue;
            int top = y;
            while (top < 254 && at(c, x, top + 1, z) == 65) top++;
            ladders(c, x, z, y, top, bits);
        }
        for (int idx : cells) {                                        // spawners and chests
            int x = idx & 15, z = (idx >> 4) & 15, y = idx >> 8;
            int id = at(c, x, y, z);
            if (id == 52 || id == 54 || id == 146) standing(c, x, y, z, id);
        }
        for (int k = cells.length - 1; k >= 0; k--) {                   // webs and vines, top down
            int idx = cells[k], x = idx & 15, z = (idx >> 4) & 15, y = idx >> 8;
            int id = at(c, x, y, z);
            if (id == 30) {
                if (!solid(c, x + 1, y, z) && !solid(c, x - 1, y, z) && !solid(c, x, y + 1, z) && !solid(c, x, y - 1, z)
                    && !solid(c, x, y, z + 1) && !solid(c, x, y, z - 1)) fix(c, x, y, z, 0, 0, x, y, z);
            } else if (id == 106) {
                vine(c, x, y, z);
            }
        }
        java.util.BitSet seen = new java.util.BitSet(1 << 16);
        for (int idx : cells) {                                        // loose rubble
            int x = idx & 15, z = (idx >> 4) & 15, y = idx >> 8;
            if (!seen.get(idx) && rubble(at(c, x, y, z))) loose(c, x, y, z, bits, seen);
        }
    }

    private static void ladders(Chunk c, int x, int z, int y0, int y1, java.util.BitSet bits) {
        int data = c.getBlock(x, y0, z).getData(), cur = data >= 2 && data <= 5 ? LADDER_SIDE[data] : 0;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) for (int y = y0; y <= y1; y++) if (backed(c, x + SIDE4[i][0], y, z + SIDE4[i][1])) n[i]++;
        int side = cur;
        for (int i = 0; i < 4; i++) if (n[i] > n[side]) side = i;
        for (int y = y0; y <= y1; y++) {
            int d = c.getBlock(x, y, z).getData(), own = d >= 2 && d <= 5 ? LADDER_SIDE[d] : side;
            if (backed(c, x + SIDE4[own][0], y, z + SIDE4[own][1])) continue;      // this rung hangs already
            // (one hung on the next chunk is only turned when a wall of its own chunk is certain)
            int to = -1;
            if (backed(c, x + SIDE4[side][0], y, z + SIDE4[side][1])) to = side;
            for (int i = 0; i < 4 && to < 0; i++) if (backed(c, x + SIDE4[i][0], y, z + SIDE4[i][1])) to = i;
            if (to >= 0) fix(c, x, y, z, 65, SIDE4[to][2], x, y, z);
        }
        int bx = x + SIDE4[side][0], bz = z + SIDE4[side][1];
        // Foot: down the same wall to the floor, when there is one within reach.
        int foot = y0;
        while (foot > y0 - 8 && at(c, x, foot - 1, z) == 0 && backed(c, bx, foot - 1, bz)) foot--;
        if (foot < y0 && floor(at(c, x, foot - 1, z))) for (int y = foot; y < y0; y++) fix(c, x, y, z, 65, SIDE4[side][2], x, y0, z);
        // Head: one course of built ceiling over the top rung, with a floor or a roof to step out onto.
        int cap = y1 + 1, capId = at(c, x, cap, z);
        if (cap > 252 || !bits.get((cap << 8) | (z << 4) | x) || fixture(capId) || hatch(capId) || !solid(c, x, cap, z)
            || !backed(c, bx, cap, bz)) return;
        int over = at(c, x, cap + 1, z);
        boolean joins = over == 65;
        boolean room = over == 0 && at(c, x, cap + 2, z) == 0 && landing(c, x, cap, z);
        if (joins || room) fix(c, x, cap, z, 65, SIDE4[side][2], x, y1, z);
    }

    /** A trapdoor or a door over the top rung: already the way through, left as built. */
    private static boolean hatch(int id) {
        return id == 96 || id == 167 || id == 64 || id == 71 || (id >= 193 && id <= 197);
    }

    /** A block beside (x, y, z) that can be stood on, with two blocks of air over it. */
    private static boolean landing(Chunk c, int x, int y, int z) {
        for (int[] s : SIDE4) {
            int nx = x + s[0], nz = z + s[1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            if (solid(c, nx, y, nz) && at(c, nx, y + 1, nz) == 0 && at(c, nx, y + 2, nz) == 0) return true;
        }
        return false;
    }

    private static void standing(Chunk c, int x, int y, int z, int id) {
        int below = at(c, x, y - 1, z);
        if (below < 0 || floor(below) || y < 3) return;
        if (liquid(below)) {                                   // on a pool: a pier of the pool's own bed
            int d = y - 1;
            while (d > y - 7 && liquid(at(c, x, d, z))) d--;
            Block bed = c.getBlock(x, d, z);
            if (!floor(bed.getTypeId()) || fixture(bed.getTypeId())) return;
            for (int yy = d + 1; yy < y; yy++) fix(c, x, yy, z, bed.getTypeId(), bed.getData(), x, y, z);
            return;
        }
        if (below != 0) return;
        // A hole in a floor (three sides of it are floor): patch it with that floor.
        int[] best = null;
        int count = 0;
        for (int[] s : SIDE4) {
            int nx = x + s[0], nz = z + s[1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            Block b = c.getBlock(nx, y - 1, nz);
            if (!floor(b.getTypeId()) || fixture(b.getTypeId())) continue;
            count++;
            if (best == null) best = new int[]{b.getTypeId(), b.getData()};
        }
        if (count >= 3) { fix(c, x, y - 1, z, best[0], best[1], x, y, z); return; }
        // Otherwise it is set down on the floor below.
        int d = y - 1;
        while (d > y - 25 && d > 1 && at(c, x, d, z) == 0) d--;
        int f = at(c, x, d, z);
        if (!floor(f) || fixture(f)) return;
        move(c, x, y, z, d + 1);
    }

    /** Moves a spawner (with its mob) or a chest (with its contents, never dropped) down its column. */
    private static void move(Chunk c, int x, int y, int z, int ny) {
        Block from = c.getBlock(x, y, z);
        int id = from.getTypeId(), data = from.getData();
        try {
            if (id == 52) {
                EntityType type = ((CreatureSpawner) from.getState()).getSpawnedType();
                fix(c, x, ny, z, 52, 0, x, y, z);
                fix(c, x, y, z, 0, 0, x, y, z);
                CreatureSpawner s = (CreatureSpawner) c.getBlock(x, ny, z).getState();
                s.setSpawnedType(type);
                s.update(true, false);
            } else {
                Inventory inv = ((Chest) from.getState()).getBlockInventory();
                ItemStack[] items = inv.getContents(), copy = new ItemStack[items.length];
                for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
                inv.clear();                                       // or removing the block would drop them
                fix(c, x, ny, z, id, data, x, y, z);
                fix(c, x, y, z, 0, 0, x, y, z);
                // No update() after filling (see graded()): the block inventory is the live one.
                ((Chest) c.getBlock(x, ny, z).getState()).getBlockInventory().setContents(copy);
            }
        } catch (RuntimeException ignored) { }
    }

    private static void vine(Chunk c, int x, int y, int z) {
        int data = c.getBlock(x, y, z).getData(), keep = 0, could = 0;
        int[][] face = {{1, 0, 1}, {2, -1, 0}, {4, 0, -1}, {8, 1, 0}};     // bit, dx, dz: S, W, N, E
        for (int[] f : face) {
            int nx = x + f[1], nz = z + f[2];
            boolean wall = nx < 0 || nx > 15 || nz < 0 || nz > 15 || Megaliths.backs(c.getBlock(nx, y, nz));
            if (wall) could |= f[0];
            if (wall && (data & f[0]) != 0) keep |= f[0];
        }
        int up = at(c, x, y + 1, z);
        if (keep != 0 || up == 106 || (up > 0 && solid(c, x, y + 1, z))) return;
        if (could != 0) fix(c, x, y, z, 106, could, x, y, z);
        else fix(c, x, y, z, 0, 0, x, y, z);
    }

    /** Loose material the builders scatter: cobble, mossy cobble, cobble monster eggs, gravel, dirt, logs. */
    private static boolean rubble(int id) {
        return id == 4 || id == 48 || id == 97 || id == 13 || id == 3 || id == 17 || id == 162;
    }

    /** A cluster of at most four written rubble blocks touching nothing that stands: it falls. */
    private static void loose(Chunk c, int x0, int y0, int z0, java.util.BitSet bits, java.util.BitSet seen) {
        java.util.ArrayList<int[]> part = new java.util.ArrayList<int[]>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<int[]>();
        // mine: this cluster; seen: every cluster so far. One seen by an earlier call is still standing
        // (a loose one has fallen already), so touching it means resting on it.
        java.util.BitSet mine = new java.util.BitSet();
        queue.add(new int[]{x0, y0, z0});
        seen.set((y0 << 8) | (z0 << 4) | x0);
        mine.set((y0 << 8) | (z0 << 4) | x0);
        boolean rests = false;
        while (!queue.isEmpty() && !rests) {
            int[] p = queue.poll();
            part.add(p);
            if (part.size() > 4 || !rubble(at(c, p[0], p[1], p[2]))) { rests = true; break; }
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 1) { rests = true; break; }
                if (ny > 254 || !c.getBlock(nx, ny, nz).getType().isSolid()) continue;
                int key = (ny << 8) | (nz << 4) | nx;
                if (!bits.get(key)) { rests = true; break; }           // terrain, or another generator's work
                if (seen.get(key) && !mine.get(key)) { rests = true; break; }   // an earlier cluster that stands
                if (!seen.get(key)) { seen.set(key); mine.set(key); queue.add(new int[]{nx, ny, nz}); }
            }
        }
        if (rests) return;
        part.sort((a, b) -> a[1] - b[1]);
        for (int[] p : part) {
            Block b = c.getBlock(p[0], p[1], p[2]);
            int id = b.getTypeId(), data = b.getData();
            int d = p[1] - 1;
            while (d > p[1] - 25 && d > 1 && at(c, p[0], d, p[2]) == 0) d--;
            int f = at(c, p[0], d, p[2]);
            if (floor(f) && !fixture(f) && d + 1 < p[1]) fix(c, p[0], d + 1, p[2], id, data, p[0], p[1], p[2]);
            fix(c, p[0], p[1], p[2], 0, 0, p[0], p[1], p[2]);
        }
    }

    /** A settle() write, credited (test harness only) to the builder that wrote cell (sx, sy, sz). */
    private static void fix(Chunk c, int x, int y, int z, int id, int data, int sx, int sy, int sz) {
        Written w = WRITTEN.get();
        String prev = w.as;
        if (w.owner != null) w.as = w.owner[(sy << 8) | (sz << 4) | sx];
        set(c, x, y, z, id, data);
        w.as = prev;
    }

    /** The blocks settle() may move, turn or remove, whose writers the test harness needs to know. */
    private static boolean tracked(int id) {
        return id == 65 || id == 52 || id == 54 || id == 146 || id == 30 || id == 106 || rubble(id) || id == 48;
    }

    /** Test harness only: the builder on the stack, named exactly as the capture harness names it. */
    private static String owner() {
        String prev = null;
        for (StackTraceElement f : new Throwable().getStackTrace()) {
            String cls = f.getClassName();
            if (!cls.startsWith("chat.jaspr.biomes.")) continue;
            String simple = cls.substring(18);
            int d = simple.indexOf('$');
            if (d >= 0) simple = simple.substring(0, d);
            if (simple.equals("CaptureHook")) continue;
            String m = f.getMethodName();
            if (m.equals("populate") || (simple.equals("Dungeons") && m.equals("build"))) return prev == null ? simple + "." + m : prev;
            if (m.startsWith("lambda$")) {
                m = m.substring(7);
                int e = m.indexOf('$');
                if (e > 0) m = m.substring(0, e);
            }
            prev = simple + "." + m;
        }
        return null;
    }

    // -- fittings ---------------------------------------------------------------

    static void set(Chunk c, int x, int y, int z, int id, int data) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1 || y > 254) return;
        Written w = WRITTEN.get();
        String as = null;
        if (w.chunk == c) {
            int key = (y << 8) | (z << 4) | x;
            w.bits.set(key);                                              // for settle() at the end of build()
            if (w.owner != null) {                                        // test harness only
                as = w.as;
                w.owner[key] = as != null ? as : tracked(data < 0 ? 48 : id) ? owner() : null;
            }
        }
        if (data < 0) {
            c.getBlock(x, y, z).setTypeIdAndData(48, (byte) 0, false);
            if (CaptureHook.sink != null) CaptureHook.chunk(c, x, y, z, 48, 0, as != null ? as : "populate");   // test harness only
            return;
        }
        c.getBlock(x, y, z).setTypeIdAndData(id, (byte) data, false);
        if (CaptureHook.sink != null) CaptureHook.chunk(c, x, y, z, id, data, as != null ? as : "populate");    // test harness only
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
            // Survivor Gear (3.26.0): at most one trinket from the same keyed stream, gear tier 0 for a room's
            // ordinary chests and 1 for its rich ones.
            GearLoot.add(inv, luck, rich ? 1 : 0);
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
        return restock(block, t, caves, rich, -1);
    }

    /** restock() plus, from 3.26.0, at most one Survivor Gear trinket at gearTier (none when gearTier is negative). */
    public static int restock(Block block, Terrain t, Caves caves, boolean rich, int gearTier) {
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
        if (gearTier >= 0 && GearLoot.add(inv, r, gearTier)) placed++;
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
