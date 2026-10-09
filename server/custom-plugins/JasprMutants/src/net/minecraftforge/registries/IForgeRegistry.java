package net.minecraftforge.registries;

import java.util.Collection;
import java.util.Set;
import net.minecraft.util.ResourceLocation;

/**
 * JasperCraft port shim of Forge's IForgeRegistry. The type bound (V extends IForgeRegistryEntry) is dropped because
 * Paper's registry objects do not implement IForgeRegistryEntry; implementations live in chat.jaspr.mutants.registry.
 */
public interface IForgeRegistry<V> extends Iterable<V> {
    Class<V> getRegistrySuperType();

    void register(V value);

    @SuppressWarnings("unchecked")
    void registerAll(V... values);

    boolean containsKey(ResourceLocation key);

    boolean containsValue(V value);

    V getValue(ResourceLocation key);

    ResourceLocation getKey(V value);

    Set<ResourceLocation> getKeys();

    Collection<V> getValuesCollection();
}
