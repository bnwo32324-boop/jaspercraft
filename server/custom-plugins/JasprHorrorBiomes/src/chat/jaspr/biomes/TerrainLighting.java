package chat.jaspr.biomes;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EnumSkyBlock;

/** Bukkit 1.12 custom chunks initialize vertical sky light, but not sideways canopy light.
 * Reconcile using native light propagation only after all needed neighbors are loaded.
 * No chunk loads; bounded queue, 32 columns/tick and a 2 ms soft time budget. */
public final class TerrainLighting implements Listener {
    private final HorrorPlugin plugin;private final LinkedHashMap<String,Work> pending=new LinkedHashMap<>();
    TerrainLighting(HorrorPlugin p){plugin=p;}
    static final class Work{final World w;final int x,z;int col,tries;Work(Chunk c){w=c.getWorld();x=c.getX();z=c.getZ();}}
    public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,this::step,1,1);}
    public void stop(){pending.clear();HandlerList.unregisterAll(this);}
    @EventHandler public void load(ChunkLoadEvent e){if(e.getWorld().getEnvironment()!=World.Environment.NORMAL||!(e.getWorld().getGenerator() instanceof HorrorGenerator||e.getWorld().getGenerator() instanceof LiminalGenerator))return;
        // Unpopulated view-distance fringe chunks can be sent too: initialize them before first delivery.
        if(e.isNewChunk())ChunkLight.initialize(e.getChunk());
        Chunk c=e.getChunk();String key=c.getWorld().getUID()+":"+c.getX()+":"+c.getZ();if(pending.size()<512)pending.putIfAbsent(key,new Work(c));}
    private void step(){long end=System.nanoTime()+2000000L;int columns=0;Iterator<Work> it=pending.values().iterator();
        while(it.hasNext()&&columns<32&&System.nanoTime()<end){Work a=it.next();if(!a.w.isChunkLoaded(a.x,a.z)){it.remove();continue;}
            boolean ready=true;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(!a.w.isChunkLoaded(a.x+x,a.z+z))ready=false;
            if(!ready){if(++a.tries>6000)it.remove();continue;}
            net.minecraft.server.v1_12_R1.WorldServer n=((CraftWorld)a.w).getHandle();Terrain terrain=new Terrain(a.w.getSeed());
            // identify(), not sites(): a structure placed under older, denser rules still needs its light (3.21.0).
            List<StructurePlanner.Site> sites=a.w.getGenerator() instanceof HorrorGenerator?WorldgenExpansion.identify(a.w,a.x,a.z):Collections.emptyList();
            // A chunk holding a spawner or a chest is relit down to bedrock: spawners need light <= 7.
            boolean tiles=false;for(BlockState t:a.w.getChunkAt(a.x,a.z).getTileEntities())if(t instanceof CreatureSpawner||t instanceof Chest){tiles=true;break;}
            while(a.col<256&&columns<32&&System.nanoTime()<end){int x=a.x*16+(a.col&15),z=a.z*16+(a.col>>4),low=terrain.sample(x,z).y+1,high=a.w.getHighestBlockYAt(x,z);
                if(a.w.getGenerator() instanceof HorrorGenerator)for(StructurePlanner.Site s:sites)low=Math.min(low,s.y-12);
                // AS SHIPPED IN THE LIVE 3.23.0 JAR (kept byte-identical by the source recovery): this
                // `if` sits above the old `else low=48;`, which now binds to it. Every column therefore
                // starts at 1 (tiles) or 48, and the site floor computed just above is always
                // overwritten. Flagged for the structure audit; not changed here.
                if(tiles)low=1;
                else low=48;
                for(int y=Math.max(1,low);y<=Math.min(249,high);y++){BlockPosition p=new BlockPosition(x,y,z);if(!n.getType(p).getMaterial().isSolid())n.c(EnumSkyBlock.SKY,p);if((a.col&15)==0||(a.col&15)==15||(a.col>>4)==0||(a.col>>4)==15)n.c(EnumSkyBlock.BLOCK,p);}
                a.col++;columns++;
            }
            if(a.col==256){plugin.visibility().refreshChunk(a.w,a.x,a.z);it.remove();}
        }
    }
}
