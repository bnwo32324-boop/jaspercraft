package net.minecraftforge.fml.common;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * JasperCraft port shim of FML's ObfuscationReflectionHelper. FML resolves SRG names at runtime; Paper uses Spigot names,
 * so the SRG names the mod passes are translated with the toolchain's names-1.12.2.tsv (rows below; only the names this
 * mod uses). Unknown names are tried as given.
 */
public class ObfuscationReflectionHelper {
    private static final Map<String, String> SRG_TO_SPIGOT = new HashMap<String, String>();

    static {
        // F net/minecraft/util/EnumParticleTypes PARTICLES -> EnumParticle.ab (field_179365_U)
        SRG_TO_SPIGOT.put("field_179365_U", "ab");
        // F net/minecraft/util/EnumParticleTypes BY_NAME -> EnumParticle.ac (field_186837_Z)
        SRG_TO_SPIGOT.put("field_186837_Z", "ac");
        // F net/minecraft/entity/Entity fire -> Entity.fireTicks (field_190534_ay)
        SRG_TO_SPIGOT.put("field_190534_ay", "fireTicks");
    }

    public static String remap(String srgName) {
        String name = SRG_TO_SPIGOT.get(srgName);
        return name == null ? srgName : name;
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(remap(fieldName));
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            throw new ReflectionHelper.UnableToFindFieldException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, String fieldName) {
        try {
            return (T) findField(classToAccess, fieldName).get(instance);
        } catch (ReflectiveOperationException e) {
            throw new ReflectionHelper.UnableToAccessFieldException(new String[]{fieldName}, e);
        }
    }

    public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, String fieldName) {
        try {
            findField(classToAccess, fieldName).set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new ReflectionHelper.UnableToAccessFieldException(new String[]{fieldName}, e);
        }
    }

    /** Minimal FML ReflectionHelper exception types. */
    public static class ReflectionHelper {
        public static class UnableToFindFieldException extends RuntimeException {
            public UnableToFindFieldException(Exception e) {
                super(e);
            }
        }

        public static class UnableToAccessFieldException extends RuntimeException {
            public UnableToAccessFieldException(String[] names, Exception e) {
                super(e);
            }
        }
    }
}
