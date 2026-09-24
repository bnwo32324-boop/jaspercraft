package chat.jaspr.apocalypse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/** Server-thread equipment subsystem. NBT marks identify server-issued gear, not privileged NBT forgery. */
public final class ExpeditionEquipment implements Listener {
    private static final String TAG = "JasprApocalypse", MARK = "jaspr-expedition-v1";
    private static final Map<String, Spec> SPECS = new LinkedHashMap<String, Spec>();
    private static final Attribute[] ATTRIBUTES = {Attribute.GENERIC_MOVEMENT_SPEED, Attribute.GENERIC_ARMOR,
        Attribute.GENERIC_KNOCKBACK_RESISTANCE, Attribute.GENERIC_ATTACK_SPEED, Attribute.GENERIC_ATTACK_DAMAGE};
    private static final String[] SETS = {"bulwark", "ranger", "spectre", "hazmat"};
    private static final String[] PARTS = {"boots", "leggings", "chestplate", "helmet"};
    // Flat, separately owned modifiers: never alter a base value or remove another plugin's modifier.
    private static final double[][] PERKS = {{-0.015, 4, 0.35, 0, 0}, {0.025, -2, 0, 0.4, 0},
        {0.015, -3, 0, 0, 3}, {0, 2, 0.15, -0.3, 0}};
    private static final String[] PERK_TEXT = {"Full set: +4 armor, +35% knockback resistance, -15% base speed",
        "Full set: +25% base speed, +0.4 attack speed, -2 armor",
        "Full set: +15% base speed, +3 melee damage, -3 armor",
        "Full set: +2 armor, +15% knockback resistance, -0.3 attack speed; 35% less fire/lava, 25% less magic/poison"};

