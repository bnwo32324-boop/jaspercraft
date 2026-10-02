package chat.jaspr.bounties;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What bounties pay: experience points (the currency of the stat sheet), a stack of something useful and, on the
 * harder ones, a chance at a trinket from JasprGear (asked for by reflection, so this plugin runs without it).
 */
final class Rewards {
    static final class Prize {
        final int xp; final Material item; final int amount; final double trinket;
        Prize(int xp, Material item, int amount, double trinket) { this.xp = xp; this.item = item; this.amount = amount; this.trinket = trinket; }
    }

    private static final Object[][] EASY = {{Material.IRON_INGOT, 6}, {Material.BREAD, 10}, {Material.ARROW, 24}, {Material.COAL, 16},
        {Material.GOLD_INGOT, 3}, {Material.COOKED_BEEF, 8}};
    private static final Object[][] MEDIUM = {{Material.IRON_INGOT, 12}, {Material.GOLD_INGOT, 6}, {Material.EMERALD, 3}, {Material.REDSTONE, 20},
        {Material.EXP_BOTTLE, 6}, {Material.ENDER_PEARL, 2}};
    private static final Object[][] HARD = {{Material.DIAMOND, 2}, {Material.EMERALD, 6}, {Material.GOLDEN_APPLE, 2}, {Material.IRON_BLOCK, 2},
        {Material.EXP_BOTTLE, 12}, {Material.BLAZE_ROD, 4}};
    private static final Object[][] WEEKLY = {{Material.DIAMOND, 6}, {Material.EMERALD_BLOCK, 2}, {Material.GOLD_BLOCK, 3}, {Material.EXP_BOTTLE, 24},
        {Material.GOLDEN_APPLE, 4}, {Material.DIAMOND_BLOCK, 1}};

    final int[] xp;
    final double[] trinketChance;
    final int streakXp;
    private final BountiesPlugin plugin;
    private Method trinketMethod;
    private boolean trinketLooked;
    long trinketsGiven, trinketsMissing;

    Rewards(BountiesPlugin plugin, int[] xp, double[] trinketChance, int streakXp) {
        this.plugin = plugin; this.xp = xp; this.trinketChance = trinketChance; this.streakXp = streakXp;
    }

    Prize prize(Template.Tier tier, Random r) {
        Object[][] pool = tier == Template.Tier.EASY ? EASY : tier == Template.Tier.MEDIUM ? MEDIUM : tier == Template.Tier.HARD ? HARD : WEEKLY;
        Object[] pick = pool[r.nextInt(pool.length)];
        return new Prize(xp[tier.ordinal()], (Material) pick[0], (Integer) pick[1], trinketChance[tier.ordinal()]);
    }

    /** One line naming a bounty's reward ("350 XP + 12 Iron Ingot, 10% trinket"). */
    static String describe(Bounty b) {
        StringBuilder s = new StringBuilder();
        s.append(b.xp).append(" XP");
        if (b.item != null && b.amount > 0) s.append(" + ").append(b.amount).append(' ').append(nice(b.item));
        if (b.trinket >= 1.0) s.append(" + a trinket");
        else if (b.trinket > 0) s.append(" + ").append(Math.round(b.trinket * 100)).append("% trinket chance");
        return s.toString();
    }

    static String nice(Material m) {
        String n = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        switch (m) {
            case EXP_BOTTLE: return "Bottle o' Enchanting";
            case INK_SACK: return "Lapis";
            case SULPHUR: return "Gunpowder";
            default: return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    /** Pays a claimed bounty to an online player. Returns a short description of what was given. */
    String pay(Player p, Bounty b) {
        StringBuilder got = new StringBuilder();
        p.giveExp(b.xp);
        got.append(b.xp).append(" XP");
        if (b.item != null && b.amount > 0) {
            give(p, new ItemStack(b.item, b.amount));
            got.append(", ").append(b.amount).append(' ').append(nice(b.item));
        }
        if (b.trinket > 0 && plugin.random().nextDouble() < b.trinket) {
            if (trinket(p)) got.append(", a trinket!");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        return got.toString();
    }

    /** Pays one owed entry ("xp:N", "item:MATERIAL:N", "trinket"). */
    void payOwed(Player p, String entry) {
        if (entry.startsWith("xp:")) {
            try { p.giveExp(Integer.parseInt(entry.substring(3))); } catch (NumberFormatException ignored) { }
        } else if (entry.startsWith("item:")) {
            String[] part = entry.split(":");
            Material m = part.length == 3 ? Material.getMaterial(part[1]) : null;
            if (m != null) try { give(p, new ItemStack(m, Integer.parseInt(part[2]))); } catch (NumberFormatException ignored) { }
        } else if (entry.equals("trinket")) {
            trinket(p);
        }
    }

    static void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    /** A random trinket from JasprGear's boss loot (GearApi.bossLoot(Random), by reflection); false when unavailable. */
    boolean trinket(Player p) {
        ItemStack item = null;
        try {
            if (!trinketLooked) {
                trinketLooked = true;
                org.bukkit.plugin.Plugin gear = plugin.getServer().getPluginManager().getPlugin("JasprGear");
                if (gear != null && gear.isEnabled()) {
                    Class<?> c = Class.forName("chat.jaspr.gear.GearApi", true, gear.getClass().getClassLoader());
                    trinketMethod = c.getMethod("bossLoot", Random.class);
                }
            }
            if (trinketMethod != null) item = (ItemStack) trinketMethod.invoke(null, plugin.random());
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            trinketMethod = null;
        }
        if (item == null) {
            trinketsMissing++;
            give(p, new ItemStack(Material.DIAMOND, 2));   // the gear plugin is away: a fair stand-in
            return false;
        }
        give(p, item);
        trinketsGiven++;
        p.sendMessage(ChatColor.LIGHT_PURPLE + "The board pays out a trinket: " + ChatColor.WHITE
            + (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : nice(item.getType())));
        return true;
    }
}
