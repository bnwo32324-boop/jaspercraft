package chat.jaspr.mutants.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * The Paper side of the mod's SimpleNetworkWrapper channel (MUTANTS_PROTOCOL.md section 3). Bytes are FML's
 * SimpleIndexedCodec: one discriminator byte, then the message's toBytes. Outgoing: NMS custom payload packets (no
 * REGISTER handshake), sent to the same players FML would pick. Incoming (client to server): Bukkit's Messenger on the
 * main thread, with a size check per message, bounded reads and the entity check of {@link IncomingValidator}.
 */
public final class ModChannel implements PluginMessageListener {
    private static final Map<String, ModChannel> CHANNELS = new ConcurrentHashMap<String, ModChannel>();
    private static Logger log = Logger.getLogger("JasprMutants");

    private final String name;
    private final Map<Integer, Class<? extends IMessage>> messages = new ConcurrentHashMap<Integer, Class<? extends IMessage>>();
    private final Map<Class<?>, Integer> discriminators = new ConcurrentHashMap<Class<?>, Integer>();
    private final Map<Integer, IMessageHandler<IMessage, ? extends IMessage>> serverHandlers = new ConcurrentHashMap<Integer, IMessageHandler<IMessage, ? extends IMessage>>();
    private final Map<Integer, IncomingValidator> validators = new ConcurrentHashMap<Integer, IncomingValidator>();
    private volatile long sent;
    private volatile long received;
    private volatile long rejected;

    /** Checks one incoming message before the mod's handler runs: exact payload size and a valid target. */
    public interface IncomingValidator {
        /** @return null when valid, else a short reason (logged as MUTANTS_PACKET_REJECTED reason=...). */
        String validate(EntityPlayerMP player, byte[] payload);
    }

    private ModChannel(String name) {
        this.name = name;
    }

    public static ModChannel create(String name) {
        ModChannel c = new ModChannel(name);
        CHANNELS.put(name, c);
        return c;
    }

    public static ModChannel get(String name) {
        return CHANNELS.get(name);
    }

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public String name() {
        return this.name;
    }

    public long sentCount() {
        return this.sent;
    }

    public long receivedCount() {
        return this.received;
    }

    public long rejectedCount() {
        return this.rejected;
    }

    @SuppressWarnings("unchecked")
    public <REQ extends IMessage, REPLY extends IMessage> void register(Class<? extends IMessageHandler<REQ, REPLY>> handlerClass, Class<REQ> type, int discriminator, Side side) {
        IMessageHandler<REQ, REPLY> handler;
        try {
            handler = handlerClass.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate message handler " + handlerClass.getName(), e);
        }
        this.registerInstance(handler, type, discriminator, side);
    }

    @SuppressWarnings("unchecked")
    public <REQ extends IMessage, REPLY extends IMessage> void registerInstance(IMessageHandler<? super REQ, ? extends REPLY> handler, Class<REQ> type, int discriminator, Side side) {
        this.messages.put(discriminator, type);
        this.discriminators.put(type, discriminator);
        if (side == Side.SERVER) {
            this.serverHandlers.put(discriminator, (IMessageHandler<IMessage, ? extends IMessage>) handler);
        }
    }

    public void setValidator(int discriminator, IncomingValidator validator) {
        this.validators.put(discriminator, validator);
    }

    public int discriminatorOf(Class<?> type) {
        Integer d = this.discriminators.get(type);
        return d == null ? -1 : d;
    }

    public void listen(Plugin plugin) {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, this.name, this);
    }

    /** The custom payload packet FML would send for this message. */
    public SPacketCustomPayload packet(IMessage message) {
        Integer disc = this.discriminators.get(message.getClass());
        if (disc == null) throw new IllegalArgumentException("Unregistered message " + message.getClass().getName());
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(disc);
        message.toBytes(buf);
        return new SPacketCustomPayload(this.name, new PacketBuffer(buf));
    }

    public void sendTo(IMessage message, EntityPlayerMP player) {
        if (player.connection == null) return;
        player.connection.sendPacket(this.packet(message));
        this.sent++;
    }

    public void sendToAll(IMessage message) {
        SPacketCustomPayload pkt = this.packet(message);
        for (EntityPlayerMP p : MinecraftServer.getServer$paper().getPlayerList().getPlayers()) {
            if (p.connection != null) {
                p.connection.sendPacket(pkt);
                this.sent++;
            }
        }
    }

    /** FML ALLAROUNDPOINT: players of the point's world (or dimension id) within range. */
    public void sendToAllAround(IMessage message, NetworkRegistry.TargetPoint point) {
        SPacketCustomPayload pkt = this.packet(message);
        double r2 = point.range * point.range;
        for (EntityPlayerMP p : MinecraftServer.getServer$paper().getPlayerList().getPlayers()) {
            if (point.world != null ? p.world != point.world : p.dimension != point.dimension) continue;
            double dx = point.x - p.posX;
            double dy = point.y - p.posY;
            double dz = point.z - p.posZ;
            if (dx * dx + dy * dy + dz * dz < r2 && p.connection != null) {
                p.connection.sendPacket(pkt);
                this.sent++;
            }
        }
    }

    /** FML TRACKING_ENTITY: every player tracking the entity (the entity itself excluded). */
    public void sendToAllTracking(IMessage message, Entity entity) {
        World world = entity.world;
        if (!(world instanceof WorldServer)) return;
        SPacketCustomPayload pkt = this.packet(message);
        ((WorldServer) world).getEntityTracker().sendToTracking(entity, pkt);
        this.sent++;
    }

    public void sendToDimension(IMessage message, int dimensionId) {
        SPacketCustomPayload pkt = this.packet(message);
        for (EntityPlayerMP p : MinecraftServer.getServer$paper().getPlayerList().getPlayers()) {
            if (p.dimension == dimensionId && p.connection != null) {
                p.connection.sendPacket(pkt);
                this.sent++;
            }
        }
    }

    @Override
    public void onPluginMessage(String channel, Player player, byte[] data) {
        if (!this.name.equals(channel) || !(player instanceof CraftPlayer)) return;
        EntityPlayerMP mp = ((CraftPlayer) player).getHandle();
        if (data == null || data.length < 1 || data.length > 1024) {
            this.reject("size", data == null ? -1 : data.length);
            return;
        }
        int disc = data[0] & 0xFF;
        Class<? extends IMessage> type = this.messages.get(disc);
        IMessageHandler<IMessage, ? extends IMessage> handler = this.serverHandlers.get(disc);
        if (type == null || handler == null) {
            this.reject("unknown_discriminator", disc);
            return;
        }
        IncomingValidator validator = this.validators.get(disc);
        if (validator == null) {
            this.reject("no_validator", disc);
            return;
        }
        String problem = validator.validate(mp, data);
        if (problem != null) {
            this.reject(problem, disc);
            return;
        }
        IMessage message;
        try {
            message = type.newInstance();
            ByteBuf buf = Unpooled.wrappedBuffer(data, 1, data.length - 1);
            message.fromBytes(buf);
            if (buf.isReadable()) {
                this.reject("trailing_bytes", disc);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.reject("decode", disc);
            return;
        }
        this.received++;
        IMessage reply = handler.onMessage(message, new MessageContext(mp.connection, Side.SERVER));
        if (reply != null) this.sendTo(reply, mp);
    }

    private void reject(String reason, int detail) {
        this.rejected++;
        if (this.rejected <= 50 || this.rejected % 500 == 0) {
            log.warning("MUTANTS_PACKET_REJECTED channel=" + this.name + " reason=" + reason + " detail=" + detail + " total=" + this.rejected);
        }
    }
}
