package chat.jaspr.biomes;
import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/** Console-only isolated-world integration checks and browser inspection controls. */
public final class StructuresProbe extends JavaPlugin implements Listener {
    private int assertions;private final List<StructurePlanner.Site> sites=new ArrayList<>();private boolean busy;
    public void onEnable(){if(!Boolean.getBoolean("jaspr.biomes.fixture"))throw new IllegalStateException("Fixture flag required");Bukkit.getPluginManager().registerEvents(this,this);}
    private void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->{try{org.bukkit.plugin.Plugin a=Bukkit.getPluginManager().getPlugin("AuthMe");Class<?> cls=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,a.getClass().getClassLoader());Object api=cls.getMethod("getInstance").invoke(null);cls.getMethod("registerPlayer",String.class,String.class).invoke(api,e.getPlayer().getName(),UUID.randomUUID().toString());cls.getMethod("forceLogin",Player.class).invoke(api,e.getPlayer());e.getPlayer().setGameMode(GameMode.CREATIVE);e.getPlayer().setAllowFlight(true);e.getPlayer().setFlying(true);show(0);}catch(Exception ex){throw new RuntimeException(ex);}},40);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof ConsoleCommandSender))return true;
        try{
            if(args.length>0&&args[0].equals("stats")){for(Player p:Bukkit.getOnlinePlayers()){World w=p.getWorld();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)w.getBlockAt(x,210,z).setType(Material.STONE);p.teleport(new Location(w,.5,211,.5,180,0));p.setGameMode(GameMode.SURVIVAL);p.setInvulnerable(true);p.setLevel(0);p.setExp(0);p.setTotalExperience(0);p.giveExp(1000);p.setHealth(20);p.setFoodLevel(20);p.getInventory().setHeldItemSlot(8);getLogger().info("STATS_BROWSER_READY player="+p.getName()+" level="+p.getLevel()+" exp="+p.getExp());}return true;}
            if(args.length>0&&args[0].equals("statinfo")){for(Player p:Bukkit.getOnlinePlayers()){p.saveData();getLogger().info("STATS_BROWSER_STATE player="+p.getName()+" level="+p.getLevel()+" exp="+p.getExp()+" total="+p.getTotalExperience()+" maxHealth="+p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()+" tags="+p.getScoreboardTags()+" menu="+p.getOpenInventory().getTopInventory().getTitle());}return true;}
            if(args.length>0&&args[0].equals("show")){show(Integer.parseInt(args[1]));return true;}
            if(args.length>1&&args[0].equals("inside")){StructurePlanner.Site s=sites.get(Integer.parseInt(args[1]));for(StructurePlanner.Marker m:s.markers())if(StructureLoot.isLoot(m.kind)&&!m.kind.equals("vault")){for(Player p:Bukkit.getOnlinePlayers()){p.setGameMode(GameMode.CREATIVE);p.teleport(new Location(Bukkit.getWorld("world"),m.x+2.5,m.y,m.z+2.5,135,0));}return true;}return true;}
            if(args.length>0&&args[0].equals("fold")){for(Player p:Bukkit.getOnlinePlayers())((HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes")).liminal().enter(p);return true;}
            if(args.length>0&&args[0].equals("return")){for(Player p:Bukkit.getOnlinePlayers())((HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes")).liminal().leave(p);return true;}
            if(args.length>0&&args[0].equals("threshold")){threshold();return true;}
            if(args.length>1&&args[0].equals("weapon")){for(Player p:Bukkit.getOnlinePlayers()){p.getInventory().setItem(0,ExpeditionLoot.custom(args[1],1));p.getInventory().setHeldItemSlot(0);}return true;}
            if(args.length>1&&args[0].equals("arena")){StructurePlanner.Site s=sites.get(Integer.parseInt(args[1]));for(StructurePlanner.Marker m:s.markers())if(m.kind.equals("boss")){for(Player p:Bukkit.getOnlinePlayers()){p.setGameMode(GameMode.SURVIVAL);p.setHealth(20);p.teleport(new Location(Bukkit.getWorld("world"),m.x+.5,m.y,m.z+4.5,180,0));}return true;}return true;}
            if(busy)return true;busy=true;assertions+=WeaponLootProbe.run();check(StructureCatalog.ALL.size()>=100,"100 architecture definitions");
            for(int b=0;b<62;b++){boolean exclusive=false;for(StructureCatalog.Design d:StructureCatalog.choices(b))if(d.exclusive&&d.biomes.length==1&&d.biomes[0]==b)exclusive=true;check(exclusive,"Biome "+b+" owns an exclusive structure");}
            World w=Bukkit.getWorld("world");spawnGuard(w);
            for(int rx=-6;rx<=6;rx++)for(int rz=-6;rz<=6;rz++){StructurePlanner.Site s=StructurePlanner.region(w.getSeed(),rx,rz);if(s!=null&&sites.size()<12)sites.add(s);}
            Set<String> families=new HashSet<>();for(StructurePlanner.Site site:sites)families.add(site.design.family);
            for(int rx=-12;rx<=12;rx++)for(int rz=-12;rz<=12;rz++){StructurePlanner.Site s=StructurePlanner.expansionRegion(w.getSeed(),rx,rz);if(s!=null&&sites.size()<50&&(families.add(s.design.family)||sites.size()<40))sites.add(s);}
            check(sites.size()>=8,"Enough natural sites for integration");check(ExpeditionLoot.ready(),"Equipment plugin enabled");for(int i=0;i<sites.size();i++)getLogger().info("STRUCTURE_SITE index="+i+" name="+sites.get(i).design.name+" family="+sites.get(i).design.family+" tier="+sites.get(i).design.tier);
            Iterator<StructurePlanner.Site> iter=sites.iterator();
            new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{if(iter.hasNext()){testSite(w,iter.next());return;}persistence(w);getLogger().info("STRUCTURE_TEST_PASS assertions="+assertions+" naturalSites="+sites.size()+" designs="+StructureCatalog.ALL.size());busy=false;cancel();}catch(Throwable e){getLogger().severe("STRUCTURE_TEST_FAIL "+e);e.printStackTrace();busy=false;cancel();}}}.runTaskTimer(this,5,1);
        }catch(Throwable e){getLogger().severe("STRUCTURE_TEST_FAIL "+e);e.printStackTrace();busy=false;}
        return true;
    }
    private void testSite(World w,StructurePlanner.Site s){
        Terrain terrain=new Terrain(w.getSeed());check(s.design.accepts(terrain.sample(s.x+s.width/2,s.z+s.depth/2).profile.index),"Biome accepts "+s.design.id);
        int bosses=0,caches=0;Map<String,ChunkGenerator.ChunkData> chunks=new HashMap<>();
        for(StructurePlanner.Marker m:s.markers()){
            String key=(m.x>>4)+":"+(m.z>>4);ChunkGenerator.ChunkData d=chunks.get(key);if(d==null){d=w.getGenerator().generateChunkData(w,new Random(1),m.x>>4,m.z>>4,new Grid());chunks.put(key,d);}
            int x=m.x&15,z=m.z&15;
            if(StructureLoot.isLoot(m.kind)){caches++;check(d.getType(x,m.y,z)==Material.CHEST,"Chest marker stamped "+s.design.id+" "+m.ordinal);check(d.getTypeId(x,m.y+1,z)==0||d.getType(x,m.y+1,z).isTransparent(),"Chest lid clearance");for(int roll=0;roll<10;roll++){List<ItemStack> loot=ExpeditionLoot.roll(w.getSeed()+roll,s,m);check(!loot.isEmpty()&&loot.size()<=27,"Loot slot budget");for(ItemStack item:loot)check(item!=null&&item.getAmount()>0&&item.getAmount()<=item.getMaxStackSize(),"Valid loot stack "+item);}}
            if(m.kind.equals("boss"))bosses++;
            if(m.kind.equals("boss")||m.kind.equals("mob")){check(d.getTypeId(x,m.y,z)==0&&d.getTypeId(x,m.y+1,z)==0,"Encounter feet/head clearance "+s.design.id+" "+m.ordinal);check(d.getType(x,m.y-1,z).isSolid(),"Encounter floor");}
        }
        check(caches>0,"Every structure has actual loot");check(bosses<=1,"One bounded guardian per structure");
        // Materialize one marker chunk through actual Paper, not only the pure ChunkData contract.
        StructurePlanner.Marker actual=s.markers().get(0);w.getChunkAt(actual.x>>4,actual.z>>4).load();
        if(StructureLoot.isLoot(actual.kind))check(w.getBlockAt(actual.x,actual.y,actual.z).getType()==Material.CHEST,"Natural generated chunk contains usable cache "+s.design.id);
    }
    private Player fake(){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,args)->{if(m.getName().equals("isOnline"))return true;if(m.getName().equals("getName"))return "LootProbe";if(m.getName().equals("getUniqueId"))return UUID.nameUUIDFromBytes("LootProbe".getBytes("UTF-8"));if(m.getName().equals("getGameMode"))return GameMode.SURVIVAL;if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==double.class)return 0d;return null;});}
    private void spawnGuard(World world)throws Exception{
        HorrorPlugin plugin=(HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
        Method repair=HorrorPlugin.class.getDeclaredMethod("repairWorldSpawn",World.class);repair.setAccessible(true);
        check(world.setSpawnLocation(0,0,0),"Fixture installs stale Y=0 world metadata");
        Location repaired=(Location)repair.invoke(plugin,world);check(repaired!=null&&SpawnSafety.standable(repaired),"World metadata repairs to supported landing");
        check(world.getSpawnLocation().getBlockY()==repaired.getBlockY()&&repaired.getBlockY()>1,"Persistent world spawn height is repaired");
        Player player=fake();PlayerSpawnLocationEvent first=new PlayerSpawnLocationEvent(player,new Location(world,.5,0,.5));plugin.safeArrival(first);
        check(!SpawnSafety.voidRisk(first.getSpawnLocation())&&SpawnSafety.standable(first.getSpawnLocation()),"First epoch arrival never receives stale Y=0");
        Field field=HorrorPlugin.class.getDeclaredField("relocated");field.setAccessible(true);YamlConfiguration relocation=(YamlConfiguration)field.get(plugin);relocation.set(player.getUniqueId().toString(),true);
        PlayerSpawnLocationEvent repeat=new PlayerSpawnLocationEvent(player,new Location(world,.5,0,.5));plugin.safeArrival(repeat);
        check(!SpawnSafety.voidRisk(repeat.getSpawnLocation())&&SpawnSafety.standable(repeat.getSpawnLocation()),"Previously relocated player is still rescued from stale Y=0");
        getLogger().info("SPAWN_GUARD_TEST_PASS safe="+repaired.getBlockX()+","+repaired.getBlockY()+","+repaired.getBlockZ());
    }
    private void threshold(){
        check(!Bukkit.getOnlinePlayers().isEmpty(),"Browser player required for real threshold interaction");
        HorrorPlugin plugin=(HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");World w=Bukkit.getWorld("world");
        for(StructurePlanner.Site s:sites)for(StructurePlanner.Marker m:s.markers())if(m.kind.equals("door")){
            org.bukkit.block.Block block=w.getBlockAt(m.x,m.y,m.z);check(block.getType()==Material.PURPUR_BLOCK,"Actual threshold generated");
            for(Player p:Bukkit.getOnlinePlayers()){
                Location origin=new Location(w,m.x+.5,m.y+1,m.z+.5);check(p.teleport(origin),"Arrived at real threshold");
                ItemStack[] before=p.getInventory().getContents();GameMode mode=p.getGameMode();float exp=p.getExp();int level=p.getLevel();
                org.bukkit.event.player.PlayerInteractEvent click=new org.bukkit.event.player.PlayerInteractEvent(p,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,block,org.bukkit.block.BlockFace.UP,org.bukkit.inventory.EquipmentSlot.HAND);
                click.setCancelled(true);Bukkit.getPluginManager().callEvent(click);
                check(plugin.liminal().isInside(p),"Empty-hand predicted no-op still enters Fold through real Bukkit event dispatch");
                check(plugin.liminal().leave(p),"Fold exit succeeds");check(p.getLocation().distanceSquared(origin)<1,"Original entry location retained");
                check(Arrays.equals(before,p.getInventory().getContents())&&mode==p.getGameMode()&&exp==p.getExp()&&level==p.getLevel(),"Threshold round trip retains inventory, mode and experience");
            }
            getLogger().info("STRUCTURE_THRESHOLD_PASS realPlayer=true cancelledVanillaInteraction=true retainedPlayerState=true");return;
        }
        throw new AssertionError("Natural threshold not present in preview sites");
    }
    private void persistence(World w)throws Exception{
        HorrorPlugin plugin=(HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");Field field=HorrorPlugin.class.getDeclaredField("loot");field.setAccessible(true);StructureLoot loot=(StructureLoot)field.get(plugin);Method open=StructureLoot.class.getDeclaredMethod("open",Chest.class,Player.class);open.setAccessible(true);
        check(plugin.terrainEpoch().equals("surface-v4")&&plugin.lootNamespace().equals("v2:surface-v4"),"Regenerated terrain owns a fresh loot epoch");
        check(new java.io.File(plugin.getDataFolder(),"structure-encounters-surface-v4.bin").exists(),"Boss state belongs to the regenerated terrain epoch");
        Class<?> cacheType=Class.forName("chat.jaspr.biomes.StructureLoot$Cache",true,plugin.getClass().getClassLoader());
        Constructor<?> foldConstructor=cacheType.getDeclaredConstructor(World.class,int.class);foldConstructor.setAccessible(true);
        Object fold=foldConstructor.newInstance(w,3);Field cacheKey=cacheType.getDeclaredField("key");cacheKey.setAccessible(true);
        check(((String)cacheKey.get(fold)).startsWith("fold-v1:"),"Fold keeps original shared claim namespace");
        Field journalField=StructureLoot.class.getDeclaredField("journal");journalField.setAccessible(true);LootJournal journal=(LootJournal)journalField.get(loot);
        World foldWorld=Bukkit.getWorld(LiminalWorld.WORLD_NAME);check(foldWorld!=null,"Unchanged Fold world available");
        Location foldAt=LiminalGenerator.lootMarkers(foldWorld).get(0);Chest foldChest=(Chest)foldAt.getBlock().getState();
        String foldKey="fold-v1:"+foldWorld.getUID()+":"+LiminalGenerator.roomAt(foldAt);journal.claim(foldKey);
        check((boolean)open.invoke(loot,foldChest,fake()),"Previously claimed Fold cache remains usable");
        for(ItemStack item:foldChest.getBlockInventory().getContents())check(item==null||item.getType()==Material.AIR,"Old Fold claim cannot refill after Overworld regeneration");
        for(StructurePlanner.Site s:sites)for(StructurePlanner.Marker m:s.markers())if(StructureLoot.isLoot(m.kind)&&!m.kind.equals("vault")){
            w.getChunkAt(m.x>>4,m.z>>4).load();Chest chest=(Chest)w.getBlockAt(m.x,m.y,m.z).getState();
            String oldKey="v2:"+w.getUID()+":"+s.key+":"+m.ordinal,newKey=plugin.lootNamespace()+":"+w.getUID()+":"+s.key+":"+m.ordinal;
            journal.claim(oldKey);check(!journal.claimed(newKey),"Old-world claim does not consume regenerated loot");
            check(!(boolean)open.invoke(loot,chest,null),"Automation cannot claim unopened cache");check((boolean)open.invoke(loot,chest,fake()),"Authenticated discovery succeeds");check(Arrays.stream(chest.getBlockInventory().getContents()).anyMatch(i->i!=null&&i.getType()!=Material.AIR),"Cache received items");
            check(journal.claimed(oldKey)&&journal.claimed(newKey)&&journal.claimed(foldKey),"Old, new and Fold loot claims coexist");
            org.bukkit.block.Block light=w.getBlockAt(m.x-2,m.y+3,m.z-7);getLogger().info("STRUCTURE_LIGHT_CHECK source="+light.getType()+" emission="+light.getLightFromBlocks());if(light.getType()==Material.TORCH)check(light.getLightFromBlocks()==14,"Generated torch receives native emission");
            chest.getBlockInventory().clear();loot.stop();loot.start();
            check((boolean)open.invoke(loot,chest,fake()),"Claimed cache remains usable");for(ItemStack i:chest.getBlockInventory().getContents())check(i==null||i.getType()==Material.AIR,"Restart does not refill");w.save();return;
        }
        throw new AssertionError("No persistence cache fixture");
    }
    private void show(int index){if(sites.isEmpty())throw new IllegalStateException("Run smoke first");StructurePlanner.Site s=sites.get(Math.floorMod(index,sites.size()));World w=Bukkit.getWorld("world");int x=s.x+s.width/2,z=s.z+s.design.rows*12+30,y=Math.max(s.y,new Terrain(w.getSeed()).sample(x,z).y)+32;for(Player p:Bukkit.getOnlinePlayers()){p.teleport(new Location(w,x+.5,y,z+.5,180,30));p.setAllowFlight(true);p.setFlying(true);}w.setTime(6000);w.setStorm(false);getLogger().info("STRUCTURE_SHOW index="+index+" "+s.design.name+" tier="+s.design.tier+" pos="+s.x+","+s.y+","+s.z);}
    private static final class Grid implements ChunkGenerator.BiomeGrid{private final org.bukkit.block.Biome[][] a=new org.bukkit.block.Biome[16][16];public org.bukkit.block.Biome getBiome(int x,int z){return a[x][z];}public void setBiome(int x,int z,org.bukkit.block.Biome b){a[x][z]=b;}}
}
