package chat.jaspr.mutants.registry;

import java.lang.reflect.Field;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/** Sound events: Paper's SoundEvent registry, id 1000 + registration index (MUTANTS_PROTOCOL.md 1.4). */
public final class SoundRegistry extends ModRegistry<SoundEvent> {
    public static final int FIRST_ID = 1000;
    public static final SoundRegistry INSTANCE = new SoundRegistry();
    private static Field nameField;

    private SoundRegistry() {
        super(SoundEvent.class);
    }

    /** The sound's own name (SoundEvent.soundName; its getter is client-only in the server jar). */
    public static ResourceLocation nameOf(SoundEvent sound) {
        try {
            if (nameField == null) {
                for (Field f : SoundEvent.class.getDeclaredFields()) {
                    if (f.getType() == ResourceLocation.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        nameField = f;
                    }
                }
            }
            return (ResourceLocation) nameField.get(sound);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SoundEvent name", e);
        }
    }

    @Override
    protected ResourceLocation keyOf(SoundEvent value) {
        ResourceLocation named = RegistryNames.get(value);
        return named != null ? named : nameOf(value);
    }

    @Override
    protected void toVanilla(int index, ResourceLocation key, SoundEvent value) {
        int id = FIRST_ID + index;
        if (SoundEvent.REGISTRY.getObjectById(id) != null) {
            throw new IllegalStateException("Sound id " + id + " is taken");
        }
        SoundEvent.REGISTRY.register(id, key, value);
    }
}
