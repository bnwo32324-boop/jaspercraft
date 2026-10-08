package chat.jaspr.journal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Where the Field Journal reads its numbers: the other plugins, asked through reflection so this one needs none of them at compile
 * time and keeps working (with that row hidden) when one is missing, disabled or changed. Everything here is read-only; a source
 * that fails is counted and reported once, and never throws into the caller.
 */
final class JournalSources {
    enum Source { SIEGE, INVASIONS, DISASTERS, RPG, GEAR, ARMOR }

    private final Logger log;
    private final Map<Source, Integer> failures = new EnumMap<Source, Integer>(Source.class);
    private final Map<Source, Boolean> reported = new EnumMap<Source, Boolean>(Source.class);

    // Blood Moon: JasprApocalypse SiegeRules.bloodMoon(long, int) and the configured interval.
    private Method siegeRule;
    // Invasions: the plugin's own private state, read-only.
    private Field invActive, invSettings, invStore, invDays, invEnabled, progressId, progressSlept;
    private Method storeAll;
    // Disasters and the stat sheet: small public methods added for this panel.
    private Method disasterState, rpgSummary, gearWorn, rpgArmor;

    JournalSources(Logger log) { this.log = log; }

    int failures(Source s) { Integer n = failures.get(s); return n == null ? 0 : n.intValue(); }

    private void fail(Source s, Throwable error) {
        failures.put(s, Integer.valueOf(failures(s) + 1));
        if (!Boolean.TRUE.equals(reported.get(s))) {
            reported.put(s, Boolean.TRUE);
            log.warning("JOURNAL_SOURCE_UNAVAILABLE source=" + s.name().toLowerCase() + " error=" + error.getClass().getSimpleName());
        }
        // Looked up again next time: the plugin may have been reloaded.
        switch (s) {
            case SIEGE: siegeRule = null; break;
            case INVASIONS: invActive = null; storeAll = null; break;
            case DISASTERS: disasterState = null; break;
            case GEAR: gearWorn = null; break;
            case ARMOR: rpgArmor = null; break;
            default: rpgSummary = null; break;
        }
    }

