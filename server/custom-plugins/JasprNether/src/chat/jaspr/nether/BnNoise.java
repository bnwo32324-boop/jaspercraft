package chat.jaspr.nether;

/**
 * BetterNether 0.1.8.6 noise, ported: the permutation table, the dither and the distorted 3D Worley "ID" noise that
 * picks a BetterNether biome per cell (100 blocks horizontal / 32 vertical by default, so biomes stack vertically).
 * Cells use floor() instead of the mod's (int) truncation to avoid the doubled cell around the origin.
 */
final class BnNoise {
    private BnNoise() {}

    static final char[] TABLE = {
        404, 350, 334, 86, 283, 55, 174, 90, 30, 137, 363, 456, 303, 445, 107, 103, 32, 471, 29, 48, 237, 2, 466, 25, 194, 182, 76, 3, 365, 222, 395, 35,
        80, 418, 438, 212, 351, 98, 20, 39, 235, 188, 433, 43, 34, 461, 348, 213, 49, 510, 59, 274, 393, 10, 195, 74, 117, 446, 157, 377, 465, 314, 304, 406,
        402, 310, 375, 200, 388, 505, 6, 290, 17, 506, 244, 338, 175, 187, 71, 97, 346, 216, 70, 128, 261, 158, 102, 113, 258, 96, 147, 124, 341, 127, 225, 499,
        60, 448, 323, 166, 378, 148, 345, 371, 149, 144, 398, 134, 82, 88, 372, 485, 498, 320, 476, 379, 474, 396, 460, 397, 192, 294, 453, 9, 458, 295, 36, 417,
        426, 203, 232, 414, 385, 509, 403, 198, 269, 125, 298, 226, 390, 140, 151, 367, 322, 173, 16, 473, 321, 155, 165, 285, 37, 308, 169, 357, 206, 455, 126, 0,
        111, 481, 15, 201, 411, 129, 380, 464, 176, 278, 429, 207, 186, 234, 13, 108, 492, 61, 329, 267, 317, 209, 254, 233, 362, 472, 220, 185, 307, 40, 189, 12,
        299, 145, 407, 257, 221, 66, 214, 199, 374, 52, 324, 218, 133, 130, 413, 255, 463, 391, 119, 408, 419, 330, 57, 276, 251, 259, 1, 420, 184, 123, 296, 229,
        18, 452, 42, 45, 284, 300, 416, 305, 504, 423, 160, 266, 431, 84, 343, 159, 332, 412, 168, 170, 67, 355, 444, 163, 425, 138, 193, 256, 150, 289, 264, 46,
        152, 366, 14, 361, 292, 370, 422, 231, 399, 316, 287, 469, 28, 58, 331, 223, 392, 87, 442, 239, 291, 196, 33, 415, 263, 7, 306, 421, 489, 270, 434, 389,
        325, 497, 79, 369, 394, 180, 183, 457, 410, 109, 488, 211, 360, 447, 50, 156, 19, 475, 335, 54, 405, 242, 432, 253, 63, 479, 275, 428, 260, 31, 337, 245,
        141, 162, 358, 153, 105, 381, 116, 249, 340, 219, 135, 382, 161, 24, 480, 47, 339, 443, 191, 131, 503, 282, 342, 356, 112, 309, 230, 450, 177, 4, 56, 27,
        11, 501, 172, 383, 441, 484, 205, 349, 247, 75, 333, 486, 477, 297, 280, 164, 352, 114, 483, 459, 115, 190, 435, 44, 118, 93, 281, 26, 73, 178, 400, 279,
        301, 430, 467, 250, 8, 353, 53, 436, 69, 427, 437, 344, 387, 286, 227, 500, 91, 78, 241, 496, 22, 122, 38, 252, 424, 136, 271, 265, 197, 313, 81, 508,
        384, 311, 478, 121, 312, 493, 94, 51, 293, 224, 462, 146, 142, 68, 215, 202, 154, 204, 248, 217, 494, 139, 319, 143, 373, 106, 77, 95, 440, 167, 386, 487,
        354, 454, 347, 110, 328, 302, 99, 409, 507, 482, 495, 243, 210, 449, 277, 41, 288, 326, 101, 490, 315, 268, 85, 468, 228, 83, 100, 376, 401, 208, 511, 327,
        72, 336, 179, 62, 246, 89, 273, 439, 318, 236, 65, 451, 171, 104, 23, 238, 132, 364, 120, 64, 262, 181, 5, 272, 359, 491, 502, 470, 368, 240, 92, 21
    };

