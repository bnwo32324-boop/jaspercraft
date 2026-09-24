package chat.jaspr.biomes;

/**
 * Where the underground shows itself.
 *
 * Two families, because the shapes want different machinery. Long features --
 * ravines, canyons, crevasse swarms -- are ridge lines of a warped 2D field, so
 * they meander for hundreds of blocks, never repeat and cost nothing to look up
 * from any chunk. Bounded features -- craters, sinkholes, cenotes, calderas,
 * collapse windows -- are placed on a lattice and stamped, because a crater has
 * a centre and a rim and a field has neither.
 *
 * Which archetype a place gets follows the cave region beneath it, so the hole
 * in the ground tells you what you will find once you are in it: lava-floored
 * canyons over the Emberveins, collapse windows into the Cathedral's chambers,
 * ice crevasses over Frostbore.
 *
 * Nothing here reads the world or a neighbouring chunk; every value comes from
 * the world coordinate and the seed, so two chunks agree along their border.
 */
public final class SurfaceOpenings {
    public static final int NONE = -1, RAVINE = 0, CANYON = 1, CREVASSE = 2, GORGE = 3,
        CRATER = 4, SINKHOLE = 5, CENOTE = 6, COLLAPSE = 7, CALDERA = 8, CIRQUE = 9;

    public static final String[] NAMES = {"ravine", "canyon", "crevasse", "gorge",
        "crater", "sinkhole", "cenote", "collapse", "caldera", "cirque"};

    /** Lattice for the bounded features. */
    private static final int PIT_CELL = 156;

    private final Terrain terrain;
    private final Caves caves;

    public SurfaceOpenings(Terrain terrain, Caves caves) { this.terrain = terrain; this.caves = caves; }

    /**
     * Pool surfaces are absolute heights, never offsets from the column's own floor.
     * A pool measured from its own floor is not a pool: each neighbouring column
     * stands its water one block higher or lower than the last, so the body reads as
     * a staircase of stranded blocks, and generated liquid never gets the block
     * update that would let it fall. Both tables drift slowly enough that a single
     * body of water is flat from end to end.
     */
    private int waterTable(int wx, int wz) {
        return 30 + (int) Math.round(13 * (terrain.noise(wx / 430.0, wz / 430.0, 104) * 0.5 + 0.5));
    }

    private int lavaTable(int wx, int wz) {
        return 11 + (int) Math.round(7 * (terrain.noise(wx / 370.0, wz / 370.0, 105) * 0.5 + 0.5));
    }

    /** Ground height at a pit's centre, so its pool can be levelled on the pit itself. */
    private final java.util.HashMap<Long, Integer> centres = new java.util.HashMap<Long, Integer>();

    private int centreGround(int cx, int cz) {
        long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
        Integer known = centres.get(key);
        if (known != null) return known.intValue();
        int y = terrain.sample(cx, cz).y;
        centres.put(key, Integer.valueOf(y));
        return y;
    }

    /** One column's worth of opening. Reused across a chunk to stay allocation-free. */
    public static final class Cut {
        public int style = NONE;
        /** Blocks removed downward from the original surface. Zero means untouched. */
        public int depth;
        /** Extra ground piled above the original surface, for ejecta rims. */
        public int rise;
        public boolean rim;
        /** 0 none, 9 still water, 11 still lava. */
        public int fill;
        /** Fill occupies from the new floor up to and including this height. */
        public int fillTop;

        void reset() { style = NONE; depth = 0; rise = 0; rim = false; fill = 0; fillTop = 0; }
        public boolean any() { return depth > 0 || rise > 0; }
    }

    public void sample(int wx, int wz, int ground, Cut out) {
        out.reset();
        gash(wx, wz, ground, out);
        pit(wx, wz, ground, out);
    }

    // -- long features ----------------------------------------------------------

    /** scale, threshold, maxDepth, flatten, terrace, fill, style -- one row per cave region. */
    private double[] gashProfile(int region) {
        switch (region) {
            //                 scale  thresh  depth  flatten terrace fill style
            case Caves.CATHEDRAL:  return new double[]{235, 0.9490, 34, 1.4, 0, 0, GORGE};
            case Caves.EMBERVEINS: return new double[]{205, 0.9440, 60, 1.9, 5, 11, CANYON};
            case Caves.FROSTBORE:  return new double[]{130, 0.9700, 40, 2.8, 0, 0, CREVASSE};
            case Caves.FUNGAL:     return new double[]{170, 0.9610, 46, 1.8, 0, 9, RAVINE};
            default:               return new double[]{162, 0.9575, 52, 2.1, 0, 0, RAVINE};
        }
    }

