package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.projectiles.ProjectileSource;

/** Two NBT sockets, one active pouch, no separate item registry or equipment attributes. */
public final class Relics implements Listener {
    private static final String KEY="JasprPenitentRelic";
    public enum Type {
        SALT_TEAR, MARROW_BEAD, PILGRIM_KNOT, CINDER_HEART, HOLLOW_LENS, RUSTED_HALO,
        BLOOD_THREAD, LAMB_BELL, MOURNING_PEARL, CONFESSOR_SEAL, WARDEN_EYE, CROWN_OF_MERCY,
        EMBER_VIAL, ASHEN_BOOKMARK, SCRIBE_QUILL, RUBY_BROOCH, RIME_NEEDLE, THAWED_LOCKET,
        ROOT_HEART, GRAVE_SEED, BRASS_ESCAPEMENT, PENANCE_COG, SURGEON_THIMBLE, QUARANTINE_MASK,
        SILVER_VERDICT, MIRROR_SHARD, SALT_CENSER, CHOIR_SHELL, DIVER_SEAL, RELIQUARY_KEY,
        HANGMAN_LOOP, FASTING_SPOON, STAR_CHART, NIGHT_TALLOW, THORN_BROOCH, SANCTUARY_ACORN,
        WAX_SEAL, VIGIL_WICK, CRIMSON_SUTURE, CHALICE_CHAIN, BASILICA_CHIP, FRACTURED_ICON,
        SPORE_PENDANT, MYCELIAL_PAD, IRON_WRIT, CUSTODIAN_RIVET, VELVET_RIBBON, FUNERAL_BUTTON,
        AMBER_PRISM, BAPTISM_DROP, MUFFLED_CLAPPER, BELL_COUNTERWEIGHT, CARRION_TOKEN, KEEPER_WHISTLE,
        OPAL_CABOCHON, PRISMATIC_CLASP, FOUNDRY_SLAG, TEMPERED_RIVET, MENAGERIE_TAG, PALE_FEATHER,
        SODDEN_BOOKMARK, SCRIBE_REED, OBSIDIAN_CLASP, VESTRY_PIN, PAUPER_COIN, GILDED_CRUMB,
        ASTRAL_COMPASS, ORBIT_BEAD, LABYRINTH_THREAD, MOURNER_TREAD, ABSOLUTION_MEDAL, LAST_CANDLE;
        public final LootCatalog.Bauble spec;
        public final String title;
        public final Material material;
        public final int rank;
        public final String[] description;
        Type(){spec=LootCatalog.Bauble.valueOf(name());title=spec.title;material=Material.valueOf(spec.material);rank=spec.rank;description=spec.description.toArray(new String[0]);}
    }
    private final DungeonPlugin plugin;
    private final LongSupplier clock;
    private final File cooldownDirectory;
    private final Map<UUID,String> currentRoom=new HashMap<>();
    private final Map<UUID,YamlConfiguration> cooldowns=new HashMap<>();
    private final Map<UUID,Rest> resting=new HashMap<>();
    private final Map<UUID,MirrorMark> mirrors=new HashMap<>();
    private boolean retaliating;
    private static final class MirrorMark {
        final UUID shooter;final long until;
        MirrorMark(UUID shooter,long until){this.shooter=shooter;this.until=until;}
    }
    private static final class Rest {
        final Location location;final long since;
        Rest(Location location,long since){this.location=location;this.since=since;}
    }
    public Relics(DungeonPlugin plugin){this(plugin,System::currentTimeMillis,new File(plugin.getDataFolder(),"mercy"));}
    // Isolated runtime audit uses a controlled clock and separate journal folder; no production bypass.
    Relics(DungeonPlugin plugin,LongSupplier clock,File cooldownDirectory){this.plugin=plugin;this.clock=clock;this.cooldownDirectory=cooldownDirectory;}
    static NBTTagCompound data(ItemStack item){
        if(item==null||item.getType()==Material.AIR)return null;
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        return n.hasTag()&&n.getTag().hasKeyOfType(KEY,10)?n.getTag().getCompound(KEY):null;
    }
    public static ItemStack edit(ItemStack item,Consumer<NBTTagCompound> fn){
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        NBTTagCompound root=n.hasTag()?n.getTag():new NBTTagCompound();
        NBTTagCompound d=root.hasKeyOfType(KEY,10)?root.getCompound(KEY):new NBTTagCompound();
        fn.accept(d);root.set(KEY,d);n.setTag(root);return CraftItemStack.asBukkitCopy(n);
    }
    public static Type type(ItemStack item){
        NBTTagCompound d=data(item);if(d==null||!"relic".equals(d.getString("kind")))return null;
        try{Type t=Type.valueOf(d.getString("id"));return item.getType()==t.material?t:null;}catch(IllegalArgumentException ex){return null;}
    }
    public static boolean pouch(ItemStack item){
        NBTTagCompound d=data(item);
        return d!=null&&"pouch".equals(d.getString("kind"))&&item.getType()==Material.RABBIT_HIDE&&item.getAmount()==1&&!d.getString("uuid").isEmpty();
    }
    private static boolean marked(ItemStack item){return data(item)!=null;}
    public static ItemStack create(Type t){
        ItemStack item=new ItemStack(t.material);ItemMeta m=item.getItemMeta();
        m.setDisplayName((t.rank>=4?ChatColor.GOLD:ChatColor.LIGHT_PURPLE)+t.title);
        List<String> lore=new ArrayList<>();lore.add(ChatColor.DARK_PURPLE+"Dungeon-exclusive bauble | Rank "+t.rank);
        for(String line:t.description)lore.add(ChatColor.GRAY+line);
        lore.add(ChatColor.DARK_GRAY+"Favored in "+LootCatalog.profile(t.spec.theme).theme+".");
        lore.add(ChatColor.GRAY+"Equip inside a Penitent Reliquary.");
        lore.add(ChatColor.DARK_GRAY+"Only found in The Penitent Below.");m.setLore(lore);item.setItemMeta(m);
        return edit(item,d->{d.setString("kind","relic");d.setString("id",t.name());d.setInt("version",1);});
    }
    public static ItemStack createPouch(){
        ItemStack item=new ItemStack(Material.RABBIT_HIDE);ItemMeta m=item.getItemMeta();m.setDisplayName(ChatColor.GOLD+"Penitent Reliquary");
        m.setLore(Arrays.asList(ChatColor.GRAY+"Right-click to equip two distinct dungeon relics.",ChatColor.GRAY+"Carry in your main inventory to activate them.",
            ChatColor.GRAY+"Only your first pouch is active. Effects work in all worlds.",ChatColor.DARK_GRAY+"Contents travel with this pouch, including on death."));
        item.setItemMeta(m);return edit(item,d->{d.setString("kind","pouch");d.setString("uuid",UUID.randomUUID().toString());d.setInt("version",1);});
    }
    public static ItemStack roll(Layout.Room r){
        LootCatalog.Bauble b=LootCatalog.roll(r.hash,r.theme,r.tier,r.kind.name());return b==null?null:create(Type.valueOf(b.name()));
    }
    public int pouchSlot(Player p){for(int s=0;s<36;s++)if(pouch(p.getInventory().getItem(s)))return s;return -1;}
    public Set<Type> equipped(Player p){
        int slot=pouchSlot(p);Set<Type> out=EnumSet.noneOf(Type.class);
        if(slot>=0){NBTTagCompound d=data(p.getInventory().getItem(slot));for(int s=0;s<2;s++)try{out.add(Type.valueOf(d.getString("slot"+s)));}catch(IllegalArgumentException ignored){}}
        return out;
    }
    public boolean has(Player p,Type t){return equipped(p).contains(t);}
    public void givePouch(Player p){
        if(pouchSlot(p)>=0){open(p);return;}if(p.getInventory().firstEmpty()<0){p.sendMessage("Make an inventory space for your Reliquary.");return;}
        p.getInventory().addItem(createPouch());p.saveData();p.sendMessage(ChatColor.GOLD+"A Penitent Reliquary. Right-click it to equip two dungeon baubles.");
    }
    public static final class Menu implements InventoryHolder {
        final UUID player;final String pouchId;final Inventory inv;
        Menu(Player p,String id){player=p.getUniqueId();pouchId=id;inv=Bukkit.createInventory(this,27,"Penitent Reliquary - 2 slots");}
        public Inventory getInventory(){return inv;}
    }
    public void open(Player p){
        int slot=pouchSlot(p);if(slot<0){p.sendMessage("The Last Candle's reliquary offers an empty accessory pouch.");return;}
        Menu menu=new Menu(p,data(p.getInventory().getItem(slot)).getString("uuid"));render(p,menu);p.openInventory(menu.inv);
    }
    private void render(Player p,Menu menu){
        menu.inv.clear();ItemStack pane=new ItemStack(Material.STAINED_GLASS_PANE,1,(short)15);ItemMeta pm=pane.getItemMeta();
        pm.setDisplayName("Click a relic below to equip; click a socket to remove.");pane.setItemMeta(pm);for(int s=0;s<27;s++)menu.inv.setItem(s,pane);
        int ps=pouchSlot(p);if(ps<0)return;NBTTagCompound d=data(p.getInventory().getItem(ps));
        for(int s=0;s<2;s++){ItemStack show=new ItemStack(Material.PAPER);ItemMeta im=show.getItemMeta();im.setDisplayName("Empty bauble socket "+(s+1));show.setItemMeta(im);
            try{show=create(Type.valueOf(d.getString("slot"+s)));}catch(IllegalArgumentException ignored){}menu.inv.setItem(s==0?11:15,show);}
    }
    // Inert items' air use is pre-cancelled by Bukkit; still open a marked personal pouch.
    @EventHandler(priority=EventPriority.HIGH) public void use(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        if(!marked(e.getItem()))return;e.setCancelled(true);
        if(e.getHand()==EquipmentSlot.HAND&&pouch(e.getItem()))open(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu))return;
        e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player))return;
        Player p=(Player)e.getWhoClicked();Menu m=(Menu)e.getView().getTopInventory().getHolder();int ps=pouchSlot(p);
        if(ps<0||!p.getUniqueId().equals(m.player)||!data(p.getInventory().getItem(ps)).getString("uuid").equals(m.pouchId)){p.closeInventory();return;}
        if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.SHIFT_LEFT)return;
        NBTTagCompound d=data(p.getInventory().getItem(ps));int socket=e.getRawSlot()==11?0:e.getRawSlot()==15?1:-1;
        if(socket>=0){
            Type t;try{t=Type.valueOf(d.getString("slot"+socket));}catch(IllegalArgumentException ex){return;}
            if(p.getInventory().firstEmpty()<0){p.sendMessage("Make an inventory space first.");return;}
            final int s=socket;p.getInventory().setItem(ps,edit(p.getInventory().getItem(ps),n->n.remove("slot"+s)));
            p.getInventory().addItem(create(t));p.saveData();render(p,m);return;
        }
        if(e.getRawSlot()<27||e.getClickedInventory()!=p.getInventory())return;
        Type t=type(e.getCurrentItem());if(t==null)return;
        if(equipped(p).contains(t)){p.sendMessage("Two identical relics do not stack.");return;}
        int free=!d.hasKey("slot0")?0:!d.hasKey("slot1")?1:-1;
        if(free<0){p.sendMessage("Both sockets are occupied. Remove one first.");return;}
        ItemStack old=e.getCurrentItem().clone();old.setAmount(old.getAmount()-1);e.setCurrentItem(old.getAmount()==0?null:old);
        p.getInventory().setItem(ps,edit(p.getInventory().getItem(ps),n->n.setString("slot"+free,t.name())));p.saveData();render(p,m);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void trade(InventoryClickEvent e){
        Inventory top=e.getView().getTopInventory();
        if(top.getType()==InventoryType.MERCHANT&&e.getRawSlot()==2&&(marked(top.getItem(0))||marked(top.getItem(1))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interactEntity(PlayerInteractEntityEvent e){
        ItemStack item=e.getHand()==EquipmentSlot.OFF_HAND?e.getPlayer().getInventory().getItemInOffHand():e.getPlayer().getInventory().getItemInMainHand();
        if(marked(item))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interactAtEntity(PlayerInteractAtEntityEvent e){interactEntity(e);}
    @EventHandler public void craft(PrepareItemCraftEvent e){for(ItemStack item:e.getInventory().getMatrix())if(marked(item)){e.getInventory().setResult(null);break;}}
    @EventHandler public void anvil(PrepareAnvilEvent e){if(marked(e.getInventory().getItem(0))||marked(e.getInventory().getItem(1)))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(marked(e.getItemInHand()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispense(BlockDispenseEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void smelt(FurnaceSmeltEvent e){if(marked(e.getSource()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(FurnaceBurnEvent e){if(marked(e.getFuel()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void brewFuel(BrewingStandFuelEvent e){if(marked(e.getFuel()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void brew(BrewEvent e){for(ItemStack item:e.getContents().getContents())if(marked(item)){e.setCancelled(true);break;}}

    private boolean ready(Player p,Type type){
        int slot=pouchSlot(p);if(slot<0)return false;long now=clock.getAsLong();String key=type.spec.cooldownKey();long cooldown=type.spec.cooldownMillis;
        NBTTagCompound d=data(p.getInventory().getItem(slot));
        File file=new File(cooldownDirectory,p.getUniqueId()+".yml");
        try{
            YamlConfiguration journal=cooldowns.get(p.getUniqueId());
            if(journal==null){journal=new YamlConfiguration();if(file.exists())journal.load(file);cooldowns.put(p.getUniqueId(),journal);}
            // Keep the original Crown's mercy/<uuid>.yml:lastUse contract.
            String journalKey=type==Type.CROWN_OF_MERCY?"lastUse":key;
            long last=Math.max(d.getLong(key),journal.getLong(journalKey));
            if(last>0&&(now<last||now-last<cooldown)){
                // Import a legacy pouch's pending timer before a fresh pouch can be substituted.
                if(last>journal.getLong(journalKey)){
                    YamlConfiguration migrated=new YamlConfiguration();migrated.loadFromString(journal.saveToString());migrated.set(journalKey,last);
                    RoomStore.atomic(file,migrated.saveToString());cooldowns.put(p.getUniqueId(),migrated);
                }
                return false;
            }
            YamlConfiguration next=new YamlConfiguration();next.loadFromString(journal.saveToString());next.set(journalKey,now);
            RoomStore.atomic(file,next.saveToString());cooldowns.put(p.getUniqueId(),next);
            p.getInventory().setItem(slot,edit(p.getInventory().getItem(slot),n->n.setLong(key,now)));return true;
        }catch(Exception ex){plugin.getLogger().warning("DUNGEON_RELIC_STORAGE_UNAVAILABLE "+type.name());return false;}
    }
    static void heal(Player p,double amount){if(!p.isDead()&&amount>0)p.setHealth(Math.min(maxHealth(p),p.getHealth()+amount));}
    private static double maxHealth(LivingEntity e){return e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();}
    private static boolean hostile(Entity e){return e instanceof Monster||e instanceof Slime||e instanceof Shulker||e instanceof Ghast;}
    private static boolean fire(EntityDamageEvent.DamageCause c){return c==EntityDamageEvent.DamageCause.FIRE||c==EntityDamageEvent.DamageCause.FIRE_TICK||c==EntityDamageEvent.DamageCause.LAVA||c==EntityDamageEvent.DamageCause.HOT_FLOOR;}
    private static boolean melee(EntityDamageEvent.DamageCause c){return c==EntityDamageEvent.DamageCause.ENTITY_ATTACK||c==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;}
    private static boolean explosion(EntityDamageEvent.DamageCause c){return c==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||c==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;}
    private static boolean armored(LivingEntity entity){
        if(entity.getEquipment()==null)return false;
        for(ItemStack item:entity.getEquipment().getArmorContents())if(item!=null&&item.getType()!=Material.AIR)return true;
        return false;
    }
    private static boolean wornArmor(ItemStack item){
        String name=item.getType().name();
        return (name.endsWith("_HELMET")||name.endsWith("_CHESTPLATE")||name.endsWith("_LEGGINGS")||name.endsWith("_BOOTS"))
            &&item.getDurability()>item.getType().getMaxDurability()*.75;
    }
    private static Entity source(EntityDamageByEntityEvent e){
        Entity source=e.getDamager();if(source instanceof Projectile){ProjectileSource shooter=((Projectile)source).getShooter();return shooter instanceof Entity?(Entity)shooter:null;}return source;
    }
    private boolean sameArena(Entity a,Entity b){
        if(a==null||b==null||!a.getWorld().equals(b.getWorld()))return false;
        return !plugin.inside(a.getWorld())||plugin.roomKey(a.getLocation()).equals(plugin.roomKey(b.getLocation()));
    }
    private static boolean ordinaryEquipment(ItemStack item){
        if(item==null||item.getType().getMaxDurability()<=0||marked(item))return false;
        ItemMeta meta=item.getItemMeta();if(meta!=null&&meta.isUnbreakable())return false;
        if(meta!=null&&meta.hasLore()){
            List<String> lore=meta.getLore();
            if(lore.size()!=2||!lore.get(1).startsWith(ChatColor.DARK_GRAY+"The Penitent Below | Threat "))return false;
        }
        // Other plugins' NBT gear is not ours to repair or intercept.
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        if(n.hasTag())for(String key:n.getTag().c())if(!Arrays.asList("display","ench","RepairCost","Unbreakable").contains(key))return false;
        return true;
    }
    private void grant(Player p,Type type,PotionEffectType potion,int ticks){
        if(p.hasPotionEffect(potion))return;if(type.spec.cooldownMillis>0&&!ready(p,type))return;
        p.addPotionEffect(new PotionEffect(potion,ticks,0));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void attack(EntityDamageByEntityEvent e){
        if(retaliating||e.isCancelled()||e.getDamage()<=0)return;
        Entity attacker=source(e);if(!(attacker instanceof Player)||!sameArena(attacker,e.getEntity()))return;
        Player p=(Player)attacker;boolean projectile=e.getDamager() instanceof Projectile;
        Set<Type> types=equipped(p);double factor=1;
        if(projectile&&types.contains(Type.HOLLOW_LENS))factor*=1.12;
        if(!projectile&&types.contains(Type.RUSTED_HALO)&&(e.getEntity() instanceof Zombie||e.getEntity() instanceof Skeleton))factor*=1.15;
        if(types.contains(Type.BLOOD_THREAD)&&p.getHealth()/maxHealth(p)<=.35)factor*=1.18;
        if(!projectile&&hostile(e.getEntity())){
            if(types.contains(Type.RUBY_BROOCH)&&((LivingEntity)e.getEntity()).getHealth()>=maxHealth((LivingEntity)e.getEntity()))factor*=1.12;
            if(types.contains(Type.PENANCE_COG)&&p.hasPotionEffect(PotionEffectType.WEAKNESS))factor*=1.18;
            EntityType target=e.getEntity().getType();
            if(types.contains(Type.SILVER_VERDICT)&&(target==EntityType.WITCH||target==EntityType.VINDICATOR||target==EntityType.EVOKER||target==EntityType.ILLUSIONER))factor*=1.15;
            if(types.contains(Type.IRON_WRIT)&&armored((LivingEntity)e.getEntity()))factor*=1.15;
            if(types.contains(Type.BELL_COUNTERWEIGHT)&&p.hasPotionEffect(PotionEffectType.SLOW))factor*=1.15;
            if(types.contains(Type.CARRION_TOKEN)&&((LivingEntity)e.getEntity()).getHealth()<=maxHealth((LivingEntity)e.getEntity())*.25)factor*=1.16;
            if(types.contains(Type.FOUNDRY_SLAG)&&e.getEntity().getFireTicks()>0)factor*=1.12;
            if(types.contains(Type.MENAGERIE_TAG)&&(target==EntityType.SPIDER||target==EntityType.CAVE_SPIDER||target==EntityType.SILVERFISH))factor*=1.15;
        }
        if(projectile&&hostile(e.getEntity())){
            if(types.contains(Type.SODDEN_BOOKMARK)&&e.getEntity() instanceof Guardian)factor*=1.15;
            if(types.contains(Type.ASTRAL_COMPASS)&&p.getLocation().distanceSquared(e.getEntity().getLocation())>=64)factor*=1.12;
        }
        e.setDamage(e.getDamage()*Math.min(1.36,factor));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hurt(EntityDamageEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player)||e.getDamage()<=0)return;
        Player p=(Player)e.getEntity();Set<Type> types=equipped(p);double damage=e.getDamage();EntityDamageEvent.DamageCause cause=e.getCause();
        if(cause==EntityDamageEvent.DamageCause.PROJECTILE&&types.contains(Type.SALT_TEAR))damage*=.85;
        if(cause==EntityDamageEvent.DamageCause.FALL&&types.contains(Type.PILGRIM_KNOT))damage*=.5;
        if(fire(cause)&&types.contains(Type.CINDER_HEART))damage*=.6;
        if((cause==EntityDamageEvent.DamageCause.MAGIC||cause==EntityDamageEvent.DamageCause.POISON||cause==EntityDamageEvent.DamageCause.WITHER)&&types.contains(Type.MOURNING_PEARL))damage*=.7;
        if((cause==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||cause==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)&&p.isBlocking()&&types.contains(Type.SALT_CENSER))damage*=.75;
        if(cause==EntityDamageEvent.DamageCause.DROWNING&&types.contains(Type.DIVER_SEAL))damage*=.5;
        if(cause==EntityDamageEvent.DamageCause.SUFFOCATION&&types.contains(Type.HANGMAN_LOOP))damage*=.6;
        if(cause==EntityDamageEvent.DamageCause.CONTACT&&types.contains(Type.WAX_SEAL))damage*=.55;
        if(cause==EntityDamageEvent.DamageCause.FALLING_BLOCK&&types.contains(Type.BASILICA_CHIP))damage*=.6;
        if(melee(cause)&&p.getHealth()>=maxHealth(p)&&types.contains(Type.FRACTURED_ICON))damage*=.8;
        if(melee(cause)&&p.isSneaking()&&types.contains(Type.VELVET_RIBBON))damage*=.85;
        if(cause==EntityDamageEvent.DamageCause.LIGHTNING&&types.contains(Type.AMBER_PRISM))damage*=.5;
        if(cause==EntityDamageEvent.DamageCause.DRAGON_BREATH&&types.contains(Type.OPAL_CABOCHON))damage*=.6;
        if(cause==EntityDamageEvent.DamageCause.FLY_INTO_WALL&&types.contains(Type.PALE_FEATHER))damage*=.55;
        if(explosion(cause)&&p.isOnGround()&&types.contains(Type.OBSIDIAN_CLASP))damage*=.85;
        if(cause==EntityDamageEvent.DamageCause.PROJECTILE&&!p.isOnGround()&&!p.isInsideVehicle()&&types.contains(Type.ORBIT_BEAD))damage*=.85;
        if(cause==EntityDamageEvent.DamageCause.FALL&&p.isSneaking()&&types.contains(Type.MOURNER_TREAD))damage-=2;
        Entity attacker=e instanceof EntityDamageByEntityEvent?source((EntityDamageByEntityEvent)e):null;
        if(attacker!=null&&sameArena(attacker,p)){
            EntityType enemy=attacker.getType();
            if(types.contains(Type.STAR_CHART)&&(enemy==EntityType.ENDERMAN||enemy==EntityType.ENDERMITE||enemy==EntityType.SHULKER))damage*=.8;
            MirrorMark mark=mirrors.get(p.getUniqueId());
            if(types.contains(Type.MIRROR_SHARD)&&mark!=null&&clock.getAsLong()<=mark.until&&mark.shooter.equals(attacker.getUniqueId())){damage*=.8;mirrors.remove(p.getUniqueId());}
        }
        // Preserve the original ward's damage categories; zero-damage hits do not spend it.
        if(e.getFinalDamage()>0&&types.contains(Type.WARDEN_EYE)&&ready(p,Type.WARDEN_EYE))damage*=.5;
        // The classic strongest pair already left 25% damage; flat reductions share that floor.
        e.setDamage(Math.max(e.getDamage()*.25,damage));
        if(e.getFinalDamage()>0&&e.getFinalDamage()>=p.getHealth()&&cause!=EntityDamageEvent.DamageCause.VOID&&types.contains(Type.CROWN_OF_MERCY)&&ready(p,Type.CROWN_OF_MERCY)){
            e.setCancelled(true);p.setHealth(1);p.setNoDamageTicks(60);p.saveData();p.sendMessage(ChatColor.GOLD+"Mercy remembers you. The crown will sleep for ten minutes.");
        }
    }
    /** Reactive effects run after damage modifiers and containment have accepted the hit. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void react(EntityDamageEvent e){
        if(retaliating||e.isCancelled()||e.getFinalDamage()<=0)return;
        Entity attacker=e instanceof EntityDamageByEntityEvent?source((EntityDamageByEntityEvent)e):null;
        if(attacker!=null&&!sameArena(attacker,e.getEntity()))return;
        if(attacker instanceof Player&&e instanceof EntityDamageByEntityEvent&&((EntityDamageByEntityEvent)e).getDamager() instanceof Projectile&&hostile(e.getEntity())){
            Player p=(Player)attacker;LivingEntity target=(LivingEntity)e.getEntity();
            if(has(p,Type.RIME_NEEDLE)&&!target.hasPotionEffect(PotionEffectType.SLOW)&&ready(p,Type.RIME_NEEDLE))target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,40,0));
        }
        if(attacker instanceof Player&&melee(e.getCause())&&hostile(e.getEntity())){
            Player p=(Player)attacker;LivingEntity target=(LivingEntity)e.getEntity();
            if(has(p,Type.SPORE_PENDANT)&&!target.hasPotionEffect(PotionEffectType.WEAKNESS)&&ready(p,Type.SPORE_PENDANT))target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
        }
        if(!(e.getEntity() instanceof Player))return;
        Player p=(Player)e.getEntity();Set<Type> types=equipped(p);
        if(fire(e.getCause())&&p.getFireTicks()>40&&types.contains(Type.EMBER_VIAL)&&ready(p,Type.EMBER_VIAL))p.setFireTicks(40);
        if(melee(e.getCause())&&types.contains(Type.BRASS_ESCAPEMENT))grant(p,Type.BRASS_ESCAPEMENT,PotionEffectType.SPEED,60);
        if(hostile(attacker)&&e.getCause()==EntityDamageEvent.DamageCause.PROJECTILE&&types.contains(Type.MUFFLED_CLAPPER))grant(p,Type.MUFFLED_CLAPPER,PotionEffectType.DAMAGE_RESISTANCE,40);
        if(hostile(attacker)&&melee(e.getCause()))cleanse(p,types,Type.VESTRY_PIN,PotionEffectType.WEAKNESS);
        if(hostile(attacker)&&e.getCause()==EntityDamageEvent.DamageCause.PROJECTILE&&types.contains(Type.MIRROR_SHARD)){
            if(ready(p,Type.MIRROR_SHARD))mirrors.put(p.getUniqueId(),new MirrorMark(attacker.getUniqueId(),clock.getAsLong()+3000));
        }
        if(hostile(attacker)&&melee(e.getCause())&&types.contains(Type.THORN_BROOCH)&&ready(p,Type.THORN_BROOCH)){
            retaliating=true;try{((LivingEntity)attacker).damage(1,p);}finally{retaliating=false;}
        }
    }
    @EventHandler public void kill(EntityDeathEvent e){
        Player p=e.getEntity().getKiller();if(p==null||!sameArena(p,e.getEntity()))return;
        Set<Type> types=equipped(p);
        if(types.contains(Type.MARROW_BEAD)&&ready(p,Type.MARROW_BEAD))heal(p,1);
        if(types.contains(Type.GRAVE_SEED)&&hostile(e.getEntity())&&p.getFoodLevel()<20&&ready(p,Type.GRAVE_SEED))p.setFoodLevel(Math.min(20,p.getFoodLevel()+2));
        if(hostile(e.getEntity())){
            if(types.contains(Type.CHALICE_CHAIN)&&p.getSaturation()<p.getFoodLevel()&&ready(p,Type.CHALICE_CHAIN))p.setSaturation(Math.min(p.getFoodLevel(),p.getSaturation()+1));
            cleanse(p,types,Type.FUNERAL_BUTTON,PotionEffectType.BLINDNESS);
            if(types.contains(Type.KEEPER_WHISTLE))grant(p,Type.KEEPER_WHISTLE,PotionEffectType.JUMP,80);
        }
    }
    @EventHandler(priority=EventPriority.HIGH) public void experience(PlayerExpChangeEvent e){
        int original=e.getAmount();
        if(e.getAmount()>0&&has(e.getPlayer(),Type.ASHEN_BOOKMARK))e.setAmount((int)Math.min(Integer.MAX_VALUE,(long)e.getAmount()+Math.min(3,e.getAmount()/10)));
        if(original>0&&original<=5&&has(e.getPlayer(),Type.PAUPER_COIN)&&ready(e.getPlayer(),Type.PAUPER_COIN))e.setAmount(e.getAmount()+2);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void durability(PlayerItemDamageEvent e){
        if(e.isCancelled()||e.getDamage()<=0||!ordinaryEquipment(e.getItem()))return;
        Player p=e.getPlayer();Set<Type> types=equipped(p);
        if(types.contains(Type.SCRIBE_QUILL)&&ready(p,Type.SCRIBE_QUILL))e.setDamage(e.getDamage()-1);
        if(e.getDamage()>0&&types.contains(Type.CUSTODIAN_RIVET)&&e.getItem().getType()==Material.SHIELD&&ready(p,Type.CUSTODIAN_RIVET))e.setDamage(Math.max(0,e.getDamage()-2));
        if(e.getDamage()>0&&types.contains(Type.TEMPERED_RIVET)&&wornArmor(e.getItem())&&ready(p,Type.TEMPERED_RIVET))e.setDamage(Math.max(0,e.getDamage()-2));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void regain(EntityRegainHealthEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player)||e.getAmount()<=0)return;
        Player p=(Player)e.getEntity();Set<Type> types=equipped(p);
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.SATIATED&&types.contains(Type.SURGEON_THIMBLE))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.25));
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC&&p.getHealth()<maxHealth(p)*.5&&types.contains(Type.CRIMSON_SUTURE))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.25));
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC_REGEN&&types.contains(Type.BAPTISM_DROP))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.2));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player))return;Player p=(Player)e.getEntity();
        boolean meal=e.getFoodLevel()>p.getFoodLevel();
        if(e.getFoodLevel()>p.getFoodLevel()&&e.getFoodLevel()<20&&has(p,Type.FASTING_SPOON)&&ready(p,Type.FASTING_SPOON))e.setFoodLevel(Math.min(20,e.getFoodLevel()+1));
        if(e.getFoodLevel()<p.getFoodLevel()&&has(p,Type.MYCELIAL_PAD)&&ready(p,Type.MYCELIAL_PAD))e.setFoodLevel(Math.min(p.getFoodLevel(),e.getFoodLevel()+1));
        if(meal&&p.getExhaustion()>0&&has(p,Type.GILDED_CRUMB)&&ready(p,Type.GILDED_CRUMB))p.setExhaustion(Math.max(0,p.getExhaustion()-1));
    }
    /** Encounters invokes this once after durably recording a combat-room clear; never grants items. */
    public void onClear(Player p){
        if(!plugin.inside(p.getWorld())||plugin.room(p.getLocation()).mobCount()==0)return;
        Set<Type> types=equipped(p);
        if(types.contains(Type.CONFESSOR_SEAL))heal(p,4);
        if(types.contains(Type.CHOIR_SHELL)){p.removePotionEffect(PotionEffectType.WEAKNESS);p.removePotionEffect(PotionEffectType.SLOW);}
        if(types.contains(Type.RELIQUARY_KEY)){
            ItemStack item=p.getInventory().getItemInMainHand();
            if(ordinaryEquipment(item)&&item.getDurability()>0){item=item.clone();item.setDurability((short)Math.max(0,item.getDurability()-12));p.getInventory().setItemInMainHand(item);}
        }
        if(types.contains(Type.SANCTUARY_ACORN))grant(p,Type.SANCTUARY_ACORN,PotionEffectType.DAMAGE_RESISTANCE,80);
        if(types.contains(Type.ABSOLUTION_MEDAL)&&p.getFoodLevel()<20&&ready(p,Type.ABSOLUTION_MEDAL))p.setFoodLevel(Math.min(20,p.getFoodLevel()+4));
        if(types.contains(Type.LAST_CANDLE)){p.removePotionEffect(PotionEffectType.WITHER);p.removePotionEffect(PotionEffectType.POISON);}
    }
    private void cleanse(Player p,Set<Type> types,Type relic,PotionEffectType potion){
        if(types.contains(relic)&&p.hasPotionEffect(potion)&&ready(p,relic))p.removePotionEffect(potion);
    }
    void tickPlayer(Player p){
        Set<Type> types=equipped(p);long now=clock.getAsLong();
        if(p.isDead()){resting.remove(p.getUniqueId());return;}
        cleanse(p,types,Type.THAWED_LOCKET,PotionEffectType.SLOW);
        cleanse(p,types,Type.QUARANTINE_MASK,PotionEffectType.POISON);
        cleanse(p,types,Type.NIGHT_TALLOW,PotionEffectType.BLINDNESS);
        cleanse(p,types,Type.VIGIL_WICK,PotionEffectType.WITHER);
        cleanse(p,types,Type.PRISMATIC_CLASP,PotionEffectType.CONFUSION);
        cleanse(p,types,Type.LABYRINTH_THREAD,PotionEffectType.HUNGER);
        if(types.contains(Type.SCRIBE_REED)&&p.getRemainingAir()<Math.min(100,p.getMaximumAir())&&ready(p,Type.SCRIBE_REED))p.setRemainingAir(Math.min(p.getMaximumAir(),p.getRemainingAir()+40));
        MirrorMark mirror=mirrors.get(p.getUniqueId());
        if(mirror!=null&&(now>mirror.until||!types.contains(Type.MIRROR_SHARD)))mirrors.remove(p.getUniqueId());
        if(types.contains(Type.ROOT_HEART)&&p.isOnGround()&&!p.isInsideVehicle()&&p.getFoodLevel()>=18){
            Rest rest=resting.get(p.getUniqueId());Location here=p.getLocation();
            if(rest==null||!rest.location.getWorld().equals(here.getWorld())||rest.location.distanceSquared(here)>.01){rest=new Rest(here.clone(),now);resting.put(p.getUniqueId(),rest);}
            if(now-rest.since>=8000&&p.getHealth()<maxHealth(p)&&ready(p,Type.ROOT_HEART))heal(p,1);
        }else resting.remove(p.getUniqueId());
        if(!plugin.inside(p.getWorld())){currentRoom.remove(p.getUniqueId());return;}
        Layout.Room r=plugin.room(p.getLocation());String key=plugin.roomKey(p.getLocation()),previous=currentRoom.put(p.getUniqueId(),key);Encounters.Run run=plugin.encounters.active.get(key);
        if(!key.equals(previous)&&run!=null&&!run.state.cleared&&r.mobCount()>0&&types.contains(Type.LAMB_BELL))grant(p,Type.LAMB_BELL,PotionEffectType.ABSORPTION,120);
    }
    public void tick(){
        Set<UUID> present=new HashSet<>();
        for(Player p:Bukkit.getOnlinePlayers()){present.add(p.getUniqueId());tickPlayer(p);}
        currentRoom.keySet().retainAll(present);resting.keySet().retainAll(present);cooldowns.keySet().retainAll(present);mirrors.keySet().retainAll(present);
    }
}
