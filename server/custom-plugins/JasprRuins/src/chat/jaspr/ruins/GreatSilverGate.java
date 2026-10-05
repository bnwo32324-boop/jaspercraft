package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Gate of the Silver Key (epoch 6): the Ultimate Gate of the dreamers. A colossal ring forty blocks tall, banded
 * quartz and obsidian and studded with lanterns, stands upright over a sunken round plaza, the Silver Key itself hanging
 * in it on a chain; the Guardian waits on the plaza before it, on the key inlaid in the floor.
 * <ul>
 * <li>the Processional Way: an avenue of sphinxes from the edge of the land to the Pylon, the gatehouse at the plaza's
 * rim (two battered towers with guard rooms and a lookout over its lintel);</li>
 * <li>the sunken plaza: four tiers of seats down to its floor, a stair cut through them on each side, eight keyhole
 * monoliths on the rim, the ring on its two plinths, flanked by two great sphinxes;</li>
 * <li>two side gatehouses with passages into the plaza, guard rooms and watch-fires on their roofs;</li>
 * <li>behind the gate the Dream Stairs: a long stair climbing to a landing in the air and breaking up into the night,
 * and two spiral stairs round pale columns that end in nothing; a circle of broken columns round the whole sanctuary;</li>
 * <li>under the plaza the Crypt of the Dreamers: a round hall with the great bier and eight alcoves of sarcophagi.</li>
 * </ul>
 */
final class GreatSilverGate extends GreatDesign {
    static final int R = 46;
    /** The plaza's centre (local v), its floor, and the ring-gate's plane, centre height and radius. */
    static final int CV = 2, P = -4, GV = 10, GY = 17;
    static final double GR = 21.5;
    static final int[] MONO_DEG = {-150, -120, -60, -30, 30, 60, 120, 150};
    private static final String TREASURE = "minecraft:chests/end_city_treasure", CROSSING = "minecraft:chests/stronghold_crossing",
        MINESHAFT = "minecraft:chests/abandoned_mineshaft", MANSION = "minecraft:chests/woodland_mansion";
    private static final int QUARTZ_STAIRS = 156;

    static final class Plan extends Layout {
        int rot;
        long h;
        /** The land's height (local) at the foot of the long dream stair and at the two spiral stairs. */
        int longFoot, spiralW, spiralE;
        /** The heights of the broken columns round the sanctuary (0: none). */
        final int[] columns = new int[24];
    }

