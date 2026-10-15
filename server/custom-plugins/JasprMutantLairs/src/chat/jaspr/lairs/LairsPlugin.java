package chat.jaspr.lairs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprMutantLairs (owner 2026-10-14: "add 3 boss structures for every mutant creature mob each in the overworld"):
 * twelve boss lairs, three for each of Mutant Creatures' four hostile mutants (zombie, skeleton, creeper, enderman), placed
 * one per 512-block cell of the main overworld into land nobody has touched (Director), each with its mutant boss,
 * garrison, supplies and a vault that opens when the boss falls (Bosses, Loot). The mod's snow golem, spider pig and
 * creeper minion are a player's creations or pets, not bosses, so they have no lairs.
 */
public final class LairsPlugin extends JavaPlugin {
    static Logger log;
    private Ledger ledger;
    private Director director;
    private Bosses bosses;
    private World world;

    @Override public void onEnable() {
        log = getLogger();
        saveDefaultConfig();
        bosses = new Bosses(this);
        Bukkit.getPluginManager().registerEvents(bosses, this);
        if (!getConfig().getBoolean("enabled", true)) { getLogger().warning("LAIRS_DISABLED by config"); return; }
        World w = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) { getLogger().severe("LAIRS_REFUSED no overworld named " + getConfig().getString("world", "world")); return; }
        try {
            ledger = new Ledger(new File(getDataFolder(), "lairs-" + w.getUID() + ".yml"));
            int n = ledger.load();
            // the guards live where JasprHorrorBiomes (and the other packs) read them; without it (test servers) in our own folder
            Plugin horror = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
            if (horror == null) getLogger().warning("LAIRS_NO_HORRORBIOMES guards kept in the plugin folder, no Lost City reservations");
            File guards = new File(new File(horror != null ? horror.getDataFolder() : getDataFolder(), "imported-protection-v1"), w.getUID().toString());
            if (!guards.isDirectory() && !guards.mkdirs()) throw new IOException("cannot create " + guards);
            world = w;
            director = new Director(this, w, ledger, guards);
            for (Ledger.Lair l : ledger.all()) bosses.index(l);
            Bukkit.getScheduler().runTaskTimer(this, director::step, 100L, 5L);
            Bukkit.getScheduler().runTaskTimer(this, () -> bosses.tick(world), 100L, 10L);
            Bukkit.getScheduler().runTaskTimer(this, this::flush, 600L, 600L);
            boolean mutants = Bukkit.getPluginManager().getPlugin("JasprMutants") != null;
            getLogger().info("LAIRS_READY version=" + getDescription().getVersion() + " designs=" + Designs.ALL.size() + " grid=" + Planner.CELL
                + " lairs=" + n + " empty=" + ledger.emptyCount() + " spawnExclusion=" + director.spawnExclusion + " playerMargin=" + director.playerMargin
                + " inhabitedLimit=" + director.inhabitedLimit + " mutants=" + mutants + " health=x" + bosses.healthMultiplier);
        } catch (IOException | RuntimeException e) {
            getLogger().severe("LAIRS_REFUSED " + e);
            ledger = null; director = null;
        }
    }

    @Override public void onDisable() { flush(); }

    private void flush() {
        if (ledger == null) return;
        try { ledger.saveIfDirty(); } catch (IOException e) { getLogger().severe("LAIRS_LEDGER_SAVE_FAILED " + e.getMessage()); }
    }

    Ledger ledger() { return ledger; }
    Bosses bosses() { return bosses; }

    // ---------------------------------------------------------------- /lairs

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (ledger == null) { sender.sendMessage(ChatColor.RED + "Mutant lairs are not running."); return true; }
        boolean op = sender.isOp() || !(sender instanceof Player);
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        if (op && sub.equals("status")) {
            int built = 0, building = 0, open = 0;
            for (Ledger.Lair l : ledger.all()) { if (l.built()) built++; else building++; if (l.vaultOpen) open++; }
            sender.sendMessage("Lairs: built=" + built + " building=" + building + " vaultsOpen=" + open + " emptyCells=" + ledger.emptyCount());
            sender.sendMessage("Director: " + director.status());
            sender.sendMessage("Bosses: " + bosses.status());
            return true;
        }
        if (op && sub.equals("list")) {
            for (Ledger.Lair l : ledger.all()) sender.sendMessage(l.key + " " + l.design.id + " " + l.state + " at " + l.x + "," + l.y + "," + l.z + " kills=" + l.kills + (l.vaultOpen ? " open" : ""));
            return true;
        }
        if (op && sub.equals("gen") && args.length >= 5) {
            // generates (and so populates the inside of) a rectangle of chunks: test servers only need this
            int a0 = Integer.parseInt(args[1]), b0 = Integer.parseInt(args[2]), a1 = Integer.parseInt(args[3]), b1 = Integer.parseInt(args[4]), n = 0;
            for (int a = a0; a <= a1; a++) for (int b = b0; b <= b1; b++) { world.loadChunk(a, b, true); n++; }
            getLogger().info("LAIR_GEN chunks=" + n);
            return true;
        }
        if (op && sub.equals("check")) {
            int bad = 0, n = 0;
            for (Ledger.Lair l : ledger.all()) {
                if (args.length >= 2 && !l.key.equals(args[1])) continue;
                if (!l.built()) continue;
                String r = director.check(l);
                n++;
                if (!r.contains(" mismatches=0 ")) bad++;
                if (sender instanceof Player) sender.sendMessage(r);
                getLogger().info(r);
            }
            getLogger().info("LAIR_CHECKS n=" + n + " bad=" + bad);
            return true;
        }
        if (op && sub.equals("build") && !(sender instanceof Player) && args.length >= 4) {
            Design d = Designs.byId(args[1]);
            if (d == null) { sender.sendMessage("Designs: " + ids()); return true; }
            int x = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]), rot = args.length >= 5 ? Integer.parseInt(args[4]) & 3 : 0;
            String why = director.forceBuild(d, x, z, rot, Integer.MIN_VALUE);
            getLogger().info(why == null ? "LAIR_FORCED design=" + d.id + " at=" + x + "," + z + " rot=" + rot : "LAIR_FORCE_REFUSED design=" + d.id + " " + why);
            return true;
        }
        if (op && sub.equals("build") && sender instanceof Player && args.length >= 2) {
            Design d = Designs.byId(args[1]);
            if (d == null) { sender.sendMessage("Designs: " + ids()); return true; }
            Player p = (Player) sender;
            if (!p.getWorld().equals(world)) { sender.sendMessage("Only in " + world.getName()); return true; }
            int rot = args.length >= 3 ? Integer.parseInt(args[2]) & 3 : 0;
            int w = (rot & 1) == 0 ? d.w : d.d, dd = (rot & 1) == 0 ? d.d : d.w;
            Location at = p.getLocation();
            String why = director.forceBuild(d, at.getBlockX() - w / 2, at.getBlockZ() - dd / 2, rot, at.getBlockY() - 1);
            sender.sendMessage(why == null ? "Building " + d.title + " here (rot " + rot + ")." : "Refused: " + why);
            return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("/lairs status | list"); return true; }
        // players: the lairs they have found, the nearest of them
        Player p = (Player) sender;
        String me = p.getUniqueId().toString();
        List<Ledger.Lair> found = new ArrayList<>();
        Ledger.Lair best = null;
        double bestD = Double.MAX_VALUE;
        for (Ledger.Lair l : ledger.all()) {
            boolean show = l.built() && (l.seen.contains(me) || op && sub.equals("near"));
            if (!show) continue;
            found.add(l);
            if (!p.getWorld().equals(world)) continue;
            double dist = Math.hypot(l.centerX() - p.getLocation().getX(), l.centerZ() - p.getLocation().getZ());
            if (dist < bestD) { bestD = dist; best = l; }
        }
        int kinds = (int) found.stream().map(l -> l.design.id).distinct().count();
        p.sendMessage(ChatColor.DARK_RED + "Mutant lairs " + ChatColor.GRAY + "found: " + found.size() + " (" + kinds + " of " + Designs.ALL.size() + " kinds)");
        if (best != null) {
            double dx = best.centerX() - p.getLocation().getX(), dz = best.centerZ() - p.getLocation().getZ();
            String state = best.bossId != null ? "its boss is awake" : System.currentTimeMillis() < best.restUntil ? "its boss rests" : "its boss sleeps";
            p.sendMessage(ChatColor.GRAY + "Nearest: " + ChatColor.WHITE + best.design.title + ChatColor.GRAY + " (" + best.design.mutantName() + "), "
                + Math.round(bestD) + " blocks " + compass(dx, dz) + ", " + state + (best.vaultOpen ? ", vault open" : ", vault sealed"));
        } else p.sendMessage(ChatColor.GRAY + "You have not found one yet. They lie in the wild, far from towns and roads.");
        return true;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        return names[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    private static String ids() { StringBuilder b = new StringBuilder(); for (Design d : Designs.ALL) b.append(d.id).append(' '); return b.toString().trim(); }
}
