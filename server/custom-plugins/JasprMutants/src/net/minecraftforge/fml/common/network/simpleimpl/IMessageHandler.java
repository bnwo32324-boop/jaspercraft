package net.minecraftforge.fml.common.network.simpleimpl;

/** JasperCraft port shim of FML's IMessageHandler. */
public interface IMessageHandler<REQ extends IMessage, REPLY extends IMessage> {
    REPLY onMessage(REQ message, MessageContext ctx);
}
