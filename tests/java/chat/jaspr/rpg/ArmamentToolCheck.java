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
        // always enhanced, never below Uncommon, even with a zero chance
        Random random = new Random(3);
        int[] seen = new int[Rarity.values().length];
        for (int i = 0; i < 4000; i++) {
            ItemStack e = Armament.maybeEnhance(helmet.clone(), settings, random, 0.0);
            check(Armament.isEnhanced(e), "always enhanced");
            Rarity r = Armament.rarity(e);
            check(r.ordinal() >= Rarity.UNCOMMON.ordinal(), "never below uncommon: " + r);
            seen[r.ordinal()]++;
        }
        check(seen[Rarity.ANCIENT.ordinal()] > 0 && seen[Rarity.UNCOMMON.ordinal()] > seen[Rarity.RARE.ordinal()], "rarity spread");
        check(!Armament.isEnhanced(Armament.maybeEnhance(new ItemStack(Material.DIAMOND_HELMET), settings, random, 0.0)), "plain gear still rolls its chance");
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
