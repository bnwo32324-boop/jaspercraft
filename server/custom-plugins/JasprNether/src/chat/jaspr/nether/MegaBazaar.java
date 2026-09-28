package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Golden Bazaar (Hell): the Pigtificate capital on a walled mesa ringed by a lava moat. Four bridges and grand
 * stairs lead through gatehouses to avenues of market stalls and round Pigtificate huts in the NetherEx style; the
 * golden-domed palace holds a Respawner Statue and, beneath it, a treasury guarded by Gold Golems. An amethyst mine
 * sinks into the mesa, and nether-wart farms fill the cavern floor outside. It is the Nether's trading hub: natural
 * hostiles do not spawn inside its walls (see Mobs), and its loot is gold, amethyst and Pigtificate wares.
 */
final class MegaBazaar extends MegaDesign {
    static final int MESA = 44, TOP = 12;       // mesa radius and height above the floor

    static final class Hut { final int u, v, r, door; final boolean chest, tall; Hut(int u, int v, int r, int door, boolean chest, boolean tall) { this.u = u; this.v = v; this.r = r; this.door = door; this.chest = chest; this.tall = tall; } }
    static final class Stall { final int u, v, colour; Stall(int u, int v, int colour) { this.u = u; this.v = v; this.colour = colour; } }
    static final class Plan {
        int rot;
        final List<Hut> huts = new ArrayList<>();
        final List<Stall> stalls = new ArrayList<>();
        final List<int[]> farms = new ArrayList<>();       // u, v, r
        final List<int[]> outer = new ArrayList<>();       // outer farmhouses u, v
    }

    private static final int NB = b(112), RNB = b(215), NB_FENCE = b(113), NB_SLAB = b(44, 6), NB_SLAB_TOP = b(44, 14), GLOW = b(89);
    private static final int SMOOTH = b(24, 2), CHISELED = b(24, 1), GOLD = b(159, 4), ORANGE = b(159, 1), PATH = b(159, 14), RACK = b(87);
    private static final int GLASS = b(160, 4), STATUE_STONE = b(98, 3), SOUL = b(88), WART = b(115, 3), LAVA = b(11), MOSS = b(48);

