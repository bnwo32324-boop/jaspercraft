package chat.jaspr.apocalypse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Server-authoritative wall cling and wall jump for every player. It uses the
 * normal sneak state reported by Minecraft, so the same behavior works with
 * keyboard Shift and the native sneak control on touch devices.
 */
public final class WallJumpController implements Listener {
    private static final double WALL_EPSILON = 0.001;
    private static final double FIRST_PROBE_GAP = 0.06;
    private static final double CLING_PROBE_GAP = 0.20;
    private static final double MAX_UPWARD_CLING_SPEED = 0.10;
    private static final double JUMP_BOOST = 0.55;
    private static final int SLIDE_DELAY_TICKS = 15;
    private static final boolean ALLOW_RECLING = false;

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final double jumpBoost;
    private final int slideDelay;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask task;
    private long ticks;
    private long clings;
    private long jumps;
    private long slideTicks;
    private long intentJumps;
    private long facingJumps;

    static final class State {
        boolean sneakWasDown;
        boolean clinging;
        int sneakTicks;
        int clingTicks;
        int walls;
        int staleWalls;
        double clingX;
        double clingZ;
        double intentX;
        double intentZ;
        int intentAge = Integer.MAX_VALUE;
        double lastJumpY = Double.NaN;

        void resetOnGround(boolean sneaking) {
            sneakWasDown = sneaking;
            sneakTicks = 0;
            clingTicks = 0;
            clinging = false;
            walls = 0;
            staleWalls = 0;
            lastJumpY = Double.NaN;
            intentX = 0.0;
            intentZ = 0.0;
            intentAge = Integer.MAX_VALUE;
        }

        void releaseCling() {
            clinging = false;
            clingTicks = 0;
            clingX = Double.NaN;
            clingZ = Double.NaN;
        }

        void rememberIntent(double x, double z) {
            if (x * x + z * z < 1.0e-8) return;
            intentX = x;
            intentZ = z;
            intentAge = 0;
        }

        void ageIntent() {
            if (intentAge < Integer.MAX_VALUE) intentAge = Math.min(Integer.MAX_VALUE - 1, intentAge + 1);
        }

        boolean hasFreshIntent() { return intentAge <= 6 && intentX * intentX + intentZ * intentZ >= 1.0e-8; }
    }

    public WallJumpController(JavaPlugin plugin) {
        this.plugin = plugin;
        enabled = plugin.getConfig().getBoolean("wall-jump.enabled", true);
        jumpBoost = clamp(plugin.getConfig().getDouble("wall-jump.jump-boost", JUMP_BOOST), 0.30, 0.90);
        slideDelay = clamp(plugin.getConfig().getInt("wall-jump.slide-delay-ticks", SLIDE_DELAY_TICKS), 0, 60);
    }

