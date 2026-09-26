package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * BetterNether fireflies as particle swarms instead of entities: in Nether Grasslands and the Nether Jungle, a player
 * has up to three swarms of 5-10 drifting coloured dust motes (each channel 128-255, as the jar's random colour). They
 * light nothing and apply no glow; they are cosmetic, cheap and vanish when the player leaves the biome.
 */
final class Fireflies {
    static final class Fly { double x, y, z, vx, vy, vz; float r, g, b; }
    static final class Swarm { final List<Fly> flies = new ArrayList<>(); long until; }

    private final NetherPlugin plugin;
    private final Map<UUID, List<Swarm>> swarms = new HashMap<>();
    private final Random random = new Random();
    long swarmsSpawned;

    Fireflies(NetherPlugin plugin) { this.plugin = plugin; }

    void tick(long ticks) {
        if (plugin.nether == null || plugin.gen == null) { swarms.clear(); return; }
        for (Player p : plugin.nether.getPlayers()) {
            Location l = p.getLocation();
            List<Swarm> list = swarms.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
            boolean biome = plugin.gen.biomes.nex(l.getBlockX(), l.getBlockZ()) == Biomes.Nex.HELL;
            if (biome) {
                Biomes.Bn b = plugin.gen.biomes.bn(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                biome = b == Biomes.Bn.GRASSLANDS || b == Biomes.Bn.NETHER_JUNGLE;
            }
            if (!biome) { list.clear(); continue; }
            if (list.size() < 3 && random.nextInt(20) == 0) {
                Swarm s = new Swarm();
                s.until = ticks + 1200 + random.nextInt(1200);
                double cx = l.getX() + random.nextInt(21) - 10, cz = l.getZ() + random.nextInt(21) - 10, cy = l.getY() + 1 + random.nextDouble() * 3;
                int n = 5 + random.nextInt(6);
                for (int i = 0; i < n; i++) {
                    Fly f = new Fly();
                    f.x = cx + random.nextGaussian(); f.y = cy + random.nextGaussian() * 0.5; f.z = cz + random.nextGaussian();
                    f.r = (128 | random.nextInt(128)) / 255f; f.g = (128 | random.nextInt(128)) / 255f; f.b = (128 | random.nextInt(128)) / 255f;
                    s.flies.add(f);
                }
                list.add(s);
                swarmsSpawned++;
            }
            Iterator<Swarm> it = list.iterator();
            while (it.hasNext()) {
                Swarm s = it.next();
                if (ticks > s.until) { it.remove(); continue; }
                for (Fly f : s.flies) {
                    if (random.nextInt(3) == 0) { f.vx += (random.nextDouble() - 0.5) * 0.04; f.vy += (random.nextDouble() - 0.5) * 0.04; f.vz += (random.nextDouble() - 0.5) * 0.04; }
                    double len = Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz);
                    if (len > 0.12) { f.vx *= 0.12 / len; f.vy *= 0.12 / len; f.vz *= 0.12 / len; }
                    f.x += f.vx; f.y += f.vy; f.z += f.vz;
                    Location at = new Location(p.getWorld(), f.x, f.y, f.z);
                    if (at.getBlock().getType().isSolid()) { f.vx = -f.vx; f.vy = Math.abs(f.vy); f.vz = -f.vz; continue; }
                    // coloured dust: offsets carry the colour when count is 0 (1.12 REDSTONE particle)
                    p.spawnParticle(Particle.REDSTONE, at, 0, Math.max(0.001, f.r), f.g, f.b, 1);
                }
            }
        }
        swarms.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }
}
