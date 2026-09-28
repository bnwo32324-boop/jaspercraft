package chat.jaspr.atlas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * What cannot be broken in Atlas, and the Veil. The Concord's cities, the Gate of Strangers, the Last Watch and the
 * Lampwall are sung stone: players cannot break or build there (the rest of the Concord's land is open to them). The
 * Teeth never break, and Anthrakion's walls and tower hold until Atlas is won. Heliodromes are protected everywhere.
 * Until Kallias falls, the Veil (his own will, as ash) turns back anyone who crosses the Teeth anywhere but his Pylon.
 */
final class Guard implements Listener {
    private final AtlasPlugin plugin;
    private final Map<UUID, Long> told = new HashMap<>();
    private final Map<UUID, Integer> veiled = new HashMap<>();
    long refused, veilTurns, veilEjects;

    Guard(AtlasPlugin plugin) { this.plugin = plugin; }

    /** Why this block is protected (the message), or null if players may change it. */
    String protectedBy(Block b) {
        int x = b.getX(), z = b.getZ();
        Realm.Zone zone = Realm.zone(x, z);
        if (zone == Realm.Zone.TEETH) return "The Teeth do not break. Only the Pylon opens.";
        if (zone == Realm.Zone.LINE) return "The Lampwall is sung stone. It does not yield.";
        Realm.Place p = Realm.placeAt(x, z);
        if (p != null) switch (p) {
            case GATE_OF_STRANGERS: case ASTREION: case LAMPSA: case HIERANTHE: case MNEMEIA: case LAST_WATCH:
                return "The Concord's stone does not yield: its builders sang it into place. Build outside the cities.";
            default: break;
        }
        if (!plugin.state().victory) {
            double dx = x - Realm.TX, dz = z - Realm.TZ;
            if (dx * dx + dz * dz <= (Anthrakion.WALL_R + 4) * (Anthrakion.WALL_R + 4)) return "Anthrakion's stone is the Cinder's own. It will not break while the Heart burns.";
        }
        if (plugin.registry().ready())
            for (Registry.Spot s : plugin.registry().all("heliodrome:")) {
                double dx = x - s.x, dz = z - s.z;
                if (dx * dx + dz * dz <= 42 && Math.abs(b.getY() - s.y) <= 7) return "The heliodrome's ring is sung stone. It does not yield.";
            }
        return null;
    }

    private boolean exempt(Player p) { return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("jaspr.atlas.admin"); }

    private void refuse(Player p, String why) {
        refused++;
        Long last = told.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 3000) return;
        told.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(ChatColor.GRAY + why);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void breaking(BlockBreakEvent e) {
        if (!plugin.isAtlas(e.getBlock().getWorld()) || exempt(e.getPlayer())) return;
        String why = protectedBy(e.getBlock());
        if (why != null) { e.setCancelled(true); refuse(e.getPlayer(), why); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void placing(BlockPlaceEvent e) {
        if (!plugin.isAtlas(e.getBlock().getWorld()) || exempt(e.getPlayer())) return;
        String why = protectedBy(e.getBlock());
        if (why != null) { e.setCancelled(true); refuse(e.getPlayer(), why); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void pouring(PlayerBucketEmptyEvent e) {
        Block b = e.getBlockClicked().getRelative(e.getBlockFace());
        if (!plugin.isAtlas(b.getWorld()) || exempt(e.getPlayer())) return;
        String why = protectedBy(b);
        if (why != null) { e.setCancelled(true); refuse(e.getPlayer(), why); }
    }

    @EventHandler(ignoreCancelled = true)
    public void blast(EntityExplodeEvent e) {
        if (!plugin.isAtlas(e.getLocation().getWorld())) return;
        e.blockList().removeIf(b -> protectedBy(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void burn(BlockBurnEvent e) { if (plugin.isAtlas(e.getBlock().getWorld()) && protectedBy(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (!plugin.isAtlas(e.getBlock().getWorld())) return;
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL || e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || protectedBy(e.getBlock()) != null && e.getPlayer() == null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void mobGrief(EntityChangeBlockEvent e) {
        if (plugin.isAtlas(e.getBlock().getWorld()) && !(e.getEntity() instanceof Player) && protectedBy(e.getBlock()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ the Veil

    /** Whether (x, z) is behind the Veil right now: inside or on the Teeth, outside the Pylon, before Kallias falls. */
    boolean veiled(int x, int z) {
        if (plugin.state().bossesFallen.containsKey("kallias")) return false;
        if (Realm.teethDistance(x, z) >= Realm.TEETH_R + Realm.TEETH_W) return false;
        return !Realm.Place.PYLON.near(x, z, 0);
    }

    /** Twice a second: the Veil chokes and pushes back anyone behind it; ten seconds of it and it throws them out before the Pylon. */
    void tick() {
        World w = plugin.atlas();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || p.isDead()) { veiled.remove(p.getUniqueId()); continue; }
            Location l = p.getLocation();
            if (!veiled(l.getBlockX(), l.getBlockZ())) { veiled.remove(p.getUniqueId()); continue; }
            int n = veiled.merge(p.getUniqueId(), 1, Integer::sum);
            veilTurns++;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, true, false), true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 50, 1, true, false), true);
            if (n % 2 == 0) p.damage(1.5);
            Vector out = new Vector(l.getX() - Realm.TX, 0, l.getZ() - Realm.TZ).normalize().multiply(0.6).setY(0.2);
            if (p.getVehicle() != null) p.getVehicle().setVelocity(out); else p.setVelocity(out);
            if (n == 1 || n % 8 == 0) p.sendMessage(ChatColor.DARK_GRAY + "The Veil: " + ChatColor.GRAY + "ash so thick you choke, and it pushes you back. Only the Pylon of Teeth leads inside, and Kallias holds it.");
            if (n >= 20) {
                veiled.remove(p.getUniqueId());
                veilEjects++;
                Location out2 = new Location(w, 212.5, 0, 0.5, -90f, 0f);
                out2.setY(w.getHighestBlockYAt(212, 0) + 0.1);
                if (p.getVehicle() != null) p.leaveVehicle();
                p.setFallDistance(0);
                p.teleport(out2);
                p.sendMessage(ChatColor.GRAY + "The Veil spits you out on the Royal Road, before the Pylon of Teeth.");
                plugin.getLogger().info("ATLAS_VEIL_EJECT player=" + p.getName());
            }
        }
    }
}
