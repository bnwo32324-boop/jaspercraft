package chat.jaspr.apocalypse;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Craftable automated defense. A placed dispenser body with server-side targeting:
 * hostile mobs by default, optional passives/players, configurable range, fire
 * rate and target priority. Right-click opens settings, sneak + right-click
 * collects it. Turrets persist across restarts and never load chunks.
 *
 * <p>Upgrade tree (owner, 2026-10-02: "Turret upgrade tree ... it should also change the look of the turret itself. The
 * model should change with each upgrade. It should also change what sound effects it makes. Also, make the baseline
 * initial turret, unupgraded, nerfed a little bit, and make it a little bit more expensive to craft."): Sentry, then
 * Reinforced, then one of three branches - Gatling (rapid fire), Cannon (explosive splash) or Tesla (chain lightning) -
 * each with a second stage. Every tier has its own head model (iron pickaxe damage 100-107, apocalypse-pack
 * sentry-upgrades.cjs), its own firing and target-lock sounds, its own particles. The owner upgrades from the settings
 * menu with materials from their inventory; a collected turret keeps its upgrades. Shots are credited to the owner
 * (metadata {@value #OWNER_META}, read by JasprBounties) and grow with the owner's Engineering stat (JasprRPG).
 */
public final class SentryTurret implements Listener {
    static final String ID = "sentry_turret";
    static final String OWNER_META = "jaspr_sentry_owner";
    private static final String TITLE = "Sentry Turret";
    private static final String UPGRADE_TITLE = "Sentry Upgrades";
    private static final String NBT = "JasprApocalypse", TIER_KEY = "sentryTier";
    private static final int[] RANGES = {12, 24, 36};
    private static final String[] RATES = {"slow", "normal", "fast"};
    private static final String[] PRIORITIES = {"nearest", "weakest", "strongest"};
    // The model is six-times effective size: the 1.12.2 item transform caps
    // the display scale at 4, so the asset bakes the remaining 1.5x into its
    // cuboids. The marker stays just above the dispenser and the baked base
    // offset makes the plate land directly on the dispenser top.
    private static final double HEAD_SCALE = 6.0;
    private static final double HEAD_DISPLAY_SCALE = 4.0;
    private static final double HEAD_ANCHOR_OFFSET = 1.1;
    private static final int HEAD_CLEARANCE = 7;
    // Cosmetic clearance the head needs so it is not buried in a block. This is
    // deliberately small and separate from HEAD_CLEARANCE (the entity-scan height)
    // so sentries can be placed underground, in caves, and in the Nether.
    private static final int PLACE_HEADROOM = 2;

    /**
     * The upgrade tree. model: the iron pickaxe damage that shows this tier's head (apocalypse-pack/sentry-upgrades.cjs
     * must agree); damage/interval: multipliers of the fire-rate setting's numbers; range: blocks added to the range
     * setting; cost: what the owner pays to reach the tier (Material, amount pairs).
     */
    enum Tier {
        SENTRY("sentry", "Sentry Turret", "Mk I", 100, null, Material.DISPENSER, 1.0, 1.0, 0,
            "The original: steady single shots."),
        REINFORCED("reinforced", "Reinforced Sentry", "Mk II", 101, SENTRY, Material.IRON_BLOCK, 1.35, 0.9, 0,
            "Armoured, heavier rounds, faster cycling.", Material.IRON_BLOCK, 4, Material.REDSTONE, 16, Material.GOLD_INGOT, 4),
        GATLING("gatling", "Gatling Sentry", "Mk III", 102, REINFORCED, Material.HOPPER, 0.42, 0.36, 0,
            "Rotary barrels: a stream of light rounds that ignores a target's recovery time.",
            Material.GOLD_BLOCK, 2, Material.REDSTONE_BLOCK, 2, Material.DIAMOND, 3, Material.HOPPER, 2),
        STORM_GATLING("storm_gatling", "Storm Gatling", "Mk IV", 103, GATLING, Material.BLAZE_ROD, 0.56, 0.3, 6,
            "Twin rotary clusters: the fastest, longest-reaching stream.",
            Material.DIAMOND, 6, Material.GOLD_BLOCK, 4, Material.REDSTONE_BLOCK, 4, Material.BLAZE_ROD, 4),
        CANNON("cannon", "Cannon Sentry", "Mk III", 104, REINFORCED, Material.TNT, 1.55, 1.7, 6,
            "Explosive shells: heavy hits that splash onto everything near the target.",
            Material.IRON_BLOCK, 6, Material.TNT, 8, Material.OBSIDIAN, 2, Material.DIAMOND, 3),
        HOWITZER("howitzer", "Siege Howitzer", "Mk IV", 105, CANNON, Material.OBSIDIAN, 2.0, 1.7, 12,
            "Siege shells: a wider blast that throws the horde back.",
            Material.DIAMOND, 6, Material.OBSIDIAN, 8, Material.TNT, 12, Material.MAGMA_CREAM, 4),
        TESLA("tesla", "Tesla Sentry", "Mk III", 106, REINFORCED, Material.END_ROD, 1.0, 1.1, 0,
            "Chain lightning: every bolt leaps to two more foes.",
            Material.GOLD_BLOCK, 2, Material.REDSTONE_BLOCK, 4, Material.DIAMOND, 3, Material.ENDER_PEARL, 4),
        ARC_TOWER("arc_tower", "Arc Tower", "Mk IV", 107, TESLA, Material.DIAMOND_BLOCK, 1.25, 1.0, 6,
            "Forked lightning: leaps to four more foes and slows everything it strikes.",
            Material.DIAMOND, 6, Material.GOLD_BLOCK, 4, Material.QUARTZ, 8, Material.GLOWSTONE_DUST, 8);

        final String id, title, mark, blurb;
        final int model, range;
        final Tier parent;
        final Material icon;
        final double damage, interval;
        final Object[] cost;

        Tier(String id, String title, String mark, int model, Tier parent, Material icon, double damage, double interval, int range,
             String blurb, Object... cost) {
            this.id = id; this.title = title; this.mark = mark; this.model = model; this.parent = parent; this.icon = icon;
            this.damage = damage; this.interval = interval; this.range = range; this.blurb = blurb; this.cost = cost;
        }

        static Tier byId(String id) {
            for (Tier t : values()) if (t.id.equals(id)) return t;
            return null;
        }

        List<Tier> next() {
            List<Tier> out = new ArrayList<Tier>();
            for (Tier t : values()) if (t.parent == this) out.add(t);
            return out;
        }

        boolean gatling() { return this == GATLING || this == STORM_GATLING; }
        boolean cannon() { return this == CANNON || this == HOWITZER; }
        boolean tesla() { return this == TESLA || this == ARC_TOWER; }
    }

    static final class Settings {
        boolean enabled = true, monsters = true, passives = false, players = false;
        int range = 24;
        String rate = "normal", focus = "nearest";
    }
    static final class Turret {
        String world;
        int x, y, z;
        UUID owner;
        String ownerName;
        Settings settings = new Settings();
        Tier tier = Tier.SENTRY;
        int cooldown;
        long kills;
        transient UUID stand;
        transient double yaw;
        transient UUID locked;
        transient int shotCount;
    }

    /** Marks the upgrade menu and remembers which turret it belongs to. */
    static final class UpgradeHolder implements InventoryHolder {
        final String key;
        Inventory inventory;
        UpgradeHolder(String key) { this.key = key; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final ApocalypsePlugin plugin;
    private BukkitTask task, saver;
    private boolean started, dirty;
    private final Map<String, Turret> turrets = new LinkedHashMap<String, Turret>();
    private final Map<UUID, Long> messageAt = new HashMap<UUID, Long>();
    private File file;
    private long shots, upgrades, splashHits, chainHits;
    private Method engineering, wearing;
    private boolean engineeringLooked, wearingLooked;

    public SentryTurret(ApocalypsePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (started) return;
        started = true;
        file = new File(plugin.getDataFolder(), "turrets.yml");
        load();
        // 2026-10-02 (owner: "a little bit more expensive to craft"): the cheap 4-ingot recipe is gone. The one recipe is
        // the blueprint (Arsenal/Blueprints: seven iron ingots, an iron block and redstone).
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        int period = Math.max(1, plugin.getConfig().getInt("sentry.tick-period", 5));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, period, period);
        // Kills are counted on every shot; the file is written at most every 30 s (and on any placement, upgrade or removal).
        saver = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { if (dirty) save(); }
        }, 600L, 600L);
        plugin.getLogger().info("SENTRY_READY turrets=" + turrets.size() + " tiers=" + Tier.values().length
            + " baseDamage=" + damage("slow") + "/" + damage("normal") + "/" + damage("fast"));
    }

    public void stop() {
        started = false;
        if (task != null) task.cancel();
        if (saver != null) saver.cancel();
        task = null;
        saver = null;
        HandlerList.unregisterAll(this);
        save();
        messageAt.clear();
    }

    String metrics() {
        return "turrets=" + turrets.size() + ",shots=" + shots + ",kills=" + kills() + ",upgrades=" + upgrades + ",upgraded=" + upgraded()
            + ",splashHits=" + splashHits + ",chainHits=" + chainHits;
    }
    private long kills() { long total = 0; for (Turret turret : turrets.values()) total += turret.kills; return total; }
    private int upgraded() { int n = 0; for (Turret turret : turrets.values()) if (turret.tier != Tier.SENTRY) n++; return n; }

    // -- Item helpers ------------------------------------------------------
    /** Public classification hook for server-side item systems; identity remains NBT-backed. */
    public static boolean verified(ItemStack item) {
        return item != null && item.getType() == Material.IRON_PICKAXE
            && ID.equals(ApocalypseItems.id(item)) && ExpeditionEquipment.verified(item);
    }
    private boolean gate(Player player) {
        return started && player != null && player.isOnline()
            && Arsenal.equipmentWorld(plugin, player.getWorld()) && plugin.authenticated(player);
    }

    /** The upgrade a sentry item carries (a collected upgraded turret), or SENTRY. */
    static Tier tierOf(ItemStack item) {
        if (item == null) return Tier.SENTRY;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
            if (nms == null || !nms.hasTag()) return Tier.SENTRY;
            Tier t = Tier.byId(nms.getTag().getCompound(NBT).getString(TIER_KEY));
            return t == null ? Tier.SENTRY : t;
        } catch (RuntimeException e) {
            return Tier.SENTRY;
        }
    }

    /** A fresh sentry item that remembers its upgrade (shown in its name and lore). */
    static ItemStack itemFor(Tier tier) {
        ItemStack item = ExpeditionEquipment.item(ID);
        if (tier == null || tier == Tier.SENTRY) return item;
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + tier.title + ChatColor.GRAY + " (" + tier.mark + ")");
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        lore.add(0, ChatColor.GOLD + "Upgraded: " + tier.title + " " + tier.mark);
        meta.setLore(lore);
        item.setItemMeta(meta);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.getTag();
        NBTTagCompound data = root.getCompound(NBT);
        data.setString(TIER_KEY, tier.id);
        root.set(NBT, data);
        nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** What the tracking head wears: the tier's model on an unbreakable iron pickaxe (the plain sentry keeps its item). */
    static ItemStack headItem(Tier tier) {
        if (tier == Tier.SENTRY) return ExpeditionEquipment.item(ID);
        ItemStack head = new ItemStack(Material.IRON_PICKAXE, 1, (short) tier.model);
        ItemMeta meta = head.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.setDisplayName(ChatColor.AQUA + tier.title);
        head.setItemMeta(meta);
        return head;
    }

    static boolean headFits(ItemStack helmet, Tier tier) {
        if (tier == Tier.SENTRY) return verified(helmet);
        return helmet != null && helmet.getType() == Material.IRON_PICKAXE && helmet.getDurability() == tier.model
            && helmet.hasItemMeta() && helmet.getItemMeta().isUnbreakable();
    }

    // -- Persistence -------------------------------------------------------
    private static String key(String world, int x, int y, int z) { return world + ":" + x + "," + y + "," + z; }
    private static String key(Turret turret) { return key(turret.world, turret.x, turret.y, turret.z); }

    @SuppressWarnings("unchecked")
    private void load() {
        turrets.clear();
        if (!file.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        List<?> rows = data.getList("turrets", new ArrayList<Object>());
        // A bound on what a corrupt or hand-edited file can load, not a gameplay limit.
        // It is far above any plausible build and it complains rather than dropping quietly,
        // because silently losing somebody's turrets on restart is a nasty way to find out.
        int stored = Math.max(1, plugin.getConfig().getInt("sentry.max-stored", 20000));
        int dropped = 0;
        for (Object row : rows) {
            if (!(row instanceof Map)) continue;
            if (turrets.size() >= stored) { dropped++; continue; }
            Map<?, ?> map = (Map<?, ?>) row;
            try {
                Turret turret = new Turret();
                turret.world = String.valueOf(map.get("world"));
                if (turret.world.length() > 64) continue;
                turret.x = ((Number) map.get("x")).intValue();
                turret.y = ((Number) map.get("y")).intValue();
                turret.z = ((Number) map.get("z")).intValue();
                if (Math.abs(turret.x) > 30000000 || Math.abs(turret.z) > 30000000 || turret.y < 0 || turret.y > 255) continue;
                Object owner = map.get("owner");
                if (owner != null) turret.owner = UUID.fromString(String.valueOf(owner));
                Object name = map.get("owner-name");
                turret.ownerName = name == null ? "unknown" : String.valueOf(name);
                turret.settings = normalize(cast(map.get("settings")));
                Object kills = map.get("kills");
                turret.kills = kills instanceof Number ? Math.max(0, ((Number) kills).longValue()) : 0;
                Tier tier = Tier.byId(String.valueOf(map.get("tier")));
                turret.tier = tier == null ? Tier.SENTRY : tier;
                turrets.put(key(turret), turret);
            } catch (RuntimeException ignored) { }
        }
        if (dropped > 0) plugin.getLogger().warning("SENTRY_LOAD_TRUNCATED dropped=" + dropped + " cap=" + stored);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        if (!(value instanceof Map)) return new LinkedHashMap<String, Object>();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }

    static Settings normalize(Map<String, Object> map) {
        Settings settings = new Settings();
        settings.enabled = bool(map.get("enabled"), true);
        settings.monsters = bool(map.get("monsters"), true);
        settings.passives = bool(map.get("passives"), false);
        settings.players = bool(map.get("players"), false);
        Object range = map.get("range");
        settings.range = range instanceof Number && (((Number) range).intValue() == 12 || ((Number) range).intValue() == 36) ? ((Number) range).intValue() : 24;
        Object rate = map.get("rate");
        settings.rate = "slow".equals(rate) || "fast".equals(rate) ? String.valueOf(rate) : "normal";
        Object focus = map.get("focus");
        settings.focus = "weakest".equals(focus) || "strongest".equals(focus) ? String.valueOf(focus) : "nearest";
        return settings;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private void save() {
        dirty = false;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Turret turret : turrets.values()) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("world", turret.world); map.put("x", turret.x); map.put("y", turret.y); map.put("z", turret.z);
            if (turret.owner != null) map.put("owner", turret.owner.toString());
            map.put("owner-name", turret.ownerName);
            Map<String, Object> settings = new LinkedHashMap<String, Object>();
            settings.put("enabled", turret.settings.enabled); settings.put("monsters", turret.settings.monsters);
            settings.put("passives", turret.settings.passives); settings.put("players", turret.settings.players);
            settings.put("range", turret.settings.range); settings.put("rate", turret.settings.rate);
            settings.put("focus", turret.settings.focus);
            map.put("settings", settings); map.put("kills", turret.kills);
            map.put("tier", turret.tier.id);
            rows.add(map);
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("turrets", rows);
        try { data.save(file); } catch (java.io.IOException e) { plugin.getLogger().warning("SENTRY_SAVE_FAILED " + e.getMessage()); }
    }

    private void hint(Player player, String message) {
        long now = System.nanoTime();
        Long next = messageAt.get(player.getUniqueId());
        if (next == null || now >= next) {
            player.sendMessage(ChatColor.GRAY + message);
            messageAt.put(player.getUniqueId(), now + 4000000000L);
        }
    }

    // -- Placement / pickup / break ----------------------------------------
    private int owned(UUID owner) {
        int count = 0;
        for (Turret turret : turrets.values()) if (owner != null && owner.equals(turret.owner)) count++;
        return count;
    }

    // The carrier is a tool, so vanilla block placement never fires for it:
    // right-clicking a face mounts the dispenser body here instead.
    private void tryPlace(Player player, Block clicked, BlockFace face, ItemStack held) {
        if (face == null || face == BlockFace.SELF) return;
        if (face == BlockFace.DOWN) { hint(player, "Sentries mount on floors and walls, not ceilings."); return; }
        Block target = clicked.getRelative(face);
        String room = roomFor(target);
        if (room != null) { hint(player, room); return; }
        // Zero or negative means no limit, and that is the default: build as many as you like.
        // Both knobs are still here so a limit can be put back from config without a rebuild.
        int perPlayer = plugin.getConfig().getInt("sentry.max-per-player", 0);
        int total = plugin.getConfig().getInt("sentry.max-total", 0);
        boolean cappedTotal = total > 0 && turrets.size() >= total;
        boolean cappedOwner = perPlayer > 0 && owned(player.getUniqueId()) >= perPlayer;
        if (cappedTotal || cappedOwner) {
            hint(player, "Sentry limit reached (" + (perPlayer > 0 ? perPlayer + " each" : "no per-player cap")
                    + ", " + (total > 0 ? total + " total" : "no total cap") + "). Collect one first.");
            return;
        }
        target.setType(Material.DISPENSER);
        faceDispenser(target, face);
        Turret turret = new Turret();
        turret.world = target.getWorld().getName(); turret.x = target.getX(); turret.y = target.getY(); turret.z = target.getZ();
        turret.owner = player.getUniqueId(); turret.ownerName = player.getName();
        turret.tier = tierOf(held);
        turrets.put(key(turret), turret);
        save();
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_PLACE, 1f, 0.9f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else inHand.setAmount(inHand.getAmount() - 1);
        }
        plugin.getLogger().info("SENTRY_PLACE player=" + player.getName() + " at=" + key(turret) + " tier=" + turret.tier.id);
    }

    /** Null when the body fits; otherwise the reason to show the placer. */
    private String roomFor(Block target) {
        if (target == null || target.getY() < 1 || target.getY() > 255 - PLACE_HEADROOM) return "No room to mount the sentry there.";
        if (!target.isEmpty() && !target.isLiquid()) return "No room to mount the sentry there.";
        for (int dy = 1; dy <= PLACE_HEADROOM; dy++) {
            if (target.getRelative(BlockFace.UP, dy).getType().isOccluding()) return "The tracker needs " + PLACE_HEADROOM + " blocks of headroom above the sentry.";
        }
        for (Entity entity : target.getWorld().getNearbyEntities(target.getLocation().add(0.5, 0.5, 0.5), 1, 1, 1)) {
            if (entity instanceof Player && !((Player) entity).isDead()) return "A survivor is standing there.";
        }
        return null;
    }

    private void faceDispenser(Block target, BlockFace face) {
        try {
            org.bukkit.material.Dispenser data = new org.bukkit.material.Dispenser();
            data.setFacingDirection(face);
            BlockState state = target.getState();
            state.setData(data);
            state.update(true);
        } catch (RuntimeException ignored) { }
    }

    private Turret at(Block block) {
        if (block == null || block.getType() != Material.DISPENSER) return null;
        return turrets.get(key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    // -- Tracking head assembly ----------------------------------------------
    // A small invisible armor stand wears the turret model as its helmet and
    // gyrates toward the current target. Marker stands have no hitbox, so
    // clicks pass through to the dispenser body and attacks ignore them.
    private static final String STAND_TAG = "jaspr_sentry";

    private static Location standAnchor(Turret turret, World world) {
        // Keep the marker in open air: burying the feet darkens the whole
        // head. The asset's baked -3.5px base offset compensates for the
        // capped display transform; the model's base plate rests on the
        // dispenser top while the marker remains above it.
        return new Location(world, turret.x + 0.5, turret.y + HEAD_ANCHOR_OFFSET, turret.z + 0.5);
    }

    private void removeStand(Turret turret) {
        try {
            if (turret.stand != null) {
                Entity entity = Bukkit.getEntity(turret.stand);
                if (entity instanceof ArmorStand && entity.getScoreboardTags().contains(STAND_TAG)) entity.remove();
            }
        } catch (RuntimeException ignored) { }
        turret.stand = null;
    }

    private ArmorStand syncStand(Turret turret, World world) {
        if (turret.stand != null) {
            Entity entity = Bukkit.getEntity(turret.stand);
            if (entity instanceof ArmorStand && entity.getScoreboardTags().contains(STAND_TAG) && entity.isValid()) {
                ArmorStand stand = (ArmorStand) entity;
                // A live in-memory reference can outlast an asset/plugin
                // update. Do not let an old full-size head bypass migration.
                if (stand.isSmall() && key(turret).equals(stand.getCustomName()) && headFits(stand.getHelmet(), turret.tier)) {
                    Location anchor = standAnchor(turret, world);
                    if (stand.getLocation().distanceSquared(anchor) > 0.25) stand.teleport(anchor);
                    return stand;
                }
                stand.remove();
            }
            turret.stand = null;
        }
        Location anchor = standAnchor(turret, world);
        ArmorStand found = null;
        for (Entity entity : world.getNearbyEntities(anchor, 2, HEAD_CLEARANCE, 2)) {
            if (!(entity instanceof ArmorStand) || !entity.getScoreboardTags().contains(STAND_TAG)) continue;
            if (!key(turret).equals(entity.getCustomName())) continue;
            if (found == null) { found = (ArmorStand) entity; turret.stand = entity.getUniqueId(); }
            else entity.remove();
        }
        // Migrate legacy heads: small ones with stale helmets, and the brief
        // full-size generation, are rebuilt instead of adopted.
        if (found != null && (!found.isSmall() || !headFits(found.getHelmet(), turret.tier))) { found.remove(); found = null; turret.stand = null; }
        if (found != null) {
            if (found.getLocation().distanceSquared(anchor) > 0.25) found.teleport(anchor);
            return found;
        }
        ArmorStand stand;
        try { stand = world.spawn(anchor, ArmorStand.class); }
        catch (RuntimeException e) { return null; }
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomName(key(turret));
        stand.setCustomNameVisible(false);
        stand.addScoreboardTag(STAND_TAG);
        ItemStack helm = headItem(turret.tier);
        helm.setAmount(1);
        stand.setHelmet(helm);
        turret.stand = stand.getUniqueId();
        return stand;
    }

    private static double yawTo(Location from, Location to) {
        return Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static double yawDelta(double from, double to) {
        double delta = (to - from) % 360;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return delta;
    }

    private void aim(Turret turret, World world, Target target, Location muzzle) {
        ArmorStand stand = syncStand(turret, world);
        if (stand == null) return;
        double yaw;
        double pitch;
        if (target != null) {
            Location aim = target.entity.getEyeLocation();
            // The server damage ray originates at the body muzzle. Use that
            // same origin for yaw so the visual barrel does not acquire a
            // different heading merely because the scaled head is elevated.
            yaw = yawTo(muzzle, aim);
            double horizontal = Math.hypot(aim.getX() - muzzle.getX(), aim.getZ() - muzzle.getZ());
            pitch = Math.toDegrees(Math.atan2(muzzle.getY() - aim.getY(), Math.max(0.001, horizontal)));
        } else {
            // Idle sweep: slow radar arc while nothing is in range.
            yaw = turret.yaw + 4;
            pitch = 0;
        }
        if (Math.abs(yawDelta(turret.yaw, yaw)) > 3) {
            turret.yaw = yaw;
            Location pose = stand.getLocation().clone();
            pose.setYaw((float) yaw);
            pose.setPitch(0);
            try { stand.teleport(pose); } catch (RuntimeException ignored) { }
        }
        try { stand.setHeadPose(new EulerAngle(Math.toRadians(Math.max(-60, Math.min(60, pitch))), 0, 0)); }
        catch (RuntimeException ignored) { }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Player player = event.getPlayer();
        Turret turret = at(clicked);
        if (turret == null) {
            // Mounting a new sentry; the tool carrier never triggers block placement.
            if (!verified(event.getItem()) || !gate(player)) return;
            event.setCancelled(true);
            tryPlace(player, clicked, event.getBlockFace(), event.getItem());
            return;
        }
        event.setCancelled(true);
        if (!gate(player)) return;
        Block block = clicked;
        if (player.isSneaking()) {
            turrets.remove(key(turret));
            removeStand(turret);
            block.setType(Material.AIR);
            if (player.getGameMode() != GameMode.CREATIVE) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(itemFor(turret.tier));
                for (ItemStack rest : left.values()) block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), rest);
            }
            save();
            player.sendMessage(ChatColor.GRAY + "Sentry collected" + (turret.tier == Tier.SENTRY ? "." : " (it keeps its " + turret.tier.title + " upgrade)."));
            plugin.getLogger().info("SENTRY_PICKUP player=" + player.getName() + " at=" + key(turret) + " tier=" + turret.tier.id);
            return;
        }
        open(player, turret);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void smash(BlockBreakEvent event) {
        Turret turret = at(event.getBlock());
        if (turret == null) return;
        if (!gate(event.getPlayer())) { event.setCancelled(true); return; }
        event.setCancelled(true);
        Block block = event.getBlock();
        turrets.remove(key(turret));
        removeStand(turret);
        block.setType(Material.AIR);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), itemFor(turret.tier));
        save();
        plugin.getLogger().info("SENTRY_BREAK player=" + event.getPlayer().getName() + " at=" + key(turret) + " tier=" + turret.tier.id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blast(BlockExplodeEvent event) {
        for (Block block : new ArrayList<Block>(event.blockList())) {
            Turret turret = at(block);
            if (turret == null) continue;
            event.blockList().remove(block);
            turrets.remove(key(turret));
            removeStand(turret);
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), itemFor(turret.tier));
            plugin.getLogger().info("SENTRY_BLAST at=" + key(turret) + " tier=" + turret.tier.id);
        }
        if (!turrets.isEmpty()) save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blast(EntityExplodeEvent event) {
        for (Block block : new ArrayList<Block>(event.blockList())) {
            Turret turret = at(block);
            if (turret == null) continue;
            event.blockList().remove(block);
            turrets.remove(key(turret));
            removeStand(turret);
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), itemFor(turret.tier));
            plugin.getLogger().info("SENTRY_BLAST at=" + key(turret) + " tier=" + turret.tier.id);
        }
        if (!turrets.isEmpty()) save();
    }

    // -- Settings menu -------------------------------------------------------
    private void open(Player player, Turret turret) {
        Inventory menu = plugin.getServer().createInventory(null, 27, TITLE);
        render(menu, turret);
        player.openInventory(menu);
    }

    private ItemStack button(Material material, short data, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggle(String name, boolean on, String hint) {
        return button(Material.WOOL, (short) (on ? 5 : 14), (on ? ChatColor.GREEN : ChatColor.RED) + name + ": " + (on ? "ON" : "OFF"),
            ChatColor.GRAY + hint, ChatColor.DARK_GRAY + "Click to switch.");
    }

    private void render(Inventory menu, Turret turret) {
        Settings settings = turret.settings;
        List<Tier> next = turret.tier.next();
        menu.setItem(4, button(Material.ANVIL, (short) 0, ChatColor.GOLD + "Upgrades: " + turret.tier.title + " " + turret.tier.mark,
            ChatColor.GRAY + turret.tier.blurb,
            next.isEmpty() ? ChatColor.GREEN + "Fully upgraded." : ChatColor.YELLOW + "Click to see the next upgrade" + (next.size() > 1 ? "s." : ".")));
        menu.setItem(10, toggle("Defense", settings.enabled, "The sentry fires while enabled."));
        menu.setItem(11, toggle("Hostile mobs", settings.monsters, "Zombies, skeletons and other monsters."));
        menu.setItem(12, toggle("Passive mobs", settings.passives, "Animals and villagers."));
        menu.setItem(13, toggle("Players", settings.players, "Other survivors where PvP is on."));
        menu.setItem(14, button(Material.PAPER, (short) 0, ChatColor.GOLD + "Range: " + (settings.range + turret.tier.range) + " blocks",
            ChatColor.GRAY + "Click to cycle 12 / 24 / 36" + (turret.tier.range > 0 ? " (+" + turret.tier.range + " from the upgrade)." : ".")));
        menu.setItem(15, button(Material.REDSTONE, (short) 0, ChatColor.GOLD + "Fire rate: " + title(settings.rate),
            ChatColor.GRAY + describeRate(settings.rate, turret.tier), ChatColor.DARK_GRAY + "Click to cycle."));
        menu.setItem(16, button(Material.COMPASS, (short) 0, ChatColor.GOLD + "Targets: " + title(settings.focus),
            ChatColor.GRAY + "Click to cycle nearest / weakest / strongest."));
        menu.setItem(22, button(Material.DISPENSER, (short) 0, ChatColor.AQUA + turret.tier.title,
            ChatColor.GRAY + "Placed by " + turret.ownerName + " at " + turret.x + ", " + turret.y + ", " + turret.z,
            ChatColor.GRAY + String.valueOf(turret.kills) + " confirmed kills.",
            ChatColor.GRAY + "Tier: " + turret.tier.mark));
        menu.setItem(26, button(Material.BARRIER, (short) 0, ChatColor.RED + "Close",
            ChatColor.GRAY + "Sneak + right-click collects the sentry."));
    }

    private static String title(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }

    private String describeRate(String rate, Tier tier) {
        String dmg = trim(damage(rate) * tier.damage);
        String every = trim(Math.max(5, interval(rate) * tier.interval) / 20.0);
        if ("slow".equals(rate)) return "Heavy rounds: " + dmg + " damage every " + every + "s.";
        if ("fast".equals(rate)) return "Light rounds: " + dmg + " damage every " + every + "s.";
        return "Standard rounds: " + dmg + " damage every " + every + "s.";
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /**
     * Base damage per shot by fire rate. 2026-10-02 (owner: "make the baseline initial turret, unupgraded, nerfed a little
     * bit"): 13 / 10 / 7, was 16 / 12 / 8. Upgrades multiply it.
     */
    private double damage(String rate) {
        if ("slow".equals(rate)) return Math.max(1, plugin.getConfig().getDouble("sentry.slow-damage", 13.0));
        if ("fast".equals(rate)) return Math.max(1, plugin.getConfig().getDouble("sentry.fast-damage", 7.0));
        return Math.max(1, plugin.getConfig().getDouble("sentry.normal-damage", 10.0));
    }

    private int interval(String rate) {
        if ("slow".equals(rate)) return Math.max(5, plugin.getConfig().getInt("sentry.slow-interval-ticks", 22));
        if ("fast".equals(rate)) return Math.max(5, plugin.getConfig().getInt("sentry.fast-interval-ticks", 7));
        return Math.max(5, plugin.getConfig().getInt("sentry.normal-interval-ticks", 12));
    }

    private Turret openTurret(Player player) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) return null;
        if (!TITLE.equals(player.getOpenInventory().getTitle())) return null;
        ItemStack info = player.getOpenInventory().getTopInventory().getItem(22);
        if (info == null || info.getType() != Material.DISPENSER || !info.hasItemMeta()) return null;
        List<String> lore = info.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) return null;
        String coords = ChatColor.stripColor(lore.get(0));
        int at = coords.lastIndexOf(" at ");
        if (at < 0) return null;
        String[] parts = coords.substring(at + 4).split(", ");
        if (parts.length != 3) return null;
        try {
            return turrets.get(key(player.getWorld().getName(),
                Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
        } catch (NumberFormatException e) { return null; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView() == null) return;
        if (event.getView().getTopInventory().getHolder() instanceof UpgradeHolder) { upgradeClick(event, player); return; }
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!gate(player) || event.getRawSlot() != event.getSlot()
                || event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.CHEST) return;
        Turret turret = openTurret(player);
        if (turret == null) { player.closeInventory(); return; }
        Settings settings = turret.settings;
        switch (event.getSlot()) {
            case 4: openUpgrades(player, turret); return;
            case 10: settings.enabled = !settings.enabled; break;
            case 11: settings.monsters = !settings.monsters; break;
            case 12: settings.passives = !settings.passives; break;
            case 13: settings.players = !settings.players; break;
            case 14: settings.range = settings.range == 12 ? 24 : settings.range == 24 ? 36 : 12; break;
            case 15: settings.rate = "slow".equals(settings.rate) ? "normal" : "normal".equals(settings.rate) ? "fast" : "slow"; break;
            case 16: settings.focus = "nearest".equals(settings.focus) ? "weakest" : "weakest".equals(settings.focus) ? "strongest" : "nearest"; break;
            case 26: player.closeInventory(); return;
            default: return;
        }
        turret.cooldown = 0;
        save();
        render(event.getView().getTopInventory(), turret);
        player.updateInventory();
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        boolean ours = event.getView().getTopInventory().getHolder() instanceof UpgradeHolder || TITLE.equals(event.getView().getTitle());
        if (!ours) return;
        for (int slot : event.getRawSlots()) if (slot < event.getView().getTopInventory().getSize()) { event.setCancelled(true); return; }
    }

    // -- Upgrade menu --------------------------------------------------------
    private boolean mayUpgrade(Player player, Turret turret) {
        return player.getGameMode() == GameMode.CREATIVE || player.isOp() || player.getUniqueId().equals(turret.owner);
    }

    private void openUpgrades(Player player, Turret turret) {
        UpgradeHolder holder = new UpgradeHolder(key(turret));
        holder.inventory = plugin.getServer().createInventory(holder, 27, UPGRADE_TITLE);
        renderUpgrades(player, holder, turret);
        player.openInventory(holder.inventory);
    }

    /** Slots of the next tiers: one in the middle, or three across for the branch point. */
    static int[] optionSlots(int count) {
        return count <= 1 ? new int[]{13} : count == 2 ? new int[]{12, 14} : new int[]{11, 13, 15};
    }

    private void renderUpgrades(Player player, UpgradeHolder holder, Turret turret) {
        Inventory menu = holder.inventory;
        menu.clear();
        ItemStack pane = button(Material.STAINED_GLASS_PANE, (short) 7, " ");
        for (int i = 0; i < 27; i++) menu.setItem(i, pane);
        Tier now = turret.tier;
        menu.setItem(4, button(now.icon, (short) 0, ChatColor.AQUA + now.title + " " + ChatColor.GRAY + now.mark,
            ChatColor.GRAY + now.blurb, ChatColor.DARK_GRAY + "Path: " + path(now)));
        List<Tier> next = now.next();
        int[] slots = optionSlots(next.size());
        boolean allowed = mayUpgrade(player, turret);
        for (int i = 0; i < next.size() && i < slots.length; i++) menu.setItem(slots[i], option(player, next.get(i), allowed, turret));
        if (next.isEmpty()) menu.setItem(13, button(Material.NETHER_STAR, (short) 0, ChatColor.GREEN + "Fully upgraded",
            ChatColor.GRAY + "This sentry is at the top of its branch."));
        menu.setItem(18, button(Material.ARROW, (short) 0, ChatColor.YELLOW + "Back to settings"));
        menu.setItem(26, button(Material.BARRIER, (short) 0, ChatColor.RED + "Close"));
    }

    private static String path(Tier tier) {
        StringBuilder s = new StringBuilder(tier.title);
        for (Tier t = tier.parent; t != null; t = t.parent) s.insert(0, t.title + " > ");
        return s.toString();
    }

    private ItemStack option(Player player, Tier tier, boolean allowed, Turret turret) {
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + tier.blurb);
        lore.add(ChatColor.DARK_GRAY + "Damage x" + trim(tier.damage) + ", cycle x" + trim(tier.interval)
            + (tier.range > 0 ? ", +" + tier.range + " range" : ""));
        lore.add("");
        lore.add(ChatColor.GOLD + "Cost:");
        boolean free = player.getGameMode() == GameMode.CREATIVE;
        boolean afford = true;
        for (int i = 0; i + 1 < tier.cost.length; i += 2) {
            Material m = (Material) tier.cost[i];
            int need = (Integer) tier.cost[i + 1];
            int have = count(player, m);
            if (have < need) afford = false;
            lore.add((have >= need || free ? ChatColor.GREEN : ChatColor.RED) + "  " + need + " " + nice(m) + ChatColor.DARK_GRAY + " (" + have + ")");
        }
        lore.add("");
        if (!allowed) lore.add(ChatColor.RED + "Only " + turret.ownerName + " can upgrade this sentry.");
        else if (afford || free) lore.add(ChatColor.YELLOW + "Click to upgrade.");
        else lore.add(ChatColor.RED + "You need more materials.");
        return button(tier.icon, (short) 0, ChatColor.AQUA + tier.title + " " + ChatColor.GRAY + tier.mark, lore.toArray(new String[0]));
    }

    static String nice(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Plain items of a kind in a player's inventory (named or tagged items never pay for upgrades). */
    private static int count(Player player, Material m) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents())
            if (plainStack(it, m)) n += it.getAmount();
        return n;
    }

    private static boolean plainStack(ItemStack it, Material m) {
        if (it == null || it.getType() != m || it.getDurability() != 0) return false;
        String id = ApocalypseItems.id(it);
        if (id != null && !id.isEmpty()) return false;
        return !it.hasItemMeta() || (!it.getItemMeta().hasDisplayName() && !it.getItemMeta().hasLore());
    }

    private static void take(Player player, Material m, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (!plainStack(it, m)) continue;
            int use = Math.min(amount, it.getAmount());
            amount -= use;
            if (use >= it.getAmount()) contents[i] = null; else it.setAmount(it.getAmount() - use);
        }
        player.getInventory().setStorageContents(contents);
    }

    private void upgradeClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        UpgradeHolder holder = (UpgradeHolder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;
        Turret turret = turrets.get(holder.key);
        if (turret == null || !gate(player)) { player.closeInventory(); return; }
        if (slot == 26) { player.closeInventory(); return; }
        if (slot == 18) { open(player, turret); return; }
        List<Tier> next = turret.tier.next();
        int[] slots = optionSlots(next.size());
        Tier pick = null;
        for (int i = 0; i < next.size() && i < slots.length; i++) if (slots[i] == slot) pick = next.get(i);
        if (pick == null) return;
        if (!mayUpgrade(player, turret)) { player.sendMessage(ChatColor.RED + "Only " + turret.ownerName + " can upgrade this sentry."); return; }
        if (player.getGameMode() != GameMode.CREATIVE) {
            for (int i = 0; i + 1 < pick.cost.length; i += 2) {
                if (count(player, (Material) pick.cost[i]) < (Integer) pick.cost[i + 1]) {
                    player.sendMessage(ChatColor.RED + "You need " + pick.cost[i + 1] + " " + nice((Material) pick.cost[i]) + " for the " + pick.title + ".");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                    return;
                }
            }
            for (int i = 0; i + 1 < pick.cost.length; i += 2) take(player, (Material) pick.cost[i], (Integer) pick.cost[i + 1]);
        }
        Tier from = turret.tier;
        turret.tier = pick;
        turret.cooldown = 0;
        upgrades++;
        save();
        World world = Bukkit.getWorld(turret.world);
        if (world != null) {
            removeStand(turret);
            syncStand(turret, world);
            Location at = new Location(world, turret.x + 0.5, turret.y + 1.5, turret.z + 0.5);
            world.playSound(at, Sound.BLOCK_ANVIL_USE, 0.9f, 1.1f);
            world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            world.spawnParticle(Particle.FIREWORKS_SPARK, at, 30, 0.4, 0.6, 0.4, 0.08);
        }
        player.sendMessage(ChatColor.GREEN + "Sentry upgraded: " + ChatColor.WHITE + pick.title + " " + pick.mark + ChatColor.GRAY + ". " + pick.blurb);
        plugin.getLogger().info("SENTRY_UPGRADE player=" + player.getName() + " at=" + key(turret) + " from=" + from.id + " to=" + pick.id);
        renderUpgrades(player, holder, turret);
        player.updateInventory();
    }

    @EventHandler public void quit(PlayerQuitEvent event) { messageAt.remove(event.getPlayer().getUniqueId()); }

    // -- Combat tick ---------------------------------------------------------
    private void tick() {
        if (!started || turrets.isEmpty() || !plugin.getConfig().getBoolean("sentry.enabled", true)) return;
        int period = Math.max(1, plugin.getConfig().getInt("sentry.tick-period", 5));
        for (Turret turret : new ArrayList<Turret>(turrets.values())) {
            World world = Bukkit.getWorld(turret.world);
            if (world == null || !world.isChunkLoaded(turret.x >> 4, turret.z >> 4)) continue;
            if (world.getBlockAt(turret.x, turret.y, turret.z).getType() != Material.DISPENSER) {
                turrets.remove(key(turret));
                removeStand(turret);
                save();
                continue;
            }
            if (!turret.settings.enabled) { turret.cooldown = 0; turret.locked = null; syncStand(turret, world); continue; }
            Location muzzle = new Location(world, turret.x + 0.5, turret.y + 1.2, turret.z + 0.5);
            boolean belt = toolbelt(turret, world, muzzle);
            Target target = acquire(world, muzzle, turret.settings, turret.settings.range + turret.tier.range + (belt ? 4 : 0));
            aim(turret, world, target, muzzle);
            if (target == null) { turret.cooldown = 0; turret.locked = null; continue; }
            if (!target.entity.getUniqueId().equals(turret.locked)) { turret.locked = target.entity.getUniqueId(); lockSound(world, muzzle, turret.tier); }
            turret.cooldown -= period;
            if (turret.cooldown > 0) continue;
            turret.cooldown = Math.max(period, (int) Math.round(interval(turret.settings.rate) * turret.tier.interval));
            fire(world, muzzle, target, turret, belt);
        }
    }

    private static final class Target {
        final LivingEntity entity;
        final double distanceSquared;
        Target(LivingEntity entity, double distanceSquared) { this.entity = entity; this.distanceSquared = distanceSquared; }
    }

    private Target acquire(World world, Location muzzle, Settings settings, int range) {
        Collection<Entity> nearby = world.getNearbyEntities(muzzle, range, range, range);
        Target best = null;
        double bestScore = 0;
        boolean first = true;
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living.isDead() || !living.isValid()) continue;
            if (!wanted(living, settings, world)) continue;
            double distanceSquared = living.getLocation().distanceSquared(muzzle);
            if (distanceSquared > (double) range * range || distanceSquared < 0.25) continue;
            if (!visible(world, muzzle, living.getEyeLocation())) continue;
            double score = score(living, settings.focus, distanceSquared);
            if (first || score < bestScore) { best = new Target(living, distanceSquared); bestScore = score; first = false; }
        }
        return best;
    }

    private boolean wanted(LivingEntity living, Settings settings, World world) {
        if (living instanceof Player) {
            if (!settings.players) return false;
            Player player = (Player) living;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
            return world.getPVP() && plugin.authenticated(player) && !player.isDead();
        }
        if (living instanceof ArmorStand) return false;
        if (living instanceof Monster || living instanceof Slime || living instanceof Ghast) return settings.monsters;
        if (living instanceof Animals || living instanceof Villager || living instanceof Golem || living instanceof Ambient) return settings.passives;
        return false;
    }

    private static double score(LivingEntity living, String focus, double distanceSquared) {
        if ("weakest".equals(focus)) return living.getHealth();
        if ("strongest".equals(focus)) {
            double max = living.getHealth();
            try { max = living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(); }
            catch (RuntimeException ignored) { }
            return -max;
        }
        return distanceSquared;
    }

    /** Block-step line of sight that never loads chunks. */
    static boolean visible(World world, Location from, Location to) {
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.001) return true;
        int steps = Math.max(1, (int) Math.ceil(distance * 2));
        for (int i = 1; i < steps; i++) {
            double fraction = (double) i / steps;
            Location point = new Location(world, from.getX() + dx * fraction, from.getY() + dy * fraction, from.getZ() + dz * fraction);
            if (!world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) return false;
            if (point.getBlock().getType().isOccluding()) return false;
        }
        return true;
    }

    /** The owner's Engineering (JasprRPG, by reflection): turrets hit harder for a trained engineer. */
    private double engineering(UUID owner) {
        if (owner == null) return 1.0;
        try {
            if (!engineeringLooked) {
                engineeringLooked = true;
                org.bukkit.plugin.Plugin rpg = plugin.getServer().getPluginManager().getPlugin("JasprRPG");
                if (rpg != null && rpg.isEnabled())
                    engineering = Class.forName("chat.jaspr.rpg.RpgApi", true, rpg.getClass().getClassLoader()).getMethod("turretMultiplier", UUID.class);
            }
            if (engineering == null) return 1.0;
            Object v = engineering.invoke(null, owner);
            return v instanceof Number ? Math.max(1.0, Math.min(3.0, ((Number) v).doubleValue())) : 1.0;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            engineering = null;
            return 1.0;
        }
    }

    /** One hit: credited to the owner (bounties read the metadata), counted when it kills. */
    private void hit(LivingEntity target, double amount, Turret turret, boolean pierce) {
        if (target.isDead() || !target.isValid()) return;
        if (turret.owner != null) target.setMetadata(OWNER_META, new FixedMetadataValue(plugin, turret.owner.toString()));
        if (pierce) target.setNoDamageTicks(0);   // rotary rounds land between a target's recovery frames
        target.damage(amount);
        if (target.isDead()) turret.kills++;
    }

    /** The owner's Engineer's Toolbelt (JasprGear, by reflection): +20% damage and +4 range while they are within 32 blocks. */
    private boolean toolbelt(Turret turret, World world, Location muzzle) {
        if (turret.owner == null) return false;
        Player owner = Bukkit.getPlayer(turret.owner);
        if (owner == null || owner.getWorld() != world || owner.getLocation().distanceSquared(muzzle) > 32 * 32) return false;
        try {
            if (!wearingLooked) {
                wearingLooked = true;
                org.bukkit.plugin.Plugin gear = plugin.getServer().getPluginManager().getPlugin("JasprGear");
                if (gear != null && gear.isEnabled())
                    wearing = Class.forName("chat.jaspr.gear.GearApi", true, gear.getClass().getClassLoader()).getMethod("wearing", Player.class, String.class);
            }
            return wearing != null && Boolean.TRUE.equals(wearing.invoke(null, owner, "engineers_toolbelt"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            wearing = null;
            return false;
        }
    }

    private void fire(World world, Location muzzle, Target target, Turret turret, boolean belt) {
        Tier tier = turret.tier;
        double amount = damage(turret.settings.rate) * tier.damage * engineering(turret.owner) * (belt ? 1.2 : 1.0);
        Location aim = target.entity.getEyeLocation();
        shots++;
        turret.shotCount++;
        fireSound(world, muzzle, aim, tier, turret.shotCount);
        if (tier.cannon()) {
            world.spawnParticle(Particle.SMOKE_LARGE, muzzle, 6, 0.2, 0.2, 0.2, 0.02);
            world.spawnParticle(Particle.EXPLOSION_LARGE, aim, tier == Tier.HOWITZER ? 2 : 1, 0.2, 0.2, 0.2, 0);
            hit(target.entity, amount, turret, false);
            double radius = tier == Tier.HOWITZER ? 4.0 : 3.0;
            for (Entity near : world.getNearbyEntities(aim, radius, radius, radius)) {
                if (!(near instanceof LivingEntity) || near == target.entity) continue;
                LivingEntity other = (LivingEntity) near;
                if (other.isDead() || !wanted(other, turret.settings, world) || other.getLocation().distanceSquared(aim) > radius * radius) continue;
                hit(other, amount * 0.6, turret, false);
                splashHits++;
                if (tier == Tier.HOWITZER) {
                    Vector away = other.getLocation().toVector().subtract(aim.toVector()).setY(0);
                    if (away.lengthSquared() > 1e-4) other.setVelocity(away.normalize().multiply(0.7).setY(0.35));
                }
            }
        } else if (tier.tesla()) {
            int jumps = tier == Tier.ARC_TOWER ? 4 : 2;
            List<LivingEntity> struck = new ArrayList<LivingEntity>();
            struck.add(target.entity);
            bolt(world, muzzle, aim);
            hit(target.entity, amount, turret, false);
            if (tier == Tier.ARC_TOWER) target.entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1), true);
            LivingEntity from = target.entity;
            double share = amount;
            for (int j = 0; j < jumps; j++) {
                LivingEntity next = nextChain(world, from, struck, turret.settings);
                if (next == null) break;
                share *= 0.75;
                bolt(world, from.getEyeLocation(), next.getEyeLocation());
                hit(next, share, turret, false);
                if (tier == Tier.ARC_TOWER) next.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1), true);
                struck.add(next);
                from = next;
                chainHits++;
            }
        } else {
            world.spawnParticle(Particle.SMOKE_NORMAL, muzzle, tier.gatling() ? 2 : 3, 0.15, 0.15, 0.15, 0.01);
            world.spawnParticle(tier.gatling() ? Particle.CRIT : Particle.CRIT_MAGIC, aim, tier.gatling() ? 4 : 8, 0.25, 0.25, 0.25, 0.05);
            hit(target.entity, amount, turret, tier.gatling());
        }
        try { plugin.noise(null, muzzle, plugin.getConfig().getDouble("sentry.noise-radius", 10.0)); }
        catch (RuntimeException ignored) { }
        dirty = true;
    }

    /** The next foe within five blocks of the last one struck (the Tesla's chain). */
    private LivingEntity nextChain(World world, LivingEntity from, List<LivingEntity> struck, Settings settings) {
        LivingEntity best = null;
        double bestD = 25;
        for (Entity near : world.getNearbyEntities(from.getLocation(), 5, 5, 5)) {
            if (!(near instanceof LivingEntity) || struck.contains(near)) continue;
            LivingEntity other = (LivingEntity) near;
            if (other.isDead() || !wanted(other, settings, world)) continue;
            double d = other.getLocation().distanceSquared(from.getLocation());
            if (d < bestD && visible(world, from.getEyeLocation(), other.getEyeLocation())) { bestD = d; best = other; }
        }
        return best;
    }

    private static void bolt(World world, Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len < 0.01) return;
        step.normalize().multiply(0.5);
        Location point = from.clone();
        for (double t = 0; t < len; t += 0.5) {
            point.add(step);
            world.spawnParticle(Particle.FIREWORKS_SPARK, point, 1, 0.03, 0.03, 0.03, 0);
        }
        world.spawnParticle(Particle.SPELL_INSTANT, to, 6, 0.2, 0.3, 0.2, 0);
    }

    /** Each tier sounds like itself when it fires. */
    static void fireSound(World world, Location muzzle, Location impact, Tier tier, int shot) {
        switch (tier) {
            case REINFORCED:
                world.playSound(muzzle, Sound.BLOCK_DISPENSER_LAUNCH, 0.8f, 1.0f);
                break;
            case GATLING:
                world.playSound(muzzle, Sound.BLOCK_NOTE_SNARE, 0.6f, 1.7f);
                if (shot % 4 == 0) world.playSound(muzzle, Sound.BLOCK_PISTON_CONTRACT, 0.25f, 2.0f);
                break;
            case STORM_GATLING:
                world.playSound(muzzle, Sound.BLOCK_NOTE_SNARE, 0.7f, 1.4f);
                world.playSound(muzzle, Sound.BLOCK_NOTE_HAT, 0.5f, 2.0f);
                break;
            case CANNON:
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_LARGE_BLAST, 1.0f, 0.6f);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                break;
            case HOWITZER:
                world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
                break;
            case TESLA:
                world.playSound(muzzle, Sound.ENTITY_LIGHTNING_IMPACT, 0.6f, 1.6f);
                break;
            case ARC_TOWER:
                world.playSound(muzzle, Sound.ENTITY_LIGHTNING_THUNDER, 0.35f, 1.8f);
                world.playSound(impact, Sound.ENTITY_LIGHTNING_IMPACT, 0.7f, 1.2f);
                break;
            default:
                world.playSound(muzzle, Sound.BLOCK_DISPENSER_DISPENSE, 0.7f, 1.4f);
        }
    }

    /** ...and when it locks on to a new target. */
    static void lockSound(World world, Location muzzle, Tier tier) {
        switch (tier) {
            case REINFORCED: world.playSound(muzzle, Sound.BLOCK_NOTE_PLING, 0.5f, 1.6f); break;
            case GATLING: world.playSound(muzzle, Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.9f); break;
            case STORM_GATLING: world.playSound(muzzle, Sound.ENTITY_MINECART_RIDING, 0.4f, 1.8f); break;
            case CANNON: world.playSound(muzzle, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f, 0.6f); break;
            case HOWITZER: world.playSound(muzzle, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.6f); break;
            case TESLA: world.playSound(muzzle, Sound.BLOCK_NOTE_CHIME, 0.5f, 2.0f); break;
            case ARC_TOWER: world.playSound(muzzle, Sound.ENTITY_GUARDIAN_ATTACK, 0.4f, 1.5f); break;
            default: world.playSound(muzzle, Sound.UI_BUTTON_CLICK, 0.4f, 1.8f);
        }
    }
}
