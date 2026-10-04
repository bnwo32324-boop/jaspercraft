package wideprobe;

import net.minecraft.server.v1_12_R1.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only console probe for tests/wide-inventory-bot.cjs. Never deploy. */
public class WideProbe extends JavaPlugin implements Listener {
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        try {
            Player: {
                if (a.length < 2) break Player;
                org.bukkit.entity.Player p = Bukkit.getPlayerExact(a[1]);
                if (p == null) { getLogger().info("WPROBE_ERR no player " + a[1]); return true; }
                PlayerInventory inv = p.getInventory();
                EntityPlayer handle = ((CraftPlayer) p).getHandle();
                switch (a[0]) {
                    case "inv": {
                        StringBuilder s = new StringBuilder();
                        for (int i = 0; i < inv.getSize(); i++) {
                            ItemStack it = inv.getItem(i);
                            if (it != null && it.getType() != Material.AIR) s.append(i).append(':').append(it.getType().name()).append('x').append(it.getAmount()).append(',');
                        }
                        ItemStack hand = inv.getItemInMainHand();
                        getLogger().info("WPROBE_INV p=" + p.getName() + " size=" + inv.getSize() + " storage=" + inv.getStorageContents().length
                            + " held=" + inv.getHeldItemSlot() + " hand=" + (hand == null ? "AIR" : hand.getType().name())
                            + " window=" + handle.defaultContainer.slots.size() + " open=" + handle.activeContainer.slots.size()
                            + " helmet=" + (inv.getHelmet() == null ? "AIR" : inv.getHelmet().getType().name())
                            + " offhand=" + inv.getItemInOffHand().getType().name() + " items=" + s);
                        return true;
                    }
                    case "set": inv.setItem(Integer.parseInt(a[2]), new ItemStack(Material.valueOf(a[3]), Integer.parseInt(a[4]))); break;
                    case "helmet": inv.setHelmet(new ItemStack(Material.valueOf(a[2]))); break;
                    case "offhand": inv.setItemInOffHand(new ItemStack(Material.valueOf(a[2]), Integer.parseInt(a[3]))); break;
                    case "held":
                        try { inv.setHeldItemSlot(Integer.parseInt(a[2])); }
                        catch (IllegalArgumentException e) { getLogger().info("WPROBE_HELD_REFUSED " + e.getMessage()); return true; }
                        break;
                    case "clear": inv.clear(); break;
                    case "give": {
                        int left = 0;
                        for (ItemStack rest : inv.addItem(new ItemStack(Material.valueOf(a[2]), Integer.parseInt(a[3]))).values()) left += rest.getAmount();
                        getLogger().info("WPROBE_GIVE left=" + left);
                        return true;
                    }
                    case "save": p.saveData(); break;
                    case "chest": p.openInventory(Bukkit.createInventory(null, 27, "Wide test chest")); break;
                    case "lore": {   // wprobe lore <player> <index> <MATERIAL> <name>|<lore line>|<lore line>... ('&' colours, '_' spaces)
                        ItemStack it = new ItemStack(Material.valueOf(a[3]));
                        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
                        String[] parts = org.bukkit.ChatColor.translateAlternateColorCodes('&', a[4].replace('_', ' ')).split("\\|");
                        meta.setDisplayName(parts[0]);
                        meta.setLore(java.util.Arrays.asList(parts).subList(1, parts.length));
                        it.setItemMeta(meta);
                        inv.setItem(Integer.parseInt(a[2]), it);
                        break;
                    }
                    case "furnace": p.openInventory(Bukkit.createInventory(null, org.bukkit.event.inventory.InventoryType.FURNACE)); break;
                    default: break Player;
                }
                getLogger().info("WPROBE_OK " + a[0]);
                return true;
            }
            getLogger().info("WPROBE_ERR usage");
        } catch (RuntimeException e) {
            getLogger().info("WPROBE_ERR " + e);
        }
        return true;
    }

    @EventHandler public void click(InventoryClickEvent e) {
        getLogger().info("WPROBE_CLICK raw=" + e.getRawSlot() + " slot=" + e.getSlot() + " type=" + e.getSlotType()
            + " top=" + (e.getClickedInventory() == e.getView().getTopInventory()) + " item=" + (e.getCurrentItem() == null ? "AIR" : e.getCurrentItem().getType().name()));
    }

    @EventHandler public void creative(InventoryCreativeEvent e) {
        getLogger().info("WPROBE_CREATIVE raw=" + e.getRawSlot() + " slot=" + e.getSlot() + " type=" + e.getSlotType());
    }

    @EventHandler public void held(PlayerItemHeldEvent e) {
        getLogger().info("WPROBE_HELD from=" + e.getPreviousSlot() + " to=" + e.getNewSlot());
    }
}
