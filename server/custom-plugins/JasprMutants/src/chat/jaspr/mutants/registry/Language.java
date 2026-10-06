package chat.jaspr.mutants.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.util.text.translation.LanguageMap;

/**
 * Forge's LanguageMap.inject for the mod's en_us.lang: a Forge server loads every mod's language file into the server
 * language map, so server-side names (entity getName() in death and tame messages, item display names in chat
 * components) read "Mutant Zombie" instead of the raw key. Same parsing as Forge/vanilla (key=value, # comments,
 * %n$s placeholders).
 */
public final class Language {
    private static final Pattern NUMERIC_VARIABLE_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");

    private Language() {
    }

    @SuppressWarnings("unchecked")
    public static int inject(InputStream in) throws IOException {
        Map<String, String> table;
        try {
            Field instance = LanguageMap.class.getDeclaredField("c"); // LanguageMap.instance
            instance.setAccessible(true);
            Object map = instance.get(null);
            Field list = LanguageMap.class.getDeclaredField("d"); // LanguageMap.languageList
            list.setAccessible(true);
            table = (Map<String, String>) list.get(map);
        } catch (ReflectiveOperationException e) {
            throw new IOException("server language map not found", e);
        }
        int count = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq);
                String value = NUMERIC_VARIABLE_PATTERN.matcher(line.substring(eq + 1)).replaceAll("%$1s");
                table.put(key, value);
                count++;
            }
        }
        return count;
    }
}
