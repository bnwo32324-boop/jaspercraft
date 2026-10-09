package chat.jaspr.dungeon;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Generation 7: mutants come from JasprMutants (the ported Mutant Creatures mod, AGPL-3.0, a separate plugin). The dungeon
 * calls its public API by reflection, so it needs no compile-time dependency: chat.jaspr.mutants.MutantsApi.spawn(String
 * kind, Location at) returns the spawned LivingEntity or null. Without that plugin every mutant species spawns as its vanilla
 * fallback (EncounterCatalog.Species.fallback).
 */
final class MutantBridge {
    private final DungeonPlugin plugin;
    private Method spawn;
    private boolean looked, warned;
    MutantBridge(DungeonPlugin plugin) { this.plugin = plugin; }
    LivingEntity spawn(EncounterCatalog.Species species, Location at) {
        if (!species.mutant()) return null;
        Method m = method();
        if (m == null) return null;
        try {
            Object e = m.invoke(null, species.mutantKind(), at);
            return e instanceof LivingEntity ? (LivingEntity) e : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warned) { warned = true; plugin.getLogger().warning("DUNGEON_MUTANT_SPAWN_FAILED kind=" + species.mutantKind() + " " + ex.getClass().getSimpleName()); }
            return null;
        }
    }
    boolean available() { return method() != null; }
    private Method method() {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprMutants");
        if (p == null || !p.isEnabled()) return null;
        if (!looked || spawn == null) {
            looked = true;
            try { spawn = Class.forName("chat.jaspr.mutants.MutantsApi", true, p.getClass().getClassLoader()).getMethod("spawn", String.class, Location.class); }
            catch (ReflectiveOperationException ex) { spawn = null; }
        }
        return spawn;
    }
}
