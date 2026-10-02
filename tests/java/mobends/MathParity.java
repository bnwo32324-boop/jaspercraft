package mobends;

import goblinbob.mobends.core.animation.keyframe.BinaryAnimationLoader;
import goblinbob.mobends.core.animation.keyframe.Bone;
import goblinbob.mobends.core.animation.keyframe.Keyframe;
import goblinbob.mobends.core.animation.keyframe.KeyframeAnimation;
import goblinbob.mobends.core.math.Quaternion;
import goblinbob.mobends.core.math.QuaternionUtils;
import goblinbob.mobends.core.math.SmoothOrientation;
import goblinbob.mobends.core.math.vector.SmoothVector3f;
import goblinbob.mobends.core.math.vector.Vec3f;
import goblinbob.mobends.core.util.GUtil;
import goblinbob.mobends.core.util.Tween;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parity harness for the JavaScript port of the Mo' Bends 1.2.2 animation core
 * (client-mods/mobends/mobends-core.js).
 *
 * tests/mobends-math-parity.test.cjs copies the ORIGINAL, unmodified Mo' Bends sources (Quaternion,
 * SmoothOrientation, QuaternionUtils, SmoothVector3f, Vec3f and its interfaces, VectorUtils, Tween, GUtil,
 * BinaryAnimationLoader, SerialHelper, ...) into a temporary directory, adds two tiny stubs for the only
 * outside classes they reference (org.apache.http.util.ByteArrayBuffer, net.minecraft.client.gui.FontRenderer),
 * compiles them together with this file and runs it. The Node test then replays every printed operation, with
 * the exact same arguments, against the JS core and compares every printed result.
 *
 * Usage: java mobends.MathParity seed steps [animation files...]
 *
 * Output: one record per line, space separated. Floats are printed as 8 hex digits (Float.floatToRawIntBits),
 * doubles as 16 hex digits (Double.doubleToRawLongBits), integers in decimal, so the JS side sees the exact Java
 * values. Records:
 *   MH group x sin(x) cos(x) wrapDegrees(x)              vanilla MathHelper (replicated below)
 *   TW kind a power result                               Tween.easeIn (0) / easeOut (1) / easeInOut (2), doubles
 *   QM left4 right4 mul(l,r,new)4 mul(l,r,l)4 mul(l,r,r)4 Quaternion.mul, including both aliasing cases
 *   QR q4 x y z angle result4                            Quaternion.rotate
 *   QA x y z angle result4                               Quaternion.setFromAxisAngle
 *   QN q4 normalised4 length lengthSquared               Quaternion.normalise / length / lengthSquared
 *   QV v3 q4 multiply(v,q,new)3 multiply(v,q,v)3          QuaternionUtils.multiply, including dest == vector
 *   QG q4 m16                                            QuaternionUtils.quatToGlMatrix
 *   GU function args... result                           GUtil
 *   SO step instance op args... = start4 end4 smooth4 progress smoothness
 *   SV step instance op args... = start3 end3 smoothness3 completion3 getX getY getZ
 *   AF file bones / AB file name keyframes / AK file name index position3 rotation4 scale3
 *   END lines
 */
public final class MathParity
{

    /**
     * net.minecraft.util.math.MathHelper from vanilla Minecraft 1.12.2. It is not part of the mod, so the three
     * members the animations use are replicated exactly (65536-entry float table, float index arithmetic).
     */
    static final class VanillaMathHelper
    {
        private static final float[] SIN_TABLE = new float[65536];

        static
        {
            for (int i = 0; i < 65536; ++i)
            {
                SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
            }
        }

        static float sin(float value)
        {
            return SIN_TABLE[(int) (value * 10430.378F) & 65535];
        }

        static float cos(float value)
        {
            return SIN_TABLE[(int) (value * 10430.378F + 16384.0F) & 65535];
        }

        static float wrapDegrees(float value)
        {
            value = value % 360.0F;

            if (value >= 180.0F)
            {
                value -= 360.0F;
            }

            if (value < -180.0F)
            {
                value += 360.0F;
            }

            return value;
        }
    }

