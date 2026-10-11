package chat.jaspr.mutants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * AGPL-3.0 section 13: a modified program that people use over a network must prominently offer them its Corresponding
 * Source. Every player is told once, a few seconds after joining (so the line is not lost in the login chatter), where the
 * source of this port and of the original mod is: OPEN_SOURCE.md in the public JasperCraft repository (the jaspr.chat gateway
 * serves only the game files). Who was told is kept in plugins/JasprMutants/source-offer-v2.txt (one UUID a line; v2 because
 * 1.0.1 pointed at a page the gateway does not serve). Logs MUTANTS_SOURCE_OFFERED with counts, never names.
 */
final class SourceOffer implements Listener {
    static final String PAGE = "https://github.com/bnwo32324-boop/jaspercraft/blob/main/OPEN_SOURCE.md";
    static final String LINE = "This server runs a port of Mutant Creatures Legacy (AGPL-3.0). Its source code and our changes: ";
    private static final int MAX_REMEMBERED = 100000;
    private final Plugin plugin;
    private final File file;
    private final Set<String> told = new HashSet<String>();
    private int offered;

    SourceOffer(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "source-offer-v2.txt");
        try {
            if (this.file.isFile()) for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
                String id = line.trim();
                if (!id.isEmpty() && this.told.size() < MAX_REMEMBERED) this.told.add(id);
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("MUTANTS_SOURCE_OFFER_LOAD_FAILED " + ex.getClass().getSimpleName());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final String id = player.getUniqueId().toString();
        if (this.told.contains(id)) return;
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline() || this.told.contains(id)) return;
            player.sendMessage(ChatColor.GRAY + LINE + ChatColor.AQUA + PAGE);
            if (this.told.size() < MAX_REMEMBERED) this.told.add(id);
            this.offered++;
            try {
                this.plugin.getDataFolder().mkdirs();
                Files.write(this.file.toPath(), (id + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ex) {
                this.plugin.getLogger().warning("MUTANTS_SOURCE_OFFER_SAVE_FAILED " + ex.getClass().getSimpleName());
            }
            if (this.offered == 1 || this.offered % 50 == 0) this.plugin.getLogger().info("MUTANTS_SOURCE_OFFERED count=" + this.offered + " remembered=" + this.told.size());
        }, 100L);
    }
}
