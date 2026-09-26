package chat.jaspr.lostcities;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, thread-safe least-recently-used cache (the mod kept these maps unbounded and static). */
final class Lru<K, V> {
    private final int max;
    private final LinkedHashMap<K, V> map;
    private long hits, misses;

    Lru(int max) {
        this.max = max;
        this.map = new LinkedHashMap<K, V>(Math.min(max, 1024) * 2, 0.75f, true) {
            private static final long serialVersionUID = 1L;
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > Lru.this.max; }
        };
    }

    synchronized V get(K k) {
        V v = map.get(k);
        if (v == null) misses++; else hits++;
        return v;
    }

    synchronized void put(K k, V v) { map.put(k, v); }

    synchronized int size() { return map.size(); }

    synchronized void clear() { map.clear(); }

    synchronized String stats() { return size() + "/" + max + " hit=" + hits + " miss=" + misses; }
}
