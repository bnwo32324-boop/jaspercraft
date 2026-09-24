package chat.jaspr.biomes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Isolated collision/void tests for the shared arrival landing resolver. */
public final class SpawnSafetyTest {
    private static int assertions;

    public static void main(String[] args) {
        Set<String> loaded = new HashSet<String>();
        World world = world(loaded, false);
        check(SpawnSafety.voidRisk(new Location(world, 0.5, 0, 0.5)), "Y=0 metadata is a void risk");
        check(SpawnSafety.voidRisk(new Location(world, Double.NaN, 73, 0.5)), "non-finite coordinates are rejected");
        check(SpawnSafety.voidRisk(new Location(world, 0.5, 255, 0.5)), "top-edge coordinates are rejected");
        Location safe = SpawnSafety.resolve(world, 0, 0);
        check(safe != null, "bounded search finds a nearby landing when the metadata column is void");
        check(safe.getBlockX() == 1 && safe.getBlockY() == 73 && safe.getBlockZ() == 0, "nearest valid column is selected deterministically");
        check(SpawnSafety.standable(safe), "selected landing has solid floor and clear feet/head");
        check(!SpawnSafety.standable(new Location(world, 2.5, 73, 0.5)), "water floor is rejected");
        check(!SpawnSafety.standable(new Location(world, 3.5, 73, 0.5)), "fire at the feet is rejected");
        check(loaded.size() <= 4, "small spawn search loads only bounded neighboring chunks");
        check(SpawnSafety.resolve(world(new HashSet<String>(), true), 0, 0) == null, "all-void fixture fails closed");
        System.out.println("SPAWN_SAFETY_OK assertions=" + assertions + " loadedChunks=" + loaded.size());
    }

    private static World world(Set<String> loaded, boolean allVoid) {
        final World[] owner = new World[1];
        owner[0] = proxy(World.class, (method, args) -> {
            String name = method.getName();
            if (name.equals("getName")) return "world";
            if (name.equals("getUID")) return UUID.nameUUIDFromBytes("spawn-world".getBytes("UTF-8"));
            if (name.equals("getMaxHeight")) return 256;
            if (name.equals("isChunkLoaded")) return loaded.contains(args[0] + ":" + args[1]);
            if (name.equals("getChunkAt")) {
                final String key = args[0] + ":" + args[1];
                return proxy(Chunk.class, (chunkMethod, chunkArgs) -> {
                    if (chunkMethod.getName().equals("load")) { loaded.add(key); return true; }
                    return defaultValue(chunkMethod.getReturnType());
                });
            }
            if (name.equals("getHighestBlockYAt")) {
                int x = (Integer) args[0], z = (Integer) args[1];
                return !allVoid && ((x == 1 || x == 2 || x == 3) && z == 0) ? 72 : 0;
            }
            if (name.equals("getBlockAt")) {
                int x = (Integer) args[0], y = (Integer) args[1], z = (Integer) args[2];
                Material type = Material.AIR;
                if (!allVoid && z == 0 && y == 72) {
                    if (x == 1 || x == 3) type = Material.STONE;
                    else if (x == 2) type = Material.STATIONARY_WATER;
                } else if (!allVoid && x == 3 && z == 0 && y == 73) type = Material.FIRE;
                final Material material = type;
                return proxy(Block.class, (blockMethod, blockArgs) -> blockMethod.getName().equals("getType") ? material : defaultValue(blockMethod.getReturnType()));
            }
            return defaultValue(method.getReturnType());
        });
        return owner[0];
    }

    private interface Call { Object invoke(Method method, Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(SpawnSafetyTest.class.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            if (method.getName().equals("equals")) return object == args[0];
            if (method.getName().equals("toString")) return "Spawn" + type.getSimpleName();
            return call.invoke(method, args == null ? new Object[0] : args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
