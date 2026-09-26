package chat.jaspr.lostcities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Port of cityassets.Palette: characters to block states, random mixes, 'damaged' forms, mobs, loot, torches. */
final class Palette {
    /** A weighted random mix ("blocks": [{"random": n, "block": ...}]). */
    static final class Mix {
        final int[] counts;
        final char[] states;
        Mix(int[] counts, char[] states) { this.counts = counts; this.states = states; }
    }

    final String name;
    /** Character -> Character (a block), Mix, or String (frompalette). */
    final Map<Character, Object> palette = new HashMap<>();
    final Map<Character, Character> damaged = new HashMap<>();
    final Map<Character, String> mobIds = new HashMap<>();
    final Map<Character, String> lootTables = new HashMap<>();
    final Map<Character, Map<String, Integer>> torchOrientations = new HashMap<>();

    Palette() { this.name = null; }

    Palette(JsonObject object) {
        this.name = object.get("name").getAsString();
        parsePaletteArray(object.get("palette").getAsJsonArray());
    }

    void merge(Palette other) {
        palette.putAll(other.palette);
        damaged.putAll(other.damaged);
        mobIds.putAll(other.mobIds);
        lootTables.putAll(other.lootTables);
        torchOrientations.putAll(other.torchOrientations);
    }

    void parsePaletteArray(JsonArray paletteArray) {
        for (JsonElement element : paletteArray) {
            JsonObject o = element.getAsJsonObject();
            Character c = o.get("char").getAsCharacter();
            Character dmg = null;
            if (o.has("damaged")) dmg = B.parse(o.get("damaged").getAsString());
            if (o.has("mob")) mobIds.put(c, o.get("mob").getAsString());
            if (o.has("loot")) lootTables.put(c, o.get("loot").getAsString());
            if (o.has("facing")) {
                Map<String, Integer> or = new HashMap<>();
                JsonObject torch = o.get("facing").getAsJsonObject();
                for (String f : new String[]{"north", "south", "west", "east", "up"})
                    or.put(f, torch.has(f) ? torch.get(f).getAsInt() : 0);
                torchOrientations.put(c, or);
            }
            if (o.has("block")) {
                char state = B.parse(o.get("block").getAsString());
                palette.put(c, state);
                if (dmg != null) damaged.put(state, dmg);
            } else if (o.has("frompalette")) {
                palette.put(c, o.get("frompalette").getAsString());
            } else if (o.has("blocks")) {
                JsonArray array = o.get("blocks").getAsJsonArray();
                List<int[]> blocks = new ArrayList<>();
                for (JsonElement el : array) {
                    JsonObject ob = el.getAsJsonObject();
                    int f = ob.get("random").getAsInt();
                    char state = B.parse(ob.get("block").getAsString());
                    blocks.add(new int[]{f, state});
                    if (dmg != null) damaged.put(state, dmg);
                }
                int[] counts = new int[blocks.size()];
                char[] states = new char[blocks.size()];
                for (int i = 0; i < counts.length; i++) { counts[i] = blocks.get(i)[0]; states[i] = (char) blocks.get(i)[1]; }
                palette.put(c, new Mix(counts, states));
            } else {
                throw new IllegalArgumentException("Illegal palette!");
            }
        }
    }
}
