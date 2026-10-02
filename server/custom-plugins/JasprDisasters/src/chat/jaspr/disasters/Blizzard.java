package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A blizzard closing in on one player.
 *
 * Snow drives in thick around the target, piling up in drifts on the ground and roofs, and still water near
 * them freezes over. Anyone out in the open near the target slowly gets colder: first slow, then sluggish,
 * then frostbitten and losing health. A roof overhead (any block above, even glass or leaves) or warmth from
 * a torch, fire or lava close by keeps the cold off and lets you warm back up. When the blizzard passes,
 * every snow layer it laid and every ice sheet it froze thaws away again, so it leaves no permanent mark;
 * snow or ice someone has since mined or built over is left as it is.
 */
final class Blizzard implements Disaster {
    private final DisasterConfig settings;
    private final Random random;
    private final String targetName;
    private final String worldName;
    private final long endTick;
    private final WeatherHold weather;

    /** Everything the blizzard changed, so the thaw can put exactly that back. Bounded by max-snow/max-ice. */
    private final List<Block> snow = new ArrayList<Block>();
    private final Set<Block> snowSet = new HashSet<Block>();
    private final List<Block> ice = new ArrayList<Block>();
    private final Map<UUID, Integer> cold = new HashMap<UUID, Integer>();

    private double snowCredit;
    private long nextSoundTick;
    private long nextFrostTick;
    private boolean thawing;
    private boolean finished;

    Blizzard(DisasterConfig settings, Random random, Player target, long nowTicks) {
        this.settings = settings;
        this.random = random;
        this.targetName = target.getName();
        World world = target.getWorld();
        this.worldName = world.getName();
        this.endTick = nowTicks + settings.blizzardDurationTicks;
        this.weather = new WeatherHold(world, false, settings.blizzardDurationTicks);
        announce(target, world);
    }

    @Override public String targetName() { return targetName; }

    @Override public boolean isFinished() { return finished; }

