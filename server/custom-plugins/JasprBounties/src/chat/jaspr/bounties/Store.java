package chat.jaspr.bounties;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Bounty boards on disk: one small YAML per player (plugins/JasprBounties/players/UUID.yml), loaded on join, written
 * when changed (on a timer, on quit and on shutdown), plus the weekly leaderboard (board.yml).
 */
final class Store {
    private final File dir, boardFile;
    private final Map<UUID, PlayerBoard> loaded = new HashMap<>();
    /** This week's completed bounties by player name (the leaderboard). */
    final Map<String, Integer> weekDone = new LinkedHashMap<>();
    long weekDoneKey = Long.MIN_VALUE;
    private boolean boardDirty;
    long saves, saveFailures, loads;

    Store(File dataFolder) {
        this.dir = new File(dataFolder, "players");
        this.boardFile = new File(dataFolder, "board.yml");
    }

    PlayerBoard get(UUID id) { return loaded.get(id); }

    Collection<PlayerBoard> loaded() { return loaded.values(); }

    PlayerBoard load(UUID id, String name) {
        PlayerBoard b = loaded.get(id);
        if (b != null) { if (name != null) b.name = name; return b; }
        b = read(id, name);
        loaded.put(id, b);
        loads++;
        return b;
    }

    /** Reads a board without keeping it (for owner commands on players who are offline). */
    PlayerBoard read(UUID id, String name) {
        PlayerBoard b = new PlayerBoard(id, name == null ? "" : name);
        File f = new File(dir, id + ".yml");
        if (!f.isFile()) return b;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (name == null) b.name = y.getString("name", "");
        b.day = y.getLong("day", Long.MIN_VALUE);
        b.week = y.getLong("week", Long.MIN_VALUE);
        b.rerolls = y.getInt("rerolls", 0);
        b.streak = y.getInt("streak", 0);
        b.streakDay = y.getLong("streak-day", Long.MIN_VALUE);
        b.bonusDay = y.getLong("bonus-day", Long.MIN_VALUE);
        b.remind = y.getBoolean("remind", true);
        b.lifetime = y.getInt("lifetime", 0);
        for (String r : y.getStringList("realms")) { Template.Realm realm = Template.Realm.parse(r); if (realm != null) b.realms.add(realm); }
        b.owed.addAll(y.getStringList("owed"));
        readSlots(y.getConfigurationSection("daily"), b.daily);
        readSlots(y.getConfigurationSection("weekly"), b.weekly);
        return b;
    }

    private static void readSlots(ConfigurationSection s, Bounty[] into) {
        if (s == null) return;
        for (int i = 0; i < into.length; i++) {
            ConfigurationSection e = s.getConfigurationSection(String.valueOf(i));
            if (e == null) continue;
            String id = e.getString("id");
            if (id == null || Template.get(id) == null) continue;   // a retired bounty: the slot is rolled again
            Bounty.State state;
            try { state = Bounty.State.valueOf(e.getString("state", "OPEN")); } catch (IllegalArgumentException bad) { state = Bounty.State.OPEN; }
            Material item = Material.getMaterial(e.getString("item", ""));
            into[i] = new Bounty(id, Math.max(1, e.getInt("need", 1)), e.getInt("have", 0), state, e.getLong("base", 0), e.getBoolean("half", false),
                Math.max(0, e.getInt("xp", 0)), item, Math.max(0, e.getInt("amount", 0)), Math.max(0, Math.min(1, e.getDouble("trinket", 0))));
        }
    }

    void save(PlayerBoard b) {
        if (!b.dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", b.name);
        y.set("day", b.day);
        y.set("week", b.week);
        y.set("rerolls", b.rerolls);
        y.set("streak", b.streak);
        y.set("streak-day", b.streakDay);
        y.set("bonus-day", b.bonusDay);
        y.set("remind", b.remind);
        y.set("lifetime", b.lifetime);
        List<String> realms = new ArrayList<>();
        for (Template.Realm r : b.realms) realms.add(r.name());
        y.set("realms", realms);
        y.set("owed", new ArrayList<>(b.owed));
        writeSlots(y, "daily", b.daily);
        writeSlots(y, "weekly", b.weekly);
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            y.save(new File(dir, b.id + ".yml"));
            b.dirty = false;
            saves++;
        } catch (IOException e) {
            saveFailures++;
        }
    }

    private static void writeSlots(YamlConfiguration y, String key, Bounty[] slots) {
        for (int i = 0; i < slots.length; i++) {
            Bounty x = slots[i];
            if (x == null) continue;
            String p = key + "." + i + ".";
            y.set(p + "id", x.id);
            y.set(p + "need", x.need);
            y.set(p + "have", x.have);
            y.set(p + "state", x.state.name());
            y.set(p + "base", x.base);
            y.set(p + "half", x.half);
            y.set(p + "xp", x.xp);
            y.set(p + "item", x.item == null ? "" : x.item.name());
            y.set(p + "amount", x.amount);
            y.set(p + "trinket", x.trinket);
        }
    }

    void unload(UUID id) {
        PlayerBoard b = loaded.remove(id);
        if (b != null) save(b);
    }

    void saveAll() {
        for (PlayerBoard b : loaded.values()) save(b);
        saveBoard();
    }

    // ---- the weekly leaderboard --------------------------------------------------------------------------------------

    void loadBoard() {
        if (!boardFile.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(boardFile);
        weekDoneKey = y.getLong("week", Long.MIN_VALUE);
        ConfigurationSection s = y.getConfigurationSection("done");
        if (s != null) for (String k : s.getKeys(false)) weekDone.put(k, s.getInt(k));
    }

    void countDone(String name, long weekKey) {
        if (weekKey != weekDoneKey) { weekDone.clear(); weekDoneKey = weekKey; }
        weekDone.merge(name, 1, Integer::sum);
        boardDirty = true;
    }

    void saveBoard() {
        if (!boardDirty) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("week", weekDoneKey);
        for (Map.Entry<String, Integer> e : weekDone.entrySet()) y.set("done." + e.getKey(), e.getValue());
        try {
            if (boardFile.getParentFile() != null) boardFile.getParentFile().mkdirs();
            y.save(boardFile);
            boardDirty = false;
        } catch (IOException e) {
            saveFailures++;
        }
    }
}
