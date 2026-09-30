package chat.jaspr.backrooms;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Pure JasprBackrooms logic (run: tests/backrooms.test.cjs): the zones and their triggers, the difficulty curve across
 * and within levels, the monsters' scaling and the peaceful Threshold, and frame detection for the gate.
 */
public final class BackroomsLogicTest {
    private static void ok(boolean c, String what) { if (!c) throw new AssertionError(what); }

    public static void main(String[] args) {
        // Zones: each level is found where it lies, nothing between them.
        for (Level lv : Level.ALL) {
            ok(Level.at(lv.entryEnd() + 5, 0) == lv && Level.at(lv.arenaCentreX(), 0) == lv, lv + " is where it lies");
            ok(Level.at(lv.xEnd() + 100, 0) == null, "nothing after " + lv);
            ok(Rooms.inExit(lv, lv.xEnd() - Level.WALL - 1.5, Level.WALK, 0.5) && !Rooms.inExit(lv, lv.arenaCentreX(), Level.WALK, 0.5), lv + " exit trigger");
            if (lv.number > 1) ok(Rooms.inReturn(lv, lv.x0() + 2.5, Level.WALK, -0.5) && !Rooms.inReturn(lv, lv.x0() + 10, Level.WALK, 0), lv + " way back");
        }
        for (int n = 2; n <= 7; n++) { int[] a = Rooms.doorAlcove(n); ok(Rooms.inDoor(a[0] + 0.5, Level.WALK, a[1] + 0.5) == n, "Threshold door " + n); }
        ok(Rooms.inDoor(24.5, Level.WALK, 0.5) == 0, "no door in the middle of the Threshold");
        // Difficulty: rises across each level, and each level starts where the last one ended.
        double last = -1;
        for (Level lv : Level.ALL) {
            for (int x = lv.entryEnd(); x < lv.arenaStart(); x += 16) { double g = lv.danger(x, 0); ok(g >= last - 1e-9, lv + " danger never drops (" + x + ")"); last = g; }
            ok(lv.danger(lv.arenaCentreX(), 0) >= last, lv + " arena is its most dangerous part");
            last = lv.danger(lv.arenaCentreX(), 0);
        }
        ok(Level.YELLOW.danger(Level.YELLOW.entryEnd(), 0) == 0 && Math.abs(Level.POOLS.danger(Level.POOLS.arenaCentreX(), 0) - 1) < 1e-9, "danger runs from 0 to 1");
        double prev = -1;
        for (int i = 0; i <= 20; i++) {
            double g = i / 20.0, v = Mobs.healthScale(g) + Mobs.damageScale(g) + Mobs.cap(g, g) + Mobs.chance(g) + Mobs.eliteChance(g);
            ok(v >= prev, "monsters grow stronger with danger at " + g);
            prev = v;
        }
        ok(Mobs.sanctuary(Level.YELLOW) >= 64, "the spawn is peaceful");
        // Every level has its own monsters, and one boss.
        for (Level lv : Level.ALL) {
            int kinds = 0;
            for (Mobs.Kind k : Mobs.Kind.values()) if (k.level == lv && k.weight > 0) kinds++;
            ok(kinds >= 3, lv + " has its own monsters (" + kinds + ")");
            ok(Bosses.Boss.of(lv).level == lv, lv + " has its boss");
        }
        // The gate: a 4x5 frame of yellow glazed terracotta (corners optional) is found along x and z; a gap is refused.
        for (boolean alongX : new boolean[] {true, false}) {
            Map<Long, Material> m = new HashMap<>();
            for (int a = -1; a <= 3; a++) for (int y = 0; y <= 5; y++) {
                boolean edge = a == -1 || a == 3 || y == 0 || y == 5;
                if (edge) m.put(Protect.key(alongX ? a : 0, y, alongX ? 0 : a), Portals.FRAME);
            }
            Portals.Blocks b = (x, y, z) -> m.getOrDefault(Protect.key(x, y, z), Material.AIR);
            Portals.Gate g = Portals.detect("w", b, 1, 2, 0 + 0);
            if (!alongX) g = Portals.detect("w", b, 0, 2, 1);
            ok(g != null && g.w == 3 && g.h == 4 && g.xAxis == alongX && g.inside(alongX ? 0 : 0, 1, 0) && g.frames(alongX ? -1 : 0, 2, alongX ? 0 : -1), "3x4 gate " + (alongX ? "along x" : "along z"));
            m.put(Protect.key(alongX ? 1 : 0, 3, alongX ? 0 : 1), Material.STONE);
            ok(Portals.detect("w", b, alongX ? 1 : 0, 2, alongX ? 0 : 1) == null, "an obstructed frame is refused");
        }
        Map<Long, Material> obsidian = new HashMap<>();
        for (int a = -1; a <= 2; a++) for (int y = 0; y <= 4; y++) if (a == -1 || a == 2 || y == 0 || y == 4) obsidian.put(Protect.key(a, y, 0), Material.OBSIDIAN);
        ok(Portals.detect("w", (x, y, z) -> obsidian.getOrDefault(Protect.key(x, y, z), Material.AIR), 0, 1, 0) == null, "an obsidian frame is the Nether's, not ours");
        System.out.println("BACKROOMS_LOGIC_OK");
    }
}
