package chat.jaspr.atlas;

/**
 * Every 24-block cell that no place or site claims holds something: in the Concord, fields, groves, vineyards, gardens,
 * shrines, tombs, wells, farmhouses, workshops and lumen works; in the Wound, craters, dead automata, trenches and burnt
 * farms; in the Dominion, what each province is made of (tents and stakes in the Marches, stone trees in the Weald, slag
 * and lava in the Forges, ruined houses in the Fallen Cities, fissures on the Plateau). Cells beside a road hold
 * roadside things (milestones, tombs, statues, wayshrines; gibbets and stakes in the Dominion).
 */
final class Cells {
    private Cells() {}

    static final String[] CONCORD = {"wheat", "wheat", "olives", "olives", "vines", "orchard", "meadow", "pasture", "garden", "shrine", "tomb",
        "statue", "well", "threshing", "windmill", "lumen_well", "aqueduct", "pond", "farmhouse", "potter", "beehives", "cypress_walk", "herm_grove",
        "fountain", "stoa", "orrery", "barley", "flax", "lavender"};
    static final String[] CONCORD_ROAD = {"milestone", "tomb", "wayshrine", "statue", "fountain", "stoa", "cypress_walk", "herm_grove"};
    static final String[] FRONTIER = {"drill_yard", "depot", "automaton_shed", "signal_tower", "watch_camp", "wheat", "olives", "barracks"};
    static final String[] WOUND = {"craters", "dead_talos", "trench", "burnt_farm", "dead_orchard", "bones", "fallen_column", "ember_pit", "craters",
        "broken_wagon", "grave_field"};
    static final String[] MARCHES = {"ash_dunes", "impaling_field", "tents", "gibbets", "signal_pyre", "burnt_village", "chain_line", "midden",
        "warg_kennel", "siege_park", "supply_dump", "obelisk", "wart_farm", "war_drum", "ash_dunes", "tents", "wart_farm"};
    static final String[] MARCHES_ROAD = {"gibbets", "impaling_field", "chain_line", "obelisk", "signal_pyre"};
    static final String[] WEALD = {"stone_trees", "stone_trees", "stone_trees", "petrified_folk", "still_pool", "stone_stumps", "hanging_cages",
        "silent_bell", "font_stone", "stone_trees", "moth_shrine"};
    static final String[] FORGES = {"slag_heap", "lava_channel", "chimney", "ore_carts", "furnace_bank", "anvil_yard", "pipe_run", "crucible",
        "cooling_pool", "slag_heap", "slave_mine"};
    static final String[] FALLEN = {"ruined_house", "ruined_house", "burnt_library", "occupied_house", "gallows", "edict_stele", "citizen_pen",
        "defaced_statue", "ruined_house", "ruined_shrine", "ash_garden"};
    static final String[] PLATEAU = {"fissure", "black_obelisk", "spike_field", "bone_pile", "ash_vent", "rubble", "fissure", "spike_field"};

    static String pick(Realm.Zone zone, long h, boolean roadside, int x, int z) {
        String[] table;
        switch (zone) {
            case CONCORD:
                if (roadside) table = CONCORD_ROAD;
                else if (x > Realm.lineX(z) - 150) table = Hash.unit(Hash.mix(h ^ 5)) < 0.6 ? FRONTIER : CONCORD;
                else table = CONCORD;
                break;
            case WOUND: table = WOUND; break;
            case MARCHES: case GATE_ROAD: table = roadside ? MARCHES_ROAD : MARCHES; break;
            case WEALD: table = WEALD; break;
            case FORGES: table = FORGES; break;
            case FALLEN: table = FALLEN; break;
            case PLATEAU: table = PLATEAU; break;
            default: return null;
        }
        return table[Hash.range(Hash.mix(h ^ 4), 0, table.length - 1)];
    }

    /** Draws the cells touching the canvas' chunk (a chunk lies in at most four cells). */
    static void draw(Plans plans, Canvas c) {
        for (int i = Math.floorDiv(c.x0, Plans.CELL); i <= Math.floorDiv(c.x0 + 15, Plans.CELL); i++)
            for (int j = Math.floorDiv(c.z0, Plans.CELL); j <= Math.floorDiv(c.z0 + 15, Plans.CELL); j++) {
                Plans.Cell cell = plans.cell(i, j);
                if (cell == null || cell.kind == null) continue;
                Frame f = new Frame(c, cell.x, cell.z, cell.base, cell.rot);
                try {
                    if (cell.zone.concord) CellsConcord.draw(plans, f, cell);
                    else if (cell.zone == Realm.Zone.WOUND) CellsWound.draw(plans, f, cell);
                    else CellsDominion.draw(plans, f, cell, c.liberated(cell.zone.province));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("cell " + cell.kind + " at " + cell.x + "," + cell.z, e);
                }
            }
    }
}
