package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.CraftingManager;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.IRecipe;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.RegistryMaterials;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Java 8 / CraftBukkit 1.12.2. All access and lifecycle calls run on the server thread. */
public final class Arsenal implements Listener {
    private static final String TAG = "JasprApocalypse";
    // Server-side NBT mark, never a display-name/lore check. Not a cryptographic signature:
    // operators with arbitrary NBT access are trusted, as they are for ApocalypseItems.
    private static final String MARK = "jaspr-arsenal-v1";
    private static final double STEP = 0.25;
    private static final double EPSILON = 0.000001;
    private static final int MAX_CANDIDATES = 256;

    private enum Pattern { STANDARD, SUPPRESSED, BURST, BRACED, DISTANT, STATIONARY, RAIL, PLASMA, FLECHETTE }

    private enum Gun {
        RIFLE("rifle", "Last Light", ChatColor.GOLD, 1560, 18, 1, 32, 64, 1, 1, 0, 300, 2400),
        SHOTGUN("shotgun", "Requiem", ChatColor.DARK_RED, 1550, 6, 2, 10, 28, 9, 1, 0.105, 1100, 3000),
        RAILGUN("railgun", "Gravebreaker", ChatColor.AQUA, 1540, 3, 8, 100, 96, 1, 3, 0, 2000, 4200),
        WHISPER("whisper", "Whisper", ChatColor.GRAY, 1530, 24, 2, 28, 72, 1, 1, 0, 240, 2600),
        TEMPEST("tempest", "Tempest", ChatColor.YELLOW, 1520, 24, 2, 38, 68, 1, 1, 0, 1600, 3200),
        BASTION("bastion", "Bastion", ChatColor.DARK_RED, 1510, 12, 4, 54, 52, 1, 2, 0, 650, 4600),
        LONGWATCH("longwatch", "Longwatch", ChatColor.GREEN, 1500, 8, 3, 58, 112, 1, 1, 0, 950, 2800),
        SUNLANCE("sunlance", "Sunlance", ChatColor.LIGHT_PURPLE, 1490, 6, 6, 30, 48, 3, 2, 0.018, 1250, 3800),
        ADJUDICATOR("adjudicator", "Adjudicator", ChatColor.GOLD, 1480, 10, 3, 46, 88, 1, 1, 0, 580, 3100),
        CYCLOPS("cyclops", "Cyclops", ChatColor.RED, 1470, 4, 5, 13, 22, 12, 1, 0.14, 1450, 4000),
        FROSTBITE("frostbite", "Frostbite", ChatColor.WHITE, 1460, 5, 5, 64, 82, 1, 4, 0, 1350, 3500),
        SEPULCHER("sepulcher", "Sepulcher Service Pistol", ChatColor.GRAY, 1450, 12, 1, 26, 46, 1, 1, 0, 380, 1800),
        VESPER("vesper", "Vesper Silenced Pistol", ChatColor.DARK_PURPLE, 1440, 9, 2, 29, 54, 1, 1, 0, 480, 2200, Pattern.SUPPRESSED),
        OSSUARY("ossuary", "Ossuary Hand Cannon", ChatColor.GOLD, 1430, 6, 3, 57, 58, 1, 1, 0, 920, 3300),
        TURNSTILE("turnstile", "Turnstile Snub Revolver", ChatColor.DARK_GRAY, 1420, 5, 2, 42, 36, 1, 1, 0, 620, 2400),
        CINDER("cinder", "Cinder Machine Pistol", ChatColor.RED, 1410, 20, 2, 24, 40, 1, 1, 0, 260, 2500),
        TUNNELRAT("tunnelrat", "Tunnel Rat Patrol Carbine", ChatColor.GREEN, 1400, 16, 2, 35, 58, 1, 1, 0, 430, 2700),
        BLACKBOX("blackbox", "Black Box Burst Carbine", ChatColor.DARK_AQUA, 1390, 21, 2, 33, 62, 1, 1, 0, 1650, 3100, Pattern.BURST),
        QUARANTINE("quarantine", "Quarantine Battle Carbine", ChatColor.YELLOW, 1380, 14, 3, 44, 70, 1, 1, 0, 600, 2900, Pattern.STATIONARY),
        SIGNAL("signal", "Signal Lost Marksman", ChatColor.AQUA, 1370, 7, 3, 53, 104, 1, 1, 0, 1050, 3200, Pattern.DISTANT),
        GALLOWS("gallows", "Gallows Bolt Rifle", ChatColor.DARK_RED, 1360, 5, 4, 72, 108, 1, 1, 0, 1500, 3400),
        WATCHTOWER("watchtower", "Watchtower Anti-Materiel", ChatColor.GOLD, 1350, 3, 6, 86, 112, 1, 2, 0, 2100, 4400, Pattern.BRACED),
        WHITEOUT("whiteout", "Whiteout Covert Rifle", ChatColor.WHITE, 1340, 6, 4, 61, 98, 1, 1, 0, 1350, 3300, Pattern.SUPPRESSED),
        BELLRINGER("bellringer", "Bellringer Double Barrel", ChatColor.GOLD, 1330, 2, 3, 12, 26, 10, 1, 0.11, 1350, 2600),
        LOCKJAW("lockjaw", "Lockjaw Breaching Pump", ChatColor.RED, 1320, 5, 3, 11, 32, 8, 1, 0.08, 1150, 3100),
        CHOIR("choir", "Choir Three-Barrel Verdict", ChatColor.LIGHT_PURPLE, 1310, 3, 5, 12, 24, 12, 1, 0.13, 1700, 3600),
        ASHFALL("ashfall", "Ashfall Drum Shotgun", ChatColor.DARK_GRAY, 1300, 8, 4, 10, 30, 8, 1, 0.095, 1000, 4100),
        NULLPOINT("nullpoint", "Null Point Needle Rail", ChatColor.AQUA, 1290, 4, 5, 68, 92, 1, 3, 0, 1650, 3600, Pattern.RAIL),
        CENOTAPH("cenotaph", "Cenotaph Siege Rail", ChatColor.DARK_PURPLE, 1280, 2, 8, 96, 106, 1, 3, 0, 2450, 4700, Pattern.RAIL),
        WITCHLIGHT("witchlight", "Witchlight Arc Projector", ChatColor.GREEN, 1270, 6, 5, 27, 44, 3, 2, 0.025, 1400, 3400, Pattern.PLASMA),
        STORMCOIL("stormcoil", "Stormcoil Induction Lance", ChatColor.BLUE, 1260, 4, 6, 74, 76, 1, 4, 0, 1850, 4100, Pattern.FLECHETTE),
        HEXBREAKER("hexbreaker", "Hexbreaker Reliquary Beam", ChatColor.LIGHT_PURPLE, 1250, 5, 5, 39, 64, 2, 2, 0.012, 1550, 3800, Pattern.PLASMA),
        PALLBEARER("pallbearer", "Pallbearer Belt Cannon", ChatColor.DARK_GRAY, 1240, 20, 4, 49, 60, 1, 2, 0, 700, 5000, Pattern.BRACED),
        IRONPSALM("ironpsalm", "Iron Psalm Rotary Gun", ChatColor.GOLD, 1230, 28, 3, 34, 48, 1, 1, 0, 380, 5200, Pattern.BRACED),
        DEADFREQUENCY("deadfrequency", "Dead Frequency Pulse Rifle", ChatColor.DARK_AQUA, 1220, 15, 4, 45, 74, 1, 1, 0, 1900, 4200, Pattern.BURST);

