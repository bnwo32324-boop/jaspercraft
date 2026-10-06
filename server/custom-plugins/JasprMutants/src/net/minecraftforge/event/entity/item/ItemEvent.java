package net.minecraftforge.event.entity.item;

import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.event.entity.EntityEvent;

/** JasperCraft port shim of Forge's ItemEvent. */
public class ItemEvent extends EntityEvent {
    private final EntityItem entityItem;

    public ItemEvent(EntityItem itemEntity) {
        super(itemEntity);
        this.entityItem = itemEntity;
    }

    public EntityItem getEntityItem() {
        return this.entityItem;
    }
}