    static {
        melee("trench_blade", "Trench Blade", 1530, 10, 350, "Sneaking strikes deal 40% more damage");
        melee("breacher_axe", "Breacher Axe", 1520, 18, 1100, "+40% against targets with at least 12 armor");
        melee("mono_katana", "Monofilament Katana", 1510, 15, 550, "Fast, consistent precision cuts");
        melee("shock_baton", "Shock Baton", 1500, 12, 650, "+35% against undead");
        melee("gravity_maul", "Gravity Maul", 1490, 26, 1800, "+25% while braced stationary");
        melee("reaper_scythe", "Reaper Scythe", 1480, 19, 1250, "+30% against targets below 40% health");
        melee("thermal_machete", "Thermal Machete", 1470, 16, 800, "+35% against already-burning targets");
        melee("sentinel_spear", "Sentinel Spear", 1460, 17, 1000, "+30% at 2.4+ blocks; vanilla reach still applies");
        melee("gravespike", "Gravespike Trench Dirk", 1450, 12, 450, "Sneaking strikes deal 40% more damage");
        melee("railpick", "Railworker's Armor Pick", 1440, 17, 1050, "+40% against targets with at least 12 armor");
        melee("wardcleaver", "Ward Cleaver", 1430, 15, 850, "+35% against zombies and skeletons");
        melee("pilgrim_lance", "Ash Pilgrim's Lance", 1420, 20, 1250, "+30% at 2.4+ blocks; vanilla reach still applies");
        melee("cautery_sabre", "Cautery Sabre", 1410, 18, 950, "+35% against already-burning targets");
        melee("suture_sickle", "Suture Harvest Sickle", 1400, 14, 700, "+30% against targets below 40% health");
        melee("tollhammer", "Last Toll Bell Hammer", 1390, 28, 2000, "+25% while braced stationary");
        melee("rebar_sword", "Quarantine Rebar Greatsword", 1380, 22, 1350, "Heavy, consistent salvage blade");
        melee("vesper_dagger", "Vesper Ritual Dagger", 1370, 11, 400, "Sneaking strikes deal 40% more damage");
        melee("hollow_halberd", "Hollow Watch Halberd", 1360, 23, 1500, "+40% against targets with at least 12 armor");
        melee("ossuary_flail", "Ossuary Chain Flail", 1350, 21, 1400, "+35% against zombies and skeletons");
        melee("ember_falchion", "Ember Procession Falchion", 1340, 19, 1150, "+35% against already-burning targets");
        melee("mourning_glaive", "Mourning Station Glaive", 1330, 18, 1100, "+30% at 2.4+ blocks; vanilla reach still applies");
        melee("altar_mallet", "Silent Altar Mallet", 1320, 24, 1700, "+25% while braced stationary");
        melee("execution_sword", "Final Verdict Execution Sword", 1310, 25, 1800, "+30% against targets below 40% health");
        melee("wire_whip", "Razorwire Scourge", 1300, 13, 600, "Fast consistent cuts; vanilla reach still applies");
        for (int set = 0; set < SETS.length; set++) for (String part : PARTS) {
            String id = SETS[set] + "_" + part;
            add(new Spec(id, title(SETS[set]) + " Exoskeleton " + title(part), "armor",
                    Material.valueOf("DIAMOND_" + part.toUpperCase(java.util.Locale.ROOT)), 10 + set * 10,
                    0, 0, PERK_TEXT[set]));
        }
        add(new Spec("sentry_turret", "Sentry Turret", "block", Material.IRON_PICKAXE, 100, 0, 0, "Placeable automated defense.|Right-click: settings.|Sneak + right-click: collect."));
        add(new Spec("portal_gun", "Portal Gun", "gadget", Material.DIAMOND_HOE, 0, 0, 0, "Right-click: cyan portal|Sneak + right-click: amber portal|Anyone can travel through your portals"));
        add(new Spec("alloy_plate", "Tempered Alloy Plate", "material", Material.IRON_INGOT, 0, 0, 0, "Combine with a Power Cell to press 48 cartridges"));
        add(new Spec("weapon_core", "Ancient Weapon Core", "material", Material.QUARTZ, 0, 0, 0, "Combine with Military Salvage to recover 4 alloy plates"));
        add(new Spec("power_cell", "Sealed Power Cell", "material", Material.PRISMARINE_SHARD, 0, 0, 0, "Powers ammunition presses and coolant injectors"));
        add(new Spec("ballistic_fiber", "Ballistic Fiber", "material", Material.STRING, 0, 0, 0, "Combine with a golden apple to make a Trauma Kit"));
        add(new Spec("trauma_kit", "Trauma Kit", "consumable", Material.MAGMA_CREAM, 0, 0, 20000, "Right-click: recover up to 8 health; 20s supply cooldown"));
        add(new Spec("field_ration", "Expedition Field Ration", "consumable", Material.CLAY_BALL, 0, 0, 10000, "Right-click: recover up to 6 food and 3 saturation; 10s supply cooldown"));
        add(new Spec("coolant_injector", "Coolant Injector", "consumable", Material.SLIME_BALL, 0, 0, 15000, "Right-click: extinguish fire; 15s supply cooldown"));
        add(new Spec("sanitized_flesh", "Sanitized Flesh", "consumable", Material.COOKED_BEEF, 0, 0, 0, "Right-click: up to 8 food, rich saturation, never sickens"));
    }

    private static final class Spec {
        final String id, title, category, perk;
        final Material material;
        final int model;
        final double damage;
        final long cooldown;
        Spec(String id, String title, String category, Material material, int model, double damage, long cooldown, String perk) {
            this.id = id; this.title = title; this.category = category; this.material = material;
            this.model = model; this.damage = damage; this.cooldown = cooldown; this.perk = perk;
        }
    }
    private static void add(Spec spec) { SPECS.put(spec.id, spec); }
    private static void melee(String id, String title, int model, double damage, long cooldown, String perk) {
        add(new Spec(id, title, "melee", Material.DIAMOND_SWORD, model, damage, cooldown, perk));
    }
    private static String title(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }

