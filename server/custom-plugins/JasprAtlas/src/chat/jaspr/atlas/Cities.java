package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Concord's cities. Each is walled (towers every 25 blocks, gates where roads arrive), laid out on a Hippodamian grid
 * of 18-block blocks between 4-block streets, with its landmarks on raised civic terraces. Astreion climbs in three
 * tiers: the outer city, the civic terrace (agora, library, Synedrion, Lyceum, Stoa of Shields, House of Return,
 * theatre, baths) and the acropolis with the Hearth of Theano.
 */
final class Cities {
    private Cities() {}

    static final int PERIOD = 22, BLOCK = 18;

    /** A city's shape: its half-size, its gates (local points on the wall), and its terraces (half-size, rise). */
    static final class Plan {
        final int half;
        final int[][] gates;
        final int[][] tiers;   // {half, rise above base} from outer to inner
        Plan(int half, int[][] gates, int[][] tiers) { this.half = half; this.gates = gates; this.tiers = tiers; }
    }

    static Plan plan(Realm.Place p) {
        switch (p) {
            case ASTREION: return new Plan(100, new int[][] {{-100, 0}, {100, 0}, {40, -100}, {-60, -100}, {40, 100}}, new int[][] {{56, 3}, {24, 11}});
            case LAMPSA: return new Plan(70, new int[][] {{-28, 70}, {70, 0}}, new int[][] {{22, 3}});
            case HIERANTHE: return new Plan(70, new int[][] {{-28, -70}, {70, 0}}, new int[][] {{22, 3}});
            default: return new Plan(64, new int[][] {{28, 64}}, new int[][] {{20, 3}});   // Mnemeia
        }
    }

    /** The floor level at a local point (terraces raise it). */
    static int level(Plan plan, int base, int x, int z) {
        int y = base;
        for (int[] t : plan.tiers) if (Math.abs(x) <= t[0] && Math.abs(z) <= t[0]) y = base + t[1];
        return y;
    }

    static void draw(Plans plans, Frame f, Realm.Place p) {
        Plan plan = plan(p);
        long h = Hash.of(plans.seed ^ 0xC17L, p.x, p.z);
        ground(f, plan);
        streets(f, plan, p);
        walls(f, plan, p);
        terraces(f, plan);
        blocks(f, plan, p, h);
        Landmarks.draw(plans, f, p, plan);
    }

    // ------------------------------------------------------------------ ground, streets, walls, terraces

