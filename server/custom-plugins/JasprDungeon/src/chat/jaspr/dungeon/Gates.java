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
        Gate(UUID world,int x,int y,int z,boolean axisX){this.world=world;this.x=x;this.y=y;this.z=z;this.axisX=axisX;}
        Block b(World w,int a,int h){return w.getBlockAt(x+(axisX?a:0),y+h,z+(axisX?0:a));}
        boolean hit(Location l){if(!world.equals(l.getWorld().getUID()))return false;int a=axisX?l.getBlockX()-x:l.getBlockZ()-z;return a>=1&&a<=2&&l.getY()>=y+1&&l.getY()<y+4&&(axisX?l.getBlockZ()==z:l.getBlockX()==x);}
        boolean frame(Block b){if(!world.equals(b.getWorld().getUID()))return false;int a=axisX?b.getX()-x:b.getZ()-z,h=b.getY()-y;return (axisX?b.getZ()==z:b.getX()==x)&&a>=0&&a<=3&&h>=0&&h<=4&&(a==0||a==3||h==0||h==4);}
        boolean valid(World w){for(int a=0;a<4;a++)for(int h=0;h<5;h++){boolean edge=a==0||a==3||h==0||h==4;boolean corner=(a==0||a==3)&&(h==0||h==4);Material m=b(w,a,h).getType();if(edge&&!corner&&m!=Material.SMOOTH_BRICK)return false;if(!edge&&m!=Material.AIR&&m!=Material.FIRE&&m!=Material.PORTAL)return false;}return true;}
        void fill(World w){for(int a=1;a<=2;a++)for(int h=1;h<=3;h++)b(w,a,h).setTypeIdAndData(90,(byte)(axisX?1:2),false);}
        Location safe(World w){return new Location(w,x+(axisX?1.5:2.5),y+1,z+(axisX?2.5:1.5));}
    }
    public Gates(DungeonPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"gates.yml");if(file.exists())try{YamlConfiguration y=new YamlConfiguration();y.load(file);for(Map<?,?> m:y.getMapList("gates"))all.add(new Gate(UUID.fromString((String)m.get("world")),((Number)m.get("x")).intValue(),((Number)m.get("y")).intValue(),((Number)m.get("z")).intValue(),Boolean.TRUE.equals(m.get("axisX"))));}catch(Exception e){throw new IllegalStateException("Cannot read portal registry; preserve file and repair it",e);}}
    public Gate at(Location l){if(l==null||l.getWorld()==null)return null;for(Gate g:all)if(g.hit(l))return g;return null;}
    public void save() throws IOException {YamlConfiguration y=new YamlConfiguration();List<Map<String,Object>> list=new ArrayList<>();for(Gate g:all){Map<String,Object> m=new LinkedHashMap<>();m.put("world",g.world.toString());m.put("x",g.x);m.put("y",g.y);m.put("z",g.z);m.put("axisX",g.axisX);list.add(m);}y.set("gates",list);RoomStore.atomic(file,y.saveToString());}
    public void saveQuietly(){try{save();}catch(Exception e){plugin.getLogger().severe("DUNGEON_GATES_SAVE_FAILED "+e.getMessage());}}
    public void installReturnGate(){World w=plugin.dungeon;Gate g=new Gate(w.getUID(),8,65,8,true);for(Gate old:all)if(old.world.equals(g.world)&&old.x==8&&old.z==8){if(old.valid(w))old.fill(w);return;}all.add(g);saveQuietly();g.fill(w);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void light(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getItem()==null||e.getItem().getType()!=Material.FLINT_AND_STEEL||e.getClickedBlock().getType()!=Material.SMOOTH_BRICK)return;
        Block b=e.getClickedBlock();Gate found=null;
        search:for(boolean axis:new boolean[]{true,false})for(int a=0;a<4;a++)for(int h=0;h<5;h++){Gate g=new Gate(b.getWorld().getUID(),b.getX()-(axis?a:0),b.getY()-h,b.getZ()-(axis?0:a),axis);if(g.y>=1&&g.y+4<255&&g.valid(b.getWorld())){found=g;break search;}}
        if(found==null)return;e.setCancelled(true);Gate g=found;
        for(Gate old:all)if(old.world.equals(g.world)&&old.x==g.x&&old.y==g.y&&old.z==g.z&&old.axisX==g.axisX){old.fill(b.getWorld());return;}
        all.add(g);try{save();}catch(Exception ex){all.remove(g);e.getPlayer().sendMessage("The gate could not be saved. Try again later.");return;}
        g.fill(b.getWorld());if(e.getPlayer().getGameMode()!=GameMode.CREATIVE){ItemStack tool=e.getPlayer().getInventory().getItemInMainHand();tool.setDurability((short)(tool.getDurability()+1));if(tool.getDurability()>=tool.getType().getMaxDurability())tool=new ItemStack(Material.AIR);e.getPlayer().getInventory().setItemInMainHand(tool);}
        e.getPlayer().sendMessage(ChatColor.DARK_PURPLE+"The stone remembers a cellar beneath the world. Step into the gate.");b.getWorld().playSound(b.getLocation(),Sound.BLOCK_PORTAL_TRIGGER,.6f,.6f);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void physics(BlockPhysicsEvent e){if(e.getBlock().getType()==Material.PORTAL&&at(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityPortal(EntityPortalEvent e){if(plugin.inside(e.getFrom().getWorld())||at(e.getFrom())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){
        List<Gate> remove=new ArrayList<>();for(Gate g:all)if(g.frame(e.getBlock())||g.hit(e.getBlock().getLocation()))remove.add(g);
        for(Gate g:remove){all.remove(g);World w=e.getBlock().getWorld();for(int a=1;a<=2;a++)for(int h=1;h<=3;h++)if(g.b(w,a,h).getType()==Material.PORTAL)g.b(w,a,h).setType(Material.AIR,false);}if(!remove.isEmpty())saveQuietly();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blast(EntityExplodeEvent e){e.blockList().removeIf(b->{for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation()))return true;return false;});}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){for(Block b:e.getBlocks())for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation())){e.setCancelled(true);return;}}
    @EventHandler(ignoreCancelled=true) public void pistonBack(BlockPistonRetractEvent e){for(Block b:e.getBlocks())for(Gate g:all)if(g.frame(b)||g.hit(b.getLocation())){e.setCancelled(true);return;}}
    public void remember(Player p) throws IOException {
        Location l=p.getLocation();Gate g=at(l);if(g!=null)l=g.safe(p.getWorld());YamlConfiguration y=new YamlConfiguration();y.set("world",l.getWorld().getUID().toString());y.set("worldName",l.getWorld().getName());y.set("x",l.getX());y.set("y",l.getY());y.set("z",l.getZ());y.set("yaw",l.getYaw());y.set("pitch",l.getPitch());RoomStore.atomic(new File(plugin.getDataFolder(),"returns/"+p.getUniqueId()+".yml"),y.saveToString());
    }
    public Location returnLocation(Player p){
        File f=new File(plugin.getDataFolder(),"returns/"+p.getUniqueId()+".yml");try{if(f.isFile()){YamlConfiguration y=new YamlConfiguration();y.load(f);World w=Bukkit.getWorld(UUID.fromString(y.getString("world")));if(w!=null&&!plugin.inside(w)){Location l=new Location(w,y.getDouble("x"),y.getDouble("y"),y.getDouble("z"),(float)y.getDouble("yaw"),(float)y.getDouble("pitch"));Location safe=findSafe(l);if(safe!=null)return safe;}}}catch(Exception ex){plugin.getLogger().warning("DUNGEON_RETURN_FALLBACK "+p.getUniqueId());}
        World w=Bukkit.getWorlds().get(0);return findSafe(w.getSpawnLocation());
    }
    private Location findSafe(Location origin){for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
        if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
        for(int dy:new int[]{0,-1,1,-2,2,-3,3,-4,4,-5,5,-6,6}){Location l=origin.clone().add(dx,dy,dz);l.setX(l.getBlockX()+.5);l.setY(l.getBlockY());l.setZ(l.getBlockZ()+.5);if(Sanctuary.safeFloor(l))return l;}}return null;}
    public void leave(Player p){if(plugin.rifts!=null&&plugin.rifts.contains(p.getWorld())){plugin.rifts.leave(p);return;}Location safe=returnLocation(p);if(safe==null){p.sendMessage(ChatColor.YELLOW+"The way home is obstructed or unsafe. Stay in the refuge and ask an administrator to clear the exit.");return;}if(plugin.move(p,safe)){cooldown.put(p.getUniqueId(),System.currentTimeMillis()+3500);p.sendMessage(ChatColor.GRAY+"The Last Candle remembers your way home.");}}
    public void tick(){for(Player p:Bukkit.getOnlinePlayers()){
        if(p.isDead()||System.currentTimeMillis()<cooldown.getOrDefault(p.getUniqueId(),0L)){dwell.remove(p.getUniqueId());continue;}
        Gate g=at(p.getLocation());if(g==null||p.getLocation().getBlock().getType()!=Material.PORTAL){dwell.remove(p.getUniqueId());continue;}
        if(!g.valid(p.getWorld())){dwell.remove(p.getUniqueId());continue;}int n=dwell.getOrDefault(p.getUniqueId(),0)+1;dwell.put(p.getUniqueId(),n);if(n<Math.max(10,plugin.getConfig().getInt("portal-dwell-ticks",20)))continue;
        dwell.remove(p.getUniqueId());cooldown.put(p.getUniqueId(),System.currentTimeMillis()+3500);
        if(plugin.inside(p.getWorld()))leave(p);else try{Location arrival=plugin.sanctuary.arrival();if(arrival==null){p.sendMessage("The refuge arrival area is obstructed. The gate will not send you into danger.");continue;}remember(p);plugin.move(p,arrival);}catch(Exception ex){p.sendMessage("The gate cannot open safely right now.");plugin.getLogger().severe("DUNGEON_PORTAL_FAILED "+ex.getMessage());}
    }}
    public void forget(Player p){dwell.remove(p.getUniqueId());cooldown.remove(p.getUniqueId());}
}
