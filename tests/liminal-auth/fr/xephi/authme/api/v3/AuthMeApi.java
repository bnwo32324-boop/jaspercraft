package fr.xephi.authme.api.v3;

import org.bukkit.entity.Player;

/** Test-only reflective API fixture. Never packaged in a server/plugin jar. */
public final class AuthMeApi {
    public static boolean authenticated=true,fail=false;
    private static final AuthMeApi INSTANCE=new AuthMeApi();
    public static AuthMeApi getInstance(){if(fail)throw new IllegalStateException("API unavailable");return INSTANCE;}
    public boolean isAuthenticated(Player player){return authenticated;}
}
