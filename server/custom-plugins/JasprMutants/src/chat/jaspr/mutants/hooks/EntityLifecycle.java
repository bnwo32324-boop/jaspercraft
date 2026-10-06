package chat.jaspr.mutants.hooks;

import chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Forge's entity lifecycle hooks, fed from Paper's World.onEntityAdded / onEntityRemoved events (both fire right after
 * the world's listeners, where Forge calls Entity.onAddedToWorld / onRemovedFromWorld):
 * - EntityJoinWorldEvent (the mod's EventHandler adds avoid/tempt AI to ocelots, villagers and pigs). Forge posts it
 *   just before the entity joins (spawnEntity, chunk loading); here it is posted as it joins. The mod never cancels it.
 * - onRemovedFromWorld (SpiderPigEntity clears its webs).
 * - isAddedToWorld: CraftBukkit's Entity.valid flips at the same two points.
 */
public final class EntityLifecycle implements Listener {
    private static Logger log = Logger.getLogger("JasprMutants");
    private static long joinEvents;
    private static int failures;

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static long joinEventCount() {
        return joinEvents;
    }

    public static boolean isAddedToWorld(Entity entity) {
        return entity.valid;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdded(EntityAddToWorldEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        if (entity.world == null || entity.world.isRemote) return;
        try {
            MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, entity.world));
            joinEvents++;
        } catch (RuntimeException e) {
            if (failures++ < 20) log.warning("MUTANTS_HOOK_FAILED hook=entity_join reason=" + e.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRemoved(EntityRemoveFromWorldEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        if (entity instanceof SpiderPigEntity) {
            try {
                ((SpiderPigEntity) entity).onRemovedFromWorld();
            } catch (RuntimeException e) {
                if (failures++ < 20) log.warning("MUTANTS_HOOK_FAILED hook=removed_from_world reason=" + e.getClass().getSimpleName());
            }
        }
    }
}
