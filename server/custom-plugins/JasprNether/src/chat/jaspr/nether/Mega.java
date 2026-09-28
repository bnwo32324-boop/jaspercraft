package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * JasperCraft's Nether mega structures (owner, 2026-09-28: "massive structures in the Nether that spawn naturally ...
 * in keeping with the Nether theme, progression and loot"). One site per 384 x 384-block cell; the NetherEx region at
 * the site decides which of the five it is, so each stays in its own land and marks one step of the Nether's
 * progression:
 * <ul>
 *   <li>Hell: the Golden Bazaar, the Pigtificate capital (amethyst trade, a Respawner Statue, a golem-guarded vault);</li>
 *   <li>Ruthless Sands: the Soul Pyramid (wither bones, the Soul King's tomb);</li>
 *   <li>Torrid Wasteland: the Cinder Forge (blaze furnaces, salamander hides, cincinnasite);</li>
 *   <li>Fungi Forest: the Spore Cathedral (spores, ghast meat, and an Urn of Sorrow on its crown for the Ghast Queen);</li>
 *   <li>Arctic Abyss: the Frozen Citadel (rime and frost, the Wight Lord's throne, the richest vault).</li>
 * </ul>
 * Every site hollows its own cavern (floor, dome, lakes, dressing) and is drawn chunk by chunk through {@link Draw};
 * the plan is deterministic from the seed and the cell. Whether a site is built is decided once, by the first chunk
 * that reaches it, and recorded in the registry, so a structure is never drawn into land generated without it.
 */
final class Mega {
    static final int CELL = 384;      // blocks per grid cell, one site per cell
    static final int MARGIN = 112;    // a site's centre keeps this far from its cell's edges
    static final int REACH = 112;     // nothing a site draws is farther than this from its centre (horizontally)

    enum Kind {
        BAZAAR("golden_bazaar", "The Golden Bazaar", Biomes.Nex.HELL, 96, 36, 76, 64),
        PYRAMID("soul_pyramid", "The Soul Pyramid", Biomes.Nex.RUTHLESS_SANDS, 100, 34, 82, 50),
        FORGE("cinder_forge", "The Cinder Forge", Biomes.Nex.TORRID_WASTELAND, 96, 34, 84, 44),
        CATHEDRAL("spore_cathedral", "The Spore Cathedral", Biomes.Nex.FUNGI_FOREST, 94, 34, 86, 44),
        CITADEL("frozen_citadel", "The Frozen Citadel", Biomes.Nex.ARCTIC_ABYSS, 100, 38, 80, 58);

        final String id, display; final Biomes.Nex region;
        /** Cavern radius, floor level, dome height above the floor at the centre, and the dry core radius (no lakes). */
        final int radius, floor, dome, core;
        Kind(String id, String display, Biomes.Nex region, int radius, int floor, int dome, int core) {
            this.id = id; this.display = display; this.region = region; this.radius = radius; this.floor = floor; this.dome = dome; this.core = core;
        }
        static Kind of(Biomes.Nex n) { for (Kind k : values()) if (k.region == n) return k; return BAZAAR; }
        static Kind byId(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return null; }
    }

    /** One planned site. Shape queries are pure functions of the seed, so every chunk agrees on them. */
    static final class Site {
        final Kind kind; final int cellX, cellZ, x, y, z; final long seed; final int salt;
        final int minX, minZ, maxX, maxZ;
        Object plan;
        Site(Kind kind, int cellX, int cellZ, int x, int z, long seed) {
            this.kind = kind; this.cellX = cellX; this.cellZ = cellZ; this.x = x; this.z = z; this.y = kind.floor; this.seed = seed;
            this.salt = (int) (seed ^ (seed >>> 32));
            minX = x - REACH; maxX = x + REACH; minZ = z - REACH; maxZ = z + REACH;
        }
        double dist(int bx, int bz) { double dx = bx - x, dz = bz - z; return Math.sqrt(dx * dx + dz * dz); }
        /** The cavern wall's distance from the centre in the direction of (bx, bz). */
        double edge(int bx, int bz) { return kind.radius + Draw.fbm(bx, bz, 52, salt) * 15 + Draw.noise(bx, bz, 11, salt + 3) * 3.5; }
        /** 0 at the centre, 1 at the cavern wall. */
        double f(int bx, int bz) { return dist(bx, bz) / edge(bx, bz); }
        int floorAt(int bx, int bz) {
            double f = f(bx, bz);
            double dune = kind == Kind.PYRAMID ? Draw.fbm(bx, bz, 26, salt + 5) * 3.2 : Draw.fbm(bx, bz, 30, salt + 5) * 1.4;
            double rim = f > 0.8 ? Math.pow((f - 0.8) / 0.2, 1.6) * 16 : 0;
            return y + (int) Math.round(dune + rim);
        }
        int ceilAt(int bx, int bz) {
            double f = Math.min(1, f(bx, bz));
            // full height over the middle (the structures need it), rougher towards the walls
            double h = kind.dome * Math.sqrt(1 - f * f) * (1 - f * 0.24 * (0.5 - 0.5 * Draw.noise(bx, bz, 17, salt + 9)));
            return Math.min(120, y + (int) Math.round(h));
        }
        boolean inside(int bx, int bz) { return f(bx, bz) < 1 && ceilAt(bx, bz) - floorAt(bx, bz) >= 3; }
        Random random() { return new Random(seed); }
        String name() { return kind.display; }
    }

    interface Regions { Biomes.Nex at(int x, int z); }
    interface Keepout { boolean blocked(int x0, int z0, int x1, int z1); }

    private static final Site NONE = new Site(Kind.BAZAAR, 0, 0, 0, 0, 0);
    static final MegaDesign[] DESIGNS = new MegaDesign[Kind.values().length];
    static {
        DESIGNS[Kind.BAZAAR.ordinal()] = new MegaBazaar();
        DESIGNS[Kind.PYRAMID.ordinal()] = new MegaPyramid();
        DESIGNS[Kind.FORGE.ordinal()] = new MegaForge();
        DESIGNS[Kind.CATHEDRAL.ordinal()] = new MegaCathedral();
        DESIGNS[Kind.CITADEL.ordinal()] = new MegaCitadel();
    }
    static MegaDesign design(Kind k) { return DESIGNS[k.ordinal()]; }

    final long seed;
    private final Regions regions;
    private final Keepout keepout;
    private final Map<Long, Site> cache = new LinkedHashMap<Long, Site>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Site> e) { return size() > 256; }
    };

    Mega(long seed, Regions regions, Keepout keepout) { this.seed = seed; this.regions = regions; this.keepout = keepout; }

    /** The planned site of a cell, or null (every candidate spot fell inside a Nether City's reach). */
    synchronized Site site(int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        Site s = cache.get(key);
        if (s == null) {
            s = plan(cellX, cellZ);
            cache.put(key, s == null ? NONE : s);
            return s;
        }
        return s == NONE ? null : s;
    }

    private Site plan(int cellX, int cellZ) {
        Random r = new Random(seed ^ (cellX * 0x5DEECE66DL + cellZ * 0x2545F4914F6CDD1DL) ^ 0x4D454741L);
        Kind want = Kind.values()[r.nextInt(Kind.values().length)];
        int span = CELL - 2 * MARGIN;
        Site fallback = null;
        for (int t = 0; t < 24; t++) {
            int x = cellX * CELL + MARGIN + r.nextInt(span), z = cellZ * CELL + MARGIN + r.nextInt(span);
            long siteSeed = r.nextLong();
            Kind k = Kind.of(regions.at(x, z));
            if (keepout != null && keepout.blocked(x - REACH, z - REACH, x + REACH, z + REACH)) continue;
            Site s = new Site(k, cellX, cellZ, x, z, siteSeed);
            if (k == want) return prepared(s);
            if (fallback == null) fallback = s;
        }
        return fallback == null ? null : prepared(fallback);
    }

    static Site prepared(Site s) {
        s.plan = design(s.kind).plan(s, s.random());
        return s;
    }

    /** Planned sites whose reach overlaps the block box. */
    List<Site> touching(int x0, int z0, int x1, int z1) {
        List<Site> out = new ArrayList<>(1);
        for (int cx = Math.floorDiv(x0 - REACH, CELL); cx <= Math.floorDiv(x1 + REACH, CELL); cx++)
            for (int cz = Math.floorDiv(z0 - REACH, CELL); cz <= Math.floorDiv(z1 + REACH, CELL); cz++) {
                Site s = site(cx, cz);
                if (s != null && s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) out.add(s);
            }
        return out;
    }

    /** The planned site whose reach contains the column, or null. */
    Site at(int x, int z) {
        Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return s != null && x >= s.minX && x <= s.maxX && z >= s.minZ && z <= s.maxZ ? s : null;
    }

    /** Nearest planned site of a kind (any kind if null) within {@code cells} cells, for rumours and navigation. */
    Site nearest(Kind kind, int x, int z, int cells) {
        int ccx = Math.floorDiv(x, CELL), ccz = Math.floorDiv(z, CELL);
        Site best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
            Site s = site(ccx + dx, ccz + dz);
            if (s == null || (kind != null && s.kind != kind)) continue;
            double d = (double) (s.x - x) * (s.x - x) + (double) (s.z - z) * (s.z - z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    /** Draws the part of a site inside d's box: its cavern first, then the structure. */
    static void draw(Site s, Draw d) {
        if (!d.touches(s.minX, s.minZ, s.maxX, s.maxZ)) return;
        MegaDesign design = design(s.kind);
        cavern(s, d, design);
        design.draw(s, d);
    }

    /**
     * Hollows the cavern: seals the lava in the surrounding rock (so no spring or pocket pours into it), carves up to
     * the dome and closes the dome with a thin crust where it broke into caves above, lays a solid floor over whatever
     * lies beneath, then lakes and dressing. The walls stay natural, so tunnels still lead in.
     */
    static void cavern(Site s, Draw d, MegaDesign design) {
        int xa = Math.max(d.x0, s.minX), xb = Math.min(d.x1, s.maxX), za = Math.max(d.z0, s.minZ), zb = Math.min(d.z1, s.maxZ);
        Draw.Mat rock = design.under();
        int wallTop = s.y + (int) (s.kind.dome * 0.5) + 16;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double f = s.f(x, z);
            if (f >= 1.12) continue;
            int floor = s.floorAt(x, z), ceil = s.ceilAt(x, z);
            boolean inside = f < 1 && ceil - floor >= 3;
            int sealTop = Math.min(122, inside ? ceil + 4 : wallTop);
            for (int y = Math.max(s.y - 3, 5); y <= sealTop; y++) {
                int id = d.id(x, y, z);
                if (id == Blocks.LAVA || id == Blocks.LAVA_FLOW) d.set(x, y, z, rock.at(x, y, z));
            }
            if (!inside) continue;
            for (int y = floor + 1; y <= ceil; y++) if (d.id(x, y, z) != Blocks.BEDROCK) d.set(x, y, z, 0);
            for (int y = ceil + 1; y <= Math.min(ceil + 3, 122); y++) if (d.air(x, y, z)) d.set(x, y, z, rock.at(x, y, z));
            boolean lake = f < 0.9 && s.dist(x, z) > s.kind.core && !design.dry(s, x, z)
                && Draw.fbm(x, z, 34, s.salt + 21) > design.lakeLevel();
            int top = lake ? design.lakeLiquid() : design.ground(s, x, floor, z);
            d.set(x, floor, z, top);
            if (lake) d.set(x, floor - 1, z, design.lakeLiquid());
            Draw.Mat sub = design.under();
            for (int y = floor - (lake ? 2 : 1); y >= floor - 9 && y > 5; y--) {
                if (Blocks.isFullSolid(d.id(x, y, z))) { if (y < floor - 3) break; else continue; }
                d.set(x, y, z, sub.at(x, y, z));
            }
            design.dress(s, d, x, z, floor, ceil, f, lake);
        }
    }

    /** The registry box a site claims: its whole reach, from under its floor to the top of its dome. */
    static int[] box(Site s) { return new int[]{s.minX, s.y - 10, s.minZ, s.maxX, Math.min(122, s.y + s.kind.dome + 4), s.maxZ}; }
}
