package chat.jaspr.biomes;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Fewer wild animals, so the spawn budget goes to the dead (3.21.0).
 *
 * VanillaFauna restores vanilla's world-generation animal pass and runs it twice, which
 * left the land crowded with cows, pigs and chickens. The owner asked for "other animals
 * don't need to spawn as much. Reserve those spawns for zombies". This cancels 65% of the
 * passive animals that appear on their own -- NATURAL spawns and the CHUNK_GEN pass --
 * except sheep, which stay untouched because they are the only source of wool.
 *
 * Breeding, eggs, spawn eggs, spawners and plugin spawns carry other reasons and are
 * never thinned.
 */
public final class SpawnBalance implements Listener {
    /** Share of wild non-sheep animal spawns that are cancelled. */
    private static final double THINNED = 0.65;

    private final HorrorPlugin plugin;
    private final Random random = new Random();
    private long kept, thinned;

    SpawnBalance(HorrorPlugin plugin) { this.plugin = plugin; }

    public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    public void stop() { HandlerList.unregisterAll(this); }

    public String status() { return "passiveKept=" + kept + " passiveThinned=" + thinned; }

    private static boolean wild(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (!wild(e.getSpawnReason())) return;
        if (!(e.getEntity() instanceof Animals)) return;
        if (e.getEntity() instanceof Sheep) { kept++; return; }
        if (random.nextDouble() >= THINNED) { kept++; return; }
        thinned++;
        e.setCancelled(true);
    }
}
