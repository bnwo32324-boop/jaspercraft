package chat.jaspr.lairs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Every decision about a cell, kept in plugins/JasprMutantLairs/lairs-<world uid>.yml: the lairs (being built or built,
 * with everything the bosses need in world coordinates, so nothing has to be drawn again at runtime) and the cells left
 * empty or the candidates refused for good. Written atomically; dirty state is flushed every 30 seconds and on disable.
 */
final class Ledger {
    /** A lair in the world. All positions are world block coordinates. */
    static final class Lair {
        final String key;
        final int ci, cj;
        final Design design;
        final int x, y, z, rot, w, d;
        String state;                       // building | built
        int[] boss;                         // x, y, z
        int wake;
        int[] vault;                        // x0, y0, z0, x1, y1, z1 (inclusive)
        final List<int[]> seals = new ArrayList<>();
        final List<int[]> vaultChests = new ArrayList<>();
        final List<Garrison> garrisons = new ArrayList<>();
        boolean vaultOpen;
        long restUntil;
        int kills;
        final Set<String> seen = new LinkedHashSet<>();
        final Set<String> chunksDone = new HashSet<>();
        // runtime only
        UUID bossId;
        long lonelySince;
        final List<UUID> minions = new ArrayList<>();

        Lair(String key, int ci, int cj, Design design, int x, int y, int z, int rot) {
            this.key = key; this.ci = ci; this.cj = cj; this.design = design; this.x = x; this.y = y; this.z = z; this.rot = rot;
            this.w = (rot & 1) == 0 ? design.w : design.d;
            this.d = (rot & 1) == 0 ? design.d : design.w;
        }
        boolean built() { return "built".equals(state); }
        int centerX() { return x + w / 2; }
        int centerZ() { return z + d / 2; }
        /** World coordinates of a local position. */
        int wx(int u, int v) { return x + Canvas.ru(u, v, design.w, design.d, rot); }
        int wz(int u, int v) { return z + Canvas.rv(u, v, design.w, design.d, rot); }
        boolean inVault(int bx, int by, int bz) {
            return vault != null && bx >= vault[0] && bx <= vault[3] && by >= vault[1] && by <= vault[4] && bz >= vault[2] && bz <= vault[5];
        }
        boolean inFootprint(int bx, int bz, int margin) { return bx >= x - margin && bx < x + w + margin && bz >= z - margin && bz < z + d + margin; }
    }

    static final class Garrison {
        final int x, y, z; final String pack;
        Garrison(int x, int y, int z, String pack) { this.x = x; this.y = y; this.z = z; this.pack = pack; }
    }

    private final File file;
    private final Map<String, Lair> lairs = new LinkedHashMap<>();
    /** Cells with no lair for good (every candidate refused). */
    private final Set<String> empty = new LinkedHashSet<>();
    /** Candidates refused for good, per cell: index -> reason. */
    private final Map<String, Map<Integer, String>> refused = new HashMap<>();
    private boolean dirty;

    Ledger(File file) { this.file = file; }

    Collection<Lair> all() { return lairs.values(); }
    Lair get(String key) { return lairs.get(key); }
    boolean decided(String key) { return lairs.containsKey(key) || empty.contains(key); }
    boolean empty(String key) { return empty.contains(key); }
    int emptyCount() { return empty.size(); }
    Map<Integer, String> refusals(String key) { return refused.computeIfAbsent(key, k -> new HashMap<>()); }

    void put(Lair l) { lairs.put(l.key, l); dirty = true; }
    void markEmpty(String key) { empty.add(key); dirty = true; }
    void refuse(String key, int index, String why) { refusals(key).put(index, why); dirty = true; }
    void touch() { dirty = true; }

    // ---------------------------------------------------------------- storage

