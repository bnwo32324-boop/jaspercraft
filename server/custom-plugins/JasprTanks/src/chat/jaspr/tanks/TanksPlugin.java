package chat.jaspr.tanks;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Mobile players get double health and a vehicle they can swap at any time:
 * <ul>
 * <li>Tank: 1-block auto-step, horse speed, plating, an infinite TNT cannon and up to four riders.</li>
 * <li>Orbital Sentinel: a flying drone with drone strikes; tap an item to pull it in (or auto-pickup), tap a
 * player to lock on and follow them.</li>
 * </ul>
 * The body is two invisible marker armor stands wearing the models (iron-axe bands 1-4). They stay
 * independent entities on the server and are mounted on the driver only in clients' eyes, so teleports keep
 * working and the model follows the player's own interpolated position with no lag. The weapon fires on the
 * swap-hands key (the touch FIRE / STRIKE button).
 */
public final class TanksPlugin extends JavaPlugin implements Listener {
    static final String TAG = "jaspr_tank", HULL_TAG = "jaspr_tank_hull", TURRET_TAG = "jaspr_tank_turret";
    static final String SHELL_TAG = "jaspr_tank_shell";
    static final short HULL_BAND = 1, TURRET_BAND = 2;
    /** Hidden scoreboard objective that tells the browser this server has tanks (reload, mode, pickup). */
    static final String OBJECTIVE = "jtk", DISPLAY = "JTK v1";
    /** Height at which clients seat a passenger on a standing player (1.8 * 0.75). */
    static final double RIDE_OFFSET = 1.35;
    /** Barrel tip of the turret model, relative to the driver's feet. */
    static final double MUZZLE_HEIGHT = 0.85, MUZZLE_REACH = 1.55;
    static final double PROXIMITY = 0.35;
    static final int MAX_SHELLS_PER_DRIVER = 9, MAX_SHELLS = 72;
    /** Riders sit on the hull stand; clients place them on the track guards. */
    static final int MAX_RIDERS = 4;
    static final float VANILLA_FLY_SPEED = 0.1f;
    private static final UUID ARMOR_ID = UUID.fromString("7a0f3a52-4d2e-4f53-9c8b-6e1d7a3b5c02");
    private static final UUID KNOCKBACK_ID = UUID.fromString("7a0f3a52-4d2e-4f53-9c8b-6e1d7a3b5c03");
    private static final UUID HEALTH_ID = UUID.fromString("7a0f3a52-4d2e-4f53-9c8b-6e1d7a3b5c04");

