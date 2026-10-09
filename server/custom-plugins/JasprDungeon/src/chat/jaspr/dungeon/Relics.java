package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

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
        ASTRAL_COMPASS, ORBIT_BEAD, LABYRINTH_THREAD, MOURNER_TREAD, ABSOLUTION_MEDAL, LAST_CANDLE,
        // Generation 7: explicit icon bands (scripts/trinket-art/catalog.cjs DUNGEON7_BANDS): the stone sword's 77..130, then the
        // stone spade's 66..123. Bands never move once given; the trophies' weapons hold the stone sword's 74..76 (Trophies).
        FOUNDERS_FORK(Skin.SWORD,77), BRONZE_TOLL(Skin.SWORD,78), MOTH_COCOON(Skin.SWORD,79), WINGDUST_PHIAL(Skin.SWORD,80), VOTIVE_STUB(Skin.SWORD,81),
        WRIGHTS_TAPER(Skin.SWORD,82), LIBRARIANS_CHAIN(Skin.SWORD,83), MARGIN_NOTE(Skin.SWORD,84), BATH_SPONGE(Skin.SWORD,85), PENITENT_PUMICE(Skin.SWORD,86),
        STONE_LIKENESS(Skin.SWORD,87), EFFIGY_WAX(Skin.SWORD,88), MARKET_LEDGER(Skin.SWORD,89), MOURNERS_OBOL(Skin.SWORD,90), WEEPING_BOUGH(Skin.SWORD,91),
        BITTER_FRUIT(Skin.SWORD,92), CASKET_NAIL(Skin.SWORD,93), CORRODED_SIGIL(Skin.SWORD,94), LANTERN_GLASS(Skin.SWORD,95), LAMPLIGHTERS_HOOK(Skin.SWORD,96),
        SALT_CELLAR(Skin.SWORD,97), COOKS_LADLE(Skin.SWORD,98), MIMES_GLOVE(Skin.SWORD,99), CURTAIN_CORD(Skin.SWORD,100), GUTTER_RAG(Skin.SWORD,101),
        RAT_KING_KNOT(Skin.SWORD,102), INCENSE_CONE(Skin.SWORD,103), SWINGING_THURIBLE(Skin.SWORD,104), GARDEN_BLOOM(Skin.SWORD,105), GARDENERS_TWINE(Skin.SWORD,106),
        HOSTEL_BLANKET(Skin.SWORD,107), PILGRIMS_TOKEN(Skin.SWORD,108), SEXTONS_MEASURE(Skin.SWORD,109), BURIAL_SHROUD(Skin.SWORD,110), UNLIT_WICK(Skin.SWORD,111),
        NAVE_VEIL(Skin.SWORD,112), MAGMA_GIZZARD(Skin.SWORD,113), BASALT_HEART(Skin.SWORD,114), STALACTITE_TOOTH(Skin.SWORD,115), CAVERN_ECHO(Skin.SWORD,116),
        GROTTO_CAP(Skin.SWORD,117), SPOREBURST_SAC(Skin.SWORD,118), PUMP_VALVE(Skin.SWORD,119), FLOODED_LANTERN(Skin.SWORD,120), RESONANT_CRYSTAL(Skin.SWORD,121),
        CRYSTAL_LATTICE(Skin.SWORD,122), CHITIN_PLATE(Skin.SWORD,123), VENOM_GLAND(Skin.SWORD,124), MARROW_FLUTE(Skin.SWORD,125), BONE_DICE(Skin.SWORD,126),
        SLING_STONE(Skin.SWORD,127), QUENCH_STONE(Skin.SWORD,128), SPRAY_VEIL(Skin.SWORD,129), LABYRINTH_CHALK(Skin.SWORD,130), BLAST_DAMPER(Skin.SHOVEL,66),
        FUSE_SNIPS(Skin.SHOVEL,67), SWITCHMANS_FLAG(Skin.SHOVEL,68), BRAKE_LEVER(Skin.SHOVEL,69), SULFUR_SALVE(Skin.SHOVEL,70), SPRING_FLASK(Skin.SHOVEL,71),
        QUARRY_WEDGE(Skin.SHOVEL,72), WORM_LURE(Skin.SHOVEL,73), FORGE_TEMPER(Skin.SHOVEL,74), RIVERBED_PEBBLE(Skin.SHOVEL,75), RUST_EATER_TOOTH(Skin.SHOVEL,76),
        GEODE_HEART(Skin.SHOVEL,77), ROOTDRINKER(Skin.SHOVEL,78), SMUGGLERS_SHIV(Skin.SHOVEL,79), CONTRABAND_PLATE(Skin.SHOVEL,80), COLUMN_CAPITAL(Skin.SHOVEL,81),
        BURROW_EMBER(Skin.SHOVEL,82), SMOLDERING_ZEAL(Skin.SHOVEL,83), TYRANTS_TALLY(Skin.SHOVEL,84), FOREMANS_BELL(Skin.SHOVEL,85), SENTINEL_RIVET(Skin.SHOVEL,86),
        COURTIERS_CLOAK(Skin.SHOVEL,87), KNEELERS_CUSHION(Skin.SHOVEL,88), SANGUINE_MERLON(Skin.SHOVEL,89), RAMPART_STONE(Skin.SHOVEL,90), VOID_THORN(Skin.SHOVEL,91),
        GRAVITY_SEED(Skin.SHOVEL,92), OBSIDIAN_SPLINTER(Skin.SHOVEL,93), SOULFIRE_WICK(Skin.SHOVEL,94), SOULFIRE_CENSER(Skin.SHOVEL,95), PENANCE_CHAIN(Skin.SHOVEL,96),
        SHACKLE_LINK(Skin.SHOVEL,97), SOUL_EMBER(Skin.SHOVEL,98), SKYFALL_TALON(Skin.SHOVEL,99), CROWDS_ROAR(Skin.SHOVEL,100), GLADIATORS_TORC(Skin.SHOVEL,101),
        HEADSMANS_LEDGER(Skin.SHOVEL,102), FERRYMANS_LANTERN(Skin.SHOVEL,103), PEARL_INDEX(Skin.SHOVEL,104), SERGEANTS_WHISTLE(Skin.SHOVEL,105), MUSTER_ROLL(Skin.SHOVEL,106),
        FOUNDRY_SIGHTS(Skin.SHOVEL,107), DOOM_RIVET(Skin.SHOVEL,108), SILVERED_RETORT(Skin.SHOVEL,109), UNMARRED_REFLECTION(Skin.SHOVEL,110), ASHEN_CROWN_SHARD(Skin.SHOVEL,111),
        THRONE_ASH(Skin.SHOVEL,112), GATEBREAKER_SIGIL(Skin.SHOVEL,113), ABYSSAL_KEYSTONE(Skin.SHOVEL,114), PYRE_URN(Skin.SHOVEL,115), STAR_IRON_LENS(Skin.SHOVEL,116),
        STARFALL_SHARD(Skin.SHOVEL,117), GRAVE_WIND_SHROUD(Skin.SHOVEL,118), MOLTEN_CORE(Skin.SHOVEL,119), CURSED_COIN(Skin.SHOVEL,120), BASTION_STANDARD(Skin.SHOVEL,121),
        BASTION_HORN(Skin.SHOVEL,122), VICTORS_LAUREL(Skin.SHOVEL,123);
        public final LootCatalog.Bauble spec;
        public final String title;
        public final Material material;
        public final int rank;
        public final String[] description;
        private final Material carrier;private final int icon;
        Type(){this(Skin.SWORD,0);}
        Type(Material carrier,int band){spec=LootCatalog.Bauble.valueOf(name());title=spec.title;material=Material.valueOf(spec.material);rank=spec.rank;description=spec.description.toArray(new String[0]);this.carrier=carrier;icon=band;}
        /**
         * The band of this bauble's own icon (scripts/trinket-art/catalog.cjs): the 72 classic baubles are their position plus one
         * on the stone sword; generation 7's carry the explicit band of their constant, so adding a bauble never moves another.
         */
        public int band(){return icon>0?icon:ordinal()+1;}
        /** The carrier tool whose band is the icon: the stone sword, or for later generation 7 baubles the stone spade. */
        public Material carrier(){return carrier;}
    }
    /** The Reliquary pouch's band: fixed right after the 72 classic baubles (generation 7's items have bands of their own). */
    static final int POUCH_BAND=73;
    private final DungeonPlugin plugin;
    private final LongSupplier clock;
    private final File cooldownDirectory;
    private final Map<UUID,String> currentRoom=new HashMap<>();
    private final Map<UUID,YamlConfiguration> cooldowns=new HashMap<>();
    private final Map<UUID,Rest> resting=new HashMap<>();
    private final Map<UUID,MirrorMark> mirrors=new HashMap<>();
    private boolean retaliating;
    // Generation 7: per-player combat memory for the new relics; every map is keyed by an online player and pruned by tick().
    /** The last monster that hurt a player (Founder's Tuning Fork, Mourner's Obol). */
    private final Map<UUID,Blow> struck=new HashMap<>();
    /** Kills in the room a player is in (Librarian's Chain). */
    private final Map<UUID,Streak> streaks=new HashMap<>();
    /** Accepted melee hits in a row on one monster (Resonant Crystal). */
    private final Map<UUID,Streak> combos=new HashMap<>();
    /** Monsters a player has already struck once, at most OPENED_MAX (Smuggler's Shiv). */
    private final Map<UUID,LinkedHashSet<UUID>> opened=new HashMap<>();
    private static final int OPENED_MAX=64;
    /** When a player was last hurt (Hostel Blanket). */
    private final Map<UUID,Long> hurtAt=new HashMap<>();
    /** Where a player stands still and since when (Margin Note). */
    private final Map<UUID,Rest> still=new HashMap<>();
    /** The room whose first monster blow is halved (Labyrinth Chalk). */
    private final Map<UUID,String> armed=new HashMap<>();
    /** A player's last three kills (Crowd's Roar). */
    private final Map<UUID,ArrayDeque<Long>> kills=new HashMap<>();
    /** Once-a-second and every-two-seconds effects that keep no journal (Sanguine Merlon, Grave-Wind Shroud). */
    private final Map<UUID,Long> leech=new HashMap<>(),aura=new HashMap<>();
    /** Harmful effects a cleansing relic may lift. Never night vision or glowing: those are never given to anyone. */
    static final PotionEffectType[] HARMFUL={PotionEffectType.SLOW,PotionEffectType.SLOW_DIGGING,PotionEffectType.CONFUSION,PotionEffectType.BLINDNESS,
        PotionEffectType.HUNGER,PotionEffectType.WEAKNESS,PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.LEVITATION,PotionEffectType.UNLUCK};
    private static final class MirrorMark {
        final UUID shooter;final long until;
        MirrorMark(UUID shooter,long until){this.shooter=shooter;this.until=until;}
    }
    /** A monster's blow on a player and when it landed. */
    private static final class Blow {
        final UUID monster;final long at;
        Blow(UUID monster,long at){this.monster=monster;this.at=at;}
    }
    private static final class Rest {
        final Location location;final long since;
        Rest(Location location,long since){this.location=location;this.since=since;}
    }
    /** A key (a room or a monster) and a count. */
    private static final class Streak {
        final String key;int count;
        Streak(String key){this.key=key;}
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
        // Baubles made before they had icons of their own are the vanilla stand-in item; upgrade() turns them into the carrier.
        try{Type t=Type.valueOf(d.getString("id"));return item.getType()==t.material||Skin.is(item,t.carrier(),t.band())?t:null;}catch(IllegalArgumentException ex){return null;}
    }
    public static boolean pouch(ItemStack item){
        NBTTagCompound d=data(item);
        return d!=null&&"pouch".equals(d.getString("kind"))&&(item.getType()==Material.RABBIT_HIDE||Skin.is(item,Skin.SWORD,POUCH_BAND))&&item.getAmount()==1&&!d.getString("uuid").isEmpty();
    }
    /** Any item this plugin marked: a relic, a pouch or (generation 7) a guardian's trophy. */
    static boolean marked(ItemStack item){return data(item)!=null;}
    /** True while a relic deals its own retaliation: other listeners (Trophies) must not treat that damage as a fresh swing. */
    boolean echoing(){return retaliating;}
    // ------------------------------------------------------------ own icons (owner 2026-10-05: every trinket has its own texture)
    /** The upgraded copy of a bauble or pouch made before its own icon (the vanilla stand-in item), or null when there is nothing to do. */
    static ItemStack upgraded(ItemStack item){
        NBTTagCompound d=data(item);if(d==null)return null;
        if("relic".equals(d.getString("kind"))){Type t=type(item);return t!=null&&item.getType()==t.material&&!Skin.is(item,t.carrier(),t.band())?Skin.apply(item,t.carrier(),t.band()):null;}
        if("pouch".equals(d.getString("kind"))&&item.getType()==Material.RABBIT_HIDE)return Skin.apply(item,Skin.SWORD,POUCH_BAND);
        return null;
    }
    /** Upgrades every old bauble and pouch in an inventory in place (same item, same data, new look); the number upgraded. */
    public static int upgrade(Inventory inventory){
        if(inventory==null)return 0;int n=0;
        for(int slot=0;slot<inventory.getSize();slot++){ItemStack up=upgraded(inventory.getItem(slot));if(up!=null){inventory.setItem(slot,up);n++;}}
        return n;
    }
    /** A trinket is never a tool: right-clicking a block with its stone sword or spade makes no path (the click itself still works). */
    @EventHandler(priority=EventPriority.HIGHEST) public void noToolUse(PlayerInteractEvent e){if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&Skin.carrier(e.getItem()))e.setUseItemInHand(Event.Result.DENY);}
    private void refresh(Player p,String why){
        int n=upgrade(p.getInventory());
        if(n>0)plugin.getLogger().info("DUNGEON_RELIC_ICONS upgraded="+n+" via="+why);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void iconsOnJoin(PlayerJoinEvent e){Player p=e.getPlayer();Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())refresh(p,"join");});}
    @EventHandler(priority=EventPriority.MONITOR) public void iconsOnOpen(InventoryOpenEvent e){
        if(e.getInventory().getHolder() instanceof Player)return;
        int n=upgrade(e.getInventory());if(n>0)plugin.getLogger().info("DUNGEON_RELIC_ICONS upgraded="+n+" via=container");
        if(e.getPlayer() instanceof Player)refresh((Player)e.getPlayer(),"open");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void iconsOnPickup(PlayerPickupItemEvent e){Player p=e.getPlayer();Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())refresh(p,"pickup");});}
    public static ItemStack create(Type t){
        ItemStack item=new ItemStack(t.material);ItemMeta m=item.getItemMeta();
        m.setDisplayName((t.rank>=4?ChatColor.GOLD:ChatColor.LIGHT_PURPLE)+t.title);
        // Generation 7: a deeper floor's relic says so; the victor's laurel is a trophy, favoured nowhere.
        int floor=t.spec.floor();boolean trophy=t.spec.trophy();
        List<String> lore=new ArrayList<>();lore.add(ChatColor.DARK_PURPLE+(trophy?"Dungeon trophy":"Dungeon-exclusive bauble")+" | Rank "+t.rank+(floor>1&&!trophy?" | Floor "+Floors.numeral(floor):""));
        for(String line:t.description)lore.add(ChatColor.GRAY+line);
        lore.add(ChatColor.DARK_GRAY+(trophy?"The victor's reward for conquering the Abyssal Citadel.":"Favored in "+LootCatalog.profile(t.spec.theme).theme+"."));
        lore.add(ChatColor.GRAY+"Equip inside a Dungeon Reliquary.");
        lore.add(ChatColor.DARK_GRAY+"Only found in the Dungeon Dimension.");m.setLore(lore);item.setItemMeta(m);
        return Skin.apply(edit(item,d->{d.setString("kind","relic");d.setString("id",t.name());d.setInt("version",1);}),t.carrier(),t.band());
    }
    public static ItemStack createPouch(){
        ItemStack item=new ItemStack(Material.RABBIT_HIDE);ItemMeta m=item.getItemMeta();m.setDisplayName(ChatColor.GOLD+"Dungeon Reliquary");
        m.setLore(Arrays.asList(ChatColor.GRAY+"Right-click to equip two distinct dungeon relics.",ChatColor.GRAY+"Carry in your main inventory to activate them.",
            ChatColor.GRAY+"Only your first pouch is active. Effects work in all worlds.",ChatColor.DARK_GRAY+"Contents travel with this pouch, including on death."));
        item.setItemMeta(m);return Skin.apply(edit(item,d->{d.setString("kind","pouch");d.setString("uuid",UUID.randomUUID().toString());d.setInt("version",1);}),Skin.SWORD,POUCH_BAND);
    }
    /** Generation 7: the room's floor picks the reliquary's relics (LootCatalog.pool by floor). */
    public static ItemStack roll(Layout.Room r){
        LootCatalog.Bauble b=LootCatalog.roll(r.hash,r.theme,r.tier,r.kind.name(),r.floor);return b==null?null:create(Type.valueOf(b.name()));
    }
    /** Generation 7: the second relic a Descent's or the Throne's reliquary holds; null for every other room. */
    public static ItemStack finale(Layout.Room r){
        if(!r.finale())return null;LootCatalog.Bauble b=LootCatalog.finaleRelic(r.hash,r.theme,r.tier,r.floor);return b==null?null:create(Type.valueOf(b.name()));
    }
    public int pouchSlot(Player p){int n=p.getInventory().getStorageContents().length;for(int s=0;s<n;s++)if(pouch(p.getInventory().getItem(s)))return s;return -1;}
    public Set<Type> equipped(Player p){
        int slot=pouchSlot(p);Set<Type> out=EnumSet.noneOf(Type.class);
        if(slot>=0){NBTTagCompound d=data(p.getInventory().getItem(slot));for(int s=0;s<2;s++)try{out.add(Type.valueOf(d.getString("slot"+s)));}catch(IllegalArgumentException ignored){}}
        return out;
    }
    public boolean has(Player p,Type t){return equipped(p).contains(t);}
    public void givePouch(Player p){
        if(pouchSlot(p)>=0){open(p);return;}if(p.getInventory().firstEmpty()<0){p.sendMessage("Make an inventory space for your Reliquary.");return;}
        p.getInventory().addItem(createPouch());p.saveData();p.sendMessage(ChatColor.GOLD+"A Dungeon Reliquary. Right-click it to equip two dungeon baubles.");
    }
    public static final class Menu implements InventoryHolder {
        final UUID player;final String pouchId;final Inventory inv;
        Menu(Player p,String id){player=p.getUniqueId();pouchId=id;inv=Bukkit.createInventory(this,27,"Dungeon Reliquary - 2 slots");}
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
        if(!marked(e.getItem()))return;
        // Owner 2026-10-03: every dungeon chest opens like a normal chest, relic or pouch in hand included; only the item's own use is refused.
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.getClickedBlock()!=null&&e.getClickedBlock().getType()==Material.CHEST){e.setUseItemInHand(Event.Result.DENY);return;}
        e.setCancelled(true);
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
    /** Generation 7: a carrier is a stone tool, so the enchanting table would take it; a relic or trophy is never enchanted. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void enchant(PrepareItemEnchantEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void enchanted(EnchantItemEvent e){if(marked(e.getItem()))e.setCancelled(true);}
    /** Generation 7: a relic or trophy is never a tool either: an unbreakable carrier would otherwise dig forever. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dig(BlockBreakEvent e){if(e.getPlayer().getGameMode()!=GameMode.CREATIVE&&marked(e.getPlayer().getInventory().getItemInMainHand()))e.setCancelled(true);}

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
    /** Generation 7: everything the dungeon spawns counts as a monster too (Rust Golems stand on iron golems, for one). */
    static boolean hostile(Entity e){return e instanceof Monster||e instanceof Slime||e instanceof Shulker||e instanceof Ghast||e!=null&&!(e instanceof Player)&&e.getScoreboardTags().contains(Encounters.TAG);}
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
            if(lore.size()!=2||!lore.get(1).startsWith(ChatColor.DARK_GRAY+Rewards.GEAR_ORIGIN)&&!lore.get(1).startsWith(ChatColor.DARK_GRAY+Rewards.LEGACY_GEAR_ORIGIN))return false;
        }
        // Other plugins' NBT gear is not ours to repair or intercept.
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);
        if(n.hasTag())for(String key:n.getTag().c())if(!Arrays.asList("display","ench","RepairCost","Unbreakable").contains(key))return false;
        return true;
    }
    private void grant(Player p,Type type,PotionEffectType potion,int ticks){
        if(p.hasPotionEffect(potion))return;if(type.spec.cooldownMillis>0&&!ready(p,type))return;
        p.addPotionEffect(new PotionEffect(potion,ticks,0,true,false));
    }
    // ------------------------------------------------------------ generation 7 helpers
    /** A silent effect for the wearer that never replaces a stronger or longer one of its kind; true when it was given. */
    private boolean grant(Player p,Type type,PotionEffectType potion,int ticks,int amplifier){
        PotionEffect existing=p.getPotionEffect(potion);
        if(existing!=null&&(existing.getAmplifier()>amplifier||existing.getAmplifier()==amplifier&&existing.getDuration()>=ticks))return false;
        if(type.spec.cooldownMillis>0&&!ready(p,type))return false;
        p.addPotionEffect(new PotionEffect(potion,ticks,amplifier,true,false),true);return true;
    }
    /** A monster's status, never shortening or weakening a stronger one from another source. */
    static void status(LivingEntity target,PotionEffectType type,int ticks,int amplifier){
        PotionEffect existing=target.getPotionEffect(type);
        if(existing==null||existing.getAmplifier()<amplifier||existing.getAmplifier()==amplifier&&existing.getDuration()<ticks)target.addPotionEffect(new PotionEffect(type,ticks,amplifier),true);
    }
    private static EncounterCatalog.Species species(Entity e){
        if(e==null)return null;
        for(String tag:e.getScoreboardTags())if(tag.startsWith(Encounters.SPECIES_TAG)){try{return EncounterCatalog.Species.valueOf(tag.substring(Encounters.SPECIES_TAG.length()));}catch(IllegalArgumentException ignored){}}
        return null;
    }
    /** A room's boss: slot 0 of a boss chamber, a floor's Descent or the Throne (Encounters tags every room mob with its slot). */
    private boolean boss(Entity e){return e!=null&&e.getScoreboardTags().contains("jpd_slot:0")&&plugin.inside(e.getWorld())&&plugin.room(e.getLocation()).bossRoom();}
    /** A floor's guardian or the Throne's sovereign. */
    private boolean guardian(Entity e){return boss(e)&&plugin.room(e.getLocation()).finale();}
    /** A boss's summons and other runtime adds (Encounters.summon tags them). */
    private static boolean summoned(Entity e){return e!=null&&e.getScoreboardTags().contains("jpd_summon");}
    private static boolean mutant(Entity e){EncounterCatalog.Species s=species(e);return s!=null&&s.mutant();}
    private static boolean gunner(Entity e){EncounterCatalog.Species s=species(e);return s==EncounterCatalog.Species.TUNNEL_GUNNER||s==EncounterCatalog.Species.ABYSSAL_GUNSLINGER||s==EncounterCatalog.Species.SQUAD_CAPTAIN;}
    private static boolean creeper(Entity e){EncounterCatalog.Species s=species(e);return e instanceof Creeper||s==EncounterCatalog.Species.MUTANT_CREEPER||s==EncounterCatalog.Species.CREEPER_MINION;}
    static boolean undead(Entity e){return e instanceof Zombie||e instanceof Skeleton||e instanceof Wither||e instanceof SkeletonHorse||e instanceof ZombieHorse;}
    private static boolean arthropod(Entity e){return e instanceof Spider||e instanceof Silverfish||e instanceof Endermite;}
    private static boolean golem(Entity e){return e instanceof IronGolem||e instanceof Snowman;}
    private static boolean inWater(Entity e){Material m=e.getLocation().getBlock().getType();return m==Material.WATER||m==Material.STATIONARY_WATER;}
    /** The real (scaled) height of a body, as the server sees it. */
    private static double height(Entity e){return ((CraftEntity)e).getHandle().length;}
    private static boolean dark(Player p){return p.getLocation().getBlock().getLightLevel()<=4;}
    private static boolean falling(Player p){return !p.isOnGround()&&p.getFallDistance()>0&&!p.isInsideVehicle();}
    private static float absorption(Player p){return ((CraftPlayer)p).getHandle().getAbsorptionHearts();}
    /** True when the attacker stands behind the monster: it faces more than a right angle away from them. */
    private static boolean behind(Player p,Entity foe){
        Vector facing=foe.getLocation().getDirection().setY(0),to=p.getLocation().toVector().subtract(foe.getLocation().toVector()).setY(0);
        return facing.lengthSquared()>1e-6&&to.lengthSquared()>1e-6&&facing.normalize().dot(to.normalize())<-.2;
    }
    private static boolean nearLava(Player p){
        Block feet=p.getLocation().getBlock();
        for(int dx=-2;dx<=2;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-2;dz<=2;dz++){Material m=feet.getRelative(dx,dy,dz).getType();if(m==Material.LAVA||m==Material.STATIONARY_LAVA)return true;}
        return false;
    }
    private boolean standingStill(Player p){Rest s=still.get(p.getUniqueId());return s!=null&&clock.getAsLong()-s.since>=1000&&s.location.getWorld().equals(p.getWorld())&&s.location.distanceSquared(p.getLocation())<=.01;}
    private boolean struckBy(Player p,Entity foe,long window){Blow s=struck.get(p.getUniqueId());return s!=null&&s.monster.equals(foe.getUniqueId())&&clock.getAsLong()-s.at<=window;}
    /** Monsters near a point in the player's own arena, nearest first, at most max (and none at all past 128 bodies nearby). */
    private List<LivingEntity> foes(Player p,Location at,double radius,int max,Entity except){
        List<LivingEntity> out=new ArrayList<>();Collection<Entity> near=at.getWorld().getNearbyEntities(at,radius,Math.max(2,radius),radius);if(near.size()>128)return out;
        for(Entity e:near){if(e==except||!(e instanceof LivingEntity)||!hostile(e)||e.isDead()||!e.isValid()||e.getLocation().distanceSquared(at)>radius*radius||!sameArena(p,e))continue;out.add((LivingEntity)e);}
        out.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)));return out.size()>max?new ArrayList<>(out.subList(0,max)):out;
    }
    /** A bounded push away from a point; bosses stand firm. The room keeps its monsters (Encounters puts strays back). */
    private void shove(Location from,LivingEntity foe,double strength){
        if(boss(foe))return;Vector push=foe.getLocation().toVector().subtract(from.toVector()).setY(0);
        if(push.lengthSquared()<1e-4)return;foe.setVelocity(push.normalize().multiply(strength).setY(.25));
    }
    /** A relic's own blow: never answered by another relic or a trophy (no recursion). */
    private void strike(Player p,LivingEntity foe,double amount){
        if(amount<=0||foe.isDead()||!foe.isValid())return;
        retaliating=true;try{foe.damage(amount,p);}finally{retaliating=false;}
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
        if(hostile(e.getEntity())&&e.getEntity() instanceof LivingEntity)factor*=outgoing(p,types,(LivingEntity)e.getEntity(),projectile,e.getCause());
        e.setDamage(e.getDamage()*Math.min(1.36,factor));
    }
    /** Generation 7's outgoing relics against monsters; they share the classic 1.36 cap. */
    private double outgoing(Player p,Set<Type> types,LivingEntity foe,boolean projectile,EntityDamageEvent.DamageCause cause){
        double f=1;
        if(!projectile){
            if(types.contains(Type.FOUNDERS_FORK)&&struckBy(p,foe,3000))f*=1.18;
            Streak chain=streaks.get(p.getUniqueId());
            if(types.contains(Type.LIBRARIANS_CHAIN)&&chain!=null&&chain.key.equals(plugin.roomKey(p.getLocation())))f*=1+Math.min(.12,.03*chain.count);
            if(types.contains(Type.MIMES_GLOVE)&&behind(p,foe))f*=1.2;
            if(types.contains(Type.SWINGING_THURIBLE)&&cause==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)f*=1.25;
            if(types.contains(Type.RAT_KING_KNOT)&&height(foe)<1)f*=1.18;
            if(types.contains(Type.CORRODED_SIGIL)&&foe instanceof WitherSkeleton)f*=1.15;
            if(types.contains(Type.UNLIT_WICK)&&dark(p))f*=1.15;
            Streak combo=combos.get(p.getUniqueId());
            if(types.contains(Type.RESONANT_CRYSTAL)&&combo!=null&&combo.key.equals(foe.getUniqueId().toString())&&combo.count==2)f*=1.3;
            if(types.contains(Type.FUSE_SNIPS)&&creeper(foe))f*=1.25;
            if(types.contains(Type.QUARRY_WEDGE)&&height(foe)>3)f*=1.25;
            if(types.contains(Type.RUST_EATER_TOOTH)&&golem(foe))f*=1.25;
            Set<UUID> seen=opened.get(p.getUniqueId());
            if(types.contains(Type.SMUGGLERS_SHIV)&&(seen==null||!seen.contains(foe.getUniqueId())))f*=1.35;
            if(types.contains(Type.SKYFALL_TALON)&&falling(p))f*=1.2;
            if(types.contains(Type.UNMARRED_REFLECTION)&&p.getHealth()>=maxHealth(p))f*=1.15;
        }else{
            if(types.contains(Type.CAVERN_ECHO)&&foe.getLocation().getY()-p.getLocation().getY()>=3)f*=1.15;
            if(types.contains(Type.MARROW_FLUTE)&&foe instanceof Skeleton)f*=1.2;
            if(types.contains(Type.FOUNDRY_SIGHTS)&&p.getLocation().distanceSquared(foe.getLocation())<=16)f*=1.15;
        }
        if(types.contains(Type.PUMP_VALVE)&&inWater(foe))f*=1.2;
        if(types.contains(Type.QUENCH_STONE)&&(foe instanceof Blaze||foe instanceof MagmaCube))f*=1.2;
        if(types.contains(Type.GEODE_HEART)&&absorption(p)>0)f*=1.15;
        if(types.contains(Type.SMOLDERING_ZEAL)&&p.getFireTicks()>0)f*=1.2;
        if(types.contains(Type.TYRANTS_TALLY)&&boss(foe))f*=1.15;
        if(types.contains(Type.VOID_THORN)&&foe instanceof Enderman)f*=1.2;
        if(types.contains(Type.SHACKLE_LINK)&&foe.hasPotionEffect(PotionEffectType.SLOW))f*=1.18;
        if(types.contains(Type.SERGEANTS_WHISTLE)&&summoned(foe))f*=1.25;
        if(types.contains(Type.DOOM_RIVET)&&mutant(foe))f*=1.2;
        if(types.contains(Type.GATEBREAKER_SIGIL)&&guardian(foe))f*=1.25;
        if(types.contains(Type.CURSED_COIN))f*=1.25;
        if(types.contains(Type.BASTION_HORN)&&foes(p,p.getLocation(),5,3,null).size()>=3)f*=1.15;
        if(types.contains(Type.VICTORS_LAUREL)&&boss(foe))f*=1.15;
        return f;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hurt(EntityDamageEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player)||e.getDamage()<=0)return;
        Player p=(Player)e.getEntity();Set<Type> types=equipped(p);double damage=e.getDamage();EntityDamageEvent.DamageCause cause=e.getCause();
        Entity attacker=e instanceof EntityDamageByEntityEvent?source((EntityDamageByEntityEvent)e):null;
        boolean monster=attacker!=null&&hostile(attacker)&&sameArena(attacker,p);
        // Generation 7: the Obsidian Splinter turns a monster's blow aside entirely, before anything else weighs it.
        if(monster&&e.getFinalDamage()>0&&types.contains(Type.OBSIDIAN_SPLINTER)&&ready(p,Type.OBSIDIAN_SPLINTER)){e.setCancelled(true);p.sendMessage(ChatColor.DARK_PURPLE+"The Obsidian Splinter turns the blow aside.");return;}
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
        if(attacker!=null&&sameArena(attacker,p)){
            EntityType enemy=attacker.getType();
            if(types.contains(Type.STAR_CHART)&&(enemy==EntityType.ENDERMAN||enemy==EntityType.ENDERMITE||enemy==EntityType.SHULKER))damage*=.8;
            MirrorMark mark=mirrors.get(p.getUniqueId());
            if(types.contains(Type.MIRROR_SHARD)&&mark!=null&&clock.getAsLong()<=mark.until&&mark.shooter.equals(attacker.getUniqueId())){damage*=.8;mirrors.remove(p.getUniqueId());}
        }
        // ------------------------------------------------------------ generation 7
        if(cause==EntityDamageEvent.DamageCause.HOT_FLOOR&&types.contains(Type.PENITENT_PUMICE))damage*=.5;
        if(cause==EntityDamageEvent.DamageCause.PROJECTILE&&p.isSneaking()&&types.contains(Type.EFFIGY_WAX))damage*=.7;
        if(cause==EntityDamageEvent.DamageCause.PROJECTILE&&p.isSprinting()&&types.contains(Type.SWITCHMANS_FLAG))damage*=.75;
        if(types.contains(Type.MARGIN_NOTE)&&standingStill(p))damage*=.75;
        if(types.contains(Type.FLOODED_LANTERN)&&inWater(p))damage*=.75;
        if(monster){
            if(types.contains(Type.WINGDUST_PHIAL)&&(attacker instanceof Vex||attacker instanceof Blaze||attacker instanceof Ghast))damage*=.8;
            if(types.contains(Type.BURIAL_SHROUD)&&undead(attacker))damage*=.85;
            if(types.contains(Type.NAVE_VEIL)&&dark(p))damage*=.8;
            if(types.contains(Type.MAGMA_GIZZARD)&&attacker instanceof MagmaCube)damage*=.65;
            if(types.contains(Type.CHITIN_PLATE)&&arthropod(attacker))damage*=.8;
            if(types.contains(Type.BLAST_DAMPER)&&explosion(cause)&&creeper(attacker))damage*=.6;
            if(types.contains(Type.CONTRABAND_PLATE)&&gunner(attacker))damage*=.75;
            if(types.contains(Type.SENTINEL_RIVET)&&attacker instanceof WitherSkeleton)damage*=.75;
            if(types.contains(Type.COURTIERS_CLOAK)&&boss(attacker))damage*=.85;
            if(types.contains(Type.RAMPART_STONE)&&p.getHealth()<maxHealth(p)*.25)damage*=.75;
            if(types.contains(Type.MUSTER_ROLL)&&summoned(attacker))damage*=.75;
            if(types.contains(Type.STAR_IRON_LENS)&&cause==EntityDamageEvent.DamageCause.PROJECTILE&&attacker.getLocation().distanceSquared(p.getLocation())>100)damage*=.7;
            if(types.contains(Type.MOLTEN_CORE)&&p.getFireTicks()>0)damage*=.8;
            if(types.contains(Type.CURSED_COIN))damage*=1.1;
            if(types.contains(Type.LABYRINTH_CHALK)&&plugin.roomKey(p.getLocation()).equals(armed.get(p.getUniqueId()))){damage*=.5;armed.remove(p.getUniqueId());}
            if(e.getFinalDamage()>0&&types.contains(Type.CRYSTAL_LATTICE)&&ready(p,Type.CRYSTAL_LATTICE))damage-=6;
        }
        // Preserve the original ward's damage categories; zero-damage hits do not spend it.
        if(e.getFinalDamage()>0&&types.contains(Type.WARDEN_EYE)&&ready(p,Type.WARDEN_EYE))damage*=.5;
        // The classic strongest pair already left 25% damage; flat reductions share that floor.
        e.setDamage(Math.max(e.getDamage()*.25,damage));
        // Generation 7: the Stone Likeness cuts a great blow down after the shared floor (it bounds what the floor leaves).
        if(e.getDamage()>12&&e.getFinalDamage()>0&&types.contains(Type.STONE_LIKENESS)&&ready(p,Type.STONE_LIKENESS))e.setDamage(8);
        // The victor's laurel: a blow that would leave fewer than 3 hearts leaves exactly 3, once every 3 minutes (never against the void).
        if(e.getFinalDamage()>0&&p.getHealth()>6&&e.getFinalDamage()>p.getHealth()-6&&cause!=EntityDamageEvent.DamageCause.VOID&&types.contains(Type.VICTORS_LAUREL)&&ready(p,Type.VICTORS_LAUREL)){
            e.setCancelled(true);p.setHealth(6);p.setNoDamageTicks(20);p.saveData();p.sendMessage(ChatColor.GOLD+"The Laurel of the Unbowed holds. It rests for three minutes.");return;
        }
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
            if(has(p,Type.SLING_STONE)&&ready(p,Type.SLING_STONE))status(target,PotionEffectType.WEAKNESS,60,0);
        }
        if(attacker instanceof Player&&melee(e.getCause())&&hostile(e.getEntity())){
            Player p=(Player)attacker;LivingEntity target=(LivingEntity)e.getEntity();
            if(has(p,Type.SPORE_PENDANT)&&!target.hasPotionEffect(PotionEffectType.WEAKNESS)&&ready(p,Type.SPORE_PENDANT))target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
            struck(p,target,e);
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
        hit(p,types,attacker,e);
    }
    /** Generation 7: what a player's accepted melee hit on a monster sets off. */
    private void struck(Player p,LivingEntity target,EntityDamageEvent e){
        Set<Type> types=equipped(p);if(types.isEmpty())return;long now=clock.getAsLong();
        if(types.contains(Type.WRIGHTS_TAPER)&&ready(p,Type.WRIGHTS_TAPER))target.setFireTicks(Math.max(target.getFireTicks(),40));
        if(types.contains(Type.VENOM_GLAND)&&!undead(target)&&ready(p,Type.VENOM_GLAND))status(target,PotionEffectType.POISON,60,0);
        if(types.contains(Type.SOULFIRE_WICK)&&ready(p,Type.SOULFIRE_WICK))status(target,PotionEffectType.WITHER,60,0);
        if(types.contains(Type.SANGUINE_MERLON)&&now-leech.getOrDefault(p.getUniqueId(),0L)>=1000&&p.getHealth()<maxHealth(p)){leech.put(p.getUniqueId(),now);heal(p,Math.min(1,e.getFinalDamage()*.1));}
        if(types.contains(Type.GLADIATORS_TORC)&&boss(target))grant(p,Type.GLADIATORS_TORC,PotionEffectType.INCREASE_DAMAGE,60,0);
        if(types.contains(Type.RESONANT_CRYSTAL)){
            String id=target.getUniqueId().toString();Streak combo=combos.get(p.getUniqueId());
            if(combo==null||!combo.key.equals(id)){combo=new Streak(id);combos.put(p.getUniqueId(),combo);}
            combo.count=combo.count>=2?0:combo.count+1;
        }
        if(types.contains(Type.SMUGGLERS_SHIV)){
            LinkedHashSet<UUID> seen=opened.computeIfAbsent(p.getUniqueId(),k->new LinkedHashSet<>());seen.add(target.getUniqueId());
            if(seen.size()>OPENED_MAX){Iterator<UUID> it=seen.iterator();it.next();it.remove();}
        }
    }
    /** Generation 7: what an accepted hit on a player sets off (and the memory of who struck them). */
    private void hit(Player p,Set<Type> types,Entity attacker,EntityDamageEvent e){
        long now=clock.getAsLong();hurtAt.put(p.getUniqueId(),now);
        boolean monster=attacker!=null&&hostile(attacker)&&attacker instanceof LivingEntity;EntityDamageEvent.DamageCause cause=e.getCause();
        if(monster)struck.put(p.getUniqueId(),new Blow(attacker.getUniqueId(),now));
        if(types.isEmpty())return;
        double after=p.getHealth()-e.getFinalDamage(),max=maxHealth(p);
        if(monster&&after>0&&after<max*.5&&types.contains(Type.BRONZE_TOLL)){List<LivingEntity> near=foes(p,p.getLocation(),4,8,null);if(!near.isEmpty()&&ready(p,Type.BRONZE_TOLL))for(LivingEntity m:near)status(m,PotionEffectType.SLOW,60,0);}
        if(after>0&&after<8&&types.contains(Type.MOTH_COCOON))grant(p,Type.MOTH_COCOON,PotionEffectType.ABSORPTION,120,1);
        if(monster&&melee(cause)&&types.contains(Type.LANTERN_GLASS)&&ready(p,Type.LANTERN_GLASS))attacker.setFireTicks(Math.max(attacker.getFireTicks(),40));
        if(monster&&types.contains(Type.INCENSE_CONE)){List<LivingEntity> near=foes(p,p.getLocation(),3,8,null);if(!near.isEmpty()&&ready(p,Type.INCENSE_CONE))for(LivingEntity m:near)status(m,PotionEffectType.WEAKNESS,60,0);}
        if(monster&&melee(cause)&&types.contains(Type.GARDENERS_TWINE)&&ready(p,Type.GARDENERS_TWINE))status((LivingEntity)attacker,PotionEffectType.SLOW,30,2);
        if(fire(cause)&&types.contains(Type.BASALT_HEART))grant(p,Type.BASALT_HEART,PotionEffectType.FIRE_RESISTANCE,120,0);
        if(cause==EntityDamageEvent.DamageCause.FALLING_BLOCK&&types.contains(Type.STALACTITE_TOOTH))grant(p,Type.STALACTITE_TOOTH,PotionEffectType.DAMAGE_RESISTANCE,80,0);
        if(after>0&&after<max/3&&types.contains(Type.BRAKE_LEVER))grant(p,Type.BRAKE_LEVER,PotionEffectType.SPEED,60,1);
        if(monster&&melee(cause)&&types.contains(Type.COLUMN_CAPITAL)&&!boss(attacker)&&ready(p,Type.COLUMN_CAPITAL))shove(p.getLocation(),(LivingEntity)attacker,.9);
        if(monster&&after>0&&after<max/3&&types.contains(Type.FOREMANS_BELL)){List<LivingEntity> near=foes(p,p.getLocation(),5,10,null);if(!near.isEmpty()&&ready(p,Type.FOREMANS_BELL))for(LivingEntity m:near){shove(p.getLocation(),m,1.1);status(m,PotionEffectType.SLOW,60,1);}}
        if(monster&&boss(attacker)&&types.contains(Type.KNEELERS_CUSHION))grant(p,Type.KNEELERS_CUSHION,PotionEffectType.DAMAGE_RESISTANCE,60,0);
        if(monster&&cause==EntityDamageEvent.DamageCause.PROJECTILE&&types.contains(Type.PEARL_INDEX)&&ready(p,Type.PEARL_INDEX))status((LivingEntity)attacker,PotionEffectType.SLOW,60,0);
        if(monster&&melee(cause)&&types.contains(Type.SILVERED_RETORT)&&ready(p,Type.SILVERED_RETORT))strike(p,(LivingEntity)attacker,Math.min(4,e.getFinalDamage()*.25));
        if(cause==EntityDamageEvent.DamageCause.FALL&&types.contains(Type.STARFALL_SHARD)){List<LivingEntity> near=foes(p,p.getLocation(),3,6,null);if(!near.isEmpty()&&ready(p,Type.STARFALL_SHARD))for(LivingEntity m:near)strike(p,m,Math.min(6,e.getFinalDamage()));}
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
            slain(p,types,e.getEntity());
        }
    }
    /** Generation 7: what a player's kill of a monster sets off. */
    private void slain(Player p,Set<Type> types,LivingEntity dead){
        if(types.isEmpty())return;long now=clock.getAsLong();
        if(types.contains(Type.LIBRARIANS_CHAIN)){String room=plugin.roomKey(p.getLocation());Streak chain=streaks.get(p.getUniqueId());if(chain==null||!chain.key.equals(room)){chain=new Streak(room);streaks.put(p.getUniqueId(),chain);}chain.count++;}
        if(types.contains(Type.MOURNERS_OBOL)&&struckBy(p,dead,5000)&&p.getHealth()<maxHealth(p)&&ready(p,Type.MOURNERS_OBOL))heal(p,2);
        if(types.contains(Type.CURTAIN_CORD))grant(p,Type.CURTAIN_CORD,PotionEffectType.SPEED,60,0);
        if(types.contains(Type.SEXTONS_MEASURE)){ItemStack held=p.getInventory().getItemInMainHand();if(ordinaryEquipment(held)&&held.getDurability()>0&&ready(p,Type.SEXTONS_MEASURE)){held=held.clone();held.setDurability((short)Math.max(0,held.getDurability()-3));p.getInventory().setItemInMainHand(held);}}
        if(types.contains(Type.GROTTO_CAP))grant(p,Type.GROTTO_CAP,PotionEffectType.REGENERATION,60,0);
        if(types.contains(Type.SPOREBURST_SAC)){List<LivingEntity> near=foes(p,dead.getLocation(),3,6,dead);near.removeIf(Relics::undead);if(!near.isEmpty()&&ready(p,Type.SPOREBURST_SAC))for(LivingEntity m:near)status(m,PotionEffectType.POISON,60,0);}
        if(types.contains(Type.BONE_DICE)&&p.getHealth()<maxHealth(p)&&ready(p,Type.BONE_DICE))heal(p,ThreadLocalRandom.current().nextInt(6)==0?4:1);
        if(types.contains(Type.WORM_LURE))grant(p,Type.WORM_LURE,PotionEffectType.ABSORPTION,120,0);
        if(types.contains(Type.BURROW_EMBER)&&grant(p,Type.BURROW_EMBER,PotionEffectType.FIRE_RESISTANCE,60,0))p.setFireTicks(0);
        if(types.contains(Type.PENANCE_CHAIN)){List<LivingEntity> next=foes(p,dead.getLocation(),4,1,dead);if(!next.isEmpty()&&ready(p,Type.PENANCE_CHAIN))strike(p,next.get(0),3);}
        if(types.contains(Type.SOUL_EMBER)){float held=absorption(p);if(held<8)((CraftPlayer)p).getHandle().setAbsorptionHearts(Math.min(8,held+2));}
        if(types.contains(Type.CROWDS_ROAR)){
            ArrayDeque<Long> times=kills.computeIfAbsent(p.getUniqueId(),k->new ArrayDeque<>());times.addLast(now);while(times.size()>3)times.removeFirst();
            if(times.size()==3&&now-times.peekFirst()<=10000&&grant(p,Type.CROWDS_ROAR,PotionEffectType.INCREASE_DAMAGE,100,0))times.clear();
        }
        if(types.contains(Type.HEADSMANS_LEDGER)&&boss(dead)&&ready(p,Type.HEADSMANS_LEDGER)){p.setHealth(maxHealth(p));for(PotionEffectType bad:HARMFUL)p.removePotionEffect(bad);}
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
        if(e.getDamage()>0&&types.contains(Type.FORGE_TEMPER)&&p.getHealth()<maxHealth(p)*.5)e.setDamage(0);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void regain(EntityRegainHealthEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player)||e.getAmount()<=0)return;
        Player p=(Player)e.getEntity();Set<Type> types=equipped(p);
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.SATIATED&&types.contains(Type.SURGEON_THIMBLE))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.25));
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC&&p.getHealth()<maxHealth(p)*.5&&types.contains(Type.CRIMSON_SUTURE))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.25));
        if(e.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC_REGEN&&types.contains(Type.BAPTISM_DROP))e.setAmount(e.getAmount()+Math.min(.5,e.getAmount()*.2));
        if(types.contains(Type.SPRING_FLASK)&&(p.hasPotionEffect(PotionEffectType.POISON)||p.hasPotionEffect(PotionEffectType.WITHER)))e.setAmount(e.getAmount()+Math.min(1,e.getAmount()*.3));
        if(types.contains(Type.THRONE_ASH)&&plugin.inside(p.getWorld())&&plugin.room(p.getLocation()).bossRoom())e.setAmount(e.getAmount()+Math.min(1,e.getAmount()*.2));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player))return;Player p=(Player)e.getEntity();
        boolean meal=e.getFoodLevel()>p.getFoodLevel();
        if(e.getFoodLevel()>p.getFoodLevel()&&e.getFoodLevel()<20&&has(p,Type.FASTING_SPOON)&&ready(p,Type.FASTING_SPOON))e.setFoodLevel(Math.min(20,e.getFoodLevel()+1));
        if(e.getFoodLevel()<p.getFoodLevel()&&has(p,Type.MYCELIAL_PAD)&&ready(p,Type.MYCELIAL_PAD))e.setFoodLevel(Math.min(p.getFoodLevel(),e.getFoodLevel()+1));
        if(meal&&p.getExhaustion()>0&&has(p,Type.GILDED_CRUMB)&&ready(p,Type.GILDED_CRUMB))p.setExhaustion(Math.max(0,p.getExhaustion()-1));
        // Generation 7. A meal's saturation is added after this event, on top of what is set here.
        if(e.getFoodLevel()<p.getFoodLevel()&&e.getFoodLevel()<6&&has(p,Type.SALT_CELLAR))e.setFoodLevel(Math.min(p.getFoodLevel(),6));
        if(meal&&p.getHealth()<maxHealth(p)*.5&&has(p,Type.BITTER_FRUIT)&&ready(p,Type.BITTER_FRUIT))heal(p,2);
        if(meal&&p.getSaturation()<e.getFoodLevel()&&has(p,Type.COOKS_LADLE)&&ready(p,Type.COOKS_LADLE))p.setSaturation(Math.min(e.getFoodLevel(),p.getSaturation()+2));
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
        // ------------------------------------------------------------ generation 7
        Layout.Room r=plugin.room(p.getLocation());
        if(types.contains(Type.VOTIVE_STUB))grant(p,Type.VOTIVE_STUB,PotionEffectType.FIRE_RESISTANCE,400,0);
        if(types.contains(Type.MARKET_LEDGER)&&r.tier>0)p.giveExp(2*r.tier);
        if(types.contains(Type.CASKET_NAIL)){
            ItemStack[] armor=p.getInventory().getArmorContents();boolean mended=false;
            for(int i=0;i<armor.length;i++){ItemStack piece=armor[i];if(ordinaryEquipment(piece)&&piece.getDurability()>0){piece=piece.clone();piece.setDurability((short)Math.max(0,piece.getDurability()-4));armor[i]=piece;mended=true;}}
            if(mended)p.getInventory().setArmorContents(armor);
        }
        if(types.contains(Type.GARDEN_BLOOM))grant(p,Type.GARDEN_BLOOM,PotionEffectType.REGENERATION,120,0);
        if(types.contains(Type.RIVERBED_PEBBLE)&&p.getSaturation()<p.getFoodLevel()&&ready(p,Type.RIVERBED_PEBBLE))p.setSaturation(Math.min(p.getFoodLevel(),p.getSaturation()+4));
        if(types.contains(Type.ROOTDRINKER))heal(p,Math.min(8,2*(r.mobCount()/4)));
        if(types.contains(Type.SOULFIRE_CENSER))for(PotionEffectType bad:HARMFUL)p.removePotionEffect(bad);
        if(types.contains(Type.PYRE_URN)&&r.bossRoom()&&grant(p,Type.PYRE_URN,PotionEffectType.HEALTH_BOOST,2400,0))heal(p,4);
    }
    private void cleanse(Player p,Set<Type> types,Type relic,PotionEffectType potion){
        if(types.contains(relic)&&p.hasPotionEffect(potion)&&ready(p,relic))p.removePotionEffect(potion);
    }
    void tickPlayer(Player p){
        Set<Type> types=equipped(p);long now=clock.getAsLong();
        if(p.isDead()){resting.remove(p.getUniqueId());still.remove(p.getUniqueId());return;}
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
        // ------------------------------------------------------------ generation 7 (every world, like the classic relics)
        if(types.contains(Type.MARGIN_NOTE)&&!p.isInsideVehicle()){
            Rest s=still.get(p.getUniqueId());Location here=p.getLocation();
            if(s==null||!s.location.getWorld().equals(here.getWorld())||s.location.distanceSquared(here)>.01)still.put(p.getUniqueId(),new Rest(here.clone(),now));
        }else still.remove(p.getUniqueId());
        if(types.contains(Type.BATH_SPONGE)&&inWater(p)&&p.getHealth()<maxHealth(p)&&ready(p,Type.BATH_SPONGE))heal(p,1);
        if(types.contains(Type.WEEPING_BOUGH)&&p.getHealth()<maxHealth(p)*.5)grant(p,Type.WEEPING_BOUGH,PotionEffectType.REGENERATION,80,0);
        cleanse(p,types,Type.GUTTER_RAG,PotionEffectType.SLOW_DIGGING);
        if(types.contains(Type.HOSTEL_BLANKET)&&now-hurtAt.getOrDefault(p.getUniqueId(),0L)>=10000&&p.getHealth()<maxHealth(p)&&ready(p,Type.HOSTEL_BLANKET))heal(p,1);
        cleanse(p,types,Type.SULFUR_SALVE,PotionEffectType.WEAKNESS);
        cleanse(p,types,Type.GRAVITY_SEED,PotionEffectType.LEVITATION);
        if(types.contains(Type.SPRAY_VEIL)&&nearLava(p))grant(p,Type.SPRAY_VEIL,PotionEffectType.FIRE_RESISTANCE,60,0);
        if(types.contains(Type.BASTION_STANDARD)&&foes(p,p.getLocation(),6,3,null).size()>=3)grant(p,Type.BASTION_STANDARD,PotionEffectType.DAMAGE_RESISTANCE,40,0);
        if(types.contains(Type.GRAVE_WIND_SHROUD)&&now-aura.getOrDefault(p.getUniqueId(),0L)>=2000){aura.put(p.getUniqueId(),now);for(LivingEntity m:foes(p,p.getLocation(),3,6,null))status(m,PotionEffectType.SLOW,50,0);}
        if(!plugin.inside(p.getWorld())){currentRoom.remove(p.getUniqueId());return;}
        Layout.Room r=plugin.room(p.getLocation());String key=plugin.roomKey(p.getLocation()),previous=currentRoom.put(p.getUniqueId(),key);Encounters.Run run=plugin.encounters.active.get(key);
        if(!key.equals(previous)&&run!=null&&!run.state.cleared&&r.mobCount()>0){
            if(types.contains(Type.LAMB_BELL))grant(p,Type.LAMB_BELL,PotionEffectType.ABSORPTION,120);
            // Generation 7: entering a room that still holds its monsters.
            if(types.contains(Type.LAMPLIGHTERS_HOOK))grant(p,Type.LAMPLIGHTERS_HOOK,PotionEffectType.SPEED,80,0);
            if(types.contains(Type.PILGRIMS_TOKEN))grant(p,Type.PILGRIMS_TOKEN,PotionEffectType.DAMAGE_RESISTANCE,60,0);
            if(types.contains(Type.LABYRINTH_CHALK)&&ready(p,Type.LABYRINTH_CHALK))armed.put(p.getUniqueId(),key);
            if(types.contains(Type.FERRYMANS_LANTERN))grant(p,Type.FERRYMANS_LANTERN,PotionEffectType.FIRE_RESISTANCE,120,0);
            if(types.contains(Type.ASHEN_CROWN_SHARD)&&r.bossRoom())grant(p,Type.ASHEN_CROWN_SHARD,PotionEffectType.INCREASE_DAMAGE,120,0);
            if(types.contains(Type.ABYSSAL_KEYSTONE)&&r.finale())grant(p,Type.ABYSSAL_KEYSTONE,PotionEffectType.ABSORPTION,600,3);
        }
        if(!key.equals(previous)){streaks.remove(p.getUniqueId());if(!key.equals(armed.get(p.getUniqueId())))armed.remove(p.getUniqueId());}
    }
    public void tick(){
        Set<UUID> present=new HashSet<>();
        for(Player p:Bukkit.getOnlinePlayers()){present.add(p.getUniqueId());tickPlayer(p);}
        currentRoom.keySet().retainAll(present);resting.keySet().retainAll(present);cooldowns.keySet().retainAll(present);mirrors.keySet().retainAll(present);
        struck.keySet().retainAll(present);streaks.keySet().retainAll(present);combos.keySet().retainAll(present);opened.keySet().retainAll(present);hurtAt.keySet().retainAll(present);
        still.keySet().retainAll(present);armed.keySet().retainAll(present);kills.keySet().retainAll(present);leech.keySet().retainAll(present);aura.keySet().retainAll(present);
    }
}