    private void announce(Player target, World world) {
        target.sendMessage(ChatColor.AQUA + "The temperature plunges and the air turns white. "
                + ChatColor.GRAY + "Blizzard! Get under a roof or near a fire.");
        try {
            target.sendTitle(ChatColor.WHITE + "Blizzard", ChatColor.GRAY + "Find shelter or a fire", 10, 60, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        world.playSound(target.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.2f, 0.5f);
        if (settings.blizzardBroadcast) {
            Bukkit.broadcastMessage(ChatColor.AQUA + "❄ " + ChatColor.WHITE + "A blizzard is closing in on "
                    + targetName + ChatColor.WHITE + "!");
        }
    }

    @Override public void tick(long nowTicks) {
        if (finished) return;
        if (thawing) {
            thaw(24, 8);
            if (snow.isEmpty() && ice.isEmpty()) finished = true;
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        World world = target == null ? null : target.getWorld();
        boolean lost = target == null || !target.isOnline() || target.isDead() || world == null || !world.getName().equals(worldName);
        if (lost || nowTicks >= endTick) {
            passOver(lost ? null : target);
            return;
        }

        weather.hold(world, nowTicks, endTick);
        whiteOut(target, world);
        if (settings.blizzardBreakBlocks) {
            pileSnow(target, world);
            if (settings.blizzardFreezeWater) freezeWater(target, world);
        }
        chill(target, world, nowTicks);
        if (nowTicks >= nextSoundTick) {
            nextSoundTick = nowTicks + 50L;
            target.playSound(target.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.7f, 0.55f + random.nextFloat() * 0.2f);
        }
    }

    // ------------------------------------------------------------------ the storm

    /** Driving snow and a white haze all around the target. */
    private void whiteOut(Player target, World world) {
        Location at = target.getLocation().add(0.0d, 1.5d, 0.0d);
        try {
            world.spawnParticle(Particle.SNOW_SHOVEL, at, 30, 8.0d, 4.0d, 8.0d, 0.05d);
            world.spawnParticle(Particle.SNOWBALL, at, 12, 6.0d, 3.0d, 6.0d, 0.0d);
            world.spawnParticle(Particle.CLOUD, at, 6, 9.0d, 2.5d, 9.0d, 0.02d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    /** Lays snow on open ground and roofs near the target, deepening drifts it already laid. */
    private void pileSnow(Player target, World world) {
        snowCredit += settings.blizzardSnowPerSecond * 0.25d;   // the shared loop runs every five ticks
        int attempts = 0;
        while (snowCredit >= 1.0d && attempts++ < 12) {
            snowCredit -= 1.0d;
            Block spot = randomSurface(target, world);
            if (spot == null) continue;
            if (snowSet.contains(spot) && spot.getType() == Material.SNOW) {
                // A drift: our own layer gets one deeper, up to four layers.
                @SuppressWarnings("deprecation") byte layers = spot.getData();
                if (layers < 3) setLayers(spot, (byte) (layers + 1));
                continue;
            }
            if (snow.size() >= settings.blizzardMaxSnow) continue;
            Block ground = spot.getRelative(0, -1, 0);
            Material type = ground.getType();
            if (spot.getType() != Material.AIR || !type.isSolid() || !type.isOccluding() && type != Material.LEAVES && type != Material.LEAVES_2) continue;
            if (Impacts.isProtected(type) || type == Material.ICE || type == Material.PACKED_ICE || type == Material.SNOW) continue;
            spot.setType(Material.SNOW, false);
            snow.add(spot);
            snowSet.add(spot);
        }
    }

    @SuppressWarnings("deprecation")
    private static void setLayers(Block block, byte layers) { block.setData(layers, false); }

    /** The surface of a random open-sky column near the target: the first air block above everything. */
    private Block randomSurface(Player target, World world) {
        Location base = target.getLocation();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        double distance = Math.sqrt(random.nextDouble()) * settings.blizzardRadius;
        int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getHighestBlockYAt(x, z);
        if (y < 2 || y > 254) return null;
        // The height map skips glass and other see-through blocks: only lay snow where nothing at all is above.
        for (int up = y + 1; up <= Math.min(255, y + 64); up++) {
            if (world.getBlockAt(x, up, z).getType() != Material.AIR) return null;
        }
        Block spot = world.getBlockAt(x, y, z);
        // A drift we already laid sits one above the solid ground the height map sees.
        Block below = spot.getRelative(0, -1, 0);
        return below.getType() == Material.SNOW && snowSet.contains(below) ? below : spot;
    }

    /** Still water near the target ices over, though never right beside a player who could be trapped under it. */
    private void freezeWater(Player target, World world) {
        for (int attempt = 0; attempt < 2 && ice.size() < settings.blizzardMaxIce; attempt++) {
            Location base = target.getLocation();
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = Math.sqrt(random.nextDouble()) * settings.blizzardRadius;
            int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = world.getHighestBlockYAt(x, z) - 1;
            if (y < 2 || y > 254) continue;
            Block water = world.getBlockAt(x, y, z);
            if (water.getType() != Material.STATIONARY_WATER || water.getRelative(0, 1, 0).getType() != Material.AIR) continue;
            if (playerNear(world, water.getLocation().add(0.5d, 0.5d, 0.5d), 3.0d)) continue;
            water.setType(Material.ICE, false);
            ice.add(water);
        }
    }

    private static boolean playerNear(World world, Location at, double radius) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= radius * radius) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ cold

    /**
     * Everyone near the target who is out in the open and away from any warmth gets colder; shelter or warmth
     * lets them recover. Cold builds to slowness, then sluggish hands, then frostbite that hurts.
     */
    private void chill(Player target, World world, long nowTicks) {
        boolean frostTurn = nowTicks >= nextFrostTick;
        if (frostTurn) nextFrostTick = nowTicks + 40L;
        double radius = settings.blizzardRadius + 4.0d;
        Location centre = target.getLocation();
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isDead()) continue;
            if (player.getLocation().distanceSquared(centre) > radius * radius) continue;

            UUID id = player.getUniqueId();
            int level = cold.containsKey(id) ? cold.get(id) : 0;
            boolean exposed = !sheltered(player) && !warm(player);
            level = exposed ? Math.min(settings.blizzardFreezeTicks, level + 5) : Math.max(0, level - 15);
            cold.put(id, level);
            double fraction = level / (double) Math.max(1, settings.blizzardFreezeTicks);

            if (fraction >= 0.3d) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, fraction >= 0.65d ? 1 : 0, true, false), true);
            if (fraction >= 0.65d) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 30, 0, true, false), true);
            if (fraction >= 1.0d && frostTurn && settings.blizzardFrostDamage > 0.0d) {
                player.damage(settings.blizzardFrostDamage);
                try {
                    world.spawnParticle(Particle.SNOW_SHOVEL, player.getLocation().add(0.0d, 1.0d, 0.0d), 12, 0.3d, 0.5d, 0.3d, 0.02d);
                } catch (Throwable cosmeticOnly) {
                    // Decoration only.
                }
            }
            if (level > 0) meter(player, fraction, exposed);
        }
    }

    /** Anything at all overhead, up to the sky, counts as shelter: a roof, an overhang, glass, a tree. */
    private static boolean sheltered(Player player) {
        Location at = player.getEyeLocation();
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        for (int y = at.getBlockY() + 1; y <= Math.min(255, at.getBlockY() + 48); y++) {
            if (world.getBlockAt(x, y, z).getType() != Material.AIR) return true;
        }
        return false;
    }

    /** Close to a torch, fire, lava or another bright light: warm enough. */
    private boolean warm(Player player) {
        if (player.getFireTicks() > 0) return true;
        return player.getLocation().getBlock().getLightFromBlocks() >= settings.blizzardWarmLight
                || player.getEyeLocation().getBlock().getLightFromBlocks() >= settings.blizzardWarmLight;
    }

    private static void meter(Player player, double fraction, boolean exposed) {
        int bars = (int) Math.round(Math.min(1.0d, fraction) * 10.0d);
        StringBuilder line = new StringBuilder();
        line.append(fraction >= 1.0d ? ChatColor.DARK_AQUA + "❄ Frostbite " : ChatColor.AQUA + "❄ Cold ");
        for (int i = 0; i < 10; i++) line.append(i < bars ? ChatColor.AQUA + "|" : ChatColor.DARK_GRAY + "|");
        line.append(exposed ? ChatColor.GRAY + "  find a roof or a fire" : ChatColor.GREEN + "  warming up");
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(line.toString()));
        } catch (Throwable olderApi) {
            // The action bar is a convenience; the effects already say it.
        }
    }

    // ------------------------------------------------------------------ passing over and thawing

    private void passOver(Player target) {
        weather.restore();
        cold.clear();
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.GRAY + "The blizzard blows itself out. The snow starts to melt.");
        }
        if (settings.blizzardThaw && (!snow.isEmpty() || !ice.isEmpty())) thawing = true;
        else finished = true;
    }

    /** Puts back up to the given number of snow layers and ice sheets, only where they are still ours. */
    private void thaw(int snowBatch, int iceBatch) {
        for (int i = 0; i < snowBatch && !snow.isEmpty(); i++) {
            Block block = snow.remove(snow.size() - 1);
            snowSet.remove(block);
            if (block.getType() == Material.SNOW) block.setType(Material.AIR, false);
        }
        for (int i = 0; i < iceBatch && !ice.isEmpty(); i++) {
            Block block = ice.remove(ice.size() - 1);
            if (block.getType() == Material.ICE) block.setType(Material.STATIONARY_WATER, false);
        }
    }

    /** Ends the blizzard now: the weather goes back and, when thawing is on, every change is undone at once. */
    @Override public void cancel() {
        if (finished) return;
        weather.restore();
        if (settings.blizzardThaw) thaw(Integer.MAX_VALUE, Integer.MAX_VALUE);
        cold.clear();
        finished = true;
    }
}
