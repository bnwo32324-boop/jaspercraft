package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.Container;
import net.minecraft.server.v1_12_R1.ContainerEnchantTable;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.EntityMinecartContainer;
import net.minecraft.server.v1_12_R1.ItemEnchantedBook;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityLootable;
import net.minecraft.server.v1_12_R1.WeightedRandomEnchant;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Item;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Enchanting table (exact SME candidates + table blacklist, clue included), Upgraded Potentials on
 * the anvil, librarian blacklist, and the loot blacklists / curse-free book rule for freshly generated
 * chest and fishing loot.
 */
public final class TableFx implements Listener {
    private static final Random RAND = new Random();
    public static long tablesRecomputed = 0, lootFixed = 0, librarianFixed = 0;

    private static ContainerEnchantTable table(org.bukkit.inventory.InventoryView view) {
        if (!(view instanceof CraftInventoryView)) return null;
        Container c = ((CraftInventoryView) view).getHandle();
        return c instanceof ContainerEnchantTable ? (ContainerEnchantTable) c : null;
    }

    /** the enchanting table clue ("Sharpness II . . . ?") from SME's candidate list */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent e) {
        Guard.run("table.prepare", () -> {
            ContainerEnchantTable t = table(e.getView());
            if (t == null) return;
            ItemStack stack = t.enchantSlots.getItem(0);
            if (stack.isEmpty()) return;
            EnchantmentOffer[] offers = e.getOffers();
            for (int slot = 0; slot < 3; slot++) {
                int cost = offers[slot] != null ? offers[slot].getCost() : t.costs[slot];
                if (cost <= 0) continue;
                List<WeightedRandomEnchant> list = Enchanting.tableList(stack, t.f, slot, cost);
                if (list.isEmpty()) {
                    offers[slot] = null;
                } else {
                    WeightedRandomEnchant pick = list.get(RAND.nextInt(list.size()));
                    offers[slot] = new EnchantmentOffer(Enchanting.bukkit(pick.enchantment), pick.level, cost);
                }
            }
            tablesRecomputed++;
        });
    }

    /** what the enchanting button actually applies */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        Guard.run("table.enchant", () -> {
            ContainerEnchantTable t = table(e.getView());
            if (t == null) return;
            int slot = e.whichButton();
            ItemStack stack = t.enchantSlots.getItem(0);
            List<WeightedRandomEnchant> list = Enchanting.tableList(stack, t.f, slot, t.costs[slot]);
            Map<org.bukkit.enchantments.Enchantment, Integer> add = e.getEnchantsToAdd();
            add.clear();
            for (WeightedRandomEnchant w : list) add.put(Enchanting.bukkit(w.enchantment), w.level);
        });
    }

    // ------------------------------------------------------------------ Upgraded Potentials (AnvilUpdateEvent)

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        Guard.run("upgradedpotentials", () -> {
            org.bukkit.inventory.ItemStack l = e.getInventory().getItem(0), r = e.getInventory().getItem(1);
            if (l == null || r == null || l.getType() == org.bukkit.Material.AIR || r.getType() == org.bukkit.Material.AIR) return;
            ItemStack left = CraftItemStack.asNMSCopy(l), right = CraftItemStack.asNMSCopy(r);
            if (right.getItem() != Items.ENCHANTED_BOOK) return;
            Map<Enchantment, Integer> bookEnchants = EnchantmentManager.a(right);
            if (bookEnchants.getOrDefault(E.UPGRADEDPOTENTIALS, 0) <= 0) return;
            Map<Enchantment, Integer> leftEnchants = EnchantmentManager.a(left);
            if (left.isStackable() || leftEnchants.isEmpty() || leftEnchants.getOrDefault(E.UPGRADEDPOTENTIALS, 0) > 0) {
                e.setResult(null); // event canceled: no output
                return;
            }
            int cost = left.getRepairCost();
            cost = Math.max(0, (cost / 4) - 20);
            ItemStack out = left.cloneItemStack();
            out.setRepairCost(cost);
            out.addEnchantment(E.UPGRADEDPOTENTIALS, 1);
            e.setResult(CraftItemStack.asCraftMirror(out));
            e.getInventory().setRepairCost(10);
        });
    }

    // ------------------------------------------------------------------ librarian

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(VillagerAcquireTradeEvent e) {
        Guard.run("librarian", () -> {
            MerchantRecipe r = e.getRecipe();
            ItemStack result = CraftItemStack.asNMSCopy(r.getResult());
            if (result.getItem() != Items.ENCHANTED_BOOK) return;
            NBTTagList stored = ItemEnchantedBook.h(result);
            if (stored.size() != 1) return;
            Enchantment ench = Enchantment.c(stored.get(0).getShort("id"));
            if (ench == null || !Enchanting.LIBRARIAN_BLACKLIST.contains(ench)) return;
            List<org.bukkit.inventory.ItemStack> ingredients = r.getIngredients();
            if (ingredients.size() != 2 || ingredients.get(0).getType() != org.bukkit.Material.BOOK) return;
            Random random = Nms.living(e.getEntity()).getRandom();
            List<Enchantment> valid = new ArrayList<>();
            for (Enchantment x : Enchantment.enchantments) if (!Enchanting.LIBRARIAN_BLACKLIST.contains(x)) valid.add(x);
            if (valid.isEmpty()) return;
            Enchantment pick = valid.get(random.nextInt(valid.size()));
            int i = MathHelper.nextInt(random, pick.getStartLevel(), pick.getMaxLevel());
            ItemStack book = ItemEnchantedBook.a(new WeightedRandomEnchant(pick, i));
            int j = 2 + random.nextInt(5 + i * 10) + 3 * i;
            if (pick.isTreasure()) j *= 2;
            if (j > 64) j = 64;
            MerchantRecipe nr = new MerchantRecipe(CraftItemStack.asBukkitCopy(book), r.getUses(), r.getMaxUses(), r.hasExperienceReward());
            nr.addIngredient(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK));
            nr.addIngredient(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD, j));
            e.setRecipe(nr);
            librarianFixed++;
        });
    }

    // ------------------------------------------------------------------ fresh loot

    /** vanilla 1.12 tables whose books use enchant_with_levels 30 + treasure (all others use enchant_randomly) */
    private static boolean levelBookTable(String name) {
        return name != null && (name.endsWith("chests/stronghold_library") || name.endsWith("chests/stronghold_corridor")
                || name.endsWith("chests/stronghold_crossing"));
    }

    private final Map<String, Object[]> pendingLoot = new HashMap<>();

    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private void rememberBlock(Block b) {
        WorldServer w = ((CraftWorld) b.getWorld()).getHandle();
        TileEntity te = w.getTileEntity(new net.minecraft.server.v1_12_R1.BlockPosition(b.getX(), b.getY(), b.getZ()));
        if (te instanceof TileEntityLootable) {
            MinecraftKey k = ((TileEntityLootable) te).getLootTableKey();
            if (k != null) pendingLoot.put(key(b.getLocation()), new Object[]{k.toString(), MinecraftServer.currentTick});
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Guard.run("loot.interact", () -> {
            Block b = e.getClickedBlock();
            rememberBlock(b);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block n = b.getRelative(f);
                if (n.getType() == b.getType()) rememberBlock(n);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof StorageMinecart)) return;
        Guard.run("loot.interactEntity", () -> {
            net.minecraft.server.v1_12_R1.Entity ent = Nms.entity(e.getRightClicked());
            if (ent instanceof EntityMinecartContainer) {
                MinecraftKey k = ((EntityMinecartContainer) ent).getLootTableKey();
                if (k != null) pendingLoot.put("cart:" + ent.getUniqueID(), new Object[]{k.toString(), MinecraftServer.currentTick});
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (pendingLoot.isEmpty()) return;
        Guard.run("loot.open", () -> {
            Inventory inv = e.getInventory();
            InventoryHolder h = inv.getHolder();
            List<String> keys = new ArrayList<>();
            if (h instanceof DoubleChest) {
                DoubleChest dc = (DoubleChest) h;
                if (dc.getLeftSide() instanceof BlockState) keys.add(key(((BlockState) dc.getLeftSide()).getLocation()));
                if (dc.getRightSide() instanceof BlockState) keys.add(key(((BlockState) dc.getRightSide()).getLocation()));
            } else if (h instanceof BlockState) {
                keys.add(key(((BlockState) h).getLocation()));
            } else if (h instanceof StorageMinecart) {
                keys.add("cart:" + ((StorageMinecart) h).getUniqueId());
            }
            String table = null;
            for (String k : keys) {
                Object[] p = pendingLoot.remove(k);
                if (p != null && (Integer) p[1] >= MinecraftServer.currentTick - 1) table = (String) p[0];
            }
            if (table == null) return;
            fixLoot(inv, table);
        });
        if (pendingLoot.size() > 256) pendingLoot.clear();
    }

    /** apply SME's loot rules to freshly generated items */
    public static void fixLoot(Inventory inv, String table) {
        Random r = new Random();
        boolean levelBooks = levelBookTable(table);
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack b = inv.getItem(i);
            if (b == null || b.getType() == org.bukkit.Material.AIR) continue;
            ItemStack s = CraftItemStack.asNMSCopy(b);
            ItemStack fixed = fixItem(s, r, levelBooks);
            if (fixed != null) {
                inv.setItem(i, CraftItemStack.asBukkitCopy(fixed));
                lootFixed++;
            }
        }
    }

    /** returns a replacement or null when the stack already follows SME's rules */
    static ItemStack fixItem(ItemStack s, Random r, boolean levelBooks) {
        if (s.getItem() == Items.ENCHANTED_BOOK) {
            NBTTagList stored = ItemEnchantedBook.h(s);
            if (stored.size() == 0) return null;
            if (levelBooks) {
                return levelBook(r, 30, true);
            }
            // enchant_randomly: exactly one enchantment; re-pick when it is on the Random blacklist
            if (stored.size() == 1) {
                Enchantment e = Enchantment.c(stored.get(0).getShort("id"));
                if (e != null && Enchanting.RANDOM_BLACKLIST.contains(e)) {
                    WeightedRandomEnchant w = Enchanting.randomly(r, new ItemStack(Items.BOOK));
                    if (w != null) return ItemEnchantedBook.a(w);
                }
            }
            return null;
        }
        Map<Enchantment, Integer> m = EnchantmentManager.a(s);
        boolean changed = false;
        for (Enchantment e : new ArrayList<>(m.keySet())) {
            if (!Enchanting.RANDOM_BLACKLIST.contains(e)) continue;
            m.remove(e);
            ItemStack probe = s.cloneItemStack();
            EnchantmentManager.a(new HashMap<>(), probe);
            WeightedRandomEnchant w = Enchanting.randomly(r, probe);
            if (w != null) m.put(w.enchantment, w.level);
            changed = true;
        }
        if (!changed) return null;
        ItemStack out = s.cloneItemStack();
        EnchantmentManager.a(m, out);
        return out;
    }

    /** enchant_with_levels on a book with SME's rules (no curses on books, Level blacklist) */
    public static ItemStack levelBook(Random r, int levels, boolean treasure) {
        List<WeightedRandomEnchant> list = Enchanting.build(r, new ItemStack(Items.BOOK), levels, treasure, Enchanting.LEVEL_BLACKLIST);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        for (WeightedRandomEnchant w : list) ItemEnchantedBook.a(book, w);
        return list.isEmpty() ? new ItemStack(Items.BOOK) : book;
    }

    /** fishing treasure books are enchant_with_levels 30 + treasure */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item)) return;
        Guard.run("loot.fishing", () -> {
            Item it = (Item) e.getCaught();
            ItemStack s = CraftItemStack.asNMSCopy(it.getItemStack());
            if (s.getItem() != Items.ENCHANTED_BOOK) return;
            it.setItemStack(CraftItemStack.asBukkitCopy(levelBook(new Random(), 30, true)));
            lootFixed++;
        });
    }

    @SuppressWarnings("unused")
    private static NBTTagCompound tag(ItemStack s) {
        return s.getTag();
    }

    static void debug(String s) {
        Log.info(s);
    }
}
