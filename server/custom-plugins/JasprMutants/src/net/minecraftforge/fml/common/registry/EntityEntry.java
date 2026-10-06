package net.minecraftforge.fml.common.registry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * JasperCraft port shim of FML's EntityEntry, carrying what EntityEntryBuilder collected (network id, translation
 * name, egg colours and tracker settings) for chat.jaspr.mutants.registry.EntityRegistry.
 */
public class EntityEntry implements IForgeRegistryEntry<EntityEntry> {
    private final Class<? extends Entity> cls;
    private final String name;
    private EntityList.EntityEggInfo egg;
    private ResourceLocation registryName;
    int networkId = -1;
    boolean hasTracker;
    int trackingRange;
    int trackingUpdateFrequency;
    boolean sendsVelocityUpdates;

    public EntityEntry(Class<? extends Entity> cls, String name) {
        this.cls = cls;
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public Class<? extends Entity> getEntityClass() {
        return this.cls;
    }

    public EntityList.EntityEggInfo getEgg() {
        return this.egg;
    }

    public void setEgg(EntityList.EntityEggInfo egg) {
        this.egg = egg;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public boolean hasTracker() {
        return this.hasTracker;
    }

    public int getTrackingRange() {
        return this.trackingRange;
    }

    public int getTrackingUpdateFrequency() {
        return this.trackingUpdateFrequency;
    }

    public boolean sendsVelocityUpdates() {
        return this.sendsVelocityUpdates;
    }

    @Override
    public EntityEntry setRegistryName(ResourceLocation name) {
        this.registryName = name;
        return this;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return this.registryName;
    }

    @Override
    public Class<EntityEntry> getRegistryType() {
        return EntityEntry.class;
    }
}