        final String id, title;
        final ChatColor color;
        final int durability, capacity, ammoCost, pellets, penetration;
        final double damage, range, spread;
        final long cooldownMs, reloadMs;
        final Pattern pattern;

        Gun(String id, String title, ChatColor color, int durability, int capacity, int ammoCost,
                double damage, double range, int pellets, int penetration, double spread,
                long cooldownMs, long reloadMs) {
            this(id, title, color, durability, capacity, ammoCost, damage, range, pellets, penetration,
                    spread, cooldownMs, reloadMs, Pattern.STANDARD);
        }

        Gun(String id, String title, ChatColor color, int durability, int capacity, int ammoCost,
                double damage, double range, int pellets, int penetration, double spread,
                long cooldownMs, long reloadMs, Pattern pattern) {
            this.id = id;
            this.title = title;
            this.color = color;
            this.durability = durability;
            this.capacity = capacity;
            this.ammoCost = ammoCost;
            this.damage = damage;
            this.range = range;
            this.pellets = pellets;
            this.penetration = penetration;
            this.spread = spread;
            this.cooldownMs = cooldownMs;
            this.reloadMs = reloadMs;
            this.pattern = pattern;
        }

        static Gun named(String id) {
            for (Gun gun : values()) if (gun.id.equals(id)) return gun;
            return null;
        }
    }

    private final ApocalypsePlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> readyAt = new HashMap<UUID, Long>();
    private final Map<UUID, Long> messageAt = new HashMap<UUID, Long>();
    private final Map<UUID, BukkitTask> reloading = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, List<BukkitTask>> bursting = new HashMap<UUID, List<BukkitTask>>();
    private final Map<NamespacedKey, String> recipeIds = new HashMap<NamespacedKey, String>();
    private final Map<MinecraftKey, IRecipe> ownedRecipes = new HashMap<MinecraftKey, IRecipe>();
    private boolean started;

    public Arsenal(ApocalypsePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        started = true;
        try {
            registerRecipes();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("Arsenal ready: " + Gun.values().length + " firearms; crafted and loot guns start empty.");
        } catch (RuntimeException failure) {
            stop();
            throw failure;
        }
    }

    public void stop() {
        started = false;
        HandlerList.unregisterAll(this);
        for (BukkitTask task : reloading.values()) task.cancel();
        reloading.clear();
        for (List<BukkitTask> tasks : bursting.values()) for (BukkitTask task : tasks) task.cancel();
        bursting.clear();
        readyAt.clear();
        messageAt.clear();
        // The main plugin registers this after Arsenal.start(). Scope cleanup to its exact key
        // and marked guide output; do not claim arbitrary recipes from our namespace.
        MinecraftKey guideKey = new MinecraftKey(new NamespacedKey(plugin, "field_guide").toString());
        IRecipe guideRecipe = CraftingManager.recipes.get(guideKey);
        if (guideRecipe != null && "guide".equals(ApocalypseItems.id(CraftItemStack.asBukkitCopy(guideRecipe.b())))) {
            ownedRecipes.put(guideKey, guideRecipe);
        }
        removeOwnedRecipes(ownedRecipes);
        ownedRecipes.clear();
        recipeIds.clear();
    }

    private void rememberRecipe(NamespacedKey key, String id) {
        MinecraftKey nativeKey = new MinecraftKey(key.toString());
        IRecipe nativeRecipe = CraftingManager.recipes.get(nativeKey);
        if (nativeRecipe == null) throw new IllegalStateException("Recipe registration missing: " + key);
        ownedRecipes.put(nativeKey, nativeRecipe);
        recipeIds.put(key, id);
    }

