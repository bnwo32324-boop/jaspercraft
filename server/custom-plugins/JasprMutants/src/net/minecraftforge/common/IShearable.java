package net.minecraftforge.common;

import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * JasperCraft port shim of Forge's IShearable. Forge's ItemShears.itemInteractionForEntity drives it; Paper's shears
 * have no such code, so chat.jaspr.mutants.hooks.InteractHooks runs Forge's shearing logic for IShearable mod entities.
 */
public interface IShearable {
    boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos);

    List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune);
}
