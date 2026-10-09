package chat.jaspr.mutants.brewing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.potion.PotionHelper;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;

/**
 * Forge's BrewingRecipeRegistry on Paper's brewing stand.
 *
 * Forge's TileEntityBrewingStand asks BrewingRecipeRegistry whether it can brew and what each slot becomes. Paper's
 * asks PotionHelper. So that a stand starts a mod brew at the same moment Forge's would, each mod recipe's ingredient
 * gets a placeholder mix in PotionHelper (input potion type to itself: it makes the stand accept the ingredient and
 * start, it never decides an output). When the brew completes, Paper fires BrewEvent right where Forge's brewPotions
 * runs; for a mod ingredient this listener cancels the vanilla step and performs Forge's brewPotions instead
 * (BrewingRecipeRegistry outputs per slot, ingredient used up, container item, brewing sound event 1035).
 */
public final class Brewing implements Listener {
    private static final List<Ingredient> PLACEHOLDERS = new ArrayList<Ingredient>();
    private static final List<String> REPORT = new ArrayList<String>();
    private static final int[] OUTPUT_SLOTS = new int[]{0, 1, 2};
    private static int modRecipes;
    private static int brewed;
    private static Logger log = Logger.getLogger("JasprMutants");

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static int modRecipeCount() {
        return modRecipes;
    }

    public static int brewedCount() {
        return brewed;
    }

    public static List<String> report() {
        return REPORT;
    }

    /** True for an ingredient the brewing stand only accepts because of a mod recipe's placeholder mix. */
    public static boolean isPlaceholderReagent(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Ingredient i : PLACEHOLDERS) {
            if (i.apply(stack)) return true;
        }
        return false;
    }

    /** Called by BrewingRecipeRegistry.addRecipe for every recipe (the vanilla recipe and the mod's). */
    public static void recipeAdded(IBrewingRecipe recipe) {
        if (!(recipe instanceof BrewingRecipe)) return;
        BrewingRecipe r = (BrewingRecipe) recipe;
        modRecipes++;
        ItemStack ingredient = r.getIngredient();
        if (PotionHelper.isReagent(ingredient) && !isPlaceholderReagent(ingredient)) return;
        PotionType input = PotionUtils.getPotionFromItem(r.getInput());
        try {
            Ingredient reagent = Ingredient.fromStacks(new ItemStack(ingredient.getItem(), 1, ingredient.getMetadata()));
            if (!isPlaceholderReagent(ingredient)) PLACEHOLDERS.add(reagent);
            addTypeMix(input, reagent, input);
            REPORT.add("placeholder-mix " + Item.REGISTRY.getNameForObject(ingredient.getItem()) + " on " + PotionType.REGISTRY.getNameForObject(input));
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("MUTANTS_BREWING_HOOK_FAILED reason=" + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTypeMix(PotionType input, Ingredient reagent, PotionType output) throws ReflectiveOperationException {
        // Spigot names: PotionHelper.POTION_TYPE_CONVERSIONS = PotionBrewer.a, MixPredicate = PotionBrewer$PredicatedCombination
        Field list = PotionHelper.class.getDeclaredField("a");
        list.setAccessible(true);
        Class<?> mix = Class.forName(PotionHelper.class.getName() + "$PredicatedCombination", true, PotionHelper.class.getClassLoader());
        Constructor<?> ctor = mix.getDeclaredConstructor(Object.class, Ingredient.class, Object.class);
        ctor.setAccessible(true);
        ((List) list.get(null)).add(ctor.newInstance(input, reagent, output));
    }

    /** Forge's TileEntityBrewingStand.brewPotions for a mod ingredient, at the moment Paper would brew. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityBrewingStand)) return;
        TileEntityBrewingStand stand = (TileEntityBrewingStand) te;
        ItemStack ingredient = stand.getStackInSlot(3);
        if (!isPlaceholderReagent(ingredient)) return;
        event.setCancelled(true);
        NonNullList<ItemStack> slots = NonNullList.withSize(5, ItemStack.EMPTY);
        for (int i = 0; i < 5; i++) slots.set(i, stand.getStackInSlot(i));
        if (!BrewingRecipeRegistry.canBrew(slots, ingredient, OUTPUT_SLOTS)) {
            // Forge would not have started: nothing is brewed and the ingredient is kept.
            REPORT.add("brew-skipped-no-output");
            return;
        }
        BrewingRecipeRegistry.brewPotions(slots, ingredient, OUTPUT_SLOTS);
        for (int i : OUTPUT_SLOTS) stand.setInventorySlotContents(i, slots.get(i));
        ingredient.shrink(1);
        if (ingredient.getItem().hasContainerItem()) {
            ItemStack container = new ItemStack(ingredient.getItem().getContainerItem());
            if (ingredient.isEmpty()) {
                ingredient = container;
            } else {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), container);
            }
        }
        stand.setInventorySlotContents(3, ingredient);
        world.playEvent(1035, pos, 0);
        stand.markDirty();
        brewed++;
    }

    static boolean isPotion(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.POTIONITEM || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }
}
