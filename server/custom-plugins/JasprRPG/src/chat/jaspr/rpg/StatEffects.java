package chat.jaspr.rpg;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    /** Attribute modifiers of the stats that are standing bonuses (fixed ids, removed exactly). */
    private static final UUID STEADFAST_ID = id("steadfast"), FLEET_ID = id("fleet_foot"), LUCK_ID = id("luck");
    static final double CRIT_BASE = 0.50d, CRIT_CHANCE_CAP = 0.30d, EVASION_CAP = 0.25d;
    static final long SECOND_WIND_BASE_MS = 180_000L, SECOND_WIND_MIN_MS = 60_000L;

    private final RpgPlugin plugin;
    private final Random random = new Random();
    private final PlacedBlocks placed = new PlacedBlocks();
    private final Map<UUID, Long> secondWind = new HashMap<UUID, Long>();
    private final Map<UUID, Double> scholarRest = new HashMap<UUID, Double>();
    long crits, dodges, leeched, winds, scavenged, doubleCatches, extraCrops, extraLogs, placedSkips;

    StatEffects(RpgPlugin plugin) { this.plugin = plugin; }

    private static UUID id(String stat) {
        return UUID.nameUUIDFromBytes(("jaspr-rpg:" + stat).getBytes(StandardCharsets.UTF_8));
    }

    private RpgConfig settings() { return plugin.settings(); }

    private PlayerStats sheet(Player player) { return plugin.stats().get(player); }

    String metrics() {
        return "crits=" + crits + " dodges=" + dodges + " leeched=" + leeched + " secondWinds=" + winds + " scavenged=" + scavenged
            + " doubleCatches=" + doubleCatches + " extraCrops=" + extraCrops + " extraLogs=" + extraLogs + " placedSkips=" + placedSkips
            + " placedTracked=" + placed.size();
    }

    // ------------------------------------------------------------------ ambient effects

    /**
     * Reapplies everything that is a standing state rather than a reaction: carrying capacity for
     * health, dig speed for the tool in hand, jump height, knockback resistance, speed and luck.
     */
    void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();

        applyHealth(player, stats, config);
        applyDigSpeed(player, stats);
        applyJump(player, stats);
        modifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, STEADFAST_ID, "jaspr_rpg_steadfast",
            Math.min(0.6d, stats.linear(StatType.STEADFAST, config)), AttributeModifier.Operation.ADD_NUMBER);
        modifier(player, Attribute.GENERIC_MOVEMENT_SPEED, FLEET_ID, "jaspr_rpg_fleet_foot",
            Math.min(0.25d, stats.linear(StatType.FLEET_FOOT, config)), AttributeModifier.Operation.ADD_SCALAR);
        modifier(player, Attribute.GENERIC_LUCK, LUCK_ID, "jaspr_rpg_luck",
            Math.min(8.0d, stats.linear(StatType.LUCK, config)), AttributeModifier.Operation.ADD_NUMBER);
    }

    private void applyHealth(Player player, PlayerStats stats, RpgConfig config) {
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health == null) return;
        // Mastery ranks add half as much as ordinary ones, like every other stat.
        double target = 20.0d + StatType.effective(stats.level(StatType.HEALTH), config.capFor(StatType.HEALTH)) * config.healthPerLevel;
        if (Math.abs(health.getBaseValue() - target) > 0.01d) {
            health.setBaseValue(target);
            if (player.getHealth() > target) player.setHealth(target);
        }
    }

    /** Adds, updates or removes one of this plugin's attribute modifiers (amount 0 removes it). */
    private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier present = null;
        for (AttributeModifier m : inst.getModifiers()) if (m.getUniqueId().equals(id)) present = m;
        boolean want = amount > 1e-6;
        if (present != null && (!want || Math.abs(present.getAmount() - amount) > 1e-9)) {
            inst.removeModifier(present);
            present = null;
        }
        if (want && present == null) inst.addModifier(new AttributeModifier(id, name, amount, op));
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
        int amplifier = hasteAmplifier(level);
        if (ours && current.getAmplifier() == amplifier) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,
                Integer.MAX_VALUE, amplifier, true, false), true);
    }

    /** One tier of haste per two levels up to Haste V at level 9; mastery ranks reach Haste VII at 13. */
    static int hasteAmplifier(int level) {
        if (level <= StatType.ORDINARY) return Math.min(4, (level - 1) / 2);
        return Math.min(6, 4 + (level - StatType.ORDINARY + 1) / 2);
    }

    private void applyJump(Player player, PlayerStats stats) {
        int level = stats.level(StatType.LEAPER_V);
        PotionEffect current = player.getPotionEffect(PotionEffectType.JUMP);
        boolean ours = current != null && current.getDuration() > 100000;

        if (level <= 0) {
            if (ours) player.removePotionEffect(PotionEffectType.JUMP);
            return;
        }
        int amplifier = jumpAmplifier(level);
        if (ours && current.getAmplifier() == amplifier) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP,
                Integer.MAX_VALUE, amplifier, true, false), true);
    }

    /** Jump Boost I-IV over the ordinary ranks; the mastery ranks add a fifth tier from level 13. */
    static int jumpAmplifier(int level) {
        if (level <= StatType.ORDINARY) return Math.min(3, (level - 1) / 3);
        return level >= 13 ? 4 : 3;
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

    /** Bosses and the elite of every realm (Slayer). */
    static boolean bossOrElite(Entity e) {
        EntityType t = e.getType();
        if (t == EntityType.WITHER || t == EntityType.ENDER_DRAGON || t == EntityType.ELDER_GUARDIAN || t == EntityType.GIANT) return true;
        for (String tag : e.getScoreboardTags()) {
            if (tag.equals("jaspr_boss") || tag.equals("jn_elite") || tag.equals("jn_lord") || tag.equals("jaspr_horror_elite")
                || tag.equals("br_elite") || tag.equals("br_boss") || tag.startsWith("atlas_boss:")) return true;
        }
        return false;
    }

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
        if (attacker == null || event.getEntity() instanceof ArmorStand) return;

        PlayerStats stats = sheet(attacker);
        RpgConfig config = settings();
        double multiplier = 1.0d;

        if (ranged) {
            multiplier = stats.multiplier(StatType.BOWMANSHIP, config);
        } else {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            String name = held == null ? "AIR" : held.getType().name();
            boolean empty = held == null || held.getType() == Material.AIR;
            if (name.endsWith("_SWORD")) multiplier = stats.multiplier(StatType.SWORDSMANSHIP, config);
            else if (name.endsWith("_AXE")) multiplier = stats.multiplier(StatType.AXEMANSHIP, config);
            else if (empty) multiplier = stats.multiplier(StatType.PUGILISM, config);
            else if (Armament.isGun(held)) multiplier = stats.multiplier(StatType.GUNSLINGER, config);
        }
        if (bossOrElite(event.getEntity())) multiplier *= stats.multiplier(StatType.SLAYER, config);

        // Precision: a clean critical, whatever the weapon. Ferocity makes it bite deeper.
        double chance = Math.min(CRIT_CHANCE_CAP, stats.linear(StatType.PRECISION, config));
        if (chance > 0 && event.getEntity() instanceof LivingEntity && random.nextDouble() < chance) {
            multiplier *= 1.0d + CRIT_BASE + stats.linear(StatType.FEROCITY, config);
            crits++;
            Entity target = event.getEntity();
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 14, 0.3, 0.4, 0.3, 0.15);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.1f);
        }

        if (multiplier != 1.0d) event.setDamage(event.getDamage() * multiplier);
    }

    /** Bloodthirst: melee blows heal a share of the damage that actually landed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageLanded(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof ArmorStand) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        Player attacker = (Player) event.getDamager();
        if (Armament.isGun(attacker.getInventory().getItemInMainHand())) return;
        double share = Math.min(0.25d, sheet(attacker).linear(StatType.BLOODTHIRST, settings()));
        if (share <= 0 || attacker.isDead()) return;
        double heal = Math.min(4.0d, event.getFinalDamage() * share);
        if (heal < 0.05d) return;
        double max = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if (attacker.getHealth() >= max) return;
        attacker.setHealth(Math.min(max, attacker.getHealth() + heal));
        leeched++;
    }

    /** Evasion: a share of blows and shots from creatures simply miss. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
            && cause != EntityDamageEvent.DamageCause.PROJECTILE) return;
        Entity from = event.getDamager();
        if (from instanceof Projectile && ((Projectile) from).getShooter() instanceof Entity) from = (Entity) ((Projectile) from).getShooter();
        if (!(from instanceof LivingEntity)) return;
        Player player = (Player) event.getEntity();
        double chance = Math.min(EVASION_CAP, sheet(player).linear(StatType.EVASION, settings()));
        if (chance <= 0 || random.nextDouble() >= chance) return;
        event.setCancelled(true);
        dodges++;
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.8f, 1.6f);
        bar(player, ChatColor.AQUA + "Dodged!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();

        double reduction = 0.0d;
        reduction += fraction(stats, StatType.TOUGH_SKIN, config);

        switch (event.getCause()) {
            case FALL: reduction += fraction(stats, StatType.FEATHER_FALL, config); break;
            case FIRE: case FIRE_TICK: case LAVA: case HOT_FLOOR: reduction += fraction(stats, StatType.FIRE_WARD, config); break;
            case BLOCK_EXPLOSION: case ENTITY_EXPLOSION: reduction += fraction(stats, StatType.BLAST_WARD, config); break;
            case POISON: case WITHER: case MAGIC: case DRAGON_BREATH: reduction += fraction(stats, StatType.ANTIDOTE, config); break;
            default: break;
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

    /** Second Wind: a blow that leaves you below 30% health triggers a short, strong regeneration. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWounded(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        double level = sheet(player).effective(StatType.SECOND_WIND, settings());
        if (level <= 0) return;
        double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double after = player.getHealth() - event.getFinalDamage();
        if (after <= 0 || after >= max * 0.30d) return;
        long now = System.currentTimeMillis();
        Long ready = secondWind.get(player.getUniqueId());
        if (ready != null && now < ready) return;
        secondWind.put(player.getUniqueId(), now + secondWindCooldown(level));
        int ticks = 40 + (int) Math.round(level * 4);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, true, false), true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.8f);
        bar(player, ChatColor.GOLD + "Second Wind!" + ChatColor.GRAY + " you catch your breath");
        winds++;
    }

    /** 180 s at level 1, 8 s less per effective level, never under a minute. */
    static long secondWindCooldown(double effectiveLevel) {
        return Math.max(SECOND_WIND_MIN_MS, SECOND_WIND_BASE_MS - Math.round(effectiveLevel * StatType.SECOND_WIND.perLevel * 1000L));
    }

    /** Monsters give a stealthy player more room before they commit. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        int level = sheet(player).level(StatType.STEALTH);
        if (level <= 0) return;

        double distance = event.getEntity().getLocation().distance(player.getLocation());
        if (distance > 16.0d - stealthBlind(level)) event.setCancelled(true);
    }

    /** About a block of room per ordinary level (12 at level 10); mastery ranks add 0.4 each, up to 14. */
    static double stealthBlind(int level) {
        return Math.min(14.0d, 1.2d * Math.min(level, StatType.ORDINARY) + 0.4d * Math.max(0, level - StatType.ORDINARY));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        PlayerStats stats = sheet(killer);
        double multiplier = stats.multiplier(StatType.REAPER, settings());
        if (multiplier > 1.0d) event.setDroppedExp((int) Math.round(event.getDroppedExp() * multiplier));

        // Scavenger: a plain drop may come twice (never named or tagged loot: relics, sigils, trinkets).
        double chance = Math.min(0.5d, stats.linear(StatType.SCAVENGER, settings()));
        if (chance <= 0 || dead instanceof Player) return;
        List<ItemStack> extra = new ArrayList<ItemStack>();
        for (ItemStack drop : event.getDrops()) if (plain(drop) && random.nextDouble() < chance) extra.add(drop.clone());
        if (!extra.isEmpty()) { event.getDrops().addAll(extra); scavenged += extra.size(); }
    }

    /** An ordinary vanilla item: no name, lore or plugin data that would make a copy a duplicate of something special. */
    static boolean plain(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.isUnbreakable();
    }

    /** Tempering: a chance for a point of wear simply not to land. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        double multiplier = sheet(event.getPlayer()).multiplier(StatType.TEMPERING, settings());
        if (multiplier <= 1.0d) return;
        double skip = Math.min(0.70d, 1.0d - (1.0d / multiplier));
        if (random.nextDouble() < skip) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ survival

    /** Recovery: natural healing (from a full stomach) heals more. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        EntityRegainHealthEvent.RegainReason why = event.getRegainReason();
        if (why != EntityRegainHealthEvent.RegainReason.SATIATED && why != EntityRegainHealthEvent.RegainReason.REGEN) return;
        double multiplier = sheet((Player) event.getEntity()).multiplier(StatType.RECOVERY, settings());
        if (multiplier > 1.0d) event.setAmount(event.getAmount() * multiplier);
    }

    /** Endurance: some of the hunger drain simply does not happen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getFoodLevel() >= player.getFoodLevel()) return;
        double multiplier = sheet(player).multiplier(StatType.ENDURANCE, settings());
        if (multiplier <= 1.0d) return;
        if (random.nextDouble() < Math.min(0.60d, 1.0d - 1.0d / multiplier)) event.setCancelled(true);
    }

    /** Scholar: every experience orb is worth more (fractions are carried over, never lost). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) return;
        Player player = event.getPlayer();
        double multiplier = sheet(player).multiplier(StatType.SCHOLAR, settings());
        if (multiplier <= 1.0d) return;
        Double rest = scholarRest.get(player.getUniqueId());
        double value = event.getAmount() * multiplier + (rest == null ? 0 : rest);
        int whole = (int) Math.floor(value);
        scholarRest.put(player.getUniqueId(), Math.min(1.0d, value - whole));
        event.setAmount(whole);
    }

    // ------------------------------------------------------------------ gathering

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) placed.place(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        boolean wasPlaced = PlacedBlocks.watched(block.getType()) && placed.take(block);
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerStats stats = sheet(player);
        RpgConfig config = settings();
        Material type = block.getType();

        int treasure = stats.level(StatType.TREASURE);
        if (treasure > 0 && isSoft(type) && random.nextDouble() < stats.effective(StatType.TREASURE, config) * 0.04d) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), treasureFor(type));
        }

        if (wasPlaced) { placedSkips++; return; }   // a block the player put down earns no gathering bonus

        if (isOre(type) && random.nextDouble() < stats.effective(StatType.MAGICIAN, config) * 0.05d) {
            for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand())) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), drop);
            }
        }

        if ((type == Material.LOG || type == Material.LOG_2) && random.nextDouble() < Math.min(0.6d, stats.linear(StatType.LUMBERJACK, config))) {
            for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand())) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), drop);
            }
            extraLogs++;
        }

        Material produce = produce(block);
        if (produce != null && random.nextDouble() < Math.min(0.75d, stats.linear(StatType.GREEN_THUMB, config))) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), new ItemStack(produce, 1 + random.nextInt(2)));
            extraCrops++;
        }
    }

    /** What a fully grown crop gives (Green Thumb), or null for anything else. */
    @SuppressWarnings("deprecation")
    static Material produce(Block b) {
        switch (b.getType()) {
            case CROPS: return b.getData() >= 7 ? Material.WHEAT : null;
            case CARROT: return b.getData() >= 7 ? Material.CARROT_ITEM : null;
            case POTATO: return b.getData() >= 7 ? Material.POTATO_ITEM : null;
            case BEETROOT_BLOCK: return b.getData() >= 3 ? Material.BEETROOT : null;
            case NETHER_WARTS: return b.getData() >= 3 ? Material.NETHER_STALK : null;
            default: return null;
        }
    }

    /** Angler: sometimes the line comes up with two. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item)) return;
        Player player = event.getPlayer();
        double chance = Math.min(0.75d, sheet(player).linear(StatType.ANGLER, settings()));
        if (chance <= 0 || random.nextDouble() >= chance) return;
        ItemStack catchItem = ((Item) event.getCaught()).getItemStack();
        if (!plain(catchItem)) return;
        player.getWorld().dropItem(player.getLocation(), catchItem.clone());
        doubleCatches++;
        bar(player, ChatColor.AQUA + "Double catch!");
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

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        scholarRest.remove(id);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { refresh(player); }
        }, 5L);
    }

    static void bar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }
}