    private void gash(int wx, int wz, int ground, Cut out) {
        int region = caves.region(wx, wz);
        double[] p = gashProfile(region);
        double scale = p[0], threshold = p[1];

        // Warp before the ridge lookup: a straight gash reads as a trench someone dug.
        double ax = wx + terrain.noise(wx / 215.0, wz / 215.0, 90) * 74;
        double az = wz + terrain.noise(wx / 215.0, wz / 215.0, 91) * 74;
        // A ridge field on its own draws closed loops of constant width, which read
        // as worm trails rather than landforms. Two corrections: a coarse presence
        // mask cuts each loop into separate features with tapering ends, and the
        // threshold itself wanders so the walls open out and pinch in along the way.
        double presence = terrain.noise(wx / 178.0, wz / 178.0, 101);
        double segment = (presence - 0.30) / 0.40;
        if (segment <= 0) return;
        segment = Math.min(1.0, segment);
        segment = segment * segment * (3 - 2 * segment);

        threshold -= terrain.noise(wx / 118.0, wz / 118.0, 102) * 0.0130;
        double line = 1.0 - Math.abs(terrain.noise(ax / scale, az / scale, 92));
        // A finer ridge field feeding the coarse one: side branches join the main
        // gash the way tributaries join a river, instead of every channel running alone.
        double branch = 1.0 - Math.abs(terrain.noise(ax / (scale * 0.34), az / (scale * 0.34), 103));
        if (branch > threshold + 0.006) line = Math.max(line, branch - 0.004);
        if (line <= threshold) return;

        double t = (line - threshold) / (1.0 - threshold);
        // Along-length modulation, so a canyon deepens, shallows and pinches out
        // instead of running at one depth from horizon to horizon.
        double along = terrain.noise(wx / 88.0, wz / 88.0, 93) * 0.5 + 0.5;
        double reach = (0.30 + 1.05 * along) * segment;
        if (reach <= 0.18) return;

        double shape = Math.min(1.0, t * p[3]);
        shape = shape * shape * (3 - 2 * shape);
        int depth = (int) Math.round(p[2] * shape * reach);
        if (depth <= 1) return;

        // A remnant span left across the gap. Natural bridges are the single most
        // striking thing a canyon can have and they cost one noise lookup.
        if (p[6] != CREVASSE && terrain.noise(wx / 37.0, wz / 37.0, 94) > 0.66
            && terrain.noise(wx / 260.0, wz / 260.0, 95) > 0.12) return;

        int terrace = (int) p[4];
        if (terrace > 0) depth = (depth / terrace) * terrace;
        if (depth <= 1) return;

        int floor = ground - depth;
        if (floor < 9) { depth = ground - 9; floor = 9; }
        if (depth <= 1) return;

        int fill = (int) p[5];
        int fillTop = fill == 9 ? waterTable(wx, wz) : fill == 11 ? lavaTable(wx, wz) : 0;
        take(out, (int) p[6], depth, t > 0.94, fill, fillTop);
    }

    // -- bounded features -------------------------------------------------------

    private int pitStyle(int region, long hash) {
        switch (region) {
            case Caves.CATHEDRAL:  return (hash & 3) == 0 ? CRATER : COLLAPSE;
            case Caves.EMBERVEINS: return (hash & 1) == 0 ? CALDERA : CRATER;
            case Caves.FROSTBORE:  return (hash & 3) == 0 ? SINKHOLE : CIRQUE;
            case Caves.FUNGAL:     return (hash & 1) == 0 ? CENOTE : SINKHOLE;
            default:               return (hash & 3) == 0 ? CRATER : SINKHOLE;
        }
    }

