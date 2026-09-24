package chat.jaspr.biomes;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Names the place you are standing in, once, the first time you walk into it.
 *
 * There are thirty-seven built sites in this world and until now none of them told you
 * what they were. Walking into one is the moment the whole thing is for, so it gets a
 * title card, a line of chat, a note, and a count of how many of them you have found.
 * After that the site is quiet: the record is per player and per site, so the same ruin
 * never announces itself twice and a second one of the same kind still does.
 *
 * The lookup is the same register that places the structures, so a name can only be
 * given to ground a structure was actually built on. A lattice that does not anchor over
 * the player's column is rejected on one modulo, which is why this can afford to run
 * twice a second for everybody online.
 */
public final class Discovery {
    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration found;
    private final Map<UUID, Integer> inside = new HashMap<UUID, Integer>();
    private BukkitTask task;
    private Terrain terrain;
    private World world;
    private long announced;

    public Discovery(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discoveries.yml");
        this.found = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        world = Bukkit.getWorld("world");
        if (world == null) return;
        terrain = new Terrain(world.getSeed());
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { sweep(); }
        }, 100L, 10L);
        plugin.getLogger().info("DISCOVERY_READY sites=" + Megaliths.siteCount()
                + " records=" + found.getKeys(false).size());
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        save();
        inside.clear();
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            found.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("DISCOVERY_SAVE_FAILED " + e);
        }
    }

    private void sweep() {
        if (world == null || terrain == null) return;
        boolean dirty = false;
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isOnline()) continue;
            int x = p.getLocation().getBlockX(), y = p.getLocation().getBlockY(), z = p.getLocation().getBlockZ();
            int k;
            try { k = Megaliths.located(terrain, x, y, z); }
            catch (RuntimeException e) { continue; }
            UUID id = p.getUniqueId();
            if (k < 0) { inside.remove(id); continue; }
            Integer was = inside.get(id);
            if (was != null && was.intValue() == k) continue;
            inside.put(id, Integer.valueOf(k));
            String site = Megaliths.key(terrain, k, x, z);
            String mine = id.toString() + "." + site;
            if (found.getBoolean(mine, false)) continue;
            found.set(mine, true);
            dirty = true;
            announce(p, k, found.getBoolean("server." + k, false));
            if (!found.getBoolean("server." + k, false)) found.set("server." + k, true);
            plugin.getLogger().info("DISCOVERY player=" + p.getName() + " site=" + Megaliths.siteName(k)
                    + " at=" + x + "," + y + "," + z);
        }
        if (dirty) {
            announced++;
            if ((announced & 7) == 0) save();     // batched; stop() flushes the rest
        }
    }

    /** How many of the thirty-seven this player has now put a name to. */
    private int tally(UUID id) {
        String prefix = id.toString() + ".";
        boolean[] seen = new boolean[Megaliths.siteCount()];
        for (String path : found.getKeys(true)) {
            if (!path.startsWith(prefix)) continue;
            String rest = path.substring(prefix.length());
            int dot = rest.indexOf('.');
            if (dot <= 0) continue;
            try {
                int k = Integer.parseInt(rest.substring(0, dot));
                if (k >= 0 && k < seen.length) seen[k] = true;
            } catch (NumberFormatException ignored) { }
        }
        int n = 0;
        for (boolean b : seen) if (b) n++;
        return n;
    }

    private void announce(Player p, int k, boolean seenBefore) {
        String name = Megaliths.siteName(k), blurb = Megaliths.siteBlurb(k);
        try {
            p.sendTitle(ChatColor.GOLD + name, ChatColor.GRAY + blurb, 10, 60, 20);
        } catch (Throwable ignored) { }
        p.sendMessage(ChatColor.DARK_GRAY + "— " + ChatColor.GOLD + name
                + ChatColor.DARK_GRAY + " — " + ChatColor.GRAY + blurb);
        int n = tally(p.getUniqueId());
        p.sendMessage(ChatColor.DARK_GRAY + "   " + n + " of " + Megaliths.siteCount() + " places found.");
        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.35f); }
        catch (Throwable ignored) { }
        if (!seenBefore) {
            String line = ChatColor.DARK_GRAY + "* " + ChatColor.YELLOW + p.getName()
                    + ChatColor.GRAY + " is the first to find " + ChatColor.GOLD + name + ChatColor.GRAY + ".";
            for (Player other : Bukkit.getOnlinePlayers()) if (other != p) other.sendMessage(line);
        }
    }
}
