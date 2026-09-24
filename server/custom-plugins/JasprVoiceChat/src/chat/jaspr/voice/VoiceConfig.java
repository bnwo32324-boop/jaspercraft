package chat.jaspr.voice;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables. Distances are in blocks; the browser derives attenuation from the same numbers. */
final class VoiceConfig {
    final int port;
    final double maxDistance;
    final double whisperDistance;
    final int maxClients;
    final int maxAudioBytes;
    final int maxPacketsPerSecond;
    final boolean spectatorsCanTalk;
    final boolean deadCanTalk;
    final boolean crossWorld;
    final boolean announceOnJoin;

    // Environmental acoustics. All of it is measured server side so the browser cannot fake
    // hearing someone through a wall by editing the overlay.
    final boolean environmentEnabled;
    final boolean occlusionEnabled;
    final double reverbStrength;
    final int probeReach;
    final int occlusionSamples;
    final int acousticIntervalTicks;
    final int caveDepth;

    VoiceConfig(FileConfiguration config) {
        this.port = clampInt(config.getInt("port", 24454), 1, 65535, 24454);
        this.maxDistance = clampDouble(config.getDouble("max-distance", 48.0d), 4.0d, 512.0d, 48.0d);
        this.whisperDistance = clampDouble(config.getDouble("whisper-distance", 12.0d), 1.0d, maxDistance, 12.0d);
        this.maxClients = clampInt(config.getInt("max-clients", 24), 1, 128, 24);
        this.maxAudioBytes = clampInt(config.getInt("max-audio-bytes", 700), 64, 4096, 700);
        this.maxPacketsPerSecond = clampInt(config.getInt("max-packets-per-second", 90), 20, 400, 90);
        this.spectatorsCanTalk = config.getBoolean("spectators-can-talk", false);
        this.deadCanTalk = config.getBoolean("dead-can-talk", false);
        this.crossWorld = config.getBoolean("cross-world", false);
        this.announceOnJoin = config.getBoolean("announce-on-join", true);

        this.environmentEnabled = config.getBoolean("environment.enabled", true);
        this.occlusionEnabled = config.getBoolean("environment.occlusion", true);
        this.reverbStrength = clampDouble(config.getDouble("environment.reverb-strength", 1.0d), 0.0d, 2.0d, 1.0d);
        this.probeReach = clampInt(config.getInt("environment.probe-reach", 9), 2, 24, 9);
        this.occlusionSamples = clampInt(config.getInt("environment.occlusion-samples", 12), 4, 48, 12);
        this.acousticIntervalTicks = clampInt(config.getInt("environment.recalc-every-ticks", 8), 1, 200, 8);
        this.caveDepth = clampInt(config.getInt("environment.cave-depth", 6), 1, 64, 6);
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        return value < min || value > max || Double.isNaN(value) ? fallback : value;
    }
}
