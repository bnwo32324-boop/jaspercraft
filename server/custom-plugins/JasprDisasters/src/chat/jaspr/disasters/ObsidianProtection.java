package chat.jaspr.disasters;

import java.util.Iterator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Mandatory world-wide obsidian protection, even with automatic disasters disabled.
 * No world scans, repair-after-damage task, or player mining restrictions.
 * Direct plugin terrain edits must also exclude obsidian at their mutation site.
 */
final class ObsidianProtection implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void mobChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && event.getBlock().getType() == Material.OBSIDIAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityExplosion(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockExplosion(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void burn(BlockBurnEvent event) {
        if (event.getBlock().getType() == Material.OBSIDIAN) event.setCancelled(true);
    }

    private static void protect(List<Block> affected) {
        // Remove only obsidian; nearby terrain, entity damage and explosion yield
        // remain unchanged. Do not un-cancel another plugin's protection event.
        for (Iterator<Block> it = affected.iterator(); it.hasNext();) {
            if (it.next().getType() == Material.OBSIDIAN) it.remove();
        }
    }
}