    private void pit(int wx, int wz, int ground, Cut out) {
        int baseX = Math.floorDiv(wx, PIT_CELL), baseZ = Math.floorDiv(wz, PIT_CELL);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cellX = baseX + ox, cellZ = baseZ + oz;
                long hash = Terrain.mix(terrain.seed + cellX * 0x27D4EB2F165667C5L + cellZ * 0x9E3779B97F4A7C15L + 411L);
                // Roughly two in five cells carry a pit; the rest is open country.
                if (Math.floorMod(hash, 100L) >= 21L) continue;
                long spin = Terrain.mix(hash);
                int cx = cellX * PIT_CELL + 24 + (int) Math.floorMod(spin, PIT_CELL - 48L);
                int cz = cellZ * PIT_CELL + 24 + (int) Math.floorMod(Terrain.mix(spin), PIT_CELL - 48L);
                int region = caves.region(cx, cz);
                int style = pitStyle(region, spin >>> 17);
                int radius = radiusFor(style, spin);
                if (Math.abs(wx - cx) > radius + 6 || Math.abs(wz - cz) > radius + 6) continue;

                // Lumpy outline: sampling a warped point makes the rim wander the way
                // erosion does, instead of drawing the circle the maths actually is.
                double px = wx + terrain.noise(wx / 13.0, wz / 13.0, 96) * radius * 0.17
                             + terrain.noise(wx / 34.0, wz / 34.0, 96) * radius * 0.10;
                double pz = wz + terrain.noise(wx / 13.0, wz / 13.0, 97) * radius * 0.17
                             + terrain.noise(wx / 34.0, wz / 34.0, 97) * radius * 0.10;
                double dist = Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
                double norm = dist / radius;
                if (norm > 1.18) continue;
                carvePit(out, style, radius, norm, wx, wz, ground, spin, cx, cz);
            }
        }
    }

    private static int radiusFor(int style, long spin) {
        int roll = (int) Math.floorMod(spin >>> 9, 100L);
        switch (style) {
            case CALDERA:  return 30 + roll * 28 / 100;
            case COLLAPSE: return 26 + roll * 28 / 100;
            case CIRQUE:   return 27 + roll * 24 / 100;
            case CRATER:   return 18 + roll * 24 / 100;
            case CENOTE:   return  9 + roll * 11 / 100;
            default:       return  8 + roll * 13 / 100;  // sinkhole
        }
    }

    private void carvePit(Cut out, int style, int radius, double norm, int wx, int wz, int ground, long spin,
                          int centreX, int centreZ) {
        int deep = 20 + (int) Math.floorMod(spin >>> 23, 34L);
        int depth = 0, rise = 0, fill = 0, fillTop = 0;
        boolean rim = false;
        switch (style) {
            case CRATER: {
                if (norm <= 1.0) depth = (int) Math.round(deep * (1.0 - norm * norm));
                // Ejecta piled on the lip, thinning outward.
                double lip = Math.abs(norm - 0.97);
                if (lip < 0.21) { rise = (int) Math.round((1 - lip / 0.21) * (3 + (deep >> 4))); rim = true; }
                break;
            }
            case CALDERA: {
                if (norm > 1.0) break;
                double bowl = 1.0 - norm * norm * norm;
                depth = (int) Math.round(deep * 0.75 * bowl);
                depth = (depth / 5) * 5;                       // terraced walls
                if (depth > 4) {
                    fill = 11;
                    // Levelled on the caldera's deepest point, not on this column.
                    fillTop = Math.max(9, centreGround(centreX, centreZ)
                        - ((int) Math.round(deep * 0.75) / 5) * 5) + 3;
                }
                double lip = Math.abs(norm - 0.98);
                if (lip < 0.15) { rise = (int) Math.round((1 - lip / 0.15) * 4); rim = true; }
                break;
            }
            case CIRQUE: {
                if (norm > 1.0) break;
                depth = (int) Math.round(deep * 0.55 * Math.cos(norm * Math.PI / 2));
                rim = norm > 0.90;
                break;
            }
            case SINKHOLE: {
                if (norm > 1.02) break;
                double wall = norm < 0.80 ? 1.0 : (1.02 - norm) / 0.22;
                depth = (int) Math.round(deep * 1.25 * wall * wall);
                rim = norm > 0.86;
                break;
            }
            case CENOTE: {
                if (norm > 1.02) break;
                double wall = norm < 0.82 ? 1.0 : (1.02 - norm) / 0.20;
                depth = (int) Math.round(deep * 1.15 * wall * wall);
                if (depth > 6) {
                    fill = 9;
                    fillTop = Math.max(9, centreGround(centreX, centreZ) - (int) Math.round(deep * 1.15)) + 7;
                }
                rim = norm > 0.88;
                break;
            }
            default: {  // COLLAPSE: a cave roof that gave way, ragged, with remnants
                if (norm > 1.0) break;
                double ragged = 0.62 + 0.42 * (terrain.noise(wx / 17.0, wz / 17.0, 98) * 0.5 + 0.5);
                if (norm > ragged) break;
                depth = (int) Math.round(deep * 1.35 * (1.0 - (norm / ragged) * (norm / ragged) * 0.45));
                // Remnant stone left standing where the roof held.
                if (terrain.noise(wx / 11.0, wz / 11.0, 99) > 0.58) return;
                rim = norm > ragged - 0.12;
                break;
            }
        }
        if (depth <= 0 && rise <= 0) return;
        int floor = ground - depth;
        if (floor < 9) { depth = ground - 9; floor = 9; }
        if (depth <= 0 && rise <= 0) return;
        if (rise > 0 && depth == 0) { if (rise > out.rise) { out.rise = rise; out.rim = true; if (out.style == NONE) out.style = style; } return; }
        take(out, style, depth, rim, fill, fillTop);
    }

    /** Deepest cut wins, so overlapping features read as one landform. */
    private static void take(Cut out, int style, int depth, boolean rim, int fill, int fillTop) {
        if (depth <= out.depth) { out.rim |= rim; return; }
        out.style = style;
        out.depth = depth;
        out.rim = rim;
        out.fill = fill;
        out.fillTop = fill == 0 ? 0 : fillTop;
    }
}
