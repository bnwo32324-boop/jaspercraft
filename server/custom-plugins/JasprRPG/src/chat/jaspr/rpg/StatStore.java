package chat.jaspr.rpg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Loads and saves stat sheets. One small YAML file, written on a timer and on shutdown. */
final class StatStore {
    private final File file;
    private final Map<UUID, PlayerStats> byId = new HashMap<UUID, PlayerStats>();
    private boolean dirty;

    StatStore(File file) { this.file = file; }

    void load() {
        byId.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException malformed) { continue; }
            ConfigurationSection row = players.getConfigurationSection(key);
            if (row == null) continue;
            PlayerStats sheet = new PlayerStats(id, row.getString("name", ""));
            ConfigurationSection statSection = row.getConfigurationSection("stats");
            if (statSection != null) {
                for (String statKey : statSection.getKeys(false)) {
                    StatType stat = StatType.byKey(statKey);
                    if (stat != null) sheet.set(stat, statSection.getInt(statKey, 0));
                }
            }
            byId.put(id, sheet);
        }
    }

    void save() {
        if (!dirty) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            YamlConfiguration yaml = new YamlConfiguration();
            for (PlayerStats sheet : byId.values()) {
                if (sheet.isEmpty()) continue;
                String base = "players." + sheet.id;
                yaml.set(base + ".name", sheet.name);
                for (Map.Entry<StatType, Integer> entry : sheet.view().entrySet()) {
                    yaml.set(base + ".stats." + entry.getKey().key(), entry.getValue());
                }
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException ignored) {
            // Retried on the next save; a lost write is not worth failing a tick over.
        }
    }

    PlayerStats get(Player player) {
        PlayerStats sheet = byId.get(player.getUniqueId());
        if (sheet == null) {
            sheet = new PlayerStats(player.getUniqueId(), player.getName());
            byId.put(player.getUniqueId(), sheet);
        } else if (!player.getName().equals(sheet.name)) {
            sheet.name = player.getName();
            dirty = true;
        }
        return sheet;
    }

    void markDirty() { dirty = true; }

    Collection<PlayerStats> all() { return new ArrayList<PlayerStats>(byId.values()); }

    List<PlayerStats> ranked() {
        List<PlayerStats> sheets = new ArrayList<PlayerStats>(byId.values());
        java.util.Collections.sort(sheets, new java.util.Comparator<PlayerStats>() {
            @Override public int compare(PlayerStats a, PlayerStats b) {
                return Integer.compare(b.totalLevels(), a.totalLevels());
            }
        });
        return sheets;
    }
}
