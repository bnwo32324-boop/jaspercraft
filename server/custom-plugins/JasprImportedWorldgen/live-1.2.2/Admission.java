// JasprImportedWorldgen 1.2.2 (live 2026-09-26): the 1.2.1 Admission, source-faithful (Admission, $1, $Box, $Primary compile
// javap-identical to 1.2.1 without the city gate), plus Admission.city. Only Admission.class and Admission$1.class are
// replaced in the 1.2.1 jar; the live Admission$2 is kept.
package chat.jaspr.imported;

import chat.jaspr.biomes.Terrain;
import java.io.IOException;

public final class Admission {
    private Admission() {
    }

    public static CellPlanner.Ground ground(long seed, Terrain terrain, Box boundary, ClaimGuard claims) {
        return Admission.ground(seed, terrain, boundary, claims, null);
    }

    public static CellPlanner.Ground ground(final long seed, final Terrain terrain, final Box boundary, final ClaimGuard claims, final Occupancy.Cache big) {
        return new CellPlanner.Ground(){

            @Override
            public int surface(int x, int z) {
                return terrain.sample(x, z).y;
            }

            @Override
            public boolean permits(int x, int y, int z, SiteSpec site) {
                int sizeX = site.dimensions[0];
                int sizeZ = site.dimensions[2];
                if (Math.abs((long)x) < 600L && Math.abs((long)z) < 600L) {
                    return false;
                }
                if (boundary != null && !boundary.permits(x, z, sizeX, sizeZ)) {
                    return false;
                }
                // 1.2.2 (2026-09-26): no imported structure where a Lost City (JasprLostCities) will stand.
                if (Admission.city(seed, x, z, sizeX, sizeZ)) {
                    return false;
                }
                if (big != null && Occupancy.big(site)) {
                    Occupancy occupancy = big.of(site);
                    if (ImportedWorldgenPlugin.expeditionEnvelope(seed, x, y, z, sizeX, sizeZ, occupancy)) {
                        return false;
                    }
                    return !claims.envelopeConflicts(terrain, x, y, z, site, occupancy);
                }
                if (ImportedWorldgenPlugin.conflictsWithExpeditions(seed, x, z, sizeX, sizeZ)) {
                    return false;
                }
                return !claims.conflicts(terrain, x, y, z, site);
            }
        };
    }

    /** HorrorBiomes 3.27.6+ answers for JasprLostCities; an older HorrorBiomes reserves nothing. */
    static boolean city(long seed, int x, int z, int sizeX, int sizeZ) {
        try {
            return chat.jaspr.biomes.Cities.reserved(seed, x, z, sizeX, sizeZ);
        } catch (LinkageError e) {
            return false;
        }
    }

    public static CellPlanner.Ground secondary(final CellPlanner.Ground primary, final Primary plans) {
        return new CellPlanner.Ground(){

            @Override
            public int surface(int x, int z) {
                return primary.surface(x, z);
            }

            @Override
            public boolean permits(int x, int y, int z, SiteSpec site) {
                int c0 = Math.floorDiv(x, 16) - 1;
                int c1 = Math.floorDiv(x + site.dimensions[0] - 1, 16) + 1;
                int d0 = Math.floorDiv(z, 16) - 1;
                int d1 = Math.floorDiv(z + site.dimensions[2] - 1, 16) + 1;
                try {
                    for (int i = Math.floorDiv(c0, 48); i <= Math.floorDiv(c1, 48); ++i) {
                        for (int j = Math.floorDiv(d0, 48); j <= Math.floorDiv(d1, 48); ++j) {
                            CellPlanner.Plan plan = plans.plan(i, j);
                            if (plan == null) continue;
                            int p0 = Math.floorDiv(plan.x, 16);
                            int p1 = Math.floorDiv(plan.x + plan.site.dimensions[0] - 1, 16);
                            int q0 = Math.floorDiv(plan.z, 16);
                            int q1 = Math.floorDiv(plan.z + plan.site.dimensions[2] - 1, 16);
                            if (p1 < c0 || p0 > c1 || q1 < d0 || q0 > d1) continue;
                            return false;
                        }
                    }
                }
                catch (IOException e) {
                    throw new IllegalStateException("Primary cell lookup failed", e);
                }
                return primary.permits(x, y, z, site);
            }
        };
    }

    public static interface Box {
        public boolean permits(int x, int z, int sizeX, int sizeZ);
    }

    public static interface Primary {
        public CellPlanner.Plan plan(int cellX, int cellZ) throws IOException;
    }
}
