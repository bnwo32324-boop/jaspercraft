package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/** The room, not a player's presence or the chunk cache, owns encounter completion. */
public final class Encounters implements Listener {
    final DungeonPlugin plugin;final RoomStore store;public final Map<String,Run> active=new LinkedHashMap<>();
    private final Map<String,RoomStore> realmStores=new HashMap<>();
    private final Map<UUID,Shot> shots=new HashMap<>();private boolean spawning,restoring;private long ticks;
    private Player impactPlayer;private boolean impactAccepted;
    static final String TAG="jaspr_penitent";
    public static final class Run {
        public final Layout.Room room;public final RoomStore.State state;public final Map<Integer,LivingEntity> mobs=new HashMap<>();
        public final World world;public final String key;
        final Set<UUID> players=new HashSet<>();long lastSeen,windup,nextAttack;Location warning;BossBar bar;double bossMax;
        EncounterCatalog.Pattern pattern;UUID warnedPlayer,warningBoss;List<double[]> marks=Collections.emptyList();
        public EncounterCatalog.Pattern warningPattern(){return pattern;}
        /** Detached legacy fixture only; live activation always supplies its world and realm key. */
        public Run(Layout.Room r,RoomStore.State s){this(null,r,s,r.id());}
        public Run(World world,Layout.Room r,RoomStore.State s,String key){this.world=world;room=r;state=s;this.key=key;}
    }
    static final class Shot {final Projectile entity;final String room;final long birth;Shot(Projectile e,String r,long t){entity=e;room=r;birth=t;}}
    Encounters(DungeonPlugin plugin){this.plugin=plugin;store=new RoomStore(new java.io.File(plugin.getDataFolder(),"rooms"));}
    private RoomStore store(World world){
        if(world.getName().equals(plugin.worldName))return store;
        String name=world.getName();if(!name.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Unsafe realm journal name");
        return realmStores.computeIfAbsent(name,n->new RoomStore(new java.io.File(plugin.getDataFolder(),"rooms-realms/"+n)));
    }
    private boolean save(Run run){try{store(run.world).save(run.room,run.state);return true;}catch(Exception ex){plugin.getLogger().severe("DUNGEON_ROOM_SAVE_FAILED room="+run.key+" "+ex.getMessage());return false;}}
    public Run activate(Layout.Room r){return activate(plugin.ensureWorld(),r);}
    public Run activate(World world,Layout.Room r){
        if(world==null||!plugin.inside(world))return null;
        String key=plugin.roomKey(world,r);Run a=active.get(key);if(a!=null)return a;
        if(active.size()>=Math.max(1,plugin.getConfig().getInt("max-active-rooms",32)))return null;
        try{a=new Run(world,r,store(world).load(r),key);active.put(key,a);if(r.mobCount()==0&&!a.state.cleared){a.state.cleared=true;if(!save(a)){active.remove(key);return null;}}return a;}catch(Exception ex){plugin.getLogger().severe("DUNGEON_ROOM_LOCKED journal="+key+" "+ex.getMessage());return null;}
    }
    public void tick(){
        ticks++;
        // Presence is refreshed every tick; the ten-tick spawn cadence is not an attack authority.
        for(Run a:active.values())a.players.clear();
        for(World world:plugin.dungeonWorlds())for(Player p:world.getPlayers())if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR){
            Layout.Room r=plugin.room(p.getLocation());Run a=ticks%10==0?activate(world,r):active.get(plugin.roomKey(world,r));
            if(ticks%10==0)plugin.announce(p,r);if(a!=null){a.players.add(p.getUniqueId());a.lastSeen=ticks;}
        }
        if(ticks%10==0){
            Iterator<Run> it=active.values().iterator();while(it.hasNext()){
                Run a=it.next();if(a.players.isEmpty()&&ticks-a.lastSeen>20*Math.max(5,plugin.getConfig().getInt("room-sleep-seconds",30))){sleep(a);it.remove();continue;}
                // Treasure and shrine guardians stay dormant until someone opens the room chest.
                if(!a.players.isEmpty()&&!a.state.cleared&&(!a.room.dormant()||a.state.triggered)){if(a.state.killed==((1<<a.room.mobCount())-1))complete(a);else for(int slot=0;slot<a.room.mobCount();slot++)if((a.state.killed&(1<<slot))==0){LivingEntity e=a.mobs.get(slot);if(e==null||!e.isValid())spawn(a,slot);}}
                if(a.bar!=null){a.bar.removeAll();for(UUID id:a.players){Player p=Bukkit.getPlayer(id);if(present(p,a))a.bar.addPlayer(p);}LivingEntity boss=a.mobs.get(0);if(boss!=null&&boss.isValid()&&!boss.isDead())a.bar.setProgress(Math.max(0,Math.min(1,boss.getHealth()/a.bossMax)));}
            }
        }
        for(Run a:active.values()){
            for(Map.Entry<Integer,LivingEntity> mob:a.mobs.entrySet()){
                LivingEntity e=mob.getValue();if(!e.isValid()||e.isDead())continue;
                if(!contained(a,e,e.getLocation())){cancelWarning(a);restoring=true;try{Location to=safe(a.world,a.room,mob.getKey());if(to!=null){if(!e.teleport(to))e.remove();else{e.setFallDistance(0);e.setVelocity(new Vector());}}else e.remove();}finally{restoring=false;}}
                if(e.isValid()&&contained(a,e,e.getLocation())&&e instanceof Creature){Player nearest=null;double distance=Double.MAX_VALUE;for(UUID id:a.players){Player p=Bukkit.getPlayer(id);if(!eligible(p,a))continue;double d=p.getLocation().distanceSquared(e.getLocation());if(d<distance){distance=d;nearest=p;}}((Creature)e).setTarget(nearest);}
            }
            if(a.room.kind==Layout.Kind.BOSS)boss(a);
        }
        Iterator<Shot> shotIt=shots.values().iterator();while(shotIt.hasNext()){Shot s=shotIt.next();if(!s.entity.isValid()){shotIt.remove();continue;}if(ticks-s.birth>200||!plugin.inside(s.entity.getWorld())||!s.room.equals(plugin.roomKey(s.entity.getLocation()))){s.entity.remove();shotIt.remove();}}
        if(ticks%20==0)plugin.relics.tick();
        if(plugin.hazards!=null)plugin.hazards.tick(active);
    }
    Location safe(Layout.Room r,int slot){return safe(plugin.ensureWorld(),r,slot);}
    Location safe(World world,Layout.Room r,int slot){
        if(world==null||!plugin.inside(world)||r.kind==Layout.Kind.REFUGE)return null;
        EncounterCatalog.Species species=EncounterCatalog.entry(r.theme).species(slot,r.motif,r.kind==Layout.Kind.BOSS);
        EncounterCatalog.Blocks blocks=new EncounterCatalog.Blocks(){
            public boolean air(int x,int y,int z){return world.getBlockAt(x,y,z).getType()==Material.AIR;}
            public boolean floor(int x,int y,int z){Block b=world.getBlockAt(x,y,z);Material m=b.getType();return m.isOccluding()&&m!=Material.MAGMA&&m!=Material.CACTUS&&m!=Material.SOUL_SAND;}
        };
        // Try canonical clear-lane positions only. Never carve blocks or spawn inside a chest.
        for(int n=0;n<14;n++){double[] p=EncounterCatalog.spawnPoint(r,slot+n);if(EncounterCatalog.fits(r,species,r.kind==Layout.Kind.BOSS,p[0],p[1],p[2],blocks))return new Location(world,p[0],p[1],p[2]);}
        return null;
    }
    public static EntityType type(Layout.Room r,int slot){
        return EntityType.valueOf(EncounterCatalog.entry(r.theme).species(slot,r.motif,r.kind==Layout.Kind.BOSS).name());
    }
    void spawn(Run a,int slot){
        Location at=safe(a.world,a.room,slot);if(at==null)return;
        LivingEntity created=null;spawning=true;try{
            LivingEntity e=(LivingEntity)a.world.spawnEntity(at,type(a.room,slot));created=e;if(!e.isValid())return;
            e.addScoreboardTag(TAG);e.addScoreboardTag("jpd_room:"+a.key);e.addScoreboardTag("jpd_slot:"+slot);e.setRemoveWhenFarAway(false);e.setCanPickupItems(false);
            boolean boss=a.room.kind==Layout.Kind.BOSS;
            if(e instanceof Zombie)((Zombie)e).setBaby(false);if(e instanceof Slime)((Slime)e).setSize(boss?4:2);
            int tier=EncounterCatalog.threat(a.room.tier);
            double danger=danger(a.world);
            double hp=boss?(90+tier*30)*Math.max(.2,Math.min(5,plugin.getConfig().getDouble("boss-health-multiplier",1)*danger)):(12+tier*5+(slot%3)*3)*danger;
            e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);e.setHealth(hp);
            if(e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)!=null)e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue((boss?5+tier:2+tier*.65)*danger);
            EncounterCatalog.Entry entry=EncounterCatalog.entry(a.room.theme);
            e.setCustomName((boss?ChatColor.DARK_RED:ChatColor.GRAY)+(boss?entry.bossName:entry.themeName+" Penitent"));e.setCustomNameVisible(boss);
            EntityEquipment eq=e.getEquipment();if(eq!=null){eq.setHelmetDropChance(0);eq.setChestplateDropChance(0);eq.setLeggingsDropChance(0);eq.setBootsDropChance(0);eq.setItemInMainHandDropChance(0);eq.setItemInOffHandDropChance(0);}
            a.mobs.put(slot,e);if(boss){cancelWarning(a);a.nextAttack=ticks+entry.cooldown(tier,false);a.bossMax=hp;if(a.bar==null)a.bar=Bukkit.createBossBar(entry.bossName,BarColor.RED,BarStyle.SEGMENTED_10);}
        }catch(RuntimeException ex){if(created!=null){a.mobs.remove(slot,created);created.remove();}plugin.getLogger().warning("DUNGEON_SPAWN_RETRY room="+a.key+" slot="+slot+" "+ex.getMessage());}finally{spawning=false;}
    }
    private boolean present(Player p,Run a){return p!=null&&p.isOnline()&&!p.isDead()&&p.getWorld().equals(a.world)&&plugin.inside(p.getWorld())&&a.key.equals(plugin.roomKey(p.getLocation()));}
    private double danger(World world){return Math.max(1,Math.min(1.45,plugin.dangerMultiplier(world)));}
    private boolean sheltered(Location l){return plugin.sanctuary!=null&&plugin.sanctuary.contains(l);}
    private boolean eligible(Player p,Run a){return present(p,a)&&!sheltered(p.getLocation())&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean contained(Run a,LivingEntity e,Location l){return l!=null&&l.getWorld()!=null&&l.getWorld().equals(a.world)&&plugin.inside(l.getWorld())&&EncounterCatalog.bodyInside(a.room,EncounterCatalog.Species.valueOf(e.getType().name()),a.room.kind==Layout.Kind.BOSS,l.getX(),l.getY(),l.getZ());}
    void cancelWarning(Run a){a.warning=null;a.pattern=null;a.warnedPlayer=null;a.warningBoss=null;a.windup=0;a.marks=Collections.emptyList();a.nextAttack=ticks+EncounterCatalog.entry(a.room.theme).cooldown(a.room.tier,false);}

    /** Also used by the test-only runtime audit; the live tick uses this exact warning path. */
    void beginWarning(Run a,Player target){
        LivingEntity caster=a.mobs.get(0);if(a.state.cleared||caster==null||!caster.isValid()||caster.isDead()||!eligible(target,a)||!contained(a,caster,caster.getLocation()))return;
        Location b=caster.getLocation(),p=target.getLocation();a.pattern=EncounterCatalog.pattern(a.room.theme,b.getX(),b.getZ(),p.getX(),p.getZ());
        a.warnedPlayer=target.getUniqueId();a.warningBoss=caster.getUniqueId();a.warning=new Location(a.world,a.pattern.x,Layout.FLOOR+1,a.pattern.z);
        a.windup=ticks+a.pattern.entry.windup(a.room.tier);a.marks=a.pattern.markers(a.room);
        if(a.marks.isEmpty()){cancelWarning(a);return;}
        announcePhase(a);
        showWarning(a);
    }
    private void announcePhase(Run a){for(Player viewer:a.world.getPlayers())if(present(viewer,a)){viewer.sendMessage(ChatColor.RED+a.pattern.entry.bossName+": "+a.pattern.entry.phaseCue(a.pattern.phase));viewer.playSound(a.warning,Sound.BLOCK_NOTE_BASS,.7f,.6f+a.pattern.phase*.2f);}}
    private void showWarning(Run a){for(Player viewer:a.world.getPlayers())if(present(viewer,a))for(double[] p:a.marks)viewer.spawnParticle(Particle.SPELL_WITCH,p[0],Layout.FLOOR+1.15,p[1],1,0,0,0,0);}
    void boss(Run a){
        LivingEntity caster=a.mobs.get(0);
        if(a.state.cleared||caster==null||!caster.isValid()||caster.isDead()||!contained(a,caster,caster.getLocation())){cancelWarning(a);return;}
        if(a.pattern!=null&&(!caster.getUniqueId().equals(a.warningBoss)||!eligible(Bukkit.getPlayer(a.warnedPlayer),a))){cancelWarning(a);return;}
        if(a.pattern==null){
            if(ticks<a.nextAttack)return;
            Player target=null;double nearest=Double.MAX_VALUE;
            for(Player p:a.world.getPlayers())if(eligible(p,a)){double distance=p.getLocation().distanceSquared(caster.getLocation());if(distance<nearest){nearest=distance;target=p;}}
            if(target!=null)beginWarning(a,target);return;
        }
        if(ticks%4==0)showWarning(a);
        if(ticks<a.windup)return;
        EncounterCatalog.Pattern impact=a.pattern;EncounterCatalog.Entry ability=impact.entry;
        for(Player p:a.world.getPlayers()){
            if(!eligible(p,a))continue;Location at=p.getLocation();
            if(!impact.hits(a.room,at.getX(),at.getY(),at.getZ()))continue;
            impactPlayer=p;impactAccepted=false;try{p.damage(ability.damage(a.room.tier)*danger(a.world),caster);}finally{impactPlayer=null;}
            // Damage callbacks can move or kill a player; recheck before applying a status.
            if(impactAccepted&&eligible(p,a)&&ability.status!=EncounterCatalog.Status.NONE)p.addPotionEffect(new PotionEffect(PotionEffectType.getByName(ability.status.name()),ability.statusDuration(a.room.tier),0));
            if(a.pattern!=impact)return;
        }
        for(Player viewer:a.world.getPlayers())if(present(viewer,a))viewer.playSound(a.warning,Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,.6f,.8f);
        if(!caster.isValid()||caster.isDead()||!eligible(Bukkit.getPlayer(a.warnedPlayer),a)){cancelWarning(a);return;}
        if(a.pattern.phase+1<ability.pulses){a.pattern=a.pattern.next();a.marks=a.pattern.markers(a.room);a.windup=ticks+ability.pulseDelay(a.room.tier);announcePhase(a);showWarning(a);}
        else{cancelWarning(a);a.nextAttack=ticks+ability.cooldown(a.room.tier,caster.getHealth()<a.bossMax*.5);}
    }
    private Run owner(Entity e){String room=taggedRoom(e);return room==null?null:active.get(room);}
    private String taggedRoom(Entity e){if(e!=null)for(String tag:e.getScoreboardTags())if(tag.startsWith("jpd_room:"))return tag.substring(9);return null;}
    private Entity shooter(Entity e){if(e instanceof Projectile){ProjectileSource source=((Projectile)e).getShooter();if(source instanceof Entity)return (Entity)source;}return e;}
    private String origin(Entity e){Shot shot=shots.get(e.getUniqueId());if(shot!=null)return shot.room;Entity source=shooter(e);String tagged=taggedRoom(source);return tagged!=null?tagged:plugin.inside(source.getWorld())?plugin.roomKey(source.getLocation()):null;}
    private boolean protectedMode(Entity e){return e instanceof Player&&(((Player)e).getGameMode()==GameMode.CREATIVE||((Player)e).getGameMode()==GameMode.SPECTATOR);}
    private boolean canHarm(Entity raw,Entity victim){
        Entity source=shooter(raw);String room=origin(raw);
        if(refuge(raw)||refuge(source)||refuge(victim))return false;
        String victimRoom=taggedRoom(victim);
        if(victimRoom!=null&&(!plugin.inside(victim.getWorld())||!victimRoom.equals(plugin.roomKey(victim.getLocation()))))return false;
        if(source instanceof Player&&((Player)source).getGameMode()==GameMode.SPECTATOR&&(plugin.inside(source.getWorld())||plugin.inside(victim.getWorld())))return false;
        if(room!=null){
            if(protectedMode(victim)||!plugin.inside(victim.getWorld())||!room.equals(plugin.roomKey(victim.getLocation())))return false;
            if(!plugin.inside(raw.getWorld())||!room.equals(plugin.roomKey(raw.getLocation())))return false;
            if(!plugin.inside(source.getWorld())||!room.equals(plugin.roomKey(source.getLocation())))return false;
            if(taggedRoom(source)!=null&&(source.isDead()||!source.isValid()))return false;
        }
        if(plugin.inside(victim.getWorld())||plugin.inside(source.getWorld()))return !protectedMode(victim)&&source.getWorld().equals(victim.getWorld())&&plugin.roomKey(source.getLocation()).equals(plugin.roomKey(victim.getLocation()));
        return true;
    }
    /** Only the arrival circle is damage-free; the rest of the refuge parcel has its own dangers now. */
    private boolean refuge(Entity entity){return entity!=null&&sheltered(entity.getLocation());}
    /** Weapon preflight only: callers must still use victim.damage(amount, player) for Bukkit policy. */
    public boolean canTargetInRoom(Player player,LivingEntity victim){return player!=null&&victim!=null&&player!=victim&&player.isOnline()&&!player.isDead()&&!victim.isDead()&&victim.isValid()&&plugin.inside(player.getWorld())&&plugin.inside(victim.getWorld())&&canHarm(player,victim);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void natural(CreatureSpawnEvent e){if(plugin.inside(e.getLocation().getWorld())&&!spawning&&!(e.getEntity() instanceof ArmorStand))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void split(SlimeSplitEvent e){if(plugin.inside(e.getEntity().getWorld()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&!canHarm(e.getEntity(),e.getTarget()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(EntityTeleportEvent e){
        if(restoring||e.getEntity() instanceof Player)return;
        Run a=owner(e.getEntity());
        // Stationary shulkers and endermen cannot reroll into a coffin, ceiling, or another room.
        if(a!=null&&(e.getEntity() instanceof Enderman||e.getEntity() instanceof Shulker||!(e.getEntity() instanceof LivingEntity)||!contained(a,(LivingEntity)e.getEntity(),e.getTo()))){e.setCancelled(true);return;}
        if(plugin.inside(e.getFrom().getWorld())&&(e.getTo()==null||!e.getFrom().getWorld().equals(e.getTo().getWorld())||!plugin.inside(e.getTo().getWorld())||!plugin.roomKey(e.getFrom()).equals(plugin.roomKey(e.getTo()))||!plugin.room(e.getFrom()).inner(e.getTo().getX(),e.getTo().getZ())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){
        if(!canHarm(e.getDamager(),e.getEntity()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void impactResult(EntityDamageByEntityEvent e){if(e.getEntity()==impactPlayer)impactAccepted=!e.isCancelled()&&e.getFinalDamage()>0;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){if(origin(e.getPotion())!=null)for(LivingEntity entity:e.getAffectedEntities())if(!canHarm(e.getPotion(),entity))e.setIntensity(entity,0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){
        if(!plugin.inside(e.getEntity().getWorld()))return;
        String room=plugin.roomKey(e.getEntity().getLocation());ProjectileSource source=e.getEntity().getSource();
        e.getAffectedEntities().removeIf(entity->refuge(entity)||refuge(e.getEntity())||protectedMode(entity)||!entity.getWorld().equals(e.getEntity().getWorld())||!plugin.inside(entity.getWorld())||!room.equals(plugin.roomKey(entity.getLocation()))||(source instanceof Entity&&!canHarm((Entity)source,entity)));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustByEntityEvent e){if(!canHarm(e.getCombuster(),e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(e.getIgnitingEntity()!=null&&taggedRoom(shooter(e.getIgnitingEntity()))!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void shot(ProjectileLaunchEvent e){if(plugin.inside(e.getEntity().getWorld())){String room=origin(e.getEntity());if(refuge(e.getEntity())||room==null||!room.equals(plugin.roomKey(e.getEntity().getLocation()))){e.setCancelled(true);return;}shots.put(e.getEntity().getUniqueId(),new Shot(e.getEntity(),room,ticks));}}
    @EventHandler(priority=EventPriority.HIGHEST) public void died(EntityDeathEvent e){
        if(!e.getEntity().getScoreboardTags().contains(TAG))return;e.getDrops().clear();e.setDroppedExp(0);Run a=owner(e.getEntity());if(a==null)return;
        int slot=-1;for(Map.Entry<Integer,LivingEntity> m:a.mobs.entrySet())if(m.getValue().getUniqueId().equals(e.getEntity().getUniqueId())){slot=m.getKey();break;}if(slot<0)return;
        if(slot==0&&a.room.kind==Layout.Kind.BOSS)cancelWarning(a);a.mobs.remove(slot);int before=a.state.killed;a.state.killed|=1<<slot;
        if(!save(a)){a.state.killed=before;return;}
        if(a.state.killed==((1<<a.room.mobCount())-1))complete(a);
    }
    private void complete(Run a){cancelWarning(a);if(!a.state.cleared){a.state.cleared=true;if(!save(a)){a.state.cleared=false;return;}if(a.bar!=null){a.bar.removeAll();a.bar=null;}String stilled=HazardCatalog.seizingNames(a.room);
            for(UUID id:a.players){Player p=Bukkit.getPlayer(id);if(present(p,a)){p.sendTitle(ChatColor.GOLD+"Room absolved",ChatColor.GRAY+"The reliquary chest is unsealed",5,40,10);if(!stilled.isEmpty())p.sendMessage(ChatColor.GRAY+"The room's "+stilled+" falls still.");plugin.relics.onClear(p);p.giveExp(a.room.tier*5);}}
            plugin.getLogger().info("DUNGEON_ROOM_CLEARED id="+a.key+" threat="+a.room.tier);
        }
    }
    // ---------------------------------------------------------------- owner 2026-10-03: every chest opens like a normal chest
    private static boolean reliquary(Layout.Room r,Block b){return b.getX()==r.cx()&&b.getY()==65&&b.getZ()==r.cz()+4;}
    private static Block chestOf(Run a){return a.world.getBlockAt(a.room.cx(),65,a.room.cz()+4);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chest(PlayerInteractEvent e){
        Block block=e.getClickedBlock();
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||block==null||block.getType()!=Material.CHEST||!plugin.inside(block.getWorld()))return;
        Layout.Room r=plugin.room(block.getLocation());if(!reliquary(r,block))return;
        // Opening a chest never also uses the held item: an armory gun would otherwise fire as the lid opens.
        e.setUseItemInHand(Event.Result.DENY);
        Player p=e.getPlayer();String key=plugin.roomKey(block.getWorld(),r);
        // Reaching across a doorway never opens, fills or wakes another room's reliquary.
        if(!plugin.inside(p.getWorld())||!plugin.roomKey(p.getLocation()).equals(key)){e.setCancelled(true);return;}
        Run open=active.get(key);boolean plain=r.kind!=Layout.Kind.REFUGE&&open!=null&&open.state.claimed;
        // Off-hand repeats and spectators may only look into an already opened chest; they never fill or wake one.
        if(e.getHand()!=EquipmentSlot.HAND||p.getGameMode()==GameMode.SPECTATOR){if(!plain)e.setCancelled(true);return;}
        if(r.kind==Layout.Kind.REFUGE){e.setCancelled(true);candle(p,r);return;}
        Run a=activate(block.getWorld(),r);if(a==null){e.setCancelled(true);p.sendMessage("The reliquary is temporarily unavailable.");return;}
        if(a.state.claimed)return;
        if(!a.state.cleared){e.setCancelled(true);
            if(a.room.dormant()&&!a.state.triggered)wake(a,p);
            else{p.playSound(block.getLocation(),Sound.BLOCK_CHEST_LOCKED,.8f,1);p.sendMessage(ChatColor.RED+"The reliquary is sealed until this room's enemies are defeated.");}
            return;
        }
        // Filled: leave the event alone so the vanilla chest window opens with the loot inside.
        if(!claim(p,a))e.setCancelled(true);
    }
    /** Treasure rooms and shrines: the sealed chest is the lure. tick() spawns the guardians once this is saved. */
    private void wake(Run a,Player opener){
        a.state.triggered=true;if(!save(a)){a.state.triggered=false;opener.sendMessage("The reliquary is temporarily unavailable.");return;}
        Location at=chestOf(a).getLocation().add(.5,.5,.5);a.lastSeen=ticks;
        for(Player viewer:a.world.getPlayers())if(present(viewer,a)){
            viewer.sendTitle(ChatColor.DARK_RED+"The guardians wake",ChatColor.GRAY+"Defeat them to open the reliquary",5,50,12);viewer.playSound(at,Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_SUMMON,1,.6f);
            viewer.sendMessage(ChatColor.RED+"The reliquary was a lure: "+a.room.mobCount()+" guardians of the "+EncounterCatalog.entry(a.room.theme).themeName+" wake. Defeat them to open it.");
        }
        plugin.getLogger().info("DUNGEON_ROOM_AMBUSH id="+a.key+" kind="+a.room.kind+" guardians="+a.room.mobCount());
    }
    /** Fills this room's chest once (the chest handler and the test probe share it); true only when this call filled it. */
    public boolean claim(Player p,Run a){
        if(a==null||a.world==null||!present(p,a))return false;
        if(!a.state.cleared){p.sendMessage(ChatColor.RED+"The reliquary is sealed. Defeat this room's remaining enemies.");return false;}if(a.state.claimed){p.sendMessage(ChatColor.GRAY+"This room's reliquary has already been opened.");return false;}
        Block block=chestOf(a);if(block.getType()!=Material.CHEST){p.sendMessage("The reliquary chest is missing; nothing was claimed.");plugin.getLogger().warning("DUNGEON_RELIQUARY_MISSING room="+a.key);return false;}
        List<ItemStack> items=Rewards.roll(a.room,plugin.rewardMultiplier(a.world));ItemStack relic=Relics.roll(a.room);if(relic!=null)items.add(relic);
        // Reserve durably before filling: repeated clicks, reloads and restarts never refill a chest.
        a.state.claimed=true;if(!save(a)){a.state.claimed=false;p.sendMessage("Reward storage is unavailable; nothing was claimed.");return false;}
        int stacks=items.size();
        try{fill(block,a.room,items);}catch(RuntimeException ex){
            // The reward is already reserved: hand over whatever did not reach the chest rather than lose it.
            plugin.getLogger().severe("DUNGEON_RELIQUARY_FILL_FAILED room="+a.key+" undelivered="+items.size()+" "+ex.getMessage());
            for(ItemStack i:items)for(ItemStack extra:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),extra);
        }
        p.playSound(block.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.5f,1.2f);
        p.sendMessage(ChatColor.GOLD+"The reliquary opens. Threat "+a.room.tier+" reward inside - take what you need"+(relic!=null?". A dungeon relic was among it!":"."));
        plugin.getLogger().info("DUNGEON_RELIQUARY_FILLED id="+a.key+" stacks="+stacks+" relic="+(relic!=null));return true;
    }
    /** Scatters the loot over the chest's empty slots like a found chest; overflow merges, then rests on the lid. Placed items leave the list. */
    private void fill(Block block,Layout.Room r,List<ItemStack> items){
        Inventory inv=((org.bukkit.block.Chest)block.getState()).getBlockInventory();List<Integer> empty=new ArrayList<>();
        for(int s=0;s<inv.getSize();s++){ItemStack i=inv.getItem(s);if(i==null||i.getType()==Material.AIR)empty.add(s);}
        Collections.shuffle(empty,new Random(r.hash^0x52656c6971756172L));int next=0;Location lid=block.getLocation().add(.5,1.1,.5);
        while(!items.isEmpty()){ItemStack item=items.get(0);
            if(item!=null&&item.getType()!=Material.AIR){if(next<empty.size())inv.setItem(empty.get(next++),item);else for(ItemStack extra:inv.addItem(item).values())block.getWorld().dropItem(lid,extra);}
            items.remove(0);
        }
    }
    /** The Last Candle's chest: a fresh offering per player and per opening. It keeps nothing a player puts in. */
    public static final class Candle implements InventoryHolder {
        final UUID player;final Inventory inv;ItemStack pouch,book;
        Candle(Player p){player=p.getUniqueId();inv=Bukkit.createInventory(this,27,"The Last Candle");}
        public Inventory getInventory(){return inv;}
    }
    private void candle(Player p,Layout.Room r){
        Candle c=new Candle(p);c.book=Rewards.lore(r.theme);c.inv.setItem(11,c.book.clone());
        if(plugin.relics.pouchSlot(p)<0){c.pouch=Relics.createPouch();c.inv.setItem(13,c.pouch.clone());}
        p.openInventory(c.inv);p.playSound(p.getLocation(),Sound.BLOCK_CHEST_OPEN,.6f,1);
        p.sendMessage(ChatColor.GOLD+"Welcome to The Dungeon Dimension. The House of Mercy grew from fear, guilt and prayers without answers. Carry its relics beyond these walls. Enemies cannot cross a threshold. The stone gate behind you leads home.");
        p.sendMessage(ChatColor.GRAY+"Only the candle-lit circle around the gate is safe. Every other room has its own dangers.");
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void candleClosed(InventoryCloseEvent e){
        if(!(e.getInventory().getHolder() instanceof Candle)||!(e.getPlayer() instanceof Player))return;
        Candle c=(Candle)e.getInventory().getHolder();Player p=(Player)e.getPlayer();int pouches=c.pouch==null?0:1,books=1;List<ItemStack> back=new ArrayList<>();
        for(ItemStack raw:c.inv.getContents()){
            if(raw==null||raw.getType()==Material.AIR)continue;ItemStack item=raw.clone();
            // Only the untaken offering stays behind; everything else goes back to the player.
            if(pouches>0&&Relics.pouch(item)&&item.isSimilar(c.pouch)){pouches--;continue;}
            if(books>0&&item.getType()==Material.WRITTEN_BOOK&&item.isSimilar(c.book)){int keep=Math.min(books,item.getAmount());books-=keep;if(item.getAmount()==keep)continue;item.setAmount(item.getAmount()-keep);}
            back.add(item);
        }
        c.inv.clear();
        // A dying player's inventory is about to be cleared; their items fall where they stand instead.
        for(ItemStack item:back){if(p.isDead()){p.getWorld().dropItemNaturally(p.getLocation(),item);continue;}for(ItemStack extra:p.getInventory().addItem(item).values())p.getWorld().dropItemNaturally(p.getLocation(),extra);}
        if(!back.isEmpty())p.sendMessage(ChatColor.GRAY+"The Last Candle keeps nothing. Your items were returned.");
    }
    @EventHandler(priority=EventPriority.MONITOR) public void chunkLoad(ChunkLoadEvent e){if(plugin.inside(e.getWorld()))for(Entity entity:e.getChunk().getEntities())if(entity.getScoreboardTags().contains(TAG)){Run a=owner(entity);if(a!=null)cancelWarning(a);entity.remove();}}
    void sleep(Run a){cancelWarning(a);for(LivingEntity e:a.mobs.values())if(e.isValid())e.remove();a.mobs.clear();if(a.bar!=null){a.bar.removeAll();a.bar=null;}Iterator<Shot> it=shots.values().iterator();while(it.hasNext()){Shot s=it.next();if(s.room.equals(a.key)){s.entity.remove();it.remove();}}if(plugin.hazards!=null)plugin.hazards.sleep(a.key);}
    public void close(){
        // Return anything left in an open Last Candle chest before the plugin unloads. Bukkit no longer delivers events to a
        // plugin inside onDisable, so the return runs directly; the close that follows then finds the chest already empty.
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Candle){candleClosed(new InventoryCloseEvent(p.getOpenInventory()));p.closeInventory();}
        for(Run a:active.values())sleep(a);active.clear();for(Shot s:shots.values())s.entity.remove();shots.clear();if(plugin.hazards!=null)plugin.hazards.close();
    }
}
