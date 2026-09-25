package chat.jaspr.blight;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The water is poisoned. Touching it poisons you; riding over it does not.
 *
 * The boat exemption is a vehicle test rather than a geometry test on purpose. A
 * seated player's own position sits inside the water block the boat floats in, so
 * anything that asked "are your feet wet" would poison every passenger on every
 * crossing. Asking what you are riding instead is exact, and it keeps rafts,
 * minecarts on bridges and any other mount equally dry.
 */
public final class BlightPlugin extends JavaPlugin {
    /** Player metadata key that exempts its holder from the blight (see JasprGear's Blight Filter). */
    static final String IMMUNE = "jaspr_blight_immune";

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { sweep(); }
        }, 20L, interval());
        getLogger().info("BLIGHT_READY poison=" + amplifier() + "+" + duration() + "t interval=" + interval()
            + "t boatsSafe=" + boatsSafe() + " mobs=" + affectMobs());
    }

    boolean enabled() { return getConfig().getBoolean("blight.enabled", true); }
    int duration() { return Math.max(20, getConfig().getInt("blight.poison-ticks", 100)); }
    int amplifier() { return Math.max(0, getConfig().getInt("blight.poison-level", 1) - 1); }
    long interval() { return Math.max(4, getConfig().getLong("blight.check-ticks", 10)); }
    boolean boatsSafe() { return getConfig().getBoolean("blight.boats-are-safe", true); }
    boolean affectMobs() { return getConfig().getBoolean("blight.also-poison-mobs", false); }

    private void sweep() {
        if (!enabled()) return;
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
                // Set by JasprGear while its Blight Filter trinket is worn (no compile-time dependency).
                if (player.hasMetadata(IMMUNE)) continue;
                if (sheltered(player)) continue;
                if (!touchingWater(player)) continue;
                poison(player);
            }
            if (!affectMobs()) continue;
            for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) continue;
                if (sheltered(entity)) continue;
                if (!touchingWater(entity)) continue;
                poison(entity);
            }
        }
    }

    /** Anything being carried is out of the water as far as this is concerned. */
    private boolean sheltered(Entity entity) {
        if (!boatsSafe()) return false;
        Entity vehicle = entity.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof Boat) return true;
            vehicle = vehicle.getVehicle();
        }
        return false;
    }

    /** Feet or head in water. Either counts as contact. */
    private boolean touchingWater(Entity entity) {
        Location at = entity.getLocation();
        if (water(at.getBlock())) return true;
        Location head = at.clone();
        head.setY(head.getY() + (entity instanceof Player ? 1.6 : 1.0));
        return water(head.getBlock());
    }

    private static boolean water(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.STATIONARY_WATER;
    }

    /** Refreshed rather than stacked, so a long swim does not compound into a death sentence. */
    private void poison(org.bukkit.entity.LivingEntity victim) {
        try {
            PotionEffect current = victim.getPotionEffect(PotionEffectType.POISON);
            if (current != null && current.getAmplifier() > amplifier()) return;
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration(), amplifier(), false, true), true);
        } catch (RuntimeException ignored) { }
    }
}
