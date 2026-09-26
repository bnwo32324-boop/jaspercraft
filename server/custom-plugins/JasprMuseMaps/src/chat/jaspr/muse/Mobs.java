package chat.jaspr.muse;

import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Garrison and custom-mob factory for Muse+GLM_Maps sites. Every mob is tagged so its site can find and retire it. */
final class Mobs {
    private Mobs() {}

    static final String TAG = "jaspr_muse";
    static final String SITE_TAG = "jaspr_muse_site:";
    static final String SIGNATURE_TAG = "jaspr_muse_sig";

    static EntityType spawnerType(Catalog.Design d) {
        for (Catalog.Garrison g : d.danger.garrison) {
            EntityType t = type(g.type);
            if (t != null && t != EntityType.SHULKER && t != EntityType.GHAST && t != EntityType.GUARDIAN && t != EntityType.IRON_GOLEM
                    && t != EntityType.WOLF && t != EntityType.RABBIT && t != EntityType.POLAR_BEAR) return t;
        }
        return EntityType.SKELETON;
    }

    static EntityType type(String name) {
        try { return EntityType.valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }

    /** A garrison mob of the given type at loc, scaled to the site tier and turned on the nearest player. */
    static LivingEntity garrison(World w, Location loc, String typeName, int tier, String siteKey, Player target, Random r) {
        EntityType type = type(typeName);
        if (type == null) return null;
        if (type == EntityType.GHAST) loc = loc.clone().add(0, 8, 0);
        Entity e = w.spawnEntity(loc, type);
        if (!(e instanceof LivingEntity)) { e.remove(); return null; }
        LivingEntity mob = (LivingEntity) e;
        tag(mob, siteKey);
        scale(mob, 1.0 + 0.15 * tier, 0.5 * tier);
        dress(mob, tier, r);
        aggravate(mob, target);
        return mob;
    }

    /** The site's own custom mob (catalogue "signature"): named, much stronger, runs its two abilities. */
    static LivingEntity signature(World w, Location loc, Catalog.Signature s, String siteKey, Player target, Random r, int tier) {
        EntityType type = type(s.type);
        if (type == null) return null;
        Entity e = w.spawnEntity(type == EntityType.GHAST ? loc.clone().add(0, 8, 0) : loc, type);
        if (!(e instanceof LivingEntity)) { e.remove(); return null; }
        LivingEntity mob = (LivingEntity) e;
        tag(mob, siteKey);
        mob.addScoreboardTag(SIGNATURE_TAG);
        mob.setCustomName(ChatColor.LIGHT_PURPLE + s.name);
        mob.setCustomNameVisible(true);
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(Math.min(1024, s.health)); mob.setHealth(hp.getBaseValue()); }
        AttributeInstance dmg = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(s.damage);
        AttributeInstance speed = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null && type != EntityType.GHAST && type != EntityType.SHULKER) speed.setBaseValue(Math.min(0.42, s.speed));
        AttributeInstance kb = mob.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.5);
        AttributeInstance follow = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(40);
        if (mob instanceof Slime) ((Slime) mob).setSize(4);
        dress(mob, Math.max(3, tier), r);
        aggravate(mob, target);
        return mob;
    }

    static void tag(LivingEntity mob, String siteKey) {
        mob.addScoreboardTag(TAG);
        mob.addScoreboardTag(SITE_TAG + siteKey);
        // Zombie-family garrison belongs to this site, not to the Apocalypse siege director.
        if (mob.getType() == EntityType.HUSK) mob.addScoreboardTag("jaspr_vanilla_undead");
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
    }

    static boolean ours(Entity e) { return e.getScoreboardTags().contains(TAG); }

    static String siteOf(Entity e) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(SITE_TAG)) return t.substring(SITE_TAG.length());
        return null;
    }

    private static void scale(LivingEntity mob, double hpFactor, double extraDamage) {
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(Math.min(1024, hp.getBaseValue() * hpFactor)); mob.setHealth(hp.getBaseValue()); }
        AttributeInstance dmg = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() + extraDamage);
    }

    private static void dress(LivingEntity mob, int tier, Random r) {
        EntityEquipment eq = mob.getEquipment();
        switch (mob.getType()) {
            case SKELETON: case STRAY: {
                ItemStack bow = new ItemStack(Material.BOW);
                if (tier >= 2) bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, Math.min(5, tier - 1));
                if (tier >= 4 && r.nextBoolean()) bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
                eq.setItemInMainHand(bow);
                eq.setHelmet(new ItemStack(tier >= 3 ? Material.IRON_HELMET : Material.LEATHER_HELMET));
                break;
            }
            case WITHER_SKELETON:
                eq.setItemInMainHand(new ItemStack(tier >= 4 ? Material.IRON_SWORD : Material.STONE_SWORD));
                break;
            case HUSK:
                eq.setItemInMainHand(new ItemStack(Material.GOLD_SPADE));
                break;
            case PIG_ZOMBIE:
                eq.setItemInMainHand(new ItemStack(tier >= 4 ? Material.GOLD_AXE : Material.GOLD_SWORD));
                break;
            default: break;
        }
        if (eq != null) {
            eq.setItemInMainHandDropChance(0.02f);
            eq.setHelmetDropChance(0.0f);
        }
        if (mob.getType() == EntityType.SLIME || mob.getType() == EntityType.MAGMA_CUBE) ((Slime) mob).setSize(Math.min(4, 1 + (tier + 1) / 2));
        if (mob.getType() == EntityType.RABBIT) ((Rabbit) mob).setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
        if (mob.getType() == EntityType.IRON_GOLEM) ((IronGolem) mob).setPlayerCreated(false);
        // Keeps sun-sensitive garrisons alive by day without any glowing effect (owner rule: no glowing).
        if (mob.getType() == EntityType.HUSK || mob.getType() == EntityType.STRAY || mob.getType() == EntityType.SKELETON)
            mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false), true);
    }

    static void aggravate(LivingEntity mob, Player target) {
        if (target == null) return;
        if (mob instanceof Wolf) ((Wolf) mob).setAngry(true);
        if (mob instanceof PigZombie) { ((PigZombie) mob).setAngry(true); ((PigZombie) mob).setAnger(Integer.MAX_VALUE / 2); }
        if (mob instanceof Creature) ((Creature) mob).setTarget(target);
        if (mob instanceof Enderman) ((Enderman) mob).setTarget(target);
    }
}
