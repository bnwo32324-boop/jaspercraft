package net.minecraftforge.common.brewing;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;

/** JasperCraft port shim of Forge's AbstractBrewingRecipe (Forge 1.12.2 logic). */
public abstract class AbstractBrewingRecipe<T> implements IBrewingRecipe {
    @Nonnull
    private final ItemStack input;
    @Nonnull
    private final T ingredient;
    private final ItemStack output;

    protected AbstractBrewingRecipe(@Nonnull ItemStack input, @Nonnull T ingredient, @Nonnull ItemStack output) {
        this.input = input;
        this.ingredient = ingredient;
        this.output = output;
        if (this.getInput() == null || this.getIngredient() == null || this.getOutput() == null) {
            throw new IllegalArgumentException("A brewing recipe cannot have a null parameter.");
        }
    }

    @Override
    public boolean isInput(@Nonnull ItemStack stack) {
        return OreDictionaryShim.itemMatches(this.getInput(), stack, false);
    }

    @Override
    @Nonnull
    public ItemStack getOutput(@Nonnull ItemStack input, @Nonnull ItemStack ingredient) {
        return this.isInput(input) && this.isIngredient(ingredient) ? this.getOutput().copy() : ItemStack.EMPTY;
    }

    @Nonnull
    public ItemStack getInput() {
        return this.input;
    }

    @Nonnull
    public T getIngredient() {
        return this.ingredient;
    }

    @Nonnull
    public ItemStack getOutput() {
        return this.output;
    }

    /** OreDictionary.itemMatches (Forge 1.12.2), the only OreDictionary logic brewing needs. */
    static final class OreDictionaryShim {
        static final int WILDCARD_VALUE = Short.MAX_VALUE;

        static boolean itemMatches(@Nonnull ItemStack target, @Nonnull ItemStack input, boolean strict) {
            if (input.isEmpty() && !target.isEmpty() || !input.isEmpty() && target.isEmpty()) {
                return false;
            }
            return target.getItem() == input.getItem() && (target.getMetadata() == WILDCARD_VALUE && !strict || target.getMetadata() == input.getMetadata());
        }
    }
}
