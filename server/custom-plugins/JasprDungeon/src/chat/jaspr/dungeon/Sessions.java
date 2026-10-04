package chat.jaspr.dungeon;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.bukkit.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/**
 * Generation 6 (owner 2026-10-04): "every time I go in, it should be like a new run ... I never want to run into stale
 * rooms that have already been completed", and "every new session should not contain any of the stale rooms that the
 * previous session had".
 *
 * A session is one run: its own world <base>_s<n> with a fresh random seed (so completely different rooms) and its own
 * rift worlds, made lazily. Friends who step through the same gate within the join window share it. Leaving through
 * the gate or /dungeon leave, dying, or being moved out by other means ends a player's part for good: they never rejoin
 * it. A member who disconnects inside stays one and comes back into the run while it lives. A run with nobody inside
 * closes after the grace period (at once when nobody can come back): its rooms sleep, its worlds unload, and their
 * folders and journals are deleted off the main thread. No run survives a restart; returning players are sent home.
 */
public final class Sessions implements Listener {
    /** JasprDaylight exempts this prefix; every run world keeps it. */
    public static final String PREFIX="jaspr_dungeon";
    static final long MAX_ID=1000000000L;
    private static final int RETRIES=6;private static final long DELETE_DELAY_TICKS=200;
    private static final SecureRandom SEEDS=new SecureRandom();
    private final DungeonPlugin plugin;private final File counter,journals;
    private final Map<Long,Session> live=new LinkedHashMap<>();private final Map<String,Session> byWorld=new HashMap<>();
    private final Set<UUID> fallen=new HashSet<>();private final Map<UUID,String> notices=new HashMap<>();private final Map<UUID,Integer> lastTheme=new HashMap<>();
    private long ticks;private volatile boolean stopping;

    /** One run: its worlds (its own and up to three rifts), seed, members, and the players who left it for good. */
    public static final class Session {
        public final long id,seed,started;public final String root,gate;public final int theme;public final File data;
        private final String[] names=new String[RiftCatalog.COUNT+1];
        final DungeonGenerator[] generators=new DungeonGenerator[RiftCatalog.COUNT+1];final World[] worlds=new World[RiftCatalog.COUNT+1];
        final Set<UUID> members=new LinkedHashSet<>(),left=new LinkedHashSet<>();
        RiftStore rifts;long emptySince=-1,ms;int unloaded,deferred;boolean closed;
        Session(long id,String base,long seed,String gate,long started,File data){
            this.id=id;root=root(base,id);this.seed=seed;this.gate=gate;this.started=started;this.data=data;
            for(int realm=0;realm<=RiftCatalog.COUNT;realm++){names[realm]=RiftCatalog.worldName(root,realm);
                generators[realm]=realm==0?new DungeonGenerator(seed):new DungeonGenerator(RiftCatalog.seed(seed,realm),realm);}
            theme=generators[0].layout.at(16,16).theme;
        }
        public String name(int realm){return names[realm];}
        /** 0 for the run's own world, 1..3 for its rifts, -1 for any other name. */
        public int realm(String world){for(int n=0;n<names.length;n++)if(names[n].equals(world))return n;return -1;}
        public boolean owns(World w){return w!=null&&realm(w.getName())>=0;}
        public DungeonGenerator generator(int realm){return generators[realm];}
        public World world(int realm){return worlds[realm];}
        public List<World> loaded(){List<World> out=new ArrayList<>(worlds.length);for(World w:worlds)if(w!=null)out.add(w);return out;}
        public boolean alive(){return !closed;}
        public boolean active(UUID player){return members.contains(player)&&!left.contains(player);}
        public int activeCount(){int n=0;for(UUID id:members)if(!left.contains(id))n++;return n;}
        public Set<UUID> members(){return Collections.unmodifiableSet(members);}
        public Set<UUID> left(){return Collections.unmodifiableSet(left);}
        /** Friends share a run: the same gate, inside the join window, still alive, and never left by this player. */
        public boolean joinable(String gate,UUID player,long now,long window){return !closed&&gate!=null&&gate.equals(this.gate)&&now-started<window&&!left.contains(player);}
        /**
         * Checked once a second. Anyone inside keeps a run open. Empty, it closes after the grace period, which lets a member
         * who disconnected inside come back to it; at once when nobody can come back (every member left) and the join window is over.
         */
        boolean expired(boolean present,long now,long grace,long window){
            if(present){emptySince=-1;return false;}
            if(emptySince<0)emptySince=now;
            return now-emptySince>=grace||activeCount()==0&&now-started>=window;
        }
    }

