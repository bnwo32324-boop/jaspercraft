package chat.jaspr.mutants.registry;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import org.bukkit.entity.EntityType;

/**
 * Entities: Paper's EntityTypes registry with network type id 210 + the mod's registration index and key
 * mutantbeasts:&lt;name&gt; (MUTANTS_PROTOCOL.md 1.1), the translation name of the EntityEntry (entity.&lt;name&gt;.name keys),
 * spawn egg colours for the living ones (vanilla spawn_egg with EntityTag.id works for them), and Paper's class caches
 * (clsToTypeMap gets UNKNOWN: Bukkit has no EntityType for mod entities, and Paper's natural spawner needs a value).
 */
public final class EntityRegistry extends ModRegistry<EntityEntry> {
    public static final int FIRST_ID = 210;
    public static final EntityRegistry INSTANCE = new EntityRegistry();
    private static final Map<Class<? extends Entity>, EntityEntry> BY_CLASS = new HashMap<Class<? extends Entity>, EntityEntry>();
    private static final Map<String, EntityEntry> BY_PATH = new HashMap<String, EntityEntry>();

    private EntityRegistry() {
        super(EntityEntry.class);
    }

    @Override
    protected ResourceLocation keyOf(EntityEntry value) {
        return value.getRegistryName();
    }

    @Override
    protected void toVanilla(int index, ResourceLocation key, EntityEntry entry) {
        int id = FIRST_ID + entry.getNetworkId();
        if (entry.getNetworkId() != index) {
            throw new IllegalStateException("Entity " + key + " has network id " + entry.getNetworkId() + " but registration index " + index);
        }
        if (EntityList.REGISTRY.getObjectById(id) != null) {
            throw new IllegalStateException("Entity type id " + id + " is taken by " + EntityList.REGISTRY.getObjectById(id).getName());
        }
        try {
            // Paper's EntityTypes.a(int, String, Class, String): registry, known-types set, Paper's class caches and
            // the old-name (translation) list, exactly as vanilla registers its own entities.
            Method register = EntityList.class.getDeclaredMethod("a", int.class, String.class, Class.class, String.class);
            register.setAccessible(true);
            register.invoke(null, id, key.toString(), entry.getEntityClass(), entry.getName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register entity " + key, e);
        }
        EntityList.clsToTypeMap.put(entry.getEntityClass(), EntityType.UNKNOWN);
        if (entry.getEgg() != null) {
            EntityList.ENTITY_EGGS.put(key, entry.getEgg());
        }
        BY_CLASS.put(entry.getEntityClass(), entry);
        BY_PATH.put(key.getPath(), entry);
    }

    public static EntityEntry entryOf(Class<?> cls) {
        return BY_CLASS.get(cls);
    }

    public static EntityEntry entryOf(String path) {
        return BY_PATH.get(path);
    }

    /** Protocol type id of a mod entity class, or -1. */
    public static int networkTypeId(Class<?> cls) {
        EntityEntry e = BY_CLASS.get(cls);
        return e == null ? -1 : FIRST_ID + e.getNetworkId();
    }

    public static boolean isModEntity(Entity entity) {
        return entity != null && BY_CLASS.containsKey(entity.getClass());
    }
}
