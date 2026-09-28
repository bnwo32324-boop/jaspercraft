package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Dominion's strongholds. Each has its master's arena (a "boss:" marker), its captives ("captive:" markers, named
 * people who can be rescued) and, for the Ash-Crowned, the mechanism that Concord knowledge breaks ("mech:" markers):
 * Melaina's three Stilling Fonts, Daidaros's three Governors (low, middle, high) and Keleos's three Edict Stones. Every
 * stronghold has a liberated form: gates open, fires out, chains gone.
 */
final class Strongholds {
    private Strongholds() {}

    static int dy(Frame f, int x, int z) { return CellsConcord.dy(f, x, z); }

    // ------------------------------------------------------------------ the Ashen Marches

    /**
     * The Pylon of Teeth, Kallias's black gate in the Teeth: two spiked towers either side of a portcullis of ash-iron,
     * and before it, on the Marches side, the Marshal's bailey: a parade ground where he holds court and fights, war
     * drums, captive pens and barracks. The gate opens when he falls.
     */
    static void pylon(Plans plans, Frame f, boolean freed) {
        // Local x: 0 at the Teeth's inner face (world x 260), negative outward (the Marches side).
        Places.level(f, 34, 1);
        int wallIn = 0, wallOut = -7;
        // The gate towers.
        for (int side = -1; side <= 1; side += 2) {
            int tz = side * 17, tx = -4;
            for (int x = tx - 9; x <= tx + 9; x++)
                for (int z = tz - 9; z <= tz + 9; z++) {
                    double d = Math.sqrt((x - tx) * (x - tx) + (z - tz) * (z - tz));
                    if (d > 9.4) continue;
                    boolean shell = d > 8.2;
                    f.footing(x, z, 0, false);
                    for (int y = 0; y <= 66; y++) {
                        if (shell) { if (y % 11 == 7 && (x == tx || z == tz)) f.set(x, y, z, BARS); else f.cinder(x, y, z); }
                        else f.set(x, y, z, y % 11 == 0 ? CONCRETE : AIR, BLACK);
                    }
                    if (shell) { int s = 2 + (int) (f.roll(x, 66, z, 141) * 7); for (int y = 1; y <= s; y++) f.set(x, 66 + y, z, y == s ? BARS : NETHER_FENCE); }
                    else if (d < 2 && !freed) { f.set(x, 67, z, NETHERRACK); f.set(x, 68, z, FIRE); }
                }
            for (int y = 1; y < 66; y++) f.set(tx + 7, y, tz, LADDER, f.facing(-1, 0));
            for (int y = 1; y <= 3; y++) f.set(tx, y, tz - side * 9, AIR);
            if (!freed) f.npc(tx + 3, 1, tz, 0, 1, "dominion:blackshield", null);
        }
        // The gate: the Teeth's opening, closed by a portcullis of ash-iron while the Marshal lives.
        for (int x = wallOut; x <= wallIn; x++)
            for (int z = -8; z <= 8; z++)
                for (int y = 0; y <= 36; y++) {
                    if (y <= 24 && Math.abs(z) <= 7) {
                        if (y == 0) f.blackRoad(x, 0, z);
                        else if (!freed && (x == wallOut + 2 || x == wallOut + 4) && (Math.floorMod(z, 2) == 0 || y % 4 == 0)) f.set(x, y, z, BARS);
                        else f.set(x, y, z, AIR);
                    } else f.cinder(x, y, z);
                }
        f.sign(wallOut - 1, 26, 0, -1, 0, freed ? "THE PYLON\nSTANDS OPEN\nBY THE FALL\nOF KALLIAS" : "THE PYLON\nOF TEETH\nNONE PASS\nUNBIDDEN");
        // The Marshal's bailey, west of the gate.
        int bx0 = -34, bx1 = -9;
        for (int x = bx0; x <= bx1; x++)
            for (int z = -30; z <= 30; z++) {
                boolean wall = x == bx0 || Math.abs(z) == 30;
                if (!wall) continue;
                if (x == bx0 && Math.abs(z) <= 3) continue;   // the bailey gate on the Royal Road
                for (int y = 1; y <= 10; y++) f.cinder(x, y, z);
                if (Math.floorMod(x + z, 2) == 0) { f.set(x, 11, z, NETHER_FENCE); f.set(x, 12, z, BARS); }
            }
        // The parade ground (the arena), ringed with standards.
        for (int x = -30; x <= -12; x++) for (int z = -16; z <= 16; z++) f.set(x, 0, z, CONCRETE, freed ? GRAY : BLACK);
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10;
            int x = -21 + (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 14);
            for (int y = 1; y <= 6; y++) f.set(x, y, z, freed ? FENCE : NETHER_FENCE);
            if (!freed) f.banner(x, 7, z, 1, 0, false, "black_ash");
        }
        if (!freed) {
            f.npc(-21, 1, 0, 1, 0, "boss:kallias", null);
            // War drums at the corners of the ground.
            for (int[] p : new int[][] {{-29, -15}, {-29, 15}, {-13, -15}, {-13, 15}}) { f.set(p[0], 1, p[1], NOTE); f.set(p[0], 2, p[1], CARPET, BROWN); }
        } else {
            // Freed: the parade ground keeps a heliodrome, woken, and a hoplite of the Line.
            f.npc(-21, 1, 9, 1, 0, "citizen:hoplite", null);
            Landmarks.heliodrome(new Frame(f.c, f.wx(-21, 0), f.wz(-21, 0), f.base, f.rot), "pylon", "THE PYLON");
        }
        // Barracks and pens along the bailey walls.
        for (int k = 0; k < 3; k++) {
            Frame b = new Frame(f.c, f.wx(-26 + k * 8, -24), f.wz(-26 + k * 8, -24), f.base, (f.rot + 2) & 3);
            CellsDominion.draw(null, b, new Plans.Cell(0, 0, 0, 0, f.base, 0, 77L + k, Realm.Zone.MARCHES, k == 1 ? "supply_dump" : "tents", false), freed);
        }
        for (int x = -30; x <= -14; x++) for (int z = 20; z <= 28; z++) if (x == -30 || x == -14 || z == 20 || z == 28) f.set(x, 1, z, freed ? AIR : BARS);
        if (!freed) {
            f.npc(-26, 1, 24, 0, -1, "captive:doros", null);
            f.npc(-19, 1, 24, 0, -1, "bound:laborer", null);
            f.npc(-22, 1, 18, 0, 1, "dominion:taskmaster", null);
        }
        // The Marshal's pavilion: his table, his orders, and the Oath he no longer reads.
        Frame m = new Frame(f.c, f.wx(-14, -12), f.wz(-14, -12), f.base, f.rot);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) { int y = 4 - Math.max(Math.abs(x), 0) / 2; m.set(x, y, z, WOOL, freed ? WHITE : RED); }
        Build.table(m, 0, 1, 0, true);
        m.chest(1, 1, 1, -1, 0, freed ? "atlas:household" : "atlas:marshal", freed ? null : "book:marshal");
    }

    /** Gorvash's war camp: the largest camp in the Marches, its war-chief's pit-arena and his trophy tent. */
    static void warcamp(Plans plans, Frame f, boolean freed) {
        Places.level(f, 40, 1);
        Plans.Site s = new Plans.Site(Plans.SiteKind.WAR_CAMP, f.ox, f.oz, f.base, 0, 9001, 9001, 0x60F7A5L, "Gorvash's war camp");
        SitesDominion.draw(plans, f, s, freed);
        // The fighting pit.
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 10.4) continue;
                Frame p = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), f.base, f.rot);
                p.clear(x, z, -3, 0);
                p.set(x, -4, z, d > 9.4 ? NETHER_BRICK : GRAVEL);
                if (d > 9.4) for (int y = -3; y <= 1; y++) p.cinder(x, y, z);
            }
        if (!freed) {
            f.npc(0, -3, 0, 0, -1, "boss:gorvash", null);
            f.npc(-6, -3, 6, 0, -1, "captive:tamsa", null);
        }
    }

    /** The Rider's Tower: the Crownless Rider's black spire; he rides the Marches' roads by night and returns here. */
    static void riderTower(Plans plans, Frame f, boolean freed) {
        Places.level(f, 22, 1);
        int r = 7, top = 60;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.4) continue;
                boolean shell = d > r - 0.8;
                f.footing(x, z, 0, false);
                for (int y = 0; y <= top; y++) {
                    int tr = y > top - 12 ? r - (y - (top - 12)) / 3 : r;
                    if (d > tr + 0.4) break;
                    if (shell || d > tr - 0.8) f.cinder(x, y, z); else f.set(x, y, z, y % 12 == 0 ? CONCRETE : AIR, BLACK);
                }
            }
        for (int y = 1; y < top - 12; y++) f.set(0, y, r - 1, LADDER, f.facing(0, -1));
        for (int y = 1; y <= 3; y++) f.set(0, y, -r, AIR);
        // The stable below (black horses) and the Rider's chamber at the top.
        if (!freed) {
            f.npc(-3, 1, 0, 1, 0, "dominion:black_horse", null);
            f.npc(3, 1, 0, -1, 0, "dominion:black_horse", null);
            f.npc(0, 49, 0, 0, -1, "boss:crownless", null);
            f.chest(2, 49, 2, -1, 0, "atlas:rider", "book:rider");
            f.npc(-2, 13, 2, 0, -1, "captive:philippos", null);
            for (int x = -3; x <= 3; x++) for (int y = 13; y <= 15; y++) f.set(x, y, 0, BARS);
        }
    }

    // ------------------------------------------------------------------ the Petrified Weald

    /**
     * The Stilled Garden: Melaina's Asklepieion of stillness, a black-marble house of healing turned mausoleum, in a
     * garden of stone trees. Its court holds her couch between three Stilling Fonts: while any font runs, nothing near
     * it can die. Ward halls either side keep the stilled; the Hymn of Passage silences the fonts.
     */
    static void stilledGarden(Plans plans, Frame f, boolean freed) {
        Places.level(f, 40, 1);
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24, r = 30 + (k % 3) * 3;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (freed && k % 2 == 0) Build.fruitTree(f, x, z); else Build.stoneTree(f, x, z, k % 3);
        }
        // The court: a black marble floor, a peristyle of dark columns, the fonts in a triangle.
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) { f.set(x, 0, z, freed ? QUARTZ : CONCRETE, freed ? 0 : BLACK); f.clear(x, z, 1, 12); }
        for (int x = -20; x <= 20; x += 4) for (int z = -20; z <= 20; z += 40) { for (int y = 1; y <= 8; y++) f.set(x, y, z, freed ? QUARTZ : STONE, freed ? 2 : 6); }
        for (int z = -20; z <= 20; z += 4) for (int x = -20; x <= 20; x += 40) { for (int y = 1; y <= 8; y++) f.set(x, y, z, freed ? QUARTZ : STONE, freed ? 2 : 6); }
        for (int k = 0; k < 3; k++) {
            double a = Math.PI * 2 * k / 3 - Math.PI / 2;
            int x = (int) Math.round(Math.cos(a) * 11), z = (int) Math.round(Math.sin(a) * 11);
            for (int xx = x - 2; xx <= x + 2; xx++) for (int zz = z - 2; zz <= z + 2; zz++) f.set(xx, 1, zz, Math.abs(xx - x) == 2 || Math.abs(zz - z) == 2 ? STONE : freed ? WATER : CONCRETE, Math.abs(xx - x) == 2 || Math.abs(zz - z) == 2 ? 6 : BLACK);
            f.set(x, 2, z, freed ? FLOWER_POT : BREWING);
            if (!freed) f.npc(x, 2, z, 0, -1, "mech:font:" + k, null);
        }
        // Her couch at the centre.
        f.fill(-2, 1, -1, 2, 1, 1, freed ? WOOL : CONCRETE, freed ? WHITE : GRAY);
        if (!freed) f.npc(0, 2, 0, 0, -1, "boss:melaina", null);
        // The ward halls east and west, where the stilled lie in glass.
        for (int side = -1; side <= 1; side += 2) {
            Frame w = new Frame(f.c, f.wx(side * 30, 0), f.wz(side * 30, 0), f.base, (f.rot + (side < 0 ? 1 : 3)) & 3);
            Build.room(w, -12, -6, 12, 6, 0, 7, freed ? 0 : 2);
            Build.gable(w, -12, -6, 12, 6, 8, false, freed ? 0 : 2);
            for (int y = 1; y <= 3; y++) w.set(0, y, -6, AIR);
            for (int x = -10; x <= 10; x += 4) {
                for (int y = 1; y <= 3; y++) { w.set(x - 1, y, 4, STAINED_PANE, freed ? WHITE : BLACK); w.set(x + 1, y, 4, STAINED_PANE, freed ? WHITE : BLACK); w.set(x, y, 3, STAINED_PANE, freed ? WHITE : BLACK); }
                if (freed) w.bed(x, 1, 5, 0, -1, WHITE);
                else w.npc(x, 1, 5, 0, -1, "bound:stilled", null);
            }
            if (!freed) {
                w.npc(0, 1, -2, 0, 1, side < 0 ? "captive:chrysa" : "captive:agapios", null);
                w.npc(6, 1, -2, 0, 1, "dominion:priest", null);
                w.chest(-10, 1, -4, 1, 0, "atlas:stilling", "book:melaina");
            }
        }
    }

    /** Mother Sallow's house of stilling: a lesser house run by the Stiller's high nurse. */
    static void sallowHouse(Plans plans, Frame f, boolean freed) {
        Places.level(f, 22, 1);
        Plans.Site s = new Plans.Site(Plans.SiteKind.STILLING_HOUSE, f.ox, f.oz, f.base, 0, 9002, 9002, 0x5A110AL, "Mother Sallow's house");
        SitesDominion.draw(plans, f, s, freed);
        if (!freed) {
            f.npc(0, 1, 2, 0, -1, "boss:sallow", null);
            f.npc(-10, 1, -5, 1, 0, "captive:myrrhine", null);
        }
    }

    // ------------------------------------------------------------------ the Scorched Forges

    /**
     * The Great Engine: Daidaros's foundry-fortress. A walled yard of furnaces and slave halls round the Engine itself, a
     * black tower over a lava pit. Its three Governors, resonance crystals that keep the Engine singing (and shield its
     * master), stand low in the pit, midway on the engine floor, and high at the tower's crown. Silence them in the
     * order the Counterpoint gives (deep, middle, high) or the Engine sings itself back to life.
     */
    static void greatEngine(Plans plans, Frame f, boolean freed) {
        Places.level(f, 42, 3);
        // The yard wall.
        for (int x = -40; x <= 40; x++)
            for (int z = -40; z <= 40; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 40) continue;
                if (Math.abs(x) <= 3 && z == -40) continue;
                for (int y = 1; y <= 9; y++) f.set(x, y, z, y % 4 == 0 ? IRON_TRAPDOOR : NETHER_BRICK, y % 4 == 0 ? 8 : 0);
            }
        // Slave halls round the yard.
        for (int k = 0; k < 4; k++) {
            int x = k < 2 ? -26 : 26, z = k % 2 == 0 ? -24 : 24;
            Plans.Site s = new Plans.Site(Plans.SiteKind.FORGE_WORKS, f.wx(x, z), f.wz(x, z), f.base, 0, 9100 + k, 9100, Hash.of(0x6E61L, k, 0), "the Engine's foundry " + (k + 1));
            Frame hall = new Frame(f.c, f.wx(x, z), f.wz(x, z), f.base, (f.rot + (k % 2 == 0 ? 0 : 2)) & 3);
            if (hall.touches(-20, -12, 20, 12)) SitesDominion.draw(plans, hall, s, freed);
        }
        // The pit and the Engine tower.
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 12.4) continue;
                f.clear(x, z, -8, 0);
                f.set(x, -9, z, freed ? OBSIDIAN : d < 6 ? LAVA : NETHER_BRICK);
                if (d > 11.4) for (int y = -8; y <= 0; y++) f.set(x, y, z, NETHER_BRICK);
            }
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4;
                for (int y = -8; y <= 30; y++) {
                    if (shell) { if (y > 0 && y % 6 == 3 && (x == 0 || z == 0)) f.set(x, y, z, BARS); else f.set(x, y, z, (y & 1) == 0 ? NETHER_BRICK : CONCRETE, BLACK); }
                    else f.set(x, y, z, y == 6 || y == 18 ? NETHER_BRICK : AIR);
                }
            }
        for (int y = -8; y <= 30; y++) f.set(3, y, 3, LADDER, f.facing(-1, 0));
        for (int y = 1; y <= 3; y++) f.set(0, y, -4, AIR);
        // The Governors: deep in the pit, midway on the engine floor, high at the crown.
        f.set(-8, -8, 0, SEA_LANTERN); f.set(-8, -7, 0, freed ? AIR : END_ROD, 1);
        f.set(0, 7, 0, SEA_LANTERN); f.set(0, 8, 0, freed ? AIR : END_ROD, 1);
        f.set(0, 31, 0, SEA_LANTERN); f.set(0, 32, 0, freed ? AIR : END_ROD, 1);
        if (!freed) {
            f.npc(-8, -7, 1, 0, -1, "mech:governor:0", null);
            f.npc(0, 8, 1, 0, -1, "mech:governor:1", null);
            f.npc(0, 32, 1, 0, -1, "mech:governor:2", null);
            f.npc(0, 1, -8, 0, -1, "boss:daidaros", null);
            f.npc(20, 1, -8, 0, -1, "captive:ktesias", null);
            f.npc(-20, 1, 8, 0, -1, "captive:nausikaa", null);
            f.chest(2, 19, -2, -1, 0, "atlas:engine", "book:daidaros_late");
        }
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (Math.max(Math.abs(x), Math.abs(z)) == 6 && Math.floorMod(x + z, 2) == 0) { f.set(x, 31, z, NETHER_FENCE); f.set(x, 32, z, BARS); }
        f.sign(0, 3, -13, 0, -1, freed ? "THE ENGINE\nIS STILL.\nLET HANDS\nREST." : "THE ENGINE\nNEVER STOPS.\nNOR SHALL\nYOU.");
    }

    /** The Slag Quarry: a great quarry pit worked by chained troll-gangs, and Grunnak the Slag-Troll's lair. */
    static void slagQuarry(Plans plans, Frame f, boolean freed) {
        Plans.Site s = new Plans.Site(Plans.SiteKind.QUARRY, f.ox, f.oz, f.base, 0, 9003, 9003, 0x51A6L, "the Slag Quarry");
        SitesDominion.draw(plans, f, s, freed);
        if (!freed) { f.npc(0, -10, 10, 0, -1, "boss:grunnak", null); f.npc(-14, -10, 14, 0, -1, "captive:lykon", null); }
    }

    /** The Pit of the Hollow Fiend: a shaft into the fire where the Forgemaster keeps his demon. */
    static void fiendPit(Plans plans, Frame f, boolean freed) {
        Places.level(f, 20, 3);
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 12.4) continue;
                int depth = (int) Math.round((12.4 - d) * 1.4);
                f.clear(x, z, -depth, 0);
                f.set(x, -depth - 1, z, d < 4 && !freed ? LAVA : NETHER_BRICK);
                if (d > 11.4) for (int y = 1; y <= 3; y++) f.set(x, y, z, NETHER_FENCE);
            }
        for (int k = 0; k < 17; k++) f.set(-12 + k, -k, 0, NETHER_STAIRS, f.stairs(-1, 0, false));
        if (!freed) f.npc(4, -16, 0, 0, -1, "boss:fiend", null);
    }

    // ------------------------------------------------------------------ the Fallen Cities

    /**
     * A conquered Asterian city: walls breached, streets under ash, houses ruined or taken by the Sworn, edict stones on
     * the corners, pens of silenced citizens. Pellene's agora holds the Hall of Edicts, Keleos's court, with three Edict
     * Stones before it; Aigai's council house is held by Kalchas the Informer-Magistrate.
     */
    static void fallenCity(Plans plans, Frame f, Realm.Place p, boolean freed) {
        int r = p.radius;
        long h = Hash.of(0xFA11L, p.x, p.z);
        Places.level(f, r, 2);
        // Broken walls.
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                int t = f.roll(x, 0, z, 151) < 0.25 ? 0 : 2 + (int) (f.roll(x, 1, z, 152) * 8);
                for (int y = 1; y <= t; y++) f.set(x, y, z, f.roll(x, y, z, 153) < 0.6 ? QUARTZ : CONCRETE, f.roll(x, y, z, 153) < 0.6 ? 0 : GRAY);
            }
        // Streets and ash.
        for (int x = -r + 1; x < r; x++) for (int z = -r + 1; z < r; z++) if (Math.floorMod(x + 11, 22) < 4 || Math.floorMod(z + 11, 22) < 4) { if (!freed && f.roll(x, 0, z, 154) < 0.35) f.ash(x, 0, z); else f.pave(x, 0, z); }
        // Blocks: ruined and occupied houses, edict steles, pens and gallows.
        String[] kinds = {"ruined_house", "occupied_house", "ruined_house", "burnt_library", "citizen_pen", "gallows", "edict_stele", "defaced_statue", "ruined_shrine", "ash_garden", "occupied_house"};
        int n = Math.floorDiv(r - 4, 22);
        for (int i = -n; i <= n; i++)
            for (int j = -n; j <= n; j++) {
                if (Math.abs(i) <= 1 && Math.abs(j) <= 1) continue;   // the agora quarter
                long bh = Hash.of(h, i, j);
                Frame b = new Frame(f.c, f.wx(i * 22 + 1, j * 22 + 1), f.wz(i * 22 + 1, j * 22 + 1), f.base, (int) (bh & 3));
                if (!b.touches(-10, -10, 10, 10)) continue;
                CellsDominion.draw(null, b, new Plans.Cell(0, 0, 0, 0, f.base, 0, bh, Realm.Zone.FALLEN, kinds[(int) Math.floorMod(bh >>> 5, kinds.length)], false), freed);
            }
        if (p == Realm.Place.PELLENE) hallOfEdicts(f, h, freed);
        else councilHouse(f, h, freed);
    }

    private static void hallOfEdicts(Frame f, long h, boolean freed) {
        // The agora of Pellene: the Hall of Edicts (the old basilica) with the three Edict Stones before it.
        for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) { f.set(x, 0, z, freed ? QUARTZ : CONCRETE, freed ? 0 : BLACK); f.clear(x, z, 1, 14); }
        Frame hall = new Frame(f.c, f.wx(0, 14), f.wz(0, 14), f.base, f.rot);
        Build.room(hall, -16, -10, 16, 10, 0, 11, freed ? 0 : 2);
        Build.gable(hall, -16, -10, 16, 10, 12, false, freed ? 1 : 2);
        for (int x = -16; x <= 16; x += 4) Build.column(hall, x, -11, 1, 10);
        for (int y = 1; y <= 5; y++) for (int x = -2; x <= 2; x++) hall.set(x, y, -10, AIR);
        // The Magistrate's bench, tiers of silent listeners, the ledgers of names.
        hall.fill(-4, 1, 6, 4, 2, 8, freed ? QUARTZ : OBSIDIAN, 0);
        for (int x = -12; x <= 12; x += 2) for (int z = -6; z <= 2; z += 2) Build.seat(hall, x, 1, z, 0, 1, freed ? BIRCH_STAIRS : DARK_OAK_STAIRS);
        if (!freed) {
            hall.npc(0, 3, 7, 0, -1, "boss:keleos", null);
            for (int k = 0; k < 4; k++) hall.npc(-9 + k * 6, 1, -2, 0, 1, "bound:citizen", null);
            hall.npc(12, 1, 6, -1, 0, "captive:erinna", null);
            hall.chest(-14, 1, 8, 1, 0, "atlas:edicts", "book:edicts");
            hall.banner(0, 8, 9, 0, -1, true, "black_ash");
        }
        for (int k = 0; k < 3; k++) {
            int x = -16 + k * 16;
            Frame s = new Frame(f.c, f.wx(x, -14), f.wz(x, -14), f.base, f.rot);
            CellsDominion.obelisk(s, h ^ k, freed, Texts.edict(h + k));
            if (!freed) s.npc(0, 1, -3, 0, -1, "mech:edict:" + k, null);
        }
        if (!freed) f.npc(20, 1, -20, 0, -1, "captive:ione", null);
    }

    private static void councilHouse(Frame f, long h, boolean freed) {
        // Aigai's council house, where Kalchas the Informer-Magistrate keeps the ledgers of who said what.
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) { f.pave(x, 0, z); f.clear(x, z, 1, 10); }
        Frame hall = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), f.base, f.rot);
        Build.room(hall, -12, -9, 12, 9, 0, 8, freed ? 0 : 2);
        Build.gable(hall, -12, -9, 12, 9, 9, false, freed ? 0 : 2);
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) hall.set(x, y, -9, AIR);
        for (int x = -10; x <= 10; x++) for (int y = 1; y <= 4; y++) if (x % 3 != 0) hall.set(x, y, 8, BOOKSHELF);
        if (!freed) {
            hall.npc(0, 1, 5, 0, -1, "boss:kalchas", null);
            hall.npc(-8, 1, 4, 1, 0, "captive:thersites", null);
            hall.chest(8, 1, 7, -1, 0, "atlas:ledgers", "book:ledger");
            hall.npc(6, 1, 0, 0, 1, "dominion:blackshield", null);
        }
    }
}
