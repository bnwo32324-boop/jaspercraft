package chat.jaspr.disasters;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Owner 2026-10-02: natural disasters must never touch obsidian, chests, furnaces, anvils, beds, turrets or other
 * utility blocks, and must never break portals.
 *
 * A small fake world (proxies of the Bukkit interfaces, no server) is packed with every protected kind of block,
 * buried chests, turret bodies with their stands, a Nether portal and a realm gate with a mossy-cobblestone frame.
 * Every disaster then runs through it at full strength (dense fissures with lava, a ripping tornado, a heavy
 * blizzard, meteor strikes and hell craters right beside the valuables), and every single block change is checked:
 * nothing protected changed, nothing holding a protected block up changed, nothing within the portal margin
 * changed, no fire beside anything protected, no lava near it, no snow on it, no armor stand moved, and after the
 * blizzard everything it laid is gone again. Disaster explosions lose every off-limits block from their lists.
 *
 * Owner 2026-10-02, 1.4.0: the earthquake, the tornado and the blizzard reach you anywhere (underground, on a
 * sky platform, inside a base) and only an obsidian bunker keeps you safe. The bunker check is tested on its
 * own, then a player sealed in obsidian sits through all three untouched, while a player on a sky platform, a
 * player down a cave and a player in a wooden hut are all reached. Builds are wrecked only where the config
 * allows it; elsewhere the hut comes through every disaster without losing a block.
 */
