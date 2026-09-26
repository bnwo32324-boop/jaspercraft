package chat.jaspr.enchant.nms;

import org.bukkit.craftbukkit.v1_12_R1.enchantments.CraftEnchantment;

/**
 * Bukkit wrapper for an SME enchantment. Identical to CraftEnchantment except for a stable,
 * readable name (CraftEnchantment would call ids above 71 "UNKNOWN_ENCHANT_n"). The name is what
 * Bukkit item serialization (ItemStack#serialize) stores, so it must never change: SME_&lt;REGNAME&gt;.
 */
public final class SmeCraftEnchantment extends CraftEnchantment {
    private final String name;
    private final SmeEnchantment sme;

    public SmeCraftEnchantment(SmeEnchantment sme) {
        super(sme);
        this.sme = sme;
        this.name = "SME_" + sme.def.regName.toUpperCase();
    }

    @Override
    public String getName() {
        return name;
    }

    public SmeEnchantment sme() {
        return sme;
    }
}
