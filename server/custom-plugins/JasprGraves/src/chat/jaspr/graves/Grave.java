package chat.jaspr.graves;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * One player's death: the headstone's position, what they were carrying and how
 * much experience they had. The contents list is the only source of truth for a
 * grave's items; the open inventory is a view onto it that is written back on close.
 */
public final class Grave {
    final UUID id;
    final UUID owner;
    final String ownerName;
    final String world;
    final int x, y, z;
    final long createdAt;
    final float yaw;
    int experience;
    /** The last encoding that reached disk, so a failed encode can never blank a grave. */
    String lastEncoded;
    final List<ItemStack> contents = new ArrayList<ItemStack>();

    Grave(UUID id, UUID owner, String ownerName, String world, int x, int y, int z,
          long createdAt, float yaw, int experience) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.createdAt = createdAt;
        this.yaw = yaw;
        this.experience = experience;
    }

    /** The headstone post. The marker head sits one block above it. */
    Block base() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return w.getBlockAt(x, y, z);
    }

    Location centre() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    boolean empty() { return contents.isEmpty() && experience <= 0; }

    int itemCount() {
        int total = 0;
        for (ItemStack item : contents) if (item != null) total += item.getAmount();
        return total;
    }

    /** Allocation-free position key: the protection handlers run on block physics. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    long basePos() { return pack(x, y, z); }
    long headPos() { return pack(x, y + 1, z); }
    String baseKey() { return world + ':' + x + ':' + y + ':' + z; }
    String chunkKey() { return world + ':' + (x >> 4) + ':' + (z >> 4); }
}
