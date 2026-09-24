package chat.jaspr.biomes;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Finite, native protocol-340 dungeon encounters. Integration: construct/start on enable,
 * stop on disable, and require defeated(site) before opening a boss site's vault.
 *
 * All mutable work runs on the server thread. Marker claims and terminal states are forced to
 * disk before their side effects. Stable entity UUIDs and scoreboard tags survive chunk saves.
 * No spawners, natural drop rewards, glowing, block edits, world entity scans or chunk loads.
 * Native damage events feed existing gore and protection plugins. AI is driven here so vanilla
 * pathfinding cannot pursue players into unloaded chunks or attack before authentication.
 *
 * Recovery is deliberately conservative: every chunk in the durable residency history must
 * already be loaded before two separated missing-UUID checks permit a
 * replacement. An unloaded entity is never evidence of death, and only KILLED unlocks loot.
 */
public final class StructureEncounters implements Listener {
    public static final int REGULAR_CAP=32, BOSS_CAP=8, PROJECTILE_CAP=48;
    private static final int RANGE=64, TETHER=24, MAX_SUMMONS=4;
    private static final String TAG="jaspr_encounter_v1:", SHOT="jaspr_encounter_shot_v1";
    private final HorrorPlugin plugin;
    private EncounterJournal journal;
    private BukkitTask task;
    private boolean running, failed;
    private String error="none";
    private long tick, spawned, recovered, deaths, duplicates, deferred;
    private int discoveryCursor;
    private Entity[] scanning=new Entity[0];
    private int scanIndex;
    private final Map<UUID,Actor> active=new LinkedHashMap<>();
    private final Map<UUID,Shot> shots=new LinkedHashMap<>();
    private final Map<UUID,Long> missingSince=new LinkedHashMap<>();
    private final LinkedHashMap<String,Chunk> scanQueue=new LinkedHashMap<>();
    private final ArrayDeque<String> scanOrder=new ArrayDeque<>();
    private final LinkedHashMap<String,List<StructurePlanner.Site>> plans=new LinkedHashMap<String,List<StructurePlanner.Site>>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<StructurePlanner.Site>> e) { return size()>256; }
    };

    enum Attack { STRIKE, LUNGE, BOLT, MIASMA, FROST, THRUST, HOWL, DRAIN, PULL, SKITTER, GUARD, QUAKE, CLEAVE, RING, SALVO, SUMMON }
    enum Theme {
        ASH_SHAMBLER("Ash Shambler",Zombie.class,Attack.STRIKE,0x5b554b,1.0),
        RUST_CUTTER("Rust Cutter",Zombie.class,Attack.LUNGE,0x863f2d,1.2),
        CINDER_ARCHER("Cinder Archer",Skeleton.class,Attack.BOLT,0x6d3825,.8),
        BOG_WALKER("Bog Walker",Husk.class,Attack.MIASMA,0x455438,.8),
        FROST_WATCHER("Frost Watcher",Skeleton.class,Attack.FROST,0x72878c,.85),
        CRYPT_SPEARMAN("Crypt Spearman",Skeleton.class,Attack.THRUST,0x5b5468,.95),
        BELL_KEEPER("Bell Keeper",Zombie.class,Attack.HOWL,0x766a37,.85),
        PLAGUE_NURSE("Plague Nurse",Zombie.class,Attack.DRAIN,0x998b78,1.0),
        CHAIN_GAOLER("Chain Gaoler",Husk.class,Attack.PULL,0x555960,.9),
        OSSUARY_SCUTTLER("Ossuary Scuttler",Spider.class,Attack.SKITTER,0x46352e,1.5),
        GRAVE_SENTINEL("Grave Sentinel",WitherSkeleton.class,Attack.GUARD,0x3b3e4c,.65),
        DROWNED_MINER("Drowned Miner",Zombie.class,Attack.QUAKE,0x3d6764,.9);
        final String label; final Class<? extends LivingEntity> entity; final Attack attack; final int color; final double speed;
        Theme(String label,Class<? extends LivingEntity> entity,Attack attack,int color,double speed) {
            this.label=label; this.entity=entity; this.attack=attack; this.color=color; this.speed=speed;
        }
    }
    enum Boss {
        OSSUARY_REGENT("Ossuary Regent",WitherSkeleton.class,0x504767,Attack.CLEAVE,Attack.SUMMON,Attack.SALVO),
        BELL_MATRON("Bell Matron",Zombie.class,0x88713a,Attack.HOWL,Attack.RING,Attack.PULL),
        FURNACE_JAILER("Furnace Jailer",Husk.class,0x993e27,Attack.SALVO,Attack.CLEAVE,Attack.QUAKE),
        MIRE_ABBESS("Mire Abbess",Zombie.class,0x4e673c,Attack.DRAIN,Attack.SUMMON,Attack.MIASMA),
        GLASS_HUNTSMAN("Glass Huntsman",Skeleton.class,0x6d8b8f,Attack.BOLT,Attack.FROST,Attack.SALVO),
        CHAIN_JUDGE("Chain Judge",WitherSkeleton.class,0x575968,Attack.PULL,Attack.THRUST,Attack.CLEAVE),
        HOLLOW_CHORISTER("Hollow Chorister",Skeleton.class,0x7e5674,Attack.RING,Attack.HOWL,Attack.SUMMON),
        GRAVE_ENGINE("Grave Engine",Zombie.class,0x69533b,Attack.QUAKE,Attack.LUNGE,Attack.CLEAVE);
        final String label; final Class<? extends LivingEntity> entity; final int color; final Attack[] attacks;
        Boss(String label,Class<? extends LivingEntity> entity,int color,Attack... attacks) {
            this.label=label; this.entity=entity; this.color=color; this.attacks=attacks;
        }
    }
    private static final class Actor {
        final LivingEntity entity;
        EncounterJournal.Record record;
        long ready, resolve, melee;
        int sequence;
        Attack attack;
        Location aim, origin;
        UUID target;
        Actor(LivingEntity entity,EncounterJournal.Record record,long now) {
            this.entity=entity; this.record=record; ready=now+40;
        }
    }
    private static final class Shot {
        final Arrow arrow; final UUID owner; final long expires; final Attack attack; final double damage;
        Shot(Arrow arrow,Actor actor,long tick) {
            this.arrow=arrow; owner=actor.record.id; expires=tick+40; attack=actor.attack; damage=damage(actor.record);
        }
    }

    public StructureEncounters(HorrorPlugin plugin) { this.plugin=Objects.requireNonNull(plugin,"plugin"); }
    public void start() {
        mainThread(); if(running)return;
        failed=false; error="none";
        try { journal=new EncounterJournal(new File(plugin.getDataFolder(),plugin.terrainEpoch().equals("structures-v2")?"structure-encounters-v1.bin":"structure-encounters-"+plugin.terrainEpoch()+".bin")); }
        catch(IOException ex) { fail(ex); }
        running=true; Bukkit.getPluginManager().registerEvents(this,plugin);
        for(World world:Bukkit.getWorlds())if(enabled(world))for(Chunk chunk:world.getLoadedChunks())queue(chunk);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::step,1,1);
    }
    public void stop() {
        mainThread(); if(task!=null)task.cancel(); task=null;
        for(Shot shot:shots.values())shot.arrow.remove(); shots.clear();
        for(Actor actor:active.values())freeze(actor.entity);
        active.clear(); scanQueue.clear(); scanOrder.clear(); scanning=new Entity[0]; scanIndex=0; plans.clear(); missingSince.clear();
        HandlerList.unregisterAll(this); running=false;
        if(journal!=null)try { journal.close(); } catch(IOException ex) { fail(ex); }
        journal=null;
    }

    /** The agreed Site API has no world; the compatibility overload is explicitly overworld-only. */
    public boolean defeated(StructurePlanner.Site site) { return defeated(Bukkit.getWorld("world"),site); }
    public boolean defeated(World world,StructurePlanner.Site site) {
        if(!healthy() || !enabled(world) || site==null)return false;
        for(StructurePlanner.Marker marker:site.markers())if(isBoss(marker.kind)) {
            EncounterJournal.Record record=journal.get(EncounterJournal.identity(world.getUID(),site.key,marker.ordinal,-1));
            if(record==null || record.state!=EncounterJournal.State.KILLED)return false;
        }
        return true;
    }
    public String metrics() {
        return "encounters="+(healthy()?"ready":"locked")+" regular="+count(false)+"/"+REGULAR_CAP
            +" bosses="+count(true)+"/"+BOSS_CAP+" projectiles="+shots.size()+" records="+(journal==null?0:journal.size())
            +" spawned="+spawned+" recovered="+recovered+" killed="+deaths+" duplicateEntitiesRemoved="+duplicates
            +" deferred="+deferred+" pendingMissingChecks="+missingSince.size()+" scansQueued="+scanQueue.size()+" error="+error;
    }
    /** Command-ready snapshot: at most eight active bosses, no entity/chunk scans. */
    public String snapshot() {
        StringBuilder out=new StringBuilder(metrics());
        for(Actor actor:active.values())if(actor.record.boss)out.append(" | ").append(actor.record.theme)
            .append(" uuid=").append(actor.record.id).append(" site=").append(actor.record.site)
            .append(" phase=").append(actor.record.phase).append(" hp=").append(Math.ceil(actor.entity.getHealth()))
            .append(" attack=").append(actor.attack==null?"ready":actor.attack.name());
        return out.toString();
    }
    /** Read-only, bounded by this site's markers; useful from an admin status command. */
    public String state(World world,StructurePlanner.Site site) {
        if(!healthy())return "LOCKED_IO: "+error;
        if(!enabled(world) || site==null)return "UNSUPPORTED_WORLD_OR_SITE";
        StringBuilder result=new StringBuilder("site="+site.key+" world="+world.getUID());
        for(StructurePlanner.Marker marker:site.markers())if(isMob(marker.kind)) {
            UUID id=EncounterJournal.identity(world.getUID(),site.key,marker.ordinal,-1);
            EncounterJournal.Record record=journal.get(id);
            result.append(" | ").append(marker.ordinal).append(isBoss(marker.kind)?":boss=":":mob=");
            if(record==null) { result.append("UNCLAIMED"); continue; }
            result.append(record.state).append(" uuid=").append(id).append(" phase=").append(record.phase)
                .append(" summons=").append(record.summons);
            if(!record.terminal()) {
                if(active.containsKey(id))result.append(" LOADED_ACTIVE");
                else if(Bukkit.getEntity(id)!=null)result.append(capacity(record.boss)?" LOADED_PENDING_ADOPTION":" DORMANT_AT_CAP");
                else if(!recoveryLoaded(world,record))result.append(" WAITING_FOR_LOADED_RECOVERY_AREA");
                else if(spawnFloor(new Location(world,record.x+.5,record.y,record.z+.5))==null)result.append(" BLOCKED_ARENA_NO_SAFE_FLOOR");
                else result.append(" MISSING_RECOVERY_PENDING");
            }
        }
        return result.toString();
    }

    private static boolean enabled(World world) { return world!=null && world.getName().equals("world") && world.getEnvironment()==World.Environment.NORMAL; }
    private boolean healthy() { return running && !failed && journal!=null && journal.healthy(); }
    private static void mainThread() { if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Encounters require the server thread"); }
    private void fail(Exception ex) {
        failed=true; error=ex.getClass().getSimpleName()+":"+String.valueOf(ex.getMessage()).replace('\n',' ');
        plugin.getLogger().severe("STRUCTURE_ENCOUNTERS_LOCKED "+error);
    }
    private boolean commit(EncounterJournal.Record record) {
        if(journal==null || failed)return false;
        try { journal.put(record); return true; } catch(IOException ex) { fail(ex); return false; }
    }
    static boolean isBoss(String kind) { return kind!=null && (kind.equalsIgnoreCase("boss") || kind.toLowerCase(Locale.ROOT).startsWith("boss:")); }
    static boolean isMob(String kind) { return isBoss(kind) || kind!=null && (kind.equalsIgnoreCase("mob") || kind.toLowerCase(Locale.ROOT).startsWith("mob:")); }
    private static UUID tagged(Entity entity) {
        for(String tag:entity.getScoreboardTags())if(tag.startsWith(TAG))try { return UUID.fromString(tag.substring(TAG.length())); } catch(IllegalArgumentException ignored) { return null; }
        return null;
    }
    private static String chunkKey(World w,int x,int z) { return w.getUID()+":"+x+":"+z; }
    private int count(boolean bosses) { int n=0; for(Actor actor:active.values())if(actor.record.boss==bosses)n++; return n; }
    private boolean capacity(boolean boss) { return count(boss)<(boss?BOSS_CAP:REGULAR_CAP); }
    private static boolean survivor(Player p) {
        return p!=null && p.isOnline() && !p.isDead() && p.getGameMode()==GameMode.SURVIVAL && HorrorPlugin.authenticated(p);
    }
    private static boolean loaded(Location p) { return p!=null && p.getWorld()!=null && p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4); }
    private static boolean withinHome(EncounterJournal.Record r,Location p,double radius) {
        double x=p.getX()-(r.x+.5),z=p.getZ()-(r.z+.5);
        return p.getWorld().getUID().equals(r.world) && x*x+z*z<=radius*radius && Math.abs(p.getY()-r.y)<=16;
    }
    private static boolean recoveryLoaded(World w,EncounterJournal.Record r) {
        for(long place:r.residency)if(!w.isChunkLoaded((int)(place>>32),(int)place))return false;
        return true;
    }

    private void queue(Chunk chunk) {
        if(!enabled(chunk.getWorld()))return;
        String key=chunkKey(chunk.getWorld(),chunk.getX(),chunk.getZ());
        if(scanQueue.containsKey(key))scanOrder.remove(key);
        else if(scanQueue.size()>=512)scanQueue.remove(scanOrder.removeFirst());
        scanQueue.put(key,chunk); scanOrder.addLast(key);
    }
    @EventHandler public void loaded(ChunkLoadEvent event) { queue(event.getChunk()); }
    @EventHandler public void worldLoaded(WorldLoadEvent event) { if(enabled(event.getWorld()))for(Chunk chunk:event.getWorld().getLoadedChunks())queue(chunk); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unloaded(ChunkUnloadEvent event) {
        Chunk chunk=event.getChunk(); String key=chunkKey(chunk.getWorld(),chunk.getX(),chunk.getZ()); scanQueue.remove(key); scanOrder.remove(key);
        for(Actor actor:new ArrayList<>(active.values())) {
            Location at=actor.entity.getLocation();
            if(at.getWorld()==chunk.getWorld() && at.getBlockX()>>4==chunk.getX() && at.getBlockZ()>>4==chunk.getZ()) {
                checkpoint(actor,at); freeze(actor.entity); active.remove(actor.record.id); missingSince.remove(actor.record.id);
            }
        }
        for(Shot shot:new ArrayList<>(shots.values()))if(shot.arrow.getWorld()==chunk.getWorld()
                && shot.arrow.getLocation().getBlockX()>>4==chunk.getX() && shot.arrow.getLocation().getBlockZ()>>4==chunk.getZ()) {
            shot.arrow.remove(); shots.remove(shot.arrow.getUniqueId());
        }
    }
    private void scan() {
        // At most one chunk snapshot and 128 entities per tick, including crowded farm chunks.
        if(scanIndex>=scanning.length) {
            scanning=new Entity[0]; scanIndex=0;
            if(scanQueue.isEmpty())return;
            // A newly loaded encounter chunk must not sit behind the startup spawn-region backlog.
            Chunk chunk=scanQueue.remove(scanOrder.removeLast());
            if(!chunk.getWorld().isChunkLoaded(chunk.getX(),chunk.getZ()))return;
            scanning=chunk.getEntities();
        }
        for(int budget=0;scanIndex<scanning.length && budget<128;budget++) {
            Entity entity=scanning[scanIndex++];
            if(!loaded(entity.getLocation()))continue;
            if(entity.getScoreboardTags().contains(SHOT)) { if(!shots.containsKey(entity.getUniqueId()))entity.remove(); continue; }
            if(entity instanceof LivingEntity && tagged(entity)!=null)adopt((LivingEntity)entity);
        }
    }
    private void adopt(LivingEntity entity) {
        UUID id=tagged(entity); if(id==null)return;
        if(!healthy()) { freeze(entity); return; }
        EncounterJournal.Record r=journal.get(id);
        // Unknown/stale entities cannot establish claims or defeat bosses.
        if(r==null || r.terminal() || !r.world.equals(entity.getWorld().getUID()) || !r.id.equals(entity.getUniqueId())) {
            entity.remove(); duplicates++; return;
        }
        if(r.parent!=null) {
            EncounterJournal.Record parent=journal.get(r.parent);
            if(parent==null || parent.terminal()) { if(commit(r.state(EncounterJournal.State.RETIRED)))entity.remove(); return; }
        }
        Actor existing=active.get(id);
        if(existing!=null) {
            if(existing.entity!=entity) { entity.remove(); duplicates++; }
            return;
        }
        freeze(entity);
        if(!capacity(r.boss)) {
            // Already saved entities remain persistent, dormant and rewardless at capacity.
            // Discovery re-adopts the SAME UUID when a slot opens; never remove them merely for a cap.
            deferred++; return;
        }
        if(r.state!=EncounterJournal.State.ACTIVE) { r=r.state(EncounterJournal.State.ACTIVE); if(!commit(r))return; }
        configure(entity,r,false); active.put(id,new Actor(entity,r,tick)); missingSince.remove(id);
    }
    private void step() {
        tick++; scan();
        if(!healthy()) { for(Actor actor:active.values())freeze(actor.entity); return; }
        for(Shot shot:new ArrayList<>(shots.values())) {
            Actor owner=active.get(shot.owner); Location at=shot.arrow.getLocation();
            Location next=at.clone().add(shot.arrow.getVelocity().multiply(2));
            if(owner==null || !shot.arrow.isValid() || shot.arrow.isDead() || tick>=shot.expires
                    || !withinHome(owner.record,at,RANGE) || !loadedBox(next,2)) {
                shot.arrow.remove(); shots.remove(shot.arrow.getUniqueId());
            }
        }
        List<Player> players=new ArrayList<>();
        for(Player player:Bukkit.getOnlinePlayers())if(enabled(player.getWorld()) && survivor(player))players.add(player);
        for(Actor actor:new ArrayList<>(active.values())) {
            LivingEntity entity=actor.entity;
            if(!entity.isValid() || entity.isDead() || !loaded(entity.getLocation())) { active.remove(actor.record.id); continue; }
            // Suppress collision/knockback drift as well as vanilla AI while dormant.
            entity.setVelocity(new Vector()); entity.setGlowing(false);
            if(!checkpoint(actor,entity.getLocation()))continue;
            if(!withinHome(actor.record,entity.getLocation(),TETHER)) { returnHome(actor); continue; }
            if(tick%5==0)advance(actor,players);
        }
        if(tick%5==0 && !players.isEmpty())discover(players);
    }
    private boolean checkpoint(Actor actor,Location at) {
        int cx=at.getBlockX()>>4,cz=at.getBlockZ()>>4;
        if(actor.record.chunkX==cx && actor.record.chunkZ==cz)return true;
        EncounterJournal.Record next=actor.record.position(cx,cz);
        if(!commit(next)) { freeze(actor.entity); return false; }
        actor.record=next; return true;
    }
    private void discover(List<Player> players) {
        // Rotate through each survivor's 9x9 area, four already-loaded chunks per pass.
        for(int n=0;n<4;n++) {
            int i=Math.floorMod(discoveryCursor++,players.size()*81); Player p=players.get(i/81);
            int cell=i%81,cx=(p.getLocation().getBlockX()>>4)+cell%9-4,cz=(p.getLocation().getBlockZ()>>4)+cell/9-4;
            World world=p.getWorld(); if(!world.isChunkLoaded(cx,cz))continue;
            String key=chunkKey(world,cx,cz); List<StructurePlanner.Site> sites=plans.get(key);
            if(sites==null) { sites=WorldgenExpansion.sites(world,cx,cz); plans.put(key,sites); }
            for(StructurePlanner.Site site:sites)for(StructurePlanner.Marker marker:site.markers()) {
                if(!isMob(marker.kind) || marker.x>>4!=cx || marker.z>>4!=cz)continue;
                Location home=new Location(world,marker.x+.5,marker.y,marker.z+.5);
                boolean boss=isBoss(marker.kind);
                if(boss?!enteredArena(site,home,players):!near(home,players,36))continue;
                UUID id=EncounterJournal.identity(world.getUID(),site.key,marker.ordinal,-1);
                EncounterJournal.Record r=journal.get(id);
                if(r==null) {
                    if(!capacity(boss))continue;
                    Location safe=spawnFloor(home); if(safe==null)continue;
                    Catalog.Profile biome=plugin.at(world,marker.x,marker.z);
                    r=new EncounterJournal.Record(world.getUID(),site.key,marker.ordinal,-1,null,boss,
                        chooseTheme(site,marker,boss,biome.atmosphere,biome.name),Math.max(1,Math.min(5,site.design.tier)),marker.x,safe.getBlockY(),marker.z);
                    if(commit(r))spawn(r,safe,false);
                } else if(!r.terminal() && !active.containsKey(id))recover(world,r);
            }
        }
    }
    static String chooseTheme(StructurePlanner.Site site,StructurePlanner.Marker marker,boolean boss,String atmosphere,String biomeName) {
        String kind=marker.kind; int colon=kind.indexOf(':');
        if(colon>=0) {
            String requested=kind.substring(colon+1).toUpperCase(Locale.ROOT).replace('-','_');
            try { return boss?Boss.valueOf(requested).name():Theme.valueOf(requested).name(); } catch(IllegalArgumentException ignored) { }
        }
        int hash=Objects.hash(site.design.family,site.design.id,site.key,marker.ordinal);
        String[] pool=themePool(boss,site.design.family,atmosphere,biomeName,site.design.mode);
        return pool[Math.floorMod(hash,pool.length)];
    }
    static String[] themePool(boolean boss,String family,String atmosphere,String biomeName,String mode) {
        String context=(family+" "+biomeName).toLowerCase(Locale.ROOT);
        String choices;
        if(atmosphere.equals("snow"))choices=boss?"GLASS_HUNTSMAN":"FROST_WATCHER CRYPT_SPEARMAN GRAVE_SENTINEL";
        else if(context.matches(".*(hospital|hospice|asylum|medical).*"))choices=boss?"BELL_MATRON MIRE_ABBESS":"PLAGUE_NURSE BELL_KEEPER BOG_WALKER";
        else if(mode.equals("underwater") || context.matches(".*(underwater|sunken|drowned|harbor|mire|marsh|sewer).*"))choices=boss?"MIRE_ABBESS CHAIN_JUDGE":"DROWNED_MINER BOG_WALKER CHAIN_GAOLER";
        else if(context.matches(".*(castle|keep|bastion|fortress|palace).*"))choices=boss?"OSSUARY_REGENT":"GRAVE_SENTINEL CRYPT_SPEARMAN RUST_CUTTER";
        else if(context.matches(".*(metro|rail|station|prison|trench).*"))choices=boss?"CHAIN_JUDGE GRAVE_ENGINE":"CHAIN_GAOLER DROWNED_MINER RUST_CUTTER";
        else if(context.matches(".*(cathedral|church|shrine|chapel|bell).*"))choices=boss?"HOLLOW_CHORISTER BELL_MATRON":"BELL_KEEPER CRYPT_SPEARMAN GRAVE_SENTINEL";
        else if(context.matches(".*(foundry|furnace|kiln|reactor|mine|cinder|ember).*"))choices=boss?"FURNACE_JAILER GRAVE_ENGINE":"CINDER_ARCHER RUST_CUTTER ASH_SHAMBLER";
        else if(atmosphere.equals("ash"))choices=boss?"FURNACE_JAILER OSSUARY_REGENT":"ASH_SHAMBLER CINDER_ARCHER RUST_CUTTER";
        else if(atmosphere.equals("spores") || atmosphere.equals("mist") || atmosphere.equals("rain"))choices=boss?"MIRE_ABBESS HOLLOW_CHORISTER":"BOG_WALKER OSSUARY_SCUTTLER DROWNED_MINER";
        else if(atmosphere.equals("rust") || atmosphere.equals("static"))choices=boss?"CHAIN_JUDGE GRAVE_ENGINE":"RUST_CUTTER CHAIN_GAOLER OSSUARY_SCUTTLER";
        else if(atmosphere.equals("whisper"))choices=boss?"HOLLOW_CHORISTER BELL_MATRON":"BELL_KEEPER PLAGUE_NURSE OSSUARY_SCUTTLER";
        else choices=boss?"OSSUARY_REGENT GRAVE_ENGINE":"ASH_SHAMBLER CRYPT_SPEARMAN GRAVE_SENTINEL";
        return choices.split(" ");
    }
    private void recover(World world,EncounterJournal.Record r) {
        if(!capacity(r.boss))return;
        Entity present=Bukkit.getEntity(r.id);
        if(present!=null) { if(present instanceof LivingEntity)adopt((LivingEntity)present); return; }
        if(!recoveryLoaded(world,r)) { missingSince.remove(r.id); return; }
        Long since=missingSince.get(r.id);
        if(since==null) {
            if(missingSince.size()>=256)missingSince.remove(missingSince.keySet().iterator().next());
            missingSince.put(r.id,tick); return;
        }
        if(tick-since<20)return;
        Location safe=spawnFloor(new Location(world,r.x+.5,r.y,r.z+.5));
        if(safe==null)return;
        // Serialize replacement claim before spawning, preserving identity, phase and summon budget.
        EncounterJournal.Record claimed=r.state(EncounterJournal.State.CLAIMED);
        if(commit(claimed))spawn(claimed,safe,true);
    }
    private void spawn(EncounterJournal.Record r,Location location,boolean recovery) {
        if(!healthy() || !capacity(r.boss) || !loaded(location) || Bukkit.getEntity(r.id)!=null)return;
        if(r.chunkX!=location.getBlockX()>>4 || r.chunkZ!=location.getBlockZ()>>4) {
            EncounterJournal.Record moved=r.position(location.getBlockX()>>4,location.getBlockZ()>>4);
            if(commit(moved))spawn(moved,location,recovery); return; // Reserve fallback destination before insertion.
        }
        Class<? extends LivingEntity> type=r.boss?Boss.valueOf(r.theme).entity:Theme.valueOf(r.theme).entity;
        LivingEntity entity=null;
        try {
            // The consumer runs before world insertion/CreatureSpawnEvent; tags/UUID precede any save.
            entity=location.getWorld().spawn(location,type,created->{
                ((CraftEntity)created).getHandle().setUUID(r.id);
                created.addScoreboardTag(TAG+r.id); created.addScoreboardTag("jaspr_vanilla_undead");
                configure(created,r,true);
            });
            if(entity==null || !entity.isValid() || entity.isDead())return; // A protection plugin may cancel the spawn.
            EncounterJournal.Record next=r.state(EncounterJournal.State.ACTIVE);
            if(!commit(next)) { freeze(entity); return; }
            active.put(r.id,new Actor(entity,next,tick)); missingSince.remove(r.id); spawned++; if(recovery)recovered++;
            if(r.boss)for(Player player:location.getWorld().getPlayers())if(survivor(player) && player.getLocation().distanceSquared(location)<400) {
                Catalog.Profile biome=plugin.at(location.getWorld(),r.x,r.z);
                player.sendMessage(ChatColor.DARK_GRAY+biome.name+" / "+family(location.getWorld(),r)+": "+Boss.valueOf(r.theme).label+" awakens.");
            }
        } catch(RuntimeException ex) {
            if(entity!=null)freeze(entity);
            plugin.getLogger().warning("ENCOUNTER_SPAWN_DEFERRED uuid="+r.id+" reason="+ex.getClass().getSimpleName());
        }
    }
    private static void freeze(LivingEntity entity) {
        entity.setAI(false); entity.setGravity(false); entity.setCollidable(false); entity.setVelocity(new Vector());
        if(entity instanceof Creature)((Creature)entity).setTarget(null);
    }
    private void configure(LivingEntity entity,EncounterJournal.Record r,boolean fresh) {
        freeze(entity); entity.setRemoveWhenFarAway(false); entity.setCanPickupItems(false); entity.setGlowing(false);
        attribute(entity,Attribute.GENERIC_FOLLOW_RANGE,RANGE); attribute(entity,Attribute.GENERIC_KNOCKBACK_RESISTANCE,1);
        attribute(entity,Attribute.GENERIC_MAX_HEALTH,health(r)); attribute(entity,Attribute.GENERIC_ATTACK_DAMAGE,damage(r));
        attribute(entity,Attribute.GENERIC_MOVEMENT_SPEED,.18+.015*r.tier);
        if(entity instanceof Zombie) { ((Zombie)entity).setBaby(false); attribute(entity,Attribute.ZOMBIE_SPAWN_REINFORCEMENTS,0); }
        if(fresh)entity.setHealth(health(r));
        int color=r.boss?Boss.valueOf(r.theme).color:Theme.valueOf(r.theme).color;
        String label=r.boss?Boss.valueOf(r.theme).label:Theme.valueOf(r.theme).label;
        entity.setCustomName((r.boss?ChatColor.DARK_RED:ChatColor.GRAY)+label+" [T"+r.tier+"]");
        entity.setCustomNameVisible(r.boss);
        EntityEquipment equipment=entity.getEquipment();
        if(equipment!=null) {
            equipment.clear(); equipment.setHelmet(leather(Material.LEATHER_HELMET,color));
            equipment.setChestplate(leather(Material.LEATHER_CHESTPLATE,color));
            equipment.setLeggings(leather(Material.LEATHER_LEGGINGS,color)); equipment.setBoots(leather(Material.LEATHER_BOOTS,color));
            Material weapon=entity instanceof Skeleton?Material.BOW:Material.STONE_SWORD;
            if(r.theme.equals("DROWNED_MINER"))weapon=Material.IRON_PICKAXE;
            if(r.theme.equals("CHAIN_GAOLER") || r.theme.equals("CHAIN_JUDGE"))weapon=Material.IRON_AXE;
            if(r.theme.equals("PLAGUE_NURSE"))weapon=Material.SHEARS;
            equipment.setItemInMainHand(new ItemStack(weapon));
            equipment.setHelmetDropChance(0); equipment.setChestplateDropChance(0); equipment.setLeggingsDropChance(0);
            equipment.setBootsDropChance(0); equipment.setItemInMainHandDropChance(0); equipment.setItemInOffHandDropChance(0);
        }
    }
    private String family(World world,EncounterJournal.Record r) {
        for(StructurePlanner.Site site:WorldgenExpansion.sites(world,r.x>>4,r.z>>4))if(site.key.equals(r.site))return site.design.family;
        return "ruins";
    }
    private static ItemStack leather(Material type,int color) {
        ItemStack item=new ItemStack(type); LeatherArmorMeta meta=(LeatherArmorMeta)item.getItemMeta();
        meta.setColor(Color.fromRGB(color)); item.setItemMeta(meta); return item;
    }
    private static void attribute(LivingEntity entity,Attribute attribute,double value) {
        AttributeInstance instance=entity.getAttribute(attribute); if(instance!=null)instance.setBaseValue(value);
    }
    static double health(EncounterJournal.Record r) { return r.boss?100+55*r.tier:15+8*r.tier; }
    static double damage(EncounterJournal.Record r) { return r.boss?4+1.8*r.tier:1.5+.9*r.tier; }

    private static boolean near(Location location,List<Player> players,double radius) {
        for(Player player:players)if(player.getWorld()==location.getWorld() && player.getLocation().distanceSquared(location)<=radius*radius)return true;
        return false;
    }
    private static boolean enteredArena(StructurePlanner.Site site,Location home,List<Player> players) {
        for(Player player:players) {
            Location at=player.getLocation();
            if(player.getWorld()!=home.getWorld() || !survivor(player) || Math.abs(at.getY()-home.getY())>3
                    || at.getX()<site.x+1 || at.getX()>=site.x+site.width-1
                    || at.getZ()<site.z+1 || at.getZ()>=site.z+site.depth-1 || at.distanceSquared(home)>100
                    || Math.abs(at.getX()-home.getX())>4.5 || Math.abs(at.getZ()-home.getZ())>4.5)continue;
            if(lineOfSight(player.getEyeLocation(),home.clone().add(0,1.5,0)))return true;
        }
        return false;
    }
    private Player target(Actor actor,List<Player> players) {
        Player best=null; double distance=RANGE*RANGE;
        for(Player p:players)if(canHit(actor,p)) {
            double d=p.getLocation().distanceSquared(actor.entity.getLocation()); if(d<distance) { best=p; distance=d; }
        }
        return best;
    }
    private static boolean canHit(Actor actor,Player p) {
        return survivor(p) && p.getWorld()==actor.entity.getWorld() && withinHome(actor.record,p.getLocation(),RANGE)
            && p.getLocation().distanceSquared(actor.entity.getLocation())<=RANGE*RANGE;
    }
    private void advance(Actor actor,List<Player> players) {
        Player target=target(actor,players);
        if(target==null) { actor.attack=null; actor.resolve=0; returnHome(actor); return; }
        if(actor.entity instanceof Creature) {
            Creature creature=(Creature)actor.entity;
            if(creature.getTarget()!=target)creature.setTarget(target);
            if(creature.getTarget()!=target) { actor.attack=null; return; } // Target protection cancellation.
        }
        EncounterJournal.Record r=actor.record;
        if(r.boss) {
            double fraction=actor.entity.getHealth()/health(r); int phase=fraction<=.30?2:fraction<=.65?1:0;
            if(phase>r.phase) {
                EncounterJournal.Record next=r.progress(phase,r.summons); if(!commit(next))return;
                actor.record=next; r=next;
                announce(actor,players,phase==2?"enters its final rage — attacks quicken!":"breaks its first seal!");
                actor.ready=Math.max(actor.ready,tick+30); // The phase change itself is a telegraph.
                actor.attack=null; actor.melee=Math.max(actor.melee,tick+30); return;
            }
            restoreSummons(actor);
        }
        if(actor.attack!=null) {
            if(tick>=actor.resolve) {
                resolve(actor,players); actor.attack=null;
                actor.ready=tick+(r.boss?Math.max(35,90-r.tier*5-r.phase*15):80-r.tier*6);
            } else telegraph(actor);
            return;
        }
        double distance=actor.entity.getLocation().distanceSquared(target.getLocation());
        if(tick>=actor.ready && lineOfSight(actor.entity.getEyeLocation(),target.getEyeLocation())) {
            Attack attack=r.boss?bossAttack(actor):Theme.valueOf(r.theme).attack;
            double range=attackRange(attack);
            if(distance<=range*range) { windup(actor,target,attack,players); return; }
        }
        if(distance<6.25 && tick>=actor.melee && lineOfSight(actor.entity.getEyeLocation(),target.getEyeLocation())) {
            actor.melee=tick+30; target.damage(damage(r)*.65,actor.entity);
        }
        move(actor,target.getLocation());
    }
    private static Attack bossAttack(Actor actor) {
        Boss boss=Boss.valueOf(actor.record.theme);
        Attack next=boss.attacks[actor.sequence++%boss.attacks.length];
        return next==Attack.SUMMON && actor.record.summons>=MAX_SUMMONS?Attack.RING:next;
    }
    private static double attackRange(Attack attack) {
        switch(attack) {
            case BOLT: case FROST: case SALVO: return 28;
            case PULL: case SUMMON: return 16;
            case LUNGE: case THRUST: return 10;
            case HOWL: case RING: return 9;
            default: return 6;
        }
    }
    private void windup(Actor actor,Player target,Attack attack,List<Player> players) {
        actor.attack=attack; actor.target=target.getUniqueId(); actor.aim=target.getEyeLocation().clone();
        actor.origin=actor.entity.getLocation().clone();
        actor.resolve=tick+(actor.record.boss?Math.max(25,45-actor.record.phase*5):25);
        announce(actor,players,warning(attack)); telegraph(actor);
    }
    private static String warning(Attack attack) {
        switch(attack) {
            case BOLT: case FROST: case SALVO: return "draws a bead — break sight or sidestep!";
            case LUNGE: case THRUST: case CLEAVE: return "raises its weapon — leave the marked line!";
            case PULL: return "casts its chain — take cover!";
            case QUAKE: return "raises the hammer — jump or retreat!";
            case RING: return "sounds the outer ring — move inside or outside it!";
            case SUMMON: return "calls its buried attendants!";
            case DRAIN: return "reaches for your breath — get away!";
            case GUARD: return "braces its shield — circle behind it!";
            default: return "gathers a pulse — retreat beyond the smoke!";
        }
    }
    private void announce(Actor actor,List<Player> players,String message) {
        String label=actor.record.boss?Boss.valueOf(actor.record.theme).label:Theme.valueOf(actor.record.theme).label;
        for(Player p:players)if(canHit(actor,p) && p.getLocation().distanceSquared(actor.entity.getLocation())<1600) {
            p.sendMessage(ChatColor.DARK_GRAY+label+" "+message);
            p.playSound(actor.entity.getLocation(),Sound.BLOCK_NOTE_BASS,actor.record.boss?.8f:.3f,.6f);
        }
    }
    private void telegraph(Actor actor) {
        if(tick%10!=0)return;
        Attack attack=actor.attack; double radius=attack==Attack.RING?8:attack==Attack.HOWL?8:5;
        if(attack==Attack.BOLT || attack==Attack.FROST || attack==Attack.SALVO || attack==Attack.LUNGE || attack==Attack.THRUST || attack==Attack.CLEAVE) {
            Vector direction=actor.aim.toVector().subtract(actor.origin.toVector()).setY(0);
            if(direction.lengthSquared()<.01)return; direction.normalize();
            for(int i=1;i<=12;i++)particle(actor.origin.clone().add(direction.clone().multiply(i)).add(0,.25,0));
        } else for(int i=0;i<16;i++) {
            double angle=i*Math.PI/8; particle(actor.origin.clone().add(Math.cos(angle)*radius,.2,Math.sin(angle)*radius));
        }
    }
    private static void particle(Location at) { if(loaded(at))at.getWorld().spawnParticle(Particle.SMOKE_NORMAL,at,1,0,0,0,0); }
    private void resolve(Actor actor,List<Player> players) {
        if(!healthy())return;
        Attack attack=actor.attack;
        if(attack==Attack.SUMMON) { summon(actor); return; }
        if(attack==Attack.BOLT || attack==Attack.FROST || attack==Attack.SALVO) {
            Player intended=Bukkit.getPlayer(actor.target);
            if(!canHit(actor,intended) || !lineOfSight(actor.entity.getEyeLocation(),intended.getEyeLocation()))return;
            bolt(actor,0); if(attack==Attack.SALVO) { bolt(actor,-.16); bolt(actor,.16); } return;
        }
        for(Player p:players) {
            if(!canHit(actor,p) || !lineOfSight(actor.entity.getEyeLocation(),p.getEyeLocation()))continue;
            double distance=p.getLocation().distanceSquared(actor.origin); boolean hit;
            switch(attack) {
                case LUNGE: case THRUST: case CLEAVE:
                    hit=inLine(actor.origin,actor.aim,p.getLocation(),attack==Attack.CLEAVE?6:10,attack==Attack.CLEAVE?2.4:1.1); break;
                case RING: hit=distance>=16 && distance<=81; break;
                case QUAKE: hit=distance<=36 && p.isOnGround(); break;
                case HOWL: hit=distance<=64; break;
                case PULL: hit=distance<=256 && p.getUniqueId().equals(actor.target); break;
                default: hit=distance<=25;
            }
            if(hit)p.damage(damage(actor.record)*(attack==Attack.CLEAVE?1.2:1),actor.entity);
        }
    }
    static boolean inLine(Location from,Location aim,Location victim,double length,double width) {
        Vector direction=aim.toVector().subtract(from.toVector()).setY(0);
        if(direction.lengthSquared()<.001)return false;
        direction.normalize(); Vector delta=victim.toVector().subtract(from.toVector());
        double along=delta.getX()*direction.getX()+delta.getZ()*direction.getZ();
        double sideways=Math.abs(delta.getX()*direction.getZ()-delta.getZ()*direction.getX());
        return along>=0 && along<=length && sideways<=width && Math.abs(delta.getY())<3;
    }
    private void bolt(Actor actor,double rotation) {
        if(shots.size()>=PROJECTILE_CAP)return;
        Location from=actor.entity.getEyeLocation(); Vector v=actor.aim.toVector().subtract(from.toVector());
        if(v.lengthSquared()<.01 || !loadedBox(from,2))return;
        v.normalize(); double x=v.getX(),z=v.getZ();
        v.setX(x*Math.cos(rotation)-z*Math.sin(rotation)); v.setZ(x*Math.sin(rotation)+z*Math.cos(rotation)); v.multiply(1.15);
        Arrow arrow=from.getWorld().spawn(from,Arrow.class,a->{
            a.addScoreboardTag(SHOT); a.setShooter(actor.entity); a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            a.setGravity(false); a.setCritical(false); a.setKnockbackStrength(0); a.setVelocity(v);
        });
        if(arrow.isValid())shots.put(arrow.getUniqueId(),new Shot(arrow,actor,tick));
    }
    private void summon(Actor actor) {
        for(int n=0;n<2 && actor.record.summons<MAX_SUMMONS;n++) {
            EncounterJournal.Record r=actor.record; int slot=r.summons;
            // Budget is durable even if spawning is cancelled or the process dies immediately afterwards.
            EncounterJournal.Record next=r.progress(r.phase,slot+1); if(!commit(next))return; actor.record=next;
            Catalog.Profile biome=plugin.at(actor.entity.getWorld(),r.x,r.z);
            String[] pool=themePool(false,family(actor.entity.getWorld(),r),biome.atmosphere,biome.name,"surface");
            EncounterJournal.Record add=new EncounterJournal.Record(r.world,r.site,r.ordinal,slot,r.id,false,
                    pool[(slot+r.tier)%pool.length],r.tier,r.x+(slot%2==0?2:-2),r.y,r.z+(slot<2?2:-2));
            if(commit(add) && capacity(false)) {
                Location safe=safeFloor(new Location(actor.entity.getWorld(),add.x+.5,add.y,add.z+.5),2);
                if(safe!=null)spawn(add,safe,false);
            }
        }
    }
    private void restoreSummons(Actor actor) {
        EncounterJournal.Record parent=actor.record;
        for(int slot=0;slot<parent.summons;slot++) {
            UUID id=EncounterJournal.identity(parent.world,parent.site,parent.ordinal,slot);
            EncounterJournal.Record add=journal.get(id);
            if(add==null) {
                // A crash between budget reservation and child claim consumes that slot; it cannot mint extra summons.
                continue;
            }
            if(!add.terminal() && !active.containsKey(id))recover(actor.entity.getWorld(),add);
        }
    }

    private void move(Actor actor,Location target) {
        Location from=actor.entity.getLocation(); Vector delta=target.toVector().subtract(from.toVector()).setY(0);
        if(delta.lengthSquared()<4)return;
        double speed=actor.record.boss?.65+.15*actor.record.phase:.65*Theme.valueOf(actor.record.theme).speed;
        if(actor.record.theme.equals("OSSUARY_SCUTTLER") || actor.record.theme.equals("GLASS_HUNTSMAN")) {
            Vector tangent=new Vector(-delta.getZ(),0,delta.getX()).normalize().multiply(.65);
            delta.normalize().multiply(.5).add(tangent);
        }
        delta.normalize().multiply(speed); Location next=safeFloor(from.clone().add(delta),1);
        if(next==null || !withinHome(actor.record,next,TETHER) || !loadedBox(next,1))return;
        next.setDirection(target.toVector().subtract(next.toVector()));
        if(checkpoint(actor,next))actor.entity.teleport(next);
    }
    private void returnHome(Actor actor) {
        actor.attack=null;
        Location home=new Location(actor.entity.getWorld(),actor.record.x+.5,actor.record.y,actor.record.z+.5);
        if(!loaded(home)) { freeze(actor.entity); return; }
        if(actor.entity.getLocation().distanceSquared(home)<=1)return;
        Location safe=safeFloor(home,3);
        if(safe!=null && checkpoint(actor,safe))actor.entity.teleport(safe);
    }
    private static Location safeFloor(Location near,int vertical) {
        if(!loaded(near))return null;
        World w=near.getWorld(); int x=near.getBlockX(),z=near.getBlockZ(),base=near.getBlockY();
        for(int d=0;d<=vertical;d++)for(int sign:d==0?new int[]{1}:new int[]{1,-1}) {
            int y=base+d*sign; if(y<2 || y>w.getMaxHeight()-3)continue;
            if(w.getBlockAt(x,y-1,z).getType().isSolid() && w.getBlockAt(x,y,z).getType()==Material.AIR
                    && w.getBlockAt(x,y+1,z).getType()==Material.AIR && w.getBlockAt(x,y+2,z).getType()==Material.AIR)
                return new Location(w,near.getX(),y,near.getZ());
        }
        return null;
    }
    private static Location spawnFloor(Location home) {
        Location center=safeFloor(home,3); if(center!=null)return center;
        // A chest/pillar or one dug-out floor tile must not strand a missing boss permanently.
        // Search only a fixed nine-by-nine room interior, without reading unloaded neighbors.
        for(int radius=1;radius<=4;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            Location safe=safeFloor(home.clone().add(x,0,z),3); if(safe!=null)return safe;
        }
        return null;
    }
    private static boolean loadedBox(Location center,double radius) {
        World w=center.getWorld();
        for(int x=(int)Math.floor(center.getX()-radius)>>4;x<=(int)Math.floor(center.getX()+radius)>>4;x++)
            for(int z=(int)Math.floor(center.getZ()-radius)>>4;z<=(int)Math.floor(center.getZ()+radius)>>4;z++)if(!w.isChunkLoaded(x,z))return false;
        return true;
    }
    private static boolean lineOfSight(Location from,Location to) {
        if(from.getWorld()!=to.getWorld())return false;
        Vector step=to.toVector().subtract(from.toVector()); double length=step.length();
        if(length>RANGE || length<.01)return length<.01;
        step.multiply(.4/length); Location cursor=from.clone();
        for(double distance=0;distance<=length;distance+=.4) {
            if(!loaded(cursor) || cursor.getBlock().getType().isSolid())return false;
            cursor.add(step);
        }
        return loaded(to);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void targeting(EntityTargetLivingEntityEvent event) {
        if(tagged(event.getEntity())==null)return;
        if(event.getTarget()==null)return;
        Actor actor=active.get(event.getEntity().getUniqueId());
        if(!healthy() || actor==null || !(event.getTarget() instanceof Player) || !canHit(actor,(Player)event.getTarget()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(EntityTeleportEvent event) {
        UUID id=tagged(event.getEntity()); if(id==null)return;
        Actor actor=active.get(id); Location to=event.getTo();
        if(!healthy() || actor==null || to==null || !loaded(to) || !withinHome(actor.record,to,TETHER) || !checkpoint(actor,to))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(EntityPortalEvent event) {
        if(tagged(event.getEntity())!=null || event.getEntity().getScoreboardTags().contains(SHOT))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void changeBlock(EntityChangeBlockEvent event) {
        if(tagged(event.getEntity())!=null)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustEvent event) {
        // A cosmetic helmet never burns away or produces replacement equipment drops in daylight.
        if(tagged(event.getEntity())!=null && !(event instanceof EntityCombustByBlockEvent) && !(event instanceof EntityCombustByEntityEvent))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawnReason(CreatureSpawnEvent event) {
        if(tagged(event.getEntity())!=null && event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.CUSTOM)event.setCancelled(true);
    }
    private Actor source(Entity damager) {
        if(damager instanceof Projectile) {
            Object shooter=((Projectile)damager).getShooter();
            if(shooter instanceof Entity)return active.get(((Entity)shooter).getUniqueId());
        }
        return active.get(damager.getUniqueId());
    }
    private static Player playerSource(Entity damager) {
        if(damager instanceof Player)return (Player)damager;
        if(damager instanceof Projectile && ((Projectile)damager).getShooter() instanceof Player)return (Player)((Projectile)damager).getShooter();
        if(damager instanceof TNTPrimed && ((TNTPrimed)damager).getSource() instanceof Player)return (Player)((TNTPrimed)damager).getSource();
        if(damager instanceof Tameable && ((Tameable)damager).getOwner() instanceof Player)return (Player)((Tameable)damager).getOwner();
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damageGuard(EntityDamageEvent event) {
        UUID victim=tagged(event.getEntity());
        if(victim!=null && (!healthy() || journal.get(victim)==null || journal.get(victim).terminal())) { event.setCancelled(true); return; }
        if(victim!=null && !active.containsKey(victim)) {
            if(event.getEntity() instanceof LivingEntity)adopt((LivingEntity)event.getEntity());
            if(!active.containsKey(victim)) { event.setCancelled(true); return; } // Cap-dormant bosses cannot yield free victories.
        }
        if(!(event instanceof EntityDamageByEntityEvent))return;
        EntityDamageByEntityEvent hit=(EntityDamageByEntityEvent)event; Entity damager=hit.getDamager(); Actor actor=source(damager);
        boolean ownedProjectile=damager.getScoreboardTags().contains(SHOT);
        if(actor!=null || tagged(damager)!=null || ownedProjectile) {
            if(!healthy() || actor==null || !(event.getEntity() instanceof Player) || !canHit(actor,(Player)event.getEntity())) {
                event.setCancelled(true); return;
            }
            if(ownedProjectile) {
                Shot shot=shots.get(damager.getUniqueId());
                if(shot==null || !lineOfSight(actor.entity.getEyeLocation(),((Player)event.getEntity()).getEyeLocation())) { event.setCancelled(true); return; }
                hit.setDamage(shot.damage);
            }
        }
        if(victim!=null) {
            Player attacker=playerSource(damager);
            if(attacker!=null && !survivor(attacker)) { event.setCancelled(true); return; }
            Actor guard=active.get(victim);
            if(guard!=null && (guard.record.theme.equals("GRAVE_SENTINEL") || guard.record.theme.equals("OSSUARY_REGENT"))
                    && guard.attack!=null && attacker!=null) {
                Vector facing=guard.entity.getLocation().getDirection().setY(0);
                Vector toward=attacker.getLocation().toVector().subtract(guard.entity.getLocation().toVector()).setY(0);
                if(toward.lengthSquared()>.01 && facing.dot(toward.normalize())>.3)hit.setDamage(hit.getDamage()*.35);
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void confirmedHit(EntityDamageByEntityEvent event) {
        Actor actor=source(event.getDamager()); if(actor==null || !(event.getEntity() instanceof Player))return;
        Player player=(Player)event.getEntity(); Shot shot=shots.get(event.getDamager().getUniqueId());
        Attack attack=shot==null?actor.attack:shot.attack;
        // Defer side effects until all protection listeners have had their cancellation opportunity.
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!healthy() || event.isCancelled() || event.getFinalDamage()<=0 || !canHit(actor,player) || !actor.entity.isValid())return;
            if(attack==Attack.FROST || attack==Attack.MIASMA)player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,60,attack==Attack.FROST?1:0));
            if(attack==Attack.HOWL)player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0));
            if(attack==Attack.DRAIN && !actor.entity.isDead())actor.entity.setHealth(Math.min(health(actor.record),actor.entity.getHealth()+event.getFinalDamage()*.6));
            if(attack==Attack.PULL) {
                Vector pull=actor.entity.getLocation().toVector().subtract(player.getLocation().toVector());
                if(pull.lengthSquared()>.1) { pull.normalize().multiply(.45).setY(.12); if(loadedBox(player.getLocation().clone().add(pull.clone().multiply(4)),1))player.setVelocity(pull); }
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent event) {
        UUID id=tagged(event.getEntity()); if(id==null)return;
        event.getDrops().clear(); event.setDroppedExp(0); // Valuable loot lives only in the locked vault.
        if(journal==null)return;
        EncounterJournal.Record record=journal.get(id);
        if(record==null || !id.equals(event.getEntity().getUniqueId()) || record.terminal())return;
        // Environmental deaths are legitimate defeats; getKiller() is intentionally irrelevant.
        if(commit(record.state(EncounterJournal.State.KILLED))) {
            active.remove(id); missingSince.remove(id); deaths++;
            for(int slot=0;record.boss && slot<MAX_SUMMONS;slot++) {
                EncounterJournal.Record add=journal.get(EncounterJournal.identity(record.world,record.site,record.ordinal,slot));
                if(add!=null && !add.terminal() && commit(add.state(EncounterJournal.State.RETIRED))) {
                    Actor summoned=active.remove(add.id); if(summoned!=null)summoned.entity.remove();
                }
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void noDrops(EntityDeathEvent event) {
        if(tagged(event.getEntity())==null)return;
        event.getDrops().clear(); event.setDroppedExp(0);
        // Vault loot stays locked away, but every custom zombie still drops its rotten flesh.
        EntityType type=event.getEntityType();
        if(type==EntityType.ZOMBIE||type==EntityType.HUSK||type==EntityType.ZOMBIE_VILLAGER)
            event.getDrops().add(new ItemStack(Material.ROTTEN_FLESH,1));
    }
}
