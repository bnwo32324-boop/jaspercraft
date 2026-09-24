package chat.jaspr.biomes;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * What am I standing in, and who built it?
 *
 * There are three separate builders in this world and until now nothing told you which
 * one you were inside. They are asked in order of how specific they are: the register of
 * named set pieces first, then the built dungeon lattices, then the expedition catalogue,
 * which is the widest net. Every answer is a pure function of the seed and the position,
 * the same function that placed the thing, so this cannot name a structure that is not
 * there and cannot mistake one for another.
 *
 * The authorship line is not a guess. The 62 register set pieces and the 15 dungeon rooms
 * were written in the Claude Code session that also wrote this file. The 234 catalogue
 * designs and the 6 apocalypse ruin families predate it: the deployment manifest generated
 * on 2026-09-11 already records structures=234 and the ruins as legacy-disabled, so they
 * came from the earlier ChatGPT Codex work.
 */
public final class Where {
    private Where() {}

    public static final String CLAUDE = "Claude Code";
    public static final String CODEX  = "ChatGPT Codex";

    /** What was found, flattened for printing. */
    public static final class Found {
        public final String name, author, group, detail, blurb;
        public final int originX, originZ, floorY, sizeX, sizeZ, height;
        Found(String name, String author, String group, String detail, String blurb,
              int originX, int originZ, int floorY, int sizeX, int sizeZ, int height) {
            this.name = name; this.author = author; this.group = group; this.detail = detail;
            this.blurb = blurb; this.originX = originX; this.originZ = originZ; this.floorY = floorY;
            this.sizeX = sizeX; this.sizeZ = sizeZ; this.height = height;
        }
    }

    public static Found at(World world, Terrain t, int x, int y, int z) {
        int k = Megaliths.located(t, x, y, z);
        if (k >= 0) {
            int[] o = Megaliths.originOf(t, k, x, z);
            return new Found(Megaliths.siteName(k), CLAUDE, Megaliths.siteGroup(k) + " set piece",
                Megaliths.siteKind(k) + ", rank " + k + " of " + Megaliths.siteCount(),
                Megaliths.siteBlurb(k),
                o == null ? x : o[0], o == null ? z : o[1], o == null ? y : o[2],
                Megaliths.siteSizeX(k), Megaliths.siteSizeZ(k), Megaliths.siteHeight(k));
        }
        String dungeon = Dungeons.locate(t, x, y, z);
        if (dungeon != null) {
            String[] f = dungeon.split("\u0000");
            return new Found(f[0], CLAUDE, "dungeon room", "lattice " + f[1],
                "one of the fifteen rooms laid on their own sparse lattices",
                Integer.parseInt(f[2]), Integer.parseInt(f[4]), Integer.parseInt(f[3]), 0, 0, 0);
        }
        // The catalogue lookup reads the expansion boundary file; a survey is never worth
        // failing over, so a bad read leaves the answer at "nothing built here".
        try {
            if (world == null) return null;
            List<StructurePlanner.Site> sites = WorldgenExpansion.sites(world, x >> 4, z >> 4);
            for (StructurePlanner.Site s : sites) {
                int rx = s.x + StructurePlanner.MARGIN, rz = s.z + StructurePlanner.MARGIN;
                int w = s.design.columns * 12, d = s.design.rows * 12;
                if (x < rx - 4 || x >= rx + w + 4 || z < rz - 4 || z >= rz + d + 4) continue;
                if (y < s.y - 40 || y > s.y + 60) continue;
                int supply = 0, vault = 0, boss = 0, mob = 0, door = 0;
                for (StructurePlanner.Marker m : s.markers()) {
                    if (m.kind.equals("boss")) boss++;
                    else if (m.kind.equals("mob")) mob++;
                    else if (m.kind.equals("vault")) vault++;
                    else if (m.kind.equals("door")) door++;
                    else supply++;
                }
                return new Found(s.design.name, CODEX, "expedition catalogue",
                    s.design.family.replace('_', ' ') + " family, tier " + s.design.tier + ", "
                        + s.design.mode + ", " + s.design.columns + "x" + s.design.rows + " rooms"
                        + (s.design.exclusive ? ", biome-exclusive" : ""),
                    supply + " caches, " + vault + " vaults, " + boss + " boss, " + mob + " encounters"
                        + (door > 0 ? ", 1 threshold" : ""),
                    rx, rz, s.y, w, d, 0);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** The lines /where prints for the place you are in. */
    public static void report(CommandSender sender, World world, Terrain t, int x, int y, int z) {
        Found f = at(world, t, x, y, z);
        if (f == null) {
            sender.sendMessage(ChatColor.GREEN + "Structure " + ChatColor.GRAY
                + "open ground — nothing built on this column");
            return;
        }
        ChatColor tint = f.author.equals(CLAUDE) ? ChatColor.AQUA : ChatColor.GOLD;
        sender.sendMessage(ChatColor.GREEN + "Structure " + ChatColor.WHITE + f.name
            + tint + "  (" + f.author + ")");
        sender.sendMessage(ChatColor.DARK_GRAY + "          " + f.group + " — " + f.detail);
        if (f.blurb != null && !f.blurb.isEmpty())
            sender.sendMessage(ChatColor.DARK_GRAY + "          " + f.blurb);
        StringBuilder where = new StringBuilder("          origin ")
            .append(f.originX).append(", ").append(f.originZ).append("  floor y").append(f.floorY);
        if (f.sizeX > 0) where.append("  footprint ").append(f.sizeX).append("x").append(f.sizeZ);
        if (f.height > 0) where.append("x").append(f.height);
        where.append("  you are ").append(Math.max(0, Math.min(x - f.originX,
                f.sizeX > 0 ? f.originX + f.sizeX - 1 - x : x - f.originX)))
             .append(" blocks in, ").append(y - f.floorY).append(" above the floor");
        sender.sendMessage(ChatColor.DARK_GRAY + where.toString());
    }
}
