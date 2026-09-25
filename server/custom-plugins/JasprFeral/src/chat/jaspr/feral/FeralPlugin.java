package chat.jaspr.feral;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.minecraft.server.v1_12_R1.EntityInsentient;

/**
 * Feral fauna (owner request 2026-09-25): every passive animal hunts survival players, each with its own
 * fighting style. Villagers stay peaceful (trading), and so does anything owned or cared for: tamed,
 * leashed or name-tagged animals and babies. Nothing attacks in Peaceful, Creative or Spectator.
 *
 * Wolves, polar bears, ocelots and llamas already carry attack AI in 1.12, so they are simply pointed at
 * the player (setTarget) and get a signature extra on hit. The rest have no attack AI at all: they are
 * steered with their own pathfinder (never teleported) and strike when in reach.
 *
 * Bounded by design: one sweep per second per player (nearby entities only), at most MAX_TRACKED hunters,
 * repaths every 4 ticks, and velocity is only ever set once per move (a charge, a leap, a knockback) --
 * never re-sent every tick (an earlier siege bug flooded clients with velocity packets).
 */
public final class FeralPlugin extends JavaPlugin implements Listener {
    enum Style {
        STAMPEDE("Cow: charges and tramples"), SPORES("Mooshroom: spore bursts that sicken"),
        GORE("Pig: rams, bowls you over, gorges on your rations"), HEADBUTT("Sheep: headbutts that stagger"),
        PECK("Chicken: fast pecks, calls the flock"), POUNCE("Rabbit: leaps in from range"),
        KICK("Horse/donkey/mule: bites in front, rear-kicks behind, bucks"), NIP("Bat: nips and flutters off"),
        INK("Squid: ink-blinds swimmers"), DIVE("Parrot: dive-bombs from above"), MAUL("Polar bear: mauls, wounds bleed"),
        AMBUSH("Ocelot: ambush pounce that pins"), SPIT("Llama: spits"), PACK("Wolf: hunts as a pack");
        final String text;
        Style(String text) { this.text = text; }
    }

    private static final Map<EntityType, Style> STYLES = new EnumMap<>(EntityType.class);
    static {
        STYLES.put(EntityType.COW, Style.STAMPEDE);
        STYLES.put(EntityType.MUSHROOM_COW, Style.SPORES);
        STYLES.put(EntityType.PIG, Style.GORE);
        STYLES.put(EntityType.SHEEP, Style.HEADBUTT);
        STYLES.put(EntityType.CHICKEN, Style.PECK);
        STYLES.put(EntityType.RABBIT, Style.POUNCE);
        STYLES.put(EntityType.HORSE, Style.KICK);
        STYLES.put(EntityType.DONKEY, Style.KICK);
        STYLES.put(EntityType.MULE, Style.KICK);
        STYLES.put(EntityType.BAT, Style.NIP);
        STYLES.put(EntityType.SQUID, Style.INK);
        STYLES.put(EntityType.PARROT, Style.DIVE);
        STYLES.put(EntityType.POLAR_BEAR, Style.MAUL);
        STYLES.put(EntityType.OCELOT, Style.AMBUSH);
        STYLES.put(EntityType.LLAMA, Style.SPIT);
        STYLES.put(EntityType.WOLF, Style.PACK);
    }
    /** These four already have attack AI; they are only aimed. */
    private static boolean vanillaAttacker(Style s) { return s == Style.MAUL || s == Style.AMBUSH || s == Style.SPIT || s == Style.PACK; }

    static final int MAX_TRACKED = 160;
    static final double AGGRO = 14, LOSE = 28;

    static final class Hunter {
        final LivingEntity mob; final Style style; UUID target;
        long nextHit, nextMove, chargeUntil, nextSpecial;
        Hunter(LivingEntity mob, Style style, UUID target) { this.mob = mob; this.style = style; this.target = target; }
    }

