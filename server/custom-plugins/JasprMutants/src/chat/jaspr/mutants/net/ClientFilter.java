package chat.jaspr.mutants.net;

import chat.jaspr.mutants.registry.SoundRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * MUTANTS_PROTOCOL.md sections 2 (op 0 HELLO) and 5: a client that has the JASPR_MUTANTS stage says HELLO once per
 * connection; until it has, the server drops the two packet kinds a client without the stage cannot handle - sound
 * effects with the mod's sound ids (1000-1042: no SoundEvent there) and particles of the mod's particle types (100, 101).
 * The filter is a Netty outbound handler in front of Paper's packet handler of each player connection.
 */
public final class ClientFilter implements Listener, PluginMessageListener {
    public static final String CHANNEL = "jaspr:mutants";
    public static final int PROTOCOL = 1;
    private static final String HANDLER = "jaspr_mutants_filter";
    private static Logger log = Logger.getLogger("JasprMutants");
    private static Field soundField;
    private static Field particleField;
    private final Map<UUID, Filter> filters = new ConcurrentHashMap<UUID, Filter>();
    private final AtomicLong filteredSounds = new AtomicLong();
    private final AtomicLong filteredParticles = new AtomicLong();
    private long hellos;
    private long rejected;
    private int injectFailures;

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public long helloCount() {
        return this.hellos;
    }

    public long filteredSoundCount() {
        return this.filteredSounds.get();
    }

    public long filteredParticleCount() {
        return this.filteredParticles.get();
    }

    public boolean hasStage(Player player) {
        Filter f = this.filters.get(player.getUniqueId());
        return f != null && f.hello;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        this.inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.filters.remove(event.getPlayer().getUniqueId());
    }

    public void inject(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().connection.netManager.channel;
            if (channel == null || !channel.isOpen()) return;
            Filter filter = new Filter();
            this.filters.put(player.getUniqueId(), filter);
            channel.eventLoop().execute(() -> {
                if (channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER);
                if (channel.pipeline().get("packet_handler") != null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER, filter);
                } else {
                    channel.pipeline().addLast(HANDLER, filter);
                }
            });
        } catch (RuntimeException e) {
            if (this.injectFailures++ < 20) log.warning("MUTANTS_FILTER_INJECT_FAILED reason=" + e.getClass().getSimpleName());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel)) return;
        if (data == null || data.length < 2 || data.length > 128 || data[0] != 0) {
            this.reject("hello_size_or_op");
            return;
        }
        int version = data[1] & 0x7F; // VarInt; versions below 128 are one byte
        if ((data[1] & 0x80) != 0) {
            this.reject("hello_version");
            return;
        }
        Filter f = this.filters.get(player.getUniqueId());
        if (f == null) {
            this.inject(player);
            f = this.filters.get(player.getUniqueId());
        }
        if (f != null && !f.hello) {
            f.hello = version >= PROTOCOL;
            this.hellos++;
            log.info("MUTANTS_CLIENT_HELLO version=" + version + " player=" + player.getName());
        }
    }

    private void reject(String reason) {
        this.rejected++;
        if (this.rejected <= 20 || this.rejected % 500 == 0) log.warning("MUTANTS_PACKET_REJECTED channel=" + CHANNEL + " reason=" + reason + " total=" + this.rejected);
    }

    private final class Filter extends ChannelDuplexHandler {
        volatile boolean hello;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!this.hello) {
                if (msg instanceof SPacketSoundEffect && modSound((SPacketSoundEffect) msg)) {
                    filteredSounds.incrementAndGet();
                    promise.trySuccess();
                    return;
                }
                if (msg instanceof SPacketParticles && modParticle((SPacketParticles) msg)) {
                    filteredParticles.incrementAndGet();
                    promise.trySuccess();
                    return;
                }
            }
            super.write(ctx, msg, promise);
        }
    }

    static boolean modSound(SPacketSoundEffect packet) {
        try {
            if (soundField == null) {
                Field f = SPacketSoundEffect.class.getDeclaredField("a"); // Spigot name of SPacketSoundEffect.sound
                f.setAccessible(true);
                soundField = f;
            }
            int id = SoundEvent.REGISTRY.getIDForObject((SoundEvent) soundField.get(packet));
            return id >= SoundRegistry.FIRST_ID && id < SoundRegistry.FIRST_ID + 43;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static boolean modParticle(SPacketParticles packet) {
        try {
            if (particleField == null) {
                Field f = SPacketParticles.class.getDeclaredField("a"); // Spigot name of SPacketParticles.particleType
                f.setAccessible(true);
                particleField = f;
            }
            EnumParticleTypes type = (EnumParticleTypes) particleField.get(packet);
            return type != null && type.getParticleID() >= 100;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
