package chat.jaspr.muse;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TippedArrow;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

/** The two environmental hazards of each Muse+GLM_Maps site (catalogue danger.hazards), fired near players inside. */
final class Hazards {
    private final MusePlugin plugin;
    private final Abilities abilities;
    private final Random random = new Random();

    Hazards(MusePlugin plugin, Abilities abilities) { this.plugin = plugin; this.abilities = abilities; }

    static String label(Catalog.Hazard h) {
        String k = h.kind.charAt(0) + h.kind.substring(1).toLowerCase().replace('_', ' ');
        return k + " (" + h.param.toLowerCase().replace('_', ' ') + ")";
    }

    /** Fire one hazard at p (inside site). Tier scales damage. Never breaks or leaves blocks. */
    void fire(Catalog.Hazard h, Player p, Planner.Plan plan, String siteKey) {
        World w = p.getWorld();
        Location at = p.getLocation();
        int tier = plan.design.tier;
        switch (h.kind) {
            case "MIASMA": {
                PotionEffectType type;
                switch (h.param) {
                    case "POISON": type = PotionEffectType.POISON; break;
                    case "WITHER": type = PotionEffectType.WITHER; break;
                    case "WEAKNESS": type = PotionEffectType.WEAKNESS; break;
                    case "SLOW": type = PotionEffectType.SLOW; break;
                    case "HUNGER": type = PotionEffectType.HUNGER; break;
                    case "NAUSEA": type = PotionEffectType.CONFUSION; break;
                    default: type = PotionEffectType.BLINDNESS;
                }
                abilities.cloud(at, type, 80, tier >= 4 ? 1 : 0, 2.5f, Particle.SPELL_MOB);
                w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.5f);
                break;
            }
            case "ARROW_TRAP": {
                Location wall = wallPoint(p);
                if (wall == null) break;
                Vector dir = p.getEyeLocation().toVector().subtract(wall.toVector()).normalize();
                Arrow a;
                if (h.param.equals("VENOM") || h.param.equals("FROST")) {
                    TippedArrow ta = w.spawnArrow(wall, dir, 1.8f, 2f, TippedArrow.class);
                    ta.setBasePotionData(new PotionData(h.param.equals("VENOM") ? PotionType.POISON : PotionType.SLOWNESS));
                    a = ta;
                } else a = w.spawnArrow(wall, dir, 1.8f, 2f);
                if (h.param.equals("FLAME")) a.setFireTicks(200);
                a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                a.setMetadata(Abilities.PROJECTILE_META, new FixedMetadataValue(plugin, true));
                w.playSound(wall, Sound.BLOCK_DISPENSER_LAUNCH, 1, 1);
                break;
            }
            case "FANG_TRAP": {
                if (h.param.equals("RING")) {
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        Location f = Abilities.floor(at.clone().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8));
                        if (f != null) w.spawnEntity(f.add(0.5, 0, 0.5), EntityType.EVOKER_FANGS);
                    }
                } else {
                    Vector d = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5).normalize();
                    for (int i = -3; i <= 3; i++) {
                        Location f = Abilities.floor(at.clone().add(d.clone().multiply(i * 1.2)));
                        if (f == null) continue;
                        final Location spot = f.add(0.5, 0, 0.5);
                        plugin.later((i + 3) * 2, () -> ((EvokerFangs) w.spawnEntity(spot, EntityType.EVOKER_FANGS)).getLocation());
                    }
                }
                break;
            }
            case "BRAZIER_GUST":
                w.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.04);
                w.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1, 0.8f);
                p.setFireTicks(Math.max(p.getFireTicks(), h.param.equals("INFERNO") ? 120 : 60));
                if (h.param.equals("INFERNO")) p.damage(2 + tier * 0.5);
                break;
            case "ICE_SNARE":
                w.spawnParticle(Particle.SNOW_SHOVEL, at.clone().add(0, 1, 0), 50, 0.6, 0.8, 0.6, 0.02);
                w.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, h.param.equals("DEEPFREEZE") ? 50 : 60, h.param.equals("DEEPFREEZE") ? 3 : 1));
                if (h.param.equals("DEEPFREEZE")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 1));
                p.damage(h.param.equals("DEEPFREEZE") ? 3 : 1);
                break;
            case "VOID_RIFT":
                w.spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 80, 0.8, 1, 0.8, 0.6);
                w.playSound(at, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.6f);
                if (h.param.equals("LEVITATE")) p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0));
                else spawn(siteKey, p, "ENDERMITE", 2, tier);
                break;
            case "STORM_CALL":
                if (w.getHighestBlockYAt(at) <= at.getBlockY() + 1) {
                    Location strike = at.clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                    w.strikeLightningEffect(strike);
                    if (strike.distance(at) < 3) p.damage(3 + tier * 0.5);
                    if (h.param.equals("TEMPEST")) p.setVelocity(new Vector(random.nextDouble() - 0.5, 0.5, random.nextDouble() - 0.5));
                } else {
                    w.playSound(at, Sound.ENTITY_LIGHTNING_THUNDER, 0.5f, 1.4f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 0));
                }
                break;
            case "RUBBLE_FALL":
                w.spawnParticle(Particle.BLOCK_DUST, at.clone().add(0, 3, 0), 60, 0.6, 0.4, 0.6, 0.1, new org.bukkit.material.MaterialData(Material.GRAVEL));
                w.playSound(at, h.param.equals("ANVIL") ? Sound.BLOCK_ANVIL_LAND : Sound.BLOCK_GRAVEL_BREAK, 1, 0.8f);
                p.damage(h.param.equals("ANVIL") ? 4 + tier * 0.5 : 2 + tier * 0.4);
                if (h.param.equals("ANVIL")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1));
                break;
            case "MAGMA_SURGE":
                w.spawnParticle(Particle.LAVA, at, 20, 1, 0.2, 1, 0);
                spawn(siteKey, p, "MAGMA_CUBE", h.param.equals("LARGE") ? 1 : 2, h.param.equals("LARGE") ? tier + 2 : 1);
                break;
            case "SWARM":
                spawn(siteKey, p, h.param, 3, tier);
                break;
            case "HAUNT":
                if (h.param.equals("VEX")) spawn(siteKey, p, "VEX", 1, tier);
                else {
                    w.playSound(at, Sound.BLOCK_NOTE_BELL, 1.4f, 0.5f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                }
                break;
            case "GRAVITY_WELL": {
                Location centre = new Location(w, plan.x + plan.design.width() / 2.0, at.getY(), plan.z + plan.design.depth() / 2.0);
                if (h.param.equals("PULL")) {
                    Vector v = centre.toVector().subtract(at.toVector());
                    if (v.lengthSquared() > 4) p.setVelocity(v.normalize().multiply(0.9).setY(0.25));
                } else if (!p.isOnGround()) { p.setVelocity(new Vector(0, -1.2, 0)); p.damage(3); }
                w.spawnParticle(Particle.SPELL_WITCH, at, 30, 0.5, 0.5, 0.5, 0);
                break;
            }
            case "CURSE_AURA":
                p.addPotionEffect(new PotionEffect(h.param.equals("FATIGUE") ? PotionEffectType.SLOW_DIGGING : PotionEffectType.WEAKNESS, 240, h.param.equals("FATIGUE") ? 1 : 0), true);
                w.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.2f);
                break;
            case "DARKNESS":
                if (h.param.equals("BLIND")) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                else { p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 0)); w.playSound(at, Sound.AMBIENT_CAVE, 1, 0.7f); }
                break;
            case "GHAST_BARRAGE":
                if (w.getHighestBlockYAt(at) > at.getBlockY() + 1) break;
                for (int i = 0; i < (h.param.equals("SIEGE") ? 3 : 1); i++) {
                    Location from = at.clone().add(random.nextInt(9) - 4, 16, random.nextInt(9) - 4);
                    Vector dir = at.toVector().subtract(from.toVector()).normalize();
                    if (h.param.equals("SIEGE")) {
                        SmallFireball f = (SmallFireball) w.spawnEntity(from, EntityType.SMALL_FIREBALL);
                        f.setDirection(dir); f.setIsIncendiary(false);
                        f.setMetadata(Abilities.PROJECTILE_META, new FixedMetadataValue(plugin, true));
                    } else {
                        LargeFireball f = (LargeFireball) w.spawnEntity(from, EntityType.FIREBALL);
                        f.setDirection(dir); f.setIsIncendiary(false); f.setYield(1.2f);
                        f.setMetadata(Abilities.PROJECTILE_META, new FixedMetadataValue(plugin, true));
                    }
                }
                w.playSound(at, Sound.ENTITY_GHAST_WARN, 1, 1);
                break;
            case "SHULKER_NEST":
                for (int i = 0; i < (h.param.equals("RING") ? 3 : 2); i++) {
                    Location wall = wallPoint(p);
                    if (wall == null) continue;
                    ShulkerBullet b = w.spawn(wall, ShulkerBullet.class);
                    b.setTarget(p);
                    b.setMetadata(Abilities.PROJECTILE_META, new FixedMetadataValue(plugin, true));
                }
                w.playSound(at, Sound.ENTITY_SHULKER_SHOOT, 1, 0.8f);
                break;
            case "BLAZE_VENT":
                w.spawnParticle(Particle.FLAME, at, h.param.equals("RING") ? 80 : 40, h.param.equals("RING") ? 2 : 0.3, 1.2, h.param.equals("RING") ? 2 : 0.3, 0.05);
                w.playSound(at, Sound.ENTITY_BLAZE_BURN, 1, 1);
                for (Player q : abilities.near(at, h.param.equals("RING") ? 3 : 1.2)) { q.setFireTicks(Math.max(q.getFireTicks(), 60)); q.damage(2 + tier * 0.4); }
                break;
            case "WEB_SNARE": {
                Block feet = at.getBlock();
                plugin.temp().place(feet, Material.WEB, 80, plugin.now());
                if (h.param.equals("COCOON")) plugin.temp().place(feet.getRelative(0, 1, 0), Material.WEB, 60, plugin.now());
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                w.playSound(at, Sound.ENTITY_SPIDER_AMBIENT, 1, 0.6f);
                break;
            }
            default: break;
        }
    }

    private void spawn(String siteKey, Player p, String type, int n, int tier) {
        if (plugin.minions(siteKey) >= 14) return;
        for (int i = 0; i < n; i++) {
            Location at = abilities.beside(p, 3 + random.nextInt(3));
            if (at == null) continue;
            LivingEntity m = Mobs.garrison(p.getWorld(), at, type, Math.max(1, tier - 1), siteKey, p, random);
            if (m != null) plugin.adopt(siteKey, m);
        }
    }

    /** A point just in front of a wall 3-9 blocks from the player, facing them (for traps). */
    private Location wallPoint(Player p) {
        Location eye = p.getEyeLocation();
        for (int attempt = 0; attempt < 6; attempt++) {
            Vector dir = new Vector(random.nextDouble() - 0.5, random.nextDouble() * 0.3 - 0.1, random.nextDouble() - 0.5).normalize();
            Location at = eye.clone();
            for (int i = 1; i <= 9; i++) {
                at.add(dir);
                if (at.getBlock().getType().isSolid()) {
                    if (i < 3) break;
                    return at.subtract(dir.multiply(0.8));
                }
            }
        }
        return null;
    }
}
