package chat.jaspr.mutants.hooks;

import chumbanotz.mutantbeasts.item.EndersoulHandItem;
import chumbanotz.mutantbeasts.item.HulkHammerItem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.MathHelper;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

/**
 * Forge's enchanting table for the mod's items that override Item.canApplyAtEnchantingTable (Hulk Hammer, Endersoul
 * Hand: weapon enchantments except Sweeping Edge). Forge's EnchantmentHelper.getEnchantmentDatas asks
 * enchantment.canApplyAtEnchantingTable(stack) where vanilla asks enchantment.type.canEnchantItem(item); everything else
 * (the container's seeded random, buildEnchantmentList, the clue) is vanilla. Paper's table draws from the vanilla
 * candidates (for these items only Unbreaking, through their durability), so:
 * - PrepareItemEnchantEvent: one tick later (after Paper filled its clues) the clues Forge would show are written into
 *   the container, which sends them with its next detectAndSendChanges;
 * - EnchantItemEvent (CraftBukkit fires it even for an empty list and enchants with the event's map): the map becomes
 *   the list Forge would draw.
 */
public final class EnchantingHooks implements Listener {
    private static Logger log = Logger.getLogger("JasprMutants");
    private final org.bukkit.plugin.Plugin plugin;

    public EnchantingHooks(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }
    private static Field randField;
    private static Field seedField;
    private static Field clueField;
    private static Field worldClueField;
    private static int failures;
    private long enchanted;

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public long enchantedCount() {
        return this.enchanted;
    }

    static boolean forgeTableItem(Item item) {
        return item instanceof HulkHammerItem || item instanceof EndersoulHandItem;
    }

    private static boolean canApplyAtTable(ItemStack stack, Enchantment enchantment) {
        Item item = stack.getItem();
        if (item instanceof HulkHammerItem) return ((HulkHammerItem) item).canApplyAtEnchantingTable(stack, enchantment);
        if (item instanceof EndersoulHandItem) return ((EndersoulHandItem) item).canApplyAtEnchantingTable(stack, enchantment);
        return enchantment.type.canEnchantItem(item);
    }

    /** Forge's EnchantmentHelper.getEnchantmentDatas. */
    static List<EnchantmentData> enchantmentDatas(int level, ItemStack stack, boolean allowTreasure) {
        List<EnchantmentData> list = new ArrayList<EnchantmentData>();
        boolean book = stack.getItem() == Items.BOOK;
        for (Enchantment enchantment : Enchantment.REGISTRY) {
            if ((!enchantment.isTreasureEnchantment() || allowTreasure) && (canApplyAtTable(stack, enchantment) || book)) {
                for (int i = enchantment.getMaxLevel(); i > enchantment.getMinLevel() - 1; --i) {
                    if (level >= enchantment.getMinEnchantability(i) && level <= enchantment.getMaxEnchantability(i)) {
                        list.add(new EnchantmentData(enchantment, i));
                        break;
                    }
                }
            }
        }
        return list;
    }

    /** EnchantmentHelper.buildEnchantmentList with Forge's candidates. */
    static List<EnchantmentData> buildEnchantmentList(Random random, ItemStack stack, int level, boolean allowTreasure) {
        List<EnchantmentData> list = new ArrayList<EnchantmentData>();
        int enchantability = stack.getItem().getItemEnchantability();
        if (enchantability <= 0) return list;
        level = level + 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        level = MathHelper.clamp(Math.round((float) level + (float) level * f), 1, Integer.MAX_VALUE);
        List<EnchantmentData> candidates = enchantmentDatas(level, stack, allowTreasure);
        if (!candidates.isEmpty()) {
            list.add(WeightedRandom.getRandomItem(random, candidates));
            while (random.nextInt(50) <= level) {
                EnchantmentHelper.removeIncompatible(candidates, list.get(list.size() - 1));
                if (candidates.isEmpty()) break;
                list.add(WeightedRandom.getRandomItem(random, candidates));
                level /= 2;
            }
        }
        return list;
    }

