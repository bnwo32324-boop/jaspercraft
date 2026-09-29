package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/**
 * Where Drownhollow is dangerous (owner, 2026-09-29: "make the spawn point, when you go through the portal, virtually
 * safe, and as you venture out, it gets more and more dangerous. Most of the dangers should come from dungeons and not
 * from random spawns ... Cap the spawn rate even more. You're supposed to only be in danger when you go inside dungeons
 * or go inside structures. Spawn should be relatively peaceful.").
 * <ul>
 *   <li><b>Sanctuary</b>: within {@value #SAFE} blocks of a lit gate nothing hostile spawns (natural, cage or summoned),
 *       horrors that stray in are driven off and none will hunt a player there, the Dread ebbs away, no masonry falls
 *       and no chest ambush springs.</li>
 *   <li><b>The ramp</b>: danger {@code d} grows from 0 at the sanctuary's edge to 1 at {@value #FULL} blocks from the
 *       nearest gate (a world without gates is dangerous everywhere).</li>
 *   <li><b>The wilds</b> (open ground outside the old cities' walls, the ruin sites and the Great Door, with nothing
 *       overhead): no horror rises within about 150 blocks of a gate; beyond that one, and past about 310 blocks two,
 *       within {@value #WILD_RANGE} blocks of each other. The Dread only whispers there: it stops rising at
 *       {@value #WILD_DREAD}, so no sickness, blindness or shadows.</li>
 *   <li><b>Structures</b>: the open air of a ruin (inside an old city's walls, a ruin site, the Great Door) and anywhere
 *       roofed or underground (houses, crypts, vaults, the catacombs). This is where the danger lives: horrors rise
 *       there and from the spawner cages at strength {@code 0.25 + 0.75 d}, at most {@code 1 + 5 d} within 24 blocks
 *       of each other, and falling masonry and chest ambushes apply. Under a roof or underground the Dread is full
 *       (sickness, blindness, shadows); in a ruin's open air it stops at a whisper by the gates and reaches full far
 *       out.</li>
 * </ul>
 * The spawn decisions run in Paper's pre-spawn event, before any creature is made, so refusing costs almost nothing.
 */
final class Danger {
    static final int SAFE = 48, FULL = 448, WILD_DREAD = 54, WILD_RANGE = 96;
    /** Pre-spawn verdicts: spawn, skip this one creature, or stop the whole attempt (pack or cage cycle). */
    static final int ALLOW = 0, SKIP = 1, STOP = 2;

    /** Danger from the distance to the nearest gate: 0 up to the sanctuary's edge, 1 from {@link #FULL} blocks on. */
    static double ramp(double gateDistance) {
        return Math.max(0, Math.min(1, (gateDistance - SAFE) / (double) (FULL - SAFE)));
    }

    /** Share of a structure's dangers (spawn chance, cage rate, ambushes, masonry): a quarter at the sanctuary's edge. */
    static double strength(double d) { return 0.25 + 0.75 * d; }

    /** Wandering horrors allowed within {@link #WILD_RANGE} blocks of each other on open ground. */
    static int wanderers(double d) { return d < 0.25 ? 0 : d < 0.65 ? 1 : 2; }

    /** Horrors allowed within 24 blocks of a spot inside a structure before no more rise there: 1 by the gates, 6 far out. */
    static int crowdCap(double d) { return 1 + (int) (5 * d); }

    /** Horrors allowed within 12 blocks of a spawner cage before it stays quiet: 2 by the gates, 5 far out. */
    static int cageCap(double d) { return 2 + (int) (3 * d); }

    /** How high the Dread rises: a whisper on open ground, full under a roof or underground, by the ramp in a ruin's open air. */
    static int dreadCap(String place, double d) {
        if (place == null) return WILD_DREAD;
        if (COVERED.equals(place)) return 100;
        return WILD_DREAD + (int) Math.round((100 - WILD_DREAD) * d);
    }

    static final String COVERED = "covered";

    private final RuinsPlugin plugin;
    private final Random random = new Random();
    /** The world's horrors (bosses left out), gathered at most once per server tick for the caps. */
    private final List<double[]> spots = new ArrayList<>();
    private int spotsTick = Integer.MIN_VALUE;
    long refusedSanctuary, refusedWild, refusedStructure, refusedCage, allowedWild, allowedStructure, allowedCage, banished, calmed;

    Danger(RuinsPlugin plugin) { this.plugin = plugin; }

    String describe() {
        return "sanctuaryRefused=" + refusedSanctuary + " wildAllowed=" + allowedWild + " wildRefused=" + refusedWild + " structureAllowed=" + allowedStructure
            + " structureRefused=" + refusedStructure + " cageAllowed=" + allowedCage + " cageRefused=" + refusedCage + " banished=" + banished + " calmed=" + calmed;
    }

    /** Horizontal distance to the nearest lit gate in the spot's world (Double.MAX_VALUE with none). */
    double gateDistance(Location l) {
        return l.getWorld() == null ? Double.MAX_VALUE : plugin.portals().gateDistance(l.getWorld().getName(), l.getX(), l.getZ());
    }

