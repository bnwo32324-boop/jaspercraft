package chat.jaspr.apocalypse;
import org.bukkit.plugin.java.JavaPlugin;
/** Implementation is intentionally loaded alongside the current subject for package-private access. */
public final class StatsRuntimeBootstrap extends JavaPlugin {
    @Override public void onEnable(){StatsRuntimeProbe.install(this);}
}
