package chat.jaspr.gear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Offline check of the trinket roster (tests/gear-trinkets.test.cjs): 32 trinkets, eight realm-only (two per realm, no
 * recipe, never in loot), unique texture models outside the slot-icon band, the end-game recipe rule and distinct
 * patterns for the craftable ones, and the realm named on the item. Prints GEAR_OK when everything holds.
 */
public final class GearCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        GearItem[] all = GearItem.values();
        check(all.length == 32, "32 trinkets: " + all.length);
        Set<Integer> models = new HashSet<Integer>();
        Map<String, Integer> perRealm = new HashMap<String, Integer>();
        Set<String> patterns = new HashSet<String>();
        int realm = 0;
        for (GearItem item : all) {
            check(models.add(item.model) && item.model > 0 && item.model < 131
                && (item.model < GearItems.ICON_BASE_MODEL || item.model > GearItems.ICON_BASE_MODEL + 5), "unique model " + item.id);
            check(item.effects.length >= 1 && item.effects.length <= 3, "lore size " + item.id);
            String lore = GearItems.canonicalTag(item).toString();
            if (item.realm != null) {
                realm++;
                perRealm.merge(item.realm, 1, Integer::sum);
                check(!item.craftable() && !item.loot && item.ingredientMap().isEmpty(), "realm-only " + item.id);
                check(lore.contains("Found only in " + item.realmTitle()), "realm on the item " + item.id);
                check(item.rank >= 3, "realm trinkets are strong " + item.id);
                continue;
            }
            check(item.craftable() && !lore.contains("Found only in"), "craftable " + item.id);
            if (!item.loot) continue;   // the craft-only Blight Filter is deliberately cheap
            int star = 0, diamond = 0, emerald = 0;
            Map<Character, String> key = item.ingredientMap();
            StringBuilder grid = new StringBuilder();
            for (String row : item.shape) {
                for (char ch : row.toCharArray()) {
                    String m = key.get(ch);
                    grid.append(m == null ? "-" : m).append(',');
                    if (m == null) continue;
                    if (m.equals("NETHER_STAR")) star++;
                    if (m.equals("DIAMOND_BLOCK")) diamond++;
                    if (m.equals("EMERALD_BLOCK")) emerald++;
                    check(org.bukkit.Material.getMaterial(m.split(":")[0]) != null, "ingredient " + m + " of " + item.id);
                }
                grid.append('/');
            }
            check(star == 1 && diamond >= 2, "star + 2 diamond blocks " + item.id);
            check(item.rank < 3 || emerald >= 1, "emerald block from rank 3 " + item.id);
            check(item.rank < 4 || diamond >= 3, "3 diamond blocks from rank 4 " + item.id);
            check(item.rank < 5 || emerald >= 2, "2 emerald blocks at rank 5 " + item.id);
            check(patterns.add(grid.toString()), "distinct pattern " + item.id);
        }
        check(realm == 8, "eight realm trinkets: " + realm);
        for (String world : new String[]{"world_nether", "jaspr_ruins", "jaspr_atlas", "jaspr_levels"})
            check(perRealm.getOrDefault(world, 0) == 2 && GearItem.ofRealm(world).size() == 2, "two in " + world);
        check(GearItem.craftableCount() == 24, "24 craftable: " + GearItem.craftableCount());
        for (GearItem g : GearItem.LOOT) check(g.realm == null && g.loot, "loot pool " + g.id);
        Random r = new Random(5);
        int trinkets = 0;
        for (int i = 0; i < 200_000; i++) {
            Object pick = GearApi.pickAny(r, i % 6);
            if (!(pick instanceof GearItem)) continue;
            trinkets++;
            check(((GearItem) pick).realm == null, "structure loot rolled a realm trinket");
        }
        check(trinkets > 1000, "loot rolls trinkets: " + trinkets);
        System.out.println("GEAR_OK trinkets=" + all.length + " realm=" + realm + " craftable=" + GearItem.craftableCount() + " lootRolls=" + trinkets);
    }
}
