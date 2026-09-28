package chat.jaspr.ruins;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.IScoreboardCriteria;
import net.minecraft.server.v1_12_R1.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_12_R1.PlayerConnection;
import net.minecraft.server.v1_12_R1.Scoreboard;
import net.minecraft.server.v1_12_R1.ScoreboardObjective;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The sky over Ul'Nhaar. The browser client paints a sickly green sky and fog, a blood-red moon and reddened stars while
 * the hidden scoreboard objective "jrs" (display "JRS v1") is present; this sends it to players in the ruins and removes
 * it when they leave (the client keeps its scoreboard across world changes). Ambience rides along: drifting pale motes,
 * far-off wails and silent lightning on the horizon.
 */
final class Sky implements Listener {
    static final String OBJECTIVE = "jrs", DISPLAY = "JRS v1 eerie";

    private final RuinsPlugin plugin;
    private final Set<UUID> shown = new HashSet<>();
    private final Random random = new Random();
    private int ticks;
    long flashes;

    Sky(RuinsPlugin plugin) { this.plugin = plugin; }

    private static boolean send(Player p, boolean add) {
        try {
            Scoreboard board = new Scoreboard();
            ScoreboardObjective o = board.registerObjective(OBJECTIVE, IScoreboardCriteria.criteria.get("dummy"));
            o.setDisplayName(DISPLAY);
            PlayerConnection c = ((CraftPlayer) p).getHandle().playerConnection;
            if (c == null) return false;
            c.sendPacket(new PacketPlayOutScoreboardObjective(o, add ? 0 : 1));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Every two seconds: the sky follows each player's world; every so often, the ambience. */
    void tick() {
        ticks++;
        World ruins = plugin.ruins();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean in = ruins != null && p.getWorld() == ruins;
            UUID id = p.getUniqueId();
            if (in && !shown.contains(id)) { if (send(p, true)) shown.add(id); }
            else if (!in && shown.contains(id)) { send(p, false); shown.remove(id); }
        }
        if (ruins == null || ruins.getPlayers().isEmpty()) return;
        for (Player p : ruins.getPlayers()) {
            Location at = p.getLocation();
            p.spawnParticle(Particle.TOWN_AURA, at.clone().add(0, 1.5, 0), 40, 8, 4, 8, 0.0);
            p.spawnParticle(Particle.SUSPENDED_DEPTH, at.clone().add(0, 2, 0), 25, 10, 5, 10, 0.0);
            if (random.nextInt(18) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Location far = at.clone().add(Math.cos(a) * 24, 4, Math.sin(a) * 24);
                Sound[] wails = {Sound.ENTITY_GHAST_AMBIENT, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, Sound.AMBIENT_CAVE, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, Sound.ENTITY_WITHER_AMBIENT};
                p.playSound(far, wails[random.nextInt(wails.length)], 0.35f, 0.4f + random.nextFloat() * 0.3f);
            }
        }
        if (ticks % 45 == 0) {   // about every ninety seconds, silent lightning far out on the horizon
            Player p = ruins.getPlayers().get(random.nextInt(ruins.getPlayers().size()));
            double a = random.nextDouble() * Math.PI * 2;
            Location far = p.getLocation().clone().add(Math.cos(a) * 110, 0, Math.sin(a) * 110);
            far.setY(ruins.getHighestBlockYAt(far.getBlockX(), far.getBlockZ()));
            if (ruins.isChunkLoaded(far.getBlockX() >> 4, far.getBlockZ() >> 4)) { ruins.strikeLightningEffect(far); flashes++; }
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { shown.remove(e.getPlayer().getUniqueId()); }
}
