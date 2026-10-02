package chat.jaspr.gear;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The 4.0.0 trinkets' mechanics (the standing bonuses - health, armour, luck, knockback resistance, speed - are
 * attribute modifiers in {@link GearAbilities}, the Exit Sign's noclip is its [H] key there). Damage bonuses and
 * reductions are multipliers on the event, so they stack with stats and other gear. No night vision, glowing or light.
 */
final class GearExtras implements Listener {
    static final String NETHER = "world_nether", RUINS = "jaspr_ruins", ATLAS = "jaspr_atlas", BACKROOMS = "jaspr_levels";
    static final long NOCLIP_MS = 15_000, MEDIC_MS = 10_000, ALMOND_MS = 4_000, VIGIL_MS = 4_000, HUNTER_MS = 10_000;
    static final double REALM_BONUS = 1.30, REALM_SOFTEN = 0.80;

    private final GearPlugin plugin;
    private final Random random = new Random();
    long noclips, refunds, sharpshots, hunterHits, realmHits, ignites, staggers, softened, alliesHealed, almondHeals, vigilChimes, luckyDrops;

    GearExtras(GearPlugin plugin) { this.plugin = plugin; }

    String metrics() {
        return "noclips=" + noclips + " arrowRefunds=" + refunds + " sharpshots=" + sharpshots + " hunterHits=" + hunterHits
            + " realmHits=" + realmHits + " ignites=" + ignites + " staggers=" + staggers + " softened=" + softened
            + " alliesHealed=" + alliesHealed + " almondHeals=" + almondHeals + " vigilChimes=" + vigilChimes + " luckyDrops=" + luckyDrops;
    }

    // ---- who is who --------------------------------------------------------------------------------------------------

    static boolean tagged(Entity e, String tag) { return e != null && e.getScoreboardTags().contains(tag); }

    static boolean tagPrefix(Entity e, String prefix) {
        if (e == null) return false;
        for (String t : e.getScoreboardTags()) if (t.startsWith(prefix)) return true;
        return false;
    }

    /** Hostile here or in any realm: vanilla monsters plus every realm's own creatures (never townsfolk). */
    static boolean hostileAnywhere(Entity e) {
        if (tagged(e, "jn_k_pigtificate") || tagged(e, "br_kind:CITIZEN") || (tagged(e, "atlas_npc") && !tagged(e, "atlas_dominion") && !tagPrefix(e, "atlas_boss:"))) return false;
        return GearAbilities.hostile(e) || tagged(e, "jaspr_horror") || tagged(e, "br_mob") || tagged(e, "jn_mob") || tagged(e, "atlas_dominion")
            || tagged(e, "jaspr_invader") || tagged(e, "jaspr_boss");
    }

    static boolean netherCreature(Entity e) {
        if (e == null || e instanceof Player) return false;
        if (tagged(e, "jn_mob") || tagged(e, "jn_lord")) return !tagged(e, "jn_k_pigtificate");
        EntityType t = e.getType();
        return t == EntityType.BLAZE || t == EntityType.GHAST || t == EntityType.MAGMA_CUBE || t == EntityType.PIG_ZOMBIE || t == EntityType.WITHER_SKELETON
            || (NETHER.equals(e.getWorld().getName()) && GearAbilities.hostile(e));
    }

    static boolean horror(Entity e) { return tagged(e, "jaspr_horror") || (tagged(e, "jaspr_boss") && RUINS.equals(e.getWorld().getName())); }

    static boolean dominion(Entity e) { return tagged(e, "atlas_dominion") || tagPrefix(e, "atlas_boss:"); }

    static boolean backroomsEntity(Entity e) { return tagged(e, "br_mob") || tagged(e, "br_boss"); }

    private Set<GearItem> worn(Player p) {
        GearProfile prof = plugin.profile(p);
        return prof == null || !GearAbilities.active(p) ? Collections.<GearItem>emptySet() : prof.worn();
    }

