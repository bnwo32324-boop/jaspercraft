package chat.jaspr.backrooms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The levels' own dangers, beyond their monsters: heat in the Maintenance Tunnels (it builds the deeper you go; water cools
 * you; the Boiler Suit and the Pressure Gauge help), live floor tiles and arcing transformer cores in the Electrical
 * Corridors (the Lineman set and the Surge Protector help), flickering lights in the Office and the City (and something
 * steps out behind you), and the undertow of the Poolrooms' deep pools (the Lifeguard set and the Rubber Duck help).
 * All of it grows with the danger where it happens.
 */
final class Hazards implements Listener {
    private final BackroomsPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Double> heat = new HashMap<>();
    private final Map<UUID, Long> zapped = new HashMap<>(), warned = new HashMap<>();
    long zaps, arcs, flickers, pulls, burns;

    Hazards(BackroomsPlugin plugin) { this.plugin = plugin; }

    double heatOf(Player p) { return heat.getOrDefault(p.getUniqueId(), 0.0); }
    void heat(Player p, double add) { heat.put(p.getUniqueId(), Math.max(0, Math.min(100, heatOf(p) + add * plugin.gear().heatRate(p)))); }
    void cool(Player p) { heat.remove(p.getUniqueId()); }

    /** The compass line's hazard note (heat when it matters). */
    String hud(Player p) {
        double h = heatOf(p);
        if (h >= 20) return (h >= 75 ? ChatColor.RED : ChatColor.GOLD) + "heat " + Math.round(h) + "%" + (h >= 75 ? " find water!" : "");
        return null;
    }

