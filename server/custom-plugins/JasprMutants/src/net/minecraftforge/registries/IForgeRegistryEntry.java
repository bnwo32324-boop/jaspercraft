package net.minecraftforge.registries;

import net.minecraft.util.ResourceLocation;

/**
 * JasperCraft port shim of Forge's IForgeRegistryEntry. On Forge every Item/SoundEvent/... implements it; Paper's
 * classes do not, so registry names of Paper objects are kept by chat.jaspr.mutants.registry.RegistryNames instead.
 */
public interface IForgeRegistryEntry<V> {
    V setRegistryName(ResourceLocation name);

    ResourceLocation getRegistryName();

    Class<V> getRegistryType();
}
