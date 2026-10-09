package net.minecraftforge.common;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

/** JasperCraft port shim of the ForgeHooks methods the mod calls, with Forge 1.12.2's logic. */
public class ForgeHooks {
    /**
     * Forge: looting of the killer's held weapon, then LootingLevelEvent (no listeners on this server, so the value is
     * returned unchanged).
     */
    public static int getLootingLevel(Entity target, Entity killer, DamageSource cause) {
        int looting = 0;
        if (killer instanceof EntityLivingBase) {
            looting = EnchantmentHelper.getLootingModifier((EntityLivingBase) killer);
        }
        if (target instanceof EntityLivingBase) {
            looting = getLootingLevel((EntityLivingBase) target, cause, looting);
        }
        return looting;
    }

    public static int getLootingLevel(EntityLivingBase target, DamageSource cause, int level) {
        return level;
    }
}
