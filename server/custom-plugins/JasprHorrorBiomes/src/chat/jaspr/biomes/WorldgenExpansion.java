package chat.jaspr.biomes;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.CRC32;
import org.bukkit.World;

/** Immutable upgrade boundary. New sites must fit completely outside all pre-upgrade chunks. */
public final class WorldgenExpansion {
    public static final String FILE_NAME="jaspr-expansion-v3.boundary";
    private static final int MAGIC=0x4a455833,MAX_CHUNKS=8000000;
    private static final Map<String,Boundary> WORLDS=new HashMap<>();
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private WorldgenExpansion() {}

    public static final class Boundary {
        private final long seed;
        private final Map<Long,BitSet> occupied;
        private final int chunks;
        private Boundary(long seed,Map<Long,BitSet> occupied,int chunks){this.seed=seed;this.occupied=occupied;this.chunks=chunks;}
        public int protectedChunks(){return chunks;}
        public boolean contains(int cx,int cz){BitSet bits=occupied.get(key(Math.floorDiv(cx,32),Math.floorDiv(cz,32)));return bits!=null&&bits.get(Math.floorMod(cz,32)*32+Math.floorMod(cx,32));}
        public boolean permits(StructurePlanner.Site site){
            if(!site.expansion())return true;
            if(site.seed!=seed)throw new IllegalArgumentException("Expansion boundary seed mismatch");
            for(int cx=Math.floorDiv(site.x-16,16);cx<=Math.floorDiv(site.x+site.width+15,16);cx++)
                for(int cz=Math.floorDiv(site.z-16,16);cz<=Math.floorDiv(site.z+site.depth+15,16);cz++)
                    if(contains(cx,cz))return false;
            return true;
        }
    }
    public static synchronized Boundary initialize(World world){
        try {
            String key=world.getWorldFolder().getCanonicalPath();Boundary boundary=WORLDS.get(key);
            if(boundary==null){boundary=open(world.getWorldFolder(),world.getSeed());WORLDS.put(key,boundary);}
            if(boundary.seed!=world.getSeed())throw new IOException("World seed changed after expansion boundary initialization");
            return boundary;
        }catch(IOException e){throw new IllegalStateException("EXPANSION_BOUNDARY_FAILED: refusing unprotected world generation",e);}
    }
    public static List<StructurePlanner.Site> sites(World world,int cx,int cz){
        Boundary boundary=initialize(world);List<StructurePlanner.Site> result=new ArrayList<>();
        for(StructurePlanner.Site site:StructurePlanner.sites(world.getSeed(),cx,cz))if(boundary.permits(site))result.add(site);
        return result;
    }
    /** Every site this chunk could hold, including ones placed under older, denser admission rules
     * (3.20.0). For recognition only -- loot, the Fold door, relighting. Never use it to decide
     * what to build: that is sites(), which applies today's gates and the expansion boundary. */
    public static List<StructurePlanner.Site> identify(World world,int cx,int cz){
        return StructurePlanner.identify(world.getSeed(),cx,cz);
    }
    /** Pure, bounded atlas search. It never loads or generates chunks. */
    public static List<StructurePlanner.Site> nearestSurface(World world,int x,int z,int radius,int limit){
        if(radius<1||radius>8192||limit<1||limit>12)throw new IllegalArgumentException("Invalid atlas search bounds");
        Boundary boundary=initialize(world);List<StructurePlanner.Site> found=new ArrayList<>();Set<String> keys=new HashSet<>();
        int expansionRange=radius/StructurePlanner.EXPANSION_REGION+2,ex=Math.floorDiv(x,StructurePlanner.EXPANSION_REGION),ez=Math.floorDiv(z,StructurePlanner.EXPANSION_REGION);
        for(int rx=ex-expansionRange;rx<=ex+expansionRange;rx++)for(int rz=ez-expansionRange;rz<=ez+expansionRange;rz++){
            StructurePlanner.Site site=StructurePlanner.expansionRegion(world.getSeed(),rx,rz);
            if(site!=null&&site.design.mode.equals("surface")&&boundary.permits(site)&&distanceSquared(site,x,z)<=(long)radius*radius&&keys.add(site.key))found.add(site);
        }
        int legacyRange=radius/StructurePlanner.REGION+2,lx=Math.floorDiv(x,StructurePlanner.REGION),lz=Math.floorDiv(z,StructurePlanner.REGION);
        for(int rx=lx-legacyRange;rx<=lx+legacyRange;rx++)for(int rz=lz-legacyRange;rz<=lz+legacyRange;rz++){
            StructurePlanner.Site site=StructurePlanner.region(world.getSeed(),rx,rz);
            if(site!=null&&site.design.mode.equals("surface")&&distanceSquared(site,x,z)<=(long)radius*radius&&keys.add(site.key))found.add(site);
        }
        found.sort(Comparator.comparingLong(site->distanceSquared(site,x,z)));
        if(found.size()>limit)found=new ArrayList<>(found.subList(0,limit));
        return Collections.unmodifiableList(found);
    }
    private static long distanceSquared(StructurePlanner.Site site,int x,int z){long dx=(long)site.anchorX-x,dz=(long)site.anchorZ-z;return dx*dx+dz*dz;}
    public static synchronized void clear(){WORLDS.clear();}
    /** Uses only Anvil location headers; never loads/generates a neighbor or changes saved terrain. */
    public static Boundary open(File folder,long seed)throws IOException {return open(folder,seed,FILE_NAME,MAGIC);}
    /** The same immutable snapshot under another name (3.25.0: StructureRates' boundary for the extra sites). */
    static Boundary open(File folder,long seed,String fileName,int magic)throws IOException {
        File file=new File(folder,fileName);
        if(file.exists())return read(file,seed,magic);
        Map<Long,BitSet> map=new TreeMap<>();int count=0;
        File region=new File(folder,"region");File[] files=region.listFiles();
        if(files==null&&region.exists())throw new IOException("Cannot list region directory");
        if(files!=null)for(File f:files){
            Matcher match=REGION.matcher(f.getName());if(!match.matches())continue;
            int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));BitSet bits=new BitSet(1024);
            try(RandomAccessFile in=new RandomAccessFile(f,"r")){
                if(in.length()<8192)throw new IOException("Truncated Anvil region header: "+f.getName());
                for(int i=0;i<1024;i++){
                    int location=in.readInt();if(location==0)continue;
                    int offset=location>>>8,sectors=location&255;
                    if(offset<2||sectors==0||(long)(offset+sectors)*4096>in.length())throw new IOException("Invalid Anvil location: "+f.getName());
                    bits.set(i);count++;
                }
            }
            if(!bits.isEmpty())map.put(key(rx,rz),bits);
        }
        if(count>MAX_CHUNKS)throw new IOException("Expansion boundary too large");
        Boundary boundary=new Boundary(seed,map,count);write(file,boundary,magic);return boundary;
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static void write(File file,Boundary boundary,int magic)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(magic);out.writeLong(boundary.seed);out.writeInt(boundary.chunks);
            for(Map.Entry<Long,BitSet> entry:boundary.occupied.entrySet()) {
                int rx=(int)(entry.getKey()>>32),rz=(int)(long)entry.getKey();
                for(int i=entry.getValue().nextSetBit(0);i>=0;i=entry.getValue().nextSetBit(i+1)){out.writeInt(rx*32+i%32);out.writeInt(rz*32+i/32);}
            }
        }
        CRC32 crc=new CRC32();byte[] payload=bytes.toByteArray();crc.update(payload);
        Path parent=file.toPath().getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"expansion-v3-",".pending");
        try {
            try(FileOutputStream raw=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(raw)){out.write(payload);out.writeLong(crc.getValue());out.flush();raw.getFD().sync();}
            // The immutable file is committed before the first new block. Never replace an existing boundary.
            Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE);
        }finally{Files.deleteIfExists(temp);}
    }
    private static Boundary read(File file,long seed,int magic)throws IOException {
        long size=file.length();if(size<24||size>24L+MAX_CHUNKS*8L)throw new IOException("Invalid expansion boundary length");
        byte[] all=Files.readAllBytes(file.toPath());CRC32 crc=new CRC32();crc.update(all,0,all.length-8);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(all))){
            if(in.readInt()!=magic||in.readLong()!=seed)throw new IOException("Expansion boundary identity mismatch");
            int count=in.readInt();if(count<0||count>MAX_CHUNKS||24L+count*8L!=all.length)throw new IOException("Invalid expansion boundary count");
            Map<Long,BitSet> map=new HashMap<>();
            for(int i=0;i<count;i++){
                int cx=in.readInt(),cz=in.readInt();long region=key(Math.floorDiv(cx,32),Math.floorDiv(cz,32));BitSet bits=map.get(region);
                if(bits==null){bits=new BitSet(1024);map.put(region,bits);}int at=Math.floorMod(cz,32)*32+Math.floorMod(cx,32);
                if(bits.get(at))throw new IOException("Duplicate boundary chunk");bits.set(at);
            }
            if(in.readLong()!=crc.getValue())throw new IOException("Expansion boundary checksum mismatch");
            return new Boundary(seed,map,count);
        }
    }
}
