package chat.jaspr.tanks;

import org.bukkit.configuration.file.YamlConfiguration;

/** Pure JasprTanks logic: aiming rays, shell steering, browser classes and config clamps (run: tests/tanks.test.cjs). */
public final class TanksLogicTest {
    private static void ok(boolean condition, String what) { if (!condition) throw new AssertionError(what); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    public static void main(String[] args) {
        double[] cube = {0, 0, 0, 1, 1, 1};
        ok(near(Aim.ray(-2, 0.5, 0.5, 1, 0, 0, cube, 10), 2), "ray enters the near face");
        ok(Aim.ray(-2, 0.5, 0.5, -1, 0, 0, cube, 10) < 0, "ray pointing away misses");
        ok(Aim.ray(-2, 2.5, 0.5, 1, 0, 0, cube, 10) < 0, "ray above misses");
        ok(Aim.ray(-20, 0.5, 0.5, 1, 0, 0, cube, 10) < 0, "entry beyond max misses");
        ok(near(Aim.ray(0.5, 0.5, 0.5, 0, 1, 0, cube, 10), 0), "origin inside hits at zero");
        ok(near(Aim.ray(-2, 2.2, 0.5, 1, 0, 0, Aim.grow(cube, 1.5), 10), 0.5), "aim assist grows the box");

        double[] south = Aim.direction(0, 0), west = Aim.direction(90, 0), down = Aim.direction(0, 90);
        ok(near(south[2], 1) && near(south[0], 0), "yaw 0 faces +Z");
        ok(near(west[0], -1), "yaw 90 faces -X");
        ok(near(down[1], -1), "pitch 90 looks down");

        double[] turned = Aim.steer(new double[] {1, 0, 0}, new double[] {0, 0, 1}, 10);
        ok(near(Math.toDegrees(Math.acos(turned[0])), 10), "steering turns at most the limit");
        ok(near(Math.sqrt(turned[0] * turned[0] + turned[1] * turned[1] + turned[2] * turned[2]), 1), "steering keeps a unit vector");
        double[] reached = Aim.steer(new double[] {1, 0, 0}, new double[] {0.9950041652780258, 0.09983341664682815, 0}, 10);
        ok(near(reached[1], 0.09983341664682815), "small corrections land exactly");

        ok(Device.classify("Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile Safari/537.36") == Device.Kind.MOBILE, "android");
        ok(Device.classify("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)") == Device.Kind.MOBILE, "iphone");
        ok(Device.classify("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15") == Device.Kind.APPLE_DESKTOP, "iPadOS looks like a Mac");
        ok(Device.classify("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0") == Device.Kind.DESKTOP, "windows");
        ok(Device.classify(null) == Device.Kind.UNKNOWN && Device.classify("  ") == Device.Kind.UNKNOWN, "relayed connections have no agent");

        Settings defaults = Settings.load(new YamlConfiguration());
        ok(defaults.cooldownTicks == 30 && near(defaults.power, 6) && !defaults.breakBlocks, "defaults");
        ok(near(defaults.walkSpeed(), 0.45), "average-horse walk speed");
        ok(near(defaults.healthBonus, 20), "mobile players get double health");
        ok(defaults.cooldownFor(Tank.Mode.SENTINEL) == 50 && defaults.cooldownFor(Tank.Mode.TANK) == 30, "per-mode reload");
        ok(Tank.Mode.parse("SENTINEL") == Tank.Mode.SENTINEL && Tank.Mode.parse("x") == Tank.Mode.TANK, "mode parsing");
        YamlConfiguration wild = new YamlConfiguration();
        wild.set("cannon.power", 500);
        wild.set("cannon.cooldown-ticks", 0);
        wild.set("tank.speed-multiplier", 40);
        wild.set("sentinel.strike-drones", 99);
        wild.set("mobile.health-bonus", 1000);
        Settings clamped = Settings.load(wild);
        ok(near(clamped.power, 10) && clamped.cooldownTicks == 5 && near(clamped.walkSpeed(), 0.6f), "config values are clamped");
        ok(clamped.strikeDrones == 6 && near(clamped.healthBonus, 40), "sentinel and health values are clamped");
        System.out.println("TANKS_LOGIC_OK");
    }
}
