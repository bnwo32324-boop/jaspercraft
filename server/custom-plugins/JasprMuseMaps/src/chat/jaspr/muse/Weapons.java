package chat.jaspr.muse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Husk;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Perks of the sixteen Muse+GLM_Maps weapons. The items themselves are JasprApocalypse expedition melee weapons
 * (ExpeditionEquipment: stable model band, base damage and recovery, lore, Creative catalogue, JasprRPG armament);
 * this listener adds what makes each one unique, after Apocalypse has scaled the hit (HIGH -> here HIGHEST).
 * Ten are the signature drops of the ten Muse+GLM_Maps bosses; six are craftable at a bench.
 */
public final class Weapons implements Listener {
    /** boss key -> weapon id */
    static final Map<String, String> BOSS_WEAPON = new HashMap<>();
    static {
        BOSS_WEAPON.put("sovereign", "nightfall_scythe"); BOSS_WEAPON.put("stepmother", "glass_rapier");
        BOSS_WEAPON.put("pyrarch", "magmaforged_greataxe"); BOSS_WEAPON.put("skysage", "skybreaker_blade");
        BOSS_WEAPON.put("magistrate", "verdict_of_rime"); BOSS_WEAPON.put("gladius", "champions_gladius");
        BOSS_WEAPON.put("house", "high_roller_sabre"); BOSS_WEAPON.put("bishop", "censer_mace");
        BOSS_WEAPON.put("admiral", "brinehook_cutlass"); BOSS_WEAPON.put("matriarch", "silkfang_dagger");
    }
    static final Set<String> CRAFTABLE = new HashSet<>(Arrays.asList("tidecaller_spear", "starmetal_rapier",
        "obsidian_warhammer", "hunters_kukri", "voidsteel_katana", "bone_reaver"));

    static boolean isBossWeapon(String id) { return BOSS_WEAPON.containsValue(id); }

    private final MusePlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, int[]> streak = new HashMap<>();     // gladius: hits, rapier: hits
    private final Map<UUID, Long> streakAt = new HashMap<>();
    private Method idMethod;
    private ClassLoader loader;

    Weapons(MusePlugin plugin) { this.plugin = plugin; }

