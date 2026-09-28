package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Every bookshelf in Atlas holds a real book. Right-click a shelf to read it; sneak and right-click with an empty hand to
 * take a copy. What a shelf holds depends on where it stands: the Great Library keeps the histories, Lampsa its
 * technical works, Hieranthe its hymns, Mnemeia the archive; in the Dominion, shelves hold orders, edicts, ledgers, and
 * in Anthrakion's Library of Ash, the Keeper's own journals.
 */
final class Library implements Listener {
    private final AtlasPlugin plugin;
    long reads, copies;

    Library(AtlasPlugin plugin) { this.plugin = plugin; }

    static String collectionAt(int x, int z) {
        Realm.Place p = Realm.placeAt(x, z);
        if (p != null) switch (p) {
            case ASTREION: return "great";
            case LAMPSA: return "daidaros";
            case HIERANTHE: return "hymns";
            case MNEMEIA: return "archive";
            case LAST_WATCH: return "line";
            case ANTHRAKION: return "journal";
            case AIGAI: return "ledger";
            case PELLENE: return "edicts";
            case STILLED_GARDEN: case SALLOW_HOUSE: return "melaina";
            case GREAT_ENGINE: return "requisitions";
            case PYLON: return "marshal";
            case RIDER_TOWER: return "rider";
            default: break;
        }
        Realm.Zone z0 = Realm.zone(x, z);
        if (z0.dominion()) return z0 == Realm.Zone.WEALD ? "stilling" : z0 == Realm.Zone.FORGES ? "requisitions" : z0 == Realm.Zone.FALLEN ? "edicts" : "orders";
        if (z0 == Realm.Zone.WOUND) return "lost";
        return "concord";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void shelf(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.BOOKSHELF || !plugin.isAtlas(b.getWorld())) return;
        Player p = e.getPlayer();
        boolean copy = p.isSneaking();
        if (copy && p.getInventory().getItemInMainHand().getType() != Material.AIR) return;   // sneak-placing against a shelf stays possible
        e.setCancelled(true);
        read(p, b, copy);
    }

    /** Reads (or copies) the book a shelf holds. */
    void read(Player p, Block b, boolean copy) {
        String coll = collectionAt(b.getX(), b.getZ());
        LoreBooks.Book book = LoreBooks.shelf(coll, Hash.of(plugin.seed(), b.getX(), b.getY(), b.getZ()));
        if (book == null) return;
        if (copy) {
            if (Items.has(p, "book_" + book.id)) { p.sendMessage(ChatColor.GRAY + "You already carry a copy of " + ChatColor.WHITE + book.title + ChatColor.GRAY + "."); return; }
            Talk.give(p, book.item());
            copies++;
            p.sendMessage(ChatColor.GRAY + "You take a copy of " + ChatColor.WHITE + book.title + ChatColor.GRAY + " (" + book.author + ").");
            plugin.getLogger().info("ATLAS_LIBRARY_COPY player=" + p.getName() + " book=" + book.id);
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_BLUE + book.title);
        lines.add(ChatColor.DARK_GRAY + book.author);
        List<net.md_5.bungee.api.chat.BaseComponent[]> pages = new ArrayList<>(Talk.paginate(lines));
        for (String page : book.pages) {
            List<String> pl = new ArrayList<>();
            for (String l : page.split("\n")) pl.add(ChatColor.BLACK + l);
            pages.addAll(Talk.paginate(pl));
        }
        Talk.openBook(p, pages);
        reads++;
        plugin.getLogger().info("ATLAS_LIBRARY_READ player=" + p.getName() + " book=" + book.id + " shelf=" + coll);
        p.sendMessage(ChatColor.GRAY + "Reading " + ChatColor.WHITE + book.title + ChatColor.GRAY + ". Sneak and right-click the shelf with an empty hand to take a copy.");
    }
}
