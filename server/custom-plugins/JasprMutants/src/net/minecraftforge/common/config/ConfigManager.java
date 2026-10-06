package net.minecraftforge.common.config;

import chat.jaspr.mutants.config.MutantsConfig;

/** JasperCraft port shim of Forge's ConfigManager: sync() re-reads plugins/JasprMutants/config.yml into MBConfig. */
public class ConfigManager {
    public static void sync(String modid, Config.Type type) {
        if ("mutantbeasts".equals(modid)) {
            MutantsConfig.sync();
        }
    }
}
