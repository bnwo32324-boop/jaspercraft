package chat.jaspr.nether;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The creatures that hold the GLM strongholds (owner, 2026-09-29: "only Nether mobs ... a lot of these Nether mobs to be
 * custom"). Thirteen kinds on vanilla bases, drawn and driven by the server like the NetherEx mobs of {@link Mobs}:
 * <ul>
 *   <li>Infernal Knight (wither skeleton): charges its target and sets it alight;</li>
 *   <li>Ashbone Archer (skeleton): burning arrows, a three-arrow volley every third draw;</li>
 *   <li>Hellhound (wolf): a burning pack hunter that leaps at its prey;</li>
 *   <li>Cinder Imp (baby zombie pigman): quick, hurls small fireballs;</li>
 *   <li>Soul Wraith (vex): passes through walls, its touch withers and slows;</li>
 *   <li>Magma Hulk (big magma cube): slams the ground in a ring of fire, bursts into embers;</li>
 *   <li>Pyre Warden (blaze): a flame nova around it and a quicker volley;</li>
 *   <li>Shade (enderman): hunts on sight, blinks behind its prey, its blow slows and weakens;</li>
 *   <li>Brimstone Spider (cave spider): poison and fire;</li>
 *   <li>Pigman Berserker (zombie pigman): a golden axe, enraged when wounded;</li>
 *   <li>Dread Rider (wither skeleton on a skeleton horse): a mounted charger;</li>
 *   <li>Charred Ghoul (husk): tough, hungering, weakening;</li>
 *   <li>Cinder Witch (witch): her brews, and imps she calls to her side.</li>
 * </ul>
 * Their drops are the Nether materials of {@link Items} (shards, fangs, essences) that the new gear is forged from.
 */
final class Fiends {
    private Fiends() {}

    static final Color ASH = Color.fromRGB(0x2B2B2B), EMBER = Color.fromRGB(0x8A1C0C), BONE = Color.fromRGB(0xD8D2BE),
        SOUL = Color.fromRGB(0x3B2A5C), GOLD = Color.fromRGB(0xC9A227), CHAR = Color.fromRGB(0x3A2418);

    static void specs(java.util.function.Consumer<Mobs.Spec> spec) {
        //                 kind                name                 base                      hp   dmg  speed follow armor kb  fireImm freezeImm infestImm hostile neutral elite
        spec.accept(new Mobs.Spec("infernal_knight", "Infernal Knight", EntityType.WITHER_SKELETON, 36, 8, 0.27, 32, 6, 0.4, true, false, false, true, false, "Infernal Champion"));
        spec.accept(new Mobs.Spec("ashbone_archer", "Ashbone Archer", EntityType.SKELETON, 24, 4, 0.26, 32, 2, 0, true, false, false, true, false, "Ashbone Marksman"));
        spec.accept(new Mobs.Spec("hellhound", "Hellhound", EntityType.WOLF, 22, 6, 0.38, 32, 0, 0, true, false, false, true, false, "Alpha Hellhound"));
        spec.accept(new Mobs.Spec("cinder_imp", "Cinder Imp", EntityType.PIG_ZOMBIE, 14, 4, 0.3, 32, 0, 0, true, false, false, true, false, "Cinder Fiend"));
        spec.accept(new Mobs.Spec("soul_wraith", "Soul Wraith", EntityType.VEX, 16, 5, 0, 32, 0, 0, true, true, true, true, false, "Soul Reaver"));
        spec.accept(new Mobs.Spec("magma_hulk", "Magma Hulk", EntityType.MAGMA_CUBE, 40, 9, 0.2, 32, 4, 0.6, true, false, false, true, false, "Molten Colossus"));
        spec.accept(new Mobs.Spec("pyre_warden", "Pyre Warden", EntityType.BLAZE, 45, 7, 0.23, 48, 4, 0.3, true, false, false, true, false, "Pyre Archon"));
        spec.accept(new Mobs.Spec("shade", "Shade", EntityType.ENDERMAN, 40, 8, 0.3, 48, 0, 0, true, false, false, true, false, "Umbral Shade"));
        spec.accept(new Mobs.Spec("brimstone_spider", "Brimstone Spider", EntityType.CAVE_SPIDER, 16, 4, 0.3, 24, 0, 0, true, false, false, true, false, "Brimstone Broodmother"));
        spec.accept(new Mobs.Spec("pigman_berserker", "Pigman Berserker", EntityType.PIG_ZOMBIE, 32, 8, 0.3, 32, 4, 0.2, true, false, false, true, false, "Pigman Warlord"));
        spec.accept(new Mobs.Spec("dread_rider", "Dread Rider", EntityType.WITHER_SKELETON, 34, 7, 0.25, 40, 4, 0.3, true, false, false, true, false, "Dread Horseman"));
        spec.accept(new Mobs.Spec("charred_ghoul", "Charred Ghoul", EntityType.HUSK, 30, 6, 0.24, 32, 3, 0.3, true, false, false, true, false, "Charnel Ghoul"));
        spec.accept(new Mobs.Spec("cinder_witch", "Cinder Witch", EntityType.WITCH, 32, 0, 0.25, 32, 0, 0, true, false, false, true, false, "Hag of Cinders"));
    }

    static final java.util.Set<String> KINDS = new java.util.HashSet<>(java.util.Arrays.asList("infernal_knight", "ashbone_archer", "hellhound",
        "cinder_imp", "soul_wraith", "magma_hulk", "pyre_warden", "shade", "brimstone_spider", "pigman_berserker", "dread_rider", "charred_ghoul",
        "cinder_witch"));

    static boolean flies(String kind) { return kind.equals("soul_wraith") || kind.equals("pyre_warden"); }

    private static ItemStack leather(Material m, Color c) {
        ItemStack s = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) s.getItemMeta();
        meta.setColor(c);
        s.setItemMeta(meta);
        return s;
    }

    private static void noDrops(EntityEquipment q) {
        q.setItemInMainHandDropChance(0f); q.setItemInOffHandDropChance(0f); q.setHelmetDropChance(0f);
        q.setChestplateDropChance(0f); q.setLeggingsDropChance(0f); q.setBootsDropChance(0f);
    }

    /** Looks and base behaviour, once when spawned. */
    static void configure(Mobs m, LivingEntity e, Mobs.Spec s, boolean elite) {
        EntityEquipment q = e.getEquipment();
        switch (s.kind) {
            case "infernal_knight":
                q.setItemInMainHand(new ItemStack(elite ? Material.GOLD_SWORD : Material.IRON_SWORD));
                q.setHelmet(leather(Material.LEATHER_HELMET, EMBER));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, elite ? GOLD : ASH));
                q.setLeggings(leather(Material.LEATHER_LEGGINGS, ASH));
                break;
            case "ashbone_archer":
                q.setItemInMainHand(new ItemStack(Material.BOW));
                q.setHelmet(leather(Material.LEATHER_HELMET, BONE));
                break;
            case "hellhound": {
                Wolf w = (Wolf) e;
                w.setAngry(true);
                w.setCollarColor(org.bukkit.DyeColor.RED);
                break;
            }
            case "cinder_imp": {
                PigZombie p = (PigZombie) e;
                p.setBaby(true);
                p.setAngry(true);
                p.setAnger(Integer.MAX_VALUE / 2);
                q.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                break;
            }
            case "pigman_berserker": {
                PigZombie p = (PigZombie) e;
                p.setBaby(false);
                p.setAngry(true);
                p.setAnger(Integer.MAX_VALUE / 2);
                q.setItemInMainHand(new ItemStack(Material.GOLD_AXE));
                q.setHelmet(new ItemStack(Material.GOLD_HELMET));
                if (elite) q.setChestplate(new ItemStack(Material.GOLD_CHESTPLATE));
                break;
            }
            case "magma_hulk": ((MagmaCube) e).setSize(elite ? 5 : 4); break;
            case "dread_rider": {
                q.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                q.setHelmet(leather(Material.LEATHER_HELMET, ASH));
                q.setChestplate(leather(Material.LEATHER_CHESTPLATE, SOUL));
                Entity raw = e.getWorld().spawnEntity(e.getLocation(), EntityType.SKELETON_HORSE);
                if (raw instanceof SkeletonHorse) {
                    SkeletonHorse h = (SkeletonHorse) raw;
                    h.setTamed(false);
                    h.addScoreboardTag("jn_mount");
                    Mobs.attr(h, Attribute.GENERIC_MAX_HEALTH, 30);
                    h.setHealth(30);
                    h.addPassenger(e);
                }
                break;
            }
            case "charred_ghoul":
                q.setHelmet(leather(Material.LEATHER_HELMET, CHAR));
                break;
            case "shade": case "soul_wraith": case "brimstone_spider": case "pyre_warden": case "cinder_witch":
            default:
        }
        noDrops(q);
        if (s.kind.equals("hellhound") || s.kind.equals("magma_hulk")) e.setFireTicks(Integer.MAX_VALUE / 4);
    }

    static Player prey(LivingEntity e, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private static LivingEntity target(LivingEntity e) { return e instanceof Creature ? ((Creature) e).getTarget() : null; }

    /** Per-think behaviour (bounded round robin). Returns false for kinds that are not ours. */
    static boolean think(Mobs m, Mobs.T t) {
        if (!KINDS.contains(t.spec.kind)) return false;
        LivingEntity e = t.e;
        long now = m.now;
        LivingEntity target = target(e);
        if ((target == null || target.isDead()) && e instanceof Creature && (now % 10) == 0) {
            Player p = prey(e, t.spec.follow * 0.6);
            if (p != null && e.hasLineOfSight(p)) { ((Creature) e).setTarget(p); target = p; }
        }
        switch (t.spec.kind) {
            case "infernal_knight": case "dread_rider": {
                if (target == null || now < t.cooldown) break;
                double d = target.getLocation().distance(e.getLocation());
                if (d > 3 && d < 10 && e.hasLineOfSight(target)) {
                    LivingEntity mover = e.getVehicle() instanceof LivingEntity ? (LivingEntity) e.getVehicle() : e;
                    Vector v = target.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0).normalize().multiply(t.elite ? 1.3 : 1.05);
                    v.setY(0.28);
                    mover.setVelocity(v);
                    e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
                    t.charging = true;
                    t.cooldown = now + (t.elite ? 70 : 100);
                    m.ability(t.spec.kind + "_charge");
                }
                break;
            }
            case "hellhound": {
                if (e.getFireTicks() < 40) e.setFireTicks(Integer.MAX_VALUE / 4);
                if (target == null || now < t.cooldown || !e.isOnGround()) break;
                double d = target.getLocation().distance(e.getLocation());
                if (d > 2.5 && d < 7) {
                    Vector v = target.getLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(0.9);
                    v.setY(0.45);
                    e.setVelocity(v);
                    t.cooldown = now + 50;
                    m.ability("hellhound_leap");
                }
                break;
            }
            case "cinder_imp": case "pyre_warden": {
                if (target == null || now < t.cooldown || !e.hasLineOfSight(target)) break;
                double d = target.getLocation().distance(e.getLocation());
                if (t.spec.kind.equals("cinder_imp") && d > 4 && d < 20) {
                    Location eye = e.getEyeLocation();
                    Vector dir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
                    m.spawning(true);
                    try { e.launchProjectile(SmallFireball.class, dir.multiply(1.2)); } finally { m.spawning(false); }
                    e.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 0.7f, 1.6f);
                    t.cooldown = now + (t.elite ? 40 : 60) + m.random.nextInt(30);
                    m.ability("cinder_imp_fireball");
                } else if (t.spec.kind.equals("pyre_warden") && d < 6) {
                    for (Entity n : e.getNearbyEntities(5, 3, 5)) {
                        if (!(n instanceof Player) || ((Player) n).getGameMode() == GameMode.CREATIVE) continue;
                        ((Player) n).setFireTicks(Math.max(((Player) n).getFireTicks(), 100));
                        m.hurt((Player) n, e, 4 * m.dmgMult * (t.elite ? 1.5 : 1));
                    }
                    e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 60, 2.5, 0.8, 2.5, 0.05);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
                    t.cooldown = now + 120;
                    m.ability("pyre_warden_nova");
                }
                break;
            }
            case "magma_hulk": {
                if (e.getFireTicks() < 40) e.setFireTicks(Integer.MAX_VALUE / 4);
                if (target == null || now < t.cooldown || !e.isOnGround()) break;
                if (target.getLocation().distanceSquared(e.getLocation()) < 36) {
                    for (Entity n : e.getNearbyEntities(5, 2.5, 5)) {
                        if (!(n instanceof Player) || ((Player) n).getGameMode() == GameMode.CREATIVE) continue;
                        m.hurt((Player) n, e, 6 * m.dmgMult * (t.elite ? 1.5 : 1));
                        Vector v = n.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
                        if (v.lengthSquared() < 1e-4) v = new Vector(1, 0, 0);
                        n.setVelocity(v.normalize().multiply(1.1).setY(0.55));
                        ((Player) n).setFireTicks(Math.max(((Player) n).getFireTicks(), 80));
                    }
                    e.getWorld().spawnParticle(Particle.LAVA, e.getLocation(), 30, 2.5, 0.3, 2.5, 0);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_MAGMACUBE_SQUISH, 2f, 0.4f);
                    t.cooldown = now + 100;
                    m.ability("magma_hulk_slam");
                }
                break;
            }
            case "shade": {
                if (target == null || now < t.cooldown) break;
                double d = target.getLocation().distance(e.getLocation());
                if (d > 4 && d < 24) {
                    Location behind = target.getLocation().clone().add(target.getLocation().getDirection().setY(0).normalize().multiply(-2));
                    if (behind.getBlock().getType() == Material.AIR && behind.clone().add(0, 1, 0).getBlock().getType() == Material.AIR
                        && behind.clone().add(0, 2, 0).getBlock().getType() == Material.AIR && behind.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                        e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation().add(0, 1, 0), 30, 0.4, 1, 0.4, 0.5);
                        behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
                        e.teleport(behind);
                        e.getWorld().playSound(behind, Sound.ENTITY_ENDERMEN_TELEPORT, 1f, 0.6f);
                        t.cooldown = now + (t.elite ? 70 : 110);
                        m.ability("shade_blink");
                    } else t.cooldown = now + 20;
                }
                break;
            }
            case "pigman_berserker": {
                if (!t.charging && e.getHealth() < e.getMaxHealth() * 0.5) {
                    t.charging = true;
                    Mobs.attr(e, Attribute.GENERIC_MOVEMENT_SPEED, t.spec.speed * 1.35);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 60, 0, false, true));
                    e.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, e.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ZOMBIE_PIG_ANGRY, 1.5f, 0.6f);
                    m.ability("pigman_berserker_rage");
                }
                break;
            }
            case "cinder_witch": {
                if (target == null || now < t.cooldown || target.getLocation().distanceSquared(e.getLocation()) > 16 * 16) break;
                int imps = 0;
                for (Entity n : e.getNearbyEntities(12, 6, 12)) { Mobs.T o = m.track(n); if (o != null && o.spec.kind.equals("cinder_imp")) imps++; }
                if (imps < 3) {
                    for (int i = 0; i < 2; i++) {
                        Location at = e.getLocation().add(m.random.nextInt(3) - 1, 0, m.random.nextInt(3) - 1);
                        if (at.getBlock().getType() != Material.AIR) at = e.getLocation();
                        LivingEntity imp = m.spawn("cinder_imp", at, false);
                        if (imp instanceof Creature) ((Creature) imp).setTarget(target);
                    }
                    e.getWorld().spawnParticle(Particle.SPELL_WITCH, e.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.2f, 0.6f);
                    m.ability("cinder_witch_summon");
                }
                t.cooldown = now + 240;
                break;
            }
            case "charred_ghoul":
                if ((now % 20) == 0) e.getWorld().spawnParticle(Particle.SMOKE_NORMAL, e.getLocation().add(0, 1.4, 0), 3, 0.2, 0.3, 0.2, 0.01);
                break;
            default:
        }
        return true;
    }

    /** A melee blow landed by one of ours. */
    static void onHit(Mobs m, Mobs.T s, LivingEntity v) {
        switch (s.spec.kind) {
            case "infernal_knight": case "dread_rider":
                v.setFireTicks(Math.max(v.getFireTicks(), s.charging ? 100 : 60));
                s.charging = false;
                m.ability(s.spec.kind + "_burn");
                break;
            case "hellhound": v.setFireTicks(Math.max(v.getFireTicks(), 60)); m.ability("hellhound_bite"); break;
            case "soul_wraith":
                v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, s.elite ? 100 : 60, 0, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, false, true), true);
                m.ability("soul_wraith_wither");
                break;
            case "shade":
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true), true);
                m.ability("shade_chill");
                break;
            case "brimstone_spider": v.setFireTicks(Math.max(v.getFireTicks(), 40)); m.ability("brimstone_spider_burn"); break;
            case "charred_ghoul":
                v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true), true);
                v.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1, false, true), true);
                m.ability("charred_ghoul_blight");
                break;
            default:
        }
    }

    /** Projectiles of ours: burning arrows (and a volley every third draw), a quicker blaze volley. True when handled. */
    static boolean launch(Mobs m, Mobs.T t, Projectile p) {
        if (t.spec.kind.equals("ashbone_archer") && p instanceof Arrow) {
            p.setFireTicks(200);
            if (m.spawning()) return true;
            t.shots++;
            if (t.shots % 3 == 0 || t.elite) {
                m.spawning(true);
                try {
                    for (int i = -1; i <= 1; i += 2) {
                        Arrow a = t.e.launchProjectile(Arrow.class, Mobs.rotY(p.getVelocity(), 0.12 * i));
                        a.setFireTicks(200);
                        a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    }
                } finally { m.spawning(false); }
                m.ability("ashbone_volley");
            }
            return true;
        }
        if (t.spec.kind.equals("pyre_warden") && p instanceof SmallFireball && !m.spawning()) {
            t.shots++;
            if (t.shots % 2 == 0) {
                m.spawning(true);
                try { t.e.launchProjectile(SmallFireball.class, Mobs.rotY(p.getVelocity(), 0.1)); } finally { m.spawning(false); }
                m.ability("pyre_warden_volley");
            }
            return true;
        }
        return false;
    }

    /** Drops: the Nether materials the new gear is forged from. True when the kind is ours. */
    static boolean loot(Mobs m, Mobs.T t, List<ItemStack> drops, int looting, boolean byPlayer) {
        if (!KINDS.contains(t.spec.kind)) return false;
        java.util.Random r = m.random;
        switch (t.spec.kind) {
            case "infernal_knight":
                add(drops, "hellforged_shard", r.nextInt(2 + looting));
                if (byPlayer && r.nextInt(60) < 1 + looting) drops.add(Items.create("infernal_blade", 1));
                break;
            case "ashbone_archer":
                drops.add(new ItemStack(Material.ARROW, r.nextInt(3 + looting)));
                add(drops, "ashbone", r.nextInt(2 + looting));
                break;
            case "hellhound": add(drops, "hellhound_fang", r.nextInt(2 + looting) - (byPlayer ? 0 : 1)); break;
            case "cinder_imp": add(drops, "imp_horn", r.nextInt(2 + looting) - 0); break;
            case "soul_wraith": add(drops, "soul_essence", r.nextInt(2 + looting)); break;
            case "magma_hulk":
                drops.add(new ItemStack(Material.MAGMA_CREAM, 1 + r.nextInt(2 + looting)));
                if (r.nextInt(3) == 0) drops.add(Items.create("molten_core", 1));
                break;
            case "pyre_warden":
                drops.add(new ItemStack(Material.BLAZE_ROD, 1 + r.nextInt(1 + looting)));
                add(drops, "pyre_ember", r.nextInt(2 + looting));
                break;
            case "shade": add(drops, "void_shard", r.nextInt(2 + looting)); break;
            case "brimstone_spider":
                drops.add(new ItemStack(Material.STRING, r.nextInt(3 + looting)));
                add(drops, "brimstone", r.nextInt(2 + looting));
                break;
            case "pigman_berserker":
                drops.add(new ItemStack(Material.GOLD_NUGGET, 2 + r.nextInt(5 + looting)));
                add(drops, "hellforged_shard", r.nextInt(2) == 0 ? 1 : 0);
                break;
            case "dread_rider":
                drops.add(new ItemStack(Material.BONE, 1 + r.nextInt(2 + looting)));
                add(drops, "hellforged_shard", r.nextInt(2 + looting));
                if (t.e.getVehicle() != null && t.e.getVehicle().getScoreboardTags().contains("jn_mount")) t.e.getVehicle().remove();
                break;
            case "charred_ghoul":
                drops.add(new ItemStack(Material.ROTTEN_FLESH, r.nextInt(3 + looting)));
                add(drops, "charred_bone", r.nextInt(2 + looting));
                break;
            case "cinder_witch":
                drops.add(new ItemStack(Material.GLOWSTONE_DUST, r.nextInt(3 + looting)));
                add(drops, "hex_ember", 1 + r.nextInt(1 + looting));
                break;
            default:
        }
        drops.removeIf(s -> s == null || s.getAmount() <= 0);
        return true;
    }

    /** A Magma Hulk bursts into embers; a Dread Rider's horse leaves with him. */
    static void died(Mobs m, Mobs.T t) {
        if (t.spec.kind.equals("magma_hulk")) {
            Location l = t.e.getLocation();
            for (int i = 0; i < 2 + (t.elite ? 2 : 0); i++) m.spawn("ember", l.clone().add(m.random.nextDouble() - 0.5, 0.2, m.random.nextDouble() - 0.5), false);
            m.ability("magma_hulk_burst");
        }
    }

    private static void add(List<ItemStack> drops, String id, int n) { if (n > 0) drops.add(Items.create(id, n)); }

    static String describe() { return ChatColor.stripColor(KINDS.size() + " fiends"); }
}
