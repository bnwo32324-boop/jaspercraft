package chat.jaspr.mutants.registry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Reflective access to sun.misc.Unsafe (module jdk.unsupported, exported on Java 9+, plain on Java 8). Used only for
 * what Forge's EnumHelper does with reflection on Java 8: replacing an enum's values() array and refreshing the JDK's
 * enum constant cache when it was computed before a constant was added.
 */
final class UnsafeAccess {
    private static final MethodHandle OBJECT_FIELD_OFFSET;
    private static final MethodHandle STATIC_FIELD_BASE;
    private static final MethodHandle STATIC_FIELD_OFFSET;
    private static final MethodHandle PUT_OBJECT;
    private static final MethodHandle GET_OBJECT;
    private static final MethodHandle GET_INT;
    private static final MethodHandle GET_LONG;
    private static final MethodHandle ARRAY_BASE_OFFSET;
    private static final MethodHandle ARRAY_INDEX_SCALE;
    private static final MethodHandle ALLOCATE_INSTANCE;

    static {
        try {
            Class<?> c = Class.forName("sun.misc.Unsafe");
            Field f = c.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object u = f.get(null);
            MethodHandles.Lookup l = MethodHandles.lookup();
            OBJECT_FIELD_OFFSET = l.unreflect(c.getMethod("objectFieldOffset", Field.class)).bindTo(u);
            STATIC_FIELD_BASE = l.unreflect(c.getMethod("staticFieldBase", Field.class)).bindTo(u);
            STATIC_FIELD_OFFSET = l.unreflect(c.getMethod("staticFieldOffset", Field.class)).bindTo(u);
            PUT_OBJECT = l.unreflect(c.getMethod("putObject", Object.class, long.class, Object.class)).bindTo(u);
            GET_OBJECT = l.unreflect(c.getMethod("getObject", Object.class, long.class)).bindTo(u);
            GET_INT = l.unreflect(c.getMethod("getInt", Object.class, long.class)).bindTo(u);
            GET_LONG = l.unreflect(c.getMethod("getLong", Object.class, long.class)).bindTo(u);
            ARRAY_BASE_OFFSET = l.unreflect(c.getMethod("arrayBaseOffset", Class.class)).bindTo(u);
            ARRAY_INDEX_SCALE = l.unreflect(c.getMethod("arrayIndexScale", Class.class)).bindTo(u);
            ALLOCATE_INSTANCE = l.unreflect(c.getMethod("allocateInstance", Class.class)).bindTo(u);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private UnsafeAccess() {
    }

    static long objectFieldOffset(Field f) {
        try {
            return (long) OBJECT_FIELD_OFFSET.invoke(f);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void putStatic(Field f, Object value) {
        try {
            Object base = STATIC_FIELD_BASE.invoke(f);
            long off = (long) STATIC_FIELD_OFFSET.invoke(f);
            PUT_OBJECT.invoke(base, off, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void putObject(Object o, long offset, Object value) {
        try {
            PUT_OBJECT.invoke(o, offset, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Only for offsets of declared reference fields (never for an unknown slot). */
    static Object getObject(Object o, long offset) {
        try {
            return GET_OBJECT.invoke(o, offset);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int getInt(Object o, long offset) {
        try {
            return (int) GET_INT.invoke(o, offset);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long getLong(Object o, long offset) {
        try {
            return (long) GET_LONG.invoke(o, offset);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int arrayBaseOffset(Class<?> arrayClass) {
        try {
            return (int) ARRAY_BASE_OFFSET.invoke(arrayClass);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int arrayIndexScale(Class<?> arrayClass) {
        try {
            return (int) ARRAY_INDEX_SCALE.invoke(arrayClass);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Object allocateInstance(Class<?> c) {
        try {
            return ALLOCATE_INSTANCE.invoke(c);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) return (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        return new IllegalStateException(t);
    }
}
