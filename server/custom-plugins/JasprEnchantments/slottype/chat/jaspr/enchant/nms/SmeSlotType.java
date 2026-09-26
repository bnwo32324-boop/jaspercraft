package chat.jaspr.enchant.nms;

import net.minecraft.server.v1_12_R1.EnchantmentSlotType;
import net.minecraft.server.v1_12_R1.Item;

import java.util.function.Predicate;

/**
 * An extra EnchantmentSlotType "constant" whose item check is SME's exact enchanting-table item set.
 * Compiled against stub/ (javac cannot extend enums); instantiated with Unsafe.allocateInstance by
 * SlotTypes, so no constructor ever runs. Keep this class free of references to other plugin classes.
 */
public final class SmeSlotType extends EnchantmentSlotType {
    /** set right after allocation; receives the NMS Item */
    public volatile Predicate<Object> test;

    private SmeSlotType() {
        super();
    }

    @Override
    public boolean canEnchant(Item item) {
        Predicate<Object> t = this.test;
        if (t == null) return false;
        try {
            return t.test(item);
        } catch (Throwable ex) {
            return false;
        }
    }
}
