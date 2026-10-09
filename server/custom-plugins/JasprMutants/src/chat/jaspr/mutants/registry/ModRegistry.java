package chat.jaspr.mutants.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * A Forge registry for the mod's objects of one type, writing each registered object into Paper's NMS registry with
 * the fixed id of MUTANTS_PROTOCOL.md (Forge assigns ids at runtime and syncs them to clients; here both sides use
 * the protocol's table). Objects keep their registration order, which is the protocol's order.
 */
public abstract class ModRegistry<V> implements IForgeRegistry<V> {
    private final Class<V> type;
    private final Map<ResourceLocation, V> byKey = new LinkedHashMap<ResourceLocation, V>();
    private final List<V> ordered = new ArrayList<V>();

    protected ModRegistry(Class<V> type) {
        this.type = type;
    }

    @Override
    public Class<V> getRegistrySuperType() {
        return this.type;
    }

    @Override
    public void register(V value) {
        ResourceLocation key = this.keyOf(value);
        if (key == null) throw new IllegalArgumentException("Registry entry without a registry name: " + value);
        if (this.byKey.containsKey(key)) throw new IllegalArgumentException("Duplicate registry name " + key);
        this.toVanilla(this.ordered.size(), key, value);
        this.byKey.put(key, value);
        this.ordered.add(value);
    }

    @SafeVarargs
    @Override
    public final void registerAll(V... values) {
        for (V v : values) this.register(v);
    }

    /** The registry name Forge would read from the object (IForgeRegistryEntry.getRegistryName). */
    protected abstract ResourceLocation keyOf(V value);

    /** Puts the object into Paper's registry; index is its position in registration order. */
    protected abstract void toVanilla(int index, ResourceLocation key, V value);

    @Override
    public boolean containsKey(ResourceLocation key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public boolean containsValue(V value) {
        return this.ordered.contains(value);
    }

    @Override
    public V getValue(ResourceLocation key) {
        return this.byKey.get(key);
    }

    @Override
    public ResourceLocation getKey(V value) {
        for (Map.Entry<ResourceLocation, V> e : this.byKey.entrySet()) {
            if (e.getValue() == value) return e.getKey();
        }
        return null;
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Collection<V> getValuesCollection() {
        return Collections.unmodifiableList(this.ordered);
    }

    @Override
    public Iterator<V> iterator() {
        return this.getValuesCollection().iterator();
    }

    public List<V> ordered() {
        return Collections.unmodifiableList(this.ordered);
    }
}
