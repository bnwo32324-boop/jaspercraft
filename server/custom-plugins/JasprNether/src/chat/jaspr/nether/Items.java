package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * BetterNether / NetherEx items as vanilla items carrying NBT {JasprNether:{id:"..."}}, a name, lore and (for special
 * items) a hidden Unbreaking I glint. Bases are chosen so the stand-in is harmless if used as its vanilla self.
 */
final class Items {
    private Items() {}

    static final class Def {
        final String id, name, mod; final Material mat; final short data; final boolean glint; final String[] lore;
        final int armor; final double toughness; final Color colour; final double attack, speed; final int efficiency;
        Def(String id, Material mat, int data, String name, String mod, boolean glint, String... lore) {
            this(id, mat, data, name, mod, glint, 0, 0, null, 0, 0, 0, lore);
        }
        Def(String id, Material mat, int data, String name, String mod, boolean glint, int armor, double toughness, Color colour,
            double attack, double speed, int efficiency, String... lore) {
            this.id = id; this.mat = mat; this.data = (short) data; this.name = name; this.mod = mod; this.glint = glint;
            this.lore = lore; this.armor = armor; this.toughness = toughness; this.colour = colour; this.attack = attack; this.speed = speed;
            this.efficiency = efficiency;
        }
    }

    static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static void def(Def d) { DEFS.put(d.id, d); }

    static final Color WITHER_BONE = Color.fromRGB(0x3B3B3B), ORANGE_HIDE = Color.fromRGB(0xE0701B), BLACK_HIDE = Color.fromRGB(0x1A1A1A);

