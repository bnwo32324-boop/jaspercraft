package chat.jaspr.lostcities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Port of cityassets.BuildingPart: a stack of 16x16 (or smaller) slices, stored as vertical columns. */
final class BuildingPart {
    final String name;
    private final String[] slices;
    final int xSize;
    final int zSize;
    private char[][] vslices;
    private Palette localPalette;
    private final String refPaletteName;
    private final Map<String, Object> metadata = new HashMap<>();
    private final Assets assets;

    BuildingPart(JsonObject object, Assets assets) {
        this.assets = assets;
        name = object.get("name").getAsString();
        xSize = object.get("xsize").getAsInt();
        zSize = object.get("zsize").getAsInt();
        JsonArray sliceArray = object.get("slices").getAsJsonArray();
        slices = new String[sliceArray.size()];
        int i = 0;
        for (JsonElement element : sliceArray) {
            StringBuilder slice = new StringBuilder();
            for (JsonElement el : element.getAsJsonArray()) slice.append(el.getAsString());
            slices[i++] = slice.toString();
        }
        String ref = null;
        if (object.has("palette")) {
            if (object.get("palette").isJsonArray()) {
                localPalette = new Palette();
                localPalette.parsePaletteArray(object.get("palette").getAsJsonArray());
            } else {
                ref = object.get("palette").getAsString();
            }
        }
        refPaletteName = ref;
        if (object.has("meta")) {
            for (JsonElement element : object.get("meta").getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                String key = o.get("key").getAsString();
                if (o.has("integer")) metadata.put(key, o.get("integer").getAsInt());
                else if (o.has("float")) metadata.put(key, o.get("float").getAsFloat());
                else if (o.has("boolean")) metadata.put(key, o.get("boolean").getAsBoolean());
                else if (o.has("char")) metadata.put(key, o.get("char").getAsCharacter());
                else if (o.has("character")) metadata.put(key, o.get("character").getAsCharacter());
                else if (o.has("string")) metadata.put(key, o.get("string").getAsString());
            }
        }
    }

    Character getMetaChar(String key) { Object o = metadata.get(key); return o instanceof Character ? (Character) o : null; }
    Integer getMetaInteger(String key) { Object o = metadata.get(key); return o instanceof Integer ? (Integer) o : null; }
    boolean getMetaBoolean(String key) { Object o = metadata.get(key); return o instanceof Boolean && (Boolean) o; }

    int getSliceCount() { return slices.length; }

    char getPaletteChar(int x, int y, int z) { return slices[y].charAt(z * xSize + x); }

    /** Vertical columns, z*xSize+x; null where the column is all spaces. */
    char[] getVSlice(int x, int z) {
        if (vslices == null) {
            char[][] v = new char[xSize * zSize][];
            for (int xx = 0; xx < xSize; xx++) {
                for (int zz = 0; zz < zSize; zz++) {
                    char[] vs = new char[slices.length];
                    boolean empty = true;
                    for (int y = 0; y < slices.length; y++) {
                        char c = slices[y].charAt(zz * xSize + xx);
                        vs[y] = c;
                        if (c != ' ') empty = false;
                    }
                    v[zz * xSize + xx] = empty ? null : vs;
                }
            }
            vslices = v;
        }
        return vslices[z * xSize + x];
    }

    Palette getLocalPalette() {
        if (localPalette == null && refPaletteName != null) {
            localPalette = assets.palettes.get(refPaletteName);
            if (localPalette == null) throw new IllegalStateException("Could not find palette '" + refPaletteName + "'!");
        }
        return localPalette;
    }
}