    public void start() {
        if (!enabled || task != null) {
            if (!enabled) plugin.getLogger().info("WALL_JUMP_DISABLED");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("WALL_JUMP_READY enabled=true players=all sneak=vanilla slideDelayTicks="
            + slideDelay + " jumpBoost=" + jumpBoost);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        states.clear();
        HandlerList.unregisterAll(this);
    }

    public String metrics() {
        int active = 0;
        for (State state : states.values()) if (state.clinging) active++;
        return "enabled=" + enabled + " active=" + active + " players=" + states.size()
            + " clings=" + clings + " jumps=" + jumps + " intentJumps=" + intentJumps
            + " facingJumps=" + facingJumps + " slideTicks=" + slideTicks + " ticks=" + ticks;
    }

    private void tick() {
        ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state == null) {
                state = new State();
                states.put(player.getUniqueId(), state);
            }
            tickPlayer(player, state);
        }
        for (Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, State> entry = it.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) it.remove();
        }
    }

    private void tickPlayer(Player player, State state) {
        boolean sneaking = player.isSneaking();
        EntityPlayer handle = handle(player);
        if (handle == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || player.isOnGround() || player.isFlying() || player.isInsideVehicle()
                || handle.isInWater()) {
            state.resetOnGround(sneaking);
            return;
        }

        if (sneaking) state.sneakTicks = state.sneakWasDown ? Math.min(4, state.sneakTicks + 1) : 1;
        else state.sneakTicks = 0;
        state.ageIntent();
        if (!state.clinging) {
            Vector motion = player.getVelocity();
            state.rememberIntent(motion.getX(), motion.getZ());
        }
        if (state.staleWalls != 0 && Double.isFinite(state.lastJumpY)
                && player.getLocation().getY() < state.lastJumpY - 1.0) {
            state.staleWalls = 0;
            state.lastJumpY = Double.NaN;
        }
        state.walls = detectWalls(player, state.clinging);

        if (!sneaking) {
            if (state.clinging && state.walls != 0) wallJump(player, state, state.walls);
            else state.releaseCling();
            state.sneakWasDown = false;
            return;
        }

        if (state.clinging) {
            if (state.walls == 0 || !canCling(player, state)) state.releaseCling();
            else applyCling(player, state);
        } else if (state.sneakTicks > 0 && state.sneakTicks <= 4 && state.walls != 0
                && canCling(player, state)) {
            state.clinging = true;
            state.clingTicks = 1;
            Location at = player.getLocation();
            state.clingX = at.getX();
            state.clingZ = at.getZ();
            clings++;
            applyCling(player, state);
        }
        state.sneakWasDown = true;
    }

    private boolean canCling(Player player, State state) {
        if (player.getFoodLevel() <= 0 || player.getVelocity().getY() > MAX_UPWARD_CLING_SPEED) return false;
        return WallJumpRules.canRecling(player.getLocation().getY(), state.lastJumpY,
            state.staleWalls, state.walls, ALLOW_RECLING);
    }

    private void applyCling(Player player, State state) {
        Vector velocity = player.getVelocity();
        double y = WallJumpRules.slideVelocity(velocity.getY(), state.clingTicks, slideDelay);
        if (y < 0.0) slideTicks++;
        player.setVelocity(new Vector(0.0, y, 0.0));
        player.setFallDistance(0.0f);
        state.clingTicks++;
    }

    private void wallJump(Player player, State state, int walls) {
        Location at = player.getLocation();
        boolean hasIntent = state.hasFreshIntent();
        double[] velocity = WallJumpRules.jumpVelocity(walls, jumpBoost, at.getYaw(),
            hasIntent ? state.intentX : 0.0, hasIntent ? state.intentZ : 0.0);
        player.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
        player.setFallDistance(0.0f);
        state.lastJumpY = at.getY();
        state.staleWalls = walls;
        state.releaseCling();
        state.sneakTicks = 0;
        state.sneakWasDown = false;
        jumps++;
        if (hasIntent) intentJumps++; else facingJumps++;
        plugin.getLogger().info("WALL_JUMP_FIRE player=" + player.getName() + " source="
            + (hasIntent ? "movement" : "facing") + " walls=" + walls + " velocity="
            + velocity[0] + "," + velocity[1] + "," + velocity[2]);
    }

    /**
     * The original mod probes a very thin column at eye height and offsets it
     * by width/2 + 0.06 (or +0.20 while clinging). This preserves detection of
     * low and irregular wall shapes without treating a whole nearby room as a
     * wall.
     */
    private int detectWalls(Player player, boolean clinging) {
        EntityPlayer handle = handle(player);
        if (handle == null) return 0;
        Location at = player.getLocation();
        World world = player.getWorld();
        if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return 0;
        double x = at.getX(), y = at.getY(), z = at.getZ();
        double height = Math.max(1.0, player.getEyeHeight());
        double gap = player.getWidth() / 2.0 + (clinging ? CLING_PROBE_GAP : FIRST_PROBE_GAP);
        AxisAlignedBB base = new AxisAlignedBB(x - WALL_EPSILON, y, z - WALL_EPSILON,
            x + WALL_EPSILON, y + height, z + WALL_EPSILON);
        int walls = 0;
        if (collides(handle, shift(base, 0.0, 0.0, gap))) walls |= WallJumpRules.SOUTH;
        if (collides(handle, shift(base, -gap, 0.0, 0.0))) walls |= WallJumpRules.WEST;
        if (collides(handle, shift(base, 0.0, 0.0, -gap))) walls |= WallJumpRules.NORTH;
        if (collides(handle, shift(base, gap, 0.0, 0.0))) walls |= WallJumpRules.EAST;
        return walls;
    }

    private static AxisAlignedBB shift(AxisAlignedBB box, double x, double y, double z) {
        return new AxisAlignedBB(box.a + x, box.b + y, box.c + z,
            box.d + x, box.e + y, box.f + z);
    }

    private static boolean collides(EntityPlayer player, AxisAlignedBB box) {
        return !player.world.getCubes(player, box).isEmpty();
    }

    private static EntityPlayer handle(Player player) {
        return player instanceof CraftPlayer ? ((CraftPlayer) player).getHandle() : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        if (event.isSneaking()) {
            state.sneakWasDown = false;
            state.sneakTicks = 0;
            return;
        }
        if (state.clinging && !player.isOnGround()) {
            int walls = detectWalls(player, true);
            if (walls != 0) wallJump(player, state, walls);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        State state = states.get(player.getUniqueId());
        if (state == null) {
            state = new State();
            states.put(player.getUniqueId(), state);
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() != from.getWorld()) return;
        if (!(event instanceof PlayerTeleportEvent)) state.rememberIntent(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (!state.clinging || state.clingX != state.clingX || state.clingZ != state.clingZ) return;
        if (Math.abs(to.getX() - state.clingX) < 1.0e-6 && Math.abs(to.getZ() - state.clingZ) < 1.0e-6) return;
        Location anchored = to.clone();
        anchored.setX(state.clingX);
        anchored.setZ(state.clingZ);
        event.setTo(anchored);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { states.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { states.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { states.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) { states.remove(event.getPlayer().getUniqueId()); }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
