package chat.jaspr.dungeon;

import java.util.*;

/**
 * Generation 7 (owner 2026-10-05): "physical dangers as well, such as lava pools, falling stalactites, and other things of
 * that nature." One live room's perils, pure: it decides, a PerilStage acts. Only eligible players are ever hurt, and only
 * after a warning of at least PerilRules.MIN_TELEGRAPH ticks; nothing here ever targets a cell PerilRules.allowed refuses;
 * every block it changes goes back from the generator (a mended floor, a regrown stalactite), whenever the room sleeps, its
 * chunk unloads or the plugin closes.
 *
 * Stalactites loosen when someone stands within two blocks beneath, dust and a crack warn for a second, then the tip (up to
 * three blocks) falls as falling blocks that never place themselves and hurt only what stands in their column. Cracked floor
 * gives way a moment after a player steps on it and mends three to five seconds later, never while a player is in the hole.
 * Vents and lava falls wake when someone is near, warn, erupt or splash in one burst, and rest. Lava and idle vents only
 * throw ambient particles. Nothing runs per tick without a player around, and nothing is logged per tick.
 */
final class PerilRoom {
    private static final PerilMarks.Kind STALACTITE = PerilMarks.Kind.STALACTITE, LAVA = PerilMarks.Kind.LAVA, CRUMBLE = PerilMarks.Kind.CRUMBLE,
        GEYSER = PerilMarks.Kind.GEYSER, LAVA_FALL = PerilMarks.Kind.LAVA_FALL;
    /** A stalactite's life: warning, falling, gone, cooling. A crumbling cell: cracking (TELEGRAPH), gone, cooling. */
    private static final int TELEGRAPH = 0, FALLING = 1, GONE = 2, COOL = 3;
    /** A vent's or a lava fall's cycle. */
    private static final int WAIT = 0, WARN = 1, BURST = 2, REST = 3;

    private static final class Fall {
        final int x, z;final int[][] column;int state = TELEGRAPH, removed;long warnAt, dropAt, deadline, regrowAt, coolUntil;boolean struck;
        final List<Object> handles = new ArrayList<>();
        Fall(int x, int z, int[][] column) { this.x = x;this.z = z;this.column = column; }
    }
    private static final class Crumble {
        final int x, z, block;int state = TELEGRAPH;long start, at, due, coolUntil, lastLift = -100;
        Crumble(int x, int z, int block, long now) { this.x = x;this.z = z;this.block = block;start = now; }
    }
    private static final class Spot {
        final int x, z;int state = WAIT;long start, at;
        Spot(int x, int z) { this.x = x;this.z = z; }
    }

    final Layout.Room room;final PerilField field;
    private final PerilStage stage;private final PerilRules.Blocks blocks;private final PerilMetrics metrics;private final double danger;private final Random random;
    private final Map<Long, Fall> falls = new LinkedHashMap<>();private final Map<Object, Fall> flying = new HashMap<>();private final Map<Long, int[][]> columns = new HashMap<>();
    private final Map<Long, Crumble> crumbles = new LinkedHashMap<>();private final Map<Long, Spot> vents = new LinkedHashMap<>(), spouts = new LinkedHashMap<>();
    private final Set<Long> deadFalls = new HashSet<>(), deadCrumbles = new HashSet<>();
    private long now, lastFall = -1000;private boolean asleep;

    /** marks: where the perils are; blocks: what the generator draws (restoration only ever reads it); danger: a rift's multiplier, 1 on a floor. */
    PerilRoom(Layout.Room room, PerilField.Source marks, PerilStage stage, PerilRules.Blocks blocks, PerilMetrics metrics, double danger) {
        this.room = room;field = new PerilField(room, marks);this.stage = stage;this.blocks = blocks;this.metrics = metrics;this.danger = Math.max(1, danger);
        random = new Random(room.hash ^ 0x5065726c73L);
    }

