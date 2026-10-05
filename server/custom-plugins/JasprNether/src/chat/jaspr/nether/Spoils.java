package chat.jaspr.nether;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * What the colossal structures' and the Catacombs' treasure does (the plain numbers are attribute modifiers on the
 * items themselves, see {@link Items}):
 * <ul>
 *   <li>blades: the Khopesh poisons, the Dao of the Fire Nation burns and quickens its wielder, the Bone Reaver weakens,
 *       the Crook of the Sunless Pharaoh blinds and slows; the Hollow Crown (worn) drinks a fifth of every blow's harm;</li>
 *   <li>the Sovereign's Flame lashes a whip of fire along where its bearer looks (right-click, every two seconds; no
 *       block is harmed);</li>
 *   <li>armour: a full Pharaoh's set keeps hunger and blindness away, a full Ember Guard set keeps fire away, a full
 *       Deepwarden set (and the Hollow Crown) keeps the wither away (Deepwarden poison too);</li>
 *   <li>trinkets: the Scarab Amulet (off hand) keeps poison away; the Phoenix Feather (carried) raises its bearer in
 *       flame when near death, once in five minutes;</li>
 *   <li>the Hollow Reliquary (a Catacombs puzzle's prize) opens into Catacomb treasure;</li>
 *   <li>the relics of the ten colossi of 2026-10-04: the Gatekeeper's Cleaver burns and drags down, the Wyrmbone Blade
 *       withers and hurls, the Gladius of the Undying mends its wielder, the Titan's Chain drags foes in and slows them,
 *       the Rime Scepter freezes, the Serpent's Fang poisons; the Burning Crown (worn) keeps fire away and sets foes
 *       alight; the Archon's Wand hurls three fireballs (right-click, every two seconds; no block is harmed); the Heart of
 *       the Sporefather (off hand) keeps poison and the wither away; the Oracle's Prism (carried) keeps blindness,
 *       nausea and levitation away.</li>
 * </ul>
 * Effects are refreshed each second and are never night vision, full-bright or glowing (owner rule).
 */
final class Spoils implements Listener {
    private final NetherPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> flameUsed = new HashMap<>(), feather = new HashMap<>(), wandUsed = new HashMap<>();
    long whips, drinks, risen, opened, kept;

    Spoils(NetherPlugin plugin) { this.plugin = plugin; }

    String describe() { return "whips=" + whips + " drinks=" + drinks + " phoenix=" + risen + " reliquaries=" + opened + " kept=" + kept; }

    private boolean fullSet(Player p, String set) { return plugin.mechanics.fullSet(p, set); }

    /** Each second: the sets' and trinkets' wards. */
    void tick() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            boolean crown = Items.is(p.getInventory().getHelmet(), "hollow_crown");
            if (fullSet(p, "pharaoh")) { clear(p, PotionEffectType.HUNGER); clear(p, PotionEffectType.BLINDNESS); }
            if (fullSet(p, "deepwarden")) { clear(p, PotionEffectType.WITHER); clear(p, PotionEffectType.POISON); }
            else if (crown) clear(p, PotionEffectType.WITHER);
            if (Items.is(p.getInventory().getItemInOffHand(), "scarab_amulet")) clear(p, PotionEffectType.POISON);
            if (Items.is(p.getInventory().getItemInOffHand(), "spore_heart")) { clear(p, PotionEffectType.POISON); clear(p, PotionEffectType.WITHER); }
            if (Ordeals.carries(p, "oracle_prism")) { clear(p, PotionEffectType.BLINDNESS); clear(p, PotionEffectType.CONFUSION); clear(p, PotionEffectType.LEVITATION); }
            if (Items.is(p.getInventory().getHelmet(), "burning_crown")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false), true);
            if (fullSet(p, "ember_guard")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false), true);
        }
    }

    private void clear(Player p, PotionEffectType t) { if (p.hasPotionEffect(t)) { p.removePotionEffect(t); kept++; } }

    // ---- blades -----------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof LivingEntity)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Player p = (Player) e.getDamager();
        LivingEntity v = (LivingEntity) e.getEntity();
        String id = Items.id(p.getInventory().getItemInMainHand());
        if (id != null) switch (id) {
            case "khopesh": v.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true), true); break;
            case "fire_nation_dao":
                v.setFireTicks(Math.max(v.getFireTicks(), 80));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false), true);
                break;
            case "bone_reaver": v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true), true); break;
            case "pharaoh_crook":
                v.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, true), true);
                break;
            case "gatekeeper_cleaver":
                v.setFireTicks(Math.max(v.getFireTicks(), 80));
                v.setVelocity(v.getVelocity().setY(-0.6));
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, true), true);
                break;
            case "wyrmbone_blade":
                v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true), true);
                v.setVelocity(v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.7).setY(0.4));
                break;
            case "gladiator_gladius": if (e.getFinalDamage() > 0) p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 1.5)); break;
            case "titan_chain":
                v.setVelocity(p.getLocation().toVector().subtract(v.getLocation().toVector()).setY(0).normalize().multiply(0.6).setY(0.2));
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 50, 1, false, true), true);
                break;
            case "rime_scepter": v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, false, true), true); break;
            case "serpent_fang": v.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, false, true), true); break;
            default:
        }
        if (Items.is(p.getInventory().getHelmet(), "burning_crown")) v.setFireTicks(Math.max(v.getFireTicks(), 60));
        if (Items.is(p.getInventory().getHelmet(), "hollow_crown") && e.getFinalDamage() > 0) {
            double max = p.getMaxHealth();
            p.setHealth(Math.min(max, p.getHealth() + e.getFinalDamage() * 0.2));
            drinks++;
        }
    }

    // ---- the Sovereign's Flame and the Reliquary --------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack hand = e.getItem();
        if (Items.is(hand, "sovereign_flame")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            long ms = System.currentTimeMillis();
            Long last = flameUsed.get(p.getUniqueId());
            if (last != null && ms - last < 2000) return;
            flameUsed.put(p.getUniqueId(), ms);
            lash(p);
            return;
        }
        if (Items.is(hand, "archon_wand")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            long ms = System.currentTimeMillis();
            Long last = wandUsed.get(p.getUniqueId());
            if (last != null && ms - last < 2000) return;
            wandUsed.put(p.getUniqueId(), ms);
            Vector dir = p.getLocation().getDirection().normalize();
            for (int i = -1; i <= 1; i++) {
                Vector d = Mobs.rotY(dir.clone(), i * 0.12);
                SmallFireball b = p.launchProjectile(SmallFireball.class, d.multiply(1.5));
                b.setIsIncendiary(false);
                b.setMetadata("jn_staff", new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE));
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.0f);
            whips++;
            return;
        }
        if (Items.is(hand, "hollow_reliquary")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
            List<ItemStack> inside = Loot.roll("jaspr:depths/rich", random);
            for (ItemStack s : inside) for (ItemStack left : p.getInventory().addItem(s).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.6f);
            p.getWorld().spawnParticle(Particle.SPELL_WITCH, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.4, 0.4, 0.1);
            p.sendMessage(ChatColor.GOLD + "The Hollow Reliquary opens: " + ChatColor.YELLOW + inside.size() + " things lie inside.");
            opened++;
            plugin.getLogger().info("NETHER_RELIQUARY_OPENED player=" + p.getUniqueId() + " items=" + inside.size());
        }
    }

    /** A whip of fire ten blocks along the bearer's look, stopped by walls: whatever lives on it burns. No block is harmed. */
    private void lash(Player p) {
        Location from = p.getEyeLocation();
        Vector dir = from.getDirection().normalize();
        World w = p.getWorld();
        double reach = 10;
        for (double s = 0.8; s <= 10; s += 0.5) {
            Location at = from.clone().add(dir.clone().multiply(s));
            if (at.getBlock().getType().isOccluding()) { reach = s; break; }
            w.spawnParticle(Particle.FLAME, at, 2, 0.05, 0.05, 0.05, 0.01);
        }
        for (Entity n : p.getNearbyEntities(10, 6, 10)) {
            if (!(n instanceof LivingEntity) || n instanceof Player || n.isDead()) continue;
            Vector to = ((LivingEntity) n).getEyeLocation().toVector().subtract(from.toVector());
            double along = to.dot(dir);
            if (along < 0 || along > reach || to.clone().subtract(dir.clone().multiply(along)).length() > 1.5) continue;
            ((LivingEntity) n).damage(8, p);
            n.setFireTicks(Math.max(n.getFireTicks(), 100));
        }
        w.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        whips++;
    }

    // ---- the Ember Guard and the Phoenix Feather ------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        switch (e.getCause()) {
            case FIRE: case FIRE_TICK: case LAVA: case HOT_FLOOR:
                if (fullSet(p, "ember_guard") || Items.is(p.getInventory().getHelmet(), "burning_crown")) { e.setCancelled(true); p.setFireTicks(0); kept++; return; }
                break;
            case WITHER:
                if (fullSet(p, "deepwarden") || Items.is(p.getInventory().getHelmet(), "hollow_crown") || Items.is(p.getInventory().getItemInOffHand(), "spore_heart")) { e.setCancelled(true); kept++; return; }
                break;
            case POISON:
                if (fullSet(p, "deepwarden") || Items.is(p.getInventory().getItemInOffHand(), "scarab_amulet") || Items.is(p.getInventory().getItemInOffHand(), "spore_heart")) { e.setCancelled(true); kept++; return; }
                break;
            default:
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (e.getFinalDamage() < p.getHealth() || !Ordeals.carries(p, "phoenix_feather")) return;
        Long last = feather.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 5 * 60 * 1000L) return;
        feather.put(p.getUniqueId(), now);
        e.setCancelled(true);
        p.setHealth(Math.min(p.getMaxHealth(), 10));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, true), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true), true);
        for (Entity n : p.getNearbyEntities(5, 3, 5)) {
            if (!(n instanceof LivingEntity) || n instanceof Player) continue;
            Vector v = n.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 1e-4) v = new Vector(1, 0, 0);
            n.setVelocity(v.normalize().multiply(1.2).setY(0.5));
            n.setFireTicks(Math.max(n.getFireTicks(), 100));
        }
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 80, 1.2, 1, 1.2, 0.08);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.5f, 1.4f);
        p.sendTitle(ChatColor.GOLD + "You rise in flame", ChatColor.YELLOW + "the Phoenix Feather burns for you", 5, 40, 10);
        risen++;
        plugin.getLogger().info("NETHER_PHOENIX_ROSE player=" + p.getUniqueId());
    }
}
