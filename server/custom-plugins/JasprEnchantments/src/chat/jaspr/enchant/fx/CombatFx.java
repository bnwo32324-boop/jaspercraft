package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.AttributeInstance;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityAnimal;
import net.minecraft.server.v1_12_R1.EntityBlaze;
import net.minecraft.server.v1_12_R1.EntityCreeper;
import net.minecraft.server.v1_12_R1.EntityDamageSource;
import net.minecraft.server.v1_12_R1.EntityEnderDragon;
import net.minecraft.server.v1_12_R1.EntityEnderman;
import net.minecraft.server.v1_12_R1.EntityEvoker;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityMagmaCube;
import net.minecraft.server.v1_12_R1.EntityMonster;
import net.minecraft.server.v1_12_R1.EntityVex;
import net.minecraft.server.v1_12_R1.EntityWitch;
import net.minecraft.server.v1_12_R1.EntityWither;
import net.minecraft.server.v1_12_R1.EnumDifficulty;
import net.minecraft.server.v1_12_R1.EnumHand;
import net.minecraft.server.v1_12_R1.EnumMonsterType;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.ItemShield;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Material;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MobEffect;
import net.minecraft.server.v1_12_R1.MobEffectList;
import net.minecraft.server.v1_12_R1.MobEffects;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static chat.jaspr.enchant.fx.Pipeline.HIGH;
import static chat.jaspr.enchant.fx.Pipeline.HIGHEST;
import static chat.jaspr.enchant.fx.Pipeline.LOW;
import static chat.jaspr.enchant.fx.Pipeline.LOWEST;
import static chat.jaspr.enchant.fx.Pipeline.NORMAL;

/**
 * All SME LivingAttackEvent / LivingHurtEvent / LivingDamageEvent handlers, ported line by line.
 * Registration order index = SME registry index (Forge calls same-priority listeners in registration order).
 */
public final class CombatFx {
    private CombatFx() {}

    public static boolean evasionDodgeEffect = true;
    public static boolean extraProtectionEffects = true;
    public static boolean atomicDeconstructorBosses = false;
    static Plugin plugin;

    // SME instance state
    private static boolean handlingArcSlash = false;
    private static boolean deconstructing = false;
    private static boolean bypassingIframe = false;
    private static EntityLiving bypassingEntity = null;

    private static int o(SmeEnchantment e) {
        return e.def.index;
    }

    static int main(SmeEnchantment e, EntityLiving ent) {
        return Nms.mainLevel(e, ent);
    }

    /** SME sets hurtResistantTime=0 in LivingAttackEvent so the hit takes the full, non-iframe path. */
    static boolean inIframes(Ctx c) {
        return !c.bypassed && (float) c.victim.noDamageTicks > (float) c.victim.maxNoDamageTicks / 2.0F;
    }

    static void bypass(Ctx c) {
        if (!inIframes(c)) return;
        c.bypassed = true;
        if (c.iframePath) {
            // Bukkit only reports iframe hits whose damage exceeds lastDamage: restore the full amount and the
            // full-path iframe window (knockback/hurt animation of the full path cannot be replayed here)
            c.event.setDamage(c.event.getDamage() + c.victim.lastDamage);
            c.victim.noDamageTicks = c.victim.maxNoDamageTicks;
        } else {
            c.victim.noDamageTicks = 0;
        }
    }

    private static EntityLiving livingImmediate(Ctx c) {
        return c.immediate instanceof EntityLiving ? (EntityLiving) c.immediate : null;
    }

    private static boolean meleeType(Ctx c) {
        String t = c.source.translationIndex;
        return "player".equals(t) || "mob".equals(t);
    }

    public static void register(Plugin pl) {
        plugin = pl;
        registerAttack();
        registerHurt();
        registerDamage();
    }

    // ================================================================== LivingAttackEvent

