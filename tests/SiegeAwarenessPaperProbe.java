package chat.jaspr.apocalypse;

import java.lang.reflect.Field;
import java.util.*;
import com.mojang.authlib.GameProfile;
import fr.xephi.authme.api.v3.AuthMeApi;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Real Paper entities/collision/ticks; only the authentication backend and connected socket are fixtures. */
public final class SiegeAwarenessPaperProbe implements Listener {
    private JavaPlugin harness;
    private ApocalypsePlugin host;
    private SiegeDirector director;
    private World world;
    private SiegeTraversal traversal;
    private SiegeAwareness awareness;
    private CraftPlayer player;
    private final List<Case> cases=new ArrayList<>();
    private final Set<UUID> cancelled=new HashSet<>();
    private int checks,changes,tick,chunkLoads;
    private boolean armed,finished,cancelTarget;
    private Zombie distant;
    private Case capCase;
    private SiegeTraversal cappedTraversal;
    private double distantStart;
    private static final class Case {
        final String name; final Zombie z; final Location goal; final SiegeTraversal.State state=new SiegeTraversal.State();
        double peak=64; float controlledFall;
        Case(String name,Zombie z,Location goal){this.name=name;this.z=z;this.goal=goal;}
    }
    public static void install(JavaPlugin harness) { SiegeAwarenessPaperProbe p=new SiegeAwarenessPaperProbe();p.harness=harness;p.start(); }
    private void start() {
        if(!Boolean.getBoolean("jaspr.siege.fixture"))throw new IllegalStateException("Fixture only");
        Bukkit.getPluginManager().registerEvents(this,harness);
        Bukkit.getScheduler().runTaskLater(harness,()->{try{setup();}catch(Throwable e){finish(e);}},30);
    }
    private void check(boolean result,String message) { checks++; if(!result)throw new AssertionError(message); }
    private static Field field(Class<?> type,String name)throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static Object get(Object target,String name)throws Exception { return field(target.getClass(),name).get(target); }
    @EventHandler public void loaded(ChunkLoadEvent e) { if(armed)chunkLoads++; }
    @EventHandler public void unload(ChunkUnloadEvent e) { if(armed)e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGH) public void target(EntityTargetLivingEntityEvent e) { if(cancelTarget)e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGH) public void change(EntityChangeBlockEvent e) {
        if(e.getTo()!=Material.COBBLESTONE)return;
        changes++;
        AxisAlignedBB b=((CraftZombie)e.getEntity()).getHandle().getBoundingBox();
        org.bukkit.block.Block at=e.getBlock();
        check(!b.c(new AxisAlignedBB(at.getX(),at.getY(),at.getZ(),at.getX()+1,at.getY()+1,at.getZ()+1)),"placement event must occur after physical body clearance");
        if(cancelled.contains(e.getEntity().getUniqueId()))e.setCancelled(true);
    }
    private CraftPlayer player(Location at)throws Exception {
        MinecraftServer server=((CraftServer)Bukkit.getServer()).getServer();
        WorldServer w=((CraftWorld)world).getHandle();
        EntityPlayer handle=new EntityPlayer(server,w,new GameProfile(UUID.randomUUID(),"SiegeFixture"),new PlayerInteractManager(w));
        handle.playerConnection=new PlayerConnection(server,new NetworkManager(EnumProtocolDirection.SERVERBOUND),handle) {
            @Override public void sendPacket(Packet<?> packet) { }
        };
        CraftPlayer p=new CraftPlayer((CraftServer)Bukkit.getServer(),handle) {
            @Override public boolean isOnline() { return true; }
        };
        field(net.minecraft.server.v1_12_R1.Entity.class,"bukkitEntity").set(handle,p);
        handle.setPosition(at.getX(),at.getY(),at.getZ());p.setGameMode(GameMode.SURVIVAL);
        return p;
    }
    private void at(Player p,double x,double y,double z) { ((CraftPlayer)p).getHandle().setPosition(x,y,z); }
    private static float angleDelta(float a,float b) { float d=(a-b)%360.0f;if(d>180)d-=360;if(d<-180)d+=360;return Math.abs(d); }
    private void checkFacing(Zombie zombie,Location goal,String phase) {
        EntityZombie h=((CraftZombie)zombie).getHandle();
        double dx=goal.getX()-h.locX,dz=goal.getZ()-h.locZ;
        float expected=(float)Math.toDegrees(Math.atan2(-dx,dz));
        check(angleDelta(h.yaw,expected)<5,phase+" entity yaw follows target: actual="+h.yaw+" expected="+expected);
        check(angleDelta(h.aN,expected)<5,phase+" body yaw follows target: actual="+h.aN+" expected="+expected);
        check(angleDelta(h.getHeadRotation(),expected)<5,phase+" head yaw follows target: actual="+h.getHeadRotation()+" expected="+expected);
    }
    private Zombie spawn(double x,double z,String kind) { Zombie result=director.spawn(new Location(world,x,64,z),kind);check(result!=null,"fixture spawn "+kind);return result; }
    private void block(int x,int y,int z,Material material) { world.getBlockAt(x,y,z).setType(material,false); }
    private void roof(int x,int z,int width) { for(int dx=0;dx<width;dx++)for(int dz=-2;dz<=2;dz++)block(x+dx,68,z+dz,Material.STONE); }
    private void setup()throws Exception {
        host=(ApocalypsePlugin)Bukkit.getPluginManager().getPlugin("JasprApocalypse");
        check(host!=null&&host.isEnabled(),"real production plugin started");
        director=(SiegeDirector)get(host,"siege");((BukkitTask)get(director,"task")).cancel();
        awareness=(SiegeAwareness)get(director,"awareness");traversal=(SiegeTraversal)get(director,"traversal");
        world=Bukkit.getWorld("world");world.setTime(6000);world.setGameRuleValue("doDaylightCycle","false");
        world.setGameRuleValue("doMobSpawning","false");world.setGameRuleValue("mobGriefing","true");
        for(int x=-1;x<=8;x++)for(int z=-1;z<=4;z++)world.getChunkAt(x,z).load();
        for(int x=3;x<=125;x++)for(int z=3;z<=60;z++)block(x,63,z,Material.STONE);
        player=player(new Location(world,100.5,64,50.5));
        awarenessTests();
        ownershipTests();
        roof(10,8,4); cases.add(new Case("pillar",spawn(8.5,8.5,"walker"),new Location(world,11.5,69,8.5)));
        roof(10,24,4);for(int y=64;y<69;y++)for(int z=22;z<27;z++)block(9,y,z,Material.STONE);
        cases.add(new Case("wall",spawn(8.5,24.5,"climber"),new Location(world,11.5,69,24.5)));
        roof(26,8,4);for(int x=23;x<=25;x++)for(int z=7;z<=9;z++)block(x,66,z,Material.BEDROCK);
        cases.add(new Case("ceiling",spawn(24.5,8.5,"walker"),new Location(world,27.5,69,8.5)));
        roof(26,24,4);for(int y=64;y<69;y++)for(int z=22;z<27;z++)block(25,y,z,Material.BEDROCK);
        for(int x=23;x<=25;x++)for(int z=22;z<=26;z++)block(x,67,z,Material.BEDROCK);
        cases.add(new Case("overhang",spawn(24.5,24.5,"climber"),new Location(world,27.5,69,24.5)));
        roof(42,8,4);Case cancel=new Case("cancel",spawn(40.5,8.5,"walker"),new Location(world,43.5,69,8.5));cases.add(cancel);cancelled.add(cancel.z.getUniqueId());
        roof(42,24,4);cases.add(new Case("grief",spawn(40.5,24.5,"walker"),new Location(world,43.5,69,24.5)));
        roof(58,8,4);cases.add(new Case("expiry",spawn(56.5,8.5,"walker"),new Location(world,59.5,69,8.5)));
        for(int y=64;y<112;y++)for(int z=22;z<27;z++)block(73,y,z,Material.BEDROCK);
        for(int x=74;x<78;x++)for(int z=22;z<27;z++)block(x,111,z,Material.STONE);
        cases.add(new Case("tall-wall",spawn(72.5,24.5,"climber"),new Location(world,75.5,112,24.5)));
        YamlConfiguration capConfig=new YamlConfiguration();capConfig.set("siege.max-pillar-blocks-per-zombie",2);
        cappedTraversal=new SiegeTraversal(capConfig);roof(90,8,4);
        capCase=new Case("pillar-cap",spawn(88.5,8.5,"walker"),new Location(world,91.5,69,8.5));cases.add(capCase);
        distant=spawn(40.5,50.5,"walker");distantStart=distant.getLocation().getX();
        check(((CraftWorld)world).getHandle().spigotConfig.monsterActivationRange==32,"fixture uses live 32-block activation range");
        armed=true;
        Bukkit.getScheduler().runTaskTimer(harness,()->{if(!finished)try{step();}catch(Throwable e){finish(e);}},1,1);
    }
    private void awarenessTests()throws Exception {
        Zombie z=spawn(8.5,50.5,"walker");
        SiegeAwareness.Memory m=new SiegeAwareness.Memory();List<Player> eligible=Collections.singletonList(player);
        awareness.begin(10);awareness.update(z,m,eligible);
        check(m.visible==player,"daylight sight acquires at 92 blocks");
        at(player,106.5,64,50.5);awareness.begin(20);m.clear();awareness.update(z,m,eligible);check(m.goal==null,"outside 96 blocks rejected");
        at(player,100.5,64,50.5);player.setSneaking(true);awareness.begin(30);awareness.update(z,m,eligible);check(m.goal==null,"sneaking reduces visible range");player.setSneaking(false);
        for(GameMode mode:new GameMode[]{GameMode.CREATIVE,GameMode.SPECTATOR}) {player.setGameMode(mode);awareness.begin(40);awareness.update(z,m,eligible);check(m.goal==null,"mode excluded "+mode);}
        player.setGameMode(GameMode.SURVIVAL);AuthMeApi.authenticated=false;awareness.begin(50);awareness.update(z,m,eligible);check(m.goal==null,"unauthenticated cannot be seen");
        int count=awareness.signalCount();director.noise(player,player.getLocation(),48);check(awareness.signalCount()==count,"unauthenticated cannot leave signals");AuthMeApi.authenticated=true;
        @SuppressWarnings("unchecked") Map<UUID,Long> grace=(Map<UUID,Long>)get(host,"grace");
        grace.put(player.getUniqueId(),System.currentTimeMillis()+100000);awareness.begin(60);awareness.update(z,m,eligible);check(m.goal==null,"newcomer grace excludes detection");grace.clear();
        player.setGameMode(GameMode.ADVENTURE);awareness.begin(70);awareness.update(z,m,eligible);check(m.visible==player,"authenticated adventure is huntable");player.setGameMode(GameMode.SURVIVAL);
        at(player,30.5,64,50.5);for(int y=64;y<68;y++)block(20,y,50,Material.BEDROCK);
        awareness.begin(80);m.clear();awareness.update(z,m,eligible);check(m.goal==null,"solid wall blocks sight");
        host.noise(player,player.getLocation(),40);awareness.update(z,m,eligible);check(m.goal!=null&&m.visible==null,"lead noise wrapper creates occluded investigation only");
        Location remembered=m.goal.clone();at(player,35.5,64,50.5);check(m.goal.equals(remembered),"sound snapshots do not follow hidden player");
        check(!awareness.valid(m,z,eligible,1000),"investigation expires");at(player,100.5,64,50.5);check(!awareness.valid(m,z,eligible,90),"owner leaving signal leash ends pursuit");
        at(player,30.5,64,50.5);awareness.clear();m.clear();awareness.begin(90);
        Object arsenal=get(host,"arsenal");Class<?> gun=Class.forName("chat.jaspr.apocalypse.Arsenal$Gun");
        java.lang.reflect.Method fire=Arsenal.class.getDeclaredMethod("fire",Player.class,Location.class,org.bukkit.util.Vector.class,gun,List.class);fire.setAccessible(true);
        Object whisper=null,rifle=null;for(Object item:gun.getEnumConstants()){if(item.toString().equals("WHISPER"))whisper=item;if(item.toString().equals("RIFLE"))rifle=item;}
        fire.invoke(arsenal,player,player.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),whisper,Collections.emptyList());
        awareness.update(z,m,eligible);check(m.goal==null,"native suppressed fire does not alert zombie 22 blocks away");
        fire.invoke(arsenal,player,player.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),rifle,Collections.emptyList());
        awareness.update(z,m,eligible);check(m.goal!=null&&m.visible==null,"native loud fire alerts through wall, even after same-tick suppressed fire");
        awareness.clear();m.clear();awareness.begin(100);
        EntityDamageEvent wound=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.CUSTOM,2);
        Bukkit.getPluginManager().callEvent(wound);awareness.update(z,m,eligible);check(m.goal!=null&&m.visible==null,"damage event leaves wound scent");
        m.clear();awareness.begin(220);awareness.update(z,m,eligible);check(m.goal==null,"decaying scent loses its outer detection radius");
        for(int y=64;y<68;y++)block(20,y,50,Material.AIR);
        world.setTime(18000);at(player,78.5,64,50.5);awareness.clear();m.clear();awareness.begin(230);awareness.update(z,m,eligible);check(m.goal==null,"unlit night reduces sight to 48 blocks");
        block(78,63,50,Material.GLOWSTONE);awareness.begin(240);awareness.update(z,m,eligible);check(m.visible==player,"visible block light reveals otherwise dark survivor");block(78,63,50,Material.STONE);world.setTime(6000);
        awareness.clear();at(player,30.5,64,50.5);
        for(int i=0;i<400;i++){awareness.begin(100+i);director.noise(player,new Location(world,25+(i%10),64,48+(i%3)),48);}
        check(awareness.signalCount()<=128,"stimulus list capped");awareness.begin(10000);check(awareness.signalCount()==0,"stimuli expire without targets");
        int before=world.getLoadedChunks().length;
        Location remote=new Location(world,100000,64,100000);check(director.spawn(remote,"walker")==null,"unloaded spawn refused");director.noise(player,remote,48);
        check(world.getLoadedChunks().length==before,"unloaded signal/spawn cannot request chunk");
        check(SiegeRules.breakPasses(Material.BEDROCK)==0&&SiegeRules.breakPasses(Material.CHEST)==0,"protected blocks excluded");
        for(int y=64;y<68;y++)block(20,y,50,Material.AIR);
        z.remove();at(player,100.5,64,50.5);awareness.clear();
        harness.getLogger().info("SIEGE_PAPER_AWARENESS checks="+checks);
    }
    @SuppressWarnings("unchecked") private Map<UUID,Object> hunters()throws Exception { return (Map<UUID,Object>)get(director,"hunters"); }
    private void ownershipTests()throws Exception {
        for(org.bukkit.entity.EntityType type:new org.bukkit.entity.EntityType[]{org.bukkit.entity.EntityType.HUSK,org.bukkit.entity.EntityType.ZOMBIE_VILLAGER}) {
            Zombie z=(Zombie)world.spawnEntity(new Location(world,120.5,64,8.5),type);
            Object goals=((CraftZombie)z).getHandle().goalSelector;
            check(!hunters().containsKey(z.getUniqueId()),"custom/farm zombie untouched "+type);
            director.onSpawn(new CreatureSpawnEvent(z,CreatureSpawnEvent.SpawnReason.SPAWNER));
            check(((CraftZombie)z).getHandle().goalSelector==goals,"spawner AI kept "+type);
            z.removeScoreboardTag("jaspr_vanilla_undead");
            director.onSpawn(new CreatureSpawnEvent(z,CreatureSpawnEvent.SpawnReason.NATURAL));
            check(hunters().containsKey(z.getUniqueId()),"natural subtype managed "+type);hunters().remove(z.getUniqueId());z.remove();
        }
        Zombie custom=(Zombie)world.spawnEntity(new Location(world,120.5,64,8.5),org.bukkit.entity.EntityType.ZOMBIE);
        custom.addScoreboardTag("jaspr_encounter_v1:fixture");custom.setCustomName("Encounter owner");Object goals=((CraftZombie)custom).getHandle().goalSelector;
        director.loaded(new ChunkLoadEvent(custom.getLocation().getChunk(),false));
        check(!hunters().containsKey(custom.getUniqueId())&&((CraftZombie)custom).getHandle().goalSelector==goals,"encounter AI untouched on chunk load");custom.remove();
        Zombie pig=(Zombie)world.spawnEntity(new Location(world,120.5,64,8.5),org.bukkit.entity.EntityType.PIG_ZOMBIE);
        director.onSpawn(new CreatureSpawnEvent(pig,CreatureSpawnEvent.SpawnReason.NATURAL));check(!hunters().containsKey(pig.getUniqueId()),"pig zombies excluded");pig.remove();
    }
    private void step()throws Exception {
        tick++;
        for(Case c:cases) {
            ((CraftZombie)c.z).getHandle().activatedTick=MinecraftServer.currentTick+2;
            if(c.name.equals("grief"))world.setGameRuleValue("mobGriefing","false");
            if(!c.name.equals("expiry")||tick<35)(c==capCase?cappedTraversal:traversal).move(c.z,c.goal,c.state,tick,new SiegeTraversal.Budget(6,1));
            else {c.state.cancel();SiegeTraversal.halt(c.z);}
            if(c.name.equals("grief"))world.setGameRuleValue("mobGriefing","true");
            c.peak=Math.max(c.peak,c.z.getLocation().getY());
            if(c.name.equals("pillar")&&(c.state.step!=null||c.state.column!=null||c.state.transferUntil>tick))
                c.controlledFall=Math.max(c.controlledFall,c.z.getFallDistance());
        }
        // Use the production director for the distant hunter; other fixture actors have no matching memory.
        // Temporarily remove traversal cases from its registry so this independent locomotion test owns their inputs.
        @SuppressWarnings("unchecked") Map<UUID,Object> hunters=(Map<UUID,Object>)get(director,"hunters");
        Map<UUID,Object> held=new LinkedHashMap<>();for(Case c:cases){Object h=hunters.remove(c.z.getUniqueId());if(h!=null)held.put(c.z.getUniqueId(),h);}
        director.tick(Collections.singletonList(player));hunters.putAll(held);
        if(tick==80) {
            check(distant.getLocation().getX()>distantStart+4,"sighted hunter physically moves beyond 32 blocks: "+distant.getLocation());
            check(distant.getLocation().distanceSquared(player.getLocation())>32*32,"movement assertion still outside activation range");
            AuthMeApi.authenticated=false;director.tick(Collections.singletonList(player));
            Object h=hunters.get(distant.getUniqueId());check(((SiegeAwareness.Memory)get(h,"memory")).goal==null,"losing authentication clears active pursuit immediately");
            check(distant.getVelocity().getX()==0,"ineligible target stops steering");AuthMeApi.authenticated=true;
            harness.getLogger().info("SIEGE_PAPER_DISTANT movement="+(distant.getLocation().getX()-distantStart));
            at(player,distant.getLocation().getX()-20,64,50.5);
        }
        if(tick==90) { checkFacing(distant,player.getEyeLocation(),"westbound reacquisition");at(player,100.5,64,50.5); }
        if(tick==110) checkFacing(distant,player.getEyeLocation(),"eastbound reacquisition");
        if(tick%80==0)for(Case c:cases)harness.getLogger().info("SIEGE_PAPER_PROGRESS "+c.name+" at="+c.z.getLocation().toVector()+" peak="+c.peak+" placed="+c.state.placed);
        if(tick>=360) {
            for(Case c:cases) {
                if(c.name.equals("pillar")) {check(c.state.placed>=4,"regular zombie physically pillars multiple levels");check(c.z.getLocation().getY()>=68.9&&c.z.getLocation().getX()>=10,"pillar reaches supported roof");check(c.controlledFall<.1f,"controlled pillar traversal clears artificial fall distance: "+c.controlledFall);}
                if(c.name.equals("wall")) {check(c.peak>=69,"wall climber rises to roof");check(c.z.getLocation().getY()>=68.9&&c.z.getLocation().getX()>=10,"wall climber traverses roof lip");check(c.state.placed==0,"wall climb needs no pillar");}
                if(c.name.equals("tall-wall")) {check(c.peak>=112,"48-block building physically climbed");check(c.z.getLocation().getY()>=111.9&&c.z.getLocation().getX()>=74,"48-block roof reached");check(c.state.placed==0,"48-block climb uses no pillars");}
                if(c.name.equals("ceiling"))check(c.peak<64.5&&c.state.placed==0,"ceiling rejects upward jump/placement");
                if(c.name.equals("overhang"))check(c.peak<66&&c.state.placed==0,"wall climber cannot pass overhang");
                if(c.name.equals("cancel"))check(c.state.placed==0&&world.getBlockAt(40,64,8).getType()==Material.AIR,"cancelled placements leave blocks unchanged");
                if(c.name.equals("grief"))check(c.state.placed==0,"mobGriefing false prevents pillars");
                if(c.name.equals("expiry"))check(c.state.placed<=2,"lost goal stops stair building");
                if(c.name.equals("pillar-cap"))check(c.state.placed==2&&c.z.getLocation().getY()<67,"physical pillar allowance stops at configured cap");
            }
            check(changes>4,"real cancellable placement events observed");check(chunkLoads==0,"no runtime chunk loading: "+chunkLoads);
            finalChecks();
            finish(null);
        }
    }
    private void finalChecks()throws Exception {
        armed=false;
        for(Case c:cases)if(c.name.equals("pillar")||c.name.equals("wall")) {
            NBTTagCompound saved=((CraftZombie)c.z).getHandle().save(new NBTTagCompound());
            EntityZombie restored=new EntityZombie(((CraftWorld)world).getHandle());restored.f(saved);
            Zombie copy=(Zombie)restored.getBukkitEntity();
            if(c.name.equals("wall"))check(copy.getScoreboardTags().contains(SiegeTraversal.CLIMBER_TAG)&&!copy.isGlowing(),"native NBT persists non-glowing climber capability");
            else {SiegeTraversal.State state=new SiegeTraversal.State();SiegeTraversal.restoreCount(copy,state);check(state.placed==5,"native NBT persists per-zombie pillar allowance");}
        }
        Object h=hunters().get(distant.getUniqueId());check(h!=null,"distant actor still managed");
        EntityZombie distantHandle=((CraftZombie)distant).getHandle();
        long visibilityBefore=((Number)get(director,"visibilityRepairs")).longValue();
        distant.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,200,0));
        distantHandle.setInvisible(true);
        for(int i=0;i<11&&(distantHandle.isInvisible()||distant.hasPotionEffect(PotionEffectType.INVISIBILITY));i++)
            director.tick(Collections.singletonList(player));
        check(!distantHandle.isInvisible()&&!distant.hasPotionEffect(PotionEffectType.INVISIBILITY),"managed zombie invisibility is repaired authoritatively");
        check(((Number)get(director,"visibilityRepairs")).longValue()>visibilityBefore,"visibility repair is counted for diagnostics");
        distant.setTarget(null);cancelTarget=true;director.tick(Collections.singletonList(player));cancelTarget=false;
        check(((SiegeAwareness.Memory)get(h,"memory")).goal==null,"target cancellation ends custom pursuit");
        for(int i=0;i<11;i++)director.tick(Collections.emptyList());
        check(((SiegeAwareness.Memory)get(h,"memory")).goal==null&&distant.getVelocity().lengthSquared()<.1,"no active targets produces no custom pursuit");
        SiegeTraversal.State session=new SiegeTraversal.State();session.sessionUntil=1;session.placed=2;session.endPursuit();
        check(session.sessionUntil==0&&session.placed==2,"new pursuit renews traversal session but preserves permanent pillar allowance");
        java.lang.reflect.Method vacant=SiegeTraversal.class.getDeclaredMethod("vacant",Zombie.class,org.bukkit.block.Block.class);vacant.setAccessible(true);
        org.bukkit.entity.LivingEntity other=(org.bukkit.entity.LivingEntity)world.spawnEntity(new Location(world,110.5,64,8.5),org.bukkit.entity.EntityType.COW);
        check(!(Boolean)vacant.invoke(null,distant,world.getBlockAt(110,64,8)),"live entity prevents block insertion into body");other.remove();
        at(player,110.5,64,8.5);((CraftWorld)world).getHandle().addEntity(player.getHandle());
        check(!(Boolean)vacant.invoke(null,distant,world.getBlockAt(110,64,8)),"player bounding box prevents block insertion");((CraftWorld)world).getHandle().removeEntity(player.getHandle());
        List<Zombie> extras=new ArrayList<>();while(director.activeCount()<80){Zombie z=director.spawn(new Location(world,120.5,64,8.5),"walker");check(z!=null,"fill bounded actor registry");extras.add(z);}
        check(director.spawn(new Location(world,120.5,64,8.5),"walker")==null&&director.activeCount()==80,"hard actor cap rejects next spawn");
        for(Zombie z:extras){hunters().remove(z.getUniqueId());z.remove();}
        org.bukkit.Chunk chunk=cases.get(1).z.getLocation().getChunk();director.unloaded(new ChunkUnloadEvent(chunk));director.loaded(new ChunkLoadEvent(chunk,false));
        check(hunters().containsKey(cases.get(1).z.getUniqueId())&&cases.get(1).z.getScoreboardTags().contains(SiegeTraversal.CLIMBER_TAG),"chunk reattachment restores climber without decoration reroll");
        director.stop();check(director.activeCount()==0&&awareness.signalCount()==0,"stop clears actor/signal state");
        check(HandlerList.getRegisteredListeners(host).stream().noneMatch(r->r.getListener()==director||r.getListener()==awareness),"stop unregisters own listeners");
    }
    private void finish(Throwable error) {
        if(finished)return;finished=true;
        if(error==null)harness.getLogger().info("SIEGE_PAPER_OK checks="+checks+" placements="+changes+" chunksLoaded="+chunkLoads);
        else {harness.getLogger().severe("SIEGE_PAPER_FAILED "+error);error.printStackTrace();}
        Bukkit.shutdown();
    }
}
