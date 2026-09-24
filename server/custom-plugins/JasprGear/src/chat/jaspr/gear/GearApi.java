package chat.jaspr.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.inventory.ItemStack;

/**
 * Stable public API for other plugins (call through reflection; no compile-time dependency):
 *
 *   ClassLoader cl = Bukkit.getPluginManager().getPlugin("JasprGear").getClass().getClassLoader();
 *   Class<?> api = Class.forName("chat.jaspr.gear.GearApi", true, cl);
 *   ItemStack loot = (ItemStack) api.getMethod("rollLoot", Random.class, int.class).invoke(null, random, tier);
 *
 * All methods are static, main-thread (world-generation populators run there), need no player,
 * never throw for bad input and return fresh canonical items.
 */
public final class GearApi {
    /** Chance that one chest receives a trinket, by tier 0..5. */
    private static final double[] CHANCE = {0.03, 0.05, 0.08, 0.12, 0.18, 0.25};
    /** Phase 2: extra chance, above the trinket band, that the chest receives one consumable. */
    static final double[] SUPPLY_CHANCE = {0.06, 0.08, 0.10, 0.12, 0.14, 0.16};

    private GearApi() {}

    /**
     * One loot roll for a chest. tier 0 = trivial (vanilla-style/dungeon rooms), 1..5 = structure
     * difficulty (catalogue tiers I-V; set-piece chests pass depth tier + 1). Returns null most of
     * the time. Uses ONLY the passed Random, in a fixed order (nextDouble, then nextInt only when
     * something is returned), so the result is deterministic for a seeded Random. Rank 1-2 at any
     * tier, rank 3 needs tier 3+, rank 4 needs tier 4+, rank 5 only at tier 5 (lowest weight).
     *
     * Phase 2: a roll that misses the trinket band may land in the supply band just above it and
     * return one consumable (Adrenaline Candy and Field Bandage anywhere, Stim Reagent from tier 2,
     * Full Restore from tier 3, Adrenaline Crystal from tier 4; rarer ones weigh more in harder
     * tiers). The trinket band and its nextInt pick are unchanged, so every seed that rolled a
     * trinket before still rolls the same trinket.
     */
    public static ItemStack rollLoot(Random random, int tier) {
        Object pick = pickAny(random, tier);
        if (pick instanceof GearItem) return GearItems.create((GearItem) pick);
        if (pick instanceof GearConsumable) return GearItems.create((GearConsumable) pick);
        return null;
    }

    /** GearItem, GearConsumable or null. */
    static Object pickAny(Random random, int tier) {
        if (random == null) return null;
        int t = Math.max(0, Math.min(5, tier));
        double roll = random.nextDouble();
        if (roll < CHANCE[t]) return pickGear(random, t);
        if (roll < CHANCE[t] + SUPPLY_CHANCE[t]) return pickSupply(random, t);
        return null;
    }

    /** The trinket part of a roll (null when the roll gave a consumable or nothing). */
    static GearItem pickLoot(Random random, int tier) {
        Object pick = pickAny(random, tier);
        return pick instanceof GearItem ? (GearItem) pick : null;
    }

    static GearConsumable pickSupply(Random random, int t) {
        int total = 0;
        for (GearConsumable c : GearConsumable.values()) total += c.weight(t);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (GearConsumable c : GearConsumable.values()) {
            roll -= c.weight(t);
            if (roll < 0) return c;
        }
        return null;
    }

    private static GearItem pickGear(Random random, int t) {
        List<GearItem> pool = new ArrayList<GearItem>();
        List<Integer> weights = new ArrayList<Integer>();
        int total = 0;
        for (GearItem item : GearItem.values()) {
            if (!eligible(item.rank, t)) continue;
            int w = lootWeight(item.rank, t);
            pool.add(item);
            weights.add(w);
            total += w;
        }
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    static boolean eligible(int rank, int tier) {
        return rank <= 2 || (rank == 3 && tier >= 3) || (rank == 4 && tier >= 4) || (rank == 5 && tier >= 5);
    }

    /** Hard tiers favour stronger gear; utility stays possible everywhere. */
    static int lootWeight(int rank, int tier) {
        if (rank <= 2) return tier >= 3 ? 2 : 4;
        return rank == 3 ? 3 : rank == 4 ? 2 : 1;
    }

    /** Fresh canonical item for a gear or consumable id, or null when the id is unknown. */
    public static ItemStack create(String gearId) {
        GearItem item = GearItem.byId(gearId);
        if (item != null) return GearItems.create(item);
        GearConsumable use = GearConsumable.byId(gearId);
        return use == null ? null : GearItems.create(use);
    }

    /** True for a genuine Phase 2 consumable (Adrenaline Candy, Field Bandage, ...). */
    public static boolean isConsumable(ItemStack stack) {
        return GearItems.consumable(stack) != null;
    }

    /** Consumable ids in catalogue order. */
    public static List<String> consumableIds() {
        List<String> out = new ArrayList<String>();
        for (GearConsumable c : GearConsumable.values()) out.add(c.id);
        return Collections.unmodifiableList(out);
    }

    /** True for a genuine gear item (identity is the NBT tag, never the name). */
    public static boolean isGear(ItemStack stack) {
        return GearItems.identify(stack) != null;
    }

    /** Gear id of the stack, or null. */
    public static String gearId(ItemStack stack) {
        GearItem item = GearItems.identify(stack);
        return item == null ? null : item.id;
    }

    /** Power rank 1..5 for an id, or 0 when unknown. */
    public static int rank(String gearId) {
        GearItem item = GearItem.byId(gearId);
        return item == null ? 0 : item.rank;
    }

    /** All gear ids in catalogue order. */
    public static List<String> ids() {
        List<String> out = new ArrayList<String>();
        for (GearItem item : GearItem.values()) out.add(item.id);
        return Collections.unmodifiableList(out);
    }
}
