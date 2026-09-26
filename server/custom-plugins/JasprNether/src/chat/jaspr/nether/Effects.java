package chat.jaspr.nether;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * NetherEx mob effects, emulated server-side (the vanilla client has no such potions): Frozen, Frostbitten, Infested,
 * Fire Burning, Soul Sucked and Crying. Durations are in ticks; state is bounded and cleared on death/quit. Frozen mobs
 * carry the scoreboard tag "jn_frozen" so a restart can never leave them without AI. No glowing is ever used.
 */
final class Effects implements Listener {
    enum Kind { FROZEN, FROSTBITTEN, INFESTED, FIRE_BURNING, SOUL_SUCKED, CRYING }

    static final String FROZEN_TAG = "jn_frozen";
    private static final int MAX_TRACKED = 600;
    private final NetherPlugin plugin;
    private final Map<UUID, EnumMap<Kind, Long>> active = new HashMap<>();
    private final Map<UUID, LivingEntity> refs = new HashMap<>();
    private final Random random = new Random();
    private long now;
    int applied;

    Effects(NetherPlugin plugin) { this.plugin = plugin; }

    int active() { return active.size(); }

    boolean has(Entity e, Kind k) {
        EnumMap<Kind, Long> m = active.get(e.getUniqueId());
        return m != null && m.containsKey(k) && m.get(k) > now;
    }

    /** Frozen is refused for creative/spectator players, Arctic Abyss players (NetherEx default) and blacklisted mobs. */
    boolean canFreeze(LivingEntity e) {
        if (e instanceof Player) {
            Player p = (Player) e;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            return !(plugin.isNether(p.getWorld()) && plugin.gen != null
                && plugin.gen.biomes.nex(p.getLocation().getBlockX(), p.getLocation().getBlockZ()) == Biomes.Nex.ARCTIC_ABYSS);
        }
        switch (e.getType()) {
            case BLAZE: case GHAST: case WITHER_SKELETON: case POLAR_BEAR: return plugin.mobs.kind(e) != null && !plugin.mobs.freezeImmune(e);
            default: return !plugin.mobs.freezeImmune(e);
        }
    }

