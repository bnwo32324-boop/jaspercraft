package chat.jaspr.mutants.net;

import chat.jaspr.mutants.registry.EntityRegistry;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityEntry;

/**
 * A world's EntityTracker with Forge's EntityRegistry.tryTrackingEntity step: mod entities are tracked with the mod's
 * own tracker settings (EntityEntryBuilder.tracker / the 80, 3, true of living entries) by a {@link MutantTrackerEntry};
 * everything else is Paper's EntityTracker unchanged. Installed per world at WorldInitEvent, before any entity loads.
 * Spigot's per-category tracking range limits (spigot.yml) apply to mod entities as to every other entity.
 */
public final class MutantEntityTracker extends EntityTracker {
    private static Logger log = Logger.getLogger("JasprMutants");
    private static Field entriesField;
    private static Field maxDistanceField;
    private static long tracked;

    private final WorldServer world;

    public MutantEntityTracker(WorldServer world) {
        super(world);
        this.world = world;
    }

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static long trackedCount() {
        return tracked;
    }

    /** Replaces the world's tracker, moving any entries it already has (normally none at WorldInitEvent). */
    @SuppressWarnings("unchecked")
    public static MutantEntityTracker install(WorldServer world) throws ReflectiveOperationException {
        EntityTracker old = world.entityTracker;
        if (old instanceof MutantEntityTracker) return (MutantEntityTracker) old;
        MutantEntityTracker tracker = new MutantEntityTracker(world);
        Set<EntityTrackerEntry> oldEntries = (Set<EntityTrackerEntry>) entries().get(old);
        Set<EntityTrackerEntry> newEntries = (Set<EntityTrackerEntry>) entries().get(tracker);
        for (EntityTrackerEntry e : oldEntries) {
            newEntries.add(e);
            tracker.trackedEntityHashTable.addKey(e.getTrackedEntity().getEntityId(), e);
        }
        maxDistance().setInt(tracker, maxDistance().getInt(old));
        world.entityTracker = tracker;
        return tracker;
    }

    private static Field entries() throws NoSuchFieldException {
        if (entriesField == null) {
            Field f = EntityTracker.class.getDeclaredField("c"); // Spigot name of EntityTracker.entries
            f.setAccessible(true);
            entriesField = f;
        }
        return entriesField;
    }

    private static Field maxDistance() throws NoSuchFieldException {
        if (maxDistanceField == null) {
            Field f = EntityTracker.class.getDeclaredField("e"); // Spigot name of maxTrackingDistanceThreshold
            f.setAccessible(true);
            maxDistanceField = f;
        }
        return maxDistanceField;
    }

    @Override
    public void track(Entity entity) {
        EntityEntry entry = EntityRegistry.entryOf(entity.getClass());
        if (entry == null || !entry.hasTracker()) {
            super.track(entity);
            return;
        }
        this.trackMutant(entity, entry.getTrackingRange(), entry.getTrackingUpdateFrequency(), entry.sendsVelocityUpdates());
    }

    /** EntityTracker.track(entity, range, frequency, velocity) of Paper, creating a MutantTrackerEntry. */
    @SuppressWarnings("unchecked")
    private void trackMutant(Entity entity, int range, int updateFrequency, boolean sendVelocityUpdates) {
        org.spigotmc.AsyncCatcher.catchOp("entity track");
        range = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, range);
        if (this.trackedEntityHashTable.containsItem(entity.getEntityId())) {
            log.warning("MUTANTS_SPAWN_FAILED type=" + EntityRegistry.networkTypeId(entity.getClass()) + " reason=already_tracked");
            return;
        }
        try {
            MutantTrackerEntry e = new MutantTrackerEntry(entity, range, maxDistance().getInt(this), updateFrequency, sendVelocityUpdates);
            ((Set<EntityTrackerEntry>) entries().get(this)).add(e);
            this.trackedEntityHashTable.addKey(entity.getEntityId(), e);
            e.updatePlayerEntities(this.world.playerEntities);
            tracked++;
        } catch (ReflectiveOperationException ex) {
            log.warning("MUTANTS_SPAWN_FAILED type=" + EntityRegistry.networkTypeId(entity.getClass()) + " reason=tracker_" + ex.getClass().getSimpleName());
        }
    }
}
