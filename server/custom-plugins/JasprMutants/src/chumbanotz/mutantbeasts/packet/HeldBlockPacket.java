package chumbanotz.mutantbeasts.packet;

import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class HeldBlockPacket
implements IMessage {
    private int entityId;
    private int blockId;
    private byte blockIndex;

    public HeldBlockPacket() {
    }

    public HeldBlockPacket(MutantEndermanEntity enderman, int blockId, int blockIndex) {
        this.entityId = enderman.getEntityId();
        this.blockId = blockId;
        this.blockIndex = (byte)blockIndex;
    }

    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeInt(this.blockId);
        buffer.writeByte(this.blockIndex);
    }

    public void fromBytes(ByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.blockId = buffer.readInt();
        this.blockIndex = buffer.readByte();
    }

    public static class Handler
    implements IMessageHandler<HeldBlockPacket, IMessage> {
        public IMessage onMessage(HeldBlockPacket packet, MessageContext ctx) {
            Entity entity = MutantBeasts.PROXY.getWorldClient().getEntityByID(packet.entityId);
            if (entity instanceof MutantEndermanEntity && packet.blockIndex > 0 && packet.blockId != -1) {
                ((MutantEndermanEntity)entity).sendHoldBlock(packet.blockIndex, packet.blockId);
            }
            return null;
        }
    }
}
