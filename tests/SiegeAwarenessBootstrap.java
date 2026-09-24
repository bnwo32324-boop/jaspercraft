package chat.jaspr.siegefixture;
public final class SiegeAwarenessBootstrap extends org.bukkit.plugin.java.JavaPlugin {
    public void onEnable() { chat.jaspr.apocalypse.SiegeAwarenessPaperProbe.install(this); }
}
