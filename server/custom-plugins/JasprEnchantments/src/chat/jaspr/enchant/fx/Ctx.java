package chat.jaspr.enchant.fx;

import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityDamageSource;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * State of one living-entity damage event while it passes through the three Forge phases SME was
 * written for: LivingAttackEvent (attackAmount, may cancel), LivingHurtEvent (hurtAmount = amount
 * before armor) and LivingDamageEvent (damageAmount = health damage after armor/potions/absorption).
 */
public final class Ctx {
    public final EntityDamageEvent event;
    public final EntityLiving victim;
    public final DamageSource source;
    public final Entity trueSource;
    public final Entity immediate;
    public final boolean iframePath;

    public float attackAmount;
    public boolean attackCanceled;

    public float hurtStart;
    public float hurtAmount;
    public boolean hurtRan;

    public float damageAmount;
    public boolean damageCanceled;

    /** fire set on the victim before the hit (SME EntityPlayerMixinSetFire); reverted if the hit fails */
    public boolean preIgnited;
    /** SME/vanilla knockback levels for the post-hit enchantment knockback */
    public int vanillaKnockback = -1, smeKnockback = -1;

    public Ctx(EntityDamageEvent event, EntityLiving victim, DamageSource source) {
        this.event = event;
        this.victim = victim;
        this.source = source;
        this.trueSource = source.getEntity();
        this.immediate = source.i();
        this.iframePath = (float) victim.noDamageTicks > (float) victim.maxNoDamageTicks / 2.0F;
    }

    public EntityLiving attacker() {
        return trueSource instanceof EntityLiving ? (EntityLiving) trueSource : null;
    }

    /** EnchantmentBase.isDamageSourceAllowed (enablePetAttacks=false) */
    public static boolean allowed(DamageSource source) {
        if (source == null) return false;
        Entity ts = source.getEntity();
        if (!(ts instanceof EntityLiving)) return false;
        Entity im = source.i();
        if (ts instanceof EntityHuman && !(im instanceof EntityHuman) && im instanceof EntityLiving) return false;
        String t = source.translationIndex;
        return "player".equals(t) || "mob".equals(t);
    }

    public boolean allowed() {
        return allowed(source);
    }

    // ---------------------------------------------------------------- SME mixin fields on EntityDamageSource

    private static final Map<DamageSource, Float> PIERCING = new WeakHashMap<>();
    private static final Map<DamageSource, Boolean> CULLING = new WeakHashMap<>();

    public static float piercing(DamageSource s) {
        Float f = PIERCING.get(s);
        return f == null ? 0.0F : f;
    }

    public static void setPiercing(DamageSource s, float v) {
        if (s instanceof EntityDamageSource) PIERCING.put(s, v);
    }

    public static boolean culling(DamageSource s) {
        return Boolean.TRUE.equals(CULLING.get(s));
    }

    public static void setCulling(DamageSource s) {
        if (s instanceof EntityDamageSource) CULLING.put(s, Boolean.TRUE);
    }

    public boolean sourceMadeMagic;
    /** an SME iframe bypass already happened for this hit */
    public boolean bypassed;
}
