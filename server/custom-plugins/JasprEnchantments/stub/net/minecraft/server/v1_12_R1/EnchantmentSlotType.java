package net.minecraft.server.v1_12_R1;

/**
 * COMPILE-TIME STUB ONLY (never packaged). javac refuses to extend an enum type, so
 * chat.jaspr.enchant.nms.SmeSlotType is compiled against this non-enum stand-in. At runtime the
 * real enum class is the superclass; SmeSlotType instances are allocated without running a
 * constructor (see SlotTypes), so the stub constructor is never linked.
 */
public abstract class EnchantmentSlotType {
    protected EnchantmentSlotType() {
    }

    public abstract boolean canEnchant(Item item);
}
