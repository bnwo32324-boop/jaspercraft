package chat.jaspr.biomes;

import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * What each cave region is made of. The carving gives the five styles different
 * shapes; this gives them different materials, so walking from one into the next
 * reads as crossing a border rather than as the tunnels merely bending.
 *
 * Everything here is placed against a surface the carve pass already made: floors
 * take the block under an air gap, ceilings the block over one. Nothing is read
 * from the world, and every roll is derived from the block position, so two
 * chunks decorate their shared border identically.
 */
public final class CaveDecor {
    private CaveDecor() { }

    private static final int AIR = 0, STONE = 1, GRASS_PATH = 198;

    public static void decorate(ChunkData d, Terrain t, Caves caves, int cx, int cz,
                                int[][] heights, int[][] lattice) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                int style = Caves.styleAt(lattice, x, z);
                int ground = heights[x][z];
                int top = Math.min(Caves.CEILING, ground - 4);
                for (int y = Caves.FLOOR + 1; y <= top; y++) {
                    if (d.getTypeId(x, y, z) != AIR) continue;
                    boolean floor = solid(d, x, y - 1, z);
                    boolean ceiling = solid(d, x, y + 1, z);
                    if (!floor && !ceiling) continue;
                    if (floor) floor(d, t, style, x, y, z, wx, wz);
                    if (ceiling) ceiling(d, t, style, x, y, z, wx, wz);
                }
            }
        }
    }

    private static boolean solid(ChunkData d, int x, int y, int z) {
        if (y < 1 || y > 254) return false;
        int id = d.getTypeId(x, y, z);
        return id != AIR && id != 8 && id != 9 && id != 10 && id != 11;
    }

    private static void floor(ChunkData d, Terrain t, int style, int x, int y, int z, int wx, int wz) {
        double r = t.random(wx, wz + y * 7919, 90 + style);
        double s = t.random(wx + y * 5417, wz, 120 + style);
        switch (style) {
            case Caves.CATHEDRAL:
                // Dressed stone underfoot, as though something built here first.
                if (r < 0.40) skin(d, x, y - 1, z, 98, (byte) (s < 0.45 ? 1 : s < 0.8 ? 2 : 0));
                else if (r < 0.46) skin(d, x, y - 1, z, 4, (byte) 0);
                if (y < 26 && r > 0.93) set(d, x, y, z, 9, (byte) 0);
                else if (r > 0.9985) set(d, x, y, z, 169, (byte) 0);
                break;
            case Caves.EMBERVEINS:
                if (r < 0.34) skin(d, x, y - 1, z, 87, (byte) 0);
                else if (r < 0.40) skin(d, x, y - 1, z, 213, (byte) 0);
                else if (r < 0.43) skin(d, x, y - 1, z, 49, (byte) 0);
                if (r > 0.9975) set(d, x, y, z, 89, (byte) 0);
                break;
            case Caves.FROSTBORE:
                if (r < 0.42) skin(d, x, y - 1, z, 174, (byte) 0);
                else if (r < 0.58) skin(d, x, y - 1, z, 79, (byte) 0);
                if (r > 0.80 && r < 0.90) set(d, x, y, z, 78, (byte) 0);
                break;
            case Caves.FUNGAL:
                if (r < 0.38) skin(d, x, y - 1, z, 110, (byte) 0);
                else if (r < 0.55) skin(d, x, y - 1, z, 3, (byte) 2);
                if (r > 0.965 && r < 0.985) set(d, x, y, z, s < 0.5 ? 39 : 40, (byte) 0);
                else if (r >= 0.985) mushroom(d, t, x, y, z, wx, wz, s);
                break;
            default:
                if (r < 0.30) skin(d, x, y - 1, z, 48, (byte) 0);
                else if (r < 0.40) skin(d, x, y - 1, z, 4, (byte) 0);
                else if (r < 0.48) skin(d, x, y - 1, z, 13, (byte) 0);
                if (r > 0.975 && r < 0.986) set(d, x, y, z, s < 0.6 ? 39 : 40, (byte) 0);
                break;
        }
    }

    private static void ceiling(ChunkData d, Terrain t, int style, int x, int y, int z, int wx, int wz) {
        double r = t.random(wx + 31, wz - y * 6151, 150 + style);
        switch (style) {
            case Caves.CATHEDRAL:
                if (r < 0.22) skin(d, x, y + 1, z, 98, (byte) 1);
                break;
            case Caves.EMBERVEINS:
                if (r < 0.24) skin(d, x, y + 1, z, 87, (byte) 0);
                else if (r < 0.255) skin(d, x, y + 1, z, 213, (byte) 0);
                break;
            case Caves.FROSTBORE:
                if (r < 0.26) skin(d, x, y + 1, z, 79, (byte) 0);
                break;
            case Caves.FUNGAL:
                if (r < 0.18) skin(d, x, y + 1, z, 99, (byte) 14);
                break;
            default:
                // Warrens are the cobweb ones. Sparse, or they become unwalkable.
                if (r < 0.030) set(d, x, y, z, 30, (byte) 0);
                else if (r < 0.20) skin(d, x, y + 1, z, 48, (byte) 0);
                break;
        }
    }

    /** A small giant mushroom: stalk, cap ring, cap top. Clipped at the chunk edge. */
    private static void mushroom(ChunkData d, Terrain t, int x, int y, int z, int wx, int wz, double s) {
        int height = 2 + (int) (s * 3);
        boolean red = t.random(wx * 3, wz * 5, 181) < 0.5;
        int cap = red ? 100 : 99;
        for (int i = 0; i < height; i++) {
            if (d.getTypeId(x, y + i, z) != AIR) return;
            set(d, x, y + i, z, cap, (byte) 10);
        }
        int capY = y + height;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int lx = x + a, lz = z + b;
                if (lx < 0 || lx > 15 || lz < 0 || lz > 15) continue;
                if (d.getTypeId(lx, capY, lz) != AIR) continue;
                int data = a == 0 && b == 0 ? 5 : a == -1 && b == -1 ? 1 : a == -1 && b == 1 ? 3
                    : a == 1 && b == -1 ? 7 : a == 1 && b == 1 ? 9 : a == -1 ? 4 : a == 1 ? 6 : b == -1 ? 2 : 8;
                set(d, lx, capY, lz, cap, (byte) data);
            }
        }
    }

    private static void set(ChunkData d, int x, int y, int z, int id, byte data) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1 || y > 250) return;
        d.setBlock(x, y, z, id, data);
    }

    /** Dressing a wall must never paint over a seam: half the point of veins is
     *  seeing them in the rock face. Only plain stone is ever re-skinned. */
    private static void skin(ChunkData d, int x, int y, int z, int id, byte data) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1 || y > 250) return;
        if (d.getTypeId(x, y, z) != STONE) return;
        d.setBlock(x, y, z, id, data);
    }
}
