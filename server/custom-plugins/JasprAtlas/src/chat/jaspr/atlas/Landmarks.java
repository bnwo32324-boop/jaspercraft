package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The great buildings of the Concord's cities, where its institutions live and its key figures can always be found:
 * Astreion's Synedrion, Great Library, Lyceum, Agora, Stoa of Shields, House of Return, theatre, baths and the Hearth of
 * Theano; Lampsa's Mechaneion; Hieranthe's Asklepieion; Mnemeia's Archive of Memory; and a heliodrome in every city.
 */
final class Landmarks {
    private Landmarks() {}

    /** Whether a city block (centre cx, cz) is taken by a landmark rather than houses. */
    static boolean reserved(Realm.Place p, Cities.Plan plan, int cx, int cz) {
        int inner = plan.tiers[0][0];
        return Math.max(Math.abs(cx), Math.abs(cz)) <= inner + 9;
    }

    static void draw(Plans plans, Frame f, Realm.Place p, Cities.Plan plan) {
        long h = Hash.of(plans.seed ^ 0x1A7DL, p.x, p.z);
        int civic = plan.tiers[0][1];
        switch (p) {
            case ASTREION: {
                int acro = plan.tiers[1][1];
                at(f, -40, -40, civic, 0, fr -> greatLibrary(fr, h));
                at(f, 0, -40, civic, 0, fr -> synedrion(fr, h));
                at(f, 40, -40, civic, 0, fr -> lyceum(fr, h));
                at(f, -40, 0, civic, 1, fr -> agora(fr, h, "astreion", "the Agora of Astreion"));
                at(f, 40, 0, civic, 3, fr -> stoaOfShields(fr, h));
                at(f, -40, 40, civic, 2, fr -> houseOfReturn(fr, h));
                at(f, 0, 40, civic, 2, fr -> theatre(fr, h));
                at(f, 40, 40, civic, 2, fr -> Houses.bath(fr, h ^ 3));
                at(f, 0, 0, acro, 1, fr -> hearthOfTheano(fr, h));
                break;
            }
            case LAMPSA:
                at(f, 0, 0, civic, 0, fr -> mechaneion(fr, h));
                at(f, -44, -44, 0, 0, fr -> heliodrome(fr, "lampsa", "LAMPSA"));
                break;
            case HIERANTHE:
                at(f, 0, 0, civic, 0, fr -> asklepieion(fr, h));
                at(f, -44, 44, 0, 0, fr -> heliodrome(fr, "hieranthe", "HIERANTHE"));
                break;
            default:
                at(f, 0, 0, civic, 0, fr -> archive(fr, h));
                at(f, 40, 40, 0, 0, fr -> heliodrome(fr, "mnemeia", "MNEMEIA"));
        }
    }

    private static void at(Frame f, int x, int z, int rise, int rot, java.util.function.Consumer<Frame> build) {
        Frame fr = new Frame(f.c, f.wx(x, z), f.wz(x, z), f.base + rise, (f.rot + rot) & 3);
        if (fr.touches(-30, -30, 30, 30)) build.accept(fr);
    }

    // ------------------------------------------------------------------ Astreion

