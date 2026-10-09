package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Generation 7 (owner 2026-10-05): "physical dangers as well, such as lava pools, falling stalactites, and other things of
 * that nature." The floors draw lava pools, stalactite points, cracked floors, vents and falls into their rooms (pure, in
 * their generators; PerilMarks says where); this class makes them act, room by room, from the same marks, so what a
 * player sees is where the danger comes from. DungeonPlugin calls tick() every tick with the active rooms and Encounters
 * calls sleep() when a room goes quiet; each active room gets a PerilRoom (the rules, in PerilRoom and PerilRules) and a
 * PerilWorld (the stage). Falling stalactites are real falling blocks that never place themselves: their landing is
 * cancelled here and becomes the hit. Everything a room changed is restored from the generator when it sleeps, when its
 * chunk is about to unload, and when the plugin closes.
 *
 * Logs: DUNGEON_PERILS_ARMED (once per room: what it holds), DUNGEON_PERIL_HIT (the first few hundred), DUNGEON_PERIL_FAILED
 * (every new failure path, once per room and reason) and DUNGEON_PERILS (counts) at close. Nothing is logged per tick; no
 * coordinates, device data or addresses ever appear.
 */
public class Perils implements Listener {
    /** Scoreboard tag of every falling block this class spawns. */
    static final String TAG = "jpd_peril";
    /** At most this many of its falling blocks exist at once, across all rooms. */
    static final int MAX_FLYING = 96;
    private static final int LOG_CAP = 4096, HIT_LOG_CAP = 400;

    /** A room that has perils: its run, stage and rules. */
    private static final class Entry {
        final Encounters.Run run;final PerilWorld stage;final PerilRoom room;boolean armed;
        Entry(Perils owner, Encounters.Run run) {
            this.run = run;stage = new PerilWorld(owner, run);
            room = new PerilRoom(run.room, PerilMarks::at, stage, PerilRules.GENERATOR, owner.metrics, Math.max(1, Math.min(1.45, owner.plugin.dangerMultiplier(run.world))));
        }
    }
    /** One falling block in flight and the room that dropped it. */
    private static final class Flight {
        final Entity entity;final Entry entry;
        Flight(Entity entity, Entry entry) { this.entity = entity;this.entry = entry; }
    }

    final DungeonPlugin plugin;
    private final PerilMetrics metrics = new PerilMetrics();
    private final Map<String, Entry> rooms = new LinkedHashMap<>();private final Map<UUID, Flight> flights = new HashMap<>();
    private final Set<String> warned = new HashSet<>(), armed = new HashSet<>(), disabled = new HashSet<>(), live = new HashSet<>();
    private long now;private int hitsLogged;

    Perils(DungeonPlugin plugin) { this.plugin = plugin; }

