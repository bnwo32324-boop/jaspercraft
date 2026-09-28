package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The roads of Atlas. Asterian roads are paved and kerbed, lit every 24 blocks by lumen lamps on quartz posts and marked
 * by milestones; across the Wound the Royal Road is broken and cratered; Dominion roads are packed black gravel and
 * cracked clay between stakes, braziers and the odd gibbet.
 */
final class Roads {
    private Roads() {}

    static void draw(Plans plans, Canvas c) {
        for (Plans.Road road : plans.roadsNear(c.x0, c.z0, c.x0 + 15, c.z0 + 15)) {
            for (int x = c.x0; x < c.x0 + 16; x++)
                for (int z = c.z0; z < c.z0 + 16; z++) {
                    double d = road.distance(x, z);
                    if (d > road.half + 1.5) continue;
                    Realm.Zone zone = Realm.zone(x, z);
                    if (zone == Realm.Zone.RIM) continue;
                    int h = c.ground(x, z);
                    if (h < 0) continue;
                    boolean healed = zone.province != null && c.liberated(zone.province);
                    if (zone.concord) asterian(c, road, x, z, h, d);
                    else if (zone == Realm.Zone.WOUND) broken(c, road, x, z, h, d);
                    else dominion(c, road, x, z, h, d, healed);
                }
        }
    }

    private static void asterian(Canvas c, Plans.Road road, int x, int z, int h, double d) {
        if (d <= road.half) {
            if (road.half >= 3) c.pave(x, h, z); else c.roadbed(x, h, z);
            c.clear(x, z, h + 1, h + 3);
            return;
        }
        // Kerb: a low slab edge, and every 24 blocks a lumen lamp on a quartz post.
        c.set(x, h, z, DOUBLE_SLAB, 8);
        c.clear(x, z, h + 1, h + 2);
        if (lampSpot(road, x, z)) {
            c.set(x, h + 1, z, QUARTZ, 2);
            c.set(x, h + 2, z, QUARTZ, 2);
            c.set(x, h + 3, z, SEA_LANTERN);
            c.set(x, h + 4, z, SLAB, 7);
        }
    }

    private static void broken(Canvas c, Plans.Road road, int x, int z, int h, double d) {
        double crater = Hash.noise(c.seed ^ 0xC4A7L, x, z, 7);
        if (d <= road.half) {
            if (crater > 0.72) { c.set(x, h, z, GRAVEL); c.set(x, h - 1, z, POWDER, BLACK); return; }
            if (c.roll(x, h, z, 31) < 0.55) c.set(x, h, z, DOUBLE_SLAB, 8); else c.set(x, h, z, c.roll(x, h, z, 32) < 0.5 ? GRAVEL : COBBLE);
            c.clear(x, z, h + 1, h + 2);
            return;
        }
        if (lampSpot(road, x, z) && c.roll(x, h, z, 33) < 0.6) {   // the lamps here are broken stumps
            c.set(x, h + 1, z, QUARTZ, 2);
            if (c.roll(x, h, z, 34) < 0.4) c.set(x, h + 2, z, QUARTZ, 2);
        }
    }

    private static void dominion(Canvas c, Plans.Road road, int x, int z, int h, double d, boolean healed) {
        if (d <= road.half) {
            if (healed && c.roll(x, h, z, 35) < 0.5) c.set(x, h, z, GRAVEL); else c.blackRoad(x, h, z);
            c.clear(x, z, h + 1, h + 2);
            return;
        }
        if (!lampSpot(road, x, z)) return;
        long k = Hash.of(c.seed ^ 0xB4A2L, x, z);
        double r = Hash.unit(k);
        if (healed) {   // after liberation the stakes are torn down; the braziers hold flowers
            if (r < 0.5) { c.set(x, h + 1, z, COBBLE); c.pot(x, h + 2, z, "red_flower:0"); }
            return;
        }
        if (r < 0.45) {   // a brazier
            c.set(x, h + 1, z, NETHER_FENCE);
            c.set(x, h + 2, z, NETHERRACK);
            c.set(x, h + 3, z, FIRE);
        } else if (r < 0.8) {   // a stake crowned with a skull
            for (int y = 1; y <= 3; y++) c.set(x, h + y, z, NETHER_FENCE);
            c.set(x, h + 4, z, SKULL, 1);
        } else {   // a bare iron spike
            for (int y = 1; y <= 2; y++) c.set(x, h + y, z, BARS);
        }
    }

    /** Lamp and stake posts stand every 24 blocks along a road's edge. */
    private static boolean lampSpot(Plans.Road road, int x, int z) {
        double[] dir = road.direction(x, z);
        long along = Math.round(x * dir[0] + z * dir[1]);
        return Math.floorMod(along, 24) == 0 && Math.abs(road.distance(x, z) - road.half - 1) < 0.5;
    }
}
