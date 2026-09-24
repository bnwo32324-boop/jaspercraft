package chat.jaspr.invasions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Loads, accrues and saves per-player progression. One small YAML file, written on a timer. */
final class ProgressStore {
    private final File file;
    private final Map<UUID, PlayerProgress> byId = new HashMap<UUID, PlayerProgress>();
    private boolean dirty;

    ProgressStore(File file) {
        this.file = file;
    }

    void load() {
        byId.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException malformed) {
                continue;
            }
            ConfigurationSection row = players.getConfigurationSection(key);
            if (row == null) continue;
            PlayerProgress progress = new PlayerProgress(id, row.getString("name", ""));
            progress.playedTicks = Math.max(0L, row.getLong("played-ticks", 0L));
            progress.invasionsSurvived = Math.max(0, row.getInt("invasions-survived", 0));
            progress.invasionsFaced = Math.max(0, row.getInt("invasions-faced", 0));
            progress.lastInvasionDay = row.getLong("last-invasion-day", -1L);
            byId.put(id, progress);
        }
    }

    void save() {
        if (!dirty) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            YamlConfiguration yaml = new YamlConfiguration();
            for (PlayerProgress progress : byId.values()) {
                String base = "players." + progress.id;
                yaml.set(base + ".name", progress.name);
                yaml.set(base + ".played-ticks", progress.playedTicks);
                yaml.set(base + ".invasions-survived", progress.invasionsSurvived);
                yaml.set(base + ".invasions-faced", progress.invasionsFaced);
                yaml.set(base + ".last-invasion-day", progress.lastInvasionDay);
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException ignored) {
            // Progression is not worth crashing a tick over; it is retried on the next save.
        }
    }

    PlayerProgress get(Player player) {
        PlayerProgress progress = byId.get(player.getUniqueId());
        if (progress == null) {
            progress = new PlayerProgress(player.getUniqueId(), player.getName());
            byId.put(player.getUniqueId(), progress);
            dirty = true;
        } else if (!player.getName().equals(progress.name)) {
            progress.name = player.getName();
            dirty = true;
        }
        return progress;
    }

    Collection<PlayerProgress> all() {
        return new ArrayList<PlayerProgress>(byId.values());
    }

    void markDirty() {
        dirty = true;
    }

    /**
     * Adds time to everyone currently playing. Called on a slow timer, so the amount credited is
     * however many ticks have passed since the last call rather than one tick at a time.
     */
    void accrue(Collection<? extends Player> online, long ticksElapsed, InvasionConfig settings) {
        if (ticksElapsed <= 0L) return;
        for (Player player : online) {
            GameMode mode = player.getGameMode();
            boolean counts = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE
                    || (settings.countCreativeTime && mode == GameMode.CREATIVE);
            if (!counts) continue;
            get(player).playedTicks += ticksElapsed;
            dirty = true;
        }
    }

    List<PlayerProgress> snapshot() {
        return new ArrayList<PlayerProgress>(byId.values());
    }
}
