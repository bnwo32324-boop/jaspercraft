package net.minecraftforge.fml.common.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** JasperCraft port shim of FML's GameRegistry (only @ObjectHolder is used; chat.jaspr.mutants.registry.ObjectHolders applies it). */
public class GameRegistry {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD})
    public @interface ObjectHolder {
        String value();
    }
}
