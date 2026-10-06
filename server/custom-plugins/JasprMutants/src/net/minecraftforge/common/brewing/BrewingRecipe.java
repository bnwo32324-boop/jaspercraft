package net.minecraftforge.common.brewing;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;

/** JasperCraft port shim of Forge's BrewingRecipe (Forge 1.12.2 logic). */
public class BrewingRecipe extends AbstractBrewingRecipe<ItemStack> {
    public BrewingRecipe(@Nonnull ItemStack input, @Nonnull ItemStack ingredient, @Nonnull ItemStack output) {
        super(input, ingredient, output);
    }

    @Override
    public boolean isIngredient(@Nonnull ItemStack stack) {
        return OreDictionaryShim.itemMatches(this.getIngredient(), stack, false);
    }
}
