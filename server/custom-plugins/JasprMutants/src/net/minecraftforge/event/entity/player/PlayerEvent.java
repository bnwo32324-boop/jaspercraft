package net.minecraftforge.event.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;

/** JasperCraft port shim of Forge's PlayerEvent. */
public class PlayerEvent extends LivingEvent {
    private final EntityPlayer entityPlayer;

    public PlayerEvent(EntityPlayer player) {
        super(player);
        this.entityPlayer = player;
    }

    public EntityPlayer getEntityPlayer() {
        return this.entityPlayer;
    }
}
