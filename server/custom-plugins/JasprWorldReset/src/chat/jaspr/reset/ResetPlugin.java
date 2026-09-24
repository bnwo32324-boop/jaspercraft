package chat.jaspr.reset;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Retires the current world so the server builds a new one, and carries every
 * player across intact.
 *
 * It runs in onLoad, which Bukkit calls before any world is loaded, and it moves
 * rather than deletes: a rename is instant whatever the world's size, it cannot
 * half-finish the way a recursive delete can, and it leaves the old world beside
 * the new one in case the change is not wanted after all.
 *
 * What makes a player is not in the plugins folder; it is in world/playerdata,
 * one raw NBT file each. Copying those files verbatim is what preserves an
 * inventory exactly -- every custom tag on every weapon, every armament level,
 * enchantments, the ender chest, experience, health, hunger, potion effects --
 * because nothing is parsed or rebuilt on the way. Anything this code understood
 * would be something it could get wrong.
 *
 * The one thing that cannot survive is position: those coordinates describe
 * terrain that no longer exists. Each carried player is marked once, and placed
 * on solid ground near the new spawn the first time they log in.
 */
public final class ResetPlugin extends JavaPlugin implements Listener {
    private static final String[] WORLDS = {"world", "world_nether", "world_the_end"};
    /** Copied wholesale: all of it is per-player and none of it describes terrain. */
    private static final String[] CARRY_TREES = {"playerdata", "stats", "advancements"};
    /** Individual files worth keeping out of data/: maps and the scoreboard, not villages. */
    private static final String[] CARRY_DATA_EXACT = {"scoreboard.dat", "idcounts.dat"};
    private static final String CARRY_DATA_PREFIX = "map_";

    private final Set<UUID> relocate = new HashSet<UUID>();
    private File relocateFile;

    @Override public void onLoad() {
        try { retire(); }
        catch (RuntimeException e) { getLogger().severe("WORLD_RESET_FAILED " + e); }
    }

    @Override public void onEnable() {
        relocateFile = new File(getDataFolder(), "relocate.txt");
        loadRelocations();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (!relocate.isEmpty()) getLogger().info("WORLD_RESET " + relocate.size() + " player(s) awaiting placement in the new world");
    }

    // -- the swap ---------------------------------------------------------------

    private void retire() {
        File data = getDataFolder();
        File request = new File(data, "reset.request");
        File completed = new File(data, "reset.completed");
        if (!request.isFile()) return;

        String token = read(request);
        if (token.isEmpty()) { getLogger().warning("WORLD_RESET ignored: request has no token"); return; }
        if (completed.isFile() && read(completed).equals(token)) {
            getLogger().info("WORLD_RESET already applied for token " + token + "; leaving worlds alone");
            return;
        }

        File root = getServer().getWorldContainer();
        File backups = new File(root, ".runtime" + File.separator + "backups"
            + File.separator + "world-retired-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        if (!backups.isDirectory() && !backups.mkdirs()) {
            getLogger().severe("WORLD_RESET_FAILED cannot create " + backups);
            return;
        }
        // Written before anything moves: a crash midway must not leave a request
        // that fires again on the next boot.
        write(completed, token);

        int moved = 0;
        File retiredOverworld = null;
        for (String name : WORLDS) {
            File world = new File(root, name);
            if (!world.isDirectory()) continue;
            File target = new File(backups, name);
            if (world.renameTo(target)) {
                moved++;
                if (name.equals("world")) retiredOverworld = target;
                getLogger().info("WORLD_RESET retired " + name + " -> " + target.getPath());
            } else getLogger().severe("WORLD_RESET could not retire " + name + " (in use?)");
        }

        if (retiredOverworld != null) carryPlayers(retiredOverworld, new File(root, "world"));
        if (!request.delete()) getLogger().warning("WORLD_RESET could not remove the request file; the token guard will stop a repeat");
        getLogger().info("WORLD_RESET complete: " + moved + " world(s) retired to " + backups.getPath());
    }

