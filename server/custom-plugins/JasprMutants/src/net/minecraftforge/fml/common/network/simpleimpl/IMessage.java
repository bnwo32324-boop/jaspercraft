package net.minecraftforge.fml.common.network.simpleimpl;

import io.netty.buffer.ByteBuf;

/** JasperCraft port shim of FML's IMessage. */
public interface IMessage {
    void fromBytes(ByteBuf buf);

    void toBytes(ByteBuf buf);
}
