package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

/**
 * Generation 7 (owner 2026-10-05: "I also want surprises and Easter eggs to make exploring more fun."). The runtime half of
 * SecretCatalog: DungeonPlugin calls tick() every tick with the active rooms, Encounters calls sleep() when a room goes quiet, and
 * this class is a Bukkit listener.
 *
 * What a room hides is decided by its hash alone (SecretCatalog.plan). The first time someone is in a room its blocks are placed
 * (a loose stone in a bay, an Easter egg's build, the peddler's camp: always inside one 7 x 7 bay, never on a lane, the chest, a
 * door or a station; the bay's own furnishings are cleared first) and its creatures follow. Run worlds are deleted with the run,
 * so nothing is ever cleaned up block by block; creatures are removed whenever the room sleeps (a thief or a peddler is never left
 * behind) and come back, with their unfinished business, when it wakes. Creatures the secrets spawn themselves carry the tag
 * jpd_secret (Encounters.natural lets them through); the mimic, Monstro and Dinnerbone's guard are room adds from
 * Encounters.summon and never count for a room's clear.
 *
 * Logs: DUNGEON_SECRET_PLACED, DUNGEON_SECRET_FOUND (kind, room, floor, player), DUNGEON_SECRET_USED, DUNGEON_SECRET_THIEF,
 * DUNGEON_SECRET_DEFEATED, DUNGEON_SECRET_SKIPPED and DUNGEON_SECRET_FAILED, and DUNGEON_SECRET_METRICS when the plugin closes.
 * Config: secrets (true), secrets-rate (1: every rate times this; 0 hides nothing).
 */
public class Secrets implements org.bukkit.event.Listener {
    /** Every creature a secret spawns itself carries this tag before it enters the world. */
    public static final String TAG = "jpd_secret";
    private static final int WAITS = 90;
    final DungeonPlugin plugin;
    private final Map<String, Spot> spots = new LinkedHashMap<String, Spot>();
    private final Set<UUID> tracked = new HashSet<UUID>();
    private final Map<UUID, Long> clicked = new HashMap<UUID, Long>();
    private long ticks;
    private boolean enabled = true;
    private double rate = 1;
    private final int[] planned = new int[SecretCatalog.Kind.values().length], placedN = new int[planned.length], foundN = new int[planned.length], usedN = new int[planned.length];
    private int thievesSpawned, thievesCaught, thievesEscaped, thievesLeft, mimicsKilled, monstrosKilled, guardsKilled, tradesMade, failures, skipped;

    Secrets(DungeonPlugin plugin) { this.plugin = plugin; }

    private static final class Site {
        final SecretBuilds.Build build; final int ax, az;
        Site(SecretBuilds.Build build, int ax, int az) { this.build = build; this.ax = ax; this.az = az; }
    }
    /** One room's secrets for the life of its run world. 0 pending, 1 done, 2 given up, for the placements. */
    private static final class Spot {
        final String key; final Layout.Room room; final SecretCatalog.Plan plan; final int phase;
        boolean failed; long firstHere = -1; int waits, refused;
        final Set<Integer> usedBays = new HashSet<Integer>(), blockedBays = new HashSet<Integer>();
        final Map<Integer, Boolean> freeBays = new HashMap<Integer, Boolean>();
        final EnumSet<SecretCatalog.Kind> foundKinds = EnumSet.noneOf(SecretCatalog.Kind.class), usedKinds = EnumSet.noneOf(SecretCatalog.Kind.class);
        int stone, egg, camp, stoneX, stoneY, stoneZ; boolean stoneOpen; Site eggSite, campSite;
        long triggerSeen = -1; boolean mimicBurst, mimicDead; LivingEntity mimic; int mimicTries;
        boolean thiefDone; LivingEntity thief; long thiefBorn; Location goal; long goalAt;
        LivingEntity peddler; int[] uses;
        boolean hatchOpen, mobDead, swordDrawn, swordHinted; LivingEntity eggMob; ArmorStand stand; int mobTries;
        final Set<UUID> listening = new HashSet<UUID>(); long recordAt;
        Spot(String key, Layout.Room room, SecretCatalog.Plan plan) { this.key = key; this.room = room; this.plan = plan; this.phase = (int) Math.floorMod(Layout.mix(key.hashCode()), 60L); }
    }
    private boolean due(Spot s, int every) { return (ticks + s.phase) % every == 0; }
    /**
     * Tracks a creature the world accepted. One it refused (another plugin cancelled the spawn) is dropped, and after three refusals
     * the room's secrets switch themselves off and say so, instead of trying every second for the rest of the run.
     */
    private boolean admit(Spot s, Entity e, SecretCatalog.Kind k) {
        if (e != null && e.isValid()) { tracked.add(e.getUniqueId()); return true; }
        if (e != null) e.remove();
        if (++s.refused >= 3 && !s.failed) {
            s.failed = true; failures++;
            plugin.getLogger().warning("DUNGEON_SECRET_FAILED room=" + s.key + " plan=" + s.plan + " error=SpawnRefused kind=" + k.id());
        }
        return false;
    }

    // ---------------------------------------------------------------- logging
    private static String clip(Object o) { String m = String.valueOf(o).replace('\n', ' ').replace('\r', ' '); return m.length() > 120 ? m.substring(0, 120) : m; }
    private void log(String event, Spot s, SecretCatalog.Kind k, Player p, String extra) {
        plugin.getLogger().info(SecretCatalog.line(event, k, s.key, s.room.floor, p == null ? null : p.getName(), extra));
    }
    /** A player met this secret: counted and logged once per room and kind. */
    private void found(Spot s, SecretCatalog.Kind k, Player p) {
        if (!s.foundKinds.add(k)) return;
        foundN[k.ordinal()]++;
        log("DUNGEON_SECRET_FOUND", s, k, p, null);
    }
    private void used(Spot s, SecretCatalog.Kind k, Player p, String extra) {
        if (s.usedKinds.add(k)) usedN[k.ordinal()]++;
        log("DUNGEON_SECRET_USED", s, k, p, extra);
    }
    private void fail(Spot s, Encounters.Run run, RuntimeException ex) {
        if (s.failed) return;
        s.failed = true; failures++;
        try { retire(s, "failed"); } catch (RuntimeException again) { /* the room is switched off anyway */ }
        plugin.getLogger().warning("DUNGEON_SECRET_FAILED room=" + s.key + " plan=" + s.plan + " error=" + ex.getClass().getSimpleName() + " " + clip(ex.getMessage()));
    }

