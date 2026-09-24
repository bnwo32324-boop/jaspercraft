package chat.jaspr.biomes;
import org.bukkit.plugin.java.JavaPlugin;
/** Probe implementation shares the host's loader to test package-private ledger contracts. */
public final class EncounterRuntimeBootstrap extends JavaPlugin {
    @Override public void onEnable() { EncounterRuntimeProbe.install(this); }
}
