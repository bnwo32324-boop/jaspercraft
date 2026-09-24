package local.eagler.testserver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps public consent-based commands separate from privileged server commands. */
final class PlayerCommandPolicy {
    enum Decision { BLOCK_AUTH_COMMAND, PUBLIC_COMMAND, ADMIN_ONLY }

    private static final Set<String> BLOCKED_AUTH_COMMANDS = new HashSet<String>(Arrays.asList(
            "login", "log", "l", "register", "reg", "logout", "email", "captcha", "2fa", "totp",
            "authme:login", "authme:register", "authme:logout"));

    private static final Set<String> PUBLIC_COMMANDS = new HashSet<String>(Arrays.asList(
            "voice", "jasprvoicechat:voice",
            // Character progression is for everyone. These open a menu and spend the player's own
            // experience or their own item's tokens; there is nothing here an operator must gate.
            "stats", "upgrades", "perks",
            "armament", "armaments", "enhance",
            "jasprrpg:stats", "jasprrpg:upgrades", "jasprrpg:perks",
            "jasprrpg:armament", "jasprrpg:armaments", "jasprrpg:enhance",
            // Waypoints and dynamic lights are personal client conveniences. The Tab overlay
            // asks for a compass readout several times a second, so every player needs these.
            "waypoints", "wp", "dl", "dynamiclights",
            "graves", "grave", "jasprgraves:graves", "jasprgraves:grave",
            // Reading the ground you are standing on changes nothing about it.
            "where", "biome", "survey",
            "jasprhorrorbiomes:where", "jasprhorrorbiomes:biome", "jasprhorrorbiomes:survey",
            // A bleeding player has to be able to stop holding on.
            "giveup", "jasprrevive:giveup",
            // Survivor Gear: the player's own trinket slots, menu, XP bank and ability fallbacks.
            "gear", "trinkets", "kit", "jasprgear:gear", "jasprgear:trinkets", "jasprgear:kit",
            "jasprapocalypse:waypoints", "jasprapocalypse:wp",
            "jasprapocalypse:dl", "jasprapocalypse:dynamiclights",
            "tp", "teleport", "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel",
            "jasprapocalypse:tp", "jasprapocalypse:teleport", "jasprapocalypse:tpa",
            "jasprapocalypse:tpahere", "jasprapocalypse:tpaccept", "jasprapocalypse:tpdeny",
            "jasprapocalypse:tpcancel"));

    private PlayerCommandPolicy() {}

    static Decision classify(String message) {
        String token = token(message);
        if (BLOCKED_AUTH_COMMANDS.contains(token)) return Decision.BLOCK_AUTH_COMMAND;
        if (PUBLIC_COMMANDS.contains(token)) return Decision.PUBLIC_COMMAND;
        return Decision.ADMIN_ONLY;
    }

    private static String token(String message) {
        if (message == null || message.length() <= 1 || message.charAt(0) != '/') return "";
        return message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
    }
}
