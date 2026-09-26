package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.BiomeBase;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityMagmaCube;
import net.minecraft.server.v1_12_R1.EntityPig;
import net.minecraft.server.v1_12_R1.EntityPigZombie;
import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.EntityZombieVillager;
import net.minecraft.server.v1_12_R1.EnumMonsterType;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.MobEffect;
import net.minecraft.server.v1_12_R1.MobEffectList;
import net.minecraft.server.v1_12_R1.MobEffects;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Every SME onEntityDamagedAlt implementation, ported 1:1 (RLCombat branches resolve to "no RLCombat"). */
public final class AltFx {
    private AltFx() {}

    /** SME config: Sol's Blessing glowing is suppressed by the JasperCraft owner rule (no glowing effects). */
    public static boolean solsBlessingGlowing = false;

    private static final List<MobEffectList> HORS_DE_COMBAT = Arrays.asList(
            MobEffects.SLOWER_MOVEMENT, MobEffects.SLOWER_DIG, MobEffects.HUNGER, MobEffects.WEAKNESS,
            MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.WITHER, MobEffects.POISON);

    static void effect(EntityLiving e, MobEffectList p, int duration, int amp) {
        e.addEffect(new MobEffect(p, duration, amp));
    }

    public static void alt(SmeEnchantment ench, EntityLiving attacker, Entity target, ItemStack weapon, int level) {
        if (attacker == null) return;
        if (!(target instanceof EntityLiving)) return;
        EntityLiving victim = (EntityLiving) target;
        if (weapon.isEmpty()) return;
        Random rng = attacker.getRandom();
        String r = ench.def.regName;
        switch (ench.def.smeClass) {
            case "EnchantmentTierDamage": {
                int t = ench.def.variant;
                EnumMonsterType mt = victim.getMonsterType();
                if (t == 4 && mt == EnumMonsterType.UNDEAD) effect(victim, MobEffects.WEAKNESS, 60 + rng.nextInt(15 * level), 0);
                else if (t == 5 && mt == EnumMonsterType.UNDEAD) effect(victim, MobEffects.WEAKNESS, 80 + rng.nextInt(20 * level), 1);
                else if (t == 6 && mt == EnumMonsterType.ARTHROPOD) effect(victim, MobEffects.SLOWER_MOVEMENT, 10 + rng.nextInt(5 * level), 1);
                else if (t == 7 && mt == EnumMonsterType.ARTHROPOD) effect(victim, MobEffects.SLOWER_MOVEMENT, 30 + rng.nextInt(15 * level), 4);
                else if (t == 8 && mt == EnumMonsterType.ARTHROPOD) effect(victim, MobEffects.SLOWER_MOVEMENT, 40 + rng.nextInt(20 * level), 5);
                return;
            }
            case "EnchantmentExtinguish":
                target.extinguish();
                return;
            case "EnchantmentBrutality": {
                int count = 0;
                for (ItemStack s : victim.getArmorItems()) if (!s.isEmpty()) count++;
                for (ItemStack s : victim.getArmorItems()) {
                    if (!s.isEmpty() && s.f()) {
                        ItemsFx.damageItem(s, (int) (s.k() * 0.0025F * (float) level / count + victim.getRandom().nextInt(level + 2)) + 1, victim);
                    }
                }
                return;
            }
            case "EnchantmentAncientSealedCurses": {
                List<Enchantment> curses = Fx.curses();
                if (curses.isEmpty()) return;
                for (ItemStack eq : victim.aQ()) {
                    if (eq.isEmpty()) continue;
                    if (!eq.canEnchant()) continue;
                    if (rng.nextFloat() < 0.125F) {
                        Enchantment curse = curses.get(rng.nextInt(curses.size()));
                        int curseLevel = 1 + rng.nextInt(curse.getMaxLevel());
                        if (!curse.canEnchant(eq)) continue;
                        Map<Enchantment, Integer> enchants = EnchantmentManager.a(eq);
                        boolean compatible = true;
                        for (Enchantment ex : enchants.keySet()) {
                            if (curse == ex || !curse.c(ex)) {
                                compatible = false;
                                break;
                            }
                        }
                        if (compatible) eq.addEnchantment(curse, curseLevel);
                    }
                }
                return;
            }
            case "EnchantmentDarkShadows":
                if (rng.nextFloat() < 0.1F * (float) level && attacker.aw() <= 0.1F) {
                    victim.addEffect(new MobEffect(MobEffects.BLINDNESS, 160));
                    victim.a((EntityLiving) null);
                }
                return;
            case "EnchantmentFlinging":
                victim.impulse = true;
                victim.motY += 0.075D + 0.1875D * (double) level;
                if (!Double.isFinite(victim.motY)) victim.motY = 0;
                victim.velocityChanged = true;
                return;
            case "EnchantmentCryogenic": {
                MobEffect slow = victim.getEffect(MobEffects.SLOWER_MOVEMENT);
                MobEffect fatigue = victim.getEffect(MobEffects.SLOWER_DIG);
                int inc = target instanceof EntityHuman ? 1 : 2;
                int slowAmp = slow != null ? Math.min(slow.getAmplifier() + inc, 3) : inc - 1;
                int fatAmp = fatigue != null ? Math.min(fatigue.getAmplifier() + inc, 3) : inc - 1;
                effect(victim, MobEffects.SLOWER_MOVEMENT, 80, slowAmp);
                effect(victim, MobEffects.SLOWER_DIG, 80, fatAmp);
                return;
            }
            case "EnchantmentDesolator":
                if (rng.nextFloat() <= 0.15F * (float) level) {
                    MobEffect res = victim.getEffect(MobEffects.RESISTANCE);
                    int amp = -level;
                    if (res != null) amp = Math.min(res.getAmplifier(), amp);
                    victim.removeEffect(MobEffects.RESISTANCE);
                    effect(victim, MobEffects.RESISTANCE, 40 + (level * 10), amp);
                    if (level > 2) effect(victim, MobEffects.WEAKNESS, 40 + (level * 10), level - 3);
                }
                return;
            case "EnchantmentDisorientatingBlade":
                if (rng.nextFloat() <= 0.15F * (float) level) {
                    effect(victim, MobEffects.CONFUSION, 40 + (level * 10), level - 1);
                    if (level > 2) effect(victim, MobEffects.BLINDNESS, 40 + (level * 10), level - 3);
                }
                return;
            case "EnchantmentEnvenomed":
                if (rng.nextFloat() <= 0.2F * (float) level) {
                    effect(victim, MobEffects.POISON, 40 + (10 * level), level - 1);
                    if (level > 2) effect(victim, MobEffects.WITHER, 40 + (10 * level), level - 1);
                }
                return;
            case "EnchantmentHorsDeCombat":
                if (rng.nextFloat() <= 0.2F * (float) level) {
                    int index = rng.nextInt(HORS_DE_COMBAT.size());
                    effect(victim, HORS_DE_COMBAT.get(index), 20 + level * 10, level - 1);
                }
                return;
            case "EnchantmentLevitator":
                effect(victim, MobEffects.LEVITATION, 20 + level * 10, level);
                return;
            case "EnchantmentPurification":
                purification(victim, level);
                return;
            case "EnchantmentLunasBlessing":
                if (!attacker.world.D() && Fx.canSeeSky(attacker)) {
                    victim.addEffect(new MobEffect(MobEffects.BLINDNESS, 20 + 10 * level));
                }
                return;
            case "EnchantmentSolsBlessing":
                if (attacker.world.D() && Fx.canSeeSky(attacker) && solsBlessingGlowing) {
                    victim.addEffect(new MobEffect(MobEffects.GLOWING, 20 + 10 * level));
                }
                return;
            case "EnchantmentSubjectEnchantments":
                subject(ench.def.variant, ench.def.maxLevel, attacker, victim, level, rng);
                return;
            default:
                if (r.isEmpty()) return;
        }
    }

