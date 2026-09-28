package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Monster;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * NetherEx mobs on vanilla bases with plugin-driven AI and abilities, the NetherEx per-region spawn lists, elite
 * variants, and the owner's difficulty rules (1.5x monster cap, +25% health and damage for Nether hostiles).
 * Identity is persistent (scoreboard tags "jn_mob", "jn_<kind>", "jn_elite", "jn_stage_N", "jn_home_x_y_z");
 * per-tick work is bounded (round-robin, capped count).
 */
final class Mobs implements Listener {
    static final class Spec {
        final String kind, name; final EntityType base; final double hp, damage, speed, follow, armor, kb;
        final boolean fireImmune, freezeImmune, infestImmune, hostile, neutral; final String elite;
        Spec(String kind, String name, EntityType base, double hp, double damage, double speed, double follow, double armor, double kb,
             boolean fireImmune, boolean freezeImmune, boolean infestImmune, boolean hostile, boolean neutral, String elite) {
            this.kind = kind; this.name = name; this.base = base; this.hp = hp; this.damage = damage; this.speed = speed; this.follow = follow;
            this.armor = armor; this.kb = kb; this.fireImmune = fireImmune; this.freezeImmune = freezeImmune; this.infestImmune = infestImmune;
            this.hostile = hostile; this.neutral = neutral; this.elite = elite;
        }
    }

    static final Map<String, Spec> KINDS = new LinkedHashMap<>();
    private static void spec(Spec s) { KINDS.put(s.kind, s); }
    static {
        //    kind              name               base                    hp    dmg  speed  follow armor kb  fireImm freezeImm infestImm hostile neutral elite
        spec(new Spec("wight", "Wight", EntityType.STRAY, 10, 3, 0.25, 32, 0, 0, true, true, false, true, false, "Wight Lord"));
        spec(new Spec("spinout", "Spinout", EntityType.VINDICATOR, 16, 4, 0.35, 32, 0, 0, true, true, false, true, false, "Spinout Dervish"));
        spec(new Spec("salamander", "Salamander", EntityType.MAGMA_CUBE, 12, 8, 0.2, 32, 0, 0.2, true, false, false, true, true, "Magma Salamander"));
        spec(new Spec("ember", "Ember", EntityType.MAGMA_CUBE, 1, 1.5, 0.35, 64, 0, 0, true, true, false, true, false, null));
        spec(new Spec("mogus", "Mogus", EntityType.MUSHROOM_COW, 2, 3, 0.35, 32, 0, 0, true, false, true, true, true, null));
        spec(new Spec("spore", "Spore", EntityType.SLIME, 8, 0, 0.0, 16, 0, 0, true, true, true, true, false, null));
        spec(new Spec("spore_creeper", "Spore Creeper", EntityType.CREEPER, 20, 0, 0.25, 16, 0, 0, true, false, true, true, false, "Spore Colossus"));
        spec(new Spec("coolmar_spider", "Coolmar Spider", EntityType.SPIDER, 20, 2.5, 0.275, 16, 0, 0, true, true, false, true, false, null));
        spec(new Spec("frost", "Frost", EntityType.BLAZE, 20, 6, 0.23, 48, 0, 0, false, true, false, true, false, null));
        spec(new Spec("brute", "Brute", EntityType.POLAR_BEAR, 32, 2, 0.25, 64, 2.5, 1.0, true, true, false, true, false, "Brute Juggernaut"));
        spec(new Spec("nethermite", "Nethermite", EntityType.ENDERMITE, 16, 7.5, 0.25, 16, 0, 0, true, false, false, true, false, null));
        spec(new Spec("ghastling", "Ghastling", EntityType.GHAST, 20, 6, 0, 100, 0, 0, true, true, false, true, false, null));
        spec(new Spec("gold_golem", "Gold Golem", EntityType.IRON_GOLEM, 100, 7, 0.25, 16, 0, 1.0, true, false, false, false, false, null));
        spec(new Spec("pigtificate", "Pigtificate", EntityType.VILLAGER, 20, 0, 0.5, 16, 0, 0, true, false, false, false, false, null));
        spec(new Spec("ghast_queen", "Ghast Queen", EntityType.GHAST, 140, 24, 0, 256, 0, 0, true, true, false, true, false, null));
        // vanilla ghasts of Hell regions can roll the Hell elite
        spec(new Spec("ghast", "Ghast", EntityType.GHAST, 10, 6, 0, 100, 0, 0, true, true, false, true, false, "Wailing Ghast"));
    }
    static int netherExKinds() { return KINDS.size() - 1; }

    static final class T {
        final LivingEntity e; final Spec spec; final boolean elite; final long born;
        long cooldown, lastSeen, angryUntil, stageSince, spinPhase; int stage, shots; UUID angerAt;
        boolean charging, offset; double dx, dy, dz, lastX, lastZ; final Set<UUID> hit = new HashSet<>();
        boolean home; double hx, hy, hz;
        T(LivingEntity e, Spec spec, boolean elite, long born) { this.e = e; this.spec = spec; this.elite = elite; this.born = born; }
    }

    private final NetherPlugin plugin;
    private final Map<UUID, T> tracked = new LinkedHashMap<>();
    private final List<UUID> order = new ArrayList<>();
    private int cursor;
    private final Random random = new Random();
    private long now;
    private boolean spawning, dealing;
    long spawnedTotal, replaced, elites, abilityUses, peaceKept;
    final Map<String, Integer> abilityCounts = new java.util.TreeMap<>();
    private final Map<Long, int[]> packs = new HashMap<>();
    private final List<LivingEntity> scaleQueue = new ArrayList<>();
    final int maxTracked;
    final double hpMult, dmgMult, eliteChance;

