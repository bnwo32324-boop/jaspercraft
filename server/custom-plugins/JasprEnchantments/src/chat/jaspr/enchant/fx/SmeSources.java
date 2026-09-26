package chat.jaspr.enchant.fx;

import net.minecraft.server.v1_12_R1.ChatMessage;
import net.minecraft.server.v1_12_R1.EntityDamageSource;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.IChatBaseComponent;
import net.minecraft.server.v1_12_R1.Vec3D;

/** SME damage sources that need protected NMS setters. */
public final class SmeSources {
    private SmeSources() {}

    /**
     * SME DamageSources.DECONSTRUCTED: "deconstructed", absolute, bypasses armor, allowed in creative, no
     * attacker. CraftBukkit throws on unknown entity-less DamageSource objects, so it is modelled as an
     * EntityDamageSource with a null entity (reported to Bukkit as ENTITY_ATTACK without damager).
     */
    public static final Deconstructed DECONSTRUCTED = new Deconstructed();

    public static final class Deconstructed extends EntityDamageSource {
        Deconstructed() {
            super("deconstructed", null);
            m();              // setDamageIsAbsolute
            setIgnoreArmor(); // setDamageBypassesArmor
            l();              // setDamageAllowedInCreativeMode
        }

        @Override
        public IChatBaseComponent getLocalizedDeathMessage(EntityLiving victim) {
            return new ChatMessage("death.attack.deconstructed", victim.getScoreboardDisplayName());
        }

        @Override
        public Vec3D v() {
            return null;
        }

        @Override
        public boolean r() {
            return false;
        }
    }

    /** EntityDamageSource whose protected setFireDamage (obfuscated setExplosion()) is reachable. */
    public static final class Entity extends EntityDamageSource {
        public Entity(String name, net.minecraft.server.v1_12_R1.Entity source) {
            super(name, source);
        }

        public Entity fire() {
            setExplosion(); // NMS name of DamageSource.setFireDamage()
            return this;
        }
    }
}
