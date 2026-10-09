package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffectType;

/**
 * Generation 7 (owner 2026-10-05: "I want some enemies to be equipped with guns that actively shoot at you. I'll let you decide
 * when and where to place these enemies, as I don't think it's appropriate to place them in the easier stages"): the world half
 * of the gunners. A gunner holds a real dungeon gun (Arsenal's own item, so the client draws the gun), keeps its distance (backs
 * away from a player within about five blocks, strafes, reloads out of reach), aims with a visible laser for 0.6 to 1.0 seconds,
 * locks the laser (the last third of the aim: sidestep now) and fires a hitscan shot along it, with a muzzle flash and a gun
 * sound. The shot is a ray through the room that walls and the room's edge stop (BestiaryBallistics), so it never goes through
 * a wall or into another room, and it hurts the first eligible player's box on it by a modest, floor-scaled amount. The timing
 * (aim, lock, burst, magazine, reload) is BestiaryBallistics.Cycle; a few gunners aim at once per room (BestiaryCatalog.aimCap) and
 * the rest take position, so a squad is dangerous without being a wall of lasers.
 */
final class Gunners {
    private final Bestiary b;
    Gunners(Bestiary b) { this.b = b; }

    /** One gunner's gun and aim. */
    static final class State {
        final BestiaryBallistics.Profile profile;
        final ArmoryCatalog.Type gun;
        /** Made on the first step (its clock starts then). */
        BestiaryBallistics.Cycle cycle;
        /** The player it sees and aims at, the nearest player in reach, and whether it sees anyone. */
        UUID seen, near;
        boolean sees, arms;
        /** The aim point (a player's chest) the laser follows, and is locked on. */
        double ax, ay, az;
        long rallyUntil, strafeUntil, navAt;
        int strafe = 1;
        State(BestiaryBallistics.Profile profile, ArmoryCatalog.Type gun) { this.profile = profile; this.gun = gun; }
    }

    /**
     * Gives a gunner its gun, or null when this room may not have one (never Floor I, never beside Floor II's arrival, never above
     * the room's gunner cap): that mob fights with its sword, like any other.
     */
    State attach(Bestiary.Mob m, Encounters.Run run, Bestiary.RunData rd, double depth) {
        BestiaryBallistics.Profile p = BestiaryBallistics.profile(m.sp);
        int floor = run.room.floor, tier = run.room.tier;
        EntityEquipment eq = m.e.getEquipment();
        if (p == null || eq == null || floor < p.minFloor || !BestiaryCatalog.gunsAllowed(floor, depth, tier) || rd.armed >= BestiaryCatalog.gunnerCap(floor, tier)) return null;
        ArmoryCatalog.Type gun = p.gun(run.room.hash ^ (m.slot * 0x632be59bd9b4e019L));
        eq.setItemInMainHand(BestiaryNms.gun(gun));
        eq.setItemInMainHandDropChance(0);
        return new State(p, gun);
    }

    private static double sq(double v) { return v * v; }
    private static Player find(List<Player> ps, UUID id) {
        if (id != null) for (Player p : ps) if (p.getUniqueId().equals(id)) return p;
        return null;
    }
    /** A place among the gunners that aim at once: those already aiming keep theirs. */
    private boolean tokenFree(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob me) {
        int n = 0;
        for (Bestiary.Mob o : rd.mobs.values()) if (o != me && o.gun != null && o.gun.cycle != null && o.gun.cycle.aiming()) n++;
        return n < BestiaryCatalog.aimCap(run.room.floor, run.room.tier);
    }

