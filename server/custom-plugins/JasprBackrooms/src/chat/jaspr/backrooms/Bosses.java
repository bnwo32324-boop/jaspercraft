package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * One boss per level, waiting in the arena at its far end (owner, 2026-09-30: "Each liminal space should have its own
 * unique dangers, threats, and bosses"). A boss wakes when a player who has not beaten it walks into its arena, fights
 * with a boss bar, its own tricks and three phases (health grows with the number of fighters), and goes back to sleep
 * if everyone leaves. Everyone who hurt it or stood in the arena when it fell has beaten the level (br_clear_n): the
 * exit lets them through, and the first time they receive the boss's hoard (its level's gear, valuables). The
 * Lifeguard is the last: beating it conquers the Backrooms.
 */
final class Bosses implements Listener {
    static final String TAG = "br_boss", CRATE_TAG = "br_crate", MINION = "br_minion";

    enum Boss {
        SMILER(Level.YELLOW, EntityType.ENDERMAN, 180, 7, BarColor.YELLOW, "It smiles at you from the dark."),
        FOREMAN(Level.WAREHOUSE, EntityType.IRON_GOLEM, 340, 9, BarColor.RED, "Break's over. Back to work."),
        STOKER(Level.TUNNELS, EntityType.WITHER_SKELETON, 400, 9, BarColor.RED, "The fires must never go out."),
        LIVE_WIRE(Level.ELECTRICAL, EntityType.CREEPER, 440, 0, BarColor.BLUE, "The hum becomes a roar."),
        MANAGER(Level.OFFICE, EntityType.EVOKER, 500, 0, BarColor.WHITE, "You're late."),
        STRANGER(Level.CITY, EntityType.ILLUSIONER, 580, 0, BarColor.PURPLE, "It wears a face you almost know."),
        LIFEGUARD(Level.POOLS, EntityType.GIANT, 1300, 14, BarColor.GREEN, "No running. No diving. No leaving.");
        final Level level;
        final EntityType type;
        final int health, damage;
        final BarColor colour;
        final String intro;
        Boss(Level level, EntityType type, int health, int damage, BarColor colour, String intro) {
            this.level = level; this.type = type; this.health = health; this.damage = damage; this.colour = colour; this.intro = intro;
        }
        static Boss of(Level lv) { return values()[lv.index()]; }
    }

    private static final class Active {
        final Boss boss;
        final LivingEntity entity;
        final BossBar bar;
        final Location home;
        final Set<UUID> fighters = new HashSet<>();
        int ticks, phase = 1;
        long lonelySince;
        LivingEntity deep;
        Active(Boss boss, LivingEntity entity, Location home) {
            this.boss = boss; this.entity = entity; this.home = home;
            this.bar = Bukkit.createBossBar(ChatColor.BOLD + boss.level.boss, boss.colour, BarStyle.SEGMENTED_10);
        }
    }

    private final BackroomsPlugin plugin;
    private final Random random = new Random();
    private final Map<Level, Active> active = new EnumMap<>(Level.class);
    private final Map<Level, Long> cooldown = new EnumMap<>(Level.class);
    long woken, slain, slept, rewarded;

    Bosses(BackroomsPlugin plugin) { this.plugin = plugin; }

    static boolean isBoss(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    Location bossAt(Level lv) {
        Active a = active.get(lv);
        return a != null && a.entity.isValid() ? a.entity.getLocation() : null;
    }

    private static double danger(Level lv) { return (lv.index() + 1.0) / Level.ALL.length; }

    private List<Player> inArena(World w, Level lv) {
        List<Player> out = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            if (lv.inArena(l.getX(), l.getZ())) out.add(p);
        }
        return out;
    }

    // ---- waking and driving -------------------------------------------------------------------------------------------
    /** Every half second: wake bosses for challengers, drive the awake ones. */
    void tick() {
        World w = plugin.world();
        if (w == null) { for (Active a : active.values()) a.bar.removeAll(); active.clear(); return; }
        long now = System.currentTimeMillis();
        for (Level lv : Level.ALL) {
            Active a = active.get(lv);
            if (a == null) {
                if (cooldown.getOrDefault(lv, 0L) > now) continue;
                for (Player p : inArena(w, lv)) {
                    if (p.getGameMode() == GameMode.CREATIVE || Travel.cleared(p, lv.number) || p.getLocation().getX() < lv.arenaStart() + 6) continue;
                    spawn(lv);
                    break;
                }
                continue;
            }
            if (!a.entity.isValid()) { a.bar.removeAll(); active.remove(lv); continue; }
            drive(w, a, now);
        }
    }

