package chat.jaspr.muse;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Short-lived blocks placed by abilities and hazards (webs, ice cages). Only ever placed into air, always restored to
 * air -- on their timer, on plugin disable, and if the server stops they never outlive a restart by more than the
 * restore on the next enable (they are journalled in memory only because they are placed only near players and the
 * restore runs within seconds).
 */
final class TempBlocks {
    private static final class Entry { final Block block; final Material type; final long until; Entry(Block b, Material t, long u) { block = b; type = t; until = u; } }
    private final Map<Location, Entry> placed = new HashMap<>();
    private final ArrayDeque<Entry> order = new ArrayDeque<>();
    private static final int CAP = 512;

    boolean place(Block b, Material type, int ticks, long now) {
        if (b.getType() != Material.AIR || placed.size() >= CAP || b.getY() < 1 || b.getY() > 254) return false;
        b.setType(type, false);
        Entry e = new Entry(b, type, now + ticks);
        placed.put(b.getLocation(), e);
        order.add(e);
        return true;
    }

    void tick(long now) {
        Iterator<Entry> it = order.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.until > now) continue;
            restore(e);
            it.remove();
        }
    }

    void restoreAll() {
        for (Entry e : order) restore(e);
        order.clear();
    }

    private void restore(Entry e) {
        placed.remove(e.block.getLocation());
        if (e.block.getType() == e.type) e.block.setType(Material.AIR, false);
    }

    int size() { return placed.size(); }
}