    // ---------------------------------------------------------------- the tick
    /** Once per server tick with every eligible player in the room (none when it is empty: timers still mend and regrow). */
    void tick(long now, List<PerilStage.Body> bodies) {
        if (asleep) return;
        this.now = now;
        if (!field.ready()) { field.build(PerilRules.BUILD_BUDGET);return; }
        if (field.total() == 0) return;
        stepFalls();stepCrumbles();stepSpots(vents, GEYSER, bodies);stepSpots(spouts, LAVA_FALL, bodies);
        if (bodies.isEmpty()) return;
        triggerFalls(bodies);triggerCrumbles(bodies);
        if (now % PerilRules.ARM_EVERY == 0) {
            arm(vents, GEYSER, PerilRules.GEYSER_ARM, PerilRules.MAX_VENTS, bodies);arm(spouts, LAVA_FALL, PerilRules.SPOUT_ARM, PerilRules.MAX_SPOUTS, bodies);
        }
        if (now % PerilRules.AMBIENT_EVERY == 0) ambient(bodies);
    }
    /** Blocks currently out of the world because of this room's perils. */
    int pending() {
        int n = 0;
        for (Fall f : falls.values()) n += f.removed;
        for (Crumble c : crumbles.values()) if (c.state == GONE) n++;
        return n;
    }
    int activeFalls() { int n = 0;for (Fall f : falls.values()) if (f.state == TELEGRAPH || f.state == FALLING) n++;return n; }
    /** Test hook: the tick the open crumbled cell at (x, z) is due to mend, or -1 when it is not open. */
    long mendDue(int x, int z) { Crumble c = crumbles.get(PerilRules.key(x, z));return c != null && c.state == GONE ? c.due : -1; }
    int crumbling() { return crumbles.size(); }
    int vents() { return vents.size(); }
    int spouts() { return spouts.size(); }
    boolean asleep() { return asleep; }

