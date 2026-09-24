package chat.jaspr.revive;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

/**
 * One player's bleed-out. Mirrors PlayerRevive's Revival capability: a countdown
 * that kills when it runs out, and a progress counter that each helper advances
 * by one per tick, so two helpers revive in half the time.
 */
final class Downed {
    final UUID id;
    final String name;
    Location where;
    int ticksLeft;
    float progress;
    /** Helpers currently holding the revive. Insertion ordered so messages read sensibly. */
    final Set<UUID> revivers = new LinkedHashSet<UUID>();
    boolean wasInvulnerable;
    boolean wasCollidable;
    boolean wasAllowFlight;
    boolean wasGravity;
    org.bukkit.boss.BossBar bar;

    Downed(UUID id, String name, Location where, int ticksLeft) {
        this.id = id;
        this.name = name;
        this.where = where;
        this.ticksLeft = ticksLeft;
    }

    int secondsLeft() { return Math.max(0, (ticksLeft + 19) / 20); }
}