public final class DisasterSafetyTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---------------------------------------------------------------- the fake world
    private static final Map<Long, Material> original = new HashMap<Long, Material>();
    private static final Map<Long, Material> placed = new HashMap<Long, Material>();
    private static final List<long[]> changes = new ArrayList<long[]>();   // x, y, z, from ordinal, to ordinal
    private static final List<Entity> entities = new ArrayList<Entity>();
    private static final Set<Material> PROTECTED_KINDS = new HashSet<Material>();
    private static World world;
    private static Player player;
    private static ArmorStand stand;
    private static Location playerAt;
    private static int standMoves;
    private static boolean explosionFlagSeen = true;
    private static int explosions;
    /** What the disasters did to the player: shoves, damage, potion effects, and when the first damage landed. */
    private static int playerPushes;
    private static double playerDamage;
    private static int playerEffects;
    private static long firstDamageTick = -1L;
    private static long now;

    private static long key(int x, int y, int z) { return ((long) (x + 4096) << 32) | ((long) (z + 4096) << 9) | (y & 511); }

    private static Material base(int x, int y, int z) {
        if (y <= 0) return Material.BEDROCK;
        if (y < 60) return Material.STONE;
        if (y < 64) return Material.DIRT;
        if (y == 64) return Material.GRASS;
        return Material.AIR;
    }

    private static Material type(int x, int y, int z) {
        Material m = placed.get(key(x, y, z));
        return m != null ? m : base(x, y, z);
    }

    private static Material originalType(int x, int y, int z) {
        Material m = original.get(key(x, y, z));
        return m != null ? m : base(x, y, z);
    }

    private static void put(int x, int y, int z, Material m) { placed.put(key(x, y, z), m); }

    private static boolean opaque(Material m) {
        return m.isOccluding() || m == Material.LEAVES || m == Material.LEAVES_2 || m == Material.WATER || m == Material.STATIONARY_WATER || m == Material.ICE;
    }

    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == List.class || type == Collection.class) return new ArrayList<Object>();
        if (type == Set.class) return new HashSet<Object>();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(DisasterSafetyTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Block block(final int x, final int y, final int z) {
        return proxy(Block.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getType")) return type(x, y, z);
                if (n.equals("getX")) return x;
                if (n.equals("getY")) return y;
                if (n.equals("getZ")) return z;
                if (n.equals("getWorld")) return world;
                if (n.equals("getLocation")) return new Location(world, x, y, z);
                if (n.equals("getData")) return (byte) 0;
                if (n.equals("getLightFromBlocks")) return (byte) 0;
                if (n.equals("isEmpty")) return type(x, y, z) == Material.AIR;
                if (n.equals("isLiquid")) return Impacts.isLiquid(type(x, y, z));
                if (n.equals("setType")) {
                    Material to = (Material) a[0], from = type(x, y, z);
                    changes.add(new long[] {x, y, z, from.ordinal(), to.ordinal()});
                    put(x, y, z, to);
                    return null;
                }
                if (n.equals("setData")) return null;
                if (n.equals("getRelative")) {
                    if (a.length == 3) return block(x + (Integer) a[0], y + (Integer) a[1], z + (Integer) a[2]);
                    BlockFace f = (BlockFace) a[0];
                    int d = a.length > 1 ? (Integer) a[1] : 1;
                    return block(x + f.getModX() * d, y + f.getModY() * d, z + f.getModZ() * d);
                }
                if (n.equals("equals")) {
                    Object o = a[0];
                    return o instanceof Block && ((Block) o).getX() == x && ((Block) o).getY() == y && ((Block) o).getZ() == z;
                }
                if (n.equals("hashCode")) return Long.hashCode(key(x, y, z));
                if (n.equals("toString")) return "Block(" + x + "," + y + "," + z + ")";
                return fallback(m.getReturnType());
            }
        });
    }

    private static FallingBlock fallingBlock(final Location at) {
        final UUID id = UUID.randomUUID();
        final Set<String> meta = new HashSet<String>();
        final boolean[] valid = {true};
        FallingBlock falling = proxy(FallingBlock.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getUniqueId")) return id;
                if (n.equals("setMetadata")) { meta.add((String) a[0]); return null; }
                if (n.equals("hasMetadata")) return meta.contains((String) a[0]);
                if (n.equals("isValid")) return valid[0];
                if (n.equals("remove")) { valid[0] = false; return null; }
                if (n.equals("getLocation")) return at.clone();
                if (n.equals("getVelocity")) return new Vector();
                if (n.equals("getWorld")) return world;
                if (n.equals("equals")) return self == a[0];
                if (n.equals("hashCode")) return id.hashCode();
                return fallback(m.getReturnType());
            }
        });
        entities.add(falling);
        return falling;
    }

    private static void buildWorld() {
        world = proxy(World.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getName")) return "world";
                if (n.equals("getEnvironment")) return World.Environment.NORMAL;
                if (n.equals("isChunkLoaded")) {
                    if (a.length != 2) return Boolean.TRUE;
                    return Math.abs((Integer) a[0]) <= 8 && Math.abs((Integer) a[1]) <= 8;
                }
                if (n.equals("getBlockAt")) {
                    if (a.length == 3) return block((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                    Location l = (Location) a[0];
                    return block(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                }
                if (n.equals("getHighestBlockYAt")) {
                    int x = a.length == 2 ? (Integer) a[0] : ((Location) a[0]).getBlockX();
                    int z = a.length == 2 ? (Integer) a[1] : ((Location) a[0]).getBlockZ();
                    for (int y = 255; y > 0; y--) if (opaque(type(x, y, z))) return y + 1;
                    return 1;
                }
                if (n.equals("createExplosion")) { explosions++; if (!Impacts.inDisasterExplosion()) explosionFlagSeen = false; return Boolean.TRUE; }
                if (n.equals("spawnFallingBlock")) return fallingBlock((Location) a[0]);
                if (n.equals("getNearbyEntities")) return new ArrayList<Entity>(Arrays2.of(player, stand));
                if (n.equals("getPlayers")) return Collections.singletonList(player);
                if (n.equals("getEntities")) return new ArrayList<Entity>(entities);
                if (n.equals("equals")) return self == a[0];
                if (n.equals("hashCode")) return 7;
                if (n.equals("toString")) return "FakeWorld";
                return fallback(m.getReturnType());
            }
        });
        playerAt = new Location(world, 0.5d, 65.0d, 0.5d);
        final UUID playerId = UUID.randomUUID();
        player = proxy(Player.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getName")) return "Tester";
                if (n.equals("getLocation")) return playerAt.clone();
                if (n.equals("getEyeLocation")) return playerAt.clone().add(0.0d, 1.62d, 0.0d);
                if (n.equals("getWorld")) return world;
                if (n.equals("isOnline")) return Boolean.TRUE;
                if (n.equals("isOnGround")) return Boolean.TRUE;
                if (n.equals("getGameMode")) return GameMode.SURVIVAL;
                if (n.equals("getUniqueId")) return playerId;
                if (n.equals("getVelocity")) return new Vector();
                if (n.equals("setVelocity")) { playerPushes++; return null; }
                if (n.equals("damage")) {
                    playerDamage += (Double) a[0];
                    if (firstDamageTick < 0L) firstDamageTick = now;
                    return null;
                }
                if (n.equals("addPotionEffect")) { playerEffects++; return Boolean.TRUE; }
                if (n.equals("equals")) return self == a[0];
                if (n.equals("hashCode")) return 11;
                return fallback(m.getReturnType());
            }
        });
        stand = proxy(ArmorStand.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getLocation")) return new Location(world, 2.5d, 66.0d, 2.5d);
                if (n.equals("getWorld")) return world;
                if (n.equals("isOnGround")) return Boolean.TRUE;
                if (n.equals("getVelocity")) return new Vector();
                if (n.equals("setVelocity") || n.equals("teleport")) { standMoves++; return n.equals("teleport") ? Boolean.TRUE : null; }
                if (n.equals("equals")) return self == a[0];
                if (n.equals("hashCode")) return 13;
                return fallback(m.getReturnType());
            }
        });
        Server server = proxy(Server.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getPlayerExact")) return "Tester".equals(a[0]) ? player : null;
                if (n.equals("getWorld")) return world;
                if (n.equals("getLogger")) return Logger.getLogger("DisasterSafetyTest");
                if (n.equals("getName") || n.equals("getVersion") || n.equals("getBukkitVersion")) return "test";
                if (n.equals("broadcastMessage")) return 0;
                return fallback(m.getReturnType());
            }
        });
        Bukkit.setServer(server);

        // Valuables everywhere around the target: on the grass, buried in the dirt, and turret bodies with stands.
        Material[] valuables = {Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE, Material.BURNING_FURNACE, Material.ANVIL,
                Material.WORKBENCH, Material.ENCHANTMENT_TABLE, Material.BOOKSHELF, Material.HOPPER, Material.DISPENSER,
                Material.DROPPER, Material.BREWING_STAND, Material.JUKEBOX, Material.ENDER_CHEST, Material.CAULDRON,
                Material.BEACON, Material.PURPLE_SHULKER_BOX, Material.NOTE_BLOCK, Material.SKULL, Material.OBSIDIAN};
        int i = 0;
        for (int x = -30; x <= 30; x += 3) {
            for (int z = -30; z <= 30; z += 3) {
                if (Math.abs(x) < 3 && Math.abs(z) < 3) continue;   // the target's own spot
                Material v = valuables[i++ % valuables.length];
                put(x, 65, z, v);
                if ((x + z) % 2 == 0) put(x + 1, 62, z + 1, valuables[(i + 7) % valuables.length]);   // buried
                if (i % 9 == 0) { put(x, 65, z + 1, Material.BED_BLOCK); put(x, 65, z + 2, Material.BED_BLOCK); }
            }
        }
        // A Nether portal (obsidian frame) and a realm gate framed in mossy cobblestone, both in the thick of it.
        portal(6, 65, -6, Material.OBSIDIAN);
        portal(-8, 65, 7, Material.MOSSY_COBBLESTONE);
        // A pond for the blizzard to freeze.
        for (int x = 10; x <= 16; x++) for (int z = 10; z <= 16; z++) put(x, 64, z, Material.STATIONARY_WATER);
        for (Material m : Material.values()) if (m.isBlock() && Impacts.isProtected(m)) PROTECTED_KINDS.add(m);
        original.putAll(placed);
        changes.clear();
    }

    /** A 4x5 frame along X with a 2x3 portal in it. */
    private static void portal(int x0, int y0, int z0, Material frame) {
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 5; dy++) {
            boolean edge = dx == 0 || dx == 3 || dy == 0 || dy == 4;
            put(x0 + dx, y0 + dy, z0, edge ? frame : Material.PORTAL);
        }
    }

    // ---------------------------------------------------------------- the rules every change must obey
    private static boolean originalProtected(int x, int y, int z) { return Impacts.isProtected(originalType(x, y, z)); }

    private static boolean originalPortalNear(int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (Impacts.isPortal(originalType(x + dx, y + dy, z + dz))) return true;
        }
        return false;
    }

    private static boolean originalProtectedNear(int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (originalProtected(x + dx, y + dy, z + dz)) return true;
        }
        return false;
    }

    /** Checks every change made since the last call, then puts the world back for the next disaster. */
    private static int audit(String disaster, int minimumChanges) {
        int count = changes.size();
        for (long[] c : changes) {
            int x = (int) c[0], y = (int) c[1], z = (int) c[2];
            Material from = Material.values()[(int) c[3]], to = Material.values()[(int) c[4]];
            String at = disaster + " changed " + from + "->" + to + " at " + x + "," + y + "," + z;
            check(!PROTECTED_KINDS.contains(from), at + ": a protected block");
            check(!Bunker.isShell(from), at + ": part of a bunker's shell");
            check(!Bunker.isShell(originalType(x, y + 1, z)), at + ": the block under an iron door");
            if (from != Material.AIR || to != Material.SNOW && to != Material.FIRE) {
                check(!originalProtected(x, y + 1, z), at + ": the block holding up " + originalType(x, y + 1, z));
            }
            check(!originalPortalNear(x, y, z, Impacts.PORTAL_MARGIN), at + ": inside a portal's frame zone");
            if (to == Material.FIRE) check(!originalProtectedNear(x, y, z, 1), at + ": fire beside a protected block");
            if (to == Material.STATIONARY_LAVA || to == Material.LAVA) check(!originalProtectedNear(x, y, z, 3), at + ": lava near a protected block");
            if (to == Material.SNOW) check(!originalProtected(x, y - 1, z), at + ": snow on top of " + originalType(x, y - 1, z));
        }
        check(count >= minimumChanges, disaster + " made only " + count + " changes: the scenario did not exercise it");
        placed.clear();
        placed.putAll(original);
        changes.clear();
        entities.clear();
        return count;
    }

    private static DisasterConfig config(Object... overrides) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < overrides.length; i += 2) yaml.set((String) overrides[i], overrides[i + 1]);
        return new DisasterConfig(yaml);
    }

    private static void run(Disaster disaster, long ticks) {
        for (long t = 0; t <= ticks && !disaster.isFinished(); t += 5) { now = t; disaster.tick(t); }
    }

    // ---------------------------------------------------------------- scenes for the bunker rule
    private static Map<Long, Material> baseline;

    /** Adds a block to the current scene; it counts as original, so the audit protects what it should. */
    private static void build(int x, int y, int z, Material m) { put(x, y, z, m); original.put(key(x, y, z), m); }

    private static void box(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) build(x, y, z, m);
    }

    /** A player-sized obsidian shell: floor, ceiling, and four walls two high, around feet at (x, y, z). */
    private static void bunker(int x, int y, int z) {
        build(x, y - 1, z, Material.OBSIDIAN);
        build(x, y + 2, z, Material.OBSIDIAN);
        for (int dy = 0; dy <= 1; dy++) {
            build(x + 1, y + dy, z, Material.OBSIDIAN);
            build(x - 1, y + dy, z, Material.OBSIDIAN);
            build(x, y + dy, z + 1, Material.OBSIDIAN);
            build(x, y + dy, z - 1, Material.OBSIDIAN);
        }
    }

    private static void resetPlayer(double x, double y, double z) {
        playerAt = new Location(world, x, y, z);
        playerPushes = 0;
        playerDamage = 0.0d;
        playerEffects = 0;
        firstDamageTick = -1L;
    }

    /** Back to the shared world after a scene. */
    private static void endScene() {
        original.clear();
        original.putAll(baseline);
        placed.clear();
        placed.putAll(baseline);
        changes.clear();
        entities.clear();
        resetPlayer(0.5d, 65.0d, 0.5d);
    }

    /** Changes since the last audit that turned this material into something else. */
    private static int changed(Material from) {
        int n = 0;
        for (long[] c : changes) if (c[3] == from.ordinal() && c[4] != from.ordinal()) n++;
        return n;
    }

    private static int changedAt(int y) {
        int n = 0;
        for (long[] c : changes) if (c[1] == y) n++;
        return n;
    }

    private static void bunkerShape() {
        // Clear ground far from the valuables: only grass, dirt and air around (60, 65, 60).
        bunker(60, 65, 60);
        check(Bunker.inside(world, 60, 65, 60), "a sealed obsidian shell is a bunker");
        put(61, 65, 60, Material.AIR);
        check(!Bunker.inside(world, 60, 65, 60), "a missing wall block is a gap");
        put(61, 65, 60, Material.WOOD);
        check(!Bunker.inside(world, 60, 65, 60), "a plank wall is a gap");
        put(61, 65, 60, Material.IRON_DOOR_BLOCK);
        put(61, 66, 60, Material.IRON_DOOR_BLOCK);
        check(Bunker.inside(world, 60, 65, 60), "an iron door is part of the shell");
        put(61, 65, 60, Material.WOODEN_DOOR);
        put(61, 66, 60, Material.WOODEN_DOOR);
        check(!Bunker.inside(world, 60, 65, 60), "a wooden door is a gap");
        // The east wall moved out a block, with a chest and a torch in the room: still a bunker.
        put(61, 65, 60, Material.CHEST);
        put(61, 66, 60, Material.TORCH);
        put(62, 65, 60, Material.OBSIDIAN);
        put(62, 66, 60, Material.OBSIDIAN);
        check(Bunker.inside(world, 60, 65, 60), "furniture inside is fine");
        // A plank ceiling under the obsidian one: the shell starts with wood, so it is not a bunker.
        put(60, 67, 60, Material.WOOD);
        put(60, 68, 60, Material.OBSIDIAN);
        check(!Bunker.inside(world, 60, 65, 60), "wood between you and the obsidian is a gap");
        put(60, 67, 60, Material.AIR);
        check(Bunker.inside(world, 60, 65, 60), "a taller room with an obsidian roof is a bunker");
        // Open to the sky: no shell overhead within reach.
        put(60, 68, 60, Material.AIR);
        check(!Bunker.inside(world, 60, 65, 60), "no roof is no bunker");
        // Stone all round (an ordinary mine) is no bunker.
        check(!Bunker.inside(world, 40, 30, 40), "solid stone is no bunker");
    }

    public static void main(String[] args) {
        buildWorld();
        Plugin plugin = proxy(Plugin.class, new InvocationHandler() {
            @Override public Object invoke(Object self, Method m, Object[] a) {
                if (m.getName().equals("getName")) return "JasprDisasters";
                if (m.getName().equals("equals")) return self == a[0];
                if (m.getName().equals("hashCode")) return 17;
                return fallback(m.getReturnType());
            }
        });
        StringBuilder report = new StringBuilder();

        // Earthquakes: many fissures, long and deep, every one with lava, right across the valuables.
        DisasterConfig quakes = config("earthquake.fissures", 12, "earthquake.fissure-length", 32, "earthquake.fissure-depth", 8,
                "earthquake.fissure-lava-percent", 100, "earthquake.duration-seconds", 60, "earthquake.radius", 30);
        int carved = 0;
        for (int seed = 1; seed <= 40; seed++) {
            Earthquake quake = new Earthquake(plugin, quakes, new Random(seed), player, 0L);
            run(quake, 2000L);
            for (long t = 2000L; t < 2400L && !quake.isFinished(); t += 5) quake.tick(t);
            carved += audit("earthquake#" + seed, 0);
        }
        check(carved > 2000, "earthquakes carved only " + carved + " blocks");
        check(standMoves == 0, "an armor stand was shoved by a quake");
        report.append(" quakeChanges=").append(carved);

        // Tornadoes: ripping as fast as the config allows, crossing the valuables toward the target.
        DisasterConfig tornadoes = config("tornado.rip-blocks-per-second", 20, "tornado.max-debris", 300, "tornado.duration-seconds", 120,
                "tornado.spawn-distance", 20);
        int ripped = 0;
        for (int seed = 1; seed <= 20; seed++) {
            Tornado tornado = new Tornado(plugin, tornadoes, new Random(seed), player, 0L);
            run(tornado, 2400L);
            ripped += audit("tornado#" + seed, 0);
        }
        check(ripped > 300, "tornadoes ripped only " + ripped + " blocks");
        check(standMoves == 0, "an armor stand was pulled by a tornado");
        report.append(" tornadoChanges=").append(ripped);

        // Blizzards: heavy snow and ice around the target; afterwards everything it laid has thawed away.
        DisasterConfig blizzards = config("blizzard.snow-per-second", 40, "blizzard.max-snow", 2000, "blizzard.max-ice", 500, "blizzard.duration-seconds", 120);
        int snowed = 0;
        for (int seed = 1; seed <= 5; seed++) {
            Blizzard blizzard = new Blizzard(blizzards, new Random(seed), player, 0L);
            run(blizzard, 2400L);
            check(!blizzard.isFinished(), "the blizzard ended early");
            for (long t = 2400L; t < 20000L && !blizzard.isFinished(); t += 5) blizzard.tick(t);
            check(blizzard.isFinished(), "the blizzard never finished thawing");
            for (Map.Entry<Long, Material> e : placed.entrySet()) {
                Material was = original.containsKey(e.getKey()) ? original.get(e.getKey()) : null;
                Material now = e.getValue();
                if (now == Material.SNOW || now == Material.ICE) check(was == now, "the blizzard left " + now + " behind");
            }
            snowed += audit("blizzard#" + seed, 100);
        }
        report.append(" blizzardChanges=").append(snowed);

        // Meteor strikes and hell craters right next to every valuable and both portals.
        DisasterConfig impacts = config("meteor-shower.magma-per-impact", 40, "meteor-shower.fire-per-impact", 40, "meteor-shower.scatter-radius", 6,
                "thunder-storm.crater-radius", 6, "thunder-storm.crater-fire", 60, "thunder-storm.crater-magma", 60);
        Random random = new Random(42L);
        int struck = 0;
        for (int x = -30; x <= 30; x += 2) {
            for (int z = -30; z <= 30; z += 5) {
                Location at = new Location(world, x + 0.5d, 65.5d, z + 0.5d);
                Impacts.strike(impacts, at, random);
                Impacts.hellCrater(impacts, at, random);
                Impacts.scorch(at);
            }
        }
        check(explosions > 0 && explosionFlagSeen, "every disaster explosion runs inside Impacts.explode");
        struck = audit("impacts", 500);
        report.append(" impactChanges=").append(struck);

        // A disaster explosion's block list loses everything off limits and keeps the rest.
        List<Block> blast = new ArrayList<Block>();
        for (int x = -12; x <= 12; x++) for (int y = 60; y <= 70; y++) for (int z = -12; z <= 12; z++) blast.add(block(x, y, z));
        int before = blast.size();
        int spared = Impacts.spare(blast);
        check(spared > 0 && blast.size() == before - spared, "spare removes from the list");
        for (Block b : blast) {
            check(!Impacts.isProtected(b.getType()), "blast still lists " + b.getType() + " at " + b);
            check(!Impacts.isProtected(b.getRelative(BlockFace.UP).getType()), "blast still lists the support of " + b.getRelative(BlockFace.UP).getType());
            check(!originalPortalNear(b.getX(), b.getY(), b.getZ(), Impacts.PORTAL_MARGIN), "blast still lists a portal-frame block at " + b);
        }
        check(blast.size() > 1000, "ordinary blocks stay in the blast");
        report.append(" blastSpared=").append(spared);

        // ------------------------------------------------------------ the bunker rule (1.4.0)
        baseline = new HashMap<Long, Material>(original);
        bunkerShape();
        endScene();
        DisasterConfig plain = config("tornado.duration-seconds", 120, "tornado.spawn-distance", 20);
        DisasterConfig dense = config("earthquake.fissures", 12, "earthquake.fissure-length", 24, "earthquake.radius", 30);

        // Sealed in obsidian at the target's own spot: no quake, tornado or blizzard reaches in.
        bunker(0, 65, 0);
        resetPlayer(0.5d, 65.0d, 0.5d);
        check(Bunker.inside(player), "the target is sealed in");
        for (int seed = 1; seed <= 6; seed++) {
            run(new Earthquake(plugin, dense, new Random(seed), player, 0L), 2000L);
            run(new Tornado(plugin, plain, new Random(seed), player, 0L), 2400L);
            Blizzard blizzard = new Blizzard(plain, new Random(seed), player, 0L);
            run(blizzard, 1800L);
            for (long t = 1800L; t < 20000L && !blizzard.isFinished(); t += 5) blizzard.tick(t);
            audit("bunker#" + seed, 0);
        }
        check(playerPushes == 0 && playerDamage == 0.0d && playerEffects == 0,
                "a player in an obsidian bunker was reached: pushes=" + playerPushes + " damage=" + playerDamage + " effects=" + playerEffects);
        endScene();

        // Up on a wooden sky platform (a chest and an obsidian block on it): shaken, cracked, flung, frozen fast.
        box(-15, 149, -15, 15, 149, 15, Material.WOOD);
        build(5, 150, 5, Material.CHEST);
        build(-5, 149, -5, Material.OBSIDIAN);
        resetPlayer(0.5d, 150.0d, 0.5d);
        int skyCracks = 0;
        for (int seed = 1; seed <= 6; seed++) {
            run(new Earthquake(plugin, dense, new Random(seed), player, 0L), 2000L);
            skyCracks += changed(Material.WOOD);
            audit("sky quake#" + seed, 0);
        }
        check(playerPushes > 0, "the quake reaches a player on a sky platform");
        check(skyCracks > 0, "the quake cracks a wooden sky platform");
        resetPlayer(0.5d, 150.0d, 0.5d);
        int skyTorn = 0;
        for (int seed = 1; seed <= 6; seed++) {
            run(new Tornado(plugin, plain, new Random(seed), player, 0L), 2400L);
            skyTorn += changed(Material.WOOD);
            audit("sky tornado#" + seed, 0);
        }
        check(playerPushes > 0, "the tornado reaches a player on a sky platform");
        check(skyTorn > 0, "the tornado tears planks off a sky platform");
        resetPlayer(0.5d, 150.0d, 0.5d);
        Blizzard open = new Blizzard(plain, new Random(5), player, 0L);
        run(open, 1800L);
        long openFrost = firstDamageTick;
        check(openFrost > 0L, "the blizzard freezes a player out in the open");
        for (long t = 1800L; t < 20000L && !open.isFinished(); t += 5) open.tick(t);
        audit("sky blizzard", 0);
        endScene();

        // Down a cave far under the field: shaken, the floor split, rocks breaking out of the roof, battered by the
        // tornado overhead, and frozen, only more slowly than out in the open.
        box(-12, 30, -12, 12, 33, 12, Material.AIR);
        resetPlayer(0.5d, 30.0d, 0.5d);
        int caveFloor = 0, caveRoof = 0;
        for (int seed = 1; seed <= 6; seed++) {
            run(new Earthquake(plugin, dense, new Random(seed), player, 0L), 2000L);
            caveRoof += changedAt(34);
            for (int y = 20; y <= 29; y++) caveFloor += changedAt(y);
            audit("cave quake#" + seed, 0);
        }
        check(playerPushes > 0, "the quake reaches a player down a cave");
        check(caveFloor > 0 && caveRoof > 0, "the quake splits the cave floor (" + caveFloor + ") and drops its roof (" + caveRoof + ")");
        resetPlayer(0.5d, 30.0d, 0.5d);
        for (int seed = 1; seed <= 8; seed++) {
            run(new Tornado(plugin, plain, new Random(seed), player, 0L), 2400L);
            audit("cave tornado#" + seed, 0);
        }
        check(playerDamage > 0.0d, "the tornado batters a player down a cave");
        resetPlayer(0.5d, 30.0d, 0.5d);
        Blizzard deep = new Blizzard(plain, new Random(5), player, 0L);
        run(deep, 1800L);
        long caveFrost = firstDamageTick;
        check(caveFrost > openFrost, "the cold reaches a player down a cave, but later (" + caveFrost + ") than in the open (" + openFrost + ")");
        for (long t = 1800L; t < 20000L && !deep.isFinished(); t += 5) deep.tick(t);
        audit("cave blizzard", 0);
        endScene();

        // A wooden hut around the target. Where builds can be wrecked the quake drops its roof and the tornado
        // tears it; where they cannot (build-damage-worlds empty) the hut keeps every plank, and the player inside
        // is still shaken and battered.
        int hutWrecked = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean wrecks = pass == 0;
            box(-4, 65, -4, 4, 69, 4, Material.WOOD);
            box(-3, 65, -3, 3, 68, 3, Material.AIR);
            resetPlayer(0.5d, 65.0d, 0.5d);
            DisasterConfig quakeHere = wrecks ? dense : config("build-damage-worlds", new ArrayList<String>(), "earthquake.fissures", 12,
                    "earthquake.fissure-length", 24, "earthquake.radius", 30);
            DisasterConfig windHere = wrecks ? plain : config("build-damage-worlds", new ArrayList<String>(), "tornado.duration-seconds", 120,
                    "tornado.spawn-distance", 20);
            int planks = 0;
            for (int seed = 1; seed <= 6; seed++) {
                run(new Earthquake(plugin, quakeHere, new Random(seed), player, 0L), 2000L);
                planks += changed(Material.WOOD);
                audit((wrecks ? "hut" : "kept hut") + " quake#" + seed, 0);
                run(new Tornado(plugin, windHere, new Random(seed), player, 0L), 2400L);
                planks += changed(Material.WOOD);
                audit((wrecks ? "hut" : "kept hut") + " tornado#" + seed, 0);
            }
            check(playerPushes > 0, "the quake reaches a player inside a hut");
            check(playerDamage > 0.0d, "the tornado batters a player inside a hut");
            if (wrecks) check(planks > 0, "where builds can be wrecked the hut loses planks");
            else check(planks == 0, "where builds are kept the hut lost " + planks + " planks");
            if (wrecks) hutWrecked = planks;
            endScene();
        }
        report.append(" skyCracks=").append(skyCracks).append(" skyTorn=").append(skyTorn).append(" caveFloor=").append(caveFloor)
                .append(" caveRoof=").append(caveRoof).append(" hutWrecked=").append(hutWrecked)
                .append(" frostOpen=").append(openFrost).append(" frostCave=").append(caveFrost);

        System.out.println("DISASTER_SAFETY_OK" + report);
    }

    /** Arrays.asList for two entities, kept tiny to avoid generic-array warnings. */
    private static final class Arrays2 {
        static List<Entity> of(Entity a, Entity b) { List<Entity> l = new ArrayList<Entity>(); l.add(a); l.add(b); return l; }
    }
}