    // ---------------------------------------------------------------- the tick
    /** Called by DungeonPlugin once per server tick with Encounters' active rooms, after their presence was refreshed. Never throws. */
    void tick(Collection<Encounters.Run> active) {
        now++;
        try {
            live.clear();
            for (Encounters.Run run : new ArrayList<>(active)) {
                if (run == null || run.world == null || PerilRules.origin(run.room) || disabled.contains(run.key)) continue;
                live.add(run.key);
                Entry e = rooms.get(run.key);
                // One failing room is switched off on its own: it never stops the others.
                try {
                    // A room that slept and woke again is a new Run: whatever the old one still had out goes back first.
                    if (e != null && e.run != run) { rooms.remove(run.key);quiet(e);e = null; }
                    if (e == null) { e = new Entry(this, run);rooms.put(run.key, e);metrics.rooms++; }
                    PerilField field = e.room.field;
                    // A room with nothing marked costs nothing; one still being read has no one to act on yet.
                    if (field.ready() && field.total() == 0) { if (!e.armed) announce(e);continue; }
                    e.room.tick(now, field.ready() ? bodies(run) : Collections.<PerilStage.Body>emptyList());
                    if (!e.armed && field.ready()) announce(e);
                } catch (RuntimeException ex) { disable(run, e, ex); }
            }
            // A room whose run left the active map without sleeping still gets its blocks back.
            if (rooms.size() > live.size())
                for (Iterator<Entry> it = rooms.values().iterator();it.hasNext();) { Entry e = it.next();if (!live.contains(e.run.key)) { it.remove();quiet(e); } }
            sweep();
        } catch (RuntimeException ex) { failure("tick", null, "tick-" + ex.getClass().getSimpleName() + "-" + ex.getMessage()); }
    }
    /** Encounters calls this when a room goes to sleep (and for every room of a closing run world): restore and forget. */
    void sleep(Encounters.Run run) {
        if (run == null) return;
        Entry e = rooms.get(run.key);
        // Only the room's own incarnation: a late call for an older Run must not retire the one that replaced it.
        if (e != null && e.run == run) { rooms.remove(run.key);quiet(e); }
    }
    /** Plugin disable: every room restores, every falling block ends, the counts are logged. */
    void close() {
        for (Entry e : new ArrayList<>(rooms.values())) quiet(e);
        rooms.clear();
        for (Flight f : new ArrayList<>(flights.values())) if (f.entity.isValid()) f.entity.remove();
        flights.clear();
        plugin.getLogger().info(metrics.line());
    }
    private void quiet(Entry e) {
        try { e.room.sleep(); } catch (RuntimeException ex) { failure(e.run.key, null, "sleep-" + ex.getClass().getSimpleName() + "-" + ex.getMessage()); }
    }
    /** A room whose perils threw is switched off for good, said once, and gets its blocks back (e is null when its stage could not even be made). */
    private void disable(Encounters.Run run, Entry e, RuntimeException ex) {
        rooms.remove(run.key);if (disabled.size() < LOG_CAP) disabled.add(run.key);
        failure(run.key, null, "disabled-" + ex.getClass().getSimpleName() + "-" + ex.getMessage());
        if (e != null) quiet(e);
    }
    /** Each room's first reading of its marks: what it holds, said once. */
    private void announce(Entry e) {
        e.armed = true;PerilField f = e.room.field;
        if (f.total() == 0 || armed.size() >= LOG_CAP || !armed.add(e.run.key)) return;
        metrics.armed++;
        plugin.getLogger().info("DUNGEON_PERILS_ARMED room=" + e.run.key + " floor=" + e.run.room.floor + " stalactite=" + f.count(PerilMarks.Kind.STALACTITE) + " lava=" + f.count(PerilMarks.Kind.LAVA)
            + " crumble=" + f.count(PerilMarks.Kind.CRUMBLE) + " geyser=" + f.count(PerilMarks.Kind.GEYSER) + " lavafall=" + f.count(PerilMarks.Kind.LAVA_FALL));
    }

    // ---------------------------------------------------------------- who may be hurt
    /** Only these players are ever hurt: alive, in Survival or Adventure, standing in this very room and outside the arrival circle. */
    boolean eligible(Player p, Encounters.Run run) {
        if (p == null || run == null || !p.isOnline() || p.isDead() || !run.players.contains(p.getUniqueId()) || !p.getWorld().equals(run.world) || plugin.sanctuary == null) return false;
        GameMode g = p.getGameMode();
        if (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) return false;
        Location l = p.getLocation();return run.key.equals(plugin.roomKey(l)) && !plugin.sanctuary.contains(l);
    }
    private List<PerilStage.Body> bodies(Encounters.Run run) {
        if (run.players.isEmpty()) return Collections.emptyList();
        List<PerilStage.Body> out = new ArrayList<>(run.players.size());
        for (UUID id : run.players) {
            Player p = Bukkit.getPlayer(id);
            if (!eligible(p, run)) continue;
            Location l = p.getLocation();out.add(new PerilStage.Body(id, l.getX(), l.getY(), l.getZ()));
        }
        return out;
    }