    private final Map<UUID, Tank> tanks = new HashMap<UUID, Tank>();
    private final Set<UUID> parts = new HashSet<UUID>();
    private final List<Shell> shells = new ArrayList<Shell>();
    private final Set<UUID> claimed = new HashSet<UUID>();
    private final Set<UUID> optedOut = new HashSet<UUID>();
    private final Set<UUID> sentinels = new HashSet<UUID>();
    private final Set<UUID> autoPickup = new HashSet<UUID>();
    private final Set<UUID> advertised = new HashSet<UUID>();
    private final Set<UUID> noticed = new HashSet<UUID>();
    private final Map<UUID, Long> fallSafe = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastTap = new HashMap<UUID, Long>();
    private final Map<UUID, Long> joinedAt = new HashMap<UUID, Long>();
    /** Mobile claims arrive a few seconds after joining; leftovers are only cleaned up after this grace. */
    static final long JOIN_GRACE_TICKS = 600;
    private Settings settings = new Settings();
    private Device device;
    private File playersFile;
    private long now;
    private long totalShots;
    /** The shell whose explosion is running right now (explosions are synchronous). */
    private Shell blasting;
    private Method authGetter, authCheck, reviveDowned;
    private boolean authMissing;
    private Plugin revive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        device = new Device(getClassLoader());
        playersFile = new File(getDataFolder(), "players.yml");
        loadPlayers();
        for (World world : getServer().getWorlds()) for (Entity entity : world.getEntities()) sweep(entity);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        for (Player player : getServer().getOnlinePlayers()) joined(player);
        getLogger().info("TANKS_READY enabled=" + settings.enabled + " cooldownTicks=" + settings.cooldownTicks
            + " power=" + settings.power + " range=" + settings.range + " breakBlocks=" + settings.breakBlocks
            + " sentinel=true healthBonus=" + settings.healthBonus);
    }

    @Override
    public void onDisable() {
        for (Tank tank : tanks.values()) {
            Player driver = getServer().getPlayer(tank.driver);
            removeParts(tank);
            if (driver != null) vehicleBuffs(driver, null);
        }
        tanks.clear();
        for (Shell shell : shells) shell.tnt.remove();
        shells.clear();
        // A reload would otherwise add the objective twice, which clients reject.
        for (Player player : getServer().getOnlinePlayers())
            if (advertised.contains(player.getUniqueId())) Nms.advertise(player, OBJECTIVE, DISPLAY, Collections.<String, Integer>emptyMap(), false);
        advertised.clear();
        savePlayers();
    }

    // -- enrolment -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { joined(event.getPlayer()); }

    private void joined(final Player player) {
        UUID id = player.getUniqueId();
        joinedAt.put(id, now);
        // A sentinel that logged out in the air keeps its flight until its vehicle is back (or the grace ends).
        if (Math.abs(player.getFlySpeed() - (float) settings.flySpeed) < 1.0E-4) fallSafe.put(id, now + JOIN_GRACE_TICKS + 200);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            advertise(player);
            if (settings.enabled && device.of(player) == Device.Kind.MOBILE && !optedOut.contains(id))
                enter(player, "mobile-browser");
        }, 20L);
        // Crash leftovers (walk/fly speed, flight, attribute modifiers are saved with the player): undo them only
        // for players who did not get a vehicle back.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !tanks.containsKey(id)) vehicleBuffs(player, null);
        }, JOIN_GRACE_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Tank tank = tanks.remove(id);
        if (tank != null) { removeParts(tank); vehicleBuffs(player, null); }
        for (Tank other : tanks.values()) if (id.equals(other.follow)) other.follow = null;
        claimed.remove(id);
        advertised.remove(id);
        noticed.remove(id);
        fallSafe.remove(id);
        lastTap.remove(id);
        joinedAt.remove(id);
    }

    private boolean mobile(Player player) {
        return claimed.contains(player.getUniqueId()) || device.of(player) == Device.Kind.MOBILE;
    }

    boolean enter(Player player, String reason) {
        if (!settings.enabled) return false;
        UUID id = player.getUniqueId();
        if (tanks.containsKey(id)) return true;
        Tank tank = new Tank(id, reason);
        tank.mode = sentinels.contains(id) ? Tank.Mode.SENTINEL : Tank.Mode.TANK;
        tank.autoPickup = autoPickup.contains(id);
        tanks.put(id, tank);
        vehicleBuffs(player, tank);
        if (mobile(player) && noticed.add(id)) {
            player.sendTitle(ChatColor.GREEN + "Mobile player detected", ChatColor.GRAY + "Tank or Orbital Sentinel · tap Mode to switch", 10, 80, 20);
            player.sendMessage(ChatColor.GREEN + "Mobile bonus: " + ChatColor.GRAY + "double health, plus your choice of vehicle. "
                + ChatColor.WHITE + "Tank" + ChatColor.GRAY + ": TNT cannon, climbs blocks, " + MAX_RIDERS + " friends can ride (right-click you). "
                + ChatColor.WHITE + "Orbital Sentinel" + ChatColor.GRAY + ": flies (Up/Down), drone strikes, tap an item to grab it, tap a player to follow them. "
                + "Tap Mode to switch any time; Tank hops out.");
        } else {
            player.sendMessage(ChatColor.GREEN + "You're in your " + tank.mode.label() + ". " + ChatColor.GRAY
                + (tank.mode == Tank.Mode.TANK ? "FIRE shoots TNT where you aim." : "STRIKE calls drones on where you aim.")
                + " Mode switches vehicles; Tank hops out.");
        }
        sendScores(player);
        getLogger().info("TANK_ENTER player=" + player.getName() + " reason=" + reason + " mode=" + tank.mode.name().toLowerCase(Locale.ROOT));
        return true;
    }

    void leave(Player player, String reason) {
        Tank tank = tanks.remove(player.getUniqueId());
        if (tank == null) return;
        removeParts(tank);
        if (tank.mode == Tank.Mode.SENTINEL) fallSafe.put(player.getUniqueId(), now + 200);
        vehicleBuffs(player, null);
        sendScores(player);
        player.sendMessage(ChatColor.GRAY + "You left your " + tank.mode.label() + ". Tap Tank (or /tank) to get back in.");
        getLogger().info("TANK_EXIT player=" + player.getName() + " reason=" + reason + " shots=" + tank.shots);
    }

    void setMode(Player player, Tank tank, Tank.Mode mode) {
        if (tank.mode == mode) return;
        UUID id = player.getUniqueId();
        if (mode == Tank.Mode.SENTINEL) {
            if (tank.hull != null && !tank.hull.getPassengers().isEmpty()) {
                for (Entity rider : tank.hull.getPassengers()) if (rider instanceof Player) bar((Player) rider, ChatColor.GRAY + "The driver launched an Orbital Sentinel");
                tank.hull.eject();
            }
            if (sentinels.add(id)) savePlayers();
        } else {
            tank.follow = null;
            fallSafe.put(id, now + 200);
            if (sentinels.remove(id)) savePlayers();
        }
        tank.mode = mode;
        tank.readyAt = Math.max(tank.readyAt, now + 10);
        tank.announced = false;
        vehicleBuffs(player, tank);
        if (tank.built()) { tank.hull.setHelmet(model(mode.bodyBand)); tank.turret.setHelmet(model(mode.topBand)); }
        if (mode == Tank.Mode.SENTINEL) player.setVelocity(new Vector(0, 0.6, 0));
        player.sendTitle("", ChatColor.GREEN + mode.label() + " online", 5, 30, 10);
        player.playSound(player.getLocation(), mode == Tank.Mode.SENTINEL ? Sound.ENTITY_FIREWORK_LAUNCH : Sound.BLOCK_PISTON_CONTRACT, 0.8f, 1.2f);
        sendScores(player);
        getLogger().info("TANK_MODE player=" + player.getName() + " mode=" + mode.name().toLowerCase(Locale.ROOT));
    }

    private boolean eligible(Player player) {
        if (claimed.contains(player.getUniqueId()) || settings.desktopPlayers || player.hasPermission("jaspr.tanks.desktop")) return true;
        Device.Kind kind = device.of(player);
        return kind == Device.Kind.MOBILE || kind == Device.Kind.APPLE_DESKTOP;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tanks: " + tanks.size() + " driving, " + shells.size() + " shells in flight, " + totalShots + " shots since start.");
            return true;
        }
        Player player = (Player) sender;
        UUID id = player.getUniqueId();
        Tank tank = tanks.get(id);
        switch (sub) {
            case "mobile":
                // Sent automatically by the touch controls once this server advertises tanks.
                if (device.of(player) == Device.Kind.DESKTOP && !settings.desktopPlayers && !player.hasPermission("jaspr.tanks.desktop")) {
                    getLogger().info("TANK_CLAIM_REFUSED player=" + player.getName() + " kind=desktop");
                    return true;
                }
                claimed.add(id);
                health(player, true);
                if (!optedOut.contains(id)) enter(player, "touch-controls");
                return true;
            case "on":
                on(player);
                return true;
            case "off":
                off(player);
                return true;
            case "toggle":
                if (tank != null) off(player); else on(player);
                return true;
            case "mode": {
                if (tank == null) { if (!on(player)) return true; tank = tanks.get(id); if (tank == null) return true; }
                Tank.Mode next = args.length > 1 ? Tank.Mode.parse(args[1])
                    : tank.mode == Tank.Mode.TANK ? Tank.Mode.SENTINEL : Tank.Mode.TANK;
                setMode(player, tank, next);
                return true;
            }
            case "pickup": {
                boolean auto = args.length > 1 ? "auto".equalsIgnoreCase(args[1]) || "on".equalsIgnoreCase(args[1]) : !autoPickup.contains(id);
                if (auto ? autoPickup.add(id) : autoPickup.remove(id)) savePlayers();
                if (tank != null) tank.autoPickup = auto;
                bar(player, ChatColor.GREEN + "Sentinel pickup: " + (auto ? "automatic" : "tap items"));
                sendScores(player);
                return true;
            }
            case "unlock":
                if (tank != null && tank.follow != null) { tank.follow = null; bar(player, ChatColor.GRAY + "Stopped following"); }
                return true;
            case "status":
                status(player);
                return true;
            default:
                player.sendMessage(ChatColor.GRAY + "/tank [on|off|mode tank|mode sentinel|pickup auto|pickup tap|unlock|status]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = args.length == 1 ? Arrays.asList("on", "off", "mode", "pickup", "unlock", "status")
            : args.length == 2 && "mode".equalsIgnoreCase(args[0]) ? Arrays.asList("tank", "sentinel")
            : args.length == 2 && "pickup".equalsIgnoreCase(args[0]) ? Arrays.asList("auto", "tap") : Collections.<String>emptyList();
        List<String> out = new ArrayList<String>();
        for (String option : options) if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(option);
        return out;
    }

    private boolean on(Player player) {
        if (!settings.enabled) { player.sendMessage(ChatColor.GRAY + "Tanks are switched off on this server."); return false; }
        if (!eligible(player)) {
            player.sendMessage(ChatColor.GRAY + "Tanks are for phone and tablet players.");
            return false;
        }
        if (optedOut.remove(player.getUniqueId())) savePlayers();
        return enter(player, "command");
    }

    private void off(Player player) {
        if (optedOut.add(player.getUniqueId())) savePlayers();
        leave(player, "command");
    }

    private void status(Player player) {
        Tank tank = tanks.get(player.getUniqueId());
        if (tank == null) player.sendMessage(ChatColor.GRAY + "You're on foot. " + (eligible(player) ? "/tank gets you in." : ""));
        else {
            Player target = tank.follow == null ? null : getServer().getPlayer(tank.follow);
            player.sendMessage(ChatColor.GREEN + tank.mode.label() + ": " + (now >= tank.readyAt ? "weapon ready" : "reloading")
                + ChatColor.GRAY + ", " + tank.shots + " shots this session"
                + (tank.mode == Tank.Mode.SENTINEL ? ", pickup " + (tank.autoPickup ? "automatic" : "by tap")
                    + (target != null ? ", following " + target.getName() : "") : "") + ".");
        }
        if (player.hasPermission("jaspr.tanks.admin"))
            player.sendMessage(ChatColor.GRAY + "Server: " + tanks.size() + " vehicles, " + shells.size() + " shells in flight, "
                + totalShots + " shots since start, block damage " + (settings.breakBlocks ? "on" : "off") + ".");
    }

    // -- the vehicle body --------------------------------------------------------------------------------

    private void tick() {
        now++;
        for (Iterator<Tank> it = tanks.values().iterator(); it.hasNext(); ) {
            Tank tank = it.next();
            Player driver = getServer().getPlayer(tank.driver);
            if (driver == null || !driver.isOnline()) { removeParts(tank); it.remove(); continue; }
            if (!shown(driver)) { if (tank.hull != null || tank.turret != null) removeParts(tank); continue; }
            if (!intact(tank.hull, driver) || !intact(tank.turret, driver)) build(tank, driver);
            if (!tank.built()) continue;
            Location at = anchor(driver);
            follow(tank.hull, at);
            follow(tank.turret, at);
            if (now >= tank.nextMount) { mount(tank, driver); tank.nextMount = now + 20; }
            if (!tank.announced && now >= tank.readyAt) {
                tank.announced = true;
                bar(driver, ChatColor.GREEN + (tank.mode == Tank.Mode.TANK ? "Cannon ready" : "Drones ready"));
                driver.playSound(driver.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.5f);
            }
            if (tank.mode == Tank.Mode.SENTINEL) {
                if (tank.follow != null && now % 2 == 0) guard(driver, tank);
                if (tank.autoPickup && now % 10 == 0) vacuum(driver);
            }
            if (now % 20 == 0) vehicleBuffs(driver, tank);
        }
        if (now % 20 == 0) for (Player player : getServer().getOnlinePlayers()) {
            // The bonus stays across rejoins (removing it would clamp a 40-point player to 20 each time) and is
            // only taken away from players who are clearly not on a phone.
            Long since = joinedAt.get(player.getUniqueId());
            if (mobile(player)) health(player, true);
            else if (since != null && now - since > JOIN_GRACE_TICKS) health(player, false);
        }
        if (now % 40 == 0) for (Player player : getServer().getOnlinePlayers()) advertise(player);
        tickShells();
    }

    private boolean shown(Player p) {
        return !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !p.isSleeping() && !p.isGliding()
            && !p.isInsideVehicle() && authenticated(p) && !downed(p);
    }

    private static boolean intact(ArmorStand stand, Player driver) {
        return stand != null && stand.isValid() && stand.getWorld().equals(driver.getWorld());
    }

    private static Location anchor(Player p) {
        Location l = p.getLocation();
        return new Location(l.getWorld(), l.getX(), l.getY() + RIDE_OFFSET, l.getZ(), 0f, 0f);
    }

    private static void follow(ArmorStand stand, Location at) {
        if (stand.getLocation().distanceSquared(at) > 1.0E-4) Nms.move(stand, at);
    }

    private void build(Tank tank, Player driver) {
        removeParts(tank);
        if (now < tank.retryAt) return; // back off after a refused spawn
        try {
            tank.hull = part(driver, HULL_TAG, tank.mode.bodyBand);
            tank.turret = part(driver, TURRET_TAG, tank.mode.topBand);
        } catch (RuntimeException e) {
            tank.hull = tank.turret = null;
        }
        if (!intact(tank.hull, driver) || !intact(tank.turret, driver)) {
            removeParts(tank);
            tank.retryAt = now + 100;
            getLogger().warning("TANK_BUILD_FAILED player=" + driver.getName() + " world=" + driver.getWorld().getName());
            return;
        }
        parts.add(tank.hull.getUniqueId());
        parts.add(tank.turret.getUniqueId());
        // Clients learn about the stands this tick; mount them just after.
        tank.nextMount = now + 2;
    }

    private ArmorStand part(Player driver, String tag, short band) {
        final ItemStack helmet = model(band);
        return driver.getWorld().spawn(anchor(driver), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.setHeadPose(EulerAngle.ZERO);
            stand.addScoreboardTag(TAG);
            stand.addScoreboardTag(tag);
            stand.setHelmet(helmet);
        });
    }

    static ItemStack model(short band) {
        ItemStack item = new ItemStack(Material.IRON_AXE, 1, band);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.values());
        meta.setDisplayName(band <= TURRET_BAND ? "Tank" : "Orbital Sentinel");
        item.setItemMeta(meta);
        return item;
    }

    private void removeParts(Tank tank) {
        for (ArmorStand stand : new ArmorStand[] {tank.hull, tank.turret}) {
            if (stand == null) continue;
            parts.remove(stand.getUniqueId());
            stand.eject();
            stand.remove();
        }
        tank.hull = tank.turret = null;
    }

    private void mount(Tank tank, Player driver) {
        int[] ids = {tank.hull.getEntityId(), tank.turret.getEntityId()};
        Location at = driver.getLocation();
        for (Player viewer : driver.getWorld().getPlayers()) {
            if (viewer != driver && (!viewer.canSee(driver) || viewer.getLocation().distanceSquared(at) > 72 * 72)) continue;
            Nms.mount(viewer, driver, ids);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Tank tank = tanks.get(event.getPlayer().getUniqueId());
        if (tank != null) tank.nextMount = Math.min(tank.nextMount, now + 3);
    }

    /** Removes tank entities left behind by a crash or reload before they can be picked up or tick. */
    private void sweep(Entity entity) {
        Set<String> tags = entity.getScoreboardTags();
        if (tags.contains(TAG) && !parts.contains(entity.getUniqueId())) { entity.eject(); entity.remove(); }
        else if (tags.contains(SHELL_TAG) && !liveShell(entity)) entity.remove();
    }

    private boolean liveShell(Entity entity) {
        for (Shell shell : shells) if (shell.tnt.equals(entity)) return true;
        return false;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) sweep(entity);
    }

    private static boolean isPart(Entity entity) { return entity.getScoreboardTags().contains(TAG); }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) { if (isPart(event.getRightClicked())) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) { if (isPart(event.getRightClicked())) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPartDamage(EntityDamageEvent event) { if (isPart(event.getEntity())) event.setCancelled(true); }

    @EventHandler
    public void onPartDeath(EntityDeathEvent event) {
        if (!isPart(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /** Applies the buffs of the vehicle's mode and removes the other mode's; {@code tank == null} removes all. */
    private void vehicleBuffs(Player player, Tank tank) {
        boolean vehicle = tank != null, driving = vehicle && tank.mode == Tank.Mode.TANK, flying = vehicle && tank.mode == Tank.Mode.SENTINEL;
        // Walk speed, not the speed attribute: the client widens the view with the attribute, not with walk speed.
        float walk = settings.walkSpeed();
        if (driving && player.getWalkSpeed() != walk) player.setWalkSpeed(walk);
        else if (!driving && Math.abs(player.getWalkSpeed() - walk) < 1.0E-4) player.setWalkSpeed(0.2f);
        Nms.stepHeight(player, driving ? (float) settings.stepHeight : Nms.VANILLA_STEP);
        modifier(player, Attribute.GENERIC_ARMOR, ARMOR_ID, "jaspr_tank_armor",
            vehicle ? settings.armor : 0, AttributeModifier.Operation.ADD_NUMBER);
        modifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "jaspr_tank_knockback",
            vehicle ? settings.knockbackResistance : 0, AttributeModifier.Operation.ADD_NUMBER);
        boolean survival = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        float fly = (float) settings.flySpeed;
        if (flying) {
            if (!player.getAllowFlight()) player.setAllowFlight(true);
            if (!player.isFlying()) player.setFlying(true);
            if (player.getFlySpeed() != fly) player.setFlySpeed(fly);
        } else if (Math.abs(player.getFlySpeed() - fly) < 1.0E-4) {
            // Only undo flight this plugin granted (our fly speed is the marker).
            player.setFlySpeed(VANILLA_FLY_SPEED);
            if (survival) { player.setFlying(false); player.setAllowFlight(false); }
        }
    }

    /** Mobile players get double health (config mobile.health-bonus, +20 by default). */
    private void health(Player player, boolean on) {
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max == null) return;
        boolean had = false;
        for (AttributeModifier modifier : max.getModifiers()) if (modifier.getUniqueId().equals(HEALTH_ID)) had = true;
        if (had == on && (!on || settings.healthBonus > 0)) return;
        modifier(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_ID, "jaspr_mobile_health", on ? settings.healthBonus : 0,
            AttributeModifier.Operation.ADD_NUMBER);
        if (!on && !player.isDead() && player.getHealth() > max.getValue()) player.setHealth(max.getValue());
    }

    private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = null;
        for (AttributeModifier modifier : instance.getModifiers()) if (modifier.getUniqueId().equals(id)) current = modifier;
        if (current != null && (amount == 0 || current.getAmount() != amount || current.getOperation() != operation)) {
            instance.removeModifier(current);
            current = null;
        }
        if (current == null && amount != 0) instance.addModifier(new AttributeModifier(id, name, amount, operation));
    }

    // -- riders ------------------------------------------------------------------------------------------

    /** Right-click (touch: Use) a tank driver to hop on; sneak hops off. Sentinels carry no riders. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoard(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player)) return;
        Player driver = (Player) event.getRightClicked(), rider = event.getPlayer();
        Tank tank = tanks.get(driver.getUniqueId());
        if (tank == null || tank.mode != Tank.Mode.TANK || !tank.built() || !shown(driver) || tanks.containsKey(rider.getUniqueId())) return;
        if (rider.isSneaking() || rider.isInsideVehicle() || rider.getGameMode() == GameMode.SPECTATOR || !authenticated(rider) || downed(rider)) return;
        if (!rider.getWorld().equals(driver.getWorld()) || rider.getLocation().distanceSquared(driver.getLocation()) > 25) return;
        event.setCancelled(true);
        if (tank.hull.getPassengers().size() >= MAX_RIDERS) { bar(rider, ChatColor.GRAY + "This tank is full (" + MAX_RIDERS + " riders)"); return; }
        if (!tank.hull.addPassenger(rider)) return;
        bar(rider, ChatColor.GREEN + "Riding " + driver.getName() + "'s tank" + ChatColor.GRAY + " · sneak to hop off");
        bar(driver, ChatColor.GREEN + rider.getName() + " hopped on (" + tank.hull.getPassengers().size() + "/" + MAX_RIDERS + ")");
        getLogger().info("TANK_RIDE player=" + rider.getName() + " driver=" + driver.getName() + " riders=" + tank.hull.getPassengers().size());
    }

    // -- orbital sentinel: taps, following, pickup ---------------------------------------------------------

    /** A tap (touch) or left click: lock onto the player under the crosshair, or pull in the item there. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTap(PlayerAnimationEvent event) {
        Player driver = event.getPlayer();
        Tank tank = tanks.get(driver.getUniqueId());
        if (tank == null || tank.mode != Tank.Mode.SENTINEL || !tank.built() || !shown(driver)) return;
        Long last = lastTap.get(driver.getUniqueId());
        if (last != null && now - last < 5) return; // held attack swings every few ticks
        lastTap.put(driver.getUniqueId(), now);
        World world = driver.getWorld();
        Location eye = driver.getEyeLocation();
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] look = Aim.direction(eye.getYaw(), eye.getPitch());
        double blockAt = solidHit(world, origin, look, settings.pickupRange + 24);
        double[] at = new double[1];
        Player friend = firstPlayer(world, origin, look, blockAt >= 0 ? blockAt : settings.pickupRange + 24, driver, at);
        if (friend != null) { toggleFollow(driver, tank, friend); return; }
        Item item = firstItem(world, origin, look, blockAt >= 0 ? blockAt + 1 : settings.pickupRange, driver);
        if (item != null) pull(driver, item, true);
    }

    private void toggleFollow(Player driver, Tank tank, Player friend) {
        if (friend.getUniqueId().equals(tank.follow)) {
            tank.follow = null;
            bar(driver, ChatColor.GRAY + "Stopped following " + friend.getName());
            return;
        }
        tank.follow = friend.getUniqueId();
        bar(driver, ChatColor.GREEN + "Following " + friend.getName() + ChatColor.GRAY + " · tap them again to stop");
        bar(friend, ChatColor.GREEN + driver.getName() + "'s Orbital Sentinel is guarding you");
        driver.playSound(driver.getLocation(), Sound.BLOCK_NOTE_PLING, 0.6f, 1.6f);
        getLogger().info("TANK_FOLLOW player=" + driver.getName() + " target=" + friend.getName());
    }

    /** Keeps a following sentinel above and just behind its friend: glides when close, jumps when far. */
    private void guard(Player driver, Tank tank) {
        Player friend = getServer().getPlayer(tank.follow);
        if (friend == null || !friend.isOnline() || friend.isDead() || friend.getGameMode() == GameMode.SPECTATOR
                || !friend.getWorld().equals(driver.getWorld())) {
            tank.follow = null;
            bar(driver, ChatColor.GRAY + "Lost your lock");
            return;
        }
        Location target = friend.getLocation();
        double yaw = Math.toRadians(target.getYaw());
        Location spot = target.clone().add(Math.sin(yaw) * settings.followDistance, settings.followHeight, -Math.cos(yaw) * settings.followDistance);
        Vector delta = spot.toVector().subtract(driver.getLocation().toVector());
        double distance = delta.length();
        if (distance > 64) { tank.follow = null; bar(driver, ChatColor.GRAY + "Lock lost: " + friend.getName() + " is too far"); return; }
        if (distance > 12) {
            Location jump = spot.clone();
            jump.setYaw(driver.getLocation().getYaw());
            jump.setPitch(driver.getLocation().getPitch());
            driver.teleport(jump, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }
        if (distance < 0.4) return;
        Vector push = delta.multiply(0.35);
        if (push.length() > 1.4) push.normalize().multiply(1.4);
        driver.setVelocity(push);
    }

    /** Auto-pickup: pulls nearby items in (never the driver's own drops, so throwing things away still works). */
    private void vacuum(Player driver) {
        double r = settings.autoPickupRadius;
        int pulled = 0;
        for (Entity entity : driver.getNearbyEntities(r, r, r)) {
            if (!(entity instanceof Item) || !entity.isValid()) continue;
            if (driver.getName().equals(Nms.thrower((Item) entity))) continue;
            if (entity.getLocation().distanceSquared(driver.getLocation()) < 2.25) continue;
            pull(driver, (Item) entity, false);
            if (++pulled >= 6) break;
        }
    }

    /** Tractor beam: the item jumps to the sentinel and vanilla pickup (and its rules) takes it from there. */
    private void pull(Player driver, Item item, boolean tapped) {
        Location from = item.getLocation(), to = driver.getLocation().add(0, 0.5, 0);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length > 0.01) {
            int points = (int) Math.min(30, Math.ceil(length / 0.8));
            step.multiply(1.0 / points);
            Location dot = from.clone();
            for (int i = 0; i < points; i++) { dot.add(step); driver.getWorld().spawnParticle(Particle.END_ROD, dot, 1, 0, 0, 0, 0); }
        }
        item.setVelocity(new Vector(0, 0, 0));
        item.teleport(to);
        if (tapped) driver.playSound(to, Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
    }

    private Player firstPlayer(World world, double[] o, double[] d, double max, Player self, double[] at) {
        double half = max / 2 + 3;
        Location middle = new Location(world, o[0] + d[0] * max / 2, o[1] + d[1] * max / 2, o[2] + d[2] * max / 2);
        Player best = null;
        double bestAt = max;
        for (Entity entity : world.getNearbyEntities(middle, half, half, half)) {
            if (!(entity instanceof Player) || entity.equals(self)) continue;
            Player player = (Player) entity;
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || !self.canSee(player)) continue;
            double[] box = Nms.box(player);
            if (box == null) continue;
            double t = Aim.ray(o[0], o[1], o[2], d[0], d[1], d[2], Aim.grow(box, 0.7), bestAt);
            if (t >= 0 && t <= bestAt) { best = player; bestAt = t; }
        }
        at[0] = bestAt;
        return best;
    }

    private Item firstItem(World world, double[] o, double[] d, double max, Player self) {
        double half = max / 2 + 2;
        Location middle = new Location(world, o[0] + d[0] * max / 2, o[1] + d[1] * max / 2, o[2] + d[2] * max / 2);
        Item best = null;
        double bestAt = max;
        for (Entity entity : world.getNearbyEntities(middle, half, half, half)) {
            if (!(entity instanceof Item) || !entity.isValid()) continue;
            double[] box = Nms.box(entity);
            if (box == null) continue;
            double t = Aim.ray(o[0], o[1], o[2], d[0], d[1], d[2], Aim.grow(box, 1.2), bestAt);
            if (t >= 0 && t <= bestAt) { best = (Item) entity; bestAt = t; }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSentinelHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Tank tank = tanks.get(event.getDamager().getUniqueId());
        // A sentinel taps players to lock on; it fights with drone strikes, never its fists.
        if (tank != null && tank.mode == Tank.Mode.SENTINEL) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player)) return;
        UUID id = event.getEntity().getUniqueId();
        Tank tank = tanks.get(id);
        Long safeUntil = fallSafe.get(id);
        if ((tank != null && tank.mode == Tank.Mode.SENTINEL) || (safeUntil != null && now < safeUntil)) event.setCancelled(true);
    }

    // -- server advertisement for the browser ----------------------------------------------------------

    private Map<String, Integer> scores(Player player) {
        Tank tank = tanks.get(player.getUniqueId());
        boolean sentinel = tank != null ? tank.mode == Tank.Mode.SENTINEL : sentinels.contains(player.getUniqueId());
        Map<String, Integer> scores = new LinkedHashMap<String, Integer>();
        scores.put("cooldown", settings.cooldownFor(sentinel ? Tank.Mode.SENTINEL : Tank.Mode.TANK));
        scores.put("mode", sentinel ? 1 : 0);
        scores.put("pickup", autoPickup.contains(player.getUniqueId()) ? 1 : 0);
        return scores;
    }

    /** Once per connection: the hidden objective survives scoreboard swaps and dimension changes client-side. */
    private void advertise(Player player) {
        if (!settings.enabled || !player.isOnline() || advertised.contains(player.getUniqueId())) return;
        if (Nms.advertise(player, OBJECTIVE, DISPLAY, scores(player), true)) advertised.add(player.getUniqueId());
    }

    private void sendScores(Player player) {
        if (advertised.contains(player.getUniqueId())) Nms.scores(player, OBJECTIVE, scores(player));
    }

    // -- weapons ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player driver = event.getPlayer();
        Tank tank = tanks.get(driver.getUniqueId());
        if (tank == null || !tank.built() || !shown(driver)) return;
        event.setCancelled(true);
        fire(driver, tank);
    }

    /** Whatever the crosshair is on: the first creature (with a forgiving margin) or solid block. */
    private LivingEntity aim(Player driver, double[] origin, double[] look, double[] target) {
        World world = driver.getWorld();
        double blockAt = solidHit(world, origin, look, settings.range);
        double reach = blockAt >= 0 ? blockAt : settings.range;
        double[] hitAt = new double[1];
        LivingEntity quarry = firstLiving(world, origin, look, reach, settings.aimAssist, driver.getUniqueId(), null, hitAt);
        double[] middle = quarry != null ? center(quarry) : null;
        if (middle != null) { System.arraycopy(middle, 0, target, 0, 3); return quarry; }
        double t = blockAt >= 0 ? Math.max(0, blockAt - 0.3) : settings.range;
        target[0] = origin[0] + look[0] * t; target[1] = origin[1] + look[1] * t; target[2] = origin[2] + look[2] * t;
        return null;
    }

    private void fire(Player driver, Tank tank) {
        if (now < tank.readyAt) {
            bar(driver, ChatColor.GRAY + "Reloading " + seconds((int) (tank.readyAt - now)));
            return;
        }
        int mine = 0;
        for (Shell shell : shells) if (shell.shooter.equals(driver.getUniqueId())) mine++;
        if (mine >= MAX_SHELLS_PER_DRIVER || shells.size() >= MAX_SHELLS) return;
        World world = driver.getWorld();
        Location eye = driver.getEyeLocation();
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] look = Aim.direction(eye.getYaw(), eye.getPitch());
        double[] target = new double[3];
        LivingEntity quarry = aim(driver, origin, look, target);
        tank.readyAt = now + settings.cooldownFor(tank.mode);
        tank.announced = false;
        tank.shots++;
        totalShots++;
        if (tank.mode == Tank.Mode.SENTINEL) { strike(driver, world, target, quarry); return; }

        // The shell leaves the barrel tip; hugging a wall fires from the hatch instead.
        Location feet = driver.getLocation();
        double[] flat = Aim.direction(eye.getYaw(), 0);
        double[] hatch = {feet.getX(), feet.getY() + MUZZLE_HEIGHT, feet.getZ()};
        double[] muzzle = {feet.getX() + flat[0] * MUZZLE_REACH, feet.getY() + MUZZLE_HEIGHT, feet.getZ() + flat[2] * MUZZLE_REACH};
        if (solidHit(world, hatch, flat, MUZZLE_REACH) >= 0) muzzle = origin.clone();
        Location from = new Location(world, muzzle[0], muzzle[1], muzzle[2]);
        double[] dir = launch(driver, world, muzzle, target, quarry, (float) settings.power);
        world.playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.7f);
        world.playSound(from, Sound.ENTITY_FIREWORK_LAUNCH, 1.0f, 0.6f);
        // Small puffs just past the barrel: a large blast particle here would white out the driver's view.
        Location flash = from.clone().add(dir[0] * 0.4, dir[1] * 0.4, dir[2] * 0.4);
        world.spawnParticle(Particle.EXPLOSION_NORMAL, flash, 4, 0.08, 0.08, 0.08, 0.03);
        world.spawnParticle(Particle.SMOKE_LARGE, flash, 3, 0.1, 0.1, 0.1, 0.01);
        world.spawnParticle(Particle.FLAME, flash, 3, 0.05, 0.05, 0.05, 0.02);
    }

    /** Drone strike: a ring of drones leaves the sentinel and dives onto the target (or chases the creature). */
    private void strike(Player driver, World world, double[] target, LivingEntity quarry) {
        Location feet = driver.getLocation();
        int drones = settings.strikeDrones;
        for (int i = 0; i < drones; i++) {
            double angle = 2 * Math.PI * i / drones, spread = quarry != null || drones == 1 ? 0 : 1.6;
            double[] start = {feet.getX() + Math.cos(angle) * 0.6, feet.getY() + 1.9 + 0.25 * i, feet.getZ() + Math.sin(angle) * 0.6};
            double[] aimAt = {target[0] + Math.cos(angle) * spread, target[1], target[2] + Math.sin(angle) * spread};
            launch(driver, world, start, aimAt, quarry, (float) settings.strikePower);
        }
        world.playSound(feet, Sound.ENTITY_FIREWORK_LAUNCH, 1.0f, 1.4f);
        world.playSound(feet, Sound.BLOCK_PISTON_EXTEND, 0.7f, 1.8f);
        world.spawnParticle(Particle.FIREWORKS_SPARK, feet.clone().add(0, 2, 0), 12, 0.3, 0.2, 0.3, 0.05);
    }

    /** Spawns one shell flying from {@code start} toward {@code target}; returns its direction. */
    private double[] launch(Player driver, World world, double[] start, double[] target, LivingEntity quarry, float power) {
        double[] path = {target[0] - start[0], target[1] - start[1], target[2] - start[2]};
        double distance = Math.sqrt(path[0] * path[0] + path[1] * path[1] + path[2] * path[2]);
        double[] dir = distance < 1.0E-6 ? new double[] {0, -1, 0} : new double[] {path[0] / distance, path[1] / distance, path[2] / distance};
        final double[] velocity = {dir[0] * settings.speed, dir[1] * settings.speed, dir[2] * settings.speed};
        final int fuse = settings.shellLifetime() + 20;
        final boolean fire = settings.fire;
        TNTPrimed tnt;
        try {
            tnt = world.spawn(new Location(world, start[0], start[1], start[2]), TNTPrimed.class, shell -> {
                shell.setFuseTicks(fuse);
                shell.setYield(power);
                shell.setIsIncendiary(fire);
                shell.setGravity(false);
                shell.addScoreboardTag(SHELL_TAG);
                shell.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
            });
        } catch (RuntimeException e) {
            return dir;
        }
        Nms.source(tnt, driver);
        Shell shell = new Shell(tnt, driver.getUniqueId(), dir, target, distance, quarry, power);
        shells.add(shell);
        // Point-blank: nothing to fly through.
        if (distance <= settings.speed && quarry == null) { detonate(shell, target); shells.remove(shell); }
        return dir;
    }

    private void tickShells() {
        for (Iterator<Shell> it = shells.iterator(); it.hasNext(); ) {
            Shell shell = it.next();
            if (!shell.tnt.isValid()) { it.remove(); continue; }
            shell.age++;
            World world = shell.tnt.getWorld();
            Location at = shell.tnt.getLocation();
            double[] pos = {at.getX(), at.getY(), at.getZ()};
            double speed = settings.speed;
            if (shell.quarry != null) {
                double[] c = shell.quarry.isValid() && !shell.quarry.isDead() && shell.quarry.getWorld().equals(world) ? center(shell.quarry) : null;
                if (c == null) shell.quarry = null;
                else {
                    double[] want = {c[0] - pos[0], c[1] - pos[1], c[2] - pos[2]};
                    double len = Math.sqrt(want[0] * want[0] + want[1] * want[1] + want[2] * want[2]);
                    if (len > 1.0E-6) {
                        want[0] /= len; want[1] /= len; want[2] /= len;
                        shell.dir = Aim.steer(shell.dir, want, settings.homingDegrees);
                    }
                    shell.left = Math.max(len, speed);
                }
            }
            double step = Math.min(speed, shell.left);
            double[] d = shell.dir;
            double blockAt = solidHit(world, pos, d, step);
            double[] hitAt = new double[1];
            LivingEntity hit = firstLiving(world, pos, d, step, PROXIMITY, shell.shooter, shell.quarry, hitAt);
            double stop = Double.POSITIVE_INFINITY;
            if (blockAt >= 0) stop = Math.max(0, blockAt - 0.3);
            if (hit != null) stop = Math.min(stop, hitAt[0]);
            if (stop != Double.POSITIVE_INFINITY) {
                detonate(shell, new double[] {pos[0] + d[0] * stop, pos[1] + d[1] * stop, pos[2] + d[2] * stop});
                it.remove();
                continue;
            }
            if (shell.left <= speed) {
                detonate(shell, shell.quarry == null ? shell.target : new double[] {pos[0] + d[0] * step, pos[1] + d[1] * step, pos[2] + d[2] * step});
                it.remove();
                continue;
            }
            if (shell.age > settings.shellLifetime()) {
                detonate(shell, pos);
                it.remove();
                continue;
            }
            shell.tnt.setVelocity(new Vector(d[0] * speed, d[1] * speed, d[2] * speed));
            shell.left -= speed;
            world.spawnParticle(Particle.SMOKE_NORMAL, at, 3, 0.05, 0.05, 0.05, 0.01);
            world.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0.005);
        }
    }

    private void detonate(Shell shell, double[] at) {
        Location where = new Location(shell.tnt.getWorld(), at[0], at[1], at[2]);
        blasting = shell;
        try {
            if (!Nms.explode(shell.tnt, where, shell.power, settings.fire))
                where.getWorld().createExplosion(at[0], at[1], at[2], shell.power, settings.fire, settings.breakBlocks);
        } finally {
            blasting = null;
            shell.tnt.remove();
        }
    }

    /** Distance along the unit ray to the first solid block, or -1. Never loads chunks. */
    static double solidHit(World world, double[] o, double[] d, double max) {
        if (max <= 0) return -1;
        BlockIterator blocks;
        try {
            blocks = new BlockIterator(world, new Vector(o[0], o[1], o[2]), new Vector(d[0], d[1], d[2]), 0, (int) Math.ceil(max) + 1);
        } catch (RuntimeException e) {
            return -1;
        }
        try {
            while (blocks.hasNext()) {
                Block block = blocks.next();
                if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    double t = Aim.ray(o[0], o[1], o[2], d[0], d[1], d[2], cube(block), max);
                    return t >= 0 ? t : -1;
                }
                if (!block.getType().isSolid()) continue;
                double t = Aim.ray(o[0], o[1], o[2], d[0], d[1], d[2], cube(block), max);
                if (t >= 0) return t;
            }
        } catch (RuntimeException ignored) { }
        return -1;
    }

    private static double[] cube(Block block) {
        return new double[] {block.getX(), block.getY(), block.getZ(), block.getX() + 1, block.getY() + 1, block.getZ() + 1};
    }

    /** The nearest creature whose (grown) box the segment enters, with its distance in {@code at[0]}. */
    private LivingEntity firstLiving(World world, double[] o, double[] d, double max, double margin, UUID shooter,
                                     LivingEntity quarry, double[] at) {
        double half = max / 2 + margin + 2;
        Location middle = new Location(world, o[0] + d[0] * max / 2, o[1] + d[1] * max / 2, o[2] + d[2] * max / 2);
        Player owner = getServer().getPlayer(shooter);
        LivingEntity best = null;
        double bestAt = max;
        for (Entity entity : world.getNearbyEntities(middle, half, half, half)) {
            if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity.isDead()) continue;
            if (entity.getUniqueId().equals(shooter) || friendly(entity, shooter)) continue;
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (player.getGameMode() == GameMode.SPECTATOR || (owner != null && !owner.canSee(player))) continue;
            }
            double[] box = Nms.box(entity);
            if (box == null) continue;
            double t = Aim.ray(o[0], o[1], o[2], d[0], d[1], d[2], Aim.grow(box, entity == quarry ? margin + 0.3 : margin), bestAt);
            if (t >= 0 && t <= bestAt) { best = (LivingEntity) entity; bestAt = t; }
        }
        at[0] = bestAt;
        return best;
    }

    /** The driver's own pets, the friends riding their tank, and the player their sentinel guards. */
    private boolean friendly(Entity entity, UUID shooter) {
        Tank tank = tanks.get(shooter);
        if (tank != null && entity.getUniqueId().equals(tank.follow)) return true;
        Entity vehicle = entity.getVehicle();
        if (tank != null && vehicle != null && (vehicle.equals(tank.hull) || vehicle.equals(tank.turret))) return true;
        if (!(entity instanceof Tameable)) return false;
        AnimalTamer owner = ((Tameable) entity).getOwner();
        return owner != null && shooter.equals(owner.getUniqueId());
    }

    private static double[] center(LivingEntity entity) {
        double[] box = Nms.box(entity);
        if (box == null) return null;
        return new double[] {(box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2};
    }

    private Shell shellOf(Entity entity) {
        if (blasting != null && blasting.tnt.equals(entity)) return blasting;
        for (Shell shell : shells) if (shell.tnt.equals(entity)) return shell;
        return null;
    }

    // -- blast rules: the driver, their friends and (unless break-blocks) builds are never hurt -----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getScoreboardTags().contains(SHELL_TAG)) return;
        if (!settings.breakBlocks) { event.blockList().clear(); event.setYield(0); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShellDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof TNTPrimed) || !damager.getScoreboardTags().contains(SHELL_TAG)) return;
        Shell shell = shellOf(damager);
        Entity victim = event.getEntity();
        if (shell != null && (victim.getUniqueId().equals(shell.shooter) || friendly(victim, shell.shooter))) { event.setCancelled(true); return; }
        if (!(victim instanceof LivingEntity) || victim instanceof ArmorStand) { if (!settings.breakBlocks) event.setCancelled(true); return; }
        if (victim instanceof Player && (!settings.damagePlayers || !victim.getWorld().getPVP())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (blasting != null && !settings.breakBlocks) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (blasting != null && !settings.breakBlocks) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (blasting != null && !settings.breakBlocks) event.setCancelled(true);
    }

    // -- integrations (optional, reflective) ------------------------------------------------------------

    private boolean authenticated(Player player) {
        if (authMissing) return true;
        try {
            if (authGetter == null) {
                Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, getClassLoader());
                authGetter = api.getMethod("getInstance");
                authCheck = api.getMethod("isAuthenticated", Player.class);
            }
            Object instance = authGetter.invoke(null);
            return instance != null && Boolean.TRUE.equals(authCheck.invoke(instance, player));
        } catch (ClassNotFoundException e) {
            authMissing = getServer().getPluginManager().getPlugin("AuthMe") == null;
            return authMissing;
        } catch (Exception e) {
            return false;
        }
    }

    /** JasprRevive's bleeding-out state: the revive presentation owns the player, so the vehicle steps aside. */
    private boolean downed(Player player) {
        try {
            Plugin plugin = getServer().getPluginManager().getPlugin("JasprRevive");
            if (plugin == null || !plugin.isEnabled()) return false;
            if (plugin != revive) {
                revive = plugin;
                reviveDowned = plugin.getClass().getDeclaredMethod("isDowned", Player.class);
                reviveDowned.setAccessible(true);
            }
            return Boolean.TRUE.equals(reviveDowned.invoke(plugin, player));
        } catch (Exception e) {
            return false;
        }
    }

    // -- small helpers ------------------------------------------------------------------------------------

    private static void bar(Player player, String text) {
        try { player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text)); } catch (Throwable ignored) { }
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    private void loadPlayers() {
        optedOut.clear();
        sentinels.clear();
        autoPickup.clear();
        if (!playersFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        readIds(yaml.getStringList("opted-out"), optedOut);
        readIds(yaml.getStringList("sentinel"), sentinels);
        readIds(yaml.getStringList("auto-pickup"), autoPickup);
    }

    private static void readIds(List<String> values, Set<UUID> into) {
        for (String value : values) {
            try { into.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private static List<String> ids(Set<UUID> set) {
        List<String> values = new ArrayList<String>();
        for (UUID id : set) values.add(id.toString());
        Collections.sort(values);
        return values;
    }

    private void savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("opted-out", ids(optedOut));
        yaml.set("sentinel", ids(sentinels));
        yaml.set("auto-pickup", ids(autoPickup));
        try {
            getDataFolder().mkdirs();
            yaml.save(playersFile);
        } catch (IOException e) {
            getLogger().warning("TANK_SAVE_FAILED " + e.getClass().getSimpleName());
        }
    }
}
