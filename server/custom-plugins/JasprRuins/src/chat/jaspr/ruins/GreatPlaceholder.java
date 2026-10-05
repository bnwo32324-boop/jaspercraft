package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * A plain stand-in for a great structure while its own design is drawn up: a walled keep on a levelled (or, at sea, a
 * raised) platform with its boss's hall, chests, spawners, carved signs and garrisons of every kind of monster. It
 * exercises the whole contract (plan, boss point, garrisons, tiles) so the framework and the offline check can be tried.
 */
abstract class GreatPlaceholder extends GreatDesign {
    static final int HALF = 16;

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Layout l = new Layout();
        int fy = floor(s);
        l.boss = new int[] {0, fy + 1, 4};
        String[] packs = {"deep_one+zombie", "ghoul+husk", "cult_zealot+vindicator", "tomb_crawler+cave_spider", "star_spawn+wither_skeleton",
            "mi_go+enderman", "hound+spider", "cult_adept+evoker", "shoggoth+slime", "nightgaunt+witch", "skeleton+stray", "creeper+zombie_villager"};
        int k = 0;
        for (int u = -12; u <= 12; u += 8) for (int v = -12; v <= 12; v += 24) l.garrisons.add(g(u, fy + 1, v, packs[k++ % packs.length]));
        for (int v = -4; v <= 4; v += 8) for (int u = -12; u <= 12; u += 24) l.garrisons.add(g(u, fy + 1, v, packs[k++ % packs.length]));
        l.garrisons.add(g(-6, fy + 1, 0, "ghoul+zombie"));
        l.garrisons.add(g(6, fy + 1, 0, "deep_one+skeleton"));
        return l;
    }

    /** The keep's floor, local y: the land's level, or just above the sea at sea. */
    static int floor(Plans.GreatSite s) { return s.kind.wet ? s.sea() + 1 : 0; }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int fy = floor(s);
        for (int a = -HALF - 2; a <= HALF + 2; a++)
            for (int b = -HALF - 2; b <= HALF + 2; b++) {
                if (!f.inside(a, b)) continue;
                for (int y = -2; y < fy; y++) f.rubble(a, y, b);
                f.footing(a, b, -3);
                f.paving(a, fy, b);
                f.clear(a, b, fy + 1, fy + 14);
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == HALF;
                if (wall) for (int y = fy + 1; y <= fy + 10; y++) { if (b == -HALF && Math.abs(a) <= 2 && y <= fy + 4) continue; if (y % 5 == 0) f.glyph(a, y, b); else f.eldritch(a, y, b); }
                if (Math.max(Math.abs(a), Math.abs(b)) < HALF) f.set(a, fy + 11, b, PRISMARINE, 2);
            }
        for (int u = -10; u <= 10; u += 10) for (int v = -10; v <= 10; v += 20) f.pillar(u, v, fy + 1, 9, false);
        f.set(0, fy + 10, 0, SEA_LANTERN);
        f.set(-8, fy + 10, 8, SEA_LANTERN);
        f.set(8, fy + 10, -8, SEA_LANTERN);
        String[] tables = {Sites.DUNGEON, Sites.CORRIDOR, Sites.LIBRARY, Sites.SMITH, Sites.JUNGLE, Sites.DESERT};
        for (int i = 0; i < 12; i++) f.chest(-14 + i * 2, fy + 1, 15, 0, -1, tables[i % tables.length], i == 0 ? "lore:" + Hash.range(s.hash, 0, 99) + ";trinket:0.3" : null);
        f.spawner(-14, fy + 1, -14, "ZOMBIE");
        f.spawner(14, fy + 1, -14, "SKELETON");
        f.sign(0, fy + 3, -HALF - 1, 0, -1, s.kind.title.length() > 15 ? "THE GREAT\nRUIN" : s.kind.title.toUpperCase(java.util.Locale.ROOT));
        f.sign(0, fy + 3, HALF - 1, 0, -1, "ITS KEEPER\nSLEEPS HERE\nWAKE IT\nAND DIE");
        f.sign(-HALF + 1, fy + 3, 0, 1, 0, "EVERY HORROR\nKEEPS THIS\nPLACE");
    }
}