    LivingEntity spawn(Level lv) {
        World w = plugin.world();
        if (w == null || active.containsKey(lv)) return null;
        Boss b = Boss.of(lv);
        int[] s = Styles.of(lv).bossSpot(lv);
        Location home = new Location(w, s[0] + 0.5, s[1], s[2] + 0.5, 90, 0);
        LivingEntity e = (LivingEntity) w.spawnEntity(home, b.type);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(Mobs.TAG);
        e.addScoreboardTag("jaspr_daylight_exempt");
        e.setCustomName(lv.colour + "" + ChatColor.BOLD + lv.boss);
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        int fighters = Math.max(1, inArena(w, lv).size());
        double hp = b.health * (1 + 0.35 * (fighters - 1));
        Mobs.set(e, Attribute.GENERIC_MAX_HEALTH, hp);
        e.setHealth(Math.min(hp, e.getMaxHealth()));
        Mobs.set(e, Attribute.GENERIC_ARMOR, 6 + 2 * lv.index());
        Mobs.set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
        Mobs.set(e, Attribute.GENERIC_FOLLOW_RANGE, 48);
        if (b.damage > 0) Mobs.set(e, Attribute.GENERIC_ATTACK_DAMAGE, b.damage * Mobs.damageScale(danger(lv)));
        switch (b) {
            case FOREMAN: ((IronGolem) e).setPlayerCreated(false); break;
            case STOKER: e.getEquipment().setItemInMainHand(fire(new ItemStack(Material.STONE_SWORD))); e.getEquipment().setItemInMainHandDropChance(0); break;
            case LIVE_WIRE: ((Creeper) e).setPowered(true); ((Creeper) e).setMaxFuseTicks(Integer.MAX_VALUE / 2); break;
            case STRANGER: e.getEquipment().setItemInMainHand(new ItemStack(Material.BOW)); e.getEquipment().setItemInMainHandDropChance(0); break;
            default: break;
        }
        Active a = new Active(b, e, home);
        active.put(lv, a);
        woken++;
        for (Player p : inArena(w, lv)) {
            p.sendTitle(lv.colour + "" + ChatColor.BOLD + lv.boss.toUpperCase(java.util.Locale.ROOT), ChatColor.GRAY + b.intro, 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.7f + 0.1f * lv.index());
        }
        plugin.getLogger().info("BACKROOMS_BOSS_WOKE level=" + lv.number + " boss=" + b.name() + " fighters=" + fighters + " health=" + Math.round(hp));
        return e;
    }

