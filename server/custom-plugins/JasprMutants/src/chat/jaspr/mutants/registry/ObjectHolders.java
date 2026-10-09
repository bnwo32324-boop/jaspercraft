package chat.jaspr.mutants.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Forge's ObjectHolderRegistry for the mod's holder classes: every public static final field of a class annotated
 * {@code @GameRegistry.ObjectHolder(modid)} receives the registered object named modid:fieldname_lowercase, if there is
 * one (MBItems gets its 15 items; MBSoundEvents' names contain dots, so Forge finds nothing there and leaves the field
 * instances, which are the registered ones on this server). Final static fields are written the way Forge writes them.
 */
public final class ObjectHolders {
    private ObjectHolders() {
    }

    public static int apply(Class<?> holder) {
        GameRegistry.ObjectHolder classHolder = holder.getAnnotation(GameRegistry.ObjectHolder.class);
        if (classHolder == null) return 0;
        try {
            // initialise the class first, or its static initialiser would later reset the injected fields
            Class.forName(holder.getName(), true, holder.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        String modid = classHolder.value();
        int injected = 0;
        for (Field f : holder.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || !Modifier.isFinal(mod)) continue;
            GameRegistry.ObjectHolder fieldHolder = f.getAnnotation(GameRegistry.ObjectHolder.class);
            String name = fieldHolder != null ? fieldHolder.value() : f.getName().toLowerCase(Locale.ENGLISH);
            ResourceLocation key = name.contains(":") ? new ResourceLocation(name) : new ResourceLocation(modid, name);
            Object value = null;
            if (Item.class.isAssignableFrom(f.getType())) {
                value = Item.REGISTRY.getObject(key);
            } else if (SoundEvent.class.isAssignableFrom(f.getType())) {
                value = SoundEvent.REGISTRY.getObject(key);
            }
            if (value == null) continue;
            UnsafeAccess.putStatic(f, value);
            injected++;
        }
        return injected;
    }
}
