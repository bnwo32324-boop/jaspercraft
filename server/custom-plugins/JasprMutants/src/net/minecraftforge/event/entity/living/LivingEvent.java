package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityEvent;

/** JasperCraft port shim of Forge's LivingEvent. */
public class LivingEvent extends EntityEvent {
    private final EntityLivingBase entityLiving;

    public LivingEvent(EntityLivingBase entity) {
        super(entity);
        this.entityLiving = entity;
    }

    public EntityLivingBase getEntityLiving() {
        return this.entityLiving;
    }
}
