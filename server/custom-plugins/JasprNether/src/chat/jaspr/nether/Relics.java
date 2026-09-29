package chat.jaspr.nether;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What the GLM strongholds' weapons, armour, trinkets and the Nether Lords' relics do (the plain numbers -- damage,
 * armour, off-hand attack, health and speed -- are attribute modifiers on the items themselves, see {@link Items}):
 * <ul>
 *   <li>blades: the Infernal Blade and Deathwing's Talon burn, the Soulreaper Scythe and the Bloodfang Dagger heal their
 *       wielder, the Magma Maul bursts in flame around its foe, the Pit Lord's Cleaver burns and slows, the Cursed
 *       Katana withers, the Colossus Maul hurls foes up;</li>
 *   <li>bows: the Ember Bow's arrows burn (Flame), the Wyrmfire Bow's burst in flame where they land (no block harm);</li>
 *   <li>the Dread Staff hurls a fireball (right-click, once a second and a half; its fire harms no block);</li>
 *   <li>armour: a full Hellforged set gives four more hearts, a full Soulweave set and the Ashen Crown keep the wither
 *       away, the Voidstep Boots stop fall damage;</li>
 *   <li>trinkets: the Ember Heart and the Tyrant's Heart (off hand) keep fire away, the Tyrant's Heart also hardens its
 *       bearer; the Wither Ward (carried) keeps the wither away; the Magma Band (carried) stops magma burns and sets
 *       attackers alight; the Ghastly Pendant (carried) mends its bearer when near death, once a minute.</li>
 * </ul>
 * Effects are refreshed each second and are never night vision, full-bright or glowing (owner rule).
 */
final class Relics implements Listener {
    private static final UUID HELLFORGED_HEALTH = UUID.fromString("5b4a2f0e-6c1d-4e8a-9d3b-4a2f0e6c1d4e");
    private final NetherPlugin plugin;
    private final Map<UUID, Long> staffUsed = new HashMap<>(), pendantUsed = new HashMap<>();
    /** What each player carries, refreshed each second (events read this instead of scanning inventories). */
    private final Map<UUID, java.util.Set<String>> carried = new HashMap<>();
    private long now;
    long burns, heals, staffShots, wardsKept, pendantMends, setBonuses;

    Relics(NetherPlugin plugin) { this.plugin = plugin; }

    String describe() {
        return "burns=" + burns + " heals=" + heals + " staff=" + staffShots + " wards=" + wardsKept + " pendant=" + pendantMends + " sets=" + setBonuses;
    }

    private boolean has(Player p, String id) {
        java.util.Set<String> c = carried.get(p.getUniqueId());
        return c != null && c.contains(id);
    }

