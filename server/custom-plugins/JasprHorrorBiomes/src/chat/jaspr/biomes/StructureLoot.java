package chat.jaspr.biomes;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;

/** Loot is materialized once, on first legitimate discovery, never on empty-chest/chunk reload. */
public final class StructureLoot implements Listener {
    private final HorrorPlugin plugin;private LootJournal journal;private boolean failed;private long opened,denied,reconstructed,restocked;
    // Lookup context for restockAuthored, rebuilt only when the world (seed) changes.
    private Terrain terrain;private Caves caves;private UUID terrainWorld;
    public StructureLoot(HorrorPlugin p){plugin=p;}
    public void start(){try{journal=new LootJournal(new File(plugin.getDataFolder(),"structure-loot-v2.journal"));}catch(IOException e){failed=true;plugin.getLogger().severe("STRUCTURE_LOOT_LOCKED journal="+e.getMessage());}Bukkit.getPluginManager().registerEvents(this,plugin);}
    public void stop(){HandlerList.unregisterAll(this);if(journal!=null)try{journal.close();}catch(IOException e){plugin.getLogger().severe("LOOT_JOURNAL_CLOSE_FAILED");}}
    public String metrics(){return "lootClaims="+(journal==null?0:journal.size())+" opens="+opened+" denied="+denied+" reconstructed="+reconstructed+" restocked="+restocked+" lootLocked="+failed+" "+GearLoot.metrics();}
    // Claim keys are the chest's position (3.20.0). The old site-key:marker-ordinal key moved whenever the
    // region layer tag in the site key changed, so the same chest could come back under a new name.
    // reconstructed = the site is recognised by identify() but today's admission would not place it.
    static final class Cache {final StructurePlanner.Site site;final StructurePlanner.Marker marker;final String key;final int room;final boolean reconstructed;Cache(World w,StructurePlanner.Site s,StructurePlanner.Marker m,String namespace,boolean rebuilt){site=s;marker=m;room=-1;reconstructed=rebuilt;key=namespace+":"+w.getUID()+":"+m.x+":"+m.y+":"+m.z;}Cache(World w,int r){site=null;marker=null;room=r;reconstructed=false;key="fold-v1:"+w.getUID()+":"+r;}}
    /** Catalogue supply chests stand on a stone-brick pedestal with two air blocks above. */
    private static boolean pedestal(Block b){return b.getRelative(0,-1,0).getType()==Material.SMOOTH_BRICK&&b.getRelative(0,1,0).getType()==Material.AIR&&b.getRelative(0,2,0).getType()==Material.AIR;}
    /** Inside the site's room grid (the reserved margin and approach excluded). */
    private static boolean inside(StructurePlanner.Site s,Block b){int x0=s.x+StructurePlanner.MARGIN,z0=s.z+StructurePlanner.MARGIN;return b.getX()>=x0&&b.getX()<x0+s.design.columns*12&&b.getZ()>=z0&&b.getZ()<z0+s.design.rows*12;}
    private Cache cache(Block b){
        if(b.getWorld().getName().equals(LiminalWorld.WORLD_NAME)){for(Location at:LiminalGenerator.lootMarkers(b.getWorld()))if(at.getBlockX()==b.getX()&&at.getBlockY()==b.getY()&&at.getBlockZ()==b.getZ())return new Cache(b.getWorld(),LiminalGenerator.roomAt(at));return null;}
        if(!b.getWorld().getName().equals("world"))return null;
        if(b.getType()!=Material.CHEST&&b.getType()!=Material.TRAPPED_CHEST)return null;
        // Identification, not admission (3.20.0): look the chest up among every site this chunk could hold,
        // including ones built under older, denser rules. Looking it up through today's admission rules is
        // what left those chests unrecognised, and empty.
        int cx=b.getX()>>4,cz=b.getZ()>>4;
        Set<String> admitted=new HashSet<>();for(StructurePlanner.Site s:WorldgenExpansion.sites(b.getWorld(),cx,cz))admitted.add(s.key);
        List<StructurePlanner.Site> known=WorldgenExpansion.identify(b.getWorld(),cx,cz);
        for(StructurePlanner.Site s:known)for(StructurePlanner.Marker m:s.markers())if(m.x==b.getX()&&m.y==b.getY()&&m.z==b.getZ()&&isLoot(m.kind))return new Cache(b.getWorld(),s,m,plugin.lootNamespace(),!admitted.contains(s.key));
        // Design drift: a chest on a supply pedestal inside the rooms of a structure that is demonstrably there
        // is a supply cache even when no marker sits exactly on it.
        if(!pedestal(b))return null;
        for(StructurePlanner.Site s:known)if(inside(s,b))return new Cache(b.getWorld(),s,StructurePlanner.Marker.reconstructed(b.getX(),b.getY(),b.getZ(),"supply"),plugin.lootNamespace(),!admitted.contains(s.key));
        return null;
    }
    public static boolean isLoot(String kind){return Arrays.asList("supply","medical","armory","relic","vault").contains(kind);}
    // A reconstructed site's guardian never spawns, so its vault is left open rather than sealed forever.
    private boolean locked(Cache c){if(c.site==null||!c.marker.kind.equals("vault")||c.reconstructed)return false;for(StructurePlanner.Marker m:c.site.markers())if(m.kind.equals("boss"))return !plugin.encounters().defeated(c.site);return false;}
    private synchronized Terrain terrain(World w){
        if(terrain==null||!w.getUID().equals(terrainWorld)){terrain=new Terrain(w.getSeed());caves=new Caves(terrain);terrainWorld=w.getUID();}
        return terrain;
    }
    /** An empty, unclaimed chest inside a located register set piece or dungeon room is stocked once,
     * on first open, from that room's own table (3.21.0). Until then the populator's chest.update()
     * erased those chests as they were built. Never blocks the open: every path returns true. */
    private boolean restockAuthored(Chest chest,Player player){
        Block b=chest.getBlock();
        if(!b.getWorld().getName().equals("world"))return true;
        if(failed||journal==null||player==null||!HorrorPlugin.authenticated(player))return true;
        for(ItemStack item:chest.getBlockInventory().getContents())if(item!=null&&item.getType()!=Material.AIR)return true;
        String key=plugin.lootNamespace()+":"+b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
        if(journal.claimed(key))return true;
        String name;boolean rich;int gearTier;
        try{
            Terrain t=terrain(b.getWorld());
            int k=Megaliths.located(t,b.getX(),b.getY(),b.getZ());
            if(k>=0){name=Megaliths.siteName(k);rich=true;gearTier=Megaliths.gearTier(t,k,b.getX(),b.getY(),b.getZ());}
            else{String room=Dungeons.locate(t,b.getX(),b.getY(),b.getZ());if(room==null)return true;name=room.split("\0")[0];rich=false;gearTier=0;}
        }catch(RuntimeException e){return true;}
        // Journal first, items second: a failed write never hands out a second copy.
        try{if(!journal.claim(key))return true;}catch(IOException e){failed=true;plugin.getLogger().severe("LOOT_WRITE_FAILED: "+e.getMessage());return true;}
        int slots;
        try{slots=Dungeons.restock(b,terrain,caves,rich,gearTier);}catch(RuntimeException e){plugin.getLogger().severe("RESTOCK_FAILED "+e.getMessage());return true;}
        restocked++;
        player.sendMessage(ChatColor.GOLD+name+ChatColor.GRAY+" · cache");
        plugin.getLogger().info("STRUCTURE_LOOT_RESTOCK site="+name+" slots="+slots+" at="+b.getX()+","+b.getY()+","+b.getZ());
        return true;
    }
    private boolean open(Chest chest,Player player){
        Cache c=cache(chest.getBlock());if(c==null)return restockAuthored(chest,player);
        if(player==null||!HorrorPlugin.authenticated(player)||failed||!ExpeditionLoot.ready()){denied++;if(player!=null)player.sendMessage(ChatColor.RED+"This cache is unavailable; your progress is safe. Try again shortly.");return false;}
        if(locked(c)){denied++;player.sendMessage(ChatColor.RED+"Sealed armory — defeat this structure's guardian first.");return false;}
        if(journal.claimed(c.key))return true;
        // Do not overwrite nonempty inventories. This also protects any player-built replacement.
        for(ItemStack item:chest.getBlockInventory().getContents())if(item!=null&&item.getType()!=Material.AIR){try{journal.claim(c.key);}catch(IOException e){failed=true;return false;}return true;}
        List<ItemStack> items;
        try{items=c.site==null?ExpeditionLoot.fold(chest.getWorld().getSeed(),c.room):ExpeditionLoot.roll(chest.getWorld().getSeed(),c.site,c.marker);}catch(RuntimeException e){plugin.getLogger().severe("LOOT_FACTORY_FAILED "+e.getMessage());return false;}
        try{if(!journal.claim(c.key))return true;}catch(IOException e){failed=true;plugin.getLogger().severe("LOOT_WRITE_FAILED; caches locked: "+e.getMessage());return false;}
        List<Integer> slots=new ArrayList<>();for(int i=0;i<27;i++)slots.add(i);Collections.shuffle(slots,new Random(Terrain.mix(c.key.hashCode())));
        int at=0;for(ItemStack item:items){if(at>=slots.size())throw new IllegalStateException("Loot roll exceeds chest capacity");chest.getBlockInventory().setItem(slots.get(at++),item);}
        // getBlockInventory is the LIVE tile inventory. BlockState.update would restore its stale empty snapshot.
        opened++;if(c.reconstructed)reconstructed++;String name=c.site==null?"Fold room "+c.room:c.site.design.name;String role=c.site==null?"liminal":c.marker.kind;
        player.sendMessage(ChatColor.GOLD+name+ChatColor.GRAY+" • "+role+" cache");
        plugin.getLogger().info("STRUCTURE_LOOT_CLAIM site="+(c.site==null?"fold-"+c.room:c.site.key)+" role="+role+" slots="+items.size()+(c.reconstructed?" reconstructed=1":"")+" at="+chest.getX()+","+chest.getY()+","+chest.getZ());return true;
    }
    private List<Chest> chests(InventoryHolder h){List<Chest> list=new ArrayList<>();if(h instanceof Chest)list.add((Chest)h);else if(h instanceof DoubleChest){DoubleChest d=(DoubleChest)h;list.addAll(chests(d.getLeftSide()));list.addAll(chests(d.getRightSide()));}return list;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent e){for(Chest chest:chests(e.getInventory().getHolder()))if(!open(chest,e.getPlayer() instanceof Player?(Player)e.getPlayer():null)){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakCache(BlockBreakEvent e){if(e.getBlock().getState() instanceof Chest&&!open((Chest)e.getBlock().getState(),e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){for(Inventory i:new Inventory[]{e.getSource(),e.getDestination()})for(Chest chest:chests(i.getHolder())){Cache c=cache(chest.getBlock());if(c!=null&&(failed||journal==null||!journal.claimed(c.key)||locked(c))){e.setCancelled(true);return;}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(b->{Cache c=cache(b);return c!=null&&(failed||journal==null||!journal.claimed(c.key)||locked(c));});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(org.bukkit.event.block.BlockExplodeEvent e){e.blockList().removeIf(b->{Cache c=cache(b);return c!=null&&(failed||journal==null||!journal.claimed(c.key)||locked(c));});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityBreak(EntityChangeBlockEvent e){Cache c=cache(e.getBlock());if(c!=null&&(failed||journal==null||!journal.claimed(c.key)||locked(c)))e.setCancelled(true);}
    // Empty-hand use of a decorative block is predicted as a cancelled vanilla no-op.
    // Authorize threshold travel ourselves; LiminalWorld still dispatches a cancellable teleport.
    @EventHandler(priority=EventPriority.HIGHEST) public void door(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null||!HorrorPlugin.authenticated(e.getPlayer()))return;
        Block b=e.getClickedBlock();if(!b.getWorld().getName().equals("world")||b.getType()!=Material.PURPUR_BLOCK
            ||e.getPlayer().getWorld()!=b.getWorld()||e.getPlayer().getLocation().distanceSquared(b.getLocation().add(.5,.5,.5))>36)return;
        // identify(): a Fold threshold in a site built under older rules must still open (3.20.0).
        for(StructurePlanner.Site s:WorldgenExpansion.identify(b.getWorld(),b.getX()>>4,b.getZ()>>4))for(StructurePlanner.Marker m:s.markers())if(m.kind.equals("door")&&m.x==b.getX()&&m.z==b.getZ()&&m.y==b.getY()){e.setCancelled(true);plugin.liminal().enter(e.getPlayer());return;}
    }
}
