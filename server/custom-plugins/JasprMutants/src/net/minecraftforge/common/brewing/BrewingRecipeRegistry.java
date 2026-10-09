package net.minecraftforge.common.brewing;

import chat.jaspr.mutants.brewing.Brewing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * JasperCraft port shim of Forge's BrewingRecipeRegistry (Forge 1.12.2 logic). Paper's brewing stand does not consult
 * it; chat.jaspr.mutants.brewing.Brewing makes the stand start and finish brews with these methods.
 */
public class BrewingRecipeRegistry {
    private static final List<IBrewingRecipe> recipes = new ArrayList<IBrewingRecipe>();

    static {
        addRecipe(new VanillaBrewingRecipe());
    }

    public static boolean addRecipe(@Nonnull ItemStack input, @Nonnull ItemStack ingredient, @Nonnull ItemStack output) {
        return addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public static boolean addRecipe(IBrewingRecipe recipe) {
        boolean added = recipes.add(recipe);
        if (added) Brewing.recipeAdded(recipe);
        return added;
    }

    @Nonnull
    public static ItemStack getOutput(@Nonnull ItemStack input, @Nonnull ItemStack ingredient) {
        if (input.isEmpty() || input.getCount() != 1) return ItemStack.EMPTY;
        if (ingredient.isEmpty()) return ItemStack.EMPTY;
        for (IBrewingRecipe recipe : recipes) {
            ItemStack output = recipe.getOutput(input, ingredient);
            if (!output.isEmpty()) {
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasOutput(@Nonnull ItemStack input, @Nonnull ItemStack ingredient) {
        return !getOutput(input, ingredient).isEmpty();
    }

    public static boolean canBrew(NonNullList<ItemStack> inputs, @Nonnull ItemStack ingredient, int[] inputIndexes) {
        if (ingredient.isEmpty()) return false;
        for (int i : inputIndexes) {
            if (hasOutput(inputs.get(i), ingredient)) {
                return true;
            }
        }
        return false;
    }

    public static void brewPotions(NonNullList<ItemStack> inputs, @Nonnull ItemStack ingredient, int[] inputIndexes) {
        for (int i : inputIndexes) {
            ItemStack output = getOutput(inputs.get(i), ingredient);
            if (!output.isEmpty()) {
                inputs.set(i, output);
            }
        }
    }

    public static boolean isValidIngredient(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isIngredient(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidInput(@Nonnull ItemStack stack) {
        if (stack.getCount() != 1) return false;
        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isInput(stack)) {
                return true;
            }
        }
        return false;
    }

    public static List<IBrewingRecipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }
}