    private static void removeOwnedRecipes(Map<MinecraftKey, IRecipe> owned) {
        if (owned.isEmpty()) return;
        RegistryMaterials<MinecraftKey, IRecipe> current = CraftingManager.recipes;
        boolean present = false;
        for (Map.Entry<MinecraftKey, IRecipe> entry : owned.entrySet()) {
            if (current.get(entry.getKey()) == entry.getValue()) { present = true; break; }
        }
        if (!present) return;
        // Paper 1.12 RecipeIterator delegates to an unmodifiable RegistryID iterator. Neither
        // registry has a remove API. Build a filtered registry in one finite pass, then publish
        // it atomically on the server thread. Keep all other recipe objects, keys and numeric IDs
        // (including holes), and leave CraftingManager's next-ID counter untouched. No reset/init.
        RegistryMaterials<MinecraftKey, IRecipe> filtered = new RegistryMaterials<MinecraftKey, IRecipe>();
        for (MinecraftKey key : current.keySet()) {
            IRecipe recipe = current.get(key);
            if (owned.get(key) == recipe) continue;
            filtered.a(current.a(recipe), key, recipe);
        }
        CraftingManager.recipes = filtered;
    }

    /**
     * Salvage-grade weapons: what a walking corpse or a forgotten footlocker might still
     * be carrying. Sidearms and workshop tools only -- the Gravebreaker is never litter.
     */
    public static final List<String> SALVAGE_GUNS = Collections.unmodifiableList(Arrays.asList(
            "sepulcher", "turnstile", "cinder", "vesper", "whisper", "tunnelrat", "ossuary", "lockjaw"));
    public static final List<String> SALVAGE_MELEE = Collections.unmodifiableList(Arrays.asList(
            "trench_blade", "shock_baton", "gravespike", "wardcleaver", "suture_sickle",
            "vesper_dagger", "wire_whip", "railpick"));

    /** Server-side recipe and rare-loot factory. Every call issues a unique, empty firearm. */
    public static ItemStack weapon(String id) {
        Gun gun = Gun.named(id);
        if (gun == null) throw new IllegalArgumentException("Unknown arsenal weapon: " + id);
        ItemStack item = new ItemStack(Material.DIAMOND_HOE, 1, (short) gun.durability);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(gun.color + gun.title);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
        NBTTagCompound data = new NBTTagCompound();
        data.setString("id", gun.id);
        data.setString("arsenalMark", MARK);
        data.setString("serial", UUID.randomUUID().toString());
        data.setInt("rounds", 0);
        root.set(TAG, data);
        nms.setTag(root);
        return rounds(CraftItemStack.asBukkitCopy(nms), gun, 0);
    }

