package jaspr.audit.harness;

import chat.jaspr.biomes.CaptureHook;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CaptureHook sink. For every chunk that belongs to a capture job it keeps, per block, the LAST
 * structure write (block state + which builder wrote it). Chunks outside the job are ignored, and a
 * chunk's record is dropped when the chunk unloads (the harness never saves chunks, so a chunk that
 * loads again is generated again and records again from scratch).
 *
 * Builder attribution: writes arriving through Dungeons.set (source "populate") are attributed to the
 * builder method on the call stack, found by walking outward to the frame just inside the populate()
 * / Dungeons.build() that called it, e.g. "Megaliths.sewer", "Dungeons.ward", "Dungeons.plain",
 * "Landmarks.precinct". Every other source is its own builder ("catalog", "sanctuary", "detail", "fold").
 */
final class Recorder implements CaptureHook.Sink {
    static final class Rec {
        /** (id << 4 | data) + 1 of the last structure write, 0 = none. index = (y << 8) | (z << 4) | x */
        final char[] state = new char[65536];
        /** builder name id of the last write */
        final short[] who = new short[65536];
        int writes;
    }

    private static final StackWalker WALKER = StackWalker.getInstance();
    private final Map<String, Set<Long>> targets = new HashMap<>();
    private final Map<String, Map<Long, Rec>> recs = new HashMap<>();
    private final List<String> names = new ArrayList<>();
    private final Map<String, Short> nameIds = new HashMap<>();
    long totalWrites, recordedWrites, walkNanos, walks;

    Recorder() { nameIds.put("?", (short) 0); names.add("?"); }

    static long key(int cx, int cz) { return (((long) cx) << 32) | (cz & 0xFFFFFFFFL); }

    synchronized void target(String world, int cx0, int cz0, int cx1, int cz1) {
        Set<Long> set = targets.computeIfAbsent(world, w -> new HashSet<>());
        for (int x = cx0; x <= cx1; x++) for (int z = cz0; z <= cz1; z++) set.add(key(x, z));
    }

    synchronized int targetChunks() { int n = 0; for (Set<Long> s : targets.values()) n += s.size(); return n; }

    @Override
    public void write(String world, int x, int y, int z, int id, int data, String source) {
        if (world == null) world = "world";
        if (y < 0 || y > 255) return;
        long key = key(x >> 4, z >> 4);
        synchronized (this) {
            totalWrites++;
            Set<Long> t = targets.get(world);
            if (t == null || !t.contains(key)) return;
        }
        String who;
        if ("populate".equals(source)) {
            long t0 = System.nanoTime();
            who = builder();
            long dt = System.nanoTime() - t0;
            synchronized (this) { walkNanos += dt; walks++; }
        } else who = source;
        synchronized (this) {
            Rec r = recs.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(key, k -> new Rec());
            int i = (y << 8) | ((z & 15) << 4) | (x & 15);
            r.state[i] = (char) ((((id & 0xFFF) << 4) | (data & 15)) + 1);
            r.who[i] = nameId(who);
            r.writes++;
            recordedWrites++;
        }
    }

    private short nameId(String who) {
        Short s = nameIds.get(who);
        if (s != null) return s;
        short n = (short) names.size();
        names.add(who);
        nameIds.put(who, n);
        return n;
    }

    synchronized String name(int id) { return id >= 0 && id < names.size() ? names.get(id) : "?"; }

    synchronized Rec rec(String world, int cx, int cz) {
        Map<Long, Rec> m = recs.get(world);
        return m == null ? null : m.get(key(cx, cz));
    }

    synchronized void drop(String world, int cx, int cz) {
        Map<Long, Rec> m = recs.get(world);
        if (m != null) m.remove(key(cx, cz));
    }

    synchronized int liveRecords() { int n = 0; for (Map<Long, Rec> m : recs.values()) n += m.size(); return n; }

    /** The builder method just inside the populate()/Dungeons.build() frame that led to this write. */
    static String builder() {
        return WALKER.walk(s -> {
            String prev = null;
            for (Iterator<StackWalker.StackFrame> it = s.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                String c = f.getClassName();
                if (!c.startsWith("chat.jaspr.biomes.")) continue;
                String simple = c.substring("chat.jaspr.biomes.".length());
                int d = simple.indexOf('$');
                if (d >= 0) simple = simple.substring(0, d);
                if (simple.equals("CaptureHook")) continue;
                String m = f.getMethodName();
                if (m.equals("populate") || (simple.equals("Dungeons") && m.equals("build")))
                    return prev == null ? simple + "." + m : prev;
                if (m.startsWith("lambda$")) {
                    m = m.substring(7);
                    int e = m.indexOf('$');
                    if (e > 0) m = m.substring(0, e);
                }
                prev = simple + "." + m;
            }
            return prev == null ? "populate?" : prev + "?";
        });
    }
}
