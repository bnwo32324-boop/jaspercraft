package chat.jaspr.lostcities;

/** Port of mcjty.lostcities.dimensions.world.lost.Orientation. */
enum Orientation {
    X, Z;

    Direction getMinDir() { return this == X ? Direction.XMIN : Direction.ZMIN; }
    Direction getMaxDir() { return this == X ? Direction.XMAX : Direction.ZMAX; }
    Orientation getOpposite() { return this == X ? Z : X; }
}