    static {
        String nx = "NetherEx", bn = "BetterNether";
        def(new Def("amethyst_crystal", Material.CHORUS_FRUIT_POPPED, 0, "Nether Amethyst Crystal", nx, false, "Pigtificate currency"));
        def(new Def("amethyst_block", Material.PURPUR_BLOCK, 0, "Nether Amethyst Block", nx, true, "Crowns a Gold Golem:", "place it on a T of gold blocks"));
        def(new Def("rime_crystal", Material.SUGAR, 0, "Nether Rime Crystal", nx, true, "Brew into an Awkward Potion: Freezing"));
        def(new Def("rime_block", Material.SEA_LANTERN, 0, "Nether Rime Block", nx, true, "Freezes water, lava and nearby mobs"));
        def(new Def("wither_bone", Material.BONE, 0, "Wither Bone", nx, false));
        def(new Def("wither_dust", Material.INK_SACK, 8, "Wither Dust", nx, false, "Use on a mushroom to grow an Elder Mushroom"));
        def(new Def("blazed_wither_bone", Material.BONE, 0, "Blazed Wither Bone", nx, true));
        def(new Def("frosted_wither_bone", Material.BONE, 0, "Frosted Wither Bone", nx, true));
        def(new Def("frost_rod", Material.STICK, 0, "Frost Rod", nx, true));
        def(new Def("frost_powder", Material.CLAY_BALL, 0, "Frost Powder", nx, false));
        def(new Def("orange_salamander_hide", Material.LEATHER, 0, "Orange Salamander Hide", nx, false));
        def(new Def("black_salamander_hide", Material.LEATHER, 0, "Black Salamander Hide", nx, false));
        def(new Def("spore", Material.SULPHUR, 0, "Spore", nx, false, "Brew into an Awkward Potion: Dispersal"));
        def(new Def("frost_fang", Material.RABBIT_FOOT, 0, "Frost Fang", nx, false, "Brew into an Awkward Potion: Frigid Health"));
        def(new Def("ghast_queen_tear", Material.GHAST_TEAR, 0, "Ghast Queen Tears", nx, true, "Cures a weakened Zombie Pigman", "into a Pigtificate"));
        def(new Def("ghast_meat_raw", Material.SPIDER_EYE, 0, "Raw Ghast Meat", nx, false, "Levitation II (5s)", "Brew into an Awkward Potion: Sorrow"));
        def(new Def("ghast_meat_cooked", Material.COOKED_MUTTON, 0, "Cooked Ghast Meat", nx, false, "Levitation II (10s)"));
        def(new Def("congealed_magma_cream", Material.BEETROOT, 0, "Congealed Magma Cream", nx, false, "Fire Resistance II (10s)"));
        def(new Def("enoki_mushroom", Material.CARROT_ITEM, 0, "Enoki Mushroom", nx, false));
        def(new Def("brown_elder_mushroom", Material.BREAD, 0, "Brown Elder Mushroom", nx, false, "Eat: fills hunger but hurts (4-8)"));
        def(new Def("red_elder_mushroom", Material.COOKIE, 0, "Red Elder Mushroom", nx, false, "Eat: full health, empties hunger"));
        def(new Def("dull_mirror", Material.WATCH, 0, "Dull Mirror", nx, true, "Sneak + right-click: anchor your respawn here", "Kept on death; 5 anchored respawns"));
        def(new Def("rime_and_steel", Material.FLINT_AND_STEEL, 0, "Rime and Steel", nx, true, "Lights blue fire"));
        String[] handles = {"withered", "blazed", "frosted"};
        String[] handleNames = {"Withered", "Blazed", "Frosted"};
        for (int h = 0; h < 3; h++) {
            def(new Def(handles[h] + "_amedian_sword", Material.DIAMOND_SWORD, 0, handleNames[h] + " Amedian Sword", nx, false, 0, 0, null, 5, 1.6, 0,
                h == 0 ? "Double damage to spores" : "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_pickaxe", Material.DIAMOND_PICKAXE, 0, handleNames[h] + " Amedian Pickaxe", nx, false, 0, 0, null, 3, 1.2, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_shovel", Material.DIAMOND_SPADE, 0, handleNames[h] + " Amedian Shovel", nx, false, 0, 0, null, 3.5, 1.0, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_axe", Material.DIAMOND_AXE, 0, handleNames[h] + " Amedian Axe", nx, false, 0, 0, null, 10, 1.2, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_hoe", Material.DIAMOND_HOE, 0, handleNames[h] + " Amedian Hoe", nx, false, 0, 0, null, 1, 2.0, 0, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_hammer", Material.DIAMOND_PICKAXE, 0, handleNames[h] + " Amedian Hammer", nx, true, 0, 0, null, 6, 0.9, 0, "Mines 3x3", "Amedian: harvest 4, 2250 uses"));
        }
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        int[] bone = {2, 4, 3, 2}, hide = {2, 5, 4, 2};
        String[] slotNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int s = 0; s < 4; s++) {
            def(new Def("wither_bone_" + slots[s], leather[s], 0, "Wither Bone " + slotNames[s], nx, false, bone[s], 0.5, WITHER_BONE, 0, 0, 0, "Full set: skeletons ignore you"));
            def(new Def("orange_salamander_hide_" + slots[s], leather[s], 0, "Orange Salamander Hide " + slotNames[s], nx, false, hide[s], 1.0, ORANGE_HIDE, 0, 0, 0, "Full set: fire and lava immunity"));
            def(new Def("black_salamander_hide_" + slots[s], leather[s], 0, "Black Salamander Hide " + slotNames[s], nx, false, hide[s], 1.0, BLACK_HIDE, 0, 0, 0));
        }
        def(new Def("potion_freezing", Material.POTION, 0, "Potion of Freezing", nx, false, "Frozen (0:30)"));
        def(new Def("potion_frigid_health", Material.POTION, 0, "Potion of Frigid Health", nx, false, "Frostbitten (0:30)"));
        def(new Def("potion_dispersal", Material.POTION, 0, "Potion of Dispersal", nx, false, "Infested (0:30)"));
        def(new Def("potion_sorrow", Material.POTION, 0, "Potion of Sorrow", nx, false, "Crying (0:30)", "Fills an Urn of Sorrow"));
        // BetterNether
        def(new Def("cincinnasite", Material.INK_SACK, 11, "Cincinnasite", bn, false));
        def(new Def("cincinnasite_block", Material.SANDSTONE, 2, "Cincinnasite Block", bn, false, "Smelt into Forged Cincinnasite"));
        def(new Def("cincinnasite_forged", Material.STAINED_CLAY, 4, "Forged Cincinnasite", bn, false));
        def(new Def("cincinnasite_pickaxe", Material.IRON_PICKAXE, 0, "Cincinnasite Pickaxe", bn, false, 0, 0, null, 4, 1.2, 4, "512 uses, very fast"));
        def(new Def("cincinnasite_axe", Material.IRON_AXE, 0, "Cincinnasite Axe", bn, false, 0, 0, null, 9, 0.9, 4, "512 uses, very fast"));
        def(new Def("cincinnasite_pickaxe_diamond", Material.DIAMOND_PICKAXE, 0, "Cincinnasite-Diamond Pickaxe", bn, false, 0, 0, null, 5, 1.2, 2, "2048 uses"));
        def(new Def("cincinnasite_axe_diamond", Material.DIAMOND_AXE, 0, "Cincinnasite-Diamond Axe", bn, false, 0, 0, null, 9, 1.0, 2, "2048 uses"));
        def(new Def("black_apple", Material.APPLE, 0, "Black Apple", bn, false, "Regeneration IV (2s)"));
        def(new Def("nether_reed", Material.SUGAR_CANE, 0, "Nether Reed", bn, false));
        def(new Def("stalagnate", Material.LOG, 2, "Stalagnate", bn, false));
        def(new Def("stalagnate_bowl", Material.BOWL, 0, "Stalagnate Bowl", bn, false));
        def(new Def("stalagnate_bowl_wart", Material.BEETROOT_SOUP, 0, "Wart Soup", bn, false));
        def(new Def("stalagnate_bowl_mushroom", Material.MUSHROOM_SOUP, 0, "Mushroom Stew", bn, false));
        def(new Def("stalagnate_bowl_apple", Material.RABBIT_STEW, 0, "Black Apple Stew", bn, false));
    }

    static ItemStack create(String id, int amount) {
        Def d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown item " + id);
        ItemStack s = new ItemStack(d.mat, Math.max(1, amount), d.data);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(ChatColor.RESET + (d.glint ? ChatColor.LIGHT_PURPLE.toString() : ChatColor.WHITE.toString()) + d.name);
        List<String> lore = new ArrayList<>();
        for (String l : d.lore) lore.add(ChatColor.GRAY + l);
        lore.add(ChatColor.DARK_GRAY + d.mod);
        m.setLore(lore);
        if (d.glint) { m.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        if (d.efficiency > 0) { m.addEnchant(org.bukkit.enchantments.Enchantment.DIG_SPEED, d.efficiency, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        if (m instanceof LeatherArmorMeta && d.colour != null) ((LeatherArmorMeta) m).setColor(d.colour);
        if (d.mat == Material.POTION) m.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        s.setItemMeta(m);
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
        NBTTagCompound tag = n.hasTag() ? n.getTag() : new NBTTagCompound();
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("id", id);
        tag.set("JasprNether", mine);
        if (d.armor > 0 || d.attack > 0) {
            NBTTagList mods = new NBTTagList();
            String slot = slotOf(d.mat);
            if (d.armor > 0) {
                mods.add(modifier("generic.armor", d.armor, slot, 1));
                if (d.toughness > 0) mods.add(modifier("generic.armorToughness", d.toughness, slot, 2));
            } else {
                mods.add(modifier("generic.attackDamage", d.attack - 1, "mainhand", 3));
                mods.add(modifier("generic.attackSpeed", d.speed - 4, "mainhand", 4));
            }
            tag.set("AttributeModifiers", mods);
            tag.setInt("HideFlags", tag.getInt("HideFlags") | 2 | (d.glint || d.efficiency > 0 ? 1 : 0));
        }
        if (d.mat == Material.POTION) {
            tag.setString("Potion", "minecraft:awkward");
            tag.setInt("CustomPotionColor", potionColour(id));
        }
        n.setTag(tag);
        return CraftItemStack.asBukkitCopy(n);
    }

    static int potionColour(String id) {
        switch (id.replace("potion_", "")) {
            case "freezing": return (93 << 16) | (188 << 8) | 210;
            case "frigid_health": return (19 << 16) | (226 << 8) | 255;
            case "dispersal": return (142 << 16) | (96 << 8) | 40;
            default: return (103 << 16) | (62 << 8) | 124;
        }
    }

    /** A potion of one of the NetherEx effects in drinkable, splash or lingering form. */
    static ItemStack potion(String effect, Material form) {
        ItemStack s = create("potion_" + effect, 1);
        if (form != Material.POTION) {
            s.setType(form);
            ItemMeta m = s.getItemMeta();
            String prefix = form == Material.SPLASH_POTION ? "Splash " : "Lingering ";
            m.setDisplayName(m.getDisplayName().replace("Potion of", prefix + "Potion of"));
            s.setItemMeta(m);
        }
        return s;
    }

    private static String slotOf(Material m) {
        String n = m.name();
        if (n.endsWith("HELMET")) return "head";
        if (n.endsWith("CHESTPLATE")) return "chest";
        if (n.endsWith("LEGGINGS")) return "legs";
        if (n.endsWith("BOOTS")) return "feet";
        return "mainhand";
    }

    private static NBTTagCompound modifier(String attr, double amount, String slot, int n) {
        NBTTagCompound c = new NBTTagCompound();
        c.setString("AttributeName", attr);
        c.setString("Name", "jasprnether");
        c.setDouble("Amount", amount);
        c.setInt("Operation", 0);
        c.setString("Slot", slot);
        c.setLong("UUIDMost", 0x4A4E0000L + attr.hashCode() + slot.hashCode());
        c.setLong("UUIDLeast", 0x5245000000L + n);
        return c;
    }

    /** The JasprNether id of an item, or null. */
    static String id(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
            if (!n.hasTag()) return null;
            NBTTagCompound t = n.getTag();
            if (!t.hasKeyOfType("JasprNether", 10)) return null;
            String id = t.getCompound("JasprNether").getString("id");
            return id.isEmpty() ? null : id;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean is(ItemStack s, String id) { return id.equals(id(s)); }

    static List<String> ids() { return new ArrayList<>(DEFS.keySet()); }

    static boolean isArmorOf(String id, String set) { return id != null && id.startsWith(set + "_") && Arrays.asList("helmet", "chestplate", "leggings", "boots").contains(id.substring(set.length() + 1)); }
}