    // ---------------------------------------------------------------- falling stalactites
    private int[][] column(long key, int x, int z) {
        int[][] c = columns.get(key);
        if (c == null) { c = PerilRules.column(blocks, room, x, z);columns.put(key, c); }
        return c;
    }
    private void triggerFalls(List<PerilStage.Body> bodies) {
        if (field.count(STALACTITE) == 0 || now - lastFall < PerilRules.FALL_SPACING) return;
        int reach = (int) Math.ceil(PerilRules.STALACTITE_RADIUS) + 1;double r2 = PerilRules.STALACTITE_RADIUS * PerilRules.STALACTITE_RADIUS;
        for (PerilStage.Body b : bodies) {
            if (activeFalls() >= PerilRules.MAX_FALLS) return;
            int bx = (int) Math.floor(b.x), bz = (int) Math.floor(b.z), px = 0, pz = 0;double best = r2 + 1;int[][] pick = null;
            for (int cx = bx - reach; cx <= bx + reach; cx++) for (int cz = bz - reach; cz <= bz + reach; cz++) {
                if (!field.has(STALACTITE, cx, cz)) continue;
                double dx = b.x - (cx + .5), dz = b.z - (cz + .5), d = dx * dx + dz * dz;
                if (d > r2 || d >= best) continue;
                long k = PerilRules.key(cx, cz);
                if (deadFalls.contains(k)) continue;
                Fall f = falls.get(k);
                if (f != null && !(f.state == COOL && now >= f.coolUntil)) continue;
                int[][] col = column(k, cx, cz);
                if (col.length == 0) { deadFalls.add(k);stage.failed(STALACTITE, "empty-column");continue; }
                // The point must hang above their head to fall on them.
                if (b.y + 1.8 > col[0][0] + .5) continue;
                best = d;px = cx;pz = cz;pick = col;
            }
            if (pick != null) { start(px, pz, pick);lastFall = now;return; }
        }
    }
    private void start(int x, int z, int[][] col) {
        Fall f = new Fall(x, z, col);f.warnAt = now;f.dropAt = now + PerilRules.STALACTITE_TELEGRAPH;falls.put(PerilRules.key(x, z), f);
        stage.sound(PerilStage.Snd.CRACK, x + .5, col[0][0], z + .5, .9f, .7f);stage.fx(PerilStage.Fx.DUST, x + .5, col[0][0] + .1, z + .5, 6, .3, col[0][1]);
        stage.note(STALACTITE, PerilRules.WARN, x, z);
    }
    private void stepFalls() {
        if (falls.isEmpty()) return;
        for (Iterator<Fall> it = falls.values().iterator();it.hasNext();) {
            Fall f = it.next();
            switch (f.state) {
                case TELEGRAPH: warn(f);break;
                case FALLING: if (now >= f.deadline) lose(f);break;
                case GONE: if (now >= f.regrowAt) regrow(f);break;
                default: if (now >= f.coolUntil) it.remove();
            }
        }
    }
    /** Falling dust at the tip and a shadow of cloud where it will land, a rumble halfway, then the drop. */
    private void warn(Fall f) {
        long age = now - f.warnAt;double cx = f.x + .5, cz = f.z + .5;int tip = f.column[0][0];
        if (age % 4 == 0) {
            stage.fx(PerilStage.Fx.DUST, cx, tip + .1, cz, 3, .25, f.column[0][1]);stage.fx(PerilStage.Fx.SHADOW, cx, PerilRules.FLOOR + 1.1, cz, 2, .3, 0);
        }
        if (age == 10) stage.sound(PerilStage.Snd.RUMBLE, cx, tip, cz, .8f, .6f);
        if (now >= f.dropAt) drop(f);
    }
    /** The world must still hold exactly what the generator drew in those cells; anything else is left alone. */
    private void drop(Fall f) {
        int n = Math.min(PerilRules.FALL_SEGMENT, f.column.length);
        for (int i = 0;i < n;i++) {
            int cur = stage.get(f.x, f.column[i][0], f.z);
            if (cur < 0) { cool(f, now + PerilRules.STALACTITE_COOLDOWN);return; }
            if ((cur & 4095) != (f.column[i][1] & 4095)) { deadFalls.add(PerilRules.key(f.x, f.z));cool(f, now);stage.failed(STALACTITE, "block-mismatch");return; }
        }
        for (int i = 0;i < n;i++) { if (!stage.set(f.x, f.column[i][0], f.z, 0)) break;f.removed++; }
        for (int i = 0;i < f.removed;i++) {
            Object h = stage.drop(f.x, f.column[i][0], f.z, f.column[i][1]);
            if (h != null) { f.handles.add(h);flying.put(h, f); }
        }
        if (f.handles.isEmpty()) {
            restore(f, false);stage.failed(STALACTITE, "drop-refused");cool(f, now + PerilRules.STALACTITE_COOLDOWN);return;
        }
        f.state = FALLING;f.deadline = now + PerilRules.fallTicks(f.column[0][0] + .5 - (PerilRules.FLOOR + 1)) + PerilRules.FALL_SLACK;
        metrics.falls++;
        stage.sound(PerilStage.Snd.BREAK, f.x + .5, f.column[0][0], f.z + .5, 1f, .6f);stage.note(STALACTITE, PerilRules.STRIKE, f.x, f.z);
    }
    /**
     * A falling block reached the floor (or was lost on its way): hurt whoever stands in its column, once per stalactite, and
     * leave the rubble of nothing -- the block never becomes a block. by is the cell it landed in.
     */
    void landed(Object handle, int bx, int by, int bz, List<PerilStage.Body> bodies) {
        Fall f = flying.remove(handle);
        if (f == null || asleep) return;
        f.handles.remove(handle);
        double cx = f.x + .5, cz = f.z + .5;
        stage.fx(PerilStage.Fx.IMPACT, cx, by + .1, cz, 6, .35, f.column[0][1]);stage.sound(PerilStage.Snd.THUD, cx, by, cz, 1f, .5f);
        if (!f.struck) {
            f.struck = true;double amount = PerilRules.damage(STALACTITE, room.tier, room.floor, danger);
            for (PerilStage.Body b : bodies) {
                if (Math.abs(b.x - cx) > PerilRules.STALACTITE_HIT || Math.abs(b.z - cz) > PerilRules.STALACTITE_HIT || b.y > by + 1.1 || b.y + 1.7 < by) continue;
                if (stage.hurt(b.id, amount, STALACTITE, f.x, f.z)) metrics.hits++;
            }
        }
        if (f.handles.isEmpty()) settle(f);
    }
    private void settle(Fall f) { f.state = GONE;f.regrowAt = now + PerilRules.REGROW_MIN + PerilRules.spread(room, f.x, f.z, 0x47524f57L, PerilRules.REGROW_SPREAD); }
    private void cool(Fall f, long until) { f.state = COOL;f.coolUntil = until; }
    /** A block that never reported its landing: given up on, nothing hurt. */
    private void lose(Fall f) { release(f);stage.failed(STALACTITE, "lost-block");settle(f); }
    private void release(Fall f) { for (Object h : f.handles) { flying.remove(h);stage.discard(h); }f.handles.clear(); }
    /** Regrows only into empty cells and never around anyone; the stalactite then rests before it can loosen again. */
    private void regrow(Fall f) {
        for (int i = 0;i < f.removed;i++) if (stage.occupants(f.x, f.column[i][0], f.z) != 0) { f.regrowAt = now + 20;return; }
        if (!restore(f, false)) { f.regrowAt = now + 20;return; }
        cool(f, now + PerilRules.STALACTITE_COOLDOWN);metrics.regrown++;
        stage.sound(PerilStage.Snd.REGROW, f.x + .5, f.column[0][0], f.z + .5, .5f, .5f);stage.note(STALACTITE, PerilRules.RESTORE, f.x, f.z);
    }
    /**
     * Puts every removed cell back from the generator; a cell that is no longer empty is left as it is. False when a chunk is
     * away. A forced restore (sleep, unload, close) never walls a player in either: a cell with a player in it stays open
     * (a hanging stalactite is no place to stand, so this is a failure path, said once).
     */
    private boolean restore(Fall f, boolean forced) {
        boolean ok = true;
        for (int i = 0;i < f.removed;i++) {
            int y = f.column[i][0], cur = stage.get(f.x, y, f.z);
            if (cur < 0) { ok = false;if (forced) stage.failed(STALACTITE, "restore-unloaded");continue; }
            if (cur != 0) continue;
            if (forced && (stage.occupants(f.x, y, f.z) & (PerilStage.ELIGIBLE | PerilStage.PLAYER)) != 0) { ok = false;stage.failed(STALACTITE, "restore-occupied");continue; }
            if (stage.set(f.x, y, f.z, blocks.block(room, f.x, y, f.z))) { if (forced) metrics.restored++; }
            else { ok = false;if (forced) stage.failed(STALACTITE, "restore-unloaded"); }
        }
        if (ok) f.removed = 0;
        return ok;
    }

