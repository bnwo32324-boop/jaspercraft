package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Generation 7 (owner 2026-10-05: "a plethora of mobs"): what each custom species does. Every ability follows one pattern: wait
 * for its cooldown, see a player inside its band and in sight, show a warning (particles and a sound) for its windup, then act
 * once on what is there at that moment, so the warning can be walked out of. Everything stays inside the room, hurts only
 * eligible players (through Bestiary.hurt, so a damage event with the mob as the attacker) and is bounded: a charge, a
 * patch, a fang or an add is counted and capped, and Bestiary removes what is left when the room sleeps. The numbers are
 * BestiaryCatalog's. Per-mob fields: stage 0 waiting, 1 warning, 2 acting, 3 recovering; nextAt the next trigger, actAt when
 * the warning ends, (ax, ay, az) a spot or direction locked at the start of the warning.
 */
final class BestiaryAbilities {
    private static final int PATCHES = 24, THINGS = 24, CHARGES = 6;
    private final Bestiary b;
    BestiaryAbilities(Bestiary b) { this.b = b; }

    void step(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        switch (m.def.kind) {
            case LASH: lash(run, rd, m, ps, t); break;
            case FLIT: flit(run, rd, m, ps, t); break;
            case SCREAM: scream(run, rd, m, ps, t); break;
            case DRAIN: drain(run, rd, m, ps, t); break;
            case GUARD: guard(m, t); break;
            case EMBERS: embers(run, rd, m, t); break;
            case POUNCE: pounce(run, rd, m, ps, t); break;
            case BLAST: blast(run, rd, m, ps, t); break;
            case SLAM: slam(run, rd, m, ps, t); break;
            case PACK: pack(run, rd, m, t); break;
            case BASH: bash(run, rd, m, ps, t); break;
            case BLINK: blink(run, rd, m, ps, t); break;
            case MEND: mend(run, rd, m, t); break;
            case SUMMON: summon(run, rd, m, ps, t); break;
            case RALLY: rally(run, rd, m, ps, t); break;
            default: break;
        }
    }

    /** The room was empty for a while: a sentinel that was recovering with its shield down raises it again. */
    void rested(Bestiary.Mob m) {
        if (m.def.kind == BestiaryCatalog.Kind.BASH && !m.up) { m.up = true; shield(m); }
    }

    private static double flat(Location a, Location c) { return Math.hypot(a.getX() - c.getX(), a.getZ() - c.getZ()); }
    private boolean sees(Encounters.Run run, Bestiary.Mob m, Player p) { return b.visible(run, Bestiary.eye(m.e), Bestiary.chest(p)); }

