package chat.jaspr.gear;

/** Slot families. ANY items (the mod's "trinket" type) fit every slot. */
public enum GearType {
    NECK("Neck", "Pendants, tags and rebreathers"),
    RING("Ring", "Rings and bands"),
    BELT("Belt", "Belts and rigs"),
    HEAD("Head", "Goggles and headsets"),
    BODY("Body", "Vests and harnesses"),
    CHARM("Charm", "Keepsakes and charms"),
    ANY("Any slot", "Fits any gear slot");

    /** Baubles-style layout: amulet, ring, ring, belt, head, body, charm. */
    public static final GearType[] SLOTS = {NECK, RING, RING, BELT, HEAD, BODY, CHARM};
    public static final int SLOT_COUNT = SLOTS.length;

    public final String label;
    public final String hint;

    GearType(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public boolean fits(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && (this == ANY || SLOTS[slot] == this);
    }
}
