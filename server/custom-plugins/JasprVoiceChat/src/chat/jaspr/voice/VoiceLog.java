package chat.jaspr.voice;

import java.util.logging.Logger;

/** Bounded structured logging. Never records audio, keys, signatures or ticket material. */
final class VoiceLog {
    private final Logger logger;

    VoiceLog(Logger logger) { this.logger = logger; }

    void info(String message) { logger.info("JASPR_VOICE " + message); }
    void warn(String message) { logger.warning("JASPR_VOICE " + message); }
    void severe(String message) { logger.severe("JASPR_VOICE " + message); }
}