    private static void purification(EntityLiving victim, int level) {
        if (victim.dead || victim.getHealth() <= 0.0F) return;
        if (victim.cc()) {
            effect(victim, MobEffects.WEAKNESS, 20 + level * 10, Math.max(0, Math.min(1, level - 1)));
            effect(victim, MobEffects.SLOWER_MOVEMENT, 20 + level * 10, Math.max(0, Math.min(2, level - 1)));
        }
        if (victim.getRandom().nextFloat() <= 0.05F * (float) level) convert(victim);
    }

    private static void convert(EntityLiving entity) {
        if (entity instanceof EntityZombieVillager) {
            entity.setSilent(true);
            try {
                // EntityZombieVillager.finishConversion (protected, obfuscated "dt")
                Nms.invoke(entity, EntityZombieVillager.class, "dt", new Class<?>[0]);
            } catch (Throwable t) {
                entity.setSilent(false);
                throw new RuntimeException("zombie villager conversion unavailable", t);
            }
        } else if (entity instanceof EntityPigZombie) {
            EntityPig pig = new EntityPig(entity.world);
            pig.u(entity);
            if (entity.isBaby()) pig.setAgeRaw(-24000);
            entity.setSilent(true);
            entity.world.removeEntity(entity);
            pig.setNoAI(((EntityPigZombie) entity).isNoAI());
            if (entity.hasCustomName()) {
                pig.setCustomName(entity.getCustomName());
                pig.setCustomNameVisible(entity.getCustomNameVisible());
            }
            entity.world.addEntity(pig);
            pig.addEffect(new MobEffect(MobEffects.CONFUSION, 200, 0));
            pig.addEffect(new MobEffect(MobEffects.HEAL, 1, 1));
            entity.world.a(null, 1027, new BlockPosition((int) pig.locX, (int) pig.locY, (int) pig.locZ), 0);
            pig.noDamageTicks = pig.maxNoDamageTicks;
        } else if (entity instanceof EntityMagmaCube) {
            EntitySlime slime = new EntitySlime(entity.world);
            slime.u(entity);
            // SME copies the NBT "Size" (size-1) and re-reads it, i.e. setSlimeSize(size, false)
            slime.setSize(((EntityMagmaCube) entity).getSize(), false);
            entity.setSilent(true);
            entity.world.removeEntity(entity);
            slime.setNoAI(((EntityMagmaCube) entity).isNoAI());
            if (entity.hasCustomName()) {
                slime.setCustomName(entity.getCustomName());
                slime.setCustomNameVisible(entity.getCustomNameVisible());
            }
            entity.world.addEntity(slime);
            slime.addEffect(new MobEffect(MobEffects.CONFUSION, 200, 0));
            slime.addEffect(new MobEffect(MobEffects.HEAL, 1, 1));
            entity.world.a(null, 1027, new BlockPosition((int) slime.locX, (int) slime.locY, (int) slime.locZ), 0);
            slime.noDamageTicks = slime.maxNoDamageTicks;
        }
    }