    // ---------------------------------------------------------------- collapsing floors
    /** Everyone's feet overlap up to four cells: each cracked one starts to give way. */
    private void triggerCrumbles(List<PerilStage.Body> bodies) {
        if (field.count(CRUMBLE) == 0) return;
        for (PerilStage.Body b : bodies) {
            if (b.y < PerilRules.FLOOR + .2 || b.y > PerilRules.FLOOR + 1.5) continue;
            int x0 = (int) Math.floor(b.x - PerilRules.CRUMBLE_REACH), x1 = (int) Math.floor(b.x + PerilRules.CRUMBLE_REACH);
            int z0 = (int) Math.floor(b.z - PerilRules.CRUMBLE_REACH), z1 = (int) Math.floor(b.z + PerilRules.CRUMBLE_REACH);
            for (int cx = x0;cx <= x1;cx++) for (int cz = z0;cz <= z1;cz++) {
                if (!field.has(CRUMBLE, cx, cz)) continue;
                long k = PerilRules.key(cx, cz);
                if (deadCrumbles.contains(k)) continue;
                Crumble c = crumbles.get(k);
                if (c != null && !(c.state == COOL && now >= c.coolUntil)) continue;
                if (c == null && crumbles.size() >= PerilRules.MAX_CRUMBLES) continue;
                Crumble n = new Crumble(cx, cz, blocks.block(room, cx, PerilRules.FLOOR, cz), now);n.at = now + PerilRules.CRUMBLE_WARN;crumbles.put(k, n);
                stage.sound(PerilStage.Snd.CRUMBLE, cx + .5, PerilRules.FLOOR + 1, cz + .5, .6f, 1.1f);
                stage.fx(PerilStage.Fx.CRACKLE, cx + .5, PerilRules.FLOOR + 1.05, cz + .5, 3, .3, n.block);stage.note(CRUMBLE, PerilRules.WARN, cx, cz);
            }
        }
    }
    private void stepCrumbles() {
        if (crumbles.isEmpty()) return;
        for (Iterator<Crumble> it = crumbles.values().iterator();it.hasNext();) {
            Crumble c = it.next();
            switch (c.state) {
                case TELEGRAPH:
                    if ((now - c.start) % 3 == 0) stage.fx(PerilStage.Fx.CRACKLE, c.x + .5, PerilRules.FLOOR + 1.05, c.z + .5, 2, .3, c.block);
                    if (now >= c.at) breakUp(c);
                    break;
                case GONE: if (now >= c.at) mend(c);break;
                default: if (now >= c.coolUntil) it.remove();
            }
        }
    }
    private void breakUp(Crumble c) {
        long k = PerilRules.key(c.x, c.z);int want = blocks.block(room, c.x, PerilRules.FLOOR, c.z), cur = stage.get(c.x, PerilRules.FLOOR, c.z);
        if (cur < 0) { c.state = COOL;c.coolUntil = now + PerilRules.CRUMBLE_COOLDOWN;return; }
        if (want == 0 || (cur & 4095) != (want & 4095)) { deadCrumbles.add(k);c.state = COOL;c.coolUntil = now;stage.failed(CRUMBLE, want == 0 ? "no-floor" : "block-mismatch");return; }
        if (!stage.set(c.x, PerilRules.FLOOR, c.z, 0)) { c.state = COOL;c.coolUntil = now + PerilRules.CRUMBLE_COOLDOWN;return; }
        c.state = GONE;c.due = now + PerilRules.HOLE_MIN + PerilRules.spread(room, c.x, c.z, 0x484f4c45L, PerilRules.HOLE_SPREAD);c.at = c.due;metrics.crumbles++;
        stage.sound(PerilStage.Snd.BREAK, c.x + .5, PerilRules.FLOOR + 1, c.z + .5, .8f, 1f);stage.fx(PerilStage.Fx.DUST, c.x + .5, PerilRules.FLOOR + 1, c.z + .5, 8, .4, c.block);
        stage.note(CRUMBLE, PerilRules.STRIKE, c.x, c.z);
    }
    /**
     * Never while a player is inside the cell's space. A player still in the hole PerilRules.PATIENCE ticks after the mend was
     * due is lifted onto its top (and the floor returns under them at once); another kind of body gives way after MOB_PATIENCE.
     */
    private void mend(Crumble c) {
        int occ = stage.occupants(c.x, PerilRules.FLOOR, c.z);long late = now - c.due;
        if (occ != 0 && (occ & PerilStage.ELIGIBLE) != 0 && late >= PerilRules.PATIENCE && now - c.lastLift >= 20) {
            stage.lift(c.x, PerilRules.FLOOR, c.z);metrics.rescues++;c.lastLift = now;occ = stage.occupants(c.x, PerilRules.FLOOR, c.z);
        }
        if (occ != 0 && ((occ & (PerilStage.ELIGIBLE | PerilStage.PLAYER)) != 0 || late < PerilRules.MOB_PATIENCE)) { c.at = now + PerilRules.MEND_RETRY;return; }
        int cur = stage.get(c.x, PerilRules.FLOOR, c.z);
        if (cur < 0 || cur == 0 && !stage.set(c.x, PerilRules.FLOOR, c.z, blocks.block(room, c.x, PerilRules.FLOOR, c.z))) { c.at = now + PerilRules.MEND_RETRY;return; }
        c.state = COOL;c.coolUntil = now + PerilRules.CRUMBLE_COOLDOWN;if (cur == 0) metrics.mended++;
        stage.sound(PerilStage.Snd.MEND, c.x + .5, PerilRules.FLOOR + 1, c.z + .5, .5f, .7f);stage.note(CRUMBLE, PerilRules.RESTORE, c.x, c.z);
    }
    /** A forced mend (sleep, unload, close): an eligible player still in the hole is lifted onto the floor first, so the floor returns under them, never around them. */
    private void restoreCell(Crumble c) {
        int cur = stage.get(c.x, PerilRules.FLOOR, c.z);
        if (cur < 0) { stage.failed(CRUMBLE, "restore-unloaded");return; }
        if (cur != 0) return;
        if ((stage.occupants(c.x, PerilRules.FLOOR, c.z) & PerilStage.ELIGIBLE) != 0) { stage.lift(c.x, PerilRules.FLOOR, c.z);metrics.rescues++; }
        if (stage.set(c.x, PerilRules.FLOOR, c.z, blocks.block(room, c.x, PerilRules.FLOOR, c.z))) metrics.restored++;else stage.failed(CRUMBLE, "restore-unloaded");
    }