    /** Copies the per-player state out of the retired world into the fresh one. */
    private void carryPlayers(File from, File to) {
        List<UUID> carried = new ArrayList<UUID>();
        try {
            if (!to.isDirectory() && !to.mkdirs()) throw new IOException("cannot create " + to);
            for (String tree : CARRY_TREES) {
                File source = new File(from, tree);
                if (!source.isDirectory()) continue;
                int files = copyTree(source, new File(to, tree));
                getLogger().info("WORLD_RESET carried " + tree + " (" + files + " file(s))");
            }
            File playerdata = new File(from, "playerdata");
            File[] listing = playerdata.listFiles();
            if (listing != null) for (File file : listing) {
                String name = file.getName();
                if (!name.endsWith(".dat")) continue;
                try { carried.add(UUID.fromString(name.substring(0, name.length() - 4))); }
                catch (IllegalArgumentException ignored) { }
            }
            File dataFrom = new File(from, "data");
            File[] dataFiles = dataFrom.listFiles();
            if (dataFiles != null) {
                File dataTo = new File(to, "data");
                int kept = 0;
                for (File file : dataFiles) {
                    if (!file.isFile()) continue;
                    String name = file.getName();
                    boolean want = name.startsWith(CARRY_DATA_PREFIX);
                    for (String exact : CARRY_DATA_EXACT) if (exact.equals(name)) want = true;
                    if (!want) continue;   // villages, strongholds: they describe terrain that is gone
                    if (!dataTo.isDirectory() && !dataTo.mkdirs()) break;
                    copyFile(file, new File(dataTo, name));
                    kept++;
                }
                if (kept > 0) getLogger().info("WORLD_RESET carried data/ (" + kept + " file(s): maps, scoreboard)");
            }
            write(new File(getDataFolder(), "relocate.txt"), join(carried));
            getLogger().info("WORLD_RESET carried " + carried.size() + " player profile(s) into the new world");
        } catch (Exception e) {
            getLogger().severe("WORLD_RESET_CARRY_FAILED " + e + " -- player data is safe in " + from.getPath());
        }
    }

    private static int copyTree(File from, File to) throws IOException {
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("cannot create " + to);
        File[] listing = from.listFiles();
        if (listing == null) return 0;
        int count = 0;
        for (File file : listing) {
            File target = new File(to, file.getName());
            if (file.isDirectory()) count += copyTree(file, target);
            else { copyFile(file, target); count++; }
        }
        return count;
    }

    private static void copyFile(File from, File to) throws IOException {
        InputStream in = null; OutputStream out = null;
        try {
            in = new FileInputStream(from); out = new FileOutputStream(to);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
            if (out != null) try { out.close(); } catch (IOException ignored) { }
        }
    }

    // -- placing the carried players --------------------------------------------

    private void loadRelocations() {
        relocate.clear();
        if (relocateFile == null || !relocateFile.isFile()) return;
        for (String line : read(relocateFile).split("\\s+")) {
            if (line.isEmpty()) continue;
            try { relocate.add(UUID.fromString(line.trim())); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveRelocations() {
        write(relocateFile, join(new ArrayList<UUID>(relocate)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!relocate.remove(player.getUniqueId())) return;
        saveRelocations();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) { relocate.add(player.getUniqueId()); saveRelocations(); return; }
                Location safe = ground(Bukkit.getWorlds().get(0));
                if (safe != null) {
                    player.teleport(safe);
                    player.setFallDistance(0f);
                }
                player.sendMessage(ChatColor.GRAY + "The world has been rebuilt. Your inventory, experience and "
                    + "progress came with you; only your position was reset.");
                getLogger().info("WORLD_RESET placed " + player.getName() + " in the rebuilt world");
            }
        }, 20L);
    }

    /** First open, solid-footed column near spawn. Nobody should wake up inside rock. */
    private Location ground(World world) {
        Location spawn = world.getSpawnLocation();
        for (int radius = 0; radius <= 48; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = spawn.getBlockX() + dx, z = spawn.getBlockZ() + dz;
                    int y = world.getHighestBlockYAt(x, z);
                    if (y < 2 || y > 250) continue;
                    Block floor = world.getBlockAt(x, y, z);
                    if (!floor.getType().isSolid()) continue;
                    if (floor.getType() == Material.LAVA || floor.getType() == Material.STATIONARY_LAVA) continue;
                    Block head = world.getBlockAt(x, y + 1, z);
                    Block over = world.getBlockAt(x, y + 2, z);
                    if (head.getType() != Material.AIR || over.getType() != Material.AIR) continue;
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return null;
    }

    // -- small helpers ----------------------------------------------------------

    private static String join(List<UUID> ids) {
        StringBuilder out = new StringBuilder();
        for (UUID id : ids) out.append(id).append('\n');
        return out.toString();
    }

    private static String read(File file) {
        try { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim(); }
        catch (Exception e) { return ""; }
    }

    private void write(File file, String text) {
        try {
            if (!file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { getLogger().severe("WORLD_RESET cannot write " + file + ": " + e); }
    }
}
