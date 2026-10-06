package chat.jaspr.mutants.hooks;

import java.lang.reflect.Field;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.event.CraftEventFactory;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * The Bukkit side of the Forge hooks the mod calls through ForgeEventFactory: the closest Bukkit event is fired so other
 * plugins can veto exactly as they veto the vanilla equivalent (creeper/TNT priming, wolf/ocelot taming).
 */
public final class BukkitBridge {
    private static Field explosionSize;
    private static Field explosionFire;

    private BukkitBridge() {
    }

    /** Forge's ExplosionEvent.Start point; Bukkit: ExplosionPrimeEvent with the exploding entity (radius/fire read only). */
    public static boolean explosionPrimeCancelled(World world, Explosion explosion) {
        Entity exploder = explosion.exploder;
        if (exploder == null || world.isRemote) return false;
        float radius = 0.0F;
        boolean fire = false;
        try {
            if (explosionSize == null) {
                // Spigot names of Explosion.size and Explosion.causesFire (names-1.12.2.tsv)
                explosionSize = Explosion.class.getDeclaredField("size");
                explosionSize.setAccessible(true);
                explosionFire = Explosion.class.getDeclaredField("a");
                explosionFire.setAccessible(true);
            }
            radius = explosionSize.getFloat(explosion);
            fire = explosionFire.getBoolean(explosion);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the event still fires with radius 0
        }
        ExplosionPrimeEvent event = new ExplosionPrimeEvent(exploder.getBukkitEntity(), radius, fire);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /** Forge's AnimalTameEvent point; Bukkit: EntityTameEvent (CraftBukkit fires it for wolves, ocelots, parrots, horses). */
    public static boolean tameCancelled(EntityAnimal animal, EntityPlayer tamer) {
        if (animal.world.isRemote) return false;
        return CraftEventFactory.callEntityTameEvent(animal, tamer).isCancelled();
    }
}
