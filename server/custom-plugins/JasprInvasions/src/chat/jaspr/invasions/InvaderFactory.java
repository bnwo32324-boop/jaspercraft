package chat.jaspr.invasions;

import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Builds a single invader for a given player's difficulty.
 *
 * The progression is the whole feature. At the bottom of the curve an invasion is a handful of
 * bare zombies; at the top it is armoured, enchanted, fast, and led by things that were never
 * going to knock politely. Everything here is driven by one number between 0 and 1, which is that
 * player's own standing - so two players invaded on the same night get different armies.
 */
final class InvaderFactory {
    /** Scoreboard tag, so invaders can still be identified after a restart. */
    static final String TAG = "jaspr_invader";

    private static final EntityType[][] BANDS = {
            { EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON },
            { EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.SPIDER },
            { EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY,
              EntityType.SPIDER, EntityType.CAVE_SPIDER },
            { EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY,
              EntityType.VINDICATOR, EntityType.ZOMBIE_VILLAGER, EntityType.SPIDER, EntityType.WITCH },
            { EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY,
              EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.WITHER_SKELETON,
              EntityType.EVOKER, EntityType.SPIDER, EntityType.WITCH }
    };

    private final InvasionConfig settings;
    private final Random random;

    InvaderFactory(InvasionConfig settings, Random random) {
        this.settings = settings;
        this.random = random;
    }

    /** How many invaders a whole night at this difficulty is worth. */
    int waveBudget(double difficulty) {
        double raw = (6.0d + 28.0d * difficulty) * settings.countMultiplier;
        return Math.max(1, (int) Math.round(raw));
    }

    static int band(double difficulty) {
        if (difficulty < 0.15d) return 0;
        if (difficulty < 0.35d) return 1;
        if (difficulty < 0.55d) return 2;
        if (difficulty < 0.80d) return 3;
        return 4;
    }

    /** Human readable tier, for status output. */
    static String tierName(double difficulty) {
        switch (band(difficulty)) {
            case 0: return "Stirring";
            case 1: return "Probing";
            case 2: return "Organised";
            case 3: return "Armoured";
            default: return "Overwhelming";
        }
    }

    /**
     * Spawns one invader. Returns null if the world refused the spawn, which happens often enough
     * near chunk borders that callers must expect it.
     */
    LivingEntity spawn(Location at, double difficulty) {
        EntityType[] pool = BANDS[band(difficulty)];
        EntityType type = pool[random.nextInt(pool.length)];

        Entity spawned;
        try {
            spawned = at.getWorld().spawnEntity(at, type);
        } catch (Throwable refused) {
            return null;
        }
        if (!(spawned instanceof LivingEntity)) {
            spawned.remove();
            return null;
        }

        LivingEntity mob = (LivingEntity) spawned;
        mob.addScoreboardTag(TAG);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);

        if (mob instanceof Zombie) {
            // Baby zombies are fast enough to be unfair on top of everything else being unfair.
            ((Zombie) mob).setBaby(false);
        }

        equip(mob, type, difficulty);
        applyAttributes(mob, difficulty);
        applyPotions(mob, difficulty);

