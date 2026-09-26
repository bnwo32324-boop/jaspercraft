package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.CombatMath;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.EntityDamageSource;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Forge's three damage events onto one Bukkit EntityDamageEvent:
 *  LOWEST  : pre-attack (vanilla crit emulation, fire-aspect pre-ignite), LivingAttackEvent handlers
 *            (may cancel), LivingHurtEvent handlers (base damage before armor, recomputes modifiers,
 *            applies SME armor piercing to the ARMOR modifier);
 *  HIGHEST : LivingDamageEvent handlers (health damage after armor/resistance/EPF/absorption);
 *  MONITOR : post-hit work (arthropod-pass marker, knockback, fire, looting, resurrection...).
 * Handlers are ordered by Forge priority, then SME registration order, like the Forge event bus.
 */
public final class Pipeline implements Listener {

    public interface H {
        void run(Ctx c) throws Throwable;
    }

    public static final int HIGHEST = 0, HIGH = 1, NORMAL = 2, LOW = 3, LOWEST = 4;

    private static final class Reg {
        final int prio, order;
        final String name;
        final H h;

        Reg(int prio, int order, String name, H h) {
            this.prio = prio;
            this.order = order;
            this.name = name;
            this.h = h;
        }
    }

    private static final List<Reg> PRE = new ArrayList<>();
    private static final List<Reg> ATTACK = new ArrayList<>();
    private static final List<Reg> HURT = new ArrayList<>();
    private static final List<Reg> DAMAGE = new ArrayList<>();
    private static final List<Reg> POST = new ArrayList<>();
    private static final Comparator<Reg> ORDER = Comparator.comparingInt((Reg r) -> r.prio).thenComparingInt(r -> r.order);

    public static void pre(String name, H h) {
        PRE.add(new Reg(0, PRE.size(), name, h));
    }

    public static void attack(int prio, int order, String name, H h) {
        ATTACK.add(new Reg(prio, order, name, h));
        ATTACK.sort(ORDER);
    }

    public static void hurt(int prio, int order, String name, H h) {
        HURT.add(new Reg(prio, order, name, h));
        HURT.sort(ORDER);
    }

    public static void damage(int prio, int order, String name, H h) {
        DAMAGE.add(new Reg(prio, order, name, h));
        DAMAGE.sort(ORDER);
    }

    public static void post(String name, H h) {
        POST.add(new Reg(0, POST.size(), name, h));
    }

    public static int handlerCount() {
        return PRE.size() + ATTACK.size() + HURT.size() + DAMAGE.size() + POST.size();
    }

    private final Map<EntityDamageEvent, Ctx> active = new IdentityHashMap<>();
    public static long eventsSeen = 0;
    /** -Djaspr.sme.debug=true or /jsme debug: one privacy-safe line per damage event (entity types, numbers) */
    public static boolean debug = Boolean.getBoolean("jaspr.sme.debug");

