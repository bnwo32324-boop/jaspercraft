package chumbanotz.mutantbeasts.compat;

import chumbanotz.mutantbeasts.MutantBeasts;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MutantBeasts.MOD_ID)
public class MBCompatHandler {
    public static void preInit() {
    }

    public static void init() {
        if (Loader.isModLoaded("thaumcraft")) {
            // JasperCraft port: Thaumcraft (a Forge mod) cannot be present on Paper, so its aspect plugin
            // (compat/thaumcraft/MBThaumcraftPlugin, which needs the Thaumcraft API) is not part of the port.
            // Original line: MinecraftForge.EVENT_BUS.register(MBThaumcraftPlugin.class);
        }
    }

    public static void postInit() {
    }
}