    // ---------------------------------------------------------------- geysers and lava falls
    private static boolean near(List<PerilStage.Body> bodies, double x, double z, double range) {
        for (PerilStage.Body b : bodies) { double dx = b.x - x, dz = b.z - z;if (dx * dx + dz * dz <= range * range) return true; }
        return false;
    }
    /** Wakes the vents (or lava falls) within reach of someone; each starts its first warning a moment apart. */
    private void arm(Map<Long, Spot> spots, PerilMarks.Kind kind, double radius, int max, List<PerilStage.Body> bodies) {
        if (field.count(kind) == 0 || spots.size() >= max) return;
        int reach = (int) Math.ceil(radius);double r2 = radius * radius;
        for (PerilStage.Body b : bodies) {
            int bx = (int) Math.floor(b.x), bz = (int) Math.floor(b.z);
            for (int cx = bx - reach;cx <= bx + reach;cx++) for (int cz = bz - reach;cz <= bz + reach;cz++) {
                if (spots.size() >= max) return;
                if (!field.has(kind, cx, cz)) continue;
                long k = PerilRules.key(cx, cz);
                if (spots.containsKey(k)) continue;
                double dx = b.x - (cx + .5), dz = b.z - (cz + .5);
                if (dx * dx + dz * dz > r2) continue;
                Spot s = new Spot(cx, cz);s.at = now + 6 + PerilRules.spread(room, cx, cz, 0x57414b45L, 40);spots.put(k, s);
            }
        }
    }
    private void stepSpots(Map<Long, Spot> spots, PerilMarks.Kind kind, List<PerilStage.Body> bodies) {
        if (spots.isEmpty()) return;
        boolean vent = kind == GEYSER;double range = vent ? PerilRules.GEYSER_RANGE : PerilRules.SPOUT_RANGE;
        for (Iterator<Spot> it = spots.values().iterator();it.hasNext();) {
            Spot s = it.next();double cx = s.x + .5, cz = s.z + .5;
            switch (s.state) {
                case WAIT:
                    if (!near(bodies, cx, cz, range)) { it.remove();break; }
                    if (now >= s.at) {
                        s.state = WARN;s.start = now;s.at = now + (vent ? PerilRules.GEYSER_TELEGRAPH : PerilRules.SPOUT_TELEGRAPH);
                        stage.sound(vent ? PerilStage.Snd.HISS : PerilStage.Snd.POP, cx, PerilRules.FLOOR + 1, cz, .8f, .7f);stage.note(kind, PerilRules.WARN, s.x, s.z);
                    }
                    break;
                case WARN:
                    warning(s, vent);
                    if (now >= s.at) { if (vent) erupt(s, bodies);else splash(s, bodies); }
                    break;
                case BURST:
                    burst(s, vent);
                    if (now >= s.at) {
                        s.state = REST;s.at = now + (vent ? PerilRules.GEYSER_REST_MIN + PerilRules.spread(room, s.x, s.z, 0x52455354L, PerilRules.GEYSER_REST_SPREAD)
                            : PerilRules.SPOUT_REST_MIN + PerilRules.spread(room, s.x, s.z, 0x52455354L, PerilRules.SPOUT_REST_SPREAD));
                    }
                    break;
                default:
                    if (now >= s.at) { if (near(bodies, cx, cz, range)) { s.state = WAIT;s.at = now + 4; } else it.remove(); }
            }
        }
    }
    /** Bubbling and smoke on a vent, drips and pops on a lava fall: growing for about a second. */
    private void warning(Spot s, boolean vent) {
        long age = now - s.start;double cx = s.x + .5, cz = s.z + .5;
        if (age % 3 != 0) return;
        if (vent) {
            boolean flame = PerilRules.flame(room);
            stage.fx(flame ? PerilStage.Fx.SMOKE : PerilStage.Fx.STEAM, cx, PerilRules.FLOOR + 1.1, cz, 2 + (int) (age / 6), .25, 0);
            if (flame) stage.fx(PerilStage.Fx.LAVA_POP, cx, PerilRules.FLOOR + 1.1, cz, 1, .2, 0);
            if (age == 12) stage.sound(PerilStage.Snd.HISS, cx, PerilRules.FLOOR + 1, cz, .8f, 1.2f);
        } else {
            stage.fx(PerilStage.Fx.LAVA_DRIP, cx, PerilRules.FLOOR + 3 + random.nextInt(4), cz, 2, .3, 0);stage.fx(PerilStage.Fx.LAVA_POP, cx, PerilRules.FLOOR + 1.2, cz, 1, .6, 0);
            if (age == 12) stage.sound(PerilStage.Snd.POP, cx, PerilRules.FLOOR + 1, cz, .8f, .8f);
        }
    }
    /** One burst at the end of the warning: whoever stands on the vent takes the scald and is thrown up (Floor III: set alight instead). */
    private void erupt(Spot s, List<PerilStage.Body> bodies) {
        s.state = BURST;s.start = now;s.at = now + PerilRules.GEYSER_BURST;metrics.eruptions++;
        double cx = s.x + .5, cz = s.z + .5;boolean flame = PerilRules.flame(room);
        stage.sound(flame ? PerilStage.Snd.FLARE : PerilStage.Snd.ERUPT, cx, PerilRules.FLOOR + 1, cz, 1f, flame ? .8f : .6f);
        stage.fx(flame ? PerilStage.Fx.FLAME : PerilStage.Fx.STEAM, cx, PerilRules.FLOOR + 1.3, cz, 12, .35, 0);
        double amount = PerilRules.damage(GEYSER, room.tier, room.floor, danger);
        for (PerilStage.Body b : bodies) {
            double dx = b.x - cx, dz = b.z - cz;
            if (Math.abs(dx) > PerilRules.GEYSER_HIT || Math.abs(dz) > PerilRules.GEYSER_HIT || b.y < PerilRules.FLOOR + .9 || b.y > PerilRules.FLOOR + 3) continue;
            if (stage.hurt(b.id, amount, GEYSER, s.x, s.z)) metrics.hits++;
            if (flame) stage.burn(b.id, PerilRules.burn(GEYSER, room.floor));else stage.toss(b.id, dx * .15, PerilRules.GEYSER_LIFT, dz * .15);
        }
        stage.note(GEYSER, PerilRules.STRIKE, s.x, s.z);
    }
    /** One splash at the end of the warning: whoever stands within a block and a half of the fall catches fire. */
    private void splash(Spot s, List<PerilStage.Body> bodies) {
        s.state = BURST;s.start = now;s.at = now + PerilRules.SPOUT_BURST;metrics.splashes++;
        double cx = s.x + .5, cz = s.z + .5, r2 = PerilRules.SPOUT_RADIUS * PerilRules.SPOUT_RADIUS;
        stage.sound(PerilStage.Snd.FLARE, cx, PerilRules.FLOOR + 1, cz, .9f, .9f);
        stage.fx(PerilStage.Fx.FLAME, cx, PerilRules.FLOOR + 1.2, cz, 10, .7, 0);stage.fx(PerilStage.Fx.LAVA_POP, cx, PerilRules.FLOOR + 1.2, cz, 6, .9, 0);
        double amount = PerilRules.damage(LAVA_FALL, room.tier, room.floor, danger);
        for (PerilStage.Body b : bodies) {
            double dx = b.x - cx, dz = b.z - cz;
            if (dx * dx + dz * dz > r2 || b.y < PerilRules.FLOOR + .5 || b.y > room.roof()) continue;
            if (stage.hurt(b.id, amount, LAVA_FALL, s.x, s.z)) metrics.hits++;
            stage.burn(b.id, PerilRules.burn(LAVA_FALL, room.floor));
        }
        stage.note(LAVA_FALL, PerilRules.STRIKE, s.x, s.z);
    }
    private void burst(Spot s, boolean vent) {
        long age = now - s.start;
        if (age % 2 != 0) return;
        double cx = s.x + .5, cz = s.z + .5;
        if (vent) stage.fx(PerilRules.flame(room) ? PerilStage.Fx.FLAME : PerilStage.Fx.STEAM, cx, PerilRules.FLOOR + 1.5 + age * .25, cz, 6, .3, 0);
        else stage.fx(PerilStage.Fx.LAVA_POP, cx, PerilRules.FLOOR + 1.2, cz, 3, .8, 0);
    }

