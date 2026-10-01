package chat.jaspr.nether;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The creatures of the colossal structures and the Endless Catacombs (owner, 2026-10-01: "Add loot, enemies, different
 * dangers"). Ten kinds on vanilla bases, driven by the server like the {@link Fiends}:
 * <ul>
 *   <li>the Great Pyramid's: the Mummy (husk; its wrappings bind and starve), the Asp (silverfish; venom), the Scarab
 *       (endermite; swarms with its Matriarch) and the Tomb Guardian (a gilded skeleton);</li>
 *   <li>the Caldera Citadel's: the Ember Legionnaire (a soldier who jabs fire), the Flame Adept (hurls fire and bursts
 *       into flame up close) and the Royal Guard (the Sovereign's own, who lunge with their blades);</li>
 *   <li>the Catacombs': the Crypt Guard (a skeleton in mail), the Deep Crawler (a spider that pounces, poison and
 *       cramp) and the Lost Soul (a burning spirit that passes through stone).</li>
 * </ul>
 * None of them changes a block: their fire never sets the halls alight, the asps never hide in the stone, the
 * soldiers never break a door or call up more of their kind. Their drops are the Nether materials of {@link Items}.
 */
final class Dwellers {
    private Dwellers() {}

    static final Color LINEN = Color.fromRGB(0xE6DCC0), EMBER = Color.fromRGB(0x8E1111), FLAME = Color.fromRGB(0xE0701B),
        SOOT = Color.fromRGB(0x1A0A0A), GOLD = Color.fromRGB(0xC9A227), BONE = Color.fromRGB(0xD8D2BE);

    static void specs(java.util.function.Consumer<Mobs.Spec> spec) {
        //                 kind                  name                  base                    hp   dmg  speed follow armor kb   fireImm freezeImm infestImm hostile neutral elite
        spec.accept(new Mobs.Spec("mummy", "Mummy", EntityType.HUSK, 34, 6, 0.21, 32, 3, 0.3, true, false, true, true, false, "Mummy Lord"));
        spec.accept(new Mobs.Spec("asp", "Asp", EntityType.SILVERFISH, 12, 4, 0.32, 24, 0, 0, true, false, true, true, false, "Royal Asp"));
        spec.accept(new Mobs.Spec("scarab", "Scarab", EntityType.ENDERMITE, 10, 3, 0.3, 24, 2, 0, true, false, true, true, false, "Gilded Scarab"));
        spec.accept(new Mobs.Spec("tomb_guardian", "Tomb Guardian", EntityType.SKELETON, 40, 8, 0.25, 32, 8, 0.4, true, false, true, true, false, "Tomb Colossus"));
        spec.accept(new Mobs.Spec("ember_legionnaire", "Ember Legionnaire", EntityType.ZOMBIE, 32, 7, 0.27, 40, 6, 0.3, true, false, true, true, false, "Ember Centurion"));
        spec.accept(new Mobs.Spec("flame_adept", "Flame Adept", EntityType.ZOMBIE, 28, 5, 0.26, 40, 2, 0, true, false, true, true, false, "Flame Master"));
        spec.accept(new Mobs.Spec("royal_guard", "Royal Guard", EntityType.ZOMBIE, 44, 8, 0.25, 40, 10, 0.5, true, false, true, true, false, "Royal Captain"));
        spec.accept(new Mobs.Spec("crypt_guard", "Crypt Guard", EntityType.SKELETON, 32, 7, 0.25, 32, 6, 0.3, true, false, true, true, false, "Crypt Warden"));
        spec.accept(new Mobs.Spec("deep_crawler", "Deep Crawler", EntityType.SPIDER, 26, 6, 0.32, 32, 0, 0, true, false, true, true, false, "Deep Horror"));
        spec.accept(new Mobs.Spec("lost_soul", "Lost Soul", EntityType.VEX, 14, 4, 0, 32, 0, 0, true, true, true, true, false, "Burning Soul"));
    }

    static final java.util.Set<String> KINDS = new java.util.HashSet<>(java.util.Arrays.asList("mummy", "asp", "scarab", "tomb_guardian",
        "ember_legionnaire", "flame_adept", "royal_guard", "crypt_guard", "deep_crawler", "lost_soul"));

    static boolean flies(String kind) { return kind.equals("lost_soul"); }

