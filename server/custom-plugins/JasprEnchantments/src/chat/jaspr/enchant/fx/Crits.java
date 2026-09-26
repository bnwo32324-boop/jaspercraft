package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.MobEffects;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Random;
import java.util.WeakHashMap;

/**
 * Forge CriticalHitEvent for Luck Magnification (HIGH) and Critical Strike (LOWEST). Bukkit has no
 * crit event, so the vanilla crit decision is re-derived: the player's cooldown strength c is solved
 * from the vanilla damage formula base = A*(0.2+0.8c^2)*m + E*c, and the crit conditions are
 * checked exactly as EntityHuman.attack does (Paper's disablePlayerCrits included).
 */
public final class Crits implements Listener {
    private static final WeakHashMap<EntityHuman, float[]> STRENGTH = new WeakHashMap<>();
    /** players whose crit was forced by Luck Magnification this tick: their sweep is suppressed (flag2 blocks sweeping) */
    private static final WeakHashMap<EntityHuman, Integer> FORCED = new WeakHashMap<>();

    public static float lastStrength(EntityHuman h) {
        float[] v = STRENGTH.get(h);
        if (v == null || (int) v[0] != MinecraftServer.currentTick) return 1.0F;
        return v[1];
    }

    static float solve(float am, float e, float base) {
        double a = 0.8 * am, b = e, c = 0.2 * am - base;
        double x;
        if (Math.abs(a) < 1.0E-9) x = Math.abs(b) < 1.0E-9 ? 1.0 : -c / b;
        else x = (-b + Math.sqrt(Math.max(0.0, b * b - 4 * a * c))) / (2 * a);
        if (Double.isNaN(x)) x = 1.0;
        return (float) Math.max(0.0, Math.min(1.0, x));
    }

    public static void register() {
        Pipeline.pre("crit", c -> {
            if (c.event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            if (!(c.trueSource instanceof EntityHuman) || c.immediate != c.trueSource) return;
            if (!"player".equals(c.source.translationIndex) || c.source.isSweep()) return;
            EntityHuman p = (EntityHuman) c.trueSource;
            ItemStack stack = p.getItemInMainHand();
            float A = (float) p.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue();
            float En = EnchantmentManager.a(stack, c.victim.getMonsterType());
            float base = (float) c.event.getDamage();
            boolean cond = p.fallDistance > 0.0F && !p.onGround && !p.m_() && !p.isInWater() && !p.hasEffect(MobEffects.BLINDNESS)
                    && !p.isPassenger() && !p.world.paperConfig.disablePlayerCrits && !p.isSprinting();
            boolean vanillaCrit = false;
            float strength;
            if (cond) {
                float c1 = solve(A * 1.5F, En, base);
                if (c1 > 0.9F) {
                    vanillaCrit = true;
                    strength = c1;
                } else strength = solve(A, En, base);
            } else strength = solve(A, En, base);
            STRENGTH.put(p, new float[]{MinecraftServer.currentTick, strength});

            int lm = Nms.level(E.LUCKMAGNIFICATION, stack);
            int cs = Nms.level(E.CRITICALSTRIKE, stack);
            if (lm <= 0 && cs <= 0) return;
            Random rng = p.getRandom();
            float modifier = vanillaCrit ? 1.5F : 1.0F;
            final float startMod = modifier;
            int result = 0; // 0 DEFAULT, 1 ALLOW
            // Luck Magnification (HIGH)
            if (lm > 0) {
                float luck = (float) p.getAttributeInstance(GenericAttributes.j).getValue();
                if (luck > 0) {
                    if (result != 1 && !vanillaCrit) {
                        if (rng.nextFloat() < Math.min(0.2F, 0.01F * luck * (float) lm)) result = 1;
                    }
                    if (result == 1 || vanillaCrit) {
                        if (rng.nextFloat() < Math.min(0.2F, 0.02F * luck * (float) lm)) {
                            modifier += Math.min(luck * 0.1F * (float) lm, 2.0F);
                        }
                    }
                }
            }
            // Critical Strike (LOWEST)
            if (cs > 0 && (result == 1 || vanillaCrit)) {
                NBTTagCompound tag = stack.getTag();
                if (tag == null) tag = new NBTTagCompound();
                int counter = 1 + tag.getInt("CriticalStrikeFailCount");
                int maxChance = 1000 - 50 * cs;
                int chance = 32 * counter;
                if (cs < 20 && rng.nextInt(maxChance) >= chance) {
                    tag.setInt("CriticalStrikeFailCount", counter);
                    Fx.customSound(p, "critical_strike_fail", 0.8F, 1.0F + (float) (2.0F * chance / maxChance));
                    modifier = Math.max(1.5F, modifier);
                } else {
                    tag.setInt("CriticalStrikeFailCount", 0);
                    float crit = 1.0F + 0.5F * (float) cs + 0.5F * (float) cs * rng.nextFloat();
                    Fx.customSound(p, "critical_strike", 0.8F, 1.0F / (1.2F + 0.4F * rng.nextFloat()) * 1.6F);
                    modifier += crit;
                }
                result = 1;
                stack.setTag(tag);
            }
            boolean isCrit = result == 1 || vanillaCrit;
            if (!isCrit) return;
            float f = A * (0.2F + strength * strength * 0.8F);
            if (modifier != startMod) {
                c.event.setDamage(c.event.getDamage() + f * (modifier - startMod));
            }
            if (!vanillaCrit) {
                // forced crit: vanilla crit sound + particles, and no sweep for this attack
                Fx.sound(p, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                p.a(c.victim);
                FORCED.put(p, MinecraftServer.currentTick);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSweep(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        net.minecraft.server.v1_12_R1.Entity d = Nms.entity(e.getDamager());
        if (!(d instanceof EntityHuman)) return;
        Integer t = FORCED.get(d);
        if (t != null && t == MinecraftServer.currentTick) e.setCancelled(true);
    }
}
