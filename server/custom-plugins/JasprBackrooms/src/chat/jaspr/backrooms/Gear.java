package chat.jaspr.backrooms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * What the Backrooms' gear does ({@link Items}). Worn sets give their bonus with all four pieces; trinkets work while
 * carried anywhere in the inventory (the Roll of Packing Tape in the off hand), right-click trinkets have cooldowns, and a
 * few weapons have a special. The effects work in every world. The items are never used up as their vanilla selves
 * (placed, crafted with, used on mobs or water).
 */
final class Gear implements Listener {
    private static final UUID STEADY = UUID.fromString("7f2c1b6e-5a3d-4e8b-9c1d-2b7a6e5f4d31");

    /** What one player carries, recomputed every second. */
    static final class Carry {
        final Set<String> ids = new HashSet<>();
        final int[] worn = new int[Level.ALL.length];
        String offHand;
        boolean full(Level lv) { return worn[lv.index()] >= 4; }
        boolean has(String id) { return ids.contains(id); }
    }

    private final BackroomsPlugin plugin;
    private final Map<UUID, Carry> carry = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> struck = new HashMap<>(), savedAt = new HashMap<>();
    long abilities, saves, arcs;

    Gear(BackroomsPlugin plugin) { this.plugin = plugin; }

    // Own icons (owner 2026-10-05: every trinket has its own texture): trinkets made before that are upgraded where they are found.
    private void icons(org.bukkit.inventory.Inventory inventory, String why) {
        int n = Items.upgrade(inventory);
        if (n > 0) plugin.getLogger().info("BACKROOMS_TRINKET_ICONS upgraded=" + n + " via=" + why);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void iconsOnJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (p.isOnline()) icons(p.getInventory(), "join"); });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void iconsOnOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (e.getInventory().getHolder() instanceof Player) return;
        icons(e.getInventory(), "container");
        if (e.getPlayer() instanceof Player) icons(((Player) e.getPlayer()).getInventory(), "open");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void iconsOnPickup(org.bukkit.event.player.PlayerPickupItemEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (p.isOnline()) icons(p.getInventory(), "pickup"); });
    }

    /** A trinket is never a tool: right-clicking a block with its stone spade makes no path (the click itself still works). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noToolUse(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && Skin.carrier(e.getItem())) e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    Carry of(Player p) { return carry.getOrDefault(p.getUniqueId(), new Carry()); }

    // ---- queries other parts ask --------------------------------------------------------------------------------------
    /** Stealth: the Wanderer set (monsters notice you only within 8 blocks), the ID badge and the Executive set (office and city monsters ignore you until you strike). */
    boolean unnoticed(Player p, Entity mob) {
        Carry c = of(p);
        double d2 = p.getLocation().distanceSquared(mob.getLocation());
        if (c.full(Level.YELLOW) && d2 > 64) return true;
        boolean struckLately = System.currentTimeMillis() - struck.getOrDefault(p.getUniqueId(), 0L) < 10_000L;
        if (struckLately) return false;
        String lv = null;
        for (String t : mob.getScoreboardTags()) if (t.startsWith(Mobs.LEVEL)) lv = t.substring(Mobs.LEVEL.length());
        boolean office = "5".equals(lv), city = "6".equals(lv);
        return c.has("employee_badge") && (office || city) || c.full(Level.OFFICE) && office;
    }

    boolean revealsMimics(Player p) { return of(p).full(Level.CITY); }
    boolean steady(Player p) { return of(p).full(Level.WAREHOUSE); }
    boolean swimmer(Player p) { Carry c = of(p); return c.full(Level.POOLS) || c.has("rubber_duck"); }
    /** Share of shocks and lightning that still hurt: none with the Lineman set, 30% with a Surge Protector. */
    double shockFactor(Player p) { Carry c = of(p); return c.full(Level.ELECTRICAL) ? 0 : c.has("surge_protector") ? 0.3 : 1; }
    /** How fast heat rises: not at all in a Boiler Suit, half as fast with a Pressure Gauge. */
    double heatRate(Player p) { Carry c = of(p); return c.full(Level.TUNNELS) ? 0 : c.has("pressure_gauge") ? 0.5 : 1; }

    // ---- every second ---------------------------------------------------------------------------------------------------
    /** JasprBlight's immunity mark (per plugin): the Backrooms' water is clean, whatever the overworld's blight. */
    static final String BLIGHT_IMMUNE = "jaspr_blight_immune";

    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isBackrooms(p.getWorld())) { if (!p.hasMetadata(BLIGHT_IMMUNE)) p.setMetadata(BLIGHT_IMMUNE, new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE)); }
            else p.removeMetadata(BLIGHT_IMMUNE, plugin);
            Carry c = scan(p);
            carry.put(p.getUniqueId(), c);
            if (c.ids.isEmpty()) { steady(p, false); continue; }
            double hp = p.getHealth() / Math.max(1, p.getMaxHealth());
            boolean wet = p.getLocation().getBlock().isLiquid();
            if (c.full(Level.TUNNELS)) apply(p, PotionEffectType.FIRE_RESISTANCE, 0);
            if (c.full(Level.OFFICE)) apply(p, PotionEffectType.FAST_DIGGING, 1);
            if (c.full(Level.POOLS)) { apply(p, PotionEffectType.WATER_BREATHING, 0); if (wet) { apply(p, PotionEffectType.SPEED, 1); apply(p, PotionEffectType.REGENERATION, 0); } }
            steady(p, c.full(Level.WAREHOUSE));
            if (c.has("flickering_bulb") && hp < 0.4) apply(p, PotionEffectType.REGENERATION, 0);
            if (c.has("forklift_key")) apply(p, PotionEffectType.SPEED, 0);
            if ("packing_tape".equals(c.offHand) && hp < 0.5) apply(p, PotionEffectType.DAMAGE_RESISTANCE, 0);
            if (c.has("rubber_duck")) { apply(p, PotionEffectType.WATER_BREATHING, 0); if (wet) apply(p, PotionEffectType.REGENERATION, 0); }
            if (c.has("exit_sign")) { apply(p, PotionEffectType.SPEED, 0); apply(p, PotionEffectType.DAMAGE_RESISTANCE, 0); if (hp < 0.4) apply(p, PotionEffectType.REGENERATION, 0); }
        }
    }

    private Carry scan(Player p) {
        Carry c = new Carry();
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) { String id = Items.id(s); if (id != null) c.ids.add(id); }
        for (ItemStack s : inv.getArmorContents()) {
            Items.Def d = Items.def(s);
            if (d != null && d.armour()) c.worn[d.level.index()]++;
        }
        c.offHand = Items.id(inv.getItemInOffHand());
        return c;
    }

    /** A short, particle-free effect refreshed every second (never over a stronger or longer potion the player drank). */
    private static void apply(Player p, PotionEffectType type, int amp) {
        for (PotionEffect e : p.getActivePotionEffects())
            if (e.getType().equals(type) && (e.getAmplifier() > amp || e.getAmplifier() == amp && e.getDuration() > 100)) return;
        p.addPotionEffect(new PotionEffect(type, 50, amp, true, false), true);
    }

    private static void steady(Player p, boolean on) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (a == null) return;
        AttributeModifier mine = null;
        for (AttributeModifier m : a.getModifiers()) if (m.getUniqueId().equals(STEADY)) mine = m;
        if (on && mine == null) a.addModifier(new AttributeModifier(STEADY, "backrooms_steady", 1.0, AttributeModifier.Operation.ADD_NUMBER));
        else if (!on && mine != null) a.removeModifier(mine);
    }

    // ---- right-click trinkets -------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Items.Def d = Items.def(e.getItem());
        if (d == null || d.kind != Items.Kind.TRINKET) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && GuideKit.interactive(e.getClickedBlock())) return;   // chests and doors open as usual
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;   // a refused click comes again with the off hand: answer once
        Player p = e.getPlayer();
        switch (d.id) {
            case "canteen":
                if (!ready(p, d.id, 90)) return;
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                break;
            case "coolant_vial":
                if (!ready(p, d.id, 90)) return;
                plugin.hazards().cool(p);
                p.setFireTicks(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0, true, false));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.2f);
                break;
            case "capacitor": {
                if (!ready(p, d.id, 30)) return;
                int hit = 0;
                for (Entity x : p.getNearbyEntities(10, 6, 10)) {
                    if (hit >= 3 || !hostile(x)) continue;
                    line(p.getEyeLocation(), ((LivingEntity) x).getEyeLocation());
                    ((LivingEntity) x).damage(8, p);
                    hit++;
                }
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_IMPACT, 0.8f, 1.6f);
                break;
            }
            case "cold_coffee":
                if (!ready(p, d.id, 120)) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 600, 1, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, true, false));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
                break;
            case "subway_token": {
                if (!plugin.isBackrooms(p.getWorld())) { p.sendMessage(ChatColor.GRAY + "The token only works in the Backrooms."); return; }
                Level lv = Level.at(p.getLocation().getX(), p.getLocation().getZ());
                if (lv == null || !ready(p, d.id, 300)) return;
                plugin.travel().noclip(p, plugin.travel().landing(p.getWorld(), lv), lv.label());
                break;
            }
            case "whistle":
                if (!ready(p, d.id, 45)) return;
                for (Entity x : p.getNearbyEntities(12, 6, 12)) if (hostile(x)) {
                    ((LivingEntity) x).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 120, 2));
                    ((LivingEntity) x).addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_FLUTE, 1.5f, 2f);
                break;
            default:
                return;
        }
        abilities++;
    }

    private boolean ready(Player p, String id, int seconds) {
        Map<String, Long> mine = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis(), until = mine.getOrDefault(id, 0L);
        if (until > now) {
            p.sendMessage(ChatColor.GRAY + Items.DEFS.get(id).name + " is recharging: " + ((until - now) / 1000 + 1) + " s.");
            return false;
        }
        mine.put(id, now + seconds * 1000L);
        return true;
    }

    static boolean hostile(Entity x) {
        return x instanceof LivingEntity && !(x instanceof Player) && !x.isDead() && (x instanceof Monster || Mobs.isOurs(x)) && !x.getScoreboardTags().contains("jr_guide");
    }

    private static void line(Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.multiply(1 / len);
        for (double t = 0; t < len; t += 0.4) a.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, a.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
    }

    // ---- weapons, armour and the watch ----------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity src = e.getDamager();
        if (src instanceof Player) {
            Player p = (Player) src;
            struck.put(p.getUniqueId(), System.currentTimeMillis());
            String weapon = Items.id(p.getInventory().getItemInMainHand());
            if ("tidebreaker".equals(weapon) && e.getEntity().getLocation().getBlock().isLiquid()) e.setDamage(e.getDamage() * 2);
            if ("arc_baton".equals(weapon) && e.getEntity() instanceof LivingEntity) {
                for (Entity x : e.getEntity().getNearbyEntities(5, 3, 5)) {
                    if (x == e.getEntity() || !hostile(x)) continue;
                    line(((LivingEntity) e.getEntity()).getEyeLocation(), ((LivingEntity) x).getEyeLocation());
                    Bukkit.getScheduler().runTask(plugin, () -> { if (x.isValid()) ((LivingEntity) x).damage(4, p); });
                    arcs++;
                    break;
                }
            }
        }
        if (e.getEntity() instanceof Player && src instanceof Projectile && revealsMimics((Player) e.getEntity())) e.setDamage(e.getDamage() * 0.5);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.LIGHTNING || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            double k = shockFactor(p);
            if (k < 1) e.setDamage(e.getDamage() * Math.max(k, 0.3));
        }
    }

    /** The Dead Man's Watch: once every five minutes a killing blow leaves you standing. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (e.getFinalDamage() < p.getHealth() || !of(p).has("dead_mans_watch")) return;
        long now = System.currentTimeMillis();
        if (now - savedAt.getOrDefault(p.getUniqueId(), 0L) < 300_000L) return;
        savedAt.put(p.getUniqueId(), now);
        e.setCancelled(true);
        p.setHealth(Math.min(p.getMaxHealth(), 8));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, true, false));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        p.sendMessage(ChatColor.GOLD + "The Dead Man's Watch stops. " + ChatColor.GRAY + "Not yet. (Again in 5 minutes.)");
        saves++;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || e.getCaught() == null) return;
        if (!"pool_skimmer".equals(Items.id(e.getPlayer().getInventory().getItemInMainHand()))) return;
        Vector pull = e.getPlayer().getLocation().toVector().subtract(e.getCaught().getLocation().toVector()).multiply(0.22);
        e.getCaught().setVelocity(pull.setY(Math.min(1.0, pull.getY() + 0.4)));
    }

    // ---- never their vanilla selves -------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (Items.id(e.getItemInHand()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseOn(PlayerInteractEntityEvent e) {
        ItemStack s = e.getHand() == EquipmentSlot.OFF_HAND ? e.getPlayer().getInventory().getItemInOffHand() : e.getPlayer().getInventory().getItemInMainHand();
        Items.Def d = Items.def(s);
        if (d != null && d.kind == Items.Kind.TRINKET) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) if (Items.id(s) != null) { e.getInventory().setResult(null); return; }
    }

    /** The Backrooms Guide also hands out Almond Water: two bottles every ten minutes. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuide(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getRightClicked().getScoreboardTags().contains("jr_guide_backrooms")) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Map<String, Long> mine = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        if (mine.getOrDefault("guide_water", 0L) > now) return;
        mine.put("guide_water", now + 600_000L);
        for (int i = 0; i < 2; i++) BackroomsPlugin.give(p, Items.make("almond_water"));
        p.sendMessage(ChatColor.YELLOW + "[Backrooms Guide] " + ChatColor.WHITE + "And some Almond Water. Drink it when the walls start to lean.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); carry.remove(id); struck.remove(id); }

    String describe() { return "abilities=" + abilities + " watchSaves=" + saves + " arcs=" + arcs; }
}
