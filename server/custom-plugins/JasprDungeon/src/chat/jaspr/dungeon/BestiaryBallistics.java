package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;
import java.util.*;

/**
 * Generation 7 (owner 2026-10-05: "I want some enemies to be equipped with guns that actively shoot at you"): the gunners' rules
 * as pure math, so every promise can be audited without a server. A shot is a ray through the room (stopped by solid blocks and by
 * the room's own edge, never through a wall, never into another room) that hits the first player's bounding box on it; a gunner
 * aims with a visible laser for 0.6 to 1.0 seconds, locks, fires, keeps a magazine and pauses to reload. No Bukkit, no clocks, no
 * random state: callers pass the numbers (u, v, seeds) that a real shot draws.
 */
public final class BestiaryBallistics {
    private BestiaryBallistics() {}
    /** The most one round may take from a player: a gunner is dangerous in groups, never a one-shot. */
    public static final double MAX_SHOT = 9.0;
    /** Aim time bounds (ticks): 0.6 to 1.0 s; the last MIN_LOCK or more ticks of it the laser is locked and the shot can be sidestepped. */
    public static final int MIN_AIM = 12, MAX_AIM = 20, MIN_LOCK = 5, MAX_LOCK = 8;
    /** Rounds of a burst are this many ticks apart, as the player's own repeater fires them. */
    public static final int BURST_GAP = 11;
    /** No shot, and no laser, is longer than this (blocks). */
    public static final double MAX_RANGE = 28;

    /** Whether the block at these coordinates stops a shot (the caller treats unloaded chunks as solid). */
    public interface Solid { boolean at(int x, int y, int z); }

    /** How one species trains its gun: which guns it may carry, its timings (ticks) and the numbers of its volley. */
    public static final class Profile {
        public final Species species;
        public final ArmoryCatalog.Type[] guns;
        public final int minFloor, aim, lock, magazine, reload, gap;
        public final double range, retreat, damage, spread;
        Profile(Species species, ArmoryCatalog.Type[] guns, int minFloor, int aim, int lock, int magazine, int reload, int gap,
                double range, double retreat, double damage, double spread) {
            this.species = species; this.guns = guns; this.minFloor = minFloor; this.aim = aim; this.lock = lock; this.magazine = magazine;
            this.reload = reload; this.gap = gap; this.range = range; this.retreat = retreat; this.damage = damage; this.spread = spread;
        }
        public int aim(boolean rally) { return rally ? Math.max(MIN_AIM, (int) Math.round(aim * .8)) : aim; }
        public int gap(boolean rally) { return rally ? Math.max(18, (int) Math.round(gap * .8)) : gap; }
        public ArmoryCatalog.Type gun(long hash) { return guns[(int) Math.floorMod(Layout.mix(hash ^ 0x47756e73L), guns.length)]; }
        /** The reach of one shot with this gun: the profile's range, never more than the gun's own or {@link #MAX_RANGE}. */
        public double range(ArmoryCatalog.Type gun) { return Math.min(MAX_RANGE, Math.min(range, gun.range)); }
    }
    private static final Map<Species, Profile> PROFILES = new EnumMap<Species, Profile>(Species.class);
    static {
        // Floor II: one accurate round at a time, slow to aim (a full second), long to reload.
        PROFILES.put(Species.TUNNEL_GUNNER, new Profile(Species.TUNNEL_GUNNER,
            new ArmoryCatalog.Type[]{ArmoryCatalog.Type.BAPTIST_NEEDLER, ArmoryCatalog.Type.MENAGERIE_DARTER, ArmoryCatalog.Type.SCRIPTORIUM_SIPHON},
            2, 20, 7, 6, 70, 34, 22, 5.5, 2.4, 2.2));
        // Floor III: the faster, harder guns; the captain's volleys are the player's own repeater, scattergun and orrery.
        PROFILES.put(Species.ABYSSAL_GUNSLINGER, new Profile(Species.ABYSSAL_GUNSLINGER,
            new ArmoryCatalog.Type[]{ArmoryCatalog.Type.LABYRINTH_DOUBLE, ArmoryCatalog.Type.PAUPERS_RICOCHET, ArmoryCatalog.Type.VESPER_DRILL, ArmoryCatalog.Type.FOUNDRY_CANNON},
            3, 16, 6, 6, 62, 26, 26, 5.5, 2.7, 1.4));
        PROFILES.put(Species.SQUAD_CAPTAIN, new Profile(Species.SQUAD_CAPTAIN,
            new ArmoryCatalog.Type[]{ArmoryCatalog.Type.BELFRY_REPEATER, ArmoryCatalog.Type.CARRION_SCATTERGUN, ArmoryCatalog.Type.ASTRAL_ORRERY},
            3, 15, 6, 8, 80, 30, 20, 5.0, 2.5, 1.8));
    }
    /** The gun profile of a gunner species, or null for every other. */
    public static Profile profile(Species s) { return PROFILES.get(s); }
    public static Collection<Profile> profiles() { return Collections.unmodifiableCollection(PROFILES.values()); }

