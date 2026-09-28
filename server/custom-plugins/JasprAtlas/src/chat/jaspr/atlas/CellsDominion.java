package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Dominion's land, one 24-block cell at a time. Each province is made of different things; every cell knows its
 * liberated form too (fires out, guards and captives gone, the first flowers and settlers back).
 */
final class CellsDominion {
    private CellsDominion() {}

    static void draw(Plans plans, Frame f, Plans.Cell cell, boolean freed) {
        long h = cell.hash;
        switch (cell.kind) {
            // The Ashen Marches.
            case "ash_dunes": ashDunes(f, h, freed); break;
            case "impaling_field": impalingField(f, h, freed); break;
            case "tents": tents(f, h, freed); break;
            case "gibbets": gibbets(f, h, freed); break;
            case "signal_pyre": signalPyre(f, h, freed); break;
            case "burnt_village": burntVillage(f, h, freed); break;
            case "chain_line": chainLine(f, h, freed); break;
            case "midden": midden(f, h, freed); break;
            case "warg_kennel": wargKennel(f, h, freed); break;
            case "siege_park": siegePark(f, h, freed); break;
            case "supply_dump": supplyDump(f, h, freed); break;
            case "obelisk": obelisk(f, h, freed, Texts.dominionSlogan(h)); break;
            case "wart_farm": wartFarm(f, h, freed); break;
            case "war_drum": warDrum(f, h, freed); break;
            // The Petrified Weald.
            case "stone_trees": stoneTrees(f, h, freed); break;
            case "petrified_folk": petrifiedFolk(f, h, freed); break;
            case "still_pool": stillPool(f, h, freed); break;
            case "stone_stumps": stoneStumps(f, h, freed); break;
            case "hanging_cages": hangingCages(f, h, freed); break;
            case "silent_bell": silentBell(f, h, freed); break;
            case "font_stone": fontStone(f, h, freed); break;
            case "moth_shrine": mothShrine(f, h, freed); break;
            // The Scorched Forges.
            case "slag_heap": slagHeap(f, h, freed); break;
            case "lava_channel": lavaChannel(f, h, freed); break;
            case "chimney": chimney(f, h, freed); break;
            case "ore_carts": oreCarts(f, h, freed); break;
            case "furnace_bank": furnaceBank(f, h, freed); break;
            case "anvil_yard": anvilYard(f, h, freed); break;
            case "pipe_run": pipeRun(f, h, freed); break;
            case "crucible": crucible(f, h, freed); break;
            case "cooling_pool": coolingPool(f, h, freed); break;
            case "slave_mine": slaveMine(f, h, freed); break;
            // The Fallen Cities.
            case "ruined_house": ruinedHouse(f, h, freed, false); break;
            case "occupied_house": ruinedHouse(f, h, freed, true); break;
            case "burnt_library": burntLibrary(f, h, freed); break;
            case "gallows": gallows(f, h, freed); break;
            case "edict_stele": obelisk(f, h, freed, Texts.edict(h)); break;
            case "citizen_pen": citizenPen(f, h, freed); break;
            case "defaced_statue": defacedStatue(f, h, freed); break;
            case "ruined_shrine": ruinedShrine(f, h, freed); break;
            case "ash_garden": ashGarden(f, h, freed); break;
            // The Plateau of Cinders.
            case "fissure": fissure(f, h, freed); break;
            case "black_obelisk": obelisk(f, h, freed, Texts.dominionSlogan(h ^ 77)); break;
            case "spike_field": spikeField(f, h, freed); break;
            case "bone_pile": bonePile(f, h, freed); break;
            case "ash_vent": ashVent(f, h, freed); break;
            case "rubble": rubble(f, h, freed); break;
            default: ashDunes(f, h, freed);
        }
    }

    static int dy(Frame f, int x, int z) { return CellsConcord.dy(f, x, z); }

    /** A guard of the Dominion (only while the province is held). */
    static void guard(Frame f, int x, int z, boolean freed, String kind) {
        if (!freed) f.npc(x, dy(f, x, z) + 1, z, 0, -1, "dominion:" + kind, null);
    }

    /** A settler of the liberated land, in the place of what was there. */
    static void settler(Frame f, long h, int x, int z, boolean freed, String kind) {
        if (freed && f.pick(h, 99) < 0.5) f.npc(x, dy(f, x, z) + 1, z, 0, -1, "citizen:" + kind, null);
    }

    // ------------------------------------------------------------------ the Ashen Marches

