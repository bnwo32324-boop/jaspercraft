package chat.jaspr.ruins;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * The gate guide kit (owner, 2026-09-29): Atlas, Drownhollow and the Nether can each be beaten on its own, in any
 * order, and it must be obvious how. A guide stands at every gate into a realm and hands out, as often as asked, three
 * items that keep themselves up to date:
 * <ul>
 *   <li>a <b>compass</b> pointing to the player's next task (the needle where the client allows it, an arrow and the
 *       distance above the hotbar while it is held, a trail of light on right-click);</li>
 *   <li>a <b>checklist</b> book that ticks each task off as it is done;</li>
 *   <li>a <b>map</b> that marks the next task in red among the realm's great places, redrawn as the player moves.</li>
 * </ul>
 * Finishing a realm crowns the player; finishing all three crowns them Conqueror of the Three Realms. {@code /goals}
 * lists every realm's tasks. This file is shared word for word by the three realm plugins (only the package line
 * differs; a test keeps the copies identical); each realm plugin supplies a {@link Realm}.
 */
final class GuideKit implements Listener {

    /** What one realm teaches. Everything here must be cheap: tasks are recomputed every second for its players. */
    interface Realm {
        String key();                       // atlas | ruins | nether: item ids and player tags (jr_beat_<key>)
        String title();                     // "The Nether"
        String goal();                      // "Slay the Ghast Queen"
        String guideName();                 // "Nether Guide"
        String gate();                      // "an obsidian portal"
        ChatColor colour();
        Villager.Profession profession();
        int order();                        // position in /goals: 0 Atlas, 1 Drownhollow, 2 the Nether
        boolean inRealm(World w);
        List<Task> tasks(Player p);         // in order; the first not done is the current one
        List<String> tips();                // checklist pages after the list (each at most ~200 characters)
        boolean needle();                   // the vanilla needle works here (NORMAL worlds)
        Particle trail();
        int mapScale();                     // blocks per map pixel
        int[] mapCentre(Player p);          // the block at the map's middle
        byte paint(int x, int z);           // background colour of the map at a block (called per pixel while painting)
        List<Marker> markers(Player p);     // the realm's places on the map (the target and the player are added)
        default List<Label> labels(Player p) { return Collections.emptyList(); }   // names written on the map
        default String hud(Player p) { return null; }   // a warning shown after the way while the compass is held
        default int paintVersion() { return 0; }         // changes when the map's background must be painted again
    }

    static final class Task {
        final String title, hint, where; final boolean done; final Location target;
        Task(String title, String hint, boolean done, Location target, String where) {
            this.title = title; this.hint = hint; this.done = done; this.target = target; this.where = where;
        }
    }

    static final class Marker {
        final int x, z; final MapCursor.Type type;
        Marker(int x, int z, MapCursor.Type type) { this.x = x; this.z = z; this.type = type; }
    }

    static final class Label {
        final int x, z; final String text;
        Label(int x, int z, String text) { this.x = x; this.z = z; this.text = text; }
    }

    static final String ALL_TAG = "jr_beat_all";
    static final String[] REALMS = {"atlas", "ruins", "nether"};
    static final String[] REALM_NAMES = {"Atlas", "Drownhollow", "the Nether"};

    final JavaPlugin plugin;
    final Realm realm;
    final String beatTag, kitTag, guideTag;
    private final Map<UUID, Seen> seen = new HashMap<>();
    private MapView view;
    private long ticks;
    int given, guidesPlaced, victories, trails;

    static final class Seen {
        String signature; int current = -1;
        Location needle; boolean needleSet;
        long lastOffer, lastTrail, lastBar;
        int mapCx = Integer.MIN_VALUE, mapCz = Integer.MIN_VALUE, painted = 128, paintVersion; String mapFooter = ""; boolean outside;
        List<Task> tasks = Collections.emptyList();
        List<Marker> markers = Collections.emptyList();
    }

    GuideKit(JavaPlugin plugin, Realm realm) {
        this.plugin = plugin;
        this.realm = realm;
        this.beatTag = "jr_beat_" + realm.key();
        this.kitTag = "jr_kit_" + realm.key();
        this.guideTag = "jr_guide_" + realm.key();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        // Maps handed out before a restart keep their id: give it its drawing again at once (else they show blank).
        Bukkit.getScheduler().runTask(plugin, this::view);
    }

    boolean beaten(Player p) { return p.getScoreboardTags().contains(beatTag); }

    // ---- the three items --------------------------------------------------------------------------------------------
    private ItemStack mark(ItemStack s, String item) {
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
        NBTTagCompound tag = n.hasTag() ? n.getTag() : new NBTTagCompound();
        NBTTagCompound g = new NBTTagCompound();
        g.setString("realm", realm.key());
        g.setString("item", item);
        tag.set("JasprGuide", g);
        n.setTag(tag);
        return CraftItemStack.asBukkitCopy(n);
    }

