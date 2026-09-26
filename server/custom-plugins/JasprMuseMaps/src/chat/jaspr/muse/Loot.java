package chat.jaspr.muse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * Loot for Muse+GLM_Maps chests. Every design stocks from its theme's table, can hold So Many Enchantments books,
 * expedition supplies, weak sidearms, Survivor Gear and (rarely) Essences; its vault chest holds the design's own
 * unique special item. Other plugins are reached by reflection only and are never hard dependencies.
 */
public final class Loot {
    private Loot() {}

    private static final class Entry {
        final Material m; final int data, min, max, weight;
        Entry(Material m, int data, int min, int max, int weight) { this.m = m; this.data = data; this.min = min; this.max = max; this.weight = weight; }
    }
    private static final Map<String, List<Entry>> THEMES = new HashMap<>();
    private static void t(String theme, Object... rows) {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < rows.length; i += 5)
            list.add(new Entry((Material) rows[i], (Integer) rows[i + 1], (Integer) rows[i + 2], (Integer) rows[i + 3], (Integer) rows[i + 4]));
        THEMES.put(theme, list);
    }
    static {
        t("arcane", Material.BOOK, 0, 2, 6, 10, Material.PAPER, 0, 3, 9, 8, Material.EXP_BOTTLE, 0, 3, 10, 10, Material.INK_SACK, 4, 3, 12, 10,
            Material.ENDER_PEARL, 0, 1, 3, 5, Material.BLAZE_ROD, 0, 1, 3, 4, Material.GLOWSTONE_DUST, 0, 3, 10, 6, Material.REDSTONE, 0, 4, 12, 6,
            Material.NAME_TAG, 0, 1, 1, 3, Material.GOLDEN_CARROT, 0, 2, 5, 5);
        t("sacred", Material.GOLDEN_APPLE, 0, 1, 2, 6, Material.GOLD_NUGGET, 0, 6, 18, 8, Material.PAPER, 0, 2, 6, 5, Material.BREAD, 0, 3, 7, 8,
            Material.EXP_BOTTLE, 0, 2, 8, 8, Material.EMERALD, 0, 1, 4, 6, Material.TOTEM, 0, 1, 1, 1, Material.GLOWSTONE_DUST, 0, 2, 8, 5,
            Material.BONE, 0, 2, 6, 5);
        t("martial", Material.ARROW, 0, 8, 24, 10, Material.IRON_INGOT, 0, 2, 7, 9, Material.SHIELD, 0, 1, 1, 4, Material.IRON_SWORD, 0, 1, 1, 4,
            Material.BOW, 0, 1, 1, 4, Material.IRON_HELMET, 0, 1, 1, 3, Material.CHAINMAIL_CHESTPLATE, 0, 1, 1, 3, Material.GOLDEN_APPLE, 0, 1, 1, 3,
            Material.SULPHUR, 0, 2, 8, 6, Material.COOKED_BEEF, 0, 3, 8, 7);
        t("craft", Material.IRON_INGOT, 0, 3, 9, 10, Material.COAL, 0, 6, 16, 10, Material.REDSTONE, 0, 4, 14, 8, Material.GOLD_INGOT, 0, 1, 4, 5,
            Material.IRON_PICKAXE, 0, 1, 1, 4, Material.BUCKET, 0, 1, 2, 4, Material.RAILS, 0, 6, 16, 5, Material.PISTON_BASE, 0, 1, 3, 4,
            Material.IRON_NUGGET, 0, 8, 24, 8, Material.OBSIDIAN, 0, 1, 4, 3);
        t("rural", Material.BREAD, 0, 3, 9, 10, Material.WHEAT, 0, 4, 12, 8, Material.CARROT_ITEM, 0, 3, 8, 7, Material.POTATO_ITEM, 0, 3, 8, 7,
            Material.SAPLING, 0, 2, 5, 5, Material.EGG, 0, 2, 6, 5, Material.LEATHER, 0, 2, 6, 6, Material.SADDLE, 0, 1, 1, 3,
            Material.PUMPKIN_PIE, 0, 2, 5, 6, Material.HAY_BLOCK, 0, 1, 4, 4, Material.NAME_TAG, 0, 1, 1, 2);
        t("civic", Material.EMERALD, 0, 1, 5, 8, Material.GOLD_NUGGET, 0, 5, 16, 8, Material.COOKED_BEEF, 0, 3, 8, 8, Material.BREAD, 0, 3, 8, 8,
            Material.PAPER, 0, 3, 10, 6, Material.WATCH, 0, 1, 1, 3, Material.COMPASS, 0, 1, 1, 3, Material.CAKE, 0, 1, 1, 3,
            Material.COOKIE, 0, 4, 12, 6, Material.BAKED_POTATO, 0, 3, 8, 7, Material.IRON_INGOT, 0, 2, 6, 6);
        t("maritime", Material.RAW_FISH, 0, 3, 9, 8, Material.COOKED_FISH, 0, 3, 8, 8, Material.PRISMARINE_SHARD, 0, 2, 8, 7,
            Material.PRISMARINE_CRYSTALS, 0, 2, 6, 5, Material.FISHING_ROD, 0, 1, 1, 4, Material.STRING, 0, 3, 9, 6, Material.BOAT, 0, 1, 1, 3,
            Material.ENDER_PEARL, 0, 1, 2, 3, Material.GOLD_INGOT, 0, 1, 4, 5, Material.SPONGE, 0, 1, 2, 2);
        t("infernal", Material.BLAZE_ROD, 0, 1, 4, 8, Material.BLAZE_POWDER, 0, 2, 6, 7, Material.MAGMA_CREAM, 0, 1, 4, 6,
            Material.NETHER_STALK, 0, 2, 8, 7, Material.QUARTZ, 0, 4, 12, 8, Material.GLOWSTONE_DUST, 0, 3, 9, 6, Material.GOLD_INGOT, 0, 2, 5, 6,
            Material.GHAST_TEAR, 0, 1, 2, 3, Material.FIREBALL, 0, 2, 6, 5, Material.OBSIDIAN, 0, 2, 5, 4);
        t("ancient", Material.GOLD_INGOT, 0, 2, 6, 8, Material.BONE, 0, 3, 9, 8, Material.EMERALD, 0, 1, 4, 6, Material.GOLDEN_APPLE, 0, 1, 1, 3,
            Material.DIAMOND, 0, 1, 2, 2, Material.LEATHER, 0, 2, 6, 6, Material.STRING, 0, 3, 9, 6, Material.SPIDER_EYE, 0, 2, 5, 5,
            Material.GOLD_NUGGET, 0, 6, 18, 7);
        t("frost", Material.SNOW_BALL, 0, 6, 16, 7, Material.PACKED_ICE, 0, 2, 8, 6, Material.COOKED_FISH, 0, 3, 8, 7, Material.LEATHER, 0, 3, 8, 7,
            Material.IRON_INGOT, 0, 2, 6, 7, Material.ARROW, 0, 8, 20, 8, Material.DIAMOND, 0, 1, 1, 2, Material.RABBIT_STEW, 0, 1, 2, 5);
        t("void", Material.ENDER_PEARL, 0, 1, 4, 9, Material.EYE_OF_ENDER, 0, 1, 2, 5, Material.CHORUS_FRUIT, 0, 3, 9, 8,
            Material.SHULKER_SHELL, 0, 1, 1, 2, Material.PURPUR_BLOCK, 0, 4, 12, 6, Material.EXP_BOTTLE, 0, 3, 9, 8, Material.END_ROD, 0, 2, 6, 5,
            Material.DRAGONS_BREATH, 0, 1, 1, 2);
    }

    /** SME enchantments never placed as loot (SME's own Random/Level loot blacklist) and curses. */
    private static final Set<String> NO_LOOT = new HashSet<>(Arrays.asList("supremeprotection", "pandorascurse"));

    static void fill(MusePlugin plugin, Inventory inv, Catalog.Design d, Random r, boolean vault) {
        try {
            List<ItemStack> out = new ArrayList<>();
            List<Entry> table = THEMES.getOrDefault(d.theme, THEMES.get("civic"));
            int rolls = (vault ? 6 : 3) + Math.min(5, d.tier) / (vault ? 1 : 2) + r.nextInt(3);
            for (int i = 0; i < rolls; i++) out.add(roll(table, r));
            if (r.nextDouble() < (vault ? 1.0 : 0.35)) out.add(smeBook(r, d.tier, vault));
            if (vault) out.add(smeBook(r, d.tier, true));
            if (r.nextDouble() < 0.25) out.add(expedition(r.nextBoolean() ? "field_ration" : pick(r, "trauma_kit", "power_cell", "alloy_plate", "ballistic_fiber", "scrap"), d.tier));
            if (d.tier >= 2 && r.nextDouble() < (vault ? 0.45 : 0.12)) { out.add(expedition(pick(r, "rapture", "g18", "magnum44", "wingman", "mozambique"), d.tier)); out.add(new ItemStack(Material.IRON_NUGGET, 12 + r.nextInt(20))); }
            if (vault && d.tier >= 3 && r.nextDouble() < 0.4) out.add(expeditionMelee(r, d.tier));
            if (vault && d.tier >= 4 && r.nextDouble() < 0.2) out.add(expedition(pick(r, "sepulcher", "turnstile", "cinder", "vesper", "whisper", "tunnelrat"), d.tier));
            ItemStack gear = gear(r, d.tier);
            if (gear != null) out.add(gear);
            if (r.nextDouble() < (vault ? (d.tier >= 3 ? 0.5 : 0.2) : (d.tier >= 3 ? 0.03 : 0.0))) out.add(Essences.item(Essences.random(r), 1));
            if (vault) out.add(special(d));
            if (d.tier >= 4 && r.nextDouble() < 0.25) out.add(new ItemStack(Material.DIAMOND, 1 + r.nextInt(2)));
            place(inv, out, r);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("MUSE_LOOT_FAILED design=" + d.id + " " + e.getClass().getSimpleName());
        }
    }

    private static String pick(Random r, String... ids) { return ids[r.nextInt(ids.length)]; }

    private static ItemStack roll(List<Entry> table, Random r) {
        int total = 0;
        for (Entry e : table) total += e.weight;
        int p = r.nextInt(total);
        for (Entry e : table) {
            if ((p -= e.weight) < 0) {
                ItemStack item = new ItemStack(e.m, e.min + r.nextInt(e.max - e.min + 1), (short) e.data);
                if (item.getAmount() > item.getMaxStackSize()) item.setAmount(item.getMaxStackSize());
                return item;
            }
        }
        return new ItemStack(Material.BREAD, 2);
    }

    static void place(Inventory inv, List<ItemStack> items, Random r) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) free.add(i);
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || free.isEmpty()) continue;
            inv.setItem(free.remove(r.nextInt(free.size())), item);
        }
    }

    // ------------------------------------------------------------------ So Many Enchantments (via the NMS registry)

    private static List<net.minecraft.server.v1_12_R1.Enchantment> smePool(boolean allowTreasure) {
        List<net.minecraft.server.v1_12_R1.Enchantment> out = new ArrayList<>();
        for (net.minecraft.server.v1_12_R1.Enchantment e : net.minecraft.server.v1_12_R1.Enchantment.enchantments) {
            MinecraftKey key = net.minecraft.server.v1_12_R1.Enchantment.enchantments.b(e);
            if (key == null || !"somanyenchantments".equals(key.b())) continue;
            if (NO_LOOT.contains(key.getKey()) || e.isCursed()) continue;
            if (e.isTreasure() && !allowTreasure) continue;
            out.add(e);
        }
        return out;
    }

    /** An enchanted book carrying one So Many Enchantments enchantment (a vanilla one if SME is not installed). */
    static ItemStack smeBook(Random r, int tier, boolean rich) {
        List<net.minecraft.server.v1_12_R1.Enchantment> pool = smePool(rich && r.nextInt(4) == 0);
        if (pool.isEmpty()) for (net.minecraft.server.v1_12_R1.Enchantment e : net.minecraft.server.v1_12_R1.Enchantment.enchantments)
            if (!e.isCursed()) pool.add(e);
        net.minecraft.server.v1_12_R1.Enchantment e = pool.get(r.nextInt(pool.size()));
        int max = Math.max(1, e.getMaxLevel());
        int level = Math.max(1, Math.min(max, 1 + r.nextInt(Math.max(1, (max * Math.min(5, tier + (rich ? 1 : 0)) + 4) / 5))));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        return withEnchants(book, "StoredEnchantments", new int[][]{{net.minecraft.server.v1_12_R1.Enchantment.getId(e), level}});
    }

    /** Writes enchantments straight into NBT so ids unknown to Bukkit (none, once SME registers) still survive. */
    static ItemStack withEnchants(ItemStack item, String tag, int[][] enchants) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
        NBTTagList list = root.hasKeyOfType(tag, 9) ? root.getList(tag, 10) : new NBTTagList();
        for (int[] en : enchants) {
            NBTTagCompound c = new NBTTagCompound();
            c.setShort("id", (short) en[0]); c.setShort("lvl", (short) en[1]);
            list.add(c);
        }
        root.set(tag, list);
        nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }

    static int enchantId(String key) {
        net.minecraft.server.v1_12_R1.Enchantment e = net.minecraft.server.v1_12_R1.Enchantment.enchantments.get(new MinecraftKey(key));
        return e == null ? -1 : net.minecraft.server.v1_12_R1.Enchantment.getId(e);
    }

    /** The design's unique special item (catalogue "special"): named, lored, enchanted with vanilla + SME. */
    static ItemStack special(Catalog.Design d) {
        Catalog.Special s = d.special;
        Material m = Material.matchMaterial(s.material);
        if (m == null) m = Material.DIAMOND_SWORD;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + s.name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + s.lore);
        lore.add(ChatColor.DARK_PURPLE + "Muse+GLM_Maps relic");
        meta.setLore(lore);
        item.setItemMeta(meta);
        List<int[]> en = new ArrayList<>();
        for (List<Object> e : s.enchants) {
            int id = enchantId(String.valueOf(e.get(0)));
            if (id >= 0) en.add(new int[]{id, ((Number) e.get(1)).intValue()});
        }
        return withEnchants(item, m == Material.ENCHANTED_BOOK ? "StoredEnchantments" : "ench", en.toArray(new int[0][]));
    }

    // ------------------------------------------------------------------ other plugins (reflection, optional)

    private static Method expeditionMethod, catalogueMethod, gearRoll;
    private static ClassLoader apocLoader, gearLoader;

    static ItemStack expedition(String id, int tier) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            if (p == null || !p.isEnabled()) return fallback(id);
            if (expeditionMethod == null || apocLoader != p.getClass().getClassLoader()) {
                apocLoader = p.getClass().getClassLoader();
                Class<?> c = Class.forName("chat.jaspr.apocalypse.ApocalypseItems", true, apocLoader);
                expeditionMethod = c.getMethod("expedition", String.class, int.class);
                catalogueMethod = c.getMethod("catalogue", String.class);
            }
            return (ItemStack) expeditionMethod.invoke(null, id, Math.max(1, Math.min(5, tier)));
        } catch (ReflectiveOperationException | RuntimeException e) { return fallback(id); }
    }

    @SuppressWarnings("unchecked")
    static ItemStack expeditionMelee(Random r, int tier) {
        try {
            expedition("scrap", 1);
            if (catalogueMethod == null) return new ItemStack(Material.IRON_SWORD);
            List<String> ids = new ArrayList<>(((Map<String, String>) catalogueMethod.invoke(null, "melee")).keySet());
            ids.removeIf(id -> Weapons.isBossWeapon(id));
            return ids.isEmpty() ? new ItemStack(Material.IRON_SWORD) : expedition(ids.get(r.nextInt(ids.size())), tier);
        } catch (ReflectiveOperationException | RuntimeException e) { return new ItemStack(Material.IRON_SWORD); }
    }

    private static ItemStack fallback(String id) { return new ItemStack(Material.IRON_NUGGET, 8); }

    static ItemStack gear(Random r, int tier) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("JasprGear");
            if (p == null || !p.isEnabled()) return null;
            if (gearRoll == null || gearLoader != p.getClass().getClassLoader()) {
                gearLoader = p.getClass().getClassLoader();
                gearRoll = Class.forName("chat.jaspr.gear.GearApi", true, gearLoader).getMethod("rollLoot", Random.class, int.class);
            }
            return (ItemStack) gearRoll.invoke(null, r, Math.max(1, Math.min(5, tier)));
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    static ItemStack potion(PotionType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionData(new PotionData(type));
        item.setItemMeta(meta);
        return item;
    }

    static void hideFlags(ItemMeta meta) { meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); }
}
