package chat.jaspr.ruins;

import chat.jaspr.biomes.Terrain;
import chat.jaspr.lostcities.CityApi;

/**
 * Ages the Lost Cities built in the ruins dimension into ancient ruins, on the Lost Cities' primer before anything is
 * written: moss and cracks, stone for modern materials, shattered windows, stripped interiors, upper floors collapsed
 * into a jagged skyline with rubble below, overgrown streets, vines and cobwebs. Loot and spawners are placed by the
 * Lost Cities afterwards only where their block survived.
 */
final class Weathering implements CityApi.PrimerHook {
    private static final long SALT = 0x416E6369656E74L;   // "Ancient"
    static volatile long chunks;
    private final Terrain terrain;

    Weathering(long seed) { this.terrain = new Terrain(seed); }

    private static int idx(int x, int y, int z) { return x << 12 | z << 8 | y; }
    private static char c(int id, int data) { return (char) (id << 4 | data); }

    private static boolean solid(int id) {
        switch (id) {
            case 0: case 8: case 9: case 10: case 11: case 18: case 161: case 106: case 31: case 175: case 30: case 50:
            case 65: case 37: case 38: case 39: case 40: case 32: case 78: case 171: case 66: case 27: case 28: case 157:
                return false;
            default: return true;
        }
    }

    @Override
    public void apply(int cx, int cz, char[] p, boolean city, int ground) {
        chunks++;
        long chunkHash = Hash.of(SALT, cx, cz);
        if (city && ground > 0) erode(cx, cz, p, ground, chunkHash);
        regreen(cx, cz, p, city ? ground : -1);
        convert(cx, cz, p, city ? ground : -1);
        overgrow(cx, cz, p, city ? ground : -1);
        vines(cx, cz, p, city ? ground : 0);
    }

