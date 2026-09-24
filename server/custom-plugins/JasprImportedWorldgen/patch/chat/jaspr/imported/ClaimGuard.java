/*
 * JasprImportedWorldgen 1.3.0 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152 decompilation of
 * the 1.2.0 ClaimGuard, cleaned up, with one change: every JasprHorrorBiomes 3.28.0 tier-2 site is a claim too
 * (tertiary set pieces Megaliths.C_CELL3/C_SALT3 via tertiaryBase, lattice-D rooms Dungeons.D_CELL_D/D_SALT_D,
 * tier-2 portal sanctuaries StructureRates.sanctuary2 and tier-2 catalogue sites StructurePlanner.tier2Legacy /
 * tier2Region), asked by conflicts() and envelopeConflicts() after every older test, for every grid. All of it is
 * resolved by reflection in the constructor and fails closed (HorrorBiomes 3.28.0 or later required); the older
 * schema checks are unchanged. Apply the same change to the PC source.
 * Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import chat.jaspr.biomes.StructurePlanner;
import chat.jaspr.biomes.Terrain;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClaimGuard {
    private final int[] mc;
    private final int[] mx;
    private final int[] mz;
    private final int[] mh;
    private final long[] ms;
    private final Method fits;
    private final Method claimed;
    private final Method baseY;
    private final int[] dc;
    private final int[] dx;
    private final int[] dz;
    private final int[] dh;
    private final long[] da;
    private final long[] db;
    private final boolean[] surface;
    private final Method dungeonAnchor;
    private final Method dungeonSurface;
    private final Method portalChunk;
    private final Field ax;
    private final Field ay;
    private final Field az;
    private final int[] mc2;
    private final int[] dc2;
    private final long[] ms2;
    private final long[] dsc;
    private final int[] mm;
    private final Method secondaryBase;
    private final Method newSanctuary;
    private final int[][] entry;
    // 1.3.0: HorrorBiomes 3.28.0 tier 2.
    private final int[] mc3;
    private final long[] ms3;
    private final Method tertiaryBase;
    private final int[] dc3;
    private final long[] dsd;
    private final Method sanctuary2;
    private final int[][] edges;
    private final Method tier2Legacy;
    private final Method tier2Region;

    public ClaimGuard() throws ReflectiveOperationException {
        Class<?> megaliths = Class.forName("chat.jaspr.biomes.Megaliths");
        this.mc = ClaimGuard.ints(megaliths, "C_CELL");
        this.mx = ClaimGuard.ints(megaliths, "C_SX");
        this.mz = ClaimGuard.ints(megaliths, "C_SZ");
        this.mh = ClaimGuard.ints(megaliths, "C_HEIGHT");
        this.ms = ClaimGuard.longs(megaliths, "C_SALT");
        if (this.mc.length != 62 || this.mx.length != 62 || this.mz.length != 62 || this.mh.length != 62 || this.ms.length != 62) {
            throw new ReflectiveOperationException("Megalith claim schema changed");
        }
        this.fits = ClaimGuard.method(megaliths, "fits", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        this.claimed = ClaimGuard.method(megaliths, "claimed", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        this.baseY = ClaimGuard.method(megaliths, "baseY", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        Class<?> dungeons = Class.forName("chat.jaspr.biomes.Dungeons");
        this.dc = ClaimGuard.ints(dungeons, "D_CELL");
        this.dx = ClaimGuard.ints(dungeons, "D_SX");
        this.dz = ClaimGuard.ints(dungeons, "D_SZ");
        this.dh = ClaimGuard.ints(dungeons, "D_SY");
        this.da = ClaimGuard.longs(dungeons, "D_SALT_A");
        this.db = ClaimGuard.longs(dungeons, "D_SALT_B");
        this.surface = ClaimGuard.booleans(dungeons, "D_SURFACE");
        if (this.dc.length != 14 || this.dx.length != 14 || this.dz.length != 14 || this.dh.length != 14 || this.da.length != 14 || this.db.length != 14 || this.surface.length != 14) {
            throw new ReflectiveOperationException("Dungeon claim schema changed");
        }
        this.dungeonAnchor = ClaimGuard.method(dungeons, "anchor", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Long.TYPE, Integer.TYPE, Integer.TYPE);
        this.dungeonSurface = ClaimGuard.method(dungeons, "surfaceAnchor", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Long.TYPE, Integer.TYPE, Integer.TYPE);
        this.mc2 = ClaimGuard.ints(megaliths, "C_CELL2");
        this.ms2 = ClaimGuard.longs(megaliths, "C_SALT2");
        this.mm = ClaimGuard.ints(megaliths, "C_MODE");
        if (this.mc2.length != 62 || this.ms2.length != 62 || this.mm.length != 62) {
            throw new ReflectiveOperationException("Secondary claim schema changed");
        }
        this.secondaryBase = ClaimGuard.method(megaliths, "secondaryBase", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        this.dc2 = ClaimGuard.ints(dungeons, "D_CELL_C");
        this.dsc = ClaimGuard.longs(dungeons, "D_SALT_C");
        if (this.dc2.length != 14 || this.dsc.length != 14) {
            throw new ReflectiveOperationException("Third room lattice schema changed");
        }
        this.newSanctuary = ClaimGuard.method(Class.forName("chat.jaspr.biomes.StructureRates"), "sanctuary", Terrain.class, Integer.TYPE, Integer.TYPE);
        this.entry = (int[][])ClaimGuard.field(dungeons, "ENTRY").get(null);
        for (int i = 0; i < this.surface.length; ++i) {
            if (this.surface[i] || i < this.entry.length && this.entry[i] != null && this.entry[i].length >= 2 && this.entry[i][0] >= 0 && this.entry[i][0] < this.dx[i] && this.entry[i][1] >= 0 && this.entry[i][1] < this.dz[i]) continue;
            throw new ReflectiveOperationException("Buried room entrance schema changed");
        }
        Class<?> anchor = Class.forName("chat.jaspr.biomes.Dungeons$Anchor");
        this.ax = ClaimGuard.field(anchor, "x");
        this.ay = ClaimGuard.field(anchor, "y");
        this.az = ClaimGuard.field(anchor, "z");
        Class<?> generator = Class.forName("chat.jaspr.biomes.HorrorGenerator");
        this.portalChunk = ClaimGuard.method(generator, "portalChunk", Integer.TYPE, Integer.TYPE);
        if (!Boolean.TRUE.equals(this.portalChunk.invoke(null, 8, 0)) || !Boolean.TRUE.equals(this.portalChunk.invoke(null, -248, -256)) || Boolean.TRUE.equals(this.portalChunk.invoke(null, 7, 0)) || Boolean.TRUE.equals(this.portalChunk.invoke(null, 8, 1))) {
            throw new ReflectiveOperationException("Sanctuary placement schema changed");
        }
        // 1.3.0: the tier-2 layer of HorrorBiomes 3.28.0. Missing or reshaped -> refuse (fail closed).
        try {
            this.mc3 = ClaimGuard.ints(megaliths, "C_CELL3");
            this.ms3 = ClaimGuard.longs(megaliths, "C_SALT3");
            this.tertiaryBase = ClaimGuard.method(megaliths, "tertiaryBase", Terrain.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            this.dc3 = ClaimGuard.ints(dungeons, "D_CELL_D");
            this.dsd = ClaimGuard.longs(dungeons, "D_SALT_D");
            Class<?> rates = Class.forName("chat.jaspr.biomes.StructureRates");
            this.sanctuary2 = ClaimGuard.method(rates, "sanctuary2", Terrain.class, Integer.TYPE, Integer.TYPE);
            this.edges = new int[][]{{ClaimGuard.field(rates, "EDGE_A_X").getInt(null), ClaimGuard.field(rates, "EDGE_A_Z").getInt(null)}, {ClaimGuard.field(rates, "EDGE_B_X").getInt(null), ClaimGuard.field(rates, "EDGE_B_Z").getInt(null)}};
            this.tier2Legacy = ClaimGuard.method(StructurePlanner.class, "tier2Legacy", Long.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            this.tier2Region = ClaimGuard.method(StructurePlanner.class, "tier2Region", Long.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
            if (ClaimGuard.field(StructurePlanner.class, "REGION").getInt(null) != 1024 || ClaimGuard.field(StructurePlanner.class, "EXPANSION_REGION").getInt(null) != 384) {
                throw new ReflectiveOperationException("Catalogue grid schema changed");
            }
        }
        catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException missing) {
            throw new ReflectiveOperationException("Tier-2 claim schema missing (JasprHorrorBiomes 3.28.0 or later required): " + missing.getMessage(), missing);
        }
        if (this.mc3.length != 62 || this.ms3.length != 62) {
            throw new ReflectiveOperationException("Tertiary claim schema changed");
        }
        if (this.dc3.length != 14 || this.dsd.length != 14) {
            throw new ReflectiveOperationException("Fourth room lattice schema changed");
        }
        if (this.edges[0][0] != 136 || this.edges[0][1] != 0 || this.edges[1][0] != 8 || this.edges[1][1] != 128) {
            throw new ReflectiveOperationException("Tier-2 sanctuary placement schema changed");
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?> ... parameters) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static int[] ints(Class<?> type, String name) throws ReflectiveOperationException {
        return (int[])ClaimGuard.field(type, name).get(null);
    }

    private static long[] longs(Class<?> type, String name) throws ReflectiveOperationException {
        return (long[])ClaimGuard.field(type, name).get(null);
    }

    private static boolean[] booleans(Class<?> type, String name) throws ReflectiveOperationException {
        return (boolean[])ClaimGuard.field(type, name).get(null);
    }

    private static boolean overlaps(int x, int y, int z, int sx, int sy, int sz, int ox, int oy, int oz, int osx, int osy, int osz) {
        return (long)x < (long)ox + (long)osx + 2L && (long)x + (long)sx + 2L > (long)ox && (long)z < (long)oz + (long)osz + 2L && (long)z + (long)sz + 2L > (long)oz && (long)y < (long)oy + (long)osy + 2L && (long)y + (long)sy + 2L > (long)oy;
    }

    private boolean sanctuaryConflict(int x, int z, int sizeX, int sizeZ) throws ReflectiveOperationException {
        long c0 = Math.floorDiv((long)x - 2L, 16L) - 2L;
        long c1 = Math.floorDiv((long)x + (long)sizeX + 1L, 16L) + 2L;
        long d0 = Math.floorDiv((long)z - 2L, 16L) - 2L;
        long d1 = Math.floorDiv((long)z + (long)sizeZ + 1L, 16L) + 2L;
        long cx = c0 + Math.floorMod(8L - c0, 256L);
        long cz = d0 + Math.floorMod(-d0, 256L);
        if (cx > c1 || cz > d1) {
            return false;
        }
        return Boolean.TRUE.equals(this.portalChunk.invoke(null, (int)cx, (int)cz));
    }

    private static boolean flat(int x, int z, int sizeX, int sizeZ, int ox, int oz, int osx, int osz, int gap) {
        return (long)x < (long)ox + (long)osx + (long)gap && (long)x + (long)sizeX + (long)gap > (long)ox && (long)z < (long)oz + (long)osz + (long)gap && (long)z + (long)sizeZ + (long)gap > (long)oz;
    }

    private boolean newSanctuaryConflict(Terrain terrain, int x, int z, int sizeX, int sizeZ) throws ReflectiveOperationException {
        long c0 = Math.floorDiv((long)x - 2L, 16L) - 2L;
        long c1 = Math.floorDiv((long)x + (long)sizeX + 1L, 16L) + 2L;
        long d0 = Math.floorDiv((long)z - 2L, 16L) - 2L;
        long d1 = Math.floorDiv((long)z + (long)sizeZ + 1L, 16L) + 2L;
        for (long i = c0 + Math.floorMod(136L - c0, 256L); i <= c1; i += 256L) {
            for (long j = d0 + Math.floorMod(128L - d0, 256L); j <= d1; j += 256L) {
                if (!Boolean.TRUE.equals(this.newSanctuary.invoke(null, terrain, (int)i, (int)j))) continue;
                return true;
            }
        }
        return false;
    }

    private boolean planViewConflict(Terrain terrain, int x, int z, int sizeX, int sizeZ) throws Exception {
        if (this.newSanctuaryConflict(terrain, x, z, sizeX, sizeZ)) {
            return true;
        }
        for (int k = 0; k < this.mc.length; ++k) {
            for (int layer = 0; layer < 2; ++layer) {
                int cell = layer == 0 ? this.mc[k] : this.mc2[k];
                long salt = layer == 0 ? this.ms[k] : this.ms2[k];
                int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
                int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
                int a0 = Math.floorDiv(x - this.mx[k] - 8, 16);
                int a1 = Math.floorDiv(x + sizeX + 8, 16);
                int b0 = Math.floorDiv(z - this.mz[k] - 8, 16);
                int b1 = Math.floorDiv(z + sizeZ + 8, 16);
                for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                    for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                        int ox = acx * 16 + 1;
                        int oz = acz * 16 + 1;
                        if (!ClaimGuard.flat(x, z, sizeX, sizeZ, ox - 3, oz - 3, this.mx[k] + 6, this.mz[k] + 6, 2)) continue;
                        boolean built = layer == 0 ? Boolean.TRUE.equals(this.fits.invoke(null, terrain, k, ox, oz)) && !Boolean.TRUE.equals(this.claimed.invoke(null, terrain, ox, oz, this.mx[k], this.mz[k], k)) : (Integer)this.secondaryBase.invoke(null, terrain, k, acx, acz) >= 0;
                        if (!built) continue;
                        return true;
                    }
                }
            }
        }
        for (int i = 0; i < this.dc.length; ++i) {
            for (int lattice = 0; lattice < 3; ++lattice) {
                long salt = lattice == 0 ? this.da[i] : (lattice == 1 ? this.db[i] : this.dsc[i]);
                if (salt == 0L) continue;
                int cell = lattice == 2 ? this.dc2[i] : this.dc[i];
                int pad = this.surface[i] ? 6 : 1;
                int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
                int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
                int a0 = Math.floorDiv(x - this.dx[i] - 12, 16);
                int a1 = Math.floorDiv(x + sizeX + 12, 16);
                int b0 = Math.floorDiv(z - this.dz[i] - 12, 16);
                int b1 = Math.floorDiv(z + sizeZ + 12, 16);
                for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                    for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                        Object found;
                        int rx = acx * 16 + 2;
                        int rz = acz * 16 + 2;
                        if (!ClaimGuard.flat(x, z, sizeX, sizeZ, rx - pad, rz - pad, this.dx[i] + 2 * pad, this.dz[i] + 2 * pad, 2) || (found = (this.surface[i] ? this.dungeonSurface : this.dungeonAnchor).invoke(null, terrain, acx, acz, cell, salt, this.dx[i], this.dz[i])) == null || this.ax.getInt(found) != rx || this.az.getInt(found) != rz) continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean envelopeConflicts(Terrain terrain, int x, int y, int z, SiteSpec site, Occupancy occupancy) {
        try {
            int sizeX = site.dimensions[0];
            int sizeZ = site.dimensions[2];
            if (this.sanctuaryConflict(x, z, sizeX, sizeZ) || this.newSanctuaryConflict(terrain, x, z, sizeX, sizeZ)) {
                return true;
            }
            for (int k = 0; k < this.mc.length; ++k) {
                for (int layer = 0; layer < 2; ++layer) {
                    int cell = layer == 0 ? this.mc[k] : this.mc2[k];
                    long salt = layer == 0 ? this.ms[k] : this.ms2[k];
                    int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
                    int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
                    int a0 = Math.floorDiv(x - this.mx[k] - 8, 16);
                    int a1 = Math.floorDiv(x + sizeX + 8, 16);
                    int b0 = Math.floorDiv(z - this.mz[k] - 8, 16);
                    int b1 = Math.floorDiv(z + sizeZ + 8, 16);
                    for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                        for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                            int ox = acx * 16 + 1;
                            int oz = acz * 16 + 1;
                            if (!ClaimGuard.flat(x, z, sizeX, sizeZ, ox - 3, oz - 3, this.mx[k] + 6, this.mz[k] + 6, 2)) continue;
                            int base = layer == 0 ? (Boolean.TRUE.equals(this.fits.invoke(null, terrain, k, ox, oz)) && !Boolean.TRUE.equals(this.claimed.invoke(null, terrain, ox, oz, this.mx[k], this.mz[k], k)) ? (Integer)this.baseY.invoke(null, terrain, k, acx, acz) : -1) : ((Integer)this.secondaryBase.invoke(null, terrain, k, acx, acz)).intValue();
                            if (base < 0 || !occupancy.meets(x, y, z, ox - 3 - 2, oz - 3 - 2, ox + this.mx[k] + 3 + 2, oz + this.mz[k] + 3 + 2, base - 10, 255)) continue;
                            return true;
                        }
                    }
                }
            }
            for (int i = 0; i < this.dc.length; ++i) {
                for (int lattice = 0; lattice < 3; ++lattice) {
                    long salt = lattice == 0 ? this.da[i] : (lattice == 1 ? this.db[i] : this.dsc[i]);
                    if (salt == 0L) continue;
                    int cell = lattice == 2 ? this.dc2[i] : this.dc[i];
                    int pad = this.surface[i] ? 6 : 1;
                    int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
                    int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
                    int a0 = Math.floorDiv(x - this.dx[i] - 12, 16);
                    int a1 = Math.floorDiv(x + sizeX + 12, 16);
                    int b0 = Math.floorDiv(z - this.dz[i] - 12, 16);
                    int b1 = Math.floorDiv(z + sizeZ + 12, 16);
                    for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                        for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                            Object found;
                            int rx = acx * 16 + 2;
                            int rz = acz * 16 + 2;
                            if (!ClaimGuard.flat(x, z, sizeX, sizeZ, rx - pad, rz - pad, this.dx[i] + 2 * pad, this.dz[i] + 2 * pad, 2) || (found = (this.surface[i] ? this.dungeonSurface : this.dungeonAnchor).invoke(null, terrain, acx, acz, cell, salt, this.dx[i], this.dz[i])) == null || this.ax.getInt(found) != rx || this.az.getInt(found) != rz) continue;
                            int ry = this.ay.getInt(found);
                            if (this.surface[i]) {
                                if (!occupancy.meets(x, y, z, rx - 6 - 2, rz - 6 - 2, rx + this.dx[i] + 6 + 2, rz + this.dz[i] + 6 + 2, ry - 12, 255)) continue;
                                return true;
                            }
                            if (occupancy.meets(x, y, z, rx - 1 - 2, rz - 1 - 2, rx + this.dx[i] + 1 + 2, rz + this.dz[i] + 1 + 2, ry - 3, ry + this.dh[i] + 3)) {
                                return true;
                            }
                            int ex = rx + this.entry[i][0];
                            int ez = rz + this.entry[i][1];
                            if (!occupancy.meets(x, y, z, ex - 1 - 2, ez - 1 - 2, ex + 2 + 2, ez + 2 + 2, ry, 255)) continue;
                            return true;
                        }
                    }
                }
            }
            return this.tier2Envelope(terrain, x, y, z, sizeX, sizeZ, occupancy);
        }
        catch (Exception e) {
            throw new IllegalStateException("Existing structure envelope check failed closed", e);
        }
    }

    /** envelopeConflicts() for the rooms of one lattice (cell, salt) of room kind i (1.3.0: used for lattice D). */
    private boolean roomEnvelope(Terrain terrain, int x, int y, int z, int sizeX, int sizeZ, Occupancy occupancy, int i, int cell, long salt) throws Exception {
        int pad = this.surface[i] ? 6 : 1;
        int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
        int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
        int a0 = Math.floorDiv(x - this.dx[i] - 12, 16);
        int a1 = Math.floorDiv(x + sizeX + 12, 16);
        int b0 = Math.floorDiv(z - this.dz[i] - 12, 16);
        int b1 = Math.floorDiv(z + sizeZ + 12, 16);
        for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
            for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                Object found;
                int rx = acx * 16 + 2;
                int rz = acz * 16 + 2;
                if (!ClaimGuard.flat(x, z, sizeX, sizeZ, rx - pad, rz - pad, this.dx[i] + 2 * pad, this.dz[i] + 2 * pad, 2) || (found = (this.surface[i] ? this.dungeonSurface : this.dungeonAnchor).invoke(null, terrain, acx, acz, cell, salt, this.dx[i], this.dz[i])) == null || this.ax.getInt(found) != rx || this.az.getInt(found) != rz) continue;
                int ry = this.ay.getInt(found);
                if (this.surface[i]) {
                    if (!occupancy.meets(x, y, z, rx - 6 - 2, rz - 6 - 2, rx + this.dx[i] + 6 + 2, rz + this.dz[i] + 6 + 2, ry - 12, 255)) continue;
                    return true;
                }
                if (occupancy.meets(x, y, z, rx - 1 - 2, rz - 1 - 2, rx + this.dx[i] + 1 + 2, rz + this.dz[i] + 1 + 2, ry - 3, ry + this.dh[i] + 3)) {
                    return true;
                }
                int ex = rx + this.entry[i][0];
                int ez = rz + this.entry[i][1];
                if (!occupancy.meets(x, y, z, ex - 1 - 2, ez - 1 - 2, ex + 2 + 2, ez + 2 + 2, ry, 255)) continue;
                return true;
            }
        }
        return false;
    }

    public boolean conflicts(Terrain terrain, int x, int y, int z, SiteSpec site) {
        try {
            int sizeX = site.dimensions[0];
            int sizeY = site.dimensions[1];
            int sizeZ = site.dimensions[2];
            if (this.sanctuaryConflict(x, z, sizeX, sizeZ)) {
                return true;
            }
            if (this.planViewConflict(terrain, x, z, sizeX, sizeZ)) {
                return true;
            }
            for (int k = 0; k < this.mc.length; ++k) {
                int cell = this.mc[k];
                int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + this.ms[k]) >>> 3, (long)cell);
                int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + this.ms[k] + 17L) >>> 3, (long)cell);
                int a0 = Math.floorDiv(x - this.mx[k] - 18, 16);
                int a1 = Math.floorDiv(x + sizeX + 18, 16);
                int b0 = Math.floorDiv(z - this.mz[k] - 18, 16);
                int b1 = Math.floorDiv(z + sizeZ + 18, 16);
                for (int acx = a0; acx <= a1; ++acx) {
                    if (Math.floorMod(acx, cell) != offX) continue;
                    for (int acz = b0; acz <= b1; ++acz) {
                        int base;
                        if (Math.floorMod(acz, cell) != offZ) continue;
                        int ox = acx * 16 + 1;
                        int oz = acz * 16 + 1;
                        if (x >= ox + this.mx[k] + 2 || x + sizeX + 2 <= ox || z >= oz + this.mz[k] + 2 || z + sizeZ + 2 <= oz || !Boolean.TRUE.equals(this.fits.invoke(null, terrain, k, ox, oz)) || Boolean.TRUE.equals(this.claimed.invoke(null, terrain, ox, oz, this.mx[k], this.mz[k], k)) || (base = ((Integer)this.baseY.invoke(null, terrain, k, acx, acz)).intValue()) < 0 || !ClaimGuard.overlaps(x, y, z, sizeX, sizeY, sizeZ, ox, base - 10, oz, this.mx[k], this.mh[k] + 17, this.mz[k])) continue;
                        return true;
                    }
                }
            }
            for (int i = 0; i < this.dc.length; ++i) {
                for (long salt : new long[]{this.da[i], this.db[i]}) {
                    if (salt == 0L) continue;
                    int cell = this.dc[i];
                    int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
                    int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
                    int a0 = Math.floorDiv(x - this.dx[i] - 18, 16);
                    int a1 = Math.floorDiv(x + sizeX + 18, 16);
                    int b0 = Math.floorDiv(z - this.dz[i] - 18, 16);
                    int b1 = Math.floorDiv(z + sizeZ + 18, 16);
                    for (int acx = a0; acx <= a1; ++acx) {
                        if (Math.floorMod(acx, cell) != offX) continue;
                        for (int acz = b0; acz <= b1; ++acz) {
                            Object found;
                            if (Math.floorMod(acz, cell) != offZ || (found = (this.surface[i] ? this.dungeonSurface : this.dungeonAnchor).invoke(null, terrain, acx, acz, cell, salt, this.dx[i], this.dz[i])) == null || !ClaimGuard.overlaps(x, y, z, sizeX, sizeY, sizeZ, this.ax.getInt(found), this.ay.getInt(found) - 2, this.az.getInt(found), this.dx[i], this.dh[i] + 5, this.dz[i])) continue;
                            return true;
                        }
                    }
                }
            }
            return this.tier2PlanView(terrain, x, z, sizeX, sizeZ);
        }
        catch (Exception e) {
            throw new IllegalStateException("Existing structure claim check failed closed", e);
        }
    }

    // == 1.3.0: HorrorBiomes 3.28.0 tier 2 ==========================================================================

    /** A tier-2 portal sanctuary (edge midpoints, 136,0 and 8,128 mod 256 chunks) within its two-chunk halo. */
    private boolean sanctuary2Conflict(Terrain terrain, int x, int z, int sizeX, int sizeZ) throws ReflectiveOperationException {
        long c0 = Math.floorDiv((long)x - 2L, 16L) - 2L;
        long c1 = Math.floorDiv((long)x + (long)sizeX + 1L, 16L) + 2L;
        long d0 = Math.floorDiv((long)z - 2L, 16L) - 2L;
        long d1 = Math.floorDiv((long)z + (long)sizeZ + 1L, 16L) + 2L;
        for (int[] edge : this.edges) {
            for (long i = c0 + Math.floorMod((long)edge[0] - c0, 256L); i <= c1; i += 256L) {
                for (long j = d0 + Math.floorMod((long)edge[1] - d0, 256L); j <= d1; j += 256L) {
                    if (!Boolean.TRUE.equals(this.sanctuary2.invoke(null, terrain, (int)i, (int)j))) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** The {x, z, width, depth} boxes of the tier-2 catalogue sites whose reserve (+16) may meet this box. */
    private boolean tier2Catalogue(long seed, int x, int z, int sizeX, int sizeZ, Terrain terrain, int y, Occupancy occupancy) throws ReflectiveOperationException {
        // Two legacy grids (1,024 blocks, the second shifted half a region), anchors up to 1,152 blocks into a cell.
        for (int g = 0; g < 2; ++g) {
            for (int rx = Math.floorDiv(x - 1536, 1024); rx <= Math.floorDiv(x + sizeX + 512, 1024); ++rx) {
                for (int rz = Math.floorDiv(z - 1536, 1024); rz <= Math.floorDiv(z + sizeZ + 512, 1024); ++rz) {
                    if (!this.catalogueMeets((StructurePlanner.Site)this.tier2Legacy.invoke(null, seed, rx, rz, g), x, y, z, sizeX, sizeZ, occupancy)) continue;
                    return true;
                }
            }
        }
        for (int rx = Math.floorDiv(x - 512, 384); rx <= Math.floorDiv(x + sizeX + 512, 384); ++rx) {
            for (int rz = Math.floorDiv(z - 512, 384); rz <= Math.floorDiv(z + sizeZ + 512, 384); ++rz) {
                if (!this.catalogueMeets((StructurePlanner.Site)this.tier2Region.invoke(null, seed, rx, rz, true), x, y, z, sizeX, sizeZ, occupancy)) continue;
                return true;
            }
        }
        return false;
    }

    /** As ImportedWorldgenPlugin.conflictsWithExpeditions (occupancy null) / expeditionEnvelope (big designs). */
    private boolean catalogueMeets(StructurePlanner.Site site, int x, int y, int z, int sizeX, int sizeZ, Occupancy occupancy) {
        if (site == null) {
            return false;
        }
        if ((long)x >= (long)site.x + (long)site.width + 16L || (long)x + (long)sizeX + 16L <= (long)site.x || (long)z >= (long)site.z + (long)site.depth + 16L || (long)z + (long)sizeZ + 16L <= (long)site.z) {
            return false;
        }
        return occupancy == null || occupancy.meets(x, y, z, site.x - 16, site.z - 16, site.x + site.width + 16, site.z + site.depth + 16, 0, 255);
    }

    /** conflicts() for tier 2: sanctuaries, tertiary set pieces and lattice-D rooms in plan view, catalogue +16. */
    private boolean tier2PlanView(Terrain terrain, int x, int z, int sizeX, int sizeZ) throws Exception {
        if (this.sanctuary2Conflict(terrain, x, z, sizeX, sizeZ)) {
            return true;
        }
        for (int k = 0; k < this.mc3.length; ++k) {
            int cell = this.mc3[k];
            long salt = this.ms3[k];
            int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
            int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
            int a0 = Math.floorDiv(x - this.mx[k] - 8, 16);
            int a1 = Math.floorDiv(x + sizeX + 8, 16);
            int b0 = Math.floorDiv(z - this.mz[k] - 8, 16);
            int b1 = Math.floorDiv(z + sizeZ + 8, 16);
            for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                    int ox = acx * 16 + 1;
                    int oz = acz * 16 + 1;
                    if (!ClaimGuard.flat(x, z, sizeX, sizeZ, ox - 3, oz - 3, this.mx[k] + 6, this.mz[k] + 6, 2)) continue;
                    if ((Integer)this.tertiaryBase.invoke(null, terrain, k, acx, acz) < 0) continue;
                    return true;
                }
            }
        }
        for (int i = 0; i < this.dc3.length; ++i) {
            long salt = this.dsd[i];
            if (salt == 0L) continue;
            int cell = this.dc3[i];
            int pad = this.surface[i] ? 6 : 1;
            int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
            int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
            int a0 = Math.floorDiv(x - this.dx[i] - 12, 16);
            int a1 = Math.floorDiv(x + sizeX + 12, 16);
            int b0 = Math.floorDiv(z - this.dz[i] - 12, 16);
            int b1 = Math.floorDiv(z + sizeZ + 12, 16);
            for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                    int rx = acx * 16 + 2;
                    int rz = acz * 16 + 2;
                    if (!ClaimGuard.flat(x, z, sizeX, sizeZ, rx - pad, rz - pad, this.dx[i] + 2 * pad, this.dz[i] + 2 * pad, 2)) continue;
                    Object found = (this.surface[i] ? this.dungeonSurface : this.dungeonAnchor).invoke(null, terrain, acx, acz, cell, salt, this.dx[i], this.dz[i]);
                    if (found == null || this.ax.getInt(found) != rx || this.az.getInt(found) != rz) continue;
                    return true;
                }
            }
        }
        return this.tier2Catalogue(terrain.seed, x, z, sizeX, sizeZ, terrain, 0, null);
    }

    /** envelopeConflicts() for tier 2 (big designs, Occupancy of their real records). */
    private boolean tier2Envelope(Terrain terrain, int x, int y, int z, int sizeX, int sizeZ, Occupancy occupancy) throws Exception {
        if (this.sanctuary2Conflict(terrain, x, z, sizeX, sizeZ)) {
            return true;
        }
        for (int k = 0; k < this.mc3.length; ++k) {
            int cell = this.mc3[k];
            long salt = this.ms3[k];
            int offX = (int)Math.floorMod(Terrain.mix(terrain.seed + salt) >>> 3, (long)cell);
            int offZ = (int)Math.floorMod(Terrain.mix(terrain.seed + salt + 17L) >>> 3, (long)cell);
            int a0 = Math.floorDiv(x - this.mx[k] - 8, 16);
            int a1 = Math.floorDiv(x + sizeX + 8, 16);
            int b0 = Math.floorDiv(z - this.mz[k] - 8, 16);
            int b1 = Math.floorDiv(z + sizeZ + 8, 16);
            for (int acx = a0 + Math.floorMod(offX - a0, cell); acx <= a1; acx += cell) {
                for (int acz = b0 + Math.floorMod(offZ - b0, cell); acz <= b1; acz += cell) {
                    int ox = acx * 16 + 1;
                    int oz = acz * 16 + 1;
                    if (!ClaimGuard.flat(x, z, sizeX, sizeZ, ox - 3, oz - 3, this.mx[k] + 6, this.mz[k] + 6, 2)) continue;
                    int base = (Integer)this.tertiaryBase.invoke(null, terrain, k, acx, acz);
                    if (base < 0 || !occupancy.meets(x, y, z, ox - 3 - 2, oz - 3 - 2, ox + this.mx[k] + 3 + 2, oz + this.mz[k] + 3 + 2, base - 10, 255)) continue;
                    return true;
                }
            }
        }
        for (int i = 0; i < this.dc3.length; ++i) {
            if (this.dsd[i] == 0L || !this.roomEnvelope(terrain, x, y, z, sizeX, sizeZ, occupancy, i, this.dc3[i], this.dsd[i])) continue;
            return true;
        }
        return this.tier2Catalogue(terrain.seed, x, z, sizeX, sizeZ, terrain, y, occupancy);
    }
}
