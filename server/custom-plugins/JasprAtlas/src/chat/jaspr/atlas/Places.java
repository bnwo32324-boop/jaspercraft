package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/** The fixed places of the story: the Gate of Strangers, the four cities, the Last Watch and the Dominion's holds. */
final class Places {
    private Places() {}

    static void draw(Plans plans, Canvas c) {
        for (Realm.Place p : Realm.Place.values()) {
            int r = p == Realm.Place.ANTHRAKION ? Realm.WARD_R + 12 : p.radius + 24;
            if (!c.touches(p.x - r, p.z - r, p.x + r, p.z + r)) continue;
            Frame f = new Frame(c, p.x, p.z, Terrain.base(p), 0);
            boolean freed = p.province != null && c.liberated(p.province);
            try {
                switch (p) {
                    case GATE_OF_STRANGERS: Threshold.draw(plans, f); break;
                    case ASTREION: case LAMPSA: case HIERANTHE: case MNEMEIA: Cities.draw(plans, f, p); break;
                    case LAST_WATCH: Frontier.lastWatch(plans, f); break;
                    case PYLON: Strongholds.pylon(plans, f, freed); break;
                    case WARCAMP: Strongholds.warcamp(plans, f, freed); break;
                    case RIDER_TOWER: Strongholds.riderTower(plans, f, freed); break;
                    case STILLED_GARDEN: Strongholds.stilledGarden(plans, f, freed); break;
                    case SALLOW_HOUSE: Strongholds.sallowHouse(plans, f, freed); break;
                    case GREAT_ENGINE: Strongholds.greatEngine(plans, f, freed); break;
                    case SLAG_QUARRY: Strongholds.slagQuarry(plans, f, freed); break;
                    case FIEND_PIT: Strongholds.fiendPit(plans, f, freed); break;
                    case PELLENE: case AIGAI: Strongholds.fallenCity(plans, f, p, freed); break;
                    case ANTHRAKION: Anthrakion.draw(plans, f, c); break;
                    case UZGAR_HIDE: Hideouts.uzgar(f); break;
                    case VESK_BURROW: Hideouts.vesk(f, freed); break;
                    default: break;
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("place " + p + " chunk " + c.cx + "," + c.cz, e);
            }
        }
    }

    /** A levelled ground disc for a place (radius r): fill up or cut down to the base, then the given surface. */
    static void level(Frame f, int r, int surface) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (!f.inside(x, z)) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                for (int y = Math.min(g, f.base) - 2; y < f.base; y++) f.c.set(wx, y, wz, DIRT);
                f.c.clear(wx, wz, f.base + 1, Math.max(g, f.base) + 4);
                switch (surface) {
                    case 0: f.c.set(wx, f.base, wz, GRASS); break;
                    case 1: f.c.ash(wx, f.base, wz); break;
                    case 2: f.c.pave(wx, f.base, wz); break;
                    case 3: f.c.set(wx, f.base, wz, CONCRETE, BLACK); break;
                    default: f.c.set(wx, f.base, wz, DIRT, 1);
                }
            }
    }
}
