package chat.jaspr.gear;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Offline check of the realm armouries (tests/realm-armory.test.cjs): six sets of nine pieces, identity only by NBT,
 * models that never collide with the expedition gear or the guns, stats above diamond, forging that keeps enchantments
 * and Armaments records but refuses special items, realm materials that are recognised only when genuine, and chest
 * rolls that are deterministic, realm-bound and leave every older loot band untouched. Prints ARMORY_OK.
 */
public final class ArmoryCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** Bukkit's item meta needs a server's item factory; a proxy server answers with CraftBukkit's own. */
    private static void fakeServer() {
        org.bukkit.Server server = (org.bukkit.Server) java.lang.reflect.Proxy.newProxyInstance(ArmoryCheck.class.getClassLoader(),
            new Class<?>[]{org.bukkit.Server.class}, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "getItemFactory": return org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemFactory.instance();
                    case "getLogger": return java.util.logging.Logger.getLogger("ArmoryCheck");
                    case "getName": case "getVersion": case "getBukkitVersion": return "offline";
                    default:
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class) return 0;
                        if (t == long.class) return 0L;
                        if (t == double.class) return 0.0;
                        if (t == float.class) return 0f;
                        return null;
                }
            });
        org.bukkit.Bukkit.setServer(server);
    }

    /** Pieces made before the helmet and boots went from 3 to 4 armour are brought up to date in place, and nothing else changes. */
    static void upgrades() {
        for (ArmorySet set : ArmorySet.values()) for (ArmoryPiece piece : ArmoryPiece.values()) {
            ItemStack canonical = ArmoryItems.create(set, piece);
            check(ArmoryItems.upgraded(canonical) == null, "a current piece needs nothing " + set.id + "_" + piece.id);
            if (!piece.armour()) continue;
            // an old piece: the armour amount as it was (3 for the helmet and boots), with enchantments, anvil cost and an Armaments record
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(canonical);
            NBTTagCompound tag = nms.getTag();
            NBTTagList mods = tag.getList("AttributeModifiers", 10);
            for (int i = 0; i < mods.size(); i++) if (mods.get(i).getString("AttributeName").equals("generic.armor")) mods.get(i).setDouble("Amount", piece.armor - 1);
            NBTTagList ench = new NBTTagList();
            NBTTagCompound prot = new NBTTagCompound();
            prot.setShort("id", (short) 0);
            prot.setShort("lvl", (short) 3);
            ench.add(prot);
            tag.set("ench", ench);
            tag.setInt("RepairCost", 7);
            NBTTagCompound armament = new NBTTagCompound();
            armament.setInt("Level", 2);
            tag.set("JasprArmament", armament);
            ItemStack old = CraftItemStack.asBukkitCopy(nms);
            check(ArmoryItems.identify(old) != null, "an old piece is still recognised " + set.id + "_" + piece.id);
            ItemStack up = ArmoryItems.upgraded(old);
            check(up != null, "an old piece is upgraded " + set.id + "_" + piece.id);
            NBTTagCompound out = CraftItemStack.asNMSCopy(up).getTag();
            double armour = -1;
            NBTTagList after = out.getList("AttributeModifiers", 10);
            for (int i = 0; i < after.size(); i++) if (after.get(i).getString("AttributeName").equals("generic.armor")) armour = after.get(i).getDouble("Amount");
            check(armour == piece.armor && after.size() == 3, "upgraded armour amount " + set.id + "_" + piece.id + ": " + armour);
            check(ArmoryItems.identify(up) != null && ArmoryItems.identify(up).piece == piece && ArmoryItems.identify(up).set == set, "identity survives " + set.id + "_" + piece.id);
            check(out.getList("ench", 10).size() == 1 && out.getInt("RepairCost") == 7 && out.getCompound("JasprArmament").getInt("Level") == 2
                && out.getCompound("display").getList("Lore", 8).toString().equals(tag.getCompound("display").getList("Lore", 8).toString()), "enchantments, anvil cost, armament and lore kept " + set.id + "_" + piece.id);
            check(ArmoryItems.upgraded(up) == null, "upgrading twice changes nothing " + set.id + "_" + piece.id);
        }
        check(ArmoryItems.upgraded(null) == null && ArmoryItems.upgraded(new ItemStack(Material.DIAMOND_HELMET)) == null, "nothing else is touched");
    }

    public static void main(String[] args) {
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        fakeServer();
        check(ArmorySet.values().length == 6 && ArmoryPiece.values().length == 9, "six sets of nine");
        Set<String> names = new HashSet<String>();
        for (ArmorySet set : ArmorySet.values()) {
            int armour = 0;
            double toughness = 0;
            for (ArmoryPiece piece : ArmoryPiece.values()) {
                ItemStack item = ArmoryItems.create(set, piece);
                ArmoryItems.Id id = ArmoryItems.identify(item);
                check(id != null && id.set == set && id.piece == piece, "identity " + set.id + "_" + piece.id);
                check(item.getType() == piece.base && item.getDurability() == set.model(), "base and model " + set.id + "_" + piece.id);
                check(set.model() + 1 < piece.baseDurability(), "fallback fits " + set.id + "_" + piece.id);
                check(names.add(ArmoryItems.name(set, piece)), "unique name " + set.id + "_" + piece.id);
                NBTTagCompound tag = CraftItemStack.asNMSCopy(item).getTag();
                check(tag.getBoolean("Unbreakable"), "unbreakable " + set.id + "_" + piece.id);
                NBTTagList mods = tag.getList("AttributeModifiers", 10);
                for (int i = 0; i < mods.size(); i++) {
                    NBTTagCompound m = mods.get(i);
                    if (m.getString("AttributeName").equals("generic.armor")) armour += (int) m.getDouble("Amount");
                    if (m.getString("AttributeName").equals("generic.armorToughness")) toughness += m.getDouble("Amount");
                    check(m.getString("Slot").equals(piece.slot), "slot " + set.id + "_" + piece.id);
                }
                // every armour piece is better than its diamond counterpart (3/8/6/3), the helmet and boots included
                if (piece.armour()) check(piece.armor > new int[]{3, 8, 6, 3}[piece.armorIndex()] && ArmoryPiece.TOUGHNESS > 2.0, "armour piece beats diamond " + piece.id);
                if (piece == ArmoryPiece.SWORD) check(piece.damage + 1 > 7, "sword beats diamond");
                if (piece == ArmoryPiece.AXE) check(piece.damage + 1 > 9, "axe beats diamond");
                String lore = tag.getCompound("display").getList("Lore", 8).toString();
                check(lore.contains(set.realmTitle), "realm on the item " + set.id + "_" + piece.id);
                // the bare, damaged-vanilla and wrong-model look-alikes are never armoury items
                ItemStack fake = new ItemStack(piece.base, 1, (short) set.model());
                check(ArmoryItems.identify(fake) == null, "untagged look-alike " + set.id + "_" + piece.id);
            }
            check(armour == 24 && Math.abs(toughness - 12.0) < 1e-9, "set totals " + set.id + ": " + armour + "/" + toughness + " (diamond 20/8)");
        }
        // models: even values 100..110 with the odd one above each free for the vanilla fallback; clear of the expedition
        // armour (10-41), the expedition melee band (1140+) and the guns (1160+)
        Set<Integer> models = new HashSet<Integer>();
        for (ArmorySet set : ArmorySet.values()) {
            check(models.add(set.model()) && !models.contains(set.model() + 1), "model " + set.id);
            check(set.model() > 41 && set.model() + 1 < 363 && set.model() < 1140, "model band " + set.id);
        }
        // worlds
        check(ArmorySet.ofWorld("world") == ArmorySet.EMERALD && ArmorySet.ofWorld("world_nether") == ArmorySet.BLAZEFORGED
            && ArmorySet.ofWorld("jaspr_ruins") == ArmorySet.ABYSSAL && ArmorySet.ofWorld("jaspr_atlas") == ArmorySet.TITAN
            && ArmorySet.ofWorld("jaspr_levels") == ArmorySet.LIMINAL && ArmorySet.ofWorld("jaspr_backrooms") == ArmorySet.LIMINAL
            && ArmorySet.ofWorld("world_the_end") == ArmorySet.VOID && ArmorySet.ofWorld("lobby") == null, "realm of each world");
        // materials
        int custom = 0;
        for (ArmorySet set : ArmorySet.values()) {
            if (!set.customMaterial()) { check(set == ArmorySet.EMERALD || set.corner != null && set.edge != null, "forge goods " + set.id); continue; }
            custom++;
            ItemStack m = ArmoryItems.material(set.materialId, 3);
            check(set.materialId.equals(ArmoryItems.materialOf(m)) && m.getAmount() == 3 && m.getType() == set.corner, "material " + set.materialId);
            check(ArmoryItems.materialOf(new ItemStack(set.corner, 3)) == null, "plain " + set.corner + " is no " + set.materialId);
            check(ArmoryItems.identify(m) == null && ArmoryItems.marked(m), "material is marked but no gear " + set.materialId);
        }
        check(custom == 3, "three realm-only materials: " + custom);
        // forging keeps enchantments and the Armaments record, and writes the record under the header
        ItemStack diamond = new ItemStack(Material.DIAMOND_CHESTPLATE, 1, (short) 200);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(diamond);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList ench = new NBTTagList();
        NBTTagCompound prot = new NBTTagCompound();
        prot.setShort("id", (short) 0);
        prot.setShort("lvl", (short) 4);
        ench.add(prot);
        tag.set("ench", ench);
        NBTTagCompound armament = new NBTTagCompound();
        armament.setInt("Level", 4);
        armament.setString("Rarity", "RARE");
        tag.set("JasprArmament", armament);
        NBTTagCompound display = new NBTTagCompound();
        NBTTagList lines = new NBTTagList();
        lines.add(new net.minecraft.server.v1_12_R1.NBTTagString("§9Rare§7  Level §f4"));
        display.set("Lore", lines);
        tag.set("display", display);
        nms.setTag(tag);
        ItemStack enhanced = CraftItemStack.asBukkitCopy(nms);
        check(Armory.plainDiamond(enhanced), "an enhanced, enchanted, worn diamond piece is accepted");
        ItemStack forged = ArmoryItems.forge(ArmorySet.VOID, ArmoryPiece.CHESTPLATE, enhanced);
        NBTTagCompound out = CraftItemStack.asNMSCopy(forged).getTag();
        check(ArmoryItems.identify(forged) != null && out.getList("ench", 10).size() == 1 && out.getCompound("JasprArmament").getInt("Level") == 4,
            "forge keeps enchantments and armament");
        String forgedLore = out.getCompound("display").getList("Lore", 8).toString();
        check(forgedLore.contains("- Armament -") && forgedLore.contains("Level") && forgedLore.indexOf("Void armoury") < forgedLore.indexOf("- Armament -"),
            "armament lines under the header");
        check(!Armory.plainDiamond(ArmoryItems.create(ArmorySet.EMERALD, ArmoryPiece.CHESTPLATE)), "armoury piece is no diamond piece");
        upgrades();
        net.minecraft.server.v1_12_R1.ItemStack marked = CraftItemStack.asNMSCopy(new ItemStack(Material.DIAMOND_SWORD));
        NBTTagCompound other = new NBTTagCompound();
        other.set("JasprApocalypse", new NBTTagCompound());
        marked.setTag(other);
        check(!Armory.plainDiamond(CraftItemStack.asBukkitCopy(marked)), "another plugin's diamond item is refused");
        check(Armory.plainDiamond(new ItemStack(Material.DIAMOND_HOE)) && !Armory.plainDiamond(new ItemStack(Material.IRON_HOE)), "plain diamond only");
        // realm chest rolls: deterministic, realm-bound, rising with tier
        for (String world : new String[]{"world_nether", "jaspr_ruins", "jaspr_atlas", "jaspr_levels", "world_the_end", "world"}) {
            ArmorySet set = ArmorySet.ofWorld(world);
            for (int tier = 0; tier <= 5; tier++) {
                Random a = new Random(99L + tier), b = new Random(99L + tier);
                int pieces = 0, mats = 0, n = 40000;
                for (int i = 0; i < n; i++) {
                    List<ItemStack> x = Armory.roll(a, world, tier), y = Armory.roll(b, world, tier);
                    check(x.size() == y.size(), "determinism " + world + " " + tier);
                    for (int k = 0; k < x.size(); k++) {
                        ArmoryItems.Id ix = ArmoryItems.identify(x.get(k)), iy = ArmoryItems.identify(y.get(k));
                        if (ix != null) { pieces++; check(iy != null && ix.key().equals(iy.key()) && ix.set == set, "same piece of the realm " + world); }
                        else { mats++; check(set.materialId.equals(ArmoryItems.materialOf(x.get(k))), "realm material " + world); }
                    }
                }
                double rate = pieces / (double) n, expect = 0.015 + 0.012 * tier;
                check(Math.abs(rate - expect) < 0.006, "piece rate " + world + " tier " + tier + " = " + rate);
                check(set.customMaterial() ? mats > 0 : mats == 0, "materials only where the realm has one " + world);
            }
        }
        check(Armory.roll(new Random(1), "lobby", 5).isEmpty() && Armory.roll(null, "world", 5).isEmpty(), "no roll outside the realms");
        // the overworld structure band sits above every older band and never changes them
        Random r = new Random(5);
        int emerald = 0, n = 300000;
        for (int i = 0; i < n; i++) {
            int tier = i % 6;
            Random a = new Random(i * 7919L), b = new Random(i * 7919L);
            Object now = GearApi.pickAny(a, tier), old = legacy(b, tier);
            if (old != null) check(now == old, "older bands unchanged");
            else if (now != null) { check(now instanceof ArmoryPiece, "only an armoury piece above the old bands"); emerald++; }
        }
        double expect = 0;
        for (double c : GearApi.ARMORY_CHANCE) expect += c / 6;
        check(Math.abs(emerald / (double) n - expect) < 0.002, "armoury band rate " + emerald / (double) n);
        ItemStack fromApi = GearApi.armoryPiece("titan", "pickaxe");
        check("titan".equals(GearApi.armorySet(fromApi)) && GearApi.armoryPiece("nope", "pickaxe") == null && GearApi.armorySet(null) == null, "api");
        System.out.println("ARMORY_OK sets=" + ArmorySet.values().length + " pieces=" + ArmorySet.values().length * ArmoryPiece.values().length
            + " materials=" + custom + " overworldBand=" + emerald);
    }

    /** pickAny as it was before the armoury band (4.0.0). */
    private static Object legacy(Random random, int tier) {
        int t = Math.max(0, Math.min(5, tier));
        double roll = random.nextDouble();
        if (roll < GearApi.CHANCE[t]) return GearApi.pickGear(random, t);
        if (roll < GearApi.CHANCE[t] + GearApi.SUPPLY_CHANCE[t]) return GearApi.pickSupply(random, t);
        if (roll < GearApi.CHANCE[t] + GearApi.SUPPLY_CHANCE[t] + GearApi.BACKPACK_CHANCE[t]) return GearApi.pickBackpack(random);
        return null;
    }
}