    // ---------------------------------------------------------------- damage
    private static double round(Profile p, int floor, int tier, boolean rally, double factor) {
        return p.damage * Floors.damage(floor) * (1 + .06 * Math.max(0, Math.min(5, tier))) * (rally ? 1.2 : 1) * factor;
    }
    /** What one ray of one round takes from a player, before armour: split over the rays and bursts of the gun in hand. */
    public static double perRay(Profile p, ArmoryCatalog.Type gun, int floor, int tier, boolean rally, double factor) {
        double v = round(p, floor, tier, rally, factor) / (Math.sqrt(Math.max(1, gun.rays)) * Math.sqrt(Math.max(1, gun.burst)));
        return Math.max(1, Math.min(MAX_SHOT, v));
    }
    /** The most one round (all of its rays together) takes from a player. */
    public static double roundCap(Profile p, int floor, int tier, boolean rally, double factor) {
        return Math.max(1, Math.min(MAX_SHOT, 1.6 * round(p, floor, tier, rally, factor)));
    }

    // ---------------------------------------------------------------- directions
    public static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return l < 1e-9 ? new double[]{1, 0, 0} : new double[]{x / l, y / l, z / l};
    }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    /** A right-handed frame around dir: {dir, right, up}. */
    private static double[][] frame(double[] dir) {
        double[] f = unit(dir[0], dir[1], dir[2]), right = cross(f, new double[]{0, 1, 0});
        right = Math.sqrt(right[0] * right[0] + right[1] * right[1] + right[2] * right[2]) < 1e-8 ? new double[]{1, 0, 0} : unit(right[0], right[1], right[2]);
        double[] up = cross(right, f);
        return new double[][]{f, right, unit(up[0], up[1], up[2])};
    }
    /** dir turned yaw degrees to the right and pitch degrees up: the offsets ArmoryCatalog.spread gives a scattergun's pellets or an orrery's cross. */
    public static double[] turn(double[] dir, double yaw, double pitch) {
        double[][] f = frame(dir);
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw)), cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
        return unit(f[0][0] * cy * cp + f[1][0] * sy * cp + f[2][0] * sp, f[0][1] * cy * cp + f[1][1] * sy * cp + f[2][1] * sp, f[0][2] * cy * cp + f[1][2] * sy * cp + f[2][2] * sp);
    }
    /** A direction at most degrees from dir: (u, v) in [0, 1) pick a point of the cone's disc, evenly by area. */
    public static double[] cone(double[] dir, double degrees, double u, double v) {
        double[][] f = frame(dir);
        if (degrees <= 0) return f[0];
        double a = Math.toRadians(degrees) * Math.sqrt(Math.max(0, Math.min(1, u))), phi = 2 * Math.PI * v, s = Math.sin(a), c = Math.cos(a);
        return unit(f[0][0] * c + (f[1][0] * Math.cos(phi) + f[2][0] * Math.sin(phi)) * s,
                    f[0][1] * c + (f[1][1] * Math.cos(phi) + f[2][1] * Math.sin(phi)) * s,
                    f[0][2] * c + (f[1][2] * Math.cos(phi) + f[2][2] * Math.sin(phi)) * s);
    }
    /** A player's bounding box as {minX, minY, minZ, maxX, maxY, maxZ}: the real 0.6 by 1.8 (1.65 crouched) with a tenth of forgiveness. */
    public static double[] box(double x, double y, double z, boolean sneaking) {
        return new double[]{x - .4, y, z - .4, x + .4, y + (sneaking ? 1.7 : 1.9), z + .4};
    }

    // ---------------------------------------------------------------- the ray
    /** True when the cell is outside the room (its walls and doorways included), below its floor or at its roof, or solid. */
    private static boolean blocked(Solid s, Layout.Room r, int x, int y, int z) {
        boolean inside = x >= r.x + 1 && x < r.x + r.w - 1 && z >= r.z + 1 && z < r.z + r.d - 1 && y >= Layout.FLOOR + 1 && y < r.roof();
        return !inside || s.at(x, y, z);
    }
    /**
     * How far a ray from (ox, oy, oz) along dir travels before a solid cell or the room's edge stops it, at most range: a voxel
     * walk, exact for any direction and any sign of coordinates. A ray that starts inside a stopping cell travels 0.
     */
    public static double reach(Solid s, Layout.Room r, double ox, double oy, double oz, double[] dir, double range) {
        double[] d = unit(dir[0], dir[1], dir[2]);
        double max = Math.min(MAX_RANGE, range);
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        if (max <= 0 || blocked(s, r, x, y, z)) return 0;
        int sx = d[0] > 0 ? 1 : d[0] < 0 ? -1 : 0, sy = d[1] > 0 ? 1 : d[1] < 0 ? -1 : 0, sz = d[2] > 0 ? 1 : d[2] < 0 ? -1 : 0;
        double inf = Double.POSITIVE_INFINITY;
        double tx = sx == 0 ? inf : (sx > 0 ? x + 1 - ox : ox - x) / Math.abs(d[0]), ty = sy == 0 ? inf : (sy > 0 ? y + 1 - oy : oy - y) / Math.abs(d[1]),
               tz = sz == 0 ? inf : (sz > 0 ? z + 1 - oz : oz - z) / Math.abs(d[2]);
        double dx = sx == 0 ? inf : 1 / Math.abs(d[0]), dy = sy == 0 ? inf : 1 / Math.abs(d[1]), dz = sz == 0 ? inf : 1 / Math.abs(d[2]);
        for (int i = 0; i < 400; i++) {
            double t;
            if (tx <= ty && tx <= tz) { t = tx; x += sx; tx += dx; }
            else if (ty <= tz) { t = ty; y += sy; ty += dy; }
            else { t = tz; z += sz; tz += dz; }
            if (t >= max) return max;
            if (blocked(s, r, x, y, z)) return t;
        }
        return max;
    }
    /** What a shot met: the distance it travelled, which box it hit (an index into the boxes given, -1 for none) and whether a wall or the room's edge stopped it short. */
    public static final class Hit {
        public final double dist;
        public final int target;
        public final boolean wall;
        Hit(double dist, int target, boolean wall) { this.dist = dist; this.target = target; this.wall = wall; }
    }
    /** A shot from o along dir: the first box (as {@link #box}) on the ray before a wall, the room's edge or the range. */
    public static Hit trace(Solid s, Layout.Room r, double[] o, double[] dir, double range, double[][] boxes) {
        double[] d = unit(dir[0], dir[1], dir[2]);
        double limit = reach(s, r, o[0], o[1], o[2], d, range);
        int best = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < boxes.length; i++) {
            double t = ArmoryCatalog.rayBox(o, d, new double[]{boxes[i][0], boxes[i][1], boxes[i][2]}, new double[]{boxes[i][3], boxes[i][4], boxes[i][5]}, limit);
            if (t < bestT) { bestT = t; best = i; }
        }
        return best < 0 ? new Hit(limit, -1, limit < Math.min(MAX_RANGE, range) - 1e-9) : new Hit(bestT, best, false);
    }
    /** Whether nothing solid lies between two points (a gunner only aims at what it can see). */
    public static boolean sees(Solid s, Layout.Room r, double[] from, double[] to) {
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return len < .3 || reach(s, r, from[0], from[1], from[2], new double[]{dx, dy, dz}, Math.min(MAX_RANGE, len)) >= len - .3;
    }

    // ---------------------------------------------------------------- Deep Miner's charges
    /** EntityTNTPrimed's flight: gravity and drag every tick. */
    public static final double TNT_GRAVITY = .04, TNT_DRAG = .98;
    /** Where a charge launched with velocity v (blocks per tick) is after n ticks, relative to its start, in open air. */
    public static double[] fly(double[] v, int ticks) {
        double x = 0, y = 0, z = 0, vx = v[0], vy = v[1], vz = v[2];
        for (int k = 0; k < ticks; k++) { vy -= TNT_GRAVITY; x += vx; y += vy; z += vz; vx *= TNT_DRAG; vy *= TNT_DRAG; vz *= TNT_DRAG; }
        return new double[]{x, y, z};
    }
    /** The launch velocity that lands a charge (dx, dy, dz) away after exactly ticks ticks of flight. */
    public static double[] lob(double dx, double dy, double dz, int ticks) {
        int t = Math.max(1, ticks);
        double sum = (1 - Math.pow(TNT_DRAG, t)) / (1 - TNT_DRAG), s0 = fly(new double[]{0, 0, 0}, t)[1];
        return new double[]{dx / sum, (dy - s0) / sum, dz / sum};
    }
    /** The highest point of a flight relative to its start. */
    public static double apex(double[] v, int ticks) {
        double best = 0, y = 0, vy = v[1];
        for (int k = 0; k < ticks; k++) { vy -= TNT_GRAVITY; y += vy; vy *= TNT_DRAG; best = Math.max(best, y); }
        return best;
    }
    /** How many ticks a throw of this length flies: longer throws take longer, between 14 and 30. */
    public static int flight(double distance) { return Math.max(14, Math.min(30, 10 + (int) Math.round(distance * 1.1))); }

    // ---------------------------------------------------------------- the trigger discipline
    /** A gunner's cycle as a state machine: it never fires without aiming first, aims 0.6 to 1.0 s, spends rounds and reloads. */
    public static final class Cycle {
        public enum Phase { READY, AIM, LOCKED, BURST, RELOAD }
        public enum Event { NONE, AIM_START, LOCK, FIRE, RELOAD_START, RELOAD_END, ABORT }
        public final Profile profile;
        public final int burst;
        public Phase phase = Phase.READY;
        /** Rounds left in the magazine. */
        public int rounds;
        private int inBurst;
        private long nextAt, phaseEnd, lockAt, fireAt, reloadEnd, seed;
        public Cycle(Profile profile, ArmoryCatalog.Type gun, long now, long seed) {
            this.profile = profile; this.seed = seed; burst = Math.max(1, Math.min(3, gun.burst)); rounds = profile.magazine;
            nextAt = now + 10 + jitter(10);
        }
        private int jitter(int n) { seed = Layout.mix(seed + 0x9e3779b97f4a7c15L); return (int) Math.floorMod(seed >>> 7, (long) n); }
        /** Holds a place among the gunners that may aim at once: from the first moment of aiming to the last round of the volley. */
        public boolean aiming() { return phase == Phase.AIM || phase == Phase.LOCKED || phase == Phase.BURST; }
        /** True while the laser still follows its target; false once it is locked. */
        public boolean tracking() { return phase == Phase.AIM; }
        /**
         * One tick. canAim: a target in range that the gunner can see; tokenFree: a place among the aiming gunners is free (the
         * gunner that already aims keeps its place); rally: a captain's rally is on. Returns what to do this tick.
         */
        public Event step(long now, boolean canAim, boolean tokenFree, boolean rally) {
            switch (phase) {
                case READY:
                    if (rounds <= 0) { phase = Phase.RELOAD; reloadEnd = now + profile.reload; return Event.RELOAD_START; }
                    if (now < nextAt || !canAim || !tokenFree) return Event.NONE;
                    phase = Phase.AIM; phaseEnd = now + profile.aim(rally); lockAt = phaseEnd - profile.lock;
                    return Event.AIM_START;
                case AIM:
                    if (!canAim) { phase = Phase.READY; nextAt = now + 8; return Event.ABORT; }
                    if (now >= lockAt) { phase = Phase.LOCKED; return Event.LOCK; }
                    return Event.NONE;
                case LOCKED:
                    if (now < phaseEnd) return Event.NONE;
                    inBurst = Math.min(burst, rounds); fireAt = now; phase = Phase.BURST;
                    return burstStep(now, rally);
                case BURST:
                    return burstStep(now, rally);
                case RELOAD:
                    if (now < reloadEnd) return Event.NONE;
                    rounds = profile.magazine; phase = Phase.READY; nextAt = Math.max(nextAt, now + 10);
                    return Event.RELOAD_END;
                default:
                    return Event.NONE;
            }
        }
        private Event burstStep(long now, boolean rally) {
            if (now < fireAt) return Event.NONE;
            rounds--; inBurst--;
            if (inBurst <= 0) { phase = Phase.READY; nextAt = now + profile.gap(rally) + jitter(10); }
            else fireAt = now + BURST_GAP;
            return Event.FIRE;
        }
        /** Cancels everything and starts over with a full magazine (a mob that was moved or resurrected). */
        public void reset(long now) { phase = Phase.READY; rounds = profile.magazine; nextAt = now + 10; inBurst = 0; }
    }
}
