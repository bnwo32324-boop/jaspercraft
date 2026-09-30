package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

/**
 * In-game self test (owner tool, and -Djaspr.backrooms.selftest=true in the test fixture): loads the Backrooms and checks
 * every level's arrival, arena and exit on the real world, the Threshold's gate, frame detection, the protection record,
 * the item catalogue, loot and the difficulty curve. Logs BACKROOMS_SELFTEST pass=.. fail=.. and each failure.
 */
final class SelfTest {
    private final BackroomsPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private int passed;

    SelfTest(BackroomsPlugin plugin) { this.plugin = plugin; }

    private void check(boolean ok, String what) { if (ok) passed++; else failures.add(what); }

    private static boolean standable(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z);
        return !feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid() && (feet.getRelative(0, -1, 0).getType().isSolid() || feet.isLiquid());
    }

    void run(CommandSender to) {
        long t0 = System.nanoTime();
        World w = plugin.ensureWorld();
        check(w != null, "world loads");
        if (w != null) {
            check(w.getGenerator() instanceof BackroomsGenerator, "world uses the Backrooms generator");
            check("false".equals(w.getGameRuleValue("mobGriefing")) && "false".equals(w.getGameRuleValue("doFireTick")), "no griefing, no fire spread");
            check("wait".equals(plugin.lightMode), "new chunks are sent only once lit (" + plugin.lightMode + ")");
            for (Level lv : Level.ALL) {
                double[] a = Rooms.arrival(lv);
                int ax = (int) Math.floor(a[0]), az = (int) Math.floor(a[2]);
                w.loadChunk(ax >> 4, az >> 4, true);
                check(standable(w, ax, (int) a[1], az), lv + " arrival standable");
                int[] b = Styles.of(lv).bossSpot(lv);
                w.loadChunk(b[0] >> 4, b[2] >> 4, true);
                check(standable(w, b[0], b[1], b[2]), lv + " boss spot standable");
                int ex = lv.xEnd() - Level.WALL - 2;
                w.loadChunk(ex >> 4, 0, true);
                check(Rooms.inExit(lv, ex + 0.5, Level.WALK, 0.5) && !w.getBlockAt(ex, Level.WALK, 0).getType().isSolid(), lv + " exit open and recognised");
                check(lv.danger(lv.entryEnd(), 0) < lv.danger(lv.arenaStart(), 0), lv + " danger grows across the level");
                if (lv.number > 1) check(Level.of(lv.number - 1).danger(lv.number - 1 == 0 ? 0 : Level.of(lv.number - 1).arenaStart(), 0) <= lv.danger(lv.entryEnd(), 0) + 1e-9, lv + " starts no easier than the last level ended");
            }
            int[] g = Rooms.gate();
            w.loadChunk(g[0] >> 4, g[2] >> 4, true);
            check(w.getBlockAt(g[0], g[1], g[2]).getType() == Material.PORTAL && w.getBlockAt(g[0], g[1] + 2, g[2] + 1).getType() == Material.PORTAL, "Threshold gate lit");
            check(w.getBlockAt(g[0], g[1], g[2] - 1).getType() == Portals.FRAME, "Threshold gate framed in yellow glazed terracotta");
            // Protection: the terrain cannot be broken, a placed block can, a chest can.
            Block wall = w.getBlockAt(g[0], Level.FLOOR, g[2] - 1);
            check(!plugin.protect().mayBreak(null, wall), "terrain cannot be broken");
            Block air = w.getBlockAt(g[0] + 3, Level.WALK + 1, 5);
            plugin.protect().remember(air);
            check(plugin.protect().placed(air) && plugin.protect().mayBreak(null, air), "a placed block can be broken");
            plugin.protect().forget(air);
            check(!plugin.protect().placed(air), "breaking forgets the block");
            // Tiles: signs of the Threshold are written once its chunks are populated (a chunk populates when its neighbours exist).
            for (int cx = (g[0] >> 4) - 2; cx <= (g[0] >> 4) + 2; cx++) for (int cz = -3; cz <= 2; cz++) w.loadChunk(cx, cz, true);
            check(plugin.generator().signs > 0, "signs written (" + plugin.generator().signs + ")");
        }
        // Frame detection on a synthetic 4x5 frame (along x and along z), with and without its corners.
        for (boolean alongX : new boolean[] {true, false}) {
            Map<Long, Material> m = new HashMap<>();
            for (int a = -1; a <= 2; a++) for (int y = 0; y <= 4; y++) {
                boolean edge = a == -1 || a == 2 || y == 0 || y == 4, corner = (a == -1 || a == 2) && (y == 0 || y == 4);
                if (edge && !corner) m.put(Protect.key(alongX ? a : 0, y, alongX ? 0 : a), Portals.FRAME);
            }
            Portals.Blocks blocks = (x, y, z) -> m.getOrDefault(Protect.key(x, y, z), Material.AIR);
            Portals.Gate gate = Portals.detect("w", blocks, 0, 1, 0);
            check(gate != null && gate.w == 2 && gate.h == 3 && gate.xAxis == alongX, "frame detected " + (alongX ? "along x" : "along z"));
            m.remove(Protect.key(alongX ? 0 : 0, 4, 0));
            check(Portals.detect("w", blocks, 0, 1, 0) == null, "a broken frame is refused " + (alongX ? "along x" : "along z"));
        }
        // Items: every definition makes an item that is recognised again; every level has a full set, a weapon, a tool, two trinkets.
        for (Items.Def d : Items.DEFS.values()) {
            ItemStack s = Items.make(d);
            check(s != null && d.id.equals(Items.id(s)), "item " + d.id + " round-trips");
        }
        for (Level lv : Level.ALL) {
            int armour = 0, weapons = 0, tools = 0, trinkets = 0;
            for (Items.Def d : Items.of(lv)) { if (d.armour()) armour++; else if (d.kind == Items.Kind.WEAPON) weapons++; else if (d.kind == Items.Kind.TOOL) tools++; else if (d.kind == Items.Kind.TRINKET) trinkets++; }
            check(armour == 4 && weapons == 1 && tools == 1 && trinkets == 2, lv + " gear: " + armour + " armour, " + weapons + " weapon, " + tools + " tool, " + trinkets + " trinkets");
        }
        // Loot improves with danger (more items on average), and the level's gear appears.
        Random r = new Random(7);
        double near = 0, far = 0;
        int gearFar = 0;
        for (int i = 0; i < 300; i++) {
            near += Loot.roll(Level.YELLOW, 0.0, "room", r).size();
            List<ItemStack> deep = Loot.roll(Level.POOLS, 1.0, "pool", r);
            far += deep.size();
            for (ItemStack s : deep) { Items.Def d = Items.def(s); if (d != null && d.level == Level.POOLS) gearFar++; }
        }
        check(far > near * 1.4, "loot grows deeper in (" + Math.round(near / 3) / 100.0 + " -> " + Math.round(far / 3) / 100.0 + " items per chest)");
        check(gearFar > 60, "deep chests hold the level's gear (" + gearFar + "/300)");
        // The difficulty curve rises monotonically, and the Threshold stretch is peaceful.
        double last = -1;
        for (int i = 0; i <= 20; i++) { double gg = i / 20.0, h = Mobs.healthScale(gg) + Mobs.damageScale(gg) + Mobs.cap(gg, gg) + Mobs.chance(gg); check(h >= last, "difficulty rises at " + gg); last = h; }
        check(Mobs.sanctuary(Level.YELLOW) >= 64, "the Threshold stretch is peaceful");
        String line = "BACKROOMS_SELFTEST pass=" + passed + " fail=" + failures.size() + " ms=" + (System.nanoTime() - t0) / 1_000_000L;
        plugin.getLogger().info(line);
        for (String f : failures) plugin.getLogger().warning("BACKROOMS_SELFTEST_FAILED " + f);
        if (to != null) {
            to.sendMessage((failures.isEmpty() ? ChatColor.GREEN : ChatColor.RED) + line);
            for (String f : failures) to.sendMessage(ChatColor.RED + "  " + f);
        }
    }
}
