package chat.jaspr.apocalypse;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;

/**
 * Craftable automated defense. A placed dispenser body with server-side targeting:
 * hostile mobs by default, optional passives/players, configurable range, fire
 * rate and target priority. Right-click opens settings, sneak + right-click
 * collects it. Turrets persist across restarts and never load chunks.
 */
public final class SentryTurret implements Listener {
    static final String ID = "sentry_turret";
    private static final String TITLE = "Sentry Turret";
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
        int cooldown;
        long kills;
        transient UUID stand;
        transient double yaw;
    }

    private final ApocalypsePlugin plugin;
    private BukkitTask task;
    private boolean started;
    private final Map<String, Turret> turrets = new LinkedHashMap<String, Turret>();
    private final Map<NamespacedKey, String> recipeIds = new HashMap<NamespacedKey, String>();
    private final Map<UUID, Long> messageAt = new HashMap<UUID, Long>();
    private File file;
    private long shots;

    public SentryTurret(ApocalypsePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (started) return;
        started = true;
        file = new File(plugin.getDataFolder(), "turrets.yml");
        load();
        NamespacedKey key = new NamespacedKey(plugin, "sentry_turret");
        ShapedRecipe recipe = new ShapedRecipe(key, ExpeditionEquipment.item(ID));
        recipe.shape(" I ", "IBI", " I ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('B', Material.IRON_BLOCK);
        if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Cannot register " + key);
        recipeIds.put(key, ID);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        int period = Math.max(1, plugin.getConfig().getInt("sentry.tick-period", 5));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, period, period);
        plugin.getLogger().info("SENTRY_READY turrets=" + turrets.size());
    }

    public void stop() {
        started = false;
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        save();
        messageAt.clear();
    }

    String metrics() { return "turrets=" + turrets.size() + ",shots=" + shots + ",kills=" + kills(); }
    private long kills() { long total = 0; for (Turret turret : turrets.values()) total += turret.kills; return total; }

    // -- Item helpers ------------------------------------------------------
    /** Public classification hook for server-side item systems; identity remains NBT-backed. */
    public static boolean verified(ItemStack item) {
        return item != null && item.getType() == Material.IRON_PICKAXE
            && ID.equals(ApocalypseItems.id(item)) && ExpeditionEquipment.verified(item);
    }
    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
    private static boolean plain(ItemStack item, Material material) {
        return item != null && item.getType() == material && item.getDurability() == 0
            && (ApocalypseItems.id(item) == null || ApocalypseItems.id(item).isEmpty());
    }
    private boolean allowed(Player player) {
        return started && player != null && player.isOnline() && !player.isDead()
            && Arsenal.equipmentWorld(plugin, player.getWorld()) && plugin.isSurvivor(player);
    }
    private boolean gate(Player player) {
        return started && player != null && player.isOnline()
            && Arsenal.equipmentWorld(plugin, player.getWorld()) && plugin.authenticated(player);
    }

    private boolean craftable(Player player) {
        return gate(player) && !player.isDead()
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.CREATIVE);
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
            rows.add(map);
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("turrets", rows);
        try { data.save(file); } catch (java.io.IOException e) { plugin.getLogger().warning("SENTRY_SAVE_FAILED " + e.getMessage()); }
    }

    // -- Crafting ----------------------------------------------------------
    private String recipeId(Recipe recipe) {
        if (!(recipe instanceof Keyed)) return null;
        return recipeIds.get(((Keyed) recipe).getKey());
    }

    private static boolean validMatrix(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) return false;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = matrix[slot];
            boolean edge = slot == 1 || slot == 3 || slot == 5 || slot == 7;
            boolean heart = slot == 4;
            if (edge && !plain(item, Material.IRON_INGOT)) return false;
            if (heart && !plain(item, Material.IRON_BLOCK)) return false;
            if (!edge && !heart && !empty(item)) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (!ID.equals(recipeId(event.getRecipe()))) return;
        if (!(event.getView().getPlayer() instanceof Player) || !craftable((Player) event.getView().getPlayer())
                || !validMatrix(inventory.getMatrix())) { inventory.setResult(null); return; }
        inventory.setResult(ExpeditionEquipment.item(ID));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!ID.equals(recipeId(event.getRecipe()))) return;
        if (!(event.getWhoClicked() instanceof Player) || !craftable((Player) event.getWhoClicked())
                || !validMatrix(event.getInventory().getMatrix())) { event.setCancelled(true); return; }
        if ((event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) || !empty(event.getCursor())) {
            event.setCancelled(true);
            hint((Player) event.getWhoClicked(), "Craft the sentry with one normal click and an empty cursor.");
            return;
        }
        event.setCurrentItem(ExpeditionEquipment.item(ID));
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
    private void tryPlace(Player player, Block clicked, BlockFace face) {
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
        turrets.put(key(turret), turret);
        save();
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_PLACE, 1f, 0.9f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else held.setAmount(held.getAmount() - 1);
        }
        plugin.getLogger().info("SENTRY_PLACE player=" + player.getName() + " at=" + key(turret));
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

    private ItemStack fresh() { return ExpeditionEquipment.item(ID); }

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
                if (stand.isSmall() && key(turret).equals(stand.getCustomName()) && verified(stand.getHelmet())) {
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
        if (found != null && (!found.isSmall() || !verified(found.getHelmet()))) { found.remove(); found = null; turret.stand = null; }
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
        ItemStack helm = fresh();
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
            tryPlace(player, clicked, event.getBlockFace());
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
                Map<Integer, ItemStack> left = player.getInventory().addItem(fresh());
                for (ItemStack rest : left.values()) block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), rest);
            }
            save();
            player.sendMessage(ChatColor.GRAY + "Sentry collected.");
            plugin.getLogger().info("SENTRY_PICKUP player=" + player.getName() + " at=" + key(turret));
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
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), fresh());
        save();
        plugin.getLogger().info("SENTRY_BREAK player=" + event.getPlayer().getName() + " at=" + key(turret));
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
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), fresh());
            plugin.getLogger().info("SENTRY_BLAST at=" + key(turret));
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
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), fresh());
            plugin.getLogger().info("SENTRY_BLAST at=" + key(turret));
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
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggle(String name, boolean on, String hint) {
        return button(Material.WOOL, (short) (on ? 5 : 14), (on ? ChatColor.GREEN : ChatColor.RED) + name + ": " + (on ? "ON" : "OFF"),
            ChatColor.GRAY + hint, ChatColor.DARK_GRAY + "Click to switch.");
    }

    private void render(Inventory menu, Turret turret) {
        Settings settings = turret.settings;
        menu.setItem(10, toggle("Defense", settings.enabled, "The sentry fires while enabled."));
        menu.setItem(11, toggle("Hostile mobs", settings.monsters, "Zombies, skeletons and other monsters."));
        menu.setItem(12, toggle("Passive mobs", settings.passives, "Animals and villagers."));
        menu.setItem(13, toggle("Players", settings.players, "Other survivors where PvP is on."));
        menu.setItem(14, button(Material.PAPER, (short) 0, ChatColor.GOLD + "Range: " + settings.range + " blocks",
            ChatColor.GRAY + "Click to cycle 12 / 24 / 36."));
        menu.setItem(15, button(Material.REDSTONE, (short) 0, ChatColor.GOLD + "Fire rate: " + title(settings.rate),
            ChatColor.GRAY + describeRate(settings.rate), ChatColor.DARK_GRAY + "Click to cycle."));
        menu.setItem(16, button(Material.COMPASS, (short) 0, ChatColor.GOLD + "Targets: " + title(settings.focus),
            ChatColor.GRAY + "Click to cycle nearest / weakest / strongest."));
        menu.setItem(22, button(Material.DISPENSER, (short) 0, ChatColor.AQUA + "Sentry Turret",
            ChatColor.GRAY + "Placed by " + turret.ownerName + " at " + turret.x + ", " + turret.y + ", " + turret.z,
            ChatColor.GRAY + String.valueOf(turret.kills) + " confirmed kills."));
        menu.setItem(26, button(Material.BARRIER, (short) 0, ChatColor.RED + "Close",
            ChatColor.GRAY + "Sneak + right-click collects the sentry."));
    }

    private static String title(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }

    private String describeRate(String rate) {
        if ("slow".equals(rate)) return "Heavy rounds: " + damage("slow") + " damage, deliberate pace.";
        if ("fast".equals(rate)) return "Light rounds: " + damage("fast") + " damage, rapid pace.";
        return "Standard rounds: " + damage("normal") + " damage, steady pace.";
    }

    private double damage(String rate) {
        if ("slow".equals(rate)) return Math.max(1, plugin.getConfig().getDouble("sentry.slow-damage", 16.0));
        if ("fast".equals(rate)) return Math.max(1, plugin.getConfig().getDouble("sentry.fast-damage", 8.0));
        return Math.max(1, plugin.getConfig().getDouble("sentry.normal-damage", 12.0));
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
        if (event.getView() == null || !TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!gate(player) || event.getRawSlot() != event.getSlot()
                || event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.CHEST) return;
        Turret turret = openTurret(player);
        if (turret == null) { player.closeInventory(); return; }
        Settings settings = turret.settings;
        switch (event.getSlot()) {
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
            if (!turret.settings.enabled) { turret.cooldown = 0; syncStand(turret, world); continue; }
            Location muzzle = new Location(world, turret.x + 0.5, turret.y + 1.2, turret.z + 0.5);
            Target target = acquire(world, muzzle, turret.settings);
            aim(turret, world, target, muzzle);
            if (target == null) { turret.cooldown = 0; continue; }
            turret.cooldown -= period;
            if (turret.cooldown > 0) continue;
            turret.cooldown = interval(turret.settings.rate);
            fire(world, muzzle, target, turret);
        }
    }

    private static final class Target {
        final LivingEntity entity;
        final double distanceSquared;
        Target(LivingEntity entity, double distanceSquared) { this.entity = entity; this.distanceSquared = distanceSquared; }
    }

    private Target acquire(World world, Location muzzle, Settings settings) {
        Collection<Entity> nearby = world.getNearbyEntities(muzzle, settings.range, settings.range, settings.range);
        Target best = null;
        double bestScore = 0;
        boolean first = true;
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living.isDead() || !living.isValid()) continue;
            if (!wanted(living, settings, world)) continue;
            double distanceSquared = living.getLocation().distanceSquared(muzzle);
            if (distanceSquared > (double) settings.range * settings.range || distanceSquared < 0.25) continue;
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

    private void fire(World world, Location muzzle, Target target, Turret turret) {
        double amount = damage(turret.settings.rate);
        Location aim = target.entity.getEyeLocation();
        target.entity.damage(amount);
        if (target.entity.isDead()) turret.kills++;
        shots++;
        world.playSound(muzzle, Sound.BLOCK_DISPENSER_DISPENSE, 0.7f, 1.4f);
        world.spawnParticle(Particle.SMOKE_NORMAL, muzzle, 3, 0.15, 0.15, 0.15, 0.01);
        world.spawnParticle(Particle.CRIT_MAGIC, aim, 8, 0.25, 0.25, 0.25, 0.05);
        try { plugin.noise(null, muzzle, plugin.getConfig().getDouble("sentry.noise-radius", 10.0)); }
        catch (RuntimeException ignored) { }
        if (turrets.containsKey(key(turret))) save();
    }
}
