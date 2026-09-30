package chat.jaspr.backrooms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Moving between levels. A level's exit (the glowing corridor behind its arena) lets through only players who beat
 * that level's boss (tag br_clear_n) and no-clips them into the next level's landing; the Poolrooms' exit leads out of
 * the Backrooms. The Threshold has a door to every level from 2 to 7, open to each player for the levels they have
 * reached (tag br_reach_n); every landing has a way back to the Threshold. Anyone who ends up outside a level's zone
 * or below its floor is brought back to the landing of the level they were last in.
 */
final class Travel implements Listener {
    static final String REACH = "br_reach_", CLEAR = "br_clear_";

    private final BackroomsPlugin plugin;
    private final Map<UUID, Long> notice = new HashMap<>();
    private final Map<UUID, Integer> lastLevel = new HashMap<>();
    private final Map<UUID, Long> movedAt = new HashMap<>();
    long exits, doors, returns, rescues, refusedExits, refusedDoors;

    Travel(BackroomsPlugin plugin) { this.plugin = plugin; }

    static boolean reached(Player p, int n) { return n <= 1 || p.getScoreboardTags().contains(REACH + n); }
    static boolean cleared(Player p, int n) { return p.getScoreboardTags().contains(CLEAR + n); }

    /** The deepest level a player has reached. */
    static int deepest(Player p) {
        int best = 1;
        for (int n = 2; n <= Level.ALL.length; n++) if (reached(p, n)) best = n;
        return best;
    }

    /** The arrival point of a level's entry room. */
    Location landing(World w, Level lv) {
        double[] a = Rooms.arrival(lv);
        return new Location(w, a[0], a[1], a[2], (float) a[3], 0);
    }

    /** Twice a second: exits, doors, returns, rescue, and the level a player is in. */
    void tick() {
        World w = plugin.world();
        if (w == null) return;
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            UUID id = p.getUniqueId();
            Location l = p.getLocation();
            Level lv = Level.at(l.getX(), l.getZ());
            if (lv == null || l.getY() < Level.FLOOR - 14 || l.getY() > 250) { rescue(p); continue; }
            Integer before = lastLevel.put(id, lv.number);
            if (before == null || before != lv.number) entered(p, lv);
            if (now - movedAt.getOrDefault(id, 0L) < 2000) continue;   // just arrived somewhere: let them step off the pad
            if (Rooms.inExit(lv, l.getX(), l.getY(), l.getZ())) { exit(p, lv); continue; }
            if (lv.number == 1) {
                int n = Rooms.inDoor(l.getX(), l.getY(), l.getZ());
                if (n > 0) { door(p, n); continue; }
            } else if (Rooms.inReturn(lv, l.getX(), l.getY(), l.getZ())) {
                returns++;
                noclip(p, landing(w, Level.YELLOW), ChatColor.YELLOW + "The Threshold");
            }
        }
    }

    private void entered(Player p, Level lv) {
        // (addScoreboardTag reports true even for a tag the player has: test first)
        if (lv.number > 1 && !reached(p, lv.number) && p.addScoreboardTag(REACH + lv.number)) {
            plugin.getLogger().info("BACKROOMS_LEVEL_REACHED level=" + lv.number);
            p.sendMessage(ChatColor.GRAY + "The Threshold's door to " + lv.colour + lv.label() + ChatColor.GRAY + " is open to you from now on.");
        }
        p.sendTitle(lv.colour + "" + ChatColor.BOLD + "LEVEL " + lv.number, lv.colour + lv.title + ChatColor.GRAY + " - " + lv.blurb, 10, 70, 20);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BASS, 0.7f, 0.5f);
    }

    private void exit(Player p, Level lv) {
        if (!cleared(p, lv.number) && !(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) {
            refusedExits++;
            say(p, ChatColor.RED + "The exit will not let you through. " + ChatColor.GRAY + "Defeat " + lv.boss + " first (it waits in the arena behind you).");
            p.setVelocity(new Vector(-0.7, 0.2, 0));
            return;
        }
        exits++;
        if (lv.last()) {
            plugin.getLogger().info("BACKROOMS_EXITED level=7");
            p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "OUT", ChatColor.YELLOW + "You found the way out of the Backrooms", 10, 90, 30);
            plugin.portals().goHome(p);
            return;
        }
        Level next = lv.next();
        noclip(p, landing(p.getWorld(), next), next.colour + next.label());
        plugin.getLogger().info("BACKROOMS_EXIT from=" + lv.number + " to=" + next.number);
    }

    private void door(Player p, int n) {
        Level lv = Level.of(n);
        if (!reached(p, n) && !(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) {
            refusedDoors++;
            say(p, ChatColor.GRAY + "This door opens once you have reached " + lv.colour + lv.label() + ChatColor.GRAY + " the long way.");
            p.setVelocity(new Vector(0, 0.2, n <= 4 ? 0.7 : -0.7));
            return;
        }
        doors++;
        noclip(p, landing(p.getWorld(), lv), lv.colour + lv.label());
    }

    /** The Backrooms' way of moving you on: a flicker, a hum, and you are somewhere else. */
    void noclip(Player p, Location to, String where) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 0.6f, 0.4f);
        movedAt.put(p.getUniqueId(), System.currentTimeMillis());
        plugin.go(p, to, true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BASS, 0.6f, 0.6f); }, 5L);
    }

    private void rescue(Player p) {
        rescues++;
        Integer n = lastLevel.get(p.getUniqueId());
        Level lv = Level.of(n == null ? 1 : n);
        movedAt.put(p.getUniqueId(), System.currentTimeMillis());
        plugin.go(p, landing(p.getWorld(), lv == null ? Level.YELLOW : lv), false);
        p.setFallDistance(0);
        p.sendMessage(ChatColor.GRAY + "You no-clipped out of reality and back in.");
        plugin.getLogger().info("BACKROOMS_RESCUED level=" + (lv == null ? 1 : lv.number) + " rescues=" + rescues);
    }

    private void say(Player p, String text) {
        long now = System.currentTimeMillis();
        Long last = notice.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        notice.put(p.getUniqueId(), now);
        p.sendMessage(text);
    }

    /** Marks an arrival (anything that teleports a player in): no triggers for two seconds. */
    void arrived(Player p) { movedAt.put(p.getUniqueId(), System.currentTimeMillis()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); notice.remove(id); lastLevel.remove(id); movedAt.remove(id); }

    String describe() { return "exits=" + exits + " doors=" + doors + " returns=" + returns + " rescues=" + rescues + " refusedExits=" + refusedExits + " refusedDoors=" + refusedDoors; }
}