    public Sessions(DungeonPlugin plugin){
        this.plugin=plugin;counter=new File(plugin.getDataFolder(),"sessions.yml");journals=new File(plugin.getDataFolder(),"sessions");
        try{peek(counter);}catch(IOException ex){throw new IllegalStateException("Cannot read the dungeon run counter; preserve sessions.yml and repair it",ex);}
        // No run survives a restart: before anything loads, every leftover run world of this base and every run journal goes.
        List<String> failed=new ArrayList<>();int deleted=cleanup(Bukkit.getWorldContainer(),plugin.worldName,name->Bukkit.getWorld(name)!=null,failed);
        boolean clear=delete(journals);
        plugin.getLogger().info("DUNGEON_SESSION_CLEANUP deleted="+deleted+" journals="+(clear?"clear":"kept")+(failed.isEmpty()?"":" failed="+String.join(",",failed)));
    }
    // ---------------------------------------------------------------- pure rules (SessionAuditTest uses them without a server)
    public static String root(String base,long id){return base+"_s"+id;}
    /** Folder names of this base's runs, <base>_s<n> and <base>_s<n>_rift_<realm>; never the base itself or anything else. */
    public static Pattern pattern(String base){
        StringBuilder realms=new StringBuilder();for(RiftCatalog.Realm r:RiftCatalog.Realm.values()){if(realms.length()>0)realms.append('|');realms.append(r.suffix);}
        return Pattern.compile("^"+Pattern.quote(base)+"_s\\d+(_rift_("+realms+"))?$");
    }
    public static boolean leftover(String base,String name){return base!=null&&name!=null&&base.startsWith(PREFIX)&&pattern(base).matcher(name).matches();}
    /** Startup cleanup: deletes every leftover run folder of this base that is not a loaded world; names any it could not delete. */
    static int cleanup(File container,String base,Predicate<String> loaded,List<String> failed){
        File[] all=container==null||base==null||!base.startsWith(PREFIX)?null:container.listFiles();if(all==null)return 0;
        Arrays.sort(all);Pattern runs=pattern(base);int deleted=0;
        for(File f:all)if(f.isDirectory()&&runs.matcher(f.getName()).matches()&&!loaded.test(f.getName())){if(delete(f))deleted++;else failed.add(f.getName());}
        return deleted;
    }
    /** Deletes a folder tree without following links; true when it is gone. A locked file is left for the next attempt. */
    static boolean delete(File root){
        if(root==null||!root.exists())return true;
        try{Files.walkFileTree(root.toPath(),new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult visitFile(Path f,BasicFileAttributes a){try{Files.deleteIfExists(f);}catch(IOException locked){}return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path f,IOException e){return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path d,IOException e){try{Files.deleteIfExists(d);}catch(IOException notEmpty){}return FileVisitResult.CONTINUE;}
        });}catch(IOException ex){return false;}
        return !root.exists();
    }
    /** The next run number. It is written before its world exists, so a number is never used twice, even across restarts. */
    static long reserve(File file) throws IOException {
        long next=peek(file);YamlConfiguration y=new YamlConfiguration();y.set("next",next+1);RoomStore.atomic(file,y.saveToString());return next;
    }
    static long peek(File file) throws IOException {
        if(!file.exists())return 1;YamlConfiguration y=new YamlConfiguration();
        try{y.load(file);}catch(InvalidConfigurationException ex){throw new IOException("Malformed run counter",ex);}
        long next=RiftStore.integer(y.get("next"));if(next<1||next>=MAX_ID)throw new IOException("Run counter out of range: "+next);return next;
    }
    /** A fresh random seed whose arrival refuge does not repeat the theme of the run this player just had (-1: no such run). */
    static long seed(int avoid){long s=SEEDS.nextLong();for(int n=0;n<32&&refugeTheme(s)==avoid;n++)s=SEEDS.nextLong();return s;}
    static int refugeTheme(long seed){return new Layout(seed).at(16,16).theme;}
    /** The newest run a player may join from this gate, or null for a new run. */
    static Session pick(Collection<Session> runs,String gate,UUID player,long now,long window){
        Session best=null;for(Session s:runs)if(s.joinable(gate,player,now,window)&&(best==null||s.id>best.id))best=s;return best;
    }
    static String key(Gates.Gate g){return g==null?null:g.world+":"+g.x+","+g.y+","+g.z+(g.axisX?"x":"z");}
    private static String where(Gates.Gate g){return g==null?"none":g.x+","+g.y+","+g.z;}

    // ---------------------------------------------------------------- lookups
    /** The live run that owns this world (its own world or one of its rifts); null for any other world. */
    public Session of(World w){return w==null?null:byWorld.get(w.getName());}
    public int realm(World w){Session s=of(w);return s==null?-1:s.realm(w.getName());}
    public DungeonGenerator generator(World w){Session s=of(w);if(s==null)return null;int n=s.realm(w.getName());return n<0?null:s.generator(n);}
    public List<World> worlds(){List<World> out=new ArrayList<>();for(Session s:live.values())out.addAll(s.loaded());return out;}
    public int live(){return live.size();}
    public Collection<Session> runs(){return Collections.unmodifiableCollection(new ArrayList<>(live.values()));}
    private long window(){return Math.max(0,plugin.getConfig().getInt("session-join-seconds",30))*1000L;}
    private long grace(){return Math.max(5,plugin.getConfig().getInt("session-grace-seconds",300))*1000L;}
    private int max(){return Math.max(1,plugin.getConfig().getInt("max-sessions",16));}

    // ---------------------------------------------------------------- entering
    /** A player stepped through an overworld gate: join a friend's run opened from it moments ago, or begin a new one. */
    public void enter(Player p,Gates.Gate g){
        UUID id=p.getUniqueId();String gate=key(g);Session s=pick(live.values(),gate,id,System.currentTimeMillis(),window());boolean fresh=s==null;
        if(fresh&&live.size()>=max()){refuse(p);return;}
        try{
            if(fresh)s=create(gate,lastTheme.getOrDefault(id,-1));
            Location arrival=plugin.sanctuary.arrival(s.world(0));
            if(arrival==null){p.sendMessage("The refuge arrival area is obstructed. The gate will not send you into danger.");if(fresh)close(s,"failed");return;}
            join(p,s);
            if(!plugin.move(p,arrival)){s.members.remove(id);plugin.gates.unmark(id,s.root);if(fresh)close(s,"failed");p.sendMessage("The gate cannot open safely right now.");return;}
            if(fresh)started(s,p,g);else plugin.getLogger().info("DUNGEON_SESSION_JOINED id="+s.id+" player="+p.getName()+" players="+s.activeCount());
            p.sendMessage(ChatColor.DARK_PURPLE+(fresh?"Run #"+s.id+" begins: a Dungeon Dimension no one has walked before.":"You join run #"+s.id+", opened from this gate moments ago."));
            p.sendMessage(ChatColor.GRAY+"Leaving, dying or /dungeon leave ends your run for good. Entering again always starts a new one.");
        }catch(Exception ex){
            p.sendMessage("The gate cannot open safely right now.");plugin.getLogger().severe("DUNGEON_PORTAL_FAILED "+ex.getMessage());
            if(fresh&&s!=null&&!s.closed)close(s,"failed");
        }
    }
    /** /dungeon visit and test fixtures: the run this player is in, or a new one begun for them (no gate, so nobody joins it). */
    public World runFor(Player p){
        Session s=of(p.getWorld());if(s!=null)return s.world(0);
        if(live.size()>=max()){refuse(p);return null;}
        try{s=create(null,lastTheme.getOrDefault(p.getUniqueId(),-1));join(p,s);started(s,p,null);return s.world(0);}
        catch(Exception ex){
            p.sendMessage("A new run cannot open safely right now.");plugin.getLogger().severe("DUNGEON_SESSION_FAILED "+ex.getMessage());
            if(s!=null&&!s.closed)close(s,"failed");return null;
        }
    }
    private void refuse(Player p){
        p.sendMessage(ChatColor.YELLOW+"Every dungeon run is taken right now ("+live.size()+" open). The gate opens again as soon as one ends.");
        plugin.getLogger().info("DUNGEON_SESSION_REFUSED reason=full player="+p.getName()+" live="+live.size());
    }
    private void started(Session s,Player p,Gates.Gate g){plugin.getLogger().info("DUNGEON_SESSION_STARTED id="+s.id+" world="+s.root+" players=1 player="+p.getName()+" gate="+where(g)+" ms="+s.ms);}
    /** Membership: any other run this player is still part of ends, and the way home is saved with this run's marker. */
    private void join(Player p,Session s) throws IOException {
        UUID id=p.getUniqueId();for(Session other:new ArrayList<>(live.values()))if(other!=s&&other.active(id))left(p,other,"elsewhere");
        plugin.gates.remember(p,s.root);s.members.add(id);lastTheme.put(id,s.theme);
    }
    /** A new run: its number reserved and its names registered before its world exists, so WorldInit and inside() see them. */
    private Session create(String gate,int avoid) throws IOException {
        long t0=System.nanoTime();File container=Bukkit.getWorldContainer();long id=reserve(counter);
        // A fresh number never names an existing folder; should one exist anyway, the number is skipped, never adopted.
        for(int tries=1;taken(container,id);tries++){if(tries>=64)throw new IOException("No free run name");id=reserve(counter);}
        Session s=new Session(id,plugin.worldName,seed(avoid),gate,System.currentTimeMillis(),new File(journals,root(plugin.worldName,id)));
        live.put(s.id,s);for(int realm=0;realm<=RiftCatalog.COUNT;realm++)byWorld.put(s.name(realm),s);
        try{
            World w=new WorldCreator(s.root).environment(World.Environment.NORMAL).seed(s.seed).generateStructures(false).generator(s.generator(0)).createWorld();
            if(w==null||w.getSeed()!=s.seed||!(w.getGenerator() instanceof DungeonGenerator)||((DungeonGenerator)w.getGenerator()).layout.seed!=s.seed)throw new IllegalStateException("Run world failed to load: "+s.root);
            s.worlds[0]=w;w.setKeepSpawnInMemory(false);w.setSpawnLocation(16,65,16);w.setDifficulty(Difficulty.NORMAL);w.setTime(18000);w.setStorm(false);
            for(String rule:new String[]{"doDaylightCycle","doWeatherCycle","doMobSpawning","doFireTick","mobGriefing"})w.setGameRuleValue(rule,"false");
            // A run world is deleted when the run ends: a death there must never leave items or a grave behind.
            w.setGameRuleValue("keepInventory","true");plugin.gates.installReturnGate(w);
            s.ms=(System.nanoTime()-t0)/1000000L;plugin.getLogger().info("DUNGEON_WORLD_LOADED "+s.root);return s;
        }catch(RuntimeException ex){close(s,"failed");throw ex;}
    }
    private boolean taken(File container,long id){
        String root=root(plugin.worldName,id);
        for(int realm=0;realm<=RiftCatalog.COUNT;realm++){String name=RiftCatalog.worldName(root,realm);if(new File(container,name).exists()||Bukkit.getWorld(name)!=null)return true;}
        return new File(journals,root).exists();
    }

    // ---------------------------------------------------------------- leaving
    /** A player's part in a run is over (gate, command, death or moved elsewhere): they never rejoin it and its marker goes. */
    public void left(Player p,Session s,String reason){
        if(p==null||s==null||!s.left.add(p.getUniqueId()))return;plugin.gates.unmark(p.getUniqueId(),s.root);
        plugin.getLogger().info("DUNGEON_SESSION_LEFT id="+s.id+" player="+p.getName()+" reason="+reason+" players="+s.activeCount());
    }
    /** Sends a player to their way home (or the main spawn); their part in the run is over. */
    private boolean home(Player p,Session s,String reason){
        Location to=plugin.gates.returnLocation(p);if(to==null)to=Bukkit.getWorlds().get(0).getSpawnLocation();
        if(!plugin.move(p,to))return false;
        if(s!=null&&s.left.add(p.getUniqueId()))plugin.gates.unmark(p.getUniqueId(),s.root);
        plugin.getLogger().info("DUNGEON_SESSION_SENT_HOME id="+(s==null?"-":String.valueOf(s.id))+" player="+p.getName()+" reason="+reason);
        p.sendMessage(ChatColor.GRAY+("left".equals(reason)?"You left that run, and it does not take you back. The gate always opens a new one."
            :"That dungeon run has ended. The Last Candle brought you home; the gate opens a new run."));
        return true;
    }
    /** Someone arrived in a run world by other means (an administrator's teleport): they are part of the run from now on. */
    private void adopt(Player p,Session s){
        UUID id=p.getUniqueId();for(Session other:new ArrayList<>(live.values()))if(other!=s&&other.active(id))left(p,other,"elsewhere");
        s.members.add(id);plugin.gates.mark(id,s.root);lastTheme.put(id,s.theme);
        plugin.getLogger().info("DUNGEON_SESSION_JOINED id="+s.id+" player="+p.getName()+" players="+s.activeCount()+" via=teleport");
    }

    // ---------------------------------------------------------------- closing
    /** Once a second: membership follows where players really are, and empty runs close. */
    public void tick(){
        if(stopping||++ticks%20!=0)return;long now=System.currentTimeMillis();
        for(Session s:new ArrayList<>(live.values())){
            if(s.closed)continue;if(s.world(0)==null){close(s,"unloaded");continue;}
            boolean present=false;
            for(World w:s.loaded())for(Player p:new ArrayList<>(w.getPlayers())){
                present=true;UUID id=p.getUniqueId();if(p.isDead()||s.active(id))continue;
                // Whoever left never comes back; anyone else is part of the run from now on.
                if(s.left.contains(id))home(p,s,"left");else adopt(p,s);
            }
            // A member online outside every world of their run has left it by other means.
            for(UUID id:new ArrayList<>(s.members)){if(s.left.contains(id))continue;Player p=Bukkit.getPlayer(id);if(p!=null&&p.isOnline()&&!p.isDead()&&!s.owns(p.getWorld()))left(p,s,"elsewhere");}
            if(s.expired(present,now,grace(),window()))close(s,s.activeCount()==0?"abandoned":"empty");
        }
    }
    /**
     * Ends a run: anyone still inside goes home, its rooms sleep (their traps with them), its transient gate goes, its worlds
     * unload unsaved, and about ten seconds later its folders and journals are deleted off the main thread, with retries.
     * A player on the death screen keeps it open until they respawn. False when the close must wait.
     */
    boolean close(Session s,String reason){
        if(s.closed)return true;List<World> worlds=new ArrayList<>();
        for(int realm=RiftCatalog.COUNT;realm>=0;realm--){World w=Bukkit.getWorld(s.name(realm));if(w!=null)worlds.add(w);}
        for(World w:worlds)for(Player p:new ArrayList<>(w.getPlayers()))if(p.isDead()||!home(p,s,"closed")){deferred(s,w,"occupied");return false;}
        // Rifts first, the run's own world last. A world's rooms and gates go just before its own unload; if the server
        // refuses that unload the run lives on, so its return gate is put back (its rooms wake again from their journals).
        for(World w:worlds){
            if(plugin.encounters!=null)plugin.encounters.forget(w);if(plugin.hazards!=null)plugin.hazards.forget(w.getName());if(plugin.gates!=null)plugin.gates.drop(w);
            if(!Bukkit.unloadWorld(w,false)){World own=Bukkit.getWorld(s.name(0));if(own!=null&&plugin.gates!=null)plugin.gates.installReturnGate(own);deferred(s,w,"unload");return false;}
            s.unloaded++;int n=s.realm(w.getName());if(n>=0)s.worlds[n]=null;
        }
        s.closed=true;live.remove(s.id);for(int realm=0;realm<=RiftCatalog.COUNT;realm++)byWorld.remove(s.name(realm),s);
        final long age=(System.currentTimeMillis()-s.started)/1000L;final int unloaded=s.unloaded;
        List<File> folders=new ArrayList<>();for(int realm=0;realm<=RiftCatalog.COUNT;realm++)folders.add(new File(Bukkit.getWorldContainer(),s.name(realm)));folders.add(s.data);
        erase(folders,1,0,deleted->plugin.getLogger().info("DUNGEON_SESSION_CLOSED id="+s.id+" ageSeconds="+age+" worlds="+unloaded+" deleted="+deleted+" reason="+reason));
        return true;
    }
    /** The close waits and is retried each second; said once a minute at most. */
    private void deferred(Session s,World w,String why){if(s.deferred++%60==0)plugin.getLogger().warning("DUNGEON_SESSION_CLOSE_DEFERRED id="+s.id+" world="+w.getName()+" reason="+why);}
    /** About ten seconds after an unload, off the main thread: late chunk writes have landed, then the folders go. */
    private void erase(List<File> folders,int attempt,int done,IntConsumer finished){
        if(stopping)return;
        try{Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,()->{
            int deleted=done;List<File> rest=new ArrayList<>();
            for(File f:folders){if(!f.exists())continue;release(f);if(delete(f))deleted++;else rest.add(f);}
            if(rest.isEmpty()||attempt>=RETRIES){
                if(!rest.isEmpty())plugin.getLogger().warning("DUNGEON_SESSION_DELETE_FAILED folders="+names(rest)+" (the next start removes them)");
                finished.accept(deleted);
            }else{plugin.getLogger().warning("DUNGEON_SESSION_DELETE_RETRY attempt="+attempt+" folders="+names(rest));erase(rest,attempt+1,deleted,finished);}
        },DELETE_DELAY_TICKS);}catch(RuntimeException disabled){/* the plugin is stopping: the next start removes the folders */}
    }
    private static String names(List<File> files){StringBuilder b=new StringBuilder();for(File f:files){if(b.length()>0)b.append(',');b.append(f.getName());}return b.toString();}
    /** A late chunk save can reopen a region file of an unloaded world; close those handles first, as unloadWorld itself does. */
    private static void release(File folder){
        File root=folder.getAbsoluteFile();
        synchronized(net.minecraft.server.v1_12_R1.RegionFileCache.class){
            Iterator<Map.Entry<File,net.minecraft.server.v1_12_R1.RegionFile>> it=net.minecraft.server.v1_12_R1.RegionFileCache.a.entrySet().iterator();
            while(it.hasNext()){Map.Entry<File,net.minecraft.server.v1_12_R1.RegionFile> e=it.next();
                for(File f=e.getKey().getAbsoluteFile();f!=null;f=f.getParentFile())if(f.equals(root)){it.remove();try{e.getValue().c();}catch(IOException ignored){}break;}}
        }
    }
    /** A plugin reload can leave run worlds loaded with nobody tracking them: everyone inside goes home, then the worlds go. */
    public void orphans(){
        for(World w:new ArrayList<>(Bukkit.getWorlds())){
            if(!leftover(plugin.worldName,w.getName())||byWorld.containsKey(w.getName()))continue;
            boolean empty=true;for(Player p:new ArrayList<>(w.getPlayers()))if(p.isDead()||!home(p,null,"restart"))empty=false;else plugin.gates.mark(p.getUniqueId(),null);
            if(!empty||!Bukkit.unloadWorld(w,false)){plugin.getLogger().warning("DUNGEON_SESSION_ORPHAN_KEPT world="+w.getName());continue;}
            final String name=w.getName();plugin.getLogger().info("DUNGEON_SESSION_ORPHAN_UNLOADED world="+name);
            erase(Collections.singletonList(new File(Bukkit.getWorldContainer(),name)),1,0,deleted->plugin.getLogger().info("DUNGEON_SESSION_CLEANUP deleted="+deleted+" world="+name));
        }
    }
    public void stop(){stopping=true;}

    // ---------------------------------------------------------------- returning, dying, respawning
    /**
     * Returning players. While a run lives its worlds stay loaded, so Bukkit puts a returning member straight back where
     * they were. Anyone else who would spawn in a run world, and a marked player whose run is gone (Bukkit then falls back
     * to some other world, by dimension number, perhaps even a new run's), is sent home and the marker cleared.
     */
    @EventHandler(priority=EventPriority.HIGH) public void spawning(PlayerSpawnLocationEvent e){
        Player p=e.getPlayer();UUID id=p.getUniqueId();Location at=e.getSpawnLocation();Session here=at==null?null:of(at.getWorld());String marker=plugin.gates.marker(id);
        if(here!=null&&here.active(id)){
            notices.put(id,ChatColor.GRAY+"You are back in run #"+here.id+". Leaving, dying or /dungeon leave ends it.");
            plugin.getLogger().info("DUNGEON_SESSION_RESUMED id="+here.id+" player="+p.getName()+" world="+at.getWorld().getName());return;
        }
        if(here==null&&marker==null)return;
        // Only a run the player really belonged to records them as gone. Landing in a stranger's run (the fallback by
        // dimension number can pick another run's world) leaves that run untouched.
        boolean member=here!=null&&here.members.contains(id);
        Session marked=marker==null?null:byWorld.get(marker),ended=member?here:marked;
        String reason=member?"left":here!=null?"stranger":marked!=null?"elsewhere":"ended";
        if(ended!=null&&ended.left.add(id))plugin.getLogger().info("DUNGEON_SESSION_LEFT id="+ended.id+" player="+p.getName()+" reason="+reason+" players="+ended.activeCount());
        Location home=plugin.gates.returnLocation(p);e.setSpawnLocation(home!=null?home:Bukkit.getWorlds().get(0).getSpawnLocation());
        if(marker!=null)plugin.gates.mark(id,null);
        plugin.getLogger().info("DUNGEON_SESSION_SENT_HOME id="+(ended!=null?String.valueOf(ended.id):"-")+" world="+(marker!=null?marker:here.root)+" player="+p.getName()+" reason="+reason+" landed="+(here==null?"-":String.valueOf(here.id)));
        notices.put(id,ChatColor.GRAY+"Your dungeon run has ended. The Last Candle brought you home; the gate opens a new run.");
    }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent e){String n=notices.remove(e.getPlayer().getUniqueId());if(n!=null)e.getPlayer().sendMessage(n);}
    @EventHandler public void quit(PlayerQuitEvent e){notices.remove(e.getPlayer().getUniqueId());}
    /**
     * A death ends the player's part in the run. The run world is deleted with the run, so nothing may stay behind there:
     * keepInventory (the world's gamerule, and on the event for every later listener) keeps their items and levels.
     */
    @EventHandler(priority=EventPriority.LOWEST) public void died(PlayerDeathEvent e){
        Player p=e.getEntity();Session s=of(p.getWorld());if(s==null)return;
        e.setKeepInventory(true);e.setKeepLevel(true);e.getDrops().clear();e.setDroppedExp(0);
        fallen.add(p.getUniqueId());left(p,s,"death");
    }
    /** Whoever died in a run (or would respawn in a run world) comes back at their way home. */
    @EventHandler(priority=EventPriority.HIGH) public void respawn(PlayerRespawnEvent e){
        Player p=e.getPlayer();boolean died=fallen.remove(p.getUniqueId()),inside=plugin.inside(e.getRespawnLocation().getWorld());if(!died&&!inside)return;
        Location home=plugin.gates.returnLocation(p);
        if(home!=null)e.setRespawnLocation(home);else if(inside)e.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unloaded(WorldUnloadEvent e){
        Session s=of(e.getWorld());if(s==null)return;int n=s.realm(e.getWorld().getName());if(n>=0&&s.worlds[n]==e.getWorld())s.worlds[n]=null;
    }
}