    void apply(LivingEntity e, Kind k, int ticks) {
        if (e == null || e.isDead() || ticks <= 0) return;
        if (k == Kind.FROZEN && !canFreeze(e)) return;
        if (k == Kind.INFESTED && plugin.mobs.infestImmune(e)) return;
        if (active.size() >= MAX_TRACKED && !active.containsKey(e.getUniqueId())) return;
        EnumMap<Kind, Long> m = active.computeIfAbsent(e.getUniqueId(), u -> new EnumMap<>(Kind.class));
        long until = now + ticks;
        Long old = m.get(k);
        if (old != null && old >= until) return;
        m.put(k, until);
        refs.put(e.getUniqueId(), e);
        applied++;
        switch (k) {
            case FROZEN:
                if (e instanceof Player) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 6, true, false), true);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 250, true, false), true);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, ticks, 4, true, false), true);
                } else {
                    e.setAI(false);
                    e.setSilent(true);
                    e.addScoreboardTag(FROZEN_TAG);
                }
                e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.8f, 0.6f);
                break;
            case SOUL_SUCKED:
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 2, true, false), true);
                break;
            default:
        }
        if (e instanceof Player) bar((Player) e, ChatColor.AQUA + pretty(k) + ChatColor.GRAY + " (" + (ticks / 20) + "s)");
    }

    static String pretty(Kind k) {
        switch (k) {
            case FROZEN: return "Frozen";
            case FROSTBITTEN: return "Frostbitten - no healing";
            case INFESTED: return "Infested";
            case FIRE_BURNING: return "Blue fire";
            case SOUL_SUCKED: return "Soul Sucked";
            default: return "Crying - ghastlings are coming";
        }
    }

    static void bar(Player p, String msg) {
        try { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg)); } catch (Throwable ignored) { }
    }

    private void end(UUID id, Kind k) {
        LivingEntity e = refs.get(id);
        if (e == null) return;
        if (k == Kind.FROZEN && !(e instanceof Player) && e.isValid()) {
            e.setAI(true);
            e.setSilent(false);
            e.removeScoreboardTag(FROZEN_TAG);
        }
    }

    void clear(LivingEntity e) {
        EnumMap<Kind, Long> m = active.remove(e.getUniqueId());
        if (m != null && m.containsKey(Kind.FROZEN)) end(e.getUniqueId(), Kind.FROZEN);
        refs.remove(e.getUniqueId());
    }

    /** A frozen-tagged mob found without an active effect (after a restart) gets its AI back. */
    void repair(LivingEntity e) {
        if (e.getScoreboardTags().contains(FROZEN_TAG) && !has(e, Kind.FROZEN)) {
            e.setAI(true);
            e.setSilent(false);
            e.removeScoreboardTag(FROZEN_TAG);
        }
    }

    void tick(long ticks) {
        now = ticks;
        if (active.isEmpty()) return;
        Iterator<Map.Entry<UUID, EnumMap<Kind, Long>>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, EnumMap<Kind, Long>> en = it.next();
            LivingEntity e = refs.get(en.getKey());
            if (e == null || !e.isValid() || e.isDead()) {
                if (e != null && e.isValid()) end(en.getKey(), Kind.FROZEN);
                refs.remove(en.getKey());
                it.remove();
                continue;
            }
            Iterator<Map.Entry<Kind, Long>> ki = en.getValue().entrySet().iterator();
            while (ki.hasNext()) {
                Map.Entry<Kind, Long> k = ki.next();
                if (k.getValue() <= now) { end(en.getKey(), k.getKey()); ki.remove(); continue; }
                perform(e, k.getKey(), ticks);
            }
            if (en.getValue().isEmpty()) { refs.remove(en.getKey()); it.remove(); }
        }
    }

    private void perform(LivingEntity e, Kind k, long ticks) {
        switch (k) {
            case FROZEN:
                if ((ticks % 10) == 0) e.getWorld().spawnParticle(Particle.SNOW_SHOVEL, e.getLocation().add(0, e.getHeight() / 2, 0), 6, 0.3, 0.5, 0.3, 0.01);
                if (random.nextInt(1024) == 0) { EnumMap<Kind, Long> m = active.get(e.getUniqueId()); if (m != null) m.put(Kind.FROZEN, now); }
                break;
            case INFESTED:
                if (random.nextInt(128) == 0) spreadSpore(e);
                if ((ticks % 20) == 0) e.getWorld().spawnParticle(Particle.SPELL_MOB, e.getLocation().add(0, 1, 0), 0, 0.55, 0.37, 0.16, 1);
                break;
            case FIRE_BURNING:
                if ((ticks % 10) == 0 && !e.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                    ((CraftLivingEntity) e).getHandle().damageEntity(net.minecraft.server.v1_12_R1.DamageSource.BURN, 1.0f);
                    e.getWorld().spawnParticle(Particle.SPELL_INSTANT, e.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.01);
                }
                break;
            case CRYING:
                if (e instanceof Player && plugin.isNether(e.getWorld()) && random.nextInt(256) == 0) crying((Player) e);
                break;
            default:
        }
    }

    private void spreadSpore(LivingEntity e) {
        if (plugin.mobs.infestImmune(e)) return;
        Location l = e.getLocation();
        int crowd = 0;
        for (Entity n : e.getNearbyEntities(1, 1, 1)) if (n instanceof LivingEntity) crowd++;
        if (crowd >= 2) return;
        int dir = random.nextInt(4);
        Block b = l.getBlock().getRelative(dir == 0 ? 1 : dir == 1 ? -1 : 0, 0, dir == 2 ? 1 : dir == 3 ? -1 : 0);
        if (b.getType().isSolid() || !b.getRelative(0, -1, 0).getType().isSolid()) return;
        plugin.mobs.spawnSpore(b.getLocation().add(0.5, 0, 0.5), 0);
    }

    private void crying(Player p) {
        Location l = p.getLocation();
        org.bukkit.util.Vector back = l.getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new org.bukkit.util.Vector(0, 0, 1);
        back.normalize().multiply(-5);
        Location at = l.clone().add(back).add(0, 5, 0);
        if (at.getBlock().getType().isSolid()) return;
        LivingEntity g = plugin.mobs.spawn("ghastling", at, false);
        if (g instanceof org.bukkit.entity.Creature) ((org.bukkit.entity.Creature) g).setTarget(p);
    }

    // ---- listeners --------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!has(e.getPlayer(), Kind.FROZEN)) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY())) return;
        Location lock = from.clone();
        lock.setYaw(to.getYaw());
        lock.setPitch(to.getPitch());
        lock.setY(Math.min(from.getY(), to.getY()));
        e.setTo(lock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrozenAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && has(e.getDamager(), Kind.FROZEN)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrozenUse(PlayerInteractEvent e) {
        if (has(e.getPlayer(), Kind.FROZEN)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player) || !has(e.getEntity(), Kind.FROSTBITTEN)) return;
        Player p = (Player) e.getEntity();
        if (p.hasPotionEffect(PotionEffectType.REGENERATION) || p.hasPotionEffect(PotionEffectType.ABSORPTION)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) { clear(e.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { clear(e.getPlayer()); }

}
