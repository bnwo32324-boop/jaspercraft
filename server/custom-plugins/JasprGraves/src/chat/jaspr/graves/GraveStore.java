package chat.jaspr.graves;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Every grave on disk, indexed three ways: by id for menus, by block position
 * for the protection handlers (which run on hot events and must not scan), and
 * by chunk so a grave can be rebuilt when its chunk comes back.
 */
final class GraveStore {
    private final GravesPlugin plugin;
    private final File file;
    private final Map<UUID, Grave> byId = new LinkedHashMap<UUID, Grave>();
    private final Map<String, Map<Long, Grave>> byBlock = new HashMap<String, Map<Long, Grave>>();
    private final Map<String, List<Grave>> byChunk = new HashMap<String, List<Grave>>();
    private boolean dirty;

    GraveStore(GravesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    Collection<Grave> all() { return byId.values(); }

    Grave byId(UUID id) { return byId.get(id); }

    /** Either half of the headstone resolves to the same grave. */
    Grave at(String world, int x, int y, int z) {
        Map<Long, Grave> positions = byBlock.get(world);
        if (positions == null) return null;
        return positions.get(Long.valueOf(Grave.pack(x, y, z)));
    }

    List<Grave> inChunk(String world, int chunkX, int chunkZ) {
        List<Grave> list = byChunk.get(world + ':' + chunkX + ':' + chunkZ);
        return list == null ? java.util.Collections.<Grave>emptyList() : list;
    }

    /** Graves in this world whose chunk is already in memory; the rest wait for ChunkLoadEvent. */
    List<Grave> loaded(World world) {
        List<Grave> out = new ArrayList<Grave>();
        for (Grave grave : byId.values()) {
            if (!world.getName().equals(grave.world)) continue;
            if (!world.isChunkLoaded(grave.x >> 4, grave.z >> 4)) continue;
            out.add(grave);
        }
        return out;
    }

    List<Grave> ownedBy(UUID owner) {
        List<Grave> mine = new ArrayList<Grave>();
        for (Grave grave : byId.values()) if (grave.owner.equals(owner)) mine.add(grave);
        return mine;
    }

    void add(Grave grave) {
        byId.put(grave.id, grave);
        Map<Long, Grave> positions = byBlock.get(grave.world);
        if (positions == null) { positions = new HashMap<Long, Grave>(); byBlock.put(grave.world, positions); }
        positions.put(Long.valueOf(grave.basePos()), grave);
        positions.put(Long.valueOf(grave.headPos()), grave);
        List<Grave> chunk = byChunk.get(grave.chunkKey());
        if (chunk == null) { chunk = new ArrayList<Grave>(); byChunk.put(grave.chunkKey(), chunk); }
        chunk.add(grave);
        dirty = true;
    }

    void remove(Grave grave) {
        byId.remove(grave.id);
        Map<Long, Grave> positions = byBlock.get(grave.world);
        if (positions != null) {
            positions.remove(Long.valueOf(grave.basePos()));
            positions.remove(Long.valueOf(grave.headPos()));
            if (positions.isEmpty()) byBlock.remove(grave.world);
        }
        List<Grave> chunk = byChunk.get(grave.chunkKey());
        if (chunk != null) {
            chunk.remove(grave);
            if (chunk.isEmpty()) byChunk.remove(grave.chunkKey());
        }
        dirty = true;
    }

    void touch() { dirty = true; }

    void load() {
        byId.clear(); byBlock.clear(); byChunk.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("graves");
        if (root == null) return;
        int broken = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection node = root.getConfigurationSection(key);
            if (node == null) continue;
            try {
                UUID id = UUID.fromString(key);
                UUID owner = UUID.fromString(node.getString("owner"));
                Grave grave = new Grave(id, owner, node.getString("owner-name", "Unknown"),
                    node.getString("world"), node.getInt("x"), node.getInt("y"), node.getInt("z"),
                    node.getLong("created-at"), (float) node.getDouble("yaw"), node.getInt("experience"));
                if (grave.world == null) { broken++; continue; }
                grave.lastEncoded = node.getString("items");
                grave.contents.addAll(ItemCodec.decode(grave.lastEncoded));
                add(grave);
            } catch (RuntimeException e) {
                broken++;
            }
        }
        dirty = false;
        if (broken > 0) plugin.getLogger().warning("GRAVES_LOAD skipped=" + broken + " unreadable entries");
    }

    void save(boolean force) {
        if (!dirty && !force) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Grave grave : byId.values()) {
            String path = "graves." + grave.id;
            yaml.set(path + ".owner", grave.owner.toString());
            yaml.set(path + ".owner-name", grave.ownerName);
            yaml.set(path + ".world", grave.world);
            yaml.set(path + ".x", grave.x);
            yaml.set(path + ".y", grave.y);
            yaml.set(path + ".z", grave.z);
            yaml.set(path + ".created-at", grave.createdAt);
            yaml.set(path + ".yaw", grave.yaw);
            yaml.set(path + ".experience", grave.experience);
            String encoded = ItemCodec.encode(grave.contents);
            if (encoded == null) {
                // Never blank a grave over an encoding fault: fall back to its last good copy.
                plugin.getLogger().severe("GRAVES_ENCODE_FAILED grave=" + grave.id + " owner=" + grave.ownerName);
                encoded = grave.lastEncoded;
                if (encoded == null) continue;
            } else {
                grave.lastEncoded = encoded;
            }
            yaml.set(path + ".items", encoded);
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs())
                throw new IOException("data folder");
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("GRAVES_SAVE_FAILED " + e.getMessage());
        }
    }

    int size() { return byId.size(); }
}