    private static void run(Reg r, Ctx c) {
        Guard.run(r.name, () -> r.h.run(c));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void lowest(EntityDamageEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        final EntityLiving victim = Nms.living(e.getEntity());
        final DamageSource src = Nms.sourceOf(e);
        if (victim == null || src == null) return;
        final Ctx c = new Ctx(e, victim, src);
        active.put(e, c);
        eventsSeen++;
        for (Reg r : PRE) run(r, c);
        c.attackAmount = (float) e.getDamage() + (c.iframePath ? victim.lastDamage : 0.0F);
        for (Reg r : ATTACK) {
            if (c.attackCanceled) break;
            run(r, c);
        }
        if (c.attackCanceled) {
            e.setCancelled(true);
            return;
        }
        double base = e.getDamage(DamageModifier.BASE);
        double hh = safe(e, DamageModifier.HARD_HAT);
        double bl = safe(e, DamageModifier.BLOCKING);
        c.hurtStart = (float) (base + hh + bl);
        c.hurtAmount = c.hurtStart;
        for (Reg r : HURT) run(r, c);
        c.hurtRan = true;
        Guard.run("pipeline.writeHurt", () -> writeHurt(c));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void highest(EntityDamageEvent e) {
        final Ctx c = active.get(e);
        if (c == null || !c.hurtRan) return;
        double hurtNow = e.getDamage(DamageModifier.BASE) + safe(e, DamageModifier.HARD_HAT) + safe(e, DamageModifier.BLOCKING);
        if (hurtNow <= 0) return; // Forge returns before LivingDamageEvent
        final float cur = (float) e.getFinalDamage();
        c.damageAmount = cur;
        for (Reg r : DAMAGE) {
            if (c.damageCanceled) break;
            run(r, c);
        }
        float target = c.damageCanceled ? 0.0F : c.damageAmount;
        if (Float.compare(target, cur) != 0) {
            Guard.run("pipeline.writeDamage", () -> {
                // carry the LivingDamage delta in MAGIC: it has no side effects in CraftBukkit (armor wear and
                // absorption use other modifiers), so only the health loss changes, exactly like Forge.
                DamageModifier carrier = e.isApplicable(DamageModifier.MAGIC) ? DamageModifier.MAGIC : DamageModifier.BASE;
                e.setDamage(carrier, e.getDamage(carrier) + (target - cur));
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void monitor(EntityDamageEvent e) {
        final Ctx c = active.remove(e);
        if (c == null) return;
        if (debug) {
            Log.info("SME_DEBUG damage victim=" + e.getEntityType() + " cause=" + e.getCause() + " type=" + c.source.translationIndex
                    + " attacker=" + (c.trueSource == null ? "none" : c.trueSource.getBukkitEntity().getType())
                    + " attack=" + c.attackAmount + " hurt=" + c.hurtAmount + " base=" + e.getDamage() + " final=" + e.getFinalDamage()
                    + " canceled=" + e.isCancelled() + " iframes=" + c.iframePath + " health=" + c.victim.getHealth());
        }
        if (e.isCancelled()) {
            if (c.preIgnited) Guard.run("fire.rollback", () -> c.victim.extinguish());
            return;
        }
        for (Reg r : POST) run(r, c);
    }

    private static double safe(EntityDamageEvent e, DamageModifier m) {
        return e.isApplicable(m) ? e.getDamage(m) : 0.0;
    }

    /** LivingHurtEvent result -> base damage; recompute the vanilla modifiers (with SME piercing on ARMOR). */
    private static void writeHurt(Ctx c) {
        EntityDamageEvent e = c.event;
        if (c.hurtStart <= 0.0F) return; // fully blocked: Forge stops before armor
        float pierce = Ctx.piercing(c.source);
        boolean changed = Float.compare(c.hurtAmount, c.hurtStart) != 0;
        if (!changed && pierce <= 0.0F && !c.sourceMadeMagic) return;
        double base = e.getDamage(DamageModifier.BASE);
        double newBase = base;
        if (changed && base > 0) newBase = c.hurtAmount / (c.hurtStart / base);
        else if (changed) newBase = c.hurtAmount;
        e.setDamage(DamageModifier.BASE, newBase);
        double rem = newBase;
        for (DamageModifier m : new DamageModifier[]{DamageModifier.HARD_HAT, DamageModifier.BLOCKING, DamageModifier.ARMOR,
                DamageModifier.RESISTANCE, DamageModifier.MAGIC, DamageModifier.ABSORPTION}) {
            if (!e.isApplicable(m)) continue;
            double v;
            if (m == DamageModifier.ARMOR && pierce > 0.0F && !c.source.ignoresArmor() && c.source instanceof EntityDamageSource) {
                float p = Math.max(0.0F, Math.min(1.0F, pierce));
                float d = (float) rem;
                float piercing = d * p;
                float normal = d - piercing;
                float armor = (float) c.victim.getArmorStrength();
                float tough = (float) c.victim.getAttributeInstance(GenericAttributes.i).getValue();
                v = (piercing + CombatMath.a(normal, armor, tough)) - d;
            } else {
                v = Nms.apply(e, m, rem);
            }
            e.setDamage(m, v);
            rem += v;
        }
    }

    public static void logOrder() {
        StringBuilder sb = new StringBuilder("SME_PIPELINE attack=");
        for (Reg r : ATTACK) sb.append(r.name).append(',');
        Log.info(sb.toString());
    }
}
