package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftZombie;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import net.minecraft.server.v1_12_R1.EntityZombie;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.MinecraftServer;

public final class SiegeDirector implements Listener {
    private static final String TAG = "jaspr_undead_";
    private final ApocalypsePlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Hunter> hunters = new LinkedHashMap<>();
    private final Map<UUID, Long> lastMoon = new HashMap<>();
    private final Map<UUID, Boolean> lastDayPhase = new HashMap<>();
    // Daylight weakness: marked monsters deal and endure half while the sun is up.
    private static final String DAY_WEAKNESS = "jaspr-day-weakened";
    // Day sluggishness: marked monsters move slower while standing in direct sunlight.
    private static final String DAY_SLUGGISH = "jaspr-day-sluggish";
    private BukkitTask task;
    private long pass, broken, blasts, spawned, visibilityRepairs;
    private int sunSlowedCount;
    private int budget;
    private String spawnVariant;
    private double range;
    private int cap;
    private SiegeAwareness awareness;
    private SiegeTraversal traversal;
    private SiegeTraversal.Budget work=new SiegeTraversal.Budget(0,0);
    private long ticks;
    private int cursor;
    private List<Player> players=new ArrayList<>();
    private static final class Hunter {
        final Zombie zombie; final String kind;
        final SiegeAwareness.Memory memory=new SiegeAwareness.Memory();
        final SiegeTraversal.State traversal=new SiegeTraversal.State();
        final PathfinderGoalSelector originalGoals, originalTargets;
        Location last; Block digging; int strikes, stalled, fuse, emptyPasses; long lastPower, lastAttack;
        Hunter(Zombie z, String k) {
            zombie=z; kind=k; last=z.getLocation();
            SiegeTraversal.restoreCount(z,traversal);
            EntityZombie handle=((CraftZombie)z).getHandle();
            originalGoals=handle.goalSelector; originalTargets=handle.targetSelector;
            handle.getNavigation().p();
            // Vanilla follow-range path caches can request unloaded chunks. Managed hunters use bounded steering.
            handle.goalSelector=new PathfinderGoalSelector(handle.world.methodProfiler);
            handle.targetSelector=new PathfinderGoalSelector(handle.world.methodProfiler);
        }
        void restore() {
            zombie.setTarget(null); SiegeTraversal.halt(zombie);
            EntityZombie handle=((CraftZombie)zombie).getHandle();
            handle.goalSelector=originalGoals; handle.targetSelector=originalTargets;
        }
    }
    public SiegeDirector(ApocalypsePlugin plugin) { this.plugin=plugin; }
    public void start() {
        if(task!=null)return;
        awareness=new SiegeAwareness(plugin.getConfig(),p->plugin.enabledWorld(p.getWorld())&&plugin.huntable(p));
        traversal=new SiegeTraversal(plugin.getConfig()); range=awareness.range();
        cap = Math.max(12, Math.min(120, plugin.getConfig().getInt("siege.max-active-zombies",80)));
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getPluginManager().registerEvents(awareness,plugin);
        for (World w : Bukkit.getWorlds()) if(plugin.enabledWorld(w)) for(Chunk c:w.getLoadedChunks()) track(c);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,1);
    }
    public void stop() {
        if(task!=null)task.cancel(); task=null;
        for(Hunter h:hunters.values())if(h.zombie.isValid())h.restore();
        hunters.clear(); players.clear(); lastMoon.clear(); lastDayPhase.clear(); HandlerList.unregisterAll(this);
        if(awareness!=null) { awareness.clear(); HandlerList.unregisterAll(awareness); }
    }
    public void noise(Player source,Location position,double radius) {
        if(awareness!=null && Bukkit.isPrimaryThread())awareness.noise(source,position,radius);
    }
    public int activeCount(){return hunters.size();}
    public String metrics(){return "spawned="+spawned+" blocksBreached="+broken+" tntBlasts="+blasts
        +" pillars="+(traversal==null?0:traversal.pillars)+" wallSteps="+(traversal==null?0:traversal.wallSteps)
        +" visibilityRepairs="+visibilityRepairs+" sunSlowed="+sunSlowedCount+" signals="+(awareness==null?0:awareness.signalCount());}
    private String kind(Zombie z) {
        for(String t:z.getScoreboardTags())if(t.startsWith(TAG))return t.substring(TAG.length());
        return "";
    }
    private static boolean siegeType(EntityType type) {
        return type==EntityType.ZOMBIE || type==EntityType.HUSK || type==EntityType.ZOMBIE_VILLAGER;
    }
    private boolean ownedElsewhere(Zombie z) {
        if(z.getScoreboardTags().contains("jaspr_vanilla_undead"))return true;
        for(String tag:z.getScoreboardTags())if(tag.startsWith("jaspr_encounter_v1:"))return true;
        return z.getCustomName()!=null&&kind(z).isEmpty();
    }
    private void track(Chunk chunk) {
        if(!plugin.enabledWorld(chunk.getWorld()))return;
        for(Entity e:chunk.getEntities()) if(siegeType(e.getType()) && hunters.size()<cap) {
            Zombie z=(Zombie)e; String k=kind(z);
            if(hunters.containsKey(z.getUniqueId()) || ownedElsewhere(z))continue;
            if(!k.isEmpty()) { if(k.equals("climber"))z.addScoreboardTag(SiegeTraversal.CLIMBER_TAG); z.setGlowing(false);z.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(16); hunters.put(z.getUniqueId(),new Hunter(z,k)); }
            else if(z.getCustomName()==null&&!z.getScoreboardTags().contains("jaspr_vanilla_undead"))decorate(z,SiegeRules.variant(random.nextInt(100),moon(z.getWorld())));
        }
    }
    @EventHandler public void loaded(ChunkLoadEvent e){track(e.getChunk());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unloaded(ChunkUnloadEvent e){
        for(Entity entity:e.getChunk().getEntities()) { Hunter h=hunters.remove(entity.getUniqueId()); if(h!=null)h.restore(); }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onSpawn(CreatureSpawnEvent event){
        if(!plugin.enabledWorld(event.getLocation().getWorld()))return;
        if(siegeType(event.getEntityType())){
            if(ownedElsewhere((Zombie)event.getEntity()))return;
            // Spawner farms keep their vanilla zombies and cannot farm rare relic variants.
            if(event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL && spawnVariant==null){
                event.getEntity().addScoreboardTag("jaspr_vanilla_undead");return;
            }
            if(hunters.size()>=cap){event.setCancelled(true);return;}
            Zombie z=(Zombie)event.getEntity();
            decorate(z,spawnVariant!=null?spawnVariant:SiegeRules.variant(random.nextInt(100),moon(z.getWorld())));
        } else if(event.getSpawnReason()==CreatureSpawnEvent.SpawnReason.NATURAL
            && (event.getEntityType()==EntityType.SKELETON || event.getEntityType()==EntityType.CREEPER || event.getEntityType()==EntityType.SPIDER)
            && random.nextInt(100)<65 && hunters.size()<cap){
            Location at=event.getLocation().clone();event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin,()->{
                if(at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))spawn(at,SiegeRules.variant(random.nextInt(100),moon(at.getWorld())));
            });
        }
    }
    public Zombie spawn(Location at,String type){
        if(hunters.size()>=cap || !loaded(at) || !plugin.enabledWorld(at.getWorld()))return null;
        spawnVariant=type;
        try{
            Zombie z=(Zombie)at.getWorld().spawnEntity(at,EntityType.ZOMBIE);
            if(!z.isValid()){hunters.remove(z.getUniqueId());return null;}
            spawned++;return z;
        }finally{spawnVariant=null;}
    }
    private void decorate(Zombie z,String k){
        z.addScoreboardTag(TAG+k);z.setBaby(false);z.setCanPickupItems(false);z.setRemoveWhenFarAway(true);z.setGlowing(false);
        ((CraftZombie)z).getHandle().setInvisible(false);
        double hp=28,speed=.23,damage=2;
        String name="Ash Shambler";
        if(k.equals("runner")){hp=24;speed=.33;name="Feral Runner";}
        if(k.equals("climber")){hp=26;speed=.26;name="Wall Crawler";z.addScoreboardTag(SiegeTraversal.CLIMBER_TAG);}
        if(k.equals("brute")){hp=70;speed=.20;damage=4;name="Concrete Breacher";}
        if(k.equals("tnt")){hp=36;speed=.25;name="TNT Carrier";z.getEquipment().setHelmet(new ItemStack(Material.TNT));}
        if(k.equals("revenant")){hp=60;speed=.26;damage=3;name="Hollow Revenant";}
        if(k.equals("warden")){hp=180;speed=.22;damage=5;name="Grave Warden";z.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(.7);}
        z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);z.setHealth(hp);
        z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        z.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(16); // Long sight is handled by awareness, never a native chunk-cache radius.
        z.getAttribute(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS).setBaseValue(0);
        z.setCustomName((k.equals("tnt")?ChatColor.RED:k.equals("warden")?ChatColor.DARK_PURPLE:ChatColor.GRAY)+name);
        z.setCustomNameVisible(false);
        if(!k.equals("tnt"))z.getEquipment().setHelmet(leather(Material.LEATHER_HELMET,k));
        z.getEquipment().setChestplate(leather(Material.LEATHER_CHESTPLATE,k));
        z.getEquipment().setHelmetDropChance(0);z.getEquipment().setChestplateDropChance(0);
        z.getEquipment().setItemInMainHand(new ItemStack(k.equals("brute")?Material.STONE_PICKAXE:Material.AIR));
        z.getEquipment().setItemInMainHandDropChance(0);
        hunters.put(z.getUniqueId(),new Hunter(z,k));
        // Day-spawned hunters start weakened immediately; the periodic pass keeps them in sync after.
        if(isDaytime(z.getWorld().getTime())){
            setDayScaled(z,true);
        }
    }
    private ItemStack leather(Material type,String k){
        ItemStack i=new ItemStack(type);LeatherArmorMeta m=(LeatherArmorMeta)i.getItemMeta();
        m.setColor(k.equals("climber")?Color.fromRGB(38,106,108):k.equals("revenant")||k.equals("warden")?Color.fromRGB(46,29,60):k.equals("runner")?Color.fromRGB(95,35,28):Color.fromRGB(65,68,48));
        i.setItemMeta(m);return i;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){
        Hunter h=hunters.get(e.getEntity().getUniqueId());
        if(h==null || e.getTarget()==null)return;
        if(!(e.getTarget() instanceof Player) || !plugin.huntable((Player)e.getTarget())
                || h.memory.visible!=e.getTarget())e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void combust(EntityCombustEvent e){
        // Daylight burning is disabled entirely: sunlight never sets mobs alight.
        // Fire blocks, lava and flame weapons (ByBlock/ByEntity) still burn as usual.
        if(!(e.getEntity() instanceof LivingEntity)||e.getEntity() instanceof Player)return;
        if(!plugin.enabledWorld(e.getEntity().getWorld()))return;
        if(!(e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent)
            && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){
        Hunter h=hunters.get(e.getDamager().getUniqueId());
        if(h==null || !(e.getEntity() instanceof Player))return;
        Player p=(Player)e.getEntity();
        if(!plugin.huntable(p)){e.setCancelled(true);return;}
        if(h.kind.equals("revenant") || h.kind.equals("warden"))p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,60,0));
    }
    @EventHandler public void death(EntityDeathEvent e){
        Hunter h=hunters.remove(e.getEntity().getUniqueId());if(h==null)return;
        // Every custom zombie drops rotten flesh, no matter the killer or the luck of the vanilla roll.
        ensureFlesh(e.getDrops());
        if(e.getEntity().getKiller()==null || !plugin.isSurvivor(e.getEntity().getKiller()))return;
        if(h.kind.equals("warden")){e.getDrops().add(ApocalypseItems.relic(2));e.getDrops().add(ApocalypseItems.scrap(5));e.setDroppedExp(80);}
        else if(h.kind.equals("revenant") && random.nextInt(3)==0)e.getDrops().add(ApocalypseItems.relic(1));
        else if(random.nextInt(5)==0)e.getDrops().add(ApocalypseItems.scrap(1+random.nextInt(2)));
        salvageDrop(e);
    }

    /**
     * Now and then a corpse is still carrying what killed it. Deliberately long odds:
     * this is meant to be the thing somebody tells the server about, not a farm. Both
     * rates are config-driven so they can be tuned without a rebuild, and only
     * salvage-grade weapons can ever appear this way.
     */
    private void salvageDrop(EntityDeathEvent e){
        int gunOdds=Math.max(1,plugin.getConfig().getInt("siege.salvage-gun-odds",500));
        int meleeOdds=Math.max(1,plugin.getConfig().getInt("siege.salvage-melee-odds",180));
        String id=null;
        if(random.nextInt(gunOdds)==0)id=Arsenal.SALVAGE_GUNS.get(random.nextInt(Arsenal.SALVAGE_GUNS.size()));
        else if(random.nextInt(meleeOdds)==0)id=Arsenal.SALVAGE_MELEE.get(random.nextInt(Arsenal.SALVAGE_MELEE.size()));
        if(id==null)return;
        try{
            e.getDrops().add(ApocalypseItems.gear(id));
            plugin.getLogger().info("SALVAGE_DROP id="+id+" at "+e.getEntity().getLocation().getBlockX()
                    +","+e.getEntity().getLocation().getBlockZ());
        }catch(RuntimeException ignored){ }
    }
    private boolean moon(World w){return SiegeRules.bloodMoon(w.getFullTime(),plugin.getConfig().getInt("siege.blood-moon-every-nights",3));}
    /** Guaranteed staple: tops drops up to at least one rotten flesh, touching nothing else. */
    static void ensureFlesh(java.util.List<ItemStack> drops){
        for(ItemStack stack:drops)if(stack!=null&&stack.getType()==Material.ROTTEN_FLESH&&stack.getAmount()>0)return;
        drops.add(new ItemStack(Material.ROTTEN_FLESH,1));
    }
    private void tick(){ tick(new ArrayList<>(Bukkit.getOnlinePlayers())); }
    void tick(List<Player> candidates){
        ticks++; boolean scan=ticks%10==1;
        if(scan) {
            pass++; budget=SiegeRules.clamp(plugin.getConfig().getInt("siege.block-breaks-per-pass",6),1,12);
            work.edits=budget; awareness.begin(ticks); players.clear();
            for(Player p:candidates)if(players.size()<32&&plugin.enabledWorld(p.getWorld())&&plugin.huntable(p))players.add(p);
        }
        work.motions=SiegeRules.clamp(plugin.getConfig().getInt("siege.motions-per-tick",40),8,120);
        List<Hunter> ordered=new ArrayList<>(hunters.values());
        int size=ordered.size(); if(size>0)cursor=(cursor+work.motions)%size;
        if(scan&&size>0)for(int index=0;index<size;index++) {
            Hunter h=ordered.get((index+(int)(pass%size))%size);
            if(h.zombie.isValid()&&!h.zombie.isDead())awareness.update(h.zombie,h.memory,players);
        }
        for(int index=0;index<size;index++){
            Hunter h=ordered.get((index+cursor)%size);
            Zombie z=h.zombie;
            if(!z.isValid()||z.isDead()){hunters.remove(z.getUniqueId());continue;}
            EntityZombie handle=((CraftZombie)z).getHandle();
            // Visibility is a hard invariant for every tracked hunter, including one
            // whose awareness memory has just expired. Repair it before pursuit gates.
            if(scan)ensureVisible(h,handle);
            if(!awareness.valid(h.memory,z,players,ticks)) {
                h.memory.clear();h.traversal.endPursuit();z.setTarget(null);SiegeTraversal.halt(z);h.digging=null;h.strikes=0;h.fuse=0;
                if(scan&&++h.emptyPasses>240 && z.getRemoveWhenFarAway()){z.remove();hunters.remove(z.getUniqueId());}continue;
            }
            h.emptyPasses=0;
            Player target=h.memory.visible;
            // Paper 1.12's Bukkit setTarget explicitly suppresses events. Ask NMS to fire them.
            if(z.getTarget()!=target)((CraftZombie)z).getHandle().setGoalTarget(
                    target==null?null:((CraftPlayer)target).getHandle(),
                    org.bukkit.event.entity.EntityTargetEvent.TargetReason.CUSTOM,true);
            if(z.getTarget()!=target) { h.memory.clear();h.traversal.endPursuit();SiegeTraversal.halt(z);continue; }
            // Spigot monsters outside its 32-block activation radius otherwise never integrate velocity.
            // Only our capped, actively pursuing hunters are extended, one server tick at a time.
            handle.activatedTick=Math.max(handle.activatedTick,(long)MinecraftServer.currentTick+2);
            Location at=z.getLocation(),to=h.memory.goal;
            // Managed hunters intentionally have empty vanilla goal selectors, so vanilla's look goals
            // cannot turn them toward their target. Keep all three NMS rotations in sync while steering.
            face(z,target==null?to:target.getEyeLocation());
            boolean climbing=traversal.move(z,to,h.traversal,ticks,work);
            // Halved hunter reach: ~0.89 blocks feet-to-feet (0.8 squared), down from ~1.79 (3.2).
            if(target!=null && ticks-h.lastAttack>=20 && at.distanceSquared(target.getLocation())<0.8
                    && SiegeAwareness.lineOfSight(z,target.getEyeLocation())) {
                h.lastAttack=ticks;handle.B(((CraftPlayer)target).getHandle());
            }
            if(!scan)continue;
            if(at.distanceSquared(h.last)<.05)h.stalled++;else h.stalled=0;
            h.last=at.clone();
            if(h.kind.equals("tnt") && ((h.stalled>=4&&at.distanceSquared(to)<range*range) || at.distanceSquared(to)<7)){
                if(h.fuse==0)at.getWorld().playSound(at,Sound.ENTITY_TNT_PRIMED,1.4f,.75f);
                h.fuse+=10;at.getWorld().spawnParticle(Particle.SMOKE_NORMAL,at.clone().add(0,2,0),3,.2,.2,.2,.01);
                if(h.fuse>=Math.max(30,plugin.getConfig().getInt("siege.tnt-fuse-ticks",50))){explode(h);continue;}
            }else h.fuse=0;
            budget=work.edits;
            if(!climbing && h.stalled>=3 && budget>0 && !h.kind.equals("tnt"))breach(h,to);
            work.edits=budget;
            if((h.kind.equals("revenant")||h.kind.equals("warden")) && pass%4==0){
                at.getWorld().spawnParticle(Particle.SMOKE_NORMAL,at.clone().add(0,1,0),3,.3,.6,.3,.008);
                if(pass-h.lastPower>60&&at.distanceSquared(to)<196){
                    h.lastPower=pass;at.getWorld().playSound(at,Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL,.8f,.55f);
                    if(h.kind.equals("warden")){
                        for(int i=0;i<2;i++){Location pos=safeGround(at.getWorld(),at.getBlockX()+random.nextInt(7)-3,at.getBlockZ()+random.nextInt(7)-3,at.getBlockY());
                            if(pos!=null&&at.distanceSquared(pos)<36)spawn(pos,"walker");}
                    }
                }
            }
        }
        if(scan&&pass%20==0){ambient(players);announceMoon();}
        if(scan&&pass%10==0)daylightPass();
        if(scan&&pass%120==0)plugin.getLogger().info("APOCALYPSE_METRICS active="+hunters.size()+" "+metrics());
    }
    private void ensureVisible(Hunter hunter,EntityZombie handle){
        Zombie zombie=hunter.zombie;boolean repaired=false;
        if(zombie.hasPotionEffect(PotionEffectType.INVISIBILITY)){
            zombie.removePotionEffect(PotionEffectType.INVISIBILITY);repaired=true;
        }
        if(handle.isInvisible()) { handle.setInvisible(false);repaired=true; }
        if(!repaired)return;
        // Mark both channels so 1.12 trackers promptly resend authoritative metadata/position.
        handle.velocityChanged=true;handle.positionChanged=true;visibilityRepairs++;
        Location at=zombie.getLocation();
        plugin.getLogger().warning("APOCALYPSE_VISIBILITY_REPAIR type="+hunter.kind+" entity="+zombie.getUniqueId()
            +" chunk="+(at.getBlockX()>>4)+","+(at.getBlockZ()>>4));
    }
    private void breach(Hunter h,Location target){
        if(!SiegeTraversal.grief(h.zombie.getWorld())||budget<=0)return;
        Location at=h.zombie.getLocation();Vector toward=target.toVector().subtract(at.toVector());
        Vector flat=toward.clone().setY(0);if(flat.lengthSquared()>.01)flat.normalize();
        Block block=null;
        // First open headroom, then feet; also dig vertically toward roofs/cellars.
        for(double distance:new double[]{.65,1.15,1.65}){
            Location front=at.clone().add(flat.clone().multiply(distance));
            for(int dy:new int[]{1,0}){Location pos=front.clone().add(0,dy,0);if(!loaded(pos))continue;
                Block b=pos.getBlock();if(breakable(b)){block=b;break;}}
            if(block!=null)break;
        }
        if(block==null && Math.abs(toward.getY())>1.5){
            Location pos=at.clone().add(0,toward.getY()>0?2:-1,0);
            if(loaded(pos)&&breakable(pos.getBlock()))block=pos.getBlock();
        }
        if(block==null){h.digging=null;h.strikes=0;return;}
        if(!block.equals(h.digging)){h.digging=block;h.strikes=0;}
        h.strikes+=h.kind.equals("brute")||h.kind.equals("warden")?2:1;
        if(h.strikes%3==0)at.getWorld().playSound(block.getLocation(),Sound.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD,.5f,.75f);
        if(h.strikes<SiegeRules.breakPasses(block.getType()))return;
        EntityChangeBlockEvent event=new EntityChangeBlockEvent(h.zombie,block,Material.AIR,(byte)0);
        budget--;Bukkit.getPluginManager().callEvent(event);h.strikes=0;
        if(event.isCancelled()||!breakable(block))return;
        broken++;block.breakNaturally();h.digging=null;
    }
    private boolean loaded(Location p){return p.getBlockY()>1&&p.getBlockY()<254&&p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4);}
    private boolean breakable(Block b){return loaded(b.getLocation())&&SiegeTraversal.grief(b.getWorld())&&SiegeRules.breakPasses(b.getType())>0 && !(b.getState() instanceof Container);}
    private void explode(Hunter h){
        Zombie z=h.zombie;Location center=z.getLocation().add(0,.5,0);World w=z.getWorld();
        double r=Math.max(1.5,Math.min(3.5,plugin.getConfig().getDouble("siege.tnt-radius",2.6)));
        int max=Math.min(work.edits,Math.max(1,Math.min(48,plugin.getConfig().getInt("siege.tnt-max-blocks",32))));
        List<Block> list=new ArrayList<>();int radius=(int)Math.ceil(r);
        for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)for(int zz=-radius;zz<=radius;zz++){
            Location p=center.clone().add(x,y,zz);if(loaded(p)&&p.distanceSquared(center)<=r*r&&breakable(p.getBlock()))list.add(p.getBlock());
        }
        list.sort((a,b)->Double.compare(a.getLocation().distanceSquared(center),b.getLocation().distanceSquared(center)));
        if(list.size()>max)list=new ArrayList<>(list.subList(0,max));
        EntityExplodeEvent event=new EntityExplodeEvent(z,center,list,.15f);Bukkit.getPluginManager().callEvent(event);
        h.fuse=0;
        if(event.isCancelled())return;
        w.playSound(center,Sound.ENTITY_GENERIC_EXPLODE,2,.8f);w.spawnParticle(Particle.EXPLOSION_LARGE,center,2);
        int removed=0;
        for(Block b:event.blockList())if(work.edits>0&&removed<max&&b.getWorld()==w&&loaded(b.getLocation())&&b.getLocation().distanceSquared(center)<=(r+1)*(r+1)&&breakable(b)){
            work.edits--;
            EntityChangeBlockEvent change=new EntityChangeBlockEvent(z,b,Material.AIR,(byte)0);Bukkit.getPluginManager().callEvent(change);
            if(!change.isCancelled()&&breakable(b)){if(random.nextDouble()<.15)b.breakNaturally();else b.setType(Material.AIR,false);removed++;}
        }
        for(Entity e:z.getNearbyEntities(4,4,4))if(e instanceof LivingEntity && e!=z){
            if(e instanceof Player && !plugin.huntable((Player)e))continue;
            double d=e.getLocation().distance(center);if(d<4)((LivingEntity)e).damage(dayDamage(Math.max(1,(16-d*3)*0.5),w),z);
        }
        broken+=removed;blasts++;hunters.remove(z.getUniqueId());z.remove();
        // Detonation bypasses the death event, so the carrier's guaranteed flesh is left at the crater.
        w.dropItemNaturally(center,new ItemStack(Material.ROTTEN_FLESH,1));
        plugin.getLogger().info("APOCALYPSE_BREACH type=tnt blocks="+removed+" chunk="+(center.getBlockX()>>4)+","+(center.getBlockZ()>>4));
    }
    private Location safeGround(World w,int x,int zz,int aroundY){
        if(!w.isChunkLoaded(x>>4,zz>>4))return null;
        for(int y=Math.min(250,aroundY+5);y>=Math.max(3,aroundY-8);y--){
            Block ground=w.getBlockAt(x,y-1,zz),feet=w.getBlockAt(x,y,zz),head=w.getBlockAt(x,y+1,zz);
            if(ground.getType().isSolid()&&!ground.isLiquid()&&feet.getType()==Material.AIR&&head.getType()==Material.AIR)
                return new Location(w,x+.5,y,zz+.5);
        }return null;
    }
    private void ambient(List<Player> players){
        int localCap=Math.max(4,Math.min(24,plugin.getConfig().getInt("siege.ambient-per-player",14)));
        for(Player p:players){
            if(hunters.size()>=cap)return;
            // Day and night spawn equally: no time gate here. Torch and artificial
            // light still suppress spawning via the block-light check below.
            int nearby=0;for(Hunter h:hunters.values())if(h.zombie.getWorld()==p.getWorld()&&h.zombie.getLocation().distanceSquared(p.getLocation())<2304)nearby++;
            if(nearby>=localCap)continue;
            for(int attempt=0;attempt<6;attempt++){
                double angle=random.nextDouble()*Math.PI*2,distance=24+random.nextInt(18);
                int x=p.getLocation().getBlockX()+(int)(Math.cos(angle)*distance),z=p.getLocation().getBlockZ()+(int)(Math.sin(angle)*distance);
                Location at=safeGround(p.getWorld(),x,z,p.getLocation().getBlockY());
                if(at==null || at.getBlock().getLightFromBlocks()>7)continue;
                boolean close=false;for(Player other:players)if(other.getWorld()==p.getWorld()&&other.getLocation().distanceSquared(at)<400){close=true;break;}
                if(!close){spawn(at,SiegeRules.variant(random.nextInt(100),moon(p.getWorld())));break;}
            }
        }
    }
    static void face(Zombie zombie,Location goal){
        if(goal==null||goal.getWorld()!=zombie.getWorld())return;
        EntityZombie handle=((CraftZombie)zombie).getHandle();
        double dx=goal.getX()-handle.locX,dz=goal.getZ()-handle.locZ;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        if(horizontal<1.0E-4)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float pitch=(float)-Math.toDegrees(Math.atan2(goal.getY()-(handle.locY+handle.getHeadHeight()),horizontal));
        pitch=Math.max(-75.0f,Math.min(75.0f,pitch));
        handle.yaw=yaw;
        handle.h(yaw);
        handle.setHeadRotation(yaw);
        handle.pitch=pitch;
    }
    private void announceMoon(){
        for(World w:Bukkit.getWorlds())if(plugin.enabledWorld(w)&&moon(w)){
            long day=w.getFullTime()/24000;
            if(lastMoon.getOrDefault(w.getUID(),-1L)==day)continue;
            lastMoon.put(w.getUID(),day);
            for(Player p:w.getPlayers())if(plugin.isSurvivor(p)){
                p.sendTitle(ChatColor.DARK_RED+"BLOOD MOON",ChatColor.GRAY+"The ruins remember. The dead are listening.",20,100,40);
                p.playSound(p.getLocation(),Sound.AMBIENT_CAVE,.8f,.6f);
            }
            plugin.getLogger().info("APOCALYPSE_BLOOD_MOON day="+day);
        }
    }
    /** Day is the inverse of the old night-spawn window, so full-strength hours are unchanged. */
    static boolean isDaytime(long time){ return time<12500L || time>23500L; }
    private static UUID dayModifierId(Attribute attribute){
        return UUID.nameUUIDFromBytes((DAY_WEAKNESS+":"+attribute.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Day weakness: half max health while the sun is up, fully restored at night. */
    static void setDayScaled(LivingEntity entity,boolean day){
        AttributeInstance instance=entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if(instance!=null){
            UUID id=dayModifierId(Attribute.GENERIC_MAX_HEALTH);
            AttributeModifier present=null;
            for(AttributeModifier modifier:instance.getModifiers())if(id.equals(modifier.getUniqueId())){present=modifier;break;}
            if(day&&present==null)instance.addModifier(new AttributeModifier(id,DAY_WEAKNESS,-0.5,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            else if(!day&&present!=null)instance.removeModifier(present);
        }
        // Day/night damage is synchronized: hits land equally around the clock. Strip any
        // legacy damage-weakness marker left by earlier builds so no mob keeps half damage.
        AttributeInstance damage=entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if(damage!=null)removeModifier(damage,dayModifierId(Attribute.GENERIC_ATTACK_DAMAGE));
        // Never leave a weakened mob above its weakened maximum.
        if(day){
            AttributeInstance health=entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if(health!=null&&entity.getHealth()>health.getValue())entity.setHealth(health.getValue());
        }
    }
    private static double dayDamage(double damage,World world){
        return damage;
    }
    /** True when the eyes stand under open sky at or above the surface (direct sunlight). */
    static boolean isSunlit(Location at){
        if(at==null||at.getWorld()==null)return false;
        Location eye=at.clone().add(0,1.5,0);
        if(eye.getBlock().getLightFromSky()<15)return false;
        return at.getWorld().getHighestBlockYAt(eye)<=eye.getBlockY();
    }
    /** Sun sluggishness: 30% slower movement while sunlit, full speed otherwise. Idempotent. */
    static void setSunSluggish(LivingEntity entity,boolean sunlit){
        AttributeInstance speed=entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if(speed==null)return;
        UUID id=sluggishModifierId();
        if(sunlit&&!hasModifier(speed,id))speed.addModifier(new AttributeModifier(id,DAY_SLUGGISH,-0.3,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        else if(!sunlit&&hasModifier(speed,id))removeModifier(speed,id);
    }
    /** True while the daylight-slowness marker is present (regardless of phase). */
    static boolean sunSlowed(LivingEntity entity){
        AttributeInstance speed=entity==null?null:entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        return speed!=null&&hasModifier(speed,sluggishModifierId());
    }
    private static UUID sluggishModifierId(){
        return UUID.nameUUIDFromBytes((DAY_SLUGGISH+":"+Attribute.GENERIC_MOVEMENT_SPEED.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static boolean hasModifier(AttributeInstance instance,UUID id){
        for(AttributeModifier modifier:instance.getModifiers())if(id.equals(modifier.getUniqueId()))return true;
        return false;
    }
    private static void removeModifier(AttributeInstance instance,UUID id){
        for(AttributeModifier modifier:new java.util.ArrayList<AttributeModifier>(instance.getModifiers()))
            if(id.equals(modifier.getUniqueId()))instance.removeModifier(modifier);
    }
    /**
     * Daylight pass, every ~5s: all hostile mobs (above ground and underground,
     * siege hunters, invaders, vault guardians and vanilla hostiles) are weakened
     * during the day and restored at night; mobs standing in direct sunlight are
     * additionally 30% slower. Also carries the subtle day/night chat note.
     */
    private void daylightPass(){
        int slowed=0;
        for(World w:Bukkit.getWorlds()){
            if(!plugin.enabledWorld(w))continue;
            boolean day=isDaytime(w.getTime());
            Boolean before=lastDayPhase.put(w.getUID(),day);
            if(before!=null&&before!=day){
                String message=day?ChatColor.GRAY+"Day breaks over the ruins. The dead weaken."
                    :ChatColor.GRAY+"Night falls. The dead hunt at full strength.";
                for(Player p:w.getPlayers())if(plugin.isSurvivor(p))p.sendMessage(message);
                plugin.getLogger().info("APOCALYPSE_DAY_PHASE day="+day+" world="+w.getName());
            }
            for(LivingEntity entity:w.getLivingEntities()){
                if(entity instanceof Player||!(entity instanceof Monster))continue;
                if(!entity.isValid()||entity.isDead())continue;
                try{
                    setDayScaled(entity,day);
                    // Movement is the universal daylight law's business now: JasprDaylight
                    // halves every mob, in the open and underground alike. This pass only
                    // retires the older sunlit-only marker, because two multiplicative cuts
                    // on one attribute would compound into a crawl rather than a half.
                    setSunSluggish(entity,false);
                    boolean sunlit=day&&isSunlit(entity.getLocation());
                    if(sunlit)slowed++;
                }catch(RuntimeException ignored){ }
            }
        }
        sunSlowedCount=slowed;
    }
}
