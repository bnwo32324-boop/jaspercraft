package chat.jaspr.disasters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Natural disasters for JasperCraft.
 *
 * Exactly one disaster runs at a time, server-wide. That is the whole cost model: whatever is
 * happening, it is happening to one player, near one player, for a bounded number of seconds.
 * Each kind keeps its own 1-14 Minecraft day timer, and an operator can call one down on
 * themselves with /shower or /lightning.
 */
public final class DisasterPlugin extends JavaPlugin implements Listener {
    private static final long TICK_INTERVAL = 5L;

    /** The disasters this plugin knows how to run. One slot, shared between them. */
    private enum Kind {
        METEOR("meteor shower", "next-shower-epoch-ms"),
        STORM("thunder-hell storm", "next-storm-epoch-ms");

        final String label;
        final String stateKey;

        Kind(String label, String stateKey) {
            this.label = label;
            this.stateKey = stateKey;
        }
    }

    private final Random random = new Random();
    private final EnumMap<Kind, Long> nextAt = new EnumMap<Kind, Long>(Kind.class);

    private DisasterConfig settings;
    private Disaster active;
    private Kind activeKind;
    private BukkitTask loop;
    private long ticks;
    private long cooldownUntil;
    private long retryAfter;
    private File stateFile;

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = new DisasterConfig(getConfig());
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();
        getServer().getPluginManager().registerEvents(this, this);

