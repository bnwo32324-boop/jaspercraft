package net.minecraftforge.fml.common.network.simpleimpl;

import chat.jaspr.mutants.net.ModChannel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;

/**
 * JasperCraft port shim of FML's SimpleNetworkWrapper. The wire format is FML's SimpleIndexedCodec (discriminator byte,
 * then the message's toBytes) on a plugin channel with the same name; chat.jaspr.mutants.net.ModChannel does the
 * Paper side (custom payload packets out, Bukkit's Messenger in, bounded and validated).
 */
public class SimpleNetworkWrapper {
    private final ModChannel channel;

    public SimpleNetworkWrapper(String channelName) {
        this.channel = ModChannel.create(channelName);
    }

    public <REQ extends IMessage, REPLY extends IMessage> void registerMessage(Class<? extends IMessageHandler<REQ, REPLY>> messageHandler, Class<REQ> requestMessageType, int discriminator, Side side) {
        this.channel.register(messageHandler, requestMessageType, discriminator, side);
    }

    public <REQ extends IMessage, REPLY extends IMessage> void registerMessage(IMessageHandler<? super REQ, ? extends REPLY> messageHandler, Class<REQ> requestMessageType, int discriminator, Side side) {
        this.channel.registerInstance(messageHandler, requestMessageType, discriminator, side);
    }

    public void sendToAll(IMessage message) {
        this.channel.sendToAll(message);
    }

    public void sendTo(IMessage message, EntityPlayerMP player) {
        this.channel.sendTo(message, player);
    }

    public void sendToAllAround(IMessage message, NetworkRegistry.TargetPoint point) {
        this.channel.sendToAllAround(message, point);
    }

    public void sendToAllTracking(IMessage message, Entity entity) {
        this.channel.sendToAllTracking(message, entity);
    }

    public void sendToDimension(IMessage message, int dimensionId) {
        this.channel.sendToDimension(message, dimensionId);
    }

    public ModChannel channel() {
        return this.channel;
    }
}
