package chat.jaspr.biomes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What is actually in the rooms.
 *
 * Two jobs, both of which happen the moment a spawner in one of these places fires.
 *
 * The first is the anomalies. A handful of sites are containment, and a plain zombie in
 * a containment cell is a disappointment, so a mob spawning inside one is re-dressed into
 * the thing that cell was built for: named, re-statted, given whatever equipment and
 * standing effects make it read correctly, and told not to despawn. None of this needs a
 * new entity type -- the whole point is that the behaviour people remember comes from
 * speed, reach, toughness and being unable to see the thing, all of which vanilla already
 * has attributes for.
 *
 * The second is the bosses. Every site in the boss table has one room that is further in
 * than any other, and the first thing that spawns in that room is promoted: a great deal
 * of health, a weapon, resistance to being knocked about, and a name. One per site per
 * run of the server, so killing it means something.
 */
public final class Containment implements Listener {

    /** A dressed mob: what it is, what it is called, and what it is like to meet. */
    private static final class Spec {
        final EntityType type;
        final String name;
        final double health, speed, damage, knockback, follow;
        final int helmet, weapon, size;
        final PotionEffectType[] effects;
        Spec(EntityType type, String name, double health, double speed, double damage,
             double knockback, double follow, int helmet, int weapon, int size, PotionEffectType... effects) {
            this.type = type; this.name = name; this.health = health; this.speed = speed;
            this.damage = damage; this.knockback = knockback; this.follow = follow;
            this.helmet = helmet; this.weapon = weapon; this.size = size; this.effects = effects;
        }
    }

    // -- the anomalies. Site name -> what the cells in it hold. --------------------
    private static final Map<String, Spec[]> ANOMALIES = new HashMap<String, Spec[]>();
    // -- the bosses. Site name -> {offsetX, offsetY, offsetZ} and the thing itself. -
    private static final Map<String, int[]> BOSS_AT = new HashMap<String, int[]>();
    private static final Map<String, Spec> BOSS = new HashMap<String, Spec>();

    private static void anomaly(String site, Spec... specs) { ANOMALIES.put(site, specs); }
    private static void boss(String site, int dx, int dy, int dz, Spec spec) {
        BOSS_AT.put(site, new int[]{dx, dy, dz});
        BOSS.put(site, spec);
    }

