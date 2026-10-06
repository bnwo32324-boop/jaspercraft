package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.MBConfig;
import java.util.Map;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class MBParticles {
    public static final EnumParticleTypes ENDERSOUL = MBParticles.addEnumParticleType("mutantbeasts:endersoul", MBConfig.GENERAL.endersoulParticleID, true);
    public static final EnumParticleTypes SKULL_SPIRIT = MBParticles.addEnumParticleType("mutantbeasts:skull_spirit", MBConfig.GENERAL.skullSpiritParticleID, true);

    public static void register() {
        Map PARTICLES = ObfuscationReflectionHelper.getPrivateValue(EnumParticleTypes.class, null, "field_179365_U");
        PARTICLES.put(ENDERSOUL.getParticleID(), ENDERSOUL);
        PARTICLES.put(SKULL_SPIRIT.getParticleID(), SKULL_SPIRIT);
        Map BY_NAME = ObfuscationReflectionHelper.getPrivateValue(EnumParticleTypes.class, null, "field_186837_Z");
        BY_NAME.put(ENDERSOUL.getParticleName(), ENDERSOUL);
        BY_NAME.put(SKULL_SPIRIT.getParticleName(), SKULL_SPIRIT);
    }

    private static EnumParticleTypes addEnumParticleType(String particleNameIn, int particleIDIn, boolean shouldIgnoreRangeIn) {
        EnumParticleTypes enumParticleTypes = EnumParticleTypes.getParticleFromId(particleIDIn);
        if (enumParticleTypes != null) {
            throw new IllegalStateException("The particle " + particleNameIn + " has the same ID as the particle " + enumParticleTypes.getParticleName() + ". Change the ID in your config!");
        }
        return EnumHelper.addEnum(EnumParticleTypes.class, particleNameIn, new Class[]{String.class, Integer.TYPE, Boolean.TYPE, Integer.TYPE}, new Object[]{particleNameIn, particleIDIn, shouldIgnoreRangeIn, 0});
    }
}