    private static Plugin enabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }

    // -------------------------------------------------------------------------------------------------------- Blood Moon
    /** The siege's Blood Moon rule for the configured interval, or null when the siege is not running. */
    JournalRules.BloodMoonRule bloodMoonRule() {
        try {
            final Plugin apocalypse = enabled("JasprApocalypse");
            if (apocalypse == null) return null;
            if (siegeRule == null) {
                Class<?> rules = apocalypse.getClass().getClassLoader().loadClass("chat.jaspr.apocalypse.SiegeRules");
                siegeRule = rules.getDeclaredMethod("bloodMoon", long.class, int.class);
                siegeRule.setAccessible(true);
            }
            final int every = apocalypse.getConfig().getInt("siege.blood-moon-every-nights", 3);
            if (every <= 0) return null;
            final Method rule = siegeRule;
            return new JournalRules.BloodMoonRule() {
                @Override public boolean at(long fullTime) {
                    try { return Boolean.TRUE.equals(rule.invoke(null, Long.valueOf(fullTime), Integer.valueOf(every))); }
                    catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                }
            };
        } catch (Throwable error) {
            fail(Source.SIEGE, error);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------- invasions
    /** {state, dueDay} for a player, or null when invasions are not running. Reads only; never creates a record. */
    int[] invasion(UUID id, long fullTime) {
        try {
            Plugin plugin = enabled("JasprInvasions");
            if (plugin == null) return null;
            if (invActive == null) {
                Class<?> type = plugin.getClass();
                invActive = field(type, "active");
                invSettings = field(type, "settings");
                invStore = field(type, "store");
                Class<?> config = invSettings.getType();
                invDays = field(config, "daysAfterSleep");
                invEnabled = field(config, "enabled");
                Class<?> store = invStore.getType();
                storeAll = store.getDeclaredMethod("all");
                storeAll.setAccessible(true);
                Class<?> progress = plugin.getClass().getClassLoader().loadClass("chat.jaspr.invasions.PlayerProgress");
                progressId = field(progress, "id");
                progressSlept = field(progress, "sleptAt");
            }
            Object settings = invSettings.get(plugin);
            if (settings == null || !invEnabled.getBoolean(settings)) return null;
            boolean underway = ((Map<?, ?>) invActive.get(plugin)).containsKey(id);
            long slept = -1L;
            Object store = invStore.get(plugin);
            if (store != null) {
                for (Object progress : (Collection<?>) storeAll.invoke(store)) {
                    if (id.equals(progressId.get(progress))) { slept = progressSlept.getLong(progress); break; }
                }
            }
            return JournalRules.invasion(underway, slept, fullTime, invDays.getInt(settings));
        } catch (Throwable error) {
            fail(Source.INVASIONS, error);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------- disasters
    /** "active:<kind>", "brewing" or "quiet"; null when disasters are not running. */
    String disaster() {
        try {
            Plugin plugin = enabled("JasprDisasters");
            if (plugin == null) return null;
            if (disasterState == null) {
                disasterState = plugin.getClass().getDeclaredMethod("journalState");
                disasterState.setAccessible(true);
            }
            Object state = disasterState.invoke(plugin);
            return state instanceof String ? (String) state : null;
        } catch (Throwable error) {
            fail(Source.DISASTERS, error);
            return null;
        }
    }

    // --------------------------------------------------------------------------------------------------------------- stats
    /** {ranks, raised, total, affordable, cheapest} from the stat sheet; null when it is not running. */
    int[] stats(UUID id, int xpLevels) {
        try {
            Plugin plugin = enabled("JasprRPG");
            if (plugin == null) return null;
            if (rpgSummary == null) {
                Class<?> api = plugin.getClass().getClassLoader().loadClass("chat.jaspr.rpg.RpgApi");
                rpgSummary = api.getDeclaredMethod("summary", UUID.class, int.class);
                rpgSummary.setAccessible(true);
            }
            Object result = rpgSummary.invoke(null, id, Integer.valueOf(xpLevels));
            return result instanceof int[] && ((int[]) result).length == 5 ? (int[]) result : null;
        } catch (Throwable error) {
            fail(Source.RPG, error);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------------ armament armour
    /** The armour points a player's worn armaments add to the armour bar (JasprRPG RpgApi.armamentArmor, display only); 0 when the stat plugin is not running. */
    int armor(org.bukkit.entity.Player player) {
        try {
            Plugin plugin = enabled("JasprRPG");
            if (plugin == null) return 0;
            if (rpgArmor == null) {
                Class<?> api = plugin.getClass().getClassLoader().loadClass("chat.jaspr.rpg.RpgApi");
                rpgArmor = api.getDeclaredMethod("armamentArmor", org.bukkit.entity.Player.class);
                rpgArmor.setAccessible(true);
            }
            Object result = rpgArmor.invoke(null, player);
            return result instanceof Integer ? Math.max(0, Math.min(JournalRules.MAX_ARMOR_BONUS, ((Integer) result).intValue())) : 0;
        } catch (Throwable error) {
            fail(Source.ARMOR, error);
            return 0;
        }
    }

    // ----------------------------------------------------------------------------------------------------------- worn trinkets
    /** The trinkets a player wears in the Survivor Gear column, in slot order, each {title, effect line, ...}; null when the Gear plugin is not running. */
    @SuppressWarnings("unchecked")
    List<String[]> gear(org.bukkit.entity.Player player) {
        try {
            Plugin plugin = enabled("JasprGear");
            if (plugin == null) return null;
            if (gearWorn == null) {
                Class<?> api = plugin.getClass().getClassLoader().loadClass("chat.jaspr.gear.GearApi");
                gearWorn = api.getDeclaredMethod("worn", org.bukkit.entity.Player.class);
                gearWorn.setAccessible(true);
            }
            Object result = gearWorn.invoke(null, player);
            return result instanceof List ? (List<String[]>) result : null;
        } catch (Throwable error) {
            fail(Source.GEAR, error);
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