    int load() throws IOException {
        if (!file.isFile()) return 0;
        YamlConfiguration y = new YamlConfiguration();
        try { y.load(file); } catch (Exception e) { throw new IOException("unreadable ledger " + file + ": " + e.getMessage()); }
        ConfigurationSection ls = y.getConfigurationSection("lairs");
        if (ls != null) for (String key : ls.getKeys(false)) {
            ConfigurationSection s = ls.getConfigurationSection(key);
            Design d = Designs.byId(s.getString("design"));
            if (d == null) throw new IOException("unknown design in ledger: " + s.getString("design"));
            String[] cell = key.split("_");
            Lair l = new Lair(key, Integer.parseInt(cell[0]), Integer.parseInt(cell[1]), d, s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getInt("rot"));
            l.state = s.getString("state", "building");
            l.boss = ints(s.getString("boss"));
            l.wake = s.getInt("wake", 20);
            l.vault = ints(s.getString("vault"));
            for (String p : s.getStringList("seals")) l.seals.add(ints(p));
            for (String p : s.getStringList("vaultChests")) l.vaultChests.add(ints(p));
            for (String p : s.getStringList("garrisons")) {
                String[] f = p.split(",", 4);
                l.garrisons.add(new Garrison(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3]));
            }
            l.vaultOpen = s.getBoolean("vaultOpen");
            l.restUntil = s.getLong("restUntil");
            l.kills = s.getInt("kills");
            l.seen.addAll(s.getStringList("seen"));
            l.chunksDone.addAll(s.getStringList("chunksDone"));
            lairs.put(key, l);
        }
        empty.addAll(y.getStringList("empty"));
        ConfigurationSection rs = y.getConfigurationSection("refused");
        if (rs != null) for (String key : rs.getKeys(false)) {
            Map<Integer, String> m = refusals(key);
            for (String e : rs.getStringList(key)) { int c = e.indexOf(':'); if (c > 0) m.put(Integer.parseInt(e.substring(0, c)), e.substring(c + 1)); }
        }
        return lairs.size();
    }

    void saveIfDirty() throws IOException { if (dirty) save(); }

    void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().header("JasprMutantLairs: lairs and decided cells of one world (do not edit while the server runs)");
        for (Lair l : lairs.values()) {
            String p = "lairs." + l.key + ".";
            y.set(p + "design", l.design.id);
            y.set(p + "state", l.state);
            y.set(p + "x", l.x); y.set(p + "y", l.y); y.set(p + "z", l.z); y.set(p + "rot", l.rot);
            y.set(p + "boss", str(l.boss));
            y.set(p + "wake", l.wake);
            y.set(p + "vault", str(l.vault));
            List<String> seals = new ArrayList<>(); for (int[] s : l.seals) seals.add(str(s));
            y.set(p + "seals", seals);
            List<String> chests = new ArrayList<>(); for (int[] s : l.vaultChests) chests.add(str(s));
            y.set(p + "vaultChests", chests);
            List<String> gs = new ArrayList<>(); for (Garrison g : l.garrisons) gs.add(g.x + "," + g.y + "," + g.z + "," + g.pack);
            y.set(p + "garrisons", gs);
            y.set(p + "vaultOpen", l.vaultOpen);
            y.set(p + "restUntil", l.restUntil);
            y.set(p + "kills", l.kills);
            y.set(p + "seen", new ArrayList<>(l.seen));
            if (!l.built()) y.set(p + "chunksDone", new ArrayList<>(l.chunksDone));
        }
        y.set("empty", new ArrayList<>(empty));
        for (Map.Entry<String, Map<Integer, String>> e : refused.entrySet()) {
            if (e.getValue().isEmpty() || empty.contains(e.getKey()) || lairs.containsKey(e.getKey())) continue;
            List<String> list = new ArrayList<>();
            for (Map.Entry<Integer, String> r : e.getValue().entrySet()) list.add(r.getKey() + ":" + r.getValue());
            y.set("refused." + e.getKey(), list);
        }
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(dir, file.getName() + ".pending");
        y.save(tmp);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        dirty = false;
    }

    static String str(int[] a) {
        if (a == null) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); }
        return b.toString();
    }
    static int[] ints(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] f = s.split(",");
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) out[i] = Integer.parseInt(f[i].trim());
        return out;
    }
}