    /** Deterministic xorshift32, so every run (and every machine) produces the same sequence. */
    static final class Rng
    {
        private int s;

        Rng(int seed)
        {
            s = seed == 0 ? 0x6D2B79F5 : seed;
        }

        int next()
        {
            int x = s;
            x ^= x << 13;
            x ^= x >>> 17;
            x ^= x << 5;
            s = x;
            return x;
        }

        int below(int n)
        {
            return (next() >>> 1) % n;
        }

        /** Uniform in [0, 1) with a full 24-bit mantissa. */
        float unit()
        {
            return (next() >>> 8) * 0x1p-24f;
        }

        float range(float lo, float hi)
        {
            return lo + (hi - lo) * unit();
        }

        /** Uniform in [0, 1) with 52 random bits. */
        double unitD()
        {
            int a = next() >>> 6;
            int b = next() >>> 6;
            return (a * 67108864.0 + b) * 0x1p-52;
        }

        double rangeD(double lo, double hi)
        {
            return lo + (hi - lo) * unitD();
        }
    }

    /** Exposes the protected interpolation state of the original class; behaviour is inherited unchanged. */
    static final class ProbeOrientation extends SmoothOrientation
    {
        Quaternion startQuat()
        {
            return this.start;
        }

        float progressValue()
        {
            return this.progress;
        }

        float smoothnessValue()
        {
            return this.smoothness;
        }
    }

    private static final int GOLDEN = 0x9E3779B9;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final String[] SO_OPS = {
            "orient", "orientX", "orientY", "orientZ",
            "rotate", "rotateX", "rotateY", "rotateZ",
            "localRotate", "localRotateX", "localRotateY", "localRotateZ",
            "rotateInstant", "rotateInstantX", "rotateInstantY", "rotateInstantZ",
            "orientInstant", "orientInstantX", "orientInstantY", "orientInstantZ",
            "orientZero", "identity", "finish", "setSmoothness", "set", "add", "update", "copy"
    };

    private static final String[] SV_OPS = {
            "slideTo", "slideToZero", "slideToZeroS", "slideX", "slideY", "slideZ", "slideXS", "slideYS", "slideZS",
            "setX", "setY", "setZ", "set", "add", "update", "finish", "limitDistanceTo", "copy"
    };

    private final Writer out;
    private final StringBuilder line = new StringBuilder(512);
    private long lines;

