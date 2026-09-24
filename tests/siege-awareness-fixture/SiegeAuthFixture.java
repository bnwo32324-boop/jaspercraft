package chat.jaspr.siegefixture;
public final class SiegeAuthFixture extends org.bukkit.plugin.java.JavaPlugin {
    public void onEnable() {
        if(!Boolean.getBoolean("jaspr.siege.fixture"))throw new IllegalStateException("Disposable fixture only");
    }
}
