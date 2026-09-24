package chat.jaspr.daylight;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Daylight torpor: while the sun is up, everything that isn't a player walks at
 * half speed. Underground counts, because the rule is the clock and not the sky
 * -- a mob in a cave at noon is as sluggish as one standing in a field.
 *
 * The slowdown is an attribute modifier rather than a Slowness effect: Slowness
 * comes in fifteen percent steps, so it cannot express a half, and it paints
 * swirling particles on every mob on the server. A modifier with a fixed id can
 * also be recognised and removed again, which is what makes dusk work.
 */
public final class DaylightPlugin extends JavaPlugin implements Listener {
    /** Fixed, so the modifier is recognised on reload and can never stack. */
    private static final UUID MODIFIER_ID = UUID.fromString("da41167f-70a5-4d0e-9b3c-51ee900da711");
    private static final String MODIFIER_NAME = "jaspr.daylight.torpor";

    private int slowed;

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { sweep(); }
        }, 40L, 20L);
        getLogger().info("DAYLIGHT_READY multiplier=" + multiplier() + " night=" + nightStart() + "-" + nightEnd()
            + " allWorlds=" + allWorlds() + " exemptRidden=" + exemptRidden());
    }

    @Override public void onDisable() {
        // Leave nothing behind: a modifier that outlived the plugin would be
        // invisible and permanent, and nobody would know where it came from.
        for (World world : Bukkit.getWorlds())
            for (LivingEntity entity : world.getLivingEntities()) clear(entity);
    }

    // -- settings ---------------------------------------------------------------

    boolean enabled() { return getConfig().getBoolean("daylight.enabled", true); }
    double multiplier() { return Math.max(0.05, Math.min(1.0, getConfig().getDouble("daylight.speed-multiplier", 0.5))); }
    long nightStart() { return getConfig().getLong("daylight.night-start", 12500L); }
    long nightEnd() { return getConfig().getLong("daylight.night-end", 23500L); }
    boolean stormsAreNight() { return getConfig().getBoolean("daylight.storms-count-as-night", false); }
    boolean exemptRidden() { return getConfig().getBoolean("daylight.exempt-ridden-mounts", true); }
    boolean exemptTamed() { return getConfig().getBoolean("daylight.exempt-tamed", false); }
    boolean allWorlds() { return getConfig().getBoolean("daylight.all-worlds", true); }

    // -- the law ----------------------------------------------------------------

    /** The overworld clock rules every world; the Nether has no sunrise of its own. */
    public boolean isDaylight() {
        if (!enabled()) return false;
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return false;
        World clock = worlds.get(0);
        if (stormsAreNight() && clock.isThundering()) return false;
        long time = ((clock.getTime() % 24000L) + 24000L) % 24000L;
        return time < nightStart() || time > nightEnd();
    }

    /**
     * The multiplier in force right now. Public and reflection-friendly on
     * purpose: JasprApocalypse's siege zombies steer themselves with explicit
     * velocities instead of pathfinding, so they have to ask for this rather
     * than inherit it from the attribute.
     */
    public double factorFor(World world) {
        if (world != null && !allWorlds() && world.getEnvironment() != World.Environment.NORMAL) return 1.0;
        return isDaylight() ? multiplier() : 1.0;
    }

    private void sweep() {
        if (!enabled()) return;
        boolean day = isDaylight();
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            boolean active = day && (allWorlds() || world.getEnvironment() == World.Environment.NORMAL);
            for (LivingEntity entity : world.getLivingEntities()) {
                if (skip(entity)) continue;
                if (active && !exempt(entity)) { if (apply(entity)) count++; else if (has(entity)) count++; }
                else clear(entity);
            }
        }
        slowed = count;
    }

    private boolean skip(Entity entity) {
        return entity instanceof Player || entity instanceof ArmorStand;
    }

    private boolean exempt(LivingEntity entity) {
        if (exemptRidden()) {
            for (Entity passenger : entity.getPassengers()) if (passenger instanceof Player) return true;
        }
        if (exemptTamed() && entity instanceof Tameable && ((Tameable) entity).isTamed()) return true;
        return false;
    }

    // -- the modifier -----------------------------------------------------------

    private AttributeInstance speed(LivingEntity entity) {
        try { return entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED); }
        catch (RuntimeException e) { return null; }
    }

    private boolean has(LivingEntity entity) {
        AttributeInstance instance = speed(entity);
        if (instance == null) return false;
        for (AttributeModifier modifier : instance.getModifiers())
            if (MODIFIER_ID.equals(modifier.getUniqueId())) return true;
        return false;
    }

    private boolean apply(LivingEntity entity) {
        AttributeInstance instance = speed(entity);
        if (instance == null) return false;
        double amount = multiplier() - 1.0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!MODIFIER_ID.equals(modifier.getUniqueId())) continue;
            // Already slowed. Re-seat it only if the configured amount moved.
            if (Math.abs(modifier.getAmount() - amount) < 1.0e-9) return false;
            try { instance.removeModifier(modifier); } catch (RuntimeException e) { return false; }
            break;
        }
        try {
            // MULTIPLY_SCALAR_1 scales the whole attribute, so this is a true half
            // of whatever that mob's speed happens to be rather than a flat subtraction.
            instance.addModifier(new AttributeModifier(MODIFIER_ID, MODIFIER_NAME, amount,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    private void clear(LivingEntity entity) {
        AttributeInstance instance = speed(entity);
        if (instance == null) return;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!MODIFIER_ID.equals(modifier.getUniqueId())) continue;
            try { instance.removeModifier(modifier); } catch (RuntimeException ignored) { }
            return;
        }
    }

    /** Catch a mob the moment it appears, so it never gets a fast first second. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (!enabled()) return;
        final LivingEntity entity = event.getEntity();
        getServer().getScheduler().runTask(this, new Runnable() {
            @Override public void run() {
                if (!entity.isValid() || skip(entity)) return;
                World world = entity.getWorld();
                boolean active = isDaylight() && (allWorlds() || world.getEnvironment() == World.Environment.NORMAL);
                if (active && !exempt(entity)) apply(entity); else clear(entity);
            }
        });
    }

    // -- status -----------------------------------------------------------------

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        World clock = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        long time = clock == null ? -1 : ((clock.getTime() % 24000L) + 24000L) % 24000L;
        sender.sendMessage(ChatColor.GRAY + "Daylight torpor: " + (enabled() ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
        sender.sendMessage(ChatColor.GRAY + "  clock " + ChatColor.WHITE + time
            + ChatColor.GRAY + " -> " + (isDaylight() ? ChatColor.YELLOW + "day" : ChatColor.DARK_AQUA + "night"));
        sender.sendMessage(ChatColor.GRAY + "  walking speed now " + ChatColor.WHITE
            + Math.round(factorFor(clock) * 100) + "%" + ChatColor.GRAY + " of normal");
        sender.sendMessage(ChatColor.GRAY + "  mobs currently slowed: " + ChatColor.WHITE + slowed);
        return true;
    }
}
