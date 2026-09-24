package local.eagler.testserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Keeps transient Windows file-sharing collisions from permanently disabling SSO. */
final class ReadyMarkerPublisher {
    private final Path readyFile;
    private final Path temporaryFile;

    ReadyMarkerPublisher(Path readyFile) {
        this.readyFile = readyFile;
        this.temporaryFile = readyFile.resolveSibling("jaspr-sso-ready.tmp");
    }

    boolean refresh(long epochMillis) {
        try {
            Files.write(temporaryFile, Long.toString(epochMillis).getBytes(StandardCharsets.US_ASCII));
            try {
                Files.move(temporaryFile, readyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporaryFile, readyFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException delayed) {
            // The outer gateways retain the previous fresh marker briefly, then fail closed.
            // A one-second lifecycle tick retries without disabling valid ticket verification.
            return false;
        }
    }

    void clear() throws IOException {
        IOException failure = null;
        try { Files.deleteIfExists(readyFile); } catch (IOException failed) { failure = failed; }
        try { Files.deleteIfExists(temporaryFile); } catch (IOException failed) { if (failure == null) failure = failed; }
        if (failure != null) throw failure;
    }
}
