package net.minecraftforge.fml.common.network.simpleimpl;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.fml.relauncher.Side;

/** JasperCraft port shim of FML's MessageContext (server side only). */
public class MessageContext {
    public final Side side;
    private final NetHandlerPlayServer serverHandler;

    public MessageContext(NetHandlerPlayServer serverHandler, Side side) {
        this.serverHandler = serverHandler;
        this.side = side;
    }

    public NetHandlerPlayServer getServerHandler() {
        return this.serverHandler;
    }
}
