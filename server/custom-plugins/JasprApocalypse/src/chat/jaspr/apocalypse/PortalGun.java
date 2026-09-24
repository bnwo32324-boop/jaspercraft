package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * A linked-teleporter tool. Right-click fires the cyan portal, sneak + right-click
 * the amber one; when both exist they are bound, and ANY player who walks into one
 * is carried out of the other. Portals live on the server, are drawn for everyone,
 * and are shared, so other people travel through portals you opened. This is an
 * original device -- not a reproduction of any existing game's art or branding.
 *
 * <p>Creative-only for now: the item is issued solely from the creative catalogue.
 */
final class PortalGun implements Listener {
    static final String ID = "portal_gun";

    private static final double RANGE = 64.0;      // how far a shot reaches for a surface
    private static final double STEP = 0.25;       // ray-march resolution
    private static final double TRIGGER_RADIUS = 0.8;   // how close to a portal centre counts as entering
    private static final double PLANE_DEPTH = 0.7;      // depth of the trigger slab along the portal normal
    private static final long COOLDOWN_MS = 900;   // per-player re-entry lock after a jump
    private static final double EXIT_OFFSET = 1.25;// how far in front of the exit the traveller appears
    private static final int[] CYAN = {85, 255, 255};
    private static final int[] AMBER = {255, 150, 0};

    private final ApocalypsePlugin plugin;
    private final Map<UUID, Pair> pairs = new HashMap<UUID, Pair>();
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private BukkitTask task;
    private int frame;