        boolean elite = difficulty >= 0.5d && random.nextInt(12) == 0;
        if (elite) {
            promote(mob, difficulty);
        } else if (settings.nameInvaders) {
            mob.setCustomName(ChatColor.DARK_RED + "Invader");
        }
        return mob;
    }

    /** Which job this invader takes once it meets a wall. */
    InvaderRole roleFor(LivingEntity mob, double difficulty) {
        EntityType type = mob.getType();
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON) {
            return InvaderRole.RANGED;
        }
        boolean handy = type == EntityType.ZOMBIE || type == EntityType.HUSK
                || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.VINDICATOR;
        if (!handy) return InvaderRole.GRUNT;

        if (settings.minersEnabled && random.nextDouble() < 0.12d + 0.28d * difficulty) return InvaderRole.MINER;
        if (settings.soldiersEnabled && random.nextDouble() < 0.10d + 0.20d * difficulty) return InvaderRole.SOLDIER;
        return InvaderRole.GRUNT;
    }

    // ------------------------------------------------------------------ gear

    private void equip(LivingEntity mob, EntityType type, double difficulty) {
        EntityEquipment gear = mob.getEquipment();
        if (gear == null) return;

        float drop = (float) settings.gearDropChance;
        gear.setHelmetDropChance(drop);
        gear.setChestplateDropChance(drop);
        gear.setLeggingsDropChance(drop);
        gear.setBootsDropChance(drop);
        gear.setItemInMainHandDropChance(drop);

        boolean archer = type == EntityType.SKELETON || type == EntityType.STRAY
                || type == EntityType.WITHER_SKELETON;
        if (archer) {
            ItemStack bow = new ItemStack(Material.BOW);
            int power = (int) Math.floor(difficulty * 3.0d);
            if (power > 0) bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, power);
            gear.setItemInMainHand(bow);
        } else if (type == EntityType.WITCH || type == EntityType.EVOKER || type == EntityType.SPIDER
                || type == EntityType.CAVE_SPIDER) {
            // These fight with what they were born with.
            return;
        } else {
            Material sword = weaponFor(difficulty);
            if (sword != null) {
                ItemStack weapon = new ItemStack(sword);
                int sharpness = (int) Math.floor(difficulty * 3.0d);
                if (sharpness > 0) weapon.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, sharpness);
                gear.setItemInMainHand(weapon);
            }
        }

        if (type == EntityType.WITCH || type == EntityType.EVOKER) return;

        double slotChance = 0.15d + 0.70d * difficulty;
        int protection = (int) Math.floor(difficulty * 2.5d);
        setPiece(gear, 0, helmetFor(difficulty), slotChance, protection);
        setPiece(gear, 1, chestFor(difficulty), slotChance, protection);
        setPiece(gear, 2, legsFor(difficulty), slotChance, protection);
        setPiece(gear, 3, bootsFor(difficulty), slotChance, protection);
    }

    private void setPiece(EntityEquipment gear, int slot, Material material, double chance, int protection) {
        if (material == null || random.nextDouble() > chance) return;
        ItemStack piece = new ItemStack(material);
        if (protection > 0) piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, protection);
        switch (slot) {
            case 0: gear.setHelmet(piece); break;
            case 1: gear.setChestplate(piece); break;
            case 2: gear.setLeggings(piece); break;
            default: gear.setBoots(piece); break;
        }
    }

    private Material weaponFor(double difficulty) {
        if (difficulty < 0.12d) return null;
        if (difficulty < 0.30d) return Material.WOOD_SWORD;
        if (difficulty < 0.50d) return Material.STONE_SWORD;
        if (difficulty < 0.78d) return Material.IRON_SWORD;
        return Material.DIAMOND_SWORD;
    }

    private Material helmetFor(double difficulty) {
        if (difficulty < 0.12d) return null;
        if (difficulty < 0.32d) return Material.LEATHER_HELMET;
        if (difficulty < 0.52d) return Material.CHAINMAIL_HELMET;
        if (difficulty < 0.80d) return Material.IRON_HELMET;
        return Material.DIAMOND_HELMET;
    }

    private Material chestFor(double difficulty) {
        if (difficulty < 0.18d) return null;
        if (difficulty < 0.32d) return Material.LEATHER_CHESTPLATE;
        if (difficulty < 0.52d) return Material.CHAINMAIL_CHESTPLATE;
        if (difficulty < 0.80d) return Material.IRON_CHESTPLATE;
        return Material.DIAMOND_CHESTPLATE;
    }

    private Material legsFor(double difficulty) {
        if (difficulty < 0.22d) return null;
        if (difficulty < 0.36d) return Material.LEATHER_LEGGINGS;
        if (difficulty < 0.56d) return Material.CHAINMAIL_LEGGINGS;
        if (difficulty < 0.84d) return Material.IRON_LEGGINGS;
        return Material.DIAMOND_LEGGINGS;
    }

    private Material bootsFor(double difficulty) {
        if (difficulty < 0.22d) return null;
        if (difficulty < 0.36d) return Material.LEATHER_BOOTS;
        if (difficulty < 0.56d) return Material.CHAINMAIL_BOOTS;
        if (difficulty < 0.84d) return Material.IRON_BOOTS;
        return Material.DIAMOND_BOOTS;
    }

    // ------------------------------------------------------------------ stats

    private void applyAttributes(LivingEntity mob, double difficulty) {
        scale(mob, Attribute.GENERIC_MAX_HEALTH, 1.0d + 0.90d * difficulty, true);
        scale(mob, Attribute.GENERIC_MOVEMENT_SPEED, 1.0d + 0.25d * difficulty, false);
        scale(mob, Attribute.GENERIC_ATTACK_DAMAGE, 1.0d + 0.60d * difficulty, false);

        AttributeInstance knockback = mob.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(Math.min(0.75d, 0.50d * difficulty));

        // Omniscience starts here: a mob that cannot see this far will never come looking.
        AttributeInstance follow = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(settings.followRange);
    }

    private void scale(LivingEntity mob, Attribute attribute, double multiplier, boolean healToFull) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) return;
        double value = instance.getBaseValue() * multiplier;
        instance.setBaseValue(value);
        if (healToFull) mob.setHealth(Math.min(value, instance.getValue()));
    }

    private void applyPotions(LivingEntity mob, double difficulty) {
        if (difficulty >= 0.60d) add(mob, PotionEffectType.SPEED, 0);
        if (difficulty >= 0.70d) add(mob, PotionEffectType.FIRE_RESISTANCE, 0);
        if (difficulty >= 0.88d) add(mob, PotionEffectType.DAMAGE_RESISTANCE, 0);
    }

    private void add(LivingEntity mob, PotionEffectType type, int amplifier) {
        // Long enough to outlast a night, quiet enough not to cover the screen in particles.
        mob.addPotionEffect(new PotionEffect(type, 20 * 60 * 20, amplifier, true, false), true);
    }

    /** Occasional standouts, so a late-game wave has something in it worth being afraid of. */
    private void promote(LivingEntity mob, double difficulty) {
        AttributeInstance health = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * 1.6d);
            mob.setHealth(health.getValue());
        }
        AttributeInstance damage = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.25d);
        mob.setCustomName(ChatColor.DARK_RED + "Invasion Captain");
        mob.setCustomNameVisible(true);
        add(mob, PotionEffectType.SPEED, 0);
    }
}
