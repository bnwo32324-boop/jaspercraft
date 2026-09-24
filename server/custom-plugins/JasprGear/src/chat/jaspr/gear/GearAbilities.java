package chat.jaspr.gear;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

/**
 * Every gear mechanic. Real mechanics first (velocity control, damage interception, projectile
 * deflection, teleports, banking, sensing); potions are only minor helpers and are ambient,
 * particle-free and short so removing an item stops them within a second. Attribute modifiers
 * use fixed per-item UUIDs and are removed exactly. No night vision, glowing or light.
 */
final class GearAbilities implements Listener {
    static final long ARC_MS = 6000, DODGE_MS = 4000, BLINK_MS = 6000, CHEST_MS = 10000, REPEL_MS = 8000,
        SCAN_MS = 3000, THREAT_MS = 2500, TRACKER_MS = 4000, LEECH_MS = 1000, PHASE_BLINK_MS = 10000,
        GYRO_MS = 4000, VEST_IDLE_MS = 5000, LAST_STAND_MS = 20L * 60L * 1000L;
    static final double ARC_PARALYSIS_CHANCE = 0.30;
    static final double VEST_MAX = 6.0;
    static final int HOVER_LIMIT_TICKS = 240;

    private final GearPlugin plugin;
    private final Random random = new Random();
    private final Map<GearItem, Map<Attribute, AttributeModifier>> modifiers = new EnumMap<GearItem, Map<Attribute, AttributeModifier>>(GearItem.class);
    private final Map<UUID, Long> fallGrace = new HashMap<UUID, Long>();
    boolean arcing;
    int arcs, chains, discharges, dodges, blinks, absorbed, glances, lastStands, bleeds, climbs, brakes, rests;

    GearAbilities(GearPlugin plugin) {
        this.plugin = plugin;
        modifier(GearItem.CAPACITOR_BELT, Attribute.GENERIC_MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_SCALAR);
        modifier(GearItem.SPRINT_BRACE, Attribute.GENERIC_MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_SCALAR);
        modifier(GearItem.SPRINT_BRACE, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.30, AttributeModifier.Operation.ADD_NUMBER);
        modifier(GearItem.GYRO_STABILIZER, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.80, AttributeModifier.Operation.ADD_NUMBER);
        modifier(GearItem.RAZOR_CLAWS, Attribute.GENERIC_ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_NUMBER);
    }

