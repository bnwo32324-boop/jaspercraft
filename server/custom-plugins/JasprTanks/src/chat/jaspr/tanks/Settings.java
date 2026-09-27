package chat.jaspr.tanks;

import org.bukkit.configuration.ConfigurationSection;

/** Bounded view of config.yml; every value is clamped so a typo cannot make the cannon absurd. */
final class Settings {
    boolean enabled = true, desktopPlayers = false;
    double healthBonus = 20;
    double stepHeight = 1.0, speedMultiplier = 2.25, armor = 6, knockbackResistance = 1.0;
    int cooldownTicks = 30, range = 96;
    double power = 6.0, speed = 2.5, aimAssist = 0.45, homingDegrees = 9;
    boolean breakBlocks = false, fire = false, damagePlayers = true;
    double flySpeed = 0.12, strikePower = 4.0, pickupRange = 24, autoPickupRadius = 8, followHeight = 3.0, followDistance = 2.0;
    int strikeDrones = 3, strikeCooldownTicks = 50;

    static Settings load(ConfigurationSection c) {
        Settings s = new Settings();
        s.enabled = c.getBoolean("enabled", s.enabled);
        s.desktopPlayers = c.getBoolean("desktop-players", s.desktopPlayers);
        s.healthBonus = clamp(c.getDouble("mobile.health-bonus", s.healthBonus), 0, 40);
        s.stepHeight = clamp(c.getDouble("tank.step-height", s.stepHeight), 0.6, 1.25);
        s.speedMultiplier = clamp(c.getDouble("tank.speed-multiplier", s.speedMultiplier), 1, 3);
        s.armor = clamp(c.getDouble("tank.armor", s.armor), 0, 20);
        s.knockbackResistance = clamp(c.getDouble("tank.knockback-resistance", s.knockbackResistance), 0, 1);
        s.cooldownTicks = (int) clamp(c.getInt("cannon.cooldown-ticks", s.cooldownTicks), 5, 400);
        s.power = clamp(c.getDouble("cannon.power", s.power), 1, 10);
        s.range = (int) clamp(c.getInt("cannon.range", s.range), 16, 160);
        s.speed = clamp(c.getDouble("cannon.speed", s.speed), 0.5, 4);
        s.aimAssist = clamp(c.getDouble("cannon.aim-assist", s.aimAssist), 0, 1.5);
        s.homingDegrees = clamp(c.getDouble("cannon.homing-degrees", s.homingDegrees), 0, 30);
        s.breakBlocks = c.getBoolean("cannon.break-blocks", s.breakBlocks);
        s.fire = c.getBoolean("cannon.fire", s.fire);
        s.damagePlayers = c.getBoolean("cannon.damage-players", s.damagePlayers);
        s.flySpeed = clamp(c.getDouble("sentinel.fly-speed", s.flySpeed), 0.05, 0.3);
        s.strikeDrones = (int) clamp(c.getInt("sentinel.strike-drones", s.strikeDrones), 1, 6);
        s.strikePower = clamp(c.getDouble("sentinel.strike-power", s.strikePower), 1, 8);
        s.strikeCooldownTicks = (int) clamp(c.getInt("sentinel.strike-cooldown-ticks", s.strikeCooldownTicks), 10, 400);
        s.pickupRange = clamp(c.getDouble("sentinel.pickup-range", s.pickupRange), 4, 48);
        s.autoPickupRadius = clamp(c.getDouble("sentinel.auto-pickup-radius", s.autoPickupRadius), 2, 16);
        s.followHeight = clamp(c.getDouble("sentinel.follow-height", s.followHeight), 1, 12);
        s.followDistance = clamp(c.getDouble("sentinel.follow-distance", s.followDistance), 0, 8);
        return s;
    }

    /** Bukkit walk speed while driving (vanilla 0.2). */
    float walkSpeed() { return (float) Math.min(1.0, 0.2 * speedMultiplier); }

    int cooldownFor(Tank.Mode mode) { return mode == Tank.Mode.SENTINEL ? strikeCooldownTicks : cooldownTicks; }

    /** Ticks a shell may fly before it detonates wherever it is. */
    int shellLifetime() { return (int) Math.ceil(range / speed) + 40; }

    static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
