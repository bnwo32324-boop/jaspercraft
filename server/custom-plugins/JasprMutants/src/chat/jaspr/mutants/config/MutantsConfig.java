package chat.jaspr.mutants.config;

import chumbanotz.mutantbeasts.MBConfig;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.minecraftforge.common.config.Config;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Forge's ConfigManager for MBConfig: plugins/JasprMutants/config.yml has the categories and option names of the mod's
 * MutantBeasts.cfg (@Config.Name), its comments and ranges, and is read into MBConfig's fields the way Forge syncs a
 * @Config class (missing options get the default and are written back; out-of-range numbers are clamped).
 */
public final class MutantsConfig {
    private static File file;
    private static Logger log = Logger.getLogger("JasprMutants");
    private static int options;
    private static int fromFile;

    private MutantsConfig() {
    }

    public static void init(File dataFolder, Logger logger) {
        file = new File(dataFolder, "config.yml");
        log = logger;
    }

    public static int optionCount() {
        return options;
    }

    public static int optionsFromFile() {
        return fromFile;
    }

    /** ConfigManager.sync("mutantbeasts", Config.Type.INSTANCE). */
    public static synchronized void sync() {
        if (file == null) return;
        YamlConfiguration yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        boolean missing = !file.isFile();
        int count = 0;
        int read = 0;
        try {
            for (Field categoryField : MBConfig.class.getFields()) {
                if (!Modifier.isStatic(categoryField.getModifiers()) || categoryField.isAnnotationPresent(Config.Ignore.class)) continue;
                String category = nameOf(categoryField);
                Object settings = categoryField.get(null);
                for (Field option : settings.getClass().getFields()) {
                    if (Modifier.isStatic(option.getModifiers()) || option.isAnnotationPresent(Config.Ignore.class)) continue;
                    count++;
                    String path = category + "." + nameOf(option);
                    if (!yaml.contains(path)) {
                        missing = true;
                        continue;
                    }
                    Object value = yaml.get(path);
                    if (apply(settings, option, value)) {
                        read++;
                    } else {
                        log.warning("MUTANTS_CONFIG_INVALID option=\"" + path + "\" (default kept)");
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        options = count;
        fromFile = read;
        if (missing) save();
    }

    private static boolean apply(Object settings, Field option, Object value) throws IllegalAccessException {
        Class<?> t = option.getType();
        if (t == int.class) {
            if (!(value instanceof Number)) return false;
            int v = ((Number) value).intValue();
            Config.RangeInt range = option.getAnnotation(Config.RangeInt.class);
            if (range != null) v = Math.max(range.min(), Math.min(range.max(), v));
            option.setInt(settings, v);
            return true;
        }
        if (t == double.class) {
            if (!(value instanceof Number)) return false;
            double v = ((Number) value).doubleValue();
            Config.RangeDouble range = option.getAnnotation(Config.RangeDouble.class);
            if (range != null) v = Math.max(range.min(), Math.min(range.max(), v));
            option.setDouble(settings, v);
            return true;
        }
        if (t == boolean.class) {
            if (!(value instanceof Boolean)) return false;
            option.setBoolean(settings, (Boolean) value);
            return true;
        }
        if (t == String.class) {
            option.set(settings, String.valueOf(value));
            return true;
        }
        return false;
    }

    /** Writes config.yml from MBConfig's current values, with the mod's comments. */
    public static synchronized void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<String>();
        lines.add("# Mutant Creatures Legacy (Mutant Beasts) on JasperCraft - plugin JasprMutants, AGPL-3.0.");
        lines.add("# The options of the mod's MutantBeasts.cfg, with the same names, defaults and comments.");
        lines.add("# Changes need a server restart (or /mutants reload for options without [restart required]).");
        lines.add("# JasperCraft default: the four 'Mutant Skeleton Legacy ... Sound' options are true (the newer skeleton");
        lines.add("# sounds are All Rights Reserved and are not shipped).");
        try {
            for (Field categoryField : MBConfig.class.getFields()) {
                if (!Modifier.isStatic(categoryField.getModifiers()) || categoryField.isAnnotationPresent(Config.Ignore.class)) continue;
                Object settings = categoryField.get(null);
                lines.add("");
                lines.add(quote(nameOf(categoryField)) + ":");
                for (Field option : settings.getClass().getFields()) {
                    if (Modifier.isStatic(option.getModifiers()) || option.isAnnotationPresent(Config.Ignore.class)) continue;
                    Config.Comment comment = option.getAnnotation(Config.Comment.class);
                    if (comment != null) {
                        for (String c : comment.value()) lines.add("  # " + c);
                    }
                    Config.RangeInt ri = option.getAnnotation(Config.RangeInt.class);
                    Config.RangeDouble rd = option.getAnnotation(Config.RangeDouble.class);
                    if (ri != null) lines.add("  # Min: " + ri.min() + (ri.max() == Integer.MAX_VALUE ? "" : ", max: " + ri.max()));
                    if (rd != null) lines.add("  # Min: " + rd.min() + ", max: " + rd.max());
                    if (option.isAnnotationPresent(Config.RequiresMcRestart.class)) lines.add("  # [restart required]");
                    lines.add("  " + quote(nameOf(option)) + ": " + option.get(settings));
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent.getName());
            File tmp = new File(file.getPath() + ".tmp");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    w.write(line);
                    w.write('\n');
                }
            }
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warning("MUTANTS_CONFIG_SAVE_FAILED reason=" + e.getClass().getSimpleName());
        }
    }

    private static String nameOf(Field f) {
        Config.Name n = f.getAnnotation(Config.Name.class);
        return n != null ? n.value() : f.getName();
    }

    private static String quote(String key) {
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
