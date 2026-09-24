package chat.jaspr.biomes;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.craftbukkit.v1_12_R1.generator.CraftChunkData;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;

/** Read-only production audit plus offline generation regression. No server is started.
 * Compile against the deployed plugin and patched Paper jar, then run from a temporary
 * working directory with the explicit project root as the sole argument. Re-run with
 * freshly compiled source ahead of the plugin on the classpath to compare source.
 * All production streams are read-only. Bukkit proxies reject block/chunk/server access.
 * The boundary is parsed with the private read-only reader, never open/initialize on disk.
 */
public final class SurfaceDiscoveryAudit_20260908_7f4c91 {
    private static int checks;
    private static final Map<Long, byte[]> chunks = new HashMap<>();
    private static final Map<Long, NBTTagCompound> decoded = new HashMap<>();
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
    private static NBTTagCompound nbt(byte[] bytes, int compression) throws Exception {
        InputStream in = new ByteArrayInputStream(bytes);
        if (compression == 1) in = new GZIPInputStream(in);
        else if (compression == 2) in = new InflaterInputStream(in);
        else throw new IOException("Unsupported chunk compression " + compression);
        try (DataInputStream data = new DataInputStream(in)) {
            return NBTCompressedStreamTools.a(data);
        }
    }
    private static void snapshot(Path region) throws Exception {
        int empty = 0, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(region, "r.*.*.mca")) {
            for (Path file : files) {
                // One read per file: subsequent inspection uses this in-memory snapshot.
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length == 0) { empty++; continue; }
                check(bytes.length >= 8192, "Anvil header: " + file);
                String[] parts = file.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                DataInputStream header = new DataInputStream(new ByteArrayInputStream(bytes));
                for (int i = 0; i < 1024; i++) {
                    int loc = header.readInt();
                    if (loc == 0) continue;
                    int offset = loc >>> 8, sectors = loc & 255;
                    check(offset >= 2 && sectors > 0 && (long)(offset + sectors)*4096 <= bytes.length, "Valid Anvil location");
                    DataInputStream payload = new DataInputStream(new ByteArrayInputStream(bytes, offset*4096, sectors*4096));
                    int length = payload.readInt();
                    check(length > 1 && length <= sectors*4096 - 4, "Valid Anvil payload");
                    byte[] saved = new byte[length]; payload.readFully(saved);
                    int cx = rx*32+i%32, cz = rz*32+i/32;
                    chunks.put(key(cx, cz), saved);
                    minX=Math.min(minX,cx); maxX=Math.max(maxX,cx); minZ=Math.min(minZ,cz); maxZ=Math.max(maxZ,cz);
                }
            }
        }
        System.out.printf("SAVED_CHUNKS count=%d chunkBounds=%d,%d..%d,%d emptyRegionFiles=%d%n", chunks.size(),minX,minZ,maxX,maxZ,empty);
    }
    private static NBTTagCompound chunk(int cx, int cz) throws Exception {
        long k = key(cx,cz);
        if (decoded.containsKey(k)) return decoded.get(k);
        byte[] bytes = chunks.get(k);
        if (bytes == null) return null;
        NBTTagCompound value = nbt(Arrays.copyOfRange(bytes,1,bytes.length),bytes[0]).getCompound("Level");
        check(value.getInt("xPos")==cx && value.getInt("zPos")==cz, "Saved chunk coordinates");
        decoded.put(k,value); return value;
    }
    private static int block(NBTTagCompound chunk, int x, int y, int z) {
        NBTTagList sections = chunk.getList("Sections",10);
        for (int n=0;n<sections.size();n++) {
            NBTTagCompound section=sections.get(n);
            if ((section.getByte("Y")&255)!=(y>>4)) continue;
            int i=((y&15)<<8)|((z&15)<<4)|(x&15);
            byte[] ids=section.getByteArray("Blocks"), add=section.getByteArray("Add");
            int high=add.length==0?0:((add[i>>1]>>((i&1)*4))&15)<<8;
            return (ids[i]&255)|high;
        }
        return 0;
    }
    private static int savedIn(StructurePlanner.Site s, boolean roomsOnly) {
        Set<Long> cells=new HashSet<>();
        if (roomsOnly) {
            for (StructurePlanner.Room r:s.rooms) {
                int x=s.x+4+r.col*12,z=s.z+4+r.row*12;
                for(int cx=Math.floorDiv(x,16);cx<=Math.floorDiv(x+11,16);cx++)
                    for(int cz=Math.floorDiv(z,16);cz<=Math.floorDiv(z+11,16);cz++) cells.add(key(cx,cz));
            }
        } else {
            for(int cx=Math.floorDiv(s.x,16);cx<=Math.floorDiv(s.x+s.width-1,16);cx++)
                for(int cz=Math.floorDiv(s.z,16);cz<=Math.floorDiv(s.z+s.depth-1,16);cz++) cells.add(key(cx,cz));
        }
        cells.retainAll(chunks.keySet()); return cells.size();
    }
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--prevalence")) { prevalence(); return; }
        if(args.length!=1) throw new IllegalArgumentException("Explicit project root required");
        Path root=Paths.get(args[0]).toRealPath(), folder=root.resolve("server/world");
        byte[] levelBytes=Files.readAllBytes(folder.resolve("level.dat"));
        NBTTagCompound level=nbt(levelBytes,1).getCompound("Data");
        long seed=level.getLong("RandomSeed");
        int sx=level.getInt("SpawnX"),sz=level.getInt("SpawnZ");
        System.out.printf("WORLD seed=%d spawn=%d,%d,%d generator=%s levelSHA256=%s%n",seed,sx,level.getInt("SpawnY"),sz,level.getString("generatorName"),hash(levelBytes));
        Path boundaryFile=folder.resolve(WorldgenExpansion.FILE_NAME);
        byte[] boundaryBefore=Files.readAllBytes(boundaryFile);
        Method read=WorldgenExpansion.class.getDeclaredMethod("read",File.class,long.class); read.setAccessible(true);
        WorldgenExpansion.Boundary boundary=(WorldgenExpansion.Boundary)read.invoke(null,boundaryFile.toFile(),seed);
        System.out.println("BOUNDARY protectedChunks="+boundary.protectedChunks()+" sha256="+hash(boundaryBefore));
        System.out.println("PLUGIN sha256="+hash(Files.readAllBytes(root.resolve("server/plugins/JasprHorrorBiomes.jar"))));
        snapshot(folder.resolve("region"));
        List<StructurePlanner.Site> sites=new ArrayList<>();
        final int radius=3072;
        for(int grid:new int[]{StructurePlanner.REGION,StructurePlanner.EXPANSION_REGION})
            for(int rx=Math.floorDiv(sx-radius,grid)-1;rx<=Math.floorDiv(sx+radius,grid)+1;rx++)
                for(int rz=Math.floorDiv(sz-radius,grid)-1;rz<=Math.floorDiv(sz+radius,grid)+1;rz++) {
                    StructurePlanner.Site s=grid==StructurePlanner.REGION?StructurePlanner.region(seed,rx,rz):StructurePlanner.expansionRegion(seed,rx,rz);
                    if(s!=null && Math.hypot(s.anchorX-sx,s.anchorZ-sz)<=radius) sites.add(s);
                }
        sites.sort(Comparator.comparingDouble(s->Math.hypot(s.anchorX-sx,s.anchorZ-sz)));
        Map<String,Integer> modes=new TreeMap<>(); int blocked=0;
        for(StructurePlanner.Site s:sites) { modes.put(s.design.mode,modes.getOrDefault(s.design.mode,0)+1); if(!boundary.permits(s))blocked++; }
        System.out.println("PLANS radius="+radius+" modes="+modes+" boundaryRejected="+blocked);
        for(int i=0;i<Math.min(12,sites.size());i++) printSite("NEAREST_ANY",sites.get(i),sx,sz,boundary);
        List<StructurePlanner.Site> surface=new ArrayList<>();
        for(StructurePlanner.Site s:sites) if(s.design.mode.equals("surface")&&boundary.permits(s)) surface.add(s);
        check(surface.size()>=8,"Surface plans exist around production spawn");
        for(int i=0;i<Math.min(10,surface.size());i++) printSite("NEAREST_SURFACE",surface.get(i),sx,sz,boundary);
        boolean v4=sites.stream().anyMatch(s->s.key.startsWith("structures:v4:"));
        System.out.println("PLANNER_VERSION="+(v4?"v4":"v3"));
        verifyVersion(sites,v4);
        if(!v4 && seed==4425965048829651136L && sx==0 && sz==0) {
            // Pin the reported discovery gap, which wide-area density tests do not measure.
            long nearby=sites.stream().filter(s->Math.hypot(s.anchorX,s.anchorZ)<300).count();
            long nearbySurface=surface.stream().filter(s->Math.hypot(s.anchorX,s.anchorZ)<300).count();
            check(nearby==4 && nearbySurface==1,"Production seed near-spawn plan baseline");
            check(surface.get(0).design.id.equals("school_broadcast_booth") && surface.get(0).entryX()==-180 && surface.get(0).entryZ()==155,"Nearest surface entrance baseline");
            System.out.println("DISCOVERY_BASELINE radius=300 allSites="+nearby+" surfaceSites="+nearbySurface);
        }

        // An impossible on-disk path plus a seeded in-memory boundary prevents any world writes.
        final File synthetic=new File("READ_ONLY_AUDIT_NO_WORLD");
        check(!synthetic.exists(),"Synthetic world path must not exist");
        Field worlds=WorldgenExpansion.class.getDeclaredField("WORLDS"); worlds.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,WorldgenExpansion.Boundary> worldMap=(Map<String,WorldgenExpansion.Boundary>)worlds.get(null);
        worldMap.put(synthetic.getCanonicalPath(),boundary);
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->{
            switch(m.getName()) {
                case "getSeed":return seed; case "getMaxHeight":return 256; case "getWorldFolder":return synthetic;
                default:throw new AssertionError("Forbidden World API: "+m.getName());
            }
        });
        Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(p,m,a)->{
            if(m.getName().equals("createChunkData"))return new CraftChunkData((World)a[0]);
            throw new AssertionError("Forbidden Server API: "+m.getName());
        });
        // Avoid Bukkit.setServer's startup logging: this is a single-use offline JVM.
        Field singleton=Bukkit.class.getDeclaredField("server"); singleton.setAccessible(true); singleton.set(null,server);
        HorrorGenerator generator=new HorrorGenerator(); int markerChecks=0,savedMarkers=0,savedChests=0;
        StructurePlanner.Site nearest=surface.get(0); Terrain terrain=new Terrain(seed);
        int roofsSaved=0,roofsMatch=0,roofsAboveNaturalGround=0;
        for(StructurePlanner.Room room:nearest.rooms) {
            int x=nearest.x+4+room.col*12+6,z=nearest.z+4+room.row*12+6;
            int y=room.floor+StructureCatalog.floors(room.type)*6;
            if(StructureCatalog.open(room.type))continue;
            ChunkGenerator.ChunkData generated=generator.generateChunkData(world,new Random(1),x>>4,z>>4,new Grid());
            int expected=generated.getTypeId(x&15,y,z&15);
            check(expected!=0,"Nearest surface room has a roof");
            NBTTagCompound saved=chunk(x>>4,z>>4);
            if(saved!=null) {
                roofsSaved++;
                int actual=block(saved,x,y,z);
                if(actual==expected) { roofsMatch++;if(y>terrain.sample(x,z).y)roofsAboveNaturalGround++; }
                System.out.printf("SAVED_ROOF x=%d y=%d z=%d naturalGround=%d expectedId=%d actualId=%d%n",x,y,z,terrain.sample(x,z).y,expected,actual);
            }
        }
        System.out.printf("NEAREST_SURFACE_ROOFS saved=%d matching=%d aboveNaturalGround=%d%n",roofsSaved,roofsMatch,roofsAboveNaturalGround);
        for(StructurePlanner.Site s:surface.subList(0,8)) {
            Map<Long,ChunkGenerator.ChunkData> generated=new HashMap<>();
            for(StructurePlanner.Marker marker:s.markers()) {
                if(!StructureLoot.isLoot(marker.kind))continue;
                int cx=marker.x>>4,cz=marker.z>>4;long k=key(cx,cz);
                ChunkGenerator.ChunkData data=generated.get(k);
                if(data==null) {
                    data=generator.generateChunkData(world,new Random(1),cx,cz,new Grid()); generated.put(k,data);
                    ChunkGenerator.ChunkData repeat=generator.generateChunkData(world,new Random(991),cx,cz,new Grid());
                    check(data.getTypeId(marker.x&15,marker.y,marker.z&15)==repeat.getTypeId(marker.x&15,marker.y,marker.z&15),"Random argument independence");
                }
                check(data.getTypeId(marker.x&15,marker.y,marker.z&15)==54,"Real generation path stamps chest: "+s.key);
                check(data.getTypeId(marker.x&15,marker.y+1,marker.z&15)==0,"Chest lid clearance: "+s.key);
                markerChecks++;
                NBTTagCompound saved=chunk(cx,cz);
                if(saved!=null) { savedMarkers++;if(block(saved,marker.x,marker.y,marker.z)==54)savedChests++; }
            }
        }
        int populated=0,allSavedChests=0;
        for(long k:chunks.keySet()) {
            NBTTagCompound saved=chunk((int)(k>>32),(int)k);
            if(saved.getBoolean("TerrainPopulated"))populated++;
            NBTTagList sections=saved.getList("Sections",10);
            for(int n=0;n<sections.size();n++)for(byte id:sections.get(n).getByteArray("Blocks"))if((id&255)==54)allSavedChests++;
        }
        System.out.printf("SAVED_EVIDENCE populated=%d totalChunks=%d chestBlocks=%d nearestSurfaceSavedMarkers=%d matchingChests=%d%n",populated,chunks.size(),allSavedChests,savedMarkers,savedChests);
        check(!synthetic.exists(),"No synthetic world was created");
        check(Arrays.equals(boundaryBefore,Files.readAllBytes(boundaryFile)),"Production boundary unchanged");
        System.out.printf("SURFACE_DISCOVERY_AUDIT_PASS checks=%d generatedLootMarkers=%d realWorldChunkCalls=0%n",checks,markerChecks);
        System.out.println("LIMIT: persisted files are a live, non-atomic snapshot; absent chunks may exist unsaved in server memory.");
    }
    private static void verifyVersion(List<StructurePlanner.Site> sites,boolean v4) {
        for(StructurePlanner.Site s:sites) if(s.expansion()) {
            check(s.key.startsWith(v4?"structures:v4:":"structures:v3:"),"Expansion namespace agrees with planner version");
            if(v4 && Math.floorMod(Math.floorDiv(s.anchorX,StructurePlanner.EXPANSION_REGION)+2*Math.floorDiv(s.anchorZ,StructurePlanner.EXPANSION_REGION),4)!=0) {
                check(s.design.mode.equals("surface") && s.design.tier>=3,"Accepted v4 landmark cell must be major surface architecture");
            }
        }
    }
    /** Fresh-world planning sample; no World objects, disk reads, generation or boundary writes.
     * Surface means catalog mode, not measured visibility through foliage or terrain.
     * Distances are horizontal distances to entrances, not walking times or path lengths.
     */
    private static void prevalence() {
        long[] seeds={4425965048829651136L,0L,1L,-1L,42L,8675309L,Long.MIN_VALUE,Long.MAX_VALUE};
        int radius=3072,total=0,surfaces=0,majors=0,near300=0,near512=0;
        double maxSpawn=0; List<Double> distances=new ArrayList<>();
        for(long seed:seeds) {
            List<StructurePlanner.Site> sites=new ArrayList<>();
            for(int grid:new int[]{StructurePlanner.REGION,StructurePlanner.EXPANSION_REGION})
                for(int rx=Math.floorDiv(-radius,grid)-1;rx<=Math.floorDiv(radius,grid)+1;rx++)
                    for(int rz=Math.floorDiv(-radius,grid)-1;rz<=Math.floorDiv(radius,grid)+1;rz++) {
                        StructurePlanner.Site s=grid==StructurePlanner.REGION?StructurePlanner.region(seed,rx,rz):StructurePlanner.expansionRegion(seed,rx,rz);
                        if(s!=null && Math.hypot(s.anchorX,s.anchorZ)<=radius)sites.add(s);
                    }
            boolean v4=sites.stream().anyMatch(s->s.key.startsWith("structures:v4:"));
            verifyVersion(sites,v4);
            List<StructurePlanner.Site> surface=new ArrayList<>(); int major=0,n300=0,n512=0;
            double nearest=Double.POSITIVE_INFINITY;
            for(StructurePlanner.Site s:sites)if(s.design.mode.equals("surface")) {
                surface.add(s);if(s.design.tier>=3)major++;
                double d=Math.hypot(s.entryX(),s.entryZ());nearest=Math.min(nearest,d);
                if(d<=300)n300++;if(d<=512)n512++;
            }
            check(!surface.isEmpty(),"Each sampled seed has surface plans");
            List<Double> local=new ArrayList<>();
            for(int x=-1024;x<=1024;x+=256)for(int z=-1024;z<=1024;z+=256) {
                double best=Double.POSITIVE_INFINITY;
                for(StructurePlanner.Site s:surface)best=Math.min(best,Math.hypot(s.entryX()-x,s.entryZ()-z));
                // An omitted anchor cannot have a nearer entrance: the <=344-block
                // reserved design bounds keep anchor-to-entrance displacement below 512.
                check(best<radius-Math.hypot(x,z)-512,"Nearest-distance sample is not clipped by scan boundary");
                local.add(best);distances.add(best);
            }
            double surfacePct=surface.size()*100.0/sites.size();
            double perMillion=surface.size()*1e6/(Math.PI*radius*radius);
            double gridP95=percentile(local,.95),gridMax=percentile(local,1);
            if(v4) {
                check(surfacePct>=80.0,"V4 surface share below 80% for seed "+seed);
                check(perMillion>=5.5,"V4 surface density below 5.5 per million blocks for seed "+seed);
                check(n300>=3,"V4 needs at least three surface entrances within 300 blocks for seed "+seed);
                check(gridP95<=350.0,"V4 sampled nearest-entrance p95 exceeds 350 blocks for seed "+seed);
                check(gridMax<=512.0,"V4 sampled nearest-entrance maximum exceeds 512 blocks for seed "+seed);
            }
            total+=sites.size();surfaces+=surface.size();majors+=major;near300+=n300;near512+=n512;maxSpawn=Math.max(maxSpawn,nearest);
            System.out.printf(Locale.ROOT,"PREVALENCE seed=%d version=%s total=%d surface=%d majorSurface=%d surfacePct=%.2f perMillion=%.2f spawnNearestEntrance=%.1f within300=%d within512=%d gridP95=%.1f gridMax=%.1f%n",
                seed,v4?"v4":"v3",sites.size(),surface.size(),major,surfacePct,perMillion,nearest,n300,n512,gridP95,gridMax);
        }
        System.out.printf(Locale.ROOT,"PREVALENCE_SUMMARY seeds=%d radius=%d total=%d surface=%d majorSurface=%d surfacePct=%.2f perMillion=%.2f spawnWorst=%.1f within300=%d within512=%d gridSamples=%d gridP50=%.1f gridP95=%.1f gridMax=%.1f checks=%d%n",
            seeds.length,radius,total,surfaces,majors,surfaces*100.0/total,surfaces*1e6/(seeds.length*Math.PI*radius*radius),maxSpawn,near300,near512,distances.size(),percentile(distances,.50),percentile(distances,.95),percentile(distances,1),checks);
        System.out.println("SURFACE_PREVALENCE_PASS");
    }
    private static double percentile(List<Double> values,double q) {
        Collections.sort(values);return values.get(Math.max(0,(int)Math.ceil(values.size()*q)-1));
    }
    private static void printSite(String label,StructurePlanner.Site s,int sx,int sz,WorldgenExpansion.Boundary boundary)throws Exception {
        System.out.printf(Locale.ROOT,"%s id=%s mode=%s anchor=%d,%d distance=%.1f entry=%d,%d,%d landing=%d,%d,%d bounds=%d,%d..%d,%d savedRoomChunks=%d savedReservedChunks=%d permitted=%s%n",
            label,s.design.id,s.design.mode,s.anchorX,s.anchorZ,Math.hypot(s.anchorX-sx,s.anchorZ-sz),s.entryX(),s.y+1,s.entryZ(),s.entryX(),s.approach[s.approach.length-1]+1,s.entryZ()+s.approach.length,s.x,s.z,s.x+s.width-1,s.z+s.depth-1,savedIn(s,true),savedIn(s,false),boundary.permits(s));
    }
    private static final class Grid implements ChunkGenerator.BiomeGrid {
        private final org.bukkit.block.Biome[][] cells=new org.bukkit.block.Biome[16][16];
        public org.bukkit.block.Biome getBiome(int x,int z){return cells[x][z];}
        public void setBiome(int x,int z,org.bukkit.block.Biome biome){cells[x][z]=biome;}
    }
}
