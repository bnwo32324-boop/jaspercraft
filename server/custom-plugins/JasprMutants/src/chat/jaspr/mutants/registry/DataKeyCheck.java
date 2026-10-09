package chat.jaspr.mutants.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;

/**
 * MUTANTS_PROTOCOL.md 1.2: the client stage hard-codes the data key ids of each entity (its own keys after its vanilla
 * parents'). At startup every mod entity is constructed (not spawned) in the main world and the ids its data manager
 * holds are compared with the table; a difference is logged as MUTANTS_DATAKEYS_MISMATCH (the client would misread
 * metadata of that entity).
 */
public final class DataKeyCheck {
    /** Highest data key id each entity must have, and the total number of keys (ids 0..max, no gaps). */
    private static final Map<String, Integer> MAX_ID = new LinkedHashMap<String, Integer>();

    static {
        MAX_ID.put("body_part", 5);
        MAX_ID.put("chemical_x", 5);
        MAX_ID.put("endersoul_clone", 11);
        MAX_ID.put("creeper_minion", 17);
        MAX_ID.put("creeper_minion_egg", 6);
        MAX_ID.put("endersoul_fragment", 6);
        MAX_ID.put("mutant_arrow", 10);
        MAX_ID.put("mutant_creeper", 15);
        MAX_ID.put("mutant_enderman", 13);
        MAX_ID.put("mutant_skeleton", 11);
        MAX_ID.put("mutant_snow_golem", 13);
        MAX_ID.put("mutant_zombie", 13);
        MAX_ID.put("skull_spirit", 6);
        MAX_ID.put("spider_pig", 15);
        MAX_ID.put("throwable_block", 6);
    }

    private DataKeyCheck() {
    }

    /** @return one line per mismatch (empty when every entity matches the protocol). */
    public static List<String> run(World world) {
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : MAX_ID.entrySet()) {
            EntityEntry entry = EntityRegistry.entryOf(e.getKey());
            if (entry == null) {
                problems.add(e.getKey() + " not registered");
                continue;
            }
            try {
                Entity entity = entry.getEntityClass().getConstructor(World.class).newInstance(world);
                List<EntityDataManager.DataEntry<?>> all = entity.getDataManager().getAll();
                int max = -1;
                boolean[] seen = new boolean[256];
                for (EntityDataManager.DataEntry<?> d : all) {
                    int id = d.getKey().getId();
                    if (id >= 0 && id < 256) seen[id] = true;
                    max = Math.max(max, id);
                }
                boolean gaps = false;
                for (int i = 0; i <= max; i++) if (!seen[i]) gaps = true;
                if (max != e.getValue() || gaps) problems.add(e.getKey() + " max=" + max + " expected=" + e.getValue() + (gaps ? " gaps" : ""));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                problems.add(e.getKey() + " construct " + ex.getClass().getSimpleName());
            }
        }
        return problems;
    }
}