    private static void subject(int type, int maxLevel, EntityLiving attacker, EntityLiving victim, int level, Random rng) {
        if (type == 5) { // PE
            if (rng.nextFloat() < 0.05F * (float) level) {
                effect(attacker, MobEffects.FASTER_DIG, 120 + (level * 20), Math.min(3, level - 1));
                effect(attacker, MobEffects.FASTER_MOVEMENT, 80 + (level * 20), Math.min(3, level - 1));
                if (level > 2) {
                    effect(attacker, MobEffects.INCREASE_DAMAGE, 60 + (level * 20), level - 3);
                    effect(attacker, MobEffects.JUMP, 60 + (level * 20), level - 3);
                }
                if (level > 4) effect(attacker, MobEffects.RESISTANCE, 20 + (level * 20), level - 5);
            }
        } else if (type == 7) { // GEOGRAPHY
            String[] types = BiomeTypes.of(attacker.world.getBiome(new BlockPosition(attacker)));
            int amp = 1;
            int dur = (200 * level) / maxLevel;
            if (BiomeTypes.has(types, "HOT") || BiomeTypes.has(types, "NETHER")) {
                FireFx.setFire(victim, level * 2);
            } else if (BiomeTypes.has(types, "COLD") || BiomeTypes.has(types, "SNOWY")) {
                effect(victim, MobEffects.SLOWER_MOVEMENT, dur, amp);
                effect(victim, MobEffects.SLOWER_DIG, dur, amp);
            } else if (BiomeTypes.has(types, "SWAMP")) {
                effect(victim, MobEffects.POISON, dur, amp);
            } else if (BiomeTypes.has(types, "WASTELAND")) {
                effect(victim, MobEffects.BLINDNESS, dur * 2, 0);
            } else if (BiomeTypes.has(types, "SPOOKY") || BiomeTypes.has(types, "DEAD")) {
                effect(victim, MobEffects.WITHER, dur, amp);
            } else if (BiomeTypes.has(types, "MAGICAL")) {
                effect(attacker, MobEffects.REGENERATION, dur / 2, 0);
            }
        }
    }

