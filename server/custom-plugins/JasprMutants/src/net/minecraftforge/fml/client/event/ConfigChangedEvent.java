package net.minecraftforge.fml.client.event;

import net.minecraftforge.fml.common.eventhandler.Event;

/** JasperCraft port shim of FML's ConfigChangedEvent (posted after /mutants reload re-reads config.yml). */
public class ConfigChangedEvent extends Event {
    private final String modID;
    private final boolean isWorldRunning;
    private final boolean requiresMcRestart;
    private final String configID;

    public ConfigChangedEvent(String modID, String configID, boolean isWorldRunning, boolean requiresMcRestart) {
        this.modID = modID;
        this.configID = configID;
        this.isWorldRunning = isWorldRunning;
        this.requiresMcRestart = requiresMcRestart;
    }

    public String getModID() {
        return this.modID;
    }

    public boolean isWorldRunning() {
        return this.isWorldRunning;
    }

    public boolean isRequiresMcRestart() {
        return this.requiresMcRestart;
    }

    public String getConfigID() {
        return this.configID;
    }

    public static class OnConfigChangedEvent extends ConfigChangedEvent {
        public OnConfigChangedEvent(String modID, String configID, boolean isWorldRunning, boolean requiresMcRestart) {
            super(modID, configID, isWorldRunning, requiresMcRestart);
        }
    }

    public static class PostConfigChangedEvent extends ConfigChangedEvent {
        public PostConfigChangedEvent(String modID, String configID, boolean isWorldRunning, boolean requiresMcRestart) {
            super(modID, configID, isWorldRunning, requiresMcRestart);
        }
    }
}