    String id(ItemStack item) {
        if (item == null) return "";
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            if (p == null || !p.isEnabled()) return "";
            if (idMethod == null || loader != p.getClass().getClassLoader()) {
                loader = p.getClass().getClassLoader();
                idMethod = Class.forName("chat.jaspr.apocalypse.ApocalypseItems", true, loader).getMethod("id", ItemStack.class);
            }
            return (String) idMethod.invoke(null, item);
        } catch (ReflectiveOperationException | RuntimeException e) { return ""; }
    }

    private static boolean undead(Entity e) {
        return e instanceof Zombie || e instanceof Skeleton || e instanceof Wither || e instanceof WitherSkeleton
            || e instanceof Stray || e instanceof Husk || e instanceof PigZombie;
    }
    private static boolean arthropod(Entity e) { return e instanceof Spider || e instanceof Silverfish || e instanceof Endermite; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player)
                || !(event.getEntity() instanceof LivingEntity)) return;
        Player p = (Player) event.getDamager();
        String id = id(p.getInventory().getItemInMainHand());
        if (id.isEmpty() || !(isBossWeapon(id) || CRAFTABLE.contains(id))) return;
        LivingEntity t = (LivingEntity) event.getEntity();
        double factor = 1;
        long now = System.currentTimeMillis();
        boolean night = !isDay(p);
        switch (id) {
            case "nightfall_scythe":
                if (night) factor = 1.25;
                heal(p, event.getDamage() * factor * 0.2);
                p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, t.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0);
                break;
            case "glass_rapier": {
                if (p.getFallDistance() > 0 && !p.isOnGround()) factor = 1.5;
                int n = bump(p, now, 4000);
                if (n % 5 == 0) { factor += 8 / Math.max(1, event.getDamage()); p.getWorld().playSound(t.getLocation(), Sound.BLOCK_NOTE_BELL, 1, 2f); }
                break;
            }
            case "magmaforged_greataxe":
                if (t.getFireTicks() > 0) factor = 1.35;
                t.setFireTicks(Math.max(t.getFireTicks(), 100));
                break;
            case "skybreaker_blade":
                if (!t.isOnGround()) factor = 1.3;
                t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 0));
                break;
            case "verdict_of_rime":
                if (t.hasPotionEffect(PotionEffectType.SLOW)) factor = 1.4;
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                p.getWorld().spawnParticle(Particle.SNOW_SHOVEL, t.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
                break;
            case "champions_gladius": {
                int n = Math.min(5, bump(p, now, 3000));
                factor = 1 + 0.08 * (n - 1);
                break;
            }
            case "high_roller_sabre": {
                int roll = random.nextInt(100);
                if (roll < 5) { factor = 3; p.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2f); }
                else if (roll < 25) { factor = 2; p.getWorld().playSound(t.getLocation(), Sound.BLOCK_NOTE_PLING, 1, 2f); }
                break;
            }
            case "censer_mace":
                if (undead(t)) factor = 1.5;
                heal(p, 1);
                break;
            case "brinehook_cutlass":
                if (p.getLocation().getBlock().isLiquid() || p.getWorld().hasStorm()) factor = 1.3;
                Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 1) plugin.later(1, () -> t.setVelocity(pull.normalize().multiply(0.35).setY(0.1)));
                break;
            case "silkfang_dagger":
                if (arthropod(t)) factor = 1.4;
                if (p.isSneaking()) factor *= 1.4;
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                break;
            case "tidecaller_spear":
                if (p.getLocation().getBlock().isLiquid() || p.getWorld().hasStorm()) factor = 1.3;
                break;
            case "starmetal_rapier":
                if (p.getFallDistance() > 0 && !p.isOnGround()) factor = 1.4;
                break;
            case "obsidian_warhammer":
                // Splash is sourceless (no second player attack for Apocalypse's recovery gate or this listener to see)
                // and never reaches players or tamed animals.
                for (Entity e : t.getNearbyEntities(2.5, 1.5, 2.5))
                    if (e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof org.bukkit.entity.Tameable && ((org.bukkit.entity.Tameable) e).isTamed()))
                        ((LivingEntity) e).damage(event.getDamage() * 0.3);
                p.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, t.getLocation(), 6, 1, 0.2, 1, 0.02);
                break;
            case "hunters_kukri":
                if (t instanceof Animals || arthropod(t)) factor = 1.35;
                break;
            case "voidsteel_katana":
                if (t instanceof Enderman || t instanceof Shulker || t instanceof Endermite) factor = 1.3;
                if (random.nextInt(10) == 0) {
                    Location behind = t.getLocation().clone().add(t.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
                    if (Abilities.floor(behind) != null) { Location to = Abilities.floor(behind).add(0.5, 0, 0.5); to.setYaw(t.getLocation().getYaw()); p.teleport(to); }
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 0.8f, 1.4f);
                }
                break;
            case "bone_reaver":
                t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                break;
            default: break;
        }
        if (factor != 1) event.setDamage(event.getDamage() * factor);
    }

    private int bump(Player p, long now, long window) {
        Long last = streakAt.get(p.getUniqueId());
        int[] n = streak.computeIfAbsent(p.getUniqueId(), k -> new int[1]);
        if (last == null || now - last > window) n[0] = 0;
        n[0]++;
        streakAt.put(p.getUniqueId(), now);
        return n[0];
    }

    private static void heal(Player p, double amount) {
        if (p.isDead() || amount <= 0) return;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
    }

    private static boolean isDay(Player p) {
        long t = p.getWorld().getTime();
        return t < 12300 || t > 23850;
    }
}
