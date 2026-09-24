package chat.jaspr.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Stray;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Survivor Gear Phase 2: the Adrenaline resource (the mod's mana), the five custom status effects
 * and the consumables. Everything is server-side and real (movement/attack interception, damage
 * over time, damage scaling, attribute modifiers with a fixed UUID); nothing here is a vanilla
 * potion, glowing or night vision. Browser clients that said "hello 2" get a small HUD packet
 * ({"t":"hud"}) whenever the numbers they show change; everyone else gets action-bar notices
 * and /gear vitals.
 */
final class GearVitals implements Listener {
    static final int BASE_MAX = 100, CRYSTAL_BONUS = 10, MAX_CRYSTALS = 10;
    static final double REGEN_PER_SECOND = 2.0, RUSH_MAX = 5.0;
    static final int COST_ARC = 25, COST_DODGE = 15, COST_BLINK = 30, COST_CHEST = 10, COST_REPEL = 20, COST_MAGNET = 5;
    static final long PARALYSIS_IMMUNE_MS = 3000, USE_COOLDOWN_MS = 400, SAVE_EVERY_MS = 60_000;
    static final int MOB_CAP = 256;

    private static final AttributeModifier VIGOR_SPEED = new AttributeModifier(
        UUID.nameUUIDFromBytes("jaspr-gear:status:vigor:GENERIC_MOVEMENT_SPEED".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        "jaspr_gear_invigorated", 0.10, AttributeModifier.Operation.ADD_SCALAR);

    private final GearPlugin plugin;
    private final Random random = new Random();
    /** Harmful statuses on non-player entities (players keep theirs in GearProfile). */
    private final Map<UUID, Map<GearStatus, Long>> mobs = new HashMap<UUID, Map<GearStatus, Long>>();
    /** Paralysed mobs are held at the spot where the shock caught them. */
    private final Map<UUID, Location> anchors = new HashMap<UUID, Location>();
    private final Map<UUID, Long> mobImmune = new HashMap<UUID, Long>();
    int spent, applied, refused, cured, bleedTicks, lockedHits, lockedMoves, used, crystalsUsed, hudSent;

    GearVitals(GearPlugin plugin) { this.plugin = plugin; }

    // ================================================================ adrenaline

    static boolean free(Player p) { return p.getGameMode() == GameMode.CREATIVE; }

    static double regenPerSecond(Player p, GearProfile prof, long now) {
        if (prof.mutation == GearMutation.SPRITE && p != null && p.isFlying() && GearMutations.survival(p)) return 0.0; // flight is paid for
        double r = REGEN_PER_SECOND;
        if (prof.has(GearStatus.INVIGORATED, now)) r *= 2.0;
        if (p.getFoodLevel() <= 6) r *= 0.5; // running on empty
        return r;
    }

    /** True when the ability may fire: not paralysed and enough adrenaline (Creative is free). */
    boolean afford(Player p, GearProfile prof, int cost, String what) {
        long now = System.currentTimeMillis();
        if (prof.has(GearStatus.PARALYSIS, now)) { GearAbilities.bar(p, ChatColor.LIGHT_PURPLE + "Paralysed" + ChatColor.GRAY + " - you can't move a muscle"); return false; }
        if (free(p) || prof.adrenaline >= cost) return true;
        GearAbilities.bar(p, ChatColor.GOLD + what + ChatColor.GRAY + " needs " + ChatColor.WHITE + cost + " adrenaline"
            + ChatColor.GRAY + " (you have " + (int) Math.floor(Math.max(0, prof.adrenaline)) + ")");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BASS, 0.5f, 0.7f);
        return false;
    }

    void spend(Player p, GearProfile prof, int cost) {
        if (free(p) || cost <= 0) return;
        prof.adrenaline = Math.max(0.0, prof.adrenaline - cost);
        prof.vitalsDirty = true;
        spent += cost;
        pushHud(p, prof);
    }

    /** Adds (or removes) adrenaline within 0..max; returns the change actually applied. */
    static double gain(GearProfile prof, double amount) {
        double before = Math.max(0.0, prof.adrenaline);
        prof.adrenaline = Math.max(0.0, Math.min(prof.maxAdrenaline(), before + amount));
        if (prof.adrenaline != before) prof.vitalsDirty = true;
        return prof.adrenaline - before;
    }

    // ================================================================ statuses

    boolean has(LivingEntity e, GearStatus s) {
        if (e == null) return false;
        long now = System.currentTimeMillis();
        if (e instanceof Player) {
            GearProfile prof = plugin.existing((Player) e);
            return prof != null && prof.has(s, now);
        }
        Map<GearStatus, Long> map = mobs.get(e.getUniqueId());
        Long until = map == null ? null : map.get(s);
        return until != null && until > now;
    }

    /**
     * Applies or extends a status (the longer remaining time wins, capped at the status maximum).
     * electric marks shocks, which Lightning Resistance stops from paralysing. Mobs only take
     * the harmful statuses. Returns false when refused (immunity, cap, Creative, dead).
     */
    boolean apply(LivingEntity target, GearStatus s, long ms, boolean electric) {
        if (target == null || target.isDead() || !target.isValid() || ms <= 0) return false;
        long now = System.currentTimeMillis();
        ms = Math.min(ms, s.maxMs);
        if (target instanceof Player) {
            Player p = (Player) target;
            if (!GearAbilities.active(p) || (s.harmful && free(p))) return false;
            GearProfile prof = plugin.profile(p);
            if (s == GearStatus.PARALYSIS) {
                if (electric && prof.has(GearStatus.LIGHTNING_RESISTANCE, now)) {
                    GearAbilities.bar(p, ChatColor.YELLOW + "Lightning Resistance" + ChatColor.GRAY + " - the shock grounded out");
                    refused++;
                    return false;
                }
                if (now < prof.paraImmuneUntil || prof.has(GearStatus.PARALYSIS, now)) { refused++; return false; }
                prof.paraImmuneUntil = now + ms + PARALYSIS_IMMUNE_MS;
                p.setSprinting(false);
                p.setVelocity(new Vector(0, Math.min(0, p.getVelocity().getY()), 0));
            }
            Long old = prof.status.get(s);
            boolean fresh = old == null || old <= now;
            prof.status.put(s, Math.max(fresh ? 0L : old, now + ms));
            prof.vitalsDirty = true;
            if (fresh) notice(p, s, true, ms);
            if (s == GearStatus.INVIGORATED) speed(p, prof, now);
            if (s == GearStatus.ICE_RESISTANCE) stripCold(p);
            applied++;
            pushHud(p, prof);
            return true;
        }
        if (!s.harmful) return false;
        UUID id = target.getUniqueId();
        Map<GearStatus, Long> map = mobs.get(id);
        if (map == null) {
            if (mobs.size() >= MOB_CAP) { refused++; return false; }
            mobs.put(id, map = new EnumMap<GearStatus, Long>(GearStatus.class));
        }
        if (s == GearStatus.PARALYSIS) {
            Long immune = mobImmune.get(id);
            Long current = map.get(s);
            if ((immune != null && now < immune) || (current != null && current > now)) { refused++; return false; }
            mobImmune.put(id, now + ms + PARALYSIS_IMMUNE_MS);
            anchors.put(id, target.getLocation());
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getEyeLocation(), 8, 0.2, 0.3, 0.2, 0.02);
        }
        Long old = map.get(s);
        map.put(s, Math.max(old == null ? 0L : old, now + ms));
        applied++;
        return true;
    }

    /** Removes a status; players get a notice with the reason. Returns true when one was active. */
    boolean cure(LivingEntity target, GearStatus s, String why) {
        if (target == null) return false;
        long now = System.currentTimeMillis();
        if (target instanceof Player) {
            Player p = (Player) target;
            GearProfile prof = plugin.existing(p);
            if (prof == null) return false;
            Long until = prof.status.remove(s);
            if (until == null) return false;
            prof.vitalsDirty = true;
            if (until > now) {
                cured++;
                if (why != null) GearAbilities.bar(p, ChatColor.GREEN + s.title + " ended" + ChatColor.GRAY + " - " + why);
                if (s == GearStatus.INVIGORATED) speed(p, prof, now);
                pushHud(p, prof);
                return true;
            }
            return false;
        }
        Map<GearStatus, Long> map = mobs.get(target.getUniqueId());
        Long until = map == null ? null : map.remove(s);
        if (s == GearStatus.PARALYSIS) anchors.remove(target.getUniqueId());
        if (map != null && map.isEmpty()) mobs.remove(target.getUniqueId());
        if (until != null && until > now) { cured++; return true; }
        return false;
    }

    private void notice(Player p, GearStatus s, boolean on, long ms) {
        String c = "§" + s.color;
        if (s == GearStatus.BLEED) GearAbilities.bar(p, c + "Bleeding!" + ChatColor.GRAY + " - a bandage or regeneration stops it");
        else if (s == GearStatus.PARALYSIS) GearAbilities.bar(p, c + "Paralysed!" + ChatColor.GRAY + " - your muscles lock up");
        else GearAbilities.bar(p, c + s.title + ChatColor.GRAY + (on ? " (" + ((ms + 999) / 1000) + "s)" : " wore off"));
    }

    private static void stripCold(Player p) {
        if (p.hasPotionEffect(PotionEffectType.SLOW)) p.removePotionEffect(PotionEffectType.SLOW);
    }

    void speed(Player p, GearProfile prof, long now) {
        GearAbilities.setModifier(p, Attribute.GENERIC_MOVEMENT_SPEED, VIGOR_SPEED,
            GearAbilities.active(p) && prof.has(GearStatus.INVIGORATED, now));
    }

    /** Quit / disable / death: remove the Invigorated modifier exactly. */
    void clear(Player p) {
        GearAbilities.setModifier(p, Attribute.GENERIC_MOVEMENT_SPEED, VIGOR_SPEED, false);
    }

    /** Offline time does not count: remaining time is parked on quit and resumed on join. */
    void suspend(GearProfile prof, long now) {
        prof.parked.clear();
        for (Map.Entry<GearStatus, Long> e : prof.status.entrySet()) if (e.getValue() > now) prof.parked.put(e.getKey(), e.getValue() - now);
        prof.status.clear();
    }

    void resume(GearProfile prof, long now) {
        for (Map.Entry<GearStatus, Long> e : prof.parked.entrySet()) prof.status.put(e.getKey(), now + e.getValue());
        prof.parked.clear();
        prof.hudSig = null;
    }

    // ================================================================ ticks

    /** Every 2 ticks per online player: Ice Resistance keeps slowness (JasprRPG Frost, strays) off. */
    void fast(Player p, GearProfile prof, long now) {
        if (!prof.status.isEmpty() && prof.has(GearStatus.ICE_RESISTANCE, now)) stripCold(p);
    }

    /** Once a second per online player: expiry, bleeding, regeneration, HUD sync, periodic save. */
    void second(Player p, GearProfile prof, long now) {
        if (prof.adrenaline < 0) return;
        boolean live = GearAbilities.active(p);
        if (!prof.status.isEmpty()) {
            for (Iterator<Map.Entry<GearStatus, Long>> it = prof.status.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<GearStatus, Long> e = it.next();
                if (e.getValue() > now) continue;
                GearStatus ended = e.getKey(); // EnumMap entries are unusable after remove()
                it.remove();
                prof.vitalsDirty = true;
                if (live) {
                    if (ended == GearStatus.BLEED) GearAbilities.bar(p, ChatColor.GREEN + "The bleeding stopped");
                    else if (ended != GearStatus.PARALYSIS) notice(p, ended, false, 0);
                }
            }
            if (live && prof.has(GearStatus.BLEED, now)) {
                if (p.hasPotionEffect(PotionEffectType.REGENERATION)) cure(p, GearStatus.BLEED, "regeneration closed the wound");
                else bleed(p);
            }
        }
        if (live) gain(prof, regenPerSecond(p, prof, now));
        speed(p, prof, now);
        pushHud(p, prof);
        if (prof.vitalsDirty && now - prof.vitalsNotedAt >= SAVE_EVERY_MS) {
            prof.vitalsNotedAt = now;
            plugin.store.saveVitalsAsync(prof);
        }
    }

    private void bleed(LivingEntity victim) {
        bleedTicks++;
        victim.getWorld().spawnParticle(Particle.REDSTONE, victim.getLocation().add(0, 1, 0), 6, 0.25, 0.4, 0.25, 0);
        victim.damage(1.0);
    }

    /** Once a second: mob bleeding and expiry (bounded map). */
    void mobSecond(long now) {
        if (!mobImmune.isEmpty()) mobImmune.values().removeIf(until -> until <= now);
        if (mobs.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Map<GearStatus, Long>>> it = mobs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<GearStatus, Long>> e = it.next();
            Entity entity = plugin.getServer().getEntity(e.getKey());
            Map<GearStatus, Long> map = e.getValue();
            map.values().removeIf(until -> until <= now);
            if (!(entity instanceof LivingEntity) || entity.isDead() || map.isEmpty()) {
                it.remove();
                anchors.remove(e.getKey());
                continue;
            }
            if (!map.containsKey(GearStatus.PARALYSIS)) anchors.remove(e.getKey());
            LivingEntity victim = (LivingEntity) entity;
            if (map.containsKey(GearStatus.BLEED)) {
                if (victim.hasPotionEffect(PotionEffectType.REGENERATION)) map.remove(GearStatus.BLEED);
                else bleed(victim);
            }
        }
    }

    /** Every tick while any mob is paralysed: hold it at its anchor (look direction stays free). */
    void tick(long now) {
        if (anchors.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Location>> it = anchors.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Location> e = it.next();
            Entity entity = plugin.getServer().getEntity(e.getKey());
            Map<GearStatus, Long> map = mobs.get(e.getKey());
            Long until = map == null ? null : map.get(GearStatus.PARALYSIS);
            if (!(entity instanceof LivingEntity) || entity.isDead() || until == null || until <= now) { it.remove(); continue; }
            Location anchor = e.getValue(), at = entity.getLocation();
            if (!anchor.getWorld().equals(at.getWorld())) { it.remove(); continue; }
            entity.setVelocity(new Vector(0, Math.min(0, entity.getVelocity().getY()), 0));
            double dx = at.getX() - anchor.getX(), dz = at.getZ() - anchor.getZ(), dy = at.getY() - anchor.getY();
            if (dx * dx + dz * dz > 0.0025 || dy > 0.05) {
                Location back = anchor.clone();
                back.setYaw(at.getYaw());
                back.setPitch(at.getPitch());
                if (dy < 0) back.setY(at.getY()); // it may still fall
                entity.teleport(back);
                if (dy < 0) e.setValue(back.clone());
            }
        }
    }

    // ================================================================ consumables

    /** Applies one dose. Returns true when it was used (the caller removes the dose). */
    boolean use(Player p, GearProfile prof, GearConsumable item) {
        long now = System.currentTimeMillis();
        if (prof.adrenaline < 0) return false;
        int max = prof.maxAdrenaline();
        Location at = p.getLocation();
        if (item.mutation != null) { // Phase 3 serums: mutagens and the Purge Serum
            if (!plugin.mutations.inject(p, prof, item.mutation)) return false;
            used++;
            pushHud(p, prof);
            return true;
        }
        switch (item) {
            case ADRENALINE_CANDY: {
                if (prof.adrenaline >= max - 0.5) { GearAbilities.bar(p, ChatColor.GRAY + "Adrenaline is already full"); return false; }
                double got = gain(prof, 20);
                p.getWorld().playSound(at, Sound.ENTITY_GENERIC_EAT, 0.7f, 1.4f);
                GearAbilities.bar(p, ChatColor.GOLD + "Adrenaline Candy: " + ChatColor.WHITE + "+" + Math.round(got) + " adrenaline");
                break;
            }
            case FIELD_BANDAGE: {
                boolean bleeding = prof.has(GearStatus.BLEED, now);
                if (!bleeding && p.getHealth() >= p.getMaxHealth()) { GearAbilities.bar(p, ChatColor.GRAY + "No wounds to dress"); return false; }
                if (bleeding) cure(p, GearStatus.BLEED, null);
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 2.0));
                p.getWorld().playSound(at, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.3f);
                GearAbilities.bar(p, ChatColor.GREEN + "Field Bandage" + ChatColor.GRAY + (bleeding ? " - bleeding stopped, +1 heart" : " - +1 heart"));
                break;
            }
            case STIM_REAGENT: {
                double got = gain(prof, 50);
                apply(p, GearStatus.INVIGORATED, 45_000L, false);
                p.getWorld().playSound(at, Sound.BLOCK_NOTE_HAT, 0.6f, 2.0f);
                p.getWorld().playSound(at, Sound.ENTITY_PLAYER_BREATH, 0.8f, 1.2f);
                GearAbilities.bar(p, ChatColor.GREEN + "Stim Reagent: " + ChatColor.WHITE + "+" + Math.round(got) + " adrenaline" + ChatColor.GRAY + ", Invigorated 45s");
                break;
            }
            case FULL_RESTORE: {
                gain(prof, max);
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6.0));
                cure(p, GearStatus.BLEED, null);
                cure(p, GearStatus.PARALYSIS, null);
                apply(p, GearStatus.ICE_RESISTANCE, 90_000L, false);
                apply(p, GearStatus.LIGHTNING_RESISTANCE, 90_000L, false);
                p.getWorld().playSound(at, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.1f);
                GearAbilities.bar(p, ChatColor.AQUA + "Full Restore" + ChatColor.GRAY + " - topped up, patched up, insulated (90s)");
                break;
            }
            case ADRENALINE_CRYSTAL: {
                if (prof.crystals >= MAX_CRYSTALS) { GearAbilities.bar(p, ChatColor.GRAY + "Your body can't take another crystal (max " + (BASE_MAX + CRYSTAL_BONUS * MAX_CRYSTALS) + ")"); return false; }
                prof.crystals++;
                gain(prof, CRYSTAL_BONUS);
                prof.vitalsDirty = true;
                crystalsUsed++;
                plugin.store.saveVitalsAsync(prof);
                p.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
                p.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
                GearAbilities.bar(p, ChatColor.LIGHT_PURPLE + "Adrenaline Crystal: " + ChatColor.WHITE + "max adrenaline " + prof.maxAdrenaline());
                plugin.getLogger().info("GEAR_CRYSTAL player=" + p.getUniqueId() + " crystals=" + prof.crystals);
                break;
            }
            default:
                return false;
        }
        used++;
        pushHud(p, prof);
        return true;
    }

    // ================================================================ HUD

    static String hudJson(Player p, GearProfile prof, long now) {
        JsonObject root = new JsonObject();
        root.addProperty("v", GearPlugin.PROTOCOL);
        root.addProperty("t", "hud");
        root.addProperty("on", p == null || (p.getGameMode() != GameMode.SPECTATOR && !p.isDead()));
        root.addProperty("a", (int) Math.floor(Math.max(0, prof.adrenaline) + 1e-6));
        root.addProperty("m", prof.maxAdrenaline());
        if (prof.mutation != GearMutation.BASELINE) root.addProperty("mu", prof.mutation.title);
        JsonArray fx = new JsonArray();
        for (Map.Entry<GearStatus, Long> e : prof.status.entrySet()) {
            long left = e.getValue() - now;
            if (left <= 0) continue;
            JsonArray one = new JsonArray();
            one.add(e.getKey().id);
            one.add((int) ((left + 999) / 1000));
            fx.add(one);
        }
        root.add("fx", fx);
        return root.toString();
    }

    void pushHud(Player p, GearProfile prof) {
        if (!prof.capable || prof.protocol < 2 || prof.adrenaline < 0 || !p.isOnline()) return;
        String json = hudJson(p, prof, System.currentTimeMillis());
        if (json.equals(prof.hudSig)) return;
        prof.hudSig = json;
        if (plugin.sendRaw(p, json)) hudSent++;
    }

    String describe(Player p, GearProfile prof) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append(ChatColor.GOLD).append("Adrenaline: ").append(ChatColor.WHITE).append((int) Math.floor(Math.max(0, prof.adrenaline)))
           .append('/').append(prof.maxAdrenaline()).append(ChatColor.GRAY).append(" (+")
           .append(String.format(java.util.Locale.ROOT, "%.1f", regenPerSecond(p, prof, now))).append("/s")
           .append(prof.crystals > 0 ? ", " + prof.crystals + " crystal" + (prof.crystals == 1 ? "" : "s") : "").append(')');
        boolean any = false;
        for (Map.Entry<GearStatus, Long> e : prof.status.entrySet()) {
            long left = e.getValue() - now;
            if (left <= 0) continue;
            out.append(any ? ChatColor.GRAY + ", " : ChatColor.GRAY + " | ").append('§').append(e.getKey().color)
               .append(e.getKey().title).append(ChatColor.GRAY).append(' ').append((left + 999) / 1000).append('s');
            any = true;
        }
        return out.toString();
    }

    // ================================================================ events

    /** Paralysis: position is locked (falling still works, looking around too). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        GearProfile prof = plugin.existing(e.getPlayer());
        if (prof == null || prof.status.isEmpty() || !prof.has(GearStatus.PARALYSIS, System.currentTimeMillis())) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())) return;
        boolean moved = Math.abs(to.getX() - from.getX()) > 1e-4 || Math.abs(to.getZ() - from.getZ()) > 1e-4 || to.getY() > from.getY() + 1e-4;
        if (!moved) return;
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        if (to.getY() < from.getY()) back.setY(to.getY());
        e.setTo(back);
        lockedMoves++;
    }

    /** Paralysis: no melee from a paralysed attacker (runs before every gear on-hit effect). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof LivingEntity && has((LivingEntity) e.getDamager(), GearStatus.PARALYSIS)) {
            e.setCancelled(true);
            lockedHits++;
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (has(e.getEntity(), GearStatus.PARALYSIS)) { e.setCancelled(true); lockedHits++; }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        ProjectileSource shooter = e.getEntity().getShooter();
        if (shooter instanceof LivingEntity && has((LivingEntity) shooter, GearStatus.PARALYSIS)) { e.setCancelled(true); lockedHits++; }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent e) {
        if (e.getEntity() instanceof Creeper && has((Creeper) e.getEntity(), GearStatus.PARALYSIS)) { e.setCancelled(true); lockedHits++; }
    }

    /**
     * Lightning paralyses (2s) unless the victim has Lightning Resistance, which instead cuts the
     * damage by 80% and puts the fire out. Ice Resistance halves stray arrows. NORMAL: before the
     * gear reductions and Last Stand (HIGH) and JasprRevive (HIGHEST) see the final damage.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        final LivingEntity victim = (LivingEntity) e.getEntity();
        if (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            if (has(victim, GearStatus.LIGHTNING_RESISTANCE)) {
                e.setDamage(e.getDamage() * 0.2);
                victim.setFireTicks(0);
                plugin.getServer().getScheduler().runTask(plugin, () -> { if (!victim.isDead()) victim.setFireTicks(0); });
            } else {
                apply(victim, GearStatus.PARALYSIS, 2000L, true);
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent && victim instanceof Player && has(victim, GearStatus.ICE_RESISTANCE)) {
            Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            if (damager instanceof Arrow && ((Projectile) damager).getShooter() instanceof Stray) e.setDamage(e.getDamage() * 0.5);
        }
    }

    /** Adrenaline rush: real hits taken add adrenaline (1 per damage point, at most 5); hostile melee may open a wound. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurtFinal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        GearProfile prof = plugin.existing(p);
        if (prof == null || prof.adrenaline < 0 || !GearAbilities.active(p)) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.CUSTOM && cause != EntityDamageEvent.DamageCause.STARVATION
            && cause != EntityDamageEvent.DamageCause.SUICIDE && e.getFinalDamage() > 0) {
            if (gain(prof, Math.min(RUSH_MAX, e.getFinalDamage())) > 0) pushHud(p, prof);
        }
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK && e instanceof EntityDamageByEntityEvent
            && GearAbilities.hostile(((EntityDamageByEntityEvent) e).getDamager())) {
            double chance = Math.max(0.0, Math.min(0.25, plugin.getConfig().getDouble("status.mob-bleed-chance", 0.04)));
            if (random.nextDouble() < chance) apply(p, GearStatus.BLEED, 5000L, false);
        }
    }

    /** Regeneration (potion, golden apple, beacon) closes a wound. Natural healing does not. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r != EntityRegainHealthEvent.RegainReason.MAGIC_REGEN && r != EntityRegainHealthEvent.RegainReason.MAGIC) return;
        if (e.getEntity() instanceof LivingEntity && has((LivingEntity) e.getEntity(), GearStatus.BLEED))
            cure((LivingEntity) e.getEntity(), GearStatus.BLEED, "regeneration closed the wound");
    }

    /** A hot stew warms you through: Ice Resistance for 60s. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem() == null) return;
        switch (e.getItem().getType()) {
            case MUSHROOM_SOUP: case RABBIT_STEW: case BEETROOT_SOUP:
                apply(e.getPlayer(), GearStatus.ICE_RESISTANCE, 60_000L, false);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        GearProfile prof = plugin.existing(e.getEntity());
        if (prof == null) return;
        if (!prof.status.isEmpty()) { prof.status.clear(); prof.vitalsDirty = true; }
        clear(e.getEntity());
        pushHud(e.getEntity(), prof);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        UUID id = e.getEntity().getUniqueId();
        mobs.remove(id);
        anchors.remove(id);
        mobImmune.remove(id);
    }

    int trackedMobs() { return mobs.size(); }

    String metrics() {
        return "adrenalineSpent=" + spent + " statusApplied=" + applied + " statusRefused=" + refused + " statusCured=" + cured
            + " bleedTicks=" + bleedTicks + " lockedHits=" + lockedHits + " lockedMoves=" + lockedMoves + " consumablesUsed=" + used
            + " crystalsUsed=" + crystalsUsed + " hudSent=" + hudSent + " statusMobs=" + mobs.size();
    }
}
