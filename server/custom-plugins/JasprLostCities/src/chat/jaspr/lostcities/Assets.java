package chat.jaspr.lostcities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Port of cityassets.AssetRegistries and the asset types it registers: conditions, worldstyles, citystyles,
 * parts, buildings, multibuildings, styles and palettes (predefined cities/spheres are not in the stock assets).
 */
final class Assets {
    static final String[] FILES = {
        "conditions.json", "palette.json", "palette_desert.json", "palette_chisel.json", "palette_chisel_desert.json",
        "highwayparts.json", "railparts.json", "monorailparts.json", "buildingparts.json", "library.json"
    };

    final Map<String, Condition> conditions = new LinkedHashMap<>();
    final Map<String, WorldStyle> worldStyles = new LinkedHashMap<>();
    final Map<String, CityStyle> cityStyles = new LinkedHashMap<>();
    final Map<String, BuildingPart> parts = new LinkedHashMap<>();
    final Map<String, Building> buildings = new LinkedHashMap<>();
    final Map<String, MultiBuilding> multiBuildings = new LinkedHashMap<>();
    final Map<String, Style> styles = new LinkedHashMap<>();
    final Map<String, Palette> palettes = new LinkedHashMap<>();

    interface Source { InputStream open(String file) throws IOException; }

    static Assets load(Source source) throws IOException {
        Assets a = new Assets();
        for (String f : FILES) {
            try (InputStream in = source.open(f)) {
                if (in == null) throw new IOException("Missing asset " + f);
                a.load(in, f);
            }
        }
        // Resolve citystyle inheritance up front (the mod did it lazily on first get()).
        for (CityStyle cs : a.cityStyles.values()) cs.init(a);
        return a;
    }

