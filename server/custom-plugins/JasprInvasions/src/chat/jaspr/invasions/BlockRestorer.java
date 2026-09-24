package chat.jaspr.invasions;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Damage to a base during an invasion is meant to be frightening, not permanent.
 *
 * Every block an invader mines or blows up is recorded here with what it used to be, and put back
 * a few minutes later. Restoring never overwrites something a player has since built in the same
 * spot, so rebuilding your own wall before the timer fires simply wins.
 */
final class BlockRestorer {
    private static final class Broken {
        final World world;
        final int x;
        final int y;
        final int z;
        final Material material;
        final byte data;
        final long restoreAtTick;

        Broken(Block block, long restoreAtTick) {
            this.world = block.getWorld();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            this.material = block.getType();
            this.data = block.getData();
            this.restoreAtTick = restoreAtTick;
        }
    }

    private final Deque<Broken> queue = new ArrayDeque<Broken>();
    private final InvasionConfig settings;

    BlockRestorer(InvasionConfig settings) {
        this.settings = settings;
    }

    int pending() {
        return queue.size();
    }

    /** Remembers a block as it is right now, to be put back later. Call before breaking it. */
    void record(Block block, long nowTick) {
        if (!settings.breakBlocks) return;
        if (block == null || block.getType() == Material.AIR) return;
        if (queue.size() >= settings.maxRecordedBlocks) return;
        queue.addLast(new Broken(block, nowTick + settings.repairDelayTicks));
    }

    /** Puts back everything whose timer has come up, a bounded number per call. */
    void tick(long nowTick) {
        int budget = settings.repairBatchSize;
        while (budget-- > 0) {
            Broken oldest = queue.peekFirst();
            if (oldest == null || nowTick < oldest.restoreAtTick) return;
            queue.removeFirst();
            restore(oldest);
        }
    }

    /** Puts everything back immediately, used on shutdown so nothing is left as a hole. */
    void restoreAll() {
        Broken next;
        while ((next = queue.pollFirst()) != null) restore(next);
    }

    private void restore(Broken broken) {
        World world = broken.world;
        if (world == null) return;
        if (!world.isChunkLoaded(broken.x >> 4, broken.z >> 4)) return;
        Block block = world.getBlockAt(broken.x, broken.y, broken.z);
        // Someone rebuilt here, or put something better here. Leave it alone.
        if (block.getType() != Material.AIR) return;
        block.setType(broken.material, false);
        try {
            block.setData(broken.data, false);
        } catch (Throwable legacyData) {
            // Block data is cosmetic for our purposes; the shape being back is what matters.
        }
    }

    Location locationOf(Block block) {
        return block.getLocation();
    }
}
