package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Teeth: the Dominion's ring wall round its inner provinces, radius 360 about Anthrakion. Black and colossal,
 * buttressed, crowned with a jagged line of spikes, and studded every fifteen degrees with a spiked tower whose brazier
 * burns while the Marches are held. Its only gate is the Pylon of Teeth, in the west (drawn with the Pylon).
 */
final class Teeth {
    private Teeth() {}

    static final int TOP = 104, TOWER_TOP = 126, TOWER_R = 7;

    static void draw(Plans plans, Canvas c) {
        double rMin = Realm.TEETH_R - TOWER_R - 2, rMax = Realm.TEETH_R + Realm.TEETH_W + TOWER_R + 2;
        // Cheap reject: the chunk's distance range to the centre.
        double near = distanceRange(c, true), far = distanceRange(c, false);
        if (far < rMin || near > rMax) return;
        boolean freed = c.liberated(Realm.Province.MARCHES);
        for (int x = c.x0; x < c.x0 + 16; x++)
            for (int z = c.z0; z < c.z0 + 16; z++) {
                double r = Realm.teethDistance(x, z);
                if (r < Realm.TEETH_R || r >= Realm.TEETH_R + Realm.TEETH_W) continue;
                if (Realm.Place.PYLON.near(x, z, 0)) continue;   // the Pylon draws the gate
                if (Math.abs(x) > Realm.BORDER || Math.abs(z) > Realm.BORDER) continue;
                wall(c, x, z, r, freed);
            }
        // Towers every 15 degrees (the west one is the Pylon).
        for (int k = 0; k < 24; k++) {
            if (k == 12) continue;
            double a = Math.PI * 2 * k / 24;
            int tx = (int) Math.round(Realm.TX + Math.cos(a) * (Realm.TEETH_R + 3)), tz = (int) Math.round(Realm.TZ + Math.sin(a) * (Realm.TEETH_R + 3));
            if (Math.abs(tx) > Realm.BORDER - 8 || Math.abs(tz) > Realm.BORDER - 8) continue;
            if (c.touches(tx - TOWER_R - 2, tz - TOWER_R - 2, tx + TOWER_R + 2, tz + TOWER_R + 2)) tower(plans, c, tx, tz, freed, k);
        }
    }

    private static double distanceRange(Canvas c, boolean nearest) {
        double best = nearest ? Double.MAX_VALUE : 0;
        int[] xs = {c.x0, c.x0 + 15}, zs = {c.z0, c.z0 + 15};
        if (nearest) {
            int cx = Math.max(c.x0, Math.min(c.x0 + 15, Realm.TX)), cz = Math.max(c.z0, Math.min(c.z0 + 15, Realm.TZ));
            return Realm.teethDistance(cx, cz);
        }
        for (int x : xs) for (int z : zs) best = Math.max(best, Realm.teethDistance(x, z));
        return best;
    }

    private static void wall(Canvas c, int x, int z, double r, boolean freed) {
        int g = c.ground(x, z);
        if (g < 0) return;
        double u = r - Realm.TEETH_R;   // 0 inner face .. 7 outer face
        // The wall leans: broad at the foot, narrow at the top (a slope on the outer face).
        int top = TOP - (int) Math.max(0, (u - 4.5) * 9);
        for (int y = Math.max(1, g - 3); y <= top; y++) c.cinder(x, y, z);
        // Jagged crown: spikes of iron and nether-brick along the outer lip.
        if (u >= 4 && u < 5.2) {
            double s = c.roll(x, 0, z, 51);
            int spike = s < 0.35 ? 4 : s < 0.7 ? 2 : 0;
            for (int y = 1; y <= spike; y++) c.set(x, top + y, z, y == spike ? BARS : NETHER_FENCE);
            if (s > 0.97 && !freed) { c.set(x, top + 1, z, NETHERRACK); c.set(x, top + 2, z, FIRE); }
        }
        // Buttresses on the outer face every 12 blocks of arc.
        double arc = Math.toDegrees(Math.atan2(z - Realm.TZ, x - Realm.TX)) * Math.PI / 180 * Realm.TEETH_R;
        if (u > 5.5 && Math.floorMod((long) Math.floor(arc), 12) < 3) for (int y = g; y <= TOP - 20; y++) c.cinder(x, y, z);
    }

    private static void tower(Plans plans, Canvas c, int tx, int tz, boolean freed, int k) {
        int g = plans.surface(tx, tz);
        for (int x = tx - TOWER_R - 1; x <= tx + TOWER_R + 1; x++)
            for (int z = tz - TOWER_R - 1; z <= tz + TOWER_R + 1; z++) {
                if (!c.inside(x, z)) continue;
                double d = Math.sqrt((x - tx) * (x - tx) + (z - tz) * (z - tz));
                if (d > TOWER_R + 0.5) continue;
                boolean shell = d > TOWER_R - 1.2;
                int gg = c.ground(x, z);
                for (int y = Math.max(1, (gg < 0 ? g : gg) - 3); y <= TOWER_TOP; y++) {
                    if (shell || y <= g || (y - g) % 10 == 0) {
                        if (shell && (y - g) % 10 == 6 && (x == tx || z == tz)) c.set(x, y, z, BARS);
                        else c.cinder(x, y, z);
                    } else c.set(x, y, z, AIR);
                }
                // The crown: a ring of great spikes, and a brazier at the heart.
                if (shell) {
                    double s = c.roll(x, TOWER_TOP, z, 52);
                    int spike = 2 + (int) (s * 5);
                    for (int y = 1; y <= spike; y++) c.set(x, TOWER_TOP + y, z, y == spike ? BARS : NETHER_FENCE);
                } else if (d < 1.5) {
                    c.set(x, TOWER_TOP + 1, z, freed ? COBBLE : NETHERRACK);
                    if (!freed) c.set(x, TOWER_TOP + 2, z, FIRE);
                }
                if (x == tx && z == tz) for (int y = g + 1; y < TOWER_TOP; y++) c.set(x, y, z, LADDER, 3);
                if (x == tx && z == tz - 1) for (int y = g + 1; y < TOWER_TOP; y++) c.cinder(x, y, z);
            }
        // Garrison: an Ashborn on the lowest floor while the Marches are held.
        if (!freed && c.inside(tx + 2, tz + 2)) c.npc(tx + 2, g + 1, tz + 2, 0, "dominion:ashborn_bowman", null);
    }
}
