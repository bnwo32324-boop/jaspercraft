package chat.jaspr.atlas;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.server.v1_12_R1.EnumHand;
import net.minecraft.server.v1_12_R1.PacketDataSerializer;
import net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Conversations. Right-click anyone in Atlas: a book opens with what they say and the answers you can give (tap one), and
 * the same answers are offered in chat. Key figures have real exchanges (they explain, disagree, admit things) and give
 * you what you need to win; every essential lesson and key can be asked for again. Citizens speak by calling and city,
 * merchants trade, captives plead (and later testify), the Bound point at their shackle post.
 */
final class Talk implements Listener {
    private final AtlasPlugin plugin;
    /** Who each player is talking to (entity) and when, to validate answers. */
    private final Map<UUID, UUID> partner = new HashMap<>();
    long opened, answered;

    Talk(AtlasPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ the page model

    static final class Page {
        final String speaker;
        final List<String> text = new ArrayList<>();
        final List<String[]> options = new ArrayList<>();   // {label, action}
        Page(String speaker) { this.speaker = speaker; }
        Page say(String t) { text.add(t); return this; }
        Page opt(String label, String action) { options.add(new String[] {label, action}); return this; }
    }

    // ------------------------------------------------------------------ opening

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity who = e.getRightClicked();
        if (!Npcs.has(who, Npcs.TAG) || !plugin.isAtlas(who.getWorld())) return;
        if (!(who instanceof Villager) && !Npcs.has(who, "atlas_talker")) return;
        e.setCancelled(true);
        Page page = open(e.getPlayer(), who, "start");
        if (page != null) show(e.getPlayer(), who, page);
    }

    /** The page for a speaker at a node ("start" first). */
    Page open(Player p, Entity who, String node) {
        String key = Npcs.tagValue(who, Npcs.KEY);
        if (key != null) return Figures.page(plugin, p, key, node);
        String captive = Npcs.tagValue(who, Npcs.CAPTIVE);
        if (captive != null) return captive(p, who, captive, node);
        String rescued = Npcs.tagValue(who, "atlas_rescued:");
        if (rescued != null) return rescued(p, rescued, node);
        if (Npcs.has(who, Npcs.BOUND)) return bound(p, who);
        if (Npcs.has(who, Npcs.CHOIR)) return new Page(name(who)).say("(They are singing one note, over and over. Their eyes follow you. They cannot stop.)").opt("I will come back for you.", "close");
        String talker = Npcs.tagValue(who, "atlas_talker:");
        if (talker != null) return Figures.talker(plugin, p, talker, node);
        return citizen(p, who, node);
    }

    static String name(Entity who) { return who.getCustomName() == null ? "Someone" : ChatColor.stripColor(who.getCustomName()); }

    // ------------------------------------------------------------------ citizens and merchants

    private Page citizen(Player p, Entity who, String node) {
        String role = Npcs.tagValue(who, "atlas_role:");
        String trade = Npcs.tagValue(who, Npcs.MERCHANT);
        Realm.Place city = Realm.placeAt(who.getLocation().getBlockX(), who.getLocation().getBlockZ());
        long h = who.getUniqueId().getLeastSignificantBits();
        Page page = new Page(name(who));
        if (node.equals("news")) {
            page.say(Voices.news(plugin.state(), h));
            page.opt("Thank you.", "close");
            return page;
        }
        if (node.equals("place")) {
            page.say(Voices.place(city, h));
            page.opt("Thank you.", "close");
            return page;
        }
        page.say(Voices.greeting(role, city, plugin.state(), h, plugin.reputation().get(p)));
        if (trade != null) page.opt(plugin.reputation().barred(p) ? "Trade (refused: your standing)" : "Let me see your wares.", "trade");
        page.opt("What news?", "news");
        page.opt("Tell me about this place.", "place");
        page.opt("Farewell.", "close");
        return page;
    }

    // ------------------------------------------------------------------ captives and the Bound

