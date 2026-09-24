package chat.jaspr.biomes;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * What is this place, and is it built correctly?
 *
 * Everything the generator decides about a column is a pure function of the seed
 * and the coordinate, so the same questions it answered while building can be
 * asked again afterwards, from anywhere, without reading the world at all. Put
 * that next to what the world actually contains and the two disagreeing is
 * itself the report: terrain that should be at y72 and is at y58 is a breach,
 * and rock that nothing is holding up is a bug.
 */
public final class Survey {
    private Survey() {}

    /** How far out to look for the nearest opening, and how coarsely. */
    private static final int SEARCH = 192, STEP = 6;

    public static boolean report(CommandSender sender, String epoch) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only a player has a position to survey."); return true; }
        Player p = (Player) sender;
        World w = p.getWorld();
        int x = p.getLocation().getBlockX(), y = p.getLocation().getBlockY(), z = p.getLocation().getBlockZ();

        sender.sendMessage(ChatColor.GOLD + "Survey " + ChatColor.GRAY + x + ", " + y + ", " + z
            + ChatColor.DARK_GRAY + "  chunk " + (x >> 4) + ", " + (z >> 4)
            + "  (" + (x & 15) + "," + (z & 15) + " in chunk)");

        if (!(w.getGenerator() instanceof HorrorGenerator)) {
            sender.sendMessage(ChatColor.GRAY + "World " + ChatColor.WHITE + w.getName()
                + ChatColor.GRAY + " — biome " + ChatColor.WHITE + w.getBiome(x, z).name());
            sender.sendMessage(ChatColor.DARK_GRAY + "Terrain detail is only charted for the overworld.");
            return true;
        }

        Terrain t = new Terrain(w.getSeed());
        Caves caves = new Caves(t);
        SurfaceOpenings openings = new SurfaceOpenings(t, caves);
        SurfaceOpenings.Cut cut = new SurfaceOpenings.Cut();
        Terrain.Sample sample = t.sample(x, z);
        Catalog.Profile profile = sample.profile;

        sender.sendMessage(ChatColor.AQUA + "Biome " + ChatColor.WHITE + profile.name
            + ChatColor.DARK_GRAY + "  (" + profile.slot.name().toLowerCase() + ", #" + profile.index
            + ", " + profile.atmosphere + ", trees " + profile.tree + ")");

        int region = caves.region(x, z);
        double breach = caves.breach(x, z);
        sender.sendMessage(ChatColor.AQUA + "Cave region " + ChatColor.WHITE + Caves.NAMES[region]
            + ChatColor.DARK_GRAY + "  surface breach " + pct(breach)
            + "  lava floor y" + Caves.lavaLevel(region));

