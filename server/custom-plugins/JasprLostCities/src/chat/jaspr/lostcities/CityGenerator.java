package chat.jaspr.lostcities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Port of terraingen.LostCitiesTerrainGenerator for the DEFAULT landscape: city chunks (buildings with cellars,
 * floors and doors; streets, parks, fountains, fronts, stairs, borders, corridors, ruins, rubble), normal chunks
 * next to a city (flattening towards it, bridges, highways), railways and stations, rail dungeons, explosions with
 * the floating-block pass, and debris. It writes into a char primer; the plugin turns the primer into the chunk.
 */
final class CityGenerator {

    /** A block the populate stage must finish: spawner, chest loot or sapling (local chunk coordinates). */
    static final class Todo {
        final int x, y, z;
        final String condition, part, building;
        Todo(int x, int y, int z, String condition, String part, String building) {
            this.x = x; this.y = y; this.z = z; this.condition = condition; this.part = part; this.building = building;
        }
    }

    static final class Result {
        final List<Todo> spawners = new ArrayList<>();
        final List<Todo> loot = new ArrayList<>();
        final List<Todo> saplings = new ArrayList<>();
        final List<String> features = new ArrayList<>();
        void feature(String f) { features.add(f); }
    }

    private final CityWorld w;
    private final int mainGroundLevel = Profile.GROUNDLEVEL;
    private final Driver driver = new Driver();
    private final FastRand fast = new FastRand();
    private Random rand = new Random();
    private Result result;
    private int genX, genZ;

    static final char airChar = B.AIR;
    static final char hardAirChar = B.COMMAND_BLOCK;
    static final char liquidChar = B.WATER;
    static final char baseChar = B.STONE;
    static final char bedrockChar = B.BEDROCK;
    static final char ironbarsChar = B.IRON_BARS;
    static final char grassChar = B.GRASS;
    static final char glowstoneChar = B.GLOWSTONE;

    private char street, streetBase, street2;
    private int streetBorder;

    private double[] rubbleBuffer = new double[256];
    private double[] leavesBuffer = new double[256];
    private double[] ruinBuffer = new double[256];

    private static final char[] RANDOM_LEAFS = new char[128];
    static {
        int i = 0;
        for (; i < 20; i++) RANDOM_LEAFS[i] = B.LEAVES2;
        for (; i < 40; i++) RANDOM_LEAFS[i] = B.LEAVES3;
        for (; i < RANDOM_LEAFS.length; i++) RANDOM_LEAFS[i] = B.LEAVES;
    }

    CityGenerator(CityWorld w) { this.w = w; }

    private char getRandomLeaf() { return RANDOM_LEAFS[fast.next128()]; }

    private char pal(CompiledPalette p, Character c) {
        if (c == null) return airChar;
        Character r = p.get(c, fast);
        return r == null ? airChar : r;
    }

    // ------------------------------------------------------------------ entry point

    /** Generate chunk (chunkX, chunkZ) into primer. For normal chunks the primer holds the existing terrain. */
    Result generate(int chunkX, int chunkZ, char[] primer) {
        driver.setPrimer(primer);
        result = new Result();
        genX = chunkX;
        genZ = chunkZ;
        fast.reseed(Regions.mix(w.seed ^ (chunkX * 341873128712L + chunkZ * 132897987541L)));
        rand = new Random(chunkX * 341873128712L + chunkZ * 132897987541L ^ w.seed);
        BuildingInfo info = w.info(chunkX, chunkZ);

        Assets.CityStyle cityStyle = info.getCityStyle();
        street = pal(info.getCompiledPalette(), cityStyle.streetBlock);
        streetBase = pal(info.getCompiledPalette(), cityStyle.streetBaseBlock);
        street2 = pal(info.getCompiledPalette(), cityStyle.streetVariantBlock);
        streetBorder = (16 - cityStyle.getStreetWidth()) / 2;

        if (info.isCity) {
            doCityChunk(chunkX, chunkZ, info);
        } else {
            doNormalChunk(chunkX, chunkZ, info);
        }

        Railway.RailChunkInfo railInfo = info.getRailInfo();
        if (railInfo.type != RailChunkType.NONE) generateRailways(info, railInfo);
        generateRailwayDungeons(info);

        rand.setSeed(chunkX * 257017164707L + chunkZ * 101754694003L);
        if (info.getDamageArea().hasExplosions()) {
            breakBlocksForDamage(chunkX, chunkZ, info);
            fixAfterExplosionNew(info, rand);
            result.feature("explosion");
        }
        generateDebris(rand, info);
        return result;
    }

    // ------------------------------------------------------------------ normal chunks

    private void doNormalChunk(int chunkX, int chunkZ, BuildingInfo info) {
        flattenChunkToCityBorder(chunkX, chunkZ);
        generateBridges(info);
        generateHighways(chunkX, chunkZ, info);
    }

    private int getHeightAt00Corner(BuildingInfo info) {
        int h = getHeightForChunk(info);
        h = Math.min(h, getHeightForChunk(info.getXmin()));
        h = Math.min(h, getHeightForChunk(info.getZmin()));
        h = Math.min(h, getHeightForChunk(w.info(info.chunkX - 1, info.chunkZ - 1)));
        return h;
    }

    private int getHeightForChunk(BuildingInfo info) {
        if (info.isCity) return info.getCityGroundLevel();
        if (info.isOcean()) return info.groundLevel - 4;
        return info.getCityGroundLevel();
    }

    private static final class Box {
        final int minX, minZ, maxX, maxZ;
        int height;
        Box(int minX, int minZ, int maxX, int maxZ) { this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ; }
        double sqDist(int x, int z) {
            double d = 0;
            if (x < minX) d += Math.pow(x - minX, 2); else if (x > maxX) d += Math.pow(x - maxX, 2);
            if (z < minZ) d += Math.pow(z - minZ, 2); else if (z > maxZ) d += Math.pow(z - maxZ, 2);
            return d;
        }
    }

