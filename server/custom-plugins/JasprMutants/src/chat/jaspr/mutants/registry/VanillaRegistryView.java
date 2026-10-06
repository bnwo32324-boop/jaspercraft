package chat.jaspr.mutants.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraftforge.registries.IForgeRegistry;

/** Read-only IForgeRegistry view of one of Paper's vanilla registries (ForgeRegistries.BIOMES). */
public final class VanillaRegistryView<V> implements IForgeRegistry<V> {
    private final Class<V> type;
    private final RegistryNamespaced<ResourceLocation, V> registry;

    public VanillaRegistryView(Class<V> type, RegistryNamespaced<ResourceLocation, V> registry) {
        this.type = type;
        this.registry = registry;
    }

    @Override
    public Class<V> getRegistrySuperType() {
        return this.type;
    }

    @Override
    public void register(V value) {
        throw new UnsupportedOperationException("read-only view of a vanilla registry");
    }

    @SafeVarargs
    @Override
    public final void registerAll(V... values) {
        throw new UnsupportedOperationException("read-only view of a vanilla registry");
    }

    @Override
    public boolean containsKey(ResourceLocation key) {
        return this.registry.containsKey(key);
    }

    @Override
    public boolean containsValue(V value) {
        return this.registry.getNameForObject(value) != null;
    }

    @Override
    public V getValue(ResourceLocation key) {
        return this.registry.getObject(key);
    }

    @Override
    public ResourceLocation getKey(V value) {
        return this.registry.getNameForObject(value);
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        return new LinkedHashSet<ResourceLocation>(this.registry.getKeys());
    }

    @Override
    public Collection<V> getValuesCollection() {
        List<V> list = new ArrayList<V>();
        for (V v : this.registry) list.add(v);
        return list;
    }

    @Override
    public Iterator<V> iterator() {
        return this.getValuesCollection().iterator();
    }
}
