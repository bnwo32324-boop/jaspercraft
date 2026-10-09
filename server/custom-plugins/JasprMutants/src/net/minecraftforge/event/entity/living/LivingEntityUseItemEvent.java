package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

/** JasperCraft port shim of Forge's LivingEntityUseItemEvent (Tick is fed by chat.jaspr.mutants.hooks.TickHooks). */
public class LivingEntityUseItemEvent extends LivingEvent {
    private final ItemStack item;
    private int duration;

    private LivingEntityUseItemEvent(EntityLivingBase entity, ItemStack item, int duration) {
        super(entity);
        this.item = item;
        this.setDuration(duration);
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Cancelable
    public static class Tick extends LivingEntityUseItemEvent {
        public Tick(EntityLivingBase entity, ItemStack item, int duration) {
            super(entity, item, duration);
        }
    }
}
