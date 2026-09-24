/*
 * JasprImportedWorldgen 1.3.0 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152 decompilation of
 * the 1.2.0 Admission, cleaned up, with one change: secondary() takes the cell size of the grid it yields to
 * (the old two-argument form keeps 48, grid 1), so grid 3 can yield to grid-1 and grid-2 plans by chaining it.
 * Apply the same change to the PC source. Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import chat.jaspr.biomes.Terrain;
import java.io.IOException;

public final class Admission {
    private Admission() {
    }

    public static CellPlanner.Ground ground(long seed, Terrain terrain, Box box, ClaimGuard claims) {
        return Admission.ground(seed, terrain, box, claims, null);
    }

    public static CellPlanner.Ground ground(final long seed, final Terrain terrain, final Box box, final ClaimGuard claims, final Occupancy.Cache cache) {
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
                if (box != null && !box.permits(x, z, sizeX, sizeZ)) {
                    return false;
                }
                if (cache != null && Occupancy.big(site)) {
                    Occupancy occupancy = cache.of(site);
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

    public static CellPlanner.Ground secondary(CellPlanner.Ground ground, Primary primary) {
        return Admission.secondary(ground, primary, 48);
    }

    /** ground, refusing any box within one chunk of a plan of the grid of cellChunks-chunk cells that primary reads. */
    public static CellPlanner.Ground secondary(final CellPlanner.Ground ground, final Primary primary, final int cellChunks) {
        return new CellPlanner.Ground(){

            @Override
            public int surface(int x, int z) {
                return ground.surface(x, z);
            }

            @Override
            public boolean permits(int x, int y, int z, SiteSpec site) {
                int c0 = Math.floorDiv(x, 16) - 1;
                int c1 = Math.floorDiv(x + site.dimensions[0] - 1, 16) + 1;
                int d0 = Math.floorDiv(z, 16) - 1;
                int d1 = Math.floorDiv(z + site.dimensions[2] - 1, 16) + 1;
                int cell = cellChunks;
                try {
                    for (int i = Math.floorDiv(c0, cell); i <= Math.floorDiv(c1, cell); ++i) {
                        for (int j = Math.floorDiv(d0, cell); j <= Math.floorDiv(d1, cell); ++j) {
                            CellPlanner.Plan plan = primary.plan(i, j);
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
                return ground.permits(x, y, z, site);
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
