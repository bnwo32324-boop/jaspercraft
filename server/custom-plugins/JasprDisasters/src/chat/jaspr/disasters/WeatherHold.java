package chat.jaspr.disasters;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * World weather borrowed for one disaster and handed back afterwards.
 *
 * Minecraft weather is world-wide, so a storm, tornado or blizzard turns the sky everywhere; what was
 * there before is captured on the way in and restored on the way out, including when the plugin is
 * disabled mid-event, so a disaster never leaves the world permanently raining. Someone running
 * /weather clear mid-event does not call it off either.
 */
final class WeatherHold {
    private final String worldName;
    private final boolean thunder;
    private final boolean priorStorm;
    private final boolean priorThunder;
    private final int priorWeatherDuration;
    private final int priorThunderDuration;
    private long nextHoldTick;
    private boolean restored;

    WeatherHold(World world, boolean thunder, long durationTicks) {
        this.worldName = world.getName();
        this.thunder = thunder;
        this.priorStorm = world.hasStorm();
        this.priorThunder = world.isThundering();
        this.priorWeatherDuration = world.getWeatherDuration();
        this.priorThunderDuration = world.getThunderDuration();
        open(world, durationTicks + 400L);
    }

    private void open(World world, long span) {
        world.setStorm(true);
        world.setThundering(thunder);
        world.setWeatherDuration((int) Math.min(Integer.MAX_VALUE, span));
        if (thunder) world.setThunderDuration((int) Math.min(Integer.MAX_VALUE, span));
    }

    /** Keeps the weather on until the event ends. Cheap: looks at the sky every three seconds. */
    void hold(World world, long nowTicks, long endTick) {
        if (restored || nowTicks < nextHoldTick) return;
        nextHoldTick = nowTicks + 60L;
        long remaining = Math.max(200L, endTick - nowTicks + 200L);
        if (!world.hasStorm() || (thunder && !world.isThundering())) open(world, remaining);
        if (world.getWeatherDuration() < remaining) world.setWeatherDuration((int) remaining);
        if (thunder && world.getThunderDuration() < remaining) world.setThunderDuration((int) remaining);
    }

    /** Puts the world's own weather back. Safe to call twice. */
    void restore() {
        if (restored) return;
        restored = true;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        world.setStorm(priorStorm);
        world.setThundering(priorThunder);
        world.setWeatherDuration(Math.max(600, priorWeatherDuration));
        world.setThunderDuration(Math.max(600, priorThunderDuration));
    }
}
