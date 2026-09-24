package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated live-registry validation for the shared gun/exoskeleton blueprint table. */
public final class RecipeProbe extends JavaPlugin {
    private int assertions;
    private boolean run;
    private final List<String> failures = new ArrayList<String>();

    @Override public void onEnable() {
        try {
            File cwd = new File(".").getCanonicalFile();
            if (!Boolean.getBoolean("jaspr.apocalypse.smoke")
                    || !cwd.getParentFile().getName().matches("apocalypse-smoke-[0-9a-f-]{36}"))
                throw new IllegalStateException("Recipe probe requires isolated fixture");
            getLogger().info("APOCALYPSE_SMOKE_ARMED recipe probe");
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || run) return true;
        run = true;
        Bukkit.getScheduler().runTask(this, new Runnable() { @Override public void run() { probe(); } });
        return true;
    }

    private void check(boolean value, String message) {
        assertions++;
        if (!value) failures.add(message);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private void probe() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            ApocalypsePlugin plugin = (ApocalypsePlugin) Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            check(plugin != null && plugin.isEnabled(), "JasprApocalypse did not enable");
            if (plugin != null && plugin.isEnabled()) recipes(plugin);
        } catch (Throwable failure) {
            while (failure.getCause() != null) failure = failure.getCause();
            failures.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            failure.printStackTrace();
        }
        result.put("success", failures.isEmpty());
        result.put("assertions", assertions);
        result.put("failures", failures);
        result.put("blueprints", Blueprints.all().size());
        result.put("firearms", Arsenal.catalogue().size());
        result.put("exoskeletonPieces", ApocalypseItems.catalogue("armor").size());
        getLogger().info("APOCALYPSE_SMOKE_RESULT " + new Gson().toJson(result));
    }

    @SuppressWarnings("unchecked")
    private void recipes(ApocalypsePlugin plugin) throws Exception {
        Arsenal arsenal = (Arsenal) field(plugin, "arsenal");
        Map<NamespacedKey, String> registered = (Map<NamespacedKey, String>) field(arsenal, "recipeIds");
        Map<String, Recipe> live = new HashMap<String, Recipe>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof Keyed)) continue;
            String id = registered.get(((Keyed) recipe).getKey());
            if (id != null) live.put(id, recipe);
        }

        check(Blueprints.all().size() == 89, "Expected 89 blueprints");
        check(Arsenal.catalogue().size() == 35, "Expected 35 firearms");
        check(ApocalypseItems.catalogue("armor").size() == 16, "Expected 16 exoskeleton pieces");
        check(registered.size() == 89, "Expected 89 registered custom recipes, got " + registered.size());
        check(live.size() == 89, "Expected 89 live Bukkit recipes, got " + live.size());

        Method valid = Arsenal.class.getDeclaredMethod("validIngredients", String.class, ItemStack[].class);
        valid.setAccessible(true);
        for (Blueprints.Blueprint blueprint : Blueprints.all().values()) {
            Recipe raw = live.get(blueprint.id);
            check(raw instanceof ShapedRecipe, "Missing shaped recipe " + blueprint.id);
            if (!(raw instanceof ShapedRecipe)) continue;
            ShapedRecipe recipe = (ShapedRecipe) raw;
            check(blueprint.id.equals(ApocalypseItems.id(recipe.getResult())), "Wrong output " + blueprint.id);
            String[] expectedShape = Blueprints.bukkitShape(blueprint);
            String[] actualShape = recipe.getShape();
            check(expectedShape.length == actualShape.length, "Wrong registered height " + blueprint.id);
            if (expectedShape.length == actualShape.length) for (int y = 0; y < expectedShape.length; y++) {
                check(expectedShape[y].length() == actualShape[y].length(), "Wrong registered width " + blueprint.id + "/" + y);
                if (expectedShape[y].length() != actualShape[y].length()) continue;
                for (int x = 0; x < expectedShape[y].length(); x++) {
                    char expected = expectedShape[y].charAt(x);
                    char actual = actualShape[y].charAt(x);
                    ItemStack ingredient = actual == ' ' ? null : recipe.getIngredientMap().get(actual);
                    if (expected == ' ') check(ingredient == null || ingredient.getType() == Material.AIR,
                        "Unexpected ingredient " + blueprint.id + "/" + x + "," + y);
                    else check(ingredient != null && ingredient.getType() == Blueprints.material(expected)
                            && ingredient.getDurability() == Blueprints.data(expected),
                        "Wrong ingredient " + blueprint.id + "/" + x + "," + y);
                }
            }

            ItemStack[] matrix = matrix(blueprint, false);
            check(Boolean.TRUE.equals(valid.invoke(null, blueprint.id, matrix)), "Recipe rejected " + blueprint.id);
            check(Boolean.TRUE.equals(valid.invoke(null, blueprint.id, matrix(blueprint, true))),
                "Mirrored recipe rejected " + blueprint.id);
            int occupied = firstOccupied(matrix);
            check(occupied >= 0, "Empty recipe " + blueprint.id);
            if (occupied >= 0) {
                matrix[occupied] = ApocalypseItems.mark(matrix[occupied], "tagged_substitute");
                check(Boolean.FALSE.equals(valid.invoke(null, blueprint.id, matrix)),
                    "Tagged custom substitute accepted " + blueprint.id);
            }
        }

        verifyExoskeletonCost("bulwark", Material.OBSIDIAN);
        verifyExoskeletonCost("ranger", Material.EMERALD_BLOCK);
        verifyExoskeletonCost("spectre", Material.EYE_OF_ENDER);
        verifyExoskeletonCost("hazmat", Material.SLIME_BLOCK);
        getLogger().info("APOCALYPSE_SMOKE_PHASE gun-and-exoskeleton-recipes "
            + (failures.isEmpty() ? "PASS" : "FAIL") + " assertions=" + assertions);
    }

    private ItemStack[] matrix(Blueprints.Blueprint blueprint, boolean mirror) {
        ItemStack[] matrix = new ItemStack[9];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            int sourceX = mirror ? 2 - x : x;
            char letter = blueprint.shape[y].charAt(sourceX);
            if (letter != '.') matrix[y * 3 + x] = new ItemStack(Blueprints.material(letter), 1, Blueprints.data(letter));
        }
        return matrix;
    }

    private int firstOccupied(ItemStack[] matrix) {
        for (int i = 0; i < matrix.length; i++) if (matrix[i] != null && matrix[i].getType() != Material.AIR) return i;
        return -1;
    }

    private void verifyExoskeletonCost(String set, Material accent) {
        Map<Material, Integer> counts = new HashMap<Material, Integer>();
        int pieces = 0;
        for (Blueprints.Blueprint blueprint : Blueprints.all().values()) {
            if (!blueprint.kind.equals("armor/" + set)) continue;
            pieces++;
            for (String row : blueprint.shape) for (int i = 0; i < row.length(); i++) {
                char letter = row.charAt(i);
                if (letter == '.') continue;
                Material material = Blueprints.material(letter);
                counts.put(material, counts.containsKey(material) ? counts.get(material) + 1 : 1);
            }
        }
        check(pieces == 4, set + " must have four pieces");
        check(Integer.valueOf(8).equals(counts.get(Material.DIAMOND_BLOCK)), set + " diamond-block cost");
        check(Integer.valueOf(8).equals(counts.get(Material.IRON_BLOCK)), set + " iron-block cost");
        check(Integer.valueOf(6).equals(counts.get(Material.GOLD_BLOCK)), set + " gold-block cost");
        check(Integer.valueOf(4).equals(counts.get(Material.NETHER_STAR)), set + " Nether-Star cost");
        check(Integer.valueOf(4).equals(counts.get(accent)), set + " specialized-core cost");
    }
}
