package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Monastery of Leng (epoch 6): a gompa of yellow clay and grey stone climbing the land in five tiers (floors 0, 12, 24, 36 and
 * 48 above the gate), its retaining walls banded in ochre, rowed with dark windows and propped with piers, a long zigzag flight
 * of steps up each of the first three and one great stair to the summit. The High Priest wears a yellow silken mask; the whole
 * place wears it too, from the eyes of the gate-chorten to the colossal face over the door of his hall.
 * <ul>
 * <li>Tier 0, the Court of Wheels: the Gate of the Wheel (a gate-chorten whose passage ramps up from the ground in front),
 * two galleries of golden prayer wheels along the outer wall, a row of chortens with reliquary niches, corner turrets, the Door
 * of Bones and, thirty blocks under the court, the Ossuary of Leng: a nave of bone pillars, niches of sarcophagi, the Charnel
 * Chapel with its bone throne;</li>
 * <li>the first wall holds the Refectory of the Silent Meal; tier 1 carries the pavilion of the Great Prayer Wheel, chortens and
 * the first prayer tower;</li>
 * <li>the second wall is the Cloister of Cells (thirteen cells behind a covered walk, some with a chest, some with a cage); tier 2
 * is the Assembly Court (a pavilion, chortens, the second tower);</li>
 * <li>the third wall is cut into the Great Prayer Hall (a forest of pillars, the idol of the Faceless Lord); tier 3 carries the
 * Library of Forbidden Sutras (three floors and a ladder), the Wall of a Hundred Faces, the Hermitages cut into the next wall,
 * and the Last Stair;</li>
 * <li>tier 4, the Court of Masks, is the forecourt of the High Priest's Hall: a throne hall behind a mask twenty-five blocks high whose mouth
 * is the door, and on its roof the deck of the sky-burial (a disc of bone, the slab, flag poles), reached by a stair up the hall's
 * flank; two more prayer towers, two pavilions and strings of prayer flags between the pinnacles.</li>
 * </ul>
 * The whole design is levelled to the ground before the gate (its frame is raised or lowered to match), faced and footed on
 * whatever land it finds, and drawn column by column clipped to the chunk in a frame turned so that the tiers climb the slope.
 */
final class GreatLeng extends GreatDesign {
    static final int R = 50;
    /** Tier k: its floor (y above base), its front edge (local v) and its half-width; a tier spans v from its front to the next one's. */
    static final int[] FL = {0, 12, 24, 36, 48};
    static final int[] VF = {-50, -32, -14, 4, 22};
    static final int[] HW = {49, 46, 42, 38, 34};
    /** The zigzag flights: wall k (1..3), the foot's u (the flight climbs toward the middle, 24 columns, two a step). */
    static final int[] FLK = {1, 2, 3}, FOOT = {38, -40, 38};
    static final int FLEN = 24, FRUN = 2;
    private static final String[] TABLES = {Sites.DUNGEON, "minecraft:chests/abandoned_mineshaft", Sites.JUNGLE, "minecraft:chests/igloo_chest", Sites.CORRIDOR, Sites.DESERT};
    private static final String TREASURE = "minecraft:chests/end_city_treasure", MANSION = "minecraft:chests/woodland_mansion",
        CROSSING = "minecraft:chests/stronghold_crossing", MINESHAFT = "minecraft:chests/abandoned_mineshaft", IGLOO = "minecraft:chests/igloo_chest",
        NETHER = "minecraft:chests/nether_bridge";
    private static final int QUARTZ_STAIRS = 156, SANDSTONE_STAIRS = 128, CARPET = 171, WOOL = 35, PANE_STAINED = 160, SPONGE_ = 19;

    static final class Plan extends Layout {
        /** The frame's turn, the whole design's rise above the site's base (the gate stands on the ground in front), the ramp's residue. */
        int rot, dy, gateY;
    }

