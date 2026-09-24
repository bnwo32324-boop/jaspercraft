package chat.jaspr.apocalypse;

/**
 * Small, dependency-free pieces of the wall-jump contract. Keeping these
 * calculations separate makes the movement edge cases deterministic and easy
 * to exercise without constructing a live Paper player.
 */
public final class WallJumpRules {
    /** A wall on the positive-Z side of the player. */
    public static final int SOUTH = 1;
    /** A wall on the negative-X side of the player. */
    public static final int WEST = 2;
    /** A wall on the negative-Z side of the player. */
    public static final int NORTH = 4;
    /** A wall on the positive-X side of the player. */
    public static final int EAST = 8;

    private WallJumpRules() { }

    /**
     * Returns the vertical component to use for the current cling tick. The
     * first branch catches an upward collision, the second softens a fast
     * fall, and the final branch implements the delayed slow slide.
     */
    public static double slideVelocity(double currentY, int clingTicks, int slideDelay) {
        if (currentY > 0.0) return 0.0;
        if (currentY < -0.6) return currentY + 0.2;
        return clingTicks > slideDelay ? -0.1 : 0.0;
    }

    /**
     * Computes the launch from the player's horizontal movement intent. This
     * mirrors the original mod's forward/strafe-based launch: a player can
     * deliberately jump into the wall and clear a two-block obstacle instead
     * of being forced backward by the wall normal.
     *
     * The wall mask remains part of the signature so the controller can keep
     * the same collision contract and callers can pass zero intent to use the
     * facing-direction fallback.
     */
    public static double[] jumpVelocity(int walls, double boost, double yawDegrees,
                                        double intentX, double intentZ) {
        double x = intentX, z = intentZ;
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0e-9) {
            double radians = Math.toRadians(yawDegrees);
            x = -Math.sin(radians);
            z = Math.cos(radians);
            length = Math.sqrt(x * x + z * z);
        }
        double horizontal = boost * 0.45;
        return new double[] { x / length * horizontal, boost, z / length * horizontal };
    }

    /** Uses the player's horizontal facing direction when no input sample is available. */
    public static double[] jumpVelocity(int walls, double boost, double yawDegrees) {
        return jumpVelocity(walls, boost, yawDegrees, 0.0, 0.0);
    }

    /** Re-clinging stays blocked until the player has dropped one block. */
    public static boolean canRecling(double currentY, double lastJumpY, int staleWalls,
                                     int currentWalls, boolean allowReclinging) {
        if (allowReclinging || !Double.isFinite(lastJumpY)) return true;
        if (currentY < lastJumpY - 1.0) return true;
        return (staleWalls & currentWalls) != currentWalls;
    }
}
