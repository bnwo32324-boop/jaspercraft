package chat.jaspr.gear;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/** One survivor's worn gear plus transient ability state. Main-thread only. */
final class GearProfile {
    final UUID uuid;
    final ItemStack[] slots = new ItemStack[GearType.SLOT_COUNT];
    /** A browser client with the gear panel announced itself on this connection. */
    boolean capable;
    int protocol;
    /** Persisted: the Teddy Bear's Last Stand is ready again at this epoch millisecond. */
    long lastStandReady;
    /** The on-disk file could not be read or moved aside: never overwrite it. */
    boolean frozen;
    long handEquipAt;

    // Transient ability state (never persisted).
    boolean magnet;
    int shieldHits;
    int sneakTicks;
    int airTicks;
    int riseTicks;
    int sinkTicks;
    boolean ownsLevitation;
    double xpRemainder;
    String scanFilter = "any";
    long arcReady, dodgeReady, blinkReady, chestReady, repelReady, scanReady, threatReady, leechReady, phaseReady;
    long trackerReady, gyroReady, vestHitAt, readyAt;
    double vestCharge;
    int restTicks;
    double lastX, lastY, lastZ, moved2;
    String lastWorld, readyName;
    long rateWindow;
    int rateCount;
    boolean rateWarned;

    GearProfile(UUID uuid) { this.uuid = uuid; }

    Set<GearItem> worn() {
        Set<GearItem> out = EnumSet.noneOf(GearItem.class);
        for (ItemStack stack : slots) {
            GearItem item = GearItems.identify(stack);
            if (item != null) out.add(item);
        }
        return out;
    }

    /** Typed gear takes its own slot; any-slot gear prefers the charm slot, then the first free one. */
    int freeSlotFor(GearItem item) {
        int charm = GearType.SLOT_COUNT - 1;
        if (item.type == GearType.ANY && GearItems.empty(slots[charm])) return charm;
        for (int i = 0; i < slots.length; i++) {
            if (GearItems.empty(slots[i]) && item.type.fits(i)) return i;
        }
        return -1;
    }

    boolean empty() {
        for (ItemStack stack : slots) if (!GearItems.empty(stack)) return false;
        return true;
    }

    /** Sliding one-second window; true when the message may be processed. */
    boolean allowMessage(long now, int perSecond) {
        if (now - rateWindow >= 1000L) { rateWindow = now; rateCount = 0; rateWarned = false; }
        return ++rateCount <= perSecond;
    }
}