    private static void ground(Frame f, Plan plan) {
        int r = plan.half;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (!f.inside(x, z)) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z), y = level(plan, f.base, x, z);
                for (int yy = Math.min(g, f.base) - 3; yy < y; yy++) f.c.set(wx, yy, wz, yy >= f.base ? STONE : DIRT, yy >= f.base ? 5 : 0);
                f.c.clear(wx, wz, y + 1, Math.max(g, y) + 6);
                f.c.set(wx, y, wz, GRASS);
            }
    }

    /** Streets: the grid (every 22 blocks, 4 wide) and the avenues to the gates (7 wide). */
    private static void streets(Frame f, Plan plan, Realm.Place p) {
        int r = plan.half - 3;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (!f.inside(x, z)) continue;
                boolean grid = Math.floorMod(x + 11, PERIOD) < 4 || Math.floorMod(z + 11, PERIOD) < 4;
                boolean avenue = Math.abs(z) <= 3 || Math.abs(x) <= 3;
                for (int[] g : plan.gates) {
                    if (g[0] == -plan.half || g[0] == plan.half) { if (Math.abs(z - g[1]) <= 3) avenue = true; }
                    else if (Math.abs(x - g[0]) <= 3 && Math.signum(z) == Math.signum(g[1])) avenue = true;
                }
                if (!grid && !avenue) continue;
                int y = level(plan, f.base, x, z);
                f.pave(x, y - f.base, z);
                if (avenue && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0) f.set(x, y - f.base, z, GLAZED_CYAN, Math.floorMod(x + z, 4));
            }
    }

    private static void walls(Frame f, Plan plan, Realm.Place p) {
        int r = plan.half, top = 13;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < r - 2 || !f.inside(x, z)) continue;
                boolean gate = false;
                for (int[] g : plan.gates) if (Math.abs(x - g[0]) <= 3 && Math.abs(z - g[1]) <= 3) gate = true;
                for (int y = -3; y <= top; y++) {
                    if (gate && y >= 1 && y <= 7) { f.set(x, y, z, AIR); continue; }
                    if (d == r || d == r - 2) f.marble(x, y, z); else f.ashlar(x, y, z);
                }
                if (gate) { f.pave(x, 0, z); f.set(x, 8, z, QUARTZ, 1); }
                // Crenellations and the wall-walk lamps.
                if (d == r && Math.floorMod(x + z, 2) == 0) { f.set(x, top + 1, z, QUARTZ, 2); f.set(x, top + 2, z, SLAB, 7); }
                if (d == r - 1 && Math.floorMod(x + z, 20) == 0) f.set(x, top + 1, z, SEA_LANTERN);
            }
        // Towers every 25 blocks along the walls and at the corners.
        for (int k = -r; k <= r; k += 25)
            for (int[] t : new int[][] {{k, -r}, {k, r}, {-r, k}, {r, k}}) {
                boolean atGate = false;
                for (int[] g : plan.gates) if (Math.abs(t[0] - g[0]) <= 6 && Math.abs(t[1] - g[1]) <= 6) atGate = true;
                tower(f, t[0], t[1], top, atGate);
            }
        for (int[] c : new int[][] {{-r, -r}, {r, -r}, {-r, r}, {r, r}}) tower(f, c[0], c[1], top, false);
        // Gatehouses: flanking towers with lamps, a lintel with the city's name.
        for (int[] g : plan.gates) {
            boolean ew = Math.abs(g[0]) == r;
            int ox = ew ? 0 : 5, oz = ew ? 5 : 0;
            tower(f, g[0] + ox, g[1] + oz, top + 4, true);
            tower(f, g[0] - ox, g[1] - oz, top + 4, true);
            int sx = g[0] - (ew ? (int) Math.signum(g[0]) * 3 : 0), sz = g[1] - (ew ? 0 : (int) Math.signum(g[1]) * 3);
            if (f.inside(sx, sz)) f.c.sign(f.wx(sx, sz), f.base + 9, f.wz(sx, sz), ew ? (g[0] < 0 ? 4 : 5) : (g[1] < 0 ? 2 : 3), gateSign(p));
        }
    }

    private static void tower(Frame f, int tx, int tz, int top, boolean gate) {
        int r = 3;
        for (int x = tx - r; x <= tx + r; x++)
            for (int z = tz - r; z <= tz + r; z++) {
                if (!f.inside(x, z)) continue;
                boolean shell = Math.abs(x - tx) == r || Math.abs(z - tz) == r;
                for (int y = -3; y <= top + 5; y++) {
                    if (shell) {
                        if (y > 1 && y % 6 == 3 && (x == tx || z == tz)) f.set(x, y, z, STAINED_PANE, LIGHT_BLUE);
                        else f.marble(x, y, z);
                    } else if (y <= 0 || y == top + 5 || y % 6 == 0) f.set(x, y, z, DOUBLE_SLAB, 8);
                    else f.set(x, y, z, AIR);
                }
                if (shell && Math.floorMod(x + z, 2) == 0) f.set(x, top + 6, z, QUARTZ, 2);
            }
        if (f.inside(tx, tz)) { f.set(tx, top + 6, tz, SEA_LANTERN); f.set(tx, top + 7, tz, END_ROD, 1); }
        if (gate && f.inside(tx + 1, tz + 1)) f.npc(tx + 1, 1, tz + 1, 0, 1, "talos", null);
    }

    static String gateSign(Realm.Place p) {
        switch (p) {
            case ASTREION: return "ASTREION\nTHE WHITE CITY\nOF THE\nCONCORD";
            case LAMPSA: return "LAMPSA\nOF THE LAMPS\nHANDS THAT\nSPARE HANDS";
            case HIERANTHE: return "HIERANTHE\nOF THE GARDENS\nENTER AND\nBE WELL";
            default: return "MNEMEIA\nTHE CITY OF\nMEMORY\nNOTHING LOST";
        }
    }

    /** Retaining walls and stairs for the raised terraces. */
    private static void terraces(Frame f, Plan plan) {
        for (int[] t : plan.tiers) {
            int r = t[0], rise = t[1], below = level(plan, f.base, r + 1, 0) - f.base;
            for (int x = -r - 1; x <= r + 1; x++)
                for (int z = -r - 1; z <= r + 1; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d < r - 1 || !f.inside(x, z)) continue;
                    boolean stair = Math.abs(x) <= 3 || Math.abs(z) <= 3;
                    if (d == r + 1) {
                        // A balustrade below the wall's foot, broken by the stairs.
                        if (stair) continue;
                        f.set(x, below + 1, z, f.roll(x, 0, z, 131) < 0.8 ? AIR : AIR);
                        continue;
                    }
                    if (stair) {
                        // Grand stairs climbing the retaining wall.
                        int step = r - d;   // 0 at the wall's outer face
                        int y = below + Math.min(rise - below, 1 + (int) ((r + 1 - d) * (rise - below) / 3.0));
                        f.set(x, y, z, QUARTZ_STAIRS, stairsInward(f, x, z));
                        for (int yy = below; yy < y; yy++) f.ashlar(x, yy, z);
                        continue;
                    }
                    for (int y = below; y <= rise; y++) { if (d == r) f.marble(x, y, z); else f.ashlar(x, y, z); }
                    if (d == r && Math.floorMod(x + z, 2) == 0) f.set(x, rise + 1, z, QUARTZ, 2);
                    if (d == r) f.set(x, rise + 2, z, SLAB, 7);
                }
        }
    }

    private static int stairsInward(Frame f, int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return f.stairs(x > 0 ? -1 : 1, 0, false);
        return f.stairs(0, z > 0 ? -1 : 1, false);
    }

    // ------------------------------------------------------------------ the blocks (insulae)

    /** Fills every grid block outside the landmarks' reserved ground. */
    private static void blocks(Frame f, Plan plan, Realm.Place p, long h) {
        int r = plan.half - 4;
        int n0 = Math.floorDiv(-r + 11, PERIOD), n1 = Math.floorDiv(r + 11, PERIOD);
        for (int i = n0; i <= n1; i++)
            for (int j = n0; j <= n1; j++) {
                int cx = i * PERIOD + 1, cz = j * PERIOD + 1;   // blocks span 15..32 between the streets at 11..14 and 33..36
                if (Math.abs(cx) + 9 > r || Math.abs(cz) + 9 > r) continue;
                if (Landmarks.reserved(p, plan, cx, cz)) continue;
                if (Math.abs(cz) <= 12 || Math.abs(cx) <= 12) continue;   // the avenues' frontage stays open
                boolean onAvenue = false;
                for (int[] g : plan.gates) {
                    if (Math.abs(g[0]) == plan.half) { if (Math.abs(cz - g[1]) <= 12) onAvenue = true; }
                    else if (Math.abs(cx - g[0]) <= 12 && Math.signum(cz) == Math.signum(g[1])) onAvenue = true;
                }
                if (onAvenue) continue;
                Frame b = new Frame(f.c, f.wx(cx, cz), f.wz(cx, cz), level(plan, f.base, cx, cz), faceStreet(f, cx, cz));
                if (!b.touches(-10, -10, 10, 10)) continue;
                long bh = Hash.of(h, i, j);
                insula(b, bh, p, Math.max(Math.abs(cx), Math.abs(cz)) > plan.half - 30);
            }
    }

    /** Rotation that turns a block's front toward the nearer avenue. */
    private static int faceStreet(Frame f, int cx, int cz) {
        int rel;
        if (Math.abs(cx) < Math.abs(cz)) rel = cz > 0 ? 0 : 2; else rel = cx > 0 ? 3 : 1;
        return (f.rot + rel) & 3;
    }

    /** One city block: a house, a pair of townhouses, a shop row, a garden, a workshop, a fountain court or a school. */
    private static void insula(Frame b, long h, Realm.Place p, boolean outer) {
        double r = Hash.unit(h);
        String trade = Houses.trade(h ^ 3);
        if (p == Realm.Place.LAMPSA && r < 0.25) trade = r < 0.12 ? "lumenwright" : "smith";
        if (p == Realm.Place.MNEMEIA && r < 0.25) trade = "scribe";
        if (p == Realm.Place.HIERANTHE && r < 0.2) trade = "apothecary";
        if (r < 0.34) Houses.courtyardHouse(b, h, 8, 8);
        else if (r < 0.52) {
            Frame a = new Frame(b.c, b.wx(-4, 0), b.wz(-4, 0), b.base, b.rot);
            Frame c = new Frame(b.c, b.wx(5, 0), b.wz(5, 0), b.base, b.rot);
            Houses.townhouse(a, h, trade);
            Houses.townhouse(c, h ^ 7, Houses.trade(h ^ 11));
        } else if (r < 0.62) Houses.stoa(b, h, 8);
        else if (r < 0.70) { gardenCourt(b, h); }
        else if (r < 0.77) Houses.smithy(b, h);
        else if (r < 0.83) fountainCourt(b, h);
        else if (r < 0.88) Houses.school(b, h);
        else if (r < 0.93) Houses.bath(b, h);
        else Houses.temple(b, h, 7, 8, p == Realm.Place.HIERANTHE ? "the Healer" : "the Lamp");
    }

    /** A walled garden with a pergola, benches and fruit trees: every city keeps some green inside its walls. */
    static void gardenCourt(Frame b, long h) {
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            boolean wall = Math.abs(x) == 8 || Math.abs(z) == 8;
            b.set(x, 0, z, wall || x == 0 || z == 0 ? DOUBLE_SLAB : GRASS, 8);
            b.clear(x, z, 1, 5);
            if (wall && !(z == -8 && Math.abs(x) <= 1)) { b.set(x, 1, z, QUARTZ, 0); b.set(x, 2, z, LEAVES, 4); }
        }
        Build.fruitTree(b, -4, -4); Build.fruitTree(b, 4, 4); Build.olive(b, -4, 4); Build.olive(b, 4, -4);
        Build.flowers(b, -7, -7, 7, 7, 0.25);
        for (int x = -2; x <= 2; x += 4) for (int z = -2; z <= 2; z += 4) { b.set(x, 1, z, QUARTZ, 2); b.set(x, 2, z, QUARTZ, 2); b.set(x, 3, z, LEAVES, 4); }
        Build.seat(b, 0, 1, 2, 0, -1, QUARTZ_STAIRS);
        b.npc(1, 1, 1, -1, 0, Hash.unit(h ^ 5) < 0.5 ? "citizen:gardener" : "citizen:poet", null);
    }

    /** A fountain court: a basin with a statue, benches, lamps and people drawing water. */
    static void fountainCourt(Frame b, long h) {
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) { b.pave(x, 0, z); b.clear(x, z, 1, 6); }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            int d = x * x + z * z;
            if (d <= 16) b.set(x, 1, z, d > 9 ? QUARTZ : WATER, 0);
        }
        Build.statue(b, 0, 1, 0, 0, -1, 4);
        Build.lamp(b, -7, 1, -7); Build.lamp(b, 7, 1, 7); Build.lamp(b, -7, 1, 7); Build.lamp(b, 7, 1, -7);
        for (int k = -1; k <= 1; k += 2) Build.seat(b, k * 6, 1, 0, -k, 0, QUARTZ_STAIRS);
        b.npc(5, 1, 2, -1, 0, "citizen:water_carrier", null);
        b.npc(-3, 1, -6, 0, 1, "citizen:child", null);
    }
}
