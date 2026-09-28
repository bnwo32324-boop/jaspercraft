package chat.jaspr.atlas;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * The Dominion as a working order, not a scatter of monsters. Its people have roles:
 * <ul>
 *   <li>the Ashborn (grunts, bowmen, Blackshields) who fight, the Gnawlings who tunnel, scout and steal,</li>
 *   <li>the Taskmasters who drive the Bound, the Cinder Priests who serve the Heart, the Slag Trolls who haul and guard,</li>
 *   <li>the Emberkin at the forges, the wargs of the Marches' kennels, and the war-chiefs who lead.</li>
 * </ul>
 * Vanilla spawning is replaced in Atlas: nothing spawns in the Concord (its automata keep it), and the Dominion's
 * occupied provinces raise their own kinds near players (more by night), bounded per player. Liberated provinces raise
 * only a few scattered remnants. The Dominion's folk never fight each other; the Concord's Talos fight them on sight.
 */
final class Dominion implements Listener {
    enum Kind {
        ASHBORN("ashborn", "Ashborn", true, 26, 5, 0.26, 2), ASHBORN_BOWMAN("ashborn_bowman", "Ashborn bowman", true, 22, 4, 0.25, 1),
        BLACKSHIELD("blackshield", "Blackshield", true, 40, 7, 0.23, 5), GNAWLING("gnawling", "Gnawling", false, 12, 3, 0.36, 0),
        TASKMASTER("taskmaster", "Taskmaster", true, 26, 8, 0.3, 2), PRIEST("priest", "Cinder Priest", false, 26, 0, 0.3, 0),
        TROLL("troll", "Slag Troll", false, 90, 14, 0.2, 4), EMBERKIN("emberkin", "Emberkin", false, 22, 5, 0.25, 0),
        WARG("warg", "Ash-warg", false, 22, 5, 0.38, 0), WARCHIEF("warchief", "War-chief", true, 60, 9, 0.27, 6),
        BLACK_HORSE("black_horse", "Black horse", false, 30, 0, 0.3, 0);
        final String id, title;
        final boolean named;
        final double health, damage, speed, armor;
        Kind(String id, String title, boolean named, double health, double damage, double speed, double armor) {
            this.id = id; this.title = title; this.named = named; this.health = health; this.damage = damage; this.speed = speed; this.armor = armor;
        }
        static Kind of(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return null; }

        void apply(LivingEntity e) {
            set(e, Attribute.GENERIC_MAX_HEALTH, health);
            e.setHealth(health);
            if (damage > 0) set(e, Attribute.GENERIC_ATTACK_DAMAGE, damage);
            set(e, Attribute.GENERIC_MOVEMENT_SPEED, speed);
            if (armor > 0) set(e, Attribute.GENERIC_ARMOR, armor);
            set(e, Attribute.GENERIC_FOLLOW_RANGE, 28);
        }
        private static void set(LivingEntity e, Attribute a, double v) { AttributeInstance i = e.getAttribute(a); if (i != null) i.setBaseValue(v); }
    }

    private final AtlasPlugin plugin;
    private final Random random = new Random();
    private boolean spawning;
    long raised, slain, blocked;

    Dominion(AtlasPlugin plugin) { this.plugin = plugin; }

    static boolean isDominion(Entity e) { return Npcs.has(e, Npcs.DOMINION); }

    // ------------------------------------------------------------------ spawning

    /** Vanilla spawning is replaced in Atlas; only this plugin's own spawns (and bosses) are let through. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (spawning || !plugin.isAtlas(e.getLocation().getWorld())) return;
        switch (e.getSpawnReason()) {
            case NATURAL: case CHUNK_GEN: case JOCKEY: case MOUNT: case VILLAGE_INVASION: case REINFORCEMENTS: case SLIME_SPLIT: case LIGHTNING:
                e.setCancelled(true);
                blocked++;
                return;
            case SPAWNER: {
                // Dominion spawners (spawning pits) raise Ashborn while the province is held.
                Realm.Zone z = Realm.zone(e.getLocation().getBlockX(), e.getLocation().getBlockZ());
                e.setCancelled(true);
                if (z.province != null && !plugin.state().liberated(z.province) && !(e.getEntity() instanceof Animals)) raise(e.getLocation(), pick(z, true), false);
                return;
            }
            default:
        }
    }

    /** What an occupied province raises near a player. */
    Kind pick(Realm.Zone z, boolean night) {
        double r = random.nextDouble();
        switch (z) {
            case WEALD: return r < 0.35 ? Kind.ASHBORN : r < 0.6 ? Kind.PRIEST : r < 0.8 ? Kind.GNAWLING : Kind.BLACKSHIELD;
            case FORGES: return r < 0.3 ? Kind.ASHBORN : r < 0.5 ? Kind.EMBERKIN : r < 0.65 ? Kind.GNAWLING : r < 0.8 ? Kind.TASKMASTER : r < 0.9 ? Kind.BLACKSHIELD : Kind.TROLL;
            case FALLEN: return r < 0.35 ? Kind.BLACKSHIELD : r < 0.6 ? Kind.TASKMASTER : r < 0.8 ? Kind.ASHBORN_BOWMAN : Kind.ASHBORN;
            case PLATEAU: return r < 0.4 ? Kind.BLACKSHIELD : r < 0.6 ? Kind.EMBERKIN : r < 0.8 ? Kind.ASHBORN_BOWMAN : Kind.TROLL;
            default: return r < 0.4 ? Kind.ASHBORN : r < 0.62 ? Kind.ASHBORN_BOWMAN : r < 0.74 ? Kind.GNAWLING : r < (night ? 0.9 : 0.84) ? Kind.WARG : r < 0.95 ? Kind.BLACKSHIELD : Kind.TROLL;
        }
    }

