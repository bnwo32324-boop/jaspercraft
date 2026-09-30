package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * The Backrooms' inhabitants: every level has its own (owner, 2026-09-30: "Each liminal space should have its own unique
 * dangers, threats"), and the difficulty grows with the level's number and the distance crossed inside it (owner: "The
 * difficulty should also increase the further you venture in and the higher the number of liminal spaces you are at").
 * Vanilla spawning is off in the Backrooms; the director below brings monsters near players instead: none within the
 * Threshold's first 64 blocks (the spawn is peaceful) nor in a landing's first stretch or an arena, then more of them,
 * tougher and more often elite, the deeper the danger ({@link Level#danger}).
 */
final class Mobs implements Listener {
    static final String TAG = "br_mob", KIND = "br_kind:", ELITE = "br_elite", LEVEL = "br_level:";

    enum Kind {
        WRETCH(Level.YELLOW, EntityType.HUSK, "Wretch", 18, 3.0, 10, 0.08),
        HOUND(Level.YELLOW, EntityType.WOLF, "Hound", 16, 3.0, 4, 0.45),
        SMILER(Level.YELLOW, EntityType.ENDERMAN, "Smiler", 30, 5.0, 2, 0.6),
        PACK_HOUND(Level.WAREHOUSE, EntityType.WOLF, "Hound", 18, 4.0, 7, 0),
        CRATE_MIMIC(Level.WAREHOUSE, EntityType.SHULKER, "Crate Mimic", 30, 4.0, 3, 0.2),
        SCAVENGER(Level.WAREHOUSE, EntityType.ZOMBIE, "Scavenger", 24, 4.0, 7, 0),
        STEAM_WRAITH(Level.TUNNELS, EntityType.BLAZE, "Steam Wraith", 20, 4.0, 4, 0.2),
        BURNER(Level.TUNNELS, EntityType.MAGMA_CUBE, "Burner", 16, 4.0, 5, 0),
        PIPE_CRAWLER(Level.TUNNELS, EntityType.CAVE_SPIDER, "Pipe Crawler", 14, 3.0, 7, 0),
        SCALDED(Level.TUNNELS, EntityType.HUSK, "Scalded Worker", 26, 5.0, 6, 0.1),
        SPARK(Level.ELECTRICAL, EntityType.VEX, "Spark", 10, 3.0, 4, 0.15),
        SURGE(Level.ELECTRICAL, EntityType.CREEPER, "Surge", 20, 0, 3, 0.35),
        LINEMAN(Level.ELECTRICAL, EntityType.SKELETON, "Lineman", 26, 4.0, 6, 0),
        GRID_CRAWLER(Level.ELECTRICAL, EntityType.SILVERFISH, "Grid Crawler", 10, 2.0, 4, 0),
        PARTYGOER(Level.OFFICE, EntityType.VINDICATOR, "Partygoer", 26, 7.0, 5, 0.25),
        CLERK(Level.OFFICE, EntityType.ZOMBIE, "Clerk", 26, 5.0, 8, 0),
        SHREDDER(Level.OFFICE, EntityType.SILVERFISH, "Paper Shredder", 12, 3.0, 4, 0.1),
        RECRUITER(Level.OFFICE, EntityType.EVOKER, "Recruiter", 30, 0, 1, 0.7),
        CITIZEN(Level.CITY, EntityType.VILLAGER, "Citizen", 30, 0, 5, 0),
        STALKER(Level.CITY, EntityType.STRAY, "Rooftop Stalker", 26, 4.0, 5, 0.15),
        WATCHER(Level.CITY, EntityType.ENDERMAN, "Watcher", 40, 7.0, 3, 0.2),
        STRAY_HOUND(Level.CITY, EntityType.WOLF, "Stray Hound", 22, 5.0, 4, 0),
        FACELESS(Level.CITY, EntityType.WITHER_SKELETON, "Faceless", 34, 8.0, 2, 0.6),
        POOL_GUARDIAN(Level.POOLS, EntityType.GUARDIAN, "Pool Guardian", 30, 6.0, 5, 0),
        SWIMMER(Level.POOLS, EntityType.ZOMBIE, "Drowned Swimmer", 30, 6.0, 6, 0),
        LOST_LIFEGUARD(Level.POOLS, EntityType.WITHER_SKELETON, "Lost Lifeguard", 40, 9.0, 3, 0.4),
        CHLORINE_WISP(Level.POOLS, EntityType.VEX, "Chlorine Wisp", 14, 5.0, 3, 0.2),
        MIMIC(Level.CITY, EntityType.VINDICATOR, "Mimic", 32, 8.0, 0, 2);   // what a Citizen becomes
        final Level level;
        final EntityType type;
        final String name;
        final double health, damage, minProgress;
        final int weight;
        Kind(Level level, EntityType type, String name, double health, double damage, int weight, double minProgress) {
            this.level = level; this.type = type; this.name = name; this.health = health; this.damage = damage; this.weight = weight; this.minProgress = minProgress;
        }
    }

    /** Health and damage of the Backrooms' monsters at a danger (0 at the Threshold, 1 at the Lifeguard). */
    static double healthScale(double g) { return 0.7 + 1.1 * g; }
    static double damageScale(double g) { return 0.55 + 1.0 * g; }
    /** Monsters allowed within 40 blocks of a player; the chance per two seconds that one more comes; elite odds. */
    static int cap(double g, double d) { return 1 + (int) (6 * g + 2 * d); }
    static double chance(double g) { return 0.25 + 0.5 * g; }
    static double eliteChance(double g) { return 0.03 + 0.25 * g; }
    /** No monster arrives this close to a level's entry room (the Threshold's stretch of level 1 is longer). */
    static int sanctuary(Level lv) { return lv.number == 1 ? 64 : 24; }

    private final BackroomsPlugin plugin;
    private final Random random = new Random();
    long spawned, elites, slain, despawned, transformed, refusedVanilla, invadersTurnedAway;

    Mobs(BackroomsPlugin plugin) { this.plugin = plugin; }

    static boolean isOurs(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    static Kind kindOf(Entity e) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(KIND)) try { return Kind.valueOf(t.substring(KIND.length())); } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static double dangerOf(Entity e) {
        Location l = e.getLocation();
        Level lv = Level.at(l.getX(), l.getZ());
        return lv == null ? 0 : lv.danger(l.getX(), l.getZ());
    }

    // ---- the director ---------------------------------------------------------------------------------------------------
    /** Every two seconds: monsters come for players, by level, danger and caps. */
    void direct() {
        World w = plugin.world();
        if (w == null) return;
        List<LivingEntity> ours = new ArrayList<>();
        for (LivingEntity e : w.getLivingEntities()) {
            if (e.getScoreboardTags().contains(INVADER)) { turnAway(e); continue; }
            if (isOurs(e) && !e.isDead()) ours.add(e);
        }
        // Far monsters go first, so they never hold the cap full.
        cleanup(w, ours);
        ours.removeIf(e -> !e.isValid());
        int players = 0;
        for (Player p : w.getPlayers()) if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) players++;
        if (ours.size() >= 24 + 10 * players) return;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || p.isDead()) continue;
            Location l = p.getLocation();
            Level lv = Level.at(l.getX(), l.getZ());
            if (lv == null || !lv.inBody((int) l.getX()) || l.getX() < lv.entryEnd() + sanctuary(lv)) continue;
            double g = lv.danger(l.getX(), l.getZ()), d = lv.progress(l.getX());
            int near = 0;
            for (LivingEntity e : ours) if (e.getWorld() == w && e.getLocation().distanceSquared(l) < 40 * 40) near++;
            if (near >= cap(g, d) || random.nextDouble() >= chance(g)) continue;
            Kind k = pick(lv, d);
            if (k == null) continue;
            Location at = spot(w, lv, l, k);
            if (at == null) continue;
            int count = k == Kind.HOUND || k == Kind.PACK_HOUND || k == Kind.STRAY_HOUND ? 2 + random.nextInt(2) : k == Kind.GRID_CRAWLER || k == Kind.SHREDDER ? 2 : 1;
            for (int i = 0; i < count; i++) spawn(k, at, g, random.nextDouble() < eliteChance(g));
        }
    }

    /** JasprInvasions' invaders dig and pillar with direct block changes that no event reports, so none may stay here. */
    static final String INVADER = "jaspr_invader";

    void turnAway(LivingEntity e) {
        if (!e.isValid() || !e.getScoreboardTags().contains(INVADER)) return;
        e.remove();
        invadersTurnedAway++;
        if (invadersTurnedAway == 1 || invadersTurnedAway % 25 == 0) plugin.getLogger().info("BACKROOMS_INVADERS_TURNED_AWAY count=" + invadersTurnedAway);
    }

    private Kind pick(Level lv, double d) {
        int total = 0;
        for (Kind k : Kind.values()) if (k.level == lv && k.weight > 0 && k.minProgress <= d) total += k.weight;
        if (total == 0) return null;
        int r = random.nextInt(total);
        for (Kind k : Kind.values()) if (k.level == lv && k.weight > 0 && k.minProgress <= d && (r -= k.weight) < 0) return k;
        return null;
    }

    /** A spot 14 to 28 blocks from the player in the level's main space, standable (or water/roof for some kinds). */
    private Location spot(World w, Level lv, Location near, Kind k) {
        long seed = plugin.seed();
        for (int tries = 0; tries < 8; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = 14 + random.nextDouble() * 14;
            int x = (int) Math.floor(near.getX() + Math.cos(a) * r), z = (int) Math.floor(near.getZ() + Math.sin(a) * r);
            if (!lv.contains(x, z) || !lv.inBody(x) || x < lv.entryEnd() + sanctuary(lv) - 8 || Math.abs(z) > lv.width / 2 - 4) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (tooClose(w, x, z)) continue;
            if (k == Kind.STALKER) {
                int roof = City.roof(lv, seed, x, z);
                if (roof > 0 && w.getBlockAt(x, roof + 1, z).getType() == Material.AIR && w.getBlockAt(x, roof + 2, z).getType() == Material.AIR)
                    return new Location(w, x + 0.5, roof + 1, z + 0.5);
                continue;
            }
            if (k == Kind.POOL_GUARDIAN) {
                if (Pools.pool(seed, x, z) && w.getBlockAt(x, Pools.POOL_FLOOR + 2, z).isLiquid()) return new Location(w, x + 0.5, Pools.POOL_FLOOR + 2, z + 0.5);
                continue;
            }
            for (int y = Level.WALK + 2; y >= Level.WALK - 8; y--) {
                Block feet = w.getBlockAt(x, y, z);
                if (passable(feet) && passable(feet.getRelative(0, 1, 0)) && feet.getRelative(0, -1, 0).getType().isSolid()
                    && (k.type != EntityType.ENDERMAN && k.type != EntityType.WITHER_SKELETON || passable(feet.getRelative(0, 2, 0))))
                    return new Location(w, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private static boolean passable(Block b) {
        Material m = b.getType();
        return m == Material.AIR || m == Material.CARPET || m == Material.WATER || m == Material.STATIONARY_WATER;
    }

    private static boolean tooClose(World w, int x, int z) {
        for (Player p : w.getPlayers()) { double dx = p.getLocation().getX() - x, dz = p.getLocation().getZ() - z; if (dx * dx + dz * dz < 100) return true; }
        return false;
    }

    /** Monsters far from everyone go (they are not saved with their chunks either). */
    private void cleanup(World w, List<LivingEntity> ours) {
        for (LivingEntity e : ours) {
            if (Bosses.isBoss(e) || !e.isValid()) continue;
            boolean near = false;
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < 72 * 72) { near = true; break; }
            if (!near || e.getTicksLived() > 20 * 60 * 6 && e.getType() == EntityType.VEX) { e.remove(); despawned++; }
        }
    }

    // ---- making one ---------------------------------------------------------------------------------------------------
    LivingEntity spawn(Kind k, Location at, double g, boolean elite) {
        LivingEntity e = (LivingEntity) at.getWorld().spawnEntity(at, k.type);
        configure(e, k, g, elite);
        spawned++;
        if (elite) elites++;
        return e;
    }

    void configure(LivingEntity e, Kind k, double g, boolean elite) {
        e.addScoreboardTag(TAG);
        e.addScoreboardTag("jaspr_daylight_exempt");   // the overworld's sun does not slow them (JasprDaylight)
        e.addScoreboardTag(KIND + k.name());
        e.addScoreboardTag(LEVEL + k.level.number);
        if (elite) e.addScoreboardTag(ELITE);
        e.setRemoveWhenFarAway(false);
        e.setCustomName((elite ? ChatColor.DARK_RED + "Elite " : k.level.colour.toString()) + k.name);
        e.setCustomNameVisible(k != Kind.CITIZEN);
        double hp = k.health * healthScale(g) * (elite ? 2 : 1);
        set(e, Attribute.GENERIC_MAX_HEALTH, hp);
        e.setHealth(Math.min(hp, e.getMaxHealth()));
        if (k.damage > 0) set(e, Attribute.GENERIC_ATTACK_DAMAGE, k.damage * damageScale(g) * (elite ? 1.3 : 1));
        set(e, Attribute.GENERIC_FOLLOW_RANGE, 32);
        if (elite) set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
        EntityEquipment eq = e.getEquipment();
        switch (k) {
            case WRETCH: eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xE3CF7A)); break;
            case HOUND: case PACK_HOUND: case STRAY_HOUND: {
                Wolf wolf = (Wolf) e;
                wolf.setAngry(true);
                set(e, Attribute.GENERIC_MOVEMENT_SPEED, 0.36 + 0.06 * g);
                break;
            }
            case CRATE_MIMIC: ((Shulker) e).setColor(DyeColor.BROWN); break;
            case SCAVENGER: eq.setHelmet(new ItemStack(Material.GOLD_HELMET)); eq.setItemInMainHand(new ItemStack(Material.IRON_SPADE)); break;
            case BURNER: ((MagmaCube) e).setSize(2); set(e, Attribute.GENERIC_MAX_HEALTH, hp); e.setHealth(Math.min(hp, e.getMaxHealth())); break;
            case SCALDED: eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xC4541A)); eq.setItemInMainHand(fire(new ItemStack(Material.IRON_SPADE))); break;
            case SURGE: ((Creeper) e).setPowered(true); ((Creeper) e).setExplosionRadius(2 + (int) Math.round(2 * g)); break;
            case LINEMAN: eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET)); eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE)); break;
            case CLERK: eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0x333338)); eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, 0x333338)); break;
            case CITIZEN: {
                Villager v = (Villager) e;
                v.setProfession(Villager.Profession.values()[1 + random.nextInt(5)]);
                v.setCustomName(ChatColor.WHITE + "Citizen");
                v.setCustomNameVisible(false);
                break;
            }
            case STALKER: eq.setHelmet(dyed(Material.LEATHER_HELMET, 0x2C3E66)); break;
            case FACELESS: eq.setHelmet(new ItemStack(Material.PUMPKIN)); break;
            case SWIMMER: eq.setBoots(ench(new ItemStack(Material.LEATHER_BOOTS), Enchantment.DEPTH_STRIDER, 3)); eq.setHelmet(dyed(Material.LEATHER_HELMET, 0xD32F2F)); break;
            case LOST_LIFEGUARD: eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xD32F2F)); eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD)); break;
            default: break;
        }
        if (eq != null) {
            eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setLeggingsDropChance(0); eq.setBootsDropChance(0); eq.setItemInMainHandDropChance(0);
        }
        if (elite) e.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
    }

    static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static ItemStack dyed(Material m, int rgb) {
        ItemStack s = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) s.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(rgb));
        s.setItemMeta(meta);
        return s;
    }

    private static ItemStack fire(ItemStack s) { s.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1); return s; }
    private static ItemStack ench(ItemStack s, Enchantment e, int n) { s.addUnsafeEnchantment(e, n); return s; }

    // ---- behaviour --------------------------------------------------------------------------------------------------------
    /** Every half second: hounds stay angry at the nearest player, Citizens show what they are when you come close. */
    void behave() {
        World w = plugin.world();
        if (w == null) return;
        for (LivingEntity e : w.getLivingEntities()) {
            if (!isOurs(e) || Bosses.isBoss(e)) continue;
            Kind k = kindOf(e);
            if (k == null) continue;
            Player near = nearest(e, 20);
            if (k == Kind.CITIZEN) {
                if (near != null && near.getLocation().distanceSquared(e.getLocation()) < 25 && near.getGameMode() != GameMode.CREATIVE) transform(e);
                else e.setCustomNameVisible(near != null && plugin.gear().revealsMimics(near) && near.getLocation().distanceSquared(e.getLocation()) < 256);
                if (e.isCustomNameVisible()) e.setCustomName(ChatColor.RED + "Mimic");
                continue;
            }
            if (near == null || !(e instanceof Creature)) continue;
            if (e instanceof Wolf) ((Wolf) e).setAngry(true);
            // Smilers and Watchers keep to the old rule (look at them and they come), unless you walk right up to them.
            if (e.getType() == EntityType.ENDERMAN && near.getLocation().distanceSquared(e.getLocation()) > 16) continue;
            if (((Creature) e).getTarget() == null && !plugin.gear().unnoticed(near, e)) ((Creature) e).setTarget(near);
        }
    }

    /** A Citizen drops the act. */
    private void transform(LivingEntity citizen) {
        Location at = citizen.getLocation();
        double g = dangerOf(citizen);
        boolean elite = citizen.getScoreboardTags().contains(ELITE);
        citizen.remove();
        at.getWorld().spawnParticle(Particle.SMOKE_LARGE, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_ILLUSION_ILLAGER_MIRROR_MOVE, 1f, 0.6f);
        spawn(Kind.MIMIC, at, g, elite);
        transformed++;
    }

    static Player nearest(Entity e, double range) {
        Player best = null;
        double bd = range * range;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ---- events -----------------------------------------------------------------------------------------------------------
    /** Vanilla spawning stays out of the Backrooms (the director, bosses, summons and splitting cubes are let through). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!plugin.isBackrooms(e.getLocation().getWorld())) return;
        switch (e.getSpawnReason()) {
            case NATURAL: case CHUNK_GEN: case SPAWNER: case VILLAGE_DEFENSE: case VILLAGE_INVASION: case JOCKEY: case MOUNT: case NETHER_PORTAL:
            case REINFORCEMENTS: case LIGHTNING: case ENDER_PEARL: case TRAP:
                e.setCancelled(true);
                refusedVanilla++;
                return;
            case SLIME_SPLIT: {
                // A Burner's children are Burners too (small, and they go when nobody is near).
                e.getEntity().addScoreboardTag(TAG);
                e.getEntity().addScoreboardTag(KIND + Kind.BURNER.name());
                return;
            }
            default: {
                // An invader is tagged only after it spawns: look again next tick, before it can dig.
                LivingEntity spawned = e.getEntity();
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> turnAway(spawned));
            }
        }
    }

    /** Monster damage grows with the danger where the monster stands (also arrows, fireballs, explosions). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !plugin.isBackrooms(e.getEntity().getWorld())) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile) { ProjectileSource s = ((Projectile) src).getShooter(); if (s instanceof Entity) src = (Entity) s; }
        if (!isOurs(src) || Bosses.isBoss(src)) return;
        Kind k = kindOf(src);
        if (k != null && k.damage > 0 && !(e.getDamager() instanceof Projectile) && src instanceof Creature && src.getType() != EntityType.CREEPER) return;   // melee uses the attribute
        e.setDamage(e.getDamage() * damageScale(dangerOf(src)) * (src.getScoreboardTags().contains(ELITE) ? 1.3 : 1));
    }

    /** Nothing here burns in the dusk light. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (isOurs(e.getEntity()) && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent) && !(e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent)) e.setCancelled(true);
    }

    /** Monsters with no one to chase drop their targets on players who went unnoticed (stealth gear). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player) || !isOurs(e.getEntity()) || Bosses.isBoss(e.getEntity())) return;
        if (plugin.gear().unnoticed((Player) e.getTarget(), e.getEntity())) e.setCancelled(true);
    }

    /** Drops: Almond Water now and then, the level's gear rarely (more often deeper in and from elites); more experience. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!isOurs(dead) || Bosses.isBoss(dead)) return;
        slain++;
        Kind k = kindOf(dead);
        double g = dangerOf(dead);
        boolean elite = dead.getScoreboardTags().contains(ELITE);
        e.getDrops().removeIf(s -> s.getType() == Material.ROTTEN_FLESH && random.nextBoolean());
        if (random.nextDouble() < 0.08 + (elite ? 0.2 : 0)) e.getDrops().add(Items.make("almond_water"));
        if (k != null && random.nextDouble() < 0.015 + 0.05 * g + (elite ? 0.12 : 0)) e.getDrops().add(Loot.gear(k.level, random, g));
        e.setDroppedExp((int) Math.round(e.getDroppedExp() * (1 + 2 * g) * (elite ? 2 : 1)) + 1);
    }

    /** Monsters are not saved with their chunks: the director brings new ones. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent e) {
        if (!plugin.isBackrooms(e.getWorld())) return;
        for (Entity x : e.getChunk().getEntities()) if (isOurs(x)) x.remove();
    }

    String describe() {
        return "spawned=" + spawned + " elites=" + elites + " slain=" + slain + " despawned=" + despawned + " mimicsRevealed=" + transformed + " vanillaRefused=" + refusedVanilla + " invadersTurnedAway=" + invadersTurnedAway;
    }

    /** One of a kind next to a player (owner test tool). */
    LivingEntity test(Kind k, Player p) {
        Location l = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4));
        Level lv = Level.at(l.getX(), l.getZ());
        return spawn(k, l, lv == null ? 0.5 : lv.danger(l.getX(), l.getZ()), false);
    }

    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Kind k : Kind.values()) out.add(k.name().toLowerCase(java.util.Locale.ROOT));
        return out;
    }

}
