package chat.jaspr.backrooms;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Who may open the Backrooms (owner, 2026-09-30: "To access the dimension, you need to beat at least one dimension ...
 * this includes the Ender Dragon in the end dimension"). A conqueror is anyone who has conquered Atlas, Drownhollow or
 * the Nether (the guide kits' jr_beat_* tags), the Backrooms themselves, or the End: everyone near the Ender Dragon when
 * it dies, and anyone whose "Free the End" advancement is done (so dragon slayers from before this update count).
 * A player who becomes a conqueror is told, once, how to open the way.
 */
final class Conquest implements Listener {
    static final String END_TAG = "jr_beat_end";
    static final String[] REALM_TAGS = {"jr_beat_atlas", "jr_beat_ruins", "jr_beat_nether", "jr_beat_backrooms", END_TAG};

    private final BackroomsPlugin plugin;
    private final File toldFile;
    private final Set<String> told = new HashSet<>();
    private Advancement dragon;
    long dragonKills, toldCount;

    Conquest(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.toldFile = new File(plugin.getDataFolder(), "told-conquerors.txt");
        try { if (toldFile.isFile()) told.addAll(Files.readAllLines(toldFile.toPath(), StandardCharsets.UTF_8)); }
        catch (IOException e) { plugin.getLogger().warning("BACKROOMS_TOLD_UNREADABLE " + e.getClass().getSimpleName()); }
    }

    private Advancement dragonAdvancement() {
        if (dragon == null) dragon = Bukkit.getAdvancement(NamespacedKey.minecraft("end/kill_dragon"));
        return dragon;
    }

    /** Whether the player conquered any realm (or the End). */
    boolean conqueror(Player p) {
        for (String t : REALM_TAGS) if (p.getScoreboardTags().contains(t)) return true;
        Advancement a = dragonAdvancement();
        if (a != null) {
            AdvancementProgress progress = p.getAdvancementProgress(a);
            if (progress != null && progress.isDone()) { p.addScoreboardTag(END_TAG); return true; }
        }
        return false;
    }

    /** The realms a player conquered, in words ("Atlas and the End"). */
    static String conquered(Player p) {
        String[] names = {"Atlas", "Drownhollow", "the Nether", "the Backrooms", "the End"};
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < REALM_TAGS.length; i++) if (p.getScoreboardTags().contains(REALM_TAGS[i])) { if (n++ > 0) sb.append(", "); sb.append(names[i]); }
        return n == 0 ? "nothing yet" : sb.toString();
    }

    /** The Ender Dragon died: everyone in its End within 200 blocks has conquered the End. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragon(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon) || e.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (e.getEntity().getCustomName() != null) return;   // a plugin's dragon (a boss somewhere), not the End's own
        dragonKills++;
        int n = 0;
        for (Player p : e.getEntity().getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(e.getEntity().getLocation()) > 200 * 200) continue;
            if (!p.getScoreboardTags().contains(END_TAG) && p.addScoreboardTag(END_TAG)) {
                n++;
                p.sendTitle(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "THE END IS CONQUERED", ChatColor.YELLOW + "Something yellow hums in the distance...", 10, 90, 30);
            }
            tell(p);
        }
        plugin.getLogger().info("BACKROOMS_DRAGON_SLAIN conquerors=" + n + " kills=" + dragonKills);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && conqueror(p)) tell(p); }, 260L);
    }

    /** Every half minute: anyone who just became a conqueror elsewhere (a realm's victory) hears of the Backrooms. */
    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) if (!told.contains(p.getUniqueId().toString()) && conqueror(p)) tell(p);
    }

    private void tell(Player p) {
        if (!told.add(p.getUniqueId().toString())) return;
        toldCount++;
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "The Backrooms have noticed you. " + ChatColor.GRAY + "You conquered " + conquered(p)
                + ". Build a nether-portal frame of " + ChatColor.YELLOW + "yellow glazed terracotta" + ChatColor.GRAY + " (at least 4 wide, 5 tall) and light it with flint and steel."
                + " Only a conqueror can light it or pass through.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 0.6f, 0.5f);
            plugin.getLogger().info("BACKROOMS_CONQUEROR_TOLD realms=" + conquered(p).replace(", ", "+").replace(' ', '_'));
        }, 40L);
    }

    private void save() {
        try { plugin.getDataFolder().mkdirs(); Files.write(toldFile.toPath(), told, StandardCharsets.UTF_8); }
        catch (IOException e) { plugin.getLogger().warning("BACKROOMS_TOLD_UNSAVED " + e.getClass().getSimpleName()); }
    }
}
