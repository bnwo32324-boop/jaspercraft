package net.minecraftforge.common.brewing;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;

/** JasperCraft port shim of Forge's IBrewingRecipe. */
public interface IBrewingRecipe {
    boolean isInput(@Nonnull ItemStack input);

    boolean isIngredient(@Nonnull ItemStack ingredient);

    @Nonnull
    ItemStack getOutput(@Nonnull ItemStack input, @Nonnull ItemStack ingredient);
}
