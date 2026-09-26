package chat.jaspr.muse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Immutable Anvil-header snapshot of the chunks that existed when JasprMuseMaps first started (copy of the importer's ChunkBoundary). */
public final class ChunkBoundary {
    private static final int MAGIC=0x494d5031, LIMIT=8000000;
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final long seed;
    private final Map<Long,BitSet> chunks;
    private final int count;
    private ChunkBoundary(long seed,Map<Long,BitSet> chunks,int count) {
        this.seed=seed;this.chunks=chunks;this.count=count;
    }
    public int count() { return count; }
    private static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    public boolean contains(int x,int z) {
        BitSet b=chunks.get(key(Math.floorDiv(x,32),Math.floorDiv(z,32)));
        return b!=null&&b.get(Math.floorMod(z,32)*32+Math.floorMod(x,32));
    }
    /** Every chunk of the snapshot, as ((long)cx<<32)^(cz&0xffffffffL) keys. */
    public java.util.List<long[]> chunks() {
        java.util.List<long[]> out=new java.util.ArrayList<>();
        for(Map.Entry<Long,BitSet> e:chunks.entrySet()) {
            int rx=(int)(e.getKey()>>32),rz=(int)(long)e.getKey();
            for(int i=e.getValue().nextSetBit(0);i>=0;i=e.getValue().nextSetBit(i+1)) out.add(new long[]{rx*32+i%32,rz*32+i/32});
        }
        return out;
    }
    public boolean permits(int x,int z,int width,int depth) {
        int minX=Math.floorDiv(x-16,16), maxX=Math.floorDiv(x+width+15,16);
        int minZ=Math.floorDiv(z-16,16), maxZ=Math.floorDiv(z+depth+15,16);
        for(int cx=minX;cx<=maxX;cx++) for(int cz=minZ;cz<=maxZ;cz++) if(contains(cx,cz)) return false;
        return true;
    }
    public static ChunkBoundary open(File world,long seed) throws IOException {
        return open(world,seed,"jaspr-muse-v1.boundary");
    }
    /** 1.2.0: the same snapshot under another name, taken the first time that name is opened. */
    public static ChunkBoundary open(File world,long seed,String name) throws IOException {
        File file=new File(world,name);
        if(file.exists()) return read(file.toPath(),seed);
        Map<Long,BitSet> map=new TreeMap<>();int count=0;
        File directory=new File(world,"region");File[] regions=directory.listFiles();
        if(regions==null&&directory.exists()) throw new IOException("Cannot enumerate Anvil regions");
        if(regions!=null) for(File fileIn:regions) {
            Matcher m=REGION.matcher(fileIn.getName());if(!m.matches()) continue;
            int rx=Integer.parseInt(m.group(1)),rz=Integer.parseInt(m.group(2));
            BitSet bits=new BitSet(1024);
            try(RandomAccessFile in=new RandomAccessFile(fileIn,"r")) {
                if(in.length()<8192) throw new IOException("Truncated region: "+fileIn);
                for(int i=0;i<1024;i++) {
                    int location=in.readInt();if(location==0) continue;
                    int off=location>>>8, sectors=location&255;
                    if(off<2||sectors==0||(long)(off+sectors)*4096>in.length())
                        throw new IOException("Invalid Anvil offset: "+fileIn);
                    bits.set(i);count++;
                }
            }
            if(!bits.isEmpty()) map.put(key(rx,rz),bits);
        }
        if(count>LIMIT) throw new IOException("Boundary chunk cap exceeded");
        ChunkBoundary boundary=new ChunkBoundary(seed,map,count);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeLong(seed);out.writeInt(count);
            for(Map.Entry<Long,BitSet> e:map.entrySet()) {
                int rx=(int)(e.getKey()>>32),rz=(int)(long)e.getKey();
                for(int i=e.getValue().nextSetBit(0);i>=0;i=e.getValue().nextSetBit(i+1)) {
                    out.writeInt(rx*32+i%32);out.writeInt(rz*32+i/32);
                }
            }
        }
        byte[] payload=bytes.toByteArray();CRC32 crc=new CRC32();crc.update(payload);
        Path target=file.toPath(),temp=Files.createTempFile(world.toPath(),"muse-boundary-",".pending");
        try {
            try(FileOutputStream raw=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(raw)) {
                out.write(payload);out.writeLong(crc.getValue());out.flush();raw.getFD().sync();
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
        return boundary;
    }
    private static ChunkBoundary read(Path file,long seed) throws IOException {
        long n=Files.size(file);if(n<24||n>24L+LIMIT*8L) throw new IOException("Bad imported boundary length");
        byte[] all=Files.readAllBytes(file);CRC32 crc=new CRC32();crc.update(all,0,all.length-8);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(all))) {
            if(in.readInt()!=MAGIC||in.readLong()!=seed) throw new IOException("Imported boundary identity changed");
            int count=in.readInt();if(count<0||count>LIMIT||24L+count*8L!=all.length) throw new IOException("Imported boundary count invalid");
            Map<Long,BitSet> map=new HashMap<>();
            for(int i=0;i<count;i++) {
                int cx=in.readInt(),cz=in.readInt();long k=key(Math.floorDiv(cx,32),Math.floorDiv(cz,32));
                BitSet b=map.computeIfAbsent(k,v->new BitSet(1024));int at=Math.floorMod(cz,32)*32+Math.floorMod(cx,32);
                if(b.get(at)) throw new IOException("Duplicate protected chunk");b.set(at);
            }
            if(in.readLong()!=crc.getValue()) throw new IOException("Imported boundary CRC mismatch");
            return new ChunkBoundary(seed,map,count);
        }
    }
}
