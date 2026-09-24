package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/** Public, consent-based player teleportation. No operator privilege is consulted. */
public final class TeleportRequests implements Listener {
    public interface Lookup {
        Player byName(String name);
        Player byId(UUID id);
    }

    private static final class Request {
        final UUID requester;
        final UUID recipient;
        final boolean here;
        final long expiresAt;
        Request(UUID requester, UUID recipient, boolean here, long expiresAt) {
            this.requester = requester;
            this.recipient = recipient;
            this.here = here;
            this.expiresAt = expiresAt;
        }
    }

    private final ApocalypsePlugin plugin;
    private final Lookup lookup;
    private final Map<UUID, Request> incoming = new LinkedHashMap<UUID, Request>();
    private final Map<UUID, Request> outgoing = new LinkedHashMap<UUID, Request>();
    private BukkitTask expiryTask;
    private boolean started;

    public TeleportRequests(ApocalypsePlugin plugin) {
        this(plugin, new Lookup() {
            @Override public Player byName(String name) {
                Player exact = Bukkit.getPlayerExact(name);
                if (exact != null) return exact;
                for (Player player : Bukkit.getOnlinePlayers())
                    if (player.getName().equalsIgnoreCase(name)) return player;
                return null;
            }
            @Override public Player byId(UUID id) { return Bukkit.getPlayer(id); }
        });
    }

    /** Package-visible deterministic lookup is used only by the isolated Paper fixture. */
    public TeleportRequests(ApocalypsePlugin plugin, Lookup lookup) {
        this.plugin = plugin;
        this.lookup = lookup;
    }

