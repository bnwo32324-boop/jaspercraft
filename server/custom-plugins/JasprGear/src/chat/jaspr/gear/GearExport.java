package chat.jaspr.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Build-time exporter (no server needed): prints the exact canonical SNBT of every gear item
 * and empty-slot icon, so the browser catalogue and panel can never drift from the server.
 * java -cp patched_1.12.2.jar;JasprGear.jar chat.jaspr.gear.GearExport out.json (UTF-8)
 */
public final class GearExport {
    private GearExport() {}

    public static void main(String[] args) throws java.io.IOException {
        JsonObject root = new JsonObject();
        root.addProperty("channel", GearPlugin.CHANNEL);
        root.addProperty("protocol", GearPlugin.PROTOCOL);
        JsonArray items = new JsonArray();
        for (GearItem item : GearItem.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id);
            o.addProperty("title", item.title);
            o.addProperty("type", item.type.name());
            o.addProperty("model", item.model);
            o.addProperty("inspiredBy", item.inspiredBy);
            o.addProperty("recipe", GearPlugin.recipeText(item));
            o.addProperty("snbt", GearItems.canonicalTag(item).toString());
            JsonArray fx = new JsonArray();
            for (String e : item.effects) fx.add(e);
            o.add("effects", fx);
            items.add(o);
        }
        root.add("items", items);
        JsonArray icons = new JsonArray();
        for (int i = 0; i < GearType.SLOT_COUNT; i++) {
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            o.addProperty("type", GearType.SLOTS[i].name());
            o.addProperty("model", GearItems.iconModel(i));
            o.addProperty("snbt", GearItems.iconTag(i).toString());
            icons.add(o);
        }
        root.add("icons", icons);
        JsonArray supplies = new JsonArray(); // Phase 2 consumables
        for (GearConsumable item : GearConsumable.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id);
            o.addProperty("title", item.title);
            o.addProperty("model", item.model);
            o.addProperty("rarity", item.rarity);
            o.addProperty("doses", item.doses);
            o.addProperty("minTier", item.minTier);
            o.addProperty("inspiredBy", item.inspiredBy);
            o.addProperty("recipe", GearPlugin.recipeText(item));
            o.addProperty("snbt", GearItems.canonicalTag(item).toString());
            JsonArray fx = new JsonArray();
            for (String e : item.effects) fx.add(e);
            o.add("effects", fx);
            supplies.add(o);
        }
        root.add("consumables", supplies);
        JsonArray statuses = new JsonArray();
        for (GearStatus s : GearStatus.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("title", s.title);
            o.addProperty("hud", s.hud);
            o.addProperty("color", String.valueOf(s.color));
            o.addProperty("harmful", s.harmful);
            statuses.add(o);
        }
        root.add("statuses", statuses);
        root.addProperty("hudProtocol", 2);
        byte[] bytes = root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (args.length > 0) java.nio.file.Files.write(java.nio.file.Paths.get(args[0]), bytes);
        else System.out.write(bytes);
        System.out.flush();
    }
}