    /** The living thing behind a hit: the attacker itself, or whoever loosed the projectile. */
    private static Entity source(Entity damager) {
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) return (Entity) ((Projectile) damager).getShooter();
        return damager;
    }

    // ---- once a second and every other tick --------------------------------------------------------------------------

    void second(Player p, GearProfile prof, long now) {
        if (!GearAbilities.active(p)) return;
        Set<GearItem> worn = prof.worn();
        if (worn.isEmpty()) return;
        if (worn.contains(GearItem.MAGMA_HEART) && p.getFireTicks() > 0) p.setFireTicks(0);
        if (worn.contains(GearItem.EYE_OF_THE_DEEP)) p.removePotionEffect(PotionEffectType.CONFUSION);
        if (worn.contains(GearItem.TIDEPEARL) && GearAbilities.headInWater(p)) p.setRemainingAir(p.getMaximumAir());
        double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if (worn.contains(GearItem.ALMOND_WATER) && p.getHealth() < max / 2 && now >= prof.almondAt) {
            prof.almondAt = now + ALMOND_MS;
            p.setHealth(Math.min(max, p.getHealth() + 1.0));
            almondHeals++;
        }
        if (worn.contains(GearItem.MEDICS_ARMBAND) && now >= prof.medicAt) {
            prof.medicAt = now + MEDIC_MS;
            for (Entity e : p.getNearbyEntities(6, 4, 6)) {
                if (!(e instanceof Player) || e == p || ((Player) e).isDead()) continue;
                Player ally = (Player) e;
                double allyMax = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                if (ally.getHealth() >= allyMax) continue;
                ally.setHealth(Math.min(allyMax, ally.getHealth() + 1.0));
                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2.1, 0), 1, 0.2, 0.1, 0.2, 0);
                alliesHealed++;
            }
        }
        if (worn.contains(GearItem.VIGIL_RING) && now >= prof.vigilReady) {
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity e : p.getNearbyEntities(10, 6, 10)) {
                if (e.isDead() || !(e instanceof LivingEntity)) continue;
                if (e.getType() != EntityType.CREEPER && !tagged(e, "jaspr_invader")) continue;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) { best = d; nearest = (LivingEntity) e; }
            }
            if (nearest != null) {
                prof.vigilReady = now + VIGIL_MS;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BELL, 0.8f, 1.6f);
                GearAbilities.bar(p, ChatColor.GOLD + "Vigil: " + ChatColor.WHITE + GearAbilities.name(nearest) + ChatColor.GRAY + " "
                    + Math.round(Math.sqrt(best)) + "m " + GearAbilities.direction(p, nearest.getLocation()));
                vigilChimes++;
            }
        }
    }

    void fast(Player p, GearProfile prof) {
        if (!GearAbilities.active(p) || !prof.worn().contains(GearItem.TIDEPEARL)) return;
        Location loc = p.getLocation();
        if (loc.getBlock().isLiquid() && GearAbilities.headInWater(p) && !p.isOnGround() && prof.moved2 > 0.004) {
            p.setVelocity(loc.getDirection().multiply(0.26));
            p.setRemainingAir(p.getMaximumAir());
        }
    }

    // ---- shooting ----------------------------------------------------------------------------------------------------

    /** Fletcher's Quiver: arrows fly faster and some come back (the refunded arrow cannot also be picked up). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getProjectile() instanceof Arrow)) return;
        Player p = (Player) e.getEntity();
        if (!worn(p).contains(GearItem.FLETCHERS_QUIVER)) return;
        Arrow arrow = (Arrow) e.getProjectile();
        arrow.setVelocity(arrow.getVelocity().multiply(1.2));
        ItemStack bow = e.getBow();
        boolean infinite = bow != null && bow.containsEnchantment(Enchantment.ARROW_INFINITE);
        if (p.getGameMode() == GameMode.CREATIVE || infinite || random.nextDouble() >= 0.30) return;
        arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
        for (ItemStack rest : p.getInventory().addItem(new ItemStack(Material.ARROW)).values()) p.getWorld().dropItem(p.getLocation(), rest);
        refunds++;
    }

    // ---- hitting -----------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity) || e.getEntity().getType() == EntityType.ARMOR_STAND) return;
        Entity src = source(e.getDamager());
        if (!(src instanceof Player)) return;
        Player p = (Player) src;
        Set<GearItem> worn = worn(p);
        if (worn.isEmpty()) return;
        LivingEntity target = (LivingEntity) e.getEntity();
        boolean melee = e.getDamager() == p && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        double distance = p.getLocation().distance(target.getLocation());
        // a direct hit from more than 4.5 blocks away is a gunshot
        boolean ranged = e.getDamager() instanceof Arrow || (e.getDamager() == p && distance > 4.5);
        double m = 1.0;
        if (ranged && worn.contains(GearItem.SHARPSHOOTER_MONOCLE)) { m *= distance > 16 ? 1.30 : 1.10; sharpshots++; }
        long now = System.currentTimeMillis();
        GearProfile prof = plugin.profile(p);
        if (prof != null && worn.contains(GearItem.HUNTERS_NECKLACE) && now < prof.hunterUntil && prof.hunterStacks > 0) {
            m *= 1.0 + 0.12 * prof.hunterStacks;
            hunterHits++;
        }
        if ((worn.contains(GearItem.SOULFIRE_RING) && netherCreature(target)) || (worn.contains(GearItem.EYE_OF_THE_DEEP) && horror(target))
            || (worn.contains(GearItem.DOMINION_SIGNET) && dominion(target))) { m *= REALM_BONUS; realmHits++; }
        if (m != 1.0) e.setDamage(e.getDamage() * m);
        if (melee && worn.contains(GearItem.MAGMA_HEART)) { target.setFireTicks(Math.max(target.getFireTicks(), 60)); ignites++; }
        if (melee && worn.contains(GearItem.TITANS_GIRDLE)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 1, false, false), true);
            staggers++;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Set<GearItem> worn = worn(p);
        if (worn.isEmpty()) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        Entity from = e instanceof EntityDamageByEntityEvent ? source(((EntityDamageByEntityEvent) e).getDamager()) : null;
        double m = 1.0;
        if (worn.contains(GearItem.MAGMA_HEART)) {
            if (cause == EntityDamageEvent.DamageCause.FIRE_TICK) { e.setCancelled(true); p.setFireTicks(0); return; }
            if (cause == EntityDamageEvent.DamageCause.LAVA) m *= 0.25;
        }
        if (worn.contains(GearItem.TIDEPEARL) && cause == EntityDamageEvent.DamageCause.DROWNING) { e.setCancelled(true); p.setRemainingAir(p.getMaximumAir()); return; }
        if (worn.contains(GearItem.TRENCH_COAT) && cause == EntityDamageEvent.DamageCause.PROJECTILE) m *= 0.80;
        if (from != null && from != p && ((worn.contains(GearItem.SOULFIRE_RING) && netherCreature(from)) || (worn.contains(GearItem.TIDEPEARL) && horror(from))
            || (worn.contains(GearItem.ALMOND_WATER) && backroomsEntity(from)))) { m *= REALM_SOFTEN; softened++; }
        if (m != 1.0) e.setDamage(e.getDamage() * m);
    }

    // ---- kills and healing -------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player || !hostileAnywhere(dead)) return;
        Set<GearItem> worn = worn(killer);
        if (worn.isEmpty()) return;
        GearProfile prof = plugin.profile(killer);
        long now = System.currentTimeMillis();
        if (prof != null && worn.contains(GearItem.HUNTERS_NECKLACE)) {
            prof.hunterStacks = now < prof.hunterUntil ? Math.min(3, prof.hunterStacks + 1) : 1;
            prof.hunterUntil = now + HUNTER_MS;
            GearAbilities.bar(killer, ChatColor.GOLD + "Hunter's fury x" + prof.hunterStacks + ChatColor.GRAY + " (+" + (12 * prof.hunterStacks) + "% damage)");
        }
        if (worn.contains(GearItem.LUCKY_COIN) && random.nextDouble() < 0.10) {
            for (ItemStack drop : e.getDrops()) {
                if (!plain(drop)) continue;
                ItemStack extra = drop.clone();
                e.getDrops().add(extra);
                luckyDrops++;
                break;
            }
        }
    }

    /** An ordinary vanilla item (a copy never duplicates anything named, tagged or unique). */
    static boolean plain(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.isUnbreakable();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (worn((Player) e.getEntity()).contains(GearItem.MEDICS_ARMBAND)) e.setAmount(e.getAmount() * 1.3);
    }

    // ---- the Exit Sign's noclip --------------------------------------------------------------------------------------

    /**
     * Where a noclip ends: through a wall that starts within two blocks ahead and is at most three blocks thick, onto
     * floor on the far side (never into liquid, never through bedrock or barriers). Null when there is no such way.
     */
    static Location noclipTarget(Player p) {
        Location at = p.getLocation();
        Vector dir = at.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return null;
        dir.normalize();
        int wall = 0;
        boolean seenWall = false;
        for (int step = 1; step <= 6; step++) {
            Location probe = at.clone().add(dir.clone().multiply(step));
            Block feet = probe.getBlock(), head = feet.getRelative(BlockFace.UP);
            if (hard(feet.getType()) || hard(head.getType())) return null;
            boolean solid = feet.getType().isSolid() || head.getType().isSolid();
            if (solid) { seenWall = true; if (++wall > 3) return null; continue; }
            if (!seenWall) { if (step >= 2) return null; continue; }
            if (feet.isLiquid() || !feet.getRelative(BlockFace.DOWN).getType().isSolid()) return null;
            Location to = feet.getLocation().add(0.5, 0, 0.5);
            to.setYaw(at.getYaw());
            to.setPitch(at.getPitch());
            return to;
        }
        return null;
    }

    private static boolean hard(Material m) { return m == Material.BEDROCK || m == Material.BARRIER || m == Material.ENDER_PORTAL_FRAME; }

}
