package chat.jaspr.biomes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structure rates probe (3.28.0, second 1.5x). Counts every structure the plugin's own admission rules build in
 * a box of fresh ground (no rates boundary: every chunk counts as new), per generator and per lattice layer,
 * and fingerprints each layer that existed before 3.28.0, so the old jar and the new jar can be compared:
 * every old fingerprint must be identical and every generator's total must grow about 1.5x.
 *
 *   java -cp paper.jar:JasprHorrorBiomes.jar:probe chat.jaspr.biomes.StructureRatesProbe [seed] [x0] [z0] [chunks]
 *
 * Vanilla-style spawner rooms depend on generated caves, not only on the seed, and are measured on a test server.
 */
public final class StructureRatesProbe {
    static Object get(Class<?> c, String name) throws Exception {
        try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
        catch (NoSuchFieldException e) { return null; }
    }
    static Method method(Class<?> c, String name, Class<?>... types) {
        try { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m; }
        catch (NoSuchMethodException e) { return null; }
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 3127727864271777472L;
        int x0 = args.length > 1 ? Integer.parseInt(args[1]) : 400, z0 = args.length > 2 ? Integer.parseInt(args[2]) : 400;
        int n = args.length > 3 ? Integer.parseInt(args[3]) : 1000;
        Terrain t = new Terrain(seed);
        Map<String, List<String>> layers = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        long started = System.currentTimeMillis();

        // -- register set pieces --------------------------------------------------------------------------
        Class<?> M = Megaliths.class;
        int[] cell = (int[]) get(M, "C_CELL"), cell2 = (int[]) get(M, "C_CELL2"), cell3 = (int[]) get(M, "C_CELL3");
        long[] salt = (long[]) get(M, "C_SALT"), salt2 = (long[]) get(M, "C_SALT2"), salt3 = (long[]) get(M, "C_SALT3");
        int[] sx = (int[]) get(M, "C_SX"), sz = (int[]) get(M, "C_SZ");
        Method fits = method(M, "fits", Terrain.class, int.class, int.class, int.class);
        Method claimed = method(M, "claimed", Terrain.class, int.class, int.class, int.class, int.class, int.class);
        Method baseY = method(M, "baseY", Terrain.class, int.class, int.class, int.class);
        Method secondaryBase = method(M, "secondaryBase", Terrain.class, int.class, int.class, int.class);
        Method tertiaryBase = method(M, "tertiaryBase", Terrain.class, int.class, int.class, int.class);
        for (int k = 0; k < cell.length; k++) {
            for (int layer = 0; layer < 3; layer++) {
                int[] cells = layer == 0 ? cell : layer == 1 ? cell2 : cell3;
                long[] salts = layer == 0 ? salt : layer == 1 ? salt2 : salt3;
                if (cells == null) continue;
                int c = cells[k];
                int ax = (int) Math.floorMod(Terrain.mix(seed + salts[k]) >>> 3, (long) c);
                int az = (int) Math.floorMod(Terrain.mix(seed + salts[k] + 17L) >>> 3, (long) c);
                for (int acx = x0 + Math.floorMod(ax - x0, c); acx < x0 + n; acx += c)
                    for (int acz = z0 + Math.floorMod(az - z0, c); acz < z0 + n; acz += c) {
                        int x = acx * 16 + 1, z = acz * 16 + 1;
                        boolean built;
                        if (layer == 0) built = (Boolean) fits.invoke(null, t, k, x, z) && !(Boolean) claimed.invoke(null, t, x, z, sx[k], sz[k], k)
                            && (Integer) baseY.invoke(null, t, k, acx, acz) >= 0;
                        else built = (Integer) (layer == 1 ? secondaryBase : tertiaryBase).invoke(null, t, k, acx, acz) >= 0;
                        if (!built) continue;
                        String name = "setpiece." + (layer == 0 ? "primary" : layer == 1 ? "secondary" : "tertiary");
                        layers.computeIfAbsent(name, q -> new ArrayList<>()).add(k + "@" + acx + "," + acz);
                        // Recognition: the new layer must be named where it stands.
                        if (layer == 2) {
                            int base = (Integer) tertiaryBase.invoke(null, t, k, acx, acz);
                            int named = Megaliths.located(t, x + sx[k] / 2, base + 1, z + sz[k] / 2);
                            if (named != k) counts.merge("ERROR.tertiary-not-located", 1, Integer::sum);
                        }
                    }
            }
        }

        // -- lattice rooms -----------------------------------------------------------------------------------
        Class<?> D = Dungeons.class;
        int[] dcell = (int[]) get(D, "D_CELL"), dcellC = (int[]) get(D, "D_CELL_C"), dcellD = (int[]) get(D, "D_CELL_D");
        long[] dA = (long[]) get(D, "D_SALT_A"), dB = (long[]) get(D, "D_SALT_B"), dC = (long[]) get(D, "D_SALT_C"), dD = (long[]) get(D, "D_SALT_D");
        int[] dsx = (int[]) get(D, "D_SX"), dsz = (int[]) get(D, "D_SZ");
        boolean[] dsurf = (boolean[]) get(D, "D_SURFACE");
        Class<?> A = Class.forName("chat.jaspr.biomes.Dungeons$Anchor");
        Field anchorX = A.getDeclaredField("x"), anchorY = A.getDeclaredField("y"), anchorZ = A.getDeclaredField("z");
        anchorX.setAccessible(true); anchorY.setAccessible(true); anchorZ.setAccessible(true);
        Method anchor = method(D, "anchor", Terrain.class, int.class, int.class, int.class, long.class, int.class, int.class, boolean.class);
        Method surface = method(D, "surfaceAnchor", Terrain.class, int.class, int.class, int.class, long.class, int.class, int.class, boolean.class);
        for (int i = 0; i < dcell.length; i++) {
            for (int lat = 0; lat < 4; lat++) {
                long s = lat == 0 ? dA[i] : lat == 1 ? dB[i] : lat == 2 ? dC[i] : dD == null ? 0L : dD[i];
                int c = lat < 2 ? dcell[i] : lat == 2 ? dcellC[i] : dcellD == null ? 0 : dcellD[i];
                if (s == 0L || c == 0) continue;
                int mx = (int) Math.floorMod(Terrain.mix(seed + s) >>> 3, (long) c);
                int mz = (int) Math.floorMod(Terrain.mix(seed + s + 17L) >>> 3, (long) c);
                for (int acx = x0 + Math.floorMod(mx - x0, c); acx < x0 + n; acx += c)
                    for (int acz = z0 + Math.floorMod(mz - z0, c); acz < z0 + n; acz += c) {
                        Object a = (dsurf[i] ? surface : anchor).invoke(null, t, acx, acz, c, s, dsx[i], dsz[i], true);
                        if (a == null || anchorX.getInt(a) != acx * 16 + 2 || anchorZ.getInt(a) != acz * 16 + 2) continue;
                        layers.computeIfAbsent("room." + "ABCD".charAt(lat), q -> new ArrayList<>()).add(i + "@" + acx + "," + acz + "," + anchorY.getInt(a));
                        if (lat == 3) {
                            String named = Dungeons.locate(t, acx * 16 + 2 + dsx[i] / 2, anchorY.getInt(a) + 1, acz * 16 + 2 + dsz[i] / 2);
                            if (named == null || !named.contains(anchorX.getInt(a) + "\u0000" + anchorY.getInt(a) + "\u0000" + anchorZ.getInt(a)))
                                counts.merge("ERROR.roomD-not-located", 1, Integer::sum);
                        }
                    }
            }
        }

        // -- catalogue -----------------------------------------------------------------------------------------
        int bx0 = x0 * 16, bz0 = z0 * 16, bx1 = (x0 + n) * 16, bz1 = (z0 + n) * 16;
        for (int grid = 0; grid < 2; grid++) {
            int region = grid == 0 ? StructurePlanner.REGION : StructurePlanner.EXPANSION_REGION;
            for (int rx = Math.floorDiv(bx0, region); rx < Math.floorDiv(bx1, region); rx++)
                for (int rz = Math.floorDiv(bz0, region); rz < Math.floorDiv(bz1, region); rz++) {
                    StructurePlanner.Site site = grid == 0 ? StructurePlanner.region(seed, rx, rz) : StructurePlanner.expansionRegion(seed, rx, rz);
                    if (site == null) continue;
                    layers.computeIfAbsent("catalogue.tier" + site.tier(), q -> new ArrayList<>())
                        .add(site.key + "/" + site.x + "/" + site.y + "/" + site.z);
                }
        }
        // 3.28.0 tier-2 grids (absent from older jars).
        Method tier2 = method(StructurePlanner.class, "tier2Region", long.class, int.class, int.class, boolean.class);
        Method tier2Legacy = method(StructurePlanner.class, "tier2Legacy", long.class, int.class, int.class, int.class);
        for (int grid = 0; grid < 3 && tier2 != null; grid++) {
            if (grid == 2 && tier2Legacy == null) break;
            int region = grid == 1 ? StructurePlanner.EXPANSION_REGION : StructurePlanner.REGION;
            for (int rx = Math.floorDiv(bx0, region); rx < Math.floorDiv(bx1, region); rx++)
                for (int rz = Math.floorDiv(bz0, region); rz < Math.floorDiv(bz1, region); rz++) {
                    StructurePlanner.Site site = (StructurePlanner.Site) (grid == 2 ? tier2Legacy.invoke(null, seed, rx, rz, 1)
                        : tier2.invoke(null, seed, rx, rz, grid == 1));
                    if (site == null) continue;
                    layers.computeIfAbsent("catalogue.tier2." + (grid == 0 ? "legacy" : grid == 1 ? "expansion" : "legacy2"), q -> new ArrayList<>())
                        .add(site.key + "/" + site.x + "/" + site.y + "/" + site.z);
                    boolean named = false;
                    for (StructurePlanner.Site id : StructurePlanner.identify(seed, (site.x + site.width / 2) >> 4, (site.z + 8) >> 4))
                        if (id.key.equals(site.key)) named = true;
                    if (!named) counts.merge("ERROR.catalogue-tier2-not-identified", 1, Integer::sum);
                }
        }
        // Old-grid counts by grid, so their tier-2 share can be calibrated per grid.
        for (String l : new String[]{"catalogue.tier0", "catalogue.tier1"}) for (String r : layers.getOrDefault(l, Collections.<String>emptyList()))
            counts.merge("INFO." + l + (r.startsWith("structures:v1:") ? ".legacy" : ".expansion"), 1, Integer::sum);

        // -- sanctuaries -------------------------------------------------------------------------------------
        Method sanctuary2 = method(StructureRates.class, "sanctuary2", Terrain.class, int.class, int.class);
        for (int cx = x0; cx < x0 + n; cx++) for (int cz = z0; cz < z0 + n; cz++) {
            if (HorrorGenerator.portalChunk(cx, cz)) layers.computeIfAbsent("sanctuary.old", q -> new ArrayList<>()).add(cx + "," + cz);
            else if (StructureRates.sanctuary(t, cx, cz)) layers.computeIfAbsent("sanctuary.new", q -> new ArrayList<>()).add(cx + "," + cz);
            else if (sanctuary2 != null && (Boolean) sanctuary2.invoke(null, t, cx, cz))
                layers.computeIfAbsent("sanctuary.tier2", q -> new ArrayList<>()).add(cx + "," + cz);
        }

        System.out.println("RATES_PROBE seed=" + seed + " box=" + x0 + "," + z0 + "+" + n + " chunks ms=" + (System.currentTimeMillis() - started));
        for (Map.Entry<String, List<String>> e : layers.entrySet()) {
            List<String> rows = new ArrayList<>(e.getValue());
            Collections.sort(rows);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String r : rows) hash.update((r + "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash.digest()) hex.append(String.format("%02x", b & 255));
            System.out.println("LAYER " + e.getKey() + " count=" + rows.size() + " sha256=" + hex.substring(0, 16));
            if (args.length > 4 && args[4].equals("--rows")) for (String r : rows) System.out.println("ROW " + e.getKey() + " " + r);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) System.out.println(e.getKey() + "=" + e.getValue());
        // Per kind, for calibration: KIND <generator> <index> <per-layer counts>.
        for (String gen : new String[]{"setpiece", "room"}) {
            String[] names = gen.equals("setpiece") ? new String[]{"primary", "secondary", "tertiary"} : new String[]{"A", "B", "C", "D"};
            Map<Integer, int[]> per = new TreeMap<>();
            for (int l = 0; l < names.length; l++) {
                List<String> rows = layers.get(gen + "." + names[l]);
                if (rows == null) continue;
                for (String r : rows) per.computeIfAbsent(Integer.parseInt(r.substring(0, r.indexOf('@'))), q -> new int[names.length])[l]++;
            }
            for (Map.Entry<Integer, int[]> e : per.entrySet()) {
                StringBuilder line = new StringBuilder("KIND " + gen + " " + e.getKey());
                for (int v : e.getValue()) line.append(' ').append(v);
                System.out.println(line);
            }
        }
    }
}