    private static ItemStack leather(Material m, Color c) {
        ItemStack s = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) s.getItemMeta();
        meta.setColor(c);
        s.setItemMeta(meta);
        return s;
    }

    /** Looks and base behaviour, once when spawned. */
    static void configure(Mobs m, LivingEntity e, Mobs.Spec s, boolean elite) {
        EntityEquipment q = e.getEquipment();
        if (e instanceof Zombie) {
            ((Zombie) e).setBaby(false);
            Mobs.attr(e, Attribute.ZOMBIE_SPAWN_REINFORCEMENTS, 0);      // no vanilla zombies called to their side
        }
        e.setCanPickupItems(false);
        switch (s.kind) {
            case "mummy":
                q.setHelmet(leather(Material.LEATHER_HELMET, LINEN));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, LINEN));
                q.setLeggings(leather(Material.LEATHER_LEGGINGS, LINEN));
                if (elite) q.setHelmet(new ItemStack(Material.GOLD_HELMET));
                break;
            case "tomb_guardian":
                q.setItemInMainHand(new ItemStack(Material.GOLD_SWORD));
                q.setHelmet(new ItemStack(Material.GOLD_HELMET));
                q.setChestplate(new ItemStack(Material.GOLD_CHESTPLATE));
                if (elite) q.setLeggings(new ItemStack(Material.GOLD_LEGGINGS));
                break;
            case "ember_legionnaire":
                q.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                q.setHelmet(new ItemStack(Material.IRON_HELMET));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, EMBER));
                q.setLeggings(leather(Material.LEATHER_LEGGINGS, SOOT));
                q.setBoots(leather(Material.LEATHER_BOOTS, SOOT));
                break;
            case "flame_adept":
                q.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                q.setHelmet(leather(Material.LEATHER_HELMET, FLAME));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, EMBER));
                q.setLeggings(leather(Material.LEATHER_LEGGINGS, FLAME));
                break;
            case "royal_guard":
                q.setItemInMainHand(new ItemStack(Material.GOLD_SWORD));
                q.setHelmet(new ItemStack(Material.GOLD_HELMET));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, SOOT));
                q.setLeggings(leather(Material.LEATHER_LEGGINGS, EMBER));
                q.setBoots(leather(Material.LEATHER_BOOTS, SOOT));
                if (elite) q.setChestplate(new ItemStack(Material.GOLD_CHESTPLATE));
                break;
            case "crypt_guard":
                q.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                q.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                q.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                if (elite) q.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                break;
            default:
        }
        q.setItemInMainHandDropChance(0f); q.setItemInOffHandDropChance(0f); q.setHelmetDropChance(0f);
        q.setChestplateDropChance(0f); q.setLeggingsDropChance(0f); q.setBootsDropChance(0f);
        if (s.kind.equals("lost_soul")) e.setFireTicks(Integer.MAX_VALUE / 4);
    }

    private static LivingEntity target(LivingEntity e) { return e instanceof Creature ? ((Creature) e).getTarget() : null; }

    /** Per-think behaviour (bounded round robin). Returns false for kinds that are not ours. */
    static boolean think(Mobs m, Mobs.T t) {
        if (!KINDS.contains(t.spec.kind)) return false;
        LivingEntity e = t.e;
        long now = m.now;
        LivingEntity target = target(e);
        if ((target == null || target.isDead()) && e instanceof Creature && (now % 10) == 0) {
            Player p = Fiends.prey(e, t.spec.follow * 0.6);
            if (p != null && e.hasLineOfSight(p)) { ((Creature) e).setTarget(p); target = p; }
        }
        switch (t.spec.kind) {
            case "ember_legionnaire": case "flame_adept": {
                if (target == null || now < t.cooldown || !e.hasLineOfSight(target)) break;
                double d = target.getLocation().distance(e.getLocation());
                boolean adept = t.spec.kind.equals("flame_adept");
                if (adept && d < 4) {        // a burst of flame around the adept
                    for (Entity n : e.getNearbyEntities(4, 2.5, 4)) {
                        if (!(n instanceof Player) || ((Player) n).getGameMode() == GameMode.CREATIVE) continue;
                        ((Player) n).setFireTicks(Math.max(((Player) n).getFireTicks(), 80));
                        m.hurt((Player) n, e, 4 * m.dmgMult * (t.elite ? 1.5 : 1));
                    }
                    e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 50, 2, 0.6, 2, 0.04);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
                    t.cooldown = now + 100;
                    m.ability("flame_adept_burst");
                } else if (d > (adept ? 4 : 3) && d < (adept ? 18 : 10)) {
                    Location eye = e.getEyeLocation();
                    Vector dir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
                    m.spawning(true);
                    try {
                        SmallFireball b = e.launchProjectile(SmallFireball.class, dir.multiply(1.1));
                        b.setIsIncendiary(false);
                    } finally { m.spawning(false); }
                    e.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.3f);
                    t.cooldown = now + (adept ? 50 : 90) + m.random.nextInt(30) - (t.elite ? 20 : 0);
                    m.ability(t.spec.kind + "_fire");
                }
                break;
            }
            case "royal_guard": case "deep_crawler": {
                if (target == null || now < t.cooldown || !e.isOnGround()) break;
                double d = target.getLocation().distance(e.getLocation());
                if (d > 3 && d < (t.spec.kind.equals("royal_guard") ? 7 : 8) && e.hasLineOfSight(target)) {
                    Vector v = target.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0).normalize().multiply(t.spec.kind.equals("royal_guard") ? 0.95 : 0.85);
                    v.setY(t.spec.kind.equals("royal_guard") ? 0.3 : 0.45);
                    e.setVelocity(v);
                    t.charging = true;
                    t.cooldown = now + (t.elite ? 60 : 80);
                    m.ability(t.spec.kind + (t.spec.kind.equals("royal_guard") ? "_lunge" : "_pounce"));
                }
                break;
            }
            case "lost_soul":
                if (e.getFireTicks() < 40) e.setFireTicks(Integer.MAX_VALUE / 4);
                break;
            case "mummy":
                if ((now % 40) == 0) e.getWorld().spawnParticle(Particle.BLOCK_DUST, e.getLocation().add(0, 1, 0), 3, 0.2, 0.4, 0.2, 0,
                    new org.bukkit.material.MaterialData(Material.SAND));
                break;
            default:
        }
        return true;
    }

    /** A melee blow landed by one of ours. */
    static void onHit(Mobs m, Mobs.T s, LivingEntity v) {
        switch (s.spec.kind) {
            case "mummy":
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1, false, true), true);
                m.ability("mummy_bind");
                break;
            case "asp": v.addPotionEffect(new PotionEffect(PotionEffectType.POISON, s.elite ? 100 : 60, 0, false, true), true); m.ability("asp_venom"); break;
            case "ember_legionnaire": case "flame_adept": v.setFireTicks(Math.max(v.getFireTicks(), 60)); m.ability(s.spec.kind + "_burn"); break;
            case "royal_guard":
                if (s.charging) { v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 1, false, true), true); s.charging = false; m.ability("royal_guard_strike"); }
                break;
            case "crypt_guard": v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true), true); m.ability("crypt_guard_chill"); break;
            case "deep_crawler":
                v.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 0, false, true), true);
                s.charging = false;
                m.ability("deep_crawler_bite");
                break;
            case "lost_soul": v.setFireTicks(Math.max(v.getFireTicks(), 60)); m.ability("lost_soul_burn"); break;
            default:
        }
    }

    /** Their fire never sets a hall alight. True when handled. */
    static boolean launch(Mobs m, Mobs.T t, Projectile p) {
        if (!KINDS.contains(t.spec.kind)) return false;
        if (p instanceof SmallFireball) ((SmallFireball) p).setIsIncendiary(false);
        return true;
    }

    /** Drops: Nether materials and old treasure. True when the kind is ours. */
    static boolean loot(Mobs m, Mobs.T t, List<ItemStack> drops, int looting, boolean byPlayer) {
        if (!KINDS.contains(t.spec.kind)) return false;
        java.util.Random r = m.random;
        switch (t.spec.kind) {
            case "mummy":
                drops.add(new ItemStack(Material.ROTTEN_FLESH, r.nextInt(3 + looting)));
                drops.add(new ItemStack(Material.PAPER, r.nextInt(2 + looting)));
                if (r.nextInt(4) == 0) drops.add(Items.create("soul_essence", 1));
                break;
            case "asp": if (r.nextInt(3) == 0) drops.add(new ItemStack(Material.SPIDER_EYE, 1)); break;
            case "scarab": drops.add(new ItemStack(Material.GOLD_NUGGET, r.nextInt(3 + looting))); break;
            case "tomb_guardian":
                drops.add(new ItemStack(Material.BONE, 1 + r.nextInt(2 + looting)));
                drops.add(new ItemStack(Material.GOLD_NUGGET, 1 + r.nextInt(4 + looting)));
                if (r.nextInt(3) == 0) drops.add(Items.create("hellforged_shard", 1));
                break;
            case "ember_legionnaire":
                if (r.nextInt(2) == 0) drops.add(Items.create("hellforged_shard", 1 + r.nextInt(1 + looting)));
                if (r.nextInt(3) == 0) drops.add(Items.create("pyre_ember", 1));
                break;
            case "flame_adept":
                drops.add(Items.create("pyre_ember", 1 + r.nextInt(1 + looting)));
                if (r.nextInt(4) == 0) drops.add(new ItemStack(Material.BLAZE_ROD, 1));
                break;
            case "royal_guard":
                drops.add(new ItemStack(Material.GOLD_NUGGET, 2 + r.nextInt(4 + looting)));
                if (r.nextInt(2) == 0) drops.add(Items.create("hellforged_shard", 1 + r.nextInt(2)));
                if (byPlayer && r.nextInt(40) < 1 + looting) drops.add(Items.create("infernal_blade", 1));
                break;
            case "crypt_guard":
                drops.add(new ItemStack(Material.BONE, r.nextInt(3 + looting)));
                if (r.nextInt(2) == 0) drops.add(Items.create("charred_bone", 1 + r.nextInt(1 + looting)));
                if (r.nextInt(4) == 0) drops.add(Items.create("hellforged_shard", 1));
                break;
            case "deep_crawler":
                drops.add(new ItemStack(Material.STRING, r.nextInt(3 + looting)));
                if (r.nextInt(3) == 0) drops.add(Items.create("brimstone", 1 + r.nextInt(1 + looting)));
                break;
            case "lost_soul": { int n = r.nextInt(2 + looting); if (n > 0) drops.add(Items.create("soul_essence", n)); break; }
            default:
        }
        drops.removeIf(s -> s == null || s.getAmount() <= 0);
        return true;
    }
}
