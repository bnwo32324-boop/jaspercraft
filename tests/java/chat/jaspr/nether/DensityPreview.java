package chat.jaspr.nether;

import java.io.*;
import java.util.*;
import java.util.function.Function;

/** Compare actual accepted sites, after the unchanged mega/GLM collision rules, over identical block areas. */
public final class DensityPreview {
    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        try (InputStream in = new FileInputStream(new File(res, "blocks.tsv"))) { BlockMap.load(in); }
        Blocks.load();
        for (long seed : new long[]{918273645L, 4242L, 1790554876916L}) {
            int before = census(res, seed, true), after = census(res, seed, false);
            System.out.println(String.format(java.util.Locale.ROOT, "DENSITY_RATIO seed=%d before=%d after=%d ratio=%.5f", seed, before, after, (double) after / before));
        }
    }
    static int census(File res, long seed, boolean legacy) throws Exception {
        Function<String, InputStream> resources = n -> {
            if (legacy && n.startsWith("glm/")) n = "glm/legacy/" + n.substring(4);
            try { return new FileInputStream(new File(res, n)); } catch (IOException e) { return null; }
        };
        Biomes biomes = new Biomes(seed);
        Mega[] holder = new Mega[1];
        GlmSites g = new GlmSites(seed, biomes::nex, null, (x0,z0,x1,z1) -> !holder[0].touching(x0,z0,x1,z1).isEmpty(), resources, legacy);
        holder[0] = new Mega(seed, biomes::nex, (x0,z0,x1,z1) -> !g.touching(GlmSites.Tier.LORD,x0,z0,x1,z1).isEmpty()
            || !g.touching(GlmSites.Tier.GREAT,x0,z0,x1,z1).isEmpty());
        int R = 6000, overlaps = 0, hits = 0, disabled = 0;
        List<GlmSites.Site> all = new ArrayList<>();
        Map<String,Integer> tiers = new TreeMap<>(), builds = new TreeMap<>();
        for (GlmSites.Tier t : GlmSites.Tier.values()) {
            int cell = g.cell(t);
            for (int x = Math.floorDiv(-R,cell); x <= Math.floorDiv(R,cell); x++)
                for (int z = Math.floorDiv(-R,cell); z <= Math.floorDiv(R,cell); z++) {
                    GlmSites.Site s = g.site(t,x,z);
                    if (s == null || s.x < -R || s.x >= R || s.z < -R || s.z >= R) continue;
                    all.add(s); tiers.merge(t.name(),1,Integer::sum); builds.merge(s.e.key,1,Integer::sum);
                    if (!g.generates(s.e.key)) disabled++;
                    if (!holder[0].touching(s.minX,s.minZ,s.maxX,s.maxZ).isEmpty()) hits++;
                }
        }
        all.sort(Comparator.comparingInt(s -> s.minX));
        for (int i=0;i<all.size();i++) for (int j=i+1;j<all.size() && all.get(j).minX<=all.get(i).maxX;j++)
            if (all.get(j).maxZ>=all.get(i).minZ && all.get(j).minZ<=all.get(i).maxZ) overlaps++;
        System.out.println("DENSITY_CENSUS layout="+(legacy?1:2)+" seed="+seed+" areaBlocks="+(2L*R*2L*R)
            +" sites="+all.size()+" tiers="+tiers+" builds="+builds.size()+" overlaps="+overlaps+" megaHits="+hits+" disabled="+disabled);
        for (GlmSites.Entry e : g.byKey.values()) if (Math.max(e.cavW,e.cavD)+17>g.cell(e.tier)) throw new AssertionError("cannot fit "+e.key);
        if (overlaps != 0 || hits != 0 || disabled != 0) throw new AssertionError("unsafe layout");
        return all.size();
    }
}
