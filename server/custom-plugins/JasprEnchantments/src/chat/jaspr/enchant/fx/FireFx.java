package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Random;
import java.util.WeakHashMap;

/**
 * Fire Aspect tiers, Fiery Edge and Extinguish (SME mixin on EnchantmentHelper.getFireAspectModifier and
 * EntityPlayerMixinSetFire), and Advanced Fire Protection (mixin on EnchantmentProtection.getFireTimeForEntity).
 */
public final class FireFx implements Listener {
    private static final Random RAND = new Random();

    /** EnchantmentTierFA.getLevelMult */
    public static int tierMult(int level, int tier) {
        switch (tier) {
            case 0: return RAND.nextInt(2) * level;
            case 1: return 2 * level;
            case 2: return 4 * level;
            default: return 0;
        }
    }

    /** SME-modified EnchantmentHelper.getFireAspectModifier (one roll per attack, cached per tick). */
    private static final WeakHashMap<EntityLiving, int[]> CACHE = new WeakHashMap<>();

    public static int modifier(EntityLiving e) {
        if (e == null) return 0;
        int tick = e.world.getMinecraftServer() == null ? 0 : net.minecraft.server.v1_12_R1.MinecraftServer.currentTick;
        int[] c = CACHE.get(e);
        if (c != null && c[0] == tick) return c[1];
        int v = compute(e);
        CACHE.put(e, new int[]{tick, v});
        return v;
    }

    private static int compute(EntityLiving e) {
        ItemStack stack = e.getItemInMainHand();
        int original = EnchantmentManager.getFireAspectEnchantmentLevel(e);
        if (Nms.level(E.EXTINGUISH, stack) > 0) return 0;
        int tier = tierMult(Nms.level(E.LESSERFIREASPECT, stack), 0) + tierMult(Nms.level(E.ADVANCEDFIREASPECT, stack), 1)
                + tierMult(Nms.level(E.SUPREMEFIREASPECT, stack), 2);
        int fiery = 2 * Nms.level(E.FIERYEDGE, stack);
        return original + tier + fiery;
    }

    /** SME-modified EnchantmentProtection.getFireTimeForEntity */
    public static int fireTime(EntityLiving e, int duration) {
        int i = EnchantmentManager.a(Enchantments.PROTECTION_FIRE, e) + 2 * (2 * Nms.maxLevel(E.ADVANCEDFIREPROTECTION, e));
        if (i > 0) duration -= MathHelper.d((float) duration * (float) i * 0.15F);
        return duration;
    }

    /** Entity.setFire(seconds) with SME fire protection. */
    public static void setFire(Entity e, int seconds) {
        int i = seconds * 20;
        if (e instanceof EntityLiving) i = fireTime((EntityLiving) e, i);
        if (e.fireTicks < i) e.fireTicks = i;
    }

    // ------------------------------------------------------------ pipeline hooks

    public static void register() {
        // EntityPlayerMixinSetFire: SME-only fire aspect (vanilla level 0) ignites before the hit
        Pipeline.pre("fire.preIgnite", c -> {
            if (c.event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            if (!(c.trueSource instanceof EntityHuman) || c.immediate != c.trueSource || c.source.isSweep()) return;
            if (!"player".equals(c.source.translationIndex)) return;
            EntityHuman p = (EntityHuman) c.trueSource;
            if (EnchantmentManager.getFireAspectEnchantmentLevel(p) > 0) return; // vanilla path handled by the combust event
            int j = modifier(p);
            if (j > 0 && !c.victim.isBurning()) {
                setFire(c.victim, j * 4);
                c.preIgnited = true;
            }
        });
        // mob attackers: SME-only fire aspect ignites after a successful hit
        Pipeline.post("fire.postIgnite", c -> {
            if (c.event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || c.source.isSweep()) return;
            EntityLiving a = c.attacker();
            if (a == null || c.immediate != a) return;
            if (EnchantmentManager.getFireAspectEnchantmentLevel(a) > 0) return;
            int j = modifier(a);
            if (j > 0) setFire(c.victim, j * 4);
        });
    }

    /** Vanilla fire aspect events from a living attacker: use SME's modified level (0 cancels). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCombustByEntity(EntityCombustByEntityEvent e) {
        Guard.run("fire.combustByEntity", () -> {
            if (!(e.getCombuster() instanceof LivingEntity)) return;
            EntityLiving a = Nms.living(e.getCombuster());
            if (a == null) return;
            int j = modifier(a);
            if (j <= 0) e.setCancelled(true);
            else e.setDuration(j * 4);
        });
    }

    /** Advanced Fire Protection shortens every burn (SME changes getFireTimeForEntity for all fire). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        Guard.run("fire.advancedFireProtection", () -> {
            if (!(e.getEntity() instanceof LivingEntity)) return;
            EntityLiving v = Nms.living(e.getEntity());
            if (v == null || Nms.maxLevel(E.ADVANCEDFIREPROTECTION, v) <= 0) return;
            int ticks = fireTime(v, e.getDuration() * 20);
            e.setCancelled(true);
            if (v.fireTicks < ticks) v.fireTicks = ticks;
        });
    }

    @SuppressWarnings("unused")
    private static boolean isBlockCombust(EntityCombustEvent e) {
        return e instanceof EntityCombustByBlockEvent;
    }
}