    // ---------------------------------------------------------------- falling blocks
    int flying() { return flights.size(); }
    /** Registers a falling block of a live room; false when that room is gone. */
    boolean track(Entity f, Encounters.Run run) {
        Entry e = rooms.get(run.key);
        if (e == null || e.run != run) return false;
        flights.put(f.getUniqueId(), new Flight(f, e));return true;
    }
    void untrack(Entity f) { flights.remove(f.getUniqueId()); }
    /** A block that vanished without reporting its landing (it hit something that cannot hold it) is treated as landed at its column. */
    private void sweep() {
        if (flights.isEmpty()) return;
        List<Flight> lost = null;
        for (Iterator<Flight> it = flights.values().iterator();it.hasNext();) {
            Flight f = it.next();
            if (f.entity.isValid()) continue;
            it.remove();if (lost == null) lost = new ArrayList<>();lost.add(f);
        }
        if (lost == null) return;
        for (Flight f : lost) {
            if (rooms.get(f.entry.run.key) != f.entry) continue;
            try { f.entry.room.landed(f.entity, 0, PerilRules.FLOOR + 1, 0, bodies(f.entry.run)); } catch (RuntimeException ex) { disable(f.entry.run, f.entry, ex); }
        }
    }
    /** The landing is the strike: the block never becomes a block (cancelled even if something else already cancelled it). */
    @EventHandler(priority = EventPriority.HIGHEST) public void landed(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;
        Flight f = flights.remove(e.getEntity().getUniqueId());
        if (f == null) return;
        e.setCancelled(true);e.getEntity().remove();
        if (rooms.get(f.entry.run.key) != f.entry) return;
        try { f.entry.room.landed(e.getEntity(), e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), bodies(f.entry.run)); } catch (RuntimeException ex) { disable(f.entry.run, f.entry, ex); }
    }
    /** A saved chunk never holds a hole or a missing stalactite: what a room changed there goes back before it unloads. */
    @EventHandler(priority = EventPriority.MONITOR) public void chunkUnload(ChunkUnloadEvent e) {
        if (rooms.isEmpty() || !plugin.inside(e.getWorld())) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        for (Entry en : new ArrayList<>(rooms.values())) {
            if (!en.run.world.equals(e.getWorld())) continue;
            try { en.room.restoreChunk(cx, cz); } catch (RuntimeException ex) { failure(en.run.key, null, "unload-" + ex.getClass().getSimpleName() + "-" + ex.getMessage()); }
        }
    }
    /** None of this class's falling blocks is saved with a chunk; any left over from a crash goes on load. */
    @EventHandler(priority = EventPriority.MONITOR) public void chunkLoad(ChunkLoadEvent e) {
        if (!plugin.inside(e.getWorld())) return;
        for (Entity en : e.getChunk().getEntities()) if (en instanceof FallingBlock && en.getScoreboardTags().contains(TAG) && !flights.containsKey(en.getUniqueId())) en.remove();
    }

    // ---------------------------------------------------------------- logs
    /** A new failure path, said once per room and reason (and counted every time). */
    void failure(String room, PerilMarks.Kind kind, String reason) {
        metrics.failures++;
        String line = PerilRules.failure(room, kind, reason);
        if (warned.size() < LOG_CAP && warned.add(line)) plugin.getLogger().warning(line);
    }
    /** The first few hundred hits, with who and how hard: enough to explain a death without logging every tick of a long fight. */
    void hit(Encounters.Run run, Player p, PerilMarks.Kind source, double dealt) {
        if (hitsLogged++ >= HIT_LOG_CAP) return;
        plugin.getLogger().info("DUNGEON_PERIL_HIT room=" + run.key + " kind=" + source + " player=" + p.getName() + " damage=" + Math.round(dealt * 10) / 10.0 + " health=" + Math.round(p.getHealth() * 10) / 10.0);
    }
}
