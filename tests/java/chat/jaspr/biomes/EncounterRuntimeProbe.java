package chat.jaspr.biomes;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Only run in the throwaway Paper server created by structure-encounters-runtime.cjs. */
public final class EncounterRuntimeProbe implements Listener {
    private JavaPlugin harness;
    public static void install(JavaPlugin harness) { EncounterRuntimeProbe test=new EncounterRuntimeProbe(); test.harness=harness; test.onEnable(); }
    private File getDataFolder() { return harness.getDataFolder(); }
    private java.util.logging.Logger getLogger() { return harness.getLogger(); }
    private ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    private HorrorPlugin host; private StructureEncounters runtime; private World world;
    private int assertions,spawnEvents,damageCalls,effects;
    private boolean cancelNext;
    private GameMode playerMode=GameMode.SURVIVAL;
    private Location playerAt;
    private Player player;
    private Properties saved=new Properties();
    private File stateFile;
    private static final String TAG="jaspr_encounter_v1:";

    public void onEnable() {
        if(!Boolean.getBoolean("jaspr.encounters.fixture"))throw new IllegalStateException("Isolated fixture only");
        Bukkit.getPluginManager().registerEvents(this,harness);
        Bukkit.getScheduler().runTaskLater(harness,()->{
            try {
                host=(HorrorPlugin)Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes"); runtime=host.encounters(); world=Bukkit.getWorld("world");
                check("details-v3".equals(host.terrainEpoch()),"new terrain epoch active across restarts");
                check(new File(host.getDataFolder(),"structure-encounters-details-v3.bin").isFile(),"fresh epoch encounter journal exists");
                getDataFolder().mkdirs(); stateFile=new File(getDataFolder(),"restart.properties");
                if(stateFile.exists())restart(); else first();
            } catch(Throwable ex) { failed(ex); }
        },40);
    }
    private void check(boolean value,String message) { assertions++; if(!value)throw new AssertionError(message); }
    private static Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static Object invoke(Object instance,String name,Class<?>[] types,Object... args) throws Exception {
        Method m=StructureEncounters.class.getDeclaredMethod(name,types); m.setAccessible(true);
        try { return m.invoke(instance,args); } catch(InvocationTargetException ex) { throw new RuntimeException(name,ex.getCause()); }
    }
    private EncounterJournal ledger() throws Exception { return (EncounterJournal)field(StructureEncounters.class,"journal").get(runtime); }
    @SuppressWarnings("unchecked") private Map<UUID,Object> actors() throws Exception { return (Map<UUID,Object>)field(StructureEncounters.class,"active").get(runtime); }
    private void spawn(EncounterJournal.Record record) throws Exception {
        ledger().put(record);
        invoke(runtime,"spawn",new Class<?>[]{EncounterJournal.Record.class,Location.class,boolean.class},record,new Location(world,record.x+.5,record.y,record.z+.5),false);
    }
    private void recover(EncounterJournal.Record record) throws Exception {
        invoke(runtime,"recover",new Class<?>[]{World.class,EncounterJournal.Record.class},world,record);
        field(StructureEncounters.class,"tick").setLong(runtime,field(StructureEncounters.class,"tick").getLong(runtime)+21);
        invoke(runtime,"recover",new Class<?>[]{World.class,EncounterJournal.Record.class},world,record);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void spawning(CreatureSpawnEvent event) throws Exception {
        for(String tag:event.getEntity().getScoreboardTags())if(tag.startsWith(TAG)) {
            UUID id=UUID.fromString(tag.substring(TAG.length())); EncounterJournal.Record r=ledger().get(id);
            check(r!=null && !r.terminal(),"claim exists before CreatureSpawnEvent");
            check(id.equals(event.getEntity().getUniqueId()),"stable native UUID exists before world insertion");
            spawnEvents++; if(cancelNext) { event.setCancelled(true); cancelNext=false; }
        }
    }
    private StructurePlanner.Site site(String key,int x,int z) throws Exception {
        StructureCatalog.Design design=null;
        for(StructureCatalog.Design d:StructureCatalog.ALL)if(d.tier>=3 && !d.mode.equals("underwater")) { design=d; break; }
        Constructor<StructurePlanner.Site> c=StructurePlanner.Site.class.getDeclaredConstructor(long.class,StructureCatalog.Design.class,int.class,int.class,String.class,int.class,int.class);
        c.setAccessible(true); return c.newInstance(world.getSeed(),design,x,z,key,x,z);
    }
    private void arena(int x,int y,int z) {
        // This terrain belongs solely to the isolated test server.
        for(int dx=-7;dx<=7;dx++)for(int dz=-7;dz<=7;dz++)for(int dy=-1;dy<=5;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(dy==-1?Material.STONE:Material.AIR,false);
    }
    private Player fakePlayer() {
        UUID id=UUID.randomUUID();
        return (Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "isOnline": return true;
                case "isDead": return false;
                case "getGameMode": return playerMode;
                case "getWorld": return world;
                case "getLocation": return playerAt.clone();
                case "getEyeLocation": return playerAt.clone().add(0,1.62,0);
                case "getUniqueId": return id;
                case "getName": return "EncounterProbeSurvivor";
                case "getScoreboardTags": return Collections.emptySet();
                case "isOnGround": return true;
                case "damage": damageCalls++; return null;
                case "addPotionEffect": effects++; return true;
                case "hashCode": return id.hashCode();
                case "equals": return proxy==args[0];
                case "toString": return "EncounterProbeSurvivor";
                default: return defaultValue(method.getReturnType());
            }
        });
    }
    private static Object defaultValue(Class<?> type) {
        if(type==boolean.class)return false; if(type==int.class)return 0; if(type==long.class)return 0L;
        if(type==double.class)return 0d; if(type==float.class)return 0f; return null;
    }
    @SuppressWarnings("unchecked") private void discover(StructurePlanner.Site site) throws Exception {
        Map<String,List<StructurePlanner.Site>> plans=(Map<String,List<StructurePlanner.Site>>)field(StructureEncounters.class,"plans").get(runtime);
        plans.clear(); int cx=playerAt.getBlockX()>>4,cz=playerAt.getBlockZ()>>4;
        for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++)plans.put(world.getUID()+":"+x+":"+z,Collections.singletonList(site));
        field(StructureEncounters.class,"discoveryCursor").setInt(runtime,0);
        for(int i=0;i<21;i++)invoke(runtime,"discover",new Class<?>[]{List.class},Collections.singletonList(player));
    }
    private EncounterJournal.Record record(String name,boolean boss,int x,int y,int z) {
        return new EncounterJournal.Record(world.getUID(),name,0,-1,null,boss,boss?"GRAVE_ENGINE":"ASH_SHAMBLER",3,x,y,z);
    }
    private void first() throws Exception {
        check(runtime.metrics().contains("encounters=ready"),"runtime started");
        check(StructureEncounters.Theme.values().length>=12 && StructureEncounters.Boss.values().length>=8,"12 mobs and 8 bosses");
        for(int tier=1;tier<=5;tier++) {
            EncounterJournal.Record r=new EncounterJournal.Record(world.getUID(),"tier",tier,-1,null,true,"GRAVE_ENGINE",tier,0,64,0);
            check(StructureEncounters.health(r)>100 && StructureEncounters.damage(r)>=5,"tier stats");
        }
        check(Arrays.asList(StructureEncounters.themePool(false,"hospital","ash","Hospice","surface")).contains("PLAGUE_NURSE"),"hospital theme");
        check(Arrays.asList(StructureEncounters.themePool(true,"castle","ash","Keep","surface")).contains("OSSUARY_REGENT"),"castle boss");
        check(Arrays.asList(StructureEncounters.themePool(false,"harbor","rain","Sunken","underwater")).contains("DROWNED_MINER"),"underwater theme");
        check(Arrays.asList(StructureEncounters.themePool(true,"metro","rust","Rails","buried")).contains("GRAVE_ENGINE"),"metro boss");
        check(Arrays.asList(StructureEncounters.themePool(false,"castle","snow","Frost","surface")).contains("FROST_WATCHER"),"snow climate overrides family");
        check(!Arrays.asList(StructureEncounters.themePool(false,"kiln","ash","Cinderlake","surface")).contains("FROST_WATCHER"),"no frost in lava family");
        StructurePlanner.Site site=site("encounter-arena-test",512,512); StructurePlanner.Marker boss=null,mob=null;
        for(StructurePlanner.Marker marker:site.markers()) { if(marker.kind.equals("boss"))boss=marker; if(marker.kind.equals("mob") && mob==null)mob=marker; }
        check(boss!=null && mob!=null,"real planner markers available");
        arena(boss.x,boss.y,boss.z); arena(mob.x,mob.y,mob.z);
        playerAt=new Location(world,boss.x+8.5,boss.y,boss.z+.5); player=fakePlayer();
        UUID bossId=EncounterJournal.identity(world.getUID(),site.key,boss.ordinal,-1);
        discover(site); check(ledger().get(bossId)==null,"footprint proximity cannot claim boss");
        playerAt=new Location(world,boss.x+2.5,boss.y,boss.z+.5); playerMode=GameMode.CREATIVE;
        discover(site); check(ledger().get(bossId)==null,"creative cannot trigger boss");
        playerMode=GameMode.SURVIVAL; discover(site);
        check(ledger().get(bossId)!=null && Bukkit.getEntity(bossId)!=null,"enter arena claims and spawns boss");
        LivingEntity bossEntity=(LivingEntity)Bukkit.getEntity(bossId);
        check(!bossEntity.isGlowing() && !bossEntity.getRemoveWhenFarAway() && !bossEntity.hasAI(),"persistent non-glowing controlled native mob");
        check(ChatColor.stripColor(bossEntity.getCustomName()).length()<40,"concise boss label");
        check(!runtime.defeated(world,site),"living boss locks vault");
        int before=spawnEvents; discover(site); check(spawnEvents==before,"re-entry cannot duplicate encounter");
        UUID mobId=EncounterJournal.identity(world.getUID(),site.key,mob.ordinal,-1);
        // Check marker distance directly as other room markers can legitimately spawn during discovery.
        playerAt=new Location(world,mob.x+37.5,mob.y,mob.z+.5);
        check(!(Boolean)invoke(runtime,"near",new Class<?>[]{Location.class,List.class,double.class},new Location(world,mob.x+.5,mob.y,mob.z+.5),Collections.singletonList(player),36d),"regular range excludes 37 blocks");
        playerAt=new Location(world,mob.x+35.5,mob.y,mob.z+.5);
        check((Boolean)invoke(runtime,"near",new Class<?>[]{Location.class,List.class,double.class},new Location(world,mob.x+.5,mob.y,mob.z+.5),Collections.singletonList(player),36d),"regular range includes 35 blocks");
        playerAt=bossEntity.getLocation().clone().add(2,0,0);
        EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(bossEntity,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,8);
        System.setProperty("jaspr.biomes.fixture","false"); runtime.damageGuard(hit); check(hit.isCancelled(),"unauthenticated damage blocked");
        System.setProperty("jaspr.biomes.fixture","true"); playerMode=GameMode.CREATIVE;
        hit=new EntityDamageByEntityEvent(bossEntity,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,8); runtime.damageGuard(hit); check(hit.isCancelled(),"creative damage blocked");
        playerMode=GameMode.SURVIVAL;
        hit=new EntityDamageByEntityEvent(bossEntity,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,8); runtime.damageGuard(hit); check(!hit.isCancelled(),"authenticated survival damage allowed");
        hit.setCancelled(true); runtime.confirmedHit(hit); check(effects==0,"cancelled attacks have no immediate secondary effects");
        EncounterJournal.Record canceled=record("cancelled-spawn",true,boss.x+3,boss.y,boss.z+3);
        cancelNext=true; spawn(canceled);
        check(ledger().get(canceled.id).state==EncounterJournal.State.CLAIMED && Bukkit.getEntity(canceled.id)==null,"cancelled spawn retains pre-spawn claim");
        check((Boolean)invoke(runtime,"recoveryLoaded",new Class<?>[]{World.class,EncounterJournal.Record.class},world,canceled),"recovery needs only known resident chunks at view-distance 3");
        World unloaded=(World)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->method.getName().equals("isChunkLoaded")?false:defaultValue(method.getReturnType()));
        check(!(Boolean)invoke(runtime,"recoveryLoaded",new Class<?>[]{World.class,EncounterJournal.Record.class},unloaded,canceled),"unloaded resident cannot prove absence");
        recover(canceled); check(Bukkit.getEntity(canceled.id)!=null,"cancelled boss recovers without 9x9 loaded neighborhood");
        int capIndex=0;
        while((Integer)invoke(runtime,"count",new Class<?>[]{boolean.class},true)<8)spawn(record("cap-"+(capIndex++),true,boss.x,boss.y,boss.z));
        StructurePlanner.Site capped=site("encounter-cap-unclaimed",512,512); playerAt=new Location(world,boss.x+2.5,boss.y,boss.z+.5);
        discover(capped); check(ledger().get(EncounterJournal.identity(world.getUID(),capped.key,boss.ordinal,-1))==null,"full eight-boss cap never consumes new claims");
        phaseReaction(boss.x,boss.y,boss.z);
        // Actual native damage causes an EntityDeathEvent without a killer, then durable vault unlock.
        bossEntity.damage(10000);
        check(ledger().get(bossId).state==EncounterJournal.State.KILLED && runtime.defeated(world,site),"environmental native death durably unlocks vault");
        saved.setProperty("killed",bossId.toString()); saved.setProperty("alive",canceled.id.toString());
        saved.setProperty("cx",Integer.toString(canceled.x>>4)); saved.setProperty("cz",Integer.toString(canceled.z>>4));
        saved.setProperty("name",((LivingEntity)Bukkit.getEntity(canceled.id)).getCustomName());
        prepareMissingRestart(boss);
        world.save();
        try(OutputStream out=new FileOutputStream(stateFile)) { saved.store(out,"isolated encounter restart test"); }
        getLogger().info("ENCOUNTER_RUNTIME_PHASE1_PASS assertions="+assertions+" "+runtime.snapshot());
        Bukkit.getScheduler().runTaskLater(harness,Bukkit::shutdown,10);
    }
    private void prepareMissingRestart(StructurePlanner.Marker boss) throws Exception {
        EncounterJournal.Record missing=new EncounterJournal.Record(world.getUID(),"encounter-missing-restart",boss.ordinal,-1,null,
            true,"GRAVE_ENGINE",3,boss.x,boss.y,boss.z);
        spawn(missing);
        LivingEntity entity=(LivingEntity)Bukkit.getEntity(missing.id);
        check(entity!=null,"missing-restart fixture starts as a real active boss");
        arena(boss.x+16,boss.y,boss.z);
        Object actor=actors().get(missing.id); Location away=new Location(world,boss.x+16.5,boss.y,boss.z+.5);
        // Mirror controlled movement's write-ahead checkpoint before the native teleport.
        check((Boolean)invoke(runtime,"checkpoint",new Class<?>[]{actor.getClass(),Location.class},actor,away),"destination is checkpointed before movement");
        check(entity.teleport(away),"boss crosses a resident chunk boundary");
        invoke(runtime,"returnHome",new Class<?>[]{actor.getClass()},actor);
        check(entity.getLocation().distanceSquared(new Location(world,boss.x+.5,boss.y,boss.z+.5))<1,"boss returns to its home chunk");
        check(ledger().get(missing.id).residency.size()==2,"movement durably records both resident chunks");
        // Simulate a saved entity going missing without a death event; keep its ACTIVE journal record.
        entity.remove(); actors().remove(missing.id);
        ledger().put(ledger().get(missing.id).progress(2,4));
        saved.setProperty("missing",missing.id.toString());
        EncounterJournal.Record claimed=record("claimed-on-restart",true,boss.x,boss.y,boss.z);
        cancelNext=true; spawn(claimed);
        check(ledger().get(claimed.id).state==EncounterJournal.State.CLAIMED,"cancelled boss claim is saved for restart");
        saved.setProperty("claimed",claimed.id.toString());
    }
    private void phaseReaction(int x,int y,int z) throws Exception {
        EncounterJournal.Record r=record("phase-reaction",true,x,y,z); ledger().put(r);
        Location at=new Location(world,x+.5,y,z+.5); playerAt=at.clone().add(2,0,0);
        LivingEntity testEntity=(LivingEntity)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{LivingEntity.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "getLocation": return at.clone(); case "getEyeLocation": return at.clone().add(0,1.6,0);
                case "getWorld": return world; case "getHealth": return 20d;
                case "getUniqueId": return r.id; default: return defaultValue(method.getReturnType());
            }
        });
        Class<?> actorClass=Class.forName("chat.jaspr.biomes.StructureEncounters$Actor");
        Constructor<?> constructor=actorClass.getDeclaredConstructor(LivingEntity.class,EncounterJournal.Record.class,long.class); constructor.setAccessible(true);
        Object actor=constructor.newInstance(testEntity,r,0L);
        field(StructureEncounters.class,"tick").setLong(runtime,200);
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player));
        check(ledger().get(r.id).phase==2,"enraged phase persisted");
        field(StructureEncounters.class,"tick").setLong(runtime,240);
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player));
        check(field(actorClass,"attack").get(actor)!=null && damageCalls==0,"boss windup causes no immediate damage");
        long deadline=field(actorClass,"resolve").getLong(actor);
        field(StructureEncounters.class,"tick").setLong(runtime,deadline-1);
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player)); check(damageCalls==0,"windup lasts through its deadline");
        field(StructureEncounters.class,"tick").setLong(runtime,deadline);
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player)); check(damageCalls==1,"telegraphed quake damages grounded survivor after windup");
        int before=damageCalls; field(actorClass,"ready").setLong(actor,0);
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player));
        playerAt=at.clone().add(-3,0,0); // Leave the aimed lunge line during its windup.
        field(StructureEncounters.class,"tick").setLong(runtime,field(actorClass,"resolve").getLong(actor));
        invoke(runtime,"advance",new Class<?>[]{actorClass,List.class},actor,Collections.singletonList(player));
        check(damageCalls==before,"sidestepping the aimed attack avoids damage");
    }
    private void restart() throws Exception {
        try(InputStream in=new FileInputStream(stateFile)) { saved.load(in); }
        UUID alive=UUID.fromString(saved.getProperty("alive")),killed=UUID.fromString(saved.getProperty("killed"));
        check(ledger().get(killed).state==EncounterJournal.State.KILLED,"process restart retains killed state");
        world.getChunkAt(Integer.parseInt(saved.getProperty("cx")),Integer.parseInt(saved.getProperty("cz")));
        Bukkit.getScheduler().runTaskLater(harness,()->{
            try {
                Entity entity=Bukkit.getEntity(alive);
                check(entity instanceof LivingEntity,"named native entity retained after full process restart");
                check(entity.getCustomName().equals(saved.getProperty("name")),"name survives restart");
                check(entity.getScoreboardTags().contains(TAG+alive),"record tag survives restart");
                invoke(runtime,"adopt",new Class<?>[]{LivingEntity.class},entity);
                check(actors().containsKey(alive),"ChunkLoad entity reclaimed under stable UUID");
                int count=spawnEvents; recover(ledger().get(alive)); check(spawnEvents==count,"loaded persisted entity is never respawned");
                check(Bukkit.getEntity(killed)==null,"defeated boss stays absent after restart");
                missingRestart();
                getLogger().info("ENCOUNTER_RUNTIME_PHASE2_PASS assertions="+assertions+" "+runtime.metrics()); Bukkit.shutdown();
            } catch(Throwable ex) { failed(ex); }
        },30);
    }
    private void missingRestart() throws Exception {
        EncounterJournal.Record missing=ledger().get(UUID.fromString(saved.getProperty("missing")));
        StructurePlanner.Site site=site(missing.site,512,512);
        check(Bukkit.getViewDistance()==3,"restart fixture uses low view distance");
        check(missing.state==EncounterJournal.State.ACTIVE && missing.phase==2 && missing.summons==4,
            "restart retains missing boss ACTIVE state, phase and spent summons");
        check(missing.residency.size()==2,"restart retains the full resident history");
        world.getChunkAt(missing.x>>4,missing.z>>4);
        int residentX=(missing.x>>4)+1,residentZ=missing.z>>4;
        if(world.isChunkLoaded(residentX,residentZ))check(world.unloadChunk(residentX,residentZ,true,false),"fixture unloads former resident chunk");
        check(!world.isChunkLoaded(residentX,residentZ),"former resident chunk starts unloaded");
        check(Bukkit.getEntity(missing.id)==null && !runtime.defeated(world,site),"absent active boss keeps vault locked after restart");
        check(runtime.state(world,site).contains("WAITING_FOR_LOADED_RECOVERY_AREA"),"status explains unloaded resident deferral");
        int count=spawnEvents,loaded=world.getLoadedChunks().length;
        recover(missing);
        check(spawnEvents==count && Bukkit.getEntity(missing.id)==null,"unloaded resident never proves a missing boss absent");
        check(!world.isChunkLoaded(residentX,residentZ) && world.getLoadedChunks().length==loaded,"recovery never loads its missing resident chunk");
        world.getChunkAt(residentX,residentZ);
        check(!world.isChunkLoaded((missing.x>>4)+4,(missing.z>>4)+4),"restart recovery proceeds with the old 9x9 neighborhood incomplete");
        check((Boolean)invoke(runtime,"recoveryLoaded",new Class<?>[]{World.class,EncounterJournal.Record.class},world,missing),"loaded recorded residents suffice after restart");
        loaded=world.getLoadedChunks().length;
        invoke(runtime,"recover",new Class<?>[]{World.class,EncounterJournal.Record.class},world,missing);
        check(spawnEvents==count,"first eligible absence check cannot replace boss");
        field(StructureEncounters.class,"tick").setLong(runtime,field(StructureEncounters.class,"tick").getLong(runtime)+19);
        invoke(runtime,"recover",new Class<?>[]{World.class,EncounterJournal.Record.class},world,missing);
        check(spawnEvents==count,"missing boss waits the full twenty-tick confirmation interval");
        field(StructureEncounters.class,"tick").setLong(runtime,field(StructureEncounters.class,"tick").getLong(runtime)+1);
        invoke(runtime,"recover",new Class<?>[]{World.class,EncounterJournal.Record.class},world,missing);
        Entity replacement=Bukkit.getEntity(missing.id);
        check(spawnEvents==count+1 && replacement instanceof LivingEntity,"missing ACTIVE boss recovers once after process restart");
        check(world.getLoadedChunks().length==loaded,"successful recovery does not load additional chunks");
        EncounterJournal.Record recovered=ledger().get(missing.id);
        check(recovered.state==EncounterJournal.State.ACTIVE && recovered.phase==2 && recovered.summons==4
            && recovered.residency.equals(missing.residency),"replacement preserves identity, progress and resident history");
        check(!runtime.defeated(world,site),"replacement leaves vault locked until death");
        recover(recovered); check(spawnEvents==count+1,"repeated recovery cannot duplicate the replacement");
        ((LivingEntity)replacement).damage(10000);
        check(runtime.defeated(world,site),"killing the replacement unlocks the previously missing boss vault");
        EncounterJournal.Record claimed=ledger().get(UUID.fromString(saved.getProperty("claimed")));
        check(claimed.state==EncounterJournal.State.CLAIMED && Bukkit.getEntity(claimed.id)==null,"unspawned claim survives process restart");
        recover(claimed);
        check(Bukkit.getEntity(claimed.id) instanceof LivingEntity && ledger().get(claimed.id).state==EncounterJournal.State.ACTIVE,
            "missing CLAIMED boss also recovers after restart at low view distance");
    }
    private void failed(Throwable ex) { ex.printStackTrace(); getLogger().severe("ENCOUNTER_RUNTIME_FAIL "+ex); Bukkit.shutdown(); }
}