    private boolean alive(Player p) { return !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE); }

    /** Every second: heat, flickers. */
    void second() {
        World w = plugin.world();
        for (Map.Entry<UUID, Double> e : heat.entrySet()) {   // heat fades for anyone out of the tunnels
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null || w == null || p.getWorld() != w || Level.at(p.getLocation().getX(), p.getLocation().getZ()) != Level.TUNNELS) e.setValue(Math.max(0, e.getValue() - 6));
        }
        heat.values().removeIf(v -> v <= 0);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (!alive(p)) continue;
            Location l = p.getLocation();
            Level lv = Level.at(l.getX(), l.getZ());
            if (lv == null) continue;
            double d = lv.progress(l.getX()), g = lv.danger(l.getX(), l.getZ());
            if (lv == Level.TUNNELS && l.getX() >= lv.entryEnd()) heatTick(p, d);
            if ((lv == Level.OFFICE && d > 0.3 || lv == Level.CITY && d > 0.15) && lv.inBody(l.getBlockX()) && random.nextDouble() < 1 / 75.0) flicker(p, lv, g);
            if (random.nextDouble() < 1 / 28.0) ambience(p, lv);
        }
    }

    private void heatTick(Player p, double d) {
        boolean wet = p.getLocation().getBlock().isLiquid();
        if (wet) { heat.put(p.getUniqueId(), Math.max(0, heatOf(p) - 12)); return; }
        heat(p, 1.2 + 1.8 * d);
        double h = heatOf(p);
        if (h >= 50) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, true, false), true);
        if (h >= 75) { p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, false), true); p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 0, true, false), true); warn(p, ChatColor.RED + "You are overheating. Water cools you down."); }
        if (h >= 90 && !p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) { p.setFireTicks(Math.max(p.getFireTicks(), 30)); burns++; }
    }

    /** Each level's own sound, quiet and from somewhere else: the hum, a clang, steam, a crackle, a phone, a howl, a splash. */
    private void ambience(Player p, Level lv) {
        Sound s; float volume = 0.25f, pitch = 1f;
        switch (lv) {
            case YELLOW: s = Sound.BLOCK_PORTAL_AMBIENT; volume = 0.07f; pitch = 1.9f; break;
            case WAREHOUSE: s = Sound.BLOCK_IRON_DOOR_CLOSE; volume = 0.35f; pitch = 0.5f; break;
            case TUNNELS: s = random.nextBoolean() ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.BLOCK_LAVA_POP; volume = 0.3f; pitch = 0.6f; break;
            case ELECTRICAL: s = Sound.ENTITY_LIGHTNING_IMPACT; volume = 0.12f; pitch = 2f; break;
            case OFFICE: s = Sound.BLOCK_NOTE_PLING; volume = 0.25f; pitch = 1.3f; break;
            case CITY: s = random.nextBoolean() ? Sound.ENTITY_WOLF_HOWL : Sound.BLOCK_NOTE_BELL; volume = 0.2f; pitch = 0.7f; break;
            default: s = random.nextBoolean() ? Sound.ENTITY_GENERIC_SPLASH : Sound.BLOCK_WATER_AMBIENT; volume = 0.3f; pitch = 1.4f; break;
        }
        double a = random.nextDouble() * Math.PI * 2;
        Location from = p.getLocation().clone().add(Math.cos(a) * 10, 0, Math.sin(a) * 10);
        p.playSound(from, s, volume, pitch);
        if (lv == Level.OFFICE) for (int i = 1; i <= 2; i++) plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.playSound(from, Sound.BLOCK_NOTE_PLING, 0.25f, 1.3f); }, i * 5L);
    }

    private void flicker(Player p, Level lv, double g) {
        flickers++;
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BASS, 0.8f, 0.5f);
        p.sendMessage(ChatColor.DARK_GRAY + "The lights flicker.");
        // Something steps out behind you.
        Location behind = p.getLocation().clone().subtract(p.getLocation().getDirection().setY(0).normalize().multiply(7));
        Block feet = behind.getBlock();
        if (feet.getType() == Material.AIR && feet.getRelative(0, 1, 0).getType() == Material.AIR && feet.getRelative(0, -1, 0).getType().isSolid())
            plugin.mobs().spawn(lv == Level.OFFICE ? Mobs.Kind.PARTYGOER : Mobs.Kind.CITIZEN, feet.getLocation().add(0.5, 0, 0.5), g, false);
    }

    /** Twice a second: live floors, arcing cores, undertow. */
    void half() {
        World w = plugin.world();
        if (w == null) return;
        long now = System.currentTimeMillis();
        long seed = plugin.seed();
        for (Player p : w.getPlayers()) {
            if (!alive(p)) continue;
            Location l = p.getLocation();
            Level lv = Level.at(l.getX(), l.getZ());
            if (lv == null) continue;
            double g = lv.danger(l.getX(), l.getZ());
            if (lv == Level.ELECTRICAL) {
                Block under = l.clone().subtract(0, 0.2, 0).getBlock();
                if ((under.getType() == Material.REDSTONE_ORE || under.getType() == Material.GLOWING_REDSTONE_ORE) && now - zapped.getOrDefault(p.getUniqueId(), 0L) > 900) {
                    zapped.put(p.getUniqueId(), now);
                    shock(p, 2 + 5 * g, "The floor is live!");
                    zaps++;
                }
                if (lv.inBody(l.getBlockX()) && random.nextDouble() < 0.35) {
                    List<int[]> cores = ((Electrical) Styles.of(lv)).transformersNear(lv, seed, l.getBlockX(), l.getBlockZ(), 8);
                    for (int[] c : cores) {
                        Location core = new Location(w, c[0] + 0.5, c[1] + 1.5, c[2] + 0.5);
                        if (core.distanceSquared(l) > 16) continue;
                        line(core, p.getEyeLocation());
                        shock(p, 2 + 3 * g, null);
                        arcs++;
                        break;
                    }
                }
            }
            if (lv == Level.POOLS && Pools.pool(seed, l.getBlockX(), l.getBlockZ()) && l.getY() < Level.FLOOR && l.getBlock().isLiquid() && !plugin.gear().swimmer(p)) {
                int cx = Math.floorDiv(l.getBlockX(), Pools.R) * Pools.R + 8, cz = Math.floorDiv(l.getBlockZ(), Pools.R) * Pools.R + 8;
                Vector pull = new Vector(cx - l.getX(), 0, cz - l.getZ()).multiply(0.03).setY(-0.12 - 0.08 * g);
                p.setVelocity(p.getVelocity().add(pull));
                pulls++;
                warn(p, ChatColor.DARK_AQUA + "The pool pulls you down. Swim up!");
            }
        }
    }

    private void shock(Player p, double amount, String say) {
        double k = plugin.gear().shockFactor(p);
        p.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, p.getLocation().add(0, 0.4, 0), 12, 0.3, 0.2, 0.3, 0.08);
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_IMPACT, 0.4f, 2f);
        if (k <= 0) return;
        p.damage(amount * k);
        if (say != null) warn(p, ChatColor.AQUA + say + ChatColor.GRAY + " Rubber soles or a Surge Protector would help.");
    }

    private void warn(Player p, String text) {
        long now = System.currentTimeMillis();
        if (now - warned.getOrDefault(p.getUniqueId(), 0L) < 12_000L) return;
        warned.put(p.getUniqueId(), now);
        p.sendMessage(text);
    }

    private static void line(Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.multiply(1 / len);
        for (double t = 0; t < len; t += 0.35) a.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, a.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); zapped.remove(id); warned.remove(id); }

    String describe() { return "zaps=" + zaps + " arcs=" + arcs + " flickers=" + flickers + " undertow=" + pulls + " burns=" + burns + " hot=" + heat.size(); }
}
