package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.NBTTagString;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Offline check of the realm armouries in the Armaments system (tests/realm-armory-armaments.test.cjs): armoury pieces
 * are always enhanced and never below Uncommon, their own lore stays above the "- Armament -" header through every
 * rewrite, realm tools (and only realm tools by default) are armaments with the five-ability tool roster. ARMAMENT_OK.
 */
public final class ArmamentToolCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    private static void fakeServer() {
        org.bukkit.Server server = (org.bukkit.Server) java.lang.reflect.Proxy.newProxyInstance(ArmamentToolCheck.class.getClassLoader(),
            new Class<?>[]{org.bukkit.Server.class}, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "getItemFactory": return org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemFactory.instance();
                    case "getLogger": return java.util.logging.Logger.getLogger("ArmamentToolCheck");
                    case "getName": case "getVersion": case "getBukkitVersion": return "offline";
                    default:
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class) return 0;
                        if (t == long.class) return 0L;
                        if (t == double.class) return 0.0;
                        return null;
                }
            });
        org.bukkit.Bukkit.setServer(server);
    }

    /** What JasprGear makes: an unbreakable diamond item, JasprArmory:{set, piece}, its own lore lines. */
    static ItemStack armory(Material base, String piece) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(new ItemStack(base, 1, (short) 106));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("set", "titan");
        mine.setString("piece", piece);
        tag.set("JasprArmory", mine);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "§eTitan " + piece);
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString("§8Titan armoury of Atlas"));
        lore.add(new NBTTagString("§7Titan's Strength: veins of ore"));
        display.set("Lore", lore);
        tag.set("display", display);
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** An item another plugin made: a material, a name and lore lines of its own (a Dungeon Dimension weapon says where it came from). */
    static ItemStack foreign(Material type, String name, String... lines) {
        ItemStack item = new ItemStack(type);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(lines));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Owner 2026-10-08: a Vermilion diamond sword from the Dungeon Dimension earned no armament points ("Fix this globally ... I think
     * 100% of weapons should have armament capabilities"). Every eligible item becomes an armament however it turned up; a weapon's
     * own lore survives under the "- Armament -" header; a plain weapon enhanced before keeps exactly the lore it had.
     */
    static void everyItemIsAnArmament(RpgConfig settings, Random random) {
        int[] rarities = new int[Rarity.values().length];
        for (Material m : new Material[]{Material.DIAMOND_SWORD, Material.WOOD_SWORD, Material.IRON_AXE, Material.BOW, Material.GOLD_SWORD,
                Material.LEATHER_BOOTS, Material.CHAINMAIL_CHESTPLATE, Material.IRON_LEGGINGS, Material.DIAMOND_HELMET}) {
            for (int i = 0; i < 300; i++) {
                ItemStack e = Armament.ensure(new ItemStack(m), settings, random);
                check(Armament.isEnhanced(e) && Armament.level(e) == 1 && Armament.tokens(e) == 0, m + " is always an armament");
                rarities[Armament.rarity(e).ordinal()]++;
            }
        }
        check(rarities[Rarity.DEFAULT.ordinal()] == 0 && rarities[Rarity.BASIC.ordinal()] > rarities[Rarity.UNCOMMON.ordinal()]
            && rarities[Rarity.ANCIENT.ordinal()] > 0, "rarity is still rolled, Basic most often: " + java.util.Arrays.toString(rarities));
        // Things that are not gear are left alone, and so is an armament (its record is never re-rolled).
        ItemStack dirt = new ItemStack(Material.DIRT), pick = new ItemStack(Material.DIAMOND_PICKAXE);
        check(Armament.ensure(dirt, settings, random) == dirt && Armament.ensure(pick, settings, random) == pick, "dirt and a plain pickaxe (realm mode) stay as they are");
        ItemStack once = Armament.ensure(new ItemStack(Material.IRON_SWORD), settings, random), again = Armament.setLevel(once, 4, 7, 2);
        check(Armament.ensure(again, settings, random) == again && Armament.level(again) == 4 && Armament.tokens(again) == 2, "an armament keeps its level and tokens");
        // The owner's sword: its own lines stay first, then one header, then the armament block - through every level-up.
        ItemStack sword = foreign(Material.DIAMOND_SWORD, "§dVermilion diamond sword", "§7Recovered from the Vermilion Court.", "§8The Dungeon Dimension | Threat 4");
        ItemStack armed = Armament.ensure(sword, settings, random);
        check(Armament.isEnhanced(armed), "the Dungeon sword is an armament");
        for (int i = 0; i < 4; i++) armed = Armament.setLevel(armed, 2 + i, 0, i);
        armed = Armament.setAbilityLevel(armed, AbilityType.FIRE, 1, 0);
        List<String> lore = armed.getItemMeta().getLore();
        check(lore.get(0).contains("Recovered from the Vermilion Court") && lore.get(1).contains("Threat 4"), "its own lines first: " + lore);
        check(lore.get(2).equals(Armament.ARMORY_HEADER) && Armament.isRarityLine(lore.get(3)) && lore.get(3).endsWith("5"), "then the header and the block: " + lore);
        int headers = 0;
        for (String line : lore) if (line.equals(Armament.ARMORY_HEADER)) headers++;
        check(headers == 1 && lore.toString().contains("Fire"), "one header, abilities listed: " + lore);
        check(armed.getItemMeta().getDisplayName().contains("Vermilion"), "its name is kept");
        // A plain weapon enhanced before 2026-10-08 (its lore is all ours, starting with the rarity line) reads exactly as before.
        ItemStack plain = Armament.enhance(new ItemStack(Material.STONE_SWORD), Rarity.RARE);
        List<String> before = plain.getItemMeta().getLore();
        check(Armament.isRarityLine(before.get(0)) && !before.contains(Armament.ARMORY_HEADER), "a plain weapon's block has no header: " + before);
        ItemStack levelled = Armament.setLevel(plain, 3, 0, 2);
        List<String> after = levelled.getItemMeta().getLore();
        check(Armament.isRarityLine(after.get(0)) && after.get(0).endsWith("3") && !after.contains(Armament.ARMORY_HEADER)
            && after.size() == before.size() + 1, "and keeps that shape when it levels (plus its token line): " + after);
        // Another plugin rewrites the lore without our block: its lines are kept and the block comes back under the header.
        ItemStack rewritten = levelled.clone();
        org.bukkit.inventory.meta.ItemMeta m = rewritten.getItemMeta();
        m.setLore(java.util.Arrays.asList("§7Blessed by a shrine"));
        rewritten.setItemMeta(m);
        List<String> back = Armament.setLevel(rewritten, 4, 0, 2).getItemMeta().getLore();
        check(back.get(0).contains("Blessed") && back.get(1).equals(Armament.ARMORY_HEADER) && Armament.isRarityLine(back.get(2)) && back.get(2).endsWith("4"), "rewritten lore: " + back);
        // Armour armaments switched off: armour stays ordinary, weapons do not.
        org.bukkit.configuration.file.YamlConfiguration off = new org.bukkit.configuration.file.YamlConfiguration();
        off.set("armaments.armor", false);
        RpgConfig noArmour = new RpgConfig(off);
        ItemStack boots = new ItemStack(Material.IRON_BOOTS);
        check(Armament.ensure(boots, noArmour, random) == boots && Armament.isEnhanced(Armament.ensure(new ItemStack(Material.IRON_SWORD), noArmour, random)),
            "armaments.armor: false keeps armour ordinary only");
    }

    public static void main(String[] args) {
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        fakeServer();
        RpgConfig settings = new RpgConfig(new YamlConfiguration());
        check("realm".equals(settings.toolsMode), "tools default realm");
        Armament.toolsMode = settings.toolsMode;
        ItemStack pick = armory(Material.DIAMOND_PICKAXE, "pickaxe");
        ItemStack helmet = armory(Material.DIAMOND_HELMET, "helmet");
        check(Armament.isArmory(pick) && Armament.isArmory(helmet) && !Armament.isArmory(new ItemStack(Material.DIAMOND_PICKAXE)), "armoury by NBT");
        check(Armament.isTool(pick) && Armament.isEligible(pick), "realm pickaxe is an armament");
        check(!Armament.isTool(new ItemStack(Material.DIAMOND_PICKAXE)) && !Armament.isEligible(new ItemStack(Material.DIAMOND_PICKAXE)),
            "plain pickaxe is not (realm mode)");
        Armament.toolsMode = "all";
        check(Armament.isTool(new ItemStack(Material.IRON_SPADE)), "all tools mode");
        Armament.toolsMode = "off";
        check(!Armament.isTool(pick), "off mode");
        Armament.toolsMode = "realm";
        check(!Armament.isTool(new ItemStack(Material.DIAMOND_SWORD)) && Armament.isWeapon(armory(Material.DIAMOND_SWORD, "sword")), "swords stay weapons");
        // always enhanced, never below Uncommon
        Random random = new Random(3);
        int[] seen = new int[Rarity.values().length];
        for (int i = 0; i < 4000; i++) {
            ItemStack e = Armament.ensure(helmet.clone(), settings, random);
            check(Armament.isEnhanced(e), "always enhanced");
            Rarity r = Armament.rarity(e);
            check(r.ordinal() >= Rarity.UNCOMMON.ordinal(), "never below uncommon: " + r);
            seen[r.ordinal()]++;
        }
        check(seen[Rarity.ANCIENT.ordinal()] > 0 && seen[Rarity.UNCOMMON.ordinal()] > seen[Rarity.RARE.ordinal()], "rarity spread");
        everyItemIsAnArmament(settings, random);
        // lore: realm lines stay above the header through repeated rewrites
        ItemStack enhanced = Armament.enhance(pick.clone(), Rarity.RARE);
        for (int i = 0; i < 3; i++) enhanced = Armament.setLevel(enhanced, 2 + i, 0, i);
        List<String> lore = enhanced.getItemMeta().getLore();
        check(lore.get(0).contains("Titan armoury") && lore.get(1).contains("Titan's Strength"), "own lines first: " + lore);
        int headers = 0;
        for (String line : lore) if (line.equals(Armament.ARMORY_HEADER)) headers++;
        check(headers == 1 && lore.indexOf(Armament.ARMORY_HEADER) == 2, "one header under the own lines: " + lore);
        check(lore.get(3).contains("Level") && lore.toString().contains("ore yield"), "armament lines under the header: " + lore);
        // the tool roster
        List<AbilityType> tools = AbilityType.forTools();
        List<String> keys = new ArrayList<String>();
        for (AbilityType t : tools) keys.add(t.key());
        check(tools.size() == 5 && keys.contains("excavation") && keys.contains("prospecting") && keys.contains("experienced")
            && keys.contains("replanting") && keys.contains("treasure_hunter"), "tool roster " + keys);
        check(ArmamentMenu.rosterFor(enhanced).equals(tools)
            && ArmamentMenu.rosterFor(Armament.enhance(helmet.clone(), Rarity.RARE)).equals(AbilityType.forArmour()), "menu rosters");
        ItemStack ability = Armament.setAbilityLevel(enhanced, AbilityType.PROSPECTING, 2, 0);
        check(Armament.abilityLevel(ability, AbilityType.PROSPECTING) == 2 && ability.getItemMeta().getLore().get(0).contains("Titan armoury"),
            "ability keeps the lore");
        check(ToolArmaments.natural(Material.STONE) && ToolArmaments.natural(Material.DIAMOND_ORE) && !ToolArmaments.natural(Material.WOOD), "natural ground");
        System.out.println("ARMAMENT_OK tools=" + tools.size() + " uncommonFloor=" + seen[Rarity.UNCOMMON.ordinal()] + " ancient=" + seen[Rarity.ANCIENT.ordinal()]);
    }
}
