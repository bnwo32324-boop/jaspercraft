package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityTNTPrimed;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.WeakHashMap;

/**
 * Combat Medic (LivingHealEvent LOW) and the explosion-knockback part of SME's EnchantmentProtectionMixin:
 * getBlastDamageReduction becomes knockback * max(0, 1 - 0.15 * (blastProtection + 4 * advancedBlastProtection))
 * instead of vanilla's floored (practically inactive) reduction. Only mobs are affected: player explosion
 * knockback is sent unreduced to the client in vanilla anyway.
 */
public final class MiscFx implements Listener {
    public static long medicHeals = 0, blastAdjusted = 0;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getAmount() <= 0.1F) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        Guard.run("combatmedic", () -> {
            EntityLiving user = Nms.living(e.getEntity());
            int level = Nms.maxLevel(E.COMBATMEDIC, user);
            if (level > 0) {
                e.setAmount(e.getAmount() * (1.1F + 0.3F * (float) level));
                medicHeals++;
            }
        });
    }

    private static final WeakHashMap<Entity, Float> RADIUS = new WeakHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent e) {
        Entity ent = Nms.entity(e.getEntity());
        if (ent != null) RADIUS.put(ent, e.getRadius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || !(e.getEntity() instanceof LivingEntity)) return;
        Guard.run("blastprotection.knockback", () -> {
            EntityLiving v = Nms.living(e.getEntity());
            if (v == null || v instanceof EntityHuman) return;
            int bp = EnchantmentManager.a(Enchantments.PROTECTION_EXPLOSIONS, v);
            int adv = Nms.maxLevel(E.ADVANCEDBLASTPROTECTION, v);
            int smeI = bp + 2 * (2 * adv);
            if (smeI <= 0) return;
            Entity src = Nms.entity(e.getDamager());
            Float radius = src == null ? null : RADIUS.get(src);
            if (radius == null) return;
            double cx = src.locX, cy = src.locY + (src instanceof EntityTNTPrimed ? (double) (src.length / 16.0F) : 0.0D), cz = src.locZ;
            float f3 = radius * 2.0F;
            double d12 = v.e(cx, cy, cz) / (double) f3;
            if (d12 > 1.0D) return;
            double dx = v.locX - cx, dy = v.locY + (double) v.getHeadHeight() - cy, dz = v.locZ - cz;
            double d13 = (double) MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
            if (d13 == 0.0D) return;
            dx /= d13;
            dy /= d13;
            dz /= d13;
            double exposure = (double) v.world.a(new Vec3D(cx, cy, cz), v.getBoundingBox());
            double d10 = (1.0D - d12) * exposure;
            double vanilla = bp > 0 ? d10 - (double) MathHelper.floor(d10 * (double) ((float) bp * 0.15F)) : d10;
            double sme = d10 * Math.max(0.0D, 1.0D - (double) smeI * 0.15D);
            double delta = sme - vanilla;
            // vanilla adds (dx,dy,dz) * vanilla right after this event
            v.motX += dx * delta;
            v.motY += dy * delta;
            v.motZ += dz * delta;
            blastAdjusted++;
        });
    }
}
