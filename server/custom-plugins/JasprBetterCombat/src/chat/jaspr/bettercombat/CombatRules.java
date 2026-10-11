/* Cooldown/damage math adapted from SanAndreasP's Better Combat Rebirth Helpers.
 * Original per-file notice: CC BY-NC-SA 4.0. See THIRD_PARTY_NOTICES.txt.
 */
package chat.jaspr.bettercombat;

/** Arithmetic from Better Combat Rebirth 1.5.6, independent of either platform. */
public final class CombatRules {
    private CombatRules() {}
    public static float strength(int remaining, int period) {
        return Math.max(0, Math.min(1, 1f - (float) remaining / Math.max(1, period)));
    }
    public static int period(double modifier) {
        double speed = 4 + modifier;
        // Harden malformed imported attributes; never divide by zero or create an unbounded timer.
        return Double.isFinite(speed) && speed > 0 ? Math.max(1, Math.min(1200, (int)(20 / speed))) : 1200;
    }
    public static float damage(float base, float enchant, float strength, boolean crit) {
        float c = Math.max(0, Math.min(1, strength));
        return base * (.2f + .8f * c * c) * (crit ? 1.5f : 1) + enchant * c;
    }
    /** Distance along a normalized ray to an AABB, or infinity. Includes origin-inside. */
    public static double intersect(double[] eye, double[] dir, double[] min, double[] max, double limit) {
        double near = 0, far = limit;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1e-12) {
                if (eye[i] < min[i] || eye[i] > max[i]) return Double.POSITIVE_INFINITY;
            } else {
                double a = (min[i] - eye[i]) / dir[i], b = (max[i] - eye[i]) / dir[i];
                near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
                if (near > far) return Double.POSITIVE_INFINITY;
            }
        }
        return near;
    }
}
