package chat.jaspr.mutants.registry;

import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

/**
 * The Forge recipe registry handed to RegistryEvent.Register&lt;IRecipe&gt;: recipes go into Paper's CraftingManager under
 * their registry name (the mod registers none there itself; its crafting recipes are JSON, see Recipes).
 */
public final class RecipeRegistry extends ModRegistry<IRecipe> {
    public static final RecipeRegistry INSTANCE = new RecipeRegistry();

    private RecipeRegistry() {
        super(IRecipe.class);
    }

    @Override
    protected ResourceLocation keyOf(IRecipe value) {
        return RegistryNames.get(value);
    }

    @Override
    protected void toVanilla(int index, ResourceLocation key, IRecipe value) {
        CraftingManager.register(key, value);
    }
}