    @SuppressWarnings("deprecation")
    private void load(InputStream in, String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            JsonElement element = new JsonParser().parse(br);
            for (JsonElement entry : element.getAsJsonArray()) {
                JsonObject object = entry.getAsJsonObject();
                String type = object.get("type").getAsString();
                switch (type) {
                    case "style": { Style s = new Style(object); styles.put(s.name, s); break; }
                    case "condition": { Condition c = new Condition(object); conditions.put(c.name, c); break; }
                    case "palette": { Palette p = new Palette(object); palettes.put(p.name, p); break; }
                    case "citystyle": { CityStyle c = new CityStyle(object); cityStyles.put(c.name, c); break; }
                    case "part": { BuildingPart p = new BuildingPart(object, this); parts.put(p.name, p); break; }
                    case "building": { Building b = new Building(object); buildings.put(b.name, b); break; }
                    case "multibuilding": { MultiBuilding m = new MultiBuilding(object); multiBuildings.put(m.name, m); break; }
                    case "worldstyle": { WorldStyle w = new WorldStyle(object); worldStyles.put(w.name, w); break; }
                    case "city": case "sphere": break;   // none in the stock assets
                    default: throw new IOException("Unknown type '" + type + " in " + filename + "'!");
                }
            }
        }
    }

    BuildingPart part(String name) { return name == null ? null : parts.get(name); }

    // ------------------------------------------------------------------ Tools.getRandomFromList

    static final class Weighted {
        final float weight;
        final String value;
        Weighted(float weight, String value) { this.weight = weight; this.value = value; }
    }

    static String getRandomFromList(Random random, List<Weighted> list) {
        if (list.isEmpty()) return null;
        float total = 0;
        for (Weighted w : list) total += w.weight;
        float r = random.nextFloat() * total;
        for (Weighted w : list) {
            r -= w.weight;
            if (r <= 0) return w.value;
        }
        return null;
    }

    // ------------------------------------------------------------------ asset types

    static final class Condition {
        final String name;
        private final List<Predicate<ConditionContext>> tests = new ArrayList<>();
        private final List<Weighted> values = new ArrayList<>();

        Condition(JsonObject object) {
            name = object.get("name").getAsString();
            for (JsonElement element : object.get("values").getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                values.add(new Weighted(o.get("factor").getAsFloat(), o.get("value").getAsString()));
                tests.add(ConditionContext.parseTest(element));
            }
        }

        String getRandomValue(Random random, ConditionContext info) {
            List<Weighted> ok = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) if (tests.get(i).test(info)) ok.add(values.get(i));
            if (ok.isEmpty()) return null;
            return getRandomFromList(random, ok);
        }
    }

    static final class Building {
        final String name;
        int minFloors = -1, minCellars = -1, maxFloors = -1, maxCellars = -1;
        final char fillerBlock;
        float prefersLonely = 0.0f;
        private final List<Predicate<ConditionContext>> tests = new ArrayList<>(), tests2 = new ArrayList<>();
        private final List<String> parts = new ArrayList<>(), parts2 = new ArrayList<>();

        Building(JsonObject object) {
            name = object.get("name").getAsString();
            if (object.has("minfloors")) minFloors = object.get("minfloors").getAsInt();
            if (object.has("mincellars")) minCellars = object.get("mincellars").getAsInt();
            if (object.has("maxfloors")) maxFloors = object.get("maxfloors").getAsInt();
            if (object.has("maxcellars")) maxCellars = object.get("maxcellars").getAsInt();
            if (object.has("preferslonely")) prefersLonely = object.get("preferslonely").getAsFloat();
            if (!object.has("filler")) throw new IllegalArgumentException("'filler' is required for building '" + name + "'!");
            fillerBlock = object.get("filler").getAsCharacter();
            read(object, "parts", tests, parts);
            read(object, "parts2", tests2, parts2);
        }

        private static void read(JsonObject object, String key, List<Predicate<ConditionContext>> t, List<String> p) {
            if (!object.has(key)) return;
            for (JsonElement element : object.get(key).getAsJsonArray()) {
                p.add(element.getAsJsonObject().get("part").getAsString());
                t.add(ConditionContext.parseTest(element));
            }
        }

        String getRandomPart(Random random, ConditionContext info) { return pick(random, info, tests, parts); }
        String getRandomPart2(Random random, ConditionContext info) { return pick(random, info, tests2, parts2); }

        private static String pick(Random random, ConditionContext info, List<Predicate<ConditionContext>> t, List<String> p) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < p.size(); i++) if (t.get(i).test(info)) names.add(p.get(i));
            if (names.isEmpty()) return null;
            return names.get(random.nextInt(names.size()));
        }
    }

    static final class MultiBuilding {
        final String name;
        final int dimX, dimZ;
        private final String[][] buildings;

        MultiBuilding(JsonObject object) {
            name = object.get("name").getAsString();
            dimX = object.get("dimx").getAsInt();
            dimZ = object.get("dimz").getAsInt();
            JsonArray array = object.get("buildings").getAsJsonArray();
            buildings = new String[dimX][dimZ];
            // Exactly as the mod: stored [z][x] but read back [x][z] (the stock multibuildings are 2x2).
            for (int z = 0; z < dimZ; z++) {
                JsonArray ar = array.get(z).getAsJsonArray();
                for (int x = 0; x < dimX; x++) buildings[z][x] = ar.get(x).getAsString();
            }
        }

        String getBuilding(int x, int z) { return buildings[x][z]; }
    }

    static final class Style {
        final String name;
        private final List<List<Weighted>> randomPaletteChoices = new ArrayList<>();

        Style(JsonObject object) {
            name = object.get("name").getAsString();
            for (JsonElement element : object.get("randompalettes").getAsJsonArray()) {
                List<Weighted> palettes = new ArrayList<>();
                for (JsonElement el : element.getAsJsonArray())
                    palettes.add(new Weighted(el.getAsJsonObject().get("factor").getAsFloat(), el.getAsJsonObject().get("palette").getAsString()));
                randomPaletteChoices.add(palettes);
            }
        }

        Palette getRandomPalette(Assets assets, Random random) {
            Palette palette = new Palette();
            for (List<Weighted> pairs : randomPaletteChoices) {
                float total = 0;
                for (Weighted w : pairs) total += w.weight;
                float r = random.nextFloat() * total;
                Palette tomerge = null;
                for (Weighted w : pairs) {
                    r -= w.weight;
                    if (r <= 0) { tomerge = assets.palettes.get(w.value); break; }
                }
                if (tomerge == null) tomerge = assets.palettes.get(pairs.get(pairs.size() - 1).value);
                if (tomerge == null) throw new IllegalStateException("Palette missing in style " + name);
                palette.merge(tomerge);
            }
            return palette;
        }
    }

    static final class CityStyle {
        final String name;
        private final List<Weighted> buildingSelector = new ArrayList<>(), bridgeSelector = new ArrayList<>(),
            parkSelector = new ArrayList<>(), fountainSelector = new ArrayList<>(), stairSelector = new ArrayList<>(),
            frontSelector = new ArrayList<>(), railDungeonSelector = new ArrayList<>(), multiBuildingSelector = new ArrayList<>();
        String style;
        Integer streetWidth, minFloorCount, minCellarCount, maxFloorCount, maxCellarCount;
        Float explosionChance;
        Character streetBlock, streetBaseBlock, streetVariantBlock, parkElevationBlock, corridorRoofBlock,
            corridorGlassBlock, railMainBlock, borderBlock, wallBlock;
        private final String inherit;
        private boolean resolved;

        CityStyle(JsonObject object) {
            name = object.get("name").getAsString();
            inherit = object.has("inherit") ? object.get("inherit").getAsString() : null;
            if (object.has("style")) style = object.get("style").getAsString();
            if (object.has("explosionchance")) explosionChance = object.get("explosionchance").getAsFloat();
            if (object.has("streetblocks")) {
                JsonObject s = object.get("streetblocks").getAsJsonObject();
                if (s.has("border")) borderBlock = s.get("border").getAsCharacter();
                if (s.has("wall")) wallBlock = s.get("wall").getAsCharacter();
                if (s.has("street")) streetBlock = s.get("street").getAsCharacter();
                if (s.has("streetvariant")) streetVariantBlock = s.get("streetvariant").getAsCharacter();
                if (s.has("streetbase")) streetBaseBlock = s.get("streetbase").getAsCharacter();
                if (s.has("width")) streetWidth = s.get("width").getAsInt();
            }
            if (object.has("buildingsettings")) {
                JsonObject s = object.get("buildingsettings").getAsJsonObject();
                if (s.has("maxfloors")) maxFloorCount = s.get("maxfloors").getAsInt();
                if (s.has("maxcellars")) maxCellarCount = s.get("maxcellars").getAsInt();
                if (s.has("minfloors")) minFloorCount = s.get("minfloors").getAsInt();
                if (s.has("mincellars")) minCellarCount = s.get("mincellars").getAsInt();
            }
            if (object.has("railblocks")) railMainBlock = object.get("railblocks").getAsJsonObject().get("railmain").getAsCharacter();
            if (object.has("parkblocks")) parkElevationBlock = object.get("parkblocks").getAsJsonObject().get("elevation").getAsCharacter();
            if (object.has("corridorblocks")) {
                JsonObject s = object.get("corridorblocks").getAsJsonObject();
                if (s.has("roof")) corridorRoofBlock = s.get("roof").getAsCharacter();
                if (s.has("glass")) corridorGlassBlock = s.get("glass").getAsCharacter();
            }
            parse(object, buildingSelector, "buildings", "building");
            parse(object, multiBuildingSelector, "multibuildings", "multibuilding");
            parse(object, parkSelector, "parks", "park");
            parse(object, fountainSelector, "fountains", "fountain");
            parse(object, stairSelector, "stairs", "stair");
            parse(object, frontSelector, "fronts", "front");
            parse(object, bridgeSelector, "bridges", "bridge");
            parse(object, railDungeonSelector, "raildungeons", "dungeon");
        }

        private static void parse(JsonObject object, List<Weighted> selector, String arrayName, String elName) {
            if (!object.has(arrayName)) return;
            for (JsonElement element : object.get(arrayName).getAsJsonArray()) {
                if (element.getAsJsonObject().has("clear")) selector.clear();
                else selector.add(new Weighted(element.getAsJsonObject().get("factor").getAsFloat(), element.getAsJsonObject().get(elName).getAsString()));
            }
        }

        void init(Assets assets) {
            if (resolved) return;
            resolved = true;
            if (inherit == null) return;
            CityStyle from = assets.cityStyles.get(inherit);
            if (from == null) throw new IllegalStateException("Cannot find citystyle '" + inherit + "' to inherit from!");
            from.init(assets);
            if (style == null) style = from.style;
            buildingSelector.addAll(from.buildingSelector);
            bridgeSelector.addAll(from.bridgeSelector);
            parkSelector.addAll(from.parkSelector);
            fountainSelector.addAll(from.fountainSelector);
            stairSelector.addAll(from.stairSelector);
            frontSelector.addAll(from.frontSelector);
            railDungeonSelector.addAll(from.railDungeonSelector);
            multiBuildingSelector.addAll(from.multiBuildingSelector);
            if (explosionChance == null) explosionChance = from.explosionChance;
            if (streetWidth == null) streetWidth = from.streetWidth;
            if (minFloorCount == null) minFloorCount = from.minFloorCount;
            if (minCellarCount == null) minCellarCount = from.minCellarCount;
            if (maxFloorCount == null) maxFloorCount = from.maxFloorCount;
            if (maxCellarCount == null) maxCellarCount = from.maxCellarCount;
            if (streetBlock == null) streetBlock = from.streetBlock;
            if (streetBaseBlock == null) streetBaseBlock = from.streetBaseBlock;
            if (streetVariantBlock == null) streetVariantBlock = from.streetVariantBlock;
            if (parkElevationBlock == null) parkElevationBlock = from.parkElevationBlock;
            if (corridorRoofBlock == null) corridorRoofBlock = from.corridorRoofBlock;
            if (corridorGlassBlock == null) corridorGlassBlock = from.corridorGlassBlock;
            if (railMainBlock == null) railMainBlock = from.railMainBlock;
            if (borderBlock == null) borderBlock = from.borderBlock;
            if (wallBlock == null) wallBlock = from.wallBlock;
        }

        int getStreetWidth() { return streetWidth == null ? 8 : streetWidth; }
        String getRandomStair(Random r) { return getRandomFromList(r, stairSelector); }
        String getRandomFront(Random r) { return getRandomFromList(r, frontSelector); }
        String getRandomRailDungeon(Random r) { return getRandomFromList(r, railDungeonSelector); }
        String getRandomPark(Random r) { return getRandomFromList(r, parkSelector); }
        String getRandomBridge(Random r) { return getRandomFromList(r, bridgeSelector); }
        String getRandomFountain(Random r) { return getRandomFromList(r, fountainSelector); }
        String getRandomBuilding(Random r) { return getRandomFromList(r, buildingSelector); }
        String getRandomMultiBuilding(Random r) { return getRandomFromList(r, multiBuildingSelector); }
    }

    static final class WorldStyle {
        final String name;
        final String outsideStyle;
        private final List<Set<String>> biomeTests = new ArrayList<>();   // null = always
        private final List<Weighted> cityStyles = new ArrayList<>();

        WorldStyle(JsonObject object) {
            name = object.get("name").getAsString();
            outsideStyle = object.get("outsidestyle").getAsString();
            for (JsonElement element : object.get("citystyles").getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                cityStyles.add(new Weighted(o.get("factor").getAsFloat(), o.get("citystyle").getAsString()));
                Set<String> biomes = null;
                if (o.has("biomes")) {
                    biomes = new HashSet<>();
                    for (JsonElement el : o.get("biomes").getAsJsonArray()) {
                        String b = el.getAsString();
                        if (b.startsWith("minecraft:")) b = b.substring(10);
                        biomes.add(b.toLowerCase());
                    }
                }
                biomeTests.add(biomes);
            }
        }

        /** biomes: the mod's five biome samples (indices 55, 54, 56, 5 and 95 of its 10x10 grid). */
        String getRandomCityStyle(String[] biomes, Random random) {
            List<Weighted> ct = new ArrayList<>();
            for (int i = 0; i < cityStyles.size(); i++) {
                Set<String> test = biomeTests.get(i);
                if (test == null || hasBiome(test, biomes)) ct.add(cityStyles.get(i));
            }
            return getRandomFromList(random, ct);
        }

        private static boolean hasBiome(Set<String> set, String[] biomes) {
            for (String b : biomes) if (set.contains(b)) return true;
            return false;
        }
    }

    Map<String, Integer> counts() {
        Map<String, Integer> m = new HashMap<>();
        m.put("parts", parts.size());
        m.put("buildings", buildings.size());
        m.put("multibuildings", multiBuildings.size());
        m.put("palettes", palettes.size());
        m.put("styles", styles.size());
        m.put("citystyles", cityStyles.size());
        m.put("worldstyles", worldStyles.size());
        m.put("conditions", conditions.size());
        return m;
    }
}
