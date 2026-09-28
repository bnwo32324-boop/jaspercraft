package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Wound's and the Dominion's medium sites. Towers and forts have floors, stairs, barracks, armouries and cells;
 * camps have their people (the Ashborn who guard, the Taskmasters who drive, the Bound who labour); every labour camp
 * has a shackle post whose breaking frees it. Liberated sites keep their stones and lose their chains.
 */
final class SitesDominion {
    private SitesDominion() {}

    static void draw(Plans plans, Frame f, Plans.Site s, boolean freed) {
        long h = s.hash;
        switch (s.kind) {
            case FALLEN_PHAROS: fallenPharos(f, h); break;
            case DEAD_OUTPOST: deadOutpost(f, h); break;
            case BATTLEFIELD: battlefield(f, h); break;
            case WAR_CAMP: warCamp(f, h, freed, s); break;
            case WATCHTOWER: watchtower(f, h, freed); break;
            case SLAVE_PEN: slavePen(f, h, freed, s); break;
            case SPAWNING_PIT: spawningPit(f, h, freed); break;
            case ASH_FORT: ashFort(f, h, freed, s); break;
            case QUARRY: quarry(f, h, freed, s); break;
            case FORGE_WORKS: forgeWorks(f, h, freed, s); break;
            case STILLING_HOUSE: stillingHouse(f, h, freed, s); break;
            case STONE_GROVE: stoneGrove(f, h, freed); break;
            case RUINED_POLIS: ruinedPolis(f, h, freed); break;
            case EDICT_SQUARE: edictSquare(f, h, freed, s); break;
            case SHRINE_OF_ASH: shrineOfAsh(f, h, freed); break;
            case SLAVE_ROAD_CAMP: roadCamp(f, h, freed, s); break;
            default: break;
        }
    }

    static int dy(Frame f, int x, int z) { return CellsConcord.dy(f, x, z); }

    /** The shackle post at the heart of a labour camp: break it (after its Taskmaster falls) to free the camp. */
    static void shacklePost(Frame f, int x, int z, boolean freed, Plans.Site s) {
        int g = dy(f, x, z);
        if (freed) { f.set(x, g + 1, z, FENCE); f.set(x, g + 2, z, FLOWER_POT); return; }
        f.set(x, g + 1, z, OBSIDIAN);
        f.set(x, g + 2, z, OBSIDIAN);
        f.set(x, g + 3, z, IRON_TRAPDOOR, 8);
        f.set(x + 1, g + 2, z, BARS); f.set(x - 1, g + 2, z, BARS);
        f.sign(x, g + 2, z - 1, 0, -1, "SHACKLE POST\n~\nBREAK IT TO\nFREE THEM");
        f.npc(x, g + 1, z - 3, 0, 1, "camp:" + s.i + ":" + s.j, null);   // the camp's registry marker
    }

    // ------------------------------------------------------------------ the Wound

