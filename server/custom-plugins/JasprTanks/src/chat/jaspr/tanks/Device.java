package chat.jaspr.tanks;

import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.entity.Player;

/**
 * Browser class from the WebSocket User-Agent that EaglercraftXServer recorded. Only the class is kept
 * or logged, never the string. Connections relayed through the Jaspr.chat gateway usually carry no
 * User-Agent, so {@link Kind#UNKNOWN} is the normal case there and the touch controls' own claim decides.
 */
final class Device {
    enum Kind { MOBILE, APPLE_DESKTOP, DESKTOP, UNKNOWN }

    static Kind classify(String agent) {
        if (agent == null || agent.trim().isEmpty()) return Kind.UNKNOWN;
        String a = agent.toLowerCase(Locale.ROOT);
        String[] mobile = {"android", "iphone", "ipad", "ipod", "mobile", "silk/", "kindle", "playbook",
            "blackberry", "bb10", "opera mini", "iemobile", "windows phone"};
        for (String marker : mobile) if (a.contains(marker)) return Kind.MOBILE;
        // iPadOS Safari and Chrome request desktop pages and report a Mac.
        if (a.contains("macintosh") || a.contains("mac os x")) return Kind.APPLE_DESKTOP;
        return Kind.DESKTOP;
    }

    private final ClassLoader loader;
    private boolean unavailable;
    private Object api, userAgentHeader;
    private Method eaglerPlayer, header;

    Device(ClassLoader loader) { this.loader = loader; }

    Kind of(Player player) { return classify(userAgent(player)); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String userAgent(Player player) {
        if (unavailable) return null;
        try {
            if (api == null) {
                Class<?> bukkitApi = Class.forName("net.lax1dude.eaglercraft.backend.server.api.bukkit.EaglerXServerAPI", true, loader);
                Class<?> serverApi = Class.forName("net.lax1dude.eaglercraft.backend.server.api.IEaglerXServerAPI", true, loader);
                Class<?> connection = Class.forName("net.lax1dude.eaglercraft.backend.server.api.IEaglerConnection", true, loader);
                Class<? extends Enum> headers = (Class<? extends Enum>) Class.forName("net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader", true, loader);
                eaglerPlayer = serverApi.getMethod("getEaglerPlayer", Object.class);
                header = connection.getMethod("getWebSocketHeader", headers);
                userAgentHeader = Enum.valueOf(headers, "HEADER_USER_AGENT");
                api = bukkitApi.getMethod("instance").invoke(null);
            }
            Object eagler = eaglerPlayer.invoke(api, player);
            return eagler == null ? null : (String) header.invoke(eagler, userAgentHeader);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            unavailable = true;
            return null;
        } catch (Throwable e) {
            return null;
        }
    }
}