    Mobs(NetherPlugin plugin) {
        this.plugin = plugin;
        maxTracked = plugin.getConfig().getInt("mobs.max-tracked", 250);
        hpMult = plugin.getConfig().getDouble("difficulty.health-multiplier", 1.25);
        dmgMult = plugin.getConfig().getDouble("difficulty.damage-multiplier", 1.25);
        eliteChance = plugin.getConfig().getDouble("difficulty.elite-chance", 0.06);
    }

    int tracked() { return tracked.size(); }
    void ability(String what) { abilityUses++; abilityCounts.merge(what, 1, Integer::sum); }

    String kind(Entity e) { T t = tracked.get(e.getUniqueId()); if (t != null) return t.spec.kind; for (String s : e.getScoreboardTags()) if (s.startsWith("jn_k_")) return s.substring(5); return null; }
    T track(Entity e) { return e == null ? null : tracked.get(e.getUniqueId()); }
    boolean freezeImmune(LivingEntity e) {
        T t = track(e);
        if (t != null) return t.spec.freezeImmune;
        switch (e.getType()) { case BLAZE: case GHAST: case WITHER_SKELETON: case POLAR_BEAR: return true; default: return false; }
    }
    boolean infestImmune(LivingEntity e) { T t = track(e); return t != null && t.spec.infestImmune; }

    // ---- spawning --------------------------------------------------------------------------------------------------
    LivingEntity spawn(String kind, Location at, boolean elite) {
        Spec s = KINDS.get(kind);
        if (s == null || at.getWorld() == null) return null;
        if (tracked.size() >= maxTracked && s.hostile && !kind.equals("ghast_queen")) return null;
        Entity raw;
        spawning = true;
        try { raw = at.getWorld().spawnEntity(at, s.base); } finally { spawning = false; }
        if (!(raw instanceof LivingEntity)) { if (raw != null) raw.remove(); return null; }
        LivingEntity e = (LivingEntity) raw;
        configure(e, s, elite, null);
        spawnedTotal++;
        return e;
    }

    LivingEntity spawnSpore(Location at, int stage) {
        LivingEntity e = spawn("spore", at, false);
        if (e != null) { T t = track(e); if (t != null) { t.stage = stage; setStage(t, stage); } }
        return e;
    }

    private void configure(LivingEntity e, Spec s, boolean elite, String variant) {
        e.addScoreboardTag("jn_mob");
        e.addScoreboardTag("jn_k_" + s.kind);
        if (elite) e.addScoreboardTag("jn_elite");
        String name = s.name;
        switch (s.kind) {
            case "salamander": variant = variant != null ? variant : (random.nextInt(10) != 0 ? "Orange" : "Black"); name = variant + " Salamander";
                ((MagmaCube) e).setSize(2); break;
            case "ember": ((MagmaCube) e).setSize(1); break;
            case "mogus": {
                variant = variant != null ? variant : (random.nextInt(10) > 1 ? (random.nextBoolean() ? "Brown" : "Red") : "White");
                name = variant + " Mogus";
                MushroomCow c = (MushroomCow) e; c.setBaby(); c.setAgeLock(true); break;
            }
            case "spore": { Slime sl = (Slime) e; sl.setSize(1); sl.setAI(false); break; }
            case "spore_creeper": { Creeper c = (Creeper) e; c.setExplosionRadius(elite ? 3 : 2); c.setMaxFuseTicks(30); break; }
            case "wight": case "spinout": e.getEquipment().setItemInMainHand(new ItemStack(Material.AIR)); e.getEquipment().setItemInMainHandDropChance(0f); break;
            case "gold_golem": ((IronGolem) e).setPlayerCreated(false); break;
            case "pigtificate": Trades.setup(this, (Villager) e, random); name = e.getCustomName() != null ? e.getCustomName() : name; break;
            case "ghast": case "ghastling": case "ghast_queen": break;
            default:
        }
        if (elite && s.elite != null) name = s.elite;
        if (variant != null) e.addScoreboardTag("jn_v_" + variant);
        e.setCustomName((elite ? ChatColor.RED : ChatColor.RESET) + name);
        e.setCustomNameVisible(false);
        attr(e, Attribute.GENERIC_MAX_HEALTH, s.hp * (elite ? 2 : 1));
        if (s.follow > 0) attr(e, Attribute.GENERIC_FOLLOW_RANGE, s.follow);
        if (s.speed > 0 && s.base != EntityType.GHAST) attr(e, Attribute.GENERIC_MOVEMENT_SPEED, s.speed);
        if (s.armor > 0) attr(e, Attribute.GENERIC_ARMOR, s.armor + (elite ? 6 : 0));
        if (s.kb > 0) attr(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, s.kb);
        if (s.hostile) scale(e);
        e.setHealth(e.getMaxHealth());
        T t = new T(e, s, elite, now);
        t.stageSince = now;
        if (elite) elites++;
        put(t);
    }