    /** The Synedrion: a round council hall with tiered seats, a central table and Archon Kleio Theanid. */
    static void synedrion(Frame f, long h) {
        int r = 13;
        for (int x = -r - 1; x <= r + 1; x++)
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 1.5) continue;
                f.footing(x, z, 0, true);
                f.pave(x, 0, z);
                if (d > r + 0.5) { if (Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), 20) < 7) Build.column(f, x, z, 1, 8); continue; }
                boolean wall = d > r - 0.6;
                for (int y = 1; y <= 9; y++) {
                    if (wall) { if (y >= 2 && y <= 3 && Math.abs(x) <= 1 && z < 0) f.set(x, y, z, AIR); else if (y == 5 && (Math.abs(x) <= 1 || Math.abs(z) <= 1)) f.set(x, y, z, STAINED_PANE, LIGHT_BLUE); else f.marble(x, y, z); }
                    else f.set(x, y, z, AIR);
                }
                if (wall && Math.abs(x) <= 1 && z < 0) f.set(x, 1, z, AIR);
                // Tiered seating rising toward the wall.
                if (!wall && d > 6) {
                    int tier = (int) Math.min(3, (d - 6) / 2);
                    for (int y = 1; y <= tier; y++) f.ashlar(x, y, z);
                    if (!(Math.abs(x) <= 1 && z < 0)) f.set(x, tier + 1, z, QUARTZ_STAIRS, seatToward(f, x, z));
                }
                // The dome.
                double dome = 10 + Math.sqrt(Math.max(0, (r + 1) * (r + 1) - d * d)) * 0.45;
                f.set(x, (int) dome, z, d < 2 ? STAINED_GLASS : DOUBLE_SLAB, d < 2 ? LIGHT_BLUE : 7);
            }
        Build.mosaic(f, -5, -5, 5, 5, 0);
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) f.set(x, 1, z, SLAB, 8 | 7);
        f.set(0, 1, 2, QUARTZ, 1); f.set(0, 2, 2, SEA_LANTERN);
        f.chest(3, 1, 0, -1, 0, "atlas:synedrion", "book:synedrion");
        f.sign(0, 2, -r - 1, 0, -1, "THE SYNEDRION\nOF THE\nASTERIAN\nCONCORD");
        f.npc(0, 1, -2, 0, -1, "key:kleio", null);
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * (0.15 + 0.7 * k / 5.0);
            f.npc((int) Math.round(Math.cos(a) * 9), 3, (int) Math.round(Math.sin(a) * 9), 0, -1, "citizen:archon", null);
        }
        for (int[] p : new int[][] {{-3, -r - 2}, {3, -r - 2}}) f.npc(p[0], 1, p[1], 0, -1, "talos", null);
    }

    private static int seatToward(Frame f, int x, int z) {
        if (Math.abs(x) > Math.abs(z)) return f.stairs(x > 0 ? 1 : -1, 0, false);
        return f.stairs(0, z > 0 ? 1 : -1, false);
    }

    /** The Great Library of Astreion: the largest collection in Atlas, three halls round a court, Librarian Eudora. */
    static void greatLibrary(Frame f, long h) {
        Houses.library(f, h, 14, 12, "great");
        f.npc(2, 1, 3, 0, -1, "key:eudora", null);
        f.sign(0, 5, -13, 0, -1, "THE GREAT\nLIBRARY\nWHAT IS SHARED\nIS NOT LESS");
        // The Register of the Lost: a wall of names, and a desk where the lost are recorded.
        for (int x = -6; x <= 6; x += 3) f.sign(x, 2, 10, 0, -1, Texts.grave(Hash.of(h, x, 1)));
    }

    /** The Lyceum: classrooms, an orrery court and the philosopher Anaxis the Quiet. */
    static void lyceum(Frame f, long h) {
        f.npc(0, 1, 4, 0, -1, "key:anaxis", null);   // first: his place is kept whatever is built round it
        Frame a = new Frame(f.c, f.wx(-8, -6), f.wz(-8, -6), f.base, f.rot);
        Houses.school(a, h);
        Frame b = new Frame(f.c, f.wx(9, -6), f.wz(9, -6), f.base, f.rot);
        Houses.library(b, h ^ 9, 7, 6, "lyceum");
        Frame o = new Frame(f.c, f.wx(0, 11), f.wz(0, 11), f.base, f.rot);
        CellsConcord.draw(null, o, new Plans.Cell(0, 0, 0, 0, f.base, 0, h ^ 5, Realm.Zone.CONCORD, "orrery", false));
    }

    /** An agora: a great paved square with a fountain, market stalls on every side, a heliodrome and a notice stone. */
    static void agora(Frame f, long h, String helioId, String title) {
        int r = 15;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) { f.footing(x, z, 0, true); f.pave(x, 0, z); f.clear(x, z, 1, 8); }
        Frame fc = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), f.base, f.rot);
        Cities.fountainCourt(fc, h);
        for (int k = -1; k <= 1; k += 2) {
            Frame s = new Frame(f.c, f.wx(0, k * 12), f.wz(0, k * 12), f.base, (f.rot + (k > 0 ? 0 : 2)) & 3);
            Houses.stoa(s, h ^ k, 11);
        }
        Frame hd = new Frame(f.c, f.wx(-11, 0), f.wz(-11, 0), f.base, f.rot);
        heliodrome(hd, helioId, title.toUpperCase().replace("THE AGORA OF ", ""));
        f.post(10, 1, -3, -1, 0, "NOTICE:\nTHE SYNEDRION\nSEEKS HELP OF\nSTRANGERS");
    }

    /** The Stoa of Shields: the Concord's war-hall, shields and arms on the walls, Strategos Lysandra Kallid. */
    static void stoaOfShields(Frame f, long h) {
        int x0 = -13, x1 = 13, z0 = -8, z1 = 8;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.pavedFloor(f, x0, z0, x1, z1, 0);
        Build.walls(f, x0, z0 + 1, x1, z1, 1, 8, 0);
        Build.hollow(f, x0 + 1, z0 + 2, x1 - 1, z1 - 1, 1, 8);
        for (int x = x0; x <= x1; x += 3) Build.column(f, x, z0, 1, 7);
        for (int x = x0; x <= x1; x++) f.set(x, 8, z0, QUARTZ, 0);
        Build.floor(f, x0, z0, x1, z1, 9, DOUBLE_SLAB, 7);
        Build.gable(f, x0, z0, x1, z1, 10, false, 1);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 4; y++) f.set(x, y, z0 + 1, AIR);
        // Shields (banners) and armour stands along the walls: the arms of every company of the Line.
        for (int x = x0 + 2; x <= x1 - 2; x += 3) { f.banner(x, 4, z1 - 1, 0, -1, true, "concord"); f.stand(x, 1, z1 - 2, 0, -1, "hoplite_rack"); }
        // The Oath shrine: where every strategos swears, and where the Oath of Kallias is kept.
        f.set(0, 1, z1 - 4, QUARTZ, 1); f.set(0, 2, z1 - 4, SEA_LANTERN);
        f.chest(1, 1, z1 - 4, -1, 0, "atlas:oath_shrine", "book:oath");
        f.sign(0, 3, z1 - 3, 0, -1, "I WILL NOT\nLIFT MY SPEAR\nAGAINST THE\nUNARMED");
        f.npc(0, 1, 2, 0, -1, "key:lysandra", null);
        f.npc(-8, 1, 2, 0, -1, "citizen:hoplite", null);
        f.npc(8, 1, 2, 0, -1, "citizen:hoplite", null);
        for (int k = 0; k < 3; k++) f.stand(-8 + k * 8, 1, -12, 0, 1, "target");
        f.npc(-4, 1, -11, 0, 1, "talos", null);
    }

    /** The House of Return: where those freed from the Dominion are brought, fed and healed; berths wait for the rescued. */
    static void houseOfReturn(Frame f, long h) {
        int x0 = -13, x1 = 13, z0 = -10, z1 = 10;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 7, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 7);
        Build.gable(f, x0, z0, x1, z1, 8, false, 0);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) f.set(x, y, z0, AIR);
        for (int x = x0 + 3; x <= x1 - 3; x += 4) { Build.window(f, x, 3, z0, WHITE); Build.window(f, x, 3, z1, WHITE); }
        // Berths: beds along both long walls; each rescued person comes to one.
        int berth = 0;
        for (int x = x0 + 2; x <= x1 - 2; x += 3)
            for (int side = -1; side <= 1; side += 2) {
                int z = side < 0 ? z0 + 2 : z1 - 2;
                f.bed(x, 1, z, 0, -side, side < 0 ? LIGHT_BLUE : WHITE);
                f.npc(x, 1, z + (side < 0 ? 2 : -2), 0, -side, "berth:" + berth++, null);
            }
        // A long table with bread, a hearth, and the keepers.
        for (int x = -6; x <= 6; x++) f.set(x, 1, 0, SLAB, 8 | 2);
        for (int x = -6; x <= 6; x += 2) { Build.seat(f, x, 1, -1, 0, 1, BIRCH_STAIRS); Build.seat(f, x, 1, 1, 0, -1, BIRCH_STAIRS); }
        f.set(x1 - 1, 1, 0, FURNACE, f.facing(-1, 0)); f.set(x1 - 1, 1, 1, CAULDRON, 3);
        f.chest(x1 - 1, 1, -2, -1, 0, "atlas:house_of_return", "book:return");
        for (int x = -9; x <= 9; x += 6) Build.ceilingLamp(f, x, 7, 0);
        f.sign(0, 4, z0 - 1, 0, -1, "THE HOUSE\nOF RETURN\nWHOEVER COMES\nHOME, EATS");
        f.npc(-3, 1, 3, 0, -1, "citizen:keeper_of_return", null);
        f.npc(4, 1, -3, 0, 1, "citizen:healer", null);
    }

    /** The theatre: a half-bowl of stepped seats facing a stage and its skene, actors rehearsing. */
    static void theatre(Frame f, long h) {
        int r = 15;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.5) continue;
                f.footing(x, z, 0, true);
                if (d < 5) { f.set(x, 0, z, DOUBLE_SLAB, 8); f.clear(x, z, 1, 10); continue; }
                int tier = (int) ((d - 5) / 1.0);
                for (int y = 0; y <= tier / 2; y++) f.ashlar(x, y, z);
                f.set(x, tier / 2 + 1, z, QUARTZ_STAIRS, seatToward(f, x, z));
                f.clear(x, z, tier / 2 + 2, tier / 2 + 5);
            }
        // Stage and skene.
        for (int x = -8; x <= 8; x++) for (int z = 3; z <= 6; z++) { f.footing(x, z, 1, true); f.set(x, 1, z, PLANKS, 2); f.clear(x, z, 2, 8); }
        for (int x = -8; x <= 8; x++) for (int y = 2; y <= 7; y++) { if (Math.abs(x) <= 1 && y <= 4) continue; f.marble(x, y, 7); }
        for (int x = -8; x <= 8; x += 4) Build.column(f, x, 6, 2, 7);
        f.npc(-2, 2, 4, 0, -1, "citizen:actor", null);
        f.npc(2, 2, 4, 0, -1, "citizen:actor", null);
        f.npc(0, 4, -9, 0, 1, "citizen:poet", null);
    }

    /** The Hearth of Theano: the acropolis temple where the Hearthstar, the Star's blue half, is kept, and Neaira its keeper. */
    static void hearthOfTheano(Frame f, long h) {
        int r = 23;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) { f.set(x, 0, z, GRASS); f.clear(x, z, 1, 30); }
        // Gardens and statues of Theano round the temple.
        Build.flowers(f, -22, -22, 22, 22, 0.25);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) Math.round(Math.cos(a) * 19), z = (int) Math.round(Math.sin(a) * 19);
            if (k % 2 == 0) Build.cypress(f, x, z); else Build.statue(f, x, 1, z, 0, -1, 5);
        }
        for (int z = -22; z <= 22; z++) for (int x = -2; x <= 2; x++) f.pave(x, 0, z);
        Houses.temple(f, h, 11, 15, "Theano");
        // The Hearthstar: a star of blue light on the altar (glass, lanterns and rods; no lapis, nothing to mine).
        int y = 3, z = 8;
        f.set(0, y, z, QUARTZ, 1);
        f.set(0, y + 1, z, SEA_LANTERN);
        f.set(0, y + 2, z, STAINED_GLASS, LIGHT_BLUE);
        f.set(1, y + 2, z, END_ROD, 5); f.set(-1, y + 2, z, END_ROD, 4); f.set(0, y + 2, z + 1, END_ROD, 3); f.set(0, y + 2, z - 1, END_ROD, 2);
        f.set(0, y + 3, z, END_ROD, 1);
        f.set(0, y + 1, z - 1, AIR);
        f.chest(2, y, z, -1, 0, "atlas:hearth", "book:hearth");
        f.npc(0, y, z - 3, 0, -1, "key:neaira", null);
        for (int[] p : new int[][] {{-6, -14}, {6, -14}, {-6, 14}, {6, 14}}) f.npc(p[0], 1, p[1], 0, -1, "talos", null);
        f.sign(0, 1, -16, 0, -1, "THE HEARTH\nOF THEANO\nWHERE THE\nLIGHT WAITS");
    }

    // ------------------------------------------------------------------ the other cities

    /** Lampsa's Mechaneion: workshops, an automaton line, the lumen foundry, and Mechanic Perdix among Daidaros's papers. */
    static void mechaneion(Frame f, long h) {
        int x0 = -18, x1 = 18, z0 = -14, z1 = 14;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.pavedFloor(f, x0, z0, x1, z1, 0);
        Build.walls(f, x0, z0, x1, z1, 1, 10, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 10);
        Build.floor(f, x0, z0, x1, z1, 11, DOUBLE_SLAB, 7);
        for (int x = x0 + 3; x <= x1 - 3; x += 4) for (int z = z0 + 3; z <= z1 - 3; z += 4) f.set(x, 11, z, STAINED_GLASS, LIGHT_BLUE);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 5; y++) f.set(x, y, z0, AIR);
        // The automaton line: three Talos in frames, the last one walking.
        for (int k = 0; k < 3; k++) {
            int x = -12 + k * 6;
            for (int y = 1; y <= 5; y++) { f.set(x - 2, y, 6, FENCE); f.set(x + 2, y, 6, FENCE); }
            f.set(x, 1, 6, STONE, 6); f.set(x, 2, 6, STONE, 6); f.set(x, 3, 6, STONE, 6); f.set(x - 1, 3, 6, STONE, 6); f.set(x + 1, 3, 6, STONE, 6); f.set(x, 4, 6, PUMPKIN, 0);
        }
        f.npc(8, 1, 6, 0, -1, "talos", null);
        // The lumen foundry: prismarine crucibles, lantern racks.
        for (int x = x0 + 2; x <= x0 + 8; x += 2) { f.set(x, 1, z0 + 2, PRISMARINE, 1); f.set(x, 2, z0 + 2, SEA_LANTERN); f.set(x, 1, z0 + 3, CAULDRON, 3); }
        // Perdix's office: Daidaros's desk and notebooks.
        Build.walls(f, 9, -12, 17, -4, 1, 5, 0);
        Build.hollow(f, 10, -11, 16, -5, 1, 5);
        f.set(13, 1, -4, AIR); f.set(13, 2, -4, AIR);
        Build.table(f, 13, 1, -9, true); Build.seat(f, 13, 1, -8, 0, -1, BIRCH_STAIRS);
        Build.shelves(f, 16, -11, 1, 4); Build.shelves(f, 16, -10, 1, 4); Build.shelves(f, 16, -9, 1, 4);
        f.chest(10, 1, -11, 1, 0, "atlas:mechaneion", "book:daidaros");
        f.npc(12, 1, -7, 0, -1, "key:perdix", null);
        for (int k = 0; k < 4; k++) f.npc(-14 + k * 7, 1, -4, 0, 1, "citizen:mechanic", null);
        f.set(-15, 1, 10, ANVIL, 0); f.set(-14, 1, 10, WORKBENCH); f.set(-13, 1, 10, FURNACE, f.facing(0, -1));
        f.sign(0, 7, z0 - 1, 0, -1, "THE MECHANEION\nOF LAMPSA\nHANDS THAT\nSPARE HANDS");
        Frame hd = new Frame(f.c, f.wx(0, -22), f.wz(0, -22), f.base, f.rot);
        heliodrome(hd, "lampsa_centre", "LAMPSA");
    }

    /** Hieranthe's Asklepieion: the houses of healing, a sacred spring, herb gardens, and Priestess Iaso. */
    static void asklepieion(Frame f, long h) {
        int x0 = -18, x1 = 18, z0 = -12, z1 = 12;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.pavedFloor(f, x0, z0, x1, z1, 0);
        f.air(x0, 1, z0, x1, 10, z1);
        // The ward: a long hall of beds.
        Frame ward = new Frame(f.c, f.wx(0, 6), f.wz(0, 6), f.base, f.rot);
        Build.room(ward, -16, -5, 16, 5, 0, 6, 0);
        Build.gable(ward, -16, -5, 16, 5, 7, false, 0);
        for (int x = -14; x <= 14; x += 3) { ward.bed(x, 1, 3, 0, 1, WHITE); ward.set(x + 1, 1, 4, FLOWER_POT); }
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) ward.set(x, y, -5, AIR);
        ward.npc(0, 1, 0, 0, 1, "citizen:healer", null);
        ward.npc(-8, 1, 3, 0, -1, "citizen:patient", null);
        // The sacred spring with its serpent-staff column, and the herb garden.
        for (int x = -3; x <= 3; x++) for (int z = -10; z <= -5; z++) f.set(x, 0, z, Math.abs(x) <= 2 && z > -10 && z < -5 ? WATER : QUARTZ, 0);
        Build.column(f, 0, -11, 1, 5); f.set(0, 6, -11, SEA_LANTERN);
        Build.flowers(f, -17, -11, -6, -1, 0.7);
        Build.flowers(f, 6, -11, 17, -1, 0.7);
        f.npc(2, 1, -4, 0, -1, "key:iaso", null);
        f.chest(-2, 1, -4, 0, -1, "atlas:asklepieion", "book:hymns");
        f.sign(0, 2, -12, 0, -1, "THE ASKLEPIEION\nLET THE LAMP\nGO OUT\nGENTLY");
        Frame t = new Frame(f.c, f.wx(0, -26), f.wz(0, -26), f.base, f.rot);
        heliodrome(t, "hieranthe_centre", "HIERANTHE");
    }

    /** Mnemeia's Archive of Memory: vaults of shelves, the Charter vault, Archivist Hesper and the copyists. */
    static void archive(Frame f, long h) {
        Houses.library(f, h, 16, 12, "archive");
        // The Charter vault: a small sealed room behind the hall with the Founding Charter under glass.
        Frame v = new Frame(f.c, f.wx(0, 16), f.wz(0, 16), f.base, f.rot);
        Build.room(v, -5, -3, 5, 3, 0, 5, 0);
        Build.flatRoof(v, -5, -3, 5, 3, 6, 0);
        v.set(0, 1, -3, AIR); v.set(0, 2, -3, AIR);
        v.set(0, 1, 1, QUARTZ, 1); v.set(0, 2, 1, STAINED_GLASS, LIGHT_BLUE); v.set(0, 3, 1, SEA_LANTERN);
        v.chest(2, 1, 1, -1, 0, "atlas:charter_vault", "book:charter");
        v.sign(0, 2, 0, 0, -1, "THE HOMONOIA\nNO LAW SHALL\nBIND THE TONGUE\nOF THE FREE");
        f.npc(-2, 1, 4, 0, -1, "key:hesper", null);
        for (int k = 0; k < 3; k++) f.npc(-8 + k * 8, 1, -5, 0, 1, "citizen:copyist", null);
        Frame hd = new Frame(f.c, f.wx(0, -18), f.wz(0, -18), f.base, f.rot);
        heliodrome(hd, "mnemeia_centre", "MNEMEIA");
    }

    // ------------------------------------------------------------------ heliodromes

    /** A heliodrome: a ring of quartz pillars round a lumen core; right-click the core to travel between discovered ones. */
    static void heliodrome(Frame f, String id, String name) {
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 5.4) continue;
                f.footing(x, z, 0, true);
                f.set(x, 0, z, d < 1.5 ? PRISMARINE : d > 4.5 ? QUARTZ : DOUBLE_SLAB, d < 1.5 ? 2 : d > 4.5 ? 0 : 7);
                f.clear(x, z, 1, 7);
            }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) Math.round(Math.cos(a) * 4), z = (int) Math.round(Math.sin(a) * 4);
            for (int y = 1; y <= 4; y++) f.set(x, y, z, QUARTZ, 2);
            f.set(x, 5, z, END_ROD, 1);
        }
        f.set(0, 1, 0, SEA_LANTERN);
        f.set(0, 2, 0, END_ROD, 1);
        f.post(0, 1, -6, 0, -1, "HELIODROME\n" + name + "\nTOUCH THE\nLIGHT TO GO");
        f.npc(0, 1, 1, 0, -1, "heliodrome:" + id, name);
    }
}
