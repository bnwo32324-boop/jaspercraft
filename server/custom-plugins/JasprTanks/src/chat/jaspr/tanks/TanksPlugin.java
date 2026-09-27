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
 * Touch-screen players drive a miniature tank: 1-block auto-step, tank plating and an infinite TNT cannon
 * on the swap-hands key (the touch FIRE button). The tank body is two invisible marker armor stands wearing
 * the hull and turret models (iron-axe bands 1 and 2). They stay independent entities on the server and are
 * mounted on the driver only in clients' eyes, so teleports keep working and the model follows the
 * player's own interpolated position with no lag.
 */
public final class TanksPlugin extends JavaPlugin implements Listener {
    static final String TAG = "jaspr_tank", HULL_TAG = "jaspr_tank_hull", TURRET_TAG = "jaspr_tank_turret";
    static final String SHELL_TAG = "jaspr_tank_shell";
    static final short HULL_BAND = 1, TURRET_BAND = 2;
    /** Hidden scoreboard objective that tells the browser this server has tanks (and the reload time). */
    static final String OBJECTIVE = "jtk", DISPLAY = "JTK v1";
    /** Height at which clients seat a passenger on a standing player (1.8 * 0.75). */
    static final double RIDE_OFFSET = 1.35;
    /** Barrel tip of the turret model, relative to the driver's feet. */
    static final double MUZZLE_HEIGHT = 0.85, MUZZLE_REACH = 1.55;
    static final double PROXIMITY = 0.35;
    static final int MAX_SHELLS_PER_DRIVER = 6, MAX_SHELLS = 64;
    /** Riders sit on the hull stand; clients place them on the rear deck and the track guards. */
    static final int MAX_RIDERS = 4;
    private static final UUID ARMOR_ID = UUID.fromString("7a0f3a52-4d2e-4f53-9c8b-6e1d7a3b5c02");
    private static final UUID KNOCKBACK_ID = UUID.fromString("7a0f3a52-4d2e-4f53-9c8b-6e1d7a3b5c03");

