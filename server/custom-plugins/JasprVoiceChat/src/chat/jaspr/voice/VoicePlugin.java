package chat.jaspr.voice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Always-on proximity voice for the browser client.
 *
 * There is no push-to-talk and nothing to switch on: the overlay opens the microphone as soon as
 * the player joins and transmits whenever it detects speech. Listening is unconditional, so a
 * nearby player is always audible whether or not they configured anything.
 */
public final class VoicePlugin extends JavaPlugin implements Listener {
    private VoiceLog log;
    private VoiceConfig settings;
    private PositionIndex positions;
    private org.bukkit.scheduler.BukkitTask environmentTask;
    private VoiceHub hub;
    private VoiceServer server;
    private BukkitTask positionTask;
    private BukkitTask rosterTask;

    @Override public void onEnable() {
        log = new VoiceLog(getLogger());
        saveDefaultConfig();
        settings = new VoiceConfig(getConfig());

        File serverRoot = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        Path projectRoot = serverRoot.getParentFile().toPath();
        EdgeAuth auth = new EdgeAuth(projectRoot.resolve("private/jaspr-bridge.key"));
        try {
            auth.checkKey();
        } catch (IOException missing) {
            // Fail closed: without the shared key no identity can be verified, so accept nobody.
            log.severe("Bridge key unavailable; voice chat stays closed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        positions = new PositionIndex(getServer(), settings);
        hub = new VoiceHub(settings, positions, log);
        server = new VoiceServer(settings.port, auth, log, new VoiceServer.Acceptor() {
            @Override public void accepted(WebSocketConnection connection, String playerName) {
                hub.attach(connection, playerName);
                connection.readLoop(hub);
            }
        });

        try {
            server.start();
        } catch (IOException failed) {
            log.severe("Voice relay could not bind to loopback port " + settings.port + ".");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        positionTask = getServer().getScheduler().runTaskTimer(this, positions, 1L, 1L);
        // Acoustics move far more slowly than voices do, so they ride their own slow timer.
        environmentTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { hub.broadcastEnvironment(); }
        }, 20L, Math.max(2L, settings.acousticIntervalTicks));
        rosterTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() { hub.broadcastRoster(); }
        }, 20L, 20L);
        getServer().getPluginManager().registerEvents(this, this);

        log.info("voice.enabled port=" + settings.port
                + " maxDistance=" + settings.maxDistance
                + " whisperDistance=" + settings.whisperDistance);
    }

    @Override public void onDisable() {
        if (positionTask != null) positionTask.cancel();
        if (environmentTask != null) environmentTask.cancel();
        if (rosterTask != null) rosterTask.cancel();
        if (server != null) server.stop();
        if (hub != null) hub.closeAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.announceOnJoin) return;
        final String name = event.getPlayer().getName();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                Player player = Bukkit.getPlayerExact(name);
                if (player == null) return;
                player.sendMessage(ChatColor.AQUA + "Voice chat is on."
                        + ChatColor.GRAY + " Nearby players hear you when you speak; sneak to whisper."
                        + " Use " + ChatColor.WHITE + "/voice" + ChatColor.GRAY + " for status.");
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (hub != null) hub.disconnectPlayer(event.getPlayer().getName());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Voice chat status is only meaningful in game.");
            return true;
        }
        Player player = (Player) sender;
        VoiceSession session = hub.sessionFor(player.getName());
        String action = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";

        if ("off".equals(action) || "disconnect".equals(action)) {
            if (session == null) {
                player.sendMessage(ChatColor.GRAY + "Voice chat is not connected.");
            } else {
                hub.sendNotice(session.connection, 1, "Voice chat stopped. Reload the page to rejoin.");
                session.connection.close();
                player.sendMessage(ChatColor.YELLOW + "Voice chat stopped for this tab.");
            }
            return true;
        }

        if ("who".equals(action)) {
            StringBuilder names = new StringBuilder();
            for (VoiceSession other : hub.sessions()) {
                if (names.length() > 0) names.append(", ");
                names.append(other.playerName);
            }
            player.sendMessage(ChatColor.AQUA + "Voice connected: "
                    + ChatColor.WHITE + (names.length() == 0 ? "nobody yet" : names.toString()));
            return true;
        }

        player.sendMessage(ChatColor.AQUA + "Proximity voice chat");
        player.sendMessage(ChatColor.GRAY + "  Status: " + (session == null
                ? ChatColor.RED + "not connected" + ChatColor.GRAY + " (reload JasperCraft and allow the microphone)"
                : ChatColor.GREEN + "connected" + ChatColor.GRAY
                  + (session.micMuted ? " (your mic is muted in the overlay)" : "")));
        player.sendMessage(ChatColor.GRAY + "  Hearing range: " + ChatColor.WHITE
                + (int) settings.maxDistance + ChatColor.GRAY + " blocks, whisper "
                + ChatColor.WHITE + (int) settings.whisperDistance + ChatColor.GRAY + " blocks while sneaking.");
        player.sendMessage(ChatColor.GRAY + "  Connected players: " + ChatColor.WHITE + hub.sessionCount());
        player.sendMessage(ChatColor.DARK_GRAY + "  Voice is always on. There is no key to hold.");
        return true;
    }
}
