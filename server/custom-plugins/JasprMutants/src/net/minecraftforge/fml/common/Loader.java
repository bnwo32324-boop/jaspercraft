package net.minecraftforge.fml.common;

/** JasperCraft port shim of FML's Loader: only Mutant Creatures exists on this server, no other Forge mods. */
public class Loader {
    private static final Loader INSTANCE = new Loader();

    public static Loader instance() {
        return INSTANCE;
    }

    public static boolean isModLoaded(String modname) {
        return "mutantbeasts".equals(modname) || "minecraft".equals(modname);
    }
}