    private static void fallenPharos(Frame f, long h) {
        // A Pharos of the old Line that stood further east before the Withdrawal: toppled, its lamp shattered.
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 5.4 || d < 4.2) continue;
                f.footing(x, z, 0, true);
                int t = 2 + (int) (f.roll(x, 0, z, 121) * 9);
                for (int y = 1; y <= t; y++) f.marble(x, y, z);
            }
        // The fallen shaft, lying east.
        for (int k = 7; k <= 26; k++)
            for (int w = -2; w <= 2; w++) {
                if (f.roll(k, 0, w, 122) < 0.2) continue;
                f.marble(k, 1, w);
                if (Math.abs(w) <= 1) f.marble(k, 2, w);
            }
        for (int k = 0; k < 6; k++) f.set(27 + (k % 3), 1, -1 + k / 3, STAINED_GLASS, k % 2 == 0 ? LIGHT_BLUE : BLACK);
        f.sign(0, 2, -6, 0, -1, "PHAROS OF\n" + Names.deme(h).toUpperCase() + "\nFELL YL 1126\nLIT NO MORE");
        f.chest(1, 1, 1, 0, -1, "atlas:pharos_ruin", "book:lost");
        CellsWound.scatter(f, h, 10);
    }

    private static void deadOutpost(Frame f, long h) {
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 12) continue;
                int t = f.roll(x, 0, z, 123) < 0.3 ? 0 : 1 + (int) (f.roll(x, 1, z, 124) * 4);
                for (int y = 1; y <= t; y++) { int g = dy(f, x, z); f.ashlar(x, g + y, z); }
            }
        Frame tent = f;
        CellsConcord.tent(tent, -6, -6, GRAY);
        f.chest(4, dy(f, 4, 4) + 1, 4, -1, 0, "atlas:outpost_ruin", "book:lost");
        f.sign(0, dy(f, 0, -13) + 1, -13, 0, -1, "OUTPOST OF\n" + Names.deme(h ^ 3).toUpperCase() + "\nHELD SIX\nWINTERS");
        CellsWound.scatter(f, h, 16);
    }

    private static void battlefield(Frame f, long h) {
        for (int k = 0; k < 4; k++) {
            Frame c = new Frame(f.c, f.wx(k % 2 == 0 ? -12 : 12, k < 2 ? -12 : 12), f.wz(k % 2 == 0 ? -12 : 12, k < 2 ? -12 : 12), f.base, (f.rot + k) & 3);
            CellsWound.draw(null, c, new Plans.Cell(0, 0, 0, 0, f.base, 0, Hash.of(h, k, 5), Realm.Zone.WOUND, k % 2 == 0 ? "craters" : "trench", false));
        }
        CellsWound.draw(null, f, new Plans.Cell(0, 0, 0, 0, f.base, 0, h, Realm.Zone.WOUND, "dead_talos", false));
    }

    // ------------------------------------------------------------------ the Ashen Marches

    private static void warCamp(Frame f, long h, boolean freed, Plans.Site s) {
        // A legion's camp: a palisade of black stakes, tents in rows, a command tent, a forge, a drill ground, a pen of
        // captives waiting to be sent to the quarries, and a shackle post.
        Sites.pad(f, 29, 1);
        for (int x = -28; x <= 28; x++)
            for (int z = -28; z <= 28; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 28) continue;
                if (Math.abs(x) <= 2 && z == -28) continue;   // the gate
                for (int y = 1; y <= 3; y++) f.set(x, y, z, freed ? (y == 1 ? FENCE : AIR) : NETHER_FENCE);
                if (!freed && Math.floorMod(x + z, 3) == 0) f.set(x, 4, z, BARS);
            }
        for (int x = -3; x <= 3; x += 6) { for (int y = 1; y <= 6; y++) f.cinder(x, y, -28); if (!freed) Build.brazier(f, x, 7, -28); }
        for (int row = -1; row <= 1; row += 2)
            for (int k = -3; k <= 3; k++) {
                if (freed && (k + row) % 2 == 0) continue;
                CellsConcord.tent(f, k * 7, row * 12, freed ? GRAY : BLACK);
                if (!freed && k % 2 == 0) f.npc(k * 7, 1, row * 12 + (row < 0 ? 3 : -3), 0, -row, "dominion:ashborn", null);
            }
        // The command tent (red), with a table of maps and orders.
        for (int x = -4; x <= 4; x++) for (int z = 19; z <= 25; z++) { int y = 5 - Math.abs(x); if (y > 0) f.set(x, y, z, WOOL, freed ? WHITE : RED); for (int yy = 1; yy < y; yy++) if (Math.abs(x) < 4 && z > 19) f.set(x, yy, z, AIR); }
        Build.table(f, 0, 1, 22, true);
        f.chest(1, 1, 23, -1, 0, freed ? "atlas:household" : "atlas:orders", freed ? null : "book:orders");
        if (!freed) {
            f.npc(-1, 1, 22, 1, 0, "dominion:warchief", null);
            f.banner(0, 5, 18, 0, -1, false, "black_ash");
            // The captives' pen by the gate.
            for (int x = 10; x <= 20; x++) for (int z = -26; z <= -18; z++) if (x == 10 || x == 20 || z == -26 || z == -18) f.set(x, 1, z, BARS);
            for (int k = 0; k < 3; k++) f.npc(13 + k * 3, 1, -22, 0, -1, "bound:laborer", null);
            shacklePost(f, 15, -16, freed, s);
            f.npc(15, 1, -14, 0, -1, "dominion:taskmaster", null);
        } else f.npc(0, 1, 0, 0, -1, "citizen:settler", null);
        // Forge and drill ground.
        f.set(-18, 1, 20, FURNACE, f.facing(1, 0)); f.set(-18, 1, 21, ANVIL, 0); f.set(-18, 2, 20, NETHER_BRICK);
        for (int k = 0; k < 4; k++) f.stand(-20 + k * 4, 1, -8, 0, -1, freed ? "scarecrow" : "orc_rack");
    }

    private static void watchtower(Frame f, long h, boolean freed) {
        // A black watchtower: four floors (guardroom, barracks, armoury, lookout), a signal fire on top.
        int r = 5, top = 34;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.max(Math.abs(x), Math.abs(z)) + (Math.abs(x) == r && Math.abs(z) == r ? 1 : 0);
                if (d > r) continue;
                boolean shell = d == r;
                f.footing(x, z, 0, false);
                for (int y = 0; y <= top; y++) {
                    if (shell) {
                        boolean slit = (y % 8 == 5) && (x == 0 || z == 0);
                        f.set(x, y, z, slit ? BARS : NETHER_BRICK);
                        if (!slit && f.roll(x, y, z, 125) < 0.5) f.cinder(x, y, z);
                    } else f.set(x, y, z, y % 8 == 0 ? CONCRETE : AIR, BLACK);
                }
            }
        // Door, ladder shaft, and each floor's furnishing.
        for (int y = 1; y <= 2; y++) f.set(0, y, -r, AIR);
        for (int y = 1; y < top; y++) f.set(r - 1, y, r - 1, LADDER, f.facing(-1, 0));
        for (int y = 8; y < top; y += 8) f.set(r - 1, y, r - 1, LADDER, f.facing(-1, 0));
        if (!freed) {
            Build.brazier(f, -3, 1, -3);
            f.npc(0, 1, 0, 0, -1, "dominion:ashborn", null);
            for (int k = 0; k < 3; k++) f.set(-3 + k * 2, 9, 3, HAY);                  // barracks: sleeping mats
            f.npc(0, 9, 0, 0, -1, "dominion:ashborn_bowman", null);
            f.stand(-3, 17, -3, 1, 0, "orc_rack"); f.chest(3, 17, -3, -1, 0, "atlas:armoury", null);   // armoury
            f.npc(0, 25, 0, 0, -1, "dominion:ashborn_bowman", null);                    // lookout
        } else f.chest(3, 17, -3, -1, 0, "atlas:household", null);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if ((Math.abs(x) == r || Math.abs(z) == r) && Math.floorMod(x + z, 2) == 0) { f.set(x, top + 1, z, NETHER_FENCE); f.set(x, top + 2, z, freed ? AIR : BARS); }
        if (!freed) { f.set(0, top + 1, 0, NETHERRACK); f.set(0, top + 2, 0, FIRE); }
        f.sign(0, 2, -r - 1, 0, -1, freed ? "TOWER TAKEN\nBY THE LINE\n~\nKEEP WATCH" : "TOWER OF\n" + Names.ashborn(h).toUpperCase() + "\nSTRANGERS\nARE MEAT");
    }

    private static void slavePen(Frame f, long h, boolean freed, Plans.Site s) {
        // A slave pen: stockades of iron bars, sleeping straw, a water trough, a whipping post, an overseer's platform,
        // and the shackle post.
        Sites.pad(f, 21, 1);
        for (int k = 0; k < 4; k++) {
            int cx = k % 2 == 0 ? -10 : 10, cz = k < 2 ? -8 : 8;
            for (int x = cx - 6; x <= cx + 6; x++)
                for (int z = cz - 5; z <= cz + 5; z++) {
                    boolean wall = Math.abs(x - cx) == 6 || Math.abs(z - cz) == 5;
                    if (wall) { f.set(x, 1, z, freed ? (x == cx ? AIR : FENCE) : BARS); f.set(x, 2, z, freed ? AIR : BARS); }
                    else if (f.roll(x, 0, z, 126) < 0.3) f.set(x, 1, z, freed ? RED_FLOWER : CARPET, freed ? 0 : YELLOW);
                }
            if (!freed) for (int n = 0; n < 2; n++) f.npc(cx - 2 + n * 4, 1, cz, 0, -1, "bound:laborer", null);
        }
        f.set(0, 1, 16, CAULDRON, freed ? 3 : 1);
        if (!freed) {
            for (int y = 1; y <= 4; y++) f.set(-4, y, 0, NETHER_FENCE);
            f.fill(3, 1, -2, 6, 3, 2, NETHER_BRICK, 0);
            f.fill(3, 4, -2, 6, 4, 2, SLAB, 6);
            f.npc(4, 5, 0, -1, 0, "dominion:taskmaster", null);
            f.npc(0, 1, -16, 0, 1, "dominion:ashborn", null);
            f.npc(-8, 1, 16, 0, -1, "dominion:gnawling", null);
        }
        shacklePost(f, 0, 0, freed, s);
    }

    private static void spawningPit(Frame f, long h, boolean freed) {
        // Where the Ashborn are made: a lava pit ringed with black stone, cages of fresh-spawned, a priest's altar.
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 9.4) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                if (d > 7.5) { for (int y = g + 1; y <= g + 2; y++) f.c.cinder(wx, y, wz); continue; }
                int depth = (int) Math.round((7.5 - d) * 0.9) + 1;
                f.c.clear(wx, wz, g - depth + 1, g + 3);
                f.c.set(wx, g - depth, wz, freed ? OBSIDIAN : d < 4 ? LAVA : SOUL_SAND);
            }
        if (!freed) {
            f.spawner(0, -4, 0, "ZOMBIE");
            f.npc(10, dy(f, 10, 0) + 1, 0, -1, 0, "dominion:priest", null);
            f.sign(0, dy(f, 0, -11) + 1, -11, 0, -1, "FROM ASH\nAND WEEPING\nTHE HEART\nMAKES SONS");
        }
    }

    private static void ashFort(Frame f, long h, boolean freed, Plans.Site s) {
        // A square fort: black walls with spiked towers, a keep with a hall, a prison below, a gate with bars.
        Sites.pad(f, 27, 1);
        int w = 22;
        for (int x = -w; x <= w; x++)
            for (int z = -w; z <= w; z++) {
                boolean wall = Math.max(Math.abs(x), Math.abs(z)) >= w - 1;
                if (!wall) continue;
                if (Math.abs(x) <= 1 && z <= -w + 1) { f.set(x, 5, z, freed ? AIR : BARS); f.cinder(x, 6, z); f.cinder(x, 7, z); continue; }
                for (int y = 1; y <= 7; y++) f.cinder(x, y, z);
                if (Math.max(Math.abs(x), Math.abs(z)) == w && Math.floorMod(x + z, 2) == 0) f.set(x, 8, z, NETHER_FENCE);
            }
        for (int[] c : new int[][] {{-w, -w}, {w, -w}, {-w, w}, {w, w}})
            for (int x = c[0] - 3; x <= c[0] + 3; x++) for (int z = c[1] - 3; z <= c[1] + 3; z++) {
                for (int y = 1; y <= 12; y++) f.cinder(x, y, z);
                if ((Math.abs(x - c[0]) == 3 || Math.abs(z - c[1]) == 3) && Math.floorMod(x + z, 2) == 0) { f.set(x, 13, z, NETHER_FENCE); f.set(x, 14, z, BARS); }
            }
        // The keep.
        Frame k = new Frame(f.c, f.wx(0, 6), f.wz(0, 6), f.base, f.rot);
        Build.room(k, -9, -7, 9, 7, 0, 7, 2);
        Build.flatRoof(k, -9, -7, 9, 7, 8, 2);
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) k.set(x, y, -7, AIR);
        if (!freed) {
            k.set(0, 1, 5, OBSIDIAN); k.set(0, 2, 5, OBSIDIAN); Build.seat(k, 0, 3, 5, 0, -1, NETHER_STAIRS);
            for (int x = -6; x <= 6; x += 4) Build.brazier(k, x, 1, 4);
            for (int x = -7; x <= 7; x += 2) { Build.table(k, x, 1, -2, false); Build.seat(k, x, 1, -3, 0, 1, DARK_OAK_STAIRS); }
            k.npc(0, 1, 3, 0, -1, "dominion:blackshield", null);
            k.npc(-4, 1, 0, 1, 0, "dominion:ashborn", null);
            k.npc(4, 1, 0, -1, 0, "dominion:ashborn", null);
            k.chest(8, 1, 6, -1, 0, "atlas:fort_hoard", "book:orders");
            k.banner(0, 5, 6, 0, -1, true, "black_ash");
            // The prison under the keep, with a named captive if the fort holds one.
            for (int x = -8; x <= 8; x++) for (int z = -6; z <= 6; z++) { k.set(x, -4, z, CONCRETE, BLACK); for (int y = -3; y <= -1; y++) k.set(x, y, z, Math.abs(x) == 8 || Math.abs(z) == 6 ? NETHER_BRICK : AIR); }
            for (int x = -6; x <= 6; x += 4) for (int y = -3; y <= -1; y++) { k.set(x, y, -2, BARS); k.set(x, y, 2, BARS); }
            for (int y = -3; y <= 0; y++) k.set(8, y, 0, LADDER, k.facing(-1, 0));
            k.set(7, 0, 0, AIR);
            k.npc(-4, -3, 4, 0, -1, "bound:laborer", null);
            k.npc(4, -3, 4, 0, -1, "bound:laborer", null);
            shacklePost(f, 0, -12, false, s);
        } else {
            k.npc(0, 1, 0, 0, -1, "citizen:hoplite", null);
            shacklePost(f, 0, -12, true, s);
        }
    }

    // ------------------------------------------------------------------ the Scorched Forges

    private static void quarry(Frame f, long h, boolean freed, Plans.Site s) {
        // An open quarry: terraced black rock, ladders and ramps, carts on rails, a crane, and the Bound at the faces.
        for (int x = -24; x <= 24; x++)
            for (int z = -24; z <= 24; z++) {
                double d = Math.max(Math.abs(x), Math.abs(z));
                if (d > 24) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                int floor = f.base - (int) Math.min(12, Math.max(0, (22 - d) / 2) * 2);
                f.c.clear(wx, wz, floor + 1, Math.max(g, f.base) + 3);
                f.c.set(wx, floor, wz, (int) d % 4 == 0 ? GRAVEL : STONE, 0);
            }
        for (int z = -20; z <= 20; z++) f.set(0, -11, z, RAIL);
        for (int y = -11; y <= 0; y++) f.set(22, y, 0, LADDER, f.facing(-1, 0));
        if (!freed) {
            for (int k = 0; k < 4; k++) f.npc(-10 + k * 6, -11 + 1, 8, 0, 1, "bound:laborer", null);
            f.npc(0, -10, -6, 0, 1, "dominion:taskmaster", null);
            f.npc(10, -10, -10, 0, 1, "dominion:troll", null);
            shacklePost(f, -6, -4, false, s);
            f.sign(20, 1, -22, 0, -1, Texts.tally(h));
        } else {
            shacklePost(f, -6, -4, true, s);
            f.npc(0, -10, 0, 0, -1, "citizen:settler", null);
        }
    }

    private static void forgeWorks(Frame f, long h, boolean freed, Plans.Site s) {
        // A foundry hall: a long black shed over a lava channel, rows of furnaces and anvils, a crucible, chimneys.
        Sites.pad(f, 25, 1);
        Frame hall = f;
        Build.room(hall, -18, -9, 18, 9, 0, 9, 2);
        Build.gable(hall, -18, -9, 18, 9, 10, false, 2);
        for (int y = 1; y <= 4; y++) for (int x = -2; x <= 2; x++) hall.set(x, y, -9, AIR);
        for (int x = -16; x <= 16; x++) { hall.set(x, 0, 0, freed ? OBSIDIAN : LAVA); hall.set(x, -1, 0, NETHER_BRICK); }
        for (int x = -15; x <= 15; x += 3) { hall.set(x, 1, -6, FURNACE, hall.facing(0, 1)); hall.set(x, 1, 6, ANVIL, 0); }
        for (int[] c : new int[][] {{-14, 12}, {14, 12}})
            for (int x = c[0] - 1; x <= c[0] + 1; x++) for (int z = c[1] - 1; z <= c[1] + 1; z++) for (int y = 1; y <= 26; y++) hall.set(x, y, z, x == c[0] && z == c[1] && y > 1 ? AIR : BRICK_BLOCK);
        if (!freed) {
            for (int k = 0; k < 5; k++) hall.npc(-12 + k * 6, 1, -4, 0, 1, "bound:laborer", null);
            hall.npc(0, 1, 4, 0, -1, "dominion:taskmaster", null);
            hall.npc(-10, 1, 4, 0, -1, "dominion:emberkin", null);
            hall.chest(17, 1, 8, -1, 0, "atlas:forge", "book:requisitions");
            shacklePost(f, 0, -14, false, s);
        } else shacklePost(f, 0, -14, true, s);
    }

    // ------------------------------------------------------------------ the Petrified Weald

    private static void stillingHouse(Frame f, long h, boolean freed, Plans.Site s) {
        // A house of stilling: a black-marble hall where the sick are kept from dying, each in a glass cell by a font.
        Sites.pad(f, 19, 1);
        Build.room(f, -14, -10, 14, 10, 0, 8, 2);
        Build.gable(f, -14, -10, 14, 10, 9, false, 2);
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) f.set(x, y, -10, AIR);
        for (int k = -2; k <= 2; k++) {
            int x = k * 5;
            // A glass cell: side walls and a front, with a slit in the front through which the stilled can be spoken to.
            for (int dx = -2; dx <= 2; dx++)
                for (int z = 3; z <= 8; z++)
                    for (int y = 1; y <= 3; y++)
                        if ((Math.abs(dx) == 2 || z == 3) && !(z == 3 && dx == 0 && y <= 2)) f.set(x + dx, y, z, STAINED_PANE, BLACK);
            f.set(x, 1, 6, freed ? AIR : BREWING);
            if (!freed) f.npc(x, 1, 7, 0, -1, "bound:stilled", null);
            else f.bed(x, 1, 7, 0, -1, WHITE);
        }
        f.set(0, 1, -4, freed ? WATER : CONCRETE, BLACK);
        if (!freed) {
            f.npc(0, 1, -2, 0, 1, "dominion:priest", null);
            f.npc(6, 1, -6, 0, 1, "dominion:ashborn", null);
            f.chest(-12, 1, -8, 1, 0, "atlas:stilling", "book:stilling");
            shacklePost(f, 0, -15, false, s);
        } else { shacklePost(f, 0, -15, true, s); f.npc(0, 1, -2, 0, 1, "citizen:healer", null); }
    }

    private static void stoneGrove(Frame f, long h, boolean freed) {
        for (int k = 0; k < 14; k++) {
            double a = Hash.unit(Hash.of(h, k, 1)) * Math.PI * 2, r = 4 + Hash.unit(Hash.of(h, k, 2)) * 22;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (freed && k % 3 == 0) Build.fruitTree(f, x, z); else Build.stoneTree(f, x, z, k % 4 == 0 ? 2 : k % 2);
        }
        // At the grove's heart, a stilled giant: a Talos turned to stone mid-stride.
        for (int y = 1; y <= 6; y++) { Build.onGround(f, -1, 0, y, STONE, y < 4 ? 0 : 5); Build.onGround(f, 1, 0, y, STONE, y < 4 ? 0 : 5); }
        for (int y = 7; y <= 11; y++) for (int x = -2; x <= 2; x++) Build.onGround(f, x, 0, y, STONE, 6);
        Build.onGround(f, 0, 0, 12, freed ? SEA_LANTERN : STONE, 0);
        f.sign(0, dy(f, 0, -2) + 1, -2, 0, -1, freed ? "TALOS OF THE\nWEALD. HE\nMAY WAKE\nONE DAY" : "EVEN IRON\nKEEPS STILL\nFOR THE\nMOTHER");
    }

    // ------------------------------------------------------------------ the Fallen Cities

    private static void ruinedPolis(Frame f, long h, boolean freed) {
        // A quarter of a conquered city: a grid of ruined and occupied houses round a defaced agora.
        Sites.pad(f, 31, 2);
        for (int a = -31; a <= 31; a++) for (int b = -2; b <= 2; b++) { f.pave(a, 0, b); f.pave(b, 0, a); }
        int n = 0;
        for (int qx = -1; qx <= 1; qx += 2)
            for (int qz = -1; qz <= 1; qz += 2) {
                Frame c = new Frame(f.c, f.wx(qx * 16, qz * 16), f.wz(qx * 16, qz * 16), f.base, (f.rot + (qz < 0 ? 2 : 0)) & 3);
                String kind = n == 0 ? "burnt_library" : (Hash.unit(Hash.of(h, n, 4)) < 0.4 ? "occupied_house" : "ruined_house");
                CellsDominion.draw(null, c, new Plans.Cell(0, 0, 0, 0, f.base, 0, Hash.of(h, n, 5), Realm.Zone.FALLEN, kind, false), freed);
                n++;
            }
        Frame mid = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), f.base, f.rot);
        CellsDominion.draw(null, mid, new Plans.Cell(0, 0, 0, 0, f.base, 0, h ^ 9, Realm.Zone.FALLEN, freed ? "ruined_shrine" : "defaced_statue", false), freed);
    }

    private static void edictSquare(Frame f, long h, boolean freed, Plans.Site s) {
        Sites.pad(f, 21, 2);
        CellsDominion.obelisk(f, h, freed, Texts.edict(h));
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            Frame c = new Frame(f.c, f.wx((int) Math.round(Math.cos(a) * 13), (int) Math.round(Math.sin(a) * 13)), f.wz((int) Math.round(Math.cos(a) * 13), (int) Math.round(Math.sin(a) * 13)), f.base, (f.rot + k) & 3);
            CellsDominion.draw(null, c, new Plans.Cell(0, 0, 0, 0, f.base, 0, Hash.of(h, k, 6), Realm.Zone.FALLEN, k % 2 == 0 ? "citizen_pen" : "gallows", false), freed);
        }
        if (!freed) shacklePost(f, 0, -8, false, s); else shacklePost(f, 0, -8, true, s);
    }

    // ------------------------------------------------------------------ everywhere

    private static void shrineOfAsh(Frame f, long h, boolean freed) {
        // A shrine of the Cinder Heart: a black altar under a spiked canopy, a basin of embers, a priest and his flock.
        Sites.pad(f, 15, 1);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) f.set(x, 0, z, CONCRETE, BLACK);
        for (int[] p : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) { for (int y = 1; y <= 7; y++) f.cinder(p[0], y, p[1]); f.set(p[0], 8, p[1], NETHER_FENCE); f.set(p[0], 9, p[1], BARS); }
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) if (Math.abs(x) + Math.abs(z) <= 7) f.set(x, 8, z, SLAB, 6 | 8);
        f.fill(-1, 1, 2, 1, 1, 3, OBSIDIAN, 0);
        f.set(0, 2, 3, freed ? SEA_LANTERN : MAGMA);
        f.sign(0, 1, 1, 0, -1, freed ? "THE LAMP\nHAS COME\nEAST AGAIN" : Texts.dominionSlogan(h));
        if (!freed) {
            f.npc(0, 1, 0, 0, -1, "dominion:priest", null);
            for (int k = -1; k <= 1; k += 2) f.npc(k * 3, 1, -3, 0, 1, "dominion:ashborn", null);
            f.chest(4, 1, 4, -1, 0, "atlas:priest_cache", "book:litany");
        }
    }

    private static void roadCamp(Frame f, long h, boolean freed, Plans.Site s) {
        // A way-camp on the slave roads: a coffle of the Bound resting under guard, a cart, a signal pole.
        Sites.pad(f, 18, 1);
        CellsConcord.tent(f, -8, -6, freed ? GRAY : BLACK);
        CellsConcord.tent(f, 8, -6, freed ? GRAY : BLACK);
        f.fill(-2, 1, 6, 2, 1, 8, WOOD_SLAB, 2 | 8);
        f.chest(0, 2, 7, 0, -1, freed ? "atlas:wagon" : "atlas:dominion_supplies", null);
        if (!freed) {
            for (int k = 0; k < 4; k++) { f.set(-6 + k * 4, 1, 12, NETHER_FENCE); f.npc(-6 + k * 4, 1, 13, 0, -1, "bound:laborer", null); }
            f.npc(0, 1, 3, 0, 1, "dominion:ashborn", null);
            f.npc(4, 1, 3, 0, 1, "dominion:taskmaster", null);
            if (Hash.unit(h ^ 5) < 0.5) f.npc(-4, 1, 3, 0, 1, "dominion:warg", null);
            shacklePost(f, 0, 10, false, s);
        } else shacklePost(f, 0, 10, true, s);
        for (int y = 1; y <= 8; y++) f.set(12, y, 12, freed ? FENCE : NETHER_FENCE);
        if (!freed) { f.set(12, 9, 12, NETHERRACK); f.set(12, 10, 12, FIRE); }
    }
}
