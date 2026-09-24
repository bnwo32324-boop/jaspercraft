package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The ability sheet for whatever is currently in hand.
 *
 * Tokens come only from levelling the item, so this screen is about spending something already
 * earned. An ability the item's level has not reached yet is shown but locked, which is the point:
 * you can see what the weapon could become.
 */
final class ArmamentMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_PURPLE + "Armament";

    private final RpgPlugin plugin;

    ArmamentMenu(RpgPlugin plugin) { this.plugin = plugin; }

    boolean open(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!Armament.isEligible(held)) {
            player.sendMessage(ChatColor.RED + "Hold a weapon or a piece of armour to inspect it.");
            return false;
        }
        Inventory inventory = Bukkit.createInventory(null, 36, TITLE);
        render(player, inventory, held);
        player.openInventory(inventory);
        return true;
    }

    private void render(Player player, Inventory inventory, ItemStack held) {
        inventory.clear();
        List<AbilityType> roster = Armament.isWeapon(held) ? AbilityType.forWeapons() : AbilityType.forArmour();

        if (!Armament.isEnhanced(held)) {
            renderOrdinary(inventory, held, roster);
            return;
        }

        int itemLevel = Armament.level(held);
        int tokens = Armament.tokens(held);
        Rarity rarity = Armament.rarity(held);
        int used = Armament.abilitiesOn(held).size();

        for (int i = 0; i < roster.size() && i < 27; i++) {
            inventory.setItem(i, icon(roster.get(i), held, itemLevel, tokens, rarity, used));
        }

        ItemStack summary = new ItemStack(held.getType());
        ItemMeta meta = summary.getItemMeta();
        meta.setDisplayName(rarity.coloured() + ChatColor.GRAY + "  Level " + ChatColor.WHITE + itemLevel);
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.AQUA + "" + tokens + ChatColor.GRAY + " token(s) to spend");
        lore.add(ChatColor.GRAY + "Ability slots: " + ChatColor.WHITE + used + "/" + rarity.abilitySlots);
        RpgConfig settings = plugin.settings();
        if (itemLevel < settings.maxLevel) {
            lore.add(ChatColor.DARK_GRAY + "Experience to next level: " + ChatColor.WHITE
                    + Armament.experience(held) + "/" + Armament.experienceForNext(itemLevel, settings));
        } else {
            lore.add(ChatColor.GOLD + "Fully levelled.");
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + (Armament.isWeapon(held) ? "Weapons level by dealing damage." : "Armour levels by taking it."));
        meta.setLore(lore);
        summary.setItemMeta(meta);
        inventory.setItem(31, summary);
    }

    private ItemStack icon(AbilityType ability, ItemStack held, int itemLevel, int tokens, Rarity rarity, int used) {
        int level = Armament.abilityLevel(held, ability);
        boolean unlocked = level > 0;
        boolean levelOk = itemLevel >= ability.requiredItemLevel();
        boolean maxed = level >= ability.maxLevel;
        int cost = ability.tokenCost(level);
        boolean slotsLeft = unlocked || used < rarity.abilitySlots;

        // The icon never changes - it is what identifies the ability. Only the stack count moves,
        // one per level, and a mastered ability picks up an enchanted shimmer.
        ItemStack item = new ItemStack(ability.icon, Math.max(1, level));
        ItemMeta meta = item.getItemMeta();
        if (maxed) glow(meta);
        meta.setDisplayName(ability.color + ability.display
                + ChatColor.GRAY + (unlocked ? "  " + level + "/" + ability.maxLevel : "  locked"));

        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + ability.description);
        lore.add(ChatColor.DARK_GRAY + (ability.active ? "Active" : "Passive"));
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GOLD + "Mastered.");
        } else if (!levelOk) {
            lore.add(ChatColor.RED + "Requires item level " + ability.requiredItemLevel() + ".");
        } else if (!slotsLeft) {
            lore.add(ChatColor.RED + "No ability slots left on this " + rarity.display.toLowerCase(java.util.Locale.ROOT) + " item.");
        } else {
            boolean affordable = tokens >= cost;
            lore.add((affordable ? ChatColor.AQUA : ChatColor.RED) + "Cost: " + cost + " token(s)");
            lore.add(affordable ? ChatColor.GRAY + "Click to " + (unlocked ? "upgrade." : "unlock.")
                                : ChatColor.DARK_GRAY + "Not enough tokens.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The shimmer that marks an ability as fully mastered, with no enchantment text attached. */
    private void glow(ItemMeta meta) {
        try {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } catch (Throwable cosmeticOnly) {
            // The shimmer is decoration; the level is already written on the item.
        }
    }

    /**
     * A piece of gear that could be enhanced but is not yet. Worth showing rather than refusing:
     * it tells the player this item is a candidate, and what it could eventually hold.
     */
    private void renderOrdinary(Inventory inventory, ItemStack held, List<AbilityType> roster) {
        for (int i = 0; i < roster.size() && i < 27; i++) {
            AbilityType ability = roster.get(i);
            ItemStack item = new ItemStack(ability.icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.DARK_GRAY + ability.display);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + ability.description);
            lore.add(ChatColor.DARK_GRAY + "Unlocks at item level " + ability.requiredItemLevel());
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }

        ItemStack summary = new ItemStack(held.getType());
        ItemMeta meta = summary.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Ordinary " + (Armament.isWeapon(held) ? "weapon" : "armour"));
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "This one has no spark in it yet.");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Enhanced gear turns up when it is crafted");
        lore.add(ChatColor.DARK_GRAY + "or picked up. Once enhanced it levels through");
        lore.add(ChatColor.DARK_GRAY + "use and earns tokens to spend here.");
        meta.setLore(lore);
        summary.setItemMeta(meta);
        inventory.setItem(31, summary);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null || !TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!Armament.isEnhanced(held)) return;

        List<AbilityType> roster = Armament.isWeapon(held) ? AbilityType.forWeapons() : AbilityType.forArmour();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= roster.size()) return;

        AbilityType ability = roster.get(slot);
        int level = Armament.abilityLevel(held, ability);
        int itemLevel = Armament.level(held);
        int tokens = Armament.tokens(held);
        Rarity rarity = Armament.rarity(held);
        int used = Armament.abilitiesOn(held).size();

        if (level >= ability.maxLevel) {
            player.sendMessage(ChatColor.GOLD + ability.display + " is already mastered.");
            return;
        }
        if (itemLevel < ability.requiredItemLevel()) {
            player.sendMessage(ChatColor.RED + ability.display + " needs the item to reach level "
                    + ability.requiredItemLevel() + ". It is level " + itemLevel + ".");
            return;
        }
        if (level == 0 && used >= rarity.abilitySlots) {
            player.sendMessage(ChatColor.RED + "A " + rarity.display.toLowerCase(java.util.Locale.ROOT)
                    + " item holds only " + rarity.abilitySlots + " ability(s).");
            return;
        }
        int cost = ability.tokenCost(level);
        if (tokens < cost) {
            player.sendMessage(ChatColor.RED + "That costs " + cost + " token(s); this item has " + tokens + ".");
            return;
        }

        ItemStack updated = Armament.setAbilityLevel(held, ability, level + 1, tokens - cost);
        player.getInventory().setItemInMainHand(updated);
        player.sendMessage(ability.color + ability.display + ChatColor.GRAY + " is now level "
                + ChatColor.WHITE + (level + 1) + ChatColor.GRAY + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        render(player, event.getInventory(), updated);
    }
}
