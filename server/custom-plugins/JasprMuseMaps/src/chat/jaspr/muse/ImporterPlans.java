package chat.jaspr.muse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Read-only view of JasprImportedWorldgen's frozen plans (grids 1 and 2), so a Muse+GLM_Maps site never shares a
 * chunk with an imported GLM/Codex site. Reflection against the live 1.2.x schema; any drift while the importer is
 * enabled fails closed (Muse admission refuses the cell) rather than guessing.
 */
final class ImporterPlans {
    static final class Box { final int x0, z0, x1, z1; Box(int x0, int z0, int x1, int z1) { this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; } }

    private Plugin plugin;
    private Field worlds, planner, ledger, ledger2, ground, ground2, planX, planZ, planSite, siteDims;
    private Method get;
    private int cell1, cell2;
    private boolean broken;

    /** null: importer absent/disabled (nothing to avoid). Throws when it is enabled but unreadable. */
    Object context(World world) throws ReflectiveOperationException {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprImportedWorldgen");
        if (p == null || !p.isEnabled()) return null;
        if (broken) throw new ReflectiveOperationException("Importer schema unreadable");
        try {
            if (plugin != p) {
                Class<?> main = p.getClass();
                ClassLoader cl = main.getClassLoader();
                worlds = field(main, "worlds"); planner = field(main, "planner");
                Class<?> ctx = Class.forName("chat.jaspr.imported.ImportedWorldgenPlugin$Context", true, cl);
                ledger = field(ctx, "ledger"); ledger2 = field(ctx, "ledger2"); ground = field(ctx, "ground"); ground2 = field(ctx, "ground2");
                Class<?> led = Class.forName("chat.jaspr.imported.CellLedger", true, cl);
                Class<?> pl = Class.forName("chat.jaspr.imported.CellPlanner", true, cl);
                Class<?> gr = Class.forName("chat.jaspr.imported.CellPlanner$Ground", true, cl);
                get = led.getMethod("get", long.class, int.class, int.class, pl, gr);
                Class<?> plan = Class.forName("chat.jaspr.imported.CellPlanner$Plan", true, cl);
                planX = plan.getField("x"); planZ = plan.getField("z"); planSite = plan.getField("site");
                siteDims = Class.forName("chat.jaspr.imported.SiteSpec", true, cl).getField("dimensions");
                cell1 = pl.getField("CELL_CHUNKS").getInt(null); cell2 = pl.getField("CELL2_CHUNKS").getInt(null);
                if (cell1 < 8 || cell2 < 8) throw new ReflectiveOperationException("Importer grid sizes changed");
                plugin = p;
            }
            @SuppressWarnings("unchecked") Map<UUID, Object> map = (Map<UUID, Object>) worlds.get(p);
            return map.get(world.getUID());
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            throw e instanceof ReflectiveOperationException ? (ReflectiveOperationException) e : new ReflectiveOperationException(e);
        }
    }

    /** True when any importer plan footprint (grown by one chunk) meets the chunk rectangle [c0,c1] x [d0,d1]. */
    boolean conflicts(World world, int c0, int d0, int c1, int d1) {
        Object ctx;
        try { ctx = context(world); }
        catch (ReflectiveOperationException e) { return true; }
        if (ctx == null) return false;
        try {
            Object pl = planner.get(plugin);
            for (int lattice = 0; lattice < 2; lattice++) {
                Object led = (lattice == 0 ? ledger : ledger2).get(ctx), gr = (lattice == 0 ? ground : ground2).get(ctx);
                if (led == null || gr == null) continue;
                int n = lattice == 0 ? cell1 : cell2;
                for (int a = Math.floorDiv(c0 - 16, n); a <= Math.floorDiv(c1 + 16, n); a++)
                    for (int b = Math.floorDiv(d0 - 16, n); b <= Math.floorDiv(d1 + 16, n); b++) {
                        Object p = get.invoke(led, world.getSeed(), a, b, pl, gr);
                        if (p == null) continue;
                        int x = planX.getInt(p), z = planZ.getInt(p);
                        int[] dims = (int[]) siteDims.get(planSite.get(p));
                        int p0 = Math.floorDiv(x, 16) - 1, p1 = Math.floorDiv(x + dims[0] - 1, 16) + 1;
                        int q0 = Math.floorDiv(z, 16) - 1, q1 = Math.floorDiv(z + dims[2] - 1, 16) + 1;
                        if (p1 >= c0 && p0 <= c1 && q1 >= d0 && q0 <= d1) return true;
                    }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return true;
        }
    }

    boolean healthy() { return !broken; }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
