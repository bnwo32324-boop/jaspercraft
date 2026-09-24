package local.eagler.testserver;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestServerControlPlugin extends JavaPlugin implements Listener {
    private long idleShutdownMillis;
    private long lastPlayerSeenMillis;
    private File stopRequestFile;
    private File readyFile;
    private boolean shutdownStarted;
    private JasprSsoBridge sso;
    private NetworkDiagnostics networkDiagnostics;
    private ConnectionKeeper connectionKeeper;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        long configuredIdleSeconds = getConfig().getLong("idle-shutdown-seconds", 0L);
        long idleSeconds = configuredIdleSeconds <= 0L ? 0L : Math.max(30L, configuredIdleSeconds);
        idleShutdownMillis = idleSeconds * 1000L;
        lastPlayerSeenMillis = System.currentTimeMillis();

        File serverRoot = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        File projectRoot = serverRoot.getParentFile();
        File runtimeRoot = new File(projectRoot, ".runtime");
        stopRequestFile = new File(runtimeRoot, "game-server-stop.request");
        readyFile = new File(runtimeRoot, "game-server-ready");

        try { sso = new JasprSsoBridge(this, projectRoot.toPath()); }
        catch (IOException failed) { throw new IllegalStateException("Jaspr SSO initialization failed; admission must remain closed."); }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::checkServerState, 20L, 20L);
        networkDiagnostics = new NetworkDiagnostics(this);
        networkDiagnostics.start();
        connectionKeeper = new ConnectionKeeper(this);
        connectionKeeper.start();

        if (!runtimeRoot.isDirectory() && !runtimeRoot.mkdirs() && !runtimeRoot.isDirectory()) {
            throw new IllegalStateException("Could not create runtime directory: " + runtimeRoot);
        }
        try {
            if (!readyFile.createNewFile() && !readyFile.isFile()) {
                throw new IOException("Ready marker was not created");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not publish the game-server readiness marker", ex);
        }

        if (idleShutdownMillis == 0L) {
            getLogger().info("Always-on mode enabled; idle shutdown is disabled.");
        } else {
            getLogger().info("Idle shutdown is " + idleSeconds + " seconds.");
        }
        getLogger().info("Administrative commands require a verified privileged Jaspr session.");
    }

    @Override
    public void onDisable() {
        if (connectionKeeper != null) connectionKeeper.stop();
        if (networkDiagnostics != null) networkDiagnostics.stop();
        if (sso != null) sso.close();
        if (readyFile != null && readyFile.isFile() && !readyFile.delete()) {
            getLogger().warning("Could not delete the game-server readiness marker.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastPlayerSeenMillis = System.currentTimeMillis();
        if (networkDiagnostics != null) networkDiagnostics.playerJoined(player);
        if (connectionKeeper != null) connectionKeeper.playerJoined(player);
        player.sendMessage(ChatColor.AQUA + "Signing in with your Jaspr.chat account...");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (networkDiagnostics != null) networkDiagnostics.playerQuit(event.getPlayer());
        if (connectionKeeper != null) connectionKeeper.playerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        PlayerCommandPolicy.Decision decision = PlayerCommandPolicy.classify(event.getMessage());
        if (decision == PlayerCommandPolicy.Decision.BLOCK_AUTH_COMMAND) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.AQUA + "Use Jaspr.chat to sign in or create an account. One account works for chat and JasperCraft.");
            return;
        }
        // JasprApocalypse applies authentication and recipient consent. This gate must not require operator status.
        if (decision == PlayerCommandPolicy.Decision.PUBLIC_COMMAND) return;
        if (sso != null && sso.isPrivileged(player)) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "This command requires a verified Jaspr administrator.");
    }

    private void checkServerState() {
        if (sso != null) sso.tick();
        if (shutdownStarted) {
            return;
        }
        if (stopRequestFile.isFile()) {
            if (!stopRequestFile.delete()) {
                getLogger().warning("Could not delete the stop request file.");
            }
            shutdownStarted = true;
            getLogger().info("Local maintenance stop requested; shutting down gracefully.");
            Bukkit.shutdown();
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            lastPlayerSeenMillis = System.currentTimeMillis();
            return;
        }
        if (idleShutdownMillis > 0L && System.currentTimeMillis() - lastPlayerSeenMillis >= idleShutdownMillis) {
            shutdownStarted = true;
            getLogger().info("No players are online; shutting down after the idle timeout.");
            Bukkit.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (sso == null || !sso.isPrivileged(player)) {
            player.sendMessage(ChatColor.RED + "A verified Jaspr administrator is required.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("jasprnet")) {
            if (!player.hasPermission("testserver.network")) {
                player.sendMessage(ChatColor.RED + "A verified Jaspr administrator is required.");
                return true;
            }
            Player target = player;
            if (args.length > 0) {
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }
            }
            player.sendMessage(ChatColor.AQUA + (networkDiagnostics == null
                    ? "Network diagnostics are unavailable."
                    : networkDiagnostics.status(target)));
            return true;
        }
        if (!player.hasPermission("testserver.mode")) {
            player.sendMessage(ChatColor.RED + "A verified Jaspr administrator is required.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("creative")) {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + "Game mode changed to Creative.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("survival")) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(ChatColor.GREEN + "Game mode changed to Survival.");
            return true;
        }
        return false;
    }
}
