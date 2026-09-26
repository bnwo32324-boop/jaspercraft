package chat.jaspr.lostcities;

/** Port of mcjty.lostcities.dimensions.world.lost.Transform (part placement transforms). */
enum Transform {
    ROTATE_NONE(0),
    ROTATE_90(1),
    ROTATE_180(2),
    ROTATE_270(3),
    MIRROR_X(2),
    MIRROR_Z(2),
    MIRROR_90_X(1);

    /** The vanilla Rotation the mod applied to stairs/ladders: 0 none, 1 cw90, 2 cw180, 3 ccw90. */
    final int mcRotation;

    Transform(int mcRotation) { this.mcRotation = mcRotation; }

    int rotateX(int x, int z) {
        switch (this) {
            case ROTATE_NONE: return x;
            case ROTATE_90: return 15 - z;
            case ROTATE_180: return 15 - x;
            case ROTATE_270: return z;
            case MIRROR_X: return 15 - x;
            case MIRROR_Z: return x;
            case MIRROR_90_X: return z;
        }
        throw new IllegalStateException();
    }

    int rotateZ(int x, int z) {
        switch (this) {
            case ROTATE_NONE: return z;
            case ROTATE_90: return x;
            case ROTATE_180: return 15 - z;
            case ROTATE_270: return 15 - x;
            case MIRROR_X: return z;
            case MIRROR_Z: return 15 - z;
            case MIRROR_90_X: return x;
        }
        throw new IllegalStateException();
    }

    // EnumRailDirection ordinals (rail meta): 0 NS, 1 EW, 2 ASC_E, 3 ASC_W, 4 ASC_N, 5 ASC_S, 6 SE, 7 SW, 8 NW, 9 NE
    private static final int NS = 0, EW = 1, AE = 2, AW = 3, AN = 4, AS = 5, SE = 6, SW = 7, NW = 8, NE = 9;

    /** Transform.transform(EnumRailDirection), exactly as the mod's table. */
    int rail(int shape) {
        if (this == ROTATE_NONE) return shape;
        switch (shape) {
            case NS: return (this == ROTATE_90 || this == ROTATE_270 || this == MIRROR_90_X) ? EW : shape;
            case EW: return (this == ROTATE_90 || this == ROTATE_270) ? NS : shape;
            case AE:
                switch (this) { case ROTATE_90: return AS; case MIRROR_90_X: return AN; case ROTATE_180: return AW; case ROTATE_270: return AN; case MIRROR_X: return AW; default: break; }
                break;
            case AW:
                switch (this) { case ROTATE_90: return AN; case MIRROR_90_X: return AS; case ROTATE_180: return AE; case ROTATE_270: return AS; case MIRROR_X: return AE; default: break; }
                break;
            case AN:
                switch (this) { case ROTATE_90: return AE; case MIRROR_90_X: return AW; case ROTATE_180: return AS; case ROTATE_270: return AW; case MIRROR_X: return AS; case MIRROR_Z: return AS; default: break; }
                break;
            case AS:
                switch (this) { case ROTATE_90: return AW; case MIRROR_90_X: return AE; case ROTATE_180: return AN; case ROTATE_270: return AE; case MIRROR_X: return AN; case MIRROR_Z: return AN; default: break; }
                break;
            case SE:
                switch (this) { case ROTATE_90: return SW; case MIRROR_90_X: return NE; case ROTATE_180: return NW; case ROTATE_270: return NE; case MIRROR_X: return SW; case MIRROR_Z: return NE; default: break; }
                break;
            case SW:
                switch (this) { case ROTATE_90: return NW; case MIRROR_90_X: return SE; case ROTATE_180: return NE; case ROTATE_270: return SE; case MIRROR_X: return SE; case MIRROR_Z: return NW; default: break; }
                break;
            case NW:
                switch (this) { case ROTATE_90: return NE; case MIRROR_90_X: return SW; case ROTATE_180: return SE; case ROTATE_270: return SW; case MIRROR_X: return NE; case MIRROR_Z: return SW; default: break; }
                break;
            case NE:
                switch (this) { case ROTATE_90: return SE; case MIRROR_90_X: return NW; case ROTATE_180: return SW; case ROTATE_270: return NW; case MIRROR_X: return NW; case MIRROR_Z: return SE; default: break; }
                break;
            default:
                break;
        }
        // The mod throws here ("Cannot happen!"); a shape it never produces is left unchanged instead.
        return shape;
    }

    /** Rotate a rail/golden-rail char's shape. Golden rails keep their powered bit. */
    char railChar(char c) {
        int id = c >> 4, m = c & 15;
        if (id == 66) {
            if (m > 9) return c;
            return B.c(id, rail(m));
        }
        int shape = m & 7, powered = m & 8;
        if (shape > 5) return c;
        int ns = rail(shape);
        if (ns > 5) ns = shape;   // golden rails cannot curve
        return B.c(id, ns | powered);
    }
}