    /** One tick of a gunner: see, aim, lock, fire, reload, and move. */
    void step(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        State g = m.gun;
        LivingEntity e = m.e;
        World w = run.world;
        if (g.cycle == null) g.cycle = new BestiaryBallistics.Cycle(g.profile, g.gun, t, m.id.getLeastSignificantBits() ^ run.room.hash);
        // Encounters points every mob at the nearest player each tick; a gunner's AI must not hunt, so the target goes again.
        if (e instanceof Creature) ((Creature) e).setTarget(null);
        double range = g.profile.range(g.gun);
        double[] from = Bestiary.eye(e);
        from[1] -= .15;
        Player tp = find(ps, g.seen);
        // Sight is looked at four times a second (staggered); the aim itself follows its player every tick.
        if ((t + Math.floorMod(m.id.getLeastSignificantBits(), 4L)) % 4 == 0 || (g.seen != null && tp == null)) {
            if (g.cycle.aiming() && tp != null) {
                double[] c = Bestiary.chest(tp);
                g.sees = sq(c[0] - from[0]) + sq(c[1] - from[1]) + sq(c[2] - from[2]) <= range * range && b.visible(run, from, c);
            } else {
                Player seen = null, near = null;
                double bs = range * range, bn = sq(range + 4);
                for (Player p : ps) {
                    double[] c = Bestiary.chest(p);
                    double d2 = sq(c[0] - from[0]) + sq(c[1] - from[1]) + sq(c[2] - from[2]);
                    if (d2 < bn) { bn = d2; near = p; }
                    if (d2 <= bs && b.visible(run, from, c)) { bs = d2; seen = p; }
                }
                g.seen = seen == null ? null : seen.getUniqueId();
                g.near = near == null ? null : near.getUniqueId();
                g.sees = seen != null;
                tp = seen;
            }
        }
        BestiaryBallistics.Cycle.Event ev = g.cycle.step(t, g.sees && tp != null, tokenFree(run, rd, m), t < g.rallyUntil);
        double[] mouth = {from[0], from[1], from[2]};
        switch (ev) {
            case AIM_START:
                rd.m.add("aims");
                b.sound(w, mouth[0], mouth[1], mouth[2], Sound.BLOCK_DISPENSER_FAIL, .7f, 1.8f);
                break;
            case LOCK:
                b.sound(w, mouth[0], mouth[1], mouth[2], Sound.BLOCK_NOTE_PLING, .8f, 2f);
                break;
            case FIRE:
                fire(run, rd, m, g, ps, t);
                break;
            case RELOAD_START:
                rd.m.add("reloads");
                b.sound(w, mouth[0], mouth[1], mouth[2], Sound.BLOCK_PISTON_CONTRACT, .7f, 1.5f);
                break;
            case RELOAD_END:
                b.sound(w, mouth[0], mouth[1], mouth[2], Sound.BLOCK_PISTON_EXTEND, .7f, 1.9f);
                break;
            default:
                break;
        }
        // The laser: red while it follows its player, yellow once locked.
        if (g.cycle.tracking() && tp != null) {
            double[] c = Bestiary.chest(tp);
            g.ax = c[0]; g.ay = c[1]; g.az = c[2];
            BestiaryNms.face(e, tp);
            if (t % 3 == 0) laser(run, g, from, false);
        } else if (g.cycle.phase == BestiaryBallistics.Cycle.Phase.LOCKED || g.cycle.phase == BestiaryBallistics.Cycle.Phase.BURST) {
            if (tp != null) BestiaryNms.face(e, tp);
            if (t % 2 == 0) laser(run, g, from, true);
        }
        boolean up = g.cycle.aiming();
        if (up != g.arms) { g.arms = up; BestiaryNms.arms(e, up); }
        move(run, m, g, tp, find(ps, g.near), t);
    }

    /** A dotted line along the aim, ending where a shot would be stopped. */
    private void laser(Encounters.Run run, State g, double[] o, boolean locked) {
        double[] d = BestiaryBallistics.unit(g.ax - o[0], g.ay - o[1], g.az - o[2]);
        double reach = BestiaryBallistics.reach(b.solid(run.world), run.room, o[0], o[1], o[2], d, g.profile.range(g.gun));
        double r = 1, gg = locked ? .85 : .05, bb = locked ? .2 : .05;
        for (double s = 1.2; s <= reach; s += 1.5) run.world.spawnParticle(Particle.REDSTONE, o[0] + d[0] * s, o[1] + d[1] * s, o[2] + d[2] * s, 0, r, gg, bb, 1);
    }