    public static Map<String, String> catalogue(String category) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Spec spec : SPECS.values()) if ("all".equals(category) || spec.category.equals(category)) result.put(spec.id, spec.title);
        return Collections.unmodifiableMap(result);
    }
    public static ItemStack item(String id) {
        Spec spec = SPECS.get(id);
        if (spec == null) throw new IllegalArgumentException("Unknown expedition item: " + id);
        ItemStack item = new ItemStack(spec.material, 1, (short) spec.model);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + spec.title);
        // Long single-line lore overflows the tooltip, so perks may carry short
        // lines separated by '|'; no shipped perk contains a literal pipe.
        java.util.List<String> lore = new java.util.ArrayList<String>();
        for (String line : spec.perk.split("\\|", -1)) lore.add(ChatColor.GRAY + line.trim());
        lore.add(spec.damage > 0
            ? ChatColor.GRAY + "Damage: " + spec.damage + " | Recovery: " + spec.cooldown + "ms"
            : ChatColor.DARK_GRAY + "Recovered expedition equipment");
        meta.setLore(lore);
        if (spec.model > 0) { meta.setUnbreakable(true); meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE); }
        item.setItemMeta(meta);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.getTag(), data = new NBTTagCompound();
        data.setString("id", id); data.setString("equipmentMark", MARK);
        if (spec.model > 0) data.setString("serial", UUID.randomUUID().toString());
        root.set(TAG, data); nms.setTag(root);
        // Gear leaves this plugin plain. Whether it becomes an enhanced armament is decided
        // by JasprRPG when it actually reaches a player, which keeps the two decoupled.
        return CraftItemStack.asBukkitCopy(nms);
    }
    private static Spec identify(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return null;
        Spec spec = SPECS.get(ApocalypseItems.id(item));
        if (spec == null || item.getType() != spec.material || item.getDurability() != spec.model) return null;
        NBTTagCompound data = CraftItemStack.asNMSCopy(item).getTag().getCompound(TAG);
        if (!MARK.equals(data.getString("equipmentMark"))) return null;
        if (spec.model > 0 && (item.getAmount() != 1 || !item.hasItemMeta() || !item.getItemMeta().isUnbreakable()
                || !validSerial(data.getString("serial")))) return null;
        return spec;
    }
    private static boolean validSerial(String value) {
        try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException ignored) { return false; }
    }
    public static boolean verified(ItemStack item) { return identify(item) != null; }
    public static boolean isMelee(ItemStack item) {
        Spec spec = identify(item); return spec != null && "melee".equals(spec.category);
    }
    public static boolean isArmor(ItemStack item) {
        Spec spec = identify(item); return spec != null && "armor".equals(spec.category);
    }

    private final ApocalypsePlugin plugin;
    private BukkitTask task;
    private final Map<UUID, Long> meleeReady = new HashMap<UUID, Long>(), supplyReady = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastConsume = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> active = new HashMap<UUID, Integer>();
    public ExpeditionEquipment(ApocalypsePlugin plugin) { this.plugin = plugin; }
    public void start() {
        if (task != null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) strip(player);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player); }
        }, 1L, 1L);
    }
    public void stop() {
        if (task != null) task.cancel(); task = null;
        HandlerList.unregisterAll(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) strip(player);
        active.clear(); meleeReady.clear(); supplyReady.clear(); lastConsume.clear();
    }
    private boolean allowed(Player player) {
        return task != null && plugin.isSurvivor(player) && Arsenal.equipmentWorld(plugin, player.getWorld());
    }
    private static int fullSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents(); // boots, leggings, chestplate, helmet
        if (armor.length != 4) return -1;
        Spec boots = identify(armor[0]);
        if (boots == null) return -1;
        for (int set = 0; set < SETS.length; set++) {
            if (!boots.id.equals(SETS[set] + "_boots")) continue;
            boolean match = true;
            for (int slot = 1; slot < 4; slot++) {
                Spec spec = identify(armor[slot]);
                if (spec == null || !spec.id.equals(SETS[set] + "_" + PARTS[slot])) { match = false; break; }
            }
            if (match) return set;
        }
        return -1;
    }
    private static UUID modifierId(Attribute attribute) {
        return UUID.nameUUIDFromBytes((MARK + ":" + attribute.name()).getBytes(StandardCharsets.UTF_8));
    }
    private void strip(Player player) {
        for (Attribute attribute : ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : new ArrayList<AttributeModifier>(instance.getModifiers())) {
                if (modifierId(attribute).equals(modifier.getUniqueId())) instance.removeModifier(modifier);
            }
        }
        active.remove(player.getUniqueId());
    }
    private void refresh(Player player) {
        int set = allowed(player) ? fullSet(player) : -1;
        Integer before = active.get(player.getUniqueId());
        if (before != null && before == set) return;
        if (before != null) strip(player);
        if (set < 0) return;
        // Also remove a stale owned modifier after a crash/reconnect before granting this set.
        strip(player);
        for (int i = 0; i < ATTRIBUTES.length; i++) if (PERKS[set][i] != 0) {
            AttributeInstance instance = player.getAttribute(ATTRIBUTES[i]);
            if (instance != null) instance.addModifier(new AttributeModifier(modifierId(ATTRIBUTES[i]), MARK,
                PERKS[set][i], AttributeModifier.Operation.ADD_NUMBER));
        }
        active.put(player.getUniqueId(), set);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) refresh((Player) event.getEntity());
        if (event instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player)
            refresh((Player) ((EntityDamageByEntityEvent) event).getDamager());
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        ItemStack held = player.getInventory().getItemInMainHand();
        Spec spec = identify(held);
        if (spec == null || !"melee".equals(spec.category)) return;
        if (!allowed(player) || !(event.getEntity() instanceof LivingEntity)) { event.setCancelled(true); return; }
        LivingEntity target = (LivingEntity) event.getEntity();
        if (target instanceof Player && (!player.getWorld().getPVP() || !allowed((Player) target))) { event.setCancelled(true); return; }
        long now = System.nanoTime();
        Long ready = meleeReady.get(player.getUniqueId());
        if (ready != null && now < ready) { event.setCancelled(true); return; }
        meleeReady.put(player.getUniqueId(), now + spec.cooldown * 1000000L);
        double factor = 1;
        if ("trench_blade".equals(spec.id) && player.isSneaking()) factor = 1.4;
        AttributeInstance armor = target.getAttribute(Attribute.GENERIC_ARMOR);
        if ("breacher_axe".equals(spec.id) && armor != null && armor.getValue() >= 12) factor = 1.4;
        if ("shock_baton".equals(spec.id) && (target instanceof org.bukkit.entity.Zombie || target instanceof org.bukkit.entity.Skeleton)) factor = 1.35;
        if ("gravity_maul".equals(spec.id) && player.getVelocity().lengthSquared() < 0.0064) factor = 1.25;
        if ("reaper_scythe".equals(spec.id) && target.getHealth() < target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 0.4) factor = 1.3;
        if ("thermal_machete".equals(spec.id) && target.getFireTicks() > 0) factor = 1.35;
        if ("sentinel_spear".equals(spec.id) && player.getLocation().distanceSquared(target.getLocation()) >= 5.76) factor = 1.3;
        // New specialties stay inside this original damage event and its native reach/charge checks.
        // No secondary hits, forced health changes, fire placement, or extra block edits.
        switch (spec.id) {
            case "gravespike": case "vesper_dagger": if (player.isSneaking()) factor = 1.4; break;
            case "railpick": case "hollow_halberd": if (armor != null && armor.getValue() >= 12) factor = 1.4; break;
            case "wardcleaver": case "ossuary_flail":
                if (target instanceof org.bukkit.entity.Zombie || target instanceof org.bukkit.entity.Skeleton) factor = 1.35;
                break;
            case "tollhammer": case "altar_mallet": if (player.getVelocity().lengthSquared() < 0.0064) factor = 1.25; break;
            case "suture_sickle": case "execution_sword":
                if (target.getHealth() < target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 0.4) factor = 1.3;
                break;
            case "cautery_sabre": case "ember_falchion": if (target.getFireTicks() > 0) factor = 1.35; break;
            case "pilgrim_lance": case "mourning_glaive":
                if (player.getLocation().distanceSquared(target.getLocation()) >= 5.76) factor = 1.3;
                break;
            default: break;
        }
        // Scale the native diamond-sword hit, retaining attack-charge/critical/other-plugin adjustments.
        // Remain in the original cancellable event: no second damage call or forced health change.
        event.setDamage(event.getDamage() * (spec.damage / 7.0) * factor);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hazards(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!allowed(player) || fullSet(player) != 3) return;
        switch (event.getCause()) {
            case FIRE: case FIRE_TICK: case LAVA: event.setDamage(event.getDamage() * 0.65); break;
            case POISON: case MAGIC: event.setDamage(event.getDamage() * 0.75); break;
            default: break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void consume(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) hand = EquipmentSlot.HAND;
        boolean offhand = hand == EquipmentSlot.OFF_HAND;
        // New click starts with the main hand: drop any stale same-click guard so a
        // fresh offhand eat is never blocked by an older meal. The guard is only set
        // by a main-hand eat and only checked by the off hand (see below).
        if (!offhand && hand == EquipmentSlot.HAND) lastConsume.remove(event.getPlayer().getUniqueId());
        Spec spec = identify(event.getItem());
        if (spec == null || !"consumable".equals(spec.category)) return;
        boolean denied = event.useItemInHand() == Event.Result.DENY || (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.useInteractedBlock() == Event.Result.DENY);
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (denied || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) || !allowed(player)) return;
        long now = System.nanoTime();
        // Vanilla single-eat behavior: main hand takes precedence. When the off hand
        // fires, ignore it if the main hand currently holds a consumable that could
        // be used (including its shared supply cooldown). Otherwise a single click
        // with food in both hands would eat twice.
        if (offhand && usableNow(player, identify(player.getInventory().getItemInMainHand()), now)) return;
        // Same-click double-fire guard: Bukkit fires HAND then OFF_HAND for one click.
        // After a main-hand eat the main slot is empty, so the precedence check above
        // would no longer see it. Ignore an offhand eat within 100ms of a main-hand eat.
        if (offhand) {
            Long last = lastConsume.get(player.getUniqueId());
            if (last != null && now - last < 100000000L) return;
        }
        ItemStack held = offhand ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (identify(held) != spec) return;
        // Staple foods carry no cooldown; timed supplies share one gate.
        if (spec.cooldown > 0) {
            Long ready = supplyReady.get(player.getUniqueId());
            if (ready != null && now < ready) return;
        }
        double healing = 0;
        int food = player.getFoodLevel();
        if ("trauma_kit".equals(spec.id)) {
            double missing = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() - player.getHealth();
            if (missing <= 0) return;
            EntityRegainHealthEvent heal = new EntityRegainHealthEvent(player, Math.min(8, missing), EntityRegainHealthEvent.RegainReason.CUSTOM);
            plugin.getServer().getPluginManager().callEvent(heal);
            if (heal.isCancelled() || !Double.isFinite(heal.getAmount()) || heal.getAmount() <= 0) return;
            healing = Math.min(8, heal.getAmount());
        } else if ("field_ration".equals(spec.id)) {
            if (food >= 20) return;
            FoodLevelChangeEvent feed = new FoodLevelChangeEvent(player, Math.min(20, food + 6));
            plugin.getServer().getPluginManager().callEvent(feed);
            if (feed.isCancelled() || feed.getFoodLevel() <= food) return;
            food = Math.min(20, Math.min(food + 6, feed.getFoodLevel()));
        } else if ("sanitized_flesh".equals(spec.id)) {
            if (food >= 20) return;
            FoodLevelChangeEvent feed = new FoodLevelChangeEvent(player, Math.min(20, food + 8));
            plugin.getServer().getPluginManager().callEvent(feed);
            if (feed.isCancelled() || feed.getFoodLevel() <= food) return;
            food = Math.min(20, Math.min(food + 8, feed.getFoodLevel()));
        } else if (player.getFireTicks() <= 0) return;
        // Event handlers may swap the inventory or revoke authentication. Recheck before consumption.
        ItemStack current = offhand ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (!allowed(player) || !held.equals(current) || identify(current) != spec) return;
        ItemStack rest = current.clone(); rest.setAmount(current.getAmount() - 1);
        ItemStack remaining = rest.getAmount() == 0 ? null : rest;
        if (offhand) player.getInventory().setItemInOffHand(remaining);
        else player.getInventory().setItemInMainHand(remaining);
        // Only main-hand eats arm the same-click guard; offhand eats never block later clicks.
        if (!offhand) lastConsume.put(player.getUniqueId(), now);
        if (spec.cooldown > 0) supplyReady.put(player.getUniqueId(), now + spec.cooldown * 1000000L);
        if (healing > 0) player.setHealth(Math.min(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), player.getHealth() + healing));
        else if ("field_ration".equals(spec.id)) {
            player.setFoodLevel(food); player.setSaturation(Math.min(food, player.getSaturation() + 3));
        } else if ("sanitized_flesh".equals(spec.id)) {
            player.setFoodLevel(food); player.setSaturation(Math.min(food, player.getSaturation() + 8));
        } else player.setFireTicks(0);
    }

    /** State-only check (no events): could this consumable be used right now? Used for main-hand precedence. */
    private boolean usableNow(Player player, Spec spec, long now) {
        if (spec == null || !"consumable".equals(spec.category)) return false;
        if (spec.cooldown > 0) {
            Long ready = supplyReady.get(player.getUniqueId());
            if (ready != null && now < ready) return false;
        }
        if ("trauma_kit".equals(spec.id)) {
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            return max != null && max.getValue() - player.getHealth() > 0;
        }
        if ("field_ration".equals(spec.id) || "sanitized_flesh".equals(spec.id)) return player.getFoodLevel() < 20;
        return player.getFireTicks() > 0;
    }

    /** Exact shapeless inputs, shared by preview and pickup. Null means not an equipment recipe. */
    static String[] recipeIngredients(String id) {
        if ("ammo_power".equals(id)) return new String[] {"power_cell", "alloy_plate"};
        if ("trauma_kit".equals(id)) return new String[] {"ballistic_fiber", "@GOLDEN_APPLE"};
        if ("coolant_injector".equals(id)) return new String[] {"power_cell", "@SLIME_BALL"};
        if ("alloy_plate".equals(id)) return new String[] {"weapon_core", "scrap"};
        return null;
    }
    static boolean validRecipe(String id, ItemStack[] matrix) {
        String[] ingredients = recipeIngredients(id);
        if (ingredients == null) return false;
        java.util.List<String> remaining = new ArrayList<String>(Arrays.asList(ingredients));
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() == 0) continue;
            String match = null;
            for (String ingredient : remaining) {
                if (ingredient.startsWith("@")) {
                    if (item.getType() == Material.valueOf(ingredient.substring(1)) && item.getDurability() == 0
                            && ApocalypseItems.id(item).isEmpty()) match = ingredient;
                } else if ("scrap".equals(ingredient)) {
                    if ("scrap".equals(ApocalypseItems.id(item)) && item.getType() == Material.IRON_NUGGET && item.getDurability() == 0) match = ingredient;
                } else if (verified(item) && ingredient.equals(ApocalypseItems.id(item))) match = ingredient;
                if (match != null) break;
            }
            if (match == null) return false;
            remaining.remove(match);
        }
        return remaining.isEmpty();
    }
    static ItemStack recipeOutput(String id) {
        ItemStack result = item(id); if ("alloy_plate".equals(id)) result.setAmount(4); return result;
    }
    private static boolean containsGear(ItemStack[] items) {
        for (ItemStack item : items) {
            Spec spec = SPECS.get(ApocalypseItems.id(item));
            if (spec != null && spec.model > 0) return true;
        }
        return false;
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void repair(PrepareItemCraftEvent event) {
        if (containsGear(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void anvil(PrepareAnvilEvent event) {
        if (containsGear(new ItemStack[] {event.getInventory().getItem(0), event.getInventory().getItem(1)})) event.setResult(null);
    }
    @EventHandler public void inventory(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player) strip((Player) event.getWhoClicked()); }
    @EventHandler public void inventory(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player) strip((Player) event.getWhoClicked()); }
    // The browser client predicts shift-click transfers with vanilla furnace
    // recipes only, so server-known recipes (sanitized flesh) desync the
    // window: the moved stack renders invisible until a full resync. Heal it.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void furnaceResync(InventoryClickEvent event) {
        try {
            if (!event.isShiftClick() || !(event.getWhoClicked() instanceof Player)) return;
            org.bukkit.inventory.Inventory top;
            try { top = event.getView().getTopInventory(); }
            catch (RuntimeException e) { return; }
            if (top == null || top.getType() != org.bukkit.event.inventory.InventoryType.FURNACE) return;
            final Player player = (Player) event.getWhoClicked();
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    try { player.updateInventory(); } catch (RuntimeException ignored) { }
                }
            }, 1L);
        } catch (RuntimeException ignored) { }
    }
    @EventHandler public void join(PlayerJoinEvent event) { strip(event.getPlayer()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { strip(event.getPlayer()); }
    @EventHandler public void mode(PlayerGameModeChangeEvent event) { strip(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { strip(event.getEntity()); }
    @EventHandler public void quit(PlayerQuitEvent event) { strip(event.getPlayer()); meleeReady.remove(event.getPlayer().getUniqueId()); supplyReady.remove(event.getPlayer().getUniqueId()); lastConsume.remove(event.getPlayer().getUniqueId()); }

    public static String[] guidePages() {
        return new String[] {
            "EXPEDITION FIREARMS\n\nWhisper: suppressed rifle.\nTempest: 3 aimed shots, 0.55s apart.\nBastion: braced autocannon.\nLongwatch: long-range scout.\nSunlance: piercing plasma.\nAdjudicator: steady marksman.\nCyclops: heavy scattergun.\nFrostbite: four-body flechette.",
            "EXPEDITION BLADES\n\nTrench Blade: sneak strikes.\nBreacher Axe: armored prey.\nMonofilament Katana: fast cuts.\nShock Baton: undead hunter.\nGravity Maul: brace still.\nReaper Scythe: finish weak foes.\nThermal Machete: burning prey.\nSentinel Spear: reach-edge hits.",
            "SALVAGED SIDEARMS\n\nSepulcher: service pistol.\nVesper: suppressed pistol.\nOssuary: heavy hand cannon.\nTurnstile: snub revolver.\nCinder: machine pistol.\n\nEvery gun loads Forged Cartridges. No ammo is conjured by the weapon.",
            "PATROL & WATCH\n\nTunnel Rat: patrol carbine.\nBlack Box: tracked burst.\nQuarantine: brace for +30%.\nSignal Lost: +35% past 32m.\nGallows: heavy bolt rifle.\nWatchtower: braced penetrator.\nWhiteout: suppressed scout.",
            "SCATTER & RELICS\n\nBellringer: double barrel.\nLockjaw: narrow pump spread.\nChoir: twelve-pellet volley.\nAshfall: drum shotgun.\nNull Point: needle rail.\nCenotaph: siege rail.\n\nPiercing crosses bodies, never walls.",
            "FORBIDDEN ARSENAL\n\nWitchlight: three arc rays.\nStormcoil: four-body lance.\nHexbreaker: paired occult rays.\nPallbearer: braced belt gun.\nIron Psalm: braced rotary.\nDead Frequency: tracked burst.\n\nOccult shots never destroy blocks.",
            "RUIN BLADES I\n\nGravespike, Vesper Dagger: sneak strikes.\nRailpick, Hollow Halberd: armored targets.\nWard Cleaver, Ossuary Flail: zombies and skeletons.\nAsh Pilgrim Lance, Mourning Glaive: reach-edge strikes; vanilla reach only.",
            "RUIN BLADES II\n\nCautery Sabre, Ember Falchion: already-burning targets.\nSuture Sickle, Final Verdict: finish wounded prey.\nLast Toll Hammer, Altar Mallet: brace still.\nRebar Greatsword: heavy cuts.\nRazorwire Scourge: fast cuts, vanilla reach.",
            "EXOSKELETONS\n\nWear all FOUR matching pieces for perks.\n\nBulwark: armor and stability; slower.\nRanger: movement and attack speed; less armor.\nSpectre: speed and melee damage; less armor.\nHazmat: fire and magic resistance; slower attacks.\n\nExisting potions are preserved.",
            "FIELD SUPPLIES\n\nRight-click a Trauma Kit to heal, Field Ration to eat, or Coolant Injector to extinguish fire. Shared supply cooldown: 10-20s.\n\nPower Cell + Alloy Plate = 48 ammo.\nFiber + Golden Apple = Trauma Kit.\nPower Cell + Slimeball = Coolant.\nWeapon Core + Military Salvage = 4 plates.",
            "EXPEDITION RULES\n\nLoot guns arrive empty. Reload before exploring. All gear works in the survival world, Nether, End and jaspr_backrooms.\n\nFull-set perks end when unequipped, logged out, unauthenticated or outside survival/adventure. Gear creates no free drops or ammo."
        };
    }
}
