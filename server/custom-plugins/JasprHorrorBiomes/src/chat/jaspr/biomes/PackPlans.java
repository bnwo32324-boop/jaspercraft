package chat.jaspr.biomes;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * The other structure packs' plans, for tier 2 (3.29.0). A tier-2 site is built only where no JasprImportedWorldgen or
 * JasprMuseMaps plan could reach: their cells that could reach the box are decided (frozen in their own ledgers) here,
 * first, so neither pack can ever plan over the tier-2 site later. Read by reflection against the live schemas
 * (importer 1.2.x as JasprMuseMaps' ImporterPlans reads it; Muse 1.0.x); with a pack enabled but unreadable the answer
 * is a conflict (the site is refused), never a guess. Main thread only (the packs' admission reads chunks).
 */
final class PackPlans {
    private PackPlans() {}

    private static Plugin importer, muse;
    private static Field iWorlds, iPlanner, iLedger, iLedger2, iGround, iGround2, iPlanX, iPlanZ, iPlanSite, iSiteDims;
    private static Method iGet;
    private static int iCell1, iCell2;
    private static Field mWorlds, mPlanner, mLedger, mGround, mSite, mX, mZ, mDims;
    private static Method mGet;
    private static int mCell;
    static volatile boolean importerBroken, museBroken;

    /** null when nothing either pack planned or could plan reaches the chunk rectangle [c0,c1] x [d0,d1]; else why. */
    static synchronized String conflict(World world, int c0, int d0, int c1, int d1) {
        if (world == null || Bukkit.getServer() == null) return null;
        String imp = importerConflict(world, c0, d0, c1, d1);
        if (imp != null) return imp;
        return museConflict(world, c0, d0, c1, d1);
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name); f.setAccessible(true); return f;
    }

    // ---------------------------------------------------------------- JasprImportedWorldgen (grids 1 and 2)

    private static String importerConflict(World world, int c0, int d0, int c1, int d1) {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprImportedWorldgen");
        if (p == null || !p.isEnabled()) return null;
        try {
            if (importer != p) {
                Class<?> main = p.getClass();
                ClassLoader cl = main.getClassLoader();
                iWorlds = field(main, "worlds"); iPlanner = field(main, "planner");
                Class<?> ctx = Class.forName("chat.jaspr.imported.ImportedWorldgenPlugin$Context", true, cl);
                iLedger = field(ctx, "ledger"); iLedger2 = field(ctx, "ledger2"); iGround = field(ctx, "ground"); iGround2 = field(ctx, "ground2");
                Class<?> led = Class.forName("chat.jaspr.imported.CellLedger", true, cl);
                Class<?> pl = Class.forName("chat.jaspr.imported.CellPlanner", true, cl);
                Class<?> gr = Class.forName("chat.jaspr.imported.CellPlanner$Ground", true, cl);
                iGet = led.getMethod("get", long.class, int.class, int.class, pl, gr);
                Class<?> plan = Class.forName("chat.jaspr.imported.CellPlanner$Plan", true, cl);
                iPlanX = plan.getField("x"); iPlanZ = plan.getField("z"); iPlanSite = plan.getField("site");
                iSiteDims = Class.forName("chat.jaspr.imported.SiteSpec", true, cl).getField("dimensions");
                iCell1 = pl.getField("CELL_CHUNKS").getInt(null); iCell2 = pl.getField("CELL2_CHUNKS").getInt(null);
                if (iCell1 < 8 || iCell2 < 8) throw new ReflectiveOperationException("importer grid sizes changed");
                importer = p;
                importerBroken = false;
            }
            @SuppressWarnings("unchecked") Map<UUID, Object> map = (Map<UUID, Object>) iWorlds.get(p);
            Object ctx = map.get(world.getUID());
            if (ctx == null) return null;
            Object planner = iPlanner.get(p);
            for (int lattice = 0; lattice < 2; lattice++) {
                Object led = (lattice == 0 ? iLedger : iLedger2).get(ctx), gr = (lattice == 0 ? iGround : iGround2).get(ctx);
                if (led == null || gr == null) continue;
                int n = lattice == 0 ? iCell1 : iCell2;
                for (int a = Math.floorDiv(c0 - 16, n); a <= Math.floorDiv(c1 + 16, n); a++)
                    for (int b = Math.floorDiv(d0 - 16, n); b <= Math.floorDiv(d1 + 16, n); b++) {
                        Object plan = iGet.invoke(led, world.getSeed(), a, b, planner, gr);
                        if (plan == null) continue;
                        int x = iPlanX.getInt(plan), z = iPlanZ.getInt(plan);
                        int[] dims = (int[]) iSiteDims.get(iPlanSite.get(plan));
                        int p0 = Math.floorDiv(x, 16) - 1, p1 = Math.floorDiv(x + dims[0] - 1, 16) + 1;
                        int q0 = Math.floorDiv(z, 16) - 1, q1 = Math.floorDiv(z + dims[2] - 1, 16) + 1;
                        if (p1 >= c0 && p0 <= c1 && q1 >= d0 && q0 <= d1) return "imported";
                    }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!importerBroken && Tier2.log != null) Tier2.log.severe("TIER2_PACK_UNREADABLE pack=JasprImportedWorldgen " + reason(e) + " -- tier-2 sites near it are refused");
            importerBroken = true;
            importer = null;
            return "imported-unreadable";
        }
    }

    // ---------------------------------------------------------------- JasprMuseMaps (32-chunk cells)

    private static String museConflict(World world, int c0, int d0, int c1, int d1) {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprMuseMaps");
        if (p == null || !p.isEnabled()) return null;
        try {
            if (muse != p) {
                Class<?> main = p.getClass();
                ClassLoader cl = main.getClassLoader();
                mWorlds = field(main, "worlds"); mPlanner = field(main, "planner");
                Class<?> ctx = Class.forName("chat.jaspr.muse.MusePlugin$Context", true, cl);
                mLedger = field(ctx, "ledger"); mGround = field(ctx, "ground");
                Class<?> led = Class.forName("chat.jaspr.muse.Ledger", true, cl);
                Class<?> pl = Class.forName("chat.jaspr.muse.Planner", true, cl);
                Class<?> gr = Class.forName("chat.jaspr.muse.Planner$Ground", true, cl);
                mGet = led.getMethod("get", long.class, int.class, int.class, pl, gr);
                Class<?> rec = Class.forName("chat.jaspr.muse.Ledger$Record", true, cl);
                mSite = rec.getField("site"); mX = rec.getField("x"); mZ = rec.getField("z"); mDims = field(rec, "dims");
                mCell = pl.getField("CELL_CHUNKS").getInt(null);
                if (mCell < 8) throw new ReflectiveOperationException("Muse grid size changed");
                muse = p;
                museBroken = false;
            }
            @SuppressWarnings("unchecked") Map<UUID, Object> map = (Map<UUID, Object>) mWorlds.get(p);
            Object ctx = map.get(world.getUID());
            if (ctx == null) return null;
            Object planner = mPlanner.get(p), led = mLedger.get(ctx), gr = mGround.get(ctx);
            // A Muse site lies inside its own cell, so only the cells the rectangle (grown by a chunk) touches can reach it.
            for (int a = Math.floorDiv(c0 - 1, mCell); a <= Math.floorDiv(c1 + 1, mCell); a++)
                for (int b = Math.floorDiv(d0 - 1, mCell); b <= Math.floorDiv(d1 + 1, mCell); b++) {
                    Object r = mGet.invoke(led, world.getSeed(), a, b, planner, gr);
                    if (r == null || mSite.get(r) == null) continue;
                    int x = mX.getInt(r), z = mZ.getInt(r);
                    int[] dims = (int[]) mDims.get(r);
                    if (dims == null || dims.length < 3) return "muse";
                    int p0 = Math.floorDiv(x, 16) - 1, p1 = Math.floorDiv(x + dims[0] - 1, 16) + 1;
                    int q0 = Math.floorDiv(z, 16) - 1, q1 = Math.floorDiv(z + dims[2] - 1, 16) + 1;
                    if (p1 >= c0 && p0 <= c1 && q1 >= d0 && q0 <= d1) return "muse";
                }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!museBroken && Tier2.log != null) Tier2.log.severe("TIER2_PACK_UNREADABLE pack=JasprMuseMaps " + reason(e) + " -- tier-2 sites near it are refused");
            museBroken = true;
            muse = null;
            return "muse-unreadable";
        }
    }

    private static String reason(Throwable e) {
        Throwable c = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage().replaceAll("\\s+", " "));
    }

    static String state() {
        return "importer=" + (Bukkit.getPluginManager().getPlugin("JasprImportedWorldgen") == null ? "absent" : importerBroken ? "unreadable" : "linked")
            + " muse=" + (Bukkit.getPluginManager().getPlugin("JasprMuseMaps") == null ? "absent" : museBroken ? "unreadable" : "linked");
    }
}