    /** One round: a ray per pellet of the gun in hand, the first eligible player's box on each, one blow per player hit. */
    private void fire(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, State g, List<Player> ps, long t) {
        World w = run.world;
        LivingEntity e = m.e;
        boolean rally = t < g.rallyUntil;
        double[] eye = Bestiary.eye(e);
        double[] dir0 = BestiaryBallistics.unit(g.ax - eye[0], g.ay - (eye[1] - .15), g.az - eye[2]);
        double[] o = {eye[0] + dir0[0] * .5, eye[1] - .2 + dir0[1] * .5, eye[2] + dir0[2] * .5};
        double range = g.profile.range(g.gun);
        double[][] boxes = new double[ps.size()][];
        for (int i = 0; i < boxes.length; i++) { Location l = ps.get(i).getLocation(); boxes[i] = BestiaryBallistics.box(l.getX(), l.getY(), l.getZ(), ps.get(i).isSneaking()); }
        int floor = run.room.floor, tier = run.room.tier;
        double ray = BestiaryBallistics.perRay(g.profile, g.gun, floor, tier, rally, m.dmg), cap = BestiaryBallistics.roundCap(g.profile, floor, tier, rally, m.dmg);
        BestiaryBallistics.Solid solid = b.solid(w);
        double[] sums = new double[ps.size()];
        for (int r = 0; r < g.gun.rays; r++) {
            double[] off = ArmoryCatalog.spread(g.gun, r);
            long h = Layout.mix(m.id.getMostSignificantBits() ^ (t * 0x9e3779b97f4a7c15L) ^ (r * 0x632be59bd9b4e019L));
            double[] d = BestiaryBallistics.cone(BestiaryBallistics.turn(dir0, off[0], off[1]), g.profile.spread, (h & 0xffffff) / 16777216.0, ((h >>> 24) & 0xffffff) / 16777216.0);
            BestiaryBallistics.Hit hit = BestiaryBallistics.trace(solid, run.room, o, d, range, boxes);
            for (double s = 1.2; s <= hit.dist; s += 1.4) w.spawnParticle(Particle.CRIT, o[0] + d[0] * s, o[1] + d[1] * s, o[2] + d[2] * s, 1, 0, 0, 0, 0);
            if (hit.target >= 0) sums[hit.target] += ray;
        }
        w.spawnParticle(Particle.FLAME, o[0], o[1], o[2], 4, .06, .06, .06, .03);
        w.spawnParticle(Particle.SMOKE_NORMAL, o[0], o[1], o[2], 3, .08, .08, .08, .01);
        b.sound(w, o[0], o[1], o[2], Sound.ENTITY_GENERIC_EXPLODE, .35f, 1.9f);
        b.sound(w, o[0], o[1], o[2], Sound.ENTITY_ARROW_SHOOT, .8f, 1.7f);
        rd.m.add("shots");
        for (int i = 0; i < sums.length; i++) {
            if (sums[i] <= 0) continue;
            Player p = ps.get(i);
            rd.m.add("hits");
            if (b.hurtExact(run, m, p, Math.min(cap, sums[i]))) rider(p, g.gun, e.getLocation());
        }
    }
    /** What a gun's own mechanic adds to a round that hurt: the same small riders the player's gun has, none lasting. */
    private void rider(Player p, ArmoryCatalog.Type gun, Location from) {
        switch (gun.mechanic) {
            case CHILL: b.status(p, PotionEffectType.SLOW, 40, 0); break;
            case DRILL: b.status(p, PotionEffectType.WEAKNESS, 40, 0); break;
            case IMPACT: b.knock(p, from, .7, .2); break;
            default: break;
        }
    }

    // ---------------------------------------------------------------- footwork
    private void move(Encounters.Run run, Bestiary.Mob m, State g, Player aimed, Player near, long t) {
        LivingEntity e = m.e;
        BestiaryBallistics.Cycle.Phase ph = g.cycle.phase;
        // Feet planted for the shot: the laser is locked and the player may sidestep it.
        if (ph == BestiaryBallistics.Cycle.Phase.LOCKED || ph == BestiaryBallistics.Cycle.Phase.BURST) { if (t % 4 == 0) BestiaryNms.stop(e); return; }
        Player who = aimed != null ? aimed : near;
        if (who == null) return;
        Location l = e.getLocation(), pl = who.getLocation();
        double dx = l.getX() - pl.getX(), dz = l.getZ() - pl.getZ(), d = Math.hypot(dx, dz);
        double keep = g.profile.retreat + (ph == BestiaryBallistics.Cycle.Phase.RELOAD ? 3 : 0), range = g.profile.range(g.gun);
        if (d < keep) {
            if (t < g.navAt) return;
            if (retreat(run, e, dx, dz, d)) { g.navAt = t + 8; return; }
            side(m, g, who, t);
        } else if (aimed == null || d > range * .85) {
            // Out of sight or out of reach: close in, but never inside its own comfort distance.
            if (t >= g.navAt && d > keep + 2) { BestiaryNms.walk(e, pl.getX(), pl.getY(), pl.getZ(), 1.0); g.navAt = t + 12; }
        } else if (t >= g.navAt) side(m, g, who, t);
    }
    /** Back away from the player: the first free, standable spot a few blocks away (straight back, then to either side), kept well inside the room. */
    private boolean retreat(Encounters.Run run, LivingEntity e, double dx, double dz, double d) {
        Location l = e.getLocation();
        double ux = d < 1e-6 ? 1 : dx / d, uz = d < 1e-6 ? 0 : dz / d;
        for (double len : new double[]{8, 6, 4}) for (double a : new double[]{0, .6, -.6, 1.2, -1.2}) {
            double c = Math.cos(a), s = Math.sin(a), x = l.getX() + (ux * c - uz * s) * len, z = l.getZ() + (ux * s + uz * c) * len;
            if (!BestiaryCatalog.inside(run.room, x, z, 4.5) || !b.ground(run.world, x, l.getY(), z)) continue;
            if (BestiaryNms.walk(e, x, l.getY(), z, 1.2)) return true;
        }
        return false;
    }
    /** Sidestep while facing the player (the bow skeleton's strafe), changing direction every second or two. */
    private void side(Bestiary.Mob m, State g, Player who, long t) {
        if (t >= g.strafeUntil) { g.strafe = -g.strafe; g.strafeUntil = t + 30 + Math.floorMod(Layout.mix(m.id.getLeastSignificantBits() ^ t), 30L); }
        BestiaryNms.strafe(m.e, 0f, g.strafe * .9f);
        BestiaryNms.face(m.e, who);
    }
}
