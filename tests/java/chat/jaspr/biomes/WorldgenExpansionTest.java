package chat.jaspr.biomes;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class WorldgenExpansionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        long seed=4425965048829651136L;int legacy=0,extra=0,surface=0,majorSurface=0,buried=0,underwater=0;Map<String,Integer> families=new TreeMap<>();
        check(Math.abs(StructurePlanner.RELATIVE_STRUCTURE_DENSITY-.10)<1e-12,"Structure update must retain exactly 10% relative candidate probability");
        for(int x=-6;x<6;x++)for(int z=-6;z<6;z++)if(StructurePlanner.region(seed,x,z)!=null)legacy++;
        List<StructurePlanner.Site> samples=new ArrayList<>();
        for(int x=-32;x<32;x++)for(int z=-32;z<32;z++){
            StructurePlanner.Site site=StructurePlanner.expansionRegion(seed,x,z);if(site==null)continue;extra++;samples.add(site);
            if(site.design.mode.equals("surface")){surface++;if(site.design.tier>=StructurePlanner.SURFACE_LANDMARK_TIER)majorSurface++;}
            else if(site.design.mode.equals("buried"))buried++;else underwater++;
            families.put(site.design.family,families.getOrDefault(site.design.family,0)+1);
            check(site.design.accepts(new Terrain(seed).sample(site.anchorX,site.anchorZ).profile.index),"Correct biome");
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0){
                StructurePlanner.Site next=StructurePlanner.expansionRegion(seed,x+dx,z+dz);
                if(next!=null){check(!overlap(site,next),"Extra sites overlap");check(Math.hypot(site.anchorX-next.anchorX,site.anchorZ-next.anchorZ)>=StructurePlanner.EXPANSION_MIN_SPACING,"Extra spacing");}
            }
            int rx=Math.floorDiv(site.anchorX,StructurePlanner.REGION),rz=Math.floorDiv(site.anchorZ,StructurePlanner.REGION);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                StructurePlanner.Site old=StructurePlanner.region(seed,rx+dx,rz+dz);check(old==null||!overlap(site,old),"Legacy site overwritten");
            }
        }
        check(legacy>0&&extra>=legacy*3,"Sparse tiers remain discoverable: "+legacy+" old + "+extra+" new");
        check(surface*5>=extra*4,"Surface sites must dominate discovery");
        check(majorSurface*4>=extra*3,"Most sites must be unmistakable major surface landmarks");
        check(buried>0&&underwater>0,"Sparse grid must retain buried and underwater dungeons");check(families.size()>=12,"Varied families");
        check(StructurePlanner.cachedExpansionRegions()<=StructurePlanner.CACHE_LIMIT,"Bounded expansion cache");
        boundary(seed,samples.get(0));
        System.out.println("WORLDGEN_EXPANSION_PASS legacy="+legacy+" added="+extra+" surface="+surface+" majorSurface="+majorSurface+" buried="+buried+" underwater="+underwater+" relativeDensity="+StructurePlanner.RELATIVE_STRUCTURE_DENSITY+" areaBlocks=150994944 tierRatio="+((legacy+extra)/(double)legacy)+" families="+families+" checks="+checks);
    }
    private static boolean overlap(StructurePlanner.Site a,StructurePlanner.Site b){return a.x<(long)b.x+b.width&&a.x+(long)a.width>b.x&&a.z<(long)b.z+b.depth&&a.z+(long)a.depth>b.z;}
    private static void boundary(long seed,StructurePlanner.Site site)throws Exception {
        Path root=Files.createTempDirectory("jaspr-boundary-");Files.createDirectories(root.resolve("region"));
        int cx=Math.floorDiv(site.x+site.width-1,16),cz=Math.floorDiv(site.z+site.depth-1,16);region(root,cx,cz);
        WorldgenExpansion.Boundary first=WorldgenExpansion.open(root.toFile(),seed);check(first.protectedChunks()==1,"All occupied chunk headers captured");check(first.contains(cx,cz),"Negative-coordinate snapshot");check(!first.permits(site),"Whole site excluded even when only far approach overlaps old terrain");
        byte[] saved=Files.readAllBytes(root.resolve(WorldgenExpansion.FILE_NAME));region(root,0,0);
        WorldgenExpansion.Boundary second=WorldgenExpansion.open(root.toFile(),seed);check(second.protectedChunks()==1&&!second.contains(0,0),"Boundary immutable on later chunks and restart");check(Arrays.equals(saved,Files.readAllBytes(root.resolve(WorldgenExpansion.FILE_NAME))),"No boundary rewrite");
        try{WorldgenExpansion.open(root.toFile(),seed+1);throw new AssertionError("seed mismatch accepted");}catch(IOException expected){checks++;}
        saved[17]^=1;Files.write(root.resolve(WorldgenExpansion.FILE_NAME),saved);
        try{WorldgenExpansion.open(root.toFile(),seed);throw new AssertionError("corruption accepted");}catch(IOException expected){checks++;}
        Path fresh=Files.createTempDirectory("jaspr-boundary-fresh-");check(WorldgenExpansion.open(fresh.toFile(),seed).permits(site),"New/reset world has all expansion sites enabled");
    }
    private static void region(Path root,int cx,int cz)throws Exception {
        File region=root.resolve("region").resolve("r."+Math.floorDiv(cx,32)+"."+Math.floorDiv(cz,32)+".mca").toFile();
        try(RandomAccessFile file=new RandomAccessFile(region,"rw")){file.setLength(12288);file.seek((Math.floorMod(cz,32)*32+Math.floorMod(cx,32))*4L);file.writeInt((2<<8)|1);}
    }
}
