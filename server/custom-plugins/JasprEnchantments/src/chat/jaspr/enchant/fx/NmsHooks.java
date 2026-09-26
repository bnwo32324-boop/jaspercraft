package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.nms.Registrar;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import net.minecraft.server.v1_12_R1.EnumMonsterType;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Behaviour behind the NMS overrides of {@link SmeEnchantment}: calcDamageByCreature, calcModifierDamage,
 * onEntityDamaged (SME's onEntityDamagedAlt: main hand only, once per arthropod pass) and onUserHurt.
 */
public final class NmsHooks implements SmeEnchantment.Hooks {

    // ------------------------------------------------------------------ calcDamageByCreature

    @Override
    public float damageByCreature(SmeEnchantment ench, int level, EnumMonsterType type) {
        switch (ench.def.smeClass) {
            case "EnchantmentTierDamage":
                return tierDamage(ench.def.variant, level, type);
            case "EnchantmentJaggedRake":
                return 1.0F + (float) level * 0.55F;
            case "EnchantmentReinforcedSharpness":
                return 2.0F + 1.3F * (float) level;
            case "EnchantmentBluntness":
                return -1.0F * level;
            default:
                return 0.0F;
        }
    }

    /** EnchantmentTierDamage.calcDamageByCreature including its switch fall-through. */
    static float tierDamage(int damageType, int level, EnumMonsterType t) {
        switch (damageType) {
            case 0: return 0.25F + 0.25F * (float) level;
            case 1: return 1.25F + 0.95F * (float) level;
            case 2: return 4.0F + 1.6F * (float) level;
            case 3: if (t == EnumMonsterType.UNDEAD) return 1.25F * (float) level;
            case 4: if (t == EnumMonsterType.UNDEAD) return 3.25F * (float) level;
            case 5: if (t == EnumMonsterType.UNDEAD) return 5.0F * (float) level;
            case 6: if (t == EnumMonsterType.ARTHROPOD) return 1.25F * (float) level;
            case 7: if (t == EnumMonsterType.ARTHROPOD) return 3.25F * (float) level;
            case 8: if (t == EnumMonsterType.ARTHROPOD) return 5.0F * (float) level;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ calcModifierDamage (EPF)

    @Override
    public int modifierDamage(SmeEnchantment ench, int level, DamageSource s) {
        if (s.ignoresInvulnerability()) return 0;
        switch (ench.def.regName) {
            case "advancedblastprotection": return s.isExplosion() ? level * 3 : 0;
            case "advancedfeatherfalling": return s == DamageSource.FALL ? level * 5 : 0;
            case "advancedfireprotection": return s.o() ? level * 3 : 0;
            case "advancedprojectileprotection": return s.a() ? level * 3 : 0;
            case "advancedprotection": return level * 2;
            case "magicprotection": return s.isMagic() ? level * 2 : 0;
            case "supremeprotection": return 8;
            case "physicalprotection": {
                String t = s.translationIndex;
                boolean excluded = s.isMagic() || s.o() || s.isExplosion() || s.a() || t.equals("outOfWorld") || t.equals("drown")
                        || t.equals("generic") || t.equals("wither") || t.equals("lightningBolt") || t.equals("inFire")
                        || t.equals("onFire") || t.equals("hotFloor") || t.equals("Ethereal") || t.equals("Culled");
                return excluded ? 0 : level * 3;
            }
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ onEntityDamaged -> onEntityDamagedAlt

    /** (attacker -> victim, tick) of hits that succeeded; the arthropod pass after each one runs the Alt hooks once. */
    private static final WeakHashMap<EntityLiving, Map<Entity, Integer>> PENDING = new WeakHashMap<>();
    public static long altPasses = 0;

    public static void markHit(EntityLiving attacker, Entity victim) {
        Map<Entity, Integer> m = PENDING.get(attacker);
        if (m == null) {
            m = new WeakHashMap<>();
            PENDING.put(attacker, m);
        }
        m.put(victim, MinecraftServer.currentTick);
    }

    private static boolean consume(EntityLiving attacker, Entity victim) {
        Map<Entity, Integer> m = PENDING.get(attacker);
        if (m == null) return false;
        Integer t = m.remove(victim);
        return t != null && t >= MinecraftServer.currentTick - 1;
    }

    public static void cleanup() {
        Iterator<Map.Entry<EntityLiving, Map<Entity, Integer>>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map<Entity, Integer> m = it.next().getValue();
            m.values().removeIf(t -> t < MinecraftServer.currentTick - 2);
            if (m.isEmpty()) it.remove();
        }
    }

    @Override
    public void onEntityDamaged(SmeEnchantment ench, EntityLiving user, Entity target, int level) {
        if (user == null || target == null) return;
        if (!consume(user, target)) return;
        altPasses++;
        // EnchantmentHelperMixin.applyArthropodEnchantments HEAD: main hand stack, every SME enchantment on it in NBT order
        ItemStack stack = user.getItemInMainHand();
        if (stack.isEmpty()) return;
        NBTTagList list = stack.getEnchantments();
        for (int i = 0; i < list.size(); i++) {
            NBTTagCompound tag = list.get(i);
            int id = tag.getShort("id");
            int lvl = tag.getShort("lvl");
            Enchantment e = Enchantment.c(id);
            if (e instanceof SmeEnchantment) {
                final SmeEnchantment se = (SmeEnchantment) e;
                Guard.run("alt." + se.def.regName, () -> AltFx.alt(se, user, target, stack, lvl));
            }
        }
    }

    // ------------------------------------------------------------------ onUserHurt (thorns family)

    @Override
    public void onUserHurt(SmeEnchantment ench, EntityLiving user, Entity attacker, int level) {
        if (user == null || attacker == null) return;
        String r = ench.def.regName;
        if (!r.equals("advancedthorns") && !r.equals("burningthorns") && !r.equals("meltdown")) return;
        Guard.run("thorns." + r, () -> thorns(ench, user, attacker, level));
    }

    private static void thorns(SmeEnchantment ench, EntityLiving user, Entity attacker, int level) {
        Random random = user.getRandom();
        ItemStack stack = EnchantmentManager.b(ench, user);
        if (stack.isEmpty()) return;
        int lvl = EnchantmentManager.getEnchantmentLevel(ench, stack);
        if (lvl <= 0) return;
        switch (ench.def.regName) {
            case "advancedthorns":
                if (lvl > 0 && random.nextFloat() < 0.05F + (0.20F * (float) lvl)) {
                    attacker.damageEntity(DamageSource.a(user), (float) (2 + lvl + random.nextInt(lvl + 3)));
                    ItemsFx.damageItem(stack, 4 + random.nextInt(lvl), user);
                } else {
                    ItemsFx.damageItem(stack, 2 + random.nextInt(lvl), user);
                }
                break;
            case "burningthorns":
                // SME uses the pass level here (not the stack level)
                if (level > 0 && random.nextFloat() < (0.15F * (float) level)) {
                    attacker.damageEntity(DamageSource.a(user), (float) (2 + random.nextInt(4)));
                    FireFx.setFire(attacker, level + 2);
                    if (!user.isBurning()) ItemsFx.damageItem(stack, 4, user);
                } else {
                    if (!user.isBurning()) ItemsFx.damageItem(stack, 2, user);
                }
                break;
            case "meltdown":
                if (lvl > 0 && random.nextFloat() < 0.05F + (0.05F * (float) lvl)) {
                    attacker.world.createExplosion(user, user.locX, user.locY, user.locZ, 1.90F + 0.30F * lvl, true, false);
                    ItemsFx.damageItem(stack, 10 + random.nextInt(15 * lvl), user);
                } else {
                    ItemsFx.damageItem(stack, 1 + random.nextInt(2 * lvl), user);
                }
                break;
            default:
                break;
        }
    }

    public static void register() {
        SmeEnchantment.hooks = new NmsHooks();
        // a successful hit (event not cancelled) is followed by vanilla's arthropod pass
        Pipeline.post("alt.markHit", c -> {
            EntityLiving a = c.attacker();
            if (a != null) markHit(a, c.victim);
        });
        Log.info("SME_HOOKS installed enchantments=" + Registrar.BY_INDEX.length);
    }

    @SuppressWarnings("unused")
    private static EnumItemSlot unused() {
        return EnumItemSlot.MAINHAND;
    }

    static int lvl(Enchantment e, EntityLiving ent) {
        return Nms.mainLevel(e, ent);
    }

    static boolean isExtinguish(Enchantment e) {
        return e == E.EXTINGUISH;
    }
}
