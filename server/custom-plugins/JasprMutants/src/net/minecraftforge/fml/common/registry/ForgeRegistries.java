package net.minecraftforge.fml.common.registry;

import chat.jaspr.mutants.registry.VanillaRegistryView;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.registries.IForgeRegistry;

/** JasperCraft port shim of FML's ForgeRegistries: read-only views of Paper's vanilla registries (only BIOMES is used). */
public class ForgeRegistries {
    public static final IForgeRegistry<Biome> BIOMES = new VanillaRegistryView<Biome>(Biome.class, Biome.REGISTRY);
}
