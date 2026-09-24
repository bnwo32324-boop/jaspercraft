package chat.jaspr.biomes;
import java.io.*;
import java.nio.file.*;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
/** Offline only; preserve level data except spawn and the old End dragon fight's terrain references. */
public final class ResetMetadata {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Explicit server directory required");
        Path root=Paths.get(args[0]).toRealPath();
        for(String world:new String[]{"world","world_nether","world_the_end"}){
            Path file=root.resolve(world).resolve("level.dat");if(!file.toRealPath().startsWith(root))throw new IOException("World outside server root");
            NBTTagCompound n;try(InputStream in=Files.newInputStream(file)){n=NBTCompressedStreamTools.a(in);}NBTTagCompound data=n.getCompound("Data");
            if(world.equals("world")){data.setInt("SpawnX",0);data.setInt("SpawnY",73);data.setInt("SpawnZ",0);}
            NBTTagCompound dims=data.getCompound("DimensionData");if(dims.hasKey("1"))dims.getCompound("1").remove("DragonFight");data.remove("DragonFight");
            Path tmp=file.resolveSibling("level.biome-reset.tmp");if(Files.exists(tmp))throw new IOException("Metadata staging file exists");
            try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE_NEW)){NBTCompressedStreamTools.a(n,out);}
            NBTTagCompound verify;try(InputStream in=Files.newInputStream(tmp)){verify=NBTCompressedStreamTools.a(in);}if(!n.equals(verify))throw new IOException("Metadata round-trip failed");
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            System.out.println("WORLD_METADATA_RESET "+world+" spawn/dragon-only; seed and gamerules retained");
        }
    }
}
