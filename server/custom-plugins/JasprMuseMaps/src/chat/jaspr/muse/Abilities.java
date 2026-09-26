package chat.jaspr.muse;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

/**
 * The moves of Muse+GLM_Maps custom mobs, hazards and bosses. Everything here is combat only: no block is broken
 * or left behind (webs and ice are TempBlocks, fireballs and blasts never damage terrain -- MusePlugin clears their
 * block lists), and no glowing effect is ever applied (owner rule).
 */
final class Abilities {
    static final String PROJECTILE_META = "jaspr_muse_projectile";
    private final MusePlugin plugin;
    private final Random random = new Random();

    Abilities(MusePlugin plugin) { this.plugin = plugin; }

    private static boolean sees(LivingEntity a, Entity b) { return a.hasLineOfSight(b); }
    private static double dist(Entity a, Entity b) { return a.getWorld() == b.getWorld() ? a.getLocation().distance(b.getLocation()) : 1e9; }
    private Vector aim(LivingEntity from, Entity to) {
        return to.getLocation().add(0, 1.1, 0).toVector().subtract(from.getEyeLocation().toVector()).normalize();
    }
    private void mark(Entity projectile) { projectile.setMetadata(PROJECTILE_META, new FixedMetadataValue(plugin, true)); }

    List<Player> near(Location at, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers())
            if (MusePlugin.vulnerable(p) && p.getLocation().distanceSquared(at) <= radius * radius) out.add(p);
        return out;
    }

    /** Casts a signature ability; returns false when the situation does not suit it (try another). */
    boolean cast(String name, LivingEntity c, Player t, int tier) {
        World w = c.getWorld();
        double d = dist(c, t);
        switch (name) {
            case "FIREBALL": case "FIREBALL_VOLLEY": {
                if (d > 24 || !sees(c, t)) return false;
                int n = name.equals("FIREBALL") ? 1 : 3;
                for (int i = 0; i < n; i++) {
                    SmallFireball f = c.launchProjectile(SmallFireball.class, aim(c, t).add(jitter(0.08 * i)));
                    f.setIsIncendiary(false); f.setYield(0); mark(f);
                }
                w.playSound(c.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1, 1);
                return true;
            }
            case "FROST_NOVA": {
                List<Player> hit = near(c.getLocation(), 5);
                if (hit.isEmpty()) return false;
                w.spawnParticle(Particle.SNOW_SHOVEL, c.getLocation().add(0, 1, 0), 80, 2.5, 0.6, 2.5, 0.05);
                w.playSound(c.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, 0.6f);
                for (Player p : hit) { p.damage(3 + tier * 0.5, c); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1)); }
                return true;
            }
            case "VOID_BOLT": case "VOID_BOLTS": {
                if (d > 28) return false;
                int n = name.equals("VOID_BOLT") ? 1 : 3;
                for (int i = 0; i < n; i++) {
                    ShulkerBullet b = w.spawn(c.getEyeLocation().add(jitter(0.8)), ShulkerBullet.class);
                    b.setShooter(c); b.setTarget(t); mark(b);
                }
                w.playSound(c.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1, 1);
                return true;
            }
            case "WEB_SHOT": case "WEB_BARRAGE": {
                if (d > 18 || !sees(c, t)) return false;
                int n = name.equals("WEB_SHOT") ? 1 : 3;
                for (Player p : name.equals("WEB_SHOT") ? single(t) : near(c.getLocation(), 16)) {
                    if (n-- <= 0) break;
                    plugin.temp().place(p.getLocation().getBlock(), Material.WEB, 90, plugin.now());
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                }
                w.playSound(c.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1, 0.7f);
                return true;
            }
            case "VENOM_SPIT": case "POISON_BITE": {
                if (name.equals("POISON_BITE")) {
                    if (d > 3.5) return false;
                    t.damage(4 + tier, c); t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                    return true;
                }
                if (d > 18 || !sees(c, t)) return false;
                Snowball s = c.launchProjectile(Snowball.class, aim(c, t).multiply(1.4));
                s.setMetadata("jaspr_muse_venom", new FixedMetadataValue(plugin, tier)); mark(s);
                w.playSound(c.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1, 1);
                return true;
            }
            case "BLINK": case "MIST_BLINK": case "AMBUSH": {
                Location to = beside(t, name.equals("AMBUSH") ? -2.5 : 4 + random.nextInt(3));
                if (to == null) return false;
                w.spawnParticle(Particle.PORTAL, c.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
                if (name.equals("MIST_BLINK")) w.spawnParticle(Particle.SMOKE_LARGE, c.getLocation().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.02);
                c.teleport(to);
                w.playSound(to, Sound.ENTITY_ENDERMEN_TELEPORT, 1, 1);
                if (name.equals("AMBUSH")) c.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 40, 0));
                return true;
            }
            case "LIFE_DRAIN": case "BLOOD_DRAIN": {
                if (d > 9 || !sees(c, t)) return false;
                double amount = name.equals("LIFE_DRAIN") ? 3 + tier * 0.5 : 6;
                t.damage(amount, c);
                c.setHealth(Math.min(c.getMaxHealth(), c.getHealth() + amount * (name.equals("BLOOD_DRAIN") ? 3 : 1)));
                line(w, t.getLocation().add(0, 1, 0), c.getLocation().add(0, 1, 0), Particle.DAMAGE_INDICATOR);
                w.playSound(c.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.6f);
                return true;
            }
            case "LEAP": case "POUNCE": case "CHARGE": case "CUTLASS_LUNGE": {
                if (d < 3 || d > 16) return false;
                Vector v = t.getLocation().toVector().subtract(c.getLocation().toVector()).normalize();
                double power = name.equals("CHARGE") ? 1.8 : 1.2;
                c.setVelocity(v.multiply(power).setY(name.equals("CHARGE") ? 0.2 : 0.55));
                w.playSound(c.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, 0.8f);
                if (!name.equals("LEAP")) plugin.later(12, () -> { if (c.isValid() && dist(c, t) < 3.5) t.damage(6 + tier, c); });
                return true;
            }
            case "SLAM": case "GRAVITY_SLAM": {
                List<Player> hit = near(c.getLocation(), name.equals("SLAM") ? 4.5 : 7);
                if (hit.isEmpty()) return false;
                w.spawnParticle(Particle.EXPLOSION_LARGE, c.getLocation(), 3, 1.5, 0.2, 1.5, 0);
                w.playSound(c.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                for (Player p : hit) {
                    p.damage(4 + tier, c);
                    Vector away = p.getLocation().toVector().subtract(c.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() < 0.01) away = new Vector(0.1, 0, 0);
                    p.setVelocity(away.normalize().multiply(name.equals("SLAM") ? 0.4 : -0.6).setY(0.8));
                }
                return true;
            }
            case "ARROW_VOLLEY": case "RIME_VOLLEY": case "CANNONADE": {
                if (d > 30 || !sees(c, t)) return false;
                if (name.equals("CANNONADE")) {
                    for (int i = 0; i < 3; i++) {
                        final int k = i;
                        plugin.later(8 * i, () -> {
                            if (!c.isValid()) return;
                            LargeFireball f = c.launchProjectile(LargeFireball.class, aim(c, t).add(jitter(0.1 * k)));
                            f.setIsIncendiary(false); f.setYield(1.5f); mark(f);
                            w.playSound(c.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                        });
                    }
                    return true;
                }
                for (int i = 0; i < (name.equals("RIME_VOLLEY") ? 5 : 3); i++) {
                    Arrow a = c.launchProjectile(Arrow.class, aim(c, t).multiply(1.7).add(jitter(0.06)));
                    a.setPickupStatus(Arrow.PickupStatus.DISALLOWED); mark(a);
                    if (name.equals("RIME_VOLLEY")) a.setMetadata("jaspr_muse_frost", new FixedMetadataValue(plugin, true));
                }
                w.playSound(c.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1, 0.8f);
                return true;
            }
            case "FANGS": case "FANG_LINES": {
                if (d > 16) return false;
                int lines = name.equals("FANGS") ? 1 : 3;
                for (int l = 0; l < lines; l++) {
                    Vector dir = t.getLocation().toVector().subtract(c.getLocation().toVector()).setY(0);
                    if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                    dir = rotate(dir.normalize(), (l - (lines - 1) / 2.0) * 0.45);
                    for (int i = 1; i <= 8; i++) {
                        Location at = c.getLocation().add(dir.clone().multiply(i * 1.25));
                        Location floor = floor(at);
                        if (floor == null) continue;
                        final Location f = floor; final int delay = i * 2;
                        plugin.later(delay, () -> { EvokerFangs fang = (EvokerFangs) w.spawnEntity(f, EntityType.EVOKER_FANGS); fang.setOwner(c); });
                    }
                }
                w.playSound(c.getLocation(), Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL, 1, 1);
                return true;
            }
            case "MURK": case "BLINDING_LIGHT": {
                if (d > 14) return false;
                cloud(t.getLocation(), PotionEffectType.BLINDNESS, 70, 0, 2.5f, name.equals("MURK") ? Particle.SMOKE_LARGE : Particle.END_ROD);
                if (name.equals("BLINDING_LIGHT")) for (Player p : near(c.getLocation(), 10)) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                return true;
            }
            case "EMBER_AURA": case "HEAT_AURA": {
                List<Player> hit = near(c.getLocation(), name.equals("EMBER_AURA") ? 3.5 : 6);
                if (hit.isEmpty()) return false;
                w.spawnParticle(Particle.FLAME, c.getLocation().add(0, 1, 0), 50, 1.5, 0.8, 1.5, 0.02);
                for (Player p : hit) { p.setFireTicks(Math.max(p.getFireTicks(), 60)); if (name.equals("HEAT_AURA")) p.damage(3, c); }
                return true;
            }
            case "BROOD": case "SUMMON_RATS": case "SUMMON_KNIGHTS": case "SUMMON_BEARS": case "SUMMON_GLADIATORS":
            case "SUMMON_CHIPS": case "SUMMON_CHOIR": case "SUMMON_CREW": case "MAGMA_SPAWN":
                return summon(name, c, t, tier);
            case "WARD": case "WARCRY": {
                c.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 80, name.equals("WARD") ? 2 : 1));
                w.spawnParticle(Particle.SPELL_INSTANT, c.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0);
                if (name.equals("WARCRY")) rally(c, 12);
                w.playSound(c.getLocation(), name.equals("WARD") ? Sound.ITEM_SHIELD_BLOCK : Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK, 1, 0.7f);
                return true;
            }
            case "RALLY": return rally(c, 10);
            case "PHANTOM": {
                c.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 50, 0, false, false));
                plugin.later(50, () -> { if (c.isValid()) { Location to = beside(t, -2); if (to != null) c.teleport(to); } });
                return true;
            }
            case "STORM": {
                Location at = t.getLocation();
                if (w.getHighestBlockYAt(at) <= at.getBlockY() + 1) {
                    w.strikeLightningEffect(at.clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1));
                    t.damage(4 + tier * 0.5, c);
                } else { w.playSound(at, Sound.ENTITY_LIGHTNING_THUNDER, 0.6f, 1.2f); t.damage(2, c); }
                return true;
            }
            case "HOOK": case "GRAPPLE": {
                if (d < 4 || d > 16 || !sees(c, t)) return false;
                Vector pull = c.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(name.equals("HOOK") ? 1.1 : 1.5).setY(0.35);
                t.setVelocity(pull);
                line(w, c.getEyeLocation(), t.getEyeLocation(), Particle.CRIT);
                w.playSound(c.getLocation(), Sound.ENTITY_BOBBER_RETRIEVE, 1, 0.7f);
                return true;
            }
            case "ALCHEMY": case "POTION_BARRAGE": {
                if (d > 14 || !sees(c, t)) return false;
                PotionType[] kinds = {PotionType.INSTANT_DAMAGE, PotionType.POISON, PotionType.SLOWNESS, PotionType.WEAKNESS};
                int n = name.equals("ALCHEMY") ? 1 : 4;
                for (int i = 0; i < n; i++) {
                    ThrownPotion tp = c.launchProjectile(ThrownPotion.class, aim(c, t).multiply(0.7).add(new Vector(0, 0.2, 0)).add(jitter(0.12 * i)));
                    ItemStack item = new ItemStack(Material.SPLASH_POTION);
                    PotionMeta meta = (PotionMeta) item.getItemMeta();
                    meta.setBasePotionData(new PotionData(kinds[random.nextInt(kinds.length)]));
                    item.setItemMeta(meta);
                    tp.setItem(item);
                }
                w.playSound(c.getLocation(), Sound.ENTITY_WITCH_THROW, 1, 1);
                return true;
            }
            case "DRIFT": {
                if (d > 16 || !sees(c, t)) return false;
                t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0));
                w.spawnParticle(Particle.END_ROD, t.getLocation(), 15, 0.3, 0.2, 0.3, 0.02);
                return true;
            }
            case "SHRIEK": {
                List<Player> hit = near(c.getLocation(), 12);
                if (hit.isEmpty()) return false;
                w.playSound(c.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.2f, 0.6f);
                for (Player p : hit) { p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 100, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0)); }
                return true;
            }
            // ---------------------------------------------------------------- boss-only moves
            case "BAT_SWARM": {
                for (int i = 0; i < 6; i++) w.spawnEntity(t.getLocation().add(jitter(2)).add(0, 2, 0), EntityType.BAT).addScoreboardTag(Mobs.TAG);
                for (Player p : near(c.getLocation(), 12)) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                w.playSound(c.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.4f, 0.6f);
                return true;
            }
            case "MIDNIGHT_TOLL": {
                w.playSound(c.getLocation(), Sound.BLOCK_NOTE_BELL, 2f, 0.5f);
                for (Player p : near(c.getLocation(), 14)) {
                    p.damage(5 + tier, c);
                    Vector away = p.getLocation().toVector().subtract(c.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 0.01) p.setVelocity(away.normalize().multiply(1.1).setY(0.5));
                }
                w.spawnParticle(Particle.SPELL_WITCH, c.getLocation().add(0, 1, 0), 120, 5, 1, 5, 0.1);
                return true;
            }
            case "PUMPKIN_HEX": {
                for (Player p : near(c.getLocation(), 12)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    w.spawnParticle(Particle.SPELL_MOB, p.getLocation().add(0, 2, 0), 20, 0.3, 0.3, 0.3, 1);
                }
                w.playSound(c.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.4f, 0.6f);
                return true;
            }
            case "ERUPTION": {
                for (Player p : near(c.getLocation(), 16)) {
                    Location at = p.getLocation();
                    w.spawnParticle(Particle.LAVA, at, 20, 0.6, 0.2, 0.6, 0);
                    plugin.later(15, () -> {
                        w.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 60, 0.4, 1.5, 0.4, 0.05);
                        for (Player q : near(at, 1.8)) { q.damage(8, c); q.setFireTicks(80); q.setVelocity(new Vector(0, 0.9, 0)); }
                    });
                }
                return true;
            }
            case "METEOR": {
                Location sky = t.getLocation().add(random.nextInt(5) - 2, 18, random.nextInt(5) - 2);
                LargeFireball f = (LargeFireball) w.spawnEntity(sky, EntityType.FIREBALL);
                f.setDirection(new Vector(0, -1, 0)); f.setShooter(c); f.setIsIncendiary(false); f.setYield(2f); mark(f);
                w.playSound(t.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1, 0.5f);
                return true;
            }
            case "ICE_PRISON": {
                if (d > 16) return false;
                Block feet = t.getLocation().getBlock();
                int[][] ring = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] o : ring) for (int dy = 0; dy < 2; dy++) plugin.temp().place(feet.getRelative(o[0], dy, o[1]), Material.PACKED_ICE, 80, plugin.now());
                plugin.temp().place(feet.getRelative(0, 2, 0), Material.PACKED_ICE, 80, plugin.now());
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 2));
                w.playSound(t.getLocation(), Sound.BLOCK_GLASS_PLACE, 1, 0.5f);
                return true;
            }
            case "WHIRLWIND": {
                List<Player> hit = near(c.getLocation(), 4);
                if (hit.isEmpty()) return false;
                w.spawnParticle(Particle.SWEEP_ATTACK, c.getLocation().add(0, 1, 0), 12, 1.8, 0.3, 1.8, 0);
                for (Player p : hit) p.damage(6 + tier, c);
                w.playSound(c.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
                return true;
            }
            case "ROULETTE": {
                boolean good = random.nextInt(3) == 0;
                for (Player p : near(c.getLocation(), 16)) {
                    if (good) p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 120, 0));
                    else p.addPotionEffect(new PotionEffect(new PotionEffectType[]{PotionEffectType.SLOW, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.CONFUSION}[random.nextInt(4)], 120, 1));
                    p.sendMessage(ChatColor.GOLD + "The wheel spins... " + (good ? ChatColor.GREEN + "the House pays you." : ChatColor.RED + "the House wins."));
                }
                w.playSound(c.getLocation(), Sound.BLOCK_NOTE_PLING, 1, good ? 2f : 0.5f);
                return true;
            }
            case "JACKPOT": {
                w.spawnParticle(Particle.FIREWORKS_SPARK, c.getLocation().add(0, 1, 0), 120, 3, 1.5, 3, 0.2);
                w.playSound(c.getLocation(), Sound.ENTITY_FIREWORK_LARGE_BLAST, 1.6f, 0.8f);
                for (Player p : near(c.getLocation(), 7)) p.damage(9 + tier, c);
                return true;
            }
            case "HOLY_FIRE": {
                Location at = c.getLocation();
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    Location r = at.clone().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4);
                    w.spawnParticle(Particle.FLAME, r, 6, 0.1, 0.4, 0.1, 0.01);
                }
                plugin.later(20, () -> { for (Player p : near(at, 5.5)) if (p.getLocation().distance(at) > 2.5) { p.damage(7 + tier, c); p.setFireTicks(60); } });
                return true;
            }
            default: return false;
        }
    }

    private List<Player> single(Player p) { List<Player> l = new ArrayList<>(); l.add(p); return l; }

    private boolean summon(String name, LivingEntity c, Player t, int tier) {
        String type;
        int n;
        switch (name) {
            case "SUMMON_RATS": type = "SILVERFISH"; n = 5; break;
            case "SUMMON_KNIGHTS": type = "WITHER_SKELETON"; n = 2; break;
            case "SUMMON_BEARS": type = "POLAR_BEAR"; n = 2; break;
            case "SUMMON_GLADIATORS": type = "VINDICATOR"; n = 2; break;
            case "SUMMON_CHIPS": type = "VEX"; n = 3; break;
            case "SUMMON_CHOIR": type = "VEX"; n = 2; break;
            case "SUMMON_CREW": type = "SKELETON"; n = 3; break;
            case "MAGMA_SPAWN": type = "MAGMA_CUBE"; n = 2; break;
            default: type = c.getType() == EntityType.SPIDER ? "CAVE_SPIDER" : c.getType() == EntityType.ENDERMAN ? "ENDERMITE" : "SILVERFISH"; n = 2;
        }
        String site = Mobs.siteOf(c);
        if (site == null || plugin.minions(site) >= 10) return false;
        for (int i = 0; i < n; i++) {
            Location at = beside(c, 2);
            if (at == null) at = c.getLocation();
            LivingEntity m = Mobs.garrison(c.getWorld(), at, type, Math.max(1, tier - 2), site, t, random);
            if (m != null) plugin.adopt(site, m);
        }
        c.getWorld().playSound(c.getLocation(), Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_SUMMON, 1, 1);
        return true;
    }

    private boolean rally(LivingEntity c, double radius) {
        int n = 0;
        for (Entity e : c.getNearbyEntities(radius, 4, radius))
            if (e instanceof LivingEntity && Mobs.ours(e) && !(e instanceof Player)) {
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 120, 0));
                n++;
            }
        return n > 0;
    }

    void cloud(Location at, PotionEffectType type, int ticks, int amp, float radius, Particle particle) {
        AreaEffectCloud cloud = (AreaEffectCloud) at.getWorld().spawnEntity(at, EntityType.AREA_EFFECT_CLOUD);
        cloud.setRadius(radius); cloud.setDuration(100); cloud.setRadiusPerTick(-0.01f);
        cloud.setParticle(particle);
        cloud.addCustomEffect(new PotionEffect(type, ticks, amp), true);
        cloud.addScoreboardTag(Mobs.TAG);
    }

    /** A standing spot on the ground at `radius` from `e` (negative: behind it), or null. */
    Location beside(Entity e, double radius) {
        Location base = e.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double a;
            if (radius < 0 && attempt == 0) a = Math.toRadians(base.getYaw() + 90);
            else a = random.nextDouble() * Math.PI * 2;
            double r = Math.abs(radius);
            Location at = base.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Location f = floor(at);
            if (f != null && Math.abs(f.getY() - base.getY()) <= 3) return f.add(0.5, 0, 0.5);
        }
        return null;
    }

    static Location floor(Location at) {
        Block b = at.getBlock();
        for (int dy = 2; dy >= -3; dy--) {
            Block feet = b.getRelative(0, dy, 0), head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
            if (!feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid()
                    && !feet.isLiquid() && feet.getType() != Material.WEB)
                return feet.getLocation();
        }
        return null;
    }

    private Vector jitter(double s) { return new Vector((random.nextDouble() - 0.5) * s, (random.nextDouble() - 0.5) * s, (random.nextDouble() - 0.5) * s); }

    private static Vector rotate(Vector v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vector(v.getX() * cos - v.getZ() * sin, 0, v.getX() * sin + v.getZ() * cos);
    }

    static void line(World w, Location a, Location b, Particle p) {
        Vector step = b.toVector().subtract(a.toVector());
        double len = step.length();
        if (len < 0.1) return;
        step.normalize().multiply(0.5);
        Location at = a.clone();
        for (double i = 0; i < len; i += 0.5) { w.spawnParticle(p, at, 1, 0, 0, 0, 0); at.add(step); }
    }

    Random random() { return random; }

    static void runLater(org.bukkit.plugin.Plugin p, int ticks, Runnable r) { Bukkit.getScheduler().runTaskLater(p, r, ticks); }
}
