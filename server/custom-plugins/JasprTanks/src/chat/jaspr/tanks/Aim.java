package chat.jaspr.tanks;

/** Pure ray and steering math shared by targeting and shell flight (no server types, unit-testable). */
final class Aim {
    private Aim() {}

    /**
     * Distance along a unit ray at which it enters the box {minX,minY,minZ,maxX,maxY,maxZ}, 0 when the origin
     * is inside, or -1 when it misses or the entry lies beyond {@code max}.
     */
    static double ray(double ox, double oy, double oz, double dx, double dy, double dz, double[] box, double max) {
        double near = 0, far = max;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz};
        for (int axis = 0; axis < 3; axis++) {
            double lo = box[axis], hi = box[axis + 3];
            if (Math.abs(d[axis]) < 1e-9) {
                if (o[axis] < lo || o[axis] > hi) return -1;
                continue;
            }
            double t1 = (lo - o[axis]) / d[axis], t2 = (hi - o[axis]) / d[axis];
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            near = Math.max(near, t1);
            far = Math.min(far, t2);
            if (near > far) return -1;
        }
        return near;
    }

    static double[] grow(double[] box, double by) {
        return new double[] {box[0] - by, box[1] - by, box[2] - by, box[3] + by, box[4] + by, box[5] + by};
    }

    /** Unit direction {x,y,z} for a Minecraft yaw/pitch in degrees. */
    static double[] direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch), c = Math.cos(p);
        return new double[] {-Math.sin(y) * c, -Math.sin(p), Math.cos(y) * c};
    }

    /** Rotates unit vector {@code from} toward unit vector {@code to} by at most {@code maxDegrees}. */
    static double[] steer(double[] from, double[] to, double maxDegrees) {
        double dot = Math.max(-1, Math.min(1, from[0] * to[0] + from[1] * to[1] + from[2] * to[2]));
        double angle = Math.acos(dot), limit = Math.toRadians(maxDegrees);
        if (angle <= limit || angle < 1e-6) return to.clone();
        // Spherical interpolation by the fraction of the angle we may turn this tick.
        double f = limit / angle, s = Math.sin(angle);
        double a = Math.sin((1 - f) * angle) / s, b = Math.sin(f * angle) / s;
        double[] out = {from[0] * a + to[0] * b, from[1] * a + to[1] * b, from[2] * a + to[2] * b};
        double len = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        if (len < 1e-9) return from.clone();
        out[0] /= len; out[1] /= len; out[2] /= len;
        return out;
    }
}