    private void modifier(GearItem item, Attribute attribute, double amount, AttributeModifier.Operation op) {
        UUID id = UUID.nameUUIDFromBytes(("jaspr-gear:" + item.id + ":" + attribute.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<Attribute, AttributeModifier> map = modifiers.get(item);
        if (map == null) modifiers.put(item, map = new EnumMap<Attribute, AttributeModifier>(Attribute.class));
        map.put(attribute, new AttributeModifier(id, "jaspr_gear_" + item.id, amount, op));
    }

    /** Expected modifier totals, exposed for the self-test. */
    Map<Attribute, Double> expectedTotals(Set<GearItem> worn) {
        Map<Attribute, Double> out = new EnumMap<Attribute, Double>(Attribute.class);
        for (GearItem item : worn) {
            Map<Attribute, AttributeModifier> map = modifiers.get(item);
            if (map == null) continue;
            for (Map.Entry<Attribute, AttributeModifier> e : map.entrySet()) {
                Double old = out.get(e.getKey());
                out.put(e.getKey(), (old == null ? 0.0 : old) + e.getValue().getAmount());
            }
        }
        return out;
    }

    static boolean active(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    // ================================================================ recompute (every second + on change)

    void apply(Player p, GearProfile prof) {
        Set<GearItem> worn = active(p) ? prof.worn() : java.util.Collections.<GearItem>emptySet();
        for (Map.Entry<GearItem, Map<Attribute, AttributeModifier>> e : modifiers.entrySet()) {
            boolean want = worn.contains(e.getKey());
            for (Map.Entry<Attribute, AttributeModifier> m : e.getValue().entrySet()) setModifier(p, m.getKey(), m.getValue(), want);
        }
        potion(p, PotionEffectType.FAST_DIGGING, 0, worn.contains(GearItem.REBREATHER) && headInWater(p));
        if (!worn.contains(GearItem.SCRAP_MAGNET)) prof.magnet = false;
        if (!worn.contains(GearItem.GRAV_HARNESS)) dropLevitation(p, prof);
        if (!worn.contains(GearItem.RIOT_VEST)) prof.vestCharge = 0;
        if (worn.isEmpty()) return;
        if (worn.contains(GearItem.TRITIUM_RING)) p.removePotionEffect(PotionEffectType.BLINDNESS);
        if (worn.contains(GearItem.NECROTIC_RING)) p.removePotionEffect(PotionEffectType.WITHER);
        if (worn.contains(GearItem.TOXIN_INJECTOR) || (worn.contains(GearItem.REBREATHER) && touchingWater(p)))
            p.removePotionEffect(PotionEffectType.POISON);
        if (worn.contains(GearItem.THERMAL_GOGGLES) && p.getFireTicks() > 0) p.setFireTicks(0);
        if (worn.contains(GearItem.REBREATHER) && headInWater(p)) p.setRemainingAir(p.getMaximumAir());
    }

    void clear(Player p, GearProfile prof) {
        for (Map<Attribute, AttributeModifier> map : modifiers.values())
            for (Map.Entry<Attribute, AttributeModifier> m : map.entrySet()) setModifier(p, m.getKey(), m.getValue(), false);
        potion(p, PotionEffectType.FAST_DIGGING, 0, false);
        dropLevitation(p, prof);
        prof.magnet = false;
        prof.restTicks = 0;
    }

    static void setModifier(Player p, Attribute attribute, AttributeModifier modifier, boolean want) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier present = null;
        for (AttributeModifier m : inst.getModifiers()) if (m.getUniqueId().equals(modifier.getUniqueId())) present = m;
        if (present != null && (!want || present.getAmount() != modifier.getAmount() || present.getOperation() != modifier.getOperation())) {
            inst.removeModifier(present);
            present = null;
        }
        if (want && present == null) inst.addModifier(modifier);
    }

    /** Ours = ambient, particle-free and short. Never touches a player's own longer potion. */
    private static void potion(Player p, PotionEffectType type, int amp, boolean want) {
        PotionEffect current = p.getPotionEffect(type);
        boolean ours = current != null && current.isAmbient() && !current.hasParticles() && current.getDuration() <= 80;
        if (want) {
            if (current == null || (ours && current.getDuration() < 50)) p.addPotionEffect(new PotionEffect(type, 70, amp, true, false), true);
        } else if (ours) {
            p.removePotionEffect(type);
        }
    }

    /** Per player, once a second: shield recharge, motion tracker, ready notices. */
    void slowTick(Player p, GearProfile prof, long now) {
        if (!active(p)) return;
        Set<GearItem> worn = prof.worn();
        if (worn.contains(GearItem.RIOT_VEST) && prof.vestCharge < VEST_MAX && now - prof.vestHitAt >= VEST_IDLE_MS) {
            prof.vestCharge = Math.min(VEST_MAX, prof.vestCharge + 1.0 / 3.0);
            if (prof.vestCharge >= VEST_MAX && prof.vestHitAt > 0) {
                bar(p, ChatColor.GOLD + "Riot Vest " + pips(prof.vestCharge) + ChatColor.GRAY + " plates ready");
                prof.vestHitAt = 0;
            }
        }
        if (worn.contains(GearItem.TRITIUM_RING) && now >= prof.trackerReady && now >= prof.threatReady) {
            prof.trackerReady = now + TRACKER_MS;
            int count = 0;
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity e : p.getNearbyEntities(12, 6, 12)) {
                if (!hostile(e) || e.isDead()) continue;
                count++;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) { best = d; nearest = (LivingEntity) e; }
            }
            if (nearest != null) bar(p, ChatColor.GREEN + "Tracker: " + ChatColor.WHITE + count + ChatColor.GRAY
                + (count == 1 ? " hostile | " : " hostiles | ") + ChatColor.WHITE + name(nearest) + ChatColor.GRAY + " "
                + Math.round(Math.sqrt(best)) + "m " + direction(p, nearest.getLocation()));
        }
        if (prof.readyName != null && now >= prof.readyAt) {
            bar(p, ChatColor.GREEN + prof.readyName + " ready");
            prof.readyName = null;
        }
    }

    static void armReady(GearProfile prof, String name, long at) { prof.readyName = name; prof.readyAt = at; }

    static String pips(double charge) {
        int full = (int) Math.floor(charge + 1e-6);
        StringBuilder out = new StringBuilder(ChatColor.AQUA.toString()).append('[');
        for (int i = 0; i < (int) VEST_MAX; i++) out.append(i < full ? '#' : '-');
        return out.append(']').toString();
    }

    // ================================================================ fast tick (every 2 ticks)

