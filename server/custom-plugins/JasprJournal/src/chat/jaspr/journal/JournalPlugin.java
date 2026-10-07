package chat.jaspr.journal;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.PacketDataSerializer;
import net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * The Field Journal's server half (owner, 2026-10-07: the empty space in the wide inventory should show an event forecast, a
 * character summary and the effects items keep on you). The browser client says "hello 1" on the plugin channel jaspr:journal
 * after joining; from then on this plugin sends that player's panel as one small JSON string (see {@link JournalRules.Snapshot})
 * whenever it changes, and at least every ten seconds so the client can tell the link is alive.
 *
 * Only information a player could already get is sent: the Blood Moon cycle, their own invasion mark (the /invasion status line),
 * a coarse disaster hint (never the exact time: that is administrator information), their own level and stat summary, and the
 * trinkets they wear (Survivor Gear) and the silent effects (ambient, particle-less) that items keep on them. Nothing is accepted from the client but the hello.
 */
public final class JournalPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    static final String CHANNEL = "jaspr:journal";
    private static final long HEARTBEAT_TICKS = 200L;
    private static final int MAX_MESSAGES_PER_SECOND = 6;
    private static final long METRICS_TICKS = 20L * 60L * 5L;

    private static final class Client {
        long helloTick = -100L, lastSent = Long.MIN_VALUE / 2, windowStart;
        int messages;
        String lastKey = "";
    }

    private final Map<UUID, Client> clients = new HashMap<UUID, Client>();
    private JournalSources sources;
    private long tick, sends, hellos, rejected, sendFailures;

    @Override public void onEnable() {
        sources = new JournalSources(getLogger());
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() { @Override public void run() { pump(); } }, 20L, 20L);
        getLogger().info("JOURNAL_READY version=" + getDescription().getVersion() + " channel=" + CHANNEL);
    }

    @Override public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        Bukkit.getScheduler().cancelTasks(this);
        clients.clear();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clients.remove(event.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------------------------------------------- the client
    @Override public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!CHANNEL.equals(channel) || !Bukkit.isPrimaryThread()) return;
        Client c = clients.get(player.getUniqueId());
        boolean fresh = c == null;
        if (fresh) c = new Client();
        // At most a handful of messages a second from one player, whatever they say.
        if (tick - c.windowStart >= 1L) { c.windowStart = tick; c.messages = 0; }
        if (++c.messages > MAX_MESSAGES_PER_SECOND) { rejected++; return; }
        String message;
        try { message = decode(bytes); } catch (IOException e) { rejected++; return; }
        if (!"hello 1".equals(message)) { rejected++; return; }
        if (!fresh && tick - c.helloTick < 20L) return;
        c.helloTick = tick;
        c.lastKey = "";
        clients.put(player.getUniqueId(), c);
        hellos++;
        getLogger().info("JOURNAL_CLIENT_HELLO clients=" + clients.size());
        send(player, c, true);
    }

    /** A string payload (what the client's readString reads): a variable-length size, then UTF-8. Bounded. */
    static String decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > 64) throw new IOException("size");
        int length = 0, shift = 0, at = 0;
        while (true) {
            if (at >= bytes.length || shift > 14) throw new IOException("varint");
            int b = bytes[at++] & 0xFF;
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (length != bytes.length - at) throw new IOException("length");
        return new String(bytes, at, length, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------------------------------------- the pump
    private void pump() {
        tick += 20L;
        Iterator<Map.Entry<UUID, Client>> it = clients.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Client> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) { it.remove(); continue; }
            send(player, entry.getValue(), false);
        }
        if (tick % METRICS_TICKS == 0L) {
            getLogger().info("JOURNAL_METRICS clients=" + clients.size() + " sends=" + sends + " hellos=" + hellos
                    + " rejected=" + rejected + " sendFailures=" + sendFailures
                    + " siegeFail=" + sources.failures(JournalSources.Source.SIEGE) + " invasionFail=" + sources.failures(JournalSources.Source.INVASIONS)
                    + " disasterFail=" + sources.failures(JournalSources.Source.DISASTERS) + " rpgFail=" + sources.failures(JournalSources.Source.RPG)
                    + " gearFail=" + sources.failures(JournalSources.Source.GEAR));
        }
    }

    /** Builds this player's panel and sends it when it changed (or when forced, or the heartbeat is due). */
    private void send(Player player, Client c, boolean force) {
        JournalRules.Snapshot snapshot;
        try { snapshot = snapshot(player); }
        catch (RuntimeException error) { sendFailures++; return; }
        String key = snapshot.json(false);
        if (!force && key.equals(c.lastKey) && tick - c.lastSent < HEARTBEAT_TICKS) return;
        try {
            PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
            buf.a(snapshot.json(true));
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutCustomPayload(CHANNEL, buf));
            c.lastKey = key;
            c.lastSent = tick;
            sends++;
        } catch (RuntimeException error) {
            sendFailures++;
            getLogger().warning("JOURNAL_SEND_FAILED error=" + error.getClass().getSimpleName());
        }
    }

    /** The payload as it would be sent to this player (what the real-server test inspects; nothing is sent). */
    public String panelJson(Player player) { return snapshot(player).json(true); }

    /** Everything the panel shows for one player, from the main world's clock and the other plugins. */
    JournalRules.Snapshot snapshot(Player player) {
        JournalRules.Snapshot s = new JournalRules.Snapshot();
        List<World> worlds = Bukkit.getWorlds();
        long full = worlds.isEmpty() ? 0L : worlds.get(0).getFullTime();
        UUID id = player.getUniqueId();
        s.day = JournalRules.day(full);
        s.phase = JournalRules.phase(full);
        JournalRules.BloodMoonRule rule = sources.bloodMoonRule();
        if (rule != null) s.moon = Integer.valueOf(JournalRules.bloodMoonNights(full, rule));
        s.invasion = sources.invasion(id, full);
        String disaster = sources.disaster();
        if (disaster != null) {
            s.disaster = Integer.valueOf(JournalRules.disasterState(disaster));
            s.disasterKind = JournalRules.disasterKind(disaster);
        }
        s.level = Math.max(0, Math.min(9999, player.getLevel()));
        s.xp = Math.max(0, Math.min(40, Math.round(player.getExp() * 40f)));
        s.rpg = sources.stats(id, player.getLevel());
        List<String[]> worn = sources.gear(player);
        if (worn != null) for (String[] row : worn) if (row != null && row.length > 0) s.gear(row[0], JournalRules.headline(row));
        for (PotionEffect effect : player.getActivePotionEffects()) {
            // The effects items keep on their bearer: ambient and without particles (the client lists no box for them).
            if (!effect.isAmbient() || effect.hasParticles()) continue;
            s.effect(JournalRules.effectName(effect.getType().getName(), effect.getAmplifier()), JournalRules.shownSeconds(effect.getDuration()));
        }
        return s;
    }
}
