package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** Server-owned, room-bounded weapons. Register this listener and call tick once per server tick. */
public final class Arsenal implements Listener {
    private static final int MAX_PENDING=512, MAX_PER_PLAYER=32, MAX_COLLIDERS=128;
    private final DungeonPlugin plugin;
    private final LongSupplier clock;
    private final File journals;
    private final Map<UUID,YamlConfiguration> cooldowns=new HashMap<>();
    private final List<Pending> pending=new ArrayList<>();
    private final List<Use> uses=new ArrayList<>();
    private final Map<EntityDamageByEntityEvent,Melee> melee=new IdentityHashMap<>();
    private Impact impact;
    private long ticks;
    private boolean closed;

    public Arsenal(DungeonPlugin plugin){this(plugin,System::currentTimeMillis,new File(plugin.getDataFolder(),"armory-cooldowns"));}
    // Controlled clock/journals only for the separately loaded runtime audit.
    Arsenal(DungeonPlugin plugin,LongSupplier clock,File journals){this.plugin=plugin;this.clock=clock;this.journals=journals;}

    static NBTTagCompound data(ItemStack item){
        if(item==null||item.getType()==Material.AIR)return null;
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        return n.hasTag()&&n.getTag().hasKeyOfType(ArmoryCatalog.NBT_KEY,10)?n.getTag().getCompound(ArmoryCatalog.NBT_KEY):null;
    }
    private static ItemStack edit(ItemStack item,Consumer<NBTTagCompound> edit){
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        NBTTagCompound root=n.hasTag()?n.getTag():new NBTTagCompound();
        NBTTagCompound tag=root.hasKeyOfType(ArmoryCatalog.NBT_KEY,10)?root.getCompound(ArmoryCatalog.NBT_KEY):new NBTTagCompound();
        edit.accept(tag);root.set(ArmoryCatalog.NBT_KEY,tag);n.setTag(root);return CraftItemStack.asBukkitCopy(n);
    }
    public static boolean marked(ItemStack item){return data(item)!=null;}
    public static ArmoryCatalog.Type type(ItemStack item){
        NBTTagCompound tag=data(item);
        if(tag==null||item.getAmount()!=1||tag.getInt("version")!=ArmoryCatalog.VERSION||!"weapon".equals(tag.getString("kind")))return null;
        try{ArmoryCatalog.Type t=ArmoryCatalog.Type.valueOf(tag.getString("id"));UUID.fromString(tag.getString("uuid"));return item.getType()==Material.valueOf(t.material)?t:null;}
        catch(IllegalArgumentException bad){return null;}
    }
    public static ItemStack create(ArmoryCatalog.Type t){
        Objects.requireNonNull(t,"type");ItemStack item=new ItemStack(Material.valueOf(t.material),1,(short)0);
        ItemMeta meta=item.getItemMeta();meta.setDisplayName((t.rank>=4?ChatColor.GOLD:ChatColor.LIGHT_PURPLE)+t.title);
        meta.setUnbreakable(true);meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);item.setItemMeta(meta);
        return describe(edit(item,d->{d.setInt("version",ArmoryCatalog.VERSION);d.setString("kind","weapon");d.setString("id",t.name());
            d.setString("uuid",UUID.randomUUID().toString());d.setInt("rounds",t.magazine);d.setLong("readyAt",0);d.setLong("procAt",0);
            d.setLong("reloadAt",0);d.setInt("loading",0);}));
    }
    /** Ordinary vanilla ammunition; reject invalid stack sizes rather than silently overflowing. */
    public static ItemStack createAmmo(int amount){if(amount<1||amount>64)throw new IllegalArgumentException("Ammo amount 1..64");return new ItemStack(Material.IRON_NUGGET,amount);}
    public static ItemStack createAmmo(){return createAmmo(64);}
    public static List<ItemStack> ammunitionItems(){return Collections.singletonList(createAmmo());}
    public static List<ItemStack> allItems(){List<ItemStack> out=new ArrayList<>();for(ArmoryCatalog.Type t:ArmoryCatalog.Type.values())out.add(create(t));out.add(createAmmo());return Collections.unmodifiableList(out);}
    public static ItemStack roll(Layout.Room room){ArmoryCatalog.Type t=ArmoryCatalog.roll(room.hash,room.theme,room.tier,room.kind.name());return t==null?null:create(t);}
    private static ItemStack describe(ItemStack item){
        ArmoryCatalog.Type t=type(item);if(t==null)return item;NBTTagCompound d=data(item);ItemMeta meta=item.getItemMeta();
        List<String> lore=new ArrayList<>();lore.add(ChatColor.DARK_PURPLE+"Dungeon armory | "+t.kind+" | Rank "+t.rank);
        lore.add(ChatColor.GRAY+t.description);lore.add(ChatColor.GRAY+"Damage "+t.damage+" | Range "+t.range+" | "+(t.cooldownMillis/1000.0)+"s recovery");
        if(t.gun()){
            lore.add(ChatColor.YELLOW+"Rounds: "+Math.max(0,Math.min(t.magazine,d.getInt("rounds")))+" / "+t.magazine+(d.getLong("reloadAt")>0?" (reloading)":""));
            lore.add(ChatColor.GRAY+"Right-click fires; reloads itself when empty; sneak + right-click reloads early.");
            lore.add(ChatColor.GRAY+"One plain iron nugget per round; reload "+(t.reloadMillis/1000.0)+"s.");
        }else lore.add(ChatColor.GRAY+(t.ranged()?"Right-click casts; no ammunition.":"Left-click strikes; special effects have bounded cooldowns."));
        lore.add(ChatColor.DARK_GRAY+"Found only in the dungeon; usable in other worlds. No PvP.");meta.setLore(lore);item.setItemMeta(meta);return item;
    }
    private static String serial(ItemStack item){NBTTagCompound d=data(item);return d==null?"":d.getString("uuid");}
    private static int rounds(ItemStack item,ArmoryCatalog.Type t){return Math.max(0,Math.min(t.magazine,data(item).getInt("rounds")));}
    private static boolean ammo(ItemStack item){return item!=null&&item.getType()==Material.IRON_NUGGET&&!item.hasItemMeta()&&!CraftItemStack.asNMSCopy(item).hasTag();}

    /** World identity is essential: three rifts may have rooms with identical x/z IDs. */
    private String roomKey(Location l){return l.getWorld().getUID()+":"+(plugin.inside(l.getWorld())?plugin.room(l).id():"outside");}
    private boolean safe(Location l,String key){
        if(l==null||l.getWorld()==null||!Double.isFinite(l.getX())||!Double.isFinite(l.getY())||!Double.isFinite(l.getZ())||!key.equals(roomKey(l)))return false;
        if(plugin.inside(l.getWorld()))return ArmoryCatalog.safePoint(plugin.room(l),l.getX(),l.getY(),l.getZ());
        return l.getY()>=0&&l.getY()<l.getWorld().getMaxHeight();
    }
    private boolean readyPlayer(Player p,String key){return !closed&&p!=null&&p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&safe(p.getLocation(),key)&&safe(p.getEyeLocation(),key);}
    private static boolean hostile(Entity e){return e instanceof Monster||e instanceof Slime||e instanceof Ghast||e instanceof Shulker;}
    private boolean allowed(Player p,LivingEntity target,String key){
        return readyPlayer(p,key)&&target!=null&&target.isValid()&&!target.isDead()&&hostile(target)&&!(target instanceof Player)
            &&!target.isInvulnerable()&&target.getWorld().equals(p.getWorld())&&safe(target.getLocation(),key);
    }
    private boolean held(Player p,ArmoryCatalog.Type t,String id){ItemStack hand=p.getInventory().getItemInMainHand();return type(hand)==t&&id.equals(serial(hand));}
    private YamlConfiguration journal(Player p)throws Exception{
        YamlConfiguration current=cooldowns.get(p.getUniqueId());
        if(current==null){current=new YamlConfiguration();File file=new File(journals,p.getUniqueId()+".yml");if(file.exists())current.load(file);cooldowns.put(p.getUniqueId(),current);}return current;
    }
    private boolean cooling(Player p,ItemStack item,String field){
        try{return clock.getAsLong()<Math.max(data(item).getLong(field),journal(p).getLong(field));}
        catch(Exception failure){plugin.getLogger().warning("ARMORY_STORAGE_READ_FAILED "+failure.getClass().getSimpleName());return true;}
    }
    /** Durably reserve recovery before damage. Swapping guns, relogging, or restarting cannot reset it. */
    private boolean reserve(Player p,ItemStack item,long until,long procUntil){
        try{
            YamlConfiguration next=new YamlConfiguration();next.loadFromString(journal(p).saveToString());
            next.set("readyAt",Math.max(next.getLong("readyAt"),until));next.set("procAt",Math.max(next.getLong("procAt"),procUntil));
            RoomStore.atomic(new File(journals,p.getUniqueId()+".yml"),next.saveToString());cooldowns.put(p.getUniqueId(),next);return true;
        }catch(Exception failure){plugin.getLogger().warning("ARMORY_STORAGE_WRITE_FAILED "+failure.getClass().getSimpleName());return false;}
    }
    private ItemStack settled(ItemStack item){
        ArmoryCatalog.Type t=type(item);if(t==null)return item;NBTTagCompound d=data(item);long due=d.getLong("reloadAt");
        if(due<=0||clock.getAsLong()<due)return item;
        int count=Math.min(t.magazine,rounds(item,t)+Math.max(0,Math.min(t.magazine,d.getInt("loading"))));
        return describe(edit(item,n->{n.setInt("rounds",count);n.setInt("loading",0);n.setLong("reloadAt",0);}));
    }
    private void reload(Player p,ItemStack item,ArmoryCatalog.Type t){
        if(!t.gun()||data(item).getLong("reloadAt")>0||rounds(item,t)>=t.magazine||cooling(p,item,"readyAt"))return;
        // Every storage slot: 56 with the wide inventory (armour and off hand come after them).
        int need=t.magazine-rounds(item,t),available=0,storage=p.getInventory().getStorageContents().length;
        for(int slot=0;slot<storage;slot++){ItemStack stack=p.getInventory().getItem(slot);if(ammo(stack))available+=stack.getAmount();}
        int load=Math.min(need,available);if(load==0){p.sendMessage(ChatColor.GRAY+"Reload requires plain iron nuggets.");return;}
        long due=clock.getAsLong()+t.reloadMillis;if(!reserve(p,item,due,0))return;
        int remaining=load;for(int slot=0;slot<storage&&remaining>0;slot++){
            ItemStack stack=p.getInventory().getItem(slot);if(!ammo(stack))continue;int take=Math.min(remaining,stack.getAmount());remaining-=take;
            ItemStack next=stack.clone();next.setAmount(stack.getAmount()-take);p.getInventory().setItem(slot,next.getAmount()==0?null:next);
        }
        cancel(p);p.getInventory().setItemInMainHand(describe(edit(item,n->{n.setInt("loading",load);n.setLong("reloadAt",due);n.setLong("readyAt",due);})));p.saveData();
        p.sendMessage(ChatColor.GRAY+"Reloading "+t.title+" ("+load+" rounds, "+(t.reloadMillis/1000.0)+"s).");
    }
    private static final class Use {
        final PlayerInteractEvent event;final String id,key;
        Use(PlayerInteractEvent event,String id,String key){this.event=event;this.id=id;this.key=key;}
    }
    /** Read both Bukkit result channels: vanilla air use begins with block=DENY, item=DEFAULT. */
    static boolean interactionAllowed(PlayerInteractEvent e){
        return e.useItemInHand()!=Event.Result.DENY&&(e.getAction()==Action.RIGHT_CLICK_AIR||e.useInteractedBlock()!=Event.Result.DENY);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void use(PlayerInteractEvent e){
        if(closed||e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        ArmoryCatalog.Type t=type(e.getItem());if(t==null||!t.ranged()||!interactionAllowed(e))return;
        String key=roomKey(e.getPlayer().getLocation());if(!readyPlayer(e.getPlayer(),key)||uses.size()>=MAX_PENDING)return;
        for(Use queued:uses)if(queued.event.getPlayer().equals(e.getPlayer()))return;
        uses.add(new Use(e,serial(e.getItem()),key));
    }
    private void activate(Use use){
        Player p=use.event.getPlayer();ItemStack item=settled(p.getInventory().getItemInMainHand());ArmoryCatalog.Type t=type(item);
        if(t==null||!t.ranged()||!use.id.equals(serial(item))||!readyPlayer(p,use.key)||!interactionAllowed(use.event))return;
        p.getInventory().setItemInMainHand(item);
        if(p.isSneaking()&&t.gun()){reload(p,item,t);return;}
        if(data(item).getLong("reloadAt")>0||cooling(p,item,"readyAt"))return;
        if(t.gun()&&rounds(item,t)<t.triggerCost()){reload(p,item,t);return;}
        long due=clock.getAsLong()+t.cooldownMillis;if(!reserve(p,item,due,0))return;
        int left=rounds(item,t)-t.triggerCost();p.getInventory().setItemInMainHand(describe(edit(item,n->{n.setInt("rounds",Math.max(0,left));n.setLong("readyAt",due);})));p.saveData();
        Location origin=p.getEyeLocation();Vector direction=origin.getDirection().normalize();
        shoot(p,t,use.id,use.key,origin,direction);
        for(int pulse=1;pulse<t.burst;pulse++)enqueue(new Pending(p,t,use.id,use.key,ticks+pulse*11,origin,direction));
        // Owner 2026-10-05: a gun that can no longer pay for a trigger pull reloads by itself once its shot recovery is over.
        if(t.gun()&&left<t.triggerCost())enqueue(new Pending(p,t,use.id,use.key,ticks+(t.cooldownMillis+49)/50+(t.burst-1)*11L+1));
    }
    /** The automatic reload: still the same gun in hand, still unable to fire, not reloading, and plain iron nuggets carried. */
    private void load(Player p,Pending job){
        ItemStack item=settled(p.getInventory().getItemInMainHand());ArmoryCatalog.Type t=type(item);
        if(t==null||t!=job.type||!t.gun()||!job.id.equals(serial(item))||rounds(item,t)>=t.triggerCost()||data(item).getLong("reloadAt")>0)return;
        boolean carried=false;int storage=p.getInventory().getStorageContents().length;
        for(int slot=0;slot<storage&&!carried;slot++)carried=ammo(p.getInventory().getItem(slot));
        if(!carried)return;
        p.getInventory().setItemInMainHand(item);reload(p,item,t);
    }

    private static final class Melee {
        final Player p;final LivingEntity target;final ArmoryCatalog.Type type;final String id,key;final double health;final boolean proc;
        Melee(Player p,LivingEntity target,ArmoryCatalog.Type type,String id,String key,boolean proc){this.p=p;this.target=target;this.type=type;this.id=id;this.key=key;this.proc=proc;health=target.getHealth();}
    }
    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true) public void strike(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player))return;Player p=(Player)e.getDamager();ItemStack item=p.getInventory().getItemInMainHand();
        if(!marked(item))return;ArmoryCatalog.Type t=type(item);
        if(closed||t==null||t.ranged()||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK||!(e.getEntity() instanceof LivingEntity)){e.setCancelled(true);return;}
        LivingEntity target=(LivingEntity)e.getEntity();String key=roomKey(p.getLocation());
        if(!allowed(p,target,key)||p.getEyeLocation().distanceSquared(target.getEyeLocation())>16||!visible(p.getEyeLocation(),target.getEyeLocation(),key)||cooling(p,item,"readyAt")){e.setCancelled(true);return;}
        boolean proc=!cooling(p,item,"procAt");long ready=clock.getAsLong()+t.cooldownMillis,procAt=proc?clock.getAsLong()+t.procMillis:data(item).getLong("procAt");
        if(!reserve(p,item,ready,procAt)){e.setCancelled(true);return;}
        p.getInventory().setItemInMainHand(edit(item,n->{n.setLong("readyAt",ready);n.setLong("procAt",procAt);}));p.saveData();
        double amount=t.damage;
        if(proc&&t.mechanic==ArmoryCatalog.Mechanic.EXECUTE&&target.getHealth()/maxHealth(target)<.30)amount+=4;
        e.setDamage(amount);melee.put(e,new Melee(p,target,t,serial(item),key,proc));
    }
    private static double maxHealth(LivingEntity target){return target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();}
    private static final class Impact {
        final Player player;final LivingEntity victim;EntityDamageByEntityEvent event;
        Impact(Player player,LivingEntity victim){this.player=player;this.victim=victim;}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void damageResult(EntityDamageByEntityEvent e){
        if(impact==null||e.getEntity()!=impact.victim||!(e.getDamager() instanceof Projectile))return;
        if(((Projectile)e.getDamager()).getShooter()==impact.player)impact.event=e;
    }
    /** The NMS bridge creates a PROJECTILE event, honors armor/cancellation/i-frames, and credits the shooter. */
    private boolean damage(Player p,LivingEntity target,double amount,String key,Location origin){
        if(!allowed(p,target,key)||!safe(origin,key)||!visible(p.getEyeLocation(),target.getEyeLocation(),key)||amount<=0)return false;
        Impact previous=impact,current=new Impact(p,target);impact=current;double before=target.getHealth();
        try{Bridge.damage(p,target,Math.min(24,amount),origin);}finally{impact=previous;}
        // Inspect the event after dispatch returns, including later MONITOR listeners.
        return current.event!=null&&!current.event.isCancelled()&&current.event.getFinalDamage()>0&&(target.isDead()||target.getHealth()<before);
    }
    private static final class Hit {final LivingEntity entity;final double distance;Hit(LivingEntity entity,double distance){this.entity=entity;this.distance=distance;}}
    private void shoot(Player p,ArmoryCatalog.Type t,String id,String key,Location origin,Vector direction){
        if(!readyPlayer(p,key)||!held(p,t,id)||!safe(origin,key))return;
        Collection<Entity> nearby=p.getWorld().getNearbyEntities(origin,t.range+1,t.range+1,t.range+1);if(nearby.size()>MAX_COLLIDERS)return;
        Map<LivingEntity,Double> sums=new LinkedHashMap<>();
        for(int ray=0;ray<t.rays;ray++){
            double[] angles=ArmoryCatalog.spread(t,ray);Vector v=direction(direction,angles[0],angles[1]);double limit=trace(origin,v,t.range,key);
            List<Hit> hits=new ArrayList<>();
            for(Entity entity:nearby)if(entity!=p&&entity instanceof LivingEntity&&entity.isValid()&&!entity.isDead()){
                double distance=Bridge.hit(origin,v,entity,limit);if(Double.isFinite(distance))hits.add(new Hit((LivingEntity)entity,distance));
            }
            hits.sort(Comparator.comparingDouble((Hit h)->h.distance).thenComparing(h->h.entity.getUniqueId()));int pierced=0;
            for(Hit hit:hits){
                // Players, pets, passive mobs, and invulnerable entities stop even piercing rays.
                if(!allowed(p,hit.entity,key)){limit=Math.min(limit,hit.distance);break;}
                sums.put(hit.entity,sums.getOrDefault(hit.entity,0.0)+ArmoryCatalog.penetrationDamage(t,pierced));
                if(++pierced>=t.pierce){limit=Math.min(limit,hit.distance);break;}
            }
            trail(origin,v,limit);
        }
        Set<UUID> primary=new HashSet<>();for(LivingEntity e:sums.keySet())primary.add(e.getUniqueId());
        for(Map.Entry<LivingEntity,Double> hit:sums.entrySet()){
            LivingEntity target=hit.getKey();double amount=hit.getValue();
            if(t.mechanic==ArmoryCatalog.Mechanic.ABSOLVE&&target.getHealth()/maxHealth(target)<.35)amount+=5;
            if(damage(p,target,amount,key,origin)&&allowed(p,target,key))effect(p,target,t,id,key,primary);
        }
    }
    static Vector direction(Vector forward,double yaw,double pitch){
        Vector f=forward.clone().normalize(),right=f.clone().crossProduct(new Vector(0,1,0));
        if(right.lengthSquared()<1e-8)right=new Vector(1,0,0);else right.normalize();
        Vector up=right.clone().crossProduct(f).normalize();
        return f.multiply(Math.cos(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch)))
            .add(right.multiply(Math.sin(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch))))
            .add(up.multiply(Math.sin(Math.toRadians(pitch)))).normalize();
    }
    /** Bound the segment before NMS traces; unloaded chunks are walls, never implicitly generated. */
    private double trace(Location origin,Vector direction,double requested,String key){
        double allowed=0;double bound=Math.min(36,requested);
        if(!safe(origin,key)||!origin.getWorld().isChunkLoaded(origin.getBlockX()>>4,origin.getBlockZ()>>4))return 0;
        for(double step=.125;step<bound+.125;step+=.125){
            double length=Math.min(bound,step);Location at=origin.clone().add(direction.clone().multiply(length));
            if(!safe(at,key)||!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))break;allowed=length;
            if(length==bound)break;
        }
        return Bridge.blockDistance(origin,direction,allowed);
    }
    private boolean visible(Location from,Location to,String key){
        if(from==null||to==null||!from.getWorld().equals(to.getWorld())||!safe(from,key)||!safe(to,key))return false;
        Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();
        return length<.001||(length<=36&&trace(from,delta.multiply(1/length),length,key)>=length-.001);
    }
    private static void trail(Location start,Vector v,double length){
        for(double step=.8;step<=length;step+=1.6)start.getWorld().spawnParticle(Particle.CRIT_MAGIC,start.clone().add(v.clone().multiply(step)),1,0,0,0,0);
    }
    private List<LivingEntity> neighbors(Player p,LivingEntity target,String key,double radius,Set<UUID> skip){
        List<LivingEntity> out=new ArrayList<>();Collection<Entity> nearby=target.getNearbyEntities(radius,2,radius);if(nearby.size()>MAX_COLLIDERS)return out;
        for(Entity e:nearby)if(e instanceof LivingEntity&&e!=target&&!skip.contains(e.getUniqueId())){
            LivingEntity other=(LivingEntity)e;
            if(allowed(p,other,key)&&target.getLocation().distanceSquared(other.getLocation())<=radius*radius
                &&visible(target.getEyeLocation(),other.getEyeLocation(),key)&&visible(p.getEyeLocation(),other.getEyeLocation(),key))out.add(other);
        }
        out.sort(Comparator.comparingDouble((LivingEntity e)->e.getLocation().distanceSquared(target.getLocation())).thenComparing(Entity::getUniqueId));return out;
    }
    private void effect(Player p,LivingEntity target,ArmoryCatalog.Type t,String id,String key,Set<UUID> primary){
        if(!allowed(p,target,key)||!held(p,t,id))return;
        switch(t.mechanic){
            case SIPHON:
                EntityRegainHealthEvent heal=new EntityRegainHealthEvent(p,1,EntityRegainHealthEvent.RegainReason.CUSTOM);Bukkit.getPluginManager().callEvent(heal);
                if(!heal.isCancelled()&&readyPlayer(p,key))p.setHealth(Math.min(maxHealth(p),p.getHealth()+Math.max(0,Math.min(1,heal.getAmount()))));break;
            case ENFEEBLE:case DRILL:status(target,PotionEffectType.WEAKNESS,60,0);break;
            case STAGGER:status(target,PotionEffectType.SLOW,40,1);break;
            case ROOT:status(target,PotionEffectType.SLOW,30,2);break;
            case CHILL:status(target,PotionEffectType.SLOW,60,0);break;
            case WARD:if(!p.hasPotionEffect(PotionEffectType.ABSORPTION))p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,60,0,true,false));break;
            case REND:case VENOM:case BRAND:
                // No vanilla poison/fire survives escape, because each pulse is a fresh bounded event.
                for(int pulse=1;pulse<=3;pulse++)enqueue(new Pending(p,t,id,key,ticks+pulse*11,target,t.mechanic==ArmoryCatalog.Mechanic.BRAND?2:1));break;
            case CLEAVE:case THRUST:case CHAIN:case RICOCHET:
                double radius=t.mechanic==ArmoryCatalog.Mechanic.CLEAVE?2.6:t.mechanic==ArmoryCatalog.Mechanic.THRUST?1.8:4;
                int budget=t.mechanic==ArmoryCatalog.Mechanic.THRUST||t.mechanic==ArmoryCatalog.Mechanic.RICOCHET?1:2;
                for(LivingEntity other:neighbors(p,target,key,radius,primary)){
                    if(t.mechanic==ArmoryCatalog.Mechanic.THRUST){
                        Vector axis=target.getLocation().toVector().subtract(p.getLocation().toVector()).normalize(),offset=other.getLocation().toVector().subtract(target.getLocation().toVector());
                        double along=offset.dot(axis);if(along<=0||offset.clone().subtract(axis.multiply(along)).lengthSquared()>.65*.65)continue;
                    }
                    double amount=t.mechanic==ArmoryCatalog.Mechanic.CLEAVE?3:t.mechanic==ArmoryCatalog.Mechanic.THRUST?4:t.damage*.5;
                    damage(p,other,amount,key,target.getEyeLocation());primary.add(other.getUniqueId());if(--budget==0)break;
                }break;
            case IMPACT:
                Vector push=target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if(push.lengthSquared()>.001){push.normalize().multiply(.28);Location end=target.getLocation().clone().add(push.clone().multiply(4));
                    if(safe(end,key)&&visible(target.getEyeLocation(),end.clone().add(0,target.getEyeHeight(),0),key))target.setVelocity(push);}break;
            default:break; // Accuracy, spread, burst, penetration and execution are applied by their own paths.
        }
    }
    private static void status(LivingEntity target,PotionEffectType effect,int ticks,int amplifier){
        // Do not shorten or overwrite stronger effects from other systems.
        PotionEffect existing=target.getPotionEffect(effect);
        if(existing==null||existing.getAmplifier()<amplifier||(existing.getAmplifier()==amplifier&&existing.getDuration()<ticks))target.addPotionEffect(new PotionEffect(effect,ticks,amplifier));
    }
    private static final class Pending {
        final UUID player;final ArmoryCatalog.Type type;final String id,key;final long due;final Location origin;final Vector direction;final LivingEntity victim;final double damage;
        /** A reload that follows the shot which emptied the magazine (owner 2026-10-05). */
        final boolean load;
        Pending(Player p,ArmoryCatalog.Type t,String id,String key,long due,Location origin,Vector direction){player=p.getUniqueId();type=t;this.id=id;this.key=key;this.due=due;this.origin=origin.clone();this.direction=direction.clone();victim=null;damage=0;load=false;}
        Pending(Player p,ArmoryCatalog.Type t,String id,String key,long due,LivingEntity victim,double damage){player=p.getUniqueId();type=t;this.id=id;this.key=key;this.due=due;origin=null;direction=null;this.victim=victim;this.damage=damage;load=false;}
        Pending(Player p,ArmoryCatalog.Type t,String id,String key,long due){player=p.getUniqueId();type=t;this.id=id;this.key=key;this.due=due;origin=null;direction=null;victim=null;damage=0;load=true;}
    }
    private void enqueue(Pending job){
        if(pending.size()>=MAX_PENDING)return;int count=0;for(Pending p:pending)if(p.player.equals(job.player))count++;if(count<MAX_PER_PLAYER)pending.add(job);
    }
    private void cancel(Player p){UUID id=p.getUniqueId();pending.removeIf(job->job.player.equals(id));uses.removeIf(use->use.event.getPlayer().equals(p));melee.entrySet().removeIf(e->e.getValue().p.equals(p));}
    public void tick(){
        if(closed)return;ticks++;
        List<Use> inputs=new ArrayList<>(uses);uses.clear();for(Use use:inputs)activate(use);
        Map<EntityDamageByEntityEvent,Melee> attacks=new IdentityHashMap<>(melee);melee.clear();
        for(Map.Entry<EntityDamageByEntityEvent,Melee> entry:attacks.entrySet()){
            EntityDamageByEntityEvent e=entry.getKey();Melee hit=entry.getValue();
            if(!e.isCancelled()&&e.getFinalDamage()>0&&hit.proc&&hit.target.getHealth()<hit.health&&allowed(hit.p,hit.target,hit.key)&&held(hit.p,hit.type,hit.id)
                &&visible(hit.p.getEyeLocation(),hit.target.getEyeLocation(),hit.key))effect(hit.p,hit.target,hit.type,hit.id,hit.key,new HashSet<UUID>());
        }
        List<Pending> due=new ArrayList<>();Iterator<Pending> iterator=pending.iterator();while(iterator.hasNext()){Pending job=iterator.next();if(job.due<=ticks){iterator.remove();due.add(job);}}
        for(Pending job:due){
            Player p=Bukkit.getPlayer(job.player);if(!readyPlayer(p,job.key)||!held(p,job.type,job.id))continue;
            if(job.load){load(p,job);continue;}
            if(job.victim!=null){if(allowed(p,job.victim,job.key))damage(p,job.victim,job.damage,job.key,p.getEyeLocation());}
            else shoot(p,job.type,job.id,job.key,job.origin,job.direction);
        }
        if(ticks%10==0)for(Player p:Bukkit.getOnlinePlayers())for(int slot=0,storage=p.getInventory().getStorageContents().length;slot<storage;slot++){
            ItemStack item=p.getInventory().getItem(slot);if(type(item)!=null){ItemStack next=settled(item);if(next!=item)p.getInventory().setItem(slot,next);}
        }
    }
    public void close(){closed=true;pending.clear();uses.clear();melee.clear();cooldowns.clear();impact=null;}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer());cooldowns.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){cancel(e.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void held(PlayerItemHeldEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void wear(PlayerItemDamageEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(marked(e.getPlayer().getInventory().getItemInMainHand()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(marked(e.getItemInHand()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispense(BlockDispenseEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void smelt(FurnaceSmeltEvent e){if(marked(e.getSource()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void brew(BrewEvent e){for(ItemStack item:e.getContents().getContents())if(marked(item)){e.setCancelled(true);break;}}
    @EventHandler public void craft(PrepareItemCraftEvent e){for(ItemStack item:e.getInventory().getMatrix())if(marked(item)){e.getInventory().setResult(null);break;}}
    @EventHandler public void anvil(PrepareAnvilEvent e){if(marked(e.getInventory().getItem(0))||marked(e.getInventory().getItem(1)))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void equipEntity(PlayerInteractEntityEvent e){
        ItemStack item=e.getHand()==EquipmentSlot.HAND?e.getPlayer().getInventory().getItemInMainHand():e.getPlayer().getInventory().getItemInOffHand();if(marked(item))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void horseInventory(InventoryClickEvent e){
        if(e.getView().getTopInventory() instanceof HorseInventory&&(marked(e.getCursor())||marked(e.getCurrentItem())
            ||(e.getHotbarButton()>=0&&marked(e.getWhoClicked().getInventory().getItem(e.getHotbarButton())))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void horseDrag(InventoryDragEvent e){if(e.getView().getTopInventory() instanceof HorseInventory&&marked(e.getOldCursor()))e.setCancelled(true);}

    /** All 1.12 implementation-specific access is kept in this small adapter. */
    private static final class Bridge {
        static double blockDistance(Location from,Vector direction,double limit){
            if(limit<=0)return 0;
            net.minecraft.server.v1_12_R1.Vec3D start=new net.minecraft.server.v1_12_R1.Vec3D(from.getX(),from.getY(),from.getZ());
            Location end=from.clone().add(direction.clone().multiply(limit));
            net.minecraft.server.v1_12_R1.MovingObjectPosition hit=((CraftWorld)from.getWorld()).getHandle().rayTrace(start,new net.minecraft.server.v1_12_R1.Vec3D(end.getX(),end.getY(),end.getZ()),false,false,false);
            return hit==null?limit:Math.max(0,Math.sqrt(square(hit.pos.x-from.getX())+square(hit.pos.y-from.getY())+square(hit.pos.z-from.getZ()))-.01);
        }
        static double square(double n){return n*n;}
        static double hit(Location origin,Vector direction,Entity entity,double limit){
            net.minecraft.server.v1_12_R1.AxisAlignedBB box=((CraftEntity)entity).getHandle().getBoundingBox();
            return ArmoryCatalog.rayBox(new double[]{origin.getX(),origin.getY(),origin.getZ()},new double[]{direction.getX(),direction.getY(),direction.getZ()},new double[]{box.a,box.b,box.c},new double[]{box.d,box.e,box.f},limit);
        }
        static void damage(Player p,LivingEntity target,double amount,Location from){
            net.minecraft.server.v1_12_R1.EntityTippedArrow arrow=new net.minecraft.server.v1_12_R1.EntityTippedArrow(((CraftWorld)p.getWorld()).getHandle());
            arrow.setPosition(from.getX(),from.getY(),from.getZ());((Arrow)arrow.getBukkitEntity()).setShooter(p);
            ((CraftLivingEntity)target).getHandle().damageEntity(net.minecraft.server.v1_12_R1.DamageSource.arrow(arrow,((CraftPlayer)p).getHandle()),(float)amount);
            // The attribution carrier is never spawned, persisted, picked up, or allowed to hit blocks.
        }
    }
}
