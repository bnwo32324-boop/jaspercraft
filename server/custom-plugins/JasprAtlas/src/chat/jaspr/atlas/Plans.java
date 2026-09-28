package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where everything goes. Four levels, each a pure function of the seed:
 * <ol>
 *   <li>fixed {@link Realm.Place}s (cities, forts, strongholds) at the story's coordinates;</li>
 *   <li>the road network (the Royal Road, the city roads, the Line Road, the Dominion's slave roads);</li>
 *   <li>sites on a 96-block grid: villas, sanctuaries and demes in the Concord, war camps, towers, pens and pits in the
 *       Dominion, each up to about 60 blocks across;</li>
 *   <li>cells on a 24-block grid filling every remaining gap, so no stretch of land is empty.</li>
 * </ol>
 */
final class Plans {
    static final int CELL = 24, SITE_GRID = 96;

    final long seed;
    final Terrain terrain;
    final List<Road> roads;

    Plans(long seed) {
        this.seed = seed;
        this.terrain = new Terrain(seed);
        this.roads = Collections.unmodifiableList(roads());
    }

    int surface(int x, int z) { return terrain.height(x, z); }

    // ------------------------------------------------------------------ roads

    /** A road: a polyline, its half-width, and whether it is an Asterian road (paved) or a Dominion one (black). */
    static final class Road {
        final String name;
        final int[] xs, zs;
        final int half;
        final int minX, minZ, maxX, maxZ;
        Road(String name, int half, int... pts) {
            this.name = name;
            this.half = half;
            xs = new int[pts.length / 2];
            zs = new int[pts.length / 2];
            int a = Integer.MAX_VALUE, b = Integer.MAX_VALUE, c = Integer.MIN_VALUE, d = Integer.MIN_VALUE;
            for (int i = 0; i < xs.length; i++) {
                xs[i] = pts[2 * i]; zs[i] = pts[2 * i + 1];
                a = Math.min(a, xs[i]); b = Math.min(b, zs[i]); c = Math.max(c, xs[i]); d = Math.max(d, zs[i]);
            }
            minX = a - half - 2; minZ = b - half - 2; maxX = c + half + 2; maxZ = d + half + 2;
        }

        /** Distance from (x, z) to the road's centre line. */
        double distance(int x, int z) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < xs.length; i++) best = Math.min(best, seg(x, z, xs[i], zs[i], xs[i + 1], zs[i + 1]));
            return best;
        }

        /** Unit direction of the segment nearest (x, z): {dx, dz}. */
        double[] direction(int x, int z) {
            double best = Double.MAX_VALUE; int k = 0;
            for (int i = 0; i + 1 < xs.length; i++) { double d = seg(x, z, xs[i], zs[i], xs[i + 1], zs[i + 1]); if (d < best) { best = d; k = i; } }
            double dx = xs[k + 1] - xs[k], dz = zs[k + 1] - zs[k], l = Math.max(1e-9, Math.sqrt(dx * dx + dz * dz));
            return new double[] {dx / l, dz / l};
        }

        boolean near(int minX, int minZ, int maxX, int maxZ) { return maxX >= this.minX && minX <= this.maxX && maxZ >= this.minZ && minZ <= this.maxZ; }

        private static double seg(double px, double pz, double ax, double az, double bx, double bz) {
            double vx = bx - ax, vz = bz - az, wx = px - ax, wz = pz - az;
            double l = vx * vx + vz * vz, t = l == 0 ? 0 : Math.max(0, Math.min(1, (wx * vx + wz * vz) / l));
            double dx = px - (ax + t * vx), dz = pz - (az + t * vz);
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static List<Road> roads() {
        List<Road> r = new ArrayList<>();
        // The Royal Road: from the Gate of Strangers through Astreion and the Last Watch, across the Wound, through the
        // Pylon of Teeth to the foot of Anthrakion.
        r.add(new Road("the Royal Road", 3, -734, 0, 560, 0));
        r.add(new Road("the Lampsa Road", 2, -560, -96, -500, -200, -448, -306));
        r.add(new Road("the Hieranthe Road", 2, -560, 96, -500, 200, -448, 306));
        r.add(new Road("the Archive Road", 2, -660, -96, -720, -200, -790, -300, -812, -352));
        r.add(new Road("the Lamp Road of the North", 2, -352, -380, -250, -382, -130, -380, -70, -380));
        r.add(new Road("the Lamp Road of the South", 2, -352, 380, -250, 378, -130, 380, -70, 380));
        // The Line Road runs behind the Lampwall, following the frontier.
        int[] line = new int[2 * 33];
        for (int k = 0; k <= 32; k++) { int z = -1000 + k * 62; line[2 * k] = Realm.lineX(z) - 16; line[2 * k + 1] = z; }
        r.add(new Road("the Line Road", 2, line));
        // The Dominion's roads: the Slavers' Ring round the Teeth, and the inner roads to the strongholds.
        int[] ring = new int[2 * 49];
        for (int k = 0; k <= 48; k++) {
            double a = Math.PI * 2 * k / 48;
            ring[2 * k] = (int) Math.round(Realm.TX + Math.cos(a) * 410);
            ring[2 * k + 1] = (int) Math.round(Realm.TZ + Math.sin(a) * 410);
        }
        r.add(new Road("the Slavers' Ring", 2, ring));
        r.add(new Road("the Weald Road", 2, Realm.TX, Realm.TZ - 100, Realm.TX + 6, Realm.TZ - 190));
        r.add(new Road("the Forge Road", 2, Realm.TX, Realm.TZ + 100, Realm.TX - 6, Realm.TZ + 188));
        r.add(new Road("the Edict Road", 2, Realm.TX + 100, Realm.TZ, Realm.TX + 150, Realm.TZ + 6, 770, 0));
        r.add(new Road("the Ash Road North", 2, 120, -400, 180, -250, 250, -60));
        r.add(new Road("the Ash Road South", 2, 170, 420, 200, 250, 250, 60));
        return r;
    }

    /** The roads that might touch the given box. */
    List<Road> roadsNear(int minX, int minZ, int maxX, int maxZ) {
        List<Road> out = new ArrayList<>(2);
        for (Road road : roads) if (road.near(minX, minZ, maxX, maxZ)) out.add(road);
        return out;
    }

    /** Distance to the nearest road (and its half-width) at (x, z), or a large number. */
    double roadDistance(int x, int z) {
        double best = 1e9;
        for (Road road : roads) if (road.near(x, z, x, z)) best = Math.min(best, road.distance(x, z) - road.half);
        return best;
    }

    // ------------------------------------------------------------------ sites

    enum SiteKind {
        // The Concord.
        DEME("deme", true, 46), VILLA("villa", true, 22), SANCTUARY("sanctuary", true, 24), FARMSTEAD("farmstead", true, 22),
        GYMNASIUM("gymnasium", true, 26), LYCEUM_ANNEX("school", true, 24), AQUEDUCT("aqueduct", true, 44), WATCH_OUTPOST("watch post", true, 18),
        // The Wound.
        FALLEN_PHAROS("fallen pharos", false, 20), DEAD_OUTPOST("ruined outpost", false, 22), BATTLEFIELD("battlefield", false, 30),
        // The Dominion.
        WAR_CAMP("war camp", false, 30), WATCHTOWER("watchtower", false, 14), SLAVE_PEN("slave pen", false, 22), SPAWNING_PIT("spawning pit", false, 20),
        ASH_FORT("ash fort", false, 28), QUARRY("quarry", false, 28), FORGE_WORKS("forge works", false, 26), STILLING_HOUSE("stilling house", false, 20),
        STONE_GROVE("stone grove", false, 28), RUINED_POLIS("ruined quarter", false, 32), EDICT_SQUARE("edict square", false, 22),
        SHRINE_OF_ASH("shrine of the Heart", false, 16), SLAVE_ROAD_CAMP("road camp", false, 20);
        final String noun;
        final boolean concord;
        final int radius;
        SiteKind(String noun, boolean concord, int radius) { this.noun = noun; this.concord = concord; this.radius = radius; }
    }

    static final class Site {
        final SiteKind kind;
        final int x, z, base, rot, i, j;
        final long hash;
        final String name;
        Site(SiteKind kind, int x, int z, int base, int rot, int i, int j, long hash, String name) {
            this.kind = kind; this.x = x; this.z = z; this.base = base; this.rot = rot; this.i = i; this.j = j; this.hash = hash; this.name = name;
        }
        boolean covers(int wx, int wz, int pad) { return Math.abs(wx - x) <= kind.radius + pad && Math.abs(wz - z) <= kind.radius + pad; }
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, Site> siteCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Site NONE = new Site(SiteKind.DEME, 0, 0, 0, 0, 0, 0, 0, "");

    /** The site of the 96-block square (i, j), or null. */
    Site site(int i, int j) {
        long k = (long) i << 32 ^ (j & 0xffffffffL);
        Site s = siteCache.get(k);
        if (s == null) { s = computeSite(i, j); if (siteCache.size() > 20000) siteCache.clear(); siteCache.put(k, s); }
        return s == NONE ? null : s;
    }

    private Site computeSite(int i, int j) {
        long h = Hash.of(seed ^ 0x51735L, i, j);
        int x = i * SITE_GRID + 48 + Hash.range(Hash.mix(h ^ 1), -12, 12), z = j * SITE_GRID + 48 + Hash.range(Hash.mix(h ^ 2), -12, 12);
        Realm.Zone zone = Realm.zone(x, z);
        if (zone == Realm.Zone.RIM || zone == Realm.Zone.LINE || zone == Realm.Zone.TEETH) return NONE;
        SiteKind kind = pick(zone, h, x, z);
        if (kind == null) return NONE;
        // Keep clear of places, the Rim, the Lampwall and the Teeth.
        int r = kind.radius;
        if (Math.abs(x) + r > Realm.BORDER - 6 || Math.abs(z) + r > Realm.BORDER - 6) return NONE;
        for (Realm.Place p : Realm.Place.values()) if (p.near(x, z, r + 10)) return NONE;
        if (zone == Realm.Zone.CONCORD && x + r > Realm.lineX(z) - 22) return NONE;
        double tr = Realm.teethDistance(x, z);
        if (tr < Realm.TEETH_R + Realm.TEETH_W + r + 6 && tr > Realm.TEETH_R - r - 6) return NONE;
        if (tr < Realm.PLATEAU_R + r + 8 && zone != Realm.Zone.PLATEAU) return NONE;
        if (roadDistance(x, z) < r * 0.5) {
            // Nudge off the road; if still on it, give the square to the cells.
            return NONE;
        }
        int rot = Hash.range(Hash.mix(h ^ 3), 0, 3);
        return new Site(kind, x, z, surface(x, z), rot, i, j, h, Names.site(Hash.mix(h ^ 9), kind));
    }

    private SiteKind pick(Realm.Zone zone, long h, int x, int z) {
        double roll = Hash.unit(Hash.mix(h ^ 7));
        switch (zone) {
            case CONCORD: {
                if (roll < 0.10) return null;
                SiteKind[] k = {SiteKind.DEME, SiteKind.DEME, SiteKind.VILLA, SiteKind.VILLA, SiteKind.FARMSTEAD, SiteKind.FARMSTEAD, SiteKind.SANCTUARY,
                    SiteKind.GYMNASIUM, SiteKind.LYCEUM_ANNEX, SiteKind.AQUEDUCT};
                if (x > Realm.lineX(z) - 170 && roll < 0.35) return SiteKind.WATCH_OUTPOST;
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case WOUND: {
                if (roll < 0.15) return null;
                SiteKind[] k = {SiteKind.FALLEN_PHAROS, SiteKind.DEAD_OUTPOST, SiteKind.BATTLEFIELD, SiteKind.BATTLEFIELD};
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case MARCHES: case GATE_ROAD: {
                if (roll < 0.08) return null;
                SiteKind[] k = {SiteKind.WAR_CAMP, SiteKind.WAR_CAMP, SiteKind.WATCHTOWER, SiteKind.SLAVE_PEN, SiteKind.SLAVE_PEN, SiteKind.SPAWNING_PIT,
                    SiteKind.ASH_FORT, SiteKind.SHRINE_OF_ASH, SiteKind.SLAVE_ROAD_CAMP};
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case WEALD: {
                if (roll < 0.08) return null;
                SiteKind[] k = {SiteKind.STILLING_HOUSE, SiteKind.STILLING_HOUSE, SiteKind.STONE_GROVE, SiteKind.STONE_GROVE, SiteKind.WATCHTOWER, SiteKind.SLAVE_PEN, SiteKind.SHRINE_OF_ASH};
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case FORGES: {
                if (roll < 0.08) return null;
                SiteKind[] k = {SiteKind.FORGE_WORKS, SiteKind.FORGE_WORKS, SiteKind.QUARRY, SiteKind.QUARRY, SiteKind.SPAWNING_PIT, SiteKind.SLAVE_PEN, SiteKind.WATCHTOWER};
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case FALLEN: {
                if (roll < 0.06) return null;
                SiteKind[] k = {SiteKind.RUINED_POLIS, SiteKind.RUINED_POLIS, SiteKind.RUINED_POLIS, SiteKind.EDICT_SQUARE, SiteKind.SLAVE_PEN, SiteKind.WATCHTOWER};
                return k[Hash.range(Hash.mix(h ^ 8), 0, k.length - 1)];
            }
            case PLATEAU: return null;
            default: return null;
        }
    }

    /** The site whose footprint covers (x, z), searching the neighbouring squares. */
    Site siteAt(int x, int z, int pad) {
        int i0 = Math.floorDiv(x, SITE_GRID), j0 = Math.floorDiv(z, SITE_GRID);
        for (int i = i0 - 1; i <= i0 + 1; i++)
            for (int j = j0 - 1; j <= j0 + 1; j++) { Site s = site(i, j); if (s != null && s.covers(x, z, pad)) return s; }
        return null;
    }

    // ------------------------------------------------------------------ cells

    /** A 24-block cell's filler: its kind is chosen by the zone; null kind means ground cover only. */
    static final class Cell {
        final int i, j, x, z, base, rot;
        final long hash;
        final Realm.Zone zone;
        final String kind;
        final boolean roadside;
        Cell(int i, int j, int x, int z, int base, int rot, long hash, Realm.Zone zone, String kind, boolean roadside) {
            this.i = i; this.j = j; this.x = x; this.z = z; this.base = base; this.rot = rot; this.hash = hash; this.zone = zone; this.kind = kind; this.roadside = roadside;
        }
    }

    /** The cell (i, j): null if a place, a site, the Lampwall or the Teeth claims its centre. */
    Cell cell(int i, int j) {
        int x = i * CELL + CELL / 2, z = j * CELL + CELL / 2;
        Realm.Zone zone = Realm.zone(x, z);
        if (zone == Realm.Zone.RIM || zone == Realm.Zone.LINE || zone == Realm.Zone.TEETH) return null;
        if (Realm.placeAt(x, z) != null) return null;
        for (Realm.Place p : Realm.Place.values()) if (p.near(x, z, 6)) return null;
        if (siteAt(x, z, 8) != null) return null;
        double tr = Realm.teethDistance(x, z);
        if (tr > Realm.TEETH_R - 10 && tr < Realm.TEETH_R + Realm.TEETH_W + 10) return null;
        long h = Hash.of(seed ^ 0xCE11L, i, j);
        boolean roadside = roadDistance(x, z) < 10;
        String kind = Cells.pick(zone, h, roadside, x, z);
        return new Cell(i, j, x, z, surface(x, z), Hash.range(Hash.mix(h ^ 3), 0, 3), h, zone, kind, roadside);
    }
}
