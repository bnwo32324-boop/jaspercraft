package chat.jaspr.dungeon;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/** Registered stone-brick portals only; never claims ordinary Nether portals. */
public final class Gates implements Listener {
    private final DungeonPlugin plugin;private final File file;
    final List<Gate> all=new ArrayList<>();private final Map<UUID,Integer> dwell=new HashMap<>();private final Map<UUID,Long> cooldown=new HashMap<>();
    public static final class Gate {
        public final UUID world;public final int x,y,z;public final boolean axisX;
        /** Generation 6: a gate inside a run world (its return gate) lives and dies with the run and is never saved. */
        public final boolean temporary;
        Gate(UUID world,int x,int y,int z,boolean axisX){this(world,x,y,z,axisX,false);}
        Gate(UUID world,int x,int y,int z,boolean axisX,boolean temporary){this.world=world;this.x=x;this.y=y;this.z=z;this.axisX=axisX;this.temporary=temporary;}
        Block b(World w,int a,int h){return w.getBlockAt(x+(axisX?a:0),y+h,z+(axisX?0:a));}
        boolean hit(Location l){if(!world.equals(l.getWorld().getUID()))return false;int a=axisX?l.getBlockX()-x:l.getBlockZ()-z;return a>=1&&a<=2&&l.getY()>=y+1&&l.getY()<y+4&&(axisX?l.getBlockZ()==z:l.getBlockX()==x);}
        boolean frame(Block b){if(!world.equals(b.getWorld().getUID()))return false;int a=axisX?b.getX()-x:b.getZ()-z,h=b.getY()-y;return (axisX?b.getZ()==z:b.getX()==x)&&a>=0&&a<=3&&h>=0&&h<=4&&(a==0||a==3||h==0||h==4);}
        boolean valid(World w){for(int a=0;a<4;a++)for(int h=0;h<5;h++){boolean edge=a==0||a==3||h==0||h==4;boolean corner=(a==0||a==3)&&(h==0||h==4);Material m=b(w,a,h).getType();if(edge&&!corner&&m!=Material.SMOOTH_BRICK)return false;if(!edge&&m!=Material.AIR&&m!=Material.FIRE&&m!=Material.PORTAL)return false;}return true;}
        void fill(World w){for(int a=1;a<=2;a++)for(int h=1;h<=3;h++)b(w,a,h).setTypeIdAndData(90,(byte)(axisX?1:2),false);}
        Location safe(World w){return new Location(w,x+(axisX?1.5:2.5),y+1,z+(axisX?2.5:1.5));}
        /** A box (feet at l, half-width, height) overlapping the 2x3 opening: the same contact vanilla needs to start portal travel. */
        boolean touches(Location l,double half,double height){
            if(l==null||l.getWorld()==null||!world.equals(l.getWorld().getUID()))return false;
            double along=axisX?l.getX():l.getZ(),across=axisX?l.getZ():l.getX(),from=(axisX?x:z)+1,plane=axisX?z:x;
            return along+half>from&&along-half<from+2&&across+half>plane&&across-half<plane+1&&l.getY()+height>y+1&&l.getY()<y+4;
        }
    }
    public Gates(DungeonPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"gates.yml");if(file.exists())try{YamlConfiguration y=new YamlConfiguration();y.load(file);for(Map<?,?> m:y.getMapList("gates"))all.add(new Gate(UUID.fromString((String)m.get("world")),((Number)m.get("x")).intValue(),((Number)m.get("y")).intValue(),((Number)m.get("z")).intValue(),Boolean.TRUE.equals(m.get("axisX"))));}catch(Exception e){throw new IllegalStateException("Cannot read portal registry; preserve file and repair it",e);}}
    public Gate at(Location l){if(l==null||l.getWorld()==null)return null;for(Gate g:all)if(g.hit(l))return g;return null;}
    /** The gate whose opening a body touches, even when its centre stands just outside the portal's plane. */
    public Gate touching(Location l,double half,double height){if(l==null||l.getWorld()==null)return null;for(Gate g:all)if(g.touches(l,half,height))return g;return null;}
    public void save() throws IOException {YamlConfiguration y=new YamlConfiguration();List<Map<String,Object>> list=new ArrayList<>();for(Gate g:all){if(g.temporary)continue;Map<String,Object> m=new LinkedHashMap<>();m.put("world",g.world.toString());m.put("x",g.x);m.put("y",g.y);m.put("z",g.z);m.put("axisX",g.axisX);list.add(m);}y.set("gates",list);RoomStore.atomic(file,y.saveToString());}
    public void saveQuietly(){try{save();}catch(Exception e){plugin.getLogger().severe("DUNGEON_GATES_SAVE_FAILED "+e.getMessage());}}
    /** Each run world's return gate (generation 6): transient, never written to gates.yml, dropped when the run closes. */
    public void installReturnGate(World w){Gate g=new Gate(w.getUID(),8,65,8,true,true);for(Gate old:all)if(old.world.equals(g.world)&&old.x==8&&old.z==8){if(old.valid(w))old.fill(w);return;}all.add(g);g.fill(w);}
    /** A closing run's world: its gates go with it. */
    public void drop(World w){boolean saved=false;Iterator<Gate> it=all.iterator();while(it.hasNext()){Gate g=it.next();if(g.world.equals(w.getUID())){saved|=!g.temporary;it.remove();}}if(saved)saveQuietly();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void light(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getItem()==null||e.getItem().getType()!=Material.FLINT_AND_STEEL||e.getClickedBlock().getType()!=Material.SMOOTH_BRICK)return;
        Block b=e.getClickedBlock();Gate found=null;boolean run=plugin.inside(b.getWorld());
        // A frame lit inside a run world is transient like its return gate: it goes with the run.
        search:for(boolean axis:new boolean[]{true,false})for(int a=0;a<4;a++)for(int h=0;h<5;h++){Gate g=new Gate(b.getWorld().getUID(),b.getX()-(axis?a:0),b.getY()-h,b.getZ()-(axis?0:a),axis,run);if(g.y>=1&&g.y+4<255&&g.valid(b.getWorld())){found=g;break search;}}
        if(found==null)return;e.setCancelled(true);Gate g=found;
        for(Gate old:all)if(old.world.equals(g.world)&&old.x==g.x&&old.y==g.y&&old.z==g.z&&old.axisX==g.axisX){old.fill(b.getWorld());return;}
        all.add(g);if(!g.temporary)try{save();}catch(Exception ex){all.remove(g);e.getPlayer().sendMessage("The gate could not be saved. Try again later.");return;}
        g.fill(b.getWorld());if(e.getPlayer().getGameMode()!=GameMode.CREATIVE){ItemStack tool=e.getPlayer().getInventory().getItemInMainHand();tool.setDurability((short)(tool.getDurability()+1));if(tool.getDurability()>=tool.getType().getMaxDurability())tool=new ItemStack(Material.AIR);e.getPlayer().getInventory().setItemInMainHand(tool);}
        e.getPlayer().sendMessage(ChatColor.DARK_PURPLE+"The stone remembers a cellar beneath the world. Step into the gate.");b.getWorld().playSound(b.getLocation(),Sound.BLOCK_PORTAL_TRIGGER,.6f,.6f);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void physics(BlockPhysicsEvent e){if(e.getBlock().getType()==Material.PORTAL&&at(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityPortal(EntityPortalEvent e){if(plugin.inside(e.getFrom().getWorld())||at(e.getFrom())!=null||touching(e.getFrom(),.8,2)!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){
        List<Gate> remove=new ArrayList<>();for(Gate g:all)if(g.frame(e.getBlock())||g.hit(e.getBlock().getLocation()))remove.add(g);
        for(Gate g:remove){all.remove(g);World w=e.getBlock().getWorld();for(int a=1;a<=2;a++)for(int h=1;h<=3;h++)if(g.b(w,a,h).getType()==Material.PORTAL)g.b(w,a,h).setType(Material.AIR,false);}if(!remove.isEmpty())saveQuietly();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blast(EntityExplodeEvent e){e.blockList().removeIf(b->{for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation()))return true;return false;});}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){for(Block b:e.getBlocks())for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation())){e.setCancelled(true);return;}}
    @EventHandler(ignoreCancelled=true) public void pistonBack(BlockPistonRetractEvent e){for(Block b:e.getBlocks())for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation())){e.setCancelled(true);return;}}
    private File returns(UUID id){return new File(plugin.getDataFolder(),"returns/"+id+".yml");}
    public void remember(Player p) throws IOException {remember(p,null);}
    /** The way home, saved at the gate; with generation 6 it also carries the run marker (session: <run world>). */
    public void remember(Player p,String session) throws IOException {
        Location l=p.getLocation();Gate g=at(l);if(g==null)g=touching(l,.3,1.8);if(g!=null)l=g.safe(p.getWorld());YamlConfiguration y=new YamlConfiguration();y.set("world",l.getWorld().getUID().toString());y.set("worldName",l.getWorld().getName());y.set("x",l.getX());y.set("y",l.getY());y.set("z",l.getZ());y.set("yaw",l.getYaw());y.set("pitch",l.getPitch());if(session!=null)y.set("session",session);RoomStore.atomic(returns(p.getUniqueId()),y.saveToString());
    }
    /** The run this player was last part of, or null. A player who logs in with a marker whose run is gone is sent home. */
    public String marker(UUID id){File f=returns(id);if(!f.isFile())return null;try{YamlConfiguration y=new YamlConfiguration();y.load(f);return y.getString("session");}catch(Exception ex){plugin.getLogger().warning("DUNGEON_SESSION_MARKER_UNREADABLE player="+id);return null;}}
    /** Sets (or with null clears) the run marker; the saved way home is never touched. */
    public void mark(UUID id,String session){
        File f=returns(id);
        try{YamlConfiguration y=new YamlConfiguration();if(f.isFile())y.load(f);else if(session==null)return;if(Objects.equals(session,y.getString("session")))return;y.set("session",session);RoomStore.atomic(f,y.saveToString());}
        catch(Exception ex){plugin.getLogger().warning("DUNGEON_SESSION_MARKER_FAILED player="+id+" "+ex.getMessage());}
    }
    /** Clears the marker only while it still names this run. */
    public void unmark(UUID id,String session){if(session!=null&&session.equals(marker(id)))mark(id,null);}
    /** The main world's spawn, on a safe floor near it when there is one (where a death in a run sends a player without a bed). */
    public Location mainSpawn(){World w=Bukkit.getWorlds().get(0);Location l=findSafe(w.getSpawnLocation());return l!=null?l:w.getSpawnLocation().add(.5,0,.5);}
    public Location returnLocation(Player p){
        File f=returns(p.getUniqueId());try{if(f.isFile()){YamlConfiguration y=new YamlConfiguration();y.load(f);String id=y.getString("world");World w=id==null?null:Bukkit.getWorld(UUID.fromString(id));if(w!=null&&!plugin.inside(w)){Location l=new Location(w,y.getDouble("x"),y.getDouble("y"),y.getDouble("z"),(float)y.getDouble("yaw"),(float)y.getDouble("pitch"));Location safe=findSafe(l);if(safe!=null)return safe;}}}catch(Exception ex){plugin.getLogger().warning("DUNGEON_RETURN_FALLBACK "+p.getUniqueId());}
        World w=Bukkit.getWorlds().get(0);return findSafe(w.getSpawnLocation());
    }
    private Location findSafe(Location origin){for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
        if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
        for(int dy:new int[]{0,-1,1,-2,2,-3,3,-4,4,-5,5,-6,6}){Location l=origin.clone().add(dx,dy,dz);l.setX(l.getBlockX()+.5);l.setY(l.getBlockY());l.setZ(l.getBlockZ()+.5);if(Sanctuary.safeFloor(l))return l;}}return null;}
    /** Home through the return gate or /dungeon leave (reason gate or command). Generation 6: the player's part in the run ends for good. */
    public void leave(Player p,String reason){
        if(plugin.rifts!=null&&plugin.rifts.contains(p.getWorld())){plugin.rifts.leave(p);return;}Location safe=returnLocation(p);if(safe==null){p.sendMessage(ChatColor.YELLOW+"The way home is obstructed or unsafe. Stay in the refuge and ask an administrator to clear the exit.");return;}
        Sessions.Session run=plugin.sessions==null?null:plugin.sessions.of(p.getWorld());
        if(plugin.move(p,safe)){cooldown.put(p.getUniqueId(),System.currentTimeMillis()+3500);if(run!=null)plugin.sessions.left(p,run,reason);
            p.sendMessage(ChatColor.GRAY+"The Last Candle remembers your way home."+(run==null?"":" Run #"+run.id+" is over for you; the gate opens a new run."));}
    }
    public void tick(){for(Player p:Bukkit.getOnlinePlayers()){
        if(p.isDead()||System.currentTimeMillis()<cooldown.getOrDefault(p.getUniqueId(),0L)){dwell.remove(p.getUniqueId());continue;}
        // Any contact with the opening counts, as it does for vanilla travel: a body half inside the portal still enters the dungeon.
        Gate g=touching(p.getLocation(),.3,1.8);if(g==null||g.b(p.getWorld(),1,1).getType()!=Material.PORTAL){dwell.remove(p.getUniqueId());continue;}
        if(!g.valid(p.getWorld())){dwell.remove(p.getUniqueId());continue;}int n=dwell.getOrDefault(p.getUniqueId(),0)+1;dwell.put(p.getUniqueId(),n);if(n<Math.max(10,plugin.getConfig().getInt("portal-dwell-ticks",20)))continue;
        dwell.remove(p.getUniqueId());cooldown.put(p.getUniqueId(),System.currentTimeMillis()+3500);
        // Generation 6: an overworld gate starts a new run or joins a friend's run opened from it moments ago.
        if(plugin.inside(p.getWorld()))leave(p,"gate");else plugin.sessions.enter(p,g);
    }}
    public void forget(Player p){dwell.remove(p.getUniqueId());cooldown.remove(p.getUniqueId());}
}
