package chat.jaspr.dungeon;

/**
 * Generation 7: where a room's physical perils are (pure, from the room alone, like HazardCatalog's marks). A floor's
 * generator draws each one (a hanging stalactite, a lava pool, cracked flagstones, a vent) at exactly the columns its own
 * predicate names, and Perils makes them act at runtime from the same predicate, so what a player sees is where the danger
 * comes from. Never inside the arrival circle, never on a lane core (stations, doors and the walk between them stay safe to
 * stand on; lava and pits may border them), never under the reliquary chest.
 */
public final class PerilMarks {
    private PerilMarks() {}
    public enum Kind {
        /** A hanging point of rock at the roof above (x, z): it may break loose and fall when someone stands beneath. */
        STALACTITE,
        /** Lava one block deep in the floor at (x, z) (the generator draws it; Perils keeps mobs and players honest about it). */
        LAVA,
        /** Cracked flagstones at (x, z) over a shallow magma pit: they give way under a player, then mend. */
        CRUMBLE,
        /** A steam or flame vent at (x, z): it erupts on a cycle, throwing or burning whoever stands on it. */
        GEYSER,
        /** A molten fall from the roof at (x, z) (a column of lava behind glass, or open on Floor III): it splashes near it. */
        LAVA_FALL
    }
    /** True when this kind of peril is at (x, z) of room r. */
    public static boolean at(Kind k, Layout.Room r, int x, int z) {
        if (r == null || r.kind == Layout.Kind.REFUGE && r.x == 0 && r.z == 0) return false;
        if (!r.inner(x + .5, z + .5)) return false;
        switch (r.floor) {
            case 2: return FloorTwo.peril(k, r, x, z);
            case 3: return FloorThree.peril(k, r, x, z);
            default: return FloorOneExtra.peril(k, r, x, z);
        }
    }
    /** A lane core (stations, doorways and the walk between them): perils never take one. */
    public static boolean core(Layout.Room r, int x, int z) {
        return Layout.Room.Stations.core(r.w, r.d, x - r.x, z - r.z) || Math.abs(x - r.cx()) <= 2 && Math.abs(z - (r.cz() + 4)) <= 2;
    }
}
