package chat.jaspr.biomes;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;

/** Offline-only, narrowly scoped world metadata reseed. */
public final class ReseedMetadata {
    private static final String[] WORLDS={"world","world_nether","world_the_end"};
    private ReseedMetadata() {}

    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Explicit server directory and signed 64-bit seed required");
        Path root=Paths.get(args[0]).toRealPath();
        long next=Long.parseLong(args[1]),prior=0;boolean first=true;
        int spawnY=new Terrain(next).sample(0,0).y+1;
        Map<Path,Path> staged=new LinkedHashMap<>();
        Map<String,Long> oldSeeds=new LinkedHashMap<>();
        try {
            for(String world:WORLDS) {
                Path folder=root.resolve(world).normalize();
                if(!folder.startsWith(root)||Files.isSymbolicLink(folder))throw new IOException("Unsafe world root: "+world);
                Path file=folder.resolve("level.dat");
                if(Files.isSymbolicLink(file)||!file.toRealPath().startsWith(root))throw new IOException("Unsafe level metadata: "+world);
                NBTTagCompound document;
                try(InputStream in=Files.newInputStream(file)){document=NBTCompressedStreamTools.a(in);}
                NBTTagCompound data=document.getCompound("Data");long old=data.getLong("RandomSeed");oldSeeds.put(world,old);
                if(first){prior=old;first=false;}else if(old!=prior)throw new IOException("World seeds disagree before reseed");
                if(old==next)throw new IOException("New seed must differ from the live seed");
                data.setLong("RandomSeed",next);
                if(world.equals("world")){data.setInt("SpawnX",0);data.setInt("SpawnY",spawnY);data.setInt("SpawnZ",0);}
                // These references point into discarded End terrain and must not survive its regeneration.
                data.remove("DragonFight");NBTTagCompound dimensions=data.getCompound("DimensionData");
                if(dimensions.hasKey("1"))dimensions.getCompound("1").remove("DragonFight");
                Path pending=Files.createTempFile(folder,"level-reseed-",".pending");
                try(OutputStream out=Files.newOutputStream(pending,StandardOpenOption.TRUNCATE_EXISTING)){NBTCompressedStreamTools.a(document,out);}
                NBTTagCompound roundTrip;try(InputStream in=Files.newInputStream(pending)){roundTrip=NBTCompressedStreamTools.a(in);}
                if(!document.equals(roundTrip))throw new IOException("Metadata round-trip failed: "+world);
                staged.put(file,pending);
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet())
                Files.move(entry.getValue(),entry.getKey(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            System.out.println("WORLD_RESEED_COMPLETE oldSeed="+prior+" newSeed="+next+" spawn=0,"+spawnY+",0 worlds="+oldSeeds.size());
        } finally {
            for(Path pending:staged.values())Files.deleteIfExists(pending);
        }
    }
}
