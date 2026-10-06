package chumbanotz.mutantbeasts.packet;

import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TeleportPacket
implements IMessage {
    private int entityId;
    private BlockPos blockPos;

    public TeleportPacket() {
    }

    public TeleportPacket(MutantEndermanEntity enderman, BlockPos pos) {
        this.entityId = enderman.getEntityId();
        this.blockPos = pos;
    }

    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeLong(this.blockPos.toLong());
    }

    public void fromBytes(ByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.blockPos = BlockPos.fromLong((long)buffer.readLong());
    }

    public static class Handler
    implements IMessageHandler<TeleportPacket, IMessage> {
        public IMessage onMessage(TeleportPacket packet, MessageContext ctx) {
            Entity entity = MutantBeasts.PROXY.getWorldClient().getEntityByID(packet.entityId);
            if (entity instanceof MutantEndermanEntity && packet.blockPos != null) {
                ((MutantEndermanEntity)entity).setTeleportPosition(packet.blockPos);
            }
            return null;
        }
    }
}
