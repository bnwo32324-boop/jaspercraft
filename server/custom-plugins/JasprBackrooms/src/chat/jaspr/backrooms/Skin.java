package chat.jaspr.backrooms;

import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.inventory.ItemStack;

/**
 * Own icons for trinkets, baubles and seals (owner 2026-10-05: "every trinket to have its own custom texture, not just stolen
 * from some kind of vanilla item"). A skinned item is an unbreakable stone tool whose damage value (its band) selects the icon
 * in the resource pack; scripts/trinket-art/catalog.cjs owns the numbers and tests/trinket-art.test.cjs keeps every plugin's copy
 * of them identical. Stone tools cannot be smelted or burned. The tool's own attack modifiers are replaced by an empty one, so a
 * trinket never wields like a sword or a spade, and the durability, attribute and unbreakable lines stay hidden.
 * This file is generated from scripts/trinket-art/Skin.java.template; edit the template, not the copies.
 */
final class Skin {
    private Skin() {}
    static final Material SWORD = Material.STONE_SWORD, SHOVEL = Material.STONE_SPADE;
    private static final long UUID_MOST = 0x4A41535052534B49L, UUID_LEAST = 0x4E00000000000001L;

    /**
     * The same item (amount, name, lore, enchantments, every NBT tag) as the unbreakable carrier of that band. Done on the NBT
     * itself, so an item whose own meta is special (a firework star, a skull) keeps everything and never loses its lore.
     */
    static ItemStack apply(ItemStack item, Material carrier, int band) {
        net.minecraft.server.v1_12_R1.ItemStack from = CraftItemStack.asNMSCopy(item);
        net.minecraft.server.v1_12_R1.ItemStack nms = new net.minecraft.server.v1_12_R1.ItemStack(CraftMagicNumbers.getItem(carrier), item.getAmount(), band);
        NBTTagCompound tag = from.hasTag() ? (NBTTagCompound) from.getTag().clone() : new NBTTagCompound();
        tag.remove("Explosion");
        tag.remove("SkullOwner");
        tag.setBoolean("Unbreakable", true);
        tag.setInt("HideFlags", tag.getInt("HideFlags") | 2 | 4);
        // A custom modifier list replaces the tool's own, so an empty one makes it a plain item in the hand.
        if (!tag.hasKeyOfType("AttributeModifiers", 9) || tag.getList("AttributeModifiers", 10).size() == 0) {
            NBTTagCompound none = new NBTTagCompound();
            none.setString("AttributeName", "generic.attackDamage");
            none.setString("Name", "jaspr_skin");
            none.setDouble("Amount", 0);
            none.setInt("Operation", 0);
            none.setLong("UUIDMost", UUID_MOST);
            none.setLong("UUIDLeast", UUID_LEAST);
            none.setString("Slot", "mainhand");
            NBTTagList list = new NBTTagList();
            list.add(none);
            tag.set("AttributeModifiers", list);
        }
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** Whether the item is the carrier of that band. */
    static boolean is(ItemStack item, Material carrier, int band) {
        return item != null && item.getType() == carrier && item.getDurability() == band;
    }

    /** Whether the item is any trinket carrier (an unbreakable stone sword or spade with a band): never a tool, so right-clicking with it uses nothing. */
    static boolean carrier(ItemStack item) {
        return item != null && (item.getType() == SWORD || item.getType() == SHOVEL) && item.getDurability() > 0 && item.hasItemMeta() && item.getItemMeta().isUnbreakable();
    }
}