    private final Map<UUID, Tank> tanks = new HashMap<UUID, Tank>();
    private final Set<UUID> parts = new HashSet<UUID>();
    private final List<Shell> shells = new ArrayList<Shell>();
    private final Set<UUID> claimed = new HashSet<UUID>();
    private final Set<UUID> optedOut = new HashSet<UUID>();
    private final Set<UUID> advertised = new HashSet<UUID>();
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
            + " power=" + settings.power + " range=" + settings.range + " breakBlocks=" + settings.breakBlocks);
    }

    @Override
    public void onDisable() {
        for (Tank tank : tanks.values()) {
            Player driver = getServer().getPlayer(tank.driver);
            removeParts(tank);
            if (driver != null) buffs(driver, false);
        }
        tanks.clear();
        for (Shell shell : shells) shell.tnt.remove();
        shells.clear();
        // A reload would otherwise add the objective twice, which clients reject.
        for (Player player : getServer().getOnlinePlayers())
            if (advertised.contains(player.getUniqueId())) Nms.advertise(player, OBJECTIVE, DISPLAY, 0, false);
        advertised.clear();
        savePlayers();
    }

    // -- enrolment -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { joined(event.getPlayer()); }

    private void joined(final Player player) {
        // Crash leftovers: attribute modifiers are saved with the player.
        buffs(player, false);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            advertise(player);
            if (settings.enabled && device.of(player) == Device.Kind.MOBILE && !optedOut.contains(player.getUniqueId()))
                enter(player, "mobile-browser");
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Tank tank = tanks.remove(player.getUniqueId());
        if (tank != null) { removeParts(tank); buffs(player, false); }
        claimed.remove(player.getUniqueId());
        advertised.remove(player.getUniqueId());
    }

    boolean enter(Player player, String reason) {
        if (!settings.enabled) return false;
        if (tanks.containsKey(player.getUniqueId())) return true;
        tanks.put(player.getUniqueId(), new Tank(player.getUniqueId(), reason));
        buffs(player, true);
        player.sendMessage(ChatColor.GREEN + "You're driving a tank! " + ChatColor.GRAY + "FIRE shoots TNT where you aim ("
            + seconds(settings.cooldownTicks) + " reload). Up to " + MAX_RIDERS + " friends can hop on with right-click (Use). The Tank button hops out.");
        getLogger().info("TANK_ENTER player=" + player.getName() + " reason=" + reason);
        return true;
    }

    void leave(Player player, String reason) {
        Tank tank = tanks.remove(player.getUniqueId());
        if (tank == null) return;
        removeParts(tank);
        buffs(player, false);
        player.sendMessage(ChatColor.GRAY + "You hopped out of your tank. Tap Tank (or /tank) to get back in.");
        getLogger().info("TANK_EXIT player=" + player.getName() + " reason=" + reason + " shots=" + tank.shots);
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
        switch (sub) {
            case "mobile":
                // Sent automatically by the touch controls once this server advertises tanks.
                if (device.of(player) == Device.Kind.DESKTOP && !settings.desktopPlayers && !player.hasPermission("jaspr.tanks.desktop")) {
                    getLogger().info("TANK_CLAIM_REFUSED player=" + player.getName() + " kind=desktop");
                    return true;
                }
                claimed.add(id);
                if (!optedOut.contains(id)) enter(player, "touch-controls");
                return true;
            case "on":
                return on(player);
            case "off":
                off(player);
                return true;
            case "toggle":
                if (tanks.containsKey(id)) off(player); else on(player);
                return true;
            case "status":
                status(player);
                return true;
            default:
                player.sendMessage(ChatColor.GRAY + "/tank [on|off|status]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        for (String option : Arrays.asList("on", "off", "status"))
            if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
        return out;
    }

    private boolean on(Player player) {
        if (!settings.enabled) { player.sendMessage(ChatColor.GRAY + "Tanks are switched off on this server."); return true; }
        if (!eligible(player)) {
            player.sendMessage(ChatColor.GRAY + "Tanks are for phone and tablet players.");
            return true;
        }
        if (optedOut.remove(player.getUniqueId())) savePlayers();
        enter(player, "command");
        return true;
    }

    private void off(Player player) {
        if (optedOut.add(player.getUniqueId())) savePlayers();
        leave(player, "command");
    }

    private void status(Player player) {
        Tank tank = tanks.get(player.getUniqueId());
        if (tank == null) player.sendMessage(ChatColor.GRAY + "You're on foot. " + (eligible(player) ? "/tank gets you in." : ""));
        else player.sendMessage(ChatColor.GREEN + "Tank: " + (now >= tank.readyAt ? "cannon ready" : "reloading")
            + ChatColor.GRAY + ", " + tank.shots + " shots this session.");
        if (player.hasPermission("jaspr.tanks.admin"))
            player.sendMessage(ChatColor.GRAY + "Server: " + tanks.size() + " tanks, " + shells.size() + " shells in flight, "
                + totalShots + " shots since start, block damage " + (settings.breakBlocks ? "on" : "off") + ".");
    }

    // -- the tank body ---------------------------------------------------------------------------------

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
                bar(driver, ChatColor.GREEN + "Cannon ready");
                driver.playSound(driver.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.5f);
            }
            if (now % 20 == 0) buffs(driver, true);
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
            tank.hull = part(driver, HULL_TAG, HULL_BAND);
            tank.turret = part(driver, TURRET_TAG, TURRET_BAND);
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
        meta.setDisplayName(band == HULL_BAND ? "Tank hull" : "Tank turret");
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

    private void buffs(Player player, boolean on) {
        // Walk speed, not the speed attribute: the client widens the view with the attribute, not with walk speed.
        float walk = settings.walkSpeed();
        if (on && player.getWalkSpeed() != walk) player.setWalkSpeed(walk);
        else if (!on && Math.abs(player.getWalkSpeed() - walk) < 1.0E-4) player.setWalkSpeed(0.2f);
        modifier(player, Attribute.GENERIC_ARMOR, ARMOR_ID, "jaspr_tank_armor",
            on ? settings.armor : 0, AttributeModifier.Operation.ADD_NUMBER);
        modifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "jaspr_tank_knockback",
            on ? settings.knockbackResistance : 0, AttributeModifier.Operation.ADD_NUMBER);
        Nms.stepHeight(player, on ? (float) settings.stepHeight : Nms.VANILLA_STEP);
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

    /** Right-click (touch: Use) a tank driver to hop on; sneak hops off. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoard(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player)) return;
        Player driver = (Player) event.getRightClicked(), rider = event.getPlayer();
        Tank tank = tanks.get(driver.getUniqueId());
        if (tank == null || !tank.built() || !shown(driver) || tanks.containsKey(rider.getUniqueId())) return;
        if (rider.isSneaking() || rider.isInsideVehicle() || rider.getGameMode() == GameMode.SPECTATOR || !authenticated(rider) || downed(rider)) return;
        if (!rider.getWorld().equals(driver.getWorld()) || rider.getLocation().distanceSquared(driver.getLocation()) > 25) return;
        event.setCancelled(true);
        if (tank.hull.getPassengers().size() >= MAX_RIDERS) { bar(rider, ChatColor.GRAY + "This tank is full (" + MAX_RIDERS + " riders)"); return; }
        if (!tank.hull.addPassenger(rider)) return;
        bar(rider, ChatColor.GREEN + "Riding " + driver.getName() + "'s tank" + ChatColor.GRAY + " · sneak to hop off");
        bar(driver, ChatColor.GREEN + rider.getName() + " hopped on (" + tank.hull.getPassengers().size() + "/" + MAX_RIDERS + ")");
        getLogger().info("TANK_RIDE player=" + rider.getName() + " driver=" + driver.getName() + " riders=" + tank.hull.getPassengers().size());
    }

    // -- server advertisement for the browser ----------------------------------------------------------

    /** Once per connection: the hidden objective survives scoreboard swaps and dimension changes client-side. */
    private void advertise(Player player) {
        if (!settings.enabled || !player.isOnline() || advertised.contains(player.getUniqueId())) return;
        if (Nms.advertise(player, OBJECTIVE, DISPLAY, settings.cooldownTicks, true)) advertised.add(player.getUniqueId());
    }

    // -- cannon ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player driver = event.getPlayer();
        Tank tank = tanks.get(driver.getUniqueId());
        if (tank == null || !tank.built() || !shown(driver)) return;
        event.setCancelled(true);
        fire(driver, tank);
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

        // Whatever the crosshair is on: the first creature (with a forgiving margin) or solid block.
        double blockAt = solidHit(world, origin, look, settings.range);
        double reach = blockAt >= 0 ? blockAt : settings.range;
        double[] hitAt = new double[1];
        LivingEntity quarry = firstLiving(world, origin, look, reach, settings.aimAssist, driver.getUniqueId(), null, hitAt);
        double[] target = quarry != null ? center(quarry) : null;
        if (target == null) {
            quarry = null;
            double t = blockAt >= 0 ? Math.max(0, blockAt - 0.3) : settings.range;
            target = new double[] {origin[0] + look[0] * t, origin[1] + look[1] * t, origin[2] + look[2] * t};
        }

        // The shell leaves the barrel tip; hugging a wall fires from the hatch instead.
        Location feet = driver.getLocation();
        double[] flat = Aim.direction(eye.getYaw(), 0);
        double[] hatch = {feet.getX(), feet.getY() + MUZZLE_HEIGHT, feet.getZ()};
        double[] muzzle = {feet.getX() + flat[0] * MUZZLE_REACH, feet.getY() + MUZZLE_HEIGHT, feet.getZ() + flat[2] * MUZZLE_REACH};
        if (solidHit(world, hatch, flat, MUZZLE_REACH) >= 0) muzzle = origin.clone();
        double[] path = {target[0] - muzzle[0], target[1] - muzzle[1], target[2] - muzzle[2]};
        double distance = Math.sqrt(path[0] * path[0] + path[1] * path[1] + path[2] * path[2]);
        double[] dir = distance < 1.0E-6 ? look.clone() : new double[] {path[0] / distance, path[1] / distance, path[2] / distance};

        tank.readyAt = now + settings.cooldownTicks;
        tank.announced = false;
        tank.shots++;
        totalShots++;
        final Location from = new Location(world, muzzle[0], muzzle[1], muzzle[2]);
        world.playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.7f);
        world.playSound(from, Sound.ENTITY_FIREWORK_LAUNCH, 1.0f, 0.6f);
        // Small puffs just past the barrel: a large blast particle here would white out the driver's view.
        Location flash = from.clone().add(dir[0] * 0.4, dir[1] * 0.4, dir[2] * 0.4);
        world.spawnParticle(Particle.EXPLOSION_NORMAL, flash, 4, 0.08, 0.08, 0.08, 0.03);
        world.spawnParticle(Particle.SMOKE_LARGE, flash, 3, 0.1, 0.1, 0.1, 0.01);
        world.spawnParticle(Particle.FLAME, flash, 3, 0.05, 0.05, 0.05, 0.02);

        final double[] velocity = {dir[0] * settings.speed, dir[1] * settings.speed, dir[2] * settings.speed};
        final int fuse = settings.shellLifetime() + 20;
        TNTPrimed tnt;
        try {
            tnt = world.spawn(from, TNTPrimed.class, shell -> {
                shell.setFuseTicks(fuse);
                shell.setYield((float) settings.power);
                shell.setIsIncendiary(settings.fire);
                shell.setGravity(false);
                shell.addScoreboardTag(SHELL_TAG);
                shell.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
            });
        } catch (RuntimeException e) {
            return;
        }
        Nms.source(tnt, driver);
        Shell shell = new Shell(tnt, driver.getUniqueId(), dir, target, distance, quarry);
        shells.add(shell);
        // Point-blank: nothing to fly through.
        if (distance <= settings.speed && quarry == null) { detonate(shell, target); shells.remove(shell); }
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
            if (!Nms.explode(shell.tnt, where, (float) settings.power, settings.fire))
                where.getWorld().createExplosion(at[0], at[1], at[2], (float) settings.power, settings.fire, settings.breakBlocks);
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

    /** The driver's own pets and the friends riding their tank. */
    private boolean friendly(Entity entity, UUID shooter) {
        Tank tank = tanks.get(shooter);
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

    // -- blast rules: the driver, their pets and (unless break-blocks) builds are never hurt -------------

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

    /** JasprRevive's bleeding-out state: the revive presentation owns the player, so the tank steps aside. */
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
        if (!playersFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        for (String value : yaml.getStringList("opted-out")) {
            try { optedOut.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> values = new ArrayList<String>();
        for (UUID id : optedOut) values.add(id.toString());
        Collections.sort(values);
        yaml.set("opted-out", values);
        try {
            getDataFolder().mkdirs();
            yaml.save(playersFile);
        } catch (IOException e) {
            getLogger().warning("TANK_SAVE_FAILED " + e.getClass().getSimpleName());
        }
    }
}
