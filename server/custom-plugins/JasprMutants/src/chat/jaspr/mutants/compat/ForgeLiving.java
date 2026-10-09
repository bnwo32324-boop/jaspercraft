package chat.jaspr.mutants.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.math.MathHelper;

/**
 * Forge 1.12.2's EntityLivingBase.SWIM_SPEED ("forge.swimSpeed", 1.0, 0..1024, watched) and the three places Forge
 * applies it (javap of Forge 14.23.5.2860's patched EntityLivingBase): moveRelative multiplies the scaled strafe/up/
 * forward by it while the entity is in water or lava, and handleJumpWater/handleJumpLava add 0.04 * swim speed to
 * motionY. Forge registers the attribute for every living entity; only the mod's mobs change it, so here the mod's
 * classes register it themselves (swimSpeed) and call these from their overrides (marked "JasperCraft port").
 */
public final class ForgeLiving {
    public static final IAttribute SWIM_SPEED = new RangedAttribute(null, "forge.swimSpeed", 1.0D, 0.0D, 1024.0D).setShouldWatch(true);

    private ForgeLiving() {
    }

    /** getEntityAttribute(SWIM_SPEED), registering it first as Forge's EntityLivingBase.applyEntityAttributes does. */
    public static IAttributeInstance swimSpeed(EntityLivingBase entity) {
        IAttributeInstance instance = entity.getEntityAttribute(SWIM_SPEED);
        return instance != null ? instance : entity.getAttributeMap().registerAttribute(SWIM_SPEED);
    }

    public static float swim(EntityLivingBase entity) {
        IAttributeInstance instance = entity.getEntityAttribute(SWIM_SPEED);
        return instance == null ? 1.0F : (float) instance.getAttributeValue();
    }

    /** Forge's EntityLivingBase.moveRelative(strafe, up, forward, friction). */
    public static void moveRelative(EntityLivingBase entity, float strafe, float up, float forward, float friction) {
        float f = strafe * strafe + up * up + forward * forward;
        if (f >= 1.0E-4F) {
            f = MathHelper.sqrt(f);
            if (f < 1.0F) f = 1.0F;
            f = friction / f;
            strafe = strafe * f;
            up = up * f;
            forward = forward * f;
            if (entity.isInWater() || entity.isInLava()) {
                strafe = strafe * (float) swimValue(entity);
                up = up * (float) swimValue(entity);
                forward = forward * (float) swimValue(entity);
            }
            float f1 = MathHelper.sin(entity.rotationYaw * 0.017453292F);
            float f2 = MathHelper.cos(entity.rotationYaw * 0.017453292F);
            entity.motionX += (double) (strafe * f2 - forward * f1);
            entity.motionY += (double) up;
            entity.motionZ += (double) (forward * f2 + strafe * f1);
        }
    }

    /** Forge's EntityLivingBase.handleJumpWater / handleJumpLava: motionY += 0.04 * swim speed. */
    public static void jumpInLiquid(EntityLivingBase entity) {
        entity.motionY += 0.03999999910593033D * swimValue(entity);
    }

    private static double swimValue(EntityLivingBase entity) {
        IAttributeInstance instance = entity.getEntityAttribute(SWIM_SPEED);
        return instance == null ? 1.0D : instance.getAttributeValue();
    }
}