    // ---------------------------------------------------------------- Floor I
    /** Flagellant: a whip along a line four blocks long, locked when the warning starts; step off the line. */
    private void lash(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        double reach = (d.far + .2) * Math.min(1.8, b.scale(m.e));
        if (m.stage == 1) {
            double ex = l.getX() + m.ax * reach, ez = l.getZ() + m.az * reach;
            if (t % 2 == 0) b.line(run.world, Particle.CRIT, l.getX(), l.getY() + 1.1, l.getZ(), ex, l.getY() + .9, ez, .7);
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            b.sound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, .6f);
            rd.m.add("ability.lashes");
            for (Player p : ps) {
                Location pl = p.getLocation();
                if (Math.abs(pl.getY() - l.getY()) > 2.2 || BestiaryCatalog.segment(pl.getX(), pl.getZ(), l.getX(), l.getZ(), ex, ez) > d.radius) continue;
                if (b.hurt(run, m, p, d.damage * m.base)) b.knock(p, l, .55, .3);
            }
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far + 1.5);
        if (p == null) return;
        Location pl = p.getLocation();
        double dx = pl.getX() - l.getX(), dz = pl.getZ() - l.getZ(), len = Math.hypot(dx, dz);
        if (len < d.near || len > d.far || !sees(run, m, p)) return;
        m.ax = dx / len; m.az = dz / len; m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, .7f, .5f);
    }

    /** Candle Wisp: hops to a marked spot beside its target after a short flicker (the fire it sets is Bestiary.onHit). */
    private void flit(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            if (t % 3 == 0) { b.puff(w, Particle.FLAME, m.ax, m.ay, m.az, 2, .25, .25, .25, .01); b.ring(w, Particle.SMOKE_NORMAL, m.ax, m.ay - .3, m.az, .6, 6); }
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            if (!b.free(w, m.ax, m.ay, m.az)) return;
            b.puff(w, Particle.SMOKE_LARGE, l.getX(), l.getY() + .5, l.getZ(), 6, .2, .2, .2, .01);
            e.teleport(new Location(w, m.ax, m.ay, m.az, l.getYaw(), l.getPitch()));
            b.puff(w, Particle.FLAME, m.ax, m.ay + .3, m.az, 8, .2, .2, .2, .03);
            b.sound(w, m.ax, m.ay, m.az, Sound.ENTITY_VEX_CHARGE, .7f, 1.4f);
            rd.m.add("ability.flits");
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null || !sees(run, m, p)) return;
        Location pl = p.getLocation();
        for (int k = 0; k < 4; k++) {
            double a = (Layout.mix(m.id.getMostSignificantBits() ^ (t * 31 + k)) & 1023) / 1023.0 * Math.PI * 2;
            double x = pl.getX() + Math.cos(a) * d.radius, y = pl.getY() + 1.3, z = pl.getZ() + Math.sin(a) * d.radius;
            if (BestiaryCatalog.inside(run.room, x, z, 4) && b.free(w, x, y, z)) { m.ax = x; m.ay = y; m.az = z; m.stage = 1; m.actAt = t + d.windup; b.sound(l, Sound.ENTITY_VEX_CHARGE, .5f, 1.8f); return; }
        }
        m.nextAt = t + 20;
    }

    /** Ossuary Crawler: the first blow that takes it under its threshold splits it into two small crawlers, once. */
    void split(Encounters.Run run, Bestiary.Mob v, double dealt) {
        if (v.flag || v.summoned || !v.e.isValid() || v.e.isDead()) return;
        double left = v.e.getHealth() - dealt;
        if (left <= 0 || left > v.e.getMaxHealth() * v.def.near) return;
        v.flag = true;
        Location l = v.e.getLocation();
        Bestiary.RunData rd = b.data(run);
        for (int i = 0; i < 2 && run.adds.size() < Bestiary.MAX_ADDS; i++) {
            LivingEntity c = b.host.summon(run, Species.OSSUARY_CRAWLER, l, Math.max(2, left * .6), Math.max(1, v.base * .5), null);
            if (c != null && rd != null) rd.m.add("ability.splits");
        }
        b.puff(l.getWorld(), Particle.CLOUD, l.getX(), l.getY() + .3, l.getZ(), 10, .3, .1, .3, .02);
        b.sound(l, Sound.ENTITY_SILVERFISH_HURT, 1f, .6f);
    }

    /** Choir Banshee: a ring of purple sparks grows for a second and a half, then the cry (slowness and weakness) fills it; out of its sight or beyond the ring is safe. */
    private void scream(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            double k = Math.max(.15, 1 - (m.actAt - t) / (double) d.windup);
            if (t % 3 == 0) b.ring(w, Particle.SPELL_WITCH, l.getX(), l.getY() + 1, l.getZ(), d.radius * k, 14);
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            b.sound(l, Sound.ENTITY_GHAST_SCREAM, .9f, .7f);
            b.ring(w, Particle.CLOUD, l.getX(), l.getY() + 1, l.getZ(), d.radius, 24);
            rd.m.add("ability.screams");
            double[] eye = Bestiary.eye(m.e);
            for (Player p : ps) {
                Location pl = p.getLocation();
                if (Math.abs(pl.getY() - l.getY()) > 3 || flat(pl, l) > d.radius || !b.visible(run, eye, Bestiary.chest(p))) continue;
                if (b.hurt(run, m, p, d.damage * m.base)) { b.status(p, PotionEffectType.SLOW, 50, 0); b.status(p, PotionEffectType.WEAKNESS, 60, 0); }
            }
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null || !sees(run, m, p)) return;
        m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_GHAST_WARN, .8f, .6f);
    }

    /** Mire Leech: a green tether to a player within five blocks and in sight; four pulses, each a small blow it heals from (Bestiary.onHit). Break it by distance or by cover. */
    private void drain(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            Player p = m.ally instanceof Player ? (Player) m.ally : null;
            boolean ok = p != null && ps.contains(p);
            if (ok) { Location pl = p.getLocation(); ok = flat(pl, l) <= d.far + 1.7 && Math.abs(pl.getY() - l.getY()) < 3 && sees(run, m, p); }
            if (!ok || m.count <= 0) { m.stage = 0; m.ally = null; m.nextAt = t + b.cd(m, d.cooldown); return; }
            if (t % 3 == 0) {
                double[] c = Bestiary.chest(p);
                for (int i = 1; i <= 6; i++) { double f = i / 6.0; b.tint(w, l.getX() + (c[0] - l.getX()) * f, l.getY() + .3 + (c[1] - l.getY() - .3) * f, l.getZ() + (c[2] - l.getZ()) * f, .15, .6, .1); }
            }
            if (t >= m.actAt) {
                m.actAt = t + d.windup;
                m.count--;
                b.sound(l, Sound.ENTITY_SLIME_ATTACK, .6f, 1.4f);
                b.hurt(run, m, p, d.damage * m.base);
            }
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null || !sees(run, m, p)) return;
        m.stage = 1; m.ally = p; m.count = 4; m.actAt = t + 8;
        rd.m.add("ability.tethers");
        b.sound(l, Sound.ENTITY_SLIME_ATTACK, .7f, .8f);
    }

    /** Gravebound Knight: the shield (the off hand shows it) is up for a while and down for a while; up, it turns away projectiles from the front (Bestiary.hurt). */
    private void guard(Bestiary.Mob m, long t) {
        if (t < m.nextAt) return;
        BestiaryCatalog.Def d = m.def;
        m.up = !m.up;
        m.nextAt = t + (m.up ? d.windup : d.cooldown) + Math.floorMod(m.id.getLeastSignificantBits() ^ t, 20L);
        shield(m);
    }
    /** The off hand shows whether the shield is up. */
    private void shield(Bestiary.Mob m) {
        EntityEquipment eq = m.e.getEquipment();
        if (eq != null) eq.setItemInOffHand(m.up ? new ItemStack(Material.SHIELD) : null);
        if (!m.up) return;
        Location l = m.e.getLocation();
        b.sound(l, Sound.ITEM_SHIELD_BLOCK, .6f, 1.4f);
        b.puff(l.getWorld(), Particle.CRIT, l.getX(), l.getY() + 1.2, l.getZ(), 6, .4, .3, .4, .05);
    }

    // ---------------------------------------------------------------- Floor II
    /** Magma Lurker: leaves a patch of embers where it has been (four of its own at most); a patch smokes for half a second, then burns what stands in it. */
    private void embers(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, long t) {
        BestiaryCatalog.Def d = m.def;
        if (t < m.nextAt) return;
        m.nextAt = t + d.cooldown;
        LivingEntity e = m.e;
        if (!e.isOnGround()) return;
        Location l = e.getLocation();
        if (m.count > 0 && Math.hypot(l.getX() - m.ax, l.getZ() - m.az) < 1.2) return;
        int mine = 0;
        for (Bestiary.Patch p : rd.patches) if (m.id.equals(p.owner)) mine++;
        if (mine >= 4 || rd.patches.size() >= PATCHES || !BestiaryCatalog.inside(run.room, l.getX(), l.getZ(), 3)) return;
        double radius = d.radius + (e instanceof Slime ? Math.min(2, ((Slime) e).getSize() * .12) : 0);
        rd.patches.add(new Bestiary.Patch(l.getX(), l.getY(), l.getZ(), radius, Math.min(Bestiary.MAX_HIT, d.damage * m.base * m.dmg), t, m.id));
        m.ax = l.getX(); m.az = l.getZ(); m.count = 1;
        rd.m.add("ability.patches");
    }

    /** Whether floor carries a body along a straight run (no gap, no lava, nothing in the way, inside the room). */
    private boolean groundAlong(World w, Location from, double ux, double uz, double len, Layout.Room room) {
        for (double s = 1; s <= len; s += 1) {
            double x = from.getX() + ux * s, z = from.getZ() + uz * s;
            if (!BestiaryCatalog.inside(room, x, z, 3) || !b.ground(w, x, from.getY(), z)) return false;
        }
        return true;
    }

    /** Ceiling Stalker: crouches for a moment, then leaps at the spot where its target stood; a player it lands on is hurt and slowed. Sidestep. */
    private void pounce(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            if (t % 3 == 0) b.puff(w, Particle.SMOKE_NORMAL, l.getX(), l.getY() + .3, l.getZ(), 3, .3, .1, .3, .01);
            if (t < m.actAt) return;
            double dx = m.ax - l.getX(), dz = m.az - l.getZ(), dist = Math.hypot(dx, dz);
            if (dist < 1e-6) { m.stage = 0; return; }
            double v = Math.max(.6, Math.min(1.2, dist * .135));
            e.setVelocity(new Vector(dx / dist * v, .5, dz / dist * v));
            m.stage = 2; m.until = t + 14; m.flag = false;
            b.sound(l, Sound.ENTITY_SPIDER_AMBIENT, 1f, .5f);
            rd.m.add("ability.pounces");
            return;
        }
        if (m.stage == 2) {
            if (!m.flag) for (Player p : ps) {
                Location pl = p.getLocation();
                if (Math.abs(pl.getY() - l.getY()) < 2 && flat(pl, l) < 1.7) { m.flag = true; if (b.hurt(run, m, p, d.damage * m.base)) b.status(p, PotionEffectType.SLOW, 30, 0); break; }
            }
            if (t >= m.until || (m.flag && t > m.until - 10)) { m.stage = 0; m.nextAt = t + b.cd(m, d.cooldown); }
            return;
        }
        if (t < m.nextAt || !e.isOnGround()) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null) return;
        Location pl = p.getLocation();
        double dx = pl.getX() - l.getX(), dz = pl.getZ() - l.getZ(), dist = Math.hypot(dx, dz);
        if (dist < d.near || dist > d.far || !sees(run, m, p) || !groundAlong(w, l, dx / dist, dz / dist, Math.min(dist + 1, 9), run.room)) return;
        m.ax = pl.getX(); m.az = pl.getZ(); m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_SPIDER_AMBIENT, 1f, .6f);
    }

    /** Deep Miner: lights a charge, then lobs primed TNT at the spot its target stood (a flight of about a second); it never breaks a block, hurts only players and never more than its cap. */
    private void blast(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        if (m.stage == 1) {
            if (t % 2 == 0) b.puff(run.world, Particle.SMOKE_NORMAL, l.getX(), l.getY() + 2.1, l.getZ(), 2, .15, .1, .15, .01);
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            lob(run, rd, m, t);
            return;
        }
        if (t < m.nextAt) return;
        if (rd.things.size() >= CHARGES) { m.nextAt = t + 20; return; }
        Player p = b.nearest(l, ps, d.far);
        if (p == null) return;
        Location pl = p.getLocation();
        double dist = flat(pl, l);
        if (dist < d.near || dist > d.far || !sees(run, m, p)) return;
        m.ax = pl.getX(); m.ay = pl.getY(); m.az = pl.getZ(); m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_TNT_PRIMED, .8f, 1.3f);
    }
    private void lob(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, long t) {
        BestiaryCatalog.Def d = m.def;
        World w = run.world;
        Location from = m.e.getLocation().add(0, 1.6, 0);
        double dx = m.ax - from.getX(), dz = m.az - from.getZ(), dist = Math.hypot(dx, dz);
        if (dist < 1 || !BestiaryCatalog.inside(run.room, m.ax, m.az, 3) || !w.isChunkLoaded(from.getBlockX() >> 4, from.getBlockZ() >> 4)) return;
        int ticks = BestiaryBallistics.flight(dist);
        final double[] v = BestiaryBallistics.lob(dx, m.ay + .1 - from.getY(), dz, ticks);
        // An arc that would strike the roof is not thrown.
        if (from.getY() + BestiaryBallistics.apex(v, ticks) > run.room.roof() - 1.5) return;
        final int fuse = ticks + 10;
        TNTPrimed tnt = w.spawn(from, TNTPrimed.class, new org.bukkit.util.Consumer<TNTPrimed>() {
            public void accept(TNTPrimed x) { x.setFuseTicks(fuse); x.setYield(2f); x.setIsIncendiary(false); x.setVelocity(new Vector(v[0], v[1], v[2])); }
        });
        Bestiary.Thing th = new Bestiary.Thing(tnt, run.key, Math.min(Bestiary.MAX_HIT, d.damage * m.base * m.dmg), t);
        rd.things.add(th);
        b.things.put(tnt.getUniqueId(), th);
        rd.m.add("ability.charges");
    }

    /** Rust Golem: raises its fists for a moment, then the ground shakes in a ring around it; a player in the air (jumped clear) is spared. */
    private void slam(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        World w = run.world;
        double radius = d.radius * Math.min(1.5, b.scale(m.e));
        if (m.stage == 1) {
            if (t % 3 == 0) { b.ring(w, Particle.FLAME, l.getX(), l.getY() + .15, l.getZ(), radius, 20); b.ring(w, Particle.SMOKE_NORMAL, l.getX(), l.getY() + .15, l.getZ(), radius * Math.max(.2, 1 - (m.actAt - t) / (double) d.windup), 10); }
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            b.sound(l, Sound.ENTITY_GENERIC_EXPLODE, .6f, .7f);
            b.ring(w, Particle.EXPLOSION_NORMAL, l.getX(), l.getY() + .2, l.getZ(), radius * .6, 12);
            b.ring(w, Particle.EXPLOSION_NORMAL, l.getX(), l.getY() + .2, l.getZ(), radius, 18);
            rd.m.add("ability.slams");
            for (Player p : ps) {
                Location pl = p.getLocation();
                double dy = pl.getY() - l.getY();
                if (Math.abs(dy) > 2.5 || flat(pl, l) > radius || (dy > .5 && !p.isOnGround())) continue;
                if (b.hurt(run, m, p, d.damage * m.base)) { b.knock(p, l, .6, .45); b.status(p, PotionEffectType.SLOW, 40, 0); }
            }
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null) return;
        m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_IRONGOLEM_ATTACK, 1f, .6f);
    }

    /** Gnawer: comes with two more (runtime adds, never counted for the clear), and a gnawer among gnawers runs faster. */
    private void pack(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, long t) {
        LivingEntity e = m.e;
        Location l = e.getLocation();
        if (!m.flag && !m.summoned) {
            m.flag = true;
            for (int i = 0; i < 2 && run.adds.size() < Bestiary.MAX_ADDS; i++) {
                LivingEntity g = b.host.summon(run, Species.GNAWER_SWARM, l, Math.max(2, e.getMaxHealth() * .6), Math.max(1, m.base * .4), null);
                if (g != null) rd.m.add("ability.packs");
            }
        }
        if (t % 20 != Math.floorMod(m.id.getLeastSignificantBits(), 20L)) return;
        int n = 0;
        for (Bestiary.Mob o : rd.mobs.values()) if (o != m && o.sp == m.sp && o.e.isValid() && o.e.getLocation().distanceSquared(l) <= 25) n++;
        AttributeInstance sp = e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null && m.speed > 0) sp.setBaseValue(m.speed * (1 + .1 * Math.min(3, n)));
    }

    // ---------------------------------------------------------------- Floor III
    /** Hellforged Sentinel: sets its feet and shield, charges eight blocks along a locked line, and is left with its shield down for a while afterwards. */
    private void bash(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        if (m.stage == 1) {
            if (t % 3 == 0) b.line(run.world, Particle.CRIT, l.getX(), l.getY() + .3, l.getZ(), l.getX() + m.ax * 7, l.getY() + .3, l.getZ() + m.az * 7, 1.2);
            if (t < m.actAt) return;
            e.setVelocity(new Vector(m.ax * 1.1, .1, m.az * 1.1));
            m.stage = 2; m.until = t + 9; m.flag = false;
            b.sound(l, Sound.BLOCK_ANVIL_LAND, .8f, .6f);
            rd.m.add("ability.bashes");
            return;
        }
        if (m.stage == 2) {
            if (t < m.until) {
                e.setVelocity(new Vector(m.ax * .95, Math.min(e.getVelocity().getY(), .1), m.az * .95));
                if (!m.flag) for (Player p : ps) {
                    Location pl = p.getLocation();
                    if (Math.abs(pl.getY() - l.getY()) < 2 && flat(pl, l) < 1.8) {
                        m.flag = true;
                        if (b.hurt(run, m, p, d.damage * m.base)) { b.knock(p, l, .9, .35); b.status(p, PotionEffectType.SLOW, 40, 1); }
                        break;
                    }
                }
                return;
            }
            // Over: it recovers with the shield down, open to anything.
            m.stage = 3; m.up = false; m.until = t + 50; m.nextAt = t + b.cd(m, d.cooldown);
            shield(m);
            return;
        }
        if (m.stage == 3) {
            if (t < m.until) return;
            m.stage = 0; m.up = true;
            shield(m);
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null) return;
        Location pl = p.getLocation();
        double dx = pl.getX() - l.getX(), dz = pl.getZ() - l.getZ(), dist = Math.hypot(dx, dz);
        if (dist < d.near || dist > d.far || !sees(run, m, p) || !groundAlong(run.world, l, dx / dist, dz / dist, 8, run.room)) return;
        m.ax = dx / dist; m.az = dz / dist; m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ITEM_SHIELD_BLOCK, 1f, .6f);
    }

    /** Void Wraith: marks a circle behind its target, and a moment later it is there and rakes it; leave the circle. */
    private void blink(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            if (t % 3 == 0) { b.ring(w, Particle.PORTAL, m.ax, m.ay + .2, m.az, d.radius, 16); b.puff(w, Particle.PORTAL, l.getX(), l.getY() + 1.5, l.getZ(), 10, .3, .8, .3, .5); }
            if (t < m.actAt) return;
            Player p = m.ally instanceof Player ? (Player) m.ally : null;
            if (!b.free(w, m.ax, m.ay, m.az)) { m.stage = 0; m.ally = null; m.nextAt = t + 30; return; }
            double yaw = p == null ? l.getYaw() : Math.toDegrees(Math.atan2(-(p.getLocation().getX() - m.ax), p.getLocation().getZ() - m.az));
            b.puff(w, Particle.PORTAL, l.getX(), l.getY() + 1.5, l.getZ(), 20, .3, .8, .3, .6);
            e.teleport(new Location(w, m.ax, m.ay, m.az, (float) yaw, 0f));
            b.sound(w, m.ax, m.ay, m.az, Sound.ENTITY_ENDERMEN_TELEPORT, .9f, .8f);
            m.stage = 2; m.until = t + 8; m.ally = null;
            rd.m.add("ability.blinks");
            return;
        }
        if (m.stage == 2) {
            if (t < m.until) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            Location at = e.getLocation();
            b.puff(w, Particle.SWEEP_ATTACK, at.getX(), at.getY() + 1, at.getZ(), 1, 0, 0, 0, 0);
            for (Player p : ps) {
                Location pl = p.getLocation();
                if (Math.abs(pl.getY() - at.getY()) < 2.5 && flat(pl, at) <= d.radius && b.hurt(run, m, p, d.damage * m.base)) b.knock(p, at, .6, .3);
            }
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null || !sees(run, m, p)) return;
        Location pl = p.getLocation();
        // Behind the player, or to either side of there.
        for (double turn : new double[]{0, 40, -40, 80, -80}) {
            double[] at = BestiaryCatalog.behind(run.room, pl.getX(), pl.getZ(), pl.getYaw() + turn, 2.4, 4);
            if (b.ground(w, at[0], pl.getY(), at[1])) {
                m.ax = at[0]; m.ay = pl.getY(); m.az = at[1]; m.ally = p; m.stage = 1; m.actAt = t + d.windup;
                b.sound(w, at[0], pl.getY(), at[1], Sound.ENTITY_ENDERMEN_TELEPORT, .5f, 1.6f);
                return;
            }
        }
        m.nextAt = t + 20;
    }

    /** The room's other mobs that are not a boss (the ones a templar can mend). */
    private List<LivingEntity> allies(Encounters.Run run, Bestiary.Mob m) {
        List<LivingEntity> out = new ArrayList<LivingEntity>();
        for (Map.Entry<Integer, LivingEntity> en : run.mobs.entrySet()) {
            LivingEntity a = en.getValue();
            if (a != m.e && a.isValid() && !a.isDead() && !run.room.bossSlot(en.getKey())) out.add(a);
        }
        for (LivingEntity a : run.adds) if (a != m.e && a.isValid() && !a.isDead()) out.add(a);
        return out;
    }
    /** Blood Templar: a red beam to the most hurt ally within ten blocks (under seven in ten of its health), then a quarter of that ally's health back; three times at most for any one ally. */
    private void mend(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            LivingEntity ally = m.ally;
            if (ally == null || !ally.isValid() || ally.isDead() || ally.getLocation().distanceSquared(l) > (d.far + 3) * (d.far + 3)) { m.stage = 0; m.ally = null; m.nextAt = t + 40; return; }
            Location al = ally.getEyeLocation();
            if (t % 2 == 0) for (int i = 1; i <= 8; i++) { double f = i / 8.0; b.tint(w, l.getX() + (al.getX() - l.getX()) * f, l.getY() + 1.4 + (al.getY() - l.getY() - 1.4) * f, l.getZ() + (al.getZ() - l.getZ()) * f, .85, .1, .1); }
            if (t < m.actAt) return;
            b.heal(ally, Math.min(40, ally.getMaxHealth() * d.damage));
            Integer n = rd.mended.get(ally.getUniqueId());
            rd.mended.put(ally.getUniqueId(), n == null ? 1 : n + 1);
            b.puff(w, Particle.HEART, al.getX(), al.getY() + .4, al.getZ(), 5, .4, .3, .4, 0);
            b.sound(al, Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL, .8f, 1.5f);
            rd.m.add("ability.mends");
            m.stage = 0; m.ally = null; m.nextAt = t + b.cd(m, d.cooldown);
            return;
        }
        if (t < m.nextAt) return;
        LivingEntity best = null;
        double bestFrac = .7;
        for (LivingEntity a : allies(run, m)) {
            if (a.getLocation().distanceSquared(l) > d.far * d.far) continue;
            Integer n = rd.mended.get(a.getUniqueId());
            if (n != null && n >= 3) continue;
            double frac = a.getHealth() / a.getMaxHealth();
            if (frac < bestFrac) { bestFrac = frac; best = a; }
        }
        if (best == null) { m.nextAt = t + 30; return; }
        m.stage = 1; m.ally = best; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK, .7f, 1.2f);
    }

    /** Doom Herald: casts in turn a call (up to two vexes, four of its own alive at most) and a row of fangs along the line to where its target stood. */
    private void summon(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        LivingEntity e = m.e;
        Location l = e.getLocation();
        World w = run.world;
        if (m.stage == 1) {
            if (t % 3 == 0) b.ring(w, Particle.SPELL_WITCH, l.getX(), l.getY() + .2, l.getZ(), 1.5 + (m.actAt - t) * .05, 12);
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            if (m.count == 1) callVexes(run, rd, m); else fangs(run, rd, m, t);
            return;
        }
        if (t < m.nextAt) return;
        Player p = b.nearest(l, ps, d.far);
        if (p == null || !sees(run, m, p)) return;
        m.minions.removeIf(new java.util.function.Predicate<LivingEntity>() { public boolean test(LivingEntity v) { return !v.isValid() || v.isDead(); } });
        m.flag = !m.flag;
        boolean vexes = m.flag && m.minions.size() < 4 && run.adds.size() < Bestiary.MAX_ADDS;
        m.count = vexes ? 1 : 2;
        Location pl = p.getLocation();
        m.ax = pl.getX(); m.ay = pl.getY(); m.az = pl.getZ(); m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, vexes ? Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_SUMMON : Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK, 1f, .8f);
    }
    private void callVexes(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m) {
        Location l = m.e.getLocation();
        int room = 4 - m.minions.size();
        for (int i = 0; i < Math.min(2, room) && run.adds.size() < Bestiary.MAX_ADDS; i++) {
            LivingEntity v = b.host.summon(run, Species.VEX, l, Math.max(4, (6 + 2 * run.room.tier) * Floors.health(run.room.floor) * .5), Math.max(1, m.base * .6), ChatColor.DARK_PURPLE + "Doom Vex");
            if (v != null) { m.minions.add(v); rd.m.add("ability.vexes"); }
        }
        b.puff(l.getWorld(), Particle.SPELL_WITCH, l.getX(), l.getY() + 1, l.getZ(), 20, .6, .6, .6, .1);
        b.sound(l, Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL, 1f, 1f);
    }
    private void fangs(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        double dx = m.ax - l.getX(), dz = m.az - l.getZ(), len = Math.hypot(dx, dz);
        if (len < 1e-6) { dx = 1; dz = 0; len = 1; }
        double ux = dx / len, uz = dz / len, cap = Math.min(Bestiary.MAX_HIT, d.damage * m.base * m.dmg);
        for (int i = 0; i < 6; i++) {
            double dist = 1.8 + 1.5 * i, x = l.getX() + ux * dist, z = l.getZ() + uz * dist;
            if (!BestiaryCatalog.inside(run.room, x, z, 2.5)) break;
            rd.casts.add(new Bestiary.Cast(t + 2 * i, x, l.getY(), z, cap, m.id));
        }
        rd.m.add("ability.fangs");
        b.sound(l, Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL, 1f, 1f);
    }
    /** A fang of a row rises (called by Bestiary when its tick comes). */
    void rise(Encounters.Run run, Bestiary.RunData rd, Bestiary.Cast c, long t) {
        World w = run.world;
        if (rd.things.size() >= THINGS || !w.isChunkLoaded(((int) Math.floor(c.x)) >> 4, ((int) Math.floor(c.z)) >> 4) || !b.free(w, c.x, c.y, c.z)) return;
        Bestiary.Mob o = rd.mobs.get(c.owner);
        final LivingEntity owner = o != null && o.e.isValid() ? o.e : null;
        EvokerFangs f = w.spawn(new Location(w, c.x, c.y, c.z), EvokerFangs.class, new org.bukkit.util.Consumer<EvokerFangs>() {
            public void accept(EvokerFangs x) { if (owner != null) x.setOwner(owner); }
        });
        Bestiary.Thing th = new Bestiary.Thing(f, run.key, c.cap, t);
        rd.things.add(th);
        b.things.put(f.getUniqueId(), th);
        b.sound(w, c.x, c.y, c.z, Sound.ENTITY_EVOCATION_FANGS_ATTACK, .8f, 1f);
    }

    /** Squad Captain: a rally every few seconds when gunners are near it: they aim a fifth faster, pause less and hit a fifth harder for five seconds (Gunners). */
    private void rally(Encounters.Run run, Bestiary.RunData rd, Bestiary.Mob m, List<Player> ps, long t) {
        BestiaryCatalog.Def d = m.def;
        Location l = m.e.getLocation();
        World w = run.world;
        double range2 = d.far * d.far;
        if (m.stage == 1) {
            if (t % 3 == 0) for (Bestiary.Mob o : rd.mobs.values()) if (o.gun != null && o.e.isValid() && o.e.getLocation().distanceSquared(l) <= range2) { Location ol = o.e.getEyeLocation(); b.puff(w, Particle.NOTE, ol.getX(), ol.getY() + .8, ol.getZ(), 0, (Math.floorMod(t / 3, 24L)) / 24.0, 0, 0, 1); }
            if (t < m.actAt) return;
            m.stage = 0;
            m.nextAt = t + b.cd(m, d.cooldown);
            int n = 0;
            for (Bestiary.Mob o : rd.mobs.values()) {
                if (o.gun == null || !o.e.isValid() || o.e.getLocation().distanceSquared(l) > range2) continue;
                o.gun.rallyUntil = t + 100;
                n++;
                Location ol = o.e.getEyeLocation();
                b.puff(w, Particle.VILLAGER_HAPPY, ol.getX(), ol.getY() + .6, ol.getZ(), 8, .4, .4, .4, 0);
            }
            b.sound(l, Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_WOLOLO, 1f, 1.2f);
            if (n > 0) rd.m.add("ability.rallies");
            return;
        }
        if (t < m.nextAt) return;
        boolean any = false;
        for (Bestiary.Mob o : rd.mobs.values()) if (o.gun != null && o.e.isValid() && o.e.getLocation().distanceSquared(l) <= range2) { any = true; break; }
        if (!any || ps.isEmpty()) { m.nextAt = t + 40; return; }
        m.stage = 1; m.actAt = t + d.windup;
        b.sound(l, Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_WOLOLO, .9f, 1f);
    }
}
