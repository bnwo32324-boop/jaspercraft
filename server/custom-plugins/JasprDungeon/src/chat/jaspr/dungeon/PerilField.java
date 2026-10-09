package chat.jaspr.dungeon;

/**
 * Generation 7: which cells of one room carry which physical perils, read once from the floor's own predicate (PerilMarks) a
 * few hundred cells a tick, so a great hall never costs one tick more than a small room. Only cells a peril may ever
 * target are kept (PerilRules.allowed): whatever a generator draws on a lane core, in the arrival circle or outside the
 * interior is ignored here, so no schedule can ever reach it. Pure: the source is a parameter, which lets the audit mark
 * cells the way no generator does.
 */
final class PerilField {
    /** Where the perils are: PerilMarks.at in the game. */
    interface Source { boolean at(PerilMarks.Kind kind, Layout.Room room, int x, int z); }
    static final PerilMarks.Kind[] KINDS = PerilMarks.Kind.values();
    final Layout.Room room;
    private final Source source;private final byte[] mask;private final int[] counts = new int[KINDS.length];private int cursor;

    PerilField(Layout.Room room, Source source) {
        if (KINDS.length > 8) throw new IllegalStateException("PerilField keeps one bit per kind: at most eight kinds");
        this.room = room;this.source = source;mask = new byte[room.w * room.d];
    }
    boolean ready() { return cursor >= mask.length; }
    /** Reads the next 'budget' cells. */
    void build(int budget) {
        int end = (int) Math.min((long) mask.length, (long) cursor + Math.max(1, budget));
        for (; cursor < end; cursor++) {
            int x = room.x + cursor % room.w, z = room.z + cursor / room.w;
            if (!PerilRules.allowed(room, x, z)) continue;
            int bits = 0;
            for (PerilMarks.Kind k : KINDS) if (source.at(k, room, x, z)) { bits |= 1 << k.ordinal();counts[k.ordinal()]++; }
            mask[cursor] = (byte) bits;
        }
    }
    /** Whether this peril is at the cell (false for any cell not yet read, outside the room, or never targetable). */
    boolean has(PerilMarks.Kind k, int x, int z) {
        int rx = x - room.x, rz = z - room.z;
        return rx >= 0 && rz >= 0 && rx < room.w && rz < room.d && (mask[rz * room.w + rx] & (1 << k.ordinal())) != 0;
    }
    int count(PerilMarks.Kind k) { return counts[k.ordinal()]; }
    int total() { int n = 0;for (int c : counts) n += c;return n; }
}
