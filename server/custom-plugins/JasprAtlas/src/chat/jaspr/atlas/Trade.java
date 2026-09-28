package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * The Concord's merchants. Each trade (baker, smith, scribe, apothecary...) sells what its calling makes and buys what
 * the Dominion leaves behind: every merchant takes Dominion trophies, and smiths rework ash-iron. Prices follow the
 * buyer's standing: friends of the Concord pay less, the distrusted are refused (and told why).
 */
final class Trade {
    private final AtlasPlugin plugin;
    long opened;

    Trade(AtlasPlugin plugin) { this.plugin = plugin; }

    void open(Player p, Entity who) {
        String trade = Npcs.tagValue(who, Npcs.MERCHANT);
        if (trade == null) return;
        if (plugin.reputation().barred(p)) {
            p.sendMessage(ChatColor.RED + Talk.name(who) + " will not trade with you. " + ChatColor.GRAY + "Your standing with the Concord is too low ("
                + plugin.reputation().get(p) + "). Pay a fine to Archon Kleio in the Synedrion, or let time and good deeds mend it.");
            return;
        }
        int standing = plugin.reputation().get(p);
        Merchant m = Bukkit.createMerchant(Talk.name(who));
        m.setRecipes(recipes(trade, standing));
        p.openMerchant(m, true);
        opened++;
        plugin.getLogger().info("ATLAS_TRADE_OPEN player=" + p.getName() + " trade=" + trade + " standing=" + standing);
    }

    /** Emerald price scaled by standing: friends pay up to a third less, strangers the list price. */
    static int price(int base, int standing) {
        double f = standing >= 100 ? 0.67 : standing >= 50 ? 0.8 : standing >= 20 ? 0.9 : 1.0;
        return Math.max(1, (int) Math.round(base * f));
    }

    static List<MerchantRecipe> recipes(String trade, int standing) {
        List<MerchantRecipe> r = new ArrayList<>();
        switch (trade) {
            case "baker": case "grocer":
                sell(r, new ItemStack(Material.BREAD, 6), 1, standing);
                sell(r, new ItemStack(Material.CAKE), 2, standing);
                sell(r, new ItemStack(Material.PUMPKIN_PIE, 4), 1, standing);
                sell(r, new ItemStack(Material.COOKED_FISH, 5), 1, standing);
                sell(r, new ItemStack(Material.APPLE, 6), 1, standing);
                buy(r, new ItemStack(Material.WHEAT, 20), 1);
                break;
            case "smith":
                sell(r, new ItemStack(Material.IRON_SWORD), 4, standing);
                sell(r, new ItemStack(Material.IRON_CHESTPLATE), 9, standing);
                sell(r, new ItemStack(Material.IRON_HELMET), 5, standing);
                sell(r, new ItemStack(Material.SHIELD), 3, standing);
                sell(r, new ItemStack(Material.IRON_PICKAXE), 4, standing);
                // Ash-iron reworked into clean iron: the Lampsa smiths' particular skill.
                r.add(recipe(new ItemStack(Material.IRON_INGOT, 2), Items.ashIron(3), null));
                buy(r, new ItemStack(Material.COAL, 16), 1);
                break;
            case "scribe":
                sell(r, new ItemStack(Material.BOOK_AND_QUILL), 1, standing);
                sell(r, new ItemStack(Material.PAPER, 12), 1, standing);
                sell(r, new ItemStack(Material.EMPTY_MAP), 2, standing);
                for (String id : new String[] {"primer", "four_crowns", "kallias_letters", "omits"}) {
                    LoreBooks.Book b = LoreBooks.BOOKS.get(id);
                    if (b != null) r.add(recipe(b.item(), new ItemStack(Material.EMERALD, price(2, standing)), null));
                }
                break;
            case "apothecary":
                r.add(recipe(potion(PotionType.INSTANT_HEAL), new ItemStack(Material.EMERALD, price(3, standing)), null));
                r.add(recipe(potion(PotionType.REGEN), new ItemStack(Material.EMERALD, price(4, standing)), null));
                r.add(recipe(potion(PotionType.FIRE_RESISTANCE), new ItemStack(Material.EMERALD, price(4, standing)), null));
                r.add(recipe(potion(PotionType.STRENGTH), new ItemStack(Material.EMERALD, price(5, standing)), null));
                buy(r, new ItemStack(Material.SPIDER_EYE, 6), 1);
                break;
            case "lumenwright":
                sell(r, new ItemStack(Material.SEA_LANTERN, 2), 2, standing);
                sell(r, new ItemStack(Material.END_ROD, 4), 2, standing);
                sell(r, new ItemStack(Material.QUARTZ_BLOCK, 8), 2, standing);   // for building a gate of one's own
                sell(r, new ItemStack(Material.PRISMARINE_CRYSTALS, 4), 2, standing);
                break;
            case "potter":
                sell(r, new ItemStack(Material.FLOWER_POT, 3), 1, standing);
                sell(r, new ItemStack(Material.HARD_CLAY, 16), 2, standing);
                sell(r, new ItemStack(Material.BRICK, 16), 1, standing);
                break;
            case "weaver":
                sell(r, new ItemStack(Material.WOOL, 8, (short) 3), 1, standing);
                sell(r, new ItemStack(Material.WOOL, 8, (short) 0), 1, standing);
                sell(r, new ItemStack(Material.BANNER, 1, (short) 15), 2, standing);
                sell(r, new ItemStack(Material.BED, 1, (short) 3), 2, standing);
                break;
            case "cartographer":
                sell(r, new ItemStack(Material.EMPTY_MAP), 2, standing);
                sell(r, new ItemStack(Material.COMPASS), 3, standing);
                break;
            case "quartermaster":
                sell(r, new ItemStack(Material.ARROW, 16), 1, standing);
                sell(r, new ItemStack(Material.BOW), 2, standing);
                sell(r, new ItemStack(Material.SHIELD), 2, standing);
                sell(r, new ItemStack(Material.COOKED_BEEF, 8), 1, standing);
                sell(r, new ItemStack(Material.IRON_CHESTPLATE), 8, standing);
                break;
            default:
                sell(r, new ItemStack(Material.BREAD, 4), 1, standing);
        }
        // Every merchant buys the Dominion's trophies: the Concord studies them, and pays.
        for (int i = 0; i < Items.TROPHIES.length; i++) r.add(recipe(new ItemStack(Material.EMERALD, 1), Items.trophy(i), null));
        return r;
    }

    private static void sell(List<MerchantRecipe> r, ItemStack what, int base, int standing) { r.add(recipe(what, new ItemStack(Material.EMERALD, price(base, standing)), null)); }
    private static void buy(List<MerchantRecipe> r, ItemStack what, int emeralds) { r.add(recipe(new ItemStack(Material.EMERALD, emeralds), what, null)); }

    private static MerchantRecipe recipe(ItemStack result, ItemStack cost, ItemStack cost2) {
        MerchantRecipe m = new MerchantRecipe(result, 9999);
        m.addIngredient(cost);
        if (cost2 != null) m.addIngredient(cost2);
        m.setExperienceReward(false);
        return m;
    }

    private static ItemStack potion(PotionType t) {
        ItemStack i = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) i.getItemMeta();
        meta.setBasePotionData(new PotionData(t));
        i.setItemMeta(meta);
        return i;
    }
}