    /** Upper floors come down: a jagged ceiling per column, higher in some chunks, with rubble on the ground. */
    private void erode(int cx, int cz, char[] p, int ground, long chunkHash) {
        int keep = 3 + Hash.range(chunkHash, 0, 6);
        int span = 6 + Hash.range(Hash.mix(chunkHash), 0, 44);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                double n = Hash.noise(SALT ^ 0x51L, wx, wz, 6.0);
                int ceil = ground + keep + (int) (span * n) + (int) (Hash.unit(SALT, wx, 7, wz) * 3);
                boolean removed = false;
                for (int y = Math.min(255, ceil); y < 256; y++) {
                    int i = idx(x, y, z);
                    if (p[i] == 0) continue;
                    if (y == ceil && Hash.unit(SALT, wx, y, wz) < 0.5) continue;   // a ragged edge, not a clean cut
                    p[i] = 0;
                    removed = true;
                }
                if (removed && ground + 1 < 255 && p[idx(x, ground + 1, z)] == 0 && solid(p[idx(x, ground, z)] >> 4)) {
                    double r = Hash.unit(SALT ^ 0x77L, wx, ground, wz);
                    if (r < 0.18) p[idx(x, ground + 1, z)] = c(48, 0);
                    else if (r < 0.28) p[idx(x, ground + 1, z)] = c(13, 0);
                    else if (r < 0.33) p[idx(x, ground + 1, z)] = c(44, 3);   // a fallen cobble slab
                }
            }
    }

    /**
     * The Lost Cities dress open ground in the HorrorBiomes profile's own surface (clay, podzol, gravel, stained clay,
     * snow...). Here the land is green: the topmost profile surface block near ground level becomes grass, with dirt
     * under it where the profile's filler continues. Only that column's own profile blocks are touched, so buildings
     * keep their materials.
     */
    private void regreen(int cx, int cz, char[] p, int ground) {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                Terrain.Sample s = terrain.sample(cx * 16 + x, cz * 16 + z);
                int ref = ground > 0 ? ground : s.y, lo = Math.max(2, ref - 4), y = Math.min(250, ref + 3);
                while (y >= lo && (p[idx(x, y, z)] == 0 || !solid(p[idx(x, y, z)] >> 4))) y--;
                if (y < lo) continue;
                char top = c(s.profile.surface, s.profile.surfaceData), fill = c(s.profile.under, s.profile.underData);
                char ch = p[idx(x, y, z)];
                if (ch != top && ch != fill) continue;
                int above = p[idx(x, y + 1, z)] >> 4;
                if (above == 8 || above == 9) continue;   // sea and lake beds keep their floor
                p[idx(x, y, z)] = c(2, 0);
                for (int k = 1; k <= 3 && y - k > 1; k++) {
                    char b = p[idx(x, y - k, z)];
                    if (b != top && b != fill) break;
                    p[idx(x, y - k, z)] = c(3, 0);
                }
            }
    }

    /** Materials age: modern blocks become old masonry, glass shatters, soft furnishings rot away. */
    private void convert(int cx, int cz, char[] p, int ground) {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                int top = 255;
                for (int y = 1; y <= top; y++) {
                    int i = idx(x, y, z);
                    char ch = p[i];
                    if (ch == 0) continue;
                    int id = ch >> 4, data = ch & 15;
                    boolean structure = ground < 0 || y >= ground - 1;   // never rework the bedrock-to-street rock
                    double r = Hash.unit(SALT ^ 0x13L, wx, y, wz);
                    switch (id) {
                        case 4: if (structure && r < 0.55) p[i] = c(48, 0); break;
                        case 98: if (data == 0) { if (r < 0.42) p[i] = c(98, 1); else if (r < 0.66) p[i] = c(98, 2); } break;
                        case 139: if (data == 0 && r < 0.6) p[i] = c(139, 1); break;
                        case 20: case 95: case 102: case 160:
                            if (r < 0.86) p[i] = 0; else if (r < 0.90) p[i] = c(30, 0);
                            break;
                        case 101: if (r < 0.6) p[i] = 0; break;
                        case 155: case 251: case 252: case 42: case 41: case 57: case 133:
                            p[i] = r < 0.45 ? c(98, 1) : r < 0.70 ? c(98, 2) : c(98, 0);
                            break;
                        case 156: p[i] = c(109, data); break;
                        case 44: if (data == 7) p[i] = c(44, 5); else if (data == 15) p[i] = c(44, 13); break;
                        case 43: if (data == 7) p[i] = c(43, 5); break;
                        case 35: case 171: case 26: case 64: case 71: case 193: case 194: case 195: case 196: case 197:
                        case 96: case 167: case 50: case 75: case 76: case 63: case 68: case 140: case 69: case 77: case 143:
                        case 55: case 93: case 94: case 149: case 150: case 151: case 178: case 70: case 72: case 147: case 148:
                            p[i] = 0;
                            break;
                        case 89: case 169: case 123: case 124: case 91: case 138:
                            p[i] = r < 0.5 ? 0 : c(98, 2);
                            break;
                        case 66: case 27: case 28: case 157: if (r < 0.7) p[i] = 0; break;
                        case 5: if (structure && r < 0.18) p[i] = 0; break;
                        case 53: case 134: case 135: case 136: case 163: case 164: if (r < 0.15) p[i] = 0; break;
                        case 85: case 188: case 189: case 190: case 191: case 192: if (r < 0.3) p[i] = 0; break;
                        case 58: case 61: case 62: case 116: case 117: case 145: case 118: case 130: case 23: case 158: case 154:
                            p[i] = r < 0.7 ? 0 : c(48, 0);
                            break;
                        case 110: case 88: p[i] = c(2, 0); break;
                        case 87: p[i] = c(48, 0); break;
                        case 174: case 79: case 80: case 78: p[i] = y > 0 && id == 78 ? 0 : c(3, 1); break;
                        default: break;
                    }
                }
            }
    }

    /** Streets and floors open to the sky turn to turf; ferns, grass and the odd shrub push through. */
    private void overgrow(int cx, int cz, char[] p, int ground) {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                int top = -1;
                for (int y = 254; y > 0; y--) if (p[idx(x, y, z)] != 0 && solid(p[idx(x, y, z)] >> 4)) { top = y; break; }
                if (top < 1) continue;
                int id = p[idx(x, top, z)] >> 4;
                double r = Hash.unit(SALT ^ 0x29L, wx, top, wz);
                boolean street = ground < 0 ? top < 90 : Math.abs(top - ground) <= 1;
                if (street && (id == 43 || id == 44 || id == 1 || id == 13 || id == 4 || id == 48 || id == 98 || id == 159 || id == 172 || id == 24 || id == 12)) {
                    if (r < 0.34) p[idx(x, top, z)] = c(2, 0);
                    else if (r < 0.42) p[idx(x, top, z)] = c(3, 1);
                    else if (r < 0.50) p[idx(x, top, z)] = c(48, 0);
                    id = p[idx(x, top, z)] >> 4;
                }
                if (top >= 254 || p[idx(x, top + 1, z)] != 0) continue;
                double g = Hash.unit(SALT ^ 0x3BL, wx, top, wz);
                if (id == 2) {
                    if (g < 0.22) p[idx(x, top + 1, z)] = c(31, 1);
                    else if (g < 0.30) p[idx(x, top + 1, z)] = c(31, 2);
                    else if (g < 0.32 && top < 253 && p[idx(x, top + 2, z)] == 0) { p[idx(x, top + 1, z)] = c(175, 2); p[idx(x, top + 2, z)] = c(175, 8); }
                    else if (g < 0.335) p[idx(x, top + 1, z)] = c(18, 4);
                } else if (top > (ground < 0 ? 64 : ground + 3) && g < 0.03) {
                    p[idx(x, top + 1, z)] = c(18, 4);   // moss-and-leaf growth on a ruined wall top
                }
            }
    }

    /** Vines hang from exposed walls; cobwebs gather in sheltered corners. */
    private void vines(int cx, int cz, char[] p, int floor) {
        int[][] sides = {{1, 0, 2}, {-1, 0, 8}, {0, 1, 4}, {0, -1, 1}};   // air cell offset, vine meta toward the wall
        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                for (int y = Math.max(2, floor + 2); y < 250; y++) {
                    char ch = p[idx(x, y, z)];
                    if (ch == 0 || !solid(ch >> 4)) continue;
                    for (int[] s : sides) {
                        int ax = x + s[0], az = z + s[1];
                        if (p[idx(ax, y, az)] != 0) continue;
                        if (Hash.unit(SALT ^ 0x61L, wx * 4 + s[0], y, wz * 4 + s[1]) >= 0.035) continue;
                        int length = 2 + (int) (Hash.unit(SALT ^ 0x62L, wx, y, wz) * 6);
                        for (int k = 0; k < length && y - k > 1; k++) {
                            int i = idx(ax, y - k, az);
                            if (p[i] != 0) break;
                            p[i] = c(106, s[2]);
                        }
                    }
                }
                if (floor > 0) for (int y = floor + 1; y < Math.min(250, floor + 24); y++) {
                    int i = idx(x, y, z);
                    if (p[i] != 0 || p[idx(x, y + 1, z)] == 0 || !solid(p[idx(x, y + 1, z)] >> 4)) continue;
                    int walls = 0;
                    if (p[idx(x + 1, y, z)] != 0) walls++;
                    if (p[idx(x - 1, y, z)] != 0) walls++;
                    if (p[idx(x, y, z + 1)] != 0) walls++;
                    if (p[idx(x, y, z - 1)] != 0) walls++;
                    if (walls >= 2 && Hash.unit(SALT ^ 0x71L, wx, y, wz) < 0.012) p[i] = c(30, 0);
                }
            }
    }
}
