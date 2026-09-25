package chat.jaspr.biomes;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Fewer wild animals, so the spawn budget goes to the dead (3.21.0, halved again in 3.27.1).
 *
 * VanillaFauna restores vanilla's world-generation animal pass and runs it twice, which
 * left the land crowded with cows, pigs and chickens. The owner asked for "other animals
 * don't need to spawn as much. Reserve those spawns for zombies". 3.21.0 cancelled 65% of the
 * passive animals that appear on their own -- NATURAL spawns and the CHUNK_GEN pass --
 * except sheep, the only source of wool.
 *
 * 3.27.1 (owner: "halve the spawn rate of all passive mobs"): every passive mob now keeps half
 * of what it kept before -- other animals 35% -> 17.5%, sheep 100% -> 50%, and squid and bats
 * (water and ambient mobs, previously untouched) 100% -> 50%.
 *
 * Breeding, eggs, spawn eggs, spawners and plugin spawns carry other reasons and are
 * never thinned.
 */
public final class SpawnBalance implements Listener {
    /** Share of wild non-sheep animal spawns kept before 3.27.1. */
    private static final double ANIMALS_KEPT = 0.35;
    /** 3.27.1: every passive mob keeps half of its previous share. */
    private static final double HALVED = 0.5;

    private final HorrorPlugin plugin;
    private final Random random = new Random();
    private long kept, thinned;

    SpawnBalance(HorrorPlugin plugin) { this.plugin = plugin; }

    /** Hosted here so the live jar could be patched without touching HorrorPlugin (its source has undeployed drift). */
    private LootBooks books;

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        books = new LootBooks(plugin);
        books.start();
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (books != null) books.stop();
    }

    public String status() { return "passiveKept=" + kept + " passiveThinned=" + thinned; }

    private static boolean wild(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
    }

    /** Share of this mob's wild spawns that is kept; 1 for anything that is not a passive mob. */
    static double keptShare(LivingEntity entity) {
        if (entity instanceof Sheep) return HALVED;
        if (entity instanceof Animals) return ANIMALS_KEPT * HALVED;
        if (entity instanceof WaterMob || entity instanceof Ambient) return HALVED;
        return 1.0;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (!wild(e.getSpawnReason())) return;
        double share = keptShare(e.getEntity());
        if (share >= 1.0) return;
        if (random.nextDouble() < share) { kept++; return; }
        thinned++;
        e.setCancelled(true);
    }
}