    public static Map<String, String> catalogue() {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Gun gun : Gun.values()) result.put(gun.id, gun.title);
        return Collections.unmodifiableMap(result);
    }

    /** Shared equipment eligibility; does not enable siege or terrain generation in liminal worlds. */
    static boolean equipmentWorld(ApocalypsePlugin plugin, World world) {
        return world != null && (plugin.enabledWorld(world) || "jaspr_backrooms".equals(world.getName())
                || world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END);
    }

    public static List<String> guideLines() {
        return Collections.unmodifiableList(Arrays.asList(
            "Main hand: right-click to fire; sneak + right-click to reload. Crafted and rare-loot guns start empty.",
            "Last Light: 32 damage, 64 blocks, 18 rounds; 1 iron nugget per round.",
            "Requiem: 9 x 10 damage pellets, 28 blocks, 6 shells; 2 iron nuggets per shell.",
            "Gravebreaker: 100 damage, 96 blocks, up to 3 bodies, 3 shots; 8 iron nuggets per shot.",
            "Armor, shields, PvP rules and damage protection apply; solid collision shapes stop all shots.",
            "All 35 firearms have distinct 3x3 bench recipes made only from ordinary vanilla items.",
            "Iron blocks form receivers; ingots form barrels; redstone controls triggers and gunpowder drives conventional actions.",
            "Repeaters control burst weapons, glass and quartz form optics, pistons work bolts, and hoppers feed heavy guns.",
            "Energy weapons replace gunpowder with redstone blocks, gold conductors and rarer vanilla minerals.",
            "No Echo Relic, Military Salvage, Weapon Core, Power Cell or other custom item is accepted in a gun recipe.",
            "Use the crafting-table recipe browser to search any gun by name and load its exact grid.",
            "Ammo is plain vanilla iron nuggets. Craft iron ingots into nuggets normally; marked Military Salvage is never consumed as ammunition.",
            "Reloads: 2.4s / 3s / 4.2s. Switching slots, dropping, inventory edits or travel cancels.",
            "Rare expedition caches also hold Whisper, Tempest, Bastion, Longwatch, Sunlance, Adjudicator, Cyclops and Frostbite.",
            "Tempest fires three aimed shots 0.55s apart; switching items or travel cancels the remaining shots.",
            "The expanded arsenal has 35 firearms: service pistols, revolvers, patrol carbines, bolt rifles, scatterguns, occult projectors and heavy weapons.",
            "Black Box and Dead Frequency also use tracked three-shot bursts. Vesper and Whiteout are suppressed. Every gun is craftable and can also appear empty in expedition loot.",
            "The built-in apocalypse models give each firearm a distinct appearance."
        ));
    }

    /** Compact, ready-to-append BookMeta pages; recipes use one ingredient per occupied slot. */
    public static String[] recipePages() {
        return new String[] {
            "FIREARM CRAFTING\n\nAll 35 firearms can be forged at a 3x3 bench using only vanilla items.\n\nOpen a crafting table and use its recipe browser. Search a gun by name, then click it to load the exact pattern.\n\nCrafted guns begin empty.",
            "FRAME LANGUAGE\n\nIron blocks: receivers\nIron ingots: barrels\nRedstone: triggers\nGunpowder: firing action\nRepeaters: burst control\nGlass/quartz: optics\nPistons: bolts\nHoppers: belt feeds\n\nAdvanced guns add gold, diamonds and redstone blocks.",
            "LAST LIGHT\n\nB B I\nS R N\nP G I\n\nB: Iron Block (2)\nI: Iron Ingot (2)\nS: Stick; R: Redstone\nN: Nether Star\nP: Gunpowder\nG: Gold Ingot\n\n18 rounds; 32 damage.",
            "REQUIEM\n\nB B I\nS R N\nP P B\n\nB: Iron Block (3)\nI: Iron Ingot\nS: Stick; R: Redstone\nN: Nether Star\nP: Gunpowder (2)\n\n6 shells; 9 x 10 damage.",
            "GRAVEBREAKER\n\nB B D\nG X N\nB Q I\n\nB: Iron Block (3)\nD: Diamond; G: Gold Block\nX: Redstone Block\nN: Nether Star\nQ: Quartz; I: Iron Ingot\n\n100 damage; pierces 3.",
            "PORTAL GUN\n\nB Q D\nQ X E\nB Q G\n\nB: Iron Block (2)\nQ: Quartz (3)\nD: Diamond\nX: Redstone Block\nE: Ender Pearl\nG: Gold Ingot\n\nVanilla materials only.",
            "AMMUNITION\n\nEvery firearm loads plain vanilla Iron Nuggets from your main inventory. Craft Iron Ingots into nuggets using the normal vanilla recipe.\n\nMarked Military Salvage is protected and is never consumed as ammunition.\n\nMost rounds cost 1 nugget; shells cost 2; Gravebreaker shots cost 8.",
            "LOADING & FIRING\n\nCrafted and rare-loot guns start empty. Keep iron nuggets in your main inventory.\n\nMain hand right-click: fire.\nSneak + right-click: reload.\n\nSwitching slots, dropping, inventory edits or world travel cancels reloads and bursts. Reload ammo is charged at completion.",
            "ARSENAL NOTES\n\nGun recipes accept plain vanilla ingredients only. Tagged custom items are rejected even when their base material looks correct.\n\nUse the exact 3x3 patterns, or mirror a pattern.\n\nWalls stop all shots. Armor, shields, PvP rules and protection still apply. Gunfire draws nearby zombies."
        };
    }

    private static NBTTagCompound data(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return new NBTTagCompound();
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        return nms.hasTag() ? nms.getTag().getCompound(TAG) : new NBTTagCompound();
    }

    private static Gun identify(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_HOE || item.getAmount() != 1) return null;
        Gun gun = Gun.named(ApocalypseItems.id(item));
        if (gun == null || item.getDurability() != gun.durability
                || !item.hasItemMeta() || !item.getItemMeta().isUnbreakable()) return null;
        NBTTagCompound data = data(item);
        return MARK.equals(data.getString("arsenalMark")) && !data.getString("serial").isEmpty()
                && data.hasKeyOfType("rounds", 3) && data.getInt("rounds") >= 0
                && data.getInt("rounds") <= capacity(item, gun) ? gun : null;
    }

    /** Public classification hook for server-side item systems; identity remains NBT-backed. */
    public static boolean isGun(ItemStack item) { return identify(item) != null; }

    // ---- Gunsmith upgrades (JasprRPG armament sheet, K on a gun) ------------------------------------------
    // JasprRPG spends a gun's level tokens and writes the ranks into the item's own JasprArmament.Abilities
    // compound; the four that change how the gun itself works are read here. NBT is the only contract between
    // the two plugins, so a gun keeps its upgrades when dropped, traded or stored.
    static int upgrade(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) return 0;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        if (nms == null || !nms.hasTag() || !nms.getTag().hasKeyOfType("JasprArmament", 10)) return 0;
        NBTTagCompound armament = nms.getTag().getCompound("JasprArmament");
        return armament.hasKeyOfType("Abilities", 10) ? Math.max(0, Math.min(5, armament.getCompound("Abilities").getInt(key))) : 0;
    }
    /** Extended Magazine: +20% capacity per rank (at least one round). */
    static int capacity(ItemStack item, Gun gun) {
        int rank = upgrade(item, "extended_mag");
        return rank == 0 ? gun.capacity : gun.capacity + Math.max(rank, (int) Math.round(gun.capacity * 0.2 * rank));
    }
    /** Speed Loader: -15% reload time per rank. */
    static long reloadMs(ItemStack item, Gun gun) { return Math.round(gun.reloadMs * (1 - 0.15 * upgrade(item, "speed_loader"))); }
    /** Hair Trigger: -10% time between shots per rank. */
    static long cooldownMs(ItemStack item, Gun gun) { return Math.round(gun.cooldownMs * (1 - 0.10 * upgrade(item, "hair_trigger"))); }
    /** Match Barrel: +15% range and -15% pellet spread per rank. */
    static double range(ItemStack item, Gun gun) { return gun.range * (1 + 0.15 * upgrade(item, "match_barrel")); }
    static double spread(ItemStack item, Gun gun) { return gun.spread * (1 - 0.15 * upgrade(item, "match_barrel")); }
    /** Arsenal owns the first lines of a gun lore; anything after them (the JasprRPG armament block) is kept. */
    private static final int OWN_LORE_LINES = 7;

    private static ItemStack rounds(ItemStack item, Gun gun, int count) {
        // Change lore before NBT, so the server's ItemMeta roundtrip cannot discard our update.
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        List<String> lore = new ArrayList<String>(Arrays.asList(ChatColor.GRAY + "Magazine: " + count + "/" + capacity(copy, gun),
                ChatColor.DARK_GRAY + "Right-click: fire | Sneak + right-click: reload",
                ChatColor.GRAY + "Ammo per round: " + gun.ammoCost,
                ChatColor.GRAY + "Damage: " + gun.pellets + " x " + gun.damage + " | Range: " + Math.round(range(copy, gun) * 10) / 10.0,
                ChatColor.GRAY + "Shot: " + cooldownMs(copy, gun) + "ms | Reload: " + reloadMs(copy, gun) + "ms",
                ChatColor.AQUA + specialty(gun),
                ChatColor.DARK_PURPLE + "Forged from the relics of a fallen world"));
        List<String> previous = meta.hasLore() ? meta.getLore() : null;
        if (previous != null && previous.size() > OWN_LORE_LINES) lore.addAll(previous.subList(OWN_LORE_LINES, previous.size()));
        meta.setLore(lore);
        copy.setItemMeta(meta);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(copy);
        NBTTagCompound root = nms.getTag();
        NBTTagCompound data = root.getCompound(TAG);
        data.setInt("rounds", count);
        root.set(TAG, data);
        nms.setTag(root);
        // Gear leaves this plugin plain. Whether it becomes an enhanced armament is decided
        // by JasprRPG when it actually reaches a player, which keeps the two decoupled.
        return CraftItemStack.asBukkitCopy(nms);
    }

    private boolean allowed(Player player) {
        return started && player != null && player.isOnline() && !player.isDead()
                && equipmentWorld(plugin, player.getWorld()) && plugin.isSurvivor(player)
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Gun gun = identify(event.getItem());
        if (gun == null) return;
        // Air interactions are commonly pre-cancelled by vanilla; the item-use result is authoritative.
        boolean denied = event.useItemInHand() == Event.Result.DENY
                || (action == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY);
        event.setCancelled(true); // Never till land or operate a block with a gun, including offhand.
        if (event.getHand() != EquipmentSlot.HAND || denied || !allowed(event.getPlayer())) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (reloading.containsKey(id) || bursting.containsKey(id)) return;
        Long ready = readyAt.get(id);
        if (ready != null && System.nanoTime() < ready) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (identify(held) != gun) return;
        if (player.isSneaking()) {
            reload(player, gun, held);
            return;
        }
        int count = data(held).getInt("rounds");
        if (count == 0) {
            hint(player, "Empty magazine. Sneak + right-click to reload with iron nuggets.");
            return;
        }
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        List<Target> candidates = targets(player, origin, direction, gun, range(held, gun), spread(held, gun));
        if (candidates == null) {
            hint(player, "Too many entities in the firing lane. Move to a clearer position.");
            return;
        }
        readyAt.put(id, System.nanoTime() + cooldownMs(held, gun) * 1000000L);
        player.getInventory().setItemInMainHand(rounds(held, gun, count - 1));
        fire(player, origin, direction, gun, candidates, range(held, gun), spread(held, gun));
        if ((gun == Gun.TEMPEST || gun.pattern == Pattern.BURST) && count > 1) burst(player, gun, held, count - 1);
    }

    private static String specialty(Gun gun) {
        if (gun.pattern != Pattern.STANDARD) switch (gun.pattern) {
            case SUPPRESSED: return "Suppressed: attracts zombies within 8 blocks";
            case BURST: return "3-shot tracked burst; 0.55s between shots";
            case BRACED: return "Brace still for full damage; 75% retained per body";
            case DISTANT: return "+35% damage beyond 32 blocks";
            case STATIONARY: return "+30% damage while stationary";
            case RAIL: return "Pierces 3 bodies; retains 80% per body";
            case PLASMA: return "Narrow occult fan; pierces 2 bodies, never walls";
            case FLECHETTE: return "Pierces 4 bodies; retains 70% per body";
            default: break;
        }
        switch (gun) {
            case WHISPER: return "Suppressed: attracts zombies within 8 blocks";
            case TEMPEST: return "3-shot tracked burst; 0.55s between shots";
            case BASTION: return "Pierces 2 bodies; brace still for full damage";
            case LONGWATCH: return "+35% damage beyond 32 blocks";
            case SUNLANCE: return "Three plasma rays; pierces 2 bodies";
            case ADJUDICATOR: return "+30% damage while stationary";
            case CYCLOPS: return "12-pellet close-range volley";
            case FROSTBITE: return "Pierces 4 bodies; retains 70% per body";
            default: return gun == Gun.RAILGUN ? "Pierces 3 bodies; retains 80% per body" :
                    gun.durability < 1460 ? (gun.pellets > 1 ? gun.pellets + "-pellet salvage scattergun" : "Recovered late-expedition firearm") : "Relic-forged firearm";
        }
    }

    private void burst(final Player player, final Gun gun, ItemStack held, int remaining) {
        final UUID id = player.getUniqueId();
        final String serial = data(held).getString("serial");
        final int slot = player.getInventory().getHeldItemSlot();
        final World world = player.getWorld();
        List<BukkitTask> tasks = new ArrayList<BukkitTask>();
        bursting.put(id, tasks);
        final int shots = Math.min(2, remaining);
        for (int i = 1; i <= shots; i++) {
            final boolean last = i == shots;
            final int expected = remaining - i + 1;
            tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (!allowed(player) || player.getWorld() != world || player.getInventory().getHeldItemSlot() != slot
                            || identify(item) != gun || !serial.equals(data(item).getString("serial"))
                            || data(item).getInt("rounds") != expected) { cancelReload(id); return; }
                    Location eye = player.getEyeLocation();
                    Vector direction = eye.getDirection().normalize();
                    List<Target> candidates = targets(player, eye, direction, gun, range(item, gun), spread(item, gun));
                    if (candidates == null) { cancelReload(id); return; }
                    player.getInventory().setItemInMainHand(rounds(item, gun, expected - 1));
                    fire(player, eye, direction, gun, candidates, range(item, gun), spread(item, gun));
                    if (last) bursting.remove(id);
                }
            }, i * 11L)); // Respect vanilla immunity without resetting noDamageTicks.
        }
    }

    private void reload(final Player player, final Gun gun, ItemStack item) {
        final int before = data(item).getInt("rounds");
        final int capacity = capacity(item, gun);
        final long reload = reloadMs(item, gun);
        if (before >= capacity) { hint(player, "Magazine already full."); return; }
        if (availableAmmo(player) < gun.ammoCost) { hint(player, "You need iron nuggets to load this."); return; }
        final UUID id = player.getUniqueId();
        final int slot = player.getInventory().getHeldItemSlot();
        final String serial = data(item).getString("serial");
        readyAt.put(id, System.nanoTime() + reload * 1000000L);
        hint(player, "Reloading " + gun.title + " (" + (reload / 1000.0) + "s)...");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.5f, 0.8f);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                reloading.remove(id);
                if (!allowed(player) || player.getInventory().getHeldItemSlot() != slot) return;
                ItemStack held = player.getInventory().getItemInMainHand();
                if (identify(held) != gun || !serial.equals(data(held).getString("serial"))
                        || data(held).getInt("rounds") != before) return;
                int added = Math.min(capacity - before, availableAmmo(player) / gun.ammoCost);
                if (added == 0) { hint(player, "Reload cancelled: out of iron nuggets."); return; }
                consumeAmmo(player, added * gun.ammoCost);
                player.getInventory().setItemInMainHand(rounds(held, gun, before + added));
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.15f);
                player.sendMessage(gun.color + gun.title + ChatColor.GRAY + ": " + (before + added) + "/" + capacity);
            }
        }, (reload + 49) / 50);
        reloading.put(id, task);
    }

    /**
     * Ammunition is a plain vanilla iron nugget, for every firearm.
     *
     * The marked check matters: Military Salvage is also an iron nugget, and without it
     * a player's salvage stack would quietly be shot out of a rifle.
     */
    private static boolean bullet(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_NUGGET) return false;
        String id = ApocalypseItems.id(item);
        return id == null || id.isEmpty();
    }

    private static int availableAmmo(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (bullet(item)) total += item.getAmount();
        }
        return total;
    }

    private static void consumeAmmo(Player player, int needed) {
        PlayerInventory inventory = player.getInventory();
        // Main storage only: never treat a vanilla lookalike, armor, or the offhand as ammunition.
        for (int slot = 0; slot < 36 && needed > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!bullet(item)) continue;
            int taken = Math.min(needed, item.getAmount());
            needed -= taken;
            if (taken == item.getAmount()) inventory.setItem(slot, null);
            else {
                ItemStack remainder = item.clone();
                remainder.setAmount(item.getAmount() - taken);
                inventory.setItem(slot, remainder);
            }
        }
        if (needed != 0) throw new IllegalStateException("Ammo changed during synchronous reload");
    }

    private List<Target> targets(Player shooter, Location eye, Vector direction, Gun gun, double range, double spread) {
        Location middle = eye.clone().add(direction.clone().multiply(range / 2));
        double padding = range * spread + 2;
        // Exactly one broad-phase entity query per trigger, shared by every pellet. The supplied
        // CraftWorld/World implementation checks loaded chunks before collecting entity lists.
        Collection<Entity> entities = eye.getWorld().getNearbyEntities(middle,
                Math.abs(direction.getX()) * range / 2 + padding,
                Math.abs(direction.getY()) * range / 2 + padding,
                Math.abs(direction.getZ()) * range / 2 + padding);
        if (entities.size() > MAX_CANDIDATES) return null; // Fail closed; don't omit a blocking body.
        List<Target> targets = new ArrayList<Target>();
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity) || entity == shooter || entity instanceof ArmorStand
                    || !entity.isValid() || entity.isDead()) continue;
            if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR) continue;
            LivingEntity living = (LivingEntity) entity;
            targets.add(new Target(living, ((CraftLivingEntity) living).getHandle().getBoundingBox()));
        }
        return targets;
    }

    private void fire(Player player, Location eye, Vector direction, Gun gun, List<Target> candidates, double range, double spread) {
        Vector origin = eye.toVector();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < EPSILON) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(direction).normalize();
        Map<LivingEntity, Double> damage = new LinkedHashMap<LivingEntity, Double>();
        CollisionCache blocks = new CollisionCache(eye.getWorld());
        double tracer = range;
        for (int pellet = 0; pellet < gun.pellets; pellet++) {
            Vector ray = direction.clone();
            if (pellet != 0) {
                double radius = Math.sqrt(random.nextDouble()) * spread;
                double angle = random.nextDouble() * Math.PI * 2;
                ray.add(right.clone().multiply(Math.cos(angle) * radius));
                ray.add(up.clone().multiply(Math.sin(angle) * radius)).normalize();
            }
            double wall = wallDistance(blocks, origin, ray, range);
            List<Hit> hits = new ArrayList<Hit>();
            for (Target candidate : candidates) {
                double distance = intersection(origin, ray, candidate.box, wall);
                // A solid surface wins a tie; bodies behind even thin geometry cannot be hit.
                if (Double.isFinite(distance) && distance + EPSILON < wall) hits.add(new Hit(candidate.entity, distance));
            }
            Collections.sort(hits, new Comparator<Hit>() {
                @Override public int compare(Hit a, Hit b) { return Double.compare(a.distance, b.distance); }
            });
            int count = Math.min(gun.penetration, hits.size());
            for (int index = 0; index < count; index++) {
                Hit hit = hits.get(index);
                double amount = shotDamage(gun, hit.distance, index, player.getVelocity().lengthSquared() < 0.0064);
                Double previous = damage.get(hit.entity);
                damage.put(hit.entity, (previous == null ? 0 : previous) + amount);
            }
            if (pellet == 0) tracer = count == gun.penetration ? hits.get(count - 1).distance : wall;
        }
        for (Map.Entry<LivingEntity, Double> hit : damage.entrySet()) {
            LivingEntity target = hit.getKey();
            if (!target.isValid() || target.isDead()) continue;
            if (target instanceof Player && (!target.getWorld().getPVP() || !allowed((Player) target))) continue;
            // Aggregate a shotgun volley once per victim: preserve immunity ticks, armor, shields,
            // teams and EntityDamageByEntityEvent cancellation. Never set health or clear immunity.
            target.damage(hit.getValue(), player);
        }
        World world = eye.getWorld();
        boolean suppressed = gun == Gun.WHISPER || gun.pattern == Pattern.SUPPRESSED;
        boolean energy = gun == Gun.RAILGUN || gun == Gun.SUNLANCE || gun.pattern == Pattern.RAIL
                || gun.pattern == Pattern.PLASMA || gun.pattern == Pattern.FLECHETTE;
        world.playSound(eye, energy ? Sound.ENTITY_FIREWORK_BLAST : Sound.ENTITY_GENERIC_EXPLODE,
                suppressed ? 0.18f : 1.2f, gun == Gun.RIFLE || suppressed ? 1.8f : gun == Gun.SHOTGUN ? 1.2f : 0.6f);
        // Trace particles stay before the first wall and have no entity glow/full-bright effect.
        if (tracer > 0.15) world.spawnParticle(Particle.SMOKE_NORMAL,
                eye.clone().add(direction.clone().multiply(Math.min(0.65, tracer / 2))), 2, 0.025, 0.025, 0.025, 0);
        int sparks = gun == Gun.RAILGUN || gun.pattern == Pattern.RAIL ? 10 : 3;
        for (int i = 1; i <= sparks; i++) {
            if (tracer <= 0.15) break;
            Location point = eye.clone().add(direction.clone().multiply(tracer * i / (sparks + 1)));
            world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }
        // One stimulus per emitted shot; the siege director owns sensing, expiry and eligibility.
        plugin.noise(player,eye,suppressed ? 8 : 40);
    }

    private static double shotDamage(Gun gun, double distance, int body, boolean stationary) {
        double damage = gun.damage;
        if (gun == Gun.RAILGUN) damage *= Math.pow(0.8, body);
        if (gun == Gun.FROSTBITE) damage *= Math.pow(0.7, body);
        if (gun == Gun.BASTION) damage *= (stationary ? 1 : 0.7) * Math.pow(0.75, body);
        if (gun == Gun.LONGWATCH && distance > 32) damage *= 1.35;
        if (gun == Gun.ADJUDICATOR && stationary) damage *= 1.3;
        if (gun == Gun.SUNLANCE) damage *= Math.pow(0.65, body);
        // New profiles use the same bounded modifiers; originals above are deliberately unchanged.
        switch (gun.pattern) {
            case RAIL: damage *= Math.pow(0.8, body); break;
            case FLECHETTE: damage *= Math.pow(0.7, body); break;
            case BRACED: damage *= (stationary ? 1 : 0.7) * Math.pow(0.75, body); break;
            case DISTANT: if (distance > 32) damage *= 1.35; break;
            case STATIONARY: if (stationary) damage *= 1.3; break;
            case PLASMA: damage *= Math.pow(0.65, body); break;
            default: break;
        }
        return damage;
    }

    /** Exact slab intersection, including origins inside a hitbox and axis-parallel rays. */
    private static double intersection(Vector origin, Vector direction, AxisAlignedBB box, double limit) {
        double near = 0, far = limit;
        for (int axis = 0; axis < 3; axis++) {
            double position = axis == 0 ? origin.getX() : axis == 1 ? origin.getY() : origin.getZ();
            double velocity = axis == 0 ? direction.getX() : axis == 1 ? direction.getY() : direction.getZ();
            double low = axis == 0 ? box.a : axis == 1 ? box.b : box.c;
            double high = axis == 0 ? box.d : axis == 1 ? box.e : box.f;
            if (Math.abs(velocity) < EPSILON) {
                if (position < low || position > high) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (low - position) / velocity, b = (high - position) / velocity;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return near;
    }

    private static double wallDistance(CollisionCache cache, Vector origin, Vector direction, double range) {
        Set<BlockPosition> visited = new HashSet<BlockPosition>();
        double nearest = range;
        // Supercover each 0.25-block segment, rather than sampling points that could skip thin walls.
        for (int step = 0, steps = (int) Math.ceil(range / STEP); step < steps; step++) {
            double start = step * STEP, end = Math.min(range, start + STEP);
            Vector a = origin.clone().add(direction.clone().multiply(start));
            Vector b = origin.clone().add(direction.clone().multiply(end));
            if (a.getY() < 0 || b.getY() < 0 || a.getY() >= cache.world.getMaxHeight()
                    || b.getY() >= cache.world.getMaxHeight()) return Math.min(start, nearest);
            for (int x = floor(Math.min(a.getX(), b.getX()) - EPSILON); x <= floor(Math.max(a.getX(), b.getX()) + EPSILON); x++) {
                for (int y = floor(Math.min(a.getY(), b.getY()) - EPSILON) - 1; y <= floor(Math.max(a.getY(), b.getY()) + EPSILON); y++) {
                    for (int z = floor(Math.min(a.getZ(), b.getZ()) - EPSILON); z <= floor(Math.max(a.getZ(), b.getZ()) + EPSILON); z++) {
                        BlockPosition position = new BlockPosition(x, y, z);
                        if (!visited.add(position)) continue;
                        List<AxisAlignedBB> boxes = cache.boxes(position);
                        if (boxes == null) return Math.min(start, nearest); // Unloaded terrain is an impenetrable boundary.
                        for (AxisAlignedBB box : boxes) nearest = Math.min(nearest, intersection(origin, direction, box, range));
                    }
                }
            }
            // Fences can extend into the next voxel. Retain a future collision, but still visit
            // all intervening voxels before returning the closest obstruction.
            if (nearest <= end) return nearest;
        }
        return nearest;
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static final class CollisionCache {
        final World world;
        final WorldServer handle;
        final Map<BlockPosition, List<AxisAlignedBB>> cached = new HashMap<BlockPosition, List<AxisAlignedBB>>();

        CollisionCache(World world) { this.world = world; this.handle = ((CraftWorld) world).getHandle(); }

        List<AxisAlignedBB> boxes(BlockPosition pos) {
            if (cached.containsKey(pos)) return cached.get(pos);
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (y < 0 || y >= world.getMaxHeight()) return Collections.emptyList();
            // Actual-state resolution for stairs/fences/doors can read adjacent blocks. Guard the
            // complete one-block neighborhood BEFORE any NMS call so it cannot load a neighbor chunk.
            for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
                for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) { cached.put(pos, null); return null; }
                }
            }
            IBlockData state = handle.getTypeIfLoaded(pos);
            if (state == null) { cached.put(pos, null); return null; }
            List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
            // Native collision shapes handle partial blocks; air, plants and liquids contribute none.
            state.a(handle, pos, new AxisAlignedBB(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2), boxes, null, false);
            cached.put(pos, boxes);
            return boxes;
        }
    }

    private static final class Target {
        final LivingEntity entity;
        final AxisAlignedBB box;
        Target(LivingEntity entity, AxisAlignedBB box) { this.entity = entity; this.box = box; }
    }

    private static final class Hit {
        final LivingEntity entity;
        final double distance;
        Hit(LivingEntity entity, double distance) { this.entity = entity; this.distance = distance; }
    }

    /**
     * Every custom item gets one bench recipe, all of them built from vanilla materials.
     * The table lives in Blueprints, where the patterns were checked against each other
     * and against all 432 vanilla recipes before any of this ran.
     */
    private void registerRecipes() {
        for (Blueprints.Blueprint blueprint : Blueprints.all().values()) {
            ItemStack result = craftOutput(blueprint.id);
            if (result == null) throw new IllegalStateException("No output for blueprint " + blueprint.id);
            NamespacedKey key = new NamespacedKey(plugin, "jaspr_" + blueprint.id);
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(Blueprints.bukkitShape(blueprint));
            String letters = Blueprints.letters(blueprint);
            for (int i = 0; i < letters.length(); i++) {
                char letter = letters.charAt(i);
                recipe.setIngredient(letter, Blueprints.material(letter), Blueprints.data(letter));
            }
            if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Cannot register " + key);
            rememberRecipe(key, blueprint.id);
        }
    }

    private String recipeId(Recipe recipe) {
        return recipe instanceof Keyed ? recipeIds.get(((Keyed) recipe).getKey()) : null;
    }

    private static boolean containsWeapon(ItemStack[] matrix) {
        for (ItemStack item : matrix) if (Gun.named(ApocalypseItems.id(item)) != null) return true;
        return false;
    }

    private static boolean validIngredients(String id, ItemStack[] matrix) {
        Blueprints.Blueprint blueprint = Blueprints.of(id);
        return blueprint != null && Blueprints.matches(blueprint, matrix);
    }

    private static boolean marked(ItemStack item) {
        String id = ApocalypseItems.id(item);
        return id != null && !id.isEmpty();
    }

    private static ItemStack craftOutput(String id) {
        ItemStack result = Gun.named(id) != null ? weapon(id) : ApocalypseItems.gear(id);
        int amount = Blueprints.amount(id);
        if (amount > 1) result.setAmount(amount);
        return result;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        String id = recipeId(event.getRecipe());
        if (id == null) {
            if (containsWeapon(inventory.getMatrix())) inventory.setResult(null); // No vanilla hoe repair.
            return;
        }
        if (!(event.getView().getPlayer() instanceof Player) || !allowed((Player) event.getView().getPlayer())
                || !validIngredients(id, inventory.getMatrix())) { inventory.setResult(null); return; }
        inventory.setResult(craftOutput(id));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        String id = recipeId(event.getRecipe());
        if (id == null) {
            if (containsWeapon(event.getInventory().getMatrix())) event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player) || !allowed((Player) event.getWhoClicked())
                || !validIngredients(id, event.getInventory().getMatrix())) { event.setCancelled(true); return; }
        // One ordinary pickup has vanilla consumption semantics and gives every gun a new serial.
        // Deny shift/number/drop/double/creative clicks to avoid batched results with shared identity.
        if ((event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) || !empty(event.getCursor())) {
            event.setCancelled(true);
            hint((Player) event.getWhoClicked(), "Craft with one normal click and an empty cursor.");
            return;
        }
        event.setCurrentItem(craftOutput(id));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        if (containsWeapon(new ItemStack[] {event.getInventory().getItem(0), event.getInventory().getItem(1)})) event.setResult(null);
    }

    private void hint(Player player, String message) {
        long now = System.nanoTime();
        Long next = messageAt.get(player.getUniqueId());
        if (next == null || now >= next) {
            player.sendMessage(ChatColor.GRAY + message);
            messageAt.put(player.getUniqueId(), now + 800000000L);
        }
    }

    private void cancelReload(UUID id) {
        BukkitTask task = reloading.remove(id);
        if (task != null) task.cancel(); // No ammunition is charged until a successful completion.
        List<BukkitTask> shots = bursting.remove(id);
        if (shots != null) for (BukkitTask shot : shots) shot.cancel();
    }

    @EventHandler public void held(PlayerItemHeldEvent event) { cancelReload(event.getPlayer().getUniqueId()); }
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) { cancelReload(event.getPlayer().getUniqueId()); }
    @EventHandler public void drop(PlayerDropItemEvent event) { cancelReload(event.getPlayer().getUniqueId()); }
    @EventHandler public void inventory(InventoryClickEvent event) { cancelReload(event.getWhoClicked().getUniqueId()); }
    @EventHandler public void inventory(InventoryDragEvent event) { cancelReload(event.getWhoClicked().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { cancelReload(event.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent event) { cancelReload(event.getEntity().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelReload(id);
        readyAt.remove(id);
        messageAt.remove(id);
    }
}