    private void put(T t) {
        if (tracked.put(t.e.getUniqueId(), t) == null) order.add(t.e.getUniqueId());
        for (String tag : t.e.getScoreboardTags()) {
            if (tag.startsWith("jn_stage_")) t.stage = Integer.parseInt(tag.substring(9));
            if (tag.startsWith("jn_home_")) {
                String[] p = tag.substring(8).split("_");
                t.home = true; t.hx = Integer.parseInt(p[0]) + 0.5; t.hy = Integer.parseInt(p[1]); t.hz = Integer.parseInt(p[2]) + 0.5;
            }
        }
    }

    static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    /** +25% max health for Nether hostiles (once; tag jn_scaled marks damage scaling too). */
    void scale(LivingEntity e) {
        if (e.getScoreboardTags().contains("jn_scaled")) return;
        e.addScoreboardTag("jn_scaled");
        AttributeInstance i = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (i != null) { i.setBaseValue(i.getBaseValue() * hpMult); e.setHealth(i.getValue()); }
    }

    void setStage(T t, int stage) {
        for (String tag : new ArrayList<>(t.e.getScoreboardTags())) if (tag.startsWith("jn_stage_")) t.e.removeScoreboardTag(tag);
        t.e.addScoreboardTag("jn_stage_" + stage);
        t.stage = stage;
        t.stageSince = now;
        if (t.e instanceof Slime) ((Slime) t.e).setSize(stage < 2 ? 1 : stage < 4 ? 2 : 3);
        attr(t.e, Attribute.GENERIC_MAX_HEALTH, t.spec.hp * hpMult);
    }

    void setHome(LivingEntity e, int x, int y, int z) {
        e.addScoreboardTag("jn_home_" + x + "_" + y + "_" + z);
        T t = track(e);
        if (t != null) { t.home = true; t.hx = x + 0.5; t.hy = y; t.hz = z + 0.5; }
    }

    /** Re-adopts tagged mobs after chunk loads and restarts. */
    void adopt(Entity raw) {
        if (!(raw instanceof LivingEntity) || raw.isDead() || tracked.containsKey(raw.getUniqueId())) return;
        LivingEntity e = (LivingEntity) raw;
        if (!e.getScoreboardTags().contains("jn_mob")) return;
        String k = kind(e);
        Spec s = k == null ? null : KINDS.get(k);
        if (s == null) return;
        plugin.effects.repair(e);
        T t = new T(e, s, e.getScoreboardTags().contains("jn_elite"), now);
        t.stageSince = now;
        put(t);
        if (s.kind.equals("ghast_queen")) plugin.boss.adopt(e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!plugin.isNether(e.getWorld())) return;
        for (Entity en : e.getChunk().getEntities()) adopt(en);
    }

    void slowTick() {
        packs.clear();
        if (plugin.nether == null) return;
        if (tracked.size() < maxTracked) for (Chunk c : plugin.nether.getLoadedChunks()) {
            for (Entity en : c.getEntities()) if (en.getScoreboardTags().contains("jn_mob")) adopt(en);
        }
    }

    void shutdown() {
        for (T t : tracked.values()) if (t.e.isValid()) plugin.effects.clear(t.e);
    }

