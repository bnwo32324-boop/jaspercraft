package chat.jaspr.lostcities;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Predicate;

/** Port of cityassets.ConditionContext (the tests used by building parts, conditions for loot and mobs). */
class ConditionContext {
    final int level;
    final int floor;
    final int floorsBelowGround;
    final int floorsAboveGround;
    final String part;
    final String building;
    final int chunkX;
    final int chunkZ;
    final String biome;
    final boolean buildingOverride;

    ConditionContext(int level, int floor, int floorsBelowGround, int floorsAboveGround, String part, String building,
                     int chunkX, int chunkZ, String biome, boolean buildingOverride) {
        this.level = level; this.floor = floor; this.floorsBelowGround = floorsBelowGround; this.floorsAboveGround = floorsAboveGround;
        this.part = part; this.building = building; this.chunkX = chunkX; this.chunkZ = chunkZ;
        this.biome = biome; this.buildingOverride = buildingOverride;
    }

    private static Predicate<ConditionContext> combine(Predicate<ConditionContext> orig, Predicate<ConditionContext> t) {
        if (orig == null) return t;
        return c -> orig.test(c) && t.test(c);
    }

    static Predicate<ConditionContext> parseTest(JsonElement element) {
        Predicate<ConditionContext> test = null;
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("top")) {
            boolean top = obj.get("top").getAsBoolean();
            test = combine(test, top ? ConditionContext::isTopOfBuilding : c -> !c.isTopOfBuilding());
        }
        if (obj.has("ground")) {
            boolean ground = obj.get("ground").getAsBoolean();
            test = combine(test, ground ? ConditionContext::isGroundFloor : c -> !c.isGroundFloor());
        }
        if (obj.has("isbuilding")) {
            boolean b = obj.get("isbuilding").getAsBoolean();
            test = combine(test, b ? ConditionContext::isBuilding : c -> !c.isBuilding());
        }
        if (obj.has("issphere")) {
            boolean b = obj.get("issphere").getAsBoolean();
            test = combine(test, c -> !b);   // no city spheres outside the 'space' landscape
        }
        if (obj.has("chunkx")) { int x = obj.get("chunkx").getAsInt(); test = combine(test, c -> x == c.chunkX); }
        if (obj.has("chunkz")) { int z = obj.get("chunkz").getAsInt(); test = combine(test, c -> z == c.chunkZ); }
        if (obj.has("inpart")) { String p = obj.get("inpart").getAsString(); test = combine(test, c -> p.equals(c.part)); }
        if (obj.has("inbuilding")) { String b = obj.get("inbuilding").getAsString(); test = combine(test, c -> b.equals(c.building)); }
        if (obj.has("inbiome")) { String b = obj.get("inbiome").getAsString(); test = combine(test, c -> b.equalsIgnoreCase(c.biome)); }
        if (obj.has("cellar")) {
            boolean cellar = obj.get("cellar").getAsBoolean();
            test = combine(test, cellar ? ConditionContext::isCellar : c -> !c.isCellar());
        }
        if (obj.has("floor")) { int l = obj.get("floor").getAsInt(); test = combine(test, c -> c.floor == l); }
        if (obj.has("range")) {
            String[] split = obj.get("range").getAsString().split(",");
            try {
                int l1 = Integer.parseInt(split[0].trim()), l2 = Integer.parseInt(split[1].trim());
                test = combine(test, c -> c.floor >= l1 && c.floor <= l2);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Bad range specification: <l1>,<l2>!");
            }
        }
        if (test == null) test = c -> true;
        return test;
    }

    boolean isGroundFloor() { return floor == 0; }
    boolean isBuilding() { return buildingOverride || !"<none>".equals(building); }
    boolean isTopOfBuilding() { return floor >= floorsAboveGround; }
    boolean isCellar() { return floor < 0; }
}
