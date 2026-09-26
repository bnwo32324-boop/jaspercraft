package chat.jaspr.lostcities;

import chat.jaspr.biomes.HorrorGenerator;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * JasprHorrorBiomes sanctuaries (portal arrival areas) are never built over: those chunks and a 3-chunk halo around
 * them are "not city" and never written, the way the mod kept cities off mansions and ocean monuments.
 *
 * Pure by design. HorrorBiomes' own sanctuary admission (StructureRates.sanctuary/sanctuary2) consults its set-piece,
 * catalogue and dungeon planners, and those now ask CityApi.reserved -- so asking it from here would be a cycle whose
 * answer depends on who asked first. Instead every lattice point HorrorBiomes COULD admit is excluded:
 * HorrorGenerator.portalChunk (the original sanctuaries, always built), and the seeded candidates of the newer
 * lattices (StructureRates.candidate, and candidate2 when that HorrorBiomes version has tier-2 sanctuaries). Each
 * admitted sanctuary is one of those candidates, so this is a superset of "a sanctuary stands here": seed math only,
 * no world access, no call back into placement code, the same answer on every thread.
 * If a lookup is missing or throws, that lattice point is treated as a sanctuary (fail closed).
 */
final class Sanctuaries {
    static final int HALO = 3;
    private static final int[][] CENTRES = {{136, 128}};          // StructureRates.CENTRE_X/Z (3.25.0)
    private static final int[][] EDGES = {{136, 0}, {8, 128}};     // StructureRates.EDGE_A/B (3.28.0)

    private final long seed;
    private final Method candidate;     // StructureRates.candidate(long, int, int)
    private final Method candidate2;    // StructureRates.candidate2(long, int, int), 3.28.0+
    private final boolean centresFailClosed, edgesFailClosed, edgesPresent;

    Sanctuaries(long seed, Logger log) {
        this.seed = seed;
        Method c = null, c2 = null;
        boolean centresClosed = false, edgesClosed = false, edges = false;
        Class<?> rates = null;
        try {
            rates = Class.forName("chat.jaspr.biomes.StructureRates");
        } catch (Throwable t) {
            centresClosed = true;
            edgesClosed = true;
            edges = true;
        }
        if (rates != null) {
            try {
                c = rates.getDeclaredMethod("candidate", long.class, int.class, int.class);
                c.setAccessible(true);
            } catch (Throwable t) {
                centresClosed = true;
            }
            boolean hasTier2 = false;
            for (Method m : rates.getDeclaredMethods()) if (m.getName().equals("sanctuary2") || m.getName().equals("candidate2")) hasTier2 = true;
            if (hasTier2) {
                edges = true;
                try {
                    c2 = rates.getDeclaredMethod("candidate2", long.class, int.class, int.class);
                    c2.setAccessible(true);
                } catch (Throwable t) {
                    edgesClosed = true;
                }
            }
        }
        candidate = c;
        candidate2 = c2;
        centresFailClosed = centresClosed;
        edgesFailClosed = edgesClosed;
        edgesPresent = edges;
        if (log != null && (centresClosed || edgesClosed))
            log.warning("LOST_CITIES_SANCTUARY_LOOKUP_FAILED centres=" + (centresClosed ? "failClosed" : "ok") + " edges=" + (edgesClosed ? "failClosed" : "ok")
                + "; every lattice point of a failed lookup is excluded");
    }

    boolean failClosed() { return centresFailClosed || edgesFailClosed; }

    String describe() {
        return "portal=on centres=" + (centresFailClosed ? "failClosed" : "candidate") + " edges=" + (!edgesPresent ? "absent" : edgesFailClosed ? "failClosed" : "candidate2")
            + " halo=" + HALO;
    }

    /** True if the chunk is a (possible) sanctuary chunk or within HALO chunks of one. */
    boolean excluded(int cx, int cz) {
        int ax = nearest(cx, 8), az = nearest(cz, 0);
        if (near(ax, az, cx, cz) && portal(ax, az)) return true;
        for (int[] p : CENTRES) {
            ax = nearest(cx, p[0]);
            az = nearest(cz, p[1]);
            if (near(ax, az, cx, cz) && (centresFailClosed || ask(candidate, ax, az))) return true;
        }
        if (edgesPresent) {
            for (int[] p : EDGES) {
                ax = nearest(cx, p[0]);
                az = nearest(cz, p[1]);
                if (near(ax, az, cx, cz) && (edgesFailClosed || ask(candidate2, ax, az))) return true;
            }
        }
        return false;
    }

    /** Every lattice point (possible sanctuary) whose halo touches the chunk box, for the fixture's report. */
    java.util.List<int[]> latticeIn(int x0, int z0, int x1, int z1) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int[][] all = {{8, 0}, {136, 128}, {136, 0}, {8, 128}};
        for (int k = 0; k < all.length; k++) {
            if (k >= 2 && !edgesPresent) continue;
            for (int ax = x0 - HALO + Math.floorMod(all[k][0] - (x0 - HALO), 256); ax <= x1 + HALO; ax += 256)
                for (int az = z0 - HALO + Math.floorMod(all[k][1] - (z0 - HALO), 256); az <= z1 + HALO; az += 256) {
                    boolean possible = k == 0 ? portal(ax, az) : k == 1 ? (centresFailClosed || ask(candidate, ax, az)) : (edgesFailClosed || ask(candidate2, ax, az));
                    out.add(new int[]{ax, az, k, possible ? 1 : 0});
                }
        }
        return out;
    }

    private static boolean near(int ax, int az, int cx, int cz) { return Math.abs(ax - cx) <= HALO && Math.abs(az - cz) <= HALO; }

    /** The lattice point (offset o, period 256) nearest to c. */
    private static int nearest(int c, int o) {
        int base = c - Math.floorMod(c - o, 256);
        return (c - base) <= 128 ? base : base + 256;
    }

    private static boolean portal(int ax, int az) {
        try { return HorrorGenerator.portalChunk(ax, az); }
        catch (Throwable t) { return Math.floorMod(ax, 256) == 8 && Math.floorMod(az, 256) == 0; }
    }

    private boolean ask(Method m, int ax, int az) {
        if (m == null) return true;
        try { return Boolean.TRUE.equals(m.invoke(null, seed, ax, az)); }
        catch (Throwable t) { return true; }   // fail closed
    }
}
