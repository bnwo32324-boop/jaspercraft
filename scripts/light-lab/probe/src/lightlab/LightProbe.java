package lightlab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Fixture-only (never deployed): cross-world teleport and read/set of the per-world random-light-updates flag. */
public final class LightProbe extends JavaPlugin {
    private long[] tickTimes;

    @Override public void onEnable() {
        Object nmsServer = ((org.bukkit.craftbukkit.v1_12_R1.CraftServer) Bukkit.getServer()).getServer();
        try {
            for (Class<?> c = nmsServer.getClass(); c != null && tickTimes == null; c = c.getSuperclass())
                for (java.lang.reflect.Field f : c.getDeclaredFields())
                    if (f.getType() == long[].class) { f.setAccessible(true); long[] a = (long[]) f.get(nmsServer); if (a != null && a.length == 100) { tickTimes = a; break; } }
        } catch (Exception e) { getLogger().warning("tick ring not found: " + e); }
    }

    private static boolean flag(World w) { return ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle().spigotConfig.randomLightUpdates; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) return false;
        try {
            switch (a[0]) {
                case "tp": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    World w = Bukkit.getWorld(a[2]);
                    if (p == null || w == null) { getLogger().info("LP_TP_FAILED player=" + a[1] + " world=" + a[2] + " found=" + (p != null) + "/" + (w != null)); return true; }
                    boolean ok = p.teleport(new Location(w, Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5])));
                    getLogger().info("LP_TP player=" + a[1] + " world=" + a[2] + " ok=" + ok);
                    return true;
                }
                case "flag": {
                    World w = Bukkit.getWorld(a[1]);
                    if (w == null) { getLogger().info("LP_FLAG_FAILED world=" + a[1]); return true; }
                    if (a.length > 2) ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle().spigotConfig.randomLightUpdates = a[2].equals("on");
                    getLogger().info("LP_FLAG world=" + a[1] + " randomLightUpdates=" + flag(w));
                    return true;
                }
                case "worlds": {
                    StringBuilder sb = new StringBuilder();
                    for (World w : Bukkit.getWorlds()) sb.append(w.getName()).append('=').append(flag(w)).append(' ');
                    getLogger().info("LP_WORLDS " + sb.toString().trim());
                    return true;
                }
                case "census": {   // lprobe census <world> <chunkX> <chunkZ> <radius>: the server's own block light at its light emitters
                    World w = Bukkit.getWorld(a[1]);
                    int cx = Integer.parseInt(a[2]), cz = Integer.parseInt(a[3]), r = Integer.parseInt(a[4]);
                    int chunks = 0, emitters = 0, dark = 0, high = 0, highDark = 0, opaque = 0, opaqueDark = 0, open = 0, openDark = 0;
                    long lit = 0;
                    for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
                        if (w == null || !w.isChunkLoaded(x, z)) continue;
                        net.minecraft.server.v1_12_R1.Chunk ch = ((org.bukkit.craftbukkit.v1_12_R1.CraftChunk) w.getChunkAt(x, z)).getHandle();
                        chunks++;
                        for (net.minecraft.server.v1_12_R1.ChunkSection sec : ch.getSections()) {
                            if (sec == null) continue;
                            net.minecraft.server.v1_12_R1.NibbleArray bl = sec.getEmittedLightArray();
                            for (int y = 0; y < 16; y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                                int light = bl.a(lx, y, lz);
                                if (light > 0) lit++;
                                int e = sec.getType(lx, y, lz).d();
                                if (e >= 7) {
                                    boolean isHigh = (sec.getYPosition() + y) >= 63, isOpaque = sec.getType(lx, y, lz).c() >= 15;
                                    boolean isOpen = (sec.getYPosition() + y) >= ch.b(lx, lz);
                                    emitters++; if (isHigh) high++; if (isOpaque) opaque++; if (isOpen) open++;
                                    if (light < e) { dark++; if (isHigh) highDark++; if (isOpaque) opaqueDark++; if (isOpen) openDark++; }
                                }
                            }
                        }
                    }
                    getLogger().info("LP_CENSUS world=" + a[1] + " cx=" + cx + " cz=" + cz + " chunks=" + chunks + " emitters=" + emitters + " dark=" + dark + " high=" + high + " highDark=" + highDark + " opaque=" + opaque + " opaqueDark=" + opaqueDark + " open=" + open + " openDark=" + openDark + " lit=" + lit);
                    return true;
                }
                case "ticks": {   // lprobe ticks: mean and max of the last 100 server ticks, in microseconds
                    if (tickTimes == null) { getLogger().info("LP_TICKS unavailable"); return true; }
                    long sum = 0, max = 0;
                    for (long t : tickTimes) { sum += t; max = Math.max(max, t); }
                    getLogger().info("LP_TICKS meanUs=" + sum / 100 / 1000 + " maxUs=" + max / 1000);
                    return true;
                }
                case "finddark": {   // lprobe finddark <world> <chunkX> <chunkZ> <radius>: up to 3 emitters the server has not lit
                    World w = Bukkit.getWorld(a[1]);
                    int cx = Integer.parseInt(a[2]), cz = Integer.parseInt(a[3]), r = Integer.parseInt(a[4]), found = 0;
                    StringBuilder sb = new StringBuilder();
                    for (int x = cx - r; x <= cx + r && found < 3; x++) for (int z = cz - r; z <= cz + r && found < 3; z++) {
                        if (w == null || !w.isChunkLoaded(x, z)) continue;
                        net.minecraft.server.v1_12_R1.Chunk ch = ((org.bukkit.craftbukkit.v1_12_R1.CraftChunk) w.getChunkAt(x, z)).getHandle();
                        for (net.minecraft.server.v1_12_R1.ChunkSection sec : ch.getSections()) {
                            if (sec == null || found >= 3) continue;
                            net.minecraft.server.v1_12_R1.NibbleArray bl = sec.getEmittedLightArray();
                            for (int y = 0; y < 16 && found < 3; y++) for (int lz = 0; lz < 16 && found < 3; lz++) for (int lx = 0; lx < 16 && found < 3; lx++) {
                                int e = sec.getType(lx, y, lz).d();
                                if (e >= 7 && bl.a(lx, y, lz) < e) { sb.append(' ').append((x << 4) + lx).append(',').append(sec.getYPosition() + y).append(',').append((z << 4) + lz); found++; }
                            }
                        }
                    }
                    getLogger().info("LP_DARK" + sb);
                    return true;
                }
                case "near": {   // lprobe near <world> <x> <y> <z> <r>: emitters within r blocks of a point, and how many the server has not lit
                    World w = Bukkit.getWorld(a[1]);
                    int px = Integer.parseInt(a[2]), py = Integer.parseInt(a[3]), pz = Integer.parseInt(a[4]), r = Integer.parseInt(a[5]), emitters = 0, dark = 0;
                    net.minecraft.server.v1_12_R1.WorldServer ws = ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle();
                    for (int x = px - r; x <= px + r; x++) for (int y = Math.max(0, py - r); y <= Math.min(255, py + r); y++) for (int z = pz - r; z <= pz + r; z++) {
                        net.minecraft.server.v1_12_R1.BlockPosition bp = new net.minecraft.server.v1_12_R1.BlockPosition(x, y, z);
                        int e = ws.getType(bp).d();
                        if (e >= 7) { emitters++; if (ws.getBrightness(net.minecraft.server.v1_12_R1.EnumSkyBlock.BLOCK, bp) < e) dark++; }
                    }
                    getLogger().info("LP_NEAR emitters=" + emitters + " dark=" + dark);
                    return true;
                }
                default: return false;
            }
        } catch (RuntimeException e) {
            getLogger().info("LP_ERR " + e);
            return true;
        }
    }
}
