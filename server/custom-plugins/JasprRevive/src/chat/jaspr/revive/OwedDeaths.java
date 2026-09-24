package chat.jaspr.revive;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Deaths that were earned but could not be carried out. A player who logs off
 * mid-bleed still owes that death, but killing them inside the quit event does
 * not reliably run the drop handlers, and a half-run death would destroy their
 * inventory instead of burying it. The debt is written down and collected the
 * next time they log in, where an ordinary death does all the ordinary things.
 */
final class OwedDeaths {
    static final class Entry {
        final UUID id;
        final String name;
        final String world;
        final double x, y, z;
        final float yaw, pitch;

        Entry(UUID id, String name, String world, double x, double y, double z, float yaw, float pitch) {
            this.id = id; this.name = name; this.world = world;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }

        Location location() {
            World target = Bukkit.getWorld(world);
            if (target == null) return null;
            return new Location(target, x, y, z, yaw, pitch);
        }
    }

    private final RevivePlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<UUID, Entry>();

    OwedDeaths(RevivePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "owed-deaths.yml");
    }

    void record(Downed state) {
        Location at = state.where;
        if (at == null || at.getWorld() == null) return;
        entries.put(state.id, new Entry(state.id, state.name, at.getWorld().getName(),
            at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()));
        save();
    }

    void restore(Entry entry) { entries.put(entry.id, entry); save(); }

    Entry take(UUID id) {
        Entry entry = entries.remove(id);
        if (entry != null) save();
        return entry;
    }
    Entry get(UUID id) { return entries.get(id); }

    void load() {
        entries.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("owed");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection node = root.getConfigurationSection(key);
            if (node == null) continue;
            try {
                UUID id = UUID.fromString(key);
                entries.put(id, new Entry(id, node.getString("name", "Someone"),
                    node.getString("world"), node.getDouble("x"), node.getDouble("y"), node.getDouble("z"),
                    (float) node.getDouble("yaw"), (float) node.getDouble("pitch")));
            } catch (RuntimeException ignored) { }
        }
        if (!entries.isEmpty()) plugin.getLogger().info("REVIVE_OWED_PENDING count=" + entries.size());
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : entries.values()) {
            String path = "owed." + entry.id;
            yaml.set(path + ".name", entry.name);
            yaml.set(path + ".world", entry.world);
            yaml.set(path + ".x", entry.x);
            yaml.set(path + ".y", entry.y);
            yaml.set(path + ".z", entry.z);
            yaml.set(path + ".yaw", entry.yaw);
            yaml.set(path + ".pitch", entry.pitch);
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs())
                throw new IOException("data folder");
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("REVIVE_OWED_SAVE_FAILED " + e.getMessage());
        }
    }
}
