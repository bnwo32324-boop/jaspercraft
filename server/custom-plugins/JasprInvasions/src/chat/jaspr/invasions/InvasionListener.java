package chat.jaspr.invasions;

import java.util.Iterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** The edges of the system: explosions, loot, sleeping, and players leaving mid-siege. */
final class InvasionListener implements Listener {
    private final InvasionPlugin plugin;

    InvasionListener(InvasionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * An invader blowing a hole in a wall is recorded the same way a miner digging through it is,
     * and anything holding items is pulled out of the blast entirely.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();
        if (source == null || !source.getScoreboardTags().contains(InvaderFactory.TAG)) return;

        InvasionConfig settings = plugin.settings();
        if (!settings.breakBlocks) {
            event.blockList().clear();
            return;
        }

        long now = plugin.ticks();
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext(); ) {
            Block block = it.next();
            if (settings.unbreakable.contains(block.getType())
                    || block.getState() instanceof InventoryHolder
                    || block.getState() instanceof CreatureSpawner) {
                it.remove();
                continue;
            }
            plugin.restorer().record(block, now);
        }
    }

    /** Invaders are a threat, not a farm. They arrive with their gear and they leave with it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!mob.getScoreboardTags().contains(InvaderFactory.TAG)) return;
        event.getDrops().clear();
        event.setDroppedExp(Math.min(event.getDroppedExp(), 8));
        // The no-farm rule stands otherwise, but every custom zombie still drops its rotten flesh.
        EntityType type = mob.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.ZOMBIE_VILLAGER)
            event.getDrops().add(new ItemStack(Material.ROTTEN_FLESH, 1));
    }

    /** You do not get to sleep through your own invasion. And settling down is what calls one. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (plugin.isBeingInvaded(player.getUniqueId())) {
            if (!plugin.settings().preventSleep) return;
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot sleep while something is trying to get in.");
            return;
        }
        if (plugin.tryBedSummon(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.noteJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.endInvasionFor(event.getPlayer().getUniqueId(), false);
    }
}
