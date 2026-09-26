package chat.jaspr.lostcities;

import java.util.Arrays;

/**
 * Port of driver.OptimizedDriver: a cursor over a 16x256x16 char primer indexed {@code x << 12 | z << 8 | y}.
 * Same index arithmetic as the mod (a column overflowing y=255 runs into the next column, as it did there), but
 * writes and reads outside the primer are ignored instead of crashing the chunk.
 */
final class Driver {
    char[] data;
    int current;

    void setPrimer(char[] data) { this.data = data; }

    static int index(int x, int y, int z) { return x << 12 | z << 8 | y; }

    Driver current(int x, int y, int z) { current = index(x, y, z); return this; }
    Driver current(int index) { current = index; return this; }
    int getCurrent() { return current; }

    void incY() { current++; }
    void incY(int amount) { current += amount; }
    void decY() { current--; }
    void incX() { current += 1 << 12; }
    void incZ() { current += 1 << 8; }

    int getX() { return (current >> 12) & 0xf; }
    int getY() { return current & 0xff; }
    int getZ() { return (current >> 8) & 0xf; }

    private static boolean ok(int i) { return i >= 0 && i < 65536; }

    void setBlockRange(int x, int y, int z, int y2, char c) {
        if (y2 <= y) return;
        int s = index(x, y, z), e = s + y2 - y;
        if (s < 0) s = 0;
        if (e > 65536) e = 65536;
        if (s < e) Arrays.fill(data, s, e, c);
    }

    void setBlockRangeSafe(int x, int y, int z, int y2, char c) { setBlockRange(x, y, z, y2, c); }

    Driver block(char c) { if (ok(current)) data[current] = c; return this; }

    Driver add(char c) { if (ok(current)) data[current] = c; current++; return this; }

    char getBlock() { return ok(current) ? data[current] : B.AIR; }
    char getBlockDown() { return ok(current - 1) ? data[current - 1] : B.AIR; }
    char getBlockEast() { return ok(current + (1 << 12)) ? data[current + (1 << 12)] : B.AIR; }
    char getBlockWest() { return ok(current - (1 << 12)) ? data[current - (1 << 12)] : B.AIR; }
    char getBlockSouth() { return ok(current + (1 << 8)) ? data[current + (1 << 8)] : B.AIR; }
    char getBlockNorth() { return ok(current - (1 << 8)) ? data[current - (1 << 8)] : B.AIR; }

    char getBlock(int x, int y, int z) { int i = index(x, y, z); return ok(i) ? data[i] : B.AIR; }

    Driver copy() { Driver d = new Driver(); d.data = data; d.current = current; return d; }
}