    private static java.util.Set<String> scan(Player p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (ItemStack s : p.getInventory().getContents()) {
            if (s == null || s.getType() == Material.AIR || !s.hasItemMeta()) continue;
            String id = Items.id(s);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static boolean offhand(Player p, String id) { return Items.is(p.getInventory().getItemInOffHand(), id); }

    private static boolean fullSet(Player p, String set) {
        for (ItemStack s : p.getInventory().getArmorContents()) if (!Items.isArmorOf(Items.id(s), set)) return false;
        return true;
    }

    void tick(long ticks) {
        now = ticks;
        carried.keySet().removeIf(id -> org.bukkit.Bukkit.getPlayer(id) == null);
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            carried.put(p.getUniqueId(), scan(p));
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (offhand(p, "ember_heart") || offhand(p, "tyrant_heart"))
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false), true);
            if (offhand(p, "tyrant_heart")) p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, true, false), true);
            boolean ward = has(p, "wither_ward") || fullSet(p, "soulweave") || Items.is(p.getInventory().getHelmet(), "ashen_crown");
            if (ward && p.hasPotionEffect(PotionEffectType.WITHER)) { p.removePotionEffect(PotionEffectType.WITHER); wardsKept++; }
            hellforged(p, fullSet(p, "hellforged"));
            if (p.getHealth() < p.getMaxHealth() * 0.3 && has(p, "ghastly_pendant")) {
                Long last = pendantUsed.get(p.getUniqueId());
                if (last == null || now - last >= 20 * 60) {
                    pendantUsed.put(p.getUniqueId(), now);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, true), true);
                    p.getWorld().spawnParticle(Particle.SPELL_MOB_AMBIENT, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
                    pendantMends++;
                }
            }
        }
    }

    /** Four more hearts while the whole Hellforged set is worn (one fixed modifier, added and removed). */
    private void hellforged(Player p, boolean on) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a == null) return;
        AttributeModifier cur = null;
        for (AttributeModifier m : a.getModifiers()) if (m.getUniqueId().equals(HELLFORGED_HEALTH)) cur = m;
        if (on && cur == null) { a.addModifier(new AttributeModifier(HELLFORGED_HEALTH, "jasprnether.hellforged", 8, AttributeModifier.Operation.ADD_NUMBER)); setBonuses++; }
        else if (!on && cur != null) { a.removeModifier(cur); if (p.getHealth() > a.getValue()) p.setHealth(a.getValue()); }
    }

    // ---- blades ------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof LivingEntity)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Player p = (Player) e.getDamager();
        LivingEntity v = (LivingEntity) e.getEntity();
        String id = Items.id(p.getInventory().getItemInMainHand());
        if (id == null) return;
        double dealt = e.getFinalDamage();
        switch (id) {
            case "infernal_blade": burn(v, 80); break;
            case "deathwing_talon":
                burn(v, 100);
                v.setVelocity(v.getVelocity().add(v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.6)).setY(0.35));
                break;
            case "soulreaper_scythe": heal(p, dealt * 0.2); break;
            case "bloodfang_dagger": heal(p, dealt * 0.25); break;
            case "magma_maul":
                burn(v, 60);
                for (Entity n : v.getNearbyEntities(3, 2, 3)) {
                    if (n == p || !(n instanceof LivingEntity) || n instanceof Player) continue;
                    ((LivingEntity) n).damage(3, p);
                    burn((LivingEntity) n, 60);
                }
                v.getWorld().spawnParticle(Particle.FLAME, v.getLocation().add(0, 0.5, 0), 30, 1.5, 0.3, 1.5, 0.02);
                break;
            case "pit_lord_cleaver":
                burn(v, 80);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, true), true);
                break;
            case "cursed_katana": v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, true), true); break;
            case "colossus_maul": v.setVelocity(v.getVelocity().setY(0.75)); break;
            default:
        }
    }

    private void burn(LivingEntity v, int ticks) { v.setFireTicks(Math.max(v.getFireTicks(), ticks)); burns++; }

    private void heal(Player p, double amount) {
        if (amount <= 0) return;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
        heals++;
    }

    // ---- bows and the staff -------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getProjectile() instanceof Arrow)) return;
        String id = Items.id(e.getBow());
        if ("wyrmfire_bow".equals(id)) e.getProjectile().setMetadata("jn_wyrmfire", new FixedMetadataValue(plugin, Boolean.TRUE));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent e) {
        Projectile a = e.getEntity();
        if (!a.hasMetadata("jn_wyrmfire") || a.hasMetadata("jn_wyrmfire_done")) return;
        a.setMetadata("jn_wyrmfire_done", new FixedMetadataValue(plugin, Boolean.TRUE));
        Location l = a.getLocation();
        l.getWorld().createExplosion(l.getX(), l.getY(), l.getZ(), 1.2f, false, false);     // no fire, no block harm
        for (Entity n : l.getWorld().getNearbyEntities(l, 2.5, 2, 2.5)) if (n instanceof LivingEntity && n != a.getShooter()) burn((LivingEntity) n, 80);
        a.remove();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStaff(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (!Items.is(e.getItem(), "dread_staff")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long ms = System.currentTimeMillis();
        Long last = staffUsed.get(p.getUniqueId());
        if (last != null && ms - last < 1500) return;
        staffUsed.put(p.getUniqueId(), ms);
        SmallFireball f = p.launchProjectile(SmallFireball.class, p.getLocation().getDirection().multiply(1.5));
        f.setIsIncendiary(false);
        f.setMetadata("jn_staff", new FixedMetadataValue(plugin, Boolean.TRUE));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);
        staffShots++;
    }

    /** Staff fireballs hit harder than a blaze's (5) and never set blocks alight. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStaffHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof SmallFireball && e.getDamager().hasMetadata("jn_staff")) e.setDamage(Math.max(e.getDamage(), 7));
    }

    // ---- armour and trinkets ------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        switch (e.getCause()) {
            case FALL: if (Items.is(p.getInventory().getBoots(), "voidstep_boots")) e.setCancelled(true); break;
            case HOT_FLOOR: if (has(p, "magma_band")) e.setCancelled(true); break;
            case WITHER:
                if (has(p, "wither_ward") || fullSet(p, "soulweave") || Items.is(p.getInventory().getHelmet(), "ashen_crown")) { e.setCancelled(true); wardsKept++; }
                break;
            default:
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStruck(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof LivingEntity)) return;
        if (has((Player) e.getEntity(), "magma_band")) burn((LivingEntity) e.getDamager(), 60);
    }
}