    // ------------------------------------------------------------------------------------------------------ the plan
    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.rot = uphill(s);
        Frame probe = new Frame(null, s.x, s.z, s.base, p.rot);
        int[] front = new int[5];
        int k = 0;
        for (int du : new int[] {-8, 0, 8}) front[k++] = s.surface(probe.wx(du, -52), probe.wz(du, -52)) - s.base;
        front[k++] = s.surface(probe.wx(-4, -50), probe.wz(-4, -50)) - s.base;
        front[k++] = s.surface(probe.wx(4, -50), probe.wz(4, -50)) - s.base;
        Arrays.sort(front);
        int ground = front[2];
        int dy = Math.max(-1, Math.min(12, ground));
        if (ground - dy < -14) dy = Math.max(-8, ground + 14);          // the front lies far below: sink the whole design (and forgo the crypt)
        p.dy = Math.min(dy, 155 - s.base);                              // the pinnacles (90 up) stay under the build limit
        p.gateY = Math.max(-14, Math.min(8, s.surface(probe.wx(0, -52), probe.wz(0, -52)) - s.base - p.dy));
        int t = (p.rot - s.rot) & 3;
        p.boss = turn(t, 0, 51 + p.dy, 46);
        // the Court of Wheels, the galleries, the Ossuary
        add(p, t, 8, 1, -38, "!cult_zealot+vindicator");
        add(p, t, -24, 1, -36, "ghoul+zombie_villager");
        add(p, t, 14, 1, -39, "husk+zombie");
        add(p, t, -30, 1, -47, "witch+cave_spider");
        add(p, t, 30, 1, -47, "creeper+spider");
        if (p.dy >= -1) {                                               // the crypt lies under the court only where the land lets it
            add(p, t, -24, -10, -41, "tomb_crawler+ghoul");
            add(p, t, -8, -10, -41, "wither_skeleton+skeleton");
            add(p, t, -34, -10, -40, "zombie+silverfish");
        }
        // the refectory, the terrace of the great wheel, the cloister, the first tower
        add(p, t, -26, 1, -26, "witch+zombie_villager");
        add(p, t, -14, 1, -27, "skeleton+stray");
        add(p, t, 20, 13, -28, "cult_adept+illusioner");
        add(p, t, -24, 13, -31, "deep_one+enderman");
        add(p, t, 14, 13, -16, "spider+cave_spider");
        add(p, t, 5, 14, -22, "star_spawn+hound");
        add(p, t, -42, 13, -24, "mi_go+enderman");
        add(p, t, -42, 33, -24, "nightgaunt");
        // the assembly court, the great prayer hall, the second tower
        add(p, t, -10, 25, -8, "!cult_zealot+vindicator+evoker");
        add(p, t, 10, 25, -6, "husk+zombie_villager");
        add(p, t, -20, 25, 12, "!star_spawn+wither_skeleton");
        add(p, t, 3, 25, 10, "cult_zealot+skeleton");
        add(p, t, 35, 25, -8, "witch+endermite");
        add(p, t, 35, 49, -8, "nightgaunt");
        // the court of the library, the library, the hermitages
        add(p, t, 0, 37, 7, "!cult_adept+hound");
        add(p, t, 26, 37, 15, "shoggoth+slime");
        add(p, t, -30, 37, 12, "silverfish+endermite+cult_adept");
        add(p, t, -30, 43, 12, "nightgaunt+cult_adept");
        add(p, t, -30, 49, 12, "shoggoth+cave_spider");
        // the forecourt, the flank courts, the towers of the summit, the hall, the deck of the sky-burial
        add(p, t, 0, 49, 26, "hound+creeper");
        add(p, t, -22, 49, 23, "witch+evoker");
        add(p, t, 25, 49, 40, "mi_go+stray");
        add(p, t, -29, 49, 40, "ghoul+tomb_crawler");
        add(p, t, -10, 49, 40, "cult_adept+illusioner");
        add(p, t, 10, 49, 36, "wither_skeleton+skeleton");
        add(p, t, -29, 77, 40, "nightgaunt");
        add(p, t, 25, 77, 40, "nightgaunt");
        add(p, t, -8, 64, 40, "nightgaunt+stray");
        add(p, t, 8, 64, 44, "nightgaunt");
        return p;
    }

    /** The frame whose back (local +v) faces the land's high side; the site's own turn where the land is flat. */
    static int uphill(Plans.GreatSite s) {
        int[] sum = new int[4];   // 0: +z is the back, 1: -x, 2: -z, 3: +x
        for (int d = 18; d <= 46; d += 14)
            for (int w = -24; w <= 24; w += 24) {
                sum[0] += s.surface(s.x + w, s.z + d);
                sum[1] += s.surface(s.x - d, s.z + w);
                sum[2] += s.surface(s.x + w, s.z - d);
                sum[3] += s.surface(s.x + d, s.z + w);
            }
        int best = s.rot & 3;
        for (int k = 0; k < 4; k++) if (sum[k] > sum[best] + 18) best = k;
        if (best != (s.rot & 3) && (best & 1) == 1) {            // nearly as good a slope with a turn that puts the axis on the world's grid: take it
            int e = sum[0] >= sum[2] ? 0 : 2;
            if (sum[e] > sum[best] - 36) best = e;
        }
        return best;
    }

    private static void add(Plan p, int t, int u, int y, int v, String pack) {
        int[] w = turn(t, u, y + p.dy, v);
        p.garrisons.add(g(w[0], w[1], w[2], pack));
    }

    /** A point of this design's frame in the site's frame (the design's frame is the site's turned t steps further). */
    private static int[] turn(int t, int u, int y, int v) {
        switch (t & 3) {
            case 1: return new int[] {-v, y, u};
            case 2: return new int[] {-u, y, -v};
            case 3: return new int[] {v, y, -u};
            default: return new int[] {u, y, v};
        }
    }

    // ------------------------------------------------------------------------------------------------------ drawing
    @Override void draw(Plans.GreatSite s, Layout l, Canvas c) {
        Plan p = (Plan) l;
        Pen q = new Pen(s, c, p.rot, R, p.dy);
        if (!q.empty()) {
            terrain(q);
            for (int i = 0; i < FLK.length; i++) flight(q, FLK[i], FOOT[i]);
            gate(q, p);
            galleries(q);
            chorten(q, -36, -42, 0, MINESHAFT, "trinket:0.2");
            chorten(q, -27, -42, 0, null, null);
            chorten(q, -18, -42, 0, IGLOO, null);
            chorten(q, 24, -42, 0, Sites.DUNGEON, "trinket:0.15");
            chorten(q, 36, -42, 0, null, null);
            if (p.dy >= -1) { ossuary(q); boneDoor(q); }
            lastStair(q);
            facade(q);
            hall(q);
            flankStair(q);
            refectory(q);
            cellRow(q, -14, 12, -8, 13, TABLES, new String[] {"SPIDER", "CAVE_SPIDER"}, 528, 2378);
            cloister(q);
            dukhang(q);
            library(q);
            cellRow(q, 22, 36, 10, 7, TABLES, new String[] {"STRAY", "WITCH"}, 8, 37);
            skyDeck(q);
            forecourt(q);
            wheelHouse(q);
            courts(q);
            gateSigns(q);
            if (p.dy >= -1) ossuaryTiles(q);
            hallTiles(q);
            cairns(q);
            maskWall(q);
            piers(q);
            pavilion(q, 0, -6, 24, 5, 3);
            maskGate(q);
            turret(q, -46, -47, 0, 9);
            turret(q, 46, -47, 0, 9);
            flags(q);
            tower(q, -27, 40, 48, 18);
            tower(q, 27, 40, 48, 18);
            tower(q, -40, -24, 12, 10);
            tower(q, 37, -8, 24, 14);
        }
    }

    // ------------------------------------------------------------------------------------------------------ materials
    private static final int MAS = -1, OCH = -2, PAV = -3, PLN = -4;

    private static int b(int id, int meta) { return id << 4 | meta; }

    /** Grey masonry: stone brick, cracked brick, andesite, polished andesite, a little moss. */
    private static void grey(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 201);
        if (r < 0.52) f.set(u, y, v, BRICK, 0);
        else if (r < 0.70) f.set(u, y, v, BRICK, 2);
        else if (r < 0.82) f.set(u, y, v, STONE, 5);
        else if (r < 0.93) f.set(u, y, v, STONE, 6);
        else if (r < 0.96) f.set(u, y, v, BRICK, 1);
        else f.set(u, y, v, COBBLE);
    }

    /** Ochre: yellow clay, smooth sandstone, now and then a sponge. */
    private static void ochre(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 202);
        if (r < 0.72) f.set(u, y, v, CLAY, 4);
        else if (r < 0.92) f.set(u, y, v, SANDSTONE, 2);
        else f.set(u, y, v, SPONGE_);
    }

    /** Paving: grey brick and andesite in a check, cracked here and there, the odd ochre tile. */
    private static void paving(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 203);
        if (r < 0.05) f.set(u, y, v, CLAY, 4);
        else if (r < 0.14) f.set(u, y, v, BRICK, 2);
        else if (((u + v) & 1) == 0) f.set(u, y, v, BRICK, 0);
        else f.set(u, y, v, STONE, 5);
    }

    /** Plinth: polished andesite and chiseled brick. */
    private static void plinth(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 204);
        if (r < 0.7) f.set(u, y, v, STONE, 6);
        else f.set(u, y, v, BRICK, 3);
    }

    // ------------------------------------------------------------------------------------------------------ the terrain
    /** The tier a column belongs to (-1: natural ground outside every tier). */
    static int level(int u, int v) {
        if (u < -R || u > R || v < -R || v > R) return -1;
        int k = -1;
        for (int i = 0; i < FL.length; i++) if (v >= VF[i] && (i == FL.length - 1 || v < VF[i + 1]) && Math.abs(u) <= HW[i]) k = i;
        return k;
    }

    /** Where a tier's front parapet opens: the heads of the zigzag flights. */
    private static boolean gap(int u, int v, int k) {
        if (v != VF[k]) return false;
        if (k == 4 && Math.abs(u) <= 6) return true;
        for (int i = 0; i < FLK.length; i++) {
            if (FLK[i] != k) continue;
            int head = FOOT[i] + (FOOT[i] > 0 ? -1 : 1) * (FLEN - 1);
            if (Math.abs(u - head) <= 2) return true;
        }
        return false;
    }

    /** Every column: levelled to its tier, faced where it stands over a lower one, the land cut back above its floor. */
    private static void terrain(Pen q) {
        Frame f = q.f;
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                int k = level(u, v);
                if (k < 0) continue;
                int H = FL[k], G = q.land(u, v);
                int ln = level(u, v - 1), ls = level(u, v + 1), lw = level(u - 1, v), le = level(u + 1, v);
                int low = Math.min(Math.min(ln, ls), Math.min(lw, le));
                boolean edge = low < k, natural = low < 0;
                boolean across = ln < k || ls < k;                               // the face runs along u
                int fromFace = natural ? G + 1 : FL[low] + 1;
                if (!edge) { for (int y = G + 1; y < H; y++) f.set(u, y, v, COBBLE, 0); }
                else {
                    for (int y = G + 1; y < Math.min(fromFace, H); y++) f.set(u, y, v, COBBLE, 0);   // hidden fill under the face
                    for (int y = fromFace; y < H; y++) face(f, u, y, v, H, fromFace, across);        // the face, even over natural rock
                }
                paving(f, u, H, v);
                if (G > H) {
                    if (natural) { for (int y = H + 1; y <= G; y++) grey(f, u, y, v); f.clear(u, v, G + 1, G + 2); }
                    else f.clear(u, v, H + 1, G + 2);
                } else f.clear(u, v, H + 1, H + 2);
                if (edge && !(natural && G > H) && !gap(u, v, k)) parapet(f, u, H + 1, v, k, across);
            }
    }

    /** The face of a retaining wall: a plinth, then bands of ochre every twelve rows with rows of dark windows between. */
    private static void face(Frame f, int u, int y, int v, int H, int from, boolean across) {
        int along = across ? u : v, top = H - y, t = (top - 1) % 12;
        if (t <= 1) ochre(f, u, y, v);
        else if (y - from < 2) plinth(f, u, y, v);
        else if (t == 2) f.set(u, y, v, BRICK, 3);
        else if (Math.floorMod(along, 8) == 0) f.set(u, y, v, STONE, 6);
        else if (Math.floorMod(along, 4) == 2 && (t == 4 || t == 5) && y - from >= 3) f.set(u, y, v, CLAY, 15);
        else grey(f, u, y, v);
    }

    private static void parapet(Frame f, int u, int y, int v, int k, boolean across) {
        if (k == 0) {                                                  // the outer wall of the forecourt
            for (int h = 0; h < 4; h++) grey(f, u, y + h, v);
            if (((u + v) & 1) == 0) grey(f, u, y + 4, v);
            return;
        }
        int along = across ? u : v;
        if (Math.floorMod(along, 12) == 6) { f.set(u, y, v, BRICK, 3); f.set(u, y + 1, v, GLOWSTONE, 0); }
        else if (Math.floorMod(along, 4) == 0) f.set(u, y, v, BRICK, 3);
        else f.set(u, y, v, WALL, 0);
    }

    // ------------------------------------------------------------------------------------------------------ the zigzag flights
    /** A flight up a retaining wall: a wedge of masonry against its face, treads two columns to the step, railed on its outer side. */
    private static void flight(Pen q, int k, int foot) {
        Frame f = q.f;
        int lo = FL[k - 1], vf = VF[k], dir = foot > 0 ? -1 : 1;
        int a0 = Math.min(foot, foot + dir * (FLEN - 1)), a1 = Math.max(foot, foot + dir * (FLEN - 1));
        if (!q.hit(a0, vf - 4, a1, vf - 1)) return;
        for (int i = 0; i < FLEN; i++) {
            int u = foot + dir * i;
            if (u < q.u0 || u > q.u1) continue;
            int p = lo + 1 + i / FRUN;
            for (int v = vf - 4; v <= vf - 1; v++) {
                if (!q.in(u, v)) continue;
                for (int y = lo + 1; y < p; y++) grey(f, u, y, v);
                if (v == vf - 4) {
                    f.set(u, p, v, BRICK, 3);
                    f.set(u, p + 1, v, WALL, 0);
                    if (i % 8 == 4) { f.set(u, p + 2, v, WALL, 0); f.set(u, p + 3, v, GLOWSTONE, 0); }
                    else f.clear(u, v, p + 2, p + 4);
                } else {
                    if (i % FRUN == 0) f.set(u, p, v, BRICK_STAIRS, f.stairs(dir, 0, false));
                    else f.set(u, p, v, BRICK, 0);
                    f.clear(u, v, p + 1, p + 4);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ small works
    private static final int AIRM = 0;

    private static void air(Pen q, int a0, int y0, int b0, int a1, int y1, int b1) { q.box(a0, y0, b0, a1, y1, b1, AIRM); }

    /** A stair block ascending toward local (du, dv). */
    private static void stair(Pen q, int u, int y, int v, int du, int dv, int id, boolean upside) {
        if (q.in(u, v)) q.f.set(u, y, v, id, q.f.stairs(du, dv, upside));
    }

    /** A butter-lamp post: a stone-wall column with a glowstone lamp on its head. */
    private static void lampPost(Pen q, int u, int y, int v, int h) {
        if (!q.in(u, v)) return;
        for (int i = 0; i < h; i++) q.f.set(u, y + i, v, WALL, 0);
        q.f.set(u, y + h, v, GLOWSTONE, 0);
    }

    /** A prayer wheel: a plinth, a yellow drum two high and a spindle. */
    private static void wheel(Pen q, int u, int y, int v) {
        if (!q.in(u, v)) return;
        q.f.set(u, y, v, BRICK, 3);
        q.f.set(u, y + 1, v, CLAY, 4);
        q.f.set(u, y + 2, v, CLAY, 4);
        q.f.set(u, y + 3, v, FENCE, 0);
    }

    /**
     * A chorten: a stepped plinth, a vase with its ochre band, the harmika with its eyes and nose, a spire. A reliquary chest stands
     * in the niche of its front (-v) face when {@code table} is given.
     */
    private static void chorten(Pen q, int cu, int cv, int y0, String table, String extras) {
        if (!q.hit(cu - 3, cv - 3, cu + 3, cv + 3)) return;
        q.box(cu - 3, y0 + 1, cv - 3, cu + 3, y0 + 1, cv + 3, PLN);
        q.box(cu - 2, y0 + 2, cv - 2, cu + 2, y0 + 5, cv + 2, MAS);
        q.box(cu - 2, y0 + 5, cv - 2, cu + 2, y0 + 5, cv + 2, OCH);
        q.box(cu - 1, y0 + 6, cv - 1, cu + 1, y0 + 7, cv + 1, b(CLAY, 4));
        q.box(cu - 1, y0 + 8, cv - 1, cu + 1, y0 + 8, cv + 1, b(SANDSTONE, 2));
        q.set(cu, y0 + 9, cv, BRICK, 3);
        q.set(cu, y0 + 10, cv, SANDSTONE, 2);
        q.set(cu, y0 + 11, cv, SPONGE_, 0);
        for (int sv = -1; sv <= 1; sv += 2) {                            // the face of the harmika: two eyes and a nose
            q.set(cu - 1, y0 + 7, cv + sv, CLAY, 15);
            q.set(cu + 1, y0 + 7, cv + sv, CLAY, 15);
            q.set(cu, y0 + 7, cv + sv, SPONGE_, 0);
        }
        for (int su = -1; su <= 1; su += 2) q.set(cu + su, y0 + 7, cv, CLAY, 15);
        if (table != null) chest(q, cu, y0 + 2, cv - 2, 0, -1, table, extras);
    }

    // ------------------------------------------------------------------------------------------------------ the gate
    /** The Gate of the Wheel: a gate-chorten of two stepped storeys with the eyes of the mask on its harmika, a ramped passage through its foot. */
    private static void gate(Pen q, Plan p) {
        if (!q.hit(-8, -50, 8, -34)) return;
        Frame f = q.f;
        int gy = p.gateY;
        q.box(-8, 1, -50, 8, 2, -42, PLN);
        q.box(-7, 3, -49, 7, 7, -43, MAS);
        q.box(-7, 8, -49, 7, 8, -43, OCH);
        q.box(-6, 9, -48, 6, 10, -44, MAS);
        q.box(-6, 11, -48, 6, 11, -44, OCH);
        q.box(-5, 12, -48, 5, 14, -44, MAS);
        q.box(-5, 14, -48, 5, 14, -44, OCH);
        q.box(-4, 15, -48, 4, 19, -44, b(CLAY, 4));
        q.box(-4, 19, -48, 4, 19, -44, b(SANDSTONE, 2));
        q.box(-3, 20, -47, 3, 21, -45, MAS);
        q.box(-3, 21, -47, 3, 21, -45, OCH);
        // the eyes of the mask: black slits with a glowstone pupil, a sponge nose, on the front and the back of the harmika
        for (int v = -48; v <= -44; v += 4) {
            q.box(-3, 17, v, -1, 18, v, b(CLAY, 15));
            q.box(1, 17, v, 3, 18, v, b(CLAY, 15));
            q.set(-2, 17, v, GLOWSTONE, 0);
            q.set(2, 17, v, GLOWSTONE, 0);
            q.box(0, 15, v, 0, 18, v, b(SPONGE_, 0));
            q.box(-3, 19, v, 3, 19, v, b(CLAY, 15));
        }
        // the thirteen wheels of the spire
        for (int i = 0; i < 12; i++) {
            int y = 22 + i;
            int hu = (i % 3 == 0) ? 1 : 0;
            int id = i % 3 == 0 ? CLAY : i % 3 == 1 ? SANDSTONE : BRICK, meta = i % 3 == 0 ? 4 : i % 3 == 1 ? 2 : 3;
            q.box(-hu, y, -46 - hu, hu, y, -46 + hu, b(id, meta));
        }
        q.set(0, 34, -46, SPONGE_, 0);
        q.set(0, 35, -46, GLOWSTONE, 0);
        // the passage: five wide, ramped to the land outside, an arch of ochre over it
        for (int v = -50; v <= -42; v++) {
            int t = v + 50;
            int pos = gy < 0 ? Math.min(0, gy + 1 + t) : Math.max(0, gy - t);
            int top = Math.max(6, pos + 4);
            for (int u = -2; u <= 2; u++) {
                if (!q.in(u, v)) continue;
                int G = q.land(u, v);
                for (int y = Math.min(G, pos - 1) + 1; y < pos; y++) grey(f, u, y, v);
                if (pos == 0 && !(gy < 0 && t == -gy - 1)) f.set(u, 0, v, STONE, 6);
                else if (gy < 0) f.set(u, pos, v, BRICK_STAIRS, f.stairs(0, 1, false));
                else if (t == 0) f.set(u, pos, v, BRICK, 0);
                else f.set(u, pos, v, BRICK_STAIRS, f.stairs(0, -1, false));
                f.clear(u, v, pos + 1, top);
                f.set(u, top + 1, v, (u == 0 && (v == -50 || v == -42)) ? GLOWSTONE : CLAY, 4);
            }
            for (int s = -1; s <= 1; s += 2) if (q.in(3 * s, v)) f.set(3 * s, top, v, BRICK, 3);
        }
        // where the ground in front lies far below the court, the stair carries on in a sunken way between two walls
        for (int v = -41; gy < 0 && v <= -50 + (-gy - 1) && v <= -34; v++) {
            int pos = gy + 1 + (v + 50);
            if (pos > 0) break;
            for (int u = -3; u <= 3; u++) {
                if (!q.in(u, v)) continue;
                if (Math.abs(u) == 3) {
                    for (int y = Math.min(pos, 0); y <= 0; y++) grey(f, u, y, v);
                    f.set(u, 1, v, WALL, 0);
                    continue;
                }
                for (int y = Math.min(q.land(u, v), pos - 1) + 1; y < pos; y++) grey(f, u, y, v);
                f.set(u, pos, v, BRICK_STAIRS, f.stairs(0, 1, false));
                f.clear(u, v, pos + 1, 4);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            lampPost(q, 9 * s, 1, -48, 3);
            lampPost(q, 9 * s, 1, -43, 3);
        }
    }

    // ------------------------------------------------------------------------------------------------------ the court of wheels
    /** The wheel galleries on either side of the gate: a roofed walk along the outer wall with a row of golden prayer wheels. */
    private static void galleries(Pen q) {
        Frame f = q.f;
        for (int s = -1; s <= 1; s += 2) {
            int a0 = Math.min(s * 11, s * 41), a1 = Math.max(s * 11, s * 41);
            if (!q.hit(a0, -49, a1, -46)) continue;
            for (int u = Math.max(q.u0, a0); u <= Math.min(q.u1, a1); u++) {
                int au = Math.abs(u);
                for (int v = Math.max(q.v0, -49); v <= Math.min(q.v1, -46); v++) {
                    if (v == -46 && (au - 11) % 4 == 0) for (int y = 1; y <= 4; y++) f.set(u, y, v, STONE, 6);
                    f.set(u, 5, v, SLAB, 1);                                   // the roof: a slab of sandstone
                    if (v == -49 && au % 2 == 1) wheel(q, u, 1, v);
                    if (v == -46 && (au - 11) % 4 == 0) f.set(u, 5, v, SANDSTONE, 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ the door of bones and the ossuary
    private static final int OSS = -5, OFL = -6, OSS_TOP = -4, OSS_FLOOR = -11;

    private static void ossuaryStone(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 211);
        if (r < 0.40) f.set(u, y, v, BRICK, 2);
        else if (r < 0.68) f.set(u, y, v, BRICK, 0);
        else if (r < 0.86) f.set(u, y, v, BONE, 0);
        else f.set(u, y, v, COBBLE, 0);
    }

    private static void ossuaryFloor(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 212);
        if (r < 0.45) f.set(u, y, v, BRICK, 0);
        else if (r < 0.75) f.set(u, y, v, STONE, 6);
        else if (r < 0.90) f.set(u, y, v, BRICK, 2);
        else f.set(u, y, v, BONE, 0);
    }

    /** A little tomb-house with a black door; the stair of the Ossuary goes down inside it, east and under the court. */
    private static void boneDoor(Pen q) {
        if (!q.hit(-48, -42, -34, -37)) return;
        Frame f = q.f;
        q.box(-48, 1, -42, -42, 4, -37, MAS);
        air(q, -47, 1, -41, -43, 4, -38);
        q.box(-48, 5, -42, -42, 5, -37, OCH);
        air(q, -46, 1, -37, -44, 3, -37);                                   // the door, on the court side
        q.box(-47, 4, -37, -43, 4, -37, b(CLAY, 15));
        q.box(-46, 5, -37, -44, 5, -37, b(BONE, 0));
        for (int u = -48; u <= -42; u += 6) for (int v = -42; v <= -37; v += 5) q.box(u, 6, v, u, 7, v, b(BONE, 0));
        q.set(-45, 6, -42, GLOWSTONE, 0);
        // the stair: eleven steps east and down, three wide, in a tunnel of grey stone
        for (int i = 0; i <= 10; i++) {
            int u = -46 + i, pos = -i;
            if (u < q.u0 || u > q.u1) continue;
            boolean room = u <= -43;
            for (int v = -42; v <= -38; v++) {
                if (!q.in(u, v)) continue;
                boolean side = v == -42 || v == -38;
                if (side) { for (int y = pos; y <= (room ? (v == -38 ? -1 : 0) : pos + 4); y++) grey(f, u, y, v); continue; }
                f.set(u, pos, v, BRICK_STAIRS, f.stairs(-1, 0, false));
                if (room) f.clear(u, v, pos + 1, 4);
                else { f.clear(u, v, pos + 1, pos + 3); grey(f, u, pos + 4, v); }
            }
        }
    }

    /**
     * The Ossuary of Leng, thirty blocks under the court: a long nave of bone pillars where the stair arrives, aisles of niches with
     * sarcophagi, and at its east end the Charnel Chapel with the bone throne of those the sky refused.
     */
    private static void ossuary(Pen q) {
        if (!q.hit(-36, -49, -3, -32)) return;
        Frame f = q.f;
        q.box(-36, OSS_FLOOR, -46, -13, -3, -34, OSS);
        q.box(-35, OSS_FLOOR, -45, -14, OSS_FLOOR, -35, OFL);
        air(q, -35, -10, -45, -14, OSS_TOP, -35);
        // the pillars of the nave
        for (int u = -32; u <= -16; u += 4)
            for (int v = -42; v <= -38; v += 4) {
                if (!q.in(u, v)) continue;
                f.set(u, -10, v, BRICK, 3);
                for (int y = -9; y <= -5; y++) f.set(u, y, v, QUARTZ, 2);
                f.set(u, OSS_TOP, v, BRICK, 3);
            }
        // the niches: south (deep) and north (shallow), each with a sarcophagus
        for (int u = -31; u <= -16; u += 5) {
            alcove(q, u, -46, -1);
            alcove(q, u, -34, 1);
        }
        // the Charnel Chapel
        q.box(-13, OSS_FLOOR, -48, -3, -3, -34, OSS);
        air(q, -12, -10, -47, -4, OSS_TOP, -35);
        q.box(-12, OSS_FLOOR, -47, -4, OSS_FLOOR, -35, OFL);
        air(q, -13, -10, -42, -13, -7, -40);                                // its doorway
        for (int v = -47; v <= -35; v += 12) for (int u = -12; u <= -4; u += 8) {
            if (!q.in(u, v)) continue;
            f.set(u, -10, v, BRICK, 3);
            for (int y = -9; y <= -5; y++) f.set(u, y, v, QUARTZ, 2);
            f.set(u, OSS_TOP, v, BRICK, 3);
        }
        // the bone throne at the east wall, between two braziers
        q.box(-6, -10, -43, -4, -10, -39, b(BONE, 0));
        q.box(-5, -9, -42, -4, -9, -40, b(BONE, 0));
        q.box(-4, -8, -42, -4, -5, -40, b(BONE, 0));
        q.set(-4, -4, -41, GLOWSTONE, 0);
        q.set(-6, -9, -41, SLAB, 13);
        for (int v = -44; v <= -38; v += 6) { q.set(-5, -10, v, BRICK, 3); q.set(-5, -9, v, MAGMA, 0); }
    }

    /** A niche in a wall of the nave: a recess three wide with a sarcophagus of quartz (dir -1 the south wall, 1 the north wall). */
    private static void alcove(Pen q, int u, int vWall, int dir) {
        int depth = dir < 0 ? 3 : 2;
        int rA = dir < 0 ? vWall - (depth - 1) : vWall, rB = dir < 0 ? vWall : vWall + depth - 1;
        int sA = dir < 0 ? vWall - depth : vWall, sB = dir < 0 ? vWall : vWall + depth;
        if (!q.hit(u - 2, sA, u + 2, sB)) return;
        q.box(u - 2, OSS_FLOOR, sA, u + 2, -5, sB, OSS);
        q.box(u - 1, OSS_FLOOR, rA, u + 1, OSS_FLOOR, rB, OFL);
        air(q, u - 1, -10, rA, u + 1, -6, rB);
        int fa = dir < 0 ? rA : rA + 1, fb = dir < 0 ? rB - 1 : rB;
        q.box(u - 1, -10, fa, u + 1, -10, fb, b(QUARTZ, 0));
        q.box(u - 1, -9, fa, u + 1, -9, fb, b(SLAB, 7));
    }

    // ------------------------------------------------------------------------------------------------------ the last stair and the court of masks
    /** The Last Stair: eleven wide, twelve steps up the wall of the summit, railed, lamps on its rails. */
    private static void lastStair(Pen q) {
        if (!q.hit(-7, 10, 7, 21)) return;
        Frame f = q.f;
        for (int i = 0; i < 12; i++) {
            int v = 10 + i, pos = 37 + i;
            for (int u = Math.max(q.u0, -6); u <= Math.min(q.u1, 6); u++) {
                if (!q.in(u, v)) continue;
                for (int y = 37; y < pos; y++) grey(f, u, y, v);
                if (Math.abs(u) == 6) {
                    f.set(u, pos, v, BRICK, 3);
                    f.set(u, pos + 1, v, WALL, 0);
                    if (i % 4 == 1) { f.set(u, pos + 2, v, WALL, 0); f.set(u, pos + 3, v, GLOWSTONE, 0); }
                    else f.clear(u, v, pos + 2, pos + 4);
                } else {
                    f.set(u, pos, v, BRICK_STAIRS, f.stairs(0, 1, false));
                    f.clear(u, v, pos + 1, pos + 4);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ the High Priest's hall
    static final int HY = 48, HROOF = 63;

    private static final int OUT = 0, FILL = 1, BLACK = 2, GLOW = 3, SPONGE_C = 4, SAND = 5, DOOR = 6;

    /** The half-width of the mask at height y (49..73); -1 outside it. */
    private static int maskW(int y) {
        if (y < 49 || y > 73) return -1;
        double t = (y - 61) / 12.5;
        return (int) Math.round(8.2 * Math.sqrt(Math.max(0, 1 - t * t)));
    }

    /** What the mask is made of at (u, y): outline, eyes with their glowing pupils, brows, nose, cheeks, and the mouth that is the door. */
    private static int maskCell(int u, int y) {
        int w = maskW(y), au = Math.abs(u);
        if (w < 0 || au > w) return OUT;
        if (y <= 55 && au <= 2) return DOOR;
        if (au == 3 && y <= 56 || y == 56 && au <= 3) return BLACK;
        if (y == 63 && au >= 3 && au <= 6) return BLACK;
        if (y == 62 && au >= 2 && au <= 5) return au == 4 ? GLOW : BLACK;
        if (y == 65 && au >= 2 && au <= 7) return SPONGE_C;
        if (au == 0 && y >= 57 && y <= 63) return SPONGE_C;
        if (au == 1 && y >= 57 && y <= 58) return SAND;
        if ((y == 58 || y == 59) && au >= 5) return SAND;
        if (au + Math.abs(y - 69) <= 1) return au == 0 && y == 69 ? SPONGE_C : BLACK;
        boolean edge = au == w || maskW(y + 1) < au || maskW(y - 1) < au;
        return edge ? BLACK : FILL;
    }

    /** The front wall of the hall: grey wings, and between them the colossal yellow mask of the High Priest, its mouth the door. */
    private static void facade(Pen q) {
        if (!q.hit(-15, 29, 15, 31)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -15); u <= Math.min(q.u1, 15); u++) {
            int au = Math.abs(u);
            for (int v = Math.max(q.v0, 30); v <= Math.min(q.v1, 31); v++) {
                for (int y = 49; y <= 75; y++) {
                    if (v == 31) { if (au <= 8 && maskCell(u, y) == DOOR) f.set(u, y, v, AIR, 0); else grey(f, u, y, v); continue; }
                    if (au >= 14) { if (y >= 74) f.set(u, y, v, y == 77 ? SPONGE_ : CLAY, 4); else if (y % 6 == 5) f.set(u, y, v, BRICK, 3); else grey(f, u, y, v); continue; }
                    switch (maskCell(u, y)) {
                        case DOOR: f.set(u, y, v, AIR, 0); break;
                        case BLACK: f.set(u, y, v, CLAY, 15); break;
                        case GLOW: f.set(u, y, v, GLOWSTONE, 0); break;
                        case SPONGE_C: f.set(u, y, v, SPONGE_, 0); break;
                        case SAND: f.set(u, y, v, SANDSTONE, 2); break;
                        case FILL: {
                            double r = f.roll(u, y, v, 221);
                            if (r < 0.70) f.set(u, y, v, CLAY, 4); else f.set(u, y, v, SANDSTONE, 2);
                            break;
                        }
                        default:
                            if (y >= 74) f.set(u, y, v, CLAY, 4);
                            else if (y <= 50) f.set(u, y, v, STONE, 6);
                            else if (y >= 71 || au >= 12 && y >= 60 && y <= 62) f.set(u, y, v, CLAY, 4);
                            else if (au == 9 || au == 13) f.set(u, y, v, STONE, 6);
                            else grey(f, u, y, v);
                    }
                }
                if (v == 30 && au <= 13 && ((u & 1) == 0)) f.set(u, 76, v, CLAY, 4);          // the crest
            }
        }
        // the windows in the wings
        for (int s = -1; s <= 1; s += 2)
            for (int u = 10; u <= 12; u += 2)
                for (int y = 52; y <= 57; y++) if (maskW(y) < u) { q.set(s * u, y, 30, PANE_STAINED, 4); q.set(s * u, y, 31, AIR, 0); }
        // the nose stands out of the face
        for (int y = 57; y <= 62; y++) q.set(0, y, 29, SPONGE_, 0);
        q.set(-1, 57, 29, SANDSTONE, 2);
        q.set(1, 57, 29, SANDSTONE, 2);
        // horns of the crown
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 77; y <= 83; y++) q.set(s * 10, y, 30, y % 2 == 1 ? CLAY : SANDSTONE, y % 2 == 1 ? 4 : 2);
            q.set(s * 10, 84, 30, SPONGE_, 0);
            q.set(s * 10, 85, 30, GLOWSTONE, 0);
        }
    }

    private static void hallFloor(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 222);
        if (Math.abs(u) <= 2 && v >= 32) f.set(u, y, v, CLAY, 4);
        else if (r < 0.10) f.set(u, y, v, BRICK, 2);
        else if ((((u >> 1) + (v >> 1)) & 1) == 0) f.set(u, y, v, STONE, 6);
        else f.set(u, y, v, BRICK, 0);
    }

    /** The hall: its side and back walls, the nave between two rows of pillars, the dais and throne, the deck of the sky-burial on its roof. */
    private static void hall(Pen q) {
        if (!q.hit(-15, 30, 15, 50)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -15); u <= Math.min(q.u1, 15); u++)
            for (int v = Math.max(q.v0, 30); v <= Math.min(q.v1, 50); v++) {
                int au = Math.abs(u);
                if (v <= 31) continue;
                boolean wall = v >= 49 || au >= 14;
                if (wall) {
                    for (int y = 49; y <= 62; y++) {
                        if (y <= 50) f.set(u, y, v, STONE, 6);
                        else if (y >= 61) ochre(f, u, y, v);
                        else grey(f, u, y, v);
                    }
                } else {
                    hallFloor(f, u, HY, v);
                    f.clear(u, v, 49, 62);
                }
                paving(f, u, HROOF, v);
                if (au == 15 || v == 50) { if (!(au == 15 && v >= 46 && v <= 49)) { if (((u + v) & 1) == 0) f.set(u, HROOF + 1, v, BRICK, 3); else f.set(u, HROOF + 1, v, WALL, 0); } }
            }
        // windows in the side walls
        for (int s = -1; s <= 1; s += 2)
            for (int v = 36; v <= 44; v += 4)
                for (int y = 53; y <= 57; y++) { q.set(s * 15, y, v, PANE_STAINED, 4); q.set(s * 14, y, v, AIR, 0); }
        // the pillars of the nave, banded, with a lamp on every other one
        for (int s = -1; s <= 1; s += 2)
            for (int v = 36; v <= 44; v += 4) {
                int u = 6 * s;
                if (!q.in(u, v)) continue;
                f.set(u, 49, v, BRICK, 3);
                for (int y = 50; y <= 60; y++) f.set(u, y, v, y % 5 == 0 ? CLAY : QUARTZ, y % 5 == 0 ? 4 : 2);
                f.set(u, 61, v, BRICK, 3);
                f.set(u, 62, v, CLAY, 4);
                if (v == 40) f.set(u + (s < 0 ? 1 : -1), 58, v, GLOWSTONE, 0);
            }
        // yellow silk hung on the side walls
        for (int s = -1; s <= 1; s += 2)
            for (int v = 34; v <= 46; v += 4)
                for (int y = 52; y <= 59; y++) { q.set(s * 13, y, v, WOOL, 4); q.set(s * 13, y, v + 1, WOOL, y == 52 ? 7 : 4); }
        // the dais: two steps and the throne against the back wall
        q.box(-8, 49, 41, 8, 49, 48, b(STONE, 6));
        q.box(-5, 50, 44, 5, 50, 48, b(STONE, 6));
        for (int u = -8; u <= 8; u++) stair(q, u, 49, 40, 0, 1, QUARTZ_STAIRS, false);
        for (int u = -5; u <= 5; u++) stair(q, u, 50, 43, 0, 1, QUARTZ_STAIRS, false);
        q.set(0, 51, 48, QUARTZ_STAIRS, f.stairs(0, 1, false));
        q.set(-1, 51, 48, SLAB, 7);
        q.set(1, 51, 48, SLAB, 7);
        for (int y = 52; y <= 59; y++) for (int u = -2; u <= 2; u++) q.set(u, y, 48, (Math.abs(u) == 2 || y == 59) ? CLAY : SANDSTONE, (Math.abs(u) == 2 || y == 59) ? 4 : 2);
        q.set(0, 56, 48, SPONGE_, 0);
        for (int y = 52; y <= 55; y++) q.set(0, y, 48, CLAY, 4);
        // ochre beams under the roof between the pillars, and two pillars of fire either side of the throne
        for (int v = 34; v <= 46; v += 4) q.box(-13, 62, v, 13, 62, v, b(CLAY, 4));
        for (int s = -1; s <= 1; s += 2) {
            q.set(7 * s, 50, 47, BRICK, 3);
            q.set(7 * s, 51, 47, BRICK, 3);
            q.set(7 * s, 52, 47, MAGMA, 0);
        }
    }

    /** The stair up the east flank of the hall to the sky-burial deck on its roof: three wide, fifteen steps, railed outside. */
    private static void flankStair(Pen q) {
        if (!q.hit(16, 31, 19, 50)) return;
        Frame f = q.f;
        for (int i = 0; i < 15; i++) {
            int v = 31 + i, pos = HY + 1 + i;
            for (int u = 16; u <= 19; u++) {
                if (!q.in(u, v)) continue;
                for (int y = HY + 1; y < pos; y++) grey(f, u, y, v);
                if (u == 19) {
                    f.set(u, pos, v, BRICK, 3);
                    f.set(u, pos + 1, v, WALL, 0);
                } else {
                    f.set(u, pos, v, BRICK_STAIRS, f.stairs(0, 1, false));
                    f.clear(u, v, pos + 1, pos + 4);
                }
            }
        }
        for (int v = 46; v <= 50; v++)                                       // the landing on the roof
            for (int u = 16; u <= 19; u++) {
                if (!q.in(u, v)) continue;
                for (int y = HY + 1; y < HROOF; y++) grey(f, u, y, v);
                paving(f, u, HROOF, v);
                f.clear(u, v, HROOF + 1, HROOF + 3);
                if (u == 19 || v == 50) f.set(u, HROOF + 1, v, WALL, 0);
            }
    }

    // ------------------------------------------------------------------------------------------------------ prayer towers
    /**
     * A prayer tower: a battered shaft with a door, a ladder to its wheel chamber and a mezzanine every eight blocks, the chamber
     * open on all sides round the great yellow wheel, a stepped roof of sandstone with a sponge pinnacle. {@code h1} is the height of
     * its upper shaft; the door faces -v.
     */
    private static void tower(Pen q, int cu, int cv, int y0, int h1) {
        if (!q.hit(cu - 5, cv - 5, cu + 5, cv + 5)) return;
        Frame f = q.f;
        int ct = y0 + 10 + h1;                                                 // the chamber's floor
        for (int a = -4; a <= 4; a++)
            for (int bb = -4; bb <= 4; bb++) {
                int u = cu + a, v = cv + bb, m = Math.max(Math.abs(a), Math.abs(bb));
                if (!q.in(u, v)) continue;
                boolean inner = m <= 2;
                f.set(u, y0, v, STONE, 6);
                for (int y = y0 + 1; y <= ct; y++) {
                    boolean stageA = y <= y0 + 9;
                    boolean wall = stageA ? m >= 3 : m == 3;
                    if (m > 3 && !stageA) continue;
                    if (inner) { f.set(u, y, v, AIR, 0); continue; }
                    if (!wall) continue;
                    if (stageA && y >= y0 + 8) ochre(f, u, y, v);
                    else if (!stageA && y == y0 + 10) f.set(u, y, v, BRICK, 3);
                    else if (y <= y0 + 2 && stageA) plinth(f, u, y, v);
                    else grey(f, u, y, v);
                }
            }
        // the door, a lintel of ochre, bars in the walls
        air(q, cu - 1, y0 + 1, cv - 4, cu + 1, y0 + 4, cv - 3);
        q.box(cu - 1, y0 + 5, cv - 4, cu + 1, y0 + 5, cv - 3, b(CLAY, 4));
        smallMask(q, cu, y0 + 6, cv - 4);
        q.box(cu - 4, y0 + 5, cv, cu - 4, y0 + 6, cv, b(BARS, 0));
        air(q, cu - 3, y0 + 5, cv, cu - 3, y0 + 6, cv);
        for (int y = y0 + 12; y < ct - 3; y += 8) {
            q.box(cu - 3, y, cv, cu - 3, y + 1, cv, b(BARS, 0));
            q.box(cu, y, cv - 3, cu, y + 1, cv - 3, b(BARS, 0));
            q.box(cu, y, cv + 3, cu, y + 1, cv + 3, b(BARS, 0));
        }
        q.box(cu, y0 + 5, cv + 4, cu, y0 + 6, cv + 4, b(BARS, 0));
        air(q, cu, y0 + 5, cv + 3, cu, y0 + 6, cv + 3);
        // the mezzanines, with a hole at the ladder
        for (int y = y0 + 6; y < ct - 2; y += 8)
            for (int a = -2; a <= 2; a++)
                for (int bb = -2; bb <= 2; bb++) {
                    if (a == 2 && bb == 0) continue;
                    q.set(cu + a, y, cv + bb, STONE, 6);
                }
        // the ladder, on the east wall of the shaft
        for (int y = y0 + 1; y <= ct + 1; y++) q.set(cu + 2, y, cv, LADDER, f.facing(-1, 0));
        // the wheel chamber: the floor, corner pillars, a low rail, the great wheel
        for (int a = -3; a <= 3; a++)
            for (int bb = -3; bb <= 3; bb++) {
                int u = cu + a, v = cv + bb;
                if (!q.in(u, v)) continue;
                if (a == 2 && bb == 0) { f.clear(u, v, ct, ct + 1); continue; }
                f.set(u, ct, v, STONE, 6);
                boolean corner = Math.abs(a) == 3 && Math.abs(bb) == 3, rim = Math.max(Math.abs(a), Math.abs(bb)) == 3;
                if (corner) { for (int y = ct + 1; y <= ct + 6; y++) f.set(u, y, v, QUARTZ, 2); f.set(u, ct + 7, v, CLAY, 4); }
                else if (rim) { f.set(u, ct + 1, v, WALL, 0); f.clear(u, v, ct + 2, ct + 6); }
                else f.clear(u, v, ct + 1, ct + 6);
            }
        for (int a = -1; a <= 1; a++)
            for (int bb = -1; bb <= 1; bb++)
                for (int y = ct + 1; y <= ct + 4; y++) {
                    if (!q.in(cu + a, cv + bb)) continue;
                    f.set(cu + a, y, cv + bb, CLAY, y == ct + 3 ? 15 : 4);
                }
        q.set(cu, ct + 5, cv, FENCE, 0);
        q.set(cu, ct + 6, cv, FENCE, 0);
        // the stepped roof
        for (int k = 0; k <= 4; k++) {
            int hu = 4 - k, y = ct + 7 + k;
            for (int a = -hu; a <= hu; a++)
                for (int bb = -hu; bb <= hu; bb++) {
                    int u = cu + a, v = cv + bb;
                    if (!q.in(u, v)) continue;
                    boolean ring = Math.max(Math.abs(a), Math.abs(bb)) == hu;
                    if (!ring) { f.set(u, y, v, SANDSTONE, 2); continue; }
                    boolean corner = Math.abs(a) == hu && Math.abs(bb) == hu;
                    if (corner || hu == 0) f.set(u, y, v, SANDSTONE, 2);
                    else if (Math.abs(a) == hu) f.set(u, y, v, SANDSTONE_STAIRS, f.stairs(a < 0 ? 1 : -1, 0, false));
                    else f.set(u, y, v, SANDSTONE_STAIRS, f.stairs(0, bb < 0 ? 1 : -1, false));
                }
        }
        q.set(cu, ct + 12, cv, SPONGE_, 0);
        q.set(cu, ct + 13, cv, FENCE, 0);
        q.set(cu, ct + 14, cv, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------------ tiles
    private static void chest(Pen q, int u, int y, int v, int dx, int dz, String table, String extras) {
        if (!q.in(u, v)) return;
        q.f.chest(u, y, v, dx, dz, table, extras);
    }

    private static void spawner(Pen q, int u, int y, int v, String type) {
        if (!q.in(u, v)) return;
        q.f.spawner(u, y, v, type);
    }

    private static void sign(Pen q, int u, int y, int v, int dx, int dz, String text) {
        if (!q.in(u, v)) return;
        q.f.sign(u, y, v, dx, dz, text);
    }

    /** An arrow trap: a pressure plate on the floor tile, a dispenser under it. */
    private static void trap(Pen q, int u, int floorY, int v) {
        if (!q.in(u, v)) return;
        q.f.set(u, floorY + 1, v, PLATE, 0);
        q.f.dispenser(u, floorY, v, 0, 0);
    }

    // ------------------------------------------------------------------------------------------------------ shared pieces
    /** A doorway through a face column: black jambs, an ochre lintel. */
    private static void portal(Pen q, int uc, int v, int half, int y0, int h) {
        air(q, uc - half, y0, v, uc + half, y0 + h - 1, v);
        q.box(uc - half - 1, y0, v, uc - half - 1, y0 + h - 1, v, b(CLAY, 15));
        q.box(uc + half + 1, y0, v, uc + half + 1, y0 + h - 1, v, b(CLAY, 15));
        q.box(uc - half - 1, y0 + h, v, uc + half + 1, y0 + h, v, b(CLAY, 4));
    }

    /** A little mask of the High Priest, three by three: two black eyes, a sponge nose. */
    private static void smallMask(Pen q, int uc, int y0, int v) {
        q.box(uc - 1, y0, v, uc + 1, y0 + 2, v, b(CLAY, 4));
        q.set(uc - 1, y0 + 1, v, CLAY, 15);
        q.set(uc + 1, y0 + 1, v, CLAY, 15);
        q.set(uc, y0 + 1, v, SPONGE_, 0);
    }

    /** A hipped roof of sandstone stairs over [a0..a1] x [b0..b1], rings shrinking a block every layer. */
    private static void hip(Pen q, int a0, int b0, int a1, int b1, int y) {
        Frame f = q.f;
        for (int k = 0; a0 + k <= a1 - k && b0 + k <= b1 - k; k++) {
            int x0 = a0 + k, x1 = a1 - k, z0 = b0 + k, z1 = b1 - k, yy = y + k;
            if (!q.hit(x0, z0, x1, z1)) continue;
            for (int u = Math.max(q.u0, x0); u <= Math.min(q.u1, x1); u++)
                for (int v = Math.max(q.v0, z0); v <= Math.min(q.v1, z1); v++) {
                    boolean w = u == x0, e = u == x1, n = v == z0, s = v == z1;
                    if (!(w || e || n || s)) { f.set(u, yy, v, SANDSTONE, 2); continue; }
                    boolean corner = (w || e) && (n || s) || x0 == x1 || z0 == z1;
                    if (corner) f.set(u, yy, v, SANDSTONE, 2);
                    else if (w) f.set(u, yy, v, SANDSTONE_STAIRS, f.stairs(1, 0, false));
                    else if (e) f.set(u, yy, v, SANDSTONE_STAIRS, f.stairs(-1, 0, false));
                    else if (n) f.set(u, yy, v, SANDSTONE_STAIRS, f.stairs(0, 1, false));
                    else f.set(u, yy, v, SANDSTONE_STAIRS, f.stairs(0, -1, false));
                }
        }
    }

    /** A pillar of pale stone with a chiseled base and an ochre capital. */
    private static void pillar(Pen q, int u, int y0, int y1, int v) {
        if (!q.in(u, v)) return;
        Frame f = q.f;
        f.set(u, y0, v, BRICK, 3);
        for (int y = y0 + 1; y < y1; y++) f.set(u, y, v, QUARTZ, 2);
        f.set(u, y1, v, CLAY, 4);
    }

    // ------------------------------------------------------------------------------------------------------ the refectory
    /** The Refectory of the Silent Meal, in the body of the first wall: two long tables, the kitchen with its hearth, a cold larder. */
    private static void refectory(Pen q) {
        if (!q.hit(-37, -32, -9, -20)) return;
        Frame f = q.f;
        q.box(-37, 0, -31, -9, 11, -20, MAS);
        q.box(-36, 0, -31, -10, 0, -21, PAV);
        air(q, -36, 1, -31, -10, 10, -21);
        for (int u = -34; u <= -12; u += 6) q.box(u, 10, -31, u, 10, -21, b(CLAY, 4));
        portal(q, -30, -32, 1, 1, 5);
        portal(q, -16, -32, 1, 1, 5);
        // two long tables under yellow cloths, each with a bench either side
        for (int[] t : new int[][] {{-29, -28}, {-24, -23}}) {
            q.box(-34, 1, t[0], -18, 1, t[1], b(BRICK, 3));
            q.box(-34, 2, t[0], -18, 2, t[1], b(CARPET, 4));
            for (int u = -34; u <= -18; u++) {
                stair(q, u, 1, t[0] - 1, 0, -1, BRICK_STAIRS, false);
                stair(q, u, 1, t[1] + 1, 0, 1, BRICK_STAIRS, false);
            }
        }
        for (int u = -33; u <= -19; u += 7) q.set(u, 3, -28, CAULDRON, 3);
        // the kitchen: a counter along the east wall, cauldrons, the hearth of magma behind a grate
        q.box(-11, 1, -30, -11, 1, -22, b(BRICK, 3));
        for (int v = -29; v <= -23; v += 3) q.set(-11, 2, v, CAULDRON, 3);
        q.box(-10, 1, -27, -10, 2, -25, b(87, 0));
        q.box(-10, 2, -26, -10, 2, -26, b(MAGMA, 0));
        q.box(-10, 1, -26, -10, 1, -26, b(MAGMA, 0));
        // lamps over the doors and the kitchen
        q.set(-30, 10, -30, GLOWSTONE, 0);
        q.set(-16, 10, -30, GLOWSTONE, 0);
        q.set(-13, 10, -24, GLOWSTONE, 0);
        chest(q, -12, 1, -22, -1, 0, Sites.CORRIDOR, null);
        chest(q, -35, 1, -22, 1, 0, Sites.SMITH, null);
        spawner(q, -12, 1, -30, "WITCH");
        sign(q, -26, 4, -21, 0, -1, "THE SILENT MEAL\nIS SERVED AT\nMIDNIGHT. NONE\nIS EVER LATE");
        sign(q, -23, 4, -21, 0, -1, "BEWARE THE\nLARDER. IT WAS\nNOT STOCKED\nBY HAND");
    }

    // ------------------------------------------------------------------------------------------------------ rows of cells
    /**
     * A row of monastic cells in the body of a wall: n cells three wide and five deep, a door each through the face at v = vf, the floor
     * of the lower court (y0); a bed and a mat in each, a chest or a cage in some.
     */
    private static void cellRow(Pen q, int vf, int y0, int uStart, int n, String[] tables, String[] cages, int cageMask, int chestMask) {
        int uEnd = uStart + 4 * (n - 1);
        if (!q.hit(uStart - 2, vf, uEnd + 2, vf + 6)) return;
        Frame f = q.f;
        q.box(uStart - 2, y0, vf + 1, uEnd + 2, y0 + 5, vf + 6, MAS);
        q.box(uStart - 2, y0 + 5, vf + 1, uEnd + 2, y0 + 5, vf + 6, b(CLAY, 4));
        for (int i = 0; i < n; i++) {
            int uc = uStart + 4 * i;
            air(q, uc - 1, y0 + 1, vf + 1, uc + 1, y0 + 4, vf + 5);
            q.box(uc - 1, y0, vf + 1, uc + 1, y0, vf + 5, b(STONE, 6));
            portal(q, uc, vf, 0, y0 + 1, 3);
            q.set(uc - 1, y0 + 1, vf + 5, SLAB, 5);
            q.set(uc - 1, y0 + 1, vf + 4, SLAB, 5);
            q.set(uc, y0 + 1, vf + 2, CARPET, 4);
            q.set(uc, y0 + 1, vf + 3, CARPET, 4);
            if ((chestMask >> i & 1) != 0) chest(q, uc + 1, y0 + 1, vf + 5, 0, -1, tables[i % tables.length], i % 3 == 0 ? "trinket:0.15" : null);
            if ((cageMask >> i & 1) != 0) spawner(q, uc + 1, y0 + 1, vf + 5, cages[(i / 2) % cages.length]);
            if (i % 4 == 2) q.set(uc, y0 + 4, vf + 3, GLOWSTONE, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------------ the cloister
    /** The covered walk before the cells of the second wall. */
    private static void cloister(Pen q) {
        if (!q.hit(-11, -18, 43, -15)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -10); u <= Math.min(q.u1, 42); u++)
            for (int v = Math.max(q.v0, -18); v <= Math.min(q.v1, -15); v++) {
                f.set(u, 17, v, SLAB, 1);
                if (v == -18 && Math.floorMod(u + 10, 4) == 0) { for (int y = 13; y <= 16; y++) f.set(u, y, v, STONE, 6); f.set(u, 17, v, SANDSTONE, 2); }
                if (v == -18 && Math.floorMod(u + 10, 4) == 2) f.set(u, 17, v, CLAY, 4);
            }
    }

    // ------------------------------------------------------------------------------------------------------ the great prayer hall
    /** The idol: a seated figure in yellow robes on a dais, its head a sponge mask with black eyes. */
    private static void idol(Pen q, int cu, int y0, int cv) {
        if (!q.hit(cu - 3, cv - 3, cu + 3, cv + 3)) return;
        q.box(cu - 3, y0, cv - 2, cu + 3, y0 + 1, cv + 1, MAS);
        q.box(cu - 2, y0 + 2, cv - 1, cu + 2, y0 + 3, cv + 1, b(CLAY, 4));
        q.box(cu - 1, y0 + 4, cv, cu + 1, y0 + 6, cv + 1, b(CLAY, 4));
        q.box(cu - 2, y0 + 4, cv - 1, cu - 2, y0 + 5, cv, b(CLAY, 4));
        q.box(cu + 2, y0 + 4, cv - 1, cu + 2, y0 + 5, cv, b(CLAY, 4));
        q.box(cu - 1, y0 + 7, cv, cu + 1, y0 + 8, cv + 1, b(SPONGE_, 0));
        q.set(cu - 1, y0 + 8, cv - 0, CLAY, 15);
        q.set(cu + 1, y0 + 8, cv - 0, CLAY, 15);
        q.set(cu, y0 + 9, cv, CLAY, 4);
        q.set(cu, y0 + 3, cv - 2, GLOWSTONE, 0);
    }

    /** The Great Prayer Hall, cut into the body of the third wall: a forest of pale pillars, the idol of the Faceless Lord at its end. */
    private static void dukhang(Pen q) {
        if (!q.hit(-31, 4, 7, 20)) return;
        Frame f = q.f;
        q.box(-31, 24, 5, 7, 35, 20, MAS);
        q.box(-30, 24, 5, 6, 24, 19, PAV);
        air(q, -30, 25, 5, 6, 34, 19);
        for (int u = -28; u <= 4; u += 4) q.box(u, 34, 5, u, 34, 19, b(CLAY, 4));
        // the aisle runner and the benches of the nave
        q.box(-13, 25, 5, -11, 25, 14, b(CARPET, 4));
        for (int v = 8; v <= 14; v += 3) {
            for (int u = -16; u <= -14; u++) { stair(q, u, 25, v, 0, -1, BRICK_STAIRS, false); stair(q, u + 6, 25, v, 0, -1, BRICK_STAIRS, false); }
        }
        // two ranks of pillars, three rows deep
        for (int u : new int[] {-24, -17, -7, 0})
            for (int v = 8; v <= 16; v += 4) {
                pillar(q, u, 25, 34, v);
                if (v == 12 && (u == -17 || u == -7)) q.set(u + (u < -12 ? 1 : -1), 31, v, GLOWSTONE, 0);
            }
        // the portals: the great one on the axis, a door either side, and the frieze of masks above them
        portal(q, -12, 4, 2, 25, 6);
        portal(q, -26, 4, 1, 25, 4);
        portal(q, 2, 4, 1, 25, 4);
        for (int u : new int[] {-26, -19, -12, -5, 2}) smallMask(q, u, 31, 4);
        idol(q, -12, 25, 16);
        // wheels along the side walls
        for (int v = 7; v <= 17; v += 2) { wheel(q, -30, 25, v); wheel(q, 6, 25, v); }
        chest(q, -16, 25, 19, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + (int) (Math.floorMod(q.s.hash, 99)) + ";trinket:0.4");
        chest(q, -8, 25, 19, 0, -1, "minecraft:chests/woodland_mansion", "trinket:0.2");
        sign(q, 6, 28, 12, -1, 0, "HE HAS NO FACE\nFOR PRAYERS TO\nLAND ON");
        sign(q, -4, 28, 5, 0, 1, "SPEAK NO\nPRAYER ALOUD");
    }

    // ------------------------------------------------------------------------------------------------------ the library
    /** The Library of Forbidden Sutras: a tall stone house of three floors, shelves to the roof, one ladder through the middle of them. */
    private static void library(Pen q) {
        if (!q.hit(-37, 5, -13, 20)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -36); u <= Math.min(q.u1, -14); u++)
            for (int v = Math.max(q.v0, 6); v <= Math.min(q.v1, 19); v++) {
                boolean wall = u == -36 || u == -14 || v == 6 || v == 19;
                for (int y = 37; y <= 54; y++) {
                    if (wall) {
                        if (y <= 38) plinth(f, u, y, v);
                        else if (y >= 53) ochre(f, u, y, v);
                        else if ((y == 42 || y == 48)) f.set(u, y, v, BRICK, 3);
                        else grey(f, u, y, v);
                    } else if (y == 42 || y == 48) f.set(u, y, v, STONE, 6);
                    else f.set(u, y, v, AIR, 0);
                }
                if (!wall) f.set(u, 54, v, STONE, 6);
            }
        // the ladder through the floors
        for (int y = 37; y <= 53; y++) q.set(-35, y, 8, LADDER, f.facing(1, 0));
        q.set(-35, 42, 8, LADDER, f.facing(1, 0));
        q.set(-35, 48, 8, LADDER, f.facing(1, 0));
        // the shelves: a bank under the back wall and four stacks, on every floor
        for (int fy : new int[] {37, 43, 49}) {
            for (int u = -34; u <= -16; u++) for (int y = fy; y <= fy + 2; y++) if (!(u >= -26 && u <= -24)) q.set(u, y, 18, BOOKSHELF, 0);
            for (int u : new int[] {-32, -28, -22, -18}) for (int v = 10; v <= 15; v++) for (int y = fy; y <= fy + 2; y++) q.set(u, y, v, BOOKSHELF, 0);
        }
        // the door, windows of yellow glass, lamps
        air(q, -25, 37, 6, -24, 40, 6);
        q.box(-26, 41, 6, -23, 41, 6, b(CLAY, 4));
        q.box(-27, 37, 6, -27, 40, 6, b(CLAY, 15));
        q.box(-22, 37, 6, -22, 40, 6, b(CLAY, 15));
        for (int y : new int[] {44, 50}) for (int u = -31; u <= -19; u += 6) { q.set(u, y, 6, PANE_STAINED, 4); q.set(u, y + 1, 6, PANE_STAINED, 4); }
        for (int y : new int[] {44, 50}) for (int v = 10; v <= 16; v += 6) { q.set(-14, y, v, PANE_STAINED, 4); q.set(-14, y + 1, v, PANE_STAINED, 4); q.set(-36, y, v, PANE_STAINED, 4); q.set(-36, y + 1, v, PANE_STAINED, 4); }
        q.set(-25, 40, 8, GLOWSTONE, 0);
        q.set(-25, 46, 12, GLOWSTONE, 0);
        // the roof
        hip(q, -37, 5, -13, 20, 55);
        q.set(-25, 62, 12, SPONGE_, 0);
        q.set(-25, 63, 12, FENCE, 0);
        q.set(-25, 64, 12, GLOWSTONE, 0);
        chest(q, -34, 37, 17, 1, 0, Sites.LIBRARY, "lore:" + (int) (Math.floorMod(q.s.hash >>> 8, 99)));
        chest(q, -34, 43, 17, 1, 0, Sites.LIBRARY, "lore:" + (int) (Math.floorMod(q.s.hash >>> 16, 99)) + ";trinket:0.25");
        chest(q, -16, 49, 17, -1, 0, Sites.LIBRARY, "lore:" + (int) (Math.floorMod(q.s.hash >>> 24, 99)) + ";trinket:0.6");
        spawner(q, -34, 49, 8, "SILVERFISH");
        sign(q, -23, 38, 7, 0, 1, "THE SUTRAS ARE\nCHAINED. THE\nCHAINS ARE FOR\nTHE READER");
    }

    // ------------------------------------------------------------------------------------------------------ the great wheel
    /** The pavilion of the Great Prayer Wheel: a platform, a ring of pillars, a hipped roof, the drum of yellow clay turning in the middle. */
    private static void wheelHouse(Pen q) {
        if (!q.hit(-11, -31, 11, -18)) return;
        Frame f = q.f;
        q.box(-9, 13, -28, 9, 13, -20, MAS);
        q.box(-8, 13, -27, 8, 13, -21, PAV);
        for (int u = -3; u <= 3; u++) {
            stair(q, u, 13, -29, 0, 1, BRICK_STAIRS, false);
            stair(q, u, 13, -19, 0, -1, BRICK_STAIRS, false);
        }
        for (int u : new int[] {-9, -3, 3, 9}) for (int v : new int[] {-28, -20}) pillar(q, u, 14, 21, v);
        for (int a = -3; a <= 3; a++)
            for (int bb = -3; bb <= 3; bb++) {
                int d = a * a + bb * bb;
                if (d > 10) continue;
                for (int y = 14; y <= 20; y++) {
                    if (!q.in(a, -24 + bb)) continue;
                    if (y == 14 || y == 20) f.set(a, y, -24 + bb, CLAY, 15);
                    else if (y == 17 && ((a + bb) & 1) == 0) f.set(a, y, -24 + bb, CLAY, 15);
                    else f.set(a, y, -24 + bb, CLAY, 4);
                }
            }
        for (int y = 21; y <= 21; y++) q.set(0, y, -24, SPONGE_, 0);
        hip(q, -10, -30, 10, -18, 22);
        q.set(0, 28, -24, GLOWSTONE, 0);
        chest(q, 8, 14, -21, -1, 0, Sites.DESERT, "trinket:0.3");

        sign(q, -8, 16, -28, 1, 0, "THE WHEEL TURNS\nWITHOUT HANDS.\nIT HAS TURNED\nSINCE BEFORE");
    }

    // ------------------------------------------------------------------------------------------------------ the court of masks
    /** The forecourt of the hall: braziers of magma either side of the Stair, a row of wheels along the foot of the facade, flag poles. */
    private static void forecourt(Pen q) {
        if (!q.hit(-30, 22, 30, 30)) return;
        Frame f = q.f;
        for (int s = -1; s <= 1; s += 2) {
            int u = 10 * s;
            if (q.in(u, 26)) {
                q.box(u - 1, 49, 25, u + 1, 50, 27, b(BRICK, 3));
                q.box(u - 1, 51, 25, u + 1, 51, 27, b(STONE, 6));
                q.set(u, 51, 26, MAGMA, 0);
                for (int a = -1; a <= 1; a += 2) { q.set(u + a, 52, 26, WALL, 0); q.set(u, 52, 26 + a, WALL, 0); }
            }
            for (int w = 3; w <= 13; w += 2) wheel(q, s * w, 49, 29);
            pole(q, s * 21, 49, 25, 12, s);
        }
        sign(q, -4, 51, 29, 0, -1, "ENTER BY THE\nMOUTH OF THE\nMASK");
        sign(q, 4, 51, 29, 0, -1, "HE WEARS IT SO\nTHAT YOU MAY\nSTILL LIVE");
    }

    /** A prayer-flag pole: a mast of fence and wall, and a string of grey and yellow flags sagging toward the ground. */
    private static void pole(Pen q, int u, int y0, int v, int h, int dir) {
        if (!q.hit(u - 8, v, u + 8, v)) return;
        Frame f = q.f;
        for (int y = 0; y < h; y++) q.set(u, y0 + y, v, y < 3 ? WALL : FENCE, 0);
        q.set(u, y0 + h, v, SPONGE_, 0);
        int[] cols = {4, 8, 0, 7, 4, 8, 0, 7};
        for (int i = 1; i <= 8; i++) q.set(u + dir * i, y0 + h - 1 - i / 2, v, WOOL, cols[i - 1]);
    }

    // ------------------------------------------------------------------------------------------------------ the sky-burial
    /** The deck of the sky-burial on the hall's roof: a disc of bone and quartz round the slab, flag poles at the corners. */
    private static void skyDeck(Pen q) {
        if (!q.hit(-15, 32, 15, 50)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -8); u <= Math.min(q.u1, 8); u++)
            for (int v = Math.max(q.v0, 34); v <= Math.min(q.v1, 49); v++) {
                double d = Math.sqrt(u * u + (v - 42) * (v - 42));
                if (d > 7.5) continue;
                int ring = (int) d;
                if (ring >= 7) f.set(u, HROOF, v, STONE, 6);
                else if ((ring & 1) == 0) f.set(u, HROOF, v, BONE, 0);
                else f.set(u, HROOF, v, QUARTZ, 0);
            }
        q.box(-2, HROOF + 1, 40, 2, HROOF + 1, 44, b(STONE, 6));
        q.box(-1, HROOF + 2, 41, 1, HROOF + 2, 43, b(BRICK, 2));
        for (int a = -2; a <= 2; a += 4) for (int bb = 40; bb <= 44; bb += 4) { q.set(a, HROOF + 2, bb, WALL, 0); q.set(a, HROOF + 3, bb, GLOWSTONE, 0); }
        pole(q, -13, HROOF + 1, 35, 9, 1);
        pole(q, 13, HROOF + 1, 35, 9, -1);
        pole(q, -13, HROOF + 1, 48, 9, 1);
        pole(q, 13, HROOF + 1, 48, 9, -1);
        for (int i = 0; i < 6; i++) q.set(-9 + i * 3 + (i & 1), HROOF + 1, 46 + (i % 3 == 0 ? 1 : 0), BONE, 0);
        chest(q, 0, HROOF + 1, 48, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + (int) (Math.floorMod(q.s.hash >>> 4, 99)) + ";trinket:0.5");
        sign(q, -10, HROOF + 2, 32, 0, 1, "THE SKY GIVES\nNOTHING BACK");
    }

    // ------------------------------------------------------------------------------------------------------ the courts
    /** A brazier of magma on a stepped pedestal, caged by wall posts. */
    private static void brazier(Pen q, int u, int y, int v) {
        if (!q.hit(u - 1, v - 1, u + 1, v + 1)) return;
        q.box(u - 1, y, v - 1, u + 1, y + 1, v + 1, b(BRICK, 3));
        q.box(u - 1, y + 2, v - 1, u + 1, y + 2, v + 1, b(STONE, 6));
        q.set(u, y + 2, v, MAGMA, 0);
        for (int a = -1; a <= 1; a += 2) { q.set(u + a, y + 3, v, WALL, 0); q.set(u, y + 3, v + a, WALL, 0); }
    }

    /** A kneeling monk in yellow robes with a sponge mask for a face: a cushion, two blocks of robe, the mask. */
    private static void monk(Pen q, int u, int y, int v) {
        if (!q.in(u, v)) return;
        q.f.set(u, y, v, STONE, 6);
        q.f.set(u, y + 1, v, CLAY, 4);
        q.f.set(u, y + 2, v, CLAY, 4);
        q.f.set(u, y + 3, v, SPONGE_, 0);
    }

    /** The courts of the middle tiers: chortens in their rows, braziers, flag poles. */
    private static void courts(Pen q) {
        chorten(q, -20, -25, 12, Sites.JUNGLE, null);
        chorten(q, -30, -25, 12, null, null);
        chorten(q, 24, -24, 12, null, null);
        chorten(q, 34, -24, 12, IGLOO, "trinket:0.2");
        chorten(q, -35, -7, 24, Sites.CORRIDOR, null);
        chorten(q, -26, -7, 24, null, null);
        chorten(q, 18, -7, 24, null, null);
        chorten(q, 26, -7, 24, Sites.DESERT, "trinket:0.2");
        chorten(q, 22, 11, 36, null, null);
        chorten(q, 31, 11, 36, MINESHAFT, null);
        chest(q, 0, 26, -4, 0, -1, Sites.DESERT, "trinket:0.3");
        for (int a = 4; a <= 12; a += 4) { monk(q, a, 25, -12); monk(q, -a, 25, -12); }       // the silent congregation of the assembly court
        for (int s = -1; s <= 1; s += 2) { monk(q, 4 * s, 49, 24); monk(q, 4 * s, 49, 27); monk(q, 6 * s, 14, -24); }
        sign(q, -6, 14, -15, 0, -1, "THE CELLS OF\nTHE WAKEFUL.\nDO NOT ANSWER\nTHE KNOCKING");
        sign(q, 12, 39, 21, 0, -1, "THE SILENT ONES\nSIT HERE. LEAVE\nTHEM TO THEIR\nSILENCE");
        brazier(q, -8, 25, -3);
        brazier(q, 8, 25, -3);
        brazier(q, -12, 13, -29);
        brazier(q, 12, 13, -29);
        brazier(q, -9, 37, 7);
        brazier(q, 9, 37, 7);
        pole(q, -6, 25, -12, 11, -1);
        pole(q, 6, 25, -12, 11, 1);
        pole(q, -12, 37, 11, 12, -1);
        pole(q, 12, 37, 11, 12, 1);
        pole(q, -44, 13, -17, 10, 1);
        pole(q, 44, 13, -20, 10, -1);
        pole(q, -20, 49, 26, 11, 1);
        pole(q, 20, 49, 26, 11, -1);
        pavilion(q, -26, 29, 48, 3, 3);
        pavilion(q, 26, 29, 48, 3, 3);
        chest(q, -26, 50, 31, 0, -1, "minecraft:chests/igloo_chest", "trinket:0.25");
        chest(q, 26, 50, 31, 0, -1, Sites.JUNGLE, null);
        sign(q, 30, 3, -49, 0, 1, "THE WINDOWS ARE\nPAINTED. NOTHING\nLOOKS OUT. THE\nWALLS LOOK IN");
    }

    /** Cairns and flag poles of the pilgrims on the open land inside the footprint. */
    private static void cairns(Pen q) {
        Frame f = q.f;
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                if (Math.floorMod(u, 7) != 3 || Math.floorMod(v, 7) != 3) continue;
                if (level(u, v) >= 0 || level(u - 3, v) >= 0 || level(u + 3, v) >= 0 || level(u, v - 3) >= 0 || level(u, v + 3) >= 0) continue;
                double r = f.roll(u, 0, v, 231);
                if (r > 0.45) continue;
                int G = q.land(u, v);
                f.set(u, G + 1, v, COBBLE, 0);
                f.set(u, G + 2, v, r < 0.2 ? MOSSY : COBBLE, 0);
                f.set(u, G + 3, v, SLAB, 3);
                if (r < 0.22) {
                    for (int y = G + 3; y < G + 9; y++) f.set(u, y, v, FENCE, 0);
                    for (int i = 1; i <= 5; i++) q.set(u + i, G + 8 - i / 2, v, WOOL, i % 2 == 0 ? 4 : 8);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------------ pavilions
    /** An open pavilion: a platform with steps on its -v side, pillars, a hipped roof of sandstone, a lamp under the ridge. */
    private static void pavilion(Pen q, int cu, int cv, int y0, int hu, int hv) {
        if (!q.hit(cu - hu - 1, cv - hv - 2, cu + hu + 1, cv + hv + 1)) return;
        Frame f = q.f;
        q.box(cu - hu, y0 + 1, cv - hv, cu + hu, y0 + 1, cv + hv, MAS);
        q.box(cu - hu + 1, y0 + 1, cv - hv + 1, cu + hu - 1, y0 + 1, cv + hv - 1, PAV);
        for (int u = cu - 2; u <= cu + 2; u++) stair(q, u, y0 + 1, cv - hv - 1, 0, 1, BRICK_STAIRS, false);
        for (int a = -hu; a <= hu; a += hu) for (int bb = -hv; bb <= hv; bb += hv) if (a != 0 || bb != 0) pillar(q, cu + a, y0 + 2, y0 + 8, cv + bb);
        hip(q, cu - hu - 1, cv - hv - 1, cu + hu + 1, cv + hv + 1, y0 + 9);
        q.set(cu, y0 + 8, cv, GLOWSTONE, 0);
    }

    /** The gate of the mask over the head of the Last Stair: two pale piers, a lintel of ochre, a little mask looking down the steps. */
    private static void maskGate(Pen q) {
        if (!q.hit(-7, 21, 7, 21)) return;
        for (int s = -1; s <= 1; s += 2) pillar(q, 6 * s, 50, 55, 21);
        q.box(-5, 56, 21, 5, 56, 21, OCH);
        q.box(-6, 56, 21, 6, 57, 21, b(CLAY, 4));
        smallMask(q, 0, 58, 21);
        q.set(-6, 58, 21, GLOWSTONE, 0);
        q.set(6, 58, 21, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------------ ornaments
    /** A corner turret: a squat battered tower with a band of ochre, slit windows and a sandstone hip roof with a sponge finial. */
    private static void turret(Pen q, int cu, int cv, int y0, int h) {
        if (!q.hit(cu - 3, cv - 3, cu + 3, cv + 3)) return;
        Frame f = q.f;
        for (int a = -2; a <= 2; a++)
            for (int bb = -2; bb <= 2; bb++) {
                int u = cu + a, v = cv + bb;
                if (!q.in(u, v)) continue;
                for (int y = y0 + 1; y <= y0 + h; y++) {
                    boolean slit = y >= y0 + 4 && y <= y0 + 5 && (a == 0 && Math.abs(bb) == 2 || bb == 0 && Math.abs(a) == 2);
                    if (slit) f.set(u, y, v, CLAY, 15);
                    else if (y <= y0 + 2) plinth(f, u, y, v);
                    else if (y >= y0 + h - 1) ochre(f, u, y, v);
                    else grey(f, u, y, v);
                }
            }
        hip(q, cu - 3, cv - 3, cu + 3, cv + 3, y0 + h + 1);
        q.set(cu, y0 + h + 5, cv, SPONGE_, 0);
    }

    /** The wall of a hundred faces: rows of the High Priest's little masks along the face of the summit's wall. */
    private static void maskWall(Pen q) {
        if (!q.hit(-37, 22, -7, 22)) return;
        for (int k = 0; k < 7; k++) {
            smallMask(q, -34 + 4 * k, 38, 22);
            smallMask(q, -34 + 4 * k, 42, 22);
        }
    }

    private static final int[] FLAG_COLS = {4, 8, 0, 7};

    /** A string of prayer flags from (u0, y0, v0) to (u1, y1, v1), sagging by {@code sag} in the middle. */
    private static void flagLine(Pen q, int u0, int y0, int v0, int u1, int y1, int v1, int sag) {
        if (!q.hit(Math.min(u0, u1), Math.min(v0, v1), Math.max(u0, u1), Math.max(v0, v1))) return;
        int n = Math.max(Math.abs(u1 - u0), Math.max(Math.abs(y1 - y0), Math.abs(v1 - v0)));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            int u = (int) Math.round(u0 + (u1 - u0) * t), v = (int) Math.round(v0 + (v1 - v0) * t);
            int y = (int) Math.round(y0 + (y1 - y0) * t - sag * 4 * t * (1 - t));
            q.set(u, y, v, WOOL, FLAG_COLS[i % 4]);
        }
    }

    private static void flags(Pen q) {
        flagLine(q, -23, 83, 36, -10, 82, 30, 1);
        flagLine(q, 23, 83, 36, 10, 82, 30, 1);
        flagLine(q, -10, 80, 29, -20, 60, 26, 2);
        flagLine(q, 10, 80, 29, 20, 60, 26, 2);
        flagLine(q, -13, 73, 35, 13, 73, 35, 3);
        flagLine(q, -13, 73, 48, 13, 73, 48, 3);
    }

    /** Piers against the long side walls of the tiers, stepped, with banners of yellow silk hung between them. */
    private static void piers(Pen q) {
        Frame f = q.f;
        for (int k = 1; k < FL.length; k++) {
            int vA = VF[k], vB = k == FL.length - 1 ? R : VF[k + 1] - 1, hw = HW[k], H = FL[k];
            for (int s = -1; s <= 1; s += 2) {
                int uA = Math.min(s * (hw + 1), s * (hw + 2)), uB = Math.max(s * (hw + 1), s * (hw + 2));
                if (!q.hit(uA, vA, uB, vB)) continue;
                for (int vc = vA + 6; vc <= vB - 4; vc += 9)
                    for (int u = Math.max(q.u0, uA); u <= Math.min(q.u1, uB); u++)
                        for (int v = Math.max(q.v0, vc - 1); v <= Math.min(q.v1, vc + 1); v++) {
                            if (level(u, v) >= 0) continue;
                            int G = q.land(u, v), top = Math.abs(u) == hw + 1 ? H - 2 : H - 5;
                            for (int y = G + 1; y <= top; y++) {
                                if (y == top) f.set(u, y, v, SLAB, 1);
                                else if (y >= top - 1) ochre(f, u, y, v);
                                else grey(f, u, y, v);
                            }
                        }
                for (int vc = vA + 6; vc + 9 <= vB - 4; vc += 9)                        // the banners between the piers
                    for (int v = vc + 4; v <= vc + 5; v++) {
                        int u = s * (hw + 1);
                        if (!q.in(u, v) || level(u, v) >= 0) continue;
                        int G = q.land(u, v);
                        for (int y = H - 2; y >= Math.max(G + 2, H - 10); y--) f.set(u, y, v, WOOL, y == Math.max(G + 2, H - 10) ? 7 : 4);
                    }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ more tiles
    private static void gateSigns(Pen q) {
        if (!q.hit(-8, -50, 8, -50)) return;
        for (int u = -8; u <= 8; u++) if (Math.abs(u) > 2) q.f.clear(u, -50, 3, 5);
        sign(q, -5, 4, -50, 0, -1, "THE MONASTERY\nOF LENG\nTURN THE WHEELS\nAS YOU PASS");
        sign(q, 5, 4, -50, 0, -1, "THE HIGH PRIEST\nNOT TO BE\nDESCRIBED WEARS\nA YELLOW MASK");
        sign(q, -41, 3, -39, 1, 0, "THE DOOR OF\nBONES. THOSE\nTHE SKY REFUSED\nARE KEPT BELOW");
    }

    private static void ossuaryTiles(Pen q) {
        if (!q.hit(-36, -49, -3, -32)) return;
        chest(q, -31, -10, -46, 0, 1, Sites.DUNGEON, null);
        chest(q, -21, -10, -46, 0, 1, MINESHAFT, "trinket:0.2");
        chest(q, -26, -10, -34, 0, -1, NETHER, null);
        chest(q, -16, -10, -34, 0, -1, CROSSING, "lore:" + Math.floorMod(q.s.hash >>> 12, 99));
        chest(q, -11, -10, -45, 1, 0, TREASURE, "trinket:0.4");
        chest(q, -11, -10, -37, 1, 0, MANSION, null);
        spawner(q, -9, -10, -45, "ZOMBIE");
        spawner(q, -9, -10, -37, "SKELETON");
        sign(q, -24, -8, -35, 0, -1, "THE SKY REFUSED\nTHEM. THE EARTH\nDID NOT.");
        sign(q, -8, -8, -35, 0, -1, "THEY WAIT FOR\nTHE HIGH PRIEST\nTO CALL THEM");
        trap(q, -20, -11, -40);
        trap(q, -29, -11, -40);
    }

    private static void hallTiles(Pen q) {
        if (!q.hit(-15, 30, 15, 50)) return;
        chest(q, -4, 51, 47, 0, -1, TREASURE, "lore:" + Math.floorMod(q.s.hash >>> 20, 99) + ";trinket:0.6");
        chest(q, 4, 51, 47, 0, -1, MANSION, "trinket:0.35");
        chest(q, -12, 49, 47, 1, 0, CROSSING, null);
        spawner(q, 12, 49, 47, "STRAY");
        sign(q, 0, 58, 47, 0, -1, "WHO WEARS THE\nMASK WEARS\nTHE FACE OF\nWHOEVER LOOKS");
        trap(q, 0, 48, 35);
        trap(q, 0, 48, 39);
    }

    // ------------------------------------------------------------------------------------------------------ the pen
    private static final class Pen {
        final Frame f;
        final Plans.GreatSite s;
        final int u0, v0, u1, v1, dy;
        private final int x0, z0;
        private final int[] land = new int[256];

        Pen(Plans.GreatSite s, Canvas c, int rot, int r, int dy) {
            this.s = s;
            this.dy = dy;
            f = new Frame(c, s.x, s.z, s.base + dy, rot);
            x0 = c.x0;
            z0 = c.z0;
            int[] a = local(rot, c.x0 - s.x, c.z0 - s.z), b = local(rot, c.x0 + 15 - s.x, c.z0 + 15 - s.z);
            u0 = Math.max(-r, Math.min(a[0], b[0]));
            u1 = Math.min(r, Math.max(a[0], b[0]));
            v0 = Math.max(-r, Math.min(a[1], b[1]));
            v1 = Math.min(r, Math.max(a[1], b[1]));
            Arrays.fill(land, Integer.MIN_VALUE);
        }

        static int[] local(int rot, int dx, int dz) {
            switch (rot & 3) {
                case 1: return new int[] {dz, -dx};
                case 2: return new int[] {-dx, -dz};
                case 3: return new int[] {-dz, dx};
                default: return new int[] {dx, dz};
            }
        }

        boolean empty() { return u0 > u1 || v0 > v1; }

        boolean in(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }

        /** The land's height at a column, in local y (from the plan's terrain, never the canvas). */
        int land(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v);
            if (wx < x0 || wx > x0 + 15 || wz < z0 || wz > z0 + 15) return s.surface(wx, wz) - s.base - dy;
            int i = (wx - x0) << 4 | (wz - z0);
            if (land[i] == Integer.MIN_VALUE) land[i] = s.surface(wx, wz) - s.base - dy;
            return land[i];
        }

        void set(int u, int y, int v, int id, int meta) { if (in(u, v)) f.set(u, y, v, id, meta); }

        void put(int u, int y, int v, int m) {
            switch (m) {
                case MAS: grey(f, u, y, v); break;
                case OCH: ochre(f, u, y, v); break;
                case PAV: paving(f, u, y, v); break;
                case PLN: plinth(f, u, y, v); break;
                case OSS: ossuaryStone(f, u, y, v); break;
                case OFL: ossuaryFloor(f, u, y, v); break;
                default: f.set(u, y, v, m >> 4, m & 15);
            }
        }

        void col(int u, int v, int y0, int y1, int m) { for (int y = y0; y <= y1; y++) put(u, y, v, m); }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int A0 = Math.max(Math.min(a0, a1), u0), A1 = Math.min(Math.max(a0, a1), u1);
            int B0 = Math.max(Math.min(b0, b1), v0), B1 = Math.min(Math.max(b0, b1), v1);
            for (int a = A0; a <= A1; a++) for (int bb = B0; bb <= B1; bb++) for (int y = y0; y <= y1; y++) put(a, y, bb, m);
        }
    }
}