    /** The mod's seeded permutation table (seed only shifts the lookups). */
    static final class Perm {
        private final int i1, i2;
        Perm(long seed) {
            int a = (int) (seed & 511);
            i1 = a;
            i2 = (int) ((seed - (a << 8)) & 511);
        }
        int pos(int x, int y) { return TABLE[(TABLE[(x + i1) & 511] + y + i2) & 511]; }
        int pos(int x, int y, int z) { return TABLE[(TABLE[(TABLE[(x + i1) & 511] + y) & 511] + z + i2) & 511]; }
        double real(int x, int y) { return pos(x, y) / 511.0; }
    }

    static final class Dither {
        private final Perm dx, dy, dz;
        Dither(long seed) { dx = new Perm(seed + 41L); dy = new Perm(seed + 59L); dz = new Perm(seed + 71L); }
        int x(int x, int y, int z) { return x + (dx.pos(x, y, z) & 3); }
        int y(int x, int y, int z) { return y + (dy.pos(x, y, z) & 3); }
        int z(int x, int y, int z) { return z + (dz.pos(x, y, z) & 3); }
    }

    static final class Worley2D {
        private final Perm rx, ry;
        Worley2D(long seed) { rx = new Perm(seed); ry = new Perm(seed + 1337L); }
        double value(double x, double y) {
            int px = (int) Math.floor(x), py = (int) Math.floor(y);
            x -= px; y -= py;
            double d = 10;
            for (int i = -1; i < 2; i++) {
                int cx = (px + i) & 255;
                for (int j = -1; j < 2; j++) {
                    int cy = (py + j) & 255;
                    double ox = x - rx.real(cx, cy) - i, oy = y - ry.real(-cy, -cx) - j;
                    double nd = ox * ox + oy * oy;
                    if (nd < d) d = nd;
                }
            }
            return Math.sqrt(d);
        }
    }

    /** WorleyNoiseOctaved: three octaves blended as in the mod. */
    static final class WorleyOctaved {
        private final Worley2D noise;
        WorleyOctaved(long seed) { noise = new Worley2D(seed); }
        double value(double x, double y) {
            double r = noise.value(x, y);
            r = noise.value(x * 4, y * 4) * 0.5 + r * 0.5;
            r = noise.value(x * 8, y * 8) * 0.25 + r * 0.75;
            return r;
        }
    }

    /** WorleyNoiseID3D: index of the nearest feature point, hashed to [0,maxId). */
    static final class WorleyId3D {
        private final Perm rx, ry, rz, ids;
        private final int maxId;
        WorleyId3D(long seed, int maxId) {
            rx = new Perm(seed); ry = new Perm(seed + 1337L); rz = new Perm(seed + 2673L); ids = new Perm(seed + 135L);
            this.maxId = Math.max(1, maxId);
        }
        int id(double x, double y, double z) {
            int px = (int) Math.floor(x), py = (int) Math.floor(y), pz = (int) Math.floor(z);
            x -= px; y -= py; z -= pz;
            int ix = px & 255, iy = py & 255, iz = pz & 255;
            double d = 100;
            for (int i = -1; i < 2; i++) {
                int cx = (px + i) & 255;
                for (int j = -1; j < 2; j++) {
                    int cy = (py + j) & 255;
                    for (int k = -1; k < 2; k++) {
                        int cz = (pz + k) & 255;
                        double ox = x - rx.real(cx, -cy) - i;
                        double oy = y - ry.real(-cz, -cx) - j;
                        double oz = z - rz.real(-cx, cy) - k;
                        double nd = ox * ox + oy * oy + oz * oz;
                        if (nd < d) { d = nd; ix = cx; iy = cy; iz = cz; }
                    }
                }
            }
            return (int) (((long) ids.pos(ix, iy, iz) * 4956823L) % maxId);
        }
    }

    /** WorleyNoiseIDDistorted3D as in the 0.1.8.6 jar: three octaved 2D Worley offsets (seeds +9/+19/+29). */
    static final class DistortedId3D {
        private final WorleyId3D idNoise;
        private final WorleyOctaved nx, ny, nz;
        DistortedId3D(long seed, int maxId) {
            idNoise = new WorleyId3D(seed, maxId);
            nx = new WorleyOctaved(seed + 9L); ny = new WorleyOctaved(seed + 19L); nz = new WorleyOctaved(seed + 29L);
        }
        int id(double x, double y, double z) {
            double dx = x + nx.value(y * 0.5, z * 0.5);
            double dy = y + ny.value(-z * 0.5, x * 0.5);
            double dz = z + nz.value(-x * 0.5, -y * 0.5);
            return idNoise.id(dx, dy, dz);
        }
    }
}
