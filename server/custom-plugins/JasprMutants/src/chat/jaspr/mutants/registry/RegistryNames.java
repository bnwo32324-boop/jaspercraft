package chat.jaspr.mutants.registry;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

/**
 * Registry names of objects whose Paper classes lack Forge's IForgeRegistryEntry (Item, SoundEvent): what Forge keeps
 * in each object's registryName field until the object is registered.
 */
public final class RegistryNames {
    private static final Map<Object, ResourceLocation> NAMES = new IdentityHashMap<Object, ResourceLocation>();

    private RegistryNames() {
    }

    public static synchronized <T> T set(T object, ResourceLocation name) {
        if (NAMES.containsKey(object)) {
            throw new IllegalStateException("Attempted to set registry name with existing registry name! New: " + name + " Old: " + NAMES.get(object));
        }
        NAMES.put(object, name);
        return object;
    }

    public static synchronized ResourceLocation get(Object object) {
        return NAMES.get(object);
    }
}
