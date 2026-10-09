package chumbanotz.mutantbeasts.packet;

import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.util.MBParticles;
import io.netty.buffer.ByteBuf;
import java.util.Random;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SpawnParticlePacket
implements IMessage {
    private EnumParticleTypes particleType;
    private double posX;
    private double posY;
    private double posZ;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private int amount;

    public SpawnParticlePacket() {
    }

    public SpawnParticlePacket(EnumParticleTypes particleType, double posX, double posY, double posZ, double offsetX, double offsetY, double offsetZ, int amount) {
        this.particleType = particleType;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.amount = amount;
    }

    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.particleType.getParticleID());
        buffer.writeDouble(this.posX);
        buffer.writeDouble(this.posY);
        buffer.writeDouble(this.posZ);
        buffer.writeDouble(this.offsetX);
        buffer.writeDouble(this.offsetY);
        buffer.writeDouble(this.offsetZ);
        buffer.writeInt(this.amount);
    }

    public void fromBytes(ByteBuf buffer) {
        this.particleType = EnumParticleTypes.getParticleFromId((int)buffer.readInt());
        if (this.particleType == null) {
            this.particleType = EnumParticleTypes.BARRIER;
        }
        this.posX = buffer.readDouble();
        this.posY = buffer.readDouble();
        this.posZ = buffer.readDouble();
        this.offsetX = buffer.readDouble();
        this.offsetY = buffer.readDouble();
        this.offsetZ = buffer.readDouble();
        this.amount = buffer.readInt();
    }

    public static class Handler
    implements IMessageHandler<SpawnParticlePacket, IMessage> {
        public IMessage onMessage(SpawnParticlePacket packet, MessageContext ctx) {
            World world = MutantBeasts.PROXY.getWorldClient();
            Random random = world.rand;
            if (packet.particleType == MBParticles.ENDERSOUL) {
                for (int i = 0; i < packet.amount; ++i) {
                    float f = (random.nextFloat() - 0.5f) * 1.8f;
                    float f1 = (random.nextFloat() - 0.5f) * 1.8f;
                    float f2 = (random.nextFloat() - 0.5f) * 1.8f;
                    double tempX = packet.posX + (double)(random.nextFloat() - 0.5f) * packet.offsetX;
                    double tempY = packet.posY + (double)(random.nextFloat() - 0.5f) * packet.offsetY + 0.5;
                    double tempZ = packet.posZ + (double)(random.nextFloat() - 0.5f) * packet.offsetZ;
                    world.spawnParticle(MBParticles.ENDERSOUL, tempX, tempY, tempZ, f, f1, f2, new int[0]);
                }
            } else {
                for (int i = 0; i < packet.amount; ++i) {
                    double posX = packet.posX + (double)random.nextFloat() * packet.offsetX * 2.0 - packet.offsetX;
                    double posY = packet.posY + 0.5 + (double)random.nextFloat() * packet.offsetY;
                    double posZ = packet.posZ + (double)random.nextFloat() * packet.offsetZ * 2.0 - packet.offsetZ;
                    double x = random.nextGaussian() * 0.02;
                    double y = random.nextGaussian() * 0.02;
                    double z = random.nextGaussian() * 0.02;
                    world.spawnParticle(packet.particleType, posX, posY, posZ, x, y, z, new int[0]);
                }
            }
            return null;
        }
    }
}
