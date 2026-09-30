package chat.jaspr.backrooms;

import org.bukkit.map.MapPalette;

/** The seven level styles, and the guide map's inks. */
final class Styles {
    private Styles() {}

    @SuppressWarnings("deprecation") static final byte FLOOR_INK = MapPalette.matchColor(214, 200, 150);
    @SuppressWarnings("deprecation") static final byte WALL_INK = MapPalette.matchColor(70, 60, 40);
    @SuppressWarnings("deprecation") static final byte WATER_INK = MapPalette.matchColor(90, 170, 210);
    @SuppressWarnings("deprecation") static final byte DEEP_INK = MapPalette.matchColor(40, 90, 160);
    @SuppressWarnings("deprecation") static final byte ROAD_INK = MapPalette.matchColor(60, 60, 66);
    @SuppressWarnings("deprecation") static final byte PARK_INK = MapPalette.matchColor(70, 120, 50);
    @SuppressWarnings("deprecation") static final byte ROOM_INK = MapPalette.matchColor(240, 220, 120);
    @SuppressWarnings("deprecation") static final byte ARENA_INK = MapPalette.matchColor(160, 40, 40);
    @SuppressWarnings("deprecation") static final byte VOID_INK = MapPalette.matchColor(20, 18, 16);

    private static final Style[] STYLES = {new YellowRooms(), new Warehouse(), new Tunnels(), new Electrical(), new Office(), new City(), new Pools()};

    static Style of(Level lv) { return STYLES[lv.index()]; }

    /** The guide map's colour of a block column (pure math, no world access). */
    static byte ink(long seed, int x, int z) {
        Level lv = Level.at(x, z);
        if (lv == null) return VOID_INK;
        Style s = of(lv);
        if (x < lv.x0() + Level.WALL || x >= lv.xEnd() - Level.WALL || z < lv.zMin() + Level.WALL || z >= lv.zMax() - Level.WALL) return WALL_INK;
        if (x < lv.entryEnd()) {
            int hx = x - lv.x0();
            if (lv.number == 1) return hx >= 4 && hx <= 47 && Math.abs(z) <= Rooms.HUB_HALF ? ROOM_INK : WALL_INK;
            return hx >= 2 && Math.abs(z) <= Rooms.LANDING_HALF ? ROOM_INK : WALL_INK;
        }
        if (x >= lv.exitStart()) return Math.abs(z) <= 2 ? ROOM_INK : WALL_INK;
        if (x >= lv.arenaStart()) return Math.abs(z) < lv.arenaWidth / 2 ? ARENA_INK : WALL_INK;
        if (lv != Level.CITY && Math.abs(z) <= 3 && (x - lv.entryEnd() < Rooms.VESTIBULE || lv.arenaStart() - x <= Rooms.VESTIBULE)) return FLOOR_INK;
        return s.solid(lv, seed, x, z) ? WALL_INK : s.open(lv, seed, x, z);
    }
}