    private Page captive(Player p, Entity who, String id, String node) {
        Lore.Captive c = Lore.captive(id);
        if (c == null) return null;
        Page page = new Page(c.name + ", " + c.title);
        if (node.equals("free")) {
            String blocked = plugin.captives().blocker(who);
            if (blocked != null) { page.say(blocked); page.opt("I'll deal with them.", "close"); return page; }
            plugin.captives().rescue(p, who, id);
            return null;
        }
        if (node.equals("who")) { page.say(c.testimony[0]); page.opt("I will get you out.", "go:free"); page.opt("Wait here.", "close"); return page; }
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        if (node.equals("bribe") && id.equals("thersites")) {
            if (rec.claimed.contains("thersites_ledger")) page.say("You already have it. I don't sell the same thing twice. I'm a clerk, not a merchant.");
            else if (!Items.take(p, Material.EMERALD, 3)) page.say("Three emeralds. I'm not greedy, stranger. I'm precise.");
            else {
                rec.claimed.add("thersites_ledger");
                rec.knows.add("withdrawal");
                plugin.saveStateSoon();
                LoreBooks.Book ledger = LoreBooks.BOOKS.get("ledger");
                if (ledger != null) give(p, ledger.item());
                plugin.getLogger().info("ATLAS_BARGAIN player=" + p.getName() + " with=thersites paid=emeralds");
                page.say("(He slides a thin book through the bars.) The Second Ledger. The Synedrion's own vote, bought from a Lampsa merchant. Seven to five. The names are on the last page.\n\nNow get me out, if you're going to.");
            }
            page.opt("I will get you out.", "go:free");
            page.opt("Later.", "close");
            return page;
        }
        page.say(c.plea);
        page.opt("I will get you out. Now.", "go:free");
        page.opt("Who are you?", "go:who");
        if (id.equals("thersites") && !rec.claimed.contains("thersites_ledger")) page.opt("What will your ledger cost me? (3 emeralds)", "go:bribe");
        page.opt("Not yet. Hold on.", "close");
        return page;
    }

    private Page rescued(Player p, String id, String node) {
        Lore.Captive c = Lore.captive(id);
        if (c == null) return null;
        Page page = new Page(c.name + ", " + c.title);
        if (node.equals("tell")) {
            ItemStack book = Items.book("Testimony of " + c.name, c.name, "testimony_" + id, c.testimony);
            give(p, book);
            State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
            String flag = id.equals("nausikaa") ? "ashiron" : id.equals("thersites") ? "withdrawal" : id.equals("tamsa") ? "kelani" : id.equals("agapios") ? "rescue_1203" : null;
            if (flag != null && rec.knows.add(flag)) plugin.saveStateSoon();
            page.say("I wrote it all down. Take it. Show it to whoever needs to read it.");
            page.opt("Thank you.", "close");
            return page;
        }
        page.say(c.thanks);
        page.opt("Tell me what you know.", "go:tell");
        page.opt("Rest well.", "close");
        return page;
    }

    private Page bound(Player p, Entity who) {
        String kind = Npcs.tagValue(who, "atlas_bound_kind:");
        Page page = new Page(name(who));
        if ("stilled".equals(kind)) page.say("(They cannot move. Their eyes find yours and hold them. Their lips shape one word, over and over: \"please.\")");
        else if ("citizen".equals(kind)) page.say("(They glance at the nearest Edict Stone and say nothing. With their hands, low, they sign: break the stones. Then, the Charter.)");
        else page.say("Don't. They'll see you. ...If you mean it, the shackle post. The black post with the chains. Kill the Taskmaster, then break it, and every chain in the camp goes slack.");
        page.opt("Hold on.", "close");
        return page;
    }

    // ------------------------------------------------------------------ showing a page

