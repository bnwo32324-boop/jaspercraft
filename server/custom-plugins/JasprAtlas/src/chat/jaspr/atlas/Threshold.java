package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Gate of Strangers, just outside Astreion's west wall: where every stranger arrives. A paved court with colonnades,
 * the Great Quartz Gate on a round dais (its surface half blue, half black), the Xenon (the guest hall, beds for any
 * stranger), the Hall of Welcome (maps of Atlas, the Stranger's Cup, the First Stranger's statue), a heliodrome, and a
 * plinth that waits for the names of those who free Atlas.
 */
final class Threshold {
    private Threshold() {}

    /** The Great Gate: its portal plane (x), interior z range, and interior floor (world y). */
    static final int GATE_X = -728, GATE_Z0 = -2, GATE_W = 4, GATE_H = 5;
    static int gateY() { return Terrain.base(Realm.Place.GATE_OF_STRANGERS) + 2; }

    static void draw(Plans plans, Frame f) {
        int cx = Realm.Place.GATE_OF_STRANGERS.x;
        Places.level(f, 16, 2);
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) if (Math.abs(x) <= 16 && Math.abs(z) <= 16) f.pave(x, 0, z);
        Build.mosaic(f, -6, -6, 6, 6, 0);
        // Colonnades on the north and south sides.
        for (int x = -15; x <= 15; x += 3) { Build.column(f, x, -13, 1, 6); Build.column(f, x, 13, 1, 6); }
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) if (Math.abs(z) >= 13) f.set(x, 7, z, DOUBLE_SLAB, 7);
        // The eastern propylon onto the Royal Road.
        for (int z = -5; z <= 5; z++) for (int y = 1; y <= 9; y++) if (Math.abs(z) >= 4 || y >= 7) f.marble(16, y, z);
        f.sign(15, 8, 0, -1, 0, "THE GATE OF\nSTRANGERS\nWELCOME TO\nATLAS");
        f.npc(14, 1, -3, -1, 0, "talos", null);
        f.npc(14, 1, 3, -1, 0, "talos", null);
        // The Great Gate on its dais: a round stepped plinth, the quartz frame, lamps either side.
        int gx = GATE_X - cx;
        for (int x = gx - 5; x <= gx + 5; x++)
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt((x - gx) * (x - gx) + z * z);
                if (d > 6.4) continue;
                f.ashlar(x, 1, z);
                if (d < 4.6) f.set(x, 1, z, DOUBLE_SLAB, 7);
            }
        for (int z = GATE_Z0 - 1; z <= GATE_Z0 + GATE_W; z++)
            for (int y = 1; y <= GATE_H + 2; y++) {
                boolean frame = z == GATE_Z0 - 1 || z == GATE_Z0 + GATE_W || y == 1 || y == GATE_H + 2;
                boolean corner = (z == GATE_Z0 - 1 || z == GATE_Z0 + GATE_W) && (y == 1 || y == GATE_H + 2);
                if (frame) f.set(gx, y, z, QUARTZ, corner ? 1 : (y == 1 || y == GATE_H + 2) ? 0 : 2);
                else f.set(gx, y, z, PORTAL, 2);   // plane along z: axis Z
            }
        f.set(gx, GATE_H + 3, GATE_Z0 + 1, QUARTZ, 1); f.set(gx, GATE_H + 3, GATE_Z0 + 2, QUARTZ, 1);
        f.set(gx, GATE_H + 4, GATE_Z0 + 1, END_ROD, 1); f.set(gx, GATE_H + 4, GATE_Z0 + 2, END_ROD, 1);
        for (int k = -1; k <= 1; k += 2) Build.lamp(f, gx + 2, 2, k * 5);
        f.sign(gx + 1, 1, GATE_Z0 - 2, 1, 0, "THE GREAT\nQUARTZ GATE\nWALK IN TO\nGO HOME");
        f.npc(gx + 4, 2, -4, -1, 0, "key:philon", null);
        // The Xenon: the guest hall along the north side, beds for strangers, a hearth and a table.
        Frame x = new Frame(f.c, f.wx(0, -22), f.wz(0, -22), f.base, f.rot);
        Build.room(x, -12, -5, 12, 5, 0, 5, 0);
        Build.gable(x, -12, -5, 12, 5, 6, false, 0);
        for (int y = 1; y <= 3; y++) for (int k = -1; k <= 1; k++) x.set(k, y, 5, AIR);
        for (int k = -10; k <= 10; k += 3) x.bed(k, 1, -3, 0, -1, Math.floorMod(k, 2) == 0 ? LIGHT_BLUE : WHITE);
        for (int k = -9; k <= 9; k++) x.set(k, 1, 2, SLAB, 8 | 2);
        x.set(11, 1, 0, FURNACE, x.facing(-1, 0)); x.set(11, 1, 1, CAULDRON, 3);
        x.chest(-11, 1, 0, 1, 0, "atlas:xenon", "book:welcome");
        for (int k = -8; k <= 8; k += 8) Build.ceilingLamp(x, k, 5, 0);
        x.npc(0, 1, 0, 0, 1, "citizen:host", null);
        x.sign(0, 3, 6, 0, 1, "THE XENON\nANY STRANGER\nMAY SLEEP\nHERE, FREE");
        // The Hall of Welcome on the south side: maps, the Stranger's Cup and the First Stranger.
        Frame w = new Frame(f.c, f.wx(0, 22), f.wz(0, 22), f.base, f.rot);
        Build.room(w, -12, -5, 12, 5, 0, 6, 0);
        Build.gable(w, -12, -5, 12, 5, 7, false, 0);
        for (int y = 1; y <= 3; y++) for (int k = -1; k <= 1; k++) w.set(k, y, -5, AIR);
        w.sign(-8, 3, 4, 0, -1, "WEST: THE\nCONCORD\nEAST: THE\nDOMINION");
        w.sign(-5, 3, 4, 0, -1, "ASTREION IS\nJUST EAST\nTHE LINE:\n80 STADIA EAST");
        w.sign(-2, 3, 4, 0, -1, "LAMPSA: N-E\nHIERANTHE: S-E\nMNEMEIA: FAR\nNORTH-WEST");
        w.sign(4, 3, 4, 0, -1, "ASK THE\nSYNEDRION\nHOW YOU MAY\nHELP ATLAS");
        w.set(0, 1, 1, QUARTZ, 1); w.set(0, 2, 1, CAULDRON, 3);
        w.sign(0, 2, 0, 0, -1, "THE STRANGER'S\nCUP. IT IS\nNEVER EMPTY.\nDRINK.");
        Build.statue(w, 8, 1, 2, 0, -1, 5);
        w.sign(8, 1, 0, 0, -1, "THE FIRST\nSTRANGER\nSHE CAME AND\nSHE STAYED");
        w.npc(-6, 1, 0, 0, -1, "citizen:cartographer", null);
        // The monument plinth: the names of the Rekindlers are carved here when Atlas is free.
        for (int xx = 7; xx <= 11; xx++) for (int zz = 9; zz <= 11; zz++) f.ashlar(xx, 1, zz);
        f.npc(9, 2, 10, 0, -1, "monument", null);
        Frame hd = new Frame(f.c, f.wx(6, -6), f.wz(6, -6), f.base, f.rot);
        Landmarks.heliodrome(hd, "threshold", "THE THRESHOLD");
    }
}
