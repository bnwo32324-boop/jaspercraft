package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Atlas's items. Every special item carries a hidden mark line ("Atlas - &lt;id&gt;") so it is recognised however it is
 * renamed. The keys of knowledge (the Oath of Kallias, the Hymn of Passage, the Counterpoint, the Founding Charter and the
 * Light of Theano) can always be asked for again from the Concord; nothing essential can be lost.
 */
final class Items {
    private Items() {}

    static final String MARK = "Atlas - ";

    static String markOf(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            String s = ChatColor.stripColor(line);
            if (s.startsWith(MARK)) return s.substring(MARK.length());
        }
        return null;
    }

    static boolean is(ItemStack item, String id) { return id.equals(markOf(item)); }

    /** Whether the player carries an item with this mark anywhere (inventory, hands, armour). */
    static boolean has(org.bukkit.entity.Player p, String id) {
        for (ItemStack i : p.getInventory().getContents()) if (is(i, id)) return true;
        return false;
    }

    /** Whether the player holds an item with this mark in either hand. */
    static boolean holding(org.bukkit.entity.Player p, String id) {
        return is(p.getInventory().getItemInMainHand(), id) || is(p.getInventory().getItemInOffHand(), id);
    }

    /** Takes up to n of a plain material from the player; true if all n were there (nothing is taken otherwise). */
    static boolean take(org.bukkit.entity.Player p, Material m, int n) {
        if (!p.getInventory().containsAtLeast(new ItemStack(m), n)) return false;
        p.getInventory().removeItem(new ItemStack(m, n));
        return true;
    }

    static ItemStack make(Material m, int data, String name, String id, String... lore) {
        ItemStack i = new ItemStack(m, 1, (short) data);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(ChatColor.GRAY + s);
        l.add(ChatColor.DARK_GRAY + MARK + id);
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(meta);
        return i;
    }

    static ItemStack glint(ItemStack i) {
        ItemMeta meta = i.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        i.setItemMeta(meta);
        return i;
    }

    /** A written book with a mark line. */
    static ItemStack book(String title, String author, String id, String... pages) {
        ItemStack i = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) i.getItemMeta();
        meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
        meta.setAuthor(author);
        meta.setPages(fit(pages));
        if (id != null) meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + MARK + id));
        i.setItemMeta(meta);
        return i;
    }

    /** Splits any page too long for a book page (about 14 rows of 19 characters) into as many pages as it needs. */
    static List<String> fit(String... pages) {
        List<String> out = new ArrayList<>();
        for (String page : pages)
            for (List<String> p : Talk.paginateLines(Arrays.asList(page.split("\n", -1)))) {
                while (!p.isEmpty() && p.get(0).trim().isEmpty()) p.remove(0);
                if (!p.isEmpty()) out.add(String.join("\n", p));
            }
        return out;
    }

    // ------------------------------------------------------------------ keys of knowledge

    static ItemStack oath() { return glint(book("The Oath of Kallias", "Kallias, son of Lysis", "oath", Lore.OATH)); }
    static ItemStack hymn() { return glint(book("The Hymn of Passage", "the Asklepieion", "hymn", Lore.HYMN)); }
    static ItemStack counterpoint() { return glint(book("The Counterpoint", "Daidaros Tekton", "counterpoint", Lore.COUNTERPOINT)); }
    static ItemStack charter() { return glint(book("The Founding Charter", "the First Synedrion", "charter", Lore.CHARTER)); }

    static ItemStack light() {
        return glint(make(Material.PRISMARINE_CRYSTALS, 0, ChatColor.AQUA + "The Light of Theano", "light",
            "The Hearthstar's light, lent by the Synedrion.", "Carry it to the Cinder Heart.", "The two halves of the Star remember each other."));
    }

    static ItemStack key(String id) {
        switch (id) {
            case "oath": return oath();
            case "hymn": return hymn();
            case "counterpoint": return counterpoint();
            case "charter": return charter();
            case "light": return light();
            default: return null;
        }
    }

    static ItemStack codex() {
        return glint(make(Material.BOOK, 0, ChatColor.GOLD + "Wayfarer's Codex", "codex", "Your record of Atlas.", "Right-click to read what you know", "and what remains to be done."));
    }

    // ------------------------------------------------------------------ trade goods

    static final String[][] TROPHIES = {
        {"Ashborn tusk", "A yellowed tusk. The Concord studies them."}, {"Blackshield sigil", "A black iron badge of rank."},
        {"Taskmaster's whip-cord", "Knotted, and stained."}, {"Ember of the pits", "Still warm. It should not be."},
        {"Gnawling's tally-stick", "Notches for every stolen thing."}, {"Cinder coin", "The Dominion pays its soldiers in these."},
    };

    static ItemStack trophy(Random r) { return trophy(r.nextInt(TROPHIES.length)); }

    static ItemStack trophy(int index) {
        String[] t = TROPHIES[index];
        Material m = t[0].contains("tusk") ? Material.BONE : t[0].contains("sigil") ? Material.FLINT : t[0].contains("whip") ? Material.STRING
            : t[0].contains("Ember") ? Material.BLAZE_POWDER : t[0].contains("tally") ? Material.STICK : Material.GOLD_NUGGET;
        return make(m, 0, ChatColor.DARK_RED + t[0], "trophy", t[1], "Concord merchants buy these.");
    }

    static ItemStack ashIron(int n) {
        ItemStack i = make(Material.IRON_NUGGET, 0, ChatColor.DARK_GRAY + "Ash-iron", "ash_iron", "Iron smelted in the Forges' black fire.", "Lampsa's smiths can rework it.");
        i.setAmount(n);
        return i;
    }

    // ------------------------------------------------------------------ rewards

    static ItemStack reward(String id) {
        switch (id) {
            case "kallias": {
                ItemStack i = make(Material.SHIELD, 0, ChatColor.GOLD + "Shield of the Unarmed", "reward_kallias",
                    "Kallias's shield from the Stoa, before the crown.", "\"I will not lift my spear against the unarmed.\"");
                ItemMeta m = i.getItemMeta(); m.addEnchant(Enchantment.DURABILITY, 3, true); m.addEnchant(Enchantment.MENDING, 1, true); i.setItemMeta(m);
                return i;
            }
            case "melaina": {
                ItemStack i = make(Material.GOLD_CHESTPLATE, 0, ChatColor.GOLD + "Mantle of Passage", "reward_melaina",
                    "A healer's mantle from the Asklepieion.", "Let the lamp go out gently.");
                ItemMeta m = i.getItemMeta(); m.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true); m.addEnchant(Enchantment.DURABILITY, 3, true); m.addEnchant(Enchantment.MENDING, 1, true); m.setUnbreakable(true); i.setItemMeta(m);
                return i;
            }
            case "daidaros": {
                ItemStack i = make(Material.DIAMOND_PICKAXE, 0, ChatColor.GOLD + "Tekton's Hand", "reward_daidaros",
                    "Daidaros's own tool from his Lampsa years.", "Hands that spare hands.");
                ItemMeta m = i.getItemMeta(); m.addEnchant(Enchantment.DIG_SPEED, 5, true); m.addEnchant(Enchantment.LOOT_BONUS_BLOCKS, 3, true); m.addEnchant(Enchantment.MENDING, 1, true); i.setItemMeta(m);
                return i;
            }
            case "keleos": {
                ItemStack i = make(Material.DIAMOND_SWORD, 0, ChatColor.GOLD + "Stylus of the Charter", "reward_keleos",
                    "The lawgiver's pen, reforged as a blade.", "No law shall bind the tongue of the free.");
                ItemMeta m = i.getItemMeta(); m.addEnchant(Enchantment.DAMAGE_ALL, 5, true); m.addEnchant(Enchantment.LOOT_BONUS_MOBS, 3, true); m.addEnchant(Enchantment.MENDING, 1, true); i.setItemMeta(m);
                return i;
            }
            case "pyrarch": {
                ItemStack i = make(Material.DIAMOND_HELMET, 0, ChatColor.AQUA + "Crown of the Rekindler", "reward_pyrarch",
                    "Worn by those who made the Star whole.", "Atlas remembers your name.");
                ItemMeta m = i.getItemMeta(); m.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true); m.addEnchant(Enchantment.OXYGEN, 3, true); m.addEnchant(Enchantment.DURABILITY, 3, true); m.addEnchant(Enchantment.MENDING, 1, true); m.setUnbreakable(true); i.setItemMeta(m);
                return i;
            }
            case "lesser": return make(Material.EMERALD, 0, ChatColor.GREEN + "Concord bounty", "bounty", "Paid by the Synedrion for a Dominion captain.");
            default: return null;
        }
    }

    static ItemStack crownShard(String who) {
        return make(Material.COAL, 1, ChatColor.DARK_GRAY + "The Ash-Crown of " + who, "crown_" + who.toLowerCase(),
            "Cold now. A shard of the Cinder Heart.", "Its Ward at Anthrakion has gone dark.");
    }
}