    public void start() {
        if (started) return;
        started = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { expire(); }
        }, 20L, 20L);
        plugin.getLogger().info("TPA_READY timeoutSeconds=" + timeoutSeconds());
    }

    public void stop() {
        started = false;
        if (expiryTask != null) expiryTask.cancel();
        expiryTask = null;
        HandlerList.unregisterAll(this);
        incoming.clear();
        outgoing.clear();
    }

    public String metrics() { return "tpaPending=" + incoming.size(); }

    public static boolean targetSyntax(String[] args) {
        return args != null && args.length == 1 && args[0] != null && !args[0].trim().isEmpty();
    }

    public boolean command(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Teleport requests can only be used by players.");
            return true;
        }
        Player actor = (Player) sender;
        if (!plugin.authenticated(actor)) {
            actor.sendMessage(ChatColor.RED + "Sign in before using teleport requests.");
            return true;
        }
        expire();
        String name = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if ("tp".equals(name) || "teleport".equals(name) || "tpa".equals(name))
            return request(actor, args, false, "/tpa <player>");
        if ("tpahere".equals(name)) return request(actor, args, true, "/tpahere <player>");
        if ("tpaccept".equals(name)) return accept(actor, args);
        if ("tpdeny".equals(name)) return deny(actor, args);
        if ("tpcancel".equals(name)) return cancel(actor, args);
        return false;
    }

    private boolean request(Player requester, String[] args, boolean here, String usage) {
        if (!targetSyntax(args)) {
            requester.sendMessage(ChatColor.RED + "Usage: " + usage);
            return true;
        }
        Player recipient = lookup.byName(args[0].trim());
        if (recipient == null || !recipient.isOnline() || !plugin.authenticated(recipient)) {
            requester.sendMessage(ChatColor.RED + "That signed-in player is not online. Use the exact in-game name.");
            return true;
        }
        if (recipient.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "You are already there.");
            return true;
        }
        Request occupied = incoming.get(recipient.getUniqueId());
        if (occupied != null && !occupied.requester.equals(requester.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + recipient.getName() + " is considering another teleport request. Try again shortly.");
            return true;
        }
        Request previous = outgoing.get(requester.getUniqueId());
        if (previous != null) remove(previous);
        Request made = new Request(requester.getUniqueId(), recipient.getUniqueId(), here,
            System.currentTimeMillis() + timeoutSeconds() * 1000L);
        incoming.put(made.recipient, made);
        outgoing.put(made.requester, made);
        requester.sendMessage(ChatColor.GREEN + "Teleport request sent to " + recipient.getName() + ". Use /tpcancel to cancel it.");
        if (here) {
            recipient.sendMessage(ChatColor.AQUA + requester.getName() + " wants you to teleport to them.");
        } else {
            recipient.sendMessage(ChatColor.AQUA + requester.getName() + " wants to teleport to you.");
        }
        recipient.sendMessage(ChatColor.GRAY + "Use /tpaccept or /tpdeny. This request expires in " + timeoutSeconds() + " seconds.");
        plugin.getLogger().info("TPA_REQUEST requester=" + requester.getName() + " recipient=" + recipient.getName()
            + " direction=" + (here ? "recipient-to-requester" : "requester-to-recipient"));
        return true;
    }

    private boolean accept(Player recipient, String[] args) {
        if (args == null || args.length != 0) {
            recipient.sendMessage(ChatColor.RED + "Usage: /tpaccept");
            return true;
        }
        Request request = incoming.get(recipient.getUniqueId());
        if (request == null) {
            recipient.sendMessage(ChatColor.RED + "You have no pending teleport request.");
            return true;
        }
        remove(request); // Single-use even if another plugin cancels the teleport event.
        Player requester = lookup.byId(request.requester);
        if (!eligible(requester) || !eligible(recipient)) {
            recipient.sendMessage(ChatColor.RED + "That request is no longer available.");
            notify(requester, ChatColor.RED + "Your teleport request is no longer available.");
            return true;
        }
        Player mover = request.here ? recipient : requester;
        Player destinationPlayer = request.here ? requester : recipient;
        Location from = mover.getLocation().clone();
        Location destination = destinationPlayer.getLocation().clone();
        if (!mover.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND)) {
            mover.sendMessage(ChatColor.RED + "Teleport failed. Try a new request in a moment.");
            if (mover != recipient) recipient.sendMessage(ChatColor.RED + "The accepted teleport was blocked.");
            plugin.getLogger().warning("TPA_FAIL requester=" + requester.getName() + " recipient=" + recipient.getName()
                + " mover=" + mover.getName());
            return true;
        }
        mover.sendMessage(ChatColor.GREEN + "Teleported to " + destinationPlayer.getName() + ".");
        if (mover != recipient) recipient.sendMessage(ChatColor.GREEN + "Accepted " + requester.getName() + "'s teleport request.");
        else requester.sendMessage(ChatColor.GREEN + recipient.getName() + " accepted your teleport request.");
        plugin.getLogger().info("TPA_ACCEPT requester=" + requester.getName() + " recipient=" + recipient.getName()
            + " mover=" + mover.getName() + " from=" + location(from) + " to=" + location(destination));
        return true;
    }

    private boolean deny(Player recipient, String[] args) {
        if (args == null || args.length != 0) {
            recipient.sendMessage(ChatColor.RED + "Usage: /tpdeny");
            return true;
        }
        Request request = incoming.get(recipient.getUniqueId());
        if (request == null) {
            recipient.sendMessage(ChatColor.RED + "You have no pending teleport request.");
            return true;
        }
        remove(request);
        Player requester = lookup.byId(request.requester);
        recipient.sendMessage(ChatColor.GRAY + "Teleport request denied.");
        notify(requester, ChatColor.RED + recipient.getName() + " denied your teleport request.");
        plugin.getLogger().info("TPA_DENY requester=" + name(requester, request.requester) + " recipient=" + recipient.getName());
        return true;
    }

    private boolean cancel(Player requester, String[] args) {
        if (args == null || args.length != 0) {
            requester.sendMessage(ChatColor.RED + "Usage: /tpcancel");
            return true;
        }
        Request request = outgoing.get(requester.getUniqueId());
        if (request == null) {
            requester.sendMessage(ChatColor.RED + "You have no outgoing teleport request.");
            return true;
        }
        remove(request);
        Player recipient = lookup.byId(request.recipient);
        requester.sendMessage(ChatColor.GRAY + "Teleport request cancelled.");
        notify(recipient, ChatColor.GRAY + requester.getName() + " cancelled their teleport request.");
        plugin.getLogger().info("TPA_CANCEL requester=" + requester.getName() + " recipient=" + name(recipient, request.recipient));
        return true;
    }

    private boolean eligible(Player player) {
        return player != null && player.isOnline() && !player.isDead() && plugin.authenticated(player);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (Request request : new ArrayList<Request>(incoming.values())) {
            if (request.expiresAt > now) continue;
            remove(request);
            Player requester = lookup.byId(request.requester), recipient = lookup.byId(request.recipient);
            notify(requester, ChatColor.GRAY + "Your teleport request to " + name(recipient, request.recipient) + " expired.");
            notify(recipient, ChatColor.GRAY + "The teleport request from " + name(requester, request.requester) + " expired.");
            plugin.getLogger().info("TPA_EXPIRE requester=" + name(requester, request.requester)
                + " recipient=" + name(recipient, request.recipient));
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        Request sent = outgoing.get(event.getPlayer().getUniqueId());
        Request received = incoming.get(event.getPlayer().getUniqueId());
        if (sent != null) {
            remove(sent);
            notify(lookup.byId(sent.recipient), ChatColor.GRAY + event.getPlayer().getName() + " disconnected; their teleport request was cancelled.");
        }
        if (received != null && received != sent) {
            remove(received);
            notify(lookup.byId(received.requester), ChatColor.GRAY + event.getPlayer().getName() + " disconnected; your teleport request was cancelled.");
        }
    }

    private void remove(Request request) {
        if (incoming.get(request.recipient) == request) incoming.remove(request.recipient);
        if (outgoing.get(request.requester) == request) outgoing.remove(request.requester);
    }

    private int timeoutSeconds() {
        return Math.max(15, Math.min(300, plugin.getConfig().getInt("teleport.request-timeout-seconds", 60)));
    }

    private static void notify(Player player, String message) {
        if (player != null && player.isOnline()) player.sendMessage(message);
    }

    private static String name(Player player, UUID fallback) {
        return player == null ? fallback.toString() : player.getName();
    }

    private static String location(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