    private void flattenChunkToCityBorder(int chunkX, int chunkZ) {
        int cx = chunkX * 16, cz = chunkZ * 16;
        BuildingInfo info = w.info(chunkX, chunkZ);
        float h00 = getHeightAt00Corner(info);
        float h10 = getHeightAt00Corner(info.getXmax());
        float h01 = getHeightAt00Corner(info.getZmax());
        float h11 = getHeightAt00Corner(w.info(chunkX + 1, chunkZ + 1));

        List<Box> boxes = new ArrayList<>();
        List<Box> boxesDownwards = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    int ccx = chunkX + x, ccz = chunkZ + z;
                    BuildingInfo info2 = w.info(ccx, ccz);
                    if (info2.isCity) {
                        boxes.add(new Box(ccx * 16, ccz * 16, ccx * 16 + 15, ccz * 16 + 15));
                    } else if (info2.getMaxHighwayLevel() >= 0 && !info2.isTunnel(info2.getMaxHighwayLevel())) {
                        Box box = new Box(ccx * 16, ccz * 16, ccx * 16 + 15, ccz * 16 + 15);
                        box.height = info.groundLevel + info2.getMaxHighwayLevel() * 6;
                        boxesDownwards.add(box);
                    }
                }
            }
        }
        if (!boxes.isEmpty()) {
            result.feature("border");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    double mindist = 1000000000.0;
                    int height = bipolate(h11, h01, h10, h00, x, z);
                    for (Box box : boxes) {
                        double dist = box.sqDist(cx + x, cz + z);
                        if (dist < mindist) mindist = dist;
                    }
                    int offset = (int) (Math.sqrt(mindist) * 2);
                    flattenChunkBorder(info, x, offset, z, rand, height);
                }
            }
        }
        if (!boxesDownwards.isEmpty()) {
            result.feature("highwayCut");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    double mindist = 1000000000.0;
                    int minheight = 1000000000;
                    for (Box box : boxesDownwards) {
                        double dist = box.sqDist(cx + x, cz + z);
                        if (dist < mindist) mindist = dist;
                        if (box.height < minheight) minheight = box.height;
                    }
                    int offset = (int) (Math.sqrt(mindist) * 2);
                    flattenChunkBorderDownwards(info, x, offset, z, rand, minheight);
                }
            }
        }
    }

    /**
     * Terrain the border fill may replace: air, liquids, plants and trees (the mod filled a bare stone landscape before
     * any decoration). Existing rock, soil and ores are kept.
     */
    private static boolean fillable(char b) {
        switch (b >> 4) {
            case 0: case 8: case 9: case 10: case 11: case 6: case 31: case 32: case 37: case 38: case 39: case 40:
            case 78: case 83: case 106: case 111: case 175:
            case 17: case 18: case 161: case 162: case 81: case 86: case 99: case 100: case 103: case 30: case 59:
                return true;
            default:
                return false;
        }
    }

    private void flattenChunkBorder(BuildingInfo info, int x, int offset, int z, Random rand, int level) {
        driver.current(x, 0, z);
        for (int y = 0; y <= (level - offset - rand.nextInt(2)); y++) {
            char b = driver.getBlock();
            // The mod filled the column with its base block from the bottom up; its terrain was plain stone at
            // that point. Here the terrain is HorrorBiomes' and already populated, so only the gaps are filled.
            if (b != bedrockChar && fillable(b)) driver.add(baseChar);
            else driver.incY();
        }
        int r = rand.nextInt(2);
        clearRange(info, x, z, level + offset + r, 230, info.waterLevel > info.groundLevel);
    }

    private void flattenChunkBorderDownwards(BuildingInfo info, int x, int offset, int z, Random rand, int level) {
        int r = rand.nextInt(2);
        clearRange(info, x, z, level + offset + r, 230, info.waterLevel > info.groundLevel);
    }

    private void clearRange(BuildingInfo info, int x, int z, int height1, int height2, boolean dowater) {
        if (dowater) {
            driver.setBlockRangeSafe(x, height1, z, info.waterLevel, liquidChar);
            driver.setBlockRangeSafe(x, info.waterLevel + 1, z, height2, airChar);
        } else {
            driver.setBlockRange(x, height1, z, height2, airChar);
        }
    }

    // ------------------------------------------------------------------ bridges

    private void generateBridges(BuildingInfo info) {
        if (info.getHighwayXLevel() == 0 || info.getHighwayZLevel() == 0) return;
        BuildingPart bt = info.hasXBridge();
        if (bt != null) {
            generateBridge(info, bt, Orientation.X);
        } else {
            bt = info.hasZBridge();
            if (bt != null) generateBridge(info, bt, Orientation.Z);
        }
    }

    private void generateBridge(BuildingInfo info, BuildingPart bt, Orientation orientation) {
        result.feature("bridge:" + bt.name);
        CompiledPalette compiledPalette = info.getCompiledPalette();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                driver.current(x, mainGroundLevel + 1, z);
                int l = 0;
                while (l < bt.getSliceCount()) {
                    char c = orientation == Orientation.X ? bt.getPaletteChar(x, l, z) : bt.getPaletteChar(z, l, x);
                    char b = compiledPalette.getOr(c, fast, airChar);
                    CompiledPalette.Info inf = compiledPalette.getInfo(c);
                    if (inf != null && inf.torchOrientations != null) b = airChar;   // no lighting in the default profile
                    driver.add(b);
                    l++;
                }
            }
        }

        Character support = bt.getMetaChar("support");
        if (Profile.BRIDGE_SUPPORTS && support != null) {
            char sup = compiledPalette.getOr(support, fast, airChar);
            BuildingInfo minDir = orientation.getMinDir().get(info);
            BuildingInfo maxDir = orientation.getMaxDir().get(info);
            if (minDir.hasBridge(orientation) != null && maxDir.hasBridge(orientation) != null) {
                for (int y = info.waterLevel - 10; y <= info.groundLevel; y++) {
                    driver.current(7, y, 7).block(sup);
                    driver.current(7, y, 8).block(sup);
                    driver.current(8, y, 7).block(sup);
                    driver.current(8, y, 8).block(sup);
                }
            }
            if (minDir.hasBridge(orientation) == null) {
                if (orientation == Orientation.X) {
                    driver.current(0, mainGroundLevel, 6);
                    for (int z = 6; z <= 9; z++) driver.block(sup).incZ();
                } else {
                    driver.current(6, mainGroundLevel, 0);
                    for (int x = 6; x <= 9; x++) driver.block(sup).incX();
                }
            }
            if (maxDir.hasBridge(orientation) == null) {
                if (orientation == Orientation.X) {
                    driver.current(15, mainGroundLevel, 6);
                    for (int z = 6; z <= 9; z++) driver.block(sup).incZ();
                } else {
                    driver.current(6, mainGroundLevel, 15);
                    for (int x = 6; x <= 9; x++) driver.block(sup).incX();
                }
            }
        }
    }

    // ------------------------------------------------------------------ highways

    private void generateHighways(int chunkX, int chunkZ, BuildingInfo info) {
        int levelX = info.getHighwayXLevel();
        int levelZ = info.getHighwayZLevel();
        if (levelX == levelZ && levelX >= 0) {
            generateHighwayPart(info, levelX, Transform.ROTATE_NONE, info.getXmax(), info.getZmax(), "_bi");
        } else if (levelX >= 0 && levelZ >= 0) {
            if (levelX == 0) {
                generateHighwayPart(info, levelX, Transform.ROTATE_NONE, info.getZmin(), info.getZmax(), "");
                generateHighwayPart(info, levelZ, Transform.ROTATE_90, info.getXmax(), info.getXmax(), "");
            } else {
                generateHighwayPart(info, levelZ, Transform.ROTATE_90, info.getXmax(), info.getXmax(), "");
                generateHighwayPart(info, levelX, Transform.ROTATE_NONE, info.getZmin(), info.getZmax(), "");
            }
        } else {
            if (levelX >= 0) generateHighwayPart(info, levelX, Transform.ROTATE_NONE, info.getZmin(), info.getZmax(), "");
            else if (levelZ >= 0) generateHighwayPart(info, levelZ, Transform.ROTATE_90, info.getXmax(), info.getXmax(), "");
        }
    }

    private void generateHighwayPart(BuildingInfo info, int level, Transform transform, BuildingInfo adjacent1, BuildingInfo adjacent2, String suffix) {
        int highwayGroundLevel = info.groundLevel + level * 6;
        BuildingPart part;
        if (info.isTunnel(level)) {
            part = w.assets.part("highway_tunnel" + suffix);
            generatePart(info, part, transform, 0, highwayGroundLevel, 0, true);
        } else if (info.isCity && level <= adjacent1.cityLevel && level <= adjacent2.cityLevel && adjacent1.isCity && adjacent2.isCity) {
            part = w.assets.part("highway_open" + suffix);
            int height = generatePart(info, part, transform, 0, highwayGroundLevel, 0, true);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) clearRange(info, x, z, height, height + 15, info.waterLevel > info.groundLevel);
        } else {
            part = w.assets.part("highway_bridge" + suffix);
            int height = generatePart(info, part, transform, 0, highwayGroundLevel, 0, true);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) clearRange(info, x, z, height, height + 15, info.waterLevel > info.groundLevel);
        }
        result.feature("highway:" + part.name);

        Character support = part.getMetaChar("support");
        if (Profile.HIGHWAY_SUPPORTS && support != null) {
            char sup = info.getCompiledPalette().getOr(support, fast, airChar);
            int x1 = transform.rotateX(0, 15), z1 = transform.rotateZ(0, 15);
            driver.current(x1, highwayGroundLevel - 1, z1);
            for (int y = 0; y < 40; y++) {
                if (driver.getBlock() == airChar || driver.getBlock() == liquidChar) driver.block(sup);
                else break;
                driver.decY();
            }
            int x2 = transform.rotateX(0, 0), z2 = transform.rotateZ(0, 0);
            driver.current(x2, highwayGroundLevel - 1, z2);
            for (int y = 0; y < 40; y++) {
                if (driver.getBlock() == airChar || driver.getBlock() == liquidChar) driver.block(sup);
                else break;
                driver.decY();
            }
        }
    }

    // ------------------------------------------------------------------ explosions

    private void breakBlocksForDamage(int chunkX, int chunkZ, BuildingInfo info) {
        int cx = chunkX * 16, cz = chunkZ * 16;
        DamageArea damageArea = info.getDamageArea();
        boolean clear = false;
        float damageFactor = 1.0f;
        for (int yy = 0; yy < 16; yy++) {
            if (clear || damageArea.hasExplosions(yy)) {
                if (clear || damageArea.isCompletelyDestroyed(yy)) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int height = yy * 16;
                            driver.current(x, height, z);
                            for (int y = 0; y < 16; y++) driver.add(((height + y) <= info.waterLevel) ? liquidChar : airChar);
                        }
                    }
                    clear = true;
                } else {
                    for (int y = 0; y < 16; y++) {
                        int cntDamaged = 0, cntAir = 0;
                        int cury = yy * 16 + y;
                        for (int x = 0; x < 16; x++) {
                            driver.current(x, cury, 0);
                            for (int z = 0; z < 16; z++) {
                                char d = driver.getBlock();
                                if (d != airChar || cury <= info.waterLevel) {
                                    float damage = damageArea.getDamage(cx + x, cury, cz + z) * damageFactor;
                                    if (damage >= 0.001) {
                                        char newd = damageArea.damageBlock(d, rand, cury, damage, info.getCompiledPalette(), liquidChar);
                                        if (newd != d) {
                                            driver.block(newd);
                                            cntDamaged++;
                                        }
                                    }
                                } else {
                                    cntAir++;
                                }
                                driver.incZ();
                            }
                        }
                        int tot = cntDamaged + cntAir;
                        if (tot > 250) {
                            damageFactor = 200;
                            clear = true;
                        } else if (tot > 220) {
                            damageFactor = damageFactor * 1.4f;
                        } else if (tot > 180) {
                            damageFactor = damageFactor * 1.2f;
                        }
                    }
                }
            }
        }
    }

    // Floating-block pass (Blob): primitive arrays instead of the mod's HashSet<IIndex>.
    private final int[] owner = new int[65536];
    private final int[] queue = new int[65536];

    private static final class Blob {
        int id;
        int[] blocks = new int[64];
        int size;
        int connections;
        int lowestY = 256;
        void add(int idx) { if (size == blocks.length) blocks = java.util.Arrays.copyOf(blocks, size * 2); blocks[size++] = idx; }
    }

    private void fixAfterExplosionNew(BuildingInfo info, Random rand) {
        int start = info.getDamageArea().getLowestExplosionHeight();
        if (start == -1) return;
        int end = info.getDamageArea().getHighestExplosionHeight();
        java.util.Arrays.fill(owner, 0);
        List<Blob> blobs = new ArrayList<>();
        char[] data = driver.data;
        int[] maxH = {info.getXmin().getMaxHeight() + 3, info.getXmax().getMaxHeight() + 3,
                      info.getZmin().getMaxHeight() + 3, info.getZmax().getMaxHeight() + 3};
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = start; y < end; y++) {
                    int idx = Driver.index(x, y, z);
                    char p = data[idx];
                    if (p != airChar && p != liquidChar && owner[idx] == 0) {
                        Blob blob = new Blob();
                        blob.id = blobs.size() + 1;
                        scan(blob, data, idx, start, maxH);
                        blobs.add(blob);
                    }
                }
            }
        }
        List<Blob> candidates = new ArrayList<>();
        for (Blob blob : blobs) {
            boolean destroyOrMove = blob.connections < 5 || (((float) blob.connections / blob.size) < Profile.DESTROY_LONE_BLOCKS_FACTOR);
            if (destroyOrMove) candidates.add(blob);
        }
        candidates.sort((a, b) -> a.lowestY - b.lowestY);
        boolean[] moving = new boolean[65536];
        List<Integer> toMove = new ArrayList<>();
        for (Blob blob : candidates) {
            if (rand.nextFloat() < Profile.DESTROY_OR_MOVE_CHANCE || blob.size < Profile.DESTROY_SMALL_SECTIONS_SIZE || blob.connections < 5) {
                for (int i = 0; i < blob.size; i++) {
                    int idx = blob.blocks[i];
                    data[idx] = (idx & 0xff) < info.waterLevel ? liquidChar : airChar;
                }
            } else {
                for (int i = 0; i < blob.size; i++) { moving[blob.blocks[i]] = true; toMove.add(blob.blocks[i]); }
            }
        }
        if (!toMove.isEmpty()) {
            toMove.sort(null);
            for (int idx : toMove) {
                char c = data[idx];
                data[idx] = (idx & 0xff) < info.waterLevel ? liquidChar : airChar;
                int cur = idx - 1;
                int y = cur & 0xff;
                while (y > 2 && (moving[cur] || data[cur] == airChar || data[cur] == liquidChar)) { cur--; y--; }
                cur++;
                data[cur] = c;
            }
        }
    }

    /**
     * Blob.scan: flood the connected non-air, non-liquid blocks from one block. Leaving the chunk sideways counts a
     * connection when the neighbour chunk is tall enough there; going below the damaged band counts five.
     * Positions are marked when queued, so the queue never holds more than the chunk.
     */
    private void scan(Blob blob, char[] data, int startIdx, int starty, int[] maxH) {
        int head = 0, tail = 0;
        owner[startIdx] = blob.id;
        blob.add(startIdx);
        blob.lowestY = Math.min(blob.lowestY, startIdx & 0xff);
        queue[tail++] = startIdx;
        while (head < tail) {
            int p = queue[head++];
            int x = (p >> 12) & 15, z = (p >> 8) & 15, y = p & 0xff;
            for (int n = 0; n < 6; n++) {
                int nx = x, ny = y, nz = z;
                switch (n) {
                    case 0: ny++; break;
                    case 1: ny--; break;
                    case 2: nx++; break;
                    case 3: nx--; break;
                    case 4: nz++; break;
                    default: nz--; break;
                }
                if (nx < 0) { if (ny <= maxH[0]) blob.connections++; continue; }
                if (nx > 15) { if (ny <= maxH[1]) blob.connections++; continue; }
                if (nz < 0) { if (ny <= maxH[2]) blob.connections++; continue; }
                if (nz > 15) { if (ny <= maxH[3]) blob.connections++; continue; }
                if (ny < starty) { blob.connections += 5; continue; }
                if (ny > 255) continue;
                int idx = Driver.index(nx, ny, nz);
                if (owner[idx] == blob.id) continue;
                char b = data[idx];
                if (b == airChar || b == liquidChar) continue;
                owner[idx] = blob.id;
                blob.add(idx);
                if (ny < blob.lowestY) blob.lowestY = ny;
                queue[tail++] = idx;
            }
        }
    }

    // ------------------------------------------------------------------ debris

    private void generateDebris(Random rand, BuildingInfo info) {
        generateDebrisFromChunk(rand, info, info.getXmin(), 0);
        generateDebrisFromChunk(rand, info, info.getXmax(), 1);
        generateDebrisFromChunk(rand, info, info.getZmin(), 2);
        generateDebrisFromChunk(rand, info, info.getZmax(), 3);
        generateDebrisFromChunk(rand, info, w.info(info.chunkX - 1, info.chunkZ - 1), 4);
        generateDebrisFromChunk(rand, info, w.info(info.chunkX + 1, info.chunkZ + 1), 5);
        generateDebrisFromChunk(rand, info, w.info(info.chunkX - 1, info.chunkZ + 1), 6);
        generateDebrisFromChunk(rand, info, w.info(info.chunkX + 1, info.chunkZ - 1), 7);
    }

    private static float locationFactor(int kind, int xx, int zz) {
        switch (kind) {
            case 0: return (15.0f - xx) / 16.0f;
            case 1: return xx / 16.0f;
            case 2: return (15.0f - zz) / 16.0f;
            case 3: return zz / 16.0f;
            case 4: return ((15.0f - xx) * (15.0f - zz)) / 256.0f;
            case 5: return (xx * zz) / 256.0f;
            case 6: return ((15.0f - xx) * zz) / 256.0f;
            default: return (xx * (15.0f - zz)) / 256.0f;
        }
    }

    private void generateDebrisFromChunk(Random rand, BuildingInfo info, BuildingInfo adjacentInfo, int kind) {
        if (adjacentInfo.hasBuilding) {
            char filler = adjacentInfo.getCompiledPalette().getOr(adjacentInfo.buildingType.fillerBlock, fast, baseChar);
            float damageFactor = adjacentInfo.getDamageArea().getDamageFactor();
            if (damageFactor > .5f) {
                int blocks = (1 + adjacentInfo.getNumFloors()) * 1000;
                float damage = Math.max(1.0f, damageFactor * DamageArea.BLOCK_DAMAGE_CHANCE);
                int destroyedBlocks = (int) (blocks * damage);
                destroyedBlocks /= Profile.DEBRIS_TO_NEARBYCHUNK_FACTOR;
                int h = adjacentInfo.getMaxHeight() + 10;
                for (int i = 0; i < destroyedBlocks; i++) {
                    int x = rand.nextInt(16);
                    int z = rand.nextInt(16);
                    if (rand.nextFloat() < locationFactor(kind, x, z)) {
                        driver.current(x, h, z);
                        while (h > 0 && (driver.getBlock() == airChar || driver.getBlock() == liquidChar)) {
                            h--;
                            driver.decY();
                        }
                        char b = rand.nextInt(5) == 0 ? ironbarsChar : filler;
                        driver.current(x, h + 1, z).block(b);
                    }
                }
                if (destroyedBlocks > 0) result.feature("debris");
            }
        }
    }

    // ------------------------------------------------------------------ city chunks

    private void doCityChunk(int chunkX, int chunkZ, BuildingInfo info) {
        boolean building = info.hasBuilding;
        Random rand = new Random(w.seed * 377 + chunkZ * 341873128712L + chunkX * 132897987541L);
        rand.nextFloat();
        rand.nextFloat();
        for (int x = 0; x < 16; ++x) for (int z = 0; z < 16; ++z) driver.setBlockRange(x, 0, z, Profile.BEDROCK_LAYER, bedrockChar);

        if (building) generateBuilding(info);
        else generateStreet(info, rand);

        if (Profile.RUINS) generateRuins(info);

        int levelX = info.getHighwayXLevel();
        int levelZ = info.getHighwayZLevel();
        if (!building) {
            Railway.RailChunkInfo railInfo = info.getRailInfo();
            if (levelX < 0 && levelZ < 0 && !railInfo.type.isSurface()) generateStreetDecorations(info);
        }
        if (levelX >= 0 || levelZ >= 0) generateHighways(chunkX, chunkZ, info);

        if (Profile.RUBBLELAYER) {
            if (!info.hasBuilding || info.ruinHeight >= 0) generateRubble(chunkX, chunkZ, info);
        }
    }

    private void generateRailwayDungeons(BuildingInfo info) {
        if (info.railDungeon == null) return;
        if (info.getZmin().getRailInfo().type == RailChunkType.HORIZONTAL || info.getZmax().getRailInfo().type == RailChunkType.HORIZONTAL) {
            int height = info.groundLevel + Railway.RAILWAY_LEVEL_OFFSET * 6;
            generatePart(info, info.railDungeon, Transform.ROTATE_NONE, 0, height, 0, false);
            result.feature("raildungeon:" + info.railDungeon.name);
        }
    }

    private boolean water6(int height) {
        return driver.getBlock(3, height + 2, 3) == liquidChar && driver.getBlock(12, height + 2, 3) == liquidChar
            && driver.getBlock(3, height + 2, 12) == liquidChar && driver.getBlock(12, height + 2, 12) == liquidChar
            && driver.getBlock(3, height + 4, 7) == liquidChar && driver.getBlock(12, height + 4, 8) == liquidChar;
    }

    private void generateRailways(BuildingInfo info, Railway.RailChunkInfo railInfo) {
        int height = info.groundLevel + railInfo.level * 6;
        RailChunkType type = railInfo.type;
        BuildingPart part;
        Transform transform = Transform.ROTATE_NONE;
        boolean needsStaircase = false;
        switch (type) {
            case NONE:
                return;
            case STATION_SURFACE:
            case STATION_EXTENSION_SURFACE:
                if (railInfo.level < info.cityLevel) {
                    part = w.assets.part("station_underground");
                } else {
                    part = railInfo.part != null ? w.assets.part(railInfo.part) : w.assets.part("station_open");
                }
                break;
            case STATION_UNDERGROUND:
                part = w.assets.part("station_underground_stairs");
                needsStaircase = true;
                break;
            case STATION_EXTENSION_UNDERGROUND:
                part = w.assets.part("station_underground");
                break;
            case RAILS_END_HERE:
                part = w.assets.part("rails_horizontal_end");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            case HORIZONTAL: {
                part = w.assets.part("rails_horizontal");
                RailChunkType type1 = info.getXmin().getRailInfo().type;
                RailChunkType type2 = info.getXmax().getRailInfo().type;
                if (!type1.isStation() && !type2.isStation() && water6(height)) part = w.assets.part("rails_horizontal_water");
                break;
            }
            case VERTICAL:
                part = w.assets.part("rails_vertical");
                if (water6(height)) part = w.assets.part("rails_vertical_water");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            case THREE_SPLIT:
                part = w.assets.part("rails_3split");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            case GOING_DOWN_TWO_FROM_SURFACE:
            case GOING_DOWN_FURTHER:
                part = w.assets.part("rails_down2");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            case GOING_DOWN_ONE_FROM_SURFACE:
                part = w.assets.part("rails_down1");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            case DOUBLE_BEND:
                part = w.assets.part("rails_bend");
                if (railInfo.direction == Railway.RailDirection.EAST) transform = Transform.MIRROR_X;
                break;
            default:
                part = w.assets.part("rails_flat");
                break;
        }
        generatePart(info, part, transform, 0, height, 0, false);
        result.feature("rail:" + part.name);

        char rail = info.getCompiledPalette().getOr(info.getCityStyle().railMainBlock, fast, baseChar);

        if (type == RailChunkType.HORIZONTAL) {
            if (info.getZmin().railDungeon != null) {
                for (int z = 0; z < 4; z++) {
                    driver.current(6, height + 1, z).add(rail).add(airChar).add(airChar);
                    driver.current(7, height + 1, z).add(rail).add(airChar).add(airChar);
                }
                for (int z = 0; z < 3; z++) {
                    driver.current(5, height + 2, z).add(rail).add(rail).add(rail);
                    driver.current(6, height + 4, z).block(rail);
                    driver.current(7, height + 4, z).block(rail);
                    driver.current(8, height + 2, z).add(rail).add(rail).add(rail);
                }
            }
            if (info.getZmax().railDungeon != null) {
                for (int z = 0; z < 5; z++) {
                    driver.current(6, height + 1, 15 - z).add(rail).add(airChar).add(airChar);
                    driver.current(7, height + 1, 15 - z).add(rail).add(airChar).add(airChar);
                }
                for (int z = 0; z < 4; z++) {
                    driver.current(5, height + 2, 15 - z).add(rail).add(rail).add(rail);
                    driver.current(6, height + 4, 15 - z).block(rail);
                    driver.current(7, height + 4, 15 - z).block(rail);
                    driver.current(8, height + 2, 15 - z).add(rail).add(rail).add(rail);
                }
            }
        }

        if (railInfo.rails < 3) {
            switch (railInfo.type) {
                case STATION_SURFACE:
                case STATION_UNDERGROUND:
                case STATION_EXTENSION_SURFACE:
                case STATION_EXTENSION_UNDERGROUND:
                case HORIZONTAL: {
                    if (railInfo.rails == 1) {
                        driver.current(0, height + 1, 5);
                        for (int x = 0; x < 16; x++) driver.block(rail).incX();
                        driver.current(0, height + 1, 9);
                        for (int x = 0; x < 16; x++) driver.block(rail).incX();
                    } else {
                        driver.current(0, height + 1, 7);
                        for (int x = 0; x < 16; x++) driver.block(rail).incX();
                    }
                    break;
                }
                case GOING_DOWN_TWO_FROM_SURFACE:
                case GOING_DOWN_ONE_FROM_SURFACE:
                case GOING_DOWN_FURTHER:
                    if (railInfo.rails == 1) {
                        for (int x = 0; x < 16; x++) {
                            for (int y = height + 1; y < height + part.getSliceCount(); y++) {
                                driver.current(x, y, 5);
                                if (B.isRail(driver.getBlock())) driver.block(rail);
                                driver.current(x, y, 9);
                                if (B.isRail(driver.getBlock())) driver.block(rail);
                            }
                        }
                    } else {
                        for (int x = 0; x < 16; x++) {
                            for (int y = height + 1; y < height + part.getSliceCount(); y++) {
                                driver.current(x, y, 7);
                                if (B.isRail(driver.getBlock())) driver.block(rail);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        if (needsStaircase) {
            part = w.assets.part("station_staircase");
            for (int i = railInfo.level + 1; i < info.cityLevel; i++) {
                height = info.groundLevel + i * 6;
                generatePart(info, part, transform, 0, height, 0, false);
            }
            height = info.groundLevel + info.cityLevel * 6;
            part = w.assets.part("station_staircase_surface");
            generatePart(info, part, transform, 0, height, 0, false);
        }

        capRailTunnel(info, railInfo, height, rail);
    }

    /**
     * Not in the mod (its rail network spanned the whole world): where a tunnel runs into a chunk this plugin will
     * never generate (pre-existing land, a sanctuary), its open end is walled with the rail block.
     */
    private void capRailTunnel(BuildingInfo info, Railway.RailChunkInfo railInfo, int ignored, char rail) {
        int base = info.groundLevel + railInfo.level * 6;
        boolean horizontal = railInfo.type != RailChunkType.VERTICAL;
        int[][] sides = horizontal ? new int[][]{{-1, 0}, {1, 0}} : new int[][]{{0, -1}, {0, 1}};
        for (int[] s : sides) {
            int nx = info.chunkX + s[0], nz = info.chunkZ + s[1];
            if (w.managed(nx, nz)) continue;
            Railway.RailChunkInfo other = w.railway.getRailChunkType(nx, nz);
            if (other.type == RailChunkType.NONE) continue;
            for (int a = 1; a < 15; a++) {
                for (int y = base + 1; y < base + 5; y++) {
                    int x = horizontal ? (s[0] < 0 ? 0 : 15) : a, z = horizontal ? a : (s[1] < 0 ? 0 : 15);
                    char b = driver.getBlock(x, y, z);
                    if (b == airChar || b == liquidChar || B.isRail(b)) driver.current(x, y, z).block(rail);
                }
            }
            result.feature("railCap");
        }
    }

    private void generateStreetDecorations(BuildingInfo info) {
        Direction stairDirection = info.getActualStairDirection();
        if (stairDirection != null) {
            BuildingPart stairs = info.stairType;
            Transform transform;
            int oy = info.getCityGroundLevel() + 1;
            switch (stairDirection) {
                case XMIN: transform = Transform.ROTATE_NONE; break;
                case XMAX: transform = Transform.ROTATE_180; break;
                case ZMIN: transform = Transform.ROTATE_90; break;
                case ZMAX: transform = Transform.ROTATE_270; break;
                default: throw new IllegalStateException("Cannot happen!");
            }
            generatePart(info, stairs, transform, 0, oy, 0, false);
            result.feature("stairs:" + stairs.name);
        }
    }

    // ------------------------------------------------------------------ rubble and ruins

    private void generateRubble(int chunkX, int chunkZ, BuildingInfo info) {
        rubbleBuffer = w.rubbleNoise.a(rubbleBuffer, (chunkX * 16), (chunkZ * 16), 16, 16, 1.0 / 16.0, 1.0 / 16.0, 1.0D);
        leavesBuffer = w.leavesNoise.a(leavesBuffer, (chunkX * 64), (chunkZ * 64), 16, 16, 1.0 / 64.0, 1.0 / 64.0, 4.0D);
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                double vr = Profile.RUBBLE_DIRT_SCALE < 0.01f ? 0 : rubbleBuffer[x + z * 16] / Profile.RUBBLE_DIRT_SCALE;
                double vl = Profile.RUBBLE_LEAVE_SCALE < 0.01f ? 0 : leavesBuffer[x + z * 16] / Profile.RUBBLE_LEAVE_SCALE;
                if (vr > .5 || vl > .5) {
                    int height = getInterpolatedHeight(info, x, z);
                    driver.current(x, height, z);
                    if (height == 0) continue;
                    char c = driver.getBlockDown();
                    if (c != airChar && c != liquidChar) {
                        for (int i = 0; i < vr; i++) {
                            if (driver.getBlock() == airChar || driver.getBlock() == liquidChar) driver.add(baseChar);
                            else driver.incY();
                        }
                    }
                    if (driver.getBlockDown() == baseChar) {
                        for (int i = 0; i < vl; i++) {
                            if (driver.getBlock() == airChar || driver.getBlock() == liquidChar) driver.add(getRandomLeaf());
                            else driver.incY();
                        }
                    }
                }
            }
        }
    }

    private int getInterpolatedHeight(BuildingInfo info, int x, int z) {
        BuildingInfo xm = info.getXmin(), xp = info.getXmax(), zm = info.getZmin(), zp = info.getZmax();
        if (x < 8 && z < 8) {
            float h00 = w.info(info.chunkX - 1, info.chunkZ - 1).getCityGroundLevelOutsideLower();
            float h10 = zm.getCityGroundLevelOutsideLower();
            float h01 = xm.getCityGroundLevelOutsideLower();
            float h11 = info.getCityGroundLevelOutsideLower();
            return bipolate(h00, h10, h01, h11, x + 8, z + 8);
        } else if (x >= 8 && z < 8) {
            float h00 = zm.getCityGroundLevelOutsideLower();
            float h10 = w.info(info.chunkX + 1, info.chunkZ - 1).getCityGroundLevelOutsideLower();
            float h01 = info.getCityGroundLevelOutsideLower();
            float h11 = xp.getCityGroundLevelOutsideLower();
            return bipolate(h00, h10, h01, h11, x - 8, z + 8);
        } else if (x < 8) {
            float h00 = xm.getCityGroundLevelOutsideLower();
            float h10 = info.getCityGroundLevelOutsideLower();
            float h01 = w.info(info.chunkX - 1, info.chunkZ + 1).getCityGroundLevelOutsideLower();
            float h11 = zp.getCityGroundLevelOutsideLower();
            return bipolate(h00, h10, h01, h11, x + 8, z - 8);
        } else {
            float h00 = info.getCityGroundLevelOutsideLower();
            float h10 = xp.getCityGroundLevelOutsideLower();
            float h01 = zp.getCityGroundLevelOutsideLower();
            float h11 = w.info(info.chunkX + 1, info.chunkZ + 1).getCityGroundLevelOutsideLower();
            return bipolate(h00, h10, h01, h11, x - 8, z - 8);
        }
    }

    private static int bipolate(float h00, float h10, float h01, float h11, int dx, int dz) {
        float factor = (15.0f - dx) / 15.0f;
        float h0 = h00 + (h10 - h00) * factor;
        float h1 = h01 + (h11 - h01) * factor;
        float h = h0 + (h1 - h0) * (15.0f - dz) / 15.0f;
        return (int) h;
    }

    private void generateRuins(BuildingInfo info) {
        if (info.ruinHeight < 0) return;
        int chunkX = info.chunkX, chunkZ = info.chunkZ;
        double d0 = 0.03125D;
        ruinBuffer = w.ruinNoise.a(ruinBuffer, (chunkX * 16), (chunkZ * 16), 16, 16, d0 * 2.0D, d0 * 2.0D, 1.0D);
        boolean doLeaves = Profile.RUBBLELAYER;
        if (doLeaves) leavesBuffer = w.leavesNoise.a(leavesBuffer, (chunkX * 64), (chunkZ * 64), 16, 16, 1.0 / 64.0, 1.0 / 64.0, 4.0D);
        int baseheight = (int) (info.getCityGroundLevel() + 1 + (info.ruinHeight * info.getNumFloors() * 6.0f));
        result.feature("ruin");
        for (int x = 0; x < 16; ++x) {
            zLoop:
            for (int z = 0; z < 16; ++z) {
                double v = ruinBuffer[x + z * 16];
                int height = baseheight + (int) v;
                if (height == 0) continue;
                driver.current(x, height, z);
                height = info.getMaxHeight() + 10 - height;
                int vl = 0;
                if (doLeaves) vl = (int) (Profile.RUBBLE_LEAVE_SCALE < 0.01f ? 0 : leavesBuffer[x + z * 16] / Profile.RUBBLE_LEAVE_SCALE);
                while (height > 0) {
                    Character damage = info.getCompiledPalette().canBeDamagedToIronBars(driver.getBlock());
                    char c = driver.getBlockDown();
                    if ((damage != null || c == ironbarsChar) && c != airChar && c != liquidChar && rand.nextFloat() < .2f) {
                        driver.add(ironbarsChar);
                    } else {
                        if (vl > 0) {
                            c = driver.getBlockDown();
                            while (c == airChar || c == liquidChar) {
                                driver.decY();
                                if (driver.getY() == 0) continue zLoop;
                                height++;
                                c = driver.getBlockDown();
                            }
                            driver.add(getRandomLeaf());
                            vl--;
                        } else {
                            driver.add(airChar);
                        }
                    }
                    height--;
                }
            }
        }
    }

    // ------------------------------------------------------------------ streets

    private void generateStreet(BuildingInfo info, Random rand) {
        boolean xRail = info.hasXCorridor();
        boolean zRail = info.hasZCorridor();
        if (xRail || zRail) {
            generateCorridors(info, xRail, zRail);
            result.feature("corridor");
        }

        Railway.RailChunkInfo railInfo = info.getRailInfo();
        boolean canDoParks = info.getHighwayXLevel() != info.cityLevel && info.getHighwayZLevel() != info.cityLevel
            && railInfo.type != RailChunkType.STATION_SURFACE
            && (railInfo.type != RailChunkType.STATION_EXTENSION_SURFACE || railInfo.level < info.cityLevel);

        if (canDoParks) {
            int height = info.getCityGroundLevel();
            BuildingInfo.StreetType streetType = info.streetType;
            boolean elevated = info.isElevatedParkSection();
            if (elevated) {
                char elevation = pal(info.getCompiledPalette(), info.getCityStyle().parkElevationBlock);
                streetType = BuildingInfo.StreetType.PARK;
                for (int x = 0; x < 16; ++x) {
                    driver.current(x, height, 0);
                    for (int z = 0; z < 16; ++z) driver.block(elevation).incZ();
                }
                height++;
            }
            switch (streetType) {
                case NORMAL: generateNormalStreetSection(info, height); break;
                case FULL: generateFullStreetSection(height); break;
                case PARK: generateParkSection(info, height, elevated); break;
            }
            height++;

            if (streetType == BuildingInfo.StreetType.PARK || info.fountainType != null) {
                BuildingPart part = streetType == BuildingInfo.StreetType.PARK ? info.parkType : info.fountainType;
                generatePart(info, part, Transform.ROTATE_NONE, 0, height, 0, false);
                result.feature((streetType == BuildingInfo.StreetType.PARK ? "park:" : "fountain:") + part.name);
            } else {
                result.feature("street:" + streetType.name().toLowerCase());
            }

            generateRandomVegetation(info, rand, height);

            generateFrontPart(info, height, info.getXmin(), Transform.ROTATE_NONE);
            generateFrontPart(info, height, info.getZmin(), Transform.ROTATE_90);
            generateFrontPart(info, height, info.getXmax(), Transform.ROTATE_180);
            generateFrontPart(info, height, info.getZmax(), Transform.ROTATE_270);
        } else {
            result.feature("street:noparks");
        }
        generateBorders(info, canDoParks);
    }

    private void generateBorders(BuildingInfo info, boolean canDoParks) {
        fillToBedrockStreetBlock(info);
        if (doBorder(info, Direction.XMIN)) for (int z = 0; z < 16; z++) generateBorder(info, canDoParks, 0, z, Direction.XMIN.get(info));
        if (doBorder(info, Direction.XMAX)) for (int z = 0; z < 16; z++) generateBorder(info, canDoParks, 15, z, Direction.XMAX.get(info));
        if (doBorder(info, Direction.ZMIN)) for (int x = 0; x < 16; x++) generateBorder(info, canDoParks, x, 0, Direction.ZMIN.get(info));
        if (doBorder(info, Direction.ZMAX)) for (int x = 0; x < 16; x++) generateBorder(info, canDoParks, x, 15, Direction.ZMAX.get(info));
    }

    private void fillToBedrockStreetBlock(BuildingInfo info) {
        for (int x = 0; x < 16; ++x)
            for (int z = 0; z < 16; ++z)
                driver.setBlockRange(x, Profile.BEDROCK_LAYER, z, info.getCityGroundLevel(), baseChar);
    }

    private void generateBorder(BuildingInfo info, boolean canDoParks, int x, int z, BuildingInfo adjacent) {
        Character borderBlock = info.getCityStyle().borderBlock;
        char wall = pal(info.getCompiledPalette(), info.getCityStyle().wallBlock);
        setBlocksFromPalette(x, info.groundLevel - 6, z, info.getCityGroundLevel() + 1, info.getCompiledPalette(), borderBlock);
        if (canDoParks) {
            if (!borderNeedsConnectionToAdjacentChunk(info, x, z)) driver.current(x, info.getCityGroundLevel() + 1, z).block(wall);
            else driver.current(x, info.getCityGroundLevel() + 1, z).block(airChar);
        }
    }

    private void generateFrontPart(BuildingInfo info, int height, BuildingInfo adj, Transform rot) {
        if (info.hasFrontPartFrom(adj)) {
            generatePart(adj, adj.frontType, rot, 0, height, 0, false);
            result.feature("front:" + adj.frontType.name);
        }
    }

    private void generateCorridors(BuildingInfo info, boolean xRail, boolean zRail) {
        char railxC = B.c(66, 1);   // rail EAST_WEST
        char railzC = B.c(66, 0);   // rail NORTH_SOUTH
        Character corridorRoofBlock = info.getCityStyle().corridorRoofBlock;
        Character corridorGlassBlock = info.getCityStyle().corridorGlassBlock;
        CompiledPalette palette = info.getCompiledPalette();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                char b;
                if ((xRail && z >= 7 && z <= 10) || (zRail && x >= 7 && x <= 10)) {
                    int height = info.groundLevel - 5;
                    if (xRail && z == 10) b = railxC;
                    else if (zRail && x == 10) b = railzC;
                    else b = airChar;
                    driver.current(x, height, z).add(b).add(airChar).add(airChar);
                    if ((xRail && x == 7 && (z == 8 || z == 9)) || (zRail && z == 7 && (x == 8 || x == 9))) {
                        char glass = pal(palette, corridorGlassBlock);
                        driver.add(glass).add(glowstoneChar);
                    } else {
                        char roof = pal(palette, corridorRoofBlock);
                        driver.add(roof).add(roof);
                    }
                } else {
                    driver.setBlockRange(x, info.groundLevel - 5, z, info.getCityGroundLevel(), baseChar);
                }
            }
        }
    }

    private void generateRandomVegetation(BuildingInfo info, Random rand, int height) {
        if (height == 0) return;
        int t = Profile.THICKNESS_OF_RANDOM_LEAFBLOCKS;
        float chance = Profile.CHANCE_OF_RANDOM_LEAFBLOCKS;
        if (info.getXmin().hasBuilding) {
            for (int x = 0; x < t; x++) {
                zLoop:
                for (int z = 0; z < 16; z++) {
                    driver.current(x, height, z);
                    while (driver.getBlockDown() == airChar) { driver.decY(); if (driver.getY() == 0) continue zLoop; }
                    float v = Math.min(.8f, chance * (t + 1 - x));
                    int cnt = 0;
                    while (rand.nextFloat() < v && cnt < 30) { driver.add(getRandomLeaf()); cnt++; }
                }
            }
        }
        if (info.getXmax().hasBuilding) {
            for (int x = 15 - t; x < 15; x++) {
                zLoop:
                for (int z = 0; z < 16; z++) {
                    driver.current(x, height, z);
                    while (driver.getBlockDown() == airChar) { driver.decY(); if (driver.getY() == 0) continue zLoop; }
                    float v = Math.min(.8f, chance * (x - 14 + t));
                    int cnt = 0;
                    while (rand.nextFloat() < v && cnt < 30) { driver.add(getRandomLeaf()); cnt++; }
                }
            }
        }
        if (info.getZmin().hasBuilding) {
            for (int z = 0; z < t; z++) {
                xLoop:
                for (int x = 0; x < 16; x++) {
                    driver.current(x, height, z);
                    while (driver.getBlockDown() == airChar) { driver.decY(); if (driver.getY() == 0) continue xLoop; }
                    float v = Math.min(.8f, chance * (t + 1 - z));
                    int cnt = 0;
                    while (rand.nextFloat() < v && cnt < 30) { driver.add(getRandomLeaf()); cnt++; }
                }
            }
        }
        if (info.getZmax().hasBuilding) {
            for (int z = 15 - t; z < 15; z++) {
                xLoop:
                for (int x = 0; x < 16; x++) {
                    driver.current(x, height, z);
                    while (driver.getBlockDown() == airChar) { driver.decY(); if (driver.getY() == 0) continue xLoop; }
                    float v = chance * (z - 14 + t);
                    int cnt = 0;
                    while (rand.nextFloat() < v && cnt < 30) { driver.add(getRandomLeaf()); cnt++; }
                }
            }
        }
    }

    private void generateParkSection(BuildingInfo info, int height, boolean elevated) {
        char b;
        boolean el00 = w.info(info.chunkX - 1, info.chunkZ - 1).isElevatedParkSection();
        boolean el10 = info.getZmin().isElevatedParkSection();
        boolean el20 = w.info(info.chunkX + 1, info.chunkZ - 1).isElevatedParkSection();
        boolean el01 = info.getXmin().isElevatedParkSection();
        boolean el21 = info.getXmax().isElevatedParkSection();
        boolean el02 = w.info(info.chunkX - 1, info.chunkZ + 1).isElevatedParkSection();
        boolean el12 = info.getZmax().isElevatedParkSection();
        boolean el22 = w.info(info.chunkX + 1, info.chunkZ + 1).isElevatedParkSection();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                if (x == 0 || x == 15 || z == 0 || z == 15) {
                    b = street;
                    if (elevated) {
                        if (x == 0 && z == 0) { if (el01 && el00 && el10) b = grassChar; }
                        else if (x == 15 && z == 0) { if (el21 && el20 && el10) b = grassChar; }
                        else if (x == 0 && z == 15) { if (el01 && el02 && el12) b = grassChar; }
                        else if (x == 15 && z == 15) { if (el12 && el22 && el21) b = grassChar; }
                        else if (x == 0) { if (el01) b = grassChar; }
                        else if (x == 15) { if (el21) b = grassChar; }
                        else if (z == 0) { if (el10) b = grassChar; }
                        else if (z == 15) { if (el12) b = grassChar; }
                    }
                } else {
                    b = grassChar;
                }
                driver.current(x, height, z).block(b);
            }
        }
    }

    private void generateFullStreetSection(int height) {
        for (int x = 0; x < 16; ++x)
            for (int z = 0; z < 16; ++z)
                driver.current(x, height, z).block(isSide(x, z) ? street : street2);
    }

    private void generateNormalStreetSection(BuildingInfo info, int height) {
        char defaultStreet = streetBase;
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                char b = defaultStreet;
                if (isStreetBorder(x, z)) {
                    if (x <= streetBorder && z > streetBorder && z < (15 - streetBorder)
                        && (BuildingInfo.hasRoadConnection(info, info.getXmin()) || (info.getXmin().hasXBridge() != null))) {
                        b = street;
                    } else if (x >= (15 - streetBorder) && z > streetBorder && z < (15 - streetBorder)
                        && (BuildingInfo.hasRoadConnection(info, info.getXmax()) || (info.getXmax().hasXBridge() != null))) {
                        b = street;
                    } else if (z <= streetBorder && x > streetBorder && x < (15 - streetBorder)
                        && (BuildingInfo.hasRoadConnection(info, info.getZmin()) || (info.getZmin().hasZBridge() != null))) {
                        b = street;
                    } else if (z >= (15 - streetBorder) && x > streetBorder && x < (15 - streetBorder)
                        && (BuildingInfo.hasRoadConnection(info, info.getZmax()) || (info.getZmax().hasZBridge() != null))) {
                        b = street;
                    }
                } else {
                    b = street;
                }
                driver.current(x, height, z).block(b);
            }
        }
    }

    private boolean borderNeedsConnectionToAdjacentChunk(BuildingInfo info, int x, int z) {
        for (Direction direction : Direction.VALUES) {
            if (direction.atSide(x, z)) {
                BuildingInfo adjacent = direction.get(info);
                if (adjacent.getActualStairDirection() == direction.getOpposite()) {
                    BuildingPart stairType = adjacent.stairType;
                    Integer z1 = stairType.getMetaInteger("z1");
                    Integer z2 = stairType.getMetaInteger("z2");
                    if (z1 != null && z2 != null) {
                        Transform transform = direction.getOpposite().getRotation();
                        int xx1 = transform.rotateX(15, z1), zz1 = transform.rotateZ(15, z1);
                        int xx2 = transform.rotateX(15, z2), zz2 = transform.rotateZ(15, z2);
                        if (x >= Math.min(xx1, xx2) && x <= Math.max(xx1, xx2) && z >= Math.min(zz1, zz2) && z <= Math.max(zz1, zz2)) return true;
                    }
                }
                if (adjacent.hasBridge(direction.getOrientation()) != null) return true;
            }
        }
        return false;
    }

    private boolean doBorder(BuildingInfo info, Direction direction) {
        BuildingInfo adjacent = direction.get(info);
        if (isHigherThenNearbyStreetChunk(info, adjacent)) return true;
        if (!adjacent.isCity) return adjacent.cityLevel <= info.cityLevel;
        return false;
    }

    private boolean isHigherThenNearbyStreetChunk(BuildingInfo info, BuildingInfo adjacent) {
        if (!adjacent.isCity) return false;
        if (adjacent.hasBuilding) return adjacent.cityLevel + adjacent.getNumFloors() < info.cityLevel;
        return adjacent.cityLevel < info.cityLevel;
    }

    private void setBlocksFromPalette(int x, int y, int z, int y2, CompiledPalette palette, Character character) {
        if (character == null) return;
        if (palette.isSimple(character)) {
            char b = palette.getOr(character, fast, airChar);
            driver.setBlockRangeSafe(x, y, z, y2, b);
        } else {
            driver.current(x, y, z);
            while (y < y2) {
                driver.add(palette.getOr(character, fast, airChar));
                y++;
            }
        }
    }

    // ------------------------------------------------------------------ parts

    /**
     * Generate a part. If 'airWaterLevel' is true then 'hard air' blocks are replaced with water below the waterLevel.
     * Otherwise they are replaced with air. Returns the height above the part.
     */
    private int generatePart(BuildingInfo info, BuildingPart part, Transform transform, int ox, int oy, int oz, boolean airWaterLevel) {
        if (part == null) return oy;
        CompiledPalette compiledPalette = info.getCompiledPalette();
        Palette localPalette = part.getLocalPalette();
        if (localPalette != null) compiledPalette = new CompiledPalette(compiledPalette, localPalette);
        boolean nowater = part.getMetaBoolean("nowater");

        for (int x = 0; x < part.xSize; x++) {
            for (int z = 0; z < part.zSize; z++) {
                char[] vs = part.getVSlice(x, z);
                if (vs == null) continue;
                int rx = ox + transform.rotateX(x, z);
                int rz = oz + transform.rotateZ(x, z);
                driver.current(rx, oy, rz);
                int len = vs.length;
                for (int y = 0; y < len; y++) {
                    char c = vs[y];
                    Character bb = compiledPalette.get(c, fast);
                    if (bb == null) throw new IllegalStateException("Could not find entry '" + c + "' in the palette for part '" + part.name + "'!");
                    char b = bb;
                    CompiledPalette.Info inf = compiledPalette.getInfo(c);

                    if (transform != Transform.ROTATE_NONE) {
                        if (B.isRotatable(b)) b = B.rotate(b, transform.mcRotation);
                        else if (B.isRail(b)) b = transform.railChar(b);
                    }
                    // The world is not replaced where the part is empty (air)
                    if (b != airChar) {
                        if (b == liquidChar) {
                            if (Profile.AVOID_WATER) b = airChar;
                        } else if ((b >> 4) == 137) {   // hard air (command_block in any facing)
                            if (airWaterLevel && !Profile.AVOID_WATER && !nowater) b = (oy + y) < info.waterLevel ? liquidChar : airChar;
                            else b = airChar;
                        } else if (inf != null) {
                            if (inf.torchOrientations != null) {
                                if (!Profile.GENERATE_LIGHTING) b = airChar;   // no torches
                            } else if (inf.loot != null && !inf.loot.isEmpty()) {
                                if (!info.noLoot) result.loot.add(new Todo(rx, oy + y, rz, inf.loot, part.name, info.hasBuilding ? info.getBuildingType() : "<none>"));
                            } else if (inf.mobId != null && !inf.mobId.isEmpty()) {
                                if (Profile.GENERATE_SPAWNERS && !info.noLoot) result.spawners.add(new Todo(rx, oy + y, rz, inf.mobId, part.name, info.hasBuilding ? info.getBuildingType() : "<none>"));
                                else b = airChar;
                            }
                        } else if (B.needsTodo(b)) {
                            if (Profile.AVOID_FOLIAGE) b = airChar;
                            else if ((b >> 4) == 6) result.saplings.add(new Todo(rx, oy + y, rz, null, part.name, null));
                        }
                        if ((b >> 4) == 137) b = airChar;   // never placed
                        driver.add(b);
                    } else {
                        driver.incY();
                    }
                }
            }
        }
        return oy + part.getSliceCount();
    }

    // ------------------------------------------------------------------ buildings

    private void generateBuilding(BuildingInfo info) {
        int lowestLevel = info.getCityGroundLevel() - info.floorsBelowGround * 6;
        Character borderBlock = info.getCityStyle().borderBlock;
        CompiledPalette palette = info.getCompiledPalette();
        char fillerBlock = info.buildingType.fillerBlock;
        result.feature("building:" + info.buildingType.name + (info.multiBuilding != null ? " multi:" + info.multiBuilding.name : ""));

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                if (isSide(x, z)) {
                    driver.setBlockRange(x, Profile.BEDROCK_LAYER, z, lowestLevel - 10, baseChar);
                    int y = lowestLevel - 10;
                    driver.current(x, y, z);
                    while (y < lowestLevel) {
                        driver.add(pal(palette, borderBlock));
                        y++;
                    }
                } else {
                    driver.setBlockRange(x, Profile.BEDROCK_LAYER, z, lowestLevel, baseChar);
                }
                if (driver.getBlock(x, lowestLevel, z) == airChar) {
                    char filler = pal(palette, fillerBlock);
                    driver.current(x, lowestLevel, z).block(filler);
                }
            }
        }

        int height = lowestLevel;
        for (int f = -info.floorsBelowGround; f <= info.getNumFloors(); f++) {
            BuildingPart part = info.getFloor(f);
            generatePart(info, part, Transform.ROTATE_NONE, 0, height, 0, false);
            part = info.getFloorPart2(f);
            if (part != null) generatePart(info, part, Transform.ROTATE_NONE, 0, height, 0, false);
            boolean isTop = f == info.getNumFloors();
            if (!isTop) generateDoors(info, height + 1, f);
            height += 6;
        }

        if (info.floorsBelowGround > 0) {
            for (int x = 0; x < 16; x++) {
                setBlocksFromPalette(x, lowestLevel, 0, Math.min(info.getCityGroundLevel(), info.getZmin().getCityGroundLevel()) + 1, palette, fillerBlock);
                setBlocksFromPalette(x, lowestLevel, 15, Math.min(info.getCityGroundLevel(), info.getZmax().getCityGroundLevel()) + 1, palette, fillerBlock);
            }
            for (int z = 1; z < 15; z++) {
                setBlocksFromPalette(0, lowestLevel, z, Math.min(info.getCityGroundLevel(), info.getXmin().getCityGroundLevel()) + 1, palette, fillerBlock);
                setBlocksFromPalette(15, lowestLevel, z, Math.min(info.getCityGroundLevel(), info.getXmax().getCityGroundLevel()) + 1, palette, fillerBlock);
            }
        }
        if (info.floorsBelowGround >= 1) generateCorridorConnections(info);
    }

    /** Door halves as BLOCK_STATE_IDS: lower = facing.rotateY() horizontal index, upper = 8 | hinge(right). */
    private static char getDoor(int door, boolean upper, boolean left, char facing) {
        if (upper) return B.c(door, 8 | (left ? 0 : 1));
        int idx;
        switch (facing) {
            case 'E': idx = 0; break;   // EAST.rotateY() = SOUTH (0)
            case 'S': idx = 1; break;   // SOUTH.rotateY() = WEST (1)
            case 'W': idx = 2; break;   // WEST.rotateY() = NORTH (2)
            default: idx = 3; break;    // NORTH.rotateY() = EAST (3)
        }
        return B.c(door, idx);
    }

    private void generateDoors(BuildingInfo info, int height, int f) {
        char filler = pal(info.getCompiledPalette(), info.buildingType.fillerBlock);
        height--;
        int door = info.doorBlock;
        if (info.hasConnectionAtX(f + info.floorsBelowGround)) {
            int x = 0;
            if (hasConnectionWithBuilding(f, info, info.getXmin())) {
                driver.setBlockRange(x, height, 6, height + 4, filler);
                driver.setBlockRange(x, height, 9, height + 4, filler);
                driver.current(x, height, 7).add(filler).add(airChar).add(airChar).add(filler);
                driver.current(x, height, 8).add(filler).add(airChar).add(airChar).add(filler);
            } else if (hasConnectionToTopOrOutside(f, info, info.getXmin())) {
                driver.setBlockRange(x, height, 6, height + 4, filler);
                driver.setBlockRange(x, height, 9, height + 4, filler);
                driver.current(x, height, 7).add(filler).add(getDoor(door, false, true, 'E')).add(getDoor(door, true, true, 'E')).add(filler);
                driver.current(x, height, 8).add(filler).add(getDoor(door, false, false, 'E')).add(getDoor(door, true, false, 'E')).add(filler);
            }
        }
        if (hasConnectionWithBuildingMax(f, info, info.getXmax(), Orientation.X)) {
            int x = 15;
            driver.setBlockRange(x, height, 6, height + 4, filler);
            driver.setBlockRange(x, height, 9, height + 4, filler);
            driver.current(x, height, 7).add(filler).add(airChar).add(airChar).add(filler);
            driver.current(x, height, 8).add(filler).add(airChar).add(airChar).add(filler);
        } else if (hasConnectionToTopOrOutside(f, info, info.getXmax()) && (info.getXmax().hasConnectionAtXFromStreet(f + info.getXmax().floorsBelowGround))) {
            int x = 15;
            driver.setBlockRange(x, height, 6, height + 4, filler);
            driver.setBlockRange(x, height, 9, height + 4, filler);
            driver.current(x, height, 7).add(filler).add(getDoor(door, false, false, 'W')).add(getDoor(door, true, false, 'W')).add(filler);
            driver.current(x, height, 8).add(filler).add(getDoor(door, false, true, 'W')).add(getDoor(door, true, true, 'W')).add(filler);
        }
        if (info.hasConnectionAtZ(f + info.floorsBelowGround)) {
            int z = 0;
            if (hasConnectionWithBuilding(f, info, info.getZmin())) {
                driver.setBlockRange(6, height, z, height + 4, filler);
                driver.setBlockRange(9, height, z, height + 4, filler);
                driver.current(7, height, z).add(filler).add(airChar).add(airChar).add(filler);
                driver.current(8, height, z).add(filler).add(airChar).add(airChar).add(filler);
            } else if (hasConnectionToTopOrOutside(f, info, info.getZmin())) {
                driver.setBlockRange(6, height, z, height + 4, filler);
                driver.setBlockRange(9, height, z, height + 4, filler);
                driver.current(7, height, z).add(filler).add(getDoor(door, false, true, 'N')).add(getDoor(door, true, true, 'N')).add(filler);
                driver.current(8, height, z).add(filler).add(getDoor(door, false, false, 'N')).add(getDoor(door, true, false, 'N')).add(filler);
            }
        }
        if (hasConnectionWithBuildingMax(f, info, info.getZmax(), Orientation.Z)) {
            int z = 15;
            driver.setBlockRange(6, height, z, height + 4, filler);
            driver.setBlockRange(9, height, z, height + 4, filler);
            driver.current(7, height, z).add(filler).add(airChar).add(airChar).add(filler);
            driver.current(8, height, z).add(filler).add(airChar).add(airChar).add(filler);
        } else if (hasConnectionToTopOrOutside(f, info, info.getZmax()) && (info.getZmax().hasConnectionAtZFromStreet(f + info.getZmax().floorsBelowGround))) {
            int z = 15;
            driver.setBlockRange(6, height, z, height + 4, filler);
            driver.setBlockRange(9, height, z, height + 4, filler);
            driver.current(7, height, z).add(filler).add(getDoor(door, false, false, 'S')).add(getDoor(door, true, false, 'S')).add(filler);
            driver.current(8, height, z).add(filler).add(getDoor(door, false, true, 'S')).add(getDoor(door, true, true, 'S')).add(filler);
        }
    }

    private void generateCorridorConnections(BuildingInfo info) {
        if (info.getXmin().hasXCorridor()) for (int z = 7; z <= 10; z++) driver.setBlockRange(0, info.groundLevel - 5, z, info.groundLevel - 2, airChar);
        if (info.getXmax().hasXCorridor()) for (int z = 7; z <= 10; z++) driver.setBlockRange(15, info.groundLevel - 5, z, info.groundLevel - 2, airChar);
        if (info.getZmin().hasXCorridor()) for (int x = 7; x <= 10; x++) driver.setBlockRange(x, info.groundLevel - 5, 0, info.groundLevel - 2, airChar);
        if (info.getZmax().hasXCorridor()) for (int x = 7; x <= 10; x++) driver.setBlockRange(x, info.groundLevel - 5, 15, info.groundLevel - 2, airChar);
    }

    private boolean hasConnectionWithBuildingMax(int localLevel, BuildingInfo info, BuildingInfo info2, Orientation x) {
        if (info.isValidFloor(localLevel) && info.getFloor(localLevel).getMetaBoolean("dontconnect")) return false;
        int globalLevel = info.localToGlobal(localLevel);
        int localAdjacent = info2.globalToLocal(globalLevel);
        if (info2.isValidFloor(localAdjacent) && info2.getFloor(localAdjacent).getMetaBoolean("dontconnect")) return false;
        int level = localAdjacent + info2.floorsBelowGround;
        return info2.hasBuilding && ((localAdjacent >= 0 && localAdjacent < info2.getNumFloors()) || (localAdjacent < 0 && (-localAdjacent) <= info2.floorsBelowGround)) && info2.hasConnectionAt(level, x);
    }

    private boolean hasConnectionToTopOrOutside(int localLevel, BuildingInfo info, BuildingInfo info2) {
        int globalLevel = info.localToGlobal(localLevel);
        int localAdjacent = info2.globalToLocal(globalLevel);
        if (info.getFloor(localLevel).getMetaBoolean("dontconnect")) return false;
        return (info2.isCity && !info2.hasBuilding && localLevel == 0 && localAdjacent == 0) || (info2.hasBuilding && localAdjacent == info2.getNumFloors());
    }

    private boolean hasConnectionWithBuilding(int localLevel, BuildingInfo info, BuildingInfo info2) {
        int globalLevel = info.localToGlobal(localLevel);
        int localAdjacent = info2.globalToLocal(globalLevel);
        return info2.hasBuilding && ((localAdjacent >= 0 && localAdjacent < info2.getNumFloors()) || (localAdjacent < 0 && (-localAdjacent) <= info2.floorsBelowGround));
    }

    private static boolean isSide(int x, int z) { return x == 0 || x == 15 || z == 0 || z == 15; }

    private boolean isStreetBorder(int x, int z) {
        return x <= streetBorder || x >= (15 - streetBorder) || z <= streetBorder || z >= (15 - streetBorder);
    }

}
