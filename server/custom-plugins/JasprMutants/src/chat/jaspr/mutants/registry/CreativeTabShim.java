package chat.jaspr.mutants.registry;

import java.lang.reflect.Field;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/**
 * The server-side object behind MutantBeasts.CREATIVE_TAB. Creative tabs only matter to clients; Paper's CreativeTabs
 * constructor would write into a fixed 12-slot array, so this tab is created without it and is not listed there.
 */
public final class CreativeTabShim {
    private CreativeTabShim() {
    }

    /** A tab with Forge's next index (12) and the mod's label, not registered in CREATIVE_TAB_ARRAY. */
    public static CreativeTabs create(String label) {
        try {
            ModTab tab = (ModTab) UnsafeAccess.allocateInstance(ModTab.class);
            // Spigot names of CreativeTabs.index / tabLabel / icon / enchantmentTypes (names-1.12.2.tsv)
            set(tab, "o", CreativeTabs.CREATIVE_TAB_ARRAY.length);
            set(tab, "p", label);
            setIfPresent(tab, ItemStack.class, ItemStack.EMPTY);
            setIfPresent(tab, EnumEnchantmentType[].class, new EnumEnchantmentType[0]);
            return tab;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create the creative tab object", e);
        }
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = CreativeTabs.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setIfPresent(Object target, Class<?> type, Object value) throws ReflectiveOperationException {
        for (Field f : CreativeTabs.class.getDeclaredFields()) {
            if (f.getType() == type && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                f.set(target, value);
            }
        }
    }

    /** Concrete subclass; its constructor never runs (the instance is allocated, see create). */
    static final class ModTab extends CreativeTabs {
        private ModTab() {
            super(0, "unused");
        }
    }
}
