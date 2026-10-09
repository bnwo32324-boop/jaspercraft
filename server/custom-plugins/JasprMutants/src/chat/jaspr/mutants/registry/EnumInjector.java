package chat.jaspr.mutants.registry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Adds enum constants the way Forge's EnumHelper does (the enum's own constructor runs, the constant is appended to
 * values()), on Java 17: the constructor is called through a private method handle, values() is replaced through
 * Unsafe, the JDK's cached enum universe (used by EnumSet/EnumMap) is refreshed if it was already computed, and the
 * switch tables javac generated in the server jar are widened so a switch over the enum never indexes past its end.
 */
public final class EnumInjector {
    static Logger log = Logger.getLogger("JasprMutants");
    private static final List<String> REPORT = new ArrayList<String>();

    private EnumInjector() {
    }

    public static List<String> report() {
        return REPORT;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<?>> T addEnum(Class<T> enumType, String enumName, Class<?>[] paramTypes, Object[] paramValues) {
        try {
            Method valuesMethod = enumType.getMethod("values");
            Object[] values = (Object[]) valuesMethod.invoke(null);
            for (Object v : values) {
                if (((Enum<?>) v).name().equals(enumName)) {
                    return (T) v;
                }
            }
            int ordinal = values.length;
            Class<?>[] ctorTypes = new Class<?>[paramTypes.length + 2];
            ctorTypes[0] = String.class;
            ctorTypes[1] = int.class;
            System.arraycopy(paramTypes, 0, ctorTypes, 2, paramTypes.length);
            MethodHandle ctor = privateLookup(enumType).findConstructor(enumType, MethodType.methodType(void.class, ctorTypes));
            Object[] args = new Object[paramValues.length + 2];
            args[0] = enumName;
            args[1] = ordinal;
            System.arraycopy(paramValues, 0, args, 2, paramValues.length);
            T value = (T) ctor.invokeWithArguments(args);
            Field valuesField = valuesArrayField(enumType);
            Object[] newValues = (Object[]) Array.newInstance(enumType, ordinal + 1);
            System.arraycopy(values, 0, newValues, 0, ordinal);
            newValues[ordinal] = value;
            UnsafeAccess.putStatic(valuesField, newValues);
            Object[] check = (Object[]) valuesMethod.invoke(null);
            if (check.length != ordinal + 1 || check[ordinal] != value) {
                throw new IllegalStateException("values() of " + enumType.getName() + " did not change");
            }
            refreshUniverse(enumType);
            return value;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Could not add enum constant " + enumName + " to " + enumType.getName(), t);
        }
    }

    private static MethodHandles.Lookup privateLookup(Class<?> target) throws ReflectiveOperationException {
        try {
            Method m = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
            return (MethodHandles.Lookup) m.invoke(null, target, MethodHandles.lookup());
        } catch (NoSuchMethodException java8) {
            java.lang.reflect.Constructor<MethodHandles.Lookup> c = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
            c.setAccessible(true);
            return c.newInstance(target, -1);
        }
    }

    private static Field valuesArrayField(Class<?> enumType) {
        for (Field f : enumType.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && f.getType().isArray() && f.getType().getComponentType() == enumType
                    && (f.isSynthetic() || f.getName().equals("$VALUES") || f.getName().equals("ENUM$VALUES"))) {
                return f;
            }
        }
        throw new IllegalStateException("No values array in " + enumType.getName());
    }

