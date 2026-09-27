package chat.jaspr.tanks;

import java.util.UUID;
import org.bukkit.entity.ArmorStand;

/** One driver's tank: two marker stands (hull, turret) that clients pin to the player. */
final class Tank {
    final UUID driver;
    final String reason;
    ArmorStand hull, turret;
    long readyAt;
    boolean announced = true;
    long nextMount, retryAt;
    int shots;

    Tank(UUID driver, String reason) {
        this.driver = driver;
        this.reason = reason;
    }

    boolean built() { return hull != null && turret != null; }
}
