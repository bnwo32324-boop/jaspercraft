package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class DungeonPlugin extends JavaPlugin implements Listener {
    public static final int GENERATION_VERSION=4;
    public String worldName;public DungeonGenerator generator;public World dungeon;public Encounters encounters;public Hazards hazards;public Gates gates;public Relics relics;public Sanctuary sanctuary;
    public Arsenal arsenal;public CreativeCatalog creative;public Rifts rifts;
    private final Map<UUID,Long> doorCooldown=new HashMap<>();private final Map<UUID,String> lastRoom=new HashMap<>();
    @Override public void onEnable(){
        saveDefaultConfig();if(!getConfig().getBoolean("enabled",true))return;
        worldName=getConfig().getString("world-name","jaspr_dungeon");
        if(!worldName.matches("[a-z0-9_-]+")||Bukkit.getWorlds().isEmpty()||worldName.equals(Bukkit.getWorlds().get(0).getName()))throw new IllegalStateException("Unsafe dungeon world name");
        long seed=Bukkit.getWorlds().get(0).getSeed()^getConfig().getLong("seed-salt",709327916L);
        File manifest=new File(getDataFolder(),"dimension.yml");
        try{org.bukkit.configuration.file.YamlConfiguration meta=new org.bukkit.configuration.file.YamlConfiguration();
            if(manifest.exists()){meta.load(manifest);if(!worldName.equals(meta.getString("world"))){archive(meta);meta=new org.bukkit.configuration.file.YamlConfiguration();}}
            if(manifest.exists()){if(meta.getInt("version")!=GENERATION_VERSION)throw new IllegalStateException("Dungeon generation identity differs: "+worldName+" is generation "+meta.getInt("version")+", not "+GENERATION_VERSION+". Never overwrite old rooms with a different layout; set a new world-name to start a new dimension.");Object savedSeed=meta.get("seed");if(!(savedSeed instanceof Long)&&!(savedSeed instanceof Integer)&&!(savedSeed instanceof Short)&&!(savedSeed instanceof Byte))throw new IllegalStateException("Dungeon seed missing or non-integral; preserve the manifest and restore its original seed.");seed=((Number)savedSeed).longValue();}
            else{if(new File(Bukkit.getWorldContainer(),worldName).exists())throw new IllegalStateException("Refusing an existing world without this plugin's dimension manifest");meta.set("version",GENERATION_VERSION);meta.set("world",worldName);meta.set("seed",seed);RoomStore.atomic(manifest,meta.saveToString());}
        }catch(Exception ex){throw new IllegalStateException("Cannot establish dungeon identity safely",ex);}
        generator=new DungeonGenerator(seed);encounters=new Encounters(this);hazards=new Hazards(this);gates=new Gates(this);relics=new Relics(this);sanctuary=new Sanctuary(this);
        arsenal=new Arsenal(this);creative=new CreativeCatalog(this);rifts=new Rifts(this);
        Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getPluginManager().registerEvents(encounters,this);Bukkit.getPluginManager().registerEvents(hazards,this);Bukkit.getPluginManager().registerEvents(gates,this);Bukkit.getPluginManager().registerEvents(relics,this);
        Bukkit.getPluginManager().registerEvents(sanctuary,this);
        Bukkit.getPluginManager().registerEvents(arsenal,this);Bukkit.getPluginManager().registerEvents(creative,this);Bukkit.getPluginManager().registerEvents(rifts,this);
        // Load an existing dimension before returning players join; create a new one on first portal use.
        if(new File(Bukkit.getWorldContainer(),worldName).isDirectory())ensureWorld();
        rifts.loadExisting();
        try{creative.export(new File(getDataFolder(),"creative-catalog.json"));}catch(Exception ex){throw new IllegalStateException("Cannot export dungeon item catalogue",ex);}
        Bukkit.getScheduler().runTaskTimer(this,()->{gates.tick();encounters.tick();sanctuary.tick();arsenal.tick();rifts.tick();},1,1);
        getLogger().info("DUNGEON_READY world="+worldName+" generation="+GENERATION_VERSION+" hazards="+HazardCatalog.Type.values().length+" chests=normal portal=stone-bricks");
    }
    /** A new world-name starts a new dimension. The old one's identity and journals move intact to archive/; nothing is deleted. */
    private void archive(org.bukkit.configuration.file.YamlConfiguration old) throws java.io.IOException {
        String from=String.valueOf(old.getString("world"));int version=old.getInt("version");
        if(new File(Bukkit.getWorldContainer(),worldName).exists())throw new java.io.IOException("Refusing to start "+worldName+" over an existing world folder");
        File target=new File(getDataFolder(),"archive/"+from.replaceAll("[^a-z0-9_-]","_")+"-generation"+version+"-"+System.currentTimeMillis());
        if(!target.mkdirs())throw new java.io.IOException("Cannot create dungeon archive "+target.getName());
        // The manifest moves last, so an interrupted handover repeats on the next start instead of mixing generations.
        for(String name:new String[]{"rooms","rooms-realms","rifts","dimension.yml"}){File f=new File(getDataFolder(),name);if(f.exists())java.nio.file.Files.move(f.toPath(),new File(target,name).toPath());}
        getLogger().warning("DUNGEON_GENERATION_ARCHIVED from="+from+" version="+version+" to="+worldName+" archive=archive/"+target.getName());
    }
    @Override public void onDisable(){if(encounters!=null)encounters.close();if(gates!=null)gates.saveQuietly();if(arsenal!=null)arsenal.close();if(rifts!=null)rifts.close();}
    public boolean inside(World w){return w!=null&&(w.getName().equals(worldName)||(rifts!=null&&rifts.contains(w)));}
    public DungeonGenerator generator(World w){DungeonGenerator g=rifts==null?null:rifts.generator(w);return g==null?generator:g;}
    public Layout.Room room(Location l){return generator(l.getWorld()).layout.at(l.getBlockX(),l.getBlockZ());}
    public String roomKey(World w,Layout.Room r){return w!=null&&w.getName().equals(worldName)?r.id():w.getName()+"/"+r.id();}
    public String roomKey(Location l){return roomKey(l.getWorld(),room(l));}
    public int realm(World w){return rifts==null?0:Math.max(0,rifts.realm(w));}
    public double rewardMultiplier(World w){return Math.max(.1,Math.min(3,getConfig().getDouble("loot-multiplier",1)))*(1+.20*realm(w));}
    public double dangerMultiplier(World w){return 1+.15*realm(w);}
    public Collection<World> dungeonWorlds(){List<World> out=new ArrayList<>();if(dungeon!=null)out.add(dungeon);if(rifts!=null)out.addAll(rifts.worlds());return out;}
    public World ensureWorld(){
        if(dungeon!=null)return dungeon;
        World existing=Bukkit.getWorld(worldName);if(existing!=null&&!(existing.getGenerator() instanceof DungeonGenerator))throw new IllegalStateException("Existing dungeon world has another generator");
        dungeon=existing!=null?existing:new WorldCreator(worldName).environment(World.Environment.NORMAL).seed(generator.layout.seed).generateStructures(false).generator(generator).createWorld();
        if(dungeon==null)throw new IllegalStateException("Dungeon world failed to load");
        dungeon.setKeepSpawnInMemory(false);dungeon.setSpawnLocation(16,65,16);dungeon.setDifficulty(Difficulty.NORMAL);dungeon.setTime(18000);dungeon.setStorm(false);
        for(String rule:new String[]{"doDaylightCycle","doWeatherCycle","doMobSpawning","doFireTick","mobGriefing"})dungeon.setGameRuleValue(rule,"false");
        dungeon.setGameRuleValue("keepInventory","false");gates.installReturnGate();getLogger().info("DUNGEON_WORLD_LOADED "+worldName);return dungeon;
    }
    @EventHandler public void init(WorldInitEvent e){if(inside(e.getWorld())){e.getWorld().setKeepSpawnInMemory(false);((org.bukkit.craftbukkit.v1_12_R1.CraftWorld)e.getWorld()).getHandle().spigotConfig.randomLightUpdates=true;}}
    public boolean move(Player p,Location to){if(to==null||p.isDead()||!p.isOnline())return false;p.leaveVehicle();boolean ok=p.teleport(to,PlayerTeleportEvent.TeleportCause.PLUGIN);if(ok){p.setFallDistance(0);p.setVelocity(new Vector());doorCooldown.put(p.getUniqueId(),System.currentTimeMillis()+900);}return ok;}
    @EventHandler(ignoreCancelled=true) public void walk(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent||!inside(e.getTo().getWorld()))return;Player p=e.getPlayer();Location l=e.getTo();Layout.Room r=room(e.getFrom());
        if(System.currentTimeMillis()<doorCooldown.getOrDefault(p.getUniqueId(),0L))return;
        int dx=0,dz=0;
        if(Layout.Room.lane(l.getBlockZ())){if(l.getX()<r.x+2.9&&l.getX()<e.getFrom().getX())dx=-1;else if(l.getX()>r.x+r.w-2.9&&l.getX()>e.getFrom().getX())dx=1;}
        if(Layout.Room.lane(l.getBlockX())){if(l.getZ()<r.z+2.9&&l.getZ()<e.getFrom().getZ())dz=-1;else if(l.getZ()>r.z+r.d-2.9&&l.getZ()>e.getFrom().getZ())dz=1;}
        if((dx!=0||dz!=0)&&l.getY()>=65&&l.getY()<69){
            Location to=l.clone();if(dx!=0)to.setX(dx<0?r.x-3.5:r.x+r.w+3.5);else to.setZ(dz<0?r.z-3.5:r.z+r.d+3.5);to.setY(65);
            if(Math.abs(to.getX())>29999000||Math.abs(to.getZ())>29999000)return;
            // Replacing the event destination lets CraftBukkit perform the transition. Cancelling
            // and teleporting inside this callback would make CraftBukkit teleport back afterward.
            e.setTo(to);p.setFallDistance(0);doorCooldown.put(p.getUniqueId(),System.currentTimeMillis()+900);return;
        }
        if(l.getY()<63||l.getY()>r.roof()){e.setTo(new Location(l.getWorld(),r.cx()+.5,65,r.cz()+.5,l.getYaw(),l.getPitch()));p.setFallDistance(0);}
    }
    public void announce(Player p,Layout.Room r){
        if(roomKey(p.getWorld(),r).equals(lastRoom.put(p.getUniqueId(),roomKey(p.getWorld(),r))))return;
        String dangers=HazardCatalog.names(r);
        p.sendTitle(ChatColor.DARK_RED+r.title(),ChatColor.GRAY+"Threat "+r.tier+"/5 | "+dangers,5,45,12);
        p.sendMessage(ChatColor.DARK_GRAY+"[Dungeon Dimension] "+r.title()+" | Dangers: "+dangers+" | "+r.w+" x "+r.d+" | Threat "+r.tier+"/5. Walk into barred doorways to leave."+(r.dormant()?" Its guardians wake when the chest is opened.":""));
    }
    @EventHandler public void quit(PlayerQuitEvent e){doorCooldown.remove(e.getPlayer().getUniqueId());lastRoom.remove(e.getPlayer().getUniqueId());gates.forget(e.getPlayer());}
    @EventHandler public void changed(PlayerChangedWorldEvent e){lastRoom.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(inside(e.getBlock().getWorld())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(inside(e.getBlock().getWorld())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent e){if(inside(e.getLocation().getWorld()))e.blockList().clear();}
    @EventHandler(priority=EventPriority.HIGHEST) public void blockExplode(BlockExplodeEvent e){if(inside(e.getBlock().getWorld()))e.blockList().clear();}
    @EventHandler(ignoreCancelled=true) public void grief(EntityChangeBlockEvent e){if(inside(e.getBlock().getWorld()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void liquid(BlockFromToEvent e){if(inside(e.getBlock().getWorld()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(inside(e.getBlockClicked().getWorld()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void fill(PlayerBucketFillEvent e){if(inside(e.getBlockClicked().getWorld()))e.setCancelled(true);}
    /**
     * A stone-brick gate is made of portal blocks, so vanilla would send anyone touching it to the Nether (instantly in
     * Creative, after four seconds in Survival). Blocked on any contact, first thing and again last thing, so no other
     * plugin can route the trip either (owner 2026-10-03: a half-entered gate sent a player to the Nether).
     */
    @EventHandler(priority=EventPriority.LOWEST) public void vanillaPortalFirst(PlayerPortalEvent e){vanillaPortal(e);}
    @EventHandler(priority=EventPriority.HIGHEST) public void vanillaPortal(PlayerPortalEvent e){
        if(e.isCancelled())return;
        if(inside(e.getFrom().getWorld())||gates.at(e.getFrom())!=null||gates.touching(e.getFrom(),.8,2)!=null){e.setCancelled(true);getLogger().info("DUNGEON_VANILLA_PORTAL_BLOCKED player="+e.getPlayer().getName()+" world="+e.getFrom().getWorld().getName());}
    }
    @EventHandler public void respawn(PlayerRespawnEvent e){if(inside(e.getRespawnLocation().getWorld())){Location safe=gates.returnLocation(e.getPlayer());if(safe==null)safe=sanctuary.arrival();if(safe!=null)e.setRespawnLocation(safe);else e.getPlayer().kickPlayer("No safe dungeon recovery position exists. Ask an administrator to clear the refuge or your return portal.");}}
    // If an administrator has obstructed every recovery square, never reconnect into the same trap.
    @EventHandler public void joined(PlayerJoinEvent e){Player p=e.getPlayer();if(inside(p.getWorld())&&sanctuary.contains(p.getLocation())&&!Sanctuary.safeFloor(p.getLocation())){Location safe=sanctuary.arrival();if(safe==null)safe=gates.returnLocation(p);if(safe==null)p.kickPlayer("Dungeon refuge and return exit are obstructed. Ask an administrator to restore a safe landing.");else move(p,safe);}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(encounters==null){sender.sendMessage("The dungeon is disabled.");return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("status")){sender.sendMessage("DUNGEON_READY world="+worldName+" loaded="+(dungeon!=null)+" activeRooms="+encounters.active.size()+" generation="+GENERATION_VERSION+" hazards="+HazardCatalog.Type.values().length+" themes="+Layout.THEMES.length+" motifs="+Layout.MOTIF_COUNT+" bosses="+EncounterCatalog.COUNT+" baubles="+Relics.Type.values().length);return true;}
        if(!(sender instanceof Player)){sender.sendMessage("Use dungeon status; player travel requires a player.");return true;}Player p=(Player)sender;
        if(args.length>0&&args[0].equalsIgnoreCase("baubles")){relics.open(p);return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("items")){int page=0;try{if(args.length>1)page=Integer.parseInt(args[1])-1;}catch(NumberFormatException ignored){}creative.open(p,page);return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("leave")){if(inside(p.getWorld())){if(!rifts.leave(p))gates.leave(p);}else p.sendMessage("You are not in the dungeon.");return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("where")){if(!inside(p.getWorld())){p.sendMessage("Outside the Dungeon Dimension.");return true;}Layout.Room r=room(p.getLocation());
            p.sendMessage((rifts.contains(p.getWorld())?rifts.displayName(p.getWorld()):"The Dungeon Dimension")+" | "+r.title()+" | "+roomKey(p.getLocation())+" | "+Layout.MOTIFS[r.motif]+" | Threat "+r.tier+"/5 | Dangers: "+HazardCatalog.names(r));return true;}
        if(args.length==3&&args[0].equalsIgnoreCase("visit")){
            if(p.getGameMode()!=GameMode.CREATIVE||!p.hasPermission("jaspr.dungeon.admin")){p.sendMessage("Inspection travel requires a Creative administrator.");return true;}
            try{int x=Integer.parseInt(args[1]),z=Integer.parseInt(args[2]);if(Math.abs((long)x)>900000||Math.abs((long)z)>900000)throw new NumberFormatException();World w=ensureWorld();Layout.Room r=generator.layout.at(x*32,z*32);if(!inside(p.getWorld()))gates.remember(p);move(p,new Location(w,r.cx()+.5,65,r.cz()+.5));}catch(Exception ex){p.sendMessage("Usage: /dungeon visit <roomX> <roomZ>");getLogger().warning("DUNGEON_VISIT_FAILED "+ex.getMessage());}return true;
        }
        p.sendMessage(ChatColor.GOLD+"The Dungeon Dimension: build a 4 x 5 stone-brick frame (2 x 3 opening), light it with flint and steel, and step inside. Every room has its own dangers. /dungeon where | /dungeon leave");return true;
    }
}
