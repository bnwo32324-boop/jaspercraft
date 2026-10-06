package net.minecraftforge.fml.common.registry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

/**
 * JasperCraft port shim of FML's EntityEntryBuilder. Same builder calls as Forge; entity(...) keeps the builder's own
 * type parameter so the mod's raw-typed helpers in RegistryHandler compile unchanged.
 */
public final class EntityEntryBuilder<E extends Entity> {
    private Class<? extends E> entity;
    private ResourceLocation id;
    private int network = -1;
    private String name;
    private int eggPrimary;
    private int eggSecondary;
    private boolean hasEgg;
    private boolean hasTracker;
    private int trackingRange;
    private int trackingUpdateFrequency;
    private boolean sendsVelocityUpdates;

    private EntityEntryBuilder() {
    }

    public static <E extends Entity> EntityEntryBuilder<E> create() {
        return new EntityEntryBuilder<E>();
    }

    public EntityEntryBuilder<E> entity(Class<? extends E> entity) {
        this.entity = entity;
        return this;
    }

    public EntityEntryBuilder<E> id(ResourceLocation id, int network) {
        this.id = id;
        this.network = network;
        return this;
    }

    public EntityEntryBuilder<E> id(String id, int network) {
        return this.id(new ResourceLocation(id), network);
    }

    public EntityEntryBuilder<E> name(String name) {
        this.name = name;
        return this;
    }

    public EntityEntryBuilder<E> egg(int primary, int secondary) {
        this.eggPrimary = primary;
        this.eggSecondary = secondary;
        this.hasEgg = true;
        return this;
    }

    public EntityEntryBuilder<E> tracker(int range, int updateFrequency, boolean sendVelocityUpdates) {
        this.hasTracker = true;
        this.trackingRange = range;
        this.trackingUpdateFrequency = updateFrequency;
        this.sendsVelocityUpdates = sendVelocityUpdates;
        return this;
    }

    public EntityEntry build() {
        if (this.entity == null || this.id == null || this.name == null) {
            throw new IllegalStateException("EntityEntryBuilder needs entity, id and name");
        }
        EntityEntry entry = new EntityEntry(this.entity, this.name);
        entry.setRegistryName(this.id);
        entry.networkId = this.network;
        entry.hasTracker = this.hasTracker;
        entry.trackingRange = this.trackingRange;
        entry.trackingUpdateFrequency = this.trackingUpdateFrequency;
        entry.sendsVelocityUpdates = this.sendsVelocityUpdates;
        if (this.hasEgg) {
            entry.setEgg(new EntityList.EntityEggInfo(this.id, this.eggPrimary, this.eggSecondary));
        }
        return entry;
    }
}
