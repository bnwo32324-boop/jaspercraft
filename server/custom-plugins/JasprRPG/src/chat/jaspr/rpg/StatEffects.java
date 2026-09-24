package chat.jaspr.rpg;

import java.util.Random;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Where the stats stop being numbers and start being felt.
 *
 * Some of these map straight onto a Bukkit event. Others - mining speed above all - have no direct
 * server-side control in 1.12, because the client decides how fast a block breaks. Those are
 * expressed through an equivalent the server can actually impose, refreshed as the player switches
 * tools, and the effect on play is the same even though the mechanism differs.
 */
final class StatEffects implements Listener {
    private final RpgPlugin plugin;
    private final Random random = new Random();

    StatEffects(RpgPlugin plugin) { this.plugin = plugin; }

    private RpgConfig settings() { return plugin.settings(); }

    private PlayerStats sheet(Player player) { return plugin.stats().get(player); }

    // ------------------------------------------------------------------ ambient effects

    /**
     * Reapplies everything that is a standing state rather than a reaction: carrying capacity for
     * health, dig speed for the tool in hand, and jump height.
     */
    void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();

        applyHealth(player, stats, config);
        applyDigSpeed(player, stats);
        applyJump(player, stats);
    }

    private void applyHealth(Player player, PlayerStats stats, RpgConfig config) {
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health == null) return;
        double target = 20.0d + stats.level(StatType.HEALTH) * config.healthPerLevel;
        if (Math.abs(health.getBaseValue() - target) > 0.01d) {
            health.setBaseValue(target);
            if (player.getHealth() > target) player.setHealth(target);
        }
    }

    /**
     * The client, not the server, decides how quickly a block gives way, so a dig-speed stat has
     * to be expressed as haste. The level of the stat that matches the tool in hand is what counts.
     */
    private void applyDigSpeed(Player player, PlayerStats stats) {
        StatType relevant = toolStat(player.getInventory().getItemInMainHand());
        int level = relevant == null ? 0 : stats.level(relevant);
        PotionEffect current = player.getPotionEffect(PotionEffectType.FAST_DIGGING);
        boolean ours = current != null && current.getDuration() > 100000;

        if (level <= 0) {
            if (ours) player.removePotionEffect(PotionEffectType.FAST_DIGGING);
            return;
        }
        // One tier of haste per two stat levels keeps ten levels inside a sane range.
        int amplifier = Math.min(4, (level - 1) / 2);
        if (ours && current.getAmplifier() == amplifier) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,
                Integer.MAX_VALUE, amplifier, true, false), true);
    }

    private void applyJump(Player player, PlayerStats stats) {
        int level = stats.level(StatType.LEAPER_V);
        PotionEffect current = player.getPotionEffect(PotionEffectType.JUMP);
        boolean ours = current != null && current.getDuration() > 100000;

        if (level <= 0) {
            if (ours) player.removePotionEffect(PotionEffectType.JUMP);
            return;
        }
        int amplifier = Math.min(3, (level - 1) / 3);
        if (ours && current.getAmplifier() == amplifier) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP,
                Integer.MAX_VALUE, amplifier, true, false), true);
    }

    private StatType toolStat(ItemStack held) {
        if (held == null) return null;
        String name = held.getType().name();
        if (name.endsWith("_PICKAXE")) return StatType.MINING;
        if (name.endsWith("_SPADE") || name.endsWith("_SHOVEL")) return StatType.DIGGING;
        if (name.endsWith("_AXE")) return StatType.CHOPPING;
        if (name.endsWith("_HOE") || name.equals("SHEARS")) return StatType.TRIMMING;
        return null;
    }

    // ------------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = null;
        boolean ranged = false;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile shot = (Projectile) event.getDamager();
            if (shot.getShooter() instanceof Player && shot instanceof Arrow) {
                attacker = (Player) shot.getShooter();
                ranged = true;
            }
        }
        if (attacker == null) return;

        PlayerStats stats = sheet(attacker);
        RpgConfig config = settings();
        double multiplier = 1.0d;

        if (ranged) {
            multiplier = stats.multiplier(StatType.BOWMANSHIP, config);
        } else {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            boolean sword = held != null && held.getType().name().endsWith("_SWORD");
            boolean empty = held == null || held.getType() == Material.AIR;
            if (sword) multiplier = stats.multiplier(StatType.SWORDSMANSHIP, config);
            else if (empty) multiplier = stats.multiplier(StatType.PUGILISM, config);
        }

        if (multiplier > 1.0d) event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();

        double reduction = 0.0d;
        reduction += fraction(stats, StatType.TOUGH_SKIN, config);

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            reduction += fraction(stats, StatType.FEATHER_FALL, config);
        }
        if (wearingArmour(player)) {
            reduction += fraction(stats, StatType.PROTECTION, config);
        }
        if (player.isBlocking()) {
            reduction += fraction(stats, StatType.STEADY_GUARD, config);
        }
        if (player.isSneaking()) {
            // Rolling with the blow. Crouching at the moment of impact is the tell.
            reduction += fraction(stats, StatType.ROLL, config);
        }

        if (reduction <= 0.0d) return;
        // Never let stacked reductions reach immunity; eighty percent is the floor on damage taken.
        reduction = Math.min(0.80d, reduction);
        event.setDamage(event.getDamage() * (1.0d - reduction));
    }

    private double fraction(PlayerStats stats, StatType stat, RpgConfig config) {
        double multiplier = stats.multiplier(stat, config);
        if (multiplier <= 1.0d) return 0.0d;
        // A growth multiplier of 1.30 reads as a thirty percent reduction, capped per stat.
        return Math.min(0.60d, multiplier - 1.0d);
    }

    private boolean wearingArmour(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR) return true;
        }
        return false;
    }

    /** Monsters give a stealthy player more room before they commit. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        int level = sheet(player).level(StatType.STEALTH);
        if (level <= 0) return;

        double distance = event.getEntity().getLocation().distance(player.getLocation());
        // Each level shaves roughly a block off how far away something can notice you.
        double blind = Math.min(12.0d, level * 1.2d);
        if (distance > 16.0d - blind) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        double multiplier = sheet(killer).multiplier(StatType.REAPER, settings());
        if (multiplier > 1.0d) event.setDroppedExp((int) Math.round(event.getDroppedExp() * multiplier));
    }

    /** Tempering: a chance for a point of wear simply not to land. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        double multiplier = sheet(event.getPlayer()).multiplier(StatType.TEMPERING, settings());
        if (multiplier <= 1.0d) return;
        double skip = Math.min(0.70d, 1.0d - (1.0d / multiplier));
        if (random.nextDouble() < skip) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ gathering

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerStats stats = sheet(player);
        Block block = event.getBlock();
        Material type = block.getType();

        int treasure = stats.level(StatType.TREASURE);
        if (treasure > 0 && isSoft(type) && random.nextInt(100) < treasure * 4) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), treasureFor(type));
        }

        int magician = stats.level(StatType.MAGICIAN);
        if (magician > 0 && isOre(type) && random.nextInt(100) < magician * 5) {
            for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand())) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), drop);
            }
        }
    }

    private boolean isSoft(Material type) {
        return type == Material.GRASS || type == Material.DIRT || type == Material.SAND
                || type == Material.LEAVES || type == Material.LEAVES_2 || type == Material.GRAVEL;
    }

    private boolean isOre(Material type) {
        String name = type.name();
        return name.endsWith("_ORE") || name.equals("QUARTZ_ORE");
    }

    private ItemStack treasureFor(Material broken) {
        Material[] pool;
        if (broken == Material.SAND || broken == Material.GRAVEL) {
            pool = new Material[]{ Material.FLINT, Material.CLAY_BALL, Material.BONE, Material.GOLD_NUGGET };
        } else if (broken == Material.LEAVES || broken == Material.LEAVES_2) {
            pool = new Material[]{ Material.APPLE, Material.STICK, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS };
        } else {
            pool = new Material[]{ Material.SEEDS, Material.BONE, Material.IRON_NUGGET, Material.CLAY_BALL };
        }
        return new ItemStack(pool[random.nextInt(pool.length)]);
    }

    /** Furnace Finesse: fuel simply lasts longer for a practised smith. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        Player owner = plugin.nearestOwner(event.getBlock().getLocation());
        if (owner == null) return;
        double multiplier = sheet(owner).multiplier(StatType.FURNACE, settings());
        if (multiplier > 1.0d) event.setBurnTime((int) Math.round(event.getBurnTime() * multiplier));
    }

    // ------------------------------------------------------------------ movement

    /** Swimming and climbing are both a gentle shove in the direction already being travelled. */
    void tickMovement(Player player) {
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();

        if (player.isInsideVehicle()) return;
        Material feet = player.getLocation().getBlock().getType();
        boolean inWater = feet == Material.WATER || feet == Material.STATIONARY_WATER;
        boolean climbing = feet == Material.LADDER || feet == Material.VINE;

        StatType relevant = inWater ? StatType.SWIMMING : climbing ? StatType.CLIMBING : null;
        if (relevant == null) return;
        double multiplier = stats.multiplier(relevant, config);
        if (multiplier <= 1.0d) return;

        Vector velocity = player.getVelocity();
        double boost = Math.min(0.35d, (multiplier - 1.0d) * 0.30d);
        if (climbing && velocity.getY() > 0.01d) {
            player.setVelocity(velocity.setY(Math.min(0.5d, velocity.getY() * (1.0d + boost))));
        } else if (inWater && velocity.lengthSquared() > 0.01d) {
            player.setVelocity(velocity.multiply(1.0d + boost));
        }
    }

    /** Leaper, horizontal: a running jump carries further. */
    void onJump(Player player) {
        double multiplier = sheet(player).multiplier(StatType.LEAPER_H, settings());
        if (multiplier <= 1.0d) return;
        Vector velocity = player.getVelocity();
        Vector flat = new Vector(velocity.getX(), 0.0d, velocity.getZ());
        if (flat.lengthSquared() < 0.005d) return;
        double boost = Math.min(0.45d, (multiplier - 1.0d) * 0.8d);
        player.setVelocity(velocity.add(flat.multiply(boost)));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Death is the reset. Everything bought is gone, and the bill starts again at six levels. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!settings().resetOnDeath) return;
        Player player = event.getEntity();
        PlayerStats stats = sheet(player);
        if (stats.isEmpty()) return;

        int lost = stats.totalLevels();
        stats.clear();
        plugin.stats().markDirty();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                stripEffects(player);
                refresh(player);
            }
        });
        player.sendMessage(org.bukkit.ChatColor.DARK_RED + "Your training dies with you. "
                + org.bukkit.ChatColor.GRAY + lost + " stat level(s) lost.");
    }

    private void stripEffects(Player player) {
        PotionEffect haste = player.getPotionEffect(PotionEffectType.FAST_DIGGING);
        if (haste != null && haste.getDuration() > 100000) player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP);
        if (jump != null && jump.getDuration() > 100000) player.removePotionEffect(PotionEffectType.JUMP);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { refresh(event.getPlayer()); }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { refresh(player); }
        }, 5L);
    }
}
