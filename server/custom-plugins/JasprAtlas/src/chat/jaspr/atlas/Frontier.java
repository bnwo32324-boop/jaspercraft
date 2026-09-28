package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Last Watch: the fort where the Royal Road crosses the Lampwall. A square keep and courtyard behind the wall,
 * barracks, an armoury, an infirmary, a map room where Lochagos Menon briefs those going east, a heliodrome, and the
 * great gate in the Lampwall itself (drawn by the Line; its opening follows the road).
 */
final class Frontier {
    private Frontier() {}

    static void lastWatch(Plans plans, Frame f) {
        Places.level(f, 30, 2);
        int r = 28;
        // Curtain walls (the east side is the Lampwall itself, a little further east).
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < r - 1 || x > r - 4) continue;
                boolean gate = Math.abs(z) <= 3 && x < 0;
                for (int y = 1; y <= 9; y++) { if (gate && y <= 6) { f.set(x, y, z, AIR); continue; } if (d == r) f.marble(x, y, z); else f.ashlar(x, y, z); }
                if (d == r && Math.floorMod(x + z, 2) == 0) f.set(x, 10, z, QUARTZ, 2);
            }
        // The keep: a tall square tower at the centre-north with a lumen crown.
        Frame k = new Frame(f.c, f.wx(-6, -14), f.wz(-6, -14), f.base, f.rot);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                boolean shell = Math.abs(x) == 6 || Math.abs(z) == 6;
                for (int y = 1; y <= 26; y++) {
                    if (shell) { if (y % 7 == 4 && (x == 0 || z == 0)) k.set(x, y, z, STAINED_PANE, LIGHT_BLUE); else k.marble(x, y, z); }
                    else k.set(x, y, z, y % 7 == 0 ? DOUBLE_SLAB : AIR, 8);
                }
            }
        for (int y = 1; y <= 26; y++) k.set(5, y, 5, LADDER, k.facing(-1, 0));
        for (int y = 1; y <= 3; y++) k.set(0, y, 6, AIR);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if ((Math.abs(x) == 6 || Math.abs(z) == 6) && Math.floorMod(x + z, 2) == 0) k.set(x, 27, z, QUARTZ, 2);
        k.set(0, 27, 0, SEA_LANTERN); k.set(0, 28, 0, END_ROD, 1);
        // The map room on the keep's ground floor: Lochagos Menon and his map table.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 1; z++) k.set(x, 1, z, x == 0 && z == 0 ? SEA_LANTERN : SLAB, 8 | 7);
        k.sign(-5, 2, 0, 1, 0, "EAST: THE\nWOUND, THEN\nTHE MARCHES.\nGO ARMED.");
        k.sign(-5, 2, -2, 1, 0, "THE PYLON OF\nTEETH LIES\n40 STADIA\nEAST");
        k.chest(4, 1, -4, -1, 0, "atlas:map_room", "book:line");
        k.npc(0, 1, 3, 0, -1, "key:menon", null);
        // Barracks along the south wall, the armoury and the infirmary.
        Frame b = new Frame(f.c, f.wx(-8, 18), f.wz(-8, 18), f.base, f.rot);
        CellsConcord.draw(null, b, new Plans.Cell(0, 0, 0, 0, f.base, 0, 17L, Realm.Zone.CONCORD, "barracks", false));
        Frame a = new Frame(f.c, f.wx(12, 18), f.wz(12, 18), f.base, f.rot);
        Build.room(a, -6, -4, 6, 4, 0, 5, 1);
        Build.flatRoof(a, -6, -4, 6, 4, 6, 1);
        a.set(0, 1, -4, AIR); a.set(0, 2, -4, AIR);
        for (int x = -5; x <= 5; x += 2) a.stand(x, 1, 3, 0, -1, "hoplite_rack");
        a.chest(5, 1, 0, -1, 0, "atlas:armoury_line", null);
        a.npc(0, 1, 0, 0, 1, "merchant:quartermaster", null);
        Frame inf = new Frame(f.c, f.wx(12, -16), f.wz(12, -16), f.base, f.rot);
        Build.room(inf, -6, -4, 6, 4, 0, 5, 0);
        Build.flatRoof(inf, -6, -4, 6, 4, 6, 0);
        inf.set(0, 1, 4, AIR); inf.set(0, 2, 4, AIR);
        for (int x = -5; x <= 5; x += 3) inf.bed(x, 1, -2, 0, -1, WHITE);
        inf.set(5, 1, 3, BREWING, 0);
        inf.npc(0, 1, 1, 0, -1, "merchant:apothecary", null);
        // The courtyard: training posts, a Talos pair, supply wagons, and the heliodrome.
        for (int kx = -3; kx <= 3; kx += 3) f.stand(kx, 1, 6, 0, -1, "target");
        f.npc(18, 1, -3, 1, 0, "talos", null);
        f.npc(18, 1, 3, 1, 0, "talos", null);
        f.npc(4, 1, 0, 1, 0, "citizen:hoplite", null);
        Frame hd = new Frame(f.c, f.wx(-18, 8), f.wz(-18, 8), f.base, f.rot);
        Landmarks.heliodrome(hd, "last_watch", "THE LAST WATCH");
    }
}