    LivingEntity raise(Location at, Kind k, boolean remnant) {
        spawning = true;
        try {
            LivingEntity e = plugin.npcs().dominion(at, k.id, random.nextLong());
            e.setRemoveWhenFarAway(true);   // raised ones come and go; garrisons stay
            e.addScoreboardTag("atlas_raised");
            if (remnant) { e.addScoreboardTag("atlas_remnant"); e.setCustomName(ChatColor.GRAY + "Ashborn remnant"); }
            raised++;
            return e;
        } finally { spawning = false; }
    }

    /** Every five seconds: occupied land raises its own near each player in it (bounded), liberated land a remnant now and then. */
    void tick() {
        World w = plugin.atlas();
        if (w == null || w.getPlayers().isEmpty()) return;
        boolean night = w.getTime() > 12500 && w.getTime() < 23500;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Location pl = p.getLocation();
            Realm.Zone z = Realm.zone(pl.getBlockX(), pl.getBlockZ());
            if (z.province == null) continue;
            boolean freed = plugin.state().liberated(z.province);
            int near = 0;
            for (Entity e : p.getNearbyEntities(40, 20, 40)) {
                if (Npcs.has(e, "atlas_raised")) near++;
                // Slag Trolls are golems: they do not choose players by themselves, so they are pointed at them.
                if (e instanceof IronGolem && "troll".equals(Npcs.tagValue(e, Npcs.DOM_KIND)) && ((IronGolem) e).getTarget() == null
                    && e.getLocation().distanceSquared(pl) < 16 * 16 && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) ((IronGolem) e).setTarget(p);
            }
            int cap = freed ? 1 : night ? 9 : 6;
            if (near >= cap || random.nextDouble() < (freed ? 0.9 : 0.35)) continue;
            Location at = spot(pl);
            if (at == null) continue;
            raise(at, pick(z, night), freed);
        }
    }

    /** A spawn spot 18-32 blocks from the player, on open ground out of sight lines is too costly: open ground will do. */
    private Location spot(Location pl) {
        World w = pl.getWorld();
        for (int tries = 0; tries < 6; tries++) {
            double a = random.nextDouble() * Math.PI * 2, d = 18 + random.nextDouble() * 14;
            int x = pl.getBlockX() + (int) (Math.cos(a) * d), z = pl.getBlockZ() + (int) (Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Realm.Zone zone = Realm.zone(x, z);
            if (zone.province == null) continue;
            Block top = w.getHighestBlockAt(x, z);
            Block below = top.getRelative(0, -1, 0);
            if (!below.getType().isSolid() || below.getType() == Material.LAVA || top.getRelative(0, 1, 0).getType().isSolid()) continue;
            return top.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------ allegiances

    /** The Dominion's folk never fight each other; the Concord's automata and the Dominion always do. */
    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        if (!plugin.isAtlas(e.getEntity().getWorld()) || e.getTarget() == null) return;
        Entity who = e.getEntity(), at = e.getTarget();
        if (isDominion(who) && (isDominion(at) || Npcs.has(at, Npcs.BOUND) || at.getScoreboardTags().stream().anyMatch(t -> t.startsWith(Npcs.CAPTIVE)))) e.setCancelled(true);
        if (isDominion(who) && Npcs.has(at, Npcs.CITIZEN) && !(at instanceof Player)) e.setCancelled(true);   // no village massacres near the Line
        if (who instanceof IronGolem && Npcs.has(who, Npcs.TALOS) && at instanceof Player && !plugin.reputation().hostile((Player) at)) e.setCancelled(true);
    }

    /** Kills by players are counted, and some drop the Dominion's trade goods (which the Concord's merchants value). */
    @EventHandler
    public void death(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!isDominion(dead) || !plugin.isAtlas(dead.getWorld())) return;
        slain++;
        Player killer = dead.getKiller();
        if (killer == null) return;
        plugin.state().player(killer.getUniqueId(), killer.getName()).slain++;
        e.setDroppedExp(e.getDroppedExp() + 4);
        if (random.nextDouble() < 0.35) e.getDrops().add(Items.trophy(random));
        if (random.nextDouble() < 0.12) e.getDrops().add(Items.ashIron(1 + random.nextInt(2)));
    }

    /** Clears raised (non-garrison) Dominion folk from a liberated province's loaded land. */
    int clearProvince(World w, Realm.Province p) {
        int n = 0;
        for (Entity e : w.getEntities()) {
            if (!isDominion(e)) continue;
            Realm.Zone z = Realm.zone(e.getLocation().getBlockX(), e.getLocation().getBlockZ());
            if (z.province != p) continue;
            e.remove();
            n++;
        }
        return n;
    }

    void broadcast(String msg) { for (Player p : Bukkit.getOnlinePlayers()) if (plugin.isAtlas(p.getWorld())) p.sendMessage(msg); }
}