    void fastTick(Player p, GearProfile prof, long now, int tick) {
        if (!active(p)) { dropLevitation(p, prof); return; }
        Location loc = p.getLocation();
        String world = loc.getWorld().getName();
        prof.moved2 = world.equals(prof.lastWorld)
            ? square(loc.getX() - prof.lastX) + square(loc.getY() - prof.lastY) + square(loc.getZ() - prof.lastZ) : 1.0;
        prof.lastX = loc.getX(); prof.lastY = loc.getY(); prof.lastZ = loc.getZ(); prof.lastWorld = world;
        Set<GearItem> worn = prof.worn();
        if (worn.isEmpty()) { dropLevitation(p, prof); prof.restTicks = 0; prof.sneakTicks = 0; return; }
        boolean still = prof.moved2 < 0.0025;
        boolean liquid = loc.getBlock().isLiquid();

        if (worn.contains(GearItem.THERMAL_GOGGLES) && p.isSneaking() && p.isOnGround() && still) {
            prof.sneakTicks += 2;
            if (prof.sneakTicks >= 20 && now >= prof.scanReady) { prof.scanReady = now + SCAN_MS; scan(p, prof, 8, "Thermal scan"); }
        } else prof.sneakTicks = 0;

        if (worn.contains(GearItem.TEDDY_BEAR) && p.isSneaking() && p.isOnGround() && still) {
            prof.restTicks += 2;
            if (prof.restTicks >= 60 && (prof.restTicks - 60) % 40 == 0) {
                double max = p.getMaxHealth();
                if (p.getHealth() < max) {
                    p.setHealth(Math.min(max, p.getHealth() + 1.0));
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.1, 0), 1, 0.2, 0.1, 0.2, 0);
                    if (prof.restTicks == 60) { bar(p, ChatColor.GOLD + "Resting" + ChatColor.GRAY + " - hugging the old bear"); rests++; }
                }
            }
        } else prof.restTicks = 0;

        boolean climbing = false;
        if (worn.contains(GearItem.RAZOR_CLAWS) && !p.isOnGround() && !p.isFlying() && !liquid && facingWall(p)) {
            climbing = true;
            p.setFallDistance(0f);
            if (p.isSneaking()) p.setVelocity(new Vector(0, 0.0, 0));
            else if (loc.getPitch() < 55f) { p.setVelocity(new Vector(0, 0.22, 0)); if ((tick & 15) == 0) climbs++; }
            if ((tick & 7) == 0) p.getWorld().playSound(loc, Sound.BLOCK_GRAVEL_STEP, 0.3f, 1.6f);
        }
        if (!climbing && worn.contains(GearItem.GRAV_HARNESS)) harness(p, prof, liquid); else if (!worn.contains(GearItem.GRAV_HARNESS)) dropLevitation(p, prof);

        if (prof.magnet && worn.contains(GearItem.SCRAP_MAGNET) && (tick & 3) == 0) pull(p);

        if (worn.contains(GearItem.REBREATHER) && liquid && headInWater(p) && !p.isOnGround() && prof.moved2 > 0.004) {
            p.setVelocity(loc.getDirection().multiply(0.24));
            p.setRemainingAir(p.getMaximumAir());
        }

        if (worn.contains(GearItem.GYRO_STABILIZER) && !p.isOnGround() && !liquid && p.getFallDistance() > 6f
            && now >= prof.gyroReady && groundWithin(loc, 4)) {
            prof.gyroReady = now + GYRO_MS;
            p.setVelocity(new Vector(0, -0.12, 0));
            p.setFallDistance(0f);
            p.getWorld().spawnParticle(Particle.CLOUD, loc, 16, 0.4, 0.1, 0.4, 0.02);
            p.getWorld().playSound(loc, Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.6f);
            bar(p, ChatColor.GOLD + "Gyro brake engaged");
            brakes++;
        }
    }

    private static double square(double v) { return v * v; }

    static boolean facingWall(Player p) {
        Location loc = p.getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yaw) * 0.55, dz = Math.cos(yaw) * 0.55;
        World w = loc.getWorld();
        for (double dy : new double[]{0.3, 1.3}) {
            Block b = w.getBlockAt((int) Math.floor(loc.getX() + dx), (int) Math.floor(loc.getY() + dy), (int) Math.floor(loc.getZ() + dz));
            if (b.getType().isSolid() && b.getType() != Material.BARRIER) return true;
        }
        return false;
    }

    private static boolean groundWithin(Location loc, int blocks) {
        Block b = loc.getBlock();
        for (int i = 1; i <= blocks; i++) if (b.getRelative(BlockFace.DOWN, i).getType().isSolid()) return true;
        return false;
    }

    private void harness(Player p, GearProfile prof, boolean liquid) {
        boolean floating = !p.isOnGround() && !p.isFlying() && !p.isGliding() && !p.isInsideVehicle()
            && !liquid && !climbable(p.getLocation().getBlock().getType());
        if (!floating) { prof.airTicks = 0; prof.riseTicks = 0; prof.sinkTicks = 0; dropLevitation(p, prof); return; }
        prof.airTicks += 2;
        if (prof.airTicks < 6) return;
        int amp;
        if (prof.riseTicks > 0 && prof.airTicks <= HOVER_LIMIT_TICKS) { amp = 1; prof.riseTicks -= 2; }
        else if (prof.sinkTicks > 0) { amp = 252; prof.sinkTicks -= 2; }
        else amp = prof.airTicks > HOVER_LIMIT_TICKS ? 253 : 255; // 255 = hover, 253 = slow descent
        PotionEffect current = p.getPotionEffect(PotionEffectType.LEVITATION);
        if (current != null && !prof.ownsLevitation) return; // a real levitation effect (shulker) wins
        if (current == null || current.getAmplifier() != amp || current.getDuration() < 6)
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 14, amp, true, false), true);
        if (!prof.ownsLevitation) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SHULKER_BULLET_HIT, 0.3f, 1.6f);
        prof.ownsLevitation = true;
        p.setFallDistance(0f);
        if (prof.airTicks == HOVER_LIMIT_TICKS + 2) bar(p, ChatColor.LIGHT_PURPLE + "Grav Harness drained" + ChatColor.GRAY + " - descending");
    }

    void dropLevitation(Player p, GearProfile prof) {
        if (!prof.ownsLevitation) return;
        prof.ownsLevitation = false;
        PotionEffect current = p.getPotionEffect(PotionEffectType.LEVITATION);
        if (current != null && current.isAmbient() && current.getDuration() <= 14) p.removePotionEffect(PotionEffectType.LEVITATION);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        GearProfile prof = plugin.profile(p);
        if (prof == null || !prof.ownsLevitation) return;
        if (p.isSneaking()) { prof.sinkTicks = 10; prof.riseTicks = 0; } else { prof.riseTicks = 10; prof.sinkTicks = 0; }
    }

    /** Sprinter's Brace: sprint-jumps launch farther and a little higher. */
    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!p.isSprinting()) return;
        GearProfile prof = plugin.profile(p);
        if (prof == null || !active(p) || !prof.worn().contains(GearItem.SPRINT_BRACE)) return;
        Vector dir = e.getTo().toVector().subtract(e.getFrom().toVector()).setY(0);
        if (dir.lengthSquared() < 1e-4) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return;
        p.setVelocity(dir.normalize().multiply(0.52).setY(0.47));
    }

    private void pull(Player p) {
        Location target = p.getLocation().add(0, 0.6, 0);
        int moved = 0;
        for (Entity e : p.getNearbyEntities(7, 4, 7)) {
            if (moved >= 24) break;
            if (e instanceof Item) {
                if (((Item) e).getPickupDelay() > 0) continue;
            } else if (!(e instanceof ExperienceOrb)) continue;
            Vector to = target.toVector().subtract(e.getLocation().toVector());
            double d = to.length();
            if (d < 0.8) continue;
            e.setVelocity(to.multiply(Math.min(0.45, 0.12 + d * 0.05) / d));
            moved++;
        }
    }

    /** Nearest ore within r blocks (Thermal Goggles: 8, Burrower's Seismic Sense: 12). */
    void scan(Player p, GearProfile prof, int r, String label) {
        Location at = p.getLocation();
        World w = at.getWorld();
        int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            int y = by + dy;
            if (y < 0 || y > 255) continue;
            Block b = w.getBlockAt(bx + dx, y, bz + dz);
            String ore = oreName(b.getType());
            if (ore == null || !("any".equals(prof.scanFilter) || ore.toLowerCase(java.util.Locale.ROOT).startsWith(prof.scanFilter))) continue;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = b; }
        }
        p.getWorld().playSound(at, Sound.BLOCK_NOTE_HAT, 0.4f, 1.8f);
        if (best == null) { bar(p, ChatColor.GOLD + label + ": " + ChatColor.GRAY + "no " + ("any".equals(prof.scanFilter) ? "ore" : prof.scanFilter + " ore") + " within " + r + "m"); return; }
        int dy = best.getY() - by;
        String vertical = dy > 1 ? ", " + dy + " up" : dy < -1 ? ", " + (-dy) + " down" : "";
        bar(p, ChatColor.GOLD + label + ": " + ChatColor.WHITE + oreName(best.getType()) + ChatColor.GRAY + " "
            + Math.round(Math.sqrt(bestD)) + "m " + direction(p, best.getLocation().add(0.5, 0.5, 0.5)) + vertical);
    }

    static String oreName(Material m) {
        switch (m) {
            case COAL_ORE: return "Coal Ore";
            case IRON_ORE: return "Iron Ore";
            case GOLD_ORE: return "Gold Ore";
            case REDSTONE_ORE: case GLOWING_REDSTONE_ORE: return "Redstone Ore";
            case LAPIS_ORE: return "Lapis Ore";
            case DIAMOND_ORE: return "Diamond Ore";
            case EMERALD_ORE: return "Emerald Ore";
            case QUARTZ_ORE: return "Quartz Ore";
            default: return null;
        }
    }

    static String direction(Player p, Location target) {
        Location at = p.getLocation();
        double dx = target.getX() - at.getX(), dz = target.getZ() - at.getZ();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((yaw - at.getYaw()) % 360 + 540) % 360 - 180;
        double a = Math.abs(rel);
        if (a <= 30) return "ahead";
        if (a >= 150) return "behind";
        String side = rel > 0 ? "right" : "left";
        return a < 70 ? "ahead-" + side : a > 110 ? "behind-" + side : side;
    }

    // ================================================================ keys (G / H / J, or /gear arc|dodge|magnet)

    /**
     * Active abilities. Phase 2: each one spends adrenaline (GearVitals.COST_*) only when it
     * actually fires, after the cooldown check; paralysis locks them all. Creative is free.
     */
    void key(Player p, GearProfile prof, String action) {
        if (!active(p)) return;
        Set<GearItem> worn = prof.worn();
        long now = System.currentTimeMillis();
        GearVitals vitals = plugin.vitals;
        if ("arc".equals(action)) {
            if (!worn.contains(GearItem.CAPACITOR_BELT)) { bar(p, ChatColor.GRAY + "Arc Shot needs a Capacitor Belt"); return; }
            if (now < prof.arcReady) { cooldown(p, "Arc Shot", prof.arcReady - now); return; }
            if (!vitals.afford(p, prof, GearVitals.COST_ARC, "Arc Shot")) return;
            if (arc(p)) { prof.arcReady = now + ARC_MS; armReady(prof, "Arc Shot", prof.arcReady); vitals.spend(p, prof, GearVitals.COST_ARC); }
            else bar(p, ChatColor.GRAY + "Arc Shot: no hostile in your sights (16m)");
        } else if ("dodge".equals(action)) {
            if (worn.contains(GearItem.PHASE_HEADSET)) {
                if (p.isSneaking()) {
                    if (now < prof.chestReady) { cooldown(p, "Remote ender chest", prof.chestReady - now); return; }
                    if (!vitals.afford(p, prof, GearVitals.COST_CHEST, "Remote ender chest")) return;
                    prof.chestReady = now + CHEST_MS;
                    p.openInventory(p.getEnderChest());
                    p.playSound(p.getLocation(), Sound.BLOCK_ENDERCHEST_OPEN, 0.6f, 1.2f);
                    vitals.spend(p, prof, GearVitals.COST_CHEST);
                    return;
                }
                if (now < prof.blinkReady) { cooldown(p, "Blink", prof.blinkReady - now); return; }
                if (wet(p)) { bar(p, ChatColor.DARK_AQUA + "Phase Headset shorted out - dry off first"); return; }
                if (!vitals.afford(p, prof, GearVitals.COST_BLINK, "Blink")) return;
                if (blink(p)) { prof.blinkReady = now + BLINK_MS; armReady(prof, "Blink", prof.blinkReady); vitals.spend(p, prof, GearVitals.COST_BLINK); }
                else bar(p, ChatColor.GRAY + "Blink: no room in that direction");
            } else if (worn.contains(GearItem.CAPACITOR_BELT)) {
                if (now < prof.dodgeReady) { cooldown(p, "Dodge", prof.dodgeReady - now); return; }
                if (!vitals.afford(p, prof, GearVitals.COST_DODGE, "Dodge")) return;
                vitals.spend(p, prof, GearVitals.COST_DODGE);
                prof.dodgeReady = now + DODGE_MS;
                armReady(prof, "Dodge", prof.dodgeReady);
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                dir.normalize();
                if (p.isSneaking()) dir.multiply(-1);
                p.setVelocity(dir.multiply(1.1).setY(0.25));
                fallGrace.put(p.getUniqueId(), now + 1500);
                p.getWorld().spawnParticle(Particle.CRIT_MAGIC, p.getLocation().add(0, 0.5, 0), 12, 0.3, 0.2, 0.3, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_FLAP, 0.5f, 1.7f);
                dodges++;
            } else bar(p, ChatColor.GRAY + "Dodge needs a Capacitor Belt or Phase Headset");
        } else if ("magnet".equals(action)) {
            if (!worn.contains(GearItem.SCRAP_MAGNET)) { bar(p, ChatColor.GRAY + "Needs a Scrap Magnet"); return; }
            if (p.isSneaking()) {
                if (now < prof.repelReady) { cooldown(p, "Repel pulse", prof.repelReady - now); return; }
                if (!vitals.afford(p, prof, GearVitals.COST_REPEL, "Repel pulse")) return;
                vitals.spend(p, prof, GearVitals.COST_REPEL);
                prof.repelReady = now + REPEL_MS;
                armReady(prof, "Repel pulse", prof.repelReady);
                repel(p);
            } else {
                if (!prof.magnet) {
                    if (!vitals.afford(p, prof, GearVitals.COST_MAGNET, "Scrap Magnet")) return;
                    vitals.spend(p, prof, GearVitals.COST_MAGNET);
                }
                prof.magnet = !prof.magnet;
                bar(p, ChatColor.GOLD + "Scrap Magnet: " + (prof.magnet ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, prof.magnet ? 1.4f : 0.8f);
            }
        }
    }

    static boolean hostile(Entity e) {
        return e instanceof Monster || e instanceof Slime || e instanceof Ghast || e instanceof Shulker;
    }

    private boolean arc(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        LivingEntity best = null;
        double bestDist = 17;
        for (Entity e : p.getNearbyEntities(16, 16, 16)) {
            if (!(e instanceof LivingEntity) || e.isDead() || !targetable(p, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector mid = le.getLocation().toVector().add(le.getEyeLocation().toVector()).multiply(0.5);
            Vector to = mid.subtract(eye.toVector());
            double dist = to.length();
            if (dist > 16 || dist < 0.1) continue;
            if (to.angle(dir) > Math.atan(1.2 / dist) + 0.03) continue;
            if (!p.hasLineOfSight(le)) continue;
            if (dist < bestDist) { bestDist = dist; best = le; }
        }
        if (best == null) return false;
        zap(p, eye, best, 4.0);
        arcs++;
        // Phase 2: the main bolt may lock the target up (short, shock: Lightning Resistance stops it).
        if (!best.isDead() && random.nextDouble() < ARC_PARALYSIS_CHANCE)
            plugin.vitals.apply(best, GearStatus.PARALYSIS, best instanceof Player ? 750L : 1500L, true);
        // Chain lightning: up to two more hostiles within 5 blocks of the first target.
        List<LivingEntity> hit = new ArrayList<LivingEntity>();
        hit.add(best);
        LivingEntity from = best;
        for (int jump = 0; jump < 2; jump++) {
            LivingEntity next = nearestOther(p, from, hit, 5.0);
            if (next == null) break;
            zap(p, from.getEyeLocation(), next, 2.0);
            hit.add(next);
            from = next;
            chains++;
        }
        return true;
    }

    static boolean targetable(Player p, Entity e) {
        return e != p && (hostile(e) || (e instanceof Player && p.getWorld().getPVP() && ((Player) e).getGameMode() != GameMode.SPECTATOR));
    }

    private LivingEntity nearestOther(Player p, LivingEntity from, List<LivingEntity> skip, double radius) {
        LivingEntity best = null;
        double bestD = radius * radius;
        for (Entity e : from.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity) || e.isDead() || skip.contains(e) || !hostile(e)) continue;
            double d = e.getLocation().distanceSquared(from.getLocation());
            if (d <= bestD && from.hasLineOfSight(e)) { bestD = d; best = (LivingEntity) e; }
        }
        return best;
    }

    private void zap(Player p, Location from, LivingEntity target, double damage) {
        Location to = target.getEyeLocation();
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len > 0.01) {
            step.normalize().multiply(0.45);
            Location point = from.clone();
            for (double t = 0; t < len; t += 0.45) {
                point.add(step);
                point.getWorld().spawnParticle(Particle.CRIT_MAGIC, point, 2, 0.04, 0.04, 0.04, 0);
            }
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_IMPACT, 0.4f, 1.9f);
        arcing = true;
        try { target.damage(damage, p); } finally { arcing = false; }
    }

    private boolean blink(Player p) {
        Location eye = p.getEyeLocation();
        Location found = null;
        BlockIterator it = new BlockIterator(p.getWorld(), eye.toVector(), eye.getDirection(), 0, 8);
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType().isSolid()) break;
            if (!b.isLiquid() && !b.getRelative(BlockFace.UP).getType().isSolid()) found = b.getLocation().add(0.5, 0, 0.5);
        }
        if (found == null || found.distanceSquared(p.getLocation()) < 4) return false;
        found.setYaw(p.getLocation().getYaw());
        found.setPitch(p.getLocation().getPitch());
        return phaseTo(p, found);
    }

    private boolean phaseTo(Player p, Location to) {
        Location from = p.getLocation();
        if (!p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
        p.setFallDistance(0f);
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 24, 0.3, 0.6, 0.3, 0.2);
        to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 24, 0.3, 0.6, 0.3, 0.2);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMEN_TELEPORT, 0.6f, 1.3f);
        blinks++;
        return true;
    }

    private boolean randomBlink(Player p) {
        Location base = p.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            Block b = base.getWorld().getBlockAt(base.getBlockX() + random.nextInt(11) - 5,
                base.getBlockY() + random.nextInt(5) - 2, base.getBlockZ() + random.nextInt(11) - 5);
            if (b.getType().isSolid() || b.isLiquid() || b.getRelative(BlockFace.UP).getType().isSolid()) continue;
            if (!b.getRelative(BlockFace.DOWN).getType().isSolid()) continue;
            Location to = b.getLocation().add(0.5, 0, 0.5);
            to.setYaw(base.getYaw());
            to.setPitch(base.getPitch());
            return phaseTo(p, to);
        }
        return false;
    }

    private void repel(Player p) {
        Location c = p.getLocation();
        int n = 0;
        for (Entity e : p.getNearbyEntities(5, 3, 5)) {
            if (e instanceof Projectile) {
                ProjectileSource shooter = ((Projectile) e).getShooter();
                if (shooter == p) continue;
                e.setVelocity(e.getVelocity().multiply(-0.8));
                n++;
            } else if (hostile(e)) {
                Vector away = e.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) away = new Vector(1, 0, 0);
                e.setVelocity(away.normalize().multiply(1.2).setY(0.35));
                n++;
            }
        }
        p.getWorld().playSound(c, Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.6f);
        p.getWorld().spawnParticle(Particle.CRIT, c.add(0, 1, 0), 30, 1.5, 0.5, 1.5, 0.1);
        bar(p, ChatColor.GOLD + "Repel pulse: " + ChatColor.WHITE + n + ChatColor.GRAY + " pushed back");
    }

    private void deflect(Projectile projectile, float pitch, Player p) {
        projectile.setVelocity(projectile.getVelocity().multiply(-0.25).setY(0.2));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, pitch);
        p.getWorld().spawnParticle(Particle.CRIT, projectile.getLocation(), 6, 0.1, 0.1, 0.1, 0.05);
    }

    // ================================================================ damage taken

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        GearProfile prof = plugin.profile(p);
        if (prof == null || !active(p)) return;
        prof.restTicks = 0;
        Set<GearItem> worn = prof.worn();
        if (worn.isEmpty()) return;
        long now = System.currentTimeMillis();
        EntityDamageEvent.DamageCause cause = e.getCause();
        Entity damager = e instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) e).getDamager() : null;
        switch (cause) {
            case FALL: {
                double m = 1.0;
                if (worn.contains(GearItem.GRAV_HARNESS)) m = 0.0;
                if (worn.contains(GearItem.GYRO_STABILIZER)) m = Math.min(m, 0.2);
                if (worn.contains(GearItem.SPRINT_BRACE)) m = Math.min(m, 0.5);
                Long grace = fallGrace.get(p.getUniqueId());
                if (grace != null && grace >= now) m = 0.0;
                if (m <= 0.0) { e.setCancelled(true); return; }
                if (m < 1.0) e.setDamage(e.getDamage() * m);
                break;
            }
            case FLY_INTO_WALL:
                if (worn.contains(GearItem.GYRO_STABILIZER)) { e.setCancelled(true); return; }
                break;
            case FIRE: case FIRE_TICK: case HOT_FLOOR:
                if (worn.contains(GearItem.THERMAL_GOGGLES)) { e.setCancelled(true); p.setFireTicks(0); return; }
                break;
            case LAVA:
                if (worn.contains(GearItem.THERMAL_GOGGLES)) e.setDamage(e.getDamage() * 0.5);
                break;
            case POISON:
                if (worn.contains(GearItem.TOXIN_INJECTOR) || (worn.contains(GearItem.REBREATHER) && touchingWater(p))) {
                    e.setCancelled(true); p.removePotionEffect(PotionEffectType.POISON); return;
                }
                break;
            case WITHER:
                if (worn.contains(GearItem.NECROTIC_RING)) { e.setCancelled(true); p.removePotionEffect(PotionEffectType.WITHER); return; }
                break;
            case DROWNING:
                if (worn.contains(GearItem.REBREATHER)) { e.setCancelled(true); p.setRemainingAir(p.getMaximumAir()); return; }
                break;
            case LIGHTNING:
                if (worn.contains(GearItem.CAPACITOR_BELT)) e.setDamage(e.getDamage() * 0.75);
                break;
            case BLOCK_EXPLOSION: case ENTITY_EXPLOSION:
                if (worn.contains(GearItem.RIOT_VEST)) e.setDamage(e.getDamage() * 0.7);
                break;
            case ENTITY_ATTACK: case ENTITY_SWEEP_ATTACK: case PROJECTILE:
                if (worn.contains(GearItem.PHASE_HEADSET) && !wet(p)) {
                    if (random.nextDouble() < 0.10) {
                        e.setCancelled(true);
                        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 16, 0.3, 0.6, 0.3, 0.2);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 0.4f, 1.8f);
                        return;
                    }
                    if (cause == EntityDamageEvent.DamageCause.PROJECTILE && now >= prof.phaseReady && random.nextDouble() < 0.25) {
                        prof.phaseReady = now + PHASE_BLINK_MS;
                        if (randomBlink(p)) { e.setCancelled(true); return; }
                    }
                }
                if (damager instanceof Arrow) {
                    if (worn.contains(GearItem.RIOT_VEST) && random.nextDouble() < 0.30) {
                        e.setCancelled(true); deflect((Arrow) damager, 1.1f, p); glances++;
                        bar(p, ChatColor.GOLD + "Arrow glanced off the Riot Vest"); return;
                    }
                    if (prof.magnet && worn.contains(GearItem.SCRAP_MAGNET) && random.nextDouble() < 0.30) {
                        e.setCancelled(true); deflect((Arrow) damager, 0.6f, p); glances++;
                        bar(p, ChatColor.GOLD + "The Scrap Magnet pulled the arrow wide"); return;
                    }
                }
                if (worn.contains(GearItem.RIOT_VEST)) {
                    prof.vestHitAt = now;
                    double dmg = e.getDamage(), soak = Math.min(prof.vestCharge, dmg);
                    if (soak > 0) {
                        prof.vestCharge -= soak;
                        absorbed++;
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.9f);
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.1, 0), 8, 0.3, 0.3, 0.3, 0.05);
                        bar(p, ChatColor.GOLD + "Riot Vest " + pips(prof.vestCharge));
                        if (soak >= dmg - 1e-9) { e.setCancelled(true); retaliate(p, worn, damager); return; }
                        e.setDamage(dmg - soak);
                    }
                }
                retaliate(p, worn, damager);
                break;
            default:
                break;
        }
        if (worn.contains(GearItem.TEDDY_BEAR)) fatal(e, p, prof, now);
    }

    /** Toxin Injector / Necrotic Ring: melee attackers get a dose back. */
    private void retaliate(Player p, Set<GearItem> worn, Entity damager) {
        if (!(damager instanceof LivingEntity) || damager == p) return;
        LivingEntity attacker = (LivingEntity) damager;
        if (worn.contains(GearItem.TOXIN_INJECTOR) && random.nextDouble() < 0.25)
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0), true);
        if (worn.contains(GearItem.NECROTIC_RING) && random.nextDouble() < 0.20)
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0), true);
    }

    /** Checked after this plugin's own reductions and before JasprRevive (HIGHEST). */
    private void fatal(EntityDamageEvent e, Player p, GearProfile prof, long now) {
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (p.getHealth() - e.getFinalDamage() > 0 || now < prof.lastStandReady) return;
        prof.lastStandReady = now + LAST_STAND_MS;
        e.setCancelled(true);
        p.setHealth(Math.min(p.getMaxHealth(), 6.0));
        p.setFireTicks(0);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.3);
        bar(p, ChatColor.GOLD + "Last Stand! " + ChatColor.GRAY + "Your old bear pulled you through (20m)");
        lastStands++;
        plugin.saveLater(prof);
        plugin.getLogger().info("GEAR_LAST_STAND player=" + p.getUniqueId());
    }

    // ================================================================ damage dealt

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (arcing || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof LivingEntity)) return;
        final Player p = (Player) e.getDamager();
        final LivingEntity target = (LivingEntity) e.getEntity();
        GearProfile prof = plugin.profile(p);
        if (prof == null || !active(p)) return;
        Set<GearItem> worn = prof.worn();
        if (worn.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (worn.contains(GearItem.TOXIN_INJECTOR)) {
            if (target.hasPotionEffect(PotionEffectType.POISON)) e.setDamage(e.getDamage() + 2.0);
            if (random.nextDouble() < 0.20) target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0), true);
        }
        if (worn.contains(GearItem.NECROTIC_RING)) {
            if (target.hasPotionEffect(PotionEffectType.WITHER) && now >= prof.leechReady) {
                prof.leechReady = now + LEECH_MS;
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 1.0));
                p.getWorld().spawnParticle(Particle.SPELL_MOB, p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
            }
            if (random.nextDouble() < 0.15) target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0), true);
        }
        // Bleeding is a Phase 2 status now (GearVitals): same 1 damage/s for 4 s, also shown to players.
        if (worn.contains(GearItem.RAZOR_CLAWS) && random.nextDouble() < 0.20 && plugin.vitals.apply(target, GearStatus.BLEED, 4000L, false)) {
            bleeds++;
        }
        if (worn.contains(GearItem.CAPACITOR_BELT) && random.nextDouble() < 0.12) {
            // Static discharge: +2 on this hit and a spark jumps to one more hostile next tick.
            e.setDamage(e.getDamage() + 2.0);
            discharges++;
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.1);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || target.isDead()) return;
                List<LivingEntity> skip = new ArrayList<LivingEntity>();
                skip.add(target);
                LivingEntity next = nearestOther(p, target, skip, 5.0);
                if (next != null) { zap(p, target.getEyeLocation(), next, 2.0); chains++; }
            });
        }
    }

    // ================================================================ misc events

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        if (e.getAmount() <= 0) return;
        GearProfile prof = plugin.profile(e.getPlayer());
        if (prof == null || !prof.worn().contains(GearItem.FIELD_JOURNAL)) return;
        double v = e.getAmount() * 1.2 + prof.xpRemainder;
        int out = (int) Math.floor(v);
        prof.xpRemainder = Math.min(1.0, v - out);
        e.setAmount(out);
    }

    @EventHandler
    public void onWake(PlayerBedLeaveEvent e) {
        Player p = e.getPlayer();
        GearProfile prof = plugin.profile(p);
        if (prof == null || !prof.worn().contains(GearItem.TEDDY_BEAR)) return;
        long time = p.getWorld().getTime();
        if (p.getSleepTicks() < 100 && time > 2000 && time < 23000) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 0, true, false), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, true, false), true);
        plugin.vitals.apply(p, GearStatus.INVIGORATED, 120_000L, false); // Phase 2: wake up Invigorated (2 min)
        bar(p, ChatColor.GOLD + "Well Rested" + ChatColor.GRAY + " - hugged the bear all night");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player)) return;
        Player p = (Player) e.getTarget();
        GearProfile prof = plugin.profile(p);
        if (prof == null) return;
        Set<GearItem> worn = prof.worn();
        // Phase Headset: endermen tolerate your gaze unless you hit them first.
        if (e.getEntity() instanceof Enderman && worn.contains(GearItem.PHASE_HEADSET)
            && e.getReason() != EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY) { e.setCancelled(true); return; }
        if (!hostile(e.getEntity()) || !worn.contains(GearItem.TRITIUM_RING)) return;
        long now = System.currentTimeMillis();
        if (now < prof.threatReady) return;
        prof.threatReady = now + THREAT_MS;
        Location at = e.getEntity().getLocation();
        bar(p, ChatColor.RED + "Threat: " + ChatColor.WHITE + name(e.getEntity()) + ChatColor.GRAY + " "
            + Math.round(at.distance(p.getLocation())) + "m " + direction(p, at));
    }

    // ================================================================ helpers

    static String name(Entity e) {
        return e.getCustomName() != null ? ChatColor.stripColor(e.getCustomName()) : pretty(e.getType().name());
    }

    static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String part : enumName.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    static boolean climbable(Material m) { return m == Material.LADDER || m == Material.VINE; }

    static boolean headInWater(Player p) {
        Material m = p.getEyeLocation().getBlock().getType();
        return m == Material.WATER || m == Material.STATIONARY_WATER;
    }

    static boolean touchingWater(Player p) {
        Material feet = p.getLocation().getBlock().getType();
        return feet == Material.WATER || feet == Material.STATIONARY_WATER || headInWater(p);
    }

    static boolean wet(Player p) {
        if (touchingWater(p)) return true;
        World w = p.getWorld();
        if (!w.hasStorm()) return false;
        Location l = p.getLocation();
        return l.getBlockY() >= w.getHighestBlockYAt(l) - 1;
    }

    static void bar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    static void cooldown(Player p, String what, long ms) {
        bar(p, ChatColor.GRAY + what + " recharging (" + ((ms + 999) / 1000) + "s)");
    }

    void forget(UUID uuid) {
        fallGrace.remove(uuid);
    }

    String metrics() {
        return "arcs=" + arcs + " chains=" + chains + " discharges=" + discharges + " dodges=" + dodges + " blinks=" + blinks
            + " absorbed=" + absorbed + " glances=" + glances + " lastStands=" + lastStands + " bleeds=" + bleeds
            + " climbs=" + climbs + " brakes=" + brakes + " rests=" + rests + " bleeding=" + plugin.vitals.trackedMobs();
    }
}
