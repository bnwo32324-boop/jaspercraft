package chat.jaspr.voice;

import java.util.UUID;

/** An immutable copy of one player's audible state, taken on the main thread. */
final class PlayerSnapshot {
    final String name;
    final UUID worldId;
    final double x;
    final double y;
    final double z;
    final float yaw;
    final boolean sneaking;
    final boolean spectator;
    final boolean dead;
    /** Head is inside water: the speaker sounds submerged, the listener hears everything submerged. */
    final boolean underwater;
    /** How much the surroundings ring back, 0 in open air through 1 in a sealed chamber. */
    final float reverb;
    /** No sky overhead. Separates a cave's long dark tail from a small room's short one. */
    final boolean enclosed;

    PlayerSnapshot(String name, UUID worldId, double x, double y, double z,
                   float yaw, boolean sneaking, boolean spectator, boolean dead,
                   boolean underwater, float reverb, boolean enclosed) {
        this.name = name;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.sneaking = sneaking;
        this.spectator = spectator;
        this.dead = dead;
        this.underwater = underwater;
        this.reverb = reverb;
        this.enclosed = enclosed;
    }
}
