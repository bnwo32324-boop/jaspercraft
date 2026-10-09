package net.minecraftforge.event.entity.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.relauncher.Side;

/** JasperCraft port shim of Forge's PlayerInteractEvent (EntityInteract is fed from Bukkit's PlayerInteractEntityEvent). */
public class PlayerInteractEvent extends PlayerEvent {
    private final EnumHand hand;
    private final BlockPos pos;
    private final EnumFacing face;
    private EnumActionResult cancellationResult = EnumActionResult.PASS;

    private PlayerInteractEvent(EntityPlayer player, EnumHand hand, BlockPos pos, EnumFacing face) {
        super(player);
        this.hand = hand;
        this.pos = pos;
        this.face = face;
    }

    public EnumHand getHand() {
        return this.hand;
    }

    public ItemStack getItemStack() {
        return this.getEntityPlayer().getHeldItem(this.hand);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public EnumFacing getFace() {
        return this.face;
    }

    public World getWorld() {
        return this.getEntityPlayer().getEntityWorld();
    }

    public Side getSide() {
        return this.getWorld().isRemote ? Side.CLIENT : Side.SERVER;
    }

    public EnumActionResult getCancellationResult() {
        return this.cancellationResult;
    }

    public void setCancellationResult(EnumActionResult result) {
        this.cancellationResult = result;
    }

    @Cancelable
    public static class EntityInteract extends PlayerInteractEvent {
        private final Entity target;

        public EntityInteract(EntityPlayer player, EnumHand hand, Entity target) {
            super(player, hand, new BlockPos(target), null);
            this.target = target;
        }

        public Entity getTarget() {
            return this.target;
        }
    }
}
