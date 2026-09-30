package chat.jaspr.backrooms;

/**
 * How one level looks. {@link #body} draws a column of the level's main space (between its entry room and its arena),
 * {@link #arena} a column of the boss arena; entry rooms, the exit corridor and the zone's outer walls are drawn by
 * {@link Rooms} in this style's palette. Everything is a pure function of (seed, x, z).
 */
interface Style {
    /** The level's surfaces: floor, walls and the solid mass around rooms, ceiling, and ceiling lights. */
    int floorId();
    int floorMeta();
    int wallId();
    int wallMeta();
    int ceilId();
    int ceilMeta();
    int lightId();
    default int lightMeta() { return 0; }
    /** Air blocks above the floor in the entry room and the exit corridor. */
    default int roomAir() { return 5; }

    void body(Canvas c, Level lv, int x, int z);
    void arena(Canvas c, Level lv, int x, int z);

    /** Whether a player at walking height is blocked at (x, z) in the level's main space (for the guide map). */
    boolean solid(Level lv, long seed, int x, int z);
    /** Map colour of an open spot (default floor colour); water or streets may differ. */
    default byte open(Level lv, long seed, int x, int z) { return Styles.FLOOR_INK; }
    /** Draws a column of the zone's outer wall itself (return false to leave it to the default solid wall). */
    default boolean boundary(Canvas c, Level lv, int x, int z) { return false; }
    /** Where the boss stands when it wakes (x, y, z). */
    default int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 8, Level.WALK, 0}; }
}
