package chat.jaspr.gear;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
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

    // Phase 2 vitals (persisted in UUID.vitals by GearStore, separately from the gear file).
    /** Current adrenaline; negative until loaded, then 0..maxAdrenaline(). */
    double adrenaline = -1;
    /** Adrenaline Crystals used (each +10 max). */
    int crystals;
    /** Active custom statuses: absolute expiry, epoch milliseconds (remaining time is persisted). */
    final Map<GearStatus, Long> status = new EnumMap<GearStatus, Long>(GearStatus.class);
    // Transient vitals state.
    long paraImmuneUntil, useReady, vitalsNotedAt;
    /** Remaining status time while offline (offline time does not count). */
    final Map<GearStatus, Long> parked = new EnumMap<GearStatus, Long>(GearStatus.class);
    String hudSig;
    boolean vitalsDirty;

    // Phase 3 (persisted in UUID.vitals): the survivor's mutation and whether others see worn gear.
    GearMutation mutation = GearMutation.BASELINE;
    boolean showWorn = true;
    // Transient mutation state.
    boolean grantedFlight;
    long abilityReady, fadeUntil, pounceUntil, mistUntil, chargeUntil;
    boolean pounceArmed;
    final java.util.Set<UUID> chargeHit = new java.util.HashSet<UUID>();

    GearProfile(UUID uuid) { this.uuid = uuid; }

    int maxAdrenaline() { return GearVitals.BASE_MAX + GearVitals.CRYSTAL_BONUS * Math.max(0, Math.min(GearVitals.MAX_CRYSTALS, crystals)); }

    boolean has(GearStatus s, long now) {
        Long until = status.get(s);
        return until != null && until > now;
    }

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
