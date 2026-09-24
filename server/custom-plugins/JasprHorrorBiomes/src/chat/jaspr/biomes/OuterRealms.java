package chat.jaspr.biomes;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
/** Native Nether fortresses and End dragon/cities remain, but neither keeps its vanilla biome. */
public final class OuterRealms extends BlockPopulator {
    public static Catalog.Profile profile(World w,int x,int z){
        return profile(w.getSeed(),w.getEnvironment()==World.Environment.NETHER,x,z);
    }
    static Catalog.Profile profile(long seed,boolean nether,int x,int z){
        if(nether){int ring=(int)(Math.hypot(x,z)/384)%9;return Catalog.ALL.get(53+ring);}
        Terrain t=new Terrain(seed);int[] ids={8,39,40,41,52};return Catalog.ALL.get(ids[Math.floorMod(t.cellIndex(Math.floorDiv(x,384),Math.floorDiv(z,384)),ids.length)]);
    }
    @Override public void populate(World w,Random random,Chunk c){
        ChunkSnapshot s=c.getChunkSnapshot();int edits=0;
        boolean nether=w.getEnvironment()==World.Environment.NETHER,reserved=BiomeDetails.nativeReserved(c.getX(),c.getZ());
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            Catalog.Profile p=profile(w,c.getX()*16+x,c.getZ()*16+z);w.setBiome(c.getX()*16+x,c.getZ()*16+z,p.slot);
            if(reserved)continue;
            for(int y=24;y<126&&edits<768;y++){
                int id=s.getBlockTypeId(x,y,z),above=s.getBlockTypeId(x,y+1,z);
                if(above!=0||id!=(nether?87:121))continue;
                if(!BiomeDetails.naturalNeighborhood(s,nether,x-1,z-1,x+1,z+1,y-1,y+3))continue;
                // Only the existing exposed floor is changed; no fortress, portal, ores or city blocks are replaced.
                Block b=c.getBlock(x,y,z);int material=p.surface;
                if(w.getEnvironment()==World.Environment.THE_END&&material!=174)material=159;
                b.setTypeIdAndData(material,(byte)(material==159?p.index%16:p.surfaceData),false);edits++;
            }
        }
        BiomeDetails.decorateNative(c,s,w.getSeed(),nether);
    }
}
