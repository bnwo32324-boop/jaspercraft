package chat.jaspr.tanks;

import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;

/**
 * A TNT shell or strike drone in flight: straight toward the crosshair point, or curving after the creature it
 * was fired at.
 */
final class Shell {
    final TNTPrimed tnt;
    final UUID shooter;
    final double[] target;
    final float power;
    LivingEntity quarry;
    double[] dir;
    double left;
    int age;

    Shell(TNTPrimed tnt, UUID shooter, double[] dir, double[] target, double distance, LivingEntity quarry, float power) {
        this.tnt = tnt;
        this.shooter = shooter;
        this.dir = dir;
        this.target = target;
        this.left = distance;
        this.quarry = quarry;
        this.power = power;
    }
}
