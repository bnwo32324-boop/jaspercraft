package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraftforge.fml.relauncher.Side;

/**
 * JasperCraft port shim of FML's @Mod and its nested annotations. There is no FML mod loader on Paper: the plugin
 * (chat.jaspr.mutants.JasprMutants) constructs the mod class, fills @Mod.Instance/@SidedProxy and calls the
 * @Mod.EventHandler methods in FML's order (preInit, init), and registers the @Mod.EventBusSubscriber classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String modid();

    String name() default "";

    String version() default "";

    String dependencies() default "";

    String acceptedMinecraftVersions() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface EventHandler {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Instance {
        String value() default "";

        String owner() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface EventBusSubscriber {
        Side[] value() default {Side.CLIENT, Side.SERVER};

        String modid() default "";
    }
}
