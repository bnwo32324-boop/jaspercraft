package net.minecraftforge.event.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

/** JasperCraft port shim of Forge's ArrowLooseEvent (fed from Paper's bow release, see chat.jaspr.mutants.hooks.BowHooks). */
@Cancelable
public class ArrowLooseEvent extends PlayerEvent {
    private final ItemStack bow;
    private final World world;
    private final boolean hasAmmo;
    private int charge;

    public ArrowLooseEvent(EntityPlayer player, ItemStack bow, World world, int charge, boolean hasAmmo) {
        super(player);
        this.bow = bow;
        this.world = world;
        this.charge = charge;
        this.hasAmmo = hasAmmo;
    }

    public ItemStack getBow() {
        return this.bow;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean hasAmmo() {
        return this.hasAmmo;
    }

    public int getCharge() {
        return this.charge;
    }

    public void setCharge(int charge) {
        this.charge = charge;
    }
}
