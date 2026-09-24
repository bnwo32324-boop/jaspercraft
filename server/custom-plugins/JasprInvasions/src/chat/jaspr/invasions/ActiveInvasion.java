package chat.jaspr.invasions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * One player's invasion.
 *
 * Every invaded player gets one of these, with its own difficulty, its own spawn budget and its
 * own roster. That is what makes this work on a shared server: two people on opposite sides of the
 * map are each fighting their own siege, scaled to their own progress, at the same time.
 */
final class ActiveInvasion {
    private final InvasionConfig settings;
    private final InvaderFactory factory;
    private final InvaderBrain brain;
    private final Random random;

    private final UUID playerId;
    private final String playerName;
    private final String worldName;
    private final double difficulty;
    private final boolean test;
    private final long startedTick;
    private final long deadlineTick;

    private final List<Invader> invaders = new ArrayList<Invader>();
    private final List<Location> pillars = new ArrayList<Location>();

    private final int budgetTotal;
    private int budgetLeft;
    private int spawnedTotal;
    private int waves;
    private long nextWaveTick;
    private int failedTries;
    private boolean allowLitSpawns;
    private boolean finished;
    private boolean survived;

    ActiveInvasion(InvasionConfig settings, InvaderFactory factory, InvaderBrain brain, Random random,
                   Player owner, double difficulty, boolean test, long nowTick, long maxDurationTicks) {
        this.settings = settings;
        this.factory = factory;
        this.brain = brain;
        this.random = random;
        this.playerId = owner.getUniqueId();
        this.playerName = owner.getName();
        this.worldName = owner.getWorld().getName();
        this.difficulty = difficulty;
        this.test = test;
        this.startedTick = nowTick;
        this.deadlineTick = nowTick + maxDurationTicks;
        this.budgetTotal = factory.waveBudget(difficulty);
        this.budgetLeft = budgetTotal;
        // A short grace period, so the warning lands before the first wave does.
        this.nextWaveTick = nowTick + 60L;
        announce(owner);
    }

    UUID playerId() { return playerId; }

    String playerName() { return playerName; }

    double difficulty() { return difficulty; }

    boolean isFinished() { return finished; }

    boolean survived() { return survived; }

    boolean isTest() { return test; }

    int aliveCount() { return invaders.size(); }

    int spawnedTotal() { return spawnedTotal; }

    int budgetTotal() { return budgetTotal; }

    void notePillar(Location where) {
        if (pillars.size() < 4096) pillars.add(where);
    }

    private void announce(Player owner) {
        String tier = InvaderFactory.tierName(difficulty);
        owner.sendMessage(ChatColor.DARK_RED + "Something has found you. "
                + ChatColor.GRAY + "Invasion incoming - " + ChatColor.RED + tier
                + ChatColor.GRAY + " (" + budgetTotal + " invaders).");
        try {
            owner.sendTitle(ChatColor.DARK_RED + "Invasion", ChatColor.GRAY + tier, 10, 60, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.6f);
        if (settings.broadcast && !test) {
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ " + ChatColor.RED
                    + "An invasion is closing in on " + ChatColor.WHITE + playerName + ChatColor.RED + ".");
        }
    }

    // ------------------------------------------------------------------ main loop

    /** Advances this invasion. {@code globalAlive} is the invader count across the whole server. */
    void tick(long nowTick, int globalAlive) {
        if (finished) return;

        prune();

        Player owner = Bukkit.getPlayer(playerId);
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            end(false, true);
            return;
        }
        World world = owner.getWorld();
        if (!world.getName().equals(worldName)) {
            end(false, true);
            return;
        }

        if (nowTick >= deadlineTick) {
            end(true, true);
            return;
        }

        // Invasions no longer burn off at sunrise: day and night waves fight on equally.

        if (budgetLeft > 0 && nowTick >= nextWaveTick) spawnWave(owner, nowTick, globalAlive);

        for (Iterator<Invader> it = invaders.iterator(); it.hasNext(); ) {
            Invader invader = it.next();
            if (!brain.tick(invader, owner, this, nowTick)) it.remove();
        }

