package chat.jaspr.rpg;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Logs and ores a player put down themselves, remembered (in memory, the most recent 50,000) so that placing and
 * breaking the same block again never pays out a gathering bonus (Mining Magician, Lumberjack).
 */
final class PlacedBlocks {
    private static final int LIMIT = 50_000;
    private final Map<String, Boolean> placed = new LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > LIMIT; }
    };

    static boolean watched(Material m) {
        return m == Material.LOG || m == Material.LOG_2 || m.name().endsWith("_ORE");
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ();
    }

    void place(Block b) { if (watched(b.getType())) placed.put(key(b), Boolean.TRUE); }

    /** True when the block was placed by a player (and forgets it: it is being broken). */
    boolean take(Block b) { return placed.remove(key(b)) != null; }

    int size() { return placed.size(); }
}