    boolean sanctuary(Location l) { return gateDistance(l) < SAFE; }

    double level(Location l) { return ramp(gateDistance(l)); }

    /** Inside a structure: an old city's walls, a ruin site, the Great Door, or anything roofed or underground. */
    boolean inside(World w, int x, int y, int z) { return place(w, x, y, z) != null; }

    /** The kind of structure a spot is in: "covered" (roofed or underground), "door", "city" or "site"; null on open ground. */
    String place(World w, int x, int y, int z) {
        if (covered(w, x, y, z)) return COVERED;
        Plans plans = plugin.plans();
        if (plans.door().near(x, z, 0)) return "door";
        Plans.City c = plans.cityNear(x, z);
        if (c != null && c.outside(x, z) <= 0) return "city";
        if (plans.siteAt(x, z) != null) return "site";
        return null;
    }

    /** Something solid over the head (a roof, a vault, the rock over the catacombs); water alone is no roof. */
    static boolean covered(World w, int x, int y, int z) {
        int top = w.getHighestBlockYAt(x, z);
        if (top <= y + 2) return false;
        for (int yy = y + 2, end = Math.min(top, y + 48); yy < end; yy++)
            if (w.getBlockAt(x, yy, z).getType().isSolid()) return true;
        return false;
    }

    /** Where the player stands, in words (for /ruins danger and the tests). */
    String report(Location l) {
        double g = gateDistance(l), d = ramp(g);
        String place = g < SAFE ? null : place(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        return (g < SAFE ? "sanctuary" : place != null ? "structure:" + place : "wilds") + " danger=" + String.format(java.util.Locale.ROOT, "%.2f", d)
            + " dreadCap=" + (g < SAFE ? 0 : dreadCap(place, d))
            + " gate=" + (g == Double.MAX_VALUE ? "none" : String.valueOf((int) g)) + " wanderers=" + wanderers(d) + " crowd=" + crowdCap(d)
            + " cage=" + cageCap(d) + " strength=" + String.format(java.util.Locale.ROOT, "%.2f", strength(d)) + " horrorsWithin32=" + horrorsNear(l, 32, 16);
    }

    // ---- the spawn decisions -------------------------------------------------------------------------------------------

    /** A natural spawn: none in a sanctuary, a few wanderers far out, the rest inside structures by the ramp. */
    int natural(Location at) {
        double g = gateDistance(at);
        if (g < SAFE) { refusedSanctuary++; return STOP; }
        double d = ramp(g);
        if (inside(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ())) {
            if (random.nextDouble() >= strength(d) || horrorsNear(at, 24, 12) >= crowdCap(d)) { refusedStructure++; return STOP; }
            allowedStructure++;
        } else {
            int allowed = wanderers(d);
            if (allowed == 0 || horrorsNear(at, WILD_RANGE, 48) >= allowed) { refusedWild++; return STOP; }
            allowedWild++;
        }
        note(at);
        return ALLOW;
    }

    /** A spawner cage: quiet at the gates and when crowded; otherwise half its old rate far out, an eighth by the gates. */
    int cage(Location at) {
        double g = gateDistance(at);
        if (g < SAFE) { refusedSanctuary++; return STOP; }
        double d = ramp(g);
        if (horrorsNear(at, 12, 6) >= cageCap(d)) { refusedCage++; return STOP; }
        if (random.nextDouble() >= RuinsPlugin.EASE * strength(d)) { refusedCage++; return SKIP; }
        allowedCage++;
        note(at);
        return ALLOW;
    }

    /** Anything else hostile that appears on its own (a summoner's helpers): never near a gate, never into a crowd. */
    boolean summoned(Location at) {
        double g = gateDistance(at);
        if (g < SAFE) { refusedSanctuary++; return false; }
        if (horrorsNear(at, 24, 12) >= crowdCap(ramp(g))) { refusedStructure++; return false; }
        note(at);
        return true;
    }

    // ---- counting horrors ----------------------------------------------------------------------------------------------

    int horrorsNear(Location at, double range, double height) {
        double r2 = range * range;
        int n = 0;
        for (double[] s : spots(at.getWorld())) {
            double dx = s[0] - at.getX(), dz = s[2] - at.getZ();
            if (dx * dx + dz * dz <= r2 && Math.abs(s[1] - at.getY()) <= height) n++;
        }
        return n;
    }

    private List<double[]> spots(World w) {
        int now = net.minecraft.server.v1_12_R1.MinecraftServer.currentTick;
        if (now != spotsTick) {
            spotsTick = now;
            spots.clear();
            if (w != null)
                for (LivingEntity e : w.getLivingEntities()) {
                    if (e.isDead() || !Horrors.isHorror(e) || Bosses.isBoss(e)) continue;
                    Location l = e.getLocation();
                    spots.add(new double[] {l.getX(), l.getY(), l.getZ()});
                }
        }
        return spots;
    }

    /** A horror about to rise here counts at once, so a burst in one tick respects the caps. */
    private void note(Location at) { spots(at.getWorld()).add(new double[] {at.getX(), at.getY(), at.getZ()}); }
}
