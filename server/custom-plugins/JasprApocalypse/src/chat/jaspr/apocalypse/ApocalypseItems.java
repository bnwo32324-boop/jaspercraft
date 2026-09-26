package chat.jaspr.apocalypse;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import net.minecraft.server.v1_12_R1.NBTTagCompound;

public final class ApocalypseItems {
    private ApocalypseItems() {}
    /** Immutable id -> display-name catalogue. Categories: gun, melee, armor, material, consumable, supply, artifact. */
    public static Map<String, String> catalogue() { return catalogue("all"); }
    public static Map<String, String> catalogue(String category) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if ("all".equals(category) || "gun".equals(category)) result.putAll(Arsenal.catalogue());
        result.putAll(ExpeditionEquipment.catalogue(category));
        if ("all".equals(category) || "supply".equals(category)) {
            result.put("relic", "Echo Relic"); result.put("scrap", "Military Salvage");
        }
        if ("all".equals(category) || "artifact".equals(category)) {
            result.put("expedition_trophy", "Expedition Trophy"); result.put("guide", "The Last Broadcast");
        }
        return Collections.unmodifiableMap(result);
    }
    /** Fresh single item, empty magazine; tier is provenance only (clamped 1..5). Main server thread. */
    public static ItemStack expedition(String id, int tier) {
        ItemStack item;
        if (Arsenal.catalogue().containsKey(id)) item = Arsenal.weapon(id);
        else if ("relic".equals(id)) item = relic(1);
        else if ("scrap".equals(id)) item = scrap(1);
        else if ("expedition_trophy".equals(id)) item = trophy();
        else if ("guide".equals(id)) item = guide();
        else item = ExpeditionEquipment.item(id);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.getTag(), data = root.getCompound("JasprApocalypse");
        data.setInt("tier", Math.max(1, Math.min(5, tier))); root.set("JasprApocalypse", data); nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }
    public static ItemStack gear(String id) { return expedition(id, 1); }
    public static String id(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        return nms.hasTag() ? nms.getTag().getCompound("JasprApocalypse").getString("id") : "";
    }
    public static ItemStack mark(ItemStack item, String id) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
        NBTTagCompound data = root.getCompound("JasprApocalypse");
        data.setString("id", id); root.set("JasprApocalypse", data); nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }
    private static ItemStack item(Material type, int amount, String id, String name, String... lore) {
        ItemStack item = new ItemStack(type, amount);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta);
        return mark(item, id);
    }
    public static ItemStack relic(int amount) {
        return item(Material.PRISMARINE_CRYSTALS, amount, "relic", ChatColor.LIGHT_PURPLE + "Echo Relic",
            ChatColor.GRAY + "A memory of something that should be dead.", ChatColor.DARK_PURPLE + "Recovered from haunted vaults and revenants.");
    }
    public static ItemStack scrap(int amount) {
        return item(Material.IRON_NUGGET, amount, "scrap", ChatColor.GRAY + "Military Salvage",
            ChatColor.DARK_GRAY + "Old-world machined parts. Keep for the arsenal.");
    }
    public static ItemStack trophy() {
        return item(Material.COMPASS, 1, "expedition_trophy", ChatColor.GOLD + "Expedition Trophy",
            ChatColor.GRAY + "Proof that a guarded ruin surrendered its secrets.",
            ChatColor.DARK_GRAY + "Named trophies record the site where they were recovered.");
    }
    public static ItemStack guide() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta book = (BookMeta) item.getItemMeta();
        book.setTitle("The Last Broadcast"); book.setAuthor("JasperCraft Survivors");
        book.setPages(
            "THE LAST BROADCAST\n\nThe dead can see exposed survivors up to 96 blocks away. Darkness, cover and sneaking help. Mining, building, wounds and gunfire leave short-lived clues.\n\nBreak sight and leave the last noise behind to lose pursuit.",
            "THE DEAD\n\nOrdinary siege dead jump and build short pillars to reach you. Teal Wall Crawlers scale tall walls. Roofs need an escape route.\n\nBreachers smash stone. TNT carriers hiss before exploding. Beds and storage survive their block damage.",
            "THE OTHER SIDE\n\nEvery third night is a Blood Moon. Revenants and Grave Wardens rise more often. Their shrines and sealed bunkers hold Echo Relics.\n\nThe dead are affected by darkness. Smoke and whispers reveal the supernatural ones.",
            "SURVIVE, THEN SALVAGE\n\nStart with ordinary tools, armor, food and torches. Loot ruins for supplies.\n\nExoskeletons are true end-game crafts: a full set costs 8 diamond blocks, 8 iron blocks, 6 gold blocks, 4 Nether Stars and 4 specialized vanilla cores.",
            "THE ARSENAL\n\n40 firearms and 40 melee weapons await. Every firearm can be forged from vanilla materials; stronger weapons demand heavier metal frames and rarer components.\n\nUse the crafting-table recipe browser. Right-click fires. Sneak + right-click reloads. Keep iron nuggets on you.",
            "A WORLD WORTH LOSING\n\nThis expedition begins in a reset world. Explore beyond camp for major ruins, rare vaults and dangerous discoveries.\n\nThe small cat returns you to Jaspr.chat. Share your discoveries, gather survivors, then enter the world again."
        );
        book.addPage(Arsenal.recipePages());
        book.addPage(ExpeditionEquipment.guidePages());
        book.addPage("SENTRY TURRET\n\nCraft: 4 iron ingots around 1 iron block.\n\nPlace it; it shoots zombies on its own and needs no ammo. Right-click it to choose targets, range and fire rate. Sneak + right-click picks it back up.");
        book.addPage("SANITIZED FLESH\n\nSmelt rotten flesh in a furnace.\n\nRight-click to eat: up to 8 food and saturation, never sickens like raw flesh.");
        book.addPage("REPLACE THIS GUIDE\n\nCraft one regular book with one rotten flesh, in any arrangement.\n\nListen for digging, watch for TNT helmets, and keep moving when the Blood Moon rises.");
        item.setItemMeta(book); return mark(item, "guide");
    }
}
