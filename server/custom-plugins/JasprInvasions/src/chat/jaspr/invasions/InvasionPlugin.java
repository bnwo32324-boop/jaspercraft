package chat.jaspr.invasions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Invasions for JasperCraft.
 *
 * Invasion nights are shared - the whole world gets one every few days - but who is invaded and
 * what comes for them is entirely personal. A player's difficulty is their own playtime on this
 * server, so on the same night a veteran can be fighting armoured, enchanted raiders at one base
 * while someone a week newer fights half a dozen bare zombies at another, and someone who joined
 * this morning is left alone until they have played enough to be worth hunting.
 */
public final class InvasionPlugin extends JavaPlugin {
    private static final long TICK_INTERVAL = 10L;
    private static final long ACCRUE_INTERVAL = 100L;

    private final Random random = new Random();
    private final Map<UUID, ActiveInvasion> active = new LinkedHashMap<UUID, ActiveInvasion>();
    private final Map<UUID, Long> joinedAt = new HashMap<UUID, Long>();

    private InvasionConfig settings;
    private ProgressStore store;
    private BlockRestorer restorer;
    private InvaderFactory factory;
    private InvaderBrain brain;
    private BukkitTask loop;
    private long ticks;
    private long lastAccrueTick;
    private long lastSaveTick;

