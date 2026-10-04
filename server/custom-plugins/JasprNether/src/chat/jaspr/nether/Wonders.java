package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * Small discoveries between the mega structures (owner, 2026-09-28: make the Nether "more interesting and fun to
 * explore"), at most one per chunk, each fitted inside its chunk's box:
 * <ul>
 *   <li>everywhere: a lost expedition's camp, whose journal gives rumours of the nearest mega structures;</li>
 *   <li>Hell: amethyst geodes in the rock, hanging cages, ghast fossils;</li>
 *   <li>Ruthless Sands: soul graveyards with a buried chest, ghast fossils, cages;</li>
 *   <li>Torrid Wasteland: basalt column groves;</li>
 *   <li>Fungi Forest: fairy rings of mushrooms;</li>
 *   <li>Arctic Abyss: frozen obelisks with blue-fire braziers and an offering chest.</li>
 * </ul>
 */
final class Wonders {
    private final Gen g;
    Wonders(Gen g) { this.g = g; }

    static String display(String id) {
        switch (id) {
            case "lost_camp": return "Lost Expedition Camp";
            case "hanging_cage": return "Hanging Cage";
            case "amethyst_geode": return "Amethyst Geode";
            case "ghast_fossil": return "Ghast Fossil";
            case "soul_graveyard": return "Soul Graveyard";
            case "basalt_grove": return "Basalt Grove";
            case "fairy_ring": return "Fairy Ring";
            case "frozen_obelisk": return "Frozen Obelisk";
            default: return NetherPlugin.pretty(id);
        }
    }

    private static final int NB = b(112), NB_FENCE = b(113), NB_SLAB = b(44, 6), NB_SLAB_TOP = b(44, 14), GLOW = b(89), RACK = b(87), FIRE = b(51);
    private static final int BONE_Y = b(216, 0), BONE_X = b(216, 4), BONE_Z = b(216, 8), BASALT = b(159, 15), PURPUR = b(201), MAGMA = b(213), LAVA = b(11);
    private static final int ICE = b(174), QUARTZ = b(155), QZ_CHISELED = b(155, 1), LANTERN = b(169), SOUL = b(88), COBBLE = b(4), LOG = b(17, 5);
    private static final int WOOL_W = b(35, 0), WOOL_B = b(35, 12), CARPET_R = b(171, 14), SHROOM_R = b(40), SHROOM_B = b(39), MYCEL = b(110);

    void populate(Area a, Random r, Biomes.Nex region, Gen.Post post) {
        int bx = a.ox + 8, bz = a.oz + 8;
        Template.Placed tiles = new Template.Placed();
        Draw d = new Draw(a, bx, bz, bx + 15, bz + 15, tiles);
        double q = r.nextDouble();
        String made = null;
        // Every chance 3x (owner 2026-10-04: "structures in the Nether 3x as common"); each region's total stays below 1.
        if (q < 3 / 40.0) made = camp(d, r);
        else {
            q = r.nextDouble();
            switch (region) {
                case HELL:
                    if (q < 3 / 20.0) made = geode(d, r);
                    else if (q < 3 / 20.0 + 3 / 25.0) made = cage(d, r);
                    else if (q < 3 / 20.0 + 3 / 25.0 + 3 / 40.0) made = fossil(d, r);
                    break;
                case RUTHLESS_SANDS:
                    if (q < 3 / 20.0) made = graveyard(d, r);
                    else if (q < 3 / 20.0 + 3 / 16.0) made = fossil(d, r);
                    else if (q < 3 / 20.0 + 3 / 16.0 + 3 / 40.0) made = cage(d, r);
                    break;
                case TORRID_WASTELAND:
                    if (q < 3 / 6.0) made = columns(d, r);
                    else if (q < 3 / 6.0 + 3 / 45.0) made = fossil(d, r);
                    break;
                case FUNGI_FOREST:
                    if (q < 3 / 12.0) made = ring(d, r);
                    break;
                default:
                    if (q < 3 / 10.0) made = obelisk(d, r);
            }
        }
        if (made == null) return;
        post.add(tiles, made);
        g.count("wonder_" + made);
    }

    // ---- finding room ----------------------------------------------------------------------------------------------
    /** A floor near the box centre: solid ground with {@code clear} air above, scanning down; y of the ground or -1. */
    private static int floor(Draw d, int x, int z, int clear) {
        for (int y = 110; y > 33; y--) {
            if (!Blocks.isFullSolid(d.id(x, y, z))) continue;
            boolean ok = true;
            for (int k = 1; k <= clear && ok; k++) if (!d.air(x, y + k, z)) ok = false;
            if (ok) return y;
        }
        return -1;
    }

    /** Whether every column of a square has its ground within one block of y and air above it. */
    private static boolean flat(Draw d, int cx, int cz, int half, int y, int clear) {
        for (int x = cx - half; x <= cx + half; x++) for (int z = cz - half; z <= cz + half; z++) {
            int top = -1;
            for (int yy = y + 1; yy >= y - 1; yy--) if (Blocks.isFullSolid(d.id(x, yy, z))) { top = yy; break; }
            if (top < 0) return false;
            for (int k = 1; k <= clear; k++) if (!d.air(x, top + k, z) && !Blocks.replaceable(d.id(x, top + k, z))) return false;
        }
        return true;
    }

    private void register(Draw d, String id, int x0, int y0, int z0, int x1, int y1, int z1) {
        g.registry.add("wonder", id, x0, y0, z0, x1, y1, z1);
    }

    // ---- the wonders -----------------------------------------------------------------------------------------------
    /** A lost expedition's camp: an A-frame tent with a bedroll and the journal chest, a campfire, a grim flag. */
    private String camp(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8;
        int y = floor(d, cx, cz, 5);
        if (y < 0 || !flat(d, cx, cz, 4, y, 4)) return null;
        boolean alongX = r.nextBoolean();
        Draw.Frame f = new Draw.Frame(d, cx, cz, alongX ? 0 : 1);
        // level the pitch
        f.box(-4, y, -4, 4, y, 4, Draw.of(RACK));
        f.box(-4, y + 1, -4, 4, y + 5, 4, Draw.AIR);
        // tent: an A-frame of hide-coloured wool along u, open at the +u end
        for (int u = -3; u <= 1; u++) {
            f.set(u, y + 1, -2, WOOL_B); f.set(u, y + 2, -1, WOOL_W); f.set(u, y + 3, 0, WOOL_B);
            f.set(u, y + 2, 1, WOOL_W); f.set(u, y + 1, 2, WOOL_B);
        }
        f.box(-3, y + 1, -1, -3, y + 2, 1, Draw.of(WOOL_W));
        f.box(-2, y + 1, -1, 0, y + 1, 0, Draw.of(CARPET_R));
        f.chest(-2, y + 1, 1, Draw.EAST, "jaspr:wonder/camp");
        // campfire four blocks from the tent (fire cannot reach the wool)
        f.box(3, y, -1, 3, y, -1, Draw.of(RACK));
        f.set(3, y + 1, -1, FIRE);
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) f.set(3 + o[0], y, -1 + o[1], COBBLE);
        f.set(4, y + 1, 2, LOG);
        f.skull(1, y + 1, 3, 0, 5);
        // the flag: a fence pole with a red rag
        f.box(-4, y + 1, 3, -4, y + 4, 3, Draw.of(NB_FENCE));
        f.set(-4, y + 4, 2, b(35, 14));
        register(d, "lost_camp", cx - 4, y, cz - 4, cx + 4, y + 4, cz + 4);
        return "lost_camp";
    }

    /** A cage hanging on a chain from the cavern roof, a skull and a chest of remains inside. */
    private String cage(Draw d, Random r) {
        int cx = d.x0 + 6 + r.nextInt(4), cz = d.z0 + 6 + r.nextInt(4);
        for (int y = 118; y > 50; y--) {
            if (!Blocks.isFullSolid(d.id(cx, y, cz)) || !d.air(cx, y - 1, cz)) continue;
            int clear = 0;
            while (clear < 20 && d.air(cx, y - 1 - clear, cz)) clear++;
            if (clear < 14) return null;
            int chain = 3 + r.nextInt(4), top = y - 1 - chain;
            for (int k = 1; k <= chain; k++) d.set(cx, y - k, cz, NB_FENCE);
            d.box(cx - 1, top, cz - 1, cx + 1, top, cz + 1, Draw.of(NB_SLAB));
            for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) d.box(cx + o[0], top - 3, cz + o[1], cx + o[0], top - 1, cz + o[1], Draw.of(NB_FENCE));
            for (int[] o : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) d.box(cx + o[0], top - 2, cz + o[1], cx + o[0], top - 1, cz + o[1], Draw.of(NB_FENCE));
            d.box(cx - 1, top - 4, cz - 1, cx + 1, top - 4, cz + 1, Draw.of(NB_SLAB_TOP));
            d.chest(cx, top - 3, cz, Draw.SOUTH, "jaspr:wonder/cage");
            d.skull(cx + (r.nextBoolean() ? 1 : -1), top - 3, cz, 0, r.nextInt(16));
            d.set(cx, top - 3, cz + 1, 0);
            register(d, "hanging_cage", cx - 1, top - 4, cz - 1, cx + 1, y, cz + 1);
            return "hanging_cage";
        }
        return null;
    }

    /** An amethyst geode: a basalt shell, a purpur lining studded with amethyst ore, hollow inside; seen where caves cut it. */
    private String geode(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8, rad = 4 + r.nextInt(2);
        for (int tries = 0; tries < 6; tries++) {
            int cy = 40 + r.nextInt(60);
            int solid = 0, air = 0, n = 0;
            for (int x = -rad; x <= rad; x += 2) for (int y = -rad; y <= rad; y += 2) for (int z = -rad; z <= rad; z += 2) {
                n++;
                int id = d.id(cx + x, cy + y, cz + z);
                if (Blocks.isFullSolid(id)) solid++; else if (id == 0) air++;
            }
            if (solid < n * 0.6 || air < n * 0.06) continue;
            int ore = Blocks.AMETHYST_ORE;
            for (int x = -rad; x <= rad; x++) for (int y = -rad; y <= rad; y++) for (int z = -rad; z <= rad; z++) {
                double dist = Math.sqrt(x * x + y * y + z * z);
                if (dist > rad + 0.4) continue;
                int wx = cx + x, wy = cy + y, wz = cz + z;
                if (dist > rad - 0.6) { if (Blocks.isFullSolid(d.id(wx, wy, wz))) d.set(wx, wy, wz, BASALT); }
                else if (dist > rad - 1.6) {
                    if (!Blocks.isFullSolid(d.id(wx, wy, wz))) continue;   // the lining shows only where rock was
                    double q = Draw.rnd(wx, wy, wz, 88);
                    d.set(wx, wy, wz, q < 0.3 ? ore : q < 0.34 ? GLOW : PURPUR);
                } else d.set(wx, wy, wz, 0);
            }
            register(d, "amethyst_geode", cx - rad, cy - rad, cz - rad, cx + rad, cy + rad, cz + rad);
            return "amethyst_geode";
        }
        return null;
    }

    /** A ghast's skeleton half sunk in the ground: a spine, rib arches and a hollow skull. */
    private String fossil(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8;
        int y = floor(d, cx, cz, 6);
        if (y < 0 || !flat(d, cx, cz, 3, y, 3)) return null;
        boolean alongX = r.nextBoolean();
        int len = 9 + r.nextInt(4);
        Draw.Frame f = new Draw.Frame(d, cx, cz, alongX ? 0 : 1);
        int u0 = -len / 2;
        for (int t = 0; t < len; t++) {
            int u = u0 + t;
            f.set(u, y, 0, alongX ? BONE_X : BONE_Z);
            if (t % 2 == 1 && t < len - 3) {
                int half = 3 + (t < len / 2 ? t / 3 : (len - t) / 3);
                for (int k = -half; k <= half; k++) {
                    double q = Math.abs(k) / (double) half;
                    int hy = y + (int) Math.round(Math.sqrt(Math.max(0, 1 - q * q)) * half * 0.9);
                    f.set(u, hy, k, BONE_Y);
                }
            }
        }
        int hu = u0 + len;
        f.box(hu, y, -2, hu + 3, y + 3, 2, Draw.of(BONE_Y));
        f.box(hu + 1, y + 1, -1, hu + 2, y + 2, 1, Draw.AIR);
        f.set(hu + 3, y + 2, -1, 0); f.set(hu + 3, y + 2, 1, 0);
        register(d, "ghast_fossil", cx - 8, y, cz - 8, cx + 8, y + 7, cz + 8);
        return "ghast_fossil";
    }

    /** A soul-sand graveyard: rows of brick headstones, a lantern, bones, and one grave hiding a chest. */
    private String graveyard(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8;
        int y = floor(d, cx, cz, 4);
        if (y < 0 || !flat(d, cx, cz, 5, y, 3)) return null;
        d.box(cx - 6, y, cz - 6, cx + 6, y, cz + 6, Draw.of(SOUL));
        d.box(cx - 6, y + 1, cz - 6, cx + 6, y + 4, cz + 6, Draw.AIR);
        d.walls(cx - 6, y + 1, cz - 6, cx + 6, y + 1, cz + 6, Draw.of(NB_FENCE));
        d.set(cx, y + 1, cz + 6, 0);
        int hidden = r.nextInt(6);
        int i = 0;
        for (int gx = -3; gx <= 3; gx += 3) for (int gz = -3; gz <= 0; gz += 3) {
            int x = cx + gx, z = cz + gz;
            d.set(x, y + 1, z - 1, NB);
            d.set(x, y + 2, z - 1, NB_SLAB);
            d.set(x, y + 1, z, NB_SLAB);
            d.set(x, y + 1, z + 1, NB_SLAB);
            if (i++ == hidden) d.chest(x, y - 1, z, Draw.NORTH, "jaspr:wonder/grave");
        }
        d.box(cx + 5, y + 1, cz + 4, cx + 5, y + 3, cz + 4, Draw.of(NB_FENCE));
        d.set(cx + 5, y + 4, cz + 4, GLOW);
        d.set(cx - 4, y + 1, cz + 4, BONE_Y);
        d.set(cx - 3, y + 1, cz + 3, BONE_X);
        d.skull(cx + 2, y + 1, cz + 4, 0, r.nextInt(16));
        register(d, "soul_graveyard", cx - 6, y - 1, cz - 6, cx + 6, y + 4, cz + 6);
        return "soul_graveyard";
    }

    /** A grove of basalt columns, floor to roof or broken off, magma at their feet. */
    private String columns(Draw d, Random r) {
        int n = 3 + r.nextInt(5), made = 0;
        for (int i = 0; i < n; i++) {
            int x = d.x0 + 2 + r.nextInt(12), z = d.z0 + 2 + r.nextInt(12);
            int y = floor(d, x, z, 3);
            if (y < 0) continue;
            int top = y + 1;
            while (top < 120 && d.air(x, top, z)) top++;
            boolean whole = top < 120 && top - y < 40 && r.nextInt(3) > 0;
            int end = whole ? top - 1 : y + 3 + r.nextInt(8);
            double rad = r.nextInt(3) == 0 ? 1.6 : 0.6;
            d.cyl(x, z, rad, y + 1, end, Draw.of(BASALT));
            d.disk(x, z, rad + 1, y, (xx, yy, zz) -> Draw.rnd(xx, yy, zz, 5) < 0.5 ? MAGMA : -1);
            made++;
        }
        return made > 0 ? "basalt_grove" : null;
    }

    /** A fairy ring of mushrooms around a patch of hyphae. */
    private String ring(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8;
        int y = floor(d, cx, cz, 3);
        if (y < 0 || !flat(d, cx, cz, 5, y, 2)) return null;
        double rad = 4 + r.nextInt(2);
        d.disk(cx, cz, rad + 0.5, y, Draw.of(MYCEL));
        for (int x = cx - 6; x <= cx + 6; x++) for (int z = cz - 6; z <= cz + 6; z++) {
            double dist = Math.hypot(x - cx, z - cz);
            if (Math.abs(dist - rad) < 0.6 && d.air(x, y + 1, z)) d.set(x, y + 1, z, ((x + z) & 1) == 0 ? SHROOM_R : SHROOM_B);
        }
        d.set(cx, y + 1, cz, Blocks.ELDER_STEM);
        d.set(cx, y + 2, cz, Blocks.ELDER_STEM);
        d.box(cx - 1, y + 3, cz - 1, cx + 1, y + 3, cz + 1, Draw.of(b(100, 14)));
        d.set(cx, y + 4, cz, b(100, 14));
        register(d, "fairy_ring", cx - 6, y, cz - 6, cx + 6, y + 4, cz + 6);
        return "fairy_ring";
    }

    /** A frozen obelisk: ice and quartz on a plinth, a lantern crown, blue-fire braziers and an offering chest. */
    private String obelisk(Draw d, Random r) {
        int cx = d.x0 + 8, cz = d.z0 + 8;
        int y = floor(d, cx, cz, 12);
        if (y < 0 || !flat(d, cx, cz, 3, y, 3)) return null;
        int h = 7 + r.nextInt(5);
        d.box(cx - 3, y, cz - 3, cx + 3, y, cz + 3, Draw.of(QZ_CHISELED));
        d.box(cx - 3, y + 1, cz - 3, cx + 3, y + 3, cz + 3, Draw.AIR);
        d.box(cx - 1, y + 1, cz - 1, cx + 1, y + h, cz + 1, (x, yy, z) -> Math.floorMod(yy, 4) == 0 ? QUARTZ : ICE);
        d.set(cx, y + h + 1, cz, LANTERN);
        for (int[] o : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            d.set(cx + o[0], y + 1, cz + o[1], RACK);
            d.set(cx + o[0], y + 2, cz + o[1], FIRE);
            d.point(cx + o[0], y + 2, cz + o[1], "bluefire");
        }
        d.chest(cx, y + 1, cz + 2, Draw.SOUTH, "jaspr:wonder/obelisk");
        register(d, "frozen_obelisk", cx - 3, y, cz - 3, cx + 3, y + h + 1, cz + 3);
        return "frozen_obelisk";
    }

    // ---- the expedition's journal ------------------------------------------------------------------------------------
    private static final String[] WRITERS = {"Surveyor Ilsa Brand", "Brother Ostric", "Captain Maren Holt", "Quartermaster Dov", "Cartographer Wen",
        "Sister Adaeze", "Old Pell the Guide", "Lieutenant Varro"};
    private static final String[] FATE = {"We came through the portal twelve strong. We are five now.", "The guide swears the heat speaks at night.",
        "Our water turned to steam on the second day.", "The ghasts found our first camp. This is the second.", "We have eaten the last of the bread."};

    /** Compass words for the direction from (x, z) to (tx, tz) (north is -z). */
    static String bearing(int x, int z, int tx, int tz) {
        double a = Math.toDegrees(Math.atan2(tx - x, -(tz - z)));
        String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8L)];
    }

    private static String rumour(Mega.Kind k) {
        switch (k) {
            case BAZAAR: return "The Pigtificates speak of their golden city, where amethyst buys anything";
            case PYRAMID: return "A pyramid of soul-stone stands in the sands; its king was buried with his wither bones";
            case FORGE: return "Chimneys pierce the roof of the fire wastes; the forge there never went cold";
            case CATHEDRAL: return "A mushroom as tall as a mountain; on its crown the Queen's urn waits for sorrow";
            default: return "A citadel of ice in the frozen deep; the Wight Lord hoards the richest vault";
        }
    }

    /** The journal of the camp's writer: a line of their fate, then a rumour of each mega structure within reach. */
    static List<String> journalPages(Gen g, int x, int z, Random r) {
        List<String> pages = new ArrayList<>();
        pages.add("Day " + (3 + r.nextInt(40)) + ".\n\n" + FATE[r.nextInt(FATE.length)] + "\n\nI write down what we learned, for whoever comes after.");
        for (Mega.Kind k : Mega.Kind.values()) {
            Mega.Site s = g.nearestMega(k, x, z, 4);
            if (s == null) continue;
            int dist = (int) (Math.round(Math.hypot(s.x - x, s.z - z) / 10.0) * 10);
            pages.add(k.display + "\n\n" + rumour(k) + ".\n\nAbout " + dist + " blocks " + bearing(x, z, s.x, s.z) + " of this camp.");
        }
        pages.add("If you read this, bring fire resistance, and bring gold for the pigs.\n\nDo not follow the crying. It is the Queen.");
        return pages;
    }

    static org.bukkit.inventory.ItemStack journal(Gen g, int x, int z, Random r) {
        org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta m = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        m.setTitle("Expedition Journal");
        m.setAuthor(WRITERS[r.nextInt(WRITERS.length)]);
        m.setPages(journalPages(g, x, z, r));
        book.setItemMeta(m);
        return book;
    }
}