    static {
        // Containment. Everything that spawns in these takes one of the site's own forms.
        anomaly("Site-19",
            new Spec(EntityType.HUSK, "SCP-173", 60, 0.46, 7, 1.0, 42, 1, 0, 0),
            new Spec(EntityType.ZOMBIE_VILLAGER, "SCP-049", 80, 0.20, 6, 0.6, 34, 3, 283, 0, PotionEffectType.SLOW),
            new Spec(EntityType.MAGMA_CUBE, "SCP-682", 150, 0.24, 9, 0.9, 40, 0, 0, 4, PotionEffectType.FIRE_RESISTANCE));
        anomaly("The Viewing Room",
            new Spec(EntityType.HUSK, "SCP-096", 45, 0.58, 11, 0.4, 64, 0, 0, 0, PotionEffectType.SPEED));
        anomaly("The Warren",
            new Spec(EntityType.STRAY, "SCP-966", 34, 0.34, 5, 0.2, 40, 0, 261, 0,
                     PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION));
        anomaly("The Kennels",
            new Spec(EntityType.CAVE_SPIDER, "SCP-939", 40, 0.42, 7, 0.3, 48, 0, 0, 0, PotionEffectType.SPEED));
        anomaly("The Pit",
            new Spec(EntityType.MAGMA_CUBE, "SCP-682", 120, 0.26, 8, 0.9, 40, 0, 0, 3, PotionEffectType.FIRE_RESISTANCE),
            new Spec(EntityType.BLAZE, "SCP-457", 40, 0.30, 6, 0.2, 40, 0, 0, 0, PotionEffectType.FIRE_RESISTANCE));
        anomaly("The Signal",
            new Spec(EntityType.WOLF, "SCP-1471", 40, 0.42, 6, 0.3, 48, 0, 0, 0, PotionEffectType.SPEED));
        anomaly("The Corroded Place",
            new Spec(EntityType.HUSK, "SCP-106", 140, 0.16, 8, 1.0, 44, 0, 0, 0, PotionEffectType.SLOW));

        // The bosses, at the deepest room of each. Offsets are from the site's own corner.
        boss("The Spire",            10,  61, 12, new Spec(EntityType.WITHER_SKELETON, "The Tenant", 220, 0.34, 13, 0.9, 48, 310, 276, 0));
        boss("Site-19",              26,   1, 22, new Spec(EntityType.ZOMBIE_VILLAGER, "SCP-049", 200, 0.24, 10, 0.8, 44, 3, 283, 0, PotionEffectType.SLOW));
        boss("The Visitor",          25,   3, 25, new Spec(EntityType.ENDERMAN, "The Pilot", 260, 0.38, 12, 0.9, 64, 0, 0, 0));
        boss("Vault 44",             28,  11, 21, new Spec(EntityType.HUSK, "The Overseer", 180, 0.30, 10, 0.8, 40, 314, 267, 0));
        boss("The Hallowed Reach",   19,  -5, 37, new Spec(EntityType.WITHER_SKELETON, "The Ashen Lord", 240, 0.32, 14, 1.0, 48, 314, 276, 0));
        boss("The Long Choir",       21,  -8, 26, new Spec(EntityType.EVOKER, "The Celebrant", 180, 0.30, 9, 0.6, 48, 0, 0, 0));
        boss("The Burnt Chancel",    21,  -8, 28, new Spec(EntityType.WITHER_SKELETON, "The Charred Deacon", 160, 0.32, 11, 0.8, 44, 0, 272, 0));
        boss("The Buried City",      30,  -9, 36, new Spec(EntityType.WITHER_SKELETON, "The Bell-Ringer", 190, 0.30, 11, 0.9, 44, 0, 267, 0));
        boss("Rapture",               9,  19,  9, new Spec(EntityType.ELDER_GUARDIAN, "The Warden of the Deep", 260, 0.24, 12, 1.0, 48, 0, 0, 0));
        boss("The Mastaba",          24, -12, 36, new Spec(EntityType.BLAZE, "The Keeper", 170, 0.32, 10, 0.7, 52, 0, 0, 0, PotionEffectType.FIRE_RESISTANCE));
        boss("AM",                   28,   1, 14, new Spec(EntityType.HUSK, "The Voice", 230, 0.28, 12, 1.0, 48, 310, 276, 0));
        boss("Columbia",             25,  25, 29, new Spec(EntityType.ILLUSIONER, "The Prophet", 170, 0.32, 9, 0.6, 56, 0, 0, 0));
        boss("Wonderland",           50,   6, 42, new Spec(EntityType.WITCH, "The Ringmaster", 180, 0.34, 8, 0.6, 48, 0, 0, 0));
        boss("Old Town",             34, -11, 52, new Spec(EntityType.HUSK, "The Manager", 170, 0.32, 10, 0.8, 42, 306, 267, 0));
        boss("Ostrovets Station",    29, -16, 23, new Spec(EntityType.BLAZE, "The Core", 200, 0.30, 11, 0.8, 48, 0, 0, 0, PotionEffectType.FIRE_RESISTANCE));
        boss("The Hive",             36,   2, 25, new Spec(EntityType.HUSK, "The Red Queen's Hand", 160, 0.34, 10, 0.7, 44, 306, 276, 0));
        boss("The Pit",              18,   1, 18, new Spec(EntityType.MAGMA_CUBE, "SCP-682", 320, 0.26, 14, 1.0, 48, 0, 0, 4, PotionEffectType.FIRE_RESISTANCE));
        boss("The Warren",           20,   1, 20, new Spec(EntityType.STRAY, "SCP-966", 150, 0.38, 9, 0.4, 56, 0, 261, 0,
                                                            PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION));
        boss("The Kennels",          17,   1, 13, new Spec(EntityType.CAVE_SPIDER, "SCP-939", 170, 0.46, 10, 0.4, 56, 0, 0, 0, PotionEffectType.SPEED));
        boss("The Viewing Room",     15,   1, 13, new Spec(EntityType.HUSK, "SCP-096", 190, 0.62, 16, 0.5, 72, 0, 0, 0, PotionEffectType.SPEED));
        boss("The Signal",           12,  20, 10, new Spec(EntityType.WOLF, "SCP-1471", 150, 0.46, 9, 0.4, 56, 0, 0, 0, PotionEffectType.SPEED));
        // Every temple sanctum holds the thing the temple was built round.
        boss("The Ember Ziggurat",   16, -14, 38, new Spec(EntityType.BLAZE, "The Ember Warden", 190, 0.32, 11, 0.8, 48, 0, 0, 0, PotionEffectType.FIRE_RESISTANCE));
        boss("The Tidewell",         16, -14, 38, new Spec(EntityType.ELDER_GUARDIAN, "The Tide Warden", 200, 0.24, 11, 1.0, 44, 0, 0, 0));
        boss("The Green Sanctum",    16, -14, 38, new Spec(EntityType.SPIDER, "The Green Warden", 180, 0.42, 10, 0.6, 48, 0, 0, 0, PotionEffectType.SPEED));
        boss("The Dust Reliquary",   16, -14, 38, new Spec(EntityType.HUSK, "The Dust Warden", 190, 0.34, 11, 0.9, 44, 314, 283, 0));
        boss("The Oxide Sepulchre",  16, -14, 38, new Spec(EntityType.VINDICATOR, "The Oxide Warden", 180, 0.36, 12, 0.7, 48, 0, 258, 0));
        boss("The Frostvault",       16, -14, 38, new Spec(EntityType.STRAY, "The Frost Warden", 180, 0.34, 10, 0.6, 52, 310, 261, 0));
    }

    private final Plugin plugin;
    private final Set<String> taken = new HashSet<String>();
    private Terrain terrain;
    private World world;
    private int dressed, crowned;