    /** "compass", "checklist" or "map" when the stack is one of this realm's guide items, else null. */
    String kind(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
            if (!n.hasTag() || !n.getTag().hasKeyOfType("JasprGuide", 10)) return null;
            NBTTagCompound g = n.getTag().getCompound("JasprGuide");
            return realm.key().equals(g.getString("realm")) ? g.getString("item") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void named(ItemMeta m, String name, String... lore) {
        m.setDisplayName(ChatColor.RESET + "" + realm.colour() + name);
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(ChatColor.GRAY + s);
        m.setLore(l);
        m.addEnchant(Enchantment.DURABILITY, 1, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    ItemStack compass() {
        ItemStack s = new ItemStack(Material.COMPASS);
        ItemMeta m = s.getItemMeta();
        named(m, realm.title().replace("The ", "") + " Compass", "Points to your next task in " + realm.title() + ".",
            "Hold it: the way shows above your hotbar.", "Right-click: a trail of light and the task.");
        s.setItemMeta(m);
        return mark(s, "compass");
    }

    ItemStack checklist(Player p) {
        ItemStack s = mark(new ItemStack(Material.WRITTEN_BOOK), "checklist");
        write(s, p, tasks(p));
        return s;
    }

    ItemStack map() {
        MapView v = view();
        @SuppressWarnings("deprecation")
        ItemStack s = new ItemStack(Material.MAP, 1, v == null ? 0 : v.getId());
        ItemMeta m = s.getItemMeta();
        named(m, realm.title().replace("The ", "") + " Map", "Your next task is marked in red; you are the white arrow.",
            "It redraws itself as you go.");
        s.setItemMeta(m);
        return mark(s, "map");
    }

    private boolean has(Player p, String item) {
        for (ItemStack s : p.getInventory().getContents()) if (item.equals(kind(s))) return true;
        return false;
    }

    private void give(Player p, ItemStack s) {
        for (ItemStack left : p.getInventory().addItem(s).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        given++;
    }

    /** Hands over the items the player lacks (all three when {@code all}); returns how many were given. */
    int offer(Player p, boolean all) {
        int n = 0;
        if (all || !has(p, "compass")) { give(p, compass()); n++; }
        if (all || !has(p, "checklist")) { give(p, checklist(p)); n++; }
        if (all || !has(p, "map")) { give(p, map()); n++; }
        p.addScoreboardTag(kitTag);
        if (n > 0) plugin.getLogger().info("GUIDE_ITEMS realm=" + realm.key() + " given=" + n);
        return n;
    }

    // ---- tasks, progress and victory --------------------------------------------------------------------------------
    List<Task> tasks(Player p) {
        try { return realm.tasks(p); } catch (RuntimeException e) {
            plugin.getLogger().warning("GUIDE_TASKS_FAILED realm=" + realm.key() + " reason=" + e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    static int current(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) if (!tasks.get(i).done) return i;
        return -1;
    }

    /** The player conquered this realm: their crown, a title, the news, and the Three Realms if this was the last. */
    void victory(Player p) {
        if (beaten(p)) return;
        p.addScoreboardTag(beatTag);
        p.addScoreboardTag(beatTag + "_on_" + java.time.LocalDate.now());
        victories++;
        p.sendTitle(realm.colour() + "" + ChatColor.BOLD + "REALM CONQUERED", ChatColor.WHITE + realm.title() + ChatColor.GRAY + " is beaten", 10, 100, 30);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcastMessage(realm.colour() + p.getName() + ChatColor.GRAY + " has conquered " + realm.colour() + realm.title() + ChatColor.GRAY + "!");
        give(p, crown(realm.title().replace("The ", "") + " Conqueror's Crown", Material.GOLD_HELMET, "Proof that you conquered " + realm.title() + "."));
        refreshBooks(p);
        plugin.getLogger().info("GUIDE_VICTORY realm=" + realm.key() + " realmsBeaten=" + realmsBeaten(p));
        threeRealms(p);
    }

    /**
     * A player who conquered the realm before the guides existed (the realm remembers them): marked as conquered,
     * without the news, and handed the crown they earned.
     */
    void recordPast(Player p, String deed) {
        if (beaten(p)) return;
        p.addScoreboardTag(beatTag);
        victories++;
        give(p, crown(realm.title().replace("The ", "") + " Conqueror's Crown", Material.GOLD_HELMET, "Proof that you conquered " + realm.title() + "."));
        p.sendMessage(realm.colour() + realm.title() + ChatColor.GRAY + ": you conquered it already (" + deed + "). Here is your Conqueror's Crown.");
        refreshBooks(p);
        plugin.getLogger().info("GUIDE_PAST_VICTORY realm=" + realm.key() + " realmsBeaten=" + realmsBeaten(p));
        threeRealms(p);
    }

    static int realmsBeaten(Player p) {
        int beaten = 0;
        for (String r : REALMS) if (p.getScoreboardTags().contains("jr_beat_" + r)) beaten++;
        return beaten;
    }

    /** All three realms conquered: the Three Realms title and crown, once. */
    private void threeRealms(Player p) {
        if (realmsBeaten(p) == REALMS.length && p.addScoreboardTag(ALL_TAG)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "THREE REALMS", ChatColor.YELLOW + "Atlas, Drownhollow and the Nether are yours", 10, 120, 30);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_DEATH, 0.6f, 1.2f);
                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + p.getName() + " is Conqueror of the Three Realms!");
                ItemStack c = crown("Crown of the Three Realms", Material.CHAINMAIL_HELMET, "Atlas, Drownhollow and the Nether: all conquered.");
                c.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 2);
                c.addUnsafeEnchantment(Enchantment.DURABILITY, 3);
                give(p, c);
                plugin.getLogger().info("GUIDE_THREE_REALMS realm=" + realm.key());
            }, 120L);
        }
    }