    // ---------------------------------------------------------------- ambience
    /** A pop of lava, a drip from a lava fall, a wisp from an idle vent: a few tries around each player, one of each at most. */
    private void ambient(List<PerilStage.Body> bodies) {
        int radius = PerilRules.AMBIENT_RADIUS;
        for (PerilStage.Body b : bodies) {
            boolean lava = false, drip = false, wisp = false;int bx = (int) Math.floor(b.x), bz = (int) Math.floor(b.z);
            for (int n = 0;n < PerilRules.AMBIENT_TRIES;n++) {
                int cx = bx + random.nextInt(2 * radius + 1) - radius, cz = bz + random.nextInt(2 * radius + 1) - radius;
                if (!lava && field.has(LAVA, cx, cz)) {
                    lava = true;stage.fx(PerilStage.Fx.LAVA_POP, cx + .15 + random.nextDouble() * .7, PerilRules.FLOOR + 1, cz + .15 + random.nextDouble() * .7, 1, .1, 0);
                } else if (!drip && field.has(LAVA_FALL, cx, cz)) {
                    drip = true;stage.fx(PerilStage.Fx.LAVA_DRIP, cx + .5, PerilRules.FLOOR + 2 + random.nextInt(4), cz + .5, 1, .2, 0);
                } else if (!wisp && field.has(GEYSER, cx, cz) && !vents.containsKey(PerilRules.key(cx, cz))) {
                    wisp = true;stage.fx(PerilStage.Fx.SMOKE, cx + .5, PerilRules.FLOOR + 1.1, cz + .5, 1, .15, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- putting the world back
    /** The room's last act: every block it changed goes back from the generator, every falling block ends, every pending effect is dropped. */
    void sleep() {
        if (asleep) return;
        asleep = true;
        for (Fall f : falls.values()) { release(f);restore(f, true); }
        for (Crumble c : crumbles.values()) if (c.state == GONE) restoreCell(c);
        falls.clear();flying.clear();crumbles.clear();vents.clear();spouts.clear();
    }
    /** A chunk is about to unload: whatever this room changed in it goes back first, so a saved chunk never holds a hole. */
    void restoreChunk(int cx, int cz) {
        if (asleep) return;
        for (Fall f : falls.values()) {
            if ((f.x >> 4) != cx || (f.z >> 4) != cz || f.removed == 0) continue;
            release(f);restore(f, true);cool(f, now + PerilRules.STALACTITE_COOLDOWN);
        }
        for (Crumble c : crumbles.values()) {
            if (c.state != GONE || (c.x >> 4) != cx || (c.z >> 4) != cz) continue;
            restoreCell(c);c.state = COOL;c.coolUntil = now + PerilRules.CRUMBLE_COOLDOWN;
        }
    }
}
