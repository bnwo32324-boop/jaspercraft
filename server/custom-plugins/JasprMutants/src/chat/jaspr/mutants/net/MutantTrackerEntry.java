package chat.jaspr.mutants.net;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketEntityProperties;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.MathHelper;

/**
 * The tracker entry of a mod entity: Paper's EntityTrackerEntry with the mod's tracking range, update frequency and
 * velocity flag (Forge's EntityRegistry.tryTrackingEntity), whose spawn step sends MUTANTS_PROTOCOL.md's SPAWN message
 * instead of a vanilla spawn packet (FML's EntitySpawnMessage on Forge). Paper's EntityTrackerEntry builds the spawn
 * packet in a private method that throws for classes it does not know, so updatePlayerEntity is Paper's own sequence
 * (git-Paper-1620) with that one packet replaced; movement, metadata, attribute, equipment, effect, passenger and
 * destroy packets stay Paper's.
 */
public final class MutantTrackerEntry extends EntityTrackerEntry {
    private static Field headYawField;
    private static Field motionXField;
    private static Field motionYField;
    private static Field motionZField;
    private static Logger log = Logger.getLogger("JasprMutants");
    private static long spawnsSent;

    private final Entity entity;
    private final boolean velocity;

    public MutantTrackerEntry(Entity entity, int range, int maxRange, int updateFrequency, boolean sendVelocityUpdates) {
        super(entity, range, maxRange, updateFrequency, sendVelocityUpdates);
        this.entity = entity;
        this.velocity = sendVelocityUpdates;
    }

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static long spawnsSent() {
        return spawnsSent;
    }

    @Override
    public void updatePlayerEntity(EntityPlayerMP player) {
        org.spigotmc.AsyncCatcher.catchOp("player tracker update");
        if (player == this.entity) return;
        if (this.isVisibleTo(player)) {
            if (!this.trackingPlayers.contains(player) && (this.watchingChunk(player) || this.entity.forceSpawn)) {
                player.addEntity(this.entity); // Paper: drop a pending destroy of this id first
                this.trackedPlayerMap.put(player, Boolean.TRUE);
                player.connection.sendPacket(SpawnMessage.packet(this.entity));
                spawnsSent++;
                if (!this.entity.getDataManager().isEmpty()) {
                    player.connection.sendPacket(new SPacketEntityMetadata(this.entity.getEntityId(), this.entity.getDataManager(), true));
                }
                boolean sendMotion = this.velocity;
                if (this.entity instanceof EntityLivingBase) {
                    AttributeMap map = (AttributeMap) ((EntityLivingBase) this.entity).getAttributeMap();
                    Collection<IAttributeInstance> watched = map.getWatchedAttributes();
                    if (!watched.isEmpty()) {
                        player.connection.sendPacket(new SPacketEntityProperties(this.entity.getEntityId(), watched));
                    }
                    if (((EntityLivingBase) this.entity).isElytraFlying()) sendMotion = true;
                }
                setMotionMemory(this.entity.motionX, this.entity.motionY, this.entity.motionZ);
                // FML's spawn message is not an SPacketSpawnMob, so vanilla follows it with a velocity packet.
                if (sendMotion) {
                    player.connection.sendPacket(new SPacketEntityVelocity(this.entity.getEntityId(), this.entity.motionX, this.entity.motionY, this.entity.motionZ));
                }
                if (this.entity instanceof EntityLivingBase) {
                    for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                        ItemStack stack = ((EntityLivingBase) this.entity).getItemStackFromSlot(slot);
                        if (!stack.isEmpty()) {
                            player.connection.sendPacket(new SPacketEntityEquipment(this.entity.getEntityId(), slot, stack));
                        }
                    }
                }
                // CraftBukkit: the head yaw goes to every tracking player
                int headYaw = MathHelper.floor(this.entity.getRotationYawHead() * 256.0F / 360.0F);
                setHeadYawMemory(headYaw);
                this.sendPacketToTrackedPlayers(new SPacketEntityHeadLook(this.entity, (byte) headYaw));
                if (this.entity instanceof EntityLivingBase) {
                    for (PotionEffect effect : ((EntityLivingBase) this.entity).getActivePotionEffects()) {
                        player.connection.sendPacket(new SPacketEntityEffect(this.entity.getEntityId(), effect));
                    }
                }
                if (!this.entity.getPassengers().isEmpty()) {
                    player.connection.sendPacket(new SPacketSetPassengers(this.entity));
                }
                if (this.entity.isRiding()) {
                    player.connection.sendPacket(new SPacketSetPassengers(this.entity.getRidingEntity()));
                }
                this.entity.addTrackingPlayer(player);
                player.addEntity(this.entity);
                this.updatePassengersOf(player);
            }
        } else if (this.trackingPlayers.contains(player)) {
            this.trackingPlayers.remove(player);
            this.entity.removeTrackingPlayer(player);
            player.removeEntity(this.entity);
            this.updatePassengersOf(player);
        }
    }

    /** Paper's EntityTrackerEntry.updatePassengers: re-check the passengers' trackers and resend the mount packet. */
    private void updatePassengersOf(EntityPlayerMP player) {
        if (this.entity.isBeingRidden()) {
            for (Entity passenger : this.entity.getPassengers()) {
                EntityTrackerEntry entry = trackerOf(passenger);
                if (entry != null) entry.updatePlayerEntity(player);
            }
            player.connection.sendPacket(new SPacketSetPassengers(this.entity));
        }
    }

    private static Field trackerField;

    private static EntityTrackerEntry trackerOf(Entity e) {
        try {
            if (trackerField == null) {
                Field f = Entity.class.getDeclaredField("tracker"); // Paper: Entity.tracker
                f.setAccessible(true);
                trackerField = f;
            }
            return (EntityTrackerEntry) trackerField.get(e);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private boolean watchingChunk(EntityPlayerMP player) {
        return player.getServerWorld().getPlayerChunkMap().isPlayerWatchingChunk(player, this.entity.chunkCoordX, this.entity.chunkCoordZ);
    }

    /** Paper's private headYaw (vanilla lastHeadMotion), so later head-turn updates compare against what was sent. */
    private void setHeadYawMemory(int value) {
        try {
            if (headYawField == null) {
                Field f = EntityTrackerEntry.class.getDeclaredField("headYaw");
                f.setAccessible(true);
                headYawField = f;
            }
            headYawField.setInt(this, value);
        } catch (ReflectiveOperationException e) {
            warnOnce(e);
        }
    }

    /** Paper's private n/o/p (vanilla lastTrackedEntityMotionX/Y, motionZ): velocity updates compare against them. */
    private void setMotionMemory(double x, double y, double z) {
        try {
            if (motionXField == null) {
                Field fx = EntityTrackerEntry.class.getDeclaredField("n");
                Field fy = EntityTrackerEntry.class.getDeclaredField("o");
                Field fz = EntityTrackerEntry.class.getDeclaredField("p");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fz.setAccessible(true);
                motionXField = fx;
                motionYField = fy;
                motionZField = fz;
            }
            motionXField.setDouble(this, x);
            motionYField.setDouble(this, y);
            motionZField.setDouble(this, z);
        } catch (ReflectiveOperationException e) {
            warnOnce(e);
        }
    }

    private static boolean warned;

    private static void warnOnce(Exception e) {
        if (!warned) {
            warned = true;
            log.warning("MUTANTS_TRACKER_REFLECTION_FAILED reason=" + e.getClass().getSimpleName());
        }
    }
}
