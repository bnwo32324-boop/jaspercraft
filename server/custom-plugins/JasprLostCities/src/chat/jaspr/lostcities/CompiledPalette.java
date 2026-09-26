package chat.jaspr.lostcities;

import java.util.HashMap;
import java.util.Map;

/** Port of cityassets.CompiledPalette: the per-chunk resolved palette (random mixes as 128-entry tables). */
final class CompiledPalette {

    static final class Info {
        final String mobId;
        final String loot;
        final Map<String, Integer> torchOrientations;
        Info(String mobId, String loot, Map<String, Integer> torchOrientations) {
            this.mobId = mobId; this.loot = loot; this.torchOrientations = torchOrientations;
        }
    }

    /** Character (single state) or char[128] (random table). ASCII keys in an array, the rest in a map. */
    private final Object[] fast = new Object[128];
    private final Map<Character, Object> slow = new HashMap<>();
    private final Map<Character, Character> damagedToBlock = new HashMap<>();
    private final Info[] infoFast = new Info[128];
    private final Map<Character, Info> infoSlow = new HashMap<>();

    CompiledPalette(CompiledPalette other, Palette... palettes) {
        System.arraycopy(other.fast, 0, fast, 0, 128);
        slow.putAll(other.slow);
        damagedToBlock.putAll(other.damagedToBlock);
        System.arraycopy(other.infoFast, 0, infoFast, 0, 128);
        infoSlow.putAll(other.infoSlow);
        addPalettes(palettes);
    }

    CompiledPalette(Palette... palettes) {
        addPalettes(palettes);
    }

    private Object raw(char c) { return c < 128 ? fast[c] : slow.get(c); }
    private boolean has(char c) { return raw(c) != null; }
    private void put(char c, Object v) { if (c < 128) fast[c] = v; else slow.put(c, v); }
    private void putInfo(char c, Info i) { if (c < 128) infoFast[c] = i; else infoSlow.put(c, i); }

    private static int addEntries(char[] randomBlocks, int idx, char c, int cnt) {
        for (int i = 0; i < cnt; i++) {
            if (idx >= randomBlocks.length) return idx;
            randomBlocks[idx++] = c;
        }
        return idx;
    }

    private void addPalettes(Palette[] palettes) {
        // First the straight palette entries
        for (Palette p : palettes) {
            for (Map.Entry<Character, Object> entry : p.palette.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Character) {
                    put(entry.getKey(), value);
                } else if (value instanceof Palette.Mix) {
                    Palette.Mix mix = (Palette.Mix) value;
                    char[] randomBlocks = new char[128];
                    int idx = 0;
                    for (int i = 0; i < mix.counts.length; i++) {
                        idx = addEntries(randomBlocks, idx, mix.states[i], mix.counts[i]);
                        if (idx >= randomBlocks.length) break;
                    }
                    put(entry.getKey(), randomBlocks);
                }
            }
        }
        // Then the entries that refer to other entries (only where the key is not already set)
        boolean dirty = true;
        while (dirty) {
            dirty = false;
            for (Palette p : palettes) {
                for (Map.Entry<Character, Object> entry : p.palette.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        char c = ((String) value).charAt(0);
                        if (has(c) && !has(entry.getKey())) {
                            put(entry.getKey(), raw(c));
                            dirty = true;
                        }
                    }
                }
            }
        }
        for (Palette p : palettes) {
            damagedToBlock.putAll(p.damaged);
            for (Map.Entry<Character, String> e : p.mobIds.entrySet()) putInfo(e.getKey(), new Info(e.getValue(), null, null));
            for (Map.Entry<Character, String> e : p.lootTables.entrySet()) putInfo(e.getKey(), new Info(null, e.getValue(), null));
            for (Map.Entry<Character, Map<String, Integer>> e : p.torchOrientations.entrySet()) putInfo(e.getKey(), new Info(null, null, e.getValue()));
        }
    }

    /** True if this is a simple character with a single value in the palette. */
    boolean isSimple(char c) { return raw(c) instanceof Character; }

    /** The state for a palette character, or null if the palette has no entry. Random mixes use the fast rand. */
    Character get(char c, FastRand rand) {
        Object o = raw(c);
        if (o == null) return null;
        if (o instanceof Character) return (Character) o;
        return ((char[]) o)[rand.next128()];
    }

    char getOr(char c, FastRand rand, char fallback) {
        Character r = get(c, rand);
        return r == null ? fallback : r;
    }

    Character canBeDamagedToIronBars(char b) { return damagedToBlock.get(b); }

    Info getInfo(char c) { return c < 128 ? infoFast[c] : infoSlow.get(c); }
}
