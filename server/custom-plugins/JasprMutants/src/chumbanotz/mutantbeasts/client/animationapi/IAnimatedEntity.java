package chumbanotz.mutantbeasts.client.animationapi;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

public interface IAnimatedEntity
extends IEntityAdditionalSpawnData {
    public int getAnimationID();

    public void setAnimationID(int var1);

    public int getAnimationTick();

    public void setAnimationTick(int var1);

    default public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(this.getAnimationID());
        buffer.writeInt(this.getAnimationTick());
    }

    default public void readSpawnData(ByteBuf additionalData) {
        this.setAnimationID(additionalData.readInt());
        this.setAnimationTick(additionalData.readInt());
    }
}
