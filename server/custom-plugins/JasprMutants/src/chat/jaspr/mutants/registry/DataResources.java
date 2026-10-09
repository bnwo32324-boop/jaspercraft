package chat.jaspr.mutants.registry;

import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;

/**
 * The mod's JSON data, loaded where Forge loads it on a dedicated server:
 * - crafting recipes (assets/mutantbeasts/recipes, Forge's CraftingHelper): vanilla CraftingManager.parseRecipeJson,
 *   registered as mutantbeasts:&lt;file&gt;. forge:ore_shapeless with plain item ingredients matches exactly like
 *   minecraft:crafting_shapeless, so it is parsed as that (no ore dictionary entries are involved);
 * - loot tables (assets/mutantbeasts/loot_tables): Forge finds them on the mod's class path; Paper's LootTableManager
 *   only looks in the server jar and the world folder, so the parsed tables are put into each world's cache;
 * - advancements (assets/mutantbeasts/advancements): Forge's AdvancementManager reads them from the mod jar at load;
 *   here they are added to the advancement list after the main world has loaded it.
 */
public final class DataResources {
    private static final String ROOT = "assets/mutantbeasts/";
    private static Logger log = Logger.getLogger("JasprMutants");
    private static final Map<ResourceLocation, LootTable> LOOT = new LinkedHashMap<ResourceLocation, LootTable>();
    private static final Map<ResourceLocation, JsonObject> ADVANCEMENTS = new LinkedHashMap<ResourceLocation, JsonObject>();
    private static final List<String> REPORT = new ArrayList<String>();
    private static int recipes;
    private static long lootInjections;
    private static int advancementLoads;

    private DataResources() {
    }

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static int recipeCount() {
        return recipes;
    }

    public static int lootTableCount() {
        return LOOT.size();
    }

    public static int advancementCount() {
        return ADVANCEMENTS.size();
    }

    public static List<String> report() {
        return REPORT;
    }

    /** Reads every JSON of the plugin jar under assets/mutantbeasts/&lt;kind&gt;/ (path without .json -> object). */
    private static Map<String, JsonObject> read(File jar, String kind) throws IOException {
        Map<String, JsonObject> out = new LinkedHashMap<String, JsonObject>();
        String prefix = ROOT + kind + "/";
        try (JarFile jf = new JarFile(jar)) {
            List<String> names = new ArrayList<String>();
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.startsWith(prefix) && n.endsWith(".json")) names.add(n);
            }
            java.util.Collections.sort(names);
            for (String n : names) {
                try (InputStream in = jf.getInputStream(jf.getJarEntry(n)); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    out.put(n.substring(prefix.length(), n.length() - 5), new JsonParser().parse(r).getAsJsonObject());
                }
            }
        }
        return out;
    }

    public static void load(File jar) throws IOException, ReflectiveOperationException {
        // recipes
        Method parse = null;
        for (Method m : CraftingManager.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == JsonObject.class && IRecipe.class.isAssignableFrom(m.getReturnType())) {
                parse = m;
            }
        }
        if (parse == null) throw new NoSuchMethodException("CraftingManager.parseRecipeJson");
        parse.setAccessible(true);
        for (Map.Entry<String, JsonObject> e : read(jar, "recipes").entrySet()) {
            JsonObject json = e.getValue();
            // Forge's CraftingHelper takes namespaced types; vanilla 1.12's parseRecipeJson takes "crafting_shaped"/"crafting_shapeless"
            String type = json.get("type").getAsString();
            if ("forge:ore_shapeless".equals(type) || "minecraft:crafting_shapeless".equals(type)) json.addProperty("type", "crafting_shapeless");
            else if ("forge:ore_shaped".equals(type) || "minecraft:crafting_shaped".equals(type)) json.addProperty("type", "crafting_shaped");
            ResourceLocation key = new ResourceLocation("mutantbeasts", e.getKey());
            if (CraftingManager.getRecipe(key) != null) continue;
            try {
                IRecipe recipe = (IRecipe) parse.invoke(null, json);
                CraftingManager.register(key, recipe);
                recipes++;
            } catch (java.lang.reflect.InvocationTargetException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                log.warning("MUTANTS_RECIPE_FAILED recipe=" + key + " reason=" + cause.getClass().getSimpleName() + " " + String.valueOf(cause.getMessage()).replaceAll("[\\r\\n]", " "));
            }
        }
        // loot tables
        Field gsonField = LootTableManager.class.getDeclaredField("b"); // Spigot name of LootTableManager.GSON_INSTANCE
        gsonField.setAccessible(true);
        Gson gson = (Gson) gsonField.get(null);
        for (Map.Entry<String, JsonObject> e : read(jar, "loot_tables").entrySet()) {
            LOOT.put(new ResourceLocation("mutantbeasts", e.getKey()), gson.fromJson(e.getValue(), LootTable.class));
        }
        // advancements (added later, see loadAdvancements)
        for (Map.Entry<String, JsonObject> e : read(jar, "advancements").entrySet()) {
            ADVANCEMENTS.put(new ResourceLocation("mutantbeasts", e.getKey()), e.getValue());
        }
    }

    /** Puts the mod's loot tables into this world's LootTableManager cache unless they are there already. */
    @SuppressWarnings("unchecked")
    public static int injectLootTables(WorldServer world) {
        int put = 0;
        try {
            LootTableManager manager = world.getLootTableManager();
            Field cacheField = LootTableManager.class.getDeclaredField("c"); // Spigot name of registeredLootTables
            cacheField.setAccessible(true);
            LoadingCache<ResourceLocation, LootTable> cache = (LoadingCache<ResourceLocation, LootTable>) cacheField.get(manager);
            for (Map.Entry<ResourceLocation, LootTable> e : LOOT.entrySet()) {
                if (cache.getIfPresent(e.getKey()) != e.getValue()) {
                    cache.put(e.getKey(), e.getValue());
                    put++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("MUTANTS_LOOT_INJECT_FAILED reason=" + e.getClass().getSimpleName());
        }
        lootInjections += put;
        return put;
    }

    /** Adds the mod's advancements to the server's advancement list if the root is missing (after load or /reload). */
    public static boolean loadAdvancements(Iterable<EntityPlayerMP> online) {
        try {
            if (ADVANCEMENTS.isEmpty() || AdvancementManager.ADVANCEMENT_LIST.getAdvancement(new ResourceLocation("mutantbeasts", "root")) != null) return false;
            Field gsonField = AdvancementManager.class.getDeclaredField("DESERIALIZER"); // Spigot name of AdvancementManager.GSON
            gsonField.setAccessible(true);
            Gson gson = (Gson) gsonField.get(null);
            Map<ResourceLocation, Advancement.Builder> builders = new LinkedHashMap<ResourceLocation, Advancement.Builder>();
            for (Map.Entry<ResourceLocation, JsonObject> e : ADVANCEMENTS.entrySet()) {
                builders.put(e.getKey(), gson.fromJson(e.getValue(), Advancement.Builder.class));
            }
            AdvancementManager.ADVANCEMENT_LIST.loadAdvancements(builders);
            for (EntityPlayerMP p : online) p.getAdvancements().reload();
            advancementLoads++;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("MUTANTS_ADVANCEMENTS_FAILED reason=" + e.getClass().getSimpleName());
            return false;
        }
    }

    public static long lootInjectionCount() {
        return lootInjections;
    }

    public static int advancementLoadCount() {
        return advancementLoads;
    }
}
