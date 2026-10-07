package chat.jaspr.disasters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Natural disasters for JasperCraft.
 *
 * Exactly one disaster runs at a time, server-wide. That is the whole cost model: whatever is
 * happening, it is happening to one player, near one player, for a bounded number of seconds.
 * All kinds share one timer (see Schedule): after each disaster the next is due a few Minecraft
 * days later and its kind is drawn at random. An operator can call any kind down on themselves
 * with /shower, /lightning, /quake, /tornado or /blizzard.
 */
public final class DisasterPlugin extends JavaPlugin implements Listener {
    private static final long TICK_INTERVAL = 5L;
    private static final String NEXT_KEY = "next-disaster-epoch-ms";
    private static final String LAST_KEY = "last-kind";

    /** The disasters this plugin knows how to run. One slot, shared between them. */
    private enum Kind {
        METEOR("meteor shower", "shower"),
        STORM("thunder-hell storm", "lightning"),
        QUAKE("earthquake", "quake"),
        TORNADO("tornado", "tornado"),
        BLIZZARD("blizzard", "blizzard");

        final String label;
        final String command;

        Kind(String label, String command) {
            this.label = label;
            this.command = command;
        }

        static Kind byCommand(String name) {
            for (Kind kind : values()) if (kind.command.equalsIgnoreCase(name)) return kind;
            return METEOR;
        }

        static Kind byName(String name) {
            for (Kind kind : values()) if (kind.name().equalsIgnoreCase(name)) return kind;
            return null;
        }
    }

    private final Random random = new Random();

