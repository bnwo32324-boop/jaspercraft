package chat.jaspr.voice;

/** Supplies the latest known audible state for a player name. */
interface SnapshotSource {
    PlayerSnapshot get(String playerName);

    /**
     * How much solid world sits between two players, 0 for a clear line and 1 for fully buried.
     * Defaulted so test doubles and simpler sources need not model geometry at all.
     */
    default float occlusion(String speakerName, String listenerName) { return 0f; }
}
