package local.eagler.testserver;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.events.RestoreSessionEvent;
import io.netty.channel.Channel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerLoginConnection;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.EaglercraftLoginEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.EaglercraftWebSocketOpenEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginPostEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class JasprSsoBridge implements Listener {
    private static final String RECONNECT = "Open https://jaspr.chat/jaspercraft/ and choose Join / reconnect.";
    private final JavaPlugin plugin;
    private final TicketVerifier verifier;
    private final ConnectionSessions sessions = new ConnectionSessions();
    private final Set<Channel> channels = Collections.newSetFromMap(new ConcurrentHashMap<Channel, Boolean>());
    private final SecureRandom random = new SecureRandom();
    private final ReadyMarkerPublisher readyMarker;
    private final AuthMeApi auth;
    private volatile boolean accepting;
    private boolean readyMarkerWarningLogged;

    /**
     * Operator powers are granted by name, not by whatever the website currently
     * believes about an account. The web side can mark several identities as owners
     * -- and has -- so leaving the game to trust that flag alone means every one of
     * them is an operator here. When this list has entries, nobody outside it is
     * ever opped, however privileged their Jaspr session is.
     */
    private boolean minecraftOpAllowed(String gameName) {
        java.util.List<String> allowed = plugin.getConfig().getStringList("minecraft-op-allowlist");
        if (allowed.isEmpty()) return true;
        for (String name : allowed) if (name != null && name.equalsIgnoreCase(gameName)) return true;
        return false;
    }

    private boolean minecraftOpDenied(String gameName) {
        for (String denied : plugin.getConfig().getStringList("minecraft-op-denylist")) {
            if (denied != null && denied.equalsIgnoreCase(gameName)) return true;
        }
        return false;
    }

    private void lifecycle(String event, String details) {
        plugin.getLogger().info("JASPR_SSO event=" + event + (details == null || details.isEmpty() ? "" : " " + details));
    }

    private String authState(Player player) {
        try { return Boolean.toString(auth.isAuthenticated(player)); }
        catch (Exception unavailable) { return "unavailable"; }
    }

    JasprSsoBridge(JavaPlugin plugin, Path projectRoot) throws IOException {
        this.plugin = plugin;
        int port = plugin.getConfig().getInt("jaspr-bridge-port", 3200);
        if (port < 1 || port > 65535) throw new IOException("Invalid loopback bridge port.");
        verifier = new TicketVerifier(projectRoot.resolve("private/jaspr-bridge.key"),
                new URL("http://127.0.0.1:" + port + "/api/jaspercraft/internal/consume"));
        Path readyFile = projectRoot.resolve(".runtime/jaspr-sso-ready");
        Files.deleteIfExists(readyFile);
        readyMarker = new ReadyMarkerPublisher(readyFile);
        auth = AuthMeApi.getInstance();
        if (auth == null) throw new IOException("AuthMe API unavailable.");
        // A missing key leaves guards registered and admission closed. No password fallback.
        try { verifier.checkKey(); accepting = true; }
        catch (IOException missing) { plugin.getLogger().severe("Jaspr SSO key unavailable; game admission is closed."); }
        for (OfflinePlayer operator : Bukkit.getOperators()) operator.setOp(false);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSocket(EaglercraftWebSocketOpenEvent event) {
        if (event.isCancelled()) return;
        event.setCancelled(true);
        try {
            if (!accepting) throw new IOException();
            TicketVerifier.ticketFromPath(event.getConnection().getWebSocketPath());
            Channel channel = event.getConnection().netty().getChannel();
            channels.add(channel);
            channel.closeFuture().addListener(future -> { channels.remove(channel); sessions.closed(channel); });
            event.setCancelled(false);
            lifecycle("socket_accepted", "channel=" + channel.id().asShortText());
        } catch (Exception denied) {
            event.setCancelled(true);
            lifecycle("socket_rejected", "reason=invalid_admission");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEaglerLogin(EaglercraftLoginEvent event) {
        if (event.isCancelled()) return;
        event.setCancelled(true); // Exceptions must leave admission denied.
        event.setMessage(RECONNECT);
        long startedAt = System.currentTimeMillis();
        try {
            if (!accepting || !event.isAsynchronous()) return;
            IEaglerLoginConnection connection = event.getLoginConnection();
            Channel channel = connection.netty().getChannel();
            if (!channels.contains(channel)) return;
            TicketVerifier.Identity identity = verifier.consume(connection.getWebSocketPath());
            // The character is decided by the Jaspr ticket, never by the browser. A player
            // who opens the game's own profile screen and types a name -- and several have --
            // would otherwise be turned away at every future sign-in, because the name saved
            // in their browser no longer equalled their account. Overwrite it. On Bukkit the
            // login-start packet is rebuilt from this name and the server derives the offline
            // UUID from it, so name and UUID move together; PlayerLoginPostEvent still checks
            // the player that actually arrived against this same identity before binding.
            String claimed = event.getProfileUsername();
            if (!identity.gameName.equals(claimed)) {
                event.setProfileUsername(identity.gameName);
                lifecycle("identity_renamed", "player=" + identity.gameName + " claimed=" + safeName(claimed));
            }
            if (!accepting || !channel.isActive()) return;
            // Existing AuthMe rows/passwords remain intact. New rows use a random internal credential.
            if (!auth.isRegistered(identity.gameName)) {
                byte[] credential = new byte[24]; random.nextBytes(credential);
                if (!auth.registerPlayer(identity.gameName, Base64.getUrlEncoder().withoutPadding().encodeToString(credential))) return;
            }
            sessions.attach(channel, identity);
            event.setCancelled(false);
            lifecycle("identity_verified", "player=" + identity.gameName + " channel=" + channel.id().asShortText());
        } catch (Exception denied) {
            // Never log exception text: it might include a URL, ticket, or key. Classify
            // it instead. "verification_failed" covered six unrelated causes and so said
            // nothing at all -- an expired ticket and an unreachable sign-in service read
            // identically, which is no use to anybody at two in the morning.
            plugin.getLogger().warning("JASPR_SSO event=identity_rejected reason=" + reason(denied)
                    + " player=" + safeName(event.getProfileUsername())
                    + " afterMs=" + (System.currentTimeMillis() - startedAt));
        }
    }

    /** A fixed, secret-free label for why admission failed. */
    private static String reason(Exception denied) {
        String note = denied.getMessage() == null ? "" : denied.getMessage();
        if (note.startsWith("Open JasperCraft")) return "no_ticket_in_url";
        if (note.startsWith("Sign-in busy")) return "verifier_saturated";
        if (note.startsWith("Invalid sign-in")) return "malformed_identity";
        if (note.startsWith("Character does not match")) return "name_mismatch";
        if (note.startsWith("Bridge key")) return "bridge_key_unavailable";
        if (note.startsWith("Sign-in rejected:")) {
            String code = note.substring("Sign-in rejected:".length());
            if (code.equals("401")) return "ticket_expired_or_already_used";
            if (code.equals("403")) return "bridge_key_refused";
            if (code.equals("503")) return "sign_in_service_down";
            return "sign_in_status_" + safeCode(code);
        }
        if (denied instanceof java.io.IOException) return "sign_in_unreachable";
        return "internal_error";
    }

    /** Names come off the wire, so they are bounded before they reach a log line. */
    private static String safeName(String name) {
        if (name == null) return "unknown";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 16; i++) {
            char c = name.charAt(i);
            if (c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) out.append(c);
        }
        return out.length() == 0 ? "unknown" : out.toString();
    }

    private static String safeCode(String code) {
        return code.matches("[0-9]{3}") ? code : "other";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PlayerLoginPostEvent event) {
        if (event.isCancelled()) return;
        event.setCancelled(true); event.setMessage(RECONNECT);
        try {
            if (!accepting) return;
            ConnectionSessions.Session session = sessions.bind(event.netty().getChannel(), event.getPlayer(), System.currentTimeMillis());
            event.setCancelled(false);
            lifecycle("player_bound", "player=" + session.identity.gameName);
        } catch (Exception denied) {
            // A connection with no verified ticket is ordinary -- vanilla clients reach here
            // too -- and stays silent. A verified ticket whose player arrived under another
            // name is not ordinary: it means the login-time rename did not take, and that is
            // worth a line, because silently it looks exactly like the game losing a player.
            if (denied.getMessage() != null && denied.getMessage().startsWith("Character does not match")) {
                plugin.getLogger().warning("JASPR_SSO event=bind_rejected reason=rename_did_not_apply player="
                        + safeName(event.getPlayer().getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) { event.getPlayer().setOp(false); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer(); player.setOp(false);
        // AuthMe establishes its limbo in join handlers. Defer one tick so that setup has completed.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConnectionSessions.Session session = sessions.get(player);
            if (!accepting || session == null || System.currentTimeMillis() >= session.identity.expiresAt
                    || auth.isAuthenticated(player)) { deny(player); return; }
            session.authRequested = true;
            lifecycle("auth_requested", "player=" + session.identity.gameName);
            try { auth.forceLogin(player); } catch (Exception failed) { deny(player); return; }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sessions.get(player) == session && !session.authenticated) deny(player);
            }, 160L);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthPreLogin(AuthMeAsyncPreLoginEvent event) {
        ConnectionSessions.Session session = sessions.get(event.getPlayer());
        if (!accepting || session == null || !session.authRequested || session.authenticated
                || System.currentTimeMillis() >= session.identity.expiresAt) event.setCanLogin(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestoreSession(RestoreSessionEvent event) { event.setCancelled(true); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthenticated(LoginEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConnectionSessions.Session session = sessions.get(player);
            if (!accepting || session == null || !session.authRequested
                    || System.currentTimeMillis() >= session.identity.expiresAt || !auth.isAuthenticated(player)) {
                deny(player); return;
            }
            session.authenticated = true; session.authRequested = false;
            player.setOp(session.identity.privileged && minecraftOpAllowed(session.identity.gameName)
                    && !minecraftOpDenied(session.identity.gameName));
            lifecycle("auth_completed", "player=" + session.identity.gameName
                    + " authme=" + authState(player) + " op=" + player.isOp());
            player.sendMessage("One Jaspr.chat account works for chat and JasperCraft. Signed in as " + session.identity.gameName + ".");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        ConnectionSessions.Session session = sessions.get(event.getPlayer());
        if (session == null || !session.authenticated || event.getTo() == null) return;
        boolean positionChanged = event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
        boolean lookChanged = event.getFrom().getYaw() != event.getTo().getYaw()
                || event.getFrom().getPitch() != event.getTo().getPitch();
        if (positionChanged && !session.movementLogged) {
            session.movementLogged = true;
            lifecycle("movement_observed", "player=" + session.identity.gameName
                    + " cancelled=" + event.isCancelled() + " authme=" + authState(event.getPlayer()));
        } else if (lookChanged && !session.lookLogged) {
            session.lookLogged = true;
            lifecycle("look_observed", "player=" + session.identity.gameName
                    + " cancelled=" + event.isCancelled() + " authme=" + auth.isAuthenticated(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogout(LogoutEvent event) { Bukkit.getScheduler().runTask(plugin, () -> deny(event.getPlayer())); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ConnectionSessions.Session session = sessions.get(event.getPlayer());
        if (session != null) lifecycle("player_quit", "player=" + session.identity.gameName
                + " movementObserved=" + session.movementLogged + " lookObserved=" + session.lookLogged);
        event.getPlayer().setOp(false);
        sessions.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDependencyDisabled(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("AuthMe") || name.equals("EaglercraftXServer")) close();
    }

    boolean isPrivileged(Player player) {
        ConnectionSessions.Session session = sessions.get(player);
        return accepting && session != null && session.authenticated && session.identity.privileged && auth.isAuthenticated(player);
    }

    private void deny(Player player) {
        ConnectionSessions.Session session = sessions.get(player);
        lifecycle("player_denied", "player=" + (session == null ? "unknown" : session.identity.gameName)
                + " authme=" + authState(player));
        player.setOp(false); sessions.remove(player);
        if (player.isOnline()) player.kickPlayer(RECONNECT);
    }

    /** Reuses the existing one-second lifecycle tick. No browser heartbeat or rendering hook. */
    void tick() {
        if (!accepting) return;
        try {
            verifier.checkKey();
            if (!Bukkit.getPluginManager().isPluginEnabled("AuthMe")
                    || !Bukkit.getPluginManager().isPluginEnabled("EaglercraftXServer")) throw new IOException();
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean privileged = isPrivileged(player) && minecraftOpAllowed(player.getName())
                        && !minecraftOpDenied(player.getName());
                if (player.isOp() != privileged) player.setOp(privileged);
            }
        } catch (Exception failed) {
            close();
            return;
        }
        if (readyMarker.refresh(System.currentTimeMillis())) {
            if (readyMarkerWarningLogged) plugin.getLogger().info("Jaspr SSO readiness marker refresh recovered.");
            readyMarkerWarningLogged = false;
        } else if (!readyMarkerWarningLogged) {
            readyMarkerWarningLogged = true;
            plugin.getLogger().warning("Jaspr SSO readiness marker refresh was delayed; public admission remains fail-closed and will retry automatically.");
        }
    }

    void close() {
        accepting = false;
        try { readyMarker.clear(); } catch (IOException failed) { /* Gateway also rejects stale markers. */ }
        for (Player player : Bukkit.getOnlinePlayers()) deny(player);
        for (Channel channel : channels) channel.close();
        channels.clear();
    }
}
