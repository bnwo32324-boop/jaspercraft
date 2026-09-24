package chat.jaspr.biomes;
import java.util.*;
import org.bukkit.*;
import org.bukkit.generator.BlockPopulator;
public final class RuinSupplies extends BlockPopulator {
    @Override public void populate(World w,Random ignored,Chunk c){
        ChunkLight.initialize(c);
        // Dungeons run here rather than in the generator: spawners and stocked
        // chests are tile entities, and ChunkData carries block ids only.
        try {
            Terrain t=new Terrain(w.getSeed());
            Dungeons.populate(w,c,t,new Caves(t));
        } catch(RuntimeException e){
            Bukkit.getLogger().warning("[JasprHorrorBiomes] DUNGEON_FAILED chunk="+c.getX()+","+c.getZ()+" "+e);
        }
        // Vanilla's world-generation animal pass. A custom ChunkGenerator never runs
        // it, and that pass is where virtually every sheep in a vanilla world comes
        // from, so without this the map has no wool at all.
        try {
            VanillaFauna.worldgen(w, c, new Random(w.getSeed() ^ ((long) c.getX() << 32) ^ (c.getZ() & 0xffffffffL)));
        } catch(RuntimeException e){
            Bukkit.getLogger().warning("[JasprHorrorBiomes] FAUNA_FAILED chunk="+c.getX()+","+c.getZ()+" "+e);
        }
        // Only the seam sweep runs here now. Springs are solved at generation, where
        // the finished water can be written straight into the chunk; a populator that
        // let the game's own physics do it would start the flow in front of the player.
        try {
            CaveSprings.seams(w,c);
        } catch(RuntimeException e){
            Bukkit.getLogger().warning("[JasprHorrorBiomes] SPRING_FAILED chunk="+c.getX()+","+c.getZ()+" "+e);
        }
        // Light again once everything above has been carved (3.21.0). The first pass ran on
        // the bare chunk, so rooms cut after it kept sky light 15 and their spawners, which
        // need light 7 or less, never fired.
        try {
            ChunkLight.initialize(c);
        } catch(RuntimeException e){
            Bukkit.getLogger().warning("[JasprHorrorBiomes] RELIGHT_FAILED chunk="+c.getX()+","+c.getZ()+" "+e);
        }
        // StructureLoot materializes role-specific, journaled loot on discovery. Never fill by emptiness.
    }
}
