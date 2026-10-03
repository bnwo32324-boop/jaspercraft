package chat.jaspr.daylight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
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
 * Owner 2026-10-03: dungeon and structure enemies are the exception and are left
 * completely alone at every hour -- anything from a spawner (including spawners
 * other plugins run in daylight), anything that appears inside a vanilla
 * structure, anything another plugin marks as a dungeon, structure or boss enemy,
 * and everything in the dungeon worlds. Only the ordinary night creatures that
 * spawn in the open feel the sun.
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
    /** Keeps a mob at full speed at every hour. Other plugins write it; so does this one, on dungeon and structure mobs. */
    static final String EXEMPT_TAG = "jaspr_daylight_exempt";

    private int slowed, exempted, structureFailures;
    private List<String> exemptTags = new ArrayList<String>(), exemptPrefixes = new ArrayList<String>(),
        exemptWorlds = new ArrayList<String>(), exemptStructures = new ArrayList<String>();

    @Override public void onEnable() {
        saveDefaultConfig();
        loadExemptions();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { sweep(); }
        }, 40L, 20L);
        // Mobs already in the world when this starts get the full one-time check, as if they had just appeared.
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                int found = 0;
                for (World world : Bukkit.getWorlds())
                    for (LivingEntity entity : world.getLivingEntities()) if (!skip(entity) && !structural(entity) && classify(entity, null)) found++;
                getLogger().info("DAYLIGHT_EXEMPT_SCAN structureMobsFound=" + found);
            }
        }, 30L);
        getLogger().info("DAYLIGHT_READY multiplier=" + multiplier() + " night=" + nightStart() + "-" + nightEnd()
            + " allWorlds=" + allWorlds() + " exemptRidden=" + exemptRidden() + " exemptSpawners=" + exemptSpawners()
            + " exemptStructures=" + exemptStructures + " exemptTags=" + exemptTags.size() + "+" + exemptPrefixes.size()
            + " exemptWorlds=" + exemptWorlds);
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
    boolean exemptSpawners() { return getConfig().getBoolean("daylight.exempt-spawners", true); }

    private void loadExemptions() {
        exemptTags = lower(getConfig().getStringList("daylight.exempt-tags"));
        if (!exemptTags.contains(EXEMPT_TAG)) exemptTags.add(EXEMPT_TAG);
        exemptPrefixes = lower(getConfig().getStringList("daylight.exempt-tag-prefixes"));
        exemptWorlds = lower(getConfig().getStringList("daylight.exempt-world-prefixes"));
        exemptStructures = new ArrayList<String>(getConfig().getStringList("daylight.exempt-structures"));
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<String>();
        for (String s : in) if (s != null && !s.trim().isEmpty()) out.add(s.trim().toLowerCase(Locale.ROOT));
        return out;
    }

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
        int count = 0, free = 0;
        for (World world : Bukkit.getWorlds()) {
            boolean active = day && (allWorlds() || world.getEnvironment() == World.Environment.NORMAL);
            for (LivingEntity entity : world.getLivingEntities()) {
                if (skip(entity)) continue;
                if (structural(entity)) { clear(entity); free++; continue; }
                if (active && !exempt(entity)) { if (apply(entity)) count++; else if (has(entity)) count++; }
                else clear(entity);
            }
        }
        slowed = count;
        exempted = free;
    }

    private boolean skip(Entity entity) {
        return entity instanceof Player || entity instanceof ArmorStand;
    }

    /**
     * Dungeon and structure enemies: marked by a plugin (or by this one when they appeared), in a dungeon world, or
     * spawned by a vanilla spawner. Cheap enough to ask every sweep.
     */
    private boolean structural(Entity entity) {
        Set<String> tags = entity.getScoreboardTags();
        if (!tags.isEmpty()) {
            for (String tag : tags) {
                String t = tag.toLowerCase(Locale.ROOT);
                if (exemptTags.contains(t)) return true;
                for (String prefix : exemptPrefixes) if (t.startsWith(prefix)) return true;
            }
        }
        String name = entity.getWorld().getName().toLowerCase(Locale.ROOT);
        for (String prefix : exemptWorlds) if (name.startsWith(prefix)) return true;
        if (exemptSpawners()) {
            try { if (entity.fromMobSpawner()) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    /**
     * The one-time check when a mob appears: a spawner spawn, a hostile that appears beside a spawner block (the way
     * plugins run spawners in daylight), or a hostile inside a vanilla structure. A structure mob is tagged, so it
     * keeps its full speed for good, across chunk unloads and restarts. Returns whether it was.
     */
    private boolean classify(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        boolean structure = exemptSpawners() && reason == CreatureSpawnEvent.SpawnReason.SPAWNER;
        if (!structure && hostile(entity)) structure = (exemptSpawners() && besideSpawner(entity)) || inStructure(entity);
        if (!structure) return false;
        entity.addScoreboardTag(EXEMPT_TAG);
        clear(entity);
        return true;
    }

    private static boolean hostile(Entity e) {
        return e instanceof Monster || e instanceof Slime || e instanceof Ghast || e instanceof Shulker;
    }

    /**
     * A mob spawner within five blocks across and three up or down, looking only at loaded chunks. Reads the few
     * nearby chunks' tile entities rather than hundreds of blocks; falls back to the block scan if that fails.
     */
    private boolean besideSpawner(Entity e) {
        Location at = e.getLocation();World w = at.getWorld();
        int x0 = at.getBlockX(), y0 = at.getBlockY(), z0 = at.getBlockZ();
        try {
            for (int cx = (x0 - 5) >> 4; cx <= (x0 + 5) >> 4; cx++) for (int cz = (z0 - 5) >> 4; cz <= (z0 + 5) >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                net.minecraft.server.v1_12_R1.Chunk chunk = ((org.bukkit.craftbukkit.v1_12_R1.CraftChunk) w.getChunkAt(cx, cz)).getHandle();
                for (java.util.Map.Entry<net.minecraft.server.v1_12_R1.BlockPosition, net.minecraft.server.v1_12_R1.TileEntity> te : chunk.getTileEntities().entrySet()) {
                    if (!(te.getValue() instanceof net.minecraft.server.v1_12_R1.TileEntityMobSpawner)) continue;
                    net.minecraft.server.v1_12_R1.BlockPosition p = te.getKey();
                    if (Math.abs(p.getX() - x0) <= 5 && Math.abs(p.getZ() - z0) <= 5 && Math.abs(p.getY() - y0) <= 3) return true;
                }
            }
            return false;
        } catch (Throwable failure) {
            if (structureFailures++ == 0) getLogger().warning("DAYLIGHT_SPAWNER_INDEX_FAILED " + failure);
        }
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            int x = x0 + dx, z = z0 + dz;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int dy = -3; dy <= 3; dy++) {
                int y = y0 + dy;
                if (y >= 0 && y < 256 && w.getBlockAt(x, y, z).getType() == Material.MOB_SPAWNER) return true;
            }
        }
        return false;
    }

    /** Inside a fortress, monument, mansion, temple, stronghold or end city that the world's own generator placed. */
    private boolean inStructure(Entity e) {
        if (exemptStructures.isEmpty()) return false;
        try {
            net.minecraft.server.v1_12_R1.WorldServer world = ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) e.getWorld()).getHandle();
            net.minecraft.server.v1_12_R1.ChunkGenerator generator = world.getChunkProviderServer().chunkGenerator;
            Location at = e.getLocation();
            net.minecraft.server.v1_12_R1.BlockPosition position = new net.minecraft.server.v1_12_R1.BlockPosition(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            for (String name : exemptStructures) if (generator.a(world, name, position)) return true;
        } catch (Throwable failure) {
            if (structureFailures++ == 0) getLogger().warning("DAYLIGHT_STRUCTURE_CHECK_FAILED " + failure);
        }
        return false;
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

    /** Catch a mob the moment it appears, so it never gets a fast first second -- or a slow one, if it is a structure's. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (!enabled()) return;
        final LivingEntity entity = event.getEntity();
        final CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        // A tick later, so the plugin that spawned it has had the chance to tag it.
        getServer().getScheduler().runTask(this, new Runnable() {
            @Override public void run() {
                if (!entity.isValid() || skip(entity)) return;
                if (structural(entity) || classify(entity, reason)) { clear(entity); return; }
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
        sender.sendMessage(ChatColor.GRAY + "  dungeon and structure mobs at full speed: " + ChatColor.WHITE + exempted);
        return true;
    }
}
