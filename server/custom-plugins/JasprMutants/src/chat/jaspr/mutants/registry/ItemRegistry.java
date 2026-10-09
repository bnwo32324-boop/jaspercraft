package chat.jaspr.mutants.registry;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/** Items: Paper's Item registry, numeric id 4000 + registration index (MUTANTS_PROTOCOL.md 1.3). */
public final class ItemRegistry extends ModRegistry<Item> {
    public static final int FIRST_ID = 4000;
    public static final ItemRegistry INSTANCE = new ItemRegistry();

    private ItemRegistry() {
        super(Item.class);
    }

    @Override
    protected ResourceLocation keyOf(Item value) {
        return RegistryNames.get(value);
    }

    @Override
    protected void toVanilla(int index, ResourceLocation key, Item value) {
        int id = FIRST_ID + index;
        if (Item.REGISTRY.getObjectById(id) != null) {
            throw new IllegalStateException("Item id " + id + " is taken by " + Item.REGISTRY.getNameForObject(Item.REGISTRY.getObjectById(id)));
        }
        Item.REGISTRY.register(id, key, value);
    }
}
