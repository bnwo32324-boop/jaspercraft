package chat.jaspr.mutants.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_12_R1.CraftSound;

/**
 * Bukkit Sound and CraftSound constants for the mod's sound events. CraftBukkit names every SoundEvent it hands to Bukkit
 * by enum: CraftSound.getBySoundEffect(sound) = CraftSound.valueOf(PATH_OF_THE_KEY) and Sound.valueOf of that name. Paper
 * does it for every EntityDeathEvent (the dying entity's death sound), so without a constant a mod mob's death throws in
 * the event and the entity is removed with no drops. As for the materials (BukkitMaterials), the constants are added the
 * way Forge's EnumHelper adds them; their CraftSound key is the mod's full key, so Bukkit's playSound reaches the mod's
 * sound event.
 */
public final class BukkitSounds {
    private static final List<Sound> ADDED = new ArrayList<Sound>();

    private BukkitSounds() {
    }

    public static List<Sound> added() {
        return ADDED;
    }

    public static void register(List<SoundEvent> sounds) {
        for (SoundEvent sound : sounds) {
            ResourceLocation key = SoundEvent.REGISTRY.getNameForObject(sound);
            if (key == null) continue;
            String name = key.getPath().replace('.', '_').toUpperCase(Locale.ENGLISH);
            Sound bukkit = EnumInjector.addEnum(Sound.class, name, new Class<?>[0], new Object[0]);
            EnumInjector.addEnum(CraftSound.class, name, new Class<?>[]{String.class}, new Object[]{key.toString()});
            ADDED.add(bukkit);
        }
    }
}
