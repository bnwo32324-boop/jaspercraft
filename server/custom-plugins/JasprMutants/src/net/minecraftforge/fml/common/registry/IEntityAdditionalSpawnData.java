package net.minecraftforge.fml.common.registry;

import io.netty.buffer.ByteBuf;

/**
 * JasperCraft port shim of FML's IEntityAdditionalSpawnData. writeSpawnData is appended to the protocol's SPAWN message
 * (MUTANTS_PROTOCOL.md section 2, op 1), as FML appends it to its EntitySpawnMessage.
 */
public interface IEntityAdditionalSpawnData {
    void writeSpawnData(ByteBuf buffer);

    void readSpawnData(ByteBuf additionalData);
}
