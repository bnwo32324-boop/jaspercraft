package chat.jaspr.invasions;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** One spawned invader and the small amount of state its behaviour needs between ticks. */
final class Invader {
    final LivingEntity entity;
    final InvaderRole role;

    double lastX;
    double lastY;
    double lastZ;
    int stuckChecks;
    long nextActionTick;
    int pillarsPlaced;

    Invader(LivingEntity entity, InvaderRole role) {
        this.entity = entity;
        this.role = role;
        Location at = entity.getLocation();
        this.lastX = at.getX();
        this.lastY = at.getY();
        this.lastZ = at.getZ();
    }

    /** True when this invader has barely moved since the last check, so something is in its way. */
    boolean updateStuck(double threshold) {
        Location at = entity.getLocation();
        double dx = at.getX() - lastX;
        double dy = at.getY() - lastY;
        double dz = at.getZ() - lastZ;
        lastX = at.getX();
        lastY = at.getY();
        lastZ = at.getZ();
        if (dx * dx + dy * dy + dz * dz < threshold * threshold) {
            stuckChecks++;
        } else {
            stuckChecks = 0;
        }
        return stuckChecks >= 2;
    }
}
