package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class HorrorPlugin extends JavaPlugin implements Listener {
    private final Map<UUID,Integer> regions=new HashMap<>();
    private YamlConfiguration relocated;
    private java.io.File relocationFile;
    private final Set<UUID> moving=new HashSet<>();
    private final Map<UUID,Long> arrivalGuard=new HashMap<>();
    private Location safeSpawn;
    private boolean spawnReadyLogged;
    private int spawnSelections,spawnRescues,voidPreventions,spawnFailures;
    private TerrainLighting lighting;
    private SpawnBalance spawnBalance;
    private SpawnerDrive spawnerDrive;
    private StructureLoot loot;
    private StructureEncounters encounters;
    private LiminalWorld liminal;
    private String terrainEpoch="structures-v2";
    public String terrainEpoch(){return terrainEpoch;}
    public String lootNamespace(){return terrainEpoch.equals("structures-v2")?"v2":"v2:"+terrainEpoch;}
    private static java.lang.reflect.Method authInstance,authCheck;
    public static boolean authenticated(Player p){
        if(p==null||!p.isOnline())return false;
        if(Boolean.getBoolean("jaspr.biomes.fixture"))return true;
        try{
            org.bukkit.plugin.Plugin auth=Bukkit.getPluginManager().getPlugin("AuthMe");if(auth==null||!auth.isEnabled())return false;
            if(authInstance==null){Class<?> api=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,auth.getClass().getClassLoader());authInstance=api.getMethod("getInstance");authCheck=api.getMethod("isAuthenticated",Player.class);}
            return Boolean.TRUE.equals(authCheck.invoke(authInstance.invoke(null),p));
        }catch(Exception e){return false;}
    }
    private DiamondRetrofit diamondRetrofit;
    private DungeonRetrofit dungeonRetrofit;
    private EntityVisibility visibility;
    private WaterRepair water;
    private Discovery discovery;
    private Containment containment;
    public EntityVisibility visibility(){return visibility;}
    public StructureEncounters encounters(){return encounters;}
    public LiminalWorld liminal(){return liminal;}
    @Override public void onEnable(){
        int count=Catalog.ALL.size();BiomeMetadata.apply();
        java.io.File epochFile=new java.io.File(getDataFolder(),"terrain-epoch.txt");
        try{if(epochFile.exists())terrainEpoch=new String(java.nio.file.Files.readAllBytes(epochFile.toPath()),java.nio.charset.StandardCharsets.UTF_8).trim();}
        catch(java.io.IOException ex){throw new IllegalStateException("Cannot read terrain epoch",ex);}
        if(!terrainEpoch.matches("structures-v2|details-v3|surface-v4|sparse-v5|rare-v6|rare-v7|caves-v8|caves-v9|caves-v10|caves-v11"))throw new IllegalStateException("Unsupported terrain epoch: "+terrainEpoch);
        relocationFile=new java.io.File(getDataFolder(),"relocated-"+terrainEpoch+".yml");relocated=YamlConfiguration.loadConfiguration(relocationFile);
        // The owner's list of structures that must never be placed again (3.23.0). Read before
        // any world is attached, so no chunk is generated under a list that is not loaded yet.
        DisabledStructures.load(getDataFolder(),getLogger());
        Bukkit.getPluginManager().registerEvents(this,this);
        visibility=new EntityVisibility(this);visibility.start();
        water=new WaterRepair(this);water.start();
        lighting=new TerrainLighting(this);lighting.start();
        // Wild passive animals thinned so the spawn budget goes to the dead (3.21.0).
        spawnBalance=new SpawnBalance(this);spawnBalance.start();
        // Spawners fire day and night, whatever the light level (3.22.0).
        spawnerDrive=new SpawnerDrive(this);spawnerDrive.start();
        for(World w:Bukkit.getWorlds())attach(w);
        Bukkit.getScheduler().runTask(this,()->{World world=Bukkit.getWorld("world");if(world!=null)repairWorldSpawn(world);});
        Bukkit.getScheduler().runTaskTimer(this,this::ambience,100,100);
        // Vanilla animals are seeded at generation by RuinSupplies; land explored before
        // that existed is topped up here so wool is obtainable without walking to new chunks.
        Bukkit.getScheduler().runTaskTimer(this,()->{
            World fauna=Bukkit.getWorld("world");
            if(fauna==null)return;
            java.util.Random rng=new java.util.Random();
            VanillaFauna.restock(fauna,rng,6,140);
            VanillaFauna.topUpSheep(fauna,rng,40);
        },600L,600L);
        // The ore table gained a second diamond vein; ground generated before that
        // would otherwise keep the old density permanently. Runs once per world.
        Bukkit.getScheduler().runTask(this,()->{
            World ore=Bukkit.getWorld("world");
            if(ore!=null){diamondRetrofit=new DiamondRetrofit(this,ore);diamondRetrofit.start();}
        });
        // The dungeon lattice was tightened and six sites added; ground explored before that
        // would keep the old, sparser layout forever. Runs once per world, after the ore pass.
        Bukkit.getScheduler().runTaskLater(this,()->{
            World rooms=Bukkit.getWorld("world");
            if(rooms!=null){dungeonRetrofit=new DungeonRetrofit(this,rooms);dungeonRetrofit.start();}
        },400L);
        // Naming the place you have walked into is what turns a list of structures into
        // somewhere to go. Started with the rest of the runtime services, after the world.
        Bukkit.getScheduler().runTask(this,()->{discovery=new Discovery(this);discovery.start();});
        // What is actually in the containment cells, and what is waiting in the last room.
        Bukkit.getScheduler().runTask(this,()->{containment=new Containment(this);containment.start();});
        encounters=new StructureEncounters(this);loot=new StructureLoot(this);liminal=new LiminalWorld(this);
        // Defer runtime services until POSTWORLD dependencies (AuthMe and Arsenal) are ready.
        Bukkit.getScheduler().runTask(this,()->{encounters.start();loot.start();liminal.start();getLogger().info("STRUCTURES_READY version=7 biomeSpecific=true surfaceReservedCells=75% relativeStructureDensity="+Math.round(StructurePlanner.RELATIVE_STRUCTURE_DENSITY*100)+"% spawnExclusion="+StructurePlanner.SPAWN_EXCLUSION_RADIUS+" valuableConstructionBlocks=false loot=journaled designs="+StructureCatalog.ALL.size()+" terrainEpoch="+terrainEpoch);});
        getLogger().info("HORROR_BIOMES_READY version="+getDescription().getVersion()+" replacements="+count+" artificialClutter=false naturalTerrain=true naturalRivers=true naturalTrees=true naturalFoliage=true circles=9 structureDensity=10% spawnExclusion="+StructurePlanner.SPAWN_EXCLUSION_RADIUS+" generator="+terrainEpoch+" caveRegions=5 caveRegionSize="+Caves.REGION+" oreMode=veins dungeonAttempts="+Dungeons.ATTEMPTS+"/chunk surfaceOpenings=10");
    }
    @Override public void onDisable(){regions.clear();moving.clear();arrivalGuard.clear();safeSpawn=null;spawnReadyLogged=false;if(diamondRetrofit!=null)diamondRetrofit.stop();if(dungeonRetrofit!=null)dungeonRetrofit.stop();if(water!=null)water.stop();if(lighting!=null)lighting.stop();if(loot!=null)loot.stop();if(encounters!=null)encounters.stop();if(spawnBalance!=null)spawnBalance.stop();if(spawnerDrive!=null)spawnerDrive.stop();if(liminal!=null)liminal.stop();if(discovery!=null)discovery.stop();if(containment!=null)containment.stop();WorldgenExpansion.clear();authInstance=null;authCheck=null;}
    @EventHandler(priority=EventPriority.HIGHEST) public void safeArrival(PlayerSpawnLocationEvent e){
        World w=Bukkit.getWorld("world");if(w==null)return;
        UUID id=e.getPlayer().getUniqueId();boolean relocation=!relocated.getBoolean(id.toString(),false);
        Location offered=e.getSpawnLocation();boolean unsafe=SpawnSafety.voidRisk(offered);
        if(!relocation&&!unsafe)return;
        Location spawn=repairWorldSpawn(w);if(spawn==null){spawnFailures++;getLogger().severe("SPAWN_GUARD_FAILED phase=selection player="+e.getPlayer().getName());return;}
        e.setSpawnLocation(spawn.clone());arrivalGuard.put(id,System.currentTimeMillis()+15000L);spawnSelections++;
        if(relocation)moving.add(id);
        getLogger().info("SPAWN_GUARD_SELECT player="+e.getPlayer().getName()+" reason="+(relocation?"terrain-relocation":"unsafe-saved-position")+" from="+where(offered)+" to="+where(spawn));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void arrived(PlayerJoinEvent e){
        Player player=e.getPlayer();UUID id=player.getUniqueId();arrivalGuard.put(id,System.currentTimeMillis()+15000L);
        player.setFallDistance(0);player.setVelocity(new org.bukkit.util.Vector());
        for(long delay:new long[]{1L,5L,20L,60L,120L,240L})Bukkit.getScheduler().runTaskLater(this,()->checkArrival(player,"join+"+delay),delay);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){
        Location offered=e.getRespawnLocation();if(offered==null||offered.getWorld()==null||!offered.getWorld().getName().equals("world")||!SpawnSafety.voidRisk(offered))return;
        Location spawn=repairWorldSpawn(offered.getWorld());if(spawn==null){spawnFailures++;return;}
        e.setRespawnLocation(spawn.clone());arrivalGuard.put(e.getPlayer().getUniqueId(),System.currentTimeMillis()+15000L);spawnSelections++;
        getLogger().warning("SPAWN_GUARD_RESPAWN player="+e.getPlayer().getName()+" from="+where(offered)+" to="+where(spawn));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void moved(PlayerMoveEvent e){
        if(!guarded(e.getPlayer())||!SpawnSafety.voidRisk(e.getTo()))return;
        Location spawn=safeFor(e.getPlayer());if(spawn==null)return;e.setTo(spawn);resetMotion(e.getPlayer());spawnRescues++;
        getLogger().warning("SPAWN_GUARD_RESCUE phase=move player="+e.getPlayer().getName()+" from="+where(e.getFrom())+" to="+where(spawn));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void voidDamage(EntityDamageEvent e){
        if(e.getCause()!=EntityDamageEvent.DamageCause.VOID||!(e.getEntity() instanceof Player))return;Player player=(Player)e.getEntity();if(!guarded(player))return;
        Location spawn=safeFor(player);if(spawn==null)return;e.setCancelled(true);voidPreventions++;
        Bukkit.getScheduler().runTask(this,()->{if(player.isOnline()){player.teleport(spawn);resetMotion(player);}});
        getLogger().warning("SPAWN_GUARD_VOID_PREVENTED player="+player.getName()+" from="+where(player.getLocation())+" to="+where(spawn));
    }
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();arrivalGuard.remove(id);moving.remove(id);}
    public SpawnerDrive spawnerDrive(){return spawnerDrive;}
    @Override public ChunkGenerator getDefaultWorldGenerator(String name,String id){if(!name.equals("world"))throw new IllegalArgumentException("Custom overworld generator only supports world");return new HorrorGenerator();}
    @EventHandler public void init(WorldInitEvent e){attach(e.getWorld());}
    @EventHandler public void loaded(WorldLoadEvent e){if(e.getWorld().getName().equals("world"))Bukkit.getScheduler().runTask(this,()->repairWorldSpawn(e.getWorld()));}
    private void attach(World w){
        if(w.getName().equals("world"))getLogger().info("EXPANSION_BOUNDARY_READY protectedChunks="+WorldgenExpansion.initialize(w).protectedChunks());
        if(w.getName().equals("world")){int n=StructureRates.initialize(w);
            if(n<0)getLogger().severe("RATES_BOUNDARY_FAILED file="+StructureRates.BOUNDARY_FILE+" -- the 3.25.0 extra structures are disabled; older placement is unaffected");
            else getLogger().info("RATES_BOUNDARY_READY protectedChunks="+n+" catalogueDensity="+Math.round(StructurePlanner.RELATIVE_STRUCTURE_DENSITY*100)+"% setPieceLattices=2 roomLattices=+1 vanillaRoomAttempts="+Dungeons.ATTEMPTS+" sanctuaries=+50%");
            // 3.28.0: the second 1.5x (tier 2). Fails closed like the first: without it no tier-2 site is placed.
            int n2=StructureRates.initializeV2(w);
            if(n2<0)getLogger().severe("RATES_V2_BOUNDARY_FAILED file="+StructureRates.BOUNDARY_FILE_V2+" -- the 3.28.0 extra structures are disabled; older placement is unaffected");
            else getLogger().info("RATES_V2_BOUNDARY_READY protectedChunks="+n2+" tier2=set-pieces,rooms-D,catalogue,sanctuaries vanillaRoomAttempts="+Dungeons.ATTEMPTS_V2);}
        if(w.getEnvironment()!=World.Environment.NORMAL&&!w.getPopulators().stream().anyMatch(p->p instanceof OuterRealms))w.getPopulators().add(new OuterRealms());
    }
    public Catalog.Profile at(World w,int x,int z){return w.getEnvironment()==World.Environment.NORMAL?new Terrain(w.getSeed()).sample(x,z).profile:OuterRealms.profile(w,x,z);}
    private void ambience(){
        Set<UUID> online=new HashSet<>();for(Player p:Bukkit.getOnlinePlayers()){
            online.add(p.getUniqueId());if(p.getWorld().getName().equals("jaspr_backrooms"))continue;Catalog.Profile b=at(p.getWorld(),p.getLocation().getBlockX(),p.getLocation().getBlockZ());Integer old=regions.put(p.getUniqueId(),b.index);
            if(old==null||old!=b.index)p.sendTitle(ChatColor.GRAY+b.name,ChatColor.DARK_GRAY+"The world remembers.",10,45,15);
            // Low-rate local accents only: no blindness, forced sound settings, emissive entities or network frame loop.
            if(p.getWorld().getFullTime()%600<100){
                Sound sound=b.atmosphere.equals("whisper")?Sound.AMBIENT_CAVE:b.atmosphere.equals("static")?Sound.BLOCK_REDSTONE_TORCH_BURNOUT:Sound.ENTITY_ENDERMEN_AMBIENT;
                p.playSound(p.getLocation(),sound,.12f,.5f);
            }
        }regions.keySet().retainAll(online);
    }
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] args){
        if(s instanceof Player&&!authenticated((Player)s))return true;
        if(c.getName().equalsIgnoreCase("where"))return Survey.report(s,terrainEpoch);
        if(args.length>0&&args[0].equalsIgnoreCase("return")){if(s instanceof Player)liminal.leave((Player)s);return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("structures")){
            s.sendMessage(ChatColor.GOLD+"Expedition Atlas — "+StructureCatalog.ALL.size()+" biome-specific ruins and dungeons");
            s.sendMessage("Surface landmarks dominate the expedition grid; buried and underwater discoveries remain in the mixed cells.");
            s.sendMessage("I: refuge / II: scavenger / III: dangerous / IV: elite / V: apex");
            s.sendMessage("Food stores, medical rooms, armories, relic caches and sealed guardian vaults contain different loot. Caches do not refill.");
            s.sendMessage("Guns and exoskeletons: elite armories and boss vaults. Keep marked ammunition; sneak + right-click reloads.");
            s.sendMessage("Right-click a strange threshold to explore the Fold. /wasteland return is your emergency exit.");
            s.sendMessage(ChatColor.AQUA+"Use /wasteland nearby for directions to the closest surface landmarks.");
            if(s instanceof Player){Player p=(Player)s;if(p.getWorld().getName().equals("world"))for(StructurePlanner.Site site:WorldgenExpansion.sites(p.getWorld(),p.getLocation().getBlockX()>>4,p.getLocation().getBlockZ()>>4))s.sendMessage(ChatColor.AQUA+site.design.name+" — tier "+site.design.tier);}
            return true;
        }
        if(args.length>0&&args[0].equalsIgnoreCase("nearby")){
            if(!(s instanceof Player))return true;Player p=(Player)s;
            if(!p.getWorld().getName().equals("world")){s.sendMessage(ChatColor.GRAY+"Surface landmarks can only be charted from the overworld.");return true;}
            List<StructurePlanner.Site> sites=WorldgenExpansion.nearestSurface(p.getWorld(),p.getLocation().getBlockX(),p.getLocation().getBlockZ(),4096,3);
            if(sites.isEmpty()){s.sendMessage(ChatColor.GRAY+"No surface landmark was charted within 4,096 blocks.");return true;}
            s.sendMessage(ChatColor.GOLD+"Nearest surface landmarks");
            for(StructurePlanner.Site site:sites){int dx=site.anchorX-p.getLocation().getBlockX(),dz=site.anchorZ-p.getLocation().getBlockZ();int distance=(int)Math.round(Math.hypot(dx,dz));s.sendMessage(ChatColor.AQUA+site.design.name+ChatColor.GRAY+" — "+direction(dx,dz)+", "+distance+" blocks, tier "+site.design.tier);}
            return true;
        }
        if(args.length>0&&args[0].equalsIgnoreCase("status")){if(s instanceof Player&&!s.hasPermission("jaspr.biomes.admin"))return true;s.sendMessage("Horror structures v7 | 90% rarer globally | first "+StructurePlanner.SPAWN_EXCLUSION_RADIUS+" blocks structure-free | no iron/gold/diamond construction blocks | artificial biome clutter off | natural rivers, trees and foliage retained | "+StructureCatalog.ALL.size()+" designs | 75% of admitted expansion cells are surface landmarks | epoch="+terrainEpoch+" | spawnSelections="+spawnSelections+",spawnRescues="+spawnRescues+",voidPreventions="+voidPreventions+",spawnFailures="+spawnFailures+" | "+loot.metrics()+" | "+encounters.metrics());return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("atlas")){
            int page=1;try{if(args.length>1)page=Integer.parseInt(args[1]);}catch(NumberFormatException ignored){}page=Math.max(1,Math.min(8,page));s.sendMessage(ChatColor.GOLD+"Wasteland Atlas "+page+"/8");
            for(int i=(page-1)*8;i<Math.min(62,page*8);i++){Catalog.Profile p=Catalog.ALL.get(i);s.sendMessage(ChatColor.GRAY+""+(i+1)+". "+p.name+ChatColor.DARK_GRAY+" - "+p.landmark);}
            s.sendMessage("/wasteland atlas "+(page%8+1));return true;
        }
        if(args.length>0&&args[0].equalsIgnoreCase("portal")){
            int x=0,z=0;if(s instanceof Player){x=((Player)s).getLocation().getBlockX();z=((Player)s).getLocation().getBlockZ();}
            int[] at=StructureRates.nearestSanctuary(new Terrain(s instanceof Player?((Player)s).getWorld().getSeed():Bukkit.getWorlds().get(0).getSeed()),x,z);
            int cx=at[0],cz=at[1];
            s.sendMessage(ChatColor.GOLD+"A ruined portal sanctuary lies at overworld X="+cx+", Z="+cz+". Bring twelve Eyes of Ender.");return true;
        }
        if(s instanceof Player){Player p=(Player)s;Catalog.Profile b=at(p.getWorld(),p.getLocation().getBlockX(),p.getLocation().getBlockZ());s.sendMessage(ChatColor.GOLD+b.name);s.sendMessage(ChatColor.GRAY+"Inspired by "+b.inspiration);}
        s.sendMessage("/wasteland nearby | /wasteland atlas | /wasteland structures | /wasteland portal | /wasteland return");return true;
    }
    private static String direction(int dx,int dz){
        String northSouth=dz<0?"north":"south",eastWest=dx<0?"west":"east";
        if(Math.abs(dx)*2<Math.abs(dz))return northSouth;if(Math.abs(dz)*2<Math.abs(dx))return eastWest;return northSouth+"-"+eastWest;
    }
    Location repairWorldSpawn(World world){
        if(world==null||!world.getName().equals("world"))return null;
        Location old=world.getSpawnLocation();int x=old==null?0:old.getBlockX(),z=old==null?0:old.getBlockZ();
        Location resolved=safeSpawn!=null&&safeSpawn.getWorld()==world&&SpawnSafety.standable(safeSpawn)?safeSpawn.clone():SpawnSafety.resolve(world,x,z);
        if(resolved==null){spawnFailures++;getLogger().severe("SPAWN_GUARD_FAILED phase=world-spawn center="+x+","+z);return null;}
        boolean changed=old==null||old.getBlockX()!=resolved.getBlockX()||old.getBlockY()!=resolved.getBlockY()||old.getBlockZ()!=resolved.getBlockZ()||!SpawnSafety.standable(old);
        if(changed&&!world.setSpawnLocation(resolved.getBlockX(),resolved.getBlockY(),resolved.getBlockZ())){spawnFailures++;getLogger().severe("SPAWN_GUARD_FAILED phase=metadata-write candidate="+where(resolved));return null;}
        safeSpawn=resolved.clone();if(changed||!spawnReadyLogged)getLogger().info("SPAWN_GUARD_READY world=world previous="+where(old)+" safe="+where(resolved)+" metadataRepaired="+changed);spawnReadyLogged=true;return resolved.clone();
    }
    private void checkArrival(Player player,String phase){
        if(player==null||!player.isOnline())return;UUID id=player.getUniqueId();
        if(guarded(player)&&SpawnSafety.voidRisk(player.getLocation())){
            Location from=player.getLocation().clone(),spawn=safeFor(player);
            if(spawn!=null&&player.teleport(spawn)){resetMotion(player);spawnRescues++;getLogger().warning("SPAWN_GUARD_RESCUE phase="+phase+" player="+player.getName()+" from="+where(from)+" to="+where(spawn));}
            else{spawnFailures++;getLogger().severe("SPAWN_GUARD_FAILED phase="+phase+" player="+player.getName());return;}
        }
        if(moving.contains(id)&&!SpawnSafety.voidRisk(player.getLocation()))completeRelocation(player);
        Long until=arrivalGuard.get(id);if(until!=null&&until<System.currentTimeMillis())arrivalGuard.remove(id);
    }
    private void completeRelocation(Player player){
        UUID id=player.getUniqueId();if(!moving.remove(id))return;relocated.set(id.toString(),true);
        try{getDataFolder().mkdirs();relocated.save(relocationFile);}catch(java.io.IOException ex){getLogger().severe("BIOME_RELOCATION_SAVE_FAILED: "+ex.getClass().getSimpleName());return;}
        player.sendMessage(ChatColor.GRAY+"The terrain has changed. Your inventory, Ender Chest and character progress are retained. /wasteland nearby");
    }
    private boolean guarded(Player player){Long until=arrivalGuard.get(player.getUniqueId());return until!=null&&until>=System.currentTimeMillis();}
    private Location safeFor(Player player){World world=Bukkit.getWorld("world");Location spawn=repairWorldSpawn(world);if(spawn==null)spawnFailures++;return spawn;}
    private static void resetMotion(Player player){player.setFallDistance(0);player.setVelocity(new org.bukkit.util.Vector());}
    private static String where(Location location){return location==null||location.getWorld()==null?"none":location.getWorld().getName()+":"+round(location.getX())+","+round(location.getY())+","+round(location.getZ());}
    private static String round(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
}
