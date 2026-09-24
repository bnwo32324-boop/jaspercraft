package chat.jaspr.biomes;

import java.util.*;

/** Pure placement audit: no Bukkit world, chunks, disk, or mutable server state. */
public final class StructureRarityAudit {
    private static long checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static double percent(long retained,long baseline){return retained*100.0/baseline;}

    public static void main(String[] args) {
        check(Math.abs(StructurePlanner.RELATIVE_STRUCTURE_DENSITY-.10)<1e-12,"Global retention must be exactly 10%");
        check(StructurePlanner.SPAWN_EXCLUSION_RADIUS==3072,"Opening-world exclusion radius");
        final int low=-1000,high=1000;long baseline=(long)(high-low)*(high-low),legacy=0,expansion=0;
        long auditSeed=0x4a61737072437261L;
        for(int x=low;x<high;x++)for(int z=low;z<high;z++){
            if(StructurePlanner.densityAdmitted(auditSeed,x,z,909))legacy++;
            if(StructurePlanner.densityAdmitted(auditSeed,x,z,1909))expansion++;
        }
        double legacyPct=percent(legacy,baseline),expansionPct=percent(expansion,baseline);
        check(legacyPct>=9.90&&legacyPct<=10.10,"Observed legacy retention: "+legacyPct);
        check(expansionPct>=9.90&&expansionPct<=10.10,"Observed expansion retention: "+expansionPct);

        long[] seeds=args.length==0?new long[]{4696544777213599765L,4184677908398476141L,0L,-1L}:new long[args.length];
        for(int i=0;i<args.length;i++)seeds[i]=Long.parseLong(args[i]);
        for(long seed:seeds)auditSeed(seed);
        System.out.printf(Locale.ROOT,"STRUCTURE_RARITY_PASS expectedRetention=10.000%% legacyObserved=%.4f%% expansionObserved=%.4f%% sampledCellsPerGrid=%d spawnExclusion=%d checks=%d%n",
                legacyPct,expansionPct,baseline,StructurePlanner.SPAWN_EXCLUSION_RADIUS,checks);
    }

    private static void auditSeed(long seed){
        final int radius=12288;Map<String,StructurePlanner.Site> sites=new LinkedHashMap<>();
        for(int grid:new int[]{StructurePlanner.REGION,StructurePlanner.EXPANSION_REGION}){
            int low=Math.floorDiv(-radius,grid)-2,high=Math.floorDiv(radius,grid)+2;
            for(int x=low;x<=high;x++)for(int z=low;z<=high;z++){
                StructurePlanner.Site site=grid==StructurePlanner.REGION?StructurePlanner.region(seed,x,z):StructurePlanner.expansionRegion(seed,x,z);
                if(site!=null&&Math.hypot(site.anchorX,site.anchorZ)<=radius)sites.put(site.key,site);
            }
        }
        check(!sites.isEmpty(),"Rarity audit must find occasional distant sites for seed "+seed);
        double nearest=Double.POSITIVE_INFINITY;int surface=0,buried=0,underwater=0;
        for(StructurePlanner.Site site:sites.values()){
            check(!StructurePlanner.intersectsSpawnExclusion(site.x,site.z,site.width,site.depth),"Structure entered protected opening region: "+site.key);
            nearest=Math.min(nearest,envelopeDistance(site));
            if(site.design.mode.equals("surface"))surface++;else if(site.design.mode.equals("buried"))buried++;else underwater++;
            int rx=Math.floorDiv(site.anchorX,site.expansion()?StructurePlanner.EXPANSION_REGION:StructurePlanner.REGION);
            int rz=Math.floorDiv(site.anchorZ,site.expansion()?StructurePlanner.EXPANSION_REGION:StructurePlanner.REGION);
            check(StructurePlanner.densityAdmitted(seed,rx,rz,site.expansion()?1909:909),"Generated site bypassed 10% gate: "+site.key);
        }
        check(nearest>=StructurePlanner.SPAWN_EXCLUSION_RADIUS,"Nearest structure edge violates opening radius");
        System.out.printf(Locale.ROOT,"SEED_RARITY seed=%d radius=%d sites=%d surface=%d buried=%d underwater=%d nearestStructureEdge=%.1f%n",
                seed,radius,sites.size(),surface,buried,underwater,nearest);
    }

    private static double envelopeDistance(StructurePlanner.Site site){
        long right=(long)site.x+site.width-1,bottom=(long)site.z+site.depth-1;
        long dx=site.x>0?site.x:right<0?-right:0,dz=site.z>0?site.z:bottom<0?-bottom:0;
        return Math.hypot(dx,dz);
    }
}