    PortalGun(ApocalypsePlugin plugin) { this.plugin = plugin; }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("PORTAL_GUN_READY item=" + ID);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        pairs.clear();
        cooldown.clear();
    }

    String metrics() {
        int open = 0;
        for (Pair p : pairs.values()) { if (p.cyan != null) open++; if (p.amber != null) open++; }
        return "portalOwners=" + pairs.size() + ",openPortals=" + open;
    }

    // -- Firing --------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!ID.equals(ApocalypseItems.id(held))) return;
        // The carrier is a tool; stop vanilla tilling/placement from the right-click.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.authenticated(player)) return;
        fire(player, player.isSneaking());
    }

    private void fire(Player player, boolean amber) {
        World world = player.getWorld();
        Hit hit = rayTrace(player);
        if (hit == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_HAT, 0.6f, 0.6f);
            actionBar(player, ChatColor.GRAY + "No surface in range.");
            return;
        }
        Portal portal = new Portal(world.getName(), hit.air.getX(), hit.air.getY(), hit.air.getZ(), hit.face, amber);
        Pair pair = pairs.get(player.getUniqueId());
        if (pair == null) { pair = new Pair(); pairs.put(player.getUniqueId(), pair); }
        if (amber) pair.amber = portal; else pair.cyan = portal;

        int[] c = amber ? AMBER : CYAN;
        Location centre = portal.centre(world);
        world.playSound(centre, Sound.ENTITY_ENDERMEN_TELEPORT, 0.8f, amber ? 0.8f : 1.3f);
        burst(world, centre, c);
        boolean linked = pair.cyan != null && pair.amber != null;
        actionBar(player, (amber ? ChatColor.GOLD + "Amber portal set." : ChatColor.AQUA + "Cyan portal set.")
                + (linked ? ChatColor.GRAY + "  Portals linked." : ChatColor.DARK_GRAY + "  Set the other to link."));
    }

    /** March from the eye until a solid surface; returns the air cell in front of it and the facing. */
    private Hit rayTrace(Player player) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        Vector dir = eye.getDirection().normalize();
        Block prev = eye.getBlock();
        int steps = (int) (RANGE / STEP);
        for (int i = 1; i <= steps; i++) {
            Location at = eye.clone().add(dir.getX() * i * STEP, dir.getY() * i * STEP, dir.getZ() * i * STEP);
            Block b = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (b.getX() == prev.getX() && b.getY() == prev.getY() && b.getZ() == prev.getZ()) continue;
            if (isSurface(b)) {
                BlockFace face = faceBetween(b, prev);
                Block air = b.getRelative(face);
                if (!isOpen(air)) return null; // no room for the portal mouth
                return new Hit(air, face);
            }
            prev = b;
        }
        return null;
    }

    private static boolean isSurface(Block b) {
        Material m = b.getType();
        return m.isSolid() && m != Material.PORTAL && m != Material.ENDER_PORTAL && !b.isLiquid();
    }
    private static boolean isOpen(Block b) {
        Material m = b.getType();
        return !m.isSolid() || m == Material.SNOW || m == Material.LONG_GRASS;
    }
    /** BlockFace pointing from the solid block toward the adjacent air cell. */
    private static BlockFace faceBetween(Block solid, Block air) {
        int dx = air.getX() - solid.getX(), dy = air.getY() - solid.getY(), dz = air.getZ() - solid.getZ();
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        if (Math.abs(dy) >= Math.abs(dz)) return dy >= 0 ? BlockFace.UP : BlockFace.DOWN;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    // -- Per-tick: draw portals and carry travellers -------------------------
    private void tick() {
        frame++;
        boolean draw = (frame % 3) == 0;
        long now = System.currentTimeMillis();
        List<Active> active = new ArrayList<Active>();
        for (Map.Entry<UUID, Pair> e : pairs.entrySet()) {
            Pair pair = e.getValue();
            if (pair.cyan != null) active.add(new Active(pair.cyan, pair.amber));
            if (pair.amber != null) active.add(new Active(pair.amber, pair.cyan));
        }
        if (active.isEmpty()) return;

        if (draw) for (Active a : active) render(a.self);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Long until = cooldown.get(player.getUniqueId());
            if (until != null && now < until) continue;
            Location feet = player.getLocation();
            Vector body = new Vector(feet.getX(), feet.getY() + 1.0, feet.getZ()); // player mid-height
            for (Active a : active) {
                if (a.exit == null) continue;                    // unlinked mouth: decorative only
                World world = Bukkit.getWorld(a.self.world);
                if (world == null || !world.getName().equals(feet.getWorld().getName())) continue;
                if (!inside(a.self, world, body)) continue;
                travel(player, a.exit, world);
                cooldown.put(player.getUniqueId(), now + COOLDOWN_MS);
                break;
            }
        }
    }

    private boolean inside(Portal p, World world, Vector body) {
        Vector centre = new Vector(p.x + 0.5, p.y + 0.5, p.z + 0.5);
        Vector n = normal(p.face);
        Vector rel = body.clone().subtract(centre);
        double along = rel.dot(n);
        if (Math.abs(along) > PLANE_DEPTH) return false;
        Vector planar = rel.clone().subtract(n.clone().multiply(along));
        return planar.lengthSquared() <= TRIGGER_RADIUS * TRIGGER_RADIUS;
    }

    private void travel(Player player, Portal exit, World fromWorld) {
        World world = Bukkit.getWorld(exit.world);
        if (world == null) return;
        Vector n = normal(exit.face);
        Location dest = new Location(world, exit.x + 0.5 + n.getX() * EXIT_OFFSET,
                exit.y + 0.5 + n.getY() * EXIT_OFFSET - 0.9, // drop to foot level when stepping out onto a floor
                exit.z + 0.5 + n.getZ() * EXIT_OFFSET);
        // Face out of the exit when it is on a wall; keep the player's own look for floor/ceiling mouths.
        if (n.getY() == 0) {
            dest.setYaw(yaw(n));
            dest.setPitch(0f);
        } else {
            dest.setYaw(player.getLocation().getYaw());
            dest.setPitch(n.getY() > 0 ? -20f : 20f);
        }
        double speed = Math.max(0.55, player.getVelocity().length());
        Location entryCentre = new Location(fromWorld, 0, 0, 0);
        player.teleport(dest);
        player.setVelocity(n.clone().multiply(speed));
        player.playSound(dest, Sound.ENTITY_ENDERMEN_TELEPORT, 0.9f, 1.0f);
        burst(world, dest.clone().add(0, 0.9, 0), exit.amber ? AMBER : CYAN);
    }

    // -- Visuals -------------------------------------------------------------
    private void render(Portal p) {
        World world = Bukkit.getWorld(p.world);
        if (world == null) return;
        int[] c = p.amber ? AMBER : CYAN;
        Location centre = p.centre(world);
        Vector n = normal(p.face);
        Vector u = basis(n);
        Vector v = n.clone().crossProduct(u).normalize();
        double outer = 0.72;
        for (int i = 0; i < 20; i++) {
            double t = (Math.PI * 2 * i) / 20.0;
            double cx = Math.cos(t) * outer, cy = Math.sin(t) * outer * 1.15; // slightly oval
            Location pt = centre.clone().add(u.getX() * cx + v.getX() * cy,
                    u.getY() * cx + v.getY() * cy, u.getZ() * cx + v.getZ() * cy);
            dust(world, pt, c);
        }
        if ((frame % 12) == 0) world.spawnParticle(Particle.PORTAL, centre, 6, 0.25, 0.25, 0.25, 0.4);
    }

    private static void dust(World world, Location at, int[] c) {
        try {
            world.spawnParticle(Particle.REDSTONE, at, 0, c[0] / 255.0, c[1] / 255.0, c[2] / 255.0, 1.0);
        } catch (Throwable ignored) { }
    }
    private static void burst(World world, Location at, int[] c) {
        for (int i = 0; i < 24; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * 0.7;
            dust(world, at.clone().add(Math.cos(a) * r, (Math.random() - 0.5) * 1.2, Math.sin(a) * r), c);
        }
    }

    // -- Geometry helpers ----------------------------------------------------
    private static Vector normal(BlockFace f) { return new Vector(f.getModX(), f.getModY(), f.getModZ()); }
    /** A unit vector in the portal plane; horizontal where possible so wall rings stand upright. */
    private static Vector basis(Vector n) {
        if (n.getY() != 0) return new Vector(1, 0, 0);          // floor/ceiling: any horizontal axis
        return new Vector(-n.getZ(), 0, n.getX()).normalize();  // wall: the horizontal tangent
    }
    private static float yaw(Vector n) {
        return (float) (Math.toDegrees(Math.atan2(-n.getX(), n.getZ())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cooldown.remove(event.getPlayer().getUniqueId()); }

    // -- Data ----------------------------------------------------------------
    private static final class Portal {
        final String world; final int x, y, z; final BlockFace face; final boolean amber;
        Portal(String world, int x, int y, int z, BlockFace face, boolean amber) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.face = face; this.amber = amber;
        }
        Location centre(World w) { return new Location(w, x + 0.5, y + 0.5, z + 0.5); }
    }
    private static final class Pair { Portal cyan, amber; }
    private static final class Active { final Portal self, exit; Active(Portal self, Portal exit) { this.self = self; this.exit = exit; } }
    private static final class Hit { final Block air; final BlockFace face; Hit(Block air, BlockFace face) { this.air = air; this.face = face; } }

    private void actionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) { player.sendMessage(message); }
    }
}
