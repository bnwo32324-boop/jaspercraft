package chat.jaspr.tanks;

import java.util.UUID;
import org.bukkit.entity.ArmorStand;

/** One mobile player's vehicle: two marker stands (body, turret/pod) that clients pin to the player. */
final class Tank {
    enum Mode {
        TANK((short) 1, (short) 2), SENTINEL((short) 3, (short) 4);
        final short bodyBand, topBand;
        Mode(short bodyBand, short topBand) { this.bodyBand = bodyBand; this.topBand = topBand; }
        String label() { return this == TANK ? "Tank" : "Orbital Sentinel"; }
        static Mode parse(String value) { return "sentinel".equalsIgnoreCase(value) ? SENTINEL : TANK; }
    }

    final UUID driver;
    final String reason;
    Mode mode = Mode.TANK;
    boolean autoPickup;
    UUID follow;
    ArmorStand hull, turret;
    long readyAt;
    boolean announced = true;
    long nextMount, retryAt, fallSafeUntil;
    int shots;

    Tank(UUID driver, String reason) {
        this.driver = driver;
        this.reason = reason;
    }

    boolean built() { return hull != null && turret != null; }
}
