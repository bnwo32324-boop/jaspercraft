package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Golgotha, the Skull Keep (epoch 6): a fortress inside a gigantic skull, forty-one blocks high (fifty-two with its crown
 * of bone spikes), of bone and pale stone, lying on a mound of bones inside a palisade of bone pillars and iron spikes. The
 * skull is sculpted from ellipsoids: a cranium with its sutures and a crack down the brow, the orbital block with two slanted,
 * soot-dark sockets in which glowstone eyes burn, a barred nose, cheekbones, ear holes, and an upper jaw whose teeth flank
 * the gate (a portcullis raised in its arch). Its lower jaw has fallen open on the ground before it, a horseshoe wall with
 * its teeth raised round the Jaw Court; the Skull Road follows the land down (or up) from the footprint's edge through the
 * palisade gate and the gap of the chin. Eight ribs buttress the skull from the mound.
 * <p>
 * Inside: the Mouth Hall and the Hall of the Dead at the ground (flagstones, a bone aisle, sarcophagi, a dart trap); a newel
 * stair up through three floors, each ceiling a floor above: the Nasal Gallery (its nose barred) and the Barracks on boards,
 * the Eye Gallery and the Ossuary Gallery (skull shelves, ear windows), and in the crown the Bone Throne Hall, a ribbed dome
 * with a floor of bone and obsidian, a red road, a dais of three tiers and the Bone Throne, where the Bone Tyrant waits.
 * The Charnel Stair goes down from the Hall of the Dead into the crypt (a vault of pillars, loculi and sarcophagi, and a
 * chapel). Three ossuary pits lie in the mound, each with a ladder; the third opens into a tunnel that runs, level and then
 * by a stair, to the crypt's wall.
 */