    private MathParity(Writer out)
    {
        this.out = out;
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 2)
        {
            System.err.println("usage: java mobends.MathParity seed steps [animation files...]");
            System.exit(2);
        }
        int seed = Integer.parseInt(args[0]);
        int steps = Integer.parseInt(args[1]);
        Writer writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII), 1 << 16);
        MathParity parity = new MathParity(writer);
        parity.mathHelper(new Rng(seed + GOLDEN));
        parity.tween(new Rng(seed + 2 * GOLDEN), steps);
        parity.quaternions(new Rng(seed + 3 * GOLDEN), steps);
        parity.gutil(new Rng(seed + 4 * GOLDEN), steps);
        parity.smoothOrientation(new Rng(seed + 5 * GOLDEN), steps);
        parity.smoothVector(new Rng(seed + 6 * GOLDEN), steps);
        for (int i = 2; i < args.length; ++i)
        {
            parity.animation(i - 2, args[i]);
        }
        parity.tag("END").i(parity.lines + 1).end();
        writer.flush();
    }

    // ---- MathHelper (vanilla) -------------------------------------------------------------------------------

    private void mathHelper(Rng r) throws IOException
    {
        // One argument in the middle of every table cell: any sane index computation picks cell i (sin) and
        // cell i + 16384 (cos) here, so this group compares the 65536 table entries themselves.
        for (int i = 0; i < 65536; ++i)
        {
            mh("cell", (float) ((i + 0.5) / 10430.3779296875));
        }
        float pi = (float) Math.PI;
        float[] specials = {
                0f, -0f, Float.MIN_VALUE, -Float.MIN_VALUE, 1e-30f, 1f, -1f, 0.5f, pi, -pi, pi / 2, 2 * pi,
                179.99998f, 180f, -180f, -180.00002f, 359.99997f, 360f, -360f, 540f, -540f, 720f, 1e4f, -1e4f,
                804.2f, 8388608f, -8388608f, 1e7f, 1e9f, -1e9f, 3e34f, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN
        };
        for (float x : specials)
        {
            mh("special", x);
        }
        String[] groups = {"r8", "r64", "r1e3", "r1e5", "r1e7"};
        float[] limits = {8f, 64f, 1000f, 100000f, 1e7f};
        for (int k = 0; k < groups.length; ++k)
        {
            for (int n = 0; n < 4000; ++n)
            {
                mh(groups[k], r.range(-limits[k], limits[k]));
            }
        }
        // Game-like arguments: a tick counter plus a partial tick (MathHelper.sin(ticks), ticks * 0.3F ...).
        for (int n = 0; n < 4000; ++n)
        {
            float ticks = (float) r.below(200000) + r.unit();
            mh("ticks", ticks);
        }
    }

    private void mh(String group, float x) throws IOException
    {
        tag("MH").s(group).f(x)
                .f(VanillaMathHelper.sin(x))
                .f(VanillaMathHelper.cos(x))
                .f(VanillaMathHelper.wrapDegrees(x))
                .end();
    }

    // ---- Tween ----------------------------------------------------------------------------------------------

    private void tween(Rng r, int steps) throws IOException
    {
        double[] powers = {0, 0.5, 1, 2, 3, 4};
        double[] edges = {0, 0.25, 0.5, 0.75, 1};
        for (double a : edges)
        {
            for (double p : powers)
            {
                for (int kind = 0; kind < 3; ++kind)
                {
                    tw(kind, a, p);
                }
            }
        }
        for (int n = 0; n < steps; ++n)
        {
            int kind = r.below(3);
            double a = r.below(10) == 0 ? r.rangeD(-0.25, 1.25) : r.unitD();
            double p = r.below(3) == 0 ? powers[r.below(powers.length)] : r.rangeD(0.25, 4);
            tw(kind, a, p);
        }
    }

    private void tw(int kind, double a, double p) throws IOException
    {
        double v = kind == 0 ? Tween.easeIn(a, p) : kind == 1 ? Tween.easeOut(a, p) : Tween.easeInOut(a, p);
        tag("TW").i(kind).d(a).d(p).d(v).end();
    }

    // ---- Quaternion / QuaternionUtils -----------------------------------------------------------------------

    private void quaternions(Rng r, int steps) throws IOException
    {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        for (int n = 0; n < steps; ++n)
        {
            Quaternion left = randomQuat(r, 1.5f);
            Quaternion right = randomQuat(r, 1.5f);
            Quaternion fresh = new Quaternion();
            Quaternion.mul(left, right, fresh);
            Quaternion aliasLeft = copyOf(left);
            Quaternion.mul(aliasLeft, right, aliasLeft);
            Quaternion aliasRight = copyOf(right);
            Quaternion.mul(left, aliasRight, aliasRight);
            tag("QM").q(left).q(right).q(fresh).q(aliasLeft).q(aliasRight).end();

            Quaternion q = randomQuat(r, 1.2f);
            float x = r.range(-2f, 2f), y = r.range(-2f, 2f), z = r.range(-2f, 2f), angle = r.range(-7f, 7f);
            tag("QR").q(q).f(x).f(y).f(z).f(angle);
            q.rotate(x, y, z, angle);
            q(q).end();

            float ax = r.range(-2f, 2f), ay = r.range(-2f, 2f), az = r.range(-2f, 2f), aAngle = r.range(-7f, 7f);
            Quaternion fromAxis = new Quaternion();
            fromAxis.setFromAxisAngle(ax, ay, az, aAngle);
            tag("QA").f(ax).f(ay).f(az).f(aAngle).q(fromAxis).end();

            Quaternion m = randomQuat(r, 2f);
            tag("QN").q(m);
            float length = m.length();
            float lengthSquared = m.lengthSquared();
            m.normalise();
            q(m).f(length).f(lengthSquared).end();

            Vec3f vector = new Vec3f(r.range(-20f, 20f), r.range(-20f, 20f), r.range(-20f, 20f));
            Quaternion rotation = randomQuat(r, 1f);
            if (r.below(2) == 0)
            {
                rotation.normalise();
            }
            Vec3f dest = new Vec3f();
            QuaternionUtils.multiply(vector, rotation, dest);
            Vec3f alias = new Vec3f(vector.x, vector.y, vector.z);
            QuaternionUtils.multiply(alias, rotation, alias);
            tag("QV").v(vector).q(rotation).v(dest).v(alias).end();

            QuaternionUtils.quatToGlMatrix(buffer, rotation);
            tag("QG").q(rotation);
            for (int i = 0; i < 16; ++i)
            {
                f(buffer.get(i));
            }
            end();
        }
    }

    private static Quaternion randomQuat(Rng r, float limit)
    {
        float x = r.range(-limit, limit), y = r.range(-limit, limit), z = r.range(-limit, limit), w = r.range(-limit, limit);
        return new Quaternion(x, y, z, w);
    }

    private static Quaternion copyOf(Quaternion q)
    {
        return new Quaternion(q.x, q.y, q.z, q.w);
    }

    // ---- GUtil ----------------------------------------------------------------------------------------------

    private void gutil(Rng r, int steps) throws IOException
    {
        double[][] atanSpecials = {{0, 0}, {-0.0, 0}, {0, -0.0}, {-0.0, -0.0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {3, -3}};
        for (double[] xz : atanSpecials)
        {
            tag("GU").s("angleFromCoordinates").d(xz[0]).d(xz[1]).d(GUtil.angleFromCoordinates(xz[0], xz[1])).end();
        }
        for (int n = 0; n < steps; ++n)
        {
            double x = r.rangeD(-10, 10), z = r.rangeD(-10, 10);
            tag("GU").s("angleFromCoordinates").d(x).d(z).d(GUtil.angleFromCoordinates(x, z)).end();

            double a = r.rangeD(-50, 50);
            tag("GU").s("wrapRadians").d(a).d(GUtil.wrapRadians(a)).end();

            double b = r.rangeD(-50, 50), c = r.rangeD(-50, 50);
            tag("GU").s("getRadianDifference").d(b).d(c).d(GUtil.getRadianDifference(b, c)).end();

            float from = r.range(-720f, 720f), to = r.range(-720f, 720f), partialTicks = r.unit();
            tag("GU").s("interpolateRotation").f(from).f(to).f(partialTicks)
                    .f(GUtil.interpolateRotation(from, to, partialTicks)).end();

            float la = r.range(-100f, 100f), lb = r.range(-100f, 100f), slide = r.range(-0.5f, 1.5f);
            tag("GU").s("lerp").f(la).f(lb).f(slide).f(GUtil.lerp(la, lb, slide)).end();

            float value = r.range(-10f, 10f), min = r.range(-5f, 0f), max = r.range(0f, 5f);
            tag("GU").s("clamp").f(value).f(min).f(max).f(GUtil.clamp(value, min, max)).end();
        }
    }

    // ---- SmoothOrientation ----------------------------------------------------------------------------------

    private void smoothOrientation(Rng r, int steps) throws IOException
    {
        ProbeOrientation[] all = new ProbeOrientation[4];
        for (int i = 0; i < all.length; ++i)
        {
            all[i] = new ProbeOrientation();
        }
        for (int step = 0; step < steps; ++step)
        {
            int instance = r.below(all.length);
            int pick = r.below(SO_OPS.length + 12); // the extra slots are update() calls: frames dominate in game
            String op = pick < SO_OPS.length ? SO_OPS[pick] : "update";
            ProbeOrientation o = all[instance];
            tag("SO").i(step).i(instance).s(op);
            switch (op)
            {
                case "orient":
                {
                    float a = angle(r), x = axis(r), y = axis(r), z = axis(r);
                    f(a).f(x).f(y).f(z);
                    o.orient(a, x, y, z);
                    break;
                }
                case "orientX":
                {
                    float a = angle(r);
                    f(a);
                    o.orientX(a);
                    break;
                }
                case "orientY":
                {
                    float a = angle(r);
                    f(a);
                    o.orientY(a);
                    break;
                }
                case "orientZ":
                {
                    float a = angle(r);
                    f(a);
                    o.orientZ(a);
                    break;
                }
                case "rotate":
                {
                    float a = angle(r), x = axis(r), y = axis(r), z = axis(r);
                    f(a).f(x).f(y).f(z);
                    o.rotate(a, x, y, z);
                    break;
                }
                case "rotateX":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateX(a);
                    break;
                }
                case "rotateY":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateY(a);
                    break;
                }
                case "rotateZ":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateZ(a);
                    break;
                }
                case "localRotate":
                {
                    float a = angle(r), x = axis(r), y = axis(r), z = axis(r);
                    f(a).f(x).f(y).f(z);
                    o.localRotate(a, x, y, z);
                    break;
                }
                case "localRotateX":
                {
                    float a = angle(r);
                    f(a);
                    o.localRotateX(a);
                    break;
                }
                case "localRotateY":
                {
                    float a = angle(r);
                    f(a);
                    o.localRotateY(a);
                    break;
                }
                case "localRotateZ":
                {
                    float a = angle(r);
                    f(a);
                    o.localRotateZ(a);
                    break;
                }
                case "rotateInstant":
                {
                    float a = angle(r), x = axis(r), y = axis(r), z = axis(r);
                    f(a).f(x).f(y).f(z);
                    o.rotateInstant(a, x, y, z);
                    break;
                }
                case "rotateInstantX":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateInstantX(a);
                    break;
                }
                case "rotateInstantY":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateInstantY(a);
                    break;
                }
                case "rotateInstantZ":
                {
                    float a = angle(r);
                    f(a);
                    o.rotateInstantZ(a);
                    break;
                }
                case "orientInstant":
                {
                    float a = angle(r), x = axis(r), y = axis(r), z = axis(r);
                    f(a).f(x).f(y).f(z);
                    o.orientInstant(a, x, y, z);
                    break;
                }
                case "orientInstantX":
                {
                    float a = angle(r);
                    f(a);
                    o.orientInstantX(a);
                    break;
                }
                case "orientInstantY":
                {
                    float a = angle(r);
                    f(a);
                    o.orientInstantY(a);
                    break;
                }
                case "orientInstantZ":
                {
                    float a = angle(r);
                    f(a);
                    o.orientInstantZ(a);
                    break;
                }
                case "orientZero":
                    o.orientZero();
                    break;
                case "identity":
                    o.identity();
                    break;
                case "finish":
                    o.finish();
                    break;
                case "setSmoothness":
                {
                    float s = smoothness(r);
                    f(s);
                    o.setSmoothness(s);
                    break;
                }
                case "set":
                {
                    // A sixth of the time the all-zero reset KeyframeAnimationLayer uses before additive keyframes.
                    boolean zero = r.below(6) == 0;
                    float x = zero ? 0f : r.range(-1f, 1f), y = zero ? 0f : r.range(-1f, 1f);
                    float z = zero ? 0f : r.range(-1f, 1f), w = zero ? 0f : r.range(-1f, 1f);
                    f(x).f(y).f(z).f(w);
                    o.set(x, y, z, w);
                    break;
                }
                case "add":
                {
                    float x = r.range(-0.6f, 0.6f), y = r.range(-0.6f, 0.6f), z = r.range(-0.6f, 0.6f), w = r.range(-0.6f, 0.6f);
                    f(x).f(y).f(z).f(w);
                    o.add(x, y, z, w);
                    break;
                }
                case "update":
                {
                    float t = ticksPerFrame(r);
                    f(t);
                    o.update(t);
                    break;
                }
                case "copy":
                {
                    int other = r.below(all.length);
                    i(other);
                    o.set(all[other]);
                    break;
                }
                default:
                    throw new IllegalStateException(op);
            }
            s("=").q(o.startQuat()).q(o.getEnd()).q(o.getSmooth()).f(o.progressValue()).f(o.smoothnessValue()).end();
        }
    }

    /** Mostly within half a turn, sometimes beyond a full turn. */
    private static float angle(Rng r)
    {
        return r.below(8) == 0 ? r.range(-400f, 400f) : r.range(-180f, 180f);
    }

    /** One component of an arbitrary, not normalised rotation axis. */
    private static float axis(Rng r)
    {
        return r.range(-1f, 1f);
    }

    private static float smoothness(Rng r)
    {
        return r.range(0.05f, 1.5f);
    }

    /** DataUpdateHandler.ticksPerFrame: anything in [0, 1], including both ends exactly. */
    private static float ticksPerFrame(Rng r)
    {
        int k = r.below(8);
        return k == 0 ? 0f : k == 1 ? 1f : r.unit();
    }

    // ---- SmoothVector3f -------------------------------------------------------------------------------------

    private void smoothVector(Rng r, int steps) throws IOException
    {
        SmoothVector3f[] all = new SmoothVector3f[4];
        float[][] last = new float[all.length][3];
        boolean[][] hasLast = new boolean[all.length][3];
        for (int i = 0; i < all.length; ++i)
        {
            all[i] = new SmoothVector3f();
        }
        for (int step = 0; step < steps; ++step)
        {
            int instance = r.below(all.length);
            int pick = r.below(SV_OPS.length + 8); // the extra slots are update() calls
            String op = pick < SV_OPS.length ? SV_OPS[pick] : "update";
            SmoothVector3f v = all[instance];
            float[] lastTarget = last[instance];
            boolean[] has = hasLast[instance];
            tag("SV").i(step).i(instance).s(op);
            switch (op)
            {
                case "slideTo":
                {
                    float x = target(r, lastTarget, has, 0), y = target(r, lastTarget, has, 1), z = target(r, lastTarget, has, 2);
                    float s = smoothness(r);
                    f(x).f(y).f(z).f(s);
                    v.slideTo(x, y, z, s);
                    break;
                }
                case "slideToZero":
                    remember(lastTarget, has, 0f, 0f, 0f);
                    v.slideToZero();
                    break;
                case "slideToZeroS":
                {
                    float s = smoothness(r);
                    f(s);
                    remember(lastTarget, has, 0f, 0f, 0f);
                    v.slideToZero(s);
                    break;
                }
                case "slideX":
                {
                    float x = target(r, lastTarget, has, 0);
                    f(x);
                    v.slideX(x);
                    break;
                }
                case "slideY":
                {
                    float y = target(r, lastTarget, has, 1);
                    f(y);
                    v.slideY(y);
                    break;
                }
                case "slideZ":
                {
                    float z = target(r, lastTarget, has, 2);
                    f(z);
                    v.slideZ(z);
                    break;
                }
                case "slideXS":
                {
                    float x = target(r, lastTarget, has, 0), s = smoothness(r);
                    f(x).f(s);
                    v.slideX(x, s);
                    break;
                }
                case "slideYS":
                {
                    float y = target(r, lastTarget, has, 1), s = smoothness(r);
                    f(y).f(s);
                    v.slideY(y, s);
                    break;
                }
                case "slideZS":
                {
                    float z = target(r, lastTarget, has, 2), s = smoothness(r);
                    f(z).f(s);
                    v.slideZ(z, s);
                    break;
                }
                case "setX":
                {
                    float x = r.range(-30f, 30f);
                    f(x);
                    lastTarget[0] = x;
                    has[0] = true;
                    v.setX(x);
                    break;
                }
                case "setY":
                {
                    float y = r.range(-30f, 30f);
                    f(y);
                    lastTarget[1] = y;
                    has[1] = true;
                    v.setY(y);
                    break;
                }
                case "setZ":
                {
                    float z = r.range(-30f, 30f);
                    f(z);
                    lastTarget[2] = z;
                    has[2] = true;
                    v.setZ(z);
                    break;
                }
                case "set":
                {
                    float x = r.range(-30f, 30f), y = r.range(-30f, 30f), z = r.range(-30f, 30f);
                    f(x).f(y).f(z);
                    remember(lastTarget, has, x, y, z);
                    v.set(x, y, z);
                    break;
                }
                case "add":
                {
                    float x = r.range(-2f, 2f), y = r.range(-2f, 2f), z = r.range(-2f, 2f);
                    f(x).f(y).f(z);
                    v.add(x, y, z);
                    break;
                }
                case "update":
                {
                    float t = ticksPerFrame(r);
                    f(t);
                    v.update(t);
                    break;
                }
                case "finish":
                    v.finish();
                    break;
                case "limitDistanceTo":
                {
                    int other = r.below(all.length);
                    float maxDistance = r.range(0.5f, 20f);
                    i(other).f(maxDistance);
                    v.limitDistanceTo(all[other], maxDistance);
                    break;
                }
                case "copy":
                {
                    int other = r.below(all.length);
                    i(other);
                    v.set(all[other]);
                    break;
                }
                default:
                    throw new IllegalStateException(op);
            }
            s("=").v(v.start).v(v.end).v(v.smoothness).v(v.completion).f(v.getX()).f(v.getY()).f(v.getZ()).end();
        }
    }

    /** A slide target; a third of the time the previous target of that axis again (the "already there" branch). */
    private static float target(Rng r, float[] last, boolean[] has, int axis)
    {
        boolean repeat = r.below(3) == 0 && has[axis];
        float t = repeat ? last[axis] : r.range(-30f, 30f);
        last[axis] = t;
        has[axis] = true;
        return t;
    }

    private static void remember(float[] last, boolean[] has, float x, float y, float z)
    {
        last[0] = x;
        last[1] = y;
        last[2] = z;
        has[0] = has[1] = has[2] = true;
    }

    // ---- BinaryAnimationLoader ------------------------------------------------------------------------------

    private void animation(int index, String file) throws IOException
    {
        KeyframeAnimation animation;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file)))
        {
            animation = BinaryAnimationLoader.loadFromBinaryInputStream(in);
        }
        List<String> names = new ArrayList<>(animation.bones.keySet());
        Collections.sort(names);
        tag("AF").i(index).i(animation.bones.size()).end();
        for (String name : names)
        {
            Bone bone = animation.bones.get(name);
            String id = "n" + hexUtf8(name);
            tag("AB").i(index).s(id).i(bone.keyframes.size()).end();
            for (int j = 0; j < bone.keyframes.size(); ++j)
            {
                Keyframe keyframe = bone.keyframes.get(j);
                tag("AK").i(index).s(id).i(j);
                for (float value : keyframe.position)
                {
                    f(value);
                }
                for (float value : keyframe.rotation)
                {
                    f(value);
                }
                for (float value : keyframe.scale)
                {
                    f(value);
                }
                end();
            }
        }
    }

    private static String hexUtf8(String text)
    {
        StringBuilder hex = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8))
        {
            hex.append(HEX[(b >>> 4) & 15]).append(HEX[b & 15]);
        }
        return hex.toString();
    }

    // ---- output ---------------------------------------------------------------------------------------------

    private MathParity tag(String tag)
    {
        line.setLength(0);
        line.append(tag);
        return this;
    }

    private MathParity s(String token)
    {
        line.append(' ').append(token);
        return this;
    }

    private MathParity i(long value)
    {
        line.append(' ').append(value);
        return this;
    }

    private MathParity f(float value)
    {
        int bits = Float.floatToRawIntBits(value);
        line.append(' ');
        for (int shift = 28; shift >= 0; shift -= 4)
        {
            line.append(HEX[(bits >>> shift) & 15]);
        }
        return this;
    }

    private MathParity d(double value)
    {
        long bits = Double.doubleToRawLongBits(value);
        line.append(' ');
        for (int shift = 60; shift >= 0; shift -= 4)
        {
            line.append(HEX[(int) (bits >>> shift) & 15]);
        }
        return this;
    }

    private MathParity q(Quaternion q)
    {
        return f(q.x).f(q.y).f(q.z).f(q.w);
    }

    private MathParity v(Vec3f v)
    {
        return f(v.x).f(v.y).f(v.z);
    }

    private void end() throws IOException
    {
        line.append('\n');
        out.append(line);
        ++lines;
    }

}
