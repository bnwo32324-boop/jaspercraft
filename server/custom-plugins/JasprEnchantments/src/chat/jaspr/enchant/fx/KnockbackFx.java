package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * SME mixin on EnchantmentHelper.getKnockbackModifier: Dragging forces 0, Advanced Knockback adds
 * floor(2.5 * level). Vanilla applies the enchantment knockback after the standard 0.4 hit knockback;
 * Paper's EntityKnockbackByEntityEvent lets us rewrite that second push in the right order.
 */
public final class KnockbackFx implements Listener {

    /** SME-modified getKnockbackModifier (without the sprint bonus) */
    public static int modifier(EntityLiving e) {
        if (e == null) return 0;
        if (Nms.mainLevel(E.DRAGGING, e) > 0) return 0;
        int adv = Nms.mainLevel(E.ADVANCEDKNOCKBACK, e);
        return EnchantmentManager.a(Enchantments.KNOCKBACK, e) + (adv > 0 ? MathHelper.d(adv * 2.5F) : 0);
    }

    private static final class Pending {
        final EntityLiving attacker;
        final int vanilla, sme, tick;
        boolean stdSeen;

        Pending(EntityLiving attacker, int vanilla, int sme, int tick) {
            this.attacker = attacker;
            this.vanilla = vanilla;
            this.sme = sme;
            this.tick = tick;
        }
    }

    private static final Map<EntityLiving, Pending> PENDING = new IdentityHashMap<>();
    private static boolean applying = false;

    public static void register() {
        Pipeline.post("knockback.enchant", c -> {
            if (c.event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || c.source.isSweep()) return;
            EntityLiving a = c.attacker();
            if (a == null || c.immediate != a) return;
            String t = c.source.translationIndex;
            if (!"player".equals(t) && !"mob".equals(t)) return;
            int vanilla = EnchantmentManager.a(Enchantments.KNOCKBACK, a);
            int sme = modifier(a);
            if (a instanceof EntityHuman) {
                int sprint = (a.isSprinting() && Crits.lastStrength((EntityHuman) a) > 0.9F) ? 1 : 0;
                vanilla += sprint;
                sme += sprint;
            }
            if (vanilla == sme) return;
            Pending p = new Pending(a, vanilla, sme, MinecraftServer.currentTick);
            if (c.iframePath && vanilla == 0) {
                // no standard knockback and no vanilla enchant knockback follow: apply now
                apply(c.victim, a, sme);
                return;
            }
            PENDING.put(c.victim, p);
        });
    }

    private static void apply(EntityLiving victim, EntityLiving attacker, int level) {
        if (level <= 0) return;
        applying = true;
        try {
            victim.a(attacker, (float) level * 0.5F, (double) MathHelper.sin(attacker.yaw * 0.017453292F),
                    (double) (-MathHelper.cos(attacker.yaw * 0.017453292F)));
        } finally {
            applying = false;
        }
        attacker.motX *= 0.6D;
        attacker.motZ *= 0.6D;
        if (!attacker.world.paperConfig.disableSprintInterruptionOnAttack) attacker.setSprinting(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent e) {
        if (applying) return;
        Guard.run("knockback.event", () -> {
            EntityLiving victim = Nms.living(e.getEntity());
            Pending p = victim == null ? null : PENDING.get(victim);
            if (p == null) return;
            Entity hitBy = Nms.entity(e.getHitBy());
            if (hitBy != p.attacker || p.tick != MinecraftServer.currentTick) return;
            float s = e.getKnockbackStrength();
            if (!p.stdSeen && Math.abs(s - 0.4F) < 1.0E-4F) {
                p.stdSeen = true;
                if (p.vanilla == 0) {
                    // apply the standard push ourselves, then the SME enchant push right after it
                    e.setCancelled(true);
                    Vector acc = e.getAcceleration();
                    victim.motX += acc.getX();
                    victim.motY += acc.getY();
                    victim.motZ += acc.getZ();
                    PENDING.remove(victim);
                    apply(victim, p.attacker, p.sme);
                }
                return;
            }
            if (p.vanilla > 0 && Math.abs(s - p.vanilla * 0.5F) < 1.0E-4F) {
                e.setCancelled(true);
                PENDING.remove(victim);
                if (p.sme > 0) {
                    applying = true;
                    try {
                        victim.a(p.attacker, (float) p.sme * 0.5F, (double) MathHelper.sin(p.attacker.yaw * 0.017453292F),
                                (double) (-MathHelper.cos(p.attacker.yaw * 0.017453292F)));
                    } finally {
                        applying = false;
                    }
                }
            }
        });
    }

    /** end of tick: a pending SME-only push whose standard knockback was resisted still happens */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<EntityLiving, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityLiving, Pending> en = it.next();
            Pending p = en.getValue();
            it.remove();
            if (p.vanilla == 0 && !p.stdSeen && en.getKey().isAlive()) apply(en.getKey(), p.attacker, p.sme);
        }
    }
}
