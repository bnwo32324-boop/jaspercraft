package chat.jaspr.biomes;

import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional isolated Paper smoke fixture. Never install this probe on a production server. */
public final class LiminalPaperProbe extends JavaPlugin implements Listener {
    private LiminalWorld liminal;private boolean boundedPreload;
    @Override public void onEnable(){
        Bukkit.getPluginManager().registerEvents(this,this);liminal=new LiminalWorld(this,HorrorPlugin::authenticated);liminal.start();
        if(Bukkit.getWorld(LiminalWorld.WORLD_NAME)!=null)throw new AssertionError("STARTUP eagerly created dimension");
        Bukkit.getScheduler().runTaskLater(this,()->{
            try{verify();}catch(Throwable failure){getLogger().severe("LIMINAL_PAPER_FAILED "+failure);failure.printStackTrace();}
            finally{Bukkit.shutdown();}
        },30);
    }
    @EventHandler public void fixtureWorld(WorldInitEvent event){
        // The synthetic main world does not need a spawn preloader either; production is untouched.
        if(!event.getWorld().getName().equals(LiminalWorld.WORLD_NAME))event.getWorld().setKeepSpawnInMemory(false);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void inspectPreload(WorldInitEvent event){
        if(event.getWorld().getName().equals(LiminalWorld.WORLD_NAME))boundedPreload=!event.getWorld().getKeepSpawnInMemory();
    }
    private void verify(){
        World normal=Bukkit.getWorld("world"),world=Bukkit.getWorld(LiminalWorld.WORLD_NAME);
        require(world!=null&&world.getEnvironment()==World.Environment.NORMAL,"real normal-environment world");
        require(normal!=null&&!world.getUID().equals(normal.getUID()),"independent dimension UUID");
        require(boundedPreload,"no dimension spawn preloading");
        require(world.getWorldBorder().getSize()==64,"finite border");
        int signs=0,lights=0;
        int[][] labels={{6,2},{13,6},{10,13},{2,10}};
        for(int room=0;room<16;room++){
            Chunk chunk=world.getChunkAt(room%4,room/4);chunk.load();
            // Populating the four bounded neighbors makes Paper's normal population lifecycle run.
            world.getChunkAt(chunk.getX()+1,chunk.getZ()).load();world.getChunkAt(chunk.getX(),chunk.getZ()+1).load();
            world.getChunkAt(chunk.getX()+1,chunk.getZ()+1).load();
            for(int side=0;side<4;side++){
                Location door=LiminalGenerator.door(world,room,side),landing=LiminalGenerator.arrival(world,room,side);
                require(door.getBlock().getType()==Material.IRON_DOOR_BLOCK,"lower door room "+room);
                require(door.clone().add(0,1,0).getBlock().getType()==Material.IRON_DOOR_BLOCK,"upper door room "+room);
                require(landing.getBlock().getType()==Material.AIR&&landing.clone().add(0,1,0).getBlock().getType()==Material.AIR,"clear landing room "+room);
                require(landing.clone().add(0,-1,0).getBlock().getType().isSolid(),"supported landing room "+room);
                Sign sign=(Sign)chunk.getBlock(labels[side][0],LiminalGenerator.floor(room)+2,labels[side][1]).getState();
                require(sign.getLine(2).startsWith("Door -> "),"populated door label room "+room+" side "+side);signs++;
            }
            Location light=LiminalGenerator.door(world,room,0).add(0,2,0);
            require(light.getBlock().getLightFromBlocks()==15,"native lantern light room "+room);
            require(light.clone().add(0,0,1).getBlock().getLightFromBlocks()>=14,"light propagates into room "+room);lights++;
        }
        require(LiminalGenerator.exitMarker(world).getBlock().getType()==Material.SEA_LANTERN,"visible escape marker");
        require(((Sign)world.getBlockAt(8,66,5).getState()).getLine(2).equals("/wasteland return"),"escape command sign");
        for(Location chest:LiminalGenerator.lootMarkers(world))require(chest.getBlock().getType()==Material.CHEST,"journaled loot marker remains present at "+chest+" type="+chest.getBlock().getType());
        // No players, portals or generator code can request remote chunks in this fixture.
        for(Chunk chunk:world.getLoadedChunks())require(Math.abs(chunk.getX())<8&&Math.abs(chunk.getZ())<8,"bounded actual chunk loads");
        world.save();getLogger().info("LIMINAL_PAPER_OK rooms=16 signs="+signs+" lights="+lights+" chunks="+world.getLoadedChunks().length+" uid="+world.getUID());
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onDisable(){if(liminal!=null)liminal.stop();}
}