    // ------------------------------------------------------------------------------------------------------ the plan
    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.h = s.hash;
        p.rot = GreatTerracesUphill.uphill(s);
        Frame fr = new Frame(null, s.x, s.z, s.base, p.rot);
        p.longFoot = Math.max(-6, Math.min(8, s.surface(fr.wx(-15, 38), fr.wz(-15, 38)) - s.base));
        p.spiralW = Math.max(-6, Math.min(8, s.surface(fr.wx(-26, 30), fr.wz(-26, 30)) - s.base));
        p.spiralE = Math.max(-6, Math.min(8, s.surface(fr.wx(26, 30), fr.wz(26, 30)) - s.base));
        for (int i = 0; i < 24; i++) {
            int deg = i * 15 - 90;
            int off = Math.floorMod(deg + 90 + 180, 360) - 180;
            p.columns[i] = Math.abs(off) <= 15 ? 0 : r.nextInt(10) < 3 ? 2 + r.nextInt(4) : 7 + r.nextInt(6);
        }
        int t = (p.rot - s.rot) & 3;
        p.boss = turn(t, 0, P + 1, CV);
        add(p, t, 0, 1, -43, "ghoul+husk");
        add(p, t, 0, 1, -33, "zombie+zombie_villager");
        add(p, t, -8, 1, -27, "skeleton+stray");
        add(p, t, 8, 1, -27, "!cult_zealot+vindicator");
        add(p, t, -18, 1, -17, "creeper+spider");
        add(p, t, 18, 1, -17, "witch+evoker");
        add(p, t, -7, P + 1, -6, "!star_spawn+wither_skeleton");
        add(p, t, 7, P + 1, -6, "cult_adept+illusioner");
        add(p, t, -32, 1, 2, "hound+spider");
        add(p, t, 32, 1, 2, "mi_go+enderman");
        add(p, t, -29, 25, -1, "nightgaunt");
        add(p, t, 0, 1, 28, "deep_one+slime");
        add(p, t, -29, p.spiralW + 1, 30, "shoggoth+cave_spider");
        add(p, t, 29, p.spiralE + 1, 30, "hound+tomb_crawler");
        add(p, t, 15, p.longFoot + 29, 38, "nightgaunt+stray");
        add(p, t, 0, -11, -2, "ghoul+tomb_crawler");
        add(p, t, -7, -11, 2, "silverfish+endermite");
        add(p, t, 7, -11, 2, "zombie+cave_spider");
        return p;
    }

    private static void add(Plan p, int t, int u, int y, int v, String pack) {
        int[] w = turn(t, u, y, v);
        p.garrisons.add(g(w[0], w[1], w[2], pack));
    }

    private static int[] turn(int t, int u, int y, int v) {
        switch (t & 3) {
            case 1: return new int[] {-v, y, u};
            case 2: return new int[] {-u, y, -v};
            case 3: return new int[] {v, y, -u};
            default: return new int[] {u, y, v};
        }
    }

    /** The frame whose back faces the land's high side (as the Terraces do), so the avenue comes up from below. */
    private static final class GreatTerracesUphill {
        static int uphill(Plans.GreatSite s) {
            int[] sum = new int[4];
            for (int d = 18; d <= 42; d += 12)
                for (int w = -20; w <= 20; w += 20) {
                    sum[0] += s.surface(s.x + w, s.z + d);
                    sum[1] += s.surface(s.x - d, s.z + w);
                    sum[2] += s.surface(s.x + w, s.z - d);
                    sum[3] += s.surface(s.x + d, s.z + w);
                }
            int best = s.rot & 3;
            for (int k = 0; k < 4; k++) if (sum[k] > sum[best] + 18) best = k;
            return best;
        }
    }

    // ------------------------------------------------------------------------------------------------------ drawing
    @Override void draw(Plans.GreatSite s, Layout l, Canvas c) {
        Plan p = (Plan) l;
        Pen q = new Pen(s, c, p.rot, R);
        if (q.empty()) return;
        ground(q);
        avenue(q);
        pylon(q);
        for (int side = -1; side <= 1; side += 2) gatehouse(q, side);
        for (int deg : MONO_DEG) monolith(q, deg);
        crypt(q);
        ringGate(q);
        dreamStair(q, p);
        spiral(q, -26, 30, p.spiralW, true);
        spiral(q, 26, 30, p.spiralE, false);
        columns(q, p);
        tiles(q, p);
    }



    // ------------------------------------------------------------------------------------------------------ the ground
    /** The plaza's height at a distance from its centre: the floor, four tiers of seats and the rim walk. */
    private static int plazaH(double r0) {
        return r0 <= 18 ? P : r0 <= 20 ? P + 1 : r0 <= 22 ? P + 2 : r0 <= 24 ? P + 3 : 0;
    }

    private static boolean inAvenue(int u, int v) { return Math.abs(u) <= 9 && v <= -24; }

    /** Every column: the plaza sunk into a levelled disc, the avenue levelled, both faced where the land falls away. */
    private static void ground(Pen q) {
        Frame f = q.f;
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                double r0 = Math.sqrt(u * u + (v - CV) * (v - CV));
                boolean disc = r0 <= 30.5, way = inAvenue(u, v);
                if (!disc && !way) continue;
                int G = q.land(u, v), H = disc ? plazaH(r0) : 0;
                boolean edge = disc ? r0 > 29.5 && !way : Math.abs(u) == 9 || v == -R;
                if (G < H) q.col(u, v, G + 1, H - 1, edge ? MAS : STONE << 4);
                floor(f, u, H, v, r0, disc);
                if (edge && G > 0) { for (int y = 1; y <= G; y++) f.masonry(u, y, v); f.clear(u, v, G + 1, G + 2); }
                else f.clear(u, v, H + 1, Math.max(H + 2, G + 2));
                boolean opening = Math.abs(u) <= 3 && v < 0 || Math.abs(v - CV) <= 1 || Math.abs(u) <= 2 && v > 0;
                if (edge && G <= 0 && !opening) f.set(u, 1, v, ((u + v) & 3) == 0 ? BRICK : WALL, ((u + v) & 3) == 0 ? 3 : 0);
                // the stairs cut down through the tiers on the four axes
                if (disc && r0 > 18 && r0 <= 24.5) {
                    int dir = Math.abs(u) <= 2 ? (v < CV ? -1 : 1) : Math.abs(v - CV) <= 1 ? 0 : 2;
                    if (dir != 2) {
                        int y = plazaH(r0);
                        int du = dir == 0 ? Integer.signum(u) : 0, dv = dir == 0 ? 0 : dir;
                        f.set(u, y, v, QUARTZ_STAIRS, f.stairs(du, dv, false));
                    }
                }
            }
    }

    private static void floor(Frame f, int u, int H, int v, double r0, boolean disc) {
        if (!disc) {                                                   // the processional way
            if (Math.abs(u) <= 3) f.set(u, H, v, Math.abs(u) == 3 ? QUARTZ : STONE, Math.abs(u) == 3 ? 0 : 6);
            else f.paving(u, H, v);
            return;
        }
        if (H < 0 && r0 > 18) { f.set(u, H, v, r0 - Math.floor(r0) < 0.5 || ((int) r0 & 1) == 1 ? STONE : QUARTZ, 6); return; }   // the tiers
        if (H == 0) { f.set(u, H, v, BRICK, f.roll(u, H, v, 91) < 0.3 ? 2 : 0); return; }
        // the plaza floor: rings of obsidian, the Silver Key inlaid in it, pale stone between
        int dv = v - CV;
        double kb = Math.sqrt(u * u + (v + 10) * (v + 10));
        boolean key = kb >= 2 && kb <= 3.3 || u == 0 && v >= -7 && v <= 6 || (u == 1 || u == 2) && (v == 4 || v == 6);
        boolean ring = r0 > 17 || Math.abs(r0 - 12) < 0.5;
        if (key || ring) f.set(u, H, v, OBSIDIAN);
        else if (((u + dv) & 1) == 0) f.set(u, H, v, QUARTZ, 0);
        else f.set(u, H, v, STONE, 4);
    }

    // ------------------------------------------------------------------------------------------------------ the way in
    /** The Processional Way: obelisks at its head and an avenue of sphinxes facing the road. */
    private static void avenue(Pen q) {
        for (int side = -1; side <= 1; side += 2) {
            for (int v = -44; v <= -36; v += 4) sphinx(q, side * 6, 0, v, -side, 0, false);
            obelisk(q, side * 8, -45);
        }
    }

    private static void obelisk(Pen q, int u, int v) {
        if (!q.in(u, v)) return;
        q.set(u, 1, v, BRICK, 3);
        for (int y = 2; y <= 9; y++) q.set(u, y, v, OBSIDIAN, 0);
        q.set(u, 10, v, QUARTZ, 1);
    }

    /**
     * A sphinx lying on its plinth at (u, v) facing (fu, fv): a lion's body of pale stone, paws forward, a human head in
     * a striped headdress. {@code great} makes the gate's guardians, twice the size.
     */
    private static void sphinx(Pen q, int u, int y0, int v, int fu, int fv, boolean great) {
        int s = great ? 2 : 1, len = 6 * s, half = s;
        if (!q.hit(u - len, v - len, u + len, v + len)) return;
        Frame f = q.f;
        int su = fv != 0 ? 1 : 0, sv = fu != 0 ? 1 : 0;                   // across the body
        for (int i = 0; i < len; i++)
            for (int k = -half - (great ? 1 : 0); k <= half + (great ? 1 : 0); k++) {
                int bu = u - fu * (len - 1 - i) + su * k, bv = v - fv * (len - 1 - i) + sv * k;
                q.set(bu, y0 + 1, bv, BRICK, i == 0 || i == len - 1 ? 3 : 0);          // the plinth
                boolean paw = i >= len - 2 * s, body = i < len - 2 * s;
                if (Math.abs(k) > half) continue;
                if (body) for (int yy = 2; yy <= 1 + 2 * s; yy++) q.set(bu, y0 + yy, bv, STONE, 4);
                else if (paw && Math.abs(k) == half) q.set(bu, y0 + 2, bv, SLAB, 7);
            }
        // the head over the forepaws, its headdress, and the haunch
        int hu = u - fu * (2 * s), hv = v - fv * (2 * s);
        for (int yy = 2 + 2 * s; yy <= 2 + 4 * s; yy++)
            for (int k = -(s - 1); k <= s - 1; k++) q.set(hu + su * k, y0 + yy, hv + sv * k, yy == 2 + 4 * s ? QUARTZ : STONE, yy == 2 + 4 * s ? 1 : 4);
        for (int k = -s; k <= s; k += 2 * s) q.set(hu + su * k, y0 + 2 + 3 * s, hv + sv * k, QUARTZ_STAIRS, f.stairs(-su * Integer.signum(k), -sv * Integer.signum(k), true));
        for (int yy = 2; yy <= 1 + 2 * s; yy++) q.set(hu + su * 0 + fu, y0 + yy, hv + fv, QUARTZ, 0);    // the chest
    }

    /** The Pylon: two battered towers with guard rooms and ladders to their tops, a lintel with its lantern eye. */
    private static void pylon(Pen q) {
        if (!q.hit(-12, -30, 12, -24)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -12); u <= Math.min(q.u1, 12); u++)
            for (int v = Math.max(q.v0, -30); v <= Math.min(q.v1, -24); v++) {
                int au = Math.abs(u), G = q.land(u, v);
                if (au <= 3) {                                          // the passage and the lintel over it
                    for (int y = 9; y <= 13; y++) {
                        if (v == -30 || v == -24) f.set(u, y, v, y == 13 ? QUARTZ : OBSIDIAN, 0);
                        else f.set(u, y, v, y == 9 || y == 13 ? QUARTZ : STONE, y == 9 || y == 13 ? 0 : 4);
                    }
                    if (u == 0 && v == -30) f.set(u, 11, v, SEA_LANTERN);
                    continue;
                }
                for (int y = Math.min(0, G + 1); y <= 18; y++) {
                    int shrink = Math.max(0, y) / 6;
                    if (au < 4 + shrink || au > 12 - shrink || v < -30 + shrink / 2 || v > -24 - shrink / 2) continue;
                    boolean room = y >= 1 && y <= 4 && au >= 6 && au <= 10 && v >= -29 && v <= -25;
                    if (room) { f.set(u, y, v, AIR); continue; }
                    boolean band = y == 5 || y == 11 || y == 17;
                    f.set(u, y, v, band ? QUARTZ : STONE, band ? 0 : (f.roll(u, y, v, 92) < 0.75 ? 4 : 3));
                }
                if (au >= 7 && au <= 9 && v >= -28 && v <= -26 && ((u + v) & 1) == 0) f.set(u, 19, v, QUARTZ, 0);
            }
        for (int side = -1; side <= 1; side += 2) {                      // the guard rooms' doors off the passage
            q.set(side * 4, 1, -27, AIR, 0);
            q.set(side * 4, 2, -27, AIR, 0);
            q.set(side * 5, 1, -27, AIR, 0);
            q.set(side * 5, 2, -27, AIR, 0);
        }
    }

    /** A side gatehouse astride the rim: a passage into the plaza, a guard room over it, a watch-fire on its roof. */
    private static void gatehouse(Pen q, int side) {
        int c0 = side * 28, c1 = side * 36, a0 = Math.min(c0, c1), a1 = Math.max(c0, c1);
        if (!q.hit(a0, CV - 4, a1, CV + 4)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, a0); u <= Math.min(q.u1, a1); u++)
            for (int v = Math.max(q.v0, CV - 4); v <= Math.min(q.v1, CV + 4); v++) {
                int G = q.land(u, v), du = Math.abs(u - side * 32), dv = Math.abs(v - CV);
                boolean wall = du == 4 || dv == 4;
                for (int y = Math.min(0, G + 1); y <= 24; y++) {
                    boolean passage = dv <= 1 && y >= 1 && y <= 4;
                    boolean room = du <= 3 && dv <= 3 && y >= 6 && y <= 10 && !wall;
                    boolean shaft = u == side * 32 && v == CV + 3 && y >= 1 && y <= 23;
                    boolean door = u == side * 32 && v == CV + 2 && y >= 1 && y <= 2;
                    if (passage || room || shaft || door) { f.set(u, y, v, AIR); continue; }
                    if (y == 0) { f.set(u, y, v, STONE, 6); continue; }
                    boolean band = y == 5 || y == 11 || y == 24;
                    boolean window = wall && y >= 7 && y <= 9 && (du == 0 || dv == 0);
                    if (window) f.set(u, y, v, BARS);
                    else f.set(u, y, v, band ? QUARTZ : STONE, band ? 0 : (f.roll(u, y, v, 93) < 0.7 ? 4 : 3));
                }
                if (wall && ((u + v) & 1) == 0) f.set(u, 25, v, QUARTZ, 0);
                if (G > 24) f.clear(u, v, 26, G + 2);
            }
        // the ladder from the passage to the guard room and the roof, the floor of the passage, the watch-fire
        for (int y = 1; y <= 24; y++) q.set(side * 32, y, CV + 3, LADDER, f.facing(0, -1));
        for (int u = Math.min(c0, c1); u <= Math.max(c0, c1); u++) for (int v = CV - 1; v <= CV + 1; v++) q.set(u, 0, v, STONE, 6);
        for (int k = 0; k <= 2; k++)                                     // a stepped pyramidion carrying the watch-fire
            q.box(side * 32 - (2 - k), 25 + k, CV - (2 - k), side * 32 + (2 - k), 25 + k, CV + (2 - k), b(QUARTZ, k == 2 ? 1 : 0));
        q.set(side * 32, 28, CV, SEA_LANTERN, 0);
    }

    // ------------------------------------------------------------------------------------------------------ the plaza
    /** A keyhole monolith on the rim: a slab of obsidian edged with quartz, a keyhole pierced through it, a lantern on top. */
    private static void monolith(Pen q, int deg) {
        double a = Math.toRadians(deg), cu = 26 * Math.cos(a), cv = CV + 26 * Math.sin(a);
        int iu = (int) Math.round(cu), iv = (int) Math.round(cv);
        if (!q.hit(iu - 4, iv - 4, iu + 4, iv + 4)) return;
        Frame f = q.f;
        double tu = -Math.sin(a), tv = Math.cos(a), ru = Math.cos(a), rv = Math.sin(a);
        for (int u = iu - 4; u <= iu + 4; u++)
            for (int v = iv - 4; v <= iv + 4; v++) {
                if (!q.in(u, v)) continue;
                double du = u - cu, dv = v - cv, s = du * tu + dv * tv, t = du * ru + dv * rv;
                if (Math.abs(s) > 2.6 || Math.abs(t) > 0.9) continue;
                for (int y = 1; y <= 16; y++) {
                    double kd = Math.sqrt(s * s + (y - 11) * (y - 11));
                    boolean hole = kd < 1.5 || Math.abs(s) < 0.6 && y >= 5 && y <= 11;
                    if (hole) { f.set(u, y, v, AIR); continue; }
                    boolean edge = Math.abs(s) > 1.9 || y == 16 || y == 1;
                    f.set(u, y, v, edge ? QUARTZ : OBSIDIAN, edge ? (y == 16 || y == 1 ? 1 : 2) : 0);
                }
            }
        q.set(iu, 17, iv, SEA_LANTERN, 0);
    }

    /** The ring-gate: banded quartz and obsidian, lantern studs, two plinths under it and the Silver Key on its chain. */
    private static void ringGate(Pen q) {
        if (!q.hit(-22, GV - 1, 22, GV + 1)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -22); u <= Math.min(q.u1, 22); u++)
            for (int v = Math.max(q.v0, GV - 1); v <= Math.min(q.v1, GV + 1); v++)
                for (int y = P; y <= GY + 22; y++) {
                    double dy = y - GY, d = Math.sqrt(u * u + dy * dy);
                    if (d < 17 || d >= GR) continue;
                    double deg = Math.toDegrees(Math.atan2(dy, u));
                    boolean stud = v != GV && d >= 18.5 && d < 20.5 && Math.abs(Math.IEEEremainder(deg, 45)) < 2.6;
                    boolean glyph = v != GV && d >= 18.5 && d < 20.5 && Math.abs(Math.IEEEremainder(deg + 22.5, 45)) < 1.6;
                    if (stud) f.set(u, y, v, SEA_LANTERN);
                    else if (glyph) f.set(u, y, v, QUARTZ, 1);
                    else if (d >= 20.5 || d < 18.5) f.set(u, y, v, QUARTZ, 0);
                    else f.set(u, y, v, OBSIDIAN);
                }
        // the two plinths cradling its foot
        for (int side = -1; side <= 1; side += 2)
            for (int a = 9; a <= 15; a++)
                for (int v = GV - 3; v <= GV + 3; v++) {
                    int u = side * a;
                    if (!q.in(u, v)) continue;
                    int low = (int) Math.floor(GY - Math.sqrt(GR * GR - a * a));
                    boolean ringCell = v >= GV - 1 && v <= GV + 1;
                    int top = ringCell ? low : Math.max(P + 1, low - 1);
                    for (int y = P + 1; y <= top; y++) f.set(u, y, v, y == top ? QUARTZ : OBSIDIAN, y == top ? 1 : 0);
                }
        // the Silver Key on its chain
        if (!q.in(0, GV)) { if (!q.hit(-4, GV, 4, GV)) return; }
        for (int u = -4; u <= 4; u++)
            for (int y = GY - 12; y <= GY + 16; y++) {
                if (!q.in(u, GV)) continue;
                double kb = Math.sqrt(u * u + (y - (GY + 10)) * (y - (GY + 10)));
                boolean bow = kb >= 2.4 && kb <= 3.5;
                boolean shaft = u == 0 && y >= GY - 11 && y <= GY + 6;
                boolean bit = (u == 1 || u == 2) && (y == GY - 11 || y == GY - 10 || y == GY - 7 || y == GY - 6) || u == 3 && y >= GY - 11 && y <= GY - 6 && y != GY - 9 && y != GY - 8;
                boolean chain = u == 0 && y >= GY + 14 && y <= GY + 16;
                if (bow || bit) f.set(u, y, GV, QUARTZ, 0);
                else if (shaft) f.set(u, y, GV, QUARTZ, 2);
                else if (chain) f.set(u, y, GV, BARS);
            }
        q.set(0, GY + 10, GV, SEA_LANTERN, 0);
    }

    /** The Crypt of the Dreamers under the plaza: a round hall with the great bier, eight alcoves of sarcophagi, two stairs. */
    private static void crypt(Pen q) {
        if (!q.hit(-14, CV - 14, 14, CV + 14)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -14); u <= Math.min(q.u1, 14); u++)
            for (int v = Math.max(q.v0, CV - 14); v <= Math.min(q.v1, CV + 14); v++) {
                int dv = v - CV;
                double r0 = Math.sqrt(u * u + dv * dv);
                if (r0 > 14.5) continue;
                double deg = Math.toDegrees(Math.atan2(dv, u));
                boolean outer = r0 > 13.2;
                boolean divider = r0 > 5.6 && Math.abs(Math.IEEEremainder(deg - 22.5, 45)) < 6.5;
                boolean pillar = r0 > 4.6 && r0 <= 5.6 && Math.abs(Math.IEEEremainder(deg - 22.5, 45)) < 9;
                f.set(u, -12, v, f.roll(u, -12, v, 94) < 0.8 ? STONE : OBSIDIAN, 4);
                f.set(u, -5, v, STONE, 6);
                for (int y = -11; y <= -6; y++) {
                    if (outer || divider) f.masonry(u, y, v);
                    else if (pillar) f.set(u, y, v, QUARTZ, 2);
                    else f.set(u, y, v, AIR);
                }
                // a sarcophagus along the middle of each alcove, but not in the two with the stairs
                double ad = Math.abs(Math.IEEEremainder(deg, 45));
                boolean stairAlcove = Math.abs(dv) <= 4 && Math.abs(u) >= 6;
                if (!stairAlcove && !outer && !divider && r0 >= 8.5 && r0 <= 11.5 && ad < 3.5) {
                    f.set(u, -11, v, QUARTZ, 0);
                    f.set(u, -10, v, SLAB, 5);
                }
            }
        // the great bier of the Dreamer in the hall, and the lantern over it
        q.box(-1, -11, CV - 2, 1, -11, CV + 2, b(QUARTZ, 0));
        q.box(0, -10, CV - 2, 0, -10, CV + 1, b(CARPET_GREY, 7));
        q.set(0, -10, CV + 2, WALL, 0);
        q.set(0, -5, CV, SEA_LANTERN, 0);
        // the two stairs down from the plaza, railed above
        for (int side = -1; side <= 1; side += 2)
            for (int j = 0; j <= 6; j++)
                for (int m = 10; m <= 11; m++) {
                    int u = side * m, v = CV - 4 + j;
                    if (!q.in(u, v)) continue;
                    f.clear(u, v, -4 - j, P);
                    f.set(u, -5 - j, v, BRICK_STAIRS, f.stairs(0, -1, false));
                    for (int y = -11; y < -5 - j; y++) f.masonry(u, y, v);
                }
        for (int side = -1; side <= 1; side += 2) {
            for (int v = CV - 4; v <= CV + 3; v++) { q.set(side * 9, P + 1, v, WALL, 0); q.set(side * 12, P + 1, v, WALL, 0); }
            q.set(side * 10, P + 1, CV + 3, WALL, 0);
            q.set(side * 11, P + 1, CV + 3, WALL, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------------ the dream stairs
    /** The long stair climbing from the land behind the gate to a landing in the air, then breaking up into the night. */
    private static void dreamStair(Pen q, Plan p) {
        if (!q.hit(-16, 36, 24, 40)) return;
        Frame f = q.f;
        int y0 = p.longFoot;
        for (int u = -16; u <= -14; u++) for (int v = 37; v <= 39; v++) if (q.in(u, v)) {     // its foot, paved
            int G = q.land(u, v);
            for (int y = G + 1; y < y0; y++) f.masonry(u, y, v);
            f.set(u, y0, v, QUARTZ, 0);
            f.clear(u, v, y0 + 1, Math.max(y0 + 3, G + 2));
        }
        for (int i = 0; i <= 27; i++) {
            int u = -13 + i, y = y0 + 1 + i;
            for (int v = 38; v <= 39; v++) {
                if (!q.in(u, v)) continue;
                f.set(u, y, v, QUARTZ_STAIRS, f.stairs(1, 0, false));
                f.set(u, y - 1, v, QUARTZ_STAIRS, f.stairs(-1, 0, true));
                if (i % 4 == 0) { int G = q.land(u, v); for (int yy = G + 1; yy < y - 1; yy++) f.set(u, yy, v, QUARTZ, 2); }
            }
        }
        // the landing in the air, and the stair breaking up beyond it
        q.box(15, y0 + 28, 37, 16, y0 + 28, 40, b(QUARTZ, 0));
        q.box(15, y0 + 27, 37, 16, y0 + 27, 40, b(QUARTZ, 2));
        for (int u = 15; u <= 16; u++) { q.set(u, y0 + 29, 37, WALL, 0); q.set(u, y0 + 29, 40, WALL, 0); }
        for (int y = y0 + 29; y <= y0 + 31; y++) q.set(16, y, 39, QUARTZ, 2);
        int[][] bits = {{18, 30}, {20, 32}, {21, 33}, {23, 35}};
        for (int[] bt : bits) for (int v = 38; v <= 39; v++) if (v == 38 || bt[0] != 21) q.set(bt[0], y0 + bt[1], v, QUARTZ_STAIRS, f.stairs(1, 0, false));
    }

    /** A spiral stair round a pale column, climbing three turns and ending in nothing; a lantern atop the column. */
    private static void spiral(Pen q, int cu, int cv, int base, boolean whole) {
        if (!q.hit(cu - 3, cv - 3, cu + 3, cv + 3)) return;
        Frame f = q.f;
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                int u = cu + a, v = cv + b;
                if (!q.in(u, v)) continue;
                int G = q.land(u, v);
                for (int y = G + 1; y < base; y++) f.masonry(u, y, v);
                if (Math.max(Math.abs(a), Math.abs(b)) <= 3) f.set(u, base, v, Math.max(Math.abs(a), Math.abs(b)) == 3 ? BRICK : QUARTZ, 0);
                f.clear(u, v, base + 1, Math.max(base + 2, G + 2));
            }
        for (int y = base + 1; y <= base + 27; y++) q.set(cu, y, cv, QUARTZ, 2);
        q.set(cu, base + 28, cv, SEA_LANTERN, 0);
        int[][] ring = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int k = 0; k < 24; k++) {
            if (!whole && (k == 17 || k == 20 || k == 21)) continue;            // the eastern one is broken
            int[] c = ring[k % 8], n = ring[(k + 1) % 8];
            int u = cu + c[0], v = cv + c[1], y = base + 1 + k;
            int du = n[0] - c[0], dv = n[1] - c[1];
            q.set(u, y, v, QUARTZ_STAIRS, f.stairs(du, dv, false));
        }
    }

    /** The circle of broken columns round the whole sanctuary. */
    private static void columns(Pen q, Plan p) {
        for (int i = 0; i < 24; i++) {
            if (p.columns[i] == 0) continue;
            double a = Math.toRadians(i * 15 - 90);
            int u = (int) Math.round(42 * Math.cos(a)), v = CV + (int) Math.round(42 * Math.sin(a));
            if (Math.abs(v) > R || !q.in(u, v)) continue;
            Frame f = q.f;
            int G = q.land(u, v);
            f.set(u, G, v, BRICK, 3);
            for (int y = G + 1; y <= G + p.columns[i]; y++) f.set(u, y, v, QUARTZ, 2);
            if (p.columns[i] > 6) f.set(u, G + p.columns[i] + 1, v, QUARTZ, 1);
            else f.set(u, G + p.columns[i] + 1, v, SLAB, 7);
        }
    }

    // ------------------------------------------------------------------------------------------------------ chests, cages, signs
    private static void tiles(Pen q, Plan p) {
        Frame f = q.f;
        long h = p.h;
        // before the gate: the Guardian's hoard
        f.chest(0, P + 1, GV - 2, 0, -1, TREASURE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        f.chest(-4, P + 1, GV - 2, 0, -1, CROSSING, "trinket:0.3");
        f.chest(4, P + 1, GV - 2, 0, -1, MANSION, null);
        f.sign(0, P + 2, GV - 1, 0, -1, "ONLY THE KEY\nMAY PASS");
        // the pylon's guard rooms and the side gatehouses' guard rooms
        f.chest(-10, 1, -28, 1, 0, Sites.SMITH, null);
        f.chest(10, 1, -28, -1, 0, Sites.CORRIDOR, "trinket:0.1");
        f.chest(-34, 6, CV - 2, 1, 0, Sites.DUNGEON, null);
        f.chest(34, 6, CV - 2, -1, 0, Sites.DESERT, null);
        f.spawner(34, 6, CV + 2, "STRAY");
        // the crypt
        int[][] alcoves = {{45, 0}, {135, 1}, {225, 2}, {315, 3}};
        String[] tables = {Sites.DUNGEON, MINESHAFT, Sites.LIBRARY, Sites.JUNGLE};
        for (int[] al : alcoves) {
            double a = Math.toRadians(al[0]);
            int u = (int) Math.round(12.5 * Math.cos(a)), v = CV + (int) Math.round(12.5 * Math.sin(a));
            f.chest(u, -11, v, -Integer.signum(u), 0, tables[al[1]], al[1] == 2 ? "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99) : al[1] == 3 ? "trinket:0.2" : null);
        }
        f.spawner(0, -11, CV - 8, "ZOMBIE");
        f.spawner(0, -11, CV + 8, "SKELETON");
        f.sign(0, -8, CV + 13, 0, -1, "THE DREAMERS\nMUST NOT\nWAKE");
        // the dream stairs: what waits where they end
        f.chest(16, p.longFoot + 29, 38, -1, 0, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 7), 0, 99) + ";trinket:0.3");
        f.sign(15, p.longFoot + 30, 39, -1, 0, "THE STAIR\nGOES ON\nIN DREAMS");
        f.chest(-27, p.spiralW + 1, 28, 0, 1, Sites.DESERT, null);
        // the way in
        f.sign(-8, 3, -46, 0, -1, "THE GATE OF\nTHE SILVER KEY");
        f.sign(8, 3, -46, 0, -1, "DREAMER\nGO NO\nFARTHER");
    }

    // ------------------------------------------------------------------------------------------------------ the pen
    private static final int MAS = -1, CARPET_GREY = 171;

    private static int b(int id, int meta) { return id << 4 | meta; }

    private static final class Pen {
        final Frame f;
        final Plans.GreatSite s;
        final int u0, v0, u1, v1;
        private final int x0, z0;
        private final int[] land = new int[256];

        Pen(Plans.GreatSite s, Canvas c, int rot, int r) {
            this.s = s;
            f = new Frame(c, s.x, s.z, s.base, rot);
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

        int land(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v);
            if (wx < x0 || wx > x0 + 15 || wz < z0 || wz > z0 + 15) return s.surface(wx, wz) - s.base;
            int i = (wx - x0) << 4 | (wz - z0);
            if (land[i] == Integer.MIN_VALUE) land[i] = s.surface(wx, wz) - s.base;
            return land[i];
        }

        void set(int u, int y, int v, int id, int meta) { if (in(u, v)) f.set(u, y, v, id, meta); }

        void put(int u, int y, int v, int m) {
            if (m >= 0) f.set(u, y, v, m >> 4, m & 15);
            else f.masonry(u, y, v);
        }

        void col(int u, int v, int y0, int y1, int m) { for (int y = y0; y <= y1; y++) put(u, y, v, m); }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int A0 = Math.max(Math.min(a0, a1), u0), A1 = Math.min(Math.max(a0, a1), u1);
            int B0 = Math.max(Math.min(b0, b1), v0), B1 = Math.min(Math.max(b0, b1), v1);
            for (int a = A0; a <= A1; a++) for (int b = B0; b <= B1; b++) for (int y = y0; y <= y1; y++) put(a, y, b, m);
        }
    }
}