    private final Map<UUID, Hunter> hunters = new LinkedHashMap<>();
    private final Map<UUID, Integer> bleeding = new HashMap<>();
    private final Map<Style, long[]> hits = new EnumMap<>(Style.class), turned = new EnumMap<>(Style.class);
    private long stepCount, navFailures;
    /** Game-tick clock driven by the 2-tick step. */
    private long clock() { return stepCount * 2; }

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::sweep, 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::step, 41L, 2L);
        getServer().getScheduler().runTaskTimer(this, this::metrics, 6000L, 6000L);
        for (Style s : Style.values()) { hits.put(s, new long[1]); turned.put(s, new long[1]); }
        getLogger().info("FERAL_READY styles=" + Style.values().length + " types=" + STYLES.size()
            + " maxTracked=" + MAX_TRACKED + " aggro=" + (int) AGGRO + " friendly=villagers,tamed,leashed,named,babies");
    }

    @Override public void onDisable() {
        for (Hunter h : hunters.values()) release(h);
        hunters.clear();
        getLogger().info("FERAL_STOPPED");
    }

    // ------------------------------------------------------------------ who fights whom

    static boolean feral(Entity e) {
        if (!(e instanceof LivingEntity) || e.isDead() || !e.isValid()) return false;
        if (!STYLES.containsKey(e.getType())) return false;
        LivingEntity mob = (LivingEntity) e;
        if (mob.getCustomName() != null || mob.isLeashed()) return false;
        if (e instanceof Tameable && (((Tameable) e).isTamed() || ((Tameable) e).getOwner() != null)) return false;
        if (e instanceof AbstractHorse && ((AbstractHorse) e).getPassengers().size() > 0) return false;
        if (e instanceof Ageable && !((Ageable) e).isAdult()) return false;
        if (e instanceof Rabbit && ((Rabbit) e).getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY) return false; // already hostile
        return true;
    }

    static boolean prey(Player p) {
        if (p == null || !p.isOnline() || p.isDead()) return false;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        return p.getWorld().getDifficulty() != Difficulty.PEACEFUL;
    }

    private void sweep() {
        for (Iterator<Map.Entry<UUID, Integer>> it = bleeding.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            Player p = getServer().getPlayer(e.getKey());
            if (p == null || !prey(p) || e.getValue() <= 0) { it.remove(); continue; }
            p.damage(1.0);
            p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2,
                new org.bukkit.material.MaterialData(org.bukkit.Material.REDSTONE_BLOCK));
            e.setValue(e.getValue() - 1);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            if (!prey(p)) continue;
            for (Entity e : p.getNearbyEntities(AGGRO, AGGRO / 2, AGGRO)) {
                if (hunters.size() >= MAX_TRACKED) return;
                if (hunters.containsKey(e.getUniqueId()) || !feral(e)) continue;
                LivingEntity mob = (LivingEntity) e;
                if (!mob.hasLineOfSight(p)) continue;
                hunt(mob, p);
            }
        }
    }

    private Hunter hunt(LivingEntity mob, Player p) {
        Hunter h = new Hunter(mob, STYLES.get(mob.getType()), p.getUniqueId());
        turned.get(h.style)[0]++;
        h.nextHit = clock() + 10;
        hunters.put(mob.getUniqueId(), h);
        unafraid(mob);
        aim(h, p);
        if (h.style == Style.PECK) rally(mob, p, EntityType.CHICKEN, 8);
        if (h.style == Style.PACK) rally(mob, p, EntityType.WOLF, 16);
        return h;
    }

    /** Pulls the rest of the flock / pack onto the same player (bounded by MAX_TRACKED). */
    private void rally(LivingEntity caller, Player p, EntityType type, double radius) {
        for (Entity e : caller.getNearbyEntities(radius, radius / 2, radius)) {
            if (hunters.size() >= MAX_TRACKED) return;
            if (e.getType() != type || hunters.containsKey(e.getUniqueId()) || !feral(e)) continue;
            Hunter h = new Hunter((LivingEntity) e, STYLES.get(type), p.getUniqueId());
            turned.get(h.style)[0]++;
            h.nextHit = clock() + 10;
            hunters.put(e.getUniqueId(), h);
            unafraid((LivingEntity) e);
            aim(h, p);
        }
    }

    private void aim(Hunter h, Player p) {
        if (!vanillaAttacker(h.style) || !(h.mob instanceof Creature)) return;
        if (h.mob instanceof Wolf) ((Wolf) h.mob).setAngry(true);
        if (((Creature) h.mob).getTarget() != p) ((Creature) h.mob).setTarget(p);
    }

    /**
     * A feral animal no longer panics when hit or keeps its distance from players (ocelots): those vanilla
     * goals would pull it away from the hunt every few ticks. Removed once, when it turns; it stays feral.
     */
    private void unafraid(LivingEntity mob) {
        try {
            net.minecraft.server.v1_12_R1.Entity self = ((CraftEntity) mob).getHandle();
            if (!(self instanceof EntityInsentient)) return;
            net.minecraft.server.v1_12_R1.PathfinderGoalSelector goals = ((EntityInsentient) self).goalSelector;
            java.lang.reflect.Field all = net.minecraft.server.v1_12_R1.PathfinderGoalSelector.class.getDeclaredField("b");
            all.setAccessible(true);
            java.util.List<net.minecraft.server.v1_12_R1.PathfinderGoal> drop = new java.util.ArrayList<>();
            for (Object item : (java.util.Set<?>) all.get(goals)) {
                java.lang.reflect.Field g = item.getClass().getDeclaredField("a");
                g.setAccessible(true);
                Object goal = g.get(item);
                if (goal instanceof net.minecraft.server.v1_12_R1.PathfinderGoalPanic
                        || goal instanceof net.minecraft.server.v1_12_R1.PathfinderGoalAvoidTarget)
                    drop.add((net.minecraft.server.v1_12_R1.PathfinderGoal) goal);
            }
            for (net.minecraft.server.v1_12_R1.PathfinderGoal goal : drop) goals.a(goal);
        } catch (Throwable t) {
            navFailures++;
        }
    }

    private void release(Hunter h) {
        try {
            if (h.mob.isValid() && h.mob instanceof Creature && vanillaAttacker(h.style)) {
                ((Creature) h.mob).setTarget(null);
                if (h.mob instanceof Wolf) ((Wolf) h.mob).setAngry(false);
            }
        } catch (RuntimeException ignored) { }
    }

    /** Vanilla AI must not quietly drop a feral target (e.g. llamas only "want" wolves). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void retarget(EntityTargetEvent e) {
        Hunter h = hunters.get(e.getEntity().getUniqueId());
        if (h == null) return;
        Player p = getServer().getPlayer(h.target);
        if (e.getTarget() == p) return;
        if (prey(p) && p.getWorld() == h.mob.getWorld() && p.getLocation().distanceSquared(h.mob.getLocation()) < LOSE * LOSE) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ the hunt, every 2 ticks

    private void step() {
        stepCount++;
        long now = clock();
        for (Iterator<Hunter> it = hunters.values().iterator(); it.hasNext(); ) {
            Hunter h = it.next();
            Player p = getServer().getPlayer(h.target);
            if (!feral(h.mob) || !prey(p) || p.getWorld() != h.mob.getWorld()
                    || p.getLocation().distanceSquared(h.mob.getLocation()) > LOSE * LOSE) {
                release(h);
                it.remove();
                continue;
            }
            double d = p.getLocation().distance(h.mob.getLocation());
            if (vanillaAttacker(h.style)) { if (now % 20 == 0) aim(h, p); continue; }
            if (now >= h.nextMove) { move(h, p, d, now); h.nextMove = now + (h.style == Style.NIP || h.style == Style.DIVE ? 10 : 4); }
            if (now >= h.nextHit) strike(h, p, d, now);
        }
    }
    private void move(Hunter h, Player p, double d, long now) {
        switch (h.style) {
            case NIP: case DIVE: {
                Vector to = p.getEyeLocation().toVector().subtract(h.mob.getLocation().toVector());
                if (to.lengthSquared() < 0.01 || d > 12) return;
                double speed = h.style == Style.DIVE ? 0.55 : 0.4;
                if (h.style == Style.DIVE && d > 3 && h.mob.getLocation().getY() < p.getEyeLocation().getY() + 1.5)
                    to.setY(to.getY() + 2.5); // climb above first, then stoop
                h.mob.setVelocity(to.normalize().multiply(speed));
                return;
            }
            case INK: return; // squid swim on their own
            default: chase(h.mob, p, speed(h.style, now < h.chargeUntil));
        }
    }

    private static double speed(Style s, boolean charging) {
        switch (s) {
            case STAMPEDE: return charging ? 2.2 : 1.35;
            case POUNCE: return 1.6;
            case PECK: return 1.5;
            case KICK: return 1.1;
            default: return 1.3;
        }
    }

    private void chase(LivingEntity mob, Player p, double speed) {
        try {
            net.minecraft.server.v1_12_R1.Entity self = ((CraftEntity) mob).getHandle();
            if (!(self instanceof EntityInsentient)) return;
            EntityInsentient in = (EntityInsentient) self;
            net.minecraft.server.v1_12_R1.Entity prey = ((CraftEntity) p).getHandle();
            in.getNavigation().a(prey, speed);
            in.getControllerLook().a(prey, 30f, 30f);
        } catch (Throwable t) {
            navFailures++;
        }
    }

    private void strike(Hunter h, Player p, double d, long now) {
        Location at = h.mob.getLocation();
        World w = at.getWorld();
        switch (h.style) {
            case STAMPEDE:
                if (d > 3 && d < 8 && now >= h.nextSpecial && h.mob.isOnGround()) {
                    lunge(h.mob, p, 0.9, 0.3);
                    h.chargeUntil = now + 24; h.nextSpecial = now + 120;
                    w.playSound(at, Sound.ENTITY_COW_HURT, 1f, 0.6f);
                    return;
                }
                if (d > 1.9) return;
                boolean charged = now < h.chargeUntil;
                hit(h, p, charged ? 5 : 3);
                knock(h.mob, p, charged ? 1.3 : 0.5, charged ? 0.45 : 0.2);
                h.chargeUntil = 0; h.nextHit = now + 30;
                return;
            case SPORES:
                if (d < 4 && now >= h.nextSpecial) {
                    w.spawnParticle(Particle.SPELL_MOB, at.clone().add(0, 1, 0), 40, 1.5, 0.8, 1.5, 0);
                    w.playSound(at, Sound.ENTITY_MOOSHROOM_SHEAR, 0.8f, 0.5f);
                    for (Entity e : h.mob.getNearbyEntities(3, 2, 3))
                        if (e instanceof Player && prey((Player) e)) { hit(h, (Player) e, 1); sicken((Player) e, 80); }
                    h.nextSpecial = now + 160;
                }
                if (d > 1.9) return;
                hit(h, p, 2); sicken(p, 60); h.nextHit = now + 30;
                return;
            case GORE:
                if (d > 1.8) return;
                hit(h, p, 3);
                knock(h.mob, p, 0.6, 0.5);
                if (p.getFoodLevel() > 2) p.setFoodLevel(p.getFoodLevel() - 2); // it gets into your rations
                w.playSound(at, Sound.ENTITY_PIG_AMBIENT, 1f, 0.5f);
                h.nextHit = now + 28;
                return;
            case HEADBUTT:
                if (d > 1.9) return;
                hit(h, p, 2);
                knock(h.mob, p, 0.8, 0.3);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 3, true, false), true); // staggered
                w.playSound(at, Sound.ENTITY_SHEEP_AMBIENT, 1f, 0.5f);
                h.nextHit = now + 40;
                return;
            case PECK:
                if (d > 1.6) return;
                hit(h, p, 1);
                h.nextHit = now + 12;
                return;
            case POUNCE:
                if (d > 2.5 && d < 6 && now >= h.nextSpecial && h.mob.isOnGround()) {
                    lunge(h.mob, p, 0.75, 0.45);
                    h.nextSpecial = now + 60;
                    return;
                }
                if (d > 1.6) return;
                hit(h, p, 2); h.nextHit = now + 20;
                return;
            case KICK: {
                if (d > 2.6) return;
                Vector facing = at.getDirection().setY(0), toP = p.getLocation().toVector().subtract(at.toVector()).setY(0);
                boolean behind = facing.lengthSquared() > 0 && toP.lengthSquared() > 0 && facing.normalize().dot(toP.normalize()) < -0.3;
                if (behind) {
                    hit(h, p, 6); knock(h.mob, p, 1.6, 0.5);
                    w.playSound(at, Sound.ENTITY_HORSE_ANGRY, 1f, 0.8f);
                    h.nextHit = now + 50;
                } else if (now >= h.nextSpecial && Math.random() < 0.3) {
                    hit(h, p, 4); p.setVelocity(p.getVelocity().setY(0.75)); // bucked
                    w.playSound(at, Sound.ENTITY_HORSE_ANGRY, 1f, 1.1f);
                    h.nextSpecial = now + 100; h.nextHit = now + 40;
                } else {
                    hit(h, p, 2); h.nextHit = now + 24;
                }
                return;
            }
            case NIP:
                if (d > 1.7) return;
                hit(h, p, 1);
                h.mob.setVelocity(at.toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.6).setY(0.5));
                h.nextHit = now + 30;
                return;
            case INK:
                if (d > 5 || !inWater(p) || now < h.nextSpecial) return;
                hit(h, p, 2);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false), true);
                w.spawnParticle(Particle.SMOKE_LARGE, p.getEyeLocation(), 30, 0.6, 0.6, 0.6, 0.02);
                h.nextSpecial = now + 100;
                return;
            case DIVE:
                if (d > 1.7) return;
                hit(h, p, 2); knock(h.mob, p, 0.4, 0.1);
                h.mob.setVelocity(new Vector(0, 0.6, 0)); // pull up out of the dive
                h.nextHit = now + 30;
                return;
            default:
        }
    }

    // ------------------------------------------------------------------ the four with vanilla attacks: signature extras

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Entity src = e.getDamager();
        if (src instanceof org.bukkit.entity.Projectile && ((org.bukkit.entity.Projectile) src).getShooter() instanceof Entity)
            src = (Entity) ((org.bukkit.entity.Projectile) src).getShooter();
        Hunter h = src == null ? null : hunters.get(src.getUniqueId());
        if (h == null || !vanillaAttacker(h.style)) return;
        count(h.style);
        switch (h.style) {
            case MAUL: bleeding.merge(p.getUniqueId(), 4, Math::max); break;             // 1 damage/s for 4 s
            case AMBUSH: p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 1, true, false), true); break; // pinned
            case SPIT: sicken(p, 40); break;
            default:
        }
    }

    // ------------------------------------------------------------------ helpers

    private void hit(Hunter h, Player p, double damage) {
        p.damage(damage, h.mob); // a real mob attack: armour, difficulty scaling and other plugins apply
        count(h.style);
    }

    private void count(Style s) { hits.get(s)[0]++; }

    private static void lunge(LivingEntity mob, Player p, double speed, double lift) {
        Vector to = p.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 1e-4) return;
        mob.setVelocity(to.normalize().multiply(speed).setY(lift));
    }

    private static void knock(LivingEntity mob, Player p, double strength, double lift) {
        Vector away = p.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = new Vector(0, 0, 1);
        p.setVelocity(away.normalize().multiply(strength).setY(lift));
    }

    private static void sicken(Player p, int ticks) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, ticks, 0, true, false), true);
    }

    private static boolean inWater(Player p) {
        for (double dy : new double[]{0, -0.4, 1.0}) {
            org.bukkit.Material m = p.getLocation().add(0, dy, 0).getBlock().getType();
            if (m == org.bukkit.Material.WATER || m == org.bukkit.Material.STATIONARY_WATER) return true;
        }
        return false;
    }

    private void metrics() {
        StringBuilder s = new StringBuilder("FERAL_METRICS hunting=").append(hunters.size()).append(" bleeding=").append(bleeding.size())
            .append(" navFailures=").append(navFailures).append(" hits=");
        for (Map.Entry<Style, long[]> e : hits.entrySet()) if (e.getValue()[0] > 0) s.append(e.getKey().name().toLowerCase()).append(':').append(e.getValue()[0]).append(',');
        getLogger().info(s.toString());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "Feral fauna: " + ChatColor.WHITE + hunters.size() + " hunting" + ChatColor.GRAY
            + " (max " + MAX_TRACKED + ", aggro " + (int) AGGRO + "m)");
        for (Style st : Style.values())
            sender.sendMessage(ChatColor.GRAY + " - " + st.text + ChatColor.DARK_GRAY + "  turned: " + turned.get(st)[0] + "  hits: " + hits.get(st)[0]);
        return true;
    }
}