    public Containment(Plugin plugin) { this.plugin = plugin; }

    public void start() {
        world = Bukkit.getWorld("world");
        if (world == null) return;
        terrain = new Terrain(world.getSeed());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("CONTAINMENT_READY anomalySites=" + ANOMALIES.size()
                + " bossSites=" + BOSS.size());
    }

    public void stop() { taken.clear(); }

    public String status() { return "dressed=" + dressed + " bosses=" + crowned; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void spawned(CreatureSpawnEvent event) {
        if (terrain == null || event.getEntity().getWorld() != world) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        LivingEntity mob = event.getEntity();
        Location at = mob.getLocation();
        int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
        int k;
        try { k = Megaliths.located(terrain, x, y, z); }
        catch (RuntimeException broken) { return; }
        if (k < 0) return;
        String site = Megaliths.siteName(k);
        int[] where = BOSS_AT.get(site);
        if (where != null) {
            int[] origin = Megaliths.originOf(terrain, k, x, z);
            if (origin != null) {
                int bx = origin[0] + where[0], by = origin[2] + where[1], bz = origin[1] + where[2];
                if (Math.abs(x - bx) <= 5 && Math.abs(z - bz) <= 5 && Math.abs(y - by) <= 5) {
                    String key = site + "@" + origin[0] + "," + origin[1];
                    if (taken.add(key)) {
                        dress(mob, BOSS.get(site), true);
                        crowned++;
                        return;
                    }
                }
            }
        }
        Spec[] pool = ANOMALIES.get(site);
        if (pool == null || pool.length == 0) return;
        Spec spec = pool[Math.floorMod(x * 31 + y * 17 + z * 7, pool.length)];
        dress(mob, spec, false);
        dressed++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void died(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        String name = dead.getCustomName();
        if (name == null || !dead.hasMetadata("jaspr_boss")) return;
        String plain = ChatColor.stripColor(name);
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers())
            if (p.getWorld() == dead.getWorld() && p.getLocation().distanceSquared(dead.getLocation()) < 64 * 64)
                p.sendMessage(ChatColor.DARK_GRAY + "* " + ChatColor.RED + plain + ChatColor.GRAY + " is down.");
    }

    /** Re-stats a mob into one of the specs above. Nothing here needs a new entity type. */
    private void dress(LivingEntity mob, Spec spec, boolean isBoss) {
        if (spec == null) return;
        try {
            if (mob.getType() != spec.type && spec.type != null) {
                Entity replacement = mob.getWorld().spawnEntity(mob.getLocation(), spec.type);
                if (!(replacement instanceof LivingEntity)) { replacement.remove(); return; }
                mob.remove();
                mob = (LivingEntity) replacement;
            }
            if (spec.size > 0 && mob instanceof Slime) ((Slime) mob).setSize(spec.size);
            if (mob instanceof Wolf) ((Wolf) mob).setAngry(true);
            set(mob, Attribute.GENERIC_MAX_HEALTH, spec.health);
            set(mob, Attribute.GENERIC_MOVEMENT_SPEED, spec.speed);
            set(mob, Attribute.GENERIC_ATTACK_DAMAGE, spec.damage);
            set(mob, Attribute.GENERIC_KNOCKBACK_RESISTANCE, spec.knockback);
            set(mob, Attribute.GENERIC_FOLLOW_RANGE, spec.follow);
            mob.setHealth(Math.min(spec.health, mob.getMaxHealth()));
            mob.setCustomName((isBoss ? ChatColor.RED : ChatColor.GRAY) + spec.name);
            mob.setCustomNameVisible(false);
            mob.setRemoveWhenFarAway(false);
            EntityEquipment kit = mob.getEquipment();
            if (kit != null) {
                if (spec.helmet > 0) { kit.setHelmet(new ItemStack(spec.helmet)); kit.setHelmetDropChance(0f); }
                if (spec.weapon > 0) { kit.setItemInMainHand(new ItemStack(spec.weapon)); kit.setItemInMainHandDropChance(0f); }
                if (isBoss) {
                    kit.setChestplateDropChance(0f);
                    kit.setLeggingsDropChance(0f);
                    kit.setBootsDropChance(0f);
                }
            }
            for (PotionEffectType effect : spec.effects)
                if (effect != null) mob.addPotionEffect(new PotionEffect(effect, Integer.MAX_VALUE, 0, false, false), true);
            if (isBoss) {
                mob.setMetadata("jaspr_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, spec.name));
                // 3.27.0: also a scoreboard tag, which is saved with the entity (metadata is not), so the boss
                // is still recognised after a restart - JasprGear drops one random trinket for every "jaspr_boss".
                mob.addScoreboardTag("jaspr_boss");
                mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false), true);
            }
        } catch (RuntimeException | NoSuchMethodError | NoSuchFieldError ignored) { }
    }

    private static void set(LivingEntity mob, Attribute attribute, double value) {
        if (value <= 0) return;
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** Material lookup for the helmet and weapon ids, kept out of the spec table. */
    static Material item(int id) { return Material.getMaterial(id); }
}