    // ---------------------------------------------------------------- the tick
    void tick(Collection<Encounters.Run> active) {
        ticks++;
        if (ticks % 100 == 1) {
            enabled = plugin.getConfig().getBoolean("secrets", true);
            rate = Math.max(0, Math.min(3, plugin.getConfig().getDouble("secrets-rate", 1)));
        }
        if (!enabled || active == null) return;
        for (Encounters.Run run : active) {
            if (run == null || run.world == null || !SecretCatalog.eligible(run.room)) continue;
            Spot s = spots.get(run.key);
            if (s == null) {
                s = new Spot(run.key, run.room, SecretCatalog.plan(run.room, rate));
                spots.put(run.key, s);
                for (SecretCatalog.Kind k : s.plan.kinds()) planned[k.ordinal()]++;
            }
            if (!s.plan.any() || s.failed) continue;
            try { step(s, run); } catch (RuntimeException ex) { fail(s, run, ex); }
        }
        if (ticks % 200 == 0) purge();
    }
    private void step(Spot s, Encounters.Run run) {
        boolean here = !run.players.isEmpty();
        if (here && s.firstHere < 0) s.firstHere = ticks;
        if (here && due(s, 10)) place(s, run);
        SecretCatalog.Plan p = s.plan;
        if (p.cache) stoneStep(s, run);
        if (p.mimic) mimicStep(s, run);
        if (p.thief) thiefStep(s, run, here);
        if (p.peddler) peddlerStep(s, run, here);
        if (p.egg != null && s.egg == 1) eggStep(s, run, here);
    }
    private List<Player> inRoom(Encounters.Run run) {
        List<Player> out = new ArrayList<Player>();
        for (UUID id : run.players) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(run.world)) out.add(p); }
        return out;
    }
    private static double flat(Location a, double x, double z) { return Math.hypot(a.getX() - x, a.getZ() - z); }
    private Location loc(World w, Site site, double[] o) { return new Location(w, site.ax + .5 + o[0], Layout.FLOOR + 1 + o[1], site.az + .5 + o[2], site.build.yaw(), 0f); }

    // ---------------------------------------------------------------- placing blocks
    private enum Fit { YES, LATER, NEVER }
    private static boolean loaded(World w, int x, int z) { return w.isChunkLoaded(x >> 4, z >> 4); }
    /**
     * A floor a build may stand on: solid, and not magma, soul sand or a cactus. Not "occluding": the floor's own glowstone lights
     * (one in every eight cells each way, so inside every bay) are solid but not occluding in this API, and a bay would never fit.
     */
    static boolean solidFloor(Material m) { return m != null && m.isSolid() && m != Material.MAGMA && m != Material.SOUL_SAND && m != Material.CACTUS; }
    /** Whether the world agrees that the bay can be built in: chunks loaded, solid floor, and nobody (nothing alive) standing in the way. */
    private Fit fit(World w, int ax, int az) {
        if (!loaded(w, ax - 3, az - 3) || !loaded(w, ax + 3, az - 3) || !loaded(w, ax - 3, az + 3) || !loaded(w, ax + 3, az + 3)) return Fit.LATER;
        for (int x = ax - 3; x <= ax + 3; x++) for (int z = az - 3; z <= az + 3; z++) if (!solidFloor(w.getBlockAt(x, Layout.FLOOR, z).getType())) return Fit.NEVER;
        for (Entity e : w.getNearbyEntities(new Location(w, ax + .5, Layout.FLOOR + 3, az + .5), 5, 5, 5)) {
            if (!(e instanceof LivingEntity) || e instanceof ArmorStand) continue;
            Location l = e.getLocation();
            if (Math.abs(l.getX() - (ax + .5)) <= 4.6 && Math.abs(l.getZ() - (az + .5)) <= 4.6) return Fit.LATER;
        }
        return Fit.YES;
    }
    /** SecretCatalog.free is pure and costs a few dozen generator calls: asked once per bay and room. */
    private boolean free(Spot s, int bi, int[] bay) {
        Boolean known = s.freeBays.get(bi);
        if (known == null) { known = SecretCatalog.free(s.room, bay[0], bay[1]); s.freeBays.put(bi, known); }
        return known;
    }
    private void place(Spot s, Encounters.Run run) {
        SecretCatalog.Plan p = s.plan;
        if (p.egg != null && s.egg == 0) s.egg = build(s, run, p.egg);
        if (p.peddler && s.camp == 0) s.camp = build(s, run, SecretCatalog.Kind.PEDDLER);
        if (p.cache && s.stone == 0) s.stone = stone(s, run);
    }
    /** Builds an egg or the camp in the first bay (in the kind's own order) that is free and fits: 1 built, 0 try again later, 2 no bay. */
    private int build(Spot s, Encounters.Run run, SecretCatalog.Kind kind) {
        Layout.Room r = s.room;
        World w = run.world;
        for (int bi : SecretCatalog.bayOrder(r, kind)) {
            if (s.usedBays.contains(bi) || s.blockedBays.contains(bi)) continue;
            int[] bay = SecretCatalog.bay(r, bi);
            if (!free(s, bi, bay)) continue;
            Fit f = fit(w, bay[0], bay[1]);
            if (f == Fit.NEVER) continue;
            if (f == Fit.LATER) { if (++s.waits <= WAITS) return 0; s.waits = 0; s.blockedBays.add(bi); continue; }
            SecretBuilds.Build b = SecretBuilds.of(kind).turned(SecretCatalog.turn(r, bay[1]));
            apply(w, r, bay[0], bay[1], b);
            s.usedBays.add(bi);
            Site site = new Site(b, bay[0], bay[1]);
            if (kind == SecretCatalog.Kind.PEDDLER) s.campSite = site; else s.eggSite = site;
            placedN[kind.ordinal()]++;
            log("DUNGEON_SECRET_PLACED", s, kind, null, "bay=" + bi + "/" + SecretCatalog.bays(r) + " turn=" + b.turn + " blocks=" + b.pieces.size());
            return 1;
        }
        skipped++;
        log("DUNGEON_SECRET_SKIPPED", s, kind, null, "reason=nobay");
        return 2;
    }
    /** Clears the bay's own furnishings (the 7 x 7 columns above the floor, up to the roof) and lays the build's blocks, signs last. */
    private void apply(World w, Layout.Room r, int ax, int az, SecretBuilds.Build b) {
        int top = r.roof() - 1;
        for (int x = ax - 3; x <= ax + 3; x++) for (int z = az - 3; z <= az + 3; z++) for (int y = Layout.FLOOR + 1; y <= top; y++) {
            Block k = w.getBlockAt(x, y, z);
            if (k.getType() != Material.AIR) k.setTypeIdAndData(0, (byte) 0, false);
        }
        for (SecretBuilds.Piece p : b.pieces) {
            Block k = w.getBlockAt(ax + p.dx, Layout.FLOOR + 1 + p.dy, az + p.dz);
            k.setTypeIdAndData(p.id(), (byte) p.data(), false);
            if (p.text != null && k.getState() instanceof Sign) {
                Sign sign = (Sign) k.getState();
                for (int i = 0; i < 4; i++) sign.setLine(i, i < p.text.length ? p.text[i] : "");
                sign.update(true, false);
            }
        }
    }
    /** The loose stone: one cracked stone brick on a free floor cell of a free bay. */
    private int stone(Spot s, Encounters.Run run) {
        Layout.Room r = s.room;
        World w = run.world;
        for (int bi : SecretCatalog.bayOrder(r, SecretCatalog.Kind.CACHE)) {
            if (s.usedBays.contains(bi) || s.blockedBays.contains(bi)) continue;
            int[] bay = SecretCatalog.bay(r, bi);
            if (!free(s, bi, bay)) continue;
            Fit f = fit(w, bay[0], bay[1]);
            if (f == Fit.NEVER) continue;
            if (f == Fit.LATER) { if (++s.waits <= WAITS) return 0; s.waits = 0; s.blockedBays.add(bi); continue; }
            for (int c : SecretCatalog.cells(r)) {
                int x = bay[0] - 3 + c % 7, z = bay[1] - 3 + c / 7;
                Block at = w.getBlockAt(x, Layout.FLOOR + 1, z);
                if (at.getType() != Material.AIR || w.getBlockAt(x, Layout.FLOOR + 2, z).getType() != Material.AIR) continue;
                at.setTypeIdAndData(98, (byte) 2, false);
                s.stoneX = x; s.stoneY = Layout.FLOOR + 1; s.stoneZ = z;
                // The stone claims its bay: a build placed here later would clear the stone away with the bay's furnishings.
                s.usedBays.add(bi);
                placedN[SecretCatalog.Kind.CACHE.ordinal()]++;
                log("DUNGEON_SECRET_PLACED", s, SecretCatalog.Kind.CACHE, null, "bay=" + bi + "/" + SecretCatalog.bays(r));
                return 1;
            }
        }
        skipped++;
        log("DUNGEON_SECRET_SKIPPED", s, SecretCatalog.Kind.CACHE, null, "reason=nocell");
        return 2;
    }

    // ---------------------------------------------------------------- the loose stone
    private void stoneStep(Spot s, Encounters.Run run) {
        if (s.stone != 1 || s.stoneOpen || run.players.isEmpty() || !due(s, 30)) return;
        Location c = new Location(run.world, s.stoneX + .5, s.stoneY + .5, s.stoneZ + .5);
        for (Player p : inRoom(run)) if (p.getLocation().distanceSquared(c) <= 100) {
            run.world.spawnParticle(Particle.BLOCK_DUST, c.getX(), c.getY() + .55, c.getZ(), 5, .22, .05, .22, 0, new MaterialData(Material.SMOOTH_BRICK, (byte) 2));
            return;
        }
    }
    private void openStone(Spot s, Player p, Block b) {
        s.stoneOpen = true;
        b.setTypeIdAndData(0, (byte) 0, false);
        World w = b.getWorld();
        Location c = b.getLocation().add(.5, .5, .5);
        w.playSound(c, Sound.BLOCK_STONE_BREAK, 1f, .8f);
        w.spawnParticle(Particle.BLOCK_CRACK, c, 30, .3, .3, .3, .05, new MaterialData(Material.SMOOTH_BRICK, (byte) 2));
        SecretLoot.Haul h = SecretLoot.cache(s.room);
        for (ItemStack i : h.items) w.dropItemNaturally(c, i);
        if (h.exp > 0) p.giveExp(h.exp);
        p.sendMessage(ChatColor.GOLD + "The stone crumbles and a small cache lies behind it" + (h.relic ? " - and something that hums." : "."));
        p.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + SecretCatalog.lore(s.room));
        found(s, SecretCatalog.Kind.CACHE, p);
        used(s, SecretCatalog.Kind.CACHE, p, "items=" + h.items.size() + " relic=" + h.relic);
    }

    // ---------------------------------------------------------------- the mimic
    private void mimicStep(Spot s, Encounters.Run run) {
        if (!run.state.triggered || s.mimicDead || run.state.cleared) return;
        if (s.mimic != null && s.mimic.isValid()) return;
        s.mimic = null;
        if (s.triggerSeen < 0) s.triggerSeen = ticks;
        // A moment after the guardians wake (their title first), the chest itself bites.
        if (ticks - s.triggerSeen < 25 || s.mimicTries >= 8 || !due(s, 10)) return;
        World w = run.world;
        Location chest = new Location(w, s.room.cx() + .5, Layout.FLOOR + 1, s.room.cz() + 4.5);
        if (!loaded(w, chest.getBlockX(), chest.getBlockZ())) return;
        LivingEntity m = plugin.encounters.summon(run, EncounterCatalog.Species.MIMIC, chest, SecretCatalog.mimicHealth(s.room), SecretCatalog.mimicDamage(s.room), ChatColor.DARK_RED + "Mimic");
        if (m == null) { s.mimicTries++; return; }
        SecretMobs.tag(m, "mimic", s.key);
        tracked.add(m.getUniqueId());
        m.setCustomNameVisible(true);
        EntityEquipment eq = m.getEquipment();
        if (eq != null) { eq.setHelmet(new ItemStack(Material.CHEST)); eq.setHelmetDropChance(0); }
        s.mimic = m;
        w.playSound(chest, Sound.BLOCK_CHEST_CLOSE, 1f, .6f);
        w.playSound(chest, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, .7f);
        w.spawnParticle(Particle.SMOKE_LARGE, chest.getX(), chest.getY() + .8, chest.getZ(), 14, .35, .3, .35, .02);
        w.spawnParticle(Particle.CRIT, chest.getX(), chest.getY() + .9, chest.getZ(), 16, .4, .3, .4, .2);
        if (!s.mimicBurst) {
            s.mimicBurst = true;
            List<Player> here = inRoom(run);
            for (Player p : here) p.sendTitle(ChatColor.RED + "The reliquary snaps at you!", ChatColor.GRAY + "A mimic bursts out of the chest", 3, 40, 10);
            found(s, SecretCatalog.Kind.MIMIC, here.isEmpty() ? null : here.get(0));
        }
    }

    // ---------------------------------------------------------------- the gilded thief
    private void thiefStep(Spot s, Encounters.Run run, boolean here) {
        if (s.thiefDone) return;
        if (s.thief == null) {
            if (!here || s.firstHere < 0 || ticks - s.firstHere < SecretCatalog.THIEF_DELAY_TICKS || !run.populated || !due(s, 10)) return;
            if (run.state.cleared) { s.thiefDone = true; return; }
            spawnThief(s, run);
            return;
        }
        if (!s.thief.isValid()) { s.thief = null; s.thiefDone = true; return; }
        if (ticks - s.thiefBorn >= SecretCatalog.THIEF_LIFE_TICKS) { escape(s, run); return; }
        if (due(s, 4)) flee(s, run);
    }
    private void spawnThief(Spot s, Encounters.Run run) {
        World w = run.world;
        List<Player> players = inRoom(run);
        if (players.isEmpty()) return;
        // The station farthest from every player, so the thief starts across the room.
        double[] start = SecretCatalog.thiefStart(s.room, where(players));
        if (start == null || !loaded(w, (int) Math.floor(start[0]), (int) Math.floor(start[1]))) return;
        Location pick = new Location(w, start[0], Layout.FLOOR + 1, start[1]);
        Zombie z = SecretMobs.thief(w, pick, s.key, SecretCatalog.thiefHealth(s.room));
        if (!admit(s, z, SecretCatalog.Kind.THIEF)) { s.thiefDone = true; return; }
        s.thief = z; s.thiefBorn = ticks; s.goal = null; thievesSpawned++;
        w.playSound(pick, Sound.ENTITY_VILLAGER_YES, 1f, 1.7f);
        w.spawnParticle(Particle.VILLAGER_HAPPY, pick.getX(), pick.getY() + 1, pick.getZ(), 10, .3, .4, .3, 0);
        for (Player p : players) p.sendMessage(ChatColor.GOLD + "A gilded thief darts across the room with a sack of loot. Catch it before it gets away!");
        found(s, SecretCatalog.Kind.THIEF, players.get(0));
        log("DUNGEON_SECRET_THIEF", s, SecretCatalog.Kind.THIEF, null, "result=spawned");
    }
    /** The thief runs for the station that is farthest from every player it can reach; players who corner it can catch it. */
    private void flee(Spot s, Encounters.Run run) {
        LivingEntity t = s.thief;
        Location me = t.getLocation();
        List<Player> players = inRoom(run);
        double nearest = 1e9;
        for (Player p : players) nearest = Math.min(nearest, p.getLocation().distance(me));
        if (players.isEmpty() || nearest > 30) { SecretMobs.halt(t); s.goal = null; return; }
        boolean replan = s.goal == null || ticks >= s.goalAt || me.distanceSquared(s.goal) < 2.5 || nearest < 6 || SecretMobs.idle(t);
        if (!replan) return;
        double[] goal = SecretCatalog.thiefGoal(s.room, me.getX(), me.getZ(), where(players));
        if (goal == null) return;
        Location best = new Location(run.world, goal[0], Layout.FLOOR + 1, goal[1]);
        if (SecretMobs.runTo(t, best.getX(), best.getY(), best.getZ(), nearest < 8 ? 1.3 : 1.0)) { s.goal = best; s.goalAt = ticks + 40; }
    }
    private static double[][] where(List<Player> players) {
        double[][] out = new double[players.size()][];
        for (int i = 0; i < out.length; i++) out[i] = new double[]{players.get(i).getLocation().getX(), players.get(i).getLocation().getZ()};
        return out;
    }
    /** Not caught in time: a cackle, a puff of smoke, gone with the loot. */
    private void escape(Spot s, Encounters.Run run) {
        LivingEntity t = s.thief;
        Location at = t.getLocation();
        run.world.playSound(at, Sound.ENTITY_WITCH_AMBIENT, 1.2f, 1.3f);
        run.world.spawnParticle(Particle.SMOKE_LARGE, at.getX(), at.getY() + 1, at.getZ(), 20, .3, .5, .3, .02);
        run.world.spawnParticle(Particle.SPELL_WITCH, at.getX(), at.getY() + 1, at.getZ(), 24, .4, .6, .4, .1);
        for (Player p : inRoom(run)) p.sendMessage(ChatColor.GOLD + "The gilded thief cackles and vanishes with the loot.");
        discard(t);
        s.thief = null; s.thiefDone = true; thievesEscaped++;
        log("DUNGEON_SECRET_THIEF", s, SecretCatalog.Kind.THIEF, null, "result=escaped");
    }

    // ---------------------------------------------------------------- the lost peddler
    private void peddlerStep(Spot s, Encounters.Run run, boolean here) {
        if (s.camp != 1 || !here || s.campSite == null) return;
        World w = run.world;
        if (s.peddler == null || !s.peddler.isValid()) {
            s.peddler = null;
            if (!due(s, 20)) return;
            Location at = loc(w, s.campSite, s.campSite.build.spawn);
            if (!loaded(w, at.getBlockX(), at.getBlockZ())) return;
            Villager v = SecretMobs.peddler(w, at, s.key, SecretLoot.trades(s.room, s.uses));
            if (admit(s, v, SecretCatalog.Kind.PEDDLER)) s.peddler = v;
            return;
        }
        if (!s.foundKinds.contains(SecretCatalog.Kind.PEDDLER) && due(s, 10)) {
            for (Player p : inRoom(run)) if (p.getLocation().distanceSquared(s.peddler.getLocation()) <= 49) {
                p.sendMessage(ChatColor.GOLD + "[Lost Peddler] " + ChatColor.GRAY + "Psst. Emeralds and bottles o' enchanting buy things down here. Right-click me.");
                found(s, SecretCatalog.Kind.PEDDLER, p);
                break;
            }
        }
    }
    /** Remembers what has been bought, so a peddler who comes back with the room has not restocked. */
    private void saveUses(Spot s) {
        if (s.peddler == null || !s.peddler.isValid() || !(s.peddler instanceof Villager)) return;
        List<org.bukkit.inventory.MerchantRecipe> rec = ((Villager) s.peddler).getRecipes();
        int[] u = new int[Math.min(rec.size(), SecretCatalog.TRADES.length)];
        for (int i = 0; i < u.length; i++) u[i] = rec.get(i).getUses();
        s.uses = u;
    }

    // ---------------------------------------------------------------- the Easter eggs
    private void eggStep(Spot s, Encounters.Run run, boolean here) {
        Site site = s.eggSite;
        World w = run.world;
        if (site == null) return;
        SecretCatalog.Kind kind = s.plan.egg;
        Location centre = new Location(w, site.ax + .5, Layout.FLOOR + 2, site.az + .5);
        // Found: someone close enough to see it.
        if (here && !s.foundKinds.contains(kind) && due(s, 10)) {
            for (Player p : inRoom(run)) if (flat(p.getLocation(), centre.getX(), centre.getZ()) <= 8 && Math.abs(p.getLocation().getY() - centre.getY()) <= 8) { found(s, kind, p); break; }
        }
        switch (kind) {
            case BASEMENT: basementStep(s, run, here, site); break;
            case CRYPT: cryptStep(s, run, here, site); break;
            case JEB: jebStep(s, run, here, site); break;
            case DINNERBONE: guardStep(s, run, here, site); break;
            case SWORD: swordStep(s, run, here, site); break;
            default: break;
        }
    }
    private boolean watched(Encounters.Run run, Location at, double range) {
        for (Player p : inRoom(run)) if (p.getLocation().distanceSquared(at) <= range * range) return true;
        return false;
    }
    private void basementStep(Spot s, Encounters.Run run, boolean here, Site site) {
        World w = run.world;
        int[] f = site.build.focus;
        int hx = site.ax + f[0], hy = Layout.FLOOR + 1 + f[1], hz = site.az + f[2];
        if (!s.hatchOpen && here) {
            for (Player p : inRoom(run)) {
                Location l = p.getLocation();
                if (l.getBlockX() == hx && l.getBlockZ() == hz && l.getBlockY() == hy) { openHatch(s, run, p); break; }
            }
        }
        if (here && due(s, 12) && watched(run, new Location(w, hx + .5, hy, hz + .5), 16)) {
            for (double[] e : site.build.emit) { Location l = loc(w, site, e); w.spawnParticle(Particle.DRIP_WATER, l.getX(), l.getY(), l.getZ(), 1, .04, 0, .04, 0); }
        }
        // Monstro, once out, is a room add that must be finished: if the room slept and woke with him alive, he is back.
        if (s.hatchOpen && !s.mobDead && !run.state.cleared && here && (s.eggMob == null || !s.eggMob.isValid()) && s.mobTries < 6 && due(s, 20)) { s.eggMob = null; summonMonstro(s, run, site); }
    }
    private void openHatch(Spot s, Encounters.Run run, Player p) {
        if (s.hatchOpen || s.eggSite == null) return;
        if (run == null) run = plugin.encounters.active.get(s.key);
        if (run == null) return;
        Site site = s.eggSite;
        int[] f = site.build.focus;
        Block hatch = run.world.getBlockAt(site.ax + f[0], Layout.FLOOR + 1 + f[1], site.az + f[2]);
        if (hatch.getTypeId() == 96) hatch.setData((byte) (hatch.getData() | 4), false);
        s.hatchOpen = true;
        Location at = hatch.getLocation().add(.5, .3, .5);
        run.world.playSound(at, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 1f, .6f);
        run.world.playSound(at, Sound.ENTITY_SLIME_SQUISH, 1.2f, .5f);
        for (Player v : inRoom(run)) v.sendMessage(ChatColor.DARK_RED + "The hatch bursts open. Something in the basement has been crying for a very long time.");
        found(s, SecretCatalog.Kind.BASEMENT, p);
        used(s, SecretCatalog.Kind.BASEMENT, p, "monstro=true");
        summonMonstro(s, run, site);
    }
    private void summonMonstro(Spot s, Encounters.Run run, Site site) {
        int[] f = site.build.focus;
        Location near = new Location(run.world, site.ax + f[0] + .5, Layout.FLOOR + 1, site.az + f[2] + .5);
        double hp = SecretCatalog.monstroHealth(s.room), damage = SecretCatalog.monstroDamage(s.room);
        LivingEntity m = plugin.encounters.summon(run, EncounterCatalog.Species.SLIME, near, hp, damage, ChatColor.DARK_RED + "Monstro");
        if (m == null) { s.mobTries++; return; }
        SecretMobs.tag(m, "monstro", s.key);
        tracked.add(m.getUniqueId());
        if (m instanceof Slime) ((Slime) m).setSize(SecretCatalog.MONSTRO_SIZE);
        // setSize resets a slime's health and speed to its size's own: put ours back.
        m.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
        m.setHealth(hp);
        if (m.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) m.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        m.setCustomNameVisible(true);
        s.eggMob = m;
        run.world.playSound(near, Sound.ENTITY_ENDERDRAGON_GROWL, .6f, 1.4f);
    }
    private void cryptStep(Spot s, Encounters.Run run, boolean here, Site site) {
        if (!due(s, 20)) return;
        World w = run.world;
        Location jb = new Location(w, site.ax + site.build.focus[0] + .5, Layout.FLOOR + 1 + site.build.focus[1] + .5, site.az + site.build.focus[2] + .5);
        Set<UUID> now = new HashSet<UUID>();
        for (Player p : inRoom(run)) if (p.getLocation().distanceSquared(jb) <= 40 * 40) {
            now.add(p.getUniqueId());
            // Record 13 lasts about three minutes: start it for whoever comes near, and again when it has run out.
            if (!s.listening.contains(p.getUniqueId()) || ticks >= s.recordAt) p.playEffect(jb, Effect.RECORD_PLAY, Material.GOLD_RECORD);
        }
        if (ticks >= s.recordAt) s.recordAt = ticks + 20 * 175;
        for (Iterator<UUID> it = s.listening.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (now.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.playEffect(jb, Effect.RECORD_PLAY, 0);
            it.remove();
        }
        s.listening.addAll(now);
        if (!now.isEmpty()) w.spawnParticle(Particle.NOTE, jb.getX(), jb.getY() + 1.1, jb.getZ(), 1, .3, .2, .3, 1);
    }
    private void stopMusic(Spot s) {
        if (s.listening.isEmpty() || s.eggSite == null) { s.listening.clear(); return; }
        Site site = s.eggSite;
        for (UUID id : s.listening) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.playEffect(new Location(p.getWorld(), site.ax + .5, Layout.FLOOR + 2, site.az + .5), Effect.RECORD_PLAY, 0);
        }
        s.listening.clear();
    }
    private void jebStep(Spot s, Encounters.Run run, boolean here, Site site) {
        if (!here) return;
        World w = run.world;
        if (s.eggMob == null || !s.eggMob.isValid()) {
            s.eggMob = null;
            if (!due(s, 20)) return;
            Location at = loc(w, site, site.build.spawn);
            if (!loaded(w, at.getBlockX(), at.getBlockZ())) return;
            Sheep sheep = SecretMobs.jeb(w, at, s.key);
            if (admit(s, sheep, SecretCatalog.Kind.JEB)) s.eggMob = sheep;
            return;
        }
        // The client cycles a "jeb_" sheep's colours itself; this steps the server's colour so the sheep is a rainbow everywhere.
        if (due(s, 20) && s.eggMob instanceof Sheep) ((Sheep) s.eggMob).setColor(DyeColor.values()[(int) ((ticks / 20) % DyeColor.values().length)]);
    }
    private void guardStep(Spot s, Encounters.Run run, boolean here, Site site) {
        if (!here || s.mobDead || run.state.cleared || !due(s, 20)) return;
        if (s.eggMob != null && s.eggMob.isValid()) return;
        s.eggMob = null;
        if (s.mobTries >= 6) return;
        World w = run.world;
        Location near = loc(w, site, site.build.spawn);
        if (!loaded(w, near.getBlockX(), near.getBlockZ())) return;
        // Named "Dinnerbone": the client draws any mob with that name upside down.
        LivingEntity g = plugin.encounters.summon(run, EncounterCatalog.Species.ZOMBIE, near, SecretCatalog.guardHealth(s.room), SecretCatalog.guardDamage(s.room), "Dinnerbone");
        if (g == null) { s.mobTries++; return; }
        SecretMobs.tag(g, "guard", s.key);
        tracked.add(g.getUniqueId());
        EntityEquipment eq = g.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.IRON_HELMET)); eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE)); eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setItemInMainHandDropChance(0);
        }
        s.eggMob = g;
        w.playSound(near, Sound.ENTITY_ZOMBIE_AMBIENT, 1f, .6f);
    }
    private void swordStep(Spot s, Encounters.Run run, boolean here, Site site) {
        if (s.swordDrawn || !here) return;
        World w = run.world;
        if (s.stand == null || !s.stand.isValid()) {
            s.stand = null;
            if (!due(s, 20)) return;
            Location at = loc(w, site, site.build.stand);
            if (!loaded(w, at.getBlockX(), at.getBlockZ())) return;
            ArmorStand stand = SecretMobs.swordStand(w, at, s.key, SecretLoot.sword(s.room));
            if (admit(s, stand, SecretCatalog.Kind.SWORD)) s.stand = stand;
            return;
        }
        if (due(s, 40) && watched(run, s.stand.getLocation(), 14)) {
            double[] e = site.build.emit[0];
            Location l = loc(w, site, e);
            w.spawnParticle(Particle.END_ROD, l.getX(), l.getY(), l.getZ(), 2, .15, .2, .15, .01);
        }
        // Once the room is at peace, whoever comes near is told how to draw it (the three ways in).
        if (!s.swordHinted && run.state.cleared && due(s, 10)) {
            for (Player p : inRoom(run)) if (flat(p.getLocation(), site.ax + .5, site.az + .5) <= 6) {
                s.swordHinted = true;
                p.sendMessage(ChatColor.GOLD + "The sword hums now that the room is at peace. Strike it, right-click it or crouch beside it to draw it.");
                break;
            }
        }
    }
    /** Drawing the sword: only once the room is at peace (absolved). */
    private void draw(Spot s, Player p) {
        if (s.swordDrawn) return;
        Encounters.Run run = plugin.encounters.activate(p.getWorld(), s.room);
        if (run == null) return;
        if (!run.state.cleared) {
            p.sendMessage(ChatColor.GRAY + "The blade does not stir. Perhaps when the room is at peace.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, .8f, .8f);
            return;
        }
        s.swordDrawn = true;
        Site site = s.eggSite;
        ItemStack sword = SecretLoot.sword(s.room);
        for (ItemStack extra : p.getInventory().addItem(sword).values()) p.getWorld().dropItemNaturally(p.getLocation(), extra);
        if (s.stand != null) { discard(s.stand); s.stand = null; }
        Location at = new Location(p.getWorld(), site.ax + .5, Layout.FLOOR + 4, site.az + .5);
        p.getWorld().playSound(at, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, .7f);
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.3f);
        p.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, at, 30, .3, .5, .3, .08);
        p.sendMessage(ChatColor.GOLD + "You draw the " + sword.getItemMeta().getDisplayName() + ChatColor.GOLD + " from the stone.");
        found(s, SecretCatalog.Kind.SWORD, p);
        used(s, SecretCatalog.Kind.SWORD, p, "item=" + sword.getType().name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- interaction
    private static void deny(PlayerInteractEvent e) { e.setCancelled(true); e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.DENY); }
    /** Two clicks of one right-click (both hands, both entity events) count once. */
    private boolean fresh(Player p) {
        long now = ticks;
        Long last = clicked.get(p.getUniqueId());
        if (last != null && now - last < 4) return false;
        clicked.put(p.getUniqueId(), now);
        return true;
    }
    /**
     * Blocks answer a right-click and a strike (left-click) alike: the browser client never sends an empty-handed right-click on a
     * plain block (found 2026-09-29), so a secret that only listened for the right-click could not be reached with a bare hand.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(PlayerInteractEvent e) {
        Action a = e.getAction();
        if ((a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK) || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !plugin.inside(b.getWorld())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        Layout.Room r = plugin.room(b.getLocation());
        String key = plugin.roomKey(b.getWorld(), r);
        Spot s = spots.get(key);
        if (s == null || s.failed) return;
        try {
            // Reaching across a doorway never uses another room's secret.
            if (!plugin.roomKey(p.getLocation()).equals(key)) return;
            if (s.stone == 1 && !s.stoneOpen && b.getX() == s.stoneX && b.getY() == s.stoneY && b.getZ() == s.stoneZ) { deny(e); if (fresh(p)) openStone(s, p, b); return; }
            if (s.egg == 1 && s.eggSite != null) eggClick(s, e, p, b);
        } catch (RuntimeException ex) { fail(s, null, ex); }
    }
    private void eggClick(Spot s, PlayerInteractEvent e, Player p, Block b) {
        Site site = s.eggSite;
        SecretBuilds.Piece piece = site.build.at(b.getX() - site.ax, b.getY() - (Layout.FLOOR + 1), b.getZ() - site.az);
        // Only what is still standing there answers: an eaten cake is air, and a click on whatever replaced a block is no click on the egg.
        if (piece == null || b.getTypeId() != piece.id()) return;
        World w = b.getWorld();
        Location at = b.getLocation().add(.5, .5, .5);
        switch (s.plan.egg) {
            case DUCK:
                if (piece.id() != 35) return;
                deny(e);
                if (!fresh(p)) return;
                w.playSound(at, Sound.ENTITY_CHICKEN_HURT, 1f, 2f);
                w.spawnParticle(Particle.NOTE, at.getX(), at.getY() + .7, at.getZ(), 1, .2, .2, .2, 1);
                p.sendMessage(ChatColor.YELLOW + "Squeak!");
                found(s, SecretCatalog.Kind.DUCK, p);
                if (!s.usedKinds.contains(SecretCatalog.Kind.DUCK)) used(s, SecretCatalog.Kind.DUCK, p, "squeak=true");
                return;
            case BASEMENT:
                if (piece.id() != 96) return;
                deny(e);
                if (fresh(p)) openHatch(s, plugin.encounters.activate(w, s.room), p);
                return;
            case CAKE:
                if (piece.id() != 92) return;
                deny(e);
                if (!fresh(p)) return;
                b.setTypeIdAndData(0, (byte) 0, false);
                w.playSound(at, Sound.ENTITY_VILLAGER_NO, 1f, .7f);
                w.spawnParticle(Particle.CLOUD, at.getX(), at.getY() + .3, at.getZ(), 18, .3, .2, .3, .02);
                p.sendMessage(ChatColor.GRAY + "The cake is a lie.");
                p.giveExp(3);
                found(s, SecretCatalog.Kind.CAKE, p);
                used(s, SecretCatalog.Kind.CAKE, p, null);
                return;
            case CRYPT:
                if (piece.id() != 84) return;
                deny(e);
                if (!fresh(p)) return;
                w.playSound(at, Sound.BLOCK_NOTE_BASS, .6f, .5f);
                found(s, SecretCatalog.Kind.CRYPT, p);
                if (!s.usedKinds.contains(SecretCatalog.Kind.CRYPT)) used(s, SecretCatalog.Kind.CRYPT, p, "record=13");
                return;
            case DESK:
                if (piece.id() != 159 && piece.id() != 95) return;
                deny(e);
                if (!fresh(p)) return;
                boolean older = piece.id() == 159 && (piece.data() == 8 || piece.data() == 15);
                p.sendMessage(ChatColor.GRAY + (older ? "The older machine hums, content. It dug all of this." : "The newer machine blinks. It tidied up afterwards."));
                w.playSound(at, Sound.BLOCK_NOTE_BASS, .5f, older ? .7f : 1.4f);
                found(s, SecretCatalog.Kind.DESK, p);
                if (!s.usedKinds.contains(SecretCatalog.Kind.DESK)) used(s, SecretCatalog.Kind.DESK, p, null);
                return;
            case SWORD:
                if (piece.id() != 1 && piece.id() != 48) return;
                deny(e);
                if (fresh(p)) draw(s, p);
                return;
            default:
        }
    }
    /**
     * A strike at one of the peaceful secrets (the sword's armour stand, the jeb_ sheep, the peddler) harms nothing; a strike at the
     * sword draws it, like a right-click (the browser client's entity clicks are not the only way in).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void strike(EntityDamageByEntityEvent e) {
        String kind = SecretMobs.kind(e.getEntity());
        if (!"sword".equals(kind) && !"jeb".equals(kind) && !"peddler".equals(kind)) return;
        e.setCancelled(true);
        Entity hitter = e.getDamager();
        if (!(hitter instanceof Player)) return;
        Player p = (Player) hitter;
        Spot s = spots.get(SecretMobs.room(e.getEntity()));
        if (s == null || s.failed || !plugin.roomKey(p.getLocation()).equals(s.key) || !fresh(p)) return;
        try {
            if (kind.equals("sword") && s.egg == 1) draw(s, p);
            else if (kind.equals("jeb")) { found(s, SecretCatalog.Kind.JEB, p); p.playSound(e.getEntity().getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1f); if (!s.usedKinds.contains(SecretCatalog.Kind.JEB)) used(s, SecretCatalog.Kind.JEB, p, null); }
        } catch (RuntimeException ex) { fail(s, null, ex); }
    }
    /** Crouching beside the sword in the stone, once its room is at peace, draws it too (a way in that needs no click at all). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void crouch(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !plugin.inside(p.getWorld()) || p.getGameMode() == GameMode.SPECTATOR) return;
        Layout.Room r = plugin.room(p.getLocation());
        Spot s = spots.get(plugin.roomKey(p.getWorld(), r));
        if (s == null || s.failed || s.egg != 1 || s.plan.egg != SecretCatalog.Kind.SWORD || s.swordDrawn || s.eggSite == null) return;
        Site site = s.eggSite;
        Location l = p.getLocation();
        if (flat(l, site.ax + .5, site.az + .5) > 2.6 || Math.abs(l.getY() - (Layout.FLOOR + 1)) > 3) return;
        Encounters.Run run = plugin.encounters.active.get(s.key);
        if (run == null || !run.state.cleared || !fresh(p)) return;
        try { draw(s, p); } catch (RuntimeException ex) { fail(s, null, ex); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityClick(PlayerInteractEntityEvent e) { touch(e, e.getRightClicked(), e.getPlayer(), e.getHand()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityClickAt(PlayerInteractAtEntityEvent e) { touch(e, e.getRightClicked(), e.getPlayer(), e.getHand()); }
    private void touch(PlayerInteractEntityEvent e, Entity who, Player p, EquipmentSlot hand) {
        String kind = SecretMobs.kind(who);
        if (kind == null) return;
        Spot s = spots.get(SecretMobs.room(who));
        if (s == null || !plugin.roomKey(p.getLocation()).equals(s.key)) return;
        try {
            if (kind.equals("peddler")) { if (hand == EquipmentSlot.HAND) found(s, SecretCatalog.Kind.PEDDLER, p); return; }
            if (kind.equals("sword")) { e.setCancelled(true); if (hand == EquipmentSlot.HAND && fresh(p) && s.egg == 1) draw(s, p); return; }
            if (kind.equals("jeb")) { e.setCancelled(true); if (hand == EquipmentSlot.HAND && fresh(p)) { found(s, SecretCatalog.Kind.JEB, p); p.playSound(who.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1f); if (!s.usedKinds.contains(SecretCatalog.Kind.JEB)) used(s, SecretCatalog.Kind.JEB, p, null); } }
        } catch (RuntimeException ex) { fail(s, null, ex); }
    }
    /** A trade at the peddler's: counted, and what is left of the stock is remembered. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trade(org.bukkit.event.inventory.InventoryClickEvent e) {
        org.bukkit.inventory.Inventory top = e.getView().getTopInventory();
        if (top.getType() != org.bukkit.event.inventory.InventoryType.MERCHANT || e.getRawSlot() != 2 || e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        if (!(top.getHolder() instanceof Villager) || !(e.getWhoClicked() instanceof Player)) return;
        Villager v = (Villager) top.getHolder();
        if (!"peddler".equals(SecretMobs.kind(v))) return;
        Spot s = spots.get(SecretMobs.room(v));
        if (s == null) return;
        tradesMade++;
        used(s, SecretCatalog.Kind.PEDDLER, (Player) e.getWhoClicked(), "item=" + e.getCurrentItem().getType().name().toLowerCase(Locale.ROOT));
        final Villager again = v;
        Bukkit.getScheduler().runTask(plugin, () -> { if (again.isValid()) { s.peddler = again; saveUses(s); } });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void manipulate(PlayerArmorStandManipulateEvent e) { if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true); }
    /** The thief never hunts anyone, and nothing hunts the peaceful ones. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent e) {
        String mine = SecretMobs.kind(e.getEntity());
        if ("thief".equals(mine)) { e.setCancelled(true); e.setTarget(null); return; }
        String theirs = SecretMobs.kind(e.getTarget());
        if (theirs != null && !theirs.equals("mimic") && !theirs.equals("monstro") && !theirs.equals("guard") && !theirs.equals("thief") && e.getEntity() instanceof Monster) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- the fallen
    /** A thief's own drops and experience are never the zombie's. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void dying(EntityDeathEvent e) {
        if ("thief".equals(SecretMobs.kind(e.getEntity()))) { e.getDrops().clear(); e.setDroppedExp(0); }
    }
    /** The bonus is dropped after every other handler has had its say (Encounters clears what a room's own mobs drop). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void died(EntityDeathEvent e) {
        LivingEntity en = e.getEntity();
        String kind = SecretMobs.kind(en);
        if (kind == null) return;
        Spot s = spots.get(SecretMobs.room(en));
        tracked.remove(en.getUniqueId());
        if (s == null) return;
        try {
            SecretLoot.Haul h;
            SecretCatalog.Kind k;
            switch (kind) {
                case "thief": h = SecretLoot.thiefPurse(s.room); k = SecretCatalog.Kind.THIEF; s.thief = null; s.thiefDone = true; thievesCaught++; break;
                case "mimic": h = SecretLoot.mimicHoard(s.room); k = SecretCatalog.Kind.MIMIC; s.mimic = null; s.mimicDead = true; mimicsKilled++; break;
                case "monstro": h = SecretLoot.monstroPurse(s.room); k = SecretCatalog.Kind.BASEMENT; s.eggMob = null; s.mobDead = true; monstrosKilled++; break;
                case "guard": h = SecretLoot.guardPurse(s.room); k = SecretCatalog.Kind.DINNERBONE; s.eggMob = null; s.mobDead = true; guardsKilled++; break;
                default: return;
            }
            Location at = en.getLocation();
            for (ItemStack i : h.items) at.getWorld().dropItemNaturally(at, i);
            Player killer = en.getKiller();
            if (killer != null && h.exp > 0) killer.giveExp(h.exp);
            if (kind.equals("thief")) {
                at.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.5f);
                if (killer != null) killer.sendMessage(ChatColor.GOLD + "You caught the gilded thief! Its purse spills across the floor.");
            }
            if (killer != null) { found(s, k, killer); used(s, k, killer, "defeated=" + kind); }
            log("DUNGEON_SECRET_DEFEATED", s, k, killer, "what=" + kind + " items=" + h.items.size() + " relic=" + h.relic);
            if (kind.equals("thief")) log("DUNGEON_SECRET_THIEF", s, k, killer, "result=caught");
        } catch (RuntimeException ex) { fail(s, null, ex); }
    }
    /** Nothing the secrets spawn is ever saved with a chunk: it goes when its chunk does, and strays found on a load are removed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void chunkLoad(ChunkLoadEvent e) {
        if (!plugin.inside(e.getWorld())) return;
        for (Entity en : e.getChunk().getEntities()) if (en.getScoreboardTags().contains(TAG) && !tracked.contains(en.getUniqueId())) en.remove();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent e) {
        if (!plugin.inside(e.getWorld())) return;
        for (Entity en : e.getChunk().getEntities()) if (en.getScoreboardTags().contains(TAG)) {
            // A peddler's stock is remembered before he goes.
            if (en instanceof Villager) { Spot s = spots.get(SecretMobs.room(en)); if (s != null && s.peddler == en) saveUses(s); }
            tracked.remove(en.getUniqueId());
            en.remove();
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { clicked.remove(e.getPlayer().getUniqueId()); for (Spot s : spots.values()) s.listening.remove(e.getPlayer().getUniqueId()); }

    // ---------------------------------------------------------------- sleeping, closing
    private void discard(Entity e) {
        if (e == null) return;
        tracked.remove(e.getUniqueId());
        if (e.isValid()) e.remove();
    }
    /** Everything alive that a secret spawned goes (a thief or a peddler is never left behind); blocks stay, they are harmless. */
    private void retire(Spot s, String why) {
        if (s.thief != null) {
            if (s.thief.isValid() && !s.thiefDone) { thievesLeft++; log("DUNGEON_SECRET_THIEF", s, SecretCatalog.Kind.THIEF, null, "result=left why=" + why); }
            discard(s.thief); s.thief = null; s.thiefDone = true;
        }
        if (s.peddler != null) { saveUses(s); discard(s.peddler); s.peddler = null; }
        discard(s.stand); s.stand = null;
        // Room adds (the mimic, Monstro, the guard) were removed with the room's adds; only the references are dropped.
        discard(s.mimic); s.mimic = null;
        discard(s.eggMob); s.eggMob = null;
        stopMusic(s);
        s.goal = null;
    }
    void sleep(Encounters.Run run) {
        if (run == null) return;
        Spot s = spots.get(run.key);
        if (s == null) return;
        try { retire(s, "sleep"); } catch (RuntimeException ex) { fail(s, run, ex); }
    }
    /** Spots of run worlds that are gone go too. */
    private void purge() {
        Iterator<Map.Entry<String, Spot>> it = spots.entrySet().iterator();
        while (it.hasNext()) {
            String key = it.next().getKey();
            int slash = key.indexOf('/');
            World w = slash < 0 ? null : Bukkit.getWorld(key.substring(0, slash));
            if (w == null || !plugin.inside(w)) it.remove();
        }
        clicked.keySet().retainAll(Collections.<UUID>emptySet());
    }
    void close() {
        for (Spot s : spots.values()) { try { retire(s, "close"); } catch (RuntimeException ignored) { /* shutting down */ } }
        plugin.getLogger().info(SecretCatalog.metrics(spots.size(), planned, placedN, foundN, usedN,
            "thieves=spawned:" + thievesSpawned + ",caught:" + thievesCaught + ",escaped:" + thievesEscaped + ",left:" + thievesLeft + " mimicsKilled=" + mimicsKilled + " monstrosKilled=" + monstrosKilled
            + " guardsKilled=" + guardsKilled + " trades=" + tradesMade + " skipped=" + skipped + " failures=" + failures));
        spots.clear(); tracked.clear(); clicked.clear();
    }
}