    private static void registerAttack() {
        Pipeline.attack(HIGHEST, o(E.EVASION), "evasion", c -> {
            if (c.source.a()) return;
            EntityLiving victim = c.victim;
            EntityLiving attacker = livingImmediate(c);
            if (attacker == null) return;
            int level = Fx.max(E.EVASION, victim);
            if (level <= 0) return;
            if (main(E.TRUESTRIKE, attacker) > 0) return;
            Random r = victim.getRandom();
            if (r.nextFloat() < 0.05F + ((float) level * 0.15F)) {
                if (evasionDodgeEffect) {
                    double randX = 0.65 + r.nextDouble() * 0.25f;
                    randX = r.nextBoolean() ? randX * -1 : randX;
                    double randZ = 0.65 + r.nextDouble() * 0.25f;
                    randZ = r.nextBoolean() ? randZ * -1 : randZ;
                    Fx.knockBackIgnoreKBRes(victim, 0.7f, (attacker.locX - victim.locX) * randX, (attacker.locZ - victim.locZ) * randZ);
                }
                Fx.sound(victim, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.3f, r.nextFloat() * 2.25f + 0.75f);
                c.attackCanceled = true;
                if ((float) victim.noDamageTicks <= (float) victim.maxNoDamageTicks / 2.0F) {
                    victim.noDamageTicks = victim.maxNoDamageTicks + (5 * (level - 1));
                }
            }
        });
        Pipeline.attack(HIGHEST, o(E.MAGMAWALKER), "magmawalker", c -> {
            if (c.source != DamageSource.LAVA && c.source != DamageSource.HOT_FLOOR) return;
            if (c.source == DamageSource.LAVA && c.victim.au()) return;
            if (Fx.max(E.MAGMAWALKER, c.victim) > 0) c.attackCanceled = true;
        });
        Pipeline.attack(HIGHEST, o(E.CURSEOFINACCURACY), "curseofinaccuracy", c -> {
            if (!c.allowed()) return;
            EntityLiving a = c.attacker();
            ItemStack w = a.getItemInMainHand();
            if (w.isEmpty()) return;
            int level = Nms.level(E.CURSEOFINACCURACY, w);
            if (level > 0 && a.getRandom().nextFloat() < ((float) level * 0.20F)) c.attackCanceled = true;
        });
        Pipeline.attack(HIGH, o(E.ATOMICDECONSTRUCTOR), "atomicdeconstructor", c -> {
            if (!c.allowed()) return;
            if (c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            EntityLiving victim = c.victim;
            if (victim instanceof EntityHuman) return;
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            if (deconstructing) return;
            int level = Nms.level(E.ATOMICDECONSTRUCTOR, stack);
            if (level > 0) {
                boolean nonBoss = !(victim instanceof EntityWither) && !(victim instanceof EntityEnderDragon);
                if (nonBoss || atomicDeconstructorBosses) {
                    Random wr = a.world.random;
                    if (wr.nextFloat() < 0.001F * (float) level) {
                        Fx.customSound(a, "atomic_deconstruct", 2.0F, 1.0F / (wr.nextFloat() * 0.4F + 1.2F) * 1.4F);
                        c.attackCanceled = true;
                        deconstructing = true;
                        try {
                            victim.damageEntity(SmeSources.DECONSTRUCTED, Float.MAX_VALUE);
                        } finally {
                            deconstructing = false;
                        }
                    }
                }
            }
        });
        Pipeline.attack(HIGH, o(E.DISARMAMENT), "disarmament", c -> {
            if (!c.allowed()) return;
            if (c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            EntityLiving victim = c.victim;
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.DISARMAMENT, stack);
            if (level <= 0) return;
            Random r = a.getRandom();
            if (r.nextFloat() < 0.02F * (float) level) {
                if (!victim.getItemInMainHand().isEmpty()) {
                    if (victim instanceof EntityInsentient && r.nextFloat() >= ((EntityInsentient) victim).dropChanceHand[0]) return;
                    ItemStack held = victim.getItemInMainHand();
                    victim.a(EnumHand.MAIN_HAND, ItemStack.a);
                    dropAt(victim, held, 0.5F);
                } else if (!victim.getItemInOffHand().isEmpty()) {
                    if (victim instanceof EntityInsentient && r.nextFloat() >= ((EntityInsentient) victim).dropChanceHand[1]) return;
                    ItemStack held = victim.getItemInOffHand();
                    victim.a(EnumHand.OFF_HAND, ItemStack.a);
                    dropAt(victim, held, 0.5F);
                }
            }
        });
        Pipeline.attack(NORMAL, o(E.BURNINGSHIELD), "burningshield", c -> {
            if (c.source.a()) return;
            EntityLiving victim = c.victim;
            EntityLiving attacker = livingImmediate(c);
            if (attacker == null || !meleeType(c)) return;
            if (!Fx.canBlock(c.source, victim)) return;
            int level = Fx.max(E.BURNINGSHIELD, victim);
            if (level > 0 && victim.getRandom().nextFloat() < 0.4F + 0.1F * (float) level) {
                float revenge = c.attackAmount * 0.1F * (float) level;
                attacker.damageEntity(new SmeSources.Entity("onFire", victim).fire(), revenge);
                FireFx.setFire(attacker, 4 + 2 * level);
            }
        });
        Pipeline.attack(NORMAL, o(E.COUNTERATTACK), "counterattack", c -> {
            if (c.source.a()) return;
            EntityLiving victim = c.victim;
            EntityLiving attacker = livingImmediate(c);
            if (attacker == null || !meleeType(c)) return;
            int level = Fx.max(E.COUNTERATTACK, victim);
            if (level > 0) {
                int parry = Fx.max(E.PARRY, victim);
                if (victim.getRandom().nextFloat() < 0.05F + (0.05F * (float) level) + (0.01F * (float) parry)) {
                    attacker.damageEntity(new EntityDamageSource("thorns", victim).w(), c.attackAmount * 0.2F * (float) level);
                }
            }
        });
        Pipeline.attack(LOW, o(E.EMPOWEREDDEFENCE), "empowereddefence", c -> {
            if (c.source.a()) return;
            EntityLiving victim = c.victim;
            EntityLiving attacker = livingImmediate(c);
            if (attacker == null || !meleeType(c)) return;
            if (!Fx.canBlock(c.source, victim)) return;
            int level = Fx.max(E.EMPOWEREDDEFENCE, victim);
            if (level > 0 && victim.getRandom().nextFloat() < 0.2F + 0.05F * (float) level) {
                float revenge = c.attackAmount * 0.225F * (float) level;
                attacker.damageEntity(new EntityDamageSource("magic", victim).setMagic(), revenge);
                Fx.knockBackIgnoreKBRes(attacker, 0.4F + 0.2F * (float) level, victim.locX - attacker.locX, victim.locZ - attacker.locZ);
                c.attackCanceled = true;
                victim.noDamageTicks = victim.maxNoDamageTicks - 5;
            }
        });
        Pipeline.attack(LOW, o(E.PARRY), "parry", c -> {
            if (c.source.a()) return;
            EntityLiving victim = c.victim;
            EntityLiving attacker = livingImmediate(c);
            if (attacker == null || !meleeType(c)) return;
            int level = Fx.max(E.PARRY, victim);
            if (level > 0) {
                if (main(E.TRUESTRIKE, attacker) > 0) return;
                int counter = Fx.max(E.COUNTERATTACK, victim);
                if (victim.getRandom().nextFloat() < 0.05F + (0.05F * (float) level) + (0.01F * (float) counter)) {
                    Fx.knockBackIgnoreKBRes(attacker, 0.3F + 0.15F * (float) level, victim.locX - attacker.locX, victim.locZ - attacker.locZ);
                    Fx.sound(attacker, Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.3F, 3.0F);
                    c.attackCanceled = true;
                    if ((float) victim.noDamageTicks <= (float) victim.maxNoDamageTicks / 2.0F) {
                        victim.noDamageTicks = victim.maxNoDamageTicks + (5 * (level - 1));
                    }
                }
            }
        });
        Pipeline.attack(LOWEST, o(E.FIERYEDGE), "fieryedge", c -> {
            if (!c.allowed() || c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.FIERYEDGE, stack);
            if (level <= 0) return;
            if (c.victim.isBurning() && inIframes(c)) {
                if (a.getRandom().nextFloat() < 0.05F * (float) level) bypass(c);
            }
        });
        Pipeline.attack(LOWEST, o(E.SWIFTERSLASHES), "swifterslashes", c -> {
            if (!c.allowed() || c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.SWIFTERSLASHES, stack);
            if (level <= 0) return;
            if (a.getRandom().nextFloat() < 0.01F * (float) level && inIframes(c)) {
                bypass(c);
                bypassingIframe = true;
                bypassingEntity = a;
            }
        });
        Pipeline.attack(LOWEST, o(E.TRUESTRIKE), "truestrike", c -> {
            if (!c.allowed() || c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.TRUESTRIKE, stack);
            if (level > 0) {
                if (a.getRandom().nextFloat() < 0.01F * (float) level && inIframes(c)) bypass(c);
            }
        });
        Pipeline.attack(LOWEST, o(E.UNREASONABLE), "unreasonable", c -> {
            if (!c.allowed() || c.attackAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            if (!(c.victim instanceof EntityInsentient)) return;
            final EntityInsentient victim = (EntityInsentient) c.victim;
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.UNREASONABLE, stack);
            if (level <= 0) return;
            if (a.getRandom().nextFloat() < 0.05F * (float) level) {
                double g = Math.min(3 + 3 * level, 16);
                com.google.common.base.Predicate<EntityInsentient> pred = en -> en != a && en != victim;
                List<EntityInsentient> entities = a.world.a(EntityInsentient.class, victim.getBoundingBox().g(g), pred);
                if (entities.isEmpty()) return;
                final EntityInsentient target = entities.get(a.getRandom().nextInt(entities.size()));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!victim.dead && !target.dead && !victim.getUniqueID().equals(target.getUniqueID())) {
                            victim.a((EntityLiving) target);
                            victim.setGoalTarget(target);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        });
    }

    static void dropAt(EntityLiving e, ItemStack stack, float offsetY) {
        if (stack.isEmpty()) return;
        Location l = new Location(e.world.getWorld(), e.locX, e.locY + offsetY, e.locZ);
        e.world.getWorld().dropItem(l, org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asBukkitCopy(stack));
    }

    // ================================================================== LivingHurtEvent

    /** common SME LivingHurt guard: allowed source, amount > 1, living attacker */
    private static EntityLiving hurtAttacker(Ctx c) {
        if (!c.allowed()) return null;
        if (c.hurtAmount <= 1.0F) return null;
        return c.attacker();
    }

    private static void registerHurt() {
        Pipeline.hurt(HIGHEST, o(E.ANCIENTSWORDMASTERY), "ancientswordmastery", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.ANCIENTSWORDMASTERY, a);
            if (level > 0) {
                AttributeInstance attr = c.victim.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE);
                if (attr == null) return;
                float enemy = (float) attr.getValue();
                c.hurtAmount = c.hurtAmount + Math.min((float) level / E.ANCIENTSWORDMASTERY.getMaxLevel() * 1.0F * MathHelper.c(enemy), 12);
            }
        });
        Pipeline.hurt(HIGHEST, o(E.ARCSLASH), "arcslash", CombatFx::arcSlash);
        Pipeline.hurt(HIGHEST, o(E.RUNE_MAGICALBLESSING), "rune_magicalblessing", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            EntityLiving victim = c.victim;
            int level = main(E.RUNE_MAGICALBLESSING, a);
            if (level <= 0) return;
            if (c.source instanceof EntityDamageSource) {
                float cur = Ctx.piercing(c.source);
                Ctx.setPiercing(c.source, Math.min(cur + 0.1F * (float) level, 1.0F));
                c.source.setMagic();
                c.sourceMadeMagic = true;
            }
            Random r = a.getRandom();
            if (r.nextFloat() >= 0.05 * level) return;
            int amplifier = Math.max(0, r.nextInt(level) - 1);
            MobEffectList nega = Potions.negative(r);
            if (nega == null) return;
            if (!nega.isInstant()) {
                int duration = (1 + r.nextInt(6)) * 20 * level;
                victim.addEffect(new MobEffect(nega, duration, amplifier));
            } else {
                if (nega == MobEffects.HARM && victim.cc()) nega = MobEffects.HEAL;
                nega.applyInstantEffect(a, a, victim, amplifier, 1.0D);
            }
        });
        // ---- HIGH (registry order)
        Pipeline.hurt(HIGH, o(E.BUTCHERING), "butchering", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.BUTCHERING, a);
            if (level > 0 && c.victim instanceof EntityAnimal) c.hurtAmount += 2.0F * (float) level;
        });
        Pipeline.hurt(HIGH, o(E.CLEARSKIESFAVOR), "clearskiesfavor", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.CLEARSKIESFAVOR, a);
            if (level <= 0) return;
            if (!a.world.isRaining() && !a.world.X()) {
                float dmg = 1.0F + 0.75F * (float) level;
                if (!Fx.canSeeSky(a)) dmg -= 0.5F + 0.5F * (float) level;
                c.hurtAmount += dmg;
            } else if (a.getRandom().nextFloat() < 0.003F * (float) level) Fx.setClear(a.world);
        });
        Pipeline.hurt(HIGH, o(E.DARKSHADOWS), "darkshadows", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.DARKSHADOWS, a);
            if (level > 0 && a.aw() <= 0.1F) c.hurtAmount += (1.0F + 2.5F * (float) level);
        });
        Pipeline.hurt(HIGH, o(E.DEFUSINGEDGE), "defusingedge", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.DEFUSINGEDGE, a);
            if (level > 0 && c.victim instanceof EntityCreeper) {
                c.hurtAmount += 2.5F * (float) level;
                if (a.getRandom().nextFloat() < 1.0F) Nms.defuseCreeper((EntityCreeper) c.victim);
            }
        });
        Pipeline.hurt(HIGH, o(E.INHUMANE), "inhumane", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.INHUMANE, a);
            EntityLiving v = c.victim;
            if (level > 0 && (v.getMonsterType() == EnumMonsterType.ILLAGER || v instanceof EntityWitch || v instanceof EntityVex)) {
                v.addEffect(new MobEffect(MobEffects.WEAKNESS, 70 + (level * 10), 1));
                c.hurtAmount += 2.5F * (float) level;
            }
        });
        Pipeline.hurt(HIGH, o(E.LUNASBLESSING), "lunasblessing", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.LUNASBLESSING, a);
            if (level <= 0 || a.world.D()) return;
            float dmg = 1.5F + 0.75F * (float) level;
            if (!Fx.canSeeSky(a)) dmg -= 1.0F + 0.5F * (float) level;
            c.hurtAmount += dmg;
        });
        Pipeline.hurt(HIGH, o(E.MORTALITAS), "mortalitas", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            ItemStack stack = a.getItemInMainHand();
            int level = Nms.level(E.MORTALITAS, stack);
            if (level > 0) {
                NBTTagCompound tag = stack.getTag();
                if (tag == null) return;
                c.hurtAmount += tag.getFloat("MortalitasDamage");
            }
        });
        Pipeline.hurt(HIGH, o(E.PENETRATINGEDGE), "penetratingedge", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.PENETRATINGEDGE, a);
            if (level > 0) {
                float armor = c.victim.getArmorStrength();
                if (armor > 2) c.hurtAmount += Math.min(15.0F, (armor / 12.0F) * (float) level);
            }
        });
        Pipeline.hurt(HIGH, o(E.RAINSBESTOWMENT), "rainsbestowment", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.RAINSBESTOWMENT, a);
            if (level <= 0) return;
            if (a.world.isRaining()) {
                if (a.getRandom().nextFloat() < 0.001F * (float) level) Fx.setThundering(a.world);
                float dmg = 1.25F + 1.0F * (float) level;
                if (!Fx.canSeeSky(a)) dmg -= 0.75F + 0.75F * (float) level;
                c.hurtAmount += dmg;
            } else if (a.getRandom().nextFloat() < 0.002F * (float) level) Fx.setRaining(a.world);
        });
        Pipeline.hurt(HIGH, o(E.SOLSBLESSING), "solsblessing", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.SOLSBLESSING, a);
            if (level <= 0 || !a.world.D()) return;
            float dmg = 1.5F + 0.75F * (float) level;
            if (!Fx.canSeeSky(a)) dmg -= 1.0F + 0.5F * (float) level;
            c.hurtAmount += dmg;
        });
        Pipeline.hurt(HIGH, o(E.SPELLBREAKER), "spellbreaker", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.SPELLBREAKER, a);
            if (level <= 0) return;
            Collection<MobEffect> effects = c.victim.getEffects();
            if (!effects.isEmpty()) c.hurtAmount += Math.min(15.0F, 0.625F * (float) effects.size() * (float) level);
            if (c.victim instanceof EntityWitch || c.victim instanceof EntityEvoker) c.hurtAmount += 1.75F * (float) level;
        });
        Pipeline.hurt(HIGH, o(E.THUNDERSTORMSBESTOWMENT), "thunderstormsbestowment", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.THUNDERSTORMSBESTOWMENT, a);
            if (level <= 0) return;
            if (a.world.X()) {
                float dmg = 1.5F + 1.25F * (float) level;
                if (!Fx.canSeeSky(a)) dmg -= 1.0F + 1.0F * (float) level;
                c.hurtAmount += dmg;
            } else if (a.getRandom().nextFloat() < 0.001F * (float) level) Fx.setThundering(a.world);
        });
        Pipeline.hurt(HIGH, o(E.VIPER), "viper", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.VIPER, a);
            if (level > 0) {
                float dmg = 0.F;
                if (c.victim.hasEffect(MobEffects.POISON)) dmg += (1.75F + 0.75F * (float) level);
                if (c.victim.hasEffect(MobEffects.WITHER)) dmg += (1.0F + 0.5F * (float) level);
                c.hurtAmount += dmg;
            }
        });
        Pipeline.hurt(HIGH, o(E.WATERASPECT), "wateraspect", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.WATERASPECT, a);
            if (level <= 0) return;
            EntityLiving v = c.victim;
            float dmg = 0.0F;
            if (v instanceof EntityEnderman || v instanceof EntityBlaze || v instanceof EntityMagmaCube) dmg += 2.5F * (float) level;
            if (v.isInWater() || v.an()) dmg += 0.75F * (float) level;
            if (a.an() || a.isInWater()) dmg += 0.75F * (float) level;
            c.hurtAmount += dmg;
            if (v.isBurning()) v.extinguish();
        });
        Pipeline.hurt(HIGH, o(E.WINTERSGRACE), "wintersgrace", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.WINTERSGRACE, a);
            if (level <= 0) return;
            BlockPosition pos = new BlockPosition(a.locX, a.locY, a.locZ);
            float temp = a.world.getBiome(pos).a(pos);
            if (temp <= 0.3F) {
                float dmg = 2.0F + 1.5F * (float) level;
                if (!a.world.isRaining()) dmg -= 0.5F + 0.25F * (float) level;
                if (!Fx.canSeeSky(a)) dmg -= 0.5F + 0.75F * (float) level;
                c.hurtAmount += dmg;
                if (a.getRandom().nextFloat() < 0.04F * (float) level) {
                    c.victim.addEffect(new MobEffect(MobEffects.SLOWER_DIG, 20 + 10 * level, Math.max(0, level - 3)));
                    if (level > 3) c.victim.addEffect(new MobEffect(MobEffects.SLOWER_MOVEMENT, 20 + 10 * level, Math.max(0, level - 4)));
                }
            }
        });
        for (SmeEnchantment s : new SmeEnchantment[]{E.SUBJECTBIOLOGY, E.SUBJECTCHEMISTRY, E.SUBJECTENGLISH, E.SUBJECTHISTORY,
                E.SUBJECTMATHEMATICS, E.SUBJECTPE, E.SUBJECTPHYSICS, E.SUBJECTGEOGRAPHY}) {
            final SmeEnchantment se = s;
            Pipeline.hurt(HIGH, o(se), se.def.regName, c -> subjectHurt(se, c));
        }
        // ---- NORMAL
        Pipeline.hurt(NORMAL, o(E.NATURALBLOCKING), "naturalblocking", c -> {
            EntityLiving v = c.victim;
            if (v.isBlocking() || c.source.ignoresArmor()) return;
            int level = Fx.max(E.NATURALBLOCKING, v);
            if (level > 0) {
                ItemStack shield = v.getItemInMainHand();
                if (shield.isEmpty() || !(shield.getItem() instanceof ItemShield)) {
                    shield = v.getItemInOffHand();
                    if (shield.isEmpty() || !(shield.getItem() instanceof ItemShield)) return;
                }
                float blocked = c.hurtAmount * (0.1F * (float) level);
                c.hurtAmount = c.hurtAmount - blocked;
                ItemsFx.damageItem(shield, (int) (1.0F + 1.5F * blocked), v);
            }
        });
        // ---- LOW
        Pipeline.hurt(LOW, o(E.BLESSEDEDGE), "blessededge", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.BLESSEDEDGE, a);
            if (level > 0 && c.victim.getMonsterType() == EnumMonsterType.UNDEAD) {
                heal(a, c.hurtAmount * 0.1F * (float) level);
                c.hurtAmount = c.hurtAmount * (1.0F + 0.1F * (float) level);
            }
        });
        Pipeline.hurt(LOW, o(E.CRYOGENIC), "cryogenic", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.CRYOGENIC, a);
            if (level <= 0) return;
            EntityLiving v = c.victim;
            MobEffect slow = v.getEffect(MobEffects.SLOWER_MOVEMENT);
            MobEffect fat = v.getEffect(MobEffects.SLOWER_DIG);
            if (slow != null && slow.getAmplifier() >= 1 && fat != null && fat.getAmplifier() >= 1) {
                v.extinguish();
                if (a.getRandom().nextFloat() <= 0.1F * (float) level) {
                    c.hurtAmount = c.hurtAmount * (1.0F + 0.2F * (float) level);
                    int range = Math.min(4, (level + 1) / 2);
                    BlockPosition base = new BlockPosition(v);
                    for (int x = -range; x <= range; x++)
                        for (int y = -range; y <= range; y++)
                            for (int z = -range; z <= range; z++) {
                                if (Math.abs(x) + Math.abs(y) + Math.abs(z) > range) continue;
                                BlockPosition p = base.a(x, y, z);
                                if (a.world.getType(p).getMaterial() == Material.AIR) {
                                    TempBlocks.placeIce(a.world, p, MathHelper.nextInt(a.getRandom(), 60, 120));
                                }
                            }
                    Fx.sound(v, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, -1f);
                }
            }
        });
        Pipeline.hurt(LOW, o(E.INNERBERSERK), "innerberserk", c -> {
            if (!c.allowed()) return;
            EntityLiving a = c.attacker();
            int level = Fx.max(E.INNERBERSERK, a);
            if (level > 0) {
                float missing = 1.0F - Math.max(0.0F, Math.min(1.0F, a.getHealth() / a.getMaxHealth()));
                float mod = 1.0F + missing * (1.1F + 0.05F * (float) level);
                c.hurtAmount = c.hurtAmount * mod;
            }
        });
        Pipeline.hurt(LOW, o(E.LIFESTEAL), "lifesteal", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.LIFESTEAL, a);
            if (level > 0) heal(a, c.hurtAmount * 0.03F * (float) level);
        });
        Pipeline.hurt(LOW, o(E.PURGINGBLADE), "purgingblade", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            int level = main(E.PURGINGBLADE, a);
            if (level <= 0) return;
            Random r = a.getRandom();
            if (r.nextFloat() < 0.1F + 0.06F * (float) level) {
                MobEffect[] arr = c.victim.getEffects().toArray(new MobEffect[0]);
                if (arr.length == 0) return;
                MobEffect effect = arr[r.nextInt(arr.length)];
                c.hurtAmount = c.hurtAmount * Math.min(2.0F, (1.0F + 0.02F * (float) (1 + effect.getAmplifier()) * (float) level));
                c.victim.removeEffect(effect.getMobEffect());
            }
        });
        Pipeline.hurt(LOW, o(E.SWIFTERSLASHES), "swifterslashes.hurt", c -> {
            if (!(c.trueSource instanceof EntityLiving)) return;
            EntityLiving a = (EntityLiving) c.trueSource;
            if (bypassingIframe && bypassingEntity == a) c.hurtAmount = c.hurtAmount / 2.0F;
            bypassingEntity = null;
            bypassingIframe = false;
        });
        Pipeline.hurt(LOW, o(E.UNSHEATHING), "unsheathing", c -> {
            if (!c.allowed() || c.hurtAmount <= 1.0F) return;
            if (!(c.trueSource instanceof EntityHuman)) return;
            EntityHuman a = (EntityHuman) c.trueSource;
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.UNSHEATHING, stack);
            if (level > 0) {
                TickFx.PlayerState st = TickFx.state(a);
                if (a.ticksLived - st.lastUnsheatheTrigger < 100) return;
                if (a.ticksLived - st.lastSwapTime < 10 + 10 * level) {
                    st.lastUnsheatheTrigger = a.ticksLived;
                    c.hurtAmount = c.hurtAmount * (1.0F + 0.25F * (float) level);
                }
            }
        });
        Pipeline.hurt(LOW, o(E.ADVANCEDBLASTPROTECTION), "advancedblastprotection", c -> {
            if (!extraProtectionEffects || !c.source.isExplosion()) return;
            int modifier = (int) (7.5F * Nms.totalLevel(E.ADVANCEDBLASTPROTECTION, c.victim));
            c.hurtAmount = Fx.afterMagicAbsorb(c.hurtAmount, modifier);
        });
        Pipeline.hurt(LOW, o(E.ADVANCEDFEATHERFALLING), "advancedfeatherfalling", c -> {
            if (!extraProtectionEffects || c.source != DamageSource.FALL) return;
            int modifier = (int) (9.0F * Nms.totalLevel(E.ADVANCEDFEATHERFALLING, c.victim));
            c.hurtAmount = Fx.afterMagicAbsorb(c.hurtAmount, modifier);
        });
        Pipeline.hurt(LOW, o(E.ADVANCEDFIREPROTECTION), "advancedfireprotection", c -> {
            if (!extraProtectionEffects || !c.source.o()) return;
            int modifier = (int) (7.5F * Nms.totalLevel(E.ADVANCEDFIREPROTECTION, c.victim));
            c.hurtAmount = Fx.afterMagicAbsorb(c.hurtAmount, modifier);
        });
        Pipeline.hurt(LOW, o(E.ADVANCEDPROJECTILEPROTECTION), "advancedprojectileprotection", c -> {
            if (!extraProtectionEffects || !c.source.a()) return;
            int modifier = (int) (7.5F * Nms.totalLevel(E.ADVANCEDPROJECTILEPROTECTION, c.victim));
            c.hurtAmount = Fx.afterMagicAbsorb(c.hurtAmount, modifier);
        });
        Pipeline.hurt(LOW, o(E.ADVANCEDPROTECTION), "advancedprotection", c -> {
            if (!extraProtectionEffects) return;
            int modifier = (int) (4.75F * Nms.totalLevel(E.ADVANCEDPROTECTION, c.victim));
            c.hurtAmount = Fx.afterMagicAbsorb(c.hurtAmount, modifier);
        });
        Pipeline.hurt(LOW, o(E.SUPREMEPROTECTION), "supremeprotection", c -> {
            if (!extraProtectionEffects) return;
            int total = Nms.totalLevel(E.SUPREMEPROTECTION, c.victim);
            if (total > 0) {
                float modifier = 1.0F - (0.05F * (float) total / (float) E.SUPREMEPROTECTION.getMaxLevel());
                c.hurtAmount = c.hurtAmount * modifier;
            }
        });
        // ---- LOWEST
        Pipeline.hurt(LOWEST, o(E.RUNE_PIERCINGCAPABILITIES), "rune_piercingcapabilities", c -> {
            EntityLiving a = hurtAttacker(c);
            if (a == null) return;
            if (c.source.ignoresArmor()) return;
            int level = main(E.RUNE_PIERCINGCAPABILITIES, a);
            if (level > 0 && c.source instanceof EntityDamageSource) {
                float cur = Ctx.piercing(c.source);
                Ctx.setPiercing(c.source, Math.min(cur + 0.25F * (float) level, 1.0F));
            }
        });
    }

    static void heal(EntityLiving e, float amount) {
        e.heal(amount, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    private static void arcSlash(Ctx c) {
        if (!c.allowed()) return;
        if (c.hurtAmount <= 1.0F) return;
        EntityLiving attacker = c.attacker();
        EntityLiving victim = c.victim;
        ItemStack stack = attacker.getItemInMainHand();
        if (stack.isEmpty()) return;
        if (handlingArcSlash) return;
        int level = Nms.level(E.ARCSLASH, stack);
        if (level <= 0) return;
        handlingArcSlash = true;
        try {
            int fireLevel = Nms.level(Enchantments.FIRE_ASPECT, stack);
            int lvl = Nms.level(E.FIERYEDGE, stack);
            if (lvl > 0) fireLevel += 2 * lvl;
            lvl = Nms.level(E.LESSERFIREASPECT, stack);
            if (lvl > 0) fireLevel += FireFx.tierMult(lvl, 0);
            lvl = Nms.level(E.ADVANCEDFIREASPECT, stack);
            if (lvl > 0) fireLevel += FireFx.tierMult(lvl, 1);
            lvl = Nms.level(E.SUPREMEFIREASPECT, stack);
            if (lvl > 0) fireLevel += FireFx.tierMult(lvl, 2);
            fireLevel /= 2;
            int levitation = Nms.level(E.LEVITATOR, stack);
            float damage = c.hurtAmount * 0.25F * (float) level;
            AxisAlignedBB box = new AxisAlignedBB(attacker.locX - 5 - level, attacker.locY - 5 - level, attacker.locZ - 5 - level,
                    attacker.locX + 5 + level, attacker.locY + 5 + level, attacker.locZ + 5 + level);
            List<Entity> targets = new ArrayList<>(attacker.world.getEntities(victim, box));
            Vec3D look = attacker.e(1.0F);
            for (Entity target : targets) {
                if (!(target instanceof EntityLiving)) continue;
                if (target == attacker || target == victim) continue;
                if (target.g(attacker) > 3.00F + 0.25F * (float) level) continue;
                Vec3D check = new Vec3D(target.locX - attacker.locX, target.locY - attacker.locY, target.locZ - attacker.locZ);
                double angle = Math.toDegrees(Math.acos(check.a().b(look)));
                if (angle < MathHelper.a(60.0D + (level * 10), 60, 359)) {
                    boolean sourced = false;
                    if (attacker instanceof EntityHuman) {
                        sourced = true;
                        target.damageEntity(new EntityDamageSource("playerCleave", attacker), damage);
                        FireFx.setFire(target, fireLevel * 4);
                        if (levitation > 0) ((EntityLiving) target).addEffect(new MobEffect(MobEffects.LEVITATION, 30 + (levitation * 12), 1 + levitation));
                    }
                    if (attacker instanceof EntityMonster) {
                        sourced = true;
                        target.damageEntity(new EntityDamageSource("mobCleave", attacker), damage);
                        FireFx.setFire(target, fireLevel * 4);
                        if (levitation > 0) ((EntityLiving) target).addEffect(new MobEffect(MobEffects.LEVITATION, 30 + (levitation * 12), 1 + levitation));
                    }
                    if (!sourced) target.damageEntity(DamageSource.GENERIC, damage);
                    int kb = 1;
                    kb += KnockbackFx.modifier(attacker);
                    if (attacker.isSprinting()) kb++;
                    kb /= 2;
                    if (kb > 0) Fx.knockBackIgnoreKBRes(target, 0.3F * kb, attacker.locX - target.locX, attacker.locZ - target.locZ);
                }
            }
            attacker.setSprinting(false);
        } finally {
            handlingArcSlash = false;
        }
    }

    private static int log2(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    private static void subjectHurt(SmeEnchantment se, Ctx c) {
        EntityLiving a = hurtAttacker(c);
        if (a == null) return;
        EntityLiving victim = c.victim;
        int level = main(se, a);
        if (level <= 0) return;
        switch (se.def.variant) {
            case 0: { // BIOLOGY
                if (!(victim instanceof EntityInsentient)) return;
                int tasks = Nms.goalCount(((EntityInsentient) victim).goalSelector);
                if (tasks > 15 - level) c.hurtAmount += Math.min(2.5F * (float) level, 7.5F);
                return;
            }
            case 1: { // CHEMISTRY
                int count = a.getEffects().size();
                if (count > 0) {
                    float dmg = Math.min(0.3F * (float) level * (float) count, 6.0F);
                    c.hurtAmount += dmg;
                    if (a.getRandom().nextFloat() < 0.04 * level) {
                        BlockPosition p = new BlockPosition(victim);
                        a.world.createExplosion(a, p.getX(), p.getY(), p.getZ(), dmg * 2F / 3F, false, false);
                    }
                }
                return;
            }
            case 2: { // ENGLISH
                int count = victim.getName().length();
                if (count > 0) c.hurtAmount += Math.min(0.075F * (float) level * (float) count, 7.5F);
                return;
            }
            case 3: { // HISTORY
                float perc = a.world.D(new BlockPosition(a)).b() / 6.75F;
                if (perc > 0) c.hurtAmount += 1.5F * (float) level * perc;
                return;
            }
            case 4: { // MATHEMATICS
                int count = a instanceof EntityHuman ? ((EntityHuman) a).expLevel / 2 : a.ticksLived / 600;
                float dmg = 0.25F * (float) level * (float) log2(Math.max(1, count));
                if (dmg > 0) c.hurtAmount += Math.min(dmg, 7.5F);
                return;
            }
            case 6: { // PHYSICS
                AxisAlignedBB vb = victim.getBoundingBox();
                AxisAlignedBB ab = a.getBoundingBox();
                double vs = (vb.d - vb.a) * (vb.e - vb.b) * (vb.f - vb.c);
                double as = (ab.d - ab.a) * (ab.e - ab.b) * (ab.f - ab.c);
                if (vs > 0 && as > 0) {
                    float perc = (float) (vs / as);
                    if (perc < 1) perc = 1.0F / perc;
                    c.hurtAmount += Math.min(0.3F * (float) level * perc, 7.5F);
                }
                return;
            }
            default:
        }
    }

    // ================================================================== LivingDamageEvent

    private static void registerDamage() {
        Pipeline.damage(HIGH, o(E.UNPREDICTABLE), "unpredictable", c -> {
            if (!c.allowed()) return;
            EntityLiving a = c.attacker();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.UNPREDICTABLE, stack);
            if (level > 0) {
                Random r = a.getRandom();
                float damage = r.nextFloat() * ((float) level + 1.0F) * c.damageAmount;
                c.damageAmount = damage;
                if (r.nextFloat() < (float) level * 0.2F) {
                    c.damageCanceled = true;
                    heal(c.victim, damage);
                }
            }
        });
        Pipeline.damage(NORMAL, o(E.DRAGGING), "dragging.melee", c -> {
            if (!(c.trueSource instanceof EntityLiving)) return;
            EntityLiving a = (EntityLiving) c.trueSource;
            int level = main(E.DRAGGING, a);
            if (level <= 0) return;
            double m = -0.6F * (1.25F + level * 1.75F);
            float pi = 0.017453292F;
            c.victim.f(-MathHelper.sin(a.yaw * pi) * m, 0.1, MathHelper.cos(a.yaw * pi) * m);
            c.victim.velocityChanged = true;
        });
        Pipeline.damage(LOW, o(E.ASHDESTROYER), "ashdestroyer", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            int level = main(E.ASHDESTROYER, c.attacker());
            if (level > 0 && c.victim.isBurning()) {
                float mult = Math.min(1.0F, c.victim.fireTicks / 640.F);
                c.damageAmount = c.damageAmount * (1.0F + 0.2F * (float) level * mult);
            }
        });
        Pipeline.damage(LOW, o(E.DIFFICULTYSENDOWMENT), "difficultysendowment", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            int level = main(E.DIFFICULTYSENDOWMENT, a);
            if (level <= 0) return;
            EnumDifficulty d = a.world.getDifficulty();
            if (d == EnumDifficulty.PEACEFUL) c.damageAmount = c.damageAmount * 0.1F * (float) level;
            else if (d == EnumDifficulty.EASY) c.damageAmount = c.damageAmount * (0.25F + 0.1F * (float) level);
            else if (d == EnumDifficulty.HARD) {
                if (!a.world.getWorldData().isHardcore()) c.damageAmount = c.damageAmount * (1.0F + 0.1F * (float) level);
                else c.damageAmount = c.damageAmount * (1.0F + 0.2F * (float) level);
            }
        });
        Pipeline.damage(LOW, o(E.REVILEDBLADE), "reviledblade", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            int level = main(E.REVILEDBLADE, c.attacker());
            if (level <= 0) return;
            if (c.victim.getMaxHealth() <= 0) return;
            float percent = 1.0F - MathHelper.a(c.victim.getHealth() / c.victim.getMaxHealth(), 0.0F, 1.0F);
            c.damageAmount = c.damageAmount * (1.0F + percent * (float) level / 2.0F);
        });
        Pipeline.damage(LOW, o(E.CURSEDEDGE), "cursededge", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            int level = main(E.CURSEDEDGE, a);
            if (level > 0) {
                float damage = c.damageAmount * (1.0F + 0.4F * (float) level);
                c.damageAmount = damage;
                a.damageEntity(DamageSource.MAGIC, damage * 0.25F);
            }
        });
        Pipeline.damage(LOW, o(E.CURSEOFVULNERABILITY), "curseofvulnerability", c -> {
            int level = Nms.totalLevel(E.CURSEOFVULNERABILITY, c.victim);
            if (level > 0) c.damageAmount = c.damageAmount * (1.0F + (float) level * 0.20F);
        });
        Pipeline.damage(LOW, o(E.INSTABILITY), "instability", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.INSTABILITY, stack);
            if (level > 0 && stack.f()) {
                float p = (float) stack.i() / (float) stack.k();
                p = 1.0F + p * 0.75F * (float) level;
                c.damageAmount = c.damageAmount * p;
                ItemsFx.damageItem(stack, 1 + (int) (a.getRandom().nextFloat() * c.damageAmount / (float) Math.max(8 - level, 1)), a);
            }
        });
        Pipeline.damage(LOWEST, o(E.CULLING), "culling", c -> {
            if (!c.allowed() || c.damageAmount <= 1.0F) return;
            EntityLiving a = c.attacker();
            int level = main(E.CULLING, a);
            if (level <= 0) return;
            EntityLiving v = c.victim;
            float cur = v.getHealth();
            if (cur - c.damageAmount <= 0) return;
            if ((cur - c.damageAmount) / v.getMaxHealth() <= 0.075F + 0.025F * (float) level) {
                // SME's World.spawnParticle calls are no-ops on a dedicated server
                if (a.getRandom().nextFloat() < 0.001F) Fx.customSound(a, "culling", 2.0F, 1.0F);
                c.damageAmount = Math.max(c.damageAmount, v.getMaxHealth()) * 10.0F;
                Ctx.setCulling(c.source);
            }
        });
    }
}
