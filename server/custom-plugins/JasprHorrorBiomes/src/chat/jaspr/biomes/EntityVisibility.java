package chat.jaspr.biomes;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.EntityTracker;
import net.minecraft.server.v1_12_R1.EntityTrackerEntry;
import net.minecraft.server.v1_12_R1.WorldServer;

/**
 * Keeps mobs drawn on the clients that can be hit by them.
 *
 * The cause this exists for: relighting a chunk ends in World.refreshChunk, and
 * CraftBukkit implements that by firing sixty-odd fake block changes specifically
 * to provoke a full chunk resend. A 1.12 client answers a full chunk packet by
 * throwing its copy of that chunk away and building a new one, and the entities
 * standing in it go with it. The server's tracker is untouched by any of this, so
 * it still believes those players can see the mob and never sends another spawn
 * packet. The mob stays alive, keeps pathing and keeps swinging, and is simply
 * not drawn -- which is how something invisible kills you.
 *
 * The repair is to tell the tracker to forget who is watching each entity in the
 * refreshed chunk. Its own per-tick pass re-adds every player still in range on
 * the very next tick, and that pass is what emits a fresh spawn packet.
 */
public final class EntityVisibility implements Listener {
    private final HorrorPlugin plugin;
    private long chunkRepairs, damageRepairs, sweepRepairs;
    private int sweeps;

    EntityVisibility(HorrorPlugin plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { sweep(); }
        }, 200L, 100L);
    }

    public void stop() { HandlerList.unregisterAll(this); }

    public String metrics() {
        return "chunkRepairs=" + chunkRepairs + " damageRepairs=" + damageRepairs + " sweepRepairs=" + sweepRepairs;
    }

    // -- the root cause ---------------------------------------------------------

    /**
     * Resend a chunk's light without losing the entities standing in it. Skips
     * the resend entirely when nobody is close enough to hold that chunk, which
     * is the common case while terrain streams in ahead of a walking player.
     */
    public void refreshChunk(World world, int chunkX, int chunkZ) {
        if (!viewable(world, chunkX, chunkZ)) return;
        world.refreshChunk(chunkX, chunkZ);
        final World target = world;
        final int cx = chunkX, cz = chunkZ;
        // One tick later: the chunk packet has gone out, so the untrack that
        // follows cannot be overtaken by the very packet it is compensating for.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { retrack(target, cx, cz); }
        }, 1L);
    }

    private boolean viewable(World world, int chunkX, int chunkZ) {
        int reach = Bukkit.getViewDistance() + 1;
        for (Player player : world.getPlayers()) {
            Chunk at = player.getLocation().getChunk();
            if (Math.abs(at.getX() - chunkX) <= reach && Math.abs(at.getZ() - chunkZ) <= reach) return true;
        }
        return false;
    }

    private void retrack(World world, int chunkX, int chunkZ) {
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;
            EntityTracker tracker = ((CraftWorld) world).getHandle().tracker;
            for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                EntityTrackerEntry entry = entryFor(tracker, entity);
                if (entry == null || entry.trackedPlayers.isEmpty()) continue;
                // Forget the watchers; the tracker's next pass re-adds everyone
                // in range and that is what re-emits the spawn packet.
                for (EntityPlayer viewer : new ArrayList<EntityPlayer>(entry.trackedPlayers)) entry.clear(viewer);
                chunkRepairs++;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("VISIBILITY_RETRACK_FAILED chunk=" + chunkX + "," + chunkZ + " " + t);
        }
    }

    // -- safety nets ------------------------------------------------------------

    /** Whatever is hitting you is something you are allowed to see. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile) {
            Object shooter = ((org.bukkit.entity.Projectile) damager).getShooter();
            if (shooter instanceof Entity) damager = (Entity) shooter;
        }
        if (!(damager instanceof LivingEntity) || damager instanceof Player) return;
        if (ensureTracked(damager, (Player) event.getEntity())) {
            damageRepairs++;
            plugin.getLogger().warning("VISIBILITY_REPAIR_ON_HIT victim=" + event.getEntity().getName()
                + " attacker=" + damager.getType() + " id=" + damager.getEntityId());
        }
    }

    /** Anything close enough to reach a player should already be on their screen. */
    private void sweep() {
        if (++sweeps % 60 == 0 && (chunkRepairs | damageRepairs | sweepRepairs) != 0L)
            plugin.getLogger().info("VISIBILITY_METRICS " + metrics());
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                Location at = player.getLocation();
                List<Entity> near;
                try { near = player.getNearbyEntities(28, 28, 28); }
                catch (RuntimeException e) { continue; }
                for (Entity entity : near) {
                    if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
                    if (entity.getLocation().distanceSquared(at) > 28 * 28) continue;
                    if (ensureTracked(entity, player)) {
                        sweepRepairs++;
                        plugin.getLogger().warning("VISIBILITY_REPAIR_ON_SWEEP player=" + player.getName()
                            + " entity=" + entity.getType() + " id=" + entity.getEntityId());
                    }
                }
            }
        }
    }

    /** Adds a player back to an entity's tracker when it has genuinely lost them. */
    private boolean ensureTracked(Entity entity, Player viewer) {
        try {
            EntityTracker tracker = ((CraftWorld) entity.getWorld()).getHandle().tracker;
            EntityTrackerEntry entry = entryFor(tracker, entity);
            if (entry == null) return false;
            EntityPlayer handle = ((CraftPlayer) viewer).getHandle();
            if (entry.trackedPlayers.contains(handle)) return false;
            entry.updatePlayer(handle);
            return true;
        } catch (Throwable t) { return false; }
    }

    private static EntityTrackerEntry entryFor(EntityTracker tracker, Entity entity) {
        return tracker.trackedEntities.get(((CraftEntity) entity).getHandle().getId());
    }
}
