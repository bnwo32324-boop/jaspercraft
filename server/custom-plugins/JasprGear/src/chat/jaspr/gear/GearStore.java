package chat.jaspr.gear;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;

/**
 * Per-player gear files: plugins/JasprGear/players/UUID.gear. One line per slot, each value the
 * Base64 of the item's full SNBT. Writes go temp file, fsync, atomic rename on one ordered
 * worker thread, so a crash leaves either the old or the new file, never a torn one.
 * A file that cannot be parsed is renamed aside (never deleted) and reported.
 */
final class GearStore {
    static final String HEADER = "jaspr-gear 1";
    private final File dir;
    private final Logger log;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JasprGear-store");
        t.setDaemon(true);
        return t;
    });
    volatile int writes, failures;

    GearStore(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
        if (!dir.isDirectory() && !dir.mkdirs()) log.warning("GEAR_STORE_DIR_FAILED path=" + dir.getName());
    }

    File file(UUID uuid) { return new File(dir, uuid.toString() + ".gear"); }

    /** Snapshot on the main thread; the disk write happens on the ordered worker. */
    void saveAsync(GearProfile profile) {
        final UUID uuid = profile.uuid;
        final byte[] data = encode(profile);
        writer.execute(() -> write(uuid, data));
    }

    void saveNow(GearProfile profile) { write(profile.uuid, encode(profile)); }

    void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) log.warning("GEAR_STORE_FLUSH_TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static byte[] encode(GearProfile profile) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("laststand=").append(profile.lastStandReady).append('\n');
        for (int i = 0; i < profile.slots.length; i++) {
            String snbt = GearItems.toSnbt(profile.slots[i]);
            out.append(i).append('=')
               .append(Base64.getEncoder().encodeToString(snbt.getBytes(StandardCharsets.UTF_8))).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void write(UUID uuid, byte[] data) {
        File target = file(uuid);
        File temp = new File(dir, uuid.toString() + ".gear.tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(data);
                out.getChannel().force(true);
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            writes++;
        } catch (IOException | RuntimeException e) {
            failures++;
            log.warning("GEAR_SAVE_FAILED player=" + uuid + " error=" + e.getClass().getSimpleName());
        }
    }

    /**
     * Loads into the profile. An unreadable file is renamed aside (never deleted). Returns false
     * only when an unreadable file could not be moved, meaning later saves must not overwrite it.
     */
    boolean load(GearProfile profile) {
        File file = file(profile.uuid);
        if (!file.isFile()) return true;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.get(0).trim())) throw new IOException("bad header");
            ItemStack[] slots = new ItemStack[GearType.SLOT_COUNT];
            long lastStand = 0L;
            for (int n = 1; n < lines.size(); n++) {
                String line = lines.get(n).trim();
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) throw new IOException("bad line");
                String key = line.substring(0, eq), value = line.substring(eq + 1);
                if (key.equals("laststand")) { lastStand = Long.parseLong(value); continue; }
                int slot = Integer.parseInt(key);
                if (slot < 0 || slot >= slots.length) throw new IOException("bad slot");
                String snbt = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                slots[slot] = GearItems.fromSnbt(snbt);
            }
            System.arraycopy(slots, 0, profile.slots, 0, slots.length);
            profile.lastStandReady = lastStand;
            return true;
        } catch (Exception e) {
            File aside = new File(dir, profile.uuid + ".gear.corrupt-" + System.currentTimeMillis());
            boolean moved = file.renameTo(aside);
            log.warning("GEAR_LOAD_FAILED player=" + profile.uuid + " error=" + e.getClass().getSimpleName()
                + " preserved=" + (moved ? aside.getName() : "in-place"));
            return moved; // moved aside: safe to start fresh; still in place: caller must not overwrite it
        }
    }
}
