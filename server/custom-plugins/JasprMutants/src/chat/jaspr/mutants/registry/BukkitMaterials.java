package chat.jaspr.mutants.registry;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;

/**
 * Bukkit Material constants for the mod's items (MUTANTBEASTS_&lt;NAME&gt;, id 4000-4014), so CraftBukkit and other plugins
 * see a real type for them: without one, CraftMagicNumbers reports AIR, and CraftBukkit drops "AIR" stacks from death
 * drops, player death drops, inventories and crafting results - the items would vanish. This is what Forge-Bukkit
 * hybrid servers do for mod items. The constants are added like EnumHelper adds enum constants; switch tables in the
 * server jar are widened and the JDK enum cache is refreshed (see EnumInjector).
 */
public final class BukkitMaterials {
    private static final List<Material> ADDED = new ArrayList<Material>();

    private BukkitMaterials() {
    }

    public static List<Material> added() {
        return ADDED;
    }

    public static String nameFor(ResourceLocation key) {
        return (key.getNamespace() + "_" + key.getPath()).toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    public static void register(List<Item> items) {
        try {
            Field byIdField = Material.class.getDeclaredField("byId");
            byIdField.setAccessible(true);
            Field byNameField = Material.class.getDeclaredField("BY_NAME");
            byNameField.setAccessible(true);
            Map<String, Material> byName = (Map<String, Material>) byNameField.get(null);
            for (Item item : items) {
                int id = Item.getIdFromItem(item);
                ResourceLocation key = Item.REGISTRY.getNameForObject(item);
                String name = nameFor(key);
                Material existing = Material.getMaterial(id);
                if (existing != null) {
                    if (existing.name().equals(name)) {
                        ADDED.add(existing);
                        continue;
                    }
                    throw new IllegalStateException("Material id " + id + " is already " + existing);
                }
                Material m = EnumInjector.addEnum(Material.class, name,
                        new Class<?>[]{int.class, int.class, int.class, Class.class},
                        new Object[]{id, item.getItemStackLimit(), item.getMaxDamage(), MaterialData.class});
                Material[] byId = (Material[]) byIdField.get(null);
                if (byId.length <= id) {
                    Material[] grown = new Material[id + 2];
                    System.arraycopy(byId, 0, grown, 0, byId.length);
                    byId = grown;
                    byIdField.set(null, byId);
                }
                byId[id] = m;
                byName.put(m.name(), m);
                ADDED.add(m);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot add Bukkit materials", e);
        }
    }

    /** Widens the Material switch tables of the server jar (classes initialised before the constants were added). */
    public static int widenSwitchTables() {
        try {
            File jar = new File(Material.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return EnumInjector.widenSwitchMaps(Material.class, jar, Material.class.getClassLoader());
        } catch (Exception e) {
            EnumInjector.report().add("switch-map-jar-unknown " + e.getClass().getSimpleName());
            return 0;
        }
    }
}
