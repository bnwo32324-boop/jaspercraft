package net.minecraftforge.common.brewing;

import chat.jaspr.mutants.brewing.Brewing;
import javax.annotation.Nonnull;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionHelper;

/**
 * JasperCraft port shim of Forge's VanillaBrewingRecipe. The brewing stand must accept the mod's ingredients; on Paper
 * that needs a placeholder mix in PotionHelper (chat.jaspr.mutants.brewing.Brewing), which this recipe ignores so the
 * vanilla part answers exactly as on Forge.
 */
public class VanillaBrewingRecipe implements IBrewingRecipe {
    @Override
    public boolean isInput(@Nonnull ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.POTIONITEM || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.GLASS_BOTTLE;
    }

    @Override
    public boolean isIngredient(@Nonnull ItemStack stack) {
        return PotionHelper.isReagent(stack) && !Brewing.isPlaceholderReagent(stack);
    }

    @Override
    @Nonnull
    public ItemStack getOutput(@Nonnull ItemStack input, @Nonnull ItemStack ingredient) {
        if (!input.isEmpty() && !ingredient.isEmpty() && this.isIngredient(ingredient)) {
            ItemStack result = PotionHelper.doReaction(ingredient, input);
            if (result != input) {
                return result;
            }
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
