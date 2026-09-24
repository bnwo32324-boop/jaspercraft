package chat.jaspr.biomes;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
public final class BiomeProbe extends JavaPlugin implements Listener {
    private final Map<Integer,int[]> points=new TreeMap<>();private int assertions,generated;private long nanos;private boolean busy;
    public void onEnable(){if(!Boolean.getBoolean("jaspr.biomes.fixture"))throw new IllegalStateException("Fixture flag required");getServer().getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->{e.getPlayer().setGameMode(GameMode.CREATIVE);e.getPlayer().setFlying(true);show(9);},30);}
    private void check(boolean ok,String message){assertions++;if(!ok)throw new IllegalStateException(message);}
    public boolean onCommand(CommandSender sender,Command c,String l,String[] args){
        if(!(sender instanceof ConsoleCommandSender))return true;
        try{
            if(args.length>0&&args[0].equals("light")){for(Player player:Bukkit.getOnlinePlayers()){
                World lw=player.getWorld();org.bukkit.craftbukkit.v1_12_R1.CraftWorld cw=(org.bukkit.craftbukkit.v1_12_R1.CraftWorld)lw;
                int px=player.getLocation().getBlockX(),pz=player.getLocation().getBlockZ();int checks=0;
                for(int x=px-20;x<=px+20;x++)for(int z=pz-20;z<=pz+20;z++){int lo=new Terrain(lw.getSeed()).sample(x,z).y+1,hi=lw.getHighestBlockYAt(x,z);for(int y=lo;y<=hi;y++){net.minecraft.server.v1_12_R1.BlockPosition pos=new net.minecraft.server.v1_12_R1.BlockPosition(x,y,z);if(!cw.getHandle().getType(pos).getMaterial().isSolid()){cw.getHandle().c(net.minecraft.server.v1_12_R1.EnumSkyBlock.SKY,pos);checks++;}}}
                for(int x=(px>>4)-1;x<=(px>>4)+1;x++)for(int z=(pz>>4)-1;z<=(pz>>4)+1;z++)lw.refreshChunk(x,z);getLogger().info("BIOME_LIGHT_CHECKS "+checks);
            }return true;}
            if(args.length>0&&args[0].equals("show")){show(Integer.parseInt(args[1]));return true;}
            if(busy)return true;busy=true;World w=Bukkit.getWorld("world");check(w.getGenerator() instanceof HorrorGenerator,"Custom generator attached");
            Terrain t=new Terrain(w.getSeed());
            check(t.sample(0,0).y==72,"Offline reset spawn y=73 stays on safe level ground");
            for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++){int wx=x*384+192,wz=z*384+192;Terrain.Sample s=t.sample(wx,wz);points.putIfAbsent(s.profile.index,new int[]{wx,wz});}
            check(points.size()==62,"Every biome reachable: "+points.size());check(Catalog.ALL.size()==org.bukkit.block.Biome.values().length,"All slots covered");
            for(int x=-1024;x<1024;x+=7)for(int z=-80;z<=80;z+=13){Terrain.Sample a=t.sample(x,z),b=t.sample(x+1,z);check(a.y>=42&&a.y<=145,"height bound");check(Math.abs(a.y-b.y)<=16,"adjacent terrain discontinuity");}
            Iterator<Map.Entry<Integer,int[]>> iterator=points.entrySet().iterator();
            new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{if(iterator.hasNext()){testChunk(w,iterator.next());return;}finish(w);cancel();}catch(Throwable e){getLogger().severe("BIOME_TEST_FAIL "+e.toString());cancel();busy=false;}}}.runTaskTimer(this,1,1);
        }catch(Throwable e){getLogger().severe("BIOME_TEST_FAIL "+e.toString());busy=false;}
        return true;
    }
    private void testChunk(World w,Map.Entry<Integer,int[]> point){
        int cx=Math.floorDiv(point.getValue()[0],16),cz=Math.floorDiv(point.getValue()[1],16);Grid a=new Grid(),b=new Grid();long start=System.nanoTime();
        ChunkGenerator.ChunkData first=w.getGenerator().generateChunkData(w,new Random(1),cx,cz,a),second=w.getGenerator().generateChunkData(w,new Random(2),cx,cz,b);nanos+=System.nanoTime()-start;
        int nonair=0,ores=0;for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            check(first.getTypeId(x,0,z)==7,"bedrock floor");check(a.getBiome(x,z)==b.getBiome(x,z),"deterministic biome");
            for(int y=1;y<160;y++){int id=first.getTypeId(x,y,z);check(id==second.getTypeId(x,y,z)&&first.getData(x,y,z)==second.getData(x,y,z),"deterministic blocks");if(id>0)nonair++;if(id==56||id==15||id==16)ores++;}
        }
        check(nonair>8000&&ores>80,"playable terrain/resources");check(a.getBiome(0,0)==Catalog.ALL.get(point.getKey()).slot,"expected biome at sample");generated++;
    }
    private void finish(World w){
        Grid grid=new Grid();ChunkGenerator.ChunkData p=w.getGenerator().generateChunkData(w,new Random(),8,0,grid);int frames=0;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=40;y<150;y++)if(p.getTypeId(x,y,z)==120)frames++;
        check(frames==12,"twelve usable End portal frames");
        for(World realm:Bukkit.getWorlds())if(realm.getEnvironment()!=World.Environment.NORMAL){check(realm.getPopulators().stream().anyMatch(pop->pop instanceof OuterRealms),"outer realm replacement registered");Chunk c=realm.getChunkAt(1,1);new OuterRealms().populate(realm,new Random(1),c);check(c.getBlock(8,80,8).getBiome()==OuterRealms.profile(realm,24,24).slot,"outer realm biome applied");}
        for(World.Environment environment:World.Environment.values())check(Bukkit.getWorlds().stream().anyMatch(realm->realm.getEnvironment()==environment),"dimension available: "+environment);
        int[] forest=points.get(9);Chunk forestChunk=w.getChunkAt(forest[0]>>4,forest[1]>>4);ChunkLight.initialize(forestChunk);int shaded=0;
        Terrain t=new Terrain(w.getSeed());for(int x=2;x<14;x++)for(int z=2;z<14;z++){int wx=forestChunk.getX()*16+x,wz=forestChunk.getZ()*16+z,y=t.sample(wx,wz).y+1;
            org.bukkit.block.Block air=w.getBlockAt(wx,y,wz);if(air.getType()==Material.AIR&&w.getHighestBlockYAt(wx,wz)>y+2){check(air.getLightFromSky()>0,"lateral canopy light propagated");shaded++;}}
        check(shaded>0,"canopy lighting fixture has shadows");
        // Real server chunk generation/persistence, in addition to the deterministic raw-chunk sweep.
        w.getChunkAt(8,0);w.save();check(w.getBlockAt(133,Math.max(65,new Terrain(w.getSeed()).sample(136,8).y)+1,6).getType()==Material.ENDER_PORTAL_FRAME,"portal persisted in world");
        getLogger().info("BIOME_TEST_PASS {\"biomes\":"+generated+",\"assertions\":"+assertions+",\"averageChunkMs\":"+(nanos/1e6/(generated*2))+",\"dimensions\":3}");busy=false;
    }
    private void show(int index){World w=Bukkit.getWorld("world");if(points.isEmpty()){Terrain t=new Terrain(w.getSeed());for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++){int a=x*384+192,b=z*384+192;points.putIfAbsent(t.sample(a,b).profile.index,new int[]{a,b});}}
        int[] at=points.get(index);if(at==null)throw new IllegalArgumentException("Unknown biome");int y=new Terrain(w.getSeed()).sample(at[0],at[1]).y;
        for(Player p:Bukkit.getOnlinePlayers()){p.teleport(new Location(w,at[0]+.5,y+14,at[1]+.5,35,20));p.setFlying(true);}
        w.setTime(11000);w.setStorm(false);getLogger().info("BIOME_SHOW "+Catalog.ALL.get(index).name);
    }
    private static final class Grid implements ChunkGenerator.BiomeGrid{final org.bukkit.block.Biome[][] cells=new org.bukkit.block.Biome[16][16];public org.bukkit.block.Biome getBiome(int x,int z){return cells[x][z];}public void setBiome(int x,int z,org.bukkit.block.Biome b){cells[x][z]=b;}}
}