    @Override public void onEnable() {
        saveDefaultConfig();
        rebuild();

        store = new ProgressStore(new File(getDataFolder(), "players.yml"));
        store.load();

        getServer().getPluginManager().registerEvents(new InvasionListener(this), this);
        sweepStrays("startup");

        loop = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { pump(); }
        }, TICK_INTERVAL, TICK_INTERVAL);

        getLogger().info("JASPR_INVASIONS enabled every=" + settings.invadeEveryDays + "d"
                + " firstDay=" + settings.firstInvasionDay
                + " minDaysPlayed=" + settings.minDaysPlayed
                + " caps=" + settings.maxConcurrentPerPlayer + "/" + settings.maxConcurrentGlobal);
    }

    @Override public void onDisable() {
        if (loop != null) loop.cancel();
        for (ActiveInvasion invasion : new ArrayList<ActiveInvasion>(active.values())) invasion.cancel();
        active.clear();
        // Never leave someone's base full of holes because the server went down mid-siege.
        if (restorer != null) restorer.restoreAll();
        sweepStrays("shutdown");
        if (store != null) store.save();
    }

    private void rebuild() {
        settings = new InvasionConfig(getConfig());
        restorer = new BlockRestorer(settings);
        factory = new InvaderFactory(settings, random);
        brain = new InvaderBrain(settings, restorer);
    }

    // ---------------------------------------------------------------- accessors used by the listener

    InvasionConfig settings() { return settings; }

    BlockRestorer restorer() { return restorer; }

    long ticks() { return ticks; }

    boolean isBeingInvaded(UUID id) { return active.containsKey(id); }

    void noteJoin(UUID id) { joinedAt.put(id, ticks); }

    void endInvasionFor(UUID id, boolean survived) {
        ActiveInvasion invasion = active.remove(id);
        if (invasion != null) invasion.end(survived, true);
        joinedAt.remove(id);
    }

    // ---------------------------------------------------------------- main loop

    private void pump() {
        ticks += TICK_INTERVAL;

        if (ticks - lastAccrueTick >= ACCRUE_INTERVAL) {
            long elapsed = ticks - lastAccrueTick;
            lastAccrueTick = ticks;
            store.accrue(Bukkit.getOnlinePlayers(), elapsed, settings);
        }
        if (ticks - lastSaveTick >= 6000L) {
            lastSaveTick = ticks;
            store.save();
        }

        restorer.tick(ticks);

        int globalAlive = 0;
        for (ActiveInvasion invasion : active.values()) globalAlive += invasion.aliveCount();

        for (Iterator<Map.Entry<UUID, ActiveInvasion>> it = active.entrySet().iterator(); it.hasNext(); ) {
            ActiveInvasion invasion = it.next().getValue();
            invasion.tick(ticks, globalAlive);
            if (invasion.isFinished()) {
                it.remove();
                Player owner = Bukkit.getPlayer(invasion.playerId());
                if (owner != null && invasion.survived() && !invasion.isTest()) {
                    PlayerProgress progress = store.get(owner);
                    progress.invasionsSurvived++;
                    store.markDirty();
                }
                getLogger().info("JASPR_INVASIONS event=finished player=" + invasion.playerName()
                        + " survived=" + invasion.survived() + " spawned=" + invasion.spawnedTotal());
            }
        }
    }

    /**
     * Bed summons: climbing into bed is the only thing that calls an invasion down.
     * One per night per player; the night schedule never starts anything by itself.
     */
    boolean tryBedSummon(Player player) {
        if (!settings.enabled || player == null) return false;
        if (!settings.allowsWorld(player.getWorld().getName())) return false;
        if (active.containsKey(player.getUniqueId())) return false;
        PlayerProgress progress = store.get(player);
        if (!canBeInvaded(player, progress)) return false;
        long day = player.getWorld().getFullTime() / InvasionConfig.TICKS_PER_DAY;
        if (progress.lastInvasionDay == day) return false;
        progress.lastInvasionDay = day;
        progress.invasionsFaced++;
        store.markDirty();
        start(player, progress.difficulty(settings), false, "bed");
        player.sendMessage(ChatColor.DARK_RED + "You try to sleep, but something has found you. "
                + ChatColor.GRAY + "No rest tonight.");
        return true;
    }

    /** Eligibility is per player: the right mode, settled in, and enough time on the server. */
    private boolean canBeInvaded(Player player, PlayerProgress progress) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return false;
        if (!progress.eligible(settings)) return false;
        Long joined = joinedAt.get(player.getUniqueId());
        // Freshly connected players are often still at a login prompt; give them a moment.
        return joined == null || ticks - joined >= 600L;
    }

    private void start(Player player, double difficulty, boolean test, String reason) {
        long maxDuration = test ? 20L * 60L * 6L : 20L * 60L * 20L;
        ActiveInvasion invasion = new ActiveInvasion(settings, factory, brain, random,
                player, difficulty, test, ticks, maxDuration);
        active.put(player.getUniqueId(), invasion);
        getLogger().info("JASPR_INVASIONS event=started player=" + player.getName()
                + " world=" + player.getWorld().getName()
                + " difficulty=" + String.format(Locale.ROOT, "%.2f", difficulty)
                + " tier=" + InvaderFactory.tierName(difficulty)
                + " budget=" + invasion.budgetTotal() + " reason=" + reason);
    }

    /** Removes invaders left behind by a crash or a reload, identified by their scoreboard tag. */
    private void sweepStrays(String when) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) continue;
                if (!entity.getScoreboardTags().contains(InvaderFactory.TAG)) continue;
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) getLogger().info("JASPR_INVASIONS event=swept when=" + when + " invaders=" + removed);
    }

    // ---------------------------------------------------------------- command

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if ("status".equals(action)) {
            sendStatus(sender);
            return true;
        }

        if ("reload".equals(action)) {
            reloadConfig();
            rebuild();
            getLogger().info("JASPR_INVASIONS event=config_reloaded by=" + sender.getName());
            sender.sendMessage(ChatColor.GREEN + "Invasion settings reloaded.");
            return true;
        }

        if ("stop".equals(action)) {
            if (active.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No invasions running.");
                return true;
            }
            int stopped = active.size();
            for (ActiveInvasion invasion : new ArrayList<ActiveInvasion>(active.values())) invasion.cancel();
            active.clear();
            sender.sendMessage(ChatColor.YELLOW + "Stopped " + stopped + " invasion(s).");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Run /invasion in game to call one down on yourself.");
            return true;
        }
        Player player = (Player) sender;

        if (active.containsKey(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You are already being invaded. Use /invasion stop first.");
            return true;
        }

        PlayerProgress progress = store.get(player);
        double difficulty = progress.difficulty(settings);
        if (!action.isEmpty()) {
            try {
                double forced = Double.parseDouble(action);
                if (forced < 0.0d || forced > 1.0d) throw new NumberFormatException();
                difficulty = forced;
            } catch (NumberFormatException notANumber) {
                sender.sendMessage(ChatColor.RED + "Usage: /invasion [status|stop|reload|<difficulty 0.0-1.0>]");
                return true;
            }
        }

        start(player, difficulty, true, "command");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        long day = -1L;
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (main != null) day = main.getFullTime() / InvasionConfig.TICKS_PER_DAY;

        sender.sendMessage(ChatColor.GOLD + "Invasions " + ChatColor.GRAY + "- day " + day
                + ChatColor.GRAY + ", summoned by sleeping (once per night).");

        if (active.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Nothing running.");
        } else {
            for (ActiveInvasion invasion : active.values()) {
                sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.WHITE + invasion.playerName()
                        + ChatColor.GRAY + " - " + InvaderFactory.tierName(invasion.difficulty())
                        + ", " + invasion.aliveCount() + " alive, "
                        + invasion.spawnedTotal() + "/" + invasion.budgetTotal() + " sent");
            }
        }

        if (sender instanceof Player) {
            PlayerProgress progress = store.get((Player) sender);
            double difficulty = progress.difficulty(settings);
            String line = ChatColor.GRAY + "  You: " + ChatColor.WHITE
                    + String.format(Locale.ROOT, "%.1f", progress.daysPlayed()) + ChatColor.GRAY + " days played, tier "
                    + ChatColor.WHITE + InvaderFactory.tierName(difficulty)
                    + ChatColor.GRAY + " (" + String.format(Locale.ROOT, "%.2f", difficulty) + "), survived "
                    + ChatColor.WHITE + progress.invasionsSurvived;
            sender.sendMessage(line);
            if (!progress.eligible(settings)) {
                sender.sendMessage(ChatColor.DARK_GRAY + "  Not yet hunted: "
                        + progress.minutesUntilEligible(settings) + " more minutes of play.");
            }
        }

        List<String> waiting = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProgress progress = store.get(player);
            if (!progress.eligible(settings)) waiting.add(player.getName());
        }
        if (!waiting.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  Too new to be invaded: " + waiting);
        }
    }
}
