package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * One meteor shower, aimed at one player. Only ever one of these exists at a time, which is what
 * keeps the cost bounded on a shared server: a shower is a handful of short-lived falling blocks
 * near a single player, not a world-wide effect.
 */
final class MeteorShower implements Disaster {
    static final String METADATA_KEY = "jaspr_meteor";

    private final Plugin plugin;
    private final DisasterConfig settings;
    private final Random random;
    private final String targetName;
    private final Map<UUID, Live> live = new HashMap<UUID, Live>();

    /** A meteor currently in the air, held directly so nothing has to scan the world for it. */
    private static final class Live {
        final FallingBlock entity;
        final long deadlineTick;
        Live(FallingBlock entity, long deadlineTick) { this.entity = entity; this.deadlineTick = deadlineTick; }
    }

    private int meteorsLeft;
    private long nextSpawnTick;
    private boolean finished;

    MeteorShower(Plugin plugin, DisasterConfig settings, Random random, Player target, long nowTicks) {
        this.plugin = plugin;
        this.settings = settings;
        this.random = random;
        this.targetName = target.getName();
        this.meteorsLeft = settings.meteorCount;
        // A short grace period so the warning lands before the first rock does.
        this.nextSpawnTick = nowTicks + 40L;
        announce(target);
    }

    @Override public String targetName() { return targetName; }

    @Override public boolean isFinished() { return finished; }

    private void announce(Player target) {
        target.sendMessage(ChatColor.GOLD + "The sky splits open above you. " + ChatColor.GRAY + "Meteor shower incoming.");
        try {
            target.sendTitle(ChatColor.RED + "Meteor Shower", ChatColor.GRAY + "Find cover", 10, 50, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 2.0f, 0.5f);
        if (settings.broadcast) {
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "☀ " + ChatColor.RED + "A meteor shower is falling on "
                    + ChatColor.WHITE + targetName + ChatColor.RED + "!");
        }
    }

    /** Advances the shower. Returns once every meteor has been spawned and has landed. */
    @Override public void tick(long nowTicks) {
        if (finished) return;

        expireOverdue(nowTicks);

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            // The target left. Stop spawning, but let anything already falling finish.
            meteorsLeft = 0;
            if (live.isEmpty()) finished = true;
            return;
        }

        if (meteorsLeft > 0 && nowTicks >= nextSpawnTick) {
            spawnMeteor(target, nowTicks);
            meteorsLeft--;
            nextSpawnTick = nowTicks + settings.meteorSpacingTicks;
        }

        if (meteorsLeft <= 0 && live.isEmpty()) finished = true;
    }

    private void spawnMeteor(Player target, long nowTicks) {
        Location base = target.getLocation();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        double distance = random.nextDouble() * settings.spawnRadius;
        double x = base.getX() + Math.cos(angle) * distance;
        double z = base.getZ() + Math.sin(angle) * distance;
        double y = Math.min(254.0d, base.getY() + settings.fallHeight);

        Location spawnAt = new Location(base.getWorld(), x, y, z);
        FallingBlock meteor;
        try {
            meteor = base.getWorld().spawnFallingBlock(spawnAt, Material.MAGMA, (byte) 0);
        } catch (Throwable blocked) {
            return;
        }
        meteor.setDropItem(false);
        meteor.setHurtEntities(true);
        meteor.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, Boolean.TRUE));
        // A little sideways drift so they do not fall in a perfect column.
        meteor.setVelocity(new Vector((random.nextDouble() - 0.5d) * 0.2d, -0.6d, (random.nextDouble() - 0.5d) * 0.2d));

        live.put(meteor.getUniqueId(), new Live(meteor, nowTicks + settings.meteorTimeoutTicks));
        base.getWorld().playSound(base, Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.7f);
    }

    /** A meteor that never fired a landing event (despawned, chunk unloaded) still detonates. */
    private void expireOverdue(long nowTicks) {
        if (live.isEmpty()) return;
        List<Live> done = null;
        for (Iterator<Map.Entry<UUID, Live>> it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Live> entry = it.next();
            if (nowTicks < entry.getValue().deadlineTick) continue;
            if (done == null) done = new ArrayList<Live>();
            done.add(entry.getValue());
            it.remove();
        }
        if (done == null) return;
        for (Live overdue : done) {
            if (!overdue.entity.isValid()) continue;
            Location where = overdue.entity.getLocation();
            overdue.entity.remove();
            Impacts.strike(settings, where, random);
        }
    }

    /** Called when one of our meteors touches down. Returns true if it was ours. */
    boolean onLanded(UUID id, Location where) {
        if (live.remove(id) == null) return false;
        Impacts.strike(settings, where, random);
        if (meteorsLeft <= 0 && live.isEmpty()) finished = true;
        return true;
    }

    boolean owns(UUID id) { return live.containsKey(id); }

    /** Removes anything still in the air, used on shutdown. */
    @Override public void cancel() {
        for (Live meteor : new ArrayList<Live>(live.values())) {
            if (meteor.entity.isValid()) meteor.entity.remove();
        }
        live.clear();
        meteorsLeft = 0;
        finished = true;
    }
}
