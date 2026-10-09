package chumbanotz.mutantbeasts.packet;

import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CreeperMinionTrackerPacket
implements IMessage {
    private int entityId;
    private byte optionsId;
    private boolean setOption;

    public CreeperMinionTrackerPacket() {
    }

    public CreeperMinionTrackerPacket(CreeperMinionEntity creeperMinionEntity, int optionsId, boolean setOption) {
        this.entityId = creeperMinionEntity.getEntityId();
        this.optionsId = (byte)optionsId;
        this.setOption = setOption;
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeByte(this.optionsId);
        buf.writeBoolean(this.setOption);
    }

    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.optionsId = buf.readByte();
        this.setOption = buf.readBoolean();
    }

    public static class Handler
    implements IMessageHandler<CreeperMinionTrackerPacket, IMessage> {
        public IMessage onMessage(CreeperMinionTrackerPacket message, MessageContext ctx) {
            ctx.getServerHandler().player.getServer().addScheduledTask(() -> {
                Entity entity = ctx.getServerHandler().player.world.getEntityByID(message.entityId);
                if (entity instanceof CreeperMinionEntity) {
                    CreeperMinionEntity creeperMinion = (CreeperMinionEntity)entity;
                    if (message.optionsId == 0) {
                        creeperMinion.setDestroyBlocks(message.setOption);
                    } else if (message.optionsId == 1) {
                        creeperMinion.setAlwaysRenderNameTag(message.setOption);
                    } else if (message.optionsId == 2) {
                        creeperMinion.setCanRideOnShoulder(message.setOption);
                    }
                }
            });
            return null;
        }
    }
}
