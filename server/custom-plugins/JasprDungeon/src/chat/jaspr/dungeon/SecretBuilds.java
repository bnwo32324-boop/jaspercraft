package chat.jaspr.dungeon;

import java.util.*;

/**
 * Generation 7: what the secrets build, as pure data (no Bukkit, no world reads). Every build is a block plan inside one bay: a
 * 7 x 7 footprint (dx and dz run -3..3 around the bay's centre cell) rising from the floor layer (dy 0 is y 65, the layer above
 * the floor) no higher than dy {@value #MAX_DY}, so the lowest roof (y 73) always clears it. Blocks are packed legacy ids
 * (id | data << 12), never an ore or a metal or gem block. The canonical build faces south (+z, where its sign stands); a bay in
 * the south half of its tile is built turned half way so the front always faces the lanes.
 *
 * Offsets for creatures and particles are measured from the centre of the bay's centre cell, x and z in cells, y above the floor
 * layer (a creature standing on the floor has y 0).
 */
public final class SecretBuilds {
    private SecretBuilds() {}
    public static final int HALF = 3, MAX_DY = 6;

    /** One block of a build; text is the four lines of a sign, else null. */
    public static final class Piece {
        public final int dx, dy, dz, block;
        public final String[] text;
        Piece(int dx, int dy, int dz, int block, String[] text) { this.dx = dx; this.dy = dy; this.dz = dz; this.block = block; this.text = text; }
        public int id() { return block & 4095; }
        public int data() { return block >>> 12; }
        public boolean sign() { return id() == 63 || id() == 68; }
        /** Placed after everything it hangs from or stands on. */
        public boolean attached() { return sign() || id() == 50; }
    }

    public static final class Build {
        public final SecretCatalog.Kind kind;
        public final List<Piece> pieces;
        /** Quarter turns (clockwise from above) applied to the canonical build: 0 or 2 in play. */
        public final int turn;
        /** The cell people use (the hatch, the cake, the jukebox, the stone column) or null: {dx, dy, dz}. */
        public final int[] focus;
        /** Where a creature stands, or an armour stand is posed ({x, y, z} offsets); null when there is none. */
        public final double[] spawn, stand;
        /** Particle points ({x, y, z} offsets). */
        public final double[][] emit;
        Build(SecretCatalog.Kind kind, List<Piece> pieces, int turn, int[] focus, double[] spawn, double[] stand, double[][] emit) {
            this.kind = kind; this.pieces = Collections.unmodifiableList(pieces); this.turn = turn; this.focus = focus; this.spawn = spawn; this.stand = stand; this.emit = emit;
        }
        /** A creature facing the front of the build looks this way (Minecraft yaw: 0 south, 90 west, 180 north, 270 east). */
        public float yaw() { return 90f * Math.floorMod(turn, 4); }
        public int maxDy() { int m = 0; for (Piece p : pieces) m = Math.max(m, p.dy); return m; }
        public Piece at(int dx, int dy, int dz) { for (Piece p : pieces) if (p.dx == dx && p.dy == dy && p.dz == dz) return p; return null; }
        /** The same build turned this many more quarter turns clockwise (blocks that face somewhere face somewhere else). */
        public Build turned(int quarter) {
            int q = Math.floorMod(quarter, 4);
            if (q == 0) return this;
            List<Piece> out = new ArrayList<Piece>(pieces.size());
            for (Piece p : pieces) {
                int[] c = rot(p.dx, p.dz, q);
                out.add(new Piece(c[0], p.dy, c[1], p.id() | (turnData(p.id(), p.data(), q) << 12), p.text));
            }
            int[] f = focus == null ? null : rot(focus[0], focus[2], q);
            double[][] e = new double[emit.length][];
            for (int i = 0; i < e.length; i++) e[i] = rotD(emit[i], q);
            return new Build(kind, out, Math.floorMod(turn + q, 4), f == null ? null : new int[]{f[0], focus[1], f[1]}, rotD(spawn, q), rotD(stand, q), e);
        }
    }

