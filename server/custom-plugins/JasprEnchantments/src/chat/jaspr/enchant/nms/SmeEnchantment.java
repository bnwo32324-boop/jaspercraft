package chat.jaspr.enchant.nms;

import chat.jaspr.enchant.data.ItemTypes;
import chat.jaspr.enchant.data.SmeDef;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentSlotType;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import net.minecraft.server.v1_12_R1.EnumMonsterType;
import net.minecraft.server.v1_12_R1.ItemStack;

import java.util.Collections;
import java.util.Set;

/**
 * A real NMS enchantment for one SME enchantment. Vanilla code paths (enchanting table, anvil,
 * loot functions, villager trades, damage calculation, EPF, thorns and arthropod passes) call these
 * overrides directly, which is what gives exact vanilla crit/cooldown scaling and EPF behaviour.
 *
 * Obfuscated NMS names used here (v1_12_R1): a(int)=getMinEnchantability, b(int)=getMaxEnchantability,
 * getStartLevel=getMinLevel, a(int,DamageSource)=calcModifierDamage, a(int,EnumMonsterType)=calcDamageByCreature,
 * a(Enchantment)=canApplyTogether, canEnchant(ItemStack)=canApply, a(EntityLiving,Entity,int)=onEntityDamaged,
 * b(EntityLiving,Entity,int)=onUserHurt, c(String)=setName.
 */
public final class SmeEnchantment extends Enchantment {

    /** Behaviour hooks, installed on enable (registration happens in onLoad before listeners exist). */
    public interface Hooks {
        float damageByCreature(SmeEnchantment ench, int level, EnumMonsterType type);

        int modifierDamage(SmeEnchantment ench, int level, DamageSource source);

        void onEntityDamaged(SmeEnchantment ench, EntityLiving user, Entity target, int level);

        void onUserHurt(SmeEnchantment ench, EntityLiving user, Entity attacker, int level);
    }

    public static volatile Hooks hooks = null;

    public final SmeDef def;
    public final boolean exactTarget;
    private volatile Set<Enchantment> incompatible = Collections.emptySet();

    public SmeEnchantment(SmeDef def, Rarity rarity, EnchantmentSlotType target, EnumItemSlot[] slots, boolean exactTarget) {
        super(rarity, target, slots);
        this.def = def;
        this.exactTarget = exactTarget;
        c(def.regName);
    }

    public void setIncompatible(Set<Enchantment> set) {
        this.incompatible = set;
    }

    public Set<Enchantment> incompatible() {
        return incompatible;
    }

    @Override
    public int getMaxLevel() {
        return def.maxLevel;
    }

    @Override
    public int getStartLevel() {
        return def.minLevel;
    }

    @Override
    public int a(int level) {
        return def.minEnchantability(level);
    }

    @Override
    public int b(int level) {
        return def.maxEnchantability(level);
    }

    @Override
    public boolean isTreasure() {
        return def.treasure;
    }

    @Override
    public boolean isCursed() {
        return def.curse;
    }

    /** EnchantmentBase.canApplyTogether: not in the SME incompatible set, and not itself. */
    @Override
    protected boolean a(Enchantment other) {
        return other != this && !incompatible.contains(other);
    }

    /** SME table set ("Can apply on enchantment table"), what Forge calls canApplyAtEnchantingTable. */
    public boolean tableApplies(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SlotTypes.tableAllows(def, stack.getItem());
    }

    /** SME canApply = anvil-additional set OR the table set (Forge Enchantment.canApply delegates to the table check). */
    @Override
    public boolean canEnchant(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) return false;
            return ItemTypes.canItemApply(def.anvilTypes, stack.getItem()) || tableApplies(stack);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public float a(int level, EnumMonsterType type) {
        Hooks h = hooks;
        if (h == null) return 0.0F;
        try {
            return h.damageByCreature(this, level, type);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    @Override
    public int a(int level, DamageSource source) {
        Hooks h = hooks;
        if (h == null) return 0;
        try {
            return h.modifierDamage(this, level, source);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public void a(EntityLiving user, Entity target, int level) {
        Hooks h = hooks;
        if (h == null) return;
        try {
            h.onEntityDamaged(this, user, target, level);
        } catch (Throwable ignored) {
            // handled/logged inside the hook implementation
        }
    }

    @Override
    public void b(EntityLiving user, Entity attacker, int level) {
        Hooks h = hooks;
        if (h == null) return;
        try {
            h.onUserHurt(this, user, attacker, level);
        } catch (Throwable ignored) {
            // handled/logged inside the hook implementation
        }
    }

    @Override
    public String toString() {
        return "SmeEnchantment{" + def.regName + "#" + def.id + "}";
    }
}