    @Override Object plan(Mega.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        // stalls along the four avenues, just outside the palace plaza
        for (int side = 0; side < 4; side++) for (int k = 0; k < 2; k++) {
            int along = 21 + k * 7, off = (k & 1) == 0 ? 6 : -6;
            int[] uv = side == 0 ? new int[]{along, off} : side == 1 ? new int[]{-along, -off} : side == 2 ? new int[]{off, along} : new int[]{-off, -along};
            p.stalls.add(new Stall(uv[0], uv[1], new int[]{14, 4, 1, 11, 10, 13}[r.nextInt(6)]));
        }
        // huts on rings between the plaza and the wall, clear of the avenues, the stalls and the mine
        for (int ring = 0; ring < 3; ring++) {
            double rad = 21 + ring * 7.5;
            int n = (int) (2 * Math.PI * rad / 10.5);
            double phase = r.nextDouble();
            for (int i = 0; i < n; i++) {
                double a = (i + phase) / n * Math.PI * 2;
                int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad);
                int hr = 3 + (r.nextInt(3) == 0 ? 1 : 0);
                if (Math.abs(u) < hr + 5 || Math.abs(v) < hr + 5) continue;                 // avenues
                boolean nearStall = false;
                for (Stall st : p.stalls) if (Math.hypot(st.u - u, st.v - v) < hr + 5) nearStall = true;
                if (nearStall) continue;
                if (Math.hypot(u - 25, v + 25) < hr + 6) continue;                            // the mine
                if (Math.hypot(u, v) + hr > MESA - 5) continue;                               // inside the wall walk
                boolean clash = false;
                for (Hut h : p.huts) if (Math.hypot(h.u - u, h.v - v) < h.r + hr + 3) clash = true;
                if (clash) continue;
                int door = Math.abs(u) > Math.abs(v) ? (u > 0 ? 1 : 0) : (v > 0 ? 3 : 2);   // faces the nearer avenue side
                p.huts.add(new Hut(u, v, hr, door, r.nextInt(5) < 3, hr == 4 && r.nextBoolean()));
            }
        }
        for (int i = 0; i < 7; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 66 + r.nextDouble() * 18;
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            if (Math.min(Math.abs(u), Math.abs(v)) < 9) continue;
            p.farms.add(new int[]{u, v, 4 + r.nextInt(4)});
        }
        for (int i = 0; i < 3; i++) {
            double a = (i + 0.5) / 3 * Math.PI * 2 + r.nextDouble(), d = 72;
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            if (Math.min(Math.abs(u), Math.abs(v)) < 10) continue;
            p.outer.add(new int[]{u, v});
        }
        return p;
    }

    @Override void draw(Mega.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y, yT = y0 + TOP;
        mesa(f, s, y0, yT);
        moatAndBridges(f, s, y0, yT);
        wall(f, s, yT);
        streets(f, s, yT);
        for (Stall st : p.stalls) stall(f, st, yT);
        for (int i = 0; i < p.huts.size(); i++) hut(f, p.huts.get(i), yT, i);
        palace(f, s, yT);
        mine(f, s, y0, yT);
        for (int[] fm : p.farms) farm(f, s, fm);
        for (int[] o : p.outer) {
            int g = s.floorAt(f.x(o[0], o[1]), f.z(o[0], o[1]));
            hut(f, new Hut(o[0], o[1], 3, 0, true, false), g, 100 + o[0]);
        }
    }

    /** The mesa: a netherrack drum faced with red nether brick, banded and buttressed, paved on top. */
    private void mesa(Draw.Frame f, Mega.Site s, int y0, int yT) {
        Draw d = f.d;
        d.tube(s.x, s.z, MESA, MESA, 0, y0 - 6, yT - 1, Draw.of(RACK));
        d.tube(s.x, s.z, MESA, MESA, 2, y0 - 2, yT - 1, Draw.bands(Draw.mix(RNB, NB, 0.1, s.salt + 3), Draw.of(NB), y0, 4));
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            double bu = Math.cos(a) * (MESA + 1), bv = Math.sin(a) * (MESA + 1);
            if (Math.min(Math.abs(bu), Math.abs(bv)) < 5) continue;
            f.line(bu, y0 - 2, bv, bu, yT - 2, bv, 1.1, Draw.of(NB));
            f.set((int) Math.round(bu), yT - 1, (int) Math.round(bv), GLOW);
        }
        // pavement: gold and orange courses in rings around the palace
        Draw.Mat pave = (x, y, z) -> {
            double r = Math.hypot(x - s.x, z - s.z);
            if (r < 17) return ((int) r & 1) == 0 ? GOLD : SMOOTH;
            return Draw.rnd(x, y, z, s.salt + 8) < 0.08 ? ORANGE : PATH;
        };
        d.tube(s.x, s.z, MESA, MESA, 0, yT, yT, pave);
        d.tube(s.x, s.z, MESA - 0.5, MESA - 0.5, 0, yT + 1, yT + 30, Draw.AIR);
    }

    /** The lava moat, the walk around the mesa's foot, and four bridges with grand stairs down to the floor. */
    private void moatAndBridges(Draw.Frame f, Mega.Site s, int y0, int yT) {
        Draw d = f.d;
        d.tube(s.x, s.z, 60, 60, 0, y0 - 3, y0, Draw.of(RNB));
        d.tube(s.x, s.z, 59, 59, 9, y0 - 1, y0, Draw.of(LAVA));
        d.tube(s.x, s.z, 50, 50, 0, y0 - 3, y0, Draw.of(NB));
        d.tube(s.x, s.z, 61, 61, 1, y0 + 1, y0 + 1, Draw.of(NB_FENCE));
        d.tube(s.x, s.z, 50, 50, 1, y0 + 1, y0 + 1, Draw.of(NB_FENCE));
        for (int side = 0; side < 4; side++) {
            Draw.Frame g = new Draw.Frame(d, s.x, s.z, f.rot + side);
            // deck from the gate to beyond the moat, on two arches
            g.box(MESA - 2, yT, -2, 63, yT, 2, Draw.of(NB));
            g.box(MESA - 2, yT + 1, -3, 63, yT + 1, -3, Draw.of(NB_FENCE));
            g.box(MESA - 2, yT + 1, 3, 63, yT + 1, 3, Draw.of(NB_FENCE));
            g.box(MESA - 2, yT + 1, -2, 63, yT + 5, 2, Draw.AIR);
            g.box(63, y0 - 2, -2, 64, yT - 1, 2, Draw.of(RNB));
            for (int w = -2; w <= 2; w++) d.arch(g.x(45, w), g.z(45, w), g.x(63, w), g.z(63, w), y0 + 1, TOP - 2, 0.5, 2, Draw.of(RNB));
            for (int t = 50; t <= 62; t += 6) { g.set(t, yT + 2, -3, GLOW); g.set(t, yT + 2, 3, GLOW); }
            // grand stairs down to the cavern floor
            for (int k = 0; k < TOP; k++) {
                int u = 64 + k, h = yT - k;
                g.box(u, y0 - 3, -3, u, h - 1, 3, Draw.of(NB));
                for (int w = -2; w <= 2; w++) g.set(u, h, w, g.stair(114, 1, false));
                g.set(u, h, -3, RNB); g.set(u, h, 3, RNB);
                g.set(u, h + 1, -3, NB_FENCE); g.set(u, h + 1, 3, NB_FENCE);
                g.box(u, h + 1, -2, u, h + 4, 2, Draw.AIR);
            }
            g.resident(56, yT + 1, 0, "netherex:gold_golem");
        }
    }

    /** The city wall on the mesa's rim: crenellated, with a wall walk, gatehouses and eight towers. */
    private void wall(Draw.Frame f, Mega.Site s, int yT) {
        Draw d = f.d;
        Draw.Mat stone = Draw.mix(RNB, NB, 0.12, s.salt + 11);
        d.tube(s.x, s.z, MESA, MESA, 3, yT + 1, yT + 8, stone);
        d.tube(s.x, s.z, MESA, MESA, 1, yT + 9, yT + 9, (x, y, z) -> ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0 ? RNB : 0);
        d.tube(s.x, s.z, MESA - 3, MESA - 3, 1, yT + 9, yT + 9, Draw.of(NB_FENCE));
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2 + Math.PI / 8;
            double tu = Math.cos(a) * (MESA - 1), tv = Math.sin(a) * (MESA - 1);
            int tx = f.x((int) Math.round(tu), (int) Math.round(tv)), tz = f.z((int) Math.round(tu), (int) Math.round(tv));
            d.cyl(tx, tz, 4, yT + 1, yT + 15, stone);
            d.cyl(tx, tz, 2.6, yT + 1, yT + 14, Draw.AIR);
            d.ring(tx, tz, 4.4, 1, yT + 16, yT + 16, (x, y, z) -> ((x + z) & 1) == 0 ? RNB : 0);
            d.disk(tx, tz, 4, yT + 15, Draw.of(NB));
            d.set(tx, yT + 16, tz, GLOW);
            d.set(tx, yT + 1, tz, GLOW);
        }
        for (int side = 0; side < 4; side++) {
            Draw.Frame g = new Draw.Frame(d, s.x, s.z, f.rot + side);
            // gatehouse: two square towers and an arched opening
            for (int w = -1; w <= 1; w += 2) {
                g.box(MESA - 5, yT + 1, w * 4, MESA + 1, yT + 13, w * 7, stone);
                g.box(MESA - 4, yT + 14, w * 4, MESA, yT + 14, w * 7, (x, y, z) -> ((x + z) & 1) == 0 ? RNB : 0);
                g.set(MESA - 2, yT + 13, w * 5, GLOW);
            }
            g.box(MESA - 5, yT + 9, -3, MESA + 1, yT + 12, 3, stone);
            g.box(MESA - 5, yT + 1, -2, MESA + 1, yT + 6, 2, Draw.AIR);
            g.box(MESA - 5, yT + 7, -1, MESA + 1, yT + 7, 1, Draw.AIR);
            g.box(MESA + 1, yT + 7, -2, MESA + 1, yT + 7, 2, Draw.of(GOLD));
            g.resident(MESA - 7, yT + 1, 4, "netherex:pigtificate");
        }
    }

    /** Avenues from the gates to the palace, lamp posts along them. */
    private void streets(Draw.Frame f, Mega.Site s, int yT) {
        for (int side = 0; side < 4; side++) {
            Draw.Frame g = new Draw.Frame(f.d, s.x, s.z, f.rot + side);
            g.box(12, yT, -2, MESA - 1, yT, 2, Draw.of(SMOOTH));
            g.box(12, yT, -3, MESA - 1, yT, -3, Draw.of(GOLD));
            g.box(12, yT, 3, MESA - 1, yT, 3, Draw.of(GOLD));
            for (int t = 16; t <= 40; t += 6) for (int w = -4; w <= 4; w += 8) {
                g.box(t, yT + 1, w, t, yT + 3, w, Draw.of(NB_FENCE));
                g.set(t, yT + 4, w, GLOW);
            }
        }
    }

    /** A market stall: fence posts, a striped canopy, a counter and a trader behind it. */
    private void stall(Draw.Frame f, Stall st, int yT) {
        int u = st.u, v = st.v;
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) f.box(u + cu * 2, yT + 1, v + cv * 2, u + cu * 2, yT + 3, v + cv * 2, Draw.of(NB_FENCE));
        f.box(u - 2, yT + 4, v - 2, u + 2, yT + 4, v + 2, (x, y, z) -> ((x + z) & 1) == 0 ? b(159, st.colour) : b(159, 0));
        f.set(u, yT + 5, v, b(159, st.colour));
        f.box(u - 1, yT + 1, v - 1, u + 1, yT + 1, v + 1, Draw.of(NB_SLAB_TOP));
        f.set(u, yT + 1, v, 0);
        f.resident(u, yT + 1, v, "netherex:pigtificate");
    }

    /** A round Pigtificate hut in the NetherEx village style: red-brick drum, brick dome, glowstone skylight. */
    private void hut(Draw.Frame f, Hut h, int base, int idx) {
        Draw d = f.d;
        int x = f.x(h.u, h.v), z = f.z(h.u, h.v), wallTop = base + (h.tall ? 7 : 3);
        d.disk(x, z, h.r, base, Draw.of(PATH));
        d.tube(x, z, h.r, h.r, 1, base + 1, wallTop, Draw.of(RNB));
        d.tube(x, z, h.r - 1, h.r - 1, 0, base + 1, wallTop, Draw.AIR);
        if (h.tall) {
            d.disk(x, z, h.r - 1, base + 4, Draw.of(NB));
            d.box(x, base + 1, z + 1, x, base + 3, z + 1, Draw.of(NB));
            d.box(x, base + 1, z, x, base + 4, z, b(65, Draw.NORTH));
        }
        d.disk(x, z, h.r + 1, wallTop + 1, Draw.of(NB_SLAB));
        d.disk(x, z, h.r, wallTop + 1, Draw.of(NB));
        d.disk(x, z, h.r - 1, wallTop + 2, Draw.of(NB_SLAB));
        d.set(x, wallTop + 1, z, GLOW);
        d.set(x, wallTop + 2, z, NB_SLAB);
        // windows on the four sides, the door towards the avenue
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dd : dirs) d.set(x + dd[0] * h.r, base + 2, z + dd[1] * h.r, NB_FENCE);
        int[] door = dirs[f.dir(h.door)];
        d.set(x + door[0] * h.r, base + 1, z + door[1] * h.r, 0);
        d.set(x + door[0] * h.r, base + 2, z + door[1] * h.r, 0);
        d.set(x + door[0] * (h.r + 1), base, z + door[1] * (h.r + 1), PATH);
        // furnishings opposite the door
        int bx = x - door[0] * (h.r - 1), bz = z - door[1] * (h.r - 1);
        int facing = door[0] > 0 ? Draw.EAST : door[0] < 0 ? Draw.WEST : door[1] > 0 ? Draw.SOUTH : Draw.NORTH;
        if (h.chest) d.chest(bx, base + 1, bz, facing, "jaspr:mega/bazaar");
        else d.set(bx, base + 1, bz, b(58));
        int sx = x - door[1] * (h.r - 1), sz = z - door[0] * (h.r - 1);
        d.set(sx, base + 1, sz, b(61, facing));
        if ((idx % 3) == 0) d.resident(x + door[0], base + 1, z + door[1], "netherex:pigtificate");
    }

    /** The palace: a sandstone hall under a golden dome, the Respawner Statue, a throne, and the treasury below. */
    private void palace(Draw.Frame f, Mega.Site s, int yT) {
        Draw d = f.d;
        int H = 12;
        f.box(-14, yT, -14, 14, yT, 14, Draw.of(SMOOTH));
        f.box(-11, yT + 1, -11, 11, yT + H, 11, (x, y, z) -> {
            int lu = Math.abs(x - s.x), lv = Math.abs(z - s.z);
            if (y == yT + 1) return RNB;
            if (y == yT + 6 || y == yT + H) return ORANGE;
            if ((lu == 11 && lv % 4 == 3) || (lv == 11 && lu % 4 == 3) || (lu == 11 && lv == 11)) return GOLD;
            return SMOOTH;
        });
        f.box(-10, yT + 1, -10, 10, yT + H - 1, 10, Draw.AIR);
        f.walls(-12, yT + H + 1, -12, 12, yT + H + 1, 12, (x, y, z) -> ((x + z) & 1) == 0 ? CHISELED : 0);
        f.box(-11, yT + H, -11, 11, yT + H, 11, Draw.of(SMOOTH));
        d.disk(s.x, s.z, 4, yT + H, Draw.AIR);
        // tall windows between the pilasters, a door in each side
        for (int side = 0; side < 4; side++) {
            Draw.Frame g = new Draw.Frame(d, s.x, s.z, f.rot + side);
            for (int w : new int[]{-9, -5, 4, 8}) g.box(11, yT + 7, w, 11, yT + 10, w + 1, Draw.of(GLASS));
            g.box(11, yT + 1, -1, 11, yT + 4, 1, Draw.AIR);
            g.box(11, yT + 5, -2, 11, yT + 5, 2, Draw.of(GOLD));
            g.box(12, yT, -2, 15, yT, 2, Draw.of(GOLD));
            // mezzanine
            g.box(8, yT + 6, -10, 10, yT + 6, 10, Draw.of(SMOOTH));
            g.box(7, yT + 7, -9, 7, yT + 7, 9, Draw.of(NB_FENCE));
        }
        for (int[] c : new int[][]{{9, 9}, {-9, 9}, {9, -9}, {-9, -9}}) f.box(c[0], yT + 1, c[1], c[0], yT + 11, c[1], Draw.of(GOLD));
        // the golden dome with glowstone ribs, an oculus above the hall, a lantern spire
        d.ellipsoid(s.x, yT + H, s.z, 9, 11, 9, 1.2, (x, y, z) -> {
            if (y <= yT + H) return -1;
            double a = Math.atan2(z - s.z, x - s.x) / (Math.PI / 4);
            return Math.abs(a - Math.round(a)) < 0.09 ? GLOW : GOLD;
        });
        d.box(s.x - 1, yT + H + 11, s.z - 1, s.x + 1, yT + H + 13, s.z + 1, Draw.of(b(95, 4)));
        d.set(s.x, yT + H + 12, s.z, GLOW);
        d.box(s.x, yT + H + 14, s.z, s.x, yT + H + 19, s.z, Draw.of(GOLD));
        d.set(s.x, yT + H + 20, s.z, GLOW);
        // the Respawner Statue on a golden plinth under the oculus
        f.box(-1, yT + 1, -1, 1, yT + 1, 1, Draw.of(GOLD));
        f.set(0, yT + 2, 0, STATUE_STONE);
        f.point(0, yT + 2, 0, "statue");
        // the throne at the north end
        f.box(-3, yT + 1, -10, 3, yT + 1, -7, Draw.of(GOLD));
        f.box(-2, yT + 2, -10, 2, yT + 2, -8, Draw.of(SMOOTH));
        f.set(0, yT + 3, -8, f.stair(128, 3, false));
        f.box(-1, yT + 3, -10, 1, yT + 5, -10, Draw.of(GOLD));
        f.set(0, yT + 6, -10, GLOW);
        f.chest(-8, yT + 1, -9, Draw.SOUTH, "jaspr:mega/bazaar");
        f.chest(8, yT + 1, -9, Draw.SOUTH, "jaspr:mega/bazaar");
        f.resident(4, yT + 1, 4, "netherex:pigtificate");
        f.resident(-4, yT + 1, 4, "netherex:pigtificate");
        // the treasury: a stair down on the east side, a golden vault inside the mesa
        for (int k = 0; k < 12; k++) {
            f.box(6, yT - k, 1 + k, 8, yT - k + 4, 1 + k, Draw.AIR);
            for (int w = 6; w <= 8; w++) f.set(w, yT - k - 1, 1 + k, f.stair(114, 3, false));
        }
        f.walls(5, yT + 1, 0, 9, yT + 1, 9, Draw.of(NB_FENCE));
        f.box(6, yT + 1, 1, 8, yT + 1, 8, Draw.AIR);
        f.box(6, yT + 1, 0, 8, yT + 1, 0, Draw.AIR);
        f.room(-7, yT - 13, 2, 9, yT - 7, 16, Draw.of(RNB));
        f.box(-6, yT - 13, 3, 5, yT - 13, 15, (x, y, z) -> ((x + z) & 1) == 0 ? GOLD : ORANGE);
        for (int k = 5; k < 12; k++) f.box(6, yT - k, 1 + k, 8, yT - k + 4, 1 + k, Draw.AIR);
        for (int k = 5; k < 12; k++) for (int w = 6; w <= 8; w++) f.set(w, yT - k - 1, 1 + k, f.stair(114, 3, false));
        for (int[] c : new int[][]{{-6, 3}, {-6, 15}, {8, 15}}) { f.box(c[0], yT - 12, c[1], c[0], yT - 8, c[1], Draw.of(GOLD)); f.set(c[0], yT - 9, c[1], GLOW); }
        f.chest(-6, yT - 12, 9, Draw.EAST, "jaspr:mega/bazaar_vault");
        f.chest(0, yT - 12, 15, Draw.NORTH, "jaspr:mega/bazaar_vault");
        f.resident(1, yT - 12, 9, "netherex:gold_golem");
        f.resident(-2, yT - 12, 12, "netherex:gold_golem");
    }

    /** The amethyst mine: a laddered shaft down through the mesa to a gallery lined with ore. */
    private void mine(Draw.Frame f, Mega.Site s, int y0, int yT) {
        int u = 25, v = -25, bottom = y0 + 1;
        f.box(u - 3, yT + 1, v - 3, u + 3, yT + 1, v + 3, Draw.of(NB_FENCE));
        f.box(u - 2, yT + 1, v - 2, u + 2, yT + 1, v + 2, Draw.AIR);
        f.set(u, yT + 1, v - 3, 0);
        f.box(u - 2, yT + 4, v - 2, u + 2, yT + 4, v + 2, Draw.of(NB));
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) f.box(u + c[0], yT + 1, v + c[1], u + c[0], yT + 3, v + c[1], Draw.of(NB_FENCE));
        f.set(u, yT + 5, v, GLOW);
        f.box(u - 1, bottom, v - 1, u + 1, yT, v + 1, Draw.AIR);
        for (int y = bottom; y <= yT; y++) f.set(u, y, v - 1, b(65, f.facing(Draw.SOUTH)));
        f.box(u, bottom, v - 2, u, yT, v - 2, Draw.of(NB));
        int ore = Blocks.AMETHYST_ORE;
        Draw.Mat rock = (x, y, z) -> Draw.rnd(x, y, z, s.salt + 77) < 0.12 ? ore : RACK;
        // galleries inwards and sideways
        for (int[] g : new int[][]{{-1, 0, 14}, {0, 1, 12}}) {
            int pu = g[1] != 0 ? 1 : 0, pv = g[0] != 0 ? 1 : 0;   // across the gallery
            for (int t = 1; t <= g[2]; t++) {
                int gu = u + g[0] * t, gv = v + g[1] * t;
                f.box(gu - 2 * pu, bottom - 1, gv - 2 * pv, gu + 2 * pu, bottom + 4, gv + 2 * pv, rock);
                f.box(gu - pu, bottom, gv - pv, gu + pu, bottom + 2, gv + pv, Draw.AIR);
                if (t % 5 == 0) f.set(gu, bottom + 3, gv, GLOW);
            }
        }
        f.point(u - 10, bottom, v, "garrison:nethermite+nethermite");
    }

    /** A nether-wart farm on the cavern floor: soul-sand beds, a brick curb, a lamp. */
    private void farm(Draw.Frame f, Mega.Site s, int[] fm) {
        Draw d = f.d;
        int x = f.x(fm[0], fm[1]), z = f.z(fm[0], fm[1]);
        int g = s.floorAt(x, z);
        d.box(x - fm[2] - 1, g, z - fm[2] - 1, x + fm[2] + 1, g, z + fm[2] + 1, Draw.of(NB));
        d.box(x - fm[2], g, z - fm[2], x + fm[2], g, z + fm[2], (xx, y, zz) -> ((xx - x) % 3 == 0) ? PATH : SOUL);
        d.box(x - fm[2], g + 1, z - fm[2], x + fm[2], g + 3, z + fm[2], Draw.AIR);
        d.box(x - fm[2], g + 1, z - fm[2], x + fm[2], g + 1, z + fm[2], (xx, y, zz) -> ((xx - x) % 3 == 0) ? 0 : WART);
        d.box(x - fm[2] - 1, g + 1, z - fm[2] - 1, x - fm[2] - 1, g + 3, z - fm[2] - 1, Draw.of(NB_FENCE));
        d.set(x - fm[2] - 1, g + 4, z - fm[2] - 1, GLOW);
        for (int xx = x - fm[2] - 1; xx <= x + fm[2] + 1; xx++) for (int zz = z - fm[2] - 1; zz <= z + fm[2] + 1; zz++) d.foundation(xx, g - 1, zz, 6, Draw.of(RACK));
    }

    @Override int ground(Mega.Site s, int x, int y, int z) {
        double n = Draw.fbm(x, z, 14, s.salt + 90);
        return n > 0.25 ? MOSS : RACK;
    }

    @Override Draw.Mat under() { return Draw.of(RACK); }

    @Override boolean dry(Mega.Site s, int x, int z) { return s.dist(x, z) < 64; }

    @Override void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        glowHang(d, x, z, ceil, s.salt + 51, 0.012);
    }
}
