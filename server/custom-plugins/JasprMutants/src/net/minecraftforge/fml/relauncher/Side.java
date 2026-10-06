package net.minecraftforge.fml.relauncher;

/** JasperCraft port shim of FML's Side. This plugin is always the SERVER side. */
public enum Side {
    CLIENT, SERVER;

    public boolean isServer() {
        return !this.isClient();
    }

    public boolean isClient() {
        return this == CLIENT;
    }
}