    /** One quarter turn clockwise seen from above (east to south): (x, z) -> (-z, x), q times. */
    static int[] rot(int x, int z, int q) {
        for (int i = 0; i < q; i++) { int t = x; x = -z; z = t; }
        return new int[]{x, z};
    }
    private static double[] rotD(double[] v, int q) {
        if (v == null) return null;
        double x = v[0], z = v[2];
        for (int i = 0; i < q; i++) { double t = x; x = -z; z = t; }
        return new double[]{x, v[1], z};
    }
    /** Block data after q clockwise quarter turns: signs, stairs and trapdoors face somewhere; everything else does not care. */
    static int turnData(int id, int data, int q) {
        switch (id) {
            case 63: return (data + 4 * q) & 15;                       // standing sign: 0 south, 4 west, 8 north, 12 east
            case 68: { int d = data; for (int i = 0; i < q; i++) d = d == 2 ? 5 : d == 5 ? 3 : d == 3 ? 4 : 2; return d; }   // wall sign: north east south west
            case 53: case 109: { int d = data & 3; for (int i = 0; i < q; i++) d = d == 0 ? 2 : d == 2 ? 1 : d == 1 ? 3 : 0; return (data & 4) | d; }   // stairs rise toward east, south, west, north
            case 96: { int d = data & 3; for (int i = 0; i < q; i++) d = d == 0 ? 3 : d == 3 ? 1 : d == 1 ? 2 : 0; return (data & 12) | d; }   // trapdoor: north, south, west, east
            default: return data;
        }
    }

    // ---------------------------------------------------------------- the builder
    private static int d(int id, int data) { return id | (data << 12); }
    private static int wool(int color) { return d(35, color); }
    private static final int TORCH = d(50, 5), HAY = 170, CAULDRON = 118, FENCE = 85, STONE = 1, BRICK = 98, SLAB = d(44, 5), COBWEB = 30;