    private static void ashDunes(Frame f, long h, boolean freed) {
        if (freed) { Build.flowers(f, -11, -11, 10, 10, 0.08); CellsWound.scatter(f, h, 4); return; }
        for (int k = 0; k < 3; k++) {
            int cx = (int) (Hash.unit(Hash.of(h, k, 11)) * 16) - 8, cz = (int) (Hash.unit(Hash.of(h, k, 12)) * 16) - 8, r = 3 + (int) (Hash.unit(Hash.of(h, k, 13)) * 3);
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    int hh = (int) Math.round((r - d) * 0.7);
                    for (int y = 1; y <= hh; y++) Build.onGround(f, x, z, y, POWDER, y == hh && f.roll(x, y, z, 97) < 0.5 ? BLACK : GRAY);
                }
        }
        CellsWound.scatter(f, h, 9);
    }

    private static void impalingField(Frame f, long h, boolean freed) {
        for (int x = -10; x <= 10; x += 3)
            for (int z = -10; z <= 10; z += 3) {
                if (f.roll(x, 0, z, 98) > 0.7) continue;
                if (freed) { if (f.roll(x, 1, z, 99) < 0.4) Build.onGround(f, x, z, 1, RED_FLOWER, 0); continue; }
                int t = 2 + (int) (f.roll(x, 2, z, 100) * 3);
                for (int y = 1; y <= t; y++) Build.onGround(f, x, z, y, NETHER_FENCE, 0);
                if (f.roll(x, 3, z, 101) < 0.6) Build.onGround(f, x, z, t + 1, SKULL, 1);
            }
        CellsWound.scatter(f, h, freed ? 2 : 6);
    }

    private static void tents(Frame f, long h, boolean freed) {
        // An Ashborn bivouac: black hide tents round a fire pit, spears in racks, sleeping mats of hay.
        for (int k = 0; k < 4; k++) {
            int x = k < 2 ? -7 : 7, z = k % 2 == 0 ? -6 : 6;
            if (freed) { Build.onGround(f, x, z, 1, CARPET, BLACK); continue; }
            CellsConcord.tent(f, x, z, k == 3 ? RED : BLACK);
        }
        if (!freed) {
            Build.onGround(f, 0, 0, 0, NETHERRACK, 0);
            Build.onGround(f, 0, 0, 1, FIRE, 0);
            for (int[] p : new int[][] {{2, 0}, {-2, 0}, {0, 2}}) Build.onGround(f, p[0], p[1], 1, HAY, 0);
            f.stand(0, dy(f, 0, -3) + 1, -3, 0, 1, "orc_rack");
            f.chest(3, dy(f, 3, 3) + 1, 3, -1, 0, "atlas:orc_stash", null);
            guard(f, 1, 1, false, "ashborn");
            guard(f, -1, 2, false, "ashborn");
            if (f.pick(h, 3) < 0.4) guard(f, 4, -2, false, "gnawling");
        } else settler(f, h, 0, 0, true, "settler");
    }

    private static void gibbets(Frame f, long h, boolean freed) {
        for (int k = -2; k <= 2; k++) {
            int x = k * 4;
            int g = dy(f, x, 0);
            for (int y = 1; y <= 7; y++) f.set(x, g + y, 0, freed ? FENCE : NETHER_FENCE);
            if (freed) continue;
            f.set(x, g + 7, 1, NETHER_FENCE); f.set(x, g + 7, 2, NETHER_FENCE);
            for (int y = 3; y <= 5; y++) { f.set(x - 1, g + y, 2, BARS); f.set(x + 1, g + y, 2, BARS); f.set(x, g + y, 3, BARS); f.set(x, g + y, 1, BARS); }
            f.set(x, g + 6, 2, BARS);
            f.set(x, g + 3, 2, BONE, 0);
            f.set(x, g + 4, 2, SKULL, 1);
        }
        if (!freed) f.sign(0, dy(f, 0, -1) + 2, -1, 0, -1, Texts.gibbet(h));
    }

    private static void signalPyre(Frame f, long h, boolean freed) {
        // A pyre on a mound, lit when strangers are seen: the Marshal's eyes across the Marches.
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                int hh = (int) Math.round((4.4 - d) * 0.8);
                for (int y = 1; y <= hh; y++) Build.onGround(f, x, z, y, y == hh ? GRAVEL : COBBLE, 0);
            }
        int top = dy(f, 0, 0) + 4;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { f.set(x, top, z, LOG2, 1); f.set(x, top + 1, z, freed ? AIR : NETHERRACK); if (!freed) f.set(x, top + 2, z, FIRE); }
        for (int[] p : new int[][] {{-2, -2}, {2, 2}, {-2, 2}, {2, -2}}) for (int y = 1; y <= 5; y++) f.set(p[0], top - 1 + y, p[1], freed ? AIR : NETHER_FENCE);
        guard(f, 3, 3, freed, "ashborn_bowman");
    }

    private static void burntVillage(Frame f, long h, boolean freed) {
        // What the Marches were: Kelani herders' round houses, burnt, their walls standing.
        for (int k = 0; k < 3; k++) {
            int cx = -7 + k * 7, cz = k % 2 == 0 ? -4 : 4;
            for (int x = cx - 3; x <= cx + 3; x++)
                for (int z = cz - 3; z <= cz + 3; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d > 3.4 || d < 2.5) continue;
                    int hgt = 1 + (int) (f.roll(x, 0, z, 102) * 3);
                    for (int y = 1; y <= hgt; y++) Build.onGround(f, x, z, y, f.roll(x, y, z, 103) < 0.5 ? COBBLE : HARD_CLAY, 0);
                }
            Build.onGround(f, cx, cz, 1, freed ? RED_FLOWER : CARPET, freed ? 8 : BLACK);
        }
        if (freed) settler(f, h, 0, 0, true, "kelani_herder");
        else f.chest(0, dy(f, 0, 8) + 1, 8, 0, -1, "atlas:kelani_hearth", null);
    }

    private static void chainLine(Frame f, long h, boolean freed) {
        // A line of shackle posts where the Bound wait to be marched to the quarries.
        for (int x = -10; x <= 10; x += 4) {
            int g = dy(f, x, 0);
            for (int y = 1; y <= 3; y++) f.set(x, g + y, 0, freed ? FENCE : NETHER_FENCE);
            if (freed) continue;
            f.set(x + 1, g + 2, 0, BARS); f.set(x + 2, g + 2, 0, BARS); f.set(x + 3, g + 2, 0, BARS);
            if (Math.floorMod(x, 8) == 2) f.npc(x + 1, g + 1, 1, 0, 1, "bound:laborer", null);
        }
        guard(f, 0, -3, freed, "taskmaster");
        if (freed) Build.flowers(f, -11, -11, 10, 10, 0.1);
    }

    private static void midden(Frame f, long h, boolean freed) {
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                f.c.clear(wx, wz, g - 1, g + 1);
                f.c.set(wx, g - 2, wz, freed ? DIRT : SOUL_SAND);
                if (!freed && f.roll(x, 0, z, 104) < 0.3) f.c.set(wx, g - 1, wz, BONE, 0);
            }
        CellsWound.scatter(f, h, 8);
    }

    private static void wargKennel(Frame f, long h, boolean freed) {
        Build.fence(f, -8, -8, 7, 7, freed ? FENCE : NETHER_FENCE, 0, 0, -8);
        if (freed) { Build.flowers(f, -7, -7, 6, 6, 0.2); return; }
        for (int k = 0; k < 3; k++) f.npc(-4 + k * 4, dy(f, -4 + k * 4, 0) + 1, 0, 0, 1, "dominion:warg", null);
        for (int[] p : new int[][] {{-5, 5}, {5, -5}, {3, 4}}) Build.onGround(f, p[0], p[1], 1, BONE, 0);
        guard(f, 0, -10, false, "gnawling");
    }

    private static void siegePark(Frame f, long h, boolean freed) {
        // Siege engines built for the Line: great catapults of blackened wood.
        for (int k = -1; k <= 1; k += 2) {
            int cx = k * 6;
            int g = dy(f, cx, 0);
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x - cx) == 2 || Math.abs(z) == 3) f.set(x, g + 1, z, LOG2, 1 | 4);
            for (int y = 2; y <= 5; y++) { f.set(cx - 2, g + y, 0, freed ? AIR : DARK_OAK_FENCE); f.set(cx + 2, g + y, 0, freed ? AIR : DARK_OAK_FENCE); }
            if (!freed) { for (int z = -4; z <= 3; z++) f.set(cx, g + 5 - Math.min(3, Math.abs(z + 1) / 2), z, LOG2, 1 | 8); f.set(cx, g + 5, -5, CAULDRON, 0); }
        }
        guard(f, 0, 6, freed, "blackshield");
    }

    private static void supplyDump(Frame f, long h, boolean freed) {
        for (int k = 0; k < 6; k++) {
            int x = -8 + (k % 3) * 8, z = k < 3 ? -3 : 3;
            Build.onGround(f, x, z, 1, freed ? HAY : LOG2, freed ? 0 : 1);
            Build.onGround(f, x + 1, z, 1, HAY, 0);
            if (!freed && k % 2 == 0) { int g = dy(f, x, z + 1); f.chest(x, g + 1, z + 1, 0, 1, "atlas:dominion_supplies", null); }
        }
        guard(f, 0, 0, freed, "ashborn");
        guard(f, 4, 6, freed, "gnawling");
    }

    static void obelisk(Frame f, long h, boolean freed, String text) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.footing(x, z, 0, false);
        for (int y = 0; y <= 12; y++) {
            int w = y < 3 ? 1 : 0;
            for (int x = -w; x <= w; x++) for (int z = -w; z <= w; z++) f.cinder(x, y, z);
        }
        f.set(0, 13, 0, freed ? QUARTZ : MAGMA, freed ? 1 : 0);
        f.sign(0, 2, -2, 0, -1, freed ? "THIS STONE\nNO LONGER\nSPEAKS.\n~ THE CONCORD" : text);
    }

    private static void wartFarm(Frame f, long h, boolean freed) {
        // Cinderwheat: nether wart on soul sand, the Ashborn's bread, worked by the Bound.
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                if (freed) { f.c.set(wx, g, wz, FARMLAND, 7); f.c.set(wx, g + 1, wz, CROPS, 2 + (int) (f.roll(x, 0, z, 105) * 4)); continue; }
                if (Math.floorMod(x, 4) == 0) { f.c.set(wx, g, wz, GRAVEL); continue; }
                f.c.set(wx, g, wz, SOUL_SAND);
                f.c.set(wx, g + 1, wz, NETHER_WART, 1 + (int) (f.roll(x, 0, z, 106) * 3));
            }
        if (!freed) {
            f.npc(-3, dy(f, -3, -2) + 1, -2, 0, 1, "bound:laborer", null);
            f.npc(5, dy(f, 5, 3) + 1, 3, 0, 1, "bound:laborer", null);
            guard(f, 0, -11, false, "taskmaster");
        } else settler(f, h, 0, 0, true, "farmer");
    }

    private static void warDrum(Frame f, long h, boolean freed) {
        // A war drum on a platform: its beat marches the legions (you can hear it from afar while the Marshal lives).
        f.footings(-3, -3, 3, 3, 1, false);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) f.cinder(x, 1, z);
        f.air(-3, 2, -3, 3, 6, 3);
        if (!freed) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { f.set(x, 2, z, NOTE); f.set(x, 3, z, CARPET, BROWN); }
            for (int[] p : new int[][] {{-3, -3}, {3, 3}, {-3, 3}, {3, -3}}) Build.brazier(f, p[0], 2, p[1]);
            guard(f, 2, 0, false, "ashborn");
            f.sign(0, 1, -4, 0, -1, "THE DRUM\nSPEAKS FOR\nTHE MARSHAL\nMARCH");
        } else Build.flowers(f, -11, -11, 10, 10, 0.1);
    }

    // ------------------------------------------------------------------ the Petrified Weald

    private static void stoneTrees(Frame f, long h, boolean freed) {
        for (int k = 0; k < 5; k++) {
            int x = (int) (Hash.unit(Hash.of(h, k, 21)) * 18) - 9, z = (int) (Hash.unit(Hash.of(h, k, 22)) * 18) - 9;
            if (freed && k % 2 == 0) Build.fruitTree(f, x, z); else Build.stoneTree(f, x, z, k % 3 == 0 ? 1 : 0);
        }
        if (freed) Build.flowers(f, -11, -11, 10, 10, 0.12);
    }

    private static void petrifiedFolk(Frame f, long h, boolean freed) {
        // People stilled mid-step: figures of stone where the Stiller's peace caught them.
        for (int k = 0; k < 4; k++) {
            int x = -6 + k * 4, z = k % 2 == 0 ? -3 : 3;
            int g = dy(f, x, z);
            f.set(x, g + 1, z, STONE, 0); f.set(x, g + 2, z, STONE, 0); f.set(x, g + 3, z, STONE, 5);
            if (!freed) f.set(x + (k % 2 == 0 ? 1 : -1), g + 2, z, COBBLE_STAIRS, f.stairs(1, 0, true));
            else f.set(x, g + 4, z, FLOWER_POT);
        }
        f.sign(0, dy(f, 0, -6) + 1, -6, 0, -1, freed ? "THEY MAY\nREST NOW.\n~ THE HYMN\nOF PASSAGE" : "HERE NONE\nSHALL DIE.\nHERE NONE\nSHALL LEAVE.");
        Build.stoneTree(f, 8, 8, 0);
    }

    private static void stillPool(Frame f, long h, boolean freed) {
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 6.4) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                f.c.set(wx, g, wz, d > 5.4 ? STONE : WATER, d > 5.4 ? 5 : 0);
                if (d <= 5.4) f.c.set(wx, g - 1, wz, freed ? CLAY_BLOCK : CONCRETE, GRAY);
                f.c.clear(wx, wz, g + 1, g + 2);
                if (!freed && d < 5 && f.roll(x, 0, z, 107) < 0.05) f.c.set(wx, g + 1, wz, CARPET, GRAY);   // ash skin on the water
                if (freed && d < 5 && f.roll(x, 0, z, 108) < 0.06) f.c.set(wx, g + 1, wz, LILY);
            }
        Build.stoneTree(f, 9, -8, 0);
        Build.stoneTree(f, -9, 8, 1);
    }

    private static void stoneStumps(Frame f, long h, boolean freed) {
        for (int x = -9; x <= 9; x += 4)
            for (int z = -9; z <= 9; z += 5) {
                int t = 1 + (int) (f.roll(x, 0, z, 109) * 3);
                for (int y = 1; y <= t; y++) Build.onGround(f, x, z, y, STONE, 5);
                if (freed && f.roll(x, 1, z, 110) < 0.5) Build.onGround(f, x, z, t + 1, SAPLING, 0);
            }
        Build.stoneTree(f, 0, 0, 1);
    }

    private static void hangingCages(Frame f, long h, boolean freed) {
        Build.stoneTree(f, 0, 0, 1);
        if (freed) return;
        for (int[] p : new int[][] {{-3, 0}, {3, 0}}) {
            int g = dy(f, p[0], p[1]);
            for (int y = 4; y <= 6; y++) { f.set(p[0] - 1, g + y, p[1], BARS); f.set(p[0] + 1, g + y, p[1], BARS); f.set(p[0], g + y, p[1] - 1, BARS); f.set(p[0], g + y, p[1] + 1, BARS); }
            f.set(p[0], g + 7, p[1], BARS); f.set(p[0], g + 8, p[1], BARS); f.set(p[0], g + 3, p[1], STONE, 5);
            f.set(p[0], g + 4, p[1], BONE, 0);
        }
        guard(f, 6, 6, false, "priest");
    }

    private static void silentBell(Frame f, long h, boolean freed) {
        // A bell frame whose bell has been filled with stone so it cannot ring (the Stiller hates endings).
        for (int y = 1; y <= 6; y++) { Build.onGround(f, -2, 0, y, STONE, 5); Build.onGround(f, 2, 0, y, STONE, 5); }
        for (int x = -2; x <= 2; x++) Build.onGround(f, x, 0, 7, STONE, 5);
        int g = dy(f, 0, 0);
        f.set(0, g + 5, 0, freed ? NOTE : STONE, 0);
        f.set(0, g + 6, 0, FENCE);
        f.sign(0, g + 1, -1, 0, -1, freed ? "THE BELL\nRINGS AGAIN\nFOR THE DEAD\nOF THE WEALD" : "NO BELL\nSHALL TOLL\nIN THE\nSTILLED WOOD");
        Build.stoneTree(f, 7, 7, 0);
    }

    private static void fontStone(Frame f, long h, boolean freed) {
        // A lesser stilling font: a basin of dark water that keeps the dying from dying.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.footing(x, z, 0, false);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.set(x, 1, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? STONE : freed ? WATER : CONCRETE, Math.abs(x) == 2 || Math.abs(z) == 2 ? 6 : BLACK);
        f.set(0, 2, 0, freed ? FLOWER_POT : BREWING, 0);
        Build.stoneTree(f, -7, 7, 0);
        guard(f, 4, 0, freed, "priest");
    }

    private static void mothShrine(Frame f, long h, boolean freed) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) { f.footing(x, z, 0, false); f.set(x, 0, z, STONE, 6); f.clear(x, z, 1, 5); }
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int y = 1; y <= 4; y++) f.set(p[0], y, p[1], STONE, 5);
        f.set(0, 1, 2, STONE, 6); f.set(0, 2, 2, SKULL, 1);
        f.sign(0, 1, 1, 0, -1, freed ? "REST\nNOW" : Texts.stillerPrayer(h));
    }

    // ------------------------------------------------------------------ the Scorched Forges

    private static void slagHeap(Frame f, long h, boolean freed) {
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 7.4) continue;
                int hh = (int) Math.round((7.4 - d) * 0.9);
                for (int y = 1; y <= hh; y++) { int g = f.ground(x, z); if (g >= 0) f.c.slag(f.wx(x, z), g + y, f.wz(x, z)); }
            }
        if (freed) Build.onGround(f, 9, 9, 1, SAPLING, 0);
        else guard(f, 9, 0, false, "gnawling");
    }

    private static void lavaChannel(Frame f, long h, boolean freed) {
        for (int z = -12; z <= 11; z++)
            for (int x = -2; x <= 2; x++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                if (Math.abs(x) == 2) { f.c.set(wx, g, wz, NETHER_BRICK); f.c.clear(wx, wz, g + 1, g + 2); continue; }
                f.c.set(wx, g - 1, wz, NETHER_BRICK);
                f.c.set(wx, g, wz, freed ? OBSIDIAN : LAVA);
                f.c.clear(wx, wz, g + 1, g + 2);
            }
        if (!freed) guard(f, 5, 0, false, "emberkin");
    }

    private static void chimney(Frame f, long h, boolean freed) {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                f.footing(x, z, 0, false);
                boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = 0; y <= 22; y++) {
                    if (shell) f.set(x, y, z, BRICK_BLOCK);
                    else f.set(x, y, z, y < 2 ? NETHERRACK : AIR);
                }
            }
        if (!freed) f.set(0, 2, 0, FIRE);
        for (int x = -2; x <= 2; x++) f.set(x, 23, -2, NETHER_BRICK);
        guard(f, 4, 4, freed, "gnawling");
    }

    private static void oreCarts(Frame f, long h, boolean freed) {
        for (int z = -12; z <= 11; z++) { Build.onGround(f, 0, z, 0, GRAVEL, 0); Build.onGround(f, 0, z, 1, RAIL, 0); }
        for (int k = -1; k <= 1; k++) Build.onGround(f, 3, k * 6, 1, k == 0 ? IRON_ORE : COAL_ORE, 0);
        if (!freed) {
            f.npc(2, dy(f, 2, 2) + 1, 2, -1, 0, "bound:laborer", null);
            guard(f, -3, -4, false, "taskmaster");
        }
    }

    private static void furnaceBank(Frame f, long h, boolean freed) {
        f.footings(-7, -2, 7, 2, 0, false);
        for (int x = -7; x <= 7; x++) for (int z = -2; z <= 2; z++) f.set(x, 0, z, NETHER_BRICK);
        for (int x = -6; x <= 6; x += 2) {
            f.set(x, 1, 0, FURNACE, f.facing(0, -1));
            f.set(x, 2, 0, BRICK_BLOCK);
            f.set(x, 3, 0, BRICK_BLOCK);
        }
        if (!freed) {
            f.npc(-3, 1, -2, 0, 1, "bound:laborer", null);
            f.npc(3, 1, -2, 0, 1, "bound:laborer", null);
            guard(f, 0, -5, false, "taskmaster");
            f.chest(7, 1, 0, -1, 0, "atlas:forge", null);
        }
    }

    private static void anvilYard(Frame f, long h, boolean freed) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) Build.onGround(f, x, z, 0, NETHER_BRICK, 0);
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) Build.onGround(f, p[0], p[1], 1, ANVIL, 0);
        Build.onGround(f, 0, 0, 1, freed ? WATER : LAVA, 0);
        if (!freed) { f.npc(-2, dy(f, -2, -3) + 1, -3, 1, 0, "bound:laborer", null); guard(f, 0, 5, false, "blackshield"); }
        f.stand(5, dy(f, 5, 0) + 1, 0, -1, 0, "orc_rack");
    }

    private static void pipeRun(Frame f, long h, boolean freed) {
        for (int x = -12; x <= 11; x++) {
            int g = dy(f, x, 0);
            f.set(x, g + 3, 0, IRON_TRAPDOOR, 8);
            if (Math.floorMod(x, 6) == 0) for (int y = 1; y <= 3; y++) f.set(x, g + y, 0, BARS);
        }
        Build.onGround(f, 0, 4, 1, CAULDRON, freed ? 3 : 0);
    }

    private static void crucible(Frame f, long h, boolean freed) {
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 3.4) continue;
                f.footing(x, z, 0, false);
                for (int y = 1; y <= 4; y++) f.set(x, y, z, d > 2.4 ? NETHER_BRICK : y == 4 && !freed ? LAVA : AIR);
            }
        guard(f, 5, 0, freed, "emberkin");
    }

    private static void coolingPool(Frame f, long h, boolean freed) {
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                boolean rim = Math.abs(x) == 5 || Math.abs(z) == 5;
                f.c.set(wx, g, wz, rim ? NETHER_BRICK : WATER);
                f.c.set(wx, g - 1, wz, OBSIDIAN);
                f.c.clear(wx, wz, g + 1, g + 2);
            }
        if (!freed) f.npc(6, dy(f, 6, 0) + 1, 0, -1, 0, "bound:laborer", null);
    }

    private static void slaveMine(Frame f, long h, boolean freed) {
        // A mine head: a timber frame over a shaft with ladders, tallies chalked on the wall.
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = g - 14; y <= g; y++) f.c.set(wx, y, wz, rim ? LOG2 : AIR, 1);
                if (x == 0 && z == -1) for (int y = g - 14; y <= g; y++) f.c.set(wx, y, wz, LADDER, f.facing(0, 1));
            }
        int g = dy(f, 0, 0);
        for (int[] p : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int y = 1; y <= 5; y++) f.set(p[0], g + y, p[1], DARK_OAK_FENCE);
        for (int x = -2; x <= 2; x++) f.set(x, g + 6, 0, LOG2, 1 | f.axisX(true));
        if (!freed) {
            f.npc(4, g + 1, 3, 0, -1, "bound:laborer", null);
            guard(f, -4, 3, false, "taskmaster");
            f.sign(0, g + 2, -3, 0, -1, Texts.tally(h));
        }
    }

    // ------------------------------------------------------------------ the Fallen Cities

    private static void ruinedHouse(Frame f, long h, boolean freed, boolean occupied) {
        // An Asterian house of the east: marble blackened by three centuries of ash, roof fallen, a hearth cold.
        int x0 = -6, x1 = 6, z0 = -5, z1 = 5;
        f.footings(x0, z0, x1, z1, 0, true);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                f.set(x, 0, z, f.roll(x, 0, z, 111) < 0.7 ? DOUBLE_SLAB : GRAVEL, 8);
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                if (!wall) { f.clear(x, z, 1, 5); continue; }
                int hgt = occupied ? 4 : 1 + (int) (f.roll(x, 1, z, 112) * 4);
                for (int y = 1; y <= hgt; y++) {
                    double r = f.roll(x, y, z, 113);
                    if (occupied && y == hgt && x == x0) f.set(x, y, z, NETHER_FENCE);
                    else f.set(x, y, z, r < 0.45 ? QUARTZ : r < 0.7 ? CONCRETE : r < 0.85 ? CONCRETE : GRAVEL, r < 0.45 ? 0 : r < 0.7 ? SILVER : GRAY);
                }
            }
        if (occupied) {
            // The Dominion lives in the conquered houses: black banners, a brazier, a Sworn scribe's desk.
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) f.set(x, 5, z, freed ? AIR : WOOL, BLACK);
            if (!freed) {
                f.banner(0, 3, z0 - 1, 0, -1, true, "black_ash");
                Build.brazier(f, 3, 1, 3);
                Build.table(f, -3, 1, 2, true);
                f.chest(-4, 1, 3, 1, 0, "atlas:sworn_desk", null);
                guard(f, 0, 0, false, "taskmaster");
                f.npc(-2, 1, -2, 1, 0, "bound:citizen", null);
            }
        } else {
            f.set(-4, 1, 3, FURNACE, f.facing(1, 0));
            f.chest(4, 1, 3, -1, 0, freed ? "atlas:household" : "atlas:ruin_house", null);
            if (!freed) f.set(0, 1, 0, CARPET, BLACK);
        }
        settler(f, h, 0, 2, freed, "settler");
    }

    private static void burntLibrary(Frame f, long h, boolean freed) {
        int x0 = -8, x1 = 8, z0 = -6, z1 = 6;
        f.footings(x0, z0, x1, z1, 0, true);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                f.set(x, 0, z, DOUBLE_SLAB, 8);
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                if (!wall) { f.clear(x, z, 1, 6); continue; }
                int hgt = 2 + (int) (f.roll(x, 1, z, 114) * 4);
                for (int y = 1; y <= hgt; y++) f.set(x, y, z, f.roll(x, y, z, 115) < 0.6 ? QUARTZ : CONCRETE, f.roll(x, y, z, 115) < 0.6 ? 0 : GRAY);
            }
        for (int x = x0 + 2; x <= x1 - 2; x += 3)
            for (int y = 1; y <= 3; y++) { f.set(x, y, z0 + 1, f.roll(x, y, 0, 116) < 0.5 ? BOOKSHELF : LOG2, 1); f.set(x, y, z1 - 1, f.roll(x, y, 1, 116) < 0.4 ? BOOKSHELF : AIR); }
        f.chest(0, 1, 0, 0, -1, "atlas:ash_library", null);
        f.sign(0, 1, z0 - 1, 0, -1, freed ? "THE LIBRARY OF\n" + Names.deme(h).toUpperCase() + "\nTO BE\nREBUILT" : "BURNED BY\nEDICT: THE\nCITY NEEDS\nNO MEMORY");
    }

    private static void gallows(Frame f, long h, boolean freed) {
        f.footings(-4, -2, 4, 2, 1, false);
        for (int x = -4; x <= 4; x++) for (int z = -2; z <= 2; z++) f.set(x, 1, z, PLANKS, 5);
        if (freed) { f.fill(-4, 2, -2, 4, 2, 2, AIR, 0); f.set(0, 2, 0, FLOWER_POT); return; }
        for (int y = 2; y <= 6; y++) { f.set(-3, y, 0, DARK_OAK_FENCE); f.set(3, y, 0, DARK_OAK_FENCE); }
        for (int x = -3; x <= 3; x++) f.set(x, 7, 0, LOG2, 1 | f.axisX(true));
        for (int x = -2; x <= 2; x += 2) f.set(x, 6, 0, BARS);
        f.sign(0, 2, -3, 0, -1, Texts.edict(h ^ 5));
        guard(f, 5, 3, false, "taskmaster");
    }

    private static void citizenPen(Frame f, long h, boolean freed) {
        // The Silent Magistrate's pens: citizens held without speech, waiting for judgement.
        Build.fence(f, -7, -7, 6, 6, freed ? FENCE : BARS, 0, 0, -7);
        if (!freed) {
            for (int k = 0; k < 3; k++) f.npc(-3 + k * 3, dy(f, -3 + k * 3, 0) + 1, 0, 0, -1, "bound:citizen", null);
            guard(f, 0, -9, false, "blackshield");
            f.sign(0, dy(f, 0, -8) + 1, -8, 0, -1, "SILENCE IS\nOBEDIENCE.\nOBEDIENCE IS\nPEACE.");
        } else Build.flowers(f, -6, -6, 5, 5, 0.2);
    }

    private static void defacedStatue(Frame f, long h, boolean freed) {
        f.footings(-2, -2, 2, 2, 1, true);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.ashlar(x, 1, z);
        Build.statue(f, 0, 2, 0, 0, -1, 3);
        if (!freed) { f.set(0, 5, 0, AIR); f.set(0, 5, 1, SKULL, 1); f.banner(0, 3, -1, 0, -1, true, "black_ash"); }
        f.sign(0, 1, -3, 0, -1, freed ? Texts.statue(h) : "THE FACE WAS\nA LIAR.\nTHE EDICT\nIS TRUE.");
    }

    private static void ruinedShrine(Frame f, long h, boolean freed) {
        f.footings(-3, -3, 3, 3, 0, true);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) { f.set(x, 0, z, DOUBLE_SLAB, 8); f.clear(x, z, 1, 6); }
        for (int[] p : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            int t = freed ? 4 : 1 + (int) (f.roll(p[0], 0, p[1], 117) * 3);
            for (int y = 1; y <= t; y++) f.set(p[0], y, p[1], QUARTZ, 2);
        }
        f.set(0, 1, 1, QUARTZ, 1);
        f.set(0, 2, 1, freed ? SEA_LANTERN : CARPET, freed ? 0 : BLACK);
        f.sign(0, 1, 0, 0, -1, freed ? Texts.shrine(h) : "THE LAMP\nIS OUT.\nWE KEEP IT\nIN SECRET");
    }

    private static void ashGarden(Frame f, long h, boolean freed) {
        for (int x = -9; x <= 9; x += 3) for (int z = -9; z <= 9; z += 3) {
            if (freed) Build.onGround(f, x, z, 1, RED_FLOWER, (x + z) & 7);
            else Build.onGround(f, x, z, 1, DEADBUSH, 0);
        }
        Build.deadTree(f, 0, 0, !freed);
        if (!freed) f.npc(2, dy(f, 2, 2) + 1, 2, 0, -1, "bound:citizen", null);
    }

    // ------------------------------------------------------------------ the Plateau of Cinders

    private static void fissure(Frame f, long h, boolean freed) {
        double a = f.pick(h, 1) * Math.PI;
        for (int t = -11; t <= 11; t++) {
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            for (int w = -1; w <= 1; w++) {
                int xx = x + (int) Math.round(-Math.sin(a) * w), zz = z + (int) Math.round(Math.cos(a) * w);
                int g = f.ground(xx, zz);
                if (g < 0) continue;
                int wx = f.wx(xx, zz), wz = f.wz(xx, zz);
                f.c.clear(wx, wz, g - 2, g + 1);
                f.c.set(wx, g - 3, wz, freed ? OBSIDIAN : LAVA);
            }
        }
    }

    private static void spikeField(Frame f, long h, boolean freed) {
        for (int x = -10; x <= 10; x += 2)
            for (int z = -10; z <= 10; z += 2) {
                if (f.roll(x, 0, z, 118) > 0.45) continue;
                int t = 1 + (int) (f.roll(x, 1, z, 119) * (freed ? 1 : 5));
                for (int y = 1; y <= t; y++) Build.onGround(f, x, z, y, y == t ? BARS : OBSIDIAN, 0);
            }
    }

    private static void bonePile(Frame f, long h, boolean freed) {
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                int hh = (int) Math.round((4.4 - d) * 0.7);
                for (int y = 1; y <= hh; y++) Build.onGround(f, x, z, y, freed ? DIRT : BONE, 0);
            }
        if (!freed) Build.onGround(f, 0, 0, 4, SKULL, 1);
        else Build.onGround(f, 0, 0, 4, SAPLING, 0);
    }

    private static void ashVent(Frame f, long h, boolean freed) {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 2.4) continue;
                Build.onGround(f, x, z, 0, d < 1 ? (freed ? STONE : MAGMA) : OBSIDIAN, 0);
            }
        if (!freed) Build.onGround(f, 0, 0, 1, FIRE, 0);
    }

    private static void rubble(Frame f, long h, boolean freed) {
        for (int k = 0; k < 14; k++) {
            int x = (int) (Hash.unit(Hash.of(h, k, 31)) * 22) - 11, z = (int) (Hash.unit(Hash.of(h, k, 32)) * 22) - 11;
            int t = 1 + (int) (Hash.unit(Hash.of(h, k, 33)) * 2);
            for (int y = 1; y <= t; y++) { int g = f.ground(x, z); if (g >= 0) f.c.cinder(f.wx(x, z), g + y, f.wz(x, z)); }
        }
    }
}
