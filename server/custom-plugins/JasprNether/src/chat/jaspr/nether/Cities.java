package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * BetterNether's big Nether cities (CityStructureManager / CityGenerator / StructureCityBuilding): one city per
 * 80 x 80-chunk grid cell at y 40, a centre building, 2-5 rings of buildings attached through their connector data
 * blocks, road ends closing the open connectors, all inside a carved, noise-warped cave with a lava lake below y 31.
 * The plan is deterministic; each populated chunk carves and builds only its own decoration box, as BN did.
 */
final class Cities {
    static final int GRID = 80;
    private static final long A = 341873128712L, B = 132897987541L;
    private static final String[] CENTERS = {"bn_city_center_01", "bn_city_center_02"};
    private static final String[] BUILDINGS = {"bn_city_library_01", "bn_city_tower_01", "bn_city_tower_02", "bn_city_building_01",
        "bn_city_building_02", "bn_city_building_03", "bn_city_building_04", "bn_city_building_05", "bn_city_building_06",
        "bn_city_building_07", "bn_city_building_08", "bn_city_building_09", "bn_city_building_10", "bn_city_enchanter_01", "bn_city_hall"};
    private static final int[][] DIR = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}; // east, south, west, north

    static final class Bld {
        final Template t; final int rot, yOff, w, d; final int[][] ends; final int[] dirs;
        Bld(Template t, int rot, int yOff) {
            this.t = t; this.rot = rot; this.yOff = yOff; w = t.width(rot); d = t.depth(rot);
            List<int[]> e = new ArrayList<>();
            List<Integer> dl = new ArrayList<>();
            int cx = t.sx >> 1, cz = t.sz >> 1;
            for (Template.Marker m : t.markers) {
                if (!m.type.equals("end")) continue;
                e.add(new int[]{t.rx(m.x, m.z, rot), m.y, t.rz(m.x, m.z, rot)});
                int px = m.x - cx, pz = m.z - cz, dir;
                if (Math.max(Math.abs(px), Math.abs(pz)) == Math.abs(px)) dir = px > 0 ? 0 : 2; else dir = pz > 0 ? 1 : 3;
                dl.add((dir + rot) & 3);
            }
            ends = e.toArray(new int[0][]);
            dirs = new int[dl.size()];
            for (int i = 0; i < dirs.length; i++) dirs[i] = dl.get(i);
        }
        int[] outward(int i) { return new int[]{ends[i][0] + DIR[dirs[i]][0], ends[i][1], ends[i][2] + DIR[dirs[i]][1]}; }
    }

    static final class Part { final Bld b; final int x, y, z; Part(Bld b, int x, int y, int z) { this.b = b; this.x = x; this.y = y; this.z = z; } }
    static final class Cave { final int x, z, r; Cave(int x, int z, int r) { this.x = x; this.z = z; this.r = r; } }

    static final class City {
        int cellX, cellZ, chunkX, chunkZ, x, z;
        final List<Part> parts = new ArrayList<>();
        final List<Cave> caves = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        void extend(int x0, int z0, int x1, int z1) { minX = Math.min(minX, x0); minZ = Math.min(minZ, z0); maxX = Math.max(maxX, x1); maxZ = Math.max(maxZ, z1); }
    }

    private final Gen g;
    private final List<Bld> centers = new ArrayList<>(), buildings = new ArrayList<>(), roadEnds = new ArrayList<>();
    private final BnNoise.WorleyOctaved nX, nY, nZ;
    private final Map<Long, City> cache = new LinkedHashMap<Long, City>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, City> e) { return size() > 64; }
    };

    Cities(Gen g) {
        this.g = g;
        for (String n : CENTERS) for (int r = 0; r < 4; r++) centers.add(new Bld(g.t(n), r, -10));
        for (String n : BUILDINGS) for (int r = 0; r < 4; r++) buildings.add(new Bld(g.t(n), r, 0));
        for (int r = 0; r < 4; r++) roadEnds.add(new Bld(g.t("bn_city_road_end_01"), r, 0));
        for (int r = 0; r < 4; r++) roadEnds.add(new Bld(g.t("bn_city_road_end_02"), r, -2));
        Random seeds = new Random(g.seed);
        nX = new BnNoise.WorleyOctaved(seeds.nextLong());
        nY = new BnNoise.WorleyOctaved(seeds.nextLong());
        nZ = new BnNoise.WorleyOctaved(seeds.nextLong());
    }

    synchronized City city(int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        City c = cache.get(key);
        if (c != null) return c;
        c = plan(cellX, cellZ);
        cache.put(key, c);
        return c;
    }

    /** Nearest city centre for a chunk (for /jnether and the self-test). */
    City nearest(int chunkX, int chunkZ) {
        return city(Math.floorDiv(chunkX, GRID), Math.floorDiv(chunkZ, GRID));
    }

    private City plan(int cellX, int cellZ) {
        City c = new City();
        c.cellX = cellX; c.cellZ = cellZ;
        Random rnd = new Random(cellX * A + cellZ * B + g.seed + 0x43495459L);
        int variance = GRID / 3;
        c.chunkX = cellX * GRID + rnd.nextInt(variance) + variance;
        c.chunkZ = cellZ * GRID + rnd.nextInt(variance) + variance;
        c.x = c.chunkX << 4; c.z = c.chunkZ << 4;
        Random random = new Random(c.chunkX * A + c.chunkZ * B ^ g.seed);
        List<int[]> bounds = new ArrayList<>();
        List<int[]> ends = new ArrayList<>();
        Bld center = centers.get(random.nextInt(centers.size()));
        bounds.add(new int[]{c.x, c.z, c.x + center.w, c.z + center.d});
        c.parts.add(new Part(center, c.x, 40 + center.yOff, c.z));
        for (int i = 0; i < center.ends.length; i++) {
            int[] o = center.outward(i);
            ends.add(new int[]{c.x + o[0], 40 + center.yOff + o[1], c.z + o[2]});
        }
        for (int i = 0; i < 2 + random.nextInt(4); i++) {
            List<int[]> add = new ArrayList<>(), rem = new ArrayList<>();
            for (int[] pos : ends) {
                boolean generate = true;
                for (int n = 0; n < 8 && generate; n++) {
                    int b = random.nextInt(buildings.size() >> 2) << 2;
                    for (int r = 0; r < 4 && generate; r++) {
                        Bld bld = buildings.get(b | r);
                        if (bld.ends.length == 0) continue;
                        int index = random.nextInt(bld.ends.length);
                        int[] off = bld.ends[index];
                        int[] bb = {pos[0] - off[0], pos[2] - off[2], pos[0] - off[0] + bld.w, pos[2] - off[2] + bld.d};
                        if (!free(bounds, bb)) continue;
                        int ny = pos[1] - off[1] + bld.yOff;
                        bounds.add(bb);
                        rem.add(pos);
                        for (int e = 0; e < bld.ends.length; e++) if (e != index) {
                            int[] o = bld.outward(e);
                            add.add(new int[]{bb[0] + o[0], ny + o[1], bb[1] + o[2]});
                        }
                        c.parts.add(new Part(bld, bb[0], ny, bb[1]));
                        generate = false;
                    }
                }
            }
            ends.removeAll(rem);
            ends.addAll(add);
        }
        for (int[] pos : ends) {
            for (Bld bld : roadEnds) {
                int[] off = bld.ends[0];
                int[] bb = {pos[0] - off[0], pos[2] - off[2], pos[0] - off[0] + bld.w, pos[2] - off[2] + bld.d};
                if (!free(bounds, bb)) continue;
                bounds.add(bb);
                c.parts.add(new Part(bld, bb[0], pos[1] - off[1] + bld.yOff, bb[1]));
                break;
            }
        }
        // caves: the main one sized from the city's side, one per building sized from its footprint
        Part first = c.parts.get(0);
        int minX = first.x, maxX = first.x + first.b.w, minZ = first.z, maxZ = first.z + first.b.d;
        for (Part p : c.parts) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x + p.b.w);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z); // BN's getCitySide uses minZ here (kept)
        }
        int side = Math.max(maxX - minX, maxZ - minZ);
        c.caves.add(new Cave(c.x, c.z, (int) (side * 0.6) + random.nextInt(50)));
        for (Part p : c.parts) c.caves.add(new Cave(p.x + (p.b.w >> 1), p.z + (p.b.d >> 1), Math.max(p.b.w, p.b.d)));
        for (Part p : c.parts) c.extend(p.x, p.z, p.x + p.b.w - 1, p.z + p.b.d - 1);
        for (Cave v : c.caves) { int b = (int) (v.r * 1.5) + 24; c.extend(v.x - b, v.z - b, v.x + b, v.z + b); }
        return c;
    }

    private static boolean free(List<int[]> bounds, int[] bb) {
        for (int[] b : bounds) if (bb[0] < b[2] && b[0] < bb[2] && bb[1] < b[3] && b[1] < bb[3]) return false;
        return true;
    }

    void populate(Area a, Gen.Post post) {
        int x0 = a.ox + 8, z0 = a.oz + 8, x1 = x0 + 15, z1 = z0 + 15;
        int cellX = Math.floorDiv(a.cx, GRID), cellZ = Math.floorDiv(a.cz, GRID);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            City c = city(cellX + dx, cellZ + dz);
            if (c.maxX < x0 || c.minX > x1 || c.maxZ < z0 || c.minZ > z1) continue;
            Boolean active = g.registry.cityDecision(c.cellX, c.cellZ);
            if (active == null) {
                boolean centreHere = a.cx == c.chunkX && a.cz == c.chunkZ;
                active = centreHere || !g.world.isChunkGenerated(c.chunkX, c.chunkZ);
                g.registry.setCityDecision(c.cellX, c.cellZ, active);
                if (active) {
                    int bx0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE;
                    for (Part p : c.parts) { bx0 = Math.min(bx0, p.x); bz0 = Math.min(bz0, p.z); bx1 = Math.max(bx1, p.x + p.b.w - 1); bz1 = Math.max(bz1, p.z + p.b.d - 1); }
                    g.registry.add("city", "nether_city", bx0, 20, bz0, bx1, 80, bz1);
                    g.count("bn_city");
                }
            }
            if (!active) continue;
            build(a, c, x0, z0, x1, z1);
        }
    }

    private void build(Area a, City c, int x0, int z0, int x1, int z1) {
        for (Cave v : c.caves) carve(a, v, x0, z0, x1, z1);
        for (Part p : c.parts) {
            if (p.x > x1 || p.x + p.b.w - 1 < x0 || p.z > z1 || p.z + p.b.d - 1 < z0) continue;
            placeClipped(a, p, x0, z0, x1, z1);
            // BN: under every full block of the ground layer, extend it down to the next full block (pillars)
            for (int x = Math.max(x0, p.x); x <= Math.min(x1, p.x + p.b.w - 1); x++)
                for (int z = Math.max(z0, p.z); z <= Math.min(z1, p.z + p.b.d - 1); z++) {
                    int v = a.get(x, p.y, z);
                    if (!Blocks.isFullSolid(v >> 4)) continue;
                    int d = 1;
                    while (d < p.y - 5 && !Blocks.isFullSolid(a.id(x, p.y - d, z))) d++;
                    for (int y = 1; y < d; y++) a.set(x, p.y - y, z, v);
                }
        }
    }

    private void placeClipped(Area a, Part p, int x0, int z0, int x1, int z1) {
        Template t = p.b.t;
        int rot = p.b.rot;
        for (int y = 0; y < t.sy; y++) {
            int wy = p.y + y;
            if (wy < 1 || wy >= Area.H) continue;
            for (int z = 0; z < t.sz; z++) for (int x = 0; x < t.sx; x++) {
                int pi = t.data[(y * t.sz + z) * t.sx + x] & 255;
                if (pi == 255 || t.palette[pi] == BlockMap.VOID) continue;
                int wx = p.x + t.rx(x, z, rot), wz = p.z + t.rz(x, z, rot);
                if (wx < x0 || wx > x1 || wz < z0 || wz > z1) continue;
                a.set(wx, wy, wz, Template.rotate(t.palette[pi], rot));
            }
        }
    }

    private void carve(Area a, Cave v, int x0, int z0, int x1, int z1) {
        int bounds = (int) (v.r * 1.5);
        double radius = v.r * 0.8, rr = radius * radius;
        if (v.x + bounds + 30 < x0 || v.x - bounds - 30 > x1 || v.z + bounds + 30 < z0 || v.z - bounds - 30 > z1) return;
        // y range the squashed sphere can reach (|2y + noise| < r, noise in [0, ~28])
        int yLo = Math.max(5, 40 + (int) Math.floor((-radius - 30) / 2)), yHi = Math.min(125, 40 + (int) Math.ceil(radius / 2) + 1);
        if (yLo > yHi) return;
        int ny = yHi - yLo + 1;
        double[] warpX = new double[16 * ny], warpZ = new double[16 * ny];
        for (int i = 0; i < 16; i++) for (int j = 0; j < ny; j++) {
            int y = yLo + j - 40;
            warpX[i * ny + j] = nX.value(y * 2 * 0.02, (z0 + i - v.z) * 0.02) * 20;
            warpZ[i * ny + j] = nZ.value(y * 0.02, (x0 + i - v.x) * 0.02) * 20;
        }
        for (int wx = x0; wx <= x1; wx++) {
            int x = wx - v.x;
            for (int wz = z0; wz <= z1; wz++) {
                int z = wz - v.z;
                double nyv = nY.value(x * 0.02, z * 0.02) * 20;
                for (int wy = yLo; wy <= yHi; wy++) {
                    int y = wy - 40;
                    double dy = y * 2 + nyv;
                    if (dy * dy >= rr) continue;
                    double dx = x + warpX[(wz - z0) * ny + (wy - yLo)];
                    double dz = z + warpZ[(wx - x0) * ny + (wy - yLo)];
                    if (dx * dx + dy * dy + dz * dz < rr) {
                        int id = a.id(wx, wy, wz);
                        if (id == Blocks.BEDROCK) continue;
                        a.set(wx, wy, wz, wy > 31 ? 0 : Blocks.LAVA, 0);
                    }
                }
            }
        }
    }
}
