package local.eagler.testserver;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Private channel attributes and exact Player identity; never a pending username/IP map. */
final class ConnectionSessions {
    private final AttributeKey<Session> key = AttributeKey.valueOf("jaspr-sso-" + UUID.randomUUID());
    private final Map<Player, Session> players = Collections.synchronizedMap(new IdentityHashMap<Player, Session>());
    Session attach(Channel channel, TicketVerifier.Identity identity) throws IOException {
        Session session = new Session(channel, identity);
        if (channel.attr(key).setIfAbsent(session) != null) throw new IOException("Connection already verified.");
        return session;
    }
    Session bind(Channel channel, Player player, long now) throws IOException {
        Session session = channel.attr(key).get();
        if (session == null || session.channel != channel || now >= session.identity.expiresAt) throw new IOException("No verified connection.");
        session.identity.requirePlayer(player.getName(), player.getUniqueId());
        synchronized (session) {
            if (!channel.isActive() || channel.attr(key).get() != session) throw new IOException("Connection closed.");
            if (session.player != null) throw new IOException("Connection already bound.");
            session.player = player; players.put(player, session);
        }
        return session;
    }
    Session get(Player player) {
        Session session = players.get(player);
        return session != null && session.player == player && session.channel.isActive()
                && session.channel.attr(key).get() == session ? session : null;
    }
    void remove(Player player) {
        Session session = players.remove(player);
        if (session != null) { session.authenticated = false; session.authRequested = false; session.channel.attr(key).set(null); }
    }
    void closed(Channel channel) {
        Session session = channel.attr(key).getAndSet(null);
        if (session != null) synchronized (session) {
            session.authenticated = false; session.authRequested = false;
            if (session.player != null) players.remove(session.player);
        }
    }
    static final class Session {
        final Channel channel;
        final TicketVerifier.Identity identity;
        volatile Player player;
        volatile boolean authRequested, authenticated, lookLogged, movementLogged;
        Session(Channel channel, TicketVerifier.Identity identity) { this.channel = channel; this.identity = identity; }
    }
}
