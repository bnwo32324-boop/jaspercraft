package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.*;

/**
 * Three lazy pocket worlds per run and durable, player-owned paths back to their parents. Generation 6: every run
 * (Sessions) has its own rift worlds <run>_rift_<realm>, seeded from the run's seed, its own manifest and journals
 * (plugins/JasprDungeon/sessions/<run>/rifts), and they all go when the run closes. Realm of a world: 0 for a run's own
 * world, 1..3 for its rifts, -1 for anything else. Nothing survives a restart, so nothing is loaded at start.
 */
public final class Rifts implements Listener {
    private final DungeonPlugin plugin;
    private final Map<UUID,Visit> visits=new HashMap<>();
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Set<UUID> warned=new HashSet<>();
    private long ticks;
    private boolean closed;
    private static final class Visit {
        boolean armed;
        int dwell;
        String crack;
        String room;
        Location forward,back;
    }

    public Rifts(DungeonPlugin plugin){this.plugin=plugin;}
    private int sourceRealm(World world){return world==null||plugin.sessions==null?-1:plugin.sessions.realm(world);}
    /** Run and unrelated worlds use multiplier zero; travel checks use the stricter sourceRealm. */
    public int realm(World world){return Math.max(0,sourceRealm(world));}
    /** A rift pocket (slots 1..3); generation 7's Floors II and III (slots 4, 5) are not rifts. */
    public boolean contains(World world){return Floors.rift(sourceRealm(world));}
    public DungeonGenerator generator(World world){return contains(world)?plugin.sessions.generator(world):null;}
    /** Every loaded rift world of every live run. */
    public Collection<World> worlds(){List<World> out=new ArrayList<>();if(plugin.sessions!=null)for(World w:plugin.sessions.worlds())if(contains(w))out.add(w);return Collections.unmodifiableList(out);}
    public String displayName(World world){int n=sourceRealm(world);return Floors.rift(n)?RiftCatalog.get(n).title:n==0?"The Dungeon Dimension":n>0?"Floor "+Floors.numeral(Floors.floor(n))+": "+Floors.title(Floors.floor(n)):world==null?"Outside the dungeon":world.getName();}
    /** A run's rift identity and journals, checked against its root name, seed and generation; made on first use. */
    public RiftStore store(Sessions.Session run) throws Exception {
        if(run==null||!run.alive())throw new IllegalStateException("This dungeon run has ended");
        if(run.rifts==null)run.rifts=new RiftStore(new File(run.data,"rifts"),run.root,run.seed,DungeonPlugin.GENERATION_VERSION);
        return run.rifts;
    }
    private void validate(World world,Sessions.Session run,int realm){
        DungeonGenerator expected=run.generator(realm);
        if(!world.getName().equals(run.name(realm))||world.getSeed()!=expected.layout.seed
                ||!(world.getGenerator() instanceof DungeonGenerator))throw new IllegalStateException("Rift world seed/generator identity mismatch: "+world.getName());
        DungeonGenerator actual=(DungeonGenerator)world.getGenerator();
        if(actual.layout.seed!=expected.layout.seed||actual.layout.realm!=realm)throw new IllegalStateException("Rift layout identity mismatch: "+world.getName());
    }
    public World ensureWorld(Sessions.Session run,int realm){
        if(closed)throw new IllegalStateException("Rifts are closed");RiftCatalog.get(realm);
        if(run==null||!run.alive())throw new IllegalStateException("This dungeon run has ended");
        World w=run.world(realm);if(w!=null)return w;
        // Re-read the manifest before any world creation; never silently repair identity drift.
        try{RiftStore store=store(run);org.bukkit.configuration.file.YamlConfiguration y=new org.bukkit.configuration.file.YamlConfiguration();y.load(store.manifestFile());store.validateManifest(y);}
        catch(Exception ex){throw new IllegalStateException("Rift manifest is missing or changed",ex);}
        // The run registered this name before it existed, so WorldInit and inside() already know it.
        String name=run.name(realm);w=Bukkit.getWorld(name);
        if(w==null)w=new WorldCreator(name).environment(World.Environment.NORMAL).seed(run.generator(realm).layout.seed)
                .generateStructures(false).generator(run.generator(realm)).createWorld();
        if(w==null)throw new IllegalStateException("Rift world failed to load: "+name);
        validate(w,run,realm);run.worlds[realm]=w;
        w.setKeepSpawnInMemory(false);w.setDifficulty(Difficulty.HARD);w.setTime(18000);w.setStorm(false);w.setThundering(false);
        for(String rule:new String[]{"doDaylightCycle","doWeatherCycle","doMobSpawning","doFireTick","mobGriefing"})w.setGameRuleValue(rule,"false");
        // Deleted with its run like the run's own world: a death here must never leave items or a grave behind.
        w.setGameRuleValue("keepInventory","true");
        // Do not force a spawn or edit terrain. All travel obtains a freshly checked safeArrival.
        plugin.getLogger().info("DUNGEON_RIFT_WORLD_LOADED realm="+realm+" world="+name+" run="+run.id);return w;
    }
    private DungeonGenerator layoutGenerator(World world){return plugin.generator(world);}
    private boolean originRefuge(World world,Location at){
        return at.getX()>=0&&at.getX()<64&&at.getZ()>=0&&at.getZ()<64&&layoutGenerator(world).layout.at(at.getBlockX(),at.getBlockZ()).kind==Layout.Kind.REFUGE;
    }
    private static boolean near(double x,double z,double cx,double cz){return Math.abs(x-cx)<2&&Math.abs(z-cz)<2;}
    private boolean outsideCracks(World world,Location at,Layout.Room room){
        if(RiftCatalog.selected(realm(world),room)&&near(at.getX(),at.getZ(),RiftCatalog.forwardX(room),RiftCatalog.forwardZ(room)))return false;
        return !Floors.rift(sourceRealm(world))||!near(at.getX(),at.getZ(),RiftCatalog.RETURN_X,RiftCatalog.RETURN_Z);
    }
    public Location safeArrival(World world){
        if(sourceRealm(world)<0)return null;
        for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            Location at=new Location(world,RiftCatalog.ARRIVAL_X+dx,65,RiftCatalog.ARRIVAL_Z+dz,180,0);
            Layout.Room room=layoutGenerator(world).layout.at(at.getBlockX(),at.getBlockZ());
            if(originRefuge(world,at)&&outsideCracks(world,at,room)&&Sanctuary.safeFloor(at))return at;
        }
        return null;
    }
    private boolean clearCrack(Location at){
        return at!=null&&Sanctuary.safeFloor(at)&&at.clone().add(0,2,0).getBlock().getType()==Material.AIR;
    }
    /** Deterministic public discovery lookup. Never creates the destination world. */
    public Location forwardRift(World world,Layout.Room room){
        int n=sourceRealm(world);if(n<0||n>=RiftCatalog.COUNT||!plugin.getConfig().getBoolean("rifts-discovery-enabled",true))return null;
        Layout.Room actual=layoutGenerator(world).layout.at(room.x,room.z);
        if(actual.hash!=room.hash||!actual.id().equals(room.id())||!RiftCatalog.selected(n,actual))return null;
        if(!RiftCatalog.origin(actual)){
            Encounters.Run run=plugin.encounters.activate(world,actual);if(run==null||!run.state.cleared)return null;
        }
        Location at=new Location(world,RiftCatalog.forwardX(actual),65,RiftCatalog.forwardZ(actual));
        return clearCrack(at)?at:null;
    }
    public Location returnRift(World world){
        if(!contains(world))return null;
        Location at=new Location(world,RiftCatalog.RETURN_X,65,RiftCatalog.RETURN_Z);
        return clearCrack(at)?at:null;
    }
    private Location safeParent(Location origin,Layout.Room room){
        World world=origin.getWorld();
        for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            Location at=origin.clone().add(dx,0,dz);at.setX(at.getBlockX()+.5);at.setY(at.getBlockY());at.setZ(at.getBlockZ()+.5);
            if(room.inner(at.getX(),at.getZ())&&outsideCracks(world,at,room)&&Sanctuary.safeFloor(at))return at;
        }
        return null;
    }
    private boolean live(Player p){return p!=null&&p.isOnline()&&!p.isDead();}
    private boolean cooling(Player p,RiftStore.Journal journal){return System.currentTimeMillis()<Math.max(cooldowns.getOrDefault(p.getUniqueId(),0L),journal.cooldownUntil);}
    private void persistTravel(Player p,RiftStore store,RiftStore.Journal journal) throws Exception {
        journal.cooldownUntil=System.currentTimeMillis()+RiftCatalog.COOLDOWN_MILLIS;store.write(p.getUniqueId(),journal);
        cooldowns.put(p.getUniqueId(),journal.cooldownUntil);visits.remove(p.getUniqueId());
    }
    private boolean move(Player p,Location to){
        // A mounted or passenger entity must not be carried through a player rift.
        p.leaveVehicle();p.eject();boolean moved=plugin.move(p,to);
        if(moved){plugin.sanctuary.calm(p);try{p.saveData();}catch(RuntimeException ex){plugin.getLogger().warning("DUNGEON_RIFT_PLAYER_SAVE_RETRY "+p.getUniqueId());}}return moved;
    }
    /** Same guarded travel used after walk-through dwell; no command is needed or registered. */
    public boolean enter(Player p){
        if(closed||!live(p)||p.getGameMode()==GameMode.SPECTATOR)return false;int parent=sourceRealm(p.getWorld());if(parent<0||parent>=RiftCatalog.COUNT)return false;
        try{
            Sessions.Session run=plugin.sessions.of(p.getWorld());RiftStore store=store(run);
            RiftStore.Journal journal=store.read(p.getUniqueId());if(cooling(p,journal))return false;
            Layout.Room room=layoutGenerator(p.getWorld()).layout.at(p.getLocation().getBlockX(),p.getLocation().getBlockZ());
            Location crack=forwardRift(p.getWorld(),room);
            Location from=p.getLocation();if(crack==null||!RiftCatalog.hit(from.getX(),from.getY(),from.getZ(),crack.getX(),crack.getZ()))return false;
            Location safe=safeParent(from,room);if(safe==null){p.sendMessage(ChatColor.YELLOW+"The rift cannot remember a safe way back from this room.");return false;}
            World target=ensureWorld(run,parent+1);Location arrival=safeArrival(target);
            if(arrival==null){p.sendMessage(ChatColor.YELLOW+"The far refuge is obstructed. The rift will not open.");return false;}
            journal.parents.entrySet().removeIf(e->e.getKey()>parent);
            journal.parents.put(parent+1,new RiftStore.Parent(parent+1,p.getWorld().getName(),p.getWorld().getUID(),p.getWorld().getSeed(),room.x,room.z,
                    safe.getX(),safe.getY(),safe.getZ(),safe.getYaw(),safe.getPitch()));
            persistTravel(p,store,journal);
            if(!move(p,arrival))return false;
            p.sendMessage(ChatColor.DARK_PURPLE+RiftCatalog.get(parent+1).title+": "+RiftCatalog.get(parent+1).mood);
            p.sendMessage(ChatColor.GRAY+"The pale crack in this refuge returns to your parent room. /dungeon leave also returns.");return true;
        }catch(Exception ex){failure(p,ex);return false;}
    }
    /** True means this pocket-world return was handled, including a safely refused attempt. */
    public boolean leave(Player p){
        if(p==null||!contains(p.getWorld()))return false;
        if(closed||!live(p))return true;
        try{
            Sessions.Session run=plugin.sessions.of(p.getWorld());RiftStore store=store(run);
            RiftStore.Journal journal=store.read(p.getUniqueId());
            // Explicit /leave remains an emergency route; cooldown is only a walk-through guard.
            int current=realm(p.getWorld());RiftStore.Parent parent=journal.parents.get(current);
            // From the first rift the way back is the run's own world, never another run's.
            World target=current==1?run.world(0):ensureWorld(run,current-1);Location safe=null;
            if(target==null)throw new IllegalStateException("The run's own world is not loaded");
            if(parent!=null){
                if(!parent.uid.equals(target.getUID())||!parent.world.equals(target.getName())||parent.seed!=target.getSeed())
                    throw new IllegalStateException("Saved parent world has changed; preserve the return journal");
                Layout.Room room=layoutGenerator(target).layout.at(parent.roomX,parent.roomZ);
                if(room.x!=parent.roomX||room.z!=parent.roomZ||!room.contains(parent.x,parent.z))throw new IllegalStateException("Saved parent room has changed");
                safe=safeParent(new Location(target,parent.x,parent.y,parent.z,(float)parent.yaw,(float)parent.pitch),room);
            }
            if(safe==null)safe=safeArrival(target);
            if(safe==null){p.sendMessage(ChatColor.YELLOW+"Your parent room and refuge have no safe landing. The rift keeps you here until the way is cleared.");return true;}
            persistTravel(p,store,journal);
            if(move(p,safe))p.sendMessage(ChatColor.GRAY+"The rift returns you to "+displayName(target)+".");
        }catch(Exception ex){failure(p,ex);}return true;
    }
    private void failure(Player player,Exception ex){
        cooldowns.put(player.getUniqueId(),System.currentTimeMillis()+RiftCatalog.COOLDOWN_MILLIS);visits.remove(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW+"The rift cannot save or verify a safe return. Travel was refused; ask an administrator to inspect the rift journal.");
        plugin.getLogger().warning("DUNGEON_RIFT_TRAVEL_REFUSED player="+player.getUniqueId()+" reason="+ex.getMessage());
    }
    private void draw(Player p,Location crack,boolean back){
        if(crack==null||p.getLocation().distanceSquared(crack)>RiftCatalog.EFFECT_RADIUS*RiftCatalog.EFFECT_RADIUS)return;
        double phase=ticks*.09;
        for(int n=0;n<RiftCatalog.POINTS_PER_CRACK;n++){
            double height=.08+n*.2,twist=phase+height*2.5;
            double x=crack.getX()+Math.sin(twist)*.27,z=crack.getZ()+Math.cos(twist)*.19;
            p.spawnParticle(back?Particle.END_ROD:Particle.PORTAL,x,crack.getY()+height,z,1,0,0,0,0);
            if(n%4==0)p.spawnParticle(back?Particle.SPELL_INSTANT:Particle.SPELL_WITCH,x,crack.getY()+height,z,1,.025,.025,.025,0);
        }
    }
    public void tick(){
        if(closed)return;ticks++;
        for(Player p:Bukkit.getOnlinePlayers()){
            if(!live(p)||p.getGameMode()==GameMode.SPECTATOR||sourceRealm(p.getWorld())<0){visits.remove(p.getUniqueId());continue;}
            Visit v=visits.computeIfAbsent(p.getUniqueId(),id->new Visit());Location at=p.getLocation();
            Layout.Room room=layoutGenerator(p.getWorld()).layout.at(at.getBlockX(),at.getBlockZ());String key=p.getWorld().getName()+"/"+room.id();
            if(!key.equals(v.room)||ticks%20==0){
                v.room=key;v.forward=forwardRift(p.getWorld(),room);
                v.back=RiftCatalog.origin(room)?returnRift(p.getWorld()):null;
            }
            if(ticks%RiftCatalog.EFFECT_INTERVAL==0&&plugin.getConfig().getBoolean("rifts-particles",true)){draw(p,v.forward,false);draw(p,v.back,true);}
            Location hit=null;boolean back=false;
            if(v.forward!=null&&RiftCatalog.hit(at.getX(),at.getY(),at.getZ(),v.forward.getX(),v.forward.getZ()))hit=v.forward;
            if(v.back!=null&&RiftCatalog.hit(at.getX(),at.getY(),at.getZ(),v.back.getX(),v.back.getZ())){hit=v.back;back=true;}
            if(hit==null){v.armed=true;v.dwell=0;v.crack=null;continue;}
            if(warned.add(p.getUniqueId()))p.sendMessage(ChatColor.LIGHT_PURPLE+"A crack in space. Step clear, then stand inside for a moment to cross. /dungeon leave returns safely.");
            if(!v.armed||System.currentTimeMillis()<cooldowns.getOrDefault(p.getUniqueId(),0L)){v.dwell=0;continue;}
            String crack=key+(back?"/back":"/forward");if(!crack.equals(v.crack)){v.crack=crack;v.dwell=0;}
            if(++v.dwell<RiftCatalog.DWELL_TICKS)continue;
            v.armed=false;v.dwell=0;
            if(back){try{if(!cooling(p,store(plugin.sessions.of(p.getWorld())).read(p.getUniqueId())))leave(p);}catch(Exception ex){failure(p,ex);}}
            else enter(p);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleported(PlayerTeleportEvent e){visits.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void changed(PlayerChangedWorldEvent e){visits.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void joined(PlayerJoinEvent e){visits.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();visits.remove(id);cooldowns.remove(id);warned.remove(id);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mobPortal(EntityPortalEvent e){if(contains(e.getFrom().getWorld())||e.getTo()!=null&&contains(e.getTo().getWorld()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mobTeleport(EntityTeleportEvent e){
        if(e.getTo()!=null&&!e.getFrom().getWorld().equals(e.getTo().getWorld())&&(contains(e.getFrom().getWorld())||contains(e.getTo().getWorld())))e.setCancelled(true);
    }
    public void close(){closed=true;visits.clear();cooldowns.clear();warned.clear();}
}