    private static ItemStack fire(ItemStack s) { s.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2); return s; }

    private void drive(World w, Active a, long now) {
        LivingEntity e = a.entity;
        Level lv = a.boss.level;
        a.ticks++;
        a.bar.setProgress(Math.max(0, Math.min(1, e.getHealth() / e.getMaxHealth())));
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < 56 * 56) a.bar.addPlayer(p); else a.bar.removePlayer(p);
        List<Player> near = inArena(w, lv);
        near.removeIf(p -> p.getGameMode() == GameMode.CREATIVE);
        if (near.isEmpty()) {
            if (a.lonelySince == 0) a.lonelySince = now;
            else if (now - a.lonelySince > 40_000L) sleep(a);
            return;
        }
        a.lonelySince = 0;
        Location at = e.getLocation();
        if (!lv.inArena(at.getX(), at.getZ()) || at.getY() < Level.FLOOR - 12) e.teleport(a.home);
        Player target = nearestOf(e, near);
        if (e instanceof Creature && target != null) ((Creature) e).setTarget(target);
        double hp = e.getHealth() / e.getMaxHealth();
        int phase = hp > 0.6 ? 1 : hp > 0.3 ? 2 : 3;
        if (phase > a.phase) { a.phase = phase; phaseUp(a, near); }
        double m = Mobs.damageScale(danger(lv));
        switch (a.boss) {
            case SMILER: smiler(a, target, near, m); break;
            case FOREMAN: foreman(a, target, near, m); break;
            case STOKER: stoker(a, target, near, m); break;
            case LIVE_WIRE: liveWire(a, target, near, m); break;
            case MANAGER: manager(a, target, near, m); break;
            case STRANGER: stranger(a, target, near, m); break;
            case LIFEGUARD: lifeguard(a, target, near, m); break;
            default: break;
        }
    }

    private void phaseUp(Active a, List<Player> near) {
        String[] titles = {"", "", "IT IS ANGRY", "LAST CHANCE"};
        for (Player p : near) p.sendTitle(a.boss.level.colour + titles[a.phase], ChatColor.GRAY + a.boss.level.boss + " is not done with you.", 5, 40, 10);
        a.entity.getWorld().playSound(a.entity.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 1.2f, 0.8f + 0.1f * a.phase);
        Mobs.Kind minion;
        switch (a.boss) {
            case SMILER: minion = Mobs.Kind.WRETCH; break;
            case FOREMAN: minion = Mobs.Kind.PACK_HOUND; break;
            case STOKER: minion = Mobs.Kind.BURNER; break;
            case LIVE_WIRE: minion = Mobs.Kind.SPARK; break;
            case MANAGER: minion = Mobs.Kind.PARTYGOER; break;
            case STRANGER: minion = Mobs.Kind.MIMIC; break;
            default: minion = Mobs.Kind.POOL_GUARDIAN; break;
        }
        int n = a.boss == Boss.LIFEGUARD ? 2 : 2 + a.phase - 2;
        for (int i = 0; i < n; i++) minion(a, minion);
        if (a.boss == Boss.LIFEGUARD && a.phase == 2 && a.deep == null) {
            Location c = a.home.clone();
            LivingEntity deep = (LivingEntity) c.getWorld().spawnEntity(c.add(4, 1, 0), EntityType.ELDER_GUARDIAN);
            deep.addScoreboardTag(Mobs.TAG);
            deep.addScoreboardTag(MINION);
            deep.setCustomName(ChatColor.DARK_AQUA + "The Deep");
            deep.setCustomNameVisible(true);
            Mobs.set(deep, Attribute.GENERIC_MAX_HEALTH, 160);
            deep.setHealth(160);
            a.deep = deep;
            for (Player p : near) p.sendMessage(ChatColor.DARK_AQUA + "Something vast rises from the bottom of the pool.");
        }
    }

    private void minion(Active a, Mobs.Kind k) {
        Location at = a.home.clone().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
        if (k == Mobs.Kind.POOL_GUARDIAN) at = a.home.clone().add(random.nextInt(7) - 3, 1, random.nextInt(7) - 3);
        if (!at.getBlock().getType().isSolid()) {
            LivingEntity m = plugin.mobs().spawn(k, at, danger(a.boss.level), false);
            m.addScoreboardTag(MINION);
        }
    }

    private void sleep(Active a) {
        a.bar.removeAll();
        removeMinions(a);
        a.entity.remove();
        active.remove(a.boss.level);
        slept++;
        plugin.getLogger().info("BACKROOMS_BOSS_SLEPT level=" + a.boss.level.number);
    }

    private void removeMinions(Active a) {
        for (Entity x : a.entity.getWorld().getEntities())
            if (x.getScoreboardTags().contains(MINION) && a.boss.level.inArena(x.getLocation().getX(), x.getLocation().getZ())) x.remove();
    }

    private static Player nearestOf(Entity e, List<Player> ps) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : ps) { double d = p.getLocation().distanceSquared(e.getLocation()); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    private static Vector away(Location from, Location to, double speed, double lift) {
        Vector v = to.toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1e-4) v = new Vector(1, 0, 0);
        return v.normalize().multiply(speed).setY(lift);
    }

    private void hurt(Player p, double amount, Entity by, boolean shock) {
        double k = shock ? plugin.gear().shockFactor(p) : 1;
        if (k <= 0) { p.getWorld().spawnParticle(Particle.CRIT_MAGIC, p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1); return; }
        p.damage(amount * k, by);
    }

    // ---- the seven fights -----------------------------------------------------------------------------------------------
    private void smiler(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        if (target == null) return;
        if (a.ticks % 6 == 0) {   // the stare
            for (Player p : near) if (p.getLocation().distanceSquared(e.getLocation()) < 16 * 16) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0));
            }
            e.getWorld().spawnParticle(Particle.END_ROD, e.getEyeLocation(), 20, 0.3, 0.1, 0.3, 0.01);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDERMEN_STARE, 1f, 0.6f);
        }
        if (a.ticks % (a.phase == 3 ? 6 : 10) == 3) {   // the lunge: out of the dark behind you
            Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));
            if (!behind.getBlock().getType().isSolid() && !behind.clone().add(0, 1, 0).getBlock().getType().isSolid() && a.boss.level.inArena(behind.getX(), behind.getZ())) {
                e.teleport(behind);
                hurt(target, a.boss.damage * m, e, false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void foreman(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        if (target == null) return;
        if (a.ticks % (a.phase == 3 ? 5 : 8) == 0) {   // crate toss
            Location from = e.getLocation().add(0, 3, 0);
            FallingBlock crate = e.getWorld().spawnFallingBlock(from, Material.WOOD, (byte) 1);
            crate.setDropItem(false);
            crate.setHurtEntities(false);
            crate.addScoreboardTag(CRATE_TAG);
            Vector to = target.getLocation().toVector().subtract(from.toVector());
            double dist = Math.max(1, to.length());
            crate.setVelocity(to.normalize().multiply(Math.min(1.6, 0.35 + dist * 0.06)).setY(0.45 + dist * 0.015));
            e.getWorld().playSound(from, Sound.ENTITY_IRONGOLEM_ATTACK, 1f, 0.6f);
        }
        if (a.ticks % 12 == 6) {   // forklift charge
            e.setVelocity(away(e.getLocation(), target.getLocation(), 1.3, 0.1));
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_MINECART_RIDING, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!e.isValid()) return;
                for (Player p : near) if (p.isOnline() && p.getLocation().distanceSquared(e.getLocation()) < 6.25) {
                    hurt(p, a.boss.damage * m, e, false);
                    if (!plugin.gear().steady(p)) p.setVelocity(away(e.getLocation(), p.getLocation(), 1.2, 0.5));
                }
            }, 8L);
        }
    }

    private void stoker(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        if (target == null) return;
        if (a.ticks % (a.phase == 3 ? 4 : 6) == 0) {   // furnace breath
            Vector dir = target.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize();
            for (int i = 0; i < 3 + a.phase; i++) {
                Vector spread = dir.clone().add(new Vector((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.25));
                SmallFireball f = e.launchProjectile(SmallFireball.class, spread.multiply(0.8));
                f.setIsIncendiary(false);
            }
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        }
        if (a.ticks % 10 == 5) {   // steam burst
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1, 0), 80, 2.5, 0.8, 2.5, 0.05);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.5f);
            for (Player p : near) if (p.getLocation().distanceSquared(e.getLocation()) < 25) {
                hurt(p, 5 * m, e, false);
                p.setVelocity(away(e.getLocation(), p.getLocation(), 0.9, 0.7));
                plugin.hazards().heat(p, 25);
            }
        }
    }

    private void liveWire(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        World w = e.getWorld();
        if (target == null) return;
        if (a.ticks % (a.phase == 3 ? 3 : 5) == 0) {   // an arc to the nearest
            w.strikeLightningEffect(target.getLocation());
            hurt(target, 6 * m, e, true);
        }
        if (a.ticks % 9 == 4) {   // chain lightning between everyone close together
            Player prev = target;
            int jumps = 0;
            for (Player p : near) {
                if (p == target || p.getLocation().distanceSquared(prev.getLocation()) > 100 || jumps >= 3) continue;
                line(w, prev.getLocation().add(0, 1, 0), p.getLocation().add(0, 1, 0), Particle.FIREWORKS_SPARK);
                hurt(p, 4 * m, e, true);
                prev = p;
                jumps++;
            }
        }
        if (a.ticks % 14 == 7) {   // the surge field: a warning ring, then it goes off
            Location c = e.getLocation();
            for (int i = 0; i < 32; i++) {
                double t = i * Math.PI / 16;
                w.spawnParticle(Particle.REDSTONE, c.clone().add(Math.cos(t) * 6, 0.3, Math.sin(t) * 6), 1, 0, 0, 0, 0);
            }
            w.playSound(c, Sound.BLOCK_NOTE_PLING, 1f, 0.5f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!e.isValid()) return;
                w.spawnParticle(Particle.EXPLOSION_LARGE, e.getLocation(), 3, 1, 0.5, 1, 0);
                w.playSound(e.getLocation(), Sound.ENTITY_LIGHTNING_THUNDER, 1f, 1.4f);
                for (Player p : near) if (p.isOnline() && p.getLocation().distanceSquared(e.getLocation()) < 36) {
                    hurt(p, 8 * m, e, true);
                    p.setVelocity(away(e.getLocation(), p.getLocation(), 0.9, 0.5));
                }
            }, 50L);
        }
        if (a.ticks % 4 == 2) for (int[] core : Electrical.cores(a.boss.level)) {   // the cores spit at anyone who stands close
            Location c = new Location(w, core[0] + 0.5, core[1] + 1, core[2] + 0.5);
            for (Player p : near) if (p.getLocation().distanceSquared(c) < 9) { line(w, c, p.getLocation().add(0, 1, 0), Particle.FIREWORKS_SPARK); hurt(p, 3 * m, e, true); }
        }
    }

    private void manager(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        if (target == null) return;
        if (a.ticks % 10 == 0) {   // the performance review: everyone is pulled in
            for (Player p : near) if (p.getLocation().distanceSquared(e.getLocation()) < 14 * 14) {
                p.setVelocity(away(p.getLocation(), e.getLocation(), 0.9, 0.3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1));
            }
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_SUMMON, 1f, 0.7f);
        }
        if (a.ticks % 14 == 7) for (Player p : near) fangLine(e, p.getLocation(), 12);   // the memo storm
    }

    private void fangLine(LivingEntity e, Location to, int length) {
        Vector d = to.toVector().subtract(e.getLocation().toVector()).setY(0);
        if (d.lengthSquared() < 1e-3) return;
        d.normalize();
        for (int i = 1; i <= length; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().clone().add(d.clone().multiply(k * 1.2));
                at.setY(Math.floor(at.getY()));
                EvokerFangs f = e.getWorld().spawn(at, EvokerFangs.class);
                f.setOwner(e);
            }, i);
        }
    }

    private void stranger(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        World w = e.getWorld();
        if (target == null) return;
        if (a.ticks % 10 == 0) {   // faces in the crowd
            for (int i = 0; i < 2 + a.phase; i++) {
                Location at = target.getLocation().clone().add(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
                if (at.getBlock().getType() == Material.AIR && a.boss.level.inArena(at.getX(), at.getZ())) {
                    LivingEntity c = plugin.mobs().spawn(Mobs.Kind.CITIZEN, at, danger(a.boss.level), false);
                    c.addScoreboardTag(MINION);
                }
            }
        }
        if (a.ticks % 14 == 7) {   // the street lights go out, and it steps aside
            for (Player p : near) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
            w.playSound(e.getLocation(), Sound.ENTITY_ILLUSION_ILLAGER_CAST_SPELL, 1.4f, 0.6f);
            Level lv = a.boss.level;
            Location to = new Location(w, lv.arenaStart() + 10 + random.nextInt(lv.arenaLength - 20) + 0.5, Level.WALK, random.nextInt(40) - 20 + 0.5);
            if (!to.getBlock().getType().isSolid() && !to.clone().add(0, 1, 0).getBlock().getType().isSolid()) e.teleport(to);
        }
    }

    private void lifeguard(Active a, Player target, List<Player> near, double m) {
        LivingEntity e = a.entity;
        World w = e.getWorld();
        Level lv = a.boss.level;
        if (target == null) return;
        Location at = e.getLocation(), to = target.getLocation();
        // It wades after you inside its pool, and slams whatever is in reach.
        double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        Location next = at.clone().add(dx / Math.max(dist, 1e-3) * 1.2, 0, dz / Math.max(dist, 1e-3) * 1.2);
        if (dist > 5 && Pools.deepEnd(lv, next.getBlockX(), next.getBlockZ())) e.setVelocity(new Vector(dx / dist * 0.25, e.getVelocity().getY(), dz / dist * 0.25));
        else e.setVelocity(new Vector(0, e.getVelocity().getY(), 0));
        if (!Pools.deepEnd(lv, at.getBlockX(), at.getBlockZ())) e.teleport(a.home);
        if (dist <= 7.5 && a.ticks % (a.phase == 3 ? 2 : 3) == 0) {
            hurt(target, a.boss.damage * m, e, false);
            target.setVelocity(away(at, to, 1.3, 0.6));
            w.playSound(to, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.5f);
        }
        if (a.ticks % 8 == 0) {   // the wave
            w.spawnParticle(Particle.WATER_SPLASH, at.clone().add(0, 6, 0), 200, 5, 2, 5, 0.3);
            w.playSound(at, Sound.ENTITY_GENERIC_SPLASH, 2f, 0.4f);
            for (Player p : near) if (p.getLocation().distanceSquared(at) < 12 * 12) { hurt(p, 6 * m, e, false); p.setVelocity(away(at, p.getLocation(), 1.0, 0.5)); }
        }
        if (a.ticks % 12 == 6) {   // the whistle
            w.playSound(at, Sound.BLOCK_NOTE_FLUTE, 2f, 2f);
            for (int i = 0; i < (a.phase >= 2 ? 2 : 1); i++) minion(a, Mobs.Kind.POOL_GUARDIAN);
        }
        if (a.phase >= 2 && a.ticks % 10 == 3) {   // the undertow
            Location c = a.home;
            for (Player p : near) if (p.getLocation().distanceSquared(c) < 18 * 18 && !plugin.gear().swimmer(p)) {
                p.setVelocity(away(p.getLocation(), c, 0.7, -0.1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1));
            }
        }
        if (a.phase == 3 && a.ticks % 6 == 0) for (Player p : near) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));   // last call: the lights go
    }

    private static void line(World w, Location a, Location b, Particle p) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.multiply(1 / len);
        for (double t = 0; t < len; t += 0.4) w.spawnParticle(p, a.clone().add(d.clone().multiply(t)), 1, 0.02, 0.02, 0.02, 0);
    }

    // ---- events -----------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        if (!isBoss(e.getEntity())) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile && ((Projectile) src).getShooter() instanceof Entity) src = (Entity) ((Projectile) src).getShooter();
        if (!(src instanceof Player)) return;
        for (Active a : active.values()) if (a.entity.equals(e.getEntity())) a.fighters.add(src.getUniqueId());
    }

    /** The Live Wire never goes off by itself. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent e) { if (isBoss(e.getEntity())) e.setCancelled(true); }

    /** The Smiler blinks about, but never out of its room. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent e) {
        if (!isBoss(e.getEntity()) || e.getTo() == null) return;
        Level lv = Level.at(e.getTo().getX(), e.getTo().getZ());
        if (lv == null || !lv.inArena(e.getTo().getX(), e.getTo().getZ())) e.setCancelled(true);
    }

    /** A crate lands: it breaks apart on whoever stands there (no block is left behind). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrate(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock) || !e.getEntity().getScoreboardTags().contains(CRATE_TAG)) return;
        e.setCancelled(true);
        Location at = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        e.getEntity().remove();
        World w = at.getWorld();
        w.spawnParticle(Particle.EXPLOSION_NORMAL, at, 12, 0.6, 0.4, 0.6, 0.02);
        w.playSound(at, Sound.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1f, 0.8f);
        Active foreman = active.get(Level.WAREHOUSE);
        double m = Mobs.damageScale(danger(Level.WAREHOUSE));
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(at) < 7 && !plugin.gear().steady(p))
            hurt(p, 6 * m, foreman != null ? foreman.entity : null, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!isBoss(e.getEntity())) return;
        Active a = null;
        for (Active x : active.values()) if (x.entity.equals(e.getEntity())) a = x;
        e.getDrops().clear();
        if (a == null) return;
        Level lv = a.boss.level;
        e.setDroppedExp(80 + 60 * lv.index());
        a.bar.removeAll();
        active.remove(lv);
        cooldown.put(lv, System.currentTimeMillis() + 20_000L);
        removeMinions(a);
        if (a.deep != null && a.deep.isValid()) a.deep.remove();
        slain++;
        Set<Player> winners = new HashSet<>(inArena(e.getEntity().getWorld(), lv));
        for (UUID id : a.fighters) { Player p = Bukkit.getPlayer(id); if (p != null && plugin.isBackrooms(p.getWorld())) winners.add(p); }
        int first = 0;
        for (Player p : winners) {
            boolean isFirst = !Travel.cleared(p, lv.number);   // (addScoreboardTag reports true even for a tag the player has)
            if (isFirst) p.addScoreboardTag(Travel.CLEAR + lv.number);
            if (isFirst) first++;
            rewarded++;
            for (ItemStack s : Loot.hoard(lv, isFirst, random)) for (ItemStack left : p.getInventory().addItem(s).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            if (lv.last()) {
                if (isFirst) for (ItemStack left : p.getInventory().addItem(Items.make("exit_sign")).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                victory(p);
            } else {
                p.sendTitle(lv.colour + "" + ChatColor.BOLD + "LEVEL " + lv.number + " CLEARED", ChatColor.GRAY + "The exit is open to you.", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                p.sendMessage(lv.colour + lv.boss + ChatColor.GRAY + " is beaten. The glowing exit behind the arena now lets you through to " + lv.next().colour + lv.next().label() + ChatColor.GRAY + ".");
            }
        }
        plugin.getLogger().info("BACKROOMS_BOSS_SLAIN level=" + lv.number + " winners=" + winners.size() + " first=" + first + " fighters=" + a.fighters.size());
    }

    /**
     * The Lifeguard is beaten: the Backrooms are conquered (the guide kit's tag jr_beat_backrooms, so the compass,
     * checklist and /goals show it), with a title, the news and the Backrooms Conqueror's Crown. Done here rather than by
     * the shared kit's victory(), which would also hand out the Three Realms' crown again to anyone who has it.
     */
    private void victory(Player p) {
        String tag = "jr_beat_backrooms";
        if (p.getScoreboardTags().contains(tag)) {
            p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "OUT AGAIN", ChatColor.YELLOW + "The Lifeguard is beaten once more", 10, 70, 20);
            return;
        }
        p.addScoreboardTag(tag);
        p.addScoreboardTag(tag + "_on_" + java.time.LocalDate.now());
        p.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "BACKROOMS CONQUERED", ChatColor.WHITE + "You found the way out", 10, 110, 30);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcastMessage(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " has conquered " + ChatColor.YELLOW + "the Backrooms" + ChatColor.GRAY + "!");
        ItemStack crown = new ItemStack(Material.GOLD_HELMET);
        org.bukkit.inventory.meta.ItemMeta meta = crown.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Backrooms Conqueror's Crown");
        meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Proof that you found the way out of the Backrooms.", ChatColor.DARK_GRAY + "Won " + java.time.LocalDate.now()));
        crown.setItemMeta(meta);
        for (ItemStack left : p.getInventory().addItem(crown).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.sendMessage(ChatColor.GRAY + "The exit behind the Deep End leads out of the Backrooms.");
        if (plugin.guide() != null) plugin.guide().refreshBooks(p);
        plugin.getLogger().info("BACKROOMS_CONQUERED realmsBeaten=" + GuideKit.realmsBeaten(p));
    }

    /** Owner test: wake a level's boss now (if the world is loaded and it is asleep). */
    LivingEntity wake(Level lv) { cooldown.remove(lv); return spawn(lv); }

    void shutdown() { for (Active a : active.values()) { a.bar.removeAll(); removeMinions(a); a.entity.remove(); } active.clear(); }

    String describe() { return "awake=" + active.size() + " woken=" + woken + " slain=" + slain + " slept=" + slept + " rewarded=" + rewarded; }
}