        openings.sample(x, z, sample.y, cut);
        if (cut.depth > 0) {
            int floor = Math.max(6, sample.y - cut.depth);
            String pool = cut.fill == 0 ? "dry"
                : (cut.fill == 9 ? "water" : "lava") + " to y" + cut.fillTop;
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Opening " + ChatColor.WHITE
                + SurfaceOpenings.NAMES[cut.style] + ChatColor.DARK_GRAY
                + "  cut " + cut.depth + " deep, floor y" + floor
                + (cut.rim ? ", on the rim" : "") + ", " + pool);
        } else if (cut.rise > 0) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Opening " + ChatColor.WHITE
                + SurfaceOpenings.NAMES[cut.style] + ChatColor.DARK_GRAY
                + "  ejecta rim, " + cut.rise + " blocks of spoil");
        } else {
            int[] near = nearest(openings, cut, t, x, z);
            if (near == null) sender.sendMessage(ChatColor.LIGHT_PURPLE + "Opening " + ChatColor.GRAY
                + "none here, and none within " + SEARCH + " blocks");
            else sender.sendMessage(ChatColor.LIGHT_PURPLE + "Opening " + ChatColor.GRAY + "none here"
                + ChatColor.DARK_GRAY + "  nearest " + SurfaceOpenings.NAMES[near[0]] + " "
                + near[1] + " blocks " + compass(near[2], near[3]));
        }

        int actual = w.getHighestBlockYAt(x, z);
        String drop = actual < sample.y - 1
            ? ChatColor.YELLOW + "  open to the sky " + (sample.y - actual) + " blocks below the heightmap"
            : ChatColor.DARK_GRAY + "  intact";
        sender.sendMessage(ChatColor.AQUA + "Surface " + ChatColor.WHITE + "y" + actual
            + ChatColor.DARK_GRAY + " vs heightmap y" + sample.y + drop);

        sender.sendMessage(ChatColor.AQUA + "Light " + ChatColor.WHITE
            + w.getBlockAt(x, Math.min(255, y + 1), z).getLightLevel()
            + ChatColor.DARK_GRAY + "  (sky " + w.getBlockAt(x, Math.min(255, y + 1), z).getLightFromSky()
            + ", block " + w.getBlockAt(x, Math.min(255, y + 1), z).getLightFromBlocks() + ")");

        Where.report(sender, w, t, x, y, z);
        audit(sender, p.getLocation().getChunk());
        sender.sendMessage(ChatColor.DARK_GRAY + "seed " + w.getSeed() + "  epoch " + epoch);
        return true;
    }

    /** The three faults worth knowing about, counted over the chunk you are standing in. */
    private static void audit(CommandSender sender, Chunk c) {
        ChunkSnapshot snap = c.getChunkSnapshot(false, false, false);
        int stranded = 0, grit = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 2; y <= 140; y++) {
            int id = snap.getBlockTypeId(x, y, z);
            if (id == 9 || id == 11) { if (snap.getBlockTypeId(x, y - 1, z) == 0) stranded++; continue; }
            if (Floaters.loose(id)) continue;
            if (x == 0 || x == 15 || z == 0 || z == 15) continue;
            if (Floaters.loose(snap.getBlockTypeId(x - 1, y, z)) && Floaters.loose(snap.getBlockTypeId(x + 1, y, z))
                && Floaters.loose(snap.getBlockTypeId(x, y, z - 1)) && Floaters.loose(snap.getBlockTypeId(x, y, z + 1)))
                grit++;
        }
        int floating = Floaters.count(snap);
        String colour = floating == 0 && stranded == 0 ? ChatColor.DARK_GRAY.toString() : ChatColor.RED.toString();
        sender.sendMessage(colour + "Chunk audit " + ChatColor.WHITE + "unsupported " + floating
            + ChatColor.DARK_GRAY + ", stranded liquid " + stranded + ", one-wide rock " + grit
            + (floating == 0 && stranded == 0 ? "  (clean)" : "  <- report this"));
    }

    /** Sweep outward on a coarse grid; the first ring with a hit wins. */
    private static int[] nearest(SurfaceOpenings openings, SurfaceOpenings.Cut cut, Terrain t, int x, int z) {
        int best = Integer.MAX_VALUE, style = -1, bx = 0, bz = 0;
        for (int dx = -SEARCH; dx <= SEARCH; dx += STEP) for (int dz = -SEARCH; dz <= SEARCH; dz += STEP) {
            int wx = x + dx, wz = z + dz;
            int d = dx * dx + dz * dz;
            if (d >= best || d > SEARCH * SEARCH) continue;
            openings.sample(wx, wz, t.sample(wx, wz).y, cut);
            if (!cut.any()) continue;
            best = d; style = cut.style; bx = dx; bz = dz;
        }
        if (style < 0) return null;
        return new int[]{style, (int) Math.round(Math.sqrt(best)), bx, bz};
    }

    private static String compass(int dx, int dz) {
        String ns = dz < -8 ? "north" : dz > 8 ? "south" : "";
        String ew = dx < -8 ? "west" : dx > 8 ? "east" : "";
        String out = ns + ew;
        return out.isEmpty() ? "right here" : out;
    }

    private static String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }
}