    private ItemStack crown(String name, Material m, String line) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + line);
        lore.add(ChatColor.DARK_GRAY + "Won " + java.time.LocalDate.now());
        meta.setLore(lore);
        s.setItemMeta(meta);
        return s;
    }

    /** The date a tag like jr_beat_nether_on_2026-09-29 records, or null. */
    private String beatenOn(Player p) {
        for (String t : p.getScoreboardTags()) if (t.startsWith(beatTag + "_on_")) return t.substring(beatTag.length() + 4);
        return null;
    }

    // ---- the guides at the gates ------------------------------------------------------------------------------------
    /**
     * A player arrived: at a gate (a guide is placed beside it if none is near) or elsewhere (gate null). The first time
     * in this realm they are handed the three items; every time they are told their goal and next task.
     */
    void arrive(Player p, Location gate) {
        if (gate != null) ensureGuide(gate);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !realm.inRealm(p.getWorld())) return;
            List<Task> t = tasks(p);
            int cur = current(t);
            if (!p.getScoreboardTags().contains(kitTag)) {
                offer(p, true);
                say(p, "Welcome, traveller. Take these: a compass that points to your next task, a checklist that ticks itself off, and a map that marks the way.");
            } else if (!has(p, "compass") || !has(p, "checklist") || !has(p, "map")) {
                p.sendMessage(ChatColor.GRAY + "Lost your compass, checklist or map? The " + realm.colour() + realm.guideName() + ChatColor.GRAY
                    + " at the gate gives new ones: right-click them.");
            }
            if (beaten(p)) p.sendMessage(realm.colour() + realm.title() + ChatColor.GRAY + ": conquered. You may roam as you please.");
            else {
                p.sendMessage(realm.colour() + "" + ChatColor.BOLD + "Goal: " + ChatColor.WHITE + realm.goal() + ChatColor.GRAY + " (" + (Math.max(0, cur)) + " of " + t.size() + " tasks done)");
                if (cur >= 0) p.sendMessage(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + t.get(cur).title + ChatColor.GRAY + " - " + where(p, t.get(cur)));
            }
        }, 30L);
    }

    private void say(Player p, String text) {
        p.sendMessage(realm.colour() + "[" + realm.guideName() + "] " + ChatColor.WHITE + text);
    }

    /** The realm's guide standing within 12 blocks of a gate, spawning one on free ground beside it if there is none. */
    Villager ensureGuide(Location gate) {
        World w = gate.getWorld();
        if (w == null) return null;
        for (Entity e : w.getNearbyEntities(gate, 12, 8, 12)) if (e instanceof Villager && e.getScoreboardTags().contains(guideTag)) return (Villager) e;
        Location spot = spotNear(gate);
        if (spot == null) return null;
        Vector look = gate.toVector().subtract(spot.toVector()).setY(0);
        if (look.lengthSquared() > 1e-4) spot.setDirection(look);
        Villager v = w.spawn(spot, Villager.class, g -> {
            g.setProfession(realm.profession());
            g.setCustomName(realm.colour() + realm.guideName());
            g.setCustomNameVisible(true);
            g.setAI(false);
            g.setInvulnerable(true);
            g.setSilent(true);
            g.setCollidable(false);
            g.setRemoveWhenFarAway(false);
            g.addScoreboardTag("jr_guide");
            g.addScoreboardTag(guideTag);
        });
        guidesPlaced++;
        plugin.getLogger().info("GUIDE_PLACED realm=" + realm.key() + " at=" + spot.getBlockX() + "," + spot.getBlockY() + "," + spot.getBlockZ());
        return v;
    }

    /** Free standing room 2-5 blocks from the gate: solid floor (not lava or a portal), two blocks of air. */
    private static Location spotNear(Location gate) {
        World w = gate.getWorld();
        int gx = gate.getBlockX(), gy = gate.getBlockY(), gz = gate.getBlockZ();
        for (int r = 2; r <= 5; r++) for (int dy : new int[]{0, 1, -1, 2, -2}) for (int i = 0; i < 8 * r; i++) {
            double a = i * Math.PI * 2 / (8 * r);
            int x = gx + (int) Math.round(Math.cos(a) * r), z = gz + (int) Math.round(Math.sin(a) * r), y = gy + dy;
            Block feet = w.getBlockAt(x, y, z), below = feet.getRelative(0, -1, 0);
            if (feet.getType() != Material.AIR || feet.getRelative(0, 1, 0).getType() != Material.AIR) continue;
            Material m = below.getType();
            if (!m.isSolid() || m == Material.PORTAL || m == Material.MAGMA || m.name().contains("LAVA")) continue;
            boolean nearPortal = false;
            for (int dx = -1; dx <= 1 && !nearPortal; dx++) for (int dz = -1; dz <= 1 && !nearPortal; dz++)
                if (feet.getRelative(dx, 0, dz).getType() == Material.PORTAL) nearPortal = true;
            if (!nearPortal) return feet.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void talk(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager) || !e.getRightClicked().getScoreboardTags().contains(guideTag)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Seen s = seen(p);
        long now = System.currentTimeMillis();
        if (now - s.lastOffer < 1500) return;
        s.lastOffer = now;
        int n = offer(p, p.isSneaking());
        List<Task> t = tasks(p);
        int cur = current(t);
        if (n > 0) say(p, n == 3 ? "Here: a compass, a checklist and a map. Come back whenever you need new ones."
            : "Here, " + (n == 1 ? "a new one" : "new ones") + ". Sneak and right-click me for a whole new set.");
        else say(p, "You carry all three already. Sneak and right-click me if you want spares.");
        if (beaten(p)) say(p, "You have conquered " + realm.title() + ". Well done.");
        else if (cur >= 0) say(p, "Your goal: " + realm.goal() + ". Next: " + t.get(cur).title + " - " + where(p, t.get(cur)) + ".");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void guideSafe(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(guideTag)) e.setCancelled(true);
    }

    // ---- the heartbeat: progress, checklists, the compass --------------------------------------------------------
    private Seen seen(Player p) { return seen.computeIfAbsent(p.getUniqueId(), k -> new Seen()); }

    private void tick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { tick(p); } catch (RuntimeException ex) {
                plugin.getLogger().warning("GUIDE_TICK_FAILED realm=" + realm.key() + " reason=" + ex.getClass().getSimpleName());
            }
        }
    }

    private void tick(Player p) {
        Seen s = seen(p);
        if (!realm.inRealm(p.getWorld())) {
            if (s.needleSet) { p.setCompassTarget(p.getWorld().getSpawnLocation()); s.needleSet = false; s.needle = null; }
            return;
        }
        if ((ticks & 1) == 0 || s.signature == null) {
            List<Task> t = tasks(p);
            StringBuilder sig = new StringBuilder();
            for (Task k : t) sig.append(k.done ? '1' : '0');
            String now = sig.toString() + (beaten(p) ? "B" : "");
            if (s.signature != null && !now.equals(s.signature)) progressed(p, s.tasks, t);
            if (s.signature == null || !now.equals(s.signature)) refreshBooks(p, t);
            s.signature = now;
            s.tasks = t;
            s.current = current(t);
            try { s.markers = realm.markers(p); } catch (RuntimeException ex) { s.markers = Collections.emptyList(); }
        }
        Task cur = s.current >= 0 && s.current < s.tasks.size() ? s.tasks.get(s.current) : null;
        Location target = cur == null ? null : cur.target;
        if (realm.needle() && target != null && target.getWorld() == p.getWorld()
            && (s.needle == null || s.needle.distanceSquared(target) > 1 || !s.needleSet)) {
            p.setCompassTarget(target);
            s.needle = target.clone();
            s.needleSet = true;
        }
        if (holdingCompass(p)) {
            String line;
            if (beaten(p)) line = realm.colour() + realm.title() + ChatColor.GRAY + " - conquered";
            else if (cur == null) line = ChatColor.GRAY + "No task left";
            else line = ChatColor.YELLOW + "" + (s.current + 1) + "/" + s.tasks.size() + " " + ChatColor.WHITE + cur.title + ChatColor.DARK_GRAY + " | " + direction(p, cur);
            String extra = realm.hud(p);
            if (extra != null) line += ChatColor.DARK_GRAY + " | " + extra;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(line));
        }
    }

    /** Whether the player holds this realm's compass (its line then owns the action bar). */
    boolean holdingCompass(Player p) {
        return "compass".equals(kind(p.getInventory().getItemInMainHand())) || "compass".equals(kind(p.getInventory().getItemInOffHand()));
    }

    /** Titles and chat for tasks that just got done. */
    private void progressed(Player p, List<Task> before, List<Task> after) {
        for (int i = 0; i < after.size(); i++) {
            boolean was = i < before.size() && before.get(i).done;
            if (after.get(i).done && !was) {
                int cur = current(after);
                String next = cur >= 0 ? after.get(cur).title : realm.goal();
                if (!beaten(p)) p.sendTitle(ChatColor.GREEN + "Task done", ChatColor.WHITE + after.get(i).title, 5, 50, 15);   // else REALM CONQUERED stays
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GREEN + "Task done: " + ChatColor.WHITE + after.get(i).title
                    + (cur >= 0 ? ChatColor.YELLOW + "  Next: " + ChatColor.WHITE + next : ""));
                plugin.getLogger().info("GUIDE_TASK_DONE realm=" + realm.key() + " task=" + (i + 1) + "/" + after.size());
                return;
            }
        }
    }

    /** "240 blocks, ahead-left, 20 up" from where the player stands and faces. */
    String direction(Player p, Task t) {
        if (t.target == null || t.target.getWorld() != p.getWorld()) return t.where;
        Location l = p.getLocation();
        double dx = t.target.getX() - l.getX(), dz = t.target.getZ() - l.getZ(), dy = t.target.getY() - l.getY();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (dist < 4 && Math.abs(dy) < 4) return ChatColor.GREEN + "you are there";
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((yawTo - l.getYaw()) % 360 + 540) % 360 - 180;   // -180..180, positive = to the right
        String[] words = {"ahead", "ahead-right", "right", "behind-right", "behind", "behind-left", "left", "ahead-left"};
        int sector = (int) Math.floorMod(Math.round(rel / 45.0), 8L);
        String vertical = dy > 5 ? ", " + (int) dy + " up" : dy < -5 ? ", " + (int) -dy + " down" : "";
        return ChatColor.YELLOW + "" + dist + " blocks " + ChatColor.WHITE + words[sector] + ChatColor.GRAY + vertical;
    }

    /** "the Spore Cathedral, 420 blocks north-east, 20 down" (compass words, for chat and the book). */
    String where(Player p, Task t) {
        if (t.target == null || t.target.getWorld() != p.getWorld()) return t.where;
        Location l = p.getLocation();
        double dx = t.target.getX() - l.getX(), dz = t.target.getZ() - l.getZ(), dy = t.target.getY() - l.getY();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (dist < 4 && Math.abs(dy) < 4) return t.where + ", right here";
        String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        String bearing = n[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8L)];
        String vertical = dy > 5 ? ", " + (int) dy + " up" : dy < -5 ? ", " + (int) -dy + " down" : "";
        return t.where + ", " + dist + " blocks " + bearing + vertical;
    }

    /** "north", "east", "south" or "west": the side of (x, z) that a point lies on. */
    static String side(int x, int z, int[] at) {
        int dx = at[0] - x, dz = at[at.length - 1] - z;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    // ---- the compass: a trail of light ------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String k = kind(e.getItem());
        if (k == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && interactive(e.getClickedBlock())) return;   // chests, doors, levers work as usual
        Player p = e.getPlayer();
        if (k.equals("checklist")) { write(e.getItem(), p, tasks(p)); return; }   // the book opens with today's ticks
        if (!k.equals("compass")) return;
        Seen s = seen(p);
        long now = System.currentTimeMillis();
        if (now - s.lastTrail < 1000) return;
        s.lastTrail = now;
        if (!realm.inRealm(p.getWorld())) {
            p.sendMessage(realm.colour() + realm.title().replace("The ", "") + " Compass" + ChatColor.GRAY + " only works in " + realm.title()
                + ". Go through " + realm.gate() + ".");
            return;
        }
        List<Task> t = tasks(p);
        int cur = current(t);
        if (beaten(p) || cur < 0) { p.sendMessage(realm.colour() + realm.title() + ChatColor.GRAY + ": conquered. Nothing left to find."); return; }
        Task task = t.get(cur);
        p.sendMessage(realm.colour() + "Task " + (cur + 1) + " of " + t.size() + ": " + ChatColor.WHITE + task.title);
        p.sendMessage(ChatColor.GRAY + "Where: " + ChatColor.WHITE + where(p, task));
        p.sendMessage(ChatColor.GRAY + "How: " + task.hint);
        if (task.target != null && task.target.getWorld() == p.getWorld()) trail(p, task.target);
    }

    /** Blocks that a right-click uses (containers, doors, levers...): the compass and the checklist stay out of their way. */
    static boolean interactive(org.bukkit.block.Block b) {
        if (b == null) return false;
        switch (b.getType()) {
            case CHEST: case TRAPPED_CHEST: case ENDER_CHEST: case FURNACE: case BURNING_FURNACE: case DISPENSER: case DROPPER: case HOPPER:
            case BREWING_STAND: case BEACON: case WORKBENCH: case ENCHANTMENT_TABLE: case ANVIL: case CAULDRON: case JUKEBOX: case NOTE_BLOCK:
            case WOODEN_DOOR: case SPRUCE_DOOR: case BIRCH_DOOR: case JUNGLE_DOOR: case ACACIA_DOOR: case DARK_OAK_DOOR: case TRAP_DOOR:
            case FENCE_GATE: case SPRUCE_FENCE_GATE: case BIRCH_FENCE_GATE: case JUNGLE_FENCE_GATE: case ACACIA_FENCE_GATE: case DARK_OAK_FENCE_GATE:
            case LEVER: case STONE_BUTTON: case WOOD_BUTTON: case BED_BLOCK: case DIODE_BLOCK_OFF: case DIODE_BLOCK_ON:
            case REDSTONE_COMPARATOR_OFF: case REDSTONE_COMPARATOR_ON: case DAYLIGHT_DETECTOR: case DAYLIGHT_DETECTOR_INVERTED:
            case FLOWER_POT: case CAKE_BLOCK: case DRAGON_EGG: case COMMAND: case COMMAND_REPEATING: case COMMAND_CHAIN:
                return true;
            default:
                return b.getType().name().endsWith("SHULKER_BOX");
        }
    }

    private static boolean overlaps(List<int[]> taken, int left, int top, int w) {
        for (int[] r : taken) if (left <= r[2] && left + w >= r[0] && top <= r[3] && top + 8 >= r[1]) return true;
        return false;
    }

    /** A line of light flying from the player's eyes towards the target, seen only by that player. */
    private void trail(Player p, Location target) {
        trails++;
        Location eye = p.getEyeLocation();
        Vector dir = target.toVector().subtract(eye.toVector());
        double len = dir.length();
        if (len < 1) return;
        dir.multiply(1 / len);
        double reach = Math.min(len, 24);
        Particle kind = realm.trail();
        p.playSound(eye, Sound.BLOCK_NOTE_CHIME, 0.7f, 1.6f);
        for (int step = 0; step < 12; step++) {
            final int k = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                for (double d = k * reach / 12; d < (k + 1) * reach / 12; d += 0.4) {
                    Location at = eye.clone().add(dir.clone().multiply(d + 1));
                    p.spawnParticle(kind, at, 2, 0.03, 0.03, 0.03, 0);
                }
            }, step);
        }
    }

    // ---- the checklist ------------------------------------------------------------------------------------------------
    void refreshBooks(Player p) { refreshBooks(p, tasks(p)); }

    private void refreshBooks(Player p, List<Task> t) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {   // hotbar, pack, armour and off hand
            ItemStack s = inv.getItem(i);
            if ("checklist".equals(kind(s))) { write(s, p, t); inv.setItem(i, s); }
        }
    }

    /** Rewrites a checklist in place: the goal, every task ticked or not, the next task and where it is, the tips. */
    private void write(ItemStack book, Player p, List<Task> t) {
        if (!(book.getItemMeta() instanceof BookMeta)) return;
        BookMeta m = (BookMeta) book.getItemMeta();
        m.setTitle(realm.title().replace("The ", "") + " Checklist");
        m.setAuthor(realm.guideName());
        List<String> pages = new ArrayList<>();
        int done = 0;
        for (Task k : t) if (k.done) done++;
        String on = beatenOn(p);
        pages.add(ChatColor.BOLD + realm.title().toUpperCase(Locale.ROOT) + ChatColor.RESET + "\n\n" + ChatColor.DARK_RED + "Goal: " + ChatColor.BLACK + realm.goal()
            + "\n\n" + (beaten(p) ? ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "CONQUERED" + ChatColor.RESET + (on == null ? "" : "\n" + on) : done + " of " + t.size() + " tasks done.")
            + "\n\n" + ChatColor.DARK_GRAY + "The compass points to your next task; the map marks it in red. This book ticks each task off.");
        StringBuilder list = new StringBuilder(ChatColor.BOLD + "CHECKLIST" + ChatColor.RESET + "\n");
        int cur = current(t);
        for (int i = 0; i < t.size(); i++) {
            Task k = t.get(i);
            String line = (k.done ? ChatColor.DARK_GREEN + "√ " : i == cur ? ChatColor.BLACK + "» " : ChatColor.DARK_GRAY + "■ ") + k.title;
            if (list.length() + line.length() > 240) { pages.add(list.toString()); list = new StringBuilder(); }
            list.append('\n').append(line);
        }
        pages.add(list.toString());
        if (cur >= 0 && !beaten(p)) {
            Task k = t.get(cur);
            String next = ChatColor.BOLD + "NEXT" + ChatColor.RESET + "\n\n" + k.title + "\n\n" + ChatColor.DARK_GRAY + k.hint + "\n\n" + ChatColor.BLACK + ChatColor.stripColor(where(p, k)) + ".";
            pages.add(next.length() > 250 ? next.substring(0, 250) : next);
        }
        for (String tip : realm.tips()) pages.add(tip.length() > 250 ? tip.substring(0, 250) : tip);
        m.setPages(pages);
        book.setItemMeta(m);
    }

    // ---- the map ------------------------------------------------------------------------------------------------------
    /** The realm's one map id (every map handed out shows the same id; each player sees their own drawing). */
    MapView view() {
        if (view != null) return view;
        if (Bukkit.getWorlds().isEmpty()) return null;
        File f = new File(plugin.getDataFolder(), "guide-map.txt");
        try {
            if (f.exists()) {
                @SuppressWarnings("deprecation")
                MapView v = Bukkit.getMap(Short.parseShort(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim()));
                view = v;
            }
            if (view == null) {
                view = Bukkit.createMap(Bukkit.getWorlds().get(0));
                plugin.getDataFolder().mkdirs();
                @SuppressWarnings("deprecation")
                short id = view.getId();
                Files.write(f.toPath(), String.valueOf(id).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("GUIDE_MAP_FAILED realm=" + realm.key() + " reason=" + e.getClass().getSimpleName());
            if (view == null) return null;
        }
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.addRenderer(new Chart());
        @SuppressWarnings("deprecation")
        short id = view.getId();
        plugin.getLogger().info("GUIDE_MAP realm=" + realm.key() + " id=" + id);
        return view;
    }

    /**
     * Each player's own drawing: the realm around them (painted a few rows per tick, then sent whole), the next task as a
     * red marker (on the edge, pointing, when it lies beyond the map), the realm's places and the player's arrow.
     */
    final class Chart extends MapRenderer {
        Chart() { super(true); }

        @Override public void render(MapView v, MapCanvas c, Player p) {
            Seen s = seen(p);
            MapCursorCollection cursors = c.getCursors();
            while (cursors.size() > 0) cursors.removeCursor(cursors.getCursor(0));
            if (!realm.inRealm(p.getWorld())) {
                if (!s.outside) {
                    s.outside = true;
                    s.mapCx = Integer.MIN_VALUE;
                    byte bg = MapPalette.matchColor(90, 70, 50);
                    for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) c.setPixel(x, y, bg);
                    c.drawText(6, 50, MinecraftFont.Font, "This map shows");
                    c.drawText(6, 62, MinecraftFont.Font, realm.title() + " only.");
                    sendSoon(p, v);
                }
                return;
            }
            s.outside = false;
            int scale = realm.mapScale();
            int[] centre = realm.mapCentre(p);
            int version = realm.paintVersion();
            if (centre[0] != s.mapCx || centre[1] != s.mapCz || version != s.paintVersion) { s.mapCx = centre[0]; s.mapCz = centre[1]; s.paintVersion = version; s.painted = 0; }
            int x0 = s.mapCx - 64 * scale, z0 = s.mapCz - 64 * scale;
            if (s.painted < 128) {
                int until = Math.min(128, s.painted + 16);
                for (int y = s.painted; y < until; y++) for (int x = 0; x < 128; x++) c.setPixel(x, y, realm.paint(x0 + x * scale, z0 + y * scale));
                s.painted = until;
                if (s.painted >= 128) {
                    frame(c, s);
                    byte ink = MapPalette.matchColor(250, 245, 230);
                    List<int[]> taken = new ArrayList<>();   // label boxes already written: names never overlap
                    for (Label lb : realm.labels(p)) {
                        int px = (lb.x - x0) / scale, w = MinecraftFont.Font.getWidth(lb.text), left = px - w / 2;
                        if (left < 2 || left + w > 125) continue;
                        for (int dy : new int[]{5, -13, 15, -23}) {   // below the mark, above it, then further out
                            int top = (lb.z - z0) / scale + dy;
                            if (top < 13 || top > 118 || overlaps(taken, left, top, w)) continue;
                            c.drawText(left, top, MinecraftFont.Font, "§" + ink + ";" + lb.text);
                            taken.add(new int[]{left - 1, top - 1, left + w + 1, top + 9});
                            break;
                        }
                    }
                    sendSoon(p, v);
                }
            }
            Task cur = s.current >= 0 && s.current < s.tasks.size() ? s.tasks.get(s.current) : null;
            for (Marker m : s.markers) cursor(cursors, x0, z0, scale, m.x, m.z, m.type, 0, false);
            if (cur != null && cur.target != null && cur.target.getWorld() == p.getWorld() && !beaten(p))
                cursor(cursors, x0, z0, scale, cur.target.getBlockX(), cur.target.getBlockZ(), MapCursor.Type.RED_MARKER, 0, true);
            Location l = p.getLocation();
            byte dir = (byte) (Math.floorMod(Math.round(l.getYaw() / 22.5f), 16));
            cursor(cursors, x0, z0, scale, l.getBlockX(), l.getBlockZ(), MapCursor.Type.WHITE_POINTER, dir, true);
        }

        private void frame(MapCanvas c, Seen s) {
            byte edge = MapPalette.matchColor(40, 30, 20);
            for (int i = 0; i < 128; i++) { c.setPixel(i, 0, edge); c.setPixel(i, 127, edge); c.setPixel(0, i, edge); c.setPixel(127, i, edge); }
            byte band = MapPalette.matchColor(30, 24, 20);
            for (int x = 1; x < 127; x++) for (int y = 1; y < 11; y++) c.setPixel(x, y, band);
            c.drawText(3, 2, MinecraftFont.Font, "§" + MapPalette.matchColor(240, 220, 160) + ";" + fit(realm.title() + " - next: red", 120));
        }
    }

    private static String fit(String s, int px) {
        while (s.length() > 1 && MinecraftFont.Font.getWidth(s) > px) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** A map cursor for a block; outside the map it sits on the edge (pointing towards it when {@code pointEdge}). */
    private static void cursor(MapCursorCollection cs, int x0, int z0, int scale, int bx, int bz, MapCursor.Type type, int dir, boolean pointEdge) {
        double px = (bx - x0) / (double) scale, pz = (bz - z0) / (double) scale;   // 0..128 on the map
        boolean off = px < 2 || px > 126 || pz < 12 || pz > 126;
        if (off && !pointEdge) return;
        if (off) {
            double cx = px - 64, cz = pz - 69, k = Math.max(Math.abs(cx) / 62.0, Math.abs(cz) / 57.0);
            double ex = 64 + cx / k, ez = 69 + cz / k;
            dir = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(-cx, cz)) / 22.5), 16L);
            px = ex; pz = ez;
            if (type == MapCursor.Type.RED_MARKER) type = MapCursor.Type.RED_POINTER;
        }
        byte cx = (byte) Math.max(-128, Math.min(127, Math.round(px * 2 - 128)));
        byte cz = (byte) Math.max(-128, Math.min(127, Math.round(pz * 2 - 128)));
        cs.addCursor(new MapCursor(cx, cz, (byte) (dir & 15), type, true));
    }

    private void sendSoon(Player p, MapView v) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) p.sendMap(v); });
    }

    // ---- /goals: every realm's tasks, each realm plugin adding its own part --------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void goals(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(Locale.ROOT).trim();
        String word = m.split(" ")[0];
        if (!(word.equals("/goals") || word.equals("/goal") || word.equals("/quests") || word.equals("/quest") || word.equals("/checklist"))) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) for (String line : goalLines(p)) p.sendMessage(line); }, 1L + realm.order());
    }

    List<String> goalLines(Player p) {
        List<String> out = new ArrayList<>();
        if (realm.order() == 0) out.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Your goals" + ChatColor.GRAY + " (the three realms, in any order)");
        boolean here = realm.inRealm(p.getWorld());
        List<Task> t = tasks(p);
        int cur = current(t);
        out.add(realm.colour() + "" + ChatColor.BOLD + realm.title() + ChatColor.GRAY + " - " + ChatColor.WHITE + realm.goal()
            + (beaten(p) ? ChatColor.GREEN + "  CONQUERED" : ""));
        if (beaten(p)) return out;
        for (int i = 0; i < t.size(); i++) {
            Task k = t.get(i);
            out.add((k.done ? ChatColor.GREEN + "  √ " + ChatColor.GRAY : i == cur ? ChatColor.YELLOW + "  » " + ChatColor.WHITE : ChatColor.DARK_GRAY + "  ■ ") + k.title
                + (i == cur && here ? ChatColor.GRAY + "  (" + ChatColor.stripColor(where(p, k)) + ")" : ""));
        }
        if (!here) out.add(ChatColor.GRAY + "  Enter through " + realm.gate() + "; the " + realm.guideName() + " there gives you a compass, a checklist and a map.");
        return out;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { seen.remove(e.getPlayer().getUniqueId()); }

    /** One line of counters for status commands. */
    String status() {
        return "guide realm=" + realm.key() + " itemsGiven=" + given + " guidesPlaced=" + guidesPlaced + " victories=" + victories + " trails=" + trails
            + " map=" + (view == null ? "-" : String.valueOf(view.getId()));
    }
}