    /** ContainerEnchantment.getEnchantmentList: rand seeded with xpSeed + slot, the book drops one at random. */
    private static List<EnchantmentData> enchantmentList(ContainerEnchantment container, ItemStack stack, int slot, int level) throws ReflectiveOperationException {
        Random rand = (Random) field("l").get(container);              // ContainerEnchantment.rand
        rand.setSeed((long) (seed(container) + slot));
        List<EnchantmentData> list = buildEnchantmentList(rand, stack, level, false);
        if (stack.getItem() == Items.BOOK && list.size() > 1) list.remove(rand.nextInt(list.size()));
        return list;
    }

    private static int seed(ContainerEnchantment container) throws ReflectiveOperationException {
        return field("f").getInt(container);                          // ContainerEnchantment.xpSeed
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f;
        if ("l".equals(name)) f = randField;
        else if ("f".equals(name)) f = seedField;
        else if ("h".equals(name)) f = clueField;
        else f = worldClueField;
        if (f == null) {
            f = ContainerEnchantment.class.getDeclaredField(name);
            f.setAccessible(true);
            if ("l".equals(name)) randField = f;
            else if ("f".equals(name)) seedField = f;
            else if ("h".equals(name)) clueField = f;
            else worldClueField = f;
        }
        return f;
    }

    private static ContainerEnchantment containerOf(org.bukkit.entity.Player player) {
        EntityPlayer handle = ((CraftPlayer) player).getHandle();
        Container open = handle.openContainer;
        return open instanceof ContainerEnchantment ? (ContainerEnchantment) open : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent event) {
        ContainerEnchantment container = containerOf(event.getEnchanter());
        if (container == null) return;
        ItemStack stack = container.tableInventory.getStackInSlot(0);
        if (stack.isEmpty() || !forgeTableItem(stack.getItem())) return;
        org.bukkit.Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (containerOf(event.getEnchanter()) == container && container.tableInventory.getStackInSlot(0) == stack) writeClues(container, stack);
        });
    }

    /** The clues ContainerEnchantment.onCraftMatrixChanged would set with Forge's candidate list. */
    static void writeClues(ContainerEnchantment container, ItemStack stack) {
        try {
            int[] costs = container.enchantLevels;
            int[] clue = (int[]) field("h").get(container);              // ContainerEnchantment.enchantClue
            int[] worldClue = (int[]) field("i").get(container);         // ContainerEnchantment.worldClue
            Random rand = (Random) field("l").get(container);
            for (int j = 0; j < 3; ++j) {
                clue[j] = -1;
                worldClue[j] = -1;
                if (costs[j] <= 0) continue;
                List<EnchantmentData> list = enchantmentList(container, stack, j, costs[j]);
                if (!list.isEmpty()) {
                    EnchantmentData data = list.get(rand.nextInt(list.size()));
                    clue[j] = Enchantment.getEnchantmentID(data.enchantment);
                    worldClue[j] = data.enchantmentLevel;
                }
            }
            container.detectAndSendChanges();
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (failures++ < 20) log.warning("MUTANTS_HOOK_FAILED hook=enchant_clue reason=" + e.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnchant(EnchantItemEvent event) {
        ContainerEnchantment container = containerOf(event.getEnchanter());
        if (container == null) return;
        ItemStack stack = container.tableInventory.getStackInSlot(0);
        if (stack.isEmpty() || !forgeTableItem(stack.getItem())) return;
        try {
            int slot = event.whichButton();
            List<EnchantmentData> list = enchantmentList(container, stack, slot, container.enchantLevels[slot]);
            event.getEnchantsToAdd().clear();
            for (EnchantmentData data : list) {
                org.bukkit.enchantments.Enchantment bukkit = org.bukkit.enchantments.Enchantment.getById(Enchantment.getEnchantmentID(data.enchantment));
                if (bukkit != null) event.getEnchantsToAdd().put(bukkit, data.enchantmentLevel);
            }
            if (!event.getEnchantsToAdd().isEmpty()) this.enchanted++;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (failures++ < 20) log.warning("MUTANTS_HOOK_FAILED hook=enchant_item reason=" + e.getClass().getSimpleName());
        }
    }
}