    private static final class B {
        private final LinkedHashMap<Integer, Piece> cells = new LinkedHashMap<Integer, Piece>();
        private int[] focus; private double[] spawn, stand; private final List<double[]> emit = new ArrayList<double[]>();
        private static int key(int x, int y, int z) { return ((x + 8) * 32 + (y + 8)) * 32 + (z + 8); }
        B put(int x, int y, int z, int block) { return put(x, y, z, block, null); }
        private B put(int x, int y, int z, int block, String[] text) {
            if (Math.abs(x) > HALF || Math.abs(z) > HALF || y < 0 || y > MAX_DY) throw new IllegalArgumentException("outside the bay: " + x + "," + y + "," + z);
            cells.remove(key(x, y, z)); cells.put(key(x, y, z), new Piece(x, y, z, block, text)); return this;
        }
        B box(int x0, int x1, int y0, int y1, int z0, int z1, int block) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) put(x, y, z, block);
            return this;
        }
        /** A standing sign (rotation 0 faces south) with up to four lines of at most fifteen characters. */
        B sign(int x, int y, int z, int rotation, String... lines) { return put(x, y, z, d(63, rotation), lines); }
        B focus(int x, int y, int z) { focus = new int[]{x, y, z}; return this; }
        B spawn(double x, double y, double z) { spawn = new double[]{x, y, z}; return this; }
        B stand(double x, double y, double z) { stand = new double[]{x, y, z}; return this; }
        B emit(double x, double y, double z) { emit.add(new double[]{x, y, z}); return this; }
        Build done(SecretCatalog.Kind kind) {
            List<Piece> list = new ArrayList<Piece>(cells.values());
            // Supports first (low to high), then what hangs from them: torches and signs.
            Collections.sort(list, new Comparator<Piece>() {
                public int compare(Piece a, Piece b) { return a.attached() != b.attached() ? (a.attached() ? 1 : -1) : Integer.compare(a.dy, b.dy); }
            });
            return new Build(kind, list, 0, focus, spawn, stand, emit.toArray(new double[0][]));
        }
    }

    private static final Map<SecretCatalog.Kind, Build> BUILDS = new EnumMap<SecretCatalog.Kind, Build>(SecretCatalog.Kind.class);
    static {
        BUILDS.put(SecretCatalog.Kind.DUCK, duck());
        BUILDS.put(SecretCatalog.Kind.BASEMENT, basement());
        BUILDS.put(SecretCatalog.Kind.CAKE, cake());
        BUILDS.put(SecretCatalog.Kind.CRYPT, crypt());
        BUILDS.put(SecretCatalog.Kind.DESK, desk());
        BUILDS.put(SecretCatalog.Kind.JEB, jeb());
        BUILDS.put(SecretCatalog.Kind.DINNERBONE, dinnerbone());
        BUILDS.put(SecretCatalog.Kind.SWORD, sword());
        BUILDS.put(SecretCatalog.Kind.PEDDLER, camp());
    }
    /** The canonical build of an Easter egg kind (or the peddler's camp); null for the secrets that build nothing. */
    public static Build of(SecretCatalog.Kind kind) { return BUILDS.get(kind); }
    public static Collection<Build> all() { return Collections.unmodifiableCollection(BUILDS.values()); }

    /** "Every bug fears the duck": a big rubber duck of yellow and orange wool, black eyes, an orange bill. */
    private static Build duck() {
        int y = wool(4), o = wool(1), k = wool(15);
        return new B().box(-2, 2, 0, 0, -2, 0, y).box(-1, 1, 0, 0, -3, -3, y).box(-2, 2, 1, 1, -3, 0, y).box(-1, 1, 2, 2, -3, -1, y).put(0, 3, -3, y)
            .box(-1, 1, 2, 2, 0, 0, y).box(-1, 1, 3, 4, 0, 2, y).box(-1, 1, 3, 3, 3, 3, o).put(-1, 4, 2, k).put(1, 4, 2, k)
            .sign(0, 0, 3, 0, "", "Every bug", "fears the duck", "").focus(0, 2, 0).emit(0, 5.3, 1).done(SecretCatalog.Kind.DUCK);
    }
    /** Isaac's basement: a crying statue (tears of light blue glass run down its face) and a hatch; stepping on the hatch lets Monstro out. */
    private static Build basement() {
        int cracked = d(98, 2), chisel = d(98, 3), tear = d(95, 3), eye = d(159, 15);
        return new B().box(-1, 1, 0, 0, -3, -1, BRICK).box(-1, 1, 1, 2, -2, -1, cracked).box(-1, 1, 3, 4, -2, -1, chisel)
            .box(-1, -1, 1, 3, -1, -1, tear).box(1, 1, 1, 3, -1, -1, tear).put(-1, 4, -1, eye).put(1, 4, -1, eye)
            .box(-2, -2, 2, 3, -1, -1, BRICK).box(2, 2, 2, 3, -1, -1, BRICK).put(-1, 1, -3, TORCH).put(1, 1, -3, TORCH)
            .box(-1, 1, 0, 0, 0, 2, SLAB).put(0, 0, 1, d(96, 0))
            .sign(-2, 0, 2, 0, "The Basement", "Tears fall here", "", "Mind the hatch.")
            .focus(0, 0, 1).emit(-1, 4.2, -.45).emit(1, 4.2, -.45).done(SecretCatalog.Kind.BASEMENT);
    }
    /** The cake chamber: a cake on a pedestal, torches, and two signs that know better. */
    private static Build cake() {
        return new B().box(-1, 1, 0, 0, -1, 1, d(98, 3)).put(0, 1, 0, d(92, 0))
            .put(-1, 1, -1, TORCH).put(1, 1, -1, TORCH).put(-1, 1, 1, TORCH).put(1, 1, 1, TORCH)
            .sign(-2, 0, 2, 0, "", "The cake", "is a lie.", "").sign(2, 0, 2, 0, "Do not", "trust", "the cake.", "")
            .focus(0, 1, 0).emit(0, 2.1, 0).done(SecretCatalog.Kind.CAKE);
    }
    /** The disc 13 crypt: a jukebox on a mossy plinth under a canopy on four pillars, cobwebs and torchlight. */
    private static Build crypt() {
        B b = new B().box(-1, 1, 0, 0, -1, 1, d(98, 1)).put(0, 1, 0, d(84, 1));
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) b.box(sx, sx, 0, 3, sz, sz, BRICK);
        b.box(-2, 2, 4, 4, -2, 2, SLAB);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) b.put(sx, 3, sz, COBWEB).put(sx, 1, sz, TORCH);
        return b.sign(0, 0, 3, 0, "Record 13", "plays here.", "Please do not", "skip the track.").focus(0, 1, 0).emit(0, 2.6, 0).done(SecretCatalog.Kind.CRYPT);
    }
    /** The developers' desk: an older machine and a newer one, a chair, a rack of books and two friendly signs. */
    private static Build desk() {
        return new B().box(-1, 1, 0, 0, 0, 0, d(5, 0)).put(-1, 1, 0, d(159, 8)).put(-1, 2, 0, d(159, 15)).put(1, 1, 0, d(159, 0)).put(1, 2, 0, d(95, 3))
            .put(0, 1, 0, d(171, 15)).put(0, 0, 1, d(53, 2)).box(-1, 1, 0, 2, -3, -3, 47).put(0, 3, -3, TORCH).put(2, 0, 0, CAULDRON)
            .sign(-2, 0, 2, 0, "The developers'", "desk. Dug by an", "older machine,", "").sign(2, 0, 2, 0, "renovated by a", "newer one.", "Thanks for", "playing!")
            .focus(0, 0, 0).done(SecretCatalog.Kind.DESK);
    }
    /** A sheep named jeb_ in a little fenced pen on green carpet. */
    private static Build jeb() {
        B b = new B();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.max(Math.abs(x), Math.abs(z)) == 2) b.put(x, 0, z, FENCE);
        return b.box(-1, 1, 0, 0, -1, 1, d(171, 5)).put(-3, 0, -3, HAY).put(3, 0, -3, HAY).put(-3, 0, 3, HAY).put(3, 0, 3, HAY)
            .sign(0, 0, 3, 0, "", "jeb_", "is a sheep", "of many colors").spawn(0, .07, 0).focus(0, 0, 0).done(SecretCatalog.Kind.JEB);
    }
    /** Dinnerbone's guard: a gate of two pillars and a lintel (four cells clear, a scaled zombie fits) over a red carpet, torches and a polite request. */
    private static Build dinnerbone() {
        return new B().box(-2, -2, 0, 3, 0, 0, BRICK).box(2, 2, 0, 3, 0, 0, BRICK).box(-2, 2, 4, 4, 0, 0, d(98, 3)).box(0, 0, 0, 0, -1, 2, d(171, 14))
            .put(-2, 5, 0, TORCH).put(2, 5, 0, TORCH)
            .sign(0, 0, 3, 0, "Guarded by", "Dinnerbone.", "Please do not", "turn him over.").spawn(0, .07, 0).focus(0, 0, 0).done(SecretCatalog.Kind.DINNERBONE);
    }
    /**
     * The sword in the stone: a stone plinth with a column; an invisible armour stand whose right arm points back and down holds
     * the sword blade-down at the column's top (the stand's feet are offset so the hand lands there). Facing south.
     */
    private static Build sword() {
        B b = new B().box(-1, 1, 0, 0, -1, 1, STONE).box(0, 0, 1, 2, 0, 0, STONE);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) b.put(sx, 0, sz, d(48, 0));
        return b.sign(0, 0, 2, 0, "Only the worthy", "may draw this", "blade, and only", "in peace.").focus(0, 2, 0).stand(.375, 2.02, .625).emit(0, 3.3, 0).done(SecretCatalog.Kind.SWORD);
    }
    /** The lost peddler's camp: a rug, hay bales with a torch, a cauldron, a pack and a sign. */
    private static Build camp() {
        return new B().box(-1, 1, 0, 0, -1, 1, d(171, 12)).put(-2, 0, -1, HAY).put(-2, 1, -1, HAY).put(-1, 0, -2, HAY).put(-2, 2, -1, TORCH)
            .put(2, 0, -1, CAULDRON).put(1, 0, -2, wool(12)).put(1, 1, -2, wool(0))
            .sign(0, 0, 3, 0, "Lost Peddler", "Honest wares,", "odd prices.", "").spawn(0, .07, 0).focus(0, 0, 0).done(SecretCatalog.Kind.PEDDLER);
    }
}