final class GreatGolgotha extends GreatDesign {
    private static final int R = 46, FLOOR = -12;
    /** The floors above the ground, each the ceiling of the level below it. */
    private static final int F1 = 8, F2 = 16, F3 = 24;
    private static final double PALISADE = 43.0, LINER = 1.4;
    private static final int SU = 0, SV = 5;                 // the newel stair
    private static final int JV = -13;                       // the open jaw's centre
    private static final int ROAD_V = -37, ROAD_HALF = 3, GATE_V = -42;
    private static final int[][] RING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] PITS = {{-30, -17}, {30, -17}, {-28, 24}};
    /** The skull's solid shapes: ellipsoids {cu, cy, cv, au, ay, av, flat}; a flat one is a prism below its equator, down to the ground. */
    private static final double[][] SOLID = {
        {0, 25, 12, 19.5, 16.5, 24.5, 0},       // the cranium
        {0, 12, 16, 15.5, 12.5, 20.5, 1},       // the back of the skull
        {0, 12, -9, 14.5, 12.5, 11, 1},         // the face
        {0, 21.5, -15, 16, 5, 8, 0},            // the orbital block
        {0, 30, -3, 14.5, 11, 17, 0},           // the forehead
        {-13, 11.5, -8, 4.5, 3.2, 8.5, 0},      // the cheekbones
        {13, 11.5, -8, 4.5, 3.2, 8.5, 0},
        {0, 5, -12, 12, 7, 8.5, 1},             // the upper jaw
    };
    /** The hollows within them (the shell is three blocks thick): the cranium, the back, the face. */
    private static final double[][] CAVITY = {
        {0, 25, 12, 16.5, 13.5, 21.5},
        {0, 12, 16, 12.5, 9.5, 17.5},
        {0, 12, -7, 11.5, 9.5, 9},
    };
    private static final int[][] SPOTS;
    /** The packs led by a horror, one of which gets an elder for its leader. */
    private static final int[] ELDERS = {2, 3, 4, 6, 7, 15, 18};
    private static final String[] PACKS = {
        "skeleton+stray", "zombie+husk",
        "cult_zealot+vindicator", "tomb_crawler+silverfish",
        "ghoul+zombie_villager", "skeleton+wither_skeleton", "hound+ghoul",
        "star_spawn+mi_go", "enderman+endermite",
        "vindicator+zombie", "cult_adept+evoker",
        "witch+cult_adept", "shoggoth+slime", "skeleton+cave_spider", "ghoul+spider",
        "hound+creeper+illusioner", "star_spawn+skeleton", "cult_zealot+wither_skeleton",
        "tomb_crawler+deep_one", "zombie+husk+ghoul", "skeleton+creeper",
        "wither_skeleton+hound", "ghoul+tomb_crawler",
        "silverfish+cave_spider",
        "nightgaunt", "deep_one+zombie_villager",
    };

    static {
        int back = mound(0, 39);
        SPOTS = new int[][] {
            {-7, 1, -28}, {7, 1, -28},                                       // the Jaw Court
            {-5, 1, -13}, {5, 1, -7},                                        // the Mouth Hall
            {-3, 1, 1}, {3, 1, 20}, {-3, 1, 25},                             // the Hall of the Dead
            {0, F1 + 1, -9}, {-8, F1 + 1, -3},                               // the Nasal Gallery
            {7, F1 + 1, 14}, {-7, F1 + 1, 26},                               // the Barracks
            {-5, F2 + 1, -9}, {5, F2 + 1, -9}, {-9, F2 + 1, 14}, {6, F2 + 1, 25},     // the Eye Gallery, the Ossuary Gallery
            {-9, F3 + 1, 0}, {11, F3 + 1, 14}, {-11, F3 + 1, 24},             // the Throne Hall
            {PITS[0][0], -4, PITS[0][1]}, {PITS[1][0], -4, PITS[1][1]}, {PITS[2][0], -4, PITS[2][1]},
            {0, FLOOR + 1, 30}, {-3, FLOOR + 1, 38},                         // the Crypt
            {-18, -4, PITS[2][1]},                                           // the Ossuary Tunnel
            {0, 35, 14},                                                     // the dome of the Throne Hall (fliers)
            {0, back + 1, 39},                                               // the back of the mound
        };
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Layout l = new Layout();
        l.boss = new int[] {0, F3 + 4, 31};                 // before the throne, on the dais
        int elder = ELDERS[r.nextInt(ELDERS.length)];
        for (int i = 0; i < SPOTS.length; i++) l.garrisons.add(g(SPOTS[i][0], SPOTS[i][1], SPOTS[i][2], i == elder ? "!" + PACKS[i] : PACKS[i]));
        return l;
    }

    /** The shape arrays of a column (allocated once per chunk). */
    private static final class Cols {
        final double[] lo = new double[SOLID.length], hi = new double[SOLID.length];
        final double[] clo = new double[CAVITY.length], chi = new double[CAVITY.length], llo = new double[CAVITY.length], lhi = new double[CAVITY.length];
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int[] b = bounds(f, c, R);
        if (b == null) return;
        int gh = Math.max(-9, Math.min(9, s.surface(f.wx(0, -R), f.wz(0, -R)) - s.base));
        Cols k = new Cols();
        for (int u = b[0]; u <= b[1]; u++)
            for (int v = b[2]; v <= b[3]; v++) column(s, f, u, v, gh, k);
        gate(f, b, gh);
        bones(f, b);
        ribs(f, b);
        crown(f, b);
        eyes(f, b);
        teeth(f, b);
        portcullis(f, b);
        nose(f, b);
        ears(f, b);
        spiral(f, b);
        crypt(f, b);
        interior(f, b);
        throne(f, b);
        for (int[] p : PITS) pit(f, b, p[0], p[1]);
        tunnel(f, b);
        court(f, b);
        tiles(f, b, s.hash);
    }

    // ------------------------------------------------------------------ shapes

    /** The mound of bones: its height (above the base) at a column, nothing before the face. */
    private static int mound(int u, int v) {
        double dm = Math.sqrt(u * u + (v - 8.0) * (v - 8.0));
        double h = 12 - 12 * (dm / 40) * (dm / 40);
        double front = Math.max(0, Math.min(1, (v + 22) / 12.0));
        return (int) Math.floor(Math.max(0, h * front));
    }

    /** The y-range an ellipsoid covers in a column (grown by {@code grow}), or lo > hi; a flat one reaches down to the ground. */
    private static void span(double[] e, int u, int v, double grow, boolean flat, double[] lo, double[] hi, int i) {
        double au = e[3] + grow, ay = e[4] + grow, av = e[5] + grow;
        double q = (u - e[0]) * (u - e[0]) / (au * au) + (v - e[2]) * (v - e[2]) / (av * av);
        if (q >= 1) { lo[i] = 1; hi[i] = 0; return; }
        double h = ay * Math.sqrt(1 - q);
        lo[i] = flat ? 0 : e[1] - h;
        hi[i] = e[1] + h;
    }

    private static boolean in(double[] lo, double[] hi, int y) {
        for (int i = 0; i < lo.length; i++) if (y >= lo[i] && y <= hi[i]) return true;
        return false;
    }

    /** Whether a cell lies in the skull's hollows. */
    private static boolean cav(int u, int y, int v) {
        for (double[] e : CAVITY) {
            double a = (u - e[0]) / e[3], b = (y - e[1]) / e[4], c = (v - e[2]) / e[5];
            if (a * a + b * b + c * c <= 1) return true;
        }
        return false;
    }

    /** The ground-level halls: the Mouth Hall behind the teeth and the Hall of the Dead, grown by {@code pad}. */
    private static boolean hall(int u, int v, int pad) {
        int au = Math.abs(u);
        return au <= 8 + pad && v >= -19 + Math.max(0, au - 4) * 0.9 - pad && v <= -4 + pad || au <= 6 + pad && v >= -3 - pad && v <= 28 + pad;
    }

    /** The nasal opening (wide below, narrow above), at height y: the half width, or -1. */
    private static double noseWidth(int y) { return y < 9 || y > 16 ? -1 : 4.2 - 0.38 * (y - 9); }

    /** Openings in the skull: the eye sockets, the nose, the gate between the teeth. */
    private static boolean opening(int u, int y, int v) {
        if (v >= 4 && v <= 6 && y >= 17 && y <= 19 && Math.abs(u) >= 13) return true;               // the ear holes
        if (v > -13) return false;
        if (y >= 15 && y <= 26) {
            double du = Math.abs(u) - 7.5, dy = y - 20.5;
            double p = du * 0.969 - dy * 0.247, q = du * 0.247 + dy * 0.969;       // the sockets slant, their outer ends lowest
            double c = (v + 21.5) / 6.0;
            if (Math.abs(u) <= 14 && p * p / 21.2 + q * q / 13 + c * c <= 1) return true;
        }
        double w = noseWidth(y);
        if (w >= 0 && Math.abs(u) <= w && !(Math.abs(u) < 0.6 && y >= 11)) return true;
        if (y >= 1 && y <= 6 && v < -17 && (Math.abs(u) <= 1 || Math.abs(u) == 2 && y <= 5)) return true;
        return false;
    }

    /** A shell block touching an opening of the face: soot-dark stone. */
    private static boolean lined(int u, int y, int v) {
        if (v >= -12 || y > 27 || Math.abs(u) > 15 || y <= 7 && (Math.abs(u) > 3 || v > -16)) return false;
        return (opening(u + 1, y, v) || opening(u - 1, y, v) || opening(u, y + 1, v) || opening(u, y - 1, v) || opening(u, y, v + 1) || opening(u, y, v - 1));
    }

    private static boolean reserved(int u, int v) {
        for (int[] s : SPOTS) if (Math.abs(u - s[0]) <= 1 && Math.abs(v - s[2]) <= 1) return true;
        return false;
    }

    // ------------------------------------------------------------------ columns

    private static void column(Plans.GreatSite s, Frame f, int u, int v, int gh, Cols k) {
        int au = Math.abs(u);
        int nat = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
        if (v <= ROAD_V && (au <= ROAD_HALF || au <= ROAD_HALF + 2 && v <= GATE_V)) { road(f, u, v, nat, gh); return; }
        double d = Math.sqrt(u * u + v * v);
        if (d > PALISADE + 1.8) return;                  // the land beyond the palisade lies as it is
        for (int y = Math.max(FLOOR, nat - 1); y < 0; y++) f.rubble(u, y, v);
        f.clear(u, v, 1, Math.max(3, nat + 3));
        if (d >= PALISADE) { palisade(f, u, v, nat); return; }
        ground(f, u, v);
        mandible(f, u, v);
        skull(f, u, v, k);
    }

    /** The Skull Road: from the land's own height at the footprint's edge down (or up) to the court, in bone stairs. */
    private static int roadY(int v, int gh) { return v >= ROAD_V ? 0 : (int) Math.round(gh * (double) (ROAD_V - v) / (R + ROAD_V)); }

    private static void road(Frame f, int u, int v, int nat, int gh) {
        int au = Math.abs(u), yb = roadY(v, gh);
        for (int y = Math.max(FLOOR, nat - 1); y < yb; y++) f.rubble(u, y, v);
        if (au <= ROAD_HALF) {
            f.clear(u, v, yb + 1, Math.max(yb + 9, nat + 3));
            boolean step = gh != 0 && v > -R && roadY(v, gh) != roadY(v - 1, gh);
            boolean a = (u + v & 1) == 0;
            if (step) f.set(u, yb, v, BRICK_STAIRS, f.stairs(0, gh > 0 ? -1 : 1, false));
            else f.set(u, yb, v, a ? BONE : BRICK, a ? 0 : 3);
            if (v >= GATE_V - 3 && v <= GATE_V) {                                                                                   // the lintel
                for (int y = yb + 8; y <= yb + 9; y++) f.set(u, y, v, y == yb + 8 ? BRICK : BONE, y == yb + 8 ? 3 : 0);
                if (v == GATE_V - 3 && (u & 1) == 0) f.set(u, yb + 10, v, SKULL, 1);
            }
            return;
        }
        // the gate's pillars
        if (v < -45) return;
        f.clear(u, v, yb + 1, Math.max(yb + 14, nat + 3));
        for (int y = yb; y <= yb + 11; y++) {
            double r = f.roll(u, y, v, 612);
            if (y % 4 == 3) f.set(u, y, v, BRICK, 3);
            else f.set(u, y, v, BONE, r < 0.2 ? 4 : 0);
        }
        if ((u + v & 1) == 0) f.set(u, yb + 12, v, SKULL, 1);
    }

    /** The palisade: bone pillars with skulls, iron spikes between them; it retains higher land. */
    private static void palisade(Frame f, int u, int v, int nat) {
        int base = Math.max(FLOOR, Math.min(nat - 1, 0));
        double arc = (Math.atan2(u, v) + Math.PI) * 43;
        int cell = (int) Math.round(arc);
        boolean pillar = Math.floorMod(cell, 4) == 0;
        for (int y = base; y <= 0; y++) f.rubble(u, y, v);
        boolean worn = nat <= 1 && f.roll(u, 0, v, 613) < 0.14;           // a broken stretch (never where the wall holds back the land)
        int h = pillar ? (worn ? 3 + (int) (f.roll(u, 1, v, 614) * 3) : 5 + (int) (f.roll(u, 0, v, 604) * 6)) : worn ? 1 : 2;
        h = Math.max(h, nat + 1);
        for (int y = 1; y <= h; y++) f.set(u, y, v, BONE, 0);
        if (pillar) { if (!worn) f.set(u, h + 1, v, SKULL, 1); }
        else if (!worn) for (int y = h + 1; y <= h + 2; y++) f.set(u, y, v, BARS);
    }

    /** The ground within the palisade: grey gravel and bone dust, the bone road, the mound, the litter of bones. */
    private static void ground(Frame f, int u, int v) {
        int au = Math.abs(u);
        double g = f.roll(u, 0, v, 601);
        boolean road = au <= 2 && v >= ROAD_V && v <= -21;
        if (road) f.set(u, 0, v, (u + v & 1) == 0 ? BONE : BRICK, (u + v & 1) == 0 ? 0 : 3);
        else f.set(u, 0, v, g < 0.36 ? GRAVEL : g < 0.60 ? STONE : g < 0.78 ? BONE : g < 0.90 ? BRICK : COBBLE, g < 0.36 ? 0 : g < 0.60 ? 5 : g < 0.78 ? 0 : g < 0.90 ? 2 : 0);
        int mh = mound(u, v);
        for (int y = 1; y <= mh; y++) {
            double q = f.roll(u, y, v, 602);
            if (q < 0.34) f.set(u, y, v, BONE, q < 0.1 ? 4 : q < 0.2 ? 8 : 0);
            else if (q < 0.58) f.set(u, y, v, GRAVEL, 0);
            else if (q < 0.78) f.set(u, y, v, STONE, 5);
            else if (q < 0.90) f.set(u, y, v, COBBLE, 0);
            else f.set(u, y, v, MOSSY, 0);
        }
        if (road || reserved(u, v)) return;
        double lit = f.roll(u, mh + 1, v, 603);
        if (lit < 0.025) f.set(u, mh + 1, v, BONE, lit < 0.012 ? 4 : 8);
        else if (lit < 0.045) f.set(u, mh + 1, v, SKULL, 1);
        else if (lit < 0.052 && mh > 2) { for (int y = mh + 1; y <= mh + 2 + (int) (lit * 200) % 3; y++) f.set(u, y, v, BONE, 0); }
    }

    /** The lower jaw lying open before the skull: a horseshoe wall, its teeth raised, its rami climbing to the cheeks. */
    private static void mandible(Frame f, int u, int v) {
        int au = Math.abs(u);
        double ox = u / 17.0, oz = (v - JV) / 23.0, ix = u / 12.5, iz = (v - JV) / 18.5;
        double outer = ox * ox + oz * oz, inner = ix * ix + iz * iz;
        int h = 0;
        boolean teeth = false;
        if (v <= JV + 4 && outer <= 1 && inner > 1) {
            if (au <= 2 && v < JV) return;               // the chin gate
            h = 7;
            teeth = inner < 1.3;
        } else if (v > JV + 4 && v <= JV + 14 && au >= 12 && au <= 17) {
            h = 7 + (v - (JV + 4));
        }
        if (h == 0) return;
        for (int y = 1; y <= h; y++) {
            double r = f.roll(u, y, v, 605);
            f.set(u, y, v, BONE, r < 0.18 ? 4 : r < 0.3 ? 8 : 0);
        }
        if (teeth) {
            double arc = Math.atan2(u, -(v - JV)) * 18.5;
            if (Math.floorMod((int) Math.round(arc), 3) != 0) for (int y = h + 1; y <= h + 4; y++) f.set(u, y, v, y == h + 4 ? SLAB : QUARTZ, y == h + 4 ? 7 : 0);
        }
    }

    /** The skull: its shell of bone and pale stone, its hollows, floors and openings. */
    private static void skull(Frame f, int u, int v, Cols k) {
        int ymin = 99, ymax = -99;
        for (int i = 0; i < SOLID.length; i++) {
            span(SOLID[i], u, v, 0, SOLID[i][6] > 0, k.lo, k.hi, i);
            if (k.lo[i] <= k.hi[i]) { ymin = Math.min(ymin, (int) Math.ceil(k.lo[i])); ymax = Math.max(ymax, (int) Math.floor(k.hi[i])); }
        }
        boolean room = hall(u, v, 0), wall = !room && hall(u, v, 1);
        if (ymin > ymax && !room && !wall) return;
        for (int i = 0; i < CAVITY.length; i++) {
            span(CAVITY[i], u, v, 0, false, k.clo, k.chi, i);
            span(CAVITY[i], u, v, LINER, false, k.llo, k.lhi, i);
        }
        if (room) plate(f, u, 0, v);
        int top = Math.max(ymax, room || wall ? F1 : 0);
        for (int y = 1; y <= top; y++) {
            boolean solid = in(k.lo, k.hi, y) || wall && y <= F1;
            boolean cav = in(k.clo, k.chi, y);
            boolean level = y == F1 || y == F2 || y == F3;
            boolean hollow = !level && (y < F1 ? room : cav);
            boolean plate = level && (cav || y == F1 && room);
            if (!solid && !hollow && !plate) continue;
            if (opening(u, y, v)) { f.set(u, y, v, AIR); continue; }
            if (plate) plate(f, u, y, v);
            else if (hollow) f.set(u, y, v, AIR);
            else if (v < -12 && lined(u, y, v)) soot(f, u, y, v);
            else shell(f, u, y, v, in(k.llo, k.lhi, y) || wall && y <= F1);
        }
    }

    private static void plate(Frame f, int u, int y, int v) {
        boolean a = (u + v & 1) == 0;
        if (y == 0) {                                    // flagstones with a bone aisle
            double r = f.roll(u, 0, v, 608);
            if (Math.abs(u) <= 1) f.set(u, 0, v, BONE, 0);
            else if (r < 0.40) f.set(u, 0, v, BRICK, 0);
            else if (r < 0.66) f.set(u, 0, v, BRICK, 2);
            else if (r < 0.88) f.set(u, 0, v, STONE, 5);
            else f.set(u, 0, v, COBBLE, 0);
        } else if (y == F1) f.set(u, y, v, PLANKS, (u & 1) == 0 ? 5 : 1);                 // dark boards: the barracks' floor, the ceiling of the halls
        else if (y == F2) f.set(u, y, v, a ? BONE : STONE, a ? 0 : 6);
        else f.set(u, y, v, a ? BONE : OBSIDIAN, 0);                                       // the throne hall: bone and obsidian
    }

    private static void soot(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 607);
        if (r < 0.45) f.set(u, y, v, COBBLE, 0);
        else if (r < 0.75) f.set(u, y, v, BRICK, 2);
        else if (r < 0.90) f.set(u, y, v, STONE, 5);
        else f.set(u, y, v, OBSIDIAN, 0);
    }

    private static void shell(Frame f, int u, int y, int v, boolean liner) {
        double r = f.roll(u, y, v, 606);
        if (liner) {                                     // the inside: bone ribs, then bone, cracked brick and andesite
            if (Math.floorMod(v + 3, 8) < 2) { f.set(u, y, v, BONE, 0); return; }
            if (r < 0.55) f.set(u, y, v, BONE, r < 0.1 ? 4 : 0);
            else if (r < 0.73) f.set(u, y, v, BRICK, 2);
            else if (r < 0.87) f.set(u, y, v, BRICK, 0);
            else f.set(u, y, v, STONE, 5);
            return;
        }
        // the seams of the cranium and the crack down the right of the brow
        if (y > 18 && v < 2 && u > 4 && u < 12 && Math.abs(u - (8 + 2.5 * Math.sin(y / 2.5))) < 0.8 && r < 0.9) { f.set(u, y, v, STONE, 5); return; }
        if (y >= 31 && Math.abs(u) < 0.5) { f.set(u, y, v, COBBLE, 0); return; }
        if (y >= 22 && v > 1 && v < 8 && Math.abs(v - (4 + 1.4 * Math.sin(u * 0.8))) < 0.6) { f.set(u, y, v, COBBLE, 0); return; }
        if (y < 14 && f.roll(u, y, v, 609) < (14 - y) * 0.016) { f.set(u, y, v, MOSSY, 0); return; }       // moss on the lower bones
        if (r < 0.80) f.set(u, y, v, BONE, r < 0.14 ? 4 : r < 0.24 ? 8 : 0);
        else if (r < 0.86) f.set(u, y, v, STONE, 5);
        else if (r < 0.90) f.set(u, y, v, QUARTZ, 0);
        else if (r < 0.94) f.set(u, y, v, STONE, 6);
        else if (r < 0.97) f.set(u, y, v, STONE, 4);
        else f.set(u, y, v, COBBLE, 0);
    }

    // ------------------------------------------------------------------ exterior features

    /** The gate in the palisade: a lamp on each pillar. */
    private static void gate(Frame f, int[] b, int gh) {
        if (!hit(b, -6, -46, 6, -41)) return;
        int yb = roadY(-43, gh);
        f.set(-4, yb + 6, -43, GLOWSTONE);
        f.set(4, yb + 6, -43, GLOWSTONE);
        f.sign(4, gh + 2, -46, 0, -1, "GOLGOTHA\nTHE SKULL KEEP");
    }

    /** Whether femurs and heaps of skulls may lie at a column: on the mound beside and behind the skull, clear of the pits and the ribs' feet. */
    private static boolean boneGround(int u, int v) {
        if (u * u + v * v > 38 * 38 || Math.abs(u) > 35 || !(Math.abs(u) > 21 && v > -10 || v > 37)) return false;
        for (int[] p : PITS) if ((u - p[0]) * (u - p[0]) + (v - p[1]) * (v - p[1]) < 64) return false;
        return !reserved(u, v);
    }

    /** Femurs and heaps of skulls lying about the mound. */
    private static void bones(Frame f, int[] b) {
        for (int u = b[0] - 6; u <= b[1] + 6; u++)
            for (int v = b[2] - 6; v <= b[3] + 6; v++) {
                if (!boneGround(u, v)) continue;
                double r = f.roll(u, 0, v, 630);
                if (r < 0.010) {                                                    // a femur
                    boolean alongU = f.roll(u, 1, v, 631) < 0.5;
                    int len = 4 + (int) (f.roll(u, 2, v, 632) * 3);
                    for (int i = 0; i < len; i++) {
                        int x = alongU ? u + i : u, z = alongU ? v : v + i;
                        if (!boneGround(x, z)) break;
                        if (i == 0 || i == len - 1) { f.set(x, mound(x, z) + 1, z, QUARTZ, 0); if (i == 0) f.set(x + (alongU ? 0 : 1), mound(x, z) + 1, z + (alongU ? 1 : 0), QUARTZ, 0); }
                        else f.set(x, mound(x, z) + 1, z, BONE, alongU ? 4 : 8);
                    }
                } else if (r < 0.015) {                                             // a heap of bones and skulls
                    for (int du = -1; du <= 1; du++)
                        for (int dv = -1; dv <= 1; dv++) {
                            int x = u + du, z = v + dv, h = du == 0 && dv == 0 ? 3 : (du + dv & 1) == 0 ? 1 : 2;
                            if (!boneGround(x, z)) continue;
                            for (int y = 1; y <= h; y++) f.set(x, mound(x, z) + y, z, BONE, 0);
                            if (h >= 2) f.set(x, mound(x, z) + h + 1, z, SKULL, 1);
                        }
                }
            }
    }

    /** Ribs buttressing the skull's sides: arches of bone from the mound to the cranium, bending in to meet it where it narrows. */
    private static void ribs(Frame f, int[] b) {
        for (int side = -1; side <= 1; side += 2)
            for (int rv = 6; rv <= 30; rv += 8) {
                if (!hit(b, side > 0 ? 9 : -40, rv - 2, side > 0 ? 40 : -9, rv + 3)) continue;
                double q = (rv + 1 - 12) / 24.5;
                int end = (int) Math.round(19.5 * Math.sqrt(Math.max(0.02, 1 - q * q - 0.004))) - 1;
                int n = 130;
                for (int i = 0; i <= n; i++) {
                    double t = i / (double) n * Math.PI / 2;
                    int u = (int) Math.round(side * (end + (37.5 - end) * Math.cos(t))), y = (int) Math.round(3 + 23 * Math.sin(t));
                    int thick = t < 1.0 ? 2 : 1;
                    for (int dv = 0; dv <= thick; dv++) {
                        f.set(u, y, rv + dv, BONE, 0);
                        f.set(u - side, y, rv + dv, BONE, 0);
                    }
                }
            }
    }

    /** The crown: spikes of bone round the top of the cranium, a lantern on the tallest. */
    private static void crown(Frame f, int[] b) {
        if (!hit(b, -12, -3, 12, 27)) return;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + 0.3;
            int cu = (int) Math.round(9 * Math.sin(a)), cv = 12 + (int) Math.round(12 * Math.cos(a));
            int len = (i & 1) == 0 ? 12 : 8;
            double q = cu * cu / (19.5 * 19.5) + (cv - 12) * (cv - 12) / (24.5 * 24.5);
            int ytop = (int) Math.floor(25 + 16.5 * Math.sqrt(Math.max(0, 1 - q)));
            for (int t = -2; t <= len; t++) {
                double rad = 1.9 * (1 - (double) Math.max(0, t) / len) + 0.3;
                int ri = (int) Math.ceil(rad);
                for (int du = -ri; du <= ri; du++)
                    for (int dv = -ri; dv <= ri; dv++) if (du * du + dv * dv <= rad * rad + 0.3) f.set(cu + du, ytop + t, cv + dv, BONE, 0);
            }
            if (i == 0) f.set(cu, ytop + len + 1, cv, SEA_LANTERN);
        }
    }

    /** The eyes: orbs of glowstone burning deep in the sockets. */
    private static void eyes(Frame f, int[] b) {
        if (!hit(b, -14, -22, 14, -9)) return;
        for (int side = -1; side <= 1; side += 2) {
            double cu = 7.5 * side, cy = 20.0, cv = -16.0, rad = 2.5;
            for (int u = (int) Math.floor(cu - rad); u <= (int) Math.ceil(cu + rad); u++)
                for (int y = 17; y <= 23; y++)
                    for (int v = -20; v <= -13; v++) {
                        double du = u - cu, dy = y - cy, dv = v - cv;
                        if (du * du + dy * dy + dv * dv > rad * rad) continue;
                        f.set(u, y, v, GLOWSTONE);
                    }
        }
    }

    /** The upper teeth along the front of the jaw, canines by the gate. */
    private static void teeth(Frame f, int[] b) {
        if (!hit(b, -12, -26, 12, -14)) return;
        for (int u = -11; u <= 11; u++) {
            int au = Math.abs(u), m = au % 3;
            if (au <= 2 || m == 2) continue;
            double fm = -12 - 8.5 * Math.sqrt(Math.max(0, 1 - (u / 12.0) * (u / 12.0))), ff = -9 - 11 * Math.sqrt(Math.max(0, 1 - (u / 14.5) * (u / 14.5)));
            int vf = (int) Math.floor(Math.min(fm, ff));
            int h = au <= 4 ? 8 : au <= 7 ? 6 : 5;
            if (f.roll(u, 0, vf, 620) < 0.2) h--;
            for (int y = 1; y <= h; y++) { f.set(u, y, vf, QUARTZ, 0); f.set(u, y, vf - 1, QUARTZ, 0); }
            f.set(u, h + 1, vf - 1, SLAB, 7);
        }
    }

    /** The gate between the upper teeth: a portcullis raised into its arch. */
    private static void portcullis(Frame f, int[] b) {
        if (!hit(b, -3, -22, 3, -20)) return;
        for (int u = -2; u <= 2; u++) for (int y = 5; y <= 6; y++) if (Math.abs(u) <= 1 || y == 5) f.set(u, y, -21, BARS);
    }

    /** Bars across the nose, so no one steps out of the Nasal Gallery by mistake. */
    private static void nose(Frame f, int[] b) {
        if (!hit(b, -4, -18, 4, -16)) return;
        for (int y = 9; y <= 15; y++) {
            double w = noseWidth(y);
            for (int u = -4; u <= 4; u++) if (Math.abs(u) <= w && !(Math.abs(u) < 0.6 && y >= 11)) f.set(u, y, -17, BARS);
        }
    }

    /** The ear holes of the skull: windows into the Ossuary Gallery, barred inside. */
    private static void ears(Frame f, int[] b) {
        if (!hit(b, -15, 3, 15, 7)) return;
        for (int side = -1; side <= 1; side += 2) for (int y = 17; y <= 19; y++) for (int v = 4; v <= 6; v++) f.set(13 * side, y, v, BARS);
    }

    // ------------------------------------------------------------------ the inside

    private static boolean near(int du, int dv, int[] c) { return Math.abs(du - c[0]) <= 1 && Math.abs(dv - c[1]) <= 1; }

    /** The newel stair from the Hall of the Dead to the Throne Hall, railed on every floor. */
    private static void spiral(Frame f, int[] b) {
        if (!hit(b, SU - 2, SV - 2, SU + 2, SV + 2)) return;
        for (int[] c : RING) f.clear(SU + c[0], SV + c[1], 1, F3 + 3);
        for (int y = 0; y <= F3 + 1; y++) f.set(SU, y, SV, BONE, 0);
        f.set(SU, F3 + 2, SV, SKULL, 1);
        for (int h = 1; h <= F3; h++) {
            int[] p = RING[h % 8], n = RING[(h + 1) % 8];
            f.set(SU + p[0], h, SV + p[1], BRICK_STAIRS, f.stairs(n[0] - p[0], n[1] - p[1], false));
        }
        for (int fl : new int[] {F1, F2, F3}) {
            int[] a = RING[fl % 8], z = RING[(fl + 1) % 8], p = RING[(fl + 7) % 8];
            for (int du = -2; du <= 2; du++)
                for (int dv = -2; dv <= 2; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != 2) continue;
                    if (near(du, dv, a) || near(du, dv, z) || near(du, dv, p)) continue;
                    f.set(SU + du, fl + 1, SV + dv, WALL, 0);
                }
        }
    }

    /** The Charnel Stair from the Hall of the Dead down to the crypt, and the crypt with its chapel. */
    private static void crypt(Frame f, int[] b) {
        if (!hit(b, -9, 9, 9, 45)) return;
        for (int u = -9; u <= 9; u++)
            for (int v = 9; v <= 45; v++) {
                int au = Math.abs(u);
                for (int y = FLOOR; y <= -1; y++) { double r = f.roll(u, y, v, 610); f.set(u, y, v, r < 0.7 ? BONE : STONE, r < 0.7 ? 0 : 5); }
                if (au > 6 || v < 20 || v > 43) {
                    if (au == 7 && v >= 22 && v <= 42 && v % 3 == 1) { f.set(u, -10, v, SKULL, 1); f.set(u, -8, v, SKULL, 1); }      // loculi in the walls
                    continue;
                }
                f.set(u, FLOOR, v, (u + v & 1) == 0 ? STONE : BONE, (u + v & 1) == 0 ? 5 : 0);
                int top = au <= 2 ? -5 : au <= 4 ? -6 : -7;
                f.clear(u, v, FLOOR + 1, top);
                if (au == 3 && v % 4 == 3 && v < 40) for (int y = FLOOR + 1; y <= top; y++) f.set(u, y, v, BONE, 0);
                if (au <= 2 && v % 8 == 4 && v < 40) f.set(u, top, v, GLOWSTONE);
            }
        for (int u = -1; u <= 1; u++)
            for (int v = 11; v <= 23; v++) {
                int y = -(v - 11);
                for (int k = FLOOR + 1; k < y; k++) f.set(u, k, v, BONE, 0);
                f.set(u, y, v, BRICK_STAIRS, f.stairs(0, -1, false));
                f.clear(u, v, y + 1, Math.max(y + 4, 0));
            }
        for (int v = 12; v <= 23; v++) for (int u = -2; u <= 2; u += 4) { f.set(u, 1, v, WALL, 0); for (int y = Math.max(FLOOR + 1, -(v - 11)); y <= 0; y++) f.set(u, y, v, BONE, 0); }
        for (int u = -2; u <= 2; u++) f.set(u, 1, 24, WALL, 0);
        for (int side = -1; side <= 1; side += 2) {                                         // sarcophagi along the walls, webs in the vault
            for (int v : new int[] {30, 37}) {
                f.set(5 * side, FLOOR + 1, v, STONE, 5);
                f.set(5 * side, FLOOR + 1, v + 1, STONE, 5);
                f.set(5 * side, FLOOR + 2, v, SKULL, 1);
            }
            for (int v = 22; v <= 40; v += 3) f.set(6 * side, -8, v, WEB);
        }
        // the chapel at the end
        for (int u = -2; u <= 2; u++) f.set(u, FLOOR + 1, 42, OBSIDIAN);
        f.set(0, FLOOR + 2, 42, SKULL, 1);
        f.set(-2, FLOOR + 2, 42, SKULL, 1);
        f.set(2, FLOOR + 2, 42, SKULL, 1);
    }

    private static void put(Frame f, int u, int y, int v, int id, int meta) { if (cav(u, y, v)) f.set(u, y, v, id, meta); }

    private static void lamp(Frame f, int u, int y, int v) { if (cav(u, y, v)) f.set(u, y, v, GLOWSTONE); }

    /** A lamp on a chain from the ceiling plate above cell y. */
    private static void hang(Frame f, int u, int y, int v, int len) {
        for (int i = 0; i < len; i++) put(f, u, y - i, v, FENCE, 0);
        put(f, u, y - len, v, GLOWSTONE, 0);
    }

    /** The furniture of the levels: pillars, partitions, bunks, racks and the rest. */
    private static void interior(Frame f, int[] b) {
        if (!hit(b, -17, -20, 17, 34)) return;
        // the Mouth Hall: two rows of pillars, skulls in the walls, lamps in the ceiling
        for (int side = -1; side <= 1; side += 2) {
            for (int v = -16; v <= -8; v += 4) { for (int y = 1; y <= F1 - 1; y++) f.set(4 * side, y, v, BONE, 0); f.set(4 * side, F1 - 1, v, BRICK, 3); }
            for (int v : new int[] {-12, -8}) f.set(9 * side, 3, v, SKULL, 1);
        }
        f.set(0, F1, -15, GLOWSTONE);
        f.set(0, F1, -9, GLOWSTONE);
        // the Hall of the Dead: sarcophagi, skulls in the walls, lamps
        for (int side = -1; side <= 1; side += 2) {
            for (int v : new int[] {0, 9, 13, 17, 21, 25}) {
                f.set(5 * side, 1, v, STONE, 5);
                f.set(5 * side, 1, v + 1, STONE, 5);
                f.set(5 * side, 2, v, SKULL, 1);
            }
            for (int v = 2; v <= 26; v += 6) f.set(7 * side, 3, v, SKULL, 1);
            f.set(4 * side, F1, side > 0 ? 10 : 22, GLOWSTONE);
        }
        f.set(2, -2, 14, GLOWSTONE);
        f.set(-2, -5, 17, GLOWSTONE);
        // the partition between the Nasal Gallery and the Barracks, with its arch
        for (int u = -13; u <= 13; u++)
            for (int y = F1 + 1; y <= F2 - 1; y++) {
                if (Math.abs(u) <= 2 && y <= F1 + 5 && !(Math.abs(u) == 2 && y == F1 + 5)) continue;
                if (Math.abs(u) <= 3 && (y == F1 + 6 || Math.abs(u) == 3 && y <= F1 + 5)) { put(f, u, y, -1, BRICK, 3); continue; }
                if (cav(u, y, -1)) f.masonry(u, y, -1);
            }
        // the Nasal Gallery: benches along the walls, a lamp
        for (int side = -1; side <= 1; side += 2) for (int v = -13; v <= -6; v++) if (v % 4 != 0) put(f, 7 * side, F1 + 1, v, SLAB, 5);
        hang(f, 0, F2 - 1, -9, 2);
        // the Barracks: bunks along both sides, lamps
        for (int v = 10; v <= 28; v += 3)
            for (int side = -1; side <= 1; side += 2) {
                put(f, 9 * side, F1 + 1, v, SLAB, 5);
                put(f, 9 * side, F1 + 1, v + 1, SLAB, 5);
                put(f, 10 * side, F1 + 1, v, FENCE, 0);
            }
        for (int v : new int[] {14, 24}) for (int side = -1; side <= 1; side += 2) hang(f, 4 * side, F2 - 1, v, 2);
        // the partition between the Eye Gallery and the Ossuary Gallery
        for (int u = -17; u <= 17; u++)
            for (int y = F2 + 1; y <= F3 - 1; y++) {
                if (Math.abs(u) <= 2 && y <= F2 + 5 && !(Math.abs(u) == 2 && y == F2 + 5)) continue;
                if (Math.abs(u) <= 3 && (y == F2 + 6 || Math.abs(u) == 3 && y <= F2 + 5)) { put(f, u, y, -4, BRICK, 3); continue; }
                if (cav(u, y, -4)) f.masonry(u, y, -4);
            }
        // the Eye Gallery: a shrine to the watchers
        put(f, 0, F2 + 1, -9, BRICK, 3);
        put(f, 0, F2 + 2, -9, SKULL, 1);
        lamp(f, -3, F3, -7);
        lamp(f, 3, F3, -7);
        // the Ossuary Gallery: pillars with skulls, a wall of skull shelves at the back, lamps
        for (int side = -1; side <= 1; side += 2)
            for (int v = 6; v <= 30; v += 8) { for (int y = F2 + 1; y <= F3 - 1; y++) put(f, 8 * side, y, v, BONE, 0); put(f, 8 * side, F2 + 2, v, SKULL, 1); }
        for (int u = -9; u <= 9; u++) if (Math.abs(u) > 1) { put(f, u, F2 + 1, 30, BONE, 0); put(f, u, F2 + 2, 30, SKULL, 1); put(f, u, F2 + 3, 30, BONE, 0); put(f, u, F2 + 4, 30, SKULL, 1); }
        for (int v : new int[] {12, 22}) for (int side = -1; side <= 1; side += 2) lamp(f, 5 * side, F3, v);
    }

    /** The Bone Throne at the back of the crown on its dais, the pillars, the red road, the lamps. */
    private static void throne(Frame f, int[] b) {
        if (!hit(b, -17, -10, 17, 34)) return;
        int y0 = F3 + 1;
        for (int v = 8; v <= 23; v++) for (int u = -1; u <= 1; u++) f.set(u, F3, v, BRICKS, 0);                          // the red road
        for (int u = -8; u <= 8; u++) for (int v = 25; v <= 33; v++) put(f, u, y0, v, BONE, 0);                          // the dais
        for (int u = -6; u <= 6; u++) for (int v = 28; v <= 33; v++) put(f, u, y0 + 1, v, BONE, 0);
        for (int u = -4; u <= 4; u++) for (int v = 31; v <= 33; v++) put(f, u, y0 + 2, v, BONE, 0);
        for (int u = -8; u <= 8; u++) put(f, u, y0, 24, BRICK_STAIRS, f.stairs(0, 1, false));
        for (int u = -6; u <= 6; u++) put(f, u, y0 + 1, 27, BRICK_STAIRS, f.stairs(0, 1, false));
        for (int u = -4; u <= 4; u++) put(f, u, y0 + 2, 30, BRICK_STAIRS, f.stairs(0, 1, false));
        put(f, 0, y0 + 3, 32, BRICK_STAIRS, f.stairs(0, 1, false));                                                    // the throne
        for (int y = y0 + 3; y <= y0 + 7; y++) put(f, 0, y, 33, BONE, 0);
        put(f, 0, y0 + 8, 33, SKULL, 1);
        for (int side = -1; side <= 1; side += 2) { put(f, 7 * side, y0 + 1, 25, BONE, 0); put(f, 7 * side, y0 + 2, 25, BONE, 0); put(f, 7 * side, y0 + 3, 25, GLOWSTONE, 0); }
        for (int side = -1; side <= 1; side += 2) { put(f, side, y0 + 3, 32, BONE, 4); put(f, side, y0 + 4, 32, SKULL, 1); for (int y = y0 + 3; y <= y0 + 6; y++) put(f, 2 * side, y, 33, BONE, 0); }
        for (int side = -1; side <= 1; side += 2)                                                                        // pillars to the dome
            for (int v = -4; v <= 28; v += 8) {
                int u = 9 * side;
                for (int y = y0; y <= 40; y++) { if (!cav(u, y, v)) break; f.set(u, y, v, BONE, 0); }
                put(f, u, y0, v, BRICK, 3);
            }
        for (int v : new int[] {-2, 12, 24}) {                                                                           // hanging lamps
            int top = 25;
            while (cav(0, top + 1, v)) top++;
            for (int y = top - 3; y <= top; y++) put(f, 0, y, v, FENCE, 0);
            put(f, 0, top - 4, v, GLOWSTONE, 0);
        }
    }

    /** An ossuary pit dug into the mound: walls of bone, heaps of bones and skulls, a ladder out. */
    private static void pit(Frame f, int[] b, int cu, int cv) {
        if (!hit(b, cu - 6, cv - 6, cu + 6, cv + 6)) return;
        for (int du = -6; du <= 6; du++)
            for (int dv = -6; dv <= 6; dv++) {
                double d = Math.sqrt(du * du + dv * dv);
                int u = cu + du, v = cv + dv, top = Math.max(0, mound(u, v));
                if (d > 5.6) continue;
                if (d > 4.5) { for (int y = -4; y <= top; y++) f.set(u, y, v, BONE, 0); if ((du + dv & 1) == 0) f.set(u, top + 1, v, SKULL, 1); else f.set(u, top + 1, v, AIR); continue; }
                f.set(u, -5, v, BONE, 0);
                f.clear(u, v, -4, top + 1);
                if (Math.abs(du) <= 1 && Math.abs(dv) <= 1) continue;
                double r = f.roll(u, 0, v, 611);
                int heap = r < 0.5 ? 0 : r < 0.8 ? 1 : 2;
                for (int y = -4; y < -4 + heap; y++) f.set(u, y, v, BONE, (int) (r * 3) * 4);
                if (r > 0.9) f.set(u, -4 + heap, v, SKULL, 1);
            }
        int top = Math.max(0, mound(cu, cv + 5));
        f.clear(cu, cv + 4, -4, top + 2);
        for (int y = -4; y <= top; y++) f.set(cu, y, cv + 4, LADDER, f.facing(0, -1));
        f.set(cu, top + 1, cv + 5, AIR);
        f.set(cu, top + 2, cv + 5, AIR);
    }

    /** The Ossuary Tunnel: from the third pit, level and then by a stair, to the west wall of the crypt. */
    private static void tunnel(Frame f, int[] b) {
        int cu = PITS[2][0], cv = PITS[2][1];
        if (!hit(b, cu + 5, cv - 3, -6, cv + 3)) return;
        for (int u = cu + 5; u <= -7; u++) {
            int yf = u <= -15 ? -5 : -5 - (u + 14);
            for (int dv = -2; dv <= 2; dv++) {
                int v = cv + dv;
                if (Math.abs(dv) == 2) { for (int y = Math.max(FLOOR, yf - 1); y <= yf + 4; y++) f.set(u, y, v, BONE, 0); continue; }
                for (int y = Math.max(FLOOR, yf - 6); y < yf; y++) f.set(u, y, v, (u + v & 1) == 0 ? BONE : STONE, (u + v & 1) == 0 ? 0 : 5);
                if (u <= -15) f.set(u, yf, v, (u + v & 1) == 0 ? BONE : STONE, (u + v & 1) == 0 ? 0 : 5);
                else f.set(u, yf, v, BRICK_STAIRS, f.stairs(-1, 0, false));
                f.clear(u, v, yf + 1, yf + 3);
                f.set(u, yf + 4, v, (u == -18 || u == -10) && dv == 0 ? GLOWSTONE : BONE, 0);
            }
        }
    }

    /** The Jaw Court: lamps on posts at the teeth gate, the impaled along the road. */
    private static void court(Frame f, int[] b) {
        if (!hit(b, -8, -38, 8, -20)) return;
        for (int side = -1; side <= 1; side += 2) {
            for (int v = -34; v <= -24; v += 5) { for (int y = 1; y <= 3; y++) f.set(4 * side, y, v, FENCE, 0); f.set(4 * side, 4, v, SKULL, 1); }
            for (int y = 1; y <= 3; y++) f.set(3 * side, y, -24, BONE, 0);
            f.set(3 * side, 4, -24, GLOWSTONE);
        }
    }

    private static void tiles(Frame f, int[] b, long h) {
        int lore = Hash.range(h, 0, 99);
        // the Tyrant's hoard on the dais
        f.chest(-3, F3 + 4, 32, 1, 0, "minecraft:chests/end_city_treasure", "lore:" + lore + ";trinket:0.6");
        f.chest(3, F3 + 4, 32, -1, 0, "minecraft:chests/woodland_mansion", "trinket:0.4");
        f.sign(0, F3 + 5, 32, 0, -1, "KNEEL\nBEFORE THE\nBONE TYRANT");
        // the Eye Gallery and the Ossuary Gallery
        f.chest(-6, F2 + 1, 26, 1, 0, Sites.LIBRARY, "lore:" + (lore + 7));
        f.chest(6, F2 + 1, 18, -1, 0, "minecraft:chests/stronghold_crossing", "trinket:0.25");
        f.chest(-7, F2 + 1, -9, 1, 0, Sites.JUNGLE, null);
        f.chest(7, F2 + 1, -9, -1, 0, Sites.DESERT, null);
        f.sign(4, F2 + 2, -5, 0, -1, "IT WATCHES\nEVERY ROAD");
        f.spawner(0, F2 + 1, 20, "STRAY");
        // the Barracks and the Nasal Gallery
        f.chest(-4, F1 + 1, 31, 0, -1, Sites.SMITH, null);
        f.chest(4, F1 + 1, 31, 0, -1, Sites.SMITH, null);
        f.chest(-8, F1 + 1, -8, 1, 0, Sites.CORRIDOR, null);
        f.chest(8, F1 + 1, -8, -1, 0, "minecraft:chests/igloo_chest", null);
        f.spawner(0, F1 + 1, 18, "ZOMBIE");
        // the Mouth Hall, the Hall of the Dead and the court
        f.chest(7, 1, -14, -1, 0, Sites.DUNGEON, null);
        f.chest(-7, 1, -7, 1, 0, Sites.CORRIDOR, null);
        f.chest(6, 1, 3, -1, 0, "minecraft:chests/abandoned_mineshaft", null);
        f.chest(-6, 1, 19, 1, 0, Sites.SMITH, "trinket:0.2");
        f.chest(-9, 1, -24, 1, 0, Sites.DESERT, null);
        f.spawner(0, 1, 26, "SKELETON");
        f.dispenser(0, 0, -12, 0, 0);
        f.set(0, 1, -12, PLATE, 0);
        f.dispenser(0, FLOOR, 26, 0, 0);
        f.set(0, FLOOR + 1, 26, PLATE, 0);
        f.sign(2, 2, 8, 0, -1, "THE CHARNEL\nSTAIR");
        f.set(2, 2, 9, BONE, 0);
        f.sign(7, 2, -4, 0, -1, "GOLGOTHA\nREMEMBERS\nEVERY SKULL");
        f.sign(3, 2, -36, 0, -1, "GOLGOTHA");
        // the crypt
        f.chest(5, FLOOR + 1, 28, -1, 0, Sites.DUNGEON, "lore:" + (lore + 31));
        f.chest(-5, FLOOR + 1, 34, 1, 0, Sites.LIBRARY, null);
        f.chest(0, FLOOR + 1, 40, 0, -1, "minecraft:chests/nether_bridge", "trinket:0.3");
        f.spawner(0, FLOOR + 1, 36, "SKELETON");
        f.sign(0, FLOOR + 3, 41, 0, -1, "HERE THE\nNAMELESS\nDEAD WAIT");
        f.sign(5, F1 + 2, -2, 0, -1, "THE TYRANT\nBUILT HIS KEEP\nIN THE SKULL\nOF A GOD");
        f.sign(PITS[0][0] + 1, -3, PITS[0][1] + 4, 0, -1, "THE OSSUARY\nHUNGERS");
        f.sign(PITS[2][0] - 1, -3, PITS[2][1] + 4, 0, -1, "ALL WHO FALL\nHERE STAY");
        // the pits (the heaps of bones give way to them)
        pitTile(f, PITS[0][0] + 2, PITS[0][1] + 2);
        pitTile(f, PITS[1][0] - 2, PITS[1][1] + 2);
        pitTile(f, PITS[2][0] + 2, PITS[2][1] - 2);
        pitTile(f, PITS[1][0] + 2, PITS[1][1] - 2);
        f.chest(PITS[0][0] + 2, -4, PITS[0][1] + 2, 0, -1, Sites.DUNGEON, null);
        f.chest(PITS[1][0] - 2, -4, PITS[1][1] + 2, 0, -1, "minecraft:chests/igloo_chest", null);
        f.chest(PITS[2][0] + 2, -4, PITS[2][1] - 2, 0, 1, "minecraft:chests/abandoned_mineshaft", null);
        f.spawner(PITS[1][0] + 2, -4, PITS[1][1] - 2, "ZOMBIE");
    }

    private static void pitTile(Frame f, int u, int v) { f.clear(u, v, -3, -2); }

    // ------------------------------------------------------------------ geometry

    private static int[] bounds(Frame f, Canvas c, int r) {
        int ax = c.x0 - f.ox, az = c.z0 - f.oz, bx = ax + 15, bz = az + 15, u0, u1, v0, v1;
        switch (f.rot) {
            case 1: u0 = az; u1 = bz; v0 = -bx; v1 = -ax; break;
            case 2: u0 = -bx; u1 = -ax; v0 = -bz; v1 = -az; break;
            case 3: u0 = -bz; u1 = -az; v0 = ax; v1 = bx; break;
            default: u0 = ax; u1 = bx; v0 = az; v1 = bz; break;
        }
        u0 = Math.max(u0, -r); u1 = Math.min(u1, r); v0 = Math.max(v0, -r); v1 = Math.min(v1, r);
        return u0 > u1 || v0 > v1 ? null : new int[] {u0, u1, v0, v1};
    }

    private static boolean hit(int[] b, int u0, int v0, int u1, int v1) { return u1 >= b[0] && u0 <= b[1] && v1 >= b[2] && v0 <= b[3]; }
}
