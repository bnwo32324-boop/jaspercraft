package chat.jaspr.atlas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Everything Atlas remembers, shared and per player, saved as YAML beside the plugin. Shared progress (liberated
 * provinces, fallen bosses, rescued captives, freed camps, the victory and its Rekindlers) can only move forward: nothing
 * here is ever undone by play. Per-player records hold standing with the Concord, what the player has learned, rewards
 * already given (so nothing is paid twice), heliodromes found, and the gate they came through.
 */
final class State {
    /** Shared. */
    int liberated;                                            // province bits
    boolean victory;
    long victoryAt;
    final List<String> rekindlers = new ArrayList<>();        // names of those who fought at the end
    final Map<String, Long> bossesFallen = new LinkedHashMap<>();   // boss id -> time
    final Map<String, String> captivesRescued = new LinkedHashMap<>(); // captive id -> rescuer name
    final Set<String> campsFreed = new HashSet<>();           // "i:j" of freed camps
    int boundFreed;                                           // how many of the Bound have been freed in all
    final Set<String> mechanisms = new HashSet<>();           // "font:0", "governor:1", "edict:2" silenced for good

    /** Per player. */
    static final class Player {
        String name = "";
        int standing = 10;
        long lastStandingTick;
        final Set<String> knows = new HashSet<>();        // "oath", "hymn", "counterpoint", "charter", "light", lore flags
        final Set<String> claimed = new HashSet<>();      // reward ids already given
        final Set<String> heliodromes = new HashSet<>();  // discovered heliodrome ids
        final Set<String> met = new HashSet<>();          // key figures met
        String homeGate;                                   // overworld gate "world;x;y;z" they came through
        String atlasGate;                                  // Atlas gate they last left by
        int rescued, slain;
        boolean welcomed;
    }

    final Map<UUID, Player> players = new HashMap<>();

    Player player(UUID id, String name) {
        Player p = players.computeIfAbsent(id, k -> new Player());
        if (name != null) p.name = name;
        return p;
    }

    boolean liberated(Realm.Province p) { return (liberated & p.bit) != 0; }

    int wardsDark() {
        int n = 0;
        for (Realm.Province p : new Realm.Province[] {Realm.Province.MARCHES, Realm.Province.WEALD, Realm.Province.FORGES, Realm.Province.FALLEN}) if (liberated(p)) n++;
        return n;
    }

    // ------------------------------------------------------------------ persistence

    void load(File f) {
        if (!f.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        liberated = y.getInt("liberated", 0) & Realm.ALL_LIBERATED;
        victory = y.getBoolean("victory", false);
        victoryAt = y.getLong("victoryAt", 0);
        rekindlers.clear();
        rekindlers.addAll(y.getStringList("rekindlers"));
        bossesFallen.clear();
        ConfigurationSection b = y.getConfigurationSection("bossesFallen");
        if (b != null) for (String k : b.getKeys(false)) bossesFallen.put(k, b.getLong(k));
        captivesRescued.clear();
        ConfigurationSection c = y.getConfigurationSection("captivesRescued");
        if (c != null) for (String k : c.getKeys(false)) captivesRescued.put(k, c.getString(k));
        campsFreed.clear();
        campsFreed.addAll(y.getStringList("campsFreed"));
        boundFreed = y.getInt("boundFreed", 0);
        mechanisms.clear();
        mechanisms.addAll(y.getStringList("mechanisms"));
        players.clear();
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ConfigurationSection s = ps.getConfigurationSection(key);
            Player p = player(id, s.getString("name", ""));
            p.standing = s.getInt("standing", 10);
            p.knows.addAll(s.getStringList("knows"));
            p.claimed.addAll(s.getStringList("claimed"));
            p.heliodromes.addAll(s.getStringList("heliodromes"));
            p.met.addAll(s.getStringList("met"));
            p.homeGate = s.getString("homeGate", null);
            p.atlasGate = s.getString("atlasGate", null);
            p.rescued = s.getInt("rescued", 0);
            p.slain = s.getInt("slain", 0);
            p.welcomed = s.getBoolean("welcomed", false);
        }
    }

    void save(File f) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.set("liberated", liberated);
        y.set("victory", victory);
        y.set("victoryAt", victoryAt);
        y.set("rekindlers", rekindlers);
        for (Map.Entry<String, Long> e : bossesFallen.entrySet()) y.set("bossesFallen." + e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : captivesRescued.entrySet()) y.set("captivesRescued." + e.getKey(), e.getValue());
        y.set("campsFreed", new ArrayList<>(campsFreed));
        y.set("boundFreed", boundFreed);
        y.set("mechanisms", new ArrayList<>(mechanisms));
        for (Map.Entry<UUID, Player> e : players.entrySet()) {
            String k = "players." + e.getKey() + ".";
            Player p = e.getValue();
            y.set(k + "name", p.name);
            y.set(k + "standing", p.standing);
            y.set(k + "knows", new ArrayList<>(p.knows));
            y.set(k + "claimed", new ArrayList<>(p.claimed));
            y.set(k + "heliodromes", new ArrayList<>(p.heliodromes));
            y.set(k + "met", new ArrayList<>(p.met));
            y.set(k + "homeGate", p.homeGate);
            y.set(k + "atlasGate", p.atlasGate);
            y.set(k + "rescued", p.rescued);
            y.set(k + "slain", p.slain);
            y.set(k + "welcomed", p.welcomed);
        }
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        y.save(tmp);
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