    // ---- natural spawning: NetherEx per-region lists ------------------------------------------------------------------
    private static final Object[][] RUTHLESS = {{"wither_skeleton", 65, 1, 4}, {"zombie_pigman", 45, 1, 4}, {"spinout", 100, 1, 4}};
    private static final Object[][] TORRID = {{"magma_cube", 30, 1, 4}, {"ember", 25, 1, 3}, {"salamander", 100, 1, 2}};
    private static final Object[][] FUNGI = {{"mogus", 100, 4, 6}, {"spore", 25, 1, 4}, {"spore_creeper", 50, 1, 4}};
    private static final Object[][] ARCTIC = {{"frost", 10, 1, 3}, {"coolmar_spider", 35, 1, 4}, {"wight", 100, 1, 4}, {"brute", 15, 1, 1}};

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (spawning || !plugin.isNether(e.getLocation().getWorld()) || plugin.gen == null) return;
        LivingEntity en = e.getEntity();
        boolean monster = en instanceof Monster || en instanceof Ghast || en instanceof Slime;
        if (!monster) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) queueScale(en);
            return;
        }
        EntityType type = en.getType();
        Location l = e.getLocation();
        if (plugin.gen.peaceful(l.getBlockX(), l.getBlockZ(), type == EntityType.GHAST)) { e.setCancelled(true); peaceKept++; return; }
        Biomes.Nex region = plugin.gen.biomes.nex(l.getBlockX(), l.getBlockZ());
        if (region == Biomes.Nex.HELL || type == EntityType.BLAZE || type == EntityType.WITHER_SKELETON || type == EntityType.SKELETON) {
            queueScale(en);
            if (type == EntityType.GHAST && region == Biomes.Nex.HELL && random.nextDouble() < eliteChance) {
                e.setCancelled(true);
                LivingEntity g = spawn("ghast", l, true);
                if (g != null) replaced++;
            }
            return;
        }
        Object[][] list = region == Biomes.Nex.RUTHLESS_SANDS ? RUTHLESS : region == Biomes.Nex.TORRID_WASTELAND ? TORRID
            : region == Biomes.Nex.FUNGI_FOREST ? FUNGI : ARCTIC;
        long key = (((long) (l.getBlockX() >> 4) << 32) ^ ((l.getBlockZ() >> 4) & 0xffffffffL)) * 31 + l.getWorld().getFullTime();
        int[] pack = packs.get(key);
        if (pack == null) {
            int total = 0;
            for (Object[] o : list) total += (Integer) o[1];
            int r = random.nextInt(total), pick = 0;
            for (int i = 0; i < list.length; i++) { r -= (Integer) list[i][1]; if (r < 0) { pick = i; break; } }
            int min = (Integer) list[pick][2], max = (Integer) list[pick][3];
            pack = new int[]{pick, 0, min + random.nextInt(max - min + 1)};
            if (packs.size() > 512) packs.clear();
            packs.put(key, pack);
        }
        e.setCancelled(true);
        if (pack[1] >= pack[2]) return;
        pack[1]++;
        String kind = (String) list[pack[0]][0];
        if (type == EntityType.GHAST) l = ground(l);
        if (l == null) return;
        if (KINDS.containsKey(kind)) {
            Spec s = KINDS.get(kind);
            if (nearbyTracked(l, 48) >= 40) return;
            spawn(kind, l, s.elite != null && random.nextDouble() < eliteChance);
        } else {
            EntityType vt = kind.equals("wither_skeleton") ? EntityType.WITHER_SKELETON : kind.equals("zombie_pigman") ? EntityType.PIG_ZOMBIE : EntityType.MAGMA_CUBE;
            spawning = true;
            try { Entity v = l.getWorld().spawnEntity(l, vt); if (v instanceof LivingEntity) scale((LivingEntity) v); } finally { spawning = false; }
        }
        replaced++;
    }

    private Location ground(Location l) {
        Block b = l.getBlock();
        for (int i = 0; i < 24 && b.getY() > 2; i++, b = b.getRelative(0, -1, 0)) {
            if (b.getType().isSolid() && !b.getRelative(0, 1, 0).getType().isSolid() && !b.getRelative(0, 2, 0).getType().isSolid())
                return b.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    private int nearbyTracked(Location l, double r) {
        int n = 0;
        for (T t : tracked.values()) if (t.e.getWorld() == l.getWorld() && t.e.getLocation().distanceSquared(l) < r * r) n++;
        return n;
    }

    private void queueScale(LivingEntity e) { if (scaleQueue.size() < 256) scaleQueue.add(e); }

    // ---- per-tick AI (bounded round robin) ---------------------------------------------------------------------------
    void tick(long ticks) {
        now = ticks;
        if (!scaleQueue.isEmpty()) { for (LivingEntity e : scaleQueue) if (e.isValid()) scale(e); scaleQueue.clear(); }
        if (order.isEmpty()) return;
        int budget = Math.min(order.size(), 40);
        for (int n = 0; n < budget; n++) {
            if (order.isEmpty()) break;
            if (cursor >= order.size()) cursor = 0;
            UUID id = order.get(cursor);
            T t = tracked.get(id);
            if (t == null || !t.e.isValid() || t.e.isDead()) {
                tracked.remove(id);
                order.remove(cursor);
                continue;
            }
            cursor++;
            try { think(t); } catch (RuntimeException ex) {
                if (plugin.warnings++ < 40) plugin.getLogger().warning("NETHER_MOB_AI_FAILED kind=" + t.spec.kind + " reason=" + ex.getClass().getSimpleName() + " at=" + NetherPlugin.where(ex));
            }
        }
    }

    private boolean frozen(LivingEntity e) { return plugin.effects.has(e, Effects.Kind.FROZEN); }

    private void think(T t) {
        LivingEntity e = t.e;
        if (frozen(e)) return;
        switch (t.spec.kind) {
            case "wight": {
                LivingEntity target = e instanceof Creature ? ((Creature) e).getTarget() : null;
                if (target != null && frozen(target)) ((Creature) e).setTarget(null);
                if (t.elite && (now % 20) == 0) e.getWorld().spawnParticle(Particle.SNOW_SHOVEL, e.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.01);
                break;
            }
            case "spinout": spinout(t); break;
            case "salamander": case "mogus": neutral(t); break;
            case "ember":
                if ((now % 4) == 0) e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 0.3, 0), 1, 0.15, 0.15, 0.15, 0.01);
                if (e.getLocation().getBlock().getType() == Material.WATER || e.getLocation().getBlock().getType() == Material.STATIONARY_WATER) hurt(e, null, 1.0);
                break;
            case "spore": spore(t); break;
            case "spore_creeper": {
                Creeper c = (Creeper) e;
                if (c.getTarget() == null && (now % 40) == 0) {
                    for (Entity n : e.getNearbyEntities(12, 6, 12)) if (n.getType() == EntityType.PIG_ZOMBIE) { c.setTarget((LivingEntity) n); break; }
                }
                break;
            }
            case "frost": {
                Material m = e.getLocation().getBlock().getType();
                if ((m == Material.LAVA || m == Material.STATIONARY_LAVA || m == Material.FIRE) && (now % 10) == 0) e.damage(1.0);
                if ((now % 6) == 0) e.getWorld().spawnParticle(Particle.SNOW_SHOVEL, e.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.0);
                break;
            }
            case "brute": brute(t); break;
            case "gold_golem": case "pigtificate": leash(t); break;
            default:
        }
    }

    /** NetherEx Spinout: 6 s spinning (moves, attacks, projectile-immune) then 2 s at rest; the Dervish rests 1 s. */
    boolean spinning(T t) {
        int active = t.elite ? 160 : 120, rest = t.elite ? 20 : 40;
        return ((now - t.born) % (active + rest)) < active;
    }

    private void spinout(T t) {
        LivingEntity e = t.e;
        boolean spin = spinning(t);
        attr(e, Attribute.GENERIC_MOVEMENT_SPEED, spin ? t.spec.speed * (t.elite ? 1.25 : 1) : 0.0);
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
        if (spin) {
            float yaw = h.yaw + 60f;
            h.yaw = yaw;
            h.setHeadRotation(yaw);
            if (h instanceof net.minecraft.server.v1_12_R1.EntityLiving) ((net.minecraft.server.v1_12_R1.EntityLiving) h).aN = yaw;
            if ((now % 4) == 0) e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 1, 0.3, 0.2, 0.3, 0);
            if (t.elite && (now - t.cooldown) >= 10) {
                t.cooldown = now;
                for (Entity n : e.getNearbyEntities(2, 1.5, 2)) if (n instanceof Player && !((Player) n).isDead()) { hurt((LivingEntity) n, e, 3 * dmgMult); ability("spinout_dervish_whirl"); }
            }
        } else {
            e.setSilent(true);
            if (h instanceof net.minecraft.server.v1_12_R1.EntityInsentient) ((net.minecraft.server.v1_12_R1.EntityInsentient) h).getNavigation().p();
        }
        if (spin && e.isSilent() && !frozen(e)) e.setSilent(false);
    }

    private void neutral(T t) {
        LivingEntity e = t.e;
        if (t.angryUntil <= now || t.angerAt == null) return;
        Entity target = Bukkit.getEntity(t.angerAt);
        if (!(target instanceof LivingEntity) || target.isDead() || target.getWorld() != e.getWorld() || target.getLocation().distanceSquared(e.getLocation()) > 32 * 32) { t.angryUntil = 0; return; }
        if (t.spec.kind.equals("mogus")) {
            net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
            if (h instanceof net.minecraft.server.v1_12_R1.EntityInsentient)
                ((net.minecraft.server.v1_12_R1.EntityInsentient) h).getNavigation().a(((CraftEntity) target).getHandle(), 1.45);
            if (target.getLocation().distanceSquared(e.getLocation()) < 2.6 && now - t.cooldown >= 20) {
                t.cooldown = now;
                hurt((LivingEntity) target, e, t.spec.damage * dmgMult);
                ability("mogus_bite");
            }
        } else if (e instanceof Creature) {
            ((Creature) e).setTarget((LivingEntity) target);
        } else if (e instanceof Slime) {
            net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
            if (h instanceof net.minecraft.server.v1_12_R1.EntityInsentient)
                ((net.minecraft.server.v1_12_R1.EntityInsentient) h).setGoalTarget((net.minecraft.server.v1_12_R1.EntityLiving) ((CraftEntity) target).getHandle(),
                    org.bukkit.event.entity.EntityTargetEvent.TargetReason.CUSTOM, false);
        }
    }

    private void anger(T t, LivingEntity at) {
        t.angryUntil = now + 400;
        t.angerAt = at.getUniqueId();
        for (Entity n : t.e.getNearbyEntities(16, 6, 16)) {
            T o = track(n);
            if (o != null && o.spec.kind.equals(t.spec.kind)) { o.angryUntil = now + 400; o.angerAt = at.getUniqueId(); }
        }
    }

    /** NetherEx Spore: grows 0->4 (1200/600/400/300 ticks), then bursts into 1-3 Spore Creepers when a player is near. */
    private void spore(T t) {
        LivingEntity e = t.e;
        if (t.stage < 4) {
            long need = (60 / (t.stage + 1)) * 20L;
            if (now - t.stageSince >= need) { setStage(t, t.stage + 1); ability("spore_grow"); }
            return;
        }
        for (Entity n : e.getNearbyEntities(2.5, 2.5, 2.5)) {
            if (n instanceof Player && ((Player) n).getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                Location l = e.getLocation();
                e.remove();
                int k = random.nextInt(3) + 1;
                for (int i = 0; i < k; i++) spawn("spore_creeper", l, false);
                l.getWorld().playSound(l, Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
                l.getWorld().spawnParticle(Particle.SPELL_MOB, l.add(0, 0.5, 0), 0, 0.55, 0.37, 0.16, 1);
                ability("spore_burst");
                return;
            }
        }
    }

    /** NetherEx Brute: charges at the player's position, overshoots 4 blocks, launches whatever it runs into. */
    private void brute(T t) {
        LivingEntity e = t.e;
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
        if (!(h instanceof net.minecraft.server.v1_12_R1.EntityInsentient)) return;
        net.minecraft.server.v1_12_R1.EntityInsentient ins = (net.minecraft.server.v1_12_R1.EntityInsentient) h;
        Location l = e.getLocation();
        if (t.charging) {
            boolean stuck = Math.abs(l.getX() - t.lastX) < 0.02 && Math.abs(l.getZ() - t.lastZ) < 0.02 && now - t.cooldown > 10;
            t.lastX = l.getX(); t.lastZ = l.getZ();
            double d2 = (l.getX() - t.dx) * (l.getX() - t.dx) + (l.getZ() - t.dz) * (l.getZ() - t.dz);
            if (d2 <= 4 && !t.offset) {
                Vector f = l.getDirection().setY(0);
                if (f.lengthSquared() < 1e-4) f = new Vector(1, 0, 0);
                f.normalize().multiply(4);
                t.dx += f.getX(); t.dz += f.getZ(); t.offset = true;
            } else if ((d2 <= 1.5 && t.offset) || stuck) {
                t.charging = false; t.offset = false; t.hit.clear();
                t.cooldown = now + (t.elite ? 30 : 40);
                ins.getNavigation().p();
                return;
            }
            ins.getNavigation().a(t.dx, t.dy, t.dz, 2.5);
            for (Entity n : e.getNearbyEntities(0.9, 1.0, 0.9)) {
                if (!(n instanceof LivingEntity) || n instanceof org.bukkit.entity.PolarBear || !t.hit.add(n.getUniqueId())) continue;
                if (n instanceof Player && ((Player) n).getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                hurt((LivingEntity) n, e, (t.elite ? 8 : t.spec.damage) * dmgMult);
                Vector v = n.getVelocity().add(l.getDirection().setY(0).normalize().multiply(t.elite ? 1.0 : 0.4));
                v.setY(random.nextBoolean() ? 0.45 : 0.7);
                n.setVelocity(v);
                ability(t.elite ? "brute_juggernaut_charge_hit" : "brute_charge_hit");
            }
            return;
        }
        if (now < t.cooldown) return;
        Player best = null;
        double bd = 32 * 32;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(l);
            if (d < bd && e.hasLineOfSight(p)) { bd = d; best = p; }
        }
        if (best == null) return;
        Location d = best.getLocation();
        t.dx = d.getBlockX() + 0.5; t.dy = d.getBlockY(); t.dz = d.getBlockZ() + 0.5;
        t.charging = true; t.offset = false; t.hit.clear(); t.cooldown = now; t.lastX = l.getX(); t.lastZ = l.getZ();
        Vector look = d.toVector().subtract(l.toVector()).setY(0);
        if (look.lengthSquared() > 1e-4) { l.setDirection(look); h.yaw = l.getYaw(); h.setHeadRotation(l.getYaw()); }
        e.getWorld().playSound(l, Sound.ENTITY_POLAR_BEAR_WARNING, 1f, 0.8f);
        ability("brute_charge");
    }

    private void leash(T t) {
        if (!t.home || (now % 20) != 0) return;
        Location l = t.e.getLocation();
        double d2 = (l.getX() - t.hx) * (l.getX() - t.hx) + (l.getZ() - t.hz) * (l.getZ() - t.hz);
        if (d2 < 20 * 20) return;
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) t.e).getHandle();
        if (h instanceof net.minecraft.server.v1_12_R1.EntityInsentient) ((net.minecraft.server.v1_12_R1.EntityInsentient) h).getNavigation().a(t.hx, t.hy, t.hz, 0.6);
        if (d2 > 64 * 64) t.e.teleport(new Location(t.e.getWorld(), t.hx, t.hy, t.hz));
    }

    /** Ability damage (already scaled by the caller); the damage listener leaves it alone. */
    void hurt(LivingEntity target, LivingEntity source, double amount) {
        dealing = true;
        try { if (source == null) target.damage(amount); else target.damage(amount, source); } finally { dealing = false; }
    }

    // ---- damage, targeting, projectiles, explosions ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent e) {
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c != EntityDamageEvent.DamageCause.FIRE && c != EntityDamageEvent.DamageCause.FIRE_TICK && c != EntityDamageEvent.DamageCause.LAVA
            && c != EntityDamageEvent.DamageCause.HOT_FLOOR) return;
        T t = track(e.getEntity());
        if (t != null && t.spec.fireImmune) { e.setCancelled(true); e.getEntity().setFireTicks(0); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity(), damager = e.getDamager();
        LivingEntity src = damager instanceof LivingEntity ? (LivingEntity) damager
            : damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof LivingEntity ? (LivingEntity) ((Projectile) damager).getShooter() : null;
        T v = track(victim);
        if (v != null) {
            if (v.spec.kind.equals("spinout") && damager instanceof Projectile && spinning(v)) { e.setCancelled(true); ability("spinout_projectile_immune"); return; }
            if ((v.spec.kind.equals("spore") || v.spec.kind.equals("spore_creeper")) && src instanceof Player) {
                String held = Items.id(((Player) src).getInventory().getItemInMainHand());
                if ("withered_amedian_sword".equals(held)) e.setDamage(e.getDamage() * 2);
            }
            if (v.spec.neutral && src != null && !(track(src) != null && track(src).spec.kind.equals(v.spec.kind))) anger(v, src);
        }
        if (src == null || dealing) return;
        T s = track(src);
        double base = e.getDamage();
        if (s != null) {
            if (s.spec.kind.equals("spore") || (s.spec.kind.equals("brute") && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)) { e.setCancelled(true); return; }
            if (s.spec.neutral && victim instanceof Player && !(s.angryUntil > now && victim.getUniqueId().equals(s.angerAt))) { e.setCancelled(true); return; }
            if (frozen(src)) { e.setCancelled(true); return; }
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && s.spec.damage > 0) {
                base = s.spec.kind.equals("gold_golem") ? 7 + random.nextInt(15) : s.spec.damage;
                if (s.elite) base *= 1.5;
                onHit(s, victim);
            }
            if (damager instanceof LargeFireball && damager.hasMetadata("jn_queen") && e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) base = 24;
        }
        if (src.getScoreboardTags().contains("jn_scaled") && plugin.isNether(src.getWorld())) e.setDamage(base * dmgMult);
        else if (s != null) e.setDamage(base);
    }

    private void onHit(T s, Entity victim) {
        if (!(victim instanceof LivingEntity)) return;
        LivingEntity v = (LivingEntity) victim;
        switch (s.spec.kind) {
            case "wight":
                if (!frozen(v)) { plugin.effects.apply(v, Effects.Kind.FROZEN, 160); ability("wight_freeze"); }
                if (s.elite) for (Entity n : s.e.getNearbyEntities(4, 2, 4)) if (n instanceof Player && n != v) { plugin.effects.apply((Player) n, Effects.Kind.FROZEN, 60); ability("wight_lord_frost_nova"); }
                break;
            case "coolmar_spider":
                if (v instanceof Player) { plugin.effects.apply(v, Effects.Kind.FROSTBITTEN, 320); ability("coolmar_frostbite"); }
                break;
            case "salamander": {
                boolean black = s.e.getScoreboardTags().contains("jn_v_Black") || s.elite;
                v.setFireTicks(Math.max(v.getFireTicks(), (black ? 8 : 4) * 20));
                if (s.elite) plugin.effects.apply(v, Effects.Kind.FIRE_BURNING, 60);
                ability("salamander_ignite");
                break;
            }
            case "ember":
                if (random.nextInt(2) == 0) { v.setFireTicks(Math.max(v.getFireTicks(), 80)); ability("ember_ignite"); }
                break;
            default:
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        LivingEntity target = e.getTarget();
        T t = track(e.getEntity());
        if (t != null) {
            if (frozen(t.e)) { e.setCancelled(true); return; }
            if (t.spec.kind.equals("brute")) { e.setCancelled(true); return; }
            if (t.spec.neutral && target != null && !(t.angryUntil > now && target.getUniqueId().equals(t.angerAt))) { e.setCancelled(true); return; }
        }
        if (target instanceof Player && (e.getEntity() instanceof Skeleton || e.getEntity() instanceof WitherSkeleton || e.getEntity() instanceof Stray)
            && (t == null || !t.spec.kind.equals("wight")) && plugin.mechanics.fullSet((Player) target, "wither_bone")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile p = e.getEntity();
        if (!(p.getShooter() instanceof LivingEntity)) return;
        T t = track((LivingEntity) p.getShooter());
        if (t == null) return;
        LivingEntity shooter = t.e;
        if (t.spec.kind.equals("frost") && p instanceof SmallFireball) {
            // The Frost keeps the blaze's volley cadence (charge, three shots, pause) but throws frost shards.
            e.setCancelled(true);
            Location eye = shooter.getEyeLocation();
            Vector dir = p.getVelocity().lengthSquared() > 1e-6 ? p.getVelocity().normalize() : eye.getDirection();
            dir.add(new Vector(random.nextGaussian() * 0.03, 0, random.nextGaussian() * 0.03)).normalize().multiply(1.3);
            Snowball s = shooter.launchProjectile(Snowball.class, dir);
            s.setMetadata("jn_frost", new FixedMetadataValue(plugin, Boolean.TRUE));
            shooter.getWorld().playSound(eye, Sound.ENTITY_SNOWMAN_SHOOT, 1f, 0.6f);
            ability("frost_volley_shot");
            return;
        }
        if (p instanceof LargeFireball) {
            if (t.spec.kind.equals("ghast_queen")) {
                p.setMetadata("jn_queen", new FixedMetadataValue(plugin, Boolean.TRUE));
                ((LargeFireball) p).setYield(3f);
                plugin.boss.onShot(t, (LargeFireball) p);
            } else if (t.spec.kind.equals("ghast") && t.elite && !spawning) {
                spawning = true;
                try {
                    for (int i = -1; i <= 1; i += 2) {
                        Vector d = rotY(p.getVelocity(), 0.18 * i);
                        LargeFireball f = shooter.launchProjectile(LargeFireball.class, d);
                        f.setDirection(rotY(((LargeFireball) p).getDirection(), 0.18 * i));
                    }
                } finally { spawning = false; }
                ability("wailing_ghast_triple_shot");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball) || !e.getEntity().hasMetadata("jn_frost")) return;
        Entity hit = e.getHitEntity();
        if (!(hit instanceof LivingEntity) || !(e.getEntity().getShooter() instanceof LivingEntity)) return;
        LivingEntity src = (LivingEntity) e.getEntity().getShooter();
        T t = track(src);
        if (hit == src || (hit instanceof Player && ((Player) hit).getGameMode() == org.bukkit.GameMode.CREATIVE)) return;
        hurt((LivingEntity) hit, src, 5 * dmgMult * (t != null && t.elite ? 1.5 : 1));
        plugin.effects.apply((LivingEntity) hit, Effects.Kind.FIRE_BURNING, 100);
        ability("frost_shard_hit");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent e) {
        if (!plugin.isNether(e.getLocation().getWorld())) return;
        T t = track(e.getEntity());
        if (e.getEntity() instanceof LargeFireball) return; // ghast fireballs keep vanilla block damage (owner rule)
        if (t == null && !(e.getEntity() instanceof Creeper)) return;
        e.blockList().clear();                           // no block damage from mob explosions
        if (t == null || !t.spec.kind.equals("spore_creeper")) return;
        Location l = e.getLocation();
        double radius = t.elite ? 6 : 4;
        int infested = 0;
        for (Entity n : l.getWorld().getNearbyEntities(l, radius, radius, radius)) {
            if (n instanceof LivingEntity && n != t.e && n.getLocation().distanceSquared(l) <= radius * radius) {
                plugin.effects.apply((LivingEntity) n, Effects.Kind.INFESTED, 2400);
                infested++;
            }
        }
        int spores = t.elite ? 2 + random.nextInt(4) : 1 + random.nextInt(3);
        for (int i = 0; i < spores; i++) {
            Block b = l.getBlock().getRelative(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
            if (b.getType() == Material.AIR && b.getRelative(0, -1, 0).getType().isSolid()) spawnSpore(b.getLocation().add(0.5, 0, 0.5), 0);
        }
        AreaEffectCloud cloud = (AreaEffectCloud) l.getWorld().spawnEntity(l, EntityType.AREA_EFFECT_CLOUD);
        cloud.setColor(Color.fromRGB(142, 96, 40));
        cloud.setRadius((float) radius * 0.7f);
        cloud.setDuration(60);
        cloud.setRadiusPerTick(-cloud.getRadius() / 60f);
        l.getWorld().playSound(l, Sound.ENTITY_SLIME_SQUISH, 2f, 0.5f);
        ability(t.elite ? "spore_colossus_burst" : "spore_creeper_burst");
        if (infested > 0) ability("spore_infest");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent e) { if (track(e.getEntity()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        T t = track(e.getRightClicked());
        if (t != null && (t.spec.kind.equals("mogus"))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { if (track(e.getEntity()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent e) {
        T t = track(e.getEntity());
        if (t == null || !t.spec.kind.equals("pigtificate")) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> Trades.levelUp(this, (Villager) t.e, random));
    }

    // ---- loot ------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity d = e.getEntity();
        T t = tracked.remove(d.getUniqueId());
        if (t == null) {
            if (!plugin.isNether(d.getWorld())) return;
            if (d.getType() == EntityType.GHAST && random.nextBoolean()) {
                e.getDrops().removeIf(s -> s.getType() == Material.GHAST_TEAR);
                e.getDrops().add(Items.create("ghast_meat_raw", 1 + random.nextInt(2)));
            } else if (d.getType() == EntityType.WITHER_SKELETON && random.nextBoolean()) {
                e.getDrops().removeIf(s -> s.getType() == Material.COAL);
                if (random.nextBoolean()) e.getDrops().add(Items.create("wither_bone", 1));
            }
            return;
        }
        List<ItemStack> drops = e.getDrops();
        drops.clear();
        boolean byPlayer = d.getKiller() != null;
        int looting = byPlayer ? d.getKiller().getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS) : 0;
        switch (t.spec.kind) {
            case "wight": addN(drops, "rime_crystal", random.nextInt(3) - 1 + random.nextInt(looting + 1)); break;
            case "spinout": drops.add(new ItemStack(Material.QUARTZ, 1 + random.nextInt(6))); break;
            case "salamander": drops.add(Items.create(d.getScoreboardTags().contains("jn_v_Black") ? "black_salamander_hide" : "orange_salamander_hide", 1)); break;
            case "mogus": {
                boolean brown = d.getScoreboardTags().contains("jn_v_Brown") || (d.getScoreboardTags().contains("jn_v_White") && random.nextBoolean());
                addN(drops, brown ? "brown_elder_mushroom" : "red_elder_mushroom", random.nextInt(3));
                break;
            }
            case "spore_creeper": addN(drops, "spore", random.nextInt(2)); break;
            case "coolmar_spider":
                if (random.nextInt(3) + random.nextInt(looting + 1) > 0) drops.add(new ItemStack(Material.BONE, random.nextInt(3) + random.nextInt(looting + 1)));
                if (byPlayer) addN(drops, "frost_fang", random.nextInt(3) - 1 + random.nextInt(looting + 1));
                break;
            case "frost": if (byPlayer) addN(drops, "frost_rod", random.nextInt(2) + random.nextInt(looting + 1)); break;
            case "brute": { int n = random.nextInt(3) - 1 + random.nextInt(looting + 1); if (n > 0) drops.add(new ItemStack(Material.IRON_NUGGET, n)); break; }
            case "ghastling": case "ghast": ghastLoot(drops, looting); break;
            case "gold_golem": drops.add(new ItemStack(Material.GOLD_INGOT, 3 + random.nextInt(3))); break;
            case "ghast_queen": plugin.boss.loot(t, drops, looting); break;
            default:
        }
        if (t.elite) { addN(drops, "amethyst_crystal", 1 + random.nextInt(3)); e.setDroppedExp(e.getDroppedExp() * 3 + 10); }
    }

    void ghastLoot(List<ItemStack> drops, int looting) {
        if (random.nextBoolean()) { int n = random.nextInt(2 + looting); if (n > 0) drops.add(new ItemStack(Material.GHAST_TEAR, n)); }
        else drops.add(Items.create("ghast_meat_raw", 1 + random.nextInt(2 + looting)));
        int g = random.nextInt(3 + looting);
        if (g > 0) drops.add(new ItemStack(Material.SULPHUR, g));
    }

    static Vector rotY(Vector v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vector(v.getX() * c + v.getZ() * s, v.getY(), -v.getX() * s + v.getZ() * c);
    }

    private static void addN(List<ItemStack> drops, String id, int n) { if (n > 0) drops.add(Items.create(id, n)); }

    Map<String, Integer> countsByKind() {
        Map<String, Integer> m = new java.util.TreeMap<>();
        for (T t : tracked.values()) m.merge(t.spec.kind + (t.elite ? "*" : ""), 1, Integer::sum);
        return m;
    }

    Iterable<T> all() { return tracked.values(); }
}