    /**
     * The JDK caches an enum's constants in its Class object the first time EnumSet/EnumMap/valueOf need them. If that
     * happened before a constant was added, EnumSet.contains(newConstant) would index past the cached universe. This
     * finds the cache reference by comparing raw reference bits (no object is ever read from an unknown slot) and clears
     * it, so the next use recomputes it from values().
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean refreshUniverse(Class<?> enumType) {
        try {
            int current = ((Object[]) enumType.getMethod("values").invoke(null)).length;
            Field universeField = EnumSet.class.getDeclaredField("universe");
            long universeOffset = UnsafeAccess.objectFieldOffset(universeField);
            for (int attempt = 0; attempt < 3; attempt++) {
                EnumSet set = EnumSet.noneOf((Class) enumType);
                Object[] universe = (Object[]) readReference(set, universeOffset);
                if (universe.length == current) {
                    return true;
                }
                long slot = findReferenceSlot(enumType, universe, 768);
                if (slot < 0) continue;
                UnsafeAccess.putObject(enumType, slot, null);
                EnumSet again = EnumSet.noneOf((Class) enumType);
                if (((Object[]) readReference(again, universeOffset)).length == current) {
                    REPORT.add("enum-cache-refreshed " + enumType.getSimpleName());
                    return true;
                }
            }
            REPORT.add("enum-cache-STALE " + enumType.getSimpleName());
            log.warning("MUTANTS_ENUM_CACHE_STALE enum=" + enumType.getSimpleName());
            return false;
        } catch (Throwable t) {
            REPORT.add("enum-cache-check-failed " + enumType.getSimpleName() + " " + t.getClass().getSimpleName());
            log.warning("MUTANTS_ENUM_CACHE_CHECK_FAILED enum=" + enumType.getSimpleName() + " error=" + t.getClass().getSimpleName());
            return false;
        }
    }

    private static Object readReference(Object holder, long offset) {
        // EnumSet.universe is a declared reference field, so reading it as an object is safe.
        return UnsafeAccess.getObject(holder, offset);
    }

    /** Offset of the slot in holder whose raw reference bits equal target's, or -1. */
    private static long findReferenceSlot(Object holder, Object target, int maxBytes) {
        Object[] probe = new Object[]{target};
        int scale = UnsafeAccess.arrayIndexScale(Object[].class);
        long base = UnsafeAccess.arrayBaseOffset(Object[].class);
        if (scale == 4) {
            int bits = UnsafeAccess.getInt(probe, base);
            for (long off = 8; off < maxBytes; off += 4) {
                if (UnsafeAccess.getInt(holder, off) == bits && UnsafeAccess.getInt(probe, base) == bits) return off;
            }
        } else {
            long bits = UnsafeAccess.getLong(probe, base);
            for (long off = 8; off < maxBytes; off += 8) {
                if (UnsafeAccess.getLong(holder, off) == bits && UnsafeAccess.getLong(probe, base) == bits) return off;
            }
        }
        return -1;
    }

    /**
     * Widens every javac switch table for enumType declared by classes in the given jar (classes are named Outer$N and
     * declare static int[] $SwitchMap$...). Tables made before a constant was added are shorter than values(); a switch
     * on the new constant would throw. New entries are 0 (no case), exactly what a fresh table holds for it.
     */
    public static int widenSwitchMaps(Class<?> enumType, File jar, ClassLoader loader) {
        String fieldPrefix = "$SwitchMap$" + enumType.getName().replace('.', '$');
        byte[] needle = fieldPrefix.getBytes(StandardCharsets.UTF_8);
        int patched = 0;
        int length;
        try {
            length = ((Object[]) enumType.getMethod("values").invoke(null)).length;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class") || !name.matches(".*\\$[0-9]+\\.class")) continue;
                byte[] bytes;
                try (InputStream in = jf.getInputStream(e)) {
                    bytes = readAll(in);
                }
                if (indexOf(bytes, needle) < 0) continue;
                String binary = name.substring(0, name.length() - 6).replace('/', '.');
                Class<?> holder;
                try {
                    holder = Class.forName(binary, false, loader);
                } catch (Throwable t) {
                    continue;
                }
                for (Field f : holder.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int[].class || !f.getName().startsWith(fieldPrefix)) continue;
                    try {
                        f.setAccessible(true);
                        int[] table = (int[]) f.get(null);
                        if (table != null && table.length < length) {
                            UnsafeAccess.putStatic(f, Arrays.copyOf(table, length));
                            patched++;
                        }
                    } catch (Throwable t) {
                        REPORT.add("switch-map-skip " + binary + " " + t.getClass().getSimpleName());
                    }
                }
            }
        } catch (IOException e) {
            REPORT.add("switch-map-scan-failed " + e.getClass().getSimpleName());
        }
        REPORT.add("switch-maps-widened " + enumType.getSimpleName() + "=" + patched);
        return patched;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
