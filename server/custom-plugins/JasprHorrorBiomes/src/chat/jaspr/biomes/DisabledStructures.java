package chat.jaspr.biomes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Structures the owner has asked never to be placed again (3.23.0).
 *
 * plugins/JasprHorrorBiomes/disabled-structures.txt holds one name per line, exactly as
 * /where prints it; a catalogue design may also be given by its id. Matching ignores case
 * and surrounding spaces, and lines starting with # are comments. The file is read once,
 * at server start, before any generator runs.
 *
 * Only NEW placement is stopped. Callers gate the placement pass (Megaliths.populate, and
 * StructurePlanner when it plans with admit=true); identification (admit=false), /where,
 * loot and relighting still see every design, so copies already in the world keep working.
 *
 * Coverage as shipped in 3.23.0: the eleven Megaliths set pieces and every catalogue design.
 * The other register groups (Landmarks, Anomalies, Relics, Metropolis, Wonders, Temples,
 * Breach) and the dungeon rooms are not gated yet; naming one of them here has no effect.
 */
public final class DisabledStructures {
    public static final String FILE_NAME = "disabled-structures.txt";
    private static final String HEADER =
            "# Structures that must never be placed again.\n"
            + "# One name per line, exactly as /where prints it. Lines starting with # are ignored.\n"
            + "# A catalogue design may also be given by its id.\n"
            + "#\n"
            + "# This stops new placement only. Copies already in the world are left alone.\n"
            + "# Takes effect at server start.\n";

    /** Lower-cased names. Replaced whole on load, so readers never see a half-built set. */
    private static volatile Set<String> off = Collections.emptySet();

    private DisabledStructures() { }

    /** Reads (and on first run creates) the list. An unreadable file disables nothing. */
    static synchronized void load(File folder, Logger log) {
        Set<String> names = new HashSet<>();
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("cannot create " + folder);
            File file = new File(folder, FILE_NAME);
            if (!file.exists()) Files.write(file.toPath(), HEADER.getBytes(StandardCharsets.UTF_8));
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                names.add(name.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            log.warning("[JasprHorrorBiomes] DISABLED_STRUCTURES_UNREADABLE " + e.getMessage() + "; every structure stays enabled");
            off = Collections.emptySet();
            return;
        }
        off = Collections.unmodifiableSet(names);
        log.info("STRUCTURES_DISABLED count=" + names.size() + (names.isEmpty() ? "" : " names=" + new TreeSet<>(names)));
    }

    /** True when any of the given names (a structure's name, alias or design id) is disabled. */
    public static boolean any(String... names) {
        Set<String> current = off;
        if (current.isEmpty()) return false;
        for (String name : names) {
            if (name != null && current.contains(name.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static int count() { return off.size(); }
}
