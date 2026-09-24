package chat.jaspr.recipeexport;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.v1_12_R1.CraftingManager;
import net.minecraft.server.v1_12_R1.CreativeModeTab;
import net.minecraft.server.v1_12_R1.IRecipe;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NonNullList;
import net.minecraft.server.v1_12_R1.RecipeItemStack;
import net.minecraft.server.v1_12_R1.ShapedRecipes;
import net.minecraft.server.v1_12_R1.ShapelessRecipes;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Build-time tool, never deployed: after every plugin has registered its recipes, writes the server's
 * complete crafting-recipe list (vanilla plus every plugin) for the browser client's EasierCrafting panel.
 * Reads NMS CraftingManager directly, because the Bukkit view keeps only one choice per ingredient
 * (a torch from charcoal would be lost). Special recipes (dyeing, fireworks, map cloning, repair...) have
 * no fixed ingredients and are listed as skipped, as in the original mod.
 *
 * Output: {choices:[[{snbt,id,data(-1 = any),name,max}...]...], recipes:[{key,type,w,h,cells|ingredients,result,count,title,tab}]}
 */
public final class RecipeExport extends JavaPlugin {
    private final Map<String, Integer> choiceIndex = new LinkedHashMap<String, Integer>();
    private final JsonArray choices = new JsonArray();

    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, this::export, 40L); // after every plugin's start-up tasks
    }

    private void export() {
        try {
            Field label = CreativeModeTab.class.getDeclaredField("p");
            label.setAccessible(true);
            JsonArray recipes = new JsonArray();
            List<String> skipped = new ArrayList<String>();
            Map<String, IRecipe> sorted = new TreeMap<String, IRecipe>();
            for (IRecipe recipe : CraftingManager.recipes) {
                MinecraftKey key = CraftingManager.recipes.b(recipe);
                sorted.put(String.valueOf(key), recipe);
            }
            for (Map.Entry<String, IRecipe> e : sorted.entrySet()) {
                IRecipe recipe = e.getValue();
                ItemStack result = recipe.b();
                if (result == null || result.isEmpty() || !(recipe instanceof ShapedRecipes || recipe instanceof ShapelessRecipes)) {
                    skipped.add(e.getKey());
                    continue;
                }
                JsonObject o = new JsonObject();
                o.addProperty("key", e.getKey());
                NonNullList<RecipeItemStack> in = recipe.d();
                JsonArray cells = new JsonArray();
                for (RecipeItemStack ingredient : in) cells.add(choice(ingredient));
                if (recipe instanceof ShapedRecipes) {
                    ShapedRecipes shaped = (ShapedRecipes) recipe;
                    o.addProperty("type", "shaped");
                    o.addProperty("w", shaped.f());
                    o.addProperty("h", shaped.g());
                    if (shaped.f() * shaped.g() != in.size()) throw new IllegalStateException("shape " + e.getKey());
                    o.add("cells", cells);
                } else {
                    o.addProperty("type", "shapeless");
                    o.add("ingredients", cells);
                }
                ItemStack one = result.cloneItemStack();
                // Display only: weapons carry a random serial per stack, which would make every export differ.
                o.addProperty("result", one.save(new NBTTagCompound()).toString()
                    .replaceAll("serial:\"[0-9a-f-]{36}\"", "serial:\"00000000-0000-0000-0000-000000000000\""));
                o.addProperty("count", result.getCount());
                o.addProperty("title", result.getName().replaceAll("§.", ""));
                CreativeModeTab tab = result.getItem().b();
                o.addProperty("tab", tab == null ? "" : String.valueOf(label.get(tab)));
                recipes.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            root.add("choices", choices);
            root.add("recipes", recipes);
            JsonArray skip = new JsonArray();
            for (String s : skipped) skip.add(s);
            root.add("skipped", skip);
            File out = new File(getDataFolder(), "recipes.json");
            getDataFolder().mkdirs();
            Files.write(out.toPath(), new GsonBuilder().disableHtmlEscaping().create().toJson(root).getBytes(StandardCharsets.UTF_8));
            getLogger().info("RECIPE_EXPORT recipes=" + recipes.size() + " choices=" + choices.size() + " skipped=" + skipped.size());
        } catch (Exception error) {
            getLogger().severe("RECIPE_EXPORT_FAILED " + error);
            error.printStackTrace();
        }
    }

    /** Index of this ingredient's choice list (-1 = empty cell); wildcard data expands to the item's variants. */
    private int choice(RecipeItemStack ingredient) {
        if (ingredient == null || ingredient.choices.length == 0) return -1;
        JsonArray list = new JsonArray();
        StringBuilder sig = new StringBuilder();
        for (ItemStack stack : ingredient.choices) {
            Item item = stack.getItem();
            String id = String.valueOf(Item.REGISTRY.b(item));
            boolean any = stack.getData() == 32767;
            List<ItemStack> shown = new ArrayList<ItemStack>();
            if (any) {
                NonNullList<ItemStack> subs = NonNullList.a();
                CreativeModeTab tab = item.b();
                if (tab != null) item.a(tab, subs);
                for (ItemStack sub : subs) if (sub.getItem() == item && !sub.hasTag()) shown.add(sub);
                if (shown.isEmpty()) shown.add(new ItemStack(item, 1, 0));
            } else {
                shown.add(stack);
            }
            for (ItemStack s : shown) {
                ItemStack one = s.cloneItemStack();
                one.setCount(1);
                JsonObject c = new JsonObject();
                c.addProperty("snbt", one.save(new NBTTagCompound()).toString());
                c.addProperty("id", id);
                c.addProperty("data", any ? -1 : s.getData());
                c.addProperty("name", one.getName().replaceAll("§.", ""));
                c.addProperty("max", one.getMaxStackSize());
                list.add(c);
                sig.append(id).append(':').append(any ? -1 : s.getData()).append(':').append(one.getData()).append(';');
            }
        }
        Integer known = choiceIndex.get(sig.toString());
        if (known != null) return known;
        choiceIndex.put(sig.toString(), choices.size());
        choices.add(list);
        return choices.size() - 1;
    }
}
