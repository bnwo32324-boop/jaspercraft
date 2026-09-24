package chat.jaspr.rpg;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Character progression for JasperCraft: trainable player stats, and gear that levels with use.
 *
 * Both halves are open to everyone. Nothing here is an operator tool, so the commands carry
 * permissions that default to true and are listed as public in the server's command policy.
 */
public final class RpgPlugin extends JavaPlugin {
    private static final long TICK_INTERVAL = 10L;

    private RpgConfig settings;
    private StatStore store;
    private StatEffects effects;
    private StatsMenu statsMenu;
    private ArmamentMenu armamentMenu;
    private ArmamentListener armaments;
    private BukkitTask loop;
    private BukkitTask saver;
    private final Set<UUID> airborne = new HashSet<UUID>();
    private long ticks;

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = new RpgConfig(getConfig());

        store = new StatStore(new File(getDataFolder(), "stats.yml"));
        store.load();

        effects = new StatEffects(this);
        statsMenu = new StatsMenu(this);
        armamentMenu = new ArmamentMenu(this);
        armaments = new ArmamentListener(this);

        getServer().getPluginManager().registerEvents(effects, this);
        getServer().getPluginManager().registerEvents(statsMenu, this);
        if (settings.armamentsEnabled) {
            getServer().getPluginManager().registerEvents(armamentMenu, this);
            getServer().getPluginManager().registerEvents(armaments, this);
        }

        loop = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { pump(); }
        }, TICK_INTERVAL, TICK_INTERVAL);

        saver = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { store.save(); }
        }, 6000L, 6000L);

        for (Player player : getServer().getOnlinePlayers()) effects.refresh(player);

        getLogger().info("JASPR_RPG enabled stats=" + settings.statsEnabled
                + " armaments=" + settings.armamentsEnabled
                + " costMultiplier=" + settings.costMultiplier
                + " resetOnDeath=" + settings.resetOnDeath);
    }

    @Override public void onDisable() {
        if (loop != null) loop.cancel();
        if (saver != null) saver.cancel();
        if (store != null) store.save();
    }

    RpgConfig settings() { return settings; }

    StatStore stats() { return store; }

    StatEffects effects() { return effects; }

    // ---------------------------------------------------------------- loop

    private void pump() {
        ticks += TICK_INTERVAL;
        boolean slow = ticks % 120L == 0L;

        for (Player player : getServer().getOnlinePlayers()) {
            if (settings.statsEnabled) {
                effects.tickMovement(player);
                trackJump(player);
                if (slow) effects.refresh(player);
            }
            if (settings.armamentsEnabled && slow) armaments.tickArmour(player);
        }
    }

    /** A jump is the tick a player leaves the ground with upward momentum. */
    private void trackJump(Player player) {
        UUID id = player.getUniqueId();
        boolean grounded = player.isOnGround();
        if (grounded) {
            airborne.remove(id);
            return;
        }
        if (airborne.add(id) && player.getVelocity().getY() > 0.2d) {
            effects.onJump(player);
        }
    }

    /** Who a furnace most plausibly belongs to: the nearest player standing by it. */
    Player nearestOwner(Location where) {
        if (where == null || where.getWorld() == null) return null;
        Player best = null;
        double bestDistance = 64.0d;
        for (Player player : where.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(where);
            if (distance < bestDistance) { bestDistance = distance; best = player; }
        }
        return best;
    }

    // ---------------------------------------------------------------- commands

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if ("armament".equals(name)) {
            if (!(sender instanceof Player)) { sender.sendMessage("Run this in game while holding something."); return true; }
            if (!settings.armamentsEnabled) { sender.sendMessage(ChatColor.GRAY + "Armaments are switched off."); return true; }
            armamentMenu.open((Player) sender);
            return true;
        }

        // /stats, which is also what the in-game stats key sends.
        if (args.length > 0 && "top".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.GREEN + "Most trained survivors:");
            int shown = 0;
            for (PlayerStats sheet : store.ranked()) {
                if (sheet.totalLevels() <= 0 || shown++ >= 8) break;
                sender.sendMessage(ChatColor.GRAY + "  " + shown + ". " + ChatColor.WHITE + sheet.name
                        + ChatColor.GRAY + " - " + sheet.totalLevels() + " level(s)");
            }
            if (shown == 0) sender.sendMessage(ChatColor.DARK_GRAY + "  Nobody has trained anything yet.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Run /stats in game to open your sheet.");
            return true;
        }
        Player player = (Player) sender;

        // One key, two sheets. What is in your hand decides: hold something that can be upgraded
        // and you get that item's sheet, hold anything else - food, a torch, nothing at all - and
        // you get your own. This is why the stats key needs no separate binding for armaments.
        if (settings.armamentsEnabled
                && Armament.isEligible(player.getInventory().getItemInMainHand())
                && armamentMenu.open(player)) {
            return true;
        }

        if (!settings.statsEnabled) {
            sender.sendMessage(ChatColor.GRAY + "Stats are switched off.");
            return true;
        }
        statsMenu.open(player);
        return true;
    }
}
