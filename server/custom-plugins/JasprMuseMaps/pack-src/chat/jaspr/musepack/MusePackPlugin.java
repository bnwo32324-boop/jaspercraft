package chat.jaspr.musepack;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Asset-only plugin: carries the Muse+GLM_Maps block data (maps/*.se45.gz, maps/catalog.json) for JasprMuseMaps.
 * Kept apart so code updates never re-ship the 16 MB of structure data.
 */
public final class MusePackPlugin extends JavaPlugin {
    @Override public void onEnable() { getLogger().info("MUSE_PACK_READY collection=Muse+GLM_Maps"); }
}
