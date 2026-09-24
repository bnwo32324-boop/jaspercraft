package chat.jaspr.graves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Items go to disk as the game's own NBT, not as Bukkit's serialised meta.
 * JasperCraft weapons carry custom NBT roots (loaded ammo, armament levels,
 * rolled abilities) that only a full NBT round-trip preserves exactly; a grave
 * that handed back a weapon with its upgrades stripped would be worse than no
 * grave at all.
 */
final class ItemCodec {
    private ItemCodec() { }

    static String encode(List<ItemStack> items) {
        try {
            NBTTagList list = new NBTTagList();
            for (ItemStack item : items) {
                NBTTagCompound tag = new NBTTagCompound();
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
                    if (nms != null) nms.save(tag);
                }
                if (!tag.isEmpty()) list.add(tag);
            }
            NBTTagCompound root = new NBTTagCompound();
            root.set("items", list);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NBTCompressedStreamTools.a(root, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    static List<ItemStack> decode(String encoded) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        if (encoded == null || encoded.isEmpty()) return items;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            NBTTagCompound root = NBTCompressedStreamTools.a(new ByteArrayInputStream(raw));
            NBTTagList list = root.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                NBTTagCompound tag = list.get(i);
                if (tag == null || tag.isEmpty()) continue;
                net.minecraft.server.v1_12_R1.ItemStack nms = new net.minecraft.server.v1_12_R1.ItemStack(tag);
                ItemStack item = CraftItemStack.asBukkitCopy(nms);
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) items.add(item);
            }
        } catch (Throwable ignored) { }
        return items;
    }
}
