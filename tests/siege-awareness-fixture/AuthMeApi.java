package fr.xephi.authme.api.v3;
import org.bukkit.entity.Player;
/** Fixture authentication switch; packaged only into the temporary AuthMe fixture jar. */
public final class AuthMeApi {
    public static boolean authenticated=true;
    private static final AuthMeApi INSTANCE=new AuthMeApi();
    public static AuthMeApi getInstance() { return INSTANCE; }
    public boolean isAuthenticated(Player player) { return authenticated; }
}
