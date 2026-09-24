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

    /**
     * EasierCrafting (recipe panel) entries in its own JasprBlueprintTable format: id is the
     * Creative-catalogue id ("gear_" + item id), keys carry the registry tag ("glass_pane:0"),
     * the display SNBT and the game's own item name. Built from the same shapes the server
     * registers, through the real item registry, so the panel cannot drift from the recipes.
     */
    static JsonArray recipes() {
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        JsonArray out = new JsonArray();
        for (GearItem item : GearItem.values()) out.add(recipe("gear_" + item.id, item.shape, item.ingredientMap()));
        for (GearConsumable item : GearConsumable.values())
            if (item.shape != null) out.add(recipe("gear_" + item.id, item.shape, item.ingredientMap()));
        for (GearBackpack item : GearBackpack.values()) out.add(recipe("gear_" + item.id, item.shape, item.ingredientMap()));
        return out;
    }

    private static JsonObject recipe(String id, String[] shape, java.util.Map<Character, String> ingredients) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        JsonArray rows = new JsonArray();
        for (String row : shape) rows.add(row.replace(' ', '.'));
        o.add("shape", rows);
        JsonObject keys = new JsonObject();
        for (java.util.Map.Entry<Character, String> e : ingredients.entrySet()) {
            String[] parts = e.getValue().split(":");
            int data = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            net.minecraft.server.v1_12_R1.Item nms = org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers.getItem(org.bukkit.Material.valueOf(parts[0]));
            net.minecraft.server.v1_12_R1.ItemStack stack = new net.minecraft.server.v1_12_R1.ItemStack(nms, 1, data);
            JsonObject k = new JsonObject();
            k.addProperty("tag", net.minecraft.server.v1_12_R1.Item.REGISTRY.b(nms).getKey() + ":" + data);
            k.addProperty("snbt", stack.save(new net.minecraft.server.v1_12_R1.NBTTagCompound()).toString());
            k.addProperty("name", stack.getName());
            keys.add(String.valueOf(e.getKey()), k);
        }
        o.add("keys", keys);
        return o;
    }

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
        JsonArray packs = new JsonArray(); // 3.2.0 backpacks
        for (GearBackpack item : GearBackpack.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id);
            o.addProperty("title", item.title);
            o.addProperty("model", item.model);
            o.addProperty("tier", item.tier);
            o.addProperty("slots", item.slots());
            o.addProperty("lootWeight", item.lootWeight);
            o.addProperty("recipe", GearPlugin.recipeText(item));
            o.addProperty("snbt", GearItems.canonicalTag(item).toString());
            packs.add(o);
        }
        root.add("backpacks", packs);
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
        root.addProperty("wornProtocol", 3);
        JsonArray muts = new JsonArray();
        for (GearMutation m : GearMutation.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("title", m.title);
            o.addProperty("inspiredBy", m.inspiredBy);
            muts.add(o);
        }
        root.add("mutations", muts);
        root.add("recipes", recipes());
        byte[] bytes = root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (args.length > 0) java.nio.file.Files.write(java.nio.file.Paths.get(args[0]), bytes);
        else System.out.write(bytes);
        System.out.flush();
    }
}