        if (budgetLeft <= 0 && invaders.isEmpty() && nowTick > startedTick + 200L) end(true, false);
    }

    private void prune() {
        for (Iterator<Invader> it = invaders.iterator(); it.hasNext(); ) {
            LivingEntity mob = it.next().entity;
            if (mob == null || !mob.isValid() || mob.isDead()) it.remove();
        }
    }

    // ------------------------------------------------------------------ spawning

    private void spawnWave(Player owner, long nowTick, int globalAlive) {
        int roomGlobal = settings.maxConcurrentGlobal - globalAlive;
        int roomLocal = settings.maxConcurrentPerPlayer - invaders.size();
        int room = Math.min(roomGlobal, roomLocal);
        if (room <= 0) {
            // Server is at capacity. Try again shortly rather than dropping the wave.
            nextWaveTick = nowTick + 100L;
            return;
        }

        int batch = Math.min(room, Math.min(budgetLeft, Math.max(2, budgetTotal / 4)));
        int spawnedThisWave = 0;
        for (int i = 0; i < batch; i++) {
            Location at = findSpawn(owner);
            if (at == null) continue;
            LivingEntity mob = factory.spawn(at, difficulty);
            if (mob == null) continue;
            if (test) {
                // A daylight test would otherwise just be a bonfire.
                mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 10, 0, true, false), true);
            }
            invaders.add(new Invader(mob, factory.roleFor(mob, difficulty)));
            budgetLeft--;
            spawnedTotal++;
            spawnedThisWave++;
        }

        if (spawnedThisWave > 0) {
            waves++;
            nextWaveTick = nowTick + settings.waveSpacingTicks;
        } else {
            nextWaveTick = nowTick + 60L;
        }
    }

    /** A dark, standable spot in the ring around the player. Null when nowhere suitable turned up. */
    private Location findSpawn(Player owner) {
        World world = owner.getWorld();
        Location base = owner.getLocation();
        int span = settings.spawnRangeMax - settings.spawnRangeMin;

        for (int attempt = 0; attempt < settings.attemptsPerSpawn; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = settings.spawnRangeMin + (span <= 0 ? 0.0d : random.nextDouble() * span);
            int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            int y = findStandableY(world, x, z, base.getBlockY());
            if (y < 0) continue;

            Block feet = world.getBlockAt(x, y, z);
            if (!allowLitSpawns && feet.getLightFromBlocks() > settings.maxLightForSpawn) continue;

            return new Location(world, x + 0.5d, y, z + 0.5d);
        }

        failedTries++;
        if (failedTries >= settings.failedTriesBeforeLitSpawns) {
            // Torch-lit base, no dark corners left. They come anyway.
            allowLitSpawns = true;
        }
        return null;
    }

    /** Prefers the player's own elevation, so a base dug into a hillside still gets visitors. */
    private int findStandableY(World world, int x, int z, int aroundY) {
        for (int offset = 0; offset <= 14; offset++) {
            int up = aroundY + offset;
            if (standable(world, x, up, z)) return up;
            if (offset == 0) continue;
            int down = aroundY - offset;
            if (standable(world, x, down, z)) return down;
        }
        int top = world.getHighestBlockYAt(x, z);
        return standable(world, x, top, z) ? top : -1;
    }

    private boolean standable(World world, int x, int y, int z) {
        if (y < 2 || y > 250) return false;
        Block floor = world.getBlockAt(x, y - 1, z);
        Material under = floor.getType();
        if (!under.isSolid()) return false;
        if (under == Material.MAGMA || under == Material.CACTUS) return false;
        return world.getBlockAt(x, y, z).getType() == Material.AIR
                && world.getBlockAt(x, y + 1, z).getType() == Material.AIR;
    }

    // ------------------------------------------------------------------ ending

    /** Ends the invasion. {@code clearMobs} removes survivors outright rather than leaving them. */
    void end(boolean survived, boolean clearMobs) {
        if (finished) return;
        finished = true;
        this.survived = survived;

        if (clearMobs) {
            for (Invader invader : invaders) {
                if (invader.entity != null && invader.entity.isValid()) invader.entity.remove();
            }
        }
        invaders.clear();

        // Siege ladders come down with the siege.
        for (Location where : pillars) {
            World world = where.getWorld();
            if (world == null) continue;
            if (!world.isChunkLoaded(where.getBlockX() >> 4, where.getBlockZ() >> 4)) continue;
            Block block = where.getBlock();
            if (block.getType() == Material.COBBLESTONE) block.setType(Material.AIR, false);
        }
        pillars.clear();

        Player owner = Bukkit.getPlayer(playerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(survived
                    ? ChatColor.GREEN + "The invasion breaks. " + ChatColor.GRAY + "You held."
                    : ChatColor.GRAY + "The invasion moves on.");
        }
    }

    void cancel() {
        end(false, true);
    }
}
