package chat.jaspr.rpg;

import java.util.UUID;
import net.minecraft.server.v1_12_R1.AttributeModifier;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The little other plugins may ask of the stat sheet (called through reflection, so nobody needs this plugin at
 * compile time). JasprApocalypse's sentry turrets read their owner's Engineering here, even while the owner is away.
 */
public final class RpgApi {
    private static volatile RpgPlugin plugin;

    /** The worn pieces in the order Bukkit lists them (boots, leggings, chestplate, helmet) and the slot each one sits in. */
    private static final EnumItemSlot[] WORN = {EnumItemSlot.FEET, EnumItemSlot.LEGS, EnumItemSlot.CHEST, EnumItemSlot.HEAD};
    /** Most armour points the armaments can add to the bar (the client reads a small whole number). */
    private static final long MAX_ARMOUR_BONUS = 200L;

    private RpgApi() {}

    static void bind(RpgPlugin p) { plugin = p; }

    /** A player's level in a stat by key ("engineering", "health", ...); 0 when unknown. */
    public static int level(UUID player, String stat) {
        RpgPlugin p = plugin;
        StatType type = StatType.byKey(stat);
        if (p == null || type == null || player == null || p.stats() == null) return 0;
        PlayerStats sheet = p.stats().find(player);
        return sheet == null ? 0 : sheet.level(type);
    }

    /**
     * A read-only summary of a player's stat sheet for the Field Journal (JasprJournal calls this through reflection):
     * {total ranks bought, stats with at least one rank, number of stats, stats that can be raised right now with
     * {@code xpLevels} experience levels, the cheapest next price in levels (-1 when every stat is at its cap)}.
     * Never changes anything.
     */
    public static int[] summary(UUID player, int xpLevels) {
        RpgPlugin p = plugin;
        if (p == null || player == null || p.stats() == null) return null;
        PlayerStats sheet = p.stats().find(player);
        RpgConfig settings = p.settings();
        int ranks = 0, raised = 0, total = 0, affordable = 0, cheapest = -1;
        for (StatType stat : StatType.values()) {
            total++;
            int level = sheet == null ? 0 : sheet.level(stat);
            if (level > 0) { ranks += level; raised++; }
            if (level >= settings.capFor(stat)) continue;
            int cost = StatCosts.cost(level, settings.costMultiplier);
            if (cheapest < 0 || cost < cheapest) cheapest = cost;
            if (xpLevels >= cost) affordable++;
        }
        return new int[] {ranks, raised, total, affordable, cheapest};
    }

    /**
     * The armour points the worn armaments add to the armour bar (the Field Journal sends them to the client, whose Overloaded
     * Armor Bar counts them): every worn, enhanced piece of armour counts its own armour value once more by its rarity's
     * protection bonus, so "+40% protection" on a piece with 4 armour is 1.6 points; the sum is rounded to whole points (0..200).
     * A display value only: how much damage a piece turns aside is still decided by {@link ArmamentListener#onHurt}, exactly as
     * before. Never changes anything; call it on the server thread.
     */
    public static int armamentArmor(Player player) {
        if (player == null) return 0;
        try {
            ItemStack[] worn = player.getInventory().getArmorContents();
            double extra = 0.0d;
            for (int i = 0; i < worn.length && i < WORN.length; i++) {
                ItemStack piece = worn[i];
                if (!Armament.isEnhanced(piece) || !Armament.isArmour(piece)) continue;
                double bonus = Armament.rarity(piece).bonus;
                if (bonus > 0.0d) extra += bonus * armourOf(piece, WORN[i]);
            }
            return (int) Math.max(0L, Math.min(MAX_ARMOUR_BONUS, Math.round(extra)));
        } catch (RuntimeException | LinkageError unsupported) {
            return 0;
        }
    }

    /** The armour points an item gives in a slot: its own armour modifiers (add operation), or the vanilla ones when it has none. */
    static double armourOf(ItemStack piece, EnumItemSlot slot) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(piece);
        if (nms == null) return 0.0d;
        double sum = 0.0d;
        for (AttributeModifier modifier : nms.a(slot).get("generic.armor")) {
            if (modifier.c() == 0) sum += modifier.d();
        }
        return Math.max(0.0d, sum);
    }

    /** Damage multiplier a player's sentry turrets get from Engineering (1.0 without it). */
    public static double turretMultiplier(UUID owner) {
        RpgPlugin p = plugin;
        if (p == null || owner == null || p.stats() == null) return 1.0d;
        PlayerStats sheet = p.stats().find(owner);
        return sheet == null ? 1.0d : sheet.multiplier(StatType.ENGINEERING, p.settings());
    }
}
