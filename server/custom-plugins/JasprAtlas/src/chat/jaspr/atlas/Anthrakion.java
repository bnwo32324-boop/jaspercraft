package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * Anthrakion, the Pyrarch's tower, where the Heliotheion stood: a black spire tapering from a 16-block base to a
 * 6-block crown 150 blocks up, jagged buttresses spiralling round it, a curtain wall at its foot whose gate stays sealed
 * while any of the four Wards burns. Inside, a stair climbs past the Gate Hall, the Chamber of Chains (the Chained
 * Choir), the Black Guard's barracks, the Library of Ash, the Forge of Crowns and the Choirmaster's hall to the Throne of
 * Cinders; above it, in a cage of spikes, burns the Cinder Heart. When Atlas is won the Heart is broken and the tower
 * stands dark.
 */
final class Anthrakion {
    private Anthrakion() {}

    static final int R0 = 16, R1 = 6, HEIGHT = 150, WALL_R = 38, LEVEL = 12;

    static int radiusAt(int y) { return Math.max(R1, R0 - (R0 - R1) * y / HEIGHT); }

    static boolean wardsBroken(Canvas c) { return (c.mask & 15) == 15; }

    static void draw(Plans plans, Frame f, Canvas c) {
        boolean won = c.liberated(Realm.Province.PLATEAU), open = wardsBroken(c);
        wards(plans, c);
        if (!f.touches(-WALL_R - 4, -WALL_R - 4, WALL_R + 4, WALL_R + 4) && !f.touches(-12, -12, 12, 12)) return;
        // The yard inside the curtain wall.
        for (int x = -WALL_R; x <= WALL_R; x++)
            for (int z = -WALL_R; z <= WALL_R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > WALL_R + 0.5 || !f.inside(x, z)) continue;
                int g = f.ground(x, z);
                if (g >= 0) for (int y = Math.min(g, f.base) - 2; y < f.base; y++) f.c.set(f.wx(x, z), y, f.wz(x, z), STONE, 5);
                f.set(x, 0, z, won ? STONE : CONCRETE, won ? 5 : BLACK);
                f.clear(x, z, 1, 12);
                if (d > WALL_R - 3) {
                    boolean gate = x < 0 && Math.abs(z) <= 4;
                    for (int y = 1; y <= 22; y++) {
                        if (gate && y <= 12) {
                            if (!open && (d > WALL_R - 1.5)) f.set(x, y, z, (Math.floorMod(z, 2) == 0 || y % 3 == 0) ? BARS : OBSIDIAN);
                            else f.set(x, y, z, AIR);
                            continue;
                        }
                        f.cinder(x, y, z);
                    }
                    if (Math.floorMod(x + z, 2) == 0) { int s = 1 + (int) (f.roll(x, 22, z, 161) * 4); for (int y = 1; y <= s; y++) f.set(x, 22 + y, z, y == s ? BARS : NETHER_FENCE); }
                }
            }
        f.sign(-WALL_R - 1, 14, 0, -1, 0, won ? "ANTHRAKION\nFALLEN\nTHE LIGHT\nIS WHOLE" : open ? "THE WARDS\nARE DARK.\nTHE GATE\nSTANDS OPEN" : "FOUR FIRES\nKEEP THIS GATE\nFOUR CROWNS\nKEEP THE FIRES");
        tower(f, won, open);
        heart(f, won);
    }

    private static void tower(Frame f, boolean won, boolean open) {
        for (int x = -R0 - 3; x <= R0 + 3; x++)
            for (int z = -R0 - 3; z <= R0 + 3; z++) {
                if (!f.inside(x, z)) continue;
                double d = Math.sqrt(x * x + z * z), ang = Math.atan2(z, x);
                for (int y = 1; y <= HEIGHT; y++) {
                    int r = radiusAt(y);
                    // Jagged buttresses spiralling up the outside.
                    double spiral = Math.sin(ang * 5 + y / 9.0);
                    int outer = r + (spiral > 0.6 ? 2 : spiral > 0.2 ? 1 : 0);
                    if (d > outer + 0.5) continue;
                    boolean shell = d > r - 1.5;
                    boolean floor = y % LEVEL == 0;
                    if (shell || d > r - 0.5) {
                        boolean window = y % LEVEL == 6 && (Math.abs(x) <= 1 || Math.abs(z) <= 1) && d > r - 1.5;
                        if (window) f.set(x, y, z, won ? AIR : BARS);
                        else if (d > r + 0.5) f.set(x, y, z, y % 7 == 0 ? OBSIDIAN : NETHER_BRICK);
                        else f.cinder(x, y, z);
                        continue;
                    }
                    if (floor && d > 3.5) { f.set(x, y, z, CONCRETE, BLACK); continue; }
                    f.set(x, y, z, AIR);
                    // The spiral stair: a quarter-turn per level, climbing round a central shaft.
                    if (d >= 1.5 && d < 3.5) {
                        double stairAng = ((y % LEVEL) / (double) LEVEL) * Math.PI * 2;
                        double da = Math.abs(Math.atan2(Math.sin(ang - stairAng), Math.cos(ang - stairAng)));
                        if (da < 0.5) f.set(x, y, z, NETHER_BRICK);
                        else if (y % LEVEL == 0) f.set(x, y, z, NETHER_BRICK);
                    }
                    if (d < 1.5) f.set(x, y, z, y % 3 == 0 ? MAGMA : OBSIDIAN);   // the Heart's root
                }
            }
        // The gate into the tower (west), and each level's furnishing.
        for (int y = 1; y <= 5; y++) for (int z = -2; z <= 2; z++) for (int x = -R0 - 3; x <= -R0 + 2; x++) f.set(x, y, z, open ? AIR : OBSIDIAN);
        level(f, 0, won, "gate");
        level(f, 1, won, "chains");
        level(f, 2, won, "guard");
        level(f, 3, won, "library");
        level(f, 4, won, "forge");
        level(f, 5, won, "choirmaster");
        for (int k = 6; k < HEIGHT / LEVEL - 1; k++) level(f, k, won, k % 2 == 0 ? "guard" : "prison");
        // The Throne of Cinders: the open crown.
        int top = HEIGHT;
        for (int x = -R1 - 4; x <= R1 + 4; x++)
            for (int z = -R1 - 4; z <= R1 + 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R1 + 4.5) continue;
                f.set(x, top, z, d > R1 + 3.5 ? NETHER_BRICK : CONCRETE, BLACK);
                f.clear(x, z, top + 1, top + 6);
                if (d > R1 + 3.5) { int s = 2 + (int) (f.roll(x, top, z, 162) * 6); for (int y = 1; y <= s; y++) f.set(x, top + y, z, y == s ? BARS : NETHER_FENCE); }
            }
        f.fill(-1, top + 1, 3, 1, top + 2, 4, won ? STONE : OBSIDIAN, won ? 5 : 0);
        if (!won) f.npc(0, top + 1, 0, 0, 1, "boss:pyrarch", null);
    }

    /** One level of the tower (k counts from 0 at the ground). */
    private static void level(Frame f, int k, boolean won, String kind) {
        int y = k * LEVEL + 1, r = radiusAt(y) - 2;
        switch (kind) {
            case "gate":
                if (won) return;
                for (int side = -1; side <= 1; side += 2) Build.brazier(f, -r + 2, y, side * 4);
                f.npc(-r + 4, y, 0, 1, 0, "dominion:blackshield", null);
                f.npc(-r + 4, y, 3, 1, 0, "dominion:blackshield", null);
                break;
            case "chains":
                // The Chained Choir: captives made to sing the Pyrarch's note, chained round the Heart's root.
                for (int n = 0; n < 6; n++) {
                    double a = Math.PI * 2 * n / 6;
                    int x = (int) Math.round(Math.cos(a) * (r - 1)), z = (int) Math.round(Math.sin(a) * (r - 1));
                    if (!won) { f.set(x, y + 2, z, BARS); f.npc(x, y, z, 0, 1, "choir:" + n, null); }
                    else f.set(x, y, z, FLOWER_POT);
                }
                if (!won) f.sign(-r, y + 1, 0, 1, 0, "SING THE\nONE NOTE.\nSING IT\nFOREVER.");
                break;
            case "guard":
                if (won) return;
                for (int n = 0; n < 4; n++) { double a = Math.PI / 2 * n + 0.4; f.set((int) (Math.cos(a) * (r - 1)), y, (int) (Math.sin(a) * (r - 1)), HAY); }
                f.npc(r - 3, y, 0, -1, 0, "dominion:blackshield", null);
                f.npc(-r + 3, y, 2, 1, 0, "dominion:ashborn_bowman", null);
                f.chest(0, y, r - 2, 0, -1, "atlas:black_guard", null);
                break;
            case "library":
                for (int n = 0; n < 12; n++) {
                    double a = Math.PI * 2 * n / 12;
                    int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                    for (int yy = y; yy <= y + 3; yy++) f.set(x, yy, z, f.roll(x, yy, z, 163) < 0.6 ? BOOKSHELF : LOG2, 1);
                }
                f.chest(2, y, 2, 0, -1, "atlas:library_of_ash", "book:journal");
                f.sign(0, y + 1, r - 1, 0, -1, "THE LIBRARY\nOF ASH. HE\nKEPT WHAT\nHE BURNED.");
                break;
            case "forge":
                f.set(-3, y, 3, ANVIL, 0); f.set(3, y, -3, ANVIL, 0);
                if (!won) { f.set(3, y - 1, 3, LAVA); f.set(-3, y - 1, -3, LAVA); }
                f.chest(-4, y, 0, 1, 0, "atlas:forge_of_crowns", "book:crowns");
                f.sign(0, y + 1, -r + 1, 0, 1, "HERE FOUR\nCROWNS WERE\nMADE FROM\nONE HEART.");
                break;
            case "choirmaster":
                if (!won) f.npc(0, y, 4, 0, -1, "boss:choirmaster", null);
                for (int n = 0; n < 8; n++) { double a = Math.PI * 2 * n / 8; f.set((int) (Math.cos(a) * (r - 1)), y, (int) (Math.sin(a) * (r - 1)), NOTE); }
                break;
            default:   // prison
                if (won) return;
                for (int n = 0; n < 4; n++) { double a = Math.PI / 2 * n; int x = (int) (Math.cos(a) * (r - 2)), z = (int) (Math.sin(a) * (r - 2)); for (int yy = y; yy <= y + 2; yy++) f.set(x, yy, z, BARS); }
        }
    }

    /** The Cinder Heart above the Throne, in a cage of spikes; broken (scattered, dark) when Atlas is won. */
    private static void heart(Frame f, boolean won) {
        int cy = HEIGHT + 16, r = 7;
        for (int x = -r - 2; x <= r + 2; x++)
            for (int z = -r - 2; z <= r + 2; z++)
                for (int y = -r - 2; y <= r + 2; y++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (won) {
                        if (d < r && f.roll(x, cy + y, z, 164) < 0.08) f.set(x, cy + y - 6, z, OBSIDIAN);
                        continue;
                    }
                    if (d <= r) {
                        double k = f.roll(x, cy + y, z, 165);
                        f.set(x, cy + y, z, d < r - 1.5 ? (k < 0.5 ? MAGMA : WART_BLOCK) : (k < 0.55 ? MAGMA : k < 0.8 ? OBSIDIAN : WART_BLOCK));
                    } else if (d <= r + 2 && (Math.abs(x) == 0 || Math.abs(z) == 0 || Math.abs(x) == Math.abs(z)) && y < 2) {
                        f.set(x, cy + y, z, NETHER_FENCE);   // the cage's ribs
                    }
                }
        if (won) return;
        for (int y = HEIGHT + 1; y < cy - r; y++) { f.set(0, y, 0, OBSIDIAN); f.set(1, y, 0, NETHER_FENCE); f.set(-1, y, 0, NETHER_FENCE); }
        f.set(0, cy + r + 1, 0, NETHERRACK);
        f.set(0, cy + r + 2, 0, FIRE);
    }

    /** The four Wards round the tower: pillars of fire, dark once their province is free. */
    static void wards(Plans plans, Canvas c) {
        for (Realm.Province p : new Realm.Province[] {Realm.Province.MARCHES, Realm.Province.WEALD, Realm.Province.FORGES, Realm.Province.FALLEN}) {
            int wx = Realm.wardX(p), wz = Realm.wardZ(p);
            if (!c.touches(wx - 6, wz - 6, wx + 6, wz + 6)) continue;
            Frame f = new Frame(c, wx, wz, plans.surface(wx, wz), 0);
            boolean dark = c.liberated(p);
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++) {
                    boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                    if (corner) continue;
                    f.footing(x, z, 0, false);
                    int top = 38 - (Math.abs(x) + Math.abs(z)) * 2;
                    for (int y = 0; y <= top; y++) f.cinder(x, y, z);
                }
            f.set(0, 39, 0, dark ? CONCRETE : NETHERRACK, dark ? GRAY : 0);
            f.set(0, 40, 0, dark ? AIR : FIRE);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { f.set(d[0], 36, d[1], dark ? CONCRETE : MAGMA, dark ? GRAY : 0); if (!dark) f.set(d[0], 37, d[1], FIRE); }
            f.sign(0, 2, -4, 0, -1, "WARD OF\n" + wardName(p) + (dark ? "\nIS DARK:\nCROWN BROKEN" : "\nBURNS WHILE\nTHE CROWN LIVES"));
            f.npc(0, 1, -5, 0, -1, "ward:" + p.name().toLowerCase(), null);
        }
    }

    static String wardName(Realm.Province p) {
        switch (p) {
            case MARCHES: return "KALLIAS";
            case WEALD: return "MELAINA";
            case FORGES: return "DAIDAROS";
            case FALLEN: return "KELEOS";
            default: return "";
        }
    }
}