    private DisasterConfig settings;
    private Disaster active;
    private Kind activeKind;
    private BukkitTask loop;
    private long ticks;
    private long nextAt;
    private Kind lastKind;
    private long cooldownUntil;
    private long retryAfter;
    private File stateFile;
    /** Where and until when disaster fire is guarded: the active disaster's world, and three minutes after it. */
    private String guardWorld;
    private long guardUntil;
    private static final long FIRE_GUARD_MILLIS = 180000L;

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = new DisasterConfig(getConfig());
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ObsidianProtection(), this);

        loop = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { pump(); }
        }, TICK_INTERVAL, TICK_INTERVAL);

        getLogger().info("JASPR_DISASTERS enabled schedule=" + settings.scheduleMinDays + "-" + settings.scheduleMaxDays
                + "d mean=" + Schedule.meanDays(settings.scheduleMinDays, settings.scheduleMaxDays) + "d next=" + minutesUntilNext() + "m"
                + " kinds=" + enabledKinds().toString().toLowerCase(Locale.ROOT)
                + " bunkerRule=quake,tornado,blizzard buildDamageWorlds=" + settings.buildDamageWorlds);
        getLogger().info("OBSIDIAN_PROTECTION_READY mobs=true explosions=true disasterTerrain=true");
    }

    @Override public void onDisable() {
        if (loop != null) loop.cancel();
        // Cancelling matters here: storms hold the world's weather and a blizzard has snow to thaw.
        if (active != null) active.cancel();
        active = null;
        activeKind = null;
        saveState();
    }

    // ---------------------------------------------------------------- scheduling

    private void loadState() {
        long now = System.currentTimeMillis();
        YamlConfiguration state = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        long stored = state.getLong(NEXT_KEY, 0L);
        // Keep a stored schedule if it still looks sane, so a restart does not reset the cycle. A state file
        // from before the shared timer has none: the first one is rolled fresh, at the new, rarer rate.
        long ceiling = now + (settings.scheduleMaxDays + 1L) * DisasterConfig.MILLIS_PER_MC_DAY;
        nextAt = (stored > now && stored <= ceiling) ? stored : Schedule.next(now, settings.scheduleMinDays, settings.scheduleMaxDays, random);
        lastKind = Kind.byName(state.getString(LAST_KEY, ""));
        saveState();
    }

    private void saveState() {
        if (stateFile == null) return;
        try {
            if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) return;
            YamlConfiguration state = new YamlConfiguration();
            state.set(NEXT_KEY, nextAt);
            state.set(LAST_KEY, lastKind == null ? "" : lastKind.name());
            state.save(stateFile);
        } catch (IOException ignored) {
            // A lost schedule only means the next one is re-rolled on boot.
        }
    }

    private boolean kindEnabled(Kind kind) {
        switch (kind) {
            case METEOR: return settings.meteorEnabled;
            case STORM: return settings.stormEnabled;
            case QUAKE: return settings.quakeEnabled;
            case TORNADO: return settings.tornadoEnabled;
            default: return settings.blizzardEnabled;
        }
    }

    private List<Kind> enabledKinds() {
        List<Kind> kinds = new ArrayList<Kind>();
        for (Kind kind : Kind.values()) if (kindEnabled(kind)) kinds.add(kind);
        return kinds;
    }

    private long minutesUntilNext() { return Math.max(0L, (nextAt - System.currentTimeMillis()) / 60000L); }

    /**
     * What the Field Journal (JasprJournal, called through reflection) may tell every player: "active:<kind>" while a disaster
     * runs, "brewing" when the next one is due within a Minecraft day, else "quiet". The exact time stays secret (the status
     * command is for administrators), and nothing is changed.
     */
    public String journalState() {
        Disaster running = active;
        Kind kind = activeKind;
        if (running != null && kind != null) return "active:" + kind.label;
        return nextAt - System.currentTimeMillis() <= DisasterConfig.MILLIS_PER_MC_DAY ? "brewing" : "quiet";
    }

    // ---------------------------------------------------------------- main loop

    private void pump() {
        ticks += TICK_INTERVAL;

        if (active != null) {
            active.tick(ticks);
            if (active.isFinished()) {
                getLogger().info("JASPR_DISASTERS event=finished kind=" + activeKind.name().toLowerCase(Locale.ROOT)
                        + " target=" + active.targetName());
                lastKind = activeKind;
                active = null;
                activeKind = null;
                long now = System.currentTimeMillis();
                guardUntil = now + FIRE_GUARD_MILLIS;
                nextAt = Schedule.next(now, settings.scheduleMinDays, settings.scheduleMaxDays, random);
                // A quiet gap, so one disaster ending never rolls straight into the next.
                cooldownUntil = now + settings.cooldownMillis;
                saveState();
            }
            return;
        }

        if (!settings.enabled) return;
        long now = System.currentTimeMillis();
        if (now < cooldownUntil || now < retryAfter || now < nextAt) return;

        // Due: the kinds in random order (never the last one first); the first with an eligible player runs.
        for (Kind kind : Schedule.order(enabledKinds(), lastKind, random)) {
            Player target = pickTarget(kind);
            if (target == null) continue;
            begin(kind, target, "scheduled");
            return;
        }
        // Nobody eligible for anything right now; look again shortly rather than burning the cycle.
        retryAfter = now + 60000L;
    }

    private void begin(Kind kind, Player target, String reason) {
        switch (kind) {
            case METEOR: active = new MeteorShower(this, settings, random, target, ticks); break;
            case STORM: active = new ThunderHellStorm(settings, random, target, ticks); break;
            case QUAKE: active = new Earthquake(this, settings, random, target, ticks); break;
            case TORNADO: active = new Tornado(this, settings, random, target, ticks); break;
            default: active = new Blizzard(settings, random, target, ticks); break;
        }
        activeKind = kind;
        guardWorld = target.getWorld().getName();
        guardUntil = Long.MAX_VALUE;
        getLogger().info("JASPR_DISASTERS event=started kind=" + kind.name().toLowerCase(Locale.ROOT)
                + " target=" + target.getName() + " world=" + target.getWorld().getName() + " reason=" + reason);
    }

    /** One player, chosen at random from those this disaster can meaningfully reach. */
    private Player pickTarget(Kind kind) {
        List<Player> eligible = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEligible(player, kind, true)) continue;
            eligible.add(player);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
    }

    private boolean isEligible(Player player, Kind kind, boolean automatic) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        World world = player.getWorld();
        if (world == null) return false;
        String name = world.getName();
        boolean worldAllowed;
        boolean skipCreative;
        switch (kind) {
            case METEOR: worldAllowed = settings.allowsWorld(name); skipCreative = settings.skipCreative; break;
            case STORM: worldAllowed = settings.stormAllowsWorld(name); skipCreative = settings.stormSkipCreative; break;
            case QUAKE: worldAllowed = DisasterConfig.allows(settings.quakeWorlds, name); skipCreative = settings.quakeSkipCreative; break;
            case TORNADO: worldAllowed = DisasterConfig.allows(settings.tornadoWorlds, name); skipCreative = settings.tornadoSkipCreative; break;
            default: worldAllowed = DisasterConfig.allows(settings.blizzardWorlds, name); skipCreative = settings.blizzardSkipCreative; break;
        }
        if (!worldAllowed) return false;
        // Tornadoes and blizzards are weather: only in worlds with an overworld sky. Within them they find you
        // anywhere, deep in a mine or up in the sky (owner 2026-10-02: only an obsidian bunker is safe).
        if ((kind == Kind.TORNADO || kind == Kind.BLIZZARD) && world.getEnvironment() != World.Environment.NORMAL) return false;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR) return false;
        if (automatic && skipCreative && mode == GameMode.CREATIVE) return false;
        return true;
    }

    // ---------------------------------------------------------------- impacts

    /** Meteors and quake rocks are falling blocks that must never just turn into blocks where they land. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorLanded(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) return;
        if (active instanceof MeteorShower && event.getEntity().hasMetadata(MeteorShower.METADATA_KEY)) {
            MeteorShower shower = (MeteorShower) active;
            if (!shower.owns(event.getEntity().getUniqueId())) return;
            // Cancel so the falling block does not simply become a magma block; the strike replaces it.
            event.setCancelled(true);
            event.getEntity().remove();
            shower.onLanded(event.getEntity().getUniqueId(), event.getBlock().getLocation().add(0.5d, 0.5d, 0.5d));
        } else if (active instanceof Earthquake && event.getEntity().hasMetadata(Earthquake.ROCK_KEY)) {
            Earthquake quake = (Earthquake) active;
            if (!quake.owns(event.getEntity().getUniqueId())) return;
            // A falling rock bursts on landing instead of leaving a block behind.
            event.setCancelled(true);
            event.getEntity().remove();
            quake.onRockLanded(event.getEntity().getUniqueId(), event.getBlock().getLocation().add(0.5d, 0.5d, 0.5d));
        } else if (active instanceof Tornado && event.getEntity().hasMetadata(Tornado.DEBRIS_KEY)) {
            Tornado tornado = (Tornado) active;
            if (!tornado.owns(event.getEntity().getUniqueId())) return;
            // Debris bursts where it comes down instead of landing on a roof, a chest or a portal.
            event.setCancelled(true);
            event.getEntity().remove();
            tornado.onDebrisLanded(event.getEntity().getUniqueId(), event.getBlock().getLocation().add(0.5d, 0.5d, 0.5d));
        }
    }

    // ---------------------------------------------------------------- what disasters never harm
    //
    // Obsidian, utility blocks (chests, furnaces, anvils, beds, crafting and enchanting tables, bookshelves, hoppers,
    // dispensers - a sentry turret's body - and the rest of Impacts.PROTECTED), the block holding any of them up, and
    // every portal with its frame. ObsidianProtection keeps obsidian safe from everything; these keep the rest safe
    // from disasters.

    /** A disaster explosion loses everything off limits before any other plugin (sentry turrets, realm gates) sees it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void spareFromDisasterBlast(BlockExplodeEvent event) {
        if (!Impacts.inDisasterExplosion()) return;
        int spared = Impacts.spare(event.blockList());
        if (spared > 0) getLogger().fine("JASPR_DISASTERS spared=" + spared);
    }

    private boolean guarded(World world) {
        return world != null && guardWorld != null && world.getName().equals(guardWorld) && System.currentTimeMillis() < guardUntil;
    }

    /** Disaster fire (from a blast, a bolt, fissure lava, or spreading from them) is never lit beside anything protected. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void guardIgnition(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        boolean ours = (cause == BlockIgniteEvent.IgniteCause.EXPLOSION && Impacts.inDisasterExplosion())
                || (cause == BlockIgniteEvent.IgniteCause.LIGHTNING && active instanceof ThunderHellStorm)
                || ((cause == BlockIgniteEvent.IgniteCause.SPREAD || cause == BlockIgniteEvent.IgniteCause.LAVA
                    || cause == BlockIgniteEvent.IgniteCause.LIGHTNING) && guarded(event.getBlock().getWorld()));
        if (ours && !Impacts.mayIgnite(event.getBlock())) event.setCancelled(true);
    }

    /** While a disaster's fires can still be burning, protected blocks and portal frames never burn away. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void guardBurning(BlockBurnEvent event) {
        if (!guarded(event.getBlock().getWorld())) return;
        if (Impacts.offLimits(event.getBlock())) event.setCancelled(true);
    }

    /** Item frames and paintings come through a disaster blast. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void spareHangings(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && Impacts.inDisasterExplosion()) event.setCancelled(true);
    }

    /** Armor stands (sentry turrets' stands among them) take no harm from a disaster. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void spareStands(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;
        if (Impacts.inDisasterExplosion()) { event.setCancelled(true); return; }
        if (!guarded(event.getEntity().getWorld())) return;
        switch (event.getCause()) {
            case LIGHTNING: case FIRE: case FIRE_TICK: case LAVA: case FALLING_BLOCK: case BLOCK_EXPLOSION:
                event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    /** Minecarts and boats (storage carts among them) come through a disaster blast. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void spareVehicles(VehicleDamageEvent event) {
        if (Impacts.inDisasterExplosion()) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- commands

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Kind kind = Kind.byCommand(command.getName());
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if ("status".equals(action)) {
            sendStatus(sender);
            return true;
        }

        if ("reload".equals(action)) {
            reloadConfig();
            // A disaster already running keeps the settings it started with, which are immutable.
            settings = new DisasterConfig(getConfig());
            getLogger().info("JASPR_DISASTERS event=config_reloaded by=" + sender.getName());
            sender.sendMessage(ChatColor.GREEN + "Disaster settings reloaded from config.yml.");
            return true;
        }

        if ("stop".equals(action)) {
            if (active == null) {
                sender.sendMessage(ChatColor.GRAY + "Nothing to stop.");
            } else {
                String stopped = activeKind.label;
                active.cancel();
                active = null;
                activeKind = null;
                cooldownUntil = System.currentTimeMillis() + settings.cooldownMillis;
                guardUntil = System.currentTimeMillis() + FIRE_GUARD_MILLIS;
                sender.sendMessage(ChatColor.YELLOW + "Stopped the " + stopped + ".");
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Run this in game to call a " + kind.label + " down on yourself.");
            return true;
        }
        Player player = (Player) sender;

        if (active != null) {
            sender.sendMessage(ChatColor.RED + "A " + activeKind.label + " is already running on "
                    + ChatColor.WHITE + active.targetName() + ChatColor.RED + ". Only one disaster runs at a time.");
            return true;
        }
        if (!isEligible(player, kind, false)) {
            sender.sendMessage(ChatColor.RED + "You cannot be targeted here right now.");
            return true;
        }
        begin(kind, player, "command");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        if (active != null) {
            sender.sendMessage(ChatColor.GOLD + "A " + activeKind.label + " is running on "
                    + ChatColor.WHITE + active.targetName() + ChatColor.GOLD + ".");
        } else {
            sender.sendMessage(ChatColor.GRAY + "No disaster running.");
        }
        long minutes = minutesUntilNext();
        sender.sendMessage(ChatColor.GRAY + "  Next disaster in " + ChatColor.WHITE + minutes + ChatColor.GRAY + " minutes ("
                + (minutes / 20) + " Minecraft days); one every " + settings.scheduleMinDays + "-" + settings.scheduleMaxDays + " days.");
        for (Kind kind : Kind.values()) {
            sender.sendMessage((kindEnabled(kind) ? ChatColor.GRAY : ChatColor.DARK_GRAY) + "  /" + kind.command + "  " + kind.label
                    + (kindEnabled(kind) ? "" : ": disabled") + (kind == lastKind ? " (last)" : ""));
        }
    }
}
