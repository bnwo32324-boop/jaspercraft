package chat.jaspr.mutants.net;

import chat.jaspr.mutants.registry.EntityRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.common.registry.IThrowableEntity;

/**
 * MUTANTS_PROTOCOL.md section 2, op 1 SPAWN: the message that replaces the vanilla spawn packet for every mod entity
 * (FML's EntitySpawnMessage on Forge), on channel jaspr:mutants:
 * byte 1, VarInt entityId, long uuidMost, long uuidLeast, VarInt typeId, double x y z, byte yaw pitch headYaw,
 * short motionX motionY motionZ (motion * 8000, clamped to +-3.9), VarInt throwerId + 1, the data manager entries
 * (0xFF terminated), VarInt spawnDataLength and IEntityAdditionalSpawnData.writeSpawnData.
 */
public final class SpawnMessage {
    public static final String CHANNEL = "jaspr:mutants";
    public static final int OP_SPAWN = 1;

    private SpawnMessage() {
    }

    public static byte[] encode(Entity entity) {
        int typeId = EntityRegistry.networkTypeId(entity.getClass());
        if (typeId < 0) throw new IllegalArgumentException("not a mod entity: " + entity.getClass().getName());
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer(256));
        buf.writeByte(OP_SPAWN);
        buf.writeVarInt(entity.getEntityId());
        UUID uuid = entity.getUniqueID();
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
        buf.writeVarInt(typeId);
        buf.writeDouble(entity.posX);
        buf.writeDouble(entity.posY);
        buf.writeDouble(entity.posZ);
        // angles as SPacketSpawnMob and FML's spawn message: (byte) (int) (degrees * 256 / 360)
        buf.writeByte((byte) (int) (entity.rotationYaw * 256.0F / 360.0F));
        buf.writeByte((byte) (int) (entity.rotationPitch * 256.0F / 360.0F));
        buf.writeByte((byte) (int) (entity.getRotationYawHead() * 256.0F / 360.0F));
        buf.writeShort(motion(entity.motionX));
        buf.writeShort(motion(entity.motionY));
        buf.writeShort(motion(entity.motionZ));
        int throwerId = 0;
        if (entity instanceof IThrowableEntity) {
            Entity thrower = ((IThrowableEntity) entity).getThrower();
            if (thrower != null) throwerId = thrower.getEntityId() + 1;
        }
        buf.writeVarInt(throwerId);
        try {
            entity.getDataManager().writeEntries(buf);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (entity instanceof IEntityAdditionalSpawnData) {
            ByteBuf extra = Unpooled.buffer(32);
            ((IEntityAdditionalSpawnData) entity).writeSpawnData(extra);
            buf.writeVarInt(extra.readableBytes());
            buf.writeBytes(extra);
        } else {
            buf.writeVarInt(0);
        }
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    public static SPacketCustomPayload packet(Entity entity) {
        return new SPacketCustomPayload(CHANNEL, new PacketBuffer(Unpooled.wrappedBuffer(encode(entity))));
    }

    private static int motion(double m) {
        double c = Math.max(-3.9D, Math.min(3.9D, m));
        return (int) (c * 8000.0D);
    }
}