    /** Opens the page as a book (tap an answer) and repeats the answers in chat. */
    void show(Player p, Entity who, Page page) {
        partner.put(p.getUniqueId(), who.getUniqueId());
        opened++;
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_BLUE + page.speaker);
        for (String t : page.text)
            for (String para : t.split("\n\n")) { lines.add(""); for (String l : para.split("\n")) lines.add(ChatColor.BLACK + l); }
        List<List<String>> text = paginateLines(lines);
        List<String> last = text.get(text.size() - 1);
        int optionRows = 1;
        for (String[] o : page.options) optionRows += rows(o[0]) + 1;
        if (rows(last) + optionRows > ROWS) { last = new ArrayList<>(); text.add(last); }
        List<BaseComponent[]> pages = new ArrayList<>();
        for (int i = 0; i < text.size() - 1; i++) pages.add(TextComponent.fromLegacyText(String.join("\n", text.get(i))));
        ComponentBuilder b = new ComponentBuilder("");
        for (BaseComponent c : TextComponent.fromLegacyText(String.join("\n", last) + "\n")) b.append(c, ComponentBuilder.FormatRetention.NONE);
        for (String[] o : page.options) {
            b.append("\n> " + o[0], ComponentBuilder.FormatRetention.NONE).color(net.md_5.bungee.api.ChatColor.DARK_GREEN)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas say " + o[1]))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Answer").create()));
        }
        pages.add(b.create());
        openBook(p, pages);
        // Chat: the first line and the answers, clickable (for anyone whose book does not take taps).
        p.sendMessage(ChatColor.AQUA + page.speaker + ChatColor.GRAY + ": " + ChatColor.WHITE + firstSentence(page.text.isEmpty() ? "" : page.text.get(0)));
        for (String[] o : page.options) {
            TextComponent c = new TextComponent("  > " + o[0]);
            c.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas say " + o[1]));
            p.spigot().sendMessage(c);
        }
    }

    /** A written book page holds about 14 rows of about 19 characters. */
    static final int ROWS = 14, COLS = 19;

    static int rows(String line) {
        int n = ChatColor.stripColor(line).length();
        return Math.max(1, (n + COLS - 1) / COLS);
    }

    static int rows(List<String> lines) { int n = 0; for (String l : lines) n += rows(l); return n; }

    /** Packs lines into pages by estimated rows, splitting any line too long for one page at word boundaries. */
    static List<List<String>> paginateLines(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        int used = 0;
        for (String line : lines) {
            for (String part : split(line)) {
                int r = rows(part);
                if (used + r > ROWS && !cur.isEmpty()) { pages.add(cur); cur = new ArrayList<>(); used = 0; if (part.trim().isEmpty()) continue; }
                cur.add(part);
                used += r;
            }
        }
        pages.add(cur);
        return pages;
    }

    /** A line longer than a page, cut into page-sized pieces at spaces (colour carried over). */
    private static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        String color = line.startsWith("§") && line.length() > 1 ? line.substring(0, 2) : "";
        String rest = line;
        int max = (ROWS - 1) * COLS;
        while (ChatColor.stripColor(rest).length() > max) {
            int cut = rest.lastIndexOf(' ', max);
            if (cut <= 0) cut = max;
            out.add(rest.substring(0, cut));
            rest = color + rest.substring(cut).trim();
        }
        out.add(rest);
        return out;
    }

    static List<BaseComponent[]> paginate(List<String> lines) {
        List<BaseComponent[]> out = new ArrayList<>();
        for (List<String> page : paginateLines(lines)) out.add(TextComponent.fromLegacyText(String.join("\n", page)));
        return out;
    }

    private static String firstSentence(String t) {
        String s = t.replace('\n', ' ');
        int dot = s.indexOf(". ");
        return dot > 0 && dot < 140 ? s.substring(0, dot + 1) : s.length() > 140 ? s.substring(0, 137) + "..." : s;
    }

    /** Opens a book GUI: the book goes into the hand for one packet, then the hand is restored. */
    static void openBook(Player p, List<BaseComponent[]> pages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Atlas");
        meta.setAuthor("Atlas");
        meta.spigot().setPages(pages);
        book.setItemMeta(meta);
        int slot = p.getInventory().getHeldItemSlot();
        ItemStack old = p.getInventory().getItem(slot);
        p.getInventory().setItem(slot, book);
        try {
            PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());
            data.a(EnumHand.MAIN_HAND);
            ((CraftPlayer) p).getHandle().playerConnection.sendPacket(new PacketPlayOutCustomPayload("MC|BOpen", data));
        } catch (Throwable t) {
            // fall back to chat only
        } finally {
            p.getInventory().setItem(slot, old);
        }
    }

    /** "/atlas say &lt;action&gt;": an answer tapped in a book or chat. */
    void answer(Player p, String action) {
        UUID with = partner.get(p.getUniqueId());
        Entity who = with == null ? null : org.bukkit.Bukkit.getEntity(with);
        if (who == null || who.getWorld() != p.getWorld() || who.getLocation().distanceSquared(p.getLocation()) > 12 * 12) {
            p.sendMessage(ChatColor.GRAY + "You are no longer talking with anyone.");
            return;
        }
        answered++;
        if (action.equals("close")) { partner.remove(p.getUniqueId()); return; }
        if (action.equals("trade")) { plugin.trade().open(p, who); return; }
        String node = action.startsWith("go:") ? action.substring(3) : action;
        Page page = open(p, who, node);
        if (page != null) show(p, who, page);
    }

    static void give(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    /** The Codex: what the player knows and what remains, as a book. */
    void openCodex(Player p) { openBook(p, Codex.pages(plugin, p)); }
}