    /** Forge BiomeDictionary.registerVanillaBiomes (1.12.2) for the vanilla biomes, with makeBestGuess otherwise. */
    public static final class BiomeTypes {
        private static final java.util.Map<String, String[]> MAP = new java.util.HashMap<>();

        private static void p(String key, String... types) {
            MAP.put(key, types);
        }

        static {
            p("ocean", "OCEAN");
            p("plains", "PLAINS");
            p("desert", "HOT", "DRY", "SANDY");
            p("extreme_hills", "MOUNTAIN", "HILLS");
            p("forest", "FOREST");
            p("taiga", "COLD", "CONIFEROUS", "FOREST");
            p("swampland", "WET", "SWAMP");
            p("river", "RIVER");
            p("hell", "HOT", "DRY", "NETHER");
            p("sky", "COLD", "DRY", "END");
            p("frozen_ocean", "COLD", "OCEAN", "SNOWY");
            p("frozen_river", "COLD", "RIVER", "SNOWY");
            p("ice_flats", "COLD", "SNOWY", "WASTELAND");
            p("ice_mountains", "COLD", "SNOWY", "MOUNTAIN");
            p("mushroom_island", "MUSHROOM", "RARE");
            p("mushroom_island_shore", "MUSHROOM", "BEACH", "RARE");
            p("beaches", "BEACH");
            p("desert_hills", "HOT", "DRY", "SANDY", "HILLS");
            p("forest_hills", "FOREST", "HILLS");
            p("taiga_hills", "COLD", "CONIFEROUS", "FOREST", "HILLS");
            p("smaller_extreme_hills", "MOUNTAIN");
            p("jungle", "HOT", "WET", "DENSE", "JUNGLE");
            p("jungle_hills", "HOT", "WET", "DENSE", "JUNGLE", "HILLS");
            p("jungle_edge", "HOT", "WET", "JUNGLE", "FOREST", "RARE");
            p("deep_ocean", "OCEAN");
            p("stone_beach", "BEACH");
            p("cold_beach", "COLD", "BEACH", "SNOWY");
            p("birch_forest", "FOREST");
            p("birch_forest_hills", "FOREST", "HILLS");
            p("roofed_forest", "SPOOKY", "DENSE", "FOREST");
            p("taiga_cold", "COLD", "CONIFEROUS", "FOREST", "SNOWY");
            p("taiga_cold_hills", "COLD", "CONIFEROUS", "FOREST", "SNOWY", "HILLS");
            p("redwood_taiga", "COLD", "CONIFEROUS", "FOREST");
            p("redwood_taiga_hills", "COLD", "CONIFEROUS", "FOREST", "HILLS");
            p("extreme_hills_with_trees", "MOUNTAIN", "FOREST", "SPARSE");
            p("savanna", "HOT", "SAVANNA", "PLAINS", "SPARSE");
            p("savanna_rock", "HOT", "SAVANNA", "PLAINS", "SPARSE", "RARE");
            p("mesa", "MESA", "SANDY");
            p("mesa_rock", "MESA", "SPARSE", "SANDY");
            p("mesa_clear_rock", "MESA", "SANDY");
            p("void", "VOID");
            p("mutated_plains", "PLAINS", "RARE");
            p("mutated_desert", "HOT", "DRY", "SANDY", "RARE");
            p("mutated_extreme_hills", "MOUNTAIN", "SPARSE", "RARE");
            p("mutated_forest", "FOREST", "HILLS", "RARE");
            p("mutated_taiga", "COLD", "CONIFEROUS", "FOREST", "MOUNTAIN", "RARE");
            p("mutated_swampland", "WET", "SWAMP", "HILLS", "RARE");
            p("mutated_ice_flats", "COLD", "SNOWY", "HILLS", "RARE");
            p("mutated_jungle", "HOT", "WET", "DENSE", "JUNGLE", "MOUNTAIN", "RARE");
            p("mutated_jungle_edge", "HOT", "SPARSE", "JUNGLE", "HILLS", "RARE");
            p("mutated_birch_forest", "FOREST", "DENSE", "HILLS", "RARE");
            p("mutated_birch_forest_hills", "FOREST", "DENSE", "MOUNTAIN", "RARE");
            p("mutated_roofed_forest", "SPOOKY", "DENSE", "FOREST", "MOUNTAIN", "RARE");
            p("mutated_taiga_cold", "COLD", "CONIFEROUS", "FOREST", "SNOWY", "MOUNTAIN", "RARE");
            p("mutated_redwood_taiga", "DENSE", "FOREST", "RARE");
            p("mutated_redwood_taiga_hills", "DENSE", "FOREST", "HILLS", "RARE");
            p("mutated_extreme_hills_with_trees", "MOUNTAIN", "SPARSE", "RARE");
            p("mutated_savanna", "HOT", "DRY", "SPARSE", "SAVANNA", "MOUNTAIN", "RARE");
            p("mutated_savanna_rock", "HOT", "DRY", "SPARSE", "SAVANNA", "HILLS", "RARE");
            p("mutated_mesa", "HOT", "DRY", "SPARSE", "SAVANNA", "MOUNTAIN", "RARE");
            p("mutated_mesa_rock", "HOT", "DRY", "SPARSE", "HILLS", "RARE");
            p("mutated_mesa_clear_rock", "HOT", "DRY", "SPARSE", "SAVANNA", "MOUNTAIN", "RARE");
        }

        public static String[] of(BiomeBase biome) {
            MinecraftKey key = BiomeBase.REGISTRY_ID.b(biome);
            if (key != null && "minecraft".equals(key.b())) {
                String[] t = MAP.get(key.getKey());
                if (t != null) return t;
            }
            // Forge makeBestGuess: temperature/rainfall classification
            java.util.List<String> l = new java.util.ArrayList<>();
            float temp = biome.getTemperature();
            if (temp > 0.85F) l.add("HOT");
            else if (temp < 0.15F) l.add("COLD");
            if (biome.getHumidity() > 0.85F) l.add("WET");
            else if (biome.getHumidity() < 0.15F) l.add("DRY");
            return l.toArray(new String[0]);
        }

        public static boolean has(String[] types, String t) {
            for (String s : types) if (s.equals(t)) return true;
            return false;
        }
    }
}
