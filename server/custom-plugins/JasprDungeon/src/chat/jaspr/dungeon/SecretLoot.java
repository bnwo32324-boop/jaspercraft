package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * What the secrets hand out. Every item comes from the dungeon's own factories (Rewards.gear, Relics.create, Arsenal.create and
 * the room's loot profile), scaled by the floor (Floors.loot), and is drawn from the room's hash, so a given cache or purse is the
 * same every time. Emeralds are the peddler's money: caches, purses and hoards are where a run finds them. Needs a server (item
 * meta, NBT), so the static audit leaves it to the runtime; the numbers it uses live in SecretCatalog.
 */
final class SecretLoot {
    private SecretLoot() {}

    /** Items to drop and experience to give. */
    static final class Haul {
        final List<ItemStack> items = new ArrayList<ItemStack>();
        int exp;
        boolean relic;
    }

    private static int scaled(int base, double mult) { return Math.max(1, (int) Math.round(base * mult)); }
    private static ItemStack stack(Material m, int n) { return new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n))); }
    private static Random rng(Layout.Room r, long salt) { return new Random(r.hash ^ salt); }

    private static ItemStack gear(Layout.Room r, Random rng) {
        LootCatalog.Profile p = LootCatalog.profile(r.theme);
        return Rewards.gear(p, p.gear.get(rng.nextInt(p.gear.size())), r.tier, r.floor >= 2, false);
    }
    /** A relic of the room's own pool (the ranks its threat allows), or null. */
    static ItemStack relic(Layout.Room r, Random rng) {
        List<LootCatalog.Bauble> pool = LootCatalog.pool(r.theme, r.tier, false);
        // Generation 7: Floors II and III hold only their own, higher-ranked relics; a quiet room there offers the floor's lowest rank.
        for (int t = r.tier + 1; pool.isEmpty() && t <= 5; t++) pool = LootCatalog.pool(r.theme, t, false);
        return pool.isEmpty() ? null : Relics.create(Relics.Type.valueOf(pool.get(rng.nextInt(pool.size())).name()));
    }
    /** An armoury piece of at most this rank, or null. */
    static ItemStack armoury(Layout.Room r, Random rng, int maxRank) {
        List<ArmoryCatalog.Type> pool = new ArrayList<ArmoryCatalog.Type>();
        for (ArmoryCatalog.Type t : ArmoryCatalog.Type.values()) if (t.rank <= maxRank && ArmoryCatalog.eligible(t, r.tier, "BATTLE")) pool.add(t);
        return pool.isEmpty() ? null : Arsenal.create(pool.get(rng.nextInt(pool.size())));
    }
    private static void relic(Haul h, Layout.Room r, Random rng, int percent) {
        if (rng.nextInt(100) >= percent) return;
        ItemStack relic = relic(r, rng);
        if (relic != null) { h.items.add(relic); h.relic = true; }
    }

    /** The loose stone's cache: provisions, the theme's essence, iron, now and then emeralds, a piece of gear, a relic or an armoury piece; and experience. */
    static Haul cache(Layout.Room r) {
        Random rng = rng(r, 0x43616368654c6f74L);
        double mult = Floors.loot(r.floor);
        Haul h = new Haul();
        LootCatalog.Profile profile = LootCatalog.profile(r.theme);
        h.items.add(stack(Material.valueOf(profile.provision), scaled(3 + rng.nextInt(4), mult)));
        h.items.add(stack(Material.valueOf(profile.essence), scaled(2 + rng.nextInt(3), mult)));
        h.items.add(stack(Material.IRON_INGOT, scaled(2 + rng.nextInt(3), mult)));
        if (rng.nextInt(100) < 35) h.items.add(stack(Material.EMERALD, 1 + rng.nextInt(2) + (r.floor >= 3 ? 1 : 0)));
        if (rng.nextInt(100) < 25) h.items.add(gear(r, rng));
        relic(h, r, rng, r.floor == 1 ? 14 : r.floor == 2 ? 18 : 22);
        if (r.floor >= 2 && rng.nextInt(100) < 10) { ItemStack gun = armoury(r, rng, 2); if (gun != null) h.items.add(gun); }
        h.exp = scaled(10 + rng.nextInt(11), mult);
        return h;
    }
    /** A caught thief's purse: emeralds, bottles o' enchanting and iron, and sometimes a relic. */
    static Haul thiefPurse(Layout.Room r) {
        Random rng = rng(r, 0x5468696566507572L);
        double mult = Floors.loot(r.floor);
        Haul h = new Haul();
        h.items.add(stack(Material.EMERALD, 3 + r.floor + rng.nextInt(3)));
        h.items.add(stack(Material.EXP_BOTTLE, scaled(3 + rng.nextInt(4), mult)));
        h.items.add(stack(Material.IRON_INGOT, scaled(3 + rng.nextInt(5), mult)));
        relic(h, r, rng, 18);
        h.exp = scaled(8 + rng.nextInt(8), mult);
        return h;
    }
    /** What a mimic leaves: a piece of gear, emeralds, experience and good odds of a relic. */
    static Haul mimicHoard(Layout.Room r) {
        Random rng = rng(r, 0x4d696d6963486f72L);
        double mult = Floors.loot(r.floor);
        Haul h = new Haul();
        h.items.add(gear(r, rng));
        h.items.add(stack(Material.EMERALD, 2 + rng.nextInt(3) + r.floor));
        h.items.add(stack(Material.EXP_BOTTLE, scaled(4 + rng.nextInt(4), mult)));
        relic(h, r, rng, 40);
        h.exp = scaled(14 + rng.nextInt(10), mult);
        return h;
    }
    /** Dinnerbone's guard: emeralds, iron, perhaps gear or an armoury piece. */
    static Haul guardPurse(Layout.Room r) {
        Random rng = rng(r, 0x4775617264507572L);
        double mult = Floors.loot(r.floor);
        Haul h = new Haul();
        h.items.add(stack(Material.EMERALD, 3 + rng.nextInt(3) + (r.floor >= 2 ? 1 : 0)));
        h.items.add(stack(Material.IRON_INGOT, scaled(4 + rng.nextInt(4), mult)));
        if (rng.nextInt(100) < 40) h.items.add(gear(r, rng));
        if (rng.nextInt(100) < 30) { ItemStack gun = armoury(r, rng, 2); if (gun != null) h.items.add(gun); }
        h.exp = scaled(10 + rng.nextInt(8), mult);
        return h;
    }
    /** Monstro: a piece of gear for certain, emeralds, bottles and a fair chance of a relic. */
    static Haul monstroPurse(Layout.Room r) {
        Random rng = rng(r, 0x4d6f6e7374726f21L);
        double mult = Floors.loot(r.floor);
        Haul h = new Haul();
        h.items.add(gear(r, rng));
        h.items.add(stack(Material.EMERALD, 4 + rng.nextInt(4) + r.floor));
        h.items.add(stack(Material.EXP_BOTTLE, scaled(6 + rng.nextInt(5), mult)));
        relic(h, r, rng, 30);
        h.exp = scaled(18 + rng.nextInt(10), mult);
        return h;
    }

    /** The sword in the stone: good, not overpowered (Sharpness II or III, Unbreaking II or III), named for its floor. */
    static ItemStack sword(Layout.Room r) {
        ItemStack s = new ItemStack(r.floor >= 2 ? Material.DIAMOND_SWORD : Material.IRON_SWORD);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(ChatColor.AQUA + (r.floor >= 3 ? "Abyssdrawn Blade" : r.floor == 2 ? "Deepdrawn Blade" : "Stonedrawn Blade"));
        // Two lore lines, the second the dungeon's gear mark, so the relics that mend ordinary dungeon gear mend this too.
        m.setLore(Arrays.asList(ChatColor.GRAY + "Drawn from the stone of " + Floors.title(r.floor) + ".", ChatColor.DARK_GRAY + Rewards.GEAR_ORIGIN + Math.max(0, Math.min(5, r.tier))));
        s.setItemMeta(m);
        s.addEnchantment(Enchantment.DAMAGE_ALL, r.floor >= 3 ? 3 : 2);
        s.addEnchantment(Enchantment.DURABILITY, r.floor >= 3 ? 3 : 2);
        return s;
    }

    // ---------------------------------------------------------------- the peddler
    private static ItemStack wares(Layout.Room r, SecretCatalog.Trade t) {
        if (t.id.equals("healing")) {
            ItemStack p = new ItemStack(Material.POTION);
            PotionMeta pm = (PotionMeta) p.getItemMeta();
            pm.setBasePotionData(new PotionData(PotionType.INSTANT_HEAL, false, true));
            pm.setDisplayName(ChatColor.LIGHT_PURPLE + "Peddler's Tonic");
            pm.setLore(Arrays.asList(ChatColor.GRAY + "Healing II, brewed somewhere that has no name."));
            p.setItemMeta(pm);
            return p;
        }
        if (t.id.equals("arrows")) return stack(Material.ARROW, t.gets);
        if (t.id.equals("ammo")) return Arsenal.createAmmo(Math.max(1, Math.min(64, t.gets)));
        // The relic of the peddler's room: the same one every time, preferring rank 2 and up.
        List<LootCatalog.Bauble> pool = LootCatalog.pool(r.theme, r.tier, false), good = new ArrayList<LootCatalog.Bauble>();
        for (LootCatalog.Bauble b : pool) if (b.rank >= 2) good.add(b);
        if (good.isEmpty()) good = pool;
        LootCatalog.Bauble pick = good.get((int) Math.floorMod(SecretCatalog.relicDraw(r, 1), (long) good.size()));
        return Relics.create(Relics.Type.valueOf(pick.name()));
    }
    /** The peddler's trades with the uses already spent (uses may be null or short). */
    static List<MerchantRecipe> trades(Layout.Room r, int[] uses) {
        List<MerchantRecipe> out = new ArrayList<MerchantRecipe>();
        for (int i = 0; i < SecretCatalog.TRADES.length; i++) {
            SecretCatalog.Trade t = SecretCatalog.TRADES[i];
            int max = SecretCatalog.uses(t, r.floor), spent = uses == null || i >= uses.length ? 0 : Math.max(0, Math.min(max, uses[i]));
            MerchantRecipe rec = new MerchantRecipe(wares(r, t), spent, max, false);
            rec.addIngredient(new ItemStack(Material.EMERALD, t.emeralds));
            if (t.bottles > 0) rec.addIngredient(new ItemStack(Material.EXP_BOTTLE, t.bottles));
            out.add(rec);
        }
        return out;
    }
}
