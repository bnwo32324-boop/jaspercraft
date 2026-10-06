package net.minecraftforge.event.entity.item;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

/** JasperCraft port shim of Forge's ItemTossEvent (fed from Bukkit's PlayerDropItemEvent). */
@Cancelable
public class ItemTossEvent extends ItemEvent {
    private final EntityPlayer player;

    public ItemTossEvent(EntityItem entityItem, EntityPlayer player) {
        super(entityItem);
        this.player = player;
    }

    public EntityPlayer getPlayer() {
        return this.player;
    }
}
