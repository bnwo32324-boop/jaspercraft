package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Generation 7 (owner 2026-10-05: "add ... custom mobs. Have them be totally original"; "a plethora of mobs"; "some enemies to be
 * equipped with guns that actively shoot at you"): custom mobs drawn on vanilla bases (name, equipment, attributes, one ability
 * each), gunners and elites. Encounters calls dress() right after a mob spawns (a room's own mob or a runtime add) and tick()
 * every tick for each active room; sleep() ends a room's state and close() the plugin's. This class keeps the registry, the
 * shared helpers and the listener; BestiaryAbilities drives the species, Gunners the guns, BestiaryCatalog and
 * BestiaryBallistics hold every number (and are audited without a server).
 *
 * Rules every ability keeps: it acts only inside its own room, hits only eligible players (alive, in Survival or Adventure,
 * outside the arrival circle) through p.damage(amount, mob) so Encounters.canHarm and every relic see it, never breaks a block,
 * never lasts after the room sleeps, and never deals more than {@link #MAX_HIT} in one blow. A telegraph always comes first.
 *
 * Logs: DUNGEON_BESTIARY_ROOM (a room's counts when it sleeps), DUNGEON_BESTIARY (totals when the plugin closes) and
 * DUNGEON_BESTIARY_FAILED (once per place and species: an error that switched a mob's behaviour off). No coordinates, ever.
 */
public class Bestiary implements Listener {
    /**
     * What the bestiary needs from the plugin: a seam, so BestiarySimTest can run the real classes against fake worlds, players
     * and mobs in a plain JVM. The live host is the plugin's own Encounters, Sanctuary and Bodies.
     */
    interface Host {
        boolean enabled();
        java.util.logging.Logger log();
        /** In the arrival circle, where nobody is ever hurt. */
        boolean sheltered(Location l);
        /** The scale a mob was grown to (1 for a vanilla body). */
        double scale(LivingEntity e);
        /** The live room by its key, or null. */
        Encounters.Run run(String key);
        LivingEntity summon(Encounters.Run run, Species species, Location near, double hp, double damage, String name);
        Player player(UUID id);
    }
    private static final class Live implements Host {
        private final DungeonPlugin plugin;
        Live(DungeonPlugin plugin) { this.plugin = plugin; }
        public boolean enabled() { return plugin.getConfig().getBoolean("bestiary-enabled", true); }
        public java.util.logging.Logger log() { return plugin.getLogger(); }
        public boolean sheltered(Location l) { return plugin.sanctuary != null && plugin.sanctuary.contains(l); }
        public double scale(LivingEntity e) { return plugin.bodies == null ? 1 : plugin.bodies.scaleOf(e); }
        public Encounters.Run run(String key) { return plugin.encounters == null ? null : plugin.encounters.active.get(key); }
        public LivingEntity summon(Encounters.Run run, Species species, Location near, double hp, double damage, String name) { return plugin.encounters.summon(run, species, near, hp, damage, name); }
        public Player player(UUID id) { return Bukkit.getPlayer(id); }
    }
    final Host host;
    /** The hardest single blow any custom mob or elite deals, whatever the floor: nobody is killed from full health by one ability. */
    static final double MAX_HIT = 12;
    private static final int MAX_MOBS = 4096;
    /** The largest maximum health this class ever sets: Paper refuses more than 2048 (spigot.yml). A mob whose health Encounters already carries beyond that is the lead's Encounters.vigor to scale. */
    static final double MAX_BODY = 2000;
    /** Runtime adds (vexes, crawlers, gnawers) the bestiary leaves alive in one room at most. */
    static final int MAX_ADDS = 40;
    final Map<UUID, Mob> mobs = new HashMap<UUID, Mob>();
    final Map<String, RunData> runs = new HashMap<String, RunData>();
    /** Entities of the bestiary that hurt through their own damage events (charges, fangs), by id. */
    final Map<UUID, Thing> things = new HashMap<UUID, Thing>();
    private final Set<String> warned = new HashSet<String>();
    private final Metrics totals = new Metrics();
    final BestiaryAbilities abilities;
    final Gunners gunners;
    /** The last tick Encounters gave us. */
    long now;
    private boolean closed;
    /** The blow in flight (see hurtExact): who strikes whom, and whether the server accepted it. */
    private Mob striker;
    private Player struck;
    private boolean landed;

    Bestiary(DungeonPlugin plugin) { this(new Live(plugin)); }
    Bestiary(Host host) {
        this.host = host;
        abilities = new BestiaryAbilities(this);
        gunners = new Gunners(this);
    }

    // ================================================================ state
    /** The state of one dressed mob: a custom species, an elite, or both. */
    static final class Mob {
        final UUID id;
        final LivingEntity e;
        final Species sp;
        final BestiaryCatalog.Def def;
        final String room;
        final int slot;
        final boolean boss, summoned;
        /** The blow every ability of this mob is a factor of, and how fast its abilities come (cooldowns are multiplied by pace). */
        final double base, pace;
        BestiaryCatalog.Mod elite;
        /** Damage dealt factor (elites), a warded elite's ward and the speed the mob was given. */
        double dmg = 1, ward, speed;
        boolean ready, flag, up;
        long nextAt, actAt, until, last;
        int stage, count, heals;
        double ax, ay, az;
        LivingEntity ally;
        final List<LivingEntity> minions = new ArrayList<LivingEntity>();
        Gunners.State gun;
        Mob(LivingEntity e, Species sp, BestiaryCatalog.Def def, Encounters.Run run, int slot, boolean boss) {
            this.e = e; id = e.getUniqueId(); this.sp = sp; this.def = def; room = run.key; this.slot = slot; this.boss = boss; summoned = slot < 0;
            base = BestiaryCatalog.base(run.room.tier, run.room.floor);
            pace = Math.max(.5, Floors.cadence(run.room.floor) * (1 - .12 * Floors.depth(run.room)));
        }
    }
    /** A charge or a row of fangs: it hurts through its own damage events, which the listener bounds. */
    static final class Thing {
        final Entity entity;
        final String room;
        final double cap;
        final long born;
        Thing(Entity entity, String room, double cap, long born) { this.entity = entity; this.room = room; this.cap = cap; this.born = born; }
    }
    /** A patch of embers a magma lurker left: harmless while it gathers, then it burns whoever stands in it. */
    static final class Patch {
        final double x, y, z, radius, damage;
        final long born;
        final UUID owner;
        long lastHit;
        Patch(double x, double y, double z, double radius, double damage, long born, UUID owner) { this.x = x; this.y = y; this.z = z; this.radius = radius; this.damage = damage; this.born = born; this.owner = owner; }
    }
    /** A volatile elite's death: a ring that warns for a second, then a burst. */
    static final class Blast {
        final double x, y, z, radius, damage;
        final long at;
        Blast(double x, double y, double z, double radius, double damage, long at) { this.x = x; this.y = y; this.z = z; this.radius = radius; this.damage = damage; this.at = at; }
    }
    /** A fang of a doom herald's row still to rise. */
    static final class Cast {
        final long at;
        final double x, y, z, cap;
        final UUID owner;
        Cast(long at, double x, double y, double z, double cap, UUID owner) { this.at = at; this.x = x; this.y = y; this.z = z; this.cap = cap; this.owner = owner; }
    }
    /** What the bestiary keeps for one active room. */
    static final class RunData {
        final String key;
        final Map<UUID, Mob> mobs = new LinkedHashMap<UUID, Mob>();
        final List<Patch> patches = new ArrayList<Patch>();
        final List<Blast> blasts = new ArrayList<Blast>();
        final List<Cast> casts = new ArrayList<Cast>();
        final List<Thing> things = new ArrayList<Thing>();
        /** How many times each ally was healed by a templar (three at most). */
        final Map<UUID, Integer> mended = new HashMap<UUID, Integer>();
        final Metrics m = new Metrics();
        long cacheTick = -1;
        List<Player> cache = Collections.emptyList();
        /** Armed gunners standing here. */
        int armed;
        RunData(String key) { this.key = key; }
        boolean idle() { return mobs.isEmpty() && patches.isEmpty() && blasts.isEmpty() && casts.isEmpty() && things.isEmpty(); }
    }
    /** Bounded counters for the logs (their keys come only from enums and fixed words). */
    static final class Metrics {
        final Map<String, Integer> c = new TreeMap<String, Integer>();
        void add(String key) { add(key, 1); }
        void add(String key, int n) { if (c.size() < 300 || c.containsKey(key)) { Integer old = c.get(key); c.put(key, (old == null ? 0 : old) + n); } }
        int get(String key) { Integer v = c.get(key); return v == null ? 0 : v; }
        void absorb(Metrics o) { for (Map.Entry<String, Integer> en : o.c.entrySet()) add(en.getKey(), en.getValue()); }
        /** "a:1,b:2" for the keys that start with prefix (the prefix cut off), or "-". */
        String group(String prefix) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Integer> en : c.entrySet()) if (en.getKey().startsWith(prefix)) { if (b.length() > 0) b.append(','); b.append(en.getKey().substring(prefix.length())).append(':').append(en.getValue()); }
            return b.length() == 0 ? "-" : b.toString();
        }
    }

    // ================================================================ hooks
    private boolean enabled() { return !closed && host.enabled(); }

    /** Called by Encounters right after a mob spawns: slot is its room slot, or -1 for a runtime add. */
    void dress(LivingEntity e, Species species, Encounters.Run run, int slot, boolean boss) {
        if (e == null || species == null || run == null || run.world == null || !enabled()) return;
        try {
            BestiaryCatalog.Def def = BestiaryCatalog.def(species);
            double depth = Floors.depth(run.room);
            BestiaryCatalog.Mod elite = BestiaryCatalog.elite(run.room.hash, slot, boss, run.room.floor, depth, species);
            if ((def == null && elite == null) || mobs.size() >= MAX_MOBS) return;
            RunData rd = runs.get(run.key);
            if (rd == null) { rd = new RunData(run.key); runs.put(run.key, rd); }
            Mob m = new Mob(e, species, def, run, slot, boss);
            m.elite = elite;
            if (def != null) wear(run, rd, m, depth);
            if (elite != null) empower(m);
            mobs.put(m.id, m);
            rd.mobs.put(m.id, m);
            rd.m.add("species." + species.name());
            if (elite != null) rd.m.add("elite." + elite.name());
        } catch (RuntimeException ex) {
            fail("dress", species, run, ex);
        }
    }

    /** Called by Encounters every tick for every active room. */
    void tick(Encounters.Run run, long ticks) {
        now = ticks;
        if (closed || run == null || run.world == null) return;
        RunData rd = runs.get(run.key);
        if (rd == null || rd.idle() || run.players.isEmpty()) return;
        try {
            List<Player> ps = players(run, rd, ticks);
            // A snapshot: a mob can die (and leave rd.mobs) inside its own step, through a relic's thorns for one.
            for (Mob m : new ArrayList<Mob>(rd.mobs.values())) {
                if (!m.e.isValid() || m.e.isDead()) { forget(rd, m); continue; }
                try { step(run, rd, m, ps, ticks); }
                catch (RuntimeException ex) { fail("step", m.sp, run, ex); forget(rd, m); }
            }
            effects(run, rd, ps, ticks);
        } catch (RuntimeException ex) {
            fail("tick", null, run, ex);
        }
    }

    /** A room goes to sleep: its mobs, charges and patches are done (Encounters removes the mobs and adds itself). */
    void sleep(Encounters.Run run) {
        if (run == null) return;
        RunData rd = runs.remove(run.key);
        if (rd == null) return;
        try {
            if (rd.m.group("species.").length() > 1) host.log().info("DUNGEON_BESTIARY_ROOM room=" + run.key + " floor=" + run.room.floor + " tier=" + run.room.tier + " species=" + rd.m.group("species.")
                + " elites=" + rd.m.group("elite.") + " aims=" + rd.m.get("aims") + " shots=" + rd.m.get("shots") + " hits=" + rd.m.get("hits") + " reloads=" + rd.m.get("reloads") + " abilities=" + rd.m.group("ability."));
        } catch (RuntimeException ex) { fail("room-log", null, run, ex); }
        release(rd);
        totals.absorb(rd.m);
        totals.add("rooms");
    }

    /** The plugin closes: everything goes, and the totals are logged. */
    void close() {
        for (RunData rd : new ArrayList<RunData>(runs.values())) { release(rd); totals.absorb(rd.m); totals.add("rooms"); }
        runs.clear(); mobs.clear(); things.clear();
        closed = true;
        if (totals.c.isEmpty()) return;
        host.log().info("DUNGEON_BESTIARY metrics rooms=" + totals.get("rooms") + " species=" + totals.group("species.") + " elites=" + totals.group("elite.")
            + " aims=" + totals.get("aims") + " shots=" + totals.get("shots") + " hits=" + totals.get("hits") + " reloads=" + totals.get("reloads") + " abilities=" + totals.group("ability.") + " failures=" + totals.get("failures"));
    }

    /** A mob leaves the registry (once: a mob that died this tick is already gone from the room's map). */
    private void forget(RunData rd, Mob m) {
        if (rd.mobs.remove(m.id) != null && m.gun != null && rd.armed > 0) rd.armed--;
        mobs.remove(m.id);
    }
    private void release(RunData rd) {
        for (Mob m : rd.mobs.values()) mobs.remove(m.id);
        rd.mobs.clear();
        for (Thing t : rd.things) { things.remove(t.entity.getUniqueId()); if (t.entity.isValid()) t.entity.remove(); }
        rd.things.clear(); rd.patches.clear(); rd.blasts.clear(); rd.casts.clear(); rd.mended.clear();
    }

    /** An error in a behaviour: logged once per place and species, counted, and the caller drops what failed. */
    void fail(String site, Species sp, Encounters.Run run, Throwable ex) {
        totals.add("failures");
        String key = site + "/" + (sp == null ? "-" : sp.name());
        if (warned.size() >= 256 || !warned.add(key)) return;
        String msg = String.valueOf(ex.getMessage()).replaceAll("[\\r\\n]+", " ");
        if (msg.length() > 160) msg = msg.substring(0, 160);
        host.log().warning("DUNGEON_BESTIARY_FAILED site=" + site + " species=" + (sp == null ? "-" : sp.name()) + " room=" + (run == null ? "-" : run.key) + " " + ex.getClass().getSimpleName() + ": " + msg);
    }

    // ================================================================ dressing
    static ItemStack item(String spec) { return BestiaryNms.item(spec); }
    /** A maximum health this class may set: at least 1, at most {@link #MAX_BODY}. */
    static double body(double hp) { return Math.max(1, Math.min(MAX_BODY, hp)); }
    private static void attribute(LivingEntity e, Attribute a, double factor, double floor) {
        AttributeInstance at = e.getAttribute(a);
        if (at != null) at.setBaseValue(Math.max(floor, at.getBaseValue() * factor));
    }

    /** A custom species: its name, its dress, its body, and for a gunner its gun (when this room may have one). */
    private void wear(Encounters.Run run, RunData rd, Mob m, double depth) {
        LivingEntity e = m.e;
        BestiaryCatalog.Def d = m.def;
        // A species is named by its definition; a boss keeps its boss's name, and a runtime add the name its caller gave it.
        if (!m.boss && (!m.summoned || e.getCustomName() == null)) { e.setCustomName(d.shown()); e.setCustomNameVisible(false); }
        EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            eq.setHelmet(item(d.head)); eq.setChestplate(item(d.chest)); eq.setLeggings(item(d.legs)); eq.setBoots(item(d.feet));
            eq.setItemInMainHand(item(d.hand));
            // A guard's shield is raised and lowered by its ability; a sentinel carries its shield up.
            eq.setItemInOffHand(d.kind == BestiaryCatalog.Kind.GUARD ? null : item(d.off));
            eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setLeggingsDropChance(0); eq.setBootsDropChance(0);
            eq.setItemInMainHandDropChance(0); eq.setItemInOffHandDropChance(0);
        }
        m.up = d.kind == BestiaryCatalog.Kind.BASH;
        if (d.plain) return;
        if (BestiaryBallistics.profile(m.sp) != null) {
            m.gun = gunners.attach(m, run, rd, depth);
            if (m.gun != null) rd.armed++;
        }
        if (!m.boss && !m.summoned && d.hp != 1) {
            AttributeInstance hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) { double max = body(hp.getBaseValue() * d.hp); hp.setBaseValue(max); e.setHealth(max); }
        }
        if (!m.boss && d.speed != 1) attribute(e, Attribute.GENERIC_MOVEMENT_SPEED, d.speed, 0);
        AttributeInstance sp = e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        m.speed = sp == null ? 0 : sp.getBaseValue();
        AttributeInstance kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), d.resist));
        AttributeInstance range = e.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (range != null) range.setBaseValue(Math.max(range.getBaseValue(), d.follow));
    }

    /** An elite: a prefix on its name, a little more health, and its modifier's effect. */
    private void empower(Mob m) {
        LivingEntity e = m.e;
        BestiaryCatalog.Mod x = m.elite;
        String name = e.getCustomName();
        e.setCustomName(x.shown() + " " + (name == null ? ChatColor.GRAY + "Penitent" : name));
        e.setCustomNameVisible(true);
        e.addScoreboardTag("jpd_elite:" + x.name());
        AttributeInstance hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) { double max = body(hp.getBaseValue() * x.hp); hp.setBaseValue(max); e.setHealth(max); }
        if (x.speed != 1) { attribute(e, Attribute.GENERIC_MOVEMENT_SPEED, x.speed, 0); AttributeInstance sp = e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED); m.speed = sp == null ? 0 : sp.getBaseValue(); }
        if (x.damage != 1) attribute(e, Attribute.GENERIC_ATTACK_DAMAGE, x.damage, 0);
        m.dmg = x.damage;
        if (x == BestiaryCatalog.Mod.WARDED) m.ward = e.getMaxHealth() * BestiaryCatalog.WARD_SHARE;
        if (x == BestiaryCatalog.Mod.ARMOURED && (e instanceof Zombie || e instanceof Skeleton)) {
            EntityEquipment eq = e.getEquipment();
            if (eq != null) {
                if (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR) eq.setHelmet(item("IRON_HELMET"));
                if (eq.getChestplate() == null || eq.getChestplate().getType() == Material.AIR) eq.setChestplate(item("IRON_CHESTPLATE"));
                eq.setHelmetDropChance(0); eq.setChestplateDropChance(0);
            }
        }
    }

    // ================================================================ the tick
    private void step(Encounters.Run run, RunData rd, Mob m, List<Player> ps, long t) {
        if (!m.ready) { m.ready = true; m.nextAt = t + 30 + Math.floorMod(m.id.getLeastSignificantBits(), 50L); }
        else if (t - m.last > 10) rested(m, t);
        m.last = t;
        if (m.def != null) {
            if (m.gun != null) gunners.step(run, rd, m, ps, t);
            if (m.def.kind != BestiaryCatalog.Kind.GUN) abilities.step(run, rd, m, ps, t);
        }
        if (m.elite != null) eliteTick(m, t);
    }

    /**
     * Nobody was in the room for a while, so nothing ran: a warning half shown or a laser half locked is dropped, rather than
     * finished at once on the first tick someone walks back in. (Absolute ticks keep counting while a room is idle.)
     */
    private void rested(Mob m, long t) {
        m.stage = 0;
        m.ally = null;
        m.nextAt = Math.max(m.nextAt, t + 20);
        if (m.gun != null && m.gun.cycle != null) m.gun.cycle.reset(t);
        if (m.def != null) abilities.rested(m);
    }

    /** A few particles every second show what an elite is: harmless, never a glow. */
    private void eliteTick(Mob m, long t) {
        if (t % 20 != Math.floorMod(m.id.getLeastSignificantBits(), 20L)) return;
        Location l = m.e.getEyeLocation();
        World w = l.getWorld();
        switch (m.elite) {
            case FRENZIED: puff(w, Particle.VILLAGER_ANGRY, l.getX(), l.getY() + .4, l.getZ(), 2, .3, .1, .3, 0); break;
            case ARMOURED: puff(w, Particle.CRIT, l.getX(), l.getY() - .4, l.getZ(), 2, .35, .5, .35, .02); break;
            case VAMPIRIC: puff(w, Particle.REDSTONE, l.getX(), l.getY() + .3, l.getZ(), 0, 1, 0, 0, 1); break;
            case VOLATILE: puff(w, Particle.SMOKE_NORMAL, l.getX(), l.getY() - .2, l.getZ(), 4, .3, .4, .3, .01); puff(w, Particle.FLAME, l.getX(), l.getY(), l.getZ(), 1, .2, .2, .2, .01); break;
            case SWIFT: puff(w, Particle.CLOUD, l.getX(), l.getY() - 1, l.getZ(), 3, .25, .05, .25, .01); break;
            case WARDED: if (m.ward > 0) puff(w, Particle.ENCHANTMENT_TABLE, l.getX(), l.getY() - .3, l.getZ(), 6, .4, .5, .4, .3); break;
            case THORNED: puff(w, Particle.CRIT_MAGIC, l.getX(), l.getY() - .4, l.getZ(), 3, .4, .5, .4, .05); break;
            case CHILLING: puff(w, Particle.SNOW_SHOVEL, l.getX(), l.getY() - .3, l.getZ(), 5, .35, .5, .35, .02); break;
            default: break;
        }
    }

    /** Patches burn, blasts go off, rows of fangs rise, spent charges and fangs are swept. */
    private void effects(Encounters.Run run, RunData rd, List<Player> ps, long t) {
        World w = run.world;
        for (Iterator<Patch> it = rd.patches.iterator(); it.hasNext();) {
            Patch p = it.next();
            long age = t - p.born;
            if (age >= 100) { it.remove(); continue; }
            if (age < 10) { if (age % 3 == 0) puff(w, Particle.SMOKE_NORMAL, p.x, p.y + .2, p.z, 3, p.radius * .5, .05, p.radius * .5, .01); continue; }
            if (t % 4 == 0) { puff(w, Particle.FLAME, p.x, p.y + .15, p.z, 2, p.radius * .5, .02, p.radius * .5, .01); if (t % 8 == 0) puff(w, Particle.LAVA, p.x, p.y + .3, p.z, 1, p.radius * .4, .02, p.radius * .4, 0); }
            if (t - p.lastHit < 10) continue;
            for (Player pl : ps) {
                Location l = pl.getLocation();
                if (Math.abs(l.getY() - p.y) < 1.6 && Math.hypot(l.getX() - p.x, l.getZ() - p.z) <= p.radius) { p.lastHit = t; hurtPlain(run, pl, p.damage); if (pl.getFireTicks() < 30) pl.setFireTicks(30); break; }
            }
        }
        for (Iterator<Blast> it = rd.blasts.iterator(); it.hasNext();) {
            Blast b = it.next();
            if (t - b.at > 40) { it.remove(); continue; }
            if (t < b.at) { if (t % 4 == 0) ring(w, Particle.FLAME, b.x, b.y + .2, b.z, b.radius * (1 - (b.at - t) / 20.0), 10); continue; }
            it.remove();
            puff(w, Particle.EXPLOSION_LARGE, b.x, b.y + .6, b.z, 1, 0, 0, 0, 0);
            sound(w, b.x, b.y, b.z, Sound.ENTITY_GENERIC_EXPLODE, .6f, 1.3f);
            for (Player pl : ps) {
                Location l = pl.getLocation();
                if (Math.abs(l.getY() - b.y) < 2.5 && Math.hypot(l.getX() - b.x, l.getZ() - b.z) <= b.radius && hurtPlain(run, pl, b.damage)) knock(pl, new Location(w, b.x, b.y, b.z), .5, .3);
            }
        }
        for (Iterator<Cast> it = rd.casts.iterator(); it.hasNext();) {
            Cast c = it.next();
            if (t - c.at > 10) { it.remove(); continue; }
            if (t < c.at) continue;
            it.remove();
            abilities.rise(run, rd, c, t);
        }
        for (Iterator<Thing> it = rd.things.iterator(); it.hasNext();) {
            Thing th = it.next();
            if (th.entity.isValid() && t - th.born <= 200) continue;
            it.remove(); things.remove(th.entity.getUniqueId());
            if (th.entity.isValid()) th.entity.remove();
        }
    }

    // ================================================================ shared helpers
    /** The eligible players of this room right now (computed once per tick per room). */
    List<Player> players(Encounters.Run run, RunData rd, long t) {
        if (rd.cacheTick == t) return rd.cache;
        List<Player> out = new ArrayList<Player>();
        for (UUID id : run.players) { Player p = host.player(id); if (eligible(p, run)) out.add(p); }
        rd.cache = out; rd.cacheTick = t;
        return out;
    }
    /** Alive, online, in this room's world, in Survival or Adventure, and outside the arrival circle: the only players ever hurt. */
    boolean eligible(Player p, Encounters.Run run) {
        if (p == null || !p.isOnline() || p.isDead() || !p.getWorld().equals(run.world)) return false;
        GameMode g = p.getGameMode();
        return (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE) && !host.sheltered(p.getLocation());
    }
    Player nearest(Location from, List<Player> ps, double max) {
        Player best = null;
        double bestD = max * max;
        for (Player p : ps) {
            Location l = p.getLocation();
            double dx = l.getX() - from.getX(), dz = l.getZ() - from.getZ(), dy = l.getY() - from.getY(), d = dx * dx + dz * dz;
            if (Math.abs(dy) <= 8 && d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }
    /** A blow of this mob's own, scaled by its damage factor (an elite hits harder). */
    boolean hurt(Encounters.Run run, Mob m, Player p, double amount) { return hurtExact(run, m, p, amount * m.dmg); }
    /** A blow of exactly this size (at most MAX_HIT): true when the server accepted it and the player is still a valid target for a status. */
    boolean hurtExact(Encounters.Run run, Mob m, Player p, double amount) {
        if (!eligible(p, run) || m.e.isDead() || !m.e.isValid()) return false;
        double a = Math.min(MAX_HIT, amount);
        if (a <= 0) return false;
        Mob was = striker;
        Player victim = struck;
        striker = m; struck = p; landed = false;
        boolean ok = false;
        try { p.damage(a, m.e); } finally { ok = landed; striker = was; struck = victim; }
        return ok && eligible(p, run);
    }
    /** A blow with no attacker (embers, a death blast): eligible players only. */
    boolean hurtPlain(Encounters.Run run, Player p, double amount) {
        if (!eligible(p, run)) return false;
        p.damage(Math.min(MAX_HIT, amount));
        return eligible(p, run);
    }
    void heal(LivingEntity e, double amount) {
        if (e == null || e.isDead() || !e.isValid() || amount <= 0) return;
        e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + amount));
    }
    void status(Player p, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect old = p.getPotionEffect(type);
        if (old == null || old.getAmplifier() < amplifier || (old.getAmplifier() == amplifier && old.getDuration() < ticks)) p.addPotionEffect(new PotionEffect(type, ticks, amplifier));
    }
    /** Pushes a player away from a point, level, and up a little. */
    void knock(Player p, Location from, double h, double v) {
        Vector away = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 1e-6) away = new Vector(1, 0, 0);
        away.normalize().multiply(h);
        away.setY(v);
        p.setVelocity(away);
    }
    double scale(LivingEntity e) { return host.scale(e); }
    /** A cooldown made quicker by the floor and the depth of the room (a telegraph never is). */
    int cd(Mob m, int cooldown) { return Math.max(30, (int) Math.round(cooldown * m.pace)); }
    static double[] eye(LivingEntity e) { Location l = e.getEyeLocation(); return new double[]{l.getX(), l.getY(), l.getZ()}; }
    static double[] chest(Player p) { Location l = p.getLocation(); return new double[]{l.getX(), l.getY() + (p.isSneaking() ? .8 : 1.0), l.getZ()}; }
    /** Blocks that stop a shot or a sight line; unloaded chunks count as walls and are never loaded by looking. */
    BestiaryBallistics.Solid solid(final World w) {
        return new BestiaryBallistics.Solid() {
            public boolean at(int x, int y, int z) { return !w.isChunkLoaded(x >> 4, z >> 4) || w.getBlockAt(x, y, z).getType().isSolid(); }
        };
    }
    boolean visible(Encounters.Run run, double[] a, double[] b) { return BestiaryBallistics.sees(solid(run.world), run.room, a, b); }
    private static boolean lava(Material m) { return m == Material.LAVA || m == Material.STATIONARY_LAVA || m == Material.MAGMA || m == Material.FIRE; }
    /** A body fits here: its feet and head cells are passable and not lava. */
    boolean free(World w, double x, double y, double z) {
        if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return false;
        for (int dy = 0; dy <= 1; dy++) { Material m = w.getBlockAt((int) Math.floor(x), (int) Math.floor(y) + dy, (int) Math.floor(z)).getType(); if (m.isSolid() || lava(m)) return false; }
        return true;
    }
    /** Standable ground: a solid floor under feet and head room above, and no lava or fire in the cell or under it. */
    boolean ground(World w, double x, double y, double z) {
        if (!free(w, x, y, z)) return false;
        Material under = w.getBlockAt((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z)).getType();
        return under.isSolid() && under != Material.CACTUS && under != Material.MAGMA;
    }
    // Particles and sounds: small counts, harmless kinds, never a glow.
    void puff(World w, Particle p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) { w.spawnParticle(p, x, y, z, n, dx, dy, dz, speed); }
    /** Coloured spell particles: with a count of 0 the offsets are the colour. */
    void tint(World w, double x, double y, double z, double r, double g, double b) { w.spawnParticle(Particle.SPELL_MOB, x, y, z, 0, r, g, b, 1); }
    void ring(World w, Particle p, double x, double y, double z, double radius, int n) {
        for (int i = 0; i < n; i++) { double a = Math.PI * 2 * i / n; w.spawnParticle(p, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0, 0, 0, 0); }
    }
    void line(World w, Particle p, double ax, double ay, double az, double bx, double by, double bz, double step) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = (int) Math.min(40, Math.max(1, Math.floor(len / step)));
        for (int i = 1; i <= n; i++) { double f = i / (double) n; w.spawnParticle(p, ax + dx * f, ay + dy * f, az + dz * f, 1, 0, 0, 0, 0); }
    }
    void sound(World w, double x, double y, double z, Sound s, float volume, float pitch) { w.playSound(new Location(w, x, y, z), s, volume, pitch); }
    void sound(Location l, Sound s, float volume, float pitch) { l.getWorld().playSound(l, s, volume, pitch); }
    RunData data(Encounters.Run run) { return runs.get(run.key); }

    // ================================================================ listener
    private static Entity shooter(Entity e) {
        if (e instanceof Projectile) { ProjectileSource s = ((Projectile) e).getShooter(); if (s instanceof Entity) return (Entity) s; }
        return e;
    }
    private static boolean dungeonMob(Entity e) { return e != null && e.getScoreboardTags().contains(Encounters.TAG); }

    /**
     * Dungeon mobs hunt players only (an iron golem would otherwise fight the room's monsters), and a gunner never lets the
     * vanilla AI pick a target: its own rules aim and walk.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        try {
            if (e.getTarget() == null || !dungeonMob(e.getEntity())) return;
            Mob m = mobs.get(e.getEntity().getUniqueId());
            if (!(e.getTarget() instanceof Player) || (m != null && m.gun != null)) e.setCancelled(true);
        } catch (RuntimeException ex) { fail("target", null, null, ex); }
    }

    /** Vanilla endermen dodge arrows by teleporting away and ignore them; in the dungeon that would make them immune to the player's guns and spells. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void escape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent e) {
        try { if (dungeonMob(e.getEntity())) e.setCancelled(true); }
        catch (RuntimeException ex) { fail("escape", null, null, ex); }
    }

    /** What happens to a blow before it lands: charges and fangs are bounded, shields deflect, wards absorb, armour holds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hurt(EntityDamageByEntityEvent e) {
        try {
            Entity victim = e.getEntity(), damager = e.getDamager(), src = shooter(damager);
            Thing th = things.get(damager.getUniqueId());
            if (th != null) {
                // Only an eligible player of the room is ever hurt by them, and never by more than the cap.
                Encounters.Run run = host.run(th.room);
                if (!(victim instanceof Player) || run == null || !eligible((Player) victim, run)) { e.setCancelled(true); return; }
                e.setDamage(Math.min(e.getDamage(), th.cap));
                return;
            }
            // The fangs a vanilla evoker conjures (a Doom Herald's own are things, above) bite whatever stands in them: not the room's mobs, and a herald's never harder than its blow.
            if (damager instanceof EvokerFangs) {
                LivingEntity owner = ((EvokerFangs) damager).getOwner();
                if (!(victim instanceof Player) && dungeonMob(victim)) { e.setCancelled(true); return; }
                Mob om = owner == null ? null : mobs.get(owner.getUniqueId());
                if (victim instanceof Player && om != null && om.def != null) e.setDamage(Math.min(e.getDamage(), Math.min(MAX_HIT, 1.2 * om.base * om.dmg)));
            }
            Mob v = mobs.get(victim.getUniqueId());
            if (v != null) {
                if (v.def != null && v.up && (v.def.kind == BestiaryCatalog.Kind.GUARD || v.def.kind == BestiaryCatalog.Kind.BASH)
                    && (damager instanceof Projectile || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) && frontal(v.e, src)) {
                    World w = v.e.getWorld();
                    Location l = v.e.getLocation();
                    sound(w, l.getX(), l.getY(), l.getZ(), Sound.ITEM_SHIELD_BLOCK, .9f, 1f);
                    puff(w, Particle.CRIT, l.getX(), l.getY() + 1.2, l.getZ(), 6, .4, .4, .4, .1);
                    e.setCancelled(true);
                    return;
                }
                if (v.ward > 0) {
                    double d = e.getDamage();
                    Location l = v.e.getEyeLocation();
                    if (d <= v.ward) { v.ward -= d; puff(l.getWorld(), Particle.SPELL_INSTANT, l.getX(), l.getY(), l.getZ(), 8, .4, .4, .4, .1); sound(l, Sound.ITEM_SHIELD_BLOCK, .6f, 1.6f); e.setCancelled(true); return; }
                    e.setDamage(d - v.ward);
                    v.ward = 0;
                    puff(l.getWorld(), Particle.SPELL_INSTANT, l.getX(), l.getY(), l.getZ(), 16, .5, .5, .5, .2);
                    sound(l, Sound.ITEM_SHIELD_BREAK, .8f, 1.2f);
                }
                if (v.elite == BestiaryCatalog.Mod.ARMOURED) e.setDamage(e.getDamage() * BestiaryCatalog.Mod.ARMOURED.taken);
            }
            Mob a = src == null ? null : mobs.get(src.getUniqueId());
            if (a != null && a.def != null && a.def.meleeCap > 0 && victim instanceof Player && !(striker != null && striker.e == src) && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                e.setDamage(Math.min(e.getDamage(), a.def.meleeCap * a.base));
        } catch (RuntimeException ex) { fail("hurt", null, null, ex); }
    }
    /** Whether the attacker stands within about sixty-five degrees of dead ahead of the mob. */
    private static boolean frontal(LivingEntity mob, Entity attacker) {
        if (attacker == null) return false;
        Location l = mob.getLocation();
        Vector face = l.getDirection().setY(0), to = attacker.getLocation().toVector().subtract(l.toVector()).setY(0);
        if (face.lengthSquared() < 1e-6 || to.lengthSquared() < 1e-6) return false;
        return face.normalize().dot(to.normalize()) > .42;
    }

    /** After a blow landed: what the attacker's kind does on a hit, thorns and splitting on the victim's side, and the blow in flight is accepted. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        try {
            Entity victim = e.getEntity(), src = shooter(e.getDamager());
            double dealt = e.getFinalDamage();
            if (striker != null && victim == struck && src == striker.e) landed = dealt > 0;
            if (dealt <= 0) return;
            Mob a = src == null ? null : mobs.get(src.getUniqueId());
            if (a != null && victim instanceof Player) onHit(a, (Player) victim, dealt);
            Mob v = mobs.get(victim.getUniqueId());
            if (v != null) onHurt(v, e, src, dealt);
        } catch (RuntimeException ex) { fail("hit", null, null, ex); }
    }
    private void onHit(Mob a, Player p, double dealt) {
        Location l = a.e.getEyeLocation();
        if (a.elite == BestiaryCatalog.Mod.VAMPIRIC) { heal(a.e, Math.min(6, dealt * .6)); puff(l.getWorld(), Particle.HEART, l.getX(), l.getY() + .5, l.getZ(), 2, .3, .2, .3, 0); }
        else if (a.elite == BestiaryCatalog.Mod.CHILLING) { status(p, PotionEffectType.SLOW, 40, 0); puff(p.getWorld(), Particle.SNOW_SHOVEL, p.getLocation().getX(), p.getLocation().getY() + 1, p.getLocation().getZ(), 8, .3, .5, .3, .05); }
        if (a.sp == Species.CANDLE_WISP) { if (p.getFireTicks() < 40) p.setFireTicks(40); puff(p.getWorld(), Particle.FLAME, p.getLocation().getX(), p.getLocation().getY() + 1, p.getLocation().getZ(), 6, .3, .5, .3, .02); }
        if (a.sp == Species.MIRE_LEECH) { heal(a.e, Math.min(4, dealt)); puff(l.getWorld(), Particle.SPELL_MOB, l.getX(), l.getY(), l.getZ(), 0, .2, .7, .1, 1); }
    }
    private void onHurt(Mob v, EntityDamageByEntityEvent e, Entity src, double dealt) {
        Encounters.Run run = host.run(v.room);
        if (run == null) return;
        if (v.elite == BestiaryCatalog.Mod.THORNED && src instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            hurtExact(run, v, (Player) src, Math.max(1.5, .35 * v.base));
            puff(v.e.getWorld(), Particle.CRIT_MAGIC, v.e.getLocation().getX(), v.e.getLocation().getY() + 1, v.e.getLocation().getZ(), 6, .4, .5, .4, .1);
        }
        if (v.def != null && v.def.kind == BestiaryCatalog.Kind.SPLIT) abilities.split(run, v, dealt);
    }

    /** A volatile elite's death starts its blast; every other mob just leaves the registry. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void died(EntityDeathEvent e) {
        try {
            Mob m = mobs.remove(e.getEntity().getUniqueId());
            if (m == null) return;
            RunData rd = runs.get(m.room);
            if (rd == null) return;
            if (rd.mobs.remove(m.id) != null && m.gun != null && rd.armed > 0) rd.armed--;
            rd.m.add("died." + m.sp.name());
            if (m.elite == BestiaryCatalog.Mod.VOLATILE) {
                Location l = e.getEntity().getLocation();
                rd.blasts.add(new Blast(l.getX(), l.getY(), l.getZ(), 3.2, Math.min(MAX_HIT, 1.3 * m.base), now + 20));
                rd.m.add("ability.blasts");
                sound(l, Sound.ENTITY_TNT_PRIMED, .8f, 1.2f);
            }
        } catch (RuntimeException ex) { fail("died", null, null, ex); }
    }
}
