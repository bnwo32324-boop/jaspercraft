package net.minecraftforge.fml.common.registry;

import net.minecraft.entity.Entity;

/** JasperCraft port shim of FML's IThrowableEntity (its thrower is sent in the SPAWN message, as FML does). */
public interface IThrowableEntity {
    Entity getThrower();

    void setThrower(Entity entity);
}
