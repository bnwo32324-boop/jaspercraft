package chat.jaspr.lostcities;

/** Port of mcjty.lostcities.dimensions.world.lost.Direction. */
enum Direction {
    XMIN, XMAX, ZMIN, ZMAX;

    static final Direction[] VALUES = {XMIN, XMAX, ZMIN, ZMAX};

    Orientation getOrientation() { return (this == XMIN || this == XMAX) ? Orientation.X : Orientation.Z; }

    Transform getRotation() {
        switch (this) {
            case XMIN: return Transform.ROTATE_NONE;
            case XMAX: return Transform.ROTATE_180;
            case ZMIN: return Transform.ROTATE_90;
            case ZMAX: return Transform.ROTATE_270;
        }
        throw new IllegalStateException();
    }

    Direction getOpposite() {
        switch (this) {
            case XMIN: return XMAX;
            case XMAX: return XMIN;
            case ZMIN: return ZMAX;
            case ZMAX: return ZMIN;
        }
        throw new IllegalStateException();
    }

    BuildingInfo get(BuildingInfo info) {
        switch (this) {
            case XMIN: return info.getXmin();
            case XMAX: return info.getXmax();
            case ZMIN: return info.getZmin();
            case ZMAX: return info.getZmax();
        }
        throw new IllegalStateException();
    }

    boolean atSide(int x, int z) {
        switch (this) {
            case XMIN: return x == 0;
            case XMAX: return x == 15;
            case ZMIN: return z == 0;
            case ZMAX: return z == 15;
        }
        throw new IllegalStateException();
    }
}