        loop = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { pump(); }
        }, TICK_INTERVAL, TICK_INTERVAL);

        getLogger().info("JASPR_DISASTERS enabled"
                + " shower=" + settings.meteorMinDays + "-" + settings.meteorMaxDays + "d/" + minutesUntil(Kind.METEOR) + "m"
                + " storm=" + settings.stormMinDays + "-" + settings.stormMaxDays + "d/" + minutesUntil(Kind.STORM) + "m");
    }

    @Override public void onDisable() {
        if (loop != null) loop.cancel();
        // Cancelling matters here: a storm holds the world's weather and has to hand it back.
        if (active != null) active.cancel();
        active = null;
        activeKind = null;
        saveState();
    }

    // ---------------------------------------------------------------- scheduling

    private void loadState() {
        long now = System.currentTimeMillis();
        YamlConfiguration state = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        for (Kind kind : Kind.values()) {
            long stored = state.getLong(kind.stateKey, 0L);
            // Keep a stored schedule if it still looks sane, so a restart does not reset the cycle.
            long ceiling = now + maxDays(kind) * DisasterConfig.MILLIS_PER_MC_DAY;
            nextAt.put(kind, (stored > now && stored <= ceiling) ? stored : rollNext(kind, now));
        }
        saveState();
    }

    private void saveState() {
        if (stateFile == null) return;
        try {
            if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) return;
            YamlConfiguration state = new YamlConfiguration();
            for (Kind kind : Kind.values()) state.set(kind.stateKey, nextAt.containsKey(kind) ? nextAt.get(kind) : 0L);
            state.save(stateFile);
        } catch (IOException ignored) {
            // A lost schedule only means the next one is re-rolled on boot.
        }
    }

    private int minDays(Kind kind) { return kind == Kind.METEOR ? settings.meteorMinDays : settings.stormMinDays; }

    private int maxDays(Kind kind) { return kind == Kind.METEOR ? settings.meteorMaxDays : settings.stormMaxDays; }

    private boolean kindEnabled(Kind kind) { return kind == Kind.METEOR ? settings.meteorEnabled : settings.stormEnabled; }

    private long rollNext(Kind kind, long now) {
        int span = maxDays(kind) - minDays(kind);
        int days = minDays(kind) + (span <= 0 ? 0 : random.nextInt(span + 1));
        // Spread inside the chosen day too, so events do not land on exact day boundaries.
        long jitter = (long) (random.nextDouble() * DisasterConfig.MILLIS_PER_MC_DAY);
        return now + days * DisasterConfig.MILLIS_PER_MC_DAY + jitter;
    }

    private long minutesUntil(Kind kind) {
        Long when = nextAt.get(kind);
        if (when == null) return 0L;
        return Math.max(0L, (when - System.currentTimeMillis()) / 60000L);
    }

    // ---------------------------------------------------------------- main loop

    private void pump() {
        ticks += TICK_INTERVAL;

        if (active != null) {
            active.tick(ticks);
            if (active.isFinished()) {
                getLogger().info("JASPR_DISASTERS event=finished kind=" + activeKind.name().toLowerCase(Locale.ROOT)
                        + " target=" + active.targetName());
                Kind done = activeKind;
                active = null;
                activeKind = null;
                long now = System.currentTimeMillis();
                nextAt.put(done, rollNext(done, now));
                // A quiet gap, so one disaster ending never rolls straight into the next.
                cooldownUntil = now + settings.cooldownMillis;
                saveState();
            }
            return;
        }

        if (!settings.enabled) return;
        long now = System.currentTimeMillis();
        if (now < cooldownUntil || now < retryAfter) return;

        Kind due = pickDue(now);
        if (due == null) return;

        Player target = pickTarget(due);
        if (target == null) {
            // Nobody eligible right now; look again shortly rather than burning the cycle.
            retryAfter = now + 60000L;
            return;
        }
        begin(due, target, "scheduled");
    }

    /** Whichever kind is due. If both are, one is chosen at random and the other simply waits. */
    private Kind pickDue(long now) {
        List<Kind> due = new ArrayList<Kind>();
        for (Kind kind : Kind.values()) {
            if (!kindEnabled(kind)) continue;
            Long when = nextAt.get(kind);
            if (when != null && now >= when) due.add(kind);
        }
        if (due.isEmpty()) return null;
        return due.get(random.nextInt(due.size()));
    }

    private void begin(Kind kind, Player target, String reason) {
        active = kind == Kind.METEOR
                ? (Disaster) new MeteorShower(this, settings, random, target, ticks)
                : (Disaster) new ThunderHellStorm(settings, random, target, ticks);
        activeKind = kind;
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
        if (player.getWorld() == null) return false;
        boolean worldAllowed = kind == Kind.METEOR
                ? settings.allowsWorld(player.getWorld().getName())
                : settings.stormAllowsWorld(player.getWorld().getName());
        if (!worldAllowed) return false;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR) return false;
        boolean skipCreative = kind == Kind.METEOR ? settings.skipCreative : settings.stormSkipCreative;
        if (automatic && skipCreative && mode == GameMode.CREATIVE) return false;
        return true;
    }

    // ---------------------------------------------------------------- impacts

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeteorLanded(EntityChangeBlockEvent event) {
        if (!(active instanceof MeteorShower)) return;
        if (!(event.getEntity() instanceof FallingBlock)) return;
        if (!event.getEntity().hasMetadata(MeteorShower.METADATA_KEY)) return;
        MeteorShower shower = (MeteorShower) active;
        if (!shower.owns(event.getEntity().getUniqueId())) return;
        // Cancel so the falling block does not simply become a magma block; the strike replaces it.
        event.setCancelled(true);
        event.getEntity().remove();
        shower.onLanded(event.getEntity().getUniqueId(), event.getBlock().getLocation().add(0.5d, 0.5d, 0.5d));
    }

    // ---------------------------------------------------------------- commands

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Kind kind = "lightning".equalsIgnoreCase(command.getName()) ? Kind.STORM : Kind.METEOR;
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
        for (Kind kind : Kind.values()) {
            if (!kindEnabled(kind)) {
                sender.sendMessage(ChatColor.DARK_GRAY + "  " + kind.label + ": disabled");
                continue;
            }
            long minutes = minutesUntil(kind);
            sender.sendMessage(ChatColor.GRAY + "  Next " + kind.label + " in " + ChatColor.WHITE + minutes
                    + ChatColor.GRAY + " minutes (" + (minutes / 20) + " Minecraft days).");
        }
    }
}
