package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * Medium sites on the 96-block grid: demes (villages), villas, sanctuaries, farmsteads, gymnasia, schools, aqueduct
 * lines and watch posts in the Concord; fallen pharoi, ruined outposts and battlefields in the Wound; war camps,
 * watchtowers, slave pens, spawning pits, ash forts, quarries, forge works, stilling houses, stone groves, ruined
 * quarters, edict squares, shrines of the Heart and road camps in the Dominion.
 */
final class Sites {
    private Sites() {}

    static void draw(Plans plans, Canvas c) {
        int i0 = Math.floorDiv(c.x0, Plans.SITE_GRID), j0 = Math.floorDiv(c.z0, Plans.SITE_GRID);
        for (int i = i0 - 1; i <= i0 + 1; i++)
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                Plans.Site s = plans.site(i, j);
                if (s == null) continue;
                int r = s.kind.radius + 2;
                if (!c.touches(s.x - r, s.z - r, s.x + r, s.z + r)) continue;
                Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
                Realm.Zone zone = Realm.zone(s.x, s.z);
                boolean freed = zone.province != null && c.liberated(zone.province);
                try {
                    if (s.kind.concord) SitesConcord.draw(plans, f, s);
                    else SitesDominion.draw(plans, f, s, freed);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("site " + s.kind + " at " + s.x + "," + s.z, e);
                }
            }
    }

    /** A levelled pad for a site: ground filled or cut to the site's base, with the given surface. */
    static void pad(Frame f, int r, int surface) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                for (int y = Math.min(g, f.base) - 2; y < f.base; y++) f.c.set(wx, y, wz, surface == 1 ? STONE : DIRT, surface == 1 ? 5 : 0);
                f.c.clear(wx, wz, f.base + 1, Math.max(g, f.base) + 3);
                switch (surface) {
                    case 0: f.c.set(wx, f.base, wz, GRASS); break;
                    case 1: f.c.ash(wx, f.base, wz); break;
                    case 2: f.c.pave(wx, f.base, wz); break;
                    default: f.c.set(wx, f.base, wz, DIRT, 1);
                }
            }
    }
}
