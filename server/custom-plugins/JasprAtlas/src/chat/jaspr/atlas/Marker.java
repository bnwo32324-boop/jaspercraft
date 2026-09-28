package chat.jaspr.atlas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.IScoreboardCriteria;
import net.minecraft.server.v1_12_R1.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_12_R1.PlayerConnection;
import net.minecraft.server.v1_12_R1.Scoreboard;
import net.minecraft.server.v1_12_R1.ScoreboardObjective;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tells the browser client where a player is. Players in Atlas get the hidden scoreboard objective "jrm" whose display
 * name is "JRM v1 atlas &lt;module version&gt; &lt;zone&gt; &lt;liberation mask&gt; &lt;victory&gt;"; the client's realm loader fetches
 * {@code realms/atlas.js} the first time it sees it and hands it the zone and mask (sky, fog, falling ash). The display
 * is updated as the player crosses a border or the realm is liberated, and the objective is removed when they leave
 * (the client keeps its scoreboard across world changes).
 */
final class Marker implements Listener {
    static final String OBJECTIVE = "jrm";

    private final AtlasPlugin plugin;
    private final Map<UUID, String> shown = new HashMap<>();
    long updates;

    Marker(AtlasPlugin plugin) { this.plugin = plugin; }

    static char code(Realm.Zone z) {
        switch (z) {
            case CONCORD: return 'C';
            case LINE: return 'L';
            case WOUND: return 'W';
            case WEALD: return 'E';
            case FORGES: return 'F';
            case FALLEN: return 'A';
            case PLATEAU: return 'P';
            case RIM: return 'R';
            default: return 'M';
        }
    }

    String display(Player p) {
        Realm.Zone z = Realm.zone(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        State s = plugin.state();
        return "JRM v1 atlas " + plugin.clientModuleVersion() + " " + code(z) + " " + s.liberated + " " + (s.victory ? 1 : 0);
    }

    private static boolean send(Player p, String display, int mode) {
        try {
            Scoreboard board = new Scoreboard();
            ScoreboardObjective o = board.registerObjective(OBJECTIVE, IScoreboardCriteria.criteria.get("dummy"));
            o.setDisplayName(display);
            PlayerConnection c = ((CraftPlayer) p).getHandle().playerConnection;
            if (c == null) return false;
            c.sendPacket(new PacketPlayOutScoreboardObjective(o, mode));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Once a second: show, update or remove each player's marker. */
    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            String had = shown.get(id);
            if (plugin.isAtlas(p.getWorld())) {
                String now = display(p);
                if (had == null) { if (send(p, now, 0)) { shown.put(id, now); updates++; plugin.getLogger().info("ATLAS_CLIENT_MARKER player=" + p.getName() + " shown=" + now.substring(7)); } }
                else if (!had.equals(now) && send(p, now, 2)) { shown.put(id, now); updates++; }
            } else if (had != null) {
                send(p, had, 1);
                shown.remove(id);
            }
        }
    }

    /** After a restart of the client (a rejoin) the marker must be sent again. */
    void forget(Player p) { shown.remove(p.getUniqueId()); }

    @EventHandler
    public void quit(PlayerQuitEvent e) { shown.remove(e.getPlayer().getUniqueId()); }
}
